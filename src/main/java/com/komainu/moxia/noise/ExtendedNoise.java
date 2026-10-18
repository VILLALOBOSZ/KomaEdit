package com.komainu.moxia.noise;

import java.util.Locale;
import java.util.function.IntFunction;

/**
 * Ruidos y opciones de ezEdits que {@link FastNoiseLite} no trae: el celular con distancias de Minkowski o
 * redondeadas y con los retornos {@code Rounded} y {@code NoiseLookup}, el ruido {@code Shard}, los fractales para
 * estos campos propios y las distorsiones de dominio {@code Flow} y {@code Turbulence}.
 *
 * <p>Son implementaciones propias a partir de la descripción pública de cada opción, no copias: los valores no
 * coinciden bloque a bloque con los de ezEdits, pero sí su carácter. Todo es determinista y sin estado mutable, así
 * que los campos se pueden usar desde varios hilos.</p>
 */
final class ExtendedNoise {
    /** Distancia máxima a la que se separa el punto de una celda de su centro con {@code cJ:1}. */
    private static final double JITTER = 0.45;

    private ExtendedNoise() {
    }

    // ------------------------------------------------------------------ Celular

    /** Función de distancia del celular propio. */
    enum Distance {
        EUCLIDEAN, EUCLIDEAN_SQ, MANHATTAN, HYBRID, MINKOWSKI, ROUNDED;

        static Distance byName(String name) {
            return switch (name) {
                case "euclidean", "e" -> EUCLIDEAN;
                case "euclideansq", "sq" -> EUCLIDEAN_SQ;
                case "manhattan", "man" -> MANHATTAN;
                case "hybrid", "h" -> HYBRID;
                case "minkowski1", "minkovski1", "m1", "minkowski4", "minkowvki4", "minkovski4", "m4",
                     "minkowski99", "minkovski99", "m99" -> MINKOWSKI;
                case "rounded", "r" -> ROUNDED;
                default -> null;
            };
        }

        /** Exponente de las variantes de Minkowski ({@code m1}, {@code m4}, {@code m99}). */
        static double exponent(String name) {
            if (name.endsWith("99")) {
                return 99.0;
            }
            return name.endsWith("4") ? 4.0 : 1.0;
        }
    }

    /** ¿Hace falta el celular propio para esta distancia y este retorno? */
    static boolean needsOwnCellular(String distance, String returnType) {
        Distance parsed = Distance.byName(distance);
        return parsed == Distance.MINKOWSKI || parsed == Distance.ROUNDED
                || returnType.equals("noiselookup") || returnType.equals("n")
                || returnType.equals("rounded") || returnType.equals("r");
    }

