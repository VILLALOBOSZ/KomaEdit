package com.komainu.moxia.client.gui;

import com.komainu.moxia.client.gui.studio.ColorStage;
import com.komainu.moxia.client.gui.studio.StudioIcons;
import com.komainu.moxia.client.gui.studio.StudioUi;
import com.komainu.moxia.client.gui.studio.StudioUi.Tone;
import com.komainu.moxia.client.gui.studio.StudioUi.Widget;
import com.komainu.moxia.client.mixin.GameRendererAccessor;
import com.komainu.moxia.client.preview.EditorOverlayRenderer;
import com.komainu.moxia.client.preview.PathHologramRenderer;
import com.komainu.moxia.client.preview.ProceduralPreviewRenderer;
import com.komainu.moxia.client.recipe.ClientInstanceCatalog;
import com.komainu.moxia.client.recipe.ClientRecipeCatalog;
import com.komainu.moxia.editor.EditorActionPayload;
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
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.StudioShortcuts;
import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.layout.StudioLayout;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static com.komainu.moxia.client.gui.studio.StudioTheme.*;

/**
 * Procedural Studio (tecla K). Un único panel opaco a la derecha con barra de herramientas, etapas, contenido
 * desplazable y un pie con el botón Construir; el mundo queda libre a la izquierda, con el chat a la vista.
 *
 * <p>El reparto lo calcula {@link StudioLayout} y los controles los dibuja {@link StudioUi}, que registra en el
 * mismo paso qué hace cada uno: esta clase guarda el estado del Studio y declara el contenido de cada etapa.</p>
 */
public final class ProceduralStudioScreen extends Screen {
    private static final double REACH = 4_096.0;
    private static final int MAX_PATH_POINTS = 32;
    private static final long STATUS_MILLIS = 4_000L;
    private static final List<PathShape> STRUCTURE_SHAPES = List.of(
            PathShape.PILLAR,
            PathShape.PILLAR_ARCHES,
            PathShape.ARCH_BRIDGE,
            PathShape.TORII
    );
    private static final List<PathShape> FIGURE_SHAPES = Arrays.stream(PathShape.values())
            .filter(shape -> !STRUCTURE_SHAPES.contains(shape))
            .toList();
    private static final PathShape[] MODIFIER_SHAPES = PathShape.values();

    private static final List<BlockChoice> ALL_BLOCKS = Registries.BLOCK.getIds().stream()
            .sorted(Comparator.comparing(Identifier::toString))
            .map(identifier -> {
                Block block = Registries.BLOCK.get(identifier);
                ItemStack stack = block.asItem().getDefaultStack();
                if (stack.isEmpty()) {
                    stack = Items.BARRIER.getDefaultStack();
                }
                return new BlockChoice(identifier.toString(), stack);
            })
            .toList();

    private static int rememberedInspectorWidth = StudioLayout.DEFAULT_PANEL_WIDTH;
    private static ProceduralRecipe rememberedDraft = ProceduralRecipe.defaults();
    private static volatile ProceduralRecipe previewDraft = rememberedDraft;
    private static final Path PREFERENCES_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("komaedit").resolve("studio.json");
    private static StudioPreferences preferences;
    /** Deshacer de ajustes del Studio; sobrevive a cerrar y abrir, no a salir del mundo. */
    private static final ParameterHistory<ProceduralRecipe> HISTORY = new ParameterHistory<>(100);
    private static final SplittableRandom SEEDS = new SplittableRandom();
    private static ShapeCategory rememberedCategory = ShapeCategory.ALL;
    /** Etapa de color: su degradado y sus ajustes siguen ahí al reabrir el Studio. */
    private static final ColorStage COLOR_STAGE = new ColorStage();

    private ProceduralRecipe draft = rememberedDraft;
    private int weatherIntensity = 50;
    private int weatherCollapse = 50;
    private int weatherVegetation = 50;
    private int weatherStyle;
    private boolean weatherWater;
    private Stage stage = STRUCTURE_SHAPES.contains(draft.shape()) ? Stage.STRUCTURES : Stage.FIGURE;
    private int inspectorWidth;
    private StudioLayout.Frame frame;
    private StudioUi ui;
    private int contentScroll;
    /** Alto del contenido de la etapa en el último fotograma, para limitar el desplazamiento. */
    private int contentHeight;
    private int blockScroll;
    private boolean resizingInspector;
    private boolean cameraDragging;
    private boolean draggingPoint;
    private int draggedPoint = -1;
    private int selectedPoint = -1;
    private int draggedAxis = -1;
    private BlockPos axisDragOrigin;
    private double axisDragStart;
    private boolean blockCatalog;
    /** Si el catálogo de bloques se abrió para elegir un bloque de la etapa de color, a quién se lo entrega. */
    private Consumer<String> blockPicker;
    private boolean editingName;
    private String nameInput = draft.name();
    private boolean editingFormula;
    private String formulaInput = "";
    /** Error de lo último escrito; si quedó sin aplicar, al reabrir el campo se recupera para corregirlo. */
    private String formulaError;
    private String blockSearch = "";
    private String status = "";
    /** El mensaje que el pie enseña y desde cuándo, para devolver el sitio al holograma pasado un rato. */
    private String shownStatus = "";
    private long statusSince;
    private String selectedInstanceId = "";
    private BlockPos hoverPlacement;
    private BlockPos lastDraggedPosition;
    private final BuildEstimate buildEstimate = new BuildEstimate();
    private ShapeCategory category = rememberedCategory;
    private boolean editingSearch;
    private String shapeSearch = "";
    private Control editingControl;
    private String numberInput = "";
    private Control dragControl;
    private double dragStartX;
    private int dragStartValue;
    private boolean dragMoved;
    private boolean cheatSheet;
    private boolean restoring;
    /** Dónde se pulsó por última vez: el arrastre de un número parte de ahí. */
    private double lastMouseX;

    public ProceduralStudioScreen() {
        super(Text.translatable("gui.komaedit.title"));
    }

    public static void resetSession() {
        rememberedDraft = ProceduralRecipe.defaults();
        previewDraft = rememberedDraft;
        HISTORY.clear();
    }

    private static StudioPreferences preferences() {
        if (preferences == null) {
            preferences = StudioPreferences.load(PREFERENCES_FILE);
        }
        return preferences;
    }

    private static void savePreferences() {
        try {
            preferences().save(PREFERENCES_FILE);
        } catch (IOException ignored) {
            // Favoritas y recientes son una comodidad: si no se pueden guardar, siguen en memoria.
        }
    }

    public static ProceduralRecipe previewRecipe() {
        return previewDraft;
    }

    @Override
    protected void init() {
        inspectorWidth = StudioLayout.clampPanelWidth(width, rememberedInspectorWidth);
        frame = StudioLayout.compute(width, height, inspectorWidth, Stage.values().length);
        ui = new StudioUi(textRenderer);
        status = tr("gui.komaedit.status.ready");
        sendCommand("komaedit path mode on");
        ClientPlayNetworking.send(new RecipeActionPayload(RecipeActionPayload.SYNC, draft));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        if (typing()) {
            // Mientras se escribe en un campo, W, A, S, D y el espacio son letras, no movimiento.
            releaseMovementKeys();
        } else {
            updateMovementKeys();
        }
        previewDraft = draft;
        if (client != null) {
            ProceduralPreviewRenderer.request(draft, PathHologramRenderer.points(), PathHologramRenderer.closed());
            ProceduralPreviewRenderer.tick(client);
        }
    }

    @Override
    public void removed() {
        rememberedInspectorWidth = inspectorWidth;
        rememberedDraft = draft;
        rememberedCategory = category;
        previewDraft = draft;
        releaseMovementKeys();
        EditorOverlayRenderer.clear();
        sendCommand("komaedit path mode off");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        frame = StudioLayout.compute(width, height, inspectorWidth, Stage.values().length);
        boolean modal = blockCatalog || cheatSheet;
        ui.begin(context, mouseX, mouseY, !modal);
        updateHover(mouseX, mouseY);

        ui.fill(frame.panel(), PANEL);
        drawToolbar();
        drawNavigation();
        drawStageHeader();
        drawContent();
        drawFooter();
        drawResizeHandle(mouseX, mouseY);

        if (blockCatalog) {
            drawBlockCatalog(mouseX, mouseY);
        } else if (cheatSheet) {
            drawCheatSheet();
        } else {
            drawTooltip(context, mouseX, mouseY);
        }
    }

    private boolean typing() {
        return editingFormula || editingName || editingSearch || editingControl != null || blockCatalog;
    }

    // ── Marco ───────────────────────────────────────────────────────────────────────────────────────────

    private void drawToolbar() {
        Rect bar = frame.toolbar();
        ui.fill(bar, SURFACE);
        ui.fill(new Rect(bar.x(), bar.bottom() - 1, bar.width(), 1), DIVIDER);
        ui.fill(new Rect(bar.x(), bar.y(), 3, bar.height()), ACCENT);
        int brandRight = frame.toolButton(4).x() - 6;
        ui.bold("KomaEdit", bar.x() + 11, bar.centerY() - 4, brandRight - bar.x() - 11, TEXT);

        ui.toolButton(frame.toolButton(0), StudioIcons.Icon.CLOSE, false, true)
                .onClick(this::close).tooltip(tr("gui.komaedit.help.top.close"));
        ui.toolButton(frame.toolButton(1), StudioIcons.Icon.HELP, cheatSheet, true)
                .onClick(() -> cheatSheet = !cheatSheet).tooltip(tr("gui.komaedit.help.top.help"));
        boolean hologram = !ProceduralPreviewRenderer.hidden();
        ui.toolButton(frame.toolButton(2), hologram ? StudioIcons.Icon.HOLOGRAM : StudioIcons.Icon.HOLOGRAM_OFF,
                        hologram, true)
                .onClick(this::toggleHologram).tooltip(tr("gui.komaedit.help.top.hologram"));
        ui.toolButton(frame.toolButton(3), StudioIcons.Icon.REDO, false, HISTORY.canRedo())
                .onClick(this::redoParameters).tooltip(tr("gui.komaedit.help.top.redo"));
        ui.toolButton(frame.toolButton(4), StudioIcons.Icon.UNDO, false, HISTORY.canUndo())
                .onClick(this::undoParameters).tooltip(tr("gui.komaedit.help.top.undo"));
    }

    private void drawNavigation() {
        Rect nav = frame.nav();
        ui.fill(nav, SURFACE);
        ui.fill(new Rect(nav.right() - 1, nav.y(), 1, nav.height()), DIVIDER);
        boolean labels = frame.navMode() == StudioLayout.NavMode.LABELS;
        for (Stage value : Stage.values()) {
            Rect item = frame.navItem(value.ordinal());
            boolean selected = value == stage;
            boolean hovered = ui.hovered(item);
            ui.fill(item, selected ? CARD_ACTIVE : hovered ? CARD_HOVER : SURFACE);
            if (selected) {
                ui.fill(item.left(2), ACCENT);
            }
            int iconY = item.centerY() - StudioIcons.SIZE / 2;
            int iconColor = selected ? ACCENT : hovered ? TEXT : TEXT_SECONDARY;
            String number = Integer.toString(value.ordinal() + 1);
            if (labels) {
                ui.icon(value.icon, item.x() + 6, iconY, iconColor);
                int labelX = item.x() + 6 + StudioIcons.SIZE + 5;
                ui.label(tr(value.titleKey), labelX, item.centerY() - 4, item.right() - 4 - labelX,
                        selected || hovered ? TEXT : TEXT_SECONDARY);
            } else {
                ui.icon(value.icon, item.centerX() - StudioIcons.SIZE / 2, iconY, iconColor);
            }
            ui.register(item)
                    .onClick(() -> selectStage(value))
                    .tooltip(number + " · " + tr(value.titleKey) + "\n" + tr(value.subtitleKey));
        }
    }

