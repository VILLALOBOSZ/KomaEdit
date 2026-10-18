package com.komainu.moxia.shape.smooth;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Render isométrico de pruebas con las piezas tal cual: losas y escaleras por octantes, capas y muros con su caja.
 * Pinta de atrás adelante (pintor) y solo los bloques con alguna cara al aire. Fuera del juego, sin Minecraft.
 */
public final class PieceRender {
    private static final double COS = Math.cos(Math.toRadians(30));
    private static final double SIN = Math.sin(Math.toRadians(30));

    private PieceRender() {
    }

    /** Un bloque del render: su pieza y su color {@code 0xRRGGBB}. */
    public record Cell(byte piece, int color) {
    }

    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {
        double depth() {
            return (minX + maxX) + (minY + maxY) + (minZ + maxZ);
        }
    }

    public static BufferedImage render(Long2ObjectOpenHashMap<Cell> cells, int scale) {
        List<Box> boxes = new ArrayList<>();
        double minScreenX = Double.POSITIVE_INFINITY;
        double maxScreenX = Double.NEGATIVE_INFINITY;
        double minScreenY = Double.POSITIVE_INFINITY;
        double maxScreenY = Double.NEGATIVE_INFINITY;
        for (var entry : cells.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            Cell cell = entry.getValue();
            if (cell.piece() == SmoothPiece.FULL && hidden(cells, x, y, z)) {
                continue;
            }
            addBoxes(boxes, x, y, z, cell);
            for (int corner = 0; corner < 8; corner++) {
                double[] point = project(x + (corner & 1), y + (corner >> 1 & 1), z + (corner >> 2 & 1), scale);
                minScreenX = Math.min(minScreenX, point[0]);
                maxScreenX = Math.max(maxScreenX, point[0]);
                minScreenY = Math.min(minScreenY, point[1]);
                maxScreenY = Math.max(maxScreenY, point[1]);
            }
        }
        if (boxes.isEmpty()) {
            return new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        }
        int width = (int) Math.ceil(maxScreenX - minScreenX) + 20;
        int height = (int) Math.ceil(maxScreenY - minScreenY) + 20;
        double offsetX = 10 - minScreenX;
        double offsetY = 10 - minScreenY;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xE9EEF2));
        graphics.fillRect(0, 0, width, height);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boxes.sort(Comparator.comparingDouble(Box::depth));
        for (Box box : boxes) {
            Color color = new Color(box.color());
            // Arriba, este (+x) y sur (+z): las tres caras que ve la cámara.
            face(graphics, color, 1.0, offsetX, offsetY, scale, new double[][]{
                    {box.minX(), box.maxY(), box.minZ()}, {box.maxX(), box.maxY(), box.minZ()},
                    {box.maxX(), box.maxY(), box.maxZ()}, {box.minX(), box.maxY(), box.maxZ()}});
            face(graphics, color, 0.78, offsetX, offsetY, scale, new double[][]{
                    {box.maxX(), box.minY(), box.minZ()}, {box.maxX(), box.maxY(), box.minZ()},
                    {box.maxX(), box.maxY(), box.maxZ()}, {box.maxX(), box.minY(), box.maxZ()}});
            face(graphics, color, 0.6, offsetX, offsetY, scale, new double[][]{
                    {box.minX(), box.minY(), box.maxZ()}, {box.maxX(), box.minY(), box.maxZ()},
                    {box.maxX(), box.maxY(), box.maxZ()}, {box.minX(), box.maxY(), box.maxZ()}});
        }
        graphics.dispose();
        return image;
    }

    /** Hoja con varias imágenes y su rótulo encima, en {@code columns} columnas. */
    public static BufferedImage sheet(List<BufferedImage> images, List<String> labels, int columns) {
        int cellWidth = 0;
        int cellHeight = 0;
        for (BufferedImage image : images) {
            cellWidth = Math.max(cellWidth, image.getWidth());
            cellHeight = Math.max(cellHeight, image.getHeight());
        }
        cellHeight += 24;
        int rows = (images.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(cellWidth * columns, cellHeight * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(0xF4F1EA));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        for (int index = 0; index < images.size(); index++) {
            int x = index % columns * cellWidth;
            int y = index / columns * cellHeight;
            BufferedImage image = images.get(index);
            graphics.drawImage(image, x + (cellWidth - image.getWidth()) / 2, y + 22, null);
            graphics.setColor(new Color(0x303030));
            graphics.drawString(labels.get(index), x + 6, y + 16);
            graphics.setColor(new Color(0xCCCCCC));
            graphics.drawRect(x, y, cellWidth - 1, cellHeight - 1);
        }
        graphics.dispose();
        return sheet;
    }

    private static boolean hidden(Long2ObjectOpenHashMap<Cell> cells, int x, int y, int z) {
        return full(cells, x, y + 1, z) && full(cells, x + 1, y, z) && full(cells, x, y, z + 1);
    }

    private static boolean full(Long2ObjectOpenHashMap<Cell> cells, int x, int y, int z) {
        Cell cell = cells.get(PackedBlockPlan.pack(x, y, z));
        return cell != null && cell.piece() == SmoothPiece.FULL;
    }

    private static void addBoxes(List<Box> boxes, int x, int y, int z, Cell cell) {
        byte piece = cell.piece();
        int[] box = SmoothPiece.box(piece);
        if (box != null) {
            boxes.add(new Box(x + box[0] / 16.0, y + box[1] / 16.0, z + box[2] / 16.0, x + box[3] / 16.0,
                    y + box[4] / 16.0, z + box[5] / 16.0, cell.color()));
            return;
        }
        if (piece == SmoothPiece.FULL) {
            boxes.add(new Box(x, y, z, x + 1, y + 1, z + 1, cell.color()));
            return;
        }
        int mask = SmoothPiece.mask(piece);
        for (int octant = 0; octant < 8; octant++) {
            if ((mask & 1 << octant) == 0) {
                continue;
            }
            double ox = x + 0.5 * (octant & 1);
            double oy = y + 0.5 * (octant >> 1 & 1);
            double oz = z + 0.5 * (octant >> 2 & 1);
            boxes.add(new Box(ox, oy, oz, ox + 0.5, oy + 0.5, oz + 0.5, cell.color()));
        }
    }

    private static void face(Graphics2D graphics, Color color, double shade, double offsetX, double offsetY,
                             int scale, double[][] corners) {
        Polygon polygon = new Polygon();
        for (double[] corner : corners) {
            double[] point = project(corner[0], corner[1], corner[2], scale);
            polygon.addPoint((int) Math.round(point[0] + offsetX), (int) Math.round(point[1] + offsetY));
        }
        graphics.setColor(new Color((int) (color.getRed() * shade), (int) (color.getGreen() * shade),
                (int) (color.getBlue() * shade)));
        graphics.fillPolygon(polygon);
        graphics.setColor(new Color(0, 0, 0, 38));
        graphics.drawPolygon(polygon);
    }

    private static double[] project(double x, double y, double z, int scale) {
        return new double[]{(x - z) * COS * scale, (x + z) * SIN * scale - y * scale};
    }
}
