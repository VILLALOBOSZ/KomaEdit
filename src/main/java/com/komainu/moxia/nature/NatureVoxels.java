package com.komainu.moxia.nature;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.GeometryLimitException;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Arrays;

/**
 * Resultado de un generador de árboles o rocas: cada celda con su papel (madera, raíz, hoja, roca…) y lo que hace
 * falta para elegir su estado sin volver a calcular nada: el eje del tronco, la distancia de la hoja a la madera y el
 * tono de la roca. Es Java puro; el comando lo convierte en bloques en el worker.
 */
public final class NatureVoxels {
    /** Papel de una celda. */
    public enum Role {
        /** Tronco y ramas, con el eje según la dirección de la rama. */
        WOOD,
        /** Raíces que se agarran al terreno. */
        ROOT,
        /** Copa: a 6 pasos o menos de la madera, como pide Minecraft. */
        LEAF,
        /** Hojas colgantes (cortinas del sauce, frondas de la palmera): unidas a la copa, a 12 pasos o menos. */
        HANGING,
        /** Cuerpo de una roca, con su tono. */
        ROCK,
        /** Musgo sobre una roca. */
        MOSS;

        private static final Role[] VALUES = values();

        public boolean wood() {
            return this == WOOD || this == ROOT;
        }

        public boolean foliage() {
            return this == LEAF || this == HANGING;
        }
    }

    /** Distancia máxima de una hoja de copa a la madera: la de Minecraft (a partir de 7 se caen sin persistent). */
    public static final int LEAF_REACH = 6;
    /** Distancia máxima de una hoja colgante: con {@code persistent=true} no se caen aunque pasen de 6. */
    public static final int HANGING_REACH = 12;

    private static final int ROLE_MASK = 0x7;
    private static final int AXIS_SHIFT = 3;
    private static final int DISTANCE_SHIFT = 5;
    private static final int SHADE_SHIFT = 9;
    private static final int GROUND_FLAG = 1 << 17;

    private final Long2IntOpenHashMap cells = new Long2IntOpenHashMap();
    private final int limit;

    public NatureVoxels(int limit) {
        this.limit = limit;
        cells.defaultReturnValue(-1);
    }

    public static long key(int x, int y, int z) {
        return PackedBlockPlan.pack(x, y, z);
    }

    /** Añade la celda si estaba libre; devuelve si la ha añadido. */
    public boolean add(int x, int y, int z, Role role, int axis, int distance, int shade, boolean ground) {
        long key = key(x, y, z);
        if (cells.containsKey(key)) {
            return false;
        }
        set(key, role, axis, distance, shade, ground);
        return true;
    }

    /** Pone la celda aunque ya existiera. */
    public void set(long key, Role role, int axis, int distance, int shade, boolean ground) {
        int info = role.ordinal() | (Math.clamp(axis, 0, 3) << AXIS_SHIFT)
                | (Math.clamp(distance, 0, 15) << DISTANCE_SHIFT) | (Math.clamp(shade, 0, 255) << SHADE_SHIFT)
                | (ground ? GROUND_FLAG : 0);
        if (cells.put(key, info) == -1 && cells.size() > limit) {
            throw new GeometryLimitException(limit);
        }
    }

    public void remove(long key) {
        cells.remove(key);
    }

    public boolean contains(long key) {
        return cells.containsKey(key);
    }

    public boolean contains(int x, int y, int z) {
        return cells.containsKey(key(x, y, z));
    }

    /** Información empaquetada de la celda, o -1 si no está. */
    public int info(long key) {
        return cells.get(key);
    }

    public Role role(int x, int y, int z) {
        int info = cells.get(key(x, y, z));
        return info < 0 ? null : role(info);
    }

    public static Role role(int info) {
        return Role.VALUES[info & ROLE_MASK];
    }

    /** 0 = x, 1 = y, 2 = z. */
    public static int axis(int info) {
        return (info >> AXIS_SHIFT) & 0x3;
    }

    /** Pasos hasta la madera por hojas (0 en la madera). */
    public static int distance(int info) {
        return (info >> DISTANCE_SHIFT) & 0xF;
    }

    /** Tono de 0 (oscuro) a 255 (claro) en las rocas. */
    public static int shade(int info) {
        return (info >> SHADE_SHIFT) & 0xFF;
    }

    /** ¿Puede ocupar suelo blando (base del tronco, raíces, rocas hundidas)? */
    public static boolean ground(int info) {
        return (info & GROUND_FLAG) != 0;
    }

    public int size() {
        return cells.size();
    }

    public int count(Role role) {
        int count = 0;
        for (int info : cells.values()) {
            if (role(info) == role) {
                count++;
            }
        }
        return count;
    }

    /** Claves ordenadas: el mismo resultado se recorre siempre igual. */
    public long[] sortedKeys() {
        long[] keys = cells.keySet().toLongArray();
        Arrays.sort(keys);
        return keys;
    }

    public int limit() {
        return limit;
    }
}
