package com.komainu.moxia.shape.tube;

import com.komainu.moxia.FullTests;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeParameters;
import com.komainu.moxia.shape.Vec3;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Formas de {@code //ezspline} en espacio de tubo: sin huecos, deterministas y del tamaño que dice la wiki.
 *
 * <p>Las comprobaciones que recorren todas las formas, recorridos y calidades pasan en cada build por una muestra
 * fija; con {@code KOMAEDIT_FULL_TESTS=1}, por todo ({@link FullTests}).</p>
 */
class EzSplineShapesTest {
    private static final int LIMIT = 1_500_000;
    /** Una muestra de cada forma, con parámetros por defecto y alguno de los ejemplos de la wiki. */
    private static final List<String> FORMS = List.of(
            "3d Beads clay 6", "3d Cubes clay 6", "3d Cubes(G:0) clay 6", "3d Chainlink clay 8",
            "3d Chainlink(M:99,N:99,E:0.6) clay 8", "3d Chainlink(M:1,N:1,E:0.7,G:-0.2,T:1.2) clay 8",
            "3d Chainlink(M:2,N:2,E:0,G:1) clay 8", "3d Fishnet clay 8", "3d Fi(S:2,D:0.5,W:0.5) clay 8",
            "3d Honeycomb clay 10", "3d Oscillate clay 6", "3d Os(D:0.6,I:1.5) clay 6", "3d Rings clay 8",
            "3d Ri(E:0.5,T:0.3,G:0.5,M:6,N:4) clay 8", "3d Sc clay 10", "3d Sc(c:8,d:1.2) clay 12",
            "3d Noodles clay 10", "3d Braids clay 7",
            "2d SC(E:0.5) clay 8", "2d SC(E:99) clay 8", "2d CC(C:1) clay 6", "2d CC(C:5,F:true) clay 8",
            "2d Sr(C:3,S:0.4) clay 9", "2d Pipe clay 8", "2d Re(x1:0.2,y1:-1,x2:1,y2:0.3) clay 8",
            "2d St(S:6,D:0.6) clay 8", "2d Fl(C:5,D:0.4) clay 8", "2d Polygon(S:6,D:0.3) clay 8",
            "noise stone 8", "noise stone 8 Ce(F:1.5,cR:sub) 0.5", "noise stone 8 -i r=sqrt(x*x+y*y);r<0.6+0.4*n",
            "expression stone 6 -z x*x+y*y<1-z*z*0.6"
    );

    @Test
    void everyFormMatchesABruteForceScan() {
        // Ningún bloque queda fuera de los ladrillos que mira el voxelizador (alcance, margen y remates bien medidos).
        SampledPath path = oblique();
        for (String form : FullTests.sample(FORMS, 4)) {
            ShapeParameters parameters = parameters(form);
            Set<BlockVector3> blocks = TubeFigures.generate(path, parameters, LIMIT);
            assertFalse(blocks.isEmpty(), form + " no genera nada");
            Set<BlockVector3> scanned = bruteForce(path, parameters);
            Set<BlockVector3> missing = new HashSet<>(scanned);
            missing.removeAll(blocks);
            assertTrue(missing.isEmpty(), form + ": el voxelizador se salta " + missing.size() + " bloques, como "
                    + missing.stream().limit(4).toList());
            assertEquals(scanned.size(), blocks.size(), form);
        }
    }

    @Test
    void everyFormIsDeterministic() {
        SampledPath path = curved();
        for (String form : FullTests.sample(FORMS, 4, 2)) {
            ShapeParameters parameters = parameters(form);
            assertEquals(TubeFigures.generate(path, parameters, LIMIT), TubeFigures.generate(path, parameters, LIMIT),
                    form + " cambia entre dos ejecuciones");
        }
    }

    @Test
    void solidFormsHaveNoCavities() {
        List<String> solid = List.of("3d Beads clay 6", "3d Cubes clay 6", "3d Cubes(G:0) clay 6",
                "3d Oscillate clay 6", "3d Os(D:0.6,I:1.5) clay 6", "3d Sc clay 10", "3d Sc(c:8,d:1.2) clay 12",
                "3d Ri(E:0,T:1,G:0,M:2,N:2) clay 6", "2d SC(E:0.5) clay 8", "2d SC(E:99) clay 8",
                "2d St(S:6,D:0.6) clay 8", "2d Fl(C:5,D:0.4) clay 8",
                "2d Re(x1:0.2,y1:-1,x2:1,y2:0.3) clay 8", "2d Pipe clay 8");
        List<String> failures = new ArrayList<>();
        List<SampledPath> paths = List.of(straight(60), oblique(), curved());
        for (int pathIndex : FullTests.pick(List.of(0, 1, 2), List.of(1))) {
            for (String form : solid) {
                for (double quality : FullTests.pick(List.of(0.0, 1.0, 3.0), List.of(1.0))) {
                    ShapeParameters parameters = with(parameters(form), Map.of("quality", quality));
                    Set<BlockVector3> blocks = TubeFigures.generate(paths.get(pathIndex), parameters, LIMIT);
                    int cavities = cavities(blocks);
                    if (cavities > 0) {
                        failures.add(form + " (recorrido " + pathIndex + ", calidad " + quality + ") encierra "
                                + cavities + " bloques de aire");
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join(" · ", failures));
    }

    @Test
    void continuousFormsStayInOnePiece() {
        List<String> continuous = List.of("3d Cubes(G:0) clay 6", "3d Fishnet clay 8", "3d Fi(S:2,D:0.5,W:0.5) clay 8",
                "3d Honeycomb clay 10", "3d Oscillate clay 6", "3d Os(D:0.6,I:1.5) clay 6", "3d Rings clay 8",
                "3d Sc clay 10", "3d Sc(c:8,d:1.2) clay 12", "3d Chainlink clay 8", "2d SC(E:0.5) clay 8",
                "2d Pipe clay 8", "2d St(S:6,D:0.6) clay 8", "2d Fl(C:5,D:0.4) clay 8", "noise stone 8");
        for (SampledPath path : FullTests.pick(List.of(straight(60), oblique(), curved()), List.of(oblique()))) {
            for (String form : continuous) {
                Set<BlockVector3> blocks = TubeFigures.generate(path, parameters(form), LIMIT);
                List<Integer> sizes = components(blocks);
                assertTrue(sizes.getFirst() >= blocks.size() * 0.995, form + ": la pieza principal tiene "
                        + sizes.getFirst() + " de " + blocks.size() + " bloques");
            }
        }
    }

    @Test
    void separateMotifsComeOutWhole() {
        SampledPath path = curved();
        // Cada cubo, cada eslabón de una mitad de la cadena, cada hebra suelta y cada fideo, de una pieza.
        assertMotifs(path, "3d Cubes clay 6");
        assertMotifs(path, "3d Chainlink(P:FIRST) clay 8");
        assertMotifs(path, "3d Chainlink(P:SECOND) clay 8");
        assertMotifs(path, "3d Ri(E:0.2,T:0.2,G:1,M:2,N:2) clay 8");
        ShapeParameters strands = parameters("2d Sr(C:3,S:0.4) clay 9");
        assertEquals(3, components(TubeFigures.generate(path, strands, LIMIT)).size(), "tres hebras separadas");
        ShapeParameters noodles = parameters("3d Noodles(A:8,W:0.5) clay 12");
        List<Integer> pieces = components(TubeFigures.generate(path, noodles, LIMIT));
        assertEquals(8, pieces.size(), "ocho fideos que no se tocan ni se cortan: " + pieces);
    }

    @Test
    void wireCoresHaveNoGaps() {
        for (SampledPath path : FullTests.pick(List.of(straight(60), oblique(), curved()), List.of(curved()))) {
            for (String form : List.of("3d Chainlink clay 8", "3d Chainlink(M:99,N:99,E:0.6) clay 8",
                    "3d Chainlink(M:2,N:2,E:0,G:1,T:0.3) clay 8", "3d Fishnet clay 8", "3d Fishnet(W:0.05) clay 10",
                    "3d Honeycomb clay 10", "3d Honeycomb(W:0.01,D:0.05) clay 10", "3d Noodles clay 10",
                    "3d Noodles(A:20,D:40%) clay 12")) {
                for (double quality : new double[]{0.0, 1.0}) {
                    ShapeParameters parameters = with(parameters(form), Map.of("quality", quality));
                    Set<BlockVector3> blocks = TubeFigures.generate(path, parameters, LIMIT);
                    List<BlockVector3> missing = missingCore(path, parameters, blocks);
                    assertTrue(missing.isEmpty(), form + " (calidad " + quality + ") deja huecos en el alma, "
                            + "por ejemplo en " + missing.subList(0, Math.min(5, missing.size())));
                }
            }
        }
    }

    @Test
    void exactQualityKeepsTheVolume() {
        SampledPath path = straight(60);
        // Por rodaja interior, el círculo de radio 8 debe ocupar su área.
        Set<BlockVector3> disk = TubeFigures.generate(path, parameters("basic clay 8 -q EXACT"), LIMIT);
        long slice = disk.stream().filter(block -> block.x() == 30).count();
        assertTrue(Math.abs(slice - Math.PI * 64.0) / (Math.PI * 64.0) < 0.03, "rodaja EXACT de " + slice
                + " bloques para " + Math.round(Math.PI * 64.0));
        int square = TubeFigures.generate(oblique(), parameters("2d Square clay 6 -q EXACT"), LIMIT).size();
        double length = Math.sqrt(53 * 53 + 23 * 23 + 37 * 37);
        double prism = 12.0 * 12.0 * length;
        assertTrue(Math.abs(square - prism) / prism < 0.03, "cuadrado oblicuo: " + square + " para " + prism);
        // Más fino que HIGH, que da por buenos los bloques ocupados en un 25 %.
        int high = TubeFigures.generate(oblique(), parameters("2d Square clay 6 -q HIGH"), LIMIT).size();
        assertTrue(Math.abs(square - prism) <= Math.abs(high - prism), "EXACT " + square + ", HIGH " + high);
    }

    @Test
    void ezEditsSizesHold() {
        SampledPath path = straight(80);
        // Cuentas: esferas de un radio que se tocan, una cada diámetro.
        Set<BlockVector3> beads = TubeFigures.generate(path, parameters("3d Beads clay 6 -q EXACT"), LIMIT);
        double sphere = 4.0 / 3.0 * Math.PI * 216.0;
        assertTrue(beads.size() > sphere * 6.0 && beads.size() < sphere * 7.5, "volumen de cuentas " + beads.size());
        // Se tocan: el radio llega a 6 en el centro de cada cuenta y casi a 0 entre dos.
        double[] profile = new double[80];
        for (int x = 0; x < 80; x++) {
            profile[x] = reach(beads, x);
        }
        for (int bead = 1; bead < 6; bead++) {
            int center = (int) Math.floor(path.controlPoints().getFirst().x() + bead * 12.0);
            assertTrue(profile[center] >= 5.5, "centro de la cuenta " + bead + ": " + profile[center]);
            assertTrue(Math.min(profile[center + 6], profile[center + 5]) <= 2.5, "entre cuentas " + bead + ": "
                    + profile[center + 6]);
        }
        // Cubos de un diámetro con hueco de medio tamaño: 80 bloques / 18 de paso.
        assertEquals(5, components(TubeFigures.generate(path, parameters("3d Cubes clay 6"), LIMIT)).size());
        // Rectángulo por esquinas: solo el cuarto de sección entre 0,6 y 1 en x e y.
        Set<BlockVector3> corner = TubeFigures.generate(path, parameters("2d Re(x1:0.6,y1:0.6,x2:1.0,y2:1.0) clay 12"),
                LIMIT);
        double area = 4.8 * 4.8 * 80.0;
        assertTrue(Math.abs(corner.size() - area) / area < 0.2, "rectángulo de esquina: " + corner.size());
        for (BlockVector3 block : corner) {
            double distance = Math.hypot(block.y() + 0.5, block.z() + 0.5);
            assertTrue(distance >= 12.0 * 0.6 * Math.sqrt(2.0) - 1.5, "el rectángulo no toca el eje: " + block);
        }
        // Tubería: hueco del 80 % del radio.
        for (BlockVector3 block : TubeFigures.generate(path, parameters("2d Pipe clay 10"), LIMIT)) {
            if (block.x() > 2 && block.x() < 78) {
                assertTrue(Math.hypot(block.y() + 0.5, block.z() + 0.5) >= 7.0, "la tubería tiene alma: " + block);
            }
        }
        // Oscilar: el radio baja la profundidad pedida cada intervalo.
        Set<BlockVector3> waves = TubeFigures.generate(path, parameters("3d Os(D:0.5,I:2) clay 10"), LIMIT);
        double thinnest = Double.MAX_VALUE;
        double thickest = 0.0;
        for (int x = 4; x < 76; x++) {
            double distance = reach(waves, x);
            thinnest = Math.min(thinnest, distance);
            thickest = Math.max(thickest, distance);
        }
        assertTrue(thickest > 9.0 && thinnest < 5.8, "ondas entre " + thinnest + " y " + thickest);
    }

    @Test
    void noiseCutExpressionDecidesTheShape() {
        SampledPath path = curved();
        // Una expresión de corte que no mira el ruido es un cilindro: el mismo que basic con la mitad de radio.
        Set<BlockVector3> cut = TubeFigures.generate(path, parameters("noise stone 8 -q FAST -i x*x+y*y<0.25"), LIMIT);
        Set<BlockVector3> basic = TubeFigures.generate(path, parameters("basic stone 8 -q FAST"), LIMIT);
        assertTrue(cut.size() > 0 && cut.size() < basic.size());
        Set<BlockVector3> half = TubeFigures.generate(path, parameters("2d Pipe(I:0) stone 4 -q FAST"), LIMIT);
        assertEquals(half.size(), cut.size(), 0.02 * half.size(), "r < 0,5 a radio 8 es el círculo de radio 4");
        // La profundidad acota el mordisco: con 0,3 el 70 % interior queda macizo.
        Set<BlockVector3> shallow = TubeFigures.generate(path, parameters("noise stone 10 Perlin(F:3) 0.3"), LIMIT);
        Set<BlockVector3> core = TubeFigures.generate(path, parameters("basic stone 6.8 -q FAST"), LIMIT);
        Set<BlockVector3> missing = new HashSet<>(core);
        missing.removeAll(shallow);
        assertTrue(missing.size() < core.size() * 0.005, "el ruido no entra más que la profundidad: " + missing.size());
    }

    @Test
    void normalizedZAndIndexedOutput() {
        SampledPath path = straight(60);
        ShapeParameters firstHalf = parameters("expression stone 6 -z z<0 && x*x+y*y<1");
        for (BlockVector3 block : TubeFigures.generate(path, firstHalf, LIMIT)) {
            assertTrue(block.x() <= 31, "con -z la mitad z < 0 es la primera: " + block);
        }
        EzSpline.Request indexed = EzSpline.parse("expression a,b,c,d,e 5 -o x*x+y*y<1?3.001:0", null)
                .withPaletteSize(5);
        TubeFigures.MaterialProbe probe = TubeFigures.materialProbe(path, indexed.parameters(), TubeCurve.NONE);
        for (BlockVector3 block : TubeFigures.generate(path, indexed.parameters(), LIMIT)) {
            double value = probe.value(block, -1.0);
            assertEquals(3, (int) Math.ceil(value * 5.0) - 1, "-o: 3,001 es la cuarta entrada en " + block);
        }
    }

    @Test
    void noiseCutDialect() {
        TubeFormula cut = TubeFormula.compileNoiseCut("t=0.2;r=sqrt(x*x+y*y);m=1-abs(2*r-t-1)/abs(t-1);n<m&&r<1");
        TubeFormula.Runner runner = cut.runner(1L, 0);
        // A radio 0,9 el umbral m vale 0,25: el ruido por debajo coloca y por encima no.
        assertEquals(1.0, runner.evaluate(0.9, 0.0, 0.0, 0.0, 0.1, 3.0));
        assertEquals(0.0, runner.evaluate(0.9, 0.0, 0.0, 0.0, 0.5, 3.0));
        assertEquals(0.5, TubeFormula.compileNoiseCut("d*n").runner(1L, 0).evaluate(0, 0, 0, 0, 0.25, 2.0));
        assertThrows(IllegalArgumentException.class, () -> TubeFormula.compile("d > 0"),
                "fuera del spline de ruido d no existe");
        assertThrows(IllegalArgumentException.class, () -> TubeFormula.compile("t=1; x > t"),
                "fuera del spline de ruido t no se redefine");
        assertThrows(IllegalArgumentException.class, () -> TubeFormula.compileNoiseCut("x=1; x > 0"));
    }

    // ---------------------------------------------------------------------------------------------------------

    private static void assertMotifs(SampledPath path, String form) {
        ShapeParameters parameters = parameters(form);
        Set<BlockVector3> blocks = TubeFigures.generate(path, parameters, LIMIT);
        List<Integer> sizes = components(blocks);
        int smallest = sizes.getLast();
        assertTrue(smallest >= sizes.getFirst() * 0.4, form + ": hay piezas sueltas de " + smallest + " bloques ("
                + sizes + ")");
    }

    private static Set<BlockVector3> bruteForce(SampledPath path, ShapeParameters parameters) {
        TubeSettings settings = TubeSettings.from(parameters);
        TubeCenterline line = TubeCenterline.create(path, settings, TubeCurve.NONE);
        TubeShapes.TubeShape shape = TubeShapes.create(settings, parameters, line, TubeFigures.pathSeed(path));
        TubeVoxelizer.Evaluator evaluator = new TubeVoxelizer.Evaluator(line, settings, shape);
        double margin = parameters.radius() * 2.2 + 6.0;
        double[] minimum = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] maximum = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Vec3 point : path.controlPoints()) {
            double[] coordinates = {point.x(), point.y(), point.z()};
            for (int axis = 0; axis < 3; axis++) {
                minimum[axis] = Math.min(minimum[axis], coordinates[axis]);
                maximum[axis] = Math.max(maximum[axis], coordinates[axis]);
            }
        }
        Set<BlockVector3> blocks = new HashSet<>();
        for (int x = (int) Math.floor(minimum[0] - margin); x <= maximum[0] + margin; x++) {
            for (int y = (int) Math.floor(minimum[1] - margin); y <= maximum[1] + margin; y++) {
                for (int z = (int) Math.floor(minimum[2] - margin); z <= maximum[2] + margin; z++) {
                    if (evaluator.place(x + 0.5, y + 0.5, z + 0.5)) {
                        blocks.add(BlockVector3.at(x, y, z));
                    }
                }
            }
        }
        if (!EzShapes.handles(parameters)) {
            return blocks;
        }
        LongOpenHashSet packed = new LongOpenHashSet();
        blocks.forEach(block -> packed.add(PackedBlockPlan.pack(block.x(), block.y(), block.z())));
        TubeFigures.fillPockets(packed, TubeFigures.POCKET);
        Set<BlockVector3> filled = new HashSet<>();
        packed.forEach((long value) -> filled.add(BlockVector3.at(PackedBlockPlan.unpackX(value),
                PackedBlockPlan.unpackY(value), PackedBlockPlan.unpackZ(value))));
        return filled;
    }

    /** Bloques de aire encerrados: los que no se alcanzan desde fuera de la caja de la figura (vecindad 6). */
    private static int cavities(Set<BlockVector3> blocks) {
        LongOpenHashSet solid = new LongOpenHashSet();
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (BlockVector3 block : blocks) {
            solid.add(PackedBlockPlan.pack(block.x(), block.y(), block.z()));
            bounds[0] = Math.min(bounds[0], block.x() - 1);
            bounds[1] = Math.min(bounds[1], block.y() - 1);
            bounds[2] = Math.min(bounds[2], block.z() - 1);
            bounds[3] = Math.max(bounds[3], block.x() + 1);
            bounds[4] = Math.max(bounds[4], block.y() + 1);
            bounds[5] = Math.max(bounds[5], block.z() + 1);
        }
        LongOpenHashSet outside = new LongOpenHashSet();
        LongArrayList queue = new LongArrayList();
        long start = PackedBlockPlan.pack(bounds[0], bounds[1], bounds[2]);
        outside.add(start);
        queue.add(start);
        for (int head = 0; head < queue.size(); head++) {
            long current = queue.getLong(head);
            int x = PackedBlockPlan.unpackX(current);
            int y = PackedBlockPlan.unpackY(current);
            int z = PackedBlockPlan.unpackZ(current);
            for (int axis = 0; axis < 6; axis++) {
                int sign = (axis & 1) == 0 ? 1 : -1;
                int nextX = axis >> 1 == 0 ? x + sign : x;
                int nextY = axis >> 1 == 1 ? y + sign : y;
                int nextZ = axis >> 1 == 2 ? z + sign : z;
                if (nextX < bounds[0] || nextX > bounds[3] || nextY < bounds[1] || nextY > bounds[4]
                        || nextZ < bounds[2] || nextZ > bounds[5]) {
                    continue;
                }
                long next = PackedBlockPlan.pack(nextX, nextY, nextZ);
                if (!solid.contains(next) && outside.add(next)) {
                    queue.add(next);
                }
            }
        }
        long volume = (long) (bounds[3] - bounds[0] + 1) * (bounds[4] - bounds[1] + 1) * (bounds[5] - bounds[2] + 1);
        return (int) (volume - solid.size() - outside.size());
    }

    /** Tamaños de las piezas unidas por cara, de mayor a menor. */
    private static List<Integer> components(Set<BlockVector3> blocks) {
        LongOpenHashSet remaining = new LongOpenHashSet();
        for (BlockVector3 block : blocks) {
            remaining.add(PackedBlockPlan.pack(block.x(), block.y(), block.z()));
        }
        List<Integer> sizes = new ArrayList<>();
        LongArrayList queue = new LongArrayList();
        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().nextLong();
            remaining.remove(seed);
            queue.clear();
            queue.add(seed);
            for (int head = 0; head < queue.size(); head++) {
                long current = queue.getLong(head);
                int x = PackedBlockPlan.unpackX(current);
                int y = PackedBlockPlan.unpackY(current);
                int z = PackedBlockPlan.unpackZ(current);
                for (int axis = 0; axis < 6; axis++) {
                    int sign = (axis & 1) == 0 ? 1 : -1;
                    long next = PackedBlockPlan.pack(axis >> 1 == 0 ? x + sign : x, axis >> 1 == 1 ? y + sign : y,
                            axis >> 1 == 2 ? z + sign : z);
                    if (remaining.remove(next)) {
                        queue.add(next);
                    }
                }
            }
            sizes.add(queue.size());
        }
        sizes.sort((first, second) -> Integer.compare(second, first));
        return sizes;
    }

    /** Bloques del alma de los hilos que faltan; los extremos abiertos se dejan fuera (allí manda el remate). */
    private static List<BlockVector3> missingCore(SampledPath path, ShapeParameters parameters,
                                                  Set<BlockVector3> blocks) {
        TubeSettings settings = TubeSettings.from(parameters);
        TubeCenterline line = TubeCenterline.create(path, settings, TubeCurve.NONE);
        TubeShapes.TubeShape shape = TubeShapes.create(settings, parameters, line, TubeFigures.pathSeed(path));
        List<double[]> core = core(parameters, line, shape);
        List<BlockVector3> missing = new ArrayList<>();
        for (int index = 1; index < core.size(); index++) {
            double[] from = core.get(index - 1);
            double[] to = core.get(index);
            if (from == null || to == null) {
                continue;
            }
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

    /** Polilíneas del alma en coordenadas de tubo {x, y, z}; un {@code null} separa polilíneas. */
    private static List<double[]> core(ShapeParameters parameters, TubeCenterline line, TubeShapes.TubeShape shape) {
        List<double[]> points = new ArrayList<>();
        double total = line.longitudinal[line.count - 1];
        double first = 0.6;
        double last = total - 0.6;
        double blocks = parameters.radius();
        var options = parameters.options();
        switch (EzShapes.form(options)) {
            case CHAINLINK -> {
                double thickness = options.get("thickness", 1.0);
                double extrusion = options.get("extrusion", 0.2);
                double gap = options.get("gap", 0.0);
                double major = options.get("majorexponent", 3.0);
                double wire = Math.min(0.9, 0.25 * thickness);
                double halfWidth = 1.0 - wire;
                double halfLength = halfWidth * (1.0 + extrusion);
                double pitch = 2.0 * (halfLength - wire) + gap * 2.0 * halfLength;
                TubeShapes.Stations stations = TubeShapes.Stations.regular(line, pitch, true);
                for (int index = 0; index < stations.count(); index++) {
                    double center = stations.center(index);
                    boolean upright = (stations.identity(index) & 1) == 0;
                    points.add(null);
                    for (double angle = 0.0; angle <= Math.PI * 2.0 + 1.0E-9; angle += 0.01) {
                        double along = halfLength * signedPower(Math.cos(angle), 2.0 / major);
                        double across = halfWidth * signedPower(Math.sin(angle), 2.0 / major);
                        double z = center + along;
                        if (z < first || z > last) {
                            points.add(null);
                            continue;
                        }
                        points.add(upright ? new double[]{0.0, across, z} : new double[]{across, 0.0, z});
                    }
                }
            }
            case FISHNET -> {
                double spacing = options.get("spacing", 1.0);
                double depth = Math.min(1.0, Math.max(options.get("depth", 0.2), 2.0 * EzShapes.STRIP / blocks));
                int strands = Math.max(1, (int) Math.round(Math.PI * 2.0 / spacing));
                for (int direction = -1; direction <= 1; direction += 2) {
                    for (int strand = 0; strand < strands; strand++) {
                        points.add(null);
                        for (double z = first; z <= last; z += 0.01) {
                            points.add(radial(Math.PI * 2.0 * strand / strands + direction * z, 1.0 - depth * 0.5, z));
                        }
                    }
                }
            }
            case HONEYCOMB -> {
                double cell = options.get("cellsize", 0.25);
                double depth = Math.min(1.0, Math.max(options.get("depth", 0.2), 2.0 * EzShapes.STRIP / blocks));
                int columns = Math.max(3, (int) Math.round(Math.PI * 2.0 / (Math.sqrt(3.0) * cell)));
                double column = Math.PI * 2.0 / columns;
                double size = column / Math.sqrt(3.0);
                double row = size * 1.5;
                double middle = 1.0 - depth * 0.5;
                for (int rowIndex = 0; rowIndex * row <= total + size; rowIndex++) {
                    double offset = (rowIndex & 1) == 0 ? 0.0 : column * 0.5;
                    for (int columnIndex = 0; columnIndex < columns; columnIndex++) {
                        double centerA = columnIndex * column + offset;
                        double centerZ = rowIndex * row;
                        for (int vertex = 0; vertex < 6; vertex++) {
                            double from = Math.toRadians(30.0 + 60.0 * vertex);
                            double to = Math.toRadians(90.0 + 60.0 * vertex);
                            points.add(null);
                            for (double amount = 0.0; amount <= 1.0 + 1.0E-9; amount += 0.05) {
                                double a = centerA + size * (Math.cos(from) + (Math.cos(to) - Math.cos(from)) * amount);
                                double z = centerZ + size * (Math.sin(from) + (Math.sin(to) - Math.sin(from)) * amount);
                                points.add(z < first || z > last ? null : radial(a, middle, z));
                            }
                        }
                    }
                }
            }
            case NOODLES -> {
                EzShapes.Noodles noodles = (EzShapes.Noodles) shape;
                for (int strand = 0; strand < noodles.amount(); strand++) {
                    points.add(null);
                    for (double z = first; z <= last; z += 0.02) {
                        double[] center = noodles.center(strand, z);
                        points.add(new double[]{center[0], center[1], z});
                    }
                }
            }
            default -> throw new IllegalArgumentException(EzShapes.form(options).name());
        }
        return points;
    }

    private static double signedPower(double value, double exponent) {
        return Math.signum(value) * Math.pow(Math.abs(value), exponent);
    }

    /** Punto a {@code distance} radios del eje en el ángulo de las figuras (0 = arriba, crece hacia -x). */
    private static double[] radial(double angle, double distance, double z) {
        return new double[]{-Math.sin(angle) * distance, Math.cos(angle) * distance, z};
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

    private static double square(double value) {
        return value * value;
    }

    private static double reach(Set<BlockVector3> blocks, int x) {
        double reach = 0.0;
        for (BlockVector3 block : blocks) {
            if (block.x() == x) {
                reach = Math.max(reach, Math.hypot(block.y() + 0.5, block.z() + 0.5));
            }
        }
        return reach;
    }

    static ShapeParameters parameters(String form) {
        return EzSpline.parse(form, null).parameters();
    }

    private static ShapeParameters with(ShapeParameters parameters, Map<String, Double> extra) {
        Map<String, Double> options = new java.util.HashMap<>(parameters.options().values());
        options.putAll(extra);
        return parameters.withOptions(new com.komainu.moxia.shape.ShapeOptions(options,
                parameters.options().formula()));
    }

    static SampledPath straight(int length) {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(length, 0, 0)), false);
    }

    static SampledPath oblique() {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(53, 23, 37)), false);
    }

    static SampledPath curved() {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(30, 12, 8),
                BlockVector3.at(52, 20, 40), BlockVector3.at(60, 4, 75)), false);
    }
}
