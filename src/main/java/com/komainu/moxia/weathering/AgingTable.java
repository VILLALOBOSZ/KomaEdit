package com.komainu.moxia.weathering;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Qué es cada bloque para el envejecimiento y en qué se convierte con los años. Solo bloques vanilla; los de otros
 * mods se tratan como piedra que se desgasta pero no cambia de variante.
 *
 * <p>Criterio de las variantes (todas conservan las propiedades del original, así una escalera musgosa mira hacia
 * el mismo lado):</p>
 * <ul>
 *     <li><b>Musgo</b>: solo donde Minecraft tiene versión musgosa (adoquín y ladrillo de piedra con sus escaleras,
 *     losas y muros). Prende abajo, en las caras norte y a la sombra.</li>
 *     <li><b>Grieta</b>: ladrillos con versión agrietada (piedra, pizarra profunda en ladrillo y baldosa, Nether,
 *     piedra negra pulida). Sale en lo más expuesto y alto; la helada del clima nevado agrieta más.</li>
 *     <li><b>Desgaste</b>: lo pulido o tallado pierde el acabado (andesita pulida → andesita, piedra lisa → piedra,
 *     arenisca cortada → arenisca, escalera de piedra → de adoquín). En el desierto el ladrillo se vuelve
 *     terracota.</li>
 *     <li><b>Óxido</b>: el cobre avanza de normal a expuesto, erosionado y oxidado según la intensidad. El encerado
 *     no se toca: para eso se enceró.</li>
 *     <li><b>Madera</b>: troncos y madera pierden la corteza (descortezado); los tablones se pudren a losa o se
 *     caen y dejan huecos.</li>
 * </ul>
 */
public final class AgingTable {
    public enum Kind {
        /** Aire: no ocupa nada. */
        AIR,
        /** Agua o lava. */
        FLUID,
        /** Cosas pequeñas sin cuerpo (flores, antorchas, alfombras, enredaderas): no sostienen nada. */
        DECOR,
        /** Bloques con contenido o de dos piezas (cofres, puertas, camas, generadores): nunca se tocan. */
        PROTECTED,
        /** Terreno: sostiene la obra y recibe vegetación, pero no se desgasta. */
        NATURAL,
        /** Material de construcción: se desgasta, envejece y puede caerse. */
        BUILDING
    }

    /**
     * Cómo se comporta el material con los años: lo pronto que se pierde (ritmo y retraso), lo que debilita un muro,
     * el talud de su borde roto, cuánto vuela sin apoyo (vano) y qué parte de lo que cae no deja escombro.
     */
    public enum Material {
        STONE(0.8, 0.19, 0.0, 2, 0, 0.3),
        HARD(0.7, 0.3, -0.25, 3, 1, 0.25),
        WOOD(0.95, -0.05, 0.45, 1, 2, 0.55),
        GLASS(1.5, 0.0, 0.5, 1, -1, 1.0),
        SOFT(2.2, 0.0, 0.8, 1, -1, 0.95),
        METAL(0.8, 0.25, -0.1, 2, 2, 0.4);

        private final double rate;
        private final double delay;
        private final double weakness;
        private final int slope;
        private final int span;
        private final double shatter;

        Material(double rate, double delay, double weakness, int slope, int span, double shatter) {
            this.rate = rate;
            this.delay = delay;
            this.weakness = weakness;
            this.slope = slope;
            this.span = span;
            this.shatter = shatter;
        }

        /**
         * Cuánto se ha perdido de las piezas sueltas (tejados, forjados, vidrio) a esta intensidad, de 0 a 1: la
         * lana y el vidrio se van enseguida, la madera pronto, la piedra tarde y la pizarra la última.
         */
        public double decay(double intensity) {
            return Math.clamp((intensity - delay) * rate, 0.0, 1.0);
        }

        /** Lo que suma a la debilidad de un muro hecho de este material (la madera se pudre, la pizarra aguanta). */
        public double wallWeakness() {
            return weakness;
        }

        /** Bloques que puede bajar el borde roto de un muro de una columna a la siguiente. */
        public int slope() {
            return slope;
        }

        /** Bloques de vano extra que aguanta sin apoyo respecto a la piedra. */
        public int spanBonus() {
            return span;
        }

        /** Parte de lo que cae que se pulveriza y no deja escombro. */
        public double shatter() {
            return shatter;
        }
    }

    /** Tipo de envejecimiento de una variante. */
    public enum Aging {
        MOSS, CRACK, WEAR, OXIDIZE, STRIP, ROT
    }

