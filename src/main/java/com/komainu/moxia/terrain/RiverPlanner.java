package com.komainu.moxia.terrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Ríos y cañones a lo largo de una trayectoria. La sección tiene un fondo plano, orillas en curva suave y, por
 * fuera, una ladera con la pendiente que se pida hasta encontrar el terreno; en lo alto se funde con él. El fondo
 * baja siempre en el sentido del agua: se toma el mínimo acumulado del terreno menos la profundidad, se suaviza con
 * una media móvil (que conserva el orden) y nunca queda por encima del suelo del eje. El agua o la lava llenan hasta
 * su nivel sobre el fondo y, si a los lados el terreno queda más bajo, se levanta un ribazo para que no se escape.
 */
public final class RiverPlanner {
    private static final double SOFTNESS = 1.5;

    private RiverPlanner() {
    }

    public enum Fluid {
        WATER("water", "agua", "minecraft:water"),
        LAVA("lava", "lava", "minecraft:lava"),
        NONE("none", "seco", null);

        private final String id;
        private final String displayName;
        private final String blockId;

        Fluid(String id, String displayName, String blockId) {
            this.id = id;
            this.displayName = displayName;
            this.blockId = blockId;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public String blockId() {
            return blockId;
        }

        public static Fluid byId(String input) {
            return switch (input.toLowerCase(Locale.ROOT)) {
                case "water", "agua" -> WATER;
                case "lava" -> LAVA;
                case "none", "seco", "ninguno", "dry", "aire" -> NONE;
                default -> throw new IllegalArgumentException("Líquido desconocido: " + input + " (agua, lava o seco)");
            };
        }
    }

    /**
     * @param halfWidth  medio ancho del fondo
     * @param depth      profundidad del fondo bajo el terreno del eje
     * @param bank       ancho de cada orilla (de fondo a lo alto)
     * @param wallSlope  bloques que sube la ladera por cada bloque hacia fuera, pasada la orilla
     * @param transition ancho de la ladera exterior antes de fundirse con el terreno
     * @param level      altura del líquido sobre el fondo
     * @param followPath el fondo sigue las alturas de los puntos (menos la profundidad) en lugar del terreno
     * @param keepOrder  respeta el orden de los puntos; si no, el agua corre hacia el extremo más bajo
     * @param smoothing  largo en bloques de la media móvil del fondo
     */
    public record Settings(double halfWidth, double depth, double bank, double wallSlope, double transition,
                           Fluid fluid, double level, boolean followPath, boolean keepOrder, double smoothing) {
        public Settings {
            check(halfWidth, 0.5, 32.0, "El medio ancho");
            check(depth, 0.0, 96.0, "La profundidad");
            check(bank, 0.5, 32.0, "La orilla");
            check(wallSlope, 0.1, 8.0, "La pendiente de las laderas");
            check(transition, 0.0, 64.0, "La transición");
            check(level, 0.0, 96.0, "El nivel del líquido");
            check(smoothing, 0.0, 256.0, "El suavizado");
        }

        private static void check(double value, double minimum, double maximum, String name) {
            if (Double.isNaN(value) || value < minimum || value > maximum) {
                throw new IllegalArgumentException(name + " debe estar entre " + format(minimum) + " y "
                        + format(maximum));
            }
        }

        /** Hasta dónde llega el corte desde el eje. */
        public double reach() {
            return halfWidth + bank + transition + 1.0;
        }

        /** Margen extra para los ribazos. */
        public int leveeMargin() {
            return fluid == Fluid.NONE ? 0 : 6;
        }
    }

    /** Preajustes de sección: río, arroyo, cañón y barranco seco. */
    public static Settings preset(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "river", "rio", "río" -> new Settings(2.5, 3.0, 4.0, 1.0, 6.0, Fluid.WATER, 2.0, false, false, 6.0);
            case "stream", "arroyo" -> new Settings(1.0, 2.0, 1.5, 1.0, 4.0, Fluid.WATER, 1.0, false, false, 4.0);
            case "canyon", "canon", "cañon", "cañón" -> new Settings(2.5, 12.0, 3.0, 3.0, 12.0, Fluid.WATER, 2.0,
                    false, false, 10.0);
            case "gully", "barranco" -> new Settings(0.5, 5.0, 2.0, 1.5, 8.0, Fluid.NONE, 0.0, false, false, 6.0);
            default -> throw new IllegalArgumentException("Sección desconocida: " + name
                    + " (rio, arroyo, canon o barranco)");
        };
    }

