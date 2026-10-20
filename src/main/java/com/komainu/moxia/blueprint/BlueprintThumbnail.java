package com.komainu.moxia.blueprint;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Miniatura isométrica de un plano, pintada en el servidor sin Minecraft: un rayo por píxel recorre la rejilla de
 * bloques (como un trazador de vóxeles) y la cara que toca se pinta con el color medio del bloque, más clara arriba
 * y más oscura en los lados. Con bloques grandes en pantalla se marcan sus aristas. Se pinta al doble y se reduce,
 * así que los bordes salen suaves.
 *
 * <p>La cámara mira desde el sureste y desde arriba (30°, la proyección 2:1 del pixel art): se ven la cara de
 * arriba, la sur (+Z, a la izquierda) y la este (+X, a la derecha). El fondo es transparente.</p>
 */
public final class BlueprintThumbnail {
    /** Lado del PNG en píxeles. */
    public static final int SIZE = 128;
    private static final int SUPERSAMPLE = 2;
    private static final double MARGIN = 0.06;
    private static final double TOP = 1.0;
    private static final double LEFT = 0.80;
    private static final double RIGHT = 0.62;
    /** A partir de cuántos píxeles por bloque se marcan las aristas. */
    private static final double EDGE_SCALE = 3.0;
    private static final double EDGE_DARKEN = 0.84;
    private static final double DEPTH_DARKEN = 0.14;

    // Cámara: dirección de la mirada y ejes de la pantalla.
    private static final double COS = Math.cos(Math.toRadians(30)) / Math.sqrt(2);
    private static final double SIN = Math.sin(Math.toRadians(30));
    private static final double[] VIEW = {-COS, -SIN, -COS};
    private static final double[] RIGHT_AXIS = {Math.sqrt(0.5), 0, -Math.sqrt(0.5)};
    private static final double[] UP_AXIS = {
            RIGHT_AXIS[1] * VIEW[2] - RIGHT_AXIS[2] * VIEW[1],
            RIGHT_AXIS[2] * VIEW[0] - RIGHT_AXIS[0] * VIEW[2],
            RIGHT_AXIS[0] * VIEW[1] - RIGHT_AXIS[1] * VIEW[0]
    };

    private BlueprintThumbnail() {
    }

    /** Los bloques del plano en una caja que empieza en (0, 0, 0). */
    public interface Voxels {
        int sizeX();

        int sizeY();

        int sizeZ();

        /** Color {@code 0xRRGGBB} del bloque, o {@code -1} si está vacío (aire o sin color). */
        int color(int x, int y, int z);
    }

    /** Rejilla de colores en memoria: lo que el servidor monta a partir del portapapeles. */
    public static final class Grid implements Voxels {
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int[] colors;

        public Grid(int sizeX, int sizeY, int sizeZ) {
            if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || (long) sizeX * sizeY * sizeZ > Integer.MAX_VALUE - 8) {
                throw new IllegalArgumentException("Tamaño de miniatura no válido");
            }
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.colors = new int[sizeX * sizeY * sizeZ];
            java.util.Arrays.fill(colors, -1);
        }

