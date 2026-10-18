package com.komainu.moxia.texture;

import com.komainu.moxia.region.RegionSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadeFieldTest {
    private static final TextureContext.Settings SETTINGS = TextureContext.Settings.at(0, 30, 0, 1L);

    /**
     * Suelo de piedra de 24×24 (y = 0) con un cubo de 6×6×6 en medio (x y z de 9 a 14) y aire alrededor.
     * Selección: todo.
     */
    private static RegionSnapshot cubeOnFloor() {
        RegionSnapshot.Builder builder = TextureScenes.empty(0, 0, 0, 24, 16, 24);
        for (int x = 0; x < 24; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 24; z++) {
                    boolean cube = x >= 9 && x <= 14 && z >= 9 && z <= 14 && y >= 1 && y <= 6;
                    builder.set(x, y, z, y == 0 || cube ? TextureScenes.STONE : TextureScenes.AIR, true);
                }
            }
        }
        return builder.build();
    }

    private static TextureContext context(RegionSnapshot snapshot) {
        return new TextureContext(snapshot, TextureContext.Filter.ALL, SETTINGS, () -> false);
    }

    private static double at(TextureContext context, double[] values, int x, int y, int z) {
        int index = context.index(x, y, z);
        assertTrue(index >= 0, "(" + x + "," + y + "," + z + ")");
        return values[index];
    }

    @Test
    void faceTowardsTheLightIsBrighterThanTheFaceAway() {
        TextureContext context = context(cubeOnFloor());
        double[] east = ShadeField.sun(new double[]{1, 0.4, 0}, 0.0, 3, 0.0, null).evaluate(context);
        double lit = at(context, east, 14, 3, 11);
        double dark = at(context, east, 9, 3, 11);
        assertTrue(lit > dark + 0.4, "cara este " + lit + " / cara oeste " + dark);
        double[] west = ShadeField.sun(new double[]{-1, 0.4, 0}, 0.0, 3, 0.0, null).evaluate(context);
        assertTrue(at(context, west, 9, 3, 11) > at(context, west, 14, 3, 11) + 0.4, "al girar la luz se invierte");
        // Con la luz de arriba, el techo del cubo gana a las paredes.
        double[] up = ShadeField.sun(new double[]{0.05, 1, 0.05}, 0.0, 3, 0.0, null).evaluate(context);
        assertTrue(at(context, up, 11, 6, 11) > at(context, up, 14, 3, 11) + 0.3);
        assertTrue(at(context, up, 11, 6, 11) > at(context, up, 11, 3, 9) + 0.3);
    }

    @Test
    void castShadowsDarkenTheFloorBehindTheCube() {
        TextureContext context = context(cubeOnFloor());
        double[] towards = {1, 0.6, 0};
        double[] withShadow = ShadeField.sun(towards, 0.0, 3, 1.0, null).brightness(context);
        double[] withoutShadow = ShadeField.sun(towards, 0.0, 3, 0.0, null).brightness(context);
        // Al oeste del cubo (la luz viene del este) el suelo queda a la sombra; lejos, no.
        double shadowed = at(context, withShadow, 4, 0, 11);
        double open = at(context, withShadow, 20, 0, 11);
        assertTrue(shadowed < open - 0.3, "sombra " + shadowed + " / abierto " + open);
        assertEquals(at(context, withoutShadow, 4, 0, 11), at(context, withoutShadow, 20, 0, 11), 1.0E-9,
                "sin sombras, el mismo suelo recibe la misma luz");
    }

    @Test
    void ambientOcclusionDarkensCornersAndCrevices() {
        TextureContext context = context(TextureScenes.wall());
        double[] values = ShadeField.sun(new double[]{0, 0.3, -1}, 1.0, 3, 0.0, null).brightness(context);
        double flat = at(context, values, 21, 8, 0);
        double crevice = at(context, values, 11, 8, 1);
        double inner = at(context, values, 8, 8, 0);
        assertTrue(crevice < flat - 0.05, "grieta " + crevice + " / pared " + flat);
        assertTrue(inner < flat - 0.02, "rincón junto al contrafuerte " + inner + " / pared " + flat);
    }

    @Test
    void onlyTargetBlocksAreShaded() {
        TextureContext context = context(cubeOnFloor());
        double[] values = ShadeField.sun(new double[]{1, 1, 0}, 0.5, 2, 0.5, (x, y, z) -> y > 0).evaluate(context);
        assertTrue(Double.isNaN(at(context, values, 3, 0, 3)), "el suelo no es objetivo");
        assertTrue(!Double.isNaN(at(context, values, 14, 3, 11)));
        assertTrue(Double.isNaN(at(context, values, 11, 3, 11)), "el interior del cubo no da al aire");
    }

    @Test
    void levelsUseThePaletteWithoutExaggeratingSmallDifferences() {
        double[] wide = ShadeField.levels(new double[]{0.1, 0.3, Double.NaN, 0.5, 0.9});
        assertEquals(0.0, wide[0], 1.0E-9);
        assertEquals(1.0, wide[4], 1.0E-9);
        assertTrue(Double.isNaN(wide[2]));
        double[] narrow = ShadeField.levels(new double[]{0.50, 0.52, 0.54, 0.56});
        for (double value : narrow) {
            assertTrue(value > 0.3 && value < 0.7, "una variación de 0,06 no recorre toda la paleta: " + value);
        }
    }

    @Test
    void pointLightComesFromItsPosition() {
        TextureContext context = context(cubeOnFloor());
        double[] values = ShadeField.spot(new double[]{11.5, 4, 22}, 0.0, 3, 0.0, null).evaluate(context);
        assertTrue(at(context, values, 11, 3, 14) > at(context, values, 11, 3, 9) + 0.4,
                "el foco al sur alumbra la cara sur");
        assertEquals(ShadeField.sun(new double[]{1, 0, 0}, 0, 3, 0, null).padding(), 8);
    }
}
