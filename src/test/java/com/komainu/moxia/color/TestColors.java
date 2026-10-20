package com.komainu.moxia.color;

import com.komainu.moxia.texture.PaletteRemap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catálogo de pruebas con los bloques vanilla de la tabla de texturas. Las banderas de forma, que en el juego salen
 * del estado de cada bloque, aquí se deducen del nombre: es una aproximación suficiente para los filtros de los
 * tests (cubos con la misma textura, troncos, arenisca… son cubos; el cristal, el hielo y las hojas no son opacos;
 * las piezas y las plantas no son cubos).
 */
public final class TestColors {
    /** Cubos con caras distintas (no llevan la «u» en la tabla). */
    private static final List<String> CUBE_SUFFIXES = List.of("_log", "_wood", "_stem", "_hyphae", "sandstone",
            "_pillar", "_ore", "bookshelf", "_block", "basalt", "deepslate", "grass_block", "podzol", "mycelium",
            "crafting_table", "furnace", "pumpkin", "melon", "lamp", "observer", "dispenser", "dropper", "tnt",
            "target", "loom", "smoker", "barrel", "beehive", "bee_nest", "shroomlight", "chiseled_quartz_block",
            "_terracotta", "smooth_stone", "quartz_bricks", "_planks", "_bricks", "_tiles", "_concrete", "_wool");
    private static final Set<String> NOT_CUBES = Set.of("minecraft:snow", "minecraft:moss_carpet",
            "minecraft:pumpkin_stem", "minecraft:melon_stem", "minecraft:attached_pumpkin_stem",
            "minecraft:attached_melon_stem", "minecraft:bamboo");
    /** Bloques que dan luz (en el juego se mira su luminosidad). */
    private static final List<String> LIGHTS = List.of("glowstone", "sea_lantern", "shroomlight", "froglight",
            "magma_block", "jack_o_lantern", "crying_obsidian", "beacon", "lantern", "torch", "end_rod");
    /** Va después de las tablas de nombres: se monta con ellas. */
    static final BlockColorCatalog VANILLA = vanilla();

    private TestColors() {
    }

    public static BlockColorCatalog vanilla() {
        List<BlockColor> blocks = new ArrayList<>();
        for (Map.Entry<String, BlockColorCatalog.Measured> entry : BlockColorCatalog.measured().entrySet()) {
            String id = entry.getKey();
            BlockColorCatalog.Measured measured = entry.getValue();
            blocks.add(BlockColor.of(id, measured.rgb(), flags(id, measured.uniform(), measured.patterned()), true));
        }
        return BlockColorCatalog.of(blocks);
    }

    static int flags(String id, boolean uniform, boolean patterned) {
        int flags = BlockColor.ITEM;
        if (!patterned && LIGHTS.stream().noneMatch(id::endsWith) && !id.contains(":suspicious_")) {
            flags |= BlockColor.PLAIN;
        }
        if (uniform) {
            flags |= BlockColor.UNIFORM;
        }
        boolean shaped = PaletteRemap.shapeOf(id) != null;
        boolean cube = !shaped && !NOT_CUBES.contains(id)
                && (uniform || CUBE_SUFFIXES.stream().anyMatch(id::endsWith));
        if (cube) {
            flags |= BlockColor.CUBE | BlockColor.SOLID;
            boolean clear = id.contains("glass") || id.endsWith("ice") || id.endsWith("leaves")
                    || id.endsWith("slime_block") || id.endsWith("honey_block");
            if (!clear) {
                flags |= BlockColor.OPAQUE;
            }
        } else if (shaped) {
            flags |= BlockColor.SOLID;
        }
        return flags;
    }

    static BlockColor block(String id) {
        return VANILLA.require(id);
    }
}
