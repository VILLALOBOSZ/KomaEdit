package com.komainu.moxia.notes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las notas que guarda el servidor: quién las ve, quién las cambia, los límites y el archivo. */
class NoteBookTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final NoteBook.Actor ANA = new NoteBook.Actor(
            UUID.fromString("00000000-0000-0000-0000-00000000000a"), "Ana", true, true, false);
    private static final NoteBook.Actor BEA = new NoteBook.Actor(
            UUID.fromString("00000000-0000-0000-0000-00000000000b"), "Bea", true, false, false);
    /** Solo notas privadas: no ve las del equipo ni las puede poner. */
    private static final NoteBook.Actor LONE = new NoteBook.Actor(
            UUID.fromString("00000000-0000-0000-0000-00000000000c"), "Cris", false, false, false);
    private static final NoteBook.Actor ADMIN = new NoteBook.Actor(
            UUID.fromString("00000000-0000-0000-0000-0000000000ad"), "Admin", true, true, true);
    private static final NoteBook.Anchor BLOCK = new NoteBook.Anchor(OVERWORLD, 10, 64, -3, true, WorldNote.UP);
    private static final NoteLimits LIMITS = new NoteLimits(3, 4, 40, 128, true);

    @TempDir
    Path folder;

    /** Ids en orden, para que las pruebas no dependan del azar. */
    private static IntUnaryOperator counter() {
        int[] next = {0};
        return bound -> next[0]++ % bound;
    }

    private NoteBook book() {
        return new NoteBook(folder.resolve("komaedit").resolve("notes.json"));
    }

    private WorldNote reread(String id) {
        NoteBook book = book();
        book.load();
        return book.find(id).orElseThrow();
    }

    @Test
    void aNoteKeepsTextColorIconDistanceAuthorAndDate() {
        NoteBook book = book();
        WorldNote note = book.add(NoteRequest.parse("Falta el tejado -c azul -i tarea -d 64 -e"), BLOCK, ANA, LIMITS,
                1_700_000_000_000L, counter());
        assertTrue(NoteIds.valid(note.id()));
        assertEquals("Falta el tejado", note.text());
        assertEquals(NoteColor.AZUL.rgb(), note.color());
        assertEquals(NoteIcon.TAREA, note.icon());
        assertEquals(64, note.distance());
        assertTrue(note.team());
        assertTrue(note.block());
        assertEquals("Ana", note.author());
        assertEquals(1_700_000_000_000L, note.created());
        assertFalse(note.resolved());
        // Encima del bloque.
        assertEquals(10.5, note.labelX());
        assertEquals(65.6, note.labelY(), 1.0E-9);

        NoteBook again = book();
        assertEquals(0, again.load());
        assertEquals(note, again.find(note.id()).orElseThrow(), "el archivo guarda todo");
        WorldNote wall = book.add(NoteRequest.parse("en la pared"), new NoteBook.Anchor(OVERWORLD, 1, 70, 1, true, 5),
                ANA, LIMITS, 2, counter());
        assertEquals(5, reread(wall.id()).side(), "y la cara en la que se puso");
    }

    @Test
    void privateNotesAreOnlyForTheirAuthorEvenForManagers() {
        NoteBook book = book();
        WorldNote secret = book.add(NoteRequest.parse("Mi idea"), BLOCK, ANA, LIMITS, 1, counter());
        WorldNote team = book.add(NoteRequest.parse("Para todos -e"), BLOCK, ANA, LIMITS, 2, counter());
        assertEquals(List.of(team, secret), book.visibleTo(ANA, OVERWORLD), "lo más reciente primero");
        assertEquals(List.of(team), book.visibleTo(BEA, OVERWORLD));
        assertEquals(List.of(), book.visibleTo(LONE, OVERWORLD), "sin permiso de equipo no ve las del equipo");
        assertEquals(List.of(team), book.visibleTo(ADMIN, OVERWORLD));
        assertThrows(IllegalArgumentException.class, () -> book.visible(secret.id(), ADMIN),
                "ni quien gestiona las notas ve una privada ajena");
        assertThrows(IllegalArgumentException.class, () -> book.remove(secret.id(), ADMIN));
        assertEquals(List.of(), book.visibleTo(ANA, "minecraft:the_nether"), "por dimensión");
        assertEquals(2, book.visibleTo(ANA, null).size(), "o en todas");
    }

    @Test
    void teamNotesNeedTheSharePermissionAndCanBeTurnedOff() {
        NoteBook book = book();
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> book.add(NoteRequest.parse("hola -e"), BLOCK, BEA, LIMITS, 1, counter()));
        assertTrue(missing.getMessage().contains(NotePermissions.SHARE));
        NoteLimits off = new NoteLimits(3, 4, 40, 128, false);
        assertThrows(IllegalArgumentException.class,
                () -> book.add(NoteRequest.parse("hola -e"), BLOCK, ANA, off, 1, counter()));
        assertEquals(0, book.size());
    }

    @Test
    void theTeamResolvesButOnlyTheAuthorOrAManagerDeletes() {
        NoteBook book = book();
        WorldNote note = book.add(NoteRequest.parse("Revisar -e"), BLOCK, ANA, LIMITS, 1, counter());
        WorldNote resolved = book.resolve(note.id(), BEA, true, 500);
        assertTrue(resolved.resolved());
        assertEquals("Bea", resolved.resolvedBy());
        assertEquals(500, resolved.resolvedAt());
        assertThrows(IllegalArgumentException.class, () -> book.resolve(note.id(), BEA, true, 600),
                "ya estaba resuelta");
        assertFalse(book.resolve(note.id(), ANA, false, 700).resolved(), "reabrirla");
        assertThrows(IllegalArgumentException.class, () -> book.remove(note.id(), BEA));
        assertEquals(note.id(), book.remove(note.id(), ADMIN).id());
        assertTrue(book.find(note.id()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> book.remove("zzzzzz", ANA));
    }

    @Test
    void limitsOfLengthDistanceAndNumber() {
        NoteBook book = book();
        assertThrows(IllegalArgumentException.class,
                () -> book.add(NoteRequest.parse("x".repeat(41)), BLOCK, ANA, LIMITS, 1, counter()), "40 letras");
        assertThrows(IllegalArgumentException.class,
                () -> book.add(NoteRequest.parse("   "), BLOCK, ANA, LIMITS, 1, counter()), "sin texto");
        assertThrows(IllegalArgumentException.class,
                () -> book.add(NoteRequest.parse("\"tiene § color\""), BLOCK, ANA, LIMITS, 1, counter()));
        assertThrows(IllegalArgumentException.class,
                () -> book.add(NoteRequest.parse("lejos -d 500"), BLOCK, ANA, LIMITS, 1, counter()));
        assertThrows(IllegalArgumentException.class,
                () -> book.add(NoteRequest.parse("cerca -d 2"), BLOCK, ANA, LIMITS, 1, counter()));
        // De serie, la distancia de serie (o la máxima del servidor si es menor).
        assertEquals(NoteLimits.DEFAULT_DISTANCE,
                book.add(NoteRequest.parse("uno"), BLOCK, ANA, LIMITS, 1, counter()).distance());
        book.add(NoteRequest.parse("dos -e"), BLOCK, ANA, LIMITS, 2, counter());
        book.add(NoteRequest.parse("tres -e"), BLOCK, ANA, LIMITS, 3, counter());
        IllegalArgumentException full = assertThrows(IllegalArgumentException.class,
                () -> book.add(NoteRequest.parse("cuatro"), BLOCK, ANA, LIMITS, 4, counter()));
        assertTrue(full.getMessage().contains("3"), full.getMessage());
        // El equipo tiene su propio tope (4) y quien gestiona se salta los dos.
        book.add(NoteRequest.parse("de admin -e"), BLOCK, ADMIN, LIMITS, 5, counter());
        book.add(NoteRequest.parse("otra de admin -e"), BLOCK, ADMIN, LIMITS, 6, counter());
        NoteBook.Actor sharer = new NoteBook.Actor(UUID.randomUUID(), "Dani", true, true, false);
        assertThrows(IllegalArgumentException.class,
                () -> book.add(NoteRequest.parse("no cabe -e"), BLOCK, sharer, LIMITS, 7, counter()));
        assertEquals("privada sí", book.add(NoteRequest.parse("privada sí"), BLOCK, sharer, LIMITS, 8, counter())
                .text(), "una privada no cuenta para el tope del equipo");
    }

    @Test
    void clearingRemovesOnlyOwnResolvedNotes() {
        NoteBook book = book();
        WorldNote mine = book.add(NoteRequest.parse("mía"), BLOCK, ANA, LIMITS, 1, counter());
        WorldNote open = book.add(NoteRequest.parse("abierta -e"), BLOCK, ANA, LIMITS, 2, counter());
        NoteBook.Actor beaShares = new NoteBook.Actor(BEA.id(), "Bea", true, true, false);
        WorldNote hers = book.add(NoteRequest.parse("de Bea -e"), BLOCK, beaShares, LIMITS, 3, counter());
        book.resolve(mine.id(), ANA, true, 10);
        book.resolve(hers.id(), ANA, true, 11);
        List<WorldNote> removed = book.clearResolved(ANA);
        assertEquals(List.of(mine.id()), removed.stream().map(WorldNote::id).toList());
        assertTrue(book.find(open.id()).isPresent());
        assertTrue(book.find(hers.id()).isPresent(), "la de Bea, aunque resuelta, no es de Ana");
        assertEquals(List.of(hers.id()), book.clearResolved(ADMIN).stream().map(WorldNote::id).toList());
    }

    @Test
    void idsAreUniqueAndBrokenNotesAreSkipped() throws Exception {
        NoteBook book = book();
        Set<String> ids = new HashSet<>();
        // Un azar que repite siempre: el siguiente id tiene que ser otro.
        int[] calls = {0};
        IntUnaryOperator stubborn = bound -> calls[0]++ < 12 ? 0 : (calls[0] % bound);
        ids.add(book.add(NoteRequest.parse("a"), BLOCK, ANA, LIMITS, 1, stubborn).id());
        ids.add(book.add(NoteRequest.parse("b"), BLOCK, ANA, LIMITS, 2, stubborn).id());
        assertEquals(2, ids.size());
        assertTrue(NoteIds.valid(NoteIds.next(Set.of(), bound -> 0)));
        assertFalse(NoteIds.valid("ABC"));

        Path file = folder.resolve("rotas.json");
        Files.writeString(file, """
                {"format": 1, "notes": [
                  {"id": "abc234", "text": "buena", "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3,
                   "authorId": "00000000-0000-0000-0000-00000000000a"},
                  {"id": "abc234", "text": "repetida", "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3,
                   "authorId": "00000000-0000-0000-0000-00000000000a"},
                  {"id": "MAL", "text": "id malo", "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3,
                   "authorId": "00000000-0000-0000-0000-00000000000a"},
                  {"id": "xyz234", "text": "sin autor", "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3},
                  {"id": "def234", "text": "icono nuevo", "icon": "dragón", "color": "#abcdef",
                   "dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3,
                   "authorId": "00000000-0000-0000-0000-00000000000a"}
                ]}""");
        NoteBook read = new NoteBook(file);
        assertEquals(3, read.load());
        assertEquals(2, read.size());
        assertEquals(NoteIcon.NOTA, read.find("def234").orElseThrow().icon(), "un icono que no conoce: el de serie");
        assertEquals(0xABCDEF, read.find("DEF234").orElseThrow().color());
    }

    /**
     * Un {@code notes.json} que no se puede leer (a medias, editado a mano o de una versión más nueva del mod tras
     * volver a una anterior) no se pisa con las notas nuevas: se aparta para poder recuperarlo.
     */
    @Test
    void anUnreadableFileIsSetAsideInsteadOfOverwritten() throws java.io.IOException {
        for (String unreadable : List.of("{\"format\": 2, \"notes\": [{\"id\": \"nueva1\"}]}",
                "{\"format\": 1, \"notes\": [{\"id\": \"abc234\", \"te")) {
            Path directory = Files.createTempDirectory(folder, "notas");
            Path file = directory.resolve("notes.json");
            Files.writeString(file, unreadable);
            NoteBook book = new NoteBook(file);
            assertThrows(RuntimeException.class, book::load);
            IntUnaryOperator ids = counter();
            book.add(NoteRequest.parse("uno"), BLOCK, ANA, LIMITS, 1, ids);
            book.add(NoteRequest.parse("dos"), BLOCK, ANA, LIMITS, 2, ids);
            List<String> kept = new java.util.ArrayList<>();
            try (var files = Files.list(directory)) {
                for (Path other : files.toList()) {
                    kept.add(Files.readString(other));
                }
            }
            assertTrue(kept.contains(unreadable), "las notas de antes se han perdido: " + unreadable);
            assertEquals(2, reread(file).size(), "y las nuevas se guardan");
        }
    }

    private static NoteBook reread(Path file) {
        NoteBook book = new NoteBook(file);
        book.load();
        return book;
    }
}