    /**
     * Celular sobre una rejilla de puntos con temblor. Devuelve, como {@link FastNoiseLite}, un valor de −1 a 1
     * aproximadamente.
     *
     * @param lookup ruido que da el valor de cada celda con {@code NoiseLookup} (o {@code null})
     * @param lookupFrequency frecuencia del ruido de {@code NoiseLookup}, en celdas
     */
    record Cellular(int seed, double frequency, double jitter, Distance distance, double exponent,
                    String returnType, NoiseSpec.RawField lookup, double lookupFrequency)
            implements NoiseSpec.RawField {

        Cellular {
            returnType = returnType.toLowerCase(Locale.ROOT);
        }

        @Override
        public double raw(double x, double y, double z) {
            double px = x * frequency;
            double py = y * frequency;
            double pz = z * frequency;
            long cx = (long) Math.floor(px + 0.5);
            long cy = (long) Math.floor(py + 0.5);
            long cz = (long) Math.floor(pz + 0.5);
            double first = Double.MAX_VALUE;
            double second = Double.MAX_VALUE;
            long closest = 0;
            double closestX = 0;
            double closestY = 0;
            double closestZ = 0;
            boolean rounded = returnType.equals("rounded") || returnType.equals("r");
            double soft = 0.0;
            for (long dx = -1; dx <= 1; dx++) {
                for (long dy = -1; dy <= 1; dy++) {
                    for (long dz = -1; dz <= 1; dz++) {
                        long hash = hash(seed, cx + dx, cy + dy, cz + dz);
                        double pointX = cx + dx + unit(hash, 0) * jitter * JITTER;
                        double pointY = cy + dy + unit(hash, 1) * jitter * JITTER;
                        double pointZ = cz + dz + unit(hash, 2) * jitter * JITTER;
                        double d = measure(pointX - px, pointY - py, pointZ - pz);
                        if (rounded) {
                            soft += Math.exp(-8.0 * d);
                        }
                        if (d < first) {
                            second = first;
                            first = d;
                            closest = hash;
                            closestX = pointX;
                            closestY = pointY;
                            closestZ = pointZ;
                        } else if (d < second) {
                            second = d;
                        }
                    }
                }
            }
            return switch (returnType) {
                case "cellvalue", "cell" -> unit(closest, 3);
                case "distance", "1" -> first - 1.0;
                case "distance2", "2" -> second - 1.0;
                case "distance2add", "2add", "add" -> (second + first) * 0.5 - 1.0;
                case "distance2sub", "2sub", "sub" -> second - first - 1.0;
                case "distance2mul", "2mul", "mul" -> second * first * 0.5 - 1.0;
                case "distance2div", "2div", "div" -> first / Math.max(second, 1.0E-9) - 1.0;
                case "distancesquared", "sq" -> first * first - 1.0;
                case "distanceinverse", "inv" -> inverse(first);
                case "distancelog", "log" -> Math.log(first + 1.0E-4) * 0.5;
                case "distanceexp", "exp" -> Math.exp(first) - 2.0;
                case "distance2sq", "2sq" -> second * second - 1.0;
                case "distance2inv", "2inv" -> inverse(second);
                case "distance2log", "2log" -> Math.log(second + 1.0E-4) * 0.5;
                case "distance2exp", "2exp" -> Math.exp(second) - 2.0;
                case "edge", "e" -> 1.0 - 2.0 * Math.clamp((second - first) * 4.0, 0.0, 1.0);
                // Mínimo suave de todas las distancias: celdas con las esquinas redondeadas.
                case "rounded", "r" -> Math.clamp(-Math.log(Math.max(soft, 1.0E-300)) / 8.0 * 1.6 - 1.0, -1.0, 1.0);
                case "noiselookup", "n" -> lookup == null ? 0.0
                        : lookup.raw(closestX * lookupFrequency, closestY * lookupFrequency,
                        closestZ * lookupFrequency);
                default -> throw new IllegalArgumentException("Tipo de retorno celular no disponible: " + returnType);
            };
        }

        private double measure(double x, double y, double z) {
            double ax = Math.abs(x);
            double ay = Math.abs(y);
            double az = Math.abs(z);
            return switch (distance) {
                case EUCLIDEAN -> Math.sqrt(x * x + y * y + z * z);
                case EUCLIDEAN_SQ -> x * x + y * y + z * z;
                case MANHATTAN -> ax + ay + az;
                case HYBRID -> ax + ay + az + x * x + y * y + z * z;
                case MINKOWSKI -> minkowski(ax, ay, az, exponent);
                case ROUNDED -> 0.5 * (Math.sqrt(x * x + y * y + z * z) + Math.max(ax, Math.max(ay, az)));
            };
        }
    }

    /** Distancia de Minkowski de exponente {@code p}; con p alto se acerca a la de Chebyshev sin desbordarse. */
    static double minkowski(double ax, double ay, double az, double p) {
        if (p == 1.0) {
            return ax + ay + az;
        }
        double largest = Math.max(ax, Math.max(ay, az));
        if (largest < 1.0E-12) {
            return 0.0;
        }
        double sum = Math.pow(ax / largest, p) + Math.pow(ay / largest, p) + Math.pow(az / largest, p);
        return largest * Math.pow(sum, 1.0 / p);
    }

    private static double inverse(double distance) {
        return Math.clamp(1.0 / Math.max(distance, 0.05) - 1.0, -1.0, 1.0);
    }

    // ------------------------------------------------------------------ Shard

