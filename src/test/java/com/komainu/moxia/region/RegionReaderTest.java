package com.komainu.moxia.region;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La lectura troceada de la captura: por tramos, con el plazo de cada tick, y la misma foto que leyendo de una vez. */
class RegionReaderTest {
    private static final long US = 1_000L;

    /** Mundo de mentira: cada lectura gasta 1 µs del reloj. */
    private static final class World implements RegionReader.BlockSource {
        private long now;
        private final Map<Integer, Integer> described = new HashMap<>();

        @Override
        public int rawId(int x, int y, int z) {
            now += US;
            return y < 2 ? 1 : (x + z) % 5 == 0 ? 7 : 0;
        }

        @Override
        public RegionSnapshot.StateInfo describe(int rawId, int x, int y, int z) {
            described.merge(rawId, 1, Integer::sum);
            return new RegionSnapshot.StateInfo(rawId, null, "test:" + rawId, rawId == 0, false, rawId == 1);
        }

        @Override
        public int light(int x, int y, int z) {
            return (y & 15) << 4 | (15 - (y & 15));
        }
    }

    @Test
    void readsTheWholeBoxAcrossTicksWithinEachDeadline() {
        World world = new World();
        RegionReader reader = new RegionReader(world, (x, y, z) -> x >= 2 && x <= 8, 0, 0, 0, 64, 4, 40, true);
        int ticks = 0;
        long worst = 0;
        while (!reader.done()) {
            long start = world.now;
            reader.step(Integer.MAX_VALUE, start + 50 * US, () -> world.now);
            worst = Math.max(worst, world.now - start);
            ticks++;
            if (!reader.done()) {
                assertThrows(IllegalStateException.class, reader::build, "a medias no hay foto");
            }
        }
        assertTrue(ticks > 5, "va repartida entre ticks");
        // El reloj se mira cada 1 024 bloques: como mucho un tramo de más.
        assertTrue(worst <= 50 * US + RegionReader.CHECK_EVERY * US);
        RegionSnapshot snapshot = reader.build();
        assertEquals(64 * 4 * 40, reader.read());
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 40; z++) {
                for (int x = 0; x < 64; x++) {
                    int expected = y < 2 ? 1 : (x + z) % 5 == 0 ? 7 : 0;
                    assertEquals(expected, snapshot.rawId(x, y, z));
                    assertEquals(x >= 2 && x <= 8, snapshot.selected(x, y, z));
                    assertEquals(y & 15, snapshot.blockLight(x, y, z));
                    assertEquals(15 - (y & 15), snapshot.skyLight(x, y, z));
                }
            }
        }
        assertEquals(Map.of(0, 1, 1, 1, 7, 1), world.described, "cada estado se describe una sola vez");
    }

    @Test
    void stoppingHalfwayLeavesNothingBuilt() {
        World world = new World();
        RegionReader reader = new RegionReader(world, (x, y, z) -> true, -5, 60, -5, 64, 64, 64, false);
        assertFalse(reader.step(2_048, Long.MAX_VALUE, () -> 0L));
        assertEquals(2_048, reader.read());
        // Cancelar es dejar de llamar a step: no hay foto ni cálculo.
        assertThrows(IllegalStateException.class, reader::build);
    }

    /** Mundo de mentira que apunta en qué columna de chunk cae cada lectura y cuándo se mira el reloj. */
    private static final class Chunks implements RegionReader.BlockSource {
        private final List<Long> reads = new ArrayList<>();
        private final List<Integer> looks = new ArrayList<>();

        @Override
        public int rawId(int x, int y, int z) {
            reads.add((long) Math.floorDiv(x, 16) << 32 ^ (Math.floorDiv(z, 16) & 0xFFFFFFFFL));
            return 0;
        }

        @Override
        public RegionSnapshot.StateInfo describe(int rawId, int x, int y, int z) {
            return new RegionSnapshot.StateInfo(rawId, null, "test:" + rawId, true, false, false);
        }

        long clock() {
            looks.add(reads.size());
            return 0L;
        }
    }

    /**
     * Leer un bloque de un chunk sin cargar lo carga en el hilo del servidor. Por capas de la caja entera, cada capa
     * volvía a cruzar todos los chunks (los sin cargar se cargaban y se soltaban una vez por capa) y entre dos miradas
     * al reloj podían entrar decenas. Ahora cada chunk se lee entero de una vez, y se mira el reloj al entrar en él.
     */
    @Test
    void eachChunkIsReadWholeAndTheClockIsCheckedBeforeEnteringIt() {
        Chunks world = new Chunks();
        // De x = -7 a 56 y de z = 3 a 66: 5 × 5 columnas de chunk, 6 capas.
        RegionReader reader = new RegionReader(world, (x, y, z) -> true, -7, 60, 3, 64, 6, 64, false);
        while (!reader.done()) {
            reader.step(Integer.MAX_VALUE, 1L, world::clock);
        }
        assertEquals(64 * 6 * 64, world.reads.size());
        int changes = 0;
        for (int index = 1; index < world.reads.size(); index++) {
            if (!world.reads.get(index).equals(world.reads.get(index - 1))) {
                changes++;
            }
        }
        assertEquals(25 - 1, changes, "se pasa por cada columna de chunk una sola vez");
        List<Integer> marks = new ArrayList<>(world.looks);
        marks.add(world.reads.size());
        for (int look = 0; look + 1 < marks.size(); look++) {
            long first = world.reads.get(Math.min(marks.get(look), world.reads.size() - 1));
            for (int index = marks.get(look); index < marks.get(look + 1); index++) {
                assertEquals(first, world.reads.get(index), "entre dos miradas al reloj se entra en otro chunk");
            }
        }
    }
}
