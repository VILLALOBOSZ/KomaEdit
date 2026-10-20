package com.komainu.moxia.client.gizmo;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.client.mixin.GameRendererAccessor;
import com.komainu.moxia.client.world.GhostRenderer;
import com.komainu.moxia.client.world.GizmoRenderer;
import com.komainu.moxia.client.world.WorldFeedbackClient;
import com.komainu.moxia.client.world.WorldLabels;
import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.gizmo.GhostOptions;
import com.komainu.moxia.gizmo.GhostPayloads;
import com.komainu.moxia.gizmo.GhostSession;
import com.komainu.moxia.gizmo.GhostStates;
import com.komainu.moxia.gizmo.GhostTransform;
import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.gizmo.GizmoHandle;
import com.komainu.moxia.gizmo.GizmoKeys;
import com.komainu.moxia.gizmo.GizmoLayout;
import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.gizmo.GroundSnap;
import com.komainu.moxia.gizmo.Orientation;
import com.komainu.moxia.preview.PreviewPlanCodec;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.studio.ui.StudioHost;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * El fantasma con gizmo en el cliente: recibe el objeto, lo mueve con el teclado, la rueda, «a donde miro» y el
 * gizmo, lo dibuja y manda solo la transformación al confirmar.
 *
 * <p>El ratón: en el mundo no se libera. El punto de mira es el puntero, como en el resto de Minecraft (y en el modo
 * constructor de Axiom): se apunta a una flecha, un anillo o el cubo, se mantiene el clic izquierdo y se gira la
 * cámara; WASD y el vuelo siguen funcionando mientras tanto. En el Studio, que ya suelta el ratón, el puntero es el
 * cursor. Así no hay ningún modo que activar para mover el fantasma.</p>
 */
public final class GhostClient {
    private static final double REACH = 192.0;
    private static final int HUD_EVERY_TICKS = 10;
    private static final int BOX = 0xC07FD7FF;
    /** Lo que puede tardar en llegar el fantasma que pidió el Studio. */
    private static final long EXPECT_MILLIS = 20_000L;
    private static final PreviewPlanCodec.Assembler ASSEMBLER = new PreviewPlanCodec.Assembler();
    private static final Map<GizmoKeys.Action, KeyBinding> BINDINGS = new EnumMap<>(GizmoKeys.Action.class);

    private static GhostSession session;
    private static int[] palette = new int[0];
    private static GroundSnap.Footprint footprint;
    private static Orientation footprintFor;
    private static GizmoHandle hovered = GizmoHandle.NONE;
    private static boolean worldDrag;
    private static boolean studioDrag;
    private static int ticks;
    private static String notice = "";
    private static long noticeAt;
    private static long studioUntil;
    private static boolean refreshPending;
    /** El fantasma de ahora lo abrió el Studio (su vista previa en vivo lo puede rehacer). */
    private static boolean fromStudio;
    private static boolean terrainDrag;
    /** El bloque y la cara a los que apuntaba el cursor la última vez al arrastrar por el terreno. */
    private static int[] terrainLast;

    private GhostClient() {
    }

