package com.komainu.moxia.editor;

import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.permission.PermissionNodes;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El paquete del editor lo puede mandar un cliente modificado con cualquier acción: las que cambian bloques tienen
 * que pedir el permiso de construir, no el de seleccionar.
 */
class EditorActionPermissionTest {
    /** Quien solo puede seleccionar: el permiso amplio de selección, sin el de construir. */
    private static final Predicate<String> SELECT_ONLY = Set.of(KomaPermissions.USE, KomaPermissions.SELECT)::contains;

    @Test
    void changingTheSelectionBlocksNeedsTheBuildPermission() {
        for (int action : new int[]{EditorActionPayload.SELECTION_REPLACE, EditorActionPayload.SELECTION_DELETE,
                EditorActionPayload.PAINT_SELECTION}) {
            assertEquals(KomaPermissions.BUILD, EditorActionService.permission(action), "acción " + action);
        }
    }

    @Test
    void whoCanOnlySelectCannotReplaceOrDeleteTheSelection() {
        assertTrue(PermissionNodes.allows(EditorActionService.permission(EditorActionPayload.BOX_PRIMARY),
                SELECT_ONLY), "selecciona");
        for (int action : new int[]{EditorActionPayload.SELECTION_REPLACE, EditorActionPayload.SELECTION_DELETE,
                EditorActionPayload.PAINT_SELECTION}) {
            assertFalse(PermissionNodes.allows(EditorActionService.permission(action), SELECT_ONLY),
                    "acción " + action + " con solo " + KomaPermissions.SELECT);
        }
    }

    @Test
    void selectingStaysWithTheSelectPermission() {
        for (int action : new int[]{EditorActionPayload.BOX_PRIMARY, EditorActionPayload.BOX_SECONDARY,
                EditorActionPayload.MAGIC_BLOCK, EditorActionPayload.MAGIC_AREA}) {
            assertEquals(KomaPermissions.SELECT, EditorActionService.permission(action), "acción " + action);
        }
        assertEquals(KomaPermissions.CLIPBOARD, EditorActionService.permission(EditorActionPayload.CLIPBOARD_PASTE),
                "pegar pide lo mismo que /komaedit paste");
    }
}
