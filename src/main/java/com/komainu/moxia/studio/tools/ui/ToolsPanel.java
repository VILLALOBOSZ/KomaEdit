package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.capability.BuildCapability;
import com.komainu.moxia.studio.NumericRange;
import com.komainu.moxia.studio.ParameterHistory;
import com.komainu.moxia.studio.ShapeSearch;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.ValueUnit;
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
import com.komainu.moxia.studio.ui.ChoiceThumbnails;
import com.komainu.moxia.studio.ui.NumberEdit;
import com.komainu.moxia.studio.ui.NumberView;
import com.komainu.moxia.studio.ui.StudioIcons;
import com.komainu.moxia.studio.ui.StudioImage;
import com.komainu.moxia.studio.ui.StudioUi;
import com.komainu.moxia.terrain.StampLibrary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Consumer;

import static com.komainu.moxia.studio.ui.StudioTheme.*;

/**
 * Etapa «Herramientas» del Studio. Sin herramienta elegida (o al pulsar su cabecera) se ve la cuadrícula de
 * herramientas con icono, por grupos y con buscador; al elegir una, la cuadrícula se pliega en una cabecera y
 * quedan a la vista sus ajustes: primero lo que se elige viendo (miniaturas), luego los esenciales y, plegado, el
 * resto. Construir (en el pie) manda la orden por el chat, igual que si se escribiera; el servidor la comprueba
 * con sus permisos.
 *
 * <p>No depende de Minecraft: dibuja con {@link StudioUi} y pide lo demás a su {@link Host}, así que la misma clase
 * la pinta el juego, la prueban los tests y la dibujan las maquetas.</p>
 */
public final class ToolsPanel {
    /** Lo que la etapa necesita de la pantalla. */
    public interface Host {
        String tr(String key, Object... arguments);

        /** Manda una orden por el chat, sin barra. */
        void send(String command);

        /** Abre la biblioteca de bloques (con {@code title} arriba) y entrega el elegido. */
        void pickBlock(String title, String current, Consumer<String> onPick);

        void status(String text);

        void copy(String text);

        /** Lleva a la etapa Trayectoria (cuando lo que falta son puntos). */
        void goToPath();

        boolean shift();

        long now();

        ToolContext context();

        ToolsInfo info();

        /** Pide al servidor el estado de las herramientas. */
        void requestState();

        StudioPreferences preferences();

        void savePreferences();

        /** Nombre traducido del bloque u objeto ({@code minecraft:spruce_planks} → «Tablones de abeto»). */
        String blockName(String id);

        /** El bloque que se mira (el centro de la pantalla), o {@code null}. */
        String lookedAtBlock();
    }

    public enum Key {
        ENTER, ESCAPE, BACKSPACE, OTHER
    }

    private enum Field {
        NONE, SEARCH, TEXT, PRESET_NAME, BIOME
    }

    /** Un estado del historial de ajustes: la tarjeta y sus valores. */
    public record Snapshot(String card, ToolValues values) {
    }

    /** Un grupo de la cuadrícula: su título y sus herramientas. */
    record Group(String titleKey, List<ToolCard> cards) {
    }

    public static final long REFRESH_MILLIS = 1_000L;
    /** Los números de las capacidades se mandan cuando se dejan de tocar, para no llenar el chat. */
    public static final long OPTION_DELAY_MILLIS = 600L;
    /** Los ajustes se guardan en disco como mucho cada tanto, no en cada paso de un arrastre. */
    public static final long SAVE_DELAY_MILLIS = 2_000L;
    public static final String MORE_KEY = "tools:more:";
    private static final int CELL = 18;
    private static final int TILE = 40;
    private static final int MAX_TILE = 56;
    private static final int BIOME_ROWS = 6;
    private static final int SMALL_ROW = StudioUi.SMALL_ROW;
    private static final SplittableRandom SEEDS = new SplittableRandom();

    private final Map<String, ToolValues> values = new HashMap<>();
    private final ParameterHistory<Snapshot> history = new ParameterHistory<>(100);
    private final Map<String, Integer> pendingOptions = new LinkedHashMap<>();
    private final Map<String, Integer> pages = new HashMap<>();
    private final NumberEdit numbers = new NumberEdit();
    /** Alto de una fila de ajuste: 20 normal, 18 en compacto. */
    private int row = Column.ROW;
    private ToolCard selected;
    private boolean browsing = true;
    private boolean loaded;
    private String search = "";
    private Field field = Field.NONE;
    private String fieldParam = "";
    private String input = "";
    private String biomeSearch = "";
    private ToolAction namingAction;
    private long lastRequest = Long.MIN_VALUE / 2;
    private long pendingSince;
    private long dirtySince = -1;
    /** La hora del último dibujo o tick, para fechar los cambios. */
    private long clock;
    private String syncedSymmetry = "";
    private boolean restoring;

    // ── Estado ──────────────────────────────────────────────────────────────────────────────────────────

    /** La herramienta elegida; la primera si aún no se eligió ninguna. */
    public ToolCard selected() {
        return selected == null ? ToolCatalog.ALL.getFirst() : selected;
    }

    /** Si el jugador ya eligió alguna herramienta (antes de eso el pie no ofrece construir nada). */
    public boolean chosen() {
        return selected != null;
    }

    /** Si se está viendo la cuadrícula para elegir herramienta (y no los ajustes de la elegida). */
    public boolean browsing() {
        return browsing || selected == null;
    }

    public ToolValues values(ToolCard card) {
        return values.computeIfAbsent(card.id(), id -> card.defaults());
    }

    public void setValues(ToolCard card, ToolValues next) {
        values.put(card.id(), next);
    }

    private ToolValues current() {
        return values(selected());
    }

    private void set(ToolValues next) {
        if (!next.equals(current())) {
            values.put(selected().id(), next);
            dirtySince = dirtySince < 0 ? clock : dirtySince;
        }
    }

    /** Recupera, una sola vez, la última herramienta y sus ajustes guardados. */
    public void load(Host host) {
        if (loaded) {
            return;
        }
        loaded = true;
        StudioPreferences preferences = host.preferences();
        for (ToolCard card : ToolCatalog.ALL) {
            Map<String, Object> stored = preferences.toolValues(card.id());
            if (!stored.isEmpty() && !values.containsKey(card.id())) {
                values.put(card.id(), card.defaults().restore(stored));
            }
        }
        ToolCard remembered = ToolCatalog.byId(preferences.tool());
        if (remembered != null && selected == null) {
            selected = remembered;
            browsing = false;
        }
    }

    public void select(ToolCard card, Host host) {
        load(host);
        if (card != selected) {
            stopEditing();
            selected = card;
            namingAction = null;
            biomeSearch = "";
        }
        browsing = false;
        search = "";
        host.preferences().pushToolRecent(card.id());
        host.preferences().setTool(card.id());
        host.savePreferences();
    }

    /** Abre o cierra la cuadrícula de herramientas. */
    public void setBrowsing(boolean value) {
        browsing = value || selected == null;
        if (!browsing && field == Field.SEARCH) {
            stopEditing();
        }
    }

    public String search() {
        return search;
    }

    /** La clave con la que se recuerda por dónde se iba: la cuadrícula o la herramienta elegida. */
    public String scrollKey() {
        return browsing() ? "tools:grid" : "tool:" + selected().id();
    }

    /** Pide el estado al servidor cada segundo mientras la etapa se ve, manda números pendientes y guarda. */
    public void tick(Host host) {
        load(host);
        long now = host.now();
        clock = now;
        if (now - lastRequest >= REFRESH_MILLIS) {
            lastRequest = now;
            host.requestState();
        }
        flushOptions(host, false);
        saveSoon(host, false);
    }

