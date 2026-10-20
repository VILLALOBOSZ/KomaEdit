package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.capability.BuildCapability;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.layout.StudioLayout;
import com.komainu.moxia.studio.tools.TerrainCards;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.TreeCard;
import com.komainu.moxia.studio.tools.UtilityCards;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maquetas PNG de la etapa Herramientas del Studio, pintadas fuera del juego con el mismo reparto
 * ({@link StudioLayout}), la misma etapa ({@link ToolsPanel}), los colores de {@code StudioTheme} y la fuente bitmap
 * del juego. Solo se generan con {@code KOMAEDIT_STUDIO_MOCKUPS=<carpeta>}:
 *
 * <pre>KOMAEDIT_STUDIO_MOCKUPS=build/maquetas ./gradlew test --tests "*StudioMockupsTest"</pre>
 */
class StudioMockupsTest {
    private static final String[] STAGES_ES = {"Trayectoria", "Figuras", "Estructuras", "Modificadores", "Material",
            "Envejecer", "Instancias", "Recetas", "Color", "Herramientas"};
    private static final String[] STAGES_EN = {"Path", "Figures", "Structures", "Modifiers", "Material", "Weather",
            "Instances", "Recipes", "Color", "Tools"};
    private static final MinecraftFont FONT = new MinecraftFont();

    /** Un caso: tamaño de pantalla, escala de GUI, idioma, cómo se prepara y dónde se mira. */
    private record Scene(String file, int width, int height, int scale, String language, int panelWidth,
                         Consumer<Setup> setup) {
    }

    /** Lo que cada caso puede tocar antes de pintar. */
    private static final class Setup {
        final ToolsPanel panel = new ToolsPanel();
        final TestHost host;
        int scroll;
        ProgressLine job;
        long changes;
        boolean scrollToCard;

        Setup(String language) {
            host = new TestHost(language);
            host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 10 * 6 * 12, 0, false);
            host.info = new ToolsInfo(true, new int[]{10, 6, 12}, List.of(), Map.of("bosque", "spruce 30 -d 1.4",
                    "sauce_grande", "willow 22 -w 1.6"), Map.of(), ToolsInfo.Symmetry.OFF, capabilities(true),
                    List.of("minecraft:cherry_grove", "minecraft:plains", "minecraft:snowy_plains"), null);
            host.preferences.pushToolRecent("text");
            host.preferences.pushToolRecent("roof");
            host.preferences.pushToolRecent("tree");
            host.preferences.toggleToolFavorite("roof");
        }

