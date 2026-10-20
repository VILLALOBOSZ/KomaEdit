package com.komainu.moxia.terrain.stamp;

import com.komainu.moxia.terrain.HeightField;
import com.komainu.moxia.terrain.HydraulicErosion;
import com.komainu.moxia.terrain.ThermalErosion;

/**
 * Cómo se genera un sello de serie: la forma (una función o un campo entero), la erosión que se le hornea y el gris
 * neutro. {@link #generate} hace siempre lo mismo para la misma semilla:
 *
 * <ol>
 *     <li>la forma en bruto, con capas de ruido, crestas y valles;</li>
 *     <li>la erosión del terraformado ({@link HydraulicErosion} y {@link ThermalErosion}) sobre esa forma puesta a
 *     escala de bloques, para que salgan cauces, crestas afiladas y faldas de derrubios en lugar de conos lisos;</li>
 *     <li>el borde: desde un poco antes de {@code edge} hasta el círculo inscrito todo baja suave al gris neutro, para
 *     que el sello se funda con lo que haya;</li>
 *     <li>la normalización: el blanco puro es lo más alto (o el negro lo más hondo en los que hunden).</li>
 * </ol>
 *
 * @param neutral el gris que no cambia nada: 0 en los que levantan, 1 en los que hunden, 0,5 en los de detalle
 * @param edge    radio (0 a 1) donde empieza a fundirse con el borde
 * @param ratio   altura natural del sello dividida por su radio (la del pincel en automático); negativa: la de la
 *                erosión horneada o la de la categoría
 */
