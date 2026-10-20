package com.komainu.moxia.studio.ui;

import com.komainu.moxia.gradient.GradientAxis;
import com.komainu.moxia.gradient.GradientPalette;
import com.komainu.moxia.recipe.CurveEasing;
import com.komainu.moxia.recipe.MaterialMetric;
import com.komainu.moxia.recipe.ProceduralInstanceSummary;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.recipe.RecipeActionPayload;
import com.komainu.moxia.recipe.RecipeExtensions;
import com.komainu.moxia.recipe.SocketPlacement;
import com.komainu.moxia.recipe.SurfaceMode;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.smooth.PieceFit;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.tube.TubeEngine;
import com.komainu.moxia.shape.tube.TubeFigures;
import com.komainu.moxia.shape.tube.TubeFormula;
import com.komainu.moxia.shape.tube.TubeFormulaPreset;
import com.komainu.moxia.shape.tube.TubeSettings;
import com.komainu.moxia.studio.BuildEstimate;
import com.komainu.moxia.studio.NumericRange;
import com.komainu.moxia.studio.ParameterHistory;
import com.komainu.moxia.studio.ShapeCategory;
import com.komainu.moxia.studio.ShapeSearch;
import com.komainu.moxia.studio.ShapeVariants;
import com.komainu.moxia.studio.StudioNames;
import com.komainu.moxia.studio.ValueUnit;
import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static com.komainu.moxia.studio.ui.StudioTheme.*;

/**
 * Las etapas de la figura sobre una trayectoria (Trayectoria, Figuras, Modificadores y Material), Envejecer y la
 * biblioteca de recetas e instancias. Guarda la receta en curso y su historial de deshacer. Todo se dibuja con
 * {@link StudioUi} y lo que toca el juego pasa por el {@link StudioHost}.
 */
public final class FigureStages {
    public static final int MAX_PATH_POINTS = 32;
    public static final double REACH = 4_096.0;
    private static final List<String> WEATHER_STYLES = StudioNames.WEATHER_STYLES;

    /** Un número de la receta (o de Envejecer): su rango, su unidad y su ayuda. */
    enum Control {
        RADIUS(ShapeVariants.RADIUS, null),
        SPACING(ShapeVariants.SPACING, null),
        THICKNESS(ShapeVariants.THICKNESS, null),
        ROLL(ShapeVariants.ROLL, ValueUnit.DEGREES),
        HEIGHT(ShapeVariants.HEIGHT, null),
        DITHER(new NumericRange(0, 100, 5, 25), ValueUnit.PERCENT_POINTS),
        START_SCALE(new NumericRange(25, 300, 5, 25), ValueUnit.PERCENT_POINTS),
        END_SCALE(new NumericRange(25, 300, 5, 25), ValueUnit.PERCENT_POINTS),
        START_SPACING(new NumericRange(50, 200, 5, 25), ValueUnit.PERCENT_POINTS),
        END_SPACING(new NumericRange(50, 200, 5, 25), ValueUnit.PERCENT_POINTS),
        TWIST(new NumericRange(-1440, 1440, 15, 90), ValueUnit.DEGREES),
        RHYTHM_INTERVAL(NumericRange.of(2, 32, 1), ValueUnit.NONE),
        CONFORM_RADIUS(NumericRange.of(2, 32, 1), ValueUnit.BLOCKS),
        SURFACE_OFFSET(new NumericRange(-8, 8, 1, 4), ValueUnit.BLOCKS),
        SOCKET_INTERVAL(new NumericRange(2, 64, 1, 8), ValueUnit.NONE),
        WEATHER_INTENSITY(new NumericRange(0, 100, 5, 25), ValueUnit.PERCENT_POINTS),
        WEATHER_COLLAPSE(new NumericRange(0, 100, 5, 25), ValueUnit.PERCENT_POINTS),
        WEATHER_VEGETATION(new NumericRange(0, 100, 5, 25), ValueUnit.PERCENT_POINTS);

        final NumericRange range;
        /** {@code null}: depende de la figura (cada una llama a su medida a su manera). */
        final ValueUnit unit;
        final String helpKey;

        Control(NumericRange range, ValueUnit unit) {
            this.range = range;
            this.unit = unit;
            this.helpKey = "gui.komaedit.help." + name().toLowerCase(Locale.ROOT);
        }

        String key() {
            return "figure:" + name();
        }
    }

    private final ParameterHistory<ProceduralRecipe> history = new ParameterHistory<>(100);
    private final NumberEdit numbers = new NumberEdit();
    private final SplittableRandom seeds = new SplittableRandom();
    private final BuildEstimate estimate = new BuildEstimate();
    private ProceduralRecipe draft = ProceduralRecipe.defaults();
    private int weatherIntensity = 50;
    private int weatherCollapse = 50;
    private int weatherVegetation = 50;
    private int weatherStyle;
    private boolean weatherWater;
    private boolean browsing;
    private boolean editingSearch;
    private String shapeSearch = "";
    private boolean editingName;
    private String nameInput = draft.name();
    private boolean editingFormula;
    private String formulaInput = "";
    /** Error de lo último escrito; si quedó sin aplicar, al reabrir el campo se recupera para corregirlo. */
    private String formulaError;
    private String selectedInstanceId = "";
    private int selectedPoint = -1;
    private boolean restoring;

    // ── Estado ──────────────────────────────────────────────────────────────────────────────────────────

    public ProceduralRecipe draft() {
        return draft;
    }

    public void setDraft(ProceduralRecipe recipe) {
        draft = recipe;
        nameInput = recipe.name();
    }

    public int selectedPoint() {
        return selectedPoint;
    }

    public void selectPoint(int index) {
        selectedPoint = index;
    }

    /** Si se está viendo la cuadrícula de figuras (y no las proporciones de la elegida). */
    public boolean browsing() {
        return browsing;
    }

