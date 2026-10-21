package com.komainu.moxia.nature;

import com.komainu.moxia.FullTests;
import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeGrowerTest {
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    private record Grown(NatureVoxels voxels, FakeTerrain terrain, Bounds bounds, int baseY) {
    }

    private static Grown grow(String options, FakeTerrain terrain, long seed) {
        TreeSettings settings = TreeSettings.parse(options).withSeed(seed);
        int baseY = terrain.height(0, 0) + 1;
        Bounds bounds = Bounds.around(0, baseY, 0, TreeGrower.reach(settings), 14,
                (int) Math.ceil(settings.height() * 1.2) + 10);
        NatureVoxels voxels = TreeGrower.grow(settings, terrain, bounds, 0, baseY, 0, seed, 2_000_000, () -> false);
        return new Grown(voxels, terrain, bounds, baseY);
    }

    private static List<FakeTerrain> terrains() {
        return List.of(FakeTerrain.flat(63), FakeTerrain.hill(64, 20.0), FakeTerrain.slope(64, 0.45));
    }

    @Test
    void nothingFloatsInAnySpeciesOrTerrain() {
        for (TreeSpecies species : TreeSpecies.values()) {
            for (FakeTerrain terrain : terrains()) {
                // En cada build, una semilla; con KOMAEDIT_FULL_TESTS, tres.
                for (long seed : FullTests.pick(List.of(1L, 7L, 42L), List.of(1L))) {
                    Grown grown = grow(species.id(), terrain, seed);
                    String label = species.id() + " semilla " + seed;
                    assertTrue(grown.voxels().count(NatureVoxels.Role.WOOD) > 0, label);
                    assertWoodStandsOnGround(grown, label);
                    assertFoliageReachesWood(grown, label);
                    assertRespectsTerrain(grown, label);
                }
            }
        }
    }

    @Test
    void manySeedsAndSettingsKeepEveryRule() {
        String[] extras = {"", " -g 0.4", " -g 2.5 -c 3", " -i 40 -w 1.8", " -b 2.5 -d 1.8", " -b 0.2 -d 0.3",
                " 6", " 40 -c 0"};
        java.util.SplittableRandom random = new java.util.SplittableRandom(5L);
        for (TreeSpecies species : TreeSpecies.values()) {
            for (int run = 0; run < 8; run++) {
                String options = species.id() + extras[run];
                long seed = random.nextLong(1L, 1_000_000L);
                if (!FullTests.ENABLED && run % 2 == 1) {
                    // En cada build, la mitad de los ajustes (las mismas semillas que con KOMAEDIT_FULL_TESTS).
                    continue;
                }
                FakeTerrain terrain = terrains().get(run % 3);
                Grown grown = grow(options, terrain, seed);
                String label = options + " semilla " + seed;
                assertWoodStandsOnGround(grown, label);
                assertFoliageReachesWood(grown, label);
                assertRespectsTerrain(grown, label);
            }
        }
    }

    /** Toda la madera y las raíces llegan cara con cara a un bloque que toca el suelo. */
    private static void assertWoodStandsOnGround(Grown grown, String label) {
        NatureVoxels voxels = grown.voxels();
        LongOpenHashSet wood = cells(voxels, true);
        LongOpenHashSet reached = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (long key : wood) {
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            for (int face = 0; face < 6; face++) {
                if (face == 2) {
                    continue;
                }
                int nx = x + DX[face];
                int ny = y + DY[face];
                int nz = z + DZ[face];
                if (!voxels.contains(nx, ny, nz) && grown.terrain().kind(nx, ny, nz).ground()) {
                    reached.add(key);
                    queue.enqueue(key);
                    break;
                }
            }
        }
        assertFalse(reached.isEmpty(), label + ": la madera no toca el suelo");
        flood(queue, reached, wood);
        assertEquals(wood.size(), reached.size(), label + ": hay madera suelta");
    }

    /** Cada hoja llega a la madera por hojas: las de copa en 6 pasos o menos, las colgantes en 12. */
    private static void assertFoliageReachesWood(Grown grown, String label) {
        NatureVoxels voxels = grown.voxels();
        LongOpenHashSet foliage = cells(voxels, false);
        Long2IntOpenHashMap distance = new Long2IntOpenHashMap();
        distance.defaultReturnValue(-1);
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (long key : cells(voxels, true)) {
            distance.put(key, 0);
            queue.enqueue(key);
        }
        while (!queue.isEmpty()) {
            long key = queue.dequeueLong();
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            for (int face = 0; face < 6; face++) {
                long next = PackedBlockPlan.pack(x + DX[face], y + DY[face], z + DZ[face]);
                if (foliage.contains(next) && !distance.containsKey(next)) {
                    distance.put(next, distance.get(key) + 1);
                    queue.enqueue(next);
                }
            }
        }
        for (long key : foliage) {
            int info = voxels.info(key);
            int real = distance.get(key);
            assertTrue(real > 0, label + ": hoja suelta");
            assertTrue(real <= NatureVoxels.distance(info), label + ": distancia guardada menor que la real");
            int limit = NatureVoxels.role(info) == NatureVoxels.Role.LEAF ? NatureVoxels.LEAF_REACH
                    : NatureVoxels.HANGING_REACH;
            assertTrue(real <= limit, label + ": hoja a " + real + " pasos de la madera");
        }
    }

    private static void assertRespectsTerrain(Grown grown, String label) {
        NatureVoxels voxels = grown.voxels();
        for (long key : voxels.sortedKeys()) {
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            assertTrue(grown.bounds().contains(x, y, z), label + ": fuera de la foto");
            NatureTerrain.Kind kind = grown.terrain().kind(x, y, z);
            NatureVoxels.Role role = NatureVoxels.role(voxels.info(key));
            if (role.foliage()) {
                assertEquals(NatureTerrain.Kind.FREE, kind, label + ": hoja sobre un bloque");
            } else {
                assertTrue(kind == NatureTerrain.Kind.FREE || kind == NatureTerrain.Kind.SOIL
                        && NatureVoxels.ground(voxels.info(key)), label + ": madera dentro de " + kind);
            }
        }
    }

    @Test
    void leavesFarFromTheTrunkAreTrimmedAndDistancesAreExact() {
        for (String species : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "baobab"}) {
            Grown grown = grow(species, FakeTerrain.flat(63), 3L);
            NatureVoxels voxels = grown.voxels();
            assertTrue(voxels.count(NatureVoxels.Role.LEAF) > 40, species);
            for (long key : voxels.sortedKeys()) {
                int info = voxels.info(key);
                if (NatureVoxels.role(info) == NatureVoxels.Role.LEAF) {
                    int distance = NatureVoxels.distance(info);
                    assertTrue(distance >= 1 && distance <= NatureVoxels.LEAF_REACH, species);
                }
            }
        }
    }

    @Test
    void leavesArePersistentAndLogsCarryTheirAxis() {
        for (TreeSpecies species : TreeSpecies.values()) {
            NatureVoxels voxels = grow(species.id(), FakeTerrain.flat(63), 11L).voxels();
            for (long key : voxels.sortedKeys()) {
                int info = voxels.info(key);
                Map<String, Object> properties = NatureStates.properties(info);
                NatureVoxels.Role role = NatureVoxels.role(info);
                if (role.foliage()) {
                    assertEquals(Boolean.TRUE, properties.get("persistent"), species.id());
                    assertEquals(Boolean.FALSE, properties.get("waterlogged"), species.id());
                    int distance = (Integer) properties.get("distance");
                    assertTrue(distance >= 1 && distance <= 7, species.id());
                } else if (role.wood()) {
                    assertEquals(List.of("x", "y", "z").get(NatureVoxels.axis(info)), properties.get("axis"));
                }
            }
        }
        assertEquals(Boolean.TRUE, NatureStates.leaf(0).get("persistent"));
        assertEquals(1, NatureStates.leaf(0).get("distance"));
        assertEquals(7, NatureStates.leaf(12).get("distance"));
    }

    @Test
    void trunksStandUprightAndSideBranchesLieDown() {
        Grown spruce = grow("spruce 24 -i 0", FakeTerrain.flat(63), 5L);
        int upright = 0;
        int lying = 0;
        for (long key : spruce.voxels().sortedKeys()) {
            int info = spruce.voxels().info(key);
            if (NatureVoxels.role(info) != NatureVoxels.Role.WOOD) {
                continue;
            }
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            boolean trunk = x * x + z * z <= 1 && y >= spruce.baseY() && y < spruce.baseY() + 3;
            if (trunk) {
                assertEquals(1, NatureVoxels.axis(info), "el tronco va en vertical");
            }
            if (x * x + z * z >= 9) {
                if (NatureVoxels.axis(info) == 1) {
                    upright++;
                } else {
                    lying++;
                }
            }
        }
        assertTrue(lying > upright * 4, "las ramas del abeto van tumbadas: " + lying + " / " + upright);
    }

    @Test
    void sameSeedSameTreeAndAnotherSeedAnotherTree() {
        for (TreeSpecies species : TreeSpecies.values()) {
            NatureVoxels first = grow(species.id(), FakeTerrain.hill(64, 20.0), 99L).voxels();
            NatureVoxels second = grow(species.id(), FakeTerrain.hill(64, 20.0), 99L).voxels();
            NatureVoxels other = grow(species.id(), FakeTerrain.hill(64, 20.0), 100L).voxels();
            assertArrayEquals(first.sortedKeys(), second.sortedKeys(), species.id());
            for (long key : first.sortedKeys()) {
                assertEquals(first.info(key), second.info(key), species.id());
            }
            assertFalse(Arrays.equals(first.sortedKeys(), other.sortedKeys()), species.id());
        }
    }

    @Test
    void speciesHaveTheirOwnFeatures() {
        assertEquals(0, grow("dead", FakeTerrain.flat(63), 2L).voxels().count(NatureVoxels.Role.LEAF));
        assertTrue(grow("willow", FakeTerrain.flat(63), 2L).voxels().count(NatureVoxels.Role.HANGING) > 40);
        assertTrue(grow("palm", FakeTerrain.flat(63), 2L).voxels().count(NatureVoxels.Role.HANGING) > 60);
        assertEquals(0, grow("oak -r no", FakeTerrain.flat(63), 2L).voxels().count(NatureVoxels.Role.ROOT));
        NatureVoxels bare = grow("oak -h no", FakeTerrain.flat(63), 2L).voxels();
        assertEquals(0, bare.count(NatureVoxels.Role.LEAF) + bare.count(NatureVoxels.Role.HANGING));
        assertTrue(grow("oak", FakeTerrain.flat(63), 2L).voxels().count(NatureVoxels.Role.ROOT) > 10);
    }

    @Test
    void parametersChangeTheTree() {
        int thin = grow("oak 18 -g 0.6", FakeTerrain.flat(63), 4L).voxels().count(NatureVoxels.Role.WOOD);
        int thick = grow("oak 18 -g 1.6", FakeTerrain.flat(63), 4L).voxels().count(NatureVoxels.Role.WOOD);
        assertTrue(thick > thin * 1.5, thin + " / " + thick);
        int sparse = grow("oak 18 -d 0.4", FakeTerrain.flat(63), 4L).voxels().count(NatureVoxels.Role.LEAF);
        int dense = grow("oak 18 -d 1.6", FakeTerrain.flat(63), 4L).voxels().count(NatureVoxels.Role.LEAF);
        assertTrue(dense > sparse, sparse + " / " + dense);
        Grown tall = grow("birch 30", FakeTerrain.flat(63), 4L);
        int top = Integer.MIN_VALUE;
        for (long key : tall.voxels().sortedKeys()) {
            top = Math.max(top, PackedBlockPlan.unpackY(key));
        }
        assertTrue(top - tall.baseY() >= 26, "altura " + (top - tall.baseY()));
        Grown leaning = grow("oak 18 -i 30,0 -c 0", FakeTerrain.flat(63), 4L);
        double sum = 0;
        int count = 0;
        for (long key : leaning.voxels().sortedKeys()) {
            if (NatureVoxels.role(leaning.voxels().info(key)) == NatureVoxels.Role.WOOD
                    && PackedBlockPlan.unpackY(key) > leaning.baseY() + 6) {
                sum += PackedBlockPlan.unpackX(key);
                count++;
            }
        }
        assertTrue(sum / count > 2.0, "se inclina hacia el este: " + sum / count);
    }

    @Test
    void stoneInTheWayCutsTheTreeWithoutLeavingLooseWood() {
        FakeTerrain terrain = FakeTerrain.flat(63);
        // Un saliente de piedra atraviesa la copa.
        for (int x = -12; x <= 12; x++) {
            for (int z = 2; z <= 4; z++) {
                terrain.with(x, 74, z, NatureTerrain.Kind.HARD);
            }
        }
        Grown grown = new Grown(TreeGrower.grow(TreeSettings.parse("oak").withSeed(8L), terrain,
                Bounds.around(0, 64, 0, 30, 14, 40), 0, 64, 0, 8L, 2_000_000, () -> false), terrain,
                Bounds.around(0, 64, 0, 30, 14, 40), 64);
        assertWoodStandsOnGround(grown, "con saliente");
        assertFoliageReachesWood(grown, "con saliente");
        assertRespectsTerrain(grown, "con saliente");
        assertNotEquals(0, grown.voxels().size());
    }

    private static LongOpenHashSet cells(NatureVoxels voxels, boolean wood) {
        LongOpenHashSet cells = new LongOpenHashSet();
        for (long key : voxels.sortedKeys()) {
            NatureVoxels.Role role = NatureVoxels.role(voxels.info(key));
            if (wood ? role.wood() : role.foliage()) {
                cells.add(key);
            }
        }
        return cells;
    }

    private static void flood(LongArrayFIFOQueue queue, LongOpenHashSet reached, LongOpenHashSet allowed) {
        while (!queue.isEmpty()) {
            long key = queue.dequeueLong();
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            for (int face = 0; face < 6; face++) {
                long next = PackedBlockPlan.pack(x + DX[face], y + DY[face], z + DZ[face]);
                if (allowed.contains(next) && reached.add(next)) {
                    queue.enqueue(next);
                }
            }
        }
    }
}
