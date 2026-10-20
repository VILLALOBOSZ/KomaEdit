package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.capability.BuildCapability;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.StudioLayout;
import com.komainu.moxia.studio.tools.TerrainCards;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.TreeCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La etapa Herramientas como la usa el jugador: pulsar, escribir, construir, sin Minecraft. */
class ToolsPanelTest {
    private static final MinecraftFont FONT = new MinecraftFont();
    private static final ToolContext ALL = new ToolContext(true, ToolCatalog.permissionNodes(), true, 512L, 0, false);

    private ToolsPanel panel;
    private TestHost host;
    private FakeToolsUi ui;

    @BeforeEach
    void setUp() {
        panel = new ToolsPanel();
        host = new TestHost("es_es");
        host.context = ALL;
        host.info = new ToolsInfo(true, new int[]{8, 5, 8}, List.of("cursiva"), Map.of("bosque", "spruce 30 -d 1.4"),
                Map.of(), ToolsInfo.Symmetry.OFF, null, List.of("minecraft:plains", "minecraft:cherry_grove",
                "minecraft:snowy_plains", "terralith:moonlight_grove"), null);
        ui = new FakeToolsUi(FONT);
    }

    private void draw() {
        ui.reset();
        panel.draw(ui, new Column(0, 0, 224), host);
    }

    private void click(String label) {
        draw();
        ui.zone(label).press();
    }

    private void select(String id) {
        panel.select(ToolCatalog.byId(id), host);
        draw();
    }

    @Test
    void choosingAToolFromTheListShowsItsCard() {
        click("Tejado");
        assertEquals("roof", panel.selected().id());
        draw();
        assertTrue(ui.has("Dos aguas"), "se ven los ajustes del tejado");
        assertTrue(host.status.contains("Tejado"));
        assertEquals("roof", host.preferences.toolRecents().getFirst());
    }

    @Test
    void theSearchFiltersAndEnterPicksTheFirstMatch() {
        click("Buscar herramienta…  (Ctrl+F)");
        assertTrue(panel.typing());
        for (char character : "nieve".toCharArray()) {
            panel.type(character);
        }
        assertEquals("snow", panel.visibleCards(host).getFirst().id());
        panel.key(ToolsPanel.Key.ENTER, false, host);
        assertEquals("snow", panel.selected().id());
        assertFalse(panel.typing());
    }

    @Test
    void familiesAndFavoritesFilterTheList() {
        click("Terreno");
        assertEquals(List.of("stamp", "ramp", "river", "erosion", "paint"),
                panel.visibleCards(host).stream().map(card -> card.id()).toList());
        host.preferences.toggleToolFavorite("text");
        click("★ Favoritas");
        assertEquals(List.of("text"), panel.visibleCards(host).stream().map(card -> card.id()).toList());
    }

    @Test
    void buildSendsTheSameCommandAsTypingIt() {
        select("tree");
        panel.build(host);
        assertEquals("komaedit tree oak", host.last());
        host.preferences.setToolPreview(true);
        panel.build(host);
        assertEquals("komaedit tree oak -v", host.last());
        assertTrue(panel.buildLabel(host).contains("vista previa"));
    }

    @Test
    void withoutPermissionTheCardIsOffAndSaysWhy() {
        host.context = new ToolContext(true, Set.of(TerrainCards.RIVER_NODE), true, 512L, 4, false);
        select("erosion");
        draw();
        assertEquals(ToolsUi.Tone.DISABLED, panel.buildTone(host));
        assertEquals("Sin permiso", panel.buildLabel(host));
        panel.build(host);
        assertNull(host.last(), "no manda nada");
        assertTrue(host.status.contains(TerrainCards.HYDRAULIC_NODE), host.status);
        assertTrue(ui.has("Te falta el permiso " + TerrainCards.HYDRAULIC_NODE), "aviso en la tarjeta");
    }

