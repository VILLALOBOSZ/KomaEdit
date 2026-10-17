package com.komainu.moxia.weathering;

import com.komainu.moxia.weathering.AgingTable.Aging;
import com.komainu.moxia.weathering.AgingTable.Kind;
import com.komainu.moxia.weathering.AgingTable.Material;
import com.komainu.moxia.weathering.AgingTable.Rubble;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Envejecer: convierte una construcción en una ruina creíble. Java puro (los bloques son texto) para poder probarlo
 * sin servidor. El orden imita lo que haría el tiempo:
 *
 * <ol>
 *     <li><b>Desgaste por exposición.</b> Cada bloque mide cuántas caras y cuántos vecinos (de 26) tiene al aire:
 *     aristas y esquinas pierden antes que una cara lisa, y lo alto antes que lo bajo. Un ruido suave por semilla
 *     agrupa el daño en mordiscos en vez de agujeros sueltos. Se hace en varias pasadas, así que el daño entra
 *     desde los bordes hacia dentro. El vidrio y la lana se van antes que la piedra; la pizarra profunda aguanta
 *     más.</li>
 *     <li><b>Derrumbe.</b> Se mide, capa a capa desde el suelo, cuántos bloques en horizontal separan cada bloque de
 *     una columna que baje hasta el suelo (como el andamio de Minecraft). Si el vano supera lo que aguanta su
 *     material, cae; lo que queda suelto del todo cae siempre.</li>
 *     <li><b>Escombro.</b> Lo que cae (y parte de lo desgastado) baja en vertical, se desliza si el montón queda
 *     muy empinado y forma montones con su propio material; arriba del montón quedan losas y escaleras.</li>
 *     <li><b>Variantes.</b> Musgo abajo, al norte y a la sombra; grietas en lo expuesto; óxido en el cobre;
 *     corteza que se pierde; tablones podridos ({@link AgingTable}).</li>
 *     <li><b>Vida.</b> Musgo, hierba y helechos encima, enredaderas en los muros al sol, telarañas en rincones
 *     oscuros y charcos opcionales; nieve, arena o algas según el clima.</li>
 * </ol>
 *
 * <p>Solo cambia bloques dentro de la selección; el borde exterior se lee para saber qué sostiene la obra.</p>
 */
public final class WeatheringEngine {
    private static final int INF = Integer.MAX_VALUE / 4;
    private static final int[][] SIDES = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
    private static final String[] SIDE_NAMES = {"north", "east", "south", "west"};
    private static final int MAX_PUDDLE = 40;
    private static final int[][] NEIGHBOURS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
    private static final float[] WEATHER_BY_STEP = {1.0f, 0.6f, 0.3f};
    private static final float SHELTERED = 0.1f;

    private WeatheringEngine() {
    }

    /** Lee el estado de un bloque (texto) en coordenadas del mundo. */
    @FunctionalInterface
    public interface BlockSource {
        String state(int x, int y, int z);
    }

    /** Forma de la selección dentro de su caja (una selección cúbica la contiene entera). */
    @FunctionalInterface
    public interface Selection {
        boolean contains(int x, int y, int z);

