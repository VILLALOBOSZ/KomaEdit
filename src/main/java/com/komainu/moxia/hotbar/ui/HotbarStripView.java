package com.komainu.moxia.hotbar.ui;

import com.komainu.moxia.hotbar.HotbarItem;
import com.komainu.moxia.hotbar.HotbarRules;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.ui.StudioCanvas;

import java.util.ArrayList;
import java.util.List;

/**
 * La tira de barras sobre la barra de verdad, mientras se mantiene la tecla: hasta {@link #VISIBLE} barras apiladas
 * con el tamaño y los huecos de la de Minecraft, la elegida con borde blanco, la que está en uso con una marca verde y
 * el nombre de cada una a la izquierda. Encima, qué pasará al soltar (en supervivencia, que solo se mueve lo que se
 * lleva). La dibuja el cliente en el HUD y las pruebas en una imagen, con el mismo código.
 */
public final class HotbarStripView {
    public static final int ROW_WIDTH = 182;
    public static final int ROW_HEIGHT = 22;
    public static final int GAP = 3;
    public static final int VISIBLE = 5;
    /** Hueco entre la tira y la barra de verdad: deja ver la experiencia, los corazones y el hambre. */
    static final int ABOVE_HOTBAR = 42;
    static final int ROW_FILL = 0xD8101010;
    static final int SELECTED_FILL = 0xE6202020;
    static final int CELL_FILL = 0x2EFFFFFF;
    static final int BORDER = 0xFF4E4E4E;
    static final int SELECTED_BORDER = 0xFFFFFFFF;
    static final int ACTIVE = 0xFF5BE38A;
    static final int NAME = 0xFFB8B8B8;
    static final int HINT = 0xFFDADADA;
    static final int WARN = 0xFFFFC857;
    static final int DENIED = 0xFFFF6B6B;
    static final int GIVE = 0xFFFF9F43;

    private HotbarStripView() {
    }

    /**
     * Dónde va cada cosa.
     *
     * @param first índice de la primera barra visible
     * @param rows  rectángulo de cada barra visible, de arriba abajo
     */
    public record Layout(int width, int height, int first, List<Rect> rows, boolean moreAbove, boolean moreBelow) {
        public int last() {
            return first + rows.size() - 1;
        }

        /** Arriba de la tira (el texto de ayuda va encima). */
        public int top() {
            return rows.isEmpty() ? height - ABOVE_HOTBAR - ROW_HEIGHT : rows.getFirst().y();
        }
    }

    /**
     * @param size     barras que hay
     * @param selected la elegida (se mantiene a la vista, centrada si se puede), o -1
     */
    public static Layout layout(int width, int height, int size, int selected) {
        int visible = Math.max(1, Math.min(VISIBLE, size));
        int first = size <= visible ? 0 : Math.clamp(Math.max(selected, 0) - visible / 2, 0, size - visible);
        int x = width / 2 - ROW_WIDTH / 2;
        int bottom = height - ABOVE_HOTBAR;
        List<Rect> rows = new ArrayList<>(visible);
        for (int index = 0; index < visible; index++) {
            int y = bottom - (visible - index) * (ROW_HEIGHT + GAP) + GAP;
            rows.add(new Rect(x, y, ROW_WIDTH, ROW_HEIGHT));
        }
        return new Layout(width, height, first, List.copyOf(rows), first > 0, first + visible < size);
    }

