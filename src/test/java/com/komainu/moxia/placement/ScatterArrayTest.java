package com.komainu.moxia.placement;

import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.tube.TubeSettings;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScatterArrayTest {
    static final RegionSnapshot.StateInfo STONE = new RegionSnapshot.StateInfo(1, null, "minecraft:stone", false,
            false, true);
    static final RegionSnapshot.StateInfo WOOL = new RegionSnapshot.StateInfo(2, null, "minecraft:red_wool", false,
            false, true);

    /** Suelo de piedra de 64×64 a y = 0, con un muro de 64×8 y 4 de grueso en z = 40–43 y lana en una esquina. */
    static RegionSnapshot terrain() {
        RegionSnapshot.Builder builder = RegionSnapshot.builder(-3, -3, -3, 70, 20, 70)
                .state(STONE).state(WOOL);
        for (int x = -3; x < 67; x++) {
            for (int z = -3; z < 67; z++) {
                for (int y = -3; y < 17; y++) {
                    boolean inside = x >= 0 && x < 64 && z >= 0 && z < 64 && y >= 0 && y < 14;
                    int id = 0;
                    if (y <= 0) {
                        id = x < 8 && z < 8 ? 2 : 1;
                    } else if (z >= 40 && z <= 43 && y <= 8) {
                        id = 1;
                    }
                    builder.set(x, y, z, id, inside);
                }
            }
        }
        return builder.build();
    }

    private static ScatterPlanner.Settings settings(double density, int iterations, long seed) {
        return new ScatterPlanner.Settings(List.of(), 0.5, density, iterations, seed);
    }

    @Test
    void surfacesAndNormalsComeFromTheSnapshot() {
        RegionSnapshot snapshot = terrain();
        ScatterPlanner.Result all = ScatterPlanner.plan(snapshot, settings(100, 0, 1), null);
        // Suelo (64×64 menos lo que tapa el muro) más los bloques del muro con alguna cara al aire.
        int ground = 64 * 64 - 64 * 4;
        int wall = 64 * 8 * 2 + 64 * 2; // las dos caras de 8 de alto y el interior del canto
        assertEquals(ground + wall, all.found());
        assertEquals(all.found(), all.points().size());
        Vec3 flat = ScatterPlanner.normal(snapshot, 20, 0, 20);
        assertEquals(1.0, flat.y(), 1.0E-9);
        Vec3 face = ScatterPlanner.normal(snapshot, 20, 4, 40);
        assertTrue(Math.abs(face.z()) > 0.9, "la cara del muro mira a lo largo de z: " + face);
    }

    @Test
    void densityCountsRemainingSurfaces() {
        RegionSnapshot snapshot = terrain();
        ScatterPlanner.Result result = ScatterPlanner.plan(snapshot, settings(2, 15, 42), null);
        int expected = (int) Math.round(result.found() * 0.02);
        assertTrue(Math.abs(result.points().size() - expected) <= 1);
        ScatterPlanner.Result none = ScatterPlanner.plan(snapshot, settings(0, 15, 42), null);
        assertEquals(0, none.points().size());
        assertThrows(IllegalArgumentException.class,
                () -> ScatterPlanner.plan(bigFlat(), settings(100, 0, 1), null), "10 000 superficies al 100 %");
    }

    private static RegionSnapshot bigFlat() {
        RegionSnapshot.Builder builder = RegionSnapshot.builder(0, 0, 0, 100, 2, 100).state(STONE);
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                builder.set(x, 0, z, 1, true);
                builder.set(x, 1, z, 0, true);
            }
        }
        return builder.build();
    }

    @Test
    void sameSeedSamePointsOtherSeedOtherPoints() {
        RegionSnapshot snapshot = terrain();
        List<ScatterPlanner.Point> first = ScatterPlanner.plan(snapshot, settings(1, 10, 7), null).points();
        List<ScatterPlanner.Point> again = ScatterPlanner.plan(snapshot, settings(1, 10, 7), null).points();
        List<ScatterPlanner.Point> other = ScatterPlanner.plan(snapshot, settings(1, 10, 8), null).points();
        assertEquals(first, again);
        assertNotEquals(first, other);
    }

    @Test
    void uniformityPushesPointsApart() {
        RegionSnapshot snapshot = bigFlat();
        double random = averageNearest(ScatterPlanner.plan(snapshot, settings(0.5, 0, 3), null).points());
        double uniform = averageNearest(ScatterPlanner.plan(snapshot, settings(0.5, 20, 3), null).points());
        assertTrue(uniform > random * 1.3, "repartidos: " + uniform + " frente a " + random);
        Set<String> distinct = new HashSet<>();
        for (ScatterPlanner.Point point : ScatterPlanner.plan(snapshot, settings(0.5, 20, 3), null).points()) {
            assertTrue(distinct.add(point.x() + "," + point.z()), "dos estructuras en la misma superficie");
        }
    }

    private static double averageNearest(List<ScatterPlanner.Point> points) {
        double total = 0;
        for (ScatterPlanner.Point point : points) {
            double best = Double.POSITIVE_INFINITY;
            for (ScatterPlanner.Point other : points) {
                if (other == point) {
                    continue;
                }
                double dx = point.x() - other.x();
                double dz = point.z() - other.z();
                best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
            }
            total += best;
        }
        return total / points.size();
    }

    @Test
    void directionalAndMaskFiltersRemoveSurfaces() {
        RegionSnapshot snapshot = terrain();
        ScatterPlanner.Result up = ScatterPlanner.plan(snapshot,
                new ScatterPlanner.Settings(List.of(Alignment.UP), 0.9, 100, 0, 1), null);
        for (ScatterPlanner.Point point : up.points()) {
            assertTrue(point.normal().y() >= 0.9);
            assertEquals(point.y() + 1, point.anchorY(), "el origen cae en el aire de encima");
        }
        ScatterPlanner.Result wall = ScatterPlanner.plan(snapshot,
                new ScatterPlanner.Settings(List.of(new Vec3(0, 0, -1)), 0.8, 100, 0, 1), null);
        assertTrue(wall.points().stream().allMatch(point -> point.z() == 40 && point.anchorZ() == 39));
        ScatterPlanner.Result wool = ScatterPlanner.plan(snapshot, settings(100, 0, 1),
                (x, y, z) -> snapshot.blockId(x, y, z).equals("minecraft:red_wool"));
        assertEquals(64, wool.points().size());
    }

    // ------------------------------------------------------------------ Array

    private static VoxelStructure cube(int size) {
        VoxelStructure.Builder builder = VoxelStructure.builder(size, size, size, 0, 0, 0);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    builder.set(x, y, z, 0);
                }
            }
        }
        return builder.build(false, 1.0, 0.0, "cubo");
    }

    private static List<Placement> array(String flags, Structure structure, List<Vec3> points) {
        PlacementSettings settings = PlacementSettings.parse(PlacementSettings.Kind.ARRAY, "Cl " + flags);
        PlacementPath path = PlacementPath.create(points, false, settings.array().tension(), settings.array().bias(),
                settings.array().continuity(), settings.array().normalMode());
        PlacementBuilder builder = new PlacementBuilder(settings, List.of(structure), PlacementBuilder.Offsets.NONE);
        return ArrayPlanner.plan(path, builder, new Vec3(0, 0, 0), DirectionExpr.Context.NONE, null);
    }

    @Test
    void gapControlsTheSpacingAlongTheLine() {
        List<Vec3> line = List.of(new Vec3(0.5, 0.5, 0.5), new Vec3(40.5, 0.5, 0.5));
        List<Placement> touching = array("", cube(2), line);
        assertEquals(21, touching.size());
        assertEquals(2.0, touching.get(1).anchor().x() - touching.get(0).anchor().x(), 1.0E-9);
        List<Placement> spaced = array("-g 3", cube(2), line);
        assertEquals(5.0, spaced.get(1).anchor().x() - spaced.get(0).anchor().x(), 1.0E-9);
        List<Placement> overlapping = array("-g -1", cube(2), line);
        assertEquals(1.0, overlapping.get(1).anchor().x() - overlapping.get(0).anchor().x(), 1.0E-9);
    }

    @Test
    void progressiveScalingGrowsAlongThePath() {
        List<Vec3> line = List.of(new Vec3(0.5, 0.5, 0.5), new Vec3(60.5, 0.5, 0.5));
        List<Placement> placements = array("-q 0.5,3", cube(2), line);
        assertEquals(0.5, placements.getFirst().scale().x(), 1.0E-9);
        assertTrue(placements.getLast().scale().x() > 2.5);
        for (int index = 1; index < placements.size(); index++) {
            assertTrue(placements.get(index).scale().x() >= placements.get(index - 1).scale().x() - 1.0E-9);
        }
    }

    @Test
    void maxOffsetLimitsHowFastPlacementsClimb() {
        List<Vec3> ramp = List.of(new Vec3(0.5, 0.5, 0.5), new Vec3(20.5, 20.5, 0.5));
        List<Placement> placements = array("-y 1 -g 1", cube(1), ramp);
        for (int index = 1; index < placements.size(); index++) {
            assertTrue(placements.get(index).anchor().y() - placements.get(index - 1).anchor().y() <= 1.0 + 1.0E-9);
        }
    }

    @Test
    void tangentialAlignsUpAlongThePath() {
        List<Vec3> line = List.of(new Vec3(0.5, 0.5, 0.5), new Vec3(0.5, 0.5, -20.5));
        List<Placement> placements = array("Tangential Constant", cube(1), line);
        Vec3 up = placements.get(3).rotation().column(1);
        assertEquals(-1.0, up.z(), 1.0E-6, "el +y de la estructura apunta al norte, como la trayectoria");
    }

    @Test
    void pathModesGiveDifferentNormals() {
        List<Vec3> climb = List.of(new Vec3(0, 0, 0), new Vec3(10, 10, 0), new Vec3(20, 10, 10));
        PlacementPath consistent = PlacementPath.create(climb, false, 0, 0, 0, TubeSettings.NormalMode.CONSISTENT);
        PlacementPath upright = PlacementPath.create(climb, false, 0, 0, 0, TubeSettings.NormalMode.UPRIGHT);
        PlacementPath horizontal = PlacementPath.create(climb, false, 0, 0, 0, TubeSettings.NormalMode.HORIZONTAL);
        double middle = consistent.length() * 0.5;
        assertEquals(0.0, consistent.at(middle).normal().dot(consistent.at(middle).tangent()), 1.0E-6);
        assertEquals(new Vec3(0, 1, 0), upright.at(middle).normal());
        PlacementPath.Frame frame = horizontal.at(middle);
        Vec3 side = frame.normal().cross(frame.tangent());
        assertEquals(0.0, side.y(), 1.0E-3, "con horizontal, el lado no se inclina");
        assertThrows(IllegalArgumentException.class, () -> PlacementPath.create(List.of(new Vec3(0, 0, 0)), false,
                0, 0, 0, TubeSettings.NormalMode.CONSISTENT));
    }

    @Test
    void snapperFindsTheNearestSurface() {
        RegionSnapshot snapshot = terrain();
        ArrayPlanner.SurfaceSnapper snapper = ArrayPlanner.snapper(snapshot, 16);
        ArrayPlanner.Snapped snapped = snapper.snap(new Vec3(20.5, 10.5, 20.5));
        assertEquals(new Vec3(20.5, 1.5, 20.5), snapped.anchor());
        assertEquals(1.0, snapped.normal().y(), 1.0E-9);
        ArrayPlanner.Snapped wall = snapper.snap(new Vec3(20.5, 4.5, 37.5));
        assertEquals(39.5, wall.anchor().z(), 1.0E-9, "la superficie más cercana es la cara del muro");
    }

    @Test
    void scatterContextSeedsAreIndependent() {
        SplittableRandom first = new SplittableRandom(5L * 0x9E3779B97F4A7C15L);
        SplittableRandom second = new SplittableRandom(5L * 0x9E3779B97F4A7C15L + 1);
        assertNotEquals(first.nextLong(), second.nextLong());
    }
}
