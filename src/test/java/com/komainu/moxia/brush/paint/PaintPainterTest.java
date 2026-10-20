package com.komainu.moxia.brush.paint;

import com.komainu.moxia.brush.ez.BrushNoise;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cada modo de degradado, la acumulación por pasadas, la trama, las máscaras y la semilla del pincel de pintura. */
class PaintPainterTest {
    static final List<String> PALETTE = List.of("grass_block", "moss_block", "coarse_dirt", "gravel", "andesite",
            "stone");
    private static final Runnable NO_CANCEL = () -> { };

    static PaintPainter.Settings settings(PaintGradient gradient, double strength, double hardness) {
        BrushNoise noise = PaintConfig.contrast(BrushNoise.of(PaintConfig.defaultNoise(12), 1.0, 7L));
        return new PaintPainter.Settings(PALETTE.size(), gradient, PaintFootprint.circle(), strength, hardness, 0.0,
                1, true, true, 16, Double.NaN, Double.NaN, 15.0, 50.0, noise, null, false);
    }

    static PaintPainter.Settings with(PaintPainter.Settings base, PaintFootprint footprint, BrushNoise noise) {
        return new PaintPainter.Settings(base.paletteSize(), base.gradient(), footprint, base.strength(),
                base.hardness(), base.jitter(), base.layers(), base.surfaceOnly(), base.shaped(), base.vertical(),
                base.heightLow(), base.heightHigh(), base.slopeLow(), base.slopeHigh(), noise, base.jitterNoise(),
                base.invert());
    }

    static PaintPainter.Settings layers(PaintPainter.Settings base, int layers, boolean surfaceOnly, boolean shaped) {
        return new PaintPainter.Settings(base.paletteSize(), base.gradient(), base.footprint(), base.strength(),
                base.hardness(), base.jitter(), layers, surfaceOnly, shaped, base.vertical(), base.heightLow(),
                base.heightHigh(), base.slopeLow(), base.slopeHigh(), base.noise(), base.jitterNoise(),
                base.invert());
    }

    static PaintPainter.Dab dab(FakePaintWorld world, int x, int z, double radius, double angle) {
        return new PaintPainter.Dab(x + 0.5, world.top(x, z) + 0.5, z + 0.5, radius, angle);
    }

    /** Un trazo con esos toques; pinta y aplica: devuelve lo escrito por posición. */
    static Map<Long, Integer> paint(FakePaintWorld world, List<PaintPainter.Dab> dabs, PaintPainter.Settings settings) {
        return paint(world, dabs, settings, 1L);
    }

    static Map<Long, Integer> paint(FakePaintWorld world, List<PaintPainter.Dab> dabs, PaintPainter.Settings settings,
                                    long salt) {
        Map<Long, Integer> writes = new LinkedHashMap<>();
        PaintPainter.paint(world, dabs, settings, salt,
                (x, y, z, index) -> writes.put(PaintPainter.pack(x, y, z), index), NO_CANCEL);
        writes.forEach((key, index) -> world.apply(PaintPainter.unpackX(key), PaintPainter.unpackY(key),
                PaintPainter.unpackZ(key), index));
        return writes;
    }

    /** Media del tono de la superficie en las columnas que cumplen {@code inside}. */
    static double meanTone(FakePaintWorld world, java.util.function.BiPredicate<Integer, Integer> inside) {
        double sum = 0.0;
        int count = 0;
        for (int z = world.minimumZ(); z <= world.maximumZ(); z++) {
            for (int x = world.minimumX(); x <= world.maximumX(); x++) {
                if (!inside.test(x, z)) {
                    continue;
                }
                int index = world.paletteIndex(x, world.top(x, z), z);
                if (index >= 0) {
                    sum += index;
                    count++;
                }
            }
        }
        assertTrue(count > 0, "ninguna columna dentro");
        return sum / count;
    }

