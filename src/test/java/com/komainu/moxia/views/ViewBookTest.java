package com.komainu.moxia.views;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las vistas privadas del cliente: por sitio, por nombre, la fija de cada dimensión y el archivo. */
class ViewBookTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String HOME = ViewBook.singleplayer("Mi mundo");
    private static final String SERVER = ViewBook.server("play.example.net");

    private static SavedView view(String name, String dimension, double x) {
        return SavedView.here(name, dimension, x, 70.5, -12.25, 135.0F, 25.5F, 1_700_000_000_000L);
    }

    @Test
    void placesSeparateWorldsAndServers() {
        assertEquals("mundo:Mi mundo", HOME);
        assertEquals("servidor:play.example.net", ViewBook.server(" Play.Example.NET:25565 "));
        assertEquals("servidor:play.example.net:25570", ViewBook.server("play.example.net:25570"));
        ViewBook book = new ViewBook();
        book.save(HOME, view("Torre", OVERWORLD, 1));
        book.save(SERVER, view("Torre", OVERWORLD, 2));
        assertEquals(1.0, book.find(HOME, "torre").orElseThrow().x());
        assertEquals(2.0, book.find(SERVER, "TORRE").orElseThrow().x());
        assertTrue(book.list(ViewBook.singleplayer("otro")).isEmpty());
    }

    @Test
    void savingWithTheSameNameReplacesAndKeepsThePin() {
        ViewBook book = new ViewBook();
        assertFalse(book.save(HOME, view("Plaza", OVERWORLD, 1)));
        book.pin(HOME, "plaza", true);
        assertTrue(book.save(HOME, view("PLAZA", OVERWORLD, 9)), "mismo nombre sin mayúsculas: la reemplaza");
        SavedView replaced = book.find(HOME, "plaza").orElseThrow();
        assertEquals(9.0, replaced.x());
        assertTrue(replaced.pinned(), "sigue siendo la fija");
        assertEquals(1, book.size(HOME));
        // En otra dimensión ya no puede ser la fija de antes.
        book.save(HOME, view("plaza", NETHER, 3));
        assertFalse(book.find(HOME, "plaza").orElseThrow().pinned());
    }

    @Test
    void onePinnedViewPerDimension() {
        ViewBook book = new ViewBook();
        book.save(HOME, view("A", OVERWORLD, 1));
        book.save(HOME, view("B", OVERWORLD, 2));
        book.save(HOME, view("C", NETHER, 3));
        book.pin(HOME, "a", true);
        book.pin(HOME, "c", true);
        book.pin(HOME, "b", true);
        assertEquals("B", book.pinned(HOME, OVERWORLD).orElseThrow().name());
        assertFalse(book.find(HOME, "a").orElseThrow().pinned(), "fijar B suelta A");
        assertEquals("C", book.pinned(HOME, NETHER).orElseThrow().name(), "la del Nether sigue");
        // La fija va la primera de la lista; luego por nombre.
        assertEquals(List.of("B", "C", "A"), book.list(HOME).stream().map(SavedView::name).toList());
        book.pin(HOME, "b", false);
        assertTrue(book.pinned(HOME, OVERWORLD).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> book.pin(HOME, "no existe", true));
    }

    @Test
    void namesAreCheckedAndRenamesCannotCollide() {
        ViewBook book = new ViewBook();
        assertThrows(IllegalArgumentException.class, () -> book.save(HOME, view("  ", OVERWORLD, 0)));
        assertThrows(IllegalArgumentException.class, () -> book.save(HOME, view("§cRoja", OVERWORLD, 0)));
        assertThrows(IllegalArgumentException.class, () -> book.save(HOME, view("x".repeat(33), OVERWORLD, 0)));
        assertThrows(IllegalArgumentException.class, () -> book.save(HOME, view("Bien", "no es una dimensión", 0)));
        book.save(HOME, view("Puerta  Norte ", OVERWORLD, 0));
        assertEquals("Puerta Norte", book.list(HOME).getFirst().name(), "sin espacios de más");
        book.save(HOME, view("Puerta Sur", OVERWORLD, 0));
        assertThrows(IllegalArgumentException.class, () -> book.rename(HOME, "puerta norte", "PUERTA SUR"));
        assertEquals("Puerta del Norte", book.rename(HOME, "puerta norte", "Puerta del Norte").name());
        assertTrue(book.remove(HOME, "PUERTA DEL NORTE"));
        assertFalse(book.remove(HOME, "puerta del norte"));
    }

    @Test
    void eachPlaceHasALimit() {
        ViewBook book = new ViewBook();
        for (int index = 0; index < ViewBook.MAX_PER_PLACE; index++) {
            book.save(HOME, view("v" + index, OVERWORLD, index));
        }
        assertThrows(IllegalArgumentException.class, () -> book.save(HOME, view("una más", OVERWORLD, 0)));
        // Reemplazar no cuenta como nueva.
        assertTrue(book.save(HOME, view("v7", OVERWORLD, 77)));
        // Y otro sitio tiene su propio límite.
        assertFalse(book.save(SERVER, view("una más", OVERWORLD, 0)));
    }

    @Test
    void theFileKeepsEverythingAndSkipsBrokenViews() {
        ViewBook book = new ViewBook();
        book.save(HOME, view("Torre", OVERWORLD, 10.5));
        book.save(HOME, view("Lava", NETHER, -3));
        book.pin(HOME, "torre", true);
        book.save(SERVER, view("Spawn", OVERWORLD, 0));
        int[] skipped = {0};
        ViewBook read = ViewBook.fromJson(book.toJson(), skipped);
        assertEquals(0, skipped[0]);
        assertEquals(book.list(HOME), read.list(HOME));
        assertEquals(book.list(SERVER), read.list(SERVER));
        SavedView torre = read.find(HOME, "torre").orElseThrow();
        assertEquals(135.0F, torre.yaw());
        assertEquals(25.5F, torre.pitch());
        assertTrue(torre.pinned());

        String damaged = """
                {"format": 1, "places": {"mundo:x": [
                  {"name": "Buena", "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3},
                  {"name": "Sin y", "dimension": "minecraft:overworld", "x": 1, "z": 3},
                  {"name": "§mala", "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3},
                  {"name": "buena", "dimension": "minecraft:overworld", "x": 9, "y": 9, "z": 9}
                ]}}""";
        int[] counted = {0};
        ViewBook partial = ViewBook.fromJson(damaged, counted);
        assertEquals(3, counted[0], "sin coordenada, nombre con § y repetida");
        assertEquals(1.0, partial.find("mundo:x", "buena").orElseThrow().x());
        assertThrows(IllegalArgumentException.class, () -> ViewBook.fromJson("{\"format\": 99}", null),
                "un archivo de una versión más nueva no se lee (ni se pisa)");
    }

    @Test
    void anglesAreKeptInRange() {
        SavedView view = SavedView.here("a", OVERWORLD, 0, 0, 0, 540.0F, 120.0F, 0);
        assertEquals(180.0F, Math.abs(view.yaw()));
        assertEquals(90.0F, view.pitch());
        assertEquals(-90.0F, SavedView.wrapYaw(270.0F));
        assertEquals(0.0F, SavedView.wrapYaw(Float.NaN));
    }
}
