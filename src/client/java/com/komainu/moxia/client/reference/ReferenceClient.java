package com.komainu.moxia.client.reference;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.client.gizmo.GhostClient;
import com.komainu.moxia.client.mixin.GameRendererAccessor;
import com.komainu.moxia.client.world.GizmoRenderer;
import com.komainu.moxia.client.world.ReferenceWorldRenderer;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.gizmo.GizmoKeys;
import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.image.ImageDecoder;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.reference.ReferenceDrag;
import com.komainu.moxia.reference.ReferenceFiles;
import com.komainu.moxia.reference.ReferenceFormat;
import com.komainu.moxia.reference.ReferenceGizmo;
import com.komainu.moxia.reference.ReferenceImage;
import com.komainu.moxia.reference.ReferenceRotation;
import com.komainu.moxia.reference.ReferenceSet;
import com.komainu.moxia.reference.WorldFrame;
import com.komainu.moxia.shape.Vec3;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Las imágenes de referencia en el cliente: qué hay abierto en este mundo o servidor (se guarda al salir y un
 * segundo después de cada cambio), la tecla de mostrar u ocultar, la orden {@code /kegui referencias} y la edición en el mundo
 * con el gizmo. No manda nada al servidor: funciona igual en un servidor sin KomaEdit.
 *
 * <p>Teclas: en Controles vienen sin asignar, como las del fantasma, para no quitarle la tecla a nadie. Mientras no se
 * asignen valen Ctrl+I (mostrar u ocultar todas) y Ctrl+Mayús+I (abrir la lista).</p>
 *
 * <p>Editar en el mundo es como el fantasma: el punto de mira es el puntero. Se apunta a una flecha, un anillo, el cubo
 * o una esquina, se mantiene el clic izquierdo y se gira la cámara. Intro, Esc o el clic derecho terminan.</p>
 */
public final class ReferenceClient {
    /** Carpeta de las imágenes, dentro de la de configuración del juego. */
    public static final String FOLDER = "komaedit/references";
    private static final long SAVE_DELAY_MILLIS = 1_000L;
    private static final int HUD_EVERY_TICKS = 10;
    /** Lo que se aleja delante del jugador una imagen recién traída al mundo. */
    private static final double FRONT = 6.0;
    private static final int KEY_I = 'I';
    private static final Random RANDOM = new Random();

    private static ReferenceSet set = ReferenceSet.EMPTY;
    private static String worldKey;
    private static boolean dirty;
    private static long dirtyAt;
    private static String editing;
    private static ReferenceGizmo.Handle hovered = ReferenceGizmo.Handle.NONE;
    private static ReferenceDrag drag;
    private static int ticks;
    private static KeyBinding toggleKey;
    private static KeyBinding openKey;

    private ReferenceClient() {
    }

