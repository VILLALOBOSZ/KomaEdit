package com.komainu.moxia.terrain;

import com.komainu.moxia.noise.NoiseEngine;
import com.komainu.moxia.terrain.stamp.StampCategory;
import com.komainu.moxia.terrain.stamp.StampInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders de la cordillera, la costa y el valle por la trayectoria: desde arriba y en perspectiva, con curvas suaves y
 * cerradas, un recorrido largo y terreno llano e irregular. Solo con {@code KOMAEDIT_LANDFORM_RENDER=<carpeta>}.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_LANDFORM_RENDER", matches = ".+")
class LandformRenderDump {
    private static final Runnable NO_CANCEL = FakeTerrain.NO_CANCEL;
    /** Las paletas automáticas de la orden, las mismas que en el juego. */
    private static final String COAST_PALETTE = LandformRequest.parse(LandformPlanner.Kind.COAST, "").paletteName();
    private static final String GLACIAL_PALETTE = LandformRequest.parse(LandformPlanner.Kind.VALLEY, "u")
            .paletteName();

    @Test
    void dumpLandforms() throws IOException {
        Path directory = Path.of(System.getenv("KOMAEDIT_LANDFORM_RENDER"));
        Files.createDirectories(directory);
        String only = System.getenv("KOMAEDIT_LANDFORM_ONLY");
        for (Scene scene : scenes()) {
            if (only != null && !only.isBlank() && !scene.name.contains(only)) {
                continue;
            }
            render(directory, scene);
        }
        if (only == null || only.isBlank() || "sello_capturado".contains(only)) {
            captured(directory);
        }
    }

