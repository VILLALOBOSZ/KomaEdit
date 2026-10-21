package com.komainu.moxia.hotbar;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotbarBookTest {
    private static SavedHotbar bar(String name, String block) {
        return new SavedHotbar(name, "", List.of(HotbarItem.of(block, 1)));
    }

    @Test
    void savingAnExistingNameNeedsForce() {
        HotbarBook book = HotbarBook.EMPTY.saved(bar("piedra", "stone"), false);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> book.saved(bar("piedra", "andesite"), false));
        assertTrue(error.getMessage().contains("-f"));
        HotbarBook replaced = book.saved(bar("Piedra", "andesite"), true);
        assertEquals(1, replaced.size());
        assertEquals("minecraft:andesite", replaced.require("piedra").slot(0).id(), "se sustituye en su sitio");
    }

    @Test
    void namesFollowThePaletteRules() {
        assertEquals("mi_barra", bar("#Mi_Barra", "stone").name());
        assertThrows(IllegalArgumentException.class, () -> bar("con espacio", "stone"));
        assertThrows(IllegalArgumentException.class, () -> bar("x".repeat(33), "stone"));
    }

    @Test
    void keepsAtMostSixtyFourHotbars() {
        HotbarBook book = HotbarBook.EMPTY;
        for (int index = 0; index < HotbarBook.MAX_HOTBARS; index++) {
            book = book.saved(bar("b" + index, "stone"), false);
        }
        HotbarBook full = book;
        assertThrows(IllegalArgumentException.class, () -> full.saved(bar("otra", "stone"), false));
        assertEquals(HotbarBook.MAX_HOTBARS, full.saved(bar("b3", "dirt"), true).size(), "sustituir sí se puede");
    }

    @Test
    void refusesHotbarsThatWeighTooMuch() {
        String text = "x".repeat(HotbarBook.MAX_HOTBAR_BYTES);
        HotbarItem heavy = HotbarItem.of("written_book", 1, "{\"minecraft:custom_data\":{\"t\":\"" + text + "\"}}");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> HotbarBook.EMPTY.saved(new SavedHotbar("pesada", "", List.of(heavy)), false));
        assertTrue(error.getMessage().contains("KiB"));

        HotbarItem medium = HotbarItem.of("written_book", 1,
                "{\"minecraft:custom_data\":{\"t\":\"" + "y".repeat(HotbarBook.MAX_HOTBAR_BYTES / 10) + "\"}}");
        List<HotbarItem> nine = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            nine.add(medium);
        }
        HotbarBook book = HotbarBook.EMPTY;
        IllegalArgumentException total = null;
        for (int index = 0; index < 64 && total == null; index++) {
            try {
                book = book.saved(new SavedHotbar("m" + index, "", nine), false);
            } catch (IllegalArgumentException exception) {
                total = exception;
            }
        }
        assertTrue(total != null && total.getMessage().contains("borra alguna"), "el total también tiene tope");
        assertTrue(book.bytes() <= HotbarBook.MAX_TOTAL_BYTES);
    }

    @Test
    void renameMoveAndDeleteKeepTheActiveHotbarRight() {
        HotbarBook book = HotbarBook.EMPTY
                .saved(bar("a", "stone"), false)
                .saved(bar("b", "dirt"), false)
                .saved(bar("c", "sand"), false)
                .withActive("b");
        HotbarBook renamed = book.renamed("b", "bosque");
        assertEquals("bosque", renamed.active());
        assertEquals(List.of("a", "bosque", "c"), renamed.names());
        assertThrows(IllegalArgumentException.class, () -> book.renamed("a", "c"));

        assertEquals(List.of("c", "a", "b"), book.moved("c", 1).names());
        assertEquals(List.of("b", "c", "a"), book.moved("a", 99).names());

        HotbarBook deleted = book.without("b");
        assertEquals("", deleted.active(), "borrar la barra en uso la deja sin barra en uso");
        assertThrows(IllegalArgumentException.class, () -> book.without("zz"));
    }

    @Test
    void theWheelGoesRoundTheList() {
        HotbarBook book = HotbarBook.EMPTY
                .saved(bar("a", "stone"), false)
                .saved(bar("b", "dirt"), false)
                .saved(bar("c", "sand"), false);
        assertEquals("b", book.neighbour("a", 1));
        assertEquals("a", book.neighbour("c", 1));
        assertEquals("c", book.neighbour("a", -1));
        assertEquals("a", book.neighbour("", 1), "sin barra en uso, hacia abajo la primera");
        assertEquals("c", book.neighbour("borrada", -1), "y hacia arriba la última");
        assertEquals("", HotbarBook.EMPTY.neighbour("a", 1));
    }

    @Test
    void freeNamesForImports() {
        HotbarBook book = HotbarBook.EMPTY.saved(bar("piedra", "stone"), false).saved(bar("piedra_2", "stone"), false);
        assertEquals("piedra_3", book.freeName("piedra"));
        assertEquals("madera", book.freeName("madera"));
        String longName = "x".repeat(32);
        HotbarBook full = HotbarBook.EMPTY.saved(bar(longName, "stone"), false);
        assertEquals("x".repeat(30) + "_2", full.freeName(longName));
    }

    @Test
    void anUnknownActiveHotbarIsForgotten() {
        assertEquals("", new HotbarBook(List.of(bar("a", "stone")), "zz", true).active());
    }
}
