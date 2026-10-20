package com.komainu.moxia.studio.ui;

import com.komainu.moxia.blueprint.BlueprintRef;
import com.komainu.moxia.blueprint.BlueprintScope;
import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.studio.tools.ToolCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La paleta de órdenes (Ctrl+K): encuentra etapas, herramientas, presets, figuras y planos, y los abre. */
class CommandPaletteTest {
    private StudioDriver studio;

    @BeforeEach
    void setUp() {
        studio = new StudioDriver("es_es", 960, 540);
        studio.host.blueprints.list.add(new BlueprintSummary(BlueprintRef.display("casita", BlueprintScope.SHARED),
                "casita", true, false, "Villalobos", 0L, 9, 9, 7, 342, List.of("aldea"), "casita"));
        studio.render();
    }

    private void open(String query) {
        studio.key(StudioKey.letter('K', true, false));
        assertTrue(studio.view.palette().isOpen());
        studio.type(query);
    }

    @Test
    void controlKOpensItAndEscapeClosesIt() {
        open("");
        assertTrue(studio.view.typing(), "con la paleta abierta las letras se escriben");
        studio.key(StudioKey.of(StudioKey.Code.ESCAPE));
        assertFalse(studio.view.palette().isOpen());
        studio.click("Paleta de órdenes (Ctrl+K): etapas, herramientas, presets, figuras y planos");
        assertTrue(studio.view.palette().isOpen(), "también desde su botón");
    }

    @Test
    void findsAToolAndEnterOpensIt() {
        open("teja");
        assertEquals("Tejado", studio.view.palette().results().getFirst().title());
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertFalse(studio.view.palette().isOpen());
        assertEquals(StudioStage.TOOLS, studio.view.stage());
        assertEquals("roof", studio.view.tools().selected().id());
        assertFalse(studio.view.tools().browsing());
    }

    @Test
    void findsStagesWithoutAccents() {
        open("modificadores");
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertEquals(StudioStage.MODIFIERS, studio.view.stage());
        open("biblio planos");
        assertEquals(CommandPalette.Kind.STAGE, studio.view.palette().results().getFirst().kind());
    }

    @Test
    void presetsOpenTheirToolWithTheValuesApplied() {
        open("joven");
        CommandPalette.Entry first = studio.view.palette().results().getFirst();
        assertEquals(CommandPalette.Kind.PRESET, first.kind());
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertEquals("tree", studio.view.tools().selected().id());
        assertEquals(9, studio.view.tools().values(ToolCatalog.byId("tree")).number("height"));
    }

    @Test
    void figuresAndBlueprintsAreThereToo() {
        open("trenza");
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertEquals(StudioStage.FIGURE, studio.view.stage());
        assertEquals(PathShape.BRAID, studio.view.figure().draft().shape());
        open("casita");
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertEquals(StudioStage.BLUEPRINTS, studio.view.stage());
        assertEquals("casita", studio.view.blueprints().selectedName(studio.host));
    }

    @Test
    void arrowsChooseAndTheMouseToo() {
        open("tejado");
        studio.key(StudioKey.of(StudioKey.Code.DOWN));
        assertEquals(1, studio.view.palette().cursor());
        studio.key(StudioKey.of(StudioKey.Code.UP));
        studio.key(StudioKey.of(StudioKey.Code.UP));
        assertEquals(studio.view.palette().results().size() - 1, studio.view.palette().cursor(), "da la vuelta");
        studio.click("Tejado: Empinado");
        assertEquals("roof", studio.view.tools().selected().id());
        assertEquals("2:1", studio.view.tools().values(ToolCatalog.byId("roof")).choice("slope"));
    }

    @Test
    void nothingMatchingSaysSo() {
        open("zzzz");
        assertTrue(studio.view.palette().results().isEmpty());
        assertTrue(studio.shows("Sin resultados"), studio.texts().toString());
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertTrue(studio.view.palette().isOpen(), "Intro sin resultados no cierra");
    }
}
