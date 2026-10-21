package com.komainu.moxia.reference;

import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.image.RgbaImage;
import com.komainu.moxia.preview.TextureTiles;
import com.komainu.moxia.shape.Vec3;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Un mundo pequeño de bloques y un trazador de rayos para las maquetas de las imágenes de referencia: la cámara de
 * Minecraft (guiñada, cabeceo y campo de visión vertical), las caras con la textura del jar si {@code KOMAEDIT_MC_JAR}
 * lo indica y su sombreado de serie. Encima, el plano de la referencia con el mismo {@link WorldFrame} que el juego:
 * con profundidad lo tapan los bloques de delante; sin ella va encima de todo. La rejilla y el gizmo se dibujan con
 * {@link ReferenceGrid} y {@link ReferenceGizmo}, igual que en el cliente.
 */
final class ReferenceScene {
    static final int MIN_X = -96;
    static final int MIN_Y = 56;
    static final int MIN_Z = -128;
    static final int SIZE_X = 208;
    static final int SIZE_Y = 40;
    static final int SIZE_Z = 192;
    /** Desde dónde empieza la niebla (y hasta dónde llega a casi tapar). */
    private static final double FOG_START = 40.0;
    private static final double FOG_RANGE = 110.0;
    private static final double[] SHADE = {0.6, 0.6, 0.5, 1.0, 0.8, 0.8};

    /** Un bloque: las texturas de arriba, de los lados y de abajo, con su tinte y un color si no hay jar. */
    enum Block {
        AIR(null, null, null, 0, 0xFFFFFF),
        GRASS("grass_block_top", "grass_block_side", "dirt", 0x7CBD6B, 0x6FA34E),
        DIRT("dirt", "dirt", "dirt", 0xFFFFFF, 0x7A5536),
        STONE_BRICKS("stone_bricks", "stone_bricks", "stone_bricks", 0xFFFFFF, 0x8A8A8A),
        COBBLESTONE("cobblestone", "cobblestone", "cobblestone", 0xFFFFFF, 0x7A7A7A),
        WINDOW("gray_stained_glass", "gray_stained_glass", "gray_stained_glass", 0xFFFFFF, 0x4C4C55),
        DOOR("spruce_planks", "spruce_planks", "spruce_planks", 0xFFFFFF, 0x6B4E2E),
        LOG("oak_log_top", "oak_log", "oak_log_top", 0xFFFFFF, 0x6B5434),
        LEAVES("oak_leaves", "oak_leaves", "oak_leaves", 0x59AE30, 0x3E7A22),
        SCAFFOLD("oak_planks", "oak_planks", "oak_planks", 0xFFFFFF, 0xA8854F),
        RED_WOOL("red_wool", "red_wool", "red_wool", 0xFFFFFF, 0xA12722),
        WHITE_WOOL("white_wool", "white_wool", "white_wool", 0xFFFFFF, 0xE9ECEC),
        BLACK_WOOL("black_wool", "black_wool", "black_wool", 0xFFFFFF, 0x15151A),
        SANDSTONE("sandstone_top", "sandstone_top", "sandstone_top", 0xFFFFFF, 0xD8CB9A);

        final String top;
        final String side;
        final String bottom;
        final int tint;
        final int fallback;

        Block(String top, String side, String bottom, int tint, int fallback) {
            this.top = top;
            this.side = side;
            this.bottom = bottom;
            this.tint = tint;
            this.fallback = fallback;
        }
    }

    /** Una referencia en el mundo para el trazado: su plano, sus píxeles y cómo se ve. */
    record Plane(WorldFrame frame, RgbaImage image, double opacity, boolean smooth, ReferenceGrid grid) {
    }

    private final byte[] blocks = new byte[SIZE_X * SIZE_Y * SIZE_Z];
    private final TextureTiles tiles;

    ReferenceScene(TextureTiles tiles) {
        this.tiles = tiles;
    }

    void set(int x, int y, int z, Block block) {
        int index = index(x, y, z);
        if (index >= 0) {
            blocks[index] = (byte) block.ordinal();
        }
    }

    Block get(int x, int y, int z) {
        int index = index(x, y, z);
        return index < 0 ? Block.AIR : Block.values()[blocks[index]];
    }

