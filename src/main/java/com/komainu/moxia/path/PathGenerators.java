package com.komainu.moxia.path;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Genera trayectorias completas a partir de unos pocos datos: círculos, polígonos, espirales, hélices, arcos y
 * catenarias. Todo es puro y determinista; los puntos salen redondeados a bloque y sin repeticiones seguidas.
 */
public final class PathGenerators {
    private static final double EPSILON = 1.0E-9;

    private PathGenerators() {
    }

    /** Hacia dónde se curva un arco o cuelga una catenaria respecto a la recta entre sus extremos. */
    public enum Bulge {
        DOWN("down", "abajo"),
        UP("up", "arriba"),
        LEFT("left", "izquierda"),
        RIGHT("right", "derecha");

        private final String id;
        private final String spanish;

        Bulge(String id, String spanish) {
            this.id = id;
            this.spanish = spanish;
        }

        public String id() {
            return id;
        }

        public static Bulge byId(String input) {
            String normalized = input.trim().toLowerCase(Locale.ROOT);
            for (Bulge bulge : values()) {
                if (bulge.id.equals(normalized) || bulge.spanish.equals(normalized)) {
                    return bulge;
                }
            }
            throw new IllegalArgumentException("Dirección desconocida: " + input + " (down, up, left, right)");
        }
    }

    public static List<BlockVector3> circle(BlockVector3 center, double radius, int points) {
        return polygon(center, radius, points, 0.0);
    }

    /** Polígono regular en horizontal; con muchos lados es un círculo. {@code startDegrees} gira el primer vértice. */
    public static List<BlockVector3> polygon(BlockVector3 center, double radius, int sides, double startDegrees) {
        if (sides < 3) {
            throw new IllegalArgumentException("Hacen falta al menos tres lados");
        }
        LinkedHashSet<BlockVector3> unique = new LinkedHashSet<>();
        double start = Math.toRadians(startDegrees);
        for (int index = 0; index < sides; index++) {
            double angle = start + Math.PI * 2.0 * index / sides;
            unique.add(BlockVector3.at(
                    center.x() + Math.round(Math.cos(angle) * radius),
                    center.y(),
                    center.z() + Math.round(Math.sin(angle) * radius)
            ));
        }
        List<BlockVector3> result = new ArrayList<>(unique);
        if (result.size() < 3) {
            throw new IllegalArgumentException("El radio es demasiado pequeño para tantos puntos distintos");
        }
        return List.copyOf(result);
    }

    /**
     * Espiral alrededor de {@code center}: el radio pasa de {@code startRadius} a {@code endRadius} en
     * {@code turns} vueltas mientras sube {@code height} bloques (negativo baja). Con radios iguales es una hélice.
     */
    public static List<BlockVector3> spiral(
            BlockVector3 center,
            double startRadius,
            double endRadius,
            double turns,
            double height,
            int points
    ) {
        if (turns <= 0.0) {
            throw new IllegalArgumentException("Las vueltas deben ser mayores que 0");
        }
        if (points < 2) {
            throw new IllegalArgumentException("Hacen falta al menos dos puntos");
        }
        List<BlockVector3> result = new ArrayList<>(points);
        for (int index = 0; index < points; index++) {
            double t = (double) index / (points - 1);
            double angle = Math.PI * 2.0 * turns * t;
            double radius = startRadius + (endRadius - startRadius) * t;
            result.add(BlockVector3.at(
                    center.x() + Math.round(Math.cos(angle) * radius),
                    center.y() + Math.round(height * t),
                    center.z() + Math.round(Math.sin(angle) * radius)
            ));
        }
        return requireTwo(PathEditing.withoutRepeats(result));
    }

    /** Puntos que reparte una espiral de forma razonable: unos doce por vuelta, dentro del máximo. */
    public static int suggestedPoints(double turns, int maximum) {
        return (int) Math.clamp(Math.ceil(turns * 12.0) + 1, 8, maximum);
    }

