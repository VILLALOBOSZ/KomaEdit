package com.komainu.moxia.path;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Operaciones puras sobre los puntos de una trayectoria. Ninguna toca el mundo ni el jugador: reciben la lista y
 * devuelven otra nueva, sin puntos consecutivos repetidos. Los índices son de base 0.
 */
public final class PathEditing {
    private PathEditing() {
    }

    /** Punto a mitad entre {@code index} y el siguiente (el primero si el lazo está cerrado). */
    public static List<BlockVector3> insertMidpoint(List<BlockVector3> points, int index, boolean closed) {
        requireIndex(points, index);
        BlockVector3 from = points.get(index);
        BlockVector3 to;
        if (index + 1 < points.size()) {
            to = points.get(index + 1);
        } else if (closed && points.size() > 1) {
            to = points.getFirst();
        } else if (points.size() > 1) {
            // Tras el último punto de un recorrido abierto: prolonga el último tramo a la mitad.
            BlockVector3 previous = points.get(index - 1);
            BlockVector3 step = from.subtract(previous);
            to = from.add(step);
        } else {
            throw new IllegalArgumentException("Hacen falta dos puntos para insertar uno entre ellos");
        }
        BlockVector3 middle = BlockVector3.at(
                Math.floorDiv(from.x() + to.x(), 2),
                Math.floorDiv(from.y() + to.y(), 2),
                Math.floorDiv(from.z() + to.z(), 2)
        );
        if (middle.equals(from) || middle.equals(to)) {
            throw new IllegalArgumentException("El punto " + (index + 1)
                    + " y el siguiente están pegados: no cabe otro entre ellos");
        }
        return insert(points, index + 1, middle);
    }

    /** Inserta {@code point} para que quede en la posición {@code index}. */
    public static List<BlockVector3> insert(List<BlockVector3> points, int index, BlockVector3 point) {
        if (index < 0 || index > points.size()) {
            throw new IllegalArgumentException("Posición fuera del recorrido: " + (index + 1));
        }
        List<BlockVector3> result = new ArrayList<>(points);
        result.add(index, point);
        return withoutRepeats(result);
    }

    public static List<BlockVector3> remove(List<BlockVector3> points, int index) {
        requireIndex(points, index);
        List<BlockVector3> result = new ArrayList<>(points);
        result.remove(index);
        return withoutRepeats(result);
    }

    public static List<BlockVector3> set(List<BlockVector3> points, int index, BlockVector3 point) {
        requireIndex(points, index);
        List<BlockVector3> result = new ArrayList<>(points);
        result.set(index, point);
        return withoutRepeats(result);
    }

    public static List<BlockVector3> reverse(List<BlockVector3> points) {
        return List.copyOf(points.reversed());
    }

    public static List<BlockVector3> translate(List<BlockVector3> points, int dx, int dy, int dz) {
        return points.stream().map(point -> point.add(dx, dy, dz)).toList();
    }

    /**
     * Refleja el recorrido sobre el plano medio de su caja en el eje dado, de modo que ocupa el mismo sitio.
     * Con {@code append} conserva el original y añade el reflejo al final en sentido inverso: media curva pasa a
     * ser una curva simétrica completa (el punto de unión no se repite).
     */
    public static List<BlockVector3> mirror(List<BlockVector3> points, char axis, boolean append) {
        if (points.isEmpty()) {
            return List.of();
        }
        char normalized = Character.toLowerCase(axis);
        int sum;
        if (append) {
            // Al añadir, el espejo se apoya en el último punto: la curva sigue desde ahí hacia el otro lado.
            BlockVector3 last = points.getLast();
            sum = 2 * component(last, normalized);
        } else {
            sum = minimum(points, normalized) + maximum(points, normalized);
        }
        List<BlockVector3> mirrored = points.stream().map(point -> switch (normalized) {
            case 'x' -> BlockVector3.at(sum - point.x(), point.y(), point.z());
            case 'y' -> BlockVector3.at(point.x(), sum - point.y(), point.z());
            case 'z' -> BlockVector3.at(point.x(), point.y(), sum - point.z());
            default -> throw new IllegalArgumentException("Eje desconocido: " + axis + " (x, y o z)");
        }).toList();
        if (!append) {
            return mirrored;
        }
        List<BlockVector3> result = new ArrayList<>(points);
        result.addAll(mirrored.reversed());
        return withoutRepeats(result);
    }

