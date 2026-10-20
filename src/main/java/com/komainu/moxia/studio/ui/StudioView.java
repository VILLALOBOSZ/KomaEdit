package com.komainu.moxia.studio.ui;

import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.recipe.ProceduralInstanceSummary;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.studio.BuildEstimate;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.StudioShortcuts;
import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.layout.StudioLayout;
import com.komainu.moxia.studio.net.JobStatusPayload;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ToolPreset;
import com.komainu.moxia.studio.tools.ToolTarget;
import com.komainu.moxia.studio.tools.ui.JobStrip;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import com.komainu.moxia.studio.tools.ui.ToolsPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.komainu.moxia.studio.ui.StudioTheme.*;

/**
 * El Studio entero sin Minecraft: barra de herramientas con el nombre de la etapa, navegación por grupos, contenido
 * desplazable de cada etapa, pie con sobre qué se trabaja y el botón Construir, y las ventanas de encima (biblioteca
 * de bloques, paleta de órdenes, historial del mundo y chuleta de atajos). La pantalla del cliente solo le pasa el
 * ratón y el teclado, le da un {@link StudioHost} y se queda con lo que pasa en el mundo (puntos, flechas, cámara).
 * Las pruebas y las maquetas usan esta misma clase.
 */
public final class StudioView {
    /** Qué tiene que hacer la pantalla con un clic que el Studio no se queda. */
    public enum Click {
        /** El Studio lo atendió. */
        HANDLED,
        /** Clic izquierdo en el mundo: un punto de la trayectoria o sus flechas. */
        WORLD,
        /** Clic derecho en el mundo: girar la cámara arrastrando. */
        CAMERA
    }

    public static final long STATUS_MILLIS = 4_000L;
    private static final int HISTORY_WIDTH = 220;
    private static final int HISTORY_ROWS = 10;

    private final ToolsPanel tools = new ToolsPanel();
    private final FigureStages figure = new FigureStages();
    private final ColorStage color = new ColorStage();
    private final BlueprintStage blueprints = new BlueprintStage();
    private final BlockPicker picker = new BlockPicker();
    private final CommandPalette palette = new CommandPalette();
    private final LivePreview live = new LivePreview();

    private StudioStage stage = StudioStage.FIGURE;
    private boolean loaded;
    private int panelWidth = StudioLayout.DEFAULT_PANEL_WIDTH;
    private StudioLayout.Frame frame;
    private StudioUi ui;
    private int scroll;
    private int contentHeight;
    private String scrollKey;
    /** El desplazamiento se acaba de recuperar: se ajusta al alto nuevo después de dibujar, no antes. */
    private boolean scrollRestored;
    private boolean cheatSheet;
    private boolean historyOpen;
    private boolean resizing;
    private String status = "";
    private long statusAt = Long.MIN_VALUE / 2;
    /** En Trayectoria, el pie construye la herramienta que llevó allí (no la figura). */
    private boolean toolCreation;
    private long lastHistoryRequest = Long.MIN_VALUE / 2;

    // ── Estado ──────────────────────────────────────────────────────────────────────────────────────────

    public ToolsPanel tools() {
        return tools;
    }

    public FigureStages figure() {
        return figure;
    }

    public BlueprintStage blueprints() {
        return blueprints;
    }

    public BlockPicker picker() {
        return picker;
    }

    public CommandPalette palette() {
        return palette;
    }

    public StudioStage stage() {
        return stage;
    }

    public StudioLayout.Frame frame() {
        return frame;
    }

    public StudioUi ui() {
        return ui;
    }

    public int scroll() {
        return scroll;
    }

    public int contentHeight() {
        return contentHeight;
    }

    public String status() {
        return status;
    }

    public boolean historyOpen() {
        return historyOpen;
    }

    public boolean cheatSheet() {
        return cheatSheet;
    }

    public int panelWidth() {
        return panelWidth;
    }

    public void setPanelWidth(int width) {
        panelWidth = width;
    }

    /** Si alguna ventana tapa el panel (las teclas y el ratón van a ella). */
    public boolean modal() {
        return picker.isOpen() || palette.isOpen() || cheatSheet;
    }

    /** Si se está escribiendo en algún campo: las letras no son atajos ni movimiento. */
    public boolean typing() {
        return picker.isOpen() || palette.isOpen() || tools.typing() || figure.typing() || blueprints.editing();
    }

    public boolean dragging() {
        return tools.dragging() || figure.dragging() || resizing;
    }

    /** Recupera, una sola vez, la última etapa. */
    private void load(StudioHost host) {
        if (loaded) {
            return;
        }
        loaded = true;
        StudioStage remembered = StudioStage.byId(host.preferences().stage());
        if (remembered != null) {
            stage = remembered;
        }
        toolCreation = stage == StudioStage.TOOLS;
        tools.load(bind(host));
    }

    /** Un mensaje en el pie durante {@value #STATUS_MILLIS} ms. */
    public void status(String text, long now) {
        status = text == null ? "" : text;
        statusAt = now;
    }

    public void selectStage(StudioStage next, StudioHost host) {
        StudioHost bound = bind(host);
        if (stage == StudioStage.TOOLS && next != StudioStage.TOOLS) {
            tools.commitField(bound);
        }
        if (next == StudioStage.TOOLS) {
            tools.refreshSoon();
            toolCreation = true;
        } else if (next == StudioStage.FIGURE || next == StudioStage.MODIFIERS || next == StudioStage.MATERIAL) {
            toolCreation = false;
        }
        stage = next;
        blueprints.stopEditing();
        if (stage == StudioStage.BLUEPRINTS) {
            blueprints.opened(bound);
        }
        host.preferences().setStage(stage.id());
        host.savePreferences();
    }

    /** Lo que pasa al cerrar el Studio: se aplica lo escrito, se manda lo pendiente y se guarda. */
    public void closed(StudioHost host) {
        StudioHost bound = bind(host);
        tools.commitField(bound);
        tools.flushOptions(bound, true);
        tools.saveSoon(bound, true);
        if (scrollKey != null) {
            host.preferences().setScroll(scrollKey, scroll);
        }
        host.savePreferences();
    }

    /** Desde la etapa Trayectoria: usar los puntos con una herramienta o con la figura. */
    private void useWith(String target, StudioHost host) {
        if (target.equals("figure")) {
            selectStage(StudioStage.FIGURE, host);
            return;
        }
        ToolCard card = ToolCatalog.byId(target);
        if (card != null) {
            tools.select(card, bind(host));
            selectStage(StudioStage.TOOLS, host);
        }
    }

    // ── Tick ────────────────────────────────────────────────────────────────────────────────────────────

    public void tick(StudioHost host) {
        load(host);
        StudioHost bound = bind(host);
        long now = host.now();
        if (stage == StudioStage.TOOLS) {
            tools.tick(bound);
        } else {
            tools.flushOptions(bound, false);
            tools.saveSoon(bound, false);
        }
        if (historyOpen && now - lastHistoryRequest >= ToolsPanel.REFRESH_MILLIS) {
            lastHistoryRequest = now;
            host.requestState();
        }
        String command = livePreviewCommand(bound);
        if (command == null) {
            live.tick(null, now, false);
            return;
        }
        Optional<JobStatusPayload> job = host.job();
        boolean busy = job.isPresent() && job.get().phaseValue() != ProgressLine.Phase.WAITING;
        String send = live.tick(command, now, busy);
        if (send != null) {
            host.send(send);
            status(host.tr("gui.komaedit.preview.live.sent"), now);
        }
    }

