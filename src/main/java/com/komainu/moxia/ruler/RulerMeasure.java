package com.komainu.moxia.ruler;

import com.komainu.moxia.shape.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que mide la regla con sus puntos. Entre bloques se mide de centro a centro (del bloque al de al lado hay 1) y las
 * cajas cuentan bloques (de 0 a 11 son 12); con un punto suelto, la distancia tal cual.
 */
public sealed interface RulerMeasure {
    /** Aún sin puntos suficientes: {@code points} son los que hay. */
    record Pending(RulerMode mode, List<RulerPoint> points) implements RulerMeasure {
        public Pending {
            points = List.copyOf(points);
        }
    }

    /** Diferencia entre dos puntos, sin signo, por ejes. */
    record Delta(double x, double y, double z) {
        public static Delta between(Vec3 from, Vec3 to) {
            return new Delta(Math.abs(to.x() - from.x()), Math.abs(to.y() - from.y()), Math.abs(to.z() - from.z()));
        }

        public double euclidean() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        public double manhattan() {
            return x + y + z;
        }

        public double largest() {
            return Math.max(x, Math.max(y, z));
        }

        Delta plus(Delta other) {
            return new Delta(x + other.x, y + other.y, z + other.z);
        }
    }

    /** Un tramo del recorrido. */
    record Segment(RulerPoint from, RulerPoint to, Delta delta) {
        /** Bloques que pone una línea de WorldEdit entre los dos (contando los dos); 0 entre puntos sueltos. */
        public long blocks() {
            return from.isBlock() && to.isBlock() ? Math.round(delta.largest()) + 1 : 0;
        }
    }

    /**
     * Recorrido por dos o más puntos.
     *
     * @param length    la suma de los tramos en línea recta (euclídea)
     * @param manhattan la suma de los tramos yendo solo por los ejes
     * @param axes      lo que se recorre en cada eje, sumando los tramos
     * @param straight  del primer punto al último en línea recta
     */
    record Line(List<RulerPoint> points, List<Segment> segments, double length, double manhattan, Delta axes,
                Delta straight) implements RulerMeasure {
        public Line {
            points = List.copyOf(points);
            segments = List.copyOf(segments);
        }

        public static Line of(List<RulerPoint> points) {
            if (points.size() < 2) {
                throw new IllegalArgumentException("Un recorrido necesita dos puntos");
            }
            List<Segment> segments = new ArrayList<>();
            double length = 0.0;
            Delta axes = new Delta(0, 0, 0);
            for (int index = 1; index < points.size(); index++) {
                RulerPoint from = points.get(index - 1);
                RulerPoint to = points.get(index);
                Delta delta = Delta.between(from.center(), to.center());
                segments.add(new Segment(from, to, delta));
                length += delta.euclidean();
                axes = axes.plus(delta);
            }
            Delta straight = Delta.between(points.getFirst().center(), points.getLast().center());
            return new Line(points, segments, length, axes.manhattan(), axes, straight);
        }
    }

    /**
     * Círculo con centro y un punto del borde. El plano es el de los dos ejes en que más se separan (si están a la
     * misma altura, horizontal): {@code axis} es el eje perpendicular (0 X, 1 Y, 2 Z) y {@code offset} lo que el borde
     * se sale de ese plano.
     */
    record Circle(RulerPoint center, RulerPoint edge, int axis, double radius, double offset)
            implements RulerMeasure {
        public static Circle of(RulerPoint center, RulerPoint edge) {
            Delta delta = Delta.between(center.center(), edge.center());
            // El eje que menos cambia es el del círculo; a igualdad, el vertical y luego X.
            int axis = 1;
            double smallest = delta.y();
            if (delta.x() < smallest - 1.0E-9) {
                axis = 0;
                smallest = delta.x();
            }
            if (delta.z() < smallest - 1.0E-9) {
                axis = 2;
                smallest = delta.z();
            }
            double radius = switch (axis) {
                case 0 -> Math.hypot(delta.y(), delta.z());
                case 1 -> Math.hypot(delta.x(), delta.z());
                default -> Math.hypot(delta.x(), delta.y());
            };
            return new Circle(center, edge, axis, radius, smallest);
        }

        public double diameter() {
            return radius * 2.0;
        }

        /**
         * Bloques de lado a lado de un círculo de bloques con este radio ({@code 2r + 1}, como {@code //cyl}); 0 si
         * algún punto es suelto.
         */
        public long blocksAcross() {
            return center.isBlock() && edge.isBlock() ? Math.round(radius) * 2 + 1 : 0;
        }

        public double circumference() {
            return 2.0 * Math.PI * radius;
        }

        public double area() {
            return Math.PI * radius * radius;
        }

        /** {@code XZ}, {@code YZ} o {@code XY}: el plano del círculo. */
        public String plane() {
            return switch (axis) {
                case 0 -> "YZ";
                case 1 -> "XZ";
                default -> "XY";
            };
        }
    }

    /**
     * Caja entre dos esquinas: {@code width} en X, {@code height} en Y y {@code length} en Z. Con bloques cuenta los
     * dos de los extremos; con puntos sueltos, la distancia.
     */
    record Box(RulerPoint first, RulerPoint second, double width, double height, double length)
            implements RulerMeasure {
        public static Box of(RulerPoint first, RulerPoint second) {
            Delta delta = Delta.between(first.center(), second.center());
            double extra = first.isBlock() && second.isBlock() ? 1.0 : 0.0;
            return new Box(first, second, delta.x() + extra, delta.y() + extra, delta.z() + extra);
        }

        public boolean blocks() {
            return first.isBlock() && second.isBlock();
        }

        public double volume() {
            return width * height * length;
        }

        /** De una esquina de la caja a la opuesta. */
        public double diagonal() {
            return Math.sqrt(width * width + height * height + length * length);
        }

        /** Esquina menor y mayor de lo que ocupa en el mundo (con bloques, sus caras de fuera). */
        public Vec3 min() {
            if (blocks()) {
                return new Vec3(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()),
                        Math.min(first.z(), second.z()));
            }
            Vec3 a = first.center();
            Vec3 b = second.center();
            return new Vec3(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()));
        }

        public Vec3 max() {
            Vec3 min = min();
            return new Vec3(min.x() + width, min.y() + height, min.z() + length);
        }
    }
}
