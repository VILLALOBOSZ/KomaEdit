package com.komainu.moxia.ivy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Renders isométricos de comprobación fuera del juego. Solo se ejecuta con {@code KOMAEDIT_IVY_RENDER=<carpeta>}:
 * deja allí un PNG por estilo con la casa de pruebas vista desde dos esquinas.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_IVY_RENDER", matches = ".+")
class IvyRenderTest {
    private static final double SCALE = 14.0;
    private static final List<IvyGrower.Point> SEEDS = List.of(
            new IvyGrower.Point(2, 0, -1),
            new IvyGrower.Point(13, 0, -1),
            new IvyGrower.Point(17, 0, 4)
    );

    @Test
    void rendersEveryStyle() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_IVY_RENDER"));
        Files.createDirectories(folder);
        for (IvyStyle style : IvyStyle.values()) {
            FakeTerrain terrain = FakeTerrain.house();
            IvySettings settings = IvySettings.parse(style.id() + " -l 45 -b 0.5 -g 0.6 -n 3");
            IvyResult result = IvyGrower.grow(terrain, SEEDS, settings, 11L, 50_000, () -> false);
            BufferedImage west = render(terrain, result, -1, -1);
            BufferedImage east = render(terrain, result, 1, -1);
            BufferedImage sheet = new BufferedImage(west.getWidth() + east.getWidth(),
                    Math.max(west.getHeight(), east.getHeight()), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = sheet.createGraphics();
            graphics.drawImage(west, 0, 0, null);
            graphics.drawImage(east, west.getWidth(), 0, null);
            graphics.setColor(Color.BLACK);
            graphics.drawString(style.displayName() + " · " + result.blocks().size() + " bloques · "
                    + result.tips() + " puntas", 10, 18);
            graphics.dispose();
            ImageIO.write(sheet, "png", folder.resolve("hiedra_" + style.id() + ".png").toFile());
        }
        // Voladizo con mucho cuelgue para ver las tiras.
        FakeTerrain ledge = new FakeTerrain(-10, -1, -10, 10, 20, 10)
                .fill(-10, -1, -10, 10, -1, 10, FakeTerrain.SOLID)
                .fill(-6, 0, 0, 6, 9, 0, FakeTerrain.SOLID)
                .fill(-6, 8, -6, 6, 8, -1, FakeTerrain.SOLID);
        IvyResult hanging = IvyGrower.grow(ledge, List.of(new IvyGrower.Point(0, 0, -1)),
                IvySettings.parse("cueva -l 50 -g 1 -b 0.8 -n 4"), 4L, 50_000, () -> false);
        ImageIO.write(render(ledge, hanging, -1, -1), "png", folder.resolve("hiedra_voladizo.png").toFile());
        // Tiempos: la casa con el máximo de todo.
        StringBuilder timings = new StringBuilder();
        for (String options : new String[]{"-l 28", "-l 80 -n 4 -b 0.6", "-l 200 -n 8 -b 1 -g 1"}) {
            long start = System.nanoTime();
            IvyResult result = IvyGrower.grow(FakeTerrain.house(), SEEDS, IvySettings.parse(options), 1L, 200_000,
                    () -> false);
            timings.append(options).append(" -> ").append(result.blocks().size()).append(" bloques, ")
                    .append(result.tips()).append(" puntas, ").append((System.nanoTime() - start) / 1_000_000)
                    .append(" ms").append(System.lineSeparator());
        }
        Files.writeString(folder.resolve("tiempos.txt"), timings);
    }

    private record Quad(double[][] points, Color color, double depth) {
    }

