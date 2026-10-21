package com.komainu.moxia.rebuild;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que se escribe tras {@code /komaedit rebuild}: el orden, el ritmo y las banderas. */
class RebuildSettingsTest {
    private static final int[] FEET = {100, 64, -20};

    private static RebuildSettings parse(String input) {
        return RebuildSettings.parse(input, FEET);
    }

    @Test
    void withNothingItIsTheBuilderAtFourBlocksPerTick() {
        RebuildSettings settings = parse("");
        assertEquals(RebuildOrder.BUILDER, settings.order());
        assertEquals(4.0, settings.pace().blocksPerTick());
        assertEquals(20.0, settings.pace().leadIn(), "un segundo de obra vacía");
        assertFalse(settings.particles() || settings.sound() || settings.skipAir());
        assertNull(settings.seed());
        assertNull(settings.center());
        assertNull(settings.location());
    }

    @Test
    void ordersReadInSpanishEnglishAndWithoutAccents() {
        assertEquals(RebuildOrder.LAYERS, parse("capas").order());
        assertEquals(RebuildOrder.LAYERS, parse("Layers").order());
        assertEquals(RebuildOrder.WAVES, parse("ondas").order());
        assertEquals(RebuildOrder.AXIS, parse("eje").order());
        assertEquals(RebuildOrder.RANDOM, parse("azar").order());
        assertEquals(RebuildOrder.BUILDER, parse("constructor").order());
        assertEquals(RebuildOrder.HISTORY, parse("HISTORIAL").order());
        assertThrows(IllegalArgumentException.class, () -> parse("espiral"));
        assertThrows(IllegalArgumentException.class, () -> parse("capas ondas"));
    }

    @Test
    void timesAreSecondsOrTicks() {
        RebuildSettings settings = parse("capas -p 1.5 -i 0,5 -d 10t -w 2s");
        assertEquals(30.0, settings.pace().layerPause());
        assertEquals(10.0, settings.pace().sameDelay());
        assertEquals(10.0, settings.pace().otherDelay());
        assertEquals(40.0, settings.pace().leadIn());
        assertEquals(600.0, parse("-t 30").pace().durationTicks());
        assertEquals(0.5, parse("-b 0.5").pace().blocksPerTick());
        assertThrows(IllegalArgumentException.class, () -> parse("-b 4 -t 30"), "uno u otro");
        assertThrows(IllegalArgumentException.class, () -> parse("-t 0"));
        assertThrows(IllegalArgumentException.class, () -> parse("-b mucho"));
        assertThrows(IllegalArgumentException.class, () -> parse("-p -2"));
        assertThrows(IllegalArgumentException.class, () -> parse("-b"), "falta el valor");
    }

    @Test
    void axisCenterAndSeedGoWithTheirOrder() {
        RebuildSettings axis = parse("-e -z");
        assertEquals(RebuildOrder.AXIS, axis.order(), "-e solo ya es el eje");
        assertEquals(RebuildAxis.Z_NEGATIVE, axis.axis());
        assertEquals(RebuildAxis.X_POSITIVE, parse("eje").axis());
        RebuildSettings waves = parse("-c ~,~-1,~5");
        assertEquals(RebuildOrder.WAVES, waves.order());
        assertArrayEquals(new int[]{100, 63, -15}, waves.center());
        assertArrayEquals(new int[]{1, 2, 3}, parse("ondas -c 1,2,3").center());
        assertEquals(42L, parse("azar -s 42").seed());
        assertEquals(parse("azar -s casa").seed(), parse("azar -s CASA").seed(), "una palabra vale de semilla");
        assertEquals(7L, parse("azar").withSeed(7).options(0L).seed());
        assertEquals(9L, parse("azar").options(9L).seed());
        assertThrows(IllegalArgumentException.class, () -> parse("capas -e x"));
        assertThrows(IllegalArgumentException.class, () -> parse("capas -c 1,2,3"));
        assertThrows(IllegalArgumentException.class, () -> parse("capas -s 1"));
        assertThrows(IllegalArgumentException.class, () -> parse("-e diagonal"));
        assertThrows(IllegalArgumentException.class, () -> parse("ondas -c 1,2"));
    }

    @Test
    void switchesAndPlace() {
        RebuildSettings settings = parse("constructor -f -n -a -l ~10,~,~");
        assertTrue(settings.particles());
        assertTrue(settings.sound());
        assertTrue(settings.skipAir());
        assertArrayEquals(new int[]{110, 64, -20}, settings.location());
        assertThrows(IllegalArgumentException.class, () -> parse("-x"), "bandera desconocida");
    }
}
