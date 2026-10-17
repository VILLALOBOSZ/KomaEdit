package com.komainu.moxia.texture;

import com.komainu.moxia.glass.GlassColors;
import com.komainu.moxia.glass.GlassGradientSolver;
import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.region.RegionRules;
import com.komainu.moxia.region.RegionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders de comprobación fuera del juego. Solo se ejecuta con {@code KOMAEDIT_TEXTURE_RENDER=<carpeta>}: deja allí
 * la pared de pruebas sin texturizar y con cada modo (paleta en escala de grises: principio negro, final blanco), un
 * suelo con musgo y una vidriera degradada.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_TEXTURE_RENDER", matches = ".+")
class TextureRenderTest {
    private static final int SCALE = 9;
    private static final int PALETTE = 8;

    @Test
    void rendersEveryMode() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_TEXTURE_RENDER"));
        Files.createDirectories(folder);
        RegionSnapshot wall = TextureScenes.wall();
        TextureContext.Settings settings = TextureContext.Settings.at(12, 12, -14, 21L);
        Map<String, TextureField> modes = new LinkedHashMap<>();
        modes.put("ambient", new TextureFields.Ambient(3, 0.0, 0.0));
        modes.put("ambient_contraste", new TextureFields.Ambient(3, 0.0, 1.0));
        modes.put("curvature", new TextureFields.Curvature(2, 0.0, 0.0));
        modes.put("axisgradient", new TextureFields.AxisGradient(1, false));
        modes.put("noise", new TextureFields.Noise(NoiseSpec.parse("Perlin(f:0.12)"), 1.0, 5L));
        modes.put("cells", new TextureFields.Cells(14, 0.0, 0.0, 5L, 5));
        modes.put("flow", new TextureFields.Flow(0.7, 32, 0.5, 1.0, NoiseSpec.parse("Perlin(f:0.06)"), 0.3, null,
                true, 5L));
        modes.put("pointlight", TextureFields.Light.point(null, 0.0, true, 1, 0, 90, 0.6, 1.0));
        modes.put("sunlight", TextureFields.Light.sun(Vectors.direction("-1;-0.6;0.5"), 1, 0, 180, 0.8, 1.0));
        modes.put("advanced", TextureSpec.parse("WeightedAverage(T1:Sun(L:-1;-0.6;0.5,O:0.8),T2:Ambient(R:3))", 5L));
        List<BufferedImage> tiles = new ArrayList<>();
        tiles.add(label(render(wall, null, null), "sin texturizar"));
        for (Map.Entry<String, TextureField> mode : modes.entrySet()) {
            TextureContext context = new TextureContext(wall, TextureContext.Filter.ALL, settings, () -> false);
            long start = System.nanoTime();
            double[] values = mode.getValue().evaluate(context);
            long millis = (System.nanoTime() - start) / 1_000_000;
            BufferedImage image = label(render(wall, context, values), mode.getKey() + " · " + millis + " ms");
            tiles.add(image);
            ImageIO.write(image, "png", folder.resolve("textura_" + mode.getKey() + ".png").toFile());
        }
        ImageIO.write(sheet(tiles, 4), "png", folder.resolve("texturas.png").toFile());
        Files.writeString(folder.resolve("tiempos.txt"), timings());
        ImageIO.write(moss(), "png", folder.resolve("musgo.png").toFile());
        ImageIO.write(glass(), "png", folder.resolve("vidriera.png").toFile());
    }

    /** Tiempos de cada modo sobre un terreno de 192×192 con colinas (unas 40 000 caras al aire). */
    private static String timings() {
        int size = 192;
        int height = 48;
        RegionSnapshot.Builder builder = TextureScenes.empty(0, 0, 0, size, height, size);
        NoiseSpec.NoiseField hills = NoiseSpec.parse("Perlin(f:0.02,fT:F,fO:3)").build(3L);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int top = 6 + (int) (hills.sample(x, 0, z) * 34);
                for (int y = 0; y < height; y++) {
                    builder.set(x, y, z, y <= top ? TextureScenes.STONE : TextureScenes.AIR,
                            x >= 4 && z >= 4 && x < size - 4 && z < size - 4);
                }
            }
        }
        RegionSnapshot terrain = builder.build();
        TextureContext.Settings settings = TextureContext.Settings.at(96, 80, 96, 1L);
        Map<String, TextureField> modes = new LinkedHashMap<>();
        modes.put("ambient r3", new TextureFields.Ambient(3, 0.0, 0.0));
        modes.put("ambient r6", new TextureFields.Ambient(6, 0.0, 0.0));
        modes.put("curvature r3", new TextureFields.Curvature(3, 0.0, 0.0));
        modes.put("noise", new TextureFields.Noise(NoiseSpec.parse("Perlin(f:0.05)"), 1.0, 1L));
        modes.put("cells 200", new TextureFields.Cells(200, 0.0, 0.0, 1L, 5));
        modes.put("flow", new TextureFields.Flow(0.6, 32, 0.5, 1.0, NoiseSpec.parse("Perlin(f:0.03)"), 0.0, null,
                false, 1L));
        modes.put("pointlight o", TextureFields.Light.point(null, 0.0, true, 1, 0, 90, 0.8, 1.0));
        modes.put("sunlight o", TextureFields.Light.sun(Vectors.direction("-1;-1;0.3"), 2, 0, 180, 0.8, 1.0));
        StringBuilder report = new StringBuilder();
        long start = System.nanoTime();
        TextureContext context = new TextureContext(terrain, TextureContext.Filter.ALL, settings, () -> false);
        report.append(String.format("contexto: %d bloques, %d de superficie, %d ms%n", context.size(),
                context.surfaceCount(), (System.nanoTime() - start) / 1_000_000));
        for (Map.Entry<String, TextureField> mode : modes.entrySet()) {
            TextureContext fresh = new TextureContext(terrain, TextureContext.Filter.ALL, settings, () -> false);
            start = System.nanoTime();
            mode.getValue().evaluate(fresh);
            report.append(String.format("%s: %d ms%n", mode.getKey(), (System.nanoTime() - start) / 1_000_000));
        }
        return report.toString();
    }

    /** Vista oblicua de frente (la cara z negativa) con un poco de la de arriba y la izquierda. */
    private static BufferedImage render(RegionSnapshot snapshot, TextureContext context, double[] values) {
        int width = 34 * SCALE;
        int height = 30 * SCALE;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(170, 200, 230));
        graphics.fillRect(0, 0, width, height);
        // Pintor: de atrás (z grande) a delante, de abajo arriba.
        for (int z = snapshot.maximumZ(); z >= snapshot.minimumZ(); z--) {
            for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
                for (int x = snapshot.maximumX(); x >= snapshot.minimumX(); x--) {
                    if (!snapshot.isSolid(x, y, z) || !snapshot.selected(x, y, z) && y >= 0) {
                        continue;
                    }
                    Color base = color(snapshot, context, values, x, y, z);
                    int sx = (x + 5) * SCALE + (z + 8) * SCALE / 3;
                    int sy = height - (y + 6) * SCALE - (z + 8) * SCALE / 3;
                    if (!snapshot.isSolid(x, y + 1, z)) {
                        graphics.setColor(shade(base, 1.1));
                        graphics.fillPolygon(new int[]{sx, sx + SCALE, sx + SCALE + SCALE / 3, sx + SCALE / 3},
                                new int[]{sy, sy, sy - SCALE / 3, sy - SCALE / 3}, 4);
                    }
                    if (!snapshot.isSolid(x - 1, y, z)) {
                        graphics.setColor(shade(base, 0.7));
                        graphics.fillPolygon(new int[]{sx, sx + SCALE / 3, sx + SCALE / 3, sx},
                                new int[]{sy, sy - SCALE / 3, sy + SCALE - SCALE / 3, sy + SCALE}, 4);
                    }
                    if (!snapshot.isSolid(x, y, z - 1)) {
                        graphics.setColor(base);
                        graphics.fillRect(sx, sy, SCALE, SCALE);
                    }
                }
            }
        }
        graphics.dispose();
        return image;
    }

    private static Color color(RegionSnapshot snapshot, TextureContext context, double[] values, int x, int y, int z) {
        if (y < 0) {
            return new Color(95, 120, 70);
        }
        if (context == null) {
            return new Color(128, 128, 128);
        }
        int index = context.index(x, y, z);
        if (index < 0 || Double.isNaN(values[index])) {
            return new Color(150, 110, 110);
        }
        int position = PaletteRemap.index(values[index], PALETTE, 0.0, x, y, z);
        int gray = 20 + position * 225 / (PALETTE - 1);
        return new Color(gray, gray, gray);
    }

    private static Color shade(Color color, double factor) {
        return new Color(Math.min(255, (int) (color.getRed() * factor)), Math.min(255, (int) (color.getGreen() * factor)),
                Math.min(255, (int) (color.getBlue() * factor)));
    }

    private static BufferedImage label(BufferedImage image, String text) {
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, 8, 16);
        graphics.dispose();
        return image;
    }

    private static BufferedImage sheet(List<BufferedImage> tiles, int columns) {
        int width = tiles.getFirst().getWidth();
        int height = tiles.getFirst().getHeight();
        int rows = (tiles.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(width * columns, height * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        for (int index = 0; index < tiles.size(); index++) {
            graphics.drawImage(tiles.get(index), (index % columns) * width, (index / columns) * height, null);
        }
        graphics.dispose();
        return sheet;
    }

    /** Suelo de 64×64 visto desde arriba: musgo con varias cantidades e iteraciones. */
    private static BufferedImage moss() {
        List<int[]> floor = new ArrayList<>();
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                floor.add(new int[]{x, 64, z});
            }
        }
        int[][] positions = floor.toArray(int[][]::new);
        double[][] settings = {{1, 1, 5}, {2, 1, 5}, {4, 1, 5}, {2, 1, 0}, {2, 2, 10}, {2, 1, 20}};
        BufferedImage image = new BufferedImage(settings.length * 140, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int panel = 0; panel < settings.length; panel++) {
            int radius = (int) settings[panel][1];
            boolean[] moss = RegionRules.moss(positions, settings[panel][0], new int[]{radius, radius, radius},
                    (int) settings[panel][2], 8L, () -> { });
            for (int index = 0; index < positions.length; index++) {
                graphics.setColor(moss[index] ? new Color(90, 130, 50) : new Color(125, 125, 125));
                graphics.fillRect(panel * 140 + 6 + positions[index][0] * 2, 24 + positions[index][2] * 2, 2, 2);
            }
            graphics.setColor(Color.BLACK);
            graphics.drawString("c " + settings[panel][0] + " r " + radius + " it " + (int) settings[panel][2],
                    panel * 140 + 6, 16);
        }
        graphics.dispose();
        return image;
    }

    /** Tres degradados con 1, 3 y 5 capas sobre negro y uno con fondo elegido: arriba el buscado, abajo el que sale. */
    private static BufferedImage glass() {
        GlassGradientSolver solver = GlassGradientSolver.standard();
        double[] start = GlassColors.hex("#E8D44D");
        double[] end = GlassColors.hex("#5A2FB2");
        int length = 24;
        BufferedImage image = new BufferedImage(length * 24 + 20, 4 * 70 + 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        Object[][] rows = {
                {1, List.of(new GlassColors.Swatch("", 0, 0, 0, 1.0)), "1 capa, fondo negro"},
                {3, List.of(new GlassColors.Swatch("", 0, 0, 0, 1.0)), "3 capas, fondo negro"},
                {5, List.of(new GlassColors.Swatch("", 0, 0, 0, 1.0)), "5 capas, fondo negro"},
                {3, GlassColors.backgrounds(), "3 capas, fondo elegido (-b)"}
        };
        for (int row = 0; row < rows.length; row++) {
            @SuppressWarnings("unchecked")
            List<GlassColors.Swatch> backgrounds = (List<GlassColors.Swatch>) rows[row][1];
            List<GlassGradientSolver.Stack> stacks = solver.gradient(start, end, (int) rows[row][0], length, 6,
                    backgrounds, false, () -> false);
            double error = 0.0;
            for (int step = 0; step < length; step++) {
                GlassGradientSolver.Stack stack = stacks.get(step);
                error += stack.error();
                graphics.setColor(rgb(stack.target()));
                graphics.fillRect(10 + step * 24, 22 + row * 70, 24, 20);
                graphics.setColor(rgb(stack.color()));
                graphics.fillRect(10 + step * 24, 42 + row * 70, 24, 20);
            }
            graphics.setColor(Color.BLACK);
            graphics.drawString(rows[row][2] + String.format(" · error medio %.3f", error / length), 10,
                    16 + row * 70);
        }
        graphics.dispose();
        return image;
    }

    private static Color rgb(double[] color) {
        return new Color((int) Math.round(Math.clamp(color[0], 0, 255)), (int) Math.round(Math.clamp(color[1], 0, 255)),
                (int) Math.round(Math.clamp(color[2], 0, 255)));
    }
}
