package com.komainu.moxia.shape.tube;

import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;

/**
 * Formas 3D del spline de ezEdits ({@code //ezspline 3d …} y {@code //ezspline noise}) en espacio de tubo.
 *
 * <p>Viajan como un {@link PathShape#SWEEP} con la opción {@link #FORM}, así heredan sin más la curva, los marcos,
 * los radios por fotogramas, la torsión, los extremos, la calidad y las losas y escaleras de los tubos. Cada forma es
 * una función implícita propia: devuelve el material (0 a 1) dentro y {@code -1 - holgura} fuera, como las figuras
 * clásicas del motor exacto, cuyas utilidades (estaciones, contornos superelípticos, hilo mínimo) reutiliza.</p>
 *
 * <p>Los parámetros siguen el significado de la wiki pública de ezEdits, en radios de la sección: {@code x} e
 * {@code y} de -1 a 1 y {@code z} avanzando un radio por cada radio de recorrido.</p>
 */
final class EzShapes {
    /** Opción que elige la forma: el ordinal de {@link Form} más uno (0 o ausente: perfil 2D normal). */
    static final String FORM = "ezform";
    /**
     * Medio grosor mínimo, en bloques, de los hilos planos de la red y del panal: con medio bloque en diagonal
     * (√3/2) de margen, el alma de cada hilo siempre cae en un bloque colocado.
     */
    static final double STRIP = 0.9;

    private EzShapes() {
    }

    enum Form {
        BEADS, CUBES, CHAINLINK, FISHNET, HONEYCOMB, OSCILLATE, RINGS, SCALES, NOODLES, NOISE;

        double option() {
            return ordinal() + 1.0;
        }
    }

    static boolean handles(ShapeParameters parameters) {
        return parameters.shape() == PathShape.SWEEP && parameters.options().get(FORM, 0.0) >= 0.5;
    }

    static Form form(ShapeOptions options) {
        int index = (int) Math.round(options.get(FORM, 0.0)) - 1;
        Form[] values = Form.values();
        if (index < 0 || index >= values.length) {
            throw new IllegalArgumentException("Forma de spline desconocida: " + index);
        }
        return values[index];
    }

    static TubeShapes.TubeShape create(TubeSettings settings, ShapeParameters parameters, TubeCenterline line,
                                       long pathSeed) {
        ShapeOptions options = parameters.options();
        return switch (form(options)) {
            // Cuentas: esferas que se tocan, los anillos con grosor 1 y sin extrusión ni hueco.
            case BEADS -> new Rings(parameters, line, 0.0, 1.0, 0.0, 2.0, 2.0);
            case RINGS -> new Rings(parameters, line,
                    Math.clamp(options.get("extrusion", 0.2), -0.9, 10.0),
                    Math.clamp(options.get("thickness", 0.15), 0.02, 1.0),
                    Math.clamp(options.get("gap", 0.0), -0.95, 20.0),
                    Math.clamp(options.get("majorexponent", 2.0), 0.2, 200.0),
                    Math.clamp(options.get("minorexponent", 2.0), 0.2, 200.0));
            case CUBES -> new Cubes(parameters, line);
            case CHAINLINK -> new Chainlink(parameters, line);
            case FISHNET -> new Fishnet(parameters);
            case HONEYCOMB -> new Honeycomb(parameters, line);
            case OSCILLATE -> new Oscillate(options);
            case SCALES -> new Scales(parameters, line);
            case NOODLES -> new Noodles(parameters, line, seed(options, "noodleseed", pathSeed));
            case NOISE -> new NoiseCut(options, settings.seed() != 0L ? settings.seed() : pathSeed);
        };
    }

    private static long seed(ShapeOptions options, String key, long fallback) {
        double value = options.get(key, -1.0);
        return value == -1.0 ? fallback : (long) value;
    }

    /**
     * Anillos: supertoros repetidos. {@code thickness} es el grosor radial del anillo (1 lo cierra en una
     * superesfera), {@code extrusion} lo alarga a lo largo del recorrido, {@code gap} separa un anillo de otro en
     * tamaños de anillo y los exponentes dan el contorno exterior y la sección. Con grosor 1, extrusión y hueco 0 y
     * exponentes 2 son las cuentas de ezEdits: esferas que se tocan.
     */
    static final class Rings extends TubeShapes.Classic {
        private final double width;
        private final double depth;
        private final double majorExponent;
        private final double minorExponent;
        private final double normFactor;
        private final double extent;
        private final TubeShapes.Stations stations;

