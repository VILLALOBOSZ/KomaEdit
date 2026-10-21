package com.komainu.moxia.blueprint;

import com.komainu.moxia.brush.ez.BrushMask;
import com.komainu.moxia.brush.ez.BrushWorld;
import com.komainu.moxia.symmetry.SymmetryTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Dónde cae cada plano de una pasada del pincel «estampar», sin Minecraft: puntos al azar dentro del círculo con
 * una separación mínima entre ellos (y con los de pasadas anteriores), la probabilidad de cada hueco, el plano
 * elegido según su peso, su giro y volteo, y la altura a la que queda apoyado en el suelo. Con la misma semilla
 * sale siempre lo mismo.
 */
public final class StampPlanner {
    /** Intentos de colocar un punto por cada plano que se pide; luego se da por lleno el círculo. */
    private static final int ATTEMPTS_PER_STAMP = 30;
    /** Hasta dónde se busca el suelo por encima y por debajo del bloque que se mira. */
    public static final int SEARCH = 24;

    private StampPlanner() {
    }

    /** Un plano estampado: cuál, dónde cae su origen (centro de la base), y su giro y volteos. */
    public record Stamp(int blueprint, int x, int y, int z, int quarterTurns, boolean flipX, boolean flipZ) {
        /** Matriz entera 3×3 (por filas) que lleva las coordenadas locales del plano al mundo. */
        public int[] matrix() {
            return matrix(quarterTurns, flipX, flipZ);
        }

        /**
         * La misma transformación para girar los estados de los bloques ({@code StateTransform}): primero los
         * volteos y después los cuartos de vuelta de este a sur, como la matriz.
         */
        public SymmetryTransform symmetry() {
            return new SymmetryTransform(flipX, flipZ, false, Math.floorMod(quarterTurns, 4), 4);
        }

        /** Primero los volteos y después los cuartos de vuelta alrededor de +Y. */
        public static int[] matrix(int quarterTurns, boolean flipX, boolean flipZ) {
            int[] matrix = {flipX ? -1 : 1, 0, 0, 0, 1, 0, 0, 0, flipZ ? -1 : 1};
            for (int turn = 0; turn < Math.floorMod(quarterTurns, 4); turn++) {
                // Un cuarto de vuelta: x' = -z, z' = x.
                matrix = new int[]{-matrix[6], -matrix[7], -matrix[8], matrix[3], matrix[4], matrix[5],
                        matrix[0], matrix[1], matrix[2]};
            }
            return matrix;
        }
    }

    /** Resultado de una pasada: los planos y cuántos huecos se quedaron sin suelo donde apoyarse. */
    public record Result(List<Stamp> stamps, int withoutGround) {
    }

    /**
     * @param shapes    huella de cada plano candidato
     * @param weights   peso de cada plano (mismo orden)
     * @param centerX   bloque de aire delante de la cara mirada: centro del círculo y altura de partida
     * @param lookTurns cuartos de vuelta de la mirada del jugador, para {@code -r mirada}
     * @param mask      suelo permitido ({@code -m}), o {@code null}
     * @param occupied  puntos {@code {x, z}} de planos recientes que también hay que respetar
     */
    public static Result plan(StampSettings settings, List<StampShape> shapes, double[] weights, int centerX,
                              int centerY, int centerZ, int lookTurns, BrushWorld world, BrushMask mask,
                              List<int[]> occupied, long seed) {
        if (shapes.isEmpty() || shapes.size() != weights.length) {
            throw new IllegalArgumentException("No hay planos que estampar");
        }
        SplittableRandom random = new SplittableRandom(seed);
        double spacing = settings.autoSpacing() ? autoSpacing(shapes) : settings.spacing();
        List<int[]> points = points(settings, spacing, centerX, centerZ, occupied, random);
        double total = 0;
        for (double weight : weights) {
            total += weight;
        }
        List<Stamp> stamps = new ArrayList<>();
        int withoutGround = 0;
        for (int[] point : points) {
            if (stamps.size() >= settings.maxStamps()) {
                break;
            }
            // Se tiran todos los dados aunque el hueco no salga: así un hueco no cambia lo que sale en los demás.
            boolean kept = random.nextDouble() < settings.probability();
            int index = pick(weights, total, random.nextDouble());
            int turns = switch (settings.rotation()) {
                case RANDOM -> random.nextInt(4);
                case FIXED -> settings.fixedTurns();
                case LOOK -> lookTurns;
            };
            boolean flipX = settings.flipX() && random.nextBoolean();
            boolean flipZ = settings.flipZ() && random.nextBoolean();
            if (!kept) {
                continue;
            }
            int[] matrix = Stamp.matrix(turns, flipX, flipZ);
            StampShape shape = shapes.get(index);
            int x = point[0];
            int z = point[1];
            int y = height(settings.gravity(), shape, matrix, x, centerY, z, world, mask);
            if (y == Integer.MIN_VALUE) {
                withoutGround++;
                continue;
            }
            stamps.add(new Stamp(index, x, y + settings.offsetY(), z, turns, flipX, flipZ));
        }
        return new Result(List.copyOf(stamps), withoutGround);
    }

