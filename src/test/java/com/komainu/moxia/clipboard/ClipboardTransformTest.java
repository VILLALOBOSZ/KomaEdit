package com.komainu.moxia.clipboard;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardTransformTest {
    private static final long LIMIT = 10_000_000L;
    private static final Runnable NO_CANCEL = () -> {
    };

    // --------------------------------------------------------------------------------------------- Scale2x y Scale3x

    @Test
    void scale2xSmoothsAKnownDiagonal() {
        int[][] image = {
                {1, 0, 0},
                {0, 1, 0},
                {0, 0, 1}};
        int[][] expected = {
                {1, 1, 0, 0, 0, 0},
                {1, 1, 1, 0, 0, 0},
                {0, 1, 1, 1, 0, 0},
                {0, 0, 1, 1, 1, 0},
                {0, 0, 0, 1, 1, 1},
                {0, 0, 0, 0, 1, 1}};
        VoxelVolume scaled = ClipboardTransform.scale(layer(image), 2.0, ClipboardTransform.Mode.SCALE2X, LIMIT,
                NO_CANCEL);
        assertArrayEquals(expected, slice(scaled, 0));
        assertArrayEquals(expected, slice(scaled, 1), "las dos capas de altura son iguales");
        assertArrayEquals(expected, reference2x(image));
    }

    @Test
    void scale2xKeepsALoneBlockSquare() {
        int[][] image = {{0, 0, 0}, {0, 3, 0}, {0, 0, 0}};
        int[][] scaled = slice(ClipboardTransform.scale(layer(image), 2.0, ClipboardTransform.Mode.SCALE2X, LIMIT,
                NO_CANCEL), 0);
        assertEquals(4, count(scaled, 3));
        assertEquals(3, scaled[2][2]);
        assertEquals(3, scaled[3][3]);
    }

    @Test
    void oneLayerMatchesTheClassic2dAlgorithms() {
        Random random = new Random(12);
        for (int round = 0; round < 40; round++) {
            int[][] image = new int[6 + random.nextInt(6)][6 + random.nextInt(6)];
            for (int[] row : image) {
                for (int x = 0; x < row.length; x++) {
                    row[x] = random.nextInt(10) < 4 ? 1 + random.nextInt(3) : 0;
                }
            }
            VoxelVolume volume = layer(image);
            VoxelVolume two = ClipboardTransform.scale(volume, 2.0, ClipboardTransform.Mode.SCALE2X, LIMIT,
                    NO_CANCEL);
            VoxelVolume three = ClipboardTransform.scale(volume, 3.0, ClipboardTransform.Mode.SCALE3X, LIMIT,
                    NO_CANCEL);
            int[][] expectedTwo = reference2x(image);
            int[][] expectedThree = reference3x(image);
            for (int y = 0; y < 2; y++) {
                assertArrayEquals(expectedTwo, slice(two, y), "Scale2x, ronda " + round);
            }
            for (int y = 0; y < 3; y++) {
                assertArrayEquals(expectedThree, slice(three, y), "Scale3x, ronda " + round);
            }
        }
    }

    // --------------------------------------------------------------------------------------------- Volumen

    @Test
    void nearestScalesTheVolumeExactly() {
        VoxelVolume sphere = sphere(6.5, 1);
        int blocks = sphere.blocks();
        assertEquals(blocks * 8, ClipboardTransform.scale(sphere, 2.0, ClipboardTransform.Mode.NEAREST, LIMIT,
                NO_CANCEL).blocks());
        assertEquals(blocks * 27, ClipboardTransform.scale(sphere, 3.0, ClipboardTransform.Mode.NEAREST, LIMIT,
                NO_CANCEL).blocks());
    }

    @Test
    void smartModesKeepTheVolumeInProportion() {
        VoxelVolume sphere = sphere(7.5, 1);
        int blocks = sphere.blocks();
        Object[][] cases = {
                {ClipboardTransform.Mode.SCALE2X, 2.0},
                {ClipboardTransform.Mode.SCALE3X, 3.0},
                {ClipboardTransform.Mode.ROTSPRITE, 2.0},
                {ClipboardTransform.Mode.ROTSPRITE, 1.5},
                {ClipboardTransform.Mode.ROTSPRITE, 0.5},
                {ClipboardTransform.Mode.SCALE2X, 4.0}};
        for (Object[] entry : cases) {
            ClipboardTransform.Mode mode = (ClipboardTransform.Mode) entry[0];
            double factor = (double) entry[1];
            int scaled = ClipboardTransform.scale(sphere, factor, mode, LIMIT, NO_CANCEL).blocks();
            double expected = blocks * factor * factor * factor;
            double error = Math.abs(scaled - expected) / expected;
            assertTrue(error < 0.08, mode + " ×" + factor + ": " + scaled + " frente a " + expected);
        }
    }

    @Test
    void scalingKeepsTheOriginCorner() {
        VoxelVolume volume = new VoxelVolume(-2, 0, -3, 4, 2, 5);
        volume.set(-2, 0, -3, 1);
        VoxelVolume scaled = ClipboardTransform.scale(volume, 2.0, ClipboardTransform.Mode.NEAREST, LIMIT,
                NO_CANCEL);
        assertEquals(-4, scaled.minimumX());
        assertEquals(-6, scaled.minimumZ());
        assertEquals(8, scaled.sizeX());
        assertEquals(1, scaled.get(-4, 0, -6));
        assertEquals(1, scaled.get(-3, 1, -5));
    }

    // --------------------------------------------------------------------------------------------- Giros

    @Test
    void quarterTurnsAreExactInEveryMode() {
        VoxelVolume volume = randomVolume(new Random(5), -3, 0, -2, 7, 3, 6);
        for (ClipboardTransform.Mode mode : ClipboardTransform.Mode.values()) {
            VoxelVolume turned = ClipboardTransform.rotate(volume, 90.0, mode, LIMIT, NO_CANCEL);
            volume.forEach((x, y, z, id) -> assertEquals(id, turned.get(-z, y, x), mode + " en " + x + "," + z));
            assertEquals(volume.blocks(), turned.blocks(), mode.id());
            VoxelVolume back = volume;
            for (int turn = 0; turn < 4; turn++) {
                back = ClipboardTransform.rotate(back, 90.0, mode, LIMIT, NO_CANCEL);
            }
            VoxelVolume full = back;
            volume.forEach((x, y, z, id) -> assertEquals(id, full.get(x, y, z)));
            assertEquals(volume.blocks(), full.blocks());
        }
    }

    @Test
    void rotSpriteKeepsAThinWallConnected() {
        VoxelVolume wall = new VoxelVolume(0, 0, 0, 24, 1, 1);
        for (int x = 0; x < 24; x++) {
            wall.set(x, 0, 0, 1);
        }
        for (double angle : new double[]{20.0, 30.0, 37.0, 45.0, 60.0, 75.0}) {
            VoxelVolume smooth = ClipboardTransform.rotate(wall, angle, ClipboardTransform.Mode.ROTSPRITE, LIMIT,
                    NO_CANCEL);
            assertEquals(1, components(smooth), "RotSprite a " + angle + "° parte el muro");
            // Una diagonal conectada por aristas necesita menos bloques que el largo (a 45°, 24/√2 ≈ 17).
            double minimum = 24.0 * Math.max(Math.abs(Math.cos(Math.toRadians(angle))),
                    Math.abs(Math.sin(Math.toRadians(angle))));
            assertTrue(smooth.blocks() >= Math.floor(minimum) && smooth.blocks() < 24 * 2.2,
                    angle + "°: " + smooth.blocks() + " bloques");
        }
    }

    @Test
    void rotatedWallsStayClosed() {
        VoxelVolume tower = new VoxelVolume(-5, 0, -5, 11, 3, 11);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.abs(x) == 4 || Math.abs(z) == 4) {
                    for (int y = 0; y < 3; y++) {
                        tower.set(x, y, z, 1);
                    }
                }
            }
        }
        for (double angle = 5.0; angle < 90.0; angle += 5.0) {
            VoxelVolume turned = ClipboardTransform.rotate(tower, angle, ClipboardTransform.Mode.ROTSPRITE, LIMIT,
                    NO_CANCEL);
            for (int y = 0; y < 3; y++) {
                assertTrue(enclosed(turned, y), "a " + angle + "° el muro de la capa " + y + " tiene un hueco");
            }
        }
    }

    /** ¿Queda el aire del centro encerrado (sin camino por caras hasta el borde de la caja) en la capa {@code y}? */
    private static boolean enclosed(VoxelVolume volume, int y) {
        Set<List<Integer>> seen = new HashSet<>();
        ArrayDeque<List<Integer>> queue = new ArrayDeque<>(List.of(List.of(0, 0)));
        seen.add(List.of(0, 0));
        while (!queue.isEmpty()) {
            List<Integer> cell = queue.poll();
            int x = cell.get(0);
            int z = cell.get(1);
            if (!volume.contains(x, y, z)) {
                return false;
            }
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                List<Integer> next = List.of(x + step[0], z + step[1]);
                if (volume.get(next.get(0), y, next.get(1)) == VoxelVolume.AIR && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return true;
    }

    @Test
    void rotationKeepsTheVolumeRoughly() {
        VoxelVolume box = new VoxelVolume(-6, 0, -4, 13, 4, 9);
        for (int x = -6; x <= 6; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 0; y < 4; y++) {
                    box.set(x, y, z, 1);
                }
            }
        }
        for (ClipboardTransform.Mode mode : List.of(ClipboardTransform.Mode.NEAREST,
                ClipboardTransform.Mode.ROTSPRITE)) {
            for (double angle : new double[]{15.0, 37.0, 45.0, -60.0}) {
                int turned = ClipboardTransform.rotate(box, angle, mode, LIMIT, NO_CANCEL).blocks();
                double error = Math.abs(turned - box.blocks()) / (double) box.blocks();
                assertTrue(error < 0.1, mode + " " + angle + "°: " + turned + " frente a " + box.blocks());
            }
        }
    }

    @Test
    void transformsAreDeterministic() {
        VoxelVolume volume = randomVolume(new Random(9), -4, 0, -4, 9, 5, 9);
        for (ClipboardTransform.Mode mode : ClipboardTransform.Mode.values()) {
            assertEquals(cells(ClipboardTransform.rotate(volume, 33.0, mode, LIMIT, NO_CANCEL)),
                    cells(ClipboardTransform.rotate(volume, 33.0, mode, LIMIT, NO_CANCEL)));
            assertEquals(cells(ClipboardTransform.scale(volume, 1.7, mode, LIMIT, NO_CANCEL)),
                    cells(ClipboardTransform.scale(volume, 1.7, mode, LIMIT, NO_CANCEL)));
        }
    }

    // --------------------------------------------------------------------------------------------- Estados

    @Test
    void statesTurnWithTheStructure() {
        Map<String, List<String>> stairs = Map.of(
                "facing", List.of("north", "south", "west", "east"),
                "half", List.of("top", "bottom"),
                "shape", List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"));
        Map<String, String> north = state("facing", "north", "half", "bottom", "shape", "inner_left");
        assertEquals("east", ClipboardStates.rotate(north, stairs, 90).get("facing"));
        assertEquals("south", ClipboardStates.rotate(north, stairs, 180).get("facing"));
        assertEquals("west", ClipboardStates.rotate(north, stairs, -90).get("facing"));
        assertEquals("north", ClipboardStates.rotate(north, stairs, 30).get("facing"), "30° se queda");
        assertEquals("east", ClipboardStates.rotate(north, stairs, 60).get("facing"), "60° redondea a 90");
        assertEquals("inner_left", ClipboardStates.rotate(north, stairs, 90).get("shape"), "girar no refleja");
        assertEquals("bottom", ClipboardStates.rotate(north, stairs, 90).get("half"));

        Map<String, List<String>> log = Map.of("axis", List.of("x", "y", "z"));
        assertEquals("z", ClipboardStates.rotate(state("axis", "x"), log, 90).get("axis"));
        assertEquals("x", ClipboardStates.rotate(state("axis", "x"), log, 180).get("axis"));
        assertEquals("y", ClipboardStates.rotate(state("axis", "y"), log, 90).get("axis"));

        Map<String, List<String>> sign = Map.of("rotation", List.of("0", "1", "2", "3", "4", "5", "6", "7", "8",
                "9", "10", "11", "12", "13", "14", "15"));
        assertEquals("4", ClipboardStates.rotate(state("rotation", "0"), sign, 90).get("rotation"));
        assertEquals("2", ClipboardStates.rotate(state("rotation", "0"), sign, 45).get("rotation"));
        assertEquals("15", ClipboardStates.rotate(state("rotation", "1"), sign, -45).get("rotation"));

        Map<String, List<String>> fence = Map.of("north", List.of("true", "false"), "east", List.of("true", "false"),
                "south", List.of("true", "false"), "west", List.of("true", "false"));
        Map<String, String> turned = ClipboardStates.rotate(state("north", "true", "east", "false", "south",
                "false", "west", "true"), fence, 90);
        assertEquals("true", turned.get("east"));
        assertEquals("true", turned.get("north"));
        assertEquals("false", turned.get("south"));
        assertEquals("false", turned.get("west"));

        Map<String, List<String>> rail = Map.of("shape", List.of("north_south", "east_west", "ascending_east",
                "ascending_west", "ascending_north", "ascending_south", "south_east", "south_west", "north_west",
                "north_east"));
        assertEquals("east_west", ClipboardStates.rotate(state("shape", "north_south"), rail, 90).get("shape"));
        assertEquals("ascending_south", ClipboardStates.rotate(state("shape", "ascending_east"), rail, 90)
                .get("shape"));
    }

    // --------------------------------------------------------------------------------------------- Ayudas

    /** Una imagen {@code [z][x]} como capa de un bloque de alto con la esquina en el origen. */
    static VoxelVolume layer(int[][] image) {
        VoxelVolume volume = new VoxelVolume(0, 0, 0, image[0].length, 1, image.length);
        for (int z = 0; z < image.length; z++) {
            for (int x = 0; x < image[z].length; x++) {
                volume.set(x, 0, z, image[z][x]);
            }
        }
        return volume;
    }

    static int[][] slice(VoxelVolume volume, int y) {
        int[][] image = new int[volume.sizeZ()][volume.sizeX()];
        for (int z = 0; z < volume.sizeZ(); z++) {
            for (int x = 0; x < volume.sizeX(); x++) {
                image[z][x] = volume.get(volume.minimumX() + x, volume.minimumY() + y, volume.minimumZ() + z);
            }
        }
        return image;
    }

    private static int at(int[][] image, int x, int z) {
        return z < 0 || x < 0 || z >= image.length || x >= image[0].length ? 0 : image[z][x];
    }

    /** Scale2x 2D de referencia (Andrea Mazzoleni), con aire fuera de la imagen. */
    static int[][] reference2x(int[][] image) {
        int[][] out = new int[image.length * 2][image[0].length * 2];
        for (int z = 0; z < image.length; z++) {
            for (int x = 0; x < image[0].length; x++) {
                int e = image[z][x];
                int b = at(image, x, z - 1);
                int d = at(image, x - 1, z);
                int f = at(image, x + 1, z);
                int h = at(image, x, z + 1);
                int[] result = {e, e, e, e};
                if (b != h && d != f) {
                    result[0] = d == b ? d : e;
                    result[1] = b == f ? f : e;
                    result[2] = d == h ? d : e;
                    result[3] = h == f ? f : e;
                }
                out[z * 2][x * 2] = result[0];
                out[z * 2][x * 2 + 1] = result[1];
                out[z * 2 + 1][x * 2] = result[2];
                out[z * 2 + 1][x * 2 + 1] = result[3];
            }
        }
        return out;
    }

    /** Scale3x 2D de referencia (AdvMAME3x), con aire fuera de la imagen. */
    static int[][] reference3x(int[][] image) {
        int[][] out = new int[image.length * 3][image[0].length * 3];
        for (int z = 0; z < image.length; z++) {
            for (int x = 0; x < image[0].length; x++) {
                int a = at(image, x - 1, z - 1);
                int b = at(image, x, z - 1);
                int c = at(image, x + 1, z - 1);
                int d = at(image, x - 1, z);
                int e = image[z][x];
                int f = at(image, x + 1, z);
                int g = at(image, x - 1, z + 1);
                int h = at(image, x, z + 1);
                int i = at(image, x + 1, z + 1);
                int[] r = {e, e, e, e, e, e, e, e, e};
                if (b != h && d != f) {
                    r[0] = d == b ? d : e;
                    r[1] = (d == b && e != c) || (b == f && e != a) ? b : e;
                    r[2] = b == f ? f : e;
                    r[3] = (d == b && e != g) || (d == h && e != a) ? d : e;
                    r[5] = (b == f && e != i) || (h == f && e != c) ? f : e;
                    r[6] = d == h ? d : e;
                    r[7] = (d == h && e != i) || (h == f && e != g) ? h : e;
                    r[8] = h == f ? f : e;
                }
                for (int k = 0; k < 9; k++) {
                    out[z * 3 + k / 3][x * 3 + k % 3] = r[k];
                }
            }
        }
        return out;
    }

    static VoxelVolume sphere(double radius, int id) {
        int half = (int) Math.ceil(radius) + 1;
        VoxelVolume volume = new VoxelVolume(-half, -half, -half, half * 2 + 1, half * 2 + 1, half * 2 + 1);
        for (int x = -half; x <= half; x++) {
            for (int y = -half; y <= half; y++) {
                for (int z = -half; z <= half; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        volume.set(x, y, z, id);
                    }
                }
            }
        }
        return volume;
    }

    static VoxelVolume randomVolume(Random random, int x0, int y0, int z0, int sx, int sy, int sz) {
        VoxelVolume volume = new VoxelVolume(x0, y0, z0, sx, sy, sz);
        for (int x = x0; x < x0 + sx; x++) {
            for (int y = y0; y < y0 + sy; y++) {
                for (int z = z0; z < z0 + sz; z++) {
                    if (random.nextInt(3) == 0) {
                        volume.set(x, y, z, 1 + random.nextInt(4));
                    }
                }
            }
        }
        return volume;
    }

    private static int count(int[][] image, int id) {
        int count = 0;
        for (int[] row : image) {
            for (int value : row) {
                if (value == id) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Map<String, String> state(String... pairs) {
        Map<String, String> state = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            state.put(pairs[index], pairs[index + 1]);
        }
        return state;
    }

    private static Set<List<Integer>> cells(VoxelVolume volume) {
        Set<List<Integer>> cells = new HashSet<>();
        volume.forEach((x, y, z, id) -> cells.add(List.of(x, y, z, id)));
        return cells;
    }

    /** Piezas conexas por caras y aristas (8 vecinos en cada capa y arriba/abajo). */
    static int components(VoxelVolume volume) {
        Set<List<Integer>> cells = new HashSet<>();
        volume.forEach((x, y, z, id) -> cells.add(List.of(x, y, z)));
        Set<List<Integer>> seen = new HashSet<>();
        int count = 0;
        for (List<Integer> start : cells) {
            if (!seen.add(start)) {
                continue;
            }
            count++;
            ArrayDeque<List<Integer>> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                List<Integer> cell = queue.poll();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 2) {
                                continue;
                            }
                            List<Integer> next = List.of(cell.get(0) + dx, cell.get(1) + dy, cell.get(2) + dz);
                            if (cells.contains(next) && seen.add(next)) {
                                queue.add(next);
                            }
                        }
                    }
                }
            }
        }
        return count;
    }
}