    /**
     * Resultado: el mapa ya tallado, el nivel del líquido de cada columna (o {@link TerrainColumns#NONE}) y el
     * perfil del fondo a lo largo, en el sentido del agua.
     */
    public record Result(int[] fluidLevels, Fluid fluid, double[] bed, boolean reversed, List<double[]> samples,
                         HeightField field) implements TerrainWriter.Fluids {
        @Override
        public int level(int x, int z) {
            int index = field.index(x, z);
            return index < 0 ? TerrainColumns.NONE : fluidLevels[index];
        }

        @Override
        public String blockId() {
            return fluid.blockId();
        }

        public double startBed() {
            return bed[0];
        }

        public double endBed() {
            return bed[bed.length - 1];
        }
    }

    /**
     * Talla el río en {@code field}.
     *
     * @param path puntos de la trayectoria muestreada {x, y, z} (centros de bloque), separados como mucho un bloque
     */
    public static Result plan(HeightField field, List<double[]> path, Settings settings, Runnable checkCancelled) {
        if (path.size() < 2) {
            throw new IllegalArgumentException("El río necesita una trayectoria de al menos dos puntos");
        }
        List<double[]> samples = new ArrayList<>(path);
        int count = samples.size();
        double[] ground = groundAlong(field, samples);
        double[] raw = new double[count];
        for (int index = 0; index < count; index++) {
            double wanted = settings.followPath ? Math.floor(samples.get(index)[1]) - settings.depth
                    : ground[index] - settings.depth;
            raw[index] = Math.min(wanted, ground[index]);
        }
        boolean reversed = false;
        if (!settings.keepOrder && endAverage(raw, false) > endAverage(raw, true)) {
            java.util.Collections.reverse(samples);
            reverse(ground);
            reverse(raw);
            reversed = true;
        }
        double[] bed = monotoneBed(samples, raw, ground, settings.smoothing);

        HeightField original = field.copy();
        int columns = field.columns();
        double[] lowestProfile = new double[columns];
        double[] nearest = new double[columns];
        int[] nearestSample = new int[columns];
        double[] waterLevel = new double[columns];
        Arrays.fill(lowestProfile, Double.POSITIVE_INFINITY);
        Arrays.fill(nearest, Double.POSITIVE_INFINITY);
        Arrays.fill(nearestSample, -1);
        Arrays.fill(waterLevel, Double.POSITIVE_INFINITY);
        boolean wet = settings.fluid != Fluid.NONE && settings.level > 0.0;
        double waterReach = waterReach(settings);
        double reach = settings.reach();
        for (int sample = 0; sample < count; sample++) {
            checkCancelled.run();
            double[] point = samples.get(sample);
            int lowX = (int) Math.floor(point[0] - reach);
            int highX = (int) Math.floor(point[0] + reach);
            int lowZ = (int) Math.floor(point[2] - reach);
            int highZ = (int) Math.floor(point[2] + reach);
            for (int z = lowZ; z <= highZ; z++) {
                for (int x = lowX; x <= highX; x++) {
                    int index = field.index(x, z);
                    if (index < 0) {
                        continue;
                    }
                    double distance = Math.hypot(x + 0.5 - point[0], z + 0.5 - point[2]);
                    if (distance > reach) {
                        continue;
                    }
                    lowestProfile[index] = Math.min(lowestProfile[index], profile(distance, bed[sample], settings));
                    if (distance < nearest[index]) {
                        nearest[index] = distance;
                        nearestSample[index] = sample;
                    }
                }
            }
        }

        // Cada columna del cauce lleva el nivel de su muestra más cercana: igual a lo ancho de cada sección.
        if (wet) {
            for (int index = 0; index < columns; index++) {
                if (nearestSample[index] >= 0 && nearest[index] <= waterReach + 0.5) {
                    waterLevel[index] = bed[nearestSample[index]] + settings.level;
                }
            }
        }
        double fade = Math.min(4.0, Math.max(1.0, settings.transition));
        for (int index = 0; index < columns; index++) {
            if (nearestSample[index] < 0 || !field.editableAt(index)) {
                continue;
            }
            double terrain = original.at(index);
            double carve = lowestProfile[index];
            double height;
            if (nearest[index] <= settings.halfWidth) {
                height = Math.min(terrain, carve);
            } else {
                height = smoothMin(terrain, carve, SOFTNESS);
                double edge = (nearest[index] - (reach - fade)) / fade;
                if (edge > 0.0) {
                    double amount = Curves.smooth(Math.clamp(edge, 0.0, 1.0));
                    height += (terrain - height) * amount;
                }
            }
            field.setAt(index, Math.min(terrain, height));
        }

        enforceDownhillAxis(field, samples, waterLevel);
        int[] levels = new int[columns];
        Arrays.fill(levels, TerrainColumns.NONE);
        if (wet) {
            fillAndContain(field, samples, waterLevel, levels, checkCancelled);
        }
        return new Result(levels, settings.fluid, bed, reversed, List.copyOf(samples), field);
    }

