package com.komainu.moxia.client.display;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.client.gizmo.GhostClient;
import com.komainu.moxia.client.mixin.GameRendererAccessor;
import com.komainu.moxia.client.world.GizmoRenderer;
import com.komainu.moxia.display.DisplayDrag;
import com.komainu.moxia.display.DisplayEditorSettings;
import com.komainu.moxia.display.DisplayEntities;
import com.komainu.moxia.display.DisplayGizmo;
import com.komainu.moxia.display.DisplayKeys;
import com.komainu.moxia.display.DisplayPayloads;
import com.komainu.moxia.display.DisplayPick;
import com.komainu.moxia.display.DisplayPose;
import com.komainu.moxia.display.DisplaySelection;
import com.komainu.moxia.display.DisplaySteps;
import com.komainu.moxia.display.DisplayVolume;
import com.komainu.moxia.display.Quat;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.gizmo.GizmoKeys;
import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.shape.Vec3;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * El editor de displays en el cliente: elige con un rayo contra el volumen de cada display (no tienen caja de
 * colisión), dibuja su contorno y el gizmo, y mientras se arrastra mueve los displays aquí mismo para que se vea sin
 * esperar al servidor. Al soltar manda cómo quedan ({@link DisplayPayloads.Edit}); el servidor lo comprueba y contesta
 * con lo que hay de verdad ({@link DisplayPayloads.Selection}), que deshace lo previsto si no lo aceptó.
 *
 * <p>Como el fantasma, en el mundo el puntero es el punto de mira (clic izquierdo y girar la cámara) y en el Studio,
 * el cursor. Clic en un display: elegirlo (Mayús: sumarlo o quitarlo; Alt: solo él, sin su grupo). Clic en el vacío:
 * soltar lo elegido. Alt mientras se arrastra: sin pasos.</p>
 */
public final class DisplayEditorClient {
    private static final double REACH = 64.0;
    private static final int HUD_EVERY_TICKS = 10;
    private static final long NUDGE_SEND_MILLIS = 250L;
    private static final int SELECTED = 0xE05FD7FF;
    private static final int PRIMARY = 0xFFB8F0FF;
    private static final int HOVER = 0xFFFFD23F;
    private static final int ARC = 0xFFFFD23F;
    private static final Map<DisplayKeys.Action, KeyBinding> BINDINGS = new EnumMap<>(DisplayKeys.Action.class);

    private static boolean editing;
    private static List<DisplayPayloads.Entry> selection = List.of();
    private static int primary = -1;
    private static int grouped;
    private static int hoveredDisplay = -1;
    private static DisplayGizmo.Handle hoveredHandle = DisplayGizmo.Handle.NONE;
    private static DisplayDrag drag;
    private static boolean worldDrag;
    private static boolean studioDrag;
    private static GizmoMath.Ray studioRay;
    private static boolean nudgePending;
    private static long nudgeAt;
    private static Clip clipboard;
    private static String notice = "";
    private static long noticeAt;
    private static int ticks;

    private DisplayEditorClient() {
    }

