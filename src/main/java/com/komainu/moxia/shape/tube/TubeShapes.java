package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeGenerator;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;

import java.util.SplittableRandom;

/**
 * Figuras definidas como funciones implícitas en espacio de tubo normalizado:
 * {@code x} e {@code y} van de -1 a 1 sobre la sección (en radios) y {@code z} avanza un radio por cada
 * radio de recorrido. Cada función devuelve un valor de material en [0, 1] si el punto está dentro y un valor
 * negativo si está fuera, de modo que la misma evaluación sirve para la geometría y para el material. Un valor
 * menor que -1 indica además cuántos bloques faltan hasta la figura ({@code -1 - holgura}).
 *
 * <p>Además de las figuras propias del tubo, aquí viven las versiones exactas de las figuras clásicas
 * (cadena, anillos, cuentas, oscilar, red, panal, nudos y espiral) que se eligen con {@link TubeEngine#EXACT}.
 * Leen las mismas opciones y proporciones que {@code ShapeGenerator}, pero en vez de estampar pinceles miden la
 * distancia de cada punto al hilo, al anillo o a la cinta, así que salen sin huecos.</p>
 */
final class TubeShapes {
    private TubeShapes() {
    }

    static TubeShape create(TubeSettings settings, ShapeParameters parameters, TubeCenterline line, long pathSeed) {
        return switch (settings.shape()) {
            case SWEEP -> new Profile(settings);
            case BRAID -> new Braid(settings);
            case NOISE_TUBE -> new Erosion(settings, settings.seed() != 0L ? settings.seed() : pathSeed);
            case EXPRESSION -> new Formula(settings, settings.seed() != 0L ? settings.seed() : pathSeed);
            case BEADS -> new Beads(parameters, line);
            case RINGS -> new Rings(parameters, line);
            case CHAINLINK -> new Chainlink(parameters, line);
            case OSCILLATE -> new Oscillate(parameters);
            case FISHNET -> new Fishnet(parameters);
            case HONEYCOMB -> new Honeycomb(parameters, line);
            case KNOTS -> new Knots(parameters, line);
            case COIL -> new Coil(parameters);
            default -> throw new IllegalArgumentException(settings.shape().displayName()
                    + " no usa el espacio de tubo");
        };
    }

    static boolean supports(PathShape shape) {
        return shape == PathShape.SWEEP || shape == PathShape.BRAID || shape == PathShape.NOISE_TUBE
                || shape == PathShape.EXPRESSION;
    }

    /** Figuras clásicas que tienen versión exacta en espacio de tubo. */
    static boolean supportsExact(PathShape shape) {
        return switch (shape) {
            case CHAINLINK, RINGS, BEADS, OSCILLATE, FISHNET, HONEYCOMB, KNOTS, COIL -> true;
            default -> false;
        };
    }

    interface TubeShape {
        /** Alcance radial máximo en radios; sirve para acotar los bloques candidatos. */
        double extent();

        double sample(double x, double y, double z);

        /** Igual, con el progreso (0 a 1) a lo largo del recorrido para las figuras que lo usan. */
        default double sample(double x, double y, double z, double progress) {
            return sample(x, y, z);
        }

        /**
         * Igual, conociendo todo el entorno del punto: además del progreso, {@code blocks} es cuántos bloques mide
         * un radio de sección y {@code stretch} cuánto se estira la coordenada longitudinal. Las figuras clásicas
         * lo usan para que sus hilos nunca bajen de un grosor mínimo en bloques y para que el espaciado de la
         * curva separe los motivos sin aplastarlos.
         */
        default double sample(double x, double y, double z, double progress, double blocks, double stretch) {
            return sample(x, y, z, progress);
        }

        /** Margen radial extra, en bloques, que puede ocupar la figura más allá de {@link #extent()}. */
        default double margin() {
            return 0.0;
        }

        /**
         * Cuánto puede asomar la figura, en radios, más allá de un extremo plano. Las figuras de motivos (cuentas,
         * eslabones…) lo usan para que el primero y el último salgan enteros, como en el motor clásico.
         */
        default double overhang() {
            return 0.0;
        }
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

    /**
     * Expresión: la fórmula del usuario decide cada punto. Solo se evalúa dentro del cuadrado [-1, 1] de la
     * sección; una salida mayor que cero coloca bloque y su valor, limitado a 1, es el material. El alcance sale
     * de la cota radial de la fórmula: √2 (las esquinas del cuadrado) salvo que la fórmula se limite a un radio.
     */
    static final class Formula implements TubeShape {
        private final TubeFormula.Runner runner;
        private final double extent;

