package com.komainu.moxia.region;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
}
