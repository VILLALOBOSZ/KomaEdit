package com.komainu.moxia.terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

/** Mundo de texto para probar las herramientas de terreno sin Minecraft. */
final class FakeTerrain implements TerrainView, TerrainWriter.BlockSink {
    static final Runnable NO_CANCEL = () -> {
    };

    private final int minimumX;
    private final int minimumY;
    private final int minimumZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final short[] cells;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Short> ids = new HashMap<>();
    private int[] writableBox;
    private java.util.function.Predicate<String> mask;
    private int writes;

    FakeTerrain(int minimumX, int minimumY, int minimumZ, int sizeX, int sizeY, int sizeZ) {
        this.minimumX = minimumX;
        this.minimumY = minimumY;
        this.minimumZ = minimumZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        cells = new short[sizeX * sizeY * sizeZ];
        id("minecraft:air");
    }

    /** Terreno de columnas: piedra, tres de tierra y hierba arriba hasta la altura que diga la función. */
    static FakeTerrain columns(int sizeX, int sizeY, int sizeZ, IntBinaryOperator height) {
        FakeTerrain terrain = new FakeTerrain(0, 0, 0, sizeX, sizeY, sizeZ);
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int top = Math.clamp(height.applyAsInt(x, z), 0, sizeY - 1);
                for (int y = 0; y <= top; y++) {
                    String block = y == top ? "minecraft:grass_block" : y >= top - 3 ? "minecraft:dirt"
                            : "minecraft:stone";
                    terrain.put(x, y, z, block);
                }
            }
        }
        return terrain;
    }

    FakeTerrain copy() {
        FakeTerrain copy = new FakeTerrain(minimumX, minimumY, minimumZ, sizeX, sizeY, sizeZ);
        for (int index = 0; index < cells.length; index++) {
            int x = index % sizeX;
            int rest = index / sizeX;
            int z = rest % sizeZ;
            int y = rest / sizeZ;
            String block = palette.get(cells[index]);
            if (!block.equals("minecraft:air")) {
                copy.put(minimumX + x, minimumY + y, minimumZ + z, block);
            }
        }
        copy.writableBox = writableBox == null ? null : writableBox.clone();
        copy.mask = mask;
        return copy;
    }

    /** Solo se puede escribir dentro de esta caja {minX, minY, minZ, maxX, maxY, maxZ}. */
    FakeTerrain writable(int[] box) {
        writableBox = box.clone();
        return this;
    }

    /** Máscara de superficie por id de bloque. */
    FakeTerrain mask(java.util.function.Predicate<String> value) {
        mask = value;
        return this;
    }

    private short id(String block) {
        return ids.computeIfAbsent(block, key -> {
            palette.add(key);
            return (short) (palette.size() - 1);
        });
    }

    private int index(int x, int y, int z) {
        return ((y - minimumY) * sizeZ + (z - minimumZ)) * sizeX + (x - minimumX);
    }

    void put(int x, int y, int z, String block) {
        if (contains(x, y, z)) {
            cells[index(x, y, z)] = id(block);
        }
    }

    @Override
    public void set(int x, int y, int z, String blockId) {
        if (!writable(x, y, z)) {
            throw new AssertionError("Escritura fuera de lo editable en " + x + " " + y + " " + z);
        }
        if (!blockId(x, y, z).equals(blockId)) {
            writes++;
            put(x, y, z, blockId);
        }
    }

    int writes() {
        return writes;
    }

    @Override
    public int minimumX() {
        return minimumX;
    }

    @Override
    public int maximumX() {
        return minimumX + sizeX - 1;
    }

    @Override
    public int minimumY() {
        return minimumY;
    }

    @Override
    public int maximumY() {
        return minimumY + sizeY - 1;
    }

    @Override
    public int minimumZ() {
        return minimumZ;
    }

    @Override
    public int maximumZ() {
        return minimumZ + sizeZ - 1;
    }

    @Override
    public String blockId(int x, int y, int z) {
        return contains(x, y, z) ? palette.get(cells[index(x, y, z)]) : "minecraft:air";
    }

    @Override
    public TerrainCell cell(int x, int y, int z) {
        String block = blockId(x, y, z);
        boolean air = block.equals("minecraft:air");
        boolean liquid = block.equals("minecraft:water") || block.equals("minecraft:lava");
        boolean solid = !air && !liquid && !block.contains("short_grass") && !block.contains("poppy")
                && !block.equals("minecraft:snow");
        return TerrainCell.classify(block, air, liquid, solid);
    }

    @Override
    public boolean writable(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return false;
        }
        return writableBox == null || (x >= writableBox[0] && y >= writableBox[1] && z >= writableBox[2]
                && x <= writableBox[3] && y <= writableBox[4] && z <= writableBox[5]);
    }

    @Override
    public boolean maskAllows(int x, int y, int z) {
        return mask == null || mask.test(blockId(x, y, z));
    }

    /** Altura del suelo más alto de la columna, o -1. */
    int top(int x, int z) {
        for (int y = maximumY(); y >= minimumY; y--) {
            if (cell(x, y, z) == TerrainCell.GROUND) {
                return y;
            }
        }
        return -1;
    }

    /** Altura del líquido más alto de la columna, o -1. */
    int liquidTop(int x, int z) {
        for (int y = maximumY(); y >= minimumY; y--) {
            TerrainCell cell = cell(x, y, z);
            if (cell.liquid()) {
                return y;
            }
            if (cell == TerrainCell.GROUND) {
                return -1;
            }
        }
        return -1;
    }

    /** Bloques de suelo en total. */
    long groundBlocks() {
        long count = 0;
        for (short cell : cells) {
            String block = palette.get(cell);
            if (!block.equals("minecraft:air") && !block.equals("minecraft:water") && !block.equals("minecraft:lava")) {
                count++;
            }
        }
        return count;
    }

    int sizeX() {
        return sizeX;
    }

    int sizeY() {
        return sizeY;
    }

    int sizeZ() {
        return sizeZ;
    }
}
