package com.komainu.moxia.shape.tube;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code ezsp 2d Strands}: usa el perfil de cuerda y se llevaba su giro de 90° por diámetro, aunque la documentación
 * dice que solo la cuerda gira. Como el círculo de círculos, no gira salvo que se pida con {@code -t}.
 */
class EzSplineTwistTest {
    private static double twist(String input) {
        return EzSpline.parse(input, null).parameters().options().get("twist", Double.NaN);
    }

    @Test
    void onlyTheRopeTwistsByDefault() {
        assertEquals(0.0, twist("2d Strands stone 10"));
        assertEquals(0.0, twist("2d Sr(C:4) stone 10"));
        assertEquals(0.0, twist("2d CC(Count:5) stone 10"));
        assertEquals(45.0, twist("2d Strands stone 10 -t 45"), "con -t gira lo que se pida");
    }
}