    /** Guarda los ajustes cambiados en las preferencias; en disco, como mucho cada {@value #SAVE_DELAY_MILLIS} ms. */
    public void saveSoon(Host host, boolean now) {
        if (dirtySince < 0) {
            return;
        }
        if (!now && host.now() - dirtySince < SAVE_DELAY_MILLIS) {
            return;
        }
        for (Map.Entry<String, ToolValues> entry : values.entrySet()) {
            host.preferences().setToolValues(entry.getKey(), entry.getValue().changed());
        }
        host.savePreferences();
        dirtySince = -1;
    }

    /** Fuerza una petición de estado en el próximo {@link #tick} (al volver a la etapa, tras una orden). */
    public void refreshSoon() {
        lastRequest = Long.MIN_VALUE / 2;
    }

    // ── Historial de ajustes ────────────────────────────────────────────────────────────────────────────

    public Snapshot snapshot() {
        return new Snapshot(selected().id(), current());
    }

    /** Anota en el historial lo que cambió una acción; {@code group} funde los cambios seguidos del mismo control. */
    public void track(Snapshot before, String group, long now) {
        if (restoring) {
            restoring = false;
            return;
        }
        if (before.card().equals(selected().id()) && !before.values().equals(current())) {
            history.record(before, group, now);
            dirtySince = dirtySince < 0 ? now : dirtySince;
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
            browsing = false;
            values.put(card.id(), snapshot.values());
        }
        stopEditing();
        restoring = true;
        host.status(host.tr(done));
        return true;
    }

    // ── Dibujo ──────────────────────────────────────────────────────────────────────────────────────────

    public void draw(StudioUi ui, Column column, Host host) {
        draw(ui, column, host, false);
    }

    /** {@code compact}: pantalla pequeña; sin descripciones, una fila de miniaturas y solo lo esencial a la vista. */
    public void draw(StudioUi ui, Column column, Host host, boolean compact) {
        load(host);
        clock = host.now();
        row = compact ? Column.ROW - 2 : Column.ROW;
        ToolContext context = host.context();
        ToolsInfo info = host.info();
        syncSymmetry(info);
        if (browsing()) {
            drawBrowser(ui, column, host, context, compact);
            return;
        }
        drawHeader(ui, column, host, compact);
        drawDetail(ui, column, host, context, info, compact);
    }

    /** Las herramientas que se ven con lo escrito en el buscador. */
    public List<ToolCard> visibleCards(Host host) {
        if (!search.isBlank()) {
            return ToolCatalog.search(ToolCatalog.ALL, search, card -> host.tr(card.nameKey()),
                    card -> host.tr(card.descriptionKey()), family -> host.tr(family.translationKey()));
        }
        return ToolCatalog.ALL;
    }

    /** Los grupos de la cuadrícula: favoritas (si hay), y por familia; simetría y capacidades juntas. */
    List<Group> groups(Host host) {
        List<Group> groups = new ArrayList<>();
        if (!search.isBlank()) {
            groups.add(new Group("", visibleCards(host)));
            return groups;
        }
        List<ToolCard> favorites = ToolCatalog.ALL.stream()
                .filter(card -> host.preferences().isToolFavorite(card.id())).toList();
        if (!favorites.isEmpty()) {
            groups.add(new Group("gui.komaedit.tools.family.favorites", favorites));
        }
        groups.add(new Group(ToolFamily.NATURE.translationKey(), ToolCatalog.family(ToolFamily.NATURE)));
        groups.add(new Group(ToolFamily.ROOFS.translationKey(), ToolCatalog.family(ToolFamily.ROOFS)));
        groups.add(new Group(ToolFamily.TERRAIN.translationKey(), ToolCatalog.family(ToolFamily.TERRAIN)));
        groups.add(new Group(ToolFamily.UTILITIES.translationKey(), ToolCatalog.family(ToolFamily.UTILITIES)));
        List<ToolCard> modes = new ArrayList<>(ToolCatalog.family(ToolFamily.SYMMETRY));
        modes.addAll(ToolCatalog.family(ToolFamily.CAPABILITIES));
        groups.add(new Group("gui.komaedit.tools.group.modes", modes));
        return groups;
    }

    private void drawBrowser(StudioUi ui, Column column, Host host, ToolContext context, boolean compact) {
        Rect box = column.next(18);
        List<ToolCard> shown = visibleCards(host);
        ui.searchField(box, search, host.tr("gui.komaedit.tools.search"), field == Field.SEARCH, shown.size(),
                        () -> {
                            search = "";
                            stopEditing();
                        })
                .key("tools:search")
                .tooltip(host.tr("gui.komaedit.tools.search.help"))
                .onClick(() -> {
                    field = Field.SEARCH;
                    host.status(host.tr("gui.komaedit.search.typing"));
                });
        int columns = column.width() >= 300 ? 3 : 2;
        boolean first = true;
        for (Group group : groups(host)) {
            if (!group.titleKey().isEmpty()) {
                if (compact && !first) {
                    column.space(2);
                }
                ui.section(column, host.tr(group.titleKey()));
            }
            first = false;
            if (group.cards().isEmpty()) {
                Rect empty = column.next(SMALL_ROW);
                ui.fill(empty, CARD);
                ui.label(host.tr("gui.komaedit.tools.search.empty"), empty.x() + 8, empty.y() + 4,
                        empty.width() - 16, MUTED);
                continue;
            }
            List<Rect> cells = column.grid(group.cards().size(), columns, CELL);
            for (int index = 0; index < group.cards().size(); index++) {
                drawToolCell(ui, cells.get(index), group.cards().get(index), host, context);
            }
        }
    }

    /** Icono de cada herramienta. */
    public static StudioIcons.Icon icon(ToolCard card) {
        return switch (card.id()) {
            case "tree" -> StudioIcons.Icon.TREE;
            case "rock" -> StudioIcons.Icon.ROCK;
            case "roof" -> StudioIcons.Icon.ROOF;
            case "loft" -> StudioIcons.Icon.LOFT;
            case "stamp" -> StudioIcons.Icon.STAMP;
            case "ramp" -> StudioIcons.Icon.RAMP;
            case "river" -> StudioIcons.Icon.RIVER;
            case "erosion" -> StudioIcons.Icon.EROSION;
            case "paint" -> StudioIcons.Icon.PAINT;
            case "text" -> StudioIcons.Icon.TEXT;
            case "analyze" -> StudioIcons.Icon.ANALYZE;
            case "gravity" -> StudioIcons.Icon.GRAVITY;
            case "snow" -> StudioIcons.Icon.SNOW;
            case "drain" -> StudioIcons.Icon.DRAIN;
            case "waterlog" -> StudioIcons.Icon.WATERLOG;
            case "biome" -> StudioIcons.Icon.BIOME;
            case "symmetry" -> StudioIcons.Icon.SYMMETRY;
            case "capabilities" -> StudioIcons.Icon.CAPABILITIES;
            default -> StudioIcons.Icon.TOOLS;
        };
    }