    /** La orden de la vista previa en vivo con lo de ahora, o {@code null} si ahora no toca. */
    String livePreviewCommand(StudioHost host) {
        if (stage != StudioStage.TOOLS || tools.browsing() || tools.typing() || tools.dragging()
                || host.preferences().previewMode() != StudioPreferences.PreviewMode.LIVE) {
            return null;
        }
        ToolCard card = tools.selected();
        if (!card.previewable()) {
            return null;
        }
        ToolContext context = host.context();
        boolean selection = card.target() == ToolTarget.SELECTION
                || card.target() == ToolTarget.PATH_OR_SELECTION && context.pathPoints() == 0;
        if (selection && context.selectionVolume() > LivePreview.MAX_VOLUME) {
            return null;
        }
        return tools.buildCommand(host, true);
    }

    // ── Dibujo ──────────────────────────────────────────────────────────────────────────────────────────

    public void render(StudioUi ui, int width, int height, int mouseX, int mouseY, StudioHost host) {
        this.ui = ui;
        load(host);
        StudioHost bound = bind(host);
        frame = StudioLayout.compute(width, height, panelWidth, StudioStage.groupSizes());
        ui.begin(mouseX, mouseY, !modal());
        rememberScroll(host);

        ui.fill(frame.panel(), PANEL);
        drawToolbar(ui, bound);
        drawNavigation(ui, bound);
        drawContent(ui, bound);
        drawFooter(ui, bound);
        drawResizeHandle(ui, mouseX, mouseY);
        if (historyOpen && !modal()) {
            drawHistory(ui, bound);
        }
        ui.canvas().beginOverlay();
        if (picker.isOpen()) {
            picker.draw(ui, frame.screen(), bound);
        } else if (palette.isOpen()) {
            palette.draw(ui, frame.screen(), bound);
        } else if (cheatSheet) {
            drawCheatSheet(ui, bound);
        }
        ui.canvas().endOverlay();
        if (!dragging() && !typing() && !cheatSheet) {
            StudioUi.Widget widget = ui.at(mouseX, mouseY);
            if (widget != null && !widget.tooltip().isEmpty() && !widget.overValue(mouseX, mouseY)) {
                ui.tooltip(widget.tooltip(), mouseX, mouseY, frame.screen());
            }
        }
    }

    /** Cambia el desplazamiento al cambiar de etapa, herramienta o figura, y recuerda el de antes. */
    private void rememberScroll(StudioHost host) {
        String key = switch (stage) {
            case TOOLS -> tools.scrollKey();
            case FIGURE -> figure.scrollKey();
            default -> "stage:" + stage.id();
        };
        if (!key.equals(scrollKey)) {
            if (scrollKey != null) {
                host.preferences().setScroll(scrollKey, scroll);
            }
            scrollKey = key;
            scroll = host.preferences().scroll(key);
            scrollRestored = true;
        }
    }

