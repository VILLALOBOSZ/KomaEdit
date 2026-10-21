package com.komainu.moxia.rebuild;

import java.util.List;
import java.util.Set;

/**
 * Lo que la reconstrucción necesita saber de un bloque para el orden «como un constructor» y los retrasos entre
 * bloques iguales o distintos. En el servidor lo responde Minecraft (forma, etiquetas, clases); en las pruebas,
 * {@link #guess} a partir del id.
 *
 * @param type       el bloque sin propiedades ({@code minecraft:oak_stairs}); dos estados del mismo bloque son «iguales»
 * @param shape      su forma, para separar paredes, tejado y detalles
 * @param air        aire de cualquier tipo
 * @param pillar     tronco o pilar (troncos, madera, tallos, pilares, basalto): la estructura de la obra
 * @param vegetation hojas, flores, hierba, plantas, enredaderas, cultivos…
 * @param fluid      agua o lava
 */
public record BlockTraits(String type, Shape shape, boolean air, boolean pillar, boolean vegetation, boolean fluid) {
    /** Forma del bloque, solo lo que distingue las fases. */
    public enum Shape {
        /** Cubo entero (piedra, tablones, troncos, cristal, hojas). */
        FULL,
        STAIRS,
        SLAB,
        /** Cristales finos y barrotes: rellenan las paredes. */
        PANE,
        /** Todo lo demás: vallas, muros, puertas, antorchas, alfombras, faroles… */
        OTHER
    }

    public static final BlockTraits AIR = new BlockTraits("minecraft:air", Shape.OTHER, true, false, false, false);

    public BlockTraits {
        type = type == null || type.isBlank() ? "minecraft:air" : type;
        shape = shape == null ? Shape.OTHER : shape;
    }

    /** Un cubo entero que no es planta ni líquido: lo que sostiene una obra. */
    public boolean solid() {
        return !air && shape == Shape.FULL && !vegetation && !fluid;
    }

    private static final Set<String> AIRS = Set.of("air", "cave_air", "void_air");
    private static final Set<String> FLUIDS = Set.of("water", "lava", "bubble_column");
    private static final Set<String> FLOWERS = Set.of("dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
            "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose", "sunflower", "lilac", "rose_bush",
            "peony", "torchflower", "pitcher_plant", "pink_petals", "spore_blossom", "short_grass", "grass",
            "tall_grass", "fern", "large_fern", "dead_bush", "lily_pad", "sweet_berry_bush", "sugar_cane", "cactus",
            "bamboo", "bamboo_sapling", "kelp", "kelp_plant", "seagrass", "tall_seagrass", "wheat", "carrots",
            "potatoes", "beetroots", "melon_stem", "pumpkin_stem", "attached_melon_stem", "attached_pumpkin_stem",
            "cocoa", "nether_wart", "chorus_plant", "chorus_flower", "glow_lichen", "hanging_roots", "moss_carpet",
            "azalea", "flowering_azalea", "big_dripleaf", "big_dripleaf_stem", "small_dripleaf", "cave_vines",
            "cave_vines_plant", "weeping_vines", "weeping_vines_plant", "twisting_vines", "twisting_vines_plant",
            "crimson_roots", "warped_roots", "nether_sprouts", "mangrove_propagule", "torchflower_crop",
            "pitcher_crop", "crimson_fungus", "warped_fungus", "brown_mushroom", "red_mushroom", "vine",
            "pale_hanging_moss");
    private static final List<String> FLOWER_SUFFIXES = List.of("_leaves", "_sapling", "_tulip", "_vine", "_vines");
    private static final List<String> PILLARS = List.of("_log", "_wood", "_stem", "_hyphae", "pillar", "basalt",
            "bamboo_block");
    private static final List<String> PANES = List.of("_pane", "iron_bars");
    /** Muebles y piezas sueltas: van con los detalles aunque algunos sean cubos enteros (hornos, barriles…). */
    private static final List<String> OTHER = List.of("fence", "_wall", "door", "torch", "lantern", "carpet",
            "button", "pressure_plate", "sign", "banner", "_bed", "chest", "barrel", "flower_pot", "potted_", "rail",
            "ladder", "lever", "chain", "candle", "bell", "_head", "skull", "anvil", "redstone", "repeater",
            "comparator", "campfire", "cauldron", "hopper", "lectern", "grindstone", "stonecutter", "brewing_stand",
            "end_rod", "lightning_rod", "scaffolding", "cobweb", "tripwire", "conduit", "amethyst_cluster", "_bud",
            "pointed_dripstone", "turtle_egg", "sea_pickle", "coral_fan", "piston", "observer", "dispenser",
            "dropper", "furnace", "smoker", "composter", "beehive", "bee_nest", "jukebox", "note_block",
            "enchanting_table", "beacon", "decorated_pot", "vault", "trial_spawner", "spawner", "heavy_core");
    /** Cubos enteros cuyo nombre se parece a un detalle. */
    private static final Set<String> FULL_EXCEPTIONS = Set.of("sea_lantern", "jack_o_lantern", "redstone_block",
            "redstone_lamp", "redstone_ore", "deepslate_redstone_ore", "chiseled_bookshelf");

    /**
     * Rasgos aproximados por el id ({@code minecraft:oak_stairs[facing=east]}), sin Minecraft: para las pruebas, los
     * renders y los bloques que el juego no sepa describir. En el servidor se usan los rasgos de verdad.
     */
    public static BlockTraits guess(String state) {
        String text = state == null ? "" : state.strip();
        int properties = text.indexOf('[');
        int data = text.indexOf('{');
        int end = properties < 0 ? data : data < 0 ? properties : Math.min(properties, data);
        String type = end < 0 ? text : text.substring(0, end);
        if (!type.contains(":")) {
            type = "minecraft:" + type;
        }
        String path = type.substring(type.indexOf(':') + 1);
        if (AIRS.contains(path)) {
            return new BlockTraits(type, Shape.OTHER, true, false, false, false);
        }
        if (FLUIDS.contains(path)) {
            return new BlockTraits(type, Shape.OTHER, false, false, false, true);
        }
        boolean vegetation = !path.startsWith("potted_")
                && (FLOWERS.contains(path) || FLOWER_SUFFIXES.stream().anyMatch(path::endsWith));
        if (vegetation) {
            return new BlockTraits(type, path.endsWith("_leaves") ? Shape.FULL : Shape.OTHER, false, false, true,
                    false);
        }
        if (path.endsWith("_stairs")) {
            return new BlockTraits(type, Shape.STAIRS, false, false, false, false);
        }
        if (path.endsWith("_slab")) {
            return new BlockTraits(type, Shape.SLAB, false, false, false, false);
        }
        if (PANES.stream().anyMatch(path::endsWith)) {
            return new BlockTraits(type, Shape.PANE, false, false, false, false);
        }
        if (PILLARS.stream().anyMatch(path::contains) && !path.contains("stairs") && !path.contains("slab")
                && !path.contains("fence") && !path.contains("button") && !path.contains("door")
                && !path.contains("sign") && !path.contains("plate")) {
            return new BlockTraits(type, Shape.FULL, false, true, false, false);
        }
        if (path.equals("snow") || !FULL_EXCEPTIONS.contains(path) && OTHER.stream().anyMatch(path::contains)) {
            return new BlockTraits(type, Shape.OTHER, false, false, false, false);
        }
        return new BlockTraits(type, Shape.FULL, false, false, false, false);
    }
}