    private void drawToolCell(StudioUi ui, Rect cell, ToolCard card, Host host, ToolContext context) {
        if (!ui.visible(cell)) {
            return;
        }
        boolean hovered = ui.hovered(cell);
        boolean chosen = card == selected;
        boolean locked = card.locked(context);
        ui.fill(cell, chosen ? CARD_ACTIVE : hovered ? CARD_HOVER : CARD);
        if (chosen) {
            ui.fill(cell.left(2), ACCENT);
        }
        int color = locked ? DISABLED : chosen ? ACCENT_HOVER : hovered ? TEXT : TEXT_SECONDARY;
        ui.icon(icon(card), cell.x() + 5, cell.centerY() - 6, color);
        Rect star = cell.rightPart(14);
        boolean favorite = host.preferences().isToolFavorite(card.id());
        int reserve = locked ? 26 : favorite || hovered ? 16 : 4;
        ui.label(host.tr(card.nameKey()), cell.x() + 21, cell.centerY() - 4, cell.width() - 21 - reserve,
                locked ? DISABLED : chosen ? WHITE : hovered ? TEXT : TEXT_SECONDARY);
        if (locked) {
            ui.icon(StudioIcons.Icon.LOCK, star.x() - 11, cell.centerY() - 6, DISABLED);
        }
        if (favorite || hovered) {
            ui.icon(favorite ? StudioIcons.Icon.STAR : StudioIcons.Icon.STAR_EMPTY, star.x() + 1,
                    cell.centerY() - 6, favorite || ui.hovered(star) ? FAVORITE : MUTED);
        }
        StringBuilder help = new StringBuilder(host.tr(card.nameKey())).append('\n')
                .append(description(card, host));
        if (locked) {
            help.append('\n').append(host.tr("gui.komaedit.tools.locked", String.join(", ", card.allPermissions())));
        }
        help.append('\n').append(host.tr("gui.komaedit.help.favorite_star"));
        ui.register(cell).onClick(() -> select(card, host)).tooltip(help.toString());
        ui.register(star).onClick(() -> toggleFavorite(card, host)).tooltip(help.toString());
    }

    private void toggleFavorite(ToolCard card, Host host) {
        boolean added = host.preferences().toggleToolFavorite(card.id());
        host.savePreferences();
        host.status(host.tr(added ? "gui.komaedit.favorite.added" : "gui.komaedit.favorite.removed",
                host.tr(card.nameKey())));
    }

    /** La descripción para el tooltip, sin la orden del chat (que en el Studio no hace falta). */
    static String description(ToolCard card, Host host) {
        return withoutCommand(host.tr(card.descriptionKey()));
    }

    /** Quita la orden ({@code /komaedit …}) y las banderas entre paréntesis de un texto de ayuda. */
    public static String withoutCommand(String text) {
        return text.replaceAll("\\s*/komaedit[^.\\n]*\\.?$", "")
                .replaceAll("\\s*\\((?:-[\\p{L}\\d_]+(?:,\\s*)?)+\\)", "")
                .strip();
    }

    /**
     * La herramienta elegida, plegada: icono y nombre (pulsar abre la cuadrícula), y a la derecha las recientes para
     * cambiar de un clic, la estrella y copiar la orden.
     */
    private void drawHeader(StudioUi ui, Column column, Host host, boolean compact) {
        ToolCard card = selected();
        Rect row = column.next(compact ? 20 : 22);
        List<ToolCard> recents = new ArrayList<>();
        for (String id : host.preferences().toolRecents()) {
            ToolCard recent = ToolCatalog.byId(id);
            if (recent != null && recent != card && recents.size() < (compact ? 2 : 3)) {
                recents.add(recent);
            }
        }
        int buttons = recents.size() + 2;
        int right = row.right();
        Rect[] icons = new Rect[buttons];
        for (int index = buttons - 1; index >= 0; index--) {
            icons[index] = new Rect(right - 18, row.centerY() - 9, 18, 18);
            right -= 19;
        }
        Rect name = new Rect(row.x(), row.y(), right - row.x() - 4, row.height());
        boolean hovered = ui.hovered(name);
        ui.fill(name, hovered ? CARD_HOVER : CARD);
        ui.fill(name.left(2), ACCENT);
        ui.icon(icon(card), name.x() + 6, name.centerY() - 6, ACCENT_HOVER);
        ui.icon(StudioIcons.Icon.CHEVRON_DOWN, name.right() - 15, name.centerY() - 5, hovered ? TEXT : MUTED);
        ui.bold(host.tr(card.nameKey()), name.x() + 22, name.centerY() - 4, name.width() - 40, WHITE);
        ui.register(name)
                .tooltip(host.tr(card.nameKey()) + "\n" + description(card, host) + "\n"
                        + host.tr("gui.komaedit.tools.change"))
                .onClick(() -> setBrowsing(true));
        for (int index = 0; index < recents.size(); index++) {
            ToolCard recent = recents.get(index);
            Rect zone = icons[index];
            boolean over = ui.hovered(zone);
            if (over) {
                ui.fill(zone, CARD_HOVER);
            }
            ui.icon(icon(recent), zone.centerX() - 6, zone.centerY() - 6, over ? TEXT : MUTED);
            ui.register(zone).tooltip(host.tr("gui.komaedit.tools.recent", host.tr(recent.nameKey())))
                    .onClick(() -> select(recent, host));
        }
        Rect star = icons[recents.size()];
        boolean favorite = host.preferences().isToolFavorite(card.id());
        boolean overStar = ui.hovered(star);
        if (overStar) {
            ui.fill(star, CARD_HOVER);
        }
        ui.icon(favorite ? StudioIcons.Icon.STAR : StudioIcons.Icon.STAR_EMPTY, star.centerX() - 6,
                star.centerY() - 6, favorite ? FAVORITE : overStar ? TEXT : MUTED);
        ui.register(star).tooltip(host.tr(favorite ? "gui.komaedit.tools.unfavorite" : "gui.komaedit.tools.favorite"))
                .onClick(() -> toggleFavorite(card, host));
        Rect copy = icons[recents.size() + 1];
        boolean overCopy = ui.hovered(copy);
        if (overCopy) {
            ui.fill(copy, CARD_HOVER);
        }
        ui.icon(StudioIcons.Icon.COPY, copy.centerX() - 6, copy.centerY() - 6, overCopy ? TEXT : MUTED);
        String command = "/" + card.command(current(), preview(host));
        ui.register(copy).tooltip(host.tr("gui.komaedit.tools.command.copy") + "\n" + command)
                .onClick(() -> {
                    host.copy(command);
                    host.status(host.tr("gui.komaedit.tools.status.copied"));
                });
    }

    private boolean preview(Host host) {
        return selected().previewable() && host.preferences().toolPreview();
    }

