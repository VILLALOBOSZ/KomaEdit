package com.komainu.moxia.generator;

import com.komainu.moxia.progress.ProgressLine;
import java.util.BitSet;

/**
 * Voxeliza las formas de {@code /komaedit shape}. Cada forma es una prueba «dentro/fuera» en sus coordenadas
 * locales (y hacia arriba, base en y = 0) que se evalúa en el centro de cada bloque; el giro y la orientación son
 * un cambio de base. La versión hueca deja los bloques de dentro que tocan uno de fuera por alguna de sus seis
 * caras, así la cáscara tiene un bloque de grosor y no se cuela la luz por las diagonales.
 *
 * <p>Los radios cuentan medio bloque más (como {@code //sphere}): un radio de 3 da 7 bloques de lado a lado.</p>
 */
public final class ShapeVoxelizer {
    /** Bloques que se recorren como mucho para una forma (la caja que la envuelve). */
    static final long MAX_CELLS = 48_000_000L;
    static final int MATERIAL_MAIN = 0;
    static final int MATERIAL_VERTEX = 1;

    private ShapeVoxelizer() {
    }

    /**
     * @param origin bloque de origen (los pies del jugador)
     * @param look   dirección de la mirada (no hace falta normalizarla)
     * @param yaw    giro horizontal del jugador en grados, como en Minecraft (0 = sur, 90 = oeste)
     */
    public static GeneratedBlocks generate(ShapeRequest request, int[] origin, double[] look, double yaw,
                                           int limit) {
        Shape shape = shape(request, look, yaw);
        GeneratedBlocks blocks = new GeneratedBlocks(limit);
        if (request.form() == ShapeRequest.Form.SQUARE) {
            square(request, origin, blocks);
            return blocks;
        }
        int[] box = shape.bounds();
        int sizeX = box[3] - box[0] + 1;
        int sizeY = box[4] - box[1] + 1;
        int sizeZ = box[5] - box[2] + 1;
        long cells = (long) sizeX * sizeY * sizeZ;
        if (cells > MAX_CELLS) {
            throw new IllegalArgumentException("La forma es demasiado grande (" + ProgressLine.group(cells)
                    + " bloques en su caja; máximo " + MAX_CELLS + ")");
        }
        BitSet inside = new BitSet((int) cells);
        for (int dy = box[1]; dy <= box[4]; dy++) {
            for (int dz = box[2]; dz <= box[5]; dz++) {
                for (int dx = box[0]; dx <= box[3]; dx++) {
                    blocks.checkCancelled();
                    if (shape.inside(dx, dy, dz)) {
                        inside.set(((dy - box[1]) * sizeZ + (dz - box[2])) * sizeX + (dx - box[0]));
                    }
                }
            }
        }
        boolean hollow = request.hollow();
        for (int dy = box[1]; dy <= box[4]; dy++) {
            for (int dz = box[2]; dz <= box[5]; dz++) {
                for (int dx = box[0]; dx <= box[3]; dx++) {
                    blocks.checkCancelled();
                    int index = ((dy - box[1]) * sizeZ + (dz - box[2])) * sizeX + (dx - box[0]);
                    if (!inside.get(index)) {
                        continue;
                    }
                    if (hollow && !exposed(inside, box, sizeX, sizeZ, dx, dy, dz, shape)) {
                        continue;
                    }
                    int material = request.vertexPattern() != null && shape.edge(dx, dy, dz)
                            ? MATERIAL_VERTEX : MATERIAL_MAIN;
                    blocks.add(origin[0] + dx, origin[1] + dy, origin[2] + dz, material);
                }
            }
        }
        return blocks;
    }

    private static boolean exposed(BitSet inside, int[] box, int sizeX, int sizeZ, int dx, int dy, int dz,
                                   Shape shape) {
        return outside(inside, box, sizeX, sizeZ, dx + 1, dy, dz, shape)
                || outside(inside, box, sizeX, sizeZ, dx - 1, dy, dz, shape)
                || outside(inside, box, sizeX, sizeZ, dx, dy, dz + 1, shape)
                || outside(inside, box, sizeX, sizeZ, dx, dy, dz - 1, shape)
                || outside(inside, box, sizeX, sizeZ, dx, dy + 1, dz, shape)
                || outside(inside, box, sizeX, sizeZ, dx, dy - 1, dz, shape);
    }

    private static boolean outside(BitSet inside, int[] box, int sizeX, int sizeZ, int dx, int dy, int dz,
                                   Shape shape) {
        if (shape.open(dy)) {
            return false;
        }
        if (dx < box[0] || dx > box[3] || dy < box[1] || dy > box[4] || dz < box[2] || dz > box[5]) {
            return true;
        }
        return !inside.get(((dy - box[1]) * sizeZ + (dz - box[2])) * sizeX + (dx - box[0]));
    }

