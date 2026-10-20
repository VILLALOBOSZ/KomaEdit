package com.komainu.moxia.terrain.stamp;

import com.komainu.moxia.terrain.HeightStamp;
import com.komainu.moxia.terrain.StampLibrary;
import com.komainu.moxia.terrain.SurfacePalette;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La biblioteca de sellos de serie: que estén todos, bien hechos, iguales a sus recetas y sin repetirse. */
class StampLibraryTest {
    @Test
    void theLibraryIsLargeAndEveryCategoryHasSeveralStamps() {
        List<StampInfo> all = StampLibrary.all();
        assertTrue(all.size() >= 60 && all.size() <= 80, all.size() + " sellos");
        Map<StampCategory, Integer> perCategory = new EnumMap<>(StampCategory.class);
        Set<String> names = new HashSet<>();
        for (StampInfo info : all) {
            assertTrue(info.name().matches("[a-z0-9_]{1,64}"), info.name());
            assertTrue(names.add(info.name()), "repetido: " + info.name());
            assertFalse(info.description().isBlank(), info.name());
            assertTrue(info.heightRatio() > 0.05 && info.heightRatio() <= 1.2, info.name() + " " + info.heightRatio());
            perCategory.merge(info.category(), 1, Integer::sum);
            // La paleta de su categoría es un preajuste de verdad.
            SurfacePalette.parse(info.palette());
        }
        for (StampCategory category : StampCategory.values()) {
            assertTrue(perCategory.getOrDefault(category, 0) >= 4, category + ": " + perCategory.get(category));
        }
        // Los nombres de la 1.8 siguen valiendo: nadie pierde sus órdenes ni sus presets.
        for (String old : List.of("montana", "pico", "colina", "loma", "cordillera", "macizo", "volcan", "crater",
                "dolina", "valle", "duna", "dunas", "mesa", "terrazas", "acantilado", "barrancos", "aguja", "domo",
                "atolon", "circo")) {
            assertTrue(StampLibrary.has(old), old);
        }
    }

    @Test
    void everyShippedStampLoadsWithItsBorderAtTheNeutralGray() throws Exception {
        for (StampInfo info : StampLibrary.all()) {
            try (InputStream input = StampLibrary.class.getResourceAsStream(StampLibrary.RESOURCE + info.name()
                    + ".png")) {
                assertNotNull(input, "falta el PNG de " + info.name() + " en el jar");
            }
            HeightStamp stamp = StampLibrary.get(info.name());
            int size = StampLibrary.SIZE;
            assertEquals(size, stamp.width(), info.name());
            assertEquals(info.neutral(), stamp.neutral(), 1e-9, info.name());
            double up = 0.0;
            double down = 0.0;
            for (int row = 0; row < size; row++) {
                double y = row / (size - 1.0) * 2.0 - 1.0;
                for (int column = 0; column < size; column++) {
                    double x = column / (size - 1.0) * 2.0 - 1.0;
                    double r = Math.sqrt(x * x + y * y);
                    double value = stamp.pixel(column, row);
                    assertTrue(value >= 0.0 && value <= 1.0, info.name());
                    if (r >= 1.0) {
                        assertEquals(info.neutral(), value, 1e-6, info.name() + " no llega al gris neutro en el borde");
                    } else if (r >= 0.98) {
                        assertEquals(info.neutral(), value, 0.02, info.name() + " no se funde con el borde");
                    }
                    up = Math.max(up, value - info.neutral());
                    down = Math.max(down, info.neutral() - value);
                }
            }
            double span = Math.max(info.neutral() < 1.0 ? up / (1.0 - info.neutral()) : 0.0,
                    info.neutral() > 0.0 ? down / info.neutral() : 0.0);
            assertEquals(1.0, span, 0.01, info.name() + " usa toda la altura");
        }
    }

    @Test
    void theShippedPngsAreWhatTheRecipesGenerate() {
        // Si una receta cambia, hay que volver a escribir los PNG (StampResourcesDump) o esto avisa.
        for (StampRecipe recipe : StampRecipes.all()) {
            HeightStamp shipped = StampLibrary.get(recipe.name());
            float[] generated = recipe.generate(StampLibrary.SIZE, recipe.defaultSeed());
            double difference = 0.0;
            for (int index = 0; index < generated.length; index++) {
                int size = StampLibrary.SIZE;
                difference += Math.abs(shipped.pixel(index % size, index / size) - generated[index]);
            }
            double mean = difference / generated.length;
            assertTrue(mean < 0.004, recipe.name() + ": el PNG del jar no es el de la receta (diferencia media "
                    + mean + ")");
        }
    }

