package com.komainu.moxia.brush.paint;

import com.komainu.moxia.brush.ez.BrushNoise;
import com.komainu.moxia.gradient.PaletteSpec;
import com.komainu.moxia.noise.NoiseEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders del pincel de pintura para revisarlo a ojo: una colina pintada con cada modo de degradado, las pasadas que
 * acumulan y un trazo largo con la semilla libre y bloqueada. Pinta como en el juego: trazos de puntos con su
 * espaciado, tandas de toques y cada tanda sobre lo pintado por la anterior. Solo con {@code KOMAEDIT_RENDER_DIR}.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_RENDER_DIR", matches = ".+")
class PaintRenderDump {
    private static final List<String> PALETTE = PaletteSpec.parse(PaintPalettes.blocks(PaintPalettes.DEFAULT));
    private static final int ISO = 520;

    @Test
    void dumpEverything() throws IOException {
        Path directory = Path.of(System.getenv("KOMAEDIT_RENDER_DIR")).resolve("paint");
        Files.createDirectories(directory);
        hillModes(directory);
        passes(directory);
        seeds(directory);
    }

    /** La colina de antes y pintada con cada modo, dos pasadas en zigzag (la de la huella, un trazo en S). */
    private static void hillModes(Path directory) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        FakePaintWorld before = hill(PALETTE);
        images.add(PaintRender.isometric(before, ISO));
        labels.add("antes");
        PaintRender.save(images.getLast(), directory.resolve("colina_antes.png"));
        for (PaintGradient gradient : PaintGradient.values()) {
            FakePaintWorld world = hill(PALETTE);
            PaintPainter.Settings settings = settings(gradient, PALETTE.size(), 0.6, 0.3, 2024L);
            if (gradient == PaintGradient.INTENSITY) {
                stroke(world, sCurve(world), 6, 0.3, 2024L, false, settings, 2);
            } else {
                stroke(world, zigzag(world), 10, 0.3, 2024L, false, settings, 2);
            }
            images.add(PaintRender.isometric(world, ISO));
            labels.add("por " + gradient.id());
            PaintRender.save(images.getLast(), directory.resolve("colina_" + gradient.id() + ".png"));
        }
        List<String> peaksPalette = PaletteSpec.parse(PaintPalettes.blocks("cumbres"));
        FakePaintWorld peaks = hill(peaksPalette);
        stroke(peaks, zigzag(peaks), 10, 0.3, 2024L, false, settings(PaintGradient.HEIGHT, peaksPalette.size(), 0.6,
                0.3, 2024L), 2);
        images.add(PaintRender.isometric(peaks, ISO));
        labels.add("cumbres por altura");
        PaintRender.save(images.getLast(), directory.resolve("colina_cumbres_altura.png"));
        PaintRender.save(PaintRender.sheet(images, labels, 3), directory.resolve("colina_modos.png"));
    }

    /** Pasadas sobre la misma ladera con fuerza 25 %: cada una empuja un poco más hacia la piedra. */
    private static void passes(Path directory) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        FakePaintWorld world = hill(PALETTE);
        PaintPainter.Settings settings = settings(PaintGradient.SLOPE, PALETTE.size(), 0.25, 0.3, 77L);
        int done = 0;
        for (int target : new int[]{1, 2, 4, 8}) {
            for (; done < target; done++) {
                stroke(world, sweep(world), 9, 0.3, 77L + done, false, settings, 1);
            }
            images.add(PaintRender.topDown(world, 5));
            labels.add(target + (target == 1 ? " pasada" : " pasadas"));
        }
        PaintRender.save(PaintRender.sheet(images, labels, 4), directory.resolve("pasadas.png"));
    }

    /** Un trazo largo con la huella del circo glaciar: con la semilla libre gira y cambia; bloqueada se repite. */
    private static void seeds(Path directory) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        PaintFootprint cirque = FootprintLibrary.builtIn().get("circo");
        for (double spacing : new double[]{1.3, 0.3}) {
            for (boolean locked : new boolean[]{false, true}) {
                FakePaintWorld world = FakePaintWorld.terrain(44, PaintRenderDump::rolling, "grass_block", "dirt",
                        PALETTE);
                // La paleta al revés (el clic izquierdo): la huella sale rellena de piedra y se lee su forma.
                PaintPainter.Settings settings = PaintPainterTest.with(settings(PaintGradient.INTENSITY,
                        PALETTE.size(), 0.9, 0.5, 31L), cirque, null).inverted(true);
                stroke(world, longStroke(world), 7, spacing, 31L, locked, settings, 1);
                images.add(PaintRender.topDown(world, 5));
                String label = (locked ? "semilla bloqueada" : "semilla libre") + " · espaciado "
                        + Math.round(spacing * 100) + " %";
                labels.add(label);
                PaintRender.save(images.getLast(), directory.resolve("trazo_" + (locked ? "bloqueada" : "libre")
                        + "_" + Math.round(spacing * 100) + ".png"));
            }
        }
        PaintRender.save(PaintRender.sheet(images, labels, 2), directory.resolve("trazo_semillas.png"));
    }

    // ── Pintar como el juego ─────────────────────────────────────────────────────────────────────────────

    static PaintPainter.Settings settings(PaintGradient gradient, int paletteSize, double strength, double hardness,
                                          long seed) {
        BrushNoise noise = PaintConfig.contrast(BrushNoise.of(PaintConfig.defaultNoise(PaintConfig.DEFAULT_NOISE_SIZE),
                1.0, seed));
        BrushNoise jitter = PaintConfig.contrast(BrushNoise.of(PaintConfig.jitterNoise(10), 1.0, seed ^ 0x4A49L));
        return new PaintPainter.Settings(paletteSize, gradient, PaintFootprint.circle(), strength, hardness,
                PaintConfig.DEFAULT_JITTER / 100.0, 1, true, true, 14, Double.NaN, Double.NaN,
                PaintConfig.DEFAULT_SLOPE_LOW, PaintConfig.DEFAULT_SLOPE_HIGH, noise, jitter, false);
    }

    /**
     * Uno o varios trazos por los puntos (los que el servidor mira cada tick), en tandas de hasta 24 toques: cada
     * tanda ve lo que pintó la anterior y la franja de alturas se mide en la primera.
     */
    static void stroke(FakePaintWorld world, List<double[]> points, double radius, double spacing, long seed,
                       boolean locked, PaintPainter.Settings settings, int repeats) {
        long counter = 0;
        PaintPainter.Settings current = settings;
        if (settings.gradient() == PaintGradient.HEIGHT && Double.isNaN(settings.heightLow())) {
            // Como el servidor: el mapa de alturas alrededor del primer punto, cada 4 bloques.
            double[] first = points.getFirst();
            int[] heights = new int[33 * 33];
            int count = 0;
            for (int dz = -64; dz <= 64; dz += 4) {
                for (int dx = -64; dx <= 64; dx += 4) {
                    int x = (int) Math.floor(first[0]) + dx;
                    int z = (int) Math.floor(first[2]) + dz;
                    if (x >= world.minimumX() && x <= world.maximumX() && z >= world.minimumZ()
                            && z <= world.maximumZ()) {
                        heights[count++] = world.top(x, z);
                    }
                }
            }
            double[] range = PaintPainter.heightRange(heights, count, first[1]);
            current = current.withHeights(range[0], range[1]);
        }
        for (int repeat = 0; repeat < repeats; repeat++) {
            PaintStroke stroke = new PaintStroke(radius, spacing, seed, locked, counter);
            List<PaintPainter.Dab> pending = new ArrayList<>();
            for (double[] point : points) {
                pending.addAll(stroke.add(point[0], point[1], point[2]));
            }
            counter = stroke.counter();
            PaintPainter.Memory memory = new PaintPainter.Memory(stroke.salt());
            for (int start = 0; start < pending.size(); start += 24) {
                PaintPainter.Result result = PaintPainter.paint(world, pending.subList(start,
                        Math.min(pending.size(), start + 24)), current, memory, world::apply, () -> { });
                current = current.withHeights(result.heightLow(), result.heightHigh());
            }
        }
    }

    private static List<double[]> zigzag(FakePaintWorld world) {
        List<double[]> points = new ArrayList<>();
        boolean forward = true;
        for (int z = -32; z <= 32; z += 7) {
            for (int step = 0; step <= 34; step++) {
                int x = forward ? -34 + step * 2 : 34 - step * 2;
                points.add(new double[]{x + 0.5, world.top(x, z) + 0.5, z + 0.5});
            }
            forward = !forward;
        }
        return points;
    }

    private static List<double[]> sweep(FakePaintWorld world) {
        List<double[]> points = new ArrayList<>();
        for (int z = -14; z <= 14; z += 7) {
            for (int x = -4; x <= 26; x += 2) {
                points.add(new double[]{x + 0.5, world.top(x, z) + 0.5, z + 0.5});
            }
        }
        return points;
    }

    private static List<double[]> sCurve(FakePaintWorld world) {
        List<double[]> points = new ArrayList<>();
        for (int step = 0; step <= 60; step++) {
            double t = step / 60.0;
            int x = (int) Math.round(-34 + 68 * t);
            int z = (int) Math.round(Math.sin(t * Math.PI * 2.0) * 18.0);
            points.add(new double[]{x + 0.5, world.top(x, z) + 0.5, z + 0.5});
        }
        return points;
    }

    private static List<double[]> longStroke(FakePaintWorld world) {
        List<double[]> points = new ArrayList<>();
        for (int step = 0; step <= 80; step++) {
            double t = step / 80.0;
            int x = (int) Math.round(-36 + 72 * t);
            int z = (int) Math.round(Math.sin(t * Math.PI * 1.5) * 16.0);
            points.add(new double[]{x + 0.5, world.top(x, z) + 0.5, z + 0.5});
        }
        return points;
    }

    /** Colina con una segunda loma, un escarpe al este y algo de rugosidad; tierra bajo la hierba y piedra abajo. */
    static FakePaintWorld hill(List<String> palette) {
        FakePaintWorld world = FakePaintWorld.terrain(44, PaintRenderDump::hillHeight, "grass_block", "stone",
                palette);
        for (int z = -44; z <= 44; z++) {
            for (int x = -44; x <= 44; x++) {
                int top = world.top(x, z);
                for (int y = top - 3; y < top; y++) {
                    world.set(x, y, z, "dirt");
                }
            }
        }
        return world;
    }

    static int hillHeight(int x, int z) {
        double r = Math.sqrt(x * x + z * z);
        double dome = 20.0 * Math.exp(-(r * r) / (2.0 * 13.0 * 13.0));
        double second = 9.0 * Math.exp(-((x + 22.0) * (x + 22.0) + (z - 20.0) * (z - 20.0)) / (2.0 * 7.0 * 7.0));
        double cliff = x > 8 ? -Math.min(14.0, (x - 8) * 2.5) * Math.exp(-(z * z) / (2.0 * 12.0 * 12.0)) : 0.0;
        double rough = 2.5 * NoiseEngine.fractalBrownianMotion2D(x * 0.07, z * 0.07, 3L, 4, 2.0, 0.5);
        return (int) Math.round(8.0 + Math.max(0.0, dome + second + cliff) + rough);
    }

    static int rolling(int x, int z) {
        return (int) Math.round(10.0 + 2.0 * NoiseEngine.fractalBrownianMotion2D(x * 0.05, z * 0.05, 9L, 3, 2.0,
                0.5));
    }
}