    /** El cuadrado va bloque a bloque, sin redondeos: un radio r da 2r+1 de lado. */
    private static void square(ShapeRequest request, int[] origin, GeneratedBlocks blocks) {
        int radius = (int) Math.round(request.radius());
        int height = request.height();
        for (int dy = 0; dy < height; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    blocks.checkCancelled();
                    boolean wall = Math.abs(dx) == radius || Math.abs(dz) == radius;
                    boolean face = wall || dy == 0 || dy == height - 1;
                    if (request.wallsOnly() && !wall || request.facesOnly() && !face) {
                        continue;
                    }
                    blocks.add(origin[0] + dx, origin[1] + dy, origin[2] + dz, MATERIAL_MAIN);
                }
            }
        }
    }

    static Shape shape(ShapeRequest request, double[] look, double yaw) {
        return switch (request.form()) {
            case CONE -> new Cone(request, Frame.yaw(request.rotation() + (request.orient() ? yaw : 0.0)));
            case POLYDOME -> new Polydome(request);
            case POLYGON -> {
                double[] direction = request.direction() != null ? request.direction()
                        : ShapeRequest.snap(look[0], look[1], look[2]);
                yield new Polygon(request, Frame.axis(direction[0], direction[1], direction[2]));
            }
            case SQUARE -> new Polygon(request, Frame.yaw(0));
            case TETRAHEDRON -> new Tetrahedron(request,
                    Frame.yaw(request.rotation() + (request.orient() ? yaw : 0.0)));
            case TORUS -> new Torus(request, request.orient()
                    ? Frame.axis(look[0], look[1], look[2]) : Frame.yaw(0));
        };
    }

    /**
     * Norma de un polígono regular de {@code sides} lados: vale el radio circunscrito en su borde. Con lados pares
     * el polígono tiene una arista mirando a cada eje (un cuadrado sale alineado); con impares, un vértice hacia
     * +z (adelante).
     */
    static double polygonNorm(int sides, double x, double z) {
        double best = Double.NEGATIVE_INFINITY;
        double offset = sides % 2 == 0 ? 0.0 : Math.PI / 2 + Math.PI / sides;
        for (int side = 0; side < sides; side++) {
            double angle = offset + (2 * side) * Math.PI / sides;
            best = Math.max(best, x * Math.cos(angle) + z * Math.sin(angle));
        }
        return best / Math.cos(Math.PI / sides);
    }

    /** Los dos mayores productos con las normales de las caras: si se parecen, el punto está en una arista. */
    static double polygonGap(int sides, double x, double z) {
        double first = Double.NEGATIVE_INFINITY;
        double second = Double.NEGATIVE_INFINITY;
        double offset = sides % 2 == 0 ? 0.0 : Math.PI / 2 + Math.PI / sides;
        for (int side = 0; side < sides; side++) {
            double angle = offset + (2 * side) * Math.PI / sides;
            double value = x * Math.cos(angle) + z * Math.sin(angle);
            if (value > first) {
                second = first;
                first = value;
            } else if (value > second) {
                second = value;
            }
        }
        return first - second;
    }

    /** Base ortonormal local → mundo: u (x local), v (arriba local) y w (z local, adelante). */
    record Frame(double ux, double uy, double uz, double vx, double vy, double vz, double wx, double wy, double wz) {
        /** Giro alrededor de y con el sentido del yaw de Minecraft: 90° lleva +z (sur) a -x (oeste). */
        static Frame yaw(double degrees) {
            double angle = Math.toRadians(degrees);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            return new Frame(cos, 0, sin, 0, 1, 0, -sin, 0, cos);
        }

        /** El eje local «arriba» apunta en la dirección dada. */
        static Frame axis(double x, double y, double z) {
            double length = Math.sqrt(x * x + y * y + z * z);
            if (length < 1.0E-9) {
                return yaw(0);
            }
            double vx = x / length;
            double vy = y / length;
            double vz = z / length;
            if (Math.abs(vy) > 0.999) {
                return vy > 0 ? yaw(0) : new Frame(1, 0, 0, 0, -1, 0, 0, 0, -1);
            }
            // u = normalize(arriba × v), w = u × v
            double ux = vz;
            double uz = -vx;
            double uLength = Math.sqrt(ux * ux + uz * uz);
            ux /= uLength;
            uz /= uLength;
            double wx = -uz * vy;
            double wy = uz * vx - ux * vz;
            double wz = ux * vy;
            return new Frame(ux, 0, uz, vx, vy, vz, wx, wy, wz);
        }

        /** Suma |v|₁: cuánto engorda un plano inclinado para no dejar agujeros al voxelizarlo. */
        double slabPadding() {
            return 0.5 * (Math.abs(vx) + Math.abs(vy) + Math.abs(vz) - 1.0);
        }
    }

    /** Forma en coordenadas locales; dx, dy, dz son el desplazamiento del centro del bloque desde el origen. */
    abstract static class Shape {
        final Frame frame;

        Shape(Frame frame) {
            this.frame = frame;
        }

        final boolean inside(int dx, int dy, int dz) {
            return insideLocal(
                    dx * frame.ux + dy * frame.uy + dz * frame.uz,
                    dx * frame.vx + dy * frame.vy + dz * frame.vz,
                    dx * frame.wx + dy * frame.wy + dz * frame.wz
            );
        }

        final boolean edge(int dx, int dy, int dz) {
            return edgeLocal(
                    dx * frame.ux + dy * frame.uy + dz * frame.uz,
                    dx * frame.vx + dy * frame.vy + dz * frame.vz,
                    dx * frame.wx + dy * frame.wy + dz * frame.wz
            );
        }

        /** @param y altura local desde el centro del bloque de origen (la base está en y = -0,5) */
        abstract boolean insideLocal(double x, double y, double z);

        boolean edgeLocal(double x, double y, double z) {
            return false;
        }

        /** Caja local {@code {minX, minY, minZ, maxX, maxY, maxZ}} que contiene la forma. */
        abstract double[] localBounds();

        /** La capa dy está abierta: la cáscara no se cierra contra ella (la base de un cono o de un domo). */
        boolean open(int dy) {
            return false;
        }

        final int[] bounds() {
            double[] local = localBounds();
            double[] result = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                    -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int corner = 0; corner < 8; corner++) {
                double x = (corner & 1) == 0 ? local[0] : local[3];
                double y = (corner & 2) == 0 ? local[1] : local[4];
                double z = (corner & 4) == 0 ? local[2] : local[5];
                double wx = x * frame.ux + y * frame.vx + z * frame.wx;
                double wy = x * frame.uy + y * frame.vy + z * frame.wy;
                double wz = x * frame.uz + y * frame.vz + z * frame.wz;
                result[0] = Math.min(result[0], wx);
                result[1] = Math.min(result[1], wy);
                result[2] = Math.min(result[2], wz);
                result[3] = Math.max(result[3], wx);
                result[4] = Math.max(result[4], wy);
                result[5] = Math.max(result[5], wz);
            }
            return new int[]{
                    (int) Math.floor(result[0]) - 1, (int) Math.floor(result[1]) - 1, (int) Math.floor(result[2]) - 1,
                    (int) Math.ceil(result[3]) + 1, (int) Math.ceil(result[4]) + 1, (int) Math.ceil(result[5]) + 1
            };
        }
    }

    /** Cono de base elíptica: radio norte-sur en z local y este-oeste en x local. */
    static final class Cone extends Shape {
        private final double radiusX;
        private final double radiusZ;
        private final int height;
        private final boolean down;

        Cone(ShapeRequest request, Frame frame) {
            super(frame);
            radiusZ = request.radius() + 0.5;
            radiusX = request.secondRadius() + 0.5;
            height = request.height();
            down = request.down();
        }

        @Override
        boolean insideLocal(double x, double y, double z) {
            double base = y + 0.5;
            if (base < 0 || base > height) {
                return false;
            }
            // El radio se mide en la cara ancha de cada capa: la base sale con el radio entero y la cúspide es un
            // solo bloque.
            double scale = down ? (base + 0.5) / height : 1.0 - (base - 0.5) / height;
            double a = x / (radiusX * scale);
            double b = z / (radiusZ * scale);
            return a * a + b * b <= 1.0;
        }

        @Override
        double[] localBounds() {
            double radius = Math.max(radiusX, radiusZ);
            return new double[]{-radius, -0.5, -radius, radius, height - 0.5, radius};
        }

        @Override
        boolean open(int dy) {
            return down ? dy >= height : dy < 0;
        }
    }

    /**
     * Domo facetado: en planta es un polígono regular y en alzado una poligonal de {@code bandas} tramos que sigue
     * un cuarto de elipse. Es la intersección de semiespacios, así que sus caras son planas y sus aristas rectas;
     * la bandera {@code -v} pinta esas aristas con otro patrón.
     */
    static final class Polydome extends Shape {
        private final int sides;
        private final int bands;
        private final double radius;
        private final double vertical;
        private final double limit;

        Polydome(ShapeRequest request) {
            super(Frame.yaw(0));
            sides = request.sides();
            bands = Math.max(2, (int) Math.ceil(sides / 4.0));
            radius = request.radius() + 0.5;
            vertical = Math.max(0.1, request.secondRadius());
            limit = radius * Math.cos(Math.PI / (4.0 * bands));
        }

        private double band(int band, double u, double v) {
            double angle = (band + 0.5) * (Math.PI / 2) / bands;
            return u * Math.cos(angle) + v * Math.sin(angle);
        }

        @Override
        boolean insideLocal(double x, double y, double z) {
            double base = y + 0.5;
            if (base < 0) {
                return false;
            }
            double u = polygonNorm(sides, x, z);
            double v = base / vertical;
            for (int band = 0; band < bands; band++) {
                if (band(band, u, v) > limit) {
                    return false;
                }
            }
            return true;
        }

        @Override
        boolean edgeLocal(double x, double y, double z) {
            double base = y + 0.5;
            if (base < 1.0) {
                return false;
            }
            // Medio bloque a cada lado de la arista: el producto con dos normales vecinas difiere en d·2·sen(α/2).
            if (polygonGap(sides, x, z) < Math.sin(Math.PI / sides)) {
                return true;
            }
            double u = polygonNorm(sides, x, z);
            double v = base / vertical;
            double first = Double.NEGATIVE_INFINITY;
            double second = Double.NEGATIVE_INFINITY;
            for (int band = 0; band < bands; band++) {
                double value = band(band, u, v);
                if (value > first) {
                    second = first;
                    first = value;
                } else if (value > second) {
                    second = value;
                }
            }
            return first - second < Math.sin(Math.PI / (4.0 * bands));
        }

        @Override
        double[] localBounds() {
            double reach = radius / Math.cos(Math.PI / sides);
            return new double[]{-reach, -0.5, -reach, reach, radius * vertical + 0.5, reach};
        }

        @Override
        boolean open(int dy) {
            return dy < 0;
        }
    }

    /** Prisma de base poligonal (o cuadrada) a lo largo del eje local «arriba». */
    static final class Polygon extends Shape {
        private final int sides;
        private final double radius;
        private final int height;
        private final double padding;

        Polygon(ShapeRequest request, Frame frame) {
            super(frame);
            sides = request.sides();
            radius = request.radius() + 0.5;
            height = Math.max(1, request.height());
            padding = frame.slabPadding();
        }

        @Override
        boolean insideLocal(double x, double y, double z) {
            double base = y + 0.5;
            if (base < -padding || base > height + padding) {
                return false;
            }
            return polygonNorm(sides, x, z) <= radius;
        }

        @Override
        double[] localBounds() {
            double reach = radius / Math.cos(Math.PI / sides) + padding;
            return new double[]{-reach, -0.5 - padding, -reach, reach, height - 0.5 + padding, reach};
        }
    }

    /** Tetraedro regular apoyado en una cara: el radio es el de la circunferencia de la base. */
    static final class Tetrahedron extends Shape {
        private final double radius;
        private final double height;

        Tetrahedron(ShapeRequest request, Frame frame) {
            super(frame);
            radius = request.radius() + 0.5;
            height = radius * Math.sqrt(2.0);
        }

        @Override
        boolean insideLocal(double x, double y, double z) {
            double base = y + 0.5;
            if (base < 0 || base - 0.5 >= height) {
                return false;
            }
            return polygonNorm(3, x, z) <= radius * (1.0 - Math.max(0.0, base - 0.5) / height);
        }

        @Override
        double[] localBounds() {
            return new double[]{-radius, -0.5, -radius, radius, height, radius};
        }
    }

    /** Toro alrededor del eje local «arriba», centrado en el bloque de origen. */
    static final class Torus extends Shape {
        private final double major;
        private final double minor;
        private final ShapeRequest.Cross cross;

        Torus(ShapeRequest request, Frame frame) {
            super(frame);
            major = request.radius();
            minor = request.secondRadius() + 0.5;
            cross = request.cross();
        }

        @Override
        boolean insideLocal(double x, double y, double z) {
            double q = Math.abs(Math.sqrt(x * x + z * z) - major) / minor;
            double h = Math.abs(y) / minor;
            return switch (cross) {
                case CIRCLE -> q * q + h * h <= 1.0;
                case DIAMOND -> q + h <= 1.0;
                case SQUARE -> Math.max(q, h) <= 1.0;
                case ROUNDED_SQUARE -> q * q * q * q + h * h * h * h <= 1.0;
            };
        }

        @Override
        double[] localBounds() {
            double reach = major + minor;
            return new double[]{-reach, -minor, -reach, reach, minor, reach};
        }
    }
}