    public void setBrowsing(boolean value) {
        browsing = value;
        if (!value) {
            editingSearch = false;
        }
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public void clearHistory() {
        history.clear();
    }

    /** Si hay un campo de estas etapas abierto: las letras se escriben y ningún atajo se dispara. */
    public boolean typing() {
        return editingFormula || editingName || editingSearch || numbers.editing();
    }

    public boolean dragging() {
        return numbers.dragging();
    }

    /** La clave con la que se recuerda por dónde se iba en Figuras: la cuadrícula o la figura elegida. */
    public String scrollKey() {
        return browsing ? "figure:grid" : "figure:" + draft.shape().id();
    }

    public int requiredPoints(StudioHost host) {
        return host.pathClosed() ? 3 : 2;
    }

    public boolean pathReady(StudioHost host) {
        return host.pathPoints().size() >= requiredPoints(host);
    }

    // ── Historial ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * Anota en el historial lo que cambió una acción. El nombre no cuenta: se fija al guardar o construir.
     * {@code group} funde los cambios seguidos del mismo control.
     */
    public void track(ProceduralRecipe before, String group, long now) {
        if (restoring) {
            restoring = false;
            return;
        }
        if (!before.withName("_").equals(draft.withName("_"))) {
            history.record(before, group, now);
        }
    }

    public void undo(StudioHost host) {
        restore(history.undo(draft), host, "gui.komaedit.history.undone", "gui.komaedit.history.nothing_undo");
    }

    public void redo(StudioHost host) {
        restore(history.redo(draft), host, "gui.komaedit.history.redone", "gui.komaedit.history.nothing_redo");
    }

    private void restore(ProceduralRecipe recipe, StudioHost host, String doneKey, String emptyKey) {
        if (recipe == null) {
            host.status(host.tr(emptyKey));
            return;
        }
        draft = recipe.withName(draft.name());
        restoring = true;
        numbers.reset();
        formulaError = null;
        host.status(host.tr(doneKey));
    }

    // ── Trayectoria ─────────────────────────────────────────────────────────────────────────────────────

    /**
     * @param useWith lo que se puede hacer con la trayectoria ya lista: ir a Figuras o a una herramienta de
     *                trayectoria (el id de la herramienta, o «figure»)
     */
    public void drawPath(StudioUi ui, Column column, StudioHost host, boolean compact, Consumer<String> useWith) {
        List<int[]> points = host.pathPoints();
        int nodes = points.size();
        int minimum = requiredPoints(host);
        boolean closed = host.pathClosed();

        Rect card = column.next(compact ? 26 : 32);
        ui.card(card);
        ui.fill(card.left(2), nodes >= minimum ? SUCCESS : WARNING);
        ui.bold(host.tr("gui.komaedit.path.count", nodes, MAX_PATH_POINTS), card.x() + 8, card.y() + 5,
                card.width() - 70, WHITE);
        ui.rightAligned(host.tr(closed ? "gui.komaedit.path.closed" : "gui.komaedit.path.open"), card.right() - 8,
                card.y() + 5, TEXT_SECONDARY);
        ui.label(nodes >= minimum ? host.tr("gui.komaedit.path.ready") : host.tr("gui.komaedit.path.minimum",
                minimum), card.x() + 8, card.y() + (compact ? 15 : 18), card.width() - 16,
                nodes >= minimum ? SUCCESS : WARNING);
        ui.register(card).tooltip(host.tr("gui.komaedit.path.how.click") + "\n" + host.tr("gui.komaedit.path.how.move")
                + "\n" + host.tr("gui.komaedit.path.how.camera"));

        Rect[] first = column.row(2, compact ? 16 : StudioUi.ROW);
        ui.button(first[0], host.tr("gui.komaedit.path.add_here"), StudioUi.Tone.SECONDARY)
                .onClick(() -> host.send("komaedit path here"))
                .tooltip(host.tr("gui.komaedit.help.path.add_here"));
        ui.button(first[1], host.tr("gui.komaedit.path.undo_point"), nodes == 0 ? StudioUi.Tone.DISABLED
                        : StudioUi.Tone.SECONDARY)
                .onClick(() -> host.send("komaedit path undo"));
        Rect[] second = column.row(2, compact ? 16 : StudioUi.ROW);
        ui.button(second[0], host.tr(closed ? "gui.komaedit.path.open_path" : "gui.komaedit.path.close_path"),
                        StudioUi.Tone.SECONDARY)
                .onClick(() -> host.send("komaedit path close"))
                .tooltip(host.tr("gui.komaedit.help.path.toggle_closed"));
        ui.button(second[1], host.tr("gui.komaedit.clear"), nodes == 0 ? StudioUi.Tone.DISABLED : StudioUi.Tone.DANGER)
                .onClick(() -> {
                    host.send("komaedit path clear");
                    host.status(host.tr("gui.komaedit.path.cleared"));
                })
                .tooltip(host.tr("gui.komaedit.help.path.clear"));

        if (nodes >= minimum) {
            ui.section(column, host.tr("gui.komaedit.path.use_with"));
            String[] targets = {"figure", "river", "ramp", "tree", "loft"};
            String[] labels = new String[targets.length];
            int[] widths = new int[targets.length];
            for (int index = 0; index < targets.length; index++) {
                labels[index] = targets[index].equals("figure") ? host.tr("gui.komaedit.stage.figure")
                        : host.tr("gui.komaedit.tools." + targets[index]);
                widths[index] = ui.chipWidth(labels[index]) + 14;
            }
            List<Rect> cells = column.flow(widths, 16);
            for (int index = 0; index < targets.length; index++) {
                String target = targets[index];
                StudioIcons.Icon icon = switch (target) {
                    case "figure" -> StudioIcons.Icon.FIGURE;
                    case "river" -> StudioIcons.Icon.RIVER;
                    case "ramp" -> StudioIcons.Icon.RAMP;
                    case "tree" -> StudioIcons.Icon.TREE;
                    default -> StudioIcons.Icon.LOFT;
                };
                ui.iconButton(cells.get(index), icon, labels[index], StudioUi.Tone.SECONDARY)
                        .tooltip(host.tr("gui.komaedit.path.use_with.help", labels[index]))
                        .onClick(() -> useWith.accept(target));
            }
        }

        if (nodes > 0) {
            ui.section(column, host.tr("gui.komaedit.path.nodes"), Integer.toString(nodes));
            for (int index = 0; index < nodes; index++) {
                Rect row = column.next(StudioUi.SMALL_ROW);
                if (!ui.visible(row)) {
                    continue;
                }
                int point = index;
                boolean selected = index == selectedPoint;
                ui.fill(row, selected ? CARD_ACTIVE : ui.hovered(row) ? CARD_HOVER : CARD);
                if (selected) {
                    ui.fill(row.left(2), ACCENT);
                }
                ui.label(String.format(Locale.ROOT, "%02d", index + 1), row.x() + 7, row.y() + 4,
                        index == nodes - 1 ? ACCENT_HOVER : MUTED);
                ui.label(coordinates(points.get(index)), row.x() + 30, row.y() + 4, row.width() - 36,
                        selected ? WHITE : TEXT_SECONDARY);
                ui.register(row).onClick(() -> {
                    selectedPoint = point == selectedPoint ? -1 : point;
                    host.status(selectedPoint < 0 ? host.tr("gui.komaedit.status.ready")
                            : host.tr("gui.komaedit.status.node.selected", point + 1));
                });
            }
        }

        if (!compact) {
            String key = "path:how";
            boolean open = host.preferences().expanded(key);
            ui.foldable(column, host.tr("gui.komaedit.path.how"), "", open).onClick(() -> {
                host.preferences().setExpanded(key, !open);
                host.savePreferences();
            });
            if (open) {
                ui.paragraph(column, host.tr("gui.komaedit.path.how.click"), TEXT_SECONDARY, 0);
                ui.paragraph(column, host.tr("gui.komaedit.path.how.move"), TEXT_SECONDARY, 0);
                ui.paragraph(column, host.tr("gui.komaedit.path.how.camera"), TEXT_SECONDARY, 0);
                ui.paragraph(column, host.tr("gui.komaedit.path.how.reach", (int) REACH), MUTED, 0);
            }
        }
    }

    private static String coordinates(int[] position) {
        return position[0] + ", " + position[1] + ", " + position[2];
    }

    // ── Figuras ─────────────────────────────────────────────────────────────────────────────────────────

    /** Las figuras de cada familia para la cuadrícula: favoritas primero (si hay) y luego por familia. */
    List<Map.Entry<String, List<PathShape>>> shapeGroups(StudioHost host) {
        List<Map.Entry<String, List<PathShape>>> groups = new ArrayList<>();
        if (!shapeSearch.isBlank()) {
            groups.add(Map.entry("", ShapeSearch.search(Arrays.asList(PathShape.values()), shapeSearch,
                    shape -> shapeName(host, shape))));
            return groups;
        }
        List<PathShape> favorites = Arrays.stream(PathShape.values())
                .filter(shape -> host.preferences().isFavorite(shape.id())).toList();
        if (!favorites.isEmpty()) {
            groups.add(Map.entry(ShapeCategory.FAVORITES.translationKey(), favorites));
        }
        for (ShapeCategory category : ShapeCategory.values()) {
            if (category == ShapeCategory.ALL || category == ShapeCategory.FAVORITES) {
                continue;
            }
            List<PathShape> shapes = Arrays.stream(PathShape.values())
                    .filter(shape -> ShapeCategory.of(shape) == category).toList();
            if (!shapes.isEmpty()) {
                groups.add(Map.entry(category.translationKey(), shapes));
            }
        }
        return groups;
    }

    public void drawFigure(StudioUi ui, Column column, StudioHost host, boolean compact) {
        if (browsing) {
            drawShapeBrowser(ui, column, host);
            return;
        }
        drawShapeHeader(ui, column, host);
        PathShape shape = draft.shape();
        if (!compact) {
            ui.paragraph(column, host.tr(StudioNames.shapeDescription(shape)), MUTED, 2);
        }
        ui.section(column, host.tr("gui.komaedit.proportions"));
        if (!compact) {
            drawPresets(ui, column, host);
        }
        stepper(ui, column, host, StudioNames.radiusLabel(shape), Control.RADIUS);
        stepper(ui, column, host, StudioNames.spacingLabel(shape), Control.SPACING);
        stepper(ui, column, host, StudioNames.thicknessLabel(shape), Control.THICKNESS);
        stepper(ui, column, host, StudioNames.rollLabel(shape), Control.ROLL);
        if (shape.usesSecondaryMeasure()) {
            stepper(ui, column, host, StudioNames.heightLabel(shape), Control.HEIGHT);
        }
        if (TubeFigures.supports(shape) || TubeFigures.hasExactEngine(shape)) {
            String key = "figure:tube";
            boolean open = !compact || host.preferences().expanded(key);
            if (compact) {
                ui.foldable(column, host.tr("gui.komaedit.tube"), "", open).onClick(() -> {
                    host.preferences().setExpanded(key, !open);
                    host.savePreferences();
                });
            } else {
                ui.section(column, host.tr("gui.komaedit.tube"));
            }
            if (open) {
                drawTubeRows(ui, column, host);
            }
        }
        if (compact) {
            String key = "figure:presets";
            boolean open = host.preferences().expanded(key);
            ui.foldable(column, host.tr("gui.komaedit.tools.presets"), "", open).onClick(() -> {
                host.preferences().setExpanded(key, !open);
                host.savePreferences();
            });
            if (open) {
                drawPresets(ui, column, host);
            }
        }
    }

    private void drawShapeHeader(StudioUi ui, Column column, StudioHost host) {
        PathShape shape = draft.shape();
        Rect row = column.next(22);
        Rect star = row.rightPart(18).inset(0, 2);
        Rect name = row.withoutRight(19, 1);
        boolean hovered = ui.hovered(name);
        ui.fill(name, hovered ? CARD_HOVER : CARD);
        ui.fill(name.left(2), ACCENT);
        ui.icon(StudioIcons.Icon.FIGURE, name.x() + 6, name.centerY() - 6, ACCENT_HOVER);
        ui.icon(StudioIcons.Icon.CHEVRON_DOWN, name.right() - 15, name.centerY() - 5, hovered ? TEXT : MUTED);
        ui.bold(shapeName(host, shape), name.x() + 22, name.centerY() - 4, name.width() - 40, WHITE);
        ui.register(name)
                .tooltip(shapeName(host, shape) + "\n" + host.tr(StudioNames.shapeDescription(shape)) + "\n"
                        + host.tr("gui.komaedit.figure.change"))
                .onClick(() -> setBrowsing(true));
        boolean favorite = host.preferences().isFavorite(shape.id());
        boolean overStar = ui.hovered(star);
        if (overStar) {
            ui.fill(star, CARD_HOVER);
        }
        ui.icon(favorite ? StudioIcons.Icon.STAR : StudioIcons.Icon.STAR_EMPTY, star.centerX() - 6,
                star.centerY() - 6, favorite ? FAVORITE : overStar ? TEXT : MUTED);
        ui.register(star).tooltip(host.tr(favorite ? "gui.komaedit.tools.unfavorite" : "gui.komaedit.tools.favorite"))
                .onClick(() -> toggleFavorite(host, shape));
    }

    private void toggleFavorite(StudioHost host, PathShape shape) {
        boolean added = host.preferences().toggleFavorite(shape.id());
        host.savePreferences();
        host.status(host.tr(added ? "gui.komaedit.favorite.added" : "gui.komaedit.favorite.removed",
                shapeName(host, shape)));
    }

    /** Buscador y cuadrícula de figuras por familia; recientes arriba. */
    private void drawShapeBrowser(StudioUi ui, Column column, StudioHost host) {
        Rect search = column.next(18);
        List<Map.Entry<String, List<PathShape>>> groups = shapeGroups(host);
        int results = groups.stream().mapToInt(group -> group.getValue().size()).sum();
        ui.searchField(search, shapeSearch, host.tr("gui.komaedit.search.placeholder"), editingSearch, results,
                        () -> {
                            shapeSearch = "";
                            editingSearch = false;
                        })
                .key("search")
                .onClick(() -> {
                    editingSearch = true;
                    host.status(host.tr("gui.komaedit.search.typing"));
                });
        if (shapeSearch.isBlank()) {
            drawShapeRecents(ui, column, host);
        }
        int columns = column.width() >= 300 ? 3 : 2;
        for (Map.Entry<String, List<PathShape>> group : groups) {
            if (!group.getKey().isEmpty()) {
                ui.section(column, host.tr(group.getKey()));
            }
            if (group.getValue().isEmpty()) {
                Rect empty = column.next(StudioUi.SMALL_ROW);
                ui.card(empty);
                ui.label(host.tr("gui.komaedit.search.empty"), empty.x() + 8, empty.y() + 4, empty.width() - 16, MUTED);
                continue;
            }
            List<Rect> cells = column.grid(group.getValue().size(), columns, StudioUi.SMALL_ROW + 2);
            for (int index = 0; index < group.getValue().size(); index++) {
                drawShapeButton(ui, cells.get(index), group.getValue().get(index), host);
            }
        }
    }

    private void drawShapeRecents(StudioUi ui, Column column, StudioHost host) {
        List<PathShape> recents = new ArrayList<>();
        for (String id : host.preferences().recents()) {
            PathShape shape = shapeById(id);
            if (shape != null && recents.size() < 4) {
                recents.add(shape);
            }
        }
        if (recents.isEmpty()) {
            return;
        }
        ui.section(column, host.tr("gui.komaedit.recents"));
        int[] widths = recents.stream().mapToInt(shape -> Math.min(column.width() / 2,
                ui.chipWidth(shapeName(host, shape)))).toArray();
        List<Rect> placed = column.flow(widths, 14);
        for (int index = 0; index < recents.size(); index++) {
            PathShape shape = recents.get(index);
            ui.chip(placed.get(index), shapeName(host, shape), shape == draft.shape(), TEXT_SECONDARY)
                    .tooltip(shapeName(host, shape) + "\n" + host.tr(StudioNames.shapeDescription(shape)))
                    .onClick(() -> selectShape(host, shape));
        }
    }

    private void drawShapeButton(StudioUi ui, Rect cell, PathShape shape, StudioHost host) {
        if (!ui.visible(cell)) {
            return;
        }
        boolean hovered = ui.hovered(cell);
        boolean selected = draft.shape() == shape;
        ui.fill(cell, selected ? CARD_ACTIVE : hovered ? CARD_HOVER : CARD);
        if (selected) {
            ui.fill(cell.left(2), ACCENT);
        }
        Rect star = cell.rightPart(14);
        boolean favorite = host.preferences().isFavorite(shape.id());
        ui.label(shapeName(host, shape), cell.x() + 7, cell.centerY() - 4, cell.width() - (favorite || hovered ? 22
                : 10), selected ? WHITE : hovered ? TEXT : TEXT_SECONDARY);
        if (favorite || hovered) {
            ui.icon(favorite ? StudioIcons.Icon.STAR : StudioIcons.Icon.STAR_EMPTY, star.x() + 1, cell.centerY() - 6,
                    favorite || ui.hovered(star) ? FAVORITE : MUTED);
        }
        String help = shapeName(host, shape) + "\n" + host.tr(StudioNames.shapeDescription(shape)) + "\n"
                + host.tr("gui.komaedit.help.favorite_star");
        ui.register(cell).onClick(() -> selectShape(host, shape)).tooltip(help);
        ui.register(star).onClick(() -> toggleFavorite(host, shape)).tooltip(help);
    }

    /** Presets y variante al azar, con su ancho natural para que ningún nombre se corte. */
    private void drawPresets(StudioUi ui, Column column, StudioHost host) {
        ShapeVariants.Preset[] presets = ShapeVariants.Preset.values();
        String[] labels = new String[presets.length + 1];
        int[] widths = new int[labels.length];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = index < presets.length
                    ? host.tr(presets[index].translationKey()) : host.tr("gui.komaedit.preset.random");
            widths[index] = ui.chipWidth(labels[index]);
        }
        List<Rect> cells = column.flow(widths, 14);
        for (int index = 0; index < presets.length; index++) {
            ShapeVariants.Preset preset = presets[index];
            ui.chip(cells.get(index), labels[index], false, TEXT_SECONDARY)
                    .onClick(() -> {
                        applyProportions(ShapeVariants.preset(draft.shape(), preset));
                        host.status(host.tr("gui.komaedit.preset.applied", host.tr(preset.translationKey())));
                    })
                    .tooltip(labels[index] + "\n" + host.tr(preset.translationKey() + ".help"))
                    .group("preset");
        }
        ui.chip(cells.get(presets.length), labels[presets.length], false, TEXT_SECONDARY)
                .onClick(() -> randomVariant(host))
                .tooltip(labels[presets.length] + "\n" + host.tr("gui.komaedit.preset.random.help"))
                .group("preset");
    }

