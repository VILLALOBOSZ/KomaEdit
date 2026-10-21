package com.komainu.moxia.blueprint;

import com.komainu.moxia.brush.ez.BrushWorld;
import com.komainu.moxia.symmetry.StateTransform;
import com.komainu.moxia.symmetry.SymmetryTransform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Separación, aleatoriedad determinista, giros y apoyo en el suelo del pincel «estampar». */
class StampPlannerTest {
    /** Terreno de prueba: lo que está por debajo de {@code height(x, z)} es piedra, lo de arriba aire. */
    private record Terrain(IntBinaryOperator height, int radius) implements BrushWorld {
        @Override
        public String blockId(int x, int y, int z) {
            return y <= height.applyAsInt(x, z) ? "minecraft:stone" : "minecraft:air";
        }

        @Override
        public Cell cell(int x, int y, int z) {
            return y <= height.applyAsInt(x, z) ? Cell.FULL : Cell.AIR;
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return Math.abs(x) <= radius && Math.abs(z) <= radius && y >= -64 && y <= 128;
        }
    }

    private static final Terrain FLAT = new Terrain((x, z) -> 63, 64);
    /** Ladera que sube un bloque por cada dos hacia +X. */
    private static final Terrain SLOPE = new Terrain((x, z) -> 63 + Math.floorDiv(x, 2), 64);

    /** Roca de 3×2×3 con una columna central más baja (sobresale por abajo). */
    private static StampShape rock() {
        int[] xs = {-1, 0, 1, -1, 0, 1, -1, 0, 1, 0, 0};
        int[] zs = {-1, -1, -1, 0, 0, 0, 1, 1, 1, 0, 0};
        int[] ys = {1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 2};
        return StampShape.of(xs, ys, zs, xs.length);
    }

    private static StampSettings settings(String text) {
        return StampSettings.parse("#rocas " + text);
    }

    private static StampPlanner.Result plan(StampSettings settings, Terrain terrain, long seed, List<int[]> occupied) {
        return StampPlanner.plan(settings, List.of(rock(), rock()), new double[]{1, 3}, 0, 64, 0, 0, terrain, null,
                occupied, seed);
    }

    @Test
    void stampsKeepTheirDistanceInsideTheCircle() {
        StampSettings settings = settings("12 -d 5 -c 64");
        for (long seed = 0; seed < 20; seed++) {
            List<StampPlanner.Stamp> stamps = plan(settings, FLAT, seed, List.of()).stamps();
            assertTrue(stamps.size() >= 8, "un círculo de radio 12 da para bastantes: " + stamps.size());
            for (int first = 0; first < stamps.size(); first++) {
                StampPlanner.Stamp a = stamps.get(first);
                assertTrue(Math.hypot(a.x(), a.z()) <= 12.5 + 0.71, "dentro del círculo");
                for (int second = first + 1; second < stamps.size(); second++) {
                    StampPlanner.Stamp b = stamps.get(second);
                    assertTrue(Math.hypot(a.x() - b.x(), a.z() - b.z()) >= 5, "separación mínima");
                }
            }
        }
    }

    @Test
    void earlierStampsAlsoKeepTheNewOnesAway() {
        StampSettings settings = settings("10 -d 6");
        List<int[]> occupied = List.of(new int[]{0, 0}, new int[]{5, 5}, new int[]{-6, 2});
        for (long seed = 0; seed < 10; seed++) {
            for (StampPlanner.Stamp stamp : plan(settings, FLAT, seed, occupied).stamps()) {
                for (int[] point : occupied) {
                    assertTrue(Math.hypot(stamp.x() - point[0], stamp.z() - point[1]) >= 6);
                }
            }
        }
    }

    @Test
    void theSameSeedGivesTheSameStampsAndAnotherSeedChangesThem() {
        StampSettings settings = settings("10 -n 70");
        List<StampPlanner.Stamp> first = plan(settings, SLOPE, 42L, List.of()).stamps();
        List<StampPlanner.Stamp> again = plan(settings, SLOPE, 42L, List.of()).stamps();
        assertEquals(first, again);
        Set<List<StampPlanner.Stamp>> different = new HashSet<>();
        for (long seed = 0; seed < 8; seed++) {
            different.add(plan(settings, SLOPE, seed, List.of()).stamps());
        }
        assertTrue(different.size() >= 7, "las semillas cambian el resultado");
    }

