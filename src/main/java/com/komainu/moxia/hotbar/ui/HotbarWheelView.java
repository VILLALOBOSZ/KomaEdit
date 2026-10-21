package com.komainu.moxia.hotbar.ui;

import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.ui.StudioCanvas;

import java.util.ArrayList;
import java.util.List;

/**
 * El menú circular de las barras (al tocar la tecla): hasta {@link #PER_PAGE} barras alrededor de un círculo, con su
 * icono y su nombre; en el centro, los nueve huecos de la que está bajo el ratón. Clic para usarla, rueda para pasar
 * de página. Abajo, guardar la barra actual con un nombre, pegar un código {@code kh1:} y copiar todas. La dibuja el
 * cliente en una pantalla y las pruebas en una imagen, con el mismo código; el cliente pone su campo de texto de
 * Minecraft encima del recuadro del nombre.
 */
public final class HotbarWheelView {
    public static final int PER_PAGE = 12;
    public static final int TILE = 26;
    /** Con pocas barras se reparten como si hubiera seis: no quedan dos en polos opuestos. */
    private static final int MIN_SPOKES = 6;
    private static final int FOOTER_HEIGHT = 18;
    /** Arriba quedan el título y la ayuda. */
    private static final int TOP = 32;
    /** Medio ancho de lo que va en el centro (los nueve huecos) más medio recuadro y un margen. */
    private static final int MIN_RADIUS_X = HotbarStripView.ROW_WIDTH / 2 + TILE / 2 + 8;
    /** Con menos radio, los nombres bajo cada icono chocarían entre sí: solo sale el de la que está bajo el ratón. */
    private static final int LABELS_RADIUS_X = 140;

    public enum Button {
        NONE,
        SAVE,
        PASTE,
        COPY_ALL
    }

    /**
     * Dónde va cada cosa.
     *
     * @param first  índice de la primera barra de la página
     * @param tiles  recuadro de cada barra de la página
     * @param labels si cabe el nombre bajo cada icono
     */
    public record Layout(int width, int height, int centerX, int centerY, int radiusX, int radiusY, int page,
                         int pages, int first, List<Rect> tiles, int spokes, boolean labels, Rect nameField, Rect save,
                         Rect paste, Rect copyAll) {
        /** Lo que ocupa la vista de la barra en el centro: ahí el ratón no elige otra. */
        public Rect center() {
            return new Rect(centerX - HotbarStripView.ROW_WIDTH / 2 - 4, centerY - 26, HotbarStripView.ROW_WIDTH + 8,
                    54);
        }

        public Rect button(Button button) {
            return switch (button) {
                case SAVE -> save;
                case PASTE -> paste;
                case COPY_ALL -> copyAll;
                case NONE -> Rect.EMPTY;
            };
        }
    }

    private HotbarWheelView() {
    }

    public static int pages(int count) {
        return Math.max(1, (count + PER_PAGE - 1) / PER_PAGE);
    }

    public static Layout layout(int width, int height, int count, int page) {
        int pages = pages(count);
        int current = Math.floorMod(page, pages);
        int first = current * PER_PAGE;
        int onPage = Math.max(0, Math.min(PER_PAGE, count - first));
        int spokes = Math.max(MIN_SPOKES, onPage);
        int centerX = width / 2;
        // Entre la ayuda de arriba y el aviso que va sobre los botones; un poco hacia arriba por los nombres.
        int bottom = height - FOOTER_HEIGHT - 8 - 14;
        int half = (bottom - TOP) / 2;
        int centerY = TOP + half - 4;
        int radiusY = Math.clamp(half - 20, 36, 100);
        int radiusX = Math.min(Math.max((int) (radiusY * 1.55), MIN_RADIUS_X), width / 2 - TILE / 2 - 8);
        List<Rect> tiles = new ArrayList<>(onPage);
        for (int index = 0; index < onPage; index++) {
            double angle = angle(index, spokes);
            int x = centerX + (int) Math.round(Math.cos(angle) * radiusX);
            int y = centerY + (int) Math.round(Math.sin(angle) * radiusY);
            tiles.add(new Rect(x - TILE / 2, y - TILE / 2, TILE, TILE));
        }
        int footerY = height - FOOTER_HEIGHT - 8;
        int gap = 4;
        int[] widths = {124, 64, 88, 88};
        int total = widths[0] + widths[1] + widths[2] + widths[3] + gap * 3;
        double shrink = Math.min(1.0, (width - 16) / (double) total);
        int x = centerX - (int) Math.round(total * shrink) / 2;
        Rect[] footer = new Rect[widths.length];
        for (int index = 0; index < widths.length; index++) {
            int w = (int) Math.round(widths[index] * shrink);
            footer[index] = new Rect(x, footerY, w, FOOTER_HEIGHT);
            x += w + (int) Math.round(gap * shrink);
        }
        return new Layout(width, height, centerX, centerY, radiusX, radiusY, current, pages, first, List.copyOf(tiles),
                spokes, radiusX >= LABELS_RADIUS_X, footer[0], footer[1], footer[2], footer[3]);
    }