    /**
     * El Studio va a pedir un fantasma. Con {@code refresh} es la vista previa en vivo: el que llegue sustituye al de
     * ahora y sale donde el jugador lo dejó ({@link GhostSession#follow}).
     */
    public static void expectStudio(boolean refresh) {
        studioUntil = System.currentTimeMillis() + EXPECT_MILLIS;
        refreshPending = refresh;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(GhostPayloads.Open.ID, (payload, context) ->
                context.client().execute(() -> open(payload, context.client())));
        ClientPlayNetworking.registerGlobalReceiver(GhostPayloads.Part.ID, (payload, context) ->
                context.client().execute(() -> part(payload, context.client())));
        ClientPlayNetworking.registerGlobalReceiver(GhostPayloads.Result.ID, (payload, context) ->
                context.client().execute(() -> result(payload)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(ClientGizmoSettings::announce));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(GhostClient::close));
        WorldRenderEvents.AFTER_ENTITIES.register(GhostClient::renderBox);
        WorldRenderEvents.LAST.register(GhostClient::render);
        ClientTickEvents.END_CLIENT_TICK.register(GhostClient::tick);
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> onAttack(client, clickCount));
        for (GizmoKeys.Action action : GizmoKeys.Action.values()) {
            if (action != GizmoKeys.Action.CONFIRM && action != GizmoKeys.Action.CANCEL) {
                BINDINGS.put(action, KeyBindingHelper.registerKeyBinding(new KeyBinding("key.komaedit.ghost_"
                        + action.id(), InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "category.komaedit")));
            }
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("kegizmo")
                        .then(literal("colocar").executes(context -> command(GizmoKeys.Action.CONFIRM, false)))
                        .then(literal("copia").executes(context -> command(GizmoKeys.Action.CONFIRM, true)))
                        .then(literal("cancelar").executes(context -> command(GizmoKeys.Action.CANCEL, false)))
                        .then(literal("conservar").executes(context -> command(GizmoKeys.Action.KEEP, false)))
                        .then(literal("aire").executes(context -> command(GizmoKeys.Action.AIR, false)))
                        .then(literal("suelo").executes(context -> command(GizmoKeys.Action.GROUND, false)))
                        .then(literal("girar").executes(context -> command(GizmoKeys.Action.ROTATE, false)))
                        .then(literal("voltear").executes(context -> command(GizmoKeys.Action.FLIP, false)))
                        .then(literal("aqui").executes(context -> command(GizmoKeys.Action.HERE, false)))));
    }

    /** Si hay un fantasma que se puede mover. */
    public static boolean active() {
        return session != null && session.active();
    }

    public static boolean open() {
        return session != null && session.open();
    }

    // ---------------------------------------------------------------- red

    private static void open(GhostPayloads.Open payload, MinecraftClient client) {
        boolean studio = System.currentTimeMillis() < studioUntil;
        GhostSession previous = studio && refreshPending && fromStudio && session != null && session.active()
                ? session : null;
        studioUntil = 0L;
        refreshPending = false;
        close();
        GhostOptions options = GhostOptions.fromBits(payload.options()).withGround(false);
        session = new GhostSession(new GhostSession.Info(payload.id(), payload.description(), payload.bounds(),
                payload.blocks(), payload.air(), GhostTransform.at(payload.pivotX(), payload.pivotY(),
                payload.pivotZ()), options));
        palette = payload.palette();
        fromStudio = studio;
        ASSEMBLER.clear();
        if (previous != null) {
            // La vista previa en vivo: el mismo objeto con otros ajustes, donde el jugador lo dejó y sin repetir el
            // aviso del chat.
            session.follow(previous, RULES);
            hud(client);
            return;
        }
        announce(client);
    }

    private static void part(GhostPayloads.Part payload, MinecraftClient client) {
        if (session == null || session.info().id() != payload.id()) {
            return;
        }
        try {
            ASSEMBLER.accept(payload.id(), payload.part(), payload.parts(), payload.data())
                    .ifPresent(plan -> GhostRenderer.load(plan, session.transform().orientation(), client));
        } catch (IllegalArgumentException exception) {
            KomaEditMod.LOGGER.warn("Discarding a broken KomaEdit ghost: {}", exception.getMessage());
            ASSEMBLER.clear();
        }
    }

    private static void result(GhostPayloads.Result payload) {
        if (session == null || session.info().id() != payload.id()) {
            return;
        }
        switch (payload.outcome()) {
            case PLACED -> {
                session.placed(false);
                notice(payload.message());
                close();
            }
            case COPY_PLACED -> {
                session.placed(true);
                notice(payload.message());
            }
            case REJECTED -> {
                session.rejected(payload.message());
                notice(payload.message());
            }
            case CLOSED -> {
                session.closed();
                notice(payload.message());
                close();
            }
            case CONFIRM -> confirm(false);
        }
    }

    private static void close() {
        session = null;
        palette = new int[0];
        footprint = null;
        footprintFor = null;
        hovered = GizmoHandle.NONE;
        worldDrag = false;
        studioDrag = false;
        fromStudio = false;
        terrainDrag = false;
        terrainLast = null;
        ASSEMBLER.clear();
        GhostRenderer.clear();
    }

    private static void confirm(boolean copy) {
        if (session == null) {
            return;
        }
        session.confirm(copy).ifPresent(confirm -> {
            if (ClientPlayNetworking.canSend(GhostPayloads.Confirm.ID)) {
                ClientPlayNetworking.send(GhostPayloads.Confirm.of(confirm));
                notice(Text.translatable(copy ? "gui.komaedit.ghost.copying" : "gui.komaedit.ghost.placing")
                        .getString());
            }
        });
    }

    private static void cancel() {
        if (session != null && session.cancel() && ClientPlayNetworking.canSend(GhostPayloads.Cancel.ID)) {
            ClientPlayNetworking.send(new GhostPayloads.Cancel(session.info().id()));
            notice(Text.translatable("gui.komaedit.ghost.cancelled").getString());
        }
        close();
    }

    // ---------------------------------------------------------------- reglas

    private static final GhostSession.Rules RULES = orientation -> GhostStates.admitsAll(palette, orientation);

    private static OptionalInt rest(GhostTransform transform) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !GhostRenderer.loaded()) {
            return OptionalInt.empty();
        }
        if (footprint == null || footprintFor != transform.orientation()) {
            footprint = GroundSnap.footprint(com.komainu.moxia.gizmo.GhostPlacement.orientPositions(
                    GhostRenderer.positions(), transform.orientation()));
            footprintFor = transform.orientation();
        }
        BlockPos.Mutable position = new BlockPos.Mutable();
        return GroundSnap.rest(footprint, transform, (x, y, z) -> {
            BlockState state = client.world.getBlockState(position.set(x, y, z));
            return !state.isAir() && !state.isReplaceable() && state.getFluidState().isEmpty();
        });
    }

    private static final GhostSession.Ground GROUND = GhostClient::rest;

    // ---------------------------------------------------------------- acciones

    private static int command(GizmoKeys.Action action, boolean control) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> perform(new GizmoKeys.Press(action, false, control, false), client));
        return 1;
    }

    /** Lo que hace cada tecla con el fantasma abierto. */
    static void perform(GizmoKeys.Press press, MinecraftClient client) {
        if (session == null) {
            return;
        }
        Vec3d look = look(client);
        int step = press.step();
        boolean changed = switch (press.action()) {
            case FORWARD, BACK, LEFT, RIGHT -> {
                int[] forward = GizmoMath.forward(look.x, look.z);
                int[] left = GizmoMath.left(forward);
                int[] direction = switch (press.action()) {
                    case FORWARD -> forward;
                    case BACK -> new int[]{-forward[0], -forward[1]};
                    case LEFT -> left;
                    default -> new int[]{-left[0], -left[1]};
                };
                yield session.nudge(direction[0] * step, 0, direction[1] * step, GROUND);
            }
            case UP -> session.nudge(0, step, 0, GROUND);
            case DOWN -> session.nudge(0, -step, 0, GROUND);
            case HERE -> here(client);
            case ROTATE -> {
                if (press.alt()) {
                    int[] forward = GizmoMath.forward(look.x, look.z);
                    int axis = forward[0] != 0 ? Orientation.Z : Orientation.X;
                    boolean turned = session.rotate(axis, press.shift() ? -1 : 1, RULES, GROUND);
                    if (!turned) {
                        notice(Text.translatable("gui.komaedit.ghost.no_tip").getString());
                    }
                    yield turned;
                }
                yield session.rotate(Orientation.Y, press.shift() ? -1 : 1, RULES, GROUND);
            }
            case FLIP -> session.flip(Math.abs(look.y) > 0.8 ? Orientation.Y
                    : Math.abs(look.x) > Math.abs(look.z) ? Orientation.X : Orientation.Z, RULES, GROUND);
            case GROUND -> {
                session.toggleGround(GROUND);
                yield true;
            }
            case KEEP -> {
                session.toggleKeepExisting();
                yield true;
            }
            case AIR -> {
                session.togglePasteAir();
                yield true;
            }
            case PASTE -> {
                confirm(false);
                yield false;
            }
            case CONFIRM -> {
                confirm(press.control());
                yield false;
            }
            case CANCEL -> {
                cancel();
                yield false;
            }
        };
        if (changed && session != null) {
            GhostRenderer.orient(session.transform().orientation(), client);
            hud(client);
        }
    }

    /** «A donde miro»: contra la cara del bloque que se mira, hasta 192 bloques. */
    private static boolean here(MinecraftClient client) {
        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        if (camera == null) {
            return false;
        }
        HitResult hit = camera.raycast(REACH, 1.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            notice(Text.translatable("gui.komaedit.ghost.no_target").getString());
            return false;
        }
        BlockPos pos = block.getBlockPos();
        return session.placeAgainst(pos.getX(), pos.getY(), pos.getZ(), face(block.getSide()), GROUND);
    }

    // ---------------------------------------------------------------- teclado y ratón (en el mundo)

    /**
     * Una tecla en el mundo (sin pantalla abierta), antes que Minecraft.
     *
     * @return {@code true} si era del fantasma y Minecraft no debe verla
     */
    public static boolean onKey(int key, int action, int modifiers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.player == null || action == GLFW.GLFW_RELEASE) {
            return false;
        }
        return key(key, action, modifiers, client);
    }

    private static boolean key(int key, int action, int modifiers, MinecraftClient client) {
        GizmoKeys keys = keys();
        if (session == null || !session.open()) {
            Optional<GizmoKeys.Press> paste = keys.matchIdle(key, modifiers);
            // Solo en un servidor con KomaEdit (si no, la orden no existe).
            if (paste.isPresent() && action == GLFW.GLFW_PRESS && client.player != null
                    && ClientPlayNetworking.canSend(GhostPayloads.Mode.ID)) {
                client.player.networkHandler.sendChatCommand("komaedit paste");
                return true;
            }
            return false;
        }
        Optional<GizmoKeys.Press> press = keys.match(key, modifiers, boundKey(WorldFeedbackClient.confirmKey()),
                boundKey(WorldFeedbackClient.discardKey()));
        if (press.isEmpty()) {
            return false;
        }
        boolean movement = switch (press.get().action()) {
            case FORWARD, BACK, LEFT, RIGHT, UP, DOWN -> true;
            default -> false;
        };
        if (action == GLFW.GLFW_REPEAT && !movement) {
            return true;
        }
        perform(press.get(), client);
        return true;
    }

    /** La rueda en el mundo: acerca o aleja en la dirección de la mirada (con Mayús, de 5 en 5). */
    public static boolean onScroll(double amount) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || !active() || !ClientGizmoSettings.get().wheel() || amount == 0.0) {
            return false;
        }
        push(client, amount);
        return true;
    }

    private static void push(MinecraftClient client, double amount) {
        Vec3d look = look(client);
        int axis = GizmoMath.dominantAxis(new Vec3(look.x, look.y, look.z));
        double component = axis == Orientation.X ? look.x : axis == Orientation.Y ? look.y : look.z;
        int step = (int) Math.signum(amount) * (int) Math.signum(component) * (Screen.hasShiftDown() ? 5 : 1);
        int[] delta = {0, 0, 0};
        delta[axis] = step;
        if (session.nudge(delta[0], delta[1], delta[2], GROUND)) {
            hud(client);
        }
    }

    /** Clic derecho con el fantasma: lo coloca (con Ctrl, una copia y sigue). */
    public static boolean onUse(MinecraftClient client) {
        if (client.currentScreen != null || !open()) {
            return false;
        }
        if (session.active()) {
            confirm(Screen.hasControlDown());
        }
        return true;
    }

    /** Clic izquierdo: agarra la pieza del gizmo bajo el punto de mira; mientras se arrastra no se rompe nada. */
    private static boolean onAttack(MinecraftClient client, int clickCount) {
        if (client.currentScreen != null || !active()) {
            return false;
        }
        if (worldDrag) {
            return true;
        }
        if (clickCount > 0 && hovered != GizmoHandle.NONE) {
            GizmoMath.Ray ray = crosshair(client, 1.0F);
            if (session.beginDrag(hovered, ray, layout(client, 1.0F))) {
                worldDrag = true;
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Studio

    /** El cursor del Studio sobre el mundo. */
    public static void studioHover(GizmoMath.Ray ray) {
        if (active() && !studioDrag) {
            hovered = layout(MinecraftClient.getInstance(), 1.0F).pick(ray);
        }
    }

    /** Clic izquierdo del Studio sobre el mundo: {@code true} si agarró una pieza del gizmo. */
    public static boolean studioPress(GizmoMath.Ray ray) {
        if (!active()) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        GizmoLayout layout = layout(client, 1.0F);
        GizmoHandle handle = layout.pick(ray);
        if (handle != GizmoHandle.NONE && session.beginDrag(handle, ray, layout)) {
            studioDrag = true;
            hovered = handle;
            return true;
        }
        return false;
    }

    public static boolean studioDragging() {
        return studioDrag;
    }

    public static void studioDrag(GizmoMath.Ray ray) {
        if (studioDrag && session != null && session.drag(ray, RULES, GROUND)) {
            GhostRenderer.orient(session.transform().orientation(), MinecraftClient.getInstance());
        }
    }

    public static void studioRelease() {
        if (studioDrag && session != null) {
            session.endDrag(RULES, GROUND);
            GhostRenderer.orient(session.transform().orientation(), MinecraftClient.getInstance());
        }
        studioDrag = false;
        terrainDrag = false;
        terrainLast = null;
    }

    /**
     * Clic del Studio en el mundo que no agarra ninguna pieza del gizmo: el fantasma salta a donde apunta el cursor
     * (contra la cara del bloque, como «a donde miro») y, mientras se mantenga el botón, lo sigue por el terreno.
     *
     * @return si había fantasma que mover
     */
    public static boolean studioPlace(BlockHitResult hit) {
        if (!active() || hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        terrainDrag = true;
        terrainLast = null;
        studioTerrain(hit);
        return true;
    }

    /** Si se está llevando el fantasma por el terreno con el ratón (en el Studio). */
    public static boolean studioTerrainDragging() {
        return terrainDrag;
    }

    /** El cursor del Studio arrastrando el fantasma: el bloque y la cara a los que apunta ahora. */
    public static void studioTerrain(BlockHitResult hit) {
        if (!terrainDrag || session == null || hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        int[] cell = {pos.getX(), pos.getY(), pos.getZ(), face(hit.getSide())};
        if (Arrays.equals(cell, terrainLast)) {
            return;
        }
        terrainLast = cell;
        if (session.placeAgainst(cell[0], cell[1], cell[2], cell[3], GROUND)) {
            GhostRenderer.orient(session.transform().orientation(), MinecraftClient.getInstance());
        }
    }

    /** La cara de un bloque con el número que usa {@link GhostSession#placeAgainst}. */
    static int face(Direction side) {
        return switch (side) {
            case WEST -> 0;
            case EAST -> 1;
            case DOWN -> 2;
            case UP -> 3;
            case NORTH -> 4;
            case SOUTH -> 5;
        };
    }

    /** La rueda sobre el mundo en el Studio. */
    public static boolean studioScroll(double amount) {
        if (!active() || amount == 0.0) {
            return false;
        }
        push(MinecraftClient.getInstance(), amount);
        return true;
    }

    /** Una tecla del Studio (sin escribir en un campo). */
    public static boolean studioKey(int key, int modifiers) {
        if (!open()) {
            return false;
        }
        return key(key, GLFW.GLFW_PRESS, modifiers, MinecraftClient.getInstance());
    }

    /** Lo que enseña el pie del Studio mientras hay fantasma. */
    public static Optional<StudioHost.Ghost> studioFooter() {
        if (session == null || !session.open()) {
            return Optional.empty();
        }
        GhostSession current = session;
        int[] size = current.size();
        GhostTransform transform = current.transform();
        int[] offset = current.offset();
        String moved = offset[0] == 0 && offset[1] == 0 && offset[2] == 0 ? ""
                : " · (" + signed(offset[0]) + ", " + signed(offset[1]) + ", " + signed(offset[2]) + ")";
        return Optional.of(new StudioHost.Ghost(
                current.info().description(),
                rotation(transform.orientation()) + " · " + size[0] + "×" + size[1] + "×" + size[2],
                transform.x() + ", " + transform.y() + ", " + transform.z() + moved,
                current.options().keepExisting(),
                current.options().pasteAir(),
                current.options().ground(),
                current.info().hasAir(),
                !current.active(),
                current.message(),
                () -> confirm(false),
                () -> confirm(true),
                GhostClient::cancel,
                () -> perform(new GizmoKeys.Press(GizmoKeys.Action.KEEP, false, false, false),
                        MinecraftClient.getInstance()),
                () -> perform(new GizmoKeys.Press(GizmoKeys.Action.AIR, false, false, false),
                        MinecraftClient.getInstance()),
                () -> perform(new GizmoKeys.Press(GizmoKeys.Action.GROUND, false, false, false),
                        MinecraftClient.getInstance()),
                fromStudio));
    }

    // ---------------------------------------------------------------- dibujo

    private static void render(WorldRenderContext context) {
        GhostSession current = session;
        if (current == null || !current.open()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        float delta = context.tickCounter().getTickDelta(true);
        try {
            if (client.currentScreen == null) {
                GizmoMath.Ray ray = crosshair(client, delta);
                if (worldDrag) {
                    if (!client.options.attackKey.isPressed() || !current.active()) {
                        current.endDrag(RULES, GROUND);
                        worldDrag = false;
                    } else if (current.drag(ray, RULES, GROUND)) {
                        hud(client);
                    }
                }
                hovered = worldDrag ? current.dragged() : current.active() ? layout(client, delta).pick(ray)
                        : GizmoHandle.NONE;
            }
            GhostRenderer.orient(current.transform().orientation(), client);
            GhostRenderer.render(context, current.transform());
            if (current.active()) {
                GizmoRenderer.render(context, GizmoGeometry.build(layout(client, delta), hovered,
                        current.dragged()));
            }
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.error("KomaEdit ghost failed; closing it", exception);
            close();
        }
    }

    /** La caja del objeto y sus medidas (con la vista previa de siempre: mismo estilo). */
    private static void renderBox(WorldRenderContext context) {
        GhostSession current = session;
        MatrixStack matrices = context.matrixStack();
        if (current == null || !current.open() || matrices == null || context.consumers() == null) {
            return;
        }
        BlockBounds bounds = current.bounds();
        Box box = new Box(bounds.minimumX(), bounds.minimumY(), bounds.minimumZ(), bounds.maximumX() + 1.0,
                bounds.maximumY() + 1.0, bounds.maximumZ() + 1.0);
        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        WorldRenderer.drawBox(matrices, lines, box.expand(0.02), (BOX >> 16 & 0xFF) / 255.0F,
                (BOX >> 8 & 0xFF) / 255.0F, (BOX & 0xFF) / 255.0F, (BOX >>> 24) / 255.0F);
        matrices.pop();
        WorldLabels.dimensions(context, box, BOX & 0xFFFFFF);
    }

    private static GizmoLayout layout(MinecraftClient client, float delta) {
        GhostTransform transform = session.transform();
        Camera camera = client.gameRenderer.getCamera();
        Vec3d position = camera.getPos();
        double fov = ((GameRendererAccessor) client.gameRenderer).komaedit$getFov(camera, delta, true);
        GizmoLayout around = GizmoLayout.around(transform.x(), transform.y(), transform.z(),
                new Vec3(position.x, position.y, position.z), fov, false);
        boolean tipX = RULES.admits(transform.turned(Orientation.X, 1).orientation());
        boolean tipZ = RULES.admits(transform.turned(Orientation.Z, 1).orientation());
        return new GizmoLayout(around.center(), around.size(), tipX, true, tipZ);
    }

    private static GizmoMath.Ray crosshair(MinecraftClient client, float delta) {
        Camera camera = client.gameRenderer.getCamera();
        Vec3d position = camera.getPos();
        Vec3d look = look(client);
        return new GizmoMath.Ray(new Vec3(position.x, position.y, position.z), new Vec3(look.x, look.y, look.z));
    }

    private static Vec3d look(MinecraftClient client) {
        Camera camera = client.gameRenderer.getCamera();
        return Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
    }

    // ---------------------------------------------------------------- textos

    private static void tick(MinecraftClient client) {
        if (session == null || !session.open() || client.player == null) {
            return;
        }
        if (++ticks % HUD_EVERY_TICKS == 0 && client.currentScreen == null) {
            hud(client);
        }
    }

    /** La línea de la action bar: posición, giro, tamaño, opciones y qué hacer. */
    private static void hud(MinecraftClient client) {
        if (session == null || client.inGameHud == null) {
            return;
        }
        GhostSession current = session;
        int[] size = current.size();
        MutableText line = Text.literal(Feedback.PREFIX).formatted(Formatting.GOLD)
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(position(current)).formatted(Formatting.WHITE))
                .append(Text.literal(" · " + rotation(current.transform().orientation()) + " · " + size[0] + "×"
                        + size[1] + "×" + size[2]).formatted(Formatting.GRAY));
        GhostOptions options = current.options();
        if (options.keepExisting()) {
            line.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable("gui.komaedit.ghost.keep").formatted(Formatting.AQUA));
        }
        if (current.info().hasAir()) {
            line.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable(options.pasteAir() ? "gui.komaedit.ghost.air"
                            : "gui.komaedit.ghost.no_air").formatted(Formatting.AQUA));
        }
        if (options.ground()) {
            line.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable("gui.komaedit.ghost.ground").formatted(Formatting.AQUA));
        }
        boolean fresh = !notice.isEmpty() && System.currentTimeMillis() - noticeAt < 4_000L;
        line.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY));
        if (fresh) {
            line.append(Text.literal(notice).formatted(Formatting.YELLOW));
        } else if (!current.active()) {
            line.append(Text.translatable("gui.komaedit.ghost.placing").formatted(Formatting.GRAY));
        } else {
            line.append(Text.translatable("gui.komaedit.ghost.hint",
                    WorldFeedbackClient.confirmKey().getBoundKeyLocalizedText()).formatted(Formatting.GRAY));
        }
        client.inGameHud.setOverlayMessage(line, false);
    }

    private static String position(GhostSession current) {
        GhostTransform transform = current.transform();
        int[] offset = current.offset();
        String moved = offset[0] == 0 && offset[1] == 0 && offset[2] == 0 ? ""
                : " (" + signed(offset[0]) + ", " + signed(offset[1]) + ", " + signed(offset[2]) + ")";
        return transform.x() + ", " + transform.y() + ", " + transform.z() + moved;
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    /** «90°», «180° volteado», «tumbado»… */
    static String rotation(Orientation orientation) {
        StringBuilder text = new StringBuilder(Text.translatable("gui.komaedit.ghost.turn",
                orientation.yawQuarters() * 90).getString());
        if (orientation.mirrored()) {
            text.append(' ').append(Text.translatable("gui.komaedit.ghost.flipped").getString());
        }
        if (orientation.upsideDown()) {
            text.append(' ').append(Text.translatable("gui.komaedit.ghost.upside_down").getString());
        } else if (!orientation.upright()) {
            text.append(' ').append(Text.translatable("gui.komaedit.ghost.tipped").getString());
        }
        return text.toString();
    }

    private static void notice(String text) {
        notice = text == null ? "" : text;
        noticeAt = System.currentTimeMillis();
        MinecraftClient client = MinecraftClient.getInstance();
        if (session != null && session.open()) {
            hud(client);
        } else if (client.inGameHud != null && !notice.isEmpty()) {
            client.inGameHud.setOverlayMessage(Text.literal(Feedback.PREFIX).formatted(Formatting.GOLD)
                    .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(notice).formatted(Formatting.GRAY)), false);
        }
    }

    /** Al abrirse: una línea en el chat con los botones y las teclas (a la vista, como pide el gizmo). */
    private static void announce(MinecraftClient client) {
        if (client.inGameHud == null || session == null) {
            return;
        }
        MutableText line = Text.literal(Feedback.PREFIX).formatted(Formatting.GOLD)
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(Text.translatable("gui.komaedit.ghost.opened", session.info().description())
                        .formatted(Formatting.GRAY))
                .append(" ")
                .append(button("gui.komaedit.ghost.chat.place", "/kegizmo colocar", Formatting.GREEN))
                .append(" ")
                .append(button("gui.komaedit.ghost.chat.copy", "/kegizmo copia", Formatting.DARK_GREEN))
                .append(" ")
                .append(button("gui.komaedit.ghost.chat.cancel", "/kegizmo cancelar", Formatting.RED))
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(button("gui.komaedit.ghost.chat.keep", "/kegizmo conservar", Formatting.AQUA))
                .append(" ");
        if (session.info().hasAir()) {
            line.append(button("gui.komaedit.ghost.chat.air", "/kegizmo aire", Formatting.AQUA)).append(" ");
        }
        line.append(button("gui.komaedit.ghost.chat.ground", "/kegizmo suelo", Formatting.AQUA));
        client.inGameHud.getChatHud().addMessage(line);
        hud(client);
    }

    private static MutableText button(String key, String command, Formatting color) {
        return Text.translatable(key).styled(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(key + ".tooltip"))));
    }

    // ---------------------------------------------------------------- teclas

    private static GizmoKeys keys() {
        GizmoKeys keys = new GizmoKeys();
        for (Map.Entry<GizmoKeys.Action, KeyBinding> entry : BINDINGS.entrySet()) {
            keys.bind(entry.getKey(), boundKey(entry.getValue()));
        }
        return keys;
    }

    private static int boundKey(KeyBinding binding) {
        if (binding == null || binding.isUnbound()) {
            return GizmoKeys.UNBOUND;
        }
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        return key.getCategory() == InputUtil.Type.KEYSYM ? key.getCode() : GizmoKeys.UNBOUND;
    }
}
