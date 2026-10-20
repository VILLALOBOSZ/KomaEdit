package com.komainu.moxia.loft;

import com.komainu.moxia.deform.SmoothProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Lo que pide {@code /komaedit loft [curvas…] [material] [banderas]}, ya validado y sin tocar Minecraft.
 *
 * <ul>
 *     <li>Curvas: de 2 a 6 nombres de recorridos guardados ({@code /komaedit path save}); {@code actual} es el
 *     recorrido de ahora (puntos P, o la selección de la supervarita o de WorldEdit). Sin nombres, los puntos de
 *     ahora se reparten en {@code -n} curvas seguidas (4 puntos = dos tramos rectos, una lona).</li>
 *     <li>{@code -i lineal|catmull|catenaria}: cómo se pasa de una curva a la siguiente.</li>
 *     <li>{@code -comba <bloques|%>}: lo que cuelga cada tramo en la catenaria (por defecto el 15 % del vano).</li>
 *     <li>{@code -g <bloques>}: grosor (1 a 8).</li>
 *     <li>{@code -w <perfil>}: losas y escaleras en la superficie, como en el resto de figuras.</li>
 *     <li>{@code -cerrar}: une la última curva con la primera (con tres o más).</li>
 *     <li>{@code -n <curvas>}: en cuántas curvas se reparten los puntos de ahora (2 a 6).</li>
 *     <li>{@code -e}: solo estima.</li>
 * </ul>
 */
public record LoftSettings(
        List<String> curves,
        String material,
        Interpolation interpolation,
        double sag,
        boolean relativeSag,
        int thickness,
        SmoothProfile smooth,
        boolean closed,
        int split,
        boolean estimate
) {
    public static final String CURRENT = "actual";
    public static final String DEFAULT_MATERIAL = "minecraft:white_wool";
    public static final int MAX_CURVES = 6;
    public static final int MAX_THICKNESS = 8;
    /** Comba por defecto: el 15 % del vano. */
    public static final double DEFAULT_RELATIVE_SAG = 0.15;
    private static final List<String> CURRENT_ALIASES = List.of("actual", "current", "p", "puntos", "sel",
            "seleccion", "selección", "varita", "supervarita", "wand");

    public enum Interpolation {
        LINEAR("lineal", "Lineal", "linear", "recta", "rectas", "lin"),
        CATMULL_ROM("catmull", "Catmull-Rom", "catmullrom", "cr", "curva", "suave", "spline"),
        CATENARY("catenaria", "Catenaria", "catenary", "cuelga", "colgante", "cat");

        private final String id;
        private final String displayName;
        private final List<String> aliases;

        Interpolation(String id, String displayName, String... aliases) {
            this.id = id;
            this.displayName = displayName;
            this.aliases = List.of(aliases);
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public static Interpolation byId(String input) {
            String normalized = input.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            for (Interpolation interpolation : values()) {
                if (interpolation.id.equals(normalized) || interpolation.aliases.contains(normalized)) {
                    return interpolation;
                }
            }
            throw new IllegalArgumentException("Interpolación desconocida: " + input
                    + " (lineal, catmull o catenaria)");
        }
    }

    public LoftSettings {
        curves = List.copyOf(curves);
        material = material == null || material.isBlank() ? DEFAULT_MATERIAL : material.trim();
        if (interpolation == null) {
            interpolation = Interpolation.CATMULL_ROM;
        }
        if (!Double.isNaN(sag) && !(sag >= 0.0 && sag <= (relativeSag ? 5.0 : 256.0))) {
            throw new IllegalArgumentException(relativeSag ? "La comba va del 0 % al 500 % del vano"
                    : "La comba va de 0 a 256 bloques");
        }
        if (thickness < 1 || thickness > MAX_THICKNESS) {
            throw new IllegalArgumentException("El grosor va de 1 a " + MAX_THICKNESS);
        }
        smooth = smooth == null ? SmoothProfile.NONE : smooth;
        if (split < 2 || split > MAX_CURVES) {
            throw new IllegalArgumentException("Las curvas van de 2 a " + MAX_CURVES);
        }
        if (!curves.isEmpty() && (curves.size() < 2 || curves.size() > MAX_CURVES)) {
            throw new IllegalArgumentException("El loft se tiende entre 2 y " + MAX_CURVES + " curvas (tiene "
                    + curves.size() + ")");
        }
    }

    /** Número de curvas que tendrá el loft. */
    public int curveCount() {
        return curves.isEmpty() ? split : curves.size();
    }

    /**
     * @param isCurve si un nombre es un recorrido guardado (lo que no lo es se toma como material)
     */
    public static LoftSettings parse(String input, Predicate<String> isCurve) {
        List<String> curves = new ArrayList<>();
        String material = null;
        Interpolation interpolation = Interpolation.CATMULL_ROM;
        double sag = Double.NaN;
        boolean relative = false;
        int thickness = 1;
        SmoothProfile smooth = SmoothProfile.NONE;
        boolean closed = false;
        int split = 2;
        boolean estimate = false;
        List<String> tokens = tokens(input);
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            String lower = token.toLowerCase(Locale.ROOT);
            if (!token.startsWith("-")) {
                if (CURRENT_ALIASES.contains(lower)) {
                    curves.add(CURRENT);
                } else if (isCurve.test(lower)) {
                    curves.add(lower);
                } else if (material == null) {
                    material = token;
                } else {
                    throw new IllegalArgumentException("No tienes ningún recorrido llamado «" + token + "» y el "
                            + "material ya es «" + material + "» (guarda curvas con /komaedit path save <nombre>)");
                }
                continue;
            }
            switch (lower) {
                case "-cerrar", "-cerrado", "-closed", "-close" -> closed = true;
                case "-e", "-estimar", "-estimate" -> estimate = true;
                default -> {
                    if (index + 1 >= tokens.size()) {
                        throw new IllegalArgumentException("Falta el valor de " + token);
                    }
                    String value = tokens.get(++index);
                    switch (lower) {
                        case "-i", "-interpolacion", "-interpolación", "-mode", "-modo" ->
                                interpolation = Interpolation.byId(value);
                        case "-comba", "-s", "-sag", "-flecha" -> {
                            String text = value.trim();
                            relative = text.endsWith("%");
                            double number = number(relative ? text.substring(0, text.length() - 1) : text, "comba");
                            sag = relative ? number / 100.0 : number;
                        }
                        case "-g", "-grosor", "-thickness" -> thickness = (int) Math.round(number(value, "grosor"));
                        case "-w", "-smooth", "-piezas" -> smooth = SmoothProfile.parse(value);
                        case "-n", "-curvas", "-curves" -> split = (int) Math.round(number(value, "curvas"));
                        default -> throw new IllegalArgumentException("Bandera desconocida: " + token
                                + " (-i, -comba, -g, -w, -cerrar, -n, -e)");
                    }
                }
            }
        }
        if (!Double.isNaN(sag) && interpolation != Interpolation.CATENARY) {
            interpolation = Interpolation.CATENARY;
        }
        return new LoftSettings(curves, material, interpolation, sag, relative, thickness, smooth, closed, split,
                estimate);
    }

    /** Comba de un tramo de longitud {@code span}. */
    public double sagFor(double span) {
        if (Double.isNaN(sag)) {
            return DEFAULT_RELATIVE_SAG * span;
        }
        return relativeSag ? sag * span : sag;
    }

    private static double number(String value, String name) {
        try {
            double parsed = Double.parseDouble(value.trim().replace(',', '.'));
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Valor de " + name + " no válido: " + value);
        }
    }

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