        Formula(TubeSettings settings, long seed) {
            runner = settings.formula().runner(seed, settings.sides());
            extent = settings.formula().radialBound();
        }

        @Override
        public double extent() {
            return extent;
        }

        @Override
        public double sample(double x, double y, double z) {
            return sample(x, y, z, 0.0);
        }

        @Override
        public double sample(double x, double y, double z, double progress) {
            if (Math.abs(x) > 1.0 || Math.abs(y) > 1.0) {
                return -1.0;
            }
            double value = runner.evaluate(x, y, z, progress);
            // NaN también queda fuera: la comparación es falsa.
            return value > 0.0 ? Math.min(value, 1.0) : -1.0;
        }
    }

    /**
     * Base de las figuras clásicas en motor exacto. Las proporciones salen de las mismas fórmulas que
     * {@code ShapeGenerator}; las medidas en bloques se pasan a radios con la escala base, así crecen y encogen
     * con los radios por fotogramas y con la curva de modificadores.
     *
     * <p>Fuera de la figura devuelven {@code -1 - holgura}: la holgura es una cota, en bloques y hasta
     * {@link #CLEARANCE}, de la distancia que falta hasta la figura. Con ella el voxelizador se salta el
     * supermuestreo de los bloques que quedan claramente fuera, que en las figuras huecas son casi todos.</p>
     */
    abstract static class Classic implements TubeShape {
        /**
         * Radio mínimo de un hilo (y semigrosor mínimo de una cinta) en bloques: con menos, un hilo en diagonal
         * podría saltarse bloques y una cinta dejaría rendijas en el lado interior de las curvas cerradas.
         */
        static final double MINIMUM_WIRE = 0.87;
        /** Holgura máxima que se mide, en bloques; más lejos basta con saber que el bloque queda fuera. */
        static final double CLEARANCE = 1.2;
        final double baseRadius;
        final ShapeOptions options;

        Classic(ShapeParameters parameters) {
            baseRadius = parameters.radius();
            options = parameters.options();
        }

        @Override
        public final double sample(double x, double y, double z) {
            return sample(x, y, z, baseRadius, 1.0);
        }

        @Override
        public final double sample(double x, double y, double z, double progress, double blocks, double stretch) {
            return sample(x, y, z, blocks, stretch);
        }

        /** La figura en el punto, con {@code blocks} bloques por radio de sección y el estiramiento local. */
        abstract double sample(double x, double y, double z, double blocks, double stretch);

        @Override
        public double margin() {
            return MINIMUM_WIRE;
        }

        /** Radio del hilo que deja un pincel clásico de grosor {@code thickness} (cubre {@code thickness - 0,65}). */
        static double wireBlocks(int thickness) {
            return Math.max(MINIMUM_WIRE, thickness - 0.65);
        }

        // Mismas fórmulas que ShapeGenerator, para que espaciado y grosor signifiquen lo mismo en los dos motores.
        static double scaledSpacing(ShapeParameters parameters, double minimum, double maximum) {
            return minimum + (maximum - minimum) * (parameters.spacing() - 2.0) / 6.0;
        }

        static int structuralThickness(ShapeParameters parameters) {
            int automatic = (int) Math.ceil(parameters.radius() / 8.0);
            return Math.clamp(automatic + parameters.thickness() - 1, 1, 4);
        }

        /** Ángulo con el criterio de {@code ShapeGenerator.radial}: 0 mira a la normal y crece hacia la binormal. */
        static double angle(double x, double y) {
            return Math.atan2(-x, y);
        }

        /** Lleva {@code value} al intervalo [-period/2, period/2]. */
        static double centered(double value, double period) {
            return value - period * Math.rint(value / period);
        }

        /** Valor de un punto que queda fuera de la figura, a {@code distance} bloques o más de ella. */
        static double outside(double distance) {
            return -1.0 - Math.clamp(distance, 0.0, CLEARANCE);
        }

        /** Franja {@code index} de {@code count} en la paleta, recorrida de núcleo (0) a superficie (1). */
        static double band(int index, int count, double local) {
            return (index + 0.999 * Math.clamp(local, 0.0, 1.0)) / count;
        }

        static double power(double value, double exponent) {
            return exponent == 2.0 ? value * value : Math.pow(value, exponent);
        }