    @Test
    void bySlopeTheFlatStaysGrassAndTheWallTurnsToStone() {
        FakePaintWorld world = FakePaintWorld.hill(32, PALETTE);
        paint(world, List.of(dab(world, 0, 0, 30, 0)), settings(PaintGradient.SLOPE, 1.0, 1.0));
        double flat = meanTone(world, (x, z) -> {
            double r = Math.sqrt(x * x + z * z);
            return r >= 26 && r < 30;
        });
        // El escarpe del este: de x 7 a 10, cerca de z 0.
        double wall = meanTone(world, (x, z) -> x >= 7 && x <= 10 && Math.abs(z) <= 3);
        assertTrue(flat < 0.6, "lo llano sigue de hierba: " + flat);
        assertTrue(wall > 3.8, "la pared sale de piedra: " + wall);
        double gentle = meanTone(world, (x, z) -> x < -6 && x > -14 && Math.abs(z) <= 3);
        assertTrue(gentle > flat && gentle < wall, "la cuesta suave queda en medio: " + gentle);
    }

    @Test
    void byHeightTheFootIsTheFirstToneAndTheTopTheLast() {
        FakePaintWorld world = FakePaintWorld.hill(32, PALETTE);
        PaintPainter.Result result = PaintPainter.paint(world, List.of(dab(world, 0, 0, 30, 0)),
                settings(PaintGradient.HEIGHT, 1.0, 1.0), 2L, world::apply, NO_CANCEL);
        assertTrue(Double.isFinite(result.heightLow()) && result.heightHigh() > result.heightLow() + 7.9,
                "la franja de alturas se mide y se devuelve para el resto del trazo");
        assertTrue(meanTone(world, (x, z) -> x * x + z * z < 9) > 4.0, "la cima, de piedra");
        assertTrue(meanTone(world, (x, z) -> {
            double r = Math.sqrt(x * x + z * z);
            return r >= 26 && r < 30;
        }) < 0.6, "el pie, de hierba");
    }

    @Test
    void theFixedHeightsOfTheFlagAreUsedAsTheyAre() {
        FakePaintWorld world = FakePaintWorld.flat(12, PALETTE);
        PaintPainter.Settings base = settings(PaintGradient.HEIGHT, 1.0, 1.0).withHeights(0, 20);
        PaintPainter.Result result = PaintPainter.paint(world, List.of(dab(world, 0, 0, 8, 0)), base, 3L,
                world::apply, NO_CANCEL);
        assertEquals(0.0, result.heightLow());
        assertEquals(20.0, result.heightHigh());
        // Y 10 en una franja de 0 a 20: la mitad de la paleta (tonos 2 y 3).
        double mean = meanTone(world, (x, z) -> x * x + z * z < 25);
        assertEquals(2.5, mean, 0.35);
    }

    @Test
    void byFootprintTheCenterIsTheFirstToneAndTheEdgeTheLast() {
        FakePaintWorld world = FakePaintWorld.flat(14, PALETTE);
        paint(world, List.of(dab(world, 0, 0, 12, 0)), settings(PaintGradient.INTENSITY, 1.0, 0.3));
        double center = meanTone(world, (x, z) -> x * x + z * z <= 4);
        double ring = meanTone(world, (x, z) -> {
            double r = Math.sqrt(x * x + z * z);
            return r >= 8.5 && r <= 9.6;
        });
        assertTrue(center < 0.5, "el centro, del primer tono: " + center);
        assertTrue(ring > 2.8, "hacia el borde, los últimos: " + ring);
    }

    @Test
    void byNoiseThePatchesFollowTheSeed() {
        PaintPainter.Settings base = settings(PaintGradient.NOISE, 1.0, 1.0);
        Map<Long, Integer> first = paint(FakePaintWorld.flat(20, PALETTE),
                List.of(new PaintPainter.Dab(0.5, 10.5, 0.5, 18, 0)), base);
        Map<Long, Integer> again = paint(FakePaintWorld.flat(20, PALETTE),
                List.of(new PaintPainter.Dab(0.5, 10.5, 0.5, 18, 0)), base);
        PaintPainter.Settings other = with(base, base.footprint(),
                PaintConfig.contrast(BrushNoise.of(PaintConfig.defaultNoise(12), 1.0, 8L)));
        Map<Long, Integer> reseeded = paint(FakePaintWorld.flat(20, PALETTE),
                List.of(new PaintPainter.Dab(0.5, 10.5, 0.5, 18, 0)), other);
        assertEquals(first, again, "la misma semilla pinta lo mismo");
        assertNotEquals(first, reseeded, "otra semilla, otras manchas");
        assertTrue(first.values().stream().distinct().count() >= 4, "las manchas usan casi toda la paleta");
    }

