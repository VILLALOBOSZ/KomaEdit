package com.komainu.moxia.client.gui;

import com.komainu.moxia.client.EditorViewportState;
import com.komainu.moxia.client.preview.EditorOverlayRenderer;
import com.komainu.moxia.client.preview.PathHologramRenderer;
import com.komainu.moxia.client.mixin.GameRendererAccessor;
import com.komainu.moxia.editor.EditorActionPayload;
import com.komainu.moxia.gradient.GradientPalette;
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
import org.lwjgl.glfw.GLFW;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;

public final class LiveBuildScreen extends Screen {
    private static final int PANEL = 0xF50A0709;
    private static final int PANEL_INNER = 0xF8180B10;
    private static final int ROW = 0xFF241117;
    private static final int ROW_HOVER = 0xFF371721;
    private static final int ROW_SELECTED = 0xFF552031;
    private static final int BORDER = 0xFF782A3C;
    private static final int ACCENT = 0xFFCA4B65;
    private static final int TEXT = 0xFFE9DDE0;
    private static final int MUTED = 0xFFB78D96;
    private static final int SUCCESS = 0xFF72D99A;
    private static final double EDITOR_REACH = 4_096.0;

    private static final List<BlockChoice> BLOCKS = List.of(
            block("stone"), block("stone_bricks"), block("cobblestone"), block("mossy_cobblestone"),
            block("deepslate_tiles"), block("deepslate_bricks"), block("polished_blackstone_bricks"), block("blackstone"),
            block("tuff_bricks"), block("calcite"), block("quartz_block"), block("smooth_quartz"),
            block("prismarine_bricks"), block("dark_prismarine"), block("warped_wart_block"), block("crimson_nylium"),
            block("red_nether_bricks"), block("magma_block"), block("orange_concrete"), block("yellow_concrete"),
            block("lime_concrete"), block("cyan_concrete"), block("blue_concrete"), block("purple_concrete"),
            block("magenta_concrete"), block("red_concrete"), block("white_concrete"), block("black_concrete"),
            block("copper_block"), block("oxidized_copper"), block("sea_lantern"), block("glowstone")
    );
    private static final List<BlockChoice> ALL_BLOCKS = Registries.BLOCK.getIds().stream()
            .sorted(Comparator.comparing(Identifier::toString))
            .map(id -> {
                Block block = Registries.BLOCK.get(id);
                ItemStack stack = block.asItem().getDefaultStack();
                if (stack.isEmpty()) {
                    stack = Items.BARRIER.getDefaultStack();
                }
                return new BlockChoice(id.toString(), stack);
            })
            .toList();

    private static int rememberedLeftWidth = 255;
    private static int rememberedRightWidth = 265;

    private Tool selectedTool = Tool.PATH;
    private PathShape selectedShape = PathShape.SCALES;
    private String selectedPattern = "stone";
    private int radius = 12;
    private int spacing = 8;
    private int thickness = 2;
    private int roll;
    private int paintRadius = 4;
    private int leftWidth;
    private int rightWidth;
    private boolean resizingLeft;
    private boolean resizingRight;
    private boolean cameraDragging;
    private boolean paintDragging;
    private boolean magicArea = true;
    private int draggingPathPoint = -1;
    private int draggingBoxPoint = -1;
    private int selectedBoxPoint = -1;
    private int gizmoAxis = -1;
    private BlockPos boxFirst;
    private BlockPos boxSecond;
    private BlockPos hoverPlacement;
    private BlockPos hoverBlock;
    private BlockPos pasteOrigin;
    private BlockPos lastSentPosition;
    private long lastPaintAt;
    private boolean blockModal;
    private boolean selectionModal;
    private boolean pasteMode;
    private boolean clipboardReady;
    private String blockSearch = "";
    private int blockModalScroll;
    private String hoveredBlockId = "";
    private String status = "Path listo · mueve el cursor sobre el terreno";

    public LiveBuildScreen() {
        super(Text.literal("KomaEdit Studio"));
    }

