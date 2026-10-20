package com.komainu.moxia.sculpt.model;

import com.komainu.moxia.sculpt.BrushFalloff;
import org.junit.jupiter.api.Test;

import static com.komainu.moxia.sculpt.model.ModelGrids.DIRT;
import static com.komainu.moxia.sculpt.model.ModelGrids.GRASS;
import static com.komainu.moxia.sculpt.model.ModelGrids.NO_CANCEL;
import static com.komainu.moxia.sculpt.model.ModelGrids.SAND;
import static com.komainu.moxia.sculpt.model.ModelGrids.STONE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSculptorTest {
    // --------------------------------------------------------------------------------------------- Normales

    @Test
    void normalOfAPlanePointsUp() {
        ModelGrid grid = ModelGrids.plane(12, 20, 8);
        double[] normal = SurfaceNormals.at(grid, 0, 7, 0, 3);
        assertEquals(0.0, normal[0], 1.0E-9);
        assertEquals(1.0, normal[1], 1.0E-9);
        assertEquals(0.0, normal[2], 1.0E-9);
        // Y la media bajo el pincel, también.
        double[] average = ModelSculptor.brushNormal(grid, ModelSettings.defaults(ModelMode.DRAW, 0, 7, 0, 6));
        assertTrue(average[1] > 0.999, () -> "media " + average[0] + ", " + average[1] + ", " + average[2]);
    }

    @Test
    void normalOfAWallPointsAway() {
        ModelGrid grid = ModelGrid.create(-10, 0, -10, 21, 21, 21);
        ModelGrids.box(grid, -10, 0, -10, 0, 20, 10, STONE);
        double[] normal = SurfaceNormals.at(grid, 0, 10, 0, 3);
        assertTrue(normal[0] > 0.999, () -> "normal " + normal[0] + ", " + normal[1] + ", " + normal[2]);
    }

    @Test
    void normalsOfASphereAreRadial() {
        ModelGrid grid = ModelGrids.sphere(14, 9.5);
        int checked = 0;
        for (int index = 0; index < grid.size(); index++) {
            int x = grid.x(index);
            int y = grid.y(index);
            int z = grid.z(index);
            if (!grid.surface(x, y, z)) {
                continue;
            }
            double[] normal = SurfaceNormals.at(grid, x, y, z, 3);
            double length = Math.sqrt(x * x + y * y + z * z);
            double dot = (normal[0] * x + normal[1] * y + normal[2] * z) / length;
            assertTrue(dot > 0.93, "normal en " + x + ", " + y + ", " + z + " desviada: " + dot);
            checked++;
        }
        assertTrue(checked > 500);
    }

    @Test
    void averageNormalOnASphereFollowsTheHitPoint() {
        ModelGrid grid = ModelGrids.sphere(16, 10.5);
        // Pulsando en el «hombro» de la esfera, la normal media va hacia fuera por ahí.
        int x = 7;
        int y = 7;
        int z = 0;
        while (grid.solid(x + 1, y + 1, z)) {
            x++;
            y++;
        }
        double[] normal = ModelSculptor.brushNormal(grid, ModelSettings.defaults(ModelMode.DRAW, x, y, z, 4));
        double dot = (normal[0] + normal[1]) / Math.sqrt(2.0);
        assertTrue(dot > 0.95, () -> "normal media desviada: " + normal[0] + ", " + normal[1] + ", " + normal[2]);
    }

    @Test
    void signedDistanceIsExact() {
        ModelGrid grid = ModelGrid.create(-5, -5, -5, 11, 11, 11);
        grid.set(0, 0, 0, STONE);
        float[] distance = ModelFields.signedDistance(grid);
        assertEquals(-0.5, distance[grid.index(0, 0, 0)], 1.0E-6);
        assertEquals(0.5, distance[grid.index(1, 0, 0)], 1.0E-6);
        assertEquals(Math.sqrt(2.0) - 0.5, distance[grid.index(1, 1, 0)], 1.0E-5);
        assertEquals(Math.sqrt(3 * 3 + 4 * 4) - 0.5, distance[grid.index(3, 0, 4)], 1.0E-4);
    }

    // --------------------------------------------------------------------------------------------- Esculpir

    @Test
    void drawRaisesAPlaneAlongItsNormal() {
        ModelGrid grid = ModelGrids.plane(14, 24, 8);
        ModelSettings settings = ModelSettings.defaults(ModelMode.DRAW, 0, 7, 0, 8).withStrength(4)
                .withFalloff(BrushFalloff.SMOOTH);
        ModelSculptor.Result result = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT,
                MaterialChooser.AIR, NO_CANCEL);
        ModelGrid after = result.grid();
        assertEquals(0, result.removed());
        assertTrue(result.added() > 30);
        // En el centro sube unos 4 bloques; lejos del pincel no cambia nada.
        assertTrue(after.solid(0, 10, 0) && !after.solid(0, 12, 0), "altura del centro");
        assertFalse(after.solid(12, 8, 12));
        for (int index = 0; index < grid.size(); index++) {
            if (result.changed(index)) {
                int x = grid.x(index);
                int z = grid.z(index);
                assertTrue(x * x + z * z <= 9 * 9, "cambio fuera del pincel en " + x + ", " + z);
            }
        }
        // Lo nuevo de arriba es hierba y lo enterrado hereda de sus vecinos.
        assertEquals(GRASS, after.material(0, 11, 0));
    }

    @Test
    void invertedDrawSinksThePlane() {
        ModelGrid grid = ModelGrids.plane(14, 24, 12);
        ModelSettings settings = ModelSettings.defaults(ModelMode.DRAW, 0, 11, 0, 8).withStrength(3)
                .withInvert(true);
        ModelSculptor.Result result = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT,
                MaterialChooser.AIR, NO_CANCEL);
        assertEquals(0, result.added());
        assertTrue(result.removed() > 20);
        ModelGrid after = result.grid();
        assertFalse(after.solid(0, 10, 0));
        assertTrue(after.solid(0, 7, 0));
    }

    @Test
    void drawPushesAWallSideways() {
        ModelGrid grid = ModelGrid.create(-12, 0, -12, 25, 25, 25);
        ModelGrids.box(grid, -12, 0, -12, 0, 24, 12, STONE);
        ModelSettings settings = ModelSettings.defaults(ModelMode.DRAW, 0, 12, 0, 6).withStrength(3);
        ModelGrid after = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT, MaterialChooser.AIR,
                NO_CANCEL).grid();
        assertTrue(after.solid(2, 12, 0));
        assertFalse(after.solid(0, 22, 0) != grid.solid(0, 22, 0));
        assertFalse(after.solid(2, 20, 0));
    }

    // --------------------------------------------------------------------------------------------- Soldar y fundir

    @Test
    void lowThresholdAddsMassAndHighThresholdRemovesIt() {
        ModelGrid grid = ModelGrids.sphere(14, 7.5);
        ModelGrids.box(grid, -3, -3, -3, 3, 10, 3, STONE);
        int original = grid.solidCount();
        ModelSettings settings = ModelSettings.defaults(ModelMode.WELD, 0, 0, 0, 13).withSigma(1.5)
                .withFalloff(BrushFalloff.CONSTANT);
        int low = ModelSculptor.blur(grid, settings, 0.2, MaterialChooser.INHERIT).grid().solidCount();
        int middle = ModelSculptor.blur(grid, settings, 0.5, MaterialChooser.INHERIT).grid().solidCount();
        int high = ModelSculptor.blur(grid, settings, 0.8, MaterialChooser.INHERIT).grid().solidCount();
        assertTrue(low > original, "umbral bajo: " + low + " frente a " + original);
        assertTrue(high < original, "umbral alto: " + high + " frente a " + original);
        assertTrue(low > middle && middle > high);
    }

    @Test
    void weldJoinsLoosePiecesAndOnlyAdds() {
        ModelGrid grid = ModelGrid.create(-12, -8, -8, 25, 17, 17);
        ModelGrids.box(grid, -10, -3, -3, -2, 3, 3, STONE);
        ModelGrids.box(grid, 1, -3, -3, 9, 3, 3, SAND);
        assertEquals(2, ModelGrids.components(grid));
        ModelSettings settings = ModelSettings.defaults(ModelMode.WELD, 0, 0, 0, 8).withThreshold(0.3)
                .withFalloff(BrushFalloff.CONSTANT);
        ModelSculptor.Result result = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT,
                MaterialChooser.AIR, NO_CANCEL);
        assertEquals(0, result.removed());
        assertTrue(result.added() > 0);
        assertEquals(1, ModelGrids.components(result.grid()), "las dos piezas quedan unidas");
    }

    @Test
    void meltRoundsEdgesAndOnlyRemoves() {
        ModelGrid grid = ModelGrid.create(-10, -10, -10, 21, 21, 21);
        ModelGrids.box(grid, -5, -5, -5, 5, 5, 5, STONE);
        ModelSettings settings = ModelSettings.defaults(ModelMode.MELT, 0, 0, 0, 10)
                .withFalloff(BrushFalloff.CONSTANT);
        ModelSculptor.Result result = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT,
                MaterialChooser.AIR, NO_CANCEL);
        ModelGrid after = result.grid();
        assertEquals(0, result.added());
        assertTrue(result.removed() > 0);
        assertFalse(after.solid(5, 5, 5), "la esquina se funde");
        assertTrue(after.solid(5, 0, 0), "el centro de la cara se queda");
        assertTrue(after.solid(0, 0, 0));
    }

    // --------------------------------------------------------------------------------------------- Rugosidad

    @Test
    void roughenAddsExposedFacesButKeepsTheVolume() {
        ModelGrid grid = ModelGrids.sphere(16, 11.5);
        int volume = grid.solidCount();
        int faces = grid.exposedFaces();
        ModelSettings settings = ModelSettings.defaults(ModelMode.ROUGHEN, 0, 0, 0, 14).withStrength(1.5)
                .withFalloff(BrushFalloff.CONSTANT).withSeed(7);
        ModelSculptor.Result result = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT,
                MaterialChooser.AIR, NO_CANCEL);
        ModelGrid after = result.grid();
        assertTrue(result.added() > 0 && result.removed() > 0);
        assertTrue(after.exposedFaces() > faces * 1.15, "caras " + faces + " → " + after.exposedFaces());
        double change = Math.abs(after.solidCount() - volume) / (double) volume;
        assertTrue(change < 0.08, "el volumen cambia un " + change * 100 + " %");
    }

    // --------------------------------------------------------------------------------------------- Grietas

    @Test
    void shatterOnlyTouchesExistingBlocks() {
        for (ModelMode.Axis axis : ModelMode.Axis.values()) {
            ModelGrid grid = ModelGrids.sphere(14, 9.5);
            ModelGrids.box(grid, -14, -14, -14, 14, -6, 14, DIRT);
            ModelSettings settings = ModelSettings.defaults(ModelMode.SHATTER, 0, 0, 0, 12).withScale(4)
                    .withWidth(1.2).withAxis(axis).withDepth(0).withFalloff(BrushFalloff.CONSTANT).withSeed(3);
            ModelSculptor.Result air = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT,
                    MaterialChooser.AIR, NO_CANCEL);
            ModelSculptor.Result sand = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT,
                    (x, y, z, inherited, covered) -> SAND, NO_CANCEL);
            assertEquals(0, air.added());
            assertTrue(air.removed() > 50, axis + ": " + air.removed());
            assertEquals(0, sand.added() + sand.removed());
            assertTrue(sand.replaced() > 50);
            for (int index = 0; index < grid.size(); index++) {
                if (air.changed(index) || sand.changed(index)) {
                    assertTrue(grid.materialAt(index) != ModelGrid.FREE, axis + ": grieta en el aire");
                }
            }
        }
    }

    @Test
    void shatterWithAnAxisMakesColumns() {
        ModelGrid grid = ModelGrid.create(-12, 0, -12, 25, 20, 25);
        ModelGrids.box(grid, -12, 0, -12, 12, 19, 12, STONE);
        ModelSettings settings = ModelSettings.defaults(ModelMode.SHATTER, 0, 10, 0, 30).withScale(5)
                .withAxis(ModelMode.Axis.Y).withDepth(0).withFalloff(BrushFalloff.CONSTANT);
        ModelGrid after = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT, MaterialChooser.AIR,
                NO_CANCEL).grid();
        // Una grieta en Y atraviesa la columna entera: lo mismo en cada altura.
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                boolean crack = !after.solid(x, 2, z);
                for (int y = 3; y < 18; y++) {
                    assertEquals(crack, !after.solid(x, y, z));
                }
            }
        }
    }

    @Test
    void shatterDepthStaysNearTheSurface() {
        ModelGrid grid = ModelGrid.create(-12, 0, -12, 25, 24, 25);
        ModelGrids.box(grid, -12, 0, -12, 12, 19, 12, STONE);
        ModelSettings settings = ModelSettings.defaults(ModelMode.SHATTER, 0, 19, 0, 12).withScale(4).withDepth(2)
                .withFalloff(BrushFalloff.CONSTANT);
        ModelSculptor.Result result = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT,
                MaterialChooser.AIR, NO_CANCEL);
        assertTrue(result.removed() > 0);
        for (int index = 0; index < grid.size(); index++) {
            if (result.changed(index)) {
                assertTrue(grid.y(index) >= 18, "grieta a " + grid.y(index));
            }
        }
    }

    // --------------------------------------------------------------------------------------------- Roca

    @Test
    void rockAddsMassJoinedToTheGround() {
        ModelGrid grid = ModelGrids.plane(16, 30, 8);
        ModelSettings settings = ModelSettings.defaults(ModelMode.ROCK, 0, 7, 0, 6).withSeed(11);
        ModelSculptor.Result result = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT,
                MaterialChooser.AIR, NO_CANCEL);
        assertEquals(0, result.removed());
        assertTrue(result.added() > 200, "añade " + result.added());
        assertEquals(1, ModelGrids.components(result.grid()), "la roca sale del suelo, no flota");
        ModelSculptor.Result carved = ModelSculptor.apply(result.grid(), settings.withInvert(true),
                MaterialChooser.INHERIT, MaterialChooser.AIR, NO_CANCEL);
        assertEquals(0, carved.added());
        assertTrue(carved.removed() > 50);
    }

    // --------------------------------------------------------------------------------------------- Comunes

    @Test
    void maskedCellsNeverChange() {
        for (ModelMode mode : ModelMode.values()) {
            ModelGrid grid = ModelGrids.plane(12, 24, 8);
            for (int x = -12; x <= 12; x++) {
                for (int y = 0; y < 24; y++) {
                    for (int z = -12; z <= 0; z++) {
                        grid.setEditable(x, y, z, false);
                    }
                }
            }
            ModelSettings settings = ModelSettings.defaults(mode, 0, 7, 0, 8).withFalloff(BrushFalloff.CONSTANT);
            ModelSculptor.Result result = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT,
                    MaterialChooser.AIR, NO_CANCEL);
            for (int index = 0; index < grid.size(); index++) {
                if (result.changed(index)) {
                    assertTrue(grid.z(index) > 0, mode + " cambia una celda bloqueada");
                }
            }
        }
    }

    @Test
    void everyModeIsDeterministic() {
        for (ModelMode mode : ModelMode.values()) {
            ModelGrid first = ModelGrids.sphere(14, 9.5);
            ModelGrids.box(first, -14, -14, -14, 14, -5, 14, DIRT);
            ModelGrid second = ModelGrids.copy(first);
            ModelSettings settings = ModelSettings.defaults(mode, 0, 9, 0, 7).withSeed(42);
            int[] one = ModelSculptor.apply(first, settings, MaterialChooser.INHERIT, MaterialChooser.AIR,
                    NO_CANCEL).after();
            int[] two = ModelSculptor.apply(second, settings, MaterialChooser.INHERIT, MaterialChooser.AIR,
                    NO_CANCEL).after();
            assertArrayEquals(one, two, mode + " no es determinista");
        }
    }

    @Test
    void coveredGrassCanBecomeDirt() {
        ModelGrid grid = ModelGrids.plane(14, 24, 8);
        ModelSettings settings = ModelSettings.defaults(ModelMode.DRAW, 0, 7, 0, 8).withStrength(4);
        MaterialChooser buried = (x, y, z, inherited, covered) -> covered && inherited == GRASS ? DIRT : inherited;
        ModelGrid after = ModelSculptor.apply(grid, settings, buried, MaterialChooser.AIR, NO_CANCEL).grid();
        assertEquals(DIRT, after.material(0, 8, 0));
        int top = 8;
        while (after.solid(0, top + 1, 0)) {
            top++;
        }
        assertEquals(GRASS, after.material(0, top, 0));
    }
}