    /**
     * Un valle cogido del mundo y estampado al doble de grande: con las alturas enteras tal cual, cada bloque se vuelve
     * una terraza; capturado por KomaEdit (sin escalones, 16 bits), sale liso.
     */
    private static void captured(Path directory) throws IOException {
        FakeTerrain source = slopeHills(140, 170, 140, 97L);
        TerrainJobs.paint(source, SurfacePalette.parse("bosque"), source, NO_CANCEL);
        TerrainJobs.landform(source, TerrainScenes.curve(new double[][]{{10, 60, 30}, {70, 55, 70},
                        {130, 50, 110}}), false, valley(30, 16, 0.7, 5L, LandformPlanner.Form.V, false),
                SurfacePalette.parse("pradera"), source, NO_CANCEL);
        int fromX = 30;
        int fromZ = 30;
        int side = 80;
        StampCapture.Columns columns = new StampCapture.Columns(side, side);
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                int top = source.top(fromX + x, fromZ + z);
                columns.set(x, z, top, false, source.blockId(fromX + x, top, fromZ + z));
                lowest = Math.min(lowest, top);
                highest = Math.max(highest, top);
            }
        }
        StampCapture.Result smooth = StampCapture.build("capturado", columns, null, NO_CANCEL);
        // Lo mismo sin quitar los escalones: el gris de cada columna, tal cual.
        float[] raw = new float[side * side];
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                raw[z * side + x] = (float) ((columns.surface(x, z) - lowest) / (double) (highest - lowest));
            }
        }
        HeightStamp naive = new HeightStamp("tal_cual", side, side, raw, 0.0);
        double scale = 2.0;
        BufferedImage original = LandformRender.perspective(window(source, fromX, fromZ, side), 640, 420, 150.0,
                34.0, 1.35);
        List<BufferedImage> views = new ArrayList<>(List.of(original));
        List<String> labels = new ArrayList<>(List.of("el trozo de mundo (80 × 80)"));
        for (HeightStamp stamp : List.of(naive, smooth.stamp())) {
            FakeTerrain target = TerrainScenes.flat(200, 170, 70);
            TerrainJobs.paint(target, SurfacePalette.parse("pradera"), target, NO_CANCEL);
            double height = (stamp == naive ? highest - lowest : smooth.above() + smooth.below()) * scale;
            double base = 70;
            TerrainJobs.stamp(target, new StampBrush.Settings(stamp, StampBrush.Mode.RAISE, side / 2.0 * scale,
                    stamp == naive ? height : Math.max(smooth.above(), smooth.below()) * scale, 1.0, 0.0, base),
                    100, 100, SurfacePalette.parse("pradera"), target, NO_CANCEL);
            views.add(LandformRender.perspective(window(target, 20, 20, 160), 640, 420, 150.0, 34.0, 1.2));
            labels.add(stamp == naive ? "estampado al doble, alturas tal cual: terrazas"
                    : "estampado al doble, capturado por KomaEdit: liso");
        }
        TerrainRender.save(TerrainRender.row(views, labels), directory.resolve("sello_capturado_escalones.png"));
    }

    /** Un trozo cuadrado de un terreno, para verlo de cerca. */
    private static FakeTerrain window(FakeTerrain terrain, int fromX, int fromZ, int side) {
        FakeTerrain copy = new FakeTerrain(0, terrain.minimumY(), 0, side, terrain.sizeY(), side);
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                for (int y = terrain.minimumY(); y <= terrain.maximumY(); y++) {
                    String block = terrain.blockId(fromX + x, y, fromZ + z);
                    if (!block.equals("minecraft:air")) {
                        copy.put(x, y, z, block);
                    }
                }
            }
        }
        return copy;
    }

    private record Scene(String name, String label, FakeTerrain terrain, List<double[]> path, boolean closed,
                         LandformPlanner.Settings settings, String palette, String ground) {
    }

    private static List<Scene> scenes() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(new Scene("cordillera_curva_suave", "cordillera · curva suave sobre llano",
                TerrainScenes.flat(300, 150, 30), TerrainScenes.curve(new double[][]{
                {30, 31, 200}, {95, 31, 150}, {165, 31, 165}, {230, 31, 95}, {270, 31, 40}}), false,
                range(56, 46, 0.7, 11L), "alpino", "pradera"));
        scenes.add(new Scene("cordillera_curva_cerrada", "cordillera · curva cerrada sobre llano",
                TerrainScenes.flat(280, 150, 30), TerrainScenes.curve(new double[][]{
                {50, 31, 240}, {60, 31, 120}, {110, 31, 55}, {165, 31, 60}, {205, 31, 120}, {215, 31, 240}}),
                false, range(46, 42, 0.7, 23L), "alpino", "pradera"));
        scenes.add(new Scene("cordillera_recorrido_largo", "cordillera · recorrido largo sobre terreno irregular",
                hills(560, 170, 220, 31L, 34, 9), TerrainScenes.curve(new double[][]{
                {20, 40, 150}, {120, 40, 90}, {230, 40, 130}, {330, 40, 70}, {440, 40, 120}, {540, 40, 80}}),
                false, range(50, 48, 0.75, 37L), "alpino", "pradera"));
        scenes.add(new Scene("cordillera_irregular", "cordillera · sobre terreno irregular",
                hills(280, 170, 280, 53L, 40, 12), TerrainScenes.curve(new double[][]{
                {30, 44, 60}, {120, 44, 110}, {170, 44, 200}, {250, 44, 240}}), false,
                range(50, 44, 0.7, 59L), "alpino", "bosque"));
        scenes.add(new Scene("cordillera_cerrada", "cordillera · trayectoria cerrada (un anillo)",
                TerrainScenes.flat(300, 150, 30), ring(150, 150, 95, 70, 9), true,
                range(40, 36, 0.7, 61L), "alpino", "pradera"));
        List<double[]> shore = TerrainScenes.curve(new double[][]{{60, 29, 170}, {140, 29, 210}, {220, 29, 170},
                {290, 29, 200}, {345, 29, 180}});
        scenes.add(new Scene("costa_curva_suave", "costa · curva suave junto al mar (a Y 26)",
                withSea(shore(400, 120, 340, shore), 26), shore, false,
                coast(46, 30, 0.75, 71L, LandformPlanner.Side.AUTO), COAST_PALETTE, "pradera"));
        scenes.add(new Scene("costa_irregular", "costa · acantilado sobre terreno irregular (tierra a la izquierda)",
                hills(380, 140, 360, 83L, 36, 10), TerrainScenes.curve(new double[][]{{70, 40, 90},
                {160, 40, 150}, {200, 40, 240}, {320, 40, 270}}), false,
                coast(44, 26, 0.75, 89L, LandformPlanner.Side.LEFT), COAST_PALETTE, "pradera"));
        scenes.add(new Scene("valle_v_irregular", "valle en V con río · sobre terreno irregular",
                slopeHills(380, 170, 340, 97L), TerrainScenes.curve(new double[][]{{60, 70, 70},
                {150, 60, 140}, {210, 50, 150}, {320, 40, 260}}), false,
                valley(46, 30, 0.7, 101L, LandformPlanner.Form.V, true), "pradera", "bosque"));
        scenes.add(new Scene("valle_u_curva_cerrada", "valle glaciar en U · curva cerrada",
                hills(340, 170, 340, 103L, 70, 10), TerrainScenes.curve(new double[][]{{70, 72, 270},
                {100, 72, 120}, {170, 72, 80}, {230, 72, 140}, {250, 72, 270}}), false,
                valley(40, 34, 0.65, 107L, LandformPlanner.Form.U, false), GLACIAL_PALETTE, "nevado"));
        scenes.add(new Scene("canon_llano", "cañón escalonado · sobre llano",
                TerrainScenes.flat(380, 140, 80), TerrainScenes.curve(new double[][]{{60, 81, 150},
                {140, 81, 110}, {230, 81, 190}, {320, 81, 140}}), false,
                valley(34, 40, 0.7, 109L, LandformPlanner.Form.CANYON, true), "mesa", "mesa"));
        return scenes;
    }

    private static LandformPlanner.Settings range(double halfWidth, double height, double variation, long seed) {
        return new LandformPlanner.Settings(LandformPlanner.Kind.RANGE, halfWidth, height,
                LandformPlanner.Profile.BELL, variation, seed, LandformPlanner.Side.RIGHT, LandformPlanner.Form.V,
                LandformPlanner.Blend.ADD, 0.5, false, family(StampCategory.RANGES));
    }

    private static LandformPlanner.Settings coast(double halfWidth, double height, double variation, long seed,
                                                  LandformPlanner.Side side) {
        return new LandformPlanner.Settings(LandformPlanner.Kind.COAST, halfWidth, height,
                LandformPlanner.Profile.FLAT, variation, seed, side, LandformPlanner.Form.V, LandformPlanner.Blend.ADD, 0.4, true,
                family(StampCategory.CLIFFS));
    }

    private static LandformPlanner.Settings valley(double halfWidth, double depth, double variation, long seed,
                                                   LandformPlanner.Form form, boolean water) {
        StampCategory family = form == LandformPlanner.Form.U ? StampCategory.GLACIAL : StampCategory.CANYONS;
        return new LandformPlanner.Settings(LandformPlanner.Kind.VALLEY, halfWidth, depth,
                LandformPlanner.Profile.FLAT, variation, seed, LandformPlanner.Side.RIGHT, form,
                LandformPlanner.Blend.MAX, 0.4, water, family(family));
    }

    private static List<HeightStamp> family(StampCategory category) {
        List<HeightStamp> stamps = new ArrayList<>();
        for (StampInfo info : StampLibrary.of(category)) {
            stamps.add(StampLibrary.get(info.name()));
        }
        return stamps;
    }

    private static FakeTerrain hills(int sizeX, int sizeY, int sizeZ, long seed, int base, int amplitude) {
        return FakeTerrain.columns(sizeX, sizeY, sizeZ, (x, z) -> (int) Math.round(base + amplitude
                * NoiseEngine.fractalBrownianMotion2D(x * 0.018, z * 0.018, seed, 4, 2.0, 0.5)));
    }

    /** Ladera que baja hacia el sureste con lomas: el valle la cruza. */
    private static FakeTerrain slopeHills(int sizeX, int sizeY, int sizeZ, long seed) {
        return FakeTerrain.columns(sizeX, sizeY, sizeZ, (x, z) -> (int) Math.round(80 - (x + z) * 0.07
                + 10 * NoiseEngine.fractalBrownianMotion2D(x * 0.02, z * 0.02, seed, 4, 2.0, 0.5)));
    }

    /** Tierra a Y 29 al norte de la orilla y fondo de mar que baja hacia el sur. */
    private static FakeTerrain shore(int sizeX, int sizeY, int sizeZ, List<double[]> line) {
        return FakeTerrain.columns(sizeX, sizeY, sizeZ, (x, z) -> {
            double best = Double.POSITIVE_INFINITY;
            double lineZ = 0.0;
            for (double[] point : line) {
                double distance = Math.abs(point[0] - x);
                if (distance < best) {
                    best = distance;
                    lineZ = point[2];
                }
            }
            double off = z - lineZ;
            return off <= 0 ? 29 : (int) Math.round(29 - Math.min(10.0, off * 0.35));
        });
    }

    private static FakeTerrain withSea(FakeTerrain terrain, int level) {
        for (int z = terrain.minimumZ(); z <= terrain.maximumZ(); z++) {
            for (int x = terrain.minimumX(); x <= terrain.maximumX(); x++) {
                for (int y = terrain.top(x, z) + 1; y <= level; y++) {
                    terrain.put(x, y, z, "minecraft:water");
                }
            }
        }
        return terrain;
    }

    private static List<double[]> ring(double centerX, double centerZ, double radiusX, double radiusZ, int points) {
        double[][] controls = new double[points + 3][];
        for (int index = 0; index < points + 3; index++) {
            double angle = 2.0 * Math.PI * (index - 1) / points;
            double wobble = 1.0 + 0.12 * Math.sin(angle * 3.0 + 0.7);
            controls[index] = new double[]{centerX + Math.cos(angle) * radiusX * wobble, 31,
                    centerZ + Math.sin(angle) * radiusZ * wobble};
        }
        List<double[]> curve = TerrainScenes.curve(controls);
        // Sin el primer y el último tramo, que solo sirven de apoyo a la curva.
        double step = curve.size() / (double) (points + 2);
        return new ArrayList<>(curve.subList((int) Math.round(step), (int) Math.round(step * (points + 1)) + 1));
    }

    private static void render(Path directory, Scene scene) throws IOException {
        FakeTerrain terrain = scene.terrain;
        TerrainJobs.paint(terrain, SurfacePalette.parse(scene.ground), terrain, NO_CANCEL);
        long start = System.nanoTime();
        TerrainJobs.Summary summary = TerrainJobs.landform(terrain, scene.path, scene.closed, scene.settings,
                SurfacePalette.parse(scene.palette), terrain, NO_CANCEL);
        long millis = (System.nanoTime() - start) / 1_000_000;
        String label = scene.label + " · " + summary.detail() + " (" + millis + " ms)";
        BufferedImage top = LandformRender.relief(terrain, 2);
        BufferedImage view = LandformRender.perspective(terrain, 1280, 720, 150.0, 38.0, 0.95);
        TerrainRender.save(TerrainRender.row(List.of(top), List.of(label)), directory.resolve(scene.name
                + "_arriba.png"));
        TerrainRender.save(TerrainRender.row(List.of(view), List.of(label)), directory.resolve(scene.name
                + "_perspectiva.png"));
    }
}