    private static final Map<String, String> MOSS = new HashMap<>();
    private static final Map<String, String> CRACK = new HashMap<>();
    private static final Map<String, String> WEAR = new HashMap<>();
    private static final Map<String, String> STRIP = new HashMap<>();
    private static final Map<String, String> OXIDIZE = new HashMap<>();
    private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped");
    private static final Set<String> NATURAL = Set.of("grass_block", "dirt", "coarse_dirt", "rooted_dirt", "podzol",
            "mycelium", "dirt_path", "farmland", "mud", "clay", "sand", "red_sand", "gravel", "stone", "deepslate",
            "granite", "diorite", "andesite", "tuff", "calcite", "bedrock", "netherrack", "end_stone", "moss_block",
            "snow_block", "powder_snow", "ice", "packed_ice", "blue_ice", "soul_sand", "soul_soil", "basalt",
            "dripstone_block", "obsidian", "crying_obsidian", "magma_block", "sculk", "suspicious_sand",
            "suspicious_gravel");
    private static final List<String> PROTECTED_PARTS = List.of("chest", "barrel", "shulker_box", "furnace",
            "smoker", "hopper", "dispenser", "dropper", "spawner", "beacon", "brewing_stand", "lectern", "jukebox",
            "enchanting_table", "_bed", "command_block", "structure_block", "jigsaw", "decorated_pot", "vault",
            "crafter", "bedrock", "end_portal", "nether_portal", "respawn_anchor", "conduit", "chiseled_bookshelf");
    private static final List<String> DECOR_PARTS = List.of("torch", "lantern", "candle", "carpet", "vine",
            "cobweb", "button", "lever", "pressure_plate", "rail", "sign", "banner", "ladder", "tripwire",
            "redstone_wire", "dead_bush", "seagrass", "kelp", "sea_pickle", "lichen", "roots", "flower", "tulip",
            "poppy", "dandelion", "orchid", "allium", "azure_bluet", "daisy", "cornflower", "lily_of_the_valley",
            "rose_bush", "peony", "lilac", "sunflower", "sapling", "potted_", "_head", "skull", "chain", "string",
            "sugar_cane", "fire", "short_grass", "tall_grass", "fern", "sweet_berry", "wheat", "carrots", "potatoes",
            "beetroots", "pitcher", "torchflower", "spore_blossom", "moss_carpet", "glow_berries", "cave_vines",
            "hanging_", "frogspawn", "snow", "coral", "bubble_column", "end_rod", "lightning_rod", "repeater",
            "comparator", "tripwire_hook", "scaffolding", "pointed_dripstone", "amethyst_cluster", "_bud");

    static {
        for (String suffix : new String[]{"", "_stairs", "_slab", "_wall"}) {
            MOSS.put("cobblestone" + suffix, "mossy_cobblestone" + suffix);
        }
        MOSS.put("stone_bricks", "mossy_stone_bricks");
        for (String suffix : new String[]{"_stairs", "_slab", "_wall"}) {
            MOSS.put("stone_brick" + suffix, "mossy_stone_brick" + suffix);
        }
        MOSS.put("infested_stone_bricks", "infested_mossy_stone_bricks");
        MOSS.put("infested_cobblestone", "mossy_cobblestone");

        CRACK.put("stone_bricks", "cracked_stone_bricks");
        CRACK.put("chiseled_stone_bricks", "cracked_stone_bricks");
        CRACK.put("deepslate_bricks", "cracked_deepslate_bricks");
        CRACK.put("deepslate_tiles", "cracked_deepslate_tiles");
        CRACK.put("nether_bricks", "cracked_nether_bricks");
        CRACK.put("polished_blackstone_bricks", "cracked_polished_blackstone_bricks");
        CRACK.put("infested_stone_bricks", "infested_cracked_stone_bricks");

        for (String rock : new String[]{"andesite", "diorite", "granite"}) {
            WEAR.put("polished_" + rock, rock);
            WEAR.put("polished_" + rock + "_stairs", rock + "_stairs");
            WEAR.put("polished_" + rock + "_slab", rock + "_slab");
        }
        for (String suffix : new String[]{"", "_stairs", "_slab", "_wall"}) {
            WEAR.put("polished_deepslate" + suffix, "cobbled_deepslate" + suffix);
            WEAR.put("polished_blackstone" + suffix, "blackstone" + suffix);
        }
        WEAR.put("polished_tuff", "tuff");
        WEAR.put("smooth_stone", "stone");
        WEAR.put("smooth_stone_slab", "stone_slab");
        WEAR.put("stone_stairs", "cobblestone_stairs");
        WEAR.put("stone_slab", "cobblestone_slab");
        for (String sand : new String[]{"sandstone", "red_sandstone"}) {
            WEAR.put("cut_" + sand, sand);
            WEAR.put("chiseled_" + sand, sand);
            WEAR.put("smooth_" + sand, sand);
            WEAR.put("cut_" + sand + "_slab", sand + "_slab");
            WEAR.put("smooth_" + sand + "_slab", sand + "_slab");
            WEAR.put("smooth_" + sand + "_stairs", sand + "_stairs");
        }
        WEAR.put("chiseled_deepslate", "cobbled_deepslate");
        WEAR.put("quartz_bricks", "quartz_block");
        WEAR.put("chiseled_quartz_block", "quartz_block");
        WEAR.put("smooth_quartz", "quartz_block");

        for (String wood : WOODS) {
            String log = switch (wood) {
                case "crimson", "warped" -> wood + "_stem";
                case "bamboo" -> "bamboo_block";
                default -> wood + "_log";
            };
            STRIP.put(log, "stripped_" + log);
            String wholeWood = switch (wood) {
                case "crimson", "warped" -> wood + "_hyphae";
                case "bamboo" -> null;
                default -> wood + "_wood";
            };
            if (wholeWood != null) {
                STRIP.put(wholeWood, "stripped_" + wholeWood);
            }
        }

        String[] copper = {"copper_block", "cut_copper", "cut_copper_stairs", "cut_copper_slab", "chiseled_copper",
                "copper_grate", "copper_door", "copper_trapdoor", "copper_bulb"};
        for (String block : copper) {
            String base = block.equals("copper_block") ? "copper" : block;
            OXIDIZE.put(block, "exposed_" + base);
            OXIDIZE.put("exposed_" + base, "weathered_" + base);
            OXIDIZE.put("weathered_" + base, "oxidized_" + base);
        }
    }