    /**
     * Filas del espacio de tubo: motor (en las figuras clásicas que tienen versión exacta), perfil o fórmula,
     * relleno, extremos, orientación de la sección y piezas.
     */
    private void drawTubeRows(StudioUi ui, Column column, StudioHost host) {
        PathShape shape = draft.shape();
        if (TubeFigures.hasExactEngine(shape)) {
            cycle(ui, column, host, "gui.komaedit.tube.engine", host.tr(StudioNames.engine(tubeEngine())),
                    TubeEngine.OPTION, direction -> {
                        TubeEngine engine = TubeEngine.byIndex(tubeEngine().ordinal() + direction);
                        Map<String, Double> next = new LinkedHashMap<>(draft.options());
                        if (engine == TubeEngine.EXACT) {
                            next.put(TubeEngine.OPTION, (double) engine.ordinal());
                        } else {
                            next.remove(TubeEngine.OPTION);
                        }
                        draft = draft.withOptions(next);
                        host.status(host.tr(engine == TubeEngine.EXACT
                                ? "gui.komaedit.status.engine.exact" : "gui.komaedit.status.engine.classic"));
                    });
            if (tubeEngine() != TubeEngine.EXACT) {
                return;
            }
        }
        if (shape == PathShape.EXPRESSION) {
            String formulaName = draft.formula().isEmpty()
                    ? host.tr(StudioNames.formula(formulaPreset())) : host.tr("gui.komaedit.tube.formula.custom");
            cycle(ui, column, host, "gui.komaedit.tube.formula", formulaName, "preset", direction -> {
                // Desde una fórmula propia, el primer paso vuelve a la de serie que había debajo.
                TubeFormulaPreset preset = draft.formula().isEmpty()
                        ? TubeFormulaPreset.byIndex(formulaPreset().ordinal() + direction)
                        : formulaPreset();
                setTubeOption("preset", preset.ordinal());
                draft = draft.withFormula("");
                formulaError = null;
                host.status(host.tr("gui.komaedit.status.formula.preset", host.tr(StudioNames.formula(preset)),
                        host.tr(StudioNames.formulaDescription(preset))));
            });
            drawFormulaField(ui, column, host);
        }
        if (shape == PathShape.SWEEP) {
            cycle(ui, column, host, "gui.komaedit.tube.profile", host.tr(StudioNames.profile(tubeProfile())), "profile",
                    direction -> {
                        TubeSettings.TubeProfile profile = TubeSettings.TubeProfile.byIndex(
                                tubeProfile().ordinal() + direction);
                        setTubeOption("profile", profile.ordinal());
                        host.status(host.tr(profile.usesSides() ? "gui.komaedit.status.profile.sides"
                                : "gui.komaedit.status.profile", host.tr(StudioNames.profile(profile))));
                    });
        }
        if (shape == PathShape.SWEEP || shape == PathShape.EXPRESSION) {
            boolean hollow = tubeOption("hollow", 0.0) >= 0.5;
            cycle(ui, column, host, "gui.komaedit.tube.fill", host.tr(hollow ? "gui.komaedit.tube.fill.hollow"
                    : "gui.komaedit.tube.fill.solid"), "hollow", direction -> {
                boolean next = tubeOption("hollow", 0.0) < 0.5;
                setTubeOption("hollow", next ? 1.0 : 0.0);
                host.status(host.tr(next ? "gui.komaedit.status.hollow.on" : "gui.komaedit.status.hollow.off"));
            });
        }
        TubeSettings.EndMode end = TubeSettings.EndMode.byIndex((int) tubeOption("end", 0.0));
        cycle(ui, column, host, "gui.komaedit.tube.ends", host.tr(StudioNames.end(end)), "end", direction -> {
            TubeSettings.EndMode mode = TubeSettings.EndMode.byIndex((int) tubeOption("end", 0.0) + direction);
            setTubeOption("end", mode.ordinal());
            host.status(host.tr("gui.komaedit.status.end", host.tr(StudioNames.end(mode))));
        });
        TubeSettings.NormalMode normal = TubeSettings.NormalMode.byIndex((int) tubeOption("normal", 0.0));
        cycle(ui, column, host, "gui.komaedit.tube.normals", host.tr(StudioNames.normal(normal)), "normal",
                direction -> {
                    TubeSettings.NormalMode mode = TubeSettings.NormalMode.byIndex(
                            (int) tubeOption("normal", 0.0) + direction);
                    setTubeOption("normal", mode.ordinal());
                    host.status(host.tr("gui.komaedit.status.normal." + mode.id()));
                });
        cycle(ui, column, host, "gui.komaedit.tube.pieces", host.tr(StudioNames.smooth(smoothMode())), "smooth",
                direction -> {
                    SmoothMode mode = SmoothMode.byIndex(smoothMode().ordinal() + direction);
                    setTubeOption("smooth", mode.ordinal());
                    host.status(host.tr("gui.komaedit.status.smooth." + mode.id()));
                });
        if (smoothMode() != SmoothMode.NONE) {
            cycle(ui, column, host, "gui.komaedit.tube.coverage", Math.round(coverage() * 100.0) + " %", "coverage",
                    direction -> {
                        double next = Math.clamp(Math.round(coverage() * 10.0 + direction) / 10.0, 0.0, 1.0);
                        setTubeOption("coverage", next);
                        host.status(next <= 0.0 ? host.tr("gui.komaedit.status.coverage.none")
                                : next >= 1.0 ? host.tr("gui.komaedit.status.coverage.full")
                                : host.tr("gui.komaedit.status.coverage.partial", Math.round(next * 100.0)));
                    });
        }
    }

