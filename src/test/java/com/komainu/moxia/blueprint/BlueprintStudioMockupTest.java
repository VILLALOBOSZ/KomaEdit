package com.komainu.moxia.blueprint;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.layout.StudioLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maqueta de la etapa «Planos» del Studio para revisarla sin abrir Minecraft: el reparto sale del mismo motor que
 * usa la pantalla ({@link StudioLayout} y {@link Column}, con las medidas de {@code BlueprintStage}), los textos de
 * {@code es_es.json} y las miniaturas de {@link BlueprintThumbnail}. Solo la tipografía es otra (Java2D en vez de la
 * fuente de Minecraft). Se ejecuta con {@code KOMAEDIT_BLUEPRINT_RENDER=<carpeta>}; en el CI no hace nada.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_BLUEPRINT_RENDER", matches = ".+")
class BlueprintStudioMockupTest {
    private static final int GUI_SCALE = 2;
    private static final int WIDTH = 640;
    private static final int HEIGHT = 560;
    // StudioTheme (cliente), copiado: las pruebas no ven el código del cliente.
    private static final int PANEL = 0xFF101117;
    private static final int SURFACE = 0xFF16171F;
    private static final int CARD = 0xFF20222B;
    private static final int CARD_HOVER = 0xFF2B2E39;
    private static final int CARD_ACTIVE = 0xFF3A1E29;
    private static final int FIELD = 0xFF0B0C10;
    private static final int DIVIDER = 0xFF2A2C36;
    private static final int ACCENT = 0xFFE24B6A;
    private static final int ACCENT_DIM = 0xFF7D2438;
    private static final int SECTION = 0xFFE88EA2;
    private static final int TEXT = 0xFFF4F0F3;
    private static final int TEXT_SECONDARY = 0xFFBDB8C2;
    private static final int MUTED = 0xFF8B8794;
    private static final int FAVORITE = 0xFFF2C14B;
    // BlueprintStage.
    private static final int MIN_CARD = 60;
    private static final int CAPTION = 20;

    private Graphics2D graphics;
    private JsonObject lang;

    private record Card(String name, BlueprintThumbnail.Grid grid, boolean shared, String author, int blocks,
                        List<String> tags) {
        String size() {
            return grid.sizeX() + "×" + grid.sizeY() + "×" + grid.sizeZ();
        }
    }