    private AgingTable() {
    }

    public static Kind kind(BlockText block) {
        String path = block.path();
        if (path.equals("air") || path.equals("cave_air") || path.equals("void_air")) {
            return Kind.AIR;
        }
        if (path.equals("water") || path.equals("lava")) {
            return Kind.FLUID;
        }
        if (path.endsWith("_door") || containsAny(path, PROTECTED_PARTS)) {
            return Kind.PROTECTED;
        }
        if (NATURAL.contains(path) || path.endsWith("_ore")) {
            return Kind.NATURAL;
        }
        if (isDecor(path)) {
            return Kind.DECOR;
        }
        return Kind.BUILDING;
    }

    private static boolean isDecor(String path) {
        if (path.endsWith("_block") || path.endsWith("_wall") || path.endsWith("_slab") || path.endsWith("_stairs")
                || path.endsWith("_leaves") || path.equals("sea_lantern") || path.equals("jack_o_lantern")) {
            return false;
        }
        return containsAny(path, DECOR_PARTS) || path.endsWith("_mushroom") || path.equals("grass");
    }

    public static Material material(BlockText block) {
        String path = block.path();
        if (path.contains("glass") || path.equals("ice")) {
            return Material.GLASS;
        }
        if (path.endsWith("_wool") || path.equals("hay_block") || path.contains("leaves") || path.endsWith("_bed")
                || path.equals("bookshelf") || path.contains("mushroom_block") || path.equals("mushroom_stem")) {
            return Material.SOFT;
        }
        if (path.contains("copper") || path.contains("iron") || path.contains("gold") || path.equals("chain")
                || path.contains("anvil")) {
            return Material.METAL;
        }
        if (isWood(path)) {
            return Material.WOOD;
        }
        if (path.contains("deepslate") || path.contains("obsidian") || path.contains("blackstone")
                || path.contains("end_stone") || path.contains("purpur")) {
            return Material.HARD;
        }
        return Material.STONE;
    }

    public static boolean isWood(String path) {
        if (path.contains("planks") || path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem")
                || path.endsWith("_hyphae") || path.startsWith("stripped_") || path.equals("bamboo_block")
                || path.equals("bamboo_mosaic")) {
            return true;
        }
        for (String wood : WOODS) {
            if (path.startsWith(wood + "_")) {
                return true;
            }
        }
        return false;
    }

    /** Variante envejecida de un tipo, o {@code null} si ese bloque no tiene. */
    public static BlockText variant(BlockText block, Aging aging, WeatherStyle style) {
        if (!block.vanilla()) {
            return null;
        }
        String path = block.path();
        String next = switch (aging) {
            case MOSS -> MOSS.get(path);
            case CRACK -> CRACK.get(path);
            case WEAR -> style == WeatherStyle.DESERT && path.equals("bricks") ? "terracotta" : WEAR.get(path);
            case STRIP -> STRIP.get(path);
            case OXIDIZE -> path.startsWith("waxed_") ? null : OXIDIZE.get(path);
            case ROT -> rot(path);
        };
        if (next == null) {
            return null;
        }
        if (aging == Aging.ROT) {
            return BlockText.of(next, "type", "bottom", "waterlogged", "false");
        }
        return block.withPath(next);
    }

