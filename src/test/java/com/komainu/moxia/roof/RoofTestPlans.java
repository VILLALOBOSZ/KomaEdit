package com.komainu.moxia.roof;

/** Plantas de prueba: rectángulo, L, T y U con alas de 7 bloques de ancho. */
final class RoofTestPlans {
    private RoofTestPlans() {
    }

    static Footprint plan(String name, int baseY) {
        return switch (name) {
            // Ala horizontal de 17×7 y ala que baja de 7×9 en un extremo.
            case "L" -> Footprint.of(0, 0, 16, 15, baseY, (x, z) -> z < 7 || x >= 10);
            // Barra de 19×7 y pie centrado de 7×10.
            case "T" -> Footprint.of(0, 0, 18, 16, baseY, (x, z) -> z < 7 || x >= 6 && x <= 12);
            // Base de 19×7 y dos brazos de 7×9.
            case "U" -> Footprint.of(0, 0, 18, 15, baseY, (x, z) -> z >= 9 || x < 7 || x >= 12);
            case "rect" -> Footprint.rectangle(0, 0, 14, 8, baseY);
            default -> throw new IllegalArgumentException(name);
        };
    }
}
