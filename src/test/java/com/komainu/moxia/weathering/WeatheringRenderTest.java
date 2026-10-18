package com.komainu.moxia.weathering;

import com.komainu.moxia.weathering.RuinWorld.Scene;
import com.komainu.moxia.weathering.WeatheringEngine.Box;
import com.komainu.moxia.weathering.WeatheringEngine.Result;
import com.komainu.moxia.weathering.WeatheringEngine.Selection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders isométricos por vóxeles de Envejecer, fuera del juego. Solo se ejecuta con
 * {@code KOMAEDIT_WEATHER_RENDER=<carpeta>}: deja una hoja por obra (casa, torre y muro) con el original y el
 * resultado a 30, 60 y 100 de intensidad, vistos desde dos esquinas opuestas.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_WEATHER_RENDER", matches = ".+")
class WeatheringRenderTest {
    private static final int TILE = 9;
    private static final int[] INTENSITIES = {0, 30, 60, 100};
    private static final long SEED = 20261018L;

    @Test
    void rendersTheTestBuildings() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_WEATHER_RENDER"));
        Files.createDirectories(folder);
        String style = System.getenv().getOrDefault("KOMAEDIT_WEATHER_STYLE", "templado");
        StringBuilder heights = new StringBuilder();
        for (Scene scene : List.of(RuinWorld.casa(), RuinWorld.torre(), RuinWorld.muro())) {
            List<BufferedImage> columns = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int intensity : INTENSITIES) {
                RuinWorld world = scene.world();
                String label = "original";
                if (intensity > 0) {
                    WeatheringSettings settings = WeatheringSettings.parse(intensity + " " + style + " -seed " + SEED);
                    long start = System.nanoTime();
                    Result result = WeatheringEngine.weather(world, scene.box(), Selection.all(), settings);
                    world = world.apply(result);
                    label = "intensidad " + intensity + " · " + result.changes().size() + " cambios · "
                            + (System.nanoTime() - start) / 1_000_000 + " ms";
                }
                heights.append(scene.name()).append(' ').append(label).append(System.lineSeparator())
                        .append(heightMap(world, scene.box())).append(System.lineSeparator());
                BufferedImage front = render(world, scene.box(), false);
                BufferedImage back = render(world, scene.box(), true);
                BufferedImage pair = new BufferedImage(Math.max(front.getWidth(), back.getWidth()),
                        front.getHeight() + back.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = pair.createGraphics();
                graphics.drawImage(front, 0, 0, null);
                graphics.drawImage(back, 0, front.getHeight(), null);
                graphics.dispose();
                columns.add(pair);
                labels.add(label);
            }
            int width = columns.stream().mapToInt(BufferedImage::getWidth).sum();
            int height = columns.stream().mapToInt(BufferedImage::getHeight).max().orElse(1) + 24;
            BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = sheet.createGraphics();
            graphics.setColor(new Color(206, 222, 236));
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            int x = 0;
            for (int index = 0; index < columns.size(); index++) {
                graphics.drawImage(columns.get(index), x, 24, null);
                graphics.setColor(Color.BLACK);
                graphics.drawString(scene.name() + " · " + labels.get(index), x + 8, 16);
                x += columns.get(index).getWidth();
            }
            graphics.dispose();
            ImageIO.write(sheet, "png", folder.resolve("envejecer_" + scene.name() + "_" + style + ".png").toFile());
        }
        Files.writeString(folder.resolve("alturas_" + style + ".txt"), heights);
    }

    /**
     * Planta con la altura de obra que queda en cada columna (la más alta que no sea escombro ni planta), en base 36,
     * con «.» donde no hay nada y «#» donde solo hay escombro.
     */
    private static String heightMap(RuinWorld world, Box box) {
        StringBuilder text = new StringBuilder();
        for (int z = box.minZ(); z <= box.maxZ(); z++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                int top = 0;
                for (int y = box.maxY(); y >= box.minY(); y--) {
                    BlockText block = BlockText.parse(world.get(x, y, z));
                    if (AgingTable.kind(block) == AgingTable.Kind.BUILDING) {
                        top = y - box.minY() + 1;
                        break;
                    }
                }
                text.append(top == 0 ? '.' : Character.forDigit(Math.min(35, top), 36));
            }
            text.append(System.lineSeparator());
        }
        return text.toString();
    }

    /** Vista isométrica desde la esquina +x +z (o desde la opuesta con {@code back}), pintada de atrás adelante. */
    private static BufferedImage render(RuinWorld world, Box box, boolean back) {
        int minX = box.minX() - 1;
        int maxX = box.maxX() + 1;
        int minZ = box.minZ() - 1;
        int maxZ = box.maxZ() + 1;
        int minY = 0;
        int maxY = box.maxY();
        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        int sizeY = maxY - minY + 1;
        int width = (sizeX + sizeZ) * TILE + 2 * TILE;
        int height = (sizeX + sizeZ) * TILE / 2 + sizeY * TILE + 3 * TILE;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(206, 222, 236));
        graphics.fillRect(0, 0, width, height);
        Renderer renderer = new Renderer(graphics, sizeZ * TILE + TILE, sizeY * TILE + TILE);
        // De lejos a cerca: suma de coordenadas de vista creciente; dentro, de abajo arriba.
        for (int sum = 0; sum <= sizeX + sizeZ - 2; sum++) {
            for (int y = minY; y <= maxY; y++) {
                for (int u = 0; u < sizeX; u++) {
                    int w = sum - u;
                    if (w < 0 || w >= sizeZ) {
                        continue;
                    }
                    int wx = back ? maxX - u : minX + u;
                    int wz = back ? maxZ - w : minZ + w;
                    String state = world.get(wx, y, wz);
                    if (state.equals("minecraft:air")) {
                        continue;
                    }
                    renderer.block(BlockText.parse(state), u, y - minY, w, back, wx, y, wz);
                }
            }
        }
        graphics.dispose();
        return image;
    }

    private static final class Renderer {
        private final Graphics2D graphics;
        private final int originX;
        private final int originY;

        Renderer(Graphics2D graphics, int originX, int originY) {
            this.graphics = graphics;
            this.originX = originX;
            this.originY = originY;
        }

        void block(BlockText block, int u, int y, int w, boolean back, int wx, int wy, int wz) {
            String path = block.path();
            Color color = vary(color(path), wx, wy, wz);
            float alpha = path.contains("glass") || path.equals("water") || path.equals("cobweb") ? 0.45f : 1f;
            if (path.endsWith("_slab")) {
                boolean top = "top".equals(block.property("type"));
                boolean full = "double".equals(block.property("type"));
                box(u, y + (top ? 0.5 : 0), w, 1, full ? 1 : 0.5, 1, color, alpha);
            } else if (path.endsWith("_stairs")) {
                box(u, y, w, 1, 0.5, 1, color, alpha);
                double[] half = stairHalf(block.property("facing"), back);
                box(u + half[0], y + 0.5, w + half[1], half[2], 0.5, half[3], color, alpha);
            } else if (path.equals("snow")) {
                int layers = Integer.parseInt(block.properties().getOrDefault("layers", "1"));
                box(u, y, w, 1, layers / 8.0, 1, color, alpha);
            } else if (path.endsWith("carpet")) {
                box(u, y, w, 1, 0.08, 1, color, alpha);
            } else if (path.endsWith("_pane") || path.endsWith("_bars")) {
                box(u + 0.4, y, w, 0.2, 1, 1, color, alpha);
                box(u, y, w + 0.4, 1, 1, 0.2, color, alpha);
            } else if (path.endsWith("_wall") || path.endsWith("_fence")) {
                box(u + 0.25, y, w + 0.25, 0.5, 1, 0.5, color, alpha);
            } else if (path.equals("vine")) {
                vine(block, u, y, w, back, color);
            } else if (path.contains("torch")) {
                box(u + 0.42, y + 0.1, w + 0.42, 0.16, 0.6, 0.16, color, alpha);
            } else if (AgingTable.kind(block) == AgingTable.Kind.DECOR && !path.equals("cobweb")) {
                box(u + 0.25, y, w + 0.25, 0.5, path.contains("roots") ? 0.8 : 0.6, 0.5, color, alpha);
            } else if (path.equals("water")) {
                box(u, y, w, 1, 0.88, 1, color, alpha);
            } else {
                box(u, y, w, 1, 1, 1, color, alpha);
            }
        }

        private void vine(BlockText block, int u, int y, int w, boolean back, Color color) {
            // Lado del bloque en el que está la enredadera, en coordenadas de vista.
            boolean north = "true".equals(block.property("north"));
            boolean south = "true".equals(block.property("south"));
            boolean east = "true".equals(block.property("east"));
            boolean west = "true".equals(block.property("west"));
            if (back) {
                boolean swap = north;
                north = south;
                south = swap;
                swap = east;
                east = west;
                west = swap;
            }
            if (north) {
                box(u, y, w, 1, 1, 0.1, color, 1f);
            }
            if (south) {
                box(u, y, w + 0.9, 1, 1, 0.1, color, 1f);
            }
            if (west) {
                box(u, y, w, 0.1, 1, 1, color, 1f);
            }
            if (east) {
                box(u + 0.9, y, w, 0.1, 1, 1, color, 1f);
            }
        }

        /** Parte alta de la escalera: {dx, dz, ancho, fondo} en coordenadas de vista. */
        private static double[] stairHalf(String facing, boolean back) {
            String side = facing == null ? "north" : facing;
            if (back) {
                side = switch (side) {
                    case "north" -> "south";
                    case "south" -> "north";
                    case "east" -> "west";
                    default -> "east";
                };
            }
            return switch (side) {
                case "south" -> new double[]{0, 0.5, 1, 0.5};
                case "east" -> new double[]{0.5, 0, 0.5, 1};
                case "west" -> new double[]{0, 0, 0.5, 1};
                default -> new double[]{0, 0, 1, 0.5};
            };
        }

        /** Caja de vista (u = x, w = z) con sus caras de arriba, +u y +w. */
        private void box(double u, double y, double w, double du, double dy, double dw, Color color, float alpha) {
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            double u1 = u + du;
            double y1 = y + dy;
            double w1 = w + dw;
            fill(new double[][]{{u, y1, w}, {u1, y1, w}, {u1, y1, w1}, {u, y1, w1}}, color);
            fill(new double[][]{{u1, y, w}, {u1, y1, w}, {u1, y1, w1}, {u1, y, w1}}, scale(color, 0.68));
            fill(new double[][]{{u, y, w1}, {u1, y, w1}, {u1, y1, w1}, {u, y1, w1}}, scale(color, 0.84));
            graphics.setComposite(AlphaComposite.SrcOver);
        }

        private void fill(double[][] points, Color color) {
            Polygon polygon = new Polygon();
            for (double[] point : points) {
                double sx = (point[0] - point[2]) * TILE;
                double sy = (point[0] + point[2]) * TILE * 0.5 - point[1] * TILE;
                polygon.addPoint((int) Math.round(originX + sx), (int) Math.round(originY + sy));
            }
            graphics.setColor(color);
            graphics.fillPolygon(polygon);
            graphics.setColor(scale(color, 0.82));
            graphics.drawPolygon(polygon);
        }
    }

    private static Color color(String path) {
        if (path.startsWith("mossy_") || path.contains("_mossy_")) {
            return path.contains("cobble") ? new Color(104, 124, 88) : new Color(110, 132, 96);
        }
        if (path.startsWith("cracked_")) {
            return new Color(112, 110, 106);
        }
        if (path.contains("stripped_")) {
            return new Color(178, 144, 90);
        }
        if (path.contains("oak_log") || path.endsWith("_wood")) {
            return new Color(104, 82, 50);
        }
        if (path.contains("spruce")) {
            return new Color(116, 86, 52);
        }
        if (path.contains("oak") || path.contains("planks")) {
            return new Color(168, 136, 82);
        }
        if (path.contains("stone_brick")) {
            return new Color(138, 136, 134);
        }
        if (path.contains("cobble")) {
            return new Color(118, 118, 118);
        }
        if (path.equals("gravel")) {
            return new Color(132, 122, 118);
        }
        if (path.contains("andesite")) {
            return new Color(146, 146, 148);
        }
        if (path.contains("glass")) {
            return new Color(190, 228, 240);
        }
        if (path.equals("grass_block")) {
            return new Color(108, 150, 72);
        }
        if (path.equals("dirt")) {
            return new Color(124, 90, 60);
        }
        if (path.equals("moss_block") || path.equals("moss_carpet")) {
            return new Color(88, 128, 44);
        }
        if (path.equals("vine")) {
            return new Color(46, 104, 34);
        }
        if (path.contains("grass") || path.contains("fern") || path.contains("roots")) {
            return new Color(84, 140, 52);
        }
        if (path.equals("cobweb")) {
            return new Color(240, 240, 240);
        }
        if (path.contains("torch")) {
            return new Color(240, 190, 60);
        }
        if (path.contains("sand")) {
            return new Color(220, 206, 150);
        }
        if (path.contains("snow")) {
            return new Color(246, 250, 252);
        }
        if (path.equals("water")) {
            return new Color(60, 100, 200);
        }
        if (path.contains("ice")) {
            return new Color(160, 190, 240);
        }
        if (path.contains("copper")) {
            return path.contains("oxidized") ? new Color(80, 160, 130) : new Color(190, 110, 80);
        }
        return new Color(150, 150, 150);
    }

    private static Color vary(Color color, int x, int y, int z) {
        int hash = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        return scale(color, 0.92 + ((hash >>> 8) & 0xFF) / 255.0 * 0.16);
    }

    private static Color scale(Color color, double factor) {
        return new Color(Math.min(255, (int) (color.getRed() * factor)), Math.min(255, (int) (color.getGreen() * factor)),
                Math.min(255, (int) (color.getBlue() * factor)));
    }
}
