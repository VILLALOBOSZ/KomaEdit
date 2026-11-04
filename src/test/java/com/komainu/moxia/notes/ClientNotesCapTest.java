package com.komainu.moxia.notes;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Las páginas de notas sin {@code reset} suman notas en el cliente: un servidor que no paraba de mandar notas nuevas
 * llenaba su memoria (y el cliente las recorre en cada tick).
 */
class ClientNotesCapTest {
    private static WorldNote note(String id, String text) {
        return new WorldNote(id, text, NoteColor.ROJO.rgb(), NoteIcon.AVISO, "minecraft:overworld", 10, 64, 10, true,
                WorldNote.UP, 48, false, new UUID(1L, 1L), "Ana", 0L, "", 0L);
    }

    @Test
    void endlessPagesStopAtTheCap() {
        Map<String, WorldNote> known = new LinkedHashMap<>();
        for (int page = 0; page < 100; page++) {
            List<WorldNote> notes = new java.util.ArrayList<>();
            for (int index = 0; index < NotePayloads.PAGE; index++) {
                notes.add(note(page + "-" + index, "nota"));
            }
            NotePayloads.merge(known, notes);
        }
        assertEquals(NotePayloads.MAX_CLIENT_NOTES, known.size());
    }

    @Test
    void aKnownNoteStillChangesWhenFull() {
        Map<String, WorldNote> known = new LinkedHashMap<>();
        for (int index = 0; index < NotePayloads.MAX_CLIENT_NOTES; index++) {
            known.put("n" + index, note("n" + index, "antes"));
        }
        NotePayloads.merge(known, List.of(note("n7", "después"), note("nueva", "no cabe")));
        assertEquals("después", known.get("n7").text());
        assertEquals(NotePayloads.MAX_CLIENT_NOTES, known.size());
    }
}
