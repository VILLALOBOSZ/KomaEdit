package com.komainu.moxia.nature;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Map;

/**
 * Pintor de vóxeles para los renders de comprobación: vista isométrica con sombreado por cara y oclusión simple, y
 * alzado frontal con niebla de profundidad para juzgar la silueta. Solo se usa en pruebas.
 */
final class VoxelRender {
    static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("oak_log", 0x66513A), Map.entry("spruce_log", 0x3E2914), Map.entry("birch_log", 0xD8D5CC),
            Map.entry("jungle_log", 0x56441C), Map.entry("acacia_log", 0x68625A), Map.entry("cherry_log", 0x3A1E26),
            Map.entry("dark_oak_log", 0x3E2D1A), Map.entry("mangrove_log", 0x54412B),
            Map.entry("stripped_oak_log", 0xB18F57), Map.entry("oak_wood", 0x66513A),
            Map.entry("oak_leaves", 0x3F7A22), Map.entry("spruce_leaves", 0x3A5E3A), Map.entry("birch_leaves", 0x6A8F3E),
            Map.entry("jungle_leaves", 0x3A8A1A), Map.entry("acacia_leaves", 0x5E7E22),
            Map.entry("cherry_leaves", 0xE8A9C6), Map.entry("dark_oak_leaves", 0x2F6416),
            Map.entry("azalea_leaves", 0x657D2C), Map.entry("flowering_azalea_leaves", 0x8D7690),
            Map.entry("mangrove_leaves", 0x4E8A22),
            Map.entry("deepslate", 0x505053), Map.entry("tuff", 0x6C6D66), Map.entry("cobblestone", 0x7A7A7A),
            Map.entry("stone", 0x7E7E7E), Map.entry("andesite", 0x888889), Map.entry("moss_block", 0x596E2C),
            Map.entry("grass", 0x5E9A3C), Map.entry("dirt", 0x866043), Map.entry("granite", 0x956756),
            Map.entry("calcite", 0xDFE0DC), Map.entry("diorite", 0xBCBCBD), Map.entry("gravel", 0x837F7E),
            Map.entry("mossy_cobblestone", 0x6E7A5E), Map.entry("sandstone", 0xD8CB9B)
    );
    private static final int SKY = 0xEEF2F5;

    private final Long2IntOpenHashMap cells = new Long2IntOpenHashMap();
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private int maxZ = Integer.MIN_VALUE;

    VoxelRender() {
        cells.defaultReturnValue(-1);
    }

    static int color(String block) {
        String id = block.contains(":") ? block.substring(block.indexOf(':') + 1) : block;
        int bracket = id.indexOf('[');
        if (bracket >= 0) {
            id = id.substring(0, bracket);
        }
        return COLORS.getOrDefault(id, 0x999999);
    }

    void put(int x, int y, int z, int rgb) {
        cells.put(PackedBlockPlan.pack(x, y, z), rgb);
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
    }

    boolean filled(int x, int y, int z) {
        return cells.containsKey(PackedBlockPlan.pack(x, y, z));
    }

    /** Vista isométrica desde (+x, +y, +z). */
    BufferedImage iso(double scale, String title) {
        double cos = 0.866 * scale;
        double half = 0.5 * scale;
        double left = (minX - maxZ - 1) * cos;
        double right = (maxX + 1 - minZ) * cos;
        double top = (minX + minZ) * half - (maxY + 1) * scale;
        double bottom = (maxX + maxZ + 2) * half - minY * scale;
        int width = (int) Math.ceil(right - left) + 40;
        int height = (int) Math.ceil(bottom - top) + 60;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = start(image, title);
        double originX = 20 - left;
        double originY = 40 - top;
        long[] keys = cells.keySet().toLongArray();
        Arrays.sort(keys);
        long[] order = Arrays.stream(keys).boxed().sorted((first, second) -> Integer.compare(sum(first), sum(second)))
                .mapToLong(Long::longValue).toArray();
        for (long key : order) {
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            // Luz del cielo: cada bloque encima la apaga un poco, como las hojas en Minecraft.
            int rgb = scaleColor(vary(cells.get(key), key), 1.0 - 0.07 * Math.min(6, above(x, y, z)));
            if (!filled(x, y + 1, z)) {
                double shade = 1.0 - 0.07 * (count(x + 1, y + 1, z) + count(x - 1, y + 1, z) + count(x, y + 1, z + 1)
                        + count(x, y + 1, z - 1));
                face(graphics, originX, originY, cos, half, scale, rgb, shade,
                        new double[][]{{x, y + 1, z}, {x + 1, y + 1, z}, {x + 1, y + 1, z + 1}, {x, y + 1, z + 1}});
            }
            if (!filled(x + 1, y, z)) {
                double shade = 0.8 - 0.06 * (count(x + 1, y + 1, z) + count(x + 1, y, z + 1) + count(x + 1, y, z - 1));
                face(graphics, originX, originY, cos, half, scale, rgb, shade,
                        new double[][]{{x + 1, y, z}, {x + 1, y + 1, z}, {x + 1, y + 1, z + 1}, {x + 1, y, z + 1}});
            }
            if (!filled(x, y, z + 1)) {
                double shade = 0.64 - 0.05 * (count(x, y + 1, z + 1) + count(x + 1, y, z + 1) + count(x - 1, y, z + 1));
                face(graphics, originX, originY, cos, half, scale, rgb, shade,
                        new double[][]{{x, y, z + 1}, {x + 1, y, z + 1}, {x + 1, y + 1, z + 1}, {x, y + 1, z + 1}});
            }
        }
        graphics.dispose();
        return image;
    }

    /** Alzado desde -z: cada columna muestra el bloque más cercano, más claro cuanto más lejos. */
    BufferedImage front(double scale, String title) {
        int width = (int) Math.ceil((maxX - minX + 1) * scale) + 40;
        int height = (int) Math.ceil((maxY - minY + 1) * scale) + 60;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = start(image, title);
        double depth = Math.max(1, maxZ - minZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    long key = PackedBlockPlan.pack(x, y, z);
                    int rgb = cells.get(key);
                    if (rgb < 0) {
                        continue;
                    }
                    double fog = (z - minZ) / depth * 0.5;
                    double shade = filled(x, y + 1, z) ? 0.86 : 1.0;
                    graphics.setColor(new Color(mix(scaleColor(vary(rgb, key), shade), SKY, fog)));
                    int px = (int) Math.round(20 + (x - minX) * scale);
                    int py = (int) Math.round(40 + (maxY - y) * scale);
                    graphics.fillRect(px, py, (int) Math.ceil(scale), (int) Math.ceil(scale));
                    break;
                }
            }
        }
        graphics.dispose();
        return image;
    }

    static BufferedImage beside(BufferedImage... images) {
        int width = 0;
        int height = 0;
        for (BufferedImage image : images) {
            width += image.getWidth();
            height = Math.max(height, image.getHeight());
        }
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(SKY));
        graphics.fillRect(0, 0, width, height);
        int x = 0;
        for (BufferedImage image : images) {
            graphics.drawImage(image, x, 0, null);
            x += image.getWidth();
        }
        graphics.dispose();
        return sheet;
    }

    /** Rejilla de imágenes de {@code columns} columnas. */
    static BufferedImage grid(int columns, BufferedImage... images) {
        int cellWidth = 0;
        int cellHeight = 0;
        for (BufferedImage image : images) {
            cellWidth = Math.max(cellWidth, image.getWidth());
            cellHeight = Math.max(cellHeight, image.getHeight());
        }
        int rows = (images.length + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(cellWidth * Math.min(columns, images.length), cellHeight * rows,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(SKY));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int index = 0; index < images.length; index++) {
            BufferedImage image = images[index];
            int x = (index % columns) * cellWidth + (cellWidth - image.getWidth()) / 2;
            int y = (index / columns) * cellHeight + (cellHeight - image.getHeight());
            graphics.drawImage(image, x, y, null);
        }
        graphics.dispose();
        return sheet;
    }

    private static Graphics2D start(BufferedImage image, String title) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setColor(new Color(SKY));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(0x30343A));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        graphics.drawString(title, 12, 22);
        return graphics;
    }

    private int above(int x, int y, int z) {
        int count = 0;
        for (int dy = 1; dy <= 8; dy++) {
            count += count(x, y + dy, z);
        }
        return count;
    }

    private int count(int x, int y, int z) {
        return filled(x, y, z) ? 1 : 0;
    }

    private static int sum(long key) {
        return PackedBlockPlan.unpackX(key) + PackedBlockPlan.unpackY(key) + PackedBlockPlan.unpackZ(key);
    }

    private static void face(Graphics2D graphics, double originX, double originY, double cos, double half,
                             double scale, int rgb, double shade, double[][] corners) {
        Polygon polygon = new Polygon();
        for (double[] corner : corners) {
            polygon.addPoint((int) Math.round(originX + (corner[0] - corner[2]) * cos),
                    (int) Math.round(originY + (corner[0] + corner[2]) * half - corner[1] * scale));
        }
        graphics.setColor(new Color(scaleColor(rgb, shade)));
        graphics.fillPolygon(polygon);
        graphics.drawPolygon(polygon);
    }

    private static int vary(int rgb, long key) {
        double amount = 0.92 + 0.16 * TreeBuilder.unit(key, 99L);
        return scaleColor(rgb, amount);
    }

    static int scaleColor(int rgb, double factor) {
        int red = (int) Math.clamp(((rgb >> 16) & 0xFF) * factor, 0, 255);
        int green = (int) Math.clamp(((rgb >> 8) & 0xFF) * factor, 0, 255);
        int blue = (int) Math.clamp((rgb & 0xFF) * factor, 0, 255);
        return red << 16 | green << 8 | blue;
    }

    private static int mix(int first, int second, double amount) {
        int red = (int) (((first >> 16) & 0xFF) * (1 - amount) + ((second >> 16) & 0xFF) * amount);
        int green = (int) (((first >> 8) & 0xFF) * (1 - amount) + ((second >> 8) & 0xFF) * amount);
        int blue = (int) ((first & 0xFF) * (1 - amount) + (second & 0xFF) * amount);
        return red << 16 | green << 8 | blue;
    }
}