        /** Norma superelíptica: vale 1 sobre el contorno; círculo con exponente 2 y casi cuadrado con exponentes altos. */
        static double superellipse(double x, double y, double exponent) {
            if (exponent == 2.0) {
                return Math.sqrt(x * x + y * y);
            }
            return Math.pow(Math.pow(Math.abs(x), exponent) + Math.pow(Math.abs(y), exponent), 1.0 / exponent);
        }

        /** Distancia al cuadrado del origen al segmento {@code a → b}. */
        static double segmentDistanceSquared(double ax, double ay, double az, double bx, double by, double bz) {
            double dx = bx - ax;
            double dy = by - ay;
            double dz = bz - az;
            double length = dx * dx + dy * dy + dz * dz;
            double amount = length < 1.0E-12 ? 0.0
                    : Math.clamp(-(ax * dx + ay * dy + az * dz) / length, 0.0, 1.0);
            double ex = ax + dx * amount;
            double ey = ay + dy * amount;
            double ez = az + dz * amount;
            return ex * ex + ey * ey + ez * ez;
        }
    }

    /**
     * Centros de los motivos repetidos en coordenada longitudinal de tubo. En recorridos abiertos se reparten como
     * en el motor clásico (uno por paso desde el inicio y otro en el final si sobra más de un tercio de paso); en
     * los cerrados el paso se ajusta para que quepa un número entero de motivos y no quede costura.
     */
    static final class Stations {
        private final double pitch;
        private final int count;
        private final boolean closed;
        private final double extra;

        private Stations(double pitch, int count, boolean closed, double extra) {
            this.pitch = pitch;
            this.count = count;
            this.closed = closed;
            this.extra = extra;
        }

        /** @param even en lazos cerrados, fuerza un número par de motivos para que la alternancia empalme */
        static Stations along(TubeCenterline line, double pitch, boolean even) {
            double total = line.longitudinal[line.count - 1];
            double step = Math.max(0.1, pitch);
            if (line.closed) {
                int count = even
                        ? Math.max(2, 2 * (int) Math.round(total / (step * 2.0)))
                        : Math.max(1, (int) Math.round(total / step));
                return new Stations(total / count, count, true, Double.NaN);
            }
            int count = (int) Math.floor(total / step + 1.0E-6) + 1;
            double last = (count - 1) * step;
            return new Stations(step, count, false, total - last > step * 0.35 ? total : Double.NaN);
        }

        double pitch() {
            return pitch;
        }

        /** Primera estación cuyo centro puede estar a {@code reach} o menos de {@code z}. */
        int first(double z, double reach) {
            int index = (int) Math.ceil((z - reach) / pitch);
            return closed ? index : Math.max(0, index);
        }

        /** Última estación cuyo centro puede estar a {@code reach} o menos de {@code z}. */
        int last(double z, double reach) {
            int index = (int) Math.floor((z + reach) / pitch);
            if (closed) {
                return index;
            }
            if (!Double.isNaN(extra) && Math.abs(z - extra) <= reach) {
                return count;
            }
            return Math.min(index, count - 1);
        }

        boolean exists(int index) {
            return closed || index >= 0 && (index < count || index == count && !Double.isNaN(extra));
        }

        double center(int index) {
            return !closed && index == count ? extra : index * pitch;
        }

        /** Número de motivo; en lazos cerrados vuelve a 0 tras el último. */
        int identity(int index) {
            return closed ? Math.floorMod(index, count) : index;
        }
    }

    /**
     * Cuentas: esferas con el paso clásico. Como en el motor clásico son cáscaras de {@code grosor + 0,45} bloques
     * que llegan algo más allá del radio; con {@code hollow:false} salen macizas.
     */
    static final class Beads extends Classic {
        /** La cáscara clásica llega algo más allá del radio; con este margen las cuentas miden lo mismo. */
        private static final double PADDING = 0.4;
        private final double shell;
        private final Stations stations;

        Beads(ShapeParameters parameters, TubeCenterline line) {
            super(parameters);
            double gap = Math.clamp(options.get("gap", 0.0), -0.5, 2.0);
            boolean solid = options.has("hollow") && options.get("hollow", 1.0) < 0.5;
            shell = solid ? Double.MAX_VALUE : parameters.thickness() + 0.45;
            stations = Stations.along(line, scaledSpacing(parameters, 1.55, 4.60) + gap, false);
        }

        @Override
        public double extent() {
            return 1.0;
        }

        @Override
        public double margin() {
            return PADDING;
        }

