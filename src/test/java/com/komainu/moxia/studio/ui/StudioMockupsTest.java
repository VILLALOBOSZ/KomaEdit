package com.komainu.moxia.studio.ui;

import com.komainu.moxia.blueprint.BlueprintFixtures;
import com.komainu.moxia.blueprint.BlueprintRef;
import com.komainu.moxia.blueprint.BlueprintScope;
import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.blueprint.BlueprintThumbnail;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.net.JobStatusPayload;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maquetas PNG del Studio pintadas fuera del juego con el mismo código que el juego: la vista común
 * ({@link StudioView}), el reparto ({@code StudioLayout}), los controles ({@link StudioUi}) y un lienzo de imagen con
 * la fuente bitmap y los iconos de objeto del juego. Solo cambia el fondo (un mundo pintado a mano). Las de la carpeta
 * van con el tema de serie; en {@code temas/} están las mismas pantallas con cada tema, una comparativa del árbol y la
 * hoja de fichas. Solo se generan con {@code KOMAEDIT_STUDIO_MOCKUPS=<carpeta>}:
 *
 * <pre>KOMAEDIT_STUDIO_MOCKUPS=build/maquetas ./gradlew test --tests "*StudioMockupsTest"</pre>
 */
class StudioMockupsTest {
    /** Un caso: archivo, pantalla en píxeles de GUI, escala de GUI, idioma y cómo se prepara. */
    private record Scene(String file, int width, int height, int scale, String language, Consumer<StudioDriver> setup) {
    }

    static final ToolContext SELECTED = new ToolContext(true, ToolCatalog.permissionNodes(), true,
            10L * 6 * 12, 0, false);

