package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;
import com.komainu.moxia.shape.smooth.PieceFit;
import com.komainu.moxia.shape.smooth.SmoothMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parámetros del espacio de tubo resueltos a partir de {@link ShapeParameters}.
 *
 * <p>Las opciones numéricas viven en el mapa de opciones de la receta, así que se pueden guardar,
 * sincronizar y exportar sin cambiar el formato de recetas.</p>
 */
public record TubeSettings(
        PathShape shape,
        double radius,
        TubeProfile profile,
        int sides,
        double depth,
        double aspect,
        double exponent,
        boolean hollow,
        int wall,
        boolean filled,
        EndMode endMode,
        NormalMode normalMode,
        Quality quality,
        double tension,
        double bias,
        double continuity,
        double twistPerDiameter,
        double startRollDegrees,
        double endRollDegrees,
        boolean rollRange,
        double stretch,
        int strands,
        double strandThickness,
        double frequency,
        long seed,
        RadiusProgression radii,
        SmoothMode smoothMode,
        double coverage
) {
    public static TubeSettings from(ShapeParameters parameters) {
        ShapeOptions options = parameters.options();
        PathShape shape = parameters.shape();
        TubeProfile profile = TubeProfile.byIndex((int) Math.round(options.get("profile", 0.0)));
        int sides = options.has("sides")
                ? (int) Math.round(Math.clamp(options.get("sides", 6.0), 2.0, 32.0))
                : parameters.spacing();
        double depth = shape == PathShape.NOISE_TUBE
                ? Math.clamp(options.get("depthmultiplier", 0.15 + parameters.thickness() * 0.15), 0.05, 1.0)
                : Math.clamp(options.get("depthmultiplier", profile.defaultDepth()), 0.0, 0.95);
        double twist = options.get("twist", profile == TubeProfile.ROPE && shape == PathShape.SWEEP ? 90.0 : 0.0);
        double startRoll = parameters.rollDegrees() + options.get("angle", 0.0);
        boolean rollRange = options.has("rollend");
        double endRoll = options.get("rollend", startRoll);
        int strands = (int) Math.round(Math.clamp(options.get("strands", 3.0), 2.0, 8.0));
        double strandThickness = options.has("thickness")
                ? Math.clamp(options.get("thickness", 0.3), 0.08, 0.6)
                : Math.clamp(0.16 + parameters.thickness() * 0.06, 0.08, 0.6);
        double frequency = options.has("frequency")
                ? Math.clamp(options.get("frequency", 1.0), 0.05, 8.0)
                : 0.35 + (parameters.spacing() - 2) * 0.18;
        return new TubeSettings(
                shape,
                parameters.radius(),
                profile,
                sides,
                depth,
                Math.clamp(options.get("aspect", 0.5), 0.1, 1.0),
                Math.clamp(options.get("exponent", 4.0), 1.0, 24.0),
                // Las cuentas hacen su propia cáscara; en ellas `hollow:false` solo las rellena.
                shape != PathShape.BEADS && options.get("hollow", 0.0) >= 0.5,
                (int) Math.round(Math.clamp(options.get("wall", parameters.thickness()), 1.0, 16.0)),
                options.get("filled", 0.0) >= 0.5,
                EndMode.byIndex((int) Math.round(options.get("end", 0.0))),
                NormalMode.byIndex((int) Math.round(options.get("normal", 0.0))),
                Quality.fromValue(options.get("quality", 1.0)),
                Math.clamp(options.get("tension", 0.0), -1.0, 1.0),
                Math.clamp(options.get("bias", 0.0), -1.0, 1.0),
                Math.clamp(options.get("continuity", 0.0), -1.0, 1.0),
                Math.clamp(twist, -3_600.0, 3_600.0),
                startRoll,
                endRoll,
                rollRange,
                // En la cadena `stretch` alarga cada eslabón (como en el motor clásico), no el espacio de tubo.
                shape == PathShape.CHAINLINK ? 1.0 : Math.clamp(options.get("stretch", 1.0), 0.1, 10.0),
                strands,
                strandThickness,
                frequency,
                (long) options.get("seed", 0.0),
                RadiusProgression.fromOptions(options),
                SmoothMode.byIndex((int) Math.round(Math.clamp(options.get("smooth", 0.0), 0.0, 2.0))),
                Math.clamp(options.get("coverage", PieceFit.DEFAULT_COVERAGE), 0.0, 1.0)
        );
    }

    /** Perfiles 2D barridos por {@code sweep}. El índice se guarda como opción numérica. */
    public enum TubeProfile {
        CIRCLE("circle", "Círculo", 0.0, "circulo", "round", "basic", "ci"),
        SQUARE("square", "Cuadrado", 0.0, "cuadrado", "sq"),
        DIAMOND("diamond", "Rombo", 0.0, "rombo", "di"),
        ROUNDED("rounded", "Redondeado", 0.0, "squircle", "supercircle", "redondeado", "rs", "sc"),
        POLYGON("polygon", "Polígono", 0.0, "poligono", "po", "ngon"),
        STAR("star", "Estrella", 0.45, "estrella", "st"),
        FLOWER("flower", "Flor", 0.25, "flor", "fl"),
        ROPE("rope", "Cuerda", 0.0, "cuerda", "circlescircle", "cc"),
        ELLIPSE("ellipse", "Elipse", 0.0, "elipse", "el"),
        RECTANGLE("rectangle", "Rectángulo", 0.0, "rectangulo", "re");

        private final String id;
        private final String displayName;
        private final double defaultDepth;
        private final String[] aliases;

        TubeProfile(String id, String displayName, double defaultDepth, String... aliases) {
            this.id = id;
            this.displayName = displayName;
            this.defaultDepth = defaultDepth;
            this.aliases = aliases;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        double defaultDepth() {
            return defaultDepth;
        }

        public boolean usesSides() {
            return this == POLYGON || this == STAR || this == FLOWER || this == ROPE;
        }

        public static TubeProfile byIndex(int index) {
            TubeProfile[] values = values();
            return values[Math.floorMod(index, values.length)];
        }

        public static TubeProfile byId(String input) {
            String normalized = normalize(input);
            for (TubeProfile profile : values()) {
                if (profile.id.equals(normalized) || List.of(profile.aliases).contains(normalized)) {
                    return profile;
                }
            }
            throw new IllegalArgumentException("Perfil desconocido: " + input
                    + " (circle, square, diamond, rounded, polygon, star, flower, rope, ellipse, rectangle)");
        }
    }

    /** Cómo terminan los extremos de un tubo abierto. */
    public enum EndMode {
        FLAT("flat", "Plano", "plano"),
        SOFT("soft", "Suave", "suave"),
        SPIKE("spike", "Punta", "punta", "cono", "cone"),
        ROUND("round", "Cúpula", "redondo", "cupula", "dome"),
        CUBE("cube", "Cubo", "cubo", "box");

        private final String id;
        private final String displayName;
        private final String[] aliases;

        EndMode(String id, String displayName, String... aliases) {
            this.id = id;
            this.displayName = displayName;
            this.aliases = aliases;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        /** Longitud extra más allá del extremo, en radios. */
        double extension() {
            return switch (this) {
                case FLAT, SOFT -> 0.0;
                case ROUND, CUBE -> 1.0;
                case SPIKE -> 2.0;
            };
        }

        public static EndMode byIndex(int index) {
            EndMode[] values = values();
            return values[Math.floorMod(index, values.length)];
        }

        public static EndMode byId(String input) {
            String normalized = normalize(input);
            for (EndMode mode : values()) {
                if (mode.id.equals(normalized) || List.of(mode.aliases).contains(normalized)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Extremo desconocido: " + input + " (flat, soft, spike, round, cube)");
        }
    }

    /** Orientación de la sección a lo largo del recorrido. */
    public enum NormalMode {
        CONSISTENT("consistent", "Continua", "consistente", "continua", "rmf"),
        HORIZONTAL("horizontal", "Horizontal", "plana"),
        UPRIGHT("upright", "Vertical", "vertical", "erguida");

        private final String id;
        private final String displayName;
        private final String[] aliases;

        NormalMode(String id, String displayName, String... aliases) {
            this.id = id;
            this.displayName = displayName;
            this.aliases = aliases;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public static NormalMode byIndex(int index) {
            NormalMode[] values = values();
            return values[Math.floorMod(index, values.length)];
        }

        public static NormalMode byId(String input) {
            String normalized = normalize(input);
            for (NormalMode mode : values()) {
                if (mode.id.equals(normalized) || List.of(mode.aliases).contains(normalized)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Modo de normales desconocido: " + input
                    + " (consistent, horizontal, upright)");
        }
    }

    /** Muestreo por bloque: más muestras conservan detalles finos a cambio de tiempo. */
    public enum Quality {
        FAST("fast", 0.0, "rapida", "rapido"),
        BALANCED("balanced", 1.0, "equilibrada", "normal"),
        HIGH("high", 2.0, "alta", "exact", "exacta");

        private final String id;
        private final double value;
        private final String[] aliases;

        Quality(String id, double value, String... aliases) {
            this.id = id;
            this.value = value;
            this.aliases = aliases;
        }

        public String id() {
            return id;
        }

        public double value() {
            return value;
        }

        static Quality fromValue(double value) {
            if (value < 0.75) {
                return FAST;
            }
            return value < 1.75 ? BALANCED : HIGH;
        }

        public static Quality byId(String input) {
            String normalized = normalize(input);
            for (Quality quality : values()) {
                if (quality.id.equals(normalized) || List.of(quality.aliases).contains(normalized)) {
                    return quality;
                }
            }
            throw new IllegalArgumentException("Calidad desconocida: " + input + " (fast, balanced, high)");
        }
    }

    /**
     * Progresión de radio por fotogramas clave ({@code 2,10,2} o {@code 2,0.3:12,2}), en porcentaje del radio
     * base. Interpola con Hermite monótono para que el grosor cambie con suavidad y sin sobrepasar los valores.
     */
    public record RadiusProgression(double[] positions, double[] factors) {
        public static final int MAX_KEYS = 6;
        public static final RadiusProgression CONSTANT = new RadiusProgression(new double[]{0.0, 1.0},
                new double[]{1.0, 1.0});

        public RadiusProgression {
            if (positions.length != factors.length || positions.length < 2) {
                throw new IllegalArgumentException("La progresión de radio necesita al menos dos valores");
            }
            for (int index = 1; index < positions.length; index++) {
                if (positions[index] <= positions[index - 1]) {
                    throw new IllegalArgumentException("Las posiciones del radio deben ser estrictamente crecientes");
                }
            }
        }

        public double at(double progress) {
            double clamped = Math.clamp(progress, 0.0, 1.0);
            int last = positions.length - 1;
            int segment = 0;
            while (segment < last - 1 && clamped > positions[segment + 1]) {
                segment++;
            }
            double span = positions[segment + 1] - positions[segment];
            double amount = (clamped - positions[segment]) / span;
            double first = factors[segment];
            double second = factors[segment + 1];
            double firstSlope = slope(segment) * span;
            double secondSlope = slope(segment + 1) * span;
            double squared = amount * amount;
            double cubed = squared * amount;
            return (2 * cubed - 3 * squared + 1) * first
                    + (cubed - 2 * squared + amount) * firstSlope
                    + (-2 * cubed + 3 * squared) * second
                    + (cubed - squared) * secondSlope;
        }

        /** Pendiente de Fritsch–Carlson: cero en extremos locales, así nunca hay rebotes. */
        private double slope(int index) {
            int last = positions.length - 1;
            if (index == 0) {
                return secant(0);
            }
            if (index == last) {
                return secant(last - 1);
            }
            double before = secant(index - 1);
            double after = secant(index);
            if (before * after <= 0.0) {
                return 0.0;
            }
            return 2.0 / (1.0 / before + 1.0 / after);
        }

        private double secant(int index) {
            return (factors[index + 1] - factors[index]) / (positions[index + 1] - positions[index]);
        }

        /**
         * Lee {@code k0..k5} (porcentaje del radio) y {@code p1..p4} (posición 0–1 de los intermedios).
         */
        static RadiusProgression fromOptions(ShapeOptions options) {
            List<Double> factors = new ArrayList<>();
            for (int index = 0; index < MAX_KEYS && options.has("k" + index); index++) {
                factors.add(Math.clamp(options.get("k" + index, 100.0), 5.0, 400.0) / 100.0);
            }
            if (factors.size() < 2) {
                return CONSTANT;
            }
            double[] positions = new double[factors.size()];
            double[] values = new double[factors.size()];
            int last = factors.size() - 1;
            for (int index = 0; index <= last; index++) {
                values[index] = factors.get(index);
                double automatic = index / (double) last;
                positions[index] = index == 0 || index == last
                        ? automatic
                        : Math.clamp(options.get("p" + index, automatic), 0.001, 0.999);
            }
            return new RadiusProgression(positions, values);
        }

        /**
         * Convierte la sintaxis de radios de comando en radio base y opciones {@code k}/{@code p}.
         * Acepta {@code 10}, {@code 2,10,2}, {@code 2,0.3:12,2} y separadores {@code _} o {@code /} para poder
         * escribirlo sin comillas.
         */
        public static Parsed parse(String input) {
            String[] entries = input.trim().split("[,_/;]+");
            if (entries.length == 0 || entries.length > MAX_KEYS) {
                throw new IllegalArgumentException("Indica entre 1 y " + MAX_KEYS + " radios");
            }
            double[] positions = new double[entries.length];
            double[] radii = new double[entries.length];
            boolean[] explicit = new boolean[entries.length];
            for (int index = 0; index < entries.length; index++) {
                String entry = entries[index].trim();
                int colon = entry.indexOf(':');
                try {
                    if (colon >= 0) {
                        positions[index] = Double.parseDouble(entry.substring(0, colon));
                        radii[index] = Double.parseDouble(entry.substring(colon + 1));
                        explicit[index] = true;
                    } else {
                        radii[index] = Double.parseDouble(entry);
                    }
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Radio inválido: " + entry);
                }
                if (!(radii[index] > 0.0) || radii[index] > 128.0) {
                    throw new IllegalArgumentException("Cada radio debe estar entre 0 y 128: " + entry);
                }
            }
            int last = entries.length - 1;
            if (explicit[0] && positions[0] != 0.0 || last > 0 && explicit[last] && positions[last] != 1.0) {
                throw new IllegalArgumentException("El primer radio está en la posición 0 y el último en la 1");
            }
            double maximum = 0.0;
            for (double radius : radii) {
                maximum = Math.max(maximum, radius);
            }
            int baseRadius = Math.clamp((int) Math.ceil(maximum), 1, 128);
            java.util.Map<String, Double> options = new java.util.LinkedHashMap<>();
            if (entries.length > 1) {
                boolean anyExplicit = false;
                for (int index = 1; index < last; index++) {
                    anyExplicit |= explicit[index];
                }
                fillImplicitPositions(positions, explicit);
                double previous = -1.0;
                for (int index = 0; index <= last; index++) {
                    double position = positions[index];
                    if (position <= previous || position < 0.0 || position > 1.0) {
                        throw new IllegalArgumentException("Las posiciones de radio deben crecer entre 0 y 1");
                    }
                    previous = position;
                    options.put("k" + index, Math.max(5.0, radii[index] / baseRadius * 100.0));
                    if (index > 0 && index < last && anyExplicit) {
                        options.put("p" + index, position);
                    }
                }
            }
            return new Parsed(baseRadius, options);
        }

        /** Las posiciones omitidas se reparten entre las dos posiciones explícitas que las rodean. */
        private static void fillImplicitPositions(double[] positions, boolean[] explicit) {
            int last = positions.length - 1;
            positions[0] = 0.0;
            positions[last] = 1.0;
            explicit[0] = true;
            explicit[last] = true;
            int anchor = 0;
            for (int index = 1; index <= last; index++) {
                if (!explicit[index]) {
                    continue;
                }
                int gap = index - anchor;
                for (int inner = anchor + 1; inner < index; inner++) {
                    positions[inner] = positions[anchor]
                            + (positions[index] - positions[anchor]) * (inner - anchor) / gap;
                }
                anchor = index;
            }
        }

        public record Parsed(int baseRadius, java.util.Map<String, Double> options) {
        }
    }

    private static String normalize(String input) {
        return input.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "")
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
    }
}
