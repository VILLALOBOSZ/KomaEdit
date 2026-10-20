package com.komainu.moxia.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasteFlagsTest {
    @Test
    void readsTheFlagsOfPaste() {
        assertEquals(new PasteCommands.Flags(false, false), PasteCommands.Flags.parse(""));
        assertEquals(new PasteCommands.Flags(true, false), PasteCommands.Flags.parse("-a"));
        assertEquals(new PasteCommands.Flags(true, true), PasteCommands.Flags.parse(" -o  -A "));
        assertEquals(new PasteCommands.Flags(true, true), PasteCommands.Flags.parse("-ao"));
        assertThrows(IllegalArgumentException.class, () -> PasteCommands.Flags.parse("-x"));
    }
}