    /** Campo de fórmula: muestra la activa (gris si es de serie) y, al editar, el final de lo escrito. */
    private void drawFormulaField(StudioUi ui, Column column, StudioHost host) {
        Rect field = column.next(18);
        String shown = editingFormula ? formulaInput : draft.formula();
        ui.field(field, shown, activeFormulaSource(), editingFormula, formulaError != null)
                .key("formula")
                .tooltip(host.tr("gui.komaedit.help.formula"))
                .onClick(() -> {
                    if (editingFormula) {
                        return;
                    }
                    editingFormula = true;
                    if (formulaError == null) {
                        formulaInput = activeFormulaSource();
                    }
                    host.status(formulaError == null ? host.tr("gui.komaedit.status.formula.typing") : formulaError);
                });
    }

    // ── Modificadores ───────────────────────────────────────────────────────────────────────────────────

    public void drawModifiers(StudioUi ui, Column column, StudioHost host, boolean compact) {
        RecipeExtensions extensions = draft.extensions();
        boolean changed = !extensions.equals(RecipeExtensions.DEFAULT);
        Rect top = column.next(16);
        ui.button(top, host.tr("gui.komaedit.modifiers.reset"), changed ? StudioUi.Tone.SECONDARY
                        : StudioUi.Tone.DISABLED)
                .tooltip(host.tr("gui.komaedit.modifiers.intro"))
                .onClick(() -> {
                    draft = draft.withExtensions(RecipeExtensions.DEFAULT);
                    host.status(host.tr("gui.komaedit.status.modifiers.reset"));
                });

        ui.section(column, host.tr("gui.komaedit.modifiers.curve"));
        stepper(ui, column, host, "gui.komaedit.param.start_scale", Control.START_SCALE);
        stepper(ui, column, host, "gui.komaedit.param.end_scale", Control.END_SCALE);
        stepper(ui, column, host, "gui.komaedit.param.start_spacing", Control.START_SPACING);
        stepper(ui, column, host, "gui.komaedit.param.end_spacing", Control.END_SPACING);
        stepper(ui, column, host, "gui.komaedit.param.twist", Control.TWIST);
        cycle(ui, column, host, "gui.komaedit.param.easing", host.tr(StudioNames.easing(extensions.easing())), "easing",
                direction -> {
                    RecipeExtensions current = draft.extensions();
                    CurveEasing easing = cycle(CurveEasing.values(), current.easing(), direction);
                    draft = draft.withExtensions(current.withCurve(current.startScalePercent(),
                            current.endScalePercent(), current.startSpacingPercent(), current.endSpacingPercent(),
                            current.twistDegrees(), easing));
                    host.status(host.tr("gui.komaedit.status.easing", host.tr(StudioNames.easing(easing))));
                });

        ui.section(column, host.tr("gui.komaedit.modifiers.fusion"));
        ui.toggle(column.next(), host.tr("gui.komaedit.modifiers.fusion.enabled"), extensions.rhythmEnabled())
                .tooltip(host.tr("gui.komaedit.help.fusion"))
                .onClick(() -> {
                    RecipeExtensions current = draft.extensions();
                    boolean enabled = !current.rhythmEnabled();
                    draft = draft.withExtensions(current.withRhythm(enabled, current.accentShape(),
                            current.rhythmInterval()));
                    host.status(host.tr(enabled ? "gui.komaedit.status.fusion.on" : "gui.komaedit.status.fusion.off"));
                });
        if (extensions.rhythmEnabled()) {
            cycle(ui, column, host, "gui.komaedit.param.accent", shapeName(host, extensions.accentShape()), "accent",
                    direction -> {
                        RecipeExtensions current = draft.extensions();
                        draft = draft.withExtensions(current.withRhythm(current.rhythmEnabled(),
                                cycle(PathShape.values(), current.accentShape(), direction),
                                current.rhythmInterval()));
                    });
            stepper(ui, column, host, "gui.komaedit.param.rhythm_interval", Control.RHYTHM_INTERVAL);
        }

        ui.section(column, host.tr("gui.komaedit.modifiers.surface"));
        SurfaceMode[] modes = SurfaceMode.values();
        List<Rect> modeCells = column.grid(modes.length, 2, 16);
        for (int index = 0; index < modes.length; index++) {
            SurfaceMode mode = modes[index];
            ui.button(modeCells.get(index), host.tr(StudioNames.surface(mode)),
                            extensions.surfaceMode() == mode ? StudioUi.Tone.SELECTED : StudioUi.Tone.SECONDARY)
                    .group("surface")
                    .onClick(() -> {
                        RecipeExtensions current = draft.extensions();
                        draft = draft.withExtensions(current.withSurface(mode, current.conformRadius(),
                                current.surfaceOffset()));
                        host.status(host.tr("gui.komaedit.status.surface", host.tr(StudioNames.surface(mode))));
                    });
        }
        if (extensions.surfaceMode() != SurfaceMode.NONE) {
            stepper(ui, column, host, "gui.komaedit.param.conform_radius", Control.CONFORM_RADIUS);
            stepper(ui, column, host, "gui.komaedit.param.surface_offset", Control.SURFACE_OFFSET);
        }

        ui.section(column, host.tr("gui.komaedit.modifiers.kitbash"));
        ui.toggle(column.next(), host.tr("gui.komaedit.modifiers.kitbash.enabled"), extensions.socketsEnabled())
                .tooltip(host.tr("gui.komaedit.help.kitbash"))
                .onClick(() -> {
                    RecipeExtensions current = draft.extensions();
                    boolean enabled = !current.socketsEnabled();
                    draft = draft.withExtensions(current.withSockets(enabled, current.socketPlacement(),
                            current.socketInterval()));
                    host.status(host.tr(enabled ? "gui.komaedit.status.kitbash.on" : "gui.komaedit.status.kitbash.off"));
                });
        if (extensions.socketsEnabled()) {
            cycle(ui, column, host, "gui.komaedit.param.socket_placement",
                    host.tr(StudioNames.socket(extensions.socketPlacement())), "socket_placement", direction -> {
                        RecipeExtensions current = draft.extensions();
                        draft = draft.withExtensions(current.withSockets(current.socketsEnabled(),
                                cycle(SocketPlacement.values(), current.socketPlacement(), direction),
                                current.socketInterval()));
                    });
            stepper(ui, column, host, "gui.komaedit.param.socket_interval", Control.SOCKET_INTERVAL);
        }

        ui.section(column, host.tr("gui.komaedit.modifiers.cleanup"));
        ui.toggle(column.next(), host.tr("gui.komaedit.modifiers.close_gaps"), extensions.closeMicroGaps())
                .tooltip(host.tr("gui.komaedit.help.close_gaps"))
                .onClick(() -> {
                    RecipeExtensions current = draft.extensions();
                    draft = draft.withExtensions(current.withQa(!current.closeMicroGaps(), current.removeIsolated()));
                });
        ui.toggle(column.next(), host.tr("gui.komaedit.modifiers.remove_isolated"), extensions.removeIsolated())
                .tooltip(host.tr("gui.komaedit.help.remove_isolated"))
                .onClick(() -> {
                    RecipeExtensions current = draft.extensions();
                    draft = draft.withExtensions(current.withQa(current.closeMicroGaps(), !current.removeIsolated()));
                });
    }

