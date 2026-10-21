package com.komainu.moxia.feedback;

import com.komainu.moxia.selection.BlockSet;
import com.komainu.moxia.selection.SelectionOutline;
import com.komainu.moxia.selection.SelectionPayloads;
import com.komainu.moxia.symmetry.SymmetryService;
import com.komainu.moxia.symmetry.SymmetrySettings;
import com.komainu.moxia.worldedit.BlockSetRegion;
import com.komainu.moxia.worldedit.BlockSetSelector;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.world.World;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lo que el cliente con KomaEdit dibuja por su cuenta en el mundo: la selección de WorldEdit (con sus medidas), el
 * contorno de una selección de bloques sueltos (la del lazo o el pincel, {@link BlockSetSelector}) y los planos de la
 * simetría. Se mira cada medio segundo y solo se manda lo que cambia. A los clientes sin el mod no les llega nada: la
 * simetría les sigue saliendo con partículas.
 */
public final class WorldOverlaySync {
    private static final int INTERVAL_TICKS = 10;
    private static final Map<UUID, SelectionPayload> SELECTIONS = new HashMap<>();
    private static final Map<UUID, SymmetryGuidePayload> SYMMETRY = new HashMap<>();
    /** La versión del contorno que tiene cada cliente ({@link BlockSetSelector#version}). */
    private static final Map<UUID, Long> OUTLINES = new HashMap<>();
    /** La versión cuyo contorno se está calculando para cada jugador. */
    private static final Map<UUID, Long> PENDING = new HashMap<>();
    /** Las aristas de una selección grande tardan: se calculan en un hilo aparte y se mandan desde el del servidor. */
    private static final ExecutorService OUTLINE_WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit-Outline");
        thread.setDaemon(true);
        return thread;
    });

    private WorldOverlaySync() {
    }

    /** Si el cliente del jugador dibuja él mismo la simetría (y no necesita partículas). */
    public static boolean drawsSymmetry(ServerPlayerEntity player) {
        return player.networkHandler != null && ServerPlayNetworking.canSend(player, SymmetryGuidePayload.ID);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.networkHandler == null) {
                continue;
            }
            UUID id = player.getUuid();
            if (ServerPlayNetworking.canSend(player, SelectionPayload.ID)) {
                SelectionPayload selection = selection(player);
                if (!selection.equals(SELECTIONS.getOrDefault(id, SelectionPayload.NONE))) {
                    SELECTIONS.put(id, selection);
                    ServerPlayNetworking.send(player, selection);
                }
            }
            if (ServerPlayNetworking.canSend(player, SelectionPayloads.Outline.ID)) {
                outline(player);
            }
            if (drawsSymmetry(player)) {
                SymmetryGuidePayload symmetry = symmetry(player);
                if (!symmetry.equals(SYMMETRY.getOrDefault(id, SymmetryGuidePayload.NONE))) {
                    SYMMETRY.put(id, symmetry);
                    ServerPlayNetworking.send(player, symmetry);
                }
            }
        }
    }

    /**
     * La selección en el mundo del jugador. No pregunta por la de otro mundo: WorldEdit la borraría al cambiar de
     * dimensión aunque no se use ninguna orden.
     */
    static SelectionPayload selection(ServerPlayerEntity player) {
        try {
            WorldEditService.PlayerContext context = WorldEditService.context(player);
            World world = context.session().getSelectionWorld();
            if (world == null || !world.equals(context.world())) {
                return SelectionPayload.NONE;
            }
            RegionSelector selector = context.session().getRegionSelector(world);
            if (selector instanceof BlockSetSelector blocks && blocks.fallback() != null) {
                // Se marcó un punto con la varita sobre una selección de bloques sueltos: ya es una caja de verdad.
                selector = blocks.fallback();
                context.session().setRegionSelector(world, selector);
            }
            if (!selector.isDefined()) {
                BlockVector3 first = selector.getPrimaryPosition();
                return new SelectionPayload(true, false, first.x(), first.y(), first.z(), first.x(), first.y(),
                        first.z(), "point");
            }
            Region region = selector.getRegion();
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            return new SelectionPayload(true, true, min.x(), min.y(), min.z(), max.x(), max.y(), max.z(),
                    shape(region));
        } catch (IncompleteRegionException | RuntimeException exception) {
            return SelectionPayload.NONE;
        }
    }

    /**
     * Manda el contorno de la selección de bloques sueltos cuando cambia, y que ya no hay cuando deja de haberla. Se
     * mira cada medio segundo (no en cada pasada del pincel); las aristas se calculan en {@link #OUTLINE_WORKER} con
     * una copia de los bloques, y solo se mandan si la selección sigue siendo esa.
     */
    private static void outline(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        BlockSet blocks = null;
        long version = 0L;
        try {
            WorldEditService.PlayerContext context = WorldEditService.context(player);
            World world = context.session().getSelectionWorld();
            if (world != null && world.equals(context.world())
                    && context.session().getRegionSelector(world) instanceof BlockSetSelector selector
                    && selector.blocks() != null) {
                blocks = selector.blocks();
                version = selector.version();
            }
        } catch (RuntimeException ignored) {
            blocks = null;
        }
        Long sent = OUTLINES.get(id);
        if (blocks == null) {
            PENDING.remove(id);
            if (sent != null) {
                OUTLINES.remove(id);
                ServerPlayNetworking.send(player, SelectionPayloads.Outline.NONE);
            }
            return;
        }
        Long pending = PENDING.get(id);
        if (sent != null && sent == version || pending != null && pending == version) {
            return;
        }
        long wanted = version;
        int[] box = blocks.bounds();
        if (blocks.size() > SelectionOutline.MAX_BLOCKS) {
            // Demasiado grande para el contorno: la caja y el número, sin copiar nada.
            PENDING.remove(id);
            OUTLINES.put(id, wanted);
            ServerPlayNetworking.send(player, new SelectionPayloads.Outline(true, blocks.size(), box[0], box[1],
                    box[2], box[3], box[4], box[5], List.of()));
            return;
        }
        PENDING.put(id, wanted);
        long[] copy = blocks.toArray();
        MinecraftServer server = player.getServer();
        OUTLINE_WORKER.execute(() -> {
            SelectionPayloads.Outline payload;
            try {
                payload = SelectionPayloads.Outline.of(BlockSet.ofPacked(copy));
            } catch (RuntimeException exception) {
                payload = null;
            }
            SelectionPayloads.Outline ready = payload;
            server.execute(() -> {
                Long still = PENDING.get(id);
                if (still == null || still != wanted) {
                    return;
                }
                PENDING.remove(id);
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(id);
                if (ready != null && target != null && target.networkHandler != null) {
                    OUTLINES.put(id, wanted);
                    ServerPlayNetworking.send(target, ready);
                }
            });
        });
    }

    private static String shape(Region region) {
        return switch (region) {
            case BlockSetRegion ignored -> "blocks";
            case Polygonal2DRegion ignored -> "poly2d";
            case EllipsoidRegion ignored -> "ellipsoid";
            case CylinderRegion ignored -> "cylinder";
            case ConvexPolyhedralRegion ignored -> "convex";
            default -> "cuboid";
        };
    }

    static SymmetryGuidePayload symmetry(ServerPlayerEntity player) {
        SymmetrySettings settings = SymmetryService.settings(player.getUuid());
        if (settings == null || settings.center() == null
                || !settings.appliesIn(SymmetryService.dimension(player.getServerWorld()))) {
            return SymmetryGuidePayload.NONE;
        }
        var mode = settings.mode();
        var center = settings.center();
        return new SymmetryGuidePayload(true, mode.radial(), mode.mirrorX(), mode.mirrorZ(), mode.mirrorY(),
                mode.arms(), center.x2(), center.y2(), center.z2());
    }

    public static void forget(UUID player) {
        SELECTIONS.remove(player);
        SYMMETRY.remove(player);
        OUTLINES.remove(player);
        PENDING.remove(player);
    }

    public static void clearAll() {
        SELECTIONS.clear();
        SYMMETRY.clear();
        OUTLINES.clear();
        PENDING.clear();
    }
}
