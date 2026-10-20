package com.komainu.moxia.terrain;

import java.util.Locale;

/**
 * Rampa de un punto a otro sobre el mapa de alturas: rellena por debajo y recorta por encima, con una franja a los
 * lados que la funde con el terreno.
 *
 * <ul>
 *     <li>{@link Section#FLAT}: sección plana; a los lados el terreno se mezcla con la rampa en la franja de
 *     transición con una curva suave.</li>
 *     <li>{@link Section#CONE}: sección en cono, como un terraplén: a los lados el terreno queda entre dos taludes
 *     que bajan (relleno) o suben (desmonte) con la pendiente indicada, así nunca hay paredes.</li>
 * </ul>
 *
 * <p>Los extremos se redondean. La altura a lo largo puede ser lineal o suave (empieza y acaba más tendida). Una
 * rampa que sube más de un bloque por bloque daría escalones y se rechaza.</p>
 */
public final class RampPlanner {
    /** Pendiente máxima a lo largo: un bloque por bloque. */
    public static final double MAX_GRADE = 1.0;

    private RampPlanner() {
    }

    public enum Section {
        FLAT("flat", "plana"),
        CONE("cone", "en cono");

        private final String id;
        private final String displayName;

        Section(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public static Section byId(String input) {
            return switch (input.toLowerCase(Locale.ROOT)) {
                case "flat", "plano", "plana", "llano" -> FLAT;
                case "cone", "cono", "talud", "terraplen", "terraplén" -> CONE;
                default -> throw new IllegalArgumentException("Sección de rampa desconocida: " + input
                        + " (plano o cono)");
            };
        }
    }

    /**
     * @param halfWidth  medio ancho de la calzada, en bloques
     * @param transition ancho de la franja que la funde con el terreno (plana)
     * @param talus      bloques que sube o baja el talud por cada bloque hacia fuera (en cono)
     * @param smooth     perfil suave a lo largo en lugar de lineal
     */
    public record Settings(Section section, double halfWidth, double transition, double talus, boolean smooth) {
        public Settings {
            if (halfWidth < 0.5 || halfWidth > 64.0) {
                throw new IllegalArgumentException("El ancho debe estar entre 1 y 128");
            }
            if (transition < 0.0 || transition > 64.0) {
                throw new IllegalArgumentException("La transición debe estar entre 0 y 64");
            }
            if (talus < 0.1 || talus > 8.0) {
                throw new IllegalArgumentException("El talud debe estar entre 0.1 y 8 bloques por bloque");
            }
        }

        /** Hasta dónde, contado desde el eje, puede cambiar el terreno. */
        public double reach(double heightRange) {
            if (section == Section.FLAT) {
                return halfWidth + transition + 1.0;
            }
            return halfWidth + Math.min(64.0, heightRange / talus) + 1.0;
        }
    }

    /** Pendiente máxima a lo largo con estos extremos (bloques por bloque). */
    public static double grade(double[] start, double[] end, boolean smooth) {
        double length = Math.hypot(end[0] - start[0], end[2] - start[2]);
        double rise = Math.abs(end[1] - start[1]);
        if (length < 1e-6) {
            return rise == 0.0 ? 0.0 : Double.POSITIVE_INFINITY;
        }
        return rise / length * (smooth ? 1.5 : 1.0);
    }

    /**
     * Modifica el mapa. {@code start} y {@code end} son {x, y, z}: el centro de la cara de arriba del bloque de la
     * calzada en cada extremo ({@code y} es la altura de ese bloque).
     */
    public static void apply(HeightField field, double[] start, double[] end, Settings settings,
                             Runnable checkCancelled) {
        double grade = grade(start, end, settings.smooth);
        if (grade > MAX_GRADE + 1e-9) {
            double length = Math.hypot(end[0] - start[0], end[2] - start[2]);
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "La rampa sube %.0f bloques en %.0f: más de uno por bloque daría escalones; aleja los puntos"
                            + "%s", Math.abs(end[1] - start[1]), length,
                    settings.smooth ? " o usa el perfil lineal" : ""));
        }
        double axisX = end[0] - start[0];
        double axisZ = end[2] - start[2];
        double lengthSquared = axisX * axisX + axisZ * axisZ;
        // Con un talud de hasta un bloque por bloque, el talud y la subida juntos no pasan de uno: así, al
        // redondear, dos columnas vecinas nunca se separan más de un bloque.
        double talus = settings.talus <= 1.0
                ? Math.min(settings.talus, Math.sqrt(Math.max(0.0, 1.0 - grade * grade)))
                : settings.talus;
        HeightField original = field.copy();
        for (int index = 0; index < field.columns(); index++) {
            if ((index & 1_023) == 0) {
                checkCancelled.run();
            }
            if (!field.editableAt(index)) {
                continue;
            }
            double terrain = original.at(index);
            double px = field.xOf(index) + 0.5;
            double pz = field.zOf(index) + 0.5;
            double t = lengthSquared == 0.0 ? 0.0
                    : Math.clamp(((px - start[0]) * axisX + (pz - start[2]) * axisZ) / lengthSquared, 0.0, 1.0);
            double nearestX = start[0] + axisX * t;
            double nearestZ = start[2] + axisZ * t;
            double distance = Math.hypot(px - nearestX, pz - nearestZ);
            double profile = settings.smooth ? t * t * (3.0 - 2.0 * t) : t;
            double axis = start[1] + (end[1] - start[1]) * profile;
            double outside = distance - settings.halfWidth;
            double height;
            if (outside <= 0.0) {
                height = axis;
            } else if (settings.section == Section.FLAT) {
                if (outside >= settings.transition) {
                    continue;
                }
                double blend = 1.0 - outside / settings.transition;
                blend = blend * blend * (3.0 - 2.0 * blend);
                height = terrain + (axis - terrain) * blend;
            } else {
                double band = outside * talus;
                height = Math.clamp(terrain, axis - band, axis + band);
            }
            field.setAt(index, height);
        }
    }
}