    @Test
    void whatIsMissingInTheWorldBlocksTheBuildAndLeadsToThePath() {
        host.context = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L, 0, false);
        select("roof");
        assertEquals("Falta la selección", panel.buildLabel(host));
        panel.build(host);
        assertNull(host.last());
        select("river");
        panel.build(host);
        assertEquals(1, host.pathVisits, "faltan puntos: lleva a Trayectoria");
        assertTrue(host.status.startsWith("Marca al menos dos puntos"));
    }

    @Test
    void stepsWheelAndTypedNumbersChangeTheSettings() {
        select("tree");
        FakeToolsUi.Recorded height = ui.zoneWithKey("tools:number:height");
        height.scroll.accept(1);
        assertEquals(19, panel.values(ToolCatalog.byId("tree")).number("height"), "arranca de la de la especie");
        draw();
        ui.zoneWithKey("tools:number:thickness").reset.run();
        // Pulsar el valor sin arrastrar abre el campo; Intro aplica.
        ui.zoneWithKey("tools:number:thickness").clickAt.run(190, ui.zoneWithKey("tools:number:thickness").bounds.centerY());
        panel.release(host);
        assertTrue(panel.typing());
        panel.key(ToolsPanel.Key.BACKSPACE, true, host);
        for (char character : "1,5".toCharArray()) {
            panel.type(character);
        }
        panel.key(ToolsPanel.Key.ENTER, false, host);
        assertEquals("1.5", panel.values(ToolCatalog.byId("tree")).formatted("thickness"));
        assertEquals("komaedit tree oak 19 -g 1.5", ToolCatalog.byId("tree").command(
                panel.values(ToolCatalog.byId("tree")), false));
    }

    @Test
    void draggingANumberWorksLikeASlider() {
        select("snow");
        ui.zoneWithKey("tools:number:layers").clickAt.run(190, ui.zoneWithKey("tools:number:layers").bounds.centerY());
        assertTrue(panel.dragging());
        panel.drag(host.mouseX + 13, host);
        panel.release(host);
        assertEquals(4, panel.values(ToolCatalog.byId("snow")).number("layers"), "dos pasos de 6 píxeles");
        assertFalse(panel.typing());
    }

    @Test
    void textFieldsApplyWhatIsTypedAndRefuseBrokenCharacters() {
        select("text");
        click("KomaEdit");
        assertTrue(panel.typing());
        panel.key(ToolsPanel.Key.BACKSPACE, true, host);
        panel.paste("Plaza Mayor\nde Alcázara§");
        panel.key(ToolsPanel.Key.ENTER, false, host);
        assertEquals("Plaza Mayor de Alcázara", panel.values(ToolCatalog.byId("text")).text("text"));
        panel.build(host);
        assertEquals("komaedit text \"Plaza Mayor de Alcázara\" stone", host.last());
    }

    @Test
    void blocksComeFromTheBlockLibrary() {
        select("roof");
        click("Material");
        host.picker.accept("minecraft:deepslate_tiles");
        assertEquals("deepslate_tiles", panel.values(ToolCatalog.byId("roof")).block("material"));
        draw();
        ui.zone("Material").reset.run();
        assertEquals("spruce_planks", panel.values(ToolCatalog.byId("roof")).block("material"));
    }

    @Test
    void presetsAndRandomSeedFillTheSettings() {
        select("tree");
        click("Viejo");
        assertEquals(160, panel.values(ToolCatalog.byId("tree")).number("thickness"));
        click("Azar");
        assertTrue(panel.values(ToolCatalog.byId("tree")).number("seed") != null);
        click("#bosque");
        assertEquals("spruce", panel.values(ToolCatalog.byId("tree")).choice("species"));
        assertEquals(30, panel.values(ToolCatalog.byId("tree")).number("height"));
    }

    @Test
    void savingAPresetAsksForANameAndSendsTheSaveCommand() {
        select("tree");
        click("Guardar preset");
        assertTrue(panel.typing());
        for (char character : "Mi_Bosque".toCharArray()) {
            panel.type(character);
        }
        panel.key(ToolsPanel.Key.ENTER, false, host);
        assertEquals("komaedit tree save mi_bosque oak -f", host.last());
        assertFalse(panel.typing());
    }

    @Test
    void secondaryActionsSendTheirOwnCommands() {
        select("tree");
        click("Al pincel");
        assertEquals("komaedit pincel tree oak", host.last());
        host.context = new ToolContext(true, Set.of(TreeCard.NODE), true, 1L, 0, false);
        draw();
        ui.zone("Al pincel").press();
        assertEquals("komaedit pincel tree oak", host.last(), "sin permiso del pincel no manda otra vez");
        assertTrue(host.status.contains(TreeCard.BRUSH_NODE));
    }

    @Test
    void theStampGalleryShowsEveryStampAndPicksOne() {
        select("stamp");
        assertEquals(20, ui.stamps.size());
        ui.zone("Cráter", "tools:stamp").press();
        assertEquals("crater", panel.values(ToolCatalog.byId("stamp")).choice("stamp"));
        panel.build(host);
        assertEquals("komaedit pincel heightmap crater 12 12 elevar natural", host.last());
    }

    @Test
    void theBiomeSearchFindsServerBiomes() {
        select("biome");
        click("plains");
        assertTrue(panel.typing());
        for (char character : "moon".toCharArray()) {
            panel.type(character);
        }
        panel.key(ToolsPanel.Key.ENTER, false, host);
        assertEquals("terralith:moonlight_grove", panel.values(ToolCatalog.byId("biome")).choice("biome"));
        panel.build(host);
        assertEquals("komaedit biome terralith:moonlight_grove", host.last());
    }

    @Test
    void symmetryFollowsTheServerAndTogglesWorldEdit() {
        host.info = new ToolsInfo(true, new int[0], List.of(), Map.of(), Map.of(),
                new ToolsInfo.Symmetry(true, "radial 8 y", 8, "x 0, y 64, z 0", false, false), null, List.of(), null);
        select("symmetry");
        assertEquals("radial", panel.values(ToolCatalog.byId("symmetry")).choice("mode"));
        assertEquals("Aplicar modo", panel.buildLabel(host));
        click("Reflejar WorldEdit y KomaEdit");
        assertEquals("komaedit symmetry worldedit on", host.last());
        click("Desactivar");
        assertEquals("komaedit symmetry off", host.last());
        click("Centro de la selección");
        assertEquals("komaedit symmetry center selection", host.last());
    }

    @Test
    void capabilitiesToggleRightAwayAndSendNumbersWhenLeftAlone() {
        host.info = new ToolsInfo(true, new int[0], List.of(), Map.of(), Map.of(), ToolsInfo.Symmetry.OFF,
                new ToolsInfo.Capabilities(BuildCapability.REPLACE.bit(), BuildCapability.REPLACE.bit()
                        | BuildCapability.REACH.bit(), true, true, false, false, 256, 4, 24, 10, 64, 4096, 100),
                List.of(), null);
        select("capabilities");
        click("Reemplazar");
        assertEquals("komaedit capability replace off", host.last());
        click("Extruir · sin permiso");
        assertEquals("komaedit capability replace off", host.last(), "sin permiso no manda nada");
        draw();
        ui.zoneWithKey("tools:number:reach").scroll.accept(1);
        ui.zoneWithKey("tools:number:reach").scroll.accept(1);
        panel.tick(host);
        assertEquals("komaedit capability replace off", host.last(), "aún no: se está tocando");
        host.now += ToolsPanel.OPTION_DELAY_MILLIS + 1;
        panel.tick(host);
        assertEquals("komaedit capability option reach 26", host.last());
    }

    @Test
    void theMaterialListShowsAndCopies() {
        host.info = new ToolsInfo(true, new int[]{4, 4, 4}, List.of(), Map.of(), Map.of(), ToolsInfo.Symmetry.OFF,
                null, List.of(), new ToolsInfo.Materials(64, 2, 70, 3, 1, List.of(
                new ToolsInfo.Materials.Line("minecraft:oak_planks", 66, 64),
                new ToolsInfo.Materials.Line("minecraft:torch", 4, 64)), 0, "oak_planks: 66\ntorch: 4"));
        select("analyze");
        click("Copiar lista");
        assertEquals("oak_planks: 66\ntorch: 4", host.copied.getLast());
        assertTrue(ui.has("oak_planks: 66 (1 pilas + 2)"));
    }

    @Test
    void undoAndRedoGoBackThroughTheSettings() {
        select("snow");
        ToolsPanel.Snapshot before = panel.snapshot();
        ui.zoneWithKey("tools:number:layers").scroll.accept(1);
        panel.track(before, "tools:layers", host.now);
        assertEquals(3, panel.values(ToolCatalog.byId("snow")).number("layers"));
        assertTrue(panel.undo(host));
        assertEquals(2, panel.values(ToolCatalog.byId("snow")).number("layers"));
        assertTrue(panel.redo(host));
        assertEquals(3, panel.values(ToolCatalog.byId("snow")).number("layers"));
    }

    @Test
    void theStateIsAskedOncePerSecond() {
        panel.tick(host);
        panel.tick(host);
        assertEquals(1, host.requests);
        host.now += ToolsPanel.REFRESH_MILLIS;
        panel.tick(host);
        assertEquals(2, host.requests);
    }

    @Test
    void theCommandBoxCopiesTheCommand() {
        select("gravity");
        ui.zones.stream().filter(zone -> zone.tooltip.startsWith("/komaedit gravity")).findFirst().orElseThrow()
                .press();
        assertEquals("/komaedit gravity", host.copied.getLast());
    }

    @Test
    void theJobStripConfirmsDiscardsAndCancels() {
        StudioLayout.Frame frame = StudioLayout.compute(960, 540, StudioLayout.DEFAULT_PANEL_WIDTH, 10);
        ProgressLine waiting = new ProgressLine("Tejado", ProgressLine.Phase.WAITING, 59_000, 90_000, 0, null);
        ui.reset();
        JobStrip.draw(ui, frame, waiting, 1_234, 0, host, "Construir", ToolsUi.Tone.PRIMARY, () -> { }, "");
        assertTrue(ui.has("1 234 cambios en vista previa · caduca en 59 s · Tejado"), ui.zones.toString());
        ui.zone("Confirmar").press();
        assertEquals("komaedit confirm", host.last());
        ui.zone("Descartar").press();
        assertEquals("komaedit cancel", host.last());

        ProgressLine placing = new ProgressLine("Nieve", ProgressLine.Phase.PLACING, 500, 1_000, 2_000_000_000L, null);
        ui.reset();
        boolean[] built = {false};
        JobStrip.draw(ui, frame, placing, 0, 0, host, "Construir", ToolsUi.Tone.PRIMARY, () -> built[0] = true, "");
        assertTrue(ui.has("Nieve · colocando 50 % · 250 bl/s · ~2 s"), ui.zones.toString());
        ui.zone("Cancelar").press();
        assertEquals("komaedit cancel", host.last());
        ui.zone("Construir").press();
        assertTrue(built[0], "mientras se coloca, Construir sigue a mano");
    }
}
