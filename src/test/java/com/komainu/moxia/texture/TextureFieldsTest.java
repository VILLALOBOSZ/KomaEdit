package com.komainu.moxia.texture;

import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.region.RegionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureFieldsTest {
    private static final BooleanSupplier NEVER = () -> false;
    private static final TextureContext.Settings SETTINGS = TextureContext.Settings.at(12, 8, -20, 7L);

    private static TextureContext wall() {
        return new TextureContext(TextureScenes.wall(), TextureContext.Filter.ALL, SETTINGS, NEVER);
    }

    private static double value(TextureContext context, double[] values, int x, int y, int z) {
        int index = context.index(x, y, z);
        assertTrue(index >= 0, "(" + x + "," + y + "," + z + ") debería texturizarse");
        return values[index];
    }

    @Test
    void contextFindsTargetsSurfaceAndNormals() {
        TextureContext context = wall();
        assertTrue(context.size() > 1_000);
        int front = context.index(2, 8, 0);
        int inside = context.index(2, 8, 2);
        assertTrue(context.surface(front));
        assertFalse(context.surface(inside), "el interior de la pared no da al aire");
        double[] normal = context.normals(2)[front];
        assertTrue(normal[2] < -0.9, "la cara delantera mira hacia z negativa: " + normal[2]);
        assertEquals(27, context.solidInBox(2, 8, 2, 1), "en el interior la caja de 3×3×3 está llena");
        assertEquals(18, context.solidInBox(2, 8, 0, 1), "en la cara, dos tercios");
    }

    @Test
    void ambientDarkensCreviceAndLightsCorners() {
        TextureContext context = wall();
        double[] values = new TextureFields.Ambient(3, 0.0, 0.0).evaluate(context);
        double flat = value(context, values, 21, 8, 0);
        double crevice = value(context, values, 11, 8, 1);
        double corner = value(context, values, 7, 15, -2);
        assertTrue(crevice < flat, "la grieta queda más ocluida que la pared lisa: " + crevice + " / " + flat);
        assertTrue(corner > flat, "la esquina del saliente ve más cielo: " + corner + " / " + flat);
        assertTrue(Double.isNaN(value(context, values, 2, 8, 2)), "el interior no se toca");
    }

    @Test
    void curvatureSeparatesEdgesFromCorners() {
        TextureContext context = wall();
        double[] values = new TextureFields.Curvature(2, 0.0, 0.0).evaluate(context);
        double flat = value(context, values, 21, 8, 0);
        double edge = value(context, values, 7, 8, -2);
        double inner = value(context, values, 8, 8, 0);
        assertEquals(0.5, flat, 1.0E-9, "en una pared lisa la curvatura es la del centro de la paleta");
        assertTrue(edge > 0.8, "arista convexa hacia el final: " + edge);
        assertTrue(inner < 0.3, "rincón cóncavo hacia el principio: " + inner);
    }

    @Test
    void axisGradientIsAbsoluteOrPerColumn() {
        TextureContext context = wall();
        double[] absolute = new TextureFields.AxisGradient(1, false).evaluate(context);
        assertEquals(0.0, value(context, absolute, 2, 0, 0), 1.0E-9);
        assertEquals(1.0, value(context, absolute, 2, 15, 0), 1.0E-9);
        assertEquals(7.0 / 15.0, value(context, absolute, 2, 7, 0), 1.0E-9);
        double[] relative = new TextureFields.AxisGradient(0, true).evaluate(context);
        assertEquals(0.0, value(context, relative, 0, 3, 0), 1.0E-9);
        assertEquals(1.0, value(context, relative, 23, 3, 0), 1.0E-9);
    }

    @Test
    void noiseAndCellsAreDeterministicPerSeed() {
        TextureContext context = wall();
        NoiseSpec noise = NoiseSpec.parse("Perlin(f:0.1)");
        assertArrayEquals(new TextureFields.Noise(noise, 1.0, 5L).evaluate(context),
                new TextureFields.Noise(noise, 1.0, 5L).evaluate(context));
        double[] first = new TextureFields.Cells(12, 0.0, 0.0, 3L, 5).evaluate(context);
        double[] again = new TextureFields.Cells(12, 0.0, 0.0, 3L, 5).evaluate(context);
        double[] other = new TextureFields.Cells(12, 0.0, 0.0, 4L, 5).evaluate(context);
        assertArrayEquals(first, again);
        assertFalse(java.util.Arrays.equals(first, other), "otra semilla, otras celdas");
        long distinct = java.util.Arrays.stream(first).distinct().count();
        assertTrue(distinct <= 12 && distinct >= 6, "cada celda tiene un único valor: " + distinct);
    }

    @Test
    void cellsUseTheNearestCentre() {
        RegionSnapshot.Builder builder = TextureScenes.empty(0, 0, 0, 40, 1, 1);
        for (int x = 0; x < 40; x++) {
            builder.set(x, 0, 0, TextureScenes.STONE, true);
        }
        TextureContext context = new TextureContext(builder.build(), TextureContext.Filter.ALL, SETTINGS, NEVER);
        double[] values = new TextureFields.Cells(2, 0.0, 0.0, 9L, 20).evaluate(context);
        // Con dos centros bien separados en una fila, la fila se parte en dos tramos seguidos.
        int changes = 0;
        for (int x = 1; x < 40; x++) {
            if (values[x] != values[x - 1]) {
                changes++;
            }
        }
        assertEquals(1, changes);
    }

    @Test
    void sunlightFacesTowardsTheLight() {
        TextureContext context = wall();
        // El sol alumbra hacia z positiva: la cara delantera lo mira de frente.
        double[] values = TextureFields.Light.sun(new double[]{0, 0, 1}, 1, 0, 180, 0.0, 1.0).evaluate(context);
        double front = value(context, values, 2, 8, 0);
        double top = value(context, values, 2, 15, 1);
        assertTrue(front < 0.1, "de frente a la luz, el principio de la paleta: " + front);
        assertEquals(0.5, top, 0.15, "la cara de arriba está a 90°");
    }

    @Test
    void sunlightShadowsDarkenOccludedFaces() {
        TextureContext context = wall();
        // Luz rasante desde la derecha: el saliente hace sombra sobre la pared a su izquierda.
        double[] direction = Vectors.direction("-1;0;0.35");
        double[] lit = TextureFields.Light.sun(direction, 1, 0, 180, 0.0, 1.0).evaluate(context);
        double[] shaded = TextureFields.Light.sun(direction, 1, 0, 180, 1.0, 0.0).evaluate(context);
        assertTrue(value(context, shaded, 2, 8, 0) > value(context, lit, 2, 8, 0) + 0.2,
                "a la sombra del saliente la pared se oscurece");
        assertEquals(value(context, lit, 20, 8, 0), value(context, shaded, 20, 8, 0), 1.0E-9,
                "lejos del saliente no hay sombra");
    }

    @Test
    void pointLightFallsOffWithDistance() {
        TextureContext context = wall();
        double[] values = TextureFields.Light.point(new double[]{2.5, 8.5, -3}, 6.0, true, 1, 0, 90, 0.0, 1.0)
                .evaluate(context);
        assertTrue(value(context, values, 2, 8, 0) < value(context, values, 22, 8, 0),
                "lo cercano a la luz va más al principio de la paleta");
        double[] flat = TextureFields.Light.point(new double[]{2.5, 8.5, -3}, 6.0, false, 1, 0, 90, 0.0, 1.0)
                .evaluate(context);
        assertTrue(value(context, flat, 22, 8, 0) < value(context, values, 22, 8, 0), "-f quita la atenuación");
    }

    @Test
    void blockLightReadsTheAirAroundEachFace() {
        RegionSnapshot.Builder builder = TextureScenes.empty(0, 0, 0, 5, 3, 1);
        for (int x = 0; x < 5; x++) {
            builder.set(x, 0, 0, x == 4 ? TextureScenes.TORCH : TextureScenes.STONE, true);
            builder.set(x, 1, 0, TextureScenes.AIR, false);
            builder.light(x, 1, 0, 14 - x * 3, 15);
            builder.set(x, 2, 0, TextureScenes.AIR, false);
        }
        TextureContext context = new TextureContext(builder.build(), TextureContext.Filter.ALL, SETTINGS, NEVER);
        double[] values = new TextureFields.BlockLight(true, false, false).evaluate(context);
        assertEquals(14 / 15.0, values[context.index(0, 0, 0)], 1.0E-9);
        assertEquals(5 / 15.0, values[context.index(3, 0, 0)], 1.0E-9);
        double[] withSky = new TextureFields.BlockLight(true, true, false).evaluate(context);
        assertEquals(1.0, withSky[context.index(3, 0, 0)], 1.0E-9);
        double[] keep = new TextureFields.BlockLight(true, false, true).evaluate(context);
        assertTrue(Double.isNaN(keep[context.index(4, 0, 0)]), "-l deja las fuentes de luz");
        assertTrue(new TextureFields.BlockLight(false, false, false).needsLight());
    }

    @Test
    void flowDrawsLinesAndFillsTheRest() {
        TextureContext context = wall();
        NoiseSpec noise = NoiseSpec.parse("Perlin(f:0.05)");
        TextureFields.Flow flow = new TextureFields.Flow(0.6, 24, 0.5, 1.0, noise, 0.3, null, false, 11L);
        double[] values = flow.evaluate(context);
        int visited = 0;
        int surface = 0;
        for (int index = 0; index < values.length; index++) {
            if (context.surface(index)) {
                surface++;
                if (!Double.isNaN(values[index])) {
                    visited++;
                }
            } else {
                assertTrue(Double.isNaN(values[index]));
            }
        }
        assertTrue(visited > surface / 10 && visited < surface, "las líneas cubren parte de la superficie: "
                + visited + " de " + surface);
        double[] filled = flow.withFill().evaluate(context);
        for (int index = 0; index < filled.length; index++) {
            assertEquals(context.surface(index), !Double.isNaN(filled[index]));
        }
        assertArrayEquals(values, flow.evaluate(context), "misma semilla, mismas líneas");
    }

    @Test
    void flowGravityPullsLinesDown() {
        TextureContext context = wall();
        NoiseSpec noise = NoiseSpec.parse("Perlin(f:0.05)");
        double[] free = new TextureFields.Flow(0.5, 40, 0.5, 1.0, noise, 0.0, null, false, 2L).evaluate(context);
        double[] down = new TextureFields.Flow(0.5, 40, 0.5, 1.0, noise, 0.0, new double[]{0, -3, 0}, false, 2L)
                .evaluate(context);
        assertTrue(endsLow(context, down) > endsLow(context, free),
                "con gravedad los finales de línea quedan más abajo");
    }

    private static double endsLow(TextureContext context, double[] values) {
        double weighted = 0.0;
        double total = 0.0;
        for (int index = 0; index < values.length; index++) {
            if (!Double.isNaN(values[index])) {
                weighted += values[index] * (15 - context.y(index));
                total += values[index];
            }
        }
        return weighted / total;
    }

    @Test
    void combinatorsFollowTheirFormulas() {
        assertEquals(0.9, TextureFields.Operation.ADD.apply(0.4, 0.5, 0), 1.0E-9);
        assertEquals(1.0, TextureFields.Operation.ADD.apply(0.7, 0.5, 0), 1.0E-9);
        assertEquals(0.0, TextureFields.Operation.SUBTRACT.apply(0.2, 0.5, 0), 1.0E-9);
        assertEquals(0.2, TextureFields.Operation.MULTIPLY.apply(0.4, 0.5, 0), 1.0E-9);
        assertEquals(0.8, TextureFields.Operation.DIVIDE.apply(0.4, 0.5, 0), 1.0E-9);
        assertEquals(0.475, TextureFields.Operation.WEIGHTED_AVERAGE.apply(0.4, 0.7, 0.25), 1.0E-9);
        assertEquals(0.4, TextureFields.Operation.DARKEN.apply(0.4, 0.7, 0), 1.0E-9);
        assertEquals(0.7, TextureFields.Operation.LIGHTEN.apply(0.4, 0.7, 0), 1.0E-9);
        assertEquals(0.3, TextureFields.Operation.DIFFERENCE.apply(0.4, 0.7, 0), 1.0E-9);
        assertEquals(0.82, TextureFields.Operation.SCREEN.apply(0.4, 0.7, 0), 1.0E-9);
    }

    @Test
    void adjustMovesTowardsTheStartAndStretches() {
        assertEquals(0.3, TextureField.adjust(0.5, 0.2, 0.0), 1.0E-9);
        assertEquals(0.8, TextureField.adjust(0.65, 0.0, 1.0), 1.0E-9);
        assertTrue(Double.isNaN(TextureField.adjust(Double.NaN, 0.1, 0.1)));
        double[] stretched = TextureField.stretch(new double[]{0.4, 0.45, 0.5, Double.NaN});
        assertEquals(0.0, stretched[0], 1.0E-9);
        assertEquals(1.0, stretched[2], 1.0E-9);
        assertTrue(Double.isNaN(stretched[3]));
    }

    @Test
    void maskAndCancellationAreHonoured() {
        TextureContext masked = new TextureContext(TextureScenes.wall(), (x, y, z) -> y < 4, SETTINGS, NEVER);
        for (int index = 0; index < masked.size(); index++) {
            assertTrue(masked.y(index) < 4);
        }
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> new TextureContext(TextureScenes.wall(), TextureContext.Filter.ALL, SETTINGS, () -> true));
    }

    @Test
    void smoothingAveragesNeighbours() {
        TextureContext context = wall();
        double[] values = context.empty();
        for (int index = 0; index < values.length; index++) {
            if (context.surface(index)) {
                values[index] = context.x(index) < 12 ? 0.0 : 1.0;
            }
        }
        double[] smooth = context.smoothSurface(values, 1.0);
        double left = value(context, smooth, 11, 1, 0);
        double right = value(context, smooth, 12, 1, 0);
        assertTrue(left > 0.0 && right < 1.0, "la frontera se suaviza");
        assertNotEquals(left, right);
        assertEquals(0.0, value(context, smooth, 1, 8, 0), 1.0E-9, "lejos de la frontera no cambia");
    }
}