    /** Índice (en el mazo) de la barra bajo el ratón, o -1: cuenta el sector entero, no solo el recuadro. */
    public static int tileAt(Layout layout, double mouseX, double mouseY) {
        if (layout.tiles().isEmpty() || mouseY >= layout.nameField().y() - 4
                || layout.center().contains(mouseX, mouseY)) {
            return -1;
        }
        double dx = (mouseX - layout.centerX()) / Math.max(1, layout.radiusX());
        double dy = (mouseY - layout.centerY()) / Math.max(1, layout.radiusY());
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < 0.55 || distance > 1.6) {
            return -1;
        }
        double slice = Math.PI * 2.0 / layout.spokes();
        int spoke = Math.floorMod((int) Math.round((Math.atan2(dy, dx) + Math.PI / 2.0) / slice), layout.spokes());
        return spoke < layout.tiles().size() ? layout.first() + spoke : -1;
    }

    public static Button buttonAt(Layout layout, double mouseX, double mouseY) {
        for (Button button : List.of(Button.SAVE, Button.PASTE, Button.COPY_ALL)) {
            if (layout.button(button).contains(mouseX, mouseY)) {
                return button;
            }
        }
        return Button.NONE;
    }

    /**
     * @param hovered barra bajo el ratón (índice del mazo) o -1
     * @param name    lo escrito en el nombre (en el juego lo pinta el campo de Minecraft encima)
     * @param status  aviso de la última acción («Copiado»…) o {@code null}
     */
    public static void draw(StudioCanvas canvas, Layout layout, HotbarDeck deck, int hovered, Button hoveredButton,
                            String name, String status, HotbarPainter painter, HotbarTexts texts) {
        canvas.fill(new Rect(0, 0, layout.width(), layout.height()), 0x8C000000);
        String title = texts.tr("gui.komaedit.hotbar.menu.title")
                + (layout.pages() > 1 ? "  ‹ " + (layout.page() + 1) + "/" + layout.pages() + " ›" : "");
        canvas.bold(title, layout.centerX() - canvas.boldWidth(title) / 2, 8, 0xFFFFFFFF);
        String help = texts.tr("gui.komaedit.hotbar.menu.help");
        canvas.text(help, layout.centerX() - canvas.width(help) / 2, 20, 0xFF9A9A9A);
        drawRing(canvas, layout);

        for (int index = 0; index < layout.tiles().size(); index++) {
            int deckIndex = layout.first() + index;
            Rect tile = layout.tiles().get(index);
            HotbarDeck.Row row = deck.row(deckIndex);
            boolean over = deckIndex == hovered;
            boolean active = deckIndex == deck.active();
            canvas.fill(tile, over ? 0xF0303030 : 0xD8141414);
            HotbarStripView.border(canvas, tile, over ? 0xFFFFFFFF : active ? HotbarStripView.ACTIVE : 0xFF4A4A4A);
            canvas.item(row.icon(), tile.x() + (TILE - 16) / 2, tile.y() + (TILE - 16) / 2);
            if (active) {
                canvas.fill(new Rect(tile.right() - 5, tile.y() + 2, 3, 3), HotbarStripView.ACTIVE);
            }
            if (layout.labels()) {
                String label = HotbarStripView.fit(canvas, row.name(), 66, false);
                canvas.text(label, tile.centerX() - canvas.width(label) / 2, tile.bottom() + 3,
                        over ? 0xFFFFFFFF : active ? 0xFF8FE3A8 : 0xFFB0B0B0);
            }
        }
        drawCenter(canvas, layout, deck, hovered, painter, texts);
        drawFooter(canvas, layout, hoveredButton, name, status, !deck.isEmpty(), texts);
    }

    private static void drawRing(StudioCanvas canvas, Layout layout) {
        int dots = 80;
        for (int dot = 0; dot < dots; dot++) {
            double angle = Math.PI * 2.0 * dot / dots;
            int x = layout.centerX() + (int) Math.round(Math.cos(angle) * layout.radiusX());
            int y = layout.centerY() + (int) Math.round(Math.sin(angle) * layout.radiusY());
            canvas.fill(new Rect(x - 1, y - 1, 2, 2), 0x55FFFFFF);
        }
    }

    private static void drawCenter(StudioCanvas canvas, Layout layout, HotbarDeck deck, int hovered,
                                   HotbarPainter painter, HotbarTexts texts) {
        int cx = layout.centerX();
        int cy = layout.centerY();
        if (deck.isEmpty()) {
            centered(canvas, texts.tr("gui.komaedit.hotbar.menu.empty"), cx, cy - 10, 0xFFFFFFFF, true);
            centered(canvas, texts.tr("gui.komaedit.hotbar.menu.empty_hint"), cx, cy + 4, 0xFFB0B0B0, false);
            return;
        }
        if (hovered < 0) {
            centered(canvas, texts.tr("gui.komaedit.hotbar.menu.pick"), cx, cy - 10, 0xFFDADADA, false);
            centered(canvas, texts.tr(deck.size() == 1 ? "gui.komaedit.hotbar.menu.count_one"
                    : "gui.komaedit.hotbar.menu.count", deck.size()), cx, cy + 4, 0xFF8A8A8A, false);
            return;
        }
        HotbarDeck.Row row = deck.row(hovered);
        centered(canvas, row.name(), cx, cy - 22, 0xFFFFFFFF, true);
        Rect strip = new Rect(cx - HotbarStripView.ROW_WIDTH / 2, cy - 9, HotbarStripView.ROW_WIDTH,
                HotbarStripView.ROW_HEIGHT);
        canvas.fill(strip, HotbarStripView.SELECTED_FILL);
        HotbarStripView.border(canvas, strip, 0xFF8A8A8A);
        for (int slot = 0; slot < row.slots().size(); slot++) {
            Rect cell = new Rect(strip.x() + 2 + slot * 20, strip.y() + 2, 18, 18);
            canvas.fill(cell, HotbarStripView.CELL_FILL);
            painter.slot(canvas, row.slots().get(slot), hovered, slot, cell.x() + 1, cell.y() + 1);
        }
        String hint;
        int color;
        switch (deck.mode()) {
            case DENIED -> {
                hint = deck.reason() == null ? texts.tr("gui.komaedit.hotbar.denied") : deck.reason();
                color = HotbarStripView.DENIED;
            }
            case REORDER -> {
                hint = texts.tr("gui.komaedit.hotbar.menu.use_survival");
                color = HotbarStripView.WARN;
            }
            case GIVE -> {
                hint = texts.tr("gui.komaedit.hotbar.menu.use_give");
                color = HotbarStripView.GIVE;
            }
            default -> {
                hint = texts.tr(hovered == deck.active() ? "gui.komaedit.hotbar.menu.in_use"
                        : "gui.komaedit.hotbar.menu.use");
                color = 0xFFB0B0B0;
            }
        }
        centered(canvas, hint, cx, strip.bottom() + 5, color, false);
    }

    private static void drawFooter(StudioCanvas canvas, Layout layout, Button hovered, String name, String status,
                                   boolean anything, HotbarTexts texts) {
        if (status != null && !status.isBlank()) {
            int width = canvas.width(status);
            int y = layout.nameField().y() - 13;
            canvas.fill(new Rect(layout.centerX() - width / 2 - 4, y - 2, width + 8, 12), 0xC0000000);
            centered(canvas, status, layout.centerX(), y, 0xFFFFE082, false);
        }
        Rect field = layout.nameField();
        canvas.fill(field, 0xFF000000);
        HotbarStripView.border(canvas, field, 0xFFA0A0A0);
        boolean blank = name == null || name.isEmpty();
        String text = blank ? texts.tr("gui.komaedit.hotbar.menu.name") : name;
        canvas.pushClip(field.inset(4, 0));
        canvas.text(text, field.x() + 4, field.y() + 5, blank ? 0xFF707070 : 0xFFE0E0E0);
        canvas.popClip();
        button(canvas, layout.save(), texts.tr("gui.komaedit.hotbar.menu.save"), hovered == Button.SAVE, true);
        button(canvas, layout.paste(), texts.tr("gui.komaedit.hotbar.menu.paste"), hovered == Button.PASTE, true);
        button(canvas, layout.copyAll(), texts.tr("gui.komaedit.hotbar.menu.copy_all"), hovered == Button.COPY_ALL,
                anything);
    }

    private static void button(StudioCanvas canvas, Rect rect, String label, boolean over, boolean enabled) {
        boolean lit = over && enabled;
        canvas.fill(rect, lit ? 0xF04A4A4A : 0xE0303030);
        HotbarStripView.border(canvas, rect, lit ? 0xFFFFFFFF : enabled ? 0xFF8A8A8A : 0xFF4A4A4A);
        String text = HotbarStripView.fit(canvas, label, rect.width() - 6, false);
        canvas.text(text, rect.centerX() - canvas.width(text) / 2, rect.y() + 5, enabled ? 0xFFFFFFFF : 0xFF707070);
    }

    private static void centered(StudioCanvas canvas, String text, int x, int y, int color, boolean bold) {
        if (bold) {
            canvas.bold(text, x - canvas.boldWidth(text) / 2, y, color);
        } else {
            canvas.text(text, x - canvas.width(text) / 2, y, color);
        }
    }

    private static double angle(int index, int spokes) {
        return -Math.PI / 2.0 + Math.PI * 2.0 * index / spokes;
    }
}