    /**
     * Arco de circunferencia entre {@code start} y {@code end} cuya flecha (separación máxima respecto a la recta)
     * es {@code sagitta} bloques hacia {@code bulge}. Una flecha mayor que media cuerda da más de media vuelta.
     */
    public static List<BlockVector3> arc(BlockVector3 start, BlockVector3 end, double sagitta, Bulge bulge, int points) {
        if (sagitta <= 0.0) {
            throw new IllegalArgumentException("La altura del arco debe ser mayor que 0");
        }
        Frame frame = Frame.of(start, end, bulge);
        double half = frame.length() * 0.5;
        double radius = (half * half + sagitta * sagitta) / (2.0 * sagitta);
        double halfAngle = 2.0 * Math.atan2(sagitta, half);
        List<BlockVector3> result = new ArrayList<>(points);
        for (int index = 0; index < points; index++) {
            double angle = -halfAngle + 2.0 * halfAngle * index / (points - 1);
            double along = half + radius * Math.sin(angle);
            double offset = sagitta - radius + radius * Math.cos(angle);
            result.add(frame.at(along, offset));
        }
        return withEnds(result, start, end);
    }

    /**
     * Catenaria (cadena colgante) entre dos puntos. {@code sag} es la comba: la mayor distancia entre la cadena y
     * la recta que une los extremos, medida en vertical para {@code down}/{@code up} y en el plano lateral para
     * {@code left}/{@code right}. Con {@code down} es la curva física de una cuerda: si un extremo está más alto,
     * el punto más bajo se desplaza hacia el otro; {@code up} es la misma curva invertida, como un arco.
     */
    public static List<BlockVector3> catenary(BlockVector3 start, BlockVector3 end, double sag, Bulge bulge, int points) {
        if (sag <= 0.0) {
            throw new IllegalArgumentException("La comba debe ser mayor que 0");
        }
        if (points < 3) {
            throw new IllegalArgumentException("Hacen falta al menos tres puntos");
        }
        List<BlockVector3> result = new ArrayList<>(points);
        if (bulge == Bulge.DOWN || bulge == Bulge.UP) {
            double dx = end.x() - start.x();
            double dz = end.z() - start.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal < 1.0) {
                throw new IllegalArgumentException("Los extremos están uno encima del otro: la cadena no tiene dónde colgar");
            }
            double sign = bulge == Bulge.DOWN ? 1.0 : -1.0;
            double rise = (end.y() - start.y()) * sign;
            Catenary curve = Catenary.solve(horizontal, rise, sag);
            for (int index = 0; index < points; index++) {
                double x = horizontal * index / (points - 1);
                double y = curve.y(x) * sign;
                result.add(BlockVector3.at(
                        Math.round(start.x() + dx / horizontal * x),
                        Math.round(start.y() + y),
                        Math.round(start.z() + dz / horizontal * x)
                ));
            }
        } else {
            Frame frame = Frame.of(start, end, bulge);
            Catenary curve = Catenary.solve(frame.length(), 0.0, sag);
            for (int index = 0; index < points; index++) {
                double along = frame.length() * index / (points - 1);
                result.add(frame.at(along, -curve.y(along)));
            }
        }
        return withEnds(result, start, end);
    }

    private static List<BlockVector3> withEnds(List<BlockVector3> points, BlockVector3 start, BlockVector3 end) {
        List<BlockVector3> result = new ArrayList<>(points);
        result.set(0, start);
        result.set(result.size() - 1, end);
        return requireTwo(PathEditing.withoutRepeats(result));
    }

    private static List<BlockVector3> requireTwo(List<BlockVector3> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("La trayectoria generada es demasiado pequeña");
        }
        return points;
    }

    /**
     * y(x) = a·cosh((x − x0)/a) + c con y(0) = 0 e y(span) = rise. El parámetro {@code a} se busca por bisección
     * para que la comba (máxima distancia vertical bajo la cuerda) sea la pedida.
     */
    record Catenary(double a, double x0, double c, double span, double rise) {
        static Catenary solve(double span, double rise, double sag) {
            if (span <= EPSILON) {
                throw new IllegalArgumentException("Los extremos de la catenaria coinciden");
            }
            double low = Math.log(span / 400.0);
            double high = Math.log(span * 1.0E6);
            if (of(Math.exp(low), span, rise).sag() < sag) {
                throw new IllegalArgumentException("La comba es demasiado grande para esa distancia");
            }
            for (int iteration = 0; iteration < 200; iteration++) {
                double middle = (low + high) * 0.5;
                // Con a pequeña la cadena cuelga mucho; con a grande queda tensa.
                if (of(Math.exp(middle), span, rise).sag() > sag) {
                    low = middle;
                } else {
                    high = middle;
                }
            }
            return of(Math.exp((low + high) * 0.5), span, rise);
        }

        static Catenary of(double a, double span, double rise) {
            double x0 = span * 0.5 - a * asinh(rise / (2.0 * a * Math.sinh(span / (2.0 * a))));
            double c = -a * Math.cosh(x0 / a);
            return new Catenary(a, x0, c, span, rise);
        }

        double y(double x) {
            return a * Math.cosh((x - x0) / a) + c;
        }

        double sag() {
            double lowest = Math.clamp(x0 + a * asinh(rise / span), 0.0, span);
            return rise * lowest / span - y(lowest);
        }

        private static double asinh(double value) {
            return Math.log(value + Math.sqrt(value * value + 1.0));
        }
    }

    /** Base local: {@code along} sobre la cuerda y {@code offset} hacia el lado de la curva. */
    private record Frame(BlockVector3 start, double[] chord, double[] side, double length) {
        static Frame of(BlockVector3 start, BlockVector3 end, Bulge bulge) {
            double[] chord = {end.x() - start.x(), end.y() - start.y(), end.z() - start.z()};
            double length = norm(chord);
            if (length < 1.0) {
                throw new IllegalArgumentException("Los extremos coinciden: marca dos puntos distintos");
            }
            scale(chord, 1.0 / length);
            double[] up = {0.0, 1.0, 0.0};
            double[] side = switch (bulge) {
                case UP, DOWN -> {
                    double dot = chord[1];
                    double[] vertical = {-chord[0] * dot, 1.0 - chord[1] * dot, -chord[2] * dot};
                    if (norm(vertical) < 1.0E-6) {
                        vertical = new double[]{1.0, 0.0, 0.0};
                    }
                    yield vertical;
                }
                case LEFT, RIGHT -> {
                    // Mirando de inicio a fin, la izquierda es arriba × dirección (al este, la izquierda es el norte).
                    double[] left = cross(up, chord);
                    if (norm(left) < 1.0E-6) {
                        left = new double[]{0.0, 0.0, -1.0};
                    }
                    yield left;
                }
            };
            scale(side, 1.0 / norm(side));
            if (bulge == Bulge.DOWN || bulge == Bulge.RIGHT) {
                scale(side, -1.0);
            }
            return new Frame(start, chord, side, length);
        }

        BlockVector3 at(double along, double offset) {
            return BlockVector3.at(
                    Math.round(start.x() + chord[0] * along + side[0] * offset),
                    Math.round(start.y() + chord[1] * along + side[1] * offset),
                    Math.round(start.z() + chord[2] * along + side[2] * offset)
            );
        }

        private static double norm(double[] vector) {
            return Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        }

        private static void scale(double[] vector, double factor) {
            vector[0] *= factor;
            vector[1] *= factor;
            vector[2] *= factor;
        }

        private static double[] cross(double[] first, double[] second) {
            return new double[]{
                    first[1] * second[2] - first[2] * second[1],
                    first[2] * second[0] - first[0] * second[2],
                    first[0] * second[1] - first[1] * second[0]
            };
        }
    }
}
