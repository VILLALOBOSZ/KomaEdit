package com.komainu.moxia.generator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorArgsTest {
    @Test
    void groupedSwitchesAndValuedFlags() {
        GeneratorArgs args = GeneratorArgs.parse("stone 5 10 -dho -z 2", "z", "dhos");
        assertEquals(3, args.count());
        assertTrue(args.has('d') && args.has('h') && args.has('o'));
        assertFalse(args.has('s'));
        assertEquals("2", args.value('z'));
    }

    @Test
    void valuedFlagMustCloseTheGroup() {
        assertEquals("3", GeneratorArgs.parse("x -cz 3", "z", "c").value('z'));
        assertThrows(IllegalArgumentException.class, () -> GeneratorArgs.parse("x -zc 3", "z", "c"));
        assertThrows(IllegalArgumentException.class, () -> GeneratorArgs.parse("x -z", "z", ""));
    }

    @Test
    void firstValueAndUnknownGroupsStayPositional() {
        GeneratorArgs args = GeneratorArgs.parse("-##Grayscale -5 -stone,dirt -h", "", "h");
        assertEquals("-##Grayscale", args.positional(0));
        assertEquals("-5", args.positional(1));
        assertEquals("-stone,dirt", args.positional(2));
        assertTrue(args.has('h'));
    }

    @Test
    void parenthesesAndQuotesGroupWords() {
        assertEquals(List.of("a", "Perlin(F:0.02, fO:3)", "\"x * 2\""),
                GeneratorArgs.tokenize("a Perlin(F:0.02, fO:3) \"x * 2\""));
        GeneratorArgs args = GeneratorArgs.parse("x -x \"x * 2 + z\"", "x", "");
        assertEquals("x * 2 + z", args.value('x'));
        assertThrows(IllegalArgumentException.class, () -> GeneratorArgs.tokenize("a \"b"));
    }

    @Test
    void optionalValueOnlyWhenAccepted() {
        GeneratorArgs withMode = GeneratorArgs.parse("x 10 -h above", "", "", "h", FlowField.Distribution::known);
        assertEquals("above", withMode.value('h'));
        GeneratorArgs bare = GeneratorArgs.parse("x 10 -h 32", "", "", "h", FlowField.Distribution::known);
        assertTrue(bare.has('h'));
        assertNull(bare.value('h'));
        assertEquals("32", bare.positional(2));
    }

    @Test
    void repeatedFlagIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> GeneratorArgs.parse("x -h -h", "", "h"));
    }

    @Test
    void numbersAndVectors() {
        assertEquals(0.5, GeneratorArgs.number("0,5", 0, 1, "n"));
        assertThrows(IllegalArgumentException.class, () -> GeneratorArgs.number("2", 0, 1, "n"));
        assertThrows(IllegalArgumentException.class, () -> GeneratorArgs.integer("1.5", 0, 3, "n"));
        assertArrayEquals(new double[]{0, -1, 0}, GeneratorArgs.vector("0,-1,0", 10, "g"));
        assertArrayEquals(new double[]{2, 2, 2}, GeneratorArgs.vector("2", 10, "g"));
        assertArrayEquals(new double[]{1, 2, 3}, GeneratorArgs.vector("(1,2,3)", 10, "g"));
        assertThrows(IllegalArgumentException.class, () -> GeneratorArgs.vector("1,2", 10, "g"));
    }
}
