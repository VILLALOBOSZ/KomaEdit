package com.komainu.moxia.terrain;

import com.komainu.moxia.gizmo.PerspectiveRaster;
import com.komainu.moxia.shape.Vec3;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders de revisión del relieve por trayectoria: el mapa sombreado desde arriba (con la luz del noroeste y los
 * colores de los bloques) y una vista en perspectiva de los bloques con la luz de la ladera.
 */
final class LandformRender {
    private LandformRender() {
    }

    /** Desde arriba: el color del bloque de arriba con sombreado por la normal suavizada y el agua encima. */
    static BufferedImage relief(FakeTerrain terrain, int scale) {
        int sizeX = terrain.sizeX();
        int sizeZ = terrain.sizeZ();
        double[] tops = tops(terrain);
        double[] soft = soften(tops, sizeX, sizeZ);
        BufferedImage image = new BufferedImage(sizeX * scale, sizeZ * scale, BufferedImage.TYPE_INT_RGB);
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (double top : tops) {
            minimum = Math.min(minimum, top);
            maximum = Math.max(maximum, top);
        }
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int wx = x + terrain.minimumX();
                int wz = z + terrain.minimumZ();
                int top = (int) tops[z * sizeX + x];
                double shade = light(soft, sizeX, sizeZ, x, z, 1.6);
                // Las aristas de los bloques (el desnivel con la vecina) se notan un poco, como en el juego.
                double step = tops[z * sizeX + x] - tops[Math.max(0, z - 1) * sizeX + x];
                shade *= step > 0.5 ? 1.06 : step < -0.5 ? 0.9 : 1.0;
                double tint = 0.9 + 0.2 * (top - minimum) / Math.max(1.0, maximum - minimum);
                Color base = top < 0 ? Color.BLACK : TerrainRender.color(terrain.blockId(wx, top, wz));
                int rgb = scale(base.getRGB(), shade * tint);
                int liquid = terrain.liquidTop(wx, wz);
                if (liquid > top) {
                    Color water = TerrainRender.color(terrain.blockId(wx, liquid, wz));
                    double depth = Math.min(0.92, 0.5 + (liquid - top) * 0.09);
                    rgb = mix(rgb, water.getRGB(), depth);
                }
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        image.setRGB(x * scale + dx, z * scale + dy, rgb);
                    }
                }
            }
        }
        return image;
    }

    /**
     * En perspectiva desde el sureste: las caras de arriba con la luz de la ladera (la normal suavizada) y las de los
     * lados con su bloque, un poco más oscuras.
     */
    static BufferedImage perspective(FakeTerrain terrain, int width, int height, double yaw, double pitch,
                                     double distanceFactor) throws IOException {
        int sizeX = terrain.sizeX();
        int sizeZ = terrain.sizeZ();
        double[] tops = tops(terrain);
        double[] soft = soften(tops, sizeX, sizeZ);
        double maximum = Arrays.stream(tops).max().orElse(0.0);
        double centerX = terrain.minimumX() + sizeX / 2.0;
        double centerZ = terrain.minimumZ() + sizeZ / 2.0;
        double span = Math.max(sizeX, sizeZ) * distanceFactor;
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        Vec3 eye = new Vec3(centerX + Math.sin(yawRadians) * Math.cos(pitchRadians) * span,
                maximum * 0.35 + Math.sin(pitchRadians) * span,
                centerZ - Math.cos(yawRadians) * Math.cos(pitchRadians) * span);
        Map<Integer, int[]> textures = new HashMap<>();
        try (PerspectiveRaster raster = new PerspectiveRaster(width, height, eye, yaw, pitch, 42.0)) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int wx = x + terrain.minimumX();
                    int wz = z + terrain.minimumZ();
                    int top = (int) tops[z * sizeX + x];
                    if (top < 0) {
                        continue;
                    }
                    int liquid = terrain.liquidTop(wx, wz);
                    double shade = light(soft, sizeX, sizeZ, x, z, 1.4);
                    if (liquid > top) {
                        int rgb = mix(scale(TerrainRender.color(terrain.blockId(wx, top, wz)).getRGB(), shade),
                                TerrainRender.color(terrain.blockId(wx, liquid, wz)).getRGB(),
                                Math.min(0.9, 0.55 + (liquid - top) * 0.08));
                        raster.face(wx, liquid, wz, wx + 1, liquid + 0.9, wz + 1, 3, flat(textures, rgb), 1.0,
                                1.0, null);
                        continue;
                    }
                    int topColor = TerrainRender.color(terrain.blockId(wx, top, wz)).getRGB();
                    raster.face(wx, top, wz, wx + 1, top + 1, wz + 1, 3, flat(textures, topColor), shade, 1.0, null);
                    int[][] sides = {{-1, 0, 0}, {1, 0, 1}, {0, -1, 4}, {0, 1, 5}};
                    for (int[] side : sides) {
                        int nx = x + side[0];
                        int nz = z + side[1];
                        int neighbour = nx < 0 || nz < 0 || nx >= sizeX || nz >= sizeZ ? terrain.minimumY() - 1
                                : (int) Math.max(tops[nz * sizeX + nx],
                                terrain.liquidTop(nx + terrain.minimumX(), nz + terrain.minimumZ()));
                        for (int y = top; y > neighbour && y >= terrain.minimumY(); y--) {
                            String block = terrain.blockId(wx, y, wz);
                            if (block.equals("minecraft:air")) {
                                continue;
                            }
                            int rgb = TerrainRender.color(block).getRGB();
                            double sideShade = PerspectiveRaster.SHADE[side[2]] * (0.75 + 0.25 * shade);
                            raster.face(wx, y, wz, wx + 1, y + 1, wz + 1, side[2], flat(textures, rgb), sideShade,
                                    1.0, null);
                            if (top - y > 48) {
                                break;
                            }
                        }
                    }
                }
            }
            return raster.image();
        }
    }

    private static int[] flat(Map<Integer, int[]> textures, int rgb) {
        return textures.computeIfAbsent(rgb, key -> {
            int[] pixels = new int[256];
            Arrays.fill(pixels, 0xFF000000 | key);
            return pixels;
        });
    }

    private static double[] tops(FakeTerrain terrain) {
        int sizeX = terrain.sizeX();
        int sizeZ = terrain.sizeZ();
        double[] tops = new double[sizeX * sizeZ];
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                tops[z * sizeX + x] = terrain.top(x + terrain.minimumX(), z + terrain.minimumZ());
            }
        }
        return tops;
    }

    /** Media de 5×5 para que la luz siga la ladera y no los escalones. */
    private static double[] soften(double[] tops, int sizeX, int sizeZ) {
        double[] result = new double[tops.length];
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                double sum = 0.0;
                double weight = 0.0;
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int ax = Math.clamp(x + dx, 0, sizeX - 1);
                        int az = Math.clamp(z + dz, 0, sizeZ - 1);
                        double w = (3 - Math.abs(dx)) * (3 - Math.abs(dz));
                        sum += tops[az * sizeX + ax] * w;
                        weight += w;
                    }
                }
                result[z * sizeX + x] = sum / weight;
            }
        }
        return result;
    }

    /** Luz del noroeste a 40° sobre la ladera suavizada, con algo de luz ambiente. */
    private static double light(double[] soft, int sizeX, int sizeZ, int x, int z, double exaggeration) {
        double east = soft[z * sizeX + Math.min(sizeX - 1, x + 1)];
        double west = soft[z * sizeX + Math.max(0, x - 1)];
        double south = soft[Math.min(sizeZ - 1, z + 1) * sizeX + x];
        double north = soft[Math.max(0, z - 1) * sizeX + x];
        double gx = (east - west) * 0.5 * exaggeration;
        double gz = (south - north) * 0.5 * exaggeration;
        double length = Math.sqrt(gx * gx + gz * gz + 1.0);
        double lx = -0.55;
        double lz = -0.55;
        double ly = 0.63;
        double direct = Math.max(0.0, (-gx * lx - gz * lz + ly) / length);
        return Math.clamp(0.38 + 0.78 * direct, 0.25, 1.2);
    }

    private static int scale(int rgb, double factor) {
        int red = Math.clamp((int) Math.round(((rgb >> 16) & 0xFF) * factor), 0, 255);
        int green = Math.clamp((int) Math.round(((rgb >> 8) & 0xFF) * factor), 0, 255);
        int blue = Math.clamp((int) Math.round((rgb & 0xFF) * factor), 0, 255);
        return red << 16 | green << 8 | blue;
    }

    private static int mix(int a, int b, double t) {
        int red = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int green = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int blue = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return red << 16 | green << 8 | blue;
    }
}