    /**
     * Recorre el eje en el sentido del agua y no deja que ninguna columna quede más alta (ya redondeada) que las
     * anteriores, ni el nivel del líquido. El tallado ya baja casi siempre; esto cubre los decimales de las curvas.
     */
    private static void enforceDownhillAxis(HeightField field, List<double[]> samples, double[] waterLevel) {
        double bed = Double.POSITIVE_INFINITY;
        double water = Double.POSITIVE_INFINITY;
        for (double[] point : samples) {
            int index = field.index((int) Math.floor(point[0]), (int) Math.floor(point[2]));
            if (index < 0 || Double.isNaN(field.at(index))) {
                continue;
            }
            if (field.editableAt(index)) {
                double rounded = Math.round(field.at(index));
                if (rounded > bed) {
                    field.setAt(index, bed);
                }
            }
            bed = Math.min(bed, Math.round(field.at(index)));
            if (!Double.isInfinite(waterLevel[index])) {
                waterLevel[index] = Math.min(waterLevel[index], water);
                water = Math.min(water, waterLevel[index]);
            }
        }
    }

    /** Altura del terreno bajo cada muestra (la columna de su bloque); los huecos toman la anterior. */
    private static double[] groundAlong(HeightField field, List<double[]> samples) {
        double[] ground = new double[samples.size()];
        double last = Double.NaN;
        for (int index = 0; index < samples.size(); index++) {
            double[] point = samples.get(index);
            double value = field.get((int) Math.floor(point[0]), (int) Math.floor(point[2]));
            if (Double.isNaN(value)) {
                value = last;
            }
            ground[index] = value;
            if (!Double.isNaN(value)) {
                last = value;
            }
        }
        double first = Double.NaN;
        for (double value : ground) {
            if (!Double.isNaN(value)) {
                first = value;
                break;
            }
        }
        if (Double.isNaN(first)) {
            throw new IllegalArgumentException("La trayectoria del río no pasa por ningún terreno capturado");
        }
        for (int index = 0; index < ground.length && Double.isNaN(ground[index]); index++) {
            ground[index] = first;
        }
        return ground;
    }

    /** Media del primer o del último 5 % de los valores. */
    private static double endAverage(double[] values, boolean start) {
        int span = Math.max(1, values.length / 20);
        double sum = 0.0;
        for (int index = 0; index < span; index++) {
            sum += values[start ? index : values.length - 1 - index];
        }
        return sum / span;
    }

    private static void reverse(double[] values) {
        for (int left = 0, right = values.length - 1; left < right; left++, right--) {
            double swap = values[left];
            values[left] = values[right];
            values[right] = swap;
        }
    }

