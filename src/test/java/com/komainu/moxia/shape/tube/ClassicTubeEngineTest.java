package com.komainu.moxia.shape.tube;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeExpression;
import com.komainu.moxia.shape.ShapeGenerator;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Motor exacto de las figuras clásicas: mismas proporciones, sin huecos y con las opciones del tubo. */
class ClassicTubeEngineTest {
    private static final int LIMIT = 1_500_000;
    private static final List<PathShape> EXACT_SHAPES = List.of(PathShape.CHAINLINK, PathShape.RINGS,
            PathShape.BEADS, PathShape.OSCILLATE, PathShape.FISHNET, PathShape.HONEYCOMB, PathShape.KNOTS,
            PathShape.COIL);

    @Test
    void defaultEngineKeepsTheClassicGeometry() {
        SampledPath path = oblique();
        for (PathShape shape : EXACT_SHAPES) {
            ShapeParameters defaults = shape.defaults();
            assertTrue(TubeFigures.hasExactEngine(shape), shape.id());
            assertFalse(TubeFigures.handles(defaults), shape.id() + " no debe cambiar de motor por defecto");
            Set<BlockVector3> classic = ShapeGenerator.generate(path, defaults);
            Set<BlockVector3> explicit = ShapeGenerator.generate(path, with(defaults, Map.of("engine", 0.0)));
            assertEquals(classic, explicit, shape.id() + ": engine:0 es el motor clásico de siempre");
            assertNotEquals(classic, ShapeGenerator.generate(path, exact(defaults, Map.of())),
                    shape.id() + ": engine:1 debe usar el motor exacto");
        }
        assertFalse(TubeFigures.hasExactEngine(PathShape.SCALES));
        assertFalse(TubeFigures.handles(with(PathShape.SCALES.defaults(), Map.of("engine", 1.0))),
                "las figuras sin versión exacta ignoran la opción");
    }

    @Test
    void engineAcceptsSymbolicNames() {
        assertEquals(1.0, ShapeExpression.parse("chainlink(engine:exact)").options().get("engine", -1.0));
        assertEquals(1.0, ShapeExpression.parse("coil(motor:exacto)").options().get("engine", -1.0));
        assertEquals(0.0, ShapeExpression.parse("rings(engine:classic)").options().get("engine", -1.0));
        assertEquals(PathShape.FISHNET, ShapeExpression.parse("net(engine:exact)").shape());
        assertThrows(IllegalArgumentException.class, () -> ShapeExpression.parse("beads(engine:magic)"));
    }

    @Test
    void wireCoresHaveNoGapsOnAnyPath() {
        List<SampledPath> paths = List.of(straight(70), oblique(), curved());
        for (PathShape shape : EXACT_SHAPES) {
            if (shape == PathShape.BEADS || shape == PathShape.OSCILLATE) {
                continue;
            }
            for (SampledPath path : paths) {
                for (double quality : new double[]{0.0, 1.0}) {
                    ShapeParameters parameters = exact(shape.defaults(), Map.of("quality", quality));
                    Set<BlockVector3> blocks = TubeFigures.generate(path, parameters, LIMIT);
                    List<BlockVector3> missing = missingCore(path, parameters, blocks);
                    assertTrue(missing.isEmpty(), shape.id() + " (calidad " + quality + ") deja huecos en el alma "
                            + "del hilo, por ejemplo en " + missing.subList(0, Math.min(5, missing.size())));
                }
            }
        }
    }

