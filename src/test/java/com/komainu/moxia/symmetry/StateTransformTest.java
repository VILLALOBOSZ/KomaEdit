package com.komainu.moxia.symmetry;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StateTransformTest {
    private static final SymmetryTransform MIRROR_X = new SymmetryTransform(true, false, false, 0, 1);
    private static final SymmetryTransform MIRROR_Z = new SymmetryTransform(false, true, false, 0, 1);
    private static final SymmetryTransform MIRROR_Y = new SymmetryTransform(false, false, true, 0, 1);
    private static final SymmetryTransform QUARTER = new SymmetryTransform(false, false, false, 1, 4);
    private static final SymmetryTransform HALF_TURN = new SymmetryTransform(false, false, false, 2, 4);

    private static final List<String> HORIZONTAL = List.of("north", "south", "west", "east");
    private static final List<String> ALL_DIRECTIONS = List.of("down", "up", "north", "south", "west", "east");
    private static final List<String> BOOL = List.of("true", "false");
    private static final Map<String, List<String>> STAIRS = Map.of(
            "facing", HORIZONTAL,
            "half", List.of("top", "bottom"),
            "shape", List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"),
            "waterlogged", BOOL
    );
    private static final Map<String, List<String>> DOOR = Map.of(
            "facing", HORIZONTAL,
            "half", List.of("upper", "lower"),
            "hinge", List.of("left", "right"),
            "open", BOOL,
            "powered", BOOL
    );
    private static final Map<String, List<String>> FENCE = Map.of(
            "north", BOOL, "east", BOOL, "south", BOOL, "west", BOOL, "waterlogged", BOOL
    );
    private static final Map<String, List<String>> RAIL = Map.of(
            "shape", List.of("north_south", "east_west", "ascending_east", "ascending_west", "ascending_north",
                    "ascending_south", "south_east", "south_west", "north_west", "north_east"),
            "waterlogged", BOOL
    );

    @Test
    void mirroredStairsFaceTheOtherWayAndSwapTheirCorners() {
        Map<String, String> stairs = state("facing", "east", "half", "bottom", "shape", "inner_left",
                "waterlogged", "false");
        Map<String, String> mirrored = StateTransform.apply(stairs, STAIRS, MIRROR_X);
        assertEquals("west", mirrored.get("facing"));
        assertEquals("inner_right", mirrored.get("shape"));
        assertEquals("bottom", mirrored.get("half"));
        // El espejo en Z deja quieta una escalera que mira al este, pero también cambia su esquina.
        Map<String, String> inZ = StateTransform.apply(stairs, STAIRS, MIRROR_Z);
        assertEquals("east", inZ.get("facing"));
        assertEquals("inner_right", inZ.get("shape"));
        // Girar no cambia la esquina de lado.
        Map<String, String> turned = StateTransform.apply(stairs, STAIRS, QUARTER);
        assertEquals("south", turned.get("facing"));
        assertEquals("inner_left", turned.get("shape"));
    }

    @Test
    void mirrorInYTurnsStairsSlabsAndDoorsUpsideDown() {
        Map<String, String> stairs = StateTransform.apply(state("facing", "north", "half", "bottom",
                "shape", "outer_left", "waterlogged", "false"), STAIRS, MIRROR_Y);
        assertEquals("top", stairs.get("half"));
        assertEquals("north", stairs.get("facing"));
        assertEquals("outer_left", stairs.get("shape"));
        Map<String, List<String>> slab = Map.of("type", List.of("top", "bottom", "double"), "waterlogged", BOOL);
        assertEquals("top", StateTransform.apply(state("type", "bottom", "waterlogged", "false"), slab, MIRROR_Y)
                .get("type"));
        assertEquals("double", StateTransform.apply(state("type", "double", "waterlogged", "false"), slab, MIRROR_Y)
                .get("type"));
        Map<String, String> door = StateTransform.apply(state("facing", "north", "half", "lower", "hinge", "left",
                "open", "false", "powered", "false"), DOOR, MIRROR_Y);
        assertEquals("upper", door.get("half"));
        assertEquals("left", door.get("hinge"));
    }

    @Test
    void mirroredDoorsChangeHingeAndChestsChangeSide() {
        Map<String, String> door = StateTransform.apply(state("facing", "north", "half", "lower", "hinge", "left",
                "open", "true", "powered", "false"), DOOR, MIRROR_Z);
        assertEquals("south", door.get("facing"));
        assertEquals("right", door.get("hinge"));
        assertEquals("true", door.get("open"));
        Map<String, List<String>> chest = Map.of("facing", HORIZONTAL, "type", List.of("single", "left", "right"),
                "waterlogged", BOOL);
        Map<String, String> left = state("facing", "south", "type", "left", "waterlogged", "false");
        assertEquals("right", StateTransform.apply(left, chest, MIRROR_X).get("type"));
        assertEquals("left", StateTransform.apply(left, chest, HALF_TURN).get("type"));
        assertEquals("north", StateTransform.apply(left, chest, HALF_TURN).get("facing"));
    }

    @Test
    void axesRotateWithOddQuarterTurnsOnly() {
        Map<String, List<String>> log = Map.of("axis", List.of("x", "y", "z"));
        assertEquals("z", StateTransform.apply(state("axis", "x"), log, QUARTER).get("axis"));
        assertEquals("x", StateTransform.apply(state("axis", "x"), log, HALF_TURN).get("axis"));
        assertEquals("y", StateTransform.apply(state("axis", "y"), log, QUARTER).get("axis"));
        assertEquals("x", StateTransform.apply(state("axis", "x"), log, MIRROR_X).get("axis"));
        assertEquals("y", StateTransform.apply(state("axis", "y"), log, MIRROR_Y).get("axis"));
    }

    @Test
    void facingThatCannotExistStaysAsItWas() {
        // Una tolva no mira hacia arriba: reflejada en Y sigue mirando abajo.
        Map<String, List<String>> hopper = Map.of("facing", List.of("down", "north", "south", "west", "east"),
                "enabled", BOOL);
        assertEquals("down", StateTransform.apply(state("facing", "down", "enabled", "true"), hopper, MIRROR_Y)
                .get("facing"));
        Map<String, List<String>> piston = Map.of("facing", ALL_DIRECTIONS, "extended", BOOL);
        assertEquals("down", StateTransform.apply(state("facing", "up", "extended", "false"), piston, MIRROR_Y)
                .get("facing"));
        assertEquals("up", StateTransform.apply(state("facing", "up", "extended", "false"), piston, QUARTER)
                .get("facing"));
    }

    @Test
    void wallAttachedBlocksFlipBetweenFloorAndCeiling() {
        Map<String, List<String>> button = Map.of("face", List.of("floor", "wall", "ceiling"), "facing", HORIZONTAL,
                "powered", BOOL);
        Map<String, String> onFloor = StateTransform.apply(state("face", "floor", "facing", "east", "powered", "false"),
                button, MIRROR_Y);
        assertEquals("ceiling", onFloor.get("face"));
        assertEquals("east", onFloor.get("facing"));
        Map<String, List<String>> bell = Map.of("attachment", List.of("floor", "ceiling", "single_wall", "double_wall"),
                "facing", HORIZONTAL, "powered", BOOL);
        assertEquals("floor", StateTransform.apply(state("attachment", "ceiling", "facing", "north", "powered", "false"),
                bell, MIRROR_Y).get("attachment"));
        Map<String, List<String>> lantern = Map.of("hanging", BOOL, "waterlogged", BOOL);
        assertEquals("false", StateTransform.apply(state("hanging", "true", "waterlogged", "false"), lantern, MIRROR_Y)
                .get("hanging"));
        Map<String, List<String>> dripstone = Map.of("vertical_direction", List.of("up", "down"),
                "thickness", List.of("tip_merge", "tip", "frustum", "middle", "base"), "waterlogged", BOOL);
        assertEquals("down", StateTransform.apply(state("vertical_direction", "up", "thickness", "tip",
                "waterlogged", "false"), dripstone, MIRROR_Y).get("vertical_direction"));
    }

    @Test
    void connectionsMoveToTheMirroredSide() {
        Map<String, String> fence = state("north", "true", "east", "true", "south", "false", "west", "false",
                "waterlogged", "false");
        Map<String, String> mirrored = StateTransform.apply(fence, FENCE, MIRROR_X);
        assertEquals(state("north", "true", "east", "false", "south", "false", "west", "true", "waterlogged", "false"),
                mirrored);
        Map<String, String> turned = StateTransform.apply(fence, FENCE, QUARTER);
        assertEquals("true", turned.get("east"));
        assertEquals("true", turned.get("south"));
        assertEquals("false", turned.get("north"));
        // Un muro conserva su poste («up») al reflejarlo en Y; el liquen sí cambia arriba por abajo.
        Map<String, List<String>> wallValues = Map.of("north", List.of("none", "low", "tall"),
                "east", List.of("none", "low", "tall"), "south", List.of("none", "low", "tall"),
                "west", List.of("none", "low", "tall"), "up", BOOL, "waterlogged", BOOL);
        Map<String, String> wall = state("north", "tall", "east", "none", "south", "low", "west", "none", "up", "true",
                "waterlogged", "false");
        assertEquals(wall, StateTransform.apply(wall, wallValues, MIRROR_Y));
        Map<String, List<String>> lichen = Map.of("north", BOOL, "east", BOOL, "south", BOOL, "west", BOOL,
                "up", BOOL, "down", BOOL, "waterlogged", BOOL);
        Map<String, String> flipped = StateTransform.apply(state("north", "false", "east", "false", "south", "false",
                "west", "false", "up", "true", "down", "false", "waterlogged", "false"), lichen, MIRROR_Y);
        assertEquals("false", flipped.get("up"));
        assertEquals("true", flipped.get("down"));
    }

    @Test
    void railsTurnCurvesAndSlopes() {
        assertEquals("east_west", StateTransform.apply(state("shape", "north_south", "waterlogged", "false"), RAIL,
                QUARTER).get("shape"));
        assertEquals("south_west", StateTransform.apply(state("shape", "south_east", "waterlogged", "false"), RAIL,
                MIRROR_X).get("shape"));
        assertEquals("north_west", StateTransform.apply(state("shape", "north_east", "waterlogged", "false"), RAIL,
                MIRROR_X).get("shape"));
        assertEquals("south_west", StateTransform.apply(state("shape", "south_east", "waterlogged", "false"), RAIL,
                QUARTER).get("shape"));
        assertEquals("ascending_south", StateTransform.apply(state("shape", "ascending_east", "waterlogged", "false"),
                RAIL, QUARTER).get("shape"));
        assertEquals("ascending_west", StateTransform.apply(state("shape", "ascending_east", "waterlogged", "false"),
                RAIL, MIRROR_Y).get("shape"));
    }

    @Test
    void signRotationFollowsExactRadialAngles() {
        Map<String, List<String>> sign = Map.of("rotation", sixteen(), "waterlogged", BOOL);
        // 0 mira al sur; un cuarto de vuelta (de este a sur) lo lleva al oeste, que es el 4.
        assertEquals("4", StateTransform.apply(state("rotation", "0", "waterlogged", "false"), sign, QUARTER)
                .get("rotation"));
        // Espejo en X: el 3 (sur-suroeste… hacia el oeste) pasa al 13.
        assertEquals("13", StateTransform.apply(state("rotation", "3", "waterlogged", "false"), sign, MIRROR_X)
                .get("rotation"));
        // Espejo en Z: el sur pasa al norte.
        assertEquals("8", StateTransform.apply(state("rotation", "0", "waterlogged", "false"), sign, MIRROR_Z)
                .get("rotation"));
        // Radial de 16 brazos: cada copia gira un paso; de 8 brazos, dos.
        assertEquals("1", StateTransform.apply(state("rotation", "0", "waterlogged", "false"), sign,
                new SymmetryTransform(false, false, false, 1, 16)).get("rotation"));
        assertEquals("6", StateTransform.apply(state("rotation", "2", "waterlogged", "false"), sign,
                new SymmetryTransform(false, false, false, 2, 8)).get("rotation"));
    }

    @Test
    void transformsAreIdempotentOverTheirCycle() {
        Map<String, String> stairs = state("facing", "west", "half", "top", "shape", "outer_right", "waterlogged", "true");
        for (SymmetryTransform mirror : List.of(MIRROR_X, MIRROR_Z, MIRROR_Y,
                new SymmetryTransform(true, true, true, 0, 1))) {
            assertEquals(stairs, StateTransform.apply(StateTransform.apply(stairs, STAIRS, mirror), STAIRS, mirror));
        }
        Map<String, String> door = state("facing", "east", "half", "upper", "hinge", "right", "open", "true",
                "powered", "false");
        Map<String, String> turned = door;
        for (int step = 0; step < 4; step++) {
            turned = StateTransform.apply(turned, DOOR, QUARTER);
        }
        assertEquals(door, turned);
        Map<String, List<String>> sign = Map.of("rotation", sixteen(), "waterlogged", BOOL);
        SymmetryTransform sixteenth = new SymmetryTransform(false, false, false, 1, 16);
        for (int start = 0; start < 16; start++) {
            Map<String, String> original = state("rotation", Integer.toString(start), "waterlogged", "false");
            Map<String, String> rotated = original;
            for (int step = 0; step < 16; step++) {
                rotated = StateTransform.apply(rotated, sign, sixteenth);
            }
            assertEquals(original, rotated);
            assertEquals(original, StateTransform.apply(StateTransform.apply(original, sign, MIRROR_X), sign, MIRROR_X));
        }
        Map<String, String> rail = state("shape", "north_east", "waterlogged", "false");
        assertEquals(rail, StateTransform.apply(StateTransform.apply(rail, RAIL, MIRROR_Z), RAIL, MIRROR_Z));
    }

    @Test
    void crafterOrientationTurnsBothDirections() {
        Map<String, List<String>> crafter = Map.of("orientation", List.of("down_east", "down_north", "down_south",
                "down_west", "up_east", "up_north", "up_south", "up_west", "west_up", "east_up", "north_up",
                "south_up"), "crafting", BOOL, "triggered", BOOL);
        assertEquals("up_west", StateTransform.apply(state("orientation", "down_east", "crafting", "false",
                "triggered", "false"), crafter, new SymmetryTransform(true, false, true, 0, 1)).get("orientation"));
        assertEquals("south_up", StateTransform.apply(state("orientation", "east_up", "crafting", "false",
                "triggered", "false"), crafter, QUARTER).get("orientation"));
    }

    @Test
    void nonQuarterRadialTurnsFacingToTheNearestQuarter() {
        Map<String, List<String>> furnace = Map.of("facing", HORIZONTAL, "lit", BOOL);
        // 120° está más cerca de 90° (este → sur); 240°, de 270° (este → norte).
        assertEquals("south", StateTransform.apply(state("facing", "east", "lit", "false"), furnace,
                new SymmetryTransform(false, false, false, 1, 3)).get("facing"));
        assertEquals("north", StateTransform.apply(state("facing", "east", "lit", "false"), furnace,
                new SymmetryTransform(false, false, false, 2, 3)).get("facing"));
    }

    private static List<String> sixteen() {
        return java.util.stream.IntStream.range(0, 16).mapToObj(Integer::toString).toList();
    }

    private static Map<String, String> state(String... pairs) {
        Map<String, String> state = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            state.put(pairs[index], pairs[index + 1]);
        }
        return state;
    }
}
