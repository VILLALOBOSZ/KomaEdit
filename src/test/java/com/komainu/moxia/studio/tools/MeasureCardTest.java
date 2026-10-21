package com.komainu.moxia.studio.tools;

import com.komainu.moxia.notes.NoteColor;
import com.komainu.moxia.notes.NoteIcon;
import com.komainu.moxia.notes.NotePermissions;
import com.komainu.moxia.notes.NoteRequest;
import com.komainu.moxia.ruler.RulerMode;
import com.komainu.moxia.views.ViewPermissions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * «Medir y anotar»: la regla, las notas y las vistas como modos de una tarjeta. Cada modo manda la orden que
 * escribiría el jugador, con su botón, su destino y sus botones, y se lee igual al otro lado.
 */
class MeasureCardTest {
    private static final ToolContext NOTHING = new ToolContext(true, Set.of(), false, 0, 0, false);
    private static final ToolCard CARD = ToolCatalog.byId("measure");

    private static ToolValues in(String mode) {
        return CARD.defaults().with(MeasureCard.MODE, mode);
    }

    private static ToolAction action(ToolValues values, String id) {
        return CARD.actions(values).stream().filter(action -> action.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<String> actionIds(ToolValues values) {
        return CARD.actions(values).stream().map(ToolAction::id).toList();
    }

    @Test
    void oneCardInUtilitiesWithTheRulerFirst() {
        assertNotNull(CARD);
        assertEquals(ToolCategory.UTILITIES, ToolCatalog.categoryOf(CARD));
        assertEquals(MeasureCard.MODE, CARD.modeParam());
        assertEquals(MeasureCard.RULER, CARD.defaults().choice(MeasureCard.MODE));
        assertFalse(CARD.locked(NOTHING), "sin permisos sigue la regla, que es del cliente");
        assertFalse(CARD.previewable(CARD.defaults()));
    }

    @Test
    void theRulerModeTurnsTheRulerOnInEachWay() {
        ToolValues ruler = CARD.defaults();
        assertTrue(CARD.clientOnly(ruler));
        assertEquals(ToolTarget.MODE, CARD.target(ruler));
        assertEquals("gui.komaedit.tools.measure.build.regla", CARD.buildKey(ruler));
        assertEquals("kegui regla linea", CARD.command(ruler, false));
        for (RulerMode mode : RulerMode.values()) {
            String command = CARD.command(ruler.with("ruler_mode", mode.id()), false);
            assertEquals(mode, RulerMode.byName(command.substring("kegui regla ".length())), command);
        }
        assertEquals(List.of("ruler_copy", "ruler_clear", "ruler_off"), actionIds(ruler));
        assertEquals(List.of("kegui regla copiar", "kegui regla borrar", "kegui regla off"),
                CARD.actions(ruler).stream().map(action -> action.command().apply(ruler)).toList());
        assertEquals(List.of(), CARD.permissions(ruler));
        assertEquals(null, CARD.missingPermission(ruler, NOTHING));
    }

    @Test
    void theNoteModeWritesTheCommandTheServerReads() {
        ToolValues note = in(MeasureCard.NOTE);
        assertFalse(CARD.clientOnly(note));
        assertEquals(ToolTarget.LOOK, CARD.target(note), "va donde haces clic");
        assertEquals("gui.komaedit.tools.measure.build.nota", CARD.buildKey(note));
        assertTrue(CARD.validate(note).stream().anyMatch(ToolProblem::blocking), "sin texto no la pone");
        ToolValues values = note
                .with("note", "Revisar la \"escalera\" -c norte")
                .with("note_color", NoteColor.ROJO.id())
                .with("note_icon", NoteIcon.AVISO.id())
                .with("distance", 96)
                .with("team", true);
        String command = CARD.command(values, false);
        NoteRequest read = NoteRequest.parse(command.substring("komaedit nota poner ".length()));
        assertEquals("Revisar la \"escalera\" -c norte", read.text(), "el texto tal cual, con comillas y guiones");
        assertEquals(NoteColor.ROJO.rgb(), read.color());
        assertEquals(NoteIcon.AVISO, read.icon());
        assertEquals(96, read.distance());
        assertTrue(read.team());
        assertFalse(read.point());
        assertEquals(List.of(NotePermissions.SHARE), CARD.permissions(values));
        ToolValues plain = note.with("note", "Hola");
        assertEquals("komaedit nota poner \"Hola\"", CARD.command(plain, false), "con lo de serie, la más corta");
        assertEquals(List.of(NotePermissions.PRIVATE), CARD.permissions(plain));
        assertEquals(NotePermissions.PRIVATE, CARD.missingPermission(plain, NOTHING));
        assertTrue(CARD.command(plain.with("here", true), false).endsWith(" -p"));
        assertEquals(List.of("notes_list", "notes_resolved"), actionIds(note));
    }

    @Test
    void theViewModeSavesOnTheClientAndJumpsWithPermission() {
        ToolValues view = in(MeasureCard.VIEW);
        assertTrue(CARD.clientOnly(view));
        assertEquals(ToolTarget.SELF, CARD.target(view), "donde estás");
        assertEquals("gui.komaedit.tools.measure.build.vista", CARD.buildKey(view));
        assertTrue(CARD.validate(view).stream().anyMatch(ToolProblem::blocking), "sin nombre no guarda");
        ToolValues named = view.with("view_name", "  Mirador  norte ");
        assertEquals(List.of(), CARD.validate(named));
        assertFalse(CARD.validate(view.with("view_name", "§cmal")).isEmpty());
        assertEquals("kegui vista guardar Mirador norte", CARD.command(named, false));
        assertEquals(List.of(), CARD.permissions(named), "guardar es del cliente");
        assertEquals(List.of("view_go", "view_pin", "view_share", "view_back", "view_list"), actionIds(view));
        assertEquals("kegui vista ir Mirador norte", action(named, "view_go").command().apply(named));
        assertEquals("kegui vistas", action(view, "view_go").command().apply(view),
                "sin nombre, la lista en lugar de una orden a medias");
        assertEquals("komaedit vista compartir Mirador norte", action(named, "view_share").command().apply(named));
        assertEquals(List.of(ViewPermissions.SHARE), action(named, "view_share").permissions());
        assertEquals(ViewPermissions.TELEPORT, CARD.missingPermission(action(named, "view_go"), named, NOTHING));
    }

    @Test
    void eachModeShowsAtMostFourControls() {
        for (String mode : MeasureCard.MODES) {
            ToolValues values = in(mode);
            long shown = CARD.params().stream()
                    .filter(param -> param.visible(values) && CARD.essential(param)).count();
            assertTrue(shown <= 4, mode + " enseña " + shown);
        }
    }
}
