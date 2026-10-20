package com.komainu.moxia.studio.ui;

import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.net.JobStatusPayload;
import com.komainu.moxia.studio.ui.StudioTheme.Token;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.komainu.moxia.studio.ui.StudioTheme.Token.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cada tema se lee bien y sigue las reglas de color, comprobado de dos formas: las parejas texto/fondo que se usan,
 * escritas aquí con la norma WCAG, y el Studio pintado de verdad con cada tema, midiendo cada texto contra el fondo
 * que tiene debajo en la imagen (así no se escapa una pareja que nadie apuntó).
 */
class StudioThemeContrastTest {
    /** WCAG AA para texto normal: la fuente de píxel a GUI 2 es pequeña, así que no vale el 3:1 de texto grande. */
    private static final double TEXT = 4.5;
    /** WCAG 1.4.11: marcas de selección, bordes de foco y controles. */
    private static final double MARK = 3.0;
    /** Lo desactivado no tiene mínimo en WCAG; pedimos que se pueda leer, y menos que una nota. */
    private static final double DISABLED_TEXT = 2.2;

    /** Cada color de texto y los fondos sobre los que sale. */
    private static final Map<Token, List<Token>> TEXT_PAIRS = new LinkedHashMap<>();

    static {
        List<Token> surfaces = List.of(PANEL, SURFACE, CARD, CARD_HOVER, CARD_ACTIVE);
        TEXT_PAIRS.put(TEXT_STRONG, List.of(PANEL, SURFACE, CARD, CARD_HOVER, CARD_ACTIVE, ACCENT_DIM));
        TEXT_PAIRS.put(StudioTheme.Token.TEXT, List.of(PANEL, SURFACE, CARD, CARD_HOVER, CARD_ACTIVE, FIELD,
                ACCENT_DIM));
        TEXT_PAIRS.put(TEXT_SECONDARY, surfaces);
        TEXT_PAIRS.put(MUTED, List.of(PANEL, SURFACE, CARD, CARD_HOVER, CARD_ACTIVE, FIELD));
        TEXT_PAIRS.put(ACCENT_TEXT, surfaces);
        TEXT_PAIRS.put(ON_ACCENT, List.of(ACCENT, ACCENT_HOVER));
        TEXT_PAIRS.put(SUCCESS, List.of(PANEL, SURFACE, CARD, CARD_HOVER));
        TEXT_PAIRS.put(WARNING, List.of(PANEL, SURFACE, CARD, CARD_HOVER, WARNING_DIM, WARNING_HOVER));
        TEXT_PAIRS.put(DANGER, List.of(PANEL, SURFACE, CARD, CARD_HOVER, DANGER_DIM));
        TEXT_PAIRS.put(FAVORITE, List.of(SURFACE, CARD, CARD_HOVER));
    }

    @BeforeAll
    static void thumbnails() {
        ChoiceThumbnails.setBlocking(true);
    }

    @AfterEach
    void restore() {
        StudioTheme.use(StudioPalette.DEFAULT);
    }