        @Override
        public double overhang() {
            return 1.0 + PADDING / baseRadius;
        }

        @Override
        public double sample(double x, double y, double z, double blocks, double stretch) {
            double size = 1.0 + PADDING / blocks;
            // En unidades de cuenta: 1 es la superficie y `inner` el interior de la cáscara.
            double scale = size * blocks;
            double inner = 1.0 - shell / scale;
            double radial = (x * x + y * y) / (size * size);
            if (radial > 1.0) {
                return outside((Math.sqrt(radial) - 1.0) * scale);
            }
            double reach = (size + CLEARANCE / blocks) / stretch;
            double gap = Double.MAX_VALUE;
            int last = stations.last(z, reach);
            for (int index = stations.first(z, reach); index <= last; index++) {
                double along = (z - stations.center(index)) * stretch / size;
                double distance = Math.sqrt(radial + along * along);
                if (distance <= 1.0 && distance >= inner) {
                    return band(stations.identity(index) & 1, 2, distance);
                }
                gap = Math.min(gap, distance > 1.0 ? distance - 1.0 : inner - distance);
            }
            return outside(gap * scale);
        }
    }

    /**
     * Anillos: supertoros del motor clásico. La corona radial mide dos veces {@code thickness} hacia dentro desde
     * el radio, el fondo es {@code thickness·(1 + extrusion)} y los exponentes dan la forma exterior y la sección.
     */
    static final class Rings extends Classic {
        private final double width;
        private final double depth;
        private final double majorExponent;
        private final double minorExponent;
        private final double extent;
        private final double normFactor;
        private final Stations stations;

        Rings(ShapeParameters parameters, TubeCenterline line) {
            super(parameters);
            double radialThickness = options.has("thickness")
                    ? baseRadius * Math.clamp(options.get("thickness", 0.15), 0.03, 0.90)
                    : structuralThickness(parameters);
            radialThickness = Math.max(0.8, radialThickness);
            double extrusion = Math.clamp(options.get("extrusion", 0.20), -0.60, 3.0);
            double depthBlocks = Math.max(0.8, radialThickness * (1.0 + extrusion));
            double gap = Math.clamp(options.get("gap", 0.0), -0.75, 4.0);
            majorExponent = Math.clamp(options.get("majorexponent", 2.0), 0.6, 20.0);
            minorExponent = Math.clamp(options.get("minorexponent", 2.0), 0.6, 20.0);
            double pitch = baseRadius * scaledSpacing(parameters, 0.32, 1.60)
                    + gap * Math.max(depthBlocks, baseRadius * 0.35);
            width = radialThickness / baseRadius;
            depth = depthBlocks / baseRadius;
            // Con exponentes altos las esquinas del contorno se alejan hasta √2.
            extent = Math.max(1.0, Math.pow(2.0, 0.5 - 1.0 / majorExponent));
            // Cuánto puede valer la norma superelíptica por cada bloque de distancia real (cota de la holgura).
            normFactor = majorExponent >= 2.0 ? 1.0 : Math.pow(2.0, 1.0 / majorExponent - 0.5);
            stations = Stations.along(line, pitch / baseRadius, false);
        }

        @Override
        public double extent() {
            return extent;
        }

        @Override
        public double margin() {
            return 0.0;
        }

        @Override
        public double overhang() {
            return depth;
        }

        @Override
        public double sample(double x, double y, double z, double blocks, double stretch) {
            double width = Math.max(this.width, 0.8 / blocks);
            double depth = Math.max(this.depth, 0.8 / blocks);
            double major = Math.max(0.5 / blocks, 1.0 - width);
            double radialGap = Math.abs(superellipse(x, y, majorExponent) - major) - width;
            if (radialGap > 0.0) {
                // Por debajo de exponente 1 la norma no cumple la desigualdad triangular y no da cota.
                return majorExponent >= 1.0 ? outside(radialGap / normFactor * blocks) : -1.0;
            }
            double radialTerm = power(radialGap / width + 1.0, minorExponent);
            double reach = (depth + CLEARANCE / blocks) / stretch;
            double best = Double.MAX_VALUE;
            double axialGap = Double.MAX_VALUE;
            int last = stations.last(z, reach);
            for (int index = stations.first(z, reach); index <= last; index++) {
                double along = Math.abs(z - stations.center(index)) * stretch;
                if (along > depth) {
                    axialGap = Math.min(axialGap, along - depth);
                    continue;
                }
                axialGap = 0.0;
                best = Math.min(best, radialTerm + power(along / depth, minorExponent));
            }
            return best <= 1.0 ? 0.999 * Math.pow(best, 1.0 / minorExponent) : outside(axialGap * blocks);
        }
    }