    @Test
    void oscillatingRibbonCannotBeCrossed() {
        for (SampledPath path : List.of(straight(70), oblique(), curved())) {
            for (double quality : new double[]{0.0, 1.0}) {
                ShapeParameters parameters = exact(PathShape.OSCILLATE.defaults().withRadius(6),
                        Map.of("quality", quality));
                Set<BlockVector3> blocks = TubeFigures.generate(path, parameters, LIMIT);
                TubeCenterline line = TubeCenterline.create(path, TubeSettings.from(parameters), TubeCurve.NONE);
                double frequency = Math.PI * 2.0 / (1.10 + 3.40 * (parameters.spacing() - 2.0) / 6.0);
                double total = line.longitudinal[line.count - 1];
                int checked = 0;
                for (double z = 0.8; z < total - 0.8; z += 0.05) {
                    for (double width = -0.2; width <= 0.21; width += 0.2) {
                        double[] point = ribbon(line, frequency, width, z);
                        double[] along = ribbon(line, frequency, width, z + 0.01);
                        double[] across = ribbon(line, frequency, width + 0.01, z);
                        double[] normal = normalize(cross(subtract(across, point), subtract(along, point)));
                        BlockVector3 front = block(point, normal, 1.6);
                        BlockVector3 back = block(point, normal, -1.6);
                        if (blocks.contains(front) || blocks.contains(back)) {
                            continue;
                        }
                        checked++;
                        assertFalse(connected(blocks, front, back, block(point, normal, 0.0), 2),
                                "la cinta tiene un agujero cerca de " + block(point, normal, 0.0)
                                        + " (calidad " + quality + ")");
                    }
                }
                assertTrue(checked > 200, "pocas comprobaciones: " + checked);
            }
        }
    }

    @Test
    void thickWiresAndScaledFiguresStayWatertight() {
        SampledPath path = curved();
        for (PathShape shape : List.of(PathShape.COIL, PathShape.CHAINLINK, PathShape.HONEYCOMB)) {
            ShapeParameters thick = exact(shape.defaults().withRadius(14).withThickness(3), Map.of());
            assertTrue(missingCore(path, thick, TubeFigures.generate(path, thick, LIMIT)).isEmpty(),
                    shape.id() + " grueso");
            ShapeParameters keyed = exact(shape.defaults().withRadius(12),
                    Map.of("k0", 30.0, "k1", 100.0, "k2", 45.0));
            assertTrue(missingCore(path, keyed, TubeFigures.generate(path, keyed, LIMIT)).isEmpty(),
                    shape.id() + " con radios por fotogramas");
        }
    }

    @Test
    void continuousFiguresStayInOnePiece() {
        for (SampledPath path : List.of(straight(70), oblique(), curved())) {
            for (PathShape shape : List.of(PathShape.COIL, PathShape.FISHNET, PathShape.OSCILLATE,
                    PathShape.HONEYCOMB)) {
                Set<BlockVector3> blocks = TubeFigures.generate(path, exact(shape.defaults(), Map.of()), LIMIT);
                int largest = largestComponent(blocks);
                assertTrue(largest >= blocks.size() * 0.995, shape.id() + ": la pieza principal tiene " + largest
                        + " de " + blocks.size() + " bloques");
            }
        }
    }

    @Test
    void beadsAreClosedShellsWithTheClassicSpacing() {
        SampledPath path = straight(60);
        ShapeParameters parameters = exact(PathShape.BEADS.defaults().withRadius(5), Map.of());
        Set<BlockVector3> blocks = TubeFigures.generate(path, parameters, LIMIT);
        // Mismo paso que el clásico: radio · (1,55 + 3,05 · progreso del espaciado).
        double pitch = 5 * (1.55 + 3.05 * (parameters.spacing() - 2) / 6.0);
        for (double center = 0.0; center <= 60.0; center += pitch) {
            BlockVector3 inside = BlockVector3.at((int) Math.floor(center + 0.5), 0, 0);
            assertFalse(blocks.contains(inside), "las cuentas son huecas como en el motor clásico");
            assertTrue(isEnclosed(blocks, inside, 8), "la cáscara de la cuenta en x=" + center + " tiene un hueco");
        }
        assertTrue(blocks.stream().anyMatch(block -> block.x() <= -4),
                "la primera cuenta sale entera aunque el extremo sea plano");
        Set<BlockVector3> solid = TubeFigures.generate(path, exact(parameters, Map.of("hollow", 0.0)), LIMIT);
        assertTrue(solid.contains(BlockVector3.at(0, 0, 0)) && solid.containsAll(blocks),
                "hollow:false rellena las cuentas");
    }

