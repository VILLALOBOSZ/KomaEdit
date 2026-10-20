package com.komainu.moxia.recipe;

import com.komainu.moxia.path.PathSnapshot;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.List;

/**
 * «Pegar al terreno» sin Minecraft: cada punto del camino baja hasta el suelo (o sube hasta el techo, o va a la
 * superficie más cercana) buscando hasta {@code conformRadius} bloques, y queda {@code surfaceOffset} bloques por
 * encima. Lo usan el servidor al construir, el holograma del Studio (con el mundo del cliente) y las miniaturas (con un
 * terreno de mentira), así que los tres dan lo mismo.
 */
public final class SurfaceConform {
    /** Lo que se mira del mundo: si un bloque es sólido o es aire (lo que no está cargado no es ninguna de las dos). */
    public interface Terrain {
        boolean solid(int x, int y, int z);

        boolean empty(int x, int y, int z);
    }

    private SurfaceConform() {
    }

    public static PathSnapshot project(Terrain terrain, PathSnapshot original, RecipeExtensions extensions) {
        if (extensions.surfaceMode() == SurfaceMode.NONE) {
            return original;
        }
        List<BlockVector3> projected = new ArrayList<>(original.points().size());
        for (BlockVector3 point : original.points()) {
            projected.add(switch (extensions.surfaceMode()) {
                case NONE -> point;
                case FLOOR -> floor(terrain, point, extensions.conformRadius(), extensions.surfaceOffset());
                case CEILING -> ceiling(terrain, point, extensions.conformRadius(), extensions.surfaceOffset());
                case NEAREST -> nearest(terrain, point, extensions.conformRadius(), extensions.surfaceOffset());
            });
        }
        long unique = projected.stream().distinct().count();
        if (unique < (original.closed() ? 3 : 2)) {
            throw new IllegalArgumentException("El conformado proyectó varios nodos en el mismo bloque");
        }
        return new PathSnapshot(projected, original.closed(), original.source() + " · conformado");
    }

    static BlockVector3 floor(Terrain terrain, BlockVector3 source, int radius, int offset) {
        for (int distance = 0; distance <= radius; distance++) {
            int y = source.y() - distance;
            if (terrain.solid(source.x(), y, source.z()) && terrain.empty(source.x(), y + 1, source.z())) {
                return BlockVector3.at(source.x(), y + 1 + offset, source.z());
            }
        }
        return source;
    }

    static BlockVector3 ceiling(Terrain terrain, BlockVector3 source, int radius, int offset) {
        for (int distance = 0; distance <= radius; distance++) {
            int y = source.y() + distance;
            if (terrain.solid(source.x(), y, source.z()) && terrain.empty(source.x(), y - 1, source.z())) {
                return BlockVector3.at(source.x(), y - 1 - offset, source.z());
            }
        }
        return source;
    }

    static BlockVector3 nearest(Terrain terrain, BlockVector3 source, int radius, int offset) {
        int[][] directions = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
        BlockVector3 best = source;
        int[] bestDirection = {0, 1, 0};
        double bestDistance = Double.MAX_VALUE;
        for (int shell = 0; shell <= radius; shell++) {
            for (int x = -shell; x <= shell; x++) {
                for (int y = -shell; y <= shell; y++) {
                    for (int z = -shell; z <= shell; z++) {
                        if (Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)) != shell) {
                            continue;
                        }
                        double distance = x * x + y * y + z * z;
                        if (distance >= bestDistance || distance > radius * radius) {
                            continue;
                        }
                        int bx = source.x() + x;
                        int by = source.y() + y;
                        int bz = source.z() + z;
                        if (!terrain.solid(bx, by, bz)) {
                            continue;
                        }
                        for (int[] direction : directions) {
                            int ox = bx + direction[0];
                            int oy = by + direction[1];
                            int oz = bz + direction[2];
                            if (terrain.empty(ox, oy, oz)) {
                                double faceDistance = square(ox - source.x()) + square(oy - source.y())
                                        + square(oz - source.z());
                                if (faceDistance < bestDistance) {
                                    bestDistance = faceDistance;
                                    best = BlockVector3.at(ox, oy, oz);
                                    bestDirection = direction;
                                }
                            }
                        }
                    }
                }
            }
            if (bestDistance <= (shell + 1.0) * (shell + 1.0)) {
                break;
            }
        }
        return bestDistance == Double.MAX_VALUE ? source : best.add(bestDirection[0] * offset,
                bestDirection[1] * offset, bestDirection[2] * offset);
    }

    private static double square(int value) {
        return (double) value * value;
    }
}
