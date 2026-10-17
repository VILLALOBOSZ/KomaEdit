package com.komainu.moxia.client.gui;

import com.komainu.moxia.client.mixin.GameRendererAccessor;
import com.komainu.moxia.client.preview.EditorOverlayRenderer;
import com.komainu.moxia.client.preview.PathHologramRenderer;
import com.komainu.moxia.client.preview.ProceduralPreviewRenderer;
import com.komainu.moxia.client.recipe.ClientRecipeCatalog;
import com.komainu.moxia.client.recipe.ClientInstanceCatalog;
import com.komainu.moxia.editor.EditorActionPayload;
import com.komainu.moxia.gradient.GradientAxis;
import com.komainu.moxia.gradient.GradientPalette;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.recipe.ProceduralInstanceSummary;
import com.komainu.moxia.recipe.RecipeExtensions;
import com.komainu.moxia.recipe.RecipeActionPayload;
import com.komainu.moxia.recipe.CurveEasing;
import com.komainu.moxia.recipe.MaterialMetric;
import com.komainu.moxia.recipe.SurfaceMode;
import com.komainu.moxia.recipe.SocketPlacement;
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
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.StudioShortcuts;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

public final class ProceduralStudioScreen extends Screen {
    private static final int PANEL = 0xEE0C0D12;
    private static final int PANEL_SOFT = 0xE5181A22;
    private static final int CARD = 0xE8242731;
    private static final int CARD_HOVER = 0xF0323642;
    private static final int CARD_SELECTED = 0xF03B1E29;
    private static final int ACCENT = 0xFFE24B6A;
    private static final int ACCENT_DARK = 0xFF7D2438;
    private static final int TEXT = 0xFFF3EEF0;
    private static final int MUTED = 0xFFA8A4AE;
    private static final int SUCCESS = 0xFF67DFA3;
    private static final int WARNING = 0xFFF4C86A;
    private static final int DANGER = 0xFFFF6B6B;
    private static final int FAVORITE = 0xFFF2C14B;
    private static final double REACH = 4_096.0;
    private static final int RAIL_WIDTH = 54;
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

    private static int rememberedInspectorWidth = 340;
    private static ProceduralRecipe rememberedDraft = ProceduralRecipe.defaults();
    private static volatile ProceduralRecipe previewDraft = rememberedDraft;
    private static final Path PREFERENCES_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("komaedit").resolve("studio.json");
    private static StudioPreferences preferences;
    /** Deshacer de ajustes del Studio; sobrevive a cerrar y abrir, no a salir del mundo. */
    private static final ParameterHistory<ProceduralRecipe> HISTORY = new ParameterHistory<>(100);
    private static final SplittableRandom SEEDS = new SplittableRandom();
    private static ShapeCategory rememberedCategory = ShapeCategory.ALL;

