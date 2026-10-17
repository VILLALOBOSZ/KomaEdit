package com.komainu.moxia.generator;

import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.shape.tube.TubeFormula;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import com.komainu.moxia.edit.PackedBlockPlan;

import java.util.SplittableRandom;

/**
 * Campo de flujo de {@code /komaedit flowfield}: el ruido da una dirección en cada punto y muchas partículas lo
 * recorren dejando rastro. Cada paso de una línea suma su fuerza al bloque que pisa; al final la suma de cada
 * bloque elige la entrada de la paleta (más líneas cruzadas, entradas más altas).
 *
 * <ul>
 *     <li>Por defecto el flujo es plano: las partículas se mueven en horizontal (la gravedad puede moverlas en
 *     vertical) y el ruido se muestrea en 3D, así que cada altura tiene su propio dibujo.</li>
 *     <li>{@code -t}: flujo 3D, la dirección sale de tres muestras de ruido desfasadas.</li>
 *     <li>{@code -h}: mapa de alturas; las partículas siguen la superficie del terreno.</li>
 *     <li>{@code -c}: el rotacional del campo. En 2D es (∂ψ/∂z, −∂ψ/∂x) con ψ el ruido; en 3D, el rotacional de
 *     tres ruidos. No tiene divergencia: salen remolinos que no se juntan en sumideros.</li>
 * </ul>
 */
public final class FlowField {
    public static final int MAX_LINES = 200_000;
    public static final int MAX_ITERATIONS = 4_096;
    public static final long MAX_STEPS = 60_000_000L;
    private static final double EPSILON = 1.0E-9;
    /** Desfase entre los ruidos de las componentes del flujo 3D: lejos para que no se parezcan. */
    private static final double CHANNEL_OFFSET = 7_919.31;

    private FlowField() {
    }

    /** Dónde se pone el bloque en el modo mapa de alturas. */
    public enum Distribution {
        SURFACE("surface", "superficie"),
        ABOVE("above", "encima");

        private final String id;
        private final String alias;

        Distribution(String id, String alias) {
            this.id = id;
            this.alias = alias;
        }

        public String id() {
            return id;
        }

