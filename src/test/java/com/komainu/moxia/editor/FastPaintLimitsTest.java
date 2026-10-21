package com.komainu.moxia.editor;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La pintura rápida va por paquetes del cliente (uno por pasada): un cliente modificado puede mandar miles por
 * segundo con centros muy separados, y un trazo nunca termina si no manda el final.
 */
class FastPaintLimitsTest {
    @Test
    void farApartDabsCannotPileUpWithoutEnd() {
        DabTrail trail = new DabTrail(12);
        for (int packet = 0; packet < 20_000; packet++) {
            // Cada paquete lejos del anterior: hasta 128 pasadas intermedias nuevas.
            trail.add(new BlockPos(packet % 2 == 0 ? 0 : 3_000, 64, packet * 40));
        }
        assertTrue(trail.pending() <= DabTrail.MAX_PENDING,
                trail.pending() + " pasadas en espera; como mucho " + DabTrail.MAX_PENDING);
    }

    @Test
    void aNormalDragKeepsEveryDabOnceAndInOrder() {
        DabTrail trail = new DabTrail(4);
        for (int x = 0; x <= 40; x++) {
            trail.add(new BlockPos(x, 64, 0));
        }
        trail.add(new BlockPos(40, 64, 0));
        Set<BlockPos> seen = new HashSet<>();
        int previous = -1;
        BlockPos dab;
        while ((dab = trail.poll()) != null) {
            assertTrue(seen.add(dab), "sin repetir " + dab);
            assertTrue(dab.getX() > previous, "en orden");
            previous = dab.getX();
        }
        assertEquals(41, seen.size(), "una pasada por bloque arrastrado");
    }

    @Test
    void aStrokeStopsAtTheServerLimitToo() {
        assertEquals(250_000, FastPaintService.strokeLimit(-1, 250_000), "sin //limit manda el del servidor");
        assertEquals(1_000, FastPaintService.strokeLimit(1_000, 250_000), "el //limit, si es menor");
        assertEquals(250_000, FastPaintService.strokeLimit(9_000_000, 250_000));
        assertEquals(-1, FastPaintService.strokeLimit(-1, Long.MAX_VALUE), "sin límite con el permiso de ilimitado");
        assertEquals(5_000, FastPaintService.strokeLimit(5_000, Long.MAX_VALUE));
    }
}
