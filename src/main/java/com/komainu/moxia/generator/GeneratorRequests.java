package com.komainu.moxia.generator;

import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.shape.tube.TubeFormula;

import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Lee los argumentos de {@code noisegen}, {@code flowfield} y {@code flowline}. No toca Minecraft: devuelve los
 * ajustes puros y el texto de la paleta, el patrón y la máscara, que el comando resuelve en el hilo del servidor.
 */
public final class GeneratorRequests {
    public static final String DEFAULT_NOISE = "Perlin()";

    private GeneratorRequests() {
    }

    public record NoiseRequest(NoiseGenerator.Settings settings, String palette, String mask) {
    }

    public record FlowFieldRequest(FlowField.Settings settings, String palette, String mask) {
    }

    public record FlowLineRequest(FlowLine.Settings settings, String pattern) {
    }

    public static String usage(NoiseGenerator.Mode mode) {
        return switch (mode) {
            case HEIGHTMAP -> "/komaedit noisegen heightmap <paleta> [ruido] [altura] [-z zoom] [-s semilla]"
                    + " [-o x,y,z] [-m máscara] [-cty]";
            case TERRAIN -> "/komaedit noisegen terrain <paleta> [ruido] [altura] [fuerza] [-z zoom] [-s semilla]"
                    + " [-l estirado] [-o x,y,z] [-m máscara] [-c]";
            case ADVANCED -> "/komaedit noisegen advanced <paleta> [ruido] [inferior] [superior] [-z zoom]"
                    + " [-s semilla] [-l estirado] [-o x,y,z] [-m máscara] [-chnty]";
        };
    }

    public static NoiseRequest noise(NoiseGenerator.Mode mode, String text, LongSupplier randomSeed) {
        return noise(mode, text, randomSeed, NoiseSpec::parse);
    }

    /**
     * @param randomSeed semilla cuando {@code -s} falta o vale -1
     * @param noises     lee el ruido (con los ruidos guardados del jugador, si los hay)
     */
    public static NoiseRequest noise(NoiseGenerator.Mode mode, String text, LongSupplier randomSeed,
                                     Function<String, NoiseSpec> noises) {
        String usage = usage(mode);
        String switches = switch (mode) {
            case HEIGHTMAP -> "cty";
            case TERRAIN -> "c";
            case ADVANCED -> "chnty";
        };
        GeneratorArgs args = GeneratorArgs.parse(text, "zsolm", switches).atMost(4, usage);
        String palette = args.require(0, "la paleta", usage);
        NoiseSpec noise = noises.apply(args.positional(1) == null ? DEFAULT_NOISE : args.positional(1));
        int height = 0;
        double[] strength = {1.0, 0.5, 0.0};
        Threshold lower = Threshold.constant(0.0);
        Threshold upper = Threshold.constant(0.5);
        if (mode == NoiseGenerator.Mode.ADVANCED) {
            if (args.positional(2) != null) {
                lower = threshold(args.positional(2), "El umbral inferior");
            }
            if (args.positional(3) != null) {
                upper = threshold(args.positional(3), "El umbral superior");
            }
            if (lower.isConstant() && upper.isConstant() && lower.constantValue() > upper.constantValue()) {
                throw new IllegalArgumentException("El umbral inferior no puede ser mayor que el superior");
            }
        } else {
            if (args.positional(2) != null) {
                height = GeneratorArgs.integer(args.positional(2), 0, 4_096, "La altura");
            }
            if (mode == NoiseGenerator.Mode.TERRAIN && args.positional(3) != null) {
                strength = strength(args.positional(3));
            } else if (args.positional(3) != null) {
                throw new IllegalArgumentException("Sobra «" + args.positional(3) + "». Uso: " + usage);
            }
        }
        double zoom = args.value('z') == null ? 1.0
                : GeneratorArgs.number(args.value('z'), 0.001, 10_000.0, "El zoom (-z)");
        long seed = seed(args.value('s'), randomSeed);
        double[] offset = args.value('o') == null ? new double[3]
                : GeneratorArgs.vector(args.value('o'), 1.0E7, "El desplazamiento (-o)");
        double smear = args.value('l') == null ? 0.0
                : GeneratorArgs.number(args.value('l'), 0.0, 1_000.0, "El estirado (-l)");
        if (mode == NoiseGenerator.Mode.HEIGHTMAP && args.value('l') != null) {
            throw new IllegalArgumentException("-l (estirado vertical) solo vale en terrain y advanced");
        }
        NoiseGenerator.Settings settings = new NoiseGenerator.Settings(
                mode, noise, height, strength, lower, upper, zoom, seed, offset, args.has('c'), args.has('t'),
                args.has('y'), smear, args.has('h'), args.has('n'));
        return new NoiseRequest(settings, palette, args.value('m'));
    }

