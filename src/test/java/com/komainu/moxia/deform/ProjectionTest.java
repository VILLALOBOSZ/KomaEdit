package com.komainu.moxia.deform;

import com.komainu.moxia.region.RegionSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionTest {
    private static final int WORLD_TOP = 320;

    @Test
    void cylinderWrapsTheMapAroundAVerticalAxisAboveTheSelection() {
        int[] selection = {0, 0, 0, 31, 1, 15};
        Projection projection = new Projection(settings(false, false, null, Double.NaN, false, false), selection,
                WORLD_TOP);
        assertEquals(32.0 / (2.0 * Math.PI), projection.radii()[0], 1.0E-9, "la vuelta mide lo que el mapa");
        assertEquals(8.0, projection.halfLength(), 1.0E-9);
        assertEquals(2.0, projection.depth(), 1.0E-9);
        int[] box = projection.box();
        assertTrue(box[1] > selection[4], "la figura se construye encima de la selección");

        double[] center = projection.center();
        double radius = projection.radii()[0];
        // Un punto en la cara exterior, en dirección -j (+x): mitad del mapa en EW y arriba del todo en UD.
        double[] onSurface = projection.mapCoordinates(center[0] + radius - 0.1, center[1], center[2]);
        assertNotNull(onSurface);
        assertEquals(0.5, onSurface[0], 1.0E-6);
        assertTrue(onSurface[1] > 0.9);
        assertEquals(0.5, onSurface[2], 1.0E-6);
        assertNull(projection.mapCoordinates(center[0], center[1], center[2]), "sin -f el centro queda hueco");
        assertNull(projection.mapCoordinates(center[0], center[1] + 9, center[2]), "fuera del largo no hay nada");

        Projection filled = new Projection(settings(false, false, null, Double.NaN, true, false), selection, WORLD_TOP);
        double[] core = filled.mapCoordinates(center[0], center[1], center[2]);
        assertNotNull(core);
        assertEquals(0.0, core[1], 1.0E-9, "con -f el interior repite la capa más honda");
    }

    @Test
    void cylinderMaterialComesFromTheMapAndAirSurroundsIt() {
        // Mapa de 32×2×16: abajo tierra, arriba hierba.
        RegionSnapshot snapshot = cylinderCapture();
        Volume volume = Volume.of(snapshot);
        int[] selection = {0, 0, 0, 31, 1, 15};
        Projection projection = new Projection(settings(false, false, null, Double.NaN, false, false), selection,
                WORLD_TOP);
        Shapes.Result result = Shapes.apply(volume, projection.field(volume), projection.box(), SmoothProfile.NONE);
        assertTrue(result.count(Shapes.GRASS) > 0 && result.count(Shapes.DIRT) > 0);
        double[] center = projection.center();
        int outerX = (int) Math.floor(center[0] + projection.radii()[0] - 0.3);
        int innerX = (int) Math.floor(center[0] + projection.radii()[0] - 1.1);
        int y = (int) Math.floor(center[1]);
        int z = (int) Math.floor(center[2]);
        assertEquals(Shapes.GRASS, result.at(outerX, y, z), "la cara de arriba del mapa queda fuera");
        assertEquals(Shapes.DIRT, result.at(innerX, y, z));
        assertEquals(Shapes.AIR, result.at((int) Math.floor(center[0]), y, z));
        // La selección original no se toca.
        assertEquals(Shapes.GRASS, result.at(5, 1, 5));
    }

    @Test
    void sphereProjectionsMapPolesAndEquator() {
        int[] selection = {0, 0, 0, 63, 2, 31};
        Projection mercator = new Projection(settings(true, false, null, Double.NaN, false, false), selection,
                WORLD_TOP);
        double[] center = mercator.center();
        double radius = mercator.radii()[0];
        double[] equator = mercator.mapCoordinates(center[0] + radius - 0.2, center[1], center[2]);
        assertNotNull(equator);
        assertEquals(0.5, equator[2], 1.0E-6, "el ecuador es la fila central del mapa");
        double[] pole = mercator.mapCoordinates(center[0], center[1] + radius - 0.2, center[2]);
        assertNotNull(pole);
        assertTrue(pole[2] > 0.99, "el polo recoge el borde del mapa");

        Projection laea = new Projection(settings(true, true, null, Double.NaN, false, false), selection, WORLD_TOP);
        double[] lambertCenter = laea.center();
        double lambertRadius = laea.radii()[0];
        assertEquals(Math.sqrt(64.0 * 32.0 / (4.0 * Math.PI)), lambertRadius, 1.0E-9, "misma área que el mapa");
        double[] top = laea.mapCoordinates(lambertCenter[0], lambertCenter[1] + lambertRadius - 0.2, lambertCenter[2]);
        assertNotNull(top);
        assertEquals(0.5, top[0], 1.0E-6, "LAEA pone el centro del mapa arriba");
        assertEquals(0.5, top[2], 1.0E-6);
        double[] bottom = laea.mapCoordinates(lambertCenter[0] + 0.01, lambertCenter[1] - lambertRadius + 0.2,
                lambertCenter[2]);
        assertNotNull(bottom);
        assertTrue(Math.abs(bottom[0] - 0.5) > 0.45 || Math.abs(bottom[2] - 0.5) > 0.45,
                "y el borde del mapa abajo");
    }

    @Test
    void diskToSquareIsTheConcentricMap() {
        assertArrayEquals(new double[]{0.0, 0.0}, Projection.diskToSquare(0.0, 1.0), 1.0E-12);
        assertArrayEquals(new double[]{1.0, 0.0}, Projection.diskToSquare(1.0, 0.0), 1.0E-12);
        assertArrayEquals(new double[]{1.0, 1.0}, Projection.diskToSquare(1.0, Math.PI / 4.0), 1.0E-12);
        assertArrayEquals(new double[]{0.0, -1.0}, Projection.diskToSquare(1.0, -Math.PI / 2.0), 1.0E-12);
    }

    @Test
    void radiiTwoBlockCenterAndAxesAreValidated() {
        int[] selection = {0, 0, 0, 31, 1, 15};
        Projection given = new Projection(settings(false, false, new double[]{10, 20}, 3, false, true), selection,
                WORLD_TOP);
        assertEquals(10.0, given.radii()[0]);
        assertEquals(20.0, given.halfLength());
        assertEquals(3.0, given.depth());
        assertEquals(0.0, given.center()[0] % 1.0, 1.0E-9, "-t centra en la arista entre dos bloques");
        assertThrows(IllegalArgumentException.class,
                () -> settings(false, false, null, Double.NaN, false, true));
        assertThrows(IllegalArgumentException.class, () -> Projection.AxisMap.parse("xxz"));
        Projection.AxisMap map = Projection.AxisMap.parse("-zyx");
        assertArrayEquals(new int[]{2, 1, 0}, map.axes());
        assertTrue(map.flipped()[0]);
    }

    @Test
    void aroundThePlayerAndNextToTheSelectionWhenThereIsNoRoom() {
        int[] selection = {0, 305, 0, 31, 306, 15};
        Projection projection = new Projection(settings(false, false, null, Double.NaN, false, false), selection,
                WORLD_TOP);
        assertTrue(projection.box()[0] > selection[3], "sin sitio arriba, al lado");
        Projection anchored = new Projection(new Projection.Settings(false, false, null, Double.NaN,
                Projection.AxisMap.DEFAULT, Deformations.Axis.Y, Deformations.Axis.X, false, false, false, false,
                new double[]{100.7, 64.2, -50.1}), new int[]{0, 0, 0, 31, 1, 15}, WORLD_TOP);
        assertArrayEquals(new double[]{100.5, 64.5, -50.5}, anchored.center(), 1.0E-9);
    }

    private static RegionSnapshot cylinderCapture() {
        // Selección 32×2×16 en el origen y aire encima hasta y=40 para la figura.
        RegionSnapshot.Builder builder = RegionSnapshot.builder(-2, 0, -2, 36, 40, 20)
                .state(new RegionSnapshot.StateInfo(0, null, "minecraft:air", true, false, false))
                .state(new RegionSnapshot.StateInfo(Shapes.DIRT, null, "minecraft:dirt", false, false, true))
                .state(new RegionSnapshot.StateInfo(Shapes.GRASS, null, "minecraft:grass_block", false, false, true));
        for (int x = -2; x < 34; x++) {
            for (int y = 0; y < 40; y++) {
                for (int z = -2; z < 18; z++) {
                    boolean inside = x >= 0 && x < 32 && y < 2 && z >= 0 && z < 16;
                    builder.set(x, y, z, inside ? (y == 0 ? Shapes.DIRT : Shapes.GRASS) : 0, inside);
                }
            }
        }
        return builder.build();
    }

    private static Projection.Settings settings(boolean sphere, boolean laea, double[] radii, double depth,
                                                boolean fill, boolean twoBlockCenter) {
        return new Projection.Settings(sphere, laea, radii, depth, Projection.AxisMap.DEFAULT, Deformations.Axis.Y,
                Deformations.Axis.X, false, fill, false, twoBlockCenter, null);
    }
}
