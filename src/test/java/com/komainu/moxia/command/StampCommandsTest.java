package com.komainu.moxia.command;

import com.komainu.moxia.terrain.stamp.StampCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que se escribe tras {@code /komaedit sello capturar}. */
class StampCommandsTest {
    @Test
    void theNameComesFirstAndTheDescriptionKeepsItsCapitals() {
        StampCommands.Capture capture = StampCommands.Capture.parse("valle_norte El valle del Río Negro -w");
        assertEquals("valle_norte", capture.name());
        assertEquals("El valle del Río Negro", capture.description());
        assertTrue(capture.keepWater());
        assertFalse(capture.replace());
        assertNull(capture.category());
    }

    @Test
    void flagsGoAnywhereAndTheNameIsOptional() {
        StampCommands.Capture capture = StampCommands.Capture.parse("-c dunas -r");
        assertNull(capture.name(), "sin nombre, el servidor pone mio_1, mio_2…");
        assertEquals(StampCategory.DUNES, capture.category());
        assertTrue(capture.replace());
        assertEquals("", capture.description());
        assertThrows(IllegalArgumentException.class, () -> StampCommands.Capture.parse("x -c"));
        assertThrows(IllegalArgumentException.class, () -> StampCommands.Capture.parse("x -c plano"));
        assertThrows(IllegalArgumentException.class, () -> StampCommands.Capture.parse("x " + "a".repeat(200)));
        assertEquals("capturar", StampCommands.canonical("capture"));
        assertEquals("borrar", StampCommands.canonical("delete"));
    }
}