    // ── Material ────────────────────────────────────────────────────────────────────────────────────────

    public void drawMaterial(StudioUi ui, Column column, StudioHost host, boolean compact) {
        Rect[] mode = column.row(2, compact ? 16 : StudioUi.ROW);
        ui.button(mode[0], host.tr("gui.komaedit.material.block"), draft.usesGradient() ? StudioUi.Tone.SECONDARY
                        : StudioUi.Tone.SELECTED)
                .onClick(() -> draft = copyMaterial(""));
        ui.button(mode[1], host.tr("gui.komaedit.material.gradient"),
                        draft.usesGradient() ? StudioUi.Tone.SELECTED : StudioUi.Tone.SECONDARY)
                .onClick(() -> draft = copyMaterial(draft.usesGradient()
                        ? draft.paletteId() : GradientPalette.GLOW_CYAN.id()));

        if (!draft.usesGradient()) {
            column.section();
            String pattern = draft.pattern();
            String blockId = pattern.contains(":") ? pattern : "minecraft:" + pattern;
            StudioUi.Widget[] zones = ui.blockRow(column.next(), new StudioUi.BlockRow(
                    host.tr("gui.komaedit.material.base_block"), blockId, host.blockName(blockId), false, false,
                    host.tr("gui.komaedit.material.library_hint"), true, host.tr("gui.komaedit.tools.block.eyedropper")));
            zones[0].onClick(() -> host.pickBlock(host.tr("gui.komaedit.blocks.for_figure"), blockId,
                    id -> setPattern(host, id)));
            zones[1].onClick(() -> {
                String looked = host.lookedAtBlock();
                if (looked == null) {
                    host.status(host.tr("gui.komaedit.blocks.look_nothing"));
                } else {
                    setPattern(host, looked);
                }
            });
            return;
        }

        ui.section(column, host.tr("gui.komaedit.material.direction"));
        GradientAxis[] axes = GradientAxis.values();
        Rect[] axisCells = column.row(axes.length, 16);
        for (int index = 0; index < axes.length; index++) {
            GradientAxis axis = axes[index];
            ui.button(axisCells[index], axis.id().toUpperCase(Locale.ROOT),
                            draft.gradientAxis() == axis ? StudioUi.Tone.SELECTED : StudioUi.Tone.SECONDARY)
                    .onClick(() -> draft = draft.copy(draft.name(), draft.shape(), draft.pattern(), draft.radius(),
                            draft.spacing(), draft.thickness(), draft.rollDegrees(), draft.height(), draft.paletteId(),
                            axis, draft.dither(), draft.reverse()));
        }
        stepper(ui, column, host, "gui.komaedit.param.dither", Control.DITHER);
        ui.toggle(column.next(), host.tr("gui.komaedit.material.reverse"), draft.reverse())
                .onClick(() -> draft = draft.copy(draft.name(), draft.shape(), draft.pattern(), draft.radius(),
                        draft.spacing(), draft.thickness(), draft.rollDegrees(), draft.height(), draft.paletteId(),
                        draft.gradientAxis(), draft.dither(), !draft.reverse()));

        ui.section(column, host.tr("gui.komaedit.material.distribution"));
        MaterialMetric[] metrics = MaterialMetric.values();
        List<Rect> metricCells = column.grid(metrics.length, 2, 16);
        MaterialMetric current = draft.extensions().materialMetric();
        for (int index = 0; index < metrics.length; index++) {
            MaterialMetric metric = metrics[index];
            ui.button(metricCells.get(index), host.tr(StudioNames.metric(metric)),
                            current == metric ? StudioUi.Tone.SELECTED : StudioUi.Tone.SECONDARY)
                    .tooltip(host.tr(StudioNames.metric(metric)) + "\n" + host.tr(StudioNames.metricHelp(metric)))
                    .onClick(() -> draft = draft.withExtensions(draft.extensions().withMaterialMetric(metric)));
        }
        if (!compact) {
            ui.paragraph(column, host.tr(StudioNames.metricHelp(current)), MUTED, 2);
        }

        ui.section(column, host.tr("gui.komaedit.material.palette"));
        GradientPalette[] palettes = GradientPalette.values();
        List<Rect> paletteCells = column.grid(palettes.length, 2, 16);
        for (int index = 0; index < palettes.length; index++) {
            GradientPalette palette = palettes[index];
            if (!ui.visible(paletteCells.get(index))) {
                continue;
            }
            ui.button(paletteCells.get(index), palette.displayName(),
                            palette.id().equals(draft.paletteId()) ? StudioUi.Tone.SELECTED : StudioUi.Tone.SECONDARY)
                    .onClick(() -> draft = copyMaterial(palette.id()));
        }
    }

    private void setPattern(StudioHost host, String id) {
        draft = draft.copy(draft.name(), draft.shape(), id, draft.radius(), draft.spacing(), draft.thickness(),
                draft.rollDegrees(), draft.height(), "", draft.gradientAxis(), draft.dither(), draft.reverse());
        host.status(host.tr("gui.komaedit.status.block.selected", host.blockName(id)));
    }

    // ── Envejecer ───────────────────────────────────────────────────────────────────────────────────────

    /** Envejecer la selección de WorldEdit: clima, intensidad, derrumbe, vida y charcos. Se lanza desde el pie. */
    public void drawWeather(StudioUi ui, Column column, StudioHost host, boolean compact) {
        ui.section(column, host.tr("gui.komaedit.weather.climate"));
        List<Rect> cells = column.grid(WEATHER_STYLES.size(), 2, compact ? 16 : 18);
        for (int index = 0; index < WEATHER_STYLES.size(); index++) {
            int style = index;
            ui.button(cells.get(index), host.tr(StudioNames.weather(WEATHER_STYLES.get(index))),
                            weatherStyle == index ? StudioUi.Tone.SELECTED : StudioUi.Tone.SECONDARY)
                    .tooltip(host.tr(StudioNames.weather(WEATHER_STYLES.get(index))) + "\n"
                            + host.tr(StudioNames.weatherHint(WEATHER_STYLES.get(index))))
                    .onClick(() -> weatherStyle = style);
        }
        if (!compact) {
            ui.paragraph(column, host.tr(StudioNames.weatherHint(WEATHER_STYLES.get(weatherStyle))), MUTED, 2);
        }
        ui.section(column, host.tr("gui.komaedit.weather.wear"));
        stepper(ui, column, host, "gui.komaedit.param.weather_intensity", Control.WEATHER_INTENSITY);
        stepper(ui, column, host, "gui.komaedit.param.weather_collapse", Control.WEATHER_COLLAPSE);
        stepper(ui, column, host, "gui.komaedit.param.weather_vegetation", Control.WEATHER_VEGETATION);
        ui.toggle(column.next(), host.tr("gui.komaedit.weather.water"), weatherWater)
                .onClick(() -> weatherWater = !weatherWater);
    }

    /** La orden de Envejecer con lo elegido. */
    public String weatherCommand() {
        return "komaedit weather " + weatherIntensity + " " + WEATHER_STYLES.get(weatherStyle)
                + " -d " + weatherCollapse + " -v " + weatherVegetation + (weatherWater ? " -agua" : "");
    }

    public void runWeather(StudioHost host) {
        host.send(weatherCommand());
        host.status(host.tr("gui.komaedit.status.weather.running"));
    }

