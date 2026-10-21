package com.komainu.moxia.editor;

import com.komainu.moxia.edit.BlockBounds;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * La selección mágica del Studio: los bloques iguales al pulsado y conectados con él por sus caras, hasta
 * {@link #LIMIT} bloques y a {@link #RANGE} del pulsado como mucho, sin entrar en chunks sin cargar. Java puro: el
 * mundo llega por {@link World}.
 */
final class MagicArea {
    static final int LIMIT = 20_000;
    static final int RANGE = 96;
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private MagicArea() {
    }

    /** Lo que se pregunta al mundo. */
    interface World {
        /** Dentro de la altura del mundo y en un chunk cargado: se lee sin cargar ni generar nada. */
        boolean readable(int x, int y, int z);

        /** El estado del bloque, por su id; solo se pide de bloques legibles. */
        int state(int x, int y, int z);

        boolean isAir(int state);
    }

    /** La caja de lo conectado y cuántos bloques son. */
    record Result(BlockBounds bounds, int blocks) {
    }

    static Result flood(int x, int y, int z, World world) {
        if (!world.readable(x, y, z)) {
            throw new IllegalArgumentException("Ese bloque está fuera del mundo o en una zona sin cargar");
        }
        int target = world.state(x, y, z);
        if (world.isAir(target)) {
            throw new IllegalArgumentException("La selección mágica necesita un bloque sólido");
        }
        ArrayDeque<int[]> pending = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        pending.add(new int[]{x, y, z});
        visited.add(BlockPos.asLong(x, y, z));
        int minX = x;
        int minY = y;
        int minZ = z;
        int maxX = x;
        int maxY = y;
        int maxZ = z;
        while (!pending.isEmpty() && visited.size() < LIMIT) {
            int[] current = pending.removeFirst();
            minX = Math.min(minX, current[0]);
            minY = Math.min(minY, current[1]);
            minZ = Math.min(minZ, current[2]);
            maxX = Math.max(maxX, current[0]);
            maxY = Math.max(maxY, current[1]);
            maxZ = Math.max(maxZ, current[2]);
            for (int[] face : FACES) {
                int nextX = current[0] + face[0];
                int nextY = current[1] + face[1];
                int nextZ = current[2] + face[2];
                if (Math.abs(nextX - x) > RANGE || Math.abs(nextY - y) > RANGE || Math.abs(nextZ - z) > RANGE
                        || !world.readable(nextX, nextY, nextZ) || world.state(nextX, nextY, nextZ) != target
                        || !visited.add(BlockPos.asLong(nextX, nextY, nextZ))) {
                    continue;
                }
                pending.addLast(new int[]{nextX, nextY, nextZ});
            }
        }
        return new Result(new BlockBounds(minX, minY, minZ, maxX, maxY, maxZ), visited.size());
    }
}
