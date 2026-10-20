package com.komainu.moxia.blueprint;

import com.komainu.moxia.color.BlockColorCatalog;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/** Planos de prueba pintados con los colores medios reales de los bloques (la tabla de {@code smooth/colors.txt}). */
public final class BlueprintFixtures {
    static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    static final UUID BRUNO = UUID.fromString("00000000-0000-0000-0000-0000000b0b00");

    private BlueprintFixtures() {
    }

    static int color(String block) {
        BlockColorCatalog.Measured measured = BlockColorCatalog.measured().get("minecraft:" + block);
        if (measured == null) {
            throw new IllegalArgumentException("Sin color medido: " + block);
        }
        return measured.rgb();
    }

    static BlueprintMeta meta(String name, UUID owner, String author, List<String> tags, int x, int y, int z,
                              int blocks) {
        return new BlueprintMeta(name, BlueprintScope.PLAYER, owner, author, 1_700_000_000_000L, 1_700_000_000_000L,
                x, y, z, blocks, tags, "", "sponge.3", "", 0L);
    }

    /** Bytes cualquiera con forma de esquema: la biblioteca no los interpreta. */
    static byte[] schematic(String seed) {
        byte[] data = new byte[512 + seed.length() * 7];
        new SplittableRandom(seed.hashCode()).nextBytes(data);
        return data;
    }

    /** Roca: un elipsoide irregular de piedra, andesita y musgo. */
    public static BlueprintThumbnail.Grid rock(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        BlueprintThumbnail.Grid grid = new BlueprintThumbnail.Grid(7, 5, 6);
        int stone = color("stone");
        int andesite = color("andesite");
        int moss = color("moss_block");
        for (int y = 0; y < 5; y++) {
            for (int z = 0; z < 6; z++) {
                for (int x = 0; x < 7; x++) {
                    double dx = (x - 3) / 3.4;
                    double dy = (y - 0.5) / 4.0;
                    double dz = (z - 2.5) / 3.0;
                    if (dx * dx + dy * dy + dz * dz < 1.0 - random.nextDouble() * 0.25) {
                        grid.set(x, y, z, y >= 3 && random.nextDouble() < 0.6 ? moss
                                : random.nextBoolean() ? stone : andesite);
                    }
                }
            }
        }
        return grid;
    }

    /** Arbusto: hojas de roble en bola con un tronco corto. */
    public static BlueprintThumbnail.Grid bush() {
        BlueprintThumbnail.Grid grid = new BlueprintThumbnail.Grid(5, 4, 5);
        grid.set(2, 0, 2, color("oak_log"));
        int leaves = color("oak_leaves");
        for (int y = 1; y < 4; y++) {
            for (int z = 0; z < 5; z++) {
                for (int x = 0; x < 5; x++) {
                    double distance = Math.hypot(x - 2, z - 2) + Math.abs(y - 1.8) * 0.9;
                    if (distance < 2.4) {
                        grid.set(x, y, z, leaves);
                    }
                }
            }
        }
        return grid;
    }

    /** Ruina: muros de ladrillo de piedra con huecos, suelo y una columna caída. */
    public static BlueprintThumbnail.Grid ruin(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        BlueprintThumbnail.Grid grid = new BlueprintThumbnail.Grid(9, 6, 9);
        int bricks = color("stone_bricks");
        int mossy = color("mossy_stone_bricks");
        int cracked = color("cracked_stone_bricks");
        int floor = color("cobblestone");
        for (int z = 0; z < 9; z++) {
            for (int x = 0; x < 9; x++) {
                grid.set(x, 0, z, random.nextDouble() < 0.8 ? floor : mossy);
                boolean wall = x == 0 || z == 0 || x == 8 || z == 8;
                if (!wall) {
                    continue;
                }
                int height = 1 + (int) Math.round(Math.abs(Math.sin((x + z) * 0.7)) * 4.5);
                for (int y = 1; y <= Math.min(5, height); y++) {
                    if (y == 2 && (x == 4 || z == 4)) {
                        continue;
                    }
                    double roll = random.nextDouble();
                    grid.set(x, y, z, roll < 0.55 ? bricks : roll < 0.8 ? mossy : cracked);
                }
            }
        }
        for (int x = 2; x < 7; x++) {
            grid.set(x, 1, 5, color("chiseled_stone_bricks"));
        }
        return grid;
    }