        public static boolean known(String text) {
            try {
                byId(text);
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        public static Distribution byId(String text) {
            String normalized = GeneratorArgs.lower(text);
            for (Distribution distribution : values()) {
                if (distribution.id.equals(normalized) || distribution.alias.equals(normalized)) {
                    return distribution;
                }
            }
            throw new IllegalArgumentException("Modo de mapa de alturas desconocido: " + text + " (surface o above)");
        }
    }

    /**
     * @param lines        número de líneas; si {@code percent} es verdadero, líneas por cada 100 bloques (o columnas)
     * @param velocity     bloques que avanza cada paso
     * @param paletteScalar multiplica la suma de cada bloque antes de elegir la entrada de la paleta
     * @param heightmap    {@code -h}: con su modo de reparto, o {@code null} sin mapa de alturas
     * @param inertia      0–1: cuánto pesa la dirección anterior frente a la del campo
     * @param boundary     bloques extra en los que una línea puede salir de la selección y volver
     * @param progression  fuerza al principio y al final de cada línea ({@code -p a:b})
     */
    public record Settings(
            double lines,
            boolean percent,
            int iterations,
            double velocity,
            double paletteScalar,
            NoiseSpec noise,
            Distribution heightmap,
            boolean masked,
            double inertia,
            double[] gravity,
            double[] jitter,
            int boundary,
            TubeFormula modX,
            TubeFormula modY,
            TubeFormula modZ,
            double progressionStart,
            double progressionEnd,
            long seed,
            boolean curl,
            boolean fill,
            boolean threeD
    ) {
    }

    /** Lo que el flujo necesita leer del mundo (solo con {@code -h} o {@code -m}). */
    public interface Terrain {
        int NONE = Integer.MIN_VALUE;

        /** Bloque sólido más alto de la columna dentro de la selección; {@link #NONE} si no hay. */
        int surface(int x, int z);

        /** El bloque cumple la máscara de origen ({@code -m}). */
        boolean source(int x, int y, int z);
    }

    public static void generate(Settings settings, Volume volume, Terrain terrain, int paletteSize,
                                GeneratedBlocks out) {
        if ((settings.heightmap() != null || settings.masked()) && terrain == null) {
            throw new IllegalArgumentException("El mapa de alturas y la máscara necesitan leer el terreno");
        }
        Field field = new Field(settings);
        SplittableRandom random = new SplittableRandom(settings.seed());
        boolean surfaceMode = settings.heightmap() != null;
        LongArrayList starts = startCandidates(settings, volume, terrain, surfaceMode, out);
        long area = surfaceMode ? columns(volume) : volumeCount(volume, out);
        int lines = settings.percent()
                ? (int) Math.min(MAX_LINES, Math.round(area * settings.lines() / 100.0))
                : (int) Math.min(MAX_LINES, Math.round(settings.lines()));
        if ((long) lines * settings.iterations() > MAX_STEPS) {
            throw new IllegalArgumentException("Demasiado trabajo: " + lines + " líneas × " + settings.iterations()
                    + " pasos (máximo " + MAX_STEPS + ")");
        }
        int padding = settings.boundary();
        int minimumX = volume.minimumX() - padding;
        int maximumX = volume.maximumX() + padding;
        int minimumY = volume.minimumY() - padding;
        int maximumY = volume.maximumY() + padding;
        int minimumZ = volume.minimumZ() - padding;
        int maximumZ = volume.maximumZ() + padding;

        Long2DoubleOpenHashMap strength = new Long2DoubleOpenHashMap();
        LongOpenHashSet visited = new LongOpenHashSet();
        double[] position = new double[3];
        double[] direction = new double[3];
        double[] flow = new double[3];
        for (int line = 0; line < lines; line++) {
            if (!start(settings, volume, terrain, surfaceMode, starts, random, position)) {
                break;
            }
            visited.clear();
            direction[0] = 0;
            direction[1] = 0;
            direction[2] = 0;
            boolean first = true;
            for (int step = 0; step < settings.iterations(); step++) {
                out.checkCancelled();
                double progress = settings.iterations() == 1 ? 0.0 : step / (double) (settings.iterations() - 1);
                double weight = settings.progressionStart()
                        + (settings.progressionEnd() - settings.progressionStart()) * progress;
                field.direction(position[0], position[1], position[2], settings.threeD() && !surfaceMode, flow);
                double inertia = first ? 0.0 : settings.inertia();
                first = false;
                double dx = inertia * direction[0] + (1.0 - inertia) * flow[0];
                double dy = inertia * direction[1] + (1.0 - inertia) * flow[1];
                double dz = inertia * direction[2] + (1.0 - inertia) * flow[2];
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length > EPSILON) {
                    dx /= length;
                    dy /= length;
                    dz /= length;
                }
                direction[0] = dx;
                direction[1] = dy;
                direction[2] = dz;
                double nextX = position[0] + dx * settings.velocity() + settings.gravity()[0];
                double nextY = position[1] + dy * settings.velocity() + settings.gravity()[1];
                double nextZ = position[2] + dz * settings.velocity() + settings.gravity()[2];
                if (surfaceMode) {
                    int top = terrain.surface((int) Math.floor(nextX), (int) Math.floor(nextZ));
                    if (top == Terrain.NONE) {
                        deposit(position, position, weight, volume, terrain, settings, strength, visited);
                        break;
                    }
                    nextY = top + 0.5;
                }
                deposit(position, new double[]{nextX, nextY, nextZ}, weight, volume, terrain, settings, strength,
                        visited);
                position[0] = nextX;
                position[1] = nextY;
                position[2] = nextZ;
                if (nextX < minimumX || nextX >= maximumX + 1 || nextY < minimumY || nextY >= maximumY + 1
                        || nextZ < minimumZ || nextZ >= maximumZ + 1) {
                    break;
                }
            }
        }
        write(settings, volume, terrain, surfaceMode, paletteSize, strength, out);
    }

