package com.komainu.moxia.gizmo;

import com.komainu.moxia.placement.Mat3;
import com.komainu.moxia.placement.PlacementStates;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.symmetry.StateTransform;
import com.komainu.moxia.symmetry.SymmetryTransform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Todos los estados de los bloques que giran, con sus propiedades como las tiene Minecraft 1.21.1, por las 48
 * orientaciones del gizmo (que de pie son las de la simetría y el portapapeles) y los radiales de la simetría:
 * escaleras con cada esquina, losas, troncos, puertas, camas, carteles colgantes, vallas y muros conectados, raíles,
 * cofres dobles, trampillas, palancas, campanas, faroles, el crafter… Cada estado se comprueba entero, combinación a
 * combinación: ida y vuelta, composición y que cada propiedad vaya a donde va el bloque.
 */
class OrientedStatesCoverageTest {
    private static final List<String> HORIZONTAL = List.of("north", "south", "west", "east");
    private static final List<String> SIX = List.of("down", "up", "north", "south", "west", "east");
    private static final List<String> BOOL = List.of("true", "false");
    private static final List<String> SIDES = List.of("west", "east", "down", "up", "north", "south");
    private static final List<String> CRAFTER = List.of("down_east", "down_north", "down_south", "down_west",
            "up_east", "up_north", "up_south", "up_west", "west_up", "east_up", "north_up", "south_up");
    private static final List<String> RAIL = List.of("north_south", "east_west", "ascending_east", "ascending_west",
            "ascending_north", "ascending_south", "south_east", "south_west", "north_west", "north_east");
    private static final List<String> STRAIGHT_RAIL = RAIL.subList(0, 6);
    private static final Map<String, Map<String, List<String>>> BLOCKS = new LinkedHashMap<>();

