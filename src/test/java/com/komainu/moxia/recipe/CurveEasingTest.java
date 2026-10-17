package com.komainu.moxia.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurveEasingTest {
    @Test
    void everyCurveKeepsEndpointsAndIsMonotonic() {
        for (CurveEasing easing : CurveEasing.values()) {
            assertEquals(0.0, easing.apply(-1.0), 1.0E-9, easing.name());
            assertEquals(1.0, easing.apply(2.0), 1.0E-9, easing.name());

            double previous = easing.apply(0.0);
            for (int sample = 1; sample <= 100; sample++) {
                double current = easing.apply(sample / 100.0);
                assertTrue(current >= previous, easing.name() + " must be monotonic");
                previous = current;
            }
        }
    }

    @Test
    void resolvesSerializedIds() {
        for (CurveEasing easing : CurveEasing.values()) {
            assertEquals(easing, CurveEasing.byId(easing.id().toUpperCase()));
        }
    }
}
