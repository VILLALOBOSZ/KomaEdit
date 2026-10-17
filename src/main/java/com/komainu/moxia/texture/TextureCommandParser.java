package com.komainu.moxia.texture;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Traduce {@code /komaedit texture <modo> …} con los mismos argumentos y banderas que {@code //eztexture} de
 * ezEdits. Los argumentos van por posición ({@code <máscara> <paleta> [radio] [brillo] [contraste]}) y las banderas
 * ({@code -w}, {@code -s 42}…) pueden ir en cualquier sitio. Un argumento con espacios va entre comillas; los
 * paréntesis y corchetes agrupan solos ({@code Ambient(Radius:2, Contrast:0.3)}).
 */
public final class TextureCommandParser {
    private TextureCommandParser() {
    }

    /**
     * @param fallbackSeed semilla para los modos con {@code -s -1} o sin semilla
     */
    public static Request parse(String modeToken, String rest, long fallbackSeed) {
        Mode mode = Mode.byId(modeToken);
        Tokens tokens = Tokens.read(rest == null ? "" : rest, mode);
        boolean shaped = tokens.flag("-w");
        double dither = Math.clamp(tokens.number("-d", mode == Mode.BLEND ? 0.5 : 0.0), 0.0, 1.0);
        Request request = switch (mode) {
            case BLEND -> {
                String palette = tokens.required("la paleta");
                double radius = tokens.optionalNumber(0.5, "el radio");
                yield new Request(mode, null, palette, null, shaped, dither,
                        Math.clamp(radius, 0.0, 4.0), tokens.flag("-v"), 0, fallbackSeed);
            }
            case SHIFT -> {
                String palette = tokens.required("la paleta");
                int shift = (int) Math.round(tokens.optionalNumber(1.0, "el desplazamiento"));
                if (shift == 0) {
                    throw new IllegalArgumentException("Desplazar 0 posiciones no cambia nada");
                }
                yield new Request(mode, null, palette, null, true, 0.0, 0.0, false, shift, fallbackSeed);
            }
            default -> {
                String mask = tokens.required("la máscara (por ejemplo #existing)");
                String palette = tokens.required("la paleta (por ejemplo ##Grayscale)");
                long seed = seed(tokens, fallbackSeed);
                TextureField field = field(mode, tokens, seed);
                yield new Request(mode, mask, palette, field, shaped, dither, 0.0, false, 0, seed);
            }
        };
        tokens.finish();
        return request;
    }

    private static long seed(Tokens tokens, long fallbackSeed) {
        if (!tokens.mode.valued.contains("-s")) {
            return fallbackSeed;
        }
        long seed = (long) tokens.number("-s", -1);
        return seed == -1 ? fallbackSeed : seed;
    }

    private static TextureField field(Mode mode, Tokens tokens, long seed) {
        return switch (mode) {
            case AMBIENT -> new TextureFields.Ambient(
                    (int) Math.round(tokens.optionalNumber(3, "el radio")),
                    tokens.optionalNumber(0.0, "el brillo"),
                    tokens.optionalNumber(0.0, "el contraste"));
            case AXIS_GRADIENT -> {
                String axis = tokens.optional("y");
                yield new TextureFields.AxisGradient(TextureSpec.axis(axis), tokens.flag("-r"));
            }
            case BLOCK_LIGHT -> new TextureFields.BlockLight(tokens.flag("-v"), tokens.flag("-s"), tokens.flag("-l"));
            case CELLS -> {
                String amount = tokens.required("el número de celdas");
                yield new TextureFields.Cells(
                        (int) Math.round(TextureSpec.number(amount, "el número de celdas")),
                        tokens.optionalNumber(0.0, "el brillo"),
                        tokens.optionalNumber(0.0, "el contraste"),
                        seed,
                        (int) Math.round(tokens.number("-r", 5)));
            }
            case CURVATURE -> new TextureFields.Curvature(
                    (int) Math.round(tokens.optionalNumber(3, "el radio")),
                    tokens.optionalNumber(0.0, "el brillo"),
                    tokens.optionalNumber(0.0, "el contraste"));
            case FLOW -> {
                double exposure = tokens.optionalNumber(0.6, "la exposición");
                int iterations = (int) Math.round(tokens.optionalNumber(32, "las iteraciones"));
                double velocity = tokens.optionalNumber(0.5, "la velocidad");
                double scalar = tokens.optionalNumber(1.0, "el escalar de paleta");
                String noise = tokens.optional("Perlin(Freq:0.03)");
                double momentum = tokens.flag("-m") ? tokens.number("-m", 0.5) : 0.0;
                double[] gravity = tokens.flag("-g") ? scaled(Vectors.direction(tokens.text("-g", "down")), 0.6) : null;
                yield new TextureFields.Flow(exposure, iterations, velocity, scalar, TextureSpec.noise(noise),
                        momentum, gravity, tokens.flag("-f"), seed);
            }
            case NOISE -> new TextureFields.Noise(
                    TextureSpec.noise(tokens.optional("Perlin(Freq:0.05)")),
                    Math.clamp(tokens.number("-z", 1.0), 0.01, 1_000.0),
                    seed);
            case POINT_LIGHT -> {
                double falloff = tokens.optionalNumber(0.0, "el alcance");
                int radius = (int) Math.round(tokens.optionalNumber(1, "el radio"));
                double[] interval = TextureSpec.interval(tokens.optional("0,90"));
                String light = tokens.text("-l", null);
                yield TextureFields.Light.point(light == null ? null : Vectors.vector(light), falloff,
                        !tokens.flag("-f"), radius, interval[0], interval[1],
                        tokens.number("-o", 0.0), tokens.number("-r", 1.0));
            }
            case SUNLIGHT -> {
                int radius = (int) Math.round(tokens.optionalNumber(1, "el radio"));
                double[] interval = TextureSpec.interval(tokens.optional("0,180"));
                yield TextureFields.Light.sun(Vectors.direction(tokens.text("-l", "down")), radius,
                        interval[0], interval[1], tokens.number("-o", 0.0), tokens.number("-r", 1.0));
            }
            case ADVANCED -> TextureSpec.parse(tokens.required("la textura, por ejemplo Ambient(Radius:2)"), seed);
            case BLEND, SHIFT -> throw new IllegalStateException();
        };
    }

    private static double[] scaled(double[] vector, double factor) {
        return new double[]{vector[0] * factor, vector[1] * factor, vector[2] * factor};
    }

    /** Lo que pide el comando, listo para resolver la máscara y la paleta en el hilo del servidor. */
    public record Request(
            Mode mode,
            String mask,
            String palette,
            TextureField field,
            boolean shaped,
            double dither,
            double blendRadius,
            boolean fullBlend,
            int shift,
            long seed
    ) {
    }

    public enum Mode {
        AMBIENT("ambient", "Textura ambiente", Set.of(), Set.of(), "ambiente"),
        AXIS_GRADIENT("axisgradient", "Degradado por eje", Set.of("-r"), Set.of(), "eje", "degradado"),
        BLEND("blend", "Mezcla de paleta", Set.of("-v"), Set.of(), "mezclar"),
        BLOCK_LIGHT("blocklight", "Textura por luz de bloque", Set.of("-v", "-s", "-l"), Set.of(), "luzbloque"),
        CELLS("cells", "Textura de celdas", Set.of(), Set.of("-s", "-r"), "celdas"),
        CURVATURE("curvature", "Textura por curvatura", Set.of(), Set.of(), "curvatura"),
        FLOW("flow", "Textura de flujo", Set.of("-f"), Set.of(), "flujo"),
        NOISE("noise", "Textura de ruido", Set.of(), Set.of("-z", "-s"), "ruido"),
        POINT_LIGHT("pointlight", "Luz puntual", Set.of("-f"), Set.of("-l", "-o", "-r"), "luzpuntual"),
        SHIFT("shift", "Desplazar paleta", Set.of(), Set.of(), "desplazar"),
        SUNLIGHT("sunlight", "Luz del sol", Set.of(), Set.of("-l", "-o", "-r"), "sol"),
        ADVANCED("advanced", "Textura avanzada", Set.of(), Set.of(), "avanzado", "avanzada");

        /** Banderas sin valor; {@code -m} y {@code -g} del flujo llevan valor opcional. */
        private final Set<String> switches;
        private final Set<String> valued;
        private final String id;
        private final String displayName;
        private final String[] aliases;

        Mode(String id, String displayName, Set<String> switches, Set<String> valued, String... aliases) {
            this.id = id;
            this.displayName = displayName;
            this.switches = switches;
            this.valued = valued;
            this.aliases = aliases;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public boolean usesMask() {
            return this != BLEND && this != SHIFT;
        }

        public static Mode byId(String input) {
            String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
            for (Mode mode : values()) {
                if (mode.id.equals(normalized) || Set.of(mode.aliases).contains(normalized)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Modo de textura desconocido: «" + input + "». Hay: " + ids());
        }

        public static String ids() {
            List<String> ids = new ArrayList<>();
            for (Mode mode : values()) {
                ids.add(mode.id);
            }
            return String.join(", ", ids);
        }
    }

    /** Palabras del comando: posicionales en orden y banderas con o sin valor. */
    static final class Tokens {
        private static final Set<String> COMMON_SWITCHES = Set.of("-w");
        private static final Set<String> COMMON_VALUED = Set.of("-d");
        private final Mode mode;
        private final List<String> positional;
        private final Map<String, String> flags;
        private int next;

        private Tokens(Mode mode, List<String> positional, Map<String, String> flags) {
            this.mode = mode;
            this.positional = positional;
            this.flags = flags;
        }

        static Tokens read(String text, Mode mode) {
            List<String> words = split(text);
            List<String> positional = new ArrayList<>();
            Map<String, String> flags = new java.util.LinkedHashMap<>();
            for (int index = 0; index < words.size(); index++) {
                String word = words.get(index);
                String lower = word.toLowerCase(Locale.ROOT);
                if (!isFlag(lower)) {
                    positional.add(word);
                    continue;
                }
                if (flags.containsKey(lower)) {
                    throw new IllegalArgumentException("La bandera " + word + " está repetida");
                }
                boolean optional = mode == Mode.FLOW && (lower.equals("-m") || lower.equals("-g"));
                if (COMMON_SWITCHES.contains(lower) || mode.switches.contains(lower)) {
                    flags.put(lower, "");
                } else if (COMMON_VALUED.contains(lower) || mode.valued.contains(lower)) {
                    if (index + 1 >= words.size()) {
                        throw new IllegalArgumentException("Falta el valor de " + word);
                    }
                    flags.put(lower, words.get(++index));
                } else if (optional) {
                    String value = "";
                    if (index + 1 < words.size() && optionalValue(lower, words.get(index + 1))) {
                        value = words.get(++index);
                    }
                    flags.put(lower, value);
                } else if (looksLikePalette(word)) {
                    // Paleta invertida (-stone,andesite) o patrón con guion: no es una bandera.
                    positional.add(word);
                } else {
                    throw new IllegalArgumentException("Bandera desconocida para " + mode.id + ": " + word);
                }
            }
            return new Tokens(mode, positional, flags);
        }

        private static boolean optionalValue(String flag, String word) {
            if (flag.equals("-m")) {
                return isNumber(word);
            }
            try {
                Vectors.direction(word);
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        private static boolean looksLikePalette(String word) {
            for (char character : ",;#[(:_".toCharArray()) {
                if (word.indexOf(character) >= 0) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isFlag(String word) {
            return word.length() > 1 && word.charAt(0) == '-' && Character.isLetter(word.charAt(1));
        }

        private static boolean isNumber(String word) {
            try {
                Double.parseDouble(word);
                return true;
            } catch (NumberFormatException exception) {
                return false;
            }
        }

        /** Parte por espacios respetando comillas, paréntesis y corchetes. */
        static List<String> split(String text) {
            List<String> words = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            int depth = 0;
            boolean quoted = false;
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                if (character == '"') {
                    quoted = !quoted;
                    continue;
                }
                if (!quoted) {
                    if (character == '(' || character == '[' || character == '{') {
                        depth++;
                    } else if ((character == ')' || character == ']' || character == '}') && depth > 0) {
                        depth--;
                    } else if (Character.isWhitespace(character) && depth == 0) {
                        if (!current.isEmpty()) {
                            words.add(current.toString());
                            current.setLength(0);
                        }
                        continue;
                    }
                }
                current.append(character);
            }
            if (quoted) {
                throw new IllegalArgumentException("Faltan las comillas de cierre");
            }
            if (!current.isEmpty()) {
                words.add(current.toString());
            }
            return words;
        }

        String required(String what) {
            if (next >= positional.size()) {
                throw new IllegalArgumentException("Falta " + what);
            }
            return positional.get(next++);
        }

        String optional(String fallback) {
            return next < positional.size() ? positional.get(next++) : fallback;
        }

        double optionalNumber(double fallback, String what) {
            return next < positional.size() ? TextureSpec.number(positional.get(next++), what) : fallback;
        }

        boolean flag(String name) {
            return flags.containsKey(name);
        }

        String text(String name, String fallback) {
            String value = flags.get(name);
            return value == null || value.isEmpty() ? fallback : value;
        }

        double number(String name, double fallback) {
            String value = flags.get(name);
            return value == null || value.isEmpty() ? fallback : TextureSpec.number(value, name);
        }

        void finish() {
            if (next < positional.size()) {
                throw new IllegalArgumentException("Sobra «" + String.join(" ", positional.subList(next,
                        positional.size())) + "»");
            }
        }
    }
}