    private static Threshold threshold(String text, String name) {
        Threshold threshold = Threshold.parse(text, name);
        if (threshold.isConstant() && (threshold.constantValue() < 0.0 || threshold.constantValue() > 1.0)) {
            throw new IllegalArgumentException(name + " debe estar entre 0 y 1");
        }
        return threshold;
    }

    /** Hasta tres fuerzas separadas por comas, de abajo arriba. */
    static double[] strength(String text) {
        String[] parts = text.split("[,;]");
        if (parts.length > 3) {
            throw new IllegalArgumentException("La fuerza admite hasta tres valores: abajo,medio,arriba");
        }
        double[] values = new double[parts.length];
        for (int index = 0; index < parts.length; index++) {
            values[index] = GeneratorArgs.number(parts[index], -4.0, 4.0, "La fuerza");
        }
        return values;
    }

    private static long seed(String text, LongSupplier randomSeed) {
        if (text == null) {
            return randomSeed.getAsLong();
        }
        long value;
        try {
            value = Long.parseLong(text.trim());
        } catch (NumberFormatException exception) {
            value = text.trim().hashCode();
        }
        return value == -1 ? randomSeed.getAsLong() : value;
    }

    public static final String FLOWFIELD_USAGE = "/komaedit flowfield <paleta> <líneas|n%> [iteraciones] [velocidad]"
            + " [escala_paleta] [ruido] [-m máscara] [-h [surface|above]] [-i inercia] [-g x,y,z] [-j x,y,z]"
            + " [-b borde] [-x/-y/-z expresión] [-p inicio:fin] [-s semilla] [-cft]";

    public static FlowFieldRequest flowField(String text, LongSupplier randomSeed) {
        return flowField(text, randomSeed, NoiseSpec::parse);
    }

    public static FlowFieldRequest flowField(String text, LongSupplier randomSeed,
                                             Function<String, NoiseSpec> noises) {
        String usage = FLOWFIELD_USAGE;
        GeneratorArgs args = GeneratorArgs.parse(text, "mijgbxyzps", "cft", "h", FlowField.Distribution::known)
                .atMost(6, usage);
        String palette = args.require(0, "la paleta", usage);
        String linesText = args.require(1, "el número de líneas", usage).trim();
        boolean percent = linesText.endsWith("%");
        double lines = percent
                ? GeneratorArgs.number(linesText.substring(0, linesText.length() - 1), 0.0, 10_000.0,
                "El porcentaje de líneas")
                : GeneratorArgs.integer(linesText, 1, FlowField.MAX_LINES, "El número de líneas");
        int iterations = args.positional(2) == null ? 32
                : GeneratorArgs.integer(args.positional(2), 1, FlowField.MAX_ITERATIONS, "Las iteraciones");
        double velocity = args.positional(3) == null ? 1.0
                : GeneratorArgs.number(args.positional(3), 0.05, 16.0, "La velocidad");
        double scalar = args.positional(4) == null ? 1.0
                : GeneratorArgs.number(args.positional(4), 0.001, 1_000.0, "La escala de paleta");
        NoiseSpec noise = noises.apply(args.positional(5) == null ? DEFAULT_NOISE : args.positional(5));
        FlowField.Distribution heightmap = !args.has('h') ? null
                : args.value('h') == null ? FlowField.Distribution.SURFACE
                : FlowField.Distribution.byId(args.value('h'));
        double inertia = args.value('i') == null ? 0.0
                : GeneratorArgs.number(args.value('i'), 0.0, 1.0, "La inercia (-i)");
        double[] gravity = args.value('g') == null ? new double[3]
                : GeneratorArgs.vector(args.value('g'), 16.0, "La gravedad (-g)");
        double[] jitter = args.value('j') == null ? new double[3]
                : GeneratorArgs.vector(args.value('j'), 256.0, "El temblor (-j)");
        int boundary = args.value('b') == null ? 0
                : GeneratorArgs.integer(args.value('b'), 0, 64, "El borde (-b)");
        double[] progression = progression(args.value('p'));
        if (args.has('t') && heightmap != null) {
            throw new IllegalArgumentException("-t (flujo 3D) y -h (mapa de alturas) no se pueden juntar");
        }
        FlowField.Settings settings = new FlowField.Settings(
                lines, percent, iterations, velocity, scalar, noise, heightmap, args.value('m') != null, inertia,
                gravity, jitter, boundary, modifier(args.value('x'), "x"), modifier(args.value('y'), "y"),
                modifier(args.value('z'), "z"), progression[0], progression[1], seed(args.value('s'), randomSeed),
                args.has('c'), args.has('f'), args.has('t'));
        return new FlowFieldRequest(settings, palette, args.value('m'));
    }

