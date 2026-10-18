package com.komainu.moxia.text;

import com.komainu.moxia.deform.SmoothProfile;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {
    private static final TextRaster.Settings EIGHT = new TextRaster.Settings(8.0, 0.0, 1.0, TextRaster.Align.CENTER);

    @Test
    void builtinFontsLoadAndCoverSpanish() {
        for (TextFonts.Builtin builtin : TextFonts.builtins()) {
            Font font = TextFonts.load(builtin.id(), null);
            assertEquals(-1, font.canDisplayUpTo("ÁÉÍÓÚÜÑáéíóúüñ¿¡ Hola, señor «año»"),
                    builtin.id());
            for (String alias : builtin.aliases()) {
                assertEquals(font, TextFonts.load(alias, null), alias);
            }
        }
        assertEquals(TextFonts.load("sans", null), TextFonts.load(null, null), "sin -f, la de serie");
        assertThrows(IllegalArgumentException.class, () -> TextFonts.load("no_existe", null));
        assertThrows(IllegalArgumentException.class, () -> TextFonts.load("../config", null));
    }

    @Test
    void capitalsAreAsTallAsTheSize() {
        TextRaster raster = TextRaster.render(TextFonts.load("sans", null), "H", EIGHT);
        int[] rows = filledRows(raster);
        assertEquals(8, rows[1] - rows[0] + 1, "una H de tamaño 8 mide 8 bloques");
        assertEquals(0, rows[0], "y se apoya en la línea base");
        TextRaster big = TextRaster.render(TextFonts.load("mono", null), "H", new TextRaster.Settings(20.0, 0.0, 1.0,
                TextRaster.Align.CENTER));
        int[] bigRows = filledRows(big);
        assertEquals(20, bigRows[1] - bigRows[0] + 1);
    }

    @Test
    void accentsAndEnyeAreDrawn() {
        Font font = TextFonts.load("sans", null);
        TextRaster plain = TextRaster.render(font, "N", EIGHT);
        TextRaster enye = TextRaster.render(font, "Ñ", EIGHT);
        TextRaster accent = TextRaster.render(font, "É", EIGHT);
        assertTrue(filledRows(enye)[1] > filledRows(plain)[1] + 1, "la virgulilla queda encima de la N");
        assertTrue(filledRows(accent)[1] > filledRows(plain)[1] + 1, "la tilde queda encima de la E");
        assertTrue(enye.filledCount() > plain.filledCount());
        TextRaster lower = TextRaster.render(font, "ñ", EIGHT);
        assertTrue(lower.filledCount() > TextRaster.render(font, "n", EIGHT).filledCount());
    }

    @Test
    void linesStackDownAndSpacingWidens() {
        Font font = TextFonts.load("sans", null);
        TextRaster one = TextRaster.render(font, "AB", EIGHT);
        TextRaster two = TextRaster.render(font, "A\\nB", EIGHT);
        int[] oneRows = filledRows(one);
        int[] twoRows = filledRows(two);
        assertTrue(twoRows[0] < -8, "la segunda línea va por debajo de la base");
        assertEquals(oneRows[1], twoRows[1], "la primera línea no se mueve");
        TextRaster wide = TextRaster.render(font, "AB", new TextRaster.Settings(8.0, 3.0, 1.0,
                TextRaster.Align.CENTER));
        assertTrue(width(wide) >= width(one) + 2, "el espaciado separa las letras");
        assertThrows(IllegalArgumentException.class, () -> TextRaster.render(font, "  ", EIGHT));
        assertThrows(IllegalArgumentException.class, () -> TextRaster.render(font, "x".repeat(300), EIGHT));
    }

    @Test
    void borderSurroundsTheLetters() {
        TextRaster letters = TextRaster.render(TextFonts.load("sans", null), "KO", EIGHT);
        TextRaster ring = letters.border(1.0);
        int overlap = 0;
        for (int v = letters.minV(); v <= letters.maxV(); v++) {
            for (int u = letters.minU(); u <= letters.maxU(); u++) {
                if (!letters.filled(u, v)) {
                    continue;
                }
                if (ring.filled(u, v)) {
                    overlap++;
                }
                for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nu = u + offset[0];
                    int nv = v + offset[1];
                    assertTrue(letters.coverage(nu, nv) + ring.coverage(nu, nv) > 0.0,
                            "hueco entre la letra y el borde en " + nu + "," + nv);
                }
            }
        }
        assertEquals(0, overlap, "el borde no pisa la letra");
        assertEquals(letters.minU() - 1, ring.minU());
        assertThrows(IllegalArgumentException.class, () -> letters.border(0.0));
    }

    @Test
    void placementFollowsTheLook() {
        int[] anchor = {10, 64, -3};
        int[][] facings = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
        for (TextPlacement.Mode mode : TextPlacement.Mode.values()) {
            for (int[] facing : facings) {
                TextPlacement placement = TextPlacement.of(mode, anchor, facing);
                for (int[] local : new int[][]{{0, 0, 0}, {3, -2, 1}, {-5, 7, 2}}) {
                    int[] world = placement.world(local[0], local[1], local[2]);
                    assertArrayEquals(local, placement.local(world[0], world[1], world[2]));
                    double[] center = placement.local(world[0] + 0.5, world[1] + 0.5, world[2] + 0.5);
                    assertEquals(local[0] + 0.5, center[0], 1e-9);
                    assertEquals(local[1] + 0.5, center[1], 1e-9);
                    assertEquals(local[2] + 0.5, center[2], 1e-9);
                }
            }
        }
        // Mirando al norte: la derecha es el este, de pie crece hacia el jugador (sur) y en el suelo hacia arriba.
        TextPlacement wall = TextPlacement.of(TextPlacement.Mode.WALL, anchor, facings[0]);
        assertArrayEquals(new int[]{1, 0, 0}, wall.right());
        assertArrayEquals(new int[]{0, 0, 1}, wall.out());
        TextPlacement floor = TextPlacement.of(TextPlacement.Mode.FLOOR, anchor, facings[0]);
        assertArrayEquals(new int[]{0, 0, -1}, floor.up(), "tumbado se lee desde los pies");
        assertArrayEquals(new int[]{0, 1, 0}, floor.out());
        TextPlacement ceiling = TextPlacement.of(TextPlacement.Mode.CEILING, anchor, facings[0]);
        assertArrayEquals(new int[]{0, -1, 0}, ceiling.out(), "colgado del techo crece hacia abajo");
        assertEquals(TextPlacement.Mode.FLOOR, TextPlacement.modeForPitch(70));
        assertEquals(TextPlacement.Mode.WALL, TextPlacement.modeForPitch(10));
        assertEquals(TextPlacement.Mode.CEILING, TextPlacement.modeForPitch(-80));
        assertArrayEquals(new int[]{8, 61, -3}, wall.shifted(2, 3).anchor());
    }

    @Test
    void modelPlacesEveryLetterBlockAndNeverRemovesAnything() {
        TextRaster letters = TextRaster.render(TextFonts.load("sans", null), "Año", EIGHT);
        TextPlacement placement = TextPlacement.of(TextPlacement.Mode.WALL, new int[]{0, 64, 0}, new int[]{0, 0, -1});
        TextModel model = new TextModel(letters, null, placement, 2, 1, TextModel.Gradient.VERTICAL, false, null);
        int[] box = model.worldBox();
        RegionSnapshot air = snapshot(box, (x, y, z) -> 0);
        TextModel.Result result = model.plan(air, SmoothProfile.NONE, () -> {
        });
        assertEquals(letters.filledCount() * 2, result.size(), "cada bloque de letra, en los dos de grosor");
        assertEquals(0, result.slabs() + result.stairs());
        // Con piedra en la mitad de la caja y la máscara «solo aire», la piedra se queda.
        int middle = (box[0] + box[3]) / 2;
        RegionSnapshot half = snapshot(box, (x, y, z) -> x < middle ? 1 : 0);
        TextModel masked = new TextModel(letters, null, placement, 2, 1, TextModel.Gradient.VERTICAL, false,
                (x, y, z) -> !(x < middle));
        TextModel.Result partial = masked.plan(half, SmoothProfile.NONE, () -> {
        });
        assertTrue(partial.size() > 0 && partial.size() < result.size());
        for (int index = 0; index < partial.size(); index++) {
            assertTrue(PackedBlockPlan.unpackX(partial.positions().getLong(index)) >= middle);
        }
    }

    @Test
    void smoothingAddsPiecesOnlyInEmptyCellsAndGradientsUseThePalette() {
        TextRaster letters = TextRaster.render(TextFonts.load("sans", null), "SO", EIGHT);
        TextPlacement placement = TextPlacement.of(TextPlacement.Mode.WALL, new int[]{0, 64, 0}, new int[]{0, 0, -1});
        TextModel model = new TextModel(letters, null, placement, 1, 4, TextModel.Gradient.VERTICAL, false, null);
        int[] box = model.worldBox();
        RegionSnapshot air = snapshot(box, (x, y, z) -> 0);
        TextModel.Result smooth = model.plan(air, SmoothProfile.parse("SlabsAndStairs"), () -> {
        });
        assertTrue(smooth.slabs() + smooth.stairs() > 0, "las curvas llevan losas y escaleras");
        Set<Integer> layers = new HashSet<>();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        int lowLayer = -1;
        int highLayer = -1;
        for (int index = 0; index < smooth.size(); index++) {
            int layer = smooth.layers().getInt(index);
            layers.add(layer);
            int y = PackedBlockPlan.unpackY(smooth.positions().getLong(index));
            if (y < lowest) {
                lowest = y;
                lowLayer = layer;
            }
            if (y > highest) {
                highest = y;
                highLayer = layer;
            }
            assertNotEquals(SmoothPiece.EMPTY, smooth.pieces().getByte(index));
        }
        assertEquals(Set.of(0, 1, 2, 3), layers, "el degradado usa toda la paleta");
        assertEquals(0, lowLayer, "la primera entrada abajo");
        assertEquals(3, highLayer, "la última arriba");

        // Un borde con material propio usa la capa siguiente a la paleta.
        TextModel bordered = new TextModel(letters, letters.border(1.0), placement, 1, 1,
                TextModel.Gradient.VERTICAL, true, null);
        TextModel.Result withBorder = bordered.plan(snapshot(bordered.worldBox(), (x, y, z) -> 0),
                SmoothProfile.NONE, () -> {
                });
        assertTrue(withBorder.layers().contains(1) && withBorder.layers().contains(0));
        assertTrue(withBorder.size() > letters.filledCount());
    }

    @Test
    void requestsParseQuotesFlagsAndPalettes() {
        TextRequest request = TextRequest.parse("\"Hola, \\\"señor\\\"\" oak_planks -s 12 -d 3 -b 2 -o black_wool"
                + " -w SlabsAndStairs -m air -f mono -v");
        assertEquals("Hola, \"señor\"", request.text());
        assertEquals("oak_planks", request.material());
        assertEquals(12.0, request.size());
        assertEquals(3, request.depth());
        assertEquals(2.0, request.border());
        assertEquals("black_wool", request.borderMaterial());
        assertEquals("SlabsAndStairs", request.smoothing());
        assertEquals("air", request.mask());
        assertEquals("mono", request.font());
        assertEquals(TextRequest.Orientation.STANDING, request.orientation());
        assertFalse(request.palette());

        TextRequest palette = TextRequest.parse("Kyobble ##Magma");
        assertEquals("Kyobble", palette.text());
        assertTrue(palette.palette(), "##Nombre es una paleta en degradado");
        TextRequest horizontal = TextRequest.parse("Hola stone,andesite,diorite -g h -h");
        assertEquals(TextModel.Gradient.HORIZONTAL, horizontal.gradient());
        assertEquals(TextRequest.Orientation.LYING, horizontal.orientation());
        TextRequest defaults = TextRequest.parse("Hola");
        assertEquals(TextRequest.DEFAULT_MATERIAL, defaults.material());
        assertEquals(TextFonts.DEFAULT, defaults.font());
        assertEquals(1.0, TextRequest.parse("Hola -o red_wool").border(), "-o sin -b pone un borde de 1");
        assertThrows(IllegalArgumentException.class, () -> TextRequest.parse("\"sin cerrar"));
        assertThrows(IllegalArgumentException.class, () -> TextRequest.parse("Hola -v -h"));
        assertThrows(IllegalArgumentException.class, () -> TextRequest.parse("Hola -s 999"));
        assertThrows(IllegalArgumentException.class, () -> TextRequest.parse("Hola stone sobra"));
    }

    // ------------------------------------------------------------------ ayudas

    @FunctionalInterface
    interface Fill {
        int id(int x, int y, int z);
    }

    /** Foto de la caja (más un bloque de margen) con aire (0) o piedra (1). */
    static RegionSnapshot snapshot(int[] box, Fill fill) {
        int minX = box[0] - 1;
        int minY = box[1] - 1;
        int minZ = box[2] - 1;
        RegionSnapshot.Builder builder = RegionSnapshot.builder(minX, minY, minZ, box[3] - box[0] + 3,
                        box[4] - box[1] + 3, box[5] - box[2] + 3)
                .state(new RegionSnapshot.StateInfo(0, null, "minecraft:air", true, false, false))
                .state(new RegionSnapshot.StateInfo(1, null, "minecraft:stone", false, false, true));
        for (int y = minY; y <= box[4] + 1; y++) {
            for (int z = minZ; z <= box[5] + 1; z++) {
                for (int x = minX; x <= box[3] + 1; x++) {
                    boolean inside = x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2]
                            && z <= box[5];
                    builder.set(x, y, z, fill.id(x, y, z), inside);
                }
            }
        }
        return builder.build();
    }

    /** Filas (v) con algún bloque lleno: {@code {mínima, máxima}}. */
    static int[] filledRows(TextRaster raster) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int v = raster.minV(); v <= raster.maxV(); v++) {
            for (int u = raster.minU(); u <= raster.maxU(); u++) {
                if (raster.filled(u, v)) {
                    low = Math.min(low, v);
                    high = Math.max(high, v);
                }
            }
        }
        return new int[]{low, high};
    }

    static int width(TextRaster raster) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int v = raster.minV(); v <= raster.maxV(); v++) {
            for (int u = raster.minU(); u <= raster.maxU(); u++) {
                if (raster.filled(u, v)) {
                    low = Math.min(low, u);
                    high = Math.max(high, u);
                }
            }
        }
        return high - low + 1;
    }
}
