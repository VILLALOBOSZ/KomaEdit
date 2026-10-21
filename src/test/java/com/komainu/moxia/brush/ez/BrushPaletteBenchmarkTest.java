package com.komainu.moxia.brush.ez;

import com.komainu.moxia.gradient.PaletteSpec;
import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.persistence.NamedDefinitions;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tiempos del pincel con distintas paletas, fuera del juego: lo que se rehace en cada clic sobre una esfera de radio
 * {@value #RADIUS} (leer la paleta o la máscara, el ruido y recorrer la esfera) y lo que cuesta configurarlo. Sin
 * Minecraft no se miden la captura del terreno ni la colocación, que no dependen de la paleta.
 *
 * <p>Solo corre con {@code KOMAEDIT_BRUSH_BENCHMARK=<archivo>}: imprime la tabla y la escribe ahí. Tarda unos
 * segundos y no aporta en cada build. Los márgenes son holgados: la prueba solo falla si algo se dispara.</p>
 */
class BrushPaletteBenchmarkTest {
    private static final int RADIUS = 10;
    private static final int WARMUP = 300;
    private static final int RUNS = 300;
    /** Un clic no puede tardar más que esto en la parte que depende de la paleta (holgado). */
    private static final double MAX_CLICK_MILLIS = 60.0;

    @TempDir
    Path directory;

    @Test
    @EnabledIfEnvironmentVariable(named = "KOMAEDIT_BRUSH_BENCHMARK", matches = ".+")
    void brushPalettesStayFast() throws IOException {
        NamedDefinitions saved = new NamedDefinitions(directory.resolve("palettes.json"), "paleta");
        saved.put("mia", "stone;andesite;cobblestone;mossy_cobblestone;tuff", false);
        saved.put("anidada", "#mia,##Grayscale(1:4)", false);
        Function<String, String> resolver = name -> saved.get(name).orElse(null);

        Map<String, String> palettes = new LinkedHashMap<>();
        palettes.put("Un bloque", "stone");
        palettes.put("Lista corta", "stone,andesite,cobblestone");
        palettes.put("##Magma (de serie)", "##Magma");
        palettes.put("##Grayscale(1:8)", "##Grayscale(1:8)");
        palettes.put("Guardada #mia", "#mia");
        palettes.put("Guardada anidada", "#anidada");

        List<String> rows = new ArrayList<>();
        rows.add(String.format(Locale.ROOT, "| %-26s | %11s | %11s | %11s | %11s | %11s |", "Paleta", "leer paleta",
                "desplazar", "degradado", "leer máscara", "con máscara"));
        double worst = 0.0;
        for (Map.Entry<String, String> palette : palettes.entrySet()) {
            String text = palette.getValue();
            List<String> entries = PaletteSpec.parse(text, resolver);
            ArrayWorld world = ArrayWorld.of(RADIUS + 2, entries);
            BlockVector3 center = BlockVector3.at(0, 0, 0);
            double configure = time(() -> PaletteSpec.parse(text, resolver).size());
            PaletteShift shift = PaletteShift.of(entries, false);
            double shiftClick = time(() -> shift.paint(world, 0, 0, 0, RADIUS, 1, (x, y, z, target) -> { }));
            GradientPainter.Settings settings = new GradientPainter.Settings(entries.size(),
                    GradientInterpolation.LINEAR, 0.5, BrushNoise.white(7L), false, false);
            GradientPainter.Ramp ramp = GradientPainter.plane(BlockVector3.at(-RADIUS, 0, 0),
                    BlockVector3.at(RADIUS, 0, 0));
            double gradientClick = time(() -> GradientPainter.paintSphere(world, center, RADIUS, ramp, settings,
                    (x, y, z, index) -> { }));
            // La máscara de los pinceles que la usan (pétalos, relieve, modelado) con los bloques de la paleta: leerla y
            // pintar con ella la esfera (pétalos sobre esos bloques).
            String maskText = String.join(",", entries);
            double maskRead = time(() -> BlockFilter.parse(maskText));
            BlockFilter mask = BlockFilter.parse(maskText);
            PetalPainter.Settings petals = new PetalPainter.Settings("minecraft:pink_petals", 4, 0.5,
                    BrushNoise.white(3L), mask, 3L);
            double maskClick = time(() -> PetalPainter.paint(world, center, RADIUS, petals, false,
                    (x, y, z, amount, facing) -> { }));
            worst = Math.max(worst, Math.max(shiftClick, Math.max(gradientClick, maskRead + maskClick)));
            rows.add(String.format(Locale.ROOT, "| %-26s | %8.3f ms | %8.3f ms | %8.3f ms | %8.3f ms | %8.3f ms |",
                    palette.getKey() + " (" + entries.size() + ")", configure, shiftClick, gradientClick, maskRead,
                    maskClick));
        }

        rows.add("");
        rows.add(String.format(Locale.ROOT, "| %-26s | %11s |", "Ruido del degradado", "por clic"));
        List<String> magma = PaletteSpec.parse("##Magma");
        ArrayWorld world = ArrayWorld.of(RADIUS + 2, magma);
        Map<String, Supplier<BrushNoise>> noises = new LinkedHashMap<>();
        noises.put("Sin ruido (bandas)", () -> null);
        noises.put("Blanco", () -> BrushNoise.white(5L));
        noises.put("Perlin()", () -> BrushNoise.of(NoiseSpec.parse("Perlin()"), 1.0, 5L));
        noises.put("ridged (5 octavas)", () -> BrushNoise.of(NoiseSpec.parse("ridged"), 1.0, 5L));
        noises.put("pebbles (celdas)", () -> BrushNoise.of(NoiseSpec.parse("pebbles"), 1.0, 5L));
        for (Map.Entry<String, Supplier<BrushNoise>> noise : noises.entrySet()) {
            BrushNoise field = noise.getValue().get();
            GradientPainter.Settings settings = new GradientPainter.Settings(magma.size(),
                    field == null ? GradientInterpolation.NONE : GradientInterpolation.TAPERED, 0.5,
                    field == null ? BrushNoise.white(1L) : field, false, false);
            GradientPainter.Ramp ramp = GradientPainter.plane(BlockVector3.at(-RADIUS, 0, 0),
                    BlockVector3.at(RADIUS, 0, 0));
            double click = time(() -> GradientPainter.paintSphere(world, BlockVector3.at(0, 0, 0), RADIUS, ramp,
                    settings, (x, y, z, index) -> { }));
            worst = Math.max(worst, click);
            rows.add(String.format(Locale.ROOT, "| %-26s | %8.3f ms |", noise.getKey(), click));
        }

        String table = String.join("\n", rows);
        System.out.println(table);
        String target = System.getenv("KOMAEDIT_BRUSH_BENCHMARK");
        if (target != null && !target.isBlank()) {
            Files.writeString(Path.of(target), table + "\n");
        }
        assertTrue(worst < MAX_CLICK_MILLIS, "un clic tarda " + worst + " ms\n" + table);
    }

    /** Media en milisegundos de {@value #RUNS} pasadas tras {@value #WARMUP} de calentamiento. */
    private static double time(Supplier<Object> work) {
        Object sink = null;
        for (int run = 0; run < WARMUP; run++) {
            sink = work.get();
        }
        long start = System.nanoTime();
        for (int run = 0; run < RUNS; run++) {
            sink = work.get();
        }
        long elapsed = System.nanoTime() - start;
        if (sink == null) {
            throw new IllegalStateException();
        }
        return elapsed / 1_000_000.0 / RUNS;
    }

    /**
     * Terreno en arrays, como la foto del juego: media esfera de bloques de la paleta (con alguna losa) y aire encima.
     * Los ids salen como los da WorldEdit ({@code minecraft:stone}).
     */
    static final class ArrayWorld implements BrushWorld {
        private final int size;
        private final int side;
        private final String[] ids;
        private final Cell[] cells;

        private ArrayWorld(int size) {
            this.size = size;
            this.side = size * 2 + 1;
            this.ids = new String[side * side * side];
            this.cells = new Cell[ids.length];
        }

        static ArrayWorld of(int size, List<String> palette) {
            ArrayWorld world = new ArrayWorld(size);
            for (int y = -size; y <= size; y++) {
                for (int z = -size; z <= size; z++) {
                    for (int x = -size; x <= size; x++) {
                        int index = world.index(x, y, z);
                        if (y > 0) {
                            world.ids[index] = "minecraft:air";
                            world.cells[index] = Cell.AIR;
                            continue;
                        }
                        String id = PaletteShift.normalize(palette.get(Math.floorMod(x * 7 + y * 3 + z, palette.size())));
                        boolean slab = (x + z) % 11 == 0;
                        world.ids[index] = slab ? id.replace("minecraft:", "minecraft:x_") + "_slab" : id;
                        world.cells[index] = slab ? Cell.SHAPED : Cell.FULL;
                    }
                }
            }
            return world;
        }

        private int index(int x, int y, int z) {
            return ((y + size) * side + (z + size)) * side + (x + size);
        }

        @Override
        public String blockId(int x, int y, int z) {
            return contains(x, y, z) ? ids[index(x, y, z)] : "minecraft:air";
        }

        @Override
        public Cell cell(int x, int y, int z) {
            return contains(x, y, z) ? cells[index(x, y, z)] : Cell.AIR;
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return Math.abs(x) <= size && Math.abs(y) <= size && Math.abs(z) <= size;
        }
    }
}
