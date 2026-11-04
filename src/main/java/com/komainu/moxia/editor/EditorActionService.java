package com.komainu.moxia.editor;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.edit.EditLimits;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.path.PathEditing;
import com.komainu.moxia.path.PathHistory;
import com.komainu.moxia.path.PathHologramSync;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.performance.OperationPolicy;
import com.komainu.moxia.performance.RequestBudget;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.UUID;

public final class EditorActionService {
    /**
     * Lo que cuesta de verdad en el servidor (deshacer, copiar, el área mágica, encolar la selección entera…), con
     * freno: por el chat, el antispam de Minecraft deja una orden por segundo a quien no es OP; por este paquete, un
     * cliente modificado lo pedía sin parar. El ráfaga cabe en un salto del historial del Studio (hasta 12 pasos).
     */
    static final RequestBudget HEAVY = new RequestBudget(16, 2.0);
    static final String TOO_FAST = "Vas demasiado deprisa con el editor: espera un momento";

    private EditorActionService() {
    }

    public static void handle(ServerPlayerEntity player, EditorActionPayload payload) {
        try {
            if (!KomaPermissions.canAccess(player)) {
                throw new IllegalArgumentException("No tienes permiso para usar el editor");
            }
            requireAction(player, payload.action());
            if (!admit(player.getUuid(), payload.action(), System.nanoTime())) {
                message(player, TOO_FAST);
                return;
            }
            validatePosition(player.getServerWorld(), payload.position());
            switch (payload.action()) {
                case EditorActionPayload.PATH_ADD -> {
                    PathManager.add(player.getUuid(), payload.position());
                    PathHologramSync.sync(player);
                }
                case EditorActionPayload.PATH_MOVE -> {
                    PathManager.move(player.getUuid(), payload.index(), payload.position());
                    PathHologramSync.sync(player);
                }
                case EditorActionPayload.PATH_REMOVE -> {
                    PathManager.remove(player.getUuid(), payload.index());
                    PathHologramSync.sync(player);
                }
                case EditorActionPayload.PATH_INSERT -> {
                    PathManager.update(player.getUuid(), state -> new PathHistory.State(
                            PathEditing.insertMidpoint(state.points(), payload.index(), state.closed()),
                            state.closed()
                    ));
                    PathHologramSync.sync(player);
                    message(player, "Punto insertado tras el " + (payload.index() + 1));
                }
                case EditorActionPayload.BOX_PRIMARY -> WorldEditService.setCuboidPoint(
                        player, true, payload.position(), payload.index() < 0
                );
                case EditorActionPayload.BOX_SECONDARY -> WorldEditService.setCuboidPoint(
                        player, false, payload.position(), false
                );
                case EditorActionPayload.MAGIC_BLOCK -> {
                    WorldEditService.setCuboid(player, payload.position(), payload.position());
                    message(player, "Bloque seleccionado: " + coordinates(payload.position()));
                }
                case EditorActionPayload.MAGIC_AREA -> selectConnectedArea(player, payload.position());
                case EditorActionPayload.PAINT -> FastPaintService.dab(
                        player, payload.position(), payload.radius(), payload.pattern()
                );
                case EditorActionPayload.PAINT_BEGIN -> FastPaintService.begin(
                        player, payload.radius(), payload.pattern()
                );
                case EditorActionPayload.PAINT_END -> FastPaintService.end(player);
                case EditorActionPayload.UNDO -> message(player,
                        WorldEditService.undo(player) ? "Operación deshecha" : "No hay operaciones para deshacer");
                case EditorActionPayload.REDO -> message(player,
                        WorldEditService.redo(player) ? "Operación rehecha" : "No hay operaciones para rehacer");
                case EditorActionPayload.PAINT_SELECTION -> paintSelection(player, payload.pattern());
                case EditorActionPayload.SELECTION_REPLACE -> replaceSelection(player, payload.pattern());
                case EditorActionPayload.SELECTION_DELETE -> deleteSelection(player);
                case EditorActionPayload.CLIPBOARD_COPY -> copySelection(player);
                case EditorActionPayload.CLIPBOARD_CUT -> cutSelection(player);
                case EditorActionPayload.CLIPBOARD_PASTE -> {
                    int queued = BatchEditService.submitClipboard(
                            player,
                            WorldEditService.clipboard(player),
                            payload.position(),
                            "Pegado de portapapeles"
                    );
                    message(player, "Pegado en cola: " + queued + " posiciones");
                }
                default -> throw new IllegalArgumentException("Acción de editor desconocida");
            }
        } catch (Exception exception) {
            message(player, "Error: " + readable(exception));
        }
    }

