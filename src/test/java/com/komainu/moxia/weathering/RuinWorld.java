package com.komainu.moxia.weathering;

import com.komainu.moxia.weathering.WeatheringEngine.Box;
import com.komainu.moxia.weathering.WeatheringEngine.Change;
import com.komainu.moxia.weathering.WeatheringEngine.Result;

import java.util.HashMap;
import java.util.Map;

/**
 * Mundo falso en memoria para probar Envejecer (lo que no se ha puesto es aire) y las obras de prueba: una casa de
 * muros de un bloque con tejado de madera, una torre con almenas y un lienzo de muralla con puerta.
 */
final class RuinWorld implements WeatheringEngine.BlockSource {
    static final String PANE = "minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]";

    private final Map<Long, String> blocks = new HashMap<>();

    /** Obra de prueba con la caja que la envuelve (con suelo debajo y aire encima). */
    record Scene(String name, RuinWorld world, Box box) {
    }

    static RuinWorld ground(int minX, int y, int minZ, int maxX, int maxZ) {
        RuinWorld world = new RuinWorld();
        for (int x = minX - 4; x <= maxX + 4; x++) {
            for (int z = minZ - 4; z <= maxZ + 4; z++) {
                world.set(x, y, z, "minecraft:grass_block[snowy=false]");
                world.set(x, y - 1, z, "minecraft:dirt");
            }
        }
        return world;
    }

