package com.komainu.moxia.shape.tube;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.Vec3;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TubeLimbTest {
    private static Long2IntOpenHashMap limb(List<Vec3> points, double... radii) {
        Long2IntOpenHashMap axes = new Long2IntOpenHashMap();
        axes.defaultReturnValue(-1);
        TubeLimb.voxelize(points, radii, 1_000_000, (x, y, z, axis, progress) -> {
            assertEquals(-1, axes.put(PackedBlockPlan.pack(x, y, z), axis), "cada bloque una sola vez");
            assertTrue(progress >= 0.0 && progress <= 1.0);
        });
        return axes;
    }

    @Test
    void logsFollowTheBranch() {
        Long2IntOpenHashMap vertical = limb(List.of(new Vec3(0.5, 0, 0.5), new Vec3(0.5, 12, 0.5)), 2.0, 1.0);
        vertical.values().forEach(axis -> assertEquals(1, axis));
        Long2IntOpenHashMap east = limb(List.of(new Vec3(0, 5.5, 0.5), new Vec3(12, 5.5, 0.5)), 1.5, 0.5);
        east.values().forEach(axis -> assertEquals(0, axis));
        Long2IntOpenHashMap south = limb(List.of(new Vec3(0.5, 5.5, 0), new Vec3(0.5, 5.5, 12)), 1.5, 0.5);
        south.values().forEach(axis -> assertEquals(2, axis));
        // Una rama tumbada que gira de este a sur: troncos en x y luego en z, ninguno de pie.
        Long2IntOpenHashMap diagonal = limb(List.of(new Vec3(0, 5.5, 0.5), new Vec3(8, 5.5, 1.5),
                new Vec3(10, 5.5, 10)), 1.2, 0.8, 0.5);
        int alongX = 0;
        int alongZ = 0;
        for (int axis : diagonal.values()) {
            assertTrue(axis != 1);
            alongX += axis == 0 ? 1 : 0;
            alongZ += axis == 2 ? 1 : 0;
        }
        assertTrue(alongX > 0 && alongZ > 0);
    }

    @Test
    void radiusTapersAlongTheBranch() {
        Long2IntOpenHashMap cone = limb(List.of(new Vec3(0.5, 0, 0.5), new Vec3(0.5, 10, 0.5),
                new Vec3(0.5, 20, 0.5)), 4.0, 2.0, 0.5);
        int bottom = 0;
        int top = 0;
        for (long key : cone.keySet()) {
            int y = PackedBlockPlan.unpackY(key);
            bottom += y == 2 ? 1 : 0;
            top += y == 18 ? 1 : 0;
        }
        assertTrue(bottom > top * 4, bottom + " / " + top);
    }

    @Test
    void thinDiagonalBranchesStayJoinedFaceToFace() {
        for (Vec3 end : List.of(new Vec3(9.3, 9.1, 8.7), new Vec3(-7.2, 3.3, 9.9), new Vec3(4.4, -6.1, -8.2))) {
            Long2IntOpenHashMap cells = limb(List.of(new Vec3(0.5, 0.5, 0.5), new Vec3(3.1, 4.0, 2.2), end),
                    0.5, 0.5, 0.5);
            LongOpenHashSet all = new LongOpenHashSet(cells.keySet());
            long start = all.iterator().nextLong();
            LongOpenHashSet reached = new LongOpenHashSet();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            reached.add(start);
            queue.enqueue(start);
            int[] dx = {1, -1, 0, 0, 0, 0};
            int[] dy = {0, 0, 1, -1, 0, 0};
            int[] dz = {0, 0, 0, 0, 1, -1};
            while (!queue.isEmpty()) {
                long key = queue.dequeueLong();
                for (int face = 0; face < 6; face++) {
                    long next = PackedBlockPlan.pack(PackedBlockPlan.unpackX(key) + dx[face],
                            PackedBlockPlan.unpackY(key) + dy[face], PackedBlockPlan.unpackZ(key) + dz[face]);
                    if (all.contains(next) && reached.add(next)) {
                        queue.enqueue(next);
                    }
                }
            }
            assertEquals(all.size(), reached.size(), "rama hacia " + end);
        }
    }
}
