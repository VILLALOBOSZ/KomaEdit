package com.komainu.moxia.command;

import com.komainu.moxia.capability.CapabilityLimits;
import com.komainu.moxia.capability.CapabilityOption;
import com.komainu.moxia.capability.CapabilityRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code capability option flyspeed on} ponía ×0,1 y {@code off}, 0; y la regla dejaba bajar hasta ×0,1 aunque el
 * menú, la tarjeta y la documentación dicen que va de ×0,5 a ×20.
 */
class CapabilityFlySpeedTest {
    @Test
    void flySpeedGoesFromHalfToTheServerCap() {
        assertEquals(25, CapabilityCommands.value(CapabilityOption.FLY_SPEED, "2.5"));
        assertEquals(10, CapabilityCommands.value(CapabilityOption.FLY_SPEED, "off"), "off es la normal");
        assertThrows(IllegalArgumentException.class, () -> CapabilityCommands.value(CapabilityOption.FLY_SPEED, "on"));
        assertEquals(5, CapabilityRules.flySpeed(1, CapabilityLimits.DEFAULT), "como mucho ×0,5 de lento");
        assertEquals(CapabilityLimits.DEFAULT.maxFlySpeed(), CapabilityRules.flySpeed(1000, CapabilityLimits.DEFAULT));
    }
}
