package com.komainu.moxia.capability;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyCycleTest {
    private static final PropertyCycle.Property FACING = new PropertyCycle.Property(
            "facing", List.of("north", "south", "west", "east"), "north");
    private static final PropertyCycle.Property HALF = new PropertyCycle.Property(
            "half", List.of("top", "bottom"), "bottom");
    private static final PropertyCycle.Property SHAPE = new PropertyCycle.Property(
            "shape", List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"), "straight");
    private static final PropertyCycle.Property WATERLOGGED = new PropertyCycle.Property(
            "waterlogged", List.of("true", "false"), "false");

    @Test
    void stairsAreWalkedInPriorityOrderWhateverTheBlockOrder() {
        List<PropertyCycle.Property> ordered = PropertyCycle.ordered(List.of(WATERLOGGED, SHAPE, HALF, FACING));
        assertEquals(List.of("facing", "half", "shape", "waterlogged"),
                ordered.stream().map(PropertyCycle.Property::name).toList());
    }

    @Test
    void unknownPropertiesKeepTheBlockOrderAfterTheKnownOnes() {
        PropertyCycle.Property note = new PropertyCycle.Property("note", List.of("0", "1", "2"), "0");
        PropertyCycle.Property instrument = new PropertyCycle.Property("instrument", List.of("harp", "bass"), "harp");
        PropertyCycle.Property powered = new PropertyCycle.Property("powered", List.of("true", "false"), "false");
        List<PropertyCycle.Property> ordered = PropertyCycle.ordered(List.of(note, instrument, powered));
        assertEquals(List.of("note", "instrument", "powered"),
                ordered.stream().map(PropertyCycle.Property::name).toList());
    }

    @Test
    void halvesOfTwoBlockThingsAreNotEditable() {
        PropertyCycle.Property doorHalf = new PropertyCycle.Property("half", List.of("upper", "lower"), "lower");
        PropertyCycle.Property bedPart = new PropertyCycle.Property("part", List.of("head", "foot"), "foot");
        PropertyCycle.Property open = new PropertyCycle.Property("open", List.of("true", "false"), "false");
        assertFalse(PropertyCycle.editable(doorHalf));
        assertFalse(PropertyCycle.editable(bedPart));
        assertTrue(PropertyCycle.editable(HALF));
        assertEquals(List.of("open"), PropertyCycle.ordered(List.of(doorHalf, bedPart, open)).stream()
                .map(PropertyCycle.Property::name).toList());
        assertFalse(PropertyCycle.editable(new PropertyCycle.Property("single", List.of("only"), "only")));
    }

    /** Girar media cama o medio cofre doble partía el bloque: la otra mitad no los sigue. */
    @Test
    void bedsAndDoubleChestsDoNotTurnHalfOfThemselves() {
        PropertyCycle.Property bedPart = new PropertyCycle.Property("part", List.of("head", "foot"), "head");
        PropertyCycle.Property occupied = new PropertyCycle.Property("occupied", List.of("true", "false"), "false");
        assertEquals(List.of("occupied"), names(PropertyCycle.ordered(List.of(FACING, bedPart, occupied))));
        PropertyCycle.Property left = new PropertyCycle.Property("type", List.of("single", "left", "right"), "left");
        assertEquals(List.of("waterlogged"), names(PropertyCycle.ordered(List.of(FACING, left, WATERLOGGED))));
        PropertyCycle.Property single = new PropertyCycle.Property("type", List.of("single", "left", "right"),
                "single");
        assertEquals(List.of("facing", "waterlogged"),
                names(PropertyCycle.ordered(List.of(FACING, single, WATERLOGGED))), "un cofre suelto sí gira");
        PropertyCycle.Property slab = new PropertyCycle.Property("type", List.of("top", "bottom", "double"), "top");
        assertEquals(List.of("type", "waterlogged"), names(PropertyCycle.ordered(List.of(slab, WATERLOGGED))),
                "la losa sigue cambiando de tipo");
    }

    private static List<String> names(List<PropertyCycle.Property> properties) {
        return properties.stream().map(PropertyCycle.Property::name).toList();
    }

    @Test
    void valuesWrapForwardAndBackward() {
        assertEquals("south", PropertyCycle.step(FACING, 1));
        assertEquals("east", PropertyCycle.step(FACING, -1));
        assertEquals("north", PropertyCycle.step(FACING, 4));
        assertEquals("top", PropertyCycle.step(HALF, 1));
        PropertyCycle.Property unknownValue = new PropertyCycle.Property("facing", FACING.values(), "up");
        assertEquals("south", PropertyCycle.step(unknownValue, 1));
    }

    @Test
    void activePropertyFallsBackToTheFirstWhenTheBlockLacksIt() {
        List<PropertyCycle.Property> stairs = PropertyCycle.ordered(List.of(FACING, HALF, SHAPE));
        assertEquals("half", PropertyCycle.active(stairs, "half").name());
        assertEquals("facing", PropertyCycle.active(stairs, "axis").name());
        assertEquals("facing", PropertyCycle.active(stairs, "").name());
        assertNull(PropertyCycle.active(List.of(), "facing"));
    }

    @Test
    void switchingPropertyGoesBothWaysAndWraps() {
        List<PropertyCycle.Property> stairs = PropertyCycle.ordered(List.of(FACING, HALF, SHAPE));
        assertEquals("half", PropertyCycle.switchProperty(stairs, "facing", 1));
        assertEquals("shape", PropertyCycle.switchProperty(stairs, "facing", -1));
        assertEquals("facing", PropertyCycle.switchProperty(stairs, "shape", 1));
        assertEquals("half", PropertyCycle.switchProperty(stairs, "missing", 1));
    }
}
