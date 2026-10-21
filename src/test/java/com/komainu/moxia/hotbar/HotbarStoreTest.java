package com.komainu.moxia.hotbar;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guardar y cargar barras con sus componentes, tal como los escribe {@code ItemStack.CODEC}. */
class HotbarStoreTest {
    /** Un pico encantado, con nombre y gastado, como lo escribe el códec de Minecraft (claves en otro orden). */
    static final String PICKAXE_COMPONENTS = """
            {"minecraft:enchantments":{"levels":{"minecraft:efficiency":5,"minecraft:unbreaking":3}},
             "minecraft:custom_name":"{\\"text\\":\\"Pico de Koma\\",\\"italic\\":false}",
             "minecraft:damage":120}""";
    static final String BOOK_COMPONENTS = """
            {"minecraft:written_book_content":{"author":"Koma","title":{"raw":"Planos"},
             "pages":[{"raw":"{\\"text\\":\\"Torre: 12×12\\"}"}]}}""";

    static HotbarItem pickaxe() {
        return HotbarItem.of("diamond_pickaxe", 1,
                HotbarJson.canonical(JsonParser.parseString(PICKAXE_COMPONENTS)));
    }

    static SavedHotbar sample(String name) {
        return new SavedHotbar(name, "", List.of(
                HotbarItem.of("stone_bricks", 64),
                HotbarItem.of("mossy_stone_bricks", 32),
                HotbarItem.EMPTY,
                pickaxe(),
                HotbarItem.of("written_book", 1, HotbarJson.canonical(JsonParser.parseString(BOOK_COMPONENTS))),
                HotbarItem.of("oak_log", 1, "{\"minecraft:block_state\":{\"axis\":\"x\"}}"),
                HotbarItem.EMPTY,
                HotbarItem.of("komaedit_test:custom_block", 7),
                HotbarItem.of("lantern", 1)));
    }

    @Test
    void savesAndLoadsEverySlotWithItsComponents(@TempDir Path folder) {
        HotbarStore store = new HotbarStore(folder.resolve("hotbars").resolve("hotbars.json"));
        HotbarBook book = HotbarBook.EMPTY
                .saved(sample("piedra"), false)
                .saved(sample("madera").withIcon("oak_planks"), false)
                .withActive("madera")
                .withAutosave(false);
        store.save(book);

        HotbarStore.Loaded loaded = store.load();
        assertNull(loaded.problem());
        assertEquals(book, loaded.book(), "lo cargado es exactamente lo guardado");
        SavedHotbar piedra = loaded.book().require("piedra");
        assertEquals(pickaxe(), piedra.slot(3));
        assertEquals(64, piedra.slot(0).count());
        assertTrue(piedra.slot(2).isEmpty());
        assertEquals("komaedit_test:custom_block", piedra.slot(7).id(), "los objetos de otros mods se conservan");
        assertEquals("minecraft:stone_bricks", piedra.iconItem(), "sin icono, el del primer hueco con algo");
        assertEquals("minecraft:oak_planks", loaded.book().require("madera").iconItem());
        assertEquals("madera", loaded.book().active());
        assertEquals(List.of("piedra", "madera"), loaded.book().names(), "el orden se conserva");
    }

    @Test
    void theFileUsesTheShapeOfTheMinecraftCodec(@TempDir Path folder) throws IOException {
        HotbarStore store = new HotbarStore(folder.resolve("hotbars.json"));
        store.save(HotbarBook.EMPTY.saved(sample("piedra"), false));
        String json = Files.readString(store.file(), StandardCharsets.UTF_8);
        var slots = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("hotbars").get(0)
                .getAsJsonObject().getAsJsonArray("slots");
        assertEquals(9, slots.size());
        assertEquals("minecraft:stone_bricks", slots.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(64, slots.get(0).getAsJsonObject().get("count").getAsInt());
        assertTrue(slots.get(2).isJsonNull(), "los huecos vacíos son null");
        assertEquals(5, slots.get(3).getAsJsonObject().getAsJsonObject("components")
                .getAsJsonObject("minecraft:enchantments").getAsJsonObject("levels")
                .get("minecraft:efficiency").getAsInt(), "los componentes quedan como JSON legible");
    }

    @Test
    void componentsCompareTheSameWhateverTheKeyOrder() {
        String reordered = """
                {"minecraft:damage":120,"minecraft:custom_name":"{\\"text\\":\\"Pico de Koma\\",\\"italic\\":false}",
                 "minecraft:enchantments":{"levels":{"minecraft:unbreaking":3,"minecraft:efficiency":5}}}""";
        HotbarItem other = HotbarJson.slot(JsonParser.parseString(
                "{\"id\":\"minecraft:diamond_pickaxe\",\"components\":" + reordered + "}"));
        assertTrue(other.sameStack(pickaxe()));
        assertEquals(1, other.count(), "sin count vale 1, como en el códec de Minecraft");
    }

    @Test
    void aMissingFileIsAnEmptyBook(@TempDir Path folder) {
        HotbarStore.Loaded loaded = new HotbarStore(folder.resolve("nada.json")).load();
        assertEquals(HotbarBook.EMPTY, loaded.book());
        assertNull(loaded.problem());
    }

    @Test
    void aBrokenFileIsSetAsideInsteadOfOverwritten(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("hotbars.json");
        Files.writeString(file, "{\"version\":1,\"hotbars\":[{\"name\":\"Nombre con espacios\"}]}");
        HotbarStore store = new HotbarStore(file);
        HotbarStore.Loaded loaded = store.load();
        assertEquals(HotbarBook.EMPTY, loaded.book());
        assertNotNull(loaded.problem());
        assertTrue(Files.isRegularFile(folder.resolve("hotbars.broken-1.json")), "el archivo dañado se aparta");
        assertTrue(!Files.exists(file));

        store.save(HotbarBook.EMPTY.saved(sample("nueva"), false));
        assertEquals(List.of("nueva"), store.load().book().names());
        assertTrue(Files.readString(folder.resolve("hotbars.broken-1.json")).contains("Nombre con espacios"),
                "y sigue ahí para recuperarlo a mano");
    }
}
