package com.komainu.moxia.color;

import com.komainu.moxia.shape.smooth.SmoothPiece;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Render isométrico con texturas para los renders de comprobación del sombreado: la cámara mira desde el sureste y
 * arriba (ve las caras de arriba, este y sur), y cada cara se oscurece como en Minecraft (arriba 100 %, sur 80 %,
 * este 60 %). Las escaleras y losas se pintan por octantes, como en el render de smoothblocks.
 */
final class VoxelRender {
    private static final double COS = Math.cos(Math.toRadians(30));
    private static final double SIN = Math.sin(Math.toRadians(30));

    private VoxelRender() {
    }

    /** Un bloque: su id, su pieza ({@link SmoothPiece}) y su color medio por si no hay textura. */
    record Cell(String id, byte piece, int color) {
    }

    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Cell cell) {
        double depth() {
            return minX + maxX + minY + maxY + minZ + maxZ;
        }
    }

    static BufferedImage render(Map<Long, Cell> cells, BlockTextures textures, int scale) {
        List<Box> boxes = new ArrayList<>();
        double minScreenX = Double.POSITIVE_INFINITY;
        double maxScreenX = Double.NEGATIVE_INFINITY;
        double minScreenY = Double.POSITIVE_INFINITY;
        double maxScreenY = Double.NEGATIVE_INFINITY;
        for (Map.Entry<Long, Cell> entry : cells.entrySet()) {
            int[] position = unpack(entry.getKey());
            int x = position[0];
            int y = position[1];
            int z = position[2];
            if (full(cells, x, y + 1, z) && full(cells, x + 1, y, z) && full(cells, x, y, z + 1)
                    && entry.getValue().piece() == SmoothPiece.FULL) {
                continue;
            }
            addBoxes(boxes, x, y, z, entry.getValue());
            for (int corner = 0; corner < 8; corner++) {
                double[] point = project(x + (corner & 1), y + (corner >> 1 & 1), z + (corner >> 2 & 1), scale);
                minScreenX = Math.min(minScreenX, point[0]);
                maxScreenX = Math.max(maxScreenX, point[0]);
                minScreenY = Math.min(minScreenY, point[1]);
                maxScreenY = Math.max(maxScreenY, point[1]);
            }
        }
        int width = (int) Math.ceil(maxScreenX - minScreenX) + 24;
        int height = (int) Math.ceil(maxScreenY - minScreenY) + 24;
        double offsetX = 12 - minScreenX;
        double offsetY = 12 - minScreenY;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xDCE6EE));
        graphics.fillRect(0, 0, width, height);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        boxes.sort(Comparator.comparingDouble(Box::depth));
        for (Box box : boxes) {
            BufferedImage[] faces = textures.faces(box.cell().id(), box.cell().color());
            // Posición dentro del bloque, para recortar la parte de la textura que toca a cada octante.
            double bx = Math.floor(box.minX() + 1.0E-9);
            double by = Math.floor(box.minY() + 1.0E-9);
            double bz = Math.floor(box.minZ() + 1.0E-9);
            // Arriba: u a lo largo de x, v a lo largo de z.
            face(graphics, faces[0], 1.0, box.minX(), box.maxY(), box.minZ(), new double[]{box.maxX() - box.minX(), 0, 0},
                    new double[]{0, 0, box.maxZ() - box.minZ()}, box.minX() - bx, box.minZ() - bz,
                    box.maxX() - box.minX(), box.maxZ() - box.minZ(), offsetX, offsetY, scale, box.cell().color());
            // Este (+x): u a lo largo de z, v hacia abajo.
            face(graphics, faces[1], 0.6, box.maxX(), box.maxY(), box.minZ(), new double[]{0, 0, box.maxZ() - box.minZ()},
                    new double[]{0, -(box.maxY() - box.minY()), 0}, box.minZ() - bz, 1.0 - (box.maxY() - by),
                    box.maxZ() - box.minZ(), box.maxY() - box.minY(), offsetX, offsetY, scale, box.cell().color());
            // Sur (+z): u a lo largo de x, v hacia abajo.
            face(graphics, faces[1], 0.8, box.minX(), box.maxY(), box.maxZ(), new double[]{box.maxX() - box.minX(), 0, 0},
                    new double[]{0, -(box.maxY() - box.minY()), 0}, box.minX() - bx, 1.0 - (box.maxY() - by),
                    box.maxX() - box.minX(), box.maxY() - box.minY(), offsetX, offsetY, scale, box.cell().color());
        }
        graphics.dispose();
        return image;
    }

    private static void face(Graphics2D graphics, BufferedImage texture, double shade, double ox, double oy, double oz,
                             double[] u, double[] v, double u0, double v0, double uSize, double vSize,
                             double offsetX, double offsetY, int scale, int color) {
        double[] origin = project(ox, oy, oz, scale);
        double[] uEnd = project(ox + u[0], oy + u[1], oz + u[2], scale);
        double[] vEnd = project(ox + v[0], oy + v[1], oz + v[2], scale);
        double ux = uEnd[0] - origin[0];
        double uy = uEnd[1] - origin[1];
        double vx = vEnd[0] - origin[0];
        double vy = vEnd[1] - origin[1];
        double sx = origin[0] + offsetX;
        double sy = origin[1] + offsetY;
        Polygon polygon = new Polygon();
        polygon.addPoint((int) Math.round(sx), (int) Math.round(sy));
        polygon.addPoint((int) Math.round(sx + ux), (int) Math.round(sy + uy));
        polygon.addPoint((int) Math.round(sx + ux + vx), (int) Math.round(sy + uy + vy));
        polygon.addPoint((int) Math.round(sx + vx), (int) Math.round(sy + vy));
        // Primero el color medio: tapa las rendijas entre caras que deja el redondeo.
        graphics.setColor(new Color(color));
        graphics.fillPolygon(polygon);
        graphics.drawPolygon(polygon);
        int size = texture.getWidth();
        int px = Math.clamp(Math.round(u0 * size), 0, size - 1);
        int py = Math.clamp(Math.round(v0 * size), 0, size - 1);
        int pw = Math.max(1, Math.min(size - px, (int) Math.round(uSize * size)));
        int ph = Math.max(1, Math.min(size - py, (int) Math.round(vSize * size)));
        BufferedImage part = texture.getSubimage(px, py, pw, ph);
        AffineTransform transform = new AffineTransform(ux / pw, uy / pw, vx / ph, vy / ph, sx, sy);
        graphics.drawImage(part, transform, null);
        if (shade < 1.0) {
            graphics.setColor(new Color(0, 0, 0, (int) Math.round((1.0 - shade) * 255)));
            graphics.fillPolygon(polygon);
        }
    }

    private static void addBoxes(List<Box> boxes, int x, int y, int z, Cell cell) {
        byte piece = cell.piece();
        if (piece == SmoothPiece.FULL) {
            boxes.add(new Box(x, y, z, x + 1, y + 1, z + 1, cell));
            return;
        }
        int[] box = SmoothPiece.box(piece);
        if (box != null) {
            boxes.add(new Box(x + box[0] / 16.0, y + box[1] / 16.0, z + box[2] / 16.0, x + box[3] / 16.0,
                    y + box[4] / 16.0, z + box[5] / 16.0, cell));
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
            boxes.add(new Box(ox, oy, oz, ox + 0.5, oy + 0.5, oz + 0.5, cell));
        }
    }

    private static boolean full(Map<Long, Cell> cells, int x, int y, int z) {
        Cell cell = cells.get(pack(x, y, z));
        return cell != null && cell.piece() == SmoothPiece.FULL;
    }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    static int[] unpack(long key) {
        return new int[]{signed((int) (key >>> 40 & 0xFFFFF)), signed((int) (key >>> 20 & 0xFFFFF)),
                signed((int) (key & 0xFFFFF))};
    }

    private static int signed(int value) {
        return value >= 0x80000 ? value - 0x100000 : value;
    }

    private static double[] project(double x, double y, double z, int scale) {
        return new double[]{(x - z) * COS * scale, (x + z) * SIN * scale - y * scale};
    }
}
