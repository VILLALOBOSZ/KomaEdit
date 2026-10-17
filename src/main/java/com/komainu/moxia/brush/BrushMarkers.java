package com.komainu.moxia.brush;

import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import org.joml.Vector3f;

import java.util.List;

/** Marcas de partículas que solo ve el jugador: puntos de un degradado, del trazo o de la supervarita. */
public final class BrushMarkers {
    public static final int START = 0x66BB6A;
    public static final int END = 0xEF5350;
    public static final int POINT = 0xFFCA28;

    private BrushMarkers() {
    }

    public static void point(ServerPlayerEntity player, BlockVector3 point, int color) {
        DustParticleEffect effect = dust(color, 1.6F);
        player.getServerWorld().spawnParticles(player, effect, true,
                point.x() + 0.5, point.y() + 0.5, point.z() + 0.5, 6, 0.25, 0.25, 0.25, 0.0);
    }

    /** Línea de puntos entre dos bloques, como mucho 96 partículas. */
    public static void line(ServerPlayerEntity player, BlockVector3 from, BlockVector3 to, int color) {
        DustParticleEffect effect = dust(color, 0.8F);
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.clamp(Math.ceil(length * 2.0), 1, 96);
        for (int step = 0; step <= steps; step++) {
            double amount = step / (double) steps;
            player.getServerWorld().spawnParticles(player, effect, true,
                    from.x() + 0.5 + dx * amount, from.y() + 0.5 + dy * amount, from.z() + 0.5 + dz * amount,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public static void path(ServerPlayerEntity player, List<BlockVector3> points, int color) {
        for (int index = 0; index < points.size(); index++) {
            point(player, points.get(index), index == 0 ? START : color);
            if (index > 0) {
                line(player, points.get(index - 1), points.get(index), color);
            }
        }
    }

    private static DustParticleEffect dust(int color, float scale) {
        return new DustParticleEffect(new Vector3f(
                (color >> 16 & 0xFF) / 255.0F,
                (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F
        ), scale);
    }
}