    private List<Integer> toolbarButtons() {
        // De derecha a izquierda; con el panel estrecho se quedan fuera holograma y rehacer (H y Mayús+Z).
        List<Integer> buttons = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6));
        if (frame.panel().width() < 300) {
            buttons.remove(Integer.valueOf(4));
            buttons.remove(Integer.valueOf(5));
        }
        return buttons;
    }

    private void drawToolbar(StudioUi ui, StudioHost host) {
        Rect bar = frame.toolbar();
        ui.fill(bar, SURFACE);
        ui.fill(new Rect(bar.x(), bar.bottom() - 1, bar.width(), 1), DIVIDER);
        ui.fill(new Rect(bar.x(), bar.y(), 2, bar.height()), ACCENT);
        List<Integer> buttons = toolbarButtons();
        int left = bar.x() + frame.padding();
        int right = frame.toolButton(buttons.size() - 1).x() - 4;
        ui.icon(stage.icon(), left, bar.centerY() - 6, ACCENT_HOVER);
        ui.bold(host.tr(stage.titleKey()), left + 17, bar.centerY() - 4, right - left - 17, WHITE);
        ui.register(new Rect(left, bar.y(), Math.max(0, right - left), bar.height()))
                .tooltip(host.tr(stage.titleKey()) + " · " + stage.shortcut() + "\n" + host.tr(stage.subtitleKey()));

        boolean tools = stage == StudioStage.TOOLS;
        for (int slot = 0; slot < buttons.size(); slot++) {
            Rect rect = frame.toolButton(slot);
            switch (buttons.get(slot)) {
                case 0 -> ui.toolButton(rect, StudioIcons.Icon.CLOSE, false, true)
                        .onClick(host::close).tooltip(host.tr("gui.komaedit.help.top.close"));
                case 1 -> ui.toolButton(rect, StudioIcons.Icon.HELP, cheatSheet, true)
                        .onClick(() -> cheatSheet = !cheatSheet).tooltip(host.tr("gui.komaedit.help.top.help"));
                case 2 -> ui.toolButton(rect, StudioIcons.Icon.COMMAND, palette.isOpen(), true)
                        .onClick(() -> openPalette(host)).tooltip(host.tr("gui.komaedit.help.top.palette"));
                case 3 -> ui.toolButton(rect, StudioIcons.Icon.HISTORY, historyOpen, true)
                        .onClick(() -> {
                            historyOpen = !historyOpen;
                            if (historyOpen) {
                                host.requestState();
                            }
                        })
                        .tooltip(host.tr("gui.komaedit.help.top.history"));
                case 4 -> {
                    boolean hologram = !host.hologramHidden();
                    ui.toolButton(rect, hologram ? StudioIcons.Icon.HOLOGRAM : StudioIcons.Icon.HOLOGRAM_OFF,
                                    hologram, true)
                            .onClick(() -> toggleHologram(host)).tooltip(host.tr("gui.komaedit.help.top.hologram"));
                }
                case 5 -> ui.toolButton(rect, StudioIcons.Icon.REDO, false, tools ? this.tools.canRedo()
                                : figure.canRedo())
                        .onClick(() -> redo(host)).tooltip(host.tr("gui.komaedit.help.top.redo"));
                default -> ui.toolButton(rect, StudioIcons.Icon.UNDO, false, tools ? this.tools.canUndo()
                                : figure.canUndo())
                        .onClick(() -> undo(host)).tooltip(host.tr("gui.komaedit.help.top.undo"));
            }
        }
    }

    private void drawNavigation(StudioUi ui, StudioHost host) {
        Rect nav = frame.nav();
        ui.fill(nav, SURFACE);
        ui.fill(new Rect(nav.right() - 1, nav.y(), 1, nav.height()), DIVIDER);
        boolean labels = frame.navMode() == StudioLayout.NavMode.LABELS;
        StudioStage.Group[] groups = StudioStage.Group.values();
        for (int group = 0; group < groups.length; group++) {
            Rect caption = frame.groupCaption(group);
            if (labels) {
                ui.label(host.tr(groups[group].translationKey()).toUpperCase(java.util.Locale.ROOT), caption.x() + 4,
                        caption.bottom() - 10, caption.width() - 8, MUTED);
            } else if (group > 0) {
                ui.fill(new Rect(caption.x() + 3, caption.centerY(), caption.width() - 6, 1), DIVIDER);
            }
        }
        for (StudioStage value : StudioStage.values()) {
            Rect item = frame.navItem(value.ordinal());
            boolean selected = value == stage;
            boolean hovered = ui.hovered(item);
            ui.fill(item, selected ? CARD_ACTIVE : hovered ? CARD_HOVER : SURFACE);
            if (selected) {
                ui.fill(item.left(2), ACCENT);
            }
            int iconY = item.centerY() - StudioIcons.SIZE / 2;
            int iconColor = selected ? ACCENT : hovered ? TEXT : TEXT_SECONDARY;
            if (labels) {
                ui.icon(value.icon(), item.x() + 6, iconY, iconColor);
                String key = String.valueOf(value.shortcut());
                int keyX = item.right() - 4 - ui.width(key);
                int labelX = item.x() + 6 + StudioIcons.SIZE + 5;
                ui.label(host.tr(value.titleKey()), labelX, item.centerY() - 4, keyX - labelX - 4,
                        selected ? WHITE : hovered ? TEXT : TEXT_SECONDARY);
                ui.label(key, keyX, item.centerY() - 4, selected ? MUTED : DISABLED);
            } else {
                ui.icon(value.icon(), item.centerX() - StudioIcons.SIZE / 2, iconY, iconColor);
            }
            ui.register(item)
                    .onClick(() -> selectStage(value, host))
                    .tooltip(host.tr(value.titleKey()) + " · " + value.shortcut() + "\n"
                            + host.tr(value.subtitleKey()));
        }
    }

    private void drawContent(StudioUi ui, StudioHost host) {
        Rect viewport = frame.viewport();
        if (!scrollRestored) {
            scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - viewport.height()));
        }
        ui.pushClip(viewport);
        int top = frame.compact() ? 4 : 8;
        boolean compact = frame.compact();
        Column column = new Column(frame.innerLeft(), viewport.y() + top - scroll, frame.innerWidth(),
                compact ? Column.COMPACT_SECTION_GAP : Column.SECTION_GAP);
        switch (stage) {
            case TOOLS -> tools.draw(ui, column, host, compact);
            case PATH -> figure.drawPath(ui, column, host, compact, target -> useWith(target, host));
            case FIGURE -> figure.drawFigure(ui, column, host, compact);
            case MODIFIERS -> figure.drawModifiers(ui, column, host, compact);
            case MATERIAL -> figure.drawMaterial(ui, column, host, compact);
            case COLOR -> color.draw(ui, column, host, compact);
            case WEATHER -> figure.drawWeather(ui, column, host, compact);
            case BLUEPRINTS -> blueprints.draw(ui, column, host, compact);
            case RECIPES -> figure.drawRecipes(ui, column, host);
            case INSTANCES -> figure.drawInstances(ui, column, host);
        }
        contentHeight = column.height() + top + (compact ? 2 : 4);
        int maximumScroll = Math.max(0, contentHeight - viewport.height());
        if (scrollRestored) {
            scrollRestored = false;
            scroll = Math.clamp(scroll, 0, maximumScroll);
        }
        ui.popClip();
        if (maximumScroll > 0) {
            // Barra fina a la derecha: cuánto queda por ver y dónde se está.
            int track = viewport.height() - 8;
            int thumb = Math.max(16, track * viewport.height() / contentHeight);
            int thumbY = viewport.y() + 4 + (track - thumb) * scroll / maximumScroll;
            ui.fill(new Rect(viewport.right() - 4, viewport.y() + 4, 2, track), DIVIDER);
            ui.fill(new Rect(viewport.right() - 4, thumbY, 2, thumb), ACCENT_DIM);
        }
    }

    /** Lo que el pie enseña y hace en la etapa de ahora. */
    record Footer(StudioIcons.Icon icon, String target, String estimate, int estimateColor, String buildLabel,
                  StudioUi.Tone tone, Runnable build, String help, boolean previewSelector, String idle) {
    }

    Footer footer(StudioHost host) {
        long now = host.now();
        boolean toolFooter = stage == StudioStage.TOOLS || stage == StudioStage.PATH && toolCreation;
        if (toolFooter && !tools.chosen()) {
            return new Footer(StudioIcons.Icon.TOOLS, "", "", MUTED, host.tr("gui.komaedit.tools.pick"),
                    StudioUi.Tone.DISABLED, () -> host.status(host.tr("gui.komaedit.tools.pick.help")),
                    host.tr("gui.komaedit.tools.pick.help"), false, "");
        }
        if (toolFooter) {
            ToolCard card = tools.selected();
            String target = tools.targetText(host);
            if (stage != StudioStage.TOOLS) {
                target = host.tr(card.nameKey()) + " · " + target;
            }
            return new Footer(tools.targetIcon(host), target, "", MUTED, tools.buildLabel(host),
                    tools.buildTone(host), () -> tools.build(host), tools.buildHelp(host), card.previewable(), "");
        }
        ToolsInfo info = host.info();
        String selection = info.hasSelection()
                ? host.tr("gui.komaedit.tools.footer.selection", info.selection()[0], info.selection()[1],
                info.selection()[2]) : host.tr("gui.komaedit.tools.footer.no_selection");
        boolean missingSelection = info.known() && !info.hasSelection();
        switch (stage) {
            case WEATHER -> {
                return new Footer(StudioIcons.Icon.SELECTION, selection, "", MUTED,
                        missingSelection ? host.tr("gui.komaedit.tools.problem.no_selection.short")
                                : host.tr("gui.komaedit.weather.run"),
                        missingSelection ? StudioUi.Tone.DISABLED : StudioUi.Tone.PRIMARY,
                        () -> {
                            if (missingSelection) {
                                host.status(host.tr("gui.komaedit.tools.problem.no_selection"));
                            } else {
                                figure.runWeather(host);
                            }
                        }, host.tr("gui.komaedit.weather.selection_hint"), false, "");
            }
            case COLOR -> {
                return new Footer(StudioIcons.Icon.SELECTION, selection, "", MUTED,
                        missingSelection ? host.tr("gui.komaedit.tools.problem.no_selection.short")
                                : host.tr("gui.komaedit.color.shade_run"),
                        missingSelection ? StudioUi.Tone.DISABLED : StudioUi.Tone.PRIMARY,
                        () -> {
                            if (missingSelection) {
                                host.status(host.tr("gui.komaedit.tools.problem.no_selection"));
                            } else {
                                color.shade(host);
                            }
                        }, host.tr("gui.komaedit.color.shade_hint"), false, "");
            }
            case BLUEPRINTS -> {
                String chosen = blueprints.selectedName(host);
                String target = host.tr("gui.komaedit.tools.footer.look") + (chosen.isEmpty() ? "" : " · " + chosen);
                return new Footer(StudioIcons.Icon.LOOK, target, "", MUTED, blueprints.placeLabel(host),
                        blueprints.placeTone(host), () -> blueprints.place(host),
                        host.tr("gui.komaedit.help.blueprint.place"), false, "");
            }
            default -> {
                ProceduralRecipe draft = figure.draft();
                int points = host.pathPoints().size();
                String target = FigureStages.shapeName(host, draft.shape()) + " · "
                        + FigureStages.materialSummary(host, draft) + " · "
                        + host.tr("gui.komaedit.tools.footer.points", points);
                int blocks = host.previewBlocks();
                String estimate = blocks < 0 || !figure.pathReady(host) ? ""
                        : host.tr("gui.komaedit.estimate", BuildEstimate.compact(blocks))
                        + (host.previewSettled() ? "" : " …");
                int estimateColor = switch (BuildEstimate.level(blocks)) {
                    case LARGE -> WARNING;
                    case OVER_DEFAULT_LIMIT -> DANGER;
                    default -> MUTED;
                };
                // La estimación ya va arriba: abajo solo lo que cambia lo que se ve (holograma oculto).
                String idle = figure.pathReady(host) && host.hologramHidden() ? host.tr("gui.komaedit.hologram.hidden")
                        : "";
                if (figure.armed(now)) {
                    idle = host.tr("gui.komaedit.build_large", BuildEstimate.compact(blocks));
                }
                return new Footer(StudioIcons.Icon.PATH, target, estimate, estimateColor, figure.buildLabel(host, now),
                        figure.buildTone(host, now), () -> {
                    if (!figure.pathReady(host)) {
                        toolCreation = false;
                        selectStage(StudioStage.PATH, host);
                    }
                    figure.build(host, host.now());
                }, host.tr("gui.komaedit.help.build"), false, idle);
            }
        }
    }

    private void drawFooter(StudioUi ui, StudioHost host) {
        Rect area = frame.footer();
        ui.fill(area, SURFACE);
        ui.fill(new Rect(area.x(), area.y(), area.width(), 1), DIVIDER);
        Footer footer = footer(host);
        long now = host.now();
        Optional<JobStatusPayload> job = host.job();
        boolean fresh = !status.isEmpty() && now - statusAt < STATUS_MILLIS;
        Rect summary = frame.footerSummary();
        Rect statusLine = frame.footerStatus();
        JobStrip.Actions actions = jobActions(host);

        String detail = fresh ? status : footer.idle();
        if ((!frame.compact() || job.isEmpty() && !fresh) && !footer.target().isEmpty()) {
            // Sobre qué se trabaja: icono, texto y, a la derecha, la estimación. Sin nada debajo, centrado en el
            // hueco que queda sobre el botón.
            if (!frame.compact() && job.isEmpty() && detail.isEmpty()) {
                int space = frame.buildButton().y() - area.y();
                summary = new Rect(summary.x(), area.y() + (space - 9) / 2 + 1, summary.width(), 9);
            }
            int estimateWidth = footer.estimate().isEmpty() ? 0 : ui.width(footer.estimate()) + 8;
            ui.icon(footer.icon(), summary.x() - 1, summary.y() - 2, TEXT_SECONDARY);
            String fitted = FooterText.fit(ui, List.of(footer.target().split(" · ")),
                    summary.width() - 16 - estimateWidth);
            ui.label(fitted, summary.x() + 15, summary.y(), TEXT_SECONDARY);
            if (!footer.estimate().isEmpty()) {
                ui.rightAligned(footer.estimate(), summary.right(), summary.y(), footer.estimateColor());
            }
            ui.register(summary).tooltip(footer.target() + (footer.estimate().isEmpty() ? ""
                    : "\n" + footer.estimate()));
        }
        if (job.isPresent()) {
            ProgressLine line = job.get().line();
            List<String> segments = JobStrip.segments(line, job.get().changes(), actions);
            String text = FooterText.fit(ui, segments, statusLine.width());
            ui.label(text, statusLine.x(), statusLine.y(), JobStrip.color(line.phase()));
            ui.register(statusLine).tooltip(JobStrip.text(line, job.get().changes(), actions));
            Rect bar = frame.footerProgress();
            ui.fill(bar, TRACK);
            ui.fill(new Rect(bar.x(), bar.y(), (int) Math.round(bar.width() * JobStrip.fraction(line, now)),
                    bar.height()), line.phase() == ProgressLine.Phase.WAITING ? WARNING : ACCENT);
            if (line.phase() == ProgressLine.Phase.WAITING) {
                String discard = host.tr("gui.komaedit.job.discard");
                Rect[] buttons = frame.footerButtons(ui.width(discard) + 20);
                ui.button(buttons[0], host.tr("gui.komaedit.job.confirm"), StudioUi.Tone.PRIMARY)
                        .tooltip(host.tr("gui.komaedit.job.confirm") + "\n" + host.tr("gui.komaedit.job.confirm.help"))
                        .onClick(() -> {
                            host.send(JobStrip.CONFIRM);
                            host.status(host.tr("gui.komaedit.job.confirmed"));
                        });
                ui.button(buttons[1], discard, StudioUi.Tone.DANGER)
                        .tooltip(discard + "\n" + host.tr("gui.komaedit.job.discard.help"))
                        .onClick(() -> {
                            host.send(JobStrip.CANCEL);
                            host.status(host.tr("gui.komaedit.job.discarded"));
                        });
                return;
            }
            String cancel = host.tr("gui.komaedit.job.cancel");
            Rect[] buttons = frame.footerButtons(ui.width(cancel) + 20);
            ui.button(buttons[0], footer.buildLabel(), footer.tone()).onClick(footer.build())
                    .tooltip(footer.buildLabel() + "\n" + footer.help());
            ui.button(buttons[1], cancel, StudioUi.Tone.DANGER)
                    .tooltip(cancel + "\n" + host.tr("gui.komaedit.job.cancel.help"))
                    .onClick(() -> {
                        host.send(JobStrip.CANCEL);
                        host.status(host.tr("gui.komaedit.job.cancelled"));
                    });
            return;
        }
        if (!detail.isEmpty()) {
            String fitted = FooterText.fit(ui, List.of(detail.split(" · ")), statusLine.width());
            ui.label(fitted, statusLine.x(), statusLine.y(), fresh ? TEXT_SECONDARY : MUTED);
            if (!fitted.equals(detail)) {
                ui.register(statusLine).tooltip(detail);
            }
        }
        Rect build = frame.buildButton();
        if (footer.previewSelector()) {
            Rect[] parts = frame.buildWithPreview();
            drawPreviewSelector(ui, parts[0], host);
            build = parts[1];
        }
        ui.button(build, footer.buildLabel(), footer.tone()).onClick(footer.build())
                .tooltip(footer.buildLabel() + "\n" + footer.help());
    }

    /** El ojo a la izquierda de Construir: sin vista previa, al construir o en vivo. Un clic pasa al siguiente. */
    private void drawPreviewSelector(StudioUi ui, Rect rect, StudioHost host) {
        StudioPreferences.PreviewMode mode = host.preferences().previewMode();
        boolean hovered = ui.hovered(rect);
        ui.fill(rect, mode == StudioPreferences.PreviewMode.OFF ? (hovered ? CARD_HOVER : CARD) : CARD_ACTIVE);
        if (mode != StudioPreferences.PreviewMode.OFF) {
            ui.fill(new Rect(rect.x(), rect.bottom() - 2, rect.width(), 2), ACCENT);
        }
        StudioIcons.Icon icon = mode == StudioPreferences.PreviewMode.LIVE ? StudioIcons.Icon.LIVE
                : StudioIcons.Icon.EYE;
        ui.icon(icon, rect.centerX() - 6, rect.centerY() - 6, mode == StudioPreferences.PreviewMode.OFF
                ? (hovered ? TEXT : MUTED) : ACCENT_HOVER);
        ui.register(rect)
                .tooltip(host.tr("gui.komaedit.preview.mode." + mode.id()) + "\n"
                        + host.tr("gui.komaedit.preview.mode." + mode.id() + ".help") + "\n"
                        + host.tr("gui.komaedit.preview.mode.cycle"))
                .onReset(() -> setPreviewMode(host, StudioPreferences.PreviewMode.OFF))
                .onClick(() -> setPreviewMode(host, mode.next()));
    }

    private void setPreviewMode(StudioHost host, StudioPreferences.PreviewMode mode) {
        host.preferences().setPreviewMode(mode);
        host.savePreferences();
        live.reset();
        host.status(host.tr("gui.komaedit.preview.mode." + mode.id()));
    }

    private JobStrip.Actions jobActions(StudioHost host) {
        return new JobStrip.Actions() {
            @Override
            public String tr(String key, Object... arguments) {
                return host.tr(key, arguments);
            }

            @Override
            public void send(String command) {
                host.send(command);
            }

            @Override
            public void status(String text) {
                host.status(text);
            }
        };
    }

    private void drawResizeHandle(StudioUi ui, int mouseX, int mouseY) {
        Rect handle = frame.resizeHandle();
        boolean active = resizing || !modal() && handle.contains(mouseX, mouseY);
        ui.fill(new Rect(frame.panel().x(), 0, 1, frame.screen().height()), active ? ACCENT : DIVIDER);
        if (active) {
            ui.fill(new Rect(frame.panel().x() - 1, 0, 2, frame.screen().height()), ACCENT);
        }
    }

    /** Dónde va el desplegable del historial: bajo la barra, pegado a la derecha del panel. */
    Rect historyBounds(int rows) {
        int width = Math.min(HISTORY_WIDTH, frame.panel().width() - 8);
        int height = 24 + Math.max(1, rows) * 16 + 24;
        return new Rect(frame.panel().right() - width - 4, frame.toolbar().bottom() + 2, width,
                Math.min(height, frame.screen().height() - frame.toolbar().bottom() - 8));
    }

    /**
     * El historial de deshacer del mundo: lo último arriba, con los bloques que cambió cada operación. Lo deshecho
     * sale apagado (se puede rehacer). Pulsar una fila deshace o rehace hasta ella.
     */
    private void drawHistory(StudioUi ui, StudioHost host) {
        StudioHost.WorldHistory history = host.worldHistory();
        List<StudioHost.WorldHistory.Entry> entries = history.entries();
        int rows = Math.min(HISTORY_ROWS, entries.size());
        Rect box = historyBounds(rows);
        ui.canvas().beginOverlay();
        ui.fill(box, PANEL);
        ui.border(box, OUTLINE);
        ui.fill(new Rect(box.x(), box.y(), 2, box.height()), ACCENT);
        ui.register(box);
        ui.bold(host.tr("gui.komaedit.history.title"), box.x() + 8, box.y() + 7, box.width() - 16, WHITE);
        int y = box.y() + 20;
        if (entries.isEmpty()) {
            ui.label(host.tr("gui.komaedit.history.empty"), box.x() + 8, y + 4, box.width() - 16, MUTED);
        }
        for (int index = 0; index < rows; index++) {
            StudioHost.WorldHistory.Entry entry = entries.get(index);
            Rect row = new Rect(box.x() + 4, y, box.width() - 8, 15);
            boolean undone = index < history.undone();
            boolean current = index == history.undone();
            boolean over = ui.hovered(row);
            ui.fill(row, over ? CARD_HOVER : current ? CARD_ACTIVE : PANEL);
            if (current) {
                ui.fill(row.left(2), ACCENT);
            }
            String blocks = host.tr("gui.komaedit.history.blocks", ProgressLine.group(entry.blocks()));
            int blocksWidth = ui.width(blocks);
            ui.label(entry.label(), row.x() + 6, row.y() + 4, row.width() - blocksWidth - 16,
                    undone ? DISABLED : current ? WHITE : TEXT_SECONDARY);
            ui.rightAligned(blocks, row.right() - 4, row.y() + 4, undone ? DISABLED : MUTED);
            int target = index;
            ui.register(row)
                    .tooltip(entry.label() + "\n" + blocks + "\n" + host.tr(undone ? "gui.komaedit.history.redo_to"
                            : "gui.komaedit.history.undo_to"))
                    .onClick(() -> travel(host, history, target));
            y += 16;
        }
        Rect[] buttons = new Rect(box.x() + 6, box.bottom() - 20, box.width() - 12, 15).columns(2, 4);
        boolean canUndo = history.undone() < entries.size() || entries.isEmpty();
        boolean canRedo = history.undone() > 0;
        ui.button(buttons[0], host.tr("gui.komaedit.history.undo"), canUndo ? StudioUi.Tone.SECONDARY
                        : StudioUi.Tone.DISABLED)
                .tooltip(host.tr("gui.komaedit.history.undo") + " · Ctrl+Z")
                .onClick(() -> {
                    host.worldUndo();
                    host.status(host.tr("gui.komaedit.status.world.undo"));
                });
        ui.button(buttons[1], host.tr("gui.komaedit.history.redo"), canRedo ? StudioUi.Tone.SECONDARY
                        : StudioUi.Tone.DISABLED)
                .tooltip(host.tr("gui.komaedit.history.redo") + " · Ctrl+Y")
                .onClick(() -> {
                    host.worldRedo();
                    host.status(host.tr("gui.komaedit.status.world.redo"));
                });
        ui.canvas().endOverlay();
    }

    /** Deshace o rehace hasta dejar la fila {@code target} como la última hecha. */
    static int[] steps(StudioHost.WorldHistory history, int target) {
        int undone = history.undone();
        if (target >= undone) {
            return new int[]{target - undone + 1, 0};
        }
        return new int[]{0, undone - target};
    }

    private void travel(StudioHost host, StudioHost.WorldHistory history, int target) {
        int[] steps = steps(history, target);
        for (int step = 0; step < steps[0]; step++) {
            host.worldUndo();
        }
        for (int step = 0; step < steps[1]; step++) {
            host.worldRedo();
        }
        host.status(host.tr(steps[0] > 0 ? "gui.komaedit.history.undone_steps" : "gui.komaedit.history.redone_steps",
                Math.max(steps[0], steps[1])));
        host.requestState();
    }

    private void drawCheatSheet(StudioUi ui, StudioHost host) {
        Rect screen = frame.screen();
        ui.fill(screen, SCRIM);
        ui.register(screen).onClick(() -> cheatSheet = false);
        ui.enableHover();
        List<StudioShortcuts.Shortcut> shortcuts = StudioShortcuts.ALL;
        int columns = screen.height() < 330 ? 2 : 1;
        int rows = (shortcuts.size() + columns - 1) / columns;
        int keyWidth = 0;
        for (StudioShortcuts.Shortcut shortcut : shortcuts) {
            keyWidth = Math.max(keyWidth, ui.width(host.tr(shortcut.keysKey())));
        }
        int columnWidth = Math.min(340, (screen.width() - 40) / columns);
        int modalWidth = columnWidth * columns + 24;
        int modalHeight = Math.min(screen.height() - 16, 48 + rows * 12);
        Rect modal = new Rect((screen.width() - modalWidth) / 2, (screen.height() - modalHeight) / 2, modalWidth,
                modalHeight);
        ui.fill(modal, PANEL);
        ui.border(modal, DIVIDER);
        ui.fill(new Rect(modal.x(), modal.y(), 2, modal.height()), ACCENT);
        ui.bold(host.tr("gui.komaedit.shortcuts.title"), modal.x() + 14, modal.y() + 10, modalWidth - 28, WHITE);
        ui.label(host.tr("gui.komaedit.shortcuts.close"), modal.x() + 14, modal.bottom() - 14, modalWidth - 28, MUTED);
        int keyColumn = Math.min(keyWidth, columnWidth / 2);
        for (int index = 0; index < shortcuts.size(); index++) {
            StudioShortcuts.Shortcut shortcut = shortcuts.get(index);
            int x = modal.x() + 14 + index / rows * columnWidth;
            int y = modal.y() + 26 + index % rows * 12;
            if (y > modal.bottom() - 26) {
                continue;
            }
            ui.label(host.tr(shortcut.keysKey()), x, y, keyColumn, ACCENT_HOVER);
            ui.label(host.tr(shortcut.translationKey()), x + keyColumn + 8, y, columnWidth - keyColumn - 16,
                    TEXT_SECONDARY);
        }
    }

    // ── Paleta de órdenes ───────────────────────────────────────────────────────────────────────────────

    /** Todo lo que se puede abrir desde la paleta, en el orden en que sale sin buscar nada. */
    public List<CommandPalette.Entry> paletteEntries(StudioHost host) {
        StudioHost bound = bind(host);
        List<CommandPalette.Entry> entries = new ArrayList<>();
        String stageKind = host.tr(CommandPalette.Kind.STAGE.translationKey());
        for (StudioStage value : StudioStage.values()) {
            entries.add(new CommandPalette.Entry(CommandPalette.Kind.STAGE, host.tr(value.titleKey()),
                    stageKind + " · " + value.shortcut(), host.tr(value.subtitleKey()) + " " + value.id(), value.icon(),
                    () -> selectStage(value, host)));
        }
        String toolKind = host.tr(CommandPalette.Kind.TOOL.translationKey());
        for (ToolCard card : ToolCatalog.ALL) {
            entries.add(new CommandPalette.Entry(CommandPalette.Kind.TOOL, host.tr(card.nameKey()), toolKind,
                    String.join(" ", card.keywords()) + " " + host.tr(card.family().translationKey()),
                    ToolsPanel.icon(card), () -> {
                tools.select(card, bound);
                selectStage(StudioStage.TOOLS, host);
            }));
        }
        String presetKind = host.tr(CommandPalette.Kind.PRESET.translationKey());
        for (ToolCard card : ToolCatalog.ALL) {
            for (ToolPreset preset : card.presets()) {
                entries.add(new CommandPalette.Entry(CommandPalette.Kind.PRESET,
                        host.tr(card.nameKey()) + ": " + host.tr(preset.labelKey()), presetKind,
                        host.tr(preset.helpKey()), ToolsPanel.icon(card), () -> {
                    tools.applyPreset(card, preset, bound);
                    selectStage(StudioStage.TOOLS, host);
                }));
            }
        }
        String figureKind = host.tr(CommandPalette.Kind.FIGURE.translationKey());
        for (PathShape shape : PathShape.values()) {
            entries.add(new CommandPalette.Entry(CommandPalette.Kind.FIGURE, FigureStages.shapeName(host, shape),
                    figureKind, shape.id(), StudioIcons.Icon.FIGURE, () -> {
                figure.selectShape(bound, shape);
                selectStage(StudioStage.FIGURE, host);
            }));
        }
        String blueprintKind = host.tr(CommandPalette.Kind.BLUEPRINT.translationKey());
        for (BlueprintSummary blueprint : host.blueprints().list()) {
            entries.add(new CommandPalette.Entry(CommandPalette.Kind.BLUEPRINT, blueprint.name(),
                    blueprintKind + " · " + blueprint.describeSize(), String.join(" ", blueprint.tags()),
                    StudioIcons.Icon.BLUEPRINTS, () -> {
                blueprints.select(blueprint.reference());
                selectStage(StudioStage.BLUEPRINTS, host);
            }));
        }
        return entries;
    }

    public void openPalette(StudioHost host) {
        historyOpen = false;
        palette.open(paletteEntries(host));
    }

    // ── Acciones de la barra ────────────────────────────────────────────────────────────────────────────

    private void toggleHologram(StudioHost host) {
        boolean hide = !host.hologramHidden();
        host.setHologramHidden(hide);
        host.status(host.tr(hide ? "gui.komaedit.hologram.hidden" : "gui.komaedit.hologram.shown"));
    }

    private void undo(StudioHost host) {
        if (stage == StudioStage.TOOLS) {
            tools.undo(bind(host));
        } else {
            figure.undo(bind(host));
        }
    }

    private void redo(StudioHost host) {
        if (stage == StudioStage.TOOLS) {
            tools.redo(bind(host));
        } else {
            figure.redo(bind(host));
        }
    }

    private void build(StudioHost host) {
        footer(bind(host)).build().run();
    }

    // ── Ratón ───────────────────────────────────────────────────────────────────────────────────────────

    public Click mouseClicked(double x, double y, int button, StudioHost host) {
        StudioHost bound = bind(host);
        ProceduralRecipe before = figure.draft();
        ToolsPanel.Snapshot toolsBefore = tools.snapshot();
        StudioUi.Widget widget = ui == null ? null : ui.at(x, y);
        Click result = click(x, y, button, widget, bound);
        figure.track(before, widget == null ? null : widget.group(), host.now());
        tools.track(toolsBefore, widget == null ? null : widget.group(), host.now());
        return result;
    }

    private Click click(double x, double y, int button, StudioUi.Widget widget, StudioHost host) {
        boolean left = button == 0;
        if (cheatSheet) {
            cheatSheet = false;
            return Click.HANDLED;
        }
        Object key = widget == null ? null : widget.key();
        figure.clickedOutside(key, host);
        if (tools.typing() && !tools.owns(key)) {
            // Pulsar fuera de un campo de herramientas aplica lo escrito antes de atender el clic.
            tools.commitField(host);
        }
        if (!BlueprintStage.SEARCH_KEY.equals(key)) {
            blueprints.stopEditing();
        }
        if (picker.isOpen() || palette.isOpen()) {
            if (left && widget != null) {
                widget.press(x, y);
            }
            return Click.HANDLED;
        }
        if (historyOpen && (widget == null || !historyBounds(HISTORY_ROWS).contains(x, y))
                && !frame.toolButton(toolbarButtons().indexOf(3)).contains(x, y)) {
            historyOpen = false;
            if (!frame.panel().contains(x, y)) {
                return Click.HANDLED;
            }
        }
        if (left && frame.resizeHandle().contains(x, y)) {
            resizing = true;
            return Click.HANDLED;
        }
        boolean inPanel = frame.panel().contains(x, y);
        if (button == 1) {
            if (widget != null && widget.reset() != null) {
                widget.reset().run();
                host.status(host.tr("gui.komaedit.reset_done"));
                return Click.HANDLED;
            }
            return inPanel ? Click.HANDLED : Click.CAMERA;
        }
        if (!left) {
            return Click.HANDLED;
        }
        if (widget != null && widget.click() != null) {
            widget.press(x, y);
            return Click.HANDLED;
        }
        return inPanel ? Click.HANDLED : Click.WORLD;
    }

    /** Arrastre con el botón pulsado; devuelve si el Studio se lo queda (si no, es del mundo). */
    public boolean mouseDragged(double x, double y, int button, StudioHost host) {
        StudioHost bound = bind(host);
        if (button != 0) {
            return false;
        }
        if (tools.dragging()) {
            ToolsPanel.Snapshot before = tools.snapshot();
            tools.drag(x, bound);
            tools.track(before, tools.dragGroup(), host.now());
            return true;
        }
        if (figure.dragging()) {
            ProceduralRecipe before = figure.draft();
            figure.drag(x, host.shift());
            figure.track(before, figure.dragGroup(), host.now());
            return true;
        }
        if (resizing && frame != null) {
            panelWidth = StudioLayout.clampPanelWidth(frame.screen().width(),
                    frame.screen().width() - (int) Math.round(x));
            return true;
        }
        return false;
    }

    public void mouseReleased(double x, double y, int button, StudioHost host) {
        if (button != 0) {
            return;
        }
        StudioHost bound = bind(host);
        tools.release(bound);
        figure.release(bound);
        resizing = false;
    }

    /** La rueda: sobre un valor lo cambia; sobre el contenido lo desplaza. Devuelve si fue dentro del Studio. */
    public boolean mouseScrolled(double x, double y, double amount, StudioHost host) {
        if (amount == 0.0 || frame == null) {
            return false;
        }
        int direction = amount > 0.0 ? 1 : -1;
        StudioUi.Widget widget = ui == null ? null : ui.at(x, y);
        if (palette.isOpen()) {
            palette.key(StudioKey.of(direction > 0 ? StudioKey.Code.UP : StudioKey.Code.DOWN));
            return true;
        }
        if (modal()) {
            if (widget != null && widget.scroll() != null) {
                widget.scroll().accept(direction);
            }
            return true;
        }
        if (!frame.panel().contains(x, y)) {
            return false;
        }
        if (widget != null && widget.scroll() != null && (widget.overValue(x, y) || widget.bounds().width() < 120)
                && !tools.typing() && !figure.typing()) {
            ProceduralRecipe before = figure.draft();
            ToolsPanel.Snapshot toolsBefore = tools.snapshot();
            widget.scroll().accept(direction);
            figure.track(before, widget.group(), host.now());
            tools.track(toolsBefore, widget.group(), host.now());
            return true;
        }
        if (frame.viewport().contains(x, y)) {
            scroll = Math.clamp(scroll - direction * 20, 0, maximumScroll());
        }
        return true;
    }

    private int maximumScroll() {
        return frame == null ? 0 : Math.max(0, contentHeight - frame.viewport().height());
    }

    // ── Teclado ─────────────────────────────────────────────────────────────────────────────────────────

    /** Una tecla. Devuelve si el Studio la atendió (si no, la pantalla hace lo suyo: Esc cierra). */
    public boolean key(StudioKey key, StudioHost host) {
        StudioHost bound = bind(host);
        ProceduralRecipe before = figure.draft();
        ToolsPanel.Snapshot toolsBefore = tools.snapshot();
        boolean handled = handleKey(key, bound);
        figure.track(before, null, host.now());
        tools.track(toolsBefore, null, host.now());
        return handled;
    }

    private boolean handleKey(StudioKey key, StudioHost host) {
        if (palette.isOpen()) {
            return palette.key(key);
        }
        if (picker.isOpen()) {
            return picker.key(key, host);
        }
        if (cheatSheet) {
            cheatSheet = false;
            if (key.code() == StudioKey.Code.ESCAPE || key.code() == StudioKey.Code.F1) {
                return true;
            }
        }
        // Con un campo abierto las letras se escriben: ningún atajo (ni K) se dispara.
        if (tools.typing()) {
            return tools.key(toolKey(key), key.control(), host);
        }
        if (figure.typing()) {
            return figure.key(key, host);
        }
        if (blueprints.editing()) {
            return blueprints.keyPressed(key, host);
        }
        if (key.control()) {
            return controlShortcut(key, host);
        }
        if (key.code() == StudioKey.Code.ESCAPE) {
            if (historyOpen) {
                historyOpen = false;
                return true;
            }
            if (stage == StudioStage.TOOLS && tools.key(ToolsPanel.Key.ESCAPE, false, host)) {
                return true;
            }
            return stage == StudioStage.FIGURE && figure.key(key, host);
        }
        if (key.code() == StudioKey.Code.DIGIT && !key.shift()) {
            StudioStage next = StudioStage.byShortcut(key.letter());
            if (next != null) {
                selectStage(next, host);
                return true;
            }
        }
        if (key.code() == StudioKey.Code.ENTER && host.job().map(job ->
                job.phaseValue() == ProgressLine.Phase.WAITING).orElse(false)) {
            host.send(JobStrip.CONFIRM);
            host.status(host.tr("gui.komaedit.job.confirmed"));
            return true;
        }
        switch (key.code()) {
            case TAB -> {
                StudioStage[] values = StudioStage.values();
                selectStage(values[Math.floorMod(stage.ordinal() + (key.shift() ? -1 : 1), values.length)], host);
                return true;
            }
            case F1 -> {
                cheatSheet = !cheatSheet;
                return true;
            }
            case PAGE_UP -> {
                scroll = Math.max(0, scroll - pageScroll());
                return true;
            }
            case PAGE_DOWN -> {
                scroll = Math.min(maximumScroll(), scroll + pageScroll());
                return true;
            }
            default -> {
            }
        }
        if (key.is('K')) {
            host.close();
            return true;
        }
        if (key.is('B')) {
            build(host);
            return true;
        }
        if (key.is('H')) {
            toggleHologram(host);
            return true;
        }
        if (key.is('Z')) {
            if (key.shift()) {
                redo(host);
            } else {
                undo(host);
            }
            return true;
        }
        if (key.is('Y')) {
            redo(host);
            return true;
        }
        if (key.is('R')) {
            if (stage == StudioStage.TOOLS) {
                tools.randomSeed(host);
            } else {
                figure.randomVariant(host);
            }
            return true;
        }
        return false;
    }

    private boolean controlShortcut(StudioKey key, StudioHost host) {
        if (key.is('K')) {
            openPalette(host);
            return true;
        }
        if (key.is('Z')) {
            if (key.shift()) {
                host.worldRedo();
                host.status(host.tr("gui.komaedit.status.world.redo"));
            } else {
                host.worldUndo();
                host.status(host.tr("gui.komaedit.status.world.undo"));
            }
            return true;
        }
        if (key.is('Y')) {
            host.worldRedo();
            host.status(host.tr("gui.komaedit.status.world.redo"));
            return true;
        }
        if (key.is('F')) {
            focusSearch(host);
            return true;
        }
        if (key.is('S')) {
            figure.saveRecipe(host);
            return true;
        }
        if (key.code() == StudioKey.Code.BACKSPACE) {
            if (host.job().isEmpty()) {
                return false;
            }
            host.send(JobStrip.CANCEL);
            host.status(host.tr("gui.komaedit.job.cancelled"));
            return true;
        }
        if (key.code() == StudioKey.Code.DELETE) {
            host.send("komaedit path clear");
            host.status(host.tr("gui.komaedit.path.cleared"));
            return true;
        }
        return false;
    }

    private int pageScroll() {
        return frame == null ? 40 : Math.max(40, frame.viewport().height() - 40);
    }

    private void focusSearch(StudioHost host) {
        switch (stage) {
            case TOOLS -> {
                scroll = 0;
                tools.focusSearch(host);
            }
            case BLUEPRINTS -> blueprints.focusSearch();
            default -> {
                if (stage != StudioStage.FIGURE) {
                    selectStage(StudioStage.FIGURE, host);
                }
                scroll = 0;
                figure.focusSearch(host);
            }
        }
    }

    public boolean charTyped(char character, StudioHost host) {
        StudioHost bound = bind(host);
        if (palette.isOpen()) {
            return palette.type(character);
        }
        if (picker.isOpen()) {
            return picker.type(character);
        }
        if (tools.typing()) {
            return tools.type(character);
        }
        if (figure.typing()) {
            return figure.type(character, bound);
        }
        if (blueprints.editing()) {
            return blueprints.charTyped(character);
        }
        if (character == '?') {
            cheatSheet = !cheatSheet;
            return true;
        }
        if (character == '/') {
            focusSearch(bound);
            return true;
        }
        return false;
    }

    /** Pegar (Ctrl+V) en el campo abierto. */
    public void paste(String text, StudioHost host) {
        StudioHost bound = bind(host);
        if (tools.typing()) {
            tools.paste(text);
        } else if (figure.typing()) {
            figure.paste(text, bound);
        } else if (palette.isOpen()) {
            text.chars().forEach(code -> palette.type((char) code));
        } else if (picker.isOpen()) {
            text.chars().forEach(code -> picker.type((char) code));
        }
    }

    private static ToolsPanel.Key toolKey(StudioKey key) {
        return switch (key.code()) {
            case ENTER -> ToolsPanel.Key.ENTER;
            case ESCAPE -> ToolsPanel.Key.ESCAPE;
            case BACKSPACE -> ToolsPanel.Key.BACKSPACE;
            default -> ToolsPanel.Key.OTHER;
        };
    }

    // ── El anfitrión, con lo que la vista se queda ──────────────────────────────────────────────────────

    /**
     * El {@link StudioHost} de la pantalla con tres cosas que resuelve la vista: los mensajes van al pie, «llevar a
     * Trayectoria» cambia de etapa y la biblioteca de bloques es la ventana del Studio.
     */
    StudioHost bind(StudioHost host) {
        return host instanceof Bound ? host : new Bound(host);
    }

    private final class Bound implements StudioHost {
        private final StudioHost host;

        private Bound(StudioHost host) {
            this.host = host;
        }

        @Override
        public void status(String text) {
            StudioView.this.status(text, host.now());
            host.status(text);
        }

        @Override
        public void goToPath() {
            toolCreation = true;
            stage = StudioStage.PATH;
            host.preferences().setStage(stage.id());
        }

        @Override
        public void pickBlock(String title, String current, Consumer<String> onPick) {
            historyOpen = false;
            picker.open(title, current, onPick);
        }

        @Override
        public String tr(String key, Object... arguments) {
            return host.tr(key, arguments);
        }

        @Override
        public void send(String command) {
            host.send(command);
        }

        @Override
        public void copy(String text) {
            host.copy(text);
        }

        @Override
        public boolean shift() {
            return host.shift();
        }

        @Override
        public long now() {
            return host.now();
        }

        @Override
        public ToolContext context() {
            return host.context();
        }

        @Override
        public ToolsInfo info() {
            return host.info();
        }

        @Override
        public void requestState() {
            host.requestState();
        }

        @Override
        public StudioPreferences preferences() {
            return host.preferences();
        }

        @Override
        public void savePreferences() {
            host.savePreferences();
        }

        @Override
        public String blockName(String id) {
            return host.blockName(id);
        }

        @Override
        public String lookedAtBlock() {
            return host.lookedAtBlock();
        }

        @Override
        public void recipeAction(int action, ProceduralRecipe recipe, String instanceId) {
            host.recipeAction(action, recipe, instanceId);
        }

        @Override
        public List<ProceduralRecipe> recipes() {
            return host.recipes();
        }

        @Override
        public List<ProceduralInstanceSummary> instances() {
            return host.instances();
        }

        @Override
        public List<int[]> pathPoints() {
            return host.pathPoints();
        }

        @Override
        public boolean pathClosed() {
            return host.pathClosed();
        }

        @Override
        public int previewBlocks() {
            return host.previewBlocks();
        }

        @Override
        public boolean previewSettled() {
            return host.previewSettled();
        }

        @Override
        public boolean hologramHidden() {
            return host.hologramHidden();
        }

        @Override
        public void setHologramHidden(boolean hidden) {
            host.setHologramHidden(hidden);
        }

        @Override
        public String hologramStatus() {
            return host.hologramStatus();
        }

        @Override
        public Optional<JobStatusPayload> job() {
            return host.job();
        }

        @Override
        public void worldUndo() {
            host.worldUndo();
        }

        @Override
        public void worldRedo() {
            host.worldRedo();
        }

        @Override
        public WorldHistory worldHistory() {
            return host.worldHistory();
        }

        @Override
        public Blueprints blueprints() {
            return host.blueprints();
        }

        @Override
        public List<String> allBlocks() {
            return host.allBlocks();
        }

        @Override
        public List<String> hotbarBlocks() {
            return host.hotbarBlocks();
        }

        @Override
        public void suggest(String command) {
            host.suggest(command);
        }

        @Override
        public void close() {
            host.close();
        }
    }
}