    /**
     * Recorre el segmento a pasos de medio bloque como mucho y suma la fuerza una vez por bloque y línea. En el
     * mapa de alturas cada muestra baja o sube a la superficie de su columna.
     */
    private static void deposit(double[] from, double[] to, double weight, Volume volume, Terrain terrain,
                                Settings settings, Long2DoubleOpenHashMap strength, LongOpenHashSet visited) {
        boolean surfaceMode = settings.heightmap() != null;
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double dz = to[2] - from[2];
        int samples = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 2.0));
        for (int sample = 0; sample <= samples; sample++) {
            double t = sample / (double) samples;
            int x = (int) Math.floor(from[0] + dx * t);
            int y = (int) Math.floor(from[1] + dy * t);
            int z = (int) Math.floor(from[2] + dz * t);
            if (surfaceMode) {
                y = volume.containsColumn(x, z) ? terrain.surface(x, z) : Terrain.NONE;
                if (y == Terrain.NONE) {
                    continue;
                }
            } else if (!volume.contains(x, y, z)) {
                continue;
            }
            long key = PackedBlockPlan.pack(x, y, z);
            if (visited.add(key)) {
                strength.addTo(key, weight);
            }
        }
    }

    private static void write(Settings settings, Volume volume, Terrain terrain, boolean surfaceMode,
                              int paletteSize, Long2DoubleOpenHashMap strength, GeneratedBlocks out) {
        int offset = surfaceMode && settings.heightmap() == Distribution.ABOVE ? 1 : 0;
        LongOpenHashSet placed = new LongOpenHashSet();
        for (Long2DoubleMap.Entry entry : strength.long2DoubleEntrySet()) {
            out.checkCancelled();
            double value = entry.getDoubleValue() * settings.paletteScalar();
            if (value <= EPSILON) {
                continue;
            }
            long key = entry.getLongKey();
            int index = Math.clamp((int) Math.ceil(value - EPSILON) - 1, 0, paletteSize - 1);
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key) + offset;
            int z = PackedBlockPlan.unpackZ(key);
            if (placed.add(PackedBlockPlan.pack(x, y, z))) {
                out.add(x, y, z, index);
            }
        }
        if (!settings.fill()) {
            return;
        }
        if (surfaceMode) {
            for (int z = volume.minimumZ(); z <= volume.maximumZ(); z++) {
                for (int x = volume.minimumX(); x <= volume.maximumX(); x++) {
                    out.checkCancelled();
                    if (!volume.containsColumn(x, z)) {
                        continue;
                    }
                    int top = terrain.surface(x, z);
                    if (top != Terrain.NONE && placed.add(PackedBlockPlan.pack(x, top + offset, z))) {
                        out.add(x, top + offset, z, 0);
                    }
                }
            }
            return;
        }
        for (int y = volume.minimumY(); y <= volume.maximumY(); y++) {
            for (int z = volume.minimumZ(); z <= volume.maximumZ(); z++) {
                for (int x = volume.minimumX(); x <= volume.maximumX(); x++) {
                    out.checkCancelled();
                    if (volume.contains(x, y, z) && placed.add(PackedBlockPlan.pack(x, y, z))) {
                        out.add(x, y, z, 0);
                    }
                }
            }
        }
    }

    /** Con máscara, los puntos de salida posibles: los bloques de la selección que la cumplen. */
    private static LongArrayList startCandidates(Settings settings, Volume volume, Terrain terrain,
                                                 boolean surfaceMode, GeneratedBlocks out) {
        if (!settings.masked()) {
            return null;
        }
        LongArrayList candidates = new LongArrayList();
        for (int z = volume.minimumZ(); z <= volume.maximumZ(); z++) {
            for (int x = volume.minimumX(); x <= volume.maximumX(); x++) {
                if (surfaceMode) {
                    out.checkCancelled();
                    int top = terrain.surface(x, z);
                    if (top != Terrain.NONE && terrain.source(x, top, z)) {
                        candidates.add(PackedBlockPlan.pack(x, top, z));
                    }
                    continue;
                }
                for (int y = volume.minimumY(); y <= volume.maximumY(); y++) {
                    out.checkCancelled();
                    if (volume.contains(x, y, z) && terrain.source(x, y, z)) {
                        candidates.add(PackedBlockPlan.pack(x, y, z));
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Ningún bloque de la selección cumple la máscara de salida (-m)");
        }
        return candidates;
    }

    /** Elige el punto de salida de una línea; {@code false} si no encuentra ninguno válido. */
    private static boolean start(Settings settings, Volume volume, Terrain terrain, boolean surfaceMode,
                                 LongArrayList candidates, SplittableRandom random, double[] position) {
        double[] jitter = settings.jitter();
        for (int attempt = 0; attempt < 64; attempt++) {
            if (candidates != null) {
                long key = candidates.getLong(random.nextInt(candidates.size()));
                position[0] = PackedBlockPlan.unpackX(key) + 0.5;
                position[1] = PackedBlockPlan.unpackY(key) + 0.5;
                position[2] = PackedBlockPlan.unpackZ(key) + 0.5;
            } else {
                position[0] = volume.minimumX() + random.nextDouble() * volume.sizeX();
                position[1] = volume.minimumY() + random.nextDouble() * volume.sizeY();
                position[2] = volume.minimumZ() + random.nextDouble() * volume.sizeZ();
            }
            position[0] += (random.nextDouble() * 2.0 - 1.0) * jitter[0];
            position[1] += (random.nextDouble() * 2.0 - 1.0) * jitter[1];
            position[2] += (random.nextDouble() * 2.0 - 1.0) * jitter[2];
            int x = (int) Math.floor(position[0]);
            int z = (int) Math.floor(position[2]);
            if (surfaceMode) {
                int top = volume.containsColumn(x, z) ? terrain.surface(x, z) : Terrain.NONE;
                if (top != Terrain.NONE) {
                    position[1] = top + 0.5;
                    return true;
                }
            } else if (candidates != null || volume.contains(x, (int) Math.floor(position[1]), z)) {
                return true;
            }
        }
        return false;
    }

    private static long columns(Volume volume) {
        long count = 0;
        for (int z = volume.minimumZ(); z <= volume.maximumZ(); z++) {
            for (int x = volume.minimumX(); x <= volume.maximumX(); x++) {
                if (volume.containsColumn(x, z)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static long volumeCount(Volume volume, GeneratedBlocks out) {
        long count = 0;
        for (int y = volume.minimumY(); y <= volume.maximumY(); y++) {
            for (int z = volume.minimumZ(); z <= volume.maximumZ(); z++) {
                for (int x = volume.minimumX(); x <= volume.maximumX(); x++) {
                    out.checkCancelled();
                    if (volume.contains(x, y, z)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Dirección del campo en un punto, ya con las transformaciones -x/-y/-z y el rotacional si se pide. */
    static final class Field {
        private final NoiseSpec.NoiseField noise;
        private final TubeFormula.Runner modX;
        private final TubeFormula.Runner modY;
        private final TubeFormula.Runner modZ;
        private final boolean curl;

        Field(Settings settings) {
            noise = settings.noise().build(settings.seed());
            modX = settings.modX() == null ? null : settings.modX().runner(settings.seed(), 1);
            modY = settings.modY() == null ? null : settings.modY().runner(settings.seed(), 1);
            modZ = settings.modZ() == null ? null : settings.modZ().runner(settings.seed(), 1);
            curl = settings.curl();
        }

        private double sample(double x, double y, double z, int channel) {
            double mx = modX == null ? x : modX.evaluate(x, y, z, 0.0);
            double my = modY == null ? y : modY.evaluate(x, y, z, 0.0);
            double mz = modZ == null ? z : modZ.evaluate(x, y, z, 0.0);
            double shift = channel * CHANNEL_OFFSET;
            return noise.sample(mx + shift, my - shift, mz + shift * 0.5);
        }

        /** Escribe en {@code out} una dirección unitaria (o cero en un punto muerto). */
        void direction(double x, double y, double z, boolean threeD, double[] out) {
            if (!threeD) {
                if (curl) {
                    double h = 0.5;
                    double dPsiDx = (sample(x + h, y, z, 0) - sample(x - h, y, z, 0)) / (2 * h);
                    double dPsiDz = (sample(x, y, z + h, 0) - sample(x, y, z - h, 0)) / (2 * h);
                    out[0] = dPsiDz;
                    out[1] = 0.0;
                    out[2] = -dPsiDx;
                } else {
                    // Dos vueltas completas en el rango del ruido: con una sola el flujo casi no gira.
                    double angle = sample(x, y, z, 0) * Math.PI * 4.0;
                    out[0] = Math.cos(angle);
                    out[1] = 0.0;
                    out[2] = Math.sin(angle);
                }
            } else if (curl) {
                double h = 0.5;
                // Potencial vectorial (A, B, C); el flujo es su rotacional.
                double dCdy = (sample(x, y + h, z, 2) - sample(x, y - h, z, 2)) / (2 * h);
                double dBdz = (sample(x, y, z + h, 1) - sample(x, y, z - h, 1)) / (2 * h);
                double dAdz = (sample(x, y, z + h, 0) - sample(x, y, z - h, 0)) / (2 * h);
                double dCdx = (sample(x + h, y, z, 2) - sample(x - h, y, z, 2)) / (2 * h);
                double dBdx = (sample(x + h, y, z, 1) - sample(x - h, y, z, 1)) / (2 * h);
                double dAdy = (sample(x, y + h, z, 0) - sample(x, y - h, z, 0)) / (2 * h);
                out[0] = dCdy - dBdz;
                out[1] = dAdz - dCdx;
                out[2] = dBdx - dAdy;
            } else {
                out[0] = sample(x, y, z, 0) * 2.0 - 1.0;
                out[1] = sample(x, y, z, 1) * 2.0 - 1.0;
                out[2] = sample(x, y, z, 2) * 2.0 - 1.0;
            }
            double length = Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
            if (length > EPSILON) {
                out[0] /= length;
                out[1] /= length;
                out[2] /= length;
            } else {
                out[0] = 0;
                out[1] = 0;
                out[2] = 0;
            }
        }
    }
}
