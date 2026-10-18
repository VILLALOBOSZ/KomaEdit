package com.komainu.moxia.shape.tube;

import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Lee la sintaxis de {@code //ezspline} de ezEdits y la traduce a las figuras de espacio de tubo de KomaEdit:
 *
 * <pre>
 * 2d &lt;forma(parámetros)&gt; &lt;patrón&gt; &lt;radios&gt;     3d &lt;forma(parámetros)&gt; &lt;patrón&gt; &lt;radios&gt;
 * basic &lt;patrón&gt; &lt;radios&gt;                    rope &lt;patrón&gt; &lt;radios&gt;
 * noise &lt;paleta&gt; &lt;radios&gt; [ruido] [profundidad] [-i expresión]
 * expression &lt;paleta&gt; &lt;radios&gt; [-z] [-o] &lt;expresión…&gt;
 * structure &lt;estructura&gt; [radio] [-z]
 * </pre>
 *
 * <p>Las banderas comunes ({@code -p t:b:c}, {@code -q}, {@code -r a[,b]}, {@code -s}, {@code -t}, {@code -n},
 * {@code -e}, {@code -w} y {@code -h}) pueden ir en cualquier sitio; en {@code expression}, antes de la expresión.
 * Los nombres de formas, parámetros y modos no distinguen mayúsculas y admiten la abreviatura de la wiki
 * ({@code Chainlink(E:0.2,T:1,G:0,M:3,N:3,P:BOTH)} es {@code Ch(...)}).</p>
 */
public final class EzSpline {
    /** Ruido del spline de ruido cuando no se escribe ninguno: el mismo valor por defecto que en ezEdits. */
    public static final String DEFAULT_NOISE = "Perlin(Freq:2,z:0.5)";
    public static final double DEFAULT_NOISE_DEPTH = 0.7;

    private EzSpline() {
    }

    /** Qué hay que hacer con lo leído. */
    public enum Kind {
        /** Figura de tubo con un patrón de WorldEdit ({@code 2d}, {@code 3d}, {@code basic}, {@code rope}). */
        PATTERN,
        /** Figura de tubo cuyo valor implícito elige la entrada de una paleta ({@code noise}, {@code expression}). */
        PALETTE,
        /** Estructura doblada a lo largo del recorrido. */
        STRUCTURE,
        /** Página de ayuda ({@code -h} o sin argumentos). */
        HELP
    }

    /**
     * @param parameters figura de tubo (nula en estructuras y ayuda)
     * @param material patrón de WorldEdit o paleta
     * @param indexed {@code -o}: la salida de la expresión cuenta entradas de paleta (1…P) en vez de ir de 0 a 1
     * @param structure estructura y su doblado (solo en {@link Kind#STRUCTURE})
     * @param help líneas de ayuda (solo en {@link Kind#HELP})
     */
    public record Request(
            Kind kind,
            String subcommand,
            ShapeParameters parameters,
            String material,
            boolean indexed,
            Structure structure,
            List<String> help
    ) {
        /** Fija cuántas entradas tiene la paleta para la salida entera de {@code -o}. */
        public Request withPaletteSize(int size) {
            if (!indexed || parameters == null) {
                return this;
            }
            Map<String, Double> options = new LinkedHashMap<>(parameters.options().values());
            options.put("paletteentries", (double) Math.max(1, size));
            return new Request(kind, subcommand, parameters.withOptions(
                    new ShapeOptions(options, parameters.options().formula())), material, true, structure, help);
        }

        public String describe() {
            return "Spline " + subcommand;
        }
    }

    /**
     * @param source estructura en la notación de colocación ({@code Clipboard}, {@code Schematic(...)}, {@code TS(...)})
     * @param radius radio al que se escala la sección de la estructura; {@code NaN} para su tamaño propio
     * @param stretch {@code -s}: estira (o encoge) la estructura a lo largo del recorrido
     * @param bend modo (repetir o, con {@code -z}, una sola copia estirada), normales y curva
     */
    public record Structure(String source, double radius, double stretch, TubeBender.Settings bend) {
    }

    /**
     * @param savedNoises busca los ruidos guardados del jugador por nombre en minúsculas; {@code null} si no hay
     */
    public static Request parse(String input, Function<String, String> savedNoises) {
        List<Token> tokens = tokenize(input == null ? "" : input);
        if (tokens.isEmpty()) {
            return help(null);
        }
        String subcommand = tokens.getFirst().text().toLowerCase(Locale.ROOT);
        List<Token> rest = tokens.subList(1, tokens.size());
        return switch (subcommand) {
            case "2d" -> twoDimensional(rest, null, "2d");
            case "3d" -> threeDimensional(rest, null);
            case "basic", "basico", "básico" -> twoDimensional(rest, Shape2D.CIRCLE, "basic");
            case "rope", "cuerda" -> twoDimensional(rest, Shape2D.CIRCLES_CIRCLE, "rope");
            case "noise", "ruido" -> noise(rest, savedNoises);
            case "expression", "expr", "expresion", "expresión" -> expression(input, rest);
            case "structure", "struct", "estructura" -> structure(rest);
            case "-h", "help", "ayuda" -> help(rest.isEmpty() ? null : rest.getFirst().text());
            default -> legacy(tokens.getFirst().text(), rest);
        };
    }

    /**
     * Versiones antiguas de ezEdits (y algunos ejemplos de su wiki) usan la forma como subcomando:
     * {@code //ezsp polygon clay 10 4} o {@code //ezspline beads clay 10}.
     */
    private static Request legacy(String name, List<Token> rest) {
        for (Shape2D shape : Shape2D.values()) {
            if (shape.is(name)) {
                return twoDimensional(rest, shape, shape.displayName.toLowerCase(Locale.ROOT));
            }
        }
        for (Shape3D shape : Shape3D.values()) {
            if (shape.is(name)) {
                return threeDimensional(rest, shape);
            }
        }
        throw new IllegalArgumentException("Subcomando de spline desconocido: " + name
                + " (2d, 3d, basic, rope, noise, expression, structure)");
    }

    // ---------------------------------------------------------------- Subcomandos

    private static Request twoDimensional(List<Token> tokens, Shape2D preset, String name) {
        Arguments arguments = Arguments.read(tokens, "-h");
        boolean rope = name.equals("rope");
        if (arguments.has("-h")) {
            return help("2d");
        }
        List<String> positional = new ArrayList<>(arguments.positional());
        int expected = preset == null ? 3 : 2;
        if (positional.size() < expected) {
            throw new IllegalArgumentException(preset != null
                    ? "Uso: //ezspline " + name + " <patrón> <radios> [banderas]"
                    : "Uso: //ezspline 2d <forma(parámetros)> <patrón> <radios> [banderas] · formas: "
                    + Shape2D.names());
        }
        Spec spec = preset == null ? Spec.parse(positional.getFirst()) : new Spec(preset.displayName, Map.of());
        Shape2D shape = preset == null ? Shape2D.find(spec.name()) : preset;
        Map<Parameter, Double> values = spec.values(shape.parameters, shape.displayName);
        if (positional.size() == expected + 1 && preset != null && shape.parameters.length > 0
                && parseNumber(positional.get(expected)) != null) {
            // Sintaxis antigua: el número de detrás es el de lados o círculos (//ezsp rope ice 15 6).
            Parameter count = shape.parameters[0];
            values.put(count, count.read(shape.displayName, positional.remove(expected)));
        }
        if (positional.size() > expected) {
            throw new IllegalArgumentException("Sobra el texto «" + positional.get(expected) + "»");
        }
        Map<String, Double> options = new LinkedHashMap<>();
        shape.translate(values, options);
        if (!rope && !arguments.has("-t") && shape == Shape2D.CIRCLES_CIRCLE) {
            // La cuerda gira 90° por diámetro; el círculo de círculos de 2d no gira salvo que se pida.
            options.put("twist", 0.0);
        }
        String material = positional.get(expected - 2);
        String radii = positional.get(expected - 1);
        ShapeParameters parameters = tube(PathShape.SWEEP, radii, arguments, options, "");
        return new Request(Kind.PATTERN, preset == null ? "2d " + shape.displayName : name, parameters, material,
                false, null, List.of());
    }

    private static Request threeDimensional(List<Token> tokens, Shape3D preset) {
        Arguments arguments = Arguments.read(tokens, "-h");
        if (arguments.has("-h")) {
            return help("3d");
        }
        List<String> positional = new ArrayList<>(arguments.positional());
        if (preset != null) {
            positional.addFirst(preset.displayName);
        }
        if (positional.size() < 3) {
            throw new IllegalArgumentException("Uso: //ezspline 3d <forma(parámetros)> <patrón> <radios> [banderas]"
                    + " · formas: " + Shape3D.names());
        }
        if (positional.size() > 3) {
            throw new IllegalArgumentException("Sobra el texto «" + positional.get(3) + "»");
        }
        Spec spec = Spec.parse(positional.getFirst());
        Shape3D shape = Shape3D.find(spec.name());
        Map<String, Double> options = new LinkedHashMap<>();
        PathShape figure = shape.translate(spec.values(shape.parameters, shape.displayName), options);
        ShapeParameters parameters = tube(figure, positional.get(2), arguments, options, "");
        return new Request(Kind.PATTERN, "3d " + shape.displayName, parameters, positional.get(1), false, null,
                List.of());
    }

    private static Request noise(List<Token> tokens, Function<String, String> savedNoises) {
        Arguments arguments = Arguments.read(tokens, "-h", "-i");
        if (arguments.has("-h")) {
            return help("noise");
        }
        List<String> positional = arguments.positional();
        if (positional.size() < 2) {
            throw new IllegalArgumentException("Uso: //ezspline noise <paleta> <radios> [ruido] [profundidad]"
                    + " [-i expresión] [banderas]");
        }
        String noiseText = DEFAULT_NOISE;
        double depth = DEFAULT_NOISE_DEPTH;
        boolean noiseSeen = false;
        boolean depthSeen = false;
        for (String extra : positional.subList(2, positional.size())) {
            Double number = parseNumber(extra);
            if (number != null && !depthSeen) {
                if (!(number > 0.0) || number > 8.0) {
                    throw new IllegalArgumentException("La profundidad del ruido debe estar entre 0 y 8");
                }
                depth = number;
                depthSeen = true;
            } else if (!noiseSeen && !depthSeen) {
                noiseText = extra;
                noiseSeen = true;
            } else {
                throw new IllegalArgumentException("Sobra el texto «" + extra + "»: el ruido va antes de la "
                        + "profundidad, cada uno en una palabra");
            }
        }
        String resolved = NoiseSpec.parse(noiseText, savedNoises).toString();
        String cut = arguments.value("-i");
        if (cut != null) {
            TubeFormula.compileNoiseCut(cut);
        }
        String packed = EzShapes.NoiseCut.pack(resolved, cut);
        if (packed.length() > ShapeOptions.MAX_FORMULA_LENGTH) {
            throw new IllegalArgumentException("El ruido y la expresión -i juntos superan "
                    + ShapeOptions.MAX_FORMULA_LENGTH + " caracteres");
        }
        Map<String, Double> options = new LinkedHashMap<>();
        options.put(EzShapes.FORM, EzShapes.Form.NOISE.option());
        options.put("noisedepth", depth);
        ShapeParameters parameters = tube(PathShape.SWEEP, positional.get(1), arguments, options, packed);
        return new Request(Kind.PALETTE, "noise", parameters, positional.getFirst(), false, null, List.of());
    }

    private static Request expression(String input, List<Token> tokens) {
        // La expresión es todo lo que sigue al primer texto que no es bandera tras la paleta y los radios.
        int index = 0;
        List<Token> leading = new ArrayList<>();
        int positionals = 0;
        String expression = null;
        while (index < tokens.size()) {
            Token token = tokens.get(index);
            Flag flag = Flag.find(token.text());
            if (flag != null) {
                leading.add(token);
                if (flag.takesValue) {
                    if (index + 1 >= tokens.size()) {
                        throw new IllegalArgumentException("Falta el valor de " + token.text());
                    }
                    leading.add(tokens.get(index + 1));
                    index += 2;
                } else {
                    index++;
                }
                continue;
            }
            if (positionals < 2) {
                leading.add(token);
                positionals++;
                index++;
                continue;
            }
            expression = input.substring(token.start()).strip();
            break;
        }
        Arguments arguments = Arguments.read(leading, "-h", "-z", "-o");
        if (arguments.has("-h")) {
            return help("expression");
        }
        List<String> positional = arguments.positional();
        if (positional.size() < 2 || expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException("Uso: //ezspline expression <paleta> <radios> [-z] [-o] [banderas]"
                    + " <expresión…>");
        }
        TubeFormula.compile(expression);
        Map<String, Double> options = new LinkedHashMap<>();
        if (arguments.has("-z")) {
            options.put("znormal", 1.0);
        }
        ShapeParameters parameters = tube(PathShape.EXPRESSION, positional.get(1), arguments, options, expression);
        return new Request(Kind.PALETTE, "expression", parameters, positional.getFirst(), arguments.has("-o"), null,
                List.of());
    }

    private static Request structure(List<Token> tokens) {
        Arguments arguments = Arguments.read(tokens, "-h", "-z");
        if (arguments.has("-h")) {
            return help("structure");
        }
        for (String unsupported : List.of("-t", "-r", "-e", "-w", "-c")) {
            if (arguments.has(unsupported)) {
                throw new IllegalArgumentException("La bandera " + unsupported + " no se aplica a //ezspline "
                        + "structure: la estructura se dobla bloque a bloque y conserva su forma");
            }
        }
        List<String> positional = arguments.positional();
        if (positional.isEmpty()) {
            throw new IllegalArgumentException("Uso: //ezspline structure <estructura> [radio] [-z] [-s] [-n] [-p]");
        }
        if (positional.size() > 2) {
            throw new IllegalArgumentException("Sobra el texto «" + positional.get(2) + "»");
        }
        double radius = Double.NaN;
        if (positional.size() == 2) {
            String text = positional.get(1);
            if (text.matches(".*[,_/;].*")) {
                throw new IllegalArgumentException("//ezspline structure admite un solo radio (" + text + ")");
            }
            Double value = parseNumber(text.contains(":") ? text.substring(text.indexOf(':') + 1) : text);
            if (value == null || !(value > 0.0) || value > 128.0) {
                throw new IllegalArgumentException("El radio de la estructura debe estar entre 0 y 128: " + text);
            }
            radius = value;
        }
        double stretch = arguments.has("-s") ? number(arguments.value("-s"), 0.1, 10.0, "-s") : 1.0;
        TubeSettings.NormalMode normal = arguments.has("-n")
                ? TubeSettings.NormalMode.byId(arguments.value("-n"))
                : TubeSettings.NormalMode.CONSISTENT;
        double[] flow = arguments.has("-p") ? kochanekBartels(arguments.value("-p")) : new double[3];
        TubeBender.Settings bend = new TubeBender.Settings(
                arguments.has("-z") ? TubeBender.Mode.STRETCH : TubeBender.Mode.REPEAT,
                TubeBender.Axis.X,
                normal,
                0,
                0,
                flow[0],
                flow[1],
                flow[2]
        );
        return new Request(Kind.STRUCTURE, "structure", null, null, false,
                new Structure(positional.getFirst(), radius, stretch, bend), List.of());
    }

    /** Pasa radios y banderas comunes por el mismo lector que {@code /komaedit sweep} y añade la forma. */
    private static ShapeParameters tube(PathShape shape, String radii, Arguments arguments,
                                        Map<String, Double> shapeOptions, String formula) {
        TubeCommandParser.Result parsed = TubeCommandParser.parse(shape, null, radii, arguments.tubeFlags(), "stone");
        Map<String, Double> options = new LinkedHashMap<>(parsed.parameters().options().values());
        shapeOptions.forEach((key, value) -> {
            // Una bandera escrita gana al valor que trae la forma (p. ej. -t sobre la cuerda sin giro de 2d).
            if (!options.containsKey(key) || !key.equals("twist")) {
                options.put(key, value);
            }
        });
        if (options.size() > ShapeOptions.MAX_ENTRIES) {
            throw new IllegalArgumentException("Demasiados parámetros a la vez (máximo " + ShapeOptions.MAX_ENTRIES
                    + ")");
        }
        return parsed.parameters().withOptions(new ShapeOptions(options, formula));
    }

    // ---------------------------------------------------------------- Formas

    /** Parámetro de forma: nombre largo, abreviatura, opción de receta, rango y valor por defecto. */
    record Parameter(String name, String[] shortNames, String option, double minimum, double maximum,
                     double fallback, ValueKind kind) {
        static Parameter number(String name, String shortName, String option, double minimum, double maximum,
                                double fallback) {
            return new Parameter(name, new String[]{shortName}, option, minimum, maximum, fallback, ValueKind.NUMBER);
        }

        static Parameter flag(String name, String shortName, String option) {
            return new Parameter(name, new String[]{shortName}, option, 0.0, 1.0, 0.0, ValueKind.BOOLEAN);
        }

        Parameter withAlias(String alias) {
            String[] names = java.util.Arrays.copyOf(shortNames, shortNames.length + 1);
            names[shortNames.length] = alias;
            return new Parameter(name, names, option, minimum, maximum, fallback, kind);
        }

        Parameter as(ValueKind other) {
            return new Parameter(name, shortNames, option, minimum, maximum, fallback, other);
        }

        boolean matches(String key) {
            String normalized = normalize(key);
            if (normalize(name).equals(normalized)) {
                return true;
            }
            for (String shortName : shortNames) {
                if (normalize(shortName).equals(normalized)) {
                    return true;
                }
            }
            return false;
        }

        double read(String shape, String raw) {
            String value = raw.trim();
            return switch (kind) {
                case BOOLEAN -> switch (value.toLowerCase(Locale.ROOT)) {
                    case "true", "t", "yes", "si", "sí", "on", "1" -> 1.0;
                    case "false", "f", "no", "off", "0" -> 0.0;
                    default -> throw new IllegalArgumentException(shape + ": " + name + " espera true o false, no "
                            + value);
                };
                case PLACE -> switch (value.toLowerCase(Locale.ROOT)) {
                    case "both", "ambos", "0" -> 0.0;
                    case "first", "primero", "1" -> 1.0;
                    case "second", "segundo", "2" -> 2.0;
                    default -> throw new IllegalArgumentException(shape + ": " + name
                            + " espera FIRST, SECOND o BOTH, no " + value);
                };
                case PERCENT -> {
                    boolean percent = value.endsWith("%");
                    Double number = parseNumber(percent ? value.substring(0, value.length() - 1) : value);
                    if (number == null) {
                        throw new IllegalArgumentException(shape + ": valor inválido para " + name + ": " + value);
                    }
                    yield check(shape, percent || number > 1.0 ? number / 100.0 : number);
                }
                case NUMBER -> {
                    Double number = parseNumber(value);
                    if (number == null) {
                        throw new IllegalArgumentException(shape + ": valor inválido para " + name + ": " + value);
                    }
                    yield check(shape, number);
                }
            };
        }

        private double check(String shape, double value) {
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(shape + ": " + name + " debe estar entre " + format(minimum)
                        + " y " + format(maximum));
            }
            return value;
        }

        String describe() {
            return name + " (" + String.join("/", shortNames) + ")";
        }
    }

    enum ValueKind {
        NUMBER, BOOLEAN, PLACE, PERCENT
    }

    /** {@code Nombre(clave:valor,…)} tal como se escribe, sin interpretar. */
    record Spec(String name, Map<String, String> raw) {
        static Spec parse(String text) {
            String trimmed = text.trim();
            int open = trimmed.indexOf('(');
            if (open < 0) {
                return new Spec(trimmed, Map.of());
            }
            if (!trimmed.endsWith(")")) {
                throw new IllegalArgumentException("Falta el paréntesis de cierre en " + trimmed);
            }
            Map<String, String> values = new LinkedHashMap<>();
            String body = trimmed.substring(open + 1, trimmed.length() - 1).trim();
            if (!body.isEmpty()) {
                for (String entry : body.split(",")) {
                    String[] pair = entry.split(":", 2);
                    if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                        throw new IllegalArgumentException("Parámetro inválido en " + trimmed + ": " + entry.trim()
                                + " (se escribe clave:valor)");
                    }
                    values.put(pair[0].trim(), pair[1].trim());
                }
            }
            return new Spec(trimmed.substring(0, open).trim(), values);
        }

        /** Valores numéricos por parámetro (con los no escritos ausentes). */
        Map<Parameter, Double> values(Parameter[] parameters, String shape) {
            Map<Parameter, Double> values = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                Parameter match = null;
                for (Parameter parameter : parameters) {
                    if (parameter.matches(key)) {
                        match = parameter;
                        break;
                    }
                }
                if (match == null) {
                    List<String> names = new ArrayList<>();
                    for (Parameter parameter : parameters) {
                        names.add(parameter.describe());
                    }
                    throw new IllegalArgumentException(shape + " no tiene el parámetro «" + key + "»"
                            + (names.isEmpty() ? " (no admite parámetros)" : " (" + String.join(", ", names) + ")"));
                }
                values.put(match, match.read(shape, value));
            });
            return values;
        }
    }

    private static final Parameter SIDES = Parameter.number("Sides", "S", "sides", 3, 32, 5);
    private static final Parameter DEPTH_2D = Parameter.number("Depth", "D", "depthmultiplier", 0.0, 0.95, 0.5);

    /** Formas de {@code //ezspline 2d}: perfiles de {@code sweep} con los parámetros de ezEdits. */
    enum Shape2D {
        CIRCLE("Circle", "Ci"),
        SQUARE("Square", "Sq"),
        DIAMOND("Diamond", "Di"),
        ROUNDED_SQUARE("RoundedSquare", "RS"),
        SUPER_CIRCLE("SuperCircle", "SC", Parameter.number("Exponent", "E", "exponent", 0.1, 200, 2)),
        CIRCLES_CIRCLE("CirclesCircle", "CC", Parameter.number("Count", "C", "sides", 1, 12, 3),
                Parameter.flag("Filled", "F", "filled")),
        STRANDS("Strands", "Sr", Parameter.number("Count", "C", "sides", 1, 12, 2),
                Parameter.number("Size", "S", "strandsize", 0.05, 1, 0.5)),
        PIPE("Pipe", "Pi", Parameter.number("Inner", "I", "inner", 0, 0.98, 0.8)),
        POLYGON("Polygon", "Po", SIDES, Parameter.number("Depth", "D", "depthmultiplier", 0.0, 0.95, 0.0)),
        RECTANGLE("Rectangle", "Re", Parameter.number("X1", "X1", "x1", -1, 1, -1),
                Parameter.number("Y1", "Y1", "y1", -1, 1, -1), Parameter.number("X2", "X2", "x2", -1, 1, 1),
                Parameter.number("Y2", "Y2", "y2", -1, 1, 1)),
        STAR("Star", "St", SIDES, DEPTH_2D),
        FLOWER("Flower", "Fl", Parameter.number("Count", "C", "sides", 2, 32, 5).withAlias("S"), DEPTH_2D);

        final String displayName;
        final String shortName;
        final Parameter[] parameters;

        Shape2D(String displayName, String shortName, Parameter... parameters) {
            this.displayName = displayName;
            this.shortName = shortName;
            this.parameters = parameters;
        }

        boolean is(String name) {
            String normalized = normalize(name);
            return normalize(displayName).equals(normalized) || normalize(shortName).equals(normalized);
        }

        static Shape2D find(String name) {
            for (Shape2D shape : values()) {
                if (shape.is(name)) {
                    return shape;
                }
            }
            throw new IllegalArgumentException("Forma 2D desconocida: " + name + " (" + names() + ")");
        }

        static String names() {
            List<String> names = new ArrayList<>();
            for (Shape2D shape : values()) {
                names.add(shape.displayName + " (" + shape.shortName + ")");
            }
            return String.join(", ", names);
        }

        void translate(Map<Parameter, Double> values, Map<String, Double> options) {
            TubeSettings.TubeProfile profile = switch (this) {
                case CIRCLE, PIPE -> TubeSettings.TubeProfile.CIRCLE;
                case SQUARE -> TubeSettings.TubeProfile.SQUARE;
                case DIAMOND -> TubeSettings.TubeProfile.DIAMOND;
                case ROUNDED_SQUARE, SUPER_CIRCLE -> TubeSettings.TubeProfile.ROUNDED;
                case CIRCLES_CIRCLE, STRANDS -> TubeSettings.TubeProfile.ROPE;
                case POLYGON -> TubeSettings.TubeProfile.POLYGON;
                case RECTANGLE -> TubeSettings.TubeProfile.RECTANGLE;
                case STAR -> TubeSettings.TubeProfile.STAR;
                case FLOWER -> TubeSettings.TubeProfile.FLOWER;
            };
            for (Parameter parameter : parameters) {
                options.put(parameter.option, values.getOrDefault(parameter, parameter.fallback));
            }
            switch (this) {
                case ROUNDED_SQUARE -> options.put("exponent", 4.0);
                case POLYGON -> {
                    // Polygon(D) hunde el centro de cada lado: con profundidad es la estrella de esos lados.
                    if (options.get("depthmultiplier") > 0.0) {
                        profile = TubeSettings.TubeProfile.STAR;
                    } else {
                        options.remove("depthmultiplier");
                    }
                }
                case STAR -> {
                    if (options.get("depthmultiplier") <= 0.0) {
                        profile = TubeSettings.TubeProfile.POLYGON;
                        options.remove("depthmultiplier");
                    }
                }
                case STRANDS -> options.put("filled", 0.0);
                default -> {
                }
            }
            options.put("profile", (double) profile.ordinal());
        }
    }

    private static final Parameter EXTRUSION = Parameter.number("Extrusion", "E", "extrusion", -0.9, 10, 0.2);
    private static final Parameter GAP = Parameter.number("Gap", "G", "gap", -0.95, 20, 0);
    private static final Parameter MAJOR = Parameter.number("MajorExponent", "M", "majorexponent", 0.2, 200, 2);
    private static final Parameter MINOR = Parameter.number("MinorExponent", "N", "minorexponent", 0.2, 200, 2);

    /** Formas de {@code //ezspline 3d}. */
    enum Shape3D {
        BEADS("Beads", "Be"),
        CUBES("Cubes", "Cu", Parameter.number("Gap", "G", "gap", -0.9, 20, 0.5)),
        BRAIDS("Braids", "Br", Parameter.number("Thickness", "T", "thickness", 0.05, 1, 0.5),
                Parameter.number("Gap", "G", "gap", -0.5, 4, 0)),
        CHAINLINK("Chainlink", "Ch", Parameter.number("Extrusion", "E", "extrusion", -0.8, 5, 0.2),
                Parameter.number("Thickness", "T", "thickness", 0.05, 3.6, 1),
                Parameter.number("Gap", "G", "gap", -0.9, 10, 0),
                Parameter.number("MajorExponent", "M", "majorexponent", 0.5, 200, 3),
                Parameter.number("MinorExponent", "N", "minorexponent", 0.5, 200, 3),
                new Parameter("Place", new String[]{"P"}, "place", 0, 2, 0, ValueKind.PLACE)),
        FISHNET("Fishnet", "Fi", Parameter.number("Spacing", "S", "spacing", 0.1, Math.PI * 2.0, 1),
                Parameter.number("Depth", "D", "depth", 0.01, 1, 0.2),
                Parameter.number("Width", "W", "width", 0.01, 2, 0.2)),
        HONEYCOMB("Honeycomb", "Ho", Parameter.number("CellSize", "C", "cellsize", 0.03, 3, 0.25),
                Parameter.number("WireWidth", "W", "wirewidth", 0.005, 1, 0.06),
                Parameter.number("Depth", "D", "depth", 0.01, 1, 0.2)),
        OSCILLATE("Oscillate", "Os", Parameter.number("Depth", "D", "depth", 0, 0.95, 0.2),
                Parameter.number("Interval", "I", "interval", 0.05, 50, 0.5)),
        RINGS("Rings", "Ri", EXTRUSION, Parameter.number("Thickness", "T", "thickness", 0.02, 1, 0.15), GAP, MAJOR,
                MINOR),
        SCALES("Scales", "Sc", Parameter.number("Columns", "C", "columns", 2, 64, 8).withAlias("Scale"),
                Parameter.number("HorizontalOffset", "H", "horizontaloffset", 0.2, 5, 1.05),
                Parameter.number("VerticalOffset", "V", "verticaloffset", 0.1, 5, 1.2),
                Parameter.number("MajorExponent", "M", "majorexponent", 0.3, 200, 1.4),
                Parameter.number("MinorExponent", "N", "minorexponent", 0.3, 200, 14),
                Parameter.number("Angle", "A", "tileangle", 0, 80, 14),
                Parameter.number("DepthMultiplier", "D", "depthmultiplier", 0, 3, 1)),
        NOODLES("Noodles", "No", Parameter.number("Amount", "A", "amount", 1, 64, 12),
                Parameter.number("Density", "D", "density", 0.05, 1, 0.7).as(ValueKind.PERCENT),
                Parameter.number("Frequency", "F", "frequency", 0.01, 10, 0.5),
                Parameter.number("Tangle", "T", "tangle", 0, 20, 3),
                Parameter.number("Width", "W", "width", 0.05, 3, 0.8),
                Parameter.number("Seed", "S", "noodleseed", -1, 2.0E9, -1));

        final String displayName;
        final String shortName;
        final Parameter[] parameters;

        Shape3D(String displayName, String shortName, Parameter... parameters) {
            this.displayName = displayName;
            this.shortName = shortName;
            this.parameters = parameters;
        }

        boolean is(String name) {
            String normalized = normalize(name);
            return normalize(displayName).equals(normalized) || normalize(shortName).equals(normalized);
        }

        static Shape3D find(String name) {
            for (Shape3D shape : values()) {
                if (shape.is(name)) {
                    return shape;
                }
            }
            throw new IllegalArgumentException("Forma 3D desconocida: " + name + " (" + names() + ")");
        }

        static String names() {
            List<String> names = new ArrayList<>();
            for (Shape3D shape : values()) {
                names.add(shape.displayName + " (" + shape.shortName + ")");
            }
            return String.join(", ", names);
        }

        /** Escribe las opciones y devuelve la figura de KomaEdit que la genera. */
        PathShape translate(Map<Parameter, Double> values, Map<String, Double> options) {
            if (this == BRAIDS) {
                // La trenza de KomaEdit: el grosor de ezEdits es relativo (0,5 por defecto) y el hueco abre los cruces.
                double thickness = values.getOrDefault(parameters[0], parameters[0].fallback);
                double gap = values.getOrDefault(parameters[1], parameters[1].fallback);
                options.put("thickness", Math.clamp(thickness * 0.6, 0.08, 0.6));
                options.put("sides", (double) Math.clamp(Math.round(4.0 * (1.0 + gap)), 2L, 32L));
                options.put("strands", 3.0);
                return PathShape.BRAID;
            }
            EzShapes.Form form = switch (this) {
                case BEADS -> EzShapes.Form.BEADS;
                case CUBES -> EzShapes.Form.CUBES;
                case CHAINLINK -> EzShapes.Form.CHAINLINK;
                case FISHNET -> EzShapes.Form.FISHNET;
                case HONEYCOMB -> EzShapes.Form.HONEYCOMB;
                case OSCILLATE -> EzShapes.Form.OSCILLATE;
                case RINGS -> EzShapes.Form.RINGS;
                case SCALES -> EzShapes.Form.SCALES;
                case NOODLES -> EzShapes.Form.NOODLES;
                case BRAIDS -> throw new IllegalStateException();
            };
            options.put(EzShapes.FORM, form.option());
            values.forEach((parameter, value) -> options.put(parameter.option, value));
            return PathShape.SWEEP;
        }
    }

    // ---------------------------------------------------------------- Banderas

    /** Banderas que entiende {@code //ezspline}; las de valor se pasan al lector de tubos. */
    enum Flag {
        KB("-p", true), QUALITY("-q", true), ROLL("-r", true), STRETCH("-s", true), TWIST("-t", true),
        NORMAL("-n", true), END("-e", true), SMOOTH("-w", true), COVERAGE("-c", true), SEED("-seed", true),
        CUT("-i", true), HELP("-h", false), NORMALIZE_Z("-z", false), INDEXED("-o", false);

        final String name;
        final boolean takesValue;

        Flag(String name, boolean takesValue) {
            this.name = name;
            this.takesValue = takesValue;
        }

        static Flag find(String token) {
            String lower = token.toLowerCase(Locale.ROOT);
            for (Flag flag : values()) {
                if (flag.name.equals(lower)) {
                    return flag;
                }
            }
            return null;
        }
    }

    /** Banderas y textos posicionales en el orden escrito. */
    private record Arguments(Map<String, String> flags, List<String> positional) {
        static Arguments read(List<Token> tokens, String... allowedSwitches) {
            Map<String, String> flags = new LinkedHashMap<>();
            List<String> positional = new ArrayList<>();
            List<String> switches = List.of(allowedSwitches);
            for (int index = 0; index < tokens.size(); index++) {
                String text = tokens.get(index).text();
                Flag flag = Flag.find(text);
                if (flag == null) {
                    if (isFlagLike(text)) {
                        throw new IllegalArgumentException("Bandera desconocida: " + text
                                + " (-p, -q, -r, -s, -t, -n, -e, -w, -h)");
                    }
                    positional.add(text);
                    continue;
                }
                if (!flag.takesValue) {
                    if (!switches.contains(flag.name)) {
                        throw new IllegalArgumentException("La bandera " + flag.name + " no se usa aquí");
                    }
                    flags.put(flag.name, "");
                    continue;
                }
                if (index + 1 >= tokens.size()) {
                    throw new IllegalArgumentException("Falta el valor de " + text);
                }
                if (flag == Flag.CUT && !switches.contains("-i")) {
                    throw new IllegalArgumentException("-i solo se usa en //ezspline noise");
                }
                flags.put(flag.name, tokens.get(++index).text());
            }
            return new Arguments(flags, positional);
        }

        boolean has(String flag) {
            return flags.containsKey(flag);
        }

        String value(String flag) {
            return flags.get(flag);
        }

        /** Las banderas de valor que entiende {@link TubeCommandParser}, con la calidad numérica antigua traducida. */
        String tubeFlags() {
            StringBuilder builder = new StringBuilder();
            flags.forEach((flag, value) -> {
                if (flag.equals("-h") || flag.equals("-z") || flag.equals("-o") || flag.equals("-i")) {
                    return;
                }
                String written = value;
                if (flag.equals("-q")) {
                    written = quality(value).id();
                }
                builder.append(' ').append(flag).append(' ').append(written);
            });
            return builder.toString();
        }
    }

    /**
     * Calidad por nombre ({@code FAST}, {@code BALANCED}, {@code HIGH}, {@code EXACT}) o, como en ezEdits 0.12 y
     * anteriores, por muestras por bloque ({@code -q 2}).
     */
    static TubeSettings.Quality quality(String value) {
        Double samples = parseNumber(value);
        if (samples == null) {
            return TubeSettings.Quality.byId(value);
        }
        if (!(samples > 0.0)) {
            throw new IllegalArgumentException("-q debe ser mayor que 0");
        }
        if (samples <= 1.0) {
            return TubeSettings.Quality.FAST;
        }
        if (samples <= 2.5) {
            return TubeSettings.Quality.BALANCED;
        }
        return samples <= 4.5 ? TubeSettings.Quality.HIGH : TubeSettings.Quality.EXACT;
    }

    private static double[] kochanekBartels(String value) {
        String[] parts = value.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("-p espera tensión:sesgo:continuidad, por ejemplo 0:0:0");
        }
        return new double[]{
                number(parts[0], -1.0, 1.0, "tensión"),
                number(parts[1], -1.0, 1.0, "sesgo"),
                number(parts[2], -1.0, 1.0, "continuidad")
        };
    }

    // ---------------------------------------------------------------- Ayuda

    private static Request help(String topic) {
        List<String> lines = new ArrayList<>();
        String normalized = topic == null ? "" : normalize(topic);
        switch (normalized) {
            case "2d", "basic", "rope" -> {
                lines.add("//ezspline 2d <forma(parámetros)> <patrón> <radios> [banderas]");
                lines.add("Formas: " + Shape2D.names());
                lines.add("SuperCircle(E) · CirclesCircle(C,F) · Strands(C,S) · Pipe(I) · Polygon(S,D) · "
                        + "Rectangle(X1,Y1,X2,Y2) · Star(S,D) · Flower(C,D)");
                lines.add("basic = 2d Circle · rope = 2d CirclesCircle -t 90");
            }
            case "3d" -> {
                lines.add("//ezspline 3d <forma(parámetros)> <patrón> <radios> [banderas]");
                lines.add("Formas: " + Shape3D.names());
                lines.add("Cubes(G) · Braids(T) · Chainlink(E,T,G,M,N,P) · Fishnet(S,D,W) · Honeycomb(C,W,D) · "
                        + "Oscillate(D,I) · Rings(E,T,G,M,N) · Scales(C,H,V,M,N,A,D) · Noodles(A,D,F,T,W,S)");
            }
            case "noise" -> {
                lines.add("//ezspline noise <paleta> <radios> [ruido] [profundidad] [-i expresión] [banderas]");
                lines.add("Ruido por defecto " + DEFAULT_NOISE + ", profundidad " + DEFAULT_NOISE_DEPTH
                        + "; en -i, n es el ruido y d la profundidad");
            }
            case "expression", "expr" -> {
                lines.add("//ezspline expression <paleta> <radios> [-z] [-o] [banderas] <expresión…>");
                lines.add("x, y de -1 a 1; z en radios (o de -1 a 1 con -z); -o: la salida cuenta entradas de paleta");
            }
            case "structure" -> {
                lines.add("//ezspline structure <estructura> [radio] [-z] [-s] [-n] [-p]");
                lines.add("Clipboard, Schematic(…), Shape(…), TS(…)… · sin radio, a su tamaño · -z: una sola copia");
            }
            default -> {
                lines.add("//ezspline (también ezsp y spline) a lo largo de la trayectoria o de la selección");
                lines.add("2d, 3d, basic, rope, noise, expression, structure · -h tras uno da su ayuda");
            }
        }
        lines.add("Banderas: -p t:b:c · -q FAST|BALANCED|HIGH|EXACT · -r inicio[,fin] · -s estiramiento · "
                + "-t grados · -n CONSISTENT|HORIZONTAL|UPRIGHT · -e FLAT|SOFT|SPIKE|ROUND|CUBE · -w piezas");
        return new Request(Kind.HELP, topic == null ? "" : topic, null, null, false, null, List.copyOf(lines));
    }

    // ---------------------------------------------------------------- Utilidades

    record Token(String text, int start) {
    }

    private static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < input.length()) {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < input.length() && !Character.isWhitespace(input.charAt(index))) {
                index++;
            }
            if (index > start) {
                tokens.add(new Token(input.substring(start, index), start));
            }
        }
        return tokens;
    }

    private static boolean isFlagLike(String token) {
        return token.length() > 1 && token.charAt(0) == '-' && Character.isLetter(token.charAt(1));
    }

    private static Double parseNumber(String value) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static double number(String value, double minimum, double maximum, String name) {
        Double parsed = parseNumber(value);
        if (parsed == null) {
            throw new IllegalArgumentException("Valor inválido para " + name + ": " + value);
        }
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(name + " debe estar entre " + format(minimum) + " y " + format(maximum));
        }
        return parsed;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private static String normalize(String input) {
        return input.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