    private void drawStageHeader() {
        Rect header = frame.header();
        int x = frame.innerLeft();
        int textWidth = frame.innerWidth();
        ui.bold(tr(stage.titleKey), x, header.y() + 9, textWidth, TEXT);
        ui.label(tr(stage.subtitleKey), x, header.y() + 21, textWidth, MUTED);
        ui.fill(new Rect(header.x(), header.bottom() - 1, header.width(), 1), DIVIDER);
    }

    private void drawContent() {
        Rect viewport = frame.viewport();
        int maximumScroll = Math.max(0, contentHeight - viewport.height());
        contentScroll = Math.clamp(contentScroll, 0, maximumScroll);
        ui.pushClip(viewport);
        Column column = new Column(frame.innerLeft(), viewport.y() + 8 - contentScroll, frame.innerWidth());
        switch (stage) {
            case PATH -> drawPathStage(column);
            case FIGURE -> drawShapeStage(column, FIGURE_SHAPES, true);
            case STRUCTURES -> drawShapeStage(column, STRUCTURE_SHAPES, false);
            case MODIFIERS -> drawModifiersStage(column);
            case MATERIAL -> drawMaterialStage(column);
            case WEATHER -> drawWeatherStage(column);
            case INSTANCES -> drawInstancesStage(column);
            case RECIPES -> drawRecipesStage(column);
            case COLOR -> COLOR_STAGE.draw(ui, column, colorHost());
        }
        contentHeight = column.height() + 12;
        ui.popClip();
        if (maximumScroll > 0) {
            // Barra fina a la derecha: cuánto queda por ver y dónde se está.
            int track = viewport.height() - 8;
            int thumb = Math.max(16, track * viewport.height() / contentHeight);
            int thumbY = viewport.y() + 4 + (track - thumb) * contentScroll / maximumScroll;
            ui.fill(new Rect(viewport.right() - 4, viewport.y() + 4, 2, track), DIVIDER);
            ui.fill(new Rect(viewport.right() - 4, thumbY, 2, thumb), ACCENT_DIM);
        }
    }

    private void drawFooter() {
        Rect footer = frame.footer();
        ui.fill(footer, SURFACE);
        ui.fill(new Rect(footer.x(), footer.y(), footer.width(), 1), DIVIDER);

        Rect summary = frame.footerSummary();
        String estimate = estimateLabel();
        int estimateWidth = estimate.isEmpty() ? 0 : ui.width(estimate) + 8;
        ui.label(shapeName(draft.shape()) + " · " + materialSummary(draft), summary.x(), summary.y(),
                summary.width() - estimateWidth, TEXT_SECONDARY);
        if (!estimate.isEmpty()) {
            ui.rightAligned(estimate, summary.right(), summary.y(), estimateColor());
        }
        Rect statusLine = frame.footerStatus();
        boolean armed = buildEstimate.armed(System.currentTimeMillis());
        String detail = footerDetail();
        ui.label(detail, statusLine.x(), statusLine.y(), statusLine.width(), armed ? WARNING : MUTED);
        if (ui.textRenderer().getWidth(detail) > statusLine.width()) {
            ui.register(statusLine).tooltip(detail);
        }

        Tone tone = !pathReady() ? Tone.DISABLED : armed ? Tone.WARNING : Tone.PRIMARY;
        String label = !pathReady()
                ? tr("gui.komaedit.build.missing", requiredPoints() - PathHologramRenderer.points().size())
                : armed ? tr("gui.komaedit.build_confirm") : tr("gui.komaedit.build");
        ui.button(frame.buildButton(), label, tone)
                .onClick(this::performBuild)
                .tooltip(tr("gui.komaedit.help.build"));
    }

    private void drawResizeHandle(int mouseX, int mouseY) {
        Rect handle = frame.resizeHandle();
        boolean active = resizingInspector || (!blockCatalog && !cheatSheet && handle.contains(mouseX, mouseY));
        ui.fill(new Rect(frame.panel().x(), 0, 1, height), active ? ACCENT : DIVIDER);
        if (active) {
            ui.fill(new Rect(frame.panel().x() - 1, 0, 2, height), ACCENT);
        }
    }

    /**
     * Lo último que pasó: el aviso de obra grande, un mensaje reciente o, pasados unos segundos, el estado del
     * holograma. Así un aviso («Receta guardada») no lo tapa enseguida el recuento del holograma.
     */
    private String footerDetail() {
        long now = System.currentTimeMillis();
        if (buildEstimate.armed(now)) {
            return tr("gui.komaedit.build_large", BuildEstimate.compact(ProceduralPreviewRenderer.blockCount()));
        }
        if (!status.equals(shownStatus)) {
            shownStatus = status;
            statusSince = now;
        }
        if (!pathReady() || now - statusSince < STATUS_MILLIS) {
            return status;
        }
        if (ProceduralPreviewRenderer.hidden()) {
            return tr("gui.komaedit.hologram.hidden");
        }
        return ProceduralPreviewRenderer.status();
    }

    /** «≈ 12,3 k bloques» del último holograma; vacío si aún no hay. */
    private String estimateLabel() {
        int blocks = ProceduralPreviewRenderer.blockCount();
        if (blocks < 0 || !pathReady()) {
            return "";
        }
        String count = tr("gui.komaedit.estimate", BuildEstimate.compact(blocks));
        return ProceduralPreviewRenderer.settled() ? count : count + " …";
    }

    private int estimateColor() {
        return switch (BuildEstimate.level(ProceduralPreviewRenderer.blockCount())) {
            case LARGE -> WARNING;
            case OVER_DEFAULT_LIMIT -> DANGER;
            default -> MUTED;
        };
    }

    // ── Etapas ──────────────────────────────────────────────────────────────────────────────────────────