    /** Gira el recorrido sobre el eje vertical, alrededor del centro de su caja, en pasos de 90°. */
    public static List<BlockVector3> rotateY(List<BlockVector3> points, int degrees) {
        int turns = Math.floorMod(degrees, 360);
        if (turns % 90 != 0) {
            throw new IllegalArgumentException("El giro va en pasos de 90°: 90, 180 o 270");
        }
        if (points.isEmpty() || turns == 0) {
            return List.copyOf(points);
        }
        // Centro doblado para trabajar con enteros; floorDiv deja el resultado en la rejilla de bloques.
        int sumX = minimum(points, 'x') + maximum(points, 'x');
        int sumZ = minimum(points, 'z') + maximum(points, 'z');
        List<BlockVector3> result = new ArrayList<>(points.size());
        for (BlockVector3 point : points) {
            int x2 = 2 * point.x() - sumX;
            int z2 = 2 * point.z() - sumZ;
            int rx = x2;
            int rz = z2;
            for (int step = 0; step < turns / 90; step++) {
                int previous = rx;
                rx = -rz;
                rz = previous;
            }
            result.add(BlockVector3.at(Math.floorDiv(rx + sumX, 2), point.y(), Math.floorDiv(rz + sumZ, 2)));
        }
        return withoutRepeats(result);
    }

    /** Ajusta cada punto al múltiplo de {@code step} más cercano; la altura solo si {@code vertical}. */
    public static List<BlockVector3> snap(List<BlockVector3> points, int step, boolean vertical) {
        if (step < 1) {
            throw new IllegalArgumentException("El paso de la rejilla debe ser al menos 1");
        }
        List<BlockVector3> result = points.stream().map(point -> BlockVector3.at(
                snap(point.x(), step),
                vertical ? snap(point.y(), step) : point.y(),
                snap(point.z(), step)
        )).toList();
        return withoutRepeats(result);
    }

    /**
     * Suaviza sin cambiar el número de puntos: cada punto se acerca a la media de sus vecinos en la proporción
     * {@code strength}. En un recorrido abierto los extremos no se mueven.
     */
    public static List<BlockVector3> relax(List<BlockVector3> points, boolean closed, double strength, int passes) {
        if (strength <= 0.0 || strength > 1.0) {
            throw new IllegalArgumentException("La fuerza del suavizado va de 0 a 1");
        }
        int size = points.size();
        if (size < 3) {
            return List.copyOf(points);
        }
        double[][] current = new double[size][];
        for (int index = 0; index < size; index++) {
            BlockVector3 point = points.get(index);
            current[index] = new double[]{point.x(), point.y(), point.z()};
        }
        for (int pass = 0; pass < passes; pass++) {
            double[][] next = new double[size][];
            for (int index = 0; index < size; index++) {
                boolean endpoint = !closed && (index == 0 || index == size - 1);
                if (endpoint) {
                    next[index] = current[index].clone();
                    continue;
                }
                double[] previous = current[Math.floorMod(index - 1, size)];
                double[] following = current[(index + 1) % size];
                next[index] = new double[3];
                for (int axis = 0; axis < 3; axis++) {
                    double average = (previous[axis] + following[axis]) * 0.5;
                    next[index][axis] = current[index][axis] + (average - current[index][axis]) * strength;
                }
            }
            current = next;
        }
        List<BlockVector3> result = new ArrayList<>(size);
        for (double[] point : current) {
            result.add(BlockVector3.at(Math.round(point[0]), Math.round(point[1]), Math.round(point[2])));
        }
        return withoutRepeats(result);
    }

