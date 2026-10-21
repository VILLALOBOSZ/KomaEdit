package com.komainu.moxia.share;

import com.komainu.moxia.persistence.NamedDefinitions;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.sculpt.SculptProfile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Jugadores, almacenes en carpetas temporales y un catálogo de bloques pequeño para las pruebas de compartir. */
final class ShareFixtures {
    static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    static final UUID BRUNO = UUID.fromString("00000000-0000-0000-0000-0000000b0b00");

    private ShareFixtures() {
    }

    /** Un jugador con todos los permisos menos los de {@code denied}. */
    static ShareTarget player(UUID id, String name, String... denied) {
        Set<String> without = Set.of(denied);
        return new ShareTarget(id, name, node -> !without.contains(node));
    }

    /** Los tipos de serie con sus almacenes en {@code root/<jugador>/…}. */
    static ShareKinds.Registry kinds(Path root) {
        return kinds(root, new MemorySculpts(), new MemoryRecipes());
    }

    static ShareKinds.Registry kinds(Path root, MemorySculpts sculpts, MemoryRecipes recipes) {
        ShareKinds.Registry registry = new ShareKinds.Registry();
        registry.register(new PaletteKind(target -> store(root, target, "palettes")));
        registry.register(new NoiseKind(target -> store(root, target, "noises")));
        registry.register(NatureKind.tree(target -> store(root, target, "trees")));
        registry.register(NatureKind.rock(target -> store(root, target, "rocks")));
        registry.register(new SculptKind(sculpts));
        registry.register(new RecipeKind(recipes));
        return registry;
    }

    static NamedDefinitions store(Path root, ShareTarget target, String kind) {
        return new NamedDefinitions(root.resolve(target.id().toString()).resolve(kind + ".json"), kind);
    }

    /** Un catálogo con unos pocos bloques de Minecraft y sus propiedades. */
    static BlockCatalog catalog() {
        Map<String, Map<String, List<String>>> blocks = new LinkedHashMap<>();
        Map<String, List<String>> stairs = Map.of("facing", List.of("north", "south", "west", "east"),
                "half", List.of("top", "bottom"), "shape", List.of("straight", "inner_left", "inner_right",
                        "outer_left", "outer_right"), "waterlogged", List.of("true", "false"));
        Map<String, List<String>> slab = Map.of("type", List.of("top", "bottom", "double"),
                "waterlogged", List.of("true", "false"));
        for (String plain : List.of("air", "cave_air", "stone", "andesite", "polished_andesite", "cobblestone",
                "oak_planks", "spruce_planks", "dirt", "glass", "white_wool", "moss_block", "tuff", "deepslate",
                "calcite", "gravel")) {
            blocks.put("minecraft:" + plain, Map.of());
        }
        for (String wood : List.of("oak", "spruce", "andesite", "stone_brick")) {
            blocks.put("minecraft:" + wood + "_stairs", stairs);
            blocks.put("minecraft:" + wood + "_slab", slab);
        }
        blocks.put("minecraft:oak_log", Map.of("axis", List.of("x", "y", "z")));
        blocks.put("minecraft:grass_block", Map.of("snowy", List.of("true", "false")));
        blocks.put("minecraft:chest", Map.of("facing", List.of("north", "south", "west", "east"),
                "type", List.of("single", "left", "right"), "waterlogged", List.of("true", "false")));
        blocks.put("minecraft:oak_sign", Map.of("rotation", List.of("0", "1", "2", "3", "4", "5", "6", "7", "8",
                "9", "10", "11", "12", "13", "14", "15"), "waterlogged", List.of("true", "false")));
        blocks.put("minecraft:spawner", Map.of());
        blocks.put("minecraft:command_block", Map.of("facing", List.of("north", "south", "west", "east", "up",
                "down"), "conditional", List.of("true", "false")));
        return BlockCatalog.of(blocks);
    }

    static ShareCheck check() {
        return new ShareCheck(catalog(), ShareLimits.DEFAULT);
    }

    /** Presets de escultura en memoria, por jugador. */
    static final class MemorySculpts implements SculptKind.Store {
        final Map<String, SculptProfile> saved = new LinkedHashMap<>();

        @Override
        public List<String> names(UUID player) {
            List<String> names = new ArrayList<>();
            saved.keySet().stream().filter(key -> key.startsWith(player + "/"))
                    .forEach(key -> names.add(key.substring(key.indexOf('/') + 1)));
            return names;
        }

        @Override
        public SculptProfile load(UUID player, String name) {
            SculptProfile profile = saved.get(player + "/" + name);
            if (profile == null) {
                throw new IllegalArgumentException("No existe el preset " + name);
            }
            return profile;
        }

        @Override
        public boolean exists(UUID player, String name) {
            return saved.containsKey(player + "/" + name);
        }

        @Override
        public void save(UUID player, String name, SculptProfile profile) {
            saved.put(player + "/" + name, profile);
        }
    }

    /** Recetas en memoria, por jugador. */
    static final class MemoryRecipes implements RecipeKind.Store {
        final Map<String, ProceduralRecipe> saved = new LinkedHashMap<>();

        @Override
        public List<String> names(UUID player) {
            List<String> names = new ArrayList<>();
            saved.keySet().stream().filter(key -> key.startsWith(player + "/"))
                    .forEach(key -> names.add(key.substring(key.indexOf('/') + 1)));
            return names;
        }

        @Override
        public ProceduralRecipe load(UUID player, String name) {
            ProceduralRecipe recipe = saved.get(player + "/" + name);
            if (recipe == null) {
                throw new IllegalArgumentException("No existe la receta " + name);
            }
            return recipe;
        }

        @Override
        public boolean exists(UUID player, String name) {
            return saved.containsKey(player + "/" + name);
        }

        @Override
        public void save(UUID player, ProceduralRecipe recipe) {
            saved.put(player + "/" + recipe.name(), recipe);
        }
    }
}
