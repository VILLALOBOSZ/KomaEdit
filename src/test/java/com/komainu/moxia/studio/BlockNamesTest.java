package com.komainu.moxia.studio;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** El nombre de un bloque en el Studio: el traducido o, si no lo hay, uno legible; nunca el id ni la clave. */
class BlockNamesTest {
    @Test
    void withATranslationItUsesIt() {
        Map<String, String> es = Map.of("block.minecraft.spruce_planks", "Tablones de abeto",
                "item.minecraft.torch", "Antorcha");
        assertEquals("Tablones de abeto", BlockNames.name("minecraft:spruce_planks", es::get));
        assertEquals("Tablones de abeto", BlockNames.name("spruce_planks", es::get), "sin espacio de nombres");
        assertEquals("Antorcha", BlockNames.name("minecraft:torch", es::get), "o la del objeto");
    }

    @Test
    void withoutATranslationItReadsTheId() {
        Map<String, String> none = Map.of();
        assertEquals("Spruce Planks", BlockNames.name("minecraft:spruce_planks", none::get));
        assertEquals("Mossy Slate Tiles", BlockNames.name("examplemod:mossy_slate_tiles", none::get));
        assertEquals("Big Oak", BlockNames.name("examplemod:trees/big_oak", none::get));
        // Un juego sin la traducción devuelve la propia clave: tampoco vale.
        assertEquals("Mossy Slate Tiles", BlockNames.name("examplemod:mossy_slate_tiles", key -> key));
        assertEquals("Mossy Slate Tiles", BlockNames.name("examplemod:mossy_slate_tiles", key -> " "));
    }

    @Test
    void readableNamesKeepWhatTheyCan() {
        assertEquals("Stone", BlockNames.readable("minecraft:stone"));
        assertEquals("Oak Log", BlockNames.readable("oak_log"));
        assertEquals("Glass Pane 2", BlockNames.readable("mod:glass-pane.2"));
        assertEquals("", BlockNames.name("", key -> null));
    }
}
