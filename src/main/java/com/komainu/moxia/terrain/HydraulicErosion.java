package com.komainu.moxia.terrain;

import java.util.SplittableRandom;

/**
 * Erosión hidráulica por gotas sobre el mapa de alturas. Cada gota cae en un punto al azar, baja siguiendo la
 * pendiente con algo de inercia, arranca suelo mientras va deprisa y cargada de menos sedimento del que puede llevar
 * y lo suelta donde frena o sube: así salen cauces que se juntan, crestas afiladas y abanicos al pie de las
 * laderas. Al morir, la gota deja lo que llevaba donde está; la masa solo se pierde si no hay ninguna columna
 * editable cerca.
 *
 * <p>Las columnas fijas guían a las gotas pero ni se erosionan ni reciben sedimento. Es determinista: la misma
 * semilla da siempre el mismo resultado.</p>
 */
public final class HydraulicErosion {
    private HydraulicErosion() {
    }

    /**
     * @param droplets   número de gotas
     * @param lifetime   pasos de cada gota como mucho
     * @param inertia    cuánto conserva la dirección (0 sigue la pendiente, 1 no gira)
     * @param capacity   sedimento que lleva una gota por unidad de desnivel, velocidad y agua
     * @param minCapacity capacidad mínima, para que siga erosionando en lo casi llano
     * @param erode      fracción de lo que le falta que arranca en cada paso
     * @param deposit    fracción del exceso que suelta en cada paso
     * @param evaporate  agua que pierde por paso
     * @param gravity    aceleración por bloque de desnivel
     * @param radius     radio del pincel de erosión
     */
    public record Settings(int droplets, int lifetime, double inertia, double capacity, double minCapacity,
                           double erode, double deposit, double evaporate, double gravity, int radius, long seed) {
        public Settings {
            if (droplets < 0 || droplets > 20_000_000) {
                throw new IllegalArgumentException("Demasiadas gotas");
            }
            if (lifetime < 1 || lifetime > 512 || radius < 1 || radius > 8) {
                throw new IllegalArgumentException("Parámetros de gota fuera de rango");
            }
        }

        /**
         * Ajustes de la orden: {@code passes} gotas por columna editable y {@code strength} (1 normal) que escala lo
         * que arranca y lo que puede llevar cada gota.
         */
        public static Settings of(int editableColumns, double passes, double strength, long seed) {
            if (passes <= 0.0 || passes > 64.0) {
                throw new IllegalArgumentException("Las iteraciones deben estar entre 0.1 y 64 gotas por columna");
            }
            if (strength <= 0.0 || strength > 8.0) {
                throw new IllegalArgumentException("La fuerza debe estar entre 0.1 y 8");
            }
            int droplets = (int) Math.min(20_000_000L, Math.round(editableColumns * passes));
            // Ajustados con renders sobre una montaña de 160×160: cauces en toda la ladera sin arrasar la cima.
            return new Settings(droplets, 80, 0.2, 0.6 * Math.sqrt(strength), 0.02,
                    Math.min(0.9, 0.08 * strength), Math.min(0.9, 0.3 * Math.sqrt(strength)), 0.015, 0.15, 3, seed);
        }
    }

    /** Masa movida: lo arrancado, lo depositado y lo que no encontró sitio. */
    public record Report(double eroded, double deposited, double lost, int droplets) {
    }

    public static Report run(HeightField field, Settings settings, Runnable checkCancelled) {
        int sizeX = field.sizeX();
        int sizeZ = field.sizeZ();
        int columns = field.columns();
        double[] map = new double[columns];
        boolean[] editable = new boolean[columns];
        double[] low = new double[columns];
        double[] high = new double[columns];
        int[] starts = new int[columns];
        int startCount = 0;
        for (int index = 0; index < columns; index++) {
            map[index] = field.at(index);
            editable[index] = field.editableAt(index) && !Double.isNaN(map[index]);
            low[index] = field.lowestAt(index);
            high[index] = field.highestAt(index);
            if (editable[index]) {
                starts[startCount++] = index;
            }
        }
        if (startCount == 0 || sizeX < 2 || sizeZ < 2) {
            return new Report(0.0, 0.0, 0.0, 0);
        }
        Brush brush = new Brush(settings.radius);
        Totals totals = new Totals();
        SplittableRandom random = new SplittableRandom(settings.seed);
        for (int drop = 0; drop < settings.droplets; drop++) {
            if ((drop & 255) == 0) {
                checkCancelled.run();
            }
            int start = starts[random.nextInt(startCount)];
            double x = Math.min(sizeX - 1.001, start % sizeX + random.nextDouble());
            double z = Math.min(sizeZ - 1.001, start / sizeX + random.nextDouble());
            simulate(map, editable, low, high, sizeX, sizeZ, x, z, settings, brush, totals, random);
        }
        for (int index = 0; index < columns; index++) {
            if (editable[index]) {
                field.setAt(index, map[index]);
            }
        }
        return new Report(totals.eroded, totals.deposited, totals.lost, settings.droplets);
    }