    // ── Biblioteca: recetas e instancias ────────────────────────────────────────────────────────────────

    public void drawInstances(StudioUi ui, Column column, StudioHost host) {
        if (!selectedInstanceId.isEmpty()) {
            Rect[] actions = column.row(2, StudioUi.ROW);
            ui.button(actions[0], host.tr("gui.komaedit.instance.update"), StudioUi.Tone.PRIMARY)
                    .tooltip(host.tr("gui.komaedit.help.instance.update"))
                    .onClick(() -> {
                        commitName(host);
                        host.recipeAction(RecipeActionPayload.UPDATE_INSTANCE, draft, selectedInstanceId);
                        host.status(host.tr("gui.komaedit.status.instance.updated"));
                    });
            ui.button(actions[1], host.tr("gui.komaedit.instance.delete"), StudioUi.Tone.DANGER).onClick(() -> {
                host.recipeAction(RecipeActionPayload.DELETE_INSTANCE, draft, selectedInstanceId);
                selectedInstanceId = "";
                host.status(host.tr("gui.komaedit.status.instance.deleted"));
            });
        }
        List<ProceduralInstanceSummary> instances = host.instances();
        ui.section(column, host.tr("gui.komaedit.instance.list", instances.size()));
        if (instances.isEmpty()) {
            ui.paragraph(column, host.tr("gui.komaedit.instance.empty"), MUTED, 0);
            return;
        }
        for (ProceduralInstanceSummary instance : instances) {
            Rect row = column.next(28);
            if (!ui.visible(row)) {
                continue;
            }
            String subtitle = host.tr("gui.komaedit.instance.summary", shapeName(host, instance.recipe().shape()),
                    instance.blockCount(), instance.pathPoints());
            ui.listItem(row, instance.recipe().name(), subtitle, instance.id().equals(selectedInstanceId), 0)
                    .onClick(() -> {
                        selectedInstanceId = instance.id();
                        draft = instance.recipe();
                        nameInput = draft.name();
                        host.recipeAction(RecipeActionPayload.LOAD_INSTANCE, draft, selectedInstanceId);
                        host.status(host.tr("gui.komaedit.status.instance.selected"));
                    });
        }
    }