        Rings(ShapeParameters parameters, TubeCenterline line, double extrusion, double thickness, double gap,
              double majorExponent, double minorExponent) {
            super(parameters);
            width = thickness;
            depth = thickness * (1.0 + extrusion);
            this.majorExponent = majorExponent;
            this.minorExponent = minorExponent;
            normFactor = majorExponent >= 2.0 ? 1.0 : Math.pow(2.0, 1.0 / majorExponent - 0.5);
            extent = Math.max(1.0, Math.pow(2.0, 0.5 - 1.0 / majorExponent));
            stations = TubeShapes.Stations.regular(line, Math.max(0.05, 2.0 * depth * (1.0 + gap)), false);
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
        double sample(double x, double y, double z, double blocks, double stretch) {
            double width = Math.min(1.0, Math.max(this.width, MINIMUM_WIRE / blocks));
            double depth = Math.max(this.depth, MINIMUM_WIRE / (blocks * stretch));
            double radial = (superellipse(x, y, majorExponent) - (1.0 - width)) / width;
            if (Math.abs(radial) > 1.0) {
                return majorExponent >= 1.0
                        ? outside((Math.abs(radial) - 1.0) * width / normFactor * blocks)
                        : -1.0;
            }
            double radialTerm = power(Math.abs(radial), minorExponent);
            double reach = depth + CLEARANCE / (blocks * stretch);
            double best = Double.MAX_VALUE;
            double axialGap = Double.MAX_VALUE;
            int link = 0;
            int last = stations.last(z, reach);
            for (int index = stations.first(z, reach); index <= last; index++) {
                double axial = Math.abs(z - stations.center(index)) / depth;
                if (axial > 1.0) {
                    axialGap = Math.min(axialGap, (axial - 1.0) * depth * stretch);
                    continue;
                }
                axialGap = 0.0;
                double value = radialTerm + power(axial, minorExponent);
                if (value < best) {
                    best = value;
                    link = stations.identity(index);
                }
            }
            if (best <= 1.0) {
                return band(link & 1, 2, Math.pow(best, 1.0 / minorExponent));
            }
            return axialGap == Double.MAX_VALUE ? outside(CLEARANCE) : outside(axialGap * blocks);
        }
    }

    /** Cubos: secciones cuadradas de un diámetro de largo, separadas {@code gap} veces su tamaño. */
    static final class Cubes extends TubeShapes.Classic {
        private final TubeShapes.Stations stations;

        Cubes(ShapeParameters parameters, TubeCenterline line) {
            super(parameters);
            double gap = Math.clamp(options.get("gap", 0.5), -0.9, 20.0);
            stations = TubeShapes.Stations.regular(line, 2.0 * (1.0 + gap), false);
        }

        @Override
        public double extent() {
            return Math.sqrt(2.0);
        }

        @Override
        public double margin() {
            return 0.0;
        }

        @Override
        public double overhang() {
            return 1.0;
        }

        @Override
        double sample(double x, double y, double z, double blocks, double stretch) {
            double box = Math.max(Math.abs(x), Math.abs(y));
            if (box > 1.0) {
                return outside((box - 1.0) * blocks);
            }
            double reach = 1.0 + CLEARANCE / (blocks * stretch);
            double nearest = Double.MAX_VALUE;
            int cube = 0;
            int last = stations.last(z, reach);
            for (int index = stations.first(z, reach); index <= last; index++) {
                double along = Math.abs(z - stations.center(index));
                if (along < nearest) {
                    nearest = along;
                    cube = stations.identity(index);
                }
            }
            if (nearest <= 1.0) {
                return band(cube & 1, 2, Math.max(box, nearest));
            }
            return nearest == Double.MAX_VALUE ? outside(CLEARANCE) : outside((nearest - 1.0) * stretch * blocks);
        }
    }

