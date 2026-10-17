package com.komainu.moxia.deform;

import com.komainu.moxia.noise.NoiseSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeformationsTest {
    @Test
    void hexagonalizeTurnsASphereIntoFlatToppedColumns() {
        Volume volume = Volume.of(Shapes.snapshot(24, 24, 24, 0, Shapes.sphere(12, 12, 12, 10)));
        Shapes.Result result = Shapes.apply(volume, Deformations.hexagonalize(volume, 6, 0, 0, 0, 60));
        // Cada columna vertical es un tramo continuo y todas las de una misma celda tienen la misma altura.
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                int transitions = 0;
                for (int y = 1; y < 24; y++) {
                    if ((result.at(x, y, z) != 0) != (result.at(x, y - 1, z) != 0)) {
                        transitions++;
                    }
                }
                assertTrue(transitions <= 2, "una columna es un solo tramo");
            }
        }
        // Dos bloques a menos de medio paso del mismo centro de celda comparten columna.
        assertEquals(column(result, 12, 12), column(result, 13, 12));
        int before = new Shapes.Result(volume).occupied();
        assertTrue(Math.abs(result.occupied() - before) < before * 0.25, "las columnas conservan el volumen a grandes rasgos");
    }

    @Test
    void hexagonalizeGapCutsChannelsAndRotationTiltsColumns() {
        Volume slab = Volume.of(Shapes.snapshot(30, 4, 30, 0, (x, y, z) -> Shapes.STONE));
        Shapes.Result solid = Shapes.apply(slab, Deformations.hexagonalize(slab, 8, 0, 0, 0, 60));
        assertEquals(0, solid.stats.total(), "sin hueco, una losa maciza no cambia");
        Shapes.Result gaps = Shapes.apply(slab, Deformations.hexagonalize(slab, 8, 2, 0, 0, 60));
        int air = gaps.count(Shapes.AIR);
        assertTrue(air > 30 * 4 * 30 / 8 && air < 30 * 4 * 30 / 2, "el hueco abre canales entre columnas: " + air);
        // Columnas tumbadas sobre X: ahora van a lo largo de Z y la losa sin hueco sigue igual en su interior.
        Shapes.Result tilted = Shapes.apply(slab, Deformations.hexagonalize(slab, 8, 0, 90, 0, 60));
        assertTrue(tilted.occupied() > 0);
    }

    @Test
    void noiseWithoutStrengthChangesNothingAndIsDeterministic() {
        Volume volume = Volume.of(Shapes.snapshot(16, 16, 16, 0, Shapes.sphere(8, 8, 8, 6)));
        NoiseSpec spec = NoiseSpec.parse("Perlin(F:0.1)");
        assertEquals(0, Shapes.apply(volume, Deformations.noise(volume, spec, 0, 1, 5, false, false)).stats.total());
        Shapes.Result first = Shapes.apply(volume, Deformations.noise(volume, spec, 3, 1, 5, false, false));
        Shapes.Result second = Shapes.apply(volume, Deformations.noise(volume, spec, 3, 1, 5, false, false));
        assertTrue(first.stats.total() > 0);
        assertArrayEquals(first.ids, second.ids);
        Shapes.Result horizontal = Shapes.apply(volume, Deformations.noise(volume, spec, 3, 1, 5, true, false));
        // Solo en horizontal: la altura máxima de la esfera no cambia.
        assertEquals(top(new Shapes.Result(volume)), top(horizontal));
    }

    @Test
    void rotateTurnsClockwiseAroundTheAimAndSpillsOutOfTheSelection() {
        // Bloque al este del centro de un cubo de 9 (centro 4.5).
        Volume volume = Volume.of(Shapes.snapshot(9, 9, 9, 4, (x, y, z) -> x == 6 && y == 4 && z == 4
                ? Shapes.STONE : Shapes.AIR));
        double[] center = {4.5, 4.5, 4.5};
        // Mirando hacia abajo (-y), un giro horario lleva el este al sur.
        Shapes.Result down = Shapes.apply(volume, Deformations.rotate(volume, new double[]{0, -1, 0}, 90, center),
                Deformations.rotatedBounds(volume.selectionBounds(), new double[]{0, -1, 0}, 90, center),
                SmoothProfile.NONE);
        assertEquals(Shapes.STONE, down.at(4, 4, 6));
        assertEquals(Shapes.AIR, down.at(6, 4, 4));
        Shapes.Result full = Shapes.apply(volume, Deformations.rotate(volume, new double[]{0, 1, 0}, 360, center));
        assertEquals(0, full.stats.total(), "una vuelta entera deja todo igual");

        int[] box = Deformations.rotatedBounds(new int[]{0, 0, 0, 9, 0, 0}, new double[]{0, 0, 1}, 45,
                new double[]{5, 0.5, 0.5});
        assertTrue(box[4] >= 3, "una barra girada 45° sale de su selección: " + box[4]);
    }

    @Test
    void voronoializeKeepsWholeCellsAndSplitsThemWithGaps() {
        Volume volume = Volume.of(Shapes.snapshot(24, 24, 24, 0, Shapes.sphere(12, 12, 12, 10)));
        Shapes.Result cells = Shapes.apply(volume, Deformations.voronoialize(volume, 8, 0, 3));
        Shapes.Result again = Shapes.apply(volume, Deformations.voronoialize(volume, 8, 0, 3));
        assertArrayEquals(cells.ids, again.ids, "la misma semilla da las mismas celdas");
        Shapes.Result other = Shapes.apply(volume, Deformations.voronoialize(volume, 8, 0, 4));
        assertNotEquals(java.util.Arrays.hashCode(cells.ids), java.util.Arrays.hashCode(other.ids));
        Shapes.Result gaps = Shapes.apply(volume, Deformations.voronoialize(volume, 8, 3, 3));
        assertTrue(gaps.occupied() < cells.occupied() * 0.8, "el hueco separa las celdas");
        int before = new Shapes.Result(volume).occupied();
        assertTrue(Math.abs(cells.occupied() - before) < before * 0.35);
    }

    @Test
    void voronoialize2FacetsTheSurfaceButKeepsTheShape() {
        Volume volume = Volume.of(Shapes.snapshot(28, 28, 28, 0, Shapes.sphere(14, 14, 14, 11)));
        Shapes.Result facets = Shapes.apply(volume, Deformations.voronoialize2(volume, 40, 0, 9, 15, 5,
                Shapes.NO_CANCEL));
        int before = new Shapes.Result(volume).occupied();
        assertTrue(facets.stats.total() > 0);
        assertTrue(Math.abs(facets.occupied() - before) < before * 0.2,
                "las facetas siguen la forma: " + facets.occupied() + " frente a " + before);
        Shapes.Result gaps = Shapes.apply(volume, Deformations.voronoialize2(volume, 40, 2, 9, 15, 5,
                Shapes.NO_CANCEL));
        assertTrue(gaps.occupied() < facets.occupied());
        Shapes.Result again = Shapes.apply(volume, Deformations.voronoialize2(volume, 40, 0, 9, 15, 5,
                Shapes.NO_CANCEL));
        assertArrayEquals(facets.ids, again.ids);
    }

    @Test
    void voxelizeFillsCellsByMajority() {
        // Cubo de 4 con un hueco y un bloque suelto lejos: con celdas de 2 el hueco se llena y el suelto se va.
        Volume volume = Volume.of(Shapes.snapshot(8, 8, 8, 0, (x, y, z) -> {
            if (x < 4 && y < 4 && z < 4) {
                return x == 1 && y == 1 && z == 1 ? Shapes.AIR : x == 0 ? Shapes.DIRT : Shapes.STONE;
            }
            return x == 6 && y == 6 && z == 6 ? Shapes.STONE : Shapes.AIR;
        }));
        Shapes.Result result = Shapes.apply(volume, Deformations.voxelize(volume, new double[]{2, 2, 2}, 0, 0,
                Deformations.Axis.Y, Deformations.Axis.NEG_X, 1, false, false, Shapes.NO_CANCEL));
        assertEquals(64, result.occupied());
        assertEquals(Shapes.AIR, result.at(6, 6, 6));
        assertTrue(result.at(1, 1, 1) != Shapes.AIR);
        // Con -h solo se agrupa en horizontal: la altura de cada bloque se conserva.
        Volume stairs = Volume.of(Shapes.snapshot(8, 8, 8, 0, (x, y, z) -> y <= x ? Shapes.STONE : Shapes.AIR));
        Shapes.Result horizontal = Shapes.apply(stairs, Deformations.voxelize(stairs, new double[]{4, 4, 4}, 0, 0,
                Deformations.Axis.Y, Deformations.Axis.X, 1, true, false, Shapes.NO_CANCEL));
        for (int x = 0; x < 8; x += 4) {
            int column = column(horizontal, x, 0);
            for (int dx = 0; dx < 4; dx++) {
                assertEquals(column, column(horizontal, x + dx, 3), "cada celda de 4×4 tiene una sola altura");
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Deformations.voxelize(volume, new double[]{2, 2, 2}, 0, 0,
                Deformations.Axis.Y, Deformations.Axis.NEG_Y, 1, false, false, Shapes.NO_CANCEL));
    }

    @Test
    void voxelizeDistortionMovesTheCutsButKeepsCuboids() {
        Volume volume = Volume.of(Shapes.snapshot(16, 16, 16, 0, Shapes.sphere(8, 8, 8, 7)));
        Shapes.Result plain = Shapes.apply(volume, Deformations.voxelize(volume, new double[]{4, 4, 4}, 0, 0,
                Deformations.Axis.Y, Deformations.Axis.X, 1, false, false, Shapes.NO_CANCEL));
        Shapes.Result distorted = Shapes.apply(volume, Deformations.voxelize(volume, new double[]{4, 4, 4}, 0, 1,
                Deformations.Axis.Y, Deformations.Axis.X, 1, false, false, Shapes.NO_CANCEL));
        assertNotEquals(java.util.Arrays.hashCode(plain.ids), java.util.Arrays.hashCode(distorted.ids));
    }

    private static int column(Shapes.Result result, int x, int z) {
        int bits = 0;
        for (int y = 0; y < result.volume.sizeY; y++) {
            bits = bits * 31 + (result.at(x, y, z) != 0 ? 1 : 0);
        }
        return bits;
    }

    private static int top(Shapes.Result result) {
        int top = -1;
        for (int index = 0; index < result.ids.length; index++) {
            if (result.ids[index] != 0) {
                top = Math.max(top, result.volume.y(index));
            }
        }
        return top;
    }
}
