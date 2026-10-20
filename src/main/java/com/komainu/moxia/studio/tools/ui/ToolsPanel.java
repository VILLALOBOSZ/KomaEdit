package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.capability.BuildCapability;
import com.komainu.moxia.studio.NumericRange;
import com.komainu.moxia.studio.ParameterHistory;
import com.komainu.moxia.studio.ShapeSearch;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.tools.CapabilityCard;
import com.komainu.moxia.studio.tools.SymmetryCard;
import com.komainu.moxia.studio.tools.ToolAction;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ToolFamily;
import com.komainu.moxia.studio.tools.ToolParam;
import com.komainu.moxia.studio.tools.ToolPreset;
import com.komainu.moxia.studio.tools.ToolProblem;
import com.komainu.moxia.studio.tools.ToolTarget;
import com.komainu.moxia.studio.tools.ToolValues;
import com.komainu.moxia.terrain.StampLibrary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Consumer;

/**
 * Etapa «Herramientas» del Studio: buscador, familias, favoritas y recientes de las herramientas y, debajo, la
 * tarjeta elegida con sus presets, sus ajustes, sus acciones y la orden que mandará. Construir (en el pie) manda esa
 * orden por el chat, igual que si se escribiera; el servidor la comprueba con sus permisos.
 *
 * <p>No depende de Minecraft: dibuja a través de {@link ToolsUi} y pide lo demás a su {@link Host}, así que la
 * misma clase la pinta el juego, la prueban los tests y la dibujan las maquetas.</p>
 */
public final class ToolsPanel {
    /** Lo que la etapa necesita de la pantalla. */
    public interface Host {
        String tr(String key, Object... arguments);

        /** Manda una orden por el chat, sin barra. */
        void send(String command);

        /** Abre la biblioteca de bloques y entrega el elegido. */
        void pickBlock(Consumer<String> onPick);

        void status(String text);

        void copy(String text);

        /** Lleva a la etapa Trayectoria (cuando lo que falta son puntos). */
        void goToPath();

        boolean shift();

        /** Dónde se pulsó por última vez: el arrastre de un número parte de ahí. */
        double mouseX();

        long now();

        ToolContext context();

        ToolsInfo info();

        /** Pide al servidor el estado de las herramientas. */
        void requestState();

        StudioPreferences preferences();

        void savePreferences();
    }

    public enum Key {
        ENTER, ESCAPE, BACKSPACE, OTHER
    }

    private enum Field {
        NONE, SEARCH, TEXT, NUMBER, PRESET_NAME, BIOME
    }

    /** Un estado del historial de ajustes: la tarjeta y sus valores. */
    public record Snapshot(String card, ToolValues values) {
    }

    public static final String ALL = "all";
    public static final String FAVORITES = "favorites";
    public static final long REFRESH_MILLIS = 1_000L;
    /** Los números de las capacidades se mandan cuando se dejan de tocar, para no llenar el chat. */
    public static final long OPTION_DELAY_MILLIS = 600L;
    private static final int GALLERY_CELL = 34;
    private static final int BIOME_ROWS = 6;
    private static final int SMALL_ROW = 16;
    private static final SplittableRandom SEEDS = new SplittableRandom();

    private final Map<String, ToolValues> values = new HashMap<>();
    private final ParameterHistory<Snapshot> history = new ParameterHistory<>(100);
    private final Map<String, Integer> pendingOptions = new LinkedHashMap<>();
    private ToolCard selected = ToolCatalog.ALL.getFirst();
    private String filter = ALL;
    private String search = "";
    private Field field = Field.NONE;
    private String fieldParam = "";
    private String input = "";
    private String biomeSearch = "";
    private ToolAction namingAction;
    private String dragParam;
    private double dragStartX;
    private int dragStartValue;
    private boolean dragMoved;
    private long lastRequest = Long.MIN_VALUE / 2;
    private long pendingSince;
    private String syncedSymmetry = "";
    private boolean restoring;

    // ── Estado ──────────────────────────────────────────────────────────────────────────────────────────

    public ToolCard selected() {
        return selected;
    }

    public ToolValues values(ToolCard card) {
        return values.computeIfAbsent(card.id(), id -> card.defaults());
    }

    public void setValues(ToolCard card, ToolValues next) {
        values.put(card.id(), next);
    }

    private ToolValues current() {
        return values(selected);
    }

    private void set(ToolValues next) {
        values.put(selected.id(), next);
    }

    public void select(ToolCard card, Host host) {
        if (card != selected) {
            stopEditing();
            selected = card;
            namingAction = null;
            biomeSearch = "";
        }
        host.preferences().pushToolRecent(card.id());
        host.savePreferences();
        host.status(host.tr("gui.komaedit.tools.status.selected", host.tr(card.nameKey())));
    }

    public String filter() {
        return filter;
    }

    public String search() {
        return search;
    }

    public void setFilter(String value) {
        filter = value;
        search = "";
    }

    /** Pide el estado al servidor cada segundo mientras la etapa se ve, y manda los números pendientes. */
    public void tick(Host host) {
        long now = host.now();
        if (now - lastRequest >= REFRESH_MILLIS) {
            lastRequest = now;
            host.requestState();
        }
        flushOptions(host, false);
    }

    /** Fuerza una petición de estado en el próximo {@link #tick} (al volver a la etapa, tras una orden). */
    public void refreshSoon() {
        lastRequest = Long.MIN_VALUE / 2;
    }

    // ── Historial de ajustes ────────────────────────────────────────────────────────────────────────────

    public Snapshot snapshot() {
        return new Snapshot(selected.id(), current());
    }

