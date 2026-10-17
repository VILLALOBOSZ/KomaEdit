package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.PathShape;

import java.util.SplittableRandom;

/**
 * Figuras definidas como funciones implícitas en espacio de tubo normalizado:
 * {@code x} e {@code y} van de -1 a 1 sobre la sección (en radios) y {@code z} avanza un radio por cada
 * radio de recorrido. Cada función devuelve un valor de material en [0, 1] si el punto está dentro y un valor
 * negativo si está fuera, de modo que la misma evaluación sirve para la geometría y para el material.
 */
final class TubeShapes {
    private TubeShapes() {
    }

    static TubeShape create(TubeSettings settings, long pathSeed) {
        return switch (settings.shape()) {
            case SWEEP -> new Profile(settings);
            case BRAID -> new Braid(settings);
            case NOISE_TUBE -> new Erosion(settings, settings.seed() != 0L ? settings.seed() : pathSeed);
            default -> throw new IllegalArgumentException(settings.shape().displayName()
                    + " no usa el espacio de tubo");
        };
    }

    static boolean supports(PathShape shape) {
        return shape == PathShape.SWEEP || shape == PathShape.BRAID || shape == PathShape.NOISE_TUBE;
    }

    interface TubeShape {
        /** Alcance radial máximo en radios; sirve para acotar los bloques candidatos. */
        double extent();

        double sample(double x, double y, double z);
    }

    /** Perfil 2D extruido: cada perfil es una "norma" cuyo contorno es el valor 1. */
    static final class Profile implements TubeShape {
        private final TubeSettings.TubeProfile profile;
        private final int sides;
        private final double depth;
        private final double aspect;
        private final double exponent;
        private final boolean filled;
        private final double sector;
        private final double halfSector;
        private final double starInner;
        private final double starNumerator;
        private final double ropeRadius;
        private final double ropeOrbit;
        private final double extent;

        Profile(TubeSettings settings) {
            profile = settings.profile();
            int minimumSides = profile == TubeSettings.TubeProfile.POLYGON
                    || profile == TubeSettings.TubeProfile.STAR ? 3 : 2;
            sides = Math.max(minimumSides, settings.sides());
            depth = settings.depth();
            aspect = settings.aspect();
            exponent = settings.exponent();
            filled = settings.filled();
            sector = Math.PI * 2.0 / sides;
            halfSector = sector * 0.5;
            starInner = 1.0 - Math.clamp(depth, 0.05, 0.95);
            starNumerator = starInner * Math.sin(halfSector);
            double sine = Math.sin(Math.PI / sides);
            ropeRadius = sine / (1.0 + sine);
            ropeOrbit = 1.0 - ropeRadius;
            // Las esquinas de los perfiles cuadrados quedan a √2 del eje.
            extent = switch (profile) {
                case SQUARE, ROUNDED -> Math.sqrt(2.0);
                case RECTANGLE -> Math.sqrt(1.0 + aspect * aspect);
                default -> 1.0;
            };
        }

        @Override
        public double extent() {
            return extent;
        }

        @Override
        public double sample(double x, double y, double z) {
            if (profile == TubeSettings.TubeProfile.ROPE) {
                return rope(x, y);
            }
            double gauge = gauge(x, y);
            return gauge <= 1.0 ? gauge : -1.0;
        }

        private double gauge(double x, double y) {
            double absoluteX = Math.abs(x);
            double absoluteY = Math.abs(y);
            return switch (profile) {
                case CIRCLE -> Math.sqrt(x * x + y * y);
                case SQUARE -> Math.max(absoluteX, absoluteY);
                case DIAMOND -> absoluteX + absoluteY;
                case ROUNDED -> Math.pow(Math.pow(absoluteX, exponent) + Math.pow(absoluteY, exponent),
                        1.0 / exponent);
                case ELLIPSE -> Math.sqrt(x * x + (y / aspect) * (y / aspect));
                case RECTANGLE -> Math.max(absoluteX, absoluteY / aspect);
                case POLYGON -> polygon(x, y);
                case STAR -> star(x, y);
                case FLOWER -> flower(x, y);
                case ROPE -> throw new IllegalStateException();
            };
        }