    /**
     * Transformación de un eje: una fórmula con x, y, z. Si empieza por un operador se aplica al propio eje, como
     * en ezEdits: {@code *10} es {@code x*10} para {@code -x}.
     */
    static TubeFormula modifier(String text, String axis) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        char first = trimmed.charAt(0);
        if ("*/+-%^".indexOf(first) >= 0) {
            trimmed = axis + trimmed;
        }
        try {
            return TubeFormula.compile(trimmed);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("-" + axis + ": " + exception.getMessage());
        }
    }

    /** {@code -p inicio:fin}; con un solo número vale para toda la línea. */
    static double[] progression(String text) {
        if (text == null) {
            return new double[]{1.0, 1.0};
        }
        String[] parts = text.split(":");
        if (parts.length > 2) {
            throw new IllegalArgumentException("-p se escribe inicio:fin, por ejemplo 1:0 o -1:1");
        }
        double start = GeneratorArgs.number(parts[0], -64.0, 64.0, "La fuerza inicial (-p)");
        double end = parts.length == 2 ? GeneratorArgs.number(parts[1], -64.0, 64.0, "La fuerza final (-p)") : start;
        return new double[]{start, end};
    }

    public static final String FLOWLINE_USAGE = "/komaedit flowline <patrón> <longitud> [gravedad] [ruido]"
            + " [-i inercia] [-c puntos] [-l líneas] [-r azar] [-s]";

    public static FlowLineRequest flowLine(String text, LongSupplier randomSeed) {
        return flowLine(text, randomSeed, NoiseSpec::parse);
    }

    public static FlowLineRequest flowLine(String text, LongSupplier randomSeed,
                                           Function<String, NoiseSpec> noises) {
        String usage = FLOWLINE_USAGE;
        GeneratorArgs args = GeneratorArgs.parse(text, "iclr", "s").atMost(4, usage);
        String pattern = args.require(0, "el patrón", usage);
        int length = GeneratorArgs.integer(args.require(1, "la longitud", usage), 1, FlowLine.MAX_LENGTH,
                "La longitud");
        double gravity = args.positional(2) == null ? -1.0
                : GeneratorArgs.number(args.positional(2), -16.0, 16.0, "La gravedad");
        NoiseSpec noise = noises.apply(args.positional(3) == null ? DEFAULT_NOISE : args.positional(3));
        double inertia = args.value('i') == null ? 0.0
                : GeneratorArgs.number(args.value('i'), 0.0, 1.0, "La inercia (-i)");
        int convex = args.value('c') == null ? 0
                : GeneratorArgs.integer(args.value('c'), 0, 256, "Los puntos de la selección (-c)");
        int lines = args.value('l') == null ? 1
                : GeneratorArgs.integer(args.value('l'), 1, FlowLine.MAX_LINES, "Las líneas (-l)");
        double randomness = args.value('r') == null ? 50.0
                : GeneratorArgs.number(args.value('r'), 0.0, 1_000.0, "El azar (-r)");
        if ((long) length * lines > 16_384) {
            throw new IllegalArgumentException("Longitud × líneas no puede pasar de 16384");
        }
        FlowLine.Settings settings = new FlowLine.Settings(length, gravity, noise, inertia, convex, lines,
                randomness, args.has('s'), randomSeed.getAsLong());
        return new FlowLineRequest(settings, pattern);
    }
}
