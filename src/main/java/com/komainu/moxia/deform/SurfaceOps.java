package com.komainu.moxia.deform;

import com.komainu.moxia.noise.NoiseSpec;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.IntPredicate;

/**
 * Retoques de superficie ({@code /komaedit surface}): mueven la superficie hacia dentro o hacia fuera hasta
 * {@code radius} bloques según un ruido, sin tocar lo que queda más lejos.
 *
 * <p>Se calcula la distancia con signo a la superficie (exacta, {@link DistanceTransform}) y la nueva forma es
 * {@code distancia < radio · ruido}. Después, si se pide, ese campo se suaviza. Lo que crece toma el material
 * del bloque ocupado más cercano.</p>
 */
public final class SurfaceOps {
    private SurfaceOps() {
    }

    /** Los cuatro modos de ezEdits. */
    public enum Mode {
        FUZZIFY("fuzzify", "pelusa"),
        ROCKIFY("rockify", "roca"),
        VORONOIFY("voronoify", "celdas"),
        NOISIFY("noisify", "ruido");

        private final String id;
        private final String alias;

        Mode(String id, String alias) {
            this.id = id;
            this.alias = alias;
        }

        public String id() {
            return id;
        }

        public static Mode byId(String input) {
            String text = input.trim().toLowerCase(Locale.ROOT);
            for (Mode mode : values()) {
                if (mode.id.equals(text) || mode.alias.equals(text)) {
                    return mode;
                }
            }
            if (text.equals("noiseify")) {
                return NOISIFY;
            }
            throw new IllegalArgumentException("Modo de superficie desconocido: " + input
                    + " (fuzzify, rockify, voronoify, noisify)");
        }
    }

    /** Ruido de desplazamiento en [0, 1] por punto. */
    @FunctionalInterface
    public interface Displacement {
        double at(double x, double y, double z);

        /** Ruido blanco: un valor al azar por bloque. */
        static Displacement white(long seed) {
            return (x, y, z) -> Deformations.unit((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), seed);
        }

        /** Perlin fractal con rasgos de unos {@code size} bloques. */
        static Displacement perlin(double size, int octaves, long seed) {
            NoiseSpec spec = NoiseSpec.parse("Perlin(F:" + (1.0 / Math.max(0.5, size)) + ",fT:FBm,fO:"
                    + Math.clamp(octaves, 1, 8) + ")");
            NoiseSpec.NoiseField field = spec.build(seed);
            return field::sample;
        }

