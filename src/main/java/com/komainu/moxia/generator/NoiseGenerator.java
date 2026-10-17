package com.komainu.moxia.generator;

import com.komainu.moxia.noise.NoiseSpec;

/**
 * {@code /komaedit noisegen}: terreno a partir de ruido dentro de la selección. Solo añade bloques (no borra lo que
 * haya encima de la superficie generada) y la paleta se reparte por alturas, de la primera entrada abajo a la
 * última arriba.
 *
 * <ul>
 *     <li><b>heightmap</b>: un valor de ruido 2D por columna da su altura.</li>
 *     <li><b>terrain</b>: ruido 3D sobre un degradado vertical, con la fuerza del ruido variable con la altura;
 *     salen voladizos y cuevas.</li>
 *     <li><b>advanced</b>: coloca donde el ruido 3D cae entre dos umbrales (números o fórmulas); con {@code -h} los
 *     umbrales recortan un mapa de alturas 2D.</li>
 * </ul>
 */
public final class NoiseGenerator {
    private NoiseGenerator() {
    }

    public enum Mode {
        HEIGHTMAP("heightmap", "alturas"),
        TERRAIN("terrain", "terreno"),
        ADVANCED("advanced", "avanzado");

        private final String id;
        private final String alias;

        Mode(String id, String alias) {
            this.id = id;
            this.alias = alias;
        }

        public String id() {
            return id;
        }

        public String alias() {
            return alias;
        }
    }

    /**
     * Ajustes de una generación.
     *
     * @param height     altura del terreno en bloques; 0 = la de la selección
     * @param strength   fuerza del ruido por alturas (1 a 3 valores, de abajo arriba), solo en terrain
     * @param zoom       las coordenadas se dividen entre el zoom antes de muestrear
     * @param offset     desplazamiento de las coordenadas del ruido (x, y, z)
     * @param centred    {@code -c}: coordenadas relativas al centro de la selección en vez de las del mundo
     * @param smooth     {@code -t}: nieve en capas y líquidos planos
     * @param force3d    {@code -y}: el mapa de alturas muestrea ruido 3D a la altura del desplazamiento
     * @param smear      {@code -l}: estira el ruido 3D en vertical
     * @param heightmap  {@code -h}: modo mapa de alturas en advanced
     * @param normalized {@code -n}: coordenadas de -1 a 1 centradas en la selección
     */
    public record Settings(
            Mode mode,
            NoiseSpec noise,
            int height,
            double[] strength,
            Threshold lower,
            Threshold upper,
            double zoom,
            long seed,
            double[] offset,
            boolean centred,
            boolean smooth,
            boolean force3d,
            double smear,
            boolean heightmap,
            boolean normalized
    ) {
    }

    /** Altura del bloque más alto que cumple la máscara en una columna; {@link #NONE} si no hay. */
    @FunctionalInterface
    public interface Surface {
        int NONE = Integer.MIN_VALUE;

        int top(int x, int z);
    }

    /**
     * @param surface base de cada columna cuando hay máscara ({@code -m}); {@code null} usa el suelo de la
     *                selección
     * @param worldMinimum altura mínima del mundo (no se escribe por debajo)
     * @param worldMaximum altura máxima del mundo (no se escribe por encima)
     */
    public static void generate(
            Settings settings,
            Volume volume,
            Surface surface,
            PaletteTraits palette,
            int worldMinimum,
            int worldMaximum,
            GeneratedBlocks out
    ) {
        Sampler sampler = new Sampler(settings, volume);
        int height = settings.height() > 0 ? settings.height() : volume.sizeY();
        switch (settings.mode()) {
            case HEIGHTMAP -> heightmap(settings, volume, surface, palette, sampler, height, false,
                    worldMinimum, worldMaximum, out);
            case TERRAIN -> terrain(settings, volume, surface, palette, sampler, height, worldMinimum,
                    worldMaximum, out);
            case ADVANCED -> {
                if (settings.heightmap()) {
                    heightmap(settings, volume, surface, palette, sampler, height, true, worldMinimum,
                            worldMaximum, out);
                } else {
                    advanced(settings, volume, surface, palette, sampler, out);
                }
            }
        }
    }

    private static void heightmap(
            Settings settings,
            Volume volume,
            Surface surface,
            PaletteTraits palette,
            Sampler sampler,
            int height,
            boolean thresholds,
            int worldMinimum,
            int worldMaximum,
            GeneratedBlocks out
    ) {
        Threshold.Evaluator lower = settings.lower().evaluator(settings.seed());
        Threshold.Evaluator upper = settings.upper().evaluator(settings.seed());
        for (int z = volume.minimumZ(); z <= volume.maximumZ(); z++) {
            for (int x = volume.minimumX(); x <= volume.maximumX(); x++) {
                out.checkCancelled();
                if (!volume.containsColumn(x, z)) {
                    continue;
                }
                int base = volume.minimumY();
                if (surface != null) {
                    int top = surface.top(x, z);
                    if (top == Surface.NONE) {
                        continue;
                    }
                    base = top + 1;
                }
                double value = settings.force3d()
                        ? sampler.sample(x, volume.minimumY(), z)
                        : sampler.sample2d(x, z);
                if (thresholds) {
                    double nx = sampler.normalizedX(x);
                    double nz = sampler.normalizedZ(z);
                    double low = lower.evaluate(nx, 0.0, nz, 0.0);
                    double high = upper.evaluate(nx, 0.0, nz, 0.0);
                    value = high - low < 1.0E-9 ? (value >= low ? 1.0 : 0.0)
                            : Math.clamp((value - low) / (high - low), 0.0, 1.0);
                }
                column(x, z, base, value * height, height, settings.smooth(), palette, volume, surface != null,
                        worldMinimum, worldMaximum, out);
            }
        }
    }

