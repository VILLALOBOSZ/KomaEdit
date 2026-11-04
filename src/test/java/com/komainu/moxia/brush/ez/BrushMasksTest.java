package com.komainu.moxia.brush.ez;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code -m oak_stairs[half=top]} en un pincel casaba con todas las escaleras de roble (la lista cortaba las
 * propiedades sin avisar) y {@code -m stone;andesite} no casaba con nada (la lista solo partía por comas).
 */
class BrushMasksTest {
    @Test
    void statesGoToWorldEditAndSemicolonsSplit() {
        assertNull(BrushMasks.fixed("oak_stairs[half=top]"), "con estado, la máscara de WorldEdit");
        assertNull(BrushMasks.fixed("grass_block[snowy=false],stone"));
        assertNotNull(BrushMasks.fixed("grass_block,stone"), "la lista sencilla sigue igual");
        BlockFilter both = BlockFilter.parse("stone;andesite");
        assertTrue(both.matches("minecraft:stone", BrushWorld.Cell.FULL));
        assertTrue(both.matches("minecraft:andesite", BrushWorld.Cell.FULL));
        assertFalse(both.matches("minecraft:dirt", BrushWorld.Cell.FULL));
        assertThrows(IllegalArgumentException.class, () -> BlockFilter.parse("oak_stairs[half=top]"),
                "-b del pincel de pintura: mejor un error que ignorar el estado");
    }
}