    /**
     * Cadena: eslabones superelípticos alternados 90° con semiejes {@code (1 + extrusion)·stretch} a lo largo del
     * recorrido y 0,68 de través, y alambre del grosor de anillo clásico. La sección del alambre sigue
     * {@code minorexponent}. En lazos cerrados el número de eslabones es par para que la alternancia empalme.
     */
    static final class Chainlink extends Classic {
        private static final double WIDTH = 0.68;
        private final double length;
        private final double wire;
        private final double minorExponent;
        private final double wireReach;
        private final Outline outline;
        private final Stations stations;

        Chainlink(ShapeParameters parameters, TubeCenterline line) {
            super(parameters);
            int thickness = options.has("thickness")
                    ? Math.clamp((int) Math.round(baseRadius * Math.clamp(options.get("thickness", 0.15), 0.03, 0.90)),
                    1, 8)
                    : structuralThickness(parameters);
            double extrusion = Math.clamp(options.get("extrusion", 0.20), -0.40, 2.0);
            double stretch = Math.clamp(options.get("stretch", 1.0), 0.45, 3.0);
            double gap = Math.clamp(options.get("gap", 0.0), -0.75, 4.0);
            double majorExponent = Math.clamp(options.get("majorexponent", 2.0), 0.6, 20.0);
            minorExponent = Math.clamp(options.get("minorexponent", 2.0), 0.6, 20.0);
            length = (1.0 + extrusion) * stretch;
            wire = wireBlocks(thickness) / baseRadius;
            // Con exponente de sección mayor que 2 las esquinas del alambre se alejan algo más del alma.
            wireReach = Math.max(1.0, Math.pow(2.0, 0.5 - 1.0 / minorExponent));
            outline = new Outline(length, WIDTH, majorExponent);
            stations = Stations.along(line, scaledSpacing(parameters, 1.10, 3.20) + gap, true);
        }

        @Override
        public double extent() {
            return Math.hypot(WIDTH + wire, wire);
        }

        @Override
        public double overhang() {
            return length + wire;
        }

        @Override
        public double sample(double x, double y, double z, double blocks, double stretch) {
            double wire = Math.max(this.wire, MINIMUM_WIRE / blocks);
            double thick = wire * wireReach;
            double clearance = CLEARANCE / blocks;
            double reach = (length + thick + clearance) / stretch;
            double best = Double.MAX_VALUE;
            double gap = clearance;
            int link = 0;
            int last = stations.last(z, reach);
            for (int index = stations.first(z, reach); index <= last; index++) {
                int identity = stations.identity(index);
                // Los pares quedan en el plano de la normal (de pie) y los impares en el de la binormal.
                boolean upright = (identity & 1) == 0;
                double across = Math.abs(upright ? x : y);
                if (across - thick > clearance) {
                    continue;
                }
                double along = (z - stations.center(index)) * stretch;
                double planar = outline.distance(along, upright ? y : x);
                double euclidean = Math.sqrt(planar * planar + across * across);
                gap = Math.min(gap, euclidean - thick);
                double distance = minorExponent == 2.0 ? euclidean : superellipse(planar, across, minorExponent);
                if (distance < best) {
                    best = distance;
                    link = identity;
                }
            }
            return best <= wire ? band(link & 1, 2, best / wire) : outside(gap * blocks);
        }
    }

    /** Contorno superelíptico muestreado en un cuadrante; por simetría basta para medir la distancia a todo él. */
    static final class Outline {
        private static final int SEGMENTS = 32;
        private final double[] px = new double[SEGMENTS + 1];
        private final double[] py = new double[SEGMENTS + 1];

        Outline(double first, double second, double exponent) {
            for (int index = 0; index <= SEGMENTS; index++) {
                double angle = Math.PI * 0.5 * index / SEGMENTS;
                px[index] = first * Math.pow(Math.max(0.0, Math.cos(angle)), 2.0 / exponent);
                py[index] = second * Math.pow(Math.max(0.0, Math.sin(angle)), 2.0 / exponent);
            }
            px[SEGMENTS] = 0.0;
        }

        double distance(double u, double v) {
            double x = Math.abs(u);
            double y = Math.abs(v);
            double best = Double.MAX_VALUE;
            for (int index = 0; index < SEGMENTS; index++) {
                best = Math.min(best, Classic.segmentDistanceSquared(
                        px[index] - x, py[index] - y, 0.0, px[index + 1] - x, py[index + 1] - y, 0.0));
            }
            return Math.sqrt(best);
        }
    }

