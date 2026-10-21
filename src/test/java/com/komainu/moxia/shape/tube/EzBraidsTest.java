package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.ShapeParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code //ezspline 3d Braids}: la trenza de la gráfica Desmos pública de ezEdits, tres hebras de radio
 * {@code T/(1+T)} centradas en {@code (−sen w/(1+T), −sen 2w/(2(1+T)))} con {@code w = z·Z + 2πk/3}.
 */
class EzBraidsTest {
    private static EzShapes.Braids braids(String input) {
        ShapeParameters parameters = EzSpline.parse(input, null).parameters();
        return new EzShapes.Braids(parameters);
    }

    /** La hebra en la que cae el punto (0, 1 o 2), o -1 si queda fuera. */
    private static int strand(EzShapes.Braids braids, double x, double y, double z) {
        double value = braids.sample(x, y, z, 40.0, 1.0);
        return value < 0.0 ? -1 : (int) Math.floor(value * 3.0);
    }

    @Test
    void threeStrandsWhereTheDesmosGraphPutsThem() {
        EzShapes.Braids braids = braids("3d Braids clay 7");
        double third = Math.PI * 2.0 / 3.0;
        double scale = 1.5;
        for (int index = 0; index < 3; index++) {
            double phase = third * index;
            double x = -Math.sin(phase) / scale;
            double y = -Math.sin(2.0 * phase) / (2.0 * scale);
            assertEquals(index, strand(braids, x, y, 0.0), "centro de la hebra " + index);
            // Radio 0,5/1,5 = 1/3: dentro justo antes del borde y fuera justo después.
            assertEquals(index, strand(braids, x + 0.32, y, 0.0), "borde de la hebra " + index);
        }
        assertEquals(-1, strand(braids, 0.0, 0.9, 0.0), "entre hebras no hay nada");
        assertTrue(braids.sample(0.95, 0.95, 0.0, 40.0, 1.0) < -1.0, "fuera, con su holgura");
    }

    @Test
    void repeatsEveryTurnAndTheGapStretchesIt() {
        EzShapes.Braids plain = braids("3d Braids clay 7");
        EzShapes.Braids stretched = braids("3d Br(G:1) clay 7");
        for (double z = 0.0; z < 6.0; z += 0.37) {
            for (double x = -1.0; x <= 1.0; x += 0.25) {
                for (double y = -1.0; y <= 1.0; y += 0.25) {
                    assertEquals(strand(plain, x, y, z), strand(plain, x, y, z + Math.PI * 2.0));
                    assertEquals(strand(plain, x, y, z), strand(stretched, x, y, z * 2.0),
                            "con G:1 los cruces quedan al doble de distancia");
                }
            }
        }
    }

    @Test
    void thicknessIsRelativeToTheTube() {
        // T:0.2 → radio 0,2/1,2 = 1/6 del tubo, en el centro de la hebra 0 (que en z = 0 está en el eje).
        EzShapes.Braids thin = braids("3d Braids(Thickness:0.2) clay 7");
        assertEquals(0, strand(thin, 0.15, 0.0, 0.0));
        assertEquals(-1, strand(thin, 0.19, 0.0, 0.0));
    }
}
