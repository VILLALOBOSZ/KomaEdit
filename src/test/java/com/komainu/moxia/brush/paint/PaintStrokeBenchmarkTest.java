package com.komainu.moxia.brush.paint;

import com.komainu.moxia.brush.ez.BrushNoise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tiempos de un trazo largo del pincel de pintura fuera del juego: lo que hace el worker en cada tanda (24 toques
 * como mucho) sobre un terreno de 161 × 161 con colina y escarpe, con cada modo y dos radios. Sin Minecraft no se
 * miden la foto ni la escritura, que dependen del servidor.
 *
 * <p>El trazo largo solo corre con {@code KOMAEDIT_PAINT_BENCHMARK=<archivo>}: imprime la tabla y la escribe ahí.
 * Tarda unos segundos y no aporta en cada build. Los márgenes son holgados: solo falla si algo se dispara.</p>
 */
class PaintStrokeBenchmarkTest {
    /** Un toque no puede costar más que esto en el worker (muy holgado: suele ir por debajo de 1 ms). */
    private static final double MAX_DAB_MILLIS = 12.0;
    private static final int WARMUP = 2;
    private static final int RUNS = 5;

    @Test
    @EnabledIfEnvironmentVariable(named = "KOMAEDIT_PAINT_BENCHMARK", matches = ".+")
    void aLongStrokeStaysFast() throws IOException {
        List<String> rows = new ArrayList<>();
        rows.add(String.format(Locale.ROOT, "| %-10s | %5s | %6s | %8s | %10s | %10s | %9s |", "Modo", "Radio",
                "Toques", "Tandas", "ms trazo", "ms/toque", "bloques"));
        double worst = 0.0;
        for (int radius : new int[]{8, 16}) {
            for (PaintGradient gradient : PaintGradient.values()) {
                double[] best = {Double.MAX_VALUE};
                int[] stats = new int[3];
                for (int run = 0; run < WARMUP + RUNS; run++) {
                    FakePaintWorld world = FakePaintWorld.terrain(80, PaintRenderDump::hillHeight, "grass_block",
                            "stone", PaintPainterTest.PALETTE);
                    PaintStroke stroke = new PaintStroke(radius, PaintConfig.DEFAULT_SPACING / 100.0, 99L, false, 0);
                    List<PaintPainter.Dab> dabs = new ArrayList<>();
                    for (int step = 0; step <= 160; step++) {
                        double t = step / 160.0;
                        int x = (int) Math.round(-70 + 140 * t);
                        int z = (int) Math.round(Math.sin(t * Math.PI * 3.0) * 40.0);
                        dabs.addAll(stroke.add(x + 0.5, world.top(x, z) + 0.5, z + 0.5));
                    }
                    PaintPainter.Settings settings = settings(gradient, radius);
                    PaintPainter.Memory memory = new PaintPainter.Memory(stroke.salt());
                    int changed = 0;
                    int batches = 0;
                    long start = System.nanoTime();
                    for (int from = 0; from < dabs.size(); from += 24) {
                        PaintPainter.Result result = PaintPainter.paint(world, dabs.subList(from,
                                Math.min(dabs.size(), from + 24)), settings, memory, world::apply, () -> { });
                        settings = settings.withHeights(result.heightLow(), result.heightHigh());
                        changed += result.changed();
                        batches++;
                    }
                    double millis = (System.nanoTime() - start) / 1.0E6;
                    if (run >= WARMUP && millis < best[0]) {
                        best[0] = millis;
                        stats[0] = dabs.size();
                        stats[1] = batches;
                        stats[2] = changed;
                    }
                }
                double perDab = best[0] / stats[0];
                worst = Math.max(worst, perDab);
                rows.add(String.format(Locale.ROOT, "| %-10s | %5d | %6d | %8d | %10.1f | %10.3f | %9d |",
                        gradient.id(), radius, stats[0], stats[1], best[0], perDab, stats[2]));
            }
        }
        rows.add("");
        rows.add(String.format(Locale.ROOT, "Peor ms por toque: %.3f (límite %.1f)", worst, MAX_DAB_MILLIS));
        rows.forEach(System.out::println);
        String file = System.getenv("KOMAEDIT_PAINT_BENCHMARK");
        if (file != null && !file.isBlank()) {
            Files.writeString(Path.of(file), String.join(System.lineSeparator(), rows) + System.lineSeparator());
        }
        assertTrue(worst < MAX_DAB_MILLIS, "un toque cuesta " + worst + " ms");
    }

    @Test
    void theFootprintIsPreparedOnceNotOnEveryClick() {
        FootprintLibrary library = FootprintLibrary.builtIn();
        long start = System.nanoTime();
        PaintFootprint first = library.get("montana");
        double prepare = (System.nanoTime() - start) / 1.0E6;
        start = System.nanoTime();
        for (int click = 0; click < 1_000; click++) {
            assertSame(first, library.get("montana"));
        }
        double again = (System.nanoTime() - start) / 1.0E6 / 1_000;
        System.out.printf(Locale.ROOT, "Huella montana: %.2f ms la primera vez, %.4f ms después%n", prepare, again);
        assertTrue(again < 0.5, "pedir otra vez la huella ya medida no cuesta: " + again + " ms");
    }

    private static PaintPainter.Settings settings(PaintGradient gradient, int radius) {
        BrushNoise noise = PaintConfig.contrast(BrushNoise.of(PaintConfig.defaultNoise(PaintConfig.DEFAULT_NOISE_SIZE),
                1.0, 5L));
        BrushNoise jitter = PaintConfig.contrast(BrushNoise.of(PaintConfig.jitterNoise(radius), 1.0, 6L));
        return new PaintPainter.Settings(PaintPainterTest.PALETTE.size(), gradient, PaintFootprint.circle(),
                PaintConfig.DEFAULT_STRENGTH / 100.0, PaintConfig.DEFAULT_HARDNESS / 100.0,
                PaintConfig.DEFAULT_JITTER / 100.0, 1, true, true, Math.clamp(radius + 4, 8, 28), Double.NaN,
                Double.NaN, PaintConfig.DEFAULT_SLOPE_LOW, PaintConfig.DEFAULT_SLOPE_HIGH, noise, jitter, false);
    }
}