        public void set(int x, int y, int z, int rgb) {
            colors[(y * sizeZ + z) * sizeX + x] = rgb < 0 ? -1 : rgb & 0xFFFFFF;
        }

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
            return colors[(y * sizeZ + z) * sizeX + x];
        }
    }

    public static byte[] png(Voxels voxels) {
        return encode(render(voxels, SIZE));
    }

    public static byte[] encode(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("No hay escritor de PNG");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * La miniatura de {@code size}×{@code size} píxeles con fondo transparente, encuadrada en la caja de lo que no
     * es aire: un portapapeles con márgenes de aire no sale pequeño.
     */
    public static BufferedImage render(Voxels voxels, int size) {
        Voxels trimmed = trim(voxels);
        return trimmed == null ? new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB) : frame(trimmed, size);
    }

    /** La caja mínima con todos los bloques, o {@code null} si no hay ninguno. */
    private static Voxels trim(Voxels voxels) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        int maxZ = -1;
        for (int y = 0; y < voxels.sizeY(); y++) {
            for (int z = 0; z < voxels.sizeZ(); z++) {
                for (int x = 0; x < voxels.sizeX(); x++) {
                    if (voxels.color(x, y, z) >= 0) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        maxZ = Math.max(maxZ, z);
                    }
                }
            }
        }
        if (maxX < 0) {
            return null;
        }
        if (minX == 0 && minY == 0 && minZ == 0 && maxX == voxels.sizeX() - 1 && maxY == voxels.sizeY() - 1
                && maxZ == voxels.sizeZ() - 1) {
            return voxels;
        }
        int offsetX = minX;
        int offsetY = minY;
        int offsetZ = minZ;
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        return new Voxels() {
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
                return voxels.color(x + offsetX, y + offsetY, z + offsetZ);
            }
        };
    }

    private static BufferedImage frame(Voxels voxels, int size) {
        int big = size * SUPERSAMPLE;
        int sx = voxels.sizeX();
        int sy = voxels.sizeY();
        int sz = voxels.sizeZ();
        // Silueta en la pantalla para encuadrar: de cada bloque, las esquinas que más se alejan a cada lado
        // (a la izquierda la de -X +Z, a la derecha la de +X -Z, arriba la de -X +Y -Z y abajo la de +X -Y +Z).
        double minU = Double.MAX_VALUE;
        double maxU = -Double.MAX_VALUE;
        double minV = Double.MAX_VALUE;
        double maxV = -Double.MAX_VALUE;
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    if (voxels.color(x, y, z) < 0) {
                        continue;
                    }
                    minU = Math.min(minU, dot(RIGHT_AXIS, x, y, z + 1));
                    maxU = Math.max(maxU, dot(RIGHT_AXIS, x + 1, y, z));
                    maxV = Math.max(maxV, dot(UP_AXIS, x, y + 1, z));
                    minV = Math.min(minV, dot(UP_AXIS, x + 1, y, z + 1));
                }
            }
        }
        double span = Math.max(maxU - minU, maxV - minV);
        double scale = big * (1.0 - 2 * MARGIN) / span;
        double centerU = (minU + maxU) / 2;
        double centerV = (minV + maxV) / 2;
        double diagonal = Math.sqrt((double) sx * sx + (double) sy * sy + (double) sz * sz);
        // El rayo sale de detrás de la esfera que envuelve la caja: siempre entra desde fuera.
        double reach = diagonal + 2;
        double boxCenterDepth = dot(VIEW, sx / 2.0, sy / 2.0, sz / 2.0);
        boolean edges = scale / SUPERSAMPLE >= EDGE_SCALE;
        double edgeWidth = 1.0 / scale;

        int[] pixels = new int[big * big];
        Hit hit = new Hit();
        for (int py = 0; py < big; py++) {
            double v = centerV + (big / 2.0 - (py + 0.5)) / scale;
            for (int px = 0; px < big; px++) {
                double u = centerU + ((px + 0.5) - big / 2.0) / scale;
                // Punto del plano de la cámara y rayo hacia la escena.
                double ox = RIGHT_AXIS[0] * u + UP_AXIS[0] * v + VIEW[0] * (boxCenterDepth - reach);
                double oy = RIGHT_AXIS[1] * u + UP_AXIS[1] * v + VIEW[1] * (boxCenterDepth - reach);
                double oz = RIGHT_AXIS[2] * u + UP_AXIS[2] * v + VIEW[2] * (boxCenterDepth - reach);
                if (!trace(voxels, ox, oy, oz, hit)) {
                    continue;
                }
                double shade = hit.axis == 1 ? TOP : hit.axis == 2 ? LEFT : RIGHT;
                // Lo más lejano, un poco más oscuro: ayuda a leer el volumen.
                double depth = (hit.distance - (reach - diagonal / 2)) / Math.max(1.0, diagonal);
                shade *= 1.0 - DEPTH_DARKEN * Math.clamp(depth, 0.0, 1.0);
                if (edges && hit.nearEdge(edgeWidth)) {
                    shade *= EDGE_DARKEN;
                }
                pixels[py * big + px] = shade(hit.color, shade);
            }
        }
        return downsample(pixels, big, size);
    }

    /** Dónde toca el rayo: color, eje de la cara (0 = X, 1 = Y, 2 = Z) y coordenadas dentro de la cara. */
    private static final class Hit {
        int color;
        int axis;
        double distance;
        double faceU;
        double faceV;

        boolean nearEdge(double width) {
            return faceU < width || faceU > 1 - width || faceV < width || faceV > 1 - width;
        }
    }

    /**
     * Recorre la rejilla con el algoritmo de Amanatides y Woo. La dirección del rayo es {@link #VIEW}: negativa en
     * los tres ejes, así que el rayo entra por las caras +X, +Y o +Z.
     */
    private static boolean trace(Voxels voxels, double ox, double oy, double oz, Hit hit) {
        int sx = voxels.sizeX();
        int sy = voxels.sizeY();
        int sz = voxels.sizeZ();
        double[] origin = {ox, oy, oz};
        int[] size = {sx, sy, sz};
        double enter = -Double.MAX_VALUE;
        double exit = Double.MAX_VALUE;
        int axis = 0;
        for (int index = 0; index < 3; index++) {
            double near = (size[index] - origin[index]) / VIEW[index];
            double far = (0 - origin[index]) / VIEW[index];
            if (near > enter) {
                enter = near;
                axis = index;
            }
            exit = Math.min(exit, far);
        }
        if (enter >= exit || exit <= 0) {
            return false;
        }
        double px = ox + VIEW[0] * enter;
        double py = oy + VIEW[1] * enter;
        double pz = oz + VIEW[2] * enter;
        int cx = Math.clamp((int) Math.floor(axis == 0 ? sx - 0.5 : px), 0, sx - 1);
        int cy = Math.clamp((int) Math.floor(axis == 1 ? sy - 0.5 : py), 0, sy - 1);
        int cz = Math.clamp((int) Math.floor(axis == 2 ? sz - 0.5 : pz), 0, sz - 1);
        double deltaX = 1.0 / -VIEW[0];
        double deltaY = 1.0 / -VIEW[1];
        double deltaZ = 1.0 / -VIEW[2];
        double nextX = enter + (px - cx) * deltaX;
        double nextY = enter + (py - cy) * deltaY;
        double nextZ = enter + (pz - cz) * deltaZ;
        double distance = enter;
        while (true) {
            int color = voxels.color(cx, cy, cz);
            if (color >= 0) {
                hit.color = color;
                hit.axis = axis;
                hit.distance = distance;
                double hx = ox + VIEW[0] * distance - cx;
                double hy = oy + VIEW[1] * distance - cy;
                double hz = oz + VIEW[2] * distance - cz;
                switch (axis) {
                    case 0 -> {
                        hit.faceU = hy;
                        hit.faceV = hz;
                    }
                    case 1 -> {
                        hit.faceU = hx;
                        hit.faceV = hz;
                    }
                    default -> {
                        hit.faceU = hx;
                        hit.faceV = hy;
                    }
                }
                return true;
            }
            if (nextX <= nextY && nextX <= nextZ) {
                distance = nextX;
                nextX += deltaX;
                axis = 0;
                if (--cx < 0) {
                    return false;
                }
            } else if (nextY <= nextZ) {
                distance = nextY;
                nextY += deltaY;
                axis = 1;
                if (--cy < 0) {
                    return false;
                }
            } else {
                distance = nextZ;
                nextZ += deltaZ;
                axis = 2;
                if (--cz < 0) {
                    return false;
                }
            }
        }
    }

    private static int shade(int rgb, double factor) {
        int red = (int) Math.round(((rgb >> 16) & 0xFF) * factor);
        int green = (int) Math.round(((rgb >> 8) & 0xFF) * factor);
        int blue = (int) Math.round((rgb & 0xFF) * factor);
        return 0xFF000000 | Math.min(255, red) << 16 | Math.min(255, green) << 8 | Math.min(255, blue);
    }

    /** Media de cada bloque de píxeles, con el alfa premultiplicado para que el fondo no oscurezca los bordes. */
    private static BufferedImage downsample(int[] pixels, int big, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int factor = big / size;
        int samples = factor * factor;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                long alpha = 0;
                long red = 0;
                long green = 0;
                long blue = 0;
                for (int dy = 0; dy < factor; dy++) {
                    for (int dx = 0; dx < factor; dx++) {
                        int pixel = pixels[(y * factor + dy) * big + x * factor + dx];
                        int a = pixel >>> 24;
                        alpha += a;
                        red += (long) ((pixel >> 16) & 0xFF) * a;
                        green += (long) ((pixel >> 8) & 0xFF) * a;
                        blue += (long) (pixel & 0xFF) * a;
                    }
                }
                if (alpha == 0) {
                    continue;
                }
                int a = (int) (alpha / samples);
                int r = (int) (red / alpha);
                int g = (int) (green / alpha);
                int b = (int) (blue / alpha);
                image.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    private static double dot(double[] axis, double x, double y, double z) {
        return axis[0] * x + axis[1] * y + axis[2] * z;
    }
}