    /** Casita: muros de tablones con esquinas de tronco, tejado escalonado y ventana. */
    public static BlueprintThumbnail.Grid house() {
        BlueprintThumbnail.Grid grid = new BlueprintThumbnail.Grid(9, 9, 7);
        int planks = color("spruce_planks");
        int log = color("spruce_log");
        int roof = color("dark_oak_planks");
        int glass = color("glass");
        int cobble = color("cobblestone");
        for (int z = 0; z < 7; z++) {
            for (int x = 0; x < 9; x++) {
                grid.set(x, 0, z, cobble);
            }
        }
        for (int y = 1; y <= 4; y++) {
            for (int z = 0; z < 7; z++) {
                for (int x = 0; x < 9; x++) {
                    boolean edgeX = x == 0 || x == 8;
                    boolean edgeZ = z == 0 || z == 6;
                    if (!edgeX && !edgeZ) {
                        continue;
                    }
                    boolean corner = edgeX && edgeZ;
                    boolean window = y == 2 && (x == 2 || x == 6) && z == 6;
                    boolean door = z == 6 && x == 4 && y <= 2;
                    if (door) {
                        continue;
                    }
                    grid.set(x, y, z, corner ? log : window ? glass : planks);
                }
            }
        }
        for (int step = 0; step < 4; step++) {
            for (int z = step; z < 7 - step; z++) {
                for (int x = 0; x < 9; x++) {
                    grid.set(x, 5 + step, z, roof);
                }
            }
        }
        return grid;
    }

    /** Árbol: tronco de abedul y copa redondeada. */
    public static BlueprintThumbnail.Grid tree() {
        BlueprintThumbnail.Grid grid = new BlueprintThumbnail.Grid(7, 11, 7);
        for (int y = 0; y < 7; y++) {
            grid.set(3, y, 3, color("birch_log"));
        }
        int leaves = color("birch_leaves");
        for (int y = 4; y < 11; y++) {
            for (int z = 0; z < 7; z++) {
                for (int x = 0; x < 7; x++) {
                    double distance = Math.sqrt(Math.pow(x - 3, 2) + Math.pow((y - 7) * 1.1, 2) + Math.pow(z - 3, 2));
                    if (distance < 3.3 && !(x == 3 && z == 3 && y < 7)) {
                        grid.set(x, y, z, leaves);
                    }
                }
            }
        }
        return grid;
    }

    /** Pozo: aro de adoquines con agua, postes y tejadillo. */
    public static BlueprintThumbnail.Grid well() {
        BlueprintThumbnail.Grid grid = new BlueprintThumbnail.Grid(5, 6, 5);
        int cobble = color("mossy_cobblestone");
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 5; x++) {
                boolean ring = x == 0 || z == 0 || x == 4 || z == 4;
                grid.set(x, 0, z, cobble);
                if (ring) {
                    grid.set(x, 1, z, cobble);
                } else {
                    grid.set(x, 1, z, 0x3F76E4);
                }
            }
        }
        for (int y = 2; y <= 4; y++) {
            grid.set(0, y, 0, color("oak_log"));
            grid.set(4, y, 4, color("oak_log"));
            grid.set(0, y, 4, color("oak_log"));
            grid.set(4, y, 0, color("oak_log"));
        }
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 5; x++) {
                grid.set(x, 5, z, color("spruce_planks"));
            }
        }
        return grid;
    }

    /** Grande: una torre de 40 de alto sobre una plaza de 64×64, para medir el tiempo de la miniatura. */
    public static BlueprintThumbnail.Grid plaza() {
        BlueprintThumbnail.Grid grid = new BlueprintThumbnail.Grid(64, 40, 64);
        int floor = color("smooth_stone");
        int tower = color("deepslate_bricks");
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                grid.set(x, 0, z, (x + z) % 7 == 0 ? color("polished_andesite") : floor);
                if (Math.hypot(x - 32, z - 32) < 6) {
                    for (int y = 1; y < 40; y++) {
                        grid.set(x, y, z, tower);
                    }
                }
            }
        }
        return grid;
    }

    static Map<String, BlueprintThumbnail.Grid> samples() {
        return Map.of("roca", rock(3), "arbusto", bush(), "ruina", ruin(7), "casita", house(), "abedul", tree(),
                "pozo", well());
    }
}
