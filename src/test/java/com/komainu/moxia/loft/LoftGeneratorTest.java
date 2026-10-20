package com.komainu.moxia.loft;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoftGeneratorTest {
    private static List<BlockVector3> line(int x1, int y1, int z1, int x2, int y2, int z2) {
        return List.of(BlockVector3.at(x1, y1, z1), BlockVector3.at(x2, y2, z2));
    }

    private static LoftResult loft(List<List<BlockVector3>> curves, String flags) {
        return LoftGenerator.generate(curves, LoftSettings.parse(flags, name -> false));
    }

    /** Dos bordes rectos de 30 bloques separados 20: una lona. */
    private static List<List<BlockVector3>> tarp(int heightA, int heightB) {
        return List.of(line(0, heightA, 0, 30, heightA, 0), line(0, heightB, 20, 30, heightB, 20));
    }

    @Test
    void everyLoftIsWatertight() {
        List<List<BlockVector3>> arch = List.of(line(0, 64, 0, 30, 64, 0), line(0, 72, 10, 30, 70, 10),
                line(0, 64, 20, 30, 66, 20));
        List<List<BlockVector3>> wavy = List.of(
                List.of(BlockVector3.at(0, 64, 0), BlockVector3.at(10, 68, 1), BlockVector3.at(20, 62, -1),
                        BlockVector3.at(30, 65, 0)),
                List.of(BlockVector3.at(0, 70, 20), BlockVector3.at(15, 75, 21), BlockVector3.at(30, 70, 20)));
        Object[][] cases = {
                {tarp(64, 64), "-i lineal"},
                {tarp(64, 75), "-i lineal"},
                {tarp(64, 64), "-i catenaria -comba 4"},
                {tarp(64, 70), "-comba 30%"},
                {arch, "-i catmull"},
                {arch, "-i lineal -g 2"},
                {wavy, "-i catmull"},
                {wavy, "-i catenaria -g 3"},
                {tarp(64, 64), "-i catenaria -comba 5 -w SlabsAndStairs"},
        };
        for (Object[] entry : cases) {
            @SuppressWarnings("unchecked")
            List<List<BlockVector3>> curves = (List<List<BlockVector3>>) entry[0];
            LoftResult result = loft(curves, (String) entry[1]);
            assertTrue(watertight(result, 2, 28, 2, 18), entry[1] + ": la lona deja pasar");
        }
    }

    @Test
    void catenaryHangsBySag() {
        LoftResult result = loft(tarp(64, 64), "-i catenaria -comba 4");
        int lowest = Integer.MAX_VALUE;
        for (int y = 50; y <= 70; y++) {
            if (result.contains(15, y, 10)) {
                lowest = Math.min(lowest, y);
            }
        }
        // Los bordes van por el centro de los bloques (64,5): cuatro más abajo queda en 60,5.
        assertEquals(60, lowest);
        assertTrue(result.contains(15, 64, 0));
        assertTrue(result.contains(15, 64, 20));
        // Sin comba, plana.
        LoftResult flat = loft(tarp(64, 64), "-i lineal");
        for (long key : flat.positions()) {
            assertEquals(64, PackedBlockPlan.unpackY(key));
        }
        // Catenaria normalizada: cero en los apoyos, uno en el centro, simétrica.
        assertEquals(0.0, LoftGenerator.hang(0.2, 0.0), 1.0E-9);
        assertEquals(1.0, LoftGenerator.hang(0.2, 0.5), 1.0E-9);
        assertEquals(LoftGenerator.hang(0.2, 0.3), LoftGenerator.hang(0.2, 0.7), 1.0E-9);
        assertEquals(4.0 * 0.3 * 0.7, LoftGenerator.hang(1.0E-6, 0.3), 1.0E-6);
    }

    @Test
    void thicknessGrowsTheSurface() {
        LoftResult thin = loft(tarp(64, 64), "-i lineal");
        LoftResult thick = loft(tarp(64, 64), "-i lineal -g 3");
        assertTrue(thick.size() > 2 * thin.size());
        for (int y = 63; y <= 65; y++) {
            assertTrue(thick.contains(15, y, 10), "y=" + y);
        }
    }

    @Test
    void reversedCurvesAreTurnedAround() {
        List<List<BlockVector3>> forward = tarp(64, 68);
        List<BlockVector3> backward = new ArrayList<>(forward.get(1));
        java.util.Collections.reverse(backward);
        LoftResult straight = loft(forward, "-i catmull");
        LoftResult turned = loft(List.of(forward.get(0), backward), "-i catmull");
        assertArrayEquals(straight.positions(), turned.positions());
    }

    @Test
    void smoothBlocksOnlyAddPieces() {
        LoftResult plain = loft(tarp(64, 70), "-i catenaria -comba 3");
        LoftResult smooth = loft(tarp(64, 70), "-i catenaria -comba 3 -w SlabsAndStairs");
        assertTrue(smooth.pieceCount() > 0);
        LongOpenHashSet blocks = new LongOpenHashSet(smooth.positions());
        for (long key : plain.positions()) {
            assertTrue(blocks.contains(key), "-w no quita bloques de la superficie");
        }
    }

    @Test
    void acrossGoesFromTheFirstCurveToTheLast() {
        LoftResult result = loft(tarp(64, 64), "-i lineal");
        assertTrue(result.across(PackedBlockPlan.pack(15, 64, 0)) < 0.05F);
        assertTrue(result.across(PackedBlockPlan.pack(15, 64, 20)) > 0.95F);
        float middle = result.across(PackedBlockPlan.pack(15, 64, 10));
        assertTrue(middle > 0.45F && middle < 0.55F, "centro: " + middle);
    }

    @Test
    void sameInputGivesTheSameLoft() {
        List<List<BlockVector3>> arch = List.of(line(0, 64, 0, 30, 64, 0), line(0, 72, 10, 30, 70, 10),
                line(0, 64, 20, 30, 66, 20));
        for (String flags : List.of("-i catmull", "-i catenaria -g 2", "-i catmull -w SlabsAndStairs")) {
            LoftResult first = loft(arch, flags);
            LoftResult second = loft(arch, flags);
            assertArrayEquals(first.positions(), second.positions(), flags);
            assertArrayEquals(first.pieces(), second.pieces(), flags);
        }
    }

    @Test
    void currentPointsSplitIntoCurves() {
        List<BlockVector3> points = List.of(BlockVector3.at(0, 64, 0), BlockVector3.at(10, 64, 0),
                BlockVector3.at(10, 64, 8), BlockVector3.at(0, 64, 8));
        List<List<BlockVector3>> curves = LoftGenerator.split(points, 2);
        assertEquals(2, curves.size());
        assertEquals(List.of(points.get(0), points.get(1)), curves.get(0));
        assertEquals(List.of(points.get(2), points.get(3)), curves.get(1));
        // Los cuatro puntos marcados alrededor dan una lona sin cruzarse (la segunda curva se gira).
        LoftResult result = LoftGenerator.generate(curves, LoftSettings.parse("-i lineal", name -> false));
        for (int z = 0; z <= 8; z++) {
            for (int x = 0; x <= 10; x++) {
                assertTrue(result.contains(x, 64, z), x + "," + z);
            }
        }
        assertEquals(99, result.size());
        assertThrows(IllegalArgumentException.class, () -> LoftGenerator.split(points.subList(0, 3), 2));
        assertThrows(IllegalArgumentException.class, () -> LoftGenerator.split(points, 3));
    }

    @Test
    void parsesCurvesMaterialAndFlags() {
        Set<String> saved = Set.of("borde1", "borde2", "borde3");
        LoftSettings settings = LoftSettings.parse("borde1 actual Borde3 red_wool -i catenaria -comba 20% -g 2 "
                + "-w SlabsAndStairs -cerrar -e", saved::contains);
        assertEquals(List.of("borde1", LoftSettings.CURRENT, "borde3"), settings.curves());
        assertEquals("red_wool", settings.material());
        assertEquals(LoftSettings.Interpolation.CATENARY, settings.interpolation());
        assertEquals(2.0, settings.sagFor(10.0), 1.0E-9);
        assertEquals(2, settings.thickness());
        assertTrue(settings.smooth().active());
        assertTrue(settings.closed() && settings.estimate());
        LoftSettings defaults = LoftSettings.parse("", name -> false);
        assertEquals(LoftSettings.DEFAULT_MATERIAL, defaults.material());
        assertEquals(LoftSettings.Interpolation.CATMULL_ROM, defaults.interpolation());
        assertEquals(1.5, defaults.sagFor(10.0), 1.0E-9);
        assertEquals(LoftSettings.Interpolation.CATENARY,
                LoftSettings.parse("-comba 3", name -> false).interpolation());
        assertEquals(3, LoftSettings.parse("-n 3", name -> false).curveCount());
        assertThrows(IllegalArgumentException.class, () -> LoftSettings.parse("borde1", saved::contains));
        assertThrows(IllegalArgumentException.class, () -> LoftSettings.parse("stone dirt", name -> false));
        assertThrows(IllegalArgumentException.class, () -> LoftSettings.parse("-i bezier", name -> false));
        assertThrows(IllegalArgumentException.class, () -> LoftSettings.parse("-g 9", name -> false));
    }

    /**
     * Dentro de la caja (entre los bordes, sin rodearlos), ningún camino de caras de bloques vacíos va de la capa
     * de arriba a la de abajo.
     */
    static boolean watertight(LoftResult result, int minX, int maxX, int minZ, int maxZ) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (long key : result.positions()) {
            minY = Math.min(minY, PackedBlockPlan.unpackY(key));
            maxY = Math.max(maxY, PackedBlockPlan.unpackY(key));
        }
        minY -= 2;
        maxY += 2;
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                long key = PackedBlockPlan.pack(x, maxY, z);
                seen.add(key);
                queue.enqueue(key);
            }
        }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            long key = queue.dequeueLong();
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            if (y == minY) {
                return false;
            }
            for (int[] step : steps) {
                int nx = x + step[0];
                int ny = y + step[1];
                int nz = z + step[2];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ || ny < minY || ny > maxY) {
                    continue;
                }
                long next = PackedBlockPlan.pack(nx, ny, nz);
                if (!seen.contains(next) && !result.contains(nx, ny, nz)) {
                    seen.add(next);
                    queue.enqueue(next);
                }
            }
        }
        return true;
    }
}