    @Test
    void generationIsDeterministicPerSeed() {
        for (StampRecipe recipe : StampRecipes.all()) {
            float[] first = recipe.generate(40, 7L);
            float[] again = recipe.generate(40, 7L);
            for (int index = 0; index < first.length; index++) {
                assertEquals(first[index], again[index], 0.0f, recipe.name() + " con la misma semilla");
            }
        }
        float[] one = StampRecipes.byName("montana").generate(40, 1L);
        float[] two = StampRecipes.byName("montana").generate(40, 2L);
        assertFalse(java.util.Arrays.equals(one, two), "otra semilla, otros detalles");
    }

    @Test
    void noTwoStampsAreNearlyTheSame() {
        // Correlación del relieve y de la pendiente, probando giros cada 15° y espejo: las formas redondas se
        // parecen algo entre sí (0,9), dos copias casi iguales pasan de 0,97.
        List<StampInfo> all = StampLibrary.all();
        List<double[][]> variants = new ArrayList<>();
        for (StampInfo info : all) {
            HeightStamp stamp = StampLibrary.get(info.name());
            double[][] turned = new double[48][];
            for (int index = 0; index < 48; index++) {
                turned[index] = features(stamp, Math.toRadians((index % 24) * 15), index >= 24);
            }
            variants.add(turned);
        }
        List<String> similar = new ArrayList<>();
        for (int first = 0; first < all.size(); first++) {
            for (int second = first + 1; second < all.size(); second++) {
                double best = -1.0;
                for (double[] turned : variants.get(second)) {
                    best = Math.max(best, correlation(variants.get(first)[0], turned));
                }
                if (best > 0.97) {
                    similar.add(all.get(first).name() + " ~ " + all.get(second).name() + " (" + best + ")");
                }
            }
        }
        assertTrue(similar.isEmpty(), "sellos casi iguales: " + similar);
    }

    private static double[] features(HeightStamp stamp, double angle, boolean mirror) {
        int side = 32;
        double[] relief = new double[side * side];
        StampPlacement placement = new StampPlacement(1.0, angle, 1.0, mirror, 0L, 0.0);
        for (int row = 0; row < side; row++) {
            for (int column = 0; column < side; column++) {
                relief[row * side + column] = placement.relief(stamp, column / (side - 1.0) * 2.0 - 1.0,
                        row / (side - 1.0) * 2.0 - 1.0);
            }
        }
        double[] result = new double[side * side * 2];
        for (int row = 0; row < side; row++) {
            for (int column = 0; column < side; column++) {
                int index = row * side + column;
                result[index] = relief[index];
                double gx = relief[row * side + Math.min(side - 1, column + 1)]
                        - relief[row * side + Math.max(0, column - 1)];
                double gy = relief[Math.min(side - 1, row + 1) * side + column]
                        - relief[Math.max(0, row - 1) * side + column];
                result[side * side + index] = Math.sqrt(gx * gx + gy * gy) * 4.0;
            }
        }
        return result;
    }

    private static double correlation(double[] a, double[] b) {
        double meanA = 0.0;
        double meanB = 0.0;
        for (int index = 0; index < a.length; index++) {
            meanA += a[index];
            meanB += b[index];
        }
        meanA /= a.length;
        meanB /= b.length;
        double ab = 0.0;
        double aa = 0.0;
        double bb = 0.0;
        for (int index = 0; index < a.length; index++) {
            double da = a[index] - meanA;
            double db = b[index] - meanB;
            ab += da * db;
            aa += da * da;
            bb += db * db;
        }
        return aa == 0.0 || bb == 0.0 ? 0.0 : ab / Math.sqrt(aa * bb);
    }

    @Test
    void thumbnailsAreColouredAndShaded() {
        for (String name : List.of("montana", "dunas", "isla", "canon", "glaciar")) {
            int[] pixels = StampLibrary.thumbnail(name, 48);
            assertEquals(48 * 48, pixels.length);
            Set<Integer> colours = new HashSet<>();
            boolean gray = true;
            for (int pixel : pixels) {
                assertEquals(0xFF, pixel >>> 24, name + ": opaca");
                colours.add(pixel);
                int red = (pixel >> 16) & 0xFF;
                int green = (pixel >> 8) & 0xFF;
                int blue = pixel & 0xFF;
                gray &= red == green && green == blue;
            }
            assertFalse(gray, name + ": no es un gris plano");
            assertTrue(colours.size() > 200, name + ": " + colours.size() + " colores, con su sombreado");
        }
        assertNotEquals(StampLibrary.thumbnail("duna", 32)[0], StampLibrary.thumbnail("montana", 32)[0],
                "cada categoría con sus colores");
    }
}