        /** Polígono regular de circunradio 1 con un vértice hacia arriba. */
        private double polygon(double x, double y) {
            double radial = Math.sqrt(x * x + y * y);
            if (radial < 1.0E-12) {
                return 0.0;
            }
            double offset = wrap(Math.atan2(x, y) + halfSector, sector) - halfSector;
            return radial * Math.cos(offset) / Math.cos(halfSector);
        }

        /** Estrella: aristas rectas entre puntas de radio 1 y valles de radio {@code 1 - depth}. */
        private double star(double x, double y) {
            double radial = Math.sqrt(x * x + y * y);
            if (radial < 1.0E-12) {
                return 0.0;
            }
            double angle = wrap(Math.atan2(x, y), sector);
            double folded = halfSector - Math.abs(angle - halfSector);
            double denominator = Math.cos(folded) * starNumerator
                    - Math.sin(folded) * (starInner * Math.cos(halfSector) - 1.0);
            return radial * denominator / starNumerator;
        }

        /** Flor: pétalos redondeados que tocan el radio 1 y valles en {@code 1 - depth}. */
        private double flower(double x, double y) {
            double radial = Math.sqrt(x * x + y * y);
            if (radial < 1.0E-12) {
                return 0.0;
            }
            double angle = Math.atan2(x, y);
            double reach = 1.0 - depth * (1.0 - Math.abs(Math.cos(sides * angle * 0.5)));
            return radial / reach;
        }

        private static double wrap(double value, double modulus) {
            return value - Math.floor(value / modulus) * modulus;
        }