    @Test
    void renderTheBlueprintStage() throws IOException {
        lang = JsonParser.parseString(Files.readString(Path.of("src", "main", "resources", "assets", "komaedit",
                "lang", "es_es.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        List<Card> cards = List.of(
                new Card("roca_musgo", BlueprintFixtures.rock(3), false, "Varo", 96, List.of("rocas", "musgo")),
                new Card("roca_gris", BlueprintFixtures.rock(11), false, "Varo", 88, List.of("rocas")),
                new Card("arbusto", BlueprintFixtures.bush(), false, "Varo", 41, List.of("arbustos", "bosque")),
                new Card("ruina_patio", BlueprintFixtures.ruin(7), true, "Villalobos", 210, List.of("ruinas")),
                new Card("casita", BlueprintFixtures.house(), true, "Villalobos", 342, List.of("casas", "aldea")),
                new Card("abedul", BlueprintFixtures.tree(), false, "Varo", 155, List.of("arboles", "bosque")),
                new Card("pozo", BlueprintFixtures.well(), true, "Villalobos", 74, List.of("aldea", "agua")),
                new Card("ruina_arco", BlueprintFixtures.ruin(21), false, "Varo", 198, List.of("ruinas")),
                new Card("roca_alta", BlueprintFixtures.rock(5), false, "Varo", 91, List.of("rocas")));
        Map<String, BufferedImage> thumbnails = new LinkedHashMap<>();
        for (Card card : cards) {
            thumbnails.put(card.name(), BlueprintThumbnail.render(card.grid(), BlueprintThumbnail.SIZE));
        }

        write(studio(cards, thumbnails, null), "studio_planos.png");
        write(studio(cards, thumbnails, "rocas"), "studio_planos_rocas.png");

        // Tira con todas las miniaturas a su tamaño real, sobre el color de las tarjetas.
        BufferedImage strip = new BufferedImage(thumbnails.size() * 136 + 8, 144, BufferedImage.TYPE_INT_ARGB);
        Graphics2D stripGraphics = strip.createGraphics();
        stripGraphics.setColor(new Color(CARD, true));
        stripGraphics.fillRect(0, 0, strip.getWidth(), strip.getHeight());
        int x = 8;
        for (BufferedImage thumbnail : thumbnails.values()) {
            stripGraphics.setColor(new Color(FIELD, true));
            stripGraphics.fillRect(x, 8, 128, 128);
            stripGraphics.drawImage(thumbnail, x, 8, null);
            x += 136;
        }
        stripGraphics.dispose();
        write(strip, "miniaturas.png");
    }

    /** El Studio entero con la etapa «Planos»; {@code tag} es la etiqueta del buscador, o ninguna. */
    private BufferedImage studio(List<Card> cards, Map<String, BufferedImage> thumbnails, String tag) {
        BufferedImage image = new BufferedImage(WIDTH * GUI_SCALE, HEIGHT * GUI_SCALE, BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();
        graphics.scale(GUI_SCALE, GUI_SCALE);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 8));
        StudioLayout.Frame frame = StudioLayout.compute(WIDTH, HEIGHT, StudioLayout.DEFAULT_PANEL_WIDTH, 10);
        world(frame.world());
        fill(frame.panel(), PANEL);
        toolbar(frame);
        navigation(frame);
        header(frame);
        content(frame, cards, thumbnails, tag);
        footer(frame, tag);
        graphics.dispose();
        return image;
    }

    // ------------------------------------------------------------------ Marco

    private void world(Rect area) {
        graphics.setPaint(new GradientPaint(0, 0, new Color(0x8FB8E8), 0, area.height() * 0.6F, new Color(0xCFE3F5)));
        graphics.fillRect(area.x(), area.y(), area.width(), area.height());
        graphics.setColor(new Color(0x5E8F3A));
        graphics.fillRect(area.x(), (int) (area.height() * 0.62), area.width(), area.height());
        graphics.setColor(new Color(0x4C7A2E));
        for (int x = area.x(); x < area.right(); x += 8) {
            graphics.fillRect(x, (int) (area.height() * 0.62) + ((x / 8) % 3), 4, 2);
        }
    }

    private void toolbar(StudioLayout.Frame frame) {
        Rect bar = frame.toolbar();
        fill(bar, SURFACE);
        fill(new Rect(bar.x(), bar.bottom() - 1, bar.width(), 1), DIVIDER);
        fill(new Rect(bar.x(), bar.y(), 3, bar.height()), ACCENT);
        bold("KomaEdit", bar.x() + 11, bar.centerY() - 4, TEXT);
        for (int index = 0; index < 5; index++) {
            Rect button = frame.toolButton(index);
            fill(button, SURFACE);
            graphics.setColor(new Color(TEXT_SECONDARY, true));
            graphics.drawRect(button.x() + 5, button.y() + 5, 9, 9);
        }
    }

    private void navigation(StudioLayout.Frame frame) {
        Rect nav = frame.nav();
        fill(nav, SURFACE);
        fill(new Rect(nav.right() - 1, nav.y(), 1, nav.height()), DIVIDER);
        String[] stages = {"path", "figure", "structures", "modifiers", "material", "weather", "instances",
                "recipes", "color", "blueprints"};
        for (int index = 0; index < stages.length; index++) {
            Rect item = frame.navItem(index);
            boolean selected = index == stages.length - 1;
            fill(item, selected ? CARD_ACTIVE : SURFACE);
            if (selected) {
                fill(item.left(2), ACCENT);
            }
            int color = selected ? ACCENT : TEXT_SECONDARY;
            fill(new Rect(item.x() + 6, item.centerY() - 5, 10, 10), color & 0x55FFFFFF);
            graphics.setColor(new Color(color, true));
            graphics.drawRect(item.x() + 6, item.centerY() - 5, 10, 10);
            label(tr("gui.komaedit.stage." + stages[index]), item.x() + 23, item.centerY() - 4,
                    selected ? TEXT : TEXT_SECONDARY);
        }
    }

    private void header(StudioLayout.Frame frame) {
        Rect header = frame.header();
        bold(tr("gui.komaedit.stage.blueprints"), frame.innerLeft(), header.y() + 9, TEXT);
        label(tr("gui.komaedit.stage.blueprints.subtitle"), frame.innerLeft(), header.y() + 21, MUTED);
        fill(new Rect(header.x(), header.bottom() - 1, header.width(), 1), DIVIDER);
    }

    private void footer(StudioLayout.Frame frame, String tag) {
        Rect footer = frame.footer();
        fill(footer, SURFACE);
        fill(new Rect(footer.x(), footer.y(), footer.width(), 1), DIVIDER);
        label("Tubo · stone", frame.footerSummary().x(), frame.footerSummary().y(), TEXT_SECONDARY);
        String status = tag == null ? tr("gui.komaedit.stage.blueprints.subtitle")
                : tr("gui.komaedit.status.blueprint.stamp").replace("%s", "#" + tag);
        label(status, frame.footerStatus().x(), frame.footerStatus().y(), MUTED);
        Rect build = frame.buildButton();
        fill(build, ACCENT);
        centered("Construir", build, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ Etapa

    private void content(StudioLayout.Frame frame, List<Card> cards, Map<String, BufferedImage> thumbnails,
                         String tag) {
        Rect viewport = frame.viewport();
        graphics.setClip(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        Column column = new Column(frame.innerLeft(), viewport.y() + 8, frame.innerWidth());

        Rect search = column.next();
        fill(search, FIELD);
        if (tag == null) {
            border(search, DIVIDER);
            label(tr("gui.komaedit.blueprint.search"), search.x() + 6, search.centerY() - 4, MUTED);
        } else {
            border(search, ACCENT);
            label("#" + tag + "_", search.x() + 6, search.centerY() - 4, TEXT);
        }
        Rect filterRow = column.next();
        int refreshWidth = Math.max(60, filterRow.width() / 3);
        Rect[] filters = {new Rect(filterRow.x(), filterRow.y(), filterRow.width() - refreshWidth - Column.GAP,
                filterRow.height()), new Rect(filterRow.right() - refreshWidth, filterRow.y(), refreshWidth,
                filterRow.height())};
        for (String scope : List.of("server", "mine", "all")) {
            // El último que se pinta es el que se ve; los otros solo comprueban que caben.
            cycle(filters[0], tr("gui.komaedit.blueprint.scope"), tr("gui.komaedit.blueprint.scope." + scope));
        }
        fill(filters[1], CARD);
        centered(tr("gui.komaedit.blueprint.refresh"), filters[1], TEXT);

        // Pastillas de etiquetas, como BlueprintStage.tagChips.
        int x = column.x();
        Rect row = column.next(14);
        for (String chipTag : List.of("rocas", "aldea", "bosque", "ruinas", "agua", "arboles", "arbustos", "casas",
                "musgo")) {
            String label = "#" + chipTag;
            int width = width(label) + 12;
            if (x + width > column.x() + column.width()) {
                row = column.next(14);
                x = column.x();
            }
            boolean on = chipTag.equals(tag);
            Rect chip = new Rect(x, row.y(), width, 14);
            fill(chip, on ? ACCENT_DIM : CARD);
            centered(label, chip, on ? 0xFFFFFFFF : TEXT_SECONDARY);
            x += width + 3;
        }

        List<Card> shown = tag == null ? cards : cards.stream().filter(card -> card.tags().contains(tag)).toList();
        Card selected = shown.getFirst();
        section(column, selected.name());
        paragraph(column, tr("gui.komaedit.blueprint.details").replaceFirst("%s", selected.size())
                .replaceFirst("%s", Integer.toString(selected.blocks())).replaceFirst("%s", selected.author())
                + " · #" + String.join(" #", selected.tags()), TEXT_SECONDARY);
        Rect[] buttons = column.row(3, Column.ROW);
        fill(buttons[0], CARD);
        centered(tr("gui.komaedit.blueprint.load"), buttons[0], TEXT);
        fill(buttons[1], ACCENT);
        centered(tr("gui.komaedit.blueprint.place"), buttons[1], 0xFFFFFFFF);
        fill(buttons[2], CARD_HOVER);
        centered(tr("gui.komaedit.blueprint.stamp"), buttons[2], TEXT);
        cycle(column.next(), tr("gui.komaedit.blueprint.radius"), tag == null ? "0" : "8");
        if (tag != null) {
            Rect button = column.next();
            fill(button, CARD);
            centered(tr("gui.komaedit.blueprint.stamp_tag").replace("%s", "#" + tag), button, TEXT);
        }

        section(column, tr("gui.komaedit.blueprint.library").replace("%s", Integer.toString(shown.size())));
        int columns = Math.max(2, (column.width() + Column.GAP) / (MIN_CARD + Column.GAP));
        int cardWidth = (column.width() - (columns - 1) * Column.GAP) / columns;
        List<Rect> cells = column.grid(shown.size(), columns, cardWidth + CAPTION);
        for (int index = 0; index < cells.size(); index++) {
            Card card = shown.get(index);
            Rect cell = cells.get(index);
            boolean chosen = card == selected;
            fill(cell, chosen ? CARD_ACTIVE : index == 4 ? CARD_HOVER : CARD);
            Rect image = new Rect(cell.x() + 3, cell.y() + 3, cell.width() - 6, cell.width() - 6);
            fill(image, FIELD);
            graphics.drawImage(thumbnails.get(card.name()), image.x(), image.y(), image.width(), image.height(),
                    null);
            if (card.shared()) {
                fill(new Rect(image.right() - 7, image.y() + 2, 5, 5), FAVORITE);
            }
            if (chosen) {
                border(cell, ACCENT);
            }
            label(card.name(), cell.x() + 4, image.bottom() + 3, TEXT);
            label(card.size(), cell.x() + 4, image.bottom() + 12, MUTED);
        }
        graphics.setClip(null);
    }

    // ------------------------------------------------------------------ Primitivas (como StudioUi)

    private void section(Column column, String title) {
        column.section();
        Rect row = column.next(11);
        label(title, row.x(), row.y() + 1, SECTION);
        int lineX = row.x() + width(title) + 6;
        fill(new Rect(lineX, row.y() + 5, row.right() - lineX, 1), DIVIDER);
    }

    private void paragraph(Column column, String text, int color) {
        Rect area = column.next(10);
        label(text, area.x(), area.y(), color);
    }

    /** Misma geometría que {@code StudioUi.cycle}: valor con sus flechas a la derecha y etiqueta recortada. */
    private void cycle(Rect row, String label, String value) {
        fill(row, CARD);
        int step = 16;
        int valueWidth = Math.min(Math.max(90, row.width() * 3 / 5), row.width() - 60);
        Rect next = new Rect(row.right() - 2 - step, row.y() + 2, step, row.height() - 4);
        Rect shown = new Rect(next.x() - (valueWidth - 2 * step), next.y(), valueWidth - 2 * step, next.height());
        Rect previous = new Rect(shown.x() - step, next.y(), step, next.height());
        label(fit(label, previous.x() - row.x() - 12), row.x() + 7, row.centerY() - 4, TEXT_SECONDARY);
        centered("‹", previous, TEXT_SECONDARY);
        centered("›", next, TEXT_SECONDARY);
        centered(value, shown, TEXT);
        if (width(value) > shown.width()) {
            throw new AssertionError("«" + value + "» no cabe en el ciclo «" + label + "»");
        }
    }

    private String fit(String value, int maximumWidth) {
        if (width(value) <= maximumWidth) {
            return value;
        }
        String cut = value;
        while (!cut.isEmpty() && width(cut + "…") > maximumWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    private void fill(Rect rect, int argb) {
        graphics.setColor(new Color(argb, true));
        graphics.fillRect(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private void border(Rect rect, int argb) {
        graphics.setColor(new Color(argb, true));
        graphics.setStroke(new BasicStroke(1));
        graphics.drawRect(rect.x(), rect.y(), rect.width() - 1, rect.height() - 1);
    }

    private void label(String text, int x, int y, int argb) {
        graphics.setColor(new Color(argb, true));
        graphics.drawString(text, x, y + 7);
    }

    private void bold(String text, int x, int y, int argb) {
        Font font = graphics.getFont();
        graphics.setFont(font.deriveFont(Font.BOLD));
        label(text, x, y, argb);
        graphics.setFont(font);
    }

    private void centered(String text, Rect rect, int argb) {
        label(text, rect.centerX() - width(text) / 2, rect.centerY() - 4, argb);
    }

    private int width(String text) {
        FontMetrics metrics = graphics.getFontMetrics();
        return metrics.stringWidth(text);
    }

    private String tr(String key) {
        return lang.get(key).getAsString();
    }

    private static void write(BufferedImage image, String name) throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_BLUEPRINT_RENDER"));
        Files.createDirectories(folder);
        ImageIO.write(image, "png", folder.resolve(name).toFile());
    }
}
