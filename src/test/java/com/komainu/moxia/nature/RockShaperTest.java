package com.komainu.moxia.nature;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RockShaperTest {
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    private static NatureVoxels shape(String options, FakeTerrain terrain, long seed) {
        RockSettings settings = RockSettings.parse(options).withSeed(seed);
        int baseY = terrain.height(0, 0) + 1;
        int tall = (int) Math.ceil(RockShaper.height(settings));
        Bounds bounds = Bounds.around(0, baseY, 0, RockShaper.reach(settings), tall + 20, tall + 8);
        return RockShaper.shape(settings, terrain, bounds, 0, baseY, 0, seed, 2_000_000, () -> false);
    }

    @Test
    void rocksAreOnePieceSunkIntoTheGround() {
        for (String options : new String[]{"boulder", "crag", "spire", "boulder 8 -e 3 -i 25", "spire 3 -a 5",
                "crag 12 -h 0.5 -u 0.7"}) {
            for (FakeTerrain terrain : new FakeTerrain[]{FakeTerrain.flat(63), FakeTerrain.slope(64, 0.5),
                    FakeTerrain.hill(64, 12.0)}) {
                for (long seed : new long[]{1L, 9L}) {
                    NatureVoxels rock = shape(options, terrain, seed);
                    LongOpenHashSet cells = new LongOpenHashSet(rock.sortedKeys());
                    assertTrue(cells.size() > 20, options);
                    long start = cells.iterator().nextLong();
                    LongOpenHashSet reached = new LongOpenHashSet();
                    LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
                    reached.add(start);
                    queue.enqueue(start);
                    while (!queue.isEmpty()) {
                        long key = queue.dequeueLong();
                        for (int face = 0; face < 6; face++) {
                            long next = PackedBlockPlan.pack(PackedBlockPlan.unpackX(key) + DX[face],
                                    PackedBlockPlan.unpackY(key) + DY[face], PackedBlockPlan.unpackZ(key) + DZ[face]);
                            if (cells.contains(next) && reached.add(next)) {
                                queue.enqueue(next);
                            }
                        }
                    }
                    assertEquals(cells.size(), reached.size(), options + ": trozos sueltos");
                    int buried = 0;
                    for (long key : cells) {
                        int x = PackedBlockPlan.unpackX(key);
                        int y = PackedBlockPlan.unpackY(key);
                        int z = PackedBlockPlan.unpackZ(key);
                        NatureTerrain.Kind kind = terrain.kind(x, y, z);
                        assertTrue(kind != NatureTerrain.Kind.HARD && kind != NatureTerrain.Kind.LEAVES, options);
                        if (y <= terrain.height(x, z)) {
                            buried++;
                        }
                    }
                    assertTrue(buried > 0, options + ": no está hundida");
                }
            }
        }
    }

    @Test
    void shadingIsDarkerBelowAndMossOnlyOnTop() {
        FakeTerrain terrain = FakeTerrain.flat(63);
        NatureVoxels rock = shape("crag 9 -u 0.8", terrain, 4L);
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (long key : rock.sortedKeys()) {
            minimum = Math.min(minimum, PackedBlockPlan.unpackY(key));
            maximum = Math.max(maximum, PackedBlockPlan.unpackY(key));
        }
        double low = 0;
        int lowCount = 0;
        double high = 0;
        int highCount = 0;
        int moss = 0;
        for (long key : rock.sortedKeys()) {
            int info = rock.info(key);
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            if (NatureVoxels.role(info) == NatureVoxels.Role.MOSS) {
                moss++;
                assertFalse(rock.contains(x, y + 1, z), "musgo tapado");
                continue;
            }
            if (y <= 63 || !exposed(rock, x, y, z)) {
                continue;
            }
            if (y <= 64 + 1) {
                low += NatureVoxels.shade(info);
                lowCount++;
            } else if (y >= maximum - 2) {
                high += NatureVoxels.shade(info);
                highCount++;
            }
        }
        assertTrue(moss > 5, "musgo: " + moss);
        assertTrue(lowCount > 0 && highCount > 0);
        assertTrue(low / lowCount + 20 < high / highCount, low / lowCount + " / " + high / highCount);
        assertEquals(0, shape("crag 9", terrain, 4L).count(NatureVoxels.Role.MOSS));
    }

    private static boolean exposed(NatureVoxels rock, int x, int y, int z) {
        for (int face = 0; face < 6; face++) {
            if (!rock.contains(x + DX[face], y + DY[face], z + DZ[face])) {
                return true;
            }
        }
        return false;
    }

    /**
     * En una ladera la base sigue al terreno: no deja más columnas al aire que la misma roca en llano (donde solo
     * vuela el bombeo natural de sus costados).
     */
    @Test
    void onASlopeTheBaseFollowsTheGround() {
        for (String options : new String[]{"crag 9", "boulder 6", "crag 10 -e 2"}) {
            double flat = airborne(options, FakeTerrain.flat(63));
            double slope = airborne(options, FakeTerrain.slope(64, 0.6));
            assertTrue(slope <= flat + 0.08, options + ": " + slope + " en ladera frente a " + flat + " en llano");
        }
    }

    /** Parte de las columnas de la roca cuyo bloque más bajo queda a más de dos bloques del suelo. */
    private static double airborne(String options, FakeTerrain terrain) {
        NatureVoxels rock = shape(options, terrain, 3L);
        java.util.Map<Long, Integer> lowest = new java.util.HashMap<>();
        for (long key : rock.sortedKeys()) {
            long column = PackedBlockPlan.pack(PackedBlockPlan.unpackX(key), 0, PackedBlockPlan.unpackZ(key));
            lowest.merge(column, PackedBlockPlan.unpackY(key), Math::min);
        }
        int gaps = 0;
        for (java.util.Map.Entry<Long, Integer> entry : lowest.entrySet()) {
            int x = PackedBlockPlan.unpackX(entry.getKey());
            int z = PackedBlockPlan.unpackZ(entry.getKey());
            if (entry.getValue() - terrain.height(x, z) > 2) {
                gaps++;
            }
        }
        return gaps / (double) lowest.size();
    }

    @Test
    void sameSeedSameRock() {
        NatureVoxels first = shape("crag 8 -u 0.5", FakeTerrain.slope(64, 0.3), 77L);
        NatureVoxels second = shape("crag 8 -u 0.5", FakeTerrain.slope(64, 0.3), 77L);
        NatureVoxels other = shape("crag 8 -u 0.5", FakeTerrain.slope(64, 0.3), 78L);
        assertArrayEquals(first.sortedKeys(), second.sortedKeys());
        for (long key : first.sortedKeys()) {
            assertEquals(first.info(key), second.info(key));
        }
        assertFalse(Arrays.equals(first.sortedKeys(), other.sortedKeys()));
    }

    @Test
    void spiresAreTallAndBouldersAreNot() {
        NatureVoxels spire = shape("spire 3", FakeTerrain.flat(63), 2L);
        NatureVoxels boulder = shape("boulder 4", FakeTerrain.flat(63), 2L);
        assertTrue(top(spire) - 63 >= 12, "aguja de " + (top(spire) - 63));
        assertTrue(top(boulder) - 63 <= 8, "roca de " + (top(boulder) - 63));
    }

    private static int top(NatureVoxels voxels) {
        int top = Integer.MIN_VALUE;
        for (long key : voxels.sortedKeys()) {
            top = Math.max(top, PackedBlockPlan.unpackY(key));
        }
        return top;
    }
}