        /** Cuerda: hebras circulares tangentes entre sí. El material separa cada hebra en su tramo de paleta. */
        private double rope(double x, double y) {
            double angle = Math.atan2(x, y);
            int strand = (int) Math.round(angle / sector);
            double best = Double.MAX_VALUE;
            int bestStrand = 0;
            for (int offset = -1; offset <= 1; offset++) {
                int candidate = strand + offset;
                double centerAngle = candidate * sector;
                double centerX = Math.sin(centerAngle) * ropeOrbit;
                double centerY = Math.cos(centerAngle) * ropeOrbit;
                double dx = x - centerX;
                double dy = y - centerY;
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance < best) {
                    best = distance;
                    bestStrand = Math.floorMod(candidate, sides);
                }
            }
            if (best <= ropeRadius) {
                return (bestStrand + 0.999 * best / ropeRadius) / sides;
            }
            if (filled && Math.sqrt(x * x + y * y) <= ropeOrbit) {
                return 0.0;
            }
            return -1.0;
        }
    }

    /** Trenza: hebras en lazo de Lissajous (ocho) desfasadas que se cruzan por encima y por debajo. */
    static final class Braid implements TubeShape {
        private final int strands;
        private final double thickness;
        private final double amplitude;
        private final double frequency;

        Braid(TubeSettings settings) {
            strands = settings.strands();
            thickness = settings.strandThickness();
            amplitude = 1.0 - thickness;
            double period = Math.max(1.5, settings.sides() * 1.6);
            frequency = Math.PI * 2.0 / period;
        }

        @Override
        public double extent() {
            return 1.0;
        }

        @Override
        public double sample(double x, double y, double z) {
            double best = Double.MAX_VALUE;
            int bestStrand = 0;
            for (int strand = 0; strand < strands; strand++) {
                double phase = z * frequency + Math.PI * 2.0 * strand / strands;
                double centerX = amplitude * Math.sin(phase);
                double centerY = amplitude * Math.sin(phase * 2.0) * 0.5;
                double dx = x - centerX;
                double dy = y - centerY;
                double distance = dx * dx + dy * dy;
                if (distance < best) {
                    best = distance;
                    bestStrand = strand;
                }
            }
            double distance = Math.sqrt(best);
            return distance <= thickness ? (bestStrand + 0.999 * distance / thickness) / strands : -1.0;
        }
    }

    /**
     * Tubo erosionado: núcleo macizo y una corteza que el ruido 3D muerde hasta {@code depth} del radio.
     * El ruido se evalúa en coordenadas de tubo, así que las vetas siguen la curva en vez del mundo.
     */
    static final class Erosion implements TubeShape {
        private final double depth;
        private final double core;
        private final double frequency;
        private final Noise noise;

        Erosion(TubeSettings settings, long seed) {
            depth = settings.depth();
            core = 1.0 - depth;
            frequency = 0.9 + settings.frequency() * 1.5;
            noise = new Noise(seed);
        }

        @Override
        public double extent() {
            return 1.0;
        }

        @Override
        public double sample(double x, double y, double z) {
            double radial = Math.sqrt(x * x + y * y);
            if (radial > 1.0) {
                return -1.0;
            }
            if (radial <= core) {
                return radial;
            }
            double threshold = 0.25 + 0.5 * (radial - core) / depth;
            double value = noise.fractal(x * frequency, y * frequency, z * frequency * 0.75);
            return value >= threshold ? radial : -1.0;
        }
    }

    /** Ruido de Perlin 3D mejorado con permutación por semilla y tres octavas. */
    static final class Noise {
        private final int[] permutation = new int[512];

        Noise(long seed) {
            int[] base = new int[256];
            for (int index = 0; index < 256; index++) {
                base[index] = index;
            }
            SplittableRandom random = new SplittableRandom(seed);
            for (int index = 255; index > 0; index--) {
                int swap = random.nextInt(index + 1);
                int value = base[index];
                base[index] = base[swap];
                base[swap] = value;
            }
            for (int index = 0; index < 512; index++) {
                permutation[index] = base[index & 255];
            }
        }

        /** Valor en [0, 1]. */
        double fractal(double x, double y, double z) {
            double value = 0.0;
            double amplitude = 1.0;
            double scale = 1.0;
            double total = 0.0;
            for (int octave = 0; octave < 3; octave++) {
                value += perlin(x * scale + octave * 17.3, y * scale - octave * 9.1, z * scale + octave * 4.7)
                        * amplitude;
                total += amplitude;
                amplitude *= 0.5;
                scale *= 2.0;
            }
            return Math.clamp(0.5 + value / total * 0.85, 0.0, 1.0);
        }

        double perlin(double x, double y, double z) {
            int cellX = (int) Math.floor(x);
            int cellY = (int) Math.floor(y);
            int cellZ = (int) Math.floor(z);
            double localX = x - cellX;
            double localY = y - cellY;
            double localZ = z - cellZ;
            int a = cellX & 255;
            int b = cellY & 255;
            int c = cellZ & 255;
            double u = fade(localX);
            double v = fade(localY);
            double w = fade(localZ);
            int aa = permutation[permutation[a] + b] + c;
            int ab = permutation[permutation[a] + b + 1] + c;
            int ba = permutation[permutation[a + 1] + b] + c;
            int bb = permutation[permutation[a + 1] + b + 1] + c;
            return lerp(w,
                    lerp(v,
                            lerp(u, gradient(permutation[aa], localX, localY, localZ),
                                    gradient(permutation[ba], localX - 1, localY, localZ)),
                            lerp(u, gradient(permutation[ab], localX, localY - 1, localZ),
                                    gradient(permutation[bb], localX - 1, localY - 1, localZ))),
                    lerp(v,
                            lerp(u, gradient(permutation[aa + 1], localX, localY, localZ - 1),
                                    gradient(permutation[ba + 1], localX - 1, localY, localZ - 1)),
                            lerp(u, gradient(permutation[ab + 1], localX, localY - 1, localZ - 1),
                                    gradient(permutation[bb + 1], localX - 1, localY - 1, localZ - 1))));
        }

        private static double fade(double value) {
            return value * value * value * (value * (value * 6 - 15) + 10);
        }

        private static double lerp(double amount, double first, double second) {
            return first + amount * (second - first);
        }

        private static double gradient(int hash, double x, double y, double z) {
            int low = hash & 15;
            double u = low < 8 ? x : y;
            double v = low < 4 ? y : low == 12 || low == 14 ? x : z;
            return ((low & 1) == 0 ? u : -u) + ((low & 2) == 0 ? v : -v);
        }
    }
}