    @Test
    void randomRotationFlipsAndWeightsAllShowUp() {
        StampSettings settings = settings("16 -d 2 -c 64");
        int[] turns = new int[4];
        int flipped = 0;
        int[] blueprints = new int[2];
        int total = 0;
        for (long seed = 0; seed < 30; seed++) {
            for (StampPlanner.Stamp stamp : plan(settings, FLAT, seed, List.of()).stamps()) {
                turns[stamp.quarterTurns()]++;
                flipped += stamp.flipX() || stamp.flipZ() ? 1 : 0;
                blueprints[stamp.blueprint()]++;
                total++;
            }
        }
        for (int count : turns) {
            assertTrue(count > total / 8, "los cuatro giros salen");
        }
        assertTrue(flipped > total / 2, "con -f xz se voltean tres de cada cuatro");
        double heavy = blueprints[1] / (double) total;
        assertTrue(heavy > 0.65 && heavy < 0.85, "el peso 3:1 se nota: " + heavy);
    }

    @Test
    void fixedRotationAndNoFlipsAreRespected() {
        for (StampPlanner.Stamp stamp : plan(settings("8 -r 90 -f no"), FLAT, 3L, List.of()).stamps()) {
            assertEquals(1, stamp.quarterTurns());
            assertFalse(stamp.flipX() || stamp.flipZ());
        }
        StampSettings look = settings("0 -r mirada");
        StampPlanner.Stamp stamp = StampPlanner.plan(look, List.of(rock()), new double[]{1}, 0, 64, 0, 3, FLAT,
                null, List.of(), 1L).stamps().getFirst();
        assertEquals(3, stamp.quarterTurns());
    }

    @Test
    void probabilityThinsTheStamps() {
        int all = 0;
        int some = 0;
        for (long seed = 0; seed < 20; seed++) {
            all += plan(settings("12 -d 4 -c 64"), FLAT, seed, List.of()).stamps().size();
            some += plan(settings("12 -d 4 -c 64 -n 30"), FLAT, seed, List.of()).stamps().size();
            assertTrue(plan(settings("12 -n 0"), FLAT, seed, List.of()).stamps().isEmpty());
        }
        double ratio = some / (double) all;
        assertTrue(ratio > 0.2 && ratio < 0.42, "con -n 30 sale cerca de un 30 %: " + ratio);
    }

    @Test
    void radiusZeroPutsOneStampWhereYouLookEvenOverAnEarlierOne() {
        List<StampPlanner.Stamp> stamps = plan(settings("0"), FLAT, 9L, List.of(new int[]{0, 0})).stamps();
        assertEquals(1, stamps.size());
        assertEquals(0, stamps.getFirst().x());
        assertEquals(0, stamps.getFirst().z());
    }

    @Test
    void restingGravityTouchesTheGroundWithoutSinking() {
        StampSettings settings = settings("10 -f no");
        for (long seed = 0; seed < 10; seed++) {
            for (StampPlanner.Stamp stamp : plan(settings, SLOPE, seed, List.of()).stamps()) {
                int[] matrix = stamp.matrix();
                boolean touches = false;
                for (StampShape.Column column : rock().columns()) {
                    int x = stamp.x() + matrix[0] * column.x() + matrix[2] * column.z();
                    int z = stamp.z() + matrix[6] * column.x() + matrix[8] * column.z();
                    int bottom = stamp.y() + column.bottom();
                    int ground = SLOPE.height().applyAsInt(x, z);
                    assertTrue(bottom > ground, "ningún bloque dentro del suelo");
                    touches |= bottom == ground + 1;
                }
                assertTrue(touches, "y alguno lo toca");
            }
        }
    }

    @Test
    void centerGravityAndOffsetsFollowTheGroundUnderTheOrigin() {
        StampPlanner.Stamp onFlat = plan(settings("0 -g centro"), FLAT, 1L, List.of()).stamps().getFirst();
        assertEquals(64, onFlat.y(), "la columna más baja del plano (y 0) queda sobre el suelo (63)");
        StampPlanner.Stamp sunk = plan(settings("0 -g centro -y -1"), FLAT, 1L, List.of()).stamps().getFirst();
        assertEquals(63, sunk.y());
        StampPlanner.Stamp floating = plan(settings("0 -g no"), FLAT, 1L, List.of()).stamps().getFirst();
        assertEquals(64, floating.y());
        // Clic en la ladera a la altura de su suelo en x = 8: el apoyo se busca hacia arriba y hacia abajo.
        StampPlanner.Stamp uphill = StampPlanner.plan(settings("0 -g centro"), List.of(rock()), new double[]{1}, 8,
                64, 0, 0, SLOPE, null, List.of(), 1L).stamps().getFirst();
        assertEquals(SLOPE.height().applyAsInt(8, 0) + 1, uphill.y());
    }

    @Test
    void withoutGroundNothingIsStamped() {
        Terrain hole = new Terrain((x, z) -> -1_000, 64);
        StampPlanner.Result result = plan(settings("0"), hole, 1L, List.of());
        assertTrue(result.stamps().isEmpty());
        assertEquals(1, result.withoutGround());
    }

