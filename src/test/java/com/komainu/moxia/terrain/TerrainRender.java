package com.komainu.moxia.terrain;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Renders de revisión fuera del juego: mapa de alturas sombreado e isométrico de vóxeles. */
final class TerrainRender {
    private TerrainRender() {
    }

    static Color color(String block) {
        String id = block.replace("minecraft:", "");
        return switch (id) {
            case "grass_block" -> new Color(0x6FA34A);
            case "podzol" -> new Color(0x7A5A2E);
            case "coarse_dirt" -> new Color(0x80603E);
            case "dirt" -> new Color(0x8B6440);
            case "mud" -> new Color(0x3D3A3A);
            case "stone" -> new Color(0x8C8C8C);
            case "andesite" -> new Color(0x9A9A98);
            case "tuff" -> new Color(0x6E6E63);
            case "mossy_cobblestone" -> new Color(0x6E7F5A);
            case "snow_block", "powder_snow" -> new Color(0xF2F6F8);
            case "sand" -> new Color(0xDCCB8E);
            case "red_sand" -> new Color(0xB9622B);
            case "sandstone", "smooth_sandstone" -> new Color(0xD3C08A);
            case "gravel" -> new Color(0x857E7B);
            case "clay" -> new Color(0x9EA4B0);
            case "water" -> new Color(0x3A6FD8);
            case "lava" -> new Color(0xE8641C);
            case "basalt", "smooth_basalt" -> new Color(0x4E4E55);
            case "blackstone" -> new Color(0x2D2830);
            case "magma_block" -> new Color(0x8E3B1B);
            case "terracotta" -> new Color(0x985E43);
            case "orange_terracotta" -> new Color(0xA1531D);
            case "yellow_terracotta" -> new Color(0xBA8524);
            case "white_terracotta" -> new Color(0xD1B2A1);
            case "red_terracotta" -> new Color(0x8F3D2E);
            case "brown_terracotta" -> new Color(0x4D3324);
            case "light_gray_terracotta" -> new Color(0x876A61);
            default -> new Color(0xB000B0);
        };
    }

