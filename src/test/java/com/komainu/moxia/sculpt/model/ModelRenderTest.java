package com.komainu.moxia.sculpt.model;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.sculpt.BrushFalloff;
import com.komainu.moxia.shape.smooth.PieceRender;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.komainu.moxia.sculpt.model.ModelGrids.DIRT;
import static com.komainu.moxia.sculpt.model.ModelGrids.GRASS;
import static com.komainu.moxia.sculpt.model.ModelGrids.NO_CANCEL;
import static com.komainu.moxia.sculpt.model.ModelGrids.STONE;

/**
 * Renders de antes y después de cada pincel de modelado, fuera del juego. Solo se ejecuta con
 * {@code KOMAEDIT_SCULPT_RENDER=<carpeta>}; en el CI no hace nada.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_SCULPT_RENDER", matches = ".+")
class ModelRenderTest {
    private static final int SCALE = 7;
    private static final int ANDESITE = 5;
    private static final int GRAVEL = 6;
    private static final int[] COLORS = {0, 0x8E8E8E, 0x6FA544, 0x8B5E3B, 0xE2D49C, 0xA7A49C, 0x6B6560};

    @Test
    void renderEveryBrush() throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        ModelGrid meadow = ModelGrids.plane(20, 26, 8);
        pair(images, labels, meadow, ModelSettings.defaults(ModelMode.DRAW, 0, 7, 0, 9).withStrength(6),
                "esculpir r9 f6");
        ModelGrid deep = ModelGrids.plane(20, 26, 14);
        pair(images, labels, deep, ModelSettings.defaults(ModelMode.DRAW, 0, 13, 0, 9).withStrength(5)
                .withInvert(true).withSmoothing(1), "esculpir invertido r9 f5 -s 1");
        ModelGrid wall = ModelGrid.create(-16, 0, -16, 33, 26, 33);
        ModelGrids.box(wall, -16, 0, -16, 16, 3, 16, GRASS);
        ModelGrids.box(wall, -16, 4, -16, 16, 25, -2, STONE);
        pair(images, labels, wall, ModelSettings.defaults(ModelMode.DRAW, 0, 14, -2, 7).withStrength(4),
                "esculpir en una pared r7 f4");

        ModelGrid flat = ModelGrids.plane(20, 32, 6);
        ModelGrid rocks = ModelGrids.copy(flat);
        int[][] clicks = {{-6, -4, 7, 1}, {5, 3, 5, 2}, {9, -9, 4, 3}};
        for (int[] click : clicks) {
            ModelSettings settings = ModelSettings.defaults(ModelMode.ROCK, click[0], 5, click[1], click[2])
                    .withSeed(click[3]);
            rocks = ModelSculptor.apply(rocks, settings, MaterialChooser.INHERIT, MaterialChooser.AIR, NO_CANCEL)
                    .grid();
        }
        BufferedImage rockBefore = PieceRender.render(cells(flat), SCALE);
        BufferedImage rockAfter = PieceRender.render(cells(recolor(rocks, flat, ANDESITE)), SCALE);
        images.add(rockBefore);
        labels.add("antes");
        images.add(rockAfter);
        labels.add("roca: tres clics (r7, r5, r4)");
        write(PieceRender.sheet(List.of(rockBefore, rockAfter), List.of("antes", "roca: tres clics (r7, r5, r4)"),
                2), "modelado_roca.png");

        ModelGrid pieces = ModelGrid.create(-16, 0, -16, 33, 26, 33);
        ModelGrids.box(pieces, -16, 0, -16, 16, 3, 16, STONE);
        ModelGrids.box(pieces, -9, 4, -4, -3, 18, 2, ANDESITE);
        ModelGrids.box(pieces, -1, 4, -4, 5, 14, 2, ANDESITE);
        ModelGrids.box(pieces, -9, 19, -4, 5, 21, 2, ANDESITE);
        pair(images, labels, pieces, ModelSettings.defaults(ModelMode.WELD, -2, 14, -1, 9)
                .withThreshold(0.3).withFalloff(BrushFalloff.CONSTANT), "soldar r9 umbral 0,3");

        ModelGrid blocky = ModelGrid.create(-16, 0, -16, 33, 26, 33);
        ModelGrids.box(blocky, -16, 0, -16, 16, 3, 16, STONE);
        ModelGrids.box(blocky, -8, 4, -8, 8, 12, 8, ANDESITE);
        ModelGrids.box(blocky, -4, 13, -4, 4, 18, 4, ANDESITE);
        pair(images, labels, blocky, ModelSettings.defaults(ModelMode.MELT, 0, 12, 0, 12)
                .withFalloff(BrushFalloff.CONSTANT), "fundir r12 umbral 0,6");

        ModelGrid boulder = ModelGrid.create(-16, 0, -16, 33, 28, 33);
        ModelGrids.box(boulder, -16, 0, -16, 16, 3, 16, GRASS);
        sphereAt(boulder, 0, 9, 0, 9.5, STONE);
        pair(images, labels, boulder, ModelSettings.defaults(ModelMode.ROUGHEN, 0, 12, 0, 14).withSeed(5),
                "rugosidad r14 amplitud 1,5");
        ModelGrid rough = hills(20, 28, 10);
        pair(images, labels, rough, ModelSettings.defaults(ModelMode.ROUGHEN, 0, top(rough, 0, 0), 0, 12)
                .withStrength(2).withSeed(8), "rugosidad en colinas r12 amplitud 2");

        ModelGrid cliff = ModelGrid.create(-16, 0, -16, 33, 28, 33);
        ModelGrids.box(cliff, -16, 0, -16, 16, 3, 16, GRASS);
        ModelGrids.box(cliff, -12, 4, -12, 12, 22, 12, STONE);
        pair(images, labels, cliff, ModelSettings.defaults(ModelMode.SHATTER, 6, 18, 6, 16).withScale(6)
                .withDepth(3).withSeed(9), "grietas 3D r16 celda 6");
        pair(images, labels, cliff, ModelSettings.defaults(ModelMode.SHATTER, 6, 18, 6, 16).withScale(7)
                .withWidth(2).withDepth(4).withSeed(9).withFalloff(BrushFalloff.SMOOTH),
                "grietas anchas (2) con caída suave");
        pair(images, labels, cliff, ModelSettings.defaults(ModelMode.SHATTER, 6, 18, 6, 16).withScale(4)
                .withAxis(ModelMode.Axis.Y).withDepth(0).withFalloff(BrushFalloff.CONSTANT).withSeed(4),
                "grietas en columnas (Y) celda 4");
        ModelGrid filled = ModelSculptor.apply(cliff, ModelSettings.defaults(ModelMode.SHATTER, 6, 18, 6, 16)
                        .withScale(5).withWidth(1.0).withDepth(2).withFalloff(BrushFalloff.CONSTANT).withSeed(2),
                MaterialChooser.INHERIT, (x, y, z, inherited, covered) -> GRAVEL, NO_CANCEL).grid();
        images.add(PieceRender.render(cells(cliff), SCALE));
        labels.add("antes");
        images.add(PieceRender.render(cells(filled), SCALE));
        labels.add("grietas rellenas de grava, profundidad 2");

        // La hoja con todo junto va aparte: no se guarda en docs/renders para no duplicar las de cada pincel.
        if (System.getenv("KOMAEDIT_SCULPT_RENDER_ALL") != null) {
            write(PieceRender.sheet(images, labels, 2), "modelado_todos.png");
        }
    }

    private static void pair(List<BufferedImage> images, List<String> labels, ModelGrid grid,
                             ModelSettings settings, String label) throws IOException {
        ModelSculptor.Result result = ModelSculptor.apply(grid, settings, MaterialChooser.INHERIT,
                MaterialChooser.AIR, NO_CANCEL);
        BufferedImage before = PieceRender.render(cells(grid), SCALE);
        BufferedImage after = PieceRender.render(cells(result.grid()), SCALE);
        images.add(before);
        labels.add("antes");
        images.add(after);
        labels.add(label + " · +" + result.added() + " −" + result.removed()
                + (result.replaced() > 0 ? " ~" + result.replaced() : ""));
        write(PieceRender.sheet(List.of(before, after), List.of("antes", label), 2),
                "modelado_" + java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "").replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("_+$", "") + ".png");
    }

    /** Colinas suaves de tierra y hierba sobre piedra. */
    static ModelGrid hills(int half, int sizeY, int base) {
        ModelGrid grid = ModelGrid.create(-half, 0, -half, half * 2 + 1, sizeY, half * 2 + 1);
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                int height = base + (int) Math.round(3.0 * Math.sin(x * 0.21) * Math.cos(z * 0.17)
                        + 1.5 * Math.sin((x + z) * 0.37));
                for (int y = 0; y < height; y++) {
                    grid.set(x, y, z, y == height - 1 ? GRASS : y >= height - 3 ? DIRT : STONE);
                }
            }
        }
        return grid;
    }

    static int top(ModelGrid grid, int x, int z) {
        for (int y = grid.minimumY() + grid.sizeY() - 1; y >= grid.minimumY(); y--) {
            if (grid.solid(x, y, z)) {
                return y;
            }
        }
        return grid.minimumY();
    }

    static void sphereAt(ModelGrid grid, int cx, int cy, int cz, double radius, int material) {
        for (int index = 0; index < grid.size(); index++) {
            int dx = grid.x(index) - cx;
            int dy = grid.y(index) - cy;
            int dz = grid.z(index) - cz;
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                grid.set(grid.x(index), grid.y(index), grid.z(index), material);
            }
        }
    }

    /** Pinta de otro color lo que no estaba en {@code before}, para ver qué añadió el pincel. */
    static ModelGrid recolor(ModelGrid after, ModelGrid before, int material) {
        ModelGrid copy = ModelGrids.copy(after);
        for (int index = 0; index < copy.size(); index++) {
            if (copy.materialAt(index) != ModelGrid.FREE && before.materialAt(index) == ModelGrid.FREE) {
                copy.set(copy.x(index), copy.y(index), copy.z(index), material);
            }
        }
        return copy;
    }

    static Long2ObjectOpenHashMap<PieceRender.Cell> cells(ModelGrid grid) {
        Long2ObjectOpenHashMap<PieceRender.Cell> cells = new Long2ObjectOpenHashMap<>();
        for (int index = 0; index < grid.size(); index++) {
            int material = grid.materialAt(index);
            if (material == ModelGrid.FREE) {
                continue;
            }
            int color = COLORS[Math.min(material, COLORS.length - 1)];
            // Un poco de variación por bloque para leer la forma.
            double shade = 0.94 + 0.06 * ModelFields.unit(grid.x(index), grid.y(index), grid.z(index), 77);
            int r = (int) ((color >> 16 & 0xFF) * shade);
            int g = (int) ((color >> 8 & 0xFF) * shade);
            int b = (int) ((color & 0xFF) * shade);
            cells.put(PackedBlockPlan.pack(grid.x(index), grid.y(index), grid.z(index)),
                    new PieceRender.Cell(SmoothPiece.FULL, r << 16 | g << 8 | b));
        }
        return cells;
    }

    static void write(BufferedImage image, String name) throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_SCULPT_RENDER"));
        Files.createDirectories(folder);
        ImageIO.write(image, "png", folder.resolve(name).toFile());
    }
}
