package com.komainu.moxia.roof;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/**
 * Calcula un tejado sobre una planta. Es Java puro: no toca Minecraft y se puede ejecutar en un worker.
 *
 * <ol>
 *     <li>Cada tipo da la altura de la cara de arriba del tejado en el centro de cada subcelda de medio bloque
 *     ({@link RoofGrid}) y la de abajo, a {@code grosor} capas medidas en perpendicular al faldón.</li>
 *     <li>Las alturas se redondean a medio bloque y se ajustan dentro de cada bloque para que lo que queda sea una
 *     pieza que existe: arriba, una losa, un bloque o una escalera (un escalón de medio bloque como mucho y con el
 *     alto en número entero); abajo, lo mismo boca abajo. Así los faldones son escaleras mirando al caballete, bajo
 *     el alero quedan escaleras colgadas ({@code half=top}) mirando hacia fuera y no hay muescas.</li>
 *     <li>Cada bloque queda como máscara de octantes; {@link RoofPieces} la convierte en pieza y resuelve las
 *     esquinas como lo hará Minecraft.</li>
 *     <li>Hastiales, paredes de las buhardillas, el escalón del holandés y el pretil se rellenan con el material
 *     del muro; nada pisa un bloque que no sea aire salvo con {@code -r}.</li>
 * </ol>
 */
public final class RoofGenerator {
    /** Altura del muro delantero de las buhardillas hasta el alero de su tejadillo. */
    static final int DORMER_WALL = 2;
    static final int DORMER_HALF_WIDTH = 2;
    /** Altura máxima de un tejado sobre los muros. */
    public static final int MAX_HEIGHT = 320;
    private static final double EPSILON = 1.0E-9;
    private static final double DORMER_SLOPE = 1.0;
    private static final double PAGODA_CURVE = 1.5;
    private static final double PAGODA_EAVE_SLOPE = 0.4;

    private RoofGenerator() {
    }

    public static RoofResult generate(Footprint footprint, RoofSettings settings, RoofWorld world) {
        RoofSettings.Side low = settings.lowSide() == null ? RoofSettings.Side.SOUTH : settings.lowSide();
        RoofGrid grid = new RoofGrid(footprint, settings.overhang());
        grid.classify(settings, low);
        Surface surface = surface(grid, settings, low);
        List<String> notes = new ArrayList<>();
        List<Dormer> dormers = settings.dormers() > 0 && settings.type().allowsDormers()
                ? placeDormers(grid, settings, surface, low, notes)
                : List.of();
        checkHeight(surface, footprint.baseY());
        Long2ByteOpenHashMap ringPieces = usesRings(settings) ? rings(grid, settings) : null;
        Long2IntOpenHashMap masks;
        if (ringPieces != null) {
            masks = new Long2IntOpenHashMap(ringPieces.size());
            for (var entry : ringPieces.long2ByteEntrySet()) {
                masks.put(entry.getLongKey(), SmoothPiece.mask(entry.getByteValue()));
            }
        } else {
            quantize(grid, surface);
            masks = masks(grid, surface);
        }
        if (masks.isEmpty()) {
            throw new IllegalArgumentException("El tejado no deja ningún bloque");
        }
        int[] lowest = new int[grid.cellsX * grid.cellsZ];
        int[] highest = new int[grid.cellsX * grid.cellsZ];
        columnLevels(grid, masks, lowest, highest);
        LongOpenHashSet ridge = settings.ridgeMaterial() != null && settings.type() != RoofType.FLAT
                && settings.type() != RoofType.DOME ? ridge(grid, surface, highest) : new LongOpenHashSet();
        Long2ByteOpenHashMap walls = walls(grid, settings, masks, lowest, highest, dormers);

        if (!settings.replace()) {
            masks.keySet().removeIf((long key) -> !air(world, key));
            walls.keySet().removeIf((long key) -> !air(world, key));
        }
        if (masks.isEmpty() && walls.isEmpty()) {
            throw new IllegalArgumentException("Todo el sitio del tejado está ocupado: quita lo que haya o usa -r");
        }
        RoofPieces.Solved solved;
        if (ringPieces != null) {
            ringPieces.keySet().removeIf((long key) -> !masks.containsKey(key));
            solved = RoofPieces.resolve(ringPieces);
        } else {
            solved = RoofPieces.solve(masks);
        }
        return assemble(solved, walls, ridge, dormers.size(), notes);
    }

    /**
     * Cono, cúpula y agujas que no son cuadradas se hacen por anillos de escaleras, como los construye un
     * constructor: cada columna mira hacia la cúspide y Minecraft pone las esquinas. La aguja de cuatro lados
     * (una pirámide) sale exacta por octantes, como los faldones.
     */
    static boolean usesRings(RoofSettings settings) {
        RoofType type = settings.type();
        return type == RoofType.CONE || type == RoofType.DOME || type == RoofType.SPIRE && settings.sides() != 4;
    }

    // ---------------------------------------------------------------- superficie

    /** Alturas de arriba y de abajo del tejado por subcelda ({@code NaN} = sin tejado). */
    static final class Surface {
        final double[] top;
        final double[] bottom;
        /** Distancia que gobierna los faldones (solo los tejados de faldones), para colocar buhardillas. */
        double[] drive;

        Surface(int size) {
            top = new double[size];
            bottom = new double[size];
            Arrays.fill(top, Double.NaN);
            Arrays.fill(bottom, Double.NaN);
        }

        void set(int index, double topY, double slope, int thickness) {
            top[index] = topY;
            bottom[index] = topY - thickness * Math.sqrt(1.0 + slope * slope);
        }

        void flat(int index, double topY, double bottomY) {
            top[index] = topY;
            bottom[index] = bottomY;
        }
    }

    /**
     * Desfase vertical de los faldones: con 1:1 deja una escalera justo encima del muro y otra fuera a la altura
     * de lo alto del muro; con 1:2 alterna losas y bloques; con más de 1:1, escaleras sobre columnas.
     */
    static double offset(double slope) {
        return slope > 1.0 + EPSILON ? 0.5 : 0.25;
    }

