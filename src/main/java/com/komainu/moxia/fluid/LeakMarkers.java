package com.komainu.moxia.fluid;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.brush.BrushMarkers;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Señala por dónde se escapa el agua: durante unos segundos, partículas que solo ve el jugador en el hueco (rojo),
 * a lo largo del camino del líquido desde el clic (naranja) y en el sitio donde se pierde (amarillo).
 */
public final class LeakMarkers {
    /** Diez segundos. */
    private static final int LIFETIME = 200;
    private static final int EVERY = 10;
    private static final int HOLE = 0xEF5350;
    private static final int PATH = 0xFFA726;
    private static final int EXIT = 0xFFEE58;
    private static final Map<UUID, Marker> MARKERS = new ConcurrentHashMap<>();

    private LeakMarkers() {
    }

    private record Marker(RegistryKey<World> world, long[] hole, long[] path, long exit, int[] age) {
    }

    /** Señala la fuga (sustituye a la que hubiera). */
    public static void show(ServerPlayerEntity player, long[] hole, long[] path, long exit) {
        MARKERS.put(player.getUuid(), new Marker(player.getWorld().getRegistryKey(), hole.clone(),
                path == null ? new long[0] : path.clone(), exit, new int[]{0}));
        draw(player, MARKERS.get(player.getUuid()));
    }

    public static void forget(UUID player) {
        MARKERS.remove(player);
    }

    public static void clearAll() {
        MARKERS.clear();
    }

    public static void tick(MinecraftServer server) {
        MARKERS.entrySet().removeIf(entry -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            Marker marker = entry.getValue();
            if (player == null || !player.getWorld().getRegistryKey().equals(marker.world())
                    || ++marker.age()[0] > LIFETIME) {
                return true;
            }
            if (marker.age()[0] % EVERY == 0) {
                draw(player, marker);
            }
            return false;
        });
    }

    private static void draw(ServerPlayerEntity player, Marker marker) {
        for (int index = 0; index + 1 < marker.path().length; index++) {
            BrushMarkers.line(player, vector(marker.path()[index]), vector(marker.path()[index + 1]), PATH);
        }
        for (int index = 0; index < Math.min(marker.hole().length, 25); index++) {
            BrushMarkers.point(player, vector(marker.hole()[index]), HOLE);
        }
        BrushMarkers.point(player, vector(marker.exit()), EXIT);
    }

    private static BlockVector3 vector(long packed) {
        return BlockVector3.at(PackedBlockPlan.unpackX(packed), PackedBlockPlan.unpackY(packed),
                PackedBlockPlan.unpackZ(packed));
    }
}
