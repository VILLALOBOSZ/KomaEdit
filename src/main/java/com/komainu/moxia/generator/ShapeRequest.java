package com.komainu.moxia.generator;

import java.util.Locale;

/**
 * Una forma de {@code /komaedit shape} ya leída: tipo, medidas, banderas y patrones. Las medidas están en bloques y
 * los ángulos en grados.
 */
public record ShapeRequest(
        Form form,
        String pattern,
        double radius,
        double secondRadius,
        int height,
        int sides,
        double rotation,
        double[] direction,
        Cross cross,
        String vertexPattern,
        boolean hollow,
        boolean down,
        boolean orient,
        boolean moveSelection,
        boolean facesOnly,
        boolean wallsOnly
) {
    public static final int MAX_RADIUS = 160;
    public static final int MAX_HEIGHT = 384;

    public enum Form {
        CONE("cone", "cono", "<patrón> <radios> <altura> [giro] [-dhos]"),
        POLYDOME("polydome", "polidomo", "<patrón> <lados> <radio> [altura] [-v <patrón>] [-s]"),
        POLYGON("polygon", "poligono", "<patrón> <lados> <radio> [altura] [dirección] [-s]"),
        SQUARE("square", "cuadrado", "<patrón> <radio> [altura] [-fws]"),
        TETRAHEDRON("tetrahedron", "tetraedro", "<patrón> <radio> [giro] [-hos]"),
        TORUS("torus", "toro", "<patrón> <radio_mayor> <radio_menor> [sección] [-hos]");

        private final String id;
        private final String alias;
        private final String usage;

        Form(String id, String alias, String usage) {
            this.id = id;
            this.alias = alias;
            this.usage = usage;
        }

        public String id() {
            return id;
        }

        public String alias() {
            return alias;
        }

        public String usage() {
            return "/komaedit shape " + id + " " + usage;
        }

        public static Form byId(String text) {
            String normalized = GeneratorArgs.lower(text);
            for (Form form : values()) {
                if (form.id.equals(normalized) || form.alias.equals(normalized)) {
                    return form;
                }
            }
            throw new IllegalArgumentException("Forma desconocida: " + text
                    + " (cone, polydome, polygon, square, tetrahedron o torus)");
        }
    }

    /** Sección del toro. */
    public enum Cross {
        CIRCLE("circle", "circulo"),
        DIAMOND("diamond", "rombo"),
        ROUNDED_SQUARE("rounded_square", "redondeada"),
        SQUARE("square", "cuadrada");

        private final String id;
        private final String alias;

        Cross(String id, String alias) {
            this.id = id;
            this.alias = alias;
        }

        public String id() {
            return id;
        }

        public static Cross byId(String text) {
            String normalized = GeneratorArgs.lower(text).replace('-', '_');
            for (Cross cross : values()) {
                if (cross.id.equals(normalized) || cross.alias.equals(normalized)
                        || cross.id.replace("_", "").equals(normalized)) {
                    return cross;
                }
            }
            throw new IllegalArgumentException("Sección desconocida: " + text
                    + " (circle, diamond, rounded_square o square)");
        }
    }

    public static ShapeRequest parse(Form form, String text) {
        String usage = form.usage();
        return switch (form) {
            case CONE -> {
                GeneratorArgs args = GeneratorArgs.parse(text, "", "dhos").atMost(4, usage);
                double[] radii = radii(args.require(1, "el radio", usage));
                int height = GeneratorArgs.integer(args.require(2, "la altura", usage), 1, MAX_HEIGHT, "La altura");
                double rotation = args.positional(3) == null ? 0.0
                        : GeneratorArgs.number(args.positional(3), -3_600.0, 3_600.0, "El giro");
                yield new ShapeRequest(form, args.positional(0), radii[0], radii[1], height, 0, rotation, null,
                        Cross.CIRCLE, null, args.has('h'), args.has('d'), args.has('o'), args.has('s'), false,
                        false);
            }
            case POLYDOME -> {
                GeneratorArgs args = GeneratorArgs.parse(text, "v", "s").atMost(4, usage);
                int sides = sides(args.require(1, "el número de lados", usage));
                double radius = radius(args.require(2, "el radio", usage), "El radio");
                double height = args.positional(3) == null ? 1.0
                        : GeneratorArgs.number(args.positional(3), 0.1, 4.0, "La altura (proporción del radio)");
                yield new ShapeRequest(form, args.positional(0), radius, height, 0, sides, 0.0, null,
                        Cross.CIRCLE, args.value('v'), true, false, false, args.has('s'), false, false);
            }
            case POLYGON -> {
                GeneratorArgs args = GeneratorArgs.parse(text, "", "s").atMost(5, usage);
                int sides = sides(args.require(1, "el número de lados", usage));
                double radius = radius(args.require(2, "el radio", usage), "El radio");
                int height = args.positional(3) == null ? 1
                        : GeneratorArgs.integer(args.positional(3), 1, MAX_HEIGHT, "La altura");
                double[] direction = args.positional(4) == null ? null : direction(args.positional(4));
                yield new ShapeRequest(form, args.positional(0), radius, 0.0, height, sides, 0.0, direction,
                        Cross.CIRCLE, null, false, false, false, args.has('s'), false, false);
            }
            case SQUARE -> {
                GeneratorArgs args = GeneratorArgs.parse(text, "", "fws").atMost(3, usage);
                int radius = GeneratorArgs.integer(args.require(1, "el radio", usage), 0, MAX_RADIUS, "El radio");
                int height = args.positional(2) == null ? 1
                        : GeneratorArgs.integer(args.positional(2), 1, MAX_HEIGHT, "La altura");
                yield new ShapeRequest(form, args.positional(0), radius, 0.0, height, 4, 0.0, null,
                        Cross.CIRCLE, null, false, false, false, args.has('s'), args.has('f'), args.has('w'));
            }
            case TETRAHEDRON -> {
                GeneratorArgs args = GeneratorArgs.parse(text, "", "hos").atMost(3, usage);
                double radius = radius(args.require(1, "el radio", usage), "El radio");
                double rotation = args.positional(2) == null ? 0.0
                        : GeneratorArgs.number(args.positional(2), -3_600.0, 3_600.0, "El giro");
                yield new ShapeRequest(form, args.positional(0), radius, 0.0, 0, 3, rotation, null,
                        Cross.CIRCLE, null, args.has('h'), false, args.has('o'), args.has('s'), false, false);
            }
            case TORUS -> {
                GeneratorArgs args = GeneratorArgs.parse(text, "", "hos").atMost(4, usage);
                double major = radius(args.require(1, "el radio mayor", usage), "El radio mayor");
                double minor = GeneratorArgs.number(args.require(2, "el radio menor", usage), 0.0, major,
                        "El radio menor");
                Cross cross = args.positional(3) == null ? Cross.CIRCLE : Cross.byId(args.positional(3));
                yield new ShapeRequest(form, args.positional(0), major, minor, 0, 0, 0.0, null, cross, null,
                        args.has('h'), false, args.has('o'), args.has('s'), false, false);
            }
        };
    }

    private static int sides(String text) {
        return GeneratorArgs.integer(text, 3, 64, "El número de lados");
    }

    private static double radius(String text, String name) {
        return GeneratorArgs.number(text, 0.0, MAX_RADIUS, name);
    }

    /** {@code r} o {@code norte-sur,este-oeste}. */
    private static double[] radii(String text) {
        String[] parts = text.split("[,;]");
        // «,» sola se parte en nada: también es escribir mal los radios.
        if (parts.length == 0 || parts.length > 2) {
            throw new IllegalArgumentException("El cono admite uno o dos radios: norte-sur,este-oeste");
        }
        double first = radius(parts[0], "El radio");
        double second = parts.length == 2 ? radius(parts[1], "El radio este-oeste") : first;
        return new double[]{first, second};
    }

    /**
     * Dirección del polígono: {@code north}, {@code up}, {@code northeast}, {@code upsouthwest}… o sus iniciales
     * ({@code n}, {@code une}); {@code me} o {@code aim} es la mirada.
     */
    static double[] direction(String text) {
        String normalized = GeneratorArgs.lower(text).replace("-", "").replace("_", "");
        if (normalized.equals("me") || normalized.equals("aim") || normalized.equals("mirada")) {
            return null;
        }
        double x = 0;
        double y = 0;
        double z = 0;
        String rest = normalized;
        String[][] words = {
                {"north", "norte", "n"}, {"south", "sur", "s"}, {"east", "este", "e"},
                {"west", "oeste", "w", "o"}, {"up", "arriba", "u"}, {"down", "abajo", "d"}
        };
        boolean[] used = new boolean[6];
        outer:
        while (!rest.isEmpty()) {
            for (int axis = 0; axis < words.length; axis++) {
                for (String word : words[axis]) {
                    if (rest.startsWith(word)) {
                        if (used[axis]) {
                            break;
                        }
                        used[axis] = true;
                        switch (axis) {
                            case 0 -> z -= 1;
                            case 1 -> z += 1;
                            case 2 -> x += 1;
                            case 3 -> x -= 1;
                            case 4 -> y += 1;
                            default -> y -= 1;
                        }
                        rest = rest.substring(word.length());
                        continue outer;
                    }
                }
            }
            throw new IllegalArgumentException("Dirección desconocida: " + text
                    + " (north, south, east, west, up, down y sus combinaciones, como northeast o upwest)");
        }
        if (x == 0 && y == 0 && z == 0) {
            throw new IllegalArgumentException("La dirección " + text + " se anula a sí misma");
        }
        return new double[]{x, y, z};
    }

    /** La mirada redondeada a una de las 26 direcciones (ejes y diagonales). */
    public static double[] snap(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-9) {
            return new double[]{0, 1, 0};
        }
        double threshold = Math.sin(Math.toRadians(22.5));
        double[] result = {
                Math.abs(x / length) > threshold ? Math.signum(x) : 0,
                Math.abs(y / length) > threshold ? Math.signum(y) : 0,
                Math.abs(z / length) > threshold ? Math.signum(z) : 0
        };
        if (result[0] == 0 && result[1] == 0 && result[2] == 0) {
            int axis = Math.abs(x) >= Math.abs(y) && Math.abs(x) >= Math.abs(z) ? 0
                    : Math.abs(y) >= Math.abs(z) ? 1 : 2;
            result[axis] = Math.signum(axis == 0 ? x : axis == 1 ? y : z);
        }
        return result;
    }

    public String describe() {
        return switch (form) {
            case CONE -> "Cono";
            case POLYDOME -> "Polidomo de " + sides + " lados";
            case POLYGON -> "Polígono de " + sides + " lados";
            case SQUARE -> "Cuadrado";
            case TETRAHEDRON -> "Tetraedro";
            case TORUS -> "Toro (" + cross.id().toLowerCase(Locale.ROOT) + ")";
        };
    }
}