    private static Surface surface(RoofGrid grid, RoofSettings settings, RoofSettings.Side low) {
        Surface surface = new Surface(grid.subX * grid.subZ);
        double base = grid.footprint.baseY();
        double slope = settings.slope();
        double offset = offset(slope);
        int thickness = settings.thickness();
        switch (settings.type()) {
            case HIP -> {
                double[] distance = grid.distance(false);
                surface.drive = distance;
                forEach(distance, (index, d) -> surface.set(index, base + offset + slope * d, slope, thickness));
            }
            case GABLE -> {
                double[] distance = eaves(grid);
                surface.drive = distance;
                forEach(distance, (index, d) -> surface.set(index, base + offset + slope * d, slope, thickness));
            }
            case GABLET -> {
                double[] all = grid.distance(false);
                double[] eaves = eaves(grid);
                double cut = settings.breakDepth() > 0 ? settings.breakDepth()
                        : Math.max(1.0, Math.floor(maximumInPlan(grid, eaves) / 2.0));
                double[] distance = new double[all.length];
                for (int index = 0; index < all.length; index++) {
                    distance[index] = all[index] < cut ? all[index] : eaves[index];
                }
                surface.drive = distance;
                forEach(distance, (index, d) -> surface.set(index, base + offset + slope * d, slope, thickness));
            }
            case MANSARD -> {
                double[] distance = grid.distance(false);
                surface.drive = distance;
                double steep = Math.max(2.0, slope + 1.0);
                double depth = settings.breakDepth() > 0 ? settings.breakDepth()
                        : Math.clamp(Math.floor(maximumInPlan(grid, distance) / 2.0), 1.0, 2.0);
                forEach(distance, (index, d) -> {
                    if (d < 0.0) {
                        surface.set(index, base + offset + d, 1.0, thickness);
                    } else if (d <= depth) {
                        surface.set(index, base + offset + steep * d, steep, thickness);
                    } else {
                        surface.set(index, base + offset + steep * depth + slope * (d - depth), slope, thickness);
                    }
                });
            }
            case PAGODA -> pagoda(grid, settings, surface, base, offset);
            case SHED -> {
                double[] distance = new double[grid.subX * grid.subZ];
                Footprint footprint = grid.footprint;
                for (int k = 0; k < grid.subZ; k++) {
                    for (int i = 0; i < grid.subX; i++) {
                        int index = k * grid.subX + i;
                        if (!grid.inExtended(i >> 1, k >> 1)) {
                            distance[index] = Double.NaN;
                            continue;
                        }
                        double x = grid.subCenterX(i);
                        double z = grid.subCenterZ(k);
                        distance[index] = switch (low) {
                            case SOUTH -> footprint.maxZ() + 1 - z;
                            case NORTH -> z - footprint.minZ();
                            case EAST -> footprint.maxX() + 1 - x;
                            case WEST -> x - footprint.minX();
                        };
                    }
                }
                surface.drive = distance;
                forEach(distance, (index, d) -> surface.set(index, base + offset + slope * d, slope, thickness));
            }
            case FLAT -> {
                for (int k = 0; k < grid.subZ; k++) {
                    for (int i = 0; i < grid.subX; i++) {
                        if (grid.inExtended(i >> 1, k >> 1)) {
                            surface.flat(k * grid.subX + i, base + thickness, base);
                        }
                    }
                }
            }
            case DOME, CONE, SPIRE -> round(grid, settings, surface, base, offset);
        }
        return surface;
    }

    /** Distancia de los de dos aguas; si la planta no tiene ningún alero (no debería), la de cuatro aguas. */
    private static double[] eaves(RoofGrid grid) {
        double[] distance = grid.distance(true);
        for (double value : distance) {
            if (!Double.isNaN(value)) {
                return distance;
            }
        }
        return grid.distance(false);
    }

    private static void pagoda(RoofGrid grid, RoofSettings settings, Surface surface, double base, double offset) {
        double[] distance = grid.distance(false);
        double maximum = Math.max(0.25, maximumInPlan(grid, distance));
        double height = Double.isNaN(settings.height()) ? 0.85 * settings.slope() * maximum : settings.height();
        double[] alongX = grid.axisDistance(true);
        double[] alongZ = grid.axisDistance(false);
        double reach = settings.overhang() + 1.5;
        double lift = 0.5 + 0.5 * settings.overhang();
        int thickness = settings.thickness();
        forEach(distance, (index, d) -> {
            double rise;
            double slope;
            if (d >= 0.0) {
                rise = height * Math.pow(d / maximum, PAGODA_CURVE);
                slope = height * PAGODA_CURVE * Math.pow(d, PAGODA_CURVE - 1.0) / Math.pow(maximum, PAGODA_CURVE);
            } else {
                rise = PAGODA_EAVE_SLOPE * d;
                slope = PAGODA_EAVE_SLOPE;
            }
            double corner = Math.max(alongX[index], alongZ[index]);
            // Solo se levantan las puntas del alero, junto a las esquinas salientes.
            if (corner < reach && d < 1.0) {
                double weight = (reach - corner) / reach;
                rise += lift * weight * weight;
            }
            surface.set(index, base + offset + rise, Math.max(slope, PAGODA_EAVE_SLOPE), thickness);
        });
    }