        void select(String id) {
            panel.select(ToolCatalog.byId(id), host);
            scrollToCard = true;
        }
    }

    private static ToolsInfo.Capabilities capabilities(boolean creative) {
        int permitted = BuildCapability.REPLACE.bit() | BuildCapability.TWEAK.bit() | BuildCapability.ANGEL.bit()
                | BuildCapability.FAST_PLACE.bit() | BuildCapability.EXTRUDE.bit();
        int enabled = BuildCapability.REPLACE.bit() | BuildCapability.FAST_PLACE.bit();
        return new ToolsInfo.Capabilities(enabled, permitted, creative, true, true, false, 256, 4, 24, 15, 64, 4096,
                100);
    }

    @Test
    void renderMockups() throws IOException {
        String target = System.getenv("KOMAEDIT_STUDIO_MOCKUPS");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "KOMAEDIT_STUDIO_MOCKUPS sin definir");
        assertTrue(FONT.loaded(), "la fuente del juego tiene que estar en el classpath");
        Path folder = Path.of(target);
        Files.createDirectories(folder);
        List<Scene> scenes = List.of(
                new Scene("01_herramientas_lista.png", 960, 540, 2, "es_es", 350, setup -> {
                }),
                new Scene("02_arbol.png", 960, 540, 2, "es_es", 350, setup -> setup.select("tree")),
                new Scene("03_arbol_ajustes.png", 960, 540, 2, "es_es", 350, setup -> {
                    setup.select("tree");
                    setup.scroll = 330;
                    setup.scrollToCard = false;
                }),
                new Scene("04_tejado_sin_seleccion.png", 960, 540, 2, "es_es", 350, setup -> {
                    setup.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0, 0, false);
                    setup.host.info = new ToolsInfo(true, new int[0], List.of(), Map.of(), Map.of(),
                            ToolsInfo.Symmetry.OFF, null, List.of(), null);
                    setup.select("roof");
                }),
                new Scene("05_sello_galeria.png", 960, 540, 2, "es_es", 350, setup -> setup.select("stamp")),
                new Scene("06_materiales.png", 960, 540, 2, "es_es", 350, setup -> {
                    setup.host.info = new ToolsInfo(true, new int[]{10, 6, 12}, List.of(), Map.of(), Map.of(),
                            ToolsInfo.Symmetry.OFF, null, List.of(), materials());
                    setup.select("analyze");
                }),
                new Scene("07_simetria.png", 960, 540, 2, "es_es", 350, setup -> {
                    setup.host.info = new ToolsInfo(true, new int[0], List.of(), Map.of(), Map.of(),
                            new ToolsInfo.Symmetry(true, "radial 8 y", 8, "x 120, y 64, z -40|-39", true, false),
                            null, List.of(), null);
                    setup.select("symmetry");
                }),
                new Scene("08_capacidades_supervivencia.png", 960, 540, 2, "es_es", 350, setup -> {
                    setup.host.info = new ToolsInfo(true, new int[0], List.of(), Map.of(), Map.of(),
                            ToolsInfo.Symmetry.OFF, capabilities(false), List.of(), null);
                    setup.select("capabilities");
                }),
                new Scene("09_sin_permiso.png", 960, 540, 2, "es_es", 350, setup -> {
                    setup.host.context = new ToolContext(true, Set.of(TreeCard.NODE, TerrainCards.RIVER_NODE,
                            UtilityCards.TEXT_NODE, UtilityCards.SNOW_NODE), true, 720, 0, false);
                    setup.select("erosion");
                }),
                new Scene("10_pie_vista_previa.png", 960, 540, 2, "es_es", 350, setup -> {
                    setup.select("roof");
                    setup.host.preferences.setToolPreview(true);
                    setup.job = new ProgressLine("Tejado a dos aguas", ProgressLine.Phase.WAITING, 74_000, 90_000, 0,
                            null);
                    setup.changes = 3_412;
                }),
                new Scene("11_pie_colocando.png", 960, 540, 2, "es_es", 350, setup -> {
                    setup.select("snow");
                    setup.job = new ProgressLine("Nieve (3 capas)", ProgressLine.Phase.PLACING, 41_000, 96_000,
                            3_000_000_000L, null);
                }),
                new Scene("12_gui4_1080p.png", 480, 270, 4, "es_es", 10_000, setup -> setup.select("tree")),
                new Scene("13_854x480_gui2.png", 427, 240, 2, "es_es", 0, setup -> setup.select("drain")),
                new Scene("14_tools_english.png", 960, 540, 2, "en_us", 350, setup -> setup.select("river")),
                new Scene("15_texto.png", 960, 540, 2, "es_es", 350, setup -> setup.select("text")),
                new Scene("16_bioma.png", 960, 540, 2, "es_es", 350, setup -> setup.select("biome"))
        );
        for (Scene scene : scenes) {
            ImageIO.write(render(scene), "png", folder.resolve(scene.file()).toFile());
        }
        // Cada tarjeta entera, sin recortar, para revisarlas todas.
        for (ToolCard card : ToolCatalog.ALL) {
            ImageIO.write(strip(card, "es_es"), "png", folder.resolve("tira_" + card.id() + ".png").toFile());
        }
    }

    private static ToolsInfo.Materials materials() {
        return new ToolsInfo.Materials(4_212, 9, 4_380, 71, 3, List.of(
                new ToolsInfo.Materials.Line("minecraft:spruce_planks", 1_197, 64),
                new ToolsInfo.Materials.Line("minecraft:stone_bricks", 1_024, 64),
                new ToolsInfo.Materials.Line("minecraft:deepslate_tiles", 845, 64),
                new ToolsInfo.Materials.Line("minecraft:spruce_log", 412, 64),
                new ToolsInfo.Materials.Line("minecraft:glass_pane", 220, 64),
                new ToolsInfo.Materials.Line("minecraft:lantern", 36, 64),
                new ToolsInfo.Materials.Line("minecraft:oak_door", 4, 64),
                new ToolsInfo.Materials.Line("minecraft:white_bed", 2, 1)), 1, "spruce_planks: 1197 …");
    }

    private static BufferedImage render(Scene scene) {
        Setup setup = new Setup(scene.language());
        scene.setup().accept(setup);
        int width = scene.width();
        int height = scene.height();
        BufferedImage image = new BufferedImage(width * scene.scale(), height * scene.scale(),
                BufferedImage.TYPE_INT_ARGB);
        FakeToolsUi ui = new FakeToolsUi(image, scene.scale(), FONT);
        world(ui, width, height);
        StudioLayout.Frame frame = StudioLayout.compute(width, height, scene.panelWidth(), 10);
        if (setup.scrollToCard) {
            // Como si el jugador bajara hasta la tarjeta: el título de la tarjeta queda arriba del contenido.
            setup.scroll = cardTop(setup, frame);
        }
        ui.fill(frame.panel(), FakeToolsUi.PANEL);
        toolbar(ui, frame);
        navigation(ui, frame, scene.language().equals("en_us") ? STAGES_EN : STAGES_ES);
        header(ui, frame, setup.host);
        content(ui, frame, setup);
        footer(ui, frame, setup);
        ui.fill(new Rect(frame.panel().x(), 0, 1, height), FakeToolsUi.DIVIDER);
        return image;
    }

    /** Dónde empieza la tarjeta elegida dentro del contenido (para «bajar» hasta ella). */
    private static int cardTop(Setup setup, StudioLayout.Frame frame) {
        FakeToolsUi probe = new FakeToolsUi(FONT);
        Column column = new Column(frame.innerLeft(), 0, frame.innerWidth());
        setup.panel.draw(probe, column, setup.host);
        int listBottom = probe.zones.stream()
                .filter(zone -> zone.tooltip.contains(setup.host.tr("gui.komaedit.help.favorite_star")))
                .mapToInt(zone -> zone.bounds.bottom())
                .max().orElse(0);
        return Math.max(0, listBottom - 6);
    }

    private static void world(FakeToolsUi ui, int width, int height) {
        int horizon = height * 11 / 20;
        for (int y = 0; y < height; y++) {
            int argb;
            if (y < horizon) {
                double t = (double) y / horizon;
                argb = mix(0xFF6FA8F0, 0xFFBFDDFB, t);
            } else {
                double t = (double) (y - horizon) / (height - horizon);
                argb = mix(0xFF5E9E3C, 0xFF3F6E2A, t);
            }
            ui.fill(new Rect(0, y, width, 1), argb);
        }
        // Unas colinas y una casa de bloques, para que se vea que el mundo sigue ahí.
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
    }

    private static int mix(int a, int b, double t) {
        int red = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int green = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int blue = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void toolbar(FakeToolsUi ui, StudioLayout.Frame frame) {
        Rect bar = frame.toolbar();
        ui.fill(bar, FakeToolsUi.SURFACE);
        ui.fill(new Rect(bar.x(), bar.bottom() - 1, bar.width(), 1), FakeToolsUi.DIVIDER);
        ui.fill(new Rect(bar.x(), bar.y(), 3, bar.height()), FakeToolsUi.ACCENT);
        ui.text("KomaEdit", bar.x() + 11, bar.centerY() - 4, FakeToolsUi.TEXT, true);
        String[] icons = {"close", "help", "hologram", "redo", "undo"};
        for (int index = 0; index < icons.length; index++) {
            Rect button = frame.toolButton(index);
            ui.fill(button, index == 2 ? FakeToolsUi.CARD_ACTIVE : FakeToolsUi.SURFACE);
            if (index == 2) {
                ui.fill(new Rect(button.x(), button.bottom() - 2, button.width(), 2), FakeToolsUi.ACCENT);
            }
            MockupIcons.draw(ui, icons[index], button.centerX() - 6, button.centerY() - 6,
                    index == 2 ? FakeToolsUi.ACCENT : index == 3 ? FakeToolsUi.DISABLED : FakeToolsUi.TEXT_SECONDARY);
        }
    }

    private static void navigation(FakeToolsUi ui, StudioLayout.Frame frame, String[] names) {
        Rect nav = frame.nav();
        ui.fill(nav, FakeToolsUi.SURFACE);
        ui.fill(new Rect(nav.right() - 1, nav.y(), 1, nav.height()), FakeToolsUi.DIVIDER);
        String[] icons = {"path", "figure", "structures", "modifiers", "material", "weather", "instances", "recipes",
                "color", "tools"};
        boolean labels = frame.navMode() == StudioLayout.NavMode.LABELS;
        for (int index = 0; index < names.length; index++) {
            Rect item = frame.navItem(index);
            boolean selected = index == 9;
            ui.fill(item, selected ? FakeToolsUi.CARD_ACTIVE : FakeToolsUi.SURFACE);
            if (selected) {
                ui.fill(item.left(2), FakeToolsUi.ACCENT);
            }
            int color = selected ? FakeToolsUi.ACCENT : FakeToolsUi.TEXT_SECONDARY;
            int iconY = item.centerY() - 6;
            if (labels) {
                MockupIcons.draw(ui, icons[index], item.x() + 6, iconY, color);
                int labelX = item.x() + 6 + 12 + 5;
                ui.text(ui.fit(names[index], item.right() - 4 - labelX), labelX, item.centerY() - 4,
                        selected ? FakeToolsUi.TEXT : FakeToolsUi.TEXT_SECONDARY, false);
            } else {
                MockupIcons.draw(ui, icons[index], item.centerX() - 6, iconY, color);
            }
        }
    }

    private static void header(FakeToolsUi ui, StudioLayout.Frame frame, TestHost host) {
        Rect header = frame.header();
        int x = frame.innerLeft();
        ui.bold(host.tr("gui.komaedit.stage.tools"), x, header.y() + 9, frame.innerWidth(), ToolsUi.Ink.TEXT);
        ui.label(host.tr("gui.komaedit.stage.tools.subtitle"), x, header.y() + 21, frame.innerWidth(),
                ToolsUi.Ink.MUTED);
        ui.fill(new Rect(header.x(), header.bottom() - 1, header.width(), 1), FakeToolsUi.DIVIDER);
    }

    private static void content(FakeToolsUi ui, StudioLayout.Frame frame, Setup setup) {
        Rect viewport = frame.viewport();
        ui.pushClip(viewport);
        Column column = new Column(frame.innerLeft(), viewport.y() + 8 - setup.scroll, frame.innerWidth());
        setup.panel.draw(ui, column, setup.host);
        int contentHeight = column.height() + 12;
        ui.popClip();
        int maximum = Math.max(0, contentHeight - viewport.height());
        if (maximum > 0) {
            int scroll = Math.clamp(setup.scroll, 0, maximum);
            int track = viewport.height() - 8;
            int thumb = Math.max(16, track * viewport.height() / contentHeight);
            int thumbY = viewport.y() + 4 + (track - thumb) * scroll / maximum;
            ui.fill(new Rect(viewport.right() - 4, viewport.y() + 4, 2, track), FakeToolsUi.DIVIDER);
            ui.fill(new Rect(viewport.right() - 4, thumbY, 2, thumb), FakeToolsUi.ACCENT_DIM);
        }
    }

    private static void footer(FakeToolsUi ui, StudioLayout.Frame frame, Setup setup) {
        Rect footer = frame.footer();
        ui.fill(footer, FakeToolsUi.SURFACE);
        ui.fill(new Rect(footer.x(), footer.y(), footer.width(), 1), FakeToolsUi.DIVIDER);
        Rect summary = frame.footerSummary();
        ToolsPanel panel = setup.panel;
        TestHost host = setup.host;
        ui.label(panel.footerSummary(host), summary.x(), summary.y(), summary.width(), ToolsUi.Ink.TEXT_SECONDARY);
        if (setup.job != null) {
            JobStrip.draw(ui, frame, setup.job, setup.changes, 0, host, panel.buildLabel(host), panel.buildTone(host),
                    () -> { }, panel.buildHelp(host));
            return;
        }
        Rect status = frame.footerStatus();
        ui.label(host.status, status.x(), status.y(), status.width(), ToolsUi.Ink.MUTED);
        ui.button(frame.buildButton(), panel.buildLabel(host), panel.buildTone(host));
    }

    /** La etapa entera con la tarjeta {@code card}, sin recorte, en una tira alta. */
    private static BufferedImage strip(ToolCard card, String language) {
        Setup setup = new Setup(language);
        setup.host.info = new ToolsInfo(true, new int[]{10, 6, 12}, List.of("cursiva"), Map.of("bosque",
                "spruce 30 -d 1.4"), Map.of("menhir", "spire 3 -a 5"), new ToolsInfo.Symmetry(true, "xz", 4,
                "x 10, y 64, z -3|-2", false, false), capabilities(true), List.of("minecraft:cherry_grove",
                "minecraft:plains", "minecraft:snowy_plains", "minecraft:desert"),
                card.id().equals("analyze") ? materials() : null);
        setup.panel.select(card, setup.host);
        int width = StudioLayout.compute(960, 540, 350, 10).innerWidth();
        FakeToolsUi probe = new FakeToolsUi(FONT);
        Column measure = new Column(10, 10, width);
        setup.panel.draw(probe, measure, setup.host);
        int height = measure.height() + 20;
        int scale = 2;
        BufferedImage image = new BufferedImage((width + 20) * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
        FakeToolsUi ui = new FakeToolsUi(image, scale, FONT);
        ui.fill(new Rect(0, 0, width + 20, height), FakeToolsUi.PANEL);
        setup.panel.draw(ui, new Column(10, 10, width), setup.host);
        return image;
    }
}