    /**
     * Cadena: eslabones superelípticos alternados 90° y enganchados. El alambre tiene un cuarto de radio por cada
     * unidad de {@code thickness}, el eslabón llega al borde del tubo y {@code extrusion} lo alarga a lo largo del
     * recorrido. Con hueco 0 cada eslabón pasa justo por el ojo del anterior; {@code gap} los separa en largos de
     * eslabón. {@code place} deja solo los de pie (1), solo los tumbados (2) o los dos (0).
     */
    static final class Chainlink extends TubeShapes.Classic {
        private final double wire;
        private final double halfWidth;
        private final double halfLength;
        private final double minorExponent;
        private final double wireReach;
        private final int place;
        private final TubeShapes.Outline outline;
        private final TubeShapes.Stations stations;

        Chainlink(ShapeParameters parameters, TubeCenterline line) {
            super(parameters);
            double extrusion = Math.clamp(options.get("extrusion", 0.2), -0.8, 5.0);
            double thickness = Math.clamp(options.get("thickness", 1.0), 0.05, 3.6);
            double gap = Math.clamp(options.get("gap", 0.0), -0.9, 10.0);
            double majorExponent = Math.clamp(options.get("majorexponent", 3.0), 0.5, 200.0);
            minorExponent = Math.clamp(options.get("minorexponent", 3.0), 0.5, 200.0);
            place = (int) Math.round(Math.clamp(options.get("place", 0.0), 0.0, 2.0));
            wire = Math.min(0.9, 0.25 * thickness);
            halfWidth = Math.max(0.05, 1.0 - wire);
            halfLength = Math.max(0.05, halfWidth * (1.0 + extrusion));
            wireReach = Math.max(1.0, Math.pow(2.0, 0.5 - 1.0 / minorExponent));
            outline = new TubeShapes.Outline(halfLength, halfWidth, majorExponent);
            // Sin hueco el siguiente eslabón empieza donde acaba el ojo del anterior.
            double pitch = 2.0 * Math.max(0.05, halfLength - wire) + gap * 2.0 * halfLength;
            stations = TubeShapes.Stations.regular(line, Math.max(0.1, pitch), true);
        }

        @Override
        public double extent() {
            return Math.hypot(halfWidth + wire * wireReach, wire * wireReach);
        }

        @Override
        public double overhang() {
            return halfLength + wire;
        }