    private void drawDetail(StudioUi ui, Column column, Host host, ToolContext context, ToolsInfo info,
                            boolean compact) {
        ToolCard card = selected();
        ToolValues values = current();
        List<ToolProblem> problems = card.problems(values, context);
        for (ToolProblem problem : problems.subList(0, Math.min(compact ? 1 : 2, problems.size()))) {
            drawProblem(ui, column, host, problem);
        }
        if (card instanceof SymmetryCard) {
            drawSymmetryState(ui, column, host, info);
        }
        if (card instanceof CapabilityCard) {
            drawCapabilities(ui, column, host, info);
            return;
        }

        List<ToolParam> essentials = new ArrayList<>();
        List<ToolParam> others = new ArrayList<>();
        for (ToolParam param : card.params()) {
            if (param.visible(values)) {
                (card.essential(param) ? essentials : others).add(param);
            }
        }
        // Primero lo que se elige viendo, después el estilo (presets) y los demás ajustes esenciales.
        for (ToolParam param : essentials) {
            if (param instanceof ToolParam.Choice choice && choice.style() == ToolParam.Choice.Style.GALLERY) {
                drawGallery(ui, column, host, card, choice, compact);
            }
        }
        if (!compact) {
            drawPresets(ui, column, host, card, info);
        }
        boolean opened = false;
        for (ToolParam param : essentials) {
            if (param instanceof ToolParam.Choice choice && choice.style() == ToolParam.Choice.Style.GALLERY) {
                continue;
            }
            if (!opened) {
                column.section();
                opened = true;
            }
            drawParam(ui, column, host, card, param, info);
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
        if (card.id().equals("analyze")) {
            drawMaterials(ui, column, host, info.materials(), compact);
        }

        List<ToolAction> actions = card.actions().stream()
                .filter(action -> !action.id().equals("symmetry_off") || info.symmetry().active())
                .toList();
        boolean presetsHidden = compact && (!card.presets().isEmpty() || seeded(card));
        if (others.isEmpty() && actions.isEmpty() && !presetsHidden) {
            return;
        }
        String key = MORE_KEY + card.id();
        boolean open = host.preferences().expanded(key);
        long changed = others.stream().filter(param -> !values.isDefault(param.id())).count();
        String detail = changed > 0 ? host.tr("gui.komaedit.tools.more.changed", changed)
                : Integer.toString(others.size() + actions.size());
        ui.foldable(column, host.tr("gui.komaedit.tools.more"), detail, open)
                .tooltip(host.tr("gui.komaedit.tools.more.help"))
                .onClick(() -> {
                    host.preferences().setExpanded(key, !open);
                    host.savePreferences();
                });
        if (!open) {
            return;
        }
        if (presetsHidden) {
            drawPresets(ui, column, host, card, info);
        }
        for (ToolParam param : others) {
            drawParam(ui, column, host, card, param, info);
        }
        drawActions(ui, column, host, card, values, context, actions);
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
                for (ToolParam param : selected().params()) {
                    if (param.id().equals(arguments[index])) {
                        arguments[index] = host.tr(param.labelKey());
                    }
                }
            }
        }
        return host.tr(problem.translationKey(), arguments);
    }

    /**
     * Un aviso en una franja: rojo si impide construir, amarillo si solo avisa. Si lo que falta son puntos, la
     * franja lleva a la etapa Trayectoria.
     */
    private void drawProblem(StudioUi ui, Column column, Host host, ToolProblem problem) {
        String text = problemText(host, problem);
        boolean path = problem.id().equals("no_path_or_selection") || problem.id().equals("loft_points");
        String action = path ? host.tr("gui.komaedit.tools.problem.go_path") : "";
        int actionWidth = action.isEmpty() ? 0 : ui.width(action) + 12;
        List<String> lines = ui.wrap(text, column.width() - 16 - actionWidth);
        lines = lines.subList(0, Math.min(2, lines.size()));
        Rect card = column.next(Math.max(SMALL_ROW, lines.size() * 10 + 6));
        int color = problem.blocking() ? DANGER : WARNING;
        ui.fill(card, problem.blocking() ? DANGER_DIM : WARNING_DIM);
        ui.fill(card.left(2), color);
        for (int index = 0; index < lines.size(); index++) {
            ui.label(lines.get(index), card.x() + 8, card.y() + 4 + index * 10, card.width() - 16 - actionWidth,
                    color);
        }
        ui.register(card).tooltip(text);
        if (path) {
            Rect go = card.rightPart(actionWidth).inset(2, 2);
            ui.button(go, action, StudioUi.Tone.SECONDARY).tooltip(text).onClick(host::goToPath);
        }
    }

    private static boolean seeded(ToolCard card) {
        return card.params().stream().anyMatch(param -> param.id().equals("seed"));
    }

    private void drawPresets(StudioUi ui, Column column, Host host, ToolCard card, ToolsInfo info) {
        List<ToolPreset> presets = card.presets();
        boolean seeded = seeded(card);
        Map<String, String> saved = !card.savedPresets() ? Map.of()
                : card.id().equals("tree") ? info.treePresets() : info.rockPresets();
        if (presets.isEmpty() && !seeded && saved.isEmpty()) {
            return;
        }
        // Sin título: son pastillas de un clic y su tooltip dice que son presets; así caben en una fila.
        column.section();
        List<String> labels = new ArrayList<>();
        presets.forEach(preset -> labels.add(host.tr(preset.labelKey())));
        if (seeded) {
            labels.add(host.tr("gui.komaedit.preset.random"));
        }
        List<String> names = new ArrayList<>(saved.keySet());
        names.forEach(name -> labels.add("#" + name));
        int[] widths = labels.stream().mapToInt(label -> ui.chipWidth(label)).toArray();
        List<Rect> cells = column.flow(widths, 14);
        String kind = host.tr("gui.komaedit.palette.kind.preset");
        for (int index = 0; index < presets.size(); index++) {
            ToolPreset preset = presets.get(index);
            ui.chip(cells.get(index), labels.get(index), false, TEXT_SECONDARY)
                    .tooltip(kind + ": " + labels.get(index) + "\n" + host.tr(preset.helpKey()))
                    .group("tools:preset")
                    .onClick(() -> {
                        set(card.apply(current(), preset));
                        host.status(host.tr("gui.komaedit.preset.applied", host.tr(preset.labelKey())));
                    });
        }
        int next = presets.size();
        if (seeded) {
            ui.chip(cells.get(next), labels.get(next), false, TEXT_SECONDARY)
                    .tooltip(labels.get(next) + "\n" + host.tr("gui.komaedit.tools.random.help"))
                    .group("tools:preset")
                    .onClick(() -> randomSeed(host));
            next++;
        }
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            String settings = saved.get(name);
            ui.chip(cells.get(next + index), "#" + name, false, FAVORITE)
                    .tooltip("#" + name + "\n" + settings)
                    .group("tools:preset")
                    .onClick(() -> applySaved(card, name, settings, host));
        }
    }

    /** Aplica un preset de la tarjeta elegida por su id (lo usa la paleta de órdenes). */
    public void applyPreset(ToolCard card, ToolPreset preset, Host host) {
        select(card, host);
        set(card.apply(current(), preset));
        host.status(host.tr("gui.komaedit.preset.applied", host.tr(preset.labelKey())));
    }

    /** Variante con semilla nueva; el resto de ajustes se queda como está. */
    public void randomSeed(Host host) {
        if (!seeded(selected())) {
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

    private void drawParam(StudioUi ui, Column column, Host host, ToolCard card, ToolParam param, ToolsInfo info) {
        switch (param) {
            case ToolParam.Number number -> drawNumber(ui, column, host, card, number);
            case ToolParam.Choice choice -> {
                switch (choice.style()) {
                    case CYCLE -> drawCycle(ui, column, host, card, choice, info);
                    case BUTTONS -> {
                        // En compacto, una fila ‹ valor ›: los botones con su título gastarían dos.
                        if (row < Column.ROW) {
                            drawCycle(ui, column, host, card, choice, info);
                        } else {
                            drawButtons(ui, column, host, card, choice, info);
                        }
                    }
                    case GALLERY -> drawGallery(ui, column, host, card, choice, false);
                    case SEARCH -> drawSearchChoice(ui, column, host, card, choice, info);
                }
            }
            case ToolParam.Toggle toggle -> {
                boolean on = current().toggle(toggle.id());
                ui.toggle(column.next(row), host.tr(toggle.labelKey()), on, !current().isDefault(toggle.id()))
                        .tooltip(help(host, card, toggle))
                        .group("tools:" + toggle.id())
                        .onReset(() -> set(current().reset(toggle.id())))
                        .onClick(() -> set(current().with(toggle.id(), !on)));
            }
            case ToolParam.Block block -> drawBlock(ui, column, host, card, block);
            case ToolParam.Text text -> drawText(ui, column, host, card, text);
        }
    }

    private static String help(Host host, ToolCard card, ToolParam param) {
        return host.tr(param.labelKey()) + "\n" + withoutCommand(host.tr(card.helpKey(param)));
    }

    /** Lo que se ve de un número de la tarjeta elegida. */
    NumberView numberView(Host host, ToolCard card, ToolParam.Number number) {
        return NumberView.of(current().number(number.id()), card.autoHint(number.id(), current()),
                number.defaultValue(), number.defaultValue(), number.auto(), number.range(), number.decimals(),
                number.unit(), host::tr);
    }

    private void drawNumber(StudioUi ui, Column column, Host host, ToolCard card, ToolParam.Number number) {
        Rect row = column.next(this.row);
        if (!ui.visible(row)) {
            return;
        }
        NumberView view = numberView(host, card, number);
        String defaultText = number.auto() ? host.tr("gui.komaedit.tools.auto")
                : number.unit().format(number.defaultValue(), number.decimals(), host::tr);
        String key = "tools:number:" + number.id();
        NumericRange range = number.range();
        ui.stepper(row, new StudioUi.Stepper(host.tr(number.labelKey()), view.value(), view.shortValue(),
                view.fraction(), view.defaultFraction(), view.modified(), view.range(), numbers.editing(key),
                numbers.dragging(key), numbers.input(),
                direction -> set(current().with(number.id(), range.stepped(numberView(host, card, number).base(),
                        direction, host.shift()))),
                (x, y) -> numbers.press(key, numberView(host, card, number).base(), x),
                () -> set(current().reset(number.id())),
                view.help(help(host, card, number), defaultText, host::tr), "tools:" + number.id(), key));
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

    private void drawCycle(StudioUi ui, Column column, Host host, ToolCard card, ToolParam.Choice choice,
                           ToolsInfo info) {
        Rect row = column.next(this.row);
        if (!ui.visible(row)) {
            return;
        }
        List<String> options = options(choice, info);
        String value = current().choice(choice.id());
        ui.cycle(row, host.tr(choice.labelKey()), optionLabel(choice, value, host), !current().isDefault(choice.id()),
                        direction -> {
                            int index = Math.max(0, options.indexOf(current().choice(choice.id())));
                            set(current().with(choice.id(),
                                    options.get(Math.floorMod(index + direction, options.size()))));
                        })
                .onReset(() -> set(current().reset(choice.id())))
                .tooltip(help(host, card, choice) + "\n" + host.tr("gui.komaedit.help.cycle"))
                .group("tools:" + choice.id());
    }

    private void drawCaption(StudioUi ui, Column column, String text, boolean modified) {
        Rect caption = column.next(9);
        if (modified) {
            ui.fill(new Rect(caption.x() - 5, caption.y() + 3, 2, 2), ACCENT);
        }
        ui.label(text, caption.x(), caption.y(), caption.width(), TEXT_SECONDARY);
    }

    private void drawButtons(StudioUi ui, Column column, Host host, ToolCard card, ToolParam.Choice choice,
                             ToolsInfo info) {
        drawCaption(ui, column, host.tr(choice.labelKey()), !current().isDefault(choice.id()));
        List<String> options = options(choice, info);
        String value = current().choice(choice.id());
        int[] widths = new int[options.size()];
        String[] labels = new String[options.size()];
        for (int index = 0; index < options.size(); index++) {
            labels[index] = optionLabel(choice, options.get(index), host);
            widths[index] = Math.min(column.width(), ui.width(labels[index]) + 14);
        }
        List<Rect> cells = column.flow(widths, SMALL_ROW);
        String help = help(host, card, choice);
        for (int index = 0; index < options.size(); index++) {
            String option = options.get(index);
            ui.button(cells.get(index), labels[index], option.equals(value) ? StudioUi.Tone.SELECTED
                            : StudioUi.Tone.SECONDARY)
                    .tooltip(labels[index] + "\n" + help)
                    .group("tools:" + choice.id())
                    .onReset(() -> set(current().reset(choice.id())))
                    .onClick(() -> set(current().with(choice.id(), option)));
        }
    }

    /**
     * Miniaturas con su nombre debajo. Caben {@code rows} filas (una en pantallas pequeñas); si hay más opciones,
     * el título lleva ‹ › para pasar de página, y la página siempre empieza enseñando la elegida.
     */
    private void drawGallery(StudioUi ui, Column column, Host host, ToolCard card, ToolParam.Choice choice,
                             boolean compact) {
        String value = current().choice(choice.id());
        boolean custom = card.id().equals("stamp") && !current().text("custom").isBlank();
        String chosenName = custom ? current().text("custom").strip() : host.tr(choice.optionKey(value));
        List<String> options = choice.options();
        int perRow = Math.max(2, (column.width() + Column.GAP) / (TILE + Column.GAP));
        int tile = Math.min(MAX_TILE, (column.width() - (perRow - 1) * Column.GAP) / perRow);
        int rows = compact ? 1 : 2;
        int tileHeight = compact ? 40 : Math.min(tile + 8, 54);
        int perPage = perRow * rows;
        int pageCount = (options.size() + perPage - 1) / perPage;
        String pageKey = card.id() + ":" + choice.id();
        int page = Math.clamp(pages.computeIfAbsent(pageKey, ignored -> Math.max(0, options.indexOf(value))
                / perPage), 0, Math.max(0, pageCount - 1));

        column.section();
        Rect title = column.next(10);
        if (!current().isDefault(choice.id())) {
            ui.fill(new Rect(title.x() - 5, title.y() + 4, 2, 2), ACCENT);
        }
        String caption = host.tr(choice.labelKey()).toUpperCase(Locale.ROOT);
        int arrows = pageCount > 1 ? 36 : 0;
        ui.label(caption, title.x(), title.y() + 1, title.width() - arrows, MUTED);
        int nameX = title.x() + ui.width(caption) + 6;
        ui.label(chosenName, nameX, title.y() + 1, title.right() - arrows - nameX, TEXT);
        if (pageCount > 1) {
            Rect previous = new Rect(title.right() - 34, title.y() - 2, 16, 13);
            Rect next = new Rect(title.right() - 16, title.y() - 2, 16, 13);
            ui.button(previous, "‹", page > 0 ? StudioUi.Tone.SECONDARY : StudioUi.Tone.DISABLED)
                    .tooltip(host.tr("gui.komaedit.page", page + 1, pageCount))
                    .group("tools:" + choice.id() + ":page")
                    .onClick(() -> pages.put(pageKey, Math.max(0, page - 1)));
            ui.button(next, "›", page < pageCount - 1 ? StudioUi.Tone.SECONDARY : StudioUi.Tone.DISABLED)
                    .tooltip(host.tr("gui.komaedit.page", page + 1, pageCount))
                    .group("tools:" + choice.id() + ":page")
                    .onClick(() -> pages.put(pageKey, Math.min(pageCount - 1, page + 1)));
        }
        int start = page * perPage;
        int end = Math.min(options.size(), start + perPage);
        for (int rowStart = start; rowStart < end; rowStart += perRow) {
            Rect row = column.next(tileHeight);
            for (int index = rowStart; index < Math.min(end, rowStart + perRow); index++) {
                String option = options.get(index);
                Rect cell = new Rect(row.x() + (index - rowStart) * (tile + Column.GAP), row.y(), tile, tileHeight);
                if (!ui.visible(cell)) {
                    continue;
                }
                boolean chosen = option.equals(value) && !custom;
                StudioImage image = ChoiceThumbnails.get(card.id(), choice.id(), option);
                String label = host.tr(choice.optionKey(option));
                String description = card.id().equals("stamp") && StampLibrary.descriptions().containsKey(option)
                        ? "\n" + host.tr("gui.komaedit.tools.option.stamp." + option + ".help") : "";
                ui.tile(cell, image, icon(card), label, chosen)
                        .tooltip(label + description)
                        .group("tools:" + choice.id())
                        .onReset(() -> set(current().reset(choice.id())))
                        .onClick(() -> {
                            ToolValues next = current().with(choice.id(), option);
                            set(card.id().equals("stamp") ? next.with("custom", "") : next);
                        });
            }
        }
    }

    private void drawSearchChoice(StudioUi ui, Column column, Host host, ToolCard card, ToolParam.Choice choice,
                                  ToolsInfo info) {
        drawCaption(ui, column, host.tr(choice.labelKey()), !current().isDefault(choice.id()));
        String value = current().choice(choice.id());
        Rect box = column.next(18);
        boolean typing = field == Field.BIOME;
        List<String> matches = biomeMatches(options(choice, info), value);
        ui.searchField(box, biomeSearch, optionLabel(choice, value, host), typing, matches.size(), () -> {
                    biomeSearch = "";
                    stopEditing();
                })
                .key("tools:biome")
                .tooltip(help(host, card, choice))
                .onClick(() -> field = Field.BIOME);
        if (matches.isEmpty()) {
            Rect empty = column.next(SMALL_ROW);
            ui.fill(empty, CARD);
            ui.label(host.tr(info.biomes().isEmpty() ? "gui.komaedit.tools.biome.none"
                    : "gui.komaedit.tools.search.empty"), empty.x() + 8, empty.y() + 4, empty.width() - 16, MUTED);
            return;
        }
        for (String option : matches) {
            Rect row = column.next(SMALL_ROW);
            if (!ui.visible(row)) {
                continue;
            }
            boolean chosen = option.equals(value);
            boolean hovered = ui.hovered(row);
            ui.fill(row, chosen ? CARD_ACTIVE : hovered ? CARD_HOVER : CARD);
            if (chosen) {
                ui.fill(row.left(2), ACCENT);
            }
            ui.label(optionLabel(choice, option, host), row.x() + 7, row.y() + 4, row.width() - 14,
                    chosen ? WHITE : TEXT_SECONDARY);
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

    private void drawBlock(StudioUi ui, Column column, Host host, ToolCard card, ToolParam.Block block) {
        Rect row = column.next(this.row);
        if (!ui.visible(row)) {
            return;
        }
        String value = current().block(block.id());
        String hint = card.autoHint(block.id(), current());
        String shown = value != null ? firstBlock(value) : hint != null ? firstBlock(hint) : null;
        String name;
        if (value != null) {
            name = blockList(value, host);
        } else if (hint != null) {
            name = host.tr("gui.komaedit.tools.block.auto", blockList(hint, host));
        } else {
            String autoKey = card.nameKey() + ".auto." + block.id();
            String described = host.tr(autoKey);
            name = described.equals(autoKey) ? host.tr("gui.komaedit.tools.block.none") : described;
        }
        String label = host.tr(block.labelKey());
        String title = host.tr("gui.komaedit.blocks.for", host.tr(card.nameKey()), label);
        StudioUi.Widget[] zones = ui.blockRow(row, new StudioUi.BlockRow(label, shown, name, value == null,
                !current().isDefault(block.id()),
                help(host, card, block) + "\n" + host.tr("gui.komaedit.tools.block.help"), true,
                host.tr("gui.komaedit.tools.block.eyedropper")));
        zones[0].group("tools:" + block.id())
                .onReset(() -> set(current().reset(block.id())))
                .onClick(() -> host.pickBlock(title, shown, id -> set(current().with(block.id(), compact(id)))));
        zones[1].group("tools:" + block.id()).onClick(() -> {
            String looked = host.lookedAtBlock();
            if (looked == null) {
                host.status(host.tr("gui.komaedit.blocks.look_nothing"));
                return;
            }
            set(current().with(block.id(), compact(looked)));
            host.status(host.tr("gui.komaedit.blocks.looked", host.blockName(looked)));
        });
    }

    /** «Tablones de abeto» o, con varios, «Piedra +2». */
    private static String blockList(String list, Host host) {
        String[] parts = list.split(",");
        String first = host.blockName(firstBlock(list));
        return parts.length > 1 ? first + " +" + (parts.length - 1) : first;
    }

    static String firstBlock(String list) {
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

    private void drawText(StudioUi ui, Column column, Host host, ToolCard card, ToolParam.Text text) {
        drawCaption(ui, column, host.tr(text.labelKey()), !current().isDefault(text.id()));
        Rect box = column.next(18);
        boolean editing = field == Field.TEXT && fieldParam.equals(text.id());
        String value = editing ? input : current().text(text.id());
        String hint = card.autoHint(text.id(), current());
        String placeholder = hint != null ? hint : host.tr(text.placeholderKey());
        ui.field(box, value, placeholder, editing, false)
                .key("tools:text:" + text.id())
                .tooltip(help(host, card, text))
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

    // ── Acciones y materiales ───────────────────────────────────────────────────────────────────────────

    private void drawActions(StudioUi ui, Column column, Host host, ToolCard card, ToolValues values,
                             ToolContext context, List<ToolAction> actions) {
        if (actions.isEmpty()) {
            return;
        }
        int[] widths = actions.stream().mapToInt(action -> ui.width(host.tr(action.labelKey())) + 16).toArray();
        List<Rect> cells = column.flow(widths, SMALL_ROW);
        for (int index = 0; index < actions.size(); index++) {
            ToolAction action = actions.get(index);
            String missing = card.missingPermission(action, values, context);
            String help = host.tr(action.helpKey()) + (missing == null ? ""
                    : "\n" + host.tr("gui.komaedit.tools.problem.permission", missing));
            ui.button(cells.get(index), host.tr(action.labelKey()), missing != null ? StudioUi.Tone.DISABLED
                            : action == namingAction ? StudioUi.Tone.SELECTED : StudioUi.Tone.SECONDARY)
                    .tooltip(host.tr(action.labelKey()) + "\n" + help)
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
            ui.button(save, host.tr("gui.komaedit.save"), validName(input) ? StudioUi.Tone.PRIMARY
                    : StudioUi.Tone.DISABLED).onClick(() -> saveNamed(host));
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

    private void drawMaterials(StudioUi ui, Column column, Host host, ToolsInfo.Materials materials, boolean compact) {
        ui.section(column, host.tr("gui.komaedit.tools.materials"),
                materials == null || materials.blocks() == 0 ? "" : group(materials.blocks()));
        if (materials == null) {
            ui.paragraph(column, host.tr("gui.komaedit.tools.materials.none"), MUTED, compact ? 2 : 0);
            return;
        }
        if (materials.blocks() == 0) {
            ui.paragraph(column, host.tr("gui.komaedit.tools.materials.empty"), MUTED, 0);
            return;
        }
        if (!compact) {
            ui.paragraph(column, host.tr("gui.komaedit.tools.materials.total", group(materials.blocks()),
                    materials.types(), group(materials.items()), group(materials.slots()),
                    group(materials.chests())), TEXT_SECONDARY, 0);
        }
        ui.iconButton(column.next(SMALL_ROW), StudioIcons.Icon.COPY, host.tr("gui.komaedit.tools.materials.copy"),
                        StudioUi.Tone.SECONDARY)
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
            ui.fill(row, ui.hovered(row) ? CARD_HOVER : CARD);
            ui.item(line.item(), row.x() + 2, row.y() + 1);
            String amount = group(line.amount());
            int amountWidth = ui.width(amount) + 6;
            String stacks = line.maxStack() > 1 && line.amount() >= line.maxStack()
                    ? host.tr("gui.komaedit.tools.materials.stacks", line.stacks(), line.remainder()) : "";
            int stacksWidth = stacks.isEmpty() || compact ? 0 : ui.width(stacks) + 6;
            int nameWidth = row.width() - 24 - amountWidth - stacksWidth;
            String name = host.blockName(line.item());
            ui.label(name, row.x() + 22, row.y() + 5, nameWidth, TEXT_SECONDARY);
            ui.label(amount, row.right() - amountWidth - stacksWidth, row.y() + 5, amountWidth, TEXT);
            if (stacksWidth > 0) {
                ui.label(stacks, row.right() - stacksWidth, row.y() + 5, stacksWidth, MUTED);
            }
            ui.register(row).tooltip(name + "\n" + line.item() + ": " + amount + (stacks.isEmpty() ? "" : " " + stacks));
        }
        if (materials.hidden() > 0) {
            ui.paragraph(column, host.tr("gui.komaedit.tools.materials.more", materials.hidden()), MUTED, 0);
        }
    }

    private static String group(long value) {
        return com.komainu.moxia.progress.ProgressLine.group(value);
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

    private void drawSymmetryState(StudioUi ui, Column column, Host host, ToolsInfo info) {
        ToolsInfo.Symmetry symmetry = info.symmetry();
        Rect card = column.next(30);
        ui.fill(card, CARD);
        ui.fill(card.left(2), symmetry.active() ? SUCCESS : DISABLED);
        String state = host.tr(symmetry.active() ? "gui.komaedit.tools.symmetry.on"
                : "gui.komaedit.tools.symmetry.off", symmetry.mode());
        ui.bold(state, card.x() + 8, card.y() + 5, card.width() - 16, symmetry.active() ? SUCCESS : TEXT_SECONDARY);
        String center = symmetry.center().isEmpty() ? host.tr("gui.komaedit.tools.symmetry.no_center")
                : host.tr(symmetry.elsewhere() ? "gui.komaedit.tools.symmetry.center_elsewhere"
                : "gui.komaedit.tools.symmetry.center", symmetry.center());
        ui.label(center, card.x() + 8, card.y() + 17, card.width() - 16, symmetry.elsewhere() ? WARNING : MUTED);
        ui.register(card).tooltip(state + "\n" + center);
    }

    private void drawCapabilities(StudioUi ui, Column column, Host host, ToolsInfo info) {
        ToolsInfo.Capabilities state = info.capabilities();
        if (state == null) {
            ui.paragraph(column, host.tr("gui.komaedit.tools.capabilities.unavailable"), MUTED, 0);
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
            String help = name + "\n" + host.tr("gui.komaedit.capability." + capability.id() + ".desc")
                    + (permitted ? "" : "\n" + host.tr("gui.komaedit.tools.problem.permission",
                    capability.permission()));
            StudioUi.Widget toggle = ui.toggle(row, label, on && permitted);
            if (!permitted) {
                ui.icon(StudioIcons.Icon.LOCK, row.right() - 44, row.centerY() - 6, DISABLED);
            }
            toggle.tooltip(help)
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
        capabilityNumber(ui, column, host, "angel", state.angelDistance(), 1, state.maxReach());
        capabilityNumber(ui, column, host, "reach", state.reachDistance(), 5, state.maxReach());
        capabilityNumber(ui, column, host, "extrude", state.extrudeLimit(), 1, state.maxLayerBlocks());
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
            capabilityNumber(ui, column, host, "flyspeed", state.flySpeed(), 5, state.maxFlySpeed());
        }
    }

    private NumericRange capabilityRange(ToolParam.Number param, int minimum, int maximum) {
        return new NumericRange(minimum, Math.max(minimum, maximum), param.range().step(),
                Math.max(param.range().step(), param.range().bigStep()));
    }

    /** Un número de las capacidades: se ve al momento y se manda al dejar de tocarlo. */
    private void capabilityNumber(StudioUi ui, Column column, Host host, String id, int serverValue, int minimum,
                                  int maximum) {
        Rect row = column.next(this.row);
        if (!ui.visible(row)) {
            return;
        }
        ToolCard card = ToolCatalog.byId("capabilities");
        ToolParam.Number param = (ToolParam.Number) card.defaults().param(id);
        NumericRange range = capabilityRange(param, minimum, maximum);
        int value = range.clamp(pendingOptions.getOrDefault(id, serverValue));
        String key = "tools:number:" + id;
        NumberView view = NumberView.of(value, null, value, param.defaultValue(), false, range, param.decimals(),
                param.unit(), host::tr);
        ui.stepper(row, new StudioUi.Stepper(host.tr(param.labelKey()), view.value(), view.shortValue(),
                view.fraction(), view.defaultFraction(), view.modified(), view.range(), numbers.editing(key),
                numbers.dragging(key), numbers.input(),
                direction -> queueOption(id, range.stepped(range.clamp(pendingOptions.getOrDefault(id, serverValue)),
                        direction, host.shift()), host),
                (x, y) -> numbers.press(key, value, x),
                () -> queueOption(id, param.defaultValue(), host),
                view.help(help(host, card, param), param.unit().format(param.defaultValue(), param.decimals(),
                        host::tr), host::tr), "tools:" + id, key));
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

    /** Icono de sobre qué trabaja la herramienta elegida, para el pie. */
    public StudioIcons.Icon targetIcon(Host host) {
        ToolContext context = host.context();
        return switch (selected().target()) {
            case SELECTION -> StudioIcons.Icon.SELECTION;
            case PATH_OR_LOOK -> context.pathPoints() > 0 ? StudioIcons.Icon.PATH : StudioIcons.Icon.LOOK;
            case PATH_OR_SELECTION -> context.pathPoints() > 0 ? StudioIcons.Icon.PATH : StudioIcons.Icon.SELECTION;
            case LOOK -> StudioIcons.Icon.LOOK;
            case HAND -> StudioIcons.Icon.HAND;
            case MODE -> StudioIcons.Icon.MODE;
        };
    }

    /** «Selección 10 × 6 × 12», «3 puntos», «Donde miras»: sobre qué trabajará Construir. */
    public String targetText(Host host) {
        ToolsInfo info = host.info();
        ToolContext context = host.context();
        ToolCard card = selected();
        return switch (card.target()) {
            case SELECTION -> info.hasSelection()
                    ? host.tr("gui.komaedit.tools.footer.selection", info.selection()[0], info.selection()[1],
                    info.selection()[2]) : host.tr("gui.komaedit.tools.footer.no_selection");
            case PATH_OR_LOOK, PATH_OR_SELECTION -> context.pathPoints() > 0
                    ? host.tr("gui.komaedit.tools.footer.points", context.pathPoints())
                    : host.tr(card.target() == ToolTarget.PATH_OR_LOOK || !info.hasSelection()
                    ? "gui.komaedit.tools.footer.look" : "gui.komaedit.tools.footer.selection_short");
            case LOOK -> host.tr("gui.komaedit.tools.footer.look");
            case HAND -> host.tr("gui.komaedit.tools.footer.hand");
            case MODE -> card instanceof SymmetryCard
                    ? host.tr(info.symmetry().active() ? "gui.komaedit.tools.footer.on"
                    : "gui.komaedit.tools.footer.off")
                    : host.tr("gui.komaedit.tools.footer.active", info.capabilities() == null ? 0
                    : Integer.bitCount(info.capabilities().enabled() & info.capabilities().permitted()));
        };
    }

    /** «Árbol · donde miras», para quien quiere el resumen en una línea (la paleta de órdenes, las pruebas). */
    public String footerSummary(Host host) {
        return host.tr(selected().nameKey()) + " · " + targetText(host);
    }

    /** El primer problema que impide construir, o {@code null}. */
    public ToolProblem blocking(Host host) {
        return selected().problems(current(), host.context()).stream().filter(ToolProblem::blocking).findFirst()
                .orElse(null);
    }

    public String buildLabel(Host host) {
        ToolProblem problem = blocking(host);
        if (problem != null) {
            return host.tr(problem.translationKey() + ".short");
        }
        if (selected() instanceof SymmetryCard && host.info().symmetry().active()) {
            return host.tr("gui.komaedit.tools.symmetry.apply");
        }
        return host.tr(selected().buildKey());
    }

    public StudioUi.Tone buildTone(Host host) {
        return blocking(host) != null ? StudioUi.Tone.DISABLED : StudioUi.Tone.PRIMARY;
    }

    public String buildHelp(Host host) {
        ToolProblem problem = blocking(host);
        String command = "/" + selected().command(current(), preview(host));
        return problem == null ? host.tr("gui.komaedit.tools.build.help", host.tr(selected().buildKey()), command)
                : problemText(host, problem);
    }

    /** La orden de Construir con lo de ahora y la vista previa pedida (o {@code null} si algo lo impide). */
    public String buildCommand(Host host, boolean preview) {
        if (blocking(host) != null) {
            return null;
        }
        return selected().command(current(), preview && selected().previewable());
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
        boolean preview = preview(host);
        send(host, selected().command(current(), preview));
        host.preferences().pushToolRecent(selected().id());
        host.savePreferences();
        host.status(host.tr(preview ? "gui.komaedit.tools.status.preview_sent" : "gui.komaedit.tools.status.built",
                host.tr(selected().nameKey())));
    }

    private void send(Host host, String command) {
        host.send(command);
        refreshSoon();
    }

    // ── Teclado y ratón ─────────────────────────────────────────────────────────────────────────────────

    /** Si hay un campo de la etapa abierto: las letras se escriben y ningún atajo se dispara. */
    public boolean typing() {
        return field != Field.NONE || numbers.editing();
    }

    /** Si el control con esta clave es el campo que se está escribiendo (un clic en él no lo cierra). */
    public boolean owns(Object key) {
        if (numbers.editing(key)) {
            return true;
        }
        return switch (field) {
            case NONE -> false;
            case SEARCH -> "tools:search".equals(key);
            case TEXT -> ("tools:text:" + fieldParam).equals(key);
            case PRESET_NAME -> "tools:preset_name".equals(key);
            case BIOME -> "tools:biome".equals(key);
        };
    }

    /** Abre el buscador de herramientas ({@code Ctrl+F} o {@code /} en esta etapa). */
    public void focusSearch(Host host) {
        commitField(host);
        browsing = true;
        field = Field.SEARCH;
        host.status(host.tr("gui.komaedit.search.typing"));
    }

    public void stopEditing() {
        field = Field.NONE;
        fieldParam = "";
        input = "";
        numbers.reset();
    }

    /** Pulsar fuera de un campo: el texto o el número escrito se aplica; la búsqueda se queda como está. */
    public void commitField(Host host) {
        if (numbers.editing()) {
            commitNumber(host);
        }
        if (field == Field.TEXT) {
            set(current().with(fieldParam, input));
        }
        if (field != Field.PRESET_NAME && field != Field.SEARCH) {
            field = Field.NONE;
            fieldParam = "";
            input = "";
        }
        if (field == Field.SEARCH) {
            field = Field.NONE;
        }
    }

    private void commitNumber(Host host) {
        Object key = numbers.editingKey();
        String typed = numbers.commit();
        String id = key.toString().substring("tools:number:".length());
        if (typed.isBlank()) {
            return;
        }
        ToolParam.Number param = selected() instanceof CapabilityCard
                ? (ToolParam.Number) selected().defaults().param(id) : (ToolParam.Number) current().param(id);
        Integer parsed = param.unit().parse(typed, param.decimals());
        if (parsed == null) {
            host.status(host.tr("gui.komaedit.number.invalid"));
            return;
        }
        if (selected() instanceof CapabilityCard) {
            queueOption(id, parsed, host);
            flushOptions(host, true);
            return;
        }
        set(current().with(id, param.range().clamp(parsed)));
    }

    public boolean key(Key key, boolean control, Host host) {
        if (numbers.editing()) {
            switch (key) {
                case ENTER -> commitNumber(host);
                case ESCAPE -> numbers.cancel();
                case BACKSPACE -> numbers.erase(control);
                default -> {
                }
            }
            return true;
        }
        if (field == Field.NONE) {
            if (key == Key.ESCAPE && browsing && selected != null) {
                browsing = false;
                search = "";
                return true;
            }
            return false;
        }
        switch (key) {
            case ENTER -> {
                switch (field) {
                    case SEARCH -> {
                        List<ToolCard> shown = visibleCards(host);
                        stopEditing();
                        if (!search.isBlank() && !shown.isEmpty()) {
                            select(shown.getFirst(), host);
                        }
                    }
                    case BIOME -> {
                        ToolCard card = selected();
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
                            if (selected != null) {
                                browsing = false;
                            }
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
        if (numbers.editing()) {
            numbers.type(character);
            return true;
        }
        if (field == Field.NONE || !ToolCard.legal(String.valueOf(character))) {
            return field != Field.NONE;
        }
        switch (field) {
            case SEARCH -> {
                if (search.length() < 32) {
                    search += character;
                }
            }
            case BIOME -> {
                if (biomeSearch.length() < 64) {
                    biomeSearch += Character.toLowerCase(character);
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
        return numbers.dragging();
    }

    /** Arrastrar el número en horizontal lo cambia como un deslizador; con Mayús va fino. */
    public void drag(double mouseX, Host host) {
        Object key = numbers.draggingKey();
        if (key == null) {
            return;
        }
        String id = key.toString().substring("tools:number:".length());
        if (selected() instanceof CapabilityCard) {
            ToolParam.Number param = (ToolParam.Number) selected().defaults().param(id);
            ToolsInfo.Capabilities state = host.info().capabilities();
            int maximum = state == null ? param.range().max() : switch (id) {
                case "angel", "reach" -> state.maxReach();
                case "extrude" -> state.maxLayerBlocks();
                default -> state.maxFlySpeed();
            };
            int minimum = id.equals("reach") || id.equals("flyspeed") ? 5 : 1;
            Integer next = numbers.drag(mouseX, capabilityRange(param, minimum, maximum), host.shift());
            if (next != null) {
                queueOption(id, next, host);
            }
            return;
        }
        ToolParam.Number param = (ToolParam.Number) current().param(id);
        Integer next = numbers.drag(mouseX, param.range(), host.shift());
        if (next != null) {
            set(current().with(id, next));
        }
    }

    /** Al soltar sin mover se abre el campo para escribir el número (en su unidad). */
    public void release(Host host) {
        Object key = numbers.draggingKey();
        if (key == null) {
            return;
        }
        String id = key.toString().substring("tools:number:".length());
        String text;
        if (selected() instanceof CapabilityCard) {
            ToolParam.Number param = (ToolParam.Number) selected().defaults().param(id);
            Integer pending = pendingOptions.get(id);
            text = pending == null ? "" : param.unit().number(pending, param.decimals());
        } else {
            Integer value = current().number(id);
            ToolParam.Number param = (ToolParam.Number) current().param(id);
            text = value == null ? "" : param.unit().number(value, param.decimals());
        }
        if (field != Field.NONE) {
            commitField(host);
        }
        if (numbers.release(text)) {
            host.status(host.tr("gui.komaedit.number.typing"));
        }
    }

    /** Grupo de historial del arrastre en curso. */
    public String dragGroup() {
        Object key = numbers.draggingKey();
        return key == null ? null : "tools:" + key.toString().substring("tools:number:".length());
    }

    /** La unidad de un ajuste numérico de la tarjeta elegida (para las pruebas y la ayuda). */
    public ValueUnit unit(String id) {
        return ((ToolParam.Number) current().param(id)).unit();
    }

    @Override
    public String toString() {
        return "ToolsPanel[" + selected().id() + ", browsing=" + browsing() + ", '"
                + search.toLowerCase(Locale.ROOT) + "']";
    }
}
