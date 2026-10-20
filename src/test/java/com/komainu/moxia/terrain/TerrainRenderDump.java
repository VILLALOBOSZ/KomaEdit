package com.komainu.moxia.terrain;

import com.komainu.moxia.terrain.stamp.StampInfo;
import com.komainu.moxia.terrain.stamp.StampPlacement;
import com.komainu.moxia.terrain.stamp.StampSheet;
import com.komainu.moxia.terrain.stamp.StampVariation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

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
        stampScenes(directory);
        stampBlocks(directory);
        stampVariation(directory);
        stampModes(directory);
        ramps(directory);
        rivers(directory);
        erosion(directory);
    }

    /** Hoja de contacto de los 80 sellos, por categoría y con las miniaturas del Studio. */
    private static void stampSheet(Path directory) throws IOException {
        TerrainRender.save(StampSheet.render(120, 8), directory.resolve("sellos_de_serie.png"));
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

    /**
     * Un clic del pincel como en el juego: el sello con la paleta de su categoría, la base a la altura del suelo
     * donde se pulsa y la variación de serie de ese número de clic.
     */
    private static void click(FakeTerrain terrain, String name, StampBrush.Mode mode, double radius, double height,
                              double degrees, int x, int z, int click) {
        click(terrain, name, StampLibrary.info(name).palette(), mode, radius, height, degrees, x, z, click);
    }

    /** Igual, con otra paleta (la de la categoría se puede cambiar). */
    private static void click(FakeTerrain terrain, String name, String palette, StampBrush.Mode mode, double radius,
                              double height, double degrees, int x, int z, int click) {
        StampPlacement placement = StampVariation.DEFAULT.place(radius, Math.toRadians(degrees), false, 2024L, click);
        TerrainJobs.stamp(terrain, new StampBrush.Settings(StampLibrary.get(name), mode, placement, height, 1.0,
                terrain.top(x, z)), x, z, SurfacePalette.parse(palette), terrain, NO_CANCEL);
    }

    /** Tres paisajes hechos solo estampando sellos sobre un llano (y el mar, en la costa). */
    private static void stampScenes(Path directory) throws IOException {
        StampBrush.Mode max = StampBrush.Mode.MAX;
        StampBrush.Mode add = StampBrush.Mode.RAISE;
        // Cordillera con valle: la sierra al norte, un pico y una montaña delante, el valle y lomas al sur.
        FakeTerrain range = TerrainScenes.flat(220, 150, 30);
        scene(directory, "escena_cordillera", range, SurfacePalette.parse("pradera"), after -> {
            click(after, "cordillera", max, 32, 40, 90, 56, 52, 1);
            click(after, "cordillera", max, 32, 44, 90, 112, 44, 2);
            click(after, "cordillera", max, 30, 38, 95, 166, 54, 3);
            click(after, "macizo", max, 30, 34, 0, 196, 40, 4);
            click(after, "pico", max, 22, 30, 0, 28, 74, 5);
            click(after, "montana", max, 26, 30, 180, 120, 82, 6);
            click(after, "valle_fluvial", "pradera", add, 40, 9, 90, 90, 132, 7);
            click(after, "valle_fluvial", "pradera", add, 40, 9, 90, 158, 136, 8);
            click(after, "lomas", max, 30, 8, 0, 60, 180, 9);
            click(after, "lomas", max, 30, 7, 0, 150, 186, 10);
            click(after, "colina", max, 18, 5, 0, 200, 170, 11);
        });
        // Costa con acantilados: el mar a Y 26 sobre un fondo a Y 20; la tierra alta al norte cae a pico.
        FakeTerrain coast = TerrainScenes.flat(220, 120, 20);
        for (int z = 0; z < 220; z++) {
            for (int x = 0; x < 220; x++) {
                for (int y = 21; y <= 26; y++) {
                    coast.put(x, y, z, "minecraft:water");
                }
            }
        }
        scene(directory, "escena_costa", coast, SurfacePalette.parse("costa"), after -> {
            click(after, "meseta", "costa", max, 56, 22, 0, 60, 34, 1);
            click(after, "meseta", "costa", max, 56, 22, 0, 160, 30, 2);
            click(after, "acantilado", max, 36, 24, 0, 40, 80, 3);
            click(after, "acantilado", max, 36, 26, 10, 110, 76, 4);
            click(after, "ensenada", max, 34, 24, 0, 182, 84, 5);
            click(after, "promontorio", max, 30, 22, 0, 76, 110, 6);
            click(after, "farallones", max, 30, 22, 0, 140, 112, 7);
            click(after, "isla", max, 30, 20, 0, 54, 172, 8);
            click(after, "archipielago", max, 32, 15, 0, 156, 176, 9);
            click(after, "colina", add, 20, 6, 0, 120, 30, 10);
        });
        // Desierto con dunas y mesas: mesas y un cerro testigo al fondo, dunas delante.
        FakeTerrain desert = TerrainScenes.flat(220, 120, 30);
        scene(directory, "escena_desierto", desert, SurfacePalette.parse("desierto"), after -> {
            click(after, "mesa", max, 34, 26, 0, 56, 52, 1);
            click(after, "mesa_escalonada", max, 30, 30, 0, 150, 56, 2);
            click(after, "cerro_testigo", max, 20, 28, 0, 104, 34, 3);
            click(after, "mesa_partida", max, 28, 22, 30, 196, 112, 4);
            click(after, "canon_ciego", add, 26, 9, 160, 100, 96, 5);
            click(after, "dunas", max, 40, 10, 0, 64, 158, 6);
            click(after, "barjanes", max, 34, 9, 0, 140, 176, 7);
            click(after, "duna_estrella", max, 24, 12, 0, 104, 136, 8);
            click(after, "duna_lineal", max, 28, 10, 60, 190, 190, 9);
            click(after, "duna", max, 20, 8, 0, 24, 112, 10);
        });
    }

    private static void scene(Path directory, String name, FakeTerrain terrain, SurfacePalette ground,
                              Consumer<FakeTerrain> operation) throws IOException {
        TerrainJobs.paint(terrain, ground, terrain, NO_CANCEL);
        long start = System.nanoTime();
        operation.accept(terrain);
        long millis = (System.nanoTime() - start) / 1_000_000;
        TerrainRender.save(TerrainRender.row(List.of(TerrainRender.isometric(terrain, 5)),
                List.of(name.replace('_', ' ') + " · solo sellos (" + millis + " ms)")),
                directory.resolve(name + "_iso.png"));
        TerrainRender.save(TerrainRender.heightmap(terrain, 3), directory.resolve(name + "_mapa.png"));
    }

    /** Cada sello estampado en bloques, con su altura natural y la paleta de su categoría. */
    private static void stampBlocks(Path directory) throws IOException {
        List<StampInfo> all = StampLibrary.all();
        int columns = 8;
        List<BufferedImage> row = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<BufferedImage> rows = new ArrayList<>();
        for (int index = 0; index < all.size(); index++) {
            StampInfo info = all.get(index);
            boolean sinks = info.neutral() >= 0.5;
            boolean water = info.category() == com.komainu.moxia.terrain.stamp.StampCategory.ISLANDS;
            int ground = sinks ? 40 : 12;
            FakeTerrain terrain = TerrainScenes.flat(66, 64, water ? 6 : ground);
            if (water) {
                for (int z = 0; z < 66; z++) {
                    for (int x = 0; x < 66; x++) {
                        for (int y = 7; y <= 10; y++) {
                            terrain.put(x, y, z, "minecraft:water");
                        }
                    }
                }
            }
            SurfacePalette palette = SurfacePalette.parse(info.palette());
            // El suelo de alrededor con la paleta del sello, salvo donde en el juego se estampan sobre hierba (y un
            // volcán sobre basalto negro no se distingue).
            boolean grass = switch (info.category()) {
                case VOLCANOES, CRATERS, ROCKS, DETAIL -> true;
                default -> false;
            };
            TerrainJobs.paint(terrain, grass ? SurfacePalette.natural() : palette, terrain, NO_CANCEL);
            double height = info.naturalHeight(30, 96);
            TerrainJobs.stamp(terrain, new StampBrush.Settings(StampLibrary.get(info.name()), StampBrush.Mode.RAISE,
                    30, height, 1.0, 0.0, terrain.top(33, 33)), 33, 33, palette, terrain, NO_CANCEL);
            row.add(TerrainRender.isometric(terrain, 4));
            labels.add(info.name());
            if (row.size() == columns || index == all.size() - 1) {
                rows.add(TerrainRender.row(row, labels));
                row = new ArrayList<>();
                labels = new ArrayList<>();
            }
        }
        int width = rows.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
        int total = rows.stream().mapToInt(BufferedImage::getHeight).sum();
        BufferedImage sheet = new BufferedImage(width, total, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new java.awt.Color(0x20242A));
        graphics.fillRect(0, 0, width, total);
        int y = 0;
        for (BufferedImage image : rows) {
            graphics.drawImage(image, 0, y, null);
            y += image.getHeight();
        }
        graphics.dispose();
        TerrainRender.save(sheet, directory.resolve("sellos_en_bloques.png"));
    }

    /** El mismo sello seis veces: cada clic sale distinto; con la semilla bloqueada, iguales. */
    private static void stampVariation(Path directory) throws IOException {
        FakeTerrain free = TerrainScenes.flat(200, 80, 20);
        FakeTerrain locked = TerrainScenes.flat(200, 80, 20);
        SurfacePalette palette = SurfacePalette.parse("alpino");
        TerrainJobs.paint(free, palette, free, NO_CANCEL);
        TerrainJobs.paint(locked, palette, locked, NO_CANCEL);
        for (int index = 0; index < 6; index++) {
            int x = 22 + (index % 3) * 78;
            int z = 40 + (index / 3) * 80;
            click(free, "montana", StampBrush.Mode.RAISE, 24, 22, 0, x, z, index);
            click(locked, "montana", StampBrush.Mode.RAISE, 24, 22, 0, x, z, 0);
        }
        TerrainRender.save(TerrainRender.row(List.of(TerrainRender.isometric(free, 4),
                        TerrainRender.isometric(locked, 4)),
                List.of("montana · seis clics, cada uno distinto", "montana · semilla bloqueada: seis iguales")),
                directory.resolve("variacion_por_clic_iso.png"));
    }

    /** Los cinco modos de mezcla con el mismo sello sobre las mismas lomas. */
    private static void stampModes(Path directory) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (StampBrush.Mode mode : StampBrush.Mode.values()) {
            FakeTerrain terrain = TerrainScenes.hills(84, 70, 12L);
            TerrainJobs.paint(terrain, SurfacePalette.natural(), terrain, NO_CANCEL);
            // Dos clics que se pisan: con sumar se apilan, con máximo no.
            click(terrain, "montana", "alpino", mode, 24, 18, 0, 34, 42, 1);
            click(terrain, "montana", "alpino", mode, 24, 18, 0, 52, 42, 2);
            images.add(TerrainRender.isometric(terrain, 4));
            labels.add(mode.displayName());
        }
        TerrainRender.save(TerrainRender.row(images, labels), directory.resolve("modos_de_mezcla_iso.png"));
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