    /** Anota en el historial lo que cambió una acción; {@code group} funde los cambios seguidos del mismo control. */
    public void track(Snapshot before, String group, long now) {
        if (restoring) {
            restoring = false;
            return;
        }
        if (before.card().equals(selected.id()) && !before.values().equals(current())) {
            history.record(before, group, now);
        }
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public boolean undo(Host host) {
        return restore(history.undo(snapshot()), host, "gui.komaedit.history.undone",
                "gui.komaedit.history.nothing_undo");
    }

    public boolean redo(Host host) {
        return restore(history.redo(snapshot()), host, "gui.komaedit.history.redone",
                "gui.komaedit.history.nothing_redo");
    }

    private boolean restore(Snapshot snapshot, Host host, String done, String empty) {
        if (snapshot == null) {
            host.status(host.tr(empty));
            return false;
        }
        ToolCard card = ToolCatalog.byId(snapshot.card());
        if (card != null) {
            selected = card;
            values.put(card.id(), snapshot.values());
        }
        stopEditing();
        restoring = true;
        host.status(host.tr(done));
        return true;
    }

    // ── Dibujo ──────────────────────────────────────────────────────────────────────────────────────────

    public void draw(ToolsUi ui, Column column, Host host) {
        ToolContext context = host.context();
        ToolsInfo info = host.info();
        syncSymmetry(info);
        drawBrowser(ui, column, host, context);
        drawDetail(ui, column, host, context, info);
    }

    /** Las herramientas que se ven con el buscador y la familia elegidos. */
    public List<ToolCard> visibleCards(Host host) {
        if (!search.isBlank()) {
            return ToolCatalog.search(ToolCatalog.ALL, search, card -> host.tr(card.nameKey()),
                    card -> host.tr(card.descriptionKey()), family -> host.tr(family.translationKey()));
        }
        if (filter.equals(FAVORITES)) {
            return ToolCatalog.ALL.stream().filter(card -> host.preferences().isToolFavorite(card.id())).toList();
        }
        for (ToolFamily family : ToolFamily.values()) {
            if (family.id().equals(filter)) {
                return ToolCatalog.family(family);
            }
        }
        return ToolCatalog.ALL;
    }

    private void drawBrowser(ToolsUi ui, Column column, Host host, ToolContext context) {
        Rect box = column.next(18);
        boolean typing = field == Field.SEARCH;
        ui.field(box, search, host.tr("gui.komaedit.tools.search"), typing, false)
                .key("tools:search")
                .tooltip(host.tr("gui.komaedit.tools.search.help"))
                .onClick(() -> {
                    field = Field.SEARCH;
                    host.status(host.tr("gui.komaedit.search.typing"));
                });
        List<ToolCard> shown = visibleCards(host);
        if (!search.isEmpty()) {
            Rect clear = box.rightPart(16);
            ui.label(Integer.toString(shown.size()), clear.x() - 4 - ui.width(Integer.toString(shown.size())),
                    box.centerY() - 4, 40, ToolsUi.Ink.MUTED);
            ui.centered("×", clear, ToolsUi.Ink.TEXT);
            ui.register(clear).onClick(() -> {
                search = "";
                stopEditing();
            });
        }

        List<String> filters = new ArrayList<>(List.of(ALL, FAVORITES));
        for (ToolFamily family : ToolFamily.values()) {
            filters.add(family.id());
        }
        String[] labels = new String[filters.size()];
        int[] widths = new int[filters.size()];
        for (int index = 0; index < filters.size(); index++) {
            labels[index] = filterLabel(filters.get(index), host);
            widths[index] = ui.chipWidth(labels[index]);
        }
        List<Rect> chips = column.flow(widths, 14);
        for (int index = 0; index < filters.size(); index++) {
            String value = filters.get(index);
            ui.chip(chips.get(index), labels[index], value.equals(filter) && search.isBlank(),
                            value.equals(FAVORITES) ? ToolsUi.Ink.FAVORITE : ToolsUi.Ink.TEXT_SECONDARY)
                    .onClick(() -> setFilter(value));
        }

        if (search.isBlank() && filter.equals(ALL)) {
            drawRecents(ui, column, host);
        }

        int columns = column.width() >= 300 ? 3 : 2;
        List<Rect> cells = column.grid(Math.max(1, shown.size()), columns, SMALL_ROW);
        if (shown.isEmpty()) {
            Rect empty = new Rect(column.x(), cells.getFirst().y(), column.width(), SMALL_ROW);
            ui.fill(empty, ToolsUi.Ink.CARD);
            ui.label(host.tr(filter.equals(FAVORITES) && search.isBlank() ? "gui.komaedit.tools.favorites.empty"
                    : "gui.komaedit.tools.search.empty"), empty.x() + 8, empty.y() + 4, empty.width() - 16,
                    ToolsUi.Ink.MUTED);
        }
        for (int index = 0; index < shown.size(); index++) {
            drawToolCell(ui, cells.get(index), shown.get(index), host, context);
        }
    }

    private String filterLabel(String value, Host host) {
        return switch (value) {
            case ALL -> host.tr("gui.komaedit.tools.family.all");
            case FAVORITES -> "★ " + host.tr("gui.komaedit.tools.family.favorites");
            default -> host.tr("gui.komaedit.tools.family." + value);
        };
    }

    private void drawRecents(ToolsUi ui, Column column, Host host) {
        List<ToolCard> recents = new ArrayList<>();
        for (String id : host.preferences().toolRecents()) {
            ToolCard card = ToolCatalog.byId(id);
            if (card != null && recents.size() < 4) {
                recents.add(card);
            }
        }
        if (recents.isEmpty()) {
            return;
        }
        String recentLabel = host.tr("gui.komaedit.recents");
        int labelWidth = ui.width(recentLabel) + 6;
        int[] widths = new int[recents.size()];
        for (int index = 0; index < recents.size(); index++) {
            widths[index] = Math.min(column.width() / 2, ui.chipWidth(host.tr(recents.get(index).nameKey())));
        }
        List<Rect> placed = Column.flow(widths, 14, column.x() + labelWidth, column.y(), column.width() - labelWidth);
        ui.label(recentLabel, column.x(), column.y() + 3, labelWidth, ToolsUi.Ink.MUTED);
        int bottom = column.y();
        for (int index = 0; index < recents.size(); index++) {
            ToolCard card = recents.get(index);
            Rect chip = placed.get(index);
            ui.chip(chip, host.tr(card.nameKey()), card == selected, ToolsUi.Ink.TEXT_SECONDARY)
                    .onClick(() -> select(card, host));
            bottom = Math.max(bottom, chip.bottom());
        }
        column.space(bottom - column.y() + Column.GAP);
    }

    private void drawToolCell(ToolsUi ui, Rect cell, ToolCard card, Host host, ToolContext context) {
        if (!ui.visible(cell)) {
            return;
        }
        boolean hovered = ui.hovered(cell);
        boolean chosen = card == selected;
        boolean locked = card.locked(context);
        ui.fill(cell, chosen ? ToolsUi.Ink.CARD_ACTIVE : hovered ? ToolsUi.Ink.CARD_HOVER : ToolsUi.Ink.CARD);
        if (chosen) {
            ui.fill(cell.left(2), ToolsUi.Ink.ACCENT);
        }
        Rect star = cell.rightPart(14);
        int reserve = locked ? 26 : 16;
        ui.label(host.tr(card.nameKey()), cell.x() + 7, cell.centerY() - 4, cell.width() - 7 - reserve,
                locked ? ToolsUi.Ink.DISABLED : chosen ? ToolsUi.Ink.TEXT : ToolsUi.Ink.TEXT_SECONDARY);
        if (locked) {
            ui.lock(star.x() - 9, cell.centerY() - 4, ToolsUi.Ink.DISABLED);
        }
        boolean favorite = host.preferences().isToolFavorite(card.id());
        if (favorite || hovered) {
            ui.label(favorite ? "★" : "☆", star.x() + 2, cell.centerY() - 4, 12,
                    favorite || ui.hovered(star) ? ToolsUi.Ink.FAVORITE : ToolsUi.Ink.MUTED);
        }
        StringBuilder help = new StringBuilder(host.tr(card.nameKey())).append('\n')
                .append(host.tr(card.descriptionKey()));
        if (locked) {
            help.append('\n').append(host.tr("gui.komaedit.tools.locked",
                    String.join(", ", card.allPermissions())));
        }
        help.append('\n').append(host.tr("gui.komaedit.help.favorite_star"));
        ui.register(cell).onClick(() -> select(card, host)).tooltip(help.toString());
        ui.register(star).onClick(() -> {
            boolean added = host.preferences().toggleToolFavorite(card.id());
            host.savePreferences();
            host.status(host.tr(added ? "gui.komaedit.favorite.added" : "gui.komaedit.favorite.removed",
                    host.tr(card.nameKey())));
        }).tooltip(help.toString());
    }

    private void drawDetail(ToolsUi ui, Column column, Host host, ToolContext context, ToolsInfo info) {
        ToolCard card = selected;
        ToolValues values = current();
        column.section();
        Rect title = column.next(10);
        String family = host.tr(card.family().translationKey());
        int familyWidth = ui.width(family) + 6;
        ui.bold(host.tr(card.nameKey()), title.x(), title.y() + 1, title.width() - familyWidth,
                ToolsUi.Ink.ACCENT_HOVER);
        ui.label(family, title.right() - familyWidth + 6, title.y() + 1, familyWidth, ToolsUi.Ink.MUTED);
        ui.paragraph(column, host.tr(card.descriptionKey()), ToolsUi.Ink.MUTED, 3);
        ui.paragraph(column, target(card, host, context, info), ToolsUi.Ink.TEXT_SECONDARY, 2);

        List<ToolProblem> problems = card.problems(values, context);
        for (ToolProblem problem : problems.subList(0, Math.min(2, problems.size()))) {
            drawProblem(ui, column, host, problem);
        }

        if (card instanceof SymmetryCard) {
            drawSymmetryState(ui, column, host, info);
        }
        if (card instanceof CapabilityCard) {
            drawCapabilities(ui, column, host, info);
            drawCommand(ui, column, host, card, values);
            return;
        }

        drawPresets(ui, column, host, card, values, info);

        ui.section(column, host.tr("gui.komaedit.tools.settings"));
        for (ToolParam param : card.params()) {
            if (param.visible(values)) {
                drawParam(ui, column, host, card, param, info);
            }
        }
        if (card.previewable()) {
            boolean preview = host.preferences().toolPreview();
            ui.toggle(column.next(), host.tr("gui.komaedit.tools.preview", card.previewFlag()), preview)
                    .tooltip(host.tr("gui.komaedit.tools.preview.help"))
                    .onClick(() -> {
                        host.preferences().setToolPreview(!preview);
                        host.savePreferences();
                    });
        }
        if (card instanceof SymmetryCard) {
            boolean on = info.symmetry().worldEdit();
            String missing = context.allows(SymmetryCard.WORLDEDIT) ? null : SymmetryCard.WORLDEDIT;
            ui.toggle(column.next(), host.tr("gui.komaedit.tools.symmetry.worldedit"), on)
                    .tooltip(host.tr("gui.komaedit.tools.symmetry.worldedit.help")
                            + (missing == null ? "" : "\n" + host.tr("gui.komaedit.tools.problem.permission", missing)))
                    .onClick(() -> {
                        if (missing != null) {
                            host.status(host.tr("gui.komaedit.tools.problem.permission", missing));
                            return;
                        }
                        send(host, SymmetryCard.worldEditCommand(!on));
                    });
        }

        drawActions(ui, column, host, card, values, context, info);
        if (card.id().equals("analyze")) {
            drawMaterials(ui, column, host, info.materials());
        }
        drawCommand(ui, column, host, card, values);
    }

    /** Sobre qué trabaja la herramienta, con lo que hay ahora (selección, puntos). */
    String target(ToolCard card, Host host, ToolContext context, ToolsInfo info) {
        String key = card.target().translationKey();
        return switch (card.target()) {
            case SELECTION -> info.hasSelection()
                    ? host.tr(key + ".some", info.selection()[0], info.selection()[1], info.selection()[2])
                    : host.tr(key + ".none");
            case PATH_OR_LOOK, PATH_OR_SELECTION -> context.pathPoints() > 0
                    ? host.tr(key + ".points", context.pathPoints())
                    : host.tr(key + (card.target() == ToolTarget.PATH_OR_SELECTION && info.hasSelection()
                    ? ".selection" : ".none"));
            default -> host.tr(key);
        };
    }

    /** El texto de un problema; en «sobran espacios» y «carácter no válido» se nombra el ajuste por su etiqueta. */
    public String problemText(Host host, ToolProblem problem) {
        Object[] arguments = problem.arguments().toArray();
        if (problem.id().equals("spaces") || problem.id().equals("illegal_character")) {
            for (int index = 0; index < arguments.length; index++) {
                for (ToolParam param : selected.params()) {
                    if (param.id().equals(arguments[index])) {
                        arguments[index] = host.tr(param.labelKey());
                    }
                }
            }
        }
        return host.tr(problem.translationKey(), arguments);
    }

    private void drawProblem(ToolsUi ui, Column column, Host host, ToolProblem problem) {
        String text = problemText(host, problem);
        List<String> lines = ui.wrap(text, column.width() - 16);
        Rect card = column.next(Math.max(SMALL_ROW, lines.size() * 10 + 6));
        ui.fill(card, problem.blocking() ? ToolsUi.Ink.DANGER_DIM : ToolsUi.Ink.CARD);
        ui.fill(card.left(2), problem.blocking() ? ToolsUi.Ink.DANGER : ToolsUi.Ink.WARNING);
        for (int index = 0; index < lines.size(); index++) {
            ui.label(lines.get(index), card.x() + 8, card.y() + 4 + index * 10, card.width() - 12,
                    problem.blocking() ? ToolsUi.Ink.DANGER : ToolsUi.Ink.WARNING);
        }
        ui.register(card).tooltip(text);
    }

    private void drawPresets(ToolsUi ui, Column column, Host host, ToolCard card, ToolValues values,
                             ToolsInfo info) {
        List<ToolPreset> presets = card.presets();
        boolean seeded = card.params().stream().anyMatch(param -> param.id().equals("seed"));
        Map<String, String> saved = !card.savedPresets() ? Map.of()
                : card.id().equals("tree") ? info.treePresets() : info.rockPresets();
        if (presets.isEmpty() && !seeded && saved.isEmpty()) {
            return;
        }
        ui.section(column, host.tr("gui.komaedit.tools.presets"));
        List<String> labels = new ArrayList<>();
        presets.forEach(preset -> labels.add(host.tr(preset.labelKey())));
        if (seeded) {
            labels.add(host.tr("gui.komaedit.preset.random"));
        }
        int[] widths = labels.stream().mapToInt(label -> ui.width(label) + 16).toArray();
        List<Rect> cells = column.flow(widths, SMALL_ROW);
        for (int index = 0; index < presets.size(); index++) {
            ToolPreset preset = presets.get(index);
            ui.button(cells.get(index), labels.get(index), ToolsUi.Tone.SECONDARY)
                    .tooltip(host.tr(preset.helpKey()))
                    .group("tools:preset")
                    .onClick(() -> {
                        set(card.apply(current(), preset));
                        host.status(host.tr("gui.komaedit.preset.applied", host.tr(preset.labelKey())));
                    });
        }
        if (seeded) {
            ui.button(cells.get(presets.size()), labels.get(presets.size()), ToolsUi.Tone.SECONDARY)
                    .tooltip(host.tr("gui.komaedit.tools.random.help"))
                    .group("tools:preset")
                    .onClick(() -> randomSeed(host));
        }
        if (!saved.isEmpty()) {
            String caption = host.tr("gui.komaedit.tools.saved_presets");
            List<String> names = new ArrayList<>(saved.keySet());
            int captionWidth = ui.width(caption) + 6;
            int[] chipWidths = names.stream().mapToInt(name -> ui.chipWidth("#" + name)).toArray();
            List<Rect> chips = Column.flow(chipWidths, 14, column.x() + captionWidth, column.y(),
                    column.width() - captionWidth);
            ui.label(caption, column.x(), column.y() + 3, captionWidth, ToolsUi.Ink.MUTED);
            int bottom = column.y();
            for (int index = 0; index < names.size(); index++) {
                String name = names.get(index);
                String settings = saved.get(name);
                Rect chip = chips.get(index);
                ui.chip(chip, "#" + name, false, ToolsUi.Ink.TEXT_SECONDARY)
                        .tooltip("#" + name + "\n" + settings)
                        .group("tools:preset")
                        .onClick(() -> applySaved(card, name, settings, host));
                bottom = Math.max(bottom, chip.bottom());
            }
            column.space(bottom - column.y() + Column.GAP);
        }
    }

    /** Variante con semilla nueva; el resto de ajustes se queda como está. */
    public void randomSeed(Host host) {
        if (selected.params().stream().noneMatch(param -> param.id().equals("seed"))) {
            return;
        }
        int seed = SEEDS.nextInt(1_000_000);
        set(current().with("seed", seed));
        host.status(host.tr("gui.komaedit.variant.applied", seed));
    }

    private void applySaved(ToolCard card, String name, String settings, Host host) {
        try {
            set(card.parsePreset(settings));
            host.status(host.tr("gui.komaedit.tools.status.preset_loaded", name));
        } catch (RuntimeException exception) {
            host.status(host.tr("gui.komaedit.tools.status.preset_unreadable", name));
        }
    }

    // ── Ajustes ─────────────────────────────────────────────────────────────────────────────────────────

    private void drawParam(ToolsUi ui, Column column, Host host, ToolCard card, ToolParam param, ToolsInfo info) {
        switch (param) {
            case ToolParam.Number number -> drawNumber(ui, column, host, card, number);
            case ToolParam.Choice choice -> {
                switch (choice.style()) {
                    case CYCLE -> drawCycle(ui, column, host, card, choice, info);
                    case BUTTONS -> drawButtons(ui, column, host, card, choice, info);
                    case GALLERY -> drawGallery(ui, column, host, card, choice);
                    case SEARCH -> drawSearchChoice(ui, column, host, card, choice, info);
                }
            }
            case ToolParam.Toggle toggle -> {
                boolean on = current().toggle(toggle.id());
                ui.toggle(column.next(), host.tr(toggle.labelKey()), on)
                        .tooltip(host.tr(card.helpKey(toggle)))
                        .group("tools:" + toggle.id())
                        .onClick(() -> set(current().with(toggle.id(), !on)));
            }
            case ToolParam.Block block -> drawBlock(ui, column, host, card, block);
            case ToolParam.Text text -> drawText(ui, column, host, card, text);
        }
    }

    private void drawNumber(ToolsUi ui, Column column, Host host, ToolCard card, ToolParam.Number number) {
        Rect row = column.next();
        if (!ui.visible(row)) {
            return;
        }
        Integer value = current().number(number.id());
        String hint = card.autoHint(number.id(), current());
        int base = base(card, number);
        String shown = value != null ? number.format(value)
                : hint != null ? hint : host.tr("gui.komaedit.tools.auto");
        String label = host.tr(number.labelKey()) + (value == null && number.auto()
                ? " · " + host.tr("gui.komaedit.tools.auto") : "");
        NumericRange range = number.range();
        String help = host.tr(card.helpKey(number)) + "\n" + host.tr("gui.komaedit.tools.range",
                number.format(range.min()), number.format(range.max()), number.format(range.step()),
                number.format(range.bigStep()), number.auto() ? host.tr("gui.komaedit.tools.auto")
                        : number.format(number.defaultValue()));
        String key = "tools:number:" + number.id();
        boolean editing = field == Field.NUMBER && fieldParam.equals(number.id());
        ui.stepper(row, new ToolsUi.Stepper(label, shown, range.fraction(base), editing,
                number.id().equals(dragParam), input,
                direction -> set(current().with(number.id(), range.stepped(base(card, number), direction,
                        host.shift()))),
                () -> {
                    dragParam = number.id();
                    dragStartX = host.mouseX();
                    dragStartValue = base(card, number);
                    dragMoved = false;
                },
                () -> set(current().reset(number.id())),
                help, "tools:" + number.id(), key));
    }

    /** El número desde el que se mueve un ajuste: el suyo, el automático que dice la tarjeta o el de arranque. */
    private int base(ToolCard card, ToolParam.Number number) {
        Integer value = current().number(number.id());
        if (value != null) {
            return value;
        }
        String hint = card.autoHint(number.id(), current());
        if (hint != null) {
            try {
                return number.scale(Double.parseDouble(hint.replace("%", "")));
            } catch (NumberFormatException ignored) {
                // Un automático que no es un número (azar): se arranca del de serie.
            }
        }
        return number.defaultValue();
    }

    private List<String> options(ToolParam.Choice choice, ToolsInfo info) {
        List<String> options = new ArrayList<>(choice.options());
        switch (choice.source()) {
            case "fonts" -> info.fonts().stream().filter(font -> !options.contains(font)).forEach(options::add);
            case "biomes" -> options.addAll(info.biomes());
            default -> {
            }
        }
        return options;
    }

    private String optionLabel(ToolParam.Choice choice, String option, Host host) {
        if (choice.options().contains(option)) {
            return host.tr(choice.optionKey(option));
        }
        return option.startsWith("minecraft:") ? option.substring("minecraft:".length()) : option;
    }

    private void drawCycle(ToolsUi ui, Column column, Host host, ToolCard card, ToolParam.Choice choice,
                           ToolsInfo info) {
        Rect row = column.next();
        if (!ui.visible(row)) {
            return;
        }
        List<String> options = options(choice, info);
        String value = current().choice(choice.id());
        ui.cycle(row, host.tr(choice.labelKey()), optionLabel(choice, value, host), direction -> {
                    int index = Math.max(0, options.indexOf(current().choice(choice.id())));
                    set(current().with(choice.id(), options.get(Math.floorMod(index + direction, options.size()))));
                })
                .onReset(() -> set(current().reset(choice.id())))
                .tooltip(host.tr(card.helpKey(choice)) + "\n" + host.tr("gui.komaedit.help.cycle"))
                .group("tools:" + choice.id());
    }

    private void drawCaption(ToolsUi ui, Column column, String text) {
        Rect caption = column.next(9);
        ui.label(text, caption.x(), caption.y(), caption.width(), ToolsUi.Ink.TEXT_SECONDARY);
    }

    private void drawButtons(ToolsUi ui, Column column, Host host, ToolCard card, ToolParam.Choice choice,
                             ToolsInfo info) {
        drawCaption(ui, column, host.tr(choice.labelKey()));
        List<String> options = options(choice, info);
        String value = current().choice(choice.id());
        int[] widths = new int[options.size()];
        String[] labels = new String[options.size()];
        for (int index = 0; index < options.size(); index++) {
            labels[index] = optionLabel(choice, options.get(index), host);
            widths[index] = Math.min(column.width(), ui.width(labels[index]) + 14);
        }
        List<Rect> cells = column.flow(widths, SMALL_ROW);
        String help = host.tr(card.helpKey(choice));
        for (int index = 0; index < options.size(); index++) {
            String option = options.get(index);
            ui.button(cells.get(index), labels[index], option.equals(value) ? ToolsUi.Tone.SELECTED
                            : ToolsUi.Tone.SECONDARY)
                    .tooltip(labels[index] + "\n" + help)
                    .group("tools:" + choice.id())
                    .onReset(() -> set(current().reset(choice.id())))
                    .onClick(() -> set(current().with(choice.id(), option)));
        }
    }

    private void drawGallery(ToolsUi ui, Column column, Host host, ToolCard card, ToolParam.Choice choice) {
        drawCaption(ui, column, host.tr(choice.labelKey()));
        String value = current().choice(choice.id());
        boolean custom = !current().text("custom").isBlank();
        int perRow = Math.max(1, (column.width() + 2) / (GALLERY_CELL + 2));
        List<String> options = choice.options();
        for (int start = 0; start < options.size(); start += perRow) {
            Rect row = column.next(GALLERY_CELL);
            for (int index = start; index < Math.min(options.size(), start + perRow); index++) {
                String option = options.get(index);
                Rect cell = new Rect(row.x() + (index - start) * (GALLERY_CELL + 2), row.y(), GALLERY_CELL,
                        GALLERY_CELL);
                if (!ui.visible(cell)) {
                    continue;
                }
                boolean chosen = option.equals(value) && !custom;
                boolean hovered = ui.hovered(cell);
                ui.fill(cell, chosen ? ToolsUi.Ink.ACCENT : hovered ? ToolsUi.Ink.CARD_HOVER : ToolsUi.Ink.CARD);
                ui.stamp(option, cell.inset(2, 2));
                String description = StampLibrary.descriptions().containsKey(option)
                        ? host.tr("gui.komaedit.tools.option.stamp." + option + ".help") : "";
                ui.register(cell)
                        .tooltip(host.tr(choice.optionKey(option)) + "\n" + description)
                        .group("tools:" + choice.id())
                        .onClick(() -> set(current().with(choice.id(), option).with("custom", "")));
            }
        }
    }

    private void drawSearchChoice(ToolsUi ui, Column column, Host host, ToolCard card, ToolParam.Choice choice,
                                  ToolsInfo info) {
        drawCaption(ui, column, host.tr(choice.labelKey()));
        String value = current().choice(choice.id());
        Rect box = column.next(18);
        boolean typing = field == Field.BIOME;
        ui.field(box, biomeSearch, optionLabel(choice, value, host), typing, false)
                .key("tools:biome")
                .tooltip(host.tr(card.helpKey(choice)))
                .onClick(() -> field = Field.BIOME);
        List<String> matches = biomeMatches(options(choice, info), value);
        if (matches.isEmpty()) {
            Rect empty = column.next(SMALL_ROW);
            ui.fill(empty, ToolsUi.Ink.CARD);
            ui.label(host.tr(info.biomes().isEmpty() ? "gui.komaedit.tools.biome.none"
                    : "gui.komaedit.tools.search.empty"), empty.x() + 8, empty.y() + 4, empty.width() - 16,
                    ToolsUi.Ink.MUTED);
            return;
        }
        for (String option : matches) {
            Rect row = column.next(SMALL_ROW);
            if (!ui.visible(row)) {
                continue;
            }
            boolean chosen = option.equals(value);
            boolean hovered = ui.hovered(row);
            ui.fill(row, chosen ? ToolsUi.Ink.CARD_ACTIVE : hovered ? ToolsUi.Ink.CARD_HOVER : ToolsUi.Ink.CARD);
            if (chosen) {
                ui.fill(row.left(2), ToolsUi.Ink.ACCENT);
            }
            ui.label(optionLabel(choice, option, host), row.x() + 7, row.y() + 4, row.width() - 14,
                    chosen ? ToolsUi.Ink.TEXT : ToolsUi.Ink.TEXT_SECONDARY);
            ui.register(row).tooltip(option).group("tools:" + choice.id()).onClick(() -> {
                set(current().with(choice.id(), option));
                biomeSearch = "";
                stopEditing();
            });
        }
    }

    /** Biomas que encajan con lo escrito (el elegido primero), como mucho {@value #BIOME_ROWS}. */
    List<String> biomeMatches(List<String> options, String value) {
        String query = ShapeSearch.normalize(biomeSearch);
        List<String> matches = new ArrayList<>();
        if (query.isEmpty() && options.contains(value)) {
            matches.add(value);
        }
        for (String option : options) {
            if (matches.size() >= BIOME_ROWS) {
                break;
            }
            if (!matches.contains(option) && (query.isEmpty() || ShapeSearch.normalize(option).contains(query))) {
                matches.add(option);
            }
        }
        return matches;
    }

    private void drawBlock(ToolsUi ui, Column column, Host host, ToolCard card, ToolParam.Block block) {
        Rect row = column.next(24);
        if (!ui.visible(row)) {
            return;
        }
        String value = current().block(block.id());
        String hint = card.autoHint(block.id(), current());
        boolean hovered = ui.hovered(row);
        ui.fill(row, hovered ? ToolsUi.Ink.CARD_HOVER : ToolsUi.Ink.CARD);
        String shown = value != null ? value : hint;
        ui.item(shown == null ? "minecraft:barrier" : firstBlock(shown), row.x() + 4, row.y() + 4);
        int textX = row.x() + 24;
        ui.label(host.tr(block.labelKey()), textX, row.y() + 3, row.width() - 28, ToolsUi.Ink.MUTED);
        String text = value != null ? compact(value)
                : host.tr("gui.komaedit.tools.block.auto", hint == null ? "" : compact(hint));
        ui.label(text, textX, row.y() + 13, row.width() - 28, value != null ? ToolsUi.Ink.TEXT
                : ToolsUi.Ink.TEXT_SECONDARY);
        ui.register(row)
                .tooltip(host.tr(card.helpKey(block)) + "\n" + host.tr("gui.komaedit.tools.block.help"))
                .group("tools:" + block.id())
                .onReset(() -> set(current().reset(block.id())))
                .onClick(() -> host.pickBlock(id -> set(current().with(block.id(), compact(id)))));
    }

    private static String firstBlock(String list) {
        String first = list.split(",")[0].strip();
        int bracket = first.indexOf('[');
        first = bracket > 0 ? first.substring(0, bracket) : first;
        int percent = first.indexOf('%');
        first = percent >= 0 ? first.substring(percent + 1) : first;
        return first.contains(":") ? first : "minecraft:" + first;
    }

    private static String compact(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private void drawText(ToolsUi ui, Column column, Host host, ToolCard card, ToolParam.Text text) {
        drawCaption(ui, column, host.tr(text.labelKey()));
        Rect box = column.next(18);
        boolean editing = field == Field.TEXT && fieldParam.equals(text.id());
        String value = editing ? input : current().text(text.id());
        String hint = card.autoHint(text.id(), current());
        String placeholder = hint != null ? hint : host.tr(text.placeholderKey());
        ui.field(box, value, placeholder, editing, false)
                .key("tools:text:" + text.id())
                .tooltip(host.tr(card.helpKey(text)))
                .onReset(() -> set(current().reset(text.id())))
                .onClick(() -> {
                    if (field == Field.TEXT && fieldParam.equals(text.id())) {
                        return;
                    }
                    commitField(host);
                    field = Field.TEXT;
                    fieldParam = text.id();
                    input = current().text(text.id());
                    host.status(host.tr("gui.komaedit.tools.status.typing"));
                });
    }

    // ── Acciones, materiales y orden ────────────────────────────────────────────────────────────────────

    private void drawActions(ToolsUi ui, Column column, Host host, ToolCard card, ToolValues values,
                             ToolContext context, ToolsInfo info) {
        List<ToolAction> actions = card.actions().stream()
                .filter(action -> !action.id().equals("symmetry_off") || info.symmetry().active())
                .toList();
        if (actions.isEmpty()) {
            return;
        }
        ui.section(column, host.tr("gui.komaedit.tools.more"));
        int[] widths = actions.stream().mapToInt(action -> ui.width(host.tr(action.labelKey())) + 16).toArray();
        List<Rect> cells = column.flow(widths, SMALL_ROW);
        for (int index = 0; index < actions.size(); index++) {
            ToolAction action = actions.get(index);
            String missing = card.missingPermission(action, values, context);
            String help = host.tr(action.helpKey()) + (missing == null ? ""
                    : "\n" + host.tr("gui.komaedit.tools.problem.permission", missing));
            ui.button(cells.get(index), host.tr(action.labelKey()), missing != null ? ToolsUi.Tone.DISABLED
                            : action == namingAction ? ToolsUi.Tone.SELECTED : ToolsUi.Tone.SECONDARY)
                    .tooltip(help)
                    .onClick(() -> runAction(card, action, host));
        }
        if (namingAction != null && actions.contains(namingAction)) {
            Rect row = column.next(18);
            Rect save = row.rightPart(70);
            Rect name = row.withoutRight(70, Column.GAP);
            boolean editing = field == Field.PRESET_NAME;
            ui.field(name, editing ? input : "", host.tr("gui.komaedit.tools.preset_name"), editing,
                            !input.isEmpty() && !validName(input))
                    .key("tools:preset_name")
                    .onClick(() -> field = Field.PRESET_NAME);
            ui.button(save, host.tr("gui.komaedit.save"), validName(input) ? ToolsUi.Tone.PRIMARY
                    : ToolsUi.Tone.DISABLED).onClick(() -> saveNamed(host));
        }
    }

    private void runAction(ToolCard card, ToolAction action, Host host) {
        String missing = card.missingPermission(action, current(), host.context());
        if (missing != null) {
            host.status(host.tr("gui.komaedit.tools.problem.permission", missing));
            return;
        }
        if (action.needsName()) {
            namingAction = action == namingAction ? null : action;
            field = namingAction == null ? Field.NONE : Field.PRESET_NAME;
            input = "";
            return;
        }
        send(host, action.command().apply(current()));
        host.status(host.tr("gui.komaedit.tools.status.sent", host.tr(action.labelKey())));
    }

    private static boolean validName(String name) {
        return name.matches("[a-z0-9_\\-]{1,32}");
    }

    private void saveNamed(Host host) {
        if (namingAction == null || !validName(input)) {
            host.status(host.tr("gui.komaedit.tools.status.bad_name"));
            return;
        }
        send(host, ToolCard.withName(namingAction.command().apply(current()), input));
        host.status(host.tr("gui.komaedit.tools.status.preset_saved", input));
        namingAction = null;
        field = Field.NONE;
        input = "";
        refreshSoon();
    }

    private void drawMaterials(ToolsUi ui, Column column, Host host, ToolsInfo.Materials materials) {
        ui.section(column, host.tr("gui.komaedit.tools.materials"));
        if (materials == null) {
            ui.paragraph(column, host.tr("gui.komaedit.tools.materials.none"), ToolsUi.Ink.MUTED, 0);
            return;
        }
        if (materials.blocks() == 0) {
            ui.paragraph(column, host.tr("gui.komaedit.tools.materials.empty"), ToolsUi.Ink.MUTED, 0);
            return;
        }
        ui.paragraph(column, host.tr("gui.komaedit.tools.materials.total", group(materials.blocks()),
                materials.types(), group(materials.items()), group(materials.slots()), group(materials.chests())),
                ToolsUi.Ink.TEXT_SECONDARY, 0);
        ui.button(column.next(SMALL_ROW), host.tr("gui.komaedit.tools.materials.copy"), ToolsUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.tools.materials.copy.help"))
                .onClick(() -> {
                    host.copy(materials.clipboard());
                    host.status(host.tr("gui.komaedit.tools.status.copied"));
                });
        for (ToolsInfo.Materials.Line line : materials.lines()) {
            Rect row = column.next(18);
            if (!ui.visible(row)) {
                continue;
            }
            ui.fill(row, ui.hovered(row) ? ToolsUi.Ink.CARD_HOVER : ToolsUi.Ink.CARD);
            ui.item(line.item(), row.x() + 2, row.y() + 1);
            String amount = group(line.amount());
            int amountWidth = ui.width(amount) + 6;
            String stacks = line.maxStack() > 1 && line.amount() >= line.maxStack()
                    ? host.tr("gui.komaedit.tools.materials.stacks", line.stacks(), line.remainder()) : "";
            int stacksWidth = stacks.isEmpty() ? 0 : ui.width(stacks) + 6;
            int nameWidth = row.width() - 24 - amountWidth - stacksWidth;
            ui.label(compact(line.item()), row.x() + 22, row.y() + 5, nameWidth, ToolsUi.Ink.TEXT_SECONDARY);
            ui.label(amount, row.right() - amountWidth - stacksWidth, row.y() + 5, amountWidth, ToolsUi.Ink.TEXT);
            if (!stacks.isEmpty()) {
                ui.label(stacks, row.right() - stacksWidth, row.y() + 5, stacksWidth, ToolsUi.Ink.MUTED);
            }
            ui.register(row).tooltip(compact(line.item()) + ": " + amount + (stacks.isEmpty() ? "" : " " + stacks));
        }
        if (materials.hidden() > 0) {
            ui.paragraph(column, host.tr("gui.komaedit.tools.materials.more", materials.hidden()),
                    ToolsUi.Ink.MUTED, 0);
        }
    }

    private static String group(long value) {
        return com.komainu.moxia.progress.ProgressLine.group(value);
    }

    private void drawCommand(ToolsUi ui, Column column, Host host, ToolCard card, ToolValues values) {
        ui.section(column, host.tr("gui.komaedit.tools.command"));
        String command = "/" + card.command(values, card.previewable() && host.preferences().toolPreview());
        List<String> lines = ui.wrap(command, column.width() - 8);
        Rect box = column.next(Math.min(4, lines.size()) * 10 + 6);
        boolean hovered = ui.hovered(box);
        ui.fill(box, hovered ? ToolsUi.Ink.CARD_HOVER : ToolsUi.Ink.FIELD);
        for (int index = 0; index < Math.min(4, lines.size()); index++) {
            ui.label(lines.get(index), box.x() + 4, box.y() + 3 + index * 10, box.width() - 8,
                    hovered ? ToolsUi.Ink.TEXT : ToolsUi.Ink.MUTED);
        }
        ui.register(box).tooltip(command + "\n" + host.tr("gui.komaedit.tools.command.help")).onClick(() -> {
            host.copy(command);
            host.status(host.tr("gui.komaedit.tools.status.copied"));
        });
    }

    // ── Simetría y capacidades ──────────────────────────────────────────────────────────────────────────

    /** Al cambiar la simetría en el servidor (también por el chat), la tarjeta enseña el modo que tiene. */
    private void syncSymmetry(ToolsInfo info) {
        if (!info.known()) {
            return;
        }
        String key = info.symmetry().mode() + "|" + info.symmetry().arms();
        if (key.equals(syncedSymmetry)) {
            return;
        }
        syncedSymmetry = key;
        SymmetryCard card = (SymmetryCard) ToolCatalog.byId("symmetry");
        values.put(card.id(), card.fromServer(values(card), info.symmetry().mode(), info.symmetry().arms()));
    }

    private void drawSymmetryState(ToolsUi ui, Column column, Host host, ToolsInfo info) {
        ToolsInfo.Symmetry symmetry = info.symmetry();
        Rect card = column.next(30);
        ui.fill(card, ToolsUi.Ink.CARD);
        ui.fill(card.left(2), symmetry.active() ? ToolsUi.Ink.SUCCESS : ToolsUi.Ink.DISABLED);
        String state = host.tr(symmetry.active() ? "gui.komaedit.tools.symmetry.on"
                : "gui.komaedit.tools.symmetry.off", symmetry.mode());
        ui.bold(state, card.x() + 8, card.y() + 5, card.width() - 16,
                symmetry.active() ? ToolsUi.Ink.SUCCESS : ToolsUi.Ink.TEXT_SECONDARY);
        String center = symmetry.center().isEmpty() ? host.tr("gui.komaedit.tools.symmetry.no_center")
                : host.tr(symmetry.elsewhere() ? "gui.komaedit.tools.symmetry.center_elsewhere"
                : "gui.komaedit.tools.symmetry.center", symmetry.center());
        ui.label(center, card.x() + 8, card.y() + 17, card.width() - 16,
                symmetry.elsewhere() ? ToolsUi.Ink.WARNING : ToolsUi.Ink.MUTED);
        ui.register(card).tooltip(state + "\n" + center);
    }

    private void drawCapabilities(ToolsUi ui, Column column, Host host, ToolsInfo info) {
        ToolsInfo.Capabilities state = info.capabilities();
        if (state == null) {
            ui.paragraph(column, host.tr("gui.komaedit.tools.capabilities.unavailable"), ToolsUi.Ink.MUTED, 0);
            return;
        }
        if (!state.modeAllowed()) {
            drawProblem(ui, column, host, ToolProblem.warning("creative_only"));
        }
        ui.section(column, host.tr("gui.komaedit.tools.capabilities.toggles"));
        for (BuildCapability capability : BuildCapability.values()) {
            Rect row = column.next();
            if (!ui.visible(row)) {
                continue;
            }
            boolean permitted = (state.permitted() & capability.bit()) != 0;
            boolean on = (state.enabled() & capability.bit()) != 0;
            String name = host.tr("gui.komaedit.capability." + capability.id());
            String label = permitted ? name : name + " · " + host.tr("gui.komaedit.capability.no_permission");
            String help = host.tr("gui.komaedit.capability." + capability.id() + ".desc") + (permitted ? ""
                    : "\n" + host.tr("gui.komaedit.tools.problem.permission", capability.permission()));
            ui.toggle(row, label, on && permitted)
                    .tooltip(help)
                    .group("tools:capability")
                    .onClick(() -> {
                        if (!permitted) {
                            host.status(host.tr("gui.komaedit.tools.problem.permission", capability.permission()));
                            return;
                        }
                        send(host, CapabilityCard.toggleCommand(capability, !on));
                    });
        }
        ui.section(column, host.tr("gui.komaedit.tools.capabilities.options"));
        capabilityNumber(ui, column, host, "angel", BuildCapability.ANGEL, state.angelDistance(), 1,
                state.maxReach(), state);
        capabilityNumber(ui, column, host, "reach", BuildCapability.REACH, state.reachDistance(), 5,
                state.maxReach(), state);
        capabilityNumber(ui, column, host, "extrude", BuildCapability.EXTRUDE, state.extrudeLimit(), 1,
                state.maxLayerBlocks(), state);
        ui.toggle(column.next(), host.tr("gui.komaedit.tools.capabilities.family"), state.replaceFamily())
                .tooltip(host.tr("gui.komaedit.tools.capabilities.family.help"))
                .onClick(() -> send(host, CapabilityCard.optionCommand("family", state.replaceFamily() ? "off"
                        : "on")));
        ui.cycle(column.next(), host.tr("gui.komaedit.tools.capabilities.match"),
                        host.tr(state.extrudeByState() ? "gui.komaedit.capability.match.state"
                                : "gui.komaedit.capability.match.type"),
                        direction -> send(host, CapabilityCard.optionCommand("match", state.extrudeByState()
                                ? "type" : "state")))
                .tooltip(host.tr("gui.komaedit.tools.capabilities.match.help"));
        if (state.flySpeedAllowed()) {
            capabilityNumber(ui, column, host, "flyspeed", null, state.flySpeed(), 5, state.maxFlySpeed(), state);
        }
    }

    /** Un número de las capacidades: se ve al momento y se manda al dejar de tocarlo. */
    private void capabilityNumber(ToolsUi ui, Column column, Host host, String id, BuildCapability capability,
                                  int serverValue, int minimum, int maximum, ToolsInfo.Capabilities state) {
        Rect row = column.next();
        if (!ui.visible(row)) {
            return;
        }
        ToolCard card = ToolCatalog.byId("capabilities");
        ToolParam.Number param = (ToolParam.Number) card.defaults().param(id);
        int top = Math.max(minimum, maximum);
        NumericRange range = new NumericRange(minimum, top, param.range().step(),
                Math.max(param.range().step(), param.range().bigStep()));
        int value = range.clamp(pendingOptions.getOrDefault(id, serverValue));
        String key = "tools:number:" + id;
        boolean editing = field == Field.NUMBER && fieldParam.equals(id);
        ui.stepper(row, new ToolsUi.Stepper(host.tr(param.labelKey()), param.format(value), range.fraction(value),
                editing, id.equals(dragParam), input,
                direction -> queueOption(id, range.stepped(value, direction, host.shift()), host),
                () -> {
                    dragParam = id;
                    dragStartX = host.mouseX();
                    dragStartValue = value;
                    dragMoved = false;
                },
                () -> queueOption(id, param.defaultValue(), host),
                host.tr(card.helpKey(param)), "tools:" + id, key));
    }

    private void queueOption(String id, int value, Host host) {
        pendingOptions.put(id, value);
        pendingSince = host.now();
    }

    /** Manda los números de las capacidades que se dejaron de tocar hace más de {@link #OPTION_DELAY_MILLIS}. */
    public void flushOptions(Host host, boolean now) {
        if (pendingOptions.isEmpty() || !now && host.now() - pendingSince < OPTION_DELAY_MILLIS) {
            return;
        }
        ToolCard card = ToolCatalog.byId("capabilities");
        for (Map.Entry<String, Integer> entry : pendingOptions.entrySet()) {
            String command = switch (entry.getKey()) {
                case "angel" -> CapabilityCard.distanceCommand(BuildCapability.ANGEL, entry.getValue());
                case "reach" -> CapabilityCard.distanceCommand(BuildCapability.REACH, entry.getValue());
                case "extrude" -> CapabilityCard.distanceCommand(BuildCapability.EXTRUDE, entry.getValue());
                default -> CapabilityCard.flySpeedCommand(card.defaults().with("flyspeed", entry.getValue()));
            };
            host.send(command);
        }
        pendingOptions.clear();
    }

    // ── Construir (pie del Studio) ──────────────────────────────────────────────────────────────────────

    /** «Árbol · 3 puntos», para la primera línea del pie. */
    public String footerSummary(Host host) {
        ToolsInfo info = host.info();
        ToolContext context = host.context();
        return host.tr(selected.nameKey()) + " · " + switch (selected.target()) {
            case SELECTION -> info.hasSelection()
                    ? host.tr("gui.komaedit.tools.footer.selection", info.selection()[0], info.selection()[1],
                    info.selection()[2]) : host.tr("gui.komaedit.tools.footer.no_selection");
            case PATH_OR_LOOK, PATH_OR_SELECTION -> context.pathPoints() > 0
                    ? host.tr("gui.komaedit.tools.footer.points", context.pathPoints())
                    : host.tr(selected.target() == ToolTarget.PATH_OR_LOOK || !info.hasSelection()
                    ? "gui.komaedit.tools.footer.look" : "gui.komaedit.tools.footer.selection_short");
            case LOOK -> host.tr("gui.komaedit.tools.footer.look");
            case HAND -> host.tr("gui.komaedit.tools.footer.hand");
            case MODE -> selected instanceof SymmetryCard
                    ? host.tr(info.symmetry().active() ? "gui.komaedit.tools.footer.on"
                    : "gui.komaedit.tools.footer.off")
                    : host.tr("gui.komaedit.tools.footer.active", info.capabilities() == null ? 0
                    : Integer.bitCount(info.capabilities().enabled() & info.capabilities().permitted()));
        };
    }

    /** El primer problema que impide construir, o {@code null}. */
    public ToolProblem blocking(Host host) {
        return selected.problems(current(), host.context()).stream().filter(ToolProblem::blocking).findFirst()
                .orElse(null);
    }

    public String buildLabel(Host host) {
        ToolProblem problem = blocking(host);
        if (problem != null) {
            return host.tr(problem.translationKey() + ".short");
        }
        if (selected instanceof SymmetryCard && host.info().symmetry().active()) {
            return host.tr("gui.komaedit.tools.symmetry.apply");
        }
        boolean preview = selected.previewable() && host.preferences().toolPreview();
        return host.tr(selected.buildKey()) + (preview ? " · " + host.tr("gui.komaedit.tools.preview.short") : "");
    }

    public ToolsUi.Tone buildTone(Host host) {
        return blocking(host) != null ? ToolsUi.Tone.DISABLED : ToolsUi.Tone.PRIMARY;
    }

    public String buildHelp(Host host) {
        ToolProblem problem = blocking(host);
        String command = "/" + selected.command(current(), selected.previewable() && host.preferences().toolPreview());
        return problem == null ? host.tr("gui.komaedit.tools.build.help", command) : problemText(host, problem);
    }

    /** Construir: manda la orden de la tarjeta o dice qué falta (y lleva a Trayectoria si faltan puntos). */
    public void build(Host host) {
        commitField(host);
        ToolProblem problem = blocking(host);
        if (problem != null) {
            if (problem.id().equals("no_path_or_selection") || problem.id().equals("loft_points")) {
                host.goToPath();
            }
            host.status(problemText(host, problem));
            return;
        }
        boolean preview = selected.previewable() && host.preferences().toolPreview();
        send(host, selected.command(current(), preview));
        host.preferences().pushToolRecent(selected.id());
        host.savePreferences();
        host.status(host.tr(preview ? "gui.komaedit.tools.status.preview_sent" : "gui.komaedit.tools.status.built",
                host.tr(selected.nameKey())));
    }

    private void send(Host host, String command) {
        host.send(command);
        refreshSoon();
    }

    // ── Teclado y ratón ─────────────────────────────────────────────────────────────────────────────────

    /** Si hay un campo de la etapa abierto: las letras se escriben y ningún atajo se dispara. */
    public boolean typing() {
        return field != Field.NONE;
    }

    /** Si el control con esta clave es el campo que se está escribiendo (un clic en él no lo cierra). */
    public boolean owns(Object key) {
        return switch (field) {
            case NONE -> false;
            case SEARCH -> "tools:search".equals(key);
            case TEXT -> ("tools:text:" + fieldParam).equals(key);
            case NUMBER -> ("tools:number:" + fieldParam).equals(key);
            case PRESET_NAME -> "tools:preset_name".equals(key);
            case BIOME -> "tools:biome".equals(key);
        };
    }

    /** Abre el buscador de herramientas ({@code Ctrl+F} o {@code /} en esta etapa). */
    public void focusSearch(Host host) {
        commitField(host);
        field = Field.SEARCH;
        host.status(host.tr("gui.komaedit.search.typing"));
    }

    public void stopEditing() {
        field = Field.NONE;
        fieldParam = "";
        input = "";
    }

    /** Pulsar fuera de un campo: el texto o el número escrito se aplica; la búsqueda se queda como está. */
    public void commitField(Host host) {
        switch (field) {
            case TEXT -> set(current().with(fieldParam, input));
            case NUMBER -> commitNumber(host);
            default -> {
            }
        }
        if (field != Field.PRESET_NAME) {
            stopEditing();
        }
    }

    private void commitNumber(Host host) {
        String id = fieldParam;
        String typed = input.strip().replace(',', '.').replace('−', '-');
        stopEditing();
        if (typed.isEmpty()) {
            return;
        }
        try {
            double parsed = Double.parseDouble(typed);
            if (selected instanceof CapabilityCard) {
                ToolParam.Number param = (ToolParam.Number) selected.defaults().param(id);
                queueOption(id, param.scale(parsed), host);
                flushOptions(host, true);
                return;
            }
            ToolParam.Number param = (ToolParam.Number) current().param(id);
            set(current().with(id, param.scale(parsed)));
        } catch (RuntimeException exception) {
            host.status(host.tr("gui.komaedit.number.invalid"));
        }
    }

    public boolean key(Key key, boolean control, Host host) {
        if (field == Field.NONE) {
            return false;
        }
        switch (key) {
            case ENTER -> {
                switch (field) {
                    case SEARCH -> {
                        List<ToolCard> shown = visibleCards(host);
                        stopEditing();
                        if (!shown.isEmpty()) {
                            select(shown.getFirst(), host);
                        }
                    }
                    case BIOME -> {
                        ToolCard card = selected;
                        ToolParam.Choice choice = (ToolParam.Choice) current().param("biome");
                        List<String> matches = biomeMatches(options(choice, host.info()), current().choice("biome"));
                        if (!matches.isEmpty() && !biomeSearch.isEmpty()) {
                            setValues(card, current().with("biome", matches.getFirst()));
                        }
                        biomeSearch = "";
                        stopEditing();
                    }
                    case PRESET_NAME -> saveNamed(host);
                    default -> commitField(host);
                }
            }
            case ESCAPE -> {
                switch (field) {
                    case SEARCH -> {
                        if (search.isEmpty()) {
                            stopEditing();
                        } else {
                            search = "";
                        }
                    }
                    case BIOME -> {
                        biomeSearch = "";
                        stopEditing();
                    }
                    case PRESET_NAME -> {
                        namingAction = null;
                        stopEditing();
                    }
                    default -> stopEditing();
                }
            }
            case BACKSPACE -> {
                switch (field) {
                    case SEARCH -> search = erase(search, control);
                    case BIOME -> biomeSearch = erase(biomeSearch, control);
                    default -> input = erase(input, control);
                }
            }
            default -> {
            }
        }
        return true;
    }

    private static String erase(String text, boolean all) {
        return all || text.isEmpty() ? "" : text.substring(0, text.length() - 1);
    }

    public boolean type(char character) {
        if (field == Field.NONE || !ToolCard.legal(String.valueOf(character))) {
            return field != Field.NONE;
        }
        switch (field) {
            case SEARCH -> {
                if (search.length() < 32) {
                    search += character;
                    filter = ALL;
                }
            }
            case BIOME -> {
                if (biomeSearch.length() < 64) {
                    biomeSearch += Character.toLowerCase(character);
                }
            }
            case NUMBER -> {
                if ((Character.isDigit(character) || character == '-' || character == '.' || character == ',')
                        && input.length() < 12) {
                    input += character;
                }
            }
            case PRESET_NAME -> {
                char lower = Character.toLowerCase(character);
                if ((Character.isLetterOrDigit(lower) && lower < 128 || lower == '_' || lower == '-')
                        && input.length() < 32) {
                    input += lower;
                }
            }
            case TEXT -> {
                ToolParam.Text param = (ToolParam.Text) current().param(fieldParam);
                if (input.length() < param.maxLength()) {
                    input += character;
                }
            }
            default -> {
            }
        }
        return true;
    }

    /** Pegar en el campo abierto (Ctrl+V). */
    public void paste(String text) {
        for (char character : text.toCharArray()) {
            type(character == '\n' ? ' ' : character);
        }
    }

    public boolean dragging() {
        return dragParam != null;
    }

    /** Arrastrar el número en horizontal lo cambia como un deslizador. */
    public void drag(double mouseX, Host host) {
        if (dragParam == null) {
            return;
        }
        double distance = mouseX - dragStartX;
        if (Math.abs(distance) >= 3) {
            dragMoved = true;
        }
        if (!dragMoved) {
            return;
        }
        if (selected instanceof CapabilityCard) {
            ToolParam.Number param = (ToolParam.Number) selected.defaults().param(dragParam);
            queueOption(dragParam, param.range().dragged(dragStartValue, distance, host.shift()), host);
            return;
        }
        ToolParam.Number param = (ToolParam.Number) current().param(dragParam);
        set(current().with(dragParam, param.range().dragged(dragStartValue, distance, host.shift())));
    }

    /** Al soltar sin mover se abre el campo para escribir el número. */
    public void release(Host host) {
        if (dragParam == null) {
            return;
        }
        if (!dragMoved) {
            commitField(host);
            field = Field.NUMBER;
            fieldParam = dragParam;
            if (selected instanceof CapabilityCard) {
                ToolParam.Number param = (ToolParam.Number) selected.defaults().param(dragParam);
                input = param.format(dragStartValue);
            } else {
                Integer value = current().number(dragParam);
                ToolParam.Number param = (ToolParam.Number) current().param(dragParam);
                input = value == null ? "" : param.format(value);
            }
            host.status(host.tr("gui.komaedit.number.typing"));
        }
        dragParam = null;
    }

    /** Grupo de historial del arrastre en curso. */
    public String dragGroup() {
        return dragParam == null ? null : "tools:" + dragParam;
    }

    @Override
    public String toString() {
        return "ToolsPanel[" + selected.id() + ", " + filter + ", '" + search.toLowerCase(Locale.ROOT) + "']";
    }
}