    @Test
    void passesBuildUpTowardsTheToneWithoutJumpingFromFirstToLast() {
        FakePaintWorld world = FakePaintWorld.flat(12, PALETTE);
        // Un campo que pide el último tono en todas partes: solo cuenta la fuerza.
        PaintPainter.Settings last = with(settings(PaintGradient.NOISE, 0.3, 1.0), PaintFootprint.circle(),
                (x, y, z) -> 1.0);
        java.util.function.BiPredicate<Integer, Integer> inside = (x, z) -> x * x + z * z <= 36;
        double previous = 0.0;
        for (int pass = 0; pass < 12; pass++) {
            paint(world, List.of(dab(world, 0, 0, 10, 0)), last, 100L + pass);
            double mean = meanTone(world, inside);
            if (pass == 0) {
                assertEquals(1.5, mean, 0.25, "la primera pasada empuja un 30 %");
                for (int z = -6; z <= 6; z++) {
                    for (int x = -6; x <= 6; x++) {
                        if (inside.test(x, z)) {
                            assertTrue(world.paletteIndex(x, 10, z) <= 2, "nada salta al último tono de golpe");
                        }
                    }
                }
            }
            assertTrue(mean > previous, "cada pasada acerca un poco más: " + mean + " tras " + previous);
            previous = mean;
        }
        assertTrue(previous > 4.6, "al final se llega al tono: " + previous);
    }

    @Test
    void dabsThatOverlapInOneStrokeDoNotAddUp() {
        FakePaintWorld dragged = FakePaintWorld.flat(24, PALETTE);
        PaintPainter.Settings last = with(settings(PaintGradient.NOISE, 0.3, 1.0), PaintFootprint.circle(),
                (x, y, z) -> 1.0);
        // Ida y vuelta muchas veces por el mismo sitio, toques cada bloque: sigue siendo un 30 %.
        PaintStroke stroke = new PaintStroke(8, 0.1, 5L, false, 0);
        List<PaintPainter.Dab> dabs = new ArrayList<>();
        for (int lap = 0; lap < 4; lap++) {
            dabs.addAll(stroke.add(-12.5, 10.5, 0.5));
            dabs.addAll(stroke.add(12.5, 10.5, 0.5));
        }
        assertTrue(dabs.size() > 80, "muchos toques encima: " + dabs.size());
        PaintPainter.Memory memory = new PaintPainter.Memory(stroke.salt());
        // En tandas, como en el juego: la memoria pasa de una a otra.
        for (int start = 0; start < dabs.size(); start += 24) {
            PaintPainter.paint(dragged, dabs.subList(start, Math.min(dabs.size(), start + 24)), last, memory,
                    dragged::apply, NO_CANCEL);
        }
        double mean = meanTone(dragged, (x, z) -> Math.abs(z) <= 3 && Math.abs(x) <= 10);
        assertEquals(1.5, mean, 0.3, "un trazo pinta con su fuerza aunque pase muchas veces: " + mean);
    }

    @Test
    void blocksOutsideThePaletteAreCoveredLittleByLittle() {
        FakePaintWorld world = FakePaintWorld.terrain(14, (x, z) -> 10, "sand", "sand", PALETTE);
        PaintPainter.Settings half = with(settings(PaintGradient.NOISE, 0.4, 1.0), PaintFootprint.circle(),
                (x, y, z) -> 0.5);
        paint(world, List.of(dab(world, 0, 0, 12, 0)), half);
        int covered = 0;
        int total = 0;
        for (int z = -8; z <= 8; z++) {
            for (int x = -8; x <= 8; x++) {
                if (x * x + z * z <= 64) {
                    total++;
                    String block = world.block(x, 10, z);
                    if (!block.equals("sand")) {
                        covered++;
                        // El tono 2,5 se trama entre el 2 y el 3.
                        assertTrue(block.equals("coarse_dirt") || block.equals("gravel"), block);
                    }
                }
            }
        }
        double fraction = covered / (double) total;
        assertEquals(0.4, fraction, 0.1, "se cubre con la fuerza del toque: " + fraction);
    }