    private static int index(int x, int y, int z) {
        int lx = x - MIN_X;
        int ly = y - MIN_Y;
        int lz = z - MIN_Z;
        if (lx < 0 || ly < 0 || lz < 0 || lx >= SIZE_X || ly >= SIZE_Y || lz >= SIZE_Z) {
            return -1;
        }
        return (lx * SIZE_Y + ly) * SIZE_Z + lz;
    }

    /** Hierba en y = 63 con tierra debajo: el suelo de todas las maquetas. */
    void ground() {
        for (int x = MIN_X; x < MIN_X + SIZE_X; x++) {
            for (int z = MIN_Z; z < MIN_Z + SIZE_Z; z++) {
                set(x, 63, z, Block.GRASS);
                set(x, 62, z, Block.DIRT);
            }
        }
    }

    void tree(int x, int y, int z, int height) {
        for (int step = 0; step < height; step++) {
            set(x, y + step, z, Block.LOG);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = height - 2; dy <= height + 1; dy++) {
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    boolean top = dy > height - 1 && (Math.abs(dx) == 2 || Math.abs(dz) == 2);
                    if (!corner && !top && get(x + dx, y + dy, z + dz) == Block.AIR) {
                        set(x + dx, y + dy, z + dz, Block.LEAVES);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- cámara

    /** La cámara: ojo, guiñada y cabeceo de Minecraft (0 = sur, cabeceo positivo = abajo) y campo de visión. */
    record Camera(Vec3 eye, double yaw, double pitch, double fov, int width, int height) {
        Vec3 forward() {
            double yawRadians = Math.toRadians(yaw);
            double pitchRadians = Math.toRadians(pitch);
            return new Vec3(-Math.sin(yawRadians) * Math.cos(pitchRadians), -Math.sin(pitchRadians),
                    Math.cos(yawRadians) * Math.cos(pitchRadians));
        }

        Vec3 right() {
            return forward().cross(new Vec3(0, 1, 0)).normalized();
        }

        Vec3 up() {
            return right().cross(forward()).normalized();
        }

        double focal() {
            return height / 2.0 / Math.tan(Math.toRadians(fov) / 2.0);
        }

        /** Mirando de {@code eye} a {@code target}. */
        static Camera looking(Vec3 eye, Vec3 target, double fov, int width, int height) {
            Vec3 direction = target.subtract(eye).normalized();
            double yaw = Math.toDegrees(Math.atan2(-direction.x(), direction.z()));
            double pitch = -Math.toDegrees(Math.asin(direction.y()));
            return new Camera(eye, yaw, pitch, fov, width, height);
        }

        /** Del mundo a la pantalla: {x, y, distancia}; {@code null} detrás de la cámara. */
        double[] project(Vec3 point) {
            Vec3 offset = point.subtract(eye);
            double depth = offset.dot(forward());
            if (depth < 0.05) {
                return null;
            }
            double scale = focal() / depth;
            return new double[]{width / 2.0 + offset.dot(right()) * scale, height / 2.0 - offset.dot(up()) * scale,
                    offset.length()};
        }

        Vec3 ray(double x, double y) {
            return forward().add(right().multiply((x - width / 2.0) / focal()))
                    .add(up().multiply((height / 2.0 - y) / focal())).normalized();
        }
    }

    // ---------------------------------------------------------------- trazado

    /** Imagen trazada y la distancia de cada píxel al primer bloque (infinito en el cielo). */
    record Frame(BufferedImage image, double[] depth) {
    }

    Frame trace(Camera camera, List<Plane> planes) {
        int width = camera.width();
        int height = camera.height();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double[] depth = new double[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Vec3 direction = camera.ray(x + 0.5, y + 0.5);
                double[] hit = new double[1];
                int color = march(camera.eye(), direction, hit);
                if (Double.isFinite(hit[0])) {
                    color = lerp(color, sky(direction), Math.clamp((hit[0] - FOG_START) / FOG_RANGE, 0.0, 0.8));
                }
                depth[y * width + x] = hit[0];
                for (Plane plane : planes) {
                    color = plane(plane, camera.eye(), direction, hit[0], color);
                }
                image.setRGB(x, y, color);
            }
        }
        return new Frame(image, depth);
    }

    private static int sky(Vec3 direction) {
        double amount = Math.clamp(direction.y() * 1.6 + 0.2, 0.0, 1.0);
        return lerp(0xC6DCF5, 0x7FA8E6, amount);
    }

    /** Recorre la rejilla de bloques (Amanatides–Woo) hasta el primero sólido. */
    private int march(Vec3 origin, Vec3 direction, double[] distance) {
        int x = (int) Math.floor(origin.x());
        int y = (int) Math.floor(origin.y());
        int z = (int) Math.floor(origin.z());
        int stepX = direction.x() > 0 ? 1 : -1;
        int stepY = direction.y() > 0 ? 1 : -1;
        int stepZ = direction.z() > 0 ? 1 : -1;
        double deltaX = Math.abs(1.0 / direction.x());
        double deltaY = Math.abs(1.0 / direction.y());
        double deltaZ = Math.abs(1.0 / direction.z());
        double maxX = (direction.x() > 0 ? x + 1 - origin.x() : origin.x() - x) * deltaX;
        double maxY = (direction.y() > 0 ? y + 1 - origin.y() : origin.y() - y) * deltaY;
        double maxZ = (direction.z() > 0 ? z + 1 - origin.z() : origin.z() - z) * deltaZ;
        int face = -1;
        double travelled = 0.0;
        for (int step = 0; step < 900; step++) {
            Block block = get(x, y, z);
            if (block != Block.AIR && face >= 0) {
                distance[0] = travelled;
                return shadeFace(block, face, origin.add(direction.multiply(travelled)));
            }
            if (maxX < maxY && maxX < maxZ) {
                x += stepX;
                travelled = maxX;
                maxX += deltaX;
                face = stepX > 0 ? 0 : 1;
            } else if (maxY < maxZ) {
                y += stepY;
                travelled = maxY;
                maxY += deltaY;
                face = stepY > 0 ? 2 : 3;
            } else {
                z += stepZ;
                travelled = maxZ;
                maxZ += deltaZ;
                face = stepZ > 0 ? 4 : 5;
            }
            if (travelled > 300.0) {
                break;
            }
        }
        distance[0] = Double.POSITIVE_INFINITY;
        return sky(direction);
    }

    /** Color de la cara (0 oeste, 1 este, 2 abajo, 3 arriba, 4 norte, 5 sur) en el punto, con su sombra. */
    private int shadeFace(Block block, int face, Vec3 point) {
        String texture = face == 3 ? block.top : face == 2 ? block.bottom : block.side;
        // La hierba solo se tiñe por arriba (el lado trae su propio verde).
        int tint = block == Block.GRASS && face != 3 ? 0xFFFFFF : block.tint;
        int[] tile = tiles.tile(texture, block.fallback, tint);
        double u;
        double v;
        switch (face) {
            case 0, 1 -> {
                u = point.z();
                v = point.y();
            }
            case 4, 5 -> {
                u = point.x();
                v = point.y();
            }
            default -> {
                u = point.x();
                v = point.z();
            }
        }
        int column = (int) Math.floor(frac(u) * 16) & 15;
        int row = face == 2 || face == 3 ? (int) Math.floor(frac(v) * 16) & 15 : 15 - ((int) Math.floor(frac(v) * 16)
                & 15);
        int argb = tile[row * 16 + column];
        if (block == Block.GRASS && face != 2 && face != 3 && !tiles.real()) {
            argb = row < 3 ? 0xFF000000 | block.fallback : 0xFF000000 | Block.DIRT.fallback;
        }
        return scale(argb, SHADE[face]);
    }

    private static double frac(double value) {
        return value - Math.floor(value);
    }

    /** El plano de una referencia sobre el color de debajo, con su profundidad. */
    private static int plane(Plane plane, Vec3 origin, Vec3 direction, double blockDistance, int under) {
        WorldFrame frame = plane.frame();
        Vec3 normal = frame.normal();
        double facing = direction.dot(normal);
        if (Math.abs(facing) < 1.0E-9) {
            return under;
        }
        double distance = frame.center().subtract(origin).dot(normal) / facing;
        if (distance <= 0.0 || frame.depth() && distance > blockDistance + 1.0E-3) {
            return under;
        }
        RgbaImage image = plane.image();
        int width = image.width();
        double[] pixel = frame.pixel(origin.add(direction.multiply(distance)), image.width(), image.height());
        if (pixel[0] < 0 || pixel[1] < 0 || pixel[0] >= width || pixel[1] >= image.height()) {
            return under;
        }
        int argb = plane.smooth() ? bilinear(image, pixel[0], pixel[1]) : image.get((int) pixel[0], (int) pixel[1]);
        double alpha = (argb >>> 24) / 255.0 * plane.opacity();
        return blend(under, argb, alpha);
    }

    private static int bilinear(RgbaImage image, double u, double v) {
        double x = Math.clamp(u - 0.5, 0, image.width() - 1);
        double y = Math.clamp(v - 0.5, 0, image.height() - 1);
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = Math.min(image.width() - 1, x0 + 1);
        int y1 = Math.min(image.height() - 1, y0 + 1);
        double fx = x - x0;
        double fy = y - y0;
        int[] channels = new int[4];
        for (int channel = 0; channel < 4; channel++) {
            int shift = channel * 8;
            double top = (image.get(x0, y0) >>> shift & 0xFF) * (1 - fx) + (image.get(x1, y0) >>> shift & 0xFF) * fx;
            double bottom = (image.get(x0, y1) >>> shift & 0xFF) * (1 - fx) + (image.get(x1, y1) >>> shift & 0xFF)
                    * fx;
            channels[channel] = (int) Math.round(top * (1 - fy) + bottom * fy);
        }
        return channels[3] << 24 | channels[2] << 16 | channels[1] << 8 | channels[0];
    }

    // ---------------------------------------------------------------- rejilla y gizmo

    /**
     * Las líneas de la rejilla y el contorno de la imagen, en 3D: con profundidad, los bloques de delante las tapan
     * (como en el juego). Mismo grosor en pantalla que el cliente a 1080p.
     */
    static void grid(Frame frame, Camera camera, Plane plane, double pixelScale) {
        ReferenceGrid grid = plane.grid();
        if (!grid.enabled()) {
            return;
        }
        WorldFrame world = plane.frame();
        int width = plane.image().width();
        int height = plane.image().height();
        int color = grid.color().rgb();
        Vec3[] corners = world.corners(width, height);
        for (int index = 0; index < corners.length; index++) {
            line3d(frame, camera, corners[index], corners[(index + 1) % 4], 0xC0 << 24 | color, 1.5 * pixelScale,
                    world.depth());
        }
        boolean minor = grid.visibleAt(1.0 / world.pixelsPerBlock(), 1.0 / 32.0);
        for (ReferenceGrid.Line line : grid.lines(width, height)) {
            if (!line.major() && !minor) {
                continue;
            }
            Vec3 from = line.vertical() ? world.point(line.at(), 0, width, height)
                    : world.point(0, line.at(), width, height);
            Vec3 to = line.vertical() ? world.point(line.at(), height, width, height)
                    : world.point(width, line.at(), width, height);
            line3d(frame, camera, from, to, (line.major() ? 0xF0 : 0xA0) << 24 | color,
                    (line.major() ? 3.0 : 1.5) * pixelScale, world.depth());
        }
    }

    private static void line3d(Frame frame, Camera camera, Vec3 from, Vec3 to, int argb, double width,
                               boolean depthTest) {
        double[] a = camera.project(from);
        double[] b = camera.project(to);
        if (a == null || b == null) {
            return;
        }
        double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
        int samples = Math.max(2, (int) Math.ceil(length * 2));
        double radius = width / 2.0;
        BufferedImage image = frame.image();
        // La cobertura de cada píxel de esta línea (la mayor), para no oscurecerlo dos veces.
        Map<Integer, Double> cover = new HashMap<>();
        for (int sample = 0; sample <= samples; sample++) {
            double[] screen = camera.project(from.lerp(to, sample / (double) samples));
            if (screen == null) {
                continue;
            }
            for (int y = (int) Math.floor(screen[1] - radius); y <= (int) Math.ceil(screen[1] + radius); y++) {
                for (int x = (int) Math.floor(screen[0] - radius); x <= (int) Math.ceil(screen[0] + radius); x++) {
                    if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
                        continue;
                    }
                    double coverage = Math.clamp(radius + 0.5 - Math.hypot(x + 0.5 - screen[0], y + 0.5 - screen[1]),
                            0.0, 1.0);
                    int key = y * image.getWidth() + x;
                    if (coverage > 0.0 && (!depthTest || screen[2] <= frame.depth()[key] + 0.02)) {
                        cover.merge(key, coverage, Math::max);
                    }
                }
            }
        }
        for (Map.Entry<Integer, Double> entry : cover.entrySet()) {
            int x = entry.getKey() % image.getWidth();
            int y = entry.getKey() / image.getWidth();
            image.setRGB(x, y, blend(image.getRGB(x, y), argb, (argb >>> 24) / 255.0 * entry.getValue()));
        }
    }

    /** El gizmo encima de todo, como {@code GizmoRenderer}: primero las líneas, luego los triángulos. */
    static void gizmo(BufferedImage image, Camera camera, GizmoGeometry.Shape shape, double pixelScale) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (GizmoGeometry.Line line : shape.lines()) {
            double[] a = camera.project(line.from());
            double[] b = camera.project(line.to());
            if (a == null || b == null) {
                continue;
            }
            graphics.setColor(color(line.argb()));
            graphics.setStroke(new BasicStroke((float) (line.width() * pixelScale), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            graphics.draw(new Line2D.Double(a[0], a[1], b[0], b[1]));
        }
        for (GizmoGeometry.Triangle triangle : shape.triangles()) {
            double[] a = camera.project(triangle.a());
            double[] b = camera.project(triangle.b());
            double[] c = camera.project(triangle.c());
            if (a == null || b == null || c == null) {
                continue;
            }
            Path2D.Double path = new Path2D.Double();
            path.moveTo(a[0], a[1]);
            path.lineTo(b[0], b[1]);
            path.lineTo(c[0], c[1]);
            path.closePath();
            graphics.setColor(color(triangle.argb()));
            graphics.fill(path);
        }
        graphics.dispose();
    }

    /** La cruz del punto de mira, en el centro. */
    static void crosshair(BufferedImage image, int scale) {
        int centerX = image.getWidth() / 2;
        int centerY = image.getHeight() / 2;
        for (int offset = -4 * scale; offset <= 4 * scale; offset++) {
            for (int thick = 0; thick < scale; thick++) {
                invert(image, centerX + offset, centerY + thick);
                if (Math.abs(offset) >= scale) {
                    invert(image, centerX + thick, centerY + offset);
                }
            }
        }
    }

    private static void invert(BufferedImage image, int x, int y) {
        if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
            image.setRGB(x, y, 0xFF000000 | ~image.getRGB(x, y) & 0xFFFFFF);
        }
    }

    // ---------------------------------------------------------------- color

    private static Color color(int argb) {
        return new Color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
    }

    static int blend(int under, int over, double alpha) {
        if (alpha <= 0.0) {
            return under;
        }
        int red = (int) Math.round((over >> 16 & 0xFF) * alpha + (under >> 16 & 0xFF) * (1 - alpha));
        int green = (int) Math.round((over >> 8 & 0xFF) * alpha + (under >> 8 & 0xFF) * (1 - alpha));
        int blue = (int) Math.round((over & 0xFF) * alpha + (under & 0xFF) * (1 - alpha));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static int lerp(int from, int to, double amount) {
        int red = (int) Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * amount);
        int green = (int) Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * amount);
        int blue = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static int scale(int argb, double factor) {
        int red = Math.min(255, (int) Math.round((argb >> 16 & 0xFF) * factor));
        int green = Math.min(255, (int) Math.round((argb >> 8 & 0xFF) * factor));
        int blue = Math.min(255, (int) Math.round((argb & 0xFF) * factor));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /** Reduce a la mitad promediando cada 2×2 (el suavizado de las maquetas). */
    static BufferedImage downsample(BufferedImage source, int factor) {
        int width = source.getWidth() / factor;
        int height = source.getHeight() / factor;
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = 0;
                int green = 0;
                int blue = 0;
                for (int dy = 0; dy < factor; dy++) {
                    for (int dx = 0; dx < factor; dx++) {
                        int rgb = source.getRGB(x * factor + dx, y * factor + dy);
                        red += rgb >> 16 & 0xFF;
                        green += rgb >> 8 & 0xFF;
                        blue += rgb & 0xFF;
                    }
                }
                int count = factor * factor;
                target.setRGB(x, y, red / count << 16 | green / count << 8 | blue / count);
            }
        }
        return target;
    }
}