    /**
     * Cúpula, cono y aguja: inscritos en la caja de la planta más el alero, con el centro en el de la caja. El resto
     * de la planta se cubre con losas, así no queda lo alto de los muros al aire.
     */
    private static void round(RoofGrid grid, RoofSettings settings, Surface surface, double base, double offset) {
        Footprint footprint = grid.footprint;
        double centerX = footprint.minX() + footprint.sizeX() / 2.0;
        double centerZ = footprint.minZ() + footprint.sizeZ() / 2.0;
        double radiusX = footprint.sizeX() / 2.0 + settings.overhang();
        double radiusZ = footprint.sizeZ() / 2.0 + settings.overhang();
        double radius = Math.min(radiusX, radiusZ);
        int thickness = settings.thickness();
        RoofType type = settings.type();
        double slope = settings.slope();
        if (type != RoofType.DOME && !Double.isNaN(settings.height())) {
            slope = Math.max(0.1, (settings.height() - offset) / radius);
        }
        double domeHeight = Double.isNaN(settings.height()) ? radius * slope : settings.height();
        double[] normalsX = new double[settings.sides()];
        double[] normalsZ = new double[settings.sides()];
        for (int side = 0; side < settings.sides(); side++) {
            normalsX[side] = snap(Math.cos(2.0 * Math.PI * side / settings.sides()));
            normalsZ[side] = snap(Math.sin(2.0 * Math.PI * side / settings.sides()));
        }
        for (int cz = 0; cz < grid.cellsZ; cz++) {
            for (int cx = 0; cx < grid.cellsX; cx++) {
                double blockX = grid.worldX(cx) + 0.5 - centerX;
                double blockZ = grid.worldZ(cz) + 0.5 - centerZ;
                boolean covered = switch (type) {
                    case DOME -> square(blockX / radiusX) + square(blockZ / radiusZ) < 1.0;
                    case CONE -> Math.hypot(blockX, blockZ) <= radius + 0.25;
                    default -> polygon(blockX, blockZ, normalsX, normalsZ) <= radius + 0.25;
                };
                for (int oz = 0; oz < 2; oz++) {
                    for (int ox = 0; ox < 2; ox++) {
                        int i = cx * 2 + ox;
                        int k = cz * 2 + oz;
                        int index = k * grid.subX + i;
                        double x = grid.subCenterX(i) - centerX;
                        double z = grid.subCenterZ(k) - centerZ;
                        if (covered && type == RoofType.DOME) {
                            double outer = square(x / radiusX) + square(z / radiusZ);
                            if (outer < 1.0) {
                                double innerX = radiusX - thickness;
                                double innerZ = radiusZ - thickness;
                                double innerHeight = domeHeight - thickness;
                                double inner = innerX > 0.0 && innerZ > 0.0
                                        ? square(x / innerX) + square(z / innerZ) : 2.0;
                                double bottom = inner < 1.0 && innerHeight > 0.0
                                        ? base + innerHeight * Math.sqrt(1.0 - inner) : base;
                                surface.flat(index, base + domeHeight * Math.sqrt(1.0 - outer), bottom);
                                continue;
                            }
                        } else if (covered) {
                            double distance = type == RoofType.CONE ? Math.hypot(x, z)
                                    : polygon(x, z, normalsX, normalsZ);
                            surface.set(index, base + offset + slope * (radius - distance - settings.overhang()), slope,
                                    thickness);
                            continue;
                        }
                        if (grid.inPlan(cx, cz)) {
                            surface.flat(index, base + 0.5, base);
                        }
                    }
                }
            }
        }
    }

    /**
     * Anillos de las figuras redondas: en cada columna, la altura de arriba en su centro decide la pieza de arriba
     * (nada, escalera mirando a la cúspide o bloque entero; losa donde casi no hay pendiente) y la de abajo, la
     * pieza de abajo (escalera colgada mirando hacia fuera, losa superior o bloque); en medio, bloques enteros.
     */
    private static Long2ByteOpenHashMap rings(RoofGrid grid, RoofSettings settings) {
        Footprint footprint = grid.footprint;
        double centerX = footprint.minX() + footprint.sizeX() / 2.0;
        double centerZ = footprint.minZ() + footprint.sizeZ() / 2.0;
        double radiusX = footprint.sizeX() / 2.0 + settings.overhang();
        double radiusZ = footprint.sizeZ() / 2.0 + settings.overhang();
        double radius = Math.min(radiusX, radiusZ);
        double base = footprint.baseY();
        double slope = settings.slope();
        double offset = offset(slope);
        int thickness = settings.thickness();
        RoofType type = settings.type();
        if (type != RoofType.DOME && !Double.isNaN(settings.height())) {
            slope = Math.max(0.1, (settings.height() - offset) / radius);
            offset = offset(slope);
        }
        double domeHeight = Double.isNaN(settings.height()) ? radius * settings.slope() : settings.height();
        double[] normalsX = new double[settings.sides()];
        double[] normalsZ = new double[settings.sides()];
        for (int side = 0; side < settings.sides(); side++) {
            normalsX[side] = snap(Math.cos(2.0 * Math.PI * side / settings.sides()));
            normalsZ[side] = snap(Math.sin(2.0 * Math.PI * side / settings.sides()));
        }
        Column[] columns = new Column[grid.cellsX * grid.cellsZ];
        int baseLevel = footprint.baseY();
        for (int cz = 0; cz < grid.cellsZ; cz++) {
            for (int cx = 0; cx < grid.cellsX; cx++) {
                int x = grid.worldX(cx);
                int z = grid.worldZ(cz);
                int cell = cz * grid.cellsX + cx;
                double bx = x + 0.5 - centerX;
                double bz = z + 0.5 - centerZ;
                double top;
                double bottom;
                double steepness;
                double uphillX;
                double uphillZ;
                if (type == RoofType.DOME) {
                    double outer = Math.sqrt(square(bx / radiusX) + square(bz / radiusZ));
                    if (outer >= 1.0) {
                        columns[cell] = grid.inPlan(cx, cz) ? Column.rest(baseLevel) : null;
                        continue;
                    }
                    // Todo el anillo a la altura de su centro: los anillos quedan limpios.
                    double ring = Math.floor((1.0 - outer) * radius) + 0.5;
                    double position = Math.max(0.0, 1.0 - ring / radius);
                    double root = Math.sqrt(1.0 - position * position);
                    top = base + domeHeight * root;
                    double innerRadius = radius - thickness;
                    double innerHeight = domeHeight - thickness;
                    double inner = innerRadius > 0.0 ? position * radius / innerRadius : 2.0;
                    bottom = inner < 1.0 && innerHeight > 0.0 ? base + innerHeight * Math.sqrt(1.0 - inner * inner)
                            : base;
                    uphillX = -bx / (radiusX * radiusX);
                    uphillZ = -bz / (radiusZ * radiusZ);
                    steepness = domeHeight * position / (radius * Math.max(root, 1.0E-3));
                } else {
                    double distance;
                    if (type == RoofType.CONE) {
                        distance = Math.hypot(bx, bz);
                        uphillX = -bx;
                        uphillZ = -bz;
                    } else {
                        int nearest = 0;
                        distance = Double.NEGATIVE_INFINITY;
                        for (int side = 0; side < normalsX.length; side++) {
                            double value = bx * normalsX[side] + bz * normalsZ[side];
                            if (value > distance + EPSILON) {
                                distance = value;
                                nearest = side;
                            }
                        }
                        uphillX = -normalsX[nearest];
                        uphillZ = -normalsZ[nearest];
                    }
                    double inset = radius - distance;
                    if (inset < -0.25) {
                        columns[cell] = grid.inPlan(cx, cz) ? Column.rest(baseLevel) : null;
                        continue;
                    }
                    // Anillos de altura constante, a la de su centro; el alero cuelga como en los faldones.
                    double ring = Math.floor(Math.max(inset, 0.0)) + 0.5;
                    top = base + offset + slope * (ring - settings.overhang());
                    bottom = top - thickness * Math.sqrt(1.0 + slope * slope);
                    steepness = distance < 0.5 ? 0.0 : slope;
                }
                Column column = Column.of(top, bottom, steepness < 0.5,
                        type == RoofType.DOME && bottom <= base + EPSILON, facing(uphillX, uphillZ));
                if (grid.inPlan(cx, cz) && column.topLevel < baseLevel) {
                    // Dentro de la planta el tejado tiene que tapar lo alto del muro aunque el anillo cuelgue.
                    column = new Column(baseLevel, SmoothPiece.slab(false), column.bottomLevel, column.bottomPiece);
                }
                columns[cell] = column;
            }
        }
        // Una columna que queda por encima de su vecina baja hasta lo alto de ella: entre anillos no hay rendijas.
        int[] reach = new int[columns.length];
        for (int cz = 0; cz < grid.cellsZ; cz++) {
            for (int cx = 0; cx < grid.cellsX; cx++) {
                int cell = cz * grid.cellsX + cx;
                if (columns[cell] == null) {
                    continue;
                }
                int bottom = columns[cell].bottomLevel;
                for (int direction = 0; direction < 4; direction++) {
                    int nx = cx + RoofGrid.DX[direction];
                    int nz = cz + RoofGrid.DZ[direction];
                    if (nx >= 0 && nz >= 0 && nx < grid.cellsX && nz < grid.cellsZ
                            && columns[nz * grid.cellsX + nx] != null) {
                        bottom = Math.min(bottom, columns[nz * grid.cellsX + nx].topLevel);
                    }
                }
                reach[cell] = bottom;
            }
        }
        Long2ByteOpenHashMap pieces = new Long2ByteOpenHashMap();
        for (int cz = 0; cz < grid.cellsZ; cz++) {
            for (int cx = 0; cx < grid.cellsX; cx++) {
                Column column = columns[cz * grid.cellsX + cx];
                if (column != null) {
                    column.emit(pieces, grid.worldX(cx), grid.worldZ(cz), reach[cz * grid.cellsX + cx]);
                }
            }
        }
        return pieces;
    }

