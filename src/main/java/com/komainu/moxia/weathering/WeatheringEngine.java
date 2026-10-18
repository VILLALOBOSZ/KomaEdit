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
 * sin servidor. Trabaja por elementos, en el orden en que trabaja el tiempo:
 *
 * <ol>
 *     <li><b>Lectura de la obra.</b> Cada columna de bloques se parte en tramos verticales: lo que tiene tres o más
 *     de alto (contando el vidrio de las ventanas) es <em>muro</em>; lo bajo que apoya en el terreno es
 *     <em>base</em>; lo demás (tejados, forjados, vigas, dinteles sueltos) es <em>pieza horizontal</em>. El vidrio
 *     y los paneles son <em>relleno</em>.</li>
 *     <li><b>Perfil de los muros.</b> Cada columna de muro recibe una altura arruinada: alta en esquinas, pilares y
 *     muros gruesos, baja en el centro de los lienzos, en los extremos sueltos y junto a puertas y ventanas, con un
 *     ruido suave a lo largo del muro. El borde roto no baja más deprisa de lo que admite el material (talud) y
 *     queda dentado. Todo lo que hay encima del perfil cae.</li>
 *     <li><b>Tejados, forjados y vidrio.</b> Las piezas horizontales se hunden a manchas grandes, primero en el
 *     centro de la luz (lejos de un muro que siga en pie) y antes la madera que la piedra. Del vidrio quedan restos
 *     de panel, sobre todo abajo de cada ventana.</li>
 *     <li><b>Derrumbe.</b> Lo que se ha quedado sin unión con el suelo cae siempre. Con derrumbe se mide además,
 *     capa a capa, la distancia en horizontal a una columna que baje hasta algo firme, y cae lo que vuela más de lo
 *     que aguanta su material.</li>
 *     <li><b>Escombro.</b> Lo de los muros cae a un lado y a otro de su pie, dentro y fuera; lo de los tejados y
 *     forjados, debajo. Cada trozo resbala mientras el montón quede empinado, así que se acumula en los rincones y
 *     contra los muros; arriba del montón quedan losas y escaleras.</li>
 *     <li><b>Variantes.</b> Musgo, grietas, desgaste, podredumbre, corteza y óxido salen en manchas (ruido de baja
 *     frecuencia) que siguen gradientes con sentido: musgo desde el suelo, al norte, a la sombra y bajo ventanas y
 *     cornisas; grietas cerca de lo roto; óxido según la intemperie ({@link AgingTable}).</li>
 *     <li><b>Vida.</b> Más verde donde hay brecha y escombro al cielo, poco bajo techo; hiedra que cuelga desde lo
 *     alto y que sube desde el suelo por las caras húmedas; nieve, arena o algas según el clima.</li>
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

    /** Papel de cada bloque en la obra. */
    private static final byte NONE = 0;
    private static final byte WALL = 1;
    private static final byte BASE = 2;
    private static final byte SPAN = 3;
    private static final byte INFILL = 4;

    /** Radio en columnas con el que un muro mira si es esquina, pilar o lienzo suelto. */
    private static final int REACH = 3;
    /** Hasta dónde llega el «aquí se ha roto algo» que atrae grietas y vida. */
    private static final int DAMAGE_REACH = 4;
    /** Bloques por encima del muro que caen con él (lo que descansaba encima: alero, cumbrera). */
    private static final int RESTING = 2;

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

    /**
     * Cambios en orden de abajo arriba y recuento de lo que ha hecho cada fase: {@code eroded} lo que se ha perdido
     * por el perfil de los muros, el hundimiento de tejados y forjados, el vidrio y la decoración; {@code fallen} lo
     * que ha caído por quedarse sin apoyo.
     */
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
        private byte[] role;
        private boolean[] groundedBefore;
        private int[] wallTop;
        private int[] wallBase;
        private int[] keep;
        private float[] openness;
        private Material[] wallMaterial;
        private float[] damage;
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
            groundedBefore = grounded();
            classify();
            ruinWalls();
            decaySpans();
            dropUnsupported();
            if (collapse > 0.0) {
                collapse();
            }
            dropLooseDecor();
            settleRubble();
            shapeRubble();
            if (style == WeatherStyle.SUNKEN) {
                flood();
            }
            computeDamage();
            age();
            computeSky();
            computeWeather();
            if (settings.water() && style != WeatherStyle.SUNKEN) {
                puddles();
            }
            grow();
            return collect();
        }

        // ---------------------------------------------------------------- lectura de la obra

        /**
         * Parte cada columna en tramos verticales de material de obra (el vidrio cuenta: una ventana no corta el
         * muro). Tres o más de alto es muro; lo más bajo, base si apoya en el terreno y pieza horizontal si no.
         */
        private void classify() {
            role = new byte[cells.length];
            int columns = nx * nz;
            wallTop = new int[columns];
            wallBase = new int[columns];
            openness = new float[columns];
            wallMaterial = new Material[columns];
            Arrays.fill(wallTop, -1);
            Arrays.fill(wallBase, -1);
            int[] counts = new int[Material.values().length];
            for (int lz = 0; lz < nz; lz++) {
                for (int lx = 0; lx < nx; lx++) {
                    int column = lz * nx + lx;
                    int ly = 0;
                    while (ly < ny) {
                        if (kind(index(lx, ly, lz)) != Kind.BUILDING) {
                            ly++;
                            continue;
                        }
                        int start = ly;
                        while (ly < ny && kind(index(lx, ly, lz)) == Kind.BUILDING) {
                            ly++;
                        }
                        int length = ly - start;
                        boolean grounded = start == 0 || holds(index(lx, start - 1, lz));
                        for (int level = start; level < ly; level++) {
                            int index = index(lx, level, lz);
                            if (infill(index)) {
                                role[index] = INFILL;
                                continue;
                            }
                            byte part = length >= 3 ? WALL : grounded ? BASE : SPAN;
                            role[index] = part;
                            if (part == WALL) {
                                if (wallBase[column] < 0) {
                                    wallBase[column] = level;
                                }
                                wallTop[column] = level;
                            }
                        }
                    }
                    if (wallTop[column] < 0) {
                        continue;
                    }
                    // Huecos de la columna desde lo alto del muro hasta el terreno: puertas, ventanas, aire.
                    Arrays.fill(counts, 0);
                    int open = 0;
                    int height = 0;
                    for (int level = wallTop[column]; level >= 1; level--) {
                        int index = index(lx, level, lz);
                        Kind kind = kind(index);
                        if (kind == Kind.NATURAL || kind == Kind.PROTECTED && !door(index)) {
                            break;
                        }
                        height++;
                        if (role[index] == WALL) {
                            counts[materials.get(cells[index]).ordinal()]++;
                        } else if (!structural(index)) {
                            open++;
                        }
                    }
                    openness[column] = height == 0 ? 0.0f : open / (float) height;
                    int best = 0;
                    for (int material = 1; material < counts.length; material++) {
                        if (counts[material] > counts[best]) {
                            best = material;
                        }
                    }
                    wallMaterial[column] = Material.values()[best];
                }
            }
        }

        // ---------------------------------------------------------------- perfil de los muros

        /**
         * Altura arruinada de cada columna de muro. La debilidad sube en el centro de los lienzos, en los extremos
         * sueltos y junto a los huecos, y baja en esquinas, pilares y muros gruesos: la esquina se reconoce porque el
         * muro de alrededor se extiende en dos direcciones (la dispersión de las columnas vecinas no es una línea).
         */
        private void ruinWalls() {
            int columns = nx * nz;
            keep = new int[columns];
            Arrays.fill(keep, -1);
            int[] loss = new int[columns];
            double ruin = Math.pow(intensity, 1.3);
            double sigma2 = 2.0 * 1.8 * 1.8;
            double openSigma2 = 2.0 * 1.4 * 1.4;
            double lineMass = 1.0;
            for (int d = 1; d <= REACH; d++) {
                lineMass += 2.0 * Math.exp(-d * d / sigma2);
            }
            for (int lz = 0; lz < nz; lz++) {
                for (int lx = 0; lx < nx; lx++) {
                    int column = lz * nx + lx;
                    if (wallTop[column] < 0) {
                        continue;
                    }
                    double mass = 0.0;
                    double sx = 0.0;
                    double sz = 0.0;
                    double sxx = 0.0;
                    double szz = 0.0;
                    double sxz = 0.0;
                    double open = 0.0;
                    double near = 0.0;
                    int close = 0;
                    for (int dz = -REACH; dz <= REACH; dz++) {
                        for (int dx = -REACH; dx <= REACH; dx++) {
                            int ox = lx + dx;
                            int oz = lz + dz;
                            int d2 = dx * dx + dz * dz;
                            if (ox < 0 || oz < 0 || ox >= nx || oz >= nz || d2 > REACH * REACH + 1) {
                                continue;
                            }
                            int other = oz * nx + ox;
                            if (wallTop[other] < 0) {
                                continue;
                            }
                            double weight = Math.exp(-d2 / sigma2);
                            mass += weight;
                            sx += weight * dx;
                            sz += weight * dz;
                            sxx += weight * dx * dx;
                            szz += weight * dz * dz;
                            sxz += weight * dx * dz;
                            double openWeight = Math.exp(-d2 / openSigma2);
                            open += openWeight * openness[other];
                            near += openWeight;
                            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                                close++;
                            }
                        }
                    }
                    double mx = sx / mass;
                    double mz = sz / mass;
                    double cxx = sxx / mass - mx * mx;
                    double czz = szz / mass - mz * mz;
                    double cxz = sxz / mass - mx * mz;
                    double half = (cxx + czz) / 2.0;
                    double root = Math.sqrt((cxx - czz) * (cxx - czz) / 4.0 + cxz * cxz);
                    double major = half + root;
                    double minor = half - root;
                    double brace = major <= 1e-9 ? 0.0 : Math.clamp(minor / major / 0.25, 0.0, 1.0);
                    double thick = Math.clamp((close - 3) / 5.0, 0.0, 1.0);
                    double loose = Math.clamp(1.0 - mass / lineMass, 0.0, 1.0) * 1.6;
                    double gaps = Math.clamp(open / near * 2.2, 0.0, 1.0);
                    int x = originX + lx;
                    int z = originZ + lz;
                    double wobble = noise(x, 0, z, 4.5, 201) - 0.5;
                    // Una esquina aguanta aunque tenga una ventana cerca o le toque una mala racha del ruido.
                    double weakness = 1.0 - 0.8 * brace - 0.4 * thick + 0.5 * loose
                            + (0.9 * gaps + 0.9 * wobble) * (1.0 - 0.6 * brace)
                            + wallMaterial[column].wallWeakness();
                    int height = wallTop[column] - wallBase[column] + 1;
                    double lost = Math.clamp(ruin * (0.05 + 0.6 * Math.max(0.0, weakness)), 0.0, 0.9);
                    loss[column] = (int) Math.round(lost * height);
                }
            }
            relaxLoss(loss);
            for (int lz = 0; lz < nz; lz++) {
                for (int lx = 0; lx < nx; lx++) {
                    int column = lz * nx + lx;
                    if (wallTop[column] < 0) {
                        continue;
                    }
                    int height = wallTop[column] - wallBase[column] + 1;
                    int value = loss[column];
                    // Borde dentado: una de cada pocas columnas pierde un bloque más.
                    if ((value > 0 || intensity > 0.35)
                            && random(originX + lx, 0, originZ + lz, 203) < 0.45) {
                        value++;
                    }
                    value = Math.min(value, height - 1);
                    keep[column] = wallTop[column] - value;
                    if (value > 0) {
                        cutColumn(lx, lz, column);
                    }
                }
            }
        }

        /**
         * Talud del borde roto: una columna no puede perder menos que su vecina menos lo que admite su material
         * (en diagonal, un bloque más), así las brechas salen en V y no en escalones verticales.
         */
        private void relaxLoss(int[] loss) {
            boolean changed = true;
            for (int pass = 0; changed && pass < 64; pass++) {
                changed = false;
                for (int sweep = 0; sweep < 2; sweep++) {
                    for (int step = 0; step < nx * nz; step++) {
                        int column = sweep == 0 ? step : nx * nz - 1 - step;
                        if (wallTop[column] < 0) {
                            continue;
                        }
                        int lx = column % nx;
                        int lz = column / nx;
                        int slope = wallMaterial[column].slope();
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                int ox = lx + dx;
                                int oz = lz + dz;
                                if ((dx | dz) == 0 || ox < 0 || oz < 0 || ox >= nx || oz >= nz) {
                                    continue;
                                }
                                int other = oz * nx + ox;
                                if (wallTop[other] < 0) {
                                    continue;
                                }
                                int floor = loss[other] - slope - (dx != 0 && dz != 0 ? 1 : 0);
                                if (loss[column] < floor) {
                                    loss[column] = floor;
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        /** Quita lo que queda por encima del perfil, con lo que descansaba encima del muro. */
        private void cutColumn(int lx, int lz, int column) {
            int limit = Math.min(ny - 2, wallTop[column] + RESTING);
            for (int ly = limit; ly > keep[column]; ly--) {
                int index = index(lx, ly, lz);
                if (!editable[index] || kind(index) != Kind.BUILDING) {
                    continue;
                }
                int side = role[index] == WALL || role[index] == INFILL ? throwSide(index) : -1;
                remove(index, side);
                eroded++;
            }
        }

        /** Hacia qué lado cae un trozo de muro: a uno de los lados que daban al aire (dentro o fuera). */
        private int throwSide(int index) {
            int lx = index % nx;
            int lz = (index / nx) % nz;
            int ly = index / (nx * nz);
            int[] open = new int[4];
            int count = 0;
            for (int side = 0; side < 4; side++) {
                int sx = lx + SIDES[side][0];
                int sz = lz + SIDES[side][2];
                if (sx < 0 || sz < 0 || sx >= nx || sz >= nz) {
                    continue;
                }
                Kind kind = kinds.get(original[index(sx, ly, sz)]);
                if (kind == Kind.AIR || kind == Kind.DECOR || kind == Kind.FLUID) {
                    open[count++] = side;
                }
            }
            if (count == 0) {
                return -1;
            }
            return open[(int) Math.floor(random(x(index), y(index), z(index), 205) * count)];
        }

        // ---------------------------------------------------------------- tejados, forjados y vidrio

        private void decaySpans() {
            int[] support = supportDistance();
            for (int ly = 1; ly < ny - 1; ly++) {
                for (int lz = 1; lz < nz - 1; lz++) {
                    for (int lx = 1; lx < nx - 1; lx++) {
                        int index = index(lx, ly, lz);
                        if (!editable[index] || kind(index) != Kind.BUILDING) {
                            continue;
                        }
                        double decay = materials.get(cells[index]).decay(intensity);
                        if (decay <= 0.0) {
                            continue;
                        }
                        int x = originX + lx;
                        int y = originY + ly;
                        int z = originZ + lz;
                        if (role[index] == SPAN) {
                            // Lejos de un apoyo (o con el muro de debajo roto) se hunde casi seguro.
                            double reach = Math.min(1.4, support[index] / 4.0);
                            double score = 0.62 * reach + 0.38 * noise(x, y, z, 3.5, 211)
                                    + 0.06 * (random(x, y, z, 212) - 0.5);
                            if (score > 1.0 - decay) {
                                remove(index, -1);
                                eroded++;
                            }
                        } else if (role[index] == INFILL) {
                            // Del vidrio quedan restos de panel, sobre todo abajo de cada ventana.
                            int below = 0;
                            while (ly - below - 1 > 0 && role[index(lx, ly - below - 1, lz)] == INFILL) {
                                below++;
                            }
                            int above = 0;
                            while (ly + above + 1 < ny - 1 && role[index(lx, ly + above + 1, lz)] == INFILL) {
                                above++;
                            }
                            double top = below + above == 0 ? 0.5 : below / (double) (below + above);
                            double score = 0.55 * noise(x, y, z, 2.0, 213) + 0.45 * top;
                            if (score > 1.0 - decay) {
                                remove(index, -1);
                                eroded++;
                            }
                        }
                    }
                }
            }
        }

        /**
         * Distancia de apoyo de cada bloque sólido, como la del derrumbe pero sin quitar nada: lo que apoya en algo
         * firme vale 0, bajar en vertical no cuesta y cada paso en diagonal (como descansa un tejado de escaleras) o
         * en horizontal cuesta uno. Así un tejado está más lejos del apoyo cuanto más cerca de la cumbrera, y una
         * hilera cuyo alero ha caído con el muro se queda colgando de los hastiales.
         */
        private int[] supportDistance() {
            int[] distance = new int[cells.length];
            Arrays.fill(distance, INF);
            for (int ly = 0; ly < ny; ly++) {
                for (int lz = 0; lz < nz; lz++) {
                    for (int lx = 0; lx < nx; lx++) {
                        int index = index(lx, ly, lz);
                        if (!solid(index)) {
                            continue;
                        }
                        distance[index] = anchor(index) || ly == 0 ? 0 : restingDistance(distance, lx, ly, lz);
                    }
                }
                relaxLayer(distance, ly);
            }
            return distance;
        }

        // ---------------------------------------------------------------- derrumbe

        /** Lo que estaba unido al suelo y ya no lo está cae, haya derrumbe o no: nada se queda flotando. */
        private void dropUnsupported() {
            boolean[] now = grounded();
            for (int index = 0; index < cells.length; index++) {
                if (groundedBefore[index] && !now[index] && editable[index] && kind(index) == Kind.BUILDING) {
                    remove(index, -1);
                    fallen++;
                }
            }
        }

        /** Bloques sólidos unidos (por caras) a algo firme: terreno, bloques protegidos o lo de fuera de la selección. */
        private boolean[] grounded() {
            boolean[] reached = new boolean[cells.length];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            for (int index = 0; index < cells.length; index++) {
                if (solid(index) && (anchor(index) || index < nx * nz)) {
                    reached[index] = true;
                    queue.add(index);
                }
            }
            while (!queue.isEmpty()) {
                int index = queue.poll();
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
                    if (!reached[neighbour] && solid(neighbour)) {
                        reached[neighbour] = true;
                        queue.add(neighbour);
                    }
                }
            }
            return reached;
        }

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
                            remove(index, -1);
                            distance[index] = INF;
                            fallen++;
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

        /** Algo sólido y firme debajo de un tramo: terreno, un bloque protegido o lo de fuera de la selección. */
        private boolean holds(int index) {
            return solid(index) && anchor(index);
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

        /** Quita un bloque de obra y apunta su escombro (lo que no se pulveriza o se pudre). */
        private void remove(int index, int side) {
            BlockText block = palette.get(cells[index]);
            Material material = materials.get(cells[index]);
            cells[index] = air;
            int x = x(index);
            int y = y(index);
            int z = z(index);
            double kept = 1.0 - material.shatter();
            if (material == Material.WOOD || material == Material.SOFT) {
                // Con los años la madera caída se pudre y desaparece.
                kept *= 1.0 - 0.75 * intensity;
            }
            if (random(x, y, z, 31) < kept) {
                falls.add(new Fall(x, y, z, AgingTable.rubble(block, style), side));
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
                for (int[] offset : NEIGHBOURS) {
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
                if (fall.side() >= 0) {
                    // Lo de un muro cae a su pie: casi siempre pegado, alguna vez un poco más lejos si caía de alto.
                    int[] side = SIDES[fall.side()];
                    double reach = Math.min(2.5, 1.0 + (y - buildingMinY) * 0.15);
                    double roll = random(x, y, z, 45);
                    int throwDistance = 1 + (int) Math.floor(roll * roll * reach);
                    for (int step = 0; step < throwDistance && passable(x + side[0], y, z + side[2]); step++) {
                        x += side[0];
                        z += side[2];
                    }
                } else if (random(x, y, z, 41) < 0.6) {
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
                for (int slide = 0; slide < 8; slide++) {
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

        /** Cercanía a lo roto (bloques de obra perdidos y escombro), de 1 junto a ello a 0 a cinco bloques. */
        private void computeDamage() {
            damage = new float[cells.length];
            int[] steps = new int[cells.length];
            Arrays.fill(steps, -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            for (int index = 0; index < cells.length; index++) {
                boolean lost = kinds.get(original[index]) == Kind.BUILDING && kind(index) != Kind.BUILDING;
                if (lost || debris[index]) {
                    steps[index] = 0;
                    queue.add(index);
                }
            }
            while (!queue.isEmpty()) {
                int index = queue.poll();
                damage[index] = 1.0f - steps[index] / (float) (DAMAGE_REACH + 1);
                if (steps[index] >= DAMAGE_REACH) {
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
                    if (steps[neighbour] < 0) {
                        steps[neighbour] = steps[index] + 1;
                        queue.add(neighbour);
                    }
                }
            }
        }

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
                        if (openFaces(lx, ly, lz) == 0) {
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

        /**
         * Variante envejecida por manchas: cada envejecimiento tiene un gradiente con sentido (dónde le toca) que se
         * multiplica por un ruido de baja frecuencia (la mancha) y se compara con un umbral que baja con la
         * intensidad. Un ruido fino y débil solo deshilacha el borde de la mancha.
         */
        private BlockText agedVariant(BlockText block, int lx, int ly, int lz) {
            int x = originX + lx;
            int y = originY + ly;
            int z = originZ + lz;
            int index = index(lx, ly, lz);
            double h = height(y);
            double exposure = Math.min(1.0, exposure(lx, ly, lz) * 1.6);
            boolean north = !solid(index(lx, ly, lz - 1));
            boolean shade = !lit(lx, ly, lz);
            double low = Math.clamp(1.0 - (y - buildingMinY) / 5.0, 0.0, 1.0);
            double broken = damage[index];

            if (AgingTable.variant(block, Aging.OXIDIZE, style) != null) {
                double rust = intensity * 3.2 * (0.4 + 0.6 * exposure) + 0.8 * (patch(x, y, z, 4.0, 71) - 0.5);
                int steps = (int) Math.floor(Math.max(0.0, rust));
                BlockText current = block;
                for (int step = 0; step < steps; step++) {
                    BlockText next = AgingTable.variant(current, Aging.OXIDIZE, style);
                    if (next == null) {
                        break;
                    }
                    current = next;
                }
                return steps > 0 ? current : null;
            }

            double damp = Math.max(low, Math.max(north ? 0.6 : 0.0, shade ? 0.5 : 0.0));
            double moss = style.moss() * (0.45 * low + 0.18 * (north ? 1 : 0) + 0.18 * (shade ? 1 : 0)
                    + 0.3 * streak(lx, ly, lz) + 0.12 * broken);
            if (spot(moss, patch(x, y, z, 4.0, 73), 0.65) && AgingTable.variant(block, Aging.MOSS, style) != null) {
                return AgingTable.variant(block, Aging.MOSS, style);
            }
            double frost = style == WeatherStyle.SNOWY ? 1.4 : 1.0;
            double crack = frost * (0.3 * exposure + 0.55 * broken + 0.2 * h);
            if (spot(crack, patch(x, y, z, 3.0, 74), 0.7) && AgingTable.variant(block, Aging.CRACK, style) != null) {
                return AgingTable.variant(block, Aging.CRACK, style);
            }
            double wear = 0.35 + 0.5 * exposure;
            if (spot(wear, patch(x, y, z, 5.0, 75), 0.6) && AgingTable.variant(block, Aging.WEAR, style) != null) {
                return AgingTable.variant(block, Aging.WEAR, style);
            }
            double rot = 0.3 + 0.3 * damp + 0.4 * broken + 0.2 * (shade ? 0 : 1);
            if (spot(rot, patch(x, y, z, 2.5, 76), 0.55) && AgingTable.variant(block, Aging.ROT, style) != null) {
                return AgingTable.variant(block, Aging.ROT, style);
            }
            double sun = (style == WeatherStyle.DESERT ? 0.75 : 0.5) * (0.4 + 0.6 * exposure);
            if (spot(sun, patch(x, y, z, 4.0, 77), 0.45)) {
                return AgingTable.variant(block, Aging.STRIP, style);
            }
            return null;
        }

        /** Alguna cara del bloque da a aire con cielo encima (le da el sol). */
        private boolean lit(int lx, int ly, int lz) {
            for (int[] offset : NEIGHBOURS) {
                int sx = lx + offset[0];
                int sy = ly + offset[1];
                int sz = lz + offset[2];
                if (!solid(index(sx, sy, sz)) && openToSky(sx, sy, sz)) {
                    return true;
                }
            }
            return false;
        }

        /** Mancha: el gradiente por la mancha supera el umbral que la intensidad va bajando. */
        private boolean spot(double gradient, double patch, double reach) {
            return gradient * (0.3 + 1.4 * patch) > 1.0 - reach * intensity;
        }

        /**
         * Churretes: bajo una ventana o bajo algo que sobresale (cornisa, alero) el agua resbala por la cara del muro.
         * 1 justo debajo, menos a cada bloque, nada a cinco.
         */
        private double streak(int lx, int ly, int lz) {
            double best = 0.0;
            for (int side = 0; side < 4; side++) {
                int fx = lx + SIDES[side][0];
                int fz = lz + SIDES[side][2];
                if (solid(index(fx, ly, fz))) {
                    continue;
                }
                for (int k = 1; k <= 4 && ly + k + 1 < ny; k++) {
                    int above = index(lx, ly + k, lz);
                    boolean opening = (!solid(above) || role[above] == INFILL) && solid(index(lx, ly + k + 1, lz));
                    boolean ledge = solid(index(fx, ly + k, fz));
                    if (opening || ledge) {
                        best = Math.max(best, 1.0 - (k - 1) * 0.25);
                        break;
                    }
                    if (!solid(above)) {
                        break;
                    }
                }
            }
            return best;
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

        /**
         * Templado: la vida sigue a la luz y al daño. Al cielo y junto a brechas y escombro crece mucho más que bajo
         * un techo entero; todo sale en manchas.
         */
        private void growTemperate(int lx, int ly, int lz, int index) {
            int x = originX + lx;
            int y = originY + ly;
            int z = originZ + lz;
            int below = index(lx, ly - 1, lz);
            boolean sky = openToSky(lx, ly, lz);
            double light = sky ? 1.0 : weather[index] >= 0.6f ? 0.45 : 0.1;
            double broken = Math.max(damage[index], damage[below]);
            double patch = patch(x, y, z, 4.0, 91);
            if (solid(below) && AgingTable.fullTop(palette.get(cells[below])) && climb(lx, ly, lz, broken)) {
                return;
            }
            if (solid(below) && AgingTable.fullTop(palette.get(cells[below]))) {
                BlockText ground = palette.get(cells[below]);
                double roll = random(x, y, z, 92);
                double green = vegetation * light * (0.35 + 0.9 * broken) * (0.2 + 1.3 * patch);
                if (AgingTable.soil(ground)) {
                    if (roll < green * 1.1) {
                        place(index, random(x, y, z, 93) < 0.7 ? "short_grass" : "fern");
                    }
                    return;
                }
                boolean rubbleTop = debris[below];
                boolean wallTop = editable[below] && kind(below) == Kind.BUILDING && !rubbleTop;
                if (sky && (rubbleTop || wallTop) && full(ground)
                        && roll < green * (rubbleTop ? 0.55 : 0.15)) {
                    cells[below] = id(BlockText.of("moss_block"));
                    life++;
                    if (random(x, y, z, 94) < 0.6) {
                        place(index, random(x, y, z, 95) < 0.75 ? "short_grass" : "fern");
                    }
                    return;
                }
                if (roll < green * 0.6) {
                    place(index, "moss_carpet");
                    return;
                }
            }
            if (cobweb(lx, ly, lz, index, 0.3)) {
                return;
            }
            int above = index(lx, ly + 1, lz);
            if (!sky && solid(above) && !solid(below) && random(x, y, z, 96) < vegetation * 0.05 * patch) {
                place(index, "hanging_roots");
                return;
            }
            vine(lx, ly, lz, index);
        }

        /**
         * Hiedra que sube desde el suelo por una cara húmeda (al norte, a la sombra o junto a lo roto), a matas: la
         * misma mancha cubre varias columnas seguidas del muro.
         */
        private boolean climb(int lx, int ly, int lz, double broken) {
            if (weather[index(lx, ly, lz)] < 0.6f) {
                return false;
            }
            int x = originX + lx;
            int y = originY + ly;
            int z = originZ + lz;
            for (int side = 0; side < 4; side++) {
                int sx = lx + SIDES[side][0];
                int sz = lz + SIDES[side][2];
                int wall = index(sx, ly, sz);
                if (kind(wall) != Kind.BUILDING || debris[wall] || kind(index(sx, ly + 1, sz)) != Kind.BUILDING) {
                    continue;
                }
                double damp = side == 2 ? 1.0 : openToSky(lx, ly, lz) ? 0.45 : 0.75;
                double clump = patch(x, y, z, 3.0, 221);
                if (clump * (0.4 + damp + 0.6 * broken) * vegetation < 0.55) {
                    continue;
                }
                int length = 2 + (int) Math.floor((vegetation + 0.3 * broken) * 6.0 * patch(x, 0, z, 2.0, 222));
                String facing = SIDE_NAMES[side];
                for (int level = ly; level < ny - 1 && level - ly < length; level++) {
                    int cell = index(lx, level, lz);
                    if (!editable[cell] || cells[cell] != air || kind(index(sx, level, sz)) != Kind.BUILDING) {
                        break;
                    }
                    place(cell, vineBlock(facing));
                }
                return true;
            }
            return false;
        }

        private void growDesert(int lx, int ly, int lz, int index) {
            int x = originX + lx;
            int y = originY + ly;
            int z = originZ + lz;
            int below = index(lx, ly - 1, lz);
            if (solid(below) && AgingTable.fullTop(palette.get(cells[below]))) {
                int walls = solidSides(lx, ly, lz);
                double roll = 1.0 - (0.7 * patch(x, y, z, 3.0, 101) + 0.3 * random(x, y, z, 102));
                double low = 1.0 - height(y);
                if (walls >= 1 && roll < vegetation * (walls >= 2 ? 0.9 : 0.45) * (0.3 + 0.7 * low)) {
                    place(index, "sand");
                    return;
                }
                String ground = palette.get(cells[below]).path();
                boolean sandy = ground.contains("sand") || ground.contains("terracotta") || ground.contains("dirt");
                if (sandy && random(x, y, z, 103) > 1.0 - vegetation * 0.12) {
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
                double roll = 0.6 * (1.0 - patch(x, y, z, 5.0, 111)) + 0.4 * random(x, y, z, 113);
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
            if (roll >= vegetation * 0.6 * (0.3 + 1.4 * patch(x, y, z, 4.0, 126))) {
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
            int x = originX + lx;
            int y = originY + ly;
            int z = originZ + lz;
            if (random(x, y, z, 131) < vegetation * rate * (0.2 + 1.6 * patch(x, y, z, 3.0, 132))) {
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
                if (random(x, y, z, 141 + side) >= vegetation * 0.45 * bias * patch(x, y, z, 4.0, 145)) {
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
                    place(cell, vineBlock(facing));
                }
                return;
            }
        }

        private static BlockText vineBlock(String facing) {
            return BlockText.of("vine", "east", Boolean.toString(facing.equals("east")),
                    "north", Boolean.toString(facing.equals("north")),
                    "south", Boolean.toString(facing.equals("south")),
                    "up", "false",
                    "west", Boolean.toString(facing.equals("west")));
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
         * a él. El aire de dentro de un tejado o de una habitación cerrada apenas recibe; el de una ventana o un
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
            for (int[] offset : NEIGHBOURS) {
                open += solid(index(lx + offset[0], ly + offset[1], lz + offset[2])) ? 0 : 1;
            }
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

        /** Material de obra que sostiene (no vidrio, paneles ni rejas). */
        private boolean structural(int index) {
            return kind(index) == Kind.BUILDING && !infill(index);
        }

        /** Vidrio, paneles y rejas: rellenan un hueco pero no cargan. */
        private boolean infill(int index) {
            if (kind(index) != Kind.BUILDING) {
                return false;
            }
            String path = palette.get(cells[index]).path();
            return materials.get(cells[index]) == Material.GLASS || path.endsWith("_pane") || path.endsWith("_bars");
        }

        private boolean door(int index) {
            return palette.get(cells[index]).path().endsWith("_door");
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

        /** Mancha de unos {@code scale} bloques con el borde deshilachado por un ruido fino. */
        private double patch(int x, int y, int z, double scale, int salt) {
            return 0.82 * noise(x, y, z, scale, salt) + 0.18 * noise(x, y, z, 1.3, salt + 500);
        }
    }

    /** Un trozo que cae: de dónde, en qué escombro se rompe y hacia qué lado sale despedido (-1: abajo). */
    private record Fall(int x, int y, int z, Rubble rubble, int side) {
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
