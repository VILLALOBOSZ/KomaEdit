package com.komainu.moxia.terrain;

/**
 * Lectura de las columnas de una zona tal como estaban: altura del suelo, el líquido que lo cubre y entre qué
 * alturas se puede escribir. De aquí sale el {@link HeightField} que modifican las herramientas y con lo que
 * {@link TerrainWriter} compara al escribir.
 */
public final class TerrainColumns {
    public static final int NONE = Integer.MIN_VALUE;

    private final TerrainView view;
    private final int minimumX;
    private final int minimumZ;
    private final int sizeX;
    private final int sizeZ;
    private final int[] surface;
    private final int[] liquidTop;
    private final String[] liquidId;
    private final int[] lowestWritable;
    private final int[] highestWritable;
    private final boolean[] editable;

    private TerrainColumns(TerrainView view, int minimumX, int minimumZ, int sizeX, int sizeZ) {
        this.view = view;
        this.minimumX = minimumX;
        this.minimumZ = minimumZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        int columns = Math.multiplyExact(sizeX, sizeZ);
        surface = new int[columns];
        liquidTop = new int[columns];
        liquidId = new String[columns];
        lowestWritable = new int[columns];
        highestWritable = new int[columns];
        editable = new boolean[columns];
    }

    /** Lee toda la caja de la vista. */
    public static TerrainColumns read(TerrainView view, Runnable checkCancelled) {
        return read(view, view.minimumX(), view.minimumZ(), view.maximumX(), view.maximumZ(), checkCancelled);
    }

    /**
     * Lee las columnas de un rectángulo. Una columna es editable si tiene suelo, ese bloque de suelo se puede
     * escribir y cumple la máscara.
     */
    public static TerrainColumns read(TerrainView view, int minimumX, int minimumZ, int maximumX, int maximumZ,
                                      Runnable checkCancelled) {
        int lowX = Math.max(minimumX, view.minimumX());
        int lowZ = Math.max(minimumZ, view.minimumZ());
        int highX = Math.min(maximumX, view.maximumX());
        int highZ = Math.min(maximumZ, view.maximumZ());
        if (highX < lowX || highZ < lowZ) {
            throw new IllegalArgumentException("La zona está fuera de lo capturado");
        }
        TerrainColumns columns = new TerrainColumns(view, lowX, lowZ, highX - lowX + 1, highZ - lowZ + 1);
        int bottom = view.minimumY();
        int top = view.maximumY();
        for (int z = lowZ; z <= highZ; z++) {
            for (int x = lowX; x <= highX; x++) {
                checkCancelled.run();
                int index = columns.index(x, z);
                int ground = NONE;
                int liquid = NONE;
                String liquidBlock = null;
                int lowest = NONE;
                int highest = NONE;
                for (int y = top; y >= bottom; y--) {
                    if (view.writable(x, y, z)) {
                        if (highest == NONE) {
                            highest = y;
                        }
                        lowest = y;
                    }
                    if (ground != NONE) {
                        continue;
                    }
                    TerrainCell cell = view.cell(x, y, z);
                    if (cell == TerrainCell.GROUND) {
                        ground = y;
                    } else if (cell.liquid() && liquid == NONE) {
                        liquid = y;
                        liquidBlock = cell == TerrainCell.LAVA ? "minecraft:lava" : "minecraft:water";
                    }
                }
                columns.surface[index] = ground;
                columns.liquidTop[index] = ground == NONE ? NONE : liquid;
                columns.liquidId[index] = ground == NONE ? null : liquidBlock;
                columns.lowestWritable[index] = lowest;
                columns.highestWritable[index] = highest;
                columns.editable[index] = ground != NONE && view.writable(x, ground, z)
                        && view.maskAllows(x, ground, z);
            }
        }
        return columns;
    }

    public TerrainView view() {
        return view;
    }

    public int minimumX() {
        return minimumX;
    }

    public int minimumZ() {
        return minimumZ;
    }

    public int maximumX() {
        return minimumX + sizeX - 1;
    }

    public int maximumZ() {
        return minimumZ + sizeZ - 1;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int index(int x, int z) {
        if (x < minimumX || x >= minimumX + sizeX || z < minimumZ || z >= minimumZ + sizeZ) {
            return -1;
        }
        return (z - minimumZ) * sizeX + (x - minimumX);
    }

    /** Altura del bloque de suelo más alto, o {@link #NONE}. */
    public int surface(int x, int z) {
        int index = index(x, z);
        return index < 0 ? NONE : surface[index];
    }

    /** Altura del líquido más alto encima del suelo (un lago, el mar), o {@link #NONE}. */
    public int liquidTop(int x, int z) {
        int index = index(x, z);
        return index < 0 ? NONE : liquidTop[index];
    }

    public String liquidId(int x, int z) {
        int index = index(x, z);
        return index < 0 ? null : liquidId[index];
    }

    public int lowestWritable(int x, int z) {
        int index = index(x, z);
        return index < 0 ? NONE : lowestWritable[index];
    }

    public int highestWritable(int x, int z) {
        int index = index(x, z);
        return index < 0 ? NONE : highestWritable[index];
    }

    public boolean editable(int x, int z) {
        int index = index(x, z);
        return index >= 0 && editable[index];
    }

    /**
     * El mapa de alturas de partida. Una columna editable puede bajar hasta un bloque por debajo de lo más bajo que
     * se puede escribir (lo vacía entera) y subir hasta lo más alto.
     */
    public HeightField heights() {
        HeightField field = new HeightField(minimumX, minimumZ, sizeX, sizeZ);
        for (int z = minimumZ; z < minimumZ + sizeZ; z++) {
            for (int x = minimumX; x < minimumX + sizeX; x++) {
                int index = index(x, z);
                double height = surface[index] == NONE ? Double.NaN : surface[index];
                boolean canEdit = editable[index];
                double low = canEdit ? lowestWritable[index] - 1.0 : height;
                double high = canEdit ? highestWritable[index] : height;
                field.define(x, z, height, canEdit, low, high);
            }
        }
        return field;
    }
}
