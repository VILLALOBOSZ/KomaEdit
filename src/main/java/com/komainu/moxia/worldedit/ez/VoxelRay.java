package com.komainu.moxia.worldedit.ez;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.function.Predicate;

/** Recorre los bloques que atraviesa un rayo, en orden, como si todos fueran cubos completos (Amanatides–Woo). */
public final class VoxelRay {
    private VoxelRay() {
    }

    /**
     * @return el primer bloque que cumple {@code hit}, o {@code null} si no hay ninguno antes de {@code maxDistance}
     */
    public static BlockVector3 first(
            double originX, double originY, double originZ,
            double directionX, double directionY, double directionZ,
            double maxDistance,
            Predicate<BlockVector3> hit
    ) {
        double length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
        if (length < 1.0E-9) {
            return null;
        }
        double dx = directionX / length;
        double dy = directionY / length;
        double dz = directionZ / length;
        int x = (int) Math.floor(originX);
        int y = (int) Math.floor(originY);
        int z = (int) Math.floor(originZ);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double deltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double deltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double deltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double nextX = dx == 0 ? Double.POSITIVE_INFINITY : (dx > 0 ? x + 1 - originX : originX - x) * deltaX;
        double nextY = dy == 0 ? Double.POSITIVE_INFINITY : (dy > 0 ? y + 1 - originY : originY - y) * deltaY;
        double nextZ = dz == 0 ? Double.POSITIVE_INFINITY : (dz > 0 ? z + 1 - originZ : originZ - z) * deltaZ;
        double travelled = 0.0;
        while (travelled <= maxDistance) {
            BlockVector3 current = BlockVector3.at(x, y, z);
            if (hit.test(current)) {
                return current;
            }
            if (nextX <= nextY && nextX <= nextZ) {
                travelled = nextX;
                nextX += deltaX;
                x += stepX;
            } else if (nextY <= nextZ) {
                travelled = nextY;
                nextY += deltaY;
                y += stepY;
            } else {
                travelled = nextZ;
                nextZ += deltaZ;
                z += stepZ;
            }
        }
        return null;
    }
}
