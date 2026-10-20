package com.komainu.moxia.clipboard;

import com.komainu.moxia.progress.ProgressLine;
import java.util.Locale;

/**
 * Escalar y girar una estructura de bloques sin dientes de sierra. Cada bloque de la salida se lee en el centro de
 * su posición transformada al revés, sobre el original ampliado con la regla del modo:
 *
 * <ul>
 *     <li><b>nearest</b>: el bloque más cercano, sin ampliar (lo de //rotate y un //stack escalado).</li>
 *     <li><b>scale2x</b> y <b>scale3x</b>: Scale2x (EPX) o Scale3x sobre el original; esquinas y diagonales salen
 *     redondeadas en vez de en escalera.</li>
 *     <li><b>rotsprite</b>: el original ampliado 8× con tres Scale2x y leído en la posición exacta; sirve para
 *     cualquier factor y cualquier ángulo (el algoritmo de RotSprite).</li>
 * </ul>
 *
 * <p>Escalar va alrededor de la esquina del bloque de origen (el bloque {@code 0} pasa a ocupar de {@code 0} a
 * {@code factor}); girar va alrededor del centro del bloque de origen y sobre el eje Y, en el sentido de //rotate
 * (positivo: de este a sur, el de las agujas del reloj visto desde arriba). Al girar, cada capa se amplía por su
 * lado y la altura no cambia. Un giro de un número exacto de cuartos de vuelta da lo mismo con cualquier modo.</p>
 */
public final class ClipboardTransform {
    private static final double EPSILON = 1.0E-7;

    private ClipboardTransform() {
    }

    public enum Mode {
        NEAREST("nearest", "vecino", "cercano"),
        SCALE2X("scale2x", "epx", "2x"),
        SCALE3X("scale3x", "3x"),
        ROTSPRITE("rotsprite", "suave");

        private final String id;
        private final String[] aliases;

        Mode(String id, String... aliases) {
            this.id = id;
            this.aliases = aliases;
        }

        public String id() {
            return id;
        }

        public static Mode byId(String input) {
            String name = input.trim().toLowerCase(Locale.ROOT);
            for (Mode mode : values()) {
                if (mode.id.equals(name)) {
                    return mode;
                }
                for (String alias : mode.aliases) {
                    if (alias.equals(name)) {
                        return mode;
                    }
                }
            }
            throw new IllegalArgumentException("Modo desconocido: " + input + " (nearest, scale2x, scale3x, rotsprite)");
        }
    }

    /**
     * @param factor    de 0,1 a 8 (lo mismo en los tres ejes)
     * @param maxVolume caja máxima de la salida, en bloques
     */
    public static VoxelVolume scale(VoxelVolume source, double factor, Mode mode, long maxVolume, Runnable cancel) {
        if (!(factor >= 0.1 && factor <= 8.0)) {
            throw new IllegalArgumentException("El factor va de 0,1 a 8");
        }
        int[] low = new int[3];
        int[] size = new int[3];
        int[] minimum = {source.minimumX(), source.minimumY(), source.minimumZ()};
        int[] extent = {source.sizeX(), source.sizeY(), source.sizeZ()};
        long volume = 1;
        for (int axis = 0; axis < 3; axis++) {
            low[axis] = (int) Math.floor(minimum[axis] * factor + EPSILON);
            int high = (int) Math.ceil((minimum[axis] + extent[axis]) * factor - EPSILON) - 1;
            size[axis] = Math.max(1, high - low[axis] + 1);
            volume *= size[axis];
        }
        checkVolume(volume, maxVolume);
        PixelArtScaler.Sampler sampler = sampler(source, mode, factor, PixelArtScaler.Planes.ALL);
        VoxelVolume result = new VoxelVolume(low[0], low[1], low[2], size[0], size[1], size[2]);
        for (int y = 0; y < size[1]; y++) {
            cancel.run();
            double sy = (low[1] + y + 0.5) / factor - minimum[1];
            for (int z = 0; z < size[2]; z++) {
                double sz = (low[2] + z + 0.5) / factor - minimum[2];
                for (int x = 0; x < size[0]; x++) {
                    double sx = (low[0] + x + 0.5) / factor - minimum[0];
                    int id = sample(sampler, source, sx, sy, sz, true);
                    if (id != VoxelVolume.AIR) {
                        result.set(low[0] + x, low[1] + y, low[2] + z, id);
                    }
                }
            }
        }
        return result;
    }