    @Test
    void quarterTurnsAndFlipsAreProperMatrices() {
        assertArrayEquals(new int[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, StampPlanner.Stamp.matrix(4, false, false));
        assertArrayEquals(new int[]{0, 0, -1, 0, 1, 0, 1, 0, 0}, StampPlanner.Stamp.matrix(1, false, false));
        List<int[]> seen = new ArrayList<>();
        for (int turns = 0; turns < 4; turns++) {
            for (int flips = 0; flips < 4; flips++) {
                int[] m = StampPlanner.Stamp.matrix(turns, (flips & 1) != 0, (flips & 2) != 0);
                int determinant = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6])
                        + m[2] * (m[3] * m[7] - m[4] * m[6]);
                assertEquals(Math.abs(determinant), 1);
                assertEquals(1, m[4], "el arriba no cambia");
                seen.add(m);
            }
        }
        assertEquals(8, seen.stream().map(java.util.Arrays::toString).distinct().count(),
                "las 8 simetrías del cuadrado");
    }

    @Test
    void settingsParseTheDefaultsAndRejectNonsense() {
        StampSettings defaults = StampSettings.parse("#rocas");
        assertEquals(0, defaults.radius());
        assertEquals(1.0, defaults.probability());
        assertTrue(defaults.autoSpacing());
        assertEquals(StampSettings.Rotation.RANDOM, defaults.rotation());
        assertTrue(defaults.flipX() && defaults.flipZ());
        assertEquals(StampSettings.Gravity.REST, defaults.gravity());
        StampSettings full = StampSettings.parse("roca_1:3,servidor/ruina 8 -n 40 -d 6 -c 5 -r 180 -f x -g no -y -2"
                + " -m grass_block -a -p");
        assertEquals(8, full.radius());
        assertEquals(0.4, full.probability(), 1e-9);
        assertEquals(6, full.spacing());
        assertEquals(5, full.maxStamps());
        assertEquals(2, full.fixedTurns());
        assertTrue(full.flipX() && !full.flipZ());
        assertEquals(StampSettings.Gravity.NONE, full.gravity());
        assertEquals(-2, full.offsetY());
        assertEquals("grass_block", full.mask());
        assertTrue(full.placeAir() && full.preview());
        for (String wrong : new String[]{"", "#rocas 40", "#rocas -n 120", "#rocas -r 45", "#rocas 4 5",
                "../x", "#rocas -g volar"}) {
            assertThrows(IllegalArgumentException.class, () -> StampSettings.parse(wrong), wrong);
        }
        assertNotEquals(0, StampPlanner.autoSpacing(List.of(rock())));
    }

    /**
     * Los estados de cada plano estampado giran y se voltean como sus posiciones, con la transformación de la
     * simetría: también los raíles (rectos, en curva y en rampa) y las bisagras de las puertas, que el giro de
     * WorldEdit deja como estaban.
     */
    @Test
    void stampedStatesTurnAndFlipLikeTheirPositions() {
        int[][] sides = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (int turns = 0; turns < 4; turns++) {
            for (boolean flipX : new boolean[]{false, true}) {
                for (boolean flipZ : new boolean[]{false, true}) {
                    StampPlanner.Stamp stamp = new StampPlanner.Stamp(0, 0, 0, 0, turns, flipX, flipZ);
                    int[] m = stamp.matrix();
                    SymmetryTransform transform = stamp.symmetry();
                    for (int[] side : sides) {
                        assertArrayEquals(new int[]{m[0] * side[0] + m[2] * side[1], m[6] * side[0] + m[8] * side[1]},
                                transform.direction(side[0], side[1]), stamp.toString());
                    }
                }
            }
        }
        Map<String, List<String>> rail = Map.of("shape", List.of("north_south", "east_west", "ascending_east",
                "ascending_west", "ascending_north", "ascending_south", "south_east", "south_west", "north_west",
                "north_east"), "waterlogged", List.of("true", "false"));
        SymmetryTransform quarter = new StampPlanner.Stamp(0, 0, 0, 0, 1, false, false).symmetry();
        assertEquals("east_west", StateTransform.apply(Map.of("shape", "north_south", "waterlogged", "false"), rail,
                quarter).get("shape"));
        assertEquals("ascending_south", StateTransform.apply(Map.of("shape", "ascending_east", "waterlogged",
                "false"), rail, quarter).get("shape"));
        assertEquals("south_west", StateTransform.apply(Map.of("shape", "south_east", "waterlogged", "false"), rail,
                quarter).get("shape"));
        Map<String, List<String>> door = Map.of("facing", List.of("north", "south", "west", "east"), "half",
                List.of("upper", "lower"), "hinge", List.of("left", "right"), "open", List.of("true", "false"),
                "powered", List.of("true", "false"));
        assertEquals("right", StateTransform.apply(Map.of("facing", "north", "half", "lower", "hinge", "left", "open",
                "false", "powered", "false"), door, new StampPlanner.Stamp(0, 0, 0, 0, 0, true, false).symmetry())
                .get("hinge"));
    }
}