    /** Casita de ladrillo de piedra con ventanas, suelo de tablones en el piso alto y tejado de adoquín. */
    static RuinWorld house() {
        RuinWorld world = ground(-4, 0, -4, 14, 14);
        for (int y = 1; y <= 8; y++) {
            for (int x = 0; x <= 8; x++) {
                for (int z = 0; z <= 8; z++) {
                    boolean wall = x == 0 || x == 8 || z == 0 || z == 8;
                    if (!wall) {
                        continue;
                    }
                    boolean corner = (x == 0 || x == 8) && (z == 0 || z == 8);
                    boolean window = !corner && (y == 3 || y == 6) && (x == 4 || z == 4);
                    boolean door = z == 0 && x == 4 && y <= 2;
                    if (door) {
                        continue;
                    }
                    world.set(x, y, z, corner ? "minecraft:oak_log[axis=y]"
                            : window ? PANE
                            : y == 1 ? "minecraft:cobblestone" : "minecraft:stone_bricks");
                }
            }
        }
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                world.set(x, 5, z, "minecraft:oak_planks");
            }
        }
        for (int layer = 0; layer <= 4; layer++) {
            for (int x = layer - 1; x <= 9 - layer; x++) {
                for (int z = layer - 1; z <= 9 - layer; z++) {
                    boolean edge = x == layer - 1 || x == 9 - layer || z == layer - 1 || z == 9 - layer;
                    if (edge || layer == 4) {
                        world.set(x, 9 + layer, z, "minecraft:cobblestone");
                    }
                }
            }
        }
        world.set(8, 9, 8, "minecraft:copper_block");
        world.set(2, 2, 7, "minecraft:wall_torch[facing=north]");
        return world;
    }

    /**
     * Casa de dos plantas: muros de un bloque de ladrillo de piedra sobre un zócalo de adoquín, esquinas de adoquín,
     * puerta, ventanas de dos de alto, forjado de tablones y tejado a dos aguas de escaleras de roble con hastiales
     * de piedra.
     */
    static Scene casa() {
        RuinWorld world = ground(-4, 0, -4, 14, 12);
        for (int y = 1; y <= 8; y++) {
            for (int x = 0; x <= 10; x++) {
                for (int z = 0; z <= 8; z++) {
                    if (x != 0 && x != 10 && z != 0 && z != 8) {
                        continue;
                    }
                    boolean corner = (x == 0 || x == 10) && (z == 0 || z == 8);
                    boolean door = z == 0 && x == 5 && y <= 2;
                    boolean window = !corner && (y == 2 || y == 3 || y == 6 || y == 7)
                            && ((z == 0 || z == 8) && (x == 2 || x == 8) || (x == 0 || x == 10) && z == 4);
                    if (door) {
                        continue;
                    }
                    world.set(x, y, z, corner || y == 1 ? "minecraft:cobblestone" : window ? PANE
                            : "minecraft:stone_bricks");
                }
            }
        }
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 7; z++) {
                world.set(x, 5, z, "minecraft:oak_planks");
            }
        }
        for (int k = 0; k <= 4; k++) {
            int y = 9 + k;
            for (int x = -1; x <= 11; x++) {
                world.set(x, y, -1 + k, stairs("oak_stairs", "south"));
                world.set(x, y, 9 - k, stairs("oak_stairs", "north"));
            }
            for (int z = k; z <= 8 - k; z++) {
                world.set(0, y, z, "minecraft:stone_bricks");
                world.set(10, y, z, "minecraft:stone_bricks");
            }
        }
        for (int x = -1; x <= 11; x++) {
            world.set(x, 14, 4, "minecraft:oak_slab[type=bottom,waterlogged=false]");
        }
        world.set(3, 2, 7, "minecraft:wall_torch[facing=north]");
        return new Scene("casa", world, new Box(-3, 1, -3, 13, 16, 11));
    }

    /**
     * Torre de 9 × 9 con muros de un bloque de ladrillo de piedra, aspilleras, dos forjados de abeto, terraza y
     * almenas.
     */
    static Scene torre() {
        RuinWorld world = ground(-4, 0, -4, 12, 12);
        for (int y = 1; y <= 17; y++) {
            for (int x = 0; x <= 8; x++) {
                for (int z = 0; z <= 8; z++) {
                    if (x != 0 && x != 8 && z != 0 && z != 8) {
                        continue;
                    }
                    boolean corner = (x == 0 || x == 8) && (z == 0 || z == 8);
                    boolean door = z == 0 && x == 4 && y <= 3 && y >= 1;
                    boolean slit = !corner && (x == 4 || z == 4) && (y == 8 || y == 9 || y == 13 || y == 14);
                    if (door || slit) {
                        continue;
                    }
                    world.set(x, y, z, y <= 2 ? "minecraft:cobblestone" : "minecraft:stone_bricks");
                }
            }
        }
        for (int level : new int[]{6, 11, 16}) {
            for (int x = 1; x <= 7; x++) {
                for (int z = 1; z <= 7; z++) {
                    world.set(x, level, z, "minecraft:spruce_planks");
                }
            }
        }
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
                if (edge && (x + z) % 2 == 0) {
                    world.set(x, 18, z, "minecraft:stone_bricks");
                }
            }
        }
        return new Scene("torre", world, new Box(-3, 1, -3, 11, 20, 11));
    }

    /** Lienzo de muralla de dos de grueso con puerta en arco, adarve y almenas en el lado de fuera. */
    static Scene muro() {
        RuinWorld world = ground(-4, 0, -4, 28, 6);
        for (int x = 0; x <= 24; x++) {
            for (int y = 1; y <= 7; y++) {
                for (int z = 0; z <= 1; z++) {
                    boolean gate = x >= 11 && x <= 13 && y <= 3 || x == 12 && y == 4;
                    if (gate) {
                        continue;
                    }
                    world.set(x, y, z, y == 1 ? "minecraft:cobblestone" : "minecraft:stone_bricks");
                }
            }
            if (x % 2 == 0) {
                world.set(x, 8, 0, "minecraft:stone_bricks");
            }
        }
        return new Scene("muro", world, new Box(-3, 1, -3, 27, 10, 5));
    }

    private static String stairs(String path, String facing) {
        return "minecraft:" + path + "[facing=" + facing + ",half=bottom,shape=straight,waterlogged=false]";
    }

    void set(int x, int y, int z, String block) {
        blocks.put(key(x, y, z), BlockText.parse(block).toString());
    }

    String get(int x, int y, int z) {
        return blocks.getOrDefault(key(x, y, z), "minecraft:air");
    }

    boolean air(int x, int y, int z) {
        return get(x, y, z).equals("minecraft:air");
    }

    @Override
    public String state(int x, int y, int z) {
        return get(x, y, z);
    }

    RuinWorld apply(Result result) {
        RuinWorld copy = new RuinWorld();
        copy.blocks.putAll(blocks);
        for (Change change : result.changes()) {
            copy.set(change.x(), change.y(), change.z(), change.after());
        }
        return copy;
    }

    /** Cuántos bloques de la caja cumplen {@code test}. */
    int count(Box box, java.util.function.Predicate<String> test) {
        int count = 0;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (test.test(get(x, y, z))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