    public void drawRecipes(StudioUi ui, Column column, StudioHost host) {
        ui.section(column, host.tr("gui.komaedit.recipe.current"));
        Rect row = column.next(18);
        Rect save = row.rightPart(70);
        Rect name = row.withoutRight(70, Column.GAP);
        ui.field(name, nameInput, host.tr("gui.komaedit.recipe.name"), editingName, false)
                .key("name")
                .tooltip(host.tr("gui.komaedit.help.recipe.name"))
                .onClick(() -> editingName = true);
        ui.button(save, host.tr("gui.komaedit.save"), StudioUi.Tone.PRIMARY).onClick(() -> saveRecipe(host))
                .tooltip(host.tr("gui.komaedit.save") + "\n" + host.tr("gui.komaedit.shortcut.save.keys"));
        Rect[] packages = column.row(2, 16);
        ui.button(packages[0], host.tr("gui.komaedit.recipe.export"), StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.help.recipe.export"))
                .onClick(() -> {
                    commitName(host);
                    host.recipeAction(RecipeActionPayload.EXPORT_PACKAGE, draft, "");
                    host.status(host.tr("gui.komaedit.status.recipe.export"));
                });
        ui.button(packages[1], host.tr("gui.komaedit.recipe.import"), StudioUi.Tone.SECONDARY)
                .tooltip(host.tr("gui.komaedit.help.recipe.import"))
                .onClick(() -> {
                    commitName(host);
                    host.recipeAction(RecipeActionPayload.IMPORT_PACKAGE, draft, "");
                    host.status(host.tr("gui.komaedit.status.recipe.import", draft.name()));
                });

        List<ProceduralRecipe> recipes = host.recipes();
        ui.section(column, host.tr("gui.komaedit.recipe.saved", recipes.size()));
        if (recipes.isEmpty()) {
            ui.paragraph(column, host.tr("gui.komaedit.recipe.empty"), MUTED, 0);
            return;
        }
        for (ProceduralRecipe recipe : recipes) {
            Rect item = column.next(28);
            if (!ui.visible(item)) {
                continue;
            }
            ui.listItem(item, recipe.name(), shapeName(host, recipe.shape()) + " · " + materialSummary(host, recipe),
                            recipe.name().equals(draft.name()), 22)
                    .onClick(() -> {
                        draft = recipe;
                        nameInput = recipe.name();
                        host.status(host.tr("gui.komaedit.status.recipe.loaded", recipe.name()));
                    });
            Rect delete = new Rect(item.right() - 22, item.y() + 6, 16, 16);
            ui.button(delete, "×", StudioUi.Tone.DANGER)
                    .tooltip(host.tr("gui.komaedit.help.recipe.delete"))
                    .onClick(() -> {
                        host.recipeAction(RecipeActionPayload.DELETE, recipe, "");
                        host.status(host.tr("gui.komaedit.status.recipe.deleted", recipe.name()));
                    });
        }
    }

    public void saveRecipe(StudioHost host) {
        commitName(host);
        host.recipeAction(RecipeActionPayload.SAVE, draft, "");
        host.status(host.tr("gui.komaedit.status.recipe.saved", draft.name()));
    }

    // ── Construir ───────────────────────────────────────────────────────────────────────────────────────

    /** Si el aviso de obra grande está armado (falta una segunda pulsación). */
    public boolean armed(long now) {
        return estimate.armed(now);
    }

    public String buildLabel(StudioHost host, long now) {
        if (!pathReady(host)) {
            return host.tr("gui.komaedit.build.missing", requiredPoints(host) - host.pathPoints().size());
        }
        return estimate.armed(now) ? host.tr("gui.komaedit.build_confirm") : host.tr("gui.komaedit.build");
    }

    public StudioUi.Tone buildTone(StudioHost host, long now) {
        return !pathReady(host) ? StudioUi.Tone.DISABLED : estimate.armed(now) ? StudioUi.Tone.WARNING
                : StudioUi.Tone.PRIMARY;
    }

    /** Construir la figura; con obra grande pide una segunda pulsación. Devuelve si se mandó. */
    public boolean build(StudioHost host, long now) {
        if (!pathReady(host)) {
            host.status(host.tr("gui.komaedit.status.need_points", requiredPoints(host)));
            return false;
        }
        int blocks = host.previewBlocks();
        if (!estimate.confirm(blocks, now)) {
            host.status(host.tr("gui.komaedit.build_large", BuildEstimate.compact(blocks)));
            return false;
        }
        commitName(host);
        host.recipeAction(RecipeActionPayload.BUILD, draft, "");
        selectedInstanceId = "";
        host.status(host.tr("gui.komaedit.status.build.sent"));
        return true;
    }

    // ── Filas con estado ────────────────────────────────────────────────────────────────────────────────

    /** La unidad de un número de la receta: la del control o, en las proporciones, la de la medida de la figura. */
    static ValueUnit unit(Control control, String labelKey) {
        if (control.unit != null) {
            return control.unit;
        }
        String name = labelKey.substring(labelKey.lastIndexOf('.') + 1);
        return switch (name) {
            case "sides", "repetitions", "symmetry", "branching", "detail", "frequency", "organicity", "elasticity",
                 "chaos", "density", "relief_3d", "curvature" -> ValueUnit.NONE;
            case "roll" -> ValueUnit.DEGREES;
            default -> ValueUnit.BLOCKS;
        };
    }

    private void stepper(StudioUi ui, Column column, StudioHost host, String labelKey, Control control) {
        Rect row = column.next();
        if (!ui.visible(row)) {
            return;
        }
        NumericRange range = range(control);
        int value = value(control);
        int defaultValue = defaultValue(control);
        ValueUnit unit = unit(control, labelKey);
        NumberView view = NumberView.of(value, null, value, defaultValue, false, range, 0, unit, host::tr);
        String help = view.help(host.tr(labelKey) + "\n" + host.tr(control.helpKey),
                unit.format(defaultValue, 0, host::tr), host::tr);
        ui.stepper(row, new StudioUi.Stepper(host.tr(labelKey), view.value(), view.shortValue(), view.fraction(),
                view.defaultFraction(), view.modified(), view.range(), numbers.editing(control.key()),
                numbers.dragging(control.key()), numbers.input(),
                direction -> setValue(control, range.stepped(value(control), direction, host.shift())),
                (x, y) -> numbers.press(control.key(), value(control), x),
                () -> setValue(control, defaultValue(control)),
                help, control.name(), control.key()));
    }

    private void cycle(StudioUi ui, Column column, StudioHost host, String labelKey, String value, String option,
                       IntConsumer step) {
        Rect row = column.next();
        if (!ui.visible(row)) {
            return;
        }
        ui.cycle(row, host.tr(labelKey), value, step)
                .onReset(() -> resetOption(option))
                .tooltip(host.tr(labelKey) + "\n" + host.tr("gui.komaedit.help.option." + option) + "\n"
                        + host.tr("gui.komaedit.help.cycle"))
                .group("option:" + option);
    }

    // ── Valores de la receta ────────────────────────────────────────────────────────────────────────────

    /** Rango del control; la altura del torii va de 4 en 4 como antes. */
    NumericRange range(Control control) {
        if (control == Control.HEIGHT && draft.shape() == PathShape.TORII) {
            return new NumericRange(3, 256, 4, 20);
        }
        return control.range;
    }

    int value(Control control) {
        RecipeExtensions extensions = draft.extensions();
        return switch (control) {
            case RADIUS -> draft.radius();
            case SPACING -> draft.spacing();
            case THICKNESS -> draft.thickness();
            case ROLL -> draft.rollDegrees();
            case HEIGHT -> draft.height();
            case DITHER -> draft.dither();
            case START_SCALE -> extensions.startScalePercent();
            case END_SCALE -> extensions.endScalePercent();
            case START_SPACING -> extensions.startSpacingPercent();
            case END_SPACING -> extensions.endSpacingPercent();
            case TWIST -> extensions.twistDegrees();
            case RHYTHM_INTERVAL -> extensions.rhythmInterval();
            case CONFORM_RADIUS -> extensions.conformRadius();
            case SURFACE_OFFSET -> extensions.surfaceOffset();
            case SOCKET_INTERVAL -> extensions.socketInterval();
            case WEATHER_INTENSITY -> weatherIntensity;
            case WEATHER_COLLAPSE -> weatherCollapse;
            case WEATHER_VEGETATION -> weatherVegetation;
        };
    }

    /** El valor con el que se restablece con clic derecho: el de serie de la figura o de los modificadores. */
    int defaultValue(Control control) {
        ShapeVariants.Proportions shape = ShapeVariants.defaults(draft.shape());
        RecipeExtensions extensions = RecipeExtensions.DEFAULT;
        return switch (control) {
            case RADIUS -> shape.radius();
            case SPACING -> shape.spacing();
            case THICKNESS -> shape.thickness();
            case ROLL -> shape.rollDegrees();
            case HEIGHT -> shape.height();
            case DITHER -> ProceduralRecipe.defaults().dither();
            case START_SCALE -> extensions.startScalePercent();
            case END_SCALE -> extensions.endScalePercent();
            case START_SPACING -> extensions.startSpacingPercent();
            case END_SPACING -> extensions.endSpacingPercent();
            case TWIST -> extensions.twistDegrees();
            case RHYTHM_INTERVAL -> extensions.rhythmInterval();
            case CONFORM_RADIUS -> extensions.conformRadius();
            case SURFACE_OFFSET -> extensions.surfaceOffset();
            case SOCKET_INTERVAL -> extensions.socketInterval();
            case WEATHER_INTENSITY, WEATHER_COLLAPSE, WEATHER_VEGETATION -> 50;
        };
    }

    void setValue(Control control, int requested) {
        int next = range(control).clamp(requested);
        // Envejecer no es parte de la receta: sus valores viven aquí y no entran en el historial.
        switch (control) {
            case WEATHER_INTENSITY -> {
                weatherIntensity = next;
                return;
            }
            case WEATHER_COLLAPSE -> {
                weatherCollapse = next;
                return;
            }
            case WEATHER_VEGETATION -> {
                weatherVegetation = next;
                return;
            }
            default -> {
            }
        }
        int radius = draft.radius();
        int spacing = draft.spacing();
        int thickness = draft.thickness();
        int roll = draft.rollDegrees();
        int height = draft.height();
        int dither = draft.dither();
        RecipeExtensions extensions = draft.extensions();
        switch (control) {
            case RADIUS -> radius = next;
            case SPACING -> {
                spacing = next;
                if (draft.options().containsKey("sides")) {
                    // El paso del Studio manda sobre los lados fijados por comando.
                    Map<String, Double> options = new LinkedHashMap<>(draft.options());
                    options.remove("sides");
                    draft = draft.withOptions(options);
                }
            }
            case THICKNESS -> thickness = next;
            case ROLL -> roll = next;
            case HEIGHT -> height = next;
            case DITHER -> dither = next;
            case START_SCALE -> extensions = extensions.withCurve(next, extensions.endScalePercent(),
                    extensions.startSpacingPercent(), extensions.endSpacingPercent(), extensions.twistDegrees(),
                    extensions.easing());
            case END_SCALE -> extensions = extensions.withCurve(extensions.startScalePercent(), next,
                    extensions.startSpacingPercent(), extensions.endSpacingPercent(), extensions.twistDegrees(),
                    extensions.easing());
            case START_SPACING -> extensions = extensions.withCurve(extensions.startScalePercent(),
                    extensions.endScalePercent(), next, extensions.endSpacingPercent(), extensions.twistDegrees(),
                    extensions.easing());
            case END_SPACING -> extensions = extensions.withCurve(extensions.startScalePercent(),
                    extensions.endScalePercent(), extensions.startSpacingPercent(), next, extensions.twistDegrees(),
                    extensions.easing());
            case TWIST -> extensions = extensions.withCurve(extensions.startScalePercent(),
                    extensions.endScalePercent(), extensions.startSpacingPercent(), extensions.endSpacingPercent(),
                    next, extensions.easing());
            case RHYTHM_INTERVAL -> extensions = extensions.withRhythm(
                    extensions.rhythmEnabled(), extensions.accentShape(), next);
            case CONFORM_RADIUS -> extensions = extensions.withSurface(
                    extensions.surfaceMode(), next, extensions.surfaceOffset());
            case SURFACE_OFFSET -> extensions = extensions.withSurface(
                    extensions.surfaceMode(), extensions.conformRadius(), next);
            case SOCKET_INTERVAL -> extensions = extensions.withSockets(
                    extensions.socketsEnabled(), extensions.socketPlacement(), next);
            case WEATHER_INTENSITY, WEATHER_COLLAPSE, WEATHER_VEGETATION -> {
            }
        }
        draft = draft.copy(draft.name(), draft.shape(), draft.pattern(), radius, spacing, thickness, roll, height,
                draft.paletteId(), draft.gradientAxis(), dither, draft.reverse()).withExtensions(extensions);
    }

    /** Clic derecho en una fila de ciclo: quita la opción para que valga la de serie. */
    private void resetOption(String option) {
        RecipeExtensions extensions = draft.extensions();
        RecipeExtensions defaults = RecipeExtensions.DEFAULT;
        switch (option) {
            case "accent" -> draft = draft.withExtensions(extensions.withRhythm(
                    extensions.rhythmEnabled(), defaults.accentShape(), extensions.rhythmInterval()));
            case "socket_placement" -> draft = draft.withExtensions(extensions.withSockets(
                    extensions.socketsEnabled(), defaults.socketPlacement(), extensions.socketInterval()));
            case "easing" -> draft = draft.withExtensions(extensions.withCurve(extensions.startScalePercent(),
                    extensions.endScalePercent(), extensions.startSpacingPercent(), extensions.endSpacingPercent(),
                    extensions.twistDegrees(), defaults.easing()));
            default -> {
                Map<String, Double> next = new LinkedHashMap<>(draft.options());
                next.remove(option);
                draft = draft.withOptions(next);
                if (option.equals("preset")) {
                    draft = draft.withFormula("");
                    formulaError = null;
                }
            }
        }
    }

    private SmoothMode smoothMode() {
        return SmoothMode.byIndex((int) Math.round(tubeOption("smooth", 0.0)));
    }

    private double coverage() {
        return Math.clamp(tubeOption("coverage", PieceFit.DEFAULT_COVERAGE), 0.0, 1.0);
    }

    private TubeEngine tubeEngine() {
        return TubeEngine.byIndex((int) Math.round(tubeOption(TubeEngine.OPTION, 0.0)));
    }

    private TubeFormulaPreset formulaPreset() {
        return TubeFormulaPreset.byIndex((int) tubeOption("preset", 0.0));
    }

    /** La fórmula que se está usando: la propia o, si no hay, la de serie. */
    private String activeFormulaSource() {
        return draft.formula().isEmpty() ? formulaPreset().source() : draft.formula();
    }

    private void updateFormulaError(StudioHost host) {
        formulaError = formulaInput.isBlank() ? null : TubeFormula.validate(formulaInput);
        host.status(formulaError == null ? host.tr("gui.komaedit.status.formula.valid") : formulaError);
    }

    /** Aplica lo escrito; vacío o igual a la de serie vuelve a la de serie. Si no compila, no cambia nada. */
    private void commitFormula(StudioHost host) {
        editingFormula = false;
        String input = formulaInput.trim();
        TubeFormulaPreset preset = formulaPreset();
        formulaError = null;
        if (input.isEmpty() || input.equals(preset.source())) {
            draft = draft.withFormula("");
            host.status(host.tr("gui.komaedit.status.formula.default", host.tr(StudioNames.formula(preset))));
            return;
        }
        String error = TubeFormula.validate(input);
        if (error != null) {
            formulaError = error;
            host.status(host.tr("gui.komaedit.status.formula.not_applied", error));
            return;
        }
        draft = draft.withFormula(input);
        host.status(host.tr("gui.komaedit.status.formula.custom"));
    }

    private TubeSettings.TubeProfile tubeProfile() {
        return TubeSettings.TubeProfile.byIndex((int) tubeOption("profile", 0.0));
    }

    private double tubeOption(String name, double fallback) {
        return draft.options().getOrDefault(name, fallback);
    }

    private void setTubeOption(String name, double value) {
        Map<String, Double> next = new LinkedHashMap<>(draft.options());
        next.put(name, value);
        draft = draft.withOptions(next);
    }

    private void applyProportions(ShapeVariants.Proportions proportions) {
        draft = draft.copy(draft.name(), draft.shape(), draft.pattern(), proportions.radius(), proportions.spacing(),
                proportions.thickness(), proportions.rollDegrees(), proportions.height(), draft.paletteId(),
                draft.gradientAxis(), draft.dither(), draft.reverse());
    }

    /** Variante con semilla nueva; la semilla queda en el estado para poder contarla. */
    public void randomVariant(StudioHost host) {
        int seed = ShapeVariants.randomSeed(seeds);
        applyProportions(ShapeVariants.variant(draft.shape(), seed));
        host.status(host.tr("gui.komaedit.variant.applied", seed));
    }

    public void selectShape(StudioHost host, PathShape shape) {
        if (draft.shape() != shape) {
            draft = draft.withShapeDefaults(shape);
            host.status(host.tr("gui.komaedit.status.shape.changed", shapeName(host, shape)));
        }
        browsing = false;
        editingSearch = false;
        shapeSearch = "";
        host.preferences().pushRecent(shape.id());
        host.savePreferences();
    }

    private ProceduralRecipe copyMaterial(String palette) {
        return draft.copy(draft.name(), draft.shape(), draft.pattern(), draft.radius(), draft.spacing(),
                draft.thickness(), draft.rollDegrees(), draft.height(), palette, draft.gradientAxis(),
                draft.dither(), draft.reverse());
    }

    private void commitName(StudioHost host) {
        if (!ProceduralRecipe.VALID_NAME.matcher(nameInput).matches()) {
            nameInput = "receta_" + host.now() % 100_000L;
            host.status(host.tr("gui.komaedit.status.name.adjusted", nameInput));
        }
        draft = draft.withName(nameInput);
    }

    // ── Teclado y ratón ─────────────────────────────────────────────────────────────────────────────────

    /** Abre el buscador de figuras. */
    public void focusSearch(StudioHost host) {
        browsing = true;
        editingSearch = true;
        host.status(host.tr("gui.komaedit.search.typing"));
    }

    /** Pulsar fuera de un campo de estas etapas: lo escrito se aplica (o se descarta si no vale). */
    public void clickedOutside(Object key, StudioHost host) {
        if (numbers.editing() && !numbers.editing(key)) {
            commitNumber(host);
        }
        if (!"search".equals(key)) {
            editingSearch = false;
        }
        if (!"name".equals(key)) {
            editingName = false;
        }
        if (editingFormula && !"formula".equals(key)) {
            commitFormula(host);
        }
    }

    /** Si el control con esta clave es el campo que se está escribiendo. */
    public boolean owns(Object key) {
        return numbers.editing(key) || editingSearch && "search".equals(key) || editingName && "name".equals(key)
                || editingFormula && "formula".equals(key);
    }

    /** Valor escrito a mano: Intro o pulsar fuera lo aplica; lo que no es un número se descarta. */
    private void commitNumber(StudioHost host) {
        Object key = numbers.editingKey();
        String typed = numbers.commit();
        Control control = control(key);
        if (control == null || typed.isBlank()) {
            return;
        }
        Integer parsed = unit(control, "").parse(typed, 0);
        if (parsed == null) {
            host.status(host.tr("gui.komaedit.number.invalid"));
            return;
        }
        setValue(control, parsed);
    }

    private static Control control(Object key) {
        if (key instanceof String text && text.startsWith("figure:")) {
            try {
                return Control.valueOf(text.substring("figure:".length()));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Arrastre de un número (Mayús: fino). */
    public void drag(double mouseX, boolean fine) {
        Control control = control(numbers.draggingKey());
        if (control == null) {
            return;
        }
        Integer next = numbers.drag(mouseX, range(control), fine);
        if (next != null) {
            setValue(control, next);
        }
    }

    /** Grupo del historial del arrastre en curso. */
    public String dragGroup() {
        Control control = control(numbers.draggingKey());
        return control == null ? null : control.name();
    }

    public void release(StudioHost host) {
        Control control = control(numbers.draggingKey());
        if (control == null) {
            return;
        }
        if (numbers.release(Integer.toString(value(control)))) {
            host.status(host.tr("gui.komaedit.number.typing"));
        }
    }

    public boolean key(StudioKey key, StudioHost host) {
        if (numbers.editing()) {
            switch (key.code()) {
                case ENTER -> commitNumber(host);
                case ESCAPE -> numbers.cancel();
                case BACKSPACE -> numbers.erase(key.control());
                default -> {
                }
            }
            return true;
        }
        if (editingFormula) {
            switch (key.code()) {
                case ENTER -> commitFormula(host);
                case ESCAPE -> {
                    editingFormula = false;
                    formulaError = null;
                    host.status(host.tr("gui.komaedit.status.formula.cancelled"));
                }
                case BACKSPACE -> {
                    if (!formulaInput.isEmpty()) {
                        formulaInput = key.control() ? "" : formulaInput.substring(0, formulaInput.length() - 1);
                        updateFormulaError(host);
                    }
                }
                default -> {
                }
            }
            return true;
        }
        if (editingName) {
            switch (key.code()) {
                case ENTER -> {
                    commitName(host);
                    editingName = false;
                }
                case BACKSPACE -> {
                    if (!nameInput.isEmpty()) {
                        nameInput = nameInput.substring(0, nameInput.length() - 1);
                    }
                }
                case ESCAPE -> {
                    nameInput = draft.name();
                    editingName = false;
                }
                default -> {
                }
            }
            return true;
        }
        if (editingSearch) {
            switch (key.code()) {
                case ESCAPE -> {
                    if (shapeSearch.isEmpty()) {
                        editingSearch = false;
                        browsing = false;
                    } else {
                        shapeSearch = "";
                    }
                }
                case ENTER -> {
                    List<Map.Entry<String, List<PathShape>>> groups = shapeGroups(host);
                    editingSearch = false;
                    groups.stream().flatMap(group -> group.getValue().stream()).findFirst()
                            .ifPresent(shape -> selectShape(host, shape));
                }
                case BACKSPACE -> {
                    if (!shapeSearch.isEmpty()) {
                        shapeSearch = key.control() ? "" : shapeSearch.substring(0, shapeSearch.length() - 1);
                    }
                }
                default -> {
                }
            }
            return true;
        }
        if (key.code() == StudioKey.Code.ESCAPE && browsing) {
            browsing = false;
            return true;
        }
        return false;
    }

    public boolean type(char character, StudioHost host) {
        if (numbers.editing()) {
            numbers.type(character);
            return true;
        }
        if (editingFormula) {
            if (character >= 32 && character < 127 && formulaInput.length() < TubeFormula.MAX_LENGTH) {
                formulaInput += character;
                updateFormulaError(host);
            }
            return true;
        }
        if (editingSearch) {
            if (character >= 32 && character != 127 && shapeSearch.length() < 32) {
                shapeSearch += character;
            }
            return true;
        }
        if (editingName) {
            if (nameInput.length() < 32 && (Character.isLetterOrDigit(character) || character == '_'
                    || character == '-')) {
                nameInput += character;
            }
            return true;
        }
        return false;
    }

    /** Pegar en la fórmula (Ctrl+V). */
    public void paste(String text, StudioHost host) {
        if (!editingFormula) {
            return;
        }
        StringBuilder next = new StringBuilder(formulaInput);
        for (int index = 0; index < text.length() && next.length() < TubeFormula.MAX_LENGTH; index++) {
            char value = text.charAt(index);
            next.append(value >= 32 && value < 127 ? value : ' ');
        }
        formulaInput = next.toString();
        updateFormulaError(host);
    }

    // ── Nombres ─────────────────────────────────────────────────────────────────────────────────────────

    public static String shapeName(StudioHost host, PathShape shape) {
        return host.tr(StudioNames.shape(shape));
    }

    public static String materialSummary(StudioHost host, ProceduralRecipe recipe) {
        if (recipe.usesGradient()) {
            return recipe.palette().displayName();
        }
        String pattern = recipe.pattern();
        return host.blockName(pattern.contains(":") ? pattern : "minecraft:" + pattern);
    }

    static PathShape shapeById(String id) {
        try {
            return PathShape.byId(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static <T> T cycle(T[] values, T current, int direction) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == current) {
                return values[Math.floorMod(index + direction, values.length)];
            }
        }
        return values[0];
    }
}