    @Test
    void exactEngineIsDeterministic() {
        SampledPath path = curved();
        for (PathShape shape : EXACT_SHAPES) {
            ShapeParameters parameters = exact(shape.defaults(), Map.of());
            Set<BlockVector3> first = ShapeGenerator.generate(path, parameters);
            Set<BlockVector3> second = ShapeGenerator.generate(path, parameters);
            assertFalse(first.isEmpty(), shape.id());
            assertEquals(first, second, shape.id() + " debe ser estable entre holograma y Build");
        }
    }

    @Test
    void exactVolumeStaysCloseToTheClassicFigure() {
        SampledPath path = straight(80);
        for (PathShape shape : EXACT_SHAPES) {
            for (int radius : new int[]{shape.defaults().radius(), 12}) {
                ShapeParameters classic = shape.defaults().withRadius(radius);
                int classicSize = ShapeGenerator.generate(path, classic).size();
                int exactSize = ShapeGenerator.generate(path, exact(classic, Map.of())).size();
                double ratio = exactSize / (double) classicSize;
                assertTrue(ratio > 0.4 && ratio < 2.5, shape.id() + " r=" + radius + ": " + exactSize
                        + " bloques frente a " + classicSize + " del clásico");
            }
        }
    }

    @Test
    void exactFiguresInheritTubeOptions() {
        SampledPath path = straight(60);
        ShapeParameters coil = exact(PathShape.COIL.defaults().withRadius(8), Map.of());
        Set<BlockVector3> plain = TubeFigures.generate(path, coil, LIMIT);
        Set<BlockVector3> keyed = TubeFigures.generate(path, exact(coil, Map.of("k0", 50.0, "k1", 150.0)), LIMIT);
        assertTrue(reach(keyed, 2) < reach(plain, 2) && reach(keyed, 58) > reach(plain, 58),
                "los radios por fotogramas estrechan el inicio y ensanchan el final");
        Set<BlockVector3> round = TubeFigures.generate(path,
                exact(coil, Map.of("end", (double) TubeSettings.EndMode.ROUND.ordinal())), LIMIT);
        assertTrue(round.stream().anyMatch(block -> block.x() < -2), "el extremo en cúpula asoma más allá del inicio");
        assertTrue(plain.stream().noneMatch(block -> block.x() < -1), "el extremo plano corta la espiral");
        Set<BlockVector3> twisted = TubeFigures.generate(path, exact(coil, Map.of("twist", 90.0)), LIMIT);
        assertNotEquals(plain, twisted, "la torsión del tubo también gira la figura clásica");
        TubeCurve shrink = new TubeCurve(1.0, 0.5, 1.0, 1.0, 0.0, value -> value);
        Set<BlockVector3> curved = TubeFigures.generate(path, coil, shrink, LIMIT);
        assertTrue(reach(curved, 56) < reach(plain, 56) - 2, "la curva de modificadores encoge la espiral");
    }

    @Test
    void exactFiguresAcceptSlabsAndStairs() {
        SampledPath path = oblique();
        for (PathShape shape : List.of(PathShape.COIL, PathShape.RINGS, PathShape.BEADS)) {
            ShapeParameters parameters = exact(shape.defaults().withRadius(9), Map.of(
                    "smooth", (double) com.komainu.moxia.shape.smooth.SmoothMode.STAIRS.ordinal(),
                    "coverage", 0.6));
            assertTrue(TubeFigures.usesPieces(parameters), shape.id());
            assertFalse(TubeFigures.usesPieces(with(shape.defaults(), Map.of("smooth", 2.0))),
                    "el motor clásico no usa piezas");
            Set<BlockVector3> original = TubeFigures.generate(path, parameters, LIMIT);
            TubeFigures.Smoothed first = TubeFigures.smooth(path, parameters, TubeCurve.NONE, original, LIMIT);
            TubeFigures.Smoothed second = TubeFigures.smooth(path, parameters, TubeCurve.NONE, original, LIMIT);
            assertFalse(first.pieces().isEmpty(), shape.id() + " debería llevar piezas en la superficie");
            assertTrue(first.blocks().containsAll(original), "las piezas sustituyen o añaden, nunca quitan");
            assertEquals(first.blocks(), second.blocks());
            assertEquals(first.pieces().size(), second.pieces().size());
        }
    }

