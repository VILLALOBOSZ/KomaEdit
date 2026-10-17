package com.komainu.moxia.generator;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.noise.NoiseSpec;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * {@code /komaedit flowline}: una o varias líneas de flujo que salen de los ojos del jugador hacia donde mira. En
 * cada paso la dirección es la de la mirada torcida por un ruido 3D, mezclada con la anterior según la inercia y
 * con la gravedad sumada en vertical. Son como mucho unos miles de bloques, así que se calcula en el hilo del
 * servidor y puede leer el mundo directamente para pegarse a las superficies ({@code -s}).
 */
public final class FlowLine {
    public static final int MAX_LENGTH = 2_048;
    public static final int MAX_LINES = 32;
    /** Lo que tuerce el ruido la mirada: con 1,5 las líneas serpentean sin dar media vuelta. */
    private static final double NOISE_WEIGHT = 1.5;
    /** Cuánto empuja cada unidad de gravedad por paso. */
    private static final double GRAVITY_WEIGHT = 0.15;
    private static final int SNAP_REACH = 8;
    private static final double CHANNEL_OFFSET = 7_919.31;

    private FlowLine() {
    }

    /**
     * @param gravity          empuje vertical por paso (negativo hacia abajo)
     * @param inertia          0–1: cuánto pesa la dirección anterior
     * @param convexPoints     puntos de la selección convexa ({@code -c}); 0 no la crea
     * @param randomness       {@code -r}: cuánto difieren las líneas entre sí
     * @param snap             {@code -s}: las líneas se pegan a las superficies
     */
    public record Settings(
            int length,
            double gravity,
            NoiseSpec noise,
            double inertia,
            int convexPoints,
            int lines,
            double randomness,
            boolean snap,
            long seed
    ) {
    }

    @FunctionalInterface
    public interface Terrain {
        boolean solid(int x, int y, int z);
    }

    /** Bloques de las líneas en orden y los puntos de la selección convexa, si se pidió. */
    public record Result(LongLinkedOpenHashSet blocks, List<double[]> convex) {
    }

    /**
     * @param eye  posición de los ojos
     * @param look dirección de la mirada
     */
    public static Result trace(Settings settings, double[] eye, double[] look, Terrain terrain) {
        NoiseSpec.NoiseField field = settings.noise().build(settings.seed());
        SplittableRandom random = new SplittableRandom(settings.seed());
        LongLinkedOpenHashSet blocks = new LongLinkedOpenHashSet();
        List<double[]> path = new ArrayList<>();
        double[] baseLook = normalize(look.clone());
        for (int line = 0; line < settings.lines(); line++) {
            double[] shift = {0, 0, 0};
            double[] heading = baseLook.clone();
            if (line > 0) {
                double reach = settings.randomness();
                shift = new double[]{
                        (random.nextDouble() * 2 - 1) * reach,
                        (random.nextDouble() * 2 - 1) * reach,
                        (random.nextDouble() * 2 - 1) * reach
                };
                heading = tilt(baseLook, Math.toRadians(random.nextDouble() * settings.randomness() * 0.5),
                        random.nextDouble() * Math.PI * 2);
            }
            double[] position = settings.snap() && terrain != null ? snap(eye.clone(), terrain) : eye.clone();
            double[] direction = heading.clone();
            for (int step = 0; step < settings.length(); step++) {
                double x = position[0] + shift[0];
                double y = position[1] + shift[1];
                double z = position[2] + shift[2];
                double[] target = {
                        heading[0] + NOISE_WEIGHT * (field.sample(x, y, z) * 2 - 1),
                        heading[1] + NOISE_WEIGHT * (field.sample(x + CHANNEL_OFFSET, y, z) * 2 - 1),
                        heading[2] + NOISE_WEIGHT * (field.sample(x, y, z + CHANNEL_OFFSET) * 2 - 1)
                };
                normalize(target);
                for (int axis = 0; axis < 3; axis++) {
                    direction[axis] = settings.inertia() * direction[axis] + (1 - settings.inertia()) * target[axis];
                }
                direction[1] += settings.gravity() * GRAVITY_WEIGHT;
                normalize(direction);
                double[] next = {position[0] + direction[0], position[1] + direction[1], position[2] + direction[2]};
                if (settings.snap() && terrain != null) {
                    next = snap(next, terrain);
                }
                draw(position, next, blocks);
                path.add(next.clone());
                position = next;
            }
        }
        return new Result(blocks, convex(path, settings.convexPoints()));
    }

    /** Lleva el punto a la superficie más cercana en vertical: sube si está enterrado, baja si está en el aire. */
    static double[] snap(double[] point, Terrain terrain) {
        int x = (int) Math.floor(point[0]);
        int y = (int) Math.floor(point[1]);
        int z = (int) Math.floor(point[2]);
        if (terrain.solid(x, y, z)) {
            for (int up = 1; up <= SNAP_REACH; up++) {
                if (!terrain.solid(x, y + up, z)) {
                    return new double[]{point[0], y + up - 1 + 0.5, point[2]};
                }
            }
            return point;
        }
        for (int down = 1; down <= SNAP_REACH; down++) {
            if (terrain.solid(x, y - down, z)) {
                return new double[]{point[0], y - down + 0.5, point[2]};
            }
        }
        return point;
    }

    private static void draw(double[] from, double[] to, LongLinkedOpenHashSet blocks) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double dz = to[2] - from[2];
        int samples = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 3.0));
        for (int sample = 1; sample <= samples; sample++) {
            double t = sample / (double) samples;
            blocks.add(PackedBlockPlan.pack(
                    (int) Math.floor(from[0] + dx * t),
                    (int) Math.floor(from[1] + dy * t),
                    (int) Math.floor(from[2] + dz * t)
            ));
        }
    }

    /** Puntos repartidos por igual a lo largo de todas las líneas. */
    static List<double[]> convex(List<double[]> path, int count) {
        List<double[]> points = new ArrayList<>();
        if (count <= 0 || path.isEmpty()) {
            return points;
        }
        int total = Math.min(count, path.size());
        for (int index = 0; index < total; index++) {
            int position = total == 1 ? 0 : (int) Math.round(index * (path.size() - 1) / (double) (total - 1));
            points.add(path.get(position));
        }
        return points;
    }

    /** Tuerce un vector unitario {@code angle} radianes hacia un lado elegido por {@code roll}. */
    static double[] tilt(double[] vector, double angle, double roll) {
        double[] helper = Math.abs(vector[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] side = normalize(cross(vector, helper));
        double[] up = cross(side, vector);
        double cos = Math.cos(roll);
        double sin = Math.sin(roll);
        double[] axis = {side[0] * cos + up[0] * sin, side[1] * cos + up[1] * sin, side[2] * cos + up[2] * sin};
        return normalize(new double[]{
                vector[0] * Math.cos(angle) + axis[0] * Math.sin(angle),
                vector[1] * Math.cos(angle) + axis[1] * Math.sin(angle),
                vector[2] * Math.cos(angle) + axis[2] * Math.sin(angle)
        });
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] normalize(double[] vector) {
        double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        if (length > 1.0E-9) {
            vector[0] /= length;
            vector[1] /= length;
            vector[2] /= length;
        } else {
            vector[0] = 0;
            vector[1] = 1;
            vector[2] = 0;
        }
        return vector;
    }
}