    /** Separación que evita que dos planos se toquen: el doble del radio de la huella más grande, más uno. */
    public static double autoSpacing(List<StampShape> shapes) {
        double radius = 0;
        for (StampShape shape : shapes) {
            radius = Math.max(radius, shape.radius());
        }
        return Math.max(2.0, 2 * radius + 1);
    }

    /**
     * Puntos de disco de Poisson: se prueba al azar dentro del círculo y se acepta el que queda a {@code spacing}
     * de todos los anteriores. Con radio 0, solo el centro.
     */
    static List<int[]> points(StampSettings settings, double spacing, int centerX, int centerZ,
                              List<int[]> occupied, SplittableRandom random) {
        List<int[]> accepted = new ArrayList<>();
        double minimum = spacing * spacing;
        if (settings.radius() == 0) {
            // Un solo plano donde se mira: el jugador eligió el sitio, no se le niega por lo de antes.
            accepted.add(new int[]{centerX, centerZ});
            return accepted;
        }
        // Se buscan más huecos de los que se van a llenar: la probabilidad quita algunos después.
        int wanted = settings.probability() >= 1.0 ? settings.maxStamps()
                : (int) Math.min(StampSettings.MAX_STAMPS * 4L,
                Math.ceil(settings.maxStamps() / Math.max(0.05, settings.probability())));
        int attempts = Math.max(ATTEMPTS_PER_STAMP, wanted * ATTEMPTS_PER_STAMP);
        double radius = settings.radius() + 0.5;
        for (int attempt = 0; attempt < attempts && accepted.size() < wanted; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = radius * Math.sqrt(random.nextDouble());
            int x = (int) Math.floor(centerX + 0.5 + Math.cos(angle) * distance);
            int z = (int) Math.floor(centerZ + 0.5 + Math.sin(angle) * distance);
            if (far(x, z, accepted, minimum) && far(x, z, occupied, minimum)) {
                accepted.add(new int[]{x, z});
            }
        }
        return accepted;
    }

    private static boolean far(int x, int z, List<int[]> others, double minimumSquared) {
        for (int[] other : others) {
            double dx = x - other[0];
            double dz = z - other[1];
            if (dx * dx + dz * dz < minimumSquared) {
                return false;
            }
        }
        return true;
    }

    private static int pick(double[] weights, double total, double roll) {
        double target = roll * total;
        double sum = 0;
        for (int index = 0; index < weights.length; index++) {
            sum += weights[index];
            if (target < sum) {
                return index;
            }
        }
        return weights.length - 1;
    }

    /**
     * Altura del origen del plano (el bloque de su base). {@link Integer#MIN_VALUE} si no hay suelo donde
     * apoyarlo o el suelo no cumple la máscara.
     */
    static int height(StampSettings.Gravity gravity, StampShape shape, int[] matrix, int x, int nearY, int z,
                      BrushWorld world, BrushMask mask) {
        if (gravity == StampSettings.Gravity.NONE) {
            return nearY;
        }
        int ground = surface(world, x, nearY, z);
        if (ground == Integer.MIN_VALUE || mask != null && !mask.matches(world, x, ground, z)) {
            return Integer.MIN_VALUE;
        }
        if (gravity == StampSettings.Gravity.CENTER) {
            return ground + 1 - shape.lowest();
        }
        // Cae hasta que alguna columna del plano toca el suelo de la suya.
        int best = Integer.MIN_VALUE;
        for (StampShape.Column column : shape.columns()) {
            int dx = matrix[0] * column.x() + matrix[2] * column.z();
            int dz = matrix[6] * column.x() + matrix[8] * column.z();
            int below = surface(world, x + dx, nearY, z + dz);
            if (below != Integer.MIN_VALUE) {
                best = Math.max(best, below + 1 - column.bottom());
            }
        }
        return best == Integer.MIN_VALUE ? ground + 1 - shape.lowest() : best;
    }

    /**
     * El bloque de suelo más cercano a {@code nearY} en la columna: si ahí hay aire, el primer suelo por debajo;
     * si ya es suelo (una ladera que sube), el último antes del aire por encima.
     */
    public static int surface(BrushWorld world, int x, int nearY, int z) {
        if (!world.contains(x, nearY, z)) {
            return Integer.MIN_VALUE;
        }
        if (!ground(world, x, nearY, z)) {
            for (int y = nearY - 1; y >= nearY - SEARCH && world.contains(x, y, z); y--) {
                if (ground(world, x, y, z)) {
                    return y;
                }
            }
            return Integer.MIN_VALUE;
        }
        for (int y = nearY + 1; y <= nearY + SEARCH && world.contains(x, y, z); y++) {
            if (!ground(world, x, y, z)) {
                return y - 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Suelo: cubos enteros, losas y escaleras. Hierba alta, flores, nieve en capa y líquidos no sostienen. */
    static boolean ground(BrushWorld world, int x, int y, int z) {
        BrushWorld.Cell cell = world.cell(x, y, z);
        return cell == BrushWorld.Cell.FULL || cell == BrushWorld.Cell.SHAPED;
    }
}
