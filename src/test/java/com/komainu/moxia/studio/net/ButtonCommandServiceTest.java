package com.komainu.moxia.studio.net;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que el servidor deja pasar del paquete de los botones: solo órdenes de KomaEdit, y con un freno que no molesta a
 * quien hace clic deprisa pero corta a quien lo inunda. Los permisos los mira el árbol de órdenes
 * ({@code ButtonCommandPermissionsTest}).
 */
class ButtonCommandServiceTest {
    private static final String TREE = "komaedit tree spruce -v";

    @Test
    void theButtonOnlyRunsKomaEditCommands() {
        UUID player = new UUID(0L, 1L);
        for (String command : List.of("gamemode creative", "op builder", "kill @e", "say hola", "//set tnt")) {
            ButtonCommandService.Admission admission = ButtonCommandService.admit(player, command, 1_000_000_000L);
            assertFalse(admission.run(), command);
            assertTrue(admission.message().startsWith("No se ejecuta: "), admission.message());
        }
    }

    /** El fallo del #74: con unos 11 clics seguidos por el chat, Minecraft echaba a quien no es OP. */
    @Test
    void thirtyClicksInARowAllRun() {
        UUID player = new UUID(0L, 2L);
        long now = 5_000_000_000L;
        for (int click = 0; click < 30; click++) {
            assertEquals(ButtonCommandService.Admission.RUN, ButtonCommandService.admit(player, TREE, now),
                    "clic " + (click + 1));
            now += 40_000_000L;
        }
    }

    @Test
    void aFloodIsCutWithASingleWarning() {
        UUID player = new UUID(0L, 3L);
        long now = 9_000_000_000L;
        assertEquals(new Flood(ButtonCommandService.BURST, 1), flood(player, now));
        // Al rato vuelve a atender lo que da de sí el freno, y otra inundación vuelve a avisar una vez.
        now += 1_000_000_000L;
        assertEquals(new Flood((int) ButtonCommandService.PER_SECOND, 1), flood(player, now));
        // Al irse el jugador se olvida.
        ButtonCommandService.forget(player);
        assertEquals(ButtonCommandService.Admission.RUN, ButtonCommandService.admit(player, TREE, now));
    }

    @Test
    void floodingWithBadCommandsDoesNotFloodTheChatWithErrors() {
        UUID player = new UUID(0L, 4L);
        int messages = 0;
        for (int sent = 0; sent < 1_000; sent++) {
            ButtonCommandService.Admission admission = ButtonCommandService.admit(player, "op builder",
                    3_000_000_000L);
            assertFalse(admission.run());
            if (admission.message() != null) {
                messages++;
            }
        }
        assertEquals(ButtonCommandService.BURST + 1, messages, "un error por orden atendida y un aviso de freno");
    }

    private record Flood(int ran, int warnings) {
    }

    private static Flood flood(UUID player, long now) {
        int ran = 0;
        int warnings = 0;
        for (int sent = 0; sent < 1_000; sent++) {
            ButtonCommandService.Admission admission = ButtonCommandService.admit(player, TREE, now);
            if (admission.run()) {
                ran++;
            } else if (admission.message() != null) {
                assertEquals(ButtonCommandService.TOO_FAST, admission.message());
                warnings++;
            }
        }
        return new Flood(ran, warnings);
    }
}
