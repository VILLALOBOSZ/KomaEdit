package com.komainu.moxia.utility;

import com.komainu.moxia.region.RegionSnapshot;

/**
 * Nieve en capas sobre la selección, sin Minecraft.
 *
 * <ol>
 *     <li>En cada columna se busca la superficie que ve el cielo (el primer bloque desde arriba). La capa de nieve
 *     va en el hueco de encima, que tiene que estar en la selección y ser aire (o una planta pequeña con
 *     {@code replacePlants}).</li>
 *     <li>Solo donde la nieve puede estar: sobre bloques con la cara de arriba entera, nunca sobre hielo, barreras,
 *     losas de abajo, vallas, cristales finos, agua…</li>
 *     <li>Sigue la pendiente: la altura del terreno se suaviza con un radio y cada columna recibe las capas que le
 *     faltan para llegar a esa superficie suave, así los escalones de un bloque se convierten en rampas y la nieve se
 *     acumula en los huecos; en las aristas y cimas queda más fina.</li>
 *     <li>Más gruesa a la sombra (la parte del cielo que tapan los bloques de alrededor) y, si se pide, con la
 *     altura dentro de la selección.</li>
 *     <li>Nunca quita nieve: una capa existente solo crece. Debajo, la hierba, el micelio y el podsol pasan a
 *     {@code snowy=true}.</li>
 * </ol>
 */
public final class SnowEngine {
    private static final int NONE = Integer.MIN_VALUE;
    /** Direcciones de los rayos de sombra: vertical y dos anillos de 8 a 35° y 60° de elevación. */
    private static final double[][] RAYS = rays();
    private static final int RAY_LENGTH = 12;
    private static final int MAX_STEP = 2;

    private SnowEngine() {
    }

    /**
     * @param layers       capas de base (1–8)
     * @param radius       radio del suavizado de la pendiente, en bloques (0 la desactiva)
     * @param fill         cuánto se rellenan los huecos hasta la superficie suave (0–1)
     * @param shade        capas extra a la sombra total (0–8)
     * @param altitude     capas extra en lo más alto de la selección (0–8)
     * @param replacePlants si las plantas pequeñas se tapan
     */
    public record Settings(int layers, int radius, double fill, int shade, int altitude, boolean replacePlants) {
        public Settings {
            if (layers < 1 || layers > 8) {
                throw new IllegalArgumentException("Las capas van de 1 a 8");
            }
            if (radius < 0 || radius > 16) {
                throw new IllegalArgumentException("El radio de la pendiente va de 0 a 16");
            }
            if (!(fill >= 0.0 && fill <= 1.0)) {
                throw new IllegalArgumentException("El relleno de los huecos va de 0 a 1");
            }
            if (shade < 0 || shade > 8 || altitude < 0 || altitude > 8) {
                throw new IllegalArgumentException("Las capas extra de sombra y altura van de 0 a 8");
            }
        }

        /** Bloques de margen que hay que leer alrededor de la selección. */
        public int padding() {
            return Math.max(radius + 1, shade > 0 ? 6 : 1);
        }
    }

    /** Destino: una capa de nieve con {@code layers} capas, o {@code snowy=true} en el bloque de debajo. */
    public interface Sink {
        void snow(int x, int y, int z, int layers);

        void snowy(int x, int y, int z);
    }

    /** Filtro sobre el bloque que sostiene la nieve (la máscara); {@code null} acepta todos. */
    @FunctionalInterface
    public interface Ground {
        boolean test(int x, int y, int z);
    }

    public record Result(int placed, int thickened, int skipped) {
    }

