package com.komainu.moxia.utility;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowEngineTest {
    static SnowEngine.Result snow(TestWorld world, SnowEngine.Settings settings) {
        Map<String, String> writes = new HashMap<>();
        SnowEngine.Result result = SnowEngine.apply(world.snapshot(), world.kinds(), settings, null,
                new SnowEngine.Sink() {
                    @Override
                    public void snow(int x, int y, int z, int layers) {
                        writes.put(x + "," + y + "," + z, "minecraft:snow[layers=" + layers + "]");
                    }

                    @Override
                    public void snowy(int x, int y, int z) {
                        writes.put(x + "," + y + "," + z, TestWorld.withProperty(world.at(x, y, z), "snowy", "true"));
                    }
                }, () -> {
                });
        writes.forEach((key, block) -> {
            String[] parts = key.split(",");
            world.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), block);
        });
        return result;
    }

    static int layers(TestWorld world, int x, int y, int z) {
        String block = world.at(x, y, z);
        if (!block.startsWith("minecraft:snow[")) {
            return 0;
        }
        return Integer.parseInt(block.substring(block.indexOf("layers=") + 7, block.length() - 1));
    }

    private static SnowEngine.Settings flat(int layers) {
        return new SnowEngine.Settings(layers, 0, 0.0, 0, 0, false);
    }

    @Test
    void snowOnlyWhereItCanStay() {
        String[] ground = {"grass_block[snowy=false]", "stone", "oak_slab[type=bottom,waterlogged=false]", "ice",
                "glass_pane", "water[level=0]", "oak_fence", "packed_ice", "dirt_path", "barrier",
                "oak_slab[type=top,waterlogged=false]", "honey_block", "torch", "oak_leaves"};
        TestWorld world = new TestWorld(0, 0, 0, ground.length, 4, 1);
        for (int x = 0; x < ground.length; x++) {
            world.set(x, 0, 0, "stone").set(x, 1, 0, ground[x]);
        }
        world.select(0, 1, 0, ground.length - 1, 3, 0);
        snow(world, flat(3));
        boolean[] expected = {true, true, false, false, false, false, false, false, false, false, true, true, false,
                true};
        for (int x = 0; x < ground.length; x++) {
            assertEquals(expected[x] ? 3 : 0, layers(world, x, 2, 0), "sobre " + ground[x]);
        }
        assertEquals("minecraft:grass_block[snowy=true]", world.at(0, 1, 0), "la hierba pasa a nevada");
    }

    @Test
    void neverOutsideOneToEightAndOnlyOnAir() {
        TestWorld world = hills(11L);
        TestWorld before = hills(11L);
        snow(world, new SnowEngine.Settings(4, 3, 1.0, 4, 4, false));
        int placed = 0;
        for (int x = world.minX; x <= world.maxX(); x++) {
            for (int z = world.minZ; z <= world.maxZ(); z++) {
                for (int y = world.minY + 1; y <= world.maxY(); y++) {
                    int layers = layers(world, x, y, z);
                    if (layers == 0) {
                        continue;
                    }
                    placed++;
                    assertTrue(layers >= 1 && layers <= 8, "capas fuera de rango: " + layers);
                    assertEquals(TestWorld.AIR, before.at(x, y, z), "la nieve solo ocupa aire");
                    assertTrue(world.selected(x, y, z), "la nieve solo va dentro de la selección");
                    String below = before.id(x, y - 1, z);
                    assertFalse(below.endsWith("_slab") || below.equals("minecraft:ice")
                            || below.equals("minecraft:water"), "nieve sobre " + below);
                }
            }
        }
        assertTrue(placed > 200, "la colina se cubre: " + placed);
    }

    @Test
    void hollowsGetMoreSnowAndSlopesBecomeRamps() {
        TestWorld world = new TestWorld(0, 0, 0, 21, 6, 9);
        world.fill(0, 0, 0, 20, 1, 8, "stone");
        world.fill(11, 2, 0, 20, 2, 8, "stone");
        world.set(4, 1, 4, "air");
        world.selectInside(0);
        snow(world, new SnowEngine.Settings(2, 3, 1.0, 0, 0, false));
        int flat = layers(world, 2, 2, 7);
        int hole = layers(world, 4, 1, 4);
        assertEquals(2, flat, "en llano, las capas pedidas");
        assertTrue(hole > flat + 2, "el hueco acumula: " + hole);
        // Junto al escalón la nieve sube hacia el bloque de arriba y se va igualando al alejarse.
        int nearStep = layers(world, 10, 2, 4);
        int fartherStep = layers(world, 8, 2, 4);
        assertTrue(nearStep > fartherStep && fartherStep >= flat, nearStep + " > " + fartherStep);
        int edgeAbove = layers(world, 11, 3, 4);
        int inlandAbove = layers(world, 16, 3, 4);
        assertTrue(edgeAbove <= inlandAbove, "en la arista queda más fina");
    }

    @Test
    void shadeAndAltitudeThicken() {
        TestWorld world = new TestWorld(0, 0, 0, 16, 14, 16);
        world.fill(0, 0, 0, 15, 0, 15, "stone");
        // Un muro alto tapa el cielo del oeste a lo que tiene al pie.
        world.fill(7, 1, 0, 7, 10, 15, "stone_bricks");
        world.select(1, 1, 1, 14, 5, 14);
        snow(world, new SnowEngine.Settings(2, 0, 0.0, 4, 0, false));
        assertTrue(layers(world, 8, 1, 8) > layers(world, 14, 1, 8), "al pie del muro, a la sombra, más nieve");
        TestWorld slope = new TestWorld(0, 0, 0, 10, 12, 3);
        for (int x = 0; x < 10; x++) {
            slope.fill(x, 0, 0, x, x, 2, "stone");
        }
        slope.selectInside(0);
        snow(slope, new SnowEngine.Settings(1, 0, 0.0, 0, 6, false));
        assertTrue(layers(slope, 9, 10, 1) > layers(slope, 0, 1, 1), "más arriba, más nieve");
    }

    @Test
    void existingSnowOnlyGrowsAndPlantsNeedTheFlag() {
        TestWorld world = new TestWorld(0, 0, 0, 3, 4, 1);
        world.fill(0, 0, 0, 2, 0, 0, "stone");
        world.set(0, 1, 0, "snow[layers=6]").set(1, 1, 0, "snow[layers=1]").set(2, 1, 0, "short_grass");
        world.selectInside(0);
        snow(world, flat(3));
        assertEquals(6, layers(world, 0, 1, 0), "no se quita nieve");
        assertEquals(3, layers(world, 1, 1, 0), "la capa fina engorda");
        assertEquals("minecraft:short_grass", world.at(2, 1, 0), "sin -p la hierba alta se respeta");
        assertEquals(0, layers(world, 2, 2, 0), "y no hay nieve encima de ella");
        snow(world, new SnowEngine.Settings(3, 0, 0.0, 0, 0, true));
        assertEquals(3, layers(world, 2, 1, 0), "con -p la nieve la tapa");
    }

    /** Colinas de prueba con algo de todo: hierba, losas, hielo y agua. */
    static TestWorld hills(long seed) {
        TestWorld world = new TestWorld(0, 0, 0, 32, 20, 32);
        SplittableRandom random = new SplittableRandom(seed);
        double phase = random.nextDouble() * 6.0;
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                double height = 6.0 + 4.0 * Math.sin(x * 0.25 + phase) * Math.cos(z * 0.2) + 3.0 * Math.exp(
                        -((x - 20) * (x - 20) + (z - 12) * (z - 12)) / 40.0) * 2.0;
                int top = (int) Math.round(height);
                world.fill(x, 0, z, x, top - 1, z, "stone");
                String surface = switch (random.nextInt(12)) {
                    case 0 -> "oak_slab[type=bottom,waterlogged=false]";
                    case 1 -> "ice";
                    default -> "grass_block[snowy=false]";
                };
                world.set(x, top, z, surface);
                if (top < 4) {
                    world.fill(x, top + 1, z, x, 4, z, "water[level=0]");
                }
            }
        }
        world.select(2, 1, 2, 29, 19, 29);
        return world;
    }
}
