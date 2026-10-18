package com.komainu.moxia.shape.smooth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Materiales por defecto (nombre y color más cercano) y sobreescrituras, con todos los bloques vanilla. */
class SmoothMaterialsColorTest {
    private static final VanillaCatalog CATALOG = VanillaCatalog.INSTANCE;

    @Test
    void colorTableCoversVanillaBlocksAndPieces() {
        var colors = SmoothMaterials.textureColors();
        assertTrue(colors.size() > 900, "la tabla trae casi todos los bloques: " + colors.size());
        assertTrue(CATALOG.pieces(PieceVariant.SLAB).size() > 50);
        assertTrue(CATALOG.pieces(PieceVariant.STAIR).size() > 50);
        assertTrue(CATALOG.pieces(PieceVariant.WALL).size() > 20);
        assertEquals(java.util.List.of("minecraft:snow"), CATALOG.pieces(PieceVariant.LAYER));
        assertTrue(CATALOG.pieces(PieceVariant.SLAB).stream().noneMatch(id -> id.contains("petrified")));
        assertEquals(ColorMatch.rgb(126, 126, 126), colors.get("minecraft:stone"));
    }

    @Test
    void sameNameFamilyWinsOverColor() {
        assertEquals("minecraft:stone_slab", resolve("stone", PieceVariant.SLAB));
        assertEquals("minecraft:stone_brick_stairs", resolve("minecraft:stone_bricks", PieceVariant.STAIR));
        assertEquals("minecraft:stone_brick_wall", resolve("minecraft:chiseled_stone_bricks", PieceVariant.WALL));
        assertEquals("minecraft:oak_slab", resolve("minecraft:oak_log", PieceVariant.SLAB));
        assertEquals("minecraft:oak_fence", resolve("minecraft:oak_planks", PieceVariant.WALL),
                "la madera no tiene muros: su valla hace de muro");
        assertEquals("minecraft:snow", resolve("minecraft:snow_block", PieceVariant.LAYER));
        assertEquals("minecraft:cobbled_deepslate_wall", resolve("minecraft:deepslate", PieceVariant.WALL));
        assertEquals("minecraft:waxed_oxidized_cut_copper_slab",
                resolve("minecraft:waxed_oxidized_chiseled_copper", PieceVariant.SLAB),
                "el prefijo chiseled_ también se quita en medio del nombre");
        assertEquals("minecraft:mud_brick_stairs", resolve("minecraft:packed_mud", PieceVariant.STAIR));
        assertEquals("minecraft:deepslate_tile_slab", resolve("minecraft:reinforced_deepslate", PieceVariant.SLAB));
    }

    @Test
    void blocksWithoutPiecesTakeTheClosestColor() {
        // Los ejemplos de la wiki de ezEdits.
        assertEquals("minecraft:deepslate_tile_slab", resolve("minecraft:gray_concrete", PieceVariant.SLAB));
        assertEquals("minecraft:smooth_quartz_slab", resolve("minecraft:white_wool", PieceVariant.SLAB));
        String clay = resolve("minecraft:clay", PieceVariant.SLAB);
        assertTrue(clay.contains("stone") || clay.contains("andesite") || clay.contains("diorite"),
                "la arcilla gris azulada va a una losa de piedra clara: " + clay);
        // Colores vivos: la lana roja a una losa rojiza, la lana negra a una oscura.
        assertTrue(lightness(resolve("minecraft:black_wool", PieceVariant.STAIR)) < 30);
        String red = resolve("minecraft:red_wool", PieceVariant.SLAB);
        int color = CATALOG.color(red);
        assertTrue((color >> 16 & 0xFF) > (color & 0xFF) + 40, "rojo → " + red);
        // Las capas solo existen en nieve: cualquier bloque las pide de nieve.
        assertEquals("minecraft:snow", resolve("minecraft:black_wool", PieceVariant.LAYER));
        assertNull(SmoothMaterials.nearest(-1, PieceVariant.SLAB, CATALOG));
    }

    @Test
    void everyOpaqueVanillaColorFindsSomePiece() {
        for (String id : SmoothMaterials.textureColors().keySet()) {
            for (PieceVariant variant : PieceVariant.values()) {
                String piece = SmoothMaterials.resolve(id, variant, CATALOG);
                assertTrue(piece != null && CATALOG.is(piece, variant), id + " → " + variant + ": " + piece);
            }
        }
    }

    @Test
    void overridesAcceptPiecesFamiliesAndPlainBlocks() {
        assertEquals("minecraft:acacia_slab", override("acacia", PieceVariant.SLAB));
        assertEquals("minecraft:acacia_slab", override("minecraft:acacia_slab", PieceVariant.SLAB));
        assertEquals("minecraft:spruce_stairs", override("spruce_planks", PieceVariant.STAIR));
        assertEquals("minecraft:cobblestone_wall", override("cobblestone", PieceVariant.WALL));
        assertEquals("minecraft:snow", override("snow", PieceVariant.LAYER));
        assertEquals("minecraft:deepslate_tile_stairs", override("gray_concrete", PieceVariant.STAIR),
                "un bloque sin piezas propias se cambia por la del color más parecido");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> override("unobtainium", PieceVariant.SLAB));
        assertTrue(error.getMessage().contains("unobtainium"), error.getMessage());
    }

    @Test
    void labDistanceIsPerceptual() {
        int gray = ColorMatch.rgb(128, 128, 128);
        assertEquals(0.0, ColorMatch.distanceSquared(gray, gray));
        double[] white = ColorMatch.lab(0xFFFFFF);
        assertEquals(100.0, white[0], 0.01);
        assertEquals(0.0, white[1], 0.01);
        assertEquals(0.0, ColorMatch.lab(0)[0], 0.01);
    }

    private static String resolve(String id, PieceVariant variant) {
        return SmoothMaterials.resolve(id, variant, CATALOG);
    }

    private static String override(String text, PieceVariant variant) {
        return SmoothMaterials.override(text, variant, CATALOG);
    }

    private static double lightness(String id) {
        return ColorMatch.lab(CATALOG.color(id))[0];
    }
}