    /** Dirección de Minecraft (sur, oeste, norte, este) más parecida a un vector horizontal. */
    static int facing(double x, double z) {
        if (Math.abs(x) >= Math.abs(z) - EPSILON) {
            return x >= 0.0 ? SmoothPiece.EAST : SmoothPiece.WEST;
        }
        return z >= 0.0 ? SmoothPiece.SOUTH : SmoothPiece.NORTH;
    }

    /**
     * Una columna de un anillo: la pieza de arriba, la de abajo y bloques enteros en medio. En pendiente, arriba
     * siempre va una escalera mirando a la cúspide (a la altura que mejor deja su escalón) y abajo una escalera
     * colgada mirando hacia fuera; donde casi no hay pendiente, losas o bloques según la altura.
     */
    private record Column(int topLevel, byte topPiece, int bottomLevel, byte bottomPiece) {
        /** Losa sobre lo alto del muro, para la parte de la planta que no cubre la figura. */
        static Column rest(int base) {
            return new Column(base, SmoothPiece.slab(false), base, SmoothPiece.slab(false));
        }

        /** @param seated la columna llega a lo alto de los muros (el arranque de la cúpula): abajo, bloque entero */
        static Column of(double top, double bottom, boolean gentle, boolean seated, int uphill) {
            int topLevel;
            byte topPiece;
            if (!gentle) {
                topLevel = (int) Math.round(top - 0.75);
                topPiece = SmoothPiece.stairs(uphill, false, SmoothPiece.STRAIGHT);
            } else {
                int level = (int) Math.floor(top);
                double fraction = top - level;
                topLevel = fraction < 0.25 ? level - 1 : level;
                topPiece = fraction >= 0.25 && fraction < 0.75 ? SmoothPiece.slab(false) : SmoothPiece.FULL;
            }
            int bottomLevel;
            byte bottomPiece;
            if (seated) {
                bottomLevel = (int) Math.round(bottom);
                bottomPiece = SmoothPiece.FULL;
            } else if (!gentle) {
                bottomLevel = (int) Math.round(bottom - 0.25);
                bottomPiece = SmoothPiece.stairs(SmoothPiece.opposite(uphill), true, SmoothPiece.STRAIGHT);
            } else {
                int level = (int) Math.floor(bottom);
                double fraction = bottom - level;
                bottomLevel = fraction >= 0.75 ? level + 1 : level;
                bottomPiece = fraction >= 0.25 && fraction < 0.75 ? SmoothPiece.slab(true) : SmoothPiece.FULL;
            }
            return new Column(topLevel, topPiece, Math.min(bottomLevel, topLevel), bottomPiece);
        }

