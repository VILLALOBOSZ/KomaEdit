package com.komainu.moxia.preview;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Rasterizador por software para los renders de comprobación de la vista previa: proyección ortográfica desde el
 * sureste y arriba, z-buffer, caras con textura (del jar de cliente si {@code KOMAEDIT_MC_JAR} lo indica; si no,
 * colores lisos), transparencia por mezcla, líneas con o sin prueba de profundidad y etiquetas. Imita lo que hace el
 * juego: el z-buffer decide qué tapa qué, el «polygon offset» es un pequeño sesgo de profundidad y el brillo de las
 * entidades de vanilla (glow) se dibuja encima de todo.
 */
final class SoftRaster implements AutoCloseable {
    /** Desde dónde se mira (hacia el origen): este, arriba y sur. */
    private static final double[] VIEW = normalize(1.0, 1.15, 1.35);
    private static final double[] RIGHT = normalize(1.0, 0.0, -VIEW[0] / VIEW[2]);
    private static final double[] UP = cross(VIEW, RIGHT);
    /** Brillo de cada cara como en Minecraft: oeste, este, abajo, arriba, norte, sur. */
    static final double[] SHADE = {0.6, 0.6, 0.5, 1.0, 0.8, 0.8};

    private final int width;
    private final int height;
    private final double scale;
    private final double offsetX;
    private final double offsetY;
    private final int[] color;
    private final double[] depth;
    private final ZipFile jar;
    private final Map<String, int[]> textures = new HashMap<>();

    SoftRaster(int width, int height, double scale, double centerX, double centerY, double centerZ)
            throws IOException {
        this.width = width;
        this.height = height;
        this.scale = scale;
        double[] center = {centerX, centerY, centerZ};
        this.offsetX = width / 2.0 - dot(center, RIGHT) * scale;
        this.offsetY = height / 2.0 + dot(center, UP) * scale;
        this.color = new int[width * height];
        this.depth = new double[width * height];
        java.util.Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        String path = System.getenv("KOMAEDIT_MC_JAR");
        this.jar = path == null || path.isBlank() ? null : new ZipFile(Path.of(path).toFile());
    }

    void clear(int top, int bottom) {
        for (int y = 0; y < height; y++) {
            int rgb = lerp(top, bottom, y / (double) (height - 1));
            for (int x = 0; x < width; x++) {
                color[y * width + x] = rgb;
            }
        }
        java.util.Arrays.fill(depth, Double.NEGATIVE_INFINITY);
    }

    boolean real() {
        return jar != null;
    }

    // ---------------------------------------------------------------- texturas

    /** Píxeles ARGB de 16×16 de {@code assets/minecraft/textures/block/<name>.png}, o un color liso. */
    int[] texture(String name, int fallback, int tint) {
        return textures.computeIfAbsent(name + "#" + Integer.toHexString(tint), key -> {
            int[] pixels = new int[256];
            BufferedImage image = jar == null ? null : load("assets/minecraft/textures/block/" + name + ".png");
            for (int index = 0; index < 256; index++) {
                int argb;
                if (image == null) {
                    // Sin jar: color liso con un poco de grano, para que se lean las caras.
                    int noise = (int) ((index * 73 + 17) * 2654435761L >>> 28 & 7) - 3;
                    argb = 0xFF000000 | add(fallback, noise * 4);
                } else {
                    argb = image.getRGB(index & 15, (index >> 4) % image.getHeight());
                }
                pixels[index] = multiply(argb, tint);
            }
            return pixels;
        });
    }