    public static void register() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.komaedit.reference_toggle",
                InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "category.komaedit"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.komaedit.reference_open",
                InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "category.komaedit"));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> join(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ReferenceClient::leave));
        WorldRenderEvents.LAST.register(ReferenceClient::renderWorld);
        ClientTickEvents.END_CLIENT_TICK.register(ReferenceClient::tick);
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> onAttack(client, clickCount));
        ReferenceWindows.register();
        // Bajo /kegui, como el resto de lo que atiende el propio cliente (también si lo manda el Studio).
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("kegui").then(references("referencias", "alternar", "listo"))
                        .then(references("references", "toggle", "done"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> references(String name, String toggle,
                                                                                 String done) {
        return literal(name).executes(context -> openScreen())
                .then(literal(toggle).executes(context -> run(ReferenceClient::toggleShown)))
                .then(literal(done).executes(context -> run(ReferenceClient::stopEditing)));
    }

    private static int run(Runnable action) {
        MinecraftClient.getInstance().send(action);
        return 1;
    }

    /** Abre la lista de referencias; desde el Studio, al cerrarla se vuelve a él. */
    public static int openScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> {
            if (client.player != null) {
                Screen parent = client.currentScreen instanceof ReferenceScreen ? null : client.currentScreen;
                client.setScreen(new ReferenceScreen(parent));
            }
        });
        return 1;
    }

    // ---------------------------------------------------------------- estado y guardado

    /** La carpeta de las imágenes ({@code config/komaedit/references/}). */
    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve(FOLDER);
    }

    public static ReferenceSet set() {
        return set;
    }

    /** Si hay un mundo donde guardar lo abierto. */
    public static boolean inWorld() {
        return worldKey != null;
    }

    public static void update(ReferenceSet next) {
        if (next == null || next.equals(set)) {
            return;
        }
        set = next;
        if (editing != null && set.byId(editing).isEmpty()) {
            stopEditing();
        }
        dirty = true;
        dirtyAt = System.currentTimeMillis();
    }

    public static void update(ReferenceImage reference) {
        update(set.replace(reference));
    }

    public static void toggleShown() {
        update(set.withShown(!set.shown()));
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) {
            client.inGameHud.setOverlayMessage(Text.literal(Feedback.PREFIX).formatted(Formatting.GOLD)
                    .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable(set.shown() ? "gui.komaedit.reference.shown"
                            : "gui.komaedit.reference.hidden", set.references().size()).formatted(Formatting.GRAY)),
                    false);
        }
    }

    private static void join(MinecraftClient client) {
        save();
        worldKey = key(client);
        set = load(worldKey);
        dirty = false;
    }

    private static void leave() {
        stopEditing();
        save();
        set = ReferenceSet.EMPTY;
        worldKey = null;
        dirty = false;
        ReferenceWindows.reset();
        ReferenceTextures.clear();
    }

    /** El mundo de un jugador por su carpeta; un servidor, por su dirección (un Realm, por su nombre). */
    private static String key(MinecraftClient client) {
        if (client.isInSingleplayer() && client.getServer() != null) {
            Path root = client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            return ReferenceSet.key(true, root.getFileName() == null ? "" : root.getFileName().toString());
        }
        ServerInfo server = client.getCurrentServerEntry();
        if (server == null) {
            return ReferenceSet.key(false, "");
        }
        return ReferenceSet.key(false, server.isRealm() ? "realms:" + server.name : server.address);
    }

    private static Path file(String key) {
        return folder().resolve(ReferenceFiles.WORLDS).resolve(key + ".json");
    }

    private static ReferenceSet load(String key) {
        Path file = file(key);
        if (!Files.isRegularFile(file)) {
            return ReferenceSet.EMPTY;
        }
        try {
            return ReferenceSet.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not read the reference images of {}", key, exception);
            return ReferenceSet.EMPTY;
        }
    }

    /** Guarda lo abierto en este mundo si cambió. */
    public static void save() {
        if (!dirty || worldKey == null) {
            return;
        }
        dirty = false;
        try {
            AtomicStorage.writeString(file(worldKey), set.toJson());
        } catch (IOException exception) {
            KomaEditMod.LOGGER.warn("Could not save the reference images of {}", worldKey, exception);
        }
    }

    // ---------------------------------------------------------------- abrir

    /**
     * Abre una imagen de la carpeta como referencia nueva, en pantalla y encima de las demás.
     *
     * @return su identificador, o vacío si no se pudo (lleno o archivo ilegible: el motivo va en {@code problem})
     */
    public static Optional<String> open(String file, java.util.function.Consumer<String> problem) {
        if (set.full()) {
            problem.accept(Text.translatable("gui.komaedit.reference.full", ReferenceSet.MAX).getString());
            return Optional.empty();
        }
        int[] size = size(file, problem);
        if (size == null) {
            return Optional.empty();
        }
        String id = set.newId(RANDOM::nextInt);
        ReferenceImage reference = ReferenceImage.open(id, file, size[0], size[1], null);
        reference = reference.withWorld(inFront(reference.world()));
        update(set.add(reference).withShown(true));
        return Optional.of(id);
    }

    /** Las medidas del archivo: las de su miniatura si ya llegó; si no, las de la cabecera. */
    private static int[] size(String file, java.util.function.Consumer<String> problem) {
        Optional<ReferenceTextures.Texture> thumbnail = ReferenceTextures.thumbnail(file);
        if (thumbnail.isPresent()) {
            return new int[]{thumbnail.get().width(), thumbnail.get().height()};
        }
        try {
            ImageDecoder.Header header = ImageDecoder.probe(ReferenceFiles.read(folder(), file),
                    ReferenceFiles.MAX_PIXELS);
            return new int[]{header.width(), header.height()};
        } catch (IllegalArgumentException exception) {
            problem.accept(exception.getMessage());
            return null;
        }
    }

    /** El plano delante del jugador, de pie y de cara a él, a la altura de sus ojos y en medios bloques. */
    public static WorldFrame inFront(WorldFrame frame) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return frame;
        }
        float yaw = client.player.getYaw();
        Vec3d eye = client.player.getEyePos();
        double radians = Math.toRadians(yaw);
        Vec3 center = new Vec3(half(eye.x - Math.sin(radians) * FRONT), half(eye.y), half(eye.z + Math.cos(radians)
                * FRONT));
        double facing = Math.round(yaw / 90.0) * 90.0;
        return frame.withCenter(center).withRotation(ReferenceRotation.facing(facing));
    }

    /** Tumbada a los pies del jugador, delante de él, para calcar una planta. */
    public static WorldFrame onFloor(WorldFrame frame) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return frame;
        }
        WorldFrame front = inFront(frame);
        double facing = Math.round(client.player.getYaw() / 90.0) * 90.0;
        Vec3 feet = new Vec3(front.center().x(), Math.floor(client.player.getY()) + 0.02, front.center().z());
        return front.withCenter(feet).withRotation(ReferenceRotation.flat(facing));
    }

    private static double half(double value) {
        return Math.round(value * 2.0) / 2.0;
    }

    // ---------------------------------------------------------------- editar en el mundo

    /** Enciende el gizmo sobre una referencia (la pasa al mundo si estaba en pantalla). */
    public static void edit(String id) {
        set.byId(id).ifPresent(reference -> {
            ReferenceImage world = reference.withMode(ReferenceImage.Mode.WORLD).withVisible(true);
            update(set.replace(world).withShown(true));
            editing = id;
            drag = null;
            hovered = ReferenceGizmo.Handle.NONE;
            ticks = 0;
            MinecraftClient client = MinecraftClient.getInstance();
            client.setScreen(null);
            hud(client);
        });
    }

    public static boolean editing() {
        return editing != null;
    }

    public static String editingId() {
        return editing;
    }

    public static void stopEditing() {
        if (editing == null) {
            return;
        }
        editing = null;
        drag = null;
        hovered = ReferenceGizmo.Handle.NONE;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) {
            client.inGameHud.setOverlayMessage(Text.literal(Feedback.PREFIX).formatted(Formatting.GOLD)
                    .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable("gui.komaedit.reference.done").formatted(Formatting.GRAY)), false);
        }
        save();
    }

    private static Optional<ReferenceImage> edited() {
        return editing == null ? Optional.empty() : set.byId(editing);
    }

    /** El gizmo solo atiende sin pantalla abierta y si no hay un fantasma, que manda. */
    private static boolean interactive(MinecraftClient client) {
        return editing != null && client.currentScreen == null && client.player != null && !GhostClient.active();
    }

    private static void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        Optional<ReferenceImage> current = edited();
        if (current.isPresent() && interactive(client)) {
            ReferenceImage reference = current.get();
            GizmoMath.Ray ray = crosshair(client);
            if (drag != null) {
                if (!client.options.attackKey.isPressed()) {
                    drag = null;
                    save();
                    hud(client);
                } else {
                    WorldFrame next = drag.drag(ray, Screen.hasControlDown());
                    if (next != null && !next.equals(reference.world())) {
                        reference = reference.withWorld(next);
                        update(reference);
                        hud(client);
                    }
                }
            }
            hovered = drag != null ? drag.handle() : layout(client, reference).pick(ray);
        }
        ReferenceWorldRenderer.render(context, set, editing);
        current = edited();
        if (current.isPresent() && !GhostClient.active()) {
            GizmoRenderer.render(context, ReferenceGizmo.build(layout(client, current.get()), hovered,
                    drag == null ? ReferenceGizmo.Handle.NONE : drag.handle()));
        }
    }

    private static ReferenceGizmo.Layout layout(MinecraftClient client, ReferenceImage reference) {
        Camera camera = client.gameRenderer.getCamera();
        Vec3d position = camera.getPos();
        double fov = ((GameRendererAccessor) client.gameRenderer).komaedit$getFov(camera, 1.0F, true);
        return ReferenceGizmo.layout(reference.world(), reference.pixelWidth(), reference.pixelHeight(),
                new Vec3(position.x, position.y, position.z), fov);
    }

    private static GizmoMath.Ray crosshair(MinecraftClient client) {
        Camera camera = client.gameRenderer.getCamera();
        Vec3d position = camera.getPos();
        Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        return new GizmoMath.Ray(new Vec3(position.x, position.y, position.z), new Vec3(look.x, look.y, look.z));
    }

    /** Clic izquierdo: agarra la pieza bajo el punto de mira; mientras se arrastra no se rompe nada. */
    private static boolean onAttack(MinecraftClient client, int clickCount) {
        if (!interactive(client)) {
            return false;
        }
        if (drag != null) {
            return true;
        }
        Optional<ReferenceImage> reference = edited();
        if (clickCount > 0 && reference.isPresent() && !hovered.none()) {
            drag = ReferenceDrag.begin(hovered, crosshair(client), reference.get().world(),
                    reference.get().pixelWidth(), reference.get().pixelHeight());
            return drag != null;
        }
        return false;
    }

    /** Clic derecho mientras se edita: termina. */
    public static boolean onUse(MinecraftClient client) {
        if (!interactive(client)) {
            return false;
        }
        stopEditing();
        return true;
    }

    /** La rueda mientras se edita: agranda o encoge un bloque (con Ctrl, 1/16). */
    public static boolean onScroll(double amount) {
        MinecraftClient client = MinecraftClient.getInstance();
        Optional<ReferenceImage> current = edited();
        if (!interactive(client) || current.isEmpty() || amount == 0.0 || drag != null) {
            return false;
        }
        ReferenceImage reference = current.get();
        double step = Screen.hasControlDown() ? ReferenceDrag.FINE_MOVE : 1.0;
        double width = reference.world().widthBlocks(reference.pixelWidth());
        double next = Math.max(step, Math.round(width / step) * step + Math.signum(amount) * step);
        update(reference.withWorld(reference.world().withWidthBlocks(next, reference.pixelWidth(),
                reference.pixelHeight())));
        hud(client);
        return true;
    }

    /**
     * Una tecla en el mundo (sin pantalla), después de las del fantasma: Ctrl+I y Ctrl+Mayús+I mientras sus entradas
     * de Controles sigan sin asignar, e Intro o Esc para terminar de editar.
     *
     * @return {@code true} si era nuestra y Minecraft no debe verla
     */
    public static boolean onKey(int key, int action, int modifiers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.player == null || action != GLFW.GLFW_PRESS) {
            return false;
        }
        if (interactive(client) && (key == GizmoKeys.KEY_ENTER || key == GizmoKeys.KEY_KP_ENTER
                || key == GizmoKeys.KEY_ESCAPE)) {
            stopEditing();
            return true;
        }
        boolean control = (modifiers & GizmoKeys.MOD_CONTROL) != 0;
        boolean shift = (modifiers & GizmoKeys.MOD_SHIFT) != 0;
        if (key == KEY_I && control) {
            if (shift && openKey.isUnbound()) {
                openScreen();
                return true;
            }
            if (!shift && toggleKey.isUnbound()) {
                toggleShown();
                return true;
            }
        }
        return false;
    }

    private static void tick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            toggleShown();
        }
        while (openKey.wasPressed()) {
            openScreen();
        }
        if (dirty && System.currentTimeMillis() - dirtyAt >= SAVE_DELAY_MILLIS) {
            save();
        }
        if (editing != null && edited().isEmpty()) {
            stopEditing();
        }
        if (editing != null && client.currentScreen == null && ++ticks % HUD_EVERY_TICKS == 0) {
            hud(client);
        }
    }

    /** La línea de la action bar mientras se edita: nombre, tamaño y qué hacer (la postura va en la lista). */
    private static void hud(MinecraftClient client) {
        Optional<ReferenceImage> current = edited();
        if (current.isEmpty() || client.inGameHud == null) {
            return;
        }
        ReferenceImage reference = current.get();
        WorldFrame world = reference.world();
        MutableText line = Text.literal(Feedback.PREFIX).formatted(Formatting.GOLD)
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(reference.displayName()).formatted(Formatting.WHITE))
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(Text.translatable("gui.komaedit.reference.size_blocks",
                        ReferenceTexts.blocks(world.widthBlocks(reference.pixelWidth())),
                        ReferenceTexts.blocks(world.heightBlocks(reference.pixelHeight()))).formatted(Formatting.GRAY));
        if (GhostClient.active()) {
            line.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable("gui.komaedit.reference.hud.ghost").formatted(Formatting.YELLOW));
        } else if (drag != null) {
            // Mientras se arrastra, lo que cambia (y cómo afinar) en lugar de la pista.
            Text doing = drag.handle().isCorner()
                    ? Text.translatable("gui.komaedit.reference.hud.scaling")
                    : drag.handle().gizmo().rotates()
                    ? Text.translatable("gui.komaedit.reference.hud.turning", ReferenceFormat.signed(drag.degrees()))
                    : Text.translatable("gui.komaedit.reference.hud.moving",
                    ReferenceFormat.offset(world.center().subtract(drag.start().center())));
            line.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(doing.copy().formatted(Formatting.AQUA));
        } else {
            line.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable("gui.komaedit.reference.hud.hint").formatted(Formatting.GRAY));
        }
        client.inGameHud.setOverlayMessage(line, false);
    }
}