    private void drawPathStage(Column column) {
        List<BlockPos> points = PathHologramRenderer.points();
        int nodes = points.size();
        int minimum = requiredPoints();
        boolean closed = PathHologramRenderer.closed();

        Rect card = column.next(34);
        ui.card(card);
        ui.bold(tr("gui.komaedit.path.count", nodes, MAX_PATH_POINTS), card.x() + 8, card.y() + 6,
                card.width() - 16, TEXT);
        String shape = tr(closed ? "gui.komaedit.path.closed" : "gui.komaedit.path.open");
        ui.rightAligned(shape, card.right() - 8, card.y() + 6, TEXT_SECONDARY);
        ui.label(nodes >= minimum ? tr("gui.komaedit.path.ready") : tr("gui.komaedit.path.minimum", minimum),
                card.x() + 8, card.y() + 20, card.width() - 16, nodes >= minimum ? SUCCESS : WARNING);

        ui.section(column, tr("gui.komaedit.actions"));
        Rect[] first = column.row(2, StudioUi.ROW);
        ui.button(first[0], tr("gui.komaedit.path.add_here"), Tone.SECONDARY)
                .onClick(() -> sendCommand("komaedit path here"))
                .tooltip(tr("gui.komaedit.help.path.add_here"));
        ui.button(first[1], tr("gui.komaedit.path.undo_point"), nodes == 0 ? Tone.DISABLED : Tone.SECONDARY)
                .onClick(() -> sendCommand("komaedit path undo"));
        Rect[] second = column.row(2, StudioUi.ROW);
        ui.button(second[0], tr(closed ? "gui.komaedit.path.open_path" : "gui.komaedit.path.close_path"),
                        Tone.SECONDARY)
                .onClick(() -> sendCommand("komaedit path close"))
                .tooltip(tr("gui.komaedit.help.path.toggle_closed"));
        ui.button(second[1], tr("gui.komaedit.clear"), nodes == 0 ? Tone.DISABLED : Tone.DANGER)
                .onClick(() -> {
                    sendCommand("komaedit path clear");
                    status = tr("gui.komaedit.path.cleared");
                })
                .tooltip(tr("gui.komaedit.help.path.clear"));

        if (nodes > 0) {
            ui.section(column, tr("gui.komaedit.path.nodes"));
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
                        selected ? TEXT : TEXT_SECONDARY);
                ui.register(row).onClick(() -> {
                    selectedPoint = point == selectedPoint ? -1 : point;
                    status = selectedPoint < 0 ? tr("gui.komaedit.status.ready")
                            : tr("gui.komaedit.status.node.selected", point + 1);
                });
            }
        }

        ui.section(column, tr("gui.komaedit.path.how"));
        ui.paragraph(column, tr("gui.komaedit.path.how.click"), TEXT_SECONDARY, 0);
        ui.paragraph(column, tr("gui.komaedit.path.how.move"), TEXT_SECONDARY, 0);
        ui.paragraph(column, tr("gui.komaedit.path.how.camera"), TEXT_SECONDARY, 0);
        ui.paragraph(column, tr("gui.komaedit.path.how.reach", (int) REACH), MUTED, 0);
    }

    private void drawShapeStage(Column column, List<PathShape> shapes, boolean browser) {
        List<PathShape> shown = shapes;
        if (browser) {
            shown = visibleShapes(shapes);
            drawShapeBrowser(column, shapes, shown.size());
        }
        int columns = column.width() >= 300 ? 3 : 2;
        List<Rect> cells = column.grid(Math.max(1, shown.size()), columns, StudioUi.SMALL_ROW);
        if (shown.isEmpty()) {
            Rect empty = new Rect(column.x(), cells.getFirst().y(), column.width(), StudioUi.SMALL_ROW);
            ui.card(empty);
            ui.label(tr("gui.komaedit.search.empty"), empty.x() + 8, empty.y() + 4, empty.width() - 16, MUTED);
        }
        for (int index = 0; index < shown.size(); index++) {
            drawShapeButton(cells.get(index), shown.get(index));
        }

        Rect title = column.next(10);
        ui.label(shapeName(draft.shape()), title.x(), title.y() + 1, title.width(), ACCENT_HOVER);
        ui.paragraph(column, tr(StudioNames.shapeDescription(draft.shape())), MUTED, 3);

        ui.section(column, tr("gui.komaedit.proportions"));
        drawPresets(column);
        PathShape shape = draft.shape();
        stepper(column, StudioNames.radiusLabel(shape), Control.RADIUS);
        stepper(column, StudioNames.spacingLabel(shape), Control.SPACING);
        stepper(column, StudioNames.thicknessLabel(shape), Control.THICKNESS);
        stepper(column, StudioNames.rollLabel(shape), Control.ROLL);
        if (shape.usesSecondaryMeasure()) {
            stepper(column, StudioNames.heightLabel(shape), Control.HEIGHT);
        }
        if (TubeFigures.supports(shape) || TubeFigures.hasExactEngine(shape)) {
            ui.section(column, tr("gui.komaedit.tube"));
            drawTubeRows(column);
        }
    }

    /** Buscador, familias y recientes de la etapa Figuras. */
    private void drawShapeBrowser(Column column, List<PathShape> shapes, int results) {
        Rect search = column.next(18);
        Rect clear = shapeSearch.isEmpty() ? Rect.EMPTY : search.rightPart(16);
        ui.field(search, shapeSearch, tr("gui.komaedit.search.placeholder"), editingSearch, false)
                .key("search")
                .onClick(() -> {
                    editingSearch = true;
                    status = tr("gui.komaedit.search.typing");
                });
        if (!shapeSearch.isEmpty()) {
            String count = Integer.toString(results);
            ui.rightAligned(count, clear.x() - 4, search.centerY() - 4, MUTED);
            ui.centered("×", clear, TEXT);
            ui.register(clear).onClick(() -> {
                shapeSearch = "";
                editingSearch = false;
            });
        }

        ShapeCategory[] values = ShapeCategory.values();
        int[] widths = new int[values.length];
        String[] labels = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            labels[index] = (values[index] == ShapeCategory.FAVORITES ? "★ " : "") + tr(values[index].translationKey());
            widths[index] = ui.chipWidth(labels[index]);
        }
        List<Rect> chips = column.flow(widths, 14);
        for (int index = 0; index < values.length; index++) {
            ShapeCategory value = values[index];
            ui.chip(chips.get(index), labels[index], value == category && shapeSearch.isBlank(),
                            value == ShapeCategory.FAVORITES ? FAVORITE : TEXT_SECONDARY)
                    .onClick(() -> {
                        category = value;
                        shapeSearch = "";
                    });
        }

        if (!shapeSearch.isBlank() || category != ShapeCategory.ALL) {
            return;
        }
        List<PathShape> recents = new ArrayList<>();
        for (String id : preferences().recents()) {
            PathShape shape = shapeById(id);
            if (shape != null && shapes.contains(shape) && recents.size() < 4) {
                recents.add(shape);
            }
        }
        if (recents.isEmpty()) {
            return;
        }
        String recentLabel = tr("gui.komaedit.recents");
        int labelWidth = ui.width(recentLabel) + 6;
        int[] recentWidths = new int[recents.size()];
        for (int index = 0; index < recents.size(); index++) {
            recentWidths[index] = Math.min(column.width() / 2, ui.chipWidth(shapeName(recents.get(index))));
        }
        List<Rect> placed = Column.flow(recentWidths, 14, column.x() + labelWidth, column.y(),
                column.width() - labelWidth);
        ui.label(recentLabel, column.x(), column.y() + 3, MUTED);
        int bottom = column.y();
        for (int index = 0; index < recents.size(); index++) {
            PathShape shape = recents.get(index);
            Rect chip = placed.get(index);
            ui.chip(chip, shapeName(shape), shape == draft.shape(), TEXT_SECONDARY)
                    .onClick(() -> selectShape(shape));
            bottom = Math.max(bottom, chip.bottom());
        }
        column.space(bottom - column.y() + Column.GAP);
    }

    private void drawShapeButton(Rect cell, PathShape shape) {
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
        ui.label(shapeName(shape), cell.x() + 7, cell.centerY() - 4, cell.width() - 24, selected ? TEXT : TEXT_SECONDARY);
        boolean favorite = preferences().isFavorite(shape.id());
        if (favorite || hovered) {
            ui.label(favorite ? "★" : "☆", star.x() + 2, cell.centerY() - 4,
                    favorite || ui.hovered(star) ? FAVORITE : MUTED);
        }
        String help = shapeName(shape) + "\n" + tr(StudioNames.shapeDescription(shape)) + "\n"
                + tr("gui.komaedit.help.favorite_star");
        ui.register(cell).onClick(() -> selectShape(shape)).tooltip(help);
        ui.register(star).onClick(() -> {
            boolean added = preferences().toggleFavorite(shape.id());
            savePreferences();
            status = tr(added ? "gui.komaedit.favorite.added" : "gui.komaedit.favorite.removed", shapeName(shape));
        }).tooltip(help);
    }

    /** Presets y variante al azar, con su ancho natural para que ningún nombre se corte. */
    private void drawPresets(Column column) {
        ShapeVariants.Preset[] presets = ShapeVariants.Preset.values();
        String[] labels = new String[presets.length + 1];
        int[] widths = new int[labels.length];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = index < presets.length
                    ? tr(presets[index].translationKey()) : tr("gui.komaedit.preset.random");
            widths[index] = ui.width(labels[index]) + 16;
        }
        List<Rect> cells = column.flow(widths, 16);
        for (int index = 0; index < presets.length; index++) {
            ShapeVariants.Preset preset = presets[index];
            ui.button(cells.get(index), labels[index], Tone.SECONDARY)
                    .onClick(() -> {
                        applyProportions(ShapeVariants.preset(draft.shape(), preset));
                        status = tr("gui.komaedit.preset.applied", tr(preset.translationKey()));
                    })
                    .tooltip(tr(preset.translationKey() + ".help"))
                    .group("preset");
        }
        ui.button(cells.get(presets.length), labels[presets.length], Tone.SECONDARY)
                .onClick(this::randomVariant)
                .tooltip(tr("gui.komaedit.preset.random.help"))
                .group("preset");
    }

    /**
     * Filas del espacio de tubo: motor (en las figuras clásicas que tienen versión exacta), perfil o fórmula,
     * relleno, extremos, orientación de la sección y piezas.
     */
    private void drawTubeRows(Column column) {
        PathShape shape = draft.shape();
        if (TubeFigures.hasExactEngine(shape)) {
            cycle(column, "gui.komaedit.tube.engine", tr(StudioNames.engine(tubeEngine())), TubeEngine.OPTION,
                    direction -> {
                        TubeEngine engine = TubeEngine.byIndex(tubeEngine().ordinal() + direction);
                        Map<String, Double> next = new LinkedHashMap<>(draft.options());
                        if (engine == TubeEngine.EXACT) {
                            next.put(TubeEngine.OPTION, (double) engine.ordinal());
                        } else {
                            next.remove(TubeEngine.OPTION);
                        }
                        draft = draft.withOptions(next);
                        status = tr(engine == TubeEngine.EXACT
                                ? "gui.komaedit.status.engine.exact" : "gui.komaedit.status.engine.classic");
                    });
            if (tubeEngine() != TubeEngine.EXACT) {
                return;
            }
        }
        if (shape == PathShape.EXPRESSION) {
            String formulaName = draft.formula().isEmpty()
                    ? tr(StudioNames.formula(formulaPreset())) : tr("gui.komaedit.tube.formula.custom");
            cycle(column, "gui.komaedit.tube.formula", formulaName, "preset", direction -> {
                // Desde una fórmula propia, el primer paso vuelve a la de serie que había debajo.
                TubeFormulaPreset preset = draft.formula().isEmpty()
                        ? TubeFormulaPreset.byIndex(formulaPreset().ordinal() + direction)
                        : formulaPreset();
                setTubeOption("preset", preset.ordinal());
                draft = draft.withFormula("");
                formulaError = null;
                status = tr("gui.komaedit.status.formula.preset", tr(StudioNames.formula(preset)),
                        tr(StudioNames.formulaDescription(preset)));
            });
            drawFormulaField(column);
        }
        if (shape == PathShape.SWEEP) {
            cycle(column, "gui.komaedit.tube.profile", tr(StudioNames.profile(tubeProfile())), "profile",
                    direction -> {
                        TubeSettings.TubeProfile profile = TubeSettings.TubeProfile.byIndex(
                                tubeProfile().ordinal() + direction);
                        setTubeOption("profile", profile.ordinal());
                        status = tr(profile.usesSides() ? "gui.komaedit.status.profile.sides"
                                : "gui.komaedit.status.profile", tr(StudioNames.profile(profile)));
                    });
        }
        if (shape == PathShape.SWEEP || shape == PathShape.EXPRESSION) {
            boolean hollow = tubeOption("hollow", 0.0) >= 0.5;
            cycle(column, "gui.komaedit.tube.fill", tr(hollow ? "gui.komaedit.tube.fill.hollow"
                    : "gui.komaedit.tube.fill.solid"), "hollow", direction -> {
                boolean next = tubeOption("hollow", 0.0) < 0.5;
                setTubeOption("hollow", next ? 1.0 : 0.0);
                status = tr(next ? "gui.komaedit.status.hollow.on" : "gui.komaedit.status.hollow.off");
            });
        }
        TubeSettings.EndMode end = TubeSettings.EndMode.byIndex((int) tubeOption("end", 0.0));
        cycle(column, "gui.komaedit.tube.ends", tr(StudioNames.end(end)), "end", direction -> {
            TubeSettings.EndMode mode = TubeSettings.EndMode.byIndex((int) tubeOption("end", 0.0) + direction);
            setTubeOption("end", mode.ordinal());
            status = tr("gui.komaedit.status.end", tr(StudioNames.end(mode)));
        });
        TubeSettings.NormalMode normal = TubeSettings.NormalMode.byIndex((int) tubeOption("normal", 0.0));
        cycle(column, "gui.komaedit.tube.normals", tr(StudioNames.normal(normal)), "normal", direction -> {
            TubeSettings.NormalMode mode = TubeSettings.NormalMode.byIndex(
                    (int) tubeOption("normal", 0.0) + direction);
            setTubeOption("normal", mode.ordinal());
            status = tr("gui.komaedit.status.normal." + mode.id());
        });
        cycle(column, "gui.komaedit.tube.pieces", tr(StudioNames.smooth(smoothMode())), "smooth", direction -> {
            SmoothMode mode = SmoothMode.byIndex(smoothMode().ordinal() + direction);
            setTubeOption("smooth", mode.ordinal());
            status = tr("gui.komaedit.status.smooth." + mode.id());
        });
        if (smoothMode() != SmoothMode.NONE) {
            cycle(column, "gui.komaedit.tube.coverage", Math.round(coverage() * 100.0) + "%", "coverage",
                    direction -> {
                        double next = Math.clamp(Math.round(coverage() * 10.0 + direction) / 10.0, 0.0, 1.0);
                        setTubeOption("coverage", next);
                        status = next <= 0.0 ? tr("gui.komaedit.status.coverage.none")
                                : next >= 1.0 ? tr("gui.komaedit.status.coverage.full")
                                : tr("gui.komaedit.status.coverage.partial", Math.round(next * 100.0));
                    });
        }
    }

    /** Campo de fórmula: muestra la activa (gris si es de serie) y, al editar, el final de lo escrito. */
    private void drawFormulaField(Column column) {
        Rect field = column.next(18);
        String shown = editingFormula ? formulaInput : draft.formula();
        ui.field(field, shown, activeFormulaSource(), editingFormula, formulaError != null)
                .key("formula")
                .tooltip(tr("gui.komaedit.help.formula"))
                .onClick(() -> {
                    if (editingFormula) {
                        return;
                    }
                    editingFormula = true;
                    if (formulaError == null) {
                        formulaInput = activeFormulaSource();
                    }
                    status = formulaError == null ? tr("gui.komaedit.status.formula.typing") : formulaError;
                });
    }

    private void drawModifiersStage(Column column) {
        RecipeExtensions extensions = draft.extensions();
        ui.paragraph(column, tr("gui.komaedit.modifiers.intro"), TEXT_SECONDARY, 0);
        ui.button(column.next(16), tr("gui.komaedit.modifiers.reset"), Tone.SECONDARY).onClick(() -> {
            draft = draft.withExtensions(RecipeExtensions.DEFAULT);
            status = tr("gui.komaedit.status.modifiers.reset");
        });

        ui.section(column, tr("gui.komaedit.modifiers.curve"));
        stepper(column, "gui.komaedit.param.start_scale", Control.START_SCALE);
        stepper(column, "gui.komaedit.param.end_scale", Control.END_SCALE);
        stepper(column, "gui.komaedit.param.start_spacing", Control.START_SPACING);
        stepper(column, "gui.komaedit.param.end_spacing", Control.END_SPACING);
        stepper(column, "gui.komaedit.param.twist", Control.TWIST);
        cycle(column, "gui.komaedit.param.easing", tr(StudioNames.easing(extensions.easing())), "easing",
                direction -> {
                    RecipeExtensions current = draft.extensions();
                    CurveEasing easing = cycle(CurveEasing.values(), current.easing(), direction);
                    draft = draft.withExtensions(current.withCurve(current.startScalePercent(),
                            current.endScalePercent(), current.startSpacingPercent(), current.endSpacingPercent(),
                            current.twistDegrees(), easing));
                    status = tr("gui.komaedit.status.easing", tr(StudioNames.easing(easing)));
                });

        ui.section(column, tr("gui.komaedit.modifiers.fusion"));
        ui.toggle(column.next(), tr("gui.komaedit.modifiers.fusion.enabled"), extensions.rhythmEnabled())
                .tooltip(tr("gui.komaedit.help.fusion"))
                .onClick(() -> {
                    RecipeExtensions current = draft.extensions();
                    boolean enabled = !current.rhythmEnabled();
                    draft = draft.withExtensions(current.withRhythm(enabled, current.accentShape(),
                            current.rhythmInterval()));
                    status = tr(enabled ? "gui.komaedit.status.fusion.on" : "gui.komaedit.status.fusion.off");
                });
        if (extensions.rhythmEnabled()) {
            cycle(column, "gui.komaedit.param.accent", shapeName(extensions.accentShape()), "accent", direction -> {
                RecipeExtensions current = draft.extensions();
                draft = draft.withExtensions(current.withRhythm(current.rhythmEnabled(),
                        cycle(MODIFIER_SHAPES, current.accentShape(), direction), current.rhythmInterval()));
            });
            stepper(column, "gui.komaedit.param.rhythm_interval", Control.RHYTHM_INTERVAL);
        }

        ui.section(column, tr("gui.komaedit.modifiers.surface"));
        SurfaceMode[] modes = SurfaceMode.values();
        List<Rect> modeCells = column.grid(modes.length, 2, 16);
        for (int index = 0; index < modes.length; index++) {
            SurfaceMode mode = modes[index];
            ui.button(modeCells.get(index), tr(StudioNames.surface(mode)),
                            extensions.surfaceMode() == mode ? Tone.SELECTED : Tone.SECONDARY)
                    .group("surface")
                    .onClick(() -> {
                        RecipeExtensions current = draft.extensions();
                        draft = draft.withExtensions(current.withSurface(mode, current.conformRadius(),
                                current.surfaceOffset()));
                        status = tr("gui.komaedit.status.surface", tr(StudioNames.surface(mode)));
                    });
        }
        if (extensions.surfaceMode() != SurfaceMode.NONE) {
            stepper(column, "gui.komaedit.param.conform_radius", Control.CONFORM_RADIUS);
            stepper(column, "gui.komaedit.param.surface_offset", Control.SURFACE_OFFSET);
        }

        ui.section(column, tr("gui.komaedit.modifiers.kitbash"));
        ui.toggle(column.next(), tr("gui.komaedit.modifiers.kitbash.enabled"), extensions.socketsEnabled())
                .tooltip(tr("gui.komaedit.help.kitbash"))
                .onClick(() -> {
                    RecipeExtensions current = draft.extensions();
                    boolean enabled = !current.socketsEnabled();
                    draft = draft.withExtensions(current.withSockets(enabled, current.socketPlacement(),
                            current.socketInterval()));
                    status = tr(enabled ? "gui.komaedit.status.kitbash.on" : "gui.komaedit.status.kitbash.off");
                });
        if (extensions.socketsEnabled()) {
            cycle(column, "gui.komaedit.param.socket_placement", tr(StudioNames.socket(extensions.socketPlacement())),
                    "socket_placement", direction -> {
                        RecipeExtensions current = draft.extensions();
                        draft = draft.withExtensions(current.withSockets(current.socketsEnabled(),
                                cycle(SocketPlacement.values(), current.socketPlacement(), direction),
                                current.socketInterval()));
                    });
            stepper(column, "gui.komaedit.param.socket_interval", Control.SOCKET_INTERVAL);
        }

        ui.section(column, tr("gui.komaedit.modifiers.cleanup"));
        ui.toggle(column.next(), tr("gui.komaedit.modifiers.close_gaps"), extensions.closeMicroGaps())
                .tooltip(tr("gui.komaedit.help.close_gaps"))
                .onClick(() -> {
                    RecipeExtensions current = draft.extensions();
                    draft = draft.withExtensions(current.withQa(!current.closeMicroGaps(), current.removeIsolated()));
                });
        ui.toggle(column.next(), tr("gui.komaedit.modifiers.remove_isolated"), extensions.removeIsolated())
                .tooltip(tr("gui.komaedit.help.remove_isolated"))
                .onClick(() -> {
                    RecipeExtensions current = draft.extensions();
                    draft = draft.withExtensions(current.withQa(current.closeMicroGaps(), !current.removeIsolated()));
                });
    }

    private void drawMaterialStage(Column column) {
        Rect[] mode = column.row(2, StudioUi.ROW);
        ui.button(mode[0], tr("gui.komaedit.material.block"), draft.usesGradient() ? Tone.SECONDARY : Tone.SELECTED)
                .onClick(() -> draft = copyMaterial(""));
        ui.button(mode[1], tr("gui.komaedit.material.gradient"),
                        draft.usesGradient() ? Tone.SELECTED : Tone.SECONDARY)
                .onClick(() -> draft = copyMaterial(draft.usesGradient()
                        ? draft.paletteId() : GradientPalette.GLOW_CYAN.id()));

        if (!draft.usesGradient()) {
            ui.section(column, tr("gui.komaedit.material.base_block"));
            Rect card = column.next(30);
            boolean hovered = ui.hovered(card);
            ui.fill(card, hovered ? CARD_HOVER : CARD);
            ui.item(stack(draft.pattern()), card.x() + 7, card.y() + 7);
            ui.label(compactId(draft.pattern()), card.x() + 30, card.y() + 6, card.width() - 40, TEXT);
            ui.label(tr("gui.komaedit.material.change"), card.x() + 30, card.y() + 17, card.width() - 40,
                    hovered ? ACCENT_HOVER : MUTED);
            ui.register(card).onClick(this::openBlockCatalog);
            ui.paragraph(column, tr("gui.komaedit.material.library_hint"), MUTED, 0);
            return;
        }

        ui.section(column, tr("gui.komaedit.material.direction"));
        GradientAxis[] axes = GradientAxis.values();
        Rect[] axisCells = column.row(axes.length, 16);
        for (int index = 0; index < axes.length; index++) {
            GradientAxis axis = axes[index];
            ui.button(axisCells[index], axis.id().toUpperCase(Locale.ROOT),
                            draft.gradientAxis() == axis ? Tone.SELECTED : Tone.SECONDARY)
                    .onClick(() -> draft = draft.copy(draft.name(), draft.shape(), draft.pattern(), draft.radius(),
                            draft.spacing(), draft.thickness(), draft.rollDegrees(), draft.height(), draft.paletteId(),
                            axis, draft.dither(), draft.reverse()));
        }
        stepper(column, "gui.komaedit.param.dither", Control.DITHER);
        ui.toggle(column.next(), tr("gui.komaedit.material.reverse"), draft.reverse())
                .onClick(() -> draft = draft.copy(draft.name(), draft.shape(), draft.pattern(), draft.radius(),
                        draft.spacing(), draft.thickness(), draft.rollDegrees(), draft.height(), draft.paletteId(),
                        draft.gradientAxis(), draft.dither(), !draft.reverse()));

        ui.section(column, tr("gui.komaedit.material.distribution"));
        MaterialMetric[] metrics = MaterialMetric.values();
        List<Rect> metricCells = column.grid(metrics.length, 2, 16);
        MaterialMetric current = draft.extensions().materialMetric();
        for (int index = 0; index < metrics.length; index++) {
            MaterialMetric metric = metrics[index];
            ui.button(metricCells.get(index), tr(StudioNames.metric(metric)),
                            current == metric ? Tone.SELECTED : Tone.SECONDARY)
                    .tooltip(tr(StudioNames.metricHelp(metric)))
                    .onClick(() -> draft = draft.withExtensions(draft.extensions().withMaterialMetric(metric)));
        }
        ui.paragraph(column, tr(StudioNames.metricHelp(current)), MUTED, 0);

        ui.section(column, tr("gui.komaedit.material.palette"));
        GradientPalette[] palettes = GradientPalette.values();
        List<Rect> paletteCells = column.grid(palettes.length, 2, 16);
        for (int index = 0; index < palettes.length; index++) {
            GradientPalette palette = palettes[index];
            if (!ui.visible(paletteCells.get(index))) {
                continue;
            }
            ui.button(paletteCells.get(index), palette.displayName(),
                            palette.id().equals(draft.paletteId()) ? Tone.SELECTED : Tone.SECONDARY)
                    .onClick(() -> draft = copyMaterial(palette.id()));
        }
    }

    /** Envejecer la selección de WorldEdit: clima, intensidad, derrumbe, vida y charcos; lanza el comando. */
    private void drawWeatherStage(Column column) {
        ui.section(column, tr("gui.komaedit.weather.climate"));
        List<String> styles = StudioNames.WEATHER_STYLES;
        List<Rect> cells = column.grid(styles.size(), 2, 18);
        for (int index = 0; index < styles.size(); index++) {
            int style = index;
            ui.button(cells.get(index), tr(StudioNames.weather(styles.get(index))),
                            weatherStyle == index ? Tone.SELECTED : Tone.SECONDARY)
                    .onClick(() -> weatherStyle = style);
        }
        ui.paragraph(column, tr(StudioNames.weatherHint(styles.get(weatherStyle))), MUTED, 0);

        ui.section(column, tr("gui.komaedit.weather.wear"));
        stepper(column, "gui.komaedit.param.weather_intensity", Control.WEATHER_INTENSITY);
        stepper(column, "gui.komaedit.param.weather_collapse", Control.WEATHER_COLLAPSE);
        stepper(column, "gui.komaedit.param.weather_vegetation", Control.WEATHER_VEGETATION);
        ui.toggle(column.next(), tr("gui.komaedit.weather.water"), weatherWater)
                .onClick(() -> weatherWater = !weatherWater);

        ui.section(column, tr("gui.komaedit.weather.apply"));
        ui.paragraph(column, tr("gui.komaedit.weather.selection_hint"), TEXT_SECONDARY, 0);
        ui.button(column.next(), tr("gui.komaedit.weather.run"), Tone.PRIMARY).onClick(() -> {
            sendCommand("komaedit weather " + weatherIntensity + " " + styles.get(weatherStyle)
                    + " -d " + weatherCollapse + " -v " + weatherVegetation + (weatherWater ? " -agua" : ""));
            status = tr("gui.komaedit.status.weather.running");
        });
    }

    private void drawInstancesStage(Column column) {
        if (!selectedInstanceId.isEmpty()) {
            Rect[] actions = column.row(2, StudioUi.ROW);
            ui.button(actions[0], tr("gui.komaedit.instance.update"), Tone.PRIMARY)
                    .tooltip(tr("gui.komaedit.help.instance.update"))
                    .onClick(() -> {
                        commitName();
                        ClientPlayNetworking.send(new RecipeActionPayload(
                                RecipeActionPayload.UPDATE_INSTANCE, draft, selectedInstanceId));
                        status = tr("gui.komaedit.status.instance.updated");
                    });
            ui.button(actions[1], tr("gui.komaedit.instance.delete"), Tone.DANGER).onClick(() -> {
                ClientPlayNetworking.send(new RecipeActionPayload(
                        RecipeActionPayload.DELETE_INSTANCE, draft, selectedInstanceId));
                selectedInstanceId = "";
                status = tr("gui.komaedit.status.instance.deleted");
            });
        }
        List<ProceduralInstanceSummary> instances = ClientInstanceCatalog.instances();
        ui.section(column, tr("gui.komaedit.instance.list", instances.size()));
        if (instances.isEmpty()) {
            ui.paragraph(column, tr("gui.komaedit.instance.empty"), MUTED, 0);
            return;
        }
        for (ProceduralInstanceSummary instance : instances) {
            Rect row = column.next(28);
            if (!ui.visible(row)) {
                continue;
            }
            String subtitle = tr("gui.komaedit.instance.summary", shapeName(instance.recipe().shape()),
                    instance.blockCount(), instance.pathPoints());
            ui.listItem(row, instance.recipe().name(), subtitle, instance.id().equals(selectedInstanceId), 0)
                    .onClick(() -> {
                        selectedInstanceId = instance.id();
                        draft = instance.recipe();
                        nameInput = draft.name();
                        ClientPlayNetworking.send(new RecipeActionPayload(
                                RecipeActionPayload.LOAD_INSTANCE, draft, selectedInstanceId));
                        status = tr("gui.komaedit.status.instance.selected");
                    });
        }
    }

    private void drawRecipesStage(Column column) {
        ui.section(column, tr("gui.komaedit.recipe.current"));
        Rect row = column.next(18);
        Rect save = row.rightPart(70);
        Rect name = row.withoutRight(70, Column.GAP);
        ui.field(name, nameInput, tr("gui.komaedit.recipe.name"), editingName, false)
                .key("name")
                .tooltip(tr("gui.komaedit.help.recipe.name"))
                .onClick(() -> editingName = true);
        ui.button(save, tr("gui.komaedit.save"), Tone.PRIMARY).onClick(this::saveRecipe)
                .tooltip(tr("gui.komaedit.shortcut.save.keys"));
        Rect[] packages = column.row(2, 16);
        ui.button(packages[0], tr("gui.komaedit.recipe.export"), Tone.SECONDARY)
                .tooltip(tr("gui.komaedit.help.recipe.export"))
                .onClick(() -> {
                    commitName();
                    ClientPlayNetworking.send(new RecipeActionPayload(RecipeActionPayload.EXPORT_PACKAGE, draft));
                    status = tr("gui.komaedit.status.recipe.export");
                });
        ui.button(packages[1], tr("gui.komaedit.recipe.import"), Tone.SECONDARY)
                .tooltip(tr("gui.komaedit.help.recipe.import"))
                .onClick(() -> {
                    commitName();
                    ClientPlayNetworking.send(new RecipeActionPayload(RecipeActionPayload.IMPORT_PACKAGE, draft));
                    status = tr("gui.komaedit.status.recipe.import", draft.name());
                });

        List<ProceduralRecipe> recipes = ClientRecipeCatalog.recipes();
        ui.section(column, tr("gui.komaedit.recipe.saved", recipes.size()));
        if (recipes.isEmpty()) {
            ui.paragraph(column, tr("gui.komaedit.recipe.empty"), MUTED, 0);
            return;
        }
        for (ProceduralRecipe recipe : recipes) {
            Rect item = column.next(28);
            if (!ui.visible(item)) {
                continue;
            }
            ui.listItem(item, recipe.name(), shapeName(recipe.shape()) + " · " + materialSummary(recipe),
                            recipe.name().equals(draft.name()), 22)
                    .onClick(() -> {
                        draft = recipe;
                        nameInput = recipe.name();
                        status = tr("gui.komaedit.status.recipe.loaded", recipe.name());
                    });
            Rect delete = new Rect(item.right() - 22, item.y() + 6, 16, 16);
            ui.button(delete, "×", Tone.DANGER)
                    .tooltip(tr("gui.komaedit.help.recipe.delete"))
                    .onClick(() -> {
                        ClientPlayNetworking.send(new RecipeActionPayload(RecipeActionPayload.DELETE, recipe));
                        status = tr("gui.komaedit.status.recipe.deleted", recipe.name());
                    });
        }
    }

    // ── Filas con estado ────────────────────────────────────────────────────────────────────────────────

    private void stepper(Column column, String labelKey, Control control) {
        Rect row = column.next();
        if (!ui.visible(row)) {
            return;
        }
        NumericRange range = range(control);
        int value = value(control);
        String help = tr(control.helpKey) + "\n" + tr("gui.komaedit.help.range", range.min(), range.max(),
                range.step(), range.bigStep(), defaultValue(control));
        ui.stepper(row, new StudioUi.Stepper(
                tr(labelKey),
                Integer.toString(value),
                range.fraction(value),
                editingControl == control,
                dragControl == control,
                numberInput,
                direction -> adjust(control, direction),
                () -> {
                    // Arrastrar en horizontal cambia el valor; un clic sin mover abre el campo para escribirlo.
                    dragControl = control;
                    dragStartX = lastMouseX;
                    dragStartValue = value(control);
                    dragMoved = false;
                },
                () -> setValue(control, defaultValue(control)),
                help,
                control.name(),
                control
        ));
    }

    private void cycle(Column column, String labelKey, String value, String option, IntConsumer step) {
        Rect row = column.next();
        if (!ui.visible(row)) {
            return;
        }
        ui.cycle(row, tr(labelKey), value, step)
                .onReset(() -> resetOption(option))
                .tooltip(tr("gui.komaedit.help.option." + option) + "\n" + tr("gui.komaedit.help.cycle"))
                .group("option:" + option);
    }

    // ── Ventanas modales ────────────────────────────────────────────────────────────────────────────────

    private ColorStage.Host colorHost() {
        return new ColorStage.Host() {
            @Override
            public String tr(String key, Object... arguments) {
                return ProceduralStudioScreen.tr(key, arguments);
            }

            @Override
            public void send(String command) {
                sendCommand(command);
            }

            @Override
            public void pickBlock(Consumer<String> onPick) {
                openBlockCatalog();
                blockPicker = onPick;
            }

            @Override
            public void status(String text) {
                status = text;
            }
        };
    }

    private void openBlockCatalog() {
        blockPicker = null;
        blockCatalog = true;
        blockSearch = "";
        blockScroll = 0;
    }

    private Rect blockCatalogRect() {
        int modalWidth = Math.min(560, width - 32);
        int modalHeight = Math.min(400, height - 28);
        return new Rect((width - modalWidth) / 2, (height - modalHeight) / 2, modalWidth, modalHeight);
    }

    private int blockColumns(Rect modal) {
        return Math.max(4, (modal.width() - 24) / 24);
    }

    private int blockRows(Rect modal) {
        return Math.max(2, (modal.height() - 70) / 24);
    }

    private void drawBlockCatalog(int mouseX, int mouseY) {
        Rect modal = blockCatalogRect();
        ui.fill(new Rect(0, 0, width, height), SCRIM);
        ui.register(new Rect(0, 0, width, height)).onClick(() -> blockCatalog = false);
        ui.enableHover();
        ui.fill(modal, PANEL);
        ui.border(modal, DIVIDER);
        ui.fill(new Rect(modal.x(), modal.y(), 3, modal.height()), ACCENT);
        ui.register(modal);
        ui.bold(tr("gui.komaedit.blocks.title"), modal.x() + 12, modal.y() + 10, modal.width() - 24, TEXT);

        Rect search = new Rect(modal.x() + 12, modal.y() + 24, modal.width() - 24 - 70, 18);
        ui.field(search, blockSearch, tr("gui.komaedit.blocks.search"), true, false);
        ui.button(new Rect(search.right() + 6, search.y(), 64, 18), tr("gui.komaedit.blocks.close"), Tone.SECONDARY)
                .onClick(() -> blockCatalog = false);

        List<BlockChoice> blocks = filteredBlocks();
        int columns = blockColumns(modal);
        int rows = blockRows(modal);
        int start = blockScroll * columns;
        int end = Math.min(blocks.size(), start + rows * columns);
        String hoveredId = null;
        for (int index = start; index < end; index++) {
            int local = index - start;
            Rect cell = new Rect(modal.x() + 12 + local % columns * 24, modal.y() + 48 + local / columns * 24, 22, 22);
            BlockChoice choice = blocks.get(index);
            boolean hovered = ui.hovered(cell);
            boolean selected = choice.pattern().equals(draft.pattern());
            ui.fill(cell, selected ? CARD_ACTIVE : hovered ? CARD_HOVER : CARD);
            if (selected) {
                ui.border(cell, ACCENT);
            }
            ui.item(choice.stack(), cell.x() + 3, cell.y() + 3);
            if (hovered) {
                hoveredId = choice.pattern();
            }
            ui.register(cell).onClick(() -> {
                if (blockPicker != null) {
                    blockPicker.accept(choice.pattern());
                    blockPicker = null;
                    blockCatalog = false;
                    return;
                }
                draft = draft.copy(draft.name(), draft.shape(), choice.pattern(), draft.radius(), draft.spacing(),
                        draft.thickness(), draft.rollDegrees(), draft.height(), "", draft.gradientAxis(),
                        draft.dither(), draft.reverse());
                blockCatalog = false;
                status = tr("gui.komaedit.status.block.selected", compactId(choice.pattern()));
            });
        }
        String footer = hoveredId != null ? hoveredId : tr("gui.komaedit.blocks.count", blocks.size());
        ui.label(footer, modal.x() + 12, modal.bottom() - 14, modal.width() - 24, hoveredId != null ? TEXT : MUTED);
    }

    private void drawCheatSheet() {
        ui.fill(new Rect(0, 0, width, height), SCRIM);
        List<StudioShortcuts.Shortcut> shortcuts = StudioShortcuts.ALL;
        int columns = height < 330 ? 2 : 1;
        int rows = (shortcuts.size() + columns - 1) / columns;
        int keyWidth = 0;
        for (StudioShortcuts.Shortcut shortcut : shortcuts) {
            keyWidth = Math.max(keyWidth, ui.width(tr(shortcut.keysKey())));
        }
        int columnWidth = Math.min(340, (width - 40) / columns);
        int modalWidth = columnWidth * columns + 24;
        int modalHeight = Math.min(height - 16, 48 + rows * 12);
        Rect modal = new Rect((width - modalWidth) / 2, (height - modalHeight) / 2, modalWidth, modalHeight);
        ui.fill(modal, PANEL);
        ui.border(modal, DIVIDER);
        ui.fill(new Rect(modal.x(), modal.y(), 3, modal.height()), ACCENT);
        ui.bold(tr("gui.komaedit.shortcuts.title"), modal.x() + 14, modal.y() + 10, modalWidth - 28, TEXT);
        ui.label(tr("gui.komaedit.shortcuts.close"), modal.x() + 14, modal.bottom() - 14, modalWidth - 28, MUTED);
        int keyColumn = Math.min(keyWidth, columnWidth / 2);
        for (int index = 0; index < shortcuts.size(); index++) {
            StudioShortcuts.Shortcut shortcut = shortcuts.get(index);
            int x = modal.x() + 14 + index / rows * columnWidth;
            int y = modal.y() + 26 + index % rows * 12;
            if (y > modal.bottom() - 26) {
                continue;
            }
            ui.label(tr(shortcut.keysKey()), x, y, keyColumn, ACCENT_HOVER);
            ui.label(tr(shortcut.translationKey()), x + keyColumn + 8, y, columnWidth - keyColumn - 16,
                    TEXT_SECONDARY);
        }
    }

    private void drawTooltip(DrawContext context, int mouseX, int mouseY) {
        if (dragControl != null || typing() || resizingInspector) {
            return;
        }
        Widget widget = ui.at(mouseX, mouseY);
        if (widget == null || widget.tooltip().isEmpty() || widget.overValue(mouseX, mouseY)) {
            return;
        }
        List<OrderedText> lines = new ArrayList<>();
        for (String paragraph : widget.tooltip().split("\n")) {
            lines.addAll(textRenderer.wrapLines(Text.literal(paragraph), 220));
        }
        context.drawOrderedTooltip(textRenderer, lines, mouseX, mouseY);
    }

    // ── Ratón ───────────────────────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        ProceduralRecipe before = draft;
        Widget widget = ui.at(mouseX, mouseY);
        boolean handled = handleMouseClicked(mouseX, mouseY, button, widget);
        track(before, widget == null ? null : widget.group());
        return handled;
    }

    private boolean handleMouseClicked(double mouseX, double mouseY, int button, Widget widget) {
        if (cheatSheet) {
            cheatSheet = false;
            return true;
        }
        Object key = widget == null ? null : widget.key();
        if (editingControl != null) {
            if (key == editingControl && widget.overValue(mouseX, mouseY)) {
                return true;
            }
            commitNumber();
        }
        if (!"search".equals(key)) {
            editingSearch = false;
        }
        if (!"name".equals(key)) {
            editingName = false;
        }
        if (editingFormula && !"formula".equals(key)) {
            // Pulsar fuera aplica la fórmula antes de atender el clic.
            commitFormula();
        }
        if (blockCatalog) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && widget != null && widget.click() != null) {
                widget.click().run(mouseX, mouseY);
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && frame.resizeHandle().contains(mouseX, mouseY)) {
            resizingInspector = true;
            return true;
        }
        boolean inPanel = frame.panel().contains(mouseX, mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (widget != null && widget.reset() != null) {
                widget.reset().run();
                status = tr("gui.komaedit.reset_done");
            } else if (!inPanel) {
                cameraDragging = true;
            }
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (widget != null && widget.click() != null) {
            widget.click().run(mouseX, mouseY);
            return true;
        }
        if (inPanel) {
            return true;
        }
        if (stage == Stage.PATH) {
            worldClicked(mouseX, mouseY);
        }
        return true;
    }

    /** Clic en el mundo en la etapa Trayectoria: flecha del nodo elegido, otro nodo o un punto nuevo. */
    private void worldClicked(double mouseX, double mouseY) {
        Ray ray = rayForMouse(mouseX, mouseY);
        List<BlockPos> points = PathHologramRenderer.points();
        BlockPos selectedPosition = selectedPoint >= 0 && selectedPoint < points.size()
                ? points.get(selectedPoint)
                : null;
        int axis = selectedPosition == null ? -1 : pickGizmoAxis(ray, selectedPosition);
        if (axis >= 0 && beginAxisDrag(ray, selectedPosition, axis)) {
            return;
        }
        int pickedPoint = pickPathPoint(ray);
        if (pickedPoint >= 0) {
            selectedPoint = pickedPoint;
            status = tr("gui.komaedit.status.node.selected", selectedPoint + 1);
        } else if (hoverPlacement != null) {
            sendEditor(EditorActionPayload.PATH_ADD, -1, hoverPlacement);
            status = tr("gui.komaedit.status.node.added", coordinates(hoverPlacement));
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragControl != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double distance = mouseX - dragStartX;
            if (Math.abs(distance) >= 3) {
                dragMoved = true;
            }
            if (dragMoved) {
                ProceduralRecipe before = draft;
                setValue(dragControl, range(dragControl).dragged(dragStartValue, distance, hasShiftDown()));
                track(before, dragControl.name());
            }
            return true;
        }
        if (resizingInspector) {
            inspectorWidth = StudioLayout.clampPanelWidth(width, width - (int) Math.round(mouseX));
            return true;
        }
        if (cameraDragging && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && client != null && client.player != null) {
            client.player.changeLookDirection(-deltaX * 1.55, -deltaY * 1.55);
            return true;
        }
        if (draggingPoint && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggedPoint >= 0
                && draggedAxis >= 0 && axisDragOrigin != null) {
            Double axisPosition = axisParameter(rayForMouse(mouseX, mouseY), Vec3d.ofCenter(axisDragOrigin), draggedAxis);
            if (axisPosition != null) {
                int offset = (int) Math.round(axisPosition - axisDragStart);
                BlockPos position = switch (draggedAxis) {
                    case 0 -> axisDragOrigin.add(offset, 0, 0);
                    case 1 -> axisDragOrigin.add(0, offset, 0);
                    default -> axisDragOrigin.add(0, 0, offset);
                };
                if (position.getY() >= -2_048 && position.getY() <= 2_047
                        && !position.equals(lastDraggedPosition)) {
                    PathHologramRenderer.moveLocal(draggedPoint, position);
                    sendEditor(EditorActionPayload.PATH_MOVE, draggedPoint, position);
                    lastDraggedPosition = position;
                    status = tr("gui.komaedit.status.node.moving", draggedPoint + 1, axisName(draggedAxis),
                            coordinates(position));
                    updateHover((int) mouseX, (int) mouseY);
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragControl != null) {
            if (!dragMoved) {
                editingControl = dragControl;
                numberInput = Integer.toString(value(dragControl));
                status = tr("gui.komaedit.number.typing");
            }
            dragControl = null;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            resizingInspector = false;
            draggingPoint = false;
            draggedPoint = -1;
            draggedAxis = -1;
            axisDragOrigin = null;
            lastDraggedPosition = null;
            rememberedInspectorWidth = inspectorWidth;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            cameraDragging = false;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (blockCatalog) {
            Rect modal = blockCatalogRect();
            int columns = blockColumns(modal);
            int rows = (filteredBlocks().size() + columns - 1) / columns;
            blockScroll = Math.clamp(blockScroll - (int) Math.signum(verticalAmount), 0,
                    Math.max(0, rows - blockRows(modal)));
            return true;
        }
        if (cheatSheet) {
            return true;
        }
        if (frame.panel().contains(mouseX, mouseY)) {
            Widget widget = ui.at(mouseX, mouseY);
            if (widget != null && widget.scroll() != null && widget.overValue(mouseX, mouseY)
                    && verticalAmount != 0.0 && editingControl == null) {
                ProceduralRecipe before = draft;
                widget.scroll().accept(verticalAmount > 0.0 ? 1 : -1);
                track(before, widget.group());
                return true;
            }
            if (frame.viewport().contains(mouseX, mouseY)) {
                contentScroll = Math.clamp(contentScroll - (int) Math.signum(verticalAmount) * 20, 0,
                        maximumContentScroll());
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ── Teclado ─────────────────────────────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ProceduralRecipe before = draft;
        boolean handled = handleKeyPressed(keyCode, scanCode, modifiers);
        track(before, null);
        return handled;
    }

    private boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        // Con un campo abierto las letras se escriben: ningún atajo (ni K) se dispara.
        if (editingFormula) {
            return formulaKeyPressed(keyCode, control);
        }
        if (editingControl != null) {
            return numberKeyPressed(keyCode);
        }
        if (editingSearch) {
            return searchKeyPressed(keyCode);
        }
        if (blockCatalog) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                blockCatalog = false;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !blockSearch.isEmpty()) {
                blockSearch = blockSearch.substring(0, blockSearch.length() - 1);
                blockScroll = 0;
            }
            return true;
        }
        if (editingName) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitName();
                editingName = false;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !nameInput.isEmpty()) {
                nameInput = nameInput.substring(0, nameInput.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nameInput = draft.name();
                editingName = false;
            }
            return true;
        }
        if (cheatSheet) {
            cheatSheet = false;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_F1) {
                return true;
            }
        }
        if (control && keyCode == GLFW.GLFW_KEY_Z) {
            sendEditor(shift ? EditorActionPayload.REDO : EditorActionPayload.UNDO, -1, BlockPos.ORIGIN);
            status = tr(shift ? "gui.komaedit.status.world.redo" : "gui.komaedit.status.world.undo");
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_Y) {
            sendEditor(EditorActionPayload.REDO, -1, BlockPos.ORIGIN);
            status = tr("gui.komaedit.status.world.redo");
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_K) {
            close();
            return true;
        }
        if (shortcutPressed(keyCode, control, shift)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Con el campo de fórmula abierto ninguna tecla dispara atajos (K cierra el Studio, Ctrl+Z deshace…). */
    private boolean formulaKeyPressed(int keyCode, boolean control) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitFormula();
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            editingFormula = false;
            formulaError = null;
            status = tr("gui.komaedit.status.formula.cancelled");
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !formulaInput.isEmpty()) {
            formulaInput = control ? "" : formulaInput.substring(0, formulaInput.length() - 1);
            updateFormulaError();
        } else if (control && keyCode == GLFW.GLFW_KEY_V && client != null) {
            appendFormula(client.keyboard.getClipboard());
        }
        return true;
    }

    private void appendFormula(String text) {
        StringBuilder next = new StringBuilder(formulaInput);
        for (int index = 0; index < text.length() && next.length() < TubeFormula.MAX_LENGTH; index++) {
            char value = text.charAt(index);
            next.append(value >= 32 && value < 127 ? value : ' ');
        }
        formulaInput = next.toString();
        updateFormulaError();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editingFormula) {
            if (chr >= 32 && chr < 127 && formulaInput.length() < TubeFormula.MAX_LENGTH) {
                formulaInput += chr;
                updateFormulaError();
            }
            return true;
        }
        if (editingControl != null) {
            if ((Character.isDigit(chr) || (chr == '-' && numberInput.isEmpty())) && numberInput.length() < 6) {
                numberInput += chr;
            }
            return true;
        }
        if (editingSearch) {
            if (chr >= 32 && chr != 127 && shapeSearch.length() < 32) {
                shapeSearch += chr;
                category = ShapeCategory.ALL;
                contentScroll = 0;
            }
            return true;
        }
        if (blockCatalog && blockSearch.length() < 64
                && (Character.isLetterOrDigit(chr) || chr == '_' || chr == ':' || chr == '-')) {
            blockSearch += Character.toLowerCase(chr);
            blockScroll = 0;
            return true;
        }
        if (editingName) {
            if (nameInput.length() < 32 && (Character.isLetterOrDigit(chr) || chr == '_' || chr == '-')) {
                nameInput += chr;
            }
            return true;
        }
        if (blockCatalog) {
            return true;
        }
        if (chr == '?') {
            cheatSheet = !cheatSheet;
            return true;
        }
        if (chr == '/') {
            focusSearch();
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private boolean numberKeyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitNumber();
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            editingControl = null;
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !numberInput.isEmpty()) {
            numberInput = numberInput.substring(0, numberInput.length() - 1);
        }
        return true;
    }

    private boolean searchKeyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (shapeSearch.isEmpty()) {
                editingSearch = false;
            } else {
                shapeSearch = "";
            }
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            List<PathShape> shown = visibleShapes(FIGURE_SHAPES);
            editingSearch = false;
            if (!shown.isEmpty()) {
                selectShape(shown.getFirst());
            }
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !shapeSearch.isEmpty()) {
            shapeSearch = hasControlDown() ? "" : shapeSearch.substring(0, shapeSearch.length() - 1);
            contentScroll = 0;
        }
        return true;
    }

    private void focusSearch() {
        if (stage != Stage.FIGURE) {
            selectStage(Stage.FIGURE);
        }
        contentScroll = 0;
        editingSearch = true;
        status = tr("gui.komaedit.search.typing");
    }

    /** Atajos con el Studio sin campo de texto abierto; devuelve si la tecla era un atajo. */
    private boolean shortcutPressed(int keyCode, boolean control, boolean shift) {
        if (control) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_F -> focusSearch();
                case GLFW.GLFW_KEY_S -> saveRecipe();
                case GLFW.GLFW_KEY_DELETE -> {
                    sendCommand("komaedit path clear");
                    status = tr("gui.komaedit.path.cleared");
                }
                default -> {
                    return false;
                }
            }
            return true;
        }
        if (!shift && keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + Stage.values().length) {
            selectStage(Stage.values()[keyCode - GLFW.GLFW_KEY_1]);
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_TAB -> selectStage(Stage.values()[Math.floorMod(
                    stage.ordinal() + (shift ? -1 : 1), Stage.values().length)]);
            case GLFW.GLFW_KEY_B -> performBuild();
            case GLFW.GLFW_KEY_H -> toggleHologram();
            case GLFW.GLFW_KEY_Z -> {
                if (shift) {
                    redoParameters();
                } else {
                    undoParameters();
                }
            }
            case GLFW.GLFW_KEY_Y -> redoParameters();
            case GLFW.GLFW_KEY_R -> randomVariant();
            case GLFW.GLFW_KEY_F1 -> cheatSheet = !cheatSheet;
            case GLFW.GLFW_KEY_PAGE_UP -> contentScroll = Math.max(0, contentScroll - pageScroll());
            case GLFW.GLFW_KEY_PAGE_DOWN -> contentScroll = Math.min(maximumContentScroll(),
                    contentScroll + pageScroll());
            default -> {
                return false;
            }
        }
        return true;
    }

    private int pageScroll() {
        return Math.max(40, frame.viewport().height() - 40);
    }

    private int maximumContentScroll() {
        return Math.max(0, contentHeight - frame.viewport().height());
    }

    // ── Mundo: puntero, rayos y flechas ─────────────────────────────────────────────────────────────────

    private void updateHover(int mouseX, int mouseY) {
        hoverPlacement = null;
        BlockPos overlay = null;
        if (!blockCatalog && stage == Stage.PATH && mouseX < frame.panel().x() && client != null
                && client.world != null && client.player != null) {
            Ray ray = rayForMouse(mouseX, mouseY);
            HitResult result = client.world.raycast(new RaycastContext(
                    ray.origin(),
                    ray.origin().add(ray.direction().multiply(REACH)),
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    client.player
            ));
            if (result instanceof BlockHitResult hit && result.getType() == HitResult.Type.BLOCK) {
                hoverPlacement = hit.getBlockPos().offset(hit.getSide()).toImmutable();
                overlay = hoverPlacement;
            }
        }
        List<BlockPos> points = PathHologramRenderer.points();
        if (selectedPoint >= points.size()) {
            selectedPoint = -1;
        }
        BlockPos gizmoOrigin = stage == Stage.PATH && selectedPoint >= 0
                ? points.get(selectedPoint)
                : null;
        EditorOverlayRenderer.update(
                true,
                EditorOverlayRenderer.PATH,
                overlay,
                draft.radius(),
                null,
                null,
                selectedPoint,
                gizmoOrigin,
                draggedAxis
        );
    }

    private Ray rayForMouse(double mouseX, double mouseY) {
        if (client == null || client.player == null) {
            return new Ray(Vec3d.ZERO, new Vec3d(0.0, -1.0, 0.0));
        }
        Camera camera = client.gameRenderer.getCamera();
        Vec3d origin = camera.getPos();
        Vector3f cameraForward = camera.getHorizontalPlane();
        Vector3f cameraUp = camera.getVerticalPlane();
        Vector3f cameraLeft = camera.getDiagonalPlane();
        Vec3d forward = new Vec3d(cameraForward.x(), cameraForward.y(), cameraForward.z()).normalize();
        Vec3d up = new Vec3d(cameraUp.x(), cameraUp.y(), cameraUp.z()).normalize();
        Vec3d right = new Vec3d(-cameraLeft.x(), -cameraLeft.y(), -cameraLeft.z()).normalize();
        double normalizedX = mouseX / Math.max(1.0, width) * 2.0 - 1.0;
        double normalizedY = mouseY / Math.max(1.0, height) * 2.0 - 1.0;
        double aspect = width / Math.max(1.0, (double) height);
        double fieldOfView = ((GameRendererAccessor) client.gameRenderer).komaedit$getFov(
                camera,
                camera.getLastTickDelta(),
                true
        );
        double tangent = Math.tan(Math.toRadians(fieldOfView * 0.5));
        Vec3d direction = forward
                .add(right.multiply(normalizedX * tangent * aspect))
                .add(up.multiply(-normalizedY * tangent))
                .normalize();
        return new Ray(origin, direction);
    }

    private int pickPathPoint(Ray ray) {
        List<BlockPos> points = PathHologramRenderer.points();
        int selected = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index < points.size(); index++) {
            Vec3d center = Vec3d.ofCenter(points.get(index));
            double along = center.subtract(ray.origin()).dotProduct(ray.direction());
            if (along < 0.0) {
                continue;
            }
            double distance = center.distanceTo(ray.origin().add(ray.direction().multiply(along)));
            double threshold = Math.clamp(along * 0.009, 0.70, 1.40);
            if (distance <= threshold && distance < bestDistance) {
                selected = index;
                bestDistance = distance;
            }
        }
        return selected;
    }

    private boolean beginAxisDrag(Ray ray, BlockPos origin, int axis) {
        Double parameter = axisParameter(ray, Vec3d.ofCenter(origin), axis);
        if (parameter == null) {
            return false;
        }
        draggingPoint = true;
        draggedPoint = selectedPoint;
        draggedAxis = axis;
        axisDragOrigin = origin.toImmutable();
        axisDragStart = parameter;
        lastDraggedPosition = origin;
        status = tr("gui.komaedit.status.node.adjusting", selectedPoint + 1, axisName(axis));
        return true;
    }

    private int pickGizmoAxis(Ray ray, BlockPos origin) {
        Vec3d center = Vec3d.ofCenter(origin);
        double cameraDistance = center.distanceTo(ray.origin());
        double length = gizmoLength(cameraDistance);
        double threshold = Math.clamp(cameraDistance * 0.010, 0.32, 1.35);
        int selected = -1;
        double best = Double.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            AxisProjection projection = axisProjection(ray, center, axis);
            if (projection == null || projection.rayDistance() < 0.0
                    || projection.axisDistance() < 0.28
                    || projection.axisDistance() > length + threshold) {
                continue;
            }
            if (projection.separation() <= threshold && projection.separation() < best) {
                selected = axis;
                best = projection.separation();
            }
        }
        return selected;
    }

    private Double axisParameter(Ray ray, Vec3d origin, int axis) {
        AxisProjection projection = axisProjection(ray, origin, axis);
        return projection == null ? null : projection.axisDistance();
    }

    private AxisProjection axisProjection(Ray ray, Vec3d origin, int axis) {
        Vec3d axisVector = axisVector(axis);
        Vec3d offset = ray.origin().subtract(origin);
        double parallel = ray.direction().dotProduct(axisVector);
        double denominator = 1.0 - parallel * parallel;
        if (denominator < 1.0E-5) {
            return null;
        }
        double rayOffset = ray.direction().dotProduct(offset);
        double axisOffset = axisVector.dotProduct(offset);
        double rayDistance = (parallel * axisOffset - rayOffset) / denominator;
        double axisDistance = (axisOffset - parallel * rayOffset) / denominator;
        Vec3d rayPoint = ray.origin().add(ray.direction().multiply(rayDistance));
        Vec3d axisPoint = origin.add(axisVector.multiply(axisDistance));
        return new AxisProjection(rayDistance, axisDistance, rayPoint.distanceTo(axisPoint));
    }

    private static Vec3d axisVector(int axis) {
        return switch (axis) {
            case 0 -> new Vec3d(1.0, 0.0, 0.0);
            case 1 -> new Vec3d(0.0, 1.0, 0.0);
            default -> new Vec3d(0.0, 0.0, 1.0);
        };
    }

    private static String axisName(int axis) {
        return switch (axis) {
            case 0 -> "X";
            case 1 -> "Y";
            default -> "Z";
        };
    }

    private static double gizmoLength(double cameraDistance) {
        return Math.clamp(cameraDistance * 0.075, 3.0, 18.0);
    }

    // ── Valores de la receta ────────────────────────────────────────────────────────────────────────────

    private void adjust(Control control, int direction) {
        setValue(control, range(control).stepped(value(control), direction, hasShiftDown()));
    }

    /** Rango del control; la altura del torii va de 4 en 4 como antes. */
    private NumericRange range(Control control) {
        if (control == Control.HEIGHT && draft.shape() == PathShape.TORII) {
            return new NumericRange(3, 256, 4, 20);
        }
        return control.range;
    }

    private int value(Control control) {
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
    private int defaultValue(Control control) {
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

    private void setValue(Control control, int requested) {
        int next = range(control).clamp(requested);
        // Envejecer no es parte de la receta: sus valores viven en la pantalla y no entran en el historial.
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

    private void updateFormulaError() {
        formulaError = formulaInput.isBlank() ? null : TubeFormula.validate(formulaInput);
        status = formulaError == null ? tr("gui.komaedit.status.formula.valid") : formulaError;
    }

    /** Aplica lo escrito; vacío o igual a la de serie vuelve a la de serie. Si no compila, no cambia nada. */
    private void commitFormula() {
        editingFormula = false;
        String input = formulaInput.trim();
        TubeFormulaPreset preset = formulaPreset();
        formulaError = null;
        if (input.isEmpty() || input.equals(preset.source())) {
            draft = draft.withFormula("");
            status = tr("gui.komaedit.status.formula.default", tr(StudioNames.formula(preset)));
            return;
        }
        String error = TubeFormula.validate(input);
        if (error != null) {
            formulaError = error;
            status = tr("gui.komaedit.status.formula.not_applied", error);
            return;
        }
        draft = draft.withFormula(input);
        status = tr("gui.komaedit.status.formula.custom");
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
    private void randomVariant() {
        int seed = ShapeVariants.randomSeed(SEEDS);
        applyProportions(ShapeVariants.variant(draft.shape(), seed));
        status = tr("gui.komaedit.variant.applied", seed);
    }

    private void selectShape(PathShape shape) {
        if (draft.shape() != shape) {
            draft = draft.withShapeDefaults(shape);
            status = tr("gui.komaedit.status.shape.changed", shapeName(shape));
        }
        preferences().pushRecent(shape.id());
        savePreferences();
    }

    private void selectStage(Stage next) {
        stage = next;
        contentScroll = 0;
        editingSearch = false;
        if (stage == Stage.STRUCTURES && !STRUCTURE_SHAPES.contains(draft.shape())) {
            draft = draft.withShapeDefaults(STRUCTURE_SHAPES.getFirst());
        } else if (stage == Stage.FIGURE && !FIGURE_SHAPES.contains(draft.shape())) {
            draft = draft.withShapeDefaults(FIGURE_SHAPES.getFirst());
        }
        status = tr(stage.subtitleKey);
    }

    private void toggleHologram() {
        boolean hide = !ProceduralPreviewRenderer.hidden();
        ProceduralPreviewRenderer.setHidden(hide);
        status = tr(hide ? "gui.komaedit.hologram.hidden" : "gui.komaedit.hologram.shown");
    }

    private void saveRecipe() {
        commitName();
        ClientPlayNetworking.send(new RecipeActionPayload(RecipeActionPayload.SAVE, draft));
        status = tr("gui.komaedit.status.recipe.saved", draft.name());
    }

    private void undoParameters() {
        restoreParameters(HISTORY.undo(draft), "gui.komaedit.history.undone", "gui.komaedit.history.nothing_undo");
    }

    private void redoParameters() {
        restoreParameters(HISTORY.redo(draft), "gui.komaedit.history.redone", "gui.komaedit.history.nothing_redo");
    }

    /** Vuelve a un estado del historial sin cambiar el nombre que se está usando. */
    private void restoreParameters(ProceduralRecipe recipe, String doneKey, String emptyKey) {
        if (recipe == null) {
            status = tr(emptyKey);
            return;
        }
        draft = recipe.withName(draft.name());
        restoring = true;
        editingControl = null;
        formulaError = null;
        if (STRUCTURE_SHAPES.contains(draft.shape()) && stage == Stage.FIGURE) {
            stage = Stage.STRUCTURES;
        } else if (FIGURE_SHAPES.contains(draft.shape()) && stage == Stage.STRUCTURES) {
            stage = Stage.FIGURE;
        }
        status = tr(doneKey);
    }

    /**
     * Anota en el historial lo que cambió una acción. El nombre no cuenta: se fija al guardar o construir.
     * {@code group} funde los cambios seguidos del mismo control.
     */
    private void track(ProceduralRecipe before, String group) {
        if (restoring) {
            restoring = false;
            return;
        }
        if (!before.withName("_").equals(draft.withName("_"))) {
            HISTORY.record(before, group, System.currentTimeMillis());
        }
    }

    /** Valor escrito a mano: Intro o pulsar fuera lo aplica; lo que no es un número se descarta. */
    private void commitNumber() {
        if (editingControl == null) {
            return;
        }
        Control control = editingControl;
        editingControl = null;
        range(control).parse(numberInput).ifPresentOrElse(
                value -> setValue(control, value),
                () -> status = tr("gui.komaedit.number.invalid"));
    }

    private ProceduralRecipe copyMaterial(String palette) {
        return draft.copy(draft.name(), draft.shape(), draft.pattern(), draft.radius(), draft.spacing(),
                draft.thickness(), draft.rollDegrees(), draft.height(), palette, draft.gradientAxis(),
                draft.dither(), draft.reverse());
    }

    private void performBuild() {
        if (!pathReady()) {
            selectStage(Stage.PATH);
            status = tr("gui.komaedit.status.need_points", requiredPoints());
            return;
        }
        int blocks = ProceduralPreviewRenderer.blockCount();
        if (!buildEstimate.confirm(blocks, System.currentTimeMillis())) {
            status = tr("gui.komaedit.build_large", BuildEstimate.compact(blocks));
            return;
        }
        commitName();
        ClientPlayNetworking.send(new RecipeActionPayload(RecipeActionPayload.BUILD, draft));
        selectedInstanceId = "";
        status = tr("gui.komaedit.status.build.sent");
    }

    private boolean pathReady() {
        return PathHologramRenderer.points().size() >= requiredPoints();
    }

    private int requiredPoints() {
        return PathHologramRenderer.closed() ? 3 : 2;
    }

    private void commitName() {
        if (!ProceduralRecipe.VALID_NAME.matcher(nameInput).matches()) {
            nameInput = "receta_" + System.currentTimeMillis() % 100_000L;
            status = tr("gui.komaedit.status.name.adjusted", nameInput);
        }
        draft = draft.withName(nameInput);
    }

    private List<PathShape> visibleShapes(List<PathShape> shapes) {
        if (!shapeSearch.isBlank()) {
            return ShapeSearch.search(shapes, shapeSearch, ProceduralStudioScreen::shapeName);
        }
        StudioPreferences current = preferences();
        return shapes.stream().filter(shape -> category.contains(shape, current)).toList();
    }

    private List<BlockChoice> filteredBlocks() {
        if (blockSearch.isBlank()) {
            return ALL_BLOCKS;
        }
        String normalized = blockSearch.toLowerCase(Locale.ROOT);
        return ALL_BLOCKS.stream().filter(choice -> choice.pattern().contains(normalized)).toList();
    }

    private ItemStack stack(String pattern) {
        Identifier identifier = Identifier.tryParse(pattern);
        if (identifier == null) {
            return Items.BARRIER.getDefaultStack();
        }
        ItemStack stack = Registries.BLOCK.get(identifier).asItem().getDefaultStack();
        return stack.isEmpty() ? Items.BARRIER.getDefaultStack() : stack;
    }

    private void sendEditor(int action, int index, BlockPos position) {
        ClientPlayNetworking.send(new EditorActionPayload(action, index, position, draft.radius(), draft.pattern()));
    }

    private void sendCommand(String command) {
        if (client != null && client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatCommand(command);
        }
    }

    private void updateMovementKeys() {
        if (client == null) {
            return;
        }
        long window = client.getWindow().getHandle();
        client.options.forwardKey.setPressed(down(window, GLFW.GLFW_KEY_W));
        client.options.backKey.setPressed(down(window, GLFW.GLFW_KEY_S));
        client.options.leftKey.setPressed(down(window, GLFW.GLFW_KEY_A));
        client.options.rightKey.setPressed(down(window, GLFW.GLFW_KEY_D));
        client.options.jumpKey.setPressed(down(window, GLFW.GLFW_KEY_SPACE));
        client.options.sneakKey.setPressed(down(window, GLFW.GLFW_KEY_LEFT_SHIFT));
        client.options.sprintKey.setPressed(down(window, GLFW.GLFW_KEY_LEFT_CONTROL));
    }

    private void releaseMovementKeys() {
        MinecraftClient current = MinecraftClient.getInstance();
        current.options.forwardKey.setPressed(false);
        current.options.backKey.setPressed(false);
        current.options.leftKey.setPressed(false);
        current.options.rightKey.setPressed(false);
        current.options.jumpKey.setPressed(false);
        current.options.sneakKey.setPressed(false);
        current.options.sprintKey.setPressed(false);
    }

    private static String shapeName(PathShape shape) {
        return tr(StudioNames.shape(shape));
    }

    private static String materialSummary(ProceduralRecipe recipe) {
        return recipe.usesGradient() ? recipe.palette().displayName() : compactId(recipe.pattern());
    }

    private static String compactId(String value) {
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + ", " + position.getY() + ", " + position.getZ();
    }

    private static PathShape shapeById(String id) {
        try {
            return PathShape.byId(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean down(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private static <T> T cycle(T[] values, T current, int direction) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == current) {
                return values[Math.floorMod(index + direction, values.length)];
            }
        }
        return values[0];
    }

    private static String tr(String key, Object... arguments) {
        return Text.translatable(key, arguments).getString();
    }

    private record BlockChoice(String pattern, ItemStack stack) {
    }

    private record Ray(Vec3d origin, Vec3d direction) {
    }

    private record AxisProjection(double rayDistance, double axisDistance, double separation) {
    }

    private enum Stage {
        PATH("gui.komaedit.stage.path", StudioIcons.Icon.PATH),
        FIGURE("gui.komaedit.stage.figure", StudioIcons.Icon.FIGURE),
        STRUCTURES("gui.komaedit.stage.structures", StudioIcons.Icon.STRUCTURES),
        MODIFIERS("gui.komaedit.stage.modifiers", StudioIcons.Icon.MODIFIERS),
        MATERIAL("gui.komaedit.stage.material", StudioIcons.Icon.MATERIAL),
        WEATHER("gui.komaedit.stage.weather", StudioIcons.Icon.WEATHER),
        INSTANCES("gui.komaedit.stage.instances", StudioIcons.Icon.INSTANCES),
        RECIPES("gui.komaedit.stage.recipes", StudioIcons.Icon.RECIPES),
        COLOR("gui.komaedit.stage.color", StudioIcons.Icon.COLOR);

        private final String titleKey;
        private final String subtitleKey;
        private final StudioIcons.Icon icon;

        Stage(String titleKey, StudioIcons.Icon icon) {
            this.titleKey = titleKey;
            this.subtitleKey = titleKey + ".subtitle";
            this.icon = icon;
        }
    }

    private enum Control {
        RADIUS(ShapeVariants.RADIUS),
        SPACING(ShapeVariants.SPACING),
        THICKNESS(ShapeVariants.THICKNESS),
        ROLL(ShapeVariants.ROLL),
        HEIGHT(ShapeVariants.HEIGHT),
        DITHER(new NumericRange(0, 100, 5, 25)),
        START_SCALE(new NumericRange(25, 300, 5, 25)),
        END_SCALE(new NumericRange(25, 300, 5, 25)),
        START_SPACING(new NumericRange(50, 200, 5, 25)),
        END_SPACING(new NumericRange(50, 200, 5, 25)),
        TWIST(new NumericRange(-1440, 1440, 15, 90)),
        RHYTHM_INTERVAL(NumericRange.of(2, 32, 1)),
        CONFORM_RADIUS(NumericRange.of(2, 32, 1)),
        SURFACE_OFFSET(new NumericRange(-8, 8, 1, 4)),
        SOCKET_INTERVAL(new NumericRange(2, 64, 1, 8)),
        WEATHER_INTENSITY(new NumericRange(0, 100, 5, 25)),
        WEATHER_COLLAPSE(new NumericRange(0, 100, 5, 25)),
        WEATHER_VEGETATION(new NumericRange(0, 100, 5, 25));

        private final NumericRange range;
        private final String helpKey;

        Control(NumericRange range) {
            this.range = range;
            this.helpKey = "gui.komaedit.help." + name().toLowerCase(Locale.ROOT);
        }
    }
}