    /** Tablones podridos: se quedan en media losa de la misma madera. */
    private static String rot(String path) {
        if (!path.endsWith("_planks")) {
            return null;
        }
        return path.substring(0, path.length() - "_planks".length()) + "_slab";
    }

    /** Pasos de óxido hasta el cobre oxidado ({@code 0} si ya está oxidado o no es cobre que se oxide). */
    public static int oxidationSteps(BlockText block, int steps) {
        BlockText current = block;
        int done = 0;
        while (done < steps && variant(current, Aging.OXIDIZE, WeatherStyle.TEMPERATE) != null) {
            current = variant(current, Aging.OXIDIZE, WeatherStyle.TEMPERATE);
            done++;
        }
        return done;
    }

    /** Si el bloque es una pieza entera por arriba, sobre la que crece algo o se posa nieve. */
    public static boolean fullTop(BlockText block) {
        String path = block.path();
        if (path.endsWith("_slab")) {
            return "top".equals(block.property("type")) || "double".equals(block.property("type"));
        }
        if (path.endsWith("_stairs")) {
            return "top".equals(block.property("half"));
        }
        return !(path.endsWith("_wall") || path.endsWith("_fence") || path.endsWith("_fence_gate")
                || path.endsWith("_pane") || path.endsWith("_bars") || path.endsWith("_trapdoor")
                || path.contains("glass") || path.equals("ice") || path.contains("leaves")
                || path.equals("dirt_path") || path.equals("farmland") || path.equals("cactus"));
    }

    /** Terreno o bloque donde puede crecer hierba de verdad (no solo musgo). */
    public static boolean soil(BlockText block) {
        String path = block.path();
        return path.equals("grass_block") || path.equals("dirt") || path.equals("coarse_dirt")
                || path.equals("podzol") || path.equals("moss_block") || path.equals("rooted_dirt")
                || path.equals("mud");
    }

    /** Familia de escombro: bloque entero, losa y escalera en los que se rompe lo que cae. */
    public static Rubble rubble(BlockText block, WeatherStyle style) {
        String path = block.path();
        if (isWood(path)) {
            String wood = woodOf(path);
            if (wood != null) {
                return new Rubble(wood + "_planks", wood + "_slab", wood + "_stairs");
            }
        }
        if (path.contains("deepslate")) {
            return new Rubble("cobbled_deepslate", "cobbled_deepslate_slab", "cobbled_deepslate_stairs");
        }
        if (path.contains("blackstone")) {
            return new Rubble("blackstone", "blackstone_slab", "blackstone_stairs");
        }
        if (path.contains("red_sandstone")) {
            return new Rubble("red_sandstone", "red_sandstone_slab", "red_sandstone_stairs");
        }
        if (path.contains("sandstone") || style == WeatherStyle.DESERT && !path.contains("brick")) {
            return new Rubble("sandstone", "sandstone_slab", "sandstone_stairs");
        }
        if (path.contains("nether_brick")) {
            return new Rubble("nether_bricks", "nether_brick_slab", "nether_brick_stairs");
        }
        if (path.equals("bricks") || path.startsWith("brick_")) {
            return new Rubble("bricks", "brick_slab", "brick_stairs");
        }
        if (path.contains("mud_brick")) {
            return new Rubble("mud_bricks", "mud_brick_slab", "mud_brick_stairs");
        }
        if (path.contains("stone_brick")) {
            return new Rubble("cobblestone", "stone_brick_slab", "stone_brick_stairs");
        }
        if (path.contains("tuff")) {
            return new Rubble("tuff", "tuff_slab", "tuff_stairs");
        }
        return new Rubble("cobblestone", "cobblestone_slab", "cobblestone_stairs");
    }

    private static String woodOf(String path) {
        String trimmed = path.startsWith("stripped_") ? path.substring("stripped_".length()) : path;
        for (String wood : WOODS) {
            if (trimmed.startsWith(wood + "_")) {
                return wood;
            }
        }
        return null;
    }

    private static boolean containsAny(String path, List<String> parts) {
        for (String part : parts) {
            if (path.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /** Lo que deja un bloque al romperse: entero, losa y escalera del mismo material. */
    public record Rubble(String full, String slab, String stairs) {
    }
}