    /**
     * Fondo que nunca sube: mínimo acumulado, media móvil hacia delante (una media de valores que no crecen tampoco
     * crece) y, otra vez, nunca por encima del suelo del eje.
     */
    static double[] monotoneBed(List<double[]> samples, double[] raw, double[] ground, double smoothingBlocks) {
        int count = raw.length;
        double[] bed = new double[count];
        double running = Double.POSITIVE_INFINITY;
        for (int index = 0; index < count; index++) {
            running = Math.min(running, raw[index]);
            bed[index] = running;
        }
        double spacing = averageSpacing(samples);
        int window = (int) Math.round(smoothingBlocks / Math.max(0.05, spacing));
        if (window > 1) {
            double[] smoothed = new double[count];
            double sum = 0.0;
            for (int index = 0; index < count; index++) {
                sum += bed[index];
                if (index >= window) {
                    sum -= bed[index - window];
                }
                int span = Math.min(index + 1, window);
                smoothed[index] = sum / span;
            }
            // La media va con retraso: se desplaza media ventana hacia atrás para que el fondo no llegue tarde.
            int shift = window / 2;
            for (int index = 0; index < count; index++) {
                bed[index] = smoothed[Math.min(count - 1, index + shift)];
            }
        }
        running = Double.POSITIVE_INFINITY;
        for (int index = 0; index < count; index++) {
            running = Math.min(running, Math.min(bed[index], ground[index]));
            bed[index] = running;
        }
        return bed;
    }

    private static double averageSpacing(List<double[]> samples) {
        double length = 0.0;
        for (int index = 1; index < samples.size(); index++) {
            double[] a = samples.get(index - 1);
            double[] b = samples.get(index);
            length += Math.hypot(b[0] - a[0], b[2] - a[2]);
        }
        return length / Math.max(1, samples.size() - 1);
    }

    /** Altura de la sección a una distancia del eje. */
    static double profile(double distance, double bed, Settings settings) {
        double outside = distance - settings.halfWidth;
        if (outside <= 0.0) {
            return bed;
        }
        if (outside <= settings.bank) {
            return bed + settings.depth * Curves.smooth(outside / settings.bank);
        }
        return bed + settings.depth + (outside - settings.bank) * settings.wallSlope;
    }

    /** Distancia al eje a la que la sección alcanza el nivel del líquido. */
    static double waterReach(Settings settings) {
        if (settings.level <= 0.0) {
            return 0.0;
        }
        if (settings.depth <= 0.0 || settings.level > settings.depth) {
            double above = settings.depth <= 0.0 ? settings.level : settings.level - settings.depth;
            return settings.halfWidth + settings.bank + above / settings.wallSlope;
        }
        double fraction = settings.level / settings.depth;
        double u = 0.5 - Math.sin(Math.asin(1.0 - 2.0 * fraction) / 3.0);
        return settings.halfWidth + settings.bank * u;
    }