    /**
     * Oscilar: cinta de un diámetro de ancho que da una vuelta por longitud de onda mientras su centro sube y baja
     * 0,42 radios. La distancia a la cinta se corrige con la pendiente del giro, así la lámina no adelgaza cuando
     * gira deprisa.
     */
    static final class Oscillate extends Classic {
        private static final double LIFT = 0.42;
        private final double sheet;
        private final double frequency;

        Oscillate(ShapeParameters parameters) {
            super(parameters);
            sheet = Math.max(MINIMUM_WIRE, parameters.thickness() - 0.65) / baseRadius;
            frequency = Math.PI * 2.0 / scaledSpacing(parameters, 1.10, 4.50);
        }

        @Override
        public double extent() {
            return LIFT + 1.0 + sheet;
        }

        @Override
        public double sample(double x, double y, double z, double blocks, double stretch) {
            double sheet = Math.max(this.sheet, MINIMUM_WIRE / blocks);
            double phase = z * frequency;
            double sine = Math.sin(phase);
            double cosine = Math.cos(phase);
            // Como en el motor clásico: el ancho gira desde la normal (+y) hacia la binormal (-x).
            double lifted = y - LIFT * sine;
            double width = -x * sine + lifted * cosine;
            double normal = x * cosine + lifted * sine;
            double slope = frequency / stretch * (width - LIFT * sine * cosine);
            double thickness = normal / Math.sqrt(1.0 + slope * slope);
            double edge = Math.max(0.0, Math.abs(width) - 1.0);
            double distance = Math.sqrt(edge * edge + thickness * thickness);
            return distance <= sheet ? 0.999 * Math.min(1.0, Math.abs(width)) : outside((distance - sheet) * blocks);
        }
    }

    /**
     * Red: dos familias de hilos helicoidales a radio 1 que giran en sentidos contrarios a ±45° (una vuelta por
     * perímetro), como la red del motor clásico; el espaciado fija cuántos hilos hay.
     */
    static final class Fishnet extends Classic {
        private final double sector;
        private final double wire;

        Fishnet(ShapeParameters parameters) {
            super(parameters);
            sector = Math.PI * 2.0 / ShapeGenerator.fishnetStrands(parameters);
            wire = wireBlocks(structuralThickness(parameters)) / baseRadius;
        }

        @Override
        public double extent() {
            return 1.0 + wire;
        }

        @Override
        public double sample(double x, double y, double z, double blocks, double stretch) {
            double wire = Math.max(this.wire, MINIMUM_WIRE / blocks);
            double offset = Math.sqrt(x * x + y * y) - 1.0;
            if (Math.abs(offset) > wire) {
                return outside((Math.abs(offset) - wire) * blocks);
            }
            double angle = angle(x, y);
            // Una vuelta por perímetro: el ángulo avanza un radián por cada radio recorrido. Por dentro del
            // cilindro el arco es más corto que el ángulo.
            double slope = 1.0 / stretch;
            double cosine = Math.min(1.0, 1.0 + offset) / Math.sqrt(1.0 + slope * slope);
            double best = Double.MAX_VALUE;
            int family = 0;
            for (int direction = 0; direction < 2; direction++) {
                double sign = direction == 0 ? 1.0 : -1.0;
                // Sobre el cilindro desenrollado cada hilo es una recta: la distancia es el desfase por el coseno.
                double gap = centered(angle - sign * z, sector) * cosine;
                double distance = offset * offset + gap * gap;
                if (distance < best) {
                    best = distance;
                    family = direction;
                }
            }
            best = Math.sqrt(best);
            return best <= wire ? band(family, 2, best / wire) : outside((best - wire) * blocks);
        }
    }

    /**
     * Panal: anillos hexagonales que alternan 30° unidos por puntales diagonales en la mitad de los vértices de
     * cada tramo, como el panal clásico. En lazos cerrados el número de anillos es par.
     */
    static final class Honeycomb extends Classic {
        private static final double SIXTH = Math.PI / 3.0;
        private static final double APOTHEM = Math.cos(Math.PI / 6.0);
        private final double[][] vertexX = new double[2][6];
        private final double[][] vertexY = new double[2][6];
        private final double wire;
        private final Stations stations;

