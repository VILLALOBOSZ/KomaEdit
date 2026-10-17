package com.komainu.moxia.placement;

import com.komainu.moxia.shape.tube.TubeFormula;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Las formas de la estructura {@code Shape} de ezEdits como funciones implícitas propias, en el cubo normalizado
 * [-1, 1]³ con +y arriba y +x delante. Las geométricas (cono, cilindro, pirámide…) llenan su caja; las orgánicas
 * (corazón, limón, hoja…) conservan sus proporciones y quedan centradas, así se estiran solo si se pide con
 * {@code -s}.
 */
public final class ProceduralShapes {
    private ProceduralShapes() {
    }

    /** Forma ya preparada para una colocación. */
    @FunctionalInterface
    public interface Implicit {
        /** Valor en (x, y, z): mayor que 0 es dentro. Las formas con nombre devuelven 1 dentro y 0 fuera. */
        double value(double x, double y, double z);

        default boolean inside(double x, double y, double z) {
            return value(x, y, z) > 0.0;
        }
    }

    /** Forma leída del comando; las que dependen del azar (pelo, fórmulas con {@code seed}) se crean por semilla. */
    @FunctionalInterface
    public interface Spec {
        Implicit create(long seed);
    }

    public static final List<String> NAMES = List.of("Cone", "Crystal", "Cuboid", "Curl", "Cylinder", "Ellipsoid",
            "Fur", "Heart", "Jellybean", "Leaf", "Lemon", "Onion", "Polygon", "Pyramid", "Supersphere", "Tetrahedron",
            "Torus");

    /** Lee {@code Cone}, {@code Crystal(Sides:6,Extrusion:0.5)}, {@code Torus(Thickness:0.4)} o {@code =fórmula}. */
    public static Spec parse(String input) {
        String text = ParamText.unquote(input);
        if (text.startsWith("=")) {
            return formula(text.substring(1));
        }
        ParamText.Call call = ParamText.call(text);
        String name = ParamText.normalize(call.name());
        switch (name) {
            case "cone", "cono" -> {
                call.requireOnly();
                return constant((x, y, z) -> bool(Math.sqrt(x * x + z * z) <= (1.0 - y) * 0.5));
            }
            case "cuboid", "cube", "box", "cubo", "caja" -> {
                call.requireOnly();
                return constant((x, y, z) -> 1.0);
            }
            case "cylinder", "cilindro" -> {
                call.requireOnly();
                return constant((x, y, z) -> bool(x * x + z * z <= 1.0));
            }
            case "ellipsoid", "sphere", "elipsoide", "esfera" -> {
                call.requireOnly();
                return constant((x, y, z) -> bool(x * x + y * y + z * z <= 1.0));
            }
            case "polygon", "poligono", "prism", "prisma" -> {
                call.requireOnly("sides", "s", "lados");
                Polygon polygon = new Polygon(sides(call, 6));
                return constant((x, y, z) -> bool(polygon.radius(x, z) <= 1.0));
            }
            case "pyramid", "piramide" -> {
                call.requireOnly("sides", "s", "lados");
                Polygon polygon = new Polygon(sides(call, 4));
                return constant((x, y, z) -> bool(polygon.radius(x, z) <= (1.0 - y) * 0.5));
            }
            case "crystal", "cristal" -> {
                call.requireOnly("sides", "s", "lados", "extrusion", "e");
                Polygon polygon = new Polygon(sides(call, 6));
                String value = call.get("extrusion", "e");
                double body = value == null ? 0.5 : ParamText.number(value, 0.0, 0.95, "Extrusion");
                return constant((x, y, z) -> {
                    double height = Math.abs(y);
                    double limit = height <= body ? 1.0 : (1.0 - height) / (1.0 - body);
                    return bool(polygon.radius(x, z) <= limit);
                });
            }
            case "supersphere", "superesfera" -> {
                call.requireOnly("exponent", "e", "exponente");
                String value = call.get("exponent", "e", "exponente");
                double exponent = value == null ? 4.0 : ParamText.number(value, 0.1, 64.0, "Exponent");
                return constant((x, y, z) -> bool(Math.pow(Math.abs(x), exponent) + Math.pow(Math.abs(y), exponent)
                        + Math.pow(Math.abs(z), exponent) <= 1.0));
            }
            case "tetrahedron", "tetraedro" -> {
                call.requireOnly();
                // Tetraedro regular inscrito en el cubo: vértices (1,1,1), (1,-1,-1), (-1,1,-1) y (-1,-1,1).
                return constant((x, y, z) -> bool(-x - y - z <= 1.0 && -x + y + z <= 1.0
                        && x - y + z <= 1.0 && x + y - z <= 1.0));
            }
            case "torus", "toro", "donut" -> {
                call.requireOnly("thickness", "t", "grosor");
                String value = call.get("thickness", "t", "grosor");
                double tube = (value == null ? 0.5 : ParamText.number(value, 0.02, 1.0, "Thickness")) * 0.5;
                double ring = 1.0 - tube;
                return constant((x, y, z) -> {
                    double radial = Math.sqrt(x * x + z * z) - ring;
                    return bool(radial * radial + y * y <= tube * tube);
                });
            }
            case "heart", "corazon" -> {
                call.requireOnly();
                return constant(Organic.HEART);
            }
            case "lemon", "limon" -> {
                call.requireOnly();
                return constant(Organic.LEMON);
            }
            case "onion", "cebolla", "bulbo" -> {
                call.requireOnly();
                return constant(Organic.ONION);
            }
            case "jellybean", "judia", "gominola" -> {
                call.requireOnly();
                return constant(Organic.JELLYBEAN);
            }
            case "leaf", "hoja" -> {
                call.requireOnly();
                return constant(Organic.LEAF);
            }
            case "curl", "rizo", "espiral" -> {
                call.requireOnly();
                return constant(Organic.CURL);
            }
            case "fur", "pelo", "mechon", "pelaje" -> {
                call.requireOnly("strands", "s", "hebras");
                String value = call.get("strands", "s", "hebras");
                int strands = value == null ? 18 : (int) ParamText.number(value, 1, 64, "Strands");
                return seed -> fur(seed, strands);
            }
            default -> throw new IllegalArgumentException("Forma desconocida: " + call.name() + " ("
                    + String.join(", ", NAMES) + " o =fórmula)");
        }
    }

