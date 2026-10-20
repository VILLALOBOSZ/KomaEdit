package com.komainu.moxia.terrain.stamp;

/**
 * Piezas comunes de los generadores de sellos: curvas de transición, mezclas suaves, distancias a trazos y
 * deformación del dominio. Las coordenadas van de -1 a 1 con {@code y} hacia abajo en la imagen: la parte de arriba
 * ({@code y = -1}) es lo que queda delante del jugador cuando el sello se gira hacia donde mira.
 */
final class StampMath {
    private StampMath() {
    }

    /** Hermite entre los bordes; admite {@code edge0 > edge1} para la curva al revés. */
    static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    static double clamp01(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }

    /** Campana de Gauss alrededor de {@code (cx, cy)} con anchos {@code sx}, {@code sy}. */
    static double gauss(double x, double y, double cx, double cy, double sx, double sy) {
        double dx = (x - cx) / sx;
        double dy = (y - cy) / sy;
        return StrictMath.exp(-(dx * dx + dy * dy));
    }

    /** Máximo suave: sin arista donde se cruzan las dos formas ({@code k} es el ancho de la unión). */
    static double smoothMax(double a, double b, double k) {
        double h = clamp01(0.5 + 0.5 * (a - b) / k);
        return lerp(b, a, h) + k * h * (1.0 - h);
    }

    static double smoothMin(double a, double b, double k) {
        return -smoothMax(-a, -b, k);
    }

    /** Escalones: {@code steps} niveles con contrahuellas de anchura {@code riser} (0 a 1) suavizadas. */
    static double terrace(double value, double steps, double riser) {
        double scaled = value * steps;
        double level = Math.floor(scaled);
        double fraction = scaled - level;
        return (level + smoothstep(1.0 - riser, 1.0, fraction)) / steps;
    }

    /** Perfil de cono: 1 en el centro y 0 en {@code radius}, más afilado arriba con {@code power > 1}. */
    static double cone(double r, double radius, double power) {
        double t = 1.0 - r / radius;
        return t <= 0.0 ? 0.0 : StrictMath.pow(t, power);
    }

    static double pow(double value, double power) {
        return value <= 0.0 ? 0.0 : StrictMath.pow(value, power);
    }

    static double angle(double x, double y) {
        return StrictMath.atan2(y, x);
    }

    /** Diferencia de ángulos en (-π, π]. */
    static double angleDelta(double a, double b) {
        double delta = (a - b) % (2.0 * Math.PI);
        if (delta > Math.PI) {
            delta -= 2.0 * Math.PI;
        } else if (delta <= -Math.PI) {
            delta += 2.0 * Math.PI;
        }
        return delta;
    }

    /** Ruido periódico en el ángulo: el mismo valor a 0 y a 2π. */
    static double around(double angle, double frequency, long seed, int octaves) {
        return StampNoise.fbm(StrictMath.cos(angle) * frequency, StrictMath.sin(angle) * frequency, seed, octaves);
    }

    /** Distancia del punto al segmento. */
    static double segment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double length = dx * dx + dy * dy;
        double t = length == 0.0 ? 0.0 : clamp01(((px - ax) * dx + (py - ay) * dy) / length);
        double ex = px - (ax + dx * t);
        double ey = py - (ay + dy * t);
        return Math.sqrt(ex * ex + ey * ey);
    }

    /**
     * Un trazo como lista de puntos: distancia mínima y, en {@code along[0]}, por dónde va (de 0 al principio a 1 al
     * final), para que el cauce pueda ahondarse río abajo.
     */
    static final class Path {
        private final double[] xs;
        private final double[] ys;
        private final double[] lengths;
        private final double total;

        Path(double[] xs, double[] ys) {
            this.xs = xs;
            this.ys = ys;
            lengths = new double[xs.length];
            double sum = 0.0;
            for (int index = 1; index < xs.length; index++) {
                sum += Math.hypot(xs[index] - xs[index - 1], ys[index] - ys[index - 1]);
                lengths[index] = sum;
            }
            total = sum;
        }

        /** Trazo de una función {@code x(t), y(t)} con {@code t} de 0 a 1. */
        static Path of(int points, java.util.function.DoubleUnaryOperator x, java.util.function.DoubleUnaryOperator y) {
            double[] xs = new double[points];
            double[] ys = new double[points];
            for (int index = 0; index < points; index++) {
                double t = index / (points - 1.0);
                xs[index] = x.applyAsDouble(t);
                ys[index] = y.applyAsDouble(t);
            }
            return new Path(xs, ys);
        }

        double distance(double px, double py, double[] along) {
            double best = Double.POSITIVE_INFINITY;
            double where = 0.0;
            for (int index = 1; index < xs.length; index++) {
                double ax = xs[index - 1];
                double ay = ys[index - 1];
                double dx = xs[index] - ax;
                double dy = ys[index] - ay;
                double length = dx * dx + dy * dy;
                double t = length == 0.0 ? 0.0 : clamp01(((px - ax) * dx + (py - ay) * dy) / length);
                double ex = px - (ax + dx * t);
                double ey = py - (ay + dy * t);
                double distance = ex * ex + ey * ey;
                if (distance < best) {
                    best = distance;
                    where = lengths[index - 1] + (lengths[index] - lengths[index - 1]) * t;
                }
            }
            if (along != null) {
                along[0] = total == 0.0 ? 0.0 : where / total;
            }
            return Math.sqrt(best);
        }
    }

    /** Deforma el dominio con dos fBm: los bordes dejan de ser círculos y rectas. */
    static double[] warp(double x, double y, long seed, double frequency, double amount) {
        double wx = x + StampNoise.fbm(x * frequency, y * frequency, seed, 3) * amount;
        double wy = y + StampNoise.fbm(x * frequency + 5.2, y * frequency - 1.7, seed + 77L, 3) * amount;
        return new double[]{wx, wy};
    }

    /** Puntos repartidos al azar en un disco sin que se pisen demasiado (para cráteres, rocas, islotes…). */
    static double[][] scatter(long seed, int count, double radius, double spacing) {
        double[][] points = new double[count][];
        int placed = 0;
        for (int attempt = 0; placed < count && attempt < count * 60; attempt++) {
            double angle = StampNoise.unit(seed, attempt * 3) * 2.0 * Math.PI;
            double distance = Math.sqrt(StampNoise.unit(seed, attempt * 3 + 1)) * radius;
            double x = StrictMath.cos(angle) * distance;
            double y = StrictMath.sin(angle) * distance;
            boolean free = true;
            for (int other = 0; other < placed && free; other++) {
                free = Math.hypot(points[other][0] - x, points[other][1] - y) >= spacing;
            }
            if (free) {
                points[placed++] = new double[]{x, y, StampNoise.unit(seed, attempt * 3 + 2)};
            }
        }
        return java.util.Arrays.copyOf(points, placed);
    }
}