    @Test
    void theDitherMixesNeighbourTonesInsteadOfBands() {
        FakePaintWorld world = FakePaintWorld.flat(14, PALETTE);
        // Tono objetivo 2,3 en todas partes y fuerza total: 70 % del 2 y 30 % del 3, mezclados.
        PaintPainter.Settings fixed = with(settings(PaintGradient.NOISE, 1.0, 1.0), PaintFootprint.circle(),
                (x, y, z) -> 2.3 / 5.0);
        paint(world, List.of(dab(world, 0, 0, 12, 0)), fixed);
        Map<Integer, Integer> counts = new HashMap<>();
        for (int z = -8; z <= 8; z++) {
            for (int x = -8; x <= 8; x++) {
                if (x * x + z * z <= 64) {
                    counts.merge(world.paletteIndex(x, 10, z), 1, Integer::sum);
                }
            }
        }
        assertEquals(java.util.Set.of(2, 3), counts.keySet());
        double third = counts.get(3) / (double) (counts.get(2) + counts.get(3));
        assertEquals(0.3, third, 0.1);
    }

    @Test
    void onlyTheSurfaceUnlessMoreLayersOrTheWholeVolume() {
        PaintPainter.Settings base = with(settings(PaintGradient.NOISE, 1.0, 1.0), PaintFootprint.circle(),
                (x, y, z) -> 1.0);
        FakePaintWorld surface = FakePaintWorld.flat(10, PALETTE);
        Map<Long, Integer> top = paint(surface, List.of(dab(surface, 0, 0, 6, 0)), base);
        assertTrue(top.keySet().stream().allMatch(key -> PaintPainter.unpackY(key) == 10), "solo la capa de arriba");

        FakePaintWorld two = FakePaintWorld.flat(10, PALETTE);
        Map<Long, Integer> layers = paint(two, List.of(dab(two, 0, 0, 6, 0)), layers(base, 2, true, true));
        assertTrue(layers.keySet().stream().anyMatch(key -> PaintPainter.unpackY(key) == 9), "dos capas");
        assertTrue(layers.keySet().stream().allMatch(key -> PaintPainter.unpackY(key) >= 9));

        FakePaintWorld volume = FakePaintWorld.flat(10, PALETTE);
        Map<Long, Integer> all = paint(volume, List.of(dab(volume, 0, 0, 6, 0)), layers(base, 1, false, true));
        assertTrue(all.keySet().stream().anyMatch(key -> PaintPainter.unpackY(key) <= 4), "todo el volumen");
    }

    @Test
    void stairsAndSlabsArePaintedKeepingTheirShapeOnlyWhenAsked() {
        PaintPainter.Settings base = with(settings(PaintGradient.NOISE, 1.0, 1.0), PaintFootprint.circle(),
                (x, y, z) -> 0.0);
        FakePaintWorld with = FakePaintWorld.flat(8, PALETTE).set(0, 11, 0, "stone_stairs").set(1, 11, 0,
                "stone_slab");
        paint(with, List.of(dab(with, 0, 0, 5, 0)), base);
        assertEquals("grass_block_stairs", with.block(0, 11, 0), "la escalera sigue siendo escalera");
        assertEquals("grass_block_slab", with.block(1, 11, 0));

        FakePaintWorld without = FakePaintWorld.flat(8, PALETTE).set(0, 11, 0, "stone_stairs");
        paint(without, List.of(dab(without, 0, 0, 5, 0)), layers(base, 1, true, false));
        assertEquals("stone_stairs", without.block(0, 11, 0), "sin escaleras y losas no se tocan");
    }

