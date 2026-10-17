package com.komainu.moxia.generator;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.GeometryLimitException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeVoxelizerTest {
    private static final int[] ORIGIN = {100, 64, -40};
    private static final double[] LOOK_SOUTH = {0, 0, 1};

    private static GeneratedBlocks shape(String form, String args) {
        return shape(form, args, LOOK_SOUTH, 0.0);
    }

    private static GeneratedBlocks shape(String form, String args, double[] look, double yaw) {
        return ShapeVoxelizer.generate(ShapeRequest.parse(ShapeRequest.Form.byId(form), args), ORIGIN, look, yaw,
                2_000_000);
    }

    private static Set<Long> set(GeneratedBlocks blocks) {
        Set<Long> result = new HashSet<>();
        for (int index = 0; index < blocks.size(); index++) {
            result.add(blocks.packed(index));
        }
        assertEquals(blocks.size(), result.size(), "sin bloques repetidos");
        return result;
    }

    private static int[] relativeBounds(GeneratedBlocks blocks) {
        int[] bounds = blocks.bounds();
        return new int[]{bounds[0] - ORIGIN[0], bounds[1] - ORIGIN[1], bounds[2] - ORIGIN[2],
                bounds[3] - ORIGIN[0], bounds[4] - ORIGIN[1], bounds[5] - ORIGIN[2]};
    }

    @Test
    void squareCountsFacesAndWalls() {
        assertEquals(13 * 13 * 4, shape("square", "stone 6 4").size());
        assertEquals(13 * 13 * 4 - 11 * 11 * 4, shape("square", "stone 6 4 -w").size());
        assertEquals(13 * 13 * 4 - 11 * 11 * 2, shape("square", "stone 6 4 -f").size());
        assertArrayEquals(new int[]{-6, 0, -6, 6, 3, 6}, relativeBounds(shape("square", "stone 6 4")));
    }

    @Test
    void coneHasRadiusPlusHalfAndSitsOnTheFeet() {
        GeneratedBlocks cone = shape("cone", "stone 4 8");
        assertArrayEquals(new int[]{-4, 0, -4, 4, 7, 4}, relativeBounds(cone));
        // La cúspide es un solo bloque sobre el eje.
        int top = 0;
        for (int index = 0; index < cone.size(); index++) {
            if (cone.y(index) == ORIGIN[1] + 7) {
                top++;
                assertEquals(ORIGIN[0], cone.x(index));
                assertEquals(ORIGIN[2], cone.z(index));
            }
        }
        assertEquals(1, top);
    }

    @Test
    void coneRadiiFollowNorthSouthAndEastWestAndRotate() {
        int[] straight = relativeBounds(shape("cone", "stone 8,3 4"));
        assertEquals(8, straight[5], "el primer radio va de norte a sur (z)");
        assertEquals(3, straight[3], "el segundo de este a oeste (x)");
        int[] turned = relativeBounds(shape("cone", "stone 8,3 4 90"));
        assertEquals(8, turned[3]);
        assertEquals(3, turned[5]);
        int[] oriented = relativeBounds(shape("cone", "stone 8,3 4 -o", LOOK_SOUTH, 90.0));
        assertArrayEquals(turned, oriented, "-o suma el giro de la mirada");
    }

    @Test
    void downwardConeFlips() {
        GeneratedBlocks up = shape("cone", "stone 5 9");
        GeneratedBlocks down = shape("cone", "stone 5 9 -d");
        assertEquals(up.size(), down.size());
        Set<Long> flipped = new HashSet<>();
        for (int index = 0; index < up.size(); index++) {
            flipped.add(PackedBlockPlan.pack(up.x(index), 2 * ORIGIN[1] + 8 - up.y(index), up.z(index)));
        }
        assertEquals(flipped, set(down));
    }

    @Test
    void hollowShapesAreShellsInsideTheSolid() {
        for (String[] spec : new String[][]{
                {"cone", "stone 6 10"}, {"tetrahedron", "stone 9"}, {"torus", "stone 9 3 circle"},
                {"torus", "stone 9 3 square"}}) {
            Set<Long> solid = set(shape(spec[0], spec[1]));
            Set<Long> shell = set(shape(spec[0], spec[1] + " -h"));
            assertTrue(solid.containsAll(shell), spec[0]);
            assertTrue(shell.size() < solid.size(), spec[0]);
            for (long key : shell) {
                int x = PackedBlockPlan.unpackX(key);
                int y = PackedBlockPlan.unpackY(key);
                int z = PackedBlockPlan.unpackZ(key);
                boolean touchesOutside = !solid.contains(PackedBlockPlan.pack(x + 1, y, z))
                        || !solid.contains(PackedBlockPlan.pack(x - 1, y, z))
                        || !solid.contains(PackedBlockPlan.pack(x, y + 1, z))
                        || !solid.contains(PackedBlockPlan.pack(x, y - 1, z))
                        || !solid.contains(PackedBlockPlan.pack(x, y, z + 1))
                        || !solid.contains(PackedBlockPlan.pack(x, y, z - 1));
                assertTrue(touchesOutside, spec[0] + " sin bloques enterrados");
            }
        }
    }

    @Test
    void hollowConeIsOpenAtTheBase() {
        Set<Long> shell = set(shape("cone", "stone 6 10 -h"));
        assertFalse(shell.contains(PackedBlockPlan.pack(ORIGIN[0], ORIGIN[1], ORIGIN[2])),
                "el centro de la base queda abierto");
    }

    @Test
    void polydomeIsAnOpenShellWithEdges() {
        GeneratedBlocks dome = shape("polydome", "stone 8 10 1 -v gold_block");
        Set<Long> blocks = set(dome);
        assertFalse(blocks.contains(PackedBlockPlan.pack(ORIGIN[0], ORIGIN[1], ORIGIN[2])), "suelo abierto");
        assertFalse(blocks.contains(PackedBlockPlan.pack(ORIGIN[0], ORIGIN[1] + 5, ORIGIN[2])), "hueco");
        assertTrue(blocks.contains(PackedBlockPlan.pack(ORIGIN[0], ORIGIN[1] + 10, ORIGIN[2])), "cúspide");
        int edges = 0;
        for (int index = 0; index < dome.size(); index++) {
            edges += dome.material(index);
        }
        assertTrue(edges > 0 && edges < dome.size() / 2, "aristas: " + edges + " de " + dome.size());
        GeneratedBlocks plain = shape("polydome", "stone 8 10 1");
        for (int index = 0; index < plain.size(); index++) {
            assertEquals(0, plain.material(index));
        }
        int[] low = relativeBounds(shape("polydome", "stone 8 10 0.5"));
        assertEquals(4, low[4], "la altura es una proporción del radio (10,5 × 0,5)");
    }

    @Test
    void polygonFollowsItsDirection() {
        int[] up = relativeBounds(shape("polygon", "stone 6 5 3 up"));
        assertEquals(0, up[1]);
        assertEquals(2, up[4]);
        int[] east = relativeBounds(shape("polygon", "stone 6 5 3 east"));
        assertEquals(0, east[0]);
        assertEquals(2, east[3]);
        int[] aimed = relativeBounds(shape("polygon", "stone 6 5 3", new double[]{0.05, -0.1, -1}, 0));
        assertEquals(-2, aimed[2], "sin dirección usa la mirada redondeada (norte)");
        assertEquals(0, aimed[5]);
    }

    @Test
    void diagonalPolygonHasNoHoles() {
        GeneratedBlocks plate = shape("polygon", "stone 8 6 1 upnortheast");
        Set<Long> blocks = set(plate);
        // Cada bloque tiene algún vecino de los 26: no hay bloques sueltos, y la placa es gruesa en su normal.
        for (long key : blocks) {
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            boolean neighbour = false;
            for (int dx = -1; dx <= 1 && !neighbour; dx++) {
                for (int dy = -1; dy <= 1 && !neighbour; dy++) {
                    for (int dz = -1; dz <= 1 && !neighbour; dz++) {
                        neighbour = (dx | dy | dz) != 0 && blocks.contains(PackedBlockPlan.pack(x + dx, y + dy, z + dz));
                    }
                }
            }
            assertTrue(neighbour);
        }
        assertTrue(blocks.contains(PackedBlockPlan.pack(ORIGIN[0], ORIGIN[1], ORIGIN[2])));
    }

    @Test
    void tetrahedronIsRegular() {
        int[] bounds = relativeBounds(shape("tetrahedron", "stone 10"));
        assertEquals(0, bounds[1]);
        assertEquals((int) Math.floor(10.5 * Math.sqrt(2) - 0.5), bounds[4]);
    }

    @Test
    void torusCrossSectionsAndOrientation() {
        int circle = shape("torus", "stone 10 3 circle").size();
        int diamond = shape("torus", "stone 10 3 diamond").size();
        int rounded = shape("torus", "stone 10 3 rounded_square").size();
        int square = shape("torus", "stone 10 3 square").size();
        assertTrue(diamond < circle && circle < rounded && rounded < square);
        assertArrayEquals(new int[]{-13, -3, -13, 13, 3, 13}, relativeBounds(shape("torus", "stone 10 3")));
        int[] standing = relativeBounds(shape("torus", "stone 10 3 circle -o", LOOK_SOUTH, 0));
        assertArrayEquals(new int[]{-13, -13, -3, 13, 13, 3}, standing, "-o: el aro mira hacia la mirada");
    }

    @Test
    void limitIsEnforced() {
        ShapeRequest request = ShapeRequest.parse(ShapeRequest.Form.SQUARE, "stone 20 20");
        assertThrows(GeometryLimitException.class, () -> ShapeVoxelizer.generate(request, ORIGIN, LOOK_SOUTH, 0,
                1_000));
    }

    @Test
    void parserValidatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> ShapeRequest.parse(ShapeRequest.Form.CONE, "stone"));
        assertThrows(IllegalArgumentException.class, () -> ShapeRequest.parse(ShapeRequest.Form.POLYGON,
                "stone 2 5"));
        assertThrows(IllegalArgumentException.class, () -> ShapeRequest.parse(ShapeRequest.Form.TORUS,
                "stone 5 6"));
        assertThrows(IllegalArgumentException.class, () -> ShapeRequest.parse(ShapeRequest.Form.TORUS,
                "stone 5 2 hexagon"));
        assertThrows(IllegalArgumentException.class, () -> ShapeRequest.parse(ShapeRequest.Form.SQUARE,
                "stone 5 2 3"));
        assertEquals(ShapeRequest.Cross.ROUNDED_SQUARE, ShapeRequest.Cross.byId("ROUNDED_SQUARE"));
        assertEquals(ShapeRequest.Form.TORUS, ShapeRequest.Form.byId("toro"));
        assertEquals("gold_block", ShapeRequest.parse(ShapeRequest.Form.POLYDOME, "stone 6 8 -v gold_block")
                .vertexPattern());
    }

    @Test
    void directionsAndSnapping() {
        assertArrayEquals(new double[]{1, 0, -1}, ShapeRequest.direction("northeast"));
        assertArrayEquals(new double[]{-1, 1, 0}, ShapeRequest.direction("up-west"));
        assertArrayEquals(new double[]{0, -1, 1}, ShapeRequest.direction("ds"));
        assertNull(ShapeRequest.direction("me"));
        assertThrows(IllegalArgumentException.class, () -> ShapeRequest.direction("northsouth"));
        assertThrows(IllegalArgumentException.class, () -> ShapeRequest.direction("nn"));
        assertThrows(IllegalArgumentException.class, () -> ShapeRequest.direction("sideways"));
        assertArrayEquals(new double[]{0, 0, 1}, ShapeRequest.snap(0.1, 0.2, 0.97));
        assertArrayEquals(new double[]{1, -1, 0}, ShapeRequest.snap(0.7, -0.7, 0.05));
    }
}
