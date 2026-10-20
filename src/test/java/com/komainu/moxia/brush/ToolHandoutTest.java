package com.komainu.moxia.brush;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Entregar una herramienta nunca la tira al suelo: a la mano, al inventario o un aviso claro de que no cabe. */
class ToolHandoutTest {
    @Test
    void withAFullInventoryNothingIsDroppedAndItSaysWhatToDo() {
        assertEquals(ToolHandout.Result.ALREADY, ToolHandout.decide(true, false, false), "ya la tiene");
        assertEquals(ToolHandout.Result.IN_HAND, ToolHandout.decide(false, true, false),
                "inventario lleno pero mano vacía: a la mano");
        assertEquals(ToolHandout.Result.IN_INVENTORY, ToolHandout.decide(false, false, true));
        assertEquals(ToolHandout.Result.FULL, ToolHandout.decide(false, false, false), "no se tira al suelo");
        String message = ToolHandout.fullMessage("el pincel para esculpir");
        assertTrue(message.startsWith("Inventario lleno"), message);
        assertTrue(message.contains("el pincel para esculpir"), message);
        assertTrue(message.contains("haz sitio"), message);
    }
}
