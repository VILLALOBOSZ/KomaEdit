package com.komainu.moxia.brush.paint;

import com.komainu.moxia.blueprint.BlueprintThumbnail;
import com.komainu.moxia.color.BlockColorCatalog;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Dibujos del terreno de prueba para revisar el pincel de pintura a ojo: en isométrico y visto desde arriba. */
final class PaintRender {
    /** La cara de arriba de los bloques que se tiñen con el bioma (su textura medida es gris). */
    private static final Map<String, Integer> TOPS = Map.of("grass_block", 0x7FA843, "moss_block", 0x596E2D);

    private PaintRender() {
    }

    static int color(String id) {
        if (id == null) {
            return -1;
        }
        String base = id.replace("_stairs", "").replace("_slab", "");
        Integer top = TOPS.get(base);
        if (top != null) {
            return top;
        }
        BlockColorCatalog.Measured measured = BlockColorCatalog.measured().get("minecraft:" + base);
        return measured == null ? 0x8A8A8A : measured.rgb() & 0xFFFFFF;
    }

    /** Isométrico con el código de las miniaturas de planos: los bloques con su color medido. */
    static BufferedImage isometric(FakePaintWorld world, int size) {
        int sizeX = world.maximumX() - world.minimumX() + 1;
        int sizeZ = world.maximumZ() - world.minimumZ() + 1;
        int sizeY = world.maximumY() + 1;
        BufferedImage image = BlueprintThumbnail.render(new BlueprintThumbnail.Voxels() {
            @Override
            public int sizeX() {
                return sizeX;
            }

            @Override
            public int sizeY() {
                return sizeY;
            }

            @Override
            public int sizeZ() {
                return sizeZ;
            }

            @Override
            public int color(int x, int y, int z) {
                return PaintRender.color(world.block(x + world.minimumX(), y, z + world.minimumZ()));
            }
        }, size);
        return onBackground(image, new Color(0xCFE3F2));
    }

    /** Desde arriba: el bloque de arriba de cada columna con sombreado de colina desde el noroeste. */
    static BufferedImage topDown(FakePaintWorld world, int scale) {
        int sizeX = world.maximumX() - world.minimumX() + 1;
        int sizeZ = world.maximumZ() - world.minimumZ() + 1;
        BufferedImage image = new BufferedImage(sizeX * scale, sizeZ * scale, BufferedImage.TYPE_INT_RGB);
        for (int z = world.minimumZ(); z <= world.maximumZ(); z++) {
            for (int x = world.minimumX(); x <= world.maximumX(); x++) {
                int top = world.top(x, z);
                int base = top < 0 ? 0x202020 : color(world.block(x, top, z));
                int west = world.top(Math.max(world.minimumX(), x - 1), z);
                int north = world.top(x, Math.max(world.minimumZ(), z - 1));
                double shade = Math.clamp(1.0 + ((top - west) + (top - north)) * 0.07, 0.6, 1.3);
                int rgb = scale(base, shade);
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        boolean edge = scale >= 4 && (dx == 0 || dy == 0);
                        image.setRGB((x - world.minimumX()) * scale + dx, (z - world.minimumZ()) * scale + dy,
                                edge ? scale(rgb, 0.9) : rgb);
                    }
                }
            }
        }
        return image;
    }

    /** Varias imágenes en fila con su rótulo debajo. */
    static BufferedImage sheet(List<BufferedImage> images, List<String> labels, int columns) {
        int cellWidth = images.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
        int cellHeight = images.stream().mapToInt(BufferedImage::getHeight).max().orElse(1) + 30;
        int rows = (images.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(columns * (cellWidth + 12) + 12, rows * (cellHeight + 12) + 12,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(new Color(0x1E2126));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        for (int index = 0; index < images.size(); index++) {
            int left = 12 + (index % columns) * (cellWidth + 12);
            int top = 12 + (index / columns) * (cellHeight + 12);
            graphics.drawImage(images.get(index), left, top, null);
            graphics.setColor(Color.WHITE);
            graphics.drawString(labels.get(index), left + 4, top + cellHeight - 10);
        }
        graphics.dispose();
        return sheet;
    }

    static void save(BufferedImage image, Path file) throws IOException {
        ImageIO.write(image, "png", file.toFile());
    }

    private static BufferedImage onBackground(BufferedImage image, Color background) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setColor(background);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return result;
    }

    private static int scale(int rgb, double factor) {
        int red = Math.clamp((int) Math.round(((rgb >> 16) & 0xFF) * factor), 0, 255);
        int green = Math.clamp((int) Math.round(((rgb >> 8) & 0xFF) * factor), 0, 255);
        int blue = Math.clamp((int) Math.round((rgb & 0xFF) * factor), 0, 255);
        return red << 16 | green << 8 | blue;
    }
}
