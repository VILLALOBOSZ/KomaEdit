package com.komainu.moxia.terrain;

import java.util.Locale;

/**
 * El pincel de relieve: aplica un sello sobre el mapa de alturas, escalado al radio, girado y con una intensidad.
 *
 * <ul>
 *     <li>{@link Mode#RAISE}: suma la forma del sello (lo que está por debajo del gris neutro hunde);</li>
 *     <li>{@link Mode#LOWER}: la resta;</li>
 *     <li>{@link Mode#FLATTEN}: lleva el terreno hacia la altura del clic con la fuerza que marca el sello.</li>
 * </ul>
 *
 * <p>El sello ocupa el cuadrado de lado {@code 2·radio} centrado en el clic; girado, sus esquinas llegan hasta
 * {@code radio·√2}.</p>
 */
public final class StampBrush {
    private StampBrush() {
    }

    public enum Mode {
        RAISE("raise", "Elevar"),
        LOWER("lower", "Bajar"),
        FLATTEN("flatten", "Aplanar");

        private final String id;
        private final String displayName;

        Mode(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        /** El del otro clic: elevar y bajar se invierten; aplanar sigue igual. */
        public Mode opposite() {
            return switch (this) {
                case RAISE -> LOWER;
                case LOWER -> RAISE;
                case FLATTEN -> FLATTEN;
            };
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Mode byId(String input) {
            return switch (input.toLowerCase(Locale.ROOT)) {
                case "raise", "elevar", "subir", "up" -> RAISE;
                case "lower", "bajar", "hundir", "down" -> LOWER;
                case "flatten", "aplanar", "flat", "llano" -> FLATTEN;
                default -> throw new IllegalArgumentException("Modo de relieve desconocido: " + input
                        + " (elevar, bajar o aplanar)");
            };
        }
    }

    /**
     * @param radius    medio lado del sello en bloques
     * @param amplitude bloques que sube o baja el blanco puro
     * @param strength  intensidad de 0 a 1
     * @param angle     giro en radianes (0 = la parte de arriba de la imagen hacia el norte)
     * @param baseY     altura de referencia para aplanar
     */
    public record Settings(HeightStamp stamp, Mode mode, double radius, double amplitude, double strength,
                           double angle, double baseY) {
        public Settings {
            if (radius < 1.0 || radius > 256.0) {
                throw new IllegalArgumentException("El radio debe estar entre 1 y 256");
            }
            if (amplitude < 0.0 || amplitude > 256.0) {
                throw new IllegalArgumentException("La altura debe estar entre 0 y 256");
            }
            if (strength <= 0.0 || strength > 1.0) {
                throw new IllegalArgumentException("La intensidad debe estar entre 1 y 100 %");
            }
        }

        /** Hasta dónde llega el sello girado. */
        public int reach() {
            return (int) Math.ceil(radius * Math.sqrt(2.0)) + 1;
        }
    }

    /** Cambia el mapa alrededor de {@code (centerX, centerZ)}, el centro del bloque del clic. */
    public static void apply(HeightField field, double centerX, double centerZ, Settings settings,
                             Runnable checkCancelled) {
        int reach = settings.reach();
        int lowX = (int) Math.floor(centerX - reach);
        int highX = (int) Math.ceil(centerX + reach);
        int lowZ = (int) Math.floor(centerZ - reach);
        int highZ = (int) Math.ceil(centerZ + reach);
        for (int z = lowZ; z <= highZ; z++) {
            checkCancelled.run();
            for (int x = lowX; x <= highX; x++) {
                int index = field.index(x, z);
                if (index < 0 || !field.editableAt(index)) {
                    continue;
                }
                double dx = x + 0.5 - centerX;
                double dz = z + 0.5 - centerZ;
                double relief = settings.stamp.relief(dx, dz, settings.radius, settings.angle);
                if (relief == 0.0) {
                    continue;
                }
                double current = field.at(index);
                double next = switch (settings.mode) {
                    case RAISE -> current + relief * settings.amplitude * settings.strength;
                    case LOWER -> current - relief * settings.amplitude * settings.strength;
                    case FLATTEN -> current + (settings.baseY - current) * Math.clamp(Math.abs(relief), 0.0, 1.0)
                            * settings.strength;
                };
                field.setAt(index, next);
            }
        }
    }
}
