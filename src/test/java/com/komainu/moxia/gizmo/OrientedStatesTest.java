package com.komainu.moxia.gizmo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrientedStatesTest {
    private static final Orientation TURN_Y = Orientation.turn(Orientation.Y, 1);
    private static final Orientation TURN_X = Orientation.turn(Orientation.X, 1);
    private static final Orientation TURN_Z = Orientation.turn(Orientation.Z, 1);

    @Test
    void uprightTurnsAreTheSymmetryOnes() {
        FakeStates states = new FakeStates();
        int stairs = states.state("oak_stairs", "facing", "east", "half", "bottom", "shape", "inner_left");
        int turned = states.map(stairs, TURN_Y);
        assertEquals("south", states.value(turned, "facing"));
        assertEquals("inner_left", states.value(turned, "shape"));
        // Voltear cambia la esquina de lado; boca abajo la escalera queda arriba.
        int flipped = states.map(stairs, Orientation.mirror(Orientation.X));
        assertEquals("west", states.value(flipped, "facing"));
        assertEquals("inner_right", states.value(flipped, "shape"));
        int upsideDown = states.map(stairs, Orientation.mirror(Orientation.Y));
        assertEquals("top", states.value(upsideDown, "half"));
        int log = states.state("oak_log", "axis", "x");
        assertEquals("z", states.value(states.map(log, TURN_Y), "axis"));
        int door = states.state("oak_door", "facing", "north", "hinge", "left");
        assertEquals("right", states.value(states.map(door, Orientation.mirror(Orientation.Z)), "hinge"));
    }

    @Test
    void tippingCarriesAxesAndSixWayDirections() {
        FakeStates states = new FakeStates();
        int log = states.state("oak_log", "axis", "y");
        assertEquals("z", states.value(states.map(log, TURN_X), "axis"));
        assertEquals("x", states.value(states.map(log, TURN_Z), "axis"));
        assertEquals("x", states.value(states.map(states.state("oak_log", "axis", "x"), TURN_X), "axis"));
        int dispenser = states.state("dispenser", "facing", "up", "triggered", "true");
        // De arriba a sur con un cuarto de vuelta en X; lo demás se queda.
        int tipped = states.map(dispenser, TURN_X);
        assertEquals("south", states.value(tipped, "facing"));
        assertEquals("true", states.value(tipped, "triggered"));
        assertEquals("up", states.value(states.map(states.state("dispenser", "facing", "east"), TURN_Z), "facing"));
        // Las setas grandes llevan sus seis caras.
        int mushroom = states.state("brown_mushroom_block", "up", "true", "down", "false", "north", "false",
                "south", "false", "west", "false", "east", "false");
        int rolled = states.map(mushroom, TURN_X);
        assertEquals("true", states.value(rolled, "south"));
        assertEquals("false", states.value(rolled, "up"));
    }

    @Test
    void onlyAdmitsTippingWhatHasATippedVersion() {
        FakeStates states = new FakeStates();
        assertTrue(states.admits(states.state("stone"), TURN_X));
        assertTrue(states.admits(states.state("oak_log", "axis", "y"), TURN_Z));
        assertTrue(states.admits(states.state("dispenser", "facing", "north"), TURN_X));
        assertTrue(states.admits(states.state("oak_slab", "type", "double"), TURN_X));
        assertTrue(states.admits(states.state("brown_mushroom_block"), TURN_Z));
        // Un horno que mira al norte acabaría mirando arriba; uno que mira al este, girado en X, sigue al este.
        assertFalse(states.admits(states.state("furnace", "facing", "north"), TURN_X));
        assertTrue(states.admits(states.state("furnace", "facing", "east"), TURN_X));
        assertFalse(states.admits(states.state("oak_stairs"), TURN_X));
        assertFalse(states.admits(states.state("oak_slab", "type", "bottom"), TURN_Z));
        assertFalse(states.admits(states.state("oak_door"), TURN_X));
        assertFalse(states.admits(states.state("oak_fence"), TURN_X));
        assertFalse(states.admits(states.state("rail"), TURN_Z));
        // El portal solo tiene x y z: tumbado en X con eje z acabaría en y.
        assertFalse(states.admits(states.state("nether_portal", "axis", "z"), TURN_X));
        // De pie, todo vale (lo que no se puede girar se queda como está).
        for (int index = 0; index < Orientation.COUNT; index++) {
            Orientation orientation = Orientation.of(index);
            if (orientation.upright()) {
                assertTrue(states.admits(states.state("oak_stairs"), orientation));
            }
        }
    }

    @Test
    void turningBackLeavesEveryStateAsItWas() {
        FakeStates states = new FakeStates();
        List<Integer> samples = samples(states);
        for (int index = 0; index < Orientation.COUNT; index++) {
            Orientation orientation = Orientation.of(index);
            for (int state : samples) {
                if (!states.admits(state, orientation)) {
                    continue;
                }
                int there = states.map(state, orientation);
                assertTrue(states.admits(there, orientation.inverse()), states.values(there) + " " + orientation);
                assertEquals(state, states.map(there, orientation.inverse()),
                        states.block(state) + states.values(state) + " con " + orientation);
            }
        }
    }

    @Test
    void turningTwiceIsTheSameAsTurningOnceByBoth() {
        FakeStates states = new FakeStates();
        List<Integer> samples = samples(states);
        for (int a = 0; a < Orientation.COUNT; a++) {
            for (int b = 0; b < Orientation.COUNT; b++) {
                Orientation first = Orientation.of(a);
                Orientation second = Orientation.of(b);
                Orientation both = first.then(second);
                for (int state : samples) {
                    if (!states.admits(state, first) || !states.admits(states.map(state, first), second)
                            || !states.admits(state, both)) {
                        continue;
                    }
                    assertEquals(states.values(states.map(state, both)),
                            states.values(states.map(states.map(state, first), second)),
                            states.block(state) + states.values(state) + " " + first + " y " + second);
                }
            }
        }
    }

    @Test
    void fourTurnsAndTwoFlipsAreIdempotent() {
        FakeStates states = new FakeStates();
        for (int state : samples(states)) {
            for (int axis = 0; axis < 3; axis++) {
                int turned = state;
                boolean admitted = true;
                for (int turn = 0; turn < 4 && admitted; turn++) {
                    admitted = states.admits(turned, Orientation.turn(axis, 1));
                    turned = states.map(turned, Orientation.turn(axis, 1));
                }
                if (admitted) {
                    assertEquals(state, turned, states.block(state) + states.values(state) + " eje " + axis);
                }
                int mirrored = states.map(states.map(state, Orientation.mirror(axis)), Orientation.mirror(axis));
                assertEquals(state, mirrored, states.block(state) + states.values(state) + " espejo " + axis);
            }
        }
    }

    /** Estados con todo lo que gira: escaleras de cada forma, puertas, raíles, troncos, vallas… */
    private static List<Integer> samples(FakeStates states) {
        List<Integer> samples = new java.util.ArrayList<>();
        samples.add(states.state("stone"));
        for (String axis : List.of("x", "y", "z")) {
            samples.add(states.state("oak_log", "axis", axis));
        }
        for (String facing : FakeStates.HORIZONTAL) {
            for (String shape : List.of("straight", "inner_left", "outer_right")) {
                for (String half : List.of("top", "bottom")) {
                    samples.add(states.state("oak_stairs", "facing", facing, "shape", shape, "half", half));
                }
            }
            samples.add(states.state("oak_door", "facing", facing, "hinge", "left", "half", "upper"));
            samples.add(states.state("furnace", "facing", facing));
        }
        for (String facing : FakeStates.SIX) {
            samples.add(states.state("dispenser", "facing", facing));
        }
        for (String type : List.of("top", "bottom", "double")) {
            samples.add(states.state("oak_slab", "type", type));
        }
        samples.add(states.state("oak_fence", "north", "true", "east", "false", "south", "true", "west", "false"));
        samples.add(states.state("brown_mushroom_block", "up", "true", "north", "false", "east", "false"));
        for (String shape : List.of("north_south", "ascending_east", "south_west")) {
            samples.add(states.state("rail", "shape", shape));
        }
        samples.add(states.state("nether_portal", "axis", "x"));
        return samples;
    }
}