    @Test
    void closedLoopsFitAWholeNumberOfMotifs() {
        List<BlockVector3> points = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            double angle = Math.PI * 2.0 * index / 10.0;
            points.add(BlockVector3.at(Math.round(Math.cos(angle) * 40), 0, Math.round(Math.sin(angle) * 40)));
        }
        SampledPath loop = SampledPath.createSmooth(points, true);
        ShapeParameters chain = exact(PathShape.CHAINLINK.defaults(), Map.of());
        TubeCenterline line = TubeCenterline.create(loop, TubeSettings.from(chain), TubeCurve.NONE);
        TubeShapes.Stations stations = TubeShapes.Stations.along(line, 2.15, true);
        double total = line.longitudinal[line.count - 1];
        int links = (int) Math.round(total / stations.pitch());
        assertEquals(0, links % 2, "número par de eslabones para que la alternancia empalme");
        assertEquals(total, links * stations.pitch(), 1.0E-9);
        assertEquals(stations.identity(0), stations.identity(links));
        Set<BlockVector3> blocks = TubeFigures.generate(loop, chain, LIMIT);
        assertTrue(missingCore(loop, chain, blocks).isEmpty(), "la cadena cerrada no tiene huecos en la costura");
    }

    @Test
    void figurePatternReadsTheImplicitValue() {
        SampledPath path = straight(60);
        ShapeParameters chain = exact(PathShape.CHAINLINK.defaults().withRadius(8), Map.of());
        TubeFigures.MaterialProbe probe = TubeFigures.materialProbe(path, chain, TubeCurve.NONE);
        int upright = 0;
        int flat = 0;
        for (BlockVector3 block : TubeFigures.generate(path, chain, LIMIT)) {
            double value = probe.value(block, -1.0);
            assertTrue(value >= 0.0 && value <= 1.0, "valor fuera de rango en " + block);
            if (value < 0.5) {
                upright++;
            } else {
                flat++;
            }
        }
        assertTrue(upright > 100 && flat > 100, "cada orientación de eslabón tiene su franja de paleta");
    }

    @Test
    void largeExactFiguresStayFast() {
        SampledPath path = SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(120, 40, 60),
                BlockVector3.at(240, 0, 180)), false);
        long start = System.nanoTime();
        Set<BlockVector3> blocks = TubeFigures.generate(path, exact(PathShape.KNOTS.defaults().withRadius(20),
                Map.of()), LIMIT);
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(blocks.size() > 20_000, "nudos: " + blocks.size());
        assertTrue(elapsed < 10_000, "demasiado lento: " + elapsed + " ms para " + blocks.size() + " bloques");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Almas de los hilos: puntos que tienen que caer dentro de la figura (mismas fórmulas que TubeShapes).
    // ---------------------------------------------------------------------------------------------------------

    /** Bloques que contienen un punto del alma de algún hilo y faltan en la figura. */
    private static List<BlockVector3> missingCore(SampledPath path, ShapeParameters parameters,
                                                  Set<BlockVector3> blocks) {
        TubeSettings settings = TubeSettings.from(parameters);
        TubeCenterline line = TubeCenterline.create(path, settings, TubeCurve.NONE);
        List<double[]> core = core(parameters, line);
        List<BlockVector3> missing = new ArrayList<>();
        for (int index = 1; index < core.size(); index++) {
            double[] from = core.get(index - 1);
            double[] to = core.get(index);
            if (from == null || to == null) {
                continue;
            }
            // Puntos en espacio de tubo; se pasan al mundo en pasos de una décima de bloque.
            double[] start = world(line, from[0], from[1], from[2]);
            double[] end = world(line, to[0], to[1], to[2]);
            double length = Math.sqrt(square(end[0] - start[0]) + square(end[1] - start[1]) + square(end[2] - start[2]));
            int steps = Math.max(1, (int) Math.ceil(length * 10.0));
            for (int step = 0; step <= steps; step++) {
                double amount = step / (double) steps;
                double[] point = world(line, from[0] + (to[0] - from[0]) * amount,
                        from[1] + (to[1] - from[1]) * amount, from[2] + (to[2] - from[2]) * amount);
                BlockVector3 block = BlockVector3.at(Math.floor(point[0]), Math.floor(point[1]), Math.floor(point[2]));
                if (!blocks.contains(block) && !missing.contains(block)) {
                    missing.add(block);
                }
            }
        }
        return missing;
    }

    /**
     * Polilíneas del alma en coordenadas de tubo {x, y, z}; un {@code null} separa polilíneas. Los extremos de los
     * recorridos abiertos se dejan fuera (allí manda el modo de extremo).
     */
    private static List<double[]> core(ShapeParameters parameters, TubeCenterline line) {
        List<double[]> points = new ArrayList<>();
        double total = line.longitudinal[line.count - 1];
        double progress = (parameters.spacing() - 2.0) / 6.0;
        double first = line.closed ? 0.0 : 0.6;
        double last = line.closed ? total : total - 0.6;
        switch (parameters.shape()) {
            case COIL -> {
                double frequency = Math.PI * 2.0 / (0.90 + 2.50 * progress);
                for (double z = first; z <= last; z += 0.02) {
                    points.add(radial(frequency * z, 1.0, z));
                }
            }
            case FISHNET -> {
                // Hilos a ±45°: un radián por cada radio recorrido.
                int strands = ShapeGenerator.fishnetStrands(parameters);
                for (int direction = -1; direction <= 1; direction += 2) {
                    for (int strand = 0; strand < strands; strand++) {
                        points.add(null);
                        for (double z = first; z <= last; z += 0.01) {
                            points.add(radial(Math.PI * 2.0 * strand / strands + direction * z, 1.0, z));
                        }
                    }
                }
            }
            case OSCILLATE -> {
                double frequency = Math.PI * 2.0 / (1.10 + 3.40 * progress);
                for (double width = -0.9; width <= 0.91; width += 0.45) {
                    points.add(null);
                    for (double z = first; z <= last; z += 0.02) {
                        double phase = frequency * z;
                        points.add(new double[]{-width * Math.sin(phase),
                                0.42 * Math.sin(phase) + width * Math.cos(phase), z});
                    }
                }
            }
            case RINGS, CHAINLINK, HONEYCOMB, KNOTS -> motifCores(parameters, line, points, first, last);
            default -> throw new IllegalArgumentException(parameters.shape().id());
        }
        return points;
    }

    private static void motifCores(ShapeParameters parameters, TubeCenterline line, List<double[]> points,
                                   double first, double last) {
        double radius = parameters.radius();
        double progress = (parameters.spacing() - 2.0) / 6.0;
        int structural = Math.clamp((int) Math.ceil(radius / 8.0) + parameters.thickness() - 1, 1, 4);
        TubeShapes.Stations stations = switch (parameters.shape()) {
            case RINGS -> TubeShapes.Stations.along(line, 0.32 + 1.28 * progress, false);
            case CHAINLINK -> TubeShapes.Stations.along(line, 1.10 + 2.10 * progress, true);
            case HONEYCOMB -> TubeShapes.Stations.along(line, 0.45 + 1.05 * progress, true);
            default -> TubeShapes.Stations.along(line, 0.95 + 1.75 * progress, false);
        };
        double total = line.longitudinal[line.count - 1];
        for (int index = 0; stations.exists(index) && stations.center(index) <= total + 1.0E-9; index++) {
            if (line.closed && index > 0 && stations.identity(index) == 0) {
                break;
            }
            double center = stations.center(index);
            int identity = stations.identity(index);
            switch (parameters.shape()) {
                case RINGS -> {
                    double width = Math.max(0.8, structural) / radius;
                    if (center < first || center > last) {
                        continue;
                    }
                    points.add(null);
                    for (double angle = 0.0; angle <= Math.PI * 2.0 + 1.0E-9; angle += 0.02) {
                        points.add(radial(angle, 1.0 - width, center));
                    }
                }
                case CHAINLINK -> {
                    boolean upright = (identity & 1) == 0;
                    points.add(null);
                    for (double angle = 0.0; angle <= Math.PI * 2.0 + 1.0E-9; angle += 0.01) {
                        double along = Math.cos(angle) * 1.2;
                        double across = Math.sin(angle) * 0.68;
                        double z = center + along;
                        if (z < first || z > last) {
                            points.add(null);
                            continue;
                        }
                        points.add(upright ? new double[]{0.0, across, z} : new double[]{across, 0.0, z});
                    }
                }
                case HONEYCOMB -> {
                    double offset = (identity & 1) == 0 ? 0.0 : Math.PI / 6.0;
                    if (center >= first && center <= last) {
                        points.add(null);
                        for (int vertex = 0; vertex <= 6; vertex++) {
                            double[] from = radial(offset + vertex * Math.PI / 3.0, 1.0, center);
                            double[] to = radial(offset + (vertex + 1) * Math.PI / 3.0, 1.0, center);
                            for (double amount = 0.0; amount < 1.0; amount += 0.05) {
                                points.add(lerp(from, to, amount));
                            }
                        }
                    }
                    if (index > 0) {
                        double previousCenter = stations.center(index - 1);
                        double previousOffset = (identity & 1) == 0 ? Math.PI / 6.0 : 0.0;
                        for (int vertex = identity & 1; vertex < 6; vertex += 2) {
                            double[] from = radial(previousOffset + vertex * Math.PI / 3.0, 1.0, previousCenter);
                            double[] to = radial(offset + vertex * Math.PI / 3.0, 1.0, center);
                            points.add(null);
                            for (double amount = 0.0; amount <= 1.0; amount += 0.02) {
                                double[] point = lerp(from, to, amount);
                                points.add(point[2] < first || point[2] > last ? null : point);
                            }
                        }
                    }
                }
                case KNOTS -> {
                    double turn = identity * 0.47;
                    points.add(null);
                    for (double t = 0.0; t <= Math.PI * 2.0 + 1.0E-9; t += 0.005) {
                        double breathing = 0.70 + 0.22 * Math.cos(t * 3.0);
                        double z = center + Math.sin(t * 3.0) * 0.34;
                        points.add(z < first || z > last ? null : radial(t * 2.0 + turn, breathing, z));
                    }
                }
                default -> throw new IllegalStateException();
            }
        }
    }

    /** Punto a {@code distance} radios del eje en el ángulo clásico (0 = normal, crece hacia la binormal). */
    private static double[] radial(double angle, double distance, double z) {
        return new double[]{-Math.sin(angle) * distance, Math.cos(angle) * distance, z};
    }

    private static double[] lerp(double[] from, double[] to, double amount) {
        return new double[]{from[0] + (to[0] - from[0]) * amount, from[1] + (to[1] - from[1]) * amount,
                from[2] + (to[2] - from[2]) * amount};
    }

    /** Coordenadas de tubo → mundo, interpolando el marco del eje en la coordenada longitudinal {@code z}. */
    private static double[] world(TubeCenterline line, double x, double y, double z) {
        int low = 0;
        int high = line.count - 1;
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (line.longitudinal[middle] <= z) {
                low = middle;
            } else {
                high = middle;
            }
        }
        double span = line.longitudinal[high] - line.longitudinal[low];
        double amount = span < 1.0E-12 ? 0.0 : Math.clamp((z - line.longitudinal[low]) / span, 0.0, 1.0);
        double radius = mix(line.radius, low, high, amount);
        double roll = mix(line.roll, low, high, amount);
        double localX = x * Math.cos(roll) - y * Math.sin(roll);
        double localY = x * Math.sin(roll) + y * Math.cos(roll);
        double[] result = new double[3];
        double[][] position = {line.px, line.py, line.pz};
        double[][] side = {line.sx, line.sy, line.sz};
        double[][] up = {line.ux, line.uy, line.uz};
        for (int axis = 0; axis < 3; axis++) {
            result[axis] = mix(position[axis], low, high, amount)
                    + mix(side[axis], low, high, amount) * localX * radius
                    + mix(up[axis], low, high, amount) * localY * radius;
        }
        return result;
    }

    private static double mix(double[] values, int low, int high, double amount) {
        return values[low] + (values[high] - values[low]) * amount;
    }

    // ---------------------------------------------------------------------------------------------------------

    private static ShapeParameters exact(ShapeParameters parameters, Map<String, Double> extra) {
        Map<String, Double> options = new HashMap<>(extra);
        options.put("engine", 1.0);
        return with(parameters, options);
    }

    private static ShapeParameters with(ShapeParameters parameters, Map<String, Double> extra) {
        Map<String, Double> options = new HashMap<>(parameters.options().values());
        options.putAll(extra);
        return parameters.withOptions(new ShapeOptions(options));
    }

    private static SampledPath straight(int length) {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(length, 0, 0)), false);
    }

    private static SampledPath oblique() {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(53, 23, 37)), false);
    }

    private static SampledPath curved() {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(30, 12, 8),
                BlockVector3.at(52, 20, 40), BlockVector3.at(60, 4, 75)), false);
    }

    /** Distancia máxima al eje x entre los bloques de la columna {@code x}. */
    private static double reach(Set<BlockVector3> blocks, int x) {
        double reach = 0.0;
        for (BlockVector3 block : blocks) {
            if (block.x() == x) {
                reach = Math.max(reach, Math.hypot(block.y(), block.z()));
            }
        }
        return reach;
    }

    private static int largestComponent(Set<BlockVector3> blocks) {
        LongOpenHashSet packed = new LongOpenHashSet();
        blocks.forEach(block -> packed.add(PackedBlockPlan.pack(block.x(), block.y(), block.z())));
        return TubeFigures.largestComponent(packed).size();
    }

    private static double[] ribbon(TubeCenterline line, double frequency, double width, double z) {
        double phase = frequency * z;
        return world(line, -width * Math.sin(phase), 0.42 * Math.sin(phase) + width * Math.cos(phase), z);
    }

    private static double[] subtract(double[] first, double[] second) {
        return new double[]{first[0] - second[0], first[1] - second[1], first[2] - second[2]};
    }

    private static double[] cross(double[] first, double[] second) {
        return new double[]{first[1] * second[2] - first[2] * second[1], first[2] * second[0] - first[0] * second[2],
                first[0] * second[1] - first[1] * second[0]};
    }

    private static double[] normalize(double[] vector) {
        double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        return new double[]{vector[0] / length, vector[1] / length, vector[2] / length};
    }

    private static BlockVector3 block(double[] point, double[] direction, double distance) {
        return BlockVector3.at(Math.floor(point[0] + direction[0] * distance),
                Math.floor(point[1] + direction[1] * distance), Math.floor(point[2] + direction[2] * distance));
    }

    /** Si hay camino en 6-vecindad de {@code from} a {@code to} sin pisar la figura dentro de una caja local. */
    private static boolean connected(Set<BlockVector3> blocks, BlockVector3 from, BlockVector3 to,
                                     BlockVector3 center, int half) {
        Set<BlockVector3> visited = new java.util.HashSet<>();
        ArrayDeque<BlockVector3> queue = new ArrayDeque<>();
        queue.add(from);
        visited.add(from);
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            BlockVector3 current = queue.poll();
            if (current.equals(to)) {
                return true;
            }
            for (int[] offset : offsets) {
                BlockVector3 next = current.add(offset[0], offset[1], offset[2]);
                if (Math.abs(next.x() - center.x()) > half || Math.abs(next.y() - center.y()) > half
                        || Math.abs(next.z() - center.z()) > half) {
                    continue;
                }
                if (!blocks.contains(next) && visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    /** Un hueco cerrado: desde {@code start} no se puede salir a más de {@code limit} bloques sin cruzar la figura. */
    private static boolean isEnclosed(Set<BlockVector3> blocks, BlockVector3 start, int limit) {
        Set<BlockVector3> visited = new java.util.HashSet<>();
        ArrayDeque<BlockVector3> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            BlockVector3 current = queue.poll();
            if (Math.abs(current.x() - start.x()) > limit || Math.abs(current.y() - start.y()) > limit
                    || Math.abs(current.z() - start.z()) > limit) {
                return false;
            }
            for (int[] offset : offsets) {
                BlockVector3 next = current.add(offset[0], offset[1], offset[2]);
                if (!blocks.contains(next) && visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return true;
    }

    private static double square(double value) {
        return value * value;
    }
}