    private BufferedImage load(String entryName) {
        try {
            ZipEntry entry = jar.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream input = jar.getInputStream(entry)) {
                return ImageIO.read(input);
            }
        } catch (IOException exception) {
            return null;
        }
    }

    // ---------------------------------------------------------------- caras

    /**
     * Una cara del cubo {@code [x0, x1]×[y0, y1]×[z0, z1]} en la dirección {@code face} (orden oeste, este, abajo,
     * arriba, norte, sur), con su textura y su brillo.
     *
     * @param alpha     opacidad de 0 a 1 (con 1 escribe profundidad)
     * @param bias      sesgo hacia la cámara en la prueba de profundidad (el polygon offset del juego)
     * @param zFighting mezcla al azar con {@code under} donde coincide con lo que ya hay, como el parpadeo de dos
     *                  caras en el mismo sitio
     */
    void face(double x0, double y0, double z0, double x1, double y1, double z1, int face, int[] texture,
              double shade, double alpha, double bias, int[] under, boolean zFighting) {
        double[][] corners = corners(x0, y0, z0, x1, y1, z1, face);
        if (dot(normal(face), VIEW) <= 0.0) {
            return;
        }
        double[][] screen = new double[4][];
        for (int corner = 0; corner < 4; corner++) {
            screen[corner] = project(corners[corner]);
        }
        triangle(screen[0], screen[1], screen[2], new double[]{0, 0}, new double[]{1, 0}, new double[]{1, 1},
                texture, shade, alpha, bias, under, zFighting);
        triangle(screen[0], screen[2], screen[3], new double[]{0, 0}, new double[]{1, 1}, new double[]{0, 1},
                texture, shade, alpha, bias, under, zFighting);
    }

    /** Caras visibles de un cubo entero o escalado alrededor de su centro. */
    void cube(double x, double y, double z, double size, int[][] textures, double alpha, double bias,
              int[][] under, boolean zFighting) {
        double shift = (1.0 - size) / 2.0;
        for (int face = 0; face < 6; face++) {
            face(x + shift, y + shift, z + shift, x + shift + size, y + shift + size, z + shift + size, face,
                    textures[face], SHADE[face], alpha, bias, under == null ? null : under[face], zFighting);
        }
    }

    /**
     * Transparencia de una sola capa, como la vista previa del cliente: una pasada solo de profundidad con todas las
     * caras y otra de color en la que solo pinta la más cercana de cada píxel. Deja esa profundidad escrita, así lo
     * que queda detrás de la vista previa (sus propias aristas de atrás) no se ve.
     *
     * @param faces cada una {x, y, z, cara} con su textura en {@code textures} y su tinte en {@code tints}
     */
    void singleLayer(List<double[]> faces, List<int[]> textures, List<Integer> tints, double alpha, double bias) {
        double[] nearest = new double[width * height];
        java.util.Arrays.fill(nearest, Double.NEGATIVE_INFINITY);
        double[] written = nearest;
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                written = nearest.clone();
            }
            for (int index = 0; index < faces.size(); index++) {
                double[] face = faces.get(index);
                int direction = (int) face[3];
                double[][] corners = corners(face[0], face[1], face[2], face[0] + 1, face[1] + 1, face[2] + 1,
                        direction);
                if (dot(normal(direction), VIEW) <= 0.0) {
                    continue;
                }
                double[][] screen = new double[4][];
                for (int corner = 0; corner < 4; corner++) {
                    screen[corner] = project(corners[corner]);
                }
                int[] texture = textures.get(index);
                double shade = SHADE[direction];
                int tint = tints.get(index);
                layerTriangle(screen[0], screen[1], screen[2], new double[]{0, 0}, new double[]{1, 0},
                        new double[]{1, 1}, texture, shade, tint, alpha, bias, nearest, pass);
                layerTriangle(screen[0], screen[2], screen[3], new double[]{0, 0}, new double[]{1, 1},
                        new double[]{0, 1}, texture, shade, tint, alpha, bias, nearest, pass);
            }
        }
        for (int index = 0; index < depth.length; index++) {
            if (Double.isFinite(written[index])) {
                depth[index] = Math.max(depth[index], written[index]);
            }
        }
    }

    private void layerTriangle(double[] a, double[] b, double[] c, double[] ta, double[] tb, double[] tc,
                               int[] texture, double shade, int tint, double alpha, double bias, double[] nearest,
                               int pass) {
        int minX = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        double area = edge(a, b, c[0], c[1]);
        if (Math.abs(area) < 1.0E-9) {
            return;
        }
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                double w0 = edge(b, c, px + 0.5, py + 0.5) / area;
                double w1 = edge(c, a, px + 0.5, py + 0.5) / area;
                double w2 = edge(a, b, px + 0.5, py + 0.5) / area;
                if (w0 < -1.0E-9 || w1 < -1.0E-9 || w2 < -1.0E-9) {
                    continue;
                }
                double z = w0 * a[2] + w1 * b[2] + w2 * c[2] + bias;
                int index = py * width + px;
                if (z < depth[index] - 1.0E-6) {
                    continue;
                }
                double u = w0 * ta[0] + w1 * tb[0] + w2 * tc[0];
                double v = w0 * ta[1] + w1 * tb[1] + w2 * tc[1];
                int texel = sample(texture, u, v);
                if ((texel >>> 24) == 0) {
                    continue;
                }
                if (pass == 0) {
                    nearest[index] = Math.max(nearest[index], z);
                } else if (Math.abs(z - nearest[index]) < 1.0E-7) {
                    int shaded = multiply(multiply(texel, gray(shade)), tint);
                    color[index] = blend(color[index], shaded, (texel >>> 24) / 255.0 * alpha);
                    nearest[index] = Double.POSITIVE_INFINITY;
                }
            }
        }
    }

    private void triangle(double[] a, double[] b, double[] c, double[] ta, double[] tb, double[] tc, int[] texture,
                          double shade, double alpha, double bias, int[] under, boolean zFighting) {
        int minX = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        double area = edge(a, b, c[0], c[1]);
        if (Math.abs(area) < 1.0E-9) {
            return;
        }
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                double sx = px + 0.5;
                double sy = py + 0.5;
                double w0 = edge(b, c, sx, sy) / area;
                double w1 = edge(c, a, sx, sy) / area;
                double w2 = edge(a, b, sx, sy) / area;
                if (w0 < -1.0E-9 || w1 < -1.0E-9 || w2 < -1.0E-9) {
                    continue;
                }
                double z = w0 * a[2] + w1 * b[2] + w2 * c[2];
                int index = py * width + px;
                if (z + bias < depth[index] - 1.0E-6) {
                    continue;
                }
                double u = w0 * ta[0] + w1 * tb[0] + w2 * tc[0];
                double v = w0 * ta[1] + w1 * tb[1] + w2 * tc[1];
                int texel = sample(texture, u, v);
                if (zFighting && under != null && Math.abs(z - depth[index]) < 0.05
                        && ((px * 7 + py * 13 + (px ^ py) * 3) / 5 & 1) == 0) {
                    texel = sample(under, u, v);
                }
                if ((texel >>> 24) < 16) {
                    continue;
                }
                double texelAlpha = (texel >>> 24) / 255.0 * alpha;
                int shaded = multiply(texel, gray(shade));
                color[index] = blend(color[index], shaded, texelAlpha);
                if (alpha >= 0.999 && (texel >>> 24) > 200) {
                    depth[index] = z;
                }
            }
        }
    }

    // ---------------------------------------------------------------- líneas y etiquetas

    /** Una línea de {@code thickness} píxeles; con {@code throughWalls} no mira la profundidad (el brillo). */
    void line(double[] from, double[] to, int argb, double thickness, boolean throughWalls, double bias) {
        double[] a = project(from);
        double[] b = project(to);
        double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
        int steps = Math.max(1, (int) Math.ceil(length * 2));
        double radius = thickness / 2.0;
        double alpha = (argb >>> 24) / 255.0;
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            double x = a[0] + (b[0] - a[0]) * t;
            double y = a[1] + (b[1] - a[1]) * t;
            double z = a[2] + (b[2] - a[2]) * t;
            for (int py = (int) Math.floor(y - radius); py <= (int) Math.ceil(y + radius); py++) {
                for (int px = (int) Math.floor(x - radius); px <= (int) Math.ceil(x + radius); px++) {
                    if (px < 0 || py < 0 || px >= width || py >= height
                            || Math.hypot(px + 0.5 - x, py + 0.5 - y) > radius) {
                        continue;
                    }
                    int index = py * width + px;
                    if (!throughWalls && z + bias < depth[index] - 1.0E-6) {
                        continue;
                    }
                    color[index] = blend(color[index], argb | 0xFF000000, alpha);
                }
            }
        }
    }

    /** El brillo de vanilla: el borde de una silueta, encima de todo. */
    void glowOutline(boolean[] silhouette, int rgb) {
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int index = y * width + x;
                if (!silhouette[index]) {
                    continue;
                }
                if (!silhouette[index - 1] || !silhouette[index + 1] || !silhouette[index - width]
                        || !silhouette[index + width]) {
                    color[index] = 0xFF000000 | rgb;
                    color[index - 1] = blend(color[index - 1], 0xFF000000 | rgb, 0.6);
                }
            }
        }
    }

    /** Píxeles que tapa un cubo (sin mirar la profundidad), para la silueta del brillo. */
    void mark(boolean[] silhouette, double x, double y, double z, double size) {
        double shift = (1.0 - size) / 2.0;
        for (int face = 0; face < 6; face++) {
            if (dot(normal(face), VIEW) <= 0.0) {
                continue;
            }
            double[][] corners = corners(x + shift, y + shift, z + shift, x + shift + size, y + shift + size,
                    z + shift + size, face);
            double[][] screen = new double[4][];
            for (int corner = 0; corner < 4; corner++) {
                screen[corner] = project(corners[corner]);
            }
            fill(silhouette, screen[0], screen[1], screen[2]);
            fill(silhouette, screen[0], screen[2], screen[3]);
        }
    }

    private void fill(boolean[] mask, double[] a, double[] b, double[] c) {
        int minX = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        double area = edge(a, b, c[0], c[1]);
        if (Math.abs(area) < 1.0E-9) {
            return;
        }
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                double w0 = edge(b, c, px + 0.5, py + 0.5) / area;
                double w1 = edge(c, a, px + 0.5, py + 0.5) / area;
                double w2 = edge(a, b, px + 0.5, py + 0.5) / area;
                if (w0 >= 0 && w1 >= 0 && w2 >= 0) {
                    mask[py * width + px] = true;
                }
            }
        }
    }

    boolean[] silhouette() {
        return new boolean[width * height];
    }

    BufferedImage image() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, color, 0, width);
        return image;
    }

    /** Texto con fondo oscuro centrado en un punto del mundo (como las medidas, se ve a través de todo). */
    static void label(Graphics2D graphics, SoftRaster raster, double[] point, String text, Color color, int size) {
        double[] screen = raster.project(point);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        FontMetrics metrics = graphics.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int x = (int) Math.round(screen[0] - textWidth / 2.0);
        int y = (int) Math.round(screen[1]);
        graphics.setColor(new Color(0, 0, 0, 110));
        graphics.fillRoundRect(x - 4, y - metrics.getAscent() / 2 - 3, textWidth + 8, metrics.getAscent() + 5, 6, 6);
        graphics.setColor(color);
        graphics.drawString(text, x, y + metrics.getAscent() / 2 - 1);
    }

    /** Un título y una nota en la esquina. */
    static void caption(Graphics2D graphics, String title, String note) {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        graphics.setColor(new Color(0, 0, 0, 140));
        graphics.fillRoundRect(12, 12, Math.max(graphics.getFontMetrics().stringWidth(title), 10) + 24, 34, 10, 10);
        graphics.setColor(Color.WHITE);
        graphics.drawString(title, 24, 36);
        if (note != null) {
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            int y = 70;
            for (String line : note.split("\n")) {
                graphics.setColor(new Color(0, 0, 0, 120));
                graphics.fillRect(18, y - 14, graphics.getFontMetrics().stringWidth(line) + 12, 19);
                graphics.setColor(new Color(235, 235, 235));
                graphics.drawString(line, 24, y);
                y += 21;
            }
        }
        graphics.setStroke(new BasicStroke(1));
    }

    // ---------------------------------------------------------------- matemáticas

    double[] project(double[] point) {
        return new double[]{offsetX + dot(point, RIGHT) * scale, offsetY - dot(point, UP) * scale, dot(point, VIEW)};
    }

    private static double[][] corners(double x0, double y0, double z0, double x1, double y1, double z1, int face) {
        return switch (face) {
            case 0 -> new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
            case 1 -> new double[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case 2 -> new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
            case 3 -> new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            case 4 -> new double[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
            default -> new double[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
        };
    }

    private static double[] normal(int face) {
        return new double[]{PreviewSurface.OFFSET_X[face], PreviewSurface.OFFSET_Y[face],
                PreviewSurface.OFFSET_Z[face]};
    }

    private static int sample(int[] texture, double u, double v) {
        int x = Math.clamp((int) (u * 16), 0, 15);
        int y = Math.clamp((int) (v * 16), 0, 15);
        return texture[y * 16 + x];
    }

    private static double edge(double[] a, double[] b, double x, double y) {
        return (b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]);
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / length, y / length, z / length};
    }

    static int blend(int under, int over, double alpha) {
        int red = (int) Math.round((under >> 16 & 0xFF) * (1 - alpha) + (over >> 16 & 0xFF) * alpha);
        int green = (int) Math.round((under >> 8 & 0xFF) * (1 - alpha) + (over >> 8 & 0xFF) * alpha);
        int blue = (int) Math.round((under & 0xFF) * (1 - alpha) + (over & 0xFF) * alpha);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static int multiply(int argb, int rgb) {
        int red = (argb >> 16 & 0xFF) * (rgb >> 16 & 0xFF) / 255;
        int green = (argb >> 8 & 0xFF) * (rgb >> 8 & 0xFF) / 255;
        int blue = (argb & 0xFF) * (rgb & 0xFF) / 255;
        return argb & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int gray(double shade) {
        int value = (int) Math.round(Math.clamp(shade, 0.0, 1.0) * 255);
        return value << 16 | value << 8 | value;
    }

    private static int add(int rgb, int amount) {
        int red = Math.clamp((rgb >> 16 & 0xFF) + amount, 0, 255);
        int green = Math.clamp((rgb >> 8 & 0xFF) + amount, 0, 255);
        int blue = Math.clamp((rgb & 0xFF) + amount, 0, 255);
        return red << 16 | green << 8 | blue;
    }

    private static int lerp(int a, int b, double t) {
        return blend(a, b, t);
    }

    @Override
    public void close() throws IOException {
        if (jar != null) {
            jar.close();
        }
    }
}