    @Test
    void theMaskDecidesWhatIsPainted() {
        PaintPainter.Settings base = with(settings(PaintGradient.NOISE, 1.0, 1.0), PaintFootprint.circle(),
                (x, y, z) -> 1.0);
        FakePaintWorld world = FakePaintWorld.terrain(10, (x, z) -> 10, "grass_block", "dirt", PALETTE)
                .only(id -> !id.equals("moss_block"));
        world.set(2, 10, 2, "moss_block");
        paint(world, List.of(dab(world, 0, 0, 6, 0)), base);
        assertEquals("moss_block", world.block(2, 10, 2), "lo que la máscara no deja se queda");
        assertEquals("stone", world.block(0, 10, 0));
    }

    @Test
    void aStampFootprintTurnsWithTheDab() {
        PaintFootprint ridge = FootprintLibrary.builtIn().get("cordillera");
        PaintPainter.Settings base = with(settings(PaintGradient.NOISE, 1.0, 0.6), ridge, (x, y, z) -> 1.0);
        int[] straight = extent(paint(FakePaintWorld.flat(20, PALETTE),
                List.of(new PaintPainter.Dab(0.5, 10.5, 0.5, 16, 0)), base));
        int[] turned = extent(paint(FakePaintWorld.flat(20, PALETTE),
                List.of(new PaintPainter.Dab(0.5, 10.5, 0.5, 16, Math.PI / 2)), base));
        assertTrue(straight[0] != straight[1], "la cordillera es alargada");
        assertEquals(straight[0] > straight[1], turned[1] > turned[0], "girada 90°, cambia de eje");
    }

    private static int[] extent(Map<Long, Integer> writes) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long key : writes.keySet()) {
            minX = Math.min(minX, PaintPainter.unpackX(key));
            maxX = Math.max(maxX, PaintPainter.unpackX(key));
            minZ = Math.min(minZ, PaintPainter.unpackZ(key));
            maxZ = Math.max(maxZ, PaintPainter.unpackZ(key));
        }
        return new int[]{maxX - minX, maxZ - minZ};
    }

    @Test
    void aClickAboveTheGroundFindsTheSurfaceBelow() {
        PaintPainter.Settings base = with(settings(PaintGradient.NOISE, 1.0, 1.0), PaintFootprint.circle(),
                (x, y, z) -> 1.0);
        FakePaintWorld world = FakePaintWorld.flat(10, PALETTE);
        Map<Long, Integer> writes = paint(world, List.of(new PaintPainter.Dab(0.5, 24.5, 0.5, 5, 0)), base);
        assertFalse(writes.isEmpty(), "el toque baja hasta el suelo de su columna");
    }

    @Test
    void theLeftClickPaintsThePaletteBackwards() {
        PaintPainter.Settings base = settings(PaintGradient.INTENSITY, 1.0, 0.0);
        FakePaintWorld world = FakePaintWorld.terrain(14, (x, z) -> 10, "gravel", "dirt", PALETTE);
        paint(world, List.of(dab(world, 0, 0, 12, 0)), base.inverted(true));
        assertTrue(meanTone(world, (x, z) -> x * x + z * z <= 4) > 4.0, "el centro, del último tono");
    }

    @Test
    void aWholeStrokeAppliesItsDabsInOrderAndReportsEachBlockOnce() {
        FakePaintWorld world = FakePaintWorld.hill(32, PALETTE);
        PaintStroke stroke = new PaintStroke(6, 0.3, 77L, false, 0);
        List<PaintPainter.Dab> dabs = new ArrayList<>();
        for (int x = -20; x <= 20; x += 4) {
            dabs.addAll(stroke.add(x + 0.5, world.top(x, 0) + 0.5, 0.5));
        }
        assertTrue(dabs.size() > 20, "un toque cada 1,8 bloques: " + dabs.size());
        List<Long> seen = new ArrayList<>();
        PaintPainter.Result result = PaintPainter.paint(world, dabs, settings(PaintGradient.SLOPE, 0.5, 0.3),
                stroke.salt(), (x, y, z, index) -> seen.add(PaintPainter.pack(x, y, z)), NO_CANCEL);
        assertEquals(seen.size(), result.changed());
        assertEquals(seen.size(), seen.stream().distinct().count(), "cada bloque, una vez");
    }
}
