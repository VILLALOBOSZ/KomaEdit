package com.komainu.moxia.roof;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lo que pide {@code /komaedit roof <tipo> [material] [banderas]}, ya validado. Es inmutable y no toca Minecraft:
 * los materiales se guardan como texto y el servidor los convierte al lanzar la orden.
 *
 * <p>Banderas (cada una con su alias corto o en inglés):</p>
 * <ul>
 *     <li>{@code -p <pendiente>}: {@code 1:1}, {@code 1:2}, {@code 2:1}… (subida:avance) o grados ({@code 30},
 *     {@code 30°}).</li>
 *     <li>{@code -a <bloques>}: alero, lo que sale el tejado por fuera de los muros (0 a 8).</li>
 *     <li>{@code -g <capas>}: grosor (1 a 6).</li>
 *     <li>{@code -c <material>}: caballete con otro material.</li>
 *     <li>{@code -b <n>}: buhardillas por cada lado largo (0 a 8).</li>
 *     <li>{@code -m <material>}: material de los hastiales y paredes; sin él se toma del muro de debajo.</li>
 *     <li>{@code -abierto}: deja los hastiales sin cerrar.</li>
 *     <li>{@code -v <bloque>}: ventana de las buhardillas.</li>
 *     <li>{@code -lado <n|s|e|o>}: lado bajo del tejado a un agua.</li>
 *     <li>{@code -lados <n>}: lados de la aguja (3 a 32).</li>
 *     <li>{@code -h <bloques>}: altura de cúpula, cono, aguja y pagoda.</li>
 *     <li>{@code -pretil <bloques>}: altura del pretil del tejado plano (0 a 6).</li>
 *     <li>{@code -q <bloques>}: quiebro, hasta dónde llega el faldón empinado de la mansarda o el de cuatro aguas
 *     del holandés.</li>
 *     <li>{@code -d}: la planta sale de los muros que hay en la selección, no de la selección.</li>
 *     <li>{@code -eje <x|z>}: dirección del caballete en los de dos aguas y holandés.</li>
 *     <li>{@code -escaleras <material>} y {@code -losas <material>}: piezas de otro material.</li>
 *     <li>{@code -r}: sustituye también los bloques que no son aire.</li>
 *     <li>{@code -e}: solo estima, no construye.</li>
 * </ul>
 */
