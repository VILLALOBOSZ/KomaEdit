package com.komainu.moxia.studio.tools;

import com.komainu.moxia.notes.NoteColor;
import com.komainu.moxia.notes.NoteIcon;
import com.komainu.moxia.notes.NotePermissions;
import com.komainu.moxia.notes.NoteRequest;
import com.komainu.moxia.ruler.RulerMode;
import com.komainu.moxia.views.ViewPermissions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las tarjetas de vistas, regla y nota mandan la orden que escribiría el jugador, y se lee igual al otro lado. */
class BuilderAidCardsTest {
    private static ToolCard card(String id) {
        return ToolCatalog.byId(id);
    }

    private static ToolAction action(ToolCard card, String id) {
        return card.actions().stream().filter(action -> action.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theViewsCardSavesOnTheClientAndJumpsWithPermission() {
        ToolCard views = card("views");
        assertTrue(views.clientOnly());
        assertTrue(views.validate(views.defaults()).stream().anyMatch(ToolProblem::blocking), "sin nombre no guarda");
        ToolValues named = views.defaults().with("view_name", "  Mirador  norte ");
        assertEquals(List.of(), views.validate(named));
        assertEquals("kegui vista guardar Mirador norte", views.command(named, false));
        assertFalse(views.validate(views.defaults().with("view_name", "§cmal")).isEmpty());
        assertEquals("kegui vista ir Mirador norte", action(views, "view_go").command().apply(named));
        assertEquals("kegui vistas", action(views, "view_go").command().apply(views.defaults()),
                "sin nombre, la lista en lugar de una orden a medias");
        assertEquals("komaedit vista compartir Mirador norte", action(views, "view_share").command().apply(named));
        assertEquals(List.of(ViewPermissions.SHARE), action(views, "view_share").permissions());
        assertEquals(List.of(ViewPermissions.TELEPORT), action(views, "view_go").permissions());
        assertEquals(List.of(), views.permissions(named), "guardar es del cliente");
        assertFalse(views.previewable());
        ToolContext nothing = new ToolContext(true, java.util.Set.of(), false, 0, 0, false);
        assertFalse(views.locked(nothing), "guardar vistas no pide permiso: nunca sale apagada");
        assertTrue(views.missingPermission(action(views, "view_go"), views.defaults(), nothing) != null);
    }

    @Test
    void theRulerCardTurnsTheRulerOnInEachMode() {
        ToolCard ruler = card("ruler");
        assertTrue(ruler.clientOnly());
        assertEquals("kegui regla linea", ruler.command(ruler.defaults(), false));
        for (RulerMode mode : RulerMode.values()) {
            String command = ruler.command(ruler.defaults().with("ruler_mode", mode.id()), false);
            assertEquals(mode, RulerMode.byName(command.substring("kegui regla ".length())), command);
        }
        assertEquals(List.of("kegui regla copiar", "kegui regla borrar", "kegui regla off"),
                ruler.actions().stream().map(action -> action.command().apply(ruler.defaults())).toList());
        assertTrue(ruler.allPermissions().isEmpty(), "no pide permisos: no toca el servidor");
        assertFalse(ruler.locked(new ToolContext(true, java.util.Set.of(), false, 0, 0, false)),
                "ni sale apagada sin permisos");
    }

    @Test
    void theNoteCardWritesTheCommandTheServerReads() {
        ToolCard note = card("note");
        assertFalse(note.clientOnly());
        assertTrue(note.validate(note.defaults()).stream().anyMatch(ToolProblem::blocking), "sin texto no la pone");
        ToolValues values = note.defaults()
                .with("note", "Revisar la \"escalera\" -c norte")
                .with("note_color", NoteColor.ROJO.id())
                .with("note_icon", NoteIcon.AVISO.id())
                .with("distance", 96)
                .with("team", true);
        String command = note.command(values, false);
        assertTrue(command.startsWith("komaedit nota poner \""), command);
        NoteRequest read = NoteRequest.parse(command.substring("komaedit nota poner ".length()));
        assertEquals("Revisar la \"escalera\" -c norte", read.text(), "el texto tal cual, con sus comillas y guiones");
        assertEquals(NoteColor.ROJO.rgb(), read.color());
        assertEquals(NoteIcon.AVISO, read.icon());
        assertEquals(96, read.distance());
        assertTrue(read.team());
        assertFalse(read.point());
        assertEquals(List.of(NotePermissions.SHARE), note.permissions(values));
        // Con los de serie, la orden más corta.
        ToolValues plain = note.defaults().with("note", "Hola");
        assertEquals("komaedit nota poner \"Hola\"", note.command(plain, false));
        assertEquals(List.of(NotePermissions.PRIVATE), note.permissions(plain));
        assertTrue(note.command(plain.with("here", true), false).endsWith(" -p"));
        assertTrue(note.locked(new ToolContext(true, java.util.Set.of(), false, 0, 0, false)),
                "sin ningún permiso de notas, apagada");
    }
}