    /**
     * Ruido de esquirlas: cada celda es un plano con su propia inclinación, como las caras de un cristal roto. La
     * nitidez ({@code h}, de 0 a 1) decide si las celdas se cortan en seco (1) o se funden con las vecinas (0).
     */
    record Shard(int seed, double frequency, double sharpness) implements NoiseSpec.RawField {
        @Override
        public double raw(double x, double y, double z) {
            double px = x * frequency;
            double py = y * frequency;
            double pz = z * frequency;
            long cx = (long) Math.floor(px + 0.5);
            long cy = (long) Math.floor(py + 0.5);
            long cz = (long) Math.floor(pz + 0.5);
            double sharp = 2.0 + Math.clamp(sharpness, 0.0, 1.0) * 58.0;
            double[] distances = new double[27];
            double[] values = new double[27];
            double nearest = Double.MAX_VALUE;
            int index = 0;
            for (long dx = -1; dx <= 1; dx++) {
                for (long dy = -1; dy <= 1; dy++) {
                    for (long dz = -1; dz <= 1; dz++) {
                        long hash = hash(seed, cx + dx, cy + dy, cz + dz);
                        double ox = cx + dx + unit(hash, 0) * JITTER - px;
                        double oy = cy + dy + unit(hash, 1) * JITTER - py;
                        double oz = cz + dz + unit(hash, 2) * JITTER - pz;
                        double nx = unit(hash, 4);
                        double ny = unit(hash, 5);
                        double nz = unit(hash, 6);
                        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
                        if (length < 1.0E-9) {
                            nx = 1.0;
                            length = 1.0;
                        }
                        double distance = Math.sqrt(ox * ox + oy * oy + oz * oz);
                        // El plano pasa por el punto de la celda con una altura propia; dentro de la celda sube en la
                        // dirección de su normal.
                        double value = unit(hash, 3) * 0.5 - (ox * nx + oy * ny + oz * nz) / length * 1.5;
                        distances[index] = distance;
                        values[index] = Math.clamp(value, -1.0, 1.0);
                        nearest = Math.min(nearest, distance);
                        index++;
                    }
                }
            }
            double weights = 0.0;
            double sum = 0.0;
            for (int cell = 0; cell < 27; cell++) {
                double weight = Math.exp(-(distances[cell] - nearest) * sharp);
                weights += weight;
                sum += weight * values[cell];
            }
            return sum / weights;
        }
    }

    // ------------------------------------------------------------------ Fractal

    /** Fractal de los campos propios, con las mismas fórmulas que {@link FastNoiseLite}. */
    static NoiseSpec.RawField fractal(IntFunction<NoiseSpec.RawField> layers, int seed, String type, int octaves,
                                      double lacunarity, double gain, double weighted, double pingPong) {
        if (type.equals("none") || type.equals("n") || octaves <= 1 && (type.equals("fbm") || type.equals("f"))) {
            return layers.apply(seed);
        }
        NoiseSpec.RawField[] fields = new NoiseSpec.RawField[octaves];
        for (int octave = 0; octave < octaves; octave++) {
            fields[octave] = layers.apply(seed + octave);
        }
        double bounding = 0.0;
        double amplitude = 1.0;
        for (int octave = 0; octave < octaves; octave++) {
            bounding += amplitude;
            amplitude *= Math.abs(gain);
        }
        double start = 1.0 / Math.max(bounding, 1.0E-9);
        return switch (type) {
            case "fbm", "f" -> (x, y, z) -> {
                double sum = 0.0;
                double amp = start;
                double fx = x;
                double fy = y;
                double fz = z;
                for (NoiseSpec.RawField field : fields) {
                    double noise = field.raw(fx, fy, fz);
                    sum += noise * amp;
                    amp *= lerp(1.0, Math.min(noise + 1.0, 2.0) * 0.5, weighted) * gain;
                    fx *= lacunarity;
                    fy *= lacunarity;
                    fz *= lacunarity;
                }
                return sum;
            };
            case "ridged", "r" -> (x, y, z) -> {
                double sum = 0.0;
                double amp = start;
                double fx = x;
                double fy = y;
                double fz = z;
                for (NoiseSpec.RawField field : fields) {
                    double noise = Math.abs(field.raw(fx, fy, fz));
                    sum += (noise * -2.0 + 1.0) * amp;
                    amp *= lerp(1.0, 1.0 - noise, weighted) * gain;
                    fx *= lacunarity;
                    fy *= lacunarity;
                    fz *= lacunarity;
                }
                return sum;
            };
            case "pingpong", "p" -> (x, y, z) -> {
                double sum = 0.0;
                double amp = start;
                double fx = x;
                double fy = y;
                double fz = z;
                for (NoiseSpec.RawField field : fields) {
                    double noise = pingPong((field.raw(fx, fy, fz) + 1.0) * pingPong);
                    sum += (noise - 0.5) * 2.0 * amp;
                    amp *= lerp(1.0, noise, weighted) * gain;
                    fx *= lacunarity;
                    fy *= lacunarity;
                    fz *= lacunarity;
                }
                return sum;
            };
            default -> throw new IllegalArgumentException("Tipo de fractal desconocido: " + type
                    + " (none, fbm, ridged, pingpong)");
        };
    }