    /** Gira {@code degrees} grados sobre el eje Y alrededor del centro del bloque de origen. */
    public static VoxelVolume rotate(VoxelVolume source, double degrees, Mode mode, long maxVolume,
                                     Runnable cancel) {
        if (!Double.isFinite(degrees)) {
            throw new IllegalArgumentException("El ángulo no es un número");
        }
        double normalized = ((degrees % 360.0) + 360.0) % 360.0;
        double radians = Math.toRadians(normalized);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        // Los cuartos de vuelta, exactos: sin errores de redondeo que muevan un bloque.
        if (Math.abs(normalized % 90.0) < EPSILON || Math.abs(normalized % 90.0 - 90.0) < EPSILON) {
            cos = Math.round(cos);
            sin = Math.round(sin);
        }
        double lowX = Double.POSITIVE_INFINITY;
        double lowZ = Double.POSITIVE_INFINITY;
        double highX = Double.NEGATIVE_INFINITY;
        double highZ = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            double x = ((corner & 1) == 0 ? source.minimumX() : source.minimumX() + source.sizeX()) - 0.5;
            double z = ((corner & 2) == 0 ? source.minimumZ() : source.minimumZ() + source.sizeZ()) - 0.5;
            double rx = x * cos - z * sin + 0.5;
            double rz = x * sin + z * cos + 0.5;
            lowX = Math.min(lowX, rx);
            lowZ = Math.min(lowZ, rz);
            highX = Math.max(highX, rx);
            highZ = Math.max(highZ, rz);
        }
        int minX = (int) Math.floor(lowX + EPSILON);
        int minZ = (int) Math.floor(lowZ + EPSILON);
        int sizeX = Math.max(1, (int) Math.ceil(highX - EPSILON) - minX);
        int sizeZ = Math.max(1, (int) Math.ceil(highZ - EPSILON) - minZ);
        checkVolume((long) sizeX * source.sizeY() * sizeZ, maxVolume);
        PixelArtScaler.Sampler sampler = sampler(source, mode, 1.0, PixelArtScaler.Planes.HORIZONTAL);
        VoxelVolume result = new VoxelVolume(minX, source.minimumY(), minZ, sizeX, source.sizeY(), sizeZ);
        for (int z = 0; z < sizeZ; z++) {
            cancel.run();
            for (int x = 0; x < sizeX; x++) {
                // Centro del bloque de salida respecto al centro del de origen, girado al revés.
                double ox = minX + x;
                double oz = minZ + z;
                double sx = ox * cos + oz * sin + 0.5 - source.minimumX();
                double sz = -ox * sin + oz * cos + 0.5 - source.minimumZ();
                boolean exact = center(sx) && center(sz);
                for (int y = 0; y < source.sizeY(); y++) {
                    int id = exact
                            ? source.local((int) Math.floor(sx), y, (int) Math.floor(sz))
                            : sample(sampler, source, sx, y + 0.5, sz, false);
                    if (id != VoxelVolume.AIR) {
                        result.set(minX + x, source.minimumY() + y, minZ + z, id);
                    }
                }
            }
        }
        return result;
    }

    /** El original ampliado según el modo: RotSprite 8×; Scale2x hasta cubrir el factor (2, 4 u 8); Scale3x 3 o 9. */
    static PixelArtScaler.Sampler sampler(VoxelVolume source, Mode mode, double factor,
                                          PixelArtScaler.Planes planes) {
        int[] size = {source.sizeX(), source.sizeY(), source.sizeZ()};
        PixelArtScaler.Sampler identity = PixelArtScaler.identity(source);
        return switch (mode) {
            case NEAREST -> identity;
            case ROTSPRITE -> PixelArtScaler.rotSprite(source, planes);
            case SCALE2X -> {
                PixelArtScaler.Sampler sampler = PixelArtScaler.scale2x(identity, planes, size);
                while (sampler.factor() < Math.min(8.0, factor) - EPSILON) {
                    sampler = PixelArtScaler.scale2x(sampler, planes, size);
                }
                yield sampler;
            }
            case SCALE3X -> {
                PixelArtScaler.Sampler sampler = PixelArtScaler.scale3x(identity, planes, size);
                yield factor > 3.0 + EPSILON ? PixelArtScaler.scale3x(sampler, planes, size) : sampler;
            }
        };
    }

    /**
     * Bloque en la coordenada continua {@code (sx, sy, sz)} del original (desde su esquina menor) leído en la
     * rejilla ampliada. Si una coordenada cae justo en el centro de un bloque y la ampliación es par, el centro es la
     * frontera entre dos trocitos: se leen los dos y gana el más repetido (a igualdad, el bloque original). Así no se
     * favorece un lado y un factor entero o un cuarto de vuelta no se comen esquinas.
     *
     * @param scaleY si la altura también está ampliada (al girar no lo está)
     */
    private static int sample(PixelArtScaler.Sampler sampler, VoxelVolume source, double sx, double sy, double sz,
                              boolean scaleY) {
        int level = sampler.factor();
        if (level == 1) {
            return source.local((int) Math.floor(sx), (int) Math.floor(sy), (int) Math.floor(sz));
        }
        int[] xs = candidates(sx, level);
        int[] ys = scaleY ? candidates(sy, level) : new int[]{(int) Math.floor(sy)};
        int[] zs = candidates(sz, level);
        if (xs.length == 1 && ys.length == 1 && zs.length == 1) {
            return sampler.at(xs[0], ys[0], zs[0]);
        }
        int[] values = new int[xs.length * ys.length * zs.length];
        int count = 0;
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    values[count++] = sampler.at(x, y, z);
                }
            }
        }
        int best = -1;
        int bestVotes = 0;
        boolean tie = false;
        for (int first = 0; first < count; first++) {
            int votes = 0;
            for (int second = 0; second < count; second++) {
                if (values[second] == values[first]) {
                    votes++;
                }
            }
            if (votes > bestVotes) {
                best = values[first];
                bestVotes = votes;
                tie = false;
            } else if (votes == bestVotes && values[first] != best) {
                tie = true;
            }
        }
        return tie ? source.local((int) Math.floor(sx), (int) Math.floor(sy), (int) Math.floor(sz)) : best;
    }

    /** Trocitos de la rejilla ampliada {@code level} veces en la coordenada {@code value}: uno o, en un centro, dos. */
    private static int[] candidates(double value, int level) {
        int cell = (int) Math.floor(value * level + EPSILON);
        if (level % 2 == 0 && center(value)) {
            return new int[]{cell - 1, cell};
        }
        return new int[]{cell};
    }

    private static boolean center(double value) {
        return Math.abs(value - Math.floor(value) - 0.5) < 1.0E-6;
    }

    private static void checkVolume(long volume, long maximum) {
        if (volume > maximum) {
            throw new IllegalArgumentException("El resultado ocuparía " + ProgressLine.group(volume) + " bloques de caja (máximo "
                    + maximum + "); usa un factor menor o copia menos");
        }
    }
}