    public static Result apply(RegionSnapshot snapshot, BlockKinds kinds, Settings settings, Ground ground, Sink sink,
                               Runnable checkCancelled) {
        int minX = snapshot.minimumX();
        int minZ = snapshot.minimumZ();
        int sizeX = snapshot.maximumX() - minX + 1;
        int sizeZ = snapshot.maximumZ() - minZ + 1;
        int[] surface = new int[sizeX * sizeZ];
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                checkCancelled.run();
                int top = surface(snapshot, kinds, minX + x, minZ + z, settings.replacePlants());
                surface[z * sizeX + x] = top;
                if (top != NONE && snapshot.selected(minX + x, top + 1, minZ + z)) {
                    lowest = Math.min(lowest, top);
                    highest = Math.max(highest, top);
                }
            }
        }
        double[] smooth = smooth(surface, sizeX, sizeZ, settings.radius());
        int placed = 0;
        int thickened = 0;
        int skipped = 0;
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                checkCancelled.run();
                int top = surface[z * sizeX + x];
                int worldX = minX + x;
                int worldZ = minZ + z;
                if (top == NONE || !snapshot.selected(worldX, top + 1, worldZ)) {
                    continue;
                }
                int below = snapshot.rawId(worldX, top, worldZ);
                if (!kinds.supportsSnow(below) || ground != null && !ground.test(worldX, top, worldZ)) {
                    skipped++;
                    continue;
                }
                int cell = snapshot.rawId(worldX, top + 1, worldZ);
                int existing = kinds.snowLayers(cell);
                double extra = settings.fill() * 8.0 * (smooth[z * sizeX + x] - (top + 1));
                if (settings.shade() > 0) {
                    extra += settings.shade() * shade(snapshot, kinds, worldX, top + 1, worldZ);
                }
                if (settings.altitude() > 0 && highest > lowest) {
                    extra += settings.altitude() * (top - lowest) / (double) (highest - lowest);
                }
                int layers = Math.clamp(Math.round(settings.layers() + extra), 1, 8);
                if (layers <= existing) {
                    continue;
                }
                sink.snow(worldX, top + 1, worldZ, layers);
                if (existing > 0) {
                    thickened++;
                } else {
                    placed++;
                }
                if (kinds.snowy(below) && snapshot.selected(worldX, top, worldZ)) {
                    sink.snowy(worldX, top, worldZ);
                }
            }
        }
        return new Result(placed, thickened, skipped);
    }

    /**
     * Bloque más alto de la columna que no sea aire (ni planta pequeña tapable, ni nieve en capas: entonces cuenta el
     * de debajo); {@link #NONE} si la columna está vacía o el hueco de encima no es aire.
     */
    static int surface(RegionSnapshot snapshot, BlockKinds kinds, int x, int z, boolean replacePlants) {
        for (int y = snapshot.maximumY(); y >= snapshot.minimumY(); y--) {
            RegionSnapshot.StateInfo info = snapshot.info(x, y, z);
            if (info.air() || replacePlants && kinds.smallPlant(info.rawId())) {
                continue;
            }
            if (kinds.snowLayers(info.rawId()) > 0 && kinds.snowLayers(info.rawId()) < 8) {
                return y - 1 >= snapshot.minimumY() ? y - 1 : NONE;
            }
            return y == snapshot.maximumY() ? NONE : y;
        }
        return NONE;
    }

    /**
     * Media ponderada de la altura de la superficie ({@code y + 1}) de las columnas cercanas que tienen superficie;
     * la diferencia con cada vecino se limita a {@value #MAX_STEP} bloques.
     */
    static double[] smooth(int[] surface, int sizeX, int sizeZ, int radius) {
        double[] result = new double[surface.length];
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int top = surface[z * sizeX + x];
                if (top == NONE) {
                    continue;
                }
                double sum = 0.0;
                double weights = 0.0;
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx < 0 || nz < 0 || nx >= sizeX || nz >= sizeZ || dx * dx + dz * dz > radius * radius) {
                            continue;
                        }
                        int neighbour = surface[nz * sizeX + nx];
                        if (neighbour == NONE) {
                            continue;
                        }
                        // Un árbol o un acantilado cerca no deben enterrar la columna: cada vecino cuenta como
                        // mucho dos bloques por encima o por debajo.
                        double weight = 1.0 / (1.0 + dx * dx + dz * dz);
                        sum += weight * (top + 1 + Math.clamp(neighbour - top, -MAX_STEP, MAX_STEP));
                        weights += weight;
                    }
                }
                result[z * sizeX + x] = weights == 0.0 ? top + 1 : sum / weights;
            }
        }
        return result;
    }

    /** Parte del cielo tapada desde el hueco (0 a cielo abierto, 1 bajo techo). */
    static double shade(RegionSnapshot snapshot, BlockKinds kinds, int x, int y, int z) {
        int blocked = 0;
        for (double[] ray : RAYS) {
            for (int step = 1; step <= RAY_LENGTH; step++) {
                int px = (int) Math.floor(x + 0.5 + ray[0] * step);
                int py = (int) Math.floor(y + 0.5 + ray[1] * step);
                int pz = (int) Math.floor(z + 0.5 + ray[2] * step);
                if (!snapshot.contains(px, py, pz)) {
                    break;
                }
                RegionSnapshot.StateInfo info = snapshot.info(px, py, pz);
                if (!info.air() && !kinds.fluid(info.rawId()) && !kinds.smallPlant(info.rawId())
                        && kinds.snowLayers(info.rawId()) == 0) {
                    blocked++;
                    break;
                }
            }
        }
        return blocked / (double) RAYS.length;
    }

    private static double[][] rays() {
        double[][] rays = new double[17][];
        rays[0] = new double[]{0.0, 1.0, 0.0};
        int index = 1;
        for (double elevation : new double[]{Math.toRadians(35.0), Math.toRadians(60.0)}) {
            for (int step = 0; step < 8; step++) {
                double azimuth = Math.PI * 2.0 * step / 8.0 + (elevation > 1.0 ? Math.PI / 8.0 : 0.0);
                rays[index++] = new double[]{Math.cos(elevation) * Math.cos(azimuth), Math.sin(elevation),
                        Math.cos(elevation) * Math.sin(azimuth)};
            }
        }
        return rays;
    }
}
