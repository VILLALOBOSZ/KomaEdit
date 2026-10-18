package com.komainu.moxia.utility;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GravityEngineTest {
    /** Aplica la gravedad y escribe el resultado en el mismo mundo de texto. */
    private static GravityEngine.Result fall(TestWorld world, GravityEngine.Movable movable, boolean unsupported) {
        var snapshot = world.snapshot();
        Map<String, String> writes = new HashMap<>();
        GravityEngine.Result result = GravityEngine.apply(snapshot, world.kinds(), movable, unsupported,
                (x, y, z, content, waterlogged) -> {
                    String block = switch (content) {
                        case GravityEngine.AIR -> TestWorld.AIR;
                        case GravityEngine.WATER -> TestWorld.WATER;
                        case GravityEngine.LAVA -> TestWorld.LAVA;
                        default -> world.state(content);
                    };
                    if (waterlogged != GravityEngine.KEEP_WATERLOG) {
                        block = TestWorld.withProperty(block, "waterlogged", waterlogged == 1 ? "true" : "false");
                    }
                    writes.put(x + "," + y + "," + z, block);
                }, () -> {
                });
        writes.forEach((key, block) -> {
            String[] parts = key.split(",");
            world.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), block);
        });
        return result;
    }

    @Test
    void columnsFallInOrder() {
        TestWorld world = new TestWorld(0, 0, 0, 3, 12, 3);
        world.fill(0, 0, 0, 2, 0, 2, "stone");
        world.select(0, 1, 0, 2, 11, 2);
        world.set(1, 5, 1, "cobblestone").set(1, 8, 1, "dirt").set(1, 9, 1, "gold_block");
        GravityEngine.Result result = fall(world, null, false);
        assertEquals("minecraft:cobblestone", world.at(1, 1, 1));
        assertEquals("minecraft:dirt", world.at(1, 2, 1));
        assertEquals("minecraft:gold_block", world.at(1, 3, 1));
        for (int y = 4; y <= 11; y++) {
            assertEquals(TestWorld.AIR, world.at(1, y, 1));
        }
        assertEquals(3, result.moved());
    }

    @Test
    void keepsTheNumberOfEveryBlock() {
        TestWorld world = new TestWorld(0, 0, 0, 12, 20, 12);
        world.fill(0, 0, 0, 11, 0, 11, "bedrock");
        world.selectInside(1);
        String[] palette = {"stone", "dirt", "oak_planks", "glass", "oak_stairs[facing=north,half=bottom,"
                + "shape=straight,waterlogged=false]", "sand"};
        SplittableRandom random = new SplittableRandom(7);
        for (int y = 1; y < 19; y++) {
            for (int z = 1; z < 11; z++) {
                for (int x = 1; x < 11; x++) {
                    if (random.nextInt(4) == 0) {
                        world.set(x, y, z, palette[random.nextInt(palette.length)]);
                    }
                }
            }
        }
        Map<String, Long> before = counts(world, palette);
        fall(world, null, false);
        assertEquals(before, counts(world, palette), "la gravedad no crea ni destruye bloques");
        // Y ya no queda ningún bloque flotando dentro de la selección.
        for (int z = 1; z < 11; z++) {
            for (int x = 1; x < 11; x++) {
                boolean air = false;
                for (int y = 1; y < 19; y++) {
                    boolean empty = world.at(x, y, z).equals(TestWorld.AIR);
                    assertTrue(!air || empty, "bloque flotando en " + x + "," + y + "," + z);
                    air |= empty;
                }
            }
        }
    }

    @Test
    void blocksSinkThroughWaterAndTheSurfaceStays() {
        TestWorld world = new TestWorld(0, 0, 0, 1, 10, 1);
        world.set(0, 0, 0, "stone");
        world.select(0, 1, 0, 0, 9, 0);
        world.fill(0, 1, 0, 0, 4, 0, TestWorld.WATER);
        world.set(0, 6, 0, "oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        world.set(0, 7, 0, "gravel");
        fall(world, null, false);
        assertEquals("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=true]",
                world.at(0, 1, 0), "la escalera cae al fondo y queda anegada");
        assertEquals("minecraft:gravel", world.at(0, 2, 0));
        assertEquals(TestWorld.WATER, world.at(0, 3, 0));
        assertEquals(TestWorld.WATER, world.at(0, 4, 0), "la superficie del agua no baja");
        assertEquals(TestWorld.AIR, world.at(0, 5, 0));
        assertEquals(TestWorld.AIR, world.at(0, 6, 0));
    }

    @Test
    void waterloggedBlocksDryWhenTheyEndAboveTheWater() {
        TestWorld world = new TestWorld(0, 0, 0, 1, 8, 1);
        world.set(0, 0, 0, "stone");
        world.select(0, 1, 0, 0, 7, 0);
        world.set(0, 5, 0, "oak_slab[type=bottom,waterlogged=true]");
        fall(world, null, false);
        assertEquals("minecraft:oak_slab[type=bottom,waterlogged=false]", world.at(0, 1, 0));
    }

    @Test
    void onlyUnsupportedBlocksFallWithTheFlag() {
        TestWorld world = new TestWorld(0, 0, 0, 8, 10, 3);
        world.fill(0, 0, 0, 7, 0, 2, "stone");
        world.select(0, 1, 0, 7, 9, 2);
        // Pared con un saliente pegado y un bloque flotando suelto.
        world.fill(1, 1, 1, 1, 6, 1, "stone_bricks");
        world.set(2, 6, 1, "stone_brick_slab[type=top,waterlogged=false]");
        world.set(3, 6, 1, "stone_bricks");
        world.set(6, 7, 1, "mossy_cobblestone");
        fall(world, null, true);
        assertEquals("minecraft:stone_bricks", world.at(3, 6, 1), "el saliente unido a la pared se queda");
        assertEquals("minecraft:stone_bricks", world.at(1, 6, 1));
        assertEquals("minecraft:mossy_cobblestone", world.at(6, 1, 1), "el bloque suelto cae");
        assertEquals(TestWorld.AIR, world.at(6, 7, 1));
        // Sin la bandera el saliente también cae.
        fall(world, null, false);
        assertEquals("minecraft:stone_bricks", world.at(3, 1, 1));
    }

    @Test
    void theMaskKeepsBlocksInPlaceAsFloors() {
        TestWorld world = new TestWorld(0, 0, 0, 1, 10, 1);
        world.set(0, 0, 0, "stone");
        world.select(0, 1, 0, 0, 9, 0);
        world.set(0, 4, 0, "glass").set(0, 8, 0, "sand").set(0, 2, 0, "dirt");
        fall(world, (x, y, z) -> !world.id(x, y, z).equals("minecraft:glass"), false);
        assertEquals("minecraft:dirt", world.at(0, 1, 0));
        assertEquals("minecraft:glass", world.at(0, 4, 0), "lo que no pasa la máscara no se mueve");
        assertEquals("minecraft:sand", world.at(0, 5, 0), "y lo de encima cae sobre él");
    }

    @Test
    void blocksWithDataStayWhereTheyAre() {
        TestWorld world = new TestWorld(0, 0, 0, 1, 8, 1);
        world.set(0, 0, 0, "stone");
        world.select(0, 1, 0, 0, 7, 0);
        world.set(0, 4, 0, "chest[facing=north,type=single,waterlogged=false]").set(0, 6, 0, "dirt");
        fall(world, null, false);
        assertEquals("minecraft:chest[facing=north,type=single,waterlogged=false]", world.at(0, 4, 0),
                "un cofre no se mueve: perdería lo que guarda");
        assertEquals("minecraft:dirt", world.at(0, 5, 0), "y lo de encima cae sobre él");
    }

    private static Map<String, Long> counts(TestWorld world, String[] palette) {
        Map<String, Long> counts = new HashMap<>();
        for (String block : palette) {
            String id = "minecraft:" + (block.contains("[") ? block.substring(0, block.indexOf('[')) : block);
            counts.put(id, world.count(id));
        }
        return counts;
    }
}
