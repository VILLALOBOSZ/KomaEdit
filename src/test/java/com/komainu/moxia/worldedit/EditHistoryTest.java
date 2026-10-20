package com.komainu.moxia.worldedit;

import com.sk89q.worldedit.LocalSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El historial del Studio lee campos privados de WorldEdit: si una versión los cambia, el Studio se quedaría sin
 * historial sin avisar, así que se comprueba aquí con la versión del proyecto.
 */
class EditHistoryTest {
    @Test
    void theWorldEditVersionStillHasTheHistoryFields() throws ReflectiveOperationException {
        Field history = LocalSession.class.getDeclaredField("history");
        Field pointer = LocalSession.class.getDeclaredField("historyPointer");
        assertTrue(List.class.isAssignableFrom(history.getType()));
        assertEquals(int.class, pointer.getType());
    }

    @Test
    void aNewSessionHasNothingToUndo() {
        EditHistory.Snapshot snapshot = EditHistory.snapshot(new LocalSession(), 12);
        assertEquals(List.of(), snapshot.lines());
        assertEquals(0, snapshot.undone());
        assertEquals(EditHistory.Snapshot.EMPTY, EditHistory.snapshot(null, 12));
    }
}