    @Override
    protected void init() {
        leftWidth = Math.clamp(rememberedLeftWidth, minimumSideWidth(), maximumSideWidth());
        rightWidth = Math.clamp(rememberedRightWidth, minimumSideWidth(), maximumSideWidth());
        updateEditorViewport();
        if (client != null && client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatCommand("komaedit path mode on");
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        updateMovementKeys();
    }

    @Override
    public void removed() {
        cameraDragging = false;
        EditorViewportState.clear();
        EditorOverlayRenderer.clear();
        releaseMovementKeys();
        rememberedLeftWidth = leftWidth;
        rememberedRightWidth = rightWidth;
        if (client != null && client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatCommand("komaedit path mode off");
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateEditorViewport();
        updateHover(mouseX, mouseY);
        drawLeftPanel(context, mouseX, mouseY);
        drawRightPanel(context, mouseX, mouseY);
        drawViewportHud(context);
        if (blockModal) {
            drawBlockModal(context, mouseX, mouseY);
        } else if (selectionModal) {
            drawSelectionModal(context, mouseX, mouseY);
        }
    }

    private void drawLeftPanel(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, leftWidth, height, PANEL);
        context.fill(leftWidth - 3, 0, leftWidth, height, BORDER);
        context.fill(8, 8, leftWidth - 10, 35, PANEL_INNER);
        drawFittedText(context, Text.literal("KOMAEDIT // STUDIO"), 16, 14, leftWidth - 32, TEXT);
        drawFittedText(context, Text.literal("K cierra · panel ajustable"), 16, 25, leftWidth - 32, MUTED);

        int toolTop = 44;
        int buttonWidth = (leftWidth - 26) / 2;
        Tool[] tools = Tool.values();
        for (int index = 0; index < tools.length; index++) {
            int column = index % 2;
            int row = index / 2;
            int x = 9 + column * (buttonWidth + 7);
            int y = toolTop + row * 26;
            drawToolButton(context, mouseX, mouseY, x, y, buttonWidth, 21, tools[index]);
        }

        int contextTop = toolTop + 86;
        context.fill(8, contextTop, leftWidth - 10, height - 9, PANEL_INNER);
        drawFittedText(context, Text.literal(selectedTool.title), 16, contextTop + 10, leftWidth - 32, ACCENT);
        switch (selectedTool) {
            case PATH -> drawPathPanel(context, mouseX, mouseY, contextTop);
            case SHAPES -> drawShapesPanel(context, mouseX, mouseY, contextTop);
            case BOX -> drawBoxPanel(context, mouseX, mouseY, contextTop);
            case MAGIC -> drawMagicPanel(context, mouseX, mouseY, contextTop);
            case PAINT -> drawPaintPanel(context, mouseX, mouseY, contextTop);
            case GRADIENT -> drawGradientPanel(context, mouseX, mouseY, contextTop);
        }
    }

    private void drawPathPanel(DrawContext context, int mouseX, int mouseY, int top) {
        int points = PathHologramRenderer.points().size();
        drawFittedText(context, Text.literal("Nodos: " + points + " / 32"), 16, top + 28, leftWidth - 32, TEXT);
        drawFittedText(context, Text.literal("Clic: crear · arrastrar nodo: mover"), 16, top + 42,
                leftWidth - 32, MUTED);
        drawFittedText(context, Text.literal("Alcance: 4096 bloques cargados"), 16, top + 54,
                leftWidth - 32, MUTED);
        int width = (leftWidth - 38) / 3;
        drawButton(context, mouseX, mouseY, 14, top + 72, width, 23, "Deshacer", false);
        drawButton(context, mouseX, mouseY, 20 + width, top + 72, width, 23, "Cerrar", false);
        drawButton(context, mouseX, mouseY, 26 + width * 2, top + 72, width, 23, "Limpiar", false);
        drawFittedText(context, Text.literal("El holograma verde marca el próximo nodo."),
                16, top + 110, leftWidth - 32, SUCCESS);
        drawFittedText(context, Text.literal("Los nodos solo se pueden arrastrar con K abierto."),
                16, top + 124, leftWidth - 32, MUTED);
    }

    private void drawShapesPanel(DrawContext context, int mouseX, int mouseY, int top) {
        PathShape[] shapes = PathShape.values();
        int buttonWidth = (leftWidth - 37) / 2;
        for (int index = 0; index < shapes.length; index++) {
            int x = 14 + index % 2 * (buttonWidth + 7);
            int y = top + 28 + index / 2 * 21;
            drawButton(context, mouseX, mouseY, x, y, buttonWidth, 18, shapes[index].displayName(),
                    selectedShape == shapes[index]);
        }
        int controlsTop = top + 205;
        drawStepper(context, mouseX, mouseY, controlsTop, "Escala", radius, Control.RADIUS);
        drawStepper(context, mouseX, mouseY, controlsTop + 23, "Espaciado", spacing, Control.SPACING);
        drawStepper(context, mouseX, mouseY, controlsTop + 46, "Grosor", thickness, Control.THICKNESS);
        drawStepper(context, mouseX, mouseY, controlsTop + 69, "Giro", roll, Control.ROLL);
        drawButton(context, mouseX, mouseY, 14, controlsTop + 99, leftWidth - 30, 31, "BUILD SOBRE EL PATH", true);
    }

    private void drawBoxPanel(DrawContext context, int mouseX, int mouseY, int top) {
        drawFittedText(context, Text.literal("1. Clic para esquina A"), 16, top + 31, leftWidth - 32, TEXT);
        drawFittedText(context, Text.literal("2. Clic para esquina B"), 16, top + 45, leftWidth - 32, TEXT);
        drawFittedText(context, Text.literal("3. Arrastra A o B para ajustar"), 16, top + 59,
                leftWidth - 32, MUTED);
        String first = boxFirst == null ? "sin marcar" : coordinates(boxFirst);
        String second = boxSecond == null ? "sin marcar" : coordinates(boxSecond);
        drawFittedText(context, Text.literal("A: " + first), 16, top + 82, leftWidth - 32, ACCENT);
        drawFittedText(context, Text.literal("B: " + second), 16, top + 96, leftWidth - 32, ACCENT);
        drawButton(context, mouseX, mouseY, 14, top + 120, leftWidth - 30, 25, "REINICIAR CAJA", false);
        drawButton(context, mouseX, mouseY, 14, top + 151, leftWidth - 30, 27,
                "OPERACIONES DE SELECCIÓN", true);
        drawFittedText(context, Text.literal("Selecciona una esquina y usa la cruceta XYZ."),
                16, top + 190, leftWidth - 32, MUTED);
    }

    private void drawMagicPanel(DrawContext context, int mouseX, int mouseY, int top) {
        drawFittedText(context, Text.literal("Selecciona con un solo clic lejano."), 16, top + 31,
                leftWidth - 32, TEXT);
        drawFittedText(context, Text.literal("Bloque: una coordenada · Área: conectados"), 16, top + 46,
                leftWidth - 32, MUTED);
        int width = (leftWidth - 35) / 2;
        drawButton(context, mouseX, mouseY, 14, top + 68, width, 27, "BLOQUE", !magicArea);
        drawButton(context, mouseX, mouseY, 21 + width, top + 68, width, 27, "ÁREA", magicArea);
        drawFittedText(context, Text.literal("El área usa bloques idénticos conectados"),
                16, top + 112, leftWidth - 32, MUTED);
        drawFittedText(context, Text.literal("y crea una selección WorldEdit ajustada."),
                16, top + 126, leftWidth - 32, MUTED);
        drawSelectionOperationsButton(context, mouseX, mouseY, top);
    }

    private void drawSelectionOperationsButton(DrawContext context, int mouseX, int mouseY, int top) {
        drawButton(context, mouseX, mouseY, 14, top + 151, leftWidth - 30, 27,
                "OPERACIONES DE SELECCIÓN", true);
    }

    private void drawPaintPanel(DrawContext context, int mouseX, int mouseY, int top) {
        drawFittedText(context, Text.literal("Clic o arrastre sobre el mundo para pintar."),
                16, top + 31, leftWidth - 32, TEXT);
        drawFittedText(context, Text.literal("El círculo 3D muestra el tamaño real."),
                16, top + 46, leftWidth - 32, MUTED);
        drawStepper(context, mouseX, mouseY, top + 69, "Radio", paintRadius, Control.PAINT_RADIUS);
        ItemStack selected = stack(selectedPattern);
        context.drawItem(selected, 17, top + 103);
        drawFittedText(context, selected.getName(), 40, top + 108, leftWidth - 56, ACCENT);
        drawFittedText(context, Text.literal("Cada trazo queda disponible para //undo."),
                16, top + 140, leftWidth - 32, MUTED);
        drawButton(context, mouseX, mouseY, 14, top + 164, leftWidth - 30, 29,
                "PINTAR TODA LA SELECCIÓN SÓLIDA", true);
    }

    private void drawGradientPanel(DrawContext context, int mouseX, int mouseY, int top) {
        drawFittedText(context, Text.literal("Aplica un preset a la selección actual."),
                16, top + 27, leftWidth - 32, MUTED);
        GradientPalette[] palettes = GradientPalette.values();
        int buttonWidth = (leftWidth - 37) / 2;
        for (int index = 0; index < palettes.length; index++) {
            int x = 14 + index % 2 * (buttonWidth + 7);
            int y = top + 43 + index / 2 * 20;
            drawButton(context, mouseX, mouseY, x, y, buttonWidth, 17, palettes[index].id(), false);
        }
    }

    private void drawRightPanel(DrawContext context, int mouseX, int mouseY) {
        int left = width - rightWidth;
        context.fill(left, 0, width, height, PANEL);
        context.fill(left, 0, left + 3, height, BORDER);
        context.fill(left + 10, 8, width - 9, 63, PANEL_INNER);
        drawFittedText(context, Text.literal("BLOQUES A USAR"), left + 18, 15, rightWidth - 36, ACCENT);
        ItemStack selected = stack(selectedPattern);
        context.drawItem(selected, left + 18, 34);
        drawFittedText(context, selected.getName(), left + 42, 39, rightWidth - 60, TEXT);
        int actionWidth = (rightWidth - 25) / 2;
        drawButton(context, mouseX, mouseY, left + 10, 69, actionWidth, 24, "CUENTAGOTAS", false);
        drawButton(context, mouseX, mouseY, left + 15 + actionWidth, 69, actionWidth, 24, "TODOS LOS BLOQUES", true);

        int columns = Math.max(4, (rightWidth - 18) / 34);
        int gridX = left + 10;
        int gridY = 101;
        for (int index = 0; index < BLOCKS.size(); index++) {
            int cellX = gridX + index % columns * 34;
            int cellY = gridY + index / columns * 32;
            BlockChoice choice = BLOCKS.get(index);
            boolean hovered = inside(mouseX, mouseY, cellX, cellY, 29, 28);
            context.fill(cellX, cellY, cellX + 29, cellY + 28,
                    choice.pattern().equals(selectedPattern) ? ROW_SELECTED : hovered ? ROW_HOVER : ROW);
            context.drawItem(choice.stack(), cellX + 6, cellY + 6);
        }
        drawFittedText(context, Text.literal("La paleta sirve para figuras y pintura."),
                left + 15, height - 28, rightWidth - 30, MUTED);
        drawFittedText(context, Text.literal("Arrastra el borde para cambiar el ancho."),
                left + 15, height - 16, rightWidth - 30, MUTED);
    }

    private void drawViewportHud(DrawContext context) {
        int viewportLeft = leftWidth + 8;
        int viewportRight = width - rightWidth - 8;
        if (viewportRight <= viewportLeft) {
            return;
        }
        int center = (viewportLeft + viewportRight) / 2;
        int hintWidth = Math.min(390, viewportRight - viewportLeft - 12);
        context.fill(center - hintWidth / 2, 8, center + hintWidth / 2, 30, 0xB90A0709);
        drawFittedCenteredText(context, Text.literal(selectedTool.viewportHint), center, 15,
                hintWidth - 14, TEXT);
        context.fill(viewportLeft, height - 27, viewportRight, height - 7, 0xB90A0709);
        drawFittedCenteredText(context, Text.literal(status), center, height - 20,
                viewportRight - viewportLeft - 14, status.startsWith("Listo") ? SUCCESS : MUTED);
    }

    private void drawStepper(
            DrawContext context,
            int mouseX,
            int mouseY,
            int y,
            String label,
            int value,
            Control control
    ) {
        drawFittedText(context, Text.literal(label), 17, y + 6, Math.max(30, leftWidth - 122), MUTED);
        int minusX = leftWidth - 96;
        int plusX = leftWidth - 40;
        drawButton(context, mouseX, mouseY, minusX, y, 22, 20, "−", false);
        drawButton(context, mouseX, mouseY, plusX, y, 22, 20, "+", false);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(Integer.toString(value)), leftWidth - 57, y + 6,
                control == Control.SPACING ? ACCENT : TEXT);
    }

