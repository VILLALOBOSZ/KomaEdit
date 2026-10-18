package com.komainu.moxia.weathering;

import com.komainu.moxia.weathering.AgingTable.Aging;
import com.komainu.moxia.weathering.RuinWorld.Scene;
import com.komainu.moxia.weathering.WeatheringEngine.Box;
import com.komainu.moxia.weathering.WeatheringEngine.Change;
import com.komainu.moxia.weathering.WeatheringEngine.Result;
import com.komainu.moxia.weathering.WeatheringEngine.Selection;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        RuinWorld world = RuinWorld.house();
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
        RuinWorld world = RuinWorld.house();
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
        RuinWorld world = RuinWorld.ground(0, 0, 0, 12, 12);
        // Plataforma flotante sin nada que la sostenga.
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                world.set(x, 9, z, "minecraft:stone_bricks");
            }
        }
        Box box = new Box(0, 1, 0, 12, 12, 12);
        Result result = WeatheringEngine.weather(world, box, Selection.all(),
                settings(5, 40, 0, 3L, WeatherStyle.TEMPERATE, false));
        RuinWorld after = world.apply(result);
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
        RuinWorld world = RuinWorld.ground(0, 0, 0, 20, 4);
        for (int y = 1; y <= 6; y++) {
            world.set(1, y, 2, "minecraft:deepslate_tiles");
        }
        for (int x = 2; x <= 16; x++) {
            world.set(x, 6, 2, "minecraft:deepslate_tiles");
        }
        Box box = new Box(0, 1, 0, 20, 8, 4);
        Result result = WeatheringEngine.weather(world, box, Selection.all(),
                settings(1, 100, 0, 11L, WeatherStyle.TEMPERATE, false));
        RuinWorld after = world.apply(result);
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
        RuinWorld world = RuinWorld.house();
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
        RuinWorld world = RuinWorld.house();
        world.set(3, 1, 3, "minecraft:chest[facing=north,type=single,waterlogged=false]");
        Result result = WeatheringEngine.weather(world, HOUSE_BOX, Selection.all(),
                settings(100, 100, 100, 5L, WeatherStyle.TEMPERATE, true));
        for (Change change : result.changes()) {
            assertFalse(change.x() == 3 && change.y() == 1 && change.z() == 3, "tocó el cofre");
        }
    }

    @Test
    void stylesLeaveTheirMark() {
        RuinWorld world = RuinWorld.house();
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

    // ---------------------------------------------------------------- ruinas por elementos

    @Test
    void cornersOutlastTheMiddleOfTheWalls() {
        Scene casa = RuinWorld.casa();
        double corners = 0.0;
        double middles = 0.0;
        int runs = 0;
        for (long seed = 1; seed <= 8; seed++) {
            RuinWorld after = weathered(casa, 70, 50, seed);
            corners += (standing(after, 0, 0) + standing(after, 10, 0) + standing(after, 0, 8)
                    + standing(after, 10, 8)) / 4.0;
            // Centro de cada lienzo, lejos de las esquinas (la puerta está en x = 5 del muro norte).
            middles += (standing(after, 4, 8) + standing(after, 5, 8) + standing(after, 6, 8)
                    + standing(after, 0, 3) + standing(after, 10, 3) + standing(after, 0, 5) + standing(after, 10, 5))
                    / 7.0;
            runs++;
        }
        corners /= runs;
        middles /= runs;
        assertTrue(corners >= middles + 2.0, "esquinas " + corners + " frente a centros " + middles);
        assertTrue(corners >= 6.0, "las esquinas siguen casi enteras: " + corners);
    }

    @Test
    void nothingIsLeftFloating() {
        for (Scene scene : scenes()) {
            for (int intensity : new int[]{30, 60, 100}) {
                for (int collapse : new int[]{0, 50}) {
                    RuinWorld after = weathered(scene, intensity, collapse, 7L);
                    String loose = floating(after, scene.box());
                    assertNull(loose, scene.name() + " a " + intensity + " (derrumbe " + collapse + "): flota " + loose);
                }
            }
        }
    }

    @Test
    void wallsBreakFromTheTopWithoutHoles() {
        for (Scene scene : scenes()) {
            for (int intensity : new int[]{30, 60, 100}) {
                for (long seed = 1; seed <= 3; seed++) {
                    RuinWorld after = weathered(scene, intensity, 50, seed);
                    Box box = scene.box();
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            for (int y = box.minY(); y < box.maxY(); y++) {
                                boolean lost = structural(scene.world().get(x, y, z)) && !building(after.get(x, y, z));
                                boolean above = structural(scene.world().get(x, y + 1, z))
                                        && building(after.get(x, y + 1, z));
                                assertFalse(lost && above, scene.name() + " a " + intensity + ", semilla " + seed
                                        + ": hueco en " + x + "," + y + "," + z + " con muro entero encima");
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void woodenRoofGoesBeforeTheStone() {
        Scene casa = RuinWorld.casa();
        for (int intensity : new int[]{20, 40}) {
            int roof = 0;
            int roofLost = 0;
            int stone = 0;
            int stoneLost = 0;
            for (long seed = 1; seed <= 4; seed++) {
                RuinWorld after = weathered(casa, intensity, 50, seed);
                Box box = casa.box();
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            String before = casa.world().get(x, y, z);
                            boolean kept = building(after.get(x, y, z));
                            if (before.contains("oak_stairs") || before.contains("oak_slab")) {
                                roof++;
                                roofLost += kept ? 0 : 1;
                            } else if (before.contains("stone_bricks") || before.contains("cobblestone")) {
                                stone++;
                                stoneLost += kept ? 0 : 1;
                            }
                        }
                    }
                }
            }
            double roofShare = roofLost / (double) roof;
            double stoneShare = stoneLost / (double) stone;
            assertTrue(roofShare > 2.0 * stoneShare + 0.1,
                    "a " + intensity + ": tejado " + roofShare + " frente a piedra " + stoneShare);
        }
    }

    @Test
    void variantsComeInPatches() {
        for (Scene scene : List.of(RuinWorld.casa(), RuinWorld.torre())) {
            RuinWorld after = weathered(scene, 60, 50, 3L);
            for (String variant : new String[]{"mossy_", "cracked_"}) {
                double[] stats = clustering(scene, after, variant);
                assertTrue(stats[0] > 0.04 && stats[0] < 0.7, scene.name() + " " + variant + " cubre " + stats[0]);
                assertTrue(stats[1] > 1.8, scene.name() + " " + variant + " sale suelto: vecino con la misma variante "
                        + stats[1] + " veces más de lo que tocaría al azar");
            }
        }
    }

    @Test
    void barkSurvivesLowIntensities() {
        RuinWorld world = RuinWorld.house();
        RuinWorld young = world.apply(WeatheringEngine.weather(world, HOUSE_BOX, Selection.all(),
                settings(1, 50, 50, 9L, WeatherStyle.TEMPERATE, false)));
        assertEquals(0, young.count(HOUSE_BOX, block -> block.contains("stripped_")), "con intensidad 1 no se descorteza");
        int logs = 0;
        int stripped = 0;
        for (long seed = 1; seed <= 6; seed++) {
            RuinWorld old = world.apply(WeatheringEngine.weather(world, HOUSE_BOX, Selection.all(),
                    settings(100, 50, 50, seed, WeatherStyle.TEMPERATE, false)));
            logs += old.count(HOUSE_BOX, block -> block.contains("oak_log"));
            stripped += old.count(HOUSE_BOX, block -> block.contains("stripped_oak_log"));
        }
        assertTrue(stripped > 0 && stripped < logs * 0.45,
                "ni con 100 se descorteza casi todo: " + stripped + " de " + logs + " troncos en pie");
    }

    @Test
    void lostStonePilesUpAtTheFootOfTheWalls() {
        Scene casa = RuinWorld.casa();
        RuinWorld after = weathered(casa, 70, 50, 5L, 0);
        int stoneLost = 0;
        int rubble = 0;
        int nearWalls = 0;
        Box box = casa.box();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    String before = casa.world().get(x, y, z);
                    String now = after.get(x, y, z);
                    if (structural(before) && !before.contains("oak") && !building(now)) {
                        stoneLost++;
                    }
                    if (!building(before) && building(now)) {
                        rubble++;
                        // Pie de muro: a tres columnas o menos de la planta de la casa (0..10 × 0..8).
                        int dx = Math.max(0, Math.max(-x, x - 10));
                        int dz = Math.max(0, Math.max(-z, z - 8));
                        if (Math.max(dx, dz) <= 3) {
                            nearWalls++;
                        }
                    }
                }
            }
        }
        assertTrue(rubble >= stoneLost * 0.4, "escombro " + rubble + " de " + stoneLost + " bloques de piedra perdidos");
        assertTrue(nearWalls >= rubble * 0.85, "escombro lejos de los muros: " + (rubble - nearWalls) + " de " + rubble);
        int inside = 0;
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 7; z++) {
                inside += building(after.get(x, 1, z)) && !building(casa.world().get(x, 1, z)) ? 1 : 0;
            }
        }
        assertTrue(inside > 0, "también cae escombro dentro de la casa");
    }

    @Test
    void ivyAlsoClimbsFromTheGround() {
        Scene casa = RuinWorld.casa();
        boolean climbing = false;
        for (long seed = 1; seed <= 4 && !climbing; seed++) {
            RuinWorld after = weathered(casa, 60, 50, seed, 100);
            Box box = casa.box();
            for (int x = box.minX(); x <= box.maxX() && !climbing; x++) {
                for (int z = box.minZ(); z <= box.maxZ() && !climbing; z++) {
                    climbing = after.get(x, 1, z).startsWith("minecraft:vine")
                            && after.get(x, 2, z).startsWith("minecraft:vine")
                            && after.get(x, 0, z).startsWith("minecraft:grass_block");
                }
            }
        }
        assertTrue(climbing, "ninguna hiedra sube desde el suelo");
    }

    @Test
    void sameSeedGivesSameRuinOnEveryScene() {
        for (Scene scene : scenes()) {
            WeatheringSettings settings = settings(65, 50, 70, 77L, WeatherStyle.TEMPERATE, true);
            Result first = WeatheringEngine.weather(scene.world(), scene.box(), Selection.all(), settings);
            Result second = WeatheringEngine.weather(scene.world(), scene.box(), Selection.all(), settings);
            assertEquals(first, second, scene.name());
        }
    }

    private static List<Scene> scenes() {
        return List.of(RuinWorld.casa(), RuinWorld.torre(), RuinWorld.muro());
    }

    private static RuinWorld weathered(Scene scene, int intensity, int collapse, long seed) {
        return weathered(scene, intensity, collapse, seed, 50);
    }

    private static RuinWorld weathered(Scene scene, int intensity, int collapse, long seed, int vegetation) {
        return scene.world().apply(WeatheringEngine.weather(scene.world(), scene.box(), Selection.all(),
                settings(intensity, collapse, vegetation, seed, WeatherStyle.TEMPERATE, false)));
    }

    /** Altura de obra que sigue en pie desde el suelo, sin cortes, en la columna. */
    private static int standing(RuinWorld world, int x, int z) {
        int height = 0;
        while (building(world.get(x, height + 1, z))) {
            height++;
        }
        return height;
    }

    private static boolean building(String state) {
        return AgingTable.kind(BlockText.parse(state)) == AgingTable.Kind.BUILDING;
    }

    /** Material de obra que carga: ni vidrio ni paneles. */
    private static boolean structural(String state) {
        BlockText block = BlockText.parse(state);
        return AgingTable.kind(block) == AgingTable.Kind.BUILDING
                && AgingTable.material(block) != AgingTable.Material.GLASS && !block.path().endsWith("_pane");
    }

    /** Primer bloque de obra de la caja sin unión (por caras) con el terreno, o {@code null}. */
    private static String floating(RuinWorld world, Box box) {
        Box around = new Box(box.minX() - 1, box.minY() - 1, box.minZ() - 1, box.maxX() + 1, box.maxY() + 1,
                box.maxZ() + 1);
        Set<Long> reached = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = around.minX(); x <= around.maxX(); x++) {
            for (int z = around.minZ(); z <= around.maxZ(); z++) {
                if (!world.air(x, around.minY(), z)) {
                    reached.add(key(x, around.minY(), z));
                    queue.add(new int[]{x, around.minY(), z});
                }
            }
        }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] step : steps) {
                int x = cell[0] + step[0];
                int y = cell[1] + step[1];
                int z = cell[2] + step[2];
                if (!around.contains(x, y, z) || reached.contains(key(x, y, z))) {
                    continue;
                }
                AgingTable.Kind kind = AgingTable.kind(BlockText.parse(world.get(x, y, z)));
                if (kind == AgingTable.Kind.BUILDING || kind == AgingTable.Kind.NATURAL
                        || kind == AgingTable.Kind.PROTECTED) {
                    reached.add(key(x, y, z));
                    queue.add(new int[]{x, y, z});
                }
            }
        }
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (building(world.get(x, y, z)) && !reached.contains(key(x, y, z))) {
                        return x + "," + y + "," + z + " " + world.get(x, y, z);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Cuánto se agrupa una variante en la piedra que sigue en pie: {proporción de bloques con la variante, cuántas
     * veces más probable es que un vecino la tenga también que lo que daría el azar}. Con ruido blanco el segundo
     * valor ronda 1.
     */
    private static double[] clustering(Scene scene, RuinWorld after, String variant) {
        Box box = scene.box();
        int total = 0;
        int marked = 0;
        int pairs = 0;
        int both = 0;
        int[][] steps = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (!sameStone(scene.world().get(x, y, z), after.get(x, y, z))) {
                        continue;
                    }
                    boolean mine = after.get(x, y, z).contains(variant);
                    total++;
                    marked += mine ? 1 : 0;
                    for (int[] step : steps) {
                        int ox = x + step[0];
                        int oy = y + step[1];
                        int oz = z + step[2];
                        if (!sameStone(scene.world().get(ox, oy, oz), after.get(ox, oy, oz))) {
                            continue;
                        }
                        boolean theirs = after.get(ox, oy, oz).contains(variant);
                        if (mine || theirs) {
                            pairs++;
                            both += mine && theirs ? 1 : 0;
                        }
                    }
                }
            }
        }
        double share = marked / (double) Math.max(1, total);
        // Entre los pares en los que alguno tiene la variante, la parte en que la tienen los dos; al azar sería
        // p² / (2p − p²) = p / (2 − p).
        double observed = both / (double) Math.max(1, pairs);
        double random = share / (2.0 - share);
        return new double[]{share, observed / Math.max(1e-9, random)};
    }

    /** El bloque original era ladrillo de piedra o adoquín y sigue siéndolo (con o sin variante). */
    private static boolean sameStone(String before, String after) {
        String path = BlockText.parse(before).path();
        if (!path.equals("stone_bricks") && !path.equals("cobblestone")) {
            return false;
        }
        String now = BlockText.parse(after).path();
        return now.endsWith(path);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static Result run(RuinWorld world, WeatherStyle style) {
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
}