    @Test
    void renderMockups() throws IOException {
        String target = System.getenv("KOMAEDIT_STUDIO_MOCKUPS");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "KOMAEDIT_STUDIO_MOCKUPS sin definir");
        assertTrue(ImageCanvas.font().loaded(), "la fuente del juego tiene que estar en el classpath");
        ChoiceThumbnails.setBlocking(true);
        Path folder = Path.of(target);
        Files.createDirectories(folder);
        List<Scene> scenes = new ArrayList<>();
        // 1080p con GUI 3: el reparto normal.
        scenes.add(new Scene("1080p_gui3_herramientas_es.png", 640, 360, 3, "es_es", driver -> tools(driver, null)));
        scenes.add(new Scene("1080p_gui3_arbol_es.png", 640, 360, 3, "es_es", driver -> tools(driver, "tree")));
        scenes.add(new Scene("1080p_gui3_tejado_es.png", 640, 360, 3, "es_es", driver -> tools(driver, "roof")));
        scenes.add(new Scene("1080p_gui3_navegacion_es.png", 640, 360, 3, "es_es", driver -> {
            // El ratón sobre los iconos: sale la lista con el nombre y la tecla de cada etapa.
            tools(driver, "roof");
            driver.render();
            var icon = driver.view.frame().navItem(StudioStage.MODIFIERS.ordinal());
            driver.mouseX = icon.centerX();
            driver.mouseY = icon.centerY();
        }));
        // 1080p con GUI 2: sitio para los nombres de las etapas.
        scenes.add(new Scene("1080p_gui2_tejado_es.png", 960, 540, 2, "es_es", driver -> tools(driver, "roof")));
        scenes.add(new Scene("1080p_gui2_arbol_en.png", 960, 540, 2, "en_us", driver -> tools(driver, "tree")));
        scenes.add(new Scene("1080p_gui3_rio_en.png", 640, 360, 3, "en_us", driver -> {
            path(driver, 4);
            tools(driver, "river");
        }));
        scenes.add(new Scene("1080p_gui3_trayectoria_es.png", 640, 360, 3, "es_es", driver -> {
            path(driver, 4);
            driver.view.selectStage(StudioStage.PATH, driver.host);
        }));
        scenes.add(new Scene("1080p_gui3_planos_es.png", 640, 360, 3, "es_es", StudioMockupsTest::blueprints));
        scenes.add(new Scene("1080p_gui3_bloques_es.png", 640, 360, 3, "es_es", driver -> {
            tools(driver, "roof");
            driver.host.lookedAt = "minecraft:stone_bricks";
            driver.host.hotbar = List.of("minecraft:spruce_planks", "minecraft:spruce_log", "minecraft:cobblestone",
                    "", "minecraft:glass_pane", "minecraft:lantern", "", "minecraft:oak_stairs", "");
            driver.host.preferences.pushBlockRecent("minecraft:dark_oak_planks");
            driver.host.preferences.pushBlockRecent("minecraft:deepslate_tiles");
            driver.click("Tablones de abeto");
        }));
        scenes.add(new Scene("1080p_gui3_paleta_es.png", 640, 360, 3, "es_es", driver -> {
            tools(driver, "tree");
            driver.key(StudioKey.letter('K', true, false));
            driver.type("teja");
        }));
        scenes.add(new Scene("1080p_gui3_historial_es.png", 640, 360, 3, "es_es", driver -> {
            tools(driver, "roof");
            driver.host.history = new StudioHost.WorldHistory(List.of(
                    new StudioHost.WorldHistory.Entry("Tejado a cuatro aguas", 1_204),
                    new StudioHost.WorldHistory.Entry("Árbol", 412),
                    new StudioHost.WorldHistory.Entry("WorldEdit", 3_820),
                    new StudioHost.WorldHistory.Entry("Río o cañón", 9_771),
                    new StudioHost.WorldHistory.Entry("Hiedra", 2_040)), 1);
            driver.click("Cambios en el mundo: deshacer o rehacer hasta un punto (Ctrl+Z, Ctrl+Y)");
        }));
        scenes.add(new Scene("1080p_gui3_vista_previa_es.png", 640, 360, 3, "es_es", driver -> {
            tools(driver, "roof");
            driver.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.LIVE);
            driver.host.job = JobStatusPayload.of(new ProgressLine("Tejado a dos aguas", ProgressLine.Phase.WAITING,
                    74_000, 90_000, 0, null), 3_412);
        }));
        scenes.add(new Scene("1080p_gui3_colocando_en.png", 640, 360, 3, "en_us", driver -> {
            tools(driver, "snow");
            driver.host.job = JobStatusPayload.of(new ProgressLine("Snow (3 layers)", ProgressLine.Phase.PLACING,
                    41_000, 96_000, 3_000_000_000L, null), 0);
        }));
        scenes.add(new Scene("1080p_gui3_fantasma_es.png", 640, 360, 3, "es_es", driver -> {
            tools(driver, "tree");
            driver.host.ghost = GhostFooterTest.ghost("Árbol roble", "giro 90° · 7×12×7",
                    "120, 64, -40 · (+3, 0, -2)", "", new int[5]);
        }));
        scenes.add(new Scene("1080p_gui3_fantasma_rechazado_es.png", 640, 360, 3, "es_es", driver -> {
            tools(driver, "roof");
            driver.host.ghost = GhostFooterTest.ghost("Tejado a dos aguas", "giro 180° volteado · 12×6×9",
                    "88, 72, 15", "Se sale de la altura del mundo", new int[5]);
        }));
        scenes.add(new Scene("1080p_gui2_fantasma_en.png", 960, 540, 2, "en_us", driver -> {
            tools(driver, "tree");
            driver.host.ghost = GhostFooterTest.ghost("Oak tree", "turned 90° · 7×12×7",
                    "120, 64, -40 · (+3, 0, -2)", "", new int[5]);
        }));
        scenes.add(new Scene("854_gui2_fantasma_es.png", 427, 240, 2, "es_es", driver -> {
            tools(driver, "tree");
            driver.host.ghost = GhostFooterTest.ghost("Árbol roble", "giro 90° · 7×12×7",
                    "120, 64, -40 · (+3, 0, -2)", "", new int[5]);
        }));
        scenes.add(new Scene("1080p_gui3_figuras_es.png", 640, 360, 3, "es_es", driver -> {
            path(driver, 3);
            driver.host.previewBlocks = 12_300;
            driver.view.figure().selectShape(driver.host, PathShape.BRAID);
            driver.view.selectStage(StudioStage.FIGURE, driver.host);
        }));
        scenes.add(new Scene("1080p_gui3_figuras_lista_es.png", 640, 360, 3, "es_es", driver -> {
            path(driver, 3);
            driver.view.selectStage(StudioStage.FIGURE, driver.host);
            driver.view.figure().setBrowsing(true);
        }));
        scenes.add(new Scene("1080p_gui3_mas_ajustes_es.png", 640, 360, 3, "es_es", driver -> {
            tools(driver, "tree");
            driver.host.preferences.setExpanded("tools:more:tree", true);
            for (int step = 0; step < 12 && !driver.has("Curvatura"); step++) {
                // Sobre las etiquetas la rueda desplaza; sobre un valor, lo cambiaría.
                driver.view.mouseScrolled(driver.view.frame().innerLeft() + 4, 200, -1, driver.host);
            }
            int[] row = driver.where("Curvatura");
            driver.mouseX = row[0];
            driver.mouseY = row[1];
        }));
        scenes.add(new Scene("1080p_gui3_material_es.png", 640, 360, 3, "es_es", driver -> {
            path(driver, 3);
            driver.host.lookedAt = "minecraft:mossy_cobblestone";
            driver.view.selectStage(StudioStage.MATERIAL, driver.host);
        }));
        scenes.add(new Scene("1080p_gui3_modificadores_en.png", 640, 360, 3, "en_us", driver -> {
            path(driver, 3);
            driver.view.selectStage(StudioStage.MODIFIERS, driver.host);
        }));
        scenes.add(new Scene("1080p_gui3_color_es.png", 640, 360, 3, "es_es", driver ->
                driver.view.selectStage(StudioStage.COLOR, driver.host)));
        // La chuleta (?): atajos y, abajo, el selector de tema.
        scenes.add(new Scene("1080p_gui3_chuleta_tema_es.png", 640, 360, 3, "es_es", driver -> {
            tools(driver, "tree");
            driver.key(StudioKey.of(StudioKey.Code.F1));
            int[] chip = driver.where("Bosque");
            driver.mouseX = chip[0];
            driver.mouseY = chip[1];
        }));
        scenes.add(new Scene("854_gui2_chuleta_tema_es.png", 427, 240, 2, "es_es", driver -> {
            tools(driver, "tree");
            driver.key(StudioKey.of(StudioKey.Code.F1));
        }));
        // Lo que pidió el autor tras probar la 1.7.0: presets que dejan el árbol entero, colocar con el ratón, la
        // vista previa en vivo con el fantasma y el pincel que sigue al Studio.
        scenes.add(new Scene("1080p_gui3_arbol_presets_es.png", 640, 360, 3, "es_es", driver -> {
            tools(driver, "tree");
            driver.click("Pelado");
            driver.click("Joven");
            driver.host.status = "";
        }));
        scenes.add(new Scene("1080p_gui3_arbol_clic_es.png", 640, 360, 3, "es_es", driver -> {
            tools(driver, "tree");
            driver.host.clickedSpot = new int[]{124, 64, -38};
        }));
        scenes.add(new Scene("1080p_gui3_fantasma_vivo_es.png", 640, 360, 3, "es_es", driver -> {
            tools(driver, "tree");
            driver.host.ghost = GhostFooterTest.ghost("Árbol roble", "giro 0° · 7×19×7",
                    "124, 65, -38 · (+2, 0, -1)", "", new int[5]);
            int[] title = driver.where("Árbol roble · giro 0° · 7×19×7");
            driver.mouseX = title[0];
            driver.mouseY = title[1];
        }));
        scenes.add(new Scene("1080p_gui3_sello_pincel_es.png", 640, 360, 3, "es_es", driver -> {
            tools(driver, "stamp");
            driver.host.preferences.setExpanded("tools:more:stamp", true);
            for (int step = 0; step < 12 && !driver.has("Vista previa en cada clic"); step++) {
                driver.view.mouseScrolled(driver.view.frame().innerLeft() + 4, 200, -1, driver.host);
            }
            driver.view.status(driver.host.tr("gui.komaedit.tools.status.brush_synced",
                    driver.host.tr("gui.komaedit.tools.stamp")), driver.host.now());
        }));
        // 1080p con GUI 4 (la automática): compacto.
        scenes.add(new Scene("1080p_gui4_arbol_es.png", 480, 270, 4, "es_es", driver -> tools(driver, "tree")));
        scenes.add(new Scene("1080p_gui4_tejado_en.png", 480, 270, 4, "en_us", driver -> tools(driver, "roof")));
        // 854×480 con GUI 2: compacto.
        scenes.add(new Scene("854_gui2_herramientas_es.png", 427, 240, 2, "es_es", driver -> tools(driver, null)));
        scenes.add(new Scene("854_gui2_arbol_es.png", 427, 240, 2, "es_es", driver -> tools(driver, "tree")));
        scenes.add(new Scene("854_gui2_tejado_en.png", 427, 240, 2, "en_us", driver -> tools(driver, "roof")));
        scenes.add(new Scene("854_gui2_rio_es.png", 427, 240, 2, "es_es", driver -> {
            path(driver, 4);
            tools(driver, "river");
        }));
        scenes.add(new Scene("854_gui2_planos_es.png", 427, 240, 2, "es_es", StudioMockupsTest::blueprints));
        for (Scene scene : scenes) {
            ImageIO.write(paint(scene, StudioPalette.DEFAULT, null), "png", folder.resolve(scene.file()).toFile());
        }
        try {
            themes(folder.resolve("temas"));
        } finally {
            StudioTheme.use(StudioPalette.DEFAULT);
        }
    }

    private static BufferedImage paint(Scene scene, StudioPalette palette, Consumer<Rect> panel) {
        StudioDriver driver = new StudioDriver(scene.language(), scene.width(), scene.height());
        driver.host.context = SELECTED;
        driver.host.info = info();
        if (palette != StudioPalette.DEFAULT) {
            driver.host.preferences.setTheme(palette.id());
        }
        scene.setup().accept(driver);
        BufferedImage image = driver.paint(scene.scale(), ui -> world(ui, scene.width(), scene.height()));
        if (panel != null) {
            panel.accept(driver.view.frame().panel());
        }
        return image;
    }

    /**
     * Las pantallas que más se usan (Herramientas, Árbol, Tejado y Planos, a 1080p con GUI 3 y a 854×480 con GUI 2)
     * con cada tema, el árbol de los cuatro lado a lado con un trozo de mundo y la hoja de fichas.
     */
    private static void themes(Path folder) throws IOException {
        List<Scene> scenes = List.of(
                new Scene("1080p_gui3_herramientas_es.png", 640, 360, 3, "es_es", driver -> tools(driver, null)),
                new Scene("1080p_gui3_arbol_es.png", 640, 360, 3, "es_es", driver -> tools(driver, "tree")),
                new Scene("1080p_gui3_tejado_es.png", 640, 360, 3, "es_es", driver -> tools(driver, "roof")),
                new Scene("1080p_gui3_planos_es.png", 640, 360, 3, "es_es", StudioMockupsTest::blueprints),
                new Scene("854_gui2_herramientas_es.png", 427, 240, 2, "es_es", driver -> tools(driver, null)),
                new Scene("854_gui2_arbol_es.png", 427, 240, 2, "es_es", driver -> tools(driver, "tree")),
                new Scene("854_gui2_tejado_es.png", 427, 240, 2, "es_es", driver -> tools(driver, "roof")),
                new Scene("854_gui2_planos_es.png", 427, 240, 2, "es_es", StudioMockupsTest::blueprints));
        List<BufferedImage> trees = new ArrayList<>();
        for (StudioPalette palette : StudioPalette.values()) {
            Path themeFolder = folder.resolve(palette.id());
            Files.createDirectories(themeFolder);
            for (Scene scene : scenes) {
                Rect[] panel = new Rect[1];
                BufferedImage image = paint(scene, palette, rect -> panel[0] = rect);
                ImageIO.write(image, "png", themeFolder.resolve(scene.file()).toFile());
                if (scene.file().equals("1080p_gui3_arbol_es.png")) {
                    // El panel con un poco de mundo a su izquierda: el tema tiene que pegar con lo que hay al lado.
                    int left = Math.max(0, panel[0].x() - 40) * scene.scale();
                    trees.add(image.getSubimage(left, 0, image.getWidth() - left, image.getHeight()));
                }
            }
        }
        ImageIO.write(sideBySide(trees, 12), "png", folder.resolve("comparativa_arbol.png").toFile());
        ImageIO.write(tokenSheet(), "png", folder.resolve("fichas.png").toFile());
    }

    private static BufferedImage sideBySide(List<BufferedImage> images, int gap) {
        int width = gap * (images.size() - 1);
        int height = 0;
        for (BufferedImage image : images) {
            width += image.getWidth();
            height = Math.max(height, image.getHeight());
        }
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        int x = 0;
        for (BufferedImage image : images) {
            graphics.drawImage(image, x, 0, null);
            x += image.getWidth() + gap;
        }
        graphics.dispose();
        return sheet;
    }

    /**
     * La hoja de fichas: una columna por tema, sobre su propio fondo, con cada ficha (muestra, nombre y valor) y
     * debajo los controles en cada estado: principal, normal, bajo el ratón, elegido, desactivado, peligro y aviso,
     * pastillas, interruptor, miniaturas, filas de lista y los colores de estado como texto.
     */
    static BufferedImage tokenSheet() {
        int column = 220;
        int line = 11;
        int scale = 2;
        StudioPalette[] palettes = StudioPalette.values();
        int tokens = StudioTheme.Token.values().length;
        int height = 26 + tokens * line + 214;
        BufferedImage image = new BufferedImage(column * palettes.length * scale, height * scale,
                BufferedImage.TYPE_INT_ARGB);
        ImageCanvas canvas = new ImageCanvas(image, scale);
        FakeStudioHost host = new FakeStudioHost("es_es");
        for (int index = 0; index < palettes.length; index++) {
            StudioPalette palette = palettes[index];
            StudioTheme.use(palette);
            int x = index * column;
            int inner = column - 16;
            int third = (inner - 8) / 3;
            int controls = 26 + tokens * line + 18;
            Rect hovered = new Rect(x + 12 + third, controls + 22, third, 18);
            StudioUi ui = new StudioUi(canvas);
            ui.begin(hovered.centerX(), hovered.centerY(), true);
            ui.fill(new Rect(x, 0, column, height), StudioTheme.PANEL);
            ui.fill(new Rect(x, 0, column, 20), StudioTheme.SURFACE);
            ui.fill(new Rect(x, 0, 2, 20), StudioTheme.ACCENT);
            ui.bold(host.tr(palette.translationKey()), x + 8, 6, inner, StudioTheme.TEXT_STRONG);
            ui.rightAligned(palette == StudioPalette.DEFAULT ? "de serie" : palette.id(), x + column - 8, 6,
                    StudioTheme.MUTED);
            int y = 26;
            for (StudioTheme.Token token : StudioTheme.Token.values()) {
                int color = palette.color(token);
                Rect swatch = new Rect(x + 8, y, 24, line - 2);
                ui.fill(swatch, color);
                ui.border(swatch, StudioTheme.DIVIDER);
                ui.label(token.name(), x + 38, y, inner - 90, StudioTheme.TEXT);
                String value = (color >>> 24) == 0xFF ? String.format("#%06X", color & 0xFFFFFF)
                        : String.format("#%08X", color);
                ui.rightAligned(value, x + column - 8, y, StudioTheme.MUTED);
                y += line;
            }
            y += 6;
            ui.label("CONTROLES", x + 8, y, StudioTheme.MUTED);
            y += 12;
            ui.button(new Rect(x + 8, y, inner, 18), "Construir", StudioUi.Tone.PRIMARY);
            y += 22;
            ui.button(new Rect(x + 8, y, third, 18), "Normal", StudioUi.Tone.SECONDARY);
            ui.button(hovered, "Encima", StudioUi.Tone.SECONDARY);
            ui.button(new Rect(x + 16 + third * 2, y, third, 18), "Elegido", StudioUi.Tone.SELECTED);
            y += 22;
            ui.button(new Rect(x + 8, y, third, 18), "Apagado", StudioUi.Tone.DISABLED);
            ui.button(new Rect(x + 12 + third, y, third, 18), "Borrar", StudioUi.Tone.DANGER);
            ui.button(new Rect(x + 16 + third * 2, y, third, 18), "Grande", StudioUi.Tone.WARNING);
            y += 24;
            ui.chip(new Rect(x + 8, y, 44, 14), "Joven", true, StudioTheme.TEXT_SECONDARY);
            ui.chip(new Rect(x + 56, y, 40, 14), "Viejo", false, StudioTheme.TEXT_SECONDARY);
            ui.chip(new Rect(x + 100, y, 52, 14), "#bosque", false, StudioTheme.FAVORITE);
            ui.toggle(new Rect(x + 156, y - 2, inner - 148, 18), "", true);
            y += 20;
            ui.tile(new Rect(x + 8, y, 48, 54), null, StudioIcons.Icon.TREE, List.of("Roble"), true);
            ui.tile(new Rect(x + 60, y, 48, 54), null, StudioIcons.Icon.ROCK, List.of("Roca"), false);
            ui.listItem(new Rect(x + 112, y, inner - 104, 26), "Elegida", "con su marca", true, 0);
            ui.listItem(new Rect(x + 112, y + 28, inner - 104, 26), "Otra", "sin marcar", false, 0);
            y += 60;
            String[] names = {"Hecho", "Aviso", "Peligro", "Nota", "Apagado", "Acento"};
            int[] colors = {StudioTheme.SUCCESS, StudioTheme.WARNING, StudioTheme.DANGER, StudioTheme.MUTED,
                    StudioTheme.DISABLED, StudioTheme.ACCENT_TEXT};
            for (int state = 0; state < names.length; state++) {
                ui.label(names[state], x + 8 + state % 3 * (inner / 3), y + state / 3 * 11, colors[state]);
            }
        }
        StudioTheme.use(StudioPalette.DEFAULT);
        return image;
    }

    static ToolsInfo info() {
        return new ToolsInfo(true, new int[]{10, 6, 12}, List.of(), Map.of("bosque", "spruce 30 -d 1.4"), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of("minecraft:plains", "minecraft:cherry_grove"), null);
    }

    static void tools(StudioDriver driver, String tool) {
        driver.view.selectStage(StudioStage.TOOLS, driver.host);
        if (tool != null) {
            driver.host.preferences.pushToolRecent("text");
            driver.host.preferences.pushToolRecent("river");
            driver.host.preferences.pushToolRecent("roof");
            driver.view.tools().select(ToolCatalog.byId(tool), driver.host);
        }
        driver.host.status = "";
    }

    static void path(StudioDriver driver, int points) {
        driver.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, points, false);
        for (int index = 0; index < points; index++) {
            driver.host.points.add(new int[]{120 + index * 9, 64 + index % 2, -40 + index * 7});
        }
    }

    /** La biblioteca de las maquetas, con «casita» elegida (también la usan otras pruebas). */
    static void blueprints(StudioDriver driver) {
        FakeStudioHost.FakeBlueprints catalog = driver.host.blueprints;
        add(catalog, "casita", BlueprintFixtures.house(), true, "Villalobos", 342, List.of("casas", "aldea"));
        add(catalog, "pozo", BlueprintFixtures.well(), true, "Villalobos", 74, List.of("aldea", "agua"));
        add(catalog, "roca_musgo", BlueprintFixtures.rock(3), false, "Varo", 96, List.of("rocas", "musgo"));
        add(catalog, "arbusto", BlueprintFixtures.bush(), false, "Varo", 41, List.of("arbustos"));
        add(catalog, "ruina_patio", BlueprintFixtures.ruin(7), false, "Varo", 210, List.of("ruinas"));
        add(catalog, "abedul", BlueprintFixtures.tree(), false, "Varo", 155, List.of("arboles"));
        add(catalog, "roca_gris", BlueprintFixtures.rock(11), false, "Varo", 88, List.of("rocas"));
        add(catalog, "plaza", BlueprintFixtures.plaza(), true, "Villalobos", 512, List.of("aldea"));
        driver.view.selectStage(StudioStage.BLUEPRINTS, driver.host);
        driver.view.blueprints().select(BlueprintRef.display("casita", BlueprintScope.SHARED));
        driver.host.status = "";
    }

    private static void add(FakeStudioHost.FakeBlueprints catalog, String name, BlueprintThumbnail.Grid grid,
                            boolean shared, String author, int blocks, List<String> tags) {
        String reference = BlueprintRef.display(name, shared ? BlueprintScope.SHARED : BlueprintScope.PLAYER);
        catalog.list.add(new BlueprintSummary(reference, name, shared, !shared, author, 1_700_000_000_000L,
                grid.sizeX(), grid.sizeY(), grid.sizeZ(), blocks, tags, name));
        BufferedImage image = BlueprintThumbnail.render(grid, BlueprintThumbnail.SIZE);
        int[] pixels = image.getRGB(0, 0, BlueprintThumbnail.SIZE, BlueprintThumbnail.SIZE, null, 0,
                BlueprintThumbnail.SIZE);
        catalog.thumbnails.put(name, StudioImage.of("blueprint/" + name, BlueprintThumbnail.SIZE,
                BlueprintThumbnail.SIZE, () -> pixels));
    }

    /** Un mundo de fondo: cielo, colinas y una casa de bloques, para ver que el mundo sigue a la izquierda. */
    static void world(StudioUi ui, int width, int height) {
        int horizon = height * 11 / 20;
        for (int y = 0; y < height; y++) {
            int argb;
            if (y < horizon) {
                argb = mix(0xFF6FA8F0, 0xFFBFDDFB, (double) y / horizon);
            } else {
                argb = mix(0xFF5E9E3C, 0xFF3F6E2A, (double) (y - horizon) / (height - horizon));
            }
            ui.fill(new Rect(0, y, width, 1), argb);
        }
        for (int x = 0; x < width; x += 4) {
            int hill = (int) (Math.sin(x / 37.0) * 9 + Math.sin(x / 13.0) * 3);
            ui.fill(new Rect(x, horizon - 12 + hill, 4, 12 - hill), 0xFF4F8A34);
        }
        int houseX = width / 5;
        int houseY = horizon - 30;
        ui.fill(new Rect(houseX, houseY, 48, 32), 0xFF9C7A4E);
        for (int step = 0; step < 6; step++) {
            ui.fill(new Rect(houseX - 4 + step * 4, houseY - 4 - step * 4, 56 - step * 8, 4), 0xFF5A3E26);
        }
        ui.fill(new Rect(houseX + 18, houseY + 14, 12, 18), 0xFF4A3220);
        // La mira, en el centro de la pantalla.
        ui.fill(new Rect(width / 2 - 4, height / 2, 9, 1), 0xCCFFFFFF);
        ui.fill(new Rect(width / 2, height / 2 - 4, 1, 9), 0xCCFFFFFF);
    }

    private static int mix(int a, int b, double t) {
        int red = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int green = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int blue = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