    private static void simulate(double[] map, boolean[] editable, double[] low, double[] high, int sizeX, int sizeZ,
                                 double x, double z, Settings settings, Brush brush, Totals totals,
                                 SplittableRandom random) {
        double directionX = 0.0;
        double directionZ = 0.0;
        double speed = 1.0;
        double water = 1.0;
        double sediment = 0.0;
        double[] sample = new double[3];
        for (int step = 0; step < settings.lifetime; step++) {
            if (!heightAndGradient(map, sizeX, x, z, sample)) {
                break;
            }
            double height = sample[0];
            directionX = directionX * settings.inertia - sample[1] * (1.0 - settings.inertia);
            directionZ = directionZ * settings.inertia - sample[2] * (1.0 - settings.inertia);
            double length = Math.hypot(directionX, directionZ);
            if (length < 1e-9) {
                // En lo llano del todo la gota se para y suelta lo que lleve.
                break;
            }
            directionX /= length;
            directionZ /= length;
            double nextX = x + directionX;
            double nextZ = z + directionZ;
            if (nextX < 0.0 || nextX >= sizeX - 1 || nextZ < 0.0 || nextZ >= sizeZ - 1
                    || !heightAndGradient(map, sizeX, nextX, nextZ, sample)) {
                break;
            }
            double delta = sample[0] - height;
            double capacity = Math.max(-delta * speed * water * settings.capacity, settings.minCapacity);
            if (sediment > capacity || delta > 0.0) {
                double amount = delta > 0.0 ? Math.min(delta, sediment) : (sediment - capacity) * settings.deposit;
                double placed = depositAt(map, editable, high, sizeX, x, z, amount);
                sediment -= placed;
                totals.deposited += placed;
            } else {
                double amount = Math.min((capacity - sediment) * settings.erode, -delta);
                double taken = brush.erode(map, editable, low, sizeX, sizeZ, (int) x, (int) z, amount);
                sediment += taken;
                totals.eroded += taken;
            }
            speed = Math.sqrt(Math.max(0.0, speed * speed - delta * settings.gravity));
            water *= 1.0 - settings.evaporate;
            x = nextX;
            z = nextZ;
        }
        if (sediment > 0.0) {
            double placed = depositAt(map, editable, high, sizeX, x, z, sediment);
            sediment -= placed;
            totals.deposited += placed;
            if (sediment > 1e-12) {
                placed = depositNearest(map, editable, high, sizeX, sizeZ, (int) x, (int) z, sediment);
                sediment -= placed;
                totals.deposited += placed;
            }
            totals.lost += Math.max(0.0, sediment);
        }
    }

    /** Altura y pendiente interpoladas; {@code false} si falta alguna esquina. */
    private static boolean heightAndGradient(double[] map, int sizeX, double x, double z, double[] out) {
        int nodeX = (int) x;
        int nodeZ = (int) z;
        double fractionX = x - nodeX;
        double fractionZ = z - nodeZ;
        int index = nodeZ * sizeX + nodeX;
        double northWest = map[index];
        double northEast = map[index + 1];
        double southWest = map[index + sizeX];
        double southEast = map[index + sizeX + 1];
        if (Double.isNaN(northWest) || Double.isNaN(northEast) || Double.isNaN(southWest) || Double.isNaN(southEast)) {
            return false;
        }
        out[1] = (northEast - northWest) * (1.0 - fractionZ) + (southEast - southWest) * fractionZ;
        out[2] = (southWest - northWest) * (1.0 - fractionX) + (southEast - northEast) * fractionX;
        out[0] = northWest * (1.0 - fractionX) * (1.0 - fractionZ) + northEast * fractionX * (1.0 - fractionZ)
                + southWest * (1.0 - fractionX) * fractionZ + southEast * fractionX * fractionZ;
        return true;
    }

