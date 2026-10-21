package com.komainu.moxia.client.gui;

import com.komainu.moxia.brush.BrushInput;
import com.komainu.moxia.brush.BrushToolPayload;
import com.komainu.moxia.client.gizmo.GhostClient;
import com.komainu.moxia.client.gui.studio.ClientStudioAim;
import com.komainu.moxia.client.gui.studio.ClientStudioHost;
import com.komainu.moxia.client.gui.studio.DrawContextCanvas;
import com.komainu.moxia.client.mixin.GameRendererAccessor;
import com.komainu.moxia.client.preview.EditorOverlayRenderer;
import com.komainu.moxia.client.preview.PathHologramRenderer;
import com.komainu.moxia.client.preview.ProceduralPreviewRenderer;
import com.komainu.moxia.client.world.WorldOverlayRenderer;
import com.komainu.moxia.editor.EditorActionPayload;
import com.komainu.moxia.feedback.SelectionPayload;
import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.recipe.RecipeActionPayload;
import com.komainu.moxia.studio.ui.FigureStages;
import com.komainu.moxia.studio.ui.StudioKey;
import com.komainu.moxia.studio.ui.StudioStage;
import com.komainu.moxia.studio.ui.StudioUi;
import com.komainu.moxia.studio.ui.StudioView;
import com.komainu.moxia.studio.world.BoxSelect;
import com.komainu.moxia.studio.world.BrushStroke;
import com.komainu.moxia.studio.world.PathDrag;
import net.minecraft.block.BlockState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Procedural Studio (tecla K). Todo lo que se ve en el panel (barra, etapas, contenido, pie y ventanas) lo dibuja y
 * lo atiende la vista común {@link StudioView}, la misma que pintan las maquetas y prueban los tests. Esta pantalla
 * solo le pasa el ratón y el teclado y se queda con el mundo: los rayos del cursor, girar la cámara con clic derecho y
 * andar con WASD mientras no se escribe. Lo que hace el botón izquierdo en el mundo lo decide la vista según la etapa
 * ({@link StudioView#worldAction}) y lo hacen los modelos sin Minecraft: puntos que se arrastran ({@link PathDrag}),
 * la caja de selección con sus caras ({@link BoxSelect}) y el trazo del pincel ({@link BrushStroke}); Alt+clic es el
 * cuentagotas. Con un fantasma del gizmo en pantalla, el cursor agarra sus flechas, anillos y cubo, la rueda lo acerca
 * o aleja y sus teclas van primero.
 */
public final class ProceduralStudioScreen extends Screen {
    /** El Studio sigue igual al reabrirlo (etapa, receta, herramienta, ajustes); no al salir del mundo. */
    private static StudioView view = new StudioView();

    private StudioUi ui;
    private DrawContextCanvas canvas;
    private ClientStudioHost host;
    private boolean cameraDragging;
    private boolean draggingPoint;
    private int draggedPoint = -1;
    private int draggedAxis = -1;
    private BlockPos axisDragOrigin;
    private double axisDragStart;
    private BlockPos lastDraggedPosition;
    private final PathDrag pathDrag = new PathDrag();
    private final BoxSelect box = new BoxSelect();
    private final BrushStroke stroke = new BrushStroke();
    /** Mientras se arrastra la caja, se manda a WorldEdit como mucho cada tanto (se dibuja al momento igual). */
    private static final long BOX_SEND_MILLIS = 60L;
    private long lastBoxSend;
    private boolean boxPending;

    public ProceduralStudioScreen() {
        super(Text.translatable("gui.komaedit.title"));
    }

    /** Al salir del mundo: el Studio vuelve a leer sus preferencias (el ancho del panel va en ellas). */
    public static void resetSession() {
        view = new StudioView();
    }

    /** {@code /komaedit tutorial}: abre el Studio (si no está abierto) con la bienvenida guiada desde el principio. */
    public static void openWelcome(MinecraftClient client) {
        view.startWelcome();
        if (!(client.currentScreen instanceof ProceduralStudioScreen) && client.player != null) {
            client.setScreen(new ProceduralStudioScreen());
        }
    }

    /** La receta que enseña el holograma: la de la figura en curso. */
    public static ProceduralRecipe previewRecipe() {
        return view.figure().draft();
    }

    @Override
    protected void init() {
        canvas = new DrawContextCanvas(textRenderer);
        ui = new StudioUi(canvas);
        host = new ClientStudioHost(client, view, this::close);
        ClientStudioHost.clearNames();
        sendCommand("komaedit path mode on");
        ClientPlayNetworking.send(new RecipeActionPayload(RecipeActionPayload.SYNC, view.figure().draft()));
        if (view.stage() == StudioStage.BLUEPRINTS) {
            view.blueprints().opened(host);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        if (view.typing()) {
            // Mientras se escribe en un campo, W, A, S, D y el espacio son letras, no movimiento.
            releaseMovementKeys();
        } else {
            updateMovementKeys();
        }
        if (client != null) {
            ProceduralPreviewRenderer.request(view.figure().draft(), PathHologramRenderer.points(),
                    PathHologramRenderer.closed());
            ProceduralPreviewRenderer.tick(client);
        }
        view.tick(host);
    }

    @Override
    public void removed() {
        releaseMovementKeys();
        EditorOverlayRenderer.clear();
        view.closed(host);
        WorldOverlayRenderer.face(null, false);
        WorldOverlayRenderer.studioCursor(null);
        sendCommand("komaedit path mode off");
        // Fuera del Studio todo vuelve a ir donde se mira.
        ClientStudioAim.clear();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        canvas.begin(context);
        updateHover(mouseX, mouseY);
        if (GhostClient.active() && overWorld(mouseX)) {
            GhostClient.studioHover(gizmoRay(mouseX, mouseY));
        }
        view.render(ui, width, height, mouseX, mouseY, host);
    }

    // ── Ratón ───────────────────────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        StudioView.Click click = view.mouseClicked(mouseX, mouseY, studioButton(button), host);
        switch (click) {
            case WORLD -> {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    worldPressed(mouseX, mouseY);
                }
            }
            case CAMERA -> cameraDragging = true;
            default -> {
            }
        }
        return true;
    }

    /**
     * El botón izquierdo en el mundo. Alt+clic es el cuentagotas y las flechas, anillos y cubo del gizmo se agarran
     * siempre; lo demás, lo que diga la etapa ({@link StudioView#worldAction}): puntos, colocar, seleccionar, pintar
     * o mover el fantasma. El rayo sale del cursor, no del centro de la pantalla.
     */
    private void worldPressed(double mouseX, double mouseY) {
        BlockHitResult hit = cursorHit(mouseX, mouseY);
        if (hasAltDown()) {
            eyedropper(hit);
            return;
        }
        if (GhostClient.studioPress(gizmoRay(mouseX, mouseY))) {
            return;
        }
        switch (view.worldAction(host)) {
            case GHOST -> GhostClient.studioPlace(hit);
            case POINTS -> pointsPressed(mouseX, mouseY, hit);
            case PLACE -> placeAt(hit);
            case SELECT -> selectPressed(mouseX, mouseY, hit);
            case PAINT -> paintPressed(hit);
            case NONE -> view.status(view.worldHint(host), System.currentTimeMillis());
        }
    }

    /** Construir ahí lo elegido (árbol, roca, texto, el plano elegido): el servidor sabe antes en qué bloque. */
    private void placeAt(BlockHitResult hit) {
        if (hit == null) {
            view.status(tr("gui.komaedit.status.click.no_block"), System.currentTimeMillis());
            return;
        }
        ClientStudioAim.send(hit);
        view.placeAtClick(host);
    }

    /** Alt+clic: el bloque pulsado pasa a ser el material de lo que se está haciendo. */
    private void eyedropper(BlockHitResult hit) {
        if (hit == null || client == null || client.world == null) {
            view.status(tr("gui.komaedit.status.click.no_block"), System.currentTimeMillis());
            return;
        }
        BlockState state = client.world.getBlockState(hit.getBlockPos());
        if (state.isAir()) {
            view.status(tr("gui.komaedit.status.click.no_block"), System.currentTimeMillis());
            return;
        }
        view.eyedropper(Registries.BLOCK.getId(state.getBlock()).toString(), hasShiftDown(), host);
    }

    /**
     * Los puntos de la trayectoria: las flechas del punto elegido lo mueven en un eje; si no, se agarra un punto, se
     * mete uno en un tramo o se añade uno al final, y arrastrando va por el terreno ({@link PathDrag}).
     */
    private void pointsPressed(double mouseX, double mouseY, BlockHitResult hit) {
        Ray ray = rayForMouse(mouseX, mouseY);
        List<BlockPos> points = PathHologramRenderer.points();
        int selectedPoint = view.figure().selectedPoint();
        BlockPos selectedPosition = selectedPoint >= 0 && selectedPoint < points.size()
                ? points.get(selectedPoint)
                : null;
        int axis = selectedPosition == null ? -1 : pickGizmoAxis(ray, selectedPosition);
        if (axis >= 0 && beginAxisDrag(ray, selectedPosition, axis)) {
            return;
        }
        int[] place = hit == null ? null : array(hit.getBlockPos().offset(hit.getSide()));
        PathDrag.Press press = pathDrag.press(gizmoRay(mouseX, mouseY), arrays(points), PathHologramRenderer.closed(),
                place, FigureStages.MAX_PATH_POINTS);
        long now = System.currentTimeMillis();
        switch (press.grab()) {
            case POINT -> {
                view.figure().selectPoint(press.index());
                view.status(tr("gui.komaedit.status.node.grabbed", press.index() + 1), now);
            }
            case INSERT -> {
                PathHologramRenderer.insertLocal(press.index(), midpoint(points, press.after()));
                sendEditor(EditorActionPayload.PATH_INSERT, press.after(), BlockPos.ORIGIN);
                view.figure().selectPoint(press.index());
                view.status(tr("gui.komaedit.status.node.inserted", press.after() + 1,
                        (press.after() + 1) % points.size() + 1), now);
            }
            case ADD -> {
                BlockPos position = new BlockPos(place[0], place[1], place[2]);
                PathHologramRenderer.insertLocal(points.size(), position);
                sendEditor(EditorActionPayload.PATH_ADD, -1, position);
                view.figure().selectPoint(-1);
                view.status(tr("gui.komaedit.status.node.added", coordinates(position)), now);
            }
            case NONE -> view.status(tr(points.size() >= FigureStages.MAX_PATH_POINTS ? "gui.komaedit.status.path.full"
                    : "gui.komaedit.status.click.no_block", FigureStages.MAX_PATH_POINTS), now);
        }
    }

    /** El punto en medio del tramo que empieza en {@code after} (cerrado, el último va al primero). */
    private static BlockPos midpoint(List<BlockPos> points, int after) {
        BlockPos start = points.get(after);
        BlockPos end = points.get((after + 1) % points.size());
        return new BlockPos(Math.floorDiv(start.getX() + end.getX(), 2), Math.floorDiv(start.getY() + end.getY(), 2),
                Math.floorDiv(start.getZ() + end.getZ(), 2));
    }

    /**
     * Seleccionar: pulsar sobre una cara de la selección la agarra; si no, empieza una caja en el bloque pulsado. Doble
     * clic sin arrastrar, al soltar, pide la selección mágica ({@link BoxSelect}).
     */
    private void selectPressed(double mouseX, double mouseY, BlockHitResult hit) {
        GizmoMath.Ray ray = gizmoRay(mouseX, mouseY);
        box.known(knownBox());
        BoxSelect.Box started = box.press(ray, hit == null ? null : array(hit.getBlockPos()), reach(hit),
                System.currentTimeMillis());
        if (started == null) {
            view.status(tr("gui.komaedit.status.click.no_block"), System.currentTimeMillis());
            return;
        }
        if (box.pulling() != null) {
            WorldOverlayRenderer.face(box.pulling(), true);
            view.status(tr("gui.komaedit.status.select.pulling", tr("gui.komaedit.world.face." + box.pulling().name()
                    .toLowerCase(java.util.Locale.ROOT))), System.currentTimeMillis());
        } else {
            sendBox(started, true);
        }
    }

    /** La selección que tiene el jugador según el servidor, para poder tirar de sus caras. */
    private static BoxSelect.Box knownBox() {
        SelectionPayload selection = WorldOverlayRenderer.selection();
        if (!selection.visible() || !selection.complete()) {
            return null;
        }
        return new BoxSelect.Box(selection.minX(), selection.minY(), selection.minZ(), selection.maxX(),
                selection.maxY(), selection.maxZ());
    }

    /**
     * Manda la caja a WorldEdit (las dos esquinas) y la dibuja ya, sin esperar al servidor, con sus medidas. Mientras
     * se arrastra, como mucho cada {@value #BOX_SEND_MILLIS} ms; al soltar, siempre.
     */
    private void sendBox(BoxSelect.Box selection, boolean force) {
        WorldOverlayRenderer.selection(new SelectionPayload(true, true, selection.minX(), selection.minY(),
                selection.minZ(), selection.maxX(), selection.maxY(), selection.maxZ(), "cuboid"));
        long now = System.currentTimeMillis();
        if (!force && now - lastBoxSend < BOX_SEND_MILLIS) {
            boxPending = true;
            return;
        }
        lastBoxSend = now;
        boxPending = false;
        sendEditor(EditorActionPayload.BOX_PRIMARY, -1, new BlockPos(selection.minX(), selection.minY(),
                selection.minZ()));
        sendEditor(EditorActionPayload.BOX_SECONDARY, 0, new BlockPos(selection.maxX(), selection.maxY(),
                selection.maxZ()));
        view.status(tr("gui.komaedit.status.select.box", selection.sizeX(), selection.sizeY(), selection.sizeZ()),
                now);
    }

    /** Pintar con el pincel de la mano donde apunta el cursor (el servidor apunta al bloque del clic). */
    private void paintPressed(BlockHitResult hit) {
        if (hit == null) {
            view.status(tr("gui.komaedit.status.click.no_block"), System.currentTimeMillis());
            return;
        }
        if (stroke.press(array(hit.getBlockPos()), System.currentTimeMillis())) {
            paint(hit);
        }
    }

    private void paint(BlockHitResult hit) {
        ClientStudioAim.send(hit);
        ClientPlayNetworking.send(new BrushToolPayload(BrushInput.of(BrushInput.Base.RIGHT, hasShiftDown())));
        view.brushUsed();
    }

    /** El bloque al que apunta el cursor (hasta {@link FigureStages#REACH} bloques), o {@code null}. */
    private BlockHitResult cursorHit(double mouseX, double mouseY) {
        if (client == null || client.world == null || client.player == null) {
            return null;
        }
        Ray ray = rayForMouse(mouseX, mouseY);
        HitResult result = client.world.raycast(new RaycastContext(ray.origin(),
                ray.origin().add(ray.direction().multiply(FigureStages.REACH)), RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, client.player));
        return result instanceof BlockHitResult hit && result.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** A qué distancia está lo que se ve bajo el cursor (lo de más allá queda tapado). */
    private double reach(BlockHitResult hit) {
        if (hit == null || client == null) {
            return FigureStages.REACH;
        }
        return client.gameRenderer.getCamera().getPos().distanceTo(hit.getPos());
    }

    private static int[] array(BlockPos position) {
        return new int[]{position.getX(), position.getY(), position.getZ()};
    }

    private static List<int[]> arrays(List<BlockPos> points) {
        return points.stream().map(ProceduralStudioScreen::array).toList();
    }

    private static int studioButton(int button) {
        return switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> 0;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> 1;
            default -> 2;
        };
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (GhostClient.studioDragging() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            GhostClient.studioDrag(gizmoRay(mouseX, mouseY));
            return true;
        }
        if (GhostClient.studioTerrainDragging() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            GhostClient.studioTerrain(cursorHit(mouseX, mouseY));
            return true;
        }
        if (view.mouseDragged(mouseX, mouseY, studioButton(button), host)) {
            return true;
        }
        if (cameraDragging && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && client != null && client.player != null) {
            client.player.changeLookDirection(-deltaX * 1.55, -deltaY * 1.55);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (draggingPoint && draggedPoint >= 0 && draggedAxis >= 0 && axisDragOrigin != null) {
            dragAxis(mouseX, mouseY);
        } else if (pathDrag.active()) {
            BlockHitResult hit = cursorHit(mouseX, mouseY);
            int[] next = pathDrag.drag(hit == null ? null : array(hit.getBlockPos().offset(hit.getSide())));
            if (next != null) {
                BlockPos position = new BlockPos(next[0], next[1], next[2]);
                PathHologramRenderer.moveLocal(pathDrag.index(), position);
                sendEditor(EditorActionPayload.PATH_MOVE, pathDrag.index(), position);
                view.status(tr("gui.komaedit.status.node.dragging", pathDrag.index() + 1, coordinates(position)),
                        System.currentTimeMillis());
            }
        } else if (box.active()) {
            BlockHitResult hit = cursorHit(mouseX, mouseY);
            BoxSelect.Box next = box.drag(gizmoRay(mouseX, mouseY), hit == null ? null : array(hit.getBlockPos()));
            if (next != null) {
                sendBox(next, false);
            }
        } else if (stroke.active()) {
            BlockHitResult hit = cursorHit(mouseX, mouseY);
            if (hit != null && stroke.drag(array(hit.getBlockPos()), System.currentTimeMillis())) {
                paint(hit);
            }
        }
        return true;
    }

    /** Arrastrar una flecha del punto elegido: el punto se mueve por ese eje, de bloque en bloque. */
    private void dragAxis(double mouseX, double mouseY) {
        Double axisPosition = axisParameter(rayForMouse(mouseX, mouseY), Vec3d.ofCenter(axisDragOrigin), draggedAxis);
        if (axisPosition == null) {
            return;
        }
        int offset = (int) Math.round(axisPosition - axisDragStart);
        BlockPos position = switch (draggedAxis) {
            case 0 -> axisDragOrigin.add(offset, 0, 0);
            case 1 -> axisDragOrigin.add(0, offset, 0);
            default -> axisDragOrigin.add(0, 0, offset);
        };
        if (position.getY() >= -2_048 && position.getY() <= 2_047 && !position.equals(lastDraggedPosition)) {
            PathHologramRenderer.moveLocal(draggedPoint, position);
            sendEditor(EditorActionPayload.PATH_MOVE, draggedPoint, position);
            lastDraggedPosition = position;
            view.status(tr("gui.komaedit.status.node.moving", draggedPoint + 1, axisName(draggedAxis),
                    coordinates(position)), System.currentTimeMillis());
            updateHover((int) mouseX, (int) mouseY);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        view.mouseReleased(mouseX, mouseY, studioButton(button), host);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            GhostClient.studioRelease();
            draggingPoint = false;
            draggedPoint = -1;
            draggedAxis = -1;
            axisDragOrigin = null;
            lastDraggedPosition = null;
            pathDrag.release();
            stroke.release();
            if (box.active()) {
                BlockHitResult hit = cursorHit(mouseX, mouseY);
                BoxSelect.Result result = box.release(hit == null ? null : array(hit.getBlockPos()),
                        System.currentTimeMillis());
                WorldOverlayRenderer.face(null, false);
                if (result.magic() && hit != null) {
                    sendEditor(EditorActionPayload.MAGIC_AREA, 0, hit.getBlockPos());
                    view.status(tr("gui.komaedit.status.select.magic"), System.currentTimeMillis());
                } else if (result.box() != null && boxPending) {
                    sendBox(result.box(), true);
                }
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            cameraDragging = false;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (view.mouseScrolled(mouseX, mouseY, verticalAmount, host)) {
            return true;
        }
        if (overWorld(mouseX) && GhostClient.studioScroll(verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ── Teclado ─────────────────────────────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (control && keyCode == GLFW.GLFW_KEY_V && client != null && (view.typing()
                || !view.modal() && StudioView.shareCode(client.keyboard.getClipboard()))) {
            view.paste(client.keyboard.getClipboard(), host);
            return true;
        }
        // Las teclas del fantasma van antes que las del panel (salvo escribiendo en un campo): Intro lo coloca y
        // Esc lo quita sin cerrar el Studio.
        if (!view.typing() && !view.modal() && GhostClient.studioKey(keyCode, modifiers)) {
            return true;
        }
        StudioKey key = studioKey(keyCode, control, shift);
        if (view.key(key, host)) {
            return true;
        }
        if (view.typing()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (view.charTyped(chr, host)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private static StudioKey studioKey(int keyCode, boolean control, boolean shift) {
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return StudioKey.letter((char) ('A' + keyCode - GLFW.GLFW_KEY_A), control, shift);
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return new StudioKey(StudioKey.Code.DIGIT, (char) ('0' + keyCode - GLFW.GLFW_KEY_0), control, shift);
        }
        StudioKey.Code code = switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> StudioKey.Code.ENTER;
            case GLFW.GLFW_KEY_ESCAPE -> StudioKey.Code.ESCAPE;
            case GLFW.GLFW_KEY_BACKSPACE -> StudioKey.Code.BACKSPACE;
            case GLFW.GLFW_KEY_DELETE -> StudioKey.Code.DELETE;
            case GLFW.GLFW_KEY_TAB -> StudioKey.Code.TAB;
            case GLFW.GLFW_KEY_UP -> StudioKey.Code.UP;
            case GLFW.GLFW_KEY_DOWN -> StudioKey.Code.DOWN;
            case GLFW.GLFW_KEY_LEFT -> StudioKey.Code.LEFT;
            case GLFW.GLFW_KEY_RIGHT -> StudioKey.Code.RIGHT;
            case GLFW.GLFW_KEY_PAGE_UP -> StudioKey.Code.PAGE_UP;
            case GLFW.GLFW_KEY_PAGE_DOWN -> StudioKey.Code.PAGE_DOWN;
            case GLFW.GLFW_KEY_HOME -> StudioKey.Code.HOME;
            case GLFW.GLFW_KEY_END -> StudioKey.Code.END;
            case GLFW.GLFW_KEY_F1 -> StudioKey.Code.F1;
            default -> StudioKey.Code.OTHER;
        };
        return new StudioKey(code, '\0', control, shift);
    }

    // ── Mundo: puntero, rayos y flechas ─────────────────────────────────────────────────────────────────

    /**
     * Lo que se ve en el mundo bajo el cursor, según lo que haría un clic: el bloque donde iría un punto (y las flechas
     * del punto elegido), la cara de la selección que se agarraría o el círculo del pincel.
     */
    private void updateHover(int mouseX, int mouseY) {
        BlockPos overlay = null;
        boolean overWorld = view.frame() == null || mouseX < view.frame().panel().x();
        StudioView.WorldAction action = view.worldAction(host);
        // Solo se lanza el rayo cuando lo que hay bajo el cursor cambia lo que se ve (el fantasma tiene el suyo).
        boolean aims = action == StudioView.WorldAction.POINTS || action == StudioView.WorldAction.SELECT
                || action == StudioView.WorldAction.PAINT;
        BlockHitResult hit = overWorld && aims ? cursorHit(mouseX, mouseY) : null;
        if (action == StudioView.WorldAction.POINTS && hit != null && !pathDrag.active()) {
            overlay = hit.getBlockPos().offset(hit.getSide()).toImmutable();
        }
        WorldOverlayRenderer.studioCursor(action == StudioView.WorldAction.PAINT ? hit : null);
        if (box.active()) {
            WorldOverlayRenderer.face(box.pulling(), true);
        } else if (action == StudioView.WorldAction.SELECT && overWorld) {
            box.known(knownBox());
            WorldOverlayRenderer.face(box.faceUnder(gizmoRay(mouseX, mouseY), reach(hit)), false);
        } else {
            WorldOverlayRenderer.face(null, false);
        }
        List<BlockPos> points = PathHologramRenderer.points();
        if (view.figure().selectedPoint() >= points.size()) {
            view.figure().selectPoint(-1);
        }
        int selectedPoint = view.figure().selectedPoint();
        BlockPos gizmoOrigin = action == StudioView.WorldAction.POINTS && selectedPoint >= 0
                ? points.get(selectedPoint)
                : null;
        EditorOverlayRenderer.update(
                true,
                EditorOverlayRenderer.PATH,
                overlay,
                view.figure().draft().radius(),
                null,
                null,
                selectedPoint,
                gizmoOrigin,
                draggedAxis
        );
    }

    private boolean overWorld(double mouseX) {
        return !view.modal() && (view.frame() == null || mouseX < view.frame().panel().x());
    }

    /** El rayo del cursor para el gizmo del fantasma. */
    private GizmoMath.Ray gizmoRay(double mouseX, double mouseY) {
        Ray ray = rayForMouse(mouseX, mouseY);
        return new GizmoMath.Ray(new com.komainu.moxia.shape.Vec3(ray.origin().x, ray.origin().y, ray.origin().z),
                new com.komainu.moxia.shape.Vec3(ray.direction().x, ray.direction().y, ray.direction().z));
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

    private boolean beginAxisDrag(Ray ray, BlockPos origin, int axis) {
        Double parameter = axisParameter(ray, Vec3d.ofCenter(origin), axis);
        if (parameter == null) {
            return false;
        }
        int selectedPoint = view.figure().selectedPoint();
        draggingPoint = true;
        draggedPoint = selectedPoint;
        draggedAxis = axis;
        axisDragOrigin = origin.toImmutable();
        axisDragStart = parameter;
        lastDraggedPosition = origin;
        view.status(tr("gui.komaedit.status.node.adjusting", selectedPoint + 1, axisName(axis)),
                System.currentTimeMillis());
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

    // ── Envío y movimiento ──────────────────────────────────────────────────────────────────────────────

    private void sendEditor(int action, int index, BlockPos position) {
        ProceduralRecipe draft = view.figure().draft();
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

    private static boolean down(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + ", " + position.getY() + ", " + position.getZ();
    }

    private static String tr(String key, Object... arguments) {
        return Text.translatable(key, arguments).getString();
    }

    private record Ray(Vec3d origin, Vec3d direction) {
    }

    private record AxisProjection(double rayDistance, double axisDistance, double separation) {
    }
}
