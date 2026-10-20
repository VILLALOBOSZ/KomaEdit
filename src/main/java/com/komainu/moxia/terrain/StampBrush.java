package com.komainu.moxia.terrain;

import com.komainu.moxia.terrain.stamp.StampPlacement;

import java.util.Locale;

/**
 * El pincel de relieve: aplica un sello sobre el mapa de alturas, colocado con su {@link StampPlacement} (radio, giro,
 * escala, espejo y ruido fino) y con una intensidad. Modos de mezcla:
 *
 * <ul>
 *     <li>{@link Mode#RAISE}: suma la forma del sello (lo que está por debajo del gris neutro hunde);</li>
 *     <li>{@link Mode#LOWER}: la resta;</li>
 *     <li>{@link Mode#MAX}: sube el terreno hasta la forma del sello puesta a la altura del clic, sin hundir nada que
 *     ya esté más alto (dos montañas que se pisan no se apilan);</li>
 *     <li>{@link Mode#FLATTEN}: deja el terreno a la altura del sello (puesto a la altura del clic), suba o baje;</li>
 *     <li>{@link Mode#SMOOTH}: suaviza lo que hay bajo el sello, con su forma como fuerza: funde bordes y
 *     costuras.</li>
 * </ul>
 *
 * <p>El sello ocupa el cuadrado de lado {@code 2·radio·escala} centrado en el clic; girado, sus esquinas llegan hasta
 * {@code radio·escala·√2}.</p>
 */
public final class StampBrush {
    /** Radio del suavizado por cada bloque de radio del sello, y su máximo. */
    private static final double SMOOTH_PER_BLOCK = 1.0 / 6.0;
    private static final int MAX_SMOOTH = 6;

    private StampBrush() {
    }

    public enum Mode {
        RAISE("raise", "Sumar"),
        LOWER("lower", "Restar"),
        MAX("max", "Máximo"),
        FLATTEN("flatten", "Aplanar"),
        SMOOTH("smooth", "Suavizar");

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

        /** El del otro clic: sumar y restar se invierten, máximo resta; aplanar y suavizar siguen igual. */
        public Mode opposite() {
            return switch (this) {
                case RAISE, MAX -> LOWER;
                case LOWER -> RAISE;
                case FLATTEN -> FLATTEN;
                case SMOOTH -> SMOOTH;
            };
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Mode byId(String input) {
            return switch (input.toLowerCase(Locale.ROOT).replace("á", "a")) {
                case "raise", "elevar", "subir", "sumar", "add", "up" -> RAISE;
                case "lower", "bajar", "hundir", "restar", "subtract", "down" -> LOWER;
                case "max", "maximo", "maximum", "tope" -> MAX;
                case "flatten", "aplanar", "flat", "llano", "ajustar" -> FLATTEN;
                case "smooth", "suavizar", "alisar", "blend", "fundir" -> SMOOTH;
                default -> throw new IllegalArgumentException("Modo de relieve desconocido: " + input
                        + " (sumar, restar, maximo, aplanar o suavizar)");
            };
        }
    }