    /** Reparte entre las cuatro esquinas editables según la posición; devuelve lo que cabe. */
    private static double depositAt(double[] map, boolean[] editable, double[] high, int sizeX, double x, double z,
                                    double amount) {
        if (amount <= 0.0) {
            return 0.0;
        }
        int nodeX = (int) x;
        int nodeZ = (int) z;
        double fractionX = x - nodeX;
        double fractionZ = z - nodeZ;
        int index = nodeZ * sizeX + nodeX;
        int[] cells = {index, index + 1, index + sizeX, index + sizeX + 1};
        double[] weights = {(1.0 - fractionX) * (1.0 - fractionZ), fractionX * (1.0 - fractionZ),
                (1.0 - fractionX) * fractionZ, fractionX * fractionZ};
        double total = 0.0;
        for (int corner = 0; corner < 4; corner++) {
            if (cells[corner] >= map.length || !editable[cells[corner]]) {
                weights[corner] = 0.0;
            }
            total += weights[corner];
        }
        if (total <= 0.0) {
            // Encima justo de una esquina: se reparte a partes iguales entre las editables.
            for (int corner = 0; corner < 4; corner++) {
                if (cells[corner] < map.length && editable[cells[corner]]) {
                    weights[corner] = 1.0;
                    total += 1.0;
                }
            }
            if (total <= 0.0) {
                return 0.0;
            }
        }
        double placed = 0.0;
        for (int corner = 0; corner < 4; corner++) {
            if (weights[corner] <= 0.0) {
                continue;
            }
            int cell = cells[corner];
            double add = Math.min(amount * weights[corner] / total, Math.max(0.0, high[cell] - map[cell]));
            map[cell] += add;
            placed += add;
        }
        return placed;
    }

    /** Último recurso: la columna editable más cercana con sitio, buscando en anillos. */
    private static double depositNearest(double[] map, boolean[] editable, double[] high, int sizeX, int sizeZ,
                                         int x, int z, double amount) {
        for (int radius = 1; radius <= 8; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int cx = x + dx;
                    int cz = z + dz;
                    if (cx < 0 || cz < 0 || cx >= sizeX || cz >= sizeZ) {
                        continue;
                    }
                    int cell = cz * sizeX + cx;
                    if (!editable[cell]) {
                        continue;
                    }
                    double add = Math.min(amount, Math.max(0.0, high[cell] - map[cell]));
                    if (add > 0.0) {
                        map[cell] += add;
                        return add;
                    }
                }
            }
        }
        return 0.0;
    }

    /** Pincel de erosión: pesos que bajan con la distancia, normalizados entre las columnas editables. */
    private static final class Brush {
        private final int[] offsetX;
        private final int[] offsetZ;
        private final double[] weights;

        Brush(int radius) {
            int capacity = (radius * 2 + 1) * (radius * 2 + 1);
            int[] xs = new int[capacity];
            int[] zs = new int[capacity];
            double[] ws = new double[capacity];
            int count = 0;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance < radius) {
                        xs[count] = dx;
                        zs[count] = dz;
                        ws[count] = 1.0 - distance / radius;
                        count++;
                    }
                }
            }
            offsetX = java.util.Arrays.copyOf(xs, count);
            offsetZ = java.util.Arrays.copyOf(zs, count);
            weights = java.util.Arrays.copyOf(ws, count);
        }

        double erode(double[] map, boolean[] editable, double[] low, int sizeX, int sizeZ, int nodeX, int nodeZ,
                     double amount) {
            if (amount <= 0.0) {
                return 0.0;
            }
            double total = 0.0;
            for (int cell = 0; cell < weights.length; cell++) {
                int x = nodeX + offsetX[cell];
                int z = nodeZ + offsetZ[cell];
                if (x >= 0 && z >= 0 && x < sizeX && z < sizeZ && editable[z * sizeX + x]) {
                    total += weights[cell];
                }
            }
            if (total <= 0.0) {
                return 0.0;
            }
            double taken = 0.0;
            for (int cell = 0; cell < weights.length; cell++) {
                int x = nodeX + offsetX[cell];
                int z = nodeZ + offsetZ[cell];
                if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ) {
                    continue;
                }
                int index = z * sizeX + x;
                if (!editable[index]) {
                    continue;
                }
                double take = Math.min(amount * weights[cell] / total, Math.max(0.0, map[index] - low[index]));
                map[index] -= take;
                taken += take;
            }
            return taken;
        }
    }

    private static final class Totals {
        private double eroded;
        private double deposited;
        private double lost;
    }
}