    /**
     * Quita los puntos que se separan menos de {@code tolerance} bloques de la recta entre sus vecinos
     * (Ramer–Douglas–Peucker). Los extremos se conservan siempre.
     */
    public static List<BlockVector3> simplify(List<BlockVector3> points, double tolerance) {
        if (tolerance < 0.0) {
            throw new IllegalArgumentException("La tolerancia no puede ser negativa");
        }
        if (points.size() < 3) {
            return List.copyOf(points);
        }
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        simplify(points, 0, points.size() - 1, tolerance, keep);
        List<BlockVector3> result = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            if (keep[index]) {
                result.add(points.get(index));
            }
        }
        return result;
    }

    /** Añade un punto a mitad de cada tramo que mida más de un bloque. */
    public static List<BlockVector3> subdivide(List<BlockVector3> points, boolean closed, int maximum) {
        List<BlockVector3> result = new ArrayList<>();
        int segments = closed ? points.size() : points.size() - 1;
        for (int index = 0; index < points.size(); index++) {
            BlockVector3 from = points.get(index);
            result.add(from);
            if (index < segments) {
                BlockVector3 to = points.get((index + 1) % points.size());
                BlockVector3 middle = BlockVector3.at(
                        Math.floorDiv(from.x() + to.x(), 2),
                        Math.floorDiv(from.y() + to.y(), 2),
                        Math.floorDiv(from.z() + to.z(), 2)
                );
                if (!middle.equals(from) && !middle.equals(to)) {
                    result.add(middle);
                }
            }
        }
        if (result.size() > maximum) {
            throw new IllegalArgumentException("Subdividir dejaría " + result.size() + " puntos (máximo " + maximum + ")");
        }
        return withoutRepeats(result);
    }

    /** Longitud del recorrido en bloques, con el tramo de cierre si está cerrado. */
    public static double length(List<BlockVector3> points, boolean closed) {
        double total = 0.0;
        for (int index = 1; index < points.size(); index++) {
            total += distance(points.get(index - 1), points.get(index));
        }
        if (closed && points.size() > 2) {
            total += distance(points.getLast(), points.getFirst());
        }
        return total;
    }

    public static List<BlockVector3> withoutRepeats(List<BlockVector3> points) {
        List<BlockVector3> result = new ArrayList<>(points.size());
        for (BlockVector3 point : points) {
            if (result.isEmpty() || !result.getLast().equals(point)) {
                result.add(point);
            }
        }
        return List.copyOf(result);
    }

    public static char axis(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != 1 || "xyz".indexOf(normalized.charAt(0)) < 0) {
            throw new IllegalArgumentException("Eje desconocido: " + input + " (x, y o z)");
        }
        return normalized.charAt(0);
    }

    private static void simplify(List<BlockVector3> points, int first, int last, double tolerance, boolean[] keep) {
        if (last - first < 2) {
            return;
        }
        double farthest = -1.0;
        int farthestIndex = -1;
        for (int index = first + 1; index < last; index++) {
            double distance = distanceToSegment(points.get(index), points.get(first), points.get(last));
            if (distance > farthest) {
                farthest = distance;
                farthestIndex = index;
            }
        }
        if (farthest > tolerance) {
            keep[farthestIndex] = true;
            simplify(points, first, farthestIndex, tolerance, keep);
            simplify(points, farthestIndex, last, tolerance, keep);
        }
    }

    static double distanceToSegment(BlockVector3 point, BlockVector3 start, BlockVector3 end) {
        double sx = end.x() - start.x();
        double sy = end.y() - start.y();
        double sz = end.z() - start.z();
        double px = point.x() - start.x();
        double py = point.y() - start.y();
        double pz = point.z() - start.z();
        double lengthSquared = sx * sx + sy * sy + sz * sz;
        double t = lengthSquared == 0.0 ? 0.0 : Math.clamp((px * sx + py * sy + pz * sz) / lengthSquared, 0.0, 1.0);
        double dx = px - sx * t;
        double dy = py - sy * t;
        double dz = pz - sz * t;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double distance(BlockVector3 first, BlockVector3 second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int snap(int value, int step) {
        return Math.round((float) value / step) * step;
    }

    private static int component(BlockVector3 point, char axis) {
        return switch (axis) {
            case 'x' -> point.x();
            case 'y' -> point.y();
            case 'z' -> point.z();
            default -> throw new IllegalArgumentException("Eje desconocido: " + axis + " (x, y o z)");
        };
    }

    private static int minimum(List<BlockVector3> points, char axis) {
        return points.stream().mapToInt(point -> component(point, axis)).min().orElse(0);
    }

    private static int maximum(List<BlockVector3> points, char axis) {
        return points.stream().mapToInt(point -> component(point, axis)).max().orElse(0);
    }

    private static void requireIndex(List<BlockVector3> points, int index) {
        if (index < 0 || index >= points.size()) {
            throw new IllegalArgumentException(points.isEmpty()
                    ? "La trayectoria no tiene puntos"
                    : "El punto " + (index + 1) + " no existe (hay " + points.size() + ")");
        }
    }
}
