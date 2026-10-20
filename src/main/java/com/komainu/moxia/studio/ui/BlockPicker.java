package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.ShapeSearch;
import com.komainu.moxia.studio.layout.Rect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.komainu.moxia.studio.ui.StudioTheme.*;

/**
 * La biblioteca de bloques del Studio, en una ventana sobre el panel: buscador por nombre traducido y por id, el
 * bloque que se mira (cuentagotas), la barra rápida del jugador, los elegidos hace poco y todos los bloques en una
 * rejilla de iconos. Intro elige el primero de la búsqueda; Esc cierra.
 */
public final class BlockPicker {
    private static final int CELL = 22;
    private static final int STEP = 24;

    private boolean open;
    private String title = "";
    private String current;
    private Consumer<String> onPick;
    private String search = "";
    private int scroll;
    private String cachedQuery;
    private List<String> cachedMatches = List.of();
    private final Map<String, String> names = new HashMap<>();

    public boolean isOpen() {
        return open;
    }

    /** Abre la ventana: {@code title} dice para qué es, {@code current} se marca y {@code onPick} recibe el id. */
    public void open(String title, String current, Consumer<String> onPick) {
        this.open = true;
        this.title = title == null ? "" : title;
        this.current = current;
        this.onPick = onPick;
        this.search = "";
        this.scroll = 0;
    }

    public void close() {
        open = false;
        onPick = null;
    }

    public String search() {
        return search;
    }

    /** La ventana centrada en la pantalla, como mucho de 340 × 300. */
    public static Rect bounds(Rect screen) {
        int width = Math.min(340, screen.width() - 24);
        int height = Math.min(300, screen.height() - 20);
        return new Rect(screen.x() + (screen.width() - width) / 2, screen.y() + (screen.height() - height) / 2,
                width, height);
    }

    /** Los bloques que encajan con lo escrito (por nombre traducido o por id), en el orden del registro. */
    public List<String> matches(StudioHost host) {
        if (search.equals(cachedQuery)) {
            return cachedMatches;
        }
        cachedQuery = search;
        String query = ShapeSearch.normalize(search);
        if (query.isEmpty()) {
            cachedMatches = host.allBlocks();
            return cachedMatches;
        }
        List<String> starts = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (String id : host.allBlocks()) {
            String name = ShapeSearch.normalize(names.computeIfAbsent(id, host::blockName));
            String path = ShapeSearch.normalize(id.substring(id.indexOf(':') + 1).replace('_', ' '));
            boolean all = true;
            for (String token : query.split(" ")) {
                if (!name.contains(token) && !path.contains(token)) {
                    all = false;
                    break;
                }
            }
            if (!all) {
                continue;
            }
            (name.startsWith(query) || path.startsWith(query) ? starts : contains).add(id);
        }
        starts.addAll(contains);
        cachedMatches = starts;
        return cachedMatches;
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
        int x = modal.x() + 10;
        int width = modal.width() - 20;
        Rect close = new Rect(modal.right() - 22, modal.y() + 4, 18, 18);
        ui.bold(title.isEmpty() ? host.tr("gui.komaedit.blocks.title") : title, x, modal.y() + 9, width - 24, WHITE);
        ui.toolButton(close, StudioIcons.Icon.CLOSE, false, true).tooltip(host.tr("gui.komaedit.blocks.close"))
                .onClick(this::close);

        List<String> matches = matches(host);
        Rect field = new Rect(x, modal.y() + 24, width, 18);
        ui.searchField(field, search, host.tr("gui.komaedit.blocks.search"), true, matches.size(), () -> {
            search = "";
            scroll = 0;
        });
        int y = field.bottom() + 6;

        String looked = host.lookedAtBlock();
        Rect lookedRow = new Rect(x, y, width, 20);
        boolean lookedHover = looked != null && ui.hovered(lookedRow);
        ui.fill(lookedRow, lookedHover ? CARD_HOVER : CARD);
        ui.icon(StudioIcons.Icon.EYEDROPPER, lookedRow.x() + 4, lookedRow.centerY() - 6, looked == null ? DISABLED
                : ACCENT_HOVER);
        if (looked != null) {
            ui.item(looked, lookedRow.x() + 20, lookedRow.y() + 2);
            ui.label(host.tr("gui.komaedit.blocks.looking", host.blockName(looked)), lookedRow.x() + 40,
                    lookedRow.centerY() - 4, lookedRow.width() - 44, lookedHover ? TEXT : TEXT_SECONDARY);
            ui.register(lookedRow).tooltip(looked).onClick(() -> pick(host, looked));
        } else {
            ui.label(host.tr("gui.komaedit.blocks.look_nothing"), lookedRow.x() + 20, lookedRow.centerY() - 4,
                    lookedRow.width() - 24, MUTED);
        }
        y = lookedRow.bottom() + 4;

        String hovered = null;
        if (search.isBlank()) {
            List<String> hotbar = host.hotbarBlocks();
            if (hotbar.stream().anyMatch(id -> id != null && !id.isEmpty())) {
                ui.label(host.tr("gui.komaedit.blocks.hotbar"), x, y + 6, 70, MUTED);
                hovered = strip(ui, hotbar, x + 74, y, width - 74, host, hovered);
                y += STEP;
            }
            List<String> recents = host.preferences().blockRecents();
            if (!recents.isEmpty()) {
                ui.label(host.tr("gui.komaedit.recents"), x, y + 6, 70, MUTED);
                hovered = strip(ui, recents, x + 74, y, width - 74, host, hovered);
                y += STEP;
            }
        }
        ui.label(host.tr("gui.komaedit.blocks.count", matches.size()), x, y + 2, width, MUTED);
        y += 12;
        Rect grid = new Rect(x, y, width, modal.bottom() - 18 - y);
        int columns = Math.max(4, (grid.width() + 2) / STEP);
        int rows = Math.max(1, grid.height() / STEP);
        int maximum = Math.max(0, (matches.size() + columns - 1) / columns - rows);
        scroll = Math.clamp(scroll, 0, maximum);
        int start = scroll * columns;
        int end = Math.min(matches.size(), start + rows * columns);
        for (int index = start; index < end; index++) {
            int local = index - start;
            Rect cell = new Rect(grid.x() + local % columns * STEP, grid.y() + local / columns * STEP, CELL, CELL);
            hovered = cell(ui, cell, matches.get(index), host, hovered);
        }
        if (maximum > 0) {
            int track = grid.height();
            int thumb = Math.max(12, track * rows / Math.max(rows, rows + maximum));
            int thumbY = grid.y() + (track - thumb) * scroll / maximum;
            ui.fill(new Rect(modal.right() - 6, grid.y(), 2, track), DIVIDER);
            ui.fill(new Rect(modal.right() - 6, thumbY, 2, thumb), ACCENT_DIM);
        }
        ui.register(grid).onScroll(direction -> scroll = Math.clamp(scroll - direction, 0, maximum));
        String footer = hovered != null ? host.blockName(hovered) + " · " + hovered : host.tr("gui.komaedit.blocks.hint");
        ui.label(footer, x, modal.bottom() - 13, width, hovered != null ? TEXT : MUTED);
    }

