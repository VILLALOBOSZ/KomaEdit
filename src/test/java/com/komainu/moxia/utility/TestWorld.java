package com.komainu.moxia.utility;

import com.komainu.moxia.region.RegionSnapshot;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mundo de texto para probar la gravedad, la nieve y el agua sin Minecraft: cada posición guarda un estado como
 * texto ({@code minecraft:oak_stairs[waterlogged=false]}) y se convierte en una {@link RegionSnapshot}.
 */
public final class TestWorld {
    public static final String AIR = "minecraft:air";
    public static final String WATER = "minecraft:water[level=0]";
    public static final String LAVA = "minecraft:lava[level=0]";
    /** Bloques sin cara de arriba entera (o que no son sólidos) en estas pruebas. */
    private static final Set<String> NOT_FULL = Set.of("minecraft:air", "minecraft:water", "minecraft:lava",
            "minecraft:short_grass", "minecraft:fern", "minecraft:dandelion", "minecraft:oak_fence",
            "minecraft:glass_pane", "minecraft:snow", "minecraft:torch", "minecraft:kelp", "minecraft:seagrass",
            "minecraft:bubble_column", "minecraft:dirt_path", "minecraft:farmland");
    private static final Set<String> LIQUIDS = Set.of("minecraft:water", "minecraft:lava", "minecraft:bubble_column",
            "minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass", "minecraft:tall_seagrass");

    public final int minX;
    public final int minY;
    public final int minZ;
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    private final String[] blocks;
    private final boolean[] selected;
    private RegionSnapshot snapshot;

    public TestWorld(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = new String[sizeX * sizeY * sizeZ];
        this.selected = new boolean[blocks.length];
        Arrays.fill(blocks, AIR);
    }

    public int maxX() {
        return minX + sizeX - 1;
    }

    public int maxY() {
        return minY + sizeY - 1;
    }

    public int maxZ() {
        return minZ + sizeZ - 1;
    }

    public TestWorld set(int x, int y, int z, String block) {
        blocks[index(x, y, z)] = normalize(block);
        snapshot = null;
        return this;
    }

    public TestWorld fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    set(x, y, z, block);
                }
            }
        }
        return this;
    }

    public TestWorld select(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int y = y1; y <= y2; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    selected[index(x, y, z)] = true;
                }
            }
        }
        snapshot = null;
        return this;
    }

    /** Todo menos un borde de {@code margin} bloques (el margen capturado alrededor de la selección). */
    public TestWorld selectInside(int margin) {
        return select(minX + margin, minY + margin, minZ + margin, maxX() - margin, maxY() - margin, maxZ() - margin);
    }

    public String at(int x, int y, int z) {
        return blocks[index(x, y, z)];
    }

    public String id(int x, int y, int z) {
        String block = at(x, y, z);
        int bracket = block.indexOf('[');
        return bracket < 0 ? block : block.substring(0, bracket);
    }

    public long count(String id) {
        return Arrays.stream(blocks).filter(block -> block.equals(id) || block.startsWith(id + "[")).count();
    }

    public boolean selected(int x, int y, int z) {
        return selected[index(x, y, z)];
    }

    public RegionSnapshot snapshot() {
        if (snapshot != null) {
            return snapshot;
        }
        Map<String, Integer> ids = new LinkedHashMap<>();
        ids.put(AIR, 0);
        RegionSnapshot.Builder builder = RegionSnapshot.builder(minX, minY, minZ, sizeX, sizeY, sizeZ);
        for (int y = minY; y <= maxY(); y++) {
            for (int z = minZ; z <= maxZ(); z++) {
                for (int x = minX; x <= maxX(); x++) {
                    String block = at(x, y, z);
                    int rawId = ids.computeIfAbsent(block, key -> ids.size());
                    if (!builder.knows(rawId)) {
                        builder.state(info(rawId, block));
                    }
                    builder.set(x, y, z, rawId, selected(x, y, z));
                }
            }
        }
        snapshot = builder.build();
        return snapshot;
    }

    /** Clasificación de texto con las caras de arriba de estas pruebas. */
    public BlockKinds kinds() {
        return BlockKinds.fromText(snapshot(), (id, properties) -> !NOT_FULL.contains(id) && !id.endsWith("_fence")
                && !(id.endsWith("_slab") && properties.contains("type=bottom")));
    }

    /** Estado de texto de un id crudo de la última foto. */
    public String state(int rawId) {
        RegionSnapshot.StateInfo info = snapshot().stateInfo(rawId);
        return info.properties().isEmpty() ? info.blockId() : info.blockId() + "[" + info.properties() + "]";
    }

    public static String withProperty(String block, String key, String value) {
        int bracket = block.indexOf('[');
        if (bracket < 0) {
            return block;
        }
        String properties = block.substring(bracket + 1, block.length() - 1);
        StringBuilder result = new StringBuilder(block.substring(0, bracket)).append('[');
        boolean first = true;
        for (String pair : properties.split(",")) {
            String name = pair.substring(0, pair.indexOf('='));
            result.append(first ? "" : ",").append(name).append('=')
                    .append(name.equals(key) ? value : pair.substring(pair.indexOf('=') + 1));
            first = false;
        }
        return result.append(']').toString();
    }

    private static RegionSnapshot.StateInfo info(int rawId, String block) {
        int bracket = block.indexOf('[');
        String id = bracket < 0 ? block : block.substring(0, bracket);
        String properties = bracket < 0 ? "" : block.substring(bracket + 1, block.length() - 1);
        boolean air = id.equals(AIR);
        boolean liquid = LIQUIDS.contains(id) || properties.contains("waterlogged=true");
        boolean solid = !air && !NOT_FULL.contains(id) && !LIQUIDS.contains(id);
        return new RegionSnapshot.StateInfo(rawId, null, id, air, liquid, solid, properties);
    }

    private static String normalize(String block) {
        return block.contains(":") ? block : "minecraft:" + block;
    }

    private int index(int x, int y, int z) {
        if (x < minX || y < minY || z < minZ || x > maxX() || y > maxY() || z > maxZ()) {
            throw new IndexOutOfBoundsException(x + "," + y + "," + z);
        }
        return ((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX);
    }
}
