package com.komainu.moxia.gizmo;

import com.komainu.moxia.preview.TextureTiles;
import com.komainu.moxia.shape.Vec3;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Rasterizador en perspectiva para los renders del fantasma con gizmo: la cámara de Minecraft (guiñada y cabeceo, campo
 * de visión vertical), z-buffer, caras con la textura del jar de cliente si {@code KOMAEDIT_MC_JAR} lo indica (si no,
 * colores lisos) y la transparencia de una sola capa de la vista previa. El gizmo se pinta encima de todo, como en el
 * juego.
 */
public final class PerspectiveRaster implements AutoCloseable {
    public static final double[] SHADE = {0.6, 0.6, 0.5, 1.0, 0.8, 0.8};
    private final int width;
    private final int height;
    private final Vec3 eye;
    private final Vec3 forward;
    private final Vec3 right;
    private final Vec3 up;
    private final double focal;
    public final double fov;
    private final int[] color;
    private final double[] depth;
    private final TextureTiles tiles;

    /** @param yaw y {@code pitch} en grados, como en Minecraft (guiñada 0 = sur, cabeceo positivo = abajo) */
    public PerspectiveRaster(int width, int height, Vec3 eye, double yaw, double pitch, double fov) throws IOException {
        this.width = width;
        this.height = height;
        this.eye = eye;
        this.fov = fov;
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        forward = new Vec3(-Math.sin(yawRadians) * Math.cos(pitchRadians), -Math.sin(pitchRadians),
                Math.cos(yawRadians) * Math.cos(pitchRadians));
        right = forward.cross(new Vec3(0, 1, 0)).normalized();
        up = right.cross(forward).normalized();
        focal = height / 2.0 / Math.tan(Math.toRadians(fov) / 2.0);
        color = new int[width * height];
        depth = new double[width * height];
        tiles = new TextureTiles();
        clear();
    }

    public Vec3 eye() {
        return eye;
    }

    public Vec3 forward() {
        return forward;
    }

    public boolean real() {
        return tiles.real();
    }

    public void clear() {
        for (int y = 0; y < height; y++) {
            double t = y / (double) (height - 1);
            int rgb = lerp(0x8DB6F0, 0xC9DCF2, t);
            Arrays.fill(color, y * width, (y + 1) * width, rgb);
        }
        Arrays.fill(depth, 0.0);
    }

    // ---------------------------------------------------------------- texturas

    /** 16×16 ARGB de {@code textures/block/<name>.png} con un tinte, o un color liso ({@link TextureTiles}). */
    public int[] texture(String name, int fallback, int tint) {
        return tiles.tile(name, fallback, tint);
    }

    // ---------------------------------------------------------------- proyección

    /** {x, y, 1/z} en pantalla, o {@code null} detrás de la cámara. */
    public double[] project(Vec3 point) {
        Vec3 offset = point.subtract(eye);
        double z = offset.dot(forward);
        if (z < 0.05) {
            return null;
        }
        return new double[]{width / 2.0 + offset.dot(right) / z * focal, height / 2.0 - offset.dot(up) / z * focal,
                1.0 / z};
    }

    // ---------------------------------------------------------------- caras

    /**
     * Una cara de la caja {@code [x0,x1]×[y0,y1]×[z0,z1]} (oeste, este, abajo, arriba, norte, sur) con su textura.
     *
     * @param ghost profundidad de la capa del fantasma: con ella solo se pinta la cara más cercana, mezclada
     */
    public void face(double x0, double y0, double z0, double x1, double y1, double z1, int face, int[] texture,
              double shade, double alpha, double[] ghost) {
        Vec3[] corners = corners(x0, y0, z0, x1, y1, z1, face);
        Vec3 normal = normal(face);
        if (normal.dot(corners[0].subtract(eye)) >= 0.0) {
            return;
        }
        double[][] screen = new double[4][];
        for (int corner = 0; corner < 4; corner++) {
            screen[corner] = project(corners[corner]);
            if (screen[corner] == null) {
                return;
            }
        }
        triangle(screen[0], screen[1], screen[2], new double[]{0, 1}, new double[]{1, 1}, new double[]{1, 0},
                texture, shade, alpha, ghost);
        triangle(screen[0], screen[2], screen[3], new double[]{0, 1}, new double[]{1, 0}, new double[]{0, 0},
                texture, shade, alpha, ghost);
    }

    /**
     * Una cara cualquiera de color liso, con sus cuatro esquinas en sentido antihorario vistas desde fuera (las de un
     * bloque girado, como un block display).
     */
    void quad(Vec3[] corners, int rgb, double shade) {
        int[] flat = new int[256];
        Arrays.fill(flat, 0xFF000000 | rgb);
        quad(corners, flat, shade);
    }