    private void drawButton(
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
                selected ? ROW_SELECTED : hovered ? ROW_HOVER : ROW);
        if (selected) {
            context.fill(x, y, x + 3, y + buttonHeight, ACCENT);
        }
        drawFittedCenteredText(context, Text.literal(label), x + buttonWidth / 2,
                y + (buttonHeight - 8) / 2, buttonWidth - 10, TEXT);
    }

    private void drawToolButton(
            DrawContext context,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int buttonWidth,
            int buttonHeight,
            Tool tool
    ) {
        boolean selected = selectedTool == tool;
        boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        context.fill(x, y, x + buttonWidth, y + buttonHeight,
                selected ? ROW_SELECTED : hovered ? ROW_HOVER : ROW);
        if (selected) {
            context.fill(x, y, x + 3, y + buttonHeight, ACCENT);
        }
        context.drawItem(tool.icon, x + 5, y + 2);
        drawFittedText(context, Text.literal(tool.label), x + 25, y + 7, buttonWidth - 31, TEXT);
    }

    private void drawFittedText(DrawContext context, Text text, int x, int y, int maximumWidth, int color) {
        int textWidth = textRenderer.getWidth(text);
        if (textWidth <= 0 || maximumWidth <= 0) {
            return;
        }
        float scale = Math.min(1.0F, maximumWidth / (float) textWidth);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.drawTextWithShadow(textRenderer, text, 0, 0, color);
        context.getMatrices().pop();
    }

    private void drawFittedCenteredText(
            DrawContext context,
            Text text,
            int centerX,
            int y,
            int maximumWidth,
            int color
    ) {
        int textWidth = textRenderer.getWidth(text);
        if (textWidth <= 0 || maximumWidth <= 0) {
            return;
        }
        float scale = Math.min(1.0F, maximumWidth / (float) textWidth);
        context.getMatrices().push();
        context.getMatrices().translate(centerX, y, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.drawTextWithShadow(textRenderer, text, -textWidth / 2, 0, color);
        context.getMatrices().pop();
    }

    private void drawBlockModal(DrawContext context, int mouseX, int mouseY) {
        int modalWidth = Math.min(780, width - 40);
        int modalHeight = Math.min(520, height - 30);
        int left = (width - modalWidth) / 2;
        int top = (height - modalHeight) / 2;
        context.fill(left - 2, top - 2, left + modalWidth + 2, top + modalHeight + 2, BORDER);
        context.fill(left, top, left + modalWidth, top + modalHeight, 0xFF080608);
        context.fill(left + 10, top + 10, left + modalWidth - 10, top + 40, PANEL_INNER);
        drawFittedText(context, Text.literal("BIBLIOTECA COMPLETA DE BLOQUES"),
                left + 18, top + 15, modalWidth - 112, TEXT);
        drawFittedText(context, Text.literal("Buscar: " + blockSearch + "_"),
                left + 18, top + 28, modalWidth - 112, blockSearch.isEmpty() ? MUTED : ACCENT);
        drawButton(context, mouseX, mouseY, left + modalWidth - 76, top + 12, 56, 24, "CERRAR", false);

        List<BlockChoice> filtered = filteredBlocks();
        int columns = Math.max(6, (modalWidth - 26) / 34);
        int visibleRows = Math.max(2, (modalHeight - 91) / 34);
        int maximumScroll = Math.max(0, (filtered.size() + columns - 1) / columns - visibleRows);
        blockModalScroll = Math.clamp(blockModalScroll, 0, maximumScroll);
        int startIndex = blockModalScroll * columns;
        int maximumIndex = Math.min(filtered.size(), startIndex + columns * visibleRows);
        hoveredBlockId = "";
        for (int index = startIndex; index < maximumIndex; index++) {
            int visibleIndex = index - startIndex;
            int cellX = left + 13 + visibleIndex % columns * 34;
            int cellY = top + 49 + visibleIndex / columns * 34;
            BlockChoice choice = filtered.get(index);
            boolean hovered = inside(mouseX, mouseY, cellX, cellY, 29, 29);
            context.fill(cellX, cellY, cellX + 29, cellY + 29,
                    choice.pattern().equals(selectedPattern) ? ROW_SELECTED : hovered ? ROW_HOVER : ROW);
            context.drawItem(choice.stack(), cellX + 6, cellY + 6);
            if (hovered) {
                hoveredBlockId = choice.pattern();
            }
        }
        String footer = hoveredBlockId.isEmpty()
                ? filtered.size() + " bloques · rueda para desplazarte · escribe para buscar"
                : hoveredBlockId;
        context.fill(left + 10, top + modalHeight - 31, left + modalWidth - 10, top + modalHeight - 10, PANEL_INNER);
        drawFittedCenteredText(context, Text.literal(footer),
                left + modalWidth / 2, top + modalHeight - 24, modalWidth - 44,
                hoveredBlockId.isEmpty() ? MUTED : ACCENT);
    }

    private void drawSelectionModal(DrawContext context, int mouseX, int mouseY) {
        int modalWidth = Math.min(500, width - 32);
        int modalHeight = Math.min(286, height - 24);
        int left = (width - modalWidth) / 2;
        int top = (height - modalHeight) / 2;
        context.fill(left - 2, top - 2, left + modalWidth + 2, top + modalHeight + 2, BORDER);
        context.fill(left, top, left + modalWidth, top + modalHeight, 0xFF080608);
        context.fill(left + 10, top + 10, left + modalWidth - 10, top + 51, PANEL_INNER);
        drawFittedText(context, Text.literal("SELECCIÓN / PORTAPAPELES"), left + 18, top + 16,
                modalWidth - 108, TEXT);
        context.drawItem(stack(selectedPattern), left + 18, top + 29);
        drawFittedText(context, Text.literal("Material: " + selectedPattern), left + 42, top + 34,
                modalWidth - 132, ACCENT);
        drawButton(context, mouseX, mouseY, left + modalWidth - 72, top + 14, 54, 24, "CERRAR", false);

        int gap = 8;
        int buttonWidth = (modalWidth - 42 - gap) / 2;
        int firstX = left + 17;
        int secondX = firstX + buttonWidth + gap;
        int rowOne = top + 65;
        int rowTwo = rowOne + 43;
        int rowThree = rowTwo + 43;
        drawIconButton(context, mouseX, mouseY, firstX, rowOne, buttonWidth, 35,
                stack(selectedPattern), "REEMPLAZAR", false);
        drawIconButton(context, mouseX, mouseY, secondX, rowOne, buttonWidth, 35,
                Items.BARRIER.getDefaultStack(), "BORRAR", false);
        drawIconButton(context, mouseX, mouseY, firstX, rowTwo, buttonWidth, 35,
                Items.PAPER.getDefaultStack(), "COPIAR  Ctrl+C", clipboardReady);
        drawIconButton(context, mouseX, mouseY, secondX, rowTwo, buttonWidth, 35,
                Items.SHEARS.getDefaultStack(), "CORTAR  Ctrl+X", false);
        drawIconButton(context, mouseX, mouseY, firstX, rowThree, buttonWidth, 35,
                Items.CHEST.getDefaultStack(), "POSICIONAR  Ctrl+V", pasteMode);
        drawIconButton(context, mouseX, mouseY, secondX, rowThree, buttonWidth, 35,
                Items.LIME_DYE.getDefaultStack(), "CONFIRMAR PEGADO", pasteMode);

        String help = pasteMode
                ? "Mueve la cruceta XYZ en el mundo y confirma con Ctrl+V."
                : "La caja o selección mágica activa estas operaciones.";
        context.fill(left + 16, top + modalHeight - 49, left + modalWidth - 16, top + modalHeight - 15, PANEL_INNER);
        drawFittedCenteredText(context, Text.literal(help), left + modalWidth / 2, top + modalHeight - 36,
                modalWidth - 48, pasteMode ? SUCCESS : MUTED);
    }

    private void drawIconButton(
            DrawContext context,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int buttonWidth,
            int buttonHeight,
            ItemStack icon,
            String label,
            boolean selected
    ) {
        boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        context.fill(x, y, x + buttonWidth, y + buttonHeight,
                selected ? ROW_SELECTED : hovered ? ROW_HOVER : ROW);
        if (selected) {
            context.fill(x, y, x + 3, y + buttonHeight, ACCENT);
        }
        context.drawItem(icon, x + 9, y + (buttonHeight - 16) / 2);
        drawFittedText(context, Text.literal(label), x + 34, y + (buttonHeight - 8) / 2,
                buttonWidth - 42, TEXT);
    }

    private List<BlockChoice> filteredBlocks() {
        if (blockSearch.isBlank()) {
            return ALL_BLOCKS;
        }
        String query = blockSearch.toLowerCase(java.util.Locale.ROOT);
        return ALL_BLOCKS.stream()
                .filter(choice -> choice.pattern().toLowerCase(java.util.Locale.ROOT).contains(query))
                .toList();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (blockModal) {
            return clickBlockModal(mouseX, mouseY, button);
        }
        if (selectionModal) {
            return clickSelectionModal(mouseX, mouseY, button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && Math.abs(mouseX - leftWidth) <= 4) {
            resizingLeft = true;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && Math.abs(mouseX - (width - rightWidth)) <= 4) {
            resizingRight = true;
            return true;
        }
        if (mouseX < leftWidth) {
            return clickLeftPanel(mouseX, mouseY, button);
        }
        if (mouseX >= width - rightWidth) {
            return clickRightPanel(mouseX, mouseY, button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            cameraDragging = true;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }

        Ray ray = rayForMouse(mouseX, mouseY);
        if (pasteMode && pasteOrigin != null) {
            gizmoAxis = pickGizmoAxis(ray, pasteOrigin);
            if (gizmoAxis < 0 && hoverPlacement != null) {
                pasteOrigin = hoverPlacement;
            }
            return true;
        }
        if (selectedTool == Tool.BOX && boxFirst != null && boxSecond != null && selectedBoxPoint >= 0) {
            BlockPos selectedCorner = selectedBoxPoint == 0 ? boxFirst : boxSecond;
            gizmoAxis = pickGizmoAxis(ray, selectedCorner);
            if (gizmoAxis >= 0) {
                draggingBoxPoint = selectedBoxPoint;
                lastSentPosition = null;
                return true;
            }
        }
        if (hoverBlock == null || hoverPlacement == null) {
            return true;
        }
        switch (selectedTool) {
            case PATH -> clickPath(ray);
            case BOX -> clickBox(ray);
            case MAGIC -> {
                sendEditor(magicArea ? EditorActionPayload.MAGIC_AREA : EditorActionPayload.MAGIC_BLOCK,
                        -1, hoverBlock);
                selectionModal = true;
                status = "Selección mágica lista · elige una operación";
            }
            case PAINT -> {
                paintDragging = true;
                sendEditor(EditorActionPayload.PAINT_BEGIN, -1, hoverBlock);
                paintAt(hoverBlock);
            }
            case SHAPES, GRADIENT -> status = "Selecciona Path, Caja, Mágico o Pintar para editar el mundo";
        }
        return true;
    }

    private boolean clickLeftPanel(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        int toolTop = 44;
        int buttonWidth = (leftWidth - 26) / 2;
        for (int index = 0; index < Tool.values().length; index++) {
            int x = 9 + index % 2 * (buttonWidth + 7);
            int y = toolTop + index / 2 * 26;
            if (inside(mouseX, mouseY, x, y, buttonWidth, 21)) {
                selectedTool = Tool.values()[index];
                status = selectedTool.status;
                return true;
            }
        }

        int top = toolTop + 86;
        switch (selectedTool) {
            case PATH -> {
                int width = (leftWidth - 38) / 3;
                if (inside(mouseX, mouseY, 14, top + 72, width, 23)) {
                    sendCommand("komaedit path undo");
                } else if (inside(mouseX, mouseY, 20 + width, top + 72, width, 23)) {
                    sendCommand("komaedit path close");
                } else if (inside(mouseX, mouseY, 26 + width * 2, top + 72, width, 23)) {
                    sendCommand("komaedit path clear");
                }
            }
            case SHAPES -> clickShapesPanel(mouseX, mouseY, top);
            case BOX -> {
                if (inside(mouseX, mouseY, 14, top + 120, leftWidth - 30, 25)) {
                    boxFirst = null;
                    boxSecond = null;
                    selectedBoxPoint = -1;
                    status = "Caja reiniciada · marca la esquina A";
                } else if (inside(mouseX, mouseY, 14, top + 151, leftWidth - 30, 27)) {
                    selectionModal = true;
                }
            }
            case MAGIC -> {
                int width = (leftWidth - 35) / 2;
                if (inside(mouseX, mouseY, 14, top + 68, width, 27)) {
                    magicArea = false;
                } else if (inside(mouseX, mouseY, 21 + width, top + 68, width, 27)) {
                    magicArea = true;
                } else if (inside(mouseX, mouseY, 14, top + 151, leftWidth - 30, 27)) {
                    selectionModal = true;
                }
            }
            case PAINT -> {
                if (!stepperClick(mouseX, mouseY, top + 69, Control.PAINT_RADIUS)
                        && inside(mouseX, mouseY, 14, top + 164, leftWidth - 30, 29)) {
                    sendEditor(EditorActionPayload.PAINT_SELECTION, -1, BlockPos.ORIGIN);
                    status = "Pintura sólida de selección enviada";
                }
            }
            case GRADIENT -> clickGradientPanel(mouseX, mouseY, top);
        }
        return true;
    }

    private void clickShapesPanel(double mouseX, double mouseY, int top) {
        PathShape[] shapes = PathShape.values();
        int buttonWidth = (leftWidth - 37) / 2;
        for (int index = 0; index < shapes.length; index++) {
            int x = 14 + index % 2 * (buttonWidth + 7);
            int y = top + 28 + index / 2 * 21;
            if (inside(mouseX, mouseY, x, y, buttonWidth, 18)) {
                selectedShape = shapes[index];
                radius = selectedShape.defaults().radius();
                spacing = selectedShape.defaults().spacing();
                thickness = selectedShape.defaults().thickness();
                status = selectedShape.description();
                return;
            }
        }
        int controlsTop = top + 205;
        if (stepperClick(mouseX, mouseY, controlsTop, Control.RADIUS)
                || stepperClick(mouseX, mouseY, controlsTop + 23, Control.SPACING)
                || stepperClick(mouseX, mouseY, controlsTop + 46, Control.THICKNESS)
                || stepperClick(mouseX, mouseY, controlsTop + 69, Control.ROLL)) {
            return;
        }
        if (inside(mouseX, mouseY, 14, controlsTop + 99, leftWidth - 30, 31)) {
            build();
        }
    }

    private void clickGradientPanel(double mouseX, double mouseY, int top) {
        GradientPalette[] palettes = GradientPalette.values();
        int buttonWidth = (leftWidth - 37) / 2;
        for (int index = 0; index < palettes.length; index++) {
            int x = 14 + index % 2 * (buttonWidth + 7);
            int y = top + 43 + index / 2 * 20;
            if (inside(mouseX, mouseY, x, y, buttonWidth, 17)) {
                sendCommand("komaedit gradient preset " + palettes[index].id() + " vertical 88 false");
                status = "Degradado enviado: " + palettes[index].id();
                return;
            }
        }
    }

    private boolean clickRightPanel(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        int left = width - rightWidth;
        int actionWidth = (rightWidth - 25) / 2;
        if (inside(mouseX, mouseY, left + 10, 69, actionWidth, 24)) {
            if (hoverBlock == null || client == null || client.world == null) {
                status = "Pasa primero el cursor por un bloque del mundo";
            } else {
                Block block = client.world.getBlockState(hoverBlock).getBlock();
                if (!block.asItem().getDefaultStack().isEmpty()) {
                    selectedPattern = Registries.BLOCK.getId(block).toString();
                    status = "Bloque tomado: " + block.getName().getString();
                }
            }
            return true;
        }
        if (inside(mouseX, mouseY, left + 15 + actionWidth, 69, actionWidth, 24)) {
            blockModal = true;
            blockModalScroll = 0;
            status = "Biblioteca completa abierta";
            return true;
        }
        int columns = Math.max(4, (rightWidth - 18) / 34);
        int gridX = left + 10;
        int gridY = 101;
        for (int index = 0; index < BLOCKS.size(); index++) {
            int x = gridX + index % columns * 34;
            int y = gridY + index / columns * 32;
            if (inside(mouseX, mouseY, x, y, 29, 28)) {
                selectedPattern = BLOCKS.get(index).pattern();
                status = "Material: " + BLOCKS.get(index).stack().getName().getString();
                return true;
            }
        }
        return true;
    }

    private boolean clickBlockModal(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        int modalWidth = Math.min(780, width - 40);
        int modalHeight = Math.min(520, height - 30);
        int left = (width - modalWidth) / 2;
        int top = (height - modalHeight) / 2;
        if (inside(mouseX, mouseY, left + modalWidth - 76, top + 12, 56, 24)) {
            blockModal = false;
            return true;
        }
        List<BlockChoice> filtered = filteredBlocks();
        int columns = Math.max(6, (modalWidth - 26) / 34);
        int visibleRows = Math.max(2, (modalHeight - 91) / 34);
        int startIndex = blockModalScroll * columns;
        int maximumIndex = Math.min(filtered.size(), startIndex + columns * visibleRows);
        for (int index = startIndex; index < maximumIndex; index++) {
            int visibleIndex = index - startIndex;
            int cellX = left + 13 + visibleIndex % columns * 34;
            int cellY = top + 49 + visibleIndex / columns * 34;
            if (inside(mouseX, mouseY, cellX, cellY, 29, 29)) {
                selectedPattern = filtered.get(index).pattern();
                status = "Material: " + selectedPattern;
                blockModal = false;
                return true;
            }
        }
        return true;
    }

    private boolean clickSelectionModal(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        int modalWidth = Math.min(500, width - 32);
        int modalHeight = Math.min(286, height - 24);
        int left = (width - modalWidth) / 2;
        int top = (height - modalHeight) / 2;
        if (inside(mouseX, mouseY, left + modalWidth - 72, top + 14, 54, 24)) {
            selectionModal = false;
            return true;
        }
        int gap = 8;
        int buttonWidth = (modalWidth - 42 - gap) / 2;
        int firstX = left + 17;
        int secondX = firstX + buttonWidth + gap;
        int rowOne = top + 65;
        int rowTwo = rowOne + 43;
        int rowThree = rowTwo + 43;
        if (inside(mouseX, mouseY, firstX, rowOne, buttonWidth, 35)) {
            sendEditor(EditorActionPayload.SELECTION_REPLACE, -1, BlockPos.ORIGIN);
            status = "Reemplazando la selección con " + selectedPattern;
            selectionModal = false;
        } else if (inside(mouseX, mouseY, secondX, rowOne, buttonWidth, 35)) {
            sendEditor(EditorActionPayload.SELECTION_DELETE, -1, BlockPos.ORIGIN);
            status = "Borrado de selección enviado";
            selectionModal = false;
        } else if (inside(mouseX, mouseY, firstX, rowTwo, buttonWidth, 35)) {
            copySelection(false);
        } else if (inside(mouseX, mouseY, secondX, rowTwo, buttonWidth, 35)) {
            copySelection(true);
        } else if (inside(mouseX, mouseY, firstX, rowThree, buttonWidth, 35)) {
            startPasteMode();
            selectionModal = false;
        } else if (inside(mouseX, mouseY, secondX, rowThree, buttonWidth, 35)) {
            confirmPaste();
            selectionModal = false;
        }
        return true;
    }

    private void clickPath(Ray ray) {
        int existing = pickPathPoint(ray);
        if (existing >= 0) {
            draggingPathPoint = existing;
            lastSentPosition = null;
            status = "Arrastrando nodo " + (existing + 1);
            return;
        }
        sendEditor(EditorActionPayload.PATH_ADD, -1, hoverPlacement);
        status = "Listo · nodo añadido en " + coordinates(hoverPlacement);
    }

    private void clickBox(Ray ray) {
        if (boxFirst == null) {
            boxFirst = hoverPlacement;
            selectedBoxPoint = 0;
            sendEditor(EditorActionPayload.BOX_PRIMARY, -1, boxFirst);
            status = "Esquina A marcada · ahora marca B";
            return;
        }
        if (boxSecond == null) {
            boxSecond = hoverPlacement;
            selectedBoxPoint = 1;
            sendEditor(EditorActionPayload.BOX_SECONDARY, 1, boxSecond);
            status = "Listo · caja seleccionada; arrastra sus extremos";
            selectionModal = true;
            return;
        }
        int handle = pickBoxPoint(ray);
        if (handle >= 0) {
            draggingBoxPoint = handle;
            selectedBoxPoint = handle;
            lastSentPosition = null;
            return;
        }
        boxFirst = hoverPlacement;
        boxSecond = null;
        selectedBoxPoint = 0;
        sendEditor(EditorActionPayload.BOX_PRIMARY, -1, boxFirst);
        status = "Nueva caja · esquina A marcada";
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (resizingLeft) {
            leftWidth = Math.clamp((int) Math.round(mouseX), minimumSideWidth(), maximumSideWidth());
            updateEditorViewport();
            return true;
        }
        if (resizingRight) {
            rightWidth = Math.clamp(width - (int) Math.round(mouseX), minimumSideWidth(), maximumSideWidth());
            updateEditorViewport();
            return true;
        }
        if (cameraDragging && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && client != null && client.player != null) {
            client.player.changeLookDirection(-deltaX * 1.35, -deltaY * 1.35);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !insideViewport(mouseX)) {
            return true;
        }
        updateHover((int) mouseX, (int) mouseY);
        Ray dragRay = rayForMouse(mouseX, mouseY);
        if (pasteMode && gizmoAxis >= 0 && pasteOrigin != null) {
            pasteOrigin = moveAlongAxis(pasteOrigin, dragRay, gizmoAxis);
            status = "Moviendo portapapeles · " + coordinates(pasteOrigin);
        } else if (draggingPathPoint >= 0 && hoverPlacement != null && !hoverPlacement.equals(lastSentPosition)) {
            sendEditor(EditorActionPayload.PATH_MOVE, draggingPathPoint, hoverPlacement);
            lastSentPosition = hoverPlacement;
            status = "Moviendo nodo " + (draggingPathPoint + 1) + " · " + coordinates(hoverPlacement);
        } else if (draggingBoxPoint >= 0 && gizmoAxis >= 0) {
            BlockPos current = draggingBoxPoint == 0 ? boxFirst : boxSecond;
            BlockPos moved = moveAlongAxis(current, dragRay, gizmoAxis);
            if (draggingBoxPoint == 0) {
                boxFirst = moved;
                sendEditor(EditorActionPayload.BOX_PRIMARY, 0, boxFirst);
            } else {
                boxSecond = moved;
                sendEditor(EditorActionPayload.BOX_SECONDARY, 1, boxSecond);
            }
            lastSentPosition = moved;
        } else if (draggingBoxPoint >= 0 && hoverPlacement != null && !hoverPlacement.equals(lastSentPosition)) {
            if (draggingBoxPoint == 0) {
                boxFirst = hoverPlacement;
                sendEditor(EditorActionPayload.BOX_PRIMARY, 0, boxFirst);
            } else {
                boxSecond = hoverPlacement;
                sendEditor(EditorActionPayload.BOX_SECONDARY, 1, boxSecond);
            }
            lastSentPosition = hoverPlacement;
        } else if (paintDragging && selectedTool == Tool.PAINT && hoverBlock != null) {
            paintAt(hoverBlock);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (paintDragging) {
                sendEditor(EditorActionPayload.PAINT_END, -1, BlockPos.ORIGIN);
                paintDragging = false;
            }
            resizingLeft = false;
            resizingRight = false;
            draggingPathPoint = -1;
            draggingBoxPoint = -1;
            gizmoAxis = -1;
            lastSentPosition = null;
            rememberedLeftWidth = leftWidth;
            rememberedRightWidth = rightWidth;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            cameraDragging = false;
            return true;
        }
        return true;
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
        if (control && keyCode == GLFW.GLFW_KEY_C) {
            copySelection(false);
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_X) {
            copySelection(true);
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_V) {
            if (pasteMode) {
                confirmPaste();
            } else {
                startPasteMode();
            }
            selectionModal = false;
            return true;
        }
        if (blockModal) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                blockModal = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !blockSearch.isEmpty()) {
                blockSearch = blockSearch.substring(0, blockSearch.length() - 1);
                blockModalScroll = 0;
                return true;
            }
        }
        if (selectionModal && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            selectionModal = false;
            return true;
        }
        if (pasteMode && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            pasteMode = false;
            gizmoAxis = -1;
            status = "Pegado cancelado";
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_K) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && selectedTool == Tool.PATH) {
            Ray ray = rayForMouse(width / 2.0, height / 2.0);
            int point = pickPathPoint(ray);
            if (point >= 0) {
                sendEditor(EditorActionPayload.PATH_REMOVE, point, BlockPos.ORIGIN);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_INSERT && selectedTool == Tool.PATH) {
            Ray ray = rayForMouse(width / 2.0, height / 2.0);
            int point = pickPathPoint(ray);
            if (point >= 0) {
                sendEditor(EditorActionPayload.PATH_INSERT, point, BlockPos.ORIGIN);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && (selectedTool == Tool.BOX || selectedTool == Tool.MAGIC)) {
            sendEditor(EditorActionPayload.SELECTION_DELETE, -1, BlockPos.ORIGIN);
            status = "Borrado de selección enviado";
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (blockModal && blockSearch.length() < 64
                && (Character.isLetterOrDigit(chr) || chr == '_' || chr == ':' || chr == '-')) {
            blockSearch += Character.toLowerCase(chr);
            blockModalScroll = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (blockModal) {
            int modalWidth = Math.min(780, width - 40);
            int modalHeight = Math.min(520, height - 30);
            int columns = Math.max(6, (modalWidth - 26) / 34);
            int visibleRows = Math.max(2, (modalHeight - 91) / 34);
            int rows = (filteredBlocks().size() + columns - 1) / columns;
            blockModalScroll = Math.clamp(
                    blockModalScroll - (int) Math.signum(verticalAmount),
                    0,
                    Math.max(0, rows - visibleRows)
            );
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void updateHover(int mouseX, int mouseY) {
        BlockPos overlayTarget = null;
        if (!blockModal && !selectionModal && insideViewport(mouseX)) {
            Ray ray = rayForMouse(mouseX, mouseY);
            if (client != null && client.world != null && client.player != null) {
                HitResult result = client.world.raycast(new RaycastContext(
                        ray.origin(),
                        ray.origin().add(ray.direction().multiply(EDITOR_REACH)),
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        client.player
                ));
                if (result instanceof BlockHitResult hit && result.getType() == HitResult.Type.BLOCK) {
                    hoverBlock = hit.getBlockPos().toImmutable();
                    hoverPlacement = hit.getBlockPos().offset(hit.getSide()).toImmutable();
                    overlayTarget = selectedTool == Tool.MAGIC || selectedTool == Tool.PAINT
                            ? hoverBlock
                            : selectedTool == Tool.PATH || selectedTool == Tool.BOX ? hoverPlacement : null;
                }
            }
        }
        BlockPos gizmoOrigin = pasteMode
                ? pasteOrigin
                : selectedTool == Tool.BOX && selectedBoxPoint >= 0
                ? selectedBoxPoint == 0 ? boxFirst : boxSecond
                : null;
        EditorOverlayRenderer.update(
                true,
                selectedTool.ordinal(),
                overlayTarget,
                selectedTool == Tool.PAINT ? paintRadius : radius,
                boxFirst,
                boxSecond,
                draggingPathPoint,
                gizmoOrigin,
                gizmoAxis
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
        double viewportWidth = Math.max(1.0, width - leftWidth - rightWidth);
        double normalizedX = (mouseX - leftWidth) / viewportWidth * 2.0 - 1.0;
        double normalizedY = mouseY / Math.max(1.0, height) * 2.0 - 1.0;
        double aspect = viewportWidth / Math.max(1.0, height);
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
            double threshold = Math.max(0.70, along * 0.009);
            if (distance <= threshold && distance < bestDistance) {
                selected = index;
                bestDistance = distance;
            }
        }
        return selected;
    }

    private int pickBoxPoint(Ray ray) {
        int first = pointHit(ray, boxFirst) ? 0 : -1;
        if (pointHit(ray, boxSecond)) {
            return first < 0 ? 1 : first;
        }
        return first;
    }

    private boolean pointHit(Ray ray, BlockPos point) {
        if (point == null) {
            return false;
        }
        Vec3d center = Vec3d.ofCenter(point);
        double along = center.subtract(ray.origin()).dotProduct(ray.direction());
        if (along < 0.0) {
            return false;
        }
        double distance = center.distanceTo(ray.origin().add(ray.direction().multiply(along)));
        return distance <= Math.max(0.8, along * 0.009);
    }

    private int pickGizmoAxis(Ray ray, BlockPos origin) {
        if (origin == null) {
            return -1;
        }
        BlockPos[] handles = {
                origin.add(3, 0, 0),
                origin.add(0, 3, 0),
                origin.add(0, 0, 3)
        };
        for (int axis = 0; axis < handles.length; axis++) {
            if (pointHit(ray, handles[axis])) {
                return axis;
            }
        }
        return -1;
    }

    private static BlockPos moveAlongAxis(BlockPos origin, BlockPos target, int axis) {
        if (origin == null) {
            return target;
        }
        return switch (axis) {
            case 0 -> new BlockPos(target.getX(), origin.getY(), origin.getZ());
            case 1 -> new BlockPos(origin.getX(), target.getY(), origin.getZ());
            case 2 -> new BlockPos(origin.getX(), origin.getY(), target.getZ());
            default -> target;
        };
    }

    private static BlockPos moveAlongAxis(BlockPos origin, Ray ray, int axis) {
        Vec3d axisVector = switch (axis) {
            case 0 -> new Vec3d(1.0, 0.0, 0.0);
            case 1 -> new Vec3d(0.0, 1.0, 0.0);
            case 2 -> new Vec3d(0.0, 0.0, 1.0);
            default -> Vec3d.ZERO;
        };
        if (axisVector == Vec3d.ZERO) {
            return origin;
        }
        Vec3d axisOrigin = Vec3d.ofCenter(origin);
        Vec3d offset = ray.origin().subtract(axisOrigin);
        double parallel = ray.direction().dotProduct(axisVector);
        double denominator = 1.0 - parallel * parallel;
        if (denominator < 1.0E-5) {
            return origin;
        }
        double alongAxis = (offset.dotProduct(axisVector)
                - parallel * ray.direction().dotProduct(offset)) / denominator;
        int coordinate = (int) Math.round(alongAxis);
        return switch (axis) {
            case 0 -> origin.add(coordinate, 0, 0);
            case 1 -> origin.add(0, coordinate, 0);
            case 2 -> origin.add(0, 0, coordinate);
            default -> origin;
        };
    }

    private void copySelection(boolean cut) {
        sendEditor(cut ? EditorActionPayload.CLIPBOARD_CUT : EditorActionPayload.CLIPBOARD_COPY,
                -1, BlockPos.ORIGIN);
        clipboardReady = true;
        status = cut ? "Selección cortada · Ctrl+V para posicionar" : "Selección copiada · Ctrl+V para posicionar";
    }

    private void startPasteMode() {
        if (pasteOrigin == null) {
            if (hoverPlacement != null) {
                pasteOrigin = hoverPlacement;
            } else if (boxFirst != null) {
                pasteOrigin = boxFirst;
            } else if (client != null && client.player != null) {
                pasteOrigin = client.player.getBlockPos();
            } else {
                pasteOrigin = BlockPos.ORIGIN;
            }
        }
        pasteMode = true;
        gizmoAxis = -1;
        status = "Portapapeles · arrastra XYZ · Ctrl+V confirma";
    }

    private void confirmPaste() {
        if (!pasteMode || pasteOrigin == null) {
            startPasteMode();
            return;
        }
        sendEditor(EditorActionPayload.CLIPBOARD_PASTE, -1, pasteOrigin);
        status = "Pegado enviado en " + coordinates(pasteOrigin) + " · Ctrl+Z";
        pasteMode = false;
        gizmoAxis = -1;
    }

    private boolean stepperClick(double mouseX, double mouseY, int y, Control control) {
        int minusX = leftWidth - 96;
        int plusX = leftWidth - 40;
        if (inside(mouseX, mouseY, minusX, y, 22, 20)) {
            adjust(control, -1);
            return true;
        }
        if (inside(mouseX, mouseY, plusX, y, 22, 20)) {
            adjust(control, 1);
            return true;
        }
        return false;
    }

    private void adjust(Control control, int direction) {
        switch (control) {
            case RADIUS -> radius = Math.clamp(radius + direction, 1, 128);
            case PAINT_RADIUS -> paintRadius = Math.clamp(paintRadius + direction, 1, 12);
            case SPACING -> spacing = Math.clamp(spacing + direction, 2, 8);
            case THICKNESS -> thickness = Math.clamp(thickness + direction, 1, 4);
            case ROLL -> roll = Math.clamp(roll + direction * 5, -180, 180);
        }
    }

    private void build() {
        String command;
        if (selectedShape == PathShape.ARCH_BRIDGE) {
            command = "komaedit bridge 18 " + radius + " " + selectedPattern;
        } else if (selectedShape == PathShape.PILLAR_ARCHES) {
            command = "komaedit pillararches 48 " + radius + " " + selectedPattern;
        } else if (selectedShape == PathShape.TORII) {
            command = "komaedit torii 96 " + radius + " " + spacing + " " + selectedPattern;
        } else if (selectedShape == PathShape.NOODLES) {
            command = "komaedit noodles " + radius + " " + spacing + " " + selectedPattern;
        } else {
            command = "komaedit build " + selectedShape.id() + " " + selectedPattern + " "
                    + radius + " " + spacing + " " + thickness + " " + roll;
        }
        sendCommand(command);
        status = "Listo · " + selectedShape.displayName() + " enviado";
    }

    private void paintAt(BlockPos position) {
        long now = System.currentTimeMillis();
        if (now - lastPaintAt < 28L || position.equals(lastSentPosition)) {
            return;
        }
        sendEditor(EditorActionPayload.PAINT, -1, position);
        lastPaintAt = now;
        lastSentPosition = position;
        status = "Pintando " + selectedPattern + " · radio " + paintRadius;
    }

    private void sendEditor(int action, int index, BlockPos position) {
        int actionRadius = action == EditorActionPayload.PAINT || action == EditorActionPayload.PAINT_BEGIN
                ? paintRadius
                : radius;
        ClientPlayNetworking.send(new EditorActionPayload(action, index, position, actionRadius, selectedPattern));
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

    private int minimumSideWidth() {
        return Math.min(205, Math.max(150, width / 5));
    }

    private int maximumSideWidth() {
        return Math.max(minimumSideWidth(), Math.min(380, width / 3));
    }

    private boolean insideViewport(double mouseX) {
        return mouseX > leftWidth + 3 && mouseX < width - rightWidth - 3;
    }

    private void updateEditorViewport() {
        if (client != null) {
            EditorViewportState.update(client, leftWidth, rightWidth);
        }
    }

    private ItemStack stack(String pattern) {
        Identifier id = Identifier.tryParse(pattern);
        return id == null ? ItemStack.EMPTY : Registries.BLOCK.get(id).asItem().getDefaultStack();
    }

    private static BlockChoice block(String pattern) {
        Identifier id = Identifier.ofVanilla(pattern);
        return new BlockChoice(pattern, Registries.BLOCK.get(id).asItem().getDefaultStack());
    }

    private static boolean down(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int areaWidth, int areaHeight) {
        return mouseX >= x && mouseX < x + areaWidth && mouseY >= y && mouseY < y + areaHeight;
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + ", " + position.getY() + ", " + position.getZ();
    }

    private record BlockChoice(String pattern, ItemStack stack) {
    }

    private record Ray(Vec3d origin, Vec3d direction) {
    }

    private enum Tool {
        PATH("PATH / SPLINE", "EDITOR DE PATH", "Clic crea nodo · arrastra nodos · clic derecho gira cámara",
                Items.ENDER_PEARL.getDefaultStack(),
                "Path activo · apunta al terreno"),
        SHAPES("FIGURAS", "FIGURAS Y BRUSHES", "Configura la figura en el panel izquierdo",
                Items.NETHER_STAR.getDefaultStack(),
                "Selecciona figura y parámetros"),
        BOX("CAJA", "SELECCIÓN DE CAJA", "Clic A · clic B · arrastra extremos",
                Items.WOODEN_AXE.getDefaultStack(),
                "Marca dos esquinas en el mundo"),
        MAGIC("MÁGICO", "SELECCIÓN MÁGICA", "Clic selecciona el bloque o área conectada",
                Items.COMPASS.getDefaultStack(),
                "Selector mágico listo"),
        PAINT("PINTAR", "BRUSH DE PINTURA", "Clic o arrastra para pintar con holograma",
                Items.BRUSH.getDefaultStack(),
                "Brush de pintura listo"),
        GRADIENT("DEGRADADOS", "DEGRADADOS", "Elige un preset para la selección WorldEdit",
                Items.AMETHYST_SHARD.getDefaultStack(),
                "Selecciona un degradado");

        private final String label;
        private final String title;
        private final String viewportHint;
        private final ItemStack icon;
        private final String status;

        Tool(String label, String title, String viewportHint, ItemStack icon, String status) {
            this.label = label;
            this.title = title;
            this.viewportHint = viewportHint;
            this.icon = icon;
            this.status = status;
        }
    }

    private enum Control {
        RADIUS,
        PAINT_RADIUS,
        SPACING,
        THICKNESS,
        ROLL
    }
}