        /**
         * Celdas de Voronoi de unos {@code size} bloques: {@code 1 − (F1/F2)²}, una cúpula por celda (1 en su centro)
         * con un pliegue en cada borde (0). El cuadrado centra el reparto en 0,5: se talla y se crece a partes
         * parecidas, sin mesetas.
         */
        static Displacement cells(double size, long seed) {
            double cell = Math.max(1.0, size);
            return (x, y, z) -> {
                int cellX = (int) Math.floor(x / cell);
                int cellY = (int) Math.floor(y / cell);
                int cellZ = (int) Math.floor(z / cell);
                double first = Double.POSITIVE_INFINITY;
                double second = Double.POSITIVE_INFINITY;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            double[] site = Deformations.site(cellX + dx, cellY + dy, cellZ + dz, cell, seed);
                            double distance = Math.sqrt(Deformations.squared(site[0] - x, site[1] - y, site[2] - z));
                            if (distance < first) {
                                second = first;
                                first = distance;
                            } else if (distance < second) {
                                second = distance;
                            }
                        }
                    }
                }
                double ratio = second > 0.0 ? first / second : 1.0;
                return 1.0 - ratio * ratio;
            };
        }

        /** Un ruido de ezEdits con su escala. */
        static Displacement of(NoiseSpec spec, double scale, long seed) {
            NoiseSpec.NoiseField field = spec.build(seed);
            double factor = 1.0 / Math.max(1.0E-3, scale);
            return (x, y, z) -> field.sample(x * factor, y * factor, z * factor);
        }
    }

    /**
     * @param carveOnly   {@code -c}: solo tallar
     * @param expandOnly  {@code -e}: solo crecer
     * @param mask        {@code -m}: qué materiales se pueden tallar o desde cuáles se puede crecer (o {@code null})
     * @param topology    {@code -t}: no abrir agujeros ni partir piezas
     */
    public record Settings(
            double radius,
            Displacement displacement,
            int smoothRadius,
            int smoothIterations,
            boolean carveOnly,
            boolean expandOnly,
            IntPredicate mask,
            boolean topology
    ) {
        public Settings {
            if (!(radius > 0.0 && radius <= 32.0)) {
                throw new IllegalArgumentException("El radio va de 0 a 32");
            }
            if (carveOnly && expandOnly) {
                throw new IllegalArgumentException("-c y -e no se pueden usar a la vez");
            }
            if (smoothRadius < 0 || smoothRadius > 8 || smoothIterations < 0 || smoothIterations > 16) {
                throw new IllegalArgumentException("El suavizado admite radio 0–8 e iteraciones 0–16");
            }
        }

        /** Margen que hay que leer alrededor de la selección. */
        public int padding() {
            return (int) Math.ceil(radius) + smoothRadius * Math.max(1, smoothIterations) + 2;
        }
    }

    public static DeformField apply(Volume volume, Settings settings, Runnable checkCancelled) {
        boolean[] occupancy = volume.occupancy();
        SignedDistance distance = SignedDistance.of(volume, occupancy);
        float[] values = new float[occupancy.length];
        for (int index = 0; index < values.length; index++) {
            checkCancelled.run();
            double noise = Math.clamp(settings.displacement().at(
                    volume.x(index) + 0.5, volume.y(index) + 0.5, volume.z(index) + 0.5), 0.0, 1.0);
            double push = settings.carveOnly() ? -noise : settings.expandOnly() ? noise : 2.0 * noise - 1.0;
            values[index] = (float) (settings.radius() * push - distance.signed[index]);
        }
        ScalarField field = new ScalarField(volume, values);
        for (int iteration = 0; iteration < settings.smoothIterations() && settings.smoothRadius() > 0; iteration++) {
            checkCancelled.run();
            field.boxBlur(settings.smoothRadius(), settings.smoothRadius(), settings.smoothRadius());
        }
        boolean[] result = new boolean[occupancy.length];
        int[] grownMaterial = distance.nearestOccupied;
        IntPredicate mask = settings.mask();
        for (int index = 0; index < result.length; index++) {
            boolean wanted = field.values[index] > 0.0F;
            boolean before = occupancy[index];
            if (!volume.selected[index] || wanted == before) {
                result[index] = before;
                continue;
            }
            if (before) {
                wanted = settings.expandOnly() || mask != null && !mask.test(volume.material[index]);
            } else {
                int source = grownMaterial[index];
                wanted = !settings.carveOnly() && source >= 0
                        && (mask == null || mask.test(volume.material[source]));
            }
            result[index] = wanted;
        }
        if (settings.topology()) {
            keepTopology(volume, occupancy, result, field.values, checkCancelled);
        }
        return new DeformField() {
            @Override
            public int material(int x, int y, int z) {
                if (!volume.selected(x, y, z)) {
                    return KEEP;
                }
                int index = volume.index(x, y, z);
                if (!result[index]) {
                    return AIR;
                }
                if (occupancy[index]) {
                    return volume.material[index];
                }
                int source = grownMaterial[index];
                return source >= 0 ? volume.material[source] : AIR;
            }

            @Override
            public boolean solid(double x, double y, double z) {
                int bx = (int) Math.floor(x);
                int by = (int) Math.floor(y);
                int bz = (int) Math.floor(z);
                if (!volume.contains(bx, by, bz)) {
                    return false;
                }
                int index = volume.index(bx, by, bz);
                if (result[index] == occupancy[index] && !volume.selected[index]) {
                    return occupancy[index];
                }
                if (settings.carveOnly() && !occupancy[index] || settings.expandOnly() && occupancy[index]) {
                    return occupancy[index];
                }
                return field.sample(x, y, z) > 0.0;
            }
        };
    }

    /**
     * Aplica los cambios uno a uno, de más seguro a menos, y solo si el bloque es simple en ese momento: así no se
     * abren túneles, no se cierran huecos y nada se parte en dos. Lo que no se puede cambiar se queda como estaba.
     */
    static void keepTopology(Volume volume, boolean[] before, boolean[] wanted, float[] strength,
                             Runnable checkCancelled) {
        int changes = 0;
        for (int index = 0; index < wanted.length; index++) {
            if (wanted[index] != before[index]) {
                changes++;
            }
        }
        if (changes == 0) {
            return;
        }
        long[] order = new long[changes];
        int cursor = 0;
        for (int index = 0; index < wanted.length; index++) {
            if (wanted[index] != before[index]) {
                int rank = Math.round(Math.min(1.0E6F, Math.abs(strength[index]) * 1_000.0F));
                order[cursor++] = ((long) (1_000_000 - rank) << 32) | index;
            }
        }
        Arrays.sort(order);
        boolean[] state = before.clone();
        boolean[] neighbourhood = new boolean[27];
        for (int pass = 0; pass < 2; pass++) {
            for (long entry : order) {
                checkCancelled.run();
                int index = (int) entry;
                if (state[index] == wanted[index]) {
                    continue;
                }
                Topology.gather(volume, state, index, neighbourhood);
                if (Topology.simple(neighbourhood)) {
                    state[index] = wanted[index];
                }
            }
        }
        System.arraycopy(state, 0, wanted, 0, state.length);
    }

    /** Distancia con signo a la superficie (positiva fuera) y el bloque ocupado más cercano a cada posición. */
    static final class SignedDistance {
        final float[] signed;
        final int[] nearestOccupied;

        private SignedDistance(float[] signed, int[] nearestOccupied) {
            this.signed = signed;
            this.nearestOccupied = nearestOccupied;
        }

        static SignedDistance of(Volume volume, boolean[] occupancy) {
            boolean[] empty = new boolean[occupancy.length];
            for (int index = 0; index < occupancy.length; index++) {
                empty[index] = !occupancy[index];
            }
            DistanceTransform.Result outside = DistanceTransform.compute(volume, occupancy, 1.0, 1.0, 1.0);
            DistanceTransform.Result inside = DistanceTransform.compute(volume, empty, 1.0, 1.0, 1.0);
            float[] signed = new float[occupancy.length];
            for (int index = 0; index < occupancy.length; index++) {
                signed[index] = occupancy[index]
                        ? -(float) (Math.sqrt(Math.min(inside.squared()[index], 1.0E6F)) - 0.5)
                        : (float) (Math.sqrt(Math.min(outside.squared()[index], 1.0E6F)) - 0.5);
            }
            return new SignedDistance(signed, outside.nearest());
        }
    }
}