    static {
        block("oak_stairs", "facing", HORIZONTAL, "half", List.of("top", "bottom"), "shape",
                List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"), "waterlogged", BOOL);
        block("oak_slab", "type", List.of("top", "bottom", "double"), "waterlogged", BOOL);
        block("oak_log", "axis", List.of("x", "y", "z"));
        block("chain", "axis", List.of("x", "y", "z"), "waterlogged", BOOL);
        block("nether_portal", "axis", List.of("x", "z"));
        block("oak_door", "facing", HORIZONTAL, "half", List.of("upper", "lower"), "hinge", List.of("left", "right"),
                "open", BOOL, "powered", BOOL);
        block("red_bed", "facing", HORIZONTAL, "part", List.of("head", "foot"), "occupied", BOOL);
        block("oak_hanging_sign", "rotation", sixteen(), "attached", BOOL, "waterlogged", BOOL);
        block("oak_wall_hanging_sign", "facing", HORIZONTAL, "waterlogged", BOOL);
        block("oak_sign", "rotation", sixteen(), "waterlogged", BOOL);
        block("white_banner", "rotation", sixteen());
        block("skeleton_skull", "rotation", sixteen(), "powered", BOOL);
        block("oak_fence", "north", BOOL, "east", BOOL, "south", BOOL, "west", BOOL, "waterlogged", BOOL);
        block("glass_pane", "north", BOOL, "east", BOOL, "south", BOOL, "west", BOOL, "waterlogged", BOOL);
        block("cobblestone_wall", "up", BOOL, "north", List.of("none", "low", "tall"), "east",
                List.of("none", "low", "tall"), "south", List.of("none", "low", "tall"), "west",
                List.of("none", "low", "tall"), "waterlogged", BOOL);
        block("redstone_wire", "north", List.of("up", "side", "none"), "east", List.of("up", "side", "none"),
                "south", List.of("up", "side", "none"), "west", List.of("up", "side", "none"));
        block("brown_mushroom_block", "down", BOOL, "up", BOOL, "north", BOOL, "south", BOOL, "west", BOOL, "east",
                BOOL);
        block("glow_lichen", "down", BOOL, "up", BOOL, "north", BOOL, "south", BOOL, "west", BOOL, "east", BOOL,
                "waterlogged", BOOL);
        block("rail", "shape", RAIL, "waterlogged", BOOL);
        block("powered_rail", "shape", STRAIGHT_RAIL, "powered", BOOL, "waterlogged", BOOL);
        block("chest", "facing", HORIZONTAL, "type", List.of("single", "left", "right"), "waterlogged", BOOL);
        block("oak_trapdoor", "facing", HORIZONTAL, "half", List.of("top", "bottom"), "open", BOOL, "powered", BOOL,
                "waterlogged", BOOL);
        block("lever", "face", List.of("floor", "wall", "ceiling"), "facing", HORIZONTAL, "powered", BOOL);
        block("bell", "attachment", List.of("floor", "ceiling", "single_wall", "double_wall"), "facing", HORIZONTAL,
                "powered", BOOL);
        block("lantern", "hanging", BOOL, "waterlogged", BOOL);
        block("pointed_dripstone", "vertical_direction", List.of("up", "down"), "thickness",
                List.of("tip_merge", "tip", "frustum", "middle", "base"), "waterlogged", BOOL);
        block("crafter", "orientation", CRAFTER, "crafting", BOOL, "triggered", BOOL);
        block("piston", "facing", SIX, "extended", BOOL);
        block("hopper", "facing", List.of("down", "north", "south", "west", "east"), "enabled", BOOL);
        block("wall_torch", "facing", HORIZONTAL);
        block("furnace", "facing", HORIZONTAL, "lit", BOOL);
    }

    private static List<String> sixteen() {
        List<String> values = new ArrayList<>();
        for (int step = 0; step < 16; step++) {
            values.add(Integer.toString(step));
        }
        return values;
    }

    private static void block(String name, Object... pairs) {
        Map<String, List<String>> properties = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) pairs[index + 1];
            properties.put((String) pairs[index], values);
        }
        BLOCKS.put(name, properties);
    }

    /** Un bloque con un estado concreto. */
    private record State(String block, Map<String, String> values) {
        Map<String, List<String>> allowed() {
            return BLOCKS.get(block);
        }

        State apply(Orientation orientation) {
            return new State(block, OrientedStates.apply(values, allowed(), orientation));
        }

        boolean admits(Orientation orientation) {
            return OrientedStates.admits(values, allowed(), orientation);
        }

        @Override
        public String toString() {
            return block + values;
        }
    }

    /** Cada combinación de las propiedades que giran (las demás, en su primer valor). */
    private static List<State> everyState() {
        List<State> states = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<String>>> block : BLOCKS.entrySet()) {
            List<Map<String, String>> combinations = new ArrayList<>();
            combinations.add(new LinkedHashMap<>());
            for (Map.Entry<String, List<String>> property : block.getValue().entrySet()) {
                boolean turns = !List.of("waterlogged", "powered", "open", "occupied", "lit", "extended", "enabled",
                        "crafting", "triggered").contains(property.getKey());
                List<String> values = turns ? property.getValue() : property.getValue().subList(0, 1);
                List<Map<String, String>> next = new ArrayList<>();
                for (Map<String, String> partial : combinations) {
                    for (String value : values) {
                        Map<String, String> extended = new LinkedHashMap<>(partial);
                        extended.put(property.getKey(), value);
                        next.add(extended);
                    }
                }
                combinations = next;
            }
            for (Map<String, String> values : combinations) {
                states.add(new State(block.getKey(), values));
            }
        }
        return states;
    }

    @Test
    void everyStateComesBackWithTheInverse() {
        int checked = 0;
        for (State state : everyState()) {
            for (int index = 0; index < Orientation.COUNT; index++) {
                Orientation orientation = Orientation.of(index);
                if (!state.admits(orientation)) {
                    continue;
                }
                State there = state.apply(orientation);
                assertTrue(there.admits(orientation.inverse()), state + " → " + there + " con " + orientation);
                assertEquals(state, there.apply(orientation.inverse()), state + " con " + orientation);
                checked++;
            }
        }
        assertTrue(checked > 10_000, "comprobados " + checked);
    }

    /**
     * Girar dos veces es girar una por las dos, siempre que cada paso tenga su estado exacto: una tolva que mira abajo
     * no puede mirar arriba, así que reflejada en Y se queda como estaba y lo que venga después ya parte de otro sitio.
     */
    @Test
    void turningTwiceIsTurningOnceByBoth() {
        List<State> states = everyState();
        for (int a = 0; a < Orientation.COUNT; a++) {
            for (int b = 0; b < Orientation.COUNT; b++) {
                Orientation first = Orientation.of(a);
                Orientation second = Orientation.of(b);
                Orientation both = first.then(second);
                for (State state : states) {
                    if (!state.admits(first) || !state.apply(first).admits(second) || !state.admits(both)
                            || !exact(state, first) || !exact(state.apply(first), second)) {
                        continue;
                    }
                    assertEquals(state.apply(both), state.apply(first).apply(second),
                            state + " con " + first + " y luego " + second);
                }
            }
        }
    }

    /** Cada dirección va a la cara a la que va el bloque; si esa no existe en el bloque, se queda como estaba. */
    @Test
    void directionsFollowTheBlock() {
        for (State state : everyState()) {
            for (int index = 0; index < Orientation.COUNT; index++) {
                Orientation orientation = Orientation.of(index);
                if (!state.admits(orientation)) {
                    continue;
                }
                State there = state.apply(orientation);
                for (Map.Entry<String, String> entry : state.values().entrySet()) {
                    List<String> allowed = state.allowed().get(entry.getKey());
                    if (allowed.size() < 2 || !SIX.containsAll(allowed) || SIX.contains(entry.getKey())) {
                        continue;
                    }
                    String expected = SIDES.get(orientation.face(SIDES.indexOf(entry.getValue())));
                    String got = there.values().get(entry.getKey());
                    assertEquals(allowed.contains(expected) ? expected : entry.getValue(), got,
                            state + " con " + orientation);
                }
                String axis = state.values().get("axis");
                if (axis != null) {
                    String expected = String.valueOf((char) ('x' + orientation.axisOf(axis.charAt(0) - 'x')));
                    if (state.allowed().get("axis").contains(expected)) {
                        assertEquals(expected, there.values().get("axis"), state + " con " + orientation);
                    }
                }
            }
        }
    }

    /**
     * De pie: izquierda y derecha (bisagras, cofres dobles, esquinas de escalera) se cambian solo si la planta queda
     * reflejada; arriba y abajo (mitades, losas, palancas de techo, faroles colgados), solo boca abajo.
     */
    @Test
    void sidesAndHalvesSwapOnlyWithTheirMirror() {
        for (State state : everyState()) {
            for (int index = 0; index < Orientation.COUNT; index++) {
                Orientation orientation = Orientation.of(index);
                if (!orientation.upright()) {
                    continue;
                }
                State there = state.apply(orientation);
                boolean flipped = orientation.x(1, 0, 0) * orientation.z(0, 0, 1)
                        - orientation.z(1, 0, 0) * orientation.x(0, 0, 1) < 0;
                boolean upsideDown = orientation.upsideDown();
                for (Map.Entry<String, String> entry : state.values().entrySet()) {
                    String value = entry.getValue();
                    String now = there.values().get(entry.getKey());
                    String name = state + " con " + orientation;
                    switch (entry.getKey()) {
                        case "hinge" -> assertEquals(flipped ? swap(value, "left", "right") : value, now, name);
                        case "shape" -> {
                            if (state.block().equals("oak_stairs")) {
                                assertEquals(flipped ? value.replace("left", "@").replace("right", "left")
                                        .replace("@", "right") : value, now, name);
                            }
                        }
                        case "type" -> assertEquals(swap(flipped ? swap(value, "left", "right") : value,
                                upsideDown ? "top" : "", upsideDown ? "bottom" : ""), now, name);
                        case "half" -> assertEquals(upsideDown ? swap(swap(value, "top", "bottom"), "upper",
                                "lower") : value, now, name);
                        case "face" -> assertEquals(upsideDown ? swap(value, "floor", "ceiling") : value, now, name);
                        case "attachment" -> assertEquals(upsideDown ? swap(value, "floor", "ceiling") : value, now,
                                name);
                        case "hanging" -> assertEquals(upsideDown ? swap(value, "true", "false") : value, now, name);
                        default -> {
                        }
                    }
                }
            }
        }
    }

    /** Carteles, estandartes y cabezas: los 16 pasos giran con la planta (y un espejo los refleja). */
    @Test
    void sixteenStepRotationFollowsThePlan() {
        for (State state : everyState()) {
            String rotation = state.values().get("rotation");
            if (rotation == null) {
                continue;
            }
            int step = Integer.parseInt(rotation);
            for (int index = 0; index < Orientation.COUNT; index++) {
                Orientation orientation = Orientation.of(index);
                if (!orientation.upright()) {
                    continue;
                }
                double angle = step * Math.PI / 8.0;
                double x = -Math.sin(angle);
                double z = Math.cos(angle);
                double worldX = orientation.x(1, 0, 0) * x + orientation.x(0, 0, 1) * z;
                double worldZ = orientation.z(1, 0, 0) * x + orientation.z(0, 0, 1) * z;
                int expected = Math.floorMod((int) Math.round(Math.atan2(-worldX, worldZ) / (Math.PI / 8.0)), 16);
                assertEquals(Integer.toString(expected), state.apply(orientation).values().get("rotation"),
                        state + " con " + orientation);
            }
        }
    }

    /** Los radiales de la simetría que no son de cuartos (8 y 16 brazos) giran los 16 pasos exactos. */
    @Test
    void radialSymmetryTurnsSixteenStepsExactly() {
        for (int arms : new int[]{8, 16}) {
            for (int turn = 0; turn < arms; turn++) {
                SymmetryTransform transform = new SymmetryTransform(false, false, false, turn, arms);
                for (int step = 0; step < 16; step++) {
                    Map<String, String> sign = new LinkedHashMap<>();
                    sign.put("rotation", Integer.toString(step));
                    sign.put("waterlogged", "false");
                    Map<String, String> turned = StateTransform.apply(sign, BLOCKS.get("oak_sign"), transform);
                    assertEquals(Integer.toString(Math.floorMod(step + turn * 16 / arms, 16)), turned.get("rotation"),
                            "paso " + step + " con " + turn + "/" + arms);
                }
            }
        }
    }

    /**
     * {@code place}, {@code scatter}, {@code array} y sus pinceles giran con una {@link Mat3} (o la base entera del
     * pincel): si es una de las 48 orientaciones, los estados van por {@link OrientedStates} como en el fantasma; si es
     * un giro libre, por WorldEdit. Los restos de los senos no deben sacar un giro exacto del camino exacto.
     */
    @Test
    void placementMatricesAreTheExactOrientations() {
        for (int index = 0; index < Orientation.COUNT; index++) {
            Orientation orientation = Orientation.of(index);
            double[] m = new double[9];
            int[] basis = new int[9];
            for (int column = 0; column < 3; column++) {
                int[] world = orientation.apply(column == 0 ? 1 : 0, column == 1 ? 1 : 0, column == 2 ? 1 : 0);
                for (int row = 0; row < 3; row++) {
                    m[row * 3 + column] = world[row] + (world[row] == 0 ? 3.0E-17 : -2.0E-16);
                    basis[row * 3 + column] = world[row];
                }
            }
            Mat3 matrix = new Mat3(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8]);
            assertSame(orientation, PlacementStates.exact(matrix), "matriz de " + orientation);
            assertSame(orientation, PlacementStates.exact(basis), "base de " + orientation);
        }
        Vec3 up = new Vec3(0, 1, 0);
        for (int quarter = 0; quarter < 4; quarter++) {
            assertNotNull(PlacementStates.exact(Mat3.rotation(up, 90.0 * quarter)), quarter + " cuartos");
            assertNotNull(PlacementStates.exact(Mat3.quarterTurns(0, quarter).multiply(Mat3.diagonal(1, 1, -1))));
        }
        assertNull(PlacementStates.exact(Mat3.rotation(up, 30.0)), "30° es libre");
        assertNull(PlacementStates.exact(Mat3.rotation(new Vec3(1, 1, 0), 90.0)), "eje en diagonal");
        assertNull(PlacementStates.exact(Mat3.diagonal(2, 1, 1)), "con escala no es un giro");
        assertNull(PlacementStates.exact(new Mat3(1, 0, 0, 1, 0, 0, 0, 0, 1)), "dos columnas al mismo eje");
    }

    /**
     * Lo que dejó el #96: con {@code BlockTransformExtent} un raíl girado 90° seguía de norte a sur, una puerta volteada
     * no cambiaba la bisagra y boca abajo no cambiaba de mitad. Con la matriz de la colocación pasada a orientación,
     * cada propiedad va a donde va el bloque.
     */
    @Test
    void placementTurnsRailsDoorsAndHingesWithTheGeometry() {
        Vec3 up = new Vec3(0, 1, 0);
        List<Mat3> matrices = new ArrayList<>();
        for (int quarter = 0; quarter < 4; quarter++) {
            Mat3 turn = Mat3.rotation(up, 90.0 * quarter);
            matrices.add(turn);
            matrices.add(turn.multiply(Mat3.diagonal(-1, 1, 1)));
            matrices.add(turn.multiply(Mat3.diagonal(1, -1, 1)));
        }
        for (Mat3 matrix : matrices) {
            Orientation orientation = PlacementStates.exact(matrix);
            assertNotNull(orientation, matrix.toString());
            boolean flipped = matrix.m00() * matrix.m22() - matrix.m02() * matrix.m20() < 0;
            boolean upsideDown = matrix.m11() < 0;
            for (String shape : RAIL) {
                Map<String, String> rail = new LinkedHashMap<>();
                rail.put("shape", shape);
                rail.put("waterlogged", "false");
                String turned = OrientedStates.apply(rail, BLOCKS.get("rail"), orientation).get("shape");
                assertEquals(railShape(shape, matrix), turned, shape + " con " + matrix);
            }
            for (String hinge : List.of("left", "right")) {
                for (String half : List.of("upper", "lower")) {
                    Map<String, String> door = new LinkedHashMap<>();
                    door.put("facing", "north");
                    door.put("half", half);
                    door.put("hinge", hinge);
                    door.put("open", "false");
                    door.put("powered", "false");
                    Map<String, String> turned = OrientedStates.apply(door, BLOCKS.get("oak_door"), orientation);
                    assertEquals(flipped ? swap(hinge, "left", "right") : hinge, turned.get("hinge"),
                            "bisagra con " + matrix);
                    assertEquals(upsideDown ? swap(half, "upper", "lower") : half, turned.get("half"),
                            "mitad con " + matrix);
                    assertEquals(horizontal(matrix.apply(0, 0, -1)), turned.get("facing"), "frente con " + matrix);
                }
            }
            Map<String, String> lever = new LinkedHashMap<>();
            lever.put("face", "floor");
            lever.put("facing", "east");
            lever.put("powered", "false");
            Map<String, String> turnedLever = OrientedStates.apply(lever, BLOCKS.get("lever"), orientation);
            assertEquals(upsideDown ? "ceiling" : "floor", turnedLever.get("face"), "palanca con " + matrix);
        }
    }

    /**
     * La forma de raíl que sale de llevar sus dos extremos con la matriz; la subida va hacia donde va su lado alto, y
     * boca abajo hacia el otro (la rampa baja hacia donde antes subía).
     */
    private static String railShape(String shape, Mat3 matrix) {
        if (shape.startsWith("ascending_")) {
            Vec3 toward = matrix.apply(vector(shape.substring("ascending_".length())));
            return "ascending_" + horizontal(matrix.m11() < 0 ? toward.multiply(-1) : toward);
        }
        String[] ends = shape.split("_");
        String first = horizontal(matrix.apply(vector(ends[0])));
        String second = horizontal(matrix.apply(vector(ends[1])));
        for (String candidate : RAIL) {
            String[] parts = candidate.split("_");
            if (parts.length == 2 && !candidate.startsWith("ascending") && (parts[0].equals(first)
                    && parts[1].equals(second) || parts[0].equals(second) && parts[1].equals(first))) {
                return candidate;
            }
        }
        throw new AssertionError("sin forma para " + first + " y " + second);
    }

    private static Vec3 vector(String direction) {
        return switch (direction) {
            case "north" -> new Vec3(0, 0, -1);
            case "south" -> new Vec3(0, 0, 1);
            case "west" -> new Vec3(-1, 0, 0);
            default -> new Vec3(1, 0, 0);
        };
    }

    private static String horizontal(Vec3 direction) {
        if (Math.abs(direction.x()) > Math.abs(direction.z())) {
            return direction.x() > 0 ? "east" : "west";
        }
        return direction.z() > 0 ? "south" : "north";
    }

    /** ¿Tiene el bloque cada dirección girada (frente, eje, frente y arriba del crafter)? */
    private static boolean exact(State state, Orientation orientation) {
        for (Map.Entry<String, String> entry : state.values().entrySet()) {
            List<String> allowed = state.allowed().get(entry.getKey());
            if (entry.getKey().equals("orientation")) {
                String[] parts = entry.getValue().split("_");
                String turned = SIDES.get(orientation.face(SIDES.indexOf(parts[0]))) + "_"
                        + SIDES.get(orientation.face(SIDES.indexOf(parts[1])));
                if (!allowed.contains(turned)) {
                    return false;
                }
            } else if (allowed.size() >= 2 && SIX.containsAll(allowed) && !SIX.contains(entry.getKey())
                    && !allowed.contains(SIDES.get(orientation.face(SIDES.indexOf(entry.getValue()))))) {
                return false;
            }
        }
        return true;
    }

    private static String swap(String value, String first, String second) {
        if (first.isEmpty()) {
            return value;
        }
        if (value.equals(first)) {
            return second;
        }
        return value.equals(second) ? first : value;
    }
}
