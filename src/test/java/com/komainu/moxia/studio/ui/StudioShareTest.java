package com.komainu.moxia.studio.ui;

import com.komainu.moxia.blueprint.BlueprintRef;
import com.komainu.moxia.blueprint.BlueprintScope;
import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.share.ShareCodePayload;
import com.komainu.moxia.share.ShareImport;
import com.komainu.moxia.share.ShareOfferPayload;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * «Compartir» y «Pegar código» en el Studio: los botones mandan la orden de siempre, el código que llega se anuncia
 * en el pie y lo que trae un paquete sale en su ventana, que contesta con {@code /komaedit share accept|cancel}.
 */
class StudioShareTest {
    private StudioDriver studio;

    @BeforeEach
    void setUp() {
        studio = new StudioDriver("es_es", 960, 600);
        studio.host.blueprints.list.add(new BlueprintSummary(BlueprintRef.display("torre", BlueprintScope.PLAYER),
                "torre", false, true, "Alicia", 0L, 9, 14, 9, 600, List.of("medieval"), "torre"));
        studio.render();
    }

    @Test
    void blueprintsHaveShareAndToServer() {
        studio.view.selectStage(StudioStage.BLUEPRINTS, studio.host);
        studio.view.blueprints().select("torre");
        assertTrue(studio.has("Al servidor"), studio.texts().toString());
        studio.click("Compartir");
        assertEquals("komaedit share export plano:torre", studio.host.last());
        studio.click("Al servidor");
        assertEquals("komaedit blueprint share torre", studio.host.last());
    }

    @Test
    void pasteCodeSendsTheClipboardOnlyIfItIsACode() {
        studio.view.selectStage(StudioStage.BLUEPRINTS, studio.host);
        studio.host.clipboard = "hola, esto no es un código";
        studio.click("Pegar código de otro servidor");
        assertTrue(studio.host.imported.isEmpty());
        studio.host.clipboard = "  ```kpack1:QzYzKD85```  ";
        studio.click("Pegar código de otro servidor");
        assertEquals(List.of("```kpack1:QzYzKD85```"), studio.host.imported);
        // Ctrl+V sin ningún campo abierto también vale.
        studio.view.paste("kp1:abc", studio.host);
        assertEquals("kp1:abc", studio.host.imported.getLast());
    }

    @Test
    void theOfferWindowAnswersWithTheUsualCommands() {
        studio.host.offer = new ShareOfferPayload("Casas de Villa", "Alicia", "1.9.1", 1, List.of(
                new ShareOfferPayload.Line(ShareImport.Status.NEW.ordinal(), "paleta «rocas»", ""),
                new ShareOfferPayload.Line(ShareImport.Status.CONFLICT.ordinal(), "plano «torre»",
                        "ya tienes plano con ese nombre"),
                new ShareOfferPayload.Line(ShareImport.Status.UNKNOWN.ordinal(), "hotbar «b»", "tipo que no se conoce")));
        studio.tick();
        assertTrue(studio.view.shareDialog().isOpen());
        assertTrue(studio.view.modal());
        assertTrue(studio.shows("Importar «Casas de Villa»"), studio.texts().toString());
        assertTrue(studio.has("Con otro nombre"));
        studio.click("Reemplazar");
        assertEquals("komaedit share accept reemplazar", studio.host.last());
        assertFalse(studio.view.shareDialog().isOpen());

        studio.host.offer = new ShareOfferPayload("", "", "", 0, List.of(
                new ShareOfferPayload.Line(ShareImport.Status.NEW.ordinal(), "ruido «suave»", "")));
        studio.tick();
        assertTrue(studio.has("Importar"));
        studio.key(StudioKey.of(StudioKey.Code.ESCAPE));
        assertEquals("komaedit share cancel", studio.host.last());
        assertFalse(studio.view.shareDialog().isOpen());

        studio.host.offer = new ShareOfferPayload("", "", "", 0, List.of(
                new ShareOfferPayload.Line(ShareImport.Status.NEW.ordinal(), "ruido «suave»", "")));
        studio.tick();
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertEquals("komaedit share accept renombrar", studio.host.last());
    }

    @Test
    void aPackWithNothingToImportOnlyCloses() {
        studio.host.offer = new ShareOfferPayload("x", "", "", 0, List.of(
                new ShareOfferPayload.Line(ShareImport.Status.INVALID.ordinal(), "receta «mala»", "fórmula rota")));
        studio.tick();
        int before = studio.host.sent.size();
        studio.click("Cerrar");
        assertEquals(before, studio.host.sent.size(), "no hay nada pendiente que cancelar");
        assertFalse(studio.view.shareDialog().isOpen());
    }

    @Test
    void theCodeThatArrivesIsAnnouncedInTheFooter() {
        studio.host.shareResult = new ShareCodePayload("kpack1:abcdef", "", "1 paleta");
        studio.tick();
        assertTrue(studio.shows("Código copiado al portapapeles: 1 paleta (13 caracteres)"), studio.texts()
                .toString());
    }

    @Test
    void savedTreePresetsAndPalettesCanBeShared() {
        studio.host.context = new ToolContext(true, Set.copyOf(ToolCatalog.permissionNodes()), false, 0L, 0, false);
        studio.host.info = new ToolsInfo(true, new int[0], List.of(), Map.of("abeto", "spruce 24 -g 1.5"), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of(), null);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        studio.view.tools().select(ToolCatalog.byId("tree"), studio.host);
        studio.click("Compartir #abeto");
        assertEquals("komaedit share export tree:abeto", studio.host.last());

        studio.host.palettes = Map.of("rocas", "stone;andesite");
        studio.view.selectStage(StudioStage.COLOR, studio.host);
        studio.click("Compartir #rocas");
        assertEquals("komaedit share export paleta:rocas", studio.host.last());
    }
}
