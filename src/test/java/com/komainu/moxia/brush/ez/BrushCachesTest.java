package com.komainu.moxia.brush.ez;

import com.komainu.moxia.gradient.PaletteSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que el pincel ya no rehace en cada bloque o en cada clic da lo mismo que antes. */
class BrushCachesTest {
    @Test
    void canonicalIdsAreNotCopiedAndTheRestStillNormalize() {
        String id = "minecraft:mossy_cobblestone";
        assertSame(id, PaletteShift.normalize(id), "el id de WorldEdit se usa tal cual");
        assertEquals("minecraft:stone", PaletteShift.normalize("stone"));
        assertEquals("minecraft:stone", PaletteShift.normalize(" Stone "));
        assertEquals("minecraft:oak_stairs", PaletteShift.normalize("oak_stairs[facing=east]"));
        assertEquals("minecraft:stone", PaletteShift.normalize("MINECRAFT:STONE"));
        assertEquals("mod:Ébano".toLowerCase(java.util.Locale.ROOT), PaletteShift.normalize("mod:Ébano"));
    }

    @Test
    void aLongPaletteAsAMaskMatchesTheSameBlocks() {
        List<String> magma = PaletteSpec.parse("##Magma");
        BlockFilter mask = BlockFilter.parse(String.join(",", magma) + ",!" + magma.getFirst());
        for (String entry : magma) {
            boolean excluded = entry.equals(magma.getFirst());
            assertEquals(!excluded, mask.matches(PaletteShift.normalize(entry), BrushWorld.Cell.FULL), entry);
        }
        assertFalse(mask.matches("minecraft:diamond_block", BrushWorld.Cell.FULL));
        BlockFilter mixed = BlockFilter.parse("#solid,!sand,grass_block");
        assertTrue(mixed.matches("minecraft:oak_slab", BrushWorld.Cell.SHAPED), "las clases siguen valiendo");
        assertTrue(mixed.matches("minecraft:grass_block", BrushWorld.Cell.OTHER), "y los ids sueltos");
        assertFalse(mixed.matches("minecraft:sand", BrushWorld.Cell.FULL), "y las exclusiones");
        assertFalse(mixed.matches("minecraft:poppy", BrushWorld.Cell.OTHER));
    }

    @Test
    void aSimpleMaskIsReadOncePerBrush() {
        assertSame(BlockFilter.FULL_BLOCK, BrushMasks.fixed(null), "sin -m, la de serie");
        BrushMask list = BrushMasks.fixed("grass_block,moss_block,!sand");
        assertTrue(list instanceof BlockFilter, "una lista no cambia de un clic a otro");
        assertSame(list, BrushMasks.forClick(list, null, "grass_block,moss_block,!sand"),
                "cada clic usa la ya leída");
        assertNull(BrushMasks.fixed("#hotbar"), "una de WorldEdit se lee en cada clic (la barra cambia)");
        assertNull(BrushMasks.fixed("#aim"));
        assertNull(BrushMasks.fixed("#fuzzypalette[##Magma]"));
    }
}
