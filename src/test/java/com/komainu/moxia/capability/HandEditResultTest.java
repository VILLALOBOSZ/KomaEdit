package com.komainu.moxia.capability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un cambio a mano de un solo bloque no va al historial de WorldEdit (como colocarlo a mano). Extruir, encoger y
 * arrastrar un solo bloque decían «· //undo», y ese //undo deshacía la edición anterior del jugador, que no tenía nada
 * que ver.
 */
class HandEditResultTest {
    @Test
    void onlyEditsInTheHistoryPromiseUndo() {
        HandEditApplier.Result single = HandEditApplier.Result.of(1, 0, null, false);
        assertFalse(single.remembered());
        assertEquals("", single.undoHint());
        HandEditApplier.Result several = HandEditApplier.Result.of(4, 1, null, false);
        assertTrue(several.remembered());
        assertEquals(" · //undo", several.undoHint());
        HandEditApplier.Result chest = HandEditApplier.Result.of(1, 0, null, true);
        assertTrue(chest.remembered(), "un cofre reemplazado va al historial para no perder lo que llevaba");
        assertFalse(HandEditApplier.Result.of(0, 3, "fuera del mundo", true).remembered());
    }
}