    /**
     * @param placement dónde y cómo cae el sello (radio, giro, escala, espejo y ruido fino)
     * @param amplitude bloques que sube o baja el blanco puro
     * @param strength  intensidad de 0 a 1
     * @param baseY     altura del clic: la base del sello en máximo y aplanar
     */
    public record Settings(HeightStamp stamp, Mode mode, StampPlacement placement, double amplitude, double strength,
                           double baseY) {
        public Settings {
            if (placement.radius() < 1.0 || placement.extent() > 384.0) {
                throw new IllegalArgumentException("El radio debe estar entre 1 y 256");
            }
            if (amplitude < 0.0 || amplitude > 256.0) {
                throw new IllegalArgumentException("La altura debe estar entre 0 y 256");
            }
            if (strength <= 0.0 || strength > 1.0) {
                throw new IllegalArgumentException("La intensidad debe estar entre 1 y 100 %");
            }
        }

        /** Sin variación: el sello tal cual con un giro. */
        public Settings(HeightStamp stamp, Mode mode, double radius, double amplitude, double strength, double angle,
                        double baseY) {
            this(stamp, mode, StampPlacement.of(checked(radius), angle), amplitude, strength, baseY);
        }

        private static double checked(double radius) {
            if (radius < 1.0 || radius > 256.0) {
                throw new IllegalArgumentException("El radio debe estar entre 1 y 256");
            }
            return radius;
        }

        public double radius() {
            return placement.radius();
        }

        public double angle() {
            return placement.angle();
        }

        /** Radio del suavizado (0 si el modo no suaviza). */
        public int smoothing() {
            return mode == Mode.SMOOTH
                    ? (int) Math.clamp(Math.round(placement.extent() * SMOOTH_PER_BLOCK), 1L, MAX_SMOOTH) : 0;
        }

        /** Hasta dónde llega el sello girado, más lo que necesita mirar el suavizado alrededor. */
        public int reach() {
            return placement.reach() + smoothing();
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
        StampPlacement placement = settings.placement;
        double[] smoothed = settings.mode == Mode.SMOOTH
                ? blur(field, lowX, lowZ, highX, highZ, settings.smoothing(), checkCancelled) : null;
        int width = highX - lowX + 1;
        for (int z = lowZ; z <= highZ; z++) {
            checkCancelled.run();
            for (int x = lowX; x <= highX; x++) {
                int index = field.index(x, z);
                if (index < 0 || !field.editableAt(index)) {
                    continue;
                }
                double dx = x + 0.5 - centerX;
                double dz = z + 0.5 - centerZ;
                double relief = placement.relief(settings.stamp, dx, dz);
                if (relief == 0.0) {
                    continue;
                }
                double current = field.at(index);
                double target = settings.baseY + relief * settings.amplitude;
                double next = switch (settings.mode) {
                    case RAISE -> current + relief * settings.amplitude * settings.strength;
                    case LOWER -> current - relief * settings.amplitude * settings.strength;
                    // Solo donde el sello asoma por encima; en su falda baja la fuerza para que no quede escalón.
                    case MAX -> target <= current ? current : current + (target - current) * settings.strength
                            * smoothstep(0.0, 0.12, relief);
                    case FLATTEN -> current + (target - current) * settings.strength * placement.footprint(dx, dz);
                    case SMOOTH -> {
                        double average = smoothed[(z - lowZ) * width + (x - lowX)];
                        yield Double.isNaN(average) ? current
                                : current + (average - current) * settings.strength * Math.min(1.0,
                                Math.abs(relief) * 1.5);
                    }
                };
                field.setAt(index, next);
            }
        }
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Media de cada columna con sus vecinas a {@code radius} bloques (dos cajas seguidas, casi una campana), sin contar
     * las columnas sin suelo. NaN donde no hay ninguna.
     */
    private static double[] blur(HeightField field, int lowX, int lowZ, int highX, int highZ, int radius,
                                 Runnable checkCancelled) {
        int width = highX - lowX + 1;
        int depth = highZ - lowZ + 1;
        double[] values = new double[width * depth];
        double[] weights = new double[width * depth];
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                double height = field.get(lowX + x, lowZ + z);
                boolean known = !Double.isNaN(height);
                values[z * width + x] = known ? height : 0.0;
                weights[z * width + x] = known ? 1.0 : 0.0;
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            checkCancelled.run();
            values = box(values, width, depth, radius, true);
            weights = box(weights, width, depth, radius, true);
            values = box(values, width, depth, radius, false);
            weights = box(weights, width, depth, radius, false);
        }
        double[] result = new double[width * depth];
        for (int index = 0; index < result.length; index++) {
            result[index] = weights[index] <= 1e-9 ? Double.NaN : values[index] / weights[index];
        }
        return result;
    }

    private static double[] box(double[] source, int width, int depth, int radius, boolean alongX) {
        double[] result = new double[source.length];
        int lines = alongX ? depth : width;
        int length = alongX ? width : depth;
        for (int line = 0; line < lines; line++) {
            for (int position = 0; position < length; position++) {
                double sum = 0.0;
                for (int offset = -radius; offset <= radius; offset++) {
                    int at = Math.clamp(position + offset, 0, length - 1);
                    sum += alongX ? source[line * width + at] : source[at * width + line];
                }
                result[alongX ? line * width + position : position * width + line] = sum / (2 * radius + 1);
            }
        }
        return result;
    }
}
