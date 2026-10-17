package com.komainu.moxia.texture;

import com.komainu.moxia.noise.NoiseSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Lee las texturas de {@code /komaedit texture advanced}: {@code Tipo(Parámetro:valor,...)} con la notación de
 * ezEdits, sin distinguir mayúsculas y con los mismos parámetros que los modos sueltos. Los valores pueden ser
 * números, direcciones, ruidos o texturas anidadas: {@code WeightedAverage(T1:Sun(),T2:Ambient())},
 * {@code Blend(T:Flow(Noise:@@ridged(Freq:0.12)),Radius:0.7)}.
 */
public final class TextureSpec {
    private static final int MAX_LENGTH = 1_024;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_NODES = 32;

    private TextureSpec() {
    }

    public static TextureField parse(String input, long seed) {
        return parse(input, seed, null);
    }

    /**
     * @param savedNoises busca los ruidos guardados del jugador por nombre ({@code null} si no hay almacén)
     */
    public static TextureField parse(String input, long seed, Function<String, String> savedNoises) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta la textura, por ejemplo Ambient(Radius:2)");
        }
        String text = input.strip();
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("La textura admite como mucho " + MAX_LENGTH + " caracteres");
        }
        return new Parser(seed, savedNoises).texture(text, 0);
    }

    /**
     * Un ruido escrito por el jugador: {@code Perlin(f:0.04)}, el nombre de uno de serie o guardado ({@code marble},
     * {@code #mio}) o, como en ezEdits, {@code @@nombre} con parámetros que sustituyen a los suyos
     * ({@code @@ridged(Freq:0.12)}).
     */
    public static NoiseSpec noise(String input, Function<String, String> savedNoises) {
        String text = input.strip();
        if (!text.startsWith("@")) {
            return NoiseSpec.parse(text, savedNoises);
        }
        String body = text.startsWith("@@") ? text.substring(2) : text.substring(1);
        int open = body.indexOf('(');
        String name = (open < 0 ? body : body.substring(0, open)).trim();
        if (open >= 0 && !body.endsWith(")")) {
            throw new IllegalArgumentException("Falta el paréntesis de cierre en " + text);
        }
        String overrides = open < 0 ? "" : body.substring(open + 1, body.length() - 1).trim();
        String base = NoiseSpec.parse(name, savedNoises).toString();
        if (overrides.isEmpty()) {
            return NoiseSpec.parse(base);
        }
        int baseOpen = base.indexOf('(');
        if (baseOpen < 0) {
            return NoiseSpec.parse(base + "(" + overrides + ")");
        }
        String baseBody = base.substring(baseOpen + 1, base.length() - 1).trim();
        return NoiseSpec.parse(base.substring(0, baseOpen) + "(" + (baseBody.isEmpty() ? "" : baseBody + ",")
                + overrides + ")");
    }

    public static NoiseSpec noise(String input) {
        return noise(input, null);
    }

    /** {@code 0,90}, {@code 0;90}, {@code 0:90} o {@code 0..90} → {@code [0, 90]} en grados. */
    public static double[] interval(String input) {
        String[] parts = input.trim().split("\\.\\.|[,;:]");
        if (parts.length != 2) {
            throw new IllegalArgumentException("El intervalo se escribe inicio,fin en grados, por ejemplo 0,90");
        }
        double first = number(parts[0], "el intervalo");
        double second = number(parts[1], "el intervalo");
        if (first < 0 || second > 180 || first > second) {
            throw new IllegalArgumentException("El intervalo va de 0 a 180 grados y el inicio no supera al fin");
        }
        return new double[]{first, second};
    }

    static double number(String text, String what) {
        try {
            double value = Double.parseDouble(text.trim());
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("«" + text.trim() + "» no es un número válido para " + what);
        }
    }

    static boolean bool(String text, String what) {
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "true", "t", "1", "yes", "on", "si", "sí" -> true;
            case "false", "f", "0", "no", "off" -> false;
            default -> throw new IllegalArgumentException("«" + text.trim() + "» no es verdadero ni falso para " + what);
        };
    }

    /** Eje {@code x}, {@code y} o {@code z} → 0, 1 o 2. */
    public static int axis(String text) {
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "x" -> 0;
            case "y" -> 1;
            case "z" -> 2;
            default -> throw new IllegalArgumentException("El eje es x, y o z, no «" + text.trim() + "»");
        };
    }

    /** Parte {@code a:b,c:Tipo(d:e,f:g)} por las comas de primer nivel. */
    static List<String> split(String body, char separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < body.length(); index++) {
            char current = body.charAt(index);
            if (current == '(' || current == '[') {
                depth++;
            } else if (current == ')' || current == ']') {
                depth--;
                if (depth < 0) {
                    throw new IllegalArgumentException("Sobra un paréntesis de cierre en «" + body + "»");
                }
            } else if (current == separator && depth == 0) {
                parts.add(body.substring(start, index));
                start = index + 1;
            }
        }
        if (depth != 0) {
            throw new IllegalArgumentException("Falta un paréntesis de cierre en «" + body + "»");
        }
        parts.add(body.substring(start));
        return parts;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private static final class Parser {
        private final long seed;
        private final Function<String, String> savedNoises;
        private int nodes;

        Parser(long seed, Function<String, String> savedNoises) {
            this.seed = seed;
            this.savedNoises = savedNoises;
        }

        TextureField texture(String text, int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("La textura anida demasiadas texturas (máximo " + MAX_DEPTH + ")");
            }
            if (++nodes > MAX_NODES) {
                throw new IllegalArgumentException("La textura tiene demasiadas partes (máximo " + MAX_NODES + ")");
            }
            String trimmed = text.strip();
            int open = trimmed.indexOf('(');
            String name = key(open < 0 ? trimmed : trimmed.substring(0, open));
            Map<String, String> values = new LinkedHashMap<>();
            if (open >= 0) {
                if (!trimmed.endsWith(")")) {
                    throw new IllegalArgumentException("Falta el paréntesis de cierre en " + trimmed);
                }
                String body = trimmed.substring(open + 1, trimmed.length() - 1).strip();
                if (!body.isEmpty()) {
                    for (String entry : split(body, ',')) {
                        int colon = entry.indexOf(':');
                        if (colon <= 0 || colon == entry.length() - 1) {
                            throw new IllegalArgumentException("Parámetro de textura inválido: «" + entry.trim()
                                    + "» (se escribe Nombre:valor)");
                        }
                        values.put(key(entry.substring(0, colon)), entry.substring(colon + 1).strip());
                    }
                }
            }
            Arguments arguments = new Arguments(name, values, savedNoises);
            TextureField field = switch (name) {
                case "ambient", "ambiente" -> new TextureFields.Ambient(
                        arguments.integer(3, "radius", "r"),
                        arguments.number(0.0, "brightness", "b"),
                        arguments.number(0.0, "contrast", "c"));
                case "axisgradient", "axis", "gradient", "eje" -> new TextureFields.AxisGradient(
                        axis(arguments.text("y", "axis", "a")),
                        arguments.bool(false, "relative", "rel"));
                case "blocklight", "luzbloque" -> new TextureFields.BlockLight(
                        arguments.bool(false, "vertical", "v"),
                        arguments.bool(false, "skylight", "sky", "s"),
                        arguments.bool(false, "keeplights", "keep", "l"));
                case "cells", "celdas" -> new TextureFields.Cells(
                        arguments.integer(32, "amount", "a"),
                        arguments.number(0.0, "brightness", "b"),
                        arguments.number(0.0, "contrast", "c"),
                        arguments.seed(seed),
                        arguments.integer(5, "repulsion", "r"));
                case "curvature", "curvatura" -> new TextureFields.Curvature(
                        arguments.integer(3, "radius", "r"),
                        arguments.number(0.0, "brightness", "b"),
                        arguments.number(0.0, "contrast", "c"));
                case "flow", "flujo" -> new TextureFields.Flow(
                        arguments.number(0.6, "exposure", "e"),
                        arguments.integer(32, "iterations", "iterationsperline", "i"),
                        arguments.number(0.5, "velocity", "pointvelocity", "v"),
                        arguments.number(1.0, "palettescalar", "paletteindexscalar", "scalar", "p"),
                        arguments.noise("Perlin(Freq:0.03)", "noise", "n"),
                        arguments.number(0.0, "momentum", "m"),
                        arguments.direction(null, "gravity", "g"),
                        arguments.bool(false, "fill", "f"),
                        arguments.seed(seed));
                case "noise", "ruido" -> new TextureFields.Noise(
                        arguments.noise("Perlin(Freq:0.05)", "noise", "n"),
                        arguments.number(1.0, "scale", "z"),
                        arguments.seed(seed));
                case "pointlight", "point", "luzpuntual" -> {
                    double[] interval = interval(arguments.text("0,90", "interval", "i"));
                    String light = arguments.text(null, "light", "position", "l");
                    yield TextureFields.Light.point(
                            light == null ? null : Vectors.vector(light),
                            arguments.number(0.0, "falloffrange", "falloff", "range"),
                            !arguments.bool(false, "nofalloff", "f"),
                            arguments.integer(1, "radius", "r"),
                            interval[0], interval[1],
                            arguments.number(0.0, "occlusion", "o"),
                            arguments.number(1.0, "occlusionradius", "or"));
                }
                case "sunlight", "sun", "sol" -> {
                    double[] interval = interval(arguments.text("0,180", "interval", "i"));
                    yield TextureFields.Light.sun(
                            arguments.direction(new double[]{0, -1, 0}, "light", "direction", "l", "d"),
                            arguments.integer(1, "radius", "r"),
                            interval[0], interval[1],
                            arguments.number(0.0, "occlusion", "o"),
                            arguments.number(1.0, "occlusionradius", "or"));
                }
                case "adjust", "ajustar" -> new TextureFields.Adjust(
                        arguments.texture(this, depth, "texture", "t"),
                        arguments.number(0.0, "brightness", "b"),
                        arguments.number(0.0, "contrast", "c"));
                case "invert", "invertir" -> new TextureFields.Invert(arguments.texture(this, depth, "texture", "t"));
                case "blend", "mezclar" -> new TextureFields.Smooth(
                        arguments.texture(this, depth, "texture", "t"),
                        Math.clamp(arguments.number(0.5, "radius", "r"), 0.0, 4.0));
                default -> combine(name, arguments, depth);
            };
            arguments.checkUnused();
            return field;
        }

        private TextureField combine(String name, Arguments arguments, int depth) {
            TextureFields.Operation operation = switch (name) {
                case "add", "sumar" -> TextureFields.Operation.ADD;
                case "subtract", "restar" -> TextureFields.Operation.SUBTRACT;
                case "multiply", "multiplicar" -> TextureFields.Operation.MULTIPLY;
                case "divide", "dividir" -> TextureFields.Operation.DIVIDE;
                case "weightedaverage", "average", "media" -> TextureFields.Operation.WEIGHTED_AVERAGE;
                case "darken", "oscurecer" -> TextureFields.Operation.DARKEN;
                case "lighten", "aclarar" -> TextureFields.Operation.LIGHTEN;
                case "difference", "diferencia" -> TextureFields.Operation.DIFFERENCE;
                case "screen", "trama" -> TextureFields.Operation.SCREEN;
                default -> throw new IllegalArgumentException("Textura desconocida: «" + name + "». Hay: Ambient, "
                        + "AxisGradient, Blocklight, Cells, Curvature, Flow, Noise, Pointlight, Sunlight, Add, "
                        + "Subtract, Multiply, Divide, WeightedAverage, Darken, Lighten, Difference, Screen, Adjust, "
                        + "Invert y Blend");
            };
            TextureField first = operand(arguments.texture(this, depth, "texture1", "t1"));
            TextureField second = operand(arguments.texture(this, depth, "texture2", "t2"));
            double weight = Math.clamp(arguments.number(0.5, "weight", "w"), 0.0, 1.0);
            return new TextureFields.Combine(operation, first, second, weight);
        }

        /** Dentro de una combinación, el flujo rellena sus huecos con el principio de la paleta. */
        private static TextureField operand(TextureField field) {
            return field instanceof TextureFields.Flow flow ? flow.withFill() : field;
        }
    }

    private static final class Arguments {
        private final String type;
        private final Map<String, String> values;
        private final Function<String, String> savedNoises;
        private final Set<String> used = new java.util.HashSet<>();

        Arguments(String type, Map<String, String> values, Function<String, String> savedNoises) {
            this.type = type;
            this.values = values;
            this.savedNoises = savedNoises;
        }

        private String raw(String... names) {
            for (String name : names) {
                String value = values.get(name);
                if (value != null) {
                    used.add(name);
                    return value;
                }
            }
            return null;
        }

        String text(String fallback, String... names) {
            String value = raw(names);
            return value == null ? fallback : value;
        }

        double number(double fallback, String... names) {
            String value = raw(names);
            return value == null ? fallback : TextureSpec.number(value, names[0]);
        }

        int integer(int fallback, String... names) {
            String value = raw(names);
            return value == null ? fallback : (int) Math.round(TextureSpec.number(value, names[0]));
        }

        boolean bool(boolean fallback, String... names) {
            String value = raw(names);
            return value == null ? fallback : TextureSpec.bool(value, names[0]);
        }

        long seed(long fallback) {
            String value = raw("seed", "s");
            if (value == null) {
                return fallback;
            }
            long seed = (long) TextureSpec.number(value, "seed");
            return seed == -1 ? fallback : seed;
        }

        NoiseSpec noise(String fallback, String... names) {
            String value = raw(names);
            return TextureSpec.noise(value == null ? fallback : value, savedNoises);
        }

        double[] direction(double[] fallback, String... names) {
            String value = raw(names);
            return value == null ? fallback : Vectors.direction(value);
        }

        TextureField texture(Parser parser, int depth, String... names) {
            String value = raw(names);
            if (value == null) {
                throw new IllegalArgumentException("Falta " + names[names.length - 1].toUpperCase(Locale.ROOT)
                        + " (" + names[0] + "): " + type + " necesita esa textura, por ejemplo "
                        + names[names.length - 1].toUpperCase(Locale.ROOT) + ":Ambient()");
            }
            return parser.texture(value, depth + 1);
        }

        void checkUnused() {
            for (String name : values.keySet()) {
                if (!used.contains(name)) {
                    throw new IllegalArgumentException("Parámetro desconocido en " + type + ": «" + name + "»");
                }
            }
        }
    }
}