    private String strip(StudioUi ui, List<String> ids, int x, int y, int width, StudioHost host, String hovered) {
        int count = Math.min(ids.size(), Math.max(1, (width + 2) / STEP));
        for (int index = 0; index < count; index++) {
            String id = ids.get(index);
            Rect cell = new Rect(x + index * STEP, y, CELL, CELL);
            if (id == null || id.isEmpty()) {
                ui.border(cell, DIVIDER);
                continue;
            }
            hovered = cell(ui, cell, id, host, hovered);
        }
        return hovered;
    }

    private String cell(StudioUi ui, Rect cell, String id, StudioHost host, String hovered) {
        boolean over = ui.hovered(cell);
        boolean selected = id.equals(current);
        ui.fill(cell, selected ? CARD_ACTIVE : over ? CARD_HOVER : CARD);
        if (selected) {
            ui.border(cell, ACCENT);
        }
        ui.item(id, cell.x() + 3, cell.y() + 3);
        ui.register(cell).tooltip(host.blockName(id) + "\n" + id).onClick(() -> pick(host, id));
        return over ? id : hovered;
    }

    private void pick(StudioHost host, String id) {
        host.preferences().pushBlockRecent(id);
        host.savePreferences();
        Consumer<String> target = onPick;
        close();
        if (target != null) {
            target.accept(id);
        }
    }

    public boolean key(StudioKey key, StudioHost host) {
        switch (key.code()) {
            case ESCAPE -> close();
            case ENTER -> {
                List<String> matches = matches(host);
                if (!search.isBlank() && !matches.isEmpty()) {
                    pick(host, matches.getFirst());
                }
            }
            case BACKSPACE -> {
                if (!search.isEmpty()) {
                    search = key.control() ? "" : search.substring(0, search.length() - 1);
                    scroll = 0;
                }
            }
            case PAGE_DOWN, DOWN -> scroll++;
            case PAGE_UP, UP -> scroll = Math.max(0, scroll - 1);
            default -> {
            }
        }
        return true;
    }

    public boolean type(char character) {
        if (character >= 32 && character != 127 && search.length() < 48) {
            search += character;
            scroll = 0;
        }
        return true;
    }
}
