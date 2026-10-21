package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.ShapeSearch;
import com.komainu.moxia.studio.layout.Rect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.komainu.moxia.studio.ui.StudioTheme.*;

/**
 * Paleta de órdenes ({@code Ctrl+K}): un buscador para llegar a cualquier etapa, herramienta, preset, figura o plano
 * sin buscarlo en los menús. Las flechas eligen, Intro lo abre y Esc cierra. Busca como el resto del Studio: sin
 * mayúsculas ni tildes, cada palabra tiene que aparecer y el nombre pesa más que el tipo.
 */
public final class CommandPalette {
    public static final int VISIBLE = 8;
    private static final int ROW = 18;

    /** Lo que se puede abrir desde la paleta. */
    public enum Kind {
        STAGE, TOOL, PRESET, FIGURE, BLUEPRINT, THEME;

        public String translationKey() {
            return "gui.komaedit.palette.kind." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Una entrada: su nombre, el detalle de la derecha (tipo o tecla), el icono y qué hace.
     *
     * @param keywords más palabras que la encuentran (ids, nombre de la herramienta de un preset…)
     */
    public record Entry(Kind kind, String title, String detail, String keywords, StudioIcons.Icon icon,
                       Runnable action) {
    }

    private boolean open;
    private String query = "";
    private int cursor;
    private int offset;
    private List<Entry> entries = List.of();

    public boolean isOpen() {
        return open;
    }

    public void open(List<Entry> available) {
        open = true;
        query = "";
        cursor = 0;
        offset = 0;
        entries = List.copyOf(available);
    }

    public void close() {
        open = false;
    }

    public String query() {
        return query;
    }

    public int cursor() {
        return cursor;
    }

    /** Las entradas que encajan con lo escrito, de mejor a peor; sin nada escrito, todas en su orden. */
    public List<Entry> results() {
        return search(entries, query);
    }

    /** Busca en {@code entries}: cada palabra tiene que aparecer en el nombre, el detalle o las palabras clave. */
    public static List<Entry> search(List<Entry> entries, String query) {
        String normalized = ShapeSearch.normalize(query);
        if (normalized.isEmpty()) {
            return entries;
        }
        record Scored(Entry entry, int score, int order) {
        }
        List<Scored> scored = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            String title = ShapeSearch.normalize(entry.title());
            String extra = ShapeSearch.normalize(entry.detail() + " " + entry.keywords());
            int total = 0;
            for (String token : normalized.split(" ")) {
                int best = title.equals(token) ? 130 : title.startsWith(token) ? 120
                        : title.contains(" " + token) ? 110 : title.contains(token) ? 90
                        : extra.contains(token) ? 40 : 0;
                if (best == 0) {
                    total = 0;
                    break;
                }
                total += best;
            }
            if (total > 0) {
                // A igualdad, primero etapas y herramientas, luego presets, figuras y planos.
                scored.add(new Scored(entry, total * 10 - entry.kind().ordinal(), index));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparingInt(Scored::order));
        return scored.stream().map(Scored::entry).toList();
    }

    /** Ejecuta la entrada elegida (o la primera) y cierra. Devuelve si había alguna. */
    public boolean run() {
        List<Entry> results = results();
        if (results.isEmpty()) {
            return false;
        }
        Entry entry = results.get(Math.clamp(cursor, 0, results.size() - 1));
        close();
        entry.action().run();
        return true;
    }

    public static Rect bounds(Rect screen) {
        int width = Math.min(300, screen.width() - 24);
        int height = 30 + VISIBLE * ROW + 16;
        int top = screen.y() + Math.max(8, (screen.height() - height) / 4);
        return new Rect(screen.x() + (screen.width() - width) / 2, top, width, Math.min(height, screen.height() - 16));
    }

    public void draw(StudioUi ui, Rect screen, StudioHost host) {
        Rect modal = bounds(screen);
        ui.fill(screen, SCRIM);
        ui.register(screen).onClick(this::close);
        ui.enableHover();
        ui.fill(modal, PANEL);
        ui.border(modal, DIVIDER);
        ui.fill(new Rect(modal.x(), modal.y(), 2, modal.height()), ACCENT);
        ui.register(modal);
        List<Entry> results = results();
        Rect field = new Rect(modal.x() + 8, modal.y() + 7, modal.width() - 16, 18);
        ui.searchField(field, query, host.tr("gui.komaedit.palette.placeholder"), true, results.size(), () -> {
            query = "";
            cursor = 0;
        });
        int rows = Math.max(1, (modal.height() - 30 - 16) / ROW);
        cursor = Math.clamp(cursor, 0, Math.max(0, results.size() - 1));
        if (cursor < offset) {
            offset = cursor;
        } else if (cursor >= offset + rows) {
            offset = cursor - rows + 1;
        }
        offset = Math.clamp(offset, 0, Math.max(0, results.size() - rows));
        int y = field.bottom() + 4;
        if (results.isEmpty()) {
            ui.label(host.tr("gui.komaedit.search.empty"), modal.x() + 12, y + 5, modal.width() - 24, MUTED);
        }
        for (int index = offset; index < Math.min(results.size(), offset + rows); index++) {
            Entry entry = results.get(index);
            Rect row = new Rect(modal.x() + 4, y, modal.width() - 8, ROW - 1);
            boolean chosen = index == cursor;
            boolean over = ui.hovered(row);
            ui.fill(row, chosen ? CARD_ACTIVE : over ? CARD_HOVER : PANEL);
            if (chosen) {
                ui.fill(row.left(2), ACCENT);
            }
            ui.icon(entry.icon(), row.x() + 6, row.centerY() - 6, chosen ? ACCENT_TEXT : TEXT_SECONDARY);
            int detailWidth = ui.width(entry.detail());
            ui.label(entry.title(), row.x() + 24, row.centerY() - 4, row.width() - 34 - detailWidth,
                    chosen ? TEXT_STRONG : TEXT);
            ui.rightAligned(entry.detail(), row.right() - 6, row.centerY() - 4, MUTED);
            int position = index;
            ui.register(row).onClick(() -> {
                cursor = position;
                run();
            });
            y += ROW;
        }
        ui.label(host.tr("gui.komaedit.palette.hint"), modal.x() + 10, modal.bottom() - 12, modal.width() - 20, MUTED);
    }

    public boolean key(StudioKey key) {
        int size = results().size();
        switch (key.code()) {
            case ESCAPE -> close();
            case ENTER -> run();
            case DOWN -> cursor = size == 0 ? 0 : (cursor + 1) % size;
            case UP -> cursor = size == 0 ? 0 : Math.floorMod(cursor - 1, size);
            case PAGE_DOWN -> cursor = Math.min(Math.max(0, size - 1), cursor + VISIBLE);
            case PAGE_UP -> cursor = Math.max(0, cursor - VISIBLE);
            case BACKSPACE -> {
                if (!query.isEmpty()) {
                    query = key.control() ? "" : query.substring(0, query.length() - 1);
                    cursor = 0;
                }
            }
            default -> {
            }
        }
        return true;
    }

    public boolean type(char character) {
        if (character >= 32 && character != 127 && query.length() < 48) {
            query += character;
            cursor = 0;
        }
        return true;
    }
}