        /** @param reach hasta dónde tiene que bajar para tocar a sus vecinas */
        void emit(Long2ByteOpenHashMap pieces, int x, int z, int reach) {
            int bottom = Math.min(bottomLevel, reach);
            if (bottom == topLevel) {
                // Una sola pieza: si las dos son parciales no caben juntas y queda el bloque entero.
                byte piece = topPiece == SmoothPiece.FULL ? bottomPiece
                        : bottomPiece == SmoothPiece.FULL || topPiece == bottomPiece ? topPiece : SmoothPiece.FULL;
                pieces.put(PackedBlockPlan.pack(x, topLevel, z), piece);
                return;
            }
            pieces.put(PackedBlockPlan.pack(x, topLevel, z), topPiece);
            for (int y = bottom + 1; y < topLevel; y++) {
                pieces.put(PackedBlockPlan.pack(x, y, z), SmoothPiece.FULL);
            }
            pieces.put(PackedBlockPlan.pack(x, bottom, z), bottom < bottomLevel && !SmoothPiece.isStairs(bottomPiece)
                    ? SmoothPiece.FULL : bottomPiece);
        }
    }

    /** Quita el ruido de coma flotante de senos y cosenos exactos (cos 90° = 0), para que la aguja sea simétrica. */
    private static double snap(double value) {
        double rounded = Math.rint(value * 1.0E12) / 1.0E12;
        return rounded == 0.0 ? 0.0 : rounded;
    }

    private static double polygon(double x, double z, double[] normalsX, double[] normalsZ) {
        double distance = Double.NEGATIVE_INFINITY;
        for (int side = 0; side < normalsX.length; side++) {
            distance = Math.max(distance, x * normalsX[side] + z * normalsZ[side]);
        }
        return distance;
    }

    private static double maximumInPlan(RoofGrid grid, double[] values) {
        double maximum = 0.0;
        for (int k = 0; k < grid.subZ; k++) {
            for (int i = 0; i < grid.subX; i++) {
                double value = values[k * grid.subX + i];
                if (grid.inPlan(i >> 1, k >> 1) && !Double.isNaN(value)) {
                    maximum = Math.max(maximum, value);
                }
            }
        }
        return maximum;
    }