    /**
     * Rellena una columna de {@code columnHeight} bloques (con decimales) desde {@code base}.
     *
     * @param clipToSelection con máscara la columna no pasa del techo de la selección
     */
    static void column(
            int x,
            int z,
            int base,
            double columnHeight,
            int height,
            boolean smooth,
            PaletteTraits palette,
            Volume volume,
            boolean clipToSelection,
            int worldMinimum,
            int worldMaximum,
            GeneratedBlocks out
    ) {
        int ceiling = Math.min(clipToSelection ? volume.maximumY() : worldMaximum, worldMaximum);
        if (!smooth) {
            int full = (int) Math.round(columnHeight);
            for (int step = 0; step < full && base + step <= ceiling; step++) {
                if (base + step >= worldMinimum) {
                    out.add(x, base + step, z, layer(step, height, palette));
                }
            }
            return;
        }
        int solid = (int) Math.floor(columnHeight);
        double fraction = columnHeight - solid;
        int topIndex = layer(Math.max(0, solid - 1), height, palette);
        int lakeTop = solid;
        if (palette.liquid()[topIndex]) {
            // Lagos planos: si la superficie cae en una franja líquida, el líquido llega hasta el techo de la franja.
            int last = topIndex;
            while (last + 1 < palette.size() && palette.liquid()[last + 1]) {
                last++;
            }
            lakeTop = Math.max(solid, (int) Math.ceil((last + 1) * (double) height / palette.size()));
            fraction = 0.0;
        }
        for (int step = 0; step < lakeTop; step++) {
            int y = base + step;
            if (y > ceiling) {
                return;
            }
            if (y < worldMinimum) {
                continue;
            }
            int index = layer(step, height, palette);
            if (step >= solid) {
                out.add(x, y, z, index);
            } else if (palette.snow()[index] && step == solid - 1 && lakeTop == solid) {
                out.add(x, y, z, index, 8);
            } else {
                out.add(x, y, z, buried(index, palette));
            }
        }
        int y = base + lakeTop;
        int index = layer(lakeTop, height, palette);
        if (fraction > 0.0 && palette.snow()[index] && y <= ceiling && y >= worldMinimum) {
            int layers = (int) Math.round(fraction * 8);
            if (layers > 0) {
                out.add(x, y, z, index, Math.min(8, layers));
            }
        }
    }

    /** Entrada de la paleta para el escalón {@code step} de una columna de {@code height}. */
    static int layer(int step, int height, PaletteTraits palette) {
        return palette.index((step + 0.5) / height);
    }

    /**
     * En modo suave, lo que queda enterrado bajo la superficie no es nieve suelta ni líquido: se cambia por la
     * entrada sólida más cercana (primero hacia arriba, luego hacia abajo).
     */
    static int buried(int index, PaletteTraits palette) {
        if (!palette.liquid()[index] && !palette.snow()[index]) {
            return index;
        }
        for (int distance = 1; distance < palette.size(); distance++) {
            int above = index + distance;
            if (above < palette.size() && !palette.liquid()[above] && !palette.snow()[above]) {
                return above;
            }
            int below = index - distance;
            if (below >= 0 && !palette.liquid()[below] && !palette.snow()[below]) {
                return below;
            }
        }
        return index;
    }

    private static void terrain(
            Settings settings,
            Volume volume,
            Surface surface,
            PaletteTraits palette,
            Sampler sampler,
            int height,
            int worldMinimum,
            int worldMaximum,
            GeneratedBlocks out
    ) {
        double[] strength = settings.strength();
        for (int z = volume.minimumZ(); z <= volume.maximumZ(); z++) {
            for (int x = volume.minimumX(); x <= volume.maximumX(); x++) {
                if (!volume.containsColumn(x, z)) {
                    continue;
                }
                int base = volume.minimumY();
                int ceiling = worldMaximum;
                if (surface != null) {
                    int top = surface.top(x, z);
                    if (top == Surface.NONE) {
                        continue;
                    }
                    base = top + 1;
                    ceiling = volume.maximumY();
                }
                for (int step = 0; step < height; step++) {
                    out.checkCancelled();
                    int y = base + step;
                    if (y > ceiling) {
                        break;
                    }
                    if (y < worldMinimum) {
                        continue;
                    }
                    double t = (step + 0.5) / height;
                    if (density(sampler.sample(x, y, z), t, strength(strength, t)) > 0.5) {
                        out.add(x, y, z, palette.index(t));
                    }
                }
            }
        }
    }

