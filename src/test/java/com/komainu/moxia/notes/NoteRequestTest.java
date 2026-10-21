package com.komainu.moxia.notes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /komaedit nota poner …}: el texto y las banderas, en cualquier orden. */
class NoteRequestTest {
    @Test
    void plainTextWithoutFlagsTakesTheDefaults() {
        NoteRequest request = NoteRequest.parse("Falta el tejado del ala norte");
        assertEquals("Falta el tejado del ala norte", request.text());
        assertNull(request.color());
        assertNull(request.icon());
        assertNull(request.distance());
        assertFalse(request.team());
        assertFalse(request.point());
        assertEquals(NoteColor.AMARILLO.rgb(), request.colorOr(NoteColor.AMARILLO.rgb()));
    }

    @Test
    void flagsGoAnywhere() {
        NoteRequest request = NoteRequest.parse("-c rojo Revisar la escalera -i aviso -e --distancia 96 -p");
        assertEquals("Revisar la escalera", request.text());
        assertEquals(NoteColor.ROJO.rgb(), request.color());
        assertEquals(NoteIcon.AVISO, request.icon());
        assertEquals(96, request.distance());
        assertTrue(request.team());
        assertTrue(request.point());
        assertEquals(0x12AB34, NoteRequest.parse("x -c #12ab34").color());
        assertEquals(NoteColor.AZUL.rgb(), NoteRequest.parse("x --color blue").color());
        assertEquals(NoteIcon.TAREA, NoteRequest.parse("x -i todo").icon());
        assertEquals(NoteIcon.AQUI, NoteRequest.parse("x -i aquí").icon());
    }

    @Test
    void quotesKeepFlagsAsText() {
        NoteRequest request = NoteRequest.parse("\"Poner -c en el cartel \\\"bonito\\\"\" -i idea");
        assertEquals("Poner -c en el cartel \"bonito\"", request.text());
        assertEquals(NoteIcon.IDEA, request.icon());
        assertEquals("-e", NoteRequest.parse("\"-e\"").text(), "entre comillas es texto");
        assertFalse(NoteRequest.parse("\"-e\"").team());
        // Lo que escribe la tarjeta del Studio se lee igual.
        String text = "Muro \"viejo\" \\ roto";
        assertEquals(text, NoteRequest.parse(NoteRequest.quote(text) + " -e").text());
    }

    @Test
    void badValuesSayWhatIsWrong() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> NoteRequest.parse("x -c fucsia"))
                .getMessage().contains("amarillo"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> NoteRequest.parse("x -i calavera"))
                .getMessage().contains("aviso"));
        assertThrows(IllegalArgumentException.class, () -> NoteRequest.parse("x -d mucho"));
        assertThrows(IllegalArgumentException.class, () -> NoteRequest.parse("x -c"));
        assertThrows(IllegalArgumentException.class, () -> NoteRequest.parse("\"sin cerrar"));
    }

    @Test
    void colorsAndIconsHaveNamesBothWays() {
        for (NoteColor color : NoteColor.values()) {
            assertEquals(color.rgb(), NoteColor.parse(NoteColor.name(color.rgb())));
        }
        assertEquals("#123456", NoteColor.name(0x123456));
        for (NoteIcon icon : NoteIcon.values()) {
            assertEquals(icon, NoteIcon.parse(icon.id()));
            assertEquals(9, icon.pixels().width());
            assertEquals(9, icon.pixels().height());
        }
        assertEquals(NoteIcon.NOTA, NoteIcon.byId("de otra versión"));
    }
}