        static Selection all() {
            return (x, y, z) -> true;
        }
    }

    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            if (maxX < minX || maxY < minY || maxZ < minZ) {
                throw new IllegalArgumentException("La caja está al revés");
            }
        }

        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeY() {
            return maxY - minY + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }

        public long volume() {
            return (long) sizeX() * sizeY() * sizeZ();
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    public record Change(int x, int y, int z, String before, String after) {
    }

    /** Cambios en orden de abajo arriba y recuento de lo que ha hecho cada fase. */
    public record Result(List<Change> changes, int eroded, int fallen, int rubble, int aged, int life) {
        public static Result empty() {
            return new Result(List.of(), 0, 0, 0, 0, 0);
        }
    }

    public static Result weather(BlockSource source, Box box, Selection selection, WeatheringSettings settings) {
        if (settings.intensity() == 0) {
            return Result.empty();
        }
        return new Run(source, box, selection, settings).run();
    }

    private static final class Run {
        private final Box box;
        private final WeatheringSettings settings;
        private final WeatherStyle style;
        private final long seed;
        private final double intensity;
        private final double collapse;
        private final double vegetation;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int nx;
        private final int ny;
        private final int nz;
        private final int[] cells;
        private final int[] original;
        private final boolean[] editable;
        private final boolean[] debris;
        private final Map<Integer, Rubble> debrisFamily = new HashMap<>();
        private final List<BlockText> palette = new ArrayList<>();
        private final List<Kind> kinds = new ArrayList<>();
        private final List<Material> materials = new ArrayList<>();
        private final Map<String, Integer> paletteIndex = new HashMap<>();
        private final List<Fall> falls = new ArrayList<>();
        private int[] skyTop;
        private float[] weather;
        private int buildingMinY = Integer.MAX_VALUE;
        private int buildingMaxY = Integer.MIN_VALUE;
        private int eroded;
        private int fallen;
        private int rubble;
        private int aged;
        private int life;
        private final int air;
        private final int water;

        Run(BlockSource source, Box box, Selection selection, WeatheringSettings settings) {
            this.box = box;
            this.settings = settings;
            this.style = settings.style();
            this.seed = settings.seedOrZero();
            this.intensity = settings.intensity() / 100.0;
            this.collapse = settings.collapse() / 100.0;
            this.vegetation = settings.vegetation() / 100.0 * (0.4 + 0.6 * intensity);
            this.originX = box.minX() - 1;
            this.originY = box.minY() - 1;
            this.originZ = box.minZ() - 1;
            this.nx = box.sizeX() + 2;
            this.ny = box.sizeY() + 2;
            this.nz = box.sizeZ() + 2;
            int volume = Math.multiplyExact(Math.multiplyExact(nx, ny), nz);
            cells = new int[volume];
            editable = new boolean[volume];
            debris = new boolean[volume];
            air = id(BlockText.AIR);
            water = id(BlockText.of("water", "level", "0"));
            Map<String, Integer> cache = new HashMap<>();
            for (int ly = 0; ly < ny; ly++) {
                for (int lz = 0; lz < nz; lz++) {
                    for (int lx = 0; lx < nx; lx++) {
                        int x = originX + lx;
                        int y = originY + ly;
                        int z = originZ + lz;
                        String text = source.state(x, y, z);
                        Integer known = cache.get(text);
                        if (known == null) {
                            known = id(BlockText.parse(text == null ? "minecraft:air" : text));
                            cache.put(text, known);
                        }
                        int index = index(lx, ly, lz);
                        cells[index] = known;
                        editable[index] = box.contains(x, y, z) && selection.contains(x, y, z)
                                && kinds.get(known) != Kind.PROTECTED;
                        if (editable[index] && kinds.get(known) == Kind.BUILDING) {
                            buildingMinY = Math.min(buildingMinY, y);
                            buildingMaxY = Math.max(buildingMaxY, y);
                        }
                    }
                }
            }
            original = cells.clone();
        }

        Result run() {
            if (buildingMinY > buildingMaxY) {
                return Result.empty();
            }
            erode();
            if (collapse > 0.0) {
                collapse();
            }
            dropLooseDecor();
            settleRubble();
            shapeRubble();
            if (style == WeatherStyle.SUNKEN) {
                flood();
            }
            age();
            computeSky();
            if (settings.water() && style != WeatherStyle.SUNKEN) {
                puddles();
            }
            grow();
            return collect();
        }

        // ---------------------------------------------------------------- desgaste

        private void erode() {
            int passes = 1 + (int) Math.round(intensity * 3.0);
            double threshold = 1.02 - 0.6 * intensity;
            for (int pass = 0; pass < passes; pass++) {
                computeSky();
                computeWeather();
                List<Integer> removed = new ArrayList<>();
                for (int ly = 1; ly < ny - 1; ly++) {
                    for (int lz = 1; lz < nz - 1; lz++) {
                        for (int lx = 1; lx < nx - 1; lx++) {
                            int index = index(lx, ly, lz);
                            if (!editable[index] || kind(index) != Kind.BUILDING) {
                                continue;
                            }
                            double exposure = exposure(lx, ly, lz);
                            if (exposure <= 0.0) {
                                continue;
                            }
                            int x = originX + lx;
                            int y = originY + ly;
                            int z = originZ + lz;
                            double weight = 0.7 + 0.6 * height(y);
                            double cluster = noise(x, y, z, 4.5, 11);
                            double score = exposure * weight * materials.get(cells[index]).fragility()
                                    * (0.45 + 1.1 * cluster) + 0.12 * (random(x, y, z, 12 + pass) - 0.5);
                            if (score > threshold) {
                                removed.add(index);
                            }
                        }
                    }
                }
                if (removed.isEmpty()) {
                    break;
                }
                for (int index : removed) {
                    BlockText block = palette.get(cells[index]);
                    Material material = materials.get(cells[index]);
                    cells[index] = air;
                    eroded++;
                    int x = x(index);
                    int y = y(index);
                    int z = z(index);
                    if (random(x, y, z, 21) < (1.0 - material.shatter()) * 0.5) {
                        falls.add(new Fall(x, y, z, AgingTable.rubble(block, style)));
                    }
                }
            }
        }

        // ---------------------------------------------------------------- derrumbe

        private void collapse() {
            int baseSpan = (int) Math.round(7.0 - 5.0 * collapse);
            int[] distance = new int[cells.length];
            Arrays.fill(distance, INF);
            for (int ly = 0; ly < ny; ly++) {
                for (int lz = 0; lz < nz; lz++) {
                    for (int lx = 0; lx < nx; lx++) {
                        int index = index(lx, ly, lz);
                        if (!solid(index)) {
                            continue;
                        }
                        if (anchor(index) || ly == 0) {
                            distance[index] = 0;
                        } else {
                            distance[index] = restingDistance(distance, lx, ly, lz);
                        }
                    }
                }
                relaxLayer(distance, ly);
                if (ly == 0 || ly == ny - 1) {
                    continue;
                }
                for (int lz = 1; lz < nz - 1; lz++) {
                    for (int lx = 1; lx < nx - 1; lx++) {
                        int index = index(lx, ly, lz);
                        if (!editable[index] || kind(index) != Kind.BUILDING) {
                            continue;
                        }
                        Material material = materials.get(cells[index]);
                        int limit = Math.max(1, baseSpan + material.spanBonus());
                        if (distance[index] > limit) {
                            BlockText block = palette.get(cells[index]);
                            cells[index] = air;
                            distance[index] = INF;
                            fallen++;
                            int x = originX + lx;
                            int y = originY + ly;
                            int z = originZ + lz;
                            if (random(x, y, z, 31) >= material.shatter()) {
                                falls.add(new Fall(x, y, z, AgingTable.rubble(block, style)));
                            }
                        }
                    }
                }
            }
        }

        /**
         * Apoyo desde la capa de abajo: en vertical sin coste y en diagonal (arista con arista) con coste uno, que es
         * como descansan los tejados de escaleras y los escalonados, cada fila un bloque más adentro.
         */
        private int restingDistance(int[] distance, int lx, int ly, int lz) {
            int best = distance[index(lx, ly - 1, lz)];
            for (int[] side : SIDES) {
                int sx = lx + side[0];
                int sz = lz + side[2];
                if (sx < 0 || sz < 0 || sx >= nx || sz >= nz) {
                    continue;
                }
                int diagonal = distance[index(sx, ly - 1, sz)];
                if (diagonal < INF) {
                    best = Math.min(best, diagonal + 1);
                }
            }
            return best;
        }

        /** Un bloque que no se puede tocar (terreno, cofres, lo de fuera de la selección) sostiene lo que lleva. */
        private boolean anchor(int index) {
            Kind kind = kind(index);
            return !editable[index] || kind == Kind.NATURAL || kind == Kind.PROTECTED;
        }

        /** Distancia mínima en horizontal a un apoyo, propagada por la capa con pasos de una unidad. */
        private void relaxLayer(int[] distance, int ly) {
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            int maximum = 0;
            for (int lz = 0; lz < nz; lz++) {
                for (int lx = 0; lx < nx; lx++) {
                    int index = index(lx, ly, lz);
                    if (distance[index] < INF) {
                        maximum = Math.max(maximum, distance[index]);
                    }
                }
            }
            // Cola por cubos: se siembran los valores de menor a mayor para que el primer valor que llega sea el bueno.
            List<List<Integer>> buckets = new ArrayList<>();
            for (int value = 0; value <= maximum; value++) {
                buckets.add(new ArrayList<>());
            }
            for (int lz = 0; lz < nz; lz++) {
                for (int lx = 0; lx < nx; lx++) {
                    int index = index(lx, ly, lz);
                    if (distance[index] < INF) {
                        buckets.get(distance[index]).add(index);
                    }
                }
            }
            for (int value = 0; value < buckets.size() || !queue.isEmpty(); value++) {
                if (value < buckets.size()) {
                    for (int index : buckets.get(value)) {
                        if (distance[index] == value) {
                            queue.add(index);
                        }
                    }
                }
                int layerSize = queue.size();
                for (int step = 0; step < layerSize; step++) {
                    int index = queue.poll();
                    int lx = index % nx;
                    int lz = (index / nx) % nz;
                    for (int[] side : SIDES) {
                        int sx = lx + side[0];
                        int sz = lz + side[2];
                        if (sx < 0 || sz < 0 || sx >= nx || sz >= nz) {
                            continue;
                        }
                        int neighbour = index(sx, ly, sz);
                        if (solid(neighbour) && distance[neighbour] > distance[index] + 1) {
                            distance[neighbour] = distance[index] + 1;
                            queue.add(neighbour);
                        }
                    }
                }
            }
        }

        /** Lo pequeño (antorchas, alfombras, flores) cae con lo que lo sostenía; lo que daba luz o abrigo se pierde. */
        private void dropLooseDecor() {
            for (int index = 0; index < cells.length; index++) {
                if (!editable[index] || kind(index) != Kind.DECOR) {
                    continue;
                }
                int lx = index % nx;
                int lz = (index / nx) % nz;
                int ly = index / (nx * nz);
                String path = palette.get(cells[index]).path();
                boolean abandoned = path.contains("torch") || path.contains("lantern") || path.contains("candle")
                        || path.contains("carpet") && !path.equals("moss_carpet") || path.contains("banner");
                boolean held = false;
                for (int[] offset : new int[][]{{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}}) {
                    int sx = lx + offset[0];
                    int sy = ly + offset[1];
                    int sz = lz + offset[2];
                    if (inside(sx, sy, sz) && solid(index(sx, sy, sz))) {
                        held = true;
                        break;
                    }
                }
                if (!held || abandoned && random(x(index), y(index), z(index), 35) < intensity * 0.85) {
                    cells[index] = air;
                    eroded++;
                }
            }
        }

        // ---------------------------------------------------------------- escombro

        private void settleRubble() {
            for (Fall fall : falls) {
                int x = fall.x();
                int z = fall.z();
                int y = fall.y();
                if (random(x, y, z, 41) < 0.6) {
                    int dx = (int) Math.floor(random(x, y, z, 42) * 3.0) - 1;
                    int dz = (int) Math.floor(random(x, y, z, 43) * 3.0) - 1;
                    if (passable(x + dx, y, z + dz)) {
                        x += dx;
                        z += dz;
                    }
                }
                if (!passable(x, y, z)) {
                    continue;
                }
                y = land(x, y, z);
                // Ángulo de reposo: si al lado hay un hueco dos bloques más bajo, el trozo resbala hacia allí.
                for (int slide = 0; slide < 4; slide++) {
                    int start = (int) Math.floor(random(x, y, z, 44 + slide) * 4.0);
                    boolean moved = false;
                    for (int turn = 0; turn < 4 && !moved; turn++) {
                        int[] side = SIDES[(start + turn) % 4];
                        int sx = x + side[0];
                        int sz = z + side[2];
                        if (!passable(sx, y, sz)) {
                            continue;
                        }
                        int landing = land(sx, y, sz);
                        if (landing <= y - 2) {
                            x = sx;
                            z = sz;
                            y = landing;
                            moved = true;
                        }
                    }
                    if (!moved) {
                        break;
                    }
                }
                int index = worldIndex(x, y, z);
                cells[index] = id(BlockText.of(rubbleBlock(fall.rubble(), x, y, z)));
                debris[index] = true;
                debrisFamily.put(index, fall.rubble());
                rubble++;
            }
        }

        /** Primer hueco con apoyo debajo, sin bajar del suelo de la selección. */
        private int land(int x, int y, int z) {
            int current = y;
            while (current - 1 >= box.minY() && passable(x, current - 1, z)) {
                current--;
            }
            return current;
        }

        private String rubbleBlock(Rubble family, int x, int y, int z) {
            double roll = random(x, y, z, 51);
            if (AgingTable.isWood(family.full())) {
                return family.full();
            }
            return switch (style) {
                case DESERT -> roll < 0.4 ? "sand" : roll < 0.55 ? "terracotta" : family.full();
                case SNOWY -> roll < 0.15 ? "gravel" : roll < 0.3 ? "snow_block" : family.full();
                case SUNKEN -> roll < 0.3 ? "gravel" : roll < 0.5 ? "sand" : mossy(family.full(), roll > 0.8);
                case TEMPERATE -> roll < 0.25 ? "gravel" : roll < 0.33 ? "andesite"
                        : mossy(family.full(), roll > 0.8);
            };
        }

        private static String mossy(String full, boolean moss) {
            return moss && full.equals("cobblestone") ? "mossy_cobblestone" : full;
        }

        /** La cara de arriba del montón: losas sueltas y escaleras apoyadas contra lo que tienen al lado. */
        private void shapeRubble() {
            for (int index = 0; index < cells.length; index++) {
                if (!debris[index]) {
                    continue;
                }
                int lx = index % nx;
                int lz = (index / nx) % nz;
                int ly = index / (nx * nz);
                if (ly + 1 < ny && solid(index(lx, ly + 1, lz))) {
                    continue;
                }
                Rubble family = debrisFamily.get(index);
                String path = palette.get(cells[index]).path();
                String slab;
                String stairs;
                if (path.equals(family.full())) {
                    slab = family.slab();
                    stairs = family.stairs();
                } else if (path.equals("mossy_cobblestone")) {
                    slab = "mossy_cobblestone_slab";
                    stairs = "mossy_cobblestone_stairs";
                } else {
                    continue;
                }
                int x = x(index);
                int y = y(index);
                int z = z(index);
                int start = (int) Math.floor(random(x, y, z, 61) * 4.0);
                int facing = -1;
                for (int turn = 0; turn < 4; turn++) {
                    int side = (start + turn) % 4;
                    int sx = lx + SIDES[side][0];
                    int sz = lz + SIDES[side][2];
                    if (solid(index(sx, ly, sz))) {
                        facing = side;
                        break;
                    }
                }
                double roll = random(x, y, z, 62);
                boolean wood = AgingTable.isWood(path);
                if (facing >= 0 && roll < 0.7 && !wood) {
                    cells[index] = id(BlockText.of(stairs, "facing", SIDE_NAMES[facing], "half", "bottom",
                            "shape", "straight", "waterlogged", "false"));
                } else if (roll < (wood ? 0.85 : 0.6) || facing < 0) {
                    cells[index] = id(BlockText.of(slab, "type", "bottom", "waterlogged", "false"));
                }
            }
        }

        private void flood() {
            for (int index = 0; index < cells.length; index++) {
                if (!editable[index]) {
                    continue;
                }
                Kind kind = kind(index);
                if (kind == Kind.AIR) {
                    cells[index] = water;
                } else if (kind == Kind.BUILDING || kind == Kind.DECOR) {
                    BlockText block = palette.get(cells[index]);
                    BlockText wet = block.withExisting("waterlogged", "true");
                    if (wet != block) {
                        cells[index] = id(wet);
                    }
                }
            }
        }

        // ---------------------------------------------------------------- variantes

        private void age() {
            computeSky();
            computeWeather();
            for (int ly = 1; ly < ny - 1; ly++) {
                for (int lz = 1; lz < nz - 1; lz++) {
                    for (int lx = 1; lx < nx - 1; lx++) {
                        int index = index(lx, ly, lz);
                        if (!editable[index] || debris[index] || kind(index) != Kind.BUILDING) {
                            continue;
                        }
                        int faces = openFaces(lx, ly, lz);
                        if (faces == 0) {
                            continue;
                        }
                        BlockText block = palette.get(cells[index]);
                        BlockText next = agedVariant(block, lx, ly, lz);
                        if (next != null && !next.equals(block)) {
                            cells[index] = id(next);
                            aged++;
                        }
                    }
                }
            }
        }

        private BlockText agedVariant(BlockText block, int lx, int ly, int lz) {
            int x = originX + lx;
            int y = originY + ly;
            int z = originZ + lz;
            double h = height(y);
            double exposure = exposure(lx, ly, lz);
            boolean north = !solid(index(lx, ly, lz - 1));
            boolean shade = !openToSky(lx, ly, lz);
            double patch = noise(x, y, z, 3.5, 71);

            int steps = (int) Math.floor(intensity * 3.2 + random(x, y, z, 72) * 0.8);
            if (AgingTable.variant(block, Aging.OXIDIZE, style) != null && steps > 0) {
                BlockText current = block;
                for (int step = 0; step < steps; step++) {
                    BlockText next = AgingTable.variant(current, Aging.OXIDIZE, style);
                    if (next == null) {
                        break;
                    }
                    current = next;
                }
                return current;
            }

            double moss = style.moss() * (0.35 * (1.0 - h) + 0.25 * (north ? 1 : 0) + 0.2 * (shade ? 1 : 0)
                    + (h < 0.25 ? 0.2 : 0.0));
            if (random(x, y, z, 73) < intensity * 1.3 * moss * patch * patch) {
                BlockText mossy = AgingTable.variant(block, Aging.MOSS, style);
                if (mossy != null) {
                    return mossy;
                }
            }
            double frost = style == WeatherStyle.SNOWY ? 1.4 : 1.0;
            if (random(x, y, z, 74) < intensity * 0.7 * frost * (0.3 + exposure) * (0.6 + 0.6 * h)) {
                BlockText cracked = AgingTable.variant(block, Aging.CRACK, style);
                if (cracked != null) {
                    return cracked;
                }
            }
            if (random(x, y, z, 75) < intensity * 0.6 * (0.4 + exposure)) {
                BlockText worn = AgingTable.variant(block, Aging.WEAR, style);
                if (worn != null) {
                    return worn;
                }
            }
            if (random(x, y, z, 76) < intensity * 0.3 * (0.5 + exposure)) {
                BlockText rotten = AgingTable.variant(block, Aging.ROT, style);
                if (rotten != null) {
                    return rotten;
                }
            }
            double sun = style == WeatherStyle.DESERT ? 0.9 : 0.5;
            if (random(x, y, z, 77) < intensity * sun) {
                return AgingTable.variant(block, Aging.STRIP, style);
            }
            return null;
        }

        // ---------------------------------------------------------------- vida

        private void puddles() {
            boolean[] seen = new boolean[cells.length];
            for (int ly = 1; ly < ny - 1; ly++) {
                for (int lz = 1; lz < nz - 1; lz++) {
                    for (int lx = 1; lx < nx - 1; lx++) {
                        int start = index(lx, ly, lz);
                        if (seen[start] || !floorCell(lx, ly, lz)) {
                            continue;
                        }
                        List<Integer> component = new ArrayList<>();
                        ArrayDeque<Integer> queue = new ArrayDeque<>();
                        queue.add(start);
                        seen[start] = true;
                        boolean closed = true;
                        while (!queue.isEmpty()) {
                            int index = queue.poll();
                            component.add(index);
                            int cx = index % nx;
                            int cz = (index / nx) % nz;
                            for (int[] side : SIDES) {
                                int sx = cx + side[0];
                                int sz = cz + side[2];
                                int neighbour = index(sx, ly, sz);
                                if (solid(neighbour)) {
                                    continue;
                                }
                                if (!floorCell(sx, ly, sz)) {
                                    closed = false;
                                    continue;
                                }
                                if (!seen[neighbour]) {
                                    seen[neighbour] = true;
                                    queue.add(neighbour);
                                }
                            }
                        }
                        if (!closed || component.size() > MAX_PUDDLE) {
                            continue;
                        }
                        int x = originX + lx;
                        int y = originY + ly;
                        int z = originZ + lz;
                        if (random(x, y, z, 81) > 0.75) {
                            continue;
                        }
                        int fill = style == WeatherStyle.SNOWY ? id(BlockText.of("ice")) : water;
                        for (int index : component) {
                            cells[index] = fill;
                            life++;
                        }
                    }
                }
            }
        }

        /** Hueco de la selección con suelo entero debajo: donde se puede quedar el agua. */
        private boolean floorCell(int lx, int ly, int lz) {
            if (lx < 1 || lz < 1 || lx >= nx - 1 || lz >= nz - 1) {
                return false;
            }
            int index = index(lx, ly, lz);
            if (!editable[index] || kind(index) != Kind.AIR) {
                return false;
            }
            int below = index(lx, ly - 1, lz);
            return solid(below) && AgingTable.fullTop(palette.get(cells[below]));
        }

        private void grow() {
            for (int ly = 1; ly < ny - 1; ly++) {
                for (int lz = 1; lz < nz - 1; lz++) {
                    for (int lx = 1; lx < nx - 1; lx++) {
                        int index = index(lx, ly, lz);
                        if (!editable[index]) {
                            continue;
                        }
                        int open = cells[index];
                        boolean emptyCell = open == air || style == WeatherStyle.SUNKEN && open == water;
                        if (!emptyCell) {
                            continue;
                        }
                        switch (style) {
                            case TEMPERATE -> growTemperate(lx, ly, lz, index);
                            case DESERT -> growDesert(lx, ly, lz, index);
                            case SNOWY -> growSnowy(lx, ly, lz, index);
                            case SUNKEN -> growSunken(lx, ly, lz, index);
                        }
                    }
                }
            }
        }

        private void growTemperate(int lx, int ly, int lz, int index) {
            int x = originX + lx;
            int y = originY + ly;
            int z = originZ + lz;
            int below = index(lx, ly - 1, lz);
            boolean sky = openToSky(lx, ly, lz);
            double patch = noise(x, y, z, 5.0, 91);
            if (solid(below) && AgingTable.fullTop(palette.get(cells[below]))) {
                BlockText ground = palette.get(cells[below]);
                double roll = random(x, y, z, 92);
                if (AgingTable.soil(ground)) {
                    if (roll < vegetation * 0.7 * (sky ? 1.0 : 0.4) * (0.4 + patch)) {
                        place(index, random(x, y, z, 93) < 0.7 ? "short_grass" : "fern");
                    }
                    return;
                }
                if (editable[below] && kind(below) == Kind.BUILDING && !debris[below] && sky
                        && roll < vegetation * 0.18 * patch && full(ground)) {
                    cells[below] = id(BlockText.of("moss_block"));
                    life++;
                    if (random(x, y, z, 94) < 0.5) {
                        place(index, random(x, y, z, 95) < 0.75 ? "short_grass" : "fern");
                    }
                    return;
                }
                if (roll < vegetation * 0.55 * (sky ? 1.0 : 0.5) * (0.3 + patch)) {
                    place(index, "moss_carpet");
                    return;
                }
            }
            if (cobweb(lx, ly, lz, index, 0.3)) {
                return;
            }
            int above = index(lx, ly + 1, lz);
            if (!sky && solid(above) && !solid(below) && random(x, y, z, 96) < vegetation * 0.05) {
                place(index, "hanging_roots");
                return;
            }
            vine(lx, ly, lz, index);
        }

        private void growDesert(int lx, int ly, int lz, int index) {
            int x = originX + lx;
            int y = originY + ly;
            int z = originZ + lz;
            int below = index(lx, ly - 1, lz);
            if (solid(below) && AgingTable.fullTop(palette.get(cells[below]))) {
                int walls = solidSides(lx, ly, lz);
                double roll = random(x, y, z, 101);
                double low = 1.0 - height(y);
                if (walls >= 1 && roll < vegetation * (walls >= 2 ? 0.9 : 0.45) * (0.3 + 0.7 * low)) {
                    place(index, "sand");
                    return;
                }
                String ground = palette.get(cells[below]).path();
                boolean sandy = ground.contains("sand") || ground.contains("terracotta") || ground.contains("dirt");
                if (sandy && roll > 1.0 - vegetation * 0.12) {
                    place(index, "dead_bush");
                    return;
                }
            }
            cobweb(lx, ly, lz, index, 0.15);
        }

        private void growSnowy(int lx, int ly, int lz, int index) {
            int x = originX + lx;
            int y = originY + ly;
            int z = originZ + lz;
            int below = index(lx, ly - 1, lz);
            if (solid(below) && AgingTable.fullTop(palette.get(cells[below])) && openToSky(lx, ly, lz)) {
                double roll = random(x, y, z, 111);
                if (roll < 0.35 + 0.6 * vegetation) {
                    int layers = 1 + (int) Math.floor(random(x, y, z, 112) * 2.0) + Math.min(2, solidSides(lx, ly, lz));
                    place(index, BlockText.of("snow", "layers", Integer.toString(Math.min(8, layers))));
                }
                return;
            }
            cobweb(lx, ly, lz, index, 0.2);
        }

        private void growSunken(int lx, int ly, int lz, int index) {
            int x = originX + lx;
            int y = originY + ly;
            int z = originZ + lz;
            int below = index(lx, ly - 1, lz);
            if (!solid(below) || !AgingTable.fullTop(palette.get(cells[below]))) {
                return;
            }
            double roll = random(x, y, z, 121);
            if (roll >= vegetation * 0.6) {
                return;
            }
            double kind = random(x, y, z, 122);
            if (kind < 0.62) {
                place(index, "seagrass");
            } else if (kind < 0.75) {
                int pickles = 1 + (int) Math.floor(random(x, y, z, 123) * 3.0);
                place(index, BlockText.of("sea_pickle", "pickles", Integer.toString(pickles), "waterlogged", "true"));
            } else {
                int height = 2 + (int) Math.floor(random(x, y, z, 124) * 5.0);
                int top = ly;
                while (top - ly < height - 1 && top + 1 < ny - 1) {
                    int next = index(lx, top + 1, lz);
                    if (!editable[next] || cells[next] != water) {
                        break;
                    }
                    top++;
                }
                for (int level = ly; level <= top; level++) {
                    int cell = index(lx, level, lz);
                    if (level == top) {
                        int age = (int) Math.floor(random(x, y, z, 125) * 25.0);
                        place(cell, BlockText.of("kelp", "age", Integer.toString(age)));
                    } else {
                        place(cell, BlockText.of("kelp_plant"));
                    }
                }
            }
        }

        /** Telaraña en un rincón oscuro: techo encima y al menos dos paredes. */
        private boolean cobweb(int lx, int ly, int lz, int index, double rate) {
            if (openToSky(lx, ly, lz) || !solid(index(lx, ly + 1, lz)) || solidSides(lx, ly, lz) < 2) {
                return false;
            }
            if (random(originX + lx, originY + ly, originZ + lz, 131) < vegetation * rate) {
                place(index, "cobweb");
                return true;
            }
            return false;
        }

        /** Enredadera que cuelga desde lo alto de un muro al sol, más en las caras norte. */
        private void vine(int lx, int ly, int lz, int index) {
            if (!openToSky(lx, ly, lz)) {
                return;
            }
            int x = originX + lx;
            int y = originY + ly;
            int z = originZ + lz;
            for (int side = 0; side < 4; side++) {
                int sx = lx + SIDES[side][0];
                int sz = lz + SIDES[side][2];
                int wall = index(sx, ly, sz);
                if (kind(wall) != Kind.BUILDING || debris[wall]) {
                    continue;
                }
                int wallAbove = index(sx, ly + 1, sz);
                if (solid(wallAbove) && ly + 1 < ny - 1) {
                    continue;
                }
                double bias = side == 2 ? 1.6 : 1.0;
                if (random(x, y, z, 141 + side) >= vegetation * 0.3 * bias * noise(x, y, z, 4.0, 145)) {
                    continue;
                }
                // La enredadera está en el lado sur de su bloque si el muro queda al sur.
                String facing = SIDE_NAMES[side];
                int length = 2 + (int) Math.floor(random(x, y, z, 146) * 5.0);
                for (int level = ly; level > 0 && ly - level < length; level--) {
                    int cell = index(lx, level, lz);
                    if (!editable[cell] || cells[cell] != air || kind(index(sx, level, sz)) != Kind.BUILDING) {
                        break;
                    }
                    place(cell, BlockText.of("vine", "east", Boolean.toString(facing.equals("east")),
                            "north", Boolean.toString(facing.equals("north")),
                            "south", Boolean.toString(facing.equals("south")),
                            "up", "false",
                            "west", Boolean.toString(facing.equals("west"))));
                }
                return;
            }
        }

        private void place(int index, String path) {
            place(index, BlockText.of(path));
        }

        private void place(int index, BlockText block) {
            cells[index] = id(block);
            life++;
        }

        // ---------------------------------------------------------------- salida

        private Result collect() {
            List<Change> changes = new ArrayList<>();
            for (int index = 0; index < cells.length; index++) {
                if (editable[index] && cells[index] != original[index]) {
                    changes.add(new Change(
                            x(index),
                            y(index),
                            z(index),
                            palette.get(original[index]).toString(),
                            palette.get(cells[index]).toString()
                    ));
                }
            }
            return new Result(List.copyOf(changes), eroded, fallen, rubble, aged, life);
        }

        // ---------------------------------------------------------------- utilidades

        private void computeSky() {
            skyTop = new int[nx * nz];
            for (int lz = 0; lz < nz; lz++) {
                for (int lx = 0; lx < nx; lx++) {
                    int top = 0;
                    for (int ly = ny - 1; ly >= 0; ly--) {
                        if (solid(index(lx, ly, lz))) {
                            top = ly + 1;
                            break;
                        }
                    }
                    skyTop[lz * nx + lx] = top;
                }
            }
        }

        /** Nada sólido encima hasta el borde de la captura (fuera se supone cielo abierto). */
        private boolean openToSky(int lx, int ly, int lz) {
            return ly >= skyTop[lz * nx + lx];
        }

        /**
         * Intemperie de cada hueco: 1 bajo el cielo abierto y menos cuanto más hay que andar por el aire para llegar
         * a él. El aire de dentro de un tejado o de una habitación cerrada apenas desgasta; el de una ventana o un
         * alero, algo.
         */
        private void computeWeather() {
            weather = new float[cells.length];
            int[] steps = new int[cells.length];
            Arrays.fill(steps, -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            for (int index = 0; index < cells.length; index++) {
                int lx = index % nx;
                int lz = (index / nx) % nz;
                int ly = index / (nx * nz);
                if (!solid(index) && openToSky(lx, ly, lz)) {
                    steps[index] = 0;
                    queue.add(index);
                }
            }
            while (!queue.isEmpty()) {
                int index = queue.poll();
                weather[index] = WEATHER_BY_STEP[steps[index]];
                if (steps[index] + 1 >= WEATHER_BY_STEP.length) {
                    continue;
                }
                int lx = index % nx;
                int lz = (index / nx) % nz;
                int ly = index / (nx * nz);
                for (int[] offset : NEIGHBOURS) {
                    int sx = lx + offset[0];
                    int sy = ly + offset[1];
                    int sz = lz + offset[2];
                    if (!inside(sx, sy, sz)) {
                        continue;
                    }
                    int neighbour = index(sx, sy, sz);
                    if (steps[neighbour] < 0 && !solid(neighbour)) {
                        steps[neighbour] = steps[index] + 1;
                        queue.add(neighbour);
                    }
                }
            }
            for (int index = 0; index < cells.length; index++) {
                if (steps[index] < 0 && !solid(index)) {
                    weather[index] = SHELTERED;
                }
            }
        }

        /** Exposición del bloque: caras al aire (pesa más) y vecinos de 26 al aire, cada uno por su intemperie. */
        private double exposure(int lx, int ly, int lz) {
            double faces = 0.0;
            for (int[] offset : NEIGHBOURS) {
                faces += weather[index(lx + offset[0], ly + offset[1], lz + offset[2])];
            }
            if (faces <= 0.0) {
                return 0.0;
            }
            double around = 0.0;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if ((dx | dy | dz) != 0) {
                            around += weather[index(lx + dx, ly + dy, lz + dz)];
                        }
                    }
                }
            }
            return 0.55 * faces / 6.0 + 0.45 * around / 26.0;
        }

        private int openFaces(int lx, int ly, int lz) {
            int open = 0;
            open += solid(index(lx - 1, ly, lz)) ? 0 : 1;
            open += solid(index(lx + 1, ly, lz)) ? 0 : 1;
            open += solid(index(lx, ly - 1, lz)) ? 0 : 1;
            open += solid(index(lx, ly + 1, lz)) ? 0 : 1;
            open += solid(index(lx, ly, lz - 1)) ? 0 : 1;
            open += solid(index(lx, ly, lz + 1)) ? 0 : 1;
            return open;
        }

        private int solidSides(int lx, int ly, int lz) {
            int count = 0;
            for (int[] side : SIDES) {
                if (solid(index(lx + side[0], ly, lz + side[2]))) {
                    count++;
                }
            }
            return count;
        }

        private boolean full(BlockText block) {
            return block.properties().isEmpty() || block.path().endsWith("_log") || block.path().endsWith("_wood");
        }

        private double height(int y) {
            return Math.clamp((y - buildingMinY) / (double) Math.max(1, buildingMaxY - buildingMinY), 0.0, 1.0);
        }

        private boolean passable(int x, int y, int z) {
            if (!box.contains(x, y, z)) {
                return false;
            }
            int index = worldIndex(x, y, z);
            if (!editable[index]) {
                return false;
            }
            Kind kind = kind(index);
            return kind == Kind.AIR || kind == Kind.DECOR
                    || kind == Kind.FLUID && palette.get(cells[index]).path().equals("water");
        }

        private boolean solid(int index) {
            Kind kind = kind(index);
            return kind == Kind.BUILDING || kind == Kind.NATURAL || kind == Kind.PROTECTED;
        }

        private Kind kind(int index) {
            return kinds.get(cells[index]);
        }

        private boolean inside(int lx, int ly, int lz) {
            return lx >= 0 && ly >= 0 && lz >= 0 && lx < nx && ly < ny && lz < nz;
        }

        private int index(int lx, int ly, int lz) {
            return (ly * nz + lz) * nx + lx;
        }

        private int worldIndex(int x, int y, int z) {
            return index(x - originX, y - originY, z - originZ);
        }

        private int x(int index) {
            return originX + index % nx;
        }

        private int y(int index) {
            return originY + index / (nx * nz);
        }

        private int z(int index) {
            return originZ + (index / nx) % nz;
        }

        private int id(BlockText block) {
            String key = block.toString();
            Integer known = paletteIndex.get(key);
            if (known != null) {
                return known;
            }
            int created = palette.size();
            palette.add(block);
            kinds.add(AgingTable.kind(block));
            materials.add(AgingTable.material(block));
            paletteIndex.put(key, created);
            return created;
        }

        private double random(int x, int y, int z, int salt) {
            return WeatheringEngine.random(seed, x, y, z, salt);
        }

        private double noise(int x, int y, int z, double scale, int salt) {
            return WeatheringEngine.noise(seed, x / scale, y / scale, z / scale, salt);
        }
    }

    private record Fall(int x, int y, int z, Rubble rubble) {
    }

    /** Número fijo en [0, 1) para cada posición, semilla y uso. */
    static double random(long seed, int x, int y, int z, int salt) {
        long hash = seed * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        hash = mix(hash ^ x * 0x165667B19E3779F9L);
        hash = mix(hash ^ y * 0x27D4EB2F165667C5L);
        hash = mix(hash ^ z * 0x94D049BB133111EBL);
        return (hash >>> 11) * 0x1.0p-53;
    }

    /** Ruido de valor suave en [0, 1): manchas de unos {@code scale} bloques. */
    static double noise(long seed, double x, double y, double z, int salt) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fy = smooth(y - y0);
        double fz = smooth(z - z0);
        double result = 0.0;
        for (int corner = 0; corner < 8; corner++) {
            int dx = corner & 1;
            int dy = (corner >> 1) & 1;
            int dz = (corner >> 2) & 1;
            double weight = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
            result += weight * random(seed, x0 + dx, y0 + dy, z0 + dz, salt);
        }
        return result;
    }

    private static double smooth(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
