package com.komainu.moxia.brush.paint;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.brush.BrushTool;
import com.komainu.moxia.brush.ez.BrushMask;
import com.komainu.moxia.brush.ez.EzBrushManager;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.performance.OperationPolicy;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.komainu.moxia.studio.net.StudioAim;
import com.komainu.moxia.worldedit.EditHistory;
import com.komainu.moxia.worldedit.WorldEditService;
import com.komainu.moxia.performance.ServerWork;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los trazos del pincel de pintura: mientras se mantiene el clic derecho, lo que se mira va dejando toques, y todo el
 * trazo es <b>una sola edición</b> de //undo.
 *
 * <p>Minecraft repite el clic derecho cada 4 ticks mientras se mantiene. Entre una repetición y la siguiente se mira
 * cada tick hacia dónde apunta el jugador (más puntos, trazo más suave) y esos puntos se confirman al llegar la
 * siguiente repetición: así el trazo acaba donde se soltó el botón. Sin repeticiones durante
 * {@value #INPUT_GAP_TICKS} ticks, el trazo se cierra.</p>
 *
 * <p>Cada tanda de toques pendientes sigue el camino de los pinceles: foto del terreno repartida entre ticks, cálculo
 * en un worker y escritura con presupuesto por tick en la {@link EditSession} del trazo, que queda abierta hasta el
 * final y entonces va al historial. Una tanda empieza cuando la anterior está escrita: los toques nuevos ven lo
 * pintado y las pasadas acumulan. Lo que el jugador cambie a mano entre la foto y la escritura no se pisa.</p>
 */
public final class PaintStrokes {
    /** Ticks sin repetición del clic que cierran el trazo (Minecraft repite cada 4). */
    static final int INPUT_GAP_TICKS = 6;
    private static final long CAPTURE_NANOS_PER_TICK = 4_000_000L;
    private static final int CAPTURE_BLOCKS_PER_STEP = 16_384;
    private static final long APPLY_NANOS_PER_TICK = 4_000_000L;
    private static final int MAX_BATCH_DABS = 24;
    private static final long MAX_BATCH_VOLUME = 1_500_000L;
    private static final long OWNER_SALT = 0x5041_494E_5452_4BL;
    /** Lo que se mira alrededor del clic para la franja de alturas automática, y cada cuántos bloques. */
    private static final int SURVEY_RADIUS = 64;
    private static final int SURVEY_STEP = 4;
    private static final Map<UUID, Stroke> STROKES = new LinkedHashMap<>();

    private PaintStrokes() {
    }

    public static void register() {
        ServerWork.light(PaintStrokes::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Stroke stroke = STROKES.remove(handler.player.getUuid());
            if (stroke != null) {
                stroke.close(null);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Stroke stroke : List.copyOf(STROKES.values())) {
                stroke.close(null);
            }
            STROKES.clear();
        });
    }

    /**
     * Un clic derecho (o su repetición) con el pincel de pintura en la mano.
     *
     * @param invert la paleta al revés (el clic izquierdo, que no se repite: un toque suelto)
     */
    public static void input(ServerPlayerEntity player, PaintBrush brush, BlockHitResult hit, boolean invert) {
        int now = player.getServer().getTicks();
        String toolId = BrushTool.id(player.getMainHandStack());
        Stroke stroke = STROKES.get(player.getUuid());
        if (stroke != null && (stroke.brush != brush || stroke.invert != invert || !stroke.sameWorld(player))) {
            STROKES.remove(player.getUuid());
            stroke.close(player);
            stroke = null;
        }
        double[] point = center(hit);
        if (stroke == null) {
            stroke = new Stroke(player, toolId, brush, invert, now, point);
            STROKES.put(player.getUuid(), stroke);
        }
        stroke.confirm(point, now);
        if (invert) {
            // El izquierdo no se repite al mantenerlo: un toque y el trazo se cierra en cuanto se escriba.
            stroke.lastInput = now - INPUT_GAP_TICKS - 1;
        }
    }

    /**
     * La franja de alturas automática del degradado por altura: el mapa de alturas del mundo alrededor del clic (sin
     * hojas), del 5 % al 95 %. Así pintar una colina reparte la paleta entre su pie y su cima aunque se empiece por el
     * llano. Solo mira chunks ya cargados.
     */
    static double[] survey(ServerWorld world, int centerX, int centerZ, double fallbackY) {
        int side = SURVEY_RADIUS * 2 / SURVEY_STEP + 1;
        int[] heights = new int[side * side];
        int count = 0;
        for (int dz = -SURVEY_RADIUS; dz <= SURVEY_RADIUS; dz += SURVEY_STEP) {
            for (int dx = -SURVEY_RADIUS; dx <= SURVEY_RADIUS; dx += SURVEY_STEP) {
                int x = centerX + dx;
                int z = centerZ + dz;
                if (world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
                    heights[count++] = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                }
            }
        }
        return PaintPainter.heightRange(heights, count, fallbackY);
    }

    private static double[] center(BlockHitResult hit) {
        return new double[]{hit.getBlockPos().getX() + 0.5, hit.getBlockPos().getY() + 0.5,
                hit.getBlockPos().getZ() + 0.5};
    }

    private static void tick(MinecraftServer server) {
        if (STROKES.isEmpty()) {
            return;
        }
        int now = server.getTicks();
        for (Stroke stroke : List.copyOf(STROKES.values())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(stroke.playerId);
            if (player == null || !stroke.sameWorld(player)) {
                STROKES.remove(stroke.playerId);
                stroke.close(player);
                continue;
            }
            try {
                stroke.step(server, player, now);
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.error("Paint stroke failed for {}", stroke.playerId, exception);
                Feedback.statusWarn(player, Text.literal("Pintar: " + readable(exception)));
                // Lo ya escrito se queda y va al historial; el trazo se cierra ahora.
                stroke.stopTrace();
                stroke.lastInput = now - INPUT_GAP_TICKS - 1;
            }
            if (stroke.finished(now)) {
                STROKES.remove(stroke.playerId);
                stroke.close(player);
            }
        }
    }

    private static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private enum Stage {
        IDLE, CAPTURE, PLAN, APPLY
    }

    /** Un trazo en curso. Todo se toca desde el hilo del servidor salvo el cálculo, que va en su worker. */
    private static final class Stroke {
        private final UUID playerId;
        private final UUID owner;
        private final String toolId;
        private final PaintBrush brush;
        private final boolean invert;
        private final RegistryKey<World> world;
        private final PaintStroke path;
        private final PaintPainter.Memory memory;
        private final WorldEditService.PlayerContext context;
        private final BrushMask mask;
        private final ArrayDeque<PaintPainter.Dab> pending = new ArrayDeque<>();
        /** Lo que se miró cada tick desde la última repetición del clic; se confirma con la siguiente. */
        private final List<double[]> tentative = new ArrayList<>();
        private int lastInput;
        private Stage stage = Stage.IDLE;
        private WorldSnapshot.Capture capture;
        private List<PaintPainter.Dab> batch;
        private PaintBrush.Planned planned;
        private int applied;
        private EditSession session;
        private double heightLow;
        private double heightHigh;
        private long changed;
        private int kept;
        /** Cambia al cerrar: un cálculo que llegue tarde se descarta. */
        private int generation;
        private boolean stopped;

        private Stroke(ServerPlayerEntity player, String toolId, PaintBrush brush, boolean invert, int now,
                       double[] start) {
            this.playerId = player.getUuid();
            this.owner = new UUID(playerId.getMostSignificantBits() ^ OWNER_SALT, playerId.getLeastSignificantBits());
            this.toolId = toolId;
            this.brush = brush;
            this.invert = invert;
            this.world = player.getWorld().getRegistryKey();
            this.path = brush.stroke();
            this.memory = new PaintPainter.Memory(path.salt());
            this.context = WorldEditService.context(player);
            this.mask = brush.mask(player);
            this.lastInput = now;
            if (brush.config().gradient() == PaintGradient.HEIGHT && brush.config().autoHeights()) {
                double[] range = survey(player.getServerWorld(), (int) Math.floor(start[0]),
                        (int) Math.floor(start[2]), Math.floor(start[1]));
                this.heightLow = range[0];
                this.heightHigh = range[1];
            } else {
                this.heightLow = brush.config().heightLow();
                this.heightHigh = brush.config().heightHigh();
            }
        }

        boolean sameWorld(ServerPlayerEntity player) {
            return player.getWorld().getRegistryKey().equals(world);
        }

        /** Una repetición del clic: lo mirado entre medias y este punto pasan a ser trazo. */
        void confirm(double[] point, int now) {
            if (stopped) {
                return;
            }
            for (double[] sample : tentative) {
                pending.addAll(path.add(sample[0], sample[1], sample[2]));
            }
            tentative.clear();
            pending.addAll(path.add(point[0], point[1], point[2]));
            brush.advance(path);
            lastInput = now;
        }

        boolean finished(int now) {
            return stage == Stage.IDLE && (pending.isEmpty() || stopped) && now - lastInput > INPUT_GAP_TICKS;
        }

        void step(MinecraftServer server, ServerPlayerEntity player, int now) {
            if (!stopped && now - lastInput <= INPUT_GAP_TICKS
                    && toolId.equals(BrushTool.id(player.getMainHandStack()))) {
                // Con el Studio abierto, el último clic del cursor; si no, lo que se mira.
                HitResult result = StudioAim.target(player, EzBrushManager.REACH);
                if (result instanceof BlockHitResult hit && result.getType() == HitResult.Type.BLOCK) {
                    tentative.add(center(hit));
                }
            }
            if (stage == Stage.IDLE && !pending.isEmpty() && !stopped) {
                startBatch(player);
            }
            if (stage == Stage.CAPTURE) {
                long deadline = ServerWork.deadline(CAPTURE_NANOS_PER_TICK);
                while (!capture.complete() && System.nanoTime() < deadline) {
                    capture.step(CAPTURE_BLOCKS_PER_STEP, deadline);
                }
                if (capture.complete()) {
                    plan(server, capture.finish());
                }
            }
            if (stage == Stage.APPLY) {
                apply(player);
            }
        }

        private void startBatch(ServerPlayerEntity player) {
            List<PaintPainter.Dab> taken = new ArrayList<>();
            int[] box = null;
            while (!pending.isEmpty() && taken.size() < MAX_BATCH_DABS) {
                int[] grown = include(box, pending.peekFirst());
                if (box != null && volume(grown) > MAX_BATCH_VOLUME) {
                    break;
                }
                box = grown;
                taken.add(pending.pollFirst());
            }
            batch = taken;
            int centerX = Math.floorDiv(box[0] + box[3], 2);
            int centerY = Math.floorDiv(box[1] + box[4], 2);
            int centerZ = Math.floorDiv(box[2] + box[5], 2);
            int horizontal = Math.max(box[3] - centerX, box[5] - centerZ);
            int vertical = box[4] - centerY;
            capture = WorldSnapshot.begin(context.world(), BlockVector3.at(centerX, centerY, centerZ), horizontal,
                    vertical);
            stage = Stage.CAPTURE;
        }

        /** La caja {@code [minX, minY, minZ, maxX, maxY, maxZ]} con un toque más (y lo que necesita alrededor). */
        private int[] include(int[] box, PaintPainter.Dab dab) {
            int reach = PaintPainter.reach(dab, brush.footprint()) + 3 + brush.config().layers();
            int vertical = brush.vertical() + brush.config().layers() + 2;
            int x = (int) Math.floor(dab.x());
            int y = (int) Math.floor(dab.y());
            int z = (int) Math.floor(dab.z());
            int[] dabBox = {x - reach, y - vertical, z - reach, x + reach, y + vertical, z + reach};
            if (box == null) {
                return dabBox;
            }
            return new int[]{Math.min(box[0], dabBox[0]), Math.min(box[1], dabBox[1]), Math.min(box[2], dabBox[2]),
                    Math.max(box[3], dabBox[3]), Math.max(box[4], dabBox[4]), Math.max(box[5], dabBox[5])};
        }

        private static long volume(int[] box) {
            // La foto es un cuadrado en planta alrededor del centro.
            long side = Math.max(box[3] - box[0], box[5] - box[2]) + 2L;
            return side * side * (box[4] - box[1] + 1L);
        }

        private void plan(MinecraftServer server, WorldSnapshot snapshot) {
            stage = Stage.PLAN;
            capture = null;
            int expected = generation;
            List<PaintPainter.Dab> dabs = batch;
            PaintPainter.Settings settings = brush.settings(heightLow, heightHigh, invert);
            AsyncEngine.submit(owner, "Pintar", () -> brush.plan(snapshot, dabs, mask, settings, memory, () -> {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new IllegalStateException("Cálculo cancelado");
                        }
                    }),
                    result -> server.execute(() -> planned(expected, result)),
                    throwable -> server.execute(() -> failed(server, expected, throwable)));
        }

        private void planned(int expected, PaintBrush.Planned result) {
            if (expected != generation || stage != Stage.PLAN) {
                return;
            }
            heightLow = result.heightLow();
            heightHigh = result.heightHigh();
            planned = result;
            applied = 0;
            stage = result.size() == 0 ? Stage.IDLE : Stage.APPLY;
        }

        private void failed(MinecraftServer server, int expected, Throwable throwable) {
            if (expected != generation) {
                return;
            }
            stage = Stage.IDLE;
            stopped = true;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                Feedback.statusWarn(player, Text.literal("Pintar: " + readable(throwable)));
            }
        }

        private void apply(ServerPlayerEntity player) {
            if (session == null) {
                session = context.session().createEditSession(context.actor());
            }
            try {
                OperationPolicy.validate(player, changed + planned.size() - applied, 0);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                Feedback.statusWarn(player, Text.literal("Pintar: el trazo llega al límite de bloques; suéltalo y"
                        + " sigue con otro"));
                stopTrace();
                return;
            }
            long deadline = ServerWork.deadline(APPLY_NANOS_PER_TICK);
            try {
                while (applied < planned.size() && System.nanoTime() < deadline) {
                    long key = planned.positions()[applied];
                    BlockVector3 position = BlockVector3.at(PaintPainter.unpackX(key), PaintPainter.unpackY(key),
                            PaintPainter.unpackZ(key));
                    BlockState now = session.getBlock(position);
                    if (now.equals(planned.expected()[applied])) {
                        session.setBlock(position, planned.states()[applied]);
                        changed++;
                    } else {
                        kept++;
                    }
                    applied++;
                }
            } catch (MaxChangedBlocksException exception) {
                Feedback.statusWarn(player, Text.literal("Pintar: parado en el límite de WorldEdit (//limit)"));
                stopTrace();
                return;
            }
            if (applied >= planned.size()) {
                // Lo escrito tiene que estar en el mundo antes de la foto de la tanda siguiente.
                session.flushSession();
                planned = null;
                stage = Stage.IDLE;
            }
        }

        /** Deja de pintar: lo escrito se queda y el trazo se cierra en cuanto se suelte el botón. */
        private void stopTrace() {
            stopped = true;
            pending.clear();
            planned = null;
            stage = Stage.IDLE;
        }

        /** Cierra el trazo: lo pintado va al historial como una edición. */
        void close(ServerPlayerEntity player) {
            generation++;
            stopped = true;
            AsyncEngine.cancel(owner);
            capture = null;
            planned = null;
            if (session == null) {
                return;
            }
            try {
                session.close();
            } finally {
                if (session.size() > 0) {
                    EditHistory.label(session, "Pintar");
                    context.session().remember(session);
                }
            }
            if (player != null && changed > 0) {
                Feedback.status(player, "Pintar: " + ProgressLine.group(changed) + " bloques"
                        + (kept > 0 ? " · " + ProgressLine.group(kept) + " cambios tuyos conservados" : "")
                        + " · //undo · semilla " + brush.seed() + (brush.locked() ? " (bloqueada)" : ""));
            }
            session = null;
        }
    }
}
