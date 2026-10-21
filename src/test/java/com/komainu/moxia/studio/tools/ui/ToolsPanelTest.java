package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.capability.BuildCapability;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.net.JobStatusPayload;
import com.komainu.moxia.studio.tools.TerrainCards;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ToolValues;
import com.komainu.moxia.studio.tools.TreeCard;
import com.komainu.moxia.studio.ui.ChoiceThumbnails;
import com.komainu.moxia.studio.ui.FakeStudioHost;
import com.komainu.moxia.studio.ui.StudioDriver;
import com.komainu.moxia.studio.ui.StudioKey;
import com.komainu.moxia.studio.ui.StudioStage;
import com.komainu.moxia.studio.ui.StudioUi;
import com.komainu.moxia.studio.ui.StudioView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La etapa Herramientas como la usa el jugador, dentro del Studio entero: pulsar, escribir, construir. */
class ToolsPanelTest {
    private static final ToolContext ALL = new ToolContext(true, ToolCatalog.permissionNodes(), true, 512L, 0, false);

    private StudioDriver studio;
    private ToolsPanel panel;

    @BeforeAll
    static void thumbnails() {
        ChoiceThumbnails.setBlocking(true);
    }

    @BeforeEach
    void setUp() {
        // Alto de sobra: todo se ve sin desplazar y se puede pulsar por lo que dice. Sin los idiomas del juego, como
        // en el CI: los nombres de bloque que se comprueban vienen de la tabla fija del host.
        studio = new StudioDriver(new StudioView(), new FakeStudioHost("es_es", false), 960, 1400);
        studio.host.context = ALL;
        // Las órdenes tal cual, sin la vista previa en vivo de serie (la prueban LivePreviewTest y las de abajo).
        studio.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.OFF);
        studio.host.info = new ToolsInfo(true, new int[]{8, 5, 8}, List.of("cursiva"),
                Map.of("bosque", "spruce 30 -d 1.4"), Map.of(), ToolsInfo.Symmetry.OFF, null,
                List.of("minecraft:plains", "minecraft:cherry_grove", "minecraft:snowy_plains",
                        "terralith:moonlight_grove"), null);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        panel = studio.view.tools();
    }

    private void select(String id) {
        panel.select(ToolCatalog.byId(id), studio.host);
        studio.render();
    }

    private ToolValues values(String id) {
        return panel.values(ToolCatalog.byId(id));
    }

    private boolean hasTile(String name) {
        studio.render();
        return studio.ui().widgets().stream().anyMatch(widget -> "tools:stamp".equals(widget.group())
                && widget.tooltip().startsWith(name + "\n"));
    }

    private void more() {
        studio.click("MÁS AJUSTES");
    }

    @Test
    void withoutAToolTheGridShowsEveryToolByGroup() {
        assertTrue(panel.browsing());
        for (String group : List.of("NATURALEZA", "EDIFICIOS", "TERRENO", "UTILIDADES", "MODOS")) {
            assertTrue(studio.has(group), group);
        }
        for (String tool : List.of("Plantar árbol", "Poner roca", "Poner tejado", "Excavar río", "Escribir texto",
                "Pintar", "Retocar", "Simetría", "Construir a mano")) {
            assertTrue(studio.ui().widgets().stream().anyMatch(widget -> widget.tooltip().startsWith(tool + "\n")),
                    tool);
        }
        assertFalse(studio.has("ESPECIE"), "sin herramienta no hay ajustes");
    }

    @Test
    void choosingAToolFoldsTheGridAndShowsItsSettings() {
        studio.press("tool:roof");
        assertEquals("roof", panel.selected().id());
        assertFalse(panel.browsing(), "la cuadrícula se pliega");
        assertFalse(studio.has("NATURALEZA"));
        assertTrue(studio.has("Dos aguas"), "se ven las miniaturas de los tipos");
        assertTrue(studio.has("Construir tejado"), "y el botón del pie");
        assertEquals("roof", studio.host.preferences.toolRecents().getFirst());
        assertEquals("roof", studio.host.preferences.tool());
        // La cabecera de la herramienta vuelve a abrir la cuadrícula.
        studio.click("Poner tejado");
        assertTrue(panel.browsing());
    }

    @Test
    void theHeaderOffersTheRecentToolsInOneClick() {
        select("tree");
        select("roof");
        select("river");
        assertNotNull(studio.widget("Reciente: Poner tejado"));
        studio.click("Reciente: Poner tejado");
        assertEquals("roof", panel.selected().id());
    }

    @Test
    void theSearchFiltersAndEnterPicksTheFirstMatch() {
        select("tree");
        studio.key(StudioKey.letter('F', true, false));
        assertTrue(panel.typing());
        assertTrue(panel.browsing(), "buscar abre la cuadrícula");
        studio.type("nieve");
        assertEquals("touchup", panel.visibleCards(studio.host).getFirst().id(), "la nieve es ahora un modo de Retocar");
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertEquals("touchup", panel.selected().id());
        assertFalse(panel.typing());
        assertFalse(panel.browsing());
    }

    @Test
    void favoritesGetTheirOwnGroupOnTop() {
        studio.host.preferences.toggleToolFavorite("text");
        List<String> texts = studio.texts();
        assertTrue(texts.indexOf("FAVORITAS") >= 0 && texts.indexOf("FAVORITAS") < texts.indexOf("NATURALEZA"));
    }

    @Test
    void buildSendsTheSameCommandAsTypingIt() {
        select("tree");
        studio.click("Plantar");
        assertEquals("komaedit tree oak", studio.host.last());
        studio.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.BUILD);
        studio.click("Plantar");
        assertEquals("komaedit tree oak -v", studio.host.last());
    }

    @Test
    void thePreviewEyeCyclesOffBuildAndLive() {
        select("tree");
        studio.click("Sin vista previa");
        assertEquals(StudioPreferences.PreviewMode.BUILD, studio.host.preferences.previewMode());
        studio.click("Vista previa al construir");
        assertEquals(StudioPreferences.PreviewMode.LIVE, studio.host.preferences.previewMode());
        studio.reset("Vista previa en vivo");
        assertEquals(StudioPreferences.PreviewMode.OFF, studio.host.preferences.previewMode());
    }

    @Test
    void withoutPermissionTheCardIsOffAndSaysWhy() {
        studio.host.context = new ToolContext(true, Set.of(TerrainCards.RIVER_NODE), true, 512L, 4, false);
        select("erosion");
        assertEquals(StudioUi.Tone.DISABLED, panel.buildTone(studio.host));
        assertEquals("Sin permiso", panel.buildLabel(studio.host));
        studio.click("Sin permiso");
        assertNull(studio.host.last(), "no manda nada");
        assertTrue(studio.view.status().contains(TerrainCards.HYDRAULIC_NODE), studio.view.status());
        assertTrue(studio.shows("Te falta el permiso " + TerrainCards.HYDRAULIC_NODE), "aviso en la tarjeta");
    }

    @Test
    void whatIsMissingInTheWorldBlocksTheBuildAndLeadsToThePath() {
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L, 0, false);
        select("roof");
        assertEquals("Falta la selección", panel.buildLabel(studio.host));
        studio.click("Falta la selección");
        assertNull(studio.host.last());
        select("river");
        studio.click("Marcar puntos");
        assertEquals(StudioStage.PATH, studio.view.stage(), "la franja lleva a Trayectoria");
        assertTrue(studio.shows("Excavar río · "), "y el pie sigue siendo el del río");
    }

    @Test
    void numbersShowTheirUnitAndRangeAndResetWithRightClick() {
        select("tree");
        assertTrue(studio.has("auto · 18 bloques"), "la altura automática, con su unidad");
        assertTrue(studio.has("100 %"), "el grosor es un tanto por ciento");
        studio.wheelValue("Altura", 1);
        assertEquals(19, values("tree").number("height"), "arranca de la de la especie");
        assertTrue(studio.has("19 bloques"));
        studio.wheelValue("Grosor", 1);
        assertEquals(105, values("tree").number("thickness"));
        studio.reset("Grosor");
        assertEquals(100, values("tree").number("thickness"));
        assertTrue(studio.view.status().contains("serie"), studio.view.status());
    }

    @Test
    void clickingAValueLetsYouTypeItInItsUnit() {
        select("tree");
        studio.clickValue("Grosor");
        assertTrue(panel.typing(), "pulsar sin arrastrar abre el campo");
        assertTrue(studio.shows("25–400 %"), "al escribir se ve el rango");
        studio.key(new StudioKey(StudioKey.Code.BACKSPACE, '\0', true, false));
        studio.type("150");
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertEquals("1.5", values("tree").formatted("thickness"), "150 % es 1.5 en la orden");
        studio.click("Plantar");
        assertEquals("komaedit tree oak -g 1.5", studio.host.last());
    }

    @Test
    void draggingANumberWorksLikeASliderAndShiftGoesFine() {
        select("snow");
        studio.dragValue("Capas", 13, false);
        assertEquals(4, values("snow").number("layers"), "dos pasos de 6 píxeles");
        assertFalse(panel.typing(), "un arrastre no abre el campo");
        studio.dragValue("Capas", -13, true);
        assertEquals(3, values("snow").number("layers"), "con Mayús, un paso cada 12 píxeles");
    }

    @Test
    void textFieldsApplyWhatIsTypedAndRefuseBrokenCharacters() {
        select("text");
        studio.click("KomaEdit");
        assertTrue(panel.typing());
        studio.key(new StudioKey(StudioKey.Code.BACKSPACE, '\0', true, false));
        studio.view.paste("Plaza Mayor\nde Alcázara§", studio.host);
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertEquals("Plaza Mayor de Alcázara", values("text").text("text"));
        studio.click("Escribir");
        assertEquals("komaedit text \"Plaza Mayor de Alcázara\" stone", studio.host.last());
    }

    @Test
    void blocksComeFromTheBlockLibraryOrTheEyedropper() {
        select("roof");
        assertTrue(studio.has("Tablones de abeto"), "el nombre del bloque, no su id");
        studio.click("Tablones de abeto");
        assertTrue(studio.view.picker().isOpen(), "abre la biblioteca de bloques");
        studio.type("deepslate tiles");
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertFalse(studio.view.picker().isOpen());
        assertEquals("deepslate_tiles", values("roof").block("material"));
        assertEquals("minecraft:deepslate_tiles", studio.host.preferences.blockRecents().getFirst());
        studio.host.lookedAt = "minecraft:stone_bricks";
        studio.click("Usar el bloque que miras");
        assertEquals("stone_bricks", values("roof").block("material"));
        studio.reset("Ladrillos de piedra");
        assertEquals("spruce_planks", values("roof").block("material"));
    }

    @Test
    void withoutATranslationTheBlockShowsAReadableNameNotItsId() {
        // Como un bloque de un mod sin traducción, o el juego sin sus idiomas: nunca el id crudo.
        studio.host.blockNames.clear();
        select("roof");
        assertTrue(studio.has("Spruce Planks"), studio.texts().toString());
        assertFalse(studio.texts().stream().anyMatch(text -> text.contains("spruce_planks")), "ni el id");
        studio.host.lookedAt = "examplemod:mossy_slate_tiles";
        studio.click("Usar el bloque que miras");
        assertEquals("examplemod:mossy_slate_tiles", values("roof").block("material"));
        assertTrue(studio.has("Mossy Slate Tiles"), studio.texts().toString());
        assertFalse(studio.texts().stream().anyMatch(text -> text.contains("mossy_slate")), "ni el id del mod");
        // Con traducción, la traducción.
        studio.host.blockNames.put("block.examplemod.mossy_slate_tiles", "Pizarra musgosa");
        assertTrue(studio.has("Pizarra musgosa"), studio.texts().toString());
    }

    @Test
    void optionalBlocksSayWhatTheyUseInAuto() {
        select("roof");
        more();
        assertTrue(studio.has("Como el tejado"), "el caballete dice qué usa");
        assertTrue(studio.has("El de los muros"));
    }

    @Test
    void presetsAndRandomSeedFillTheSettings() {
        select("tree");
        studio.click("Viejo");
        assertEquals(160, values("tree").number("thickness"));
        studio.click("Azar");
        assertNotNull(values("tree").number("seed"));
        studio.click("#bosque");
        assertEquals("spruce", values("tree").choice("species"));
        assertEquals(30, values("tree").number("height"));
    }

    @Test
    void theSpeciesAreThumbnailsWithTheirNames() {
        select("tree");
        assertTrue(studio.has("Abeto"));
        studio.click("Abeto");
        assertEquals("spruce", values("tree").choice("species"));
        studio.render();
        assertTrue(studio.canvas().images().stream().anyMatch(key -> key.contains("tree/species/spruce")),
                studio.canvas().images().toString());
    }

    @Test
    void theRestIsFoldedUnderMoreSettingsAndCountsWhatChanged() {
        select("tree");
        assertFalse(studio.has("Semilla"), "la semilla va plegada");
        studio.click("MÁS AJUSTES");
        assertTrue(studio.has("Semilla"));
        assertTrue(studio.host.preferences.expanded(ToolsPanel.MORE_KEY + "tree"), "se recuerda abierto");
        studio.click("Azar");
        assertTrue(studio.has("1 cambiados"));
    }

    @Test
    void savingAPresetAsksForANameAndSendsTheSaveCommand() {
        select("tree");
        more();
        studio.click("Guardar preset");
        assertTrue(panel.typing());
        studio.type("Mi_Bosque");
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertEquals("komaedit tree save mi_bosque oak -f", studio.host.last());
        assertFalse(panel.typing());
    }

    @Test
    void theUseSwitchTurnsTheTreeIntoABrushWithTheSameSettings() {
        select("tree");
        studio.click("Abeto");
        studio.click("Pincel");
        assertTrue(studio.has("Al pincel"), "el botón dice que deja un pincel");
        studio.click("Al pincel");
        assertEquals("komaedit pincel tree spruce", studio.host.last(), "la misma especie, ahora en el pincel");
        studio.host.context = new ToolContext(true, Set.of(TreeCard.NODE), true, 1L, 0, false);
        studio.render();
        assertTrue(studio.has("Sin permiso"), "sin el permiso del pincel, el botón lo dice");
        studio.click("Con un clic");
        studio.click("Plantar");
        assertEquals("komaedit tree spruce", studio.host.last(), "y de vuelta, donde haces clic");
    }

    @Test
    void theStampGalleryPagesAndPicksOne() {
        select("stamp");
        // Por categorías: ‹ › en la categoría hasta los cráteres.
        for (int step = 0; step < 14 && !hasTile("Cráter"); step++) {
            studio.clickValue("Categoría");
        }
        studio.click("Cráter", "tools:stamp");
        assertEquals("crater", values("stamp").choice("stamp"));
        studio.click("Al pincel");
        assertEquals("komaedit pincel heightmap crater 12 5 sumar auto", studio.host.last());
        // Buscando sale de todas las categorías, por páginas.
        studio.clickKey("tools:stamp_search");
        studio.type("a");
        assertTrue(studio.has("‹") && studio.has("›"), "más sellos que casillas: se pasa de página");
        studio.click("›", "tools:stamp:page");
    }

    @Test
    void theBiomeSearchFindsServerBiomes() {
        select("touchup");
        studio.click("Cambiar bioma", "tools:touch");
        studio.clickKey("tools:biome");
        assertTrue(panel.typing());
        studio.type("moon");
        studio.key(StudioKey.of(StudioKey.Code.ENTER));
        assertEquals("terralith:moonlight_grove", values("touchup").choice("biome"));
        studio.click("Cambiar bioma");
        assertEquals("komaedit retocar bioma terralith:moonlight_grove", studio.host.last());
    }

    @Test
    void symmetryFollowsTheServerAndTogglesWorldEdit() {
        studio.host.info = new ToolsInfo(true, new int[0], List.of(), Map.of(), Map.of(),
                new ToolsInfo.Symmetry(true, "radial 8 y", 8, "x 0, y 64, z 0", false, false), null, List.of(),
                null);
        select("symmetry");
        assertEquals("radial", values("symmetry").choice("mode"));
        assertEquals("Aplicar modo", panel.buildLabel(studio.host));
        studio.click("Reflejar WorldEdit y KomaEdit");
        assertEquals("komaedit symmetry worldedit on", studio.host.last());
        more();
        studio.click("Desactivar");
        assertEquals("komaedit symmetry off", studio.host.last());
        studio.click("Centro de la selección");
        assertEquals("komaedit symmetry center selection", studio.host.last());
    }

    @Test
    void capabilitiesToggleRightAwayAndSendNumbersWhenLeftAlone() {
        studio.host.info = new ToolsInfo(true, new int[0], List.of(), Map.of(), Map.of(), ToolsInfo.Symmetry.OFF,
                new ToolsInfo.Capabilities(BuildCapability.REPLACE.bit(), BuildCapability.REPLACE.bit()
                        | BuildCapability.REACH.bit(), true, true, false, false, 256, 4, 24, 10, 64, 4096, 100),
                List.of(), null);
        select("capabilities");
        studio.click("Reemplazar");
        assertEquals("komaedit capability replace off", studio.host.last());
        studio.click("Extruir · sin permiso");
        assertEquals("komaedit capability replace off", studio.host.last(), "sin permiso no manda nada");
        studio.wheelValue("Alcance", 1);
        studio.wheelValue("Alcance", 1);
        studio.tick();
        assertEquals("komaedit capability replace off", studio.host.last(), "aún no: se está tocando");
        studio.advance(ToolsPanel.OPTION_DELAY_MILLIS + 1);
        assertEquals("komaedit capability option reach 26", studio.host.last());
    }

    @Test
    void theMaterialListShowsBlockNamesAndCopies() {
        studio.host.info = new ToolsInfo(true, new int[]{4, 4, 4}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of(), new ToolsInfo.Materials(64, 2, 70, 3, 1, List.of(
                new ToolsInfo.Materials.Line("minecraft:oak_planks", 66, 64),
                new ToolsInfo.Materials.Line("minecraft:torch", 4, 64)), 0, "oak_planks: 66\ntorch: 4"));
        select("analyze");
        studio.click("Copiar lista");
        assertEquals("oak_planks: 66\ntorch: 4", studio.host.copied.getLast());
        assertTrue(studio.has("Tablones de roble"), "el nombre traducido, no el id");
        assertTrue(studio.has("(1 pilas + 2)"));
    }

    @Test
    void undoAndRedoGoBackThroughTheSettings() {
        select("snow");
        studio.wheelValue("Capas", 1);
        assertEquals(3, values("snow").number("layers"));
        studio.key(StudioKey.letter('Z', false, false));
        assertEquals(2, values("snow").number("layers"));
        studio.key(StudioKey.letter('Z', false, true));
        assertEquals(3, values("snow").number("layers"));
    }

    @Test
    void theStateIsAskedOncePerSecond() {
        studio.tick();
        studio.tick();
        assertEquals(1, studio.host.requests);
        studio.advance(ToolsPanel.REFRESH_MILLIS);
        assertEquals(2, studio.host.requests);
    }

    @Test
    void theCopyIconCopiesTheChatCommand() {
        select("touchup");
        studio.click("Dejar caer", "tools:touch");
        studio.click("Copiar la orden del chat");
        assertEquals("/komaedit retocar gravedad", studio.host.copied.getLast());
    }

    @Test
    void theFooterConfirmsDiscardsAndCancelsWhatIsRunning() {
        select("roof");
        studio.host.job = JobStatusPayload.of(new ProgressLine("Tejado", ProgressLine.Phase.WAITING, 59_000, 90_000,
                0, null), 1_234);
        assertTrue(studio.has("1 234 cambios · caduca en 59 s · Tejado"), studio.texts().toString());
        studio.click("Confirmar");
        assertEquals("komaedit confirm", studio.host.last());
        studio.click("Descartar");
        assertEquals("komaedit cancel", studio.host.last());

        select("snow");
        studio.host.job = JobStatusPayload.of(new ProgressLine("Nieve", ProgressLine.Phase.PLACING, 500, 1_000,
                2_000_000_000L, null), 0);
        assertTrue(studio.has("Colocando 50 % · ~2 s · 250 bl/s · Nieve"), studio.texts().toString());
        studio.click("Cancelar");
        assertEquals("komaedit cancel", studio.host.last());
        studio.click("Nevar");
        assertEquals("komaedit retocar nieve 2", studio.host.last(), "mientras se coloca, Construir sigue a mano");
    }

    @Test
    void theLastToolAndItsSettingsAreRemembered() {
        select("tree");
        studio.wheelValue("Altura", 1);
        studio.advance(ToolsPanel.SAVE_DELAY_MILLIS + 1);
        assertEquals(Map.of("height", 19), studio.host.preferences.toolValues("tree"));
        // Otro Studio (otra partida) con las mismas preferencias empieza donde se dejó.
        StudioDriver again = new StudioDriver(new StudioView(), studio.host, 960, 1400);
        again.render();
        assertEquals(StudioStage.TOOLS, again.view.stage());
        assertEquals("tree", again.view.tools().selected().id());
        assertFalse(again.view.tools().browsing());
        assertEquals(19, again.view.tools().values(ToolCatalog.byId("tree")).number("height"));
    }

    /**
     * Deshacer y rehacer un ajuste también se guarda: si no, al abrir otra partida volvía el valor deshecho (el que
     * ya estaba en disco), no el que se veía al cerrar.
     */
    @Test
    void undoingASettingIsRememberedLikeChangingIt() {
        select("tree");
        studio.wheelValue("Altura", 1);
        studio.advance(ToolsPanel.SAVE_DELAY_MILLIS + 1);
        assertEquals(Map.of("height", 19), studio.host.preferences.toolValues("tree"));
        studio.key(StudioKey.letter('Z', false, false));
        studio.advance(ToolsPanel.SAVE_DELAY_MILLIS + 1);
        assertEquals(Map.of(), studio.host.preferences.toolValues("tree"), "deshecho: la altura vuelve a auto");
        studio.key(StudioKey.letter('Y', false, false));
        studio.view.closed(studio.host);
        assertEquals(Map.of("height", 19), studio.host.preferences.toolValues("tree"), "rehecho y cerrado enseguida");
    }

    /** Deshacer puede volver a otra herramienta: es la que se recuerda, no la que se dejó al deshacer. */
    @Test
    void undoingBackToAnotherToolRemembersThatTool() {
        select("tree");
        studio.wheelValue("Altura", 1);
        select("rock");
        studio.key(StudioKey.letter('Z', false, false));
        assertEquals("tree", panel.selected().id());
        assertEquals("tree", studio.host.preferences.tool());
    }

    /**
     * El nombre de la herramienta elegida se lee entero arriba de la tarjeta: con el panel de serie a 1080p (GUI 3),
     * a 854×480 (GUI 2) y a 1080p con GUI 4, en los dos idiomas. Si no cabe en negrita, va en dos líneas, como en la
     * cuadrícula.
     */
    @Test
    void theNameOfTheChosenToolIsNeverCutInTheHeader() {
        List<String> cut = new java.util.ArrayList<>();
        for (String language : List.of("es_es", "en_us")) {
            for (int[] screen : new int[][]{{640, 360}, {427, 240}, {480, 270}}) {
                for (var card : ToolCatalog.ALL) {
                    StudioDriver small = new StudioDriver(new StudioView(), new FakeStudioHost(language, false),
                            screen[0], screen[1]);
                    small.host.context = ALL;
                    small.view.selectStage(StudioStage.TOOLS, small.host);
                    small.view.tools().select(card, small.host);
                    String name = small.host.tr(card.nameKey());
                    small.render();
                    var header = small.ui().widgets().stream()
                            .filter(widget -> widget.tooltip() != null && widget.tooltip().startsWith(name + "\n"))
                            .findFirst().orElseThrow();
                    String shown = small.canvas().texts().stream()
                            .filter(text -> header.bounds().contains(text.x(), text.y() + 4))
                            .map(text -> text.text())
                            .reduce((first, second) -> first + " " + second).orElse("");
                    if (!shown.equals(name)) {
                        cut.add(language + " " + screen[0] + "×" + screen[1] + " " + card.id() + ": «" + shown + "»");
                    }
                }
            }
        }
        assertEquals(List.of(), cut);
    }
}
