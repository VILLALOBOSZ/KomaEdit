package com.komainu.moxia.terrain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capturar un sello de una zona con chunks sin cargar (300 × 300, o tras alejarse): la altura del mapa de un chunk
 * sin cargar es el fondo del mundo, así que sus columnas salían «sin suelo» y el sello tenía huecos planos.
 */
class StampColumnWalkTest {
    @Test
    void everyColumnIsReadWithItsChunkLoaded() {
        Set<Long> loaded = new HashSet<>();
        int[] loads = {0};
        int[] blind = {0};
        int[] reads = {0};
        StampColumnWalk.World world = new StampColumnWalk.World() {
            @Override
            public boolean loaded(int chunkX, int chunkZ) {
                return loaded.contains(key(chunkX, chunkZ));
            }

            @Override
            public void load(int chunkX, int chunkZ) {
                loads[0]++;
                loaded.add(key(chunkX, chunkZ));
            }

            @Override
            public void read(int x, int z, int column, int row) {
                reads[0]++;
                if (!loaded.contains(key(x >> 4, z >> 4))) {
                    blind[0]++;
                }
            }
        };
        // 48 × 48 columnas desde (-16, -16): nueve chunks, con coordenadas negativas.
        int total = 48 * 48;
        int next = 0;
        int ticks = 0;
        while (next < total) {
            next = StampColumnWalk.step(next, total, 48, -16, -16, world, () -> false);
            ticks++;
            assertTrue(ticks < 100, "no avanza");
        }
        assertEquals(total, reads[0]);
        assertEquals(0, blind[0], "ninguna columna se lee con su chunk sin cargar");
        assertEquals(9, loads[0], "cada chunk se carga una vez");
        assertEquals(10, ticks, "un chunk cargado por tick y el último tick acaba");
    }

    private static long key(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
    }
}
