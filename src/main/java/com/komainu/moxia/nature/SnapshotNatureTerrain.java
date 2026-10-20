package com.komainu.moxia.nature;

import com.komainu.moxia.sculpt.WorldSnapshot;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * La foto capturada vista como {@link NatureTerrain}. Clasifica cada estado distinto una sola vez; se usa desde el
 * worker y nunca toca el mundo. Fuera de la foto todo cuenta como bloque duro: nada crece hacia donde no se ha leído.
 */
final class SnapshotNatureTerrain implements NatureTerrain {
    private static final Set<String> SOIL = Set.of(
            "grass_block", "dirt", "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "moss_block", "mud",
            "muddy_mangrove_roots", "sand", "red_sand", "gravel", "clay", "snow_block", "snow", "powder_snow",
            "farmland", "dirt_path", "soul_sand", "soul_soil", "suspicious_sand", "suspicious_gravel",
            "packed_mud");
    private static final Set<String> STONE = Set.of(
            "stone", "granite", "diorite", "andesite", "deepslate", "cobbled_deepslate", "tuff", "calcite",
            "cobblestone", "mossy_cobblestone", "sandstone", "red_sandstone", "terracotta", "dripstone_block",
            "basalt", "smooth_basalt", "blackstone", "netherrack", "end_stone", "magma_block", "obsidian",
            "packed_ice", "ice", "blue_ice");
    private static final String[] PLANTS = {"_sapling", "_tulip", "flower", "mushroom", "dandelion", "poppy",
            "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "lily_of_the_valley", "rose_bush", "peony",
            "lilac", "pink_petals", "sweet_berry_bush", "dead_bush", "fern", "short_grass", "tall_grass",
            "moss_carpet", "leaf_litter", "bush", "sprouts", "hanging_roots", "crimson_roots", "warped_roots",
            "fungus", "pitcher_plant", "torchflower"};

    private final WorldSnapshot snapshot;
    private final Map<BlockState, Kind> kinds = new IdentityHashMap<>();

    SnapshotNatureTerrain(WorldSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public Kind kind(int x, int y, int z) {
        if (!snapshot.contains(x, y, z)) {
            return Kind.HARD;
        }
        return kinds.computeIfAbsent(snapshot.get(x, y, z), SnapshotNatureTerrain::classify);
    }

    static Kind classify(BlockState state) {
        BlockType type = state.getBlockType();
        var material = type.getMaterial();
        if (material.isAir()) {
            return Kind.FREE;
        }
        if (material.isLiquid()) {
            return Kind.LIQUID;
        }
        String id = type.id();
        String name = id.substring(id.indexOf(':') + 1);
        if (name.endsWith("_leaves")) {
            return Kind.LEAVES;
        }
        if (SOIL.contains(name)) {
            // Una capa de nieve fina se reemplaza como la hierba.
            return name.equals("snow") && material.isReplacedDuringPlacement() ? Kind.FREE : Kind.SOIL;
        }
        if (STONE.contains(name) || name.endsWith("_terracotta") && !name.endsWith("glazed_terracotta")) {
            return Kind.STONE;
        }
        if (material.isReplacedDuringPlacement()) {
            return Kind.FREE;
        }
        if (!material.isSolid() && !material.isFullCube() && plant(name)) {
            return Kind.FREE;
        }
        return Kind.HARD;
    }

    private static boolean plant(String name) {
        for (String plant : PLANTS) {
            if (name.contains(plant)) {
                return true;
            }
        }
        return false;
    }
}
