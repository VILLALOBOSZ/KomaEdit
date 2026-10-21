package com.komainu.moxia.brush.paint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

/**
 * Terreno de prueba para el pincel de pintura: columnas de bloques sobre un mapa de alturas, con la paleta por ids.
 * Se le pueden aplicar los cambios de una pasada para pintar por encima en la siguiente.
 */
final class FakePaintWorld implements PaintWorld {
    private final int minimumX;
    private final int minimumZ;
    private final int maximumX;
    private final int maximumZ;
    private final int minimumY;
    private final int maximumY;
    private final Map<Long, String> blocks = new HashMap<>();
    private final List<String> palette;
    private Predicate<String> allowed = id -> true;

    private FakePaintWorld(int half, int minimumY, int maximumY, List<String> palette) {
        this.minimumX = -half;
        this.minimumZ = -half;
        this.maximumX = half;
        this.maximumZ = half;
        this.minimumY = minimumY;
        this.maximumY = maximumY;
        this.palette = List.copyOf(palette);
    }

    /** Columnas de {@code fill} hasta la altura {@code height(x, z)}, con {@code top} encima de cada una. */
    static FakePaintWorld terrain(int half, IntBinaryOperator height, String top, String fill, List<String> palette) {
        FakePaintWorld world = new FakePaintWorld(half, 0, 96, palette);
        for (int z = -half; z <= half; z++) {
            for (int x = -half; x <= half; x++) {
                int surface = Math.clamp(height.applyAsInt(x, z), 1, 90);
                for (int y = 0; y <= surface; y++) {
                    world.set(x, y, z, y == surface ? top : fill);
                }
            }
        }
        return world;
    }

    /** Suelo llano de hierba a Y 10. */
    static FakePaintWorld flat(int half, List<String> palette) {
        return terrain(half, (x, z) -> 10, "grass_block", "dirt", palette);
    }

    /** Colina redonda con una ladera muy empinada al este: hay llano, cuesta suave y pared. */
    static FakePaintWorld hill(int half, List<String> palette) {
        return terrain(half, FakePaintWorld::hillHeight, "grass_block", "dirt", palette);
    }

    static int hillHeight(int x, int z) {
        double r = Math.sqrt(x * x + z * z);
        double dome = 12.0 * Math.exp(-(r * r) / (2.0 * 10.0 * 10.0));
        // Al este se corta en un escarpe.
        double cliff = x > 6 ? -Math.min(12.0, (x - 6) * 3.0) * Math.exp(-(z * z) / 200.0) : 0.0;
        return (int) Math.round(10.0 + Math.max(0.0, dome + cliff));
    }

    FakePaintWorld set(int x, int y, int z, String id) {
        if (id == null) {
            blocks.remove(key(x, y, z));
        } else {
            blocks.put(key(x, y, z), id);
        }
        return this;
    }

    String block(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    FakePaintWorld only(Predicate<String> allowedIds) {
        this.allowed = allowedIds;
        return this;
    }

    /** Pone el tono {@code index} conservando la forma (una escalera de piedra pasa a escalera de andesita). */
    void apply(int x, int y, int z, int index) {
        String current = block(x, y, z);
        String material = palette.get(index);
        if (current != null && current.endsWith("_stairs")) {
            material = material + "_stairs";
        } else if (current != null && current.endsWith("_slab")) {
            material = material + "_slab";
        }
        set(x, y, z, material);
    }

    /** La Y del bloque más alto de la columna, o -1. */
    int top(int x, int z) {
        for (int y = maximumY; y >= minimumY; y--) {
            if (blocks.containsKey(key(x, y, z))) {
                return y;
            }
        }
        return -1;
    }

    int minimumX() {
        return minimumX;
    }

    int maximumX() {
        return maximumX;
    }

    int minimumZ() {
        return minimumZ;
    }

    int maximumZ() {
        return maximumZ;
    }

    List<String> palette() {
        return palette;
    }

    @Override
    public Kind kind(int x, int y, int z) {
        if (x < minimumX || x > maximumX || z < minimumZ || z > maximumZ || y < minimumY || y > maximumY) {
            return Kind.BLOCKED;
        }
        String id = block(x, y, z);
        if (id == null || id.endsWith("_leaves") || id.equals("short_grass")) {
            return Kind.OPEN;
        }
        if (id.endsWith("_stairs") || id.endsWith("_slab")) {
            return Kind.SHAPED;
        }
        return Kind.GROUND;
    }

    @Override
    public int paletteIndex(int x, int y, int z) {
        String id = block(x, y, z);
        if (id == null) {
            return -1;
        }
        String base = id.replace("_stairs", "").replace("_slab", "");
        return palette.indexOf(base);
    }

    @Override
    public boolean allowed(int x, int y, int z) {
        String id = block(x, y, z);
        return id != null && allowed.test(id);
    }

    @Override
    public int minimumY() {
        return minimumY;
    }

    @Override
    public int maximumY() {
        return maximumY;
    }

    private static long key(int x, int y, int z) {
        return PaintPainter.pack(x, y, z);
    }
}