    /**
     * Un cuadrilátero cualquiera con su textura (la cara de un display girado o escalado), si mira a la cámara. Las
     * esquinas van en sentido antihorario vistas desde fuera, empezando abajo a la izquierda, como en {@link #corners}.
     */
    void quad(Vec3[] corners, int[] texture, double shade) {
        Vec3 normal = corners[1].subtract(corners[0]).cross(corners[3].subtract(corners[0]));
        if (normal.dot(corners[0].subtract(eye)) >= 0.0) {
            return;
        }
        double[][] screen = new double[4][];
        for (int corner = 0; corner < 4; corner++) {
            screen[corner] = project(corners[corner]);
            if (screen[corner] == null) {
                return;
            }
        }
        triangle(screen[0], screen[1], screen[2], new double[]{0, 1}, new double[]{1, 1}, new double[]{1, 0},
                texture, shade, 1.0, null);
        triangle(screen[0], screen[2], screen[3], new double[]{0, 1}, new double[]{1, 0}, new double[]{0, 0},
                texture, shade, 1.0, null);
    }

    /** Nueva capa de fantasma: profundidades vacías. */
    public double[] layer() {
        return new double[width * height];
    }

    /** Lo que tapa la capa del fantasma pasa a tapar también las líneas (sus aristas de detrás no se ven). */
    public void mergeDepth(double[] layer) {
        for (int index = 0; index < depth.length; index++) {
            if (Double.isFinite(layer[index])) {
                depth[index] = Math.max(depth[index], layer[index]);
            }
        }
    }

