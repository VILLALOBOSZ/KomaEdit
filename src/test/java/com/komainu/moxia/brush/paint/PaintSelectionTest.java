package com.komainu.moxia.brush.paint;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pintar la selección ({@code /komaedit pintar}): un toque que la cubre entera con la fuerza del pincel, sin dejarse
 * ninguna columna y sin pintar nada fuera, también cuando el terreno sube y baja dentro de ella.
 */
class PaintSelectionTest {
    private static final List<String> PALETTE = PaintPainterTest.PALETTE;

    /** La caja de -9..6 × 0..40 × -4..11 sobre la colina, con su escarpe. */
    private static final int LOW_X = -9;
    private static final int HIGH_X = 6;
    private static final int LOW_Z = -4;
    private static final int HIGH_Z = 11;

    private static boolean inside(int x, int z) {
        return x >= LOW_X && x <= HIGH_X && z >= LOW_Z && z <= HIGH_Z;
    }

    /** El mundo de prueba visto desde la selección: solo se puede pintar dentro de la caja. */
    private record Selected(FakePaintWorld world) implements PaintWorld {
        @Override
        public Kind kind(int x, int y, int z) {
            return world.kind(x, y, z);
        }

        @Override
        public int paletteIndex(int x, int y, int z) {
            return world.paletteIndex(x, y, z);
        }

        @Override
        public boolean allowed(int x, int y, int z) {
            return inside(x, z) && y >= 0 && y <= 40 && world.allowed(x, y, z);
        }

        @Override
        public int minimumY() {
            return world.minimumY();
        }

        @Override
        public int maximumY() {
            return world.maximumY();
        }
    }

    private static PaintPainter.Settings settings(PaintSelection.Cover cover, PaintGradient gradient,
                                                  PaintFootprint footprint) {
        // Por altura con la franja por debajo del terreno: a cada bloque le toca el último tono, así se ve si pintó.
        return new PaintPainter.Settings(PALETTE.size(), gradient, footprint, 1.0, cover.hardness(), 0.0, 1, true,
                true, cover.vertical(), 0.0, 5.0, 30.0, 65.0, null, null, false);
    }

    @Test
    void oneDabCoversEveryColumnOfTheSelectionAndNothingElse() {
        FakePaintWorld world = FakePaintWorld.hill(20, PALETTE);
        PaintSelection.Cover cover = PaintSelection.Cover.of(LOW_X, 0, LOW_Z, HIGH_X, 40, HIGH_Z, false,
                PaintFootprint.square().extent(), 0.3, 1);
        assertEquals(1.0, cover.hardness(), "sin el modo Huella, el toque no difumina su borde");
        Map<Long, Integer> painted = new java.util.LinkedHashMap<>();
        PaintPainter.paint(new Selected(world), List.of(cover.dab()),
                settings(cover, PaintGradient.HEIGHT, PaintFootprint.square()), 3L,
                (x, y, z, index) -> painted.put(PaintPainter.pack(x, y, z), index), () -> { });
        for (int z = -20; z <= 20; z++) {
            for (int x = -20; x <= 20; x++) {
                long top = PaintPainter.pack(x, world.top(x, z), z);
                if (inside(x, z)) {
                    assertEquals(PALETTE.size() - 1, painted.get(top), "columna " + x + "," + z);
                } else {
                    assertTrue(!painted.containsKey(top), "fuera de la selección: " + x + "," + z);
                }
            }
        }
        // Lo que se lee cubre la caja con su margen.
        assertTrue(cover.horizontal() >= Math.max(HIGH_X - LOW_X + 1, HIGH_Z - LOW_Z + 1) / 2 + 3);
        assertTrue(cover.capture() >= cover.vertical());
    }

    @Test
    void theFootprintModeGoesFromTheCentreOfTheSelectionToItsEdge() {
        FakePaintWorld world = FakePaintWorld.flat(20, PALETTE);
        PaintSelection.Cover cover = PaintSelection.Cover.of(LOW_X, 0, LOW_Z, HIGH_X, 40, HIGH_Z, true,
                PaintFootprint.circle().extent(), 0.3, 1);
        assertEquals(0.3, cover.hardness(), 1e-9, "con Huella, la dureza del pincel");
        PaintPainterTest.paint(world, List.of(cover.dab()), new PaintPainter.Settings(PALETTE.size(),
                PaintGradient.INTENSITY, PaintFootprint.circle(), 1.0, cover.hardness(), 0.0, 1, true, true,
                cover.vertical(), Double.NaN, Double.NaN, 30.0, 65.0, null, null, false));
        int centre = world.paletteIndex(-2, world.top(-2, 4), 4);
        int corner = world.paletteIndex(LOW_X, world.top(LOW_X, LOW_Z), LOW_Z);
        assertTrue(centre < corner, "el centro va al principio de la paleta y el borde al final: " + centre + " "
                + corner);
    }
}