    @Test
    void textPairsMeetWcagAa() {
        List<String> failures = new ArrayList<>();
        for (StudioPalette palette : StudioPalette.values()) {
            TEXT_PAIRS.forEach((text, backgrounds) -> {
                for (Token background : backgrounds) {
                    double ratio = contrast(palette.color(text), palette.color(background));
                    if (ratio < TEXT) {
                        failures.add(palette.id() + ": " + text + " sobre " + background + " = " + round(ratio));
                    }
                }
            });
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void marksAndStatesAreVisible() {
        List<String> failures = new ArrayList<>();
        for (StudioPalette palette : StudioPalette.values()) {
            // Barra o marco de lo elegido sobre cualquier tarjeta, y el borde del campo en el que se escribe.
            for (Token background : List.of(PANEL, SURFACE, CARD, CARD_HOVER, CARD_ACTIVE)) {
                need(failures, palette, ACCENT, background, MARK);
            }
            need(failures, palette, FOCUS, FIELD, MARK);
            need(failures, palette, ACCENT, TRACK, 2.0);
            // El pulsador de un interruptor, encendido sobre el acento y apagado sobre el carril.
            need(failures, palette, ON_ACCENT, ACCENT, MARK);
            need(failures, palette, TEXT_SECONDARY, TRACK, MARK);
            // Bajo el ratón: un relleno que se nota y un borde claro.
            need(failures, palette, CARD_HOVER, CARD, 1.12);
            need(failures, palette, OUTLINE, CARD_HOVER, 2.0);
            need(failures, palette, OUTLINE, SURFACE, 2.0);
            // Las tarjetas se separan del fondo, y las barras sin cambiar del carril.
            need(failures, palette, CARD, PANEL, 1.08);
            need(failures, palette, TRACK, CARD, 1.15);
            need(failures, palette, ACCENT_DIM, TRACK, 1.2);
            need(failures, palette, DIVIDER, PANEL, 1.25);
            for (Token background : List.of(PANEL, SURFACE, CARD)) {
                double disabled = contrast(palette.color(DISABLED), palette.color(background));
                double muted = contrast(palette.color(MUTED), palette.color(background));
                if (disabled < DISABLED_TEXT || disabled >= muted) {
                    failures.add(palette.id() + ": DISABLED sobre " + background + " = " + round(disabled)
                            + " (tiene que leerse y quedar por debajo de MUTED, " + round(muted) + ")");
                }
            }
            for (Token token : Token.values()) {
                int alpha = palette.color(token) >>> 24;
                if (token != SCRIM && alpha != 0xFF) {
                    failures.add(palette.id() + ": " + token + " no es opaco");
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /** El rojo solo dice peligro; éxito (verde o turquesa) y aviso (ámbar) se distinguen del acento. */
    @Test
    void redIsOnlyForDanger() {
        List<String> failures = new ArrayList<>();
        for (StudioPalette palette : StudioPalette.values()) {
            double accent = hue(palette.color(ACCENT));
            double danger = hue(palette.color(DANGER));
            double success = hue(palette.color(SUCCESS));
            double warning = hue(palette.color(WARNING));
            if (distance(danger, 0) > 15) {
                failures.add(palette.id() + ": el peligro no es rojo (" + Math.round(danger) + "°)");
            }
            if (distance(accent, 0) < 35) {
                failures.add(palette.id() + ": el acento es rojo o rosa (" + Math.round(accent) + "°)");
            }
            if (success < 85 || success > 185) {
                failures.add(palette.id() + ": el éxito no es verde ni turquesa (" + Math.round(success) + "°)");
            }
            if (warning < 30 || warning > 55) {
                failures.add(palette.id() + ": el aviso no es ámbar (" + Math.round(warning) + "°)");
            }
            for (Map.Entry<String, Double> state : Map.of("peligro", danger, "éxito", success, "aviso", warning)
                    .entrySet()) {
                if (distance(accent, state.getValue()) < 30) {
                    failures.add(palette.id() + ": el acento se confunde con el " + state.getKey());
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /**
     * Elegido, bajo el ratón y desactivado se distinguen por la forma y no solo por el tono: lo elegido lleva una marca
     * de acento, lo de debajo del ratón un borde, y lo desactivado va hueco.
     */
    @Test
    void statesDifferInShapeNotOnlyInColor() {
        for (StudioPalette palette : StudioPalette.values()) {
            StudioTheme.use(palette);
            Rect rect = new Rect(10, 10, 60, 16);
            // Un píxel del relleno sin letras: arriba a la izquierda, dentro del borde.
            int[] fill = {12, 11};
            int[] edge = {10, 18};
            int[] bar = {40, 25};

            BufferedImage normal = paint(ui -> ui.button(rect, "", StudioUi.Tone.SECONDARY), -1, -1);
            BufferedImage hovered = paint(ui -> ui.button(rect, "", StudioUi.Tone.SECONDARY), 40, 18);
            BufferedImage selected = paint(ui -> ui.button(rect, "", StudioUi.Tone.SELECTED), -1, -1);
            BufferedImage disabled = paint(ui -> ui.button(rect, "", StudioUi.Tone.DISABLED), -1, -1);
            String name = palette.id() + ": ";
            assertEquals(StudioTheme.CARD, pixel(normal, fill), name + "botón normal: relleno de tarjeta");
            assertEquals(StudioTheme.CARD_HOVER, pixel(hovered, fill), name + "bajo el ratón: relleno más claro");
            assertEquals(StudioTheme.OUTLINE, pixel(hovered, edge), name + "bajo el ratón: borde");
            assertEquals(StudioTheme.ACCENT, pixel(selected, bar), name + "elegido: barra de acento");
            assertEquals(StudioTheme.PANEL, pixel(disabled, fill), name + "desactivado: hueco");
            assertEquals(StudioTheme.DIVIDER, pixel(disabled, edge), name + "desactivado: solo el contorno");

            BufferedImage chip = paint(ui -> ui.chip(rect, "", true, StudioTheme.TEXT), -1, -1);
            BufferedImage chipHover = paint(ui -> ui.chip(rect, "", false, StudioTheme.TEXT), 40, 18);
            assertEquals(StudioTheme.ACCENT, pixel(chip, bar), name + "pastilla elegida: barra de acento");
            assertEquals(StudioTheme.OUTLINE, pixel(chipHover, edge), name + "pastilla bajo el ratón: borde");

            // Las tarjetas de variación: encendida, relleno tintado, barra de acento y el pulsador a la derecha.
            Rect card = new Rect(10, 10, 160, 28);
            BufferedImage on = paint(ui -> ui.pictureToggle(card, null, "", "", true), -1, -1);
            BufferedImage off = paint(ui -> ui.pictureToggle(card, null, "", "", false), -1, -1);
            int[] leftBar = {10, 24};
            int[] knobOn = {158, 23};
            int[] knobOff = {148, 23};
            assertEquals(StudioTheme.ACCENT, pixel(on, leftBar), name + "variación encendida: barra de acento");
            assertEquals(StudioTheme.CARD, pixel(off, leftBar), name + "variación apagada: sin barra");
            assertEquals(StudioTheme.ON_ACCENT, pixel(on, knobOn), name + "encendida: pulsador a la derecha");
            assertEquals(StudioTheme.ACCENT, pixel(on, knobOff), name + "encendida: carril de acento");
            assertEquals(StudioTheme.TEXT_SECONDARY, pixel(off, knobOff), name + "apagada: pulsador a la izquierda");
            assertEquals(StudioTheme.TRACK, pixel(off, knobOn), name + "apagada: carril gris");
        }
    }

    /**
     * Las hojas oscuras de una miniatura (el abeto, la más oscura) se separan de su fondo en todos los temas: el fondo de
     * miniatura es más claro y neutro que la tarjeta.
     */
    @Test
    void darkFoliageStandsOutOfItsThumbnail() {
        StudioImage spruce = ChoiceThumbnails.get("tree", "species", "spruce");
        long red = 0;
        long green = 0;
        long blue = 0;
        int count = 0;
        for (int argb : spruce.pixels()) {
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            if ((argb >>> 24) == 0xFF && g > r && g >= b) {
                red += r;
                green += g;
                blue += b;
                count++;
            }
        }
        assertTrue(count > 100, "la miniatura del abeto no tiene hojas");
        int leaves = 0xFF000000 | (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);
        List<String> failures = new ArrayList<>();
        for (StudioPalette palette : StudioPalette.values()) {
            double onThumbnail = contrast(leaves, palette.color(THUMBNAIL));
            double onCard = contrast(leaves, palette.color(CARD));
            if (onThumbnail < 1.8 || onThumbnail < onCard && onCard < 3) {
                failures.add(palette.id() + ": hojas " + hex(leaves) + " sobre la miniatura " + round(onThumbnail)
                        + " (sobre la tarjeta " + round(onCard) + ")");
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /** Todo el texto que pinta el Studio, en las pantallas de las maquetas, con cada tema y en dos tamaños. */
    @Test
    void everyDrawnTextIsReadable() {
        Map<String, Consumer<StudioDriver>> scenes = scenes();
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (StudioPalette palette : StudioPalette.values()) {
            for (int[] size : new int[][]{{640, 360}, {427, 240}}) {
                for (Map.Entry<String, Consumer<StudioDriver>> scene : scenes.entrySet()) {
                    String language = scene.getKey().endsWith("_en") ? "en_us" : "es_es";
                    StudioDriver driver = new StudioDriver(language, size[0], size[1]);
                    driver.host.context = StudioMockupsTest.SELECTED;
                    driver.host.info = StudioMockupsTest.info();
                    driver.host.preferences.setTheme(palette.id());
                    scene.getValue().accept(driver);
                    BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
                    ContrastCanvas canvas = new ContrastCanvas(image);
                    driver.view.render(new StudioUi(canvas), size[0], size[1], driver.mouseX, driver.mouseY,
                            driver.host);
                    assertEquals(palette, StudioTheme.current(), "la vista pone el tema de las preferencias");
                    for (Measured text : canvas.measured) {
                        checked++;
                        boolean disabled = text.argb() == StudioTheme.DISABLED;
                        double need = disabled ? DISABLED_TEXT : TEXT;
                        if (text.ratio() < need) {
                            failures.add(palette.id() + " " + size[0] + "×" + size[1] + " " + scene.getKey() + ": «"
                                    + text.text() + "» " + hex(text.argb()) + " sobre " + hex(text.background()) + " = "
                                    + round(text.ratio()));
                        }
                    }
                }
            }
        }
        assertTrue(checked > 2_000, "solo se midieron " + checked + " textos");
        assertTrue(failures.isEmpty(), failures.size() + " textos con poco contraste:\n"
                + String.join("\n", failures.subList(0, Math.min(40, failures.size()))));
    }

    private static Map<String, Consumer<StudioDriver>> scenes() {
        Map<String, Consumer<StudioDriver>> scenes = new LinkedHashMap<>();
        scenes.put("herramientas", driver -> StudioMockupsTest.tools(driver, null));
        scenes.put("arbol", driver -> StudioMockupsTest.tools(driver, "tree"));
        scenes.put("tejado_en", driver -> StudioMockupsTest.tools(driver, "roof"));
        scenes.put("rio", driver -> {
            StudioMockupsTest.path(driver, 4);
            StudioMockupsTest.tools(driver, "river");
        });
        scenes.put("planos", StudioMockupsTest::blueprints);
        scenes.put("trayectoria", driver -> {
            StudioMockupsTest.path(driver, 4);
            driver.view.selectStage(StudioStage.PATH, driver.host);
        });
        scenes.put("figuras", driver -> {
            StudioMockupsTest.path(driver, 3);
            driver.view.selectStage(StudioStage.FIGURE, driver.host);
        });
        scenes.put("figuras_lista", driver -> {
            StudioMockupsTest.path(driver, 3);
            driver.view.selectStage(StudioStage.FIGURE, driver.host);
            driver.view.figure().setBrowsing(true);
        });
        scenes.put("material", driver -> {
            StudioMockupsTest.figure(driver);
            StudioMockupsTest.scrollTo(driver, "MATERIAL");
        });
        scenes.put("variaciones_en", driver -> StudioMockupsTest.variations(driver, true));
        scenes.put("variaciones_apagadas", driver -> StudioMockupsTest.variations(driver, false));
        scenes.put("primera_vez", driver -> driver.view.selectStage(StudioStage.FIGURE, driver.host));
        scenes.put("sello_galeria", driver -> {
            StudioMockupsTest.stampGallery(driver);
            if (driver.has("Cumbre doble")) {
                int[] tile = driver.where("Cumbre doble");
                driver.mouseX = tile[0];
                driver.mouseY = tile[1];
            }
        });
        scenes.put("sello_buscar", driver -> {
            StudioMockupsTest.stampGallery(driver);
            driver.render();
            // En pantallas pequeñas la galería va por páginas, sin buscador.
            if (driver.ui().widgets().stream().anyMatch(widget -> "tools:stamp_search".equals(widget.key()))) {
                driver.clickKey("tools:stamp_search");
                driver.type("isla");
            }
        });
        scenes.put("pintar", driver -> StudioMockupsTest.tools(driver, "paintbrush"));
        scenes.put("ayuda_numero", driver -> {
            StudioMockupsTest.tools(driver, "tree");
            int[] value = driver.valuePoint("Ancho de copa");
            driver.mouseX = value[0];
            driver.mouseY = value[1];
        });
        scenes.put("arrastrar", driver -> {
            StudioMockupsTest.tools(driver, "tree");
            int[] value = driver.valuePoint("Altura");
            driver.view.mouseClicked(value[0], value[1], 0, driver.host);
            driver.view.mouseDragged(value[0] + 30, value[1], 0, driver.host);
            driver.mouseX = value[0] + 30;
            driver.mouseY = value[1];
        });
        scenes.put("pie_mundo", driver -> {
            StudioMockupsTest.tools(driver, "roof");
            driver.mouseX = 60;
            driver.mouseY = 150;
            driver.render();
            driver.advance(StudioView.LEGEND_MILLIS + 100);
        });
        scenes.put("color", driver -> driver.view.selectStage(StudioStage.COLOR, driver.host));
        scenes.put("recetas", driver -> driver.view.selectStage(StudioStage.RECIPES, driver.host));
        scenes.put("instancias", driver -> driver.view.selectStage(StudioStage.INSTANCES, driver.host));
        scenes.put("navegacion", driver -> {
            StudioMockupsTest.tools(driver, "roof");
            driver.render();
            Rect icon = driver.view.frame().navItem(StudioStage.COLOR.ordinal());
            driver.mouseX = icon.centerX();
            driver.mouseY = icon.centerY();
        });
        scenes.put("bloques", driver -> {
            StudioMockupsTest.tools(driver, "roof");
            driver.host.lookedAt = "minecraft:stone_bricks";
            driver.click("Tablones de abeto");
        });
        scenes.put("paleta", driver -> {
            StudioMockupsTest.tools(driver, "tree");
            driver.key(StudioKey.letter('K', true, false));
            driver.type("tema");
        });
        scenes.put("chuleta", driver -> {
            StudioMockupsTest.tools(driver, "tree");
            driver.key(StudioKey.of(StudioKey.Code.F1));
        });
        scenes.put("historial", driver -> {
            StudioMockupsTest.tools(driver, "roof");
            driver.host.history = new StudioHost.WorldHistory(List.of(
                    new StudioHost.WorldHistory.Entry("Tejado a cuatro aguas", 1_204),
                    new StudioHost.WorldHistory.Entry("Árbol", 412),
                    new StudioHost.WorldHistory.Entry("WorldEdit", 3_820)), 1);
            driver.click("Cambios en el mundo: deshacer o rehacer hasta un punto (Ctrl+Z, Ctrl+Y)");
        });
        scenes.put("en_espera", driver -> {
            StudioMockupsTest.tools(driver, "roof");
            driver.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.LIVE);
            driver.host.job = JobStatusPayload.of(new ProgressLine("Tejado a dos aguas", ProgressLine.Phase.WAITING,
                    74_000, 90_000, 0, null), 3_412);
        });
        scenes.put("colocando_en", driver -> {
            StudioMockupsTest.tools(driver, "snow");
            driver.host.job = JobStatusPayload.of(new ProgressLine("Snow (3 layers)", ProgressLine.Phase.PLACING,
                    41_000, 96_000, 3_000_000_000L, null), 0);
        });
        scenes.put("fantasma_rechazado", driver -> {
            StudioMockupsTest.tools(driver, "roof");
            driver.host.ghost = GhostFooterTest.ghost("Tejado a dos aguas", "giro 180° volteado · 12×6×9",
                    "88, 72, 15", "Se sale de la altura del mundo", new int[5]);
        });
        scenes.put("mas_ajustes", driver -> {
            StudioMockupsTest.tools(driver, "tree");
            driver.host.preferences.setExpanded("tools:more:tree", true);
            for (int step = 0; step < 16 && !driver.has("Curvatura"); step++) {
                driver.view.mouseScrolled(driver.view.frame().innerLeft() + 4,
                        driver.view.frame().viewport().centerY(), -1, driver.host);
            }
            if (driver.has("Curvatura")) {
                int[] row = driver.where("Curvatura");
                driver.mouseX = row[0];
                driver.mouseY = row[1];
            }
        });
        scenes.put("sin_seleccion", driver -> {
            driver.host.context = new com.komainu.moxia.studio.tools.ToolContext(true,
                    com.komainu.moxia.studio.tools.ToolCatalog.permissionNodes(), false, 0L, 0, false);
            StudioMockupsTest.tools(driver, "snow");
        });
        return scenes;
    }

    private static void need(List<String> failures, StudioPalette palette, Token a, Token b, double minimum) {
        double ratio = contrast(palette.color(a), palette.color(b));
        if (ratio < minimum) {
            failures.add(palette.id() + ": " + a + " sobre " + b + " = " + round(ratio) + " < " + minimum);
        }
    }

    private static BufferedImage paint(Consumer<StudioUi> draw, int mouseX, int mouseY) {
        BufferedImage image = new BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB);
        StudioUi ui = new StudioUi(new ImageCanvas(image, 1));
        ui.fill(new Rect(0, 0, 200, 50), StudioTheme.PANEL);
        ui.begin(mouseX, mouseY, true);
        draw.accept(ui);
        return image;
    }

    private static int pixel(BufferedImage image, int[] point) {
        return image.getRGB(point[0], point[1]);
    }

    // ── Medir ───────────────────────────────────────────────────────────────────────────────────────────

    /** Contraste WCAG entre dos colores opacos. */
    static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    static double luminance(int argb) {
        return 0.2126 * channel(argb >> 16) + 0.7152 * channel(argb >> 8) + 0.0722 * channel(argb);
    }

    private static double channel(int value) {
        double c = (value & 0xFF) / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Tono en grados (0 rojo, 120 verde, 240 azul). */
    static double hue(int argb) {
        double r = ((argb >> 16) & 0xFF) / 255.0;
        double g = ((argb >> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        if (delta == 0) {
            return 0;
        }
        double hue = max == r ? ((g - b) / delta) % 6 : max == g ? (b - r) / delta + 2 : (r - g) / delta + 4;
        return (hue * 60 + 360) % 360;
    }

    private static double distance(double a, double b) {
        double difference = Math.abs(a - b) % 360;
        return Math.min(difference, 360 - difference);
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String hex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    /** Un texto pintado y el fondo que tenía debajo. */
    private record Measured(String text, int argb, int background, double ratio) {
    }

    /**
     * Lienzo de imagen que, antes de escribir cada texto, mira el color que más se repite en el hueco que ocupará
     * (el fondo) y apunta el contraste. Los textos sobre algo variado (una miniatura) no se miden.
     */
    private static final class ContrastCanvas implements StudioCanvas {
        private final ImageCanvas canvas;
        private final BufferedImage image;
        private final List<Rect> clips = new ArrayList<>();
        private final List<Measured> measured = new ArrayList<>();

        private ContrastCanvas(BufferedImage image) {
            this.image = image;
            this.canvas = new ImageCanvas(image, 1);
        }

        @Override
        public int width(String text) {
            return canvas.width(text);
        }

        @Override
        public int boldWidth(String text) {
            return canvas.boldWidth(text);
        }

        @Override
        public void text(String text, int x, int y, int argb) {
            measure(text, x, y, canvas.width(text), argb);
            canvas.text(text, x, y, argb);
        }

        @Override
        public void bold(String text, int x, int y, int argb) {
            measure(text, x, y, canvas.boldWidth(text), argb);
            canvas.bold(text, x, y, argb);
        }

        private void measure(String text, int x, int y, int width, int argb) {
            if (text.isBlank()) {
                return;
            }
            Rect box = new Rect(x, y, width, 8);
            for (Rect clip : clips) {
                box = intersection(box, clip);
            }
            int left = Math.max(0, box.x());
            int top = Math.max(0, box.y());
            int right = Math.min(image.getWidth(), box.right());
            int bottom = Math.min(image.getHeight(), box.bottom());
            if (right - left < 2 || bottom - top < 4) {
                return;
            }
            Map<Integer, Integer> counts = new HashMap<>();
            for (int row = top; row < bottom; row++) {
                for (int column = left; column < right; column++) {
                    counts.merge(image.getRGB(column, row), 1, Integer::sum);
                }
            }
            Map.Entry<Integer, Integer> most = counts.entrySet().stream().max(Map.Entry.comparingByValue())
                    .orElseThrow();
            if (most.getValue() * 10 < (right - left) * (bottom - top) * 4) {
                return;
            }
            int background = most.getKey();
            int color = blend(background, argb);
            measured.add(new Measured(text, argb, background, contrast(color, background)));
        }

        private static int blend(int under, int over) {
            double alpha = (over >>> 24) / 255.0;
            int red = (int) Math.round(((over >> 16) & 0xFF) * alpha + ((under >> 16) & 0xFF) * (1 - alpha));
            int green = (int) Math.round(((over >> 8) & 0xFF) * alpha + ((under >> 8) & 0xFF) * (1 - alpha));
            int blue = (int) Math.round((over & 0xFF) * alpha + (under & 0xFF) * (1 - alpha));
            return 0xFF000000 | red << 16 | green << 8 | blue;
        }

        private static Rect intersection(Rect a, Rect b) {
            int left = Math.max(a.x(), b.x());
            int top = Math.max(a.y(), b.y());
            return new Rect(left, top, Math.min(a.right(), b.right()) - left, Math.min(a.bottom(), b.bottom()) - top);
        }

        @Override
        public void fill(Rect rect, int argb) {
            canvas.fill(rect, argb);
        }

        @Override
        public void item(String id, int x, int y) {
            canvas.item(id, x, y);
        }

        @Override
        public void image(StudioImage studioImage, Rect target) {
            canvas.image(studioImage, target);
        }

        @Override
        public void pushClip(Rect clip) {
            clips.add(clip);
            canvas.pushClip(clip);
        }

        @Override
        public void popClip() {
            if (!clips.isEmpty()) {
                clips.removeLast();
            }
            canvas.popClip();
        }

        @Override
        public void beginOverlay() {
            canvas.beginOverlay();
        }

        @Override
        public void endOverlay() {
            canvas.endOverlay();
        }
    }
}