    private void triangle(double[] a, double[] b, double[] c, double[] ta, double[] tb, double[] tc, int[] texture,
                          double shade, double alpha, double[] ghost) {
        int minX = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        double area = edge(a, b, c[0], c[1]);
        if (Math.abs(area) < 1.0E-9) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double w0 = edge(b, c, px, py) / area;
                double w1 = edge(c, a, px, py) / area;
                double w2 = edge(a, b, px, py) / area;
                if (w0 < 0 || w1 < 0 || w2 < 0) {
                    continue;
                }
                double inverse = w0 * a[2] + w1 * b[2] + w2 * c[2];
                int pixel = y * width + x;
                double u = (w0 * ta[0] * a[2] + w1 * tb[0] * b[2] + w2 * tc[0] * c[2]) / inverse;
                double v = (w0 * ta[1] * a[2] + w1 * tb[1] * b[2] + w2 * tc[1] * c[2]) / inverse;
                int texel = texture[Math.clamp((int) (v * 16), 0, 15) * 16 + Math.clamp((int) (u * 16), 0, 15)];
                if ((texel >>> 24) < 128) {
                    continue;
                }
                int rgb = scale(texel, shade);
                if (ghost == null) {
                    if (inverse > depth[pixel]) {
                        depth[pixel] = inverse;
                        color[pixel] = rgb;
                    }
                } else if (alpha < 0.0) {
                    // Pasada de profundidad del fantasma.
                    ghost[pixel] = Math.max(ghost[pixel], inverse);
                } else if (inverse >= ghost[pixel] * 0.99999 && inverse * 1.0005 > depth[pixel]) {
                    // Solo la cara más cercana del fantasma, y por delante del mundo (con su polygon offset).
                    color[pixel] = lerp(color[pixel], rgb, alpha);
                    ghost[pixel] = Double.POSITIVE_INFINITY;
                }
            }
        }
    }

    // ---------------------------------------------------------------- líneas y gizmo

    /** Una línea del mundo con prueba de profundidad (las aristas del contorno). */
    public void depthLine(Vec3 from, Vec3 to, int argb, double widthPixels) {
        double[] a = project(from);
        double[] b = project(to);
        if (a == null || b == null) {
            return;
        }
        double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
        int steps = Math.max(1, (int) Math.ceil(length * 2));
        double radius = widthPixels / 2.0;
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            double x = a[0] + (b[0] - a[0]) * t;
            double y = a[1] + (b[1] - a[1]) * t;
            double inverse = a[2] + (b[2] - a[2]) * t;
            for (int dy = (int) Math.floor(-radius); dy <= Math.ceil(radius); dy++) {
                for (int dx = (int) Math.floor(-radius); dx <= Math.ceil(radius); dx++) {
                    int px = (int) Math.floor(x + dx);
                    int py = (int) Math.floor(y + dy);
                    if (px < 0 || py < 0 || px >= width || py >= height || dx * dx + dy * dy > radius * radius + 0.5) {
                        continue;
                    }
                    int pixel = py * width + px;
                    if (inverse * 1.002 >= depth[pixel]) {
                        color[pixel] = lerp(color[pixel], argb & 0xFFFFFF, (argb >>> 24) / 255.0);
                    }
                }
            }
        }
    }

    /** El gizmo encima de todo (sin profundidad), con antialias. */
    void gizmo(GizmoGeometry.Shape shape, double alpha) {
        BufferedImage image = image();
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (GizmoGeometry.Line line : shape.lines()) {
            double[] a = project(line.from());
            double[] b = project(line.to());
            if (a == null || b == null) {
                continue;
            }
            graphics.setColor(argb(line.argb(), alpha));
            graphics.setStroke(new BasicStroke(line.width() * 1.15F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(new Line2D.Double(a[0], a[1], b[0], b[1]));
        }
        for (GizmoGeometry.Triangle triangle : shape.triangles()) {
            double[] a = project(triangle.a());
            double[] b = project(triangle.b());
            double[] c = project(triangle.c());
            if (a == null || b == null || c == null) {
                continue;
            }
            Path2D.Double path = new Path2D.Double();
            path.moveTo(a[0], a[1]);
            path.lineTo(b[0], b[1]);
            path.lineTo(c[0], c[1]);
            path.closePath();
            graphics.setColor(argb(triangle.argb(), alpha));
            graphics.fill(path);
        }
        graphics.dispose();
        load(image);
    }

    /** Un texto con fondo oscuro en un punto del mundo (las medidas de la caja). */
    public void label(String text, Vec3 at, int rgb) {
        double[] point = project(at);
        if (point == null) {
            return;
        }
        BufferedImage image = image();
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        int textWidth = graphics.getFontMetrics().stringWidth(text);
        int x = (int) Math.round(point[0] - textWidth / 2.0);
        int y = (int) Math.round(point[1]);
        graphics.setColor(new Color(0, 0, 0, 110));
        graphics.fillRect(x - 3, y - 13, textWidth + 6, 17);
        graphics.setColor(new Color(rgb));
        graphics.drawString(text, x, y);
        graphics.dispose();
        load(image);
    }

    /** La action bar del juego: una línea abajo, centrada, con sombra. */
    public void actionBar(String text) {
        BufferedImage image = image();
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        int textWidth = graphics.getFontMetrics().stringWidth(text);
        int x = (width - textWidth) / 2;
        int y = height - 70;
        graphics.setColor(new Color(0x3F3F3F));
        graphics.drawString(text, x + 2, y + 2);
        graphics.setColor(new Color(0xE8E8E8));
        graphics.drawString(text, x, y);
        // El punto de mira.
        graphics.setColor(new Color(255, 255, 255, 220));
        graphics.fillRect(width / 2 - 1, height / 2 - 8, 2, 16);
        graphics.fillRect(width / 2 - 8, height / 2 - 1, 16, 2);
        graphics.dispose();
        load(image);
    }

    public BufferedImage image() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, color, 0, width);
        return image;
    }

    private void load(BufferedImage image) {
        image.getRGB(0, 0, width, height, color, 0, width);
        for (int index = 0; index < color.length; index++) {
            color[index] &= 0xFFFFFF;
        }
    }

    public void write(Path file) throws IOException {
        ImageIO.write(image(), "png", file.toFile());
    }

    @Override
    public void close() throws IOException {
        tiles.close();
    }

    // ---------------------------------------------------------------- utilidades

    public static Vec3[] corners(double x0, double y0, double z0, double x1, double y1, double z1, int face) {
        // Las esquinas de cada cara en sentido antihorario visto desde fuera, empezando abajo a la izquierda.
        return switch (face) {
            case 0 -> new Vec3[]{new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0)};
            case 1 -> new Vec3[]{new Vec3(x1, y0, z1), new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1)};
            case 2 -> new Vec3[]{new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1)};
            case 3 -> new Vec3[]{new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0), new Vec3(x0, y1, z0)};
            case 4 -> new Vec3[]{new Vec3(x1, y0, z0), new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x1, y1, z0)};
            default -> new Vec3[]{new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1)};
        };
    }

    public static Vec3 normal(int face) {
        return switch (face) {
            case 0 -> new Vec3(-1, 0, 0);
            case 1 -> new Vec3(1, 0, 0);
            case 2 -> new Vec3(0, -1, 0);
            case 3 -> new Vec3(0, 1, 0);
            case 4 -> new Vec3(0, 0, -1);
            default -> new Vec3(0, 0, 1);
        };
    }

    private static double edge(double[] a, double[] b, double x, double y) {
        return (b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]);
    }

    private static Color argb(int value, double alpha) {
        int a = (int) Math.round((value >>> 24) * alpha);
        return new Color(value >> 16 & 0xFF, value >> 8 & 0xFF, value & 0xFF, Math.clamp(a, 0, 255));
    }

    public static int lerp(int from, int to, double amount) {
        int r = (int) Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * amount);
        int g = (int) Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * amount);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return r << 16 | g << 8 | b;
    }

    private static int scale(int argb, double factor) {
        int r = (int) Math.round((argb >> 16 & 0xFF) * factor);
        int g = (int) Math.round((argb >> 8 & 0xFF) * factor);
        int b = (int) Math.round((argb & 0xFF) * factor);
        return Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }
}