    public static void draw(StudioCanvas canvas, Layout layout, HotbarDeck deck, int selected, HotbarPainter painter,
                            HotbarTexts texts) {
        Rect firstRow = layout.rows().getFirst();
        int nameSpace = Math.min(120, firstRow.x() - 12);
        String hint = hint(deck, texts);
        int hintWidth = canvas.width(hint);
        // Centrado en la pantalla, como la barra: ancho para los nombres (izquierda) o la posición (derecha).
        int center = layout.width() / 2;
        int half = Math.max(hintWidth / 2 + 6, deck.isEmpty() ? ROW_WIDTH / 2 + 8
                : Math.max(center - (firstRow.x() - nameSpace - 8), firstRow.right() + 40 - center));
        int left = Math.max(0, center - half);
        Rect backdrop = new Rect(left, layout.top() - 17, Math.min(layout.width(), center + half) - left,
                layout.rows().getLast().bottom() - layout.top() + 21);
        canvas.fill(backdrop, 0x70000000);
        canvas.text(hint, layout.width() / 2 - hintWidth / 2, layout.top() - 12, hintColor(deck));

        if (deck.isEmpty()) {
            Rect row = firstRow;
            canvas.fill(row, ROW_FILL);
            border(canvas, row, BORDER);
            String empty = texts.tr("gui.komaedit.hotbar.strip.empty");
            canvas.text(empty, row.centerX() - canvas.width(empty) / 2, row.y() + 7, NAME);
            return;
        }
        for (int index = 0; index < layout.rows().size(); index++) {
            int deckIndex = layout.first() + index;
            Rect row = layout.rows().get(index);
            HotbarDeck.Row data = deck.row(deckIndex);
            boolean chosen = deckIndex == selected;
            boolean active = deckIndex == deck.active();
            canvas.fill(row, chosen ? SELECTED_FILL : ROW_FILL);
            if (chosen) {
                border(canvas, new Rect(row.x() - 1, row.y() - 1, row.width() + 2, row.height() + 2), 0x66FFFFFF);
            }
            border(canvas, row, chosen ? SELECTED_BORDER : BORDER);
            for (int slot = 0; slot < data.slots().size(); slot++) {
                Rect cell = new Rect(row.x() + 2 + slot * 20, row.y() + 2, 18, 18);
                canvas.fill(cell, CELL_FILL);
                HotbarItem item = data.slots().get(slot);
                painter.slot(canvas, item, deckIndex, slot, cell.x() + 1, cell.y() + 1);
            }
            if (active) {
                canvas.fill(new Rect(row.x() - 5, row.y() + 3, 2, row.height() - 6), ACTIVE);
            }
            String name = fit(canvas, data.name(), nameSpace, chosen);
            int nameX = row.x() - 9 - (chosen ? canvas.boldWidth(name) : canvas.width(name));
            if (chosen) {
                canvas.bold(name, nameX, row.y() + 7, 0xFFFFFFFF);
            } else {
                canvas.text(name, nameX, row.y() + 7, active ? 0xFF8FE3A8 : NAME);
            }
            if (chosen) {
                String position = (deckIndex + 1) + "/" + deck.size();
                canvas.text(position, row.right() + 7, row.y() + 7, NAME);
            }
        }
        if (layout.moreAbove()) {
            arrow(canvas, layout.rows().getFirst(), true);
        }
        if (layout.moreBelow()) {
            arrow(canvas, layout.rows().getLast(), false);
        }
    }

    /** Qué pasará al soltar, según el modo de juego. */
    static String hint(HotbarDeck deck, HotbarTexts texts) {
        if (deck.mode() == HotbarRules.Mode.DENIED) {
            return deck.reason() == null ? texts.tr("gui.komaedit.hotbar.denied") : deck.reason();
        }
        if (deck.isEmpty()) {
            return texts.tr("gui.komaedit.hotbar.strip.hint_empty");
        }
        return texts.tr(switch (deck.mode()) {
            case REORDER -> "gui.komaedit.hotbar.strip.survival";
            case GIVE -> "gui.komaedit.hotbar.strip.give";
            default -> "gui.komaedit.hotbar.strip.hint";
        });
    }

    private static int hintColor(HotbarDeck deck) {
        return switch (deck.mode()) {
            case DENIED -> DENIED;
            case REORDER -> WARN;
            case GIVE -> GIVE;
            case SET -> HINT;
        };
    }

    /** El nombre recortado con «…» si no cabe. */
    static String fit(StudioCanvas canvas, String text, int width, boolean bold) {
        if (measure(canvas, text, bold) <= width) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && measure(canvas, cut + "…", bold) > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    private static int measure(StudioCanvas canvas, String text, boolean bold) {
        return bold ? canvas.boldWidth(text) : canvas.width(text);
    }

    static void border(StudioCanvas canvas, Rect rect, int color) {
        canvas.fill(new Rect(rect.x(), rect.y(), rect.width(), 1), color);
        canvas.fill(new Rect(rect.x(), rect.bottom() - 1, rect.width(), 1), color);
        canvas.fill(new Rect(rect.x(), rect.y(), 1, rect.height()), color);
        canvas.fill(new Rect(rect.right() - 1, rect.y(), 1, rect.height()), color);
    }

    /** Un triángulo pequeño a la derecha de la fila: quedan barras por arriba o por abajo. */
    private static void arrow(StudioCanvas canvas, Rect row, boolean up) {
        int x = row.right() + 30;
        int y = up ? row.y() + 2 : row.bottom() - 6;
        for (int line = 0; line < 4; line++) {
            int half = up ? line : 3 - line;
            canvas.fill(new Rect(x - half, y + line, half * 2 + 1, 1), NAME);
        }
    }
}
