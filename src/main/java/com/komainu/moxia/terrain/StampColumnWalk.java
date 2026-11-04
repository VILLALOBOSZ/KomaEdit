package com.komainu.moxia.terrain;

import java.util.function.BooleanSupplier;

/**
 * El recorrido de las columnas al capturar un sello, repartido entre ticks. Antes de leer una columna se carga su
 * chunk si falta: la altura del mapa de un chunk sin cargar es el fondo del mundo, así que la columna salía «sin
 * suelo», el chunk no llegaba a cargarse y el sello tenía huecos (y se leía a una columna por tick). Tras cargar un
 * chunk se para hasta el tick siguiente. Java puro: el mundo llega por {@link World}.
 */
final class StampColumnWalk {
    private StampColumnWalk() {
    }

    interface World {
        boolean loaded(int chunkX, int chunkZ);

        /** Carga (o genera) el chunk en el hilo del servidor. */
        void load(int chunkX, int chunkZ);

        void read(int x, int z, int column, int row);
    }

    /**
     * Lee desde la columna {@code next} hasta acabar, cargar un chunk o pasar el plazo (mirado cada 64 columnas).
     *
     * @return la siguiente columna por leer ({@code total} si ha acabado)
     */
    static int step(int next, int total, int sizeX, int minimumX, int minimumZ, World world, BooleanSupplier late) {
        while (next < total) {
            int x = minimumX + next % sizeX;
            int z = minimumZ + next / sizeX;
            boolean loaded = world.loaded(x >> 4, z >> 4);
            if (!loaded) {
                world.load(x >> 4, z >> 4);
            }
            world.read(x, z, next % sizeX, next / sizeX);
            next++;
            if (!loaded || (next & 63) == 0 && late.getAsBoolean()) {
                return next;
            }
        }
        return next;
    }
}
