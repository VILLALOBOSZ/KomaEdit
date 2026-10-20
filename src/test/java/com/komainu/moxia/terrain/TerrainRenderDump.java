package com.komainu.moxia.terrain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Renders de antes y después de cada herramienta de terreno para revisarlas a ojo. Solo se ejecuta con
 * {@code KOMAEDIT_RENDER_DIR} definido; en el CI no hace nada.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_RENDER_DIR", matches = ".+")
class TerrainRenderDump {
    private static final Runnable NO_CANCEL = FakeTerrain.NO_CANCEL;

    @Test
    void dumpEverything() throws IOException {
        Path directory = Path.of(System.getenv("KOMAEDIT_RENDER_DIR"));
        Files.createDirectories(directory);
        stampSheet(directory);
        stampBrush(directory);
        ramps(directory);
        rivers(directory);
        erosion(directory);
    }

    private static void stampSheet(Path directory) throws IOException {
        List<String> names = new ArrayList<>(StampLibrary.descriptions().keySet());
        int thumb = 128;
        int columns = 5;
        int rows = (names.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(columns * (thumb + 8) + 8, rows * (thumb + 26) + 8,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(0x20242A));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        for (int index = 0; index < names.size(); index++) {
            HeightStamp stamp = StampLibrary.render(names.get(index), thumb);
            int left = 8 + (index % columns) * (thumb + 8);
            int top = 8 + (index / columns) * (thumb + 26);
            for (int row = 0; row < thumb; row++) {
                for (int column = 0; column < thumb; column++) {
                    int value = (int) Math.round(stamp.pixel(column, row) * 255.0);
                    sheet.setRGB(left + column, top + row, new Color(value, value, value).getRGB());
                }
            }
            graphics.setColor(Color.WHITE);
            graphics.drawString(names.get(index) + (stamp.neutral() > 0 ? " (neutro " + stamp.neutral() + ")" : ""),
                    left, top + thumb + 16);
        }
        graphics.dispose();
        TerrainRender.save(sheet, directory.resolve("sellos_de_serie.png"));
    }

    private static void stampBrush(Path directory) throws IOException {
        FakeTerrain terrain = TerrainScenes.hills(160, 110, 4L);
        before(directory, "pincel_relieve", terrain, after -> {
            SurfacePalette palette = SurfacePalette.natural();
            stamp(after, "montana", StampBrush.Mode.RAISE, 34, 60, 0.0, 40, 40, palette);
            stamp(after, "volcan", StampBrush.Mode.RAISE, 30, 45, 0.0, 118, 44, palette);
            stamp(after, "crater", StampBrush.Mode.RAISE, 24, 14, 0.0, 42, 118, palette);
            stamp(after, "duna", StampBrush.Mode.RAISE, 22, 12, Math.toRadians(45), 118, 118, palette);
            stamp(after, "mesa", StampBrush.Mode.RAISE, 18, 18, 0.0, 80, 82, palette);
        });
        FakeTerrain desert = TerrainScenes.flat(140, 70, 30);
        before(directory, "pincel_desierto", desert, SurfacePalette.parse("desierto"), after -> {
            SurfacePalette palette = SurfacePalette.parse("mesa");
            stamp(after, "mesa", StampBrush.Mode.RAISE, 30, 26, 0.0, 40, 45, palette);
            stamp(after, "acantilado", StampBrush.Mode.RAISE, 28, 22, Math.toRadians(200), 100, 40, palette);
            stamp(after, "dunas", StampBrush.Mode.RAISE, 30, 5, Math.toRadians(20), 50, 105, palette);
            stamp(after, "aguja", StampBrush.Mode.RAISE, 14, 30, 0.0, 105, 100, palette);
        });
    }

    private static void stamp(FakeTerrain terrain, String name, StampBrush.Mode mode, double radius, double height,
                              double angle, int x, int z, SurfacePalette palette) {
        int top = terrain.top(x, z);
        TerrainJobs.stamp(terrain, new StampBrush.Settings(StampLibrary.get(name), mode, radius, height, 1.0, angle,
                top), x, z, palette, terrain, NO_CANCEL);
    }

    private static void ramps(Path directory) throws IOException {
        for (RampPlanner.Section section : RampPlanner.Section.values()) {
            FakeTerrain terrain = TerrainScenes.hills(120, 80, 9L);
            before(directory, "rampa_" + section.id(), terrain, after -> {
                int[] start = {14, after.top(14, 20), 20};
                int[] end = {100, start[1] + 18, 96};
                TerrainJobs.ramp(after, start, end, new RampPlanner.Settings(section, 3.0, 7.0, 1.0, false),
                        SurfacePalette.natural(), after, NO_CANCEL);
            });
        }
    }

    private static void rivers(Path directory) throws IOException {
        FakeTerrain terrain = TerrainScenes.slope(160, 90, 21L);
        double[][] controls = {{6, 0, 30}, {50, 0, 60}, {80, 0, 40}, {120, 0, 100}, {154, 0, 120}};
        before(directory, "rio", terrain, after -> TerrainJobs.river(after, TerrainScenes.curve(controls),
                RiverPlanner.preset("rio"), SurfacePalette.natural(), after, NO_CANCEL));
        FakeTerrain plateau = TerrainScenes.plateau(140, 90, 5L);
        double[][] canyon = {{4, 0, 30}, {50, 0, 60}, {90, 0, 50}, {136, 0, 110}};
        RiverPlanner.Settings deep = RiverPlanner.preset("canon");
        RiverPlanner.Settings wide = new RiverPlanner.Settings(3.0, 24.0, 7.0, 2.5, 12.0, deep.fluid(), deep.level(),
                false, false, deep.smoothing());
        before(directory, "canon", plateau, SurfacePalette.parse("mesa"), after -> TerrainJobs.river(after,
                TerrainScenes.curve(canyon), wide, SurfacePalette.parse("mesa"), after, NO_CANCEL));
        FakeTerrain volcanic = TerrainScenes.slope(120, 80, 33L);
        double[][] lava = {{4, 0, 60}, {60, 0, 40}, {116, 0, 70}};
        before(directory, "rio_lava", volcanic, SurfacePalette.parse("volcanico"), after -> {
            RiverPlanner.Settings base = RiverPlanner.preset("rio");
            RiverPlanner.Settings settings = new RiverPlanner.Settings(base.halfWidth(), base.depth(), base.bank(),
                    base.wallSlope(), base.transition(), RiverPlanner.Fluid.LAVA, base.level(), false, false,
                    base.smoothing());
            TerrainJobs.river(after, TerrainScenes.curve(lava), settings, SurfacePalette.parse("volcanico"), after,
                    NO_CANCEL);
        });
    }

    private static void erosion(Path directory) throws IOException {
        for (TerrainJobs.Erosion mode : TerrainJobs.Erosion.values()) {
            FakeTerrain terrain = TerrainScenes.mountain(160, 120, 7L);
            double iterations = mode == TerrainJobs.Erosion.THERMAL ? 40 : 3;
            before(directory, "erosion_" + mode.id(), terrain, after -> TerrainJobs.erode(after, mode, iterations,
                    1.0, 40.0, 1234L, SurfacePalette.natural(), after, NO_CANCEL));
        }
    }

    private static void before(Path directory, String name, FakeTerrain terrain, Consumer<FakeTerrain> operation)
            throws IOException {
        before(directory, name, terrain, SurfacePalette.natural(), operation);
    }

    private static void before(Path directory, String name, FakeTerrain terrain, SurfacePalette ground,
                               Consumer<FakeTerrain> operation) throws IOException {
        // Antes se repinta con la paleta para que el color sea comparable.
        TerrainJobs.paint(terrain, ground, terrain, NO_CANCEL);
        FakeTerrain after = terrain.copy();
        long start = System.nanoTime();
        operation.accept(after);
        long millis = (System.nanoTime() - start) / 1_000_000;
        int scale = terrain.sizeX() <= 128 ? 4 : 3;
        BufferedImage maps = TerrainRender.row(List.of(TerrainRender.heightmap(terrain, scale),
                TerrainRender.heightmap(after, scale)), List.of(name + " · antes", name + " · después (" + millis
                + " ms)"));
        TerrainRender.save(maps, directory.resolve(name + "_mapa.png"));
        BufferedImage iso = TerrainRender.row(List.of(TerrainRender.isometric(terrain, 6),
                TerrainRender.isometric(after, 6)), List.of(name + " · antes", name + " · después"));
        TerrainRender.save(iso, directory.resolve(name + "_iso.png"));
    }
}
