package com.komainu.moxia.weathering;

import com.komainu.moxia.weathering.AgingTable.Aging;
import com.komainu.moxia.weathering.WeatheringEngine.Box;
import com.komainu.moxia.weathering.WeatheringEngine.Change;
import com.komainu.moxia.weathering.WeatheringEngine.Result;
import com.komainu.moxia.weathering.WeatheringEngine.Selection;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatheringEngineTest {
    private static final Box HOUSE_BOX = new Box(-2, 1, -2, 12, 14, 12);

    @Test
    void sameSeedGivesSameRuin() {
        FakeWorld world = FakeWorld.house();
        WeatheringSettings settings = settings(70, 60, 60, 1234L, WeatherStyle.TEMPERATE, true);
        Result first = WeatheringEngine.weather(world, HOUSE_BOX, Selection.all(), settings);
        Result second = WeatheringEngine.weather(world, HOUSE_BOX, Selection.all(), settings);
        assertFalse(first.changes().isEmpty());
        assertEquals(first.changes(), second.changes());

        Result other = WeatheringEngine.weather(world, HOUSE_BOX, Selection.all(), settings.withSeed(99L));
        assertNotEquals(first.changes(), other.changes());
    }

    @Test
    void zeroIntensityChangesNothing() {
        FakeWorld world = FakeWorld.house();
        for (WeatherStyle style : WeatherStyle.values()) {
            Result result = WeatheringEngine.weather(world, HOUSE_BOX, Selection.all(),
                    settings(0, 100, 100, 7L, style, true));
            assertTrue(result.changes().isEmpty(), style.id());
        }
    }

    @Test
    void agingTableKeepsPropertiesAndFollowsTheCriteria() {
        BlockText stairs = BlockText.parse("minecraft:stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]");
        BlockText mossy = AgingTable.variant(stairs, Aging.MOSS, WeatherStyle.TEMPERATE);
        assertEquals("minecraft:mossy_stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]",
                mossy.toString());
        assertEquals("mossy_cobblestone", variant("cobblestone", Aging.MOSS));
        assertEquals("mossy_cobblestone_wall", variant("cobblestone_wall", Aging.MOSS));
        assertEquals("cracked_stone_bricks", variant("stone_bricks", Aging.CRACK));
        assertEquals("cracked_deepslate_bricks", variant("deepslate_bricks", Aging.CRACK));
        assertEquals("cracked_deepslate_tiles", variant("deepslate_tiles", Aging.CRACK));
        assertEquals("andesite", variant("polished_andesite", Aging.WEAR));
        assertEquals("cobblestone_stairs", variant("stone_stairs", Aging.WEAR));
        assertEquals("sandstone", variant("cut_sandstone", Aging.WEAR));
        assertEquals("stripped_oak_log", variant("oak_log", Aging.STRIP));
        assertEquals("stripped_dark_oak_wood", variant("dark_oak_wood", Aging.STRIP));
        assertEquals("stripped_crimson_stem", variant("crimson_stem", Aging.STRIP));
        assertEquals("spruce_slab", variant("spruce_planks", Aging.ROT));
        assertEquals("exposed_copper", variant("copper_block", Aging.OXIDIZE));
        assertEquals("weathered_cut_copper_stairs", variant("exposed_cut_copper_stairs", Aging.OXIDIZE));
        assertEquals("oxidized_copper_grate", variant("weathered_copper_grate", Aging.OXIDIZE));
        assertNull(variant("oxidized_copper", Aging.OXIDIZE));
        assertNull(variant("waxed_copper_block", Aging.OXIDIZE), "el cobre encerado no se oxida");
        assertNull(variant("bricks", Aging.WEAR));
        assertEquals("terracotta", AgingTable.variant(BlockText.of("bricks"), Aging.WEAR, WeatherStyle.DESERT).path());
        assertNull(variant("stone_bricks", Aging.STRIP));
        assertNull(AgingTable.variant(BlockText.parse("othermod:stone_bricks"), Aging.MOSS, WeatherStyle.TEMPERATE));

        assertEquals(AgingTable.Kind.PROTECTED, AgingTable.kind(BlockText.parse("minecraft:chest[facing=north]")));
        assertEquals(AgingTable.Kind.PROTECTED, AgingTable.kind(BlockText.of("oak_door")));
        assertEquals(AgingTable.Kind.NATURAL, AgingTable.kind(BlockText.of("grass_block")));
        assertEquals(AgingTable.Kind.DECOR, AgingTable.kind(BlockText.of("wall_torch")));
        assertEquals(AgingTable.Kind.BUILDING, AgingTable.kind(BlockText.of("sea_lantern")));
        assertEquals(AgingTable.Kind.BUILDING, AgingTable.kind(BlockText.of("oak_trapdoor")));
        assertEquals(AgingTable.Material.GLASS, AgingTable.material(BlockText.of("glass_pane")));
        assertEquals(AgingTable.Material.WOOD, AgingTable.material(BlockText.of("dark_oak_stairs")));
        assertEquals(AgingTable.Material.HARD, AgingTable.material(BlockText.of("deepslate_bricks")));
    }

    @Test
    void unsupportedBlocksFallAndPileUp() {
        FakeWorld world = FakeWorld.ground(0, 0, 0, 12, 12);
        // Plataforma flotante sin nada que la sostenga.
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                world.set(x, 9, z, "minecraft:stone_bricks");
            }
        }
        Box box = new Box(0, 1, 0, 12, 12, 12);
        Result result = WeatheringEngine.weather(world, box, Selection.all(),
                settings(5, 40, 0, 3L, WeatherStyle.TEMPERATE, false));
        FakeWorld after = world.apply(result);
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                assertEquals("minecraft:air", after.get(x, 9, z), "sigue flotando en " + x + "," + z);
            }
        }
        assertEquals(9, result.fallen() + result.eroded());
        assertTrue(result.rubble() > 0, "lo que cae deja escombro");
        int onGround = 0;
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                if (!after.get(x, 1, z).equals("minecraft:air")) {
                    onGround++;
                }
                for (int y = 2; y <= 12; y++) {
                    String block = after.get(x, y, z);
                    if (!block.equals("minecraft:air")) {
                        assertFalse(after.get(x, y - 1, z).equals("minecraft:air"), "escombro en el aire en " + y);
                    }
                }
            }
        }
        assertEquals(result.rubble() > 0, onGround > 0);
    }

    @Test
    void longOverhangsBreakWhereTheSpanEnds() {
        FakeWorld world = FakeWorld.ground(0, 0, 0, 20, 4);
        for (int y = 1; y <= 6; y++) {
            world.set(1, y, 2, "minecraft:deepslate_tiles");
        }
        for (int x = 2; x <= 16; x++) {
            world.set(x, 6, 2, "minecraft:deepslate_tiles");
        }
        Box box = new Box(0, 1, 0, 20, 8, 4);
        Result result = WeatheringEngine.weather(world, box, Selection.all(),
                settings(1, 100, 0, 11L, WeatherStyle.TEMPERATE, false));
        FakeWorld after = world.apply(result);
        assertEquals("minecraft:air", after.get(16, 6, 2), "la punta del voladizo cae");
        assertEquals("minecraft:air", after.get(10, 6, 2));
        assertEquals("minecraft:deepslate_tiles", after.get(2, 6, 2), "junto al pilar aguanta");
        assertTrue(result.fallen() >= 10);

        Result calm = WeatheringEngine.weather(world, box, Selection.all(),
                settings(1, 0, 0, 11L, WeatherStyle.TEMPERATE, false));
        assertEquals(0, calm.fallen(), "sin derrumbe no cae nada por el vano");
    }

    @Test
    void neverLeavesTheSelection() {
        FakeWorld world = FakeWorld.house();
        Box box = new Box(0, 1, 0, 10, 12, 10);
        Selection half = (x, y, z) -> x + z <= 12;
        for (WeatherStyle style : WeatherStyle.values()) {
            Result result = WeatheringEngine.weather(world, box, half,
                    settings(100, 100, 100, 5L, style, true));
            assertFalse(result.changes().isEmpty(), style.id());
            for (Change change : result.changes()) {
                assertTrue(box.contains(change.x(), change.y(), change.z()), "fuera de la caja: " + change);
                assertTrue(half.contains(change.x(), change.y(), change.z()), "fuera de la selección: " + change);
                assertEquals(world.get(change.x(), change.y(), change.z()),
                        BlockText.parse(change.before()).toString());
            }
        }
    }

    @Test
    void protectedBlocksAreNeverTouched() {
        FakeWorld world = FakeWorld.house();
        world.set(3, 1, 3, "minecraft:chest[facing=north,type=single,waterlogged=false]");
        Result result = WeatheringEngine.weather(world, HOUSE_BOX, Selection.all(),
                settings(100, 100, 100, 5L, WeatherStyle.TEMPERATE, true));
        for (Change change : result.changes()) {
            assertFalse(change.x() == 3 && change.y() == 1 && change.z() == 3, "tocó el cofre");
        }
    }

    @Test
    void stylesLeaveTheirMark() {
        FakeWorld world = FakeWorld.house();
        assertTrue(contains(run(world, WeatherStyle.SNOWY), "minecraft:snow["));
        assertTrue(contains(run(world, WeatherStyle.SUNKEN), "minecraft:water"));
        assertTrue(contains(run(world, WeatherStyle.DESERT), "minecraft:sand"));
        Result temperate = run(world, WeatherStyle.TEMPERATE);
        assertTrue(contains(temperate, "mossy_"));
        assertFalse(contains(run(world, WeatherStyle.DESERT), "mossy_stone_bricks"), "sin musgo en el desierto");
    }

    @Test
    void settingsParseLikeTheOtherCommands() {
        WeatheringSettings parsed = WeatheringSettings.parse("80 nevado -d 30 -v 10 -seed 42 -agua");
        assertEquals(80, parsed.intensity());
        assertEquals(30, parsed.collapse());
        assertEquals(10, parsed.vegetation());
        assertEquals(42L, parsed.seed());
        assertEquals(WeatherStyle.SNOWY, parsed.style());
        assertTrue(parsed.water());
        WeatheringSettings defaults = WeatheringSettings.parse("");
        assertEquals(WeatheringSettings.DEFAULT_INTENSITY, defaults.intensity());
        assertNull(defaults.seed());
        assertEquals(WeatherStyle.DESERT, WeatheringSettings.parse("desert").style());
        assertThrows(IllegalArgumentException.class, () -> WeatheringSettings.parse("120"));
        assertThrows(IllegalArgumentException.class, () -> WeatheringSettings.parse("-d"));
        assertThrows(IllegalArgumentException.class, () -> WeatheringSettings.parse("50 marte"));
        assertThrows(IllegalArgumentException.class, () -> WeatheringSettings.parse("-z 3"));
    }

    private static Result run(FakeWorld world, WeatherStyle style) {
        return WeatheringEngine.weather(world, HOUSE_BOX, Selection.all(), settings(80, 50, 80, 21L, style, true));
    }

    private static boolean contains(Result result, String text) {
        return result.changes().stream().anyMatch(change -> change.after().contains(text));
    }

    private static String variant(String path, Aging aging) {
        BlockText next = AgingTable.variant(BlockText.of(path), aging, WeatherStyle.TEMPERATE);
        return next == null ? null : next.path();
    }

    private static WeatheringSettings settings(
            int intensity, int collapse, int vegetation, long seed, WeatherStyle style, boolean water
    ) {
        return new WeatheringSettings(intensity, collapse, vegetation, seed, style, water);
    }

    /** Mundo falso en memoria: lo que no se ha puesto es aire. */
    static final class FakeWorld implements WeatheringEngine.BlockSource {
        private final Map<Long, String> blocks = new HashMap<>();

        static FakeWorld ground(int minX, int y, int minZ, int maxX, int maxZ) {
            FakeWorld world = new FakeWorld();
            for (int x = minX - 4; x <= maxX + 4; x++) {
                for (int z = minZ - 4; z <= maxZ + 4; z++) {
                    world.set(x, y, z, "minecraft:grass_block[snowy=false]");
                    world.set(x, y - 1, z, "minecraft:dirt");
                }
            }
            return world;
        }

        /** Casita de ladrillo de piedra con ventanas, suelo de tablones en el piso alto y tejado de adoquín. */
        static FakeWorld house() {
            FakeWorld world = ground(-4, 0, -4, 14, 14);
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
                                : window ? "minecraft:glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]"
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

        void set(int x, int y, int z, String block) {
            blocks.put(key(x, y, z), BlockText.parse(block).toString());
        }

        String get(int x, int y, int z) {
            return blocks.getOrDefault(key(x, y, z), "minecraft:air");
        }

        @Override
        public String state(int x, int y, int z) {
            return get(x, y, z);
        }

        FakeWorld apply(Result result) {
            FakeWorld copy = new FakeWorld();
            copy.blocks.putAll(blocks);
            for (Change change : result.changes()) {
                copy.set(change.x(), change.y(), change.z(), change.after());
            }
            return copy;
        }

        private static long key(int x, int y, int z) {
            return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
        }
    }
}