    private static void selectConnectedArea(ServerPlayerEntity player, BlockPos origin) {
        ServerWorld world = player.getServerWorld();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        MagicArea.Result area = MagicArea.flood(origin.getX(), origin.getY(), origin.getZ(), new MagicArea.World() {
            @Override
            public boolean readable(int x, int y, int z) {
                cursor.set(x, y, z);
                return world.isInBuildLimit(cursor) && world.isChunkLoaded(cursor);
            }

            @Override
            public int state(int x, int y, int z) {
                return Block.getRawIdFromState(world.getBlockState(cursor.set(x, y, z)));
            }

            @Override
            public boolean isAir(int state) {
                return Block.getStateFromRawId(state).isAir();
            }
        });
        BlockBounds bounds = area.bounds();
        WorldEditService.setCuboid(player, new BlockPos(bounds.minimumX(), bounds.minimumY(), bounds.minimumZ()),
                new BlockPos(bounds.maximumX(), bounds.maximumY(), bounds.maximumZ()));
        message(player, "Área mágica: " + ProgressLine.group(area.blocks()) + " bloques conectados");
    }

    private static void requireAction(ServerPlayerEntity player, int action) {
        for (String node : permissions(action)) {
            KomaPermissions.require(player, node);
        }
    }

    /**
     * Todo lo que pide cada acción: cortar copia (portapapeles) y además borra la selección entera, como
     * {@link EditorActionPayload#SELECTION_DELETE} (construir). Antes bastaba el portapapeles para borrar.
     */
    static java.util.List<String> permissions(int action) {
        return action == EditorActionPayload.CLIPBOARD_CUT
                ? java.util.List.of(KomaPermissions.CLIPBOARD, KomaPermissions.BUILD)
                : java.util.List.of(permission(action));
    }

    /** El permiso que pide cada acción del paquete. */
    static String permission(int action) {
        return switch (action) {
            case EditorActionPayload.PATH_ADD, EditorActionPayload.PATH_MOVE, EditorActionPayload.PATH_REMOVE,
                 EditorActionPayload.PATH_INSERT ->
                    KomaPermissions.PATH;
            case EditorActionPayload.PAINT, EditorActionPayload.PAINT_BEGIN, EditorActionPayload.PAINT_END ->
                    KomaPermissions.PAINT;
            case EditorActionPayload.CLIPBOARD_COPY, EditorActionPayload.CLIPBOARD_CUT,
                 EditorActionPayload.CLIPBOARD_PASTE -> KomaPermissions.CLIPBOARD;
            case EditorActionPayload.UNDO, EditorActionPayload.REDO -> KomaPermissions.HISTORY;
            // Cambian los bloques de toda la selección: construir, no seleccionar.
            case EditorActionPayload.SELECTION_REPLACE, EditorActionPayload.SELECTION_DELETE,
                 EditorActionPayload.PAINT_SELECTION -> KomaPermissions.BUILD;
            default -> KomaPermissions.SELECT;
        };
    }

    private static void paintSelection(ServerPlayerEntity player, String patternInput)
            throws IncompleteRegionException {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        Region selection = context.session().getSelection(context.world()).clone();
        Pattern pattern = WorldEditService.parsePatternUnchecked(context, patternInput);
        int queued = BatchEditService.submitRegion(
                player,
                selection,
                block -> {
                    BlockPos position = new BlockPos(block.x(), block.y(), block.z());
                    BlockState existing = player.getServerWorld().getBlockState(position);
                    if (existing.isAir() || !existing.getFluidState().isEmpty()) {
                        return null;
                    }
                    return pattern;
                },
                "Pintura sólida de selección"
        );
        message(player, "Pintura sólida en cola: " + queued + " posiciones · Ctrl+Z al finalizar");
    }

    private static void replaceSelection(ServerPlayerEntity player, String patternInput)
            throws IncompleteRegionException {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        Region selection = context.session().getSelection(context.world()).clone();
        Pattern pattern = WorldEditService.parsePatternUnchecked(context, patternInput);
        int queued = BatchEditService.submitRegion(player, selection, position -> pattern, "Reemplazo de selección");
        message(player, "Reemplazo en cola: " + queued + " posiciones");
    }

