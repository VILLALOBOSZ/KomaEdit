package com.komainu.moxia.rebuild;

import com.komainu.moxia.edit.PackedBlockPlan;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El historial de colocación: orden, volver a colocar, olvidar, el tope y guardar y leer. */
class PlacementLogTest {
    private static long at(int x) {
        return PackedBlockPlan.pack(x, 64, -x);
    }

    @Test
    void laterPlacementsRankHigher() {
        PlacementLog log = new PlacementLog(10);
        log.record(at(1));
        log.record(at(2));
        log.record(at(3));
        assertTrue(log.rank(at(1)) < log.rank(at(2)));
        assertTrue(log.rank(at(2)) < log.rank(at(3)));
        assertEquals(RebuildModel.NO_HISTORY, log.rank(at(4)));
        // Volver a colocar pone el bloque al final; romperlo lo olvida.
        log.record(at(1));
        assertTrue(log.rank(at(1)) > log.rank(at(3)));
        log.forget(at(2));
        assertEquals(RebuildModel.NO_HISTORY, log.rank(at(2)));
        assertArrayEquals(new long[]{at(3), at(1)}, log.positions());
        assertEquals(2, log.size());
    }

    @Test
    void whenFullTheOldestIsForgotten() {
        PlacementLog log = new PlacementLog(3);
        for (int x = 0; x < 5; x++) {
            log.record(at(x));
        }
        assertEquals(RebuildModel.NO_HISTORY, log.rank(at(0)));
        assertEquals(RebuildModel.NO_HISTORY, log.rank(at(1)));
        assertArrayEquals(new long[]{at(2), at(3), at(4)}, log.positions());
        // Lo vuelto a colocar no se pierde por el hueco viejo que ocupaba en el anillo.
        log.record(at(2));
        log.record(at(5));
        assertArrayEquals(new long[]{at(4), at(2), at(5)}, log.positions());
        assertTrue(log.rank(at(2)) != RebuildModel.NO_HISTORY);
    }

    @Test
    void savedAndReadInTheSameOrder() throws IOException {
        PlacementLog log = new PlacementLog(100);
        for (int x = 0; x < 50; x++) {
            log.record(at(x % 30));
        }
        assertTrue(log.dirty());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        log.write(output);
        assertFalse(log.dirty());
        PlacementLog read = PlacementLog.read(new ByteArrayInputStream(output.toByteArray()), 100);
        assertArrayEquals(log.positions(), read.positions());
        assertFalse(read.dirty());
        // Con menos capacidad se quedan las más recientes.
        PlacementLog small = PlacementLog.read(new ByteArrayInputStream(output.toByteArray()), 5);
        long[] all = log.positions();
        assertArrayEquals(java.util.Arrays.copyOfRange(all, all.length - 5, all.length), small.positions());
        assertThrows(IOException.class, () -> PlacementLog.read(new ByteArrayInputStream(new byte[]{1, 2, 3}), 5));
    }

    @Test
    void zeroCapacityRemembersNothing() {
        PlacementLog off = new PlacementLog(0);
        off.record(at(1));
        assertEquals(RebuildModel.NO_HISTORY, off.rank(at(1)));
        assertEquals(0, off.positions().length);
        assertThrows(IllegalArgumentException.class, () -> new PlacementLog(-1));
    }
}