    private static void checkHeight(Surface surface, int base) {
        double highest = Double.NEGATIVE_INFINITY;
        double lowest = Double.POSITIVE_INFINITY;
        for (int index = 0; index < surface.top.length; index++) {
            if (!Double.isNaN(surface.top[index])) {
                highest = Math.max(highest, surface.top[index]);
                lowest = Math.min(lowest, surface.bottom[index]);
            }
        }
        if (highest == Double.NEGATIVE_INFINITY) {
            throw new IllegalArgumentException("El tejado no cubre nada");
        }
        if (highest - base > MAX_HEIGHT) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "El tejado saldría de %d bloques de alto (máximo %d): baja la pendiente o la altura",
                    (int) Math.ceil(highest - base), MAX_HEIGHT));
        }
        if (highest > 2040 || lowest < -2040) {
            throw new IllegalArgumentException("El tejado sale de la altura del mundo");
        }
    }

    // ---------------------------------------------------------------- buhardillas

    /** Buhardilla en un alero: fila del muro, columna central y dirección hacia fuera. */
    record Dormer(int direction, int centerX, int centerZ) {
        int lateralX() {
            return Math.abs(RoofGrid.DZ[direction]);
        }

        int lateralZ() {
            return Math.abs(RoofGrid.DX[direction]);
        }
    }

    /**
     * Reparte las buhardillas por los aleros rectos de al menos nueve bloques con dos esquinas salientes. Cada una
     * mide cinco de ancho: muro delantero de {@value #DORMER_WALL} bloques con ventana, mejillas a los lados y
     * tejadillo a dos aguas que entra en el faldón hasta que este lo tapa. Se omiten las que caerían fuera de un
     * faldón limpio (en una limatesa) o en un tejado demasiado bajo para que quepan.
     */
    private static List<Dormer> placeDormers(RoofGrid grid, RoofSettings settings, Surface surface,
                                             RoofSettings.Side low, List<String> notes) {
        List<Dormer> placed = new ArrayList<>();
        int skipped = 0;
        int missing = 0;
        double base = grid.footprint.baseY();
        double dormerTop = base + DORMER_WALL + offset(DORMER_SLOPE) + DORMER_SLOPE * (DORMER_HALF_WIDTH + 0.25);
        for (int direction = 0; direction < 4; direction++) {
            int nx = RoofGrid.DX[direction];
            int nz = RoofGrid.DZ[direction];
            if (settings.type() == RoofType.SHED && (low.dx() != nx || low.dz() != nz)) {
                continue;
            }
            int lx = Math.abs(nz);
            int lz = Math.abs(nx);
            for (int cz = 0; cz < grid.cellsZ; cz++) {
                for (int cx = 0; cx < grid.cellsX; cx++) {
                    if (!edgeStart(grid, cx, cz, direction)) {
                        continue;
                    }
                    int length = 0;
                    while (crossing(grid, cx + length * lx, cz + length * lz, direction)) {
                        length++;
                    }
                    boolean convex = !grid.inPlan(cx - lx, cz - lz) && !grid.inPlan(cx + length * lx, cz + length * lz);
                    if (!convex || length < 9 || grid.edgeType(cx, cz, direction) != RoofGrid.EAVE) {
                        continue;
                    }
                    int fit = (length - 9) / 6 + 1;
                    int count = Math.min(settings.dormers(), fit);
                    missing += settings.dormers() - count;
                    for (int dormer = 0; dormer < count; dormer++) {
                        int offsetAlong = 4 + (int) Math.floor((dormer + 0.5) * (length - 8) / count);
                        int centerX = cx + offsetAlong * lx;
                        int centerZ = cz + offsetAlong * lz;
                        Dormer candidate = new Dormer(direction, centerX, centerZ);
                        if (apply(grid, settings, surface, candidate, dormerTop)) {
                            placed.add(candidate);
                        } else {
                            skipped++;
                        }
                    }
                }
            }
        }
        if (missing > 0) {
            notes.add(missing + " buhardilla(s) no caben en los aleros (cada una pide 5 bloques y 1 de hueco)");
        }
        if (skipped > 0) {
            notes.add(skipped + " buhardilla(s) omitidas: el faldón es demasiado bajo o cae en una limatesa");
        }
        if (placed.isEmpty() && notes.isEmpty()) {
            notes.add("No hay aleros rectos de 9 bloques o más para poner buhardillas");
        }
        return placed;
    }

    private static boolean edgeStart(RoofGrid grid, int cx, int cz, int direction) {
        int lx = Math.abs(RoofGrid.DZ[direction]);
        int lz = Math.abs(RoofGrid.DX[direction]);
        return crossing(grid, cx, cz, direction) && !crossing(grid, cx - lx, cz - lz, direction);
    }

    private static boolean crossing(RoofGrid grid, int cx, int cz, int direction) {
        return grid.inPlan(cx, cz) && !grid.inPlan(cx + RoofGrid.DX[direction], cz + RoofGrid.DZ[direction]);
    }

    private static boolean apply(RoofGrid grid, RoofSettings settings, Surface surface, Dormer dormer,
                                 double dormerTop) {
        if (surface.drive == null) {
            return false;
        }
        int direction = dormer.direction();
        int nx = RoofGrid.DX[direction];
        int nz = RoofGrid.DZ[direction];
        int lx = dormer.lateralX();
        int lz = dormer.lateralZ();
        // Fila hasta la que llega el tejadillo: la primera en la que el faldón ya lo tapa por el centro.
        int rows = -1;
        for (int row = 0; row < 64; row++) {
            int cx = dormer.centerX() - row * nx;
            int cz = dormer.centerZ() - row * nz;
            if (!grid.inPlan(cx, cz)) {
                return false;
            }
            boolean covered = true;
            for (int index : subcells(grid, cx, cz)) {
                covered &= surface.top[index] >= dormerTop + 0.5;
            }
            if (covered) {
                rows = row;
                break;
            }
        }
        if (rows <= 1) {
            return false;
        }
        double face = faceCoordinate(grid, dormer);
        // Todo el tejadillo tiene que caer en un faldón limpio de este alero.
        for (int row = 0; row < rows; row++) {
            for (int lateral = -DORMER_HALF_WIDTH; lateral <= DORMER_HALF_WIDTH; lateral++) {
                int cx = dormer.centerX() + lateral * lx - row * nx;
                int cz = dormer.centerZ() + lateral * lz - row * nz;
                if (!grid.inPlan(cx, cz)) {
                    return false;
                }
                for (int index : subcells(grid, cx, cz)) {
                    double perpendicular = perpendicular(grid, index, direction, face);
                    if (Math.abs(surface.drive[index] - perpendicular) > 1.0E-6) {
                        return false;
                    }
                }
            }
        }
        double base = grid.footprint.baseY();
        double start = lateralCoordinate(grid, dormer.centerX() - DORMER_HALF_WIDTH * lx,
                dormer.centerZ() - DORMER_HALF_WIDTH * lz, lx);
        double width = 2 * DORMER_HALF_WIDTH + 1;
        double dormerOffset = offset(DORMER_SLOPE);
        for (int row = 0; row < rows; row++) {
            for (int lateral = -DORMER_HALF_WIDTH; lateral <= DORMER_HALF_WIDTH; lateral++) {
                int cx = dormer.centerX() + lateral * lx - row * nx;
                int cz = dormer.centerZ() + lateral * lz - row * nz;
                for (int index : subcells(grid, cx, cz)) {
                    int i = index % grid.subX;
                    int k = index / grid.subX;
                    double position = lx == 1 ? grid.subCenterX(i) : grid.subCenterZ(k);
                    double inward = Math.min(position - start, start + width - position);
                    double top = base + DORMER_WALL + dormerOffset + DORMER_SLOPE * inward;
                    if (top > surface.top[index] + EPSILON) {
                        surface.set(index, top, DORMER_SLOPE, settings.thickness());
                    }
                }
            }
        }
        return true;
    }

    /** Coordenada de la cara de fuera del muro del alero de la buhardilla (x o z según la dirección). */
    private static double faceCoordinate(RoofGrid grid, Dormer dormer) {
        int direction = dormer.direction();
        return switch (direction) {
            case 0 -> grid.worldX(dormer.centerX()) + 1;
            case 1 -> grid.worldX(dormer.centerX());
            case 2 -> grid.worldZ(dormer.centerZ()) + 1;
            default -> grid.worldZ(dormer.centerZ());
        };
    }

    private static double perpendicular(RoofGrid grid, int index, int direction, double face) {
        int i = index % grid.subX;
        int k = index / grid.subX;
        return switch (direction) {
            case 0 -> face - grid.subCenterX(i);
            case 1 -> grid.subCenterX(i) - face;
            case 2 -> face - grid.subCenterZ(k);
            default -> grid.subCenterZ(k) - face;
        };
    }

    private static double lateralCoordinate(RoofGrid grid, int cx, int cz, int lx) {
        return lx == 1 ? grid.worldX(cx) : grid.worldZ(cz);
    }

    private static int[] subcells(RoofGrid grid, int cx, int cz) {
        int i = cx * 2;
        int k = cz * 2;
        int row = k * grid.subX + i;
        return new int[]{row, row + 1, row + grid.subX, row + grid.subX + 1};
    }

    // ---------------------------------------------------------------- cuantización y octantes

    /**
     * Redondea a medio bloque y ajusta cada bloque a una pieza posible. Arriba: si lo más alto es un número entero,
     * lo demás sube hasta medio bloque por debajo (escalera); si es medio, todo sube a esa altura (losa). Abajo, lo
     * mismo boca abajo. Solo se añade material: el tejado nunca queda más fino que lo calculado.
     */
    private static void quantize(RoofGrid grid, Surface surface) {
        for (int index = 0; index < surface.top.length; index++) {
            if (!Double.isNaN(surface.top[index])) {
                surface.top[index] = Math.floor(surface.top[index] * 2.0 + 0.5) / 2.0;
                surface.bottom[index] = Math.min(Math.ceil(surface.bottom[index] * 2.0 - 0.5) / 2.0,
                        surface.top[index] - 0.5);
            }
        }
        for (int cz = 0; cz < grid.cellsZ; cz++) {
            for (int cx = 0; cx < grid.cellsX; cx++) {
                int[] cells = subcells(grid, cx, cz);
                double highest = Double.NEGATIVE_INFINITY;
                double lowest = Double.POSITIVE_INFINITY;
                for (int index : cells) {
                    if (!Double.isNaN(surface.top[index])) {
                        highest = Math.max(highest, surface.top[index]);
                        lowest = Math.min(lowest, surface.bottom[index]);
                    }
                }
                if (highest == Double.NEGATIVE_INFINITY) {
                    continue;
                }
                double topFloor = isWhole(highest) ? highest - 0.5 : highest;
                double bottomCeiling = isWhole(lowest) ? lowest + 0.5 : lowest;
                for (int index : cells) {
                    if (!Double.isNaN(surface.top[index])) {
                        surface.top[index] = Math.max(surface.top[index], topFloor);
                        surface.bottom[index] = Math.min(surface.bottom[index], bottomCeiling);
                    }
                }
            }
        }
    }

    private static boolean isWhole(double value) {
        return Math.abs(value - Math.rint(value)) < EPSILON;
    }

    /** Máscara de octantes de cada bloque que toca el tejado. */
    private static Long2IntOpenHashMap masks(RoofGrid grid, Surface surface) {
        Long2IntOpenHashMap masks = new Long2IntOpenHashMap();
        int checked = 0;
        for (int cz = 0; cz < grid.cellsZ; cz++) {
            for (int cx = 0; cx < grid.cellsX; cx++) {
                if ((checked++ & 1_023) == 0 && Thread.currentThread().isInterrupted()) {
                    throw new CancellationException();
                }
                int[] cells = subcells(grid, cx, cz);
                double highest = Double.NEGATIVE_INFINITY;
                double lowest = Double.POSITIVE_INFINITY;
                for (int index : cells) {
                    if (!Double.isNaN(surface.top[index])) {
                        highest = Math.max(highest, surface.top[index]);
                        lowest = Math.min(lowest, surface.bottom[index]);
                    }
                }
                if (highest == Double.NEGATIVE_INFINITY) {
                    continue;
                }
                int x = grid.worldX(cx);
                int z = grid.worldZ(cz);
                for (int level = (int) Math.floor(lowest); level < (int) Math.ceil(highest); level++) {
                    int mask = 0;
                    for (int corner = 0; corner < 4; corner++) {
                        int index = cells[corner];
                        if (Double.isNaN(surface.top[index])) {
                            continue;
                        }
                        int ox = corner & 1;
                        int oz = corner >> 1;
                        for (int oy = 0; oy < 2; oy++) {
                            double low = level + 0.5 * oy;
                            if (surface.bottom[index] <= low + EPSILON && low + 0.5 <= surface.top[index] + EPSILON) {
                                mask |= 1 << SmoothPiece.octant(ox, oy, oz);
                            }
                        }
                    }
                    if (mask != 0) {
                        masks.put(PackedBlockPlan.pack(x, level, z), mask);
                    }
                }
            }
        }
        return masks;
    }

    private static void columnLevels(RoofGrid grid, Long2IntOpenHashMap masks, int[] lowest, int[] highest) {
        Arrays.fill(lowest, Integer.MAX_VALUE);
        Arrays.fill(highest, Integer.MIN_VALUE);
        for (long key : masks.keySet()) {
            int cx = PackedBlockPlan.unpackX(key) - grid.originX;
            int cz = PackedBlockPlan.unpackZ(key) - grid.originZ;
            int y = PackedBlockPlan.unpackY(key);
            int cell = cz * grid.cellsX + cx;
            lowest[cell] = Math.min(lowest[cell], y);
            highest[cell] = Math.max(highest[cell], y);
        }
    }

    /** Lo más alto de cada columna que no tiene ninguna vecina (de las ocho) más alta: el caballete y la cúspide. */
    private static LongOpenHashSet ridge(RoofGrid grid, Surface surface, int[] highest) {
        double[] columnTop = new double[grid.cellsX * grid.cellsZ];
        Arrays.fill(columnTop, Double.NEGATIVE_INFINITY);
        for (int cz = 0; cz < grid.cellsZ; cz++) {
            for (int cx = 0; cx < grid.cellsX; cx++) {
                for (int index : subcells(grid, cx, cz)) {
                    if (!Double.isNaN(surface.top[index])) {
                        columnTop[cz * grid.cellsX + cx] = Math.max(columnTop[cz * grid.cellsX + cx],
                                surface.top[index]);
                    }
                }
            }
        }
        LongOpenHashSet ridge = new LongOpenHashSet();
        for (int cz = 0; cz < grid.cellsZ; cz++) {
            for (int cx = 0; cx < grid.cellsX; cx++) {
                int cell = cz * grid.cellsX + cx;
                if (columnTop[cell] == Double.NEGATIVE_INFINITY || highest[cell] == Integer.MIN_VALUE
                        || !grid.inExtended(cx, cz)) {
                    continue;
                }
                boolean peak = true;
                for (int dz = -1; dz <= 1 && peak; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = cx + dx;
                        int nz = cz + dz;
                        if (nx >= 0 && nz >= 0 && nx < grid.cellsX && nz < grid.cellsZ
                                && columnTop[nz * grid.cellsX + nx] > columnTop[cell] + EPSILON) {
                            peak = false;
                            break;
                        }
                    }
                }
                if (peak) {
                    ridge.add(PackedBlockPlan.pack(grid.worldX(cx), highest[cell], grid.worldZ(cz)));
                }
            }
        }
        return ridge;
    }

    // ---------------------------------------------------------------- paredes

    private static Long2ByteOpenHashMap walls(RoofGrid grid, RoofSettings settings, Long2IntOpenHashMap roof,
                                              int[] lowest, int[] highest, List<Dormer> dormers) {
        Long2ByteOpenHashMap walls = new Long2ByteOpenHashMap();
        int base = grid.footprint.baseY();
        RoofType type = settings.type();
        if (settings.closedGables() && type.hasGables()) {
            for (int cz = 0; cz < grid.cellsZ; cz++) {
                for (int cx = 0; cx < grid.cellsX; cx++) {
                    if (!grid.isWall(cx, cz) || lowest[cz * grid.cellsX + cx] == Integer.MAX_VALUE) {
                        continue;
                    }
                    // En el de un agua se cierra todo el contorno: en el lado bajo no queda hueco que cerrar, y
                    // en plantas en L o U los bordes que miran al lado bajo pero quedan altos también se tapan.
                    boolean gable = type == RoofType.SHED;
                    for (int direction = 0; direction < 4; direction++) {
                        gable |= crossing(grid, cx, cz, direction)
                                && grid.edgeType(cx, cz, direction) == RoofGrid.GABLE;
                    }
                    if (gable) {
                        fill(walls, roof, grid.worldX(cx), grid.worldZ(cz), base, lowest[cz * grid.cellsX + cx] - 1);
                    }
                }
            }
        }
        if (type == RoofType.GABLE || type == RoofType.GABLET || type == RoofType.HIP || type == RoofType.MANSARD
                || type == RoofType.SHED) {
            // Escalones del tejado (holandés, buhardillas): la columna alta se cierra hasta lo alto de la vecina.
            for (int cz = 0; cz < grid.cellsZ; cz++) {
                for (int cx = 0; cx < grid.cellsX; cx++) {
                    int cell = cz * grid.cellsX + cx;
                    if (lowest[cell] == Integer.MAX_VALUE) {
                        continue;
                    }
                    int lowNeighbour = Integer.MAX_VALUE;
                    for (int direction = 0; direction < 4; direction++) {
                        int nx = cx + RoofGrid.DX[direction];
                        int nz = cz + RoofGrid.DZ[direction];
                        if (nx < 0 || nz < 0 || nx >= grid.cellsX || nz >= grid.cellsZ) {
                            continue;
                        }
                        int neighbourTop = highest[nz * grid.cellsX + nx];
                        if (neighbourTop != Integer.MIN_VALUE && neighbourTop < lowest[cell]) {
                            lowNeighbour = Math.min(lowNeighbour, neighbourTop);
                        }
                    }
                    if (lowNeighbour != Integer.MAX_VALUE) {
                        fill(walls, roof, grid.worldX(cx), grid.worldZ(cz), Math.max(lowNeighbour, base),
                                lowest[cell] - 1);
                    }
                }
            }
        }
        for (Dormer dormer : dormers) {
            int lx = dormer.lateralX();
            int lz = dormer.lateralZ();
            for (int lateral = -DORMER_HALF_WIDTH; lateral <= DORMER_HALF_WIDTH; lateral++) {
                int cx = dormer.centerX() + lateral * lx;
                int cz = dormer.centerZ() + lateral * lz;
                int low = lowest[cz * grid.cellsX + cx];
                if (low == Integer.MAX_VALUE) {
                    continue;
                }
                fill(walls, roof, grid.worldX(cx), grid.worldZ(cz), base, low - 1);
                if (lateral == 0) {
                    byte window = lx == 1 ? RoofResult.WINDOW_X : RoofResult.WINDOW_Z;
                    for (int y = base; y < Math.min(base + DORMER_WALL, low); y++) {
                        walls.put(PackedBlockPlan.pack(grid.worldX(cx), y, grid.worldZ(cz)), window);
                    }
                }
            }
        }
        if (type == RoofType.FLAT && settings.parapet() > 0) {
            int from = base + settings.thickness();
            for (int cz = 0; cz < grid.cellsZ; cz++) {
                for (int cx = 0; cx < grid.cellsX; cx++) {
                    if (grid.isExtendedBorder(cx, cz)) {
                        fill(walls, roof, grid.worldX(cx), grid.worldZ(cz), from, from + settings.parapet() - 1);
                    }
                }
            }
        }
        return walls;
    }

    private static void fill(Long2ByteOpenHashMap walls, Long2IntOpenHashMap roof, int x, int z, int from, int to) {
        for (int y = from; y <= to; y++) {
            long key = PackedBlockPlan.pack(x, y, z);
            if (!roof.containsKey(key) && !walls.containsKey(key)) {
                walls.put(key, RoofResult.WALL);
            }
        }
    }

    // ---------------------------------------------------------------- resultado

    private static boolean air(RoofWorld world, long key) {
        return world.air(PackedBlockPlan.unpackX(key), PackedBlockPlan.unpackY(key), PackedBlockPlan.unpackZ(key));
    }

    private static RoofResult assemble(RoofPieces.Solved solved, Long2ByteOpenHashMap walls, LongOpenHashSet ridge,
                                       int dormers, List<String> notes) {
        LongArrayList keys = new LongArrayList(solved.pieces().size() + walls.size());
        keys.addAll(solved.pieces().keySet());
        for (long key : walls.keySet()) {
            if (!solved.pieces().containsKey(key)) {
                keys.add(key);
            }
        }
        long[] positions = keys.toLongArray();
        Arrays.sort(positions);
        byte[] pieces = new byte[positions.length];
        byte[] materials = new byte[positions.length];
        int minimumY = Integer.MAX_VALUE;
        int maximumY = Integer.MIN_VALUE;
        for (int index = 0; index < positions.length; index++) {
            long key = positions[index];
            if (solved.pieces().containsKey(key)) {
                pieces[index] = solved.pieces().get(key);
                materials[index] = ridge.contains(key) ? RoofResult.RIDGE : RoofResult.ROOF;
                int y = PackedBlockPlan.unpackY(key);
                minimumY = Math.min(minimumY, y);
                maximumY = Math.max(maximumY, y);
            } else {
                pieces[index] = SmoothPiece.FULL;
                materials[index] = walls.get(key);
            }
        }
        if (minimumY == Integer.MAX_VALUE) {
            minimumY = 0;
            maximumY = 0;
        }
        return new RoofResult(positions, pieces, materials, minimumY, maximumY, dormers, solved.mismatches(), notes);
    }

    private static double square(double value) {
        return value * value;
    }

    private interface IndexedValue {
        void accept(int index, double value);
    }

    private static void forEach(double[] values, IndexedValue action) {
        for (int index = 0; index < values.length; index++) {
            if (!Double.isNaN(values[index])) {
                action.accept(index, values[index]);
            }
        }
    }
}