    private static void deleteSelection(ServerPlayerEntity player) throws IncompleteRegionException {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        Region selection = context.session().getSelection(context.world()).clone();
        Pattern air = WorldEditService.parsePatternUnchecked(context, "air");
        int queued = BatchEditService.submitRegion(player, selection, position -> air, "Borrado de selección");
        message(player, "Borrado en cola: " + queued + " posiciones");
    }

    /** ¿Se atiende la acción? Las ligeras (puntos, caja, pincel) siempre; las pesadas, con {@link #HEAVY}. */
    static boolean admit(UUID player, int action, long nanos) {
        return !heavy(action) || HEAVY.allow(player, nanos);
    }

    /** Las acciones que leen o encolan mucho en el hilo del servidor. */
    static boolean heavy(int action) {
        return switch (action) {
            case EditorActionPayload.MAGIC_AREA, EditorActionPayload.UNDO, EditorActionPayload.REDO,
                 EditorActionPayload.PAINT_SELECTION, EditorActionPayload.SELECTION_REPLACE,
                 EditorActionPayload.SELECTION_DELETE, EditorActionPayload.CLIPBOARD_COPY,
                 EditorActionPayload.CLIPBOARD_CUT, EditorActionPayload.CLIPBOARD_PASTE -> true;
            default -> false;
        };
    }

    public static void forget(UUID player) {
        HEAVY.forget(player);
    }

    /**
     * Copiar lee la selección entera de una vez, en el hilo del servidor y en memoria: no pasa del límite por edición
     * del jugador (que es lo que podría pegar después) ni de lo que KomaEdit lee de una vez. Antes no miraba nada, y
     * una selección de punta a punta del mundo agotaba la memoria del servidor.
     */
    static Optional<EditLimits.Excess> copyExcess(long volume, long limit) {
        return EditLimits.check(EditLimits.Kind.SERVER, volume, limit)
                .or(() -> EditLimits.check(EditLimits.Kind.CAPTURE, volume, RegionOperationService.MAX_CAPTURE_BLOCKS));
    }

    /**
     * ¿Están cargados todos los chunks de la caja? Copiar no genera terreno: con la selección en chunks sin cargar,
     * WorldEdit los cargaba (o generaba) uno tras otro sin soltar el hilo del servidor.
     */
    static boolean loaded(int minX, int minZ, int maxX, int maxZ, ChunkCheck chunks) {
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (!chunks.loaded(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    @FunctionalInterface
    interface ChunkCheck {
        boolean loaded(int chunkX, int chunkZ);
    }

    private static void copySelection(ServerPlayerEntity player) throws Exception {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        Region selection = context.session().getSelection(context.world());
        copyExcess(selection.getVolume(), OperationPolicy.limit(player)).ifPresent(excess -> {
            throw new IllegalArgumentException(excess.message("La selección", false));
        });
        BlockVector3 low = selection.getMinimumPoint();
        BlockVector3 high = selection.getMaximumPoint();
        ServerWorld world = player.getServerWorld();
        if (!loaded(low.x(), low.z(), high.x(), high.z(),
                (chunkX, chunkZ) -> world.getChunkManager().isChunkLoaded(chunkX, chunkZ))) {
            throw new IllegalArgumentException("Parte de la selección no está cargada: acércate para copiarla");
        }
        long copied = WorldEditService.copySelection(player);
        message(player, "Copiado: " + ProgressLine.group(copied) + " bloques · Ctrl+V para posicionar");
    }

    private static void cutSelection(ServerPlayerEntity player) throws Exception {
        copySelection(player);
        deleteSelection(player);
    }

    private static void validatePosition(ServerWorld world, BlockPos position) {
        if (!world.isInBuildLimit(position)) {
            throw new IllegalArgumentException("El punto está fuera de los límites del mundo");
        }
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + ", " + position.getY() + ", " + position.getZ();
    }

    private static void message(ServerPlayerEntity player, String message) {
        player.sendMessage(Feedback.line(Feedback.Level.INFO, message), true);
    }

    private static String readable(Exception exception) {
        return com.komainu.moxia.feedback.FailureReason.of(exception);
    }
}