    /** Vista cenital: color del bloque de arriba, sombreado por la luz del noroeste, y el agua encima. */
    static BufferedImage heightmap(FakeTerrain terrain, int scale) {
        int sizeX = terrain.sizeX();
        int sizeZ = terrain.sizeZ();
        int[] tops = new int[sizeX * sizeZ];
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                tops[z * sizeX + x] = terrain.top(x + terrain.minimumX(), z + terrain.minimumZ());
            }
        }
        BufferedImage image = new BufferedImage(sizeX * scale, sizeZ * scale, BufferedImage.TYPE_INT_RGB);
        double lx = -0.5;
        double ly = 0.75;
        double lz = -0.45;
        double length = Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= length;
        ly /= length;
        lz /= length;
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int top = tops[z * sizeX + x];
                int wx = x + terrain.minimumX();
                int wz = z + terrain.minimumZ();
                Color base = top < 0 ? Color.BLACK : color(terrain.blockId(wx, top, wz));
                double east = tops[z * sizeX + Math.min(sizeX - 1, x + 1)];
                double west = tops[z * sizeX + Math.max(0, x - 1)];
                double south = tops[Math.min(sizeZ - 1, z + 1) * sizeX + x];
                double north = tops[Math.max(0, z - 1) * sizeX + x];
                double nx = (west - east) / 2.0;
                double nz = (north - south) / 2.0;
                double norm = Math.sqrt(nx * nx + 1.0 + nz * nz);
                double light = Math.max(0.0, (nx * lx + ly + nz * lz) / norm);
                double shade = 0.35 + 0.75 * light;
                double tint = 0.85 + 0.3 * (top - terrain.minimumY()) / Math.max(1.0, terrain.sizeY());
                Color shaded = scale(base, shade * tint);
                int liquid = terrain.liquidTop(wx, wz);
                if (liquid > top) {
                    Color water = color(terrain.blockId(wx, liquid, wz));
                    double depth = Math.min(1.0, 0.45 + (liquid - top) * 0.12);
                    shaded = mix(shaded, water, depth);
                }
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        image.setRGB(x * scale + dx, z * scale + dy, shaded.getRGB());
                    }
                }
            }
        }
        return image;
    }

    /** Isométrico de vóxeles visto desde el sureste, con caras de distinto tono. */
    static BufferedImage isometric(FakeTerrain terrain, int tile) {
        int sizeX = terrain.sizeX();
        int sizeZ = terrain.sizeZ();
        int half = tile / 2;
        int blockHeight = tile / 2 + tile / 4;
        int rise = blockHeight / 2;
        int width = (sizeX + sizeZ) * half + tile * 4;
        int height = (sizeX + sizeZ) * half / 2 + terrain.sizeY() * rise + tile * 4;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SKY);
        graphics.fillRect(0, 0, width, height);
        int originX = sizeZ * half + tile * 2;
        int originY = terrain.sizeY() * rise + tile * 2;
        for (int sum = 0; sum <= sizeX + sizeZ - 2; sum++) {
            for (int x = 0; x <= sum; x++) {
                int z = sum - x;
                if (x >= sizeX || z >= sizeZ) {
                    continue;
                }
                int wx = x + terrain.minimumX();
                int wz = z + terrain.minimumZ();
                int top = Math.max(terrain.top(wx, wz), terrain.liquidTop(wx, wz));
                if (top < 0) {
                    continue;
                }
                int front = Math.min(
                        x + 1 < sizeX ? Math.max(terrain.top(wx + 1, wz), terrain.liquidTop(wx + 1, wz)) : -1,
                        z + 1 < sizeZ ? Math.max(terrain.top(wx, wz + 1), terrain.liquidTop(wx, wz + 1)) : -1);
                int from = Math.max(terrain.minimumY(), Math.min(top, front + 1) - 1);
                for (int y = from; y <= top; y++) {
                    String block = terrain.blockId(wx, y, wz);
                    if (block.equals("minecraft:air")) {
                        continue;
                    }
                    int screenX = originX + (x - z) * half;
                    int screenY = originY + (x + z) * half / 2 - (y - terrain.minimumY()) * blockHeight / 2;
                    Color base = color(block);
                    boolean liquid = block.endsWith("water") || block.endsWith("lava");
                    if (liquid && y < top) {
                        continue;
                    }
                    drawCube(graphics, screenX, screenY, half, blockHeight / 2, base);
                }
            }
        }
        graphics.dispose();
        return crop(image);
    }

    private static final Color SKY = new Color(0xCFE3F2);

    /** Recorta el cielo sobrante alrededor del dibujo. */
    private static BufferedImage crop(BufferedImage image) {
        int sky = SKY.getRGB();
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != sky) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return image;
        }
        int margin = 6;
        int left = Math.max(0, minX - margin);
        int top = Math.max(0, minY - margin);
        int right = Math.min(image.getWidth() - 1, maxX + margin);
        int bottom = Math.min(image.getHeight() - 1, maxY + margin);
        return image.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }

    private static void drawCube(Graphics2D graphics, int x, int y, int half, int rise, Color base) {
        int quarter = half / 2;
        Polygon top = new Polygon(new int[]{x, x + half, x, x - half}, new int[]{y - quarter, y, y + quarter, y},
                4);
        Polygon left = new Polygon(new int[]{x - half, x, x, x - half},
                new int[]{y, y + quarter, y + quarter + rise, y + rise}, 4);
        Polygon right = new Polygon(new int[]{x, x + half, x + half, x},
                new int[]{y + quarter, y, y + rise, y + quarter + rise}, 4);
        graphics.setColor(scale(base, 0.78));
        graphics.fillPolygon(left);
        graphics.setColor(scale(base, 0.62));
        graphics.fillPolygon(right);
        graphics.setColor(base);
        graphics.fillPolygon(top);
    }

    /** Une imágenes en fila con su rótulo debajo. */
    static BufferedImage row(List<BufferedImage> images, List<String> labels) {
        int gap = 8;
        int labelHeight = 18;
        int width = gap;
        int height = 0;
        for (BufferedImage image : images) {
            width += image.getWidth() + gap;
            height = Math.max(height, image.getHeight());
        }
        BufferedImage result = new BufferedImage(width, height + labelHeight + gap * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(new Color(0x20242A));
        graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        int x = gap;
        for (int index = 0; index < images.size(); index++) {
            BufferedImage image = images.get(index);
            graphics.drawImage(image, x, gap, null);
            graphics.setColor(Color.WHITE);
            graphics.drawString(labels.get(index), x + 2, gap + height + labelHeight);
            x += image.getWidth() + gap;
        }
        graphics.dispose();
        return result;
    }

    static void save(BufferedImage image, Path file) throws IOException {
        ImageIO.write(image, "png", file.toFile());
    }

    private static Color scale(Color color, double factor) {
        return new Color(clamp(color.getRed() * factor), clamp(color.getGreen() * factor),
                clamp(color.getBlue() * factor));
    }

    private static Color mix(Color a, Color b, double amount) {
        return new Color(clamp(a.getRed() + (b.getRed() - a.getRed()) * amount),
                clamp(a.getGreen() + (b.getGreen() - a.getGreen()) * amount),
                clamp(a.getBlue() + (b.getBlue() - a.getBlue()) * amount));
    }

    private static int clamp(double value) {
        return (int) Math.clamp(Math.round(value), 0, 255);
    }
}
