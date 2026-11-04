package com.komainu.moxia.editor;

import com.komainu.moxia.edit.EditLimits;
import com.komainu.moxia.region.RegionOperationService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El paquete del editor lo puede mandar un cliente modificado sin parar y con cualquier selección: copiar no pasa del
 * límite ni lee chunks sin cargar, y lo que cuesta en el servidor tiene freno.
 */
class EditorActionLimitsTest {
    private static final long SERVER_LIMIT = 1_500_000L;

    @Test
    void copyingAWorldSizedSelectionIsRefusedBeforeReadingIt() {
        // De punta a punta del mundo: lo que antes se copiaba entero en memoria, en el hilo del servidor.
        long world = 60_000_000L * 384L * 60_000_000L;
        assertEquals(EditLimits.Kind.SERVER, EditorActionService.copyExcess(world, SERVER_LIMIT).orElseThrow().kind());
        assertEquals(EditLimits.Kind.SERVER,
                EditorActionService.copyExcess(SERVER_LIMIT + 1, SERVER_LIMIT).orElseThrow().kind());
        assertTrue(EditorActionService.copyExcess(SERVER_LIMIT, SERVER_LIMIT).isEmpty(), "el límite justo vale");
        assertTrue(EditorActionService.copyExcess(64L * 64 * 64, SERVER_LIMIT).isEmpty());
    }

    @Test
    void evenWithoutAServerLimitACopyStaysWithinWhatKomaEditReadsAtOnce() {
        long unlimited = Long.MAX_VALUE;
        assertTrue(EditorActionService.copyExcess(RegionOperationService.MAX_CAPTURE_BLOCKS, unlimited).isEmpty());
        assertEquals(EditLimits.Kind.CAPTURE, EditorActionService.copyExcess(
                RegionOperationService.MAX_CAPTURE_BLOCKS + 1, unlimited).orElseThrow().kind());
    }

    @Test
    void aCopyNeedsEveryChunkOfTheSelectionLoaded() {
        Set<Long> loaded = new HashSet<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                loaded.add(((long) x << 32) ^ (z & 0xffffffffL));
            }
        }
        EditorActionService.ChunkCheck chunks = (x, z) -> loaded.contains(((long) x << 32) ^ (z & 0xffffffffL));
        assertTrue(EditorActionService.loaded(-16, -16, 31, 31, chunks), "los nueve chunks de alrededor");
        assertFalse(EditorActionService.loaded(-16, -16, 32, 31, chunks), "un bloque en el chunk de al lado");
        assertFalse(EditorActionService.loaded(-30_000_000, 0, 0, 0, chunks), "de lejos");
    }

    @Test
    void heavyActionsHaveABrakeAndLightOnesDoNot() {
        UUID player = new UUID(7L, 7L);
        long now = 1_000_000_000L;
        int undone = 0;
        for (int packet = 0; packet < 10_000; packet++) {
            if (EditorActionService.admit(player, EditorActionPayload.UNDO, now)) {
                undone++;
            }
        }
        assertEquals(16, undone, "una ráfaga que cabe en un salto del historial (12 pasos), no diez mil");
        for (int packet = 0; packet < 1_000; packet++) {
            assertTrue(EditorActionService.admit(player, EditorActionPayload.PATH_MOVE, now), "arrastrar un punto");
            assertTrue(EditorActionService.admit(player, EditorActionPayload.PAINT, now), "pintar");
        }
        assertFalse(EditorActionService.admit(player, EditorActionPayload.CLIPBOARD_COPY, now));
        assertFalse(EditorActionService.admit(player, EditorActionPayload.MAGIC_AREA, now));
        // Medio segundo después vuelve a atender una.
        assertTrue(EditorActionService.admit(player, EditorActionPayload.REDO, now + 500_000_000L));
        EditorActionService.forget(player);
        assertTrue(EditorActionService.admit(player, EditorActionPayload.UNDO, now + 500_000_000L));
    }
}
