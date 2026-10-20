package com.komainu.moxia.feedback;

import com.komainu.moxia.symmetry.SymmetryService;
import com.komainu.moxia.symmetry.SymmetrySettings;
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
import java.util.Map;
import java.util.UUID;

/**
 * Lo que el cliente con KomaEdit dibuja por su cuenta en el mundo: la selección de WorldEdit (con sus medidas) y los
 * planos de la simetría. Se mira cada medio segundo y solo se manda lo que cambia. A los clientes sin el mod no les
 * llega nada: la simetría les sigue saliendo con partículas.
 */
public final class WorldOverlaySync {
    private static final int INTERVAL_TICKS = 10;
    private static final Map<UUID, SelectionPayload> SELECTIONS = new HashMap<>();
    private static final Map<UUID, SymmetryGuidePayload> SYMMETRY = new HashMap<>();

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

    private static String shape(Region region) {
        return switch (region) {
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
    }

    public static void clearAll() {
        SELECTIONS.clear();
        SYMMETRY.clear();
    }
}
