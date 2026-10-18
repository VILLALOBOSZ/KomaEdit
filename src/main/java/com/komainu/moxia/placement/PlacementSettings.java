package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.smooth.PieceFit;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.tube.TubeSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lo que se escribe tras el comando: {@code <estructura> [<primaria>] [<secundaria>] [banderas]}, con las banderas
 * de ezEdits. Las de dispersión solo valen en {@code scatter} y las de array solo en {@code array}; {@code -n} es
 * la densidad en la dispersión y el modo de normales en el array, como en ezEdits.
 *
 * <p>Comunes: {@code -s} dimensiones, {@code -o} escala aleatoria, {@code -c}/{@code -k} orientación,
 * {@code -f} simetrías aleatorias, {@code -r} giros de 90° aleatorios, {@code -a} colocar aire, {@code -j} ajuste
 * de direcciones, {@code -x} perturbar la secundaria, {@code -w} losas y escaleras e {@code -i} semilla.</p>
 */
public record PlacementSettings(
        Kind kind,
        String structure,
        Alignment primary,
        Alignment secondary,
        Vec3 dimensions,
        double minimumScale,
        double maximumScale,
        DirectionExpr orientationAxis,
        double orientationAngle,
        boolean[] flips,
        int rotationAxis,
        boolean placeAir,
        AlignmentFrame.Snap snap,
        boolean perturb,
        SmoothMode smoothMode,
        double coverage,
        long seed,
        boolean randomSeed,
        Scatter scatter,
        Array array
) {
    public enum Kind {
        PLACE("place"), SCATTER("scatter"), ARRAY("array");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    /** Parámetros de {@code scatter}. */
    public record Scatter(
            String region,
            List<Vec3> directions,
            double threshold,
            String mask,
            double density,
            int iterations,
            String cover,
            boolean trim
    ) {
        static Scatter defaults() {
            return new Scatter(null, List.of(), 0.5, null, 2.0, 15, null, false);
        }
    }

    /** Parámetros de {@code array}. */
    public record Array(
            double gap,
            double maxOffset,
            TubeSettings.RadiusProgression progression,
            double tension,
            double bias,
            double continuity,
            TubeSettings.NormalMode normalMode,
            boolean snapToSurface
    ) {
        static Array defaults() {
            return new Array(0.0, Double.NaN, TubeSettings.RadiusProgression.CONSTANT, 0, 0, 0,
                    TubeSettings.NormalMode.CONSISTENT, false);
        }
    }

    public static PlacementSettings parse(Kind kind, String input) {
        return parse(kind, input, false);
    }

    /**
     * @param brush si es para un pincel ({@code /komaedit pincel place|scatter|array}): el de colocación admite
     *              {@code ViewDiff}, la dirección que marca un segundo clic
     */
    public static PlacementSettings parse(Kind kind, String input, boolean brush) {
        List<String> words = ParamText.words(input);
        if (words.isEmpty()) {
            throw new IllegalArgumentException("Falta la estructura: Clipboard, Schematic(N:…), Shape(S:…,P:…), "
                    + "Expression(E:…,P:…), TexturedShape(S:…,T:…,P:…) o Icosphere(P:…)");
        }
        String structure = words.getFirst();
        if (isFlag(structure)) {
            throw new IllegalArgumentException("La estructura va primero, antes de las banderas");
        }
        Alignment primary = null;
        Alignment secondary = null;
        Vec3 dimensions = null;
        double minimumScale = 1.0;
        double maximumScale = 1.0;
        DirectionExpr axis = DirectionExpr.constant(Alignment.UP);
        double angle = 0.0;
        boolean[] flips = new boolean[3];
        int rotationAxis = -1;
        boolean placeAir = false;
        AlignmentFrame.Snap snap = AlignmentFrame.Snap.NONE;
        boolean perturb = false;
        SmoothMode smoothMode = SmoothMode.NONE;
        double coverage = PieceFit.DEFAULT_COVERAGE;
        long seed = -1;
        Scatter scatter = Scatter.defaults();
        Array array = Array.defaults();
        String region = scatter.region();
        List<Vec3> directions = new ArrayList<>();
        double threshold = scatter.threshold();
        String mask = null;
        double density = scatter.density();
        int iterations = scatter.iterations();
        String cover = null;
        boolean trim = false;
        double gap = array.gap();
        double maxOffset = array.maxOffset();
        TubeSettings.RadiusProgression progression = array.progression();
        double tension = 0;
        double bias = 0;
        double continuity = 0;
        TubeSettings.NormalMode normalMode = array.normalMode();
        boolean snapSurface = false;

        for (int index = 1; index < words.size(); index++) {
            String word = words.get(index);
            if (!isFlag(word)) {
                if (primary == null) {
                    primary = Alignment.parse(word, true);
                } else if (secondary == null) {
                    secondary = Alignment.parse(word, false);
                } else {
                    throw new IllegalArgumentException("Sobra «" + word + "»: tras la estructura van como mucho la "
                            + "primaria y la secundaria; lo demás son banderas");
                }
                continue;
            }
            String flag = word.toLowerCase(Locale.ROOT);
            switch (flag) {
                case "-a" -> {
                    placeAir = true;
                    continue;
                }
                case "-x" -> {
                    perturb = true;
                    continue;
                }
                case "-t" -> {
                    only(kind, Kind.SCATTER, flag);
                    trim = true;
                    continue;
                }
                case "-b" -> {
                    only(kind, Kind.ARRAY, flag);
                    snapSurface = true;
                    continue;
                }
                default -> {
                }
            }
            if (index + 1 >= words.size()) {
                throw new IllegalArgumentException("Falta el valor de " + word);
            }
            String value = ParamText.unquote(words.get(++index));
            switch (flag) {
                case "-s" -> dimensions = dimensions(value);
                case "-o" -> {
                    double[] range = pair(value, 0.01, 64.0, "-o");
                    minimumScale = range[0];
                    maximumScale = range[1];
                }
                case "-c" -> angle = ParamText.number(value, -3_600, 3_600, "-c");
                case "-k" -> axis = DirectionExpr.parse(value);
                case "-f" -> flips = flips(value);
                case "-r" -> rotationAxis = axisIndex(value);
                case "-j" -> snap = AlignmentFrame.Snap.byId(value);
                case "-w" -> {
                    int colon = value.indexOf(':');
                    smoothMode = SmoothMode.byId(colon < 0 ? value : value.substring(0, colon));
                    if (colon >= 0) {
                        coverage = ParamText.number(value.substring(colon + 1), 0.0, 1.0, "la cobertura de -w");
                    }
                }
                case "-i", "-seed", "-semilla" -> seed = (long) ParamText.number(value, -1, 9.0E15, "-i");
                case "-h" -> {
                    only(kind, Kind.SCATTER, flag);
                    region = value;
                }
                case "-d" -> {
                    only(kind, Kind.SCATTER, flag);
                    for (String direction : ParamText.split(value, ',')) {
                        directions.add(DirectionExpr.parse(direction).evaluate(DirectionExpr.Context.NONE)
                                .normalized());
                    }
                }
                case "-e" -> {
                    only(kind, Kind.SCATTER, flag);
                    threshold = ParamText.number(value, -1.0, 1.0, "-e");
                }
                case "-m" -> {
                    only(kind, Kind.SCATTER, flag);
                    mask = value;
                }
                case "-u" -> {
                    only(kind, Kind.SCATTER, flag);
                    iterations = (int) ParamText.number(value, 0, 200, "-u");
                }
                case "-l" -> {
                    only(kind, Kind.SCATTER, flag);
                    cover = value;
                }
                case "-n" -> {
                    if (kind == Kind.SCATTER) {
                        density = ParamText.number(value, 0.0, 100.0, "-n (densidad en %)");
                    } else if (kind == Kind.ARRAY) {
                        normalMode = TubeSettings.NormalMode.byId(value);
                    } else {
                        throw new IllegalArgumentException("-n solo vale en scatter (densidad) y array (normales)");
                    }
                }
                case "-g" -> {
                    only(kind, Kind.ARRAY, flag);
                    gap = ParamText.number(value, -512, 4_096, "-g");
                }
                case "-y" -> {
                    only(kind, Kind.ARRAY, flag);
                    maxOffset = ParamText.number(value, 0, 4_096, "-y");
                }
                case "-q" -> {
                    only(kind, Kind.ARRAY, flag);
                    progression = progression(value);
                }
                case "-p" -> {
                    only(kind, Kind.ARRAY, flag);
                    String[] parts = value.split("[:,]");
                    if (parts.length != 3) {
                        throw new IllegalArgumentException("-p espera tensión:sesgo:continuidad, por ejemplo 0:0:0");
                    }
                    tension = ParamText.number(parts[0], -1.0, 1.0, "la tensión");
                    bias = ParamText.number(parts[1], -1.0, 1.0, "el sesgo");
                    continuity = ParamText.number(parts[2], -1.0, 1.0, "la continuidad");
                }
                default -> throw new IllegalArgumentException("Bandera desconocida para " + kind.id() + ": " + word);
            }
        }
        Alignment resolvedPrimary = primary == null ? Alignment.parse("Constant", true) : primary;
        Alignment resolvedSecondary = secondary == null ? Alignment.parse("Constant", false) : secondary;
        if (kind != Kind.ARRAY && (resolvedPrimary.needsPath() || resolvedSecondary.needsPath())) {
            throw new IllegalArgumentException("Tangential y Orthogonal solo funcionan en array");
        }
        if ((resolvedPrimary.needsViewDiff() || resolvedSecondary.needsViewDiff())
                && (!brush || kind != Kind.PLACE)) {
            throw new IllegalArgumentException(brush
                    ? "ViewDiff solo funciona en el pincel de colocación (place)"
                    : "ViewDiff solo funciona con un pincel (el segundo clic marca la dirección)");
        }
        boolean randomSeed = seed == -1;
        return new PlacementSettings(kind, structure, resolvedPrimary, resolvedSecondary, dimensions, minimumScale,
                maximumScale, axis, angle, flips, rotationAxis, placeAir, snap, perturb, smoothMode, coverage,
                randomSeed ? 0 : seed, randomSeed,
                new Scatter(region, List.copyOf(directions), threshold, mask, density, iterations, cover, trim),
                new Array(gap, maxOffset, progression, tension, bias, continuity, normalMode, snapSurface));
    }

    /** Fija la semilla si se pidió al azar ({@code -i -1}), para que el cálculo sea reproducible a partir de aquí. */
    public PlacementSettings withSeed(long value) {
        return new PlacementSettings(kind, structure, primary, secondary, dimensions, minimumScale, maximumScale,
                orientationAxis, orientationAngle, flips, rotationAxis, placeAir, snap, perturb, smoothMode, coverage,
                value, false, scatter, array);
    }

    public boolean needsNormal() {
        return primary.needsNormal() || secondary.needsNormal();
    }

    public boolean needsViewDiff() {
        return primary.needsViewDiff() || secondary.needsViewDiff();
    }

    private static void only(Kind kind, Kind expected, String flag) {
        if (kind != expected) {
            throw new IllegalArgumentException(flag + " solo vale en " + expected.id());
        }
    }

    private static boolean isFlag(String word) {
        return word.length() > 1 && word.charAt(0) == '-' && Character.isLetter(word.charAt(1));
    }

    static Vec3 dimensions(String value) {
        String[] parts = ParamText.unquote(value).split("[,x×]");
        if (parts.length == 1) {
            double size = ParamText.number(parts[0], 1, 512, "-s");
            return new Vec3(size, size, size);
        }
        if (parts.length != 3) {
            throw new IllegalArgumentException("-s espera ancho,alto,fondo, por ejemplo 20,30,20");
        }
        return new Vec3(ParamText.number(parts[0], 1, 512, "el ancho de -s"),
                ParamText.number(parts[1], 1, 512, "el alto de -s"),
                ParamText.number(parts[2], 1, 512, "el fondo de -s"));
    }

    private static double[] pair(String value, double minimum, double maximum, String name) {
        String[] parts = value.split(",");
        double first = ParamText.number(parts[0], minimum, maximum, name);
        double second = parts.length > 1 ? ParamText.number(parts[1], minimum, maximum, name) : first;
        if (parts.length > 2) {
            throw new IllegalArgumentException(name + " espera mínimo,máximo, por ejemplo 0.5,2");
        }
        return new double[]{Math.min(first, second), Math.max(first, second)};
    }

    private static boolean[] flips(String value) {
        String text = value.trim().toLowerCase(Locale.ROOT);
        boolean[] flips = new boolean[3];
        if (text.equals("none") || text.equals("ninguno") || text.equals("no")) {
            return flips;
        }
        for (char character : text.toCharArray()) {
            switch (character) {
                case 'x' -> flips[0] = true;
                case 'y' -> flips[1] = true;
                case 'z' -> flips[2] = true;
                default -> throw new IllegalArgumentException("-f espera ejes (X, Y, Z, XY, XZ, YZ, XYZ o None): " + value);
            }
        }
        return flips;
    }

    private static int axisIndex(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "x" -> 0;
            case "y" -> 1;
            case "z" -> 2;
            case "none", "no", "ninguno" -> -1;
            default -> throw new IllegalArgumentException("-r espera un eje: X, Y o Z");
        };
    }

    /** {@code 1}, {@code 0.3,3} o {@code 1.5,0.5:5,2}: factores repartidos (o en su posición) por el recorrido. */
    static TubeSettings.RadiusProgression progression(String value) {
        String[] entries = value.split(",");
        if (entries.length > 32) {
            throw new IllegalArgumentException("-q admite como mucho 32 factores");
        }
        if (entries.length == 1) {
            double factor = ParamText.number(entries[0], 0.01, 64, "-q");
            return new TubeSettings.RadiusProgression(new double[]{0.0, 1.0}, new double[]{factor, factor});
        }
        double[] positions = new double[entries.length];
        double[] factors = new double[entries.length];
        boolean[] explicit = new boolean[entries.length];
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index].trim();
            int colon = entry.indexOf(':');
            if (colon >= 0) {
                positions[index] = ParamText.number(entry.substring(0, colon), 0, 1, "la posición de -q");
                factors[index] = ParamText.number(entry.substring(colon + 1), 0.01, 64, "-q");
                explicit[index] = true;
            } else {
                factors[index] = ParamText.number(entry, 0.01, 64, "-q");
            }
        }
        int last = entries.length - 1;
        positions[0] = 0.0;
        positions[last] = 1.0;
        explicit[0] = true;
        explicit[last] = true;
        int anchor = 0;
        for (int index = 1; index <= last; index++) {
            if (!explicit[index]) {
                continue;
            }
            for (int inner = anchor + 1; inner < index; inner++) {
                positions[inner] = positions[anchor]
                        + (positions[index] - positions[anchor]) * (inner - anchor) / (index - anchor);
            }
            anchor = index;
        }
        return new TubeSettings.RadiusProgression(positions, factors);
    }
}
