package com.komainu.moxia.studio.net;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que lleva el paquete de los botones: órdenes de KomaEdit y nada más. Cualquier otra cosa que un cliente
 * modificado mande por él no llega a ejecutarse (y las del propio cliente nunca van por él).
 */
class ButtonCommandsTest {
    @Test
    void komaEditCommandsAndTheirAliasesGoThrough() {
        for (String command : List.of("komaedit tree spruce -v", "ke path here", "moxia confirm", "mx repeat",
                "komaedit nota poner \"mirar la torre\" -e", "komaedit")) {
            assertTrue(ButtonCommands.problem(command).isEmpty(), command);
        }
    }

    @Test
    void nothingElseGoesThrough() {
        // Órdenes de Minecraft, de otros mods (WorldEdit) y las del propio cliente: con su texto, por el chat.
        for (String command : List.of("gamemode creative", "op builder", "say hola", "msg alguien hola",
                "/komaedit tree oak", "//set stone", "kegui regla on", "kedisplay ajustes", " komaedit tree oak",
                "KomaEdit tree oak", "komaeditx tree", "")) {
            assertTrue(ButtonCommands.problem(command).isPresent(), "«" + command + "»");
        }
        assertTrue(ButtonCommands.problem(null).isPresent());
    }

    @Test
    void textsTheChatWouldRefuseDoNotGoThrough() {
        // Minecraft echa a quien manda estos caracteres por el chat; por el paquete, simplemente no se ejecuta.
        for (String command : List.of("komaedit nota poner §cRojo", "komaedit nota poner a\nb",
                "komaedit text \u0000", "komaedit text \u007f")) {
            assertTrue(ButtonCommands.problem(command).isPresent(), command);
        }
        assertTrue(ButtonCommands.problem("komaedit nota poner \"Ñandú, café y ✓\"").isEmpty(),
                "lo que admite el chat (acentos, símbolos) sí");
    }

    @Test
    void longCommandsStayWithinTheLimit() {
        String longest = "komaedit text " + "a".repeat(ButtonCommandPayload.MAX_COMMAND - "komaedit text ".length());
        assertEquals(ButtonCommandPayload.MAX_COMMAND, longest.length());
        assertTrue(ButtonCommands.problem(longest).isEmpty());
        assertTrue(ButtonCommands.problem(longest + "a").isPresent());
        assertThrows(IllegalArgumentException.class, () -> new ButtonCommandPayload(longest + "a"));
    }

    @Test
    void theRootIsTheFirstWordAsWritten() {
        assertEquals("komaedit", ButtonCommands.root("komaedit tree oak"));
        assertEquals("kegui", ButtonCommands.root("kegui"));
        assertEquals("", ButtonCommands.root(" komaedit"));
    }
}
