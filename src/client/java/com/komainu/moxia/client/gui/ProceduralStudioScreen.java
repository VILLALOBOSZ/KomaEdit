package com.komainu.moxia.client.gui;

import com.komainu.moxia.client.gizmo.GhostClient;
import com.komainu.moxia.client.gui.studio.ClientStudioHost;
import com.komainu.moxia.client.gui.studio.DrawContextCanvas;
import com.komainu.moxia.client.mixin.GameRendererAccessor;
import com.komainu.moxia.client.preview.EditorOverlayRenderer;
import com.komainu.moxia.client.preview.PathHologramRenderer;
import com.komainu.moxia.client.preview.ProceduralPreviewRenderer;
import com.komainu.moxia.editor.EditorActionPayload;
import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.recipe.RecipeActionPayload;
import com.komainu.moxia.studio.ui.FigureStages;
import com.komainu.moxia.studio.ui.StudioKey;
import com.komainu.moxia.studio.ui.StudioStage;
import com.komainu.moxia.studio.ui.StudioUi;
import com.komainu.moxia.studio.ui.StudioView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Camera;
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
 * solo le pasa el ratón y el teclado y se queda con el mundo: el puntero, los puntos de la trayectoria, sus flechas
 * para moverlos, girar la cámara con clic derecho y andar con WASD mientras no se escribe. Con un fantasma del gizmo
 * en pantalla, el cursor agarra sus flechas, anillos y cubo, la rueda lo acerca o aleja y sus teclas van primero.
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
    private BlockPos hoverPlacement;
    private BlockPos lastDraggedPosition;

    public ProceduralStudioScreen() {
        super(Text.translatable("gui.komaedit.title"));
    }

    /** Al salir del mundo: el Studio vuelve a leer sus preferencias (el ancho del panel va en ellas). */
    public static void resetSession() {
        view = new StudioView();
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
        sendCommand("komaedit path mode off");
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
        if (click == StudioView.Click.WORLD && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && GhostClient.studioPress(gizmoRay(mouseX, mouseY))) {
            return true;
        }
        switch (click) {
            case WORLD -> {
                if (view.stage() == StudioStage.PATH) {
                    worldClicked(mouseX, mouseY);
                }
            }
            case CAMERA -> cameraDragging = true;
            default -> {
            }
        }
        return true;
    }

    private static int studioButton(int button) {
        return switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> 0;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> 1;
            default -> 2;
        };
    }

    /** Clic en el mundo en la etapa Trayectoria: flecha del nodo elegido, otro nodo o un punto nuevo. */
    private void worldClicked(double mouseX, double mouseY) {
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
        int pickedPoint = pickPathPoint(ray);
        if (pickedPoint >= 0) {
            view.figure().selectPoint(pickedPoint);
            view.status(tr("gui.komaedit.status.node.selected", pickedPoint + 1), System.currentTimeMillis());
        } else if (hoverPlacement != null) {
            sendEditor(EditorActionPayload.PATH_ADD, -1, hoverPlacement);
            view.status(tr("gui.komaedit.status.node.added", coordinates(hoverPlacement)), System.currentTimeMillis());
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (GhostClient.studioDragging() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            GhostClient.studioDrag(gizmoRay(mouseX, mouseY));
            return true;
        }
        if (view.mouseDragged(mouseX, mouseY, studioButton(button), host)) {
            return true;
        }
        if (cameraDragging && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && client != null && client.player != null) {
            client.player.changeLookDirection(-deltaX * 1.55, -deltaY * 1.55);
            return true;
        }
        if (draggingPoint && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggedPoint >= 0
                && draggedAxis >= 0 && axisDragOrigin != null) {
            Double axisPosition = axisParameter(rayForMouse(mouseX, mouseY), Vec3d.ofCenter(axisDragOrigin),
                    draggedAxis);
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
                    view.status(tr("gui.komaedit.status.node.moving", draggedPoint + 1, axisName(draggedAxis),
                            coordinates(position)), System.currentTimeMillis());
                    updateHover((int) mouseX, (int) mouseY);
                }
            }
        }
        return true;
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
        if (control && keyCode == GLFW.GLFW_KEY_V && view.typing() && client != null) {
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

    private void updateHover(int mouseX, int mouseY) {
        hoverPlacement = null;
        BlockPos overlay = null;
        boolean overWorld = view.frame() == null || mouseX < view.frame().panel().x();
        if (!view.modal() && view.stage() == StudioStage.PATH && overWorld && client != null
                && client.world != null && client.player != null) {
            Ray ray = rayForMouse(mouseX, mouseY);
            HitResult result = client.world.raycast(new RaycastContext(
                    ray.origin(),
                    ray.origin().add(ray.direction().multiply(FigureStages.REACH)),
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
        if (view.figure().selectedPoint() >= points.size()) {
            view.figure().selectPoint(-1);
        }
        int selectedPoint = view.figure().selectedPoint();
        BlockPos gizmoOrigin = view.stage() == StudioStage.PATH && selectedPoint >= 0
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
