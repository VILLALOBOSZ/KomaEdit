package com.komainu.moxia.capability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El punto del clic llega del cliente: con {@code Math.clamp} un {@code NaN} seguía siendo {@code NaN} y decidía la
 * mitad de la losa o el lado de la escalera que se colocaba.
 */
class CapabilityHitTest {
    @Test
    void aHitThatIsNotANumberGoesToTheMiddleOfTheBlock() {
        assertEquals(10.5, CapabilityRules.hitCoordinate(Double.NaN, 10));
        assertEquals(-3.5, CapabilityRules.hitCoordinate(Double.POSITIVE_INFINITY, -4));
        assertEquals(64.5, CapabilityRules.hitCoordinate(Double.NEGATIVE_INFINITY, 64));
    }

    @Test
    void aRealHitStaysInsideItsBlock() {
        assertEquals(10.25, CapabilityRules.hitCoordinate(10.25, 10));
        assertEquals(10.0, CapabilityRules.hitCoordinate(-1.0E9, 10));
        assertEquals(11.0, CapabilityRules.hitCoordinate(1.0E9, 10));
    }
}