    private static BufferedImage render(FakeTerrain terrain, IvyResult result, int cx, int cz) {
        List<Quad> quads = new ArrayList<>();
        double[] camera = {cx, 1.25, cz};
        for (Map.Entry<Long, Integer> entry : terrain.blocks.entrySet()) {
            long key = entry.getKey();
            int x = (int) (key >> 38);
            int y = (int) (key << 52 >> 52);
            int z = (int) (key << 26 >> 38);
            if (!terrain.inside(x, y, z)) {
                continue;
            }
            Color base = y < 0 ? new Color(118, 150, 82) : y >= 9 && terrain.kind(x, y, z) != 0
                    && !(x == 0 || x == 16) ? new Color(150, 78, 60) : new Color(170, 166, 158);
            for (int face = 0; face < 6; face++) {
                if (terrain.kind(x + IvyGrower.DX[face], y + IvyGrower.DY[face], z + IvyGrower.DZ[face]) != 0) {
                    continue;
                }
                addFace(quads, camera, x, y, z, face, 0.0, shade(base, face), true);
            }
        }
        for (IvyBlock block : result.blocks()) {
            int x = block.x();
            int y = block.y();
            int z = block.z();
            switch (block.material()) {
                case VINE, GLOW_LICHEN -> {
                    Color color = block.material() == IvyMaterial.VINE ? new Color(46, 104, 34) : new Color(96, 168, 140);
                    for (int face = 0; face < 6; face++) {
                        if (block.hasFace(face)) {
                            // La cara del bloque de apoyo, desplazada un poco hacia la celda.
                            addFace(quads, camera, x + IvyGrower.DX[face], y + IvyGrower.DY[face],
                                    z + IvyGrower.DZ[face], face ^ 1, 0.07, vary(color, block), false);
                        }
                    }
                }
                case MOSS_CARPET -> addFace(quads, camera, x, y - 1, z, IvyGrower.UP, 0.08,
                        vary(new Color(88, 128, 44), block), false);
                case CAVE_VINES, CAVE_VINES_PLANT -> {
                    addCross(quads, camera, x, y, z, new Color(70, 120, 40));
                    if (block.berries()) {
                        addBox(quads, camera, x + 0.35, y + 0.2, z + 0.35, 0.3, new Color(240, 160, 40));
                    }
                }
                case SPORE_BLOSSOM -> addBox(quads, camera, x + 0.2, y + 0.75, z + 0.2, 0.6, new Color(220, 110, 170));
                default -> {
                    Color color = switch (block.material()) {
                        case OAK_LEAVES -> new Color(60, 120, 40);
                        case JUNGLE_LEAVES -> new Color(48, 150, 30);
                        case FLOWERING_AZALEA_LEAVES -> new Color(170, 120, 175);
                        default -> new Color(90, 140, 50);
                    };
                    for (int face = 0; face < 6; face++) {
                        addFace(quads, camera, x, y, z, face, 0.0, shade(vary(color, block), face), true);
                    }
                }
            }
        }
        quads.sort(Comparator.comparingDouble(Quad::depth));
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        List<double[][]> projected = new ArrayList<>(quads.size());
        for (Quad quad : quads) {
            double[][] screen = new double[4][];
            for (int corner = 0; corner < 4; corner++) {
                screen[corner] = project(quad.points[corner], cx, cz);
                minX = Math.min(minX, screen[corner][0]);
                minY = Math.min(minY, screen[corner][1]);
                maxX = Math.max(maxX, screen[corner][0]);
                maxY = Math.max(maxY, screen[corner][1]);
            }
            projected.add(screen);
        }
        int width = (int) Math.ceil(maxX - minX) + 40;
        int height = (int) Math.ceil(maxY - minY) + 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(214, 228, 240));
        graphics.fillRect(0, 0, width, height);
        graphics.setStroke(new BasicStroke(0.6f));
        for (int index = 0; index < quads.size(); index++) {
            double[][] screen = projected.get(index);
            Path2D path = new Path2D.Double();
            path.moveTo(screen[0][0] - minX + 20, screen[0][1] - minY + 20);
            for (int corner = 1; corner < 4; corner++) {
                path.lineTo(screen[corner][0] - minX + 20, screen[corner][1] - minY + 20);
            }
            path.closePath();
            Color color = quads.get(index).color;
            graphics.setColor(color);
            graphics.fill(path);
            graphics.setColor(color.darker());
            graphics.draw(path);
        }
        graphics.dispose();
        return image;
    }

    private static double[] project(double[] point, int cx, int cz) {
        double u = point[0] * cx;
        double w = point[2] * cz;
        return new double[]{(u - w) * 0.866 * SCALE, (-point[1] + (u + w) * 0.5) * SCALE};
    }

    /** Cara {@code face} del bloque {@code (x, y, z)}, empujada {@code offset} hacia fuera. */
    private static void addFace(List<Quad> quads, double[] camera, double x, double y, double z, int face,
                                double offset, Color color, boolean cull) {
        double nx = IvyGrower.DX[face];
        double ny = IvyGrower.DY[face];
        double nz = IvyGrower.DZ[face];
        if (cull && nx * camera[0] + ny * camera[1] + nz * camera[2] <= 0) {
            return;
        }
        double cx = x + 0.5 + nx * (0.5 + offset);
        double cy = y + 0.5 + ny * (0.5 + offset);
        double cz = z + 0.5 + nz * (0.5 + offset);
        double[] a;
        double[] b;
        if (ny != 0) {
            a = new double[]{0.5, 0, 0};
            b = new double[]{0, 0, 0.5};
        } else if (nx != 0) {
            a = new double[]{0, 0.5, 0};
            b = new double[]{0, 0, 0.5};
        } else {
            a = new double[]{0.5, 0, 0};
            b = new double[]{0, 0.5, 0};
        }
        double[][] points = {
                {cx - a[0] - b[0], cy - a[1] - b[1], cz - a[2] - b[2]},
                {cx + a[0] - b[0], cy + a[1] - b[1], cz + a[2] - b[2]},
                {cx + a[0] + b[0], cy + a[1] + b[1], cz + a[2] + b[2]},
                {cx - a[0] + b[0], cy - a[1] + b[1], cz - a[2] + b[2]}
        };
        quads.add(new Quad(points, color, cx * camera[0] + cy * camera[1] + cz * camera[2]));
    }

    private static void addCross(List<Quad> quads, double[] camera, int x, int y, int z, Color color) {
        double[][][] planes = {
                {{x + 0.15, y, z + 0.15}, {x + 0.85, y, z + 0.85}, {x + 0.85, y + 1, z + 0.85}, {x + 0.15, y + 1, z + 0.15}},
                {{x + 0.85, y, z + 0.15}, {x + 0.15, y, z + 0.85}, {x + 0.15, y + 1, z + 0.85}, {x + 0.85, y + 1, z + 0.15}}
        };
        for (double[][] plane : planes) {
            quads.add(new Quad(plane, color, (x + 0.5) * camera[0] + (y + 0.5) * camera[1] + (z + 0.5) * camera[2]));
        }
    }

    private static void addBox(List<Quad> quads, double[] camera, double x, double y, double z, double size,
                               Color color) {
        double[][] top = {{x, y + size, z}, {x + size, y + size, z}, {x + size, y + size, z + size},
                {x, y + size, z + size}};
        quads.add(new Quad(top, color, (x + size / 2) * camera[0] + (y + size) * camera[1]
                + (z + size / 2) * camera[2] + 0.3));
    }

    private static Color shade(Color color, int face) {
        double factor = switch (face) {
            case IvyGrower.UP -> 1.0;
            case IvyGrower.NORTH, IvyGrower.SOUTH -> 0.82;
            case IvyGrower.DOWN -> 0.5;
            default -> 0.66;
        };
        return new Color((int) (color.getRed() * factor), (int) (color.getGreen() * factor),
                (int) (color.getBlue() * factor));
    }

    private static Color vary(Color color, IvyBlock block) {
        int h = (block.x() * 73856093) ^ (block.y() * 19349663) ^ (block.z() * 83492791);
        double factor = 0.88 + ((h >>> 8) & 0xFF) / 255.0 * 0.24;
        return new Color(Math.min(255, (int) (color.getRed() * factor)), Math.min(255, (int) (color.getGreen() * factor)),
                Math.min(255, (int) (color.getBlue() * factor)));
    }
}