    private ProceduralRecipe draft = rememberedDraft;
    private Stage stage = STRUCTURE_SHAPES.contains(draft.shape()) ? Stage.STRUCTURES : Stage.FIGURE;
    private int inspectorWidth;
    private int contentScroll;
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
    private boolean editingName;
    private String nameInput = draft.name();
    private boolean editingFormula;
    private String formulaInput = "";
    /** Error de lo último escrito; si quedó sin aplicar, al reabrir el campo se recupera para corregirlo. */
    private String formulaError;
    private int formulaFieldX;
    private int formulaFieldY;
    private int formulaFieldWidth;
    private String blockSearch = "";
    private String status = tr("gui.komaedit.status.ready");
    private String selectedInstanceId = "";
    private BlockPos hoverPlacement;
    private BlockPos lastDraggedPosition;
    /** Controles dibujados en el último fotograma: rueda, clic derecho, arrastre y ayudas. */
    private final List<Hotspot> hotspots = new ArrayList<>();
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
    private List<OrderedText> tooltip = List.of();

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
        inspectorWidth = Math.clamp(rememberedInspectorWidth, minimumInspectorWidth(), maximumInspectorWidth());
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
        contentScroll = Math.clamp(contentScroll, 0, maximumContentScroll());
        hotspots.clear();
        tooltip = List.of();
        updateHover(mouseX, mouseY);
        drawTopBar(context, mouseX, mouseY);
        drawStageRail(context, mouseX, mouseY);
        drawInspector(context, mouseX, mouseY);
        drawBuildBar(context, mouseX, mouseY);
        if (blockCatalog) {
            drawBlockCatalog(context, mouseX, mouseY);
        } else if (cheatSheet) {
            drawCheatSheet(context);
        } else {
            hotspotTooltip(mouseX, mouseY);
            if (!tooltip.isEmpty()) {
                context.drawOrderedTooltip(textRenderer, tooltip, mouseX, mouseY);
            }
        }
    }

    private boolean typing() {
        return editingFormula || editingName || editingSearch || editingControl != null || blockCatalog;
    }

    private void drawTopBar(DrawContext context, int mouseX, int mouseY) {
        int right = inspectorLeft() - 10;
        context.fill(12, 10, right, 42, PANEL);
        context.fill(12, 10, 16, 42, ACCENT);
        List<TopPill> pills = topPills();
        int leftmost = pills.isEmpty() ? right : pills.getLast().x();
        int titleWidth = textRenderer.getWidth("KOMAEDIT");
        if (24 + titleWidth + 6 <= leftmost) {
            context.drawText(textRenderer, "KOMAEDIT", 24, 17, TEXT, false);
            int subtitleWidth = leftmost - 34 - titleWidth - 8;
            if (subtitleWidth >= 40) {
                drawTrimmed(context, tr("gui.komaedit.subtitle"), 34 + titleWidth, 17, subtitleWidth, MUTED);
            }
        }
        for (TopPill pill : pills) {
            boolean enabled = switch (pill.action()) {
                case UNDO -> HISTORY.canUndo();
                case REDO -> HISTORY.canRedo();
                default -> true;
            };
            boolean active = switch (pill.action()) {
                case NODES -> pathReady();
                case HOLOGRAM -> !ProceduralPreviewRenderer.hidden();
                case HELP -> cheatSheet;
                default -> false;
            };
            drawPill(context, mouseX, mouseY, pill.x(), 16, pill.width(), 20, pill.label(), active,
                    enabled ? (active ? SUCCESS : TEXT) : 0xFF5C5A63);
            hotspots.add(Hotspot.tip(pill.x(), 16, pill.width(), 20, tr(pill.action().helpKey)));
        }
    }

    /** Botones de la barra superior, de derecha a izquierda; los que no caben se omiten (nodos primero). */
    private List<TopPill> topPills() {
        int right = inspectorLeft() - 10;
        List<TopPill> pills = new ArrayList<>();
        int x = right - 10;
        for (TopAction action : TopAction.values()) {
            String label = switch (action) {
                case CLOSE -> tr("gui.komaedit.close");
                case HELP -> "?";
                case HOLOGRAM -> ProceduralPreviewRenderer.hidden() ? "H ○" : "H ◉";
                case REDO -> "↷";
                case UNDO -> "↶";
                case NODES -> tr("gui.komaedit.nodes", PathHologramRenderer.points().size());
            };
            int pillWidth = Math.max(20, textRenderer.getWidth(label) + 14);
            if (x - pillWidth < 18) {
                break;
            }
            x -= pillWidth;
            pills.add(new TopPill(action, x, pillWidth, label));
            x -= 5;
        }
        return pills;
    }

    private boolean topBarClicked(double mouseX, double mouseY) {
        for (TopPill pill : topPills()) {
            if (!inside(mouseX, mouseY, pill.x(), 16, pill.width(), 20)) {
                continue;
            }
            switch (pill.action()) {
                case CLOSE -> close();
                case HELP -> cheatSheet = !cheatSheet;
                case HOLOGRAM -> toggleHologram();
                case UNDO -> undoParameters();
                case REDO -> redoParameters();
                case NODES -> {
                    stage = Stage.PATH;
                    contentScroll = 0;
                }
            }
            return true;
        }
        return false;
    }

    private void drawStageRail(DrawContext context, int mouseX, int mouseY) {
        int top = 54;
        int stride = stageStride();
        int buttonSize = Math.min(42, stride - 4);
        int buttonX = 39 - buttonSize / 2;
        int railHeight = Stage.values().length * stride + 14;
        context.fill(12, top, 12 + RAIL_WIDTH, top + railHeight, PANEL);
        for (int index = 0; index < Stage.values().length; index++) {
            Stage value = Stage.values()[index];
            int y = top + 8 + index * stride;
            boolean selected = value == stage;
            boolean hovered = inside(mouseX, mouseY, buttonX, y, buttonSize, buttonSize);
            context.fill(buttonX, y, buttonX + buttonSize, y + buttonSize,
                    selected ? CARD_SELECTED : hovered ? CARD_HOVER : CARD);
            if (selected) {
                context.fill(buttonX, y, buttonX + 3, y + buttonSize, ACCENT);
            }
            drawStageIcon(context, value, 39, y + buttonSize / 2, selected ? ACCENT : TEXT);
            if (hovered) {
                String title = (index + 1) + " · " + tr(value.titleKey);
                int width = textRenderer.getWidth(title) + 16;
                int tooltipY = y + Math.max(4, (buttonSize - 21) / 2);
                context.fill(66, tooltipY, 66 + width, tooltipY + 21, PANEL);
                context.drawText(textRenderer, title, 74, tooltipY + 7, TEXT, false);
            }
        }
    }

    private void drawInspector(DrawContext context, int mouseX, int mouseY) {
        int left = inspectorLeft();
        context.fill(left, 0, width, height, PANEL);
        context.fill(left, 0, left + 2, height, ACCENT_DARK);
        if (inside(mouseX, mouseY, left - 4, 0, 8, height)) {
            context.fill(left - 2, 0, left + 2, height, ACCENT);
        }

        context.drawText(textRenderer, tr(stage.titleKey).toUpperCase(Locale.ROOT), left + 18, 17, ACCENT, false);
        drawTrimmed(context, tr(stage.subtitleKey), left + 18, 31, inspectorWidth - 36, MUTED);
        context.fill(left + 14, 49, width - 14, 50, 0xFF343640);

        int clipBottom = inspectorClipBottom();
        context.enableScissor(left + 8, inspectorClipTop(), width - 8, clipBottom);
        int top = 61 - contentScroll;
        switch (stage) {
            case PATH -> drawPathStage(context, mouseX, mouseY, left, top);
            case FIGURE -> drawFigureStage(context, mouseX, mouseY, left, top);
            case STRUCTURES -> drawStructuresStage(context, mouseX, mouseY, left, top);
            case MODIFIERS -> drawModifiersStage(context, mouseX, mouseY, left, top);
            case MATERIAL -> drawMaterialStage(context, mouseX, mouseY, left, top);
            case INSTANCES -> drawInstancesStage(context, mouseX, mouseY, left, top);
            case RECIPES -> drawRecipesStage(context, mouseX, mouseY, left, top);
        }
        context.disableScissor();

        int footerTop = height - footerHeight();
        context.fill(left, footerTop, width, height, PANEL_SOFT);
        if (!compactFooter()) {
            context.drawText(textRenderer, tr("gui.komaedit.recipe"), left + 16, footerTop + 10, MUTED, false);
            String estimate = estimateLabel();
            if (!estimate.isEmpty()) {
                int estimateWidth = textRenderer.getWidth(estimate);
                context.drawText(textRenderer, estimate, width - 16 - estimateWidth, footerTop + 10,
                        estimateColor(), false);
            }
        }
        drawNameField(context, mouseX, mouseY, left + 16, nameFieldY(), inspectorWidth - 108, 24);
        drawSmallButton(context, mouseX, mouseY, width - 82, nameFieldY(), 66, 24,
                tr("gui.komaedit.save"), false);
        drawSmallButton(context, mouseX, mouseY, left + 16, height - 44, inspectorWidth - 32, 29,
                buildLabel(tr("gui.komaedit.build_recipe")), pathReady());
    }

    /** En pantallas bajas el pie pierde la etiqueta «RECETA» para dejar sitio a los controles. */
    private boolean compactFooter() {
        return height < 330;
    }

    private int footerHeight() {
        return compactFooter() ? 78 : 100;
    }

    private int nameFieldY() {
        return height - footerHeight() + (compactFooter() ? 8 : 23);
    }

    private int inspectorClipTop() {
        return 52;
    }

    private int inspectorClipBottom() {
        return height - footerHeight() - 5;
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

    private String buildLabel(String ready) {
        if (!pathReady()) {
            return tr("gui.komaedit.add_points");
        }
        if (buildEstimate.armed(System.currentTimeMillis())) {
            return tr("gui.komaedit.build_confirm");
        }
        return ready;
    }

    private void drawPathStage(DrawContext context, int mouseX, int mouseY, int left, int top) {
        int inner = inspectorWidth - 32;
        drawSectionLabel(context, tr("gui.komaedit.path.active"), left + 16, top);
        int nodes = PathHologramRenderer.points().size();
        int minimum = requiredPoints();
        drawCard(context, mouseX, mouseY, left + 16, top + 17, inner, 65, false);
        context.drawText(textRenderer, nodes + " / 32", left + 30, top + 32,
                nodes >= minimum ? SUCCESS : WARNING, false);
        drawTrimmed(context, nodes >= minimum
                        ? tr("gui.komaedit.path.ready")
                        : tr("gui.komaedit.path.minimum", minimum),
                left + 30, top + 48, inner - 28, TEXT);
        drawTrimmed(context, tr("gui.komaedit.path.hint"),
                left + 30, top + 63, inner - 28, MUTED);

        drawSectionLabel(context, tr("gui.komaedit.actions"), left + 16, top + 100);
        int half = (inner - 7) / 2;
        drawSmallButton(context, mouseX, mouseY, left + 16, top + 118, half, 30,
                tr("gui.komaedit.path.add_here"), false);
        drawSmallButton(context, mouseX, mouseY, left + 23 + half, top + 118, half, 30,
                tr("gui.komaedit.path.undo_point"), false);
        drawSmallButton(context, mouseX, mouseY, left + 16, top + 155, half, 30,
                tr("gui.komaedit.path.toggle_closed"), false);
        drawSmallButton(context, mouseX, mouseY, left + 23 + half, top + 155, half, 30,
                tr("gui.komaedit.clear"), true);

        drawSectionLabel(context, tr("gui.komaedit.navigation"), left + 16, top + 211);
        drawCard(context, mouseX, mouseY, left + 16, top + 228, inner, 58, false);
        drawTrimmed(context, tr("gui.komaedit.navigation.hint"),
                left + 28, top + 242, inner - 24, TEXT);
        drawTrimmed(context, tr("gui.komaedit.navigation.reach", 4096),
                left + 28, top + 259, inner - 24, MUTED);
    }

    private void drawFigureStage(DrawContext context, int mouseX, int mouseY, int left, int top) {
        drawShapeStage(context, mouseX, mouseY, left, top, FIGURE_SHAPES, "FIGURAS 3D");
    }

    private void drawStructuresStage(DrawContext context, int mouseX, int mouseY, int left, int top) {
        drawShapeStage(context, mouseX, mouseY, left, top, STRUCTURE_SHAPES, "ESTRUCTURAS");
    }

    private void drawShapeStage(
            DrawContext context,
            int mouseX,
            int mouseY,
            int left,
            int top,
            List<PathShape> shapes,
            String title
    ) {
        int inner = inspectorWidth - 32;
        ShapeLayout layout = shapeLayout(left, top, shapes);
        drawSectionLabel(context, title, left + 16, top);
        if (layout.searchY() >= 0) {
            drawSearchField(context, mouseX, mouseY, left + 16, layout.searchY(), inner, layout.shown().size());
            for (Chip chip : layout.categories()) {
                ShapeCategory value = (ShapeCategory) chip.value();
                drawChip(context, mouseX, mouseY, chip, value == category && shapeSearch.isBlank(),
                        value == ShapeCategory.FAVORITES ? FAVORITE : TEXT);
            }
            if (layout.recentsY() >= 0) {
                context.drawText(textRenderer, tr("gui.komaedit.recents"), left + 16, layout.recentsY() + 4, MUTED, false);
                for (Chip chip : layout.recents()) {
                    drawChip(context, mouseX, mouseY, chip, chip.value() == draft.shape(), TEXT);
                }
            }
        }
        for (int index = 0; index < layout.shown().size(); index++) {
            int x = left + 16 + index % 2 * (layout.buttonWidth() + 7);
            int y = layout.gridY() + index / 2 * 27;
            drawShapeButton(context, mouseX, mouseY, x, y, layout.buttonWidth(), 22, layout.shown().get(index));
        }
        if (layout.shown().isEmpty()) {
            drawCard(context, mouseX, mouseY, left + 16, layout.gridY(), inner, 22, false);
            drawTrimmed(context, tr("gui.komaedit.search.empty"), left + 26, layout.gridY() + 7, inner - 20, MUTED);
        }
        for (int line = 0; line < layout.description().size(); line++) {
            context.drawText(textRenderer, layout.description().get(line), left + 18,
                    layout.descriptionY() + line * 10, MUTED, false);
        }
        drawSectionLabel(context, tr("gui.komaedit.presets"), left + 16, layout.presetsY());
        for (Chip chip : layout.presets()) {
            drawSmallButton(context, mouseX, mouseY, chip.x(), chip.y(), chip.width(), chip.height(), chip.label(), false);
            hotspots.add(Hotspot.tip(chip.x(), chip.y(), chip.width(), chip.height(),
                    chip.value() instanceof ShapeVariants.Preset preset
                            ? tr(preset.translationKey() + ".help")
                            : tr("gui.komaedit.preset.random.help")));
        }
        int controlsTop = layout.controlsTop();
        drawSectionLabel(context, "PROPORCIONES", left + 16, controlsTop);
        boolean torii = draft.shape() == PathShape.TORII;
        boolean monumental = draft.shape() == PathShape.PILLAR || draft.shape() == PathShape.PILLAR_ARCHES;
        boolean mandala = draft.shape() == PathShape.MANDALA;
        boolean web = draft.shape() == PathShape.WEB;
        boolean lightning = draft.shape() == PathShape.LIGHTNING;
        boolean tube = TubeFigures.supports(draft.shape());
        String radiusLabel = tube ? "Radio"
                : lightning ? "Amplitud"
                : web ? "Elasticidad"
                : draft.shape() == PathShape.CLOUDS || mandala ? "Tamaño"
                : torii ? "Grosor" : monumental ? "Ancho" : "Escala";
        String spacingLabel = draft.shape() == PathShape.SWEEP ? "Lados / puntas"
                : draft.shape() == PathShape.EXPRESSION ? "Repeticiones (n)"
                : draft.shape() == PathShape.BRAID ? "Paso de trenza"
                : draft.shape() == PathShape.NOISE_TUBE ? "Frecuencia"
                : lightning ? "Ramificación"
                : web ? "Separación hilos"
                : mandala ? "Simetría"
                : draft.shape() == PathShape.CLOUDS ? "Organicidad"
                : torii || monumental ? "Detalle" : "Espaciado";
        String thicknessLabel = draft.shape() == PathShape.SWEEP || draft.shape() == PathShape.EXPRESSION
                ? "Pared (hueco)"
                : draft.shape() == PathShape.BRAID ? "Grosor hebra"
                : draft.shape() == PathShape.NOISE_TUBE ? "Profundidad"
                : lightning ? "Grosor núcleo"
                : mandala ? "Relieve"
                : draft.shape() == PathShape.CLOUDS ? "Densidad"
                : torii || monumental ? "Relieve" : "Grosor";
        String rollLabel = lightning ? "Caos 3D"
                : web ? "Relieve 3D" : torii ? "Curvatura" : "Giro";
        drawStepper(context, mouseX, mouseY, left + 16, controlsTop + 18, inner, radiusLabel, draft.radius(), Control.RADIUS);
        drawStepper(context, mouseX, mouseY, left + 16, controlsTop + 48, inner, spacingLabel, draft.spacing(), Control.SPACING);
        drawStepper(context, mouseX, mouseY, left + 16, controlsTop + 78, inner, thicknessLabel, draft.thickness(), Control.THICKNESS);
        drawStepper(context, mouseX, mouseY, left + 16, controlsTop + 108, inner,
                rollLabel, draft.rollDegrees(), Control.ROLL);
        if (draft.shape().usesSecondaryMeasure()) {
            String secondaryLabel = draft.shape() == PathShape.CLOUDS ? "Separación" : "Altura";
            drawStepper(context, mouseX, mouseY, left + 16, controlsTop + 138, inner,
                    secondaryLabel, draft.height(), Control.HEIGHT);
        }
        if (tubeRowCount() > 0) {
            drawTubeRows(context, mouseX, mouseY, left + 16, controlsTop + 140, inner);
        }
    }

    /**
     * Filas del espacio de tubo: motor (en las figuras clásicas que tienen versión exacta), perfil o fórmula,
     * relleno, extremos, orientación de la sección y piezas.
     */
    private void drawTubeRows(DrawContext context, int mouseX, int mouseY, int x, int y, int inner) {
        int row = 0;
        if (TubeFigures.hasExactEngine(draft.shape())) {
            drawCycleRow(context, mouseX, mouseY, x, y + row++ * 31, inner, "Motor", tubeEngine().displayName(),
                    TubeEngine.OPTION);
            if (tubeEngine() != TubeEngine.EXACT) {
                return;
            }
        }
        if (draft.shape() == PathShape.EXPRESSION) {
            drawCycleRow(context, mouseX, mouseY, x, y + row++ * 31, inner, "Fórmula",
                    draft.formula().isEmpty() ? formulaPreset().displayName() : "Propia", "preset");
            drawFormulaField(context, mouseX, mouseY, x, y + row++ * 31, inner);
        }
        if (draft.shape() == PathShape.SWEEP) {
            drawCycleRow(context, mouseX, mouseY, x, y + row++ * 31, inner, "Perfil", tubeProfile().displayName(),
                    "profile");
        }
        if (usesHollowRow()) {
            drawCycleRow(context, mouseX, mouseY, x, y + row++ * 31, inner, "Relleno",
                    tubeOption("hollow", 0.0) >= 0.5 ? "Hueco" : "Macizo", "hollow");
        }
        drawCycleRow(context, mouseX, mouseY, x, y + row++ * 31, inner, "Extremos",
                TubeSettings.EndMode.byIndex((int) tubeOption("end", 0.0)).displayName(), "end");
        drawCycleRow(context, mouseX, mouseY, x, y + row++ * 31, inner, "Normales",
                TubeSettings.NormalMode.byIndex((int) tubeOption("normal", 0.0)).displayName(), "normal");
        drawCycleRow(context, mouseX, mouseY, x, y + row++ * 31, inner, "Piezas", smoothMode().shortName(),
                "smooth");
        if (smoothMode() != SmoothMode.NONE) {
            drawCycleRow(context, mouseX, mouseY, x, y + row * 31, inner, "Cobertura",
                    Math.round(coverage() * 100.0) + "%", "coverage");
        }
    }

    private boolean tubeRowsClicked(double mouseX, double mouseY, int x, int y, int inner) {
        int direction = mouseX < x + inner - 80 ? -1 : 1;
        int row = 0;
        if (TubeFigures.hasExactEngine(draft.shape())) {
            if (cycleRowClicked(mouseX, mouseY, x, y + row++ * 31, inner, false)) {
                TubeEngine engine = TubeEngine.byIndex(tubeEngine().ordinal() + direction);
                Map<String, Double> next = new LinkedHashMap<>(draft.options());
                if (engine == TubeEngine.EXACT) {
                    next.put(TubeEngine.OPTION, (double) engine.ordinal());
                } else {
                    next.remove(TubeEngine.OPTION);
                }
                draft = draft.withOptions(next);
                status = engine == TubeEngine.EXACT
                        ? "Motor exacto · sin huecos, con extremos, normales, radios por fotogramas y torsión"
                        : "Motor clásico · la figura se estampa con pinceles";
                return true;
            }
            if (tubeEngine() != TubeEngine.EXACT) {
                return false;
            }
        }
        if (draft.shape() == PathShape.EXPRESSION) {
            if (cycleRowClicked(mouseX, mouseY, x, y + row++ * 31, inner, false)) {
                // Desde una fórmula propia, el primer clic vuelve a la de serie que había debajo.
                TubeFormulaPreset preset = draft.formula().isEmpty()
                        ? TubeFormulaPreset.byIndex(formulaPreset().ordinal() + direction)
                        : formulaPreset();
                setTubeOption("preset", preset.ordinal());
                draft = draft.withFormula("");
                formulaError = null;
                status = "Fórmula " + preset.displayName() + " · " + preset.description();
                return true;
            }
            if (inside(mouseX, mouseY, x, y + row++ * 31, inner, 25)) {
                editingFormula = true;
                if (formulaError == null) {
                    formulaInput = activeFormulaSource();
                }
                status = formulaError == null
                        ? "Escribe la fórmula · Intro la aplica, Esc cancela, Ctrl+V pega"
                        : formulaError;
                return true;
            }
        }
        if (draft.shape() == PathShape.SWEEP) {
            if (cycleRowClicked(mouseX, mouseY, x, y + row++ * 31, inner, false)) {
                TubeSettings.TubeProfile profile = TubeSettings.TubeProfile.byIndex(
                        tubeProfile().ordinal() + direction);
                setTubeOption("profile", profile.ordinal());
                status = "Perfil " + profile.displayName()
                        + (profile.usesSides() ? " · Lados / puntas fija cuántas" : "");
                return true;
            }
        }
        if (usesHollowRow()) {
            if (cycleRowClicked(mouseX, mouseY, x, y + row++ * 31, inner, false)) {
                boolean hollow = tubeOption("hollow", 0.0) < 0.5;
                setTubeOption("hollow", hollow ? 1.0 : 0.0);
                status = hollow ? "Tubo hueco · Pared fija el grosor en bloques" : "Tubo macizo";
                return true;
            }
        }
        if (cycleRowClicked(mouseX, mouseY, x, y + row++ * 31, inner, false)) {
            TubeSettings.EndMode mode = TubeSettings.EndMode.byIndex((int) tubeOption("end", 0.0) + direction);
            setTubeOption("end", mode.ordinal());
            status = "Extremos: " + mode.displayName();
            return true;
        }
        if (cycleRowClicked(mouseX, mouseY, x, y + row++ * 31, inner, false)) {
            TubeSettings.NormalMode mode = TubeSettings.NormalMode.byIndex(
                    (int) tubeOption("normal", 0.0) + direction);
            setTubeOption("normal", mode.ordinal());
            status = switch (mode) {
                case CONSISTENT -> "Normales continuas · la sección rueda con suavidad en las curvas";
                case HORIZONTAL -> "Normales horizontales · la cara de arriba sigue mirando al cielo";
                case UPRIGHT -> "Normales verticales · la sección queda alineada con el eje Y";
            };
            return true;
        }
        if (cycleRowClicked(mouseX, mouseY, x, y + row++ * 31, inner, false)) {
            SmoothMode mode = SmoothMode.byIndex(smoothMode().ordinal() + direction);
            setTubeOption("smooth", mode.ordinal());
            status = switch (mode) {
                case NONE -> "Sin piezas · solo bloques enteros";
                case SLABS -> "Losas en la superficie · mismo material que el patrón";
                case STAIRS -> "Losas y escaleras en la superficie · Cobertura decide cuántas";
            };
            return true;
        }
        if (smoothMode() != SmoothMode.NONE && cycleRowClicked(mouseX, mouseY, x, y + row * 31, inner, false)) {
            double next = Math.clamp(Math.round(coverage() * 10.0 + direction) / 10.0, 0.0, 1.0);
            setTubeOption("coverage", next);
            status = next <= 0.0 ? "Cobertura 0% · solo bloques enteros"
                    : next >= 1.0 ? "Cobertura 100% · toda la superficie con piezas"
                    : "Cobertura " + Math.round(next * 100.0) + "% · más alto, más piezas";
            return true;
        }
        return false;
    }

    private int tubeRowCount() {
        int smoothRows = smoothMode() == SmoothMode.NONE ? 1 : 2;
        if (TubeFigures.hasExactEngine(draft.shape())) {
            // Motor y, con el exacto, extremos, normales y piezas.
            return tubeEngine() == TubeEngine.EXACT ? 3 + smoothRows : 1;
        }
        if (!TubeFigures.supports(draft.shape())) {
            return 0;
        }
        int shapeRows = switch (draft.shape()) {
            case EXPRESSION -> 5;
            case SWEEP -> 4;
            default -> 2;
        };
        return shapeRows + smoothRows;
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

    private boolean usesHollowRow() {
        return draft.shape() == PathShape.SWEEP || draft.shape() == PathShape.EXPRESSION;
    }

    private TubeFormulaPreset formulaPreset() {
        return TubeFormulaPreset.byIndex((int) tubeOption("preset", 0.0));
    }

    /** La fórmula que se está usando: la propia o, si no hay, la de serie. */
    private String activeFormulaSource() {
        return draft.formula().isEmpty() ? formulaPreset().source() : draft.formula();
    }

    /** Campo de fórmula: muestra la activa (gris si es de serie) y, al editar, el final de lo escrito. */
    private void drawFormulaField(DrawContext context, int mouseX, int mouseY, int x, int y, int rowWidth) {
        formulaFieldX = x;
        formulaFieldY = y;
        formulaFieldWidth = rowWidth;
        boolean hovered = inside(mouseX, mouseY, x, y, rowWidth, 25);
        context.fill(x, y, x + rowWidth, y + 25, editingFormula ? CARD_SELECTED : hovered ? CARD_HOVER : CARD);
        if (editingFormula && formulaError != null) {
            context.fill(x, y + 23, x + rowWidth, y + 25, WARNING);
        }
        int textWidth = rowWidth - 16;
        if (editingFormula) {
            String shown = textRenderer.trimToWidth(formulaInput + "_", textWidth, true);
            context.drawText(textRenderer, shown, x + 8, y + 8, TEXT, false);
        } else {
            drawTrimmed(context, activeFormulaSource(), x + 8, y + 8, textWidth,
                    draft.formula().isEmpty() ? MUTED : TEXT);
        }
    }

    private void updateFormulaError() {
        formulaError = formulaInput.isBlank() ? null : TubeFormula.validate(formulaInput);
        status = formulaError == null ? "Fórmula válida · Intro la aplica" : formulaError;
    }

    /** Aplica lo escrito; vacío o igual a la de serie vuelve a la de serie. Si no compila, no cambia nada. */
    private void commitFormula() {
        editingFormula = false;
        String input = formulaInput.trim();
        TubeFormulaPreset preset = formulaPreset();
        formulaError = null;
        if (input.isEmpty() || input.equals(preset.source())) {
            draft = draft.withFormula("");
            status = "Fórmula " + preset.displayName();
            return;
        }
        String error = TubeFormula.validate(input);
        if (error != null) {
            formulaError = error;
            status = "Fórmula sin aplicar · " + error;
            return;
        }
        draft = draft.withFormula(input);
        status = "Fórmula propia aplicada · lo que da > 0 se construye";
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

    private void drawModifiersStage(DrawContext context, int mouseX, int mouseY, int left, int top) {
        int inner = inspectorWidth - 32;
        RecipeExtensions extensions = draft.extensions();
        drawCard(context, mouseX, mouseY, left + 16, top, inner, 88, false);
        drawTrimmed(context, "OPCIONAL · NO CAMBIA LOS PUNTOS", left + 27, top + 10, inner - 22, ACCENT);
        drawTrimmed(context, "Solo deforma el brush elegido desde el inicio al final.",
                left + 27, top + 27, inner - 22, TEXT);
        drawTrimmed(context, "Fusión superpone otra figura cada N tramos.",
                left + 27, top + 42, inner - 22, MUTED);
        drawSmallButton(context, mouseX, mouseY, left + 24, top + 60, inner - 16, 21,
                "RESTABLECER MODIFICADORES", false);
        top += 98;
        drawSectionLabel(context, "CURVA DE PARÁMETROS", left + 16, top);
        drawStepper(context, mouseX, mouseY, left + 16, top + 18, inner,
                "Escala inicial %", extensions.startScalePercent(), Control.START_SCALE);
        drawStepper(context, mouseX, mouseY, left + 16, top + 48, inner,
                "Escala final %", extensions.endScalePercent(), Control.END_SCALE);
        drawStepper(context, mouseX, mouseY, left + 16, top + 78, inner,
                "Espaciado inicial %", extensions.startSpacingPercent(), Control.START_SPACING);
        drawStepper(context, mouseX, mouseY, left + 16, top + 108, inner,
                "Espaciado final %", extensions.endSpacingPercent(), Control.END_SPACING);
        drawStepper(context, mouseX, mouseY, left + 16, top + 138, inner,
                "Torsión total", extensions.twistDegrees(), Control.TWIST);
        drawSmallButton(context, mouseX, mouseY, left + 16, top + 169, inner, 26,
                "CURVA: " + extensions.easing().displayName().toUpperCase(Locale.ROOT), true);

        drawSectionLabel(context, "FUSIÓN PROCEDURAL", left + 16, top + 211);
        drawSmallButton(context, mouseX, mouseY, left + 16, top + 229, inner, 27,
                extensions.rhythmEnabled() ? "FUSIÓN ACTIVADA" : "ACTIVAR FUSIÓN", extensions.rhythmEnabled());
        drawCycleRow(context, mouseX, mouseY, left + 16, top + 263, inner,
                "Figura secundaria", extensions.accentShape().displayName(), "accent");
        drawStepper(context, mouseX, mouseY, left + 16, top + 294, inner,
                "Cada N tramos", extensions.rhythmInterval(), Control.RHYTHM_INTERVAL);

        drawSectionLabel(context, "SURFACE CONFORM", left + 16, top + 339);
        SurfaceMode[] modes = SurfaceMode.values();
        int modeWidth = (inner - 9) / 4;
        for (int index = 0; index < modes.length; index++) {
            drawSmallButton(context, mouseX, mouseY, left + 16 + index * (modeWidth + 3), top + 357,
                    modeWidth, 25, modes[index].displayName(), extensions.surfaceMode() == modes[index]);
        }
        drawStepper(context, mouseX, mouseY, left + 16, top + 389, inner,
                "Alcance", extensions.conformRadius(), Control.CONFORM_RADIUS);
        drawStepper(context, mouseX, mouseY, left + 16, top + 419, inner,
                "Separación", extensions.surfaceOffset(), Control.SURFACE_OFFSET);

        drawSectionLabel(context, "KITBASH / SOCKETS", left + 16, top + 464);
        drawSmallButton(context, mouseX, mouseY, left + 16, top + 482, inner, 27,
                extensions.socketsEnabled() ? "CLIPBOARD KITBASH ACTIVADO" : "USAR CLIPBOARD DE WORLDEDIT",
                extensions.socketsEnabled());
        drawCycleRow(context, mouseX, mouseY, left + 16, top + 516, inner,
                "Colocar", extensions.socketPlacement().displayName(), "socket_placement");
        drawStepper(context, mouseX, mouseY, left + 16, top + 547, inner,
                "Intervalo", extensions.socketInterval(), Control.SOCKET_INTERVAL);

        drawSectionLabel(context, "BUILD QA", left + 16, top + 592);
        int half = (inner - 7) / 2;
        drawSmallButton(context, mouseX, mouseY, left + 16, top + 610, half, 28,
                "CERRAR HUECOS", extensions.closeMicroGaps());
        drawSmallButton(context, mouseX, mouseY, left + 23 + half, top + 610, half, 28,
                "LIMPIAR AISLADOS", extensions.removeIsolated());
    }

    private void drawCycleRow(
            DrawContext context,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int rowWidth,
            String label,
            String value,
            String option
    ) {
        context.fill(x, y, x + rowWidth, y + 25, CARD);
        drawTrimmed(context, label, x + 9, y + 8, Math.min(rowWidth - 124, Math.max(40, rowWidth / 3)), MUTED);
        drawSmallButton(context, mouseX, mouseY, x + rowWidth - 116, y + 3, 22, 19, "‹", false);
        String fitted = textRenderer.trimToWidth(value, 64);
        context.drawText(textRenderer, fitted, x + rowWidth - 60 - textRenderer.getWidth(fitted) / 2, y + 8,
                TEXT, false);
        drawSmallButton(context, mouseX, mouseY, x + rowWidth - 25, y + 3, 22, 19, "›", false);
        registerRow(Hotspot.Kind.CYCLE, x, y, rowWidth, null, option);
    }

    private void drawMaterialStage(DrawContext context, int mouseX, int mouseY, int left, int top) {
        int inner = inspectorWidth - 32;
        drawSectionLabel(context, "MODO DE MATERIAL", left + 16, top);
        int half = (inner - 7) / 2;
        drawSmallButton(context, mouseX, mouseY, left + 16, top + 18, half, 27,
                "BLOQUE", !draft.usesGradient());
        drawSmallButton(context, mouseX, mouseY, left + 23 + half, top + 18, half, 27,
                "DEGRADADO", draft.usesGradient());

        if (!draft.usesGradient()) {
            drawSectionLabel(context, "BLOQUE BASE", left + 16, top + 67);
            drawBlockChoice(context, mouseX, mouseY, left + 16, top + 85, inner, 42);
            drawTrimmed(context, "La biblioteca incluye todos los bloques registrados.",
                    left + 18, top + 139, inner - 4, MUTED);
            return;
        }

        drawSectionLabel(context, "PALETAS PROFESIONALES", left + 16, top + 67);
        GradientPalette[] palettes = GradientPalette.values();
        int paletteWidth = (inner - 7) / 2;
        for (int index = 0; index < palettes.length; index++) {
            int x = left + 16 + index % 2 * (paletteWidth + 7);
            int y = top + 84 + index / 2 * 25;
            boolean selected = palettes[index].id().equals(draft.paletteId());
            drawSmallButton(context, mouseX, mouseY, x, y, paletteWidth, 20,
                    palettes[index].displayName(), selected);
        }
        int controlsTop = top + 84 + ((palettes.length + 1) / 2) * 25 + 8;
        drawSectionLabel(context, "DIRECCIÓN Y MEZCLA", left + 16, controlsTop);
        drawAxisRow(context, mouseX, mouseY, left + 16, controlsTop + 18, inner);
        drawStepper(context, mouseX, mouseY, left + 16, controlsTop + 49, inner,
                "Mezcla orgánica", draft.dither(), Control.DITHER);
        drawSmallButton(context, mouseX, mouseY, left + 16, controlsTop + 81, inner, 26,
                draft.reverse() ? "DIRECCIÓN INVERTIDA" : "INVERTIR DIRECCIÓN", draft.reverse());
        int metricTop = controlsTop + 121;
        drawSectionLabel(context, "DISTRIBUCIÓN GEOMÉTRICA", left + 16, metricTop);
        MaterialMetric[] metrics = MaterialMetric.values();
        int metricWidth = (inner - 7) / 2;
        for (int index = 0; index < metrics.length; index++) {
            int x = left + 16 + index % 2 * (metricWidth + 7);
            int y = metricTop + 18 + index / 2 * 25;
            drawSmallButton(context, mouseX, mouseY, x, y, metricWidth, 20,
                    metrics[index].displayName(), draft.extensions().materialMetric() == metrics[index]);
        }
        int metricRows = (metrics.length + 1) / 2;
        drawTrimmed(context, materialMetricHelp(draft.extensions().materialMetric()), left + 18,
                metricTop + 21 + metricRows * 25, inner - 4, MUTED);
    }

    private void drawInstancesStage(DrawContext context, int mouseX, int mouseY, int left, int top) {
        int inner = inspectorWidth - 32;
        drawSectionLabel(context, "OBJETOS PROCEDURALES", left + 16, top);
        int listTop = top + 18;
        if (!selectedInstanceId.isEmpty()) {
            int half = (inner - 7) / 2;
            drawSmallButton(context, mouseX, mouseY, left + 16, listTop, half, 29,
                    "ACTUALIZAR", true);
            drawSmallButton(context, mouseX, mouseY, left + 23 + half, listTop, half, 29,
                    "ELIMINAR", false);
            listTop += 38;
        }
        List<ProceduralInstanceSummary> instances = ClientInstanceCatalog.instances();
        if (instances.isEmpty()) {
            drawCard(context, mouseX, mouseY, left + 16, listTop, inner, 62, false);
            drawTrimmed(context, "No hay instancias construidas", left + 28, listTop + 16, inner - 24, TEXT);
            drawTrimmed(context, "Cada Build nuevo aparecerá aquí.", left + 28, listTop + 34, inner - 24, MUTED);
            return;
        }
        for (int index = 0; index < instances.size(); index++) {
            ProceduralInstanceSummary instance = instances.get(index);
            int y = listTop + index * 55;
            boolean selected = instance.id().equals(selectedInstanceId);
            drawCard(context, mouseX, mouseY, left + 16, y, inner, 48, selected);
            drawTrimmed(context, instance.recipe().name(), left + 28, y + 9,
                    inner - 28, selected ? ACCENT : TEXT);
            drawTrimmed(context, instance.recipe().shape().displayName() + " · "
                            + instance.blockCount() + " bloques · " + instance.pathPoints() + " nodos",
                    left + 28, y + 27, inner - 28, MUTED);
        }
    }

    private void drawRecipesStage(DrawContext context, int mouseX, int mouseY, int left, int top) {
        int inner = inspectorWidth - 32;
        drawSectionLabel(context, "RECETAS GUARDADAS", left + 16, top);
        int half = (inner - 7) / 2;
        drawSmallButton(context, mouseX, mouseY, left + 16, top + 18, half, 27, "EXPORTAR .KRECIPE", false);
        drawSmallButton(context, mouseX, mouseY, left + 23 + half, top + 18, half, 27, "IMPORTAR .KRECIPE", false);
        int listTop = top + 54;
        List<ProceduralRecipe> recipes = ClientRecipeCatalog.recipes();
        if (recipes.isEmpty()) {
            drawCard(context, mouseX, mouseY, left + 16, listTop, inner, 62, false);
            drawTrimmed(context, "Aún no hay recetas", left + 30, listTop + 16, inner - 28, TEXT);
            drawTrimmed(context, "Configura una figura y usa Guardar.", left + 30, listTop + 33, inner - 28, MUTED);
            return;
        }
        for (int index = 0; index < recipes.size(); index++) {
            ProceduralRecipe recipe = recipes.get(index);
            int y = listTop + index * 49;
            boolean selected = recipe.name().equals(draft.name());
            drawCard(context, mouseX, mouseY, left + 16, y, inner, 42, selected);
            drawTrimmed(context, recipe.name(), left + 28, y + 9, inner - 76, selected ? ACCENT : TEXT);
            drawTrimmed(context, recipe.shape().displayName() + " · " + materialSummary(recipe),
                    left + 28, y + 24, inner - 76, MUTED);
            drawSmallButton(context, mouseX, mouseY, left + inner - 11, y + 9, 28, 24, "×", false);
        }
    }

    private void drawBuildBar(DrawContext context, int mouseX, int mouseY) {
        int right = inspectorLeft() - 12;
        int available = right - 84;
        if (available < 260) {
            return;
        }
        int barWidth = Math.min(520, available);
        int left = 78 + (available - barWidth) / 2;
        int top = height - 56;
        context.fill(left, top, left + barWidth, top + 42, PANEL);
        String estimate = estimateLabel();
        int estimateWidth = estimate.isEmpty() ? 0 : textRenderer.getWidth(estimate) + 8;
        drawTrimmed(context, draft.shape().displayName() + " · " + materialSummary(draft),
                left + 14, top + 9, barWidth - 156 - estimateWidth, TEXT);
        if (!estimate.isEmpty()) {
            context.drawText(textRenderer, estimate, left + barWidth - 142 - estimateWidth + 8, top + 9,
                    estimateColor(), false);
        }
        String detail = buildBarDetail();
        drawTrimmed(context, detail, left + 14, top + 24, barWidth - 156,
                buildEstimate.armed(System.currentTimeMillis()) ? WARNING : MUTED);
        boolean ready = pathReady();
        drawSmallButton(context, mouseX, mouseY, left + barWidth - 132, top + 7, 121, 28,
                buildLabel(tr("gui.komaedit.build")), ready);
        hotspots.add(Hotspot.tip(left + barWidth - 132, top + 7, 121, 28, tr("gui.komaedit.help.build")));
    }

    /** Lo último que pasó: el aviso de obra grande, el estado del holograma o el último mensaje. */
    private String buildBarDetail() {
        if (buildEstimate.armed(System.currentTimeMillis())) {
            return tr("gui.komaedit.build_large", BuildEstimate.compact(ProceduralPreviewRenderer.blockCount()));
        }
        if (!pathReady()) {
            return status;
        }
        if (ProceduralPreviewRenderer.hidden()) {
            return tr("gui.komaedit.hologram.hidden");
        }
        return ProceduralPreviewRenderer.status();
    }

    private void drawNameField(DrawContext context, int mouseX, int mouseY, int x, int y, int fieldWidth, int fieldHeight) {
        boolean hovered = inside(mouseX, mouseY, x, y, fieldWidth, fieldHeight);
        context.fill(x, y, x + fieldWidth, y + fieldHeight, editingName ? CARD_SELECTED : hovered ? CARD_HOVER : CARD);
        String shown = editingName ? nameInput + "_" : nameInput;
        drawTrimmed(context, shown, x + 8, y + 8, fieldWidth - 16, TEXT);
    }

    private void drawBlockChoice(DrawContext context, int mouseX, int mouseY, int x, int y, int cardWidth, int cardHeight) {
        drawCard(context, mouseX, mouseY, x, y, cardWidth, cardHeight, true);
        ItemStack stack = stack(draft.pattern());
        context.drawItem(stack, x + 10, y + 13);
        drawTrimmed(context, "Bloque base · " + compactId(draft.pattern()), x + 34, y + 10, cardWidth - 82, TEXT);
        drawTrimmed(context, "Clic para cambiar bloque", x + 34, y + 25, cardWidth - 82, MUTED);
        context.drawText(textRenderer, "▦", x + cardWidth - 26, y + 17, ACCENT, false);
    }

    private void openBlockCatalog() {
        blockCatalog = true;
        blockSearch = "";
        blockScroll = 0;
    }

    private void drawBlockCatalog(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, width, height, 0xA8000000);
        int modalWidth = Math.min(780, width - 32);
        int modalHeight = Math.min(520, height - 28);
        int left = (width - modalWidth) / 2;
        int top = (height - modalHeight) / 2;
        context.fill(left, top, left + modalWidth, top + modalHeight, 0xFA0C0D12);
        context.fill(left, top, left + 4, top + modalHeight, ACCENT);
        context.drawText(textRenderer, "BIBLIOTECA · BLOQUE BASE",
                left + 18, top + 16, ACCENT, false);
        context.fill(left + 18, top + 36, left + modalWidth - 82, top + 62, CARD);
        drawTrimmed(context, "Buscar: " + blockSearch + "_", left + 28, top + 45, modalWidth - 128, TEXT);
        drawSmallButton(context, mouseX, mouseY, left + modalWidth - 70, top + 36, 52, 26, "SALIR", false);

        List<BlockChoice> blocks = filteredBlocks();
        int columns = Math.max(6, (modalWidth - 36) / 36);
        int rowsVisible = Math.max(2, (modalHeight - 96) / 36);
        int start = blockScroll * columns;
        int end = Math.min(blocks.size(), start + rowsVisible * columns);
        for (int index = start; index < end; index++) {
            int local = index - start;
            int x = left + 18 + local % columns * 36;
            int y = top + 76 + local / columns * 36;
            BlockChoice choice = blocks.get(index);
            boolean hovered = inside(mouseX, mouseY, x, y, 31, 31);
            boolean selected = choice.pattern().equals(draft.pattern());
            context.fill(x, y, x + 31, y + 31, selected ? CARD_SELECTED : hovered ? CARD_HOVER : CARD);
            context.drawItem(choice.stack(), x + 7, y + 7);
            if (hovered) {
                int tooltipWidth = Math.min(260, textRenderer.getWidth(choice.pattern()) + 12);
                context.fill(mouseX + 10, mouseY + 8, mouseX + 10 + tooltipWidth, mouseY + 26, PANEL);
                drawTrimmed(context, choice.pattern(), mouseX + 16, mouseY + 13, tooltipWidth - 12, TEXT);
            }
        }
        drawTrimmed(context, blocks.size() + " bloques · rueda para desplazarte",
                left + 18, top + modalHeight - 17, modalWidth - 36, MUTED);
    }

    private void drawShapeButton(
            DrawContext context,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int buttonWidth,
            int buttonHeight,
            PathShape shape
    ) {
        boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        boolean selected = draft.shape() == shape;
        context.fill(x, y, x + buttonWidth, y + buttonHeight,
                selected ? CARD_SELECTED : hovered ? CARD_HOVER : CARD);
        if (selected) {
            context.fill(x, y, x + 3, y + buttonHeight, ACCENT);
        }
        drawTrimmed(context, shape.displayName(), x + 8, y + 7, buttonWidth - 24, selected ? ACCENT : TEXT);
        boolean favorite = preferences().isFavorite(shape.id());
        if (favorite || hovered) {
            boolean starHovered = hovered && mouseX >= x + buttonWidth - 15;
            context.drawText(textRenderer, favorite ? "★" : "☆", x + buttonWidth - 12, y + 7,
                    favorite || starHovered ? FAVORITE : MUTED, false);
        }
        if (y + buttonHeight > inspectorClipTop() && y < inspectorClipBottom()) {
            hotspots.add(Hotspot.tip(x, y, buttonWidth, buttonHeight,
                    shape.displayName() + "\n" + shape.description() + "\n" + tr("gui.komaedit.help.favorite_star")));
        }
    }

    private void drawStepper(
            DrawContext context,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int rowWidth,
            String label,
            int value,
            Control control
    ) {
        context.fill(x, y, x + rowWidth, y + 25, CARD);
        NumericRange range = range(control);
        // La barra inferior enseña dónde está el valor dentro de su rango, como un deslizador.
        context.fill(x, y + 23, x + rowWidth, y + 25, 0xFF2C2E37);
        context.fill(x, y + 23, x + (int) Math.round(rowWidth * range.fraction(value)), y + 25, ACCENT_DARK);
        drawTrimmed(context, label, x + 9, y + 8, rowWidth - 105, TEXT);
        int minusX = x + rowWidth - 86;
        drawSmallButton(context, mouseX, mouseY, minusX, y + 3, 22, 19, "−", false);
        int valueX = x + rowWidth - 63;
        boolean editing = editingControl == control;
        if (editing || dragControl == control || inside(mouseX, mouseY, valueX, y + 3, 37, 19)) {
            context.fill(valueX, y + 3, valueX + 37, y + 22, editing ? CARD_SELECTED : CARD_HOVER);
        }
        String shown = editing ? textRenderer.trimToWidth(numberInput + "_", 35, true) : Integer.toString(value);
        context.drawCenteredTextWithShadow(textRenderer, shown, valueX + 19, y + 8, editing ? TEXT : ACCENT);
        drawSmallButton(context, mouseX, mouseY, x + rowWidth - 25, y + 3, 22, 19, "+", false);
        registerRow(Hotspot.Kind.STEPPER, x, y, rowWidth, control, null);
    }

    /** Solo cuenta lo que se ve dentro del panel; lo desplazado fuera no responde a la rueda. */
    private void registerRow(Hotspot.Kind kind, int x, int y, int rowWidth, Control control, String option) {
        if (y + 25 > inspectorClipTop() && y < inspectorClipBottom()) {
            hotspots.add(new Hotspot(kind, x, y, rowWidth, 25, control, option, ""));
        }
    }

    private void drawAxisRow(DrawContext context, int mouseX, int mouseY, int x, int y, int rowWidth) {
        GradientAxis[] axes = GradientAxis.values();
        int gap = 4;
        int buttonWidth = (rowWidth - gap * (axes.length - 1)) / axes.length;
        for (int index = 0; index < axes.length; index++) {
            drawSmallButton(context, mouseX, mouseY, x + index * (buttonWidth + gap), y, buttonWidth, 25,
                    axes[index].id().toUpperCase(Locale.ROOT), draft.gradientAxis() == axes[index]);
        }
    }

    private void drawSmallButton(
            DrawContext context,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int buttonWidth,
            int buttonHeight,
            String label,
            boolean selected
    ) {
        boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        context.fill(x, y, x + buttonWidth, y + buttonHeight,
                selected ? ACCENT_DARK : hovered ? CARD_HOVER : CARD);
        int color = selected ? 0xFFFFFFFF : TEXT;
        String fitted = textRenderer.trimToWidth(label, Math.max(1, buttonWidth - 10));
        context.drawCenteredTextWithShadow(textRenderer, fitted, x + buttonWidth / 2,
                y + (buttonHeight - 8) / 2, color);
    }

    private void drawCard(
            DrawContext context,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int cardWidth,
            int cardHeight,
            boolean selected
    ) {
        boolean hovered = inside(mouseX, mouseY, x, y, cardWidth, cardHeight);
        context.fill(x, y, x + cardWidth, y + cardHeight,
                selected ? CARD_SELECTED : hovered ? CARD_HOVER : CARD);
    }

    private void drawPill(
            DrawContext context,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int pillWidth,
            int pillHeight,
            String label,
            boolean active,
            int color
    ) {
        boolean hovered = inside(mouseX, mouseY, x, y, pillWidth, pillHeight);
        context.fill(x, y, x + pillWidth, y + pillHeight, active ? ACCENT_DARK : hovered ? CARD_HOVER : CARD);
        context.drawCenteredTextWithShadow(textRenderer, label, x + pillWidth / 2, y + 6, color);
    }

    private void drawSectionLabel(DrawContext context, String label, int x, int y) {
        context.drawText(textRenderer, label, x, y + 3, MUTED, false);
    }

    private void drawTrimmed(DrawContext context, String value, int x, int y, int maximumWidth, int color) {
        if (maximumWidth <= 0) {
            return;
        }
        context.drawText(textRenderer, textRenderer.trimToWidth(value, maximumWidth), x, y, color, false);
    }

    private void drawStageIcon(DrawContext context, Stage value, int centerX, int centerY, int color) {
        switch (value) {
            case PATH -> {
                context.fill(centerX - 9, centerY - 7, centerX - 5, centerY - 3, color);
                context.fill(centerX - 1, centerY - 2, centerX + 3, centerY + 2, color);
                context.fill(centerX + 7, centerY + 5, centerX + 11, centerY + 9, color);
                context.fill(centerX - 5, centerY - 5, centerX, centerY - 4, color);
                context.fill(centerX + 3, centerY + 3, centerX + 8, centerY + 4, color);
            }
            case FIGURE -> {
                context.drawBorder(centerX - 9, centerY - 8, 18, 16, color);
                context.drawBorder(centerX - 5, centerY - 11, 18, 16, color);
            }
            case STRUCTURES -> {
                context.fill(centerX - 10, centerY - 9, centerX + 11, centerY - 6, color);
                context.fill(centerX - 8, centerY - 6, centerX - 4, centerY + 10, color);
                context.fill(centerX + 5, centerY - 6, centerX + 9, centerY + 10, color);
                context.fill(centerX - 10, centerY + 8, centerX - 2, centerY + 11, color);
                context.fill(centerX + 3, centerY + 8, centerX + 11, centerY + 11, color);
            }
            case MODIFIERS -> {
                context.fill(centerX - 10, centerY - 7, centerX + 10, centerY - 6, color);
                context.fill(centerX - 10, centerY, centerX + 10, centerY + 1, color);
                context.fill(centerX - 10, centerY + 7, centerX + 10, centerY + 8, color);
                context.fill(centerX - 5, centerY - 10, centerX - 2, centerY - 3, color);
                context.fill(centerX + 3, centerY - 3, centerX + 6, centerY + 4, color);
                context.fill(centerX - 1, centerY + 4, centerX + 2, centerY + 11, color);
            }
            case MATERIAL -> {
                context.fill(centerX - 10, centerY - 8, centerX - 3, centerY + 8, 0xFFE24B6A);
                context.fill(centerX - 3, centerY - 8, centerX + 4, centerY + 8, 0xFFF2B94B);
                context.fill(centerX + 4, centerY - 8, centerX + 11, centerY + 8, 0xFF4BC3E2);
            }
            case INSTANCES -> {
                context.drawBorder(centerX - 10, centerY - 8, 14, 14, color);
                context.drawBorder(centerX - 3, centerY - 1, 14, 14, color);
            }
            case RECIPES -> {
                context.drawBorder(centerX - 9, centerY - 10, 18, 20, color);
                context.fill(centerX - 5, centerY - 5, centerX + 6, centerY - 4, color);
                context.fill(centerX - 5, centerY, centerX + 6, centerY + 1, color);
                context.fill(centerX - 5, centerY + 5, centerX + 3, centerY + 6, color);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ProceduralRecipe before = draft;
        Hotspot hotspot = hotspotAt(mouseX, mouseY);
        boolean handled = handleMouseClicked(mouseX, mouseY, button, hotspot);
        track(before, group(hotspot));
        return handled;
    }

    private boolean handleMouseClicked(double mouseX, double mouseY, int button, Hotspot hotspot) {
        if (cheatSheet) {
            cheatSheet = false;
            return true;
        }
        if (editingControl != null) {
            if (hotspot != null && hotspot.control() == editingControl && overValueZone(hotspot, mouseX)) {
                return true;
            }
            commitNumber();
        }
        editingSearch = false;
        if (editingFormula) {
            if (inside(mouseX, mouseY, formulaFieldX, formulaFieldY, formulaFieldWidth, 25)) {
                return true;
            }
            // Pulsar fuera aplica la fórmula antes de atender el clic.
            commitFormula();
        }
        if (blockCatalog) {
            return blockCatalogClicked(mouseX, mouseY, button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(mouseX, mouseY, inspectorLeft() - 4, 0, 8, height)) {
            resizingInspector = true;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hotspot != null && hotspot.kind() != Hotspot.Kind.TIP
                && mouseX >= inspectorLeft()) {
            if (hotspot.control() != null) {
                setValue(hotspot.control(), defaultValue(hotspot.control()));
            } else if (hotspot.option() != null) {
                resetOption(hotspot.option());
            }
            status = tr("gui.komaedit.reset_done");
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && mouseX < inspectorLeft()) {
            cameraDragging = true;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (hotspot != null && hotspot.kind() == Hotspot.Kind.STEPPER
                && inside(mouseX, mouseY, hotspot.x() + hotspot.width() - 63, hotspot.y() + 3, 37, 19)) {
            // Arrastrar en horizontal cambia el valor; un clic sin mover abre el campo para escribirlo.
            dragControl = hotspot.control();
            dragStartX = mouseX;
            dragStartValue = value(dragControl);
            dragMoved = false;
            return true;
        }
        if (topBarClicked(mouseX, mouseY)) {
            return true;
        }
        if (inside(mouseX, mouseY, inspectorLeft(), 0, inspectorWidth, height)) {
            return inspectorClicked(mouseX, mouseY);
        }
        if (stageRailClicked(mouseX, mouseY)) {
            return true;
        }
        if (buildButtonClicked(mouseX, mouseY)) {
            return true;
        }
        if (stage == Stage.PATH && mouseX < inspectorLeft()) {
            Ray ray = rayForMouse(mouseX, mouseY);
            List<BlockPos> points = PathHologramRenderer.points();
            BlockPos selectedPosition = selectedPoint >= 0 && selectedPoint < points.size()
                    ? points.get(selectedPoint)
                    : null;
            int axis = selectedPosition == null ? -1 : pickGizmoAxis(ray, selectedPosition);
            if (axis >= 0 && beginAxisDrag(ray, selectedPosition, axis)) {
                return true;
            }
            int pickedPoint = pickPathPoint(ray);
            if (pickedPoint >= 0) {
                selectedPoint = pickedPoint;
                status = "Nodo " + (selectedPoint + 1) + " seleccionado · arrastra una flecha X, Y o Z";
            } else if (hoverPlacement != null) {
                sendEditor(EditorActionPayload.PATH_ADD, -1, hoverPlacement);
                status = "Punto añadido · " + coordinates(hoverPlacement);
            }
        }
        return true;
    }

    private boolean inspectorClicked(double mouseX, double mouseY) {
        int left = inspectorLeft();
        if (inside(mouseX, mouseY, left + 16, nameFieldY(), inspectorWidth - 108, 24)) {
            editingName = true;
            return true;
        }
        if (inside(mouseX, mouseY, width - 82, nameFieldY(), 66, 24)) {
            saveRecipe();
            return true;
        }
        if (inside(mouseX, mouseY, left + 16, height - 44, inspectorWidth - 32, 29)) {
            performBuild();
            return true;
        }
        editingName = false;
        if (mouseY < inspectorClipTop() || mouseY >= inspectorClipBottom()) {
            // Lo desplazado bajo la cabecera o el pie no se ve: no debe responder.
            return true;
        }
        int top = 61 - contentScroll;
        return switch (stage) {
            case PATH -> pathStageClicked(mouseX, mouseY, left, top);
            case FIGURE -> shapeStageClicked(mouseX, mouseY, left, top, FIGURE_SHAPES);
            case STRUCTURES -> shapeStageClicked(mouseX, mouseY, left, top, STRUCTURE_SHAPES);
            case MODIFIERS -> modifiersStageClicked(mouseX, mouseY, left, top);
            case MATERIAL -> materialStageClicked(mouseX, mouseY, left, top);
            case INSTANCES -> instancesStageClicked(mouseX, mouseY, left, top);
            case RECIPES -> recipesStageClicked(mouseX, mouseY, left, top);
        };
    }

    private boolean pathStageClicked(double mouseX, double mouseY, int left, int top) {
        int inner = inspectorWidth - 32;
        int half = (inner - 7) / 2;
        if (inside(mouseX, mouseY, left + 16, top + 118, half, 30)) {
            sendCommand("komaedit path here");
            return true;
        }
        if (inside(mouseX, mouseY, left + 23 + half, top + 118, half, 30)) {
            sendCommand("komaedit path undo");
            return true;
        }
        if (inside(mouseX, mouseY, left + 16, top + 155, half, 30)) {
            sendCommand("komaedit path close");
            return true;
        }
        if (inside(mouseX, mouseY, left + 23 + half, top + 155, half, 30)) {
            sendCommand("komaedit path clear");
            return true;
        }
        return true;
    }

    private boolean shapeStageClicked(
            double mouseX,
            double mouseY,
            int left,
            int top,
            List<PathShape> shapes
    ) {
        int inner = inspectorWidth - 32;
        ShapeLayout layout = shapeLayout(left, top, shapes);
        if (layout.searchY() >= 0) {
            if (inside(mouseX, mouseY, left + 16, layout.searchY(), inner, 20)) {
                if (!shapeSearch.isEmpty() && mouseX >= left + 16 + inner - 18) {
                    shapeSearch = "";
                    editingSearch = false;
                } else {
                    editingSearch = true;
                    status = tr("gui.komaedit.search.typing");
                }
                return true;
            }
            for (Chip chip : layout.categories()) {
                if (inside(mouseX, mouseY, chip.x(), chip.y(), chip.width(), chip.height())) {
                    category = (ShapeCategory) chip.value();
                    shapeSearch = "";
                    return true;
                }
            }
            for (Chip chip : layout.recents()) {
                if (inside(mouseX, mouseY, chip.x(), chip.y(), chip.width(), chip.height())) {
                    selectShape((PathShape) chip.value());
                    return true;
                }
            }
        }
        for (int index = 0; index < layout.shown().size(); index++) {
            int x = left + 16 + index % 2 * (layout.buttonWidth() + 7);
            int y = layout.gridY() + index / 2 * 27;
            if (inside(mouseX, mouseY, x, y, layout.buttonWidth(), 22)) {
                PathShape shape = layout.shown().get(index);
                if (mouseX >= x + layout.buttonWidth() - 15) {
                    boolean favorite = preferences().toggleFavorite(shape.id());
                    savePreferences();
                    status = tr(favorite ? "gui.komaedit.favorite.added" : "gui.komaedit.favorite.removed",
                            shape.displayName());
                } else {
                    selectShape(shape);
                }
                return true;
            }
        }
        for (Chip chip : layout.presets()) {
            if (inside(mouseX, mouseY, chip.x(), chip.y(), chip.width(), chip.height())) {
                if (chip.value() instanceof ShapeVariants.Preset preset) {
                    applyProportions(ShapeVariants.preset(draft.shape(), preset));
                    status = tr("gui.komaedit.preset.applied", tr(preset.translationKey()));
                } else {
                    randomVariant();
                }
                return true;
            }
        }
        int controlsTop = layout.controlsTop();
        if (stepperClicked(mouseX, mouseY, left + 16, controlsTop + 18, inner, Control.RADIUS)) return true;
        if (stepperClicked(mouseX, mouseY, left + 16, controlsTop + 48, inner, Control.SPACING)) {
            if (draft.options().containsKey("sides")) {
                // El paso del Studio manda sobre los lados fijados por comando.
                Map<String, Double> next = new LinkedHashMap<>(draft.options());
                next.remove("sides");
                draft = draft.withOptions(next);
            }
            return true;
        }
        if (stepperClicked(mouseX, mouseY, left + 16, controlsTop + 78, inner, Control.THICKNESS)) return true;
        if (stepperClicked(mouseX, mouseY, left + 16, controlsTop + 108, inner, Control.ROLL)) return true;
        if (draft.shape().usesSecondaryMeasure()) {
            if (stepperClicked(mouseX, mouseY, left + 16, controlsTop + 138, inner, Control.HEIGHT)) {
                return true;
            }
        }
        if (tubeRowCount() > 0
                && tubeRowsClicked(mouseX, mouseY, left + 16, controlsTop + 140, inner)) {
            return true;
        }
        return true;
    }

    private boolean modifiersStageClicked(double mouseX, double mouseY, int left, int top) {
        int inner = inspectorWidth - 32;
        RecipeExtensions extensions = draft.extensions();
        if (inside(mouseX, mouseY, left + 24, top + 60, inner - 16, 21)) {
            draft = draft.withExtensions(RecipeExtensions.DEFAULT);
            status = "Modificadores restablecidos · una sola figura limpia";
            return true;
        }
        top += 98;
        if (stepperClicked(mouseX, mouseY, left + 16, top + 18, inner, Control.START_SCALE)) return true;
        if (stepperClicked(mouseX, mouseY, left + 16, top + 48, inner, Control.END_SCALE)) return true;
        if (stepperClicked(mouseX, mouseY, left + 16, top + 78, inner, Control.START_SPACING)) return true;
        if (stepperClicked(mouseX, mouseY, left + 16, top + 108, inner, Control.END_SPACING)) return true;
        if (stepperClicked(mouseX, mouseY, left + 16, top + 138, inner, Control.TWIST)) return true;
        if (inside(mouseX, mouseY, left + 16, top + 169, inner, 26)) {
            draft = draft.withExtensions(extensions.withCurve(
                    extensions.startScalePercent(), extensions.endScalePercent(),
                    extensions.startSpacingPercent(), extensions.endSpacingPercent(), extensions.twistDegrees(),
                    cycle(CurveEasing.values(), extensions.easing(), 1)
            ));
            return true;
        }
        if (inside(mouseX, mouseY, left + 16, top + 229, inner, 27)) {
            boolean enabled = !extensions.rhythmEnabled();
            draft = draft.withExtensions(extensions.withRhythm(
                    enabled, extensions.accentShape(), extensions.rhythmInterval()
            ));
            status = enabled
                    ? "Fusión activada · la figura secundaria se superpondrá por intervalos"
                    : "Fusión apagada · se generará únicamente la figura principal";
            return true;
        }
        if (cycleRowClicked(mouseX, mouseY, left + 16, top + 263, inner, false)) {
            int direction = mouseX < left + 16 + inner - 80 ? -1 : 1;
            draft = draft.withExtensions(extensions.withRhythm(
                    extensions.rhythmEnabled(),
                    cycle(MODIFIER_SHAPES, extensions.accentShape(), direction),
                    extensions.rhythmInterval()
            ));
            status = "Figura secundaria cambiada · solo se usa con Fusión activada";
            return true;
        }
        if (stepperClicked(mouseX, mouseY, left + 16, top + 294, inner, Control.RHYTHM_INTERVAL)) return true;
        SurfaceMode[] modes = SurfaceMode.values();
        int modeWidth = (inner - 9) / 4;
        for (int index = 0; index < modes.length; index++) {
            if (inside(mouseX, mouseY, left + 16 + index * (modeWidth + 3), top + 357, modeWidth, 25)) {
                draft = draft.withExtensions(extensions.withSurface(
                        modes[index], extensions.conformRadius(), extensions.surfaceOffset()
                ));
                status = "Conformado: " + modes[index].displayName();
                return true;
            }
        }
        if (stepperClicked(mouseX, mouseY, left + 16, top + 389, inner, Control.CONFORM_RADIUS)) return true;
        if (stepperClicked(mouseX, mouseY, left + 16, top + 419, inner, Control.SURFACE_OFFSET)) return true;
        if (inside(mouseX, mouseY, left + 16, top + 482, inner, 27)) {
            boolean enabled = !extensions.socketsEnabled();
            draft = draft.withExtensions(extensions.withSockets(
                    enabled, extensions.socketPlacement(), extensions.socketInterval()
            ));
            status = enabled ? "Kitbash activado · usará el clipboard de WorldEdit" : "Kitbash apagado";
            return true;
        }
        if (cycleRowClicked(mouseX, mouseY, left + 16, top + 516, inner, false)) {
            int direction = mouseX < left + 16 + inner - 80 ? -1 : 1;
            draft = draft.withExtensions(extensions.withSockets(
                    extensions.socketsEnabled(),
                    cycle(SocketPlacement.values(), extensions.socketPlacement(), direction),
                    extensions.socketInterval()
            ));
            return true;
        }
        if (stepperClicked(mouseX, mouseY, left + 16, top + 547, inner, Control.SOCKET_INTERVAL)) return true;
        int half = (inner - 7) / 2;
        if (inside(mouseX, mouseY, left + 16, top + 610, half, 28)) {
            draft = draft.withExtensions(extensions.withQa(
                    !extensions.closeMicroGaps(), extensions.removeIsolated()
            ));
            return true;
        }
        if (inside(mouseX, mouseY, left + 23 + half, top + 610, half, 28)) {
            draft = draft.withExtensions(extensions.withQa(
                    extensions.closeMicroGaps(), !extensions.removeIsolated()
            ));
        }
        return true;
    }

    private boolean cycleRowClicked(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int rowWidth,
            boolean entireRow
    ) {
        return entireRow
                ? inside(mouseX, mouseY, x, y, rowWidth, 25)
                : inside(mouseX, mouseY, x + rowWidth - 116, y + 3, 22, 19)
                || inside(mouseX, mouseY, x + rowWidth - 25, y + 3, 22, 19);
    }

    private boolean materialStageClicked(double mouseX, double mouseY, int left, int top) {
        int inner = inspectorWidth - 32;
        int half = (inner - 7) / 2;
        if (inside(mouseX, mouseY, left + 16, top + 18, half, 27)) {
            draft = copyMaterial("");
            return true;
        }
        if (inside(mouseX, mouseY, left + 23 + half, top + 18, half, 27)) {
            String palette = draft.usesGradient() ? draft.paletteId() : GradientPalette.GLOW_CYAN.id();
            draft = copyMaterial(palette);
            return true;
        }
        if (!draft.usesGradient()) {
            if (inside(mouseX, mouseY, left + 16, top + 85, inner, 42)) {
                openBlockCatalog();
            }
            return true;
        }
        GradientPalette[] palettes = GradientPalette.values();
        int paletteWidth = (inner - 7) / 2;
        for (int index = 0; index < palettes.length; index++) {
            int x = left + 16 + index % 2 * (paletteWidth + 7);
            int y = top + 84 + index / 2 * 25;
            if (inside(mouseX, mouseY, x, y, paletteWidth, 20)) {
                draft = copyMaterial(palettes[index].id());
                return true;
            }
        }
        int controlsTop = top + 84 + ((palettes.length + 1) / 2) * 25 + 8;
        GradientAxis[] axes = GradientAxis.values();
        int gap = 4;
        int axisWidth = (inner - gap * (axes.length - 1)) / axes.length;
        for (int index = 0; index < axes.length; index++) {
            if (inside(mouseX, mouseY, left + 16 + index * (axisWidth + gap), controlsTop + 18, axisWidth, 25)) {
                draft = draft.copy(draft.name(), draft.shape(), draft.pattern(), draft.radius(), draft.spacing(),
                        draft.thickness(), draft.rollDegrees(), draft.height(), draft.paletteId(), axes[index],
                        draft.dither(), draft.reverse());
                return true;
            }
        }
        if (stepperClicked(mouseX, mouseY, left + 16, controlsTop + 49, inner, Control.DITHER)) return true;
        if (inside(mouseX, mouseY, left + 16, controlsTop + 81, inner, 26)) {
            draft = draft.copy(draft.name(), draft.shape(), draft.pattern(), draft.radius(), draft.spacing(),
                    draft.thickness(), draft.rollDegrees(), draft.height(), draft.paletteId(), draft.gradientAxis(),
                    draft.dither(), !draft.reverse());
        }
        int metricTop = controlsTop + 121;
        MaterialMetric[] metrics = MaterialMetric.values();
        int metricWidth = (inner - 7) / 2;
        for (int index = 0; index < metrics.length; index++) {
            int x = left + 16 + index % 2 * (metricWidth + 7);
            int y = metricTop + 18 + index / 2 * 25;
            if (inside(mouseX, mouseY, x, y, metricWidth, 20)) {
                draft = draft.withExtensions(draft.extensions().withMaterialMetric(metrics[index]));
                return true;
            }
        }
        return true;
    }

    private boolean instancesStageClicked(double mouseX, double mouseY, int left, int top) {
        int inner = inspectorWidth - 32;
        int listTop = top + 18;
        if (!selectedInstanceId.isEmpty()) {
            int half = (inner - 7) / 2;
            if (inside(mouseX, mouseY, left + 16, listTop, half, 29)) {
                commitName();
                ClientPlayNetworking.send(new RecipeActionPayload(
                        RecipeActionPayload.UPDATE_INSTANCE, draft, selectedInstanceId
                ));
                status = "Actualización enviada · conserva la trayectoria original";
                return true;
            }
            if (inside(mouseX, mouseY, left + 23 + half, listTop, half, 29)) {
                ClientPlayNetworking.send(new RecipeActionPayload(
                        RecipeActionPayload.DELETE_INSTANCE, draft, selectedInstanceId
                ));
                selectedInstanceId = "";
                status = "Eliminación de instancia enviada";
                return true;
            }
            listTop += 38;
        }
        List<ProceduralInstanceSummary> instances = ClientInstanceCatalog.instances();
        for (int index = 0; index < instances.size(); index++) {
            int y = listTop + index * 55;
            if (inside(mouseX, mouseY, left + 16, y, inner, 48)) {
                ProceduralInstanceSummary instance = instances.get(index);
                selectedInstanceId = instance.id();
                draft = instance.recipe();
                nameInput = draft.name();
                ClientPlayNetworking.send(new RecipeActionPayload(
                        RecipeActionPayload.LOAD_INSTANCE, draft, selectedInstanceId
                ));
                status = "Instancia seleccionada · cambia parámetros y pulsa Actualizar";
                return true;
            }
        }
        return true;
    }

    private boolean recipesStageClicked(double mouseX, double mouseY, int left, int top) {
        int inner = inspectorWidth - 32;
        int half = (inner - 7) / 2;
        if (inside(mouseX, mouseY, left + 16, top + 18, half, 27)) {
            commitName();
            ClientPlayNetworking.send(new RecipeActionPayload(RecipeActionPayload.EXPORT_PACKAGE, draft));
            status = "Exportación solicitada · config/komaedit/packages";
            return true;
        }
        if (inside(mouseX, mouseY, left + 23 + half, top + 18, half, 27)) {
            commitName();
            ClientPlayNetworking.send(new RecipeActionPayload(RecipeActionPayload.IMPORT_PACKAGE, draft));
            status = "Importación solicitada · " + draft.name() + ".krecipe";
            return true;
        }
        List<ProceduralRecipe> recipes = ClientRecipeCatalog.recipes();
        for (int index = 0; index < recipes.size(); index++) {
            int y = top + 54 + index * 49;
            ProceduralRecipe recipe = recipes.get(index);
            if (inside(mouseX, mouseY, left + inner - 11, y + 9, 28, 24)) {
                ClientPlayNetworking.send(new RecipeActionPayload(RecipeActionPayload.DELETE, recipe));
                status = "Receta eliminada · " + recipe.name();
                return true;
            }
            if (inside(mouseX, mouseY, left + 16, y, inner, 42)) {
                draft = recipe;
                nameInput = recipe.name();
                status = "Receta cargada · " + recipe.name();
                return true;
            }
        }
        return true;
    }

    private boolean stageRailClicked(double mouseX, double mouseY) {
        int top = 54;
        int stride = stageStride();
        int buttonSize = Math.min(42, stride - 4);
        int buttonX = 39 - buttonSize / 2;
        for (int index = 0; index < Stage.values().length; index++) {
            int y = top + 8 + index * stride;
            if (inside(mouseX, mouseY, buttonX, y, buttonSize, buttonSize)) {
                selectStage(Stage.values()[index]);
                return true;
            }
        }
        return false;
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

    /** En pantallas bajas los botones encogen para que la barra lateral no se salga. */
    private int stageStride() {
        int available = Math.max(Stage.values().length * 24, height - 68);
        return Math.clamp(available / Stage.values().length, 24, 54);
    }

    private boolean buildButtonClicked(double mouseX, double mouseY) {
        int right = inspectorLeft() - 12;
        int available = right - 84;
        if (available < 260) {
            return false;
        }
        int barWidth = Math.min(520, available);
        int left = 78 + (available - barWidth) / 2;
        int top = height - 56;
        if (!inside(mouseX, mouseY, left + barWidth - 132, top + 7, 121, 28)) {
            return false;
        }
        performBuild();
        return true;
    }

    private boolean blockCatalogClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        int modalWidth = Math.min(780, width - 32);
        int modalHeight = Math.min(520, height - 28);
        int left = (width - modalWidth) / 2;
        int top = (height - modalHeight) / 2;
        if (inside(mouseX, mouseY, left + modalWidth - 70, top + 36, 52, 26)
                || !inside(mouseX, mouseY, left, top, modalWidth, modalHeight)) {
            blockCatalog = false;
            return true;
        }
        List<BlockChoice> blocks = filteredBlocks();
        int columns = Math.max(6, (modalWidth - 36) / 36);
        int rowsVisible = Math.max(2, (modalHeight - 96) / 36);
        int start = blockScroll * columns;
        int end = Math.min(blocks.size(), start + rowsVisible * columns);
        for (int index = start; index < end; index++) {
            int local = index - start;
            int x = left + 18 + local % columns * 36;
            int y = top + 76 + local / columns * 36;
            if (inside(mouseX, mouseY, x, y, 31, 31)) {
                BlockChoice choice = blocks.get(index);
                draft = draft.copy(draft.name(), draft.shape(), choice.pattern(), draft.radius(), draft.spacing(),
                        draft.thickness(), draft.rollDegrees(), draft.height(), "", draft.gradientAxis(),
                        draft.dither(), draft.reverse());
                blockCatalog = false;
                status = "Bloque base seleccionado · " + compactId(choice.pattern());
                return true;
            }
        }
        return true;
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
            inspectorWidth = Math.clamp(width - (int) Math.round(mouseX), minimumInspectorWidth(), maximumInspectorWidth());
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
                    status = "Nodo " + (draggedPoint + 1) + " · eje "
                            + axisName(draggedAxis) + " · " + coordinates(position);
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
            int modalWidth = Math.min(780, width - 32);
            int modalHeight = Math.min(520, height - 28);
            int columns = Math.max(6, (modalWidth - 36) / 36);
            int rowsVisible = Math.max(2, (modalHeight - 96) / 36);
            int rows = (filteredBlocks().size() + columns - 1) / columns;
            blockScroll = Math.clamp(blockScroll - (int) Math.signum(verticalAmount), 0, Math.max(0, rows - rowsVisible));
            return true;
        }
        if (cheatSheet) {
            return true;
        }
        if (mouseX >= inspectorLeft()) {
            Hotspot hotspot = hotspotAt(mouseX, mouseY);
            if (hotspot != null && hotspot.kind() != Hotspot.Kind.TIP && overValueZone(hotspot, mouseX)
                    && verticalAmount != 0.0 && editingControl == null) {
                ProceduralRecipe before = draft;
                int direction = verticalAmount > 0.0 ? 1 : -1;
                if (hotspot.kind() == Hotspot.Kind.STEPPER) {
                    adjust(hotspot.control(), direction);
                } else {
                    // Igual que pulsar la flecha de ese lado: cada fila decide qué significa avanzar.
                    int arrowX = hotspot.x() + hotspot.width() - (direction < 0 ? 112 : 21);
                    inspectorClicked(arrowX, hotspot.y() + 8);
                }
                track(before, group(hotspot));
                return true;
            }
            contentScroll = Math.clamp(contentScroll - (int) Math.signum(verticalAmount) * 18, 0,
                    maximumContentScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

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
            status = shift ? "Rehacer solicitado" : "Deshacer solicitado";
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_Y) {
            sendEditor(EditorActionPayload.REDO, -1, BlockPos.ORIGIN);
            status = "Rehacer solicitado";
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
            status = "Edición de fórmula cancelada";
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

    private void updateHover(int mouseX, int mouseY) {
        hoverPlacement = null;
        BlockPos overlay = null;
        if (!blockCatalog && stage == Stage.PATH && mouseX < inspectorLeft() && client != null
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
        status = "Ajustando nodo " + (selectedPoint + 1) + " sobre eje " + axisName(axis);
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

    private boolean stepperClicked(double mouseX, double mouseY, int x, int y, int rowWidth, Control control) {
        if (inside(mouseX, mouseY, x + rowWidth - 86, y + 3, 22, 19)) {
            adjust(control, -1);
            return true;
        }
        if (inside(mouseX, mouseY, x + rowWidth - 25, y + 3, 22, 19)) {
            adjust(control, 1);
            return true;
        }
        return false;
    }

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
        };
    }

    private void setValue(Control control, int requested) {
        int next = range(control).clamp(requested);
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
        }
        draft = draft.copy(draft.name(), draft.shape(), draft.pattern(), radius, spacing, thickness, roll, height,
                draft.paletteId(), draft.gradientAxis(), dither, draft.reverse()).withExtensions(extensions);
    }

    /** Clic derecho en una fila de ciclo: quita la opción para que valga la de serie. */
    private void resetOption(String option) {
        RecipeExtensions extensions = draft.extensions();
        switch (option) {
            case "accent" -> draft = draft.withExtensions(extensions.withRhythm(
                    extensions.rhythmEnabled(), RecipeExtensions.DEFAULT.accentShape(), extensions.rhythmInterval()));
            case "socket_placement" -> draft = draft.withExtensions(extensions.withSockets(
                    extensions.socketsEnabled(), RecipeExtensions.DEFAULT.socketPlacement(),
                    extensions.socketInterval()));
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
            status = "Figura nueva · parámetros y modificadores reiniciados";
        }
        preferences().pushRecent(shape.id());
        savePreferences();
    }

    private void toggleHologram() {
        boolean hide = !ProceduralPreviewRenderer.hidden();
        ProceduralPreviewRenderer.setHidden(hide);
        status = tr(hide ? "gui.komaedit.hologram.hidden" : "gui.komaedit.hologram.shown");
    }

    private void saveRecipe() {
        commitName();
        ClientPlayNetworking.send(new RecipeActionPayload(RecipeActionPayload.SAVE, draft));
        status = "Receta guardada · " + draft.name();
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

    private Hotspot hotspotAt(double mouseX, double mouseY) {
        for (int index = hotspots.size() - 1; index >= 0; index--) {
            Hotspot hotspot = hotspots.get(index);
            if (inside(mouseX, mouseY, hotspot.x(), hotspot.y(), hotspot.width(), hotspot.height())) {
                return hotspot;
            }
        }
        return null;
    }

    private static String group(Hotspot hotspot) {
        if (hotspot == null || hotspot.kind() == Hotspot.Kind.TIP) {
            return null;
        }
        return hotspot.control() != null ? hotspot.control().name() : "option:" + hotspot.option();
    }

    /** Zona de manejo de la fila (botones y valor): la rueda solo cambia valores ahí; fuera, desplaza el panel. */
    private static boolean overValueZone(Hotspot hotspot, double mouseX) {
        int zone = hotspot.kind() == Hotspot.Kind.STEPPER ? 88 : 118;
        return mouseX >= hotspot.x() + hotspot.width() - zone;
    }

    private void hotspotTooltip(int mouseX, int mouseY) {
        if (dragControl != null || typing()) {
            return;
        }
        Hotspot hotspot = hotspotAt(mouseX, mouseY);
        if (hotspot == null) {
            return;
        }
        String text = switch (hotspot.kind()) {
            case TIP -> hotspot.text();
            case STEPPER -> {
                if (overValueZone(hotspot, mouseX)) {
                    yield "";
                }
                NumericRange range = range(hotspot.control());
                yield tr(hotspot.control().helpKey) + "\n" + tr("gui.komaedit.help.range", range.min(), range.max(),
                        range.step(), range.bigStep(), defaultValue(hotspot.control()));
            }
            case CYCLE -> overValueZone(hotspot, mouseX) || hotspot.option() == null
                    ? ""
                    : tr("gui.komaedit.help.option." + hotspot.option()) + "\n" + tr("gui.komaedit.help.cycle");
        };
        if (text.isEmpty()) {
            return;
        }
        List<OrderedText> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            lines.addAll(textRenderer.wrapLines(Text.literal(paragraph), 230));
        }
        tooltip = lines;
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
        return Math.max(40, inspectorClipBottom() - inspectorClipTop() - 40);
    }

    private ShapeLayout shapeLayout(int left, int top, List<PathShape> shapes) {
        int inner = inspectorWidth - 32;
        int x = left + 16;
        int y = top + 17;
        int searchY = -1;
        int recentsY = -1;
        List<Chip> categories = List.of();
        List<Chip> recents = List.of();
        List<PathShape> shown = shapes;
        if (shapes == FIGURE_SHAPES) {
            searchY = y;
            y += 25;
            List<Chip> categoryChips = new ArrayList<>();
            for (ShapeCategory value : ShapeCategory.values()) {
                String label = (value == ShapeCategory.FAVORITES ? "★ " : "") + tr(value.translationKey());
                categoryChips.add(new Chip(0, 0, textRenderer.getWidth(label) + 12, 16, label, value));
            }
            categories = flow(categoryChips, x, y, inner, x);
            y = bottom(categories, y) + 6;
            shown = visibleShapes(shapes);
            if (shapeSearch.isBlank() && category == ShapeCategory.ALL) {
                List<Chip> recentChips = new ArrayList<>();
                for (String id : preferences().recents()) {
                    PathShape shape = shapeById(id);
                    if (shape != null && shapes.contains(shape) && recentChips.size() < 4) {
                        recentChips.add(new Chip(0, 0, Math.min(inner / 2,
                                textRenderer.getWidth(shape.displayName()) + 12), 16, shape.displayName(), shape));
                    }
                }
                if (!recentChips.isEmpty()) {
                    recentsY = y;
                    int labelWidth = textRenderer.getWidth(tr("gui.komaedit.recents")) + 6;
                    recents = flow(recentChips, x + labelWidth, y, inner - labelWidth, x + labelWidth);
                    y = bottom(recents, y) + 6;
                }
            }
        }
        int buttonWidth = (inner - 7) / 2;
        int gridY = y;
        y = gridY + Math.max(1, (shown.size() + 1) / 2) * 27 + 4;
        List<OrderedText> description = textRenderer.wrapLines(Text.literal(draft.shape().description()), inner - 4);
        if (description.size() > 3) {
            description = description.subList(0, 3);
        }
        int descriptionY = y;
        y += description.size() * 10 + 8;
        int presetsY = y;
        List<Chip> presets = new ArrayList<>();
        ShapeVariants.Preset[] values = ShapeVariants.Preset.values();
        int count = values.length + 1;
        int presetWidth = (inner - 4 * (count - 1)) / count;
        for (int index = 0; index < count; index++) {
            boolean random = index == values.length;
            String label = random ? "⚄ " + tr("gui.komaedit.preset.random") : tr(values[index].translationKey());
            presets.add(new Chip(x + index * (presetWidth + 4), presetsY + 17, presetWidth, 20, label,
                    random ? null : values[index]));
        }
        int controlsTop = presetsY + 17 + 20 + 12;
        return new ShapeLayout(searchY, categories, recentsY, recents, shown, gridY, buttonWidth,
                descriptionY, description, presetsY, presets, controlsTop);
    }

    /** Coloca botones de ancho variable en líneas, saltando de línea cuando no caben. */
    private static List<Chip> flow(List<Chip> chips, int startX, int y, int maxWidth, int lineStartX) {
        List<Chip> placed = new ArrayList<>();
        int x = startX;
        int right = startX + maxWidth;
        for (Chip chip : chips) {
            int chipWidth = Math.min(chip.width(), right - lineStartX);
            if (x > lineStartX && x + chipWidth > right) {
                x = lineStartX;
                y += chip.height() + 4;
            }
            placed.add(new Chip(x, y, chipWidth, chip.height(), chip.label(), chip.value()));
            x += chipWidth + 4;
        }
        return placed;
    }

    private static int bottom(List<Chip> chips, int fallback) {
        int bottom = fallback;
        for (Chip chip : chips) {
            bottom = Math.max(bottom, chip.y() + chip.height());
        }
        return bottom;
    }

    private List<PathShape> visibleShapes(List<PathShape> shapes) {
        if (!shapeSearch.isBlank()) {
            return ShapeSearch.search(shapes, shapeSearch);
        }
        StudioPreferences current = preferences();
        return shapes.stream().filter(shape -> category.contains(shape, current)).toList();
    }

    private static PathShape shapeById(String id) {
        try {
            return PathShape.byId(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void drawChip(DrawContext context, int mouseX, int mouseY, Chip chip, boolean selected, int color) {
        boolean hovered = inside(mouseX, mouseY, chip.x(), chip.y(), chip.width(), chip.height());
        context.fill(chip.x(), chip.y(), chip.x() + chip.width(), chip.y() + chip.height(),
                selected ? ACCENT_DARK : hovered ? CARD_HOVER : CARD);
        String fitted = textRenderer.trimToWidth(chip.label(), Math.max(1, chip.width() - 8));
        context.drawText(textRenderer, fitted, chip.x() + (chip.width() - textRenderer.getWidth(fitted)) / 2,
                chip.y() + 4, selected ? 0xFFFFFFFF : color, false);
    }

    private void drawSearchField(DrawContext context, int mouseX, int mouseY, int x, int y, int fieldWidth, int results) {
        boolean hovered = inside(mouseX, mouseY, x, y, fieldWidth, 20);
        context.fill(x, y, x + fieldWidth, y + 20, editingSearch ? CARD_SELECTED : hovered ? CARD_HOVER : CARD);
        context.drawText(textRenderer, "⌕", x + 6, y + 6, editingSearch ? ACCENT : MUTED, false);
        int textWidth = fieldWidth - 44;
        if (editingSearch || !shapeSearch.isEmpty()) {
            String shown = textRenderer.trimToWidth(shapeSearch + (editingSearch ? "_" : ""), textWidth, true);
            context.drawText(textRenderer, shown, x + 18, y + 6, TEXT, false);
        } else {
            drawTrimmed(context, tr("gui.komaedit.search.placeholder"), x + 18, y + 6, textWidth, MUTED);
        }
        if (!shapeSearch.isEmpty()) {
            String count = Integer.toString(results);
            context.drawText(textRenderer, count, x + fieldWidth - 20 - textRenderer.getWidth(count), y + 6,
                    MUTED, false);
            context.drawText(textRenderer, "×", x + fieldWidth - 12, y + 6, TEXT, false);
        }
    }

    private void drawCheatSheet(DrawContext context) {
        context.fill(0, 0, width, height, 0xA8000000);
        List<StudioShortcuts.Shortcut> shortcuts = StudioShortcuts.ALL;
        int columns = height < 330 ? 2 : 1;
        int rows = (shortcuts.size() + columns - 1) / columns;
        int keyWidth = 0;
        for (StudioShortcuts.Shortcut shortcut : shortcuts) {
            keyWidth = Math.max(keyWidth, textRenderer.getWidth(tr(shortcut.keysKey())));
        }
        int columnWidth = Math.min(330, (width - 40) / columns);
        int modalWidth = columnWidth * columns + 24;
        int modalHeight = Math.min(height - 16, 46 + rows * 12);
        int left = (width - modalWidth) / 2;
        int top = (height - modalHeight) / 2;
        context.fill(left, top, left + modalWidth, top + modalHeight, 0xFA0C0D12);
        context.fill(left, top, left + 4, top + modalHeight, ACCENT);
        context.drawText(textRenderer, tr("gui.komaedit.shortcuts.title"), left + 16, top + 10, ACCENT, false);
        drawTrimmed(context, tr("gui.komaedit.shortcuts.close"), left + 16, top + modalHeight - 14,
                modalWidth - 32, MUTED);
        int keyColumn = Math.min(keyWidth, columnWidth / 2);
        for (int index = 0; index < shortcuts.size(); index++) {
            StudioShortcuts.Shortcut shortcut = shortcuts.get(index);
            int x = left + 16 + index / rows * columnWidth;
            int y = top + 26 + index % rows * 12;
            if (y > top + modalHeight - 26) {
                continue;
            }
            drawTrimmed(context, tr(shortcut.keysKey()), x, y, keyColumn, TEXT);
            drawTrimmed(context, tr(shortcut.translationKey()), x + keyColumn + 8, y,
                    columnWidth - keyColumn - 16, MUTED);
        }
    }

    private ProceduralRecipe copyMaterial(String palette) {
        return draft.copy(draft.name(), draft.shape(), draft.pattern(), draft.radius(), draft.spacing(),
                draft.thickness(), draft.rollDegrees(), draft.height(), palette, draft.gradientAxis(),
                draft.dither(), draft.reverse());
    }

    private void performBuild() {
        if (!pathReady()) {
            stage = Stage.PATH;
            contentScroll = 0;
            status = "Necesitas al menos " + requiredPoints() + " puntos";
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
        status = "Build enviado · los puntos se conservan hasta pulsar Limpiar";
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
            status = "Nombre ajustado automáticamente · " + nameInput;
        }
        draft = draft.withName(nameInput);
    }

    private int maximumContentScroll() {
        int viewport = Math.max(80, inspectorClipBottom() - 61);
        int content = switch (stage) {
            case PATH -> 315;
            case FIGURE -> figureContentHeight();
            case STRUCTURES -> structuresContentHeight();
            case MODIFIERS -> 723;
            case MATERIAL -> materialContentHeight();
            case INSTANCES -> 70 + ClientInstanceCatalog.instances().size() * 55;
            case RECIPES -> 84 + ClientRecipeCatalog.recipes().size() * 49;
        };
        return Math.max(0, content - viewport);
    }

    private int figureContentHeight() {
        return shapeContentHeight(FIGURE_SHAPES);
    }

    private int structuresContentHeight() {
        return shapeContentHeight(STRUCTURE_SHAPES);
    }

    private int shapeContentHeight(List<PathShape> shapes) {
        int controlsTop = shapeLayout(0, 0, shapes).controlsTop();
        return controlsTop + (draft.shape().usesSecondaryMeasure() ? 180 : 150) + tubeRowCount() * 31;
    }

    private int materialContentHeight() {
        if (!draft.usesGradient()) {
            return 190;
        }
        int paletteRows = (GradientPalette.values().length + 1) / 2;
        int controlsTop = 84 + paletteRows * 25 + 8;
        int metricTop = controlsTop + 121;
        int metricRows = (MaterialMetric.values().length + 1) / 2;
        int metricBottom = metricTop + 18 + Math.max(0, metricRows - 1) * 25 + 20;
        return metricBottom + 40;
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

    private int inspectorLeft() {
        return width - inspectorWidth;
    }

    public int worldContentRight() {
        return inspectorLeft() - 12;
    }

    private int minimumInspectorWidth() {
        return Math.min(284, Math.max(230, width / 4));
    }

    private int maximumInspectorWidth() {
        return Math.max(minimumInspectorWidth(), Math.min(430, width / 2));
    }

    private static String materialSummary(ProceduralRecipe recipe) {
        return recipe.usesGradient() ? recipe.palette().displayName() : compactId(recipe.pattern());
    }

    private static String materialMetricHelp(MaterialMetric metric) {
        return switch (metric) {
            case FIGURE -> "Patrón local adaptado a la geometría de la figura.";
            case MOTIF -> "Reinicia el degradado entre la raíz y el frente de cada motivo.";
            case RELIEF -> "Separa núcleo, profundidad y zonas elevadas.";
            case ALTERNATE -> "Alterna dos rangos de la paleta entre filas consecutivas.";
            default -> "Distribuye la paleta usando " + metric.displayName().toLowerCase(Locale.ROOT) + ".";
        };
    }

    private static String compactId(String value) {
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + ", " + position.getY() + ", " + position.getZ();
    }

    private static boolean down(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int areaWidth, int areaHeight) {
        return mouseX >= x && mouseX < x + areaWidth && mouseY >= y && mouseY < y + areaHeight;
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
        PATH("gui.komaedit.stage.path", "gui.komaedit.stage.path.subtitle"),
        FIGURE("gui.komaedit.stage.figure", "gui.komaedit.stage.figure.subtitle"),
        STRUCTURES("gui.komaedit.stage.structures", "gui.komaedit.stage.structures.subtitle"),
        MODIFIERS("gui.komaedit.stage.modifiers", "gui.komaedit.stage.modifiers.subtitle"),
        MATERIAL("gui.komaedit.stage.material", "gui.komaedit.stage.material.subtitle"),
        INSTANCES("gui.komaedit.stage.instances", "gui.komaedit.stage.instances.subtitle"),
        RECIPES("gui.komaedit.stage.recipes", "gui.komaedit.stage.recipes.subtitle");

        private final String titleKey;
        private final String subtitleKey;

        Stage(String titleKey, String subtitleKey) {
            this.titleKey = titleKey;
            this.subtitleKey = subtitleKey;
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
        SOCKET_INTERVAL(new NumericRange(2, 64, 1, 8));

        private final NumericRange range;
        private final String helpKey;

        Control(NumericRange range) {
            this.range = range;
            this.helpKey = "gui.komaedit.help." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Zona de la interfaz dibujada en el último fotograma que responde a rueda, clic derecho o ayuda. */
    private record Hotspot(Kind kind, int x, int y, int width, int height, Control control, String option, String text) {
        private enum Kind {
            STEPPER,
            CYCLE,
            TIP
        }

        private static Hotspot tip(int x, int y, int width, int height, String text) {
            return new Hotspot(Kind.TIP, x, y, width, height, null, null, text);
        }
    }

    private record Chip(int x, int y, int width, int height, String label, Object value) {
    }

    private record ShapeLayout(
            int searchY,
            List<Chip> categories,
            int recentsY,
            List<Chip> recents,
            List<PathShape> shown,
            int gridY,
            int buttonWidth,
            int descriptionY,
            List<OrderedText> description,
            int presetsY,
            List<Chip> presets,
            int controlsTop
    ) {
    }

    private record TopPill(TopAction action, int x, int width, String label) {
    }

    /** Botones de la barra superior en orden de derecha a izquierda. */
    private enum TopAction {
        CLOSE("gui.komaedit.help.top.close"),
        HELP("gui.komaedit.help.top.help"),
        HOLOGRAM("gui.komaedit.help.top.hologram"),
        REDO("gui.komaedit.help.top.redo"),
        UNDO("gui.komaedit.help.top.undo"),
        NODES("gui.komaedit.help.top.nodes");

        private final String helpKey;

        TopAction(String helpKey) {
            this.helpKey = helpKey;
        }
    }

}