    /** Lo copiado con Ctrl+C: giro y escala. */
    private record Clip(Quat left, Quat right, Vec3 scale) {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DisplayPayloads.Selection.ID, (payload, context) ->
                context.client().execute(() -> selection(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(DisplayEditorClient::reset));
        WorldRenderEvents.LAST.register(DisplayEditorClient::render);
        ClientTickEvents.END_CLIENT_TICK.register(DisplayEditorClient::tick);
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> onAttack(client, clickCount));
        for (DisplayKeys.Action action : DisplayKeys.Action.values()) {
            BINDINGS.put(action, KeyBindingHelper.registerKeyBinding(new KeyBinding("key.komaedit.display_"
                    + action.id(), InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "category.komaedit")));
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("kedisplay")
                        .then(literal("herramienta").then(argument("tool",
                                com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
                                    String tool = com.mojang.brigadier.arguments.StringArgumentType.getString(context,
                                            "tool");
                                    return option(() -> settings(ClientDisplaySettings.get().withTool(
                                            DisplayGizmo.Tool.parse(tool))));
                                })))
                        .then(literal("local").executes(context -> option(() -> settings(
                                ClientDisplaySettings.get().withLocal(true)))))
                        .then(literal("global").executes(context -> option(() -> settings(
                                ClientDisplaySettings.get().withLocal(false)))))
                        .then(literal("paso").then(argument("step",
                                com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
                                    String step = com.mojang.brigadier.arguments.StringArgumentType.getString(context,
                                            "step");
                                    return option(() -> settings(ClientDisplaySettings.get().withSteps(
                                            ClientDisplaySettings.get().steps().withMove(DisplaySteps.Move.parse(
                                                    step)))));
                                })))
                        .then(literal("giro").then(argument("angle",
                                com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
                                    String angle = com.mojang.brigadier.arguments.StringArgumentType.getString(context,
                                            "angle");
                                    return option(() -> settings(ClientDisplaySettings.get().withSteps(
                                            ClientDisplaySettings.get().steps().withAngle(DisplaySteps.Angle.parse(
                                                    angle)))));
                                })))
                        .then(literal("ajustes").then(argument("settings",
                                com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(context -> {
                                    String text = com.mojang.brigadier.arguments.StringArgumentType.getString(context,
                                            "settings");
                                    return option(() -> settings(steps(ClientDisplaySettings.get(), text)));
                                })))
                        .then(literal("copiar").executes(context -> key(DisplayKeys.Action.COPY, false)))
                        .then(literal("pegar").executes(context -> key(DisplayKeys.Action.PASTE, false)))
                        .then(literal("duplicar").executes(context -> key(DisplayKeys.Action.DUPLICATE, false)))
                        .then(literal("borrar").executes(context -> key(DisplayKeys.Action.DELETE, false)))
                        .then(literal("agrupar").executes(context -> key(DisplayKeys.Action.GROUP, false)))
                        .then(literal("desagrupar").executes(context -> key(DisplayKeys.Action.GROUP, true)))
                        .then(literal("soltar").executes(context -> key(DisplayKeys.Action.CLEAR, false)))));
    }

    /** {@code /kedisplay ajustes <paso> <giro> <local|global>}, lo que manda la tarjeta del Studio. */
    static DisplayEditorSettings steps(DisplayEditorSettings settings, String text) {
        String[] words = text.strip().split("\\s+");
        DisplayEditorSettings result = settings;
        if (words.length > 0 && !words[0].isEmpty()) {
            result = result.withSteps(result.steps().withMove(DisplaySteps.Move.parse(words[0])));
        }
        if (words.length > 1) {
            result = result.withSteps(result.steps().withAngle(DisplaySteps.Angle.parse(words[1])));
        }
        if (words.length > 2) {
            result = result.withLocal(words[2].equalsIgnoreCase("local"));
        }
        notice(Text.translatable("gui.komaedit.display.steps_set", stepText(result.steps().move()),
                angleText(result.steps().angle()), Text.translatable(result.local() ? "gui.komaedit.display.local"
                        : "gui.komaedit.display.global")).getString());
        return result;
    }

    private static int option(Runnable change) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> {
            try {
                change.run();
            } catch (IllegalArgumentException exception) {
                notice(exception.getMessage());
            }
        });
        return 1;
    }

    private static int key(DisplayKeys.Action action, boolean shift) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> perform(new DisplayKeys.Press(action, shift, true, false), client));
        return 1;
    }

    /** Si el editor está abierto (y no hay un fantasma, que manda). */
    public static boolean active() {
        return editing && !GhostClient.open();
    }

    public static boolean hasSelection() {
        return !selection.isEmpty();
    }

    // ---------------------------------------------------------------- red

    private static void selection(DisplayPayloads.Selection payload) {
        boolean wasEditing = editing;
        editing = payload.editing();
        selection = payload.entries();
        primary = payload.primary();
        grouped = payload.grouped();
        if (drag == null && !nudgePending) {
            // Lo de verdad: deshace lo previsto si el servidor no lo aceptó.
            for (DisplayPayloads.Entry entry : selection) {
                place(entry.entityId(), entry.pose());
            }
        }
        if (!payload.message().isEmpty()) {
            notice(payload.message());
        }
        if (editing && !wasEditing) {
            announce(MinecraftClient.getInstance());
        }
        if (!editing) {
            drag = null;
            worldDrag = false;
            studioDrag = false;
        }
        hud(MinecraftClient.getInstance());
    }

    private static void reset() {
        editing = false;
        selection = List.of();
        primary = -1;
        drag = null;
        worldDrag = false;
        studioDrag = false;
        nudgePending = false;
        hoveredDisplay = -1;
        hoveredHandle = DisplayGizmo.Handle.NONE;
    }

    private static void sendEdit(DisplayPayloads.Operation operation, List<DisplayDrag.Item> items) {
        if (items.isEmpty() || !ClientPlayNetworking.canSend(DisplayPayloads.Edit.ID)) {
            return;
        }
        List<DisplayPayloads.Entry> entries = new ArrayList<>(items.size());
        for (DisplayDrag.Item item : items) {
            entries.add(new DisplayPayloads.Entry(item.id(), item.pose()));
        }
        ClientPlayNetworking.send(new DisplayPayloads.Edit(operation, entries));
    }

    private static void sendAct(DisplayPayloads.Action action) {
        if (ClientPlayNetworking.canSend(DisplayPayloads.Act.ID)) {
            ClientPlayNetworking.send(new DisplayPayloads.Act(action));
        }
    }

    private static void sendPick(int entityId, DisplaySelection.Mode mode, boolean single) {
        if (ClientPlayNetworking.canSend(DisplayPayloads.Pick.ID)) {
            ClientPlayNetworking.send(new DisplayPayloads.Pick(entityId, mode, single));
        }
    }

    // ---------------------------------------------------------------- los displays del cliente

    private static DisplayEntity entity(int id) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return null;
        }
        return client.world.getEntityById(id) instanceof DisplayEntity display ? display : null;
    }

    /** Pone la pose al display del cliente (lo previsto, o lo de verdad al llegar del servidor). */
    private static void place(int id, DisplayPose pose) {
        DisplayEntity entity = entity(id);
        if (entity == null) {
            return;
        }
        try {
            DisplayEntities.apply(entity, pose);
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.debug("Could not preview a KomaEdit display edit", exception);
        }
    }

    private static void place(List<DisplayDrag.Item> items) {
        for (DisplayDrag.Item item : items) {
            place(item.id(), item.pose());
        }
    }

    /**
     * Lo que ocupa el modelo de un display: el bloque; el objeto con la transformación de su modelo para su modo de
     * vista; el texto medido con la fuente del juego, como lo parte en líneas el display.
     */
    static DisplayVolume volume(DisplayEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            if (entity instanceof DisplayEntity.ItemDisplayEntity item) {
                BakedModel model = client.getItemRenderer().getModel(item.getItemStack(), client.world, null, 0);
                Transformation transformation = model.getTransformation().getTransformation(
                        item.getTransformationMode());
                return DisplayVolume.item(DisplayEntities.vector(transformation.rotation),
                        DisplayEntities.vector(transformation.translation), DisplayEntities.vector(
                                transformation.scale), !model.hasDepth());
            }
            if (entity instanceof DisplayEntity.TextDisplayEntity text) {
                TextRenderer font = client.textRenderer;
                List<OrderedText> lines = font.wrapLines(text.getText(), text.getLineWidth());
                int width = 0;
                for (OrderedText line : lines) {
                    width = Math.max(width, font.getWidth(line));
                }
                return DisplayVolume.text(width, Math.max(1, lines.size()));
            }
        } catch (RuntimeException exception) {
            return DisplayEntities.serverVolume(entity);
        }
        return DisplayVolume.block();
    }

    /** Lo elegido como lo ve ahora el cliente (con lo previsto), y cuál es el activo. */
    private static List<DisplayDrag.Item> items() {
        List<DisplayDrag.Item> items = new ArrayList<>(selection.size());
        for (DisplayPayloads.Entry entry : selection) {
            DisplayEntity entity = entity(entry.entityId());
            if (entity != null) {
                items.add(new DisplayDrag.Item(entry.entityId(), DisplayEntities.pose(entity).folded(),
                        volume(entity)));
            }
        }
        return items;
    }

    private static int primaryIndex(List<DisplayDrag.Item> items) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).id() == primary) {
                return index;
            }
        }
        return items.size() - 1;
    }

    // ---------------------------------------------------------------- rayos

    private static Camera camera(MinecraftClient client) {
        return client.gameRenderer.getCamera();
    }

    private static Vec3 cameraPosition(MinecraftClient client) {
        Vec3d position = camera(client).getPos();
        return new Vec3(position.x, position.y, position.z);
    }

    private static GizmoMath.Ray crosshair(MinecraftClient client) {
        Camera camera = camera(client);
        Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        return new GizmoMath.Ray(cameraPosition(client), new Vec3(look.x, look.y, look.z));
    }

    /** El rayo con el que se apunta ahora: el cursor en el Studio, el punto de mira en el mundo. */
    private static GizmoMath.Ray ray(MinecraftClient client) {
        return client.currentScreen != null && studioRay != null ? studioRay : crosshair(client);
    }

    private static DisplayGizmo gizmo(MinecraftClient client, float delta) {
        List<DisplayDrag.Item> items = drag != null ? drag.current() : items();
        if (items.isEmpty()) {
            return null;
        }
        DisplayDrag.Item active = items.get(primaryIndex(items));
        DisplayEditorSettings settings = ClientDisplaySettings.get();
        double fov = ((GameRendererAccessor) client.gameRenderer).komaedit$getFov(camera(client), delta, true);
        return DisplayGizmo.around(active.center(), cameraPosition(client), fov,
                settings.frame(active.pose().frame()), settings.tool());
    }

    /** El display bajo el rayo, si no lo tapa un bloque. */
    private static int pick(MinecraftClient client, GizmoMath.Ray ray) {
        if (client.world == null) {
            return -1;
        }
        Vec3 origin = ray.origin();
        Box around = new Box(origin.x() - REACH, origin.y() - REACH, origin.z() - REACH, origin.x() + REACH,
                origin.y() + REACH, origin.z() + REACH);
        Camera camera = camera(client);
        List<DisplayPick.Candidate> candidates = new ArrayList<>();
        for (DisplayEntity entity : client.world.getEntitiesByClass(DisplayEntity.class, around, entity -> true)) {
            DisplayPose pose = DisplayEntities.pose(entity);
            candidates.add(new DisplayPick.Candidate(entity.getId(), volume(entity).world(pose, camera.getYaw(),
                    camera.getPitch())));
        }
        double limit = Math.min(REACH, blockDistance(client, ray) + 0.05);
        Optional<DisplayPick.Hit> hit = DisplayPick.pick(ray, candidates, limit);
        return hit.map(DisplayPick.Hit::id).orElse(-1);
    }

    private static double blockDistance(MinecraftClient client, GizmoMath.Ray ray) {
        Entity viewer = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        if (viewer == null || client.world == null) {
            return REACH;
        }
        Vec3d start = new Vec3d(ray.origin().x(), ray.origin().y(), ray.origin().z());
        Vec3 far = ray.at(REACH);
        HitResult hit = client.world.raycast(new RaycastContext(start, new Vec3d(far.x(), far.y(), far.z()),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, viewer));
        return hit.getType() == HitResult.Type.MISS ? REACH : hit.getPos().distanceTo(start);
    }

    // ---------------------------------------------------------------- arrastrar

    private static boolean beginDrag(MinecraftClient client, GizmoMath.Ray ray) {
        if (hoveredHandle == DisplayGizmo.Handle.NONE || nudgePending) {
            return false;
        }
        DisplayGizmo gizmo = gizmo(client, 1.0F);
        List<DisplayDrag.Item> items = items();
        if (gizmo == null || items.isEmpty()) {
            return false;
        }
        DisplayEditorSettings settings = ClientDisplaySettings.get();
        drag = DisplayDrag.begin(gizmo, hoveredHandle, ray, items, primaryIndex(items), settings.steps(),
                settings.globalGrid());
        return drag != null;
    }

    private static void updateDrag(GizmoMath.Ray ray, boolean free) {
        if (drag != null && drag.update(ray, free)) {
            place(drag.current());
            hud(MinecraftClient.getInstance());
        }
    }

    /** Suelta el gizmo: manda cómo quedan. Un clic en un anillo sin arrastrar gira un paso. */
    private static void endDrag() {
        DisplayDrag finished = drag;
        drag = null;
        worldDrag = false;
        studioDrag = false;
        if (finished == null) {
            return;
        }
        DisplayPayloads.Operation operation = switch (finished.tool()) {
            case MOVE -> DisplayPayloads.Operation.MOVE;
            case ROTATE -> DisplayPayloads.Operation.ROTATE;
            case SCALE -> DisplayPayloads.Operation.SCALE;
        };
        if (finished.moved()) {
            sendEdit(operation, finished.current());
            return;
        }
        if (finished.tool() == DisplayGizmo.Tool.ROTATE && finished.handle().axis() >= 0) {
            double degrees = ClientDisplaySettings.get().steps().angle().degrees();
            List<DisplayDrag.Item> turned = finished.rotatedBy(Quat.axisAngle(
                    finished.gizmo().axis(finished.handle().axis()), Math.toRadians(degrees > 0 ? degrees : 90.0)));
            place(turned);
            sendEdit(operation, turned);
        }
    }

    // ---------------------------------------------------------------- ratón en el mundo

    private static boolean onAttack(MinecraftClient client, int clickCount) {
        if (client.currentScreen != null || !active()) {
            return false;
        }
        if (worldDrag) {
            return true;
        }
        if (clickCount == 0) {
            // Mantener el clic sobre un display o el gizmo no rompe el bloque de detrás.
            return hoveredHandle != DisplayGizmo.Handle.NONE || hoveredDisplay >= 0;
        }
        GizmoMath.Ray ray = crosshair(client);
        if (hoveredHandle != DisplayGizmo.Handle.NONE && beginDrag(client, ray)) {
            worldDrag = true;
            return true;
        }
        return click(hoveredDisplay);
    }

    /** Clic en un display (o en el vacío). */
    private static boolean click(int display) {
        if (display >= 0) {
            DisplaySelection.Mode mode = Screen.hasShiftDown() ? DisplaySelection.Mode.TOGGLE
                    : DisplaySelection.Mode.REPLACE;
            sendPick(display, mode, Screen.hasAltDown());
            return true;
        }
        if (!selection.isEmpty()) {
            sendPick(-1, DisplaySelection.Mode.REPLACE, false);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- Studio

    /** El cursor del Studio sobre el mundo. */
    public static void studioHover(GizmoMath.Ray ray) {
        studioRay = ray;
    }

    /** Clic izquierdo del Studio en el mundo: {@code true} si era del editor (gizmo o display). */
    public static boolean studioPress(GizmoMath.Ray ray) {
        if (!active()) {
            return false;
        }
        studioRay = ray;
        MinecraftClient client = MinecraftClient.getInstance();
        hover(client, ray, 1.0F);
        if (hoveredHandle != DisplayGizmo.Handle.NONE && beginDrag(client, ray)) {
            studioDrag = true;
            return true;
        }
        if (hoveredDisplay >= 0) {
            return click(hoveredDisplay);
        }
        return false;
    }

    public static boolean studioDragging() {
        return studioDrag;
    }

    public static void studioDrag(GizmoMath.Ray ray) {
        studioRay = ray;
        if (studioDrag) {
            updateDrag(ray, Screen.hasAltDown());
        }
    }

    public static void studioRelease() {
        if (studioDrag) {
            endDrag();
        }
    }

    /** Una tecla del Studio (sin escribir en un campo). */
    public static boolean studioKey(int key, int modifiers) {
        return key(key, GLFW.GLFW_PRESS, modifiers, MinecraftClient.getInstance());
    }

    /** Al cerrar el Studio. */
    public static void studioClosed() {
        studioRay = null;
        if (studioDrag) {
            endDrag();
        }
    }

    // ---------------------------------------------------------------- teclado

    /** Una tecla en el mundo (sin pantalla), antes que Minecraft: {@code true} si era del editor. */
    public static boolean onKey(int key, int action, int modifiers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.player == null || action == GLFW.GLFW_RELEASE) {
            return false;
        }
        return key(key, action, modifiers, client);
    }

    private static boolean key(int key, int action, int modifiers, MinecraftClient client) {
        if (!active()) {
            return false;
        }
        Optional<DisplayKeys.Press> press = keys().match(key, modifiers);
        if (press.isEmpty()) {
            return false;
        }
        DisplayKeys.Action pressed = press.get().action();
        if (selection.isEmpty() && !pressed.idle()) {
            // Sin nada elegido, Esc, Supr y Ctrl+V siguen siendo de Minecraft (y del fantasma).
            return false;
        }
        if (action == GLFW.GLFW_REPEAT && !pressed.repeats()) {
            return true;
        }
        perform(press.get(), client);
        return true;
    }

    private static DisplayKeys keys() {
        DisplayKeys keys = new DisplayKeys();
        for (Map.Entry<DisplayKeys.Action, KeyBinding> entry : BINDINGS.entrySet()) {
            KeyBinding binding = entry.getValue();
            int code = binding == null || binding.isUnbound() ? GizmoKeys.UNBOUND
                    : KeyBindingHelper.getBoundKeyOf(binding).getCategory() == InputUtil.Type.KEYSYM
                    ? KeyBindingHelper.getBoundKeyOf(binding).getCode() : GizmoKeys.UNBOUND;
            keys.bind(entry.getKey(), code);
        }
        return keys;
    }

    static void perform(DisplayKeys.Press press, MinecraftClient client) {
        DisplayEditorSettings settings = ClientDisplaySettings.get();
        switch (press.action()) {
            case MOVE -> settings(settings.withTool(DisplayGizmo.Tool.MOVE));
            case ROTATE -> settings(settings.withTool(DisplayGizmo.Tool.ROTATE));
            case SCALE -> settings(settings.withTool(DisplayGizmo.Tool.SCALE));
            case FRAME -> settings(settings.withLocal(!settings.local()));
            case STEP -> settings(settings.withSteps(settings.steps().withMove(settings.steps().move().next())));
            case ANGLE -> settings(settings.withSteps(settings.steps().withAngle(settings.steps().angle().next())));
            case COPY -> copy();
            case PASTE -> paste();
            case DUPLICATE -> sendAct(DisplayPayloads.Action.DUPLICATE);
            case GROUP -> sendAct(press.shift() ? DisplayPayloads.Action.UNGROUP : DisplayPayloads.Action.GROUP);
            case DELETE -> sendAct(DisplayPayloads.Action.DELETE);
            case CLEAR -> {
                if (drag != null) {
                    // Esc con el gizmo agarrado: todo vuelve a donde estaba.
                    place(drag.start());
                    drag = null;
                    worldDrag = false;
                    studioDrag = false;
                } else {
                    sendPick(-1, DisplaySelection.Mode.REPLACE, false);
                }
            }
            case FORWARD, BACK, LEFT, RIGHT, UP, DOWN -> nudge(press, client);
        }
        hud(client);
    }

    private static void settings(DisplayEditorSettings settings) {
        ClientDisplaySettings.set(settings);
        hud(MinecraftClient.getInstance());
    }

    private static void copy() {
        List<DisplayDrag.Item> items = items();
        if (items.isEmpty()) {
            return;
        }
        DisplayPose pose = items.get(primaryIndex(items)).pose().folded();
        clipboard = new Clip(pose.left(), pose.right(), pose.scale());
        notice(Text.translatable("gui.komaedit.display.copied").getString());
    }

    private static void paste() {
        if (clipboard == null) {
            notice(Text.translatable("gui.komaedit.display.nothing_copied").getString());
            return;
        }
        List<DisplayDrag.Item> pasted = new ArrayList<>();
        for (DisplayDrag.Item item : items()) {
            pasted.add(item.with(item.pose().withRotationScale(clipboard.left(), clipboard.right(), clipboard.scale(),
                    item.localCenter())));
        }
        place(pasted);
        sendEdit(DisplayPayloads.Operation.PASTE, pasted);
    }

    /**
     * Flechas y RePág/AvPág: un paso de mover en la dirección de la mirada (con Mayús, un bloque). Se ve al momento y
     * se manda al servidor cuando se dejan de pulsar, en un solo //undo.
     */
    private static void nudge(DisplayKeys.Press press, MinecraftClient client) {
        if (drag != null) {
            return;
        }
        double size = ClientDisplaySettings.get().steps().move().size();
        double step = press.shift() ? 1.0 : size > 0.0 ? size : 1.0 / 16.0;
        Camera camera = camera(client);
        Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        int[] forward = GizmoMath.forward(look.x, look.z);
        int[] left = GizmoMath.left(forward);
        Vec3 delta = switch (press.action()) {
            case FORWARD -> new Vec3(forward[0], 0, forward[1]);
            case BACK -> new Vec3(-forward[0], 0, -forward[1]);
            case LEFT -> new Vec3(left[0], 0, left[1]);
            case RIGHT -> new Vec3(-left[0], 0, -left[1]);
            case UP -> new Vec3(0, 1, 0);
            default -> new Vec3(0, -1, 0);
        };
        List<DisplayDrag.Item> moved = new ArrayList<>();
        for (DisplayDrag.Item item : items()) {
            moved.add(item.with(item.pose().moved(delta.multiply(step))));
        }
        place(moved);
        nudgePending = true;
        nudgeAt = System.currentTimeMillis();
    }

    private static void tick(MinecraftClient client) {
        if (nudgePending && System.currentTimeMillis() - nudgeAt >= NUDGE_SEND_MILLIS) {
            nudgePending = false;
            sendEdit(DisplayPayloads.Operation.NUDGE, items());
        }
        if (!active() || client.player == null) {
            return;
        }
        if (++ticks % HUD_EVERY_TICKS == 0 && client.currentScreen == null) {
            hud(client);
        }
    }

    // ---------------------------------------------------------------- dibujo

    private static void hover(MinecraftClient client, GizmoMath.Ray ray, float delta) {
        if (drag != null) {
            hoveredHandle = drag.handle();
            hoveredDisplay = -1;
            return;
        }
        DisplayGizmo gizmo = gizmo(client, delta);
        hoveredHandle = gizmo == null ? DisplayGizmo.Handle.NONE : gizmo.pick(ray);
        hoveredDisplay = hoveredHandle == DisplayGizmo.Handle.NONE ? pick(client, ray) : -1;
    }

    private static void render(WorldRenderContext context) {
        if (!active()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        float delta = context.tickCounter().getTickDelta(true);
        try {
            GizmoMath.Ray ray = ray(client);
            if (worldDrag) {
                if (!client.options.attackKey.isPressed()) {
                    endDrag();
                } else {
                    updateDrag(ray, Screen.hasAltDown());
                }
            }
            hover(client, ray, delta);
            List<GizmoGeometry.Line> lines = new ArrayList<>();
            List<DisplayDrag.Item> items = drag != null ? drag.current() : items();
            int active = primaryIndex(items);
            Camera camera = camera(client);
            for (int index = 0; index < items.size(); index++) {
                DisplayDrag.Item item = items.get(index);
                DisplayEntity entity = entity(item.id());
                if (entity != null) {
                    outline(lines, volume(entity).world(item.pose(), camera.getYaw(), camera.getPitch()),
                            index == active ? PRIMARY : SELECTED, index == active ? 2.5F : 2.0F);
                }
            }
            if (hoveredDisplay >= 0 && selection.stream().noneMatch(entry -> entry.entityId() == hoveredDisplay)) {
                DisplayEntity entity = entity(hoveredDisplay);
                if (entity != null) {
                    outline(lines, volume(entity).world(DisplayEntities.pose(entity), camera.getYaw(),
                            camera.getPitch()), HOVER, 2.0F);
                }
            }
            DisplayGizmo gizmo = gizmo(client, delta);
            List<GizmoGeometry.Triangle> triangles = new ArrayList<>();
            if (gizmo != null) {
                GizmoGeometry.Shape shape = gizmo.shape(hoveredHandle, drag == null ? DisplayGizmo.Handle.NONE
                        : drag.handle());
                lines.addAll(shape.lines());
                triangles.addAll(shape.triangles());
                if (drag != null && drag.tool() == DisplayGizmo.Tool.ROTATE && drag.handle().axis() >= 0) {
                    lines.addAll(drag.gizmo().arc(drag.handle().axis(), drag.angleStart(),
                            drag.angleStart() + drag.turned(), ARC));
                }
            }
            if (!lines.isEmpty() || !triangles.isEmpty()) {
                GizmoRenderer.render(context, new GizmoGeometry.Shape(lines, triangles));
            }
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.error("KomaEdit display editor failed to draw; closing it", exception);
            reset();
        }
    }

    private static void outline(List<GizmoGeometry.Line> lines, com.komainu.moxia.display.Affine unit, int argb,
                                float width) {
        lines.addAll(DisplayGizmo.outline(unit, argb, width));
    }

    // ---------------------------------------------------------------- textos

    /** La línea de la barra de acción: cuántos, herramienta, marco, pasos y qué hacer. */
    private static void hud(MinecraftClient client) {
        if (!active() || client.inGameHud == null) {
            return;
        }
        DisplayEditorSettings settings = ClientDisplaySettings.get();
        MutableText line = Text.literal(Feedback.PREFIX).formatted(Formatting.GOLD)
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(Text.translatable("gui.komaedit.display.hud.title").formatted(Formatting.WHITE))
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY));
        if (selection.isEmpty()) {
            line.append(Text.translatable("gui.komaedit.display.hud.empty").formatted(Formatting.GRAY));
        } else {
            line.append(Text.translatable("gui.komaedit.display.hud.selected", selection.size())
                    .formatted(Formatting.AQUA));
            if (grouped > 0) {
                line.append(Text.literal(" ").append(Text.translatable("gui.komaedit.display.hud.grouped"))
                        .formatted(Formatting.AQUA));
            }
            line.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable("gui.komaedit.display.tool." + settings.tool().id())
                            .formatted(Formatting.YELLOW))
                    .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    // Al escalar el gizmo va siempre en los ejes del objeto.
                    .append(Text.translatable(settings.local() || settings.tool() == DisplayGizmo.Tool.SCALE
                            ? "gui.komaedit.display.local" : "gui.komaedit.display.global").formatted(Formatting.GRAY))
                    .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable("gui.komaedit.display.hud.steps", stepText(settings.steps().move()),
                            angleText(settings.steps().angle())).formatted(Formatting.GRAY));
            if (drag != null) {
                line.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY)).append(Text.literal(
                        dragged(drag)).formatted(Formatting.WHITE));
            }
        }
        boolean fresh = !notice.isEmpty() && System.currentTimeMillis() - noticeAt < 4_000L;
        line.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY));
        line.append(fresh ? Text.literal(notice).formatted(Formatting.YELLOW)
                : Text.translatable(selection.isEmpty() ? "gui.komaedit.display.hud.hint_empty"
                : "gui.komaedit.display.hud.hint").formatted(Formatting.GRAY));
        client.inGameHud.setOverlayMessage(line, false);
    }

    private static String stepText(DisplaySteps.Move move) {
        return move == DisplaySteps.Move.FREE ? Text.translatable("gui.komaedit.display.free").getString()
                : move.id();
    }

    private static String angleText(DisplaySteps.Angle angle) {
        return angle == DisplaySteps.Angle.FREE ? Text.translatable("gui.komaedit.display.free").getString()
                : angle.id() + "°";
    }

    /** Lo que lleva el arrastre: el centro del activo, los grados o la escala. */
    private static String dragged(DisplayDrag current) {
        List<DisplayDrag.Item> items = current.current();
        DisplayDrag.Item active = items.get(primaryIndex(items));
        return switch (current.tool()) {
            case MOVE -> {
                Vec3 center = active.center();
                yield String.format(java.util.Locale.ROOT, "%.4g, %.4g, %.4g", center.x(), center.y(), center.z());
            }
            case ROTATE -> String.format(java.util.Locale.ROOT, "%+.1f°", Math.toDegrees(current.turned()));
            case SCALE -> {
                Vec3 scale = active.pose().scale();
                yield String.format(java.util.Locale.ROOT, "%.4g × %.4g × %.4g", scale.x(), scale.y(), scale.z());
            }
        };
    }

    private static void notice(String text) {
        notice = text == null ? "" : text;
        noticeAt = System.currentTimeMillis();
        hud(MinecraftClient.getInstance());
    }

    /** Al abrir el editor: una línea en el chat con las teclas y botones. */
    private static void announce(MinecraftClient client) {
        if (client.inGameHud == null) {
            return;
        }
        MutableText line = Text.literal(Feedback.PREFIX).formatted(Formatting.GOLD)
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(Text.translatable("gui.komaedit.display.opened").formatted(Formatting.GRAY))
                .append(" ")
                .append(button("gui.komaedit.display.chat.move", "/kedisplay herramienta mover", Formatting.YELLOW))
                .append(" ")
                .append(button("gui.komaedit.display.chat.rotate", "/kedisplay herramienta girar", Formatting.YELLOW))
                .append(" ")
                .append(button("gui.komaedit.display.chat.scale", "/kedisplay herramienta escalar", Formatting.YELLOW))
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(button("gui.komaedit.display.chat.local", "/kedisplay local", Formatting.AQUA))
                .append(" ")
                .append(button("gui.komaedit.display.chat.global", "/kedisplay global", Formatting.AQUA))
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(button("gui.komaedit.display.chat.close", "/komaedit display edit off", Formatting.RED));
        client.inGameHud.getChatHud().addMessage(line);
    }

    private static MutableText button(String key, String command, Formatting color) {
        return Text.translatable(key).styled(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(key + ".tooltip"))));
    }
}