public record RoofSettings(
        RoofType type,
        String material,
        double slope,
        int overhang,
        int thickness,
        String ridgeMaterial,
        int dormers,
        boolean closedGables,
        String wallMaterial,
        String windowMaterial,
        Side lowSide,
        int sides,
        double height,
        int parapet,
        int breakDepth,
        boolean detectWalls,
        Axis axis,
        String stairMaterial,
        String slabMaterial,
        boolean replace,
        boolean estimate
) {
    public static final String DEFAULT_MATERIAL = "minecraft:spruce_planks";
    public static final String DEFAULT_WINDOW = "minecraft:glass_pane";
    public static final double MIN_SLOPE = 0.1;
    public static final double MAX_SLOPE = 8.0;
    public static final int MAX_OVERHANG = 8;
    public static final int MAX_THICKNESS = 6;
    public static final int MAX_DORMERS = 8;

    /** Dirección horizontal: el lado bajo del tejado a un agua. */
    public enum Side {
        NORTH("norte", 0, -1, "n", "north", "norte"),
        SOUTH("sur", 0, 1, "s", "south", "sur"),
        EAST("este", 1, 0, "e", "east", "este"),
        WEST("oeste", -1, 0, "o", "w", "west", "oeste");

        private final String displayName;
        private final int dx;
        private final int dz;
        private final List<String> aliases;

        Side(String displayName, int dx, int dz, String... aliases) {
            this.displayName = displayName;
            this.dx = dx;
            this.dz = dz;
            this.aliases = List.of(aliases);
        }

        public String displayName() {
            return displayName;
        }

        public int dx() {
            return dx;
        }

        public int dz() {
            return dz;
        }

        public static Side byId(String input) {
            String normalized = RoofType.normalize(input);
            for (Side side : values()) {
                if (side.aliases.contains(normalized)) {
                    return side;
                }
            }
            throw new IllegalArgumentException("Lado desconocido: " + input + " (n, s, e u o)");
        }
    }

    public enum Axis {
        X, Z;

        public static Axis byId(String input) {
            return switch (RoofType.normalize(input)) {
                case "x", "este", "oeste", "eo", "ew" -> X;
                case "z", "norte", "sur", "ns" -> Z;
                default -> throw new IllegalArgumentException("Eje desconocido: " + input + " (x o z)");
            };
        }
    }

    public RoofSettings {
        if (type == null) {
            throw new IllegalArgumentException("Falta el tipo de tejado (" + RoofType.names() + ")");
        }
        material = material == null || material.isBlank() ? DEFAULT_MATERIAL : material.trim();
        windowMaterial = windowMaterial == null || windowMaterial.isBlank() ? DEFAULT_WINDOW : windowMaterial.trim();
        if (!(slope >= MIN_SLOPE && slope <= MAX_SLOPE) && type != RoofType.FLAT) {
            throw new IllegalArgumentException("La pendiente va de 1:10 a 8:1");
        }
        overhang = check("alero", overhang, 0, MAX_OVERHANG);
        thickness = check("grosor", thickness, 1, MAX_THICKNESS);
        dormers = check("buhardillas", dormers, 0, MAX_DORMERS);
        sides = check("lados", sides, 3, 32);
        parapet = check("pretil", parapet, 0, 6);
        if (!Double.isNaN(height) && !(height >= 1.0 && height <= 256.0)) {
            throw new IllegalArgumentException("La altura va de 1 a 256 bloques");
        }
        if (breakDepth != -1) {
            breakDepth = check("quiebro", breakDepth, 1, 64);
        }
    }

    /** Ajustes de un tipo con todo por defecto. */
    public static RoofSettings defaults(RoofType type) {
        return new RoofSettings(type, null, type.defaultSlope(),
                type == RoofType.FLAT || type == RoofType.DOME ? 0 : 1, 1, null, 0, true,
                null, null, null, 8, Double.NaN, 1, -1, false, null, null, null, false, false);
    }

    public RoofSettings withSlope(double value) {
        return new RoofSettings(type, material, value, overhang, thickness, ridgeMaterial, dormers, closedGables,
                wallMaterial, windowMaterial, lowSide, sides, height, parapet, breakDepth, detectWalls, axis,
                stairMaterial, slabMaterial, replace, estimate);
    }

    public RoofSettings withOverhang(int value) {
        return new RoofSettings(type, material, slope, value, thickness, ridgeMaterial, dormers, closedGables,
                wallMaterial, windowMaterial, lowSide, sides, height, parapet, breakDepth, detectWalls, axis,
                stairMaterial, slabMaterial, replace, estimate);
    }

    public RoofSettings withThickness(int value) {
        return new RoofSettings(type, material, slope, overhang, value, ridgeMaterial, dormers, closedGables,
                wallMaterial, windowMaterial, lowSide, sides, height, parapet, breakDepth, detectWalls, axis,
                stairMaterial, slabMaterial, replace, estimate);
    }

    public RoofSettings withDormers(int value) {
        return new RoofSettings(type, material, slope, overhang, thickness, ridgeMaterial, value, closedGables,
                wallMaterial, windowMaterial, lowSide, sides, height, parapet, breakDepth, detectWalls, axis,
                stairMaterial, slabMaterial, replace, estimate);
    }

    public RoofSettings withLowSide(Side value) {
        return new RoofSettings(type, material, slope, overhang, thickness, ridgeMaterial, dormers, closedGables,
                wallMaterial, windowMaterial, value, sides, height, parapet, breakDepth, detectWalls, axis,
                stairMaterial, slabMaterial, replace, estimate);
    }

    public RoofSettings withWallMaterial(String value) {
        return new RoofSettings(type, material, slope, overhang, thickness, ridgeMaterial, dormers, closedGables,
                wallMaterial == null ? value : wallMaterial, windowMaterial, lowSide, sides, height, parapet,
                breakDepth, detectWalls, axis, stairMaterial, slabMaterial, replace, estimate);
    }

    /** Lee {@code <tipo> [material] [banderas]}. */
    public static RoofSettings parse(String input) {
        List<String> tokens = tokens(input);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Falta el tipo de tejado (" + RoofType.names() + ")");
        }
        RoofType type = RoofType.byId(tokens.getFirst());
        RoofSettings defaults = defaults(type);
        String material = null;
        double slope = defaults.slope();
        int overhang = defaults.overhang();
        int thickness = 1;
        String ridge = null;
        int dormers = 0;
        boolean closed = true;
        String wall = null;
        String window = null;
        Side side = null;
        int sides = 8;
        double height = Double.NaN;
        int parapet = 1;
        int breakDepth = -1;
        boolean detect = false;
        Axis axis = null;
        String stairs = null;
        String slabs = null;
        boolean replace = false;
        boolean estimate = false;
        for (int index = 1; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (!token.startsWith("-") || isNumber(token)) {
                if (material != null) {
                    throw new IllegalArgumentException("Sobra «" + token + "»: el material ya es «" + material
                            + "» (las banderas empiezan por -)");
                }
                material = token;
                continue;
            }
            String flag = token.toLowerCase(Locale.ROOT);
            switch (flag) {
                case "-abierto", "-open", "-sinhastial", "-sinhastiales" -> closed = false;
                case "-d", "-detectar", "-muros", "-auto", "-detect" -> detect = true;
                case "-r", "-reemplazar", "-replace" -> replace = true;
                case "-e", "-estimar", "-estimate" -> estimate = true;
                default -> {
                    String value = value(tokens, index, token);
                    index++;
                    switch (flag) {
                        case "-p", "-pendiente", "-pitch", "-slope" -> slope = slope(value);
                        case "-a", "-alero", "-overhang" -> overhang = integer(value, "alero");
                        case "-g", "-grosor", "-thickness" -> thickness = integer(value, "grosor");
                        case "-c", "-caballete", "-ridge" -> ridge = value;
                        case "-b", "-buhardillas", "-buhardilla", "-dormers" -> dormers = integer(value, "buhardillas");
                        case "-m", "-muro", "-wall", "-hastial" -> wall = value;
                        case "-v", "-ventana", "-window" -> window = value;
                        case "-lado", "-side", "-bajo" -> side = Side.byId(value);
                        case "-lados", "-sides" -> sides = integer(value, "lados");
                        case "-h", "-altura", "-height" -> height = number(value, "altura");
                        case "-pretil", "-parapet" -> parapet = integer(value, "pretil");
                        case "-q", "-quiebro", "-break" -> breakDepth = integer(value, "quiebro");
                        case "-eje", "-axis" -> axis = Axis.byId(value);
                        case "-escaleras", "-escalera", "-stairs" -> stairs = value;
                        case "-losas", "-losa", "-slabs" -> slabs = value;
                        default -> throw new IllegalArgumentException("Bandera desconocida: " + token
                                + " (-p, -a, -g, -c, -b, -m, -abierto, -v, -lado, -lados, -h, -pretil, -q, -d, "
                                + "-eje, -escaleras, -losas, -r, -e)");
                    }
                }
            }
        }
        if (dormers > 0 && !type.allowsDormers()) {
            throw new IllegalArgumentException("El tejado " + type.displayName().toLowerCase(Locale.ROOT)
                    + " no admite buhardillas (sí los de dos y cuatro aguas, mansarda, a un agua y holandés)");
        }
        return new RoofSettings(type, material, slope, overhang, thickness, ridge, dormers, closed, wall, window,
                side, sides, height, parapet, breakDepth, detect, axis, stairs, slabs, replace, estimate);
    }

    /**
     * Pendiente como subida por bloque de avance: {@code 1:2} = 0,5; {@code 2:1} = 2; un número solo, o con
     * {@code °}, son grados.
     */
    public static double slope(String input) {
        String text = input.trim().toLowerCase(Locale.ROOT).replace(',', '.');
        int colon = text.indexOf(':');
        if (colon < 0) {
            colon = text.indexOf('/');
        }
        if (colon > 0) {
            double rise = number(text.substring(0, colon), "pendiente");
            double run = number(text.substring(colon + 1), "pendiente");
            if (!(rise > 0.0) || !(run > 0.0)) {
                throw new IllegalArgumentException("La pendiente necesita dos números positivos, como 1:2");
            }
            return checkSlope(rise / run, input);
        }
        String degrees = text;
        for (String suffix : List.of("grados", "grado", "deg", "°", "º", "g", "d")) {
            if (degrees.endsWith(suffix)) {
                degrees = degrees.substring(0, degrees.length() - suffix.length());
                break;
            }
        }
        double angle = number(degrees, "pendiente");
        if (!(angle > 5.0 && angle < 83.0)) {
            throw new IllegalArgumentException("La pendiente en grados va de 6 a 82 (o usa subida:avance, como 1:2)");
        }
        return checkSlope(Math.tan(Math.toRadians(angle)), input);
    }

    /** {@code 1:2}, {@code 1:1}, {@code 2:1} o {@code 1:1,73}: la pendiente como la escribe un constructor. */
    public static String describeSlope(double slope) {
        if (slope >= 1.0) {
            return format(slope) + ":1";
        }
        return "1:" + format(1.0 / slope);
    }

    private static String format(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        return String.format(Locale.ROOT, "%.2f", rounded).replaceAll("0+$", "").replace('.', ',');
    }

    private static double checkSlope(double slope, String input) {
        if (!(slope >= MIN_SLOPE && slope <= MAX_SLOPE)) {
            throw new IllegalArgumentException("Pendiente fuera de rango: " + input + " (de 1:10 a 8:1)");
        }
        return slope;
    }

    private static int check(String name, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Valor de " + name + " fuera de rango: va de " + minimum + " a " + maximum);
        }
        return value;
    }

    private static String value(List<String> tokens, int index, String flag) {
        if (index + 1 >= tokens.size()) {
            throw new IllegalArgumentException("Falta el valor de " + flag);
        }
        return tokens.get(index + 1);
    }

    private static int integer(String value, String name) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Valor de " + name + " no válido (tiene que ser un número entero): " + value);
        }
    }

    private static double number(String value, String name) {
        try {
            double parsed = Double.parseDouble(value.trim().replace(',', '.'));
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Valor de " + name + " no válido (tiene que ser un número): " + value);
        }
    }

    private static boolean isNumber(String token) {
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /** Separa por espacios sin partir lo que va entre corchetes ({@code oak_stairs[half=top]}). */
    static List<String> tokens(String input) {
        List<String> tokens = new ArrayList<>();
        if (input == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char character : input.strip().toCharArray()) {
            if (character == '[' || character == '(') {
                depth++;
            } else if ((character == ']' || character == ')') && depth > 0) {
                depth--;
            }
            if (Character.isWhitespace(character) && depth == 0) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