        Honeycomb(ShapeParameters parameters, TubeCenterline line) {
            super(parameters);
            wire = wireBlocks(structuralThickness(parameters)) / baseRadius;
            stations = Stations.along(line, scaledSpacing(parameters, 0.45, 1.50), true);
            for (int parity = 0; parity < 2; parity++) {
                for (int vertex = 0; vertex < 6; vertex++) {
                    double angle = parity * Math.PI / 6.0 + vertex * SIXTH;
                    vertexX[parity][vertex] = -Math.sin(angle);
                    vertexY[parity][vertex] = Math.cos(angle);
                }
            }
        }

        @Override
        public double extent() {
            return 1.0 + wire;
        }

        @Override
        public double overhang() {
            return wire;
        }

        @Override
        public double sample(double x, double y, double z, double blocks, double stretch) {
            double wire = Math.max(this.wire, MINIMUM_WIRE / blocks);
            double radial = Math.sqrt(x * x + y * y);
            // Aristas y puntales quedan entre el apotema del hexágono y el radio 1.
            if (radial > 1.0 + wire) {
                return outside((radial - 1.0 - wire) * blocks);
            }
            if (radial < APOTHEM - wire) {
                return outside((APOTHEM - wire - radial) * blocks);
            }
            double angle = angle(x, y);
            double thick = wire + CLEARANCE / blocks;
            double ringReach = thick / stretch;
            double best = Double.MAX_VALUE;
            int part = 0;
            int last = stations.last(z, stations.pitch() + ringReach);
            for (int index = stations.first(z, ringReach); index <= last; index++) {
                if (!stations.exists(index)) {
                    continue;
                }
                int parity = stations.identity(index) & 1;
                double along = (stations.center(index) - z) * stretch;
                if (Math.abs(along) <= thick) {
                    double ring = hexagonDistance(radial, angle - parity * Math.PI / 6.0);
                    double distance = ring * ring + along * along;
                    if (distance < best) {
                        best = distance;
                        part = 0;
                    }
                }
                if (!stations.exists(index - 1)) {
                    continue;
                }
                double previous = (stations.center(index - 1) - z) * stretch;
                if (previous > thick || along < -thick) {
                    continue;
                }
                int before = 1 - parity;
                for (int vertex = parity; vertex < 6; vertex += 2) {
                    double distance = segmentDistanceSquared(
                            vertexX[before][vertex] - x, vertexY[before][vertex] - y, previous,
                            vertexX[parity][vertex] - x, vertexY[parity][vertex] - y, along);
                    if (distance < best) {
                        best = distance;
                        part = 1;
                    }
                }
            }
            best = Math.sqrt(best);
            return best <= wire ? band(part, 2, best / wire) : outside((best - wire) * blocks);
        }

        /** Distancia en la sección al contorno del hexágono de radio 1 con un vértice en el ángulo 0. */
        private static double hexagonDistance(double radial, double angle) {
            double folded = angle - SIXTH * Math.floor(angle / SIXTH);
            double x = radial * Math.cos(folded);
            double y = radial * Math.sin(folded);
            return Math.sqrt(segmentDistanceSquared(1.0 - x, -y, 0.0, 0.5 - x, APOTHEM - y, 0.0));
        }
    }

    /**
     * Nudos: un trébol (nudo tórico 2:3) por estación, de radio 0,70 ± 0,22 y ±0,34 de fondo, girado 0,47 rad
     * respecto al anterior como el nudo clásico. El hilo se mide contra la curva muestreada, buscando solo cerca
     * de las dos ramas que pasan por el ángulo del punto.
     */
    static final class Knots extends Classic {
        private static final int SEGMENTS = 192;
        private static final double TURN = 0.47;
        private static final double INNER = 0.48;
        private static final double OUTER = 0.92;
        private static final double DEPTH = 0.34;
        private final double[] curveX = new double[SEGMENTS + 1];
        private final double[] curveY = new double[SEGMENTS + 1];
        private final double[] curveZ = new double[SEGMENTS + 1];
        private final double wire;
        private final Stations stations;

        Knots(ShapeParameters parameters, TubeCenterline line) {
            super(parameters);
            wire = wireBlocks(structuralThickness(parameters)) / baseRadius;
            stations = Stations.along(line, scaledSpacing(parameters, 0.95, 2.70), false);
            for (int index = 0; index <= SEGMENTS; index++) {
                double t = Math.PI * 2.0 * index / SEGMENTS;
                double breathing = 0.70 + 0.22 * Math.cos(t * 3.0);
                // Ángulo mayor 2t con el criterio clásico: la posición es (−sen, cos).
                curveX[index] = -Math.sin(t * 2.0) * breathing;
                curveY[index] = Math.cos(t * 2.0) * breathing;
                curveZ[index] = Math.sin(t * 3.0) * DEPTH;
            }
        }

