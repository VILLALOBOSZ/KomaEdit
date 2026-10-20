package com.komainu.moxia.terrain;

/**
 * Vuelve a pasar un mapa de alturas a bloques comparándolo con las columnas originales:
 *
 * <ul>
 *     <li>donde sube, rellena con la paleta (la vieja superficie pasa a ser relleno);</li>
 *     <li>donde baja, vacía (o deja el agua que hubiera encima), quita la vegetación que quedaría flotando y
 *     repinta la superficie nueva;</li>
 *     <li>el líquido que pida la herramienta (un río) llena hasta su nivel.</li>
 * </ul>
 *
 * <p>Solo escribe en columnas editables y en posiciones que la vista deja escribir; las cuevas bajo la superficie
 * no se rellenan.</p>
 */
public final class TerrainWriter {
    /** Desnivel con la vecina más baja a partir del cual la cara al aire se pinta como pared de roca. */
    static final int MIN_WALL = 3;

    private TerrainWriter() {
    }

    /** Recibe cada bloque a escribir. Puede ignorar los que ya son así. */
    @FunctionalInterface
    public interface BlockSink {
        void set(int x, int y, int z, String blockId);
    }

    /** Líquido que pide la herramienta en cada columna: nivel de su superficie y bloque. */
    public interface Fluids {
        /** Altura de la superficie del líquido en la columna, o {@link TerrainColumns#NONE}. */
        int level(int x, int z);

        String blockId();
    }

    /**
     * @param repaintAll repinta también las columnas que no cambian de altura (la orden de pintar)
     * @return columnas que han cambiado
     */
    public static int write(TerrainColumns original, HeightField target, Fluids fluids, SurfacePalette palette,
                            boolean repaintAll, BlockSink sink, Runnable checkCancelled) {
        TerrainView view = original.view();
        boolean[] changedMask = new boolean[original.sizeX() * original.sizeZ()];
        int changedColumns = 0;
        for (int z = original.minimumZ(); z <= original.maximumZ(); z++) {
            for (int x = original.minimumX(); x <= original.maximumX(); x++) {
                checkCancelled.run();
                if (!original.editable(x, z) || !target.editable(x, z)) {
                    continue;
                }
                int oldTop = original.surface(x, z);
                double wanted = target.get(x, z);
                if (oldTop == TerrainColumns.NONE || Double.isNaN(wanted)) {
                    continue;
                }
                int newTop = (int) Math.round(wanted);
                int fluidLevel = fluids == null ? TerrainColumns.NONE : fluids.level(x, z);
                boolean changed = newTop != oldTop || (fluidLevel != TerrainColumns.NONE && fluidLevel > newTop);
                if (!changed && !repaintAll) {
                    continue;
                }
                if (changed) {
                    changedColumns++;
                    changedMask[original.index(x, z)] = true;
                }
                writeColumn(view, original, target, fluids, fluidLevel, palette, x, z, oldTop, newTop, sink);
            }
        }
        if (!palette.keepsExisting()) {
            paintExposedWalls(original, target, palette, changedMask, sink, checkCancelled);
        }
        return changedColumns;
    }