public record StampRecipe(String name, StampCategory category, String description, double neutral, double edge,
                          Field field, Bake bake, double ratio) {
    /** Lado de la rejilla de trabajo con el que están ajustadas las erosiones (en bloques). */
    public static final int REFERENCE_SIZE = 160;
    /** Lo que baja el llano del centro a la esquina mientras se erosiona (en bloques, con la rejilla de serie). */
    private static final double RAMP_BLOCKS = 6.0;
    /** Cuánto antes del {@code edge} de la receta empieza a fundirse con el borde. */
    private static final double WINDOW_LEAD = 0.15;

    public StampRecipe(String name, StampCategory category, String description, double neutral, double edge,
                       Field field, Bake bake) {
        this(name, category, description, neutral, edge, field, bake, -1.0);
    }

    /** La misma receta con otra altura natural. */
    StampRecipe withRatio(double value) {
        return new StampRecipe(name, category, description, neutral, edge, field, bake, value);
    }

    /**
     * Altura natural dividida por el radio: con radio 20 y 0,5 sale una forma de 10 bloques que se ve como en la
     * galería. Si la receta no la fija, la de la erosión horneada (la forma se erosionó con esas proporciones) o la
     * típica de su categoría.
     */
    public double heightRatio() {
        if (ratio > 0.0) {
            return ratio;
        }
        if (bake != null) {
            return bake.blocks / (REFERENCE_SIZE / 2.0);
        }
        return category.heightRatio();
    }

    /** Forma punto a punto; {@code x} e {@code y} de -1 a 1 y {@code r} la distancia al centro. */
    @FunctionalInterface
    public interface Shape {
        double value(double x, double y, double r, long seed);
    }

    /** Forma que necesita ver todo el campo (campos de cráteres, cárcavas…): valores por filas. */
    @FunctionalInterface
    public interface Field {
        double[] build(int size, long seed);
    }

    /**
     * Erosión horneada.
     *
     * @param blocks   altura en bloques de la forma (con la rejilla de {@value #REFERENCE_SIZE}) al erosionarla: la
     *                 pendiente decide dónde corre el agua y dónde resbala el suelo
     * @param drops    gotas por columna (0 sin hidráulica)
     * @param strength fuerza de la hidráulica (1 normal)
     * @param passes   pasadas térmicas (0 sin térmica)
     * @param talus    ángulo de reposo de la térmica
     */
    public record Bake(double blocks, double drops, double strength, int passes, double talus) {
        static Bake water(double blocks, double drops, double strength) {
            return new Bake(blocks, drops, strength, 0, 40.0);
        }

        static Bake full(double blocks, double drops, double strength, int passes, double talus) {
            return new Bake(blocks, drops, strength, passes, talus);
        }

        static Bake slide(double blocks, int passes, double talus) {
            return new Bake(blocks, 0.0, 1.0, passes, talus);
        }
    }

    static Field points(Shape shape) {
        return (size, seed) -> {
            double[] values = new double[size * size];
            for (int row = 0; row < size; row++) {
                double y = coordinate(row, size);
                for (int column = 0; column < size; column++) {
                    double x = coordinate(column, size);
                    values[row * size + column] = shape.value(x, y, Math.sqrt(x * x + y * y), seed);
                }
            }
            return values;
        };
    }

    /** Coordenada de -1 a 1 de una fila o columna: la primera es -1 y la última 1. */
    static double coordinate(int index, int size) {
        return index / (size - 1.0) * 2.0 - 1.0;
    }

    /** La semilla fija del sello de serie: un hash estable del nombre. */
    public long defaultSeed() {
        long hash = 0xCBF29CE484222325L;
        for (int index = 0; index < name.length(); index++) {
            hash ^= name.charAt(index);
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    /** Valores de 0 a 1 por filas, {@code size}×{@code size}. */
    public float[] generate(int size, long seed) {
        double[] values = field.build(size, seed);
        if (bake != null) {
            erode(values, size, seed);
        }
        for (int row = 0; row < size; row++) {
            double y = coordinate(row, size);
            for (int column = 0; column < size; column++) {
                double x = coordinate(column, size);
                double r = Math.sqrt(x * x + y * y);
                int index = row * size + column;
                // El borde empieza un poco antes de lo que pide la receta: la falda llega a cero con una curva
                // larga, sin el codo que se vería como un anillo alrededor.
                double window = StampMath.smoothstep(1.0, edge - WINDOW_LEAD, r);
                values[index] = r >= 1.0 ? neutral : neutral + (values[index] - neutral) * window;
            }
        }
        // Después del borde: así lo más alto que queda (o lo más hondo) es exactamente el blanco (o el negro).
        normalize(values);
        float[] result = new float[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (float) Math.clamp(values[index], 0.0, 1.0);
        }
        return result;
    }

    private void erode(double[] values, int size, long seed) {
        double scale = bake.blocks * size / REFERENCE_SIZE;
        // Mientras se erosiona, el llano de alrededor baja un poco hacia fuera: el agua que sale de la forma sigue
        // corriendo y se lleva sus derrubios fuera del círculo, en lugar de dejar una capa fina que el borde
        // cortaría en un anillo. Después se quita la rampa.
        double[] ramp = new double[size * size];
        double rampHeight = neutral < 0.5 ? RAMP_BLOCKS * size / REFERENCE_SIZE : 0.0;
        HeightField heights = new HeightField(0, 0, size, size);
        for (int row = 0; row < size; row++) {
            double y = coordinate(row, size);
            for (int column = 0; column < size; column++) {
                double x = coordinate(column, size);
                int index = row * size + column;
                ramp[index] = rampHeight * Math.max(0.0, 1.5 - Math.sqrt(x * x + y * y)) / 1.5;
                double value = values[index];
                // En los que hunden, el terreno de alrededor (el blanco) no se erosiona: el agua talla las paredes y
                // el fondo, y el borde sigue liso para fundirse con lo que haya.
                double lowest = neutral >= 0.5 && value >= neutral - 0.002 ? value * scale : 0.0;
                heights.define(column, row, value * scale + ramp[index], true, lowest + ramp[index],
                        Double.POSITIVE_INFINITY);
            }
        }
        Runnable never = () -> {
        };
        if (bake.drops > 0.0) {
            HydraulicErosion.run(heights, HydraulicErosion.Settings.of(size * size, bake.drops, bake.strength,
                    seed ^ 0x5EEDL), never);
        }
        if (bake.passes > 0) {
            ThermalErosion.run(heights, new ThermalErosion.Settings(bake.passes, bake.talus, 1.0), never);
        }
        for (int index = 0; index < values.length; index++) {
            values[index] = (heights.at(index) - ramp[index]) / scale;
        }
    }

    /** El blanco a 1 (o el negro a 0 en los que hunden), sin mover el gris neutro. */
    private void normalize(double[] values) {
        double above = 0.0;
        double below = 0.0;
        for (double value : values) {
            above = Math.max(above, value - neutral);
            below = Math.max(below, neutral - value);
        }
        double up = above > 1e-9 ? (1.0 - neutral) / above : 1.0;
        double down = below > 1e-9 ? neutral / below : 1.0;
        if (neutral > 0.0 && neutral < 1.0) {
            // Los que suben y bajan (detalle, cráteres) conservan la proporción entre lo uno y lo otro.
            double both = Math.min(up, down);
            up = both;
            down = both;
        }
        for (int index = 0; index < values.length; index++) {
            double delta = values[index] - neutral;
            values[index] = neutral + (delta >= 0.0 ? delta * up : delta * down);
        }
    }
}