        @Override
        double sample(double x, double y, double z, double blocks, double stretch) {
            double wire = Math.max(this.wire, MINIMUM_WIRE / blocks);
            double thick = wire * wireReach;
            double clearance = CLEARANCE / blocks;
            double reach = (halfLength + thick + clearance) / stretch;
            double best = Double.MAX_VALUE;
            double gap = clearance;
            int link = 0;
            int last = stations.last(z, reach);
            for (int index = stations.first(z, reach); index <= last; index++) {
                int identity = stations.identity(index);
                boolean upright = (identity & 1) == 0;
                if (place == 1 && !upright || place == 2 && upright) {
                    continue;
                }
                double across = Math.abs(upright ? x : y);
                if (across - thick > clearance) {
                    continue;
                }
                double along = (z - stations.center(index)) * stretch;
                double planar = outline.distance(along, upright ? y : x);
                double euclidean = Math.hypot(planar, across);
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

    /**
     * Red: dos familias de hilos a ±45° sobre la piel del tubo, separados {@code spacing} radios (ajustado para que
     * den la vuelta entera), de {@code width} radios de ancho y que se hunden {@code depth} radios hacia el eje.
     */
    static final class Fishnet extends TubeShapes.Classic {
        private final double period;
        private final double width;
        private final double depth;

        Fishnet(ShapeParameters parameters) {
            super(parameters);
            double spacing = Math.clamp(options.get("spacing", 1.0), 0.1, Math.PI * 2.0);
            period = Math.PI * 2.0 / Math.max(1, (int) Math.round(Math.PI * 2.0 / spacing));
            width = Math.clamp(options.get("width", 0.2), 0.01, 2.0);
            depth = Math.clamp(options.get("depth", 0.2), 0.01, 1.0);
        }

        @Override
        public double extent() {
            return 1.0;
        }

        @Override
        public double margin() {
            return 0.0;
        }

        @Override
        double sample(double x, double y, double z, double blocks, double stretch) {
            double depth = Math.min(1.0, Math.max(this.depth, 2.0 * STRIP / blocks));
            double half = Math.max(width * 0.5, STRIP / blocks);
            double radial = Math.sqrt(x * x + y * y);
            double inner = 1.0 - depth;
            if (radial > 1.0 || radial < inner) {
                return outside((radial > 1.0 ? radial - 1.0 : inner - radial) * blocks);
            }
            double angle = angle(x, y);
            // En la piel desenrollada a radio r (arco r·ángulo, avance z) cada hilo es la recta arco = r·z + c:
            // la distancia a ella es r·|desfase| / √(1 + r²), a 45° sobre la piel exterior.
            double factor = radial / Math.sqrt(1.0 + radial * radial);
            double best = Double.MAX_VALUE;
            int family = 0;
            for (int direction = 0; direction < 2; direction++) {
                double offset = direction == 0 ? angle - z : angle + z;
                double distance = Math.abs(centered(offset, period)) * factor;
                if (distance < best) {
                    best = distance;
                    family = direction;
                }
            }
            if (best > half) {
                return outside((best - half) * blocks);
            }
            return band(family, 2, (radial - inner) / depth);
        }
    }

    /**
     * Panal: rejilla de hexágonos de {@code cellsize} radios (de centro a vértice) sobre la piel del tubo, con
     * hilos de {@code wirewidth} radios que se hunden {@code depth} hacia el eje. Las columnas se ajustan para dar
     * la vuelta entera y, en lazos cerrados, las filas para empalmar sin costura.
     */
    static final class Honeycomb extends TubeShapes.Classic {
        private static final double ROOT_THREE = Math.sqrt(3.0);
        private final double column;
        private final double row;
        private final double apothem;
        private final double wireWidth;
        private final double depth;

        Honeycomb(ShapeParameters parameters, TubeCenterline line) {
            super(parameters);
            double cell = Math.clamp(options.get("cellsize", 0.25), 0.03, 3.0);
            int columns = Math.max(3, (int) Math.round(Math.PI * 2.0 / (ROOT_THREE * cell)));
            column = Math.PI * 2.0 / columns;
            double size = column / ROOT_THREE;
            double rowPitch = size * 1.5;
            if (line.closed) {
                double total = line.longitudinal[line.count - 1];
                int rows = Math.max(2, 2 * (int) Math.round(total / (rowPitch * 2.0)));
                rowPitch = total / rows;
            }
            row = rowPitch;
            apothem = column * 0.5;
            wireWidth = Math.clamp(options.get("wirewidth", 0.06), 0.005, 1.0);
            depth = Math.clamp(options.get("depth", 0.2), 0.01, 1.0);
        }

        @Override
        public double extent() {
            return 1.0;
        }

        @Override
        public double margin() {
            return 0.0;
        }

        @Override
        double sample(double x, double y, double z, double blocks, double stretch) {
            double depth = Math.min(1.0, Math.max(this.depth, 2.0 * STRIP / blocks));
            double half = Math.max(wireWidth * 0.5, STRIP / blocks);
            double radial = Math.sqrt(x * x + y * y);
            double inner = 1.0 - depth;
            if (radial > 1.0 || radial < inner) {
                return outside((radial > 1.0 ? radial - 1.0 : inner - radial) * blocks);
            }
            double angle = angle(x, y);
            // Hexágonos con un vértice hacia el recorrido: la celda de un punto es la del centro más cercano entre
            // las filas vecinas, y el hilo es la franja junto a su borde.
            double best = Double.MAX_VALUE;
            double bestA = 0.0;
            double bestZ = 0.0;
            int nearestRow = (int) Math.round(z / row);
            for (int candidate = nearestRow - 1; candidate <= nearestRow + 1; candidate++) {
                double offset = (candidate & 1) == 0 ? 0.0 : column * 0.5;
                double alongCell = z - candidate * row;
                double acrossCell = centered(angle - offset, column);
                double distance = acrossCell * acrossCell + alongCell * alongCell;
                if (distance < best) {
                    best = distance;
                    bestA = acrossCell;
                    bestZ = alongCell;
                }
            }
            double across = Math.abs(bestA);
            double along = Math.abs(bestZ);
            double hexagon = Math.max(across, across * 0.5 + along * ROOT_THREE * 0.5);
            // La rejilla se mide en (ángulo, avance): a radio r el arco encoge, así que el hilo mínimo se abre en
            // ángulo para seguir midiendo lo mismo en bloques.
            double edge = (apothem - hexagon) * Math.min(1.0, radial);
            if (edge > half) {
                return outside((edge - half) * blocks);
            }
            return 0.999 * Math.clamp((radial - inner) / depth, 0.0, 1.0);
        }
    }

    /** Ondas: el radio baja {@code depth} radios cada {@code interval} radios de recorrido, como surcos suaves. */
    static final class Oscillate implements TubeShapes.TubeShape {
        private final double depth;
        private final double frequency;

        Oscillate(ShapeOptions options) {
            depth = Math.clamp(options.get("depth", 0.2), 0.0, 0.95);
            frequency = Math.PI * 2.0 / Math.clamp(options.get("interval", 0.5), 0.05, 50.0);
        }

        @Override
        public double extent() {
            return 1.0;
        }

        @Override
        public double sample(double x, double y, double z) {
            double reach = 1.0 - depth * 0.5 * (1.0 + Math.cos(z * frequency));
            double radial = Math.sqrt(x * x + y * y);
            return radial <= reach ? 0.999 * radial / reach : -1.0;
        }
    }

    /**
     * Escamas: placas en filas a lo largo del recorrido, {@code columns} por vuelta, con las filas impares
     * desplazadas media columna. Cada placa tiene un contorno superelíptico ({@code majorexponent}), se inclina
     * {@code tileangle} grados levantando el borde de detrás y cae hacia su contorno con la forma de
     * {@code minorexponent}. {@code horizontaloffset} y {@code verticaloffset} las separan de lado y a lo largo, y
     * {@code depthmultiplier} hunde (más de 1) o rellena (menos de 1) las juntas entre placas.
     */
    static final class Scales extends TubeShapes.Classic {
        private final double column;
        private final double halfWidth;
        private final double halfLength;
        private final double row;
        private final double majorExponent;
        private final double minorExponent;
        private final double slope;
        private final double base;
        private final double lift;
        private final double core;
        private final int columnReach;
        private final int rowReach;

        Scales(ShapeParameters parameters, TubeCenterline line) {
            super(parameters);
            int columns = (int) Math.round(Math.clamp(options.get("columns", 8.0), 2.0, 64.0));
            double horizontal = Math.clamp(options.get("horizontaloffset", 1.05), 0.2, 5.0);
            double vertical = Math.clamp(options.get("verticaloffset", 1.2), 0.1, 5.0);
            majorExponent = Math.clamp(options.get("majorexponent", 1.4), 0.3, 200.0);
            minorExponent = Math.clamp(options.get("minorexponent", 14.0), 0.3, 200.0);
            double angle = Math.clamp(options.get("tileangle", 14.0), 0.0, 80.0);
            double multiplier = Math.clamp(options.get("depthmultiplier", 1.0), 0.0, 3.0);
            column = Math.PI * 2.0 / columns;
            halfWidth = column * 0.5 / horizontal;
            halfLength = halfWidth;
            double rowPitch = vertical * halfLength;
            if (line.closed) {
                double total = line.longitudinal[line.count - 1];
                int rows = Math.max(2, 2 * (int) Math.round(total / (rowPitch * 2.0)));
                rowPitch = total / rows;
            }
            row = rowPitch;
            slope = Math.tan(Math.toRadians(angle));
            lift = halfLength * 0.25;
            double height = Math.min(0.6, lift + 2.0 * halfLength * slope);
            base = 1.0 - height;
            core = 1.0 - height * multiplier;
            columnReach = (int) Math.ceil(halfWidth / column);
            rowReach = (int) Math.ceil(halfLength / row);
        }

        @Override
        public double extent() {
            return 1.0;
        }

        @Override
        public double margin() {
            return 0.0;
        }

        @Override
        double sample(double x, double y, double z, double blocks, double stretch) {
            double radial = Math.sqrt(x * x + y * y);
            if (radial > 1.0) {
                return outside((radial - 1.0) * blocks);
            }
            double value = 0.999 * Math.clamp((radial - core) / Math.max(1.0E-6, 1.0 - core), 0.0, 1.0);
            if (radial <= core) {
                return value;
            }
            double angle = angle(x, y);
            double lift = Math.max(this.lift, 1.0 / blocks);
            int nearestRow = (int) Math.round(z / row);
            for (int candidate = nearestRow - rowReach - 1; candidate <= nearestRow + rowReach + 1; candidate++) {
                double v = (z - candidate * row) / halfLength;
                if (Math.abs(v) > 1.0) {
                    continue;
                }
                double offset = (candidate & 1) == 0 ? 0.0 : column * 0.5;
                int nearestColumn = (int) Math.round((angle - offset) / column);
                for (int step = -columnReach; step <= columnReach; step++) {
                    double u = centered(angle - offset - (nearestColumn + step) * column, Math.PI * 2.0) / halfWidth;
                    double footprint = power(Math.abs(u), majorExponent) + power(Math.abs(v), majorExponent);
                    if (footprint > 1.0) {
                        continue;
                    }
                    // La placa sube hacia su borde trasero y cae hacia el contorno con el exponente de la sección.
                    double rise = lift + (1.0 + v) * halfLength * slope;
                    double edge = Math.pow(Math.max(0.0, 1.0 - footprint), 1.0 / minorExponent);
                    if (radial <= base + rise * edge) {
                        return value;
                    }
                }
            }
            return outside(0.0);
        }
    }

    /**
     * Fideos: {@code amount} tubos finos que se enredan sin cortarse. Sus ejes son las trayectorias de un flujo
     * plano sin divergencia (el rotacional de un ruido de Perlin) que avanza con el recorrido: dos trayectorias de
     * un mismo flujo nunca se cruzan, y una relajación por pasos mantiene además la distancia de choque entre ellos.
     * {@code density} fija qué parte de la sección llenan, {@code tangle} cuánto se mueven, {@code frequency} lo
     * menudo de sus curvas y {@code width} el grosor con el que se colocan respecto al de choque.
     */
    static final class Noodles extends TubeShapes.Classic {
        private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
        private final int amount;
        private final double collision;
        private final double rendered;
        private final double boundary;
        private final double step;
        private final int steps;
        private final double[][] centerX;
        private final double[][] centerY;

        Noodles(ShapeParameters parameters, TubeCenterline line, long seed) {
            super(parameters);
            amount = (int) Math.round(Math.clamp(options.get("amount", 12.0), 1.0, 64.0));
            double density = Math.clamp(options.get("density", 0.7), 0.05, 1.0);
            double frequency = Math.clamp(options.get("frequency", 0.5), 0.01, 10.0);
            double tangle = Math.clamp(options.get("tangle", 3.0), 0.0, 20.0);
            double width = Math.clamp(options.get("width", 0.8), 0.05, 3.0);
            double[] startX = new double[amount];
            double[] startY = new double[amount];
            double closest = Double.MAX_VALUE;
            for (int index = 0; index < amount; index++) {
                double radius = Math.sqrt((index + 0.5) / amount);
                startX[index] = radius * Math.cos(index * GOLDEN_ANGLE);
                startY[index] = radius * Math.sin(index * GOLDEN_ANGLE);
                for (int other = 0; other < index; other++) {
                    closest = Math.min(closest, Math.hypot(startX[index] - startX[other], startY[index] - startY[other]));
                }
            }
            // Radio con el que caben todos dentro del círculo (espiral de girasol encogida a 1 - radio).
            double fit = amount == 1 ? 1.0 : closest / (2.0 + closest);
            collision = fit * Math.sqrt(density);
            rendered = collision * width;
            boundary = Math.max(0.0, 1.0 - collision);
            double total = line.longitudinal[line.count - 1];
            step = Math.max(0.04, total / 20_000.0);
            steps = (int) Math.ceil(total / step) + 2;
            centerX = new double[amount][steps];
            centerY = new double[amount][steps];
            trace(startX, startY, frequency, tangle * 0.25, new TubeShapes.Noise(seed));
        }

        private void trace(double[] startX, double[] startY, double frequency, double speed, TubeShapes.Noise noise) {
            double[] x = new double[amount];
            double[] y = new double[amount];
            for (int index = 0; index < amount; index++) {
                x[index] = startX[index] * boundary;
                y[index] = startY[index] * boundary;
            }
            double[] velocity = new double[2];
            for (int stepIndex = 0; stepIndex < steps; stepIndex++) {
                if ((stepIndex & 255) == 0 && Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException();
                }
                for (int index = 0; index < amount; index++) {
                    centerX[index][stepIndex] = x[index];
                    centerY[index][stepIndex] = y[index];
                }
                double z = stepIndex * step;
                for (int index = 0; index < amount; index++) {
                    // Punto medio (RK2) sobre el flujo.
                    flow(noise, x[index], y[index], z, frequency, speed, velocity);
                    double middleX = x[index] + velocity[0] * step * 0.5;
                    double middleY = y[index] + velocity[1] * step * 0.5;
                    flow(noise, middleX, middleY, z + step * 0.5, frequency, speed, velocity);
                    x[index] += velocity[0] * step;
                    y[index] += velocity[1] * step;
                }
                relax(x, y);
            }
        }

        /** Rotacional de un potencial que se anula en el borde: el flujo nunca sale del círculo permitido. */
        private void flow(TubeShapes.Noise noise, double x, double y, double z, double frequency, double speed,
                          double[] velocity) {
            double h = 1.0E-3;
            double up = potential(noise, x, y + h, z, frequency);
            double down = potential(noise, x, y - h, z, frequency);
            double right = potential(noise, x + h, y, z, frequency);
            double left = potential(noise, x - h, y, z, frequency);
            velocity[0] = (up - down) / (2.0 * h) * speed;
            velocity[1] = -(right - left) / (2.0 * h) * speed;
        }

        private double potential(TubeShapes.Noise noise, double x, double y, double z, double frequency) {
            if (boundary <= 0.0) {
                return 0.0;
            }
            double falloff = 1.0 - (x * x + y * y) / (boundary * boundary);
            if (falloff <= 0.0) {
                return 0.0;
            }
            return noise.perlin(x * frequency * 2.0, y * frequency * 2.0, z * frequency) * falloff * falloff;
        }

        /** Separa los pares que se acercan más que la distancia de choque y devuelve al círculo a los que salen. */
        private void relax(double[] x, double[] y) {
            double minimum = collision * 2.0;
            for (int pass = 0; pass < 2; pass++) {
                for (int index = 0; index < amount; index++) {
                    for (int other = index + 1; other < amount; other++) {
                        double dx = x[other] - x[index];
                        double dy = y[other] - y[index];
                        double distance = Math.hypot(dx, dy);
                        if (distance >= minimum) {
                            continue;
                        }
                        if (distance < 1.0E-9) {
                            dx = 1.0E-6 * (other - index);
                            dy = 0.0;
                            distance = Math.abs(dx);
                        }
                        double push = (minimum - distance) * 0.5 / distance;
                        x[index] -= dx * push;
                        y[index] -= dy * push;
                        x[other] += dx * push;
                        y[other] += dy * push;
                    }
                }
                for (int index = 0; index < amount; index++) {
                    double radial = Math.hypot(x[index], y[index]);
                    if (radial > boundary && radial > 0.0) {
                        x[index] *= boundary / radial;
                        y[index] *= boundary / radial;
                    }
                }
            }
        }

        int amount() {
            return amount;
        }

        /** Centro del fideo {@code strand} en la sección a la altura {@code z} del recorrido. */
        double[] center(int strand, double z) {
            double position = Math.clamp(z / step, 0.0, steps - 1.000001);
            int index = (int) position;
            double fraction = position - index;
            double[] px = centerX[strand];
            double[] py = centerY[strand];
            return new double[]{px[index] + (px[index + 1] - px[index]) * fraction,
                    py[index] + (py[index + 1] - py[index]) * fraction};
        }

        double radius(double blocks) {
            return Math.max(rendered, MINIMUM_WIRE / blocks);
        }

        double collision() {
            return collision;
        }

        @Override
        public double extent() {
            return Math.max(1.0, boundary + rendered);
        }

        @Override
        double sample(double x, double y, double z, double blocks, double stretch) {
            double radius = Math.max(rendered, MINIMUM_WIRE / blocks);
            double radial = Math.sqrt(x * x + y * y);
            if (radial > boundary + radius) {
                return outside((radial - boundary - radius) * blocks);
            }
            double position = Math.clamp(z / step, 0.0, steps - 1.000001);
            int index = (int) position;
            double fraction = position - index;
            double best = Double.MAX_VALUE;
            int noodle = 0;
            for (int strand = 0; strand < amount; strand++) {
                double[] px = centerX[strand];
                double[] py = centerY[strand];
                double cx = px[index] + (px[index + 1] - px[index]) * fraction;
                double cy = py[index] + (py[index + 1] - py[index]) * fraction;
                double dx = x - cx;
                double dy = y - cy;
                double planar = dx * dx + dy * dy;
                if (planar > (radius + CLEARANCE / blocks) * (radius + CLEARANCE / blocks) * 4.0) {
                    best = Math.min(best, planar);
                    continue;
                }
                // El eje va inclinado: la distancia perpendicular es la plana por el coseno de la pendiente.
                double slopeX = (px[index + 1] - px[index]) / (step * stretch);
                double slopeY = (py[index + 1] - py[index]) / (step * stretch);
                double distance = planar / (1.0 + slopeX * slopeX + slopeY * slopeY);
                if (distance < best) {
                    best = distance;
                    noodle = strand;
                }
            }
            double distance = Math.sqrt(best);
            return distance <= radius
                    ? (noodle + 0.999 * distance / radius) / amount
                    : outside((distance - radius) * blocks);
        }
    }

    /**
     * Spline de ruido: un cilindro cuya corteza muerde un ruido de la notación de ezEdits evaluado en espacio de
     * tubo, hasta {@code noisedepth} radios hacia dentro. El bloque se queda donde el ruido supera la recta que va de
     * 0 en la profundidad pedida a 1 en la superficie, y el material va de esa profundidad (0) a la superficie (1).
     * Con una expresión de corte ({@code -i}) decide ella, con {@code n} el ruido y {@code d} la profundidad.
     *
     * <p>El ruido y la expresión viajan en el texto de fórmula de las opciones, separados por un salto de línea.</p>
     */
    static final class NoiseCut implements TubeShapes.TubeShape {
        private final NoiseSpec.NoiseField field;
        private final double depth;
        private final TubeFormula.Runner cut;
        private final double extent;

        NoiseCut(ShapeOptions options, long seed) {
            String[] parts = options.formula().split("\n", 2);
            field = NoiseSpec.parse(parts[0].isBlank() ? EzSpline.DEFAULT_NOISE : parts[0]).build(seed);
            depth = Math.clamp(options.get("noisedepth", 0.7), 0.01, 8.0);
            if (parts.length > 1 && !parts[1].isBlank()) {
                TubeFormula formula = TubeFormula.compileNoiseCut(parts[1]);
                cut = formula.runner(seed, 0);
                extent = formula.radialBound();
            } else {
                cut = null;
                extent = 1.0;
            }
        }

        static String pack(String noise, String cut) {
            return cut == null || cut.isBlank() ? noise : noise + "\n" + cut.strip();
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
            if (cut != null) {
                if (Math.abs(x) > 1.0 || Math.abs(y) > 1.0) {
                    return -1.0;
                }
                double value = cut.evaluate(x, y, z, progress, field.sample(x, y, z), depth);
                return value > 0.0 ? Math.min(value, 1.0) : -1.0;
            }
            double radial = Math.sqrt(x * x + y * y);
            if (radial > 1.0) {
                return -1.0;
            }
            // Recta de corte: 0 a `depth` radios de la superficie y 1 en ella.
            double reach = 1.0 + (radial - 1.0) / depth;
            if (!(field.sample(x, y, z) > reach)) {
                return -1.0;
            }
            return Math.clamp(1.0 + (radial - 1.0) / Math.min(depth, 1.0), 0.001, 0.999);
        }
    }
}
