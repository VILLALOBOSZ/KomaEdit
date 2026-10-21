package com.komainu.moxia.client.utility;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.views.SavedView;
import com.komainu.moxia.views.ViewBook;
import com.komainu.moxia.views.ViewNames;
import com.komainu.moxia.views.ViewPayloads;
import com.komainu.moxia.views.ViewTeleport;
import com.komainu.moxia.views.ViewTrail;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Las vistas en el cliente: las privadas ({@link ViewBook}, en {@code config/komaedit/views.json}, una lista por
 * mundo o servidor), las del equipo que manda el servidor, los saltos (que el servidor comprueba) y las teclas de la
 * vista fija y de volver.
 */
public final class ClientViews {
    private static final ViewTrail TRAIL = new ViewTrail();
    private static ViewBook book;
    private static boolean unreadable;
    private static ViewPayloads.ViewCatalogPayload catalog = ViewPayloads.ViewCatalogPayload.NONE;
    private static ViewTeleport.Target pendingFrom;
    private static KeyBinding pinnedKey;
    private static KeyBinding backKey;
    private static KeyBinding listKey;
    private static Runnable onChange = () -> {
    };

    private ClientViews() {
    }

    public static void register() {
        pinnedKey = key("view_pinned");
        backKey = key("view_back");
        listKey = key("views");
        ClientPlayNetworking.registerGlobalReceiver(ViewPayloads.ViewCatalogPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    catalog = payload;
                    onChange.run();
                }));
        ClientPlayNetworking.registerGlobalReceiver(ViewPayloads.ViewResultPayload.ID, (payload, context) ->
                context.client().execute(() -> result(context.client(), payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            catalog = ViewPayloads.ViewCatalogPayload.NONE;
            pendingFrom = null;
            TRAIL.clear();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(ClientViews::tick);
    }

    private static KeyBinding key(String id) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.komaedit." + id, InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(), "category.komaedit"));
    }

    private static void tick(MinecraftClient client) {
        while (pinnedKey.wasPressed()) {
            pinned(client);
        }
        while (backKey.wasPressed()) {
            back(client);
        }
        while (listKey.wasPressed()) {
            if (client.currentScreen == null && client.player != null) {
                client.setScreen(new ViewsScreen(null));
            }
        }
    }

    /** Para que la lista abierta se rehaga cuando llega el catálogo. */
    static void onChange(Runnable listener) {
        onChange = listener == null ? () -> {
        } : listener;
    }

    // ------------------------------------------------------------------ sitio y archivo

    /** El sitio de las vistas: el mundo de un jugador por su carpeta o el servidor por su dirección. */
    public static String place(MinecraftClient client) {
        if (client.isIntegratedServerRunning() && client.getServer() != null) {
            Path root = client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            return ViewBook.singleplayer(root.getFileName() == null ? "mundo" : root.getFileName().toString());
        }
        ServerInfo server = client.getCurrentServerEntry();
        return ViewBook.server(server == null ? "realms" : server.address);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("komaedit").resolve("views.json");
    }

    static synchronized ViewBook book() {
        if (book == null) {
            Path file = file();
            book = new ViewBook();
            if (Files.isRegularFile(file)) {
                try {
                    int[] skipped = {0};
                    book = ViewBook.fromJson(Files.readString(file, StandardCharsets.UTF_8), skipped);
                    if (skipped[0] > 0) {
                        KomaEditMod.LOGGER.warn("Skipped {} unreadable KomaEdit views", skipped[0]);
                    }
                } catch (IOException | RuntimeException exception) {
                    // No se pisa un archivo que no se entiende (de una versión más nueva, o editado a mano).
                    unreadable = true;
                    KomaEditMod.LOGGER.error("Could not read {}; views will not be saved", file, exception);
                }
            }
        }
        return book;
    }

    private static void save() {
        if (unreadable) {
            throw new IllegalArgumentException("No se pudo leer config/komaedit/views.json: las vistas no se guardan"
                    + " hasta que lo arregles o lo borres");
        }
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            AtomicStorage.writeString(file, book().toJson());
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudieron guardar las vistas: " + exception.getMessage());
        }
        onChange.run();
    }

    // ------------------------------------------------------------------ vistas

    public static List<SavedView> own(MinecraftClient client) {
        return book().list(place(client));
    }

    public static ViewPayloads.ViewCatalogPayload catalog() {
        return catalog;
    }

    public static ViewTeleport.Target here(ClientPlayerEntity player) {
        return new ViewTeleport.Target(player.getWorld().getRegistryKey().getValue().toString(), player.getX(),
                player.getY(), player.getZ(), player.getYaw(), player.getPitch());
    }

    /** Guarda dónde está el jugador y hacia dónde mira. Devuelve el mensaje. */
    public static String save(MinecraftClient client, String name) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            throw new IllegalArgumentException("No hay mundo");
        }
        ViewTeleport.Target here = here(player);
        String clean = ViewNames.validate(name);
        boolean replaced = book().save(place(client), SavedView.here(clean, here.dimension(), here.x(), here.y(),
                here.z(), here.yaw(), here.pitch(), System.currentTimeMillis()));
        save();
        return ClientWords.INSTANCE.tr(replaced ? "gui.komaedit.views.updated" : "gui.komaedit.views.saved", clean);
    }

    public static String remove(MinecraftClient client, String name) {
        if (!book().remove(place(client), name)) {
            throw new IllegalArgumentException(ClientWords.INSTANCE.tr("gui.komaedit.views.missing",
                    ViewNames.clean(name)));
        }
        save();
        return ClientWords.INSTANCE.tr("gui.komaedit.views.removed", ViewNames.clean(name));
    }

    public static String pin(MinecraftClient client, String name, boolean pinned) {
        SavedView view = book().pin(place(client), name, pinned);
        save();
        return ClientWords.INSTANCE.tr(pinned ? "gui.komaedit.views.pinned" : "gui.komaedit.views.unpinned",
                view.name());
    }

    /** Una vista por nombre: primero las propias, luego las del equipo. */
    public static Optional<SavedView> find(MinecraftClient client, String name) {
        Optional<SavedView> own = book().find(place(client), name);
        if (own.isPresent()) {
            return own;
        }
        return catalog.shared().stream().filter(view -> ViewNames.key(view.name()).equals(ViewNames.key(name)))
                .findFirst();
    }

    public static void go(MinecraftClient client, String name) {
        SavedView view = find(client, name).orElseThrow(() -> new IllegalArgumentException(
                ClientWords.INSTANCE.tr("gui.komaedit.views.missing", ViewNames.clean(name))));
        jump(client, ViewTeleport.Target.of(view));
    }

    /** Pide al servidor el salto; si lo acepta, el sitio de ahora queda para volver. */
    public static void jump(MinecraftClient client, ViewTeleport.Target target) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(ViewPayloads.ViewJumpPayload.ID)) {
            RulerClient.status(client, ClientWords.INSTANCE.tr("gui.komaedit.views.no_server"));
            return;
        }
        pendingFrom = here(player);
        ClientPlayNetworking.send(new ViewPayloads.ViewJumpPayload(target));
    }

    public static void back(MinecraftClient client) {
        Optional<ViewTeleport.Target> previous = TRAIL.back();
        if (previous.isEmpty()) {
            RulerClient.status(client, ClientWords.INSTANCE.tr("gui.komaedit.views.no_back"));
            return;
        }
        jumpWithoutTrail(client, previous.get());
    }

    /** La tecla de la vista fija: a la vista y, si sigues en ella, de vuelta. */
    public static void pinned(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        ViewTeleport.Target here = here(player);
        Optional<SavedView> pinned = book().pinned(place(client), here.dimension());
        if (pinned.isEmpty()) {
            RulerClient.status(client, ClientWords.INSTANCE.tr("gui.komaedit.views.no_pinned"));
            return;
        }
        jumpWithoutTrail(client, TRAIL.pinned(here, pinned.get()));
    }

    /** Un salto de ida y vuelta (volver, vista fija): no se apunta para «volver», o se volvería en círculo. */
    private static void jumpWithoutTrail(MinecraftClient client, ViewTeleport.Target target) {
        jump(client, target);
        pendingFrom = null;
    }

    public static void share(MinecraftClient client, SavedView view) {
        if (!ClientPlayNetworking.canSend(ViewPayloads.ViewSharePayload.ID)) {
            RulerClient.status(client, ClientWords.INSTANCE.tr("gui.komaedit.views.no_server"));
            return;
        }
        ClientPlayNetworking.send(new ViewPayloads.ViewSharePayload(view.name(), ViewTeleport.Target.of(view)));
    }

    public static void requestCatalog() {
        if (ClientPlayNetworking.canSend(ViewPayloads.ViewCatalogRequest.ID)) {
            ClientPlayNetworking.send(new ViewPayloads.ViewCatalogRequest());
        }
    }

    public static boolean canGoBack() {
        return TRAIL.canGoBack();
    }

    private static void result(MinecraftClient client, ViewPayloads.ViewResultPayload payload) {
        if (payload.jump() && payload.ok() && pendingFrom != null) {
            TRAIL.left(pendingFrom);
        }
        if (payload.jump()) {
            pendingFrom = null;
        }
        if (!payload.message().isEmpty() && client.inGameHud != null) {
            client.inGameHud.setOverlayMessage(ClientWords.prefix().append(Text.literal(payload.message())
                    .formatted(payload.ok() ? Formatting.GREEN : Formatting.RED)), false);
        }
        onChange.run();
    }
}
