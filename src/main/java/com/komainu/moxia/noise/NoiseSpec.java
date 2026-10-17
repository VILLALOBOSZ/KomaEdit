package com.komainu.moxia.noise;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Ruido configurable con la notación de ezEdits: {@code Tipo(clave:valor,...)}, con nombres largos o abreviados y sin
 * distinguir mayúsculas. Ejemplos: {@code Perlin(Frequency:0.04)}, {@code Ce(cD:e,cR:sub,F:.1)},
 * {@code Si(fT:R,fO:4,wT:S,wA:30)}.
 *
 * <p>Los tipos de ruido, los fractales y la distorsión de dominio los resuelve {@link FastNoiseLite}; los tipos
 * {@code White} y {@code Gabor}, las variantes de distancia celular derivadas y el mapeo de valores se calculan
 * aquí. Por defecto el resultado va de 0 a 1.</p>
 */
public final class NoiseSpec {
    private static final Map<String, String> KEYS = keys();

    private final String source;
    private final Type type;
    private final Map<String, String> values;

    private NoiseSpec(String source, Type type, Map<String, String> values) {
        this.source = source;
        this.type = type;
        this.values = values;
    }

    public static NoiseSpec parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta el ruido, por ejemplo Perlin() o Ce(F:0.1)");
        }
        String text = input.trim();
        if (text.length() > 512) {
            throw new IllegalArgumentException("El ruido admite como mucho 512 caracteres");
        }
        int open = text.indexOf('(');
        String name = open < 0 ? text : text.substring(0, open);
        Type type = Type.byName(name);
        Map<String, String> values = new LinkedHashMap<>();
        if (open >= 0) {
            if (!text.endsWith(")")) {
                throw new IllegalArgumentException("Falta el paréntesis de cierre en " + text);
            }
            String body = text.substring(open + 1, text.length() - 1).trim();
            if (!body.isEmpty()) {
                for (String entry : body.split(",")) {
                    String[] pair = entry.split(":", 2);
                    if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                        throw new IllegalArgumentException("Parámetro de ruido inválido: " + entry.trim());
                    }
                    String key = KEYS.get(normalize(pair[0]));
                    if (key == null) {
                        throw new IllegalArgumentException("Parámetro de ruido desconocido: " + pair[0].trim());
                    }
                    values.put(key, pair[1].trim());
                }
            }
        }
        NoiseSpec spec = new NoiseSpec(text, type, Map.copyOf(values));
        spec.build(0L);
        return spec;
    }

    public Type type() {
        return type;
    }

    @Override
    public String toString() {
        return source;
    }

    /**
     * Construye el campo de ruido.
     *
     * @param fallbackSeed semilla si el ruido no fija una ({@code s:-1} o sin {@code s}); úsala para que holograma
     *                     y Build coincidan
     */
    public NoiseField build(long fallbackSeed) {
        int seed = (int) number("seed", -1);
        if (seed == -1) {
            seed = (int) (fallbackSeed ^ (fallbackSeed >>> 32));
        }
        double frequency = number("frequency", 0.01);
        double scaleX = number("xscale", 1.0);
        double scaleY = number("yscale", 1.0);
        double scaleZ = number("zscale", 1.0);
        boolean inverted = bool("inverted", false);
        Mapping mapping = Mapping.byName(text("mapping", "default"));
        double lower = number("lower", -1.0);
        double upper = number("upper", 1.0);
        double minimum = number("minclamp", Double.NEGATIVE_INFINITY);
        double maximum = number("maxclamp", Double.POSITIVE_INFINITY);
        if (mapping == Mapping.OVERRIDE && Math.abs(upper - lower) < 1.0E-9) {
            throw new IllegalArgumentException("En el mapeo Override los límites l y u no pueden coincidir");
        }

        RawField raw = switch (type) {
            case WHITE -> new WhiteField(seed, frequency);
            case GABOR -> new GaborField(seed, frequency, number("gaborradius", 1.0),
                    number("gaborfrequency", 1.0), number("gaborspread", 0.0), vector("gabororientation"),
                    number("gaborjitter", 0.0), (int) Math.clamp(number("gaborimpulses", 4), 1, 8),
                    number("gabormagnitude", 1.0));
            case SHARD -> throw new IllegalArgumentException(
                    "El ruido Shard todavía no está disponible; usa Cellular con cR:edge para un efecto parecido");
            default -> fastNoise(seed, frequency);
        };
        RawField warped = warp(raw, seed, frequency);
        return new MappedField(warped, scaleX, scaleY, scaleZ, inverted, mapping, lower, upper, minimum, maximum);
    }

    private RawField fastNoise(int seed, double frequency) {
        FastNoiseLite noise = configured(seed, frequency);
        if (type != Type.CELLULAR) {
            return noise::GetNoise;
        }
        String returnType = normalize(text("cellreturn", "distance"));
        FastNoiseLite.CellularReturnType direct = switch (returnType) {
            case "cellvalue", "cell" -> FastNoiseLite.CellularReturnType.CellValue;
            case "distance", "1" -> FastNoiseLite.CellularReturnType.Distance;
            case "distance2", "2" -> FastNoiseLite.CellularReturnType.Distance2;
            case "distance2add", "2add", "add" -> FastNoiseLite.CellularReturnType.Distance2Add;
            case "distance2sub", "2sub", "sub" -> FastNoiseLite.CellularReturnType.Distance2Sub;
            case "distance2mul", "2mul", "mul" -> FastNoiseLite.CellularReturnType.Distance2Mul;
            case "distance2div", "2div", "div" -> FastNoiseLite.CellularReturnType.Distance2Div;
            default -> null;
        };
        if (direct != null) {
            noise.SetCellularReturnType(direct);
            return noise::GetNoise;
        }
        FastNoiseLite second = configured(seed, frequency);
        noise.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance);
        second.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance2);
        return derivedCellular(noise, second, returnType);
    }

    /** Generador de FastNoiseLite con el tipo, el fractal y los ajustes celulares del ruido. */
    private FastNoiseLite configured(int seed, double frequency) {
        FastNoiseLite noise = new FastNoiseLite(seed);
        noise.SetFrequency((float) frequency);
        noise.SetNoiseType(switch (type) {
            case PERLIN -> FastNoiseLite.NoiseType.Perlin;
            case OPEN_SIMPLEX_2 -> FastNoiseLite.NoiseType.OpenSimplex2;
            case OPEN_SIMPLEX_2S -> FastNoiseLite.NoiseType.OpenSimplex2S;
            case VALUE -> FastNoiseLite.NoiseType.Value;
            case VALUE_CUBIC -> FastNoiseLite.NoiseType.ValueCubic;
            case CELLULAR -> FastNoiseLite.NoiseType.Cellular;
            default -> throw new IllegalStateException(type.name());
        });
        configureFractal(noise, text("fractaltype", "none"), false);
        if (type == Type.CELLULAR) {
            noise.SetCellularJitter((float) number("celljitter", 1.0));
            noise.SetCellularDistanceFunction(switch (normalize(text("celldistance", "euclideansq"))) {
                case "euclidean", "e" -> FastNoiseLite.CellularDistanceFunction.Euclidean;
                case "euclideansq", "sq" -> FastNoiseLite.CellularDistanceFunction.EuclideanSq;
                case "manhattan", "man" -> FastNoiseLite.CellularDistanceFunction.Manhattan;
                case "hybrid", "h" -> FastNoiseLite.CellularDistanceFunction.Hybrid;
                default -> throw new IllegalArgumentException("Distancia celular no disponible: "
                        + text("celldistance", "") + " (euclidean, euclideansq, manhattan, hybrid)");
            });
        }
        return noise;
    }

    /** Variantes celulares que FastNoiseLite no trae, calculadas a partir de la primera y la segunda distancia. */
    private static RawField derivedCellular(FastNoiseLite first, FastNoiseLite second, String returnType) {
        return switch (returnType) {
            case "distancesquared", "sq" -> (x, y, z) -> {
                double distance = first.GetNoise(x, y, z) + 1.0;
                return distance * distance - 1.0;
            };
            case "distanceinverse", "inv" -> (x, y, z) -> inverse(first.GetNoise(x, y, z) + 1.0);
            case "distancelog", "log" -> (x, y, z) -> Math.log(first.GetNoise(x, y, z) + 1.0 + 1.0E-4) * 0.5;
            case "distanceexp", "exp" -> (x, y, z) -> Math.exp(first.GetNoise(x, y, z) + 1.0) - 2.0;
            case "distance2sq", "2sq" -> (x, y, z) -> {
                double distance = second.GetNoise(x, y, z) + 1.0;
                return distance * distance - 1.0;
            };
            case "distance2inv", "2inv" -> (x, y, z) -> inverse(second.GetNoise(x, y, z) + 1.0);
            case "distance2log", "2log" -> (x, y, z) -> Math.log(second.GetNoise(x, y, z) + 1.0 + 1.0E-4) * 0.5;
            case "distance2exp", "2exp" -> (x, y, z) -> Math.exp(second.GetNoise(x, y, z) + 1.0) - 2.0;
            case "edge", "e" -> (x, y, z) -> {
                double gap = (second.GetNoise(x, y, z) + 1.0) - (first.GetNoise(x, y, z) + 1.0);
                return 1.0 - 2.0 * Math.clamp(gap * 4.0, 0.0, 1.0);
            };
            default -> throw new IllegalArgumentException("Tipo de retorno celular no disponible: " + returnType
                    + " (cell, distance, distance2, add, sub, mul, div, sq, inv, log, exp, 2sq, 2inv, 2log, 2exp, edge)");
        };
    }

    private static double inverse(double distance) {
        return Math.clamp(1.0 / Math.max(distance, 0.05) - 1.0, -1.0, 1.0);
    }

    private void configureFractal(FastNoiseLite noise, String name, boolean warp) {
        String fractal = normalize(name);
        noise.SetFractalType(switch (fractal) {
            case "none", "n" -> FastNoiseLite.FractalType.None;
            case "fbm", "f" -> FastNoiseLite.FractalType.FBm;
            case "ridged", "r" -> FastNoiseLite.FractalType.Ridged;
            case "pingpong", "p" -> warp ? FastNoiseLite.FractalType.DomainWarpProgressive
                    : FastNoiseLite.FractalType.PingPong;
            case "domainwarpindependent", "i" -> FastNoiseLite.FractalType.DomainWarpIndependent;
            case "domainwarpprogressive" -> FastNoiseLite.FractalType.DomainWarpProgressive;
            default -> throw new IllegalArgumentException("Tipo de fractal desconocido: " + name
                    + " (none, fbm, ridged, pingpong)");
        });
        String prefix = warp ? "warp" : "fractal";
        noise.SetFractalOctaves((int) Math.clamp(number(prefix + "octaves", 3), 1, 12));
        noise.SetFractalLacunarity((float) number(prefix + "lacunarity", 2.0));
        noise.SetFractalGain((float) number(prefix + "gain", 0.5));
        if (!warp) {
            noise.SetFractalWeightedStrength((float) number("fractalweighted", 0.0));
            noise.SetFractalPingPongStrength((float) number("fractalpingpong", 2.0));
        }
    }

    private RawField warp(RawField field, int seed, double frequency) {
        String type = normalize(text("warptype", "none"));
        FastNoiseLite.DomainWarpType warpType = switch (type) {
            case "none", "n" -> null;
            case "basicgrid", "g" -> FastNoiseLite.DomainWarpType.BasicGrid;
            case "opensimplex2", "s" -> FastNoiseLite.DomainWarpType.OpenSimplex2;
            case "opensimplex2reduced", "r" -> FastNoiseLite.DomainWarpType.OpenSimplex2Reduced;
            default -> throw new IllegalArgumentException("Distorsión de dominio no disponible: " + type
                    + " (basicgrid, opensimplex2, opensimplex2reduced)");
        };
        if (warpType == null) {
            return field;
        }
        FastNoiseLite warp = new FastNoiseLite(seed + 1);
        warp.SetDomainWarpType(warpType);
        warp.SetFrequency((float) number("warpfrequency", frequency));
        warp.SetDomainWarpAmp((float) number("warpamplitude", 30.0));
        configureFractal(warp, text("warpfractal", "none"), true);
        return (x, y, z) -> {
            FastNoiseLite.Vector3 point = new FastNoiseLite.Vector3(x, y, z);
            warp.DomainWarp(point);
            return field.raw(point.x, point.y, point.z);
        };
    }

    private double number(String key, double fallback) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || Math.abs(parsed) > 1.0E7) {
                throw new IllegalArgumentException("Valor fuera de rango para " + key + ": " + value);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Valor numérico inválido para " + key + ": " + value);
        }
    }

    private boolean bool(String key, boolean fallback) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        return switch (normalize(value)) {
            case "true", "t", "1", "yes", "si" -> true;
            case "false", "f", "0", "no" -> false;
            default -> throw new IllegalArgumentException("Valor booleano inválido para " + key + ": " + value);
        };
    }

    private String text(String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    private double[] vector(String key) {
        String value = values.get(key);
        if (value == null) {
            return new double[]{1.0, 0.0, 0.0};
        }
        String[] parts = value.replace("(", "").replace(")", "").split("[;/ ]+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("La orientación Gabor espera tres números separados por ; (1;0;0)");
        }
        double[] vector = new double[3];
        for (int index = 0; index < 3; index++) {
            vector[index] = Double.parseDouble(parts[index]);
        }
        return vector;
    }

    private static String normalize(String input) {
        return input.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private static Map<String, String> keys() {
        Map<String, String> keys = new LinkedHashMap<>();
        alias(keys, "seed", "seed", "s");
        alias(keys, "frequency", "frequency", "freq", "f");
        alias(keys, "inverted", "inverted", "invert", "i");
        alias(keys, "mapping", "valuemapping", "mapping", "m");
        alias(keys, "lower", "lowerbound", "lower", "l");
        alias(keys, "upper", "upperbound", "upper", "u");
        alias(keys, "minclamp", "minclamp", "mn");
        alias(keys, "maxclamp", "maxclamp", "mx");
        alias(keys, "xscale", "xscaling", "xscale", "x");
        alias(keys, "yscale", "yscaling", "yscale", "y");
        alias(keys, "zscale", "zscaling", "zscale", "z");
        alias(keys, "celljitter", "cellularjittermodifier", "cellularjitter", "cj");
        alias(keys, "celldistance", "cellulardistancefunction", "cellulardistance", "cd");
        alias(keys, "cellreturn", "cellularreturntype", "cellularreturn", "cr");
        alias(keys, "fractaltype", "fractaltype", "ft");
        alias(keys, "fractalpingpong", "pingpongstrength", "fp");
        alias(keys, "fractaloctaves", "octaves", "fractaloctaves", "fo");
        alias(keys, "fractallacunarity", "lacunarity", "fractallacunarity", "fl");
        alias(keys, "fractalgain", "gain", "fractalgain", "fg");
        alias(keys, "fractalweighted", "weightedstrength", "fw");
        alias(keys, "warptype", "domainwarptype", "wt");
        alias(keys, "warpfrequency", "domainwarpfreq", "domainwarpfrequency", "wf");
        alias(keys, "warpoctaves", "domainwarpoct", "domainwarpoctaves", "wo");
        alias(keys, "warpgain", "domainwarpgain", "wg");
        alias(keys, "warpamplitude", "domainwarpamp", "domainwarpamplitude", "wa");
        alias(keys, "warpfractal", "domainwarpfrac", "domainwarpfractal", "wc");
        alias(keys, "warplacunarity", "domainwarplacunarity", "wl");
        alias(keys, "gaborradius", "gaborradius", "gr");
        alias(keys, "gaborfrequency", "gaborfrequency", "gf");
        alias(keys, "gaborspread", "gaborfrequencyspread", "gfs");
        alias(keys, "gabororientation", "gabororientation", "go");
        alias(keys, "gaborjitter", "gabororientationjitter", "gj");
        alias(keys, "gaborimpulses", "gaborimpulsespercell", "gn");
        alias(keys, "gabormagnitude", "gabormagnitude", "gm");
        alias(keys, "sharpness", "sharpness", "h");
        return Map.copyOf(keys);
    }

    private static void alias(Map<String, String> keys, String canonical, String... names) {
        for (String name : names) {
            keys.put(name, canonical);
        }
    }

    public enum Type {
        PERLIN("Perlin", "pe"),
        OPEN_SIMPLEX_2("OpenSimplex2", "si", "simplex"),
        OPEN_SIMPLEX_2S("OpenSimplex2S", "sm"),
        VALUE("Value", "va"),
        VALUE_CUBIC("ValueCubic", "vc"),
        WHITE("White", "wh"),
        CELLULAR("Cellular", "ce", "voronoi", "worley"),
        SHARD("Shard", "sh"),
        GABOR("Gabor", "gb");

        private final String displayName;
        private final List<String> aliases;

        Type(String displayName, String... aliases) {
            this.displayName = displayName;
            this.aliases = List.of(aliases);
        }

        public String displayName() {
            return displayName;
        }

        static Type byName(String input) {
            String name = normalize(input);
            for (Type type : values()) {
                if (normalize(type.displayName).equals(name) || type.aliases.contains(name)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Tipo de ruido desconocido: " + input
                    + " (Perlin, OpenSimplex2, OpenSimplex2S, Value, ValueCubic, White, Cellular, Gabor)");
        }
    }

    private enum Mapping {
        DEFAULT, NONE, OVERRIDE;

        static Mapping byName(String input) {
            return switch (normalize(input)) {
                case "default", "def" -> DEFAULT;
                case "none", "no" -> NONE;
                case "override", "or" -> OVERRIDE;
                default -> throw new IllegalArgumentException("Mapeo de valores desconocido: " + input
                        + " (default, none, override)");
            };
        }
    }

    /** Campo de ruido ya configurado; es inmutable y se puede usar desde varios hilos. */
    public interface NoiseField {
        double sample(double x, double y, double z);

        default double sample(double x, double z) {
            return sample(x, 0.0, z);
        }
    }

    @FunctionalInterface
    private interface RawField {
        double raw(double x, double y, double z);
    }

    private record MappedField(
            RawField field,
            double scaleX,
            double scaleY,
            double scaleZ,
            boolean inverted,
            Mapping mapping,
            double lower,
            double upper,
            double minimum,
            double maximum
    ) implements NoiseField {
        @Override
        public double sample(double x, double y, double z) {
            double value = field.raw(x * scaleX, y * scaleY, z * scaleZ);
            if (inverted) {
                value = -value;
            }
            value = switch (mapping) {
                case DEFAULT -> Math.clamp((value + 1.0) * 0.5, 0.0, 1.0);
                case NONE -> value;
                case OVERRIDE -> (value - lower) / (upper - lower);
            };
            return Math.clamp(value, minimum, maximum);
        }
    }

    /** Ruido blanco: un valor independiente por celda de tamaño 1/frecuencia. */
    private record WhiteField(int seed, double frequency) implements RawField {
        @Override
        public double raw(double x, double y, double z) {
            long hash = mix((long) Math.floor(x * frequency) * 0x9E3779B97F4A7C15L
                    ^ (long) Math.floor(y * frequency) * 0xC2B2AE3D27D4EB4FL
                    ^ (long) Math.floor(z * frequency) * 0x165667B19E3779F9L
                    ^ seed);
            return (hash >>> 11) * 0x1.0p-53 * 2.0 - 1.0;
        }
    }

    /**
     * Ruido de Gabor por convolución dispersa (Lagae et al. 2009): impulsos aleatorios por celda con un núcleo
     * gaussiano modulado por un coseno. Da vetas con dirección, como madera o roca estratificada.
     */
    private record GaborField(
            int seed,
            double frequency,
            double radius,
            double kernelFrequency,
            double spread,
            double[] orientation,
            double jitter,
            int impulses,
            double magnitude
    ) implements RawField {
        @Override
        public double raw(double x, double y, double z) {
            double px = x * frequency / radius;
            double py = y * frequency / radius;
            double pz = z * frequency / radius;
            long cellX = (long) Math.floor(px);
            long cellY = (long) Math.floor(py);
            long cellZ = (long) Math.floor(pz);
            double length = Math.sqrt(orientation[0] * orientation[0] + orientation[1] * orientation[1]
                    + orientation[2] * orientation[2]);
            double ox = length < 1.0E-9 ? 1.0 : orientation[0] / length;
            double oy = length < 1.0E-9 ? 0.0 : orientation[1] / length;
            double oz = length < 1.0E-9 ? 0.0 : orientation[2] / length;
            double sum = 0.0;
            for (long dx = -1; dx <= 1; dx++) {
                for (long dy = -1; dy <= 1; dy++) {
                    for (long dz = -1; dz <= 1; dz++) {
                        long cx = cellX + dx;
                        long cy = cellY + dy;
                        long cz = cellZ + dz;
                        SplittableRandom random = new SplittableRandom(mix(cx * 73856093L ^ cy * 19349663L
                                ^ cz * 83492791L ^ seed));
                        for (int impulse = 0; impulse < impulses; impulse++) {
                            double ix = cx + random.nextDouble() - px;
                            double iy = cy + random.nextDouble() - py;
                            double iz = cz + random.nextDouble() - pz;
                            double weight = random.nextBoolean() ? 1.0 : -1.0;
                            double localFrequency = kernelFrequency * (1.0 + spread * (random.nextDouble() * 2 - 1));
                            double jx = ox + jitter * (random.nextDouble() * 2 - 1);
                            double jy = oy + jitter * (random.nextDouble() * 2 - 1);
                            double jz = oz + jitter * (random.nextDouble() * 2 - 1);
                            double jl = Math.sqrt(jx * jx + jy * jy + jz * jz);
                            double distanceSquared = ix * ix + iy * iy + iz * iz;
                            if (distanceSquared > 1.0 || jl < 1.0E-9) {
                                continue;
                            }
                            double envelope = Math.exp(-Math.PI * distanceSquared * 4.0);
                            double phase = 2.0 * Math.PI * localFrequency * (ix * jx + iy * jy + iz * jz) / jl;
                            sum += weight * envelope * Math.cos(phase);
                        }
                    }
                }
            }
            return Math.clamp(sum * magnitude / Math.sqrt(impulses), -1.0, 1.0);
        }
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 33)) * 0xFF51AFD7ED558CCDL;
        value = (value ^ (value >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return value ^ (value >>> 33);
    }
}