        @Override
        public double extent() {
            return OUTER + wire;
        }

        @Override
        public double overhang() {
            return DEPTH + wire;
        }

        @Override
        public double sample(double x, double y, double z, double blocks, double stretch) {
            double wire = Math.max(this.wire, MINIMUM_WIRE / blocks);
            double radial = Math.sqrt(x * x + y * y);
            if (radial < INNER - wire) {
                return outside((INNER - wire - radial) * blocks);
            }
            if (radial > OUTER + wire) {
                return outside((radial - OUTER - wire) * blocks);
            }
            // A menos de `thick` de la curva, el ángulo del punto no se aparta más de asen(thick / radio interior):
            // basta buscar en esa ventana y la distancia encontrada es exacta hasta la holgura máxima.
            double thick = wire + CLEARANCE / blocks;
            int window = thick >= INNER * 0.9 ? SEGMENTS
                    : (int) Math.ceil(Math.asin(thick / INNER) * 0.5 * SEGMENTS / (Math.PI * 2.0)) + 2;
            double reach = (DEPTH + thick) / stretch;
            double angle = angle(x, y);
            double best = Double.MAX_VALUE;
            int last = stations.last(z, reach);
            for (int index = stations.first(z, reach); index <= last; index++) {
                double along = (z - stations.center(index)) * stretch;
                double turn = stations.identity(index) * TURN;
                double cosine = Math.cos(turn);
                double sine = Math.sin(turn);
                double localX = x * cosine + y * sine;
                double localY = y * cosine - x * sine;
                if (window * 4 >= SEGMENTS) {
                    best = Math.min(best, nearest(localX, localY, along, 0, SEGMENTS / 2));
                    continue;
                }
                // El ángulo mayor de la curva es 2t: el punto queda cerca de t = ángulo/2 o de t = ángulo/2 + π.
                int branch = (int) Math.round((angle - turn) * 0.5 / (Math.PI * 2.0) * SEGMENTS);
                best = Math.min(best, nearest(localX, localY, along, branch, window));
                best = Math.min(best, nearest(localX, localY, along, branch + SEGMENTS / 2, window));
            }
            best = Math.sqrt(best);
            return best <= wire ? 0.999 * best / wire : outside((best - wire) * blocks);
        }

        private double nearest(double x, double y, double z, int center, int window) {
            double best = Double.MAX_VALUE;
            for (int offset = -window; offset < window; offset++) {
                int index = Math.floorMod(center + offset, SEGMENTS);
                best = Math.min(best, segmentDistanceSquared(
                        curveX[index] - x, curveY[index] - y, curveZ[index] - z,
                        curveX[index + 1] - x, curveY[index + 1] - y, curveZ[index + 1] - z));
            }
            return best;
        }
    }

    /** Espiral: un hilo helicoidal a radio 1 que da una vuelta por paso, como el resorte clásico. */
    static final class Coil extends Classic {
        private final double frequency;
        private final double wire;

        Coil(ShapeParameters parameters) {
            super(parameters);
            frequency = Math.PI * 2.0 / scaledSpacing(parameters, 0.90, 3.40);
            int thickness = Math.clamp(structuralThickness(parameters) + parameters.thickness() / 2, 1, 6);
            wire = wireBlocks(thickness) / baseRadius;
        }

        @Override
        public double extent() {
            return 1.0 + wire;
        }

        @Override
        public double sample(double x, double y, double z, double blocks, double stretch) {
            double wire = Math.max(this.wire, MINIMUM_WIRE / blocks);
            double offset = Math.sqrt(x * x + y * y) - 1.0;
            if (Math.abs(offset) > wire) {
                return outside((Math.abs(offset) - wire) * blocks);
            }
            double slope = frequency / stretch;
            // Por dentro del cilindro el arco es más corto que el ángulo.
            double gap = centered(angle(x, y) - frequency * z, Math.PI * 2.0) * Math.min(1.0, 1.0 + offset)
                    / Math.sqrt(1.0 + slope * slope);
            double distance = Math.sqrt(offset * offset + gap * gap);
            return distance <= wire ? 0.999 * distance / wire : outside((distance - wire) * blocks);
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