    /**
     * Densidad del modo terrain: un degradado que va de macizo abajo a vacío arriba, más el ruido centrado en
     * cero con su fuerza. Con fuerza 0 sale un llano a media altura; con fuerza 1 el ruido puede abrir cuevas
     * abajo y dejar islas arriba.
     */
    static double density(double noise, double t, double strength) {
        return 1.0 - t + strength * (2.0 * noise - 1.0);
    }

    /** Interpola linealmente la fuerza entre los valores dados, repartidos de abajo (t = 0) arriba (t = 1). */
    static double strength(double[] values, double t) {
        if (values.length == 1) {
            return values[0];
        }
        double position = Math.clamp(t, 0.0, 1.0) * (values.length - 1);
        int index = Math.min(values.length - 2, (int) Math.floor(position));
        double local = position - index;
        return values[index] + (values[index + 1] - values[index]) * local;
    }

    private static void advanced(
            Settings settings,
            Volume volume,
            Surface surface,
            PaletteTraits palette,
            Sampler sampler,
            GeneratedBlocks out
    ) {
        Threshold.Evaluator lower = settings.lower().evaluator(settings.seed());
        Threshold.Evaluator upper = settings.upper().evaluator(settings.seed());
        double sizeY = Math.max(1, volume.sizeY());
        for (int z = volume.minimumZ(); z <= volume.maximumZ(); z++) {
            for (int x = volume.minimumX(); x <= volume.maximumX(); x++) {
                int floor = Integer.MIN_VALUE;
                if (surface != null) {
                    if (!volume.containsColumn(x, z)) {
                        continue;
                    }
                    int top = surface.top(x, z);
                    if (top == Surface.NONE) {
                        continue;
                    }
                    floor = top;
                }
                double nx = sampler.normalizedX(x);
                double nz = sampler.normalizedZ(z);
                for (int y = volume.minimumY(); y <= volume.maximumY(); y++) {
                    out.checkCancelled();
                    if (y <= floor || !volume.contains(x, y, z)) {
                        continue;
                    }
                    double ny = sampler.normalizedY(y);
                    double t = (y - volume.minimumY() + 0.5) / sizeY;
                    double low = lower.evaluate(nx, ny, nz, t);
                    double high = upper.evaluate(nx, ny, nz, t);
                    double value = sampler.sample(x, y, z);
                    if (value < low || value > high) {
                        continue;
                    }
                    double fraction = high - low < 1.0E-9 ? 0.0 : (value - low) / (high - low);
                    out.add(x, y, z, palette.index(fraction));
                }
            }
        }
    }

    /** Traduce posiciones del mundo a coordenadas de ruido según zoom, desplazamiento, centrado y estirado. */
    static final class Sampler {
        private final NoiseSpec.NoiseField field;
        private final double zoom;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private final double smear;
        private final boolean centred;
        private final boolean normalized;
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final double halfX;
        private final double halfY;
        private final double halfZ;

        Sampler(Settings settings, Volume volume) {
            field = settings.noise().build(settings.seed());
            zoom = settings.zoom();
            offsetX = settings.offset()[0];
            offsetY = settings.offset()[1];
            offsetZ = settings.offset()[2];
            smear = 1.0 + Math.max(0.0, settings.smear());
            centred = settings.centred();
            normalized = settings.normalized();
            centerX = volume.centerX();
            centerY = volume.centerY();
            centerZ = volume.centerZ();
            halfX = Math.max(0.5, volume.sizeX() * 0.5);
            halfY = Math.max(0.5, volume.sizeY() * 0.5);
            halfZ = Math.max(0.5, volume.sizeZ() * 0.5);
        }

        double normalizedX(int x) {
            return (x + 0.5 - centerX) / halfX;
        }

        double normalizedY(int y) {
            return (y + 0.5 - centerY) / halfY;
        }

        double normalizedZ(int z) {
            return (z + 0.5 - centerZ) / halfZ;
        }

        private double coordinate(int value, double center, double half, boolean axisCentred) {
            if (normalized) {
                return (value + 0.5 - center) / half;
            }
            return axisCentred ? value + 0.5 - center : value;
        }

        double sample(int x, int y, int z) {
            double nx = (coordinate(x, centerX, halfX, centred) + offsetX) / zoom;
            double ny = (coordinate(y, centerY, halfY, centred) + offsetY) / zoom / smear;
            double nz = (coordinate(z, centerZ, halfZ, centred) + offsetZ) / zoom;
            return field.sample(nx, ny, nz);
        }

        /** Mapa de alturas: el desplazamiento vertical no cuenta (por eso existe {@code -y}). */
        double sample2d(int x, int z) {
            double nx = (coordinate(x, centerX, halfX, centred) + offsetX) / zoom;
            double nz = (coordinate(z, centerZ, halfZ, centred) + offsetZ) / zoom;
            return field.sample(nx, 0.0, nz);
        }
    }
}
