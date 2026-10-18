package com.komainu.moxia.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Imágenes de prueba generadas por código (sin derechos de terceros). */
final class TestImages {
    private TestImages() {
    }

    /**
     * Un atardecer: cielo degradado, sol con halo, dos filas de colinas, un lago con reflejos y las esquinas
     * redondeadas transparentes (con borde suave).
     */
    static RgbaImage scene(int width, int height) {
        RgbaImage image = RgbaImage.blank(width, height);
        double sunX = width * 0.68;
        double sunY = height * 0.42;
        double sunRadius = Math.min(width, height) * 0.11;
        double horizon = height * 0.62;
        double corner = Math.min(width, height) * 0.12;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double u = (x + 0.5) / width;
                double v = (y + 0.5) / height;
                double[] color;
                double far = horizon - height * (0.10 + 0.05 * Math.sin(u * 7.0 + 1.3) + 0.03 * Math.sin(u * 17.0));
                double near = horizon - height * (0.03 + 0.06 * Math.sin(u * 4.0 - 0.7) * Math.sin(u * 2.3 + 0.4));
                if (y < far) {
                    // Cielo: azul profundo arriba, naranja y rosa hacia el horizonte.
                    double t = Math.clamp(v / (far / height), 0.0, 1.0);
                    color = mix(new double[]{28, 40, 112}, new double[]{246, 140, 76}, Math.pow(t, 1.6));
                    color = mix(color, new double[]{232, 96, 128}, 0.25 * Math.sin(Math.PI * t));
                    double distance = Math.hypot(x + 0.5 - sunX, y + 0.5 - sunY);
                    double glow = Math.exp(-Math.pow(distance / (sunRadius * 2.6), 2.0)) * 0.55;
                    color = mix(color, new double[]{255, 214, 120}, glow);
                    if (distance < sunRadius) {
                        color = mix(new double[]{255, 236, 170}, new double[]{255, 196, 92}, distance / sunRadius);
                    }
                } else if (y < near) {
                    color = mix(new double[]{86, 70, 112}, new double[]{62, 92, 98}, (y - far) / Math.max(1, near - far));
                } else if (y < horizon + height * 0.04) {
                    color = mix(new double[]{58, 120, 52}, new double[]{30, 78, 36}, (y - near) / (height * 0.1));
                } else {
                    // Lago: el cielo reflejado, más oscuro, con ondas.
                    double ripple = 0.5 + 0.5 * Math.sin(x * 0.9 + y * 2.1) * Math.sin(y * 0.7);
                    double t = (y - horizon) / (height - horizon);
                    color = mix(new double[]{214, 120, 80}, new double[]{24, 44, 96}, Math.clamp(t * 1.4, 0.0, 1.0));
                    color = mix(color, new double[]{255, 210, 140}, 0.35 * ripple
                            * Math.exp(-Math.pow((x + 0.5 - sunX) / (sunRadius * 1.2), 2.0)));
                }
                image.set(x, y, RgbaImage.argb((int) Math.round(255 * cornerAlpha(x, y, width, height, corner)),
                        channel(color[0]), channel(color[1]), channel(color[2])));
            }
        }
        return image;
    }

    /** Un rombo de colores puros sobre fondo transparente, para el pixel art sin tramado. */
    static RgbaImage icon(int size) {
        RgbaImage image = RgbaImage.blank(size, size);
        int[] colors = {0xFFD81E1E, 0xFF1E5AD8, 0xFFF0C81E, 0xFF1EA03C, 0xFFF0F0F0};
        double center = (size - 1) / 2.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double distance = Math.abs(x - center) + Math.abs(y - center);
                if (distance <= center) {
                    image.set(x, y, colors[(int) (distance / (center + 1) * colors.length)]);
                }
            }
        }
        return image;
    }

    private static double cornerAlpha(int x, int y, int width, int height, double radius) {
        double cx = Math.clamp(x + 0.5, radius, width - radius);
        double cy = Math.clamp(y + 0.5, radius, height - radius);
        double distance = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
        return Math.clamp(radius - distance + 0.5, 0.0, 1.0);
    }

    private static double[] mix(double[] first, double[] second, double t) {
        double clamped = Math.clamp(t, 0.0, 1.0);
        return new double[]{
                first[0] + (second[0] - first[0]) * clamped,
                first[1] + (second[1] - first[1]) * clamped,
                first[2] + (second[2] - first[2]) * clamped
        };
    }

    private static int channel(double value) {
        return (int) Math.round(Math.clamp(value, 0.0, 255.0));
    }

    static BufferedImage buffered(RgbaImage image, int type) {
        BufferedImage buffered = new BufferedImage(image.width(), image.height(), type);
        buffered.setRGB(0, 0, image.width(), image.height(), image.argb(), 0, image.width());
        return buffered;
    }

    static byte[] encode(RgbaImage image, String format) {
        int type = format.equals("png") || format.equals("gif") ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(buffered(image, type), format, output)) {
                throw new IllegalStateException("Sin escritor de " + format);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