    private static int sides(ParamText.Call call, int fallback) {
        String value = call.get("sides", "s", "lados");
        return value == null ? fallback : (int) ParamText.number(value, 3, 64, "Sides");
    }

    private static Spec constant(Implicit implicit) {
        return seed -> implicit;
    }

    private static double bool(boolean inside) {
        return inside ? 1.0 : 0.0;
    }

    /**
     * Fórmula propia con el compilador de KomaEdit: {@code x, y, z} de -1 a 1 y {@code seed}, un entero distinto
     * en cada colocación (y fijo dentro de ella). Dentro donde vale más que 0.
     */
    static Spec formula(String source) {
        String body = source.trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("La fórmula de la forma está vacía");
        }
        TubeFormula.compile("seed=0;" + body);
        return seed -> {
            int value = (int) (Math.floorMod(seed, 2_147_483_647L));
            TubeFormula.Runner runner = TubeFormula.compile("seed=" + value + ";" + body).runner(seed, 0);
            return (x, y, z) -> runner.evaluate(x, y, z, 0.0);
        };
    }

    /** Polígono regular con un lado mirando a +x, estirado para tocar los cuatro lados de su caja. */
    static final class Polygon {
        private final double sector;
        private final double apothem;
        private final double scaleX;
        private final double scaleZ;

        Polygon(int sides) {
            sector = Math.PI * 2.0 / sides;
            apothem = Math.cos(sector * 0.5);
            double extentX = 0.0;
            double extentZ = 0.0;
            for (int index = 0; index < sides; index++) {
                double angle = sector * (index + 0.5);
                extentX = Math.max(extentX, Math.abs(Math.cos(angle)));
                extentZ = Math.max(extentZ, Math.abs(Math.sin(angle)));
            }
            scaleX = extentX;
            scaleZ = extentZ;
        }

        /** 1 en el borde, 0 en el centro. */
        double radius(double x, double z) {
            double px = x * scaleX;
            double pz = z * scaleZ;
            double distance = Math.sqrt(px * px + pz * pz);
            if (distance < 1.0E-12) {
                return 0.0;
            }
            double angle = Math.atan2(pz, px) + sector * 0.5;
            double local = angle - Math.floor(angle / sector) * sector - sector * 0.5;
            return distance * Math.cos(local) / apothem;
        }
    }

    /** Tubo alrededor de una polilínea con radio variable; la base de rizos, judías y pelo. */
    static final class Tube {
        private final double[] points;
        private final double[] radii;
        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;

        Tube(List<double[]> centerline, double[] radii) {
            this.radii = radii;
            points = new double[centerline.size() * 3];
            double lowX = Double.POSITIVE_INFINITY;
            double lowY = Double.POSITIVE_INFINITY;
            double lowZ = Double.POSITIVE_INFINITY;
            double highX = Double.NEGATIVE_INFINITY;
            double highY = Double.NEGATIVE_INFINITY;
            double highZ = Double.NEGATIVE_INFINITY;
            double widest = 0.0;
            for (double radius : radii) {
                widest = Math.max(widest, radius);
            }
            for (int index = 0; index < centerline.size(); index++) {
                double[] point = centerline.get(index);
                points[index * 3] = point[0];
                points[index * 3 + 1] = point[1];
                points[index * 3 + 2] = point[2];
                lowX = Math.min(lowX, point[0]);
                lowY = Math.min(lowY, point[1]);
                lowZ = Math.min(lowZ, point[2]);
                highX = Math.max(highX, point[0]);
                highY = Math.max(highY, point[1]);
                highZ = Math.max(highZ, point[2]);
            }
            minX = lowX - widest;
            minY = lowY - widest;
            minZ = lowZ - widest;
            maxX = highX + widest;
            maxY = highY + widest;
            maxZ = highZ + widest;
        }

        boolean inside(double x, double y, double z) {
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                return false;
            }
            int segments = radii.length - 1;
            for (int index = 0; index < segments; index++) {
                int a = index * 3;
                int b = a + 3;
                double dx = points[b] - points[a];
                double dy = points[b + 1] - points[a + 1];
                double dz = points[b + 2] - points[a + 2];
                double length = dx * dx + dy * dy + dz * dz;
                double amount = length < 1.0E-12 ? 0.0
                        : ((x - points[a]) * dx + (y - points[a + 1]) * dy + (z - points[a + 2]) * dz) / length;
                amount = Math.clamp(amount, 0.0, 1.0);
                double radius = radii[index] + (radii[index + 1] - radii[index]) * amount;
                double ox = x - points[a] - dx * amount;
                double oy = y - points[a + 1] - dy * amount;
                double oz = z - points[a + 2] - dz * amount;
                if (ox * ox + oy * oy + oz * oz <= radius * radius) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Mechón de hebras que nacen en la base, se inclinan y se curvan; cada colocación sale distinta. */
    static Implicit fur(long seed, int strands) {
        SplittableRandom random = new SplittableRandom(seed ^ 0xF0F0_5EEDL);
        List<Tube> tubes = new ArrayList<>(strands);
        int steps = 8;
        for (int strand = 0; strand < strands; strand++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double spread = Math.sqrt(random.nextDouble()) * 0.55;
            double baseX = Math.cos(angle) * spread;
            double baseZ = Math.sin(angle) * spread;
            double leanAngle = angle + (random.nextDouble() - 0.5) * 1.2;
            double lean = 0.15 + random.nextDouble() * 0.3;
            double height = 0.65 + random.nextDouble() * 0.35;
            double bend = 0.15 + random.nextDouble() * 0.35;
            List<double[]> line = new ArrayList<>(steps + 1);
            double[] radii = new double[steps + 1];
            for (int step = 0; step <= steps; step++) {
                double t = step / (double) steps;
                double out = lean * t + bend * t * t;
                line.add(new double[]{
                        clamp(baseX + Math.cos(leanAngle) * out),
                        -1.0 + 2.0 * height * t,
                        clamp(baseZ + Math.sin(leanAngle) * out)
                });
                radii[step] = 0.11 * (1.0 - t) + 0.035;
            }
            tubes.add(new Tube(line, radii));
        }
        return (x, y, z) -> {
            for (Tube tube : tubes) {
                if (tube.inside(x, y, z)) {
                    return 1.0;
                }
            }
            return 0.0;
        };
    }

    private static double clamp(double value) {
        return Math.clamp(value, -0.95, 0.95);
    }

    /**
     * Formas orgánicas definidas en su propio espacio y llevadas al cubo [-1, 1] con la misma escala en los tres
     * ejes (se busca su caja una vez al cargar la clase).
     */
    static final class Organic implements Implicit {
        static final Organic HEART = new Organic(Organic::heart, 1.6);
        static final Organic LEMON = new Organic(Organic::lemon, 1.3);
        static final Organic ONION = new Organic(Organic::onion, 1.3);
        static final Organic JELLYBEAN = new Organic(jellybean(), 1.6);
        static final Organic LEAF = new Organic(Organic::leaf, 1.3);
        static final Organic CURL = new Organic(curl(), 1.6);

        private final Implicit raw;
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final double half;

        private Organic(Implicit raw, double domain) {
            this.raw = raw;
            int samples = 72;
            double lowX = Double.POSITIVE_INFINITY;
            double lowY = Double.POSITIVE_INFINITY;
            double lowZ = Double.POSITIVE_INFINITY;
            double highX = Double.NEGATIVE_INFINITY;
            double highY = Double.NEGATIVE_INFINITY;
            double highZ = Double.NEGATIVE_INFINITY;
            double step = domain * 2.0 / samples;
            for (int i = 0; i <= samples; i++) {
                double x = -domain + i * step;
                for (int j = 0; j <= samples; j++) {
                    double y = -domain + j * step;
                    for (int k = 0; k <= samples; k++) {
                        double z = -domain + k * step;
                        if (raw.value(x, y, z) > 0.0) {
                            lowX = Math.min(lowX, x);
                            lowY = Math.min(lowY, y);
                            lowZ = Math.min(lowZ, z);
                            highX = Math.max(highX, x);
                            highY = Math.max(highY, y);
                            highZ = Math.max(highZ, z);
                        }
                    }
                }
            }
            centerX = (lowX + highX) * 0.5;
            centerY = (lowY + highY) * 0.5;
            centerZ = (lowZ + highZ) * 0.5;
            half = Math.max(highX - lowX, Math.max(highY - lowY, highZ - lowZ)) * 0.5 + step * 0.5;
        }

        @Override
        public double value(double x, double y, double z) {
            return raw.value(centerX + x * half, centerY + y * half, centerZ + z * half) > 0.0 ? 1.0 : 0.0;
        }

        /** Corazón de Taubin: ancho en z, delgado en x (mira hacia delante) y la punta abajo. */
        private static double heart(double x, double y, double z) {
            double width = z;
            double depth = x;
            double up = y;
            double base = width * width + 2.25 * depth * depth + up * up - 1.0;
            double value = base * base * base - width * width * up * up * up - 0.1125 * depth * depth * up * up * up;
            return -value;
        }

        /** Limón tumbado a lo largo de x, con sus dos pezones. */
        private static double lemon(double x, double y, double z) {
            double along = Math.abs(x);
            double shoulder = 0.72 * Math.sqrt(0.08);
            double radius;
            if (along <= 0.85) {
                double t = along / 0.85;
                radius = 0.72 * Math.sqrt(1.0 - t * t * t * 0.92);
            } else if (along <= 1.05) {
                radius = shoulder * (1.05 - along) / 0.2;
            } else {
                return -1.0;
            }
            return radius * radius - (y * y + z * z);
        }

        /** Bulbo de cebolla: base redonda, panza ancha y cuello que se afila hacia la punta de arriba. */
        private static double onion(double x, double y, double z) {
            double t = (y + 1.0) * 0.5;
            if (t < 0.0 || t > 1.0) {
                return -1.0;
            }
            double radius = 1.25 * Math.sin(Math.PI * Math.pow(t, 0.62)) * Math.pow(1.0 - t, 0.55);
            return radius * radius - (x * x + z * z);
        }

        /** Hoja: el limbo crece hacia arriba (y), ancho en z, fino en x y algo abarquillado, con su peciolo. */
        private static double leaf(double x, double y, double z) {
            if (y < -1.0 || y > 1.0) {
                return -1.0;
            }
            if (y < -0.78) {
                return 0.05 * 0.05 - (x * x + z * z);
            }
            double t = (y + 0.78) / 1.78;
            double width = 0.62 * Math.pow(Math.sin(Math.PI * t), 0.7) * (1.0 - 0.3 * t);
            if (width < 1.0E-4) {
                return -1.0;
            }
            double across = z / width;
            if (Math.abs(across) > 1.0) {
                return -1.0;
            }
            double center = 0.16 * width * across * across;
            double thickness = 0.03 + 0.04 * (1.0 - across * across);
            return thickness - Math.abs(x - center);
        }

        private static Implicit jellybean() {
            List<double[]> line = new ArrayList<>();
            int segments = 20;
            double bend = 1.7;
            double span = 0.6;
            double[] radii = new double[segments + 1];
            for (int index = 0; index <= segments; index++) {
                double angle = -span + 2.0 * span * index / segments;
                line.add(new double[]{bend * Math.cos(angle) - bend, 0.0, bend * Math.sin(angle)});
                radii[index] = 0.42;
            }
            Tube tube = new Tube(line, radii);
            return (x, y, z) -> tube.inside(x, y, z) ? 1.0 : 0.0;
        }

        /** Rizo de helecho: un tallo corto que sube y se enrosca en espiral logarítmica en el plano x-y. */
        private static Implicit curl() {
            List<double[]> line = new ArrayList<>();
            List<Double> radii = new ArrayList<>();
            double start = 1.0;
            line.add(new double[]{start, -0.75, 0.0});
            radii.add(0.26);
            int steps = 90;
            double turns = Math.PI * 3.2;
            double decay = 0.2;
            for (int index = 0; index <= steps; index++) {
                double angle = turns * index / steps;
                double radius = start * Math.exp(-decay * angle);
                // Empieza a la derecha del centro (x = start) y gira hacia arriba y a la izquierda.
                line.add(new double[]{radius * Math.cos(angle), 0.25 + radius * Math.sin(angle), 0.0});
                radii.add(0.24 * Math.exp(-0.16 * angle) + 0.03);
            }
            double[] values = new double[radii.size()];
            for (int index = 0; index < values.length; index++) {
                values[index] = radii.get(index);
            }
            Tube tube = new Tube(line, values);
            return (x, y, z) -> tube.inside(x, y, z) ? 1.0 : 0.0;
        }
    }
}
