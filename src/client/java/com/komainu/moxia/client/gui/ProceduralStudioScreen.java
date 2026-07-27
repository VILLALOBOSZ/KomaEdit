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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
    private String blockSearch = "";
    private String status = "Studio listo · crea una trayectoria y construye una receta";
    private String selectedInstanceId = "";
    private BlockPos hoverPlacement;
    private BlockPos lastDraggedPosition;

    public ProceduralStudioScreen() {
        super(Text.literal("KomaEdit Procedural Studio"));
    }

    public static void resetSession() {
        rememberedDraft = ProceduralRecipe.defaults();
        previewDraft = rememberedDraft;
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
        updateMovementKeys();
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
        previewDraft = draft;
        releaseMovementKeys();
        EditorOverlayRenderer.clear();
        sendCommand("komaedit path mode off");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        contentScroll = Math.clamp(contentScroll, 0, maximumContentScroll());
        updateHover(mouseX, mouseY);
        drawTopBar(context, mouseX, mouseY);
        drawStageRail(context, mouseX, mouseY);
        drawInspector(context, mouseX, mouseY);
        drawBuildBar(context, mouseX, mouseY);
        if (blockCatalog) {
            drawBlockCatalog(context, mouseX, mouseY);
        }
    }

    private void drawTopBar(DrawContext context, int mouseX, int mouseY) {
        int right = inspectorLeft() - 10;
        context.fill(12, 10, right, 42, PANEL);
        context.fill(12, 10, 16, 42, ACCENT);
        drawTrimmed(context, "KOMAEDIT", 24, 17, 95, TEXT);
        drawTrimmed(context, "PROCEDURAL STUDIO", 102, 17, Math.max(90, right - 260), MUTED);
        String nodes = PathHologramRenderer.points().size() + " nodos";
        int nodeWidth = textRenderer.getWidth(nodes) + 18;
        drawPill(context, mouseX, mouseY, Math.max(160, right - nodeWidth - 66), 16, nodeWidth, 20,
                nodes, pathReady());
        drawPill(context, mouseX, mouseY, right - 52, 16, 42, 20, "K ×", false);
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
                int width = textRenderer.getWidth(value.title) + 16;
                int tooltipY = y + Math.max(4, (buttonSize - 21) / 2);
                context.fill(66, tooltipY, 66 + width, tooltipY + 21, PANEL);
                context.drawText(textRenderer, value.title, 74, tooltipY + 7, TEXT, false);
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

        context.drawText(textRenderer, stage.title.toUpperCase(Locale.ROOT), left + 18, 17, ACCENT, false);
        drawTrimmed(context, stage.subtitle, left + 18, 31, inspectorWidth - 36, MUTED);
        context.fill(left + 14, 49, width - 14, 50, 0xFF343640);

        int clipBottom = height - 105;
        context.enableScissor(left + 8, 52, width - 8, clipBottom);
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

        context.fill(left, height - 100, width, height, PANEL_SOFT);
        context.drawText(textRenderer, "RECETA", left + 16, height - 90, MUTED, false);
        drawNameField(context, mouseX, mouseY, left + 16, height - 77, inspectorWidth - 108, 24);
        drawSmallButton(context, mouseX, mouseY, width - 82, height - 77, 66, 24, "GUARDAR", false);
        boolean ready = pathReady();
        drawSmallButton(context, mouseX, mouseY, left + 16, height - 44, inspectorWidth - 32, 29,
                ready ? "CONSTRUIR RECETA" : "AÑADE PUNTOS", ready);
    }

    private void drawPathStage(DrawContext context, int mouseX, int mouseY, int left, int top) {
        int inner = inspectorWidth - 32;
        drawSectionLabel(context, "TRAYECTORIA ACTIVA", left + 16, top);
        int nodes = PathHologramRenderer.points().size();
        int minimum = requiredPoints();
        drawCard(context, mouseX, mouseY, left + 16, top + 17, inner, 65, false);
        context.drawText(textRenderer, nodes + " / 32", left + 30, top + 32,
                nodes >= minimum ? SUCCESS : WARNING, false);
        drawTrimmed(context, nodes >= minimum ? "Lista para construir" : "Añade al menos " + minimum + " puntos",
                left + 30, top + 48, inner - 28, TEXT);
        drawTrimmed(context, "Clic en el mundo: punto · arrastra un nodo: mover",
                left + 30, top + 63, inner - 28, MUTED);

        drawSectionLabel(context, "ACCIONES", left + 16, top + 100);
        int half = (inner - 7) / 2;
        drawSmallButton(context, mouseX, mouseY, left + 16, top + 118, half, 30, "+ AQUÍ", false);
        drawSmallButton(context, mouseX, mouseY, left + 23 + half, top + 118, half, 30, "DESHACER PUNTO", false);
        drawSmallButton(context, mouseX, mouseY, left + 16, top + 155, half, 30, "ABRIR / CERRAR", false);
        drawSmallButton(context, mouseX, mouseY, left + 23 + half, top + 155, half, 30, "LIMPIAR", true);

        drawSectionLabel(context, "NAVEGACIÓN", left + 16, top + 211);
        drawCard(context, mouseX, mouseY, left + 16, top + 228, inner, 58, false);
        drawTrimmed(context, "WASD para moverte · clic derecho y arrastre para mirar",
                left + 28, top + 242, inner - 24, TEXT);
        drawTrimmed(context, "Alcance de edición: 4096 bloques cargados",
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
        drawSectionLabel(context, title, left + 16, top);
        int buttonWidth = (inner - 7) / 2;
        for (int index = 0; index < shapes.size(); index++) {
            int x = left + 16 + index % 2 * (buttonWidth + 7);
            int y = top + 17 + index / 2 * 27;
            drawShapeButton(context, mouseX, mouseY, x, y, buttonWidth, 22, shapes.get(index));
        }
        int controlsTop = top + 17 + ((shapes.size() + 1) / 2) * 27 + 8;
        drawTrimmed(context, draft.shape().description(), left + 18, controlsTop,
                inner - 4, MUTED);
        controlsTop += 23;
        drawSectionLabel(context, "PROPORCIONES", left + 16, controlsTop);
        boolean torii = draft.shape() == PathShape.TORII;
        boolean monumental = draft.shape() == PathShape.PILLAR || draft.shape() == PathShape.PILLAR_ARCHES;
        boolean mandala = draft.shape() == PathShape.MANDALA;
        boolean web = draft.shape() == PathShape.WEB;
        String radiusLabel = web ? "Apertura"
                : draft.shape() == PathShape.CLOUDS || mandala ? "Tamaño"
                : torii ? "Grosor" : monumental ? "Ancho" : "Escala";
        String spacingLabel = web ? "Separación hilos"
                : mandala ? "Simetría"
                : draft.shape() == PathShape.CLOUDS ? "Organicidad"
                : torii || monumental ? "Detalle" : "Espaciado";
        String thicknessLabel = mandala ? "Relieve"
                : draft.shape() == PathShape.CLOUDS ? "Densidad"
                : torii || monumental ? "Relieve" : "Grosor";
        String rollLabel = web ? "Giro / espiral" : torii ? "Curvatura" : "Giro";
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
                "Figura secundaria", extensions.accentShape().displayName());
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
                "Colocar", extensions.socketPlacement().displayName());
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
            String value
    ) {
        context.fill(x, y, x + rowWidth, y + 25, CARD);
        drawTrimmed(context, label, x + 9, y + 8, Math.max(40, rowWidth / 3), MUTED);
        drawSmallButton(context, mouseX, mouseY, x + rowWidth - 116, y + 3, 22, 19, "‹", false);
        drawTrimmed(context, value, x + rowWidth - 89, y + 8, 60, TEXT);
        drawSmallButton(context, mouseX, mouseY, x + rowWidth - 25, y + 3, 22, 19, "›", false);
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
        drawTrimmed(context, draft.shape().displayName() + " · " + materialSummary(draft),
                left + 14, top + 9, barWidth - 156, TEXT);
        String detail = pathReady()
                ? ProceduralPreviewRenderer.status()
                : status;
        drawTrimmed(context, detail, left + 14, top + 24, barWidth - 156, MUTED);
        boolean ready = pathReady();
        drawSmallButton(context, mouseX, mouseY, left + barWidth - 132, top + 7, 121, 28,
                ready ? "CONSTRUIR" : "FALTAN PUNTOS", ready);
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
        drawTrimmed(context, shape.displayName(), x + 8, y + 7, buttonWidth - 14, selected ? ACCENT : TEXT);
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
        drawTrimmed(context, label, x + 9, y + 8, rowWidth - 105, TEXT);
        int minusX = x + rowWidth - 86;
        drawSmallButton(context, mouseX, mouseY, minusX, y + 3, 22, 19, "−", false);
        context.drawCenteredTextWithShadow(textRenderer, Integer.toString(value), x + rowWidth - 49, y + 8, ACCENT);
        drawSmallButton(context, mouseX, mouseY, x + rowWidth - 25, y + 3, 22, 19, "+", false);
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
            boolean active
    ) {
        boolean hovered = inside(mouseX, mouseY, x, y, pillWidth, pillHeight);
        context.fill(x, y, x + pillWidth, y + pillHeight, active ? ACCENT_DARK : hovered ? CARD_HOVER : CARD);
        context.drawCenteredTextWithShadow(textRenderer, label, x + pillWidth / 2, y + 6, active ? SUCCESS : TEXT);
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
        if (blockCatalog) {
            return blockCatalogClicked(mouseX, mouseY, button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(mouseX, mouseY, inspectorLeft() - 4, 0, 8, height)) {
            resizingInspector = true;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && mouseX < inspectorLeft()) {
            cameraDragging = true;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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
        if (inside(mouseX, mouseY, left + 16, height - 77, inspectorWidth - 108, 24)) {
            editingName = true;
            return true;
        }
        if (inside(mouseX, mouseY, width - 82, height - 77, 66, 24)) {
            commitName();
            ClientPlayNetworking.send(new RecipeActionPayload(RecipeActionPayload.SAVE, draft));
            status = "Receta guardada · " + draft.name();
            return true;
        }
        if (inside(mouseX, mouseY, left + 16, height - 44, inspectorWidth - 32, 29)) {
            performBuild();
            return true;
        }
        editingName = false;
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
        int buttonWidth = (inner - 7) / 2;
        for (int index = 0; index < shapes.size(); index++) {
            int x = left + 16 + index % 2 * (buttonWidth + 7);
            int y = top + 17 + index / 2 * 27;
            if (inside(mouseX, mouseY, x, y, buttonWidth, 22)) {
                PathShape shape = shapes.get(index);
                if (draft.shape() != shape) {
                    draft = draft.withShapeDefaults(shape);
                    status = "Figura nueva · parámetros y modificadores reiniciados";
                }
                return true;
            }
        }
        int controlsTop = top + 17 + ((shapes.size() + 1) / 2) * 27 + 31;
        if (stepperClicked(mouseX, mouseY, left + 16, controlsTop + 18, inner, Control.RADIUS)) return true;
        if (stepperClicked(mouseX, mouseY, left + 16, controlsTop + 48, inner, Control.SPACING)) return true;
        if (stepperClicked(mouseX, mouseY, left + 16, controlsTop + 78, inner, Control.THICKNESS)) return true;
        if (stepperClicked(mouseX, mouseY, left + 16, controlsTop + 108, inner, Control.ROLL)) return true;
        if (draft.shape().usesSecondaryMeasure()) {
            if (stepperClicked(mouseX, mouseY, left + 16, controlsTop + 138, inner, Control.HEIGHT)) {
                return true;
            }
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
                stage = Stage.values()[index];
                contentScroll = 0;
                if (stage == Stage.STRUCTURES && !STRUCTURE_SHAPES.contains(draft.shape())) {
                    draft = draft.withShapeDefaults(STRUCTURE_SHAPES.getFirst());
                } else if (stage == Stage.FIGURE && !FIGURE_SHAPES.contains(draft.shape())) {
                    draft = draft.withShapeDefaults(FIGURE_SHAPES.getFirst());
                }
                status = stage.subtitle;
                return true;
            }
        }
        return false;
    }

    private int stageStride() {
        int available = Math.max(272, height - 68);
        return Math.clamp(available / Stage.values().length, 34, 54);
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
        if (mouseX >= inspectorLeft()) {
            contentScroll = Math.clamp(contentScroll - (int) Math.signum(verticalAmount) * 18, 0, maximumContentScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
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
        if (blockCatalog) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                blockCatalog = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !blockSearch.isEmpty()) {
                blockSearch = blockSearch.substring(0, blockSearch.length() - 1);
                blockScroll = 0;
                return true;
            }
        }
        if (editingName) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitName();
                editingName = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !nameInput.isEmpty()) {
                nameInput = nameInput.substring(0, nameInput.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nameInput = draft.name();
                editingName = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (blockCatalog && blockSearch.length() < 64
                && (Character.isLetterOrDigit(chr) || chr == '_' || chr == ':' || chr == '-')) {
            blockSearch += Character.toLowerCase(chr);
            blockScroll = 0;
            return true;
        }
        if (editingName && nameInput.length() < 32
                && (Character.isLetterOrDigit(chr) || chr == '_' || chr == '-')) {
            nameInput += chr;
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
        int radius = draft.radius();
        int spacing = draft.spacing();
        int thickness = draft.thickness();
        int roll = draft.rollDegrees();
        int height = draft.height();
        int dither = draft.dither();
        RecipeExtensions extensions = draft.extensions();
        switch (control) {
            case RADIUS -> radius = Math.clamp(radius + direction, 1, 128);
            case SPACING -> spacing = Math.clamp(spacing + direction, 2, 8);
            case THICKNESS -> thickness = Math.clamp(thickness + direction, 1, 4);
            case ROLL -> roll = Math.clamp(roll + direction * 5, -180, 180);
            case HEIGHT -> {
                int step = draft.shape() == PathShape.TORII ? 4 : 1;
                height = Math.clamp(height + direction * step, 3, 256);
            }
            case DITHER -> dither = Math.clamp(dither + direction * 5, 0, 100);
            case START_SCALE -> extensions = extensions.withCurve(
                    Math.clamp(extensions.startScalePercent() + direction * 5, 25, 300),
                    extensions.endScalePercent(), extensions.startSpacingPercent(), extensions.endSpacingPercent(),
                    extensions.twistDegrees(), extensions.easing());
            case END_SCALE -> extensions = extensions.withCurve(
                    extensions.startScalePercent(),
                    Math.clamp(extensions.endScalePercent() + direction * 5, 25, 300),
                    extensions.startSpacingPercent(), extensions.endSpacingPercent(), extensions.twistDegrees(),
                    extensions.easing());
            case START_SPACING -> extensions = extensions.withCurve(
                    extensions.startScalePercent(), extensions.endScalePercent(),
                    Math.clamp(extensions.startSpacingPercent() + direction * 5, 50, 200),
                    extensions.endSpacingPercent(), extensions.twistDegrees(), extensions.easing());
            case END_SPACING -> extensions = extensions.withCurve(
                    extensions.startScalePercent(), extensions.endScalePercent(), extensions.startSpacingPercent(),
                    Math.clamp(extensions.endSpacingPercent() + direction * 5, 50, 200),
                    extensions.twistDegrees(), extensions.easing());
            case TWIST -> extensions = extensions.withCurve(
                    extensions.startScalePercent(), extensions.endScalePercent(), extensions.startSpacingPercent(),
                    extensions.endSpacingPercent(),
                    Math.clamp(extensions.twistDegrees() + direction * 15, -1440, 1440), extensions.easing());
            case RHYTHM_INTERVAL -> extensions = extensions.withRhythm(
                    extensions.rhythmEnabled(), extensions.accentShape(),
                    Math.clamp(extensions.rhythmInterval() + direction, 2, 32));
            case CONFORM_RADIUS -> extensions = extensions.withSurface(
                    extensions.surfaceMode(), Math.clamp(extensions.conformRadius() + direction, 2, 32),
                    extensions.surfaceOffset());
            case SURFACE_OFFSET -> extensions = extensions.withSurface(
                    extensions.surfaceMode(), extensions.conformRadius(),
                    Math.clamp(extensions.surfaceOffset() + direction, -8, 8));
            case SOCKET_INTERVAL -> extensions = extensions.withSockets(
                    extensions.socketsEnabled(), extensions.socketPlacement(),
                    Math.clamp(extensions.socketInterval() + direction, 2, 64));
        }
        draft = draft.copy(draft.name(), draft.shape(), draft.pattern(), radius, spacing, thickness, roll, height,
                draft.paletteId(), draft.gradientAxis(), dither, draft.reverse()).withExtensions(extensions);
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
        int viewport = Math.max(80, height - 166);
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
        return shapeContentHeight(FIGURE_SHAPES.size());
    }

    private int structuresContentHeight() {
        return shapeContentHeight(STRUCTURE_SHAPES.size());
    }

    private int shapeContentHeight(int shapeCount) {
        int rows = (shapeCount + 1) / 2;
        int controlsTop = 17 + rows * 27 + 31;
        return controlsTop + (draft.shape().usesSecondaryMeasure() ? 180 : 150);
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

    private record BlockChoice(String pattern, ItemStack stack) {
    }

    private record Ray(Vec3d origin, Vec3d direction) {
    }

    private record AxisProjection(double rayDistance, double axisDistance, double separation) {
    }

    private enum Stage {
        PATH("Trayectoria", "Diseña el recorrido en el mundo"),
        FIGURE("Figura", "Escoge una gramática 3D"),
        STRUCTURES("Estructuras", "Pilares, puentes y portales monumentales"),
        MODIFIERS("Deformar", "Opcional · no edita la trayectoria del brush"),
        MATERIAL("Estilo", "Material sólido o degradado integrado"),
        INSTANCES("Instancias", "Actualiza construcciones sin rehacerlas"),
        RECIPES("Recetas", "Guarda, carga y reutiliza sistemas");

        private final String title;
        private final String subtitle;

        Stage(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private enum Control {
        RADIUS,
        SPACING,
        THICKNESS,
        ROLL,
        HEIGHT,
        DITHER,
        START_SCALE,
        END_SCALE,
        START_SPACING,
        END_SPACING,
        TWIST,
        RHYTHM_INTERVAL,
        CONFORM_RADIUS,
        SURFACE_OFFSET,
        SOCKET_INTERVAL
    }

}
