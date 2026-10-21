package com.komainu.moxia.views;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las vistas compartidas que guarda el servidor: autor, límites, quién reemplaza o quita y el archivo. */
class SharedViewsTest {
    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEA = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final SharedViews.Limits LIMITS = new SharedViews.Limits(true, 2, 3);

    @TempDir
    Path folder;

    private static SavedView shared(String name, UUID author, String authorName, double x) {
        return SavedView.here(name, "minecraft:overworld", x, 80, 5, 90, 10, 0).sharedBy(authorName, author,
                1_700_000_000_000L);
    }

    @Test
    void aSharedViewIsSavedWithItsAuthorAndReadBack() {
        Path file = folder.resolve("komaedit").resolve("views.json");
        SharedViews views = new SharedViews(file);
        assertTrue(views.share(shared("Plaza", ANA, "Ana", 1), LIMITS, false, false).isEmpty());
        assertTrue(Files.isRegularFile(file), "se escribe al momento");
        SharedViews again = new SharedViews(file);
        assertEquals(0, again.load());
        SavedView read = again.find("plaza").orElseThrow();
        assertEquals("Ana", read.author());
        assertEquals(ANA, read.authorId());
        assertEquals(1_700_000_000_000L, read.created());
        assertEquals(90.0F, read.yaw());
    }

    @Test
    void theAuthorReplacesItsOwnButNotSomeoneElses() {
        SharedViews views = new SharedViews(folder.resolve("views.json"));
        views.share(shared("Plaza", ANA, "Ana", 1), LIMITS, false, false);
        assertTrue(views.share(shared("PLAZA", ANA, "Ana", 2), LIMITS, false, false).isPresent());
        assertEquals(2.0, views.find("plaza").orElseThrow().x());
        IllegalArgumentException taken = assertThrows(IllegalArgumentException.class,
                () -> views.share(shared("plaza", BEA, "Bea", 3), LIMITS, false, false));
        assertTrue(taken.getMessage().contains("Ana"), taken.getMessage());
        // Quien gestiona las vistas sí.
        views.share(shared("plaza", BEA, "Bea", 3), LIMITS, false, true);
        assertEquals("Bea", views.find("plaza").orElseThrow().author());
        assertEquals(1, views.all().size());
    }

    @Test
    void limitsPerPlayerAndForTheWholeServer() {
        SharedViews views = new SharedViews(folder.resolve("views.json"));
        views.share(shared("a1", ANA, "Ana", 1), LIMITS, false, false);
        views.share(shared("a2", ANA, "Ana", 2), LIMITS, false, false);
        assertThrows(IllegalArgumentException.class,
                () -> views.share(shared("a3", ANA, "Ana", 3), LIMITS, false, false), "2 por jugador");
        views.share(shared("a3", ANA, "Ana", 3), LIMITS, true, false);
        assertEquals(3, views.countBy(ANA), "sin límite, con el permiso");
        assertThrows(IllegalArgumentException.class,
                () -> views.share(shared("b1", BEA, "Bea", 1), LIMITS, false, false), "3 en el servidor");
        assertThrows(IllegalArgumentException.class, () -> views.share(shared("b1", BEA, "Bea", 1),
                new SharedViews.Limits(false, 10, 10), false, false), "compartir apagado");
        assertThrows(IllegalArgumentException.class, () -> views.share(SavedView.here("sin autor",
                "minecraft:overworld", 0, 0, 0, 0, 0, 0), LIMITS, false, false));
    }

    @Test
    void onlyTheAuthorOrAManagerRemovesIt() {
        SharedViews views = new SharedViews(folder.resolve("views.json"));
        views.share(shared("Mirador", ANA, "Ana", 1), LIMITS, false, false);
        assertThrows(IllegalArgumentException.class, () -> views.remove("mirador", BEA, false));
        assertEquals("Mirador", views.remove("MIRADOR", ANA, false).name());
        views.share(shared("Mirador", ANA, "Ana", 1), LIMITS, false, false);
        views.remove("mirador", BEA, true);
        assertTrue(views.all().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> views.remove("mirador", ANA, true));
    }

    @Test
    void aDiskFailureLeavesTheViewsAsTheyWere() throws IOException {
        // La carpeta del archivo es un archivo: no se puede escribir.
        Path blocker = folder.resolve("bloqueo");
        Files.writeString(blocker, "no soy una carpeta");
        SharedViews views = new SharedViews(blocker.resolve("views.json"));
        assertThrows(UncheckedIOException.class,
                () -> views.share(shared("Plaza", ANA, "Ana", 1), LIMITS, false, false));
        assertTrue(views.all().isEmpty(), "no queda a medias en memoria");
    }

    @Test
    void brokenOrRepeatedViewsAreSkippedWhenReading() {
        SharedViews views = new SharedViews(folder.resolve("views.json"));
        int skipped = views.read("""
                {"format": 1, "views": [
                  {"name": "Buena", "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3, "author": "Ana",
                   "authorId": "00000000-0000-0000-0000-00000000000a"},
                  {"name": "Sin autor", "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3},
                  {"name": "BUENA", "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3, "author": "Bea",
                   "authorId": "00000000-0000-0000-0000-00000000000b"},
                  {"name": "Rota", "dimension": "minecraft:overworld", "x": "mucho"}
                ]}""");
        assertEquals(3, skipped);
        assertEquals(1, views.all().size());
        assertThrows(IllegalArgumentException.class, () -> views.read("{\"format\": 2}"));
    }

    /**
     * Un {@code views.json} que no se puede leer (a medias, editado a mano o de una versión más nueva del mod tras
     * volver a una anterior) no se pisa con las vistas nuevas: se aparta para poder recuperarlo.
     */
    @Test
    void anUnreadableFileIsSetAsideInsteadOfOverwritten() throws IOException {
        String newer = "{\"format\": 2, \"views\": [{\"name\": \"De la versión nueva\"}]}";
        Path file = folder.resolve("views.json");
        Files.writeString(file, newer);
        SharedViews views = new SharedViews(file);
        assertThrows(RuntimeException.class, views::load);
        views.share(shared("Plaza", ANA, "Ana", 1), LIMITS, false, false);
        views.share(shared("Puerto", ANA, "Ana", 2), LIMITS, false, false);
        java.util.List<String> kept = new java.util.ArrayList<>();
        try (var files = Files.list(folder)) {
            for (Path other : files.toList()) {
                kept.add(Files.readString(other));
            }
        }
        assertTrue(kept.contains(newer), "las vistas de antes se han perdido");
        SharedViews again = new SharedViews(file);
        assertEquals(0, again.load());
        assertEquals(2, again.all().size(), "y las nuevas se guardan");
    }
}