    /** Mínimo suave: redondea la arista donde la sección corta el terreno. */
    static double smoothMin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0.0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    /**
     * Llena de líquido las columnas que quedan por debajo de su nivel sin que se escape:
     *
     * <ul>
     *     <li>fuera del cauce, la vecina más baja que el agua se levanta como ribazo;</li>
     *     <li>dentro del cauce, una vecina seca más baja que el agua le baja el nivel a esa columna;</li>
     *     <li>a lo largo del eje, el nivel nunca sube en el sentido del agua.</li>
     * </ul>
     *
     * <p>Los niveles solo bajan y los ribazos solo suben, así que se repite hasta que no cambia nada. Al final los
     * ribazos se ataludan hacia fuera un bloque por bloque.</p>
     */
    private static void fillAndContain(HeightField field, List<double[]> samples, double[] waterLevel, int[] levels,
                                       Runnable checkCancelled) {
        int columns = field.columns();
        int sizeX = field.sizeX();
        boolean[] channel = new boolean[columns];
        for (int index = 0; index < columns; index++) {
            if (!Double.isInfinite(waterLevel[index]) && field.editableAt(index)) {
                channel[index] = true;
                levels[index] = (int) Math.floor(waterLevel[index] + 1e-9);
            }
        }
        int[] axis = axisColumns(field, samples);
        boolean[] onAxis = new boolean[columns];
        for (int index : axis) {
            onAxis[index] = true;
        }
        int[] offsets = {-1, 1, -sizeX, sizeX};
        boolean[] raised = new boolean[columns];
        boolean changed = true;
        for (int round = 0; changed && round < 256; round++) {
            checkCancelled.run();
            changed = false;
            int running = Integer.MAX_VALUE;
            for (int index : axis) {
                if (!channel[index]) {
                    continue;
                }
                if (levels[index] > running) {
                    levels[index] = running;
                    changed = true;
                }
                running = Math.min(running, wet(field, levels, index) ? levels[index] : top(field, index));
            }
            for (int index = 0; index < columns; index++) {
                if (!channel[index] || !wet(field, levels, index)) {
                    continue;
                }
                int x = index % sizeX;
                for (int offset : offsets) {
                    int neighbour = index + offset;
                    if (neighbour < 0 || neighbour >= columns || Math.abs(neighbour % sizeX - x) > 1
                            || Double.isNaN(field.at(neighbour))) {
                        continue;
                    }
                    int ground = top(field, neighbour);
                    if (ground >= levels[index]) {
                        continue;
                    }
                    if (channel[neighbour]) {
                        if (wet(field, levels, neighbour)) {
                            // Agua más baja al lado: un salto de agua río abajo, se deja.
                            continue;
                        }
                        if (onAxis[neighbour] || !field.editableAt(neighbour)) {
                            // El eje nunca sube: el agua de aquí se queda a su altura.
                            levels[index] = ground;
                            changed = true;
                            break;
                        }
                        // Orilla seca de la sección de abajo, un bloque por debajo del agua de aquí: se levanta.
                        field.setAt(neighbour, levels[index]);
                        changed = true;
                        if (top(field, neighbour) < levels[index]) {
                            levels[index] = top(field, neighbour);
                            break;
                        }
                    } else if (field.editableAt(neighbour)) {
                        field.setAt(neighbour, levels[index]);
                        raised[neighbour] = true;
                        changed = true;
                        if (top(field, neighbour) < levels[index]) {
                            // No cabe más alto (fuera de la selección): el agua se queda a esa altura.
                            levels[index] = top(field, neighbour);
                            break;
                        }
                    } else {
                        levels[index] = ground;
                        changed = true;
                        break;
                    }
                }
            }
        }
        for (int index = 0; index < columns; index++) {
            if (levels[index] != TerrainColumns.NONE && !wet(field, levels, index)) {
                levels[index] = TerrainColumns.NONE;
            }
        }
        for (int ring = 0; ring < 6; ring++) {
            boolean[] next = raised.clone();
            for (int index = 0; index < columns; index++) {
                if (channel[index] || !field.editableAt(index) || Double.isNaN(field.at(index))) {
                    continue;
                }
                int x = index % sizeX;
                double support = Double.NEGATIVE_INFINITY;
                for (int offset : offsets) {
                    int neighbour = index + offset;
                    if (neighbour < 0 || neighbour >= columns || !raised[neighbour]
                            || Math.abs(neighbour % sizeX - x) > 1) {
                        continue;
                    }
                    support = Math.max(support, field.at(neighbour) - 1.0);
                }
                if (support > field.at(index) + 0.5) {
                    field.setAt(index, support);
                    next[index] = true;
                }
            }
            raised = next;
        }
    }

    private static boolean wet(HeightField field, int[] levels, int index) {
        return levels[index] != TerrainColumns.NONE && top(field, index) < levels[index];
    }

    private static int top(HeightField field, int index) {
        return (int) Math.round(field.at(index));
    }

    /** Columnas del eje sin repetir, en el sentido del agua. */
    private static int[] axisColumns(HeightField field, List<double[]> samples) {
        int[] axis = new int[samples.size()];
        int count = 0;
        int last = -1;
        for (double[] point : samples) {
            int index = field.index((int) Math.floor(point[0]), (int) Math.floor(point[2]));
            if (index >= 0 && index != last) {
                axis[count++] = index;
                last = index;
            }
        }
        return Arrays.copyOf(axis, count);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** Curvas compartidas. */
    static final class Curves {
        private Curves() {
        }

        static double smooth(double t) {
            double clamped = Math.clamp(t, 0.0, 1.0);
            return clamped * clamped * (3.0 - 2.0 * clamped);
        }
    }
}
