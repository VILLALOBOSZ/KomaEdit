package com.komainu.moxia.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTokensTest {
    @Test
    void separatesFlagsOptionsAndArguments() {
        CommandTokens tokens = CommandTokens.parse("world  mi_paleta 12 -d north -f", Set.of("d"));

        assertEquals(List.of("world", "mi_paleta", "12"), tokens.positional());
        assertTrue(tokens.flag("f"));
        assertFalse(tokens.flag("a"));
        assertEquals("north", tokens.option("d", "me"));
        assertEquals("me", CommandTokens.parse("world x", Set.of("d")).option("d", "me"));
    }

    @Test
    void invertedPalettesAndNegativeNumbersAreNotFlags() {
        CommandTokens tokens = CommandTokens.parse("-##Grayscale -[stone,dirt] -3 -s", Set.of());

        assertEquals(List.of("-##Grayscale", "-[stone,dirt]", "-3"), tokens.positional());
        assertTrue(tokens.flag("s"));
    }

    @Test
    void reportsMissingValuesUnknownFlagsAndExtraArguments() {
        assertThrows(IllegalArgumentException.class, () -> CommandTokens.parse("x -d", Set.of("d")));
        assertThrows(IllegalArgumentException.class, () -> CommandTokens.parse("x -z", Set.of()).allow("f"));
        assertThrows(IllegalArgumentException.class, () -> CommandTokens.parse("a b c", Set.of()).atMost(2));
        assertThrows(IllegalArgumentException.class, () -> CommandTokens.parse("", Set.of()).require(0, "algo"));
        assertEquals(List.of(), CommandTokens.parse("  ", Set.of()).positional());
    }
}