    /**
     * Las paredes que quedan al aire junto a una columna que ha bajado (la orilla de un cañón, el corte de una
     * rampa) se pintan como pendiente: piedra o estratos en lugar del relleno o la piedra de dentro.
     */
    private static void paintExposedWalls(TerrainColumns original, HeightField target, SurfacePalette palette,
                                          boolean[] changedMask, BlockSink sink, Runnable checkCancelled) {
        TerrainView view = original.view();
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int z = original.minimumZ(); z <= original.maximumZ(); z++) {
            for (int x = original.minimumX(); x <= original.maximumX(); x++) {
                checkCancelled.run();
                if (!original.editable(x, z) || !target.editable(x, z)) {
                    continue;
                }
                double own = target.get(x, z);
                if (Double.isNaN(own)) {
                    continue;
                }
                int top = (int) Math.round(own);
                int lowestNeighbour = Integer.MAX_VALUE;
                boolean nearChange = changedMask[original.index(x, z)];
                for (int[] offset : offsets) {
                    int index = original.index(x + offset[0], z + offset[1]);
                    if (index < 0) {
                        continue;
                    }
                    nearChange |= changedMask[index];
                    double neighbour = target.get(x + offset[0], z + offset[1]);
                    if (!Double.isNaN(neighbour)) {
                        lowestNeighbour = Math.min(lowestNeighbour, (int) Math.round(neighbour));
                    }
                }
                // Un escalón de uno o dos bloques se queda con su tierra; a partir de tres es una pared.
                if (!nearChange || lowestNeighbour > top - MIN_WALL) {
                    continue;
                }
                SurfacePalette.Column wall = new SurfacePalette.Column(top, 90.0, TerrainColumns.NONE,
                        null, null);
                for (int y = top - 1; y > lowestNeighbour; y--) {
                    if (view.writable(x, y, z) && view.cell(x, y, z) == TerrainCell.GROUND) {
                        sink.set(x, y, z, palette.material(x, y, z, 0, wall));
                    }
                }
            }
        }
    }

    private static void writeColumn(TerrainView view, TerrainColumns original, HeightField target, Fluids fluids,
                                    int fluidLevel, SurfacePalette palette, int x, int z, int oldTop, int newTop,
                                    BlockSink sink) {
        int originalLiquid = original.liquidTop(x, z);
        String originalLiquidId = original.liquidId(x, z);
        int waterLevel = fluidLevel != TerrainColumns.NONE ? fluidLevel : originalLiquid;
        String originalTop = view.blockId(x, oldTop, z);
        String originalBelow = view.cell(x, oldTop - 1, z) == TerrainCell.GROUND
                ? view.blockId(x, oldTop - 1, z) : originalTop;
        SurfacePalette.Column column = new SurfacePalette.Column(newTop, target.slopeDegrees(x, z), waterLevel,
                originalTop, originalBelow);
        int fillDepth = palette.fillDepth();

        // Por encima de la superficie nueva: vaciar lo que era suelo, dejar el líquido y quitar lo suelto.
        int clearTop = Math.max(oldTop, fluidLevel);
        if (newTop < oldTop) {
            int y = oldTop + 1;
            while (view.contains(x, y, z) && view.cell(x, y, z) == TerrainCell.PLANT) {
                y++;
            }
            clearTop = Math.max(clearTop, y - 1);
        }
        for (int y = newTop + 1; y <= clearTop; y++) {
            if (!view.writable(x, y, z)) {
                continue;
            }
            if (fluidLevel != TerrainColumns.NONE && y <= fluidLevel) {
                sink.set(x, y, z, fluids.blockId());
            } else if (y <= oldTop) {
                boolean underOldLiquid = originalLiquid != TerrainColumns.NONE && y <= originalLiquid;
                sink.set(x, y, z, underOldLiquid ? originalLiquidId : "minecraft:air");
            } else {
                TerrainCell cell = view.cell(x, y, z);
                if (cell == TerrainCell.PLANT) {
                    boolean underOldLiquid = originalLiquid != TerrainColumns.NONE && y <= originalLiquid;
                    sink.set(x, y, z, underOldLiquid ? originalLiquidId : "minecraft:air");
                } else if (cell == TerrainCell.AIR && fluidLevel == TerrainColumns.NONE) {
                    break;
                }
            }
        }

        // La superficie nueva y sus capas: lo que se rellena entero y lo que se repinta si es suelo.
        int bottom = Math.min(newTop, oldTop) - fillDepth;
        for (int y = newTop; y >= bottom; y--) {
            if (!view.writable(x, y, z)) {
                continue;
            }
            int depth = newTop - y;
            boolean filled = y > oldTop;
            if (!filled && view.cell(x, y, z) != TerrainCell.GROUND) {
                continue;
            }
            // La vieja superficie enterrada sí cambia siempre: la hierba no se queda bajo tierra.
            if (!filled && depth > fillDepth && y != oldTop) {
                continue;
            }
            sink.set(x, y, z, palette.material(x, y, z, depth, column));
        }
    }
}