    private static double pingPong(double value) {
        double t = value - (int) (value * 0.5) * 2;
        return t < 1 ? t : 2 - t;
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    // ------------------------------------------------------------------ Distorsión de dominio

    /**
     * Distorsión de dominio propia.
     *
     * <ul>
     *     <li>{@code Turbulence}: cada eje se desplaza con el valor absoluto de un fBm, que da pliegues marcados
     *     como los de la turbulencia clásica de Perlin.</li>
     *     <li>{@code Flow}: el desplazamiento es el rotacional de tres ruidos; no tiene fuentes ni sumideros, así
     *     que el dominio gira en remolinos sin amontonarse.</li>
     * </ul>
     */
    static NoiseSpec.RawField warp(NoiseSpec.RawField field, boolean flow, int seed, double frequency,
                                   double amplitude, int octaves, double gain, double lacunarity) {
        FastNoiseLite[] potentials = new FastNoiseLite[3];
        for (int axis = 0; axis < 3; axis++) {
            FastNoiseLite noise = new FastNoiseLite(seed + 101 + axis * 37);
            noise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
            noise.SetFrequency((float) frequency);
            noise.SetFractalType(octaves > 1 ? FastNoiseLite.FractalType.FBm : FastNoiseLite.FractalType.None);
            noise.SetFractalOctaves(Math.max(1, octaves));
            noise.SetFractalGain((float) gain);
            noise.SetFractalLacunarity((float) lacunarity);
            potentials[axis] = noise;
        }
        if (!flow) {
            return (x, y, z) -> {
                double ox = (Math.abs(potentials[0].GetNoise(x, y, z)) * 2.0 - 0.5) * amplitude;
                double oy = (Math.abs(potentials[1].GetNoise(x, y, z)) * 2.0 - 0.5) * amplitude;
                double oz = (Math.abs(potentials[2].GetNoise(x, y, z)) * 2.0 - 0.5) * amplitude;
                return field.raw(x + ox, y + oy, z + oz);
            };
        }
        // Paso de las diferencias centrales: una décima de rasgo, en bloques.
        double step = 0.1 / Math.max(frequency, 1.0E-6);
        double scale = amplitude * step * 2.5;
        return (x, y, z) -> {
            FastNoiseLite a = potentials[0];
            FastNoiseLite b = potentials[1];
            FastNoiseLite c = potentials[2];
            double dCdy = (c.GetNoise(x, y + step, z) - c.GetNoise(x, y - step, z));
            double dBdz = (b.GetNoise(x, y, z + step) - b.GetNoise(x, y, z - step));
            double dAdz = (a.GetNoise(x, y, z + step) - a.GetNoise(x, y, z - step));
            double dCdx = (c.GetNoise(x + step, y, z) - c.GetNoise(x - step, y, z));
            double dBdx = (b.GetNoise(x + step, y, z) - b.GetNoise(x - step, y, z));
            double dAdy = (a.GetNoise(x, y + step, z) - a.GetNoise(x, y - step, z));
            double inverse = 1.0 / (2.0 * step);
            double ox = (dCdy - dBdz) * inverse * scale;
            double oy = (dAdz - dCdx) * inverse * scale;
            double oz = (dBdx - dAdy) * inverse * scale;
            return field.raw(x + ox, y + oy, z + oz);
        };
    }

    // ------------------------------------------------------------------ Azar por celda

    private static long hash(int seed, long x, long y, long z) {
        return mix(x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ seed * 0x27D4EB2FL);
    }

    /** Un número de −1 a 1 sacado del hash de la celda; {@code channel} da valores independientes. */
    private static double unit(long hash, int channel) {
        long value = mix(hash + channel * 0x632BE59BD9B4E019L);
        return (value >>> 11) * 0x1.0p-53 * 2.0 - 1.0;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 33)) * 0xFF51AFD7ED558CCDL;
        value = (value ^ (value >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return value ^ (value >>> 33);
    }
}
