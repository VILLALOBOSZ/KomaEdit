package com.komainu.moxia.client.hotbar;

import com.komainu.moxia.hotbar.HotbarCodec;
import com.komainu.moxia.hotbar.HotbarPayloads.HotbarActionPayload;
import com.komainu.moxia.hotbar.HotbarPayloads.HotbarSyncPayload;
import com.komainu.moxia.hotbar.HotbarPayloads.HotbarTextPayload;
import com.komainu.moxia.hotbar.HotbarRules;
import com.komainu.moxia.hotbar.SavedHotbar;
import com.komainu.moxia.hotbar.ui.HotbarSwitcher;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * Las barras guardadas en el cliente: la tecla (Alt izquierdo de serie, como en Axiom; mantener y rueda para la tira,
 * tocar para el menú circular), otra opcional solo para el menú, los paquetes del servidor y el cambio de barra.
 *
 * <p>En creativo la barra se pone al instante en el cliente y el servidor la confirma con la suya (o devuelve la de
 * antes si no se podía). En supervivencia se espera al servidor, que es quien sabe qué hay en el inventario.</p>
 */
public final class HotbarControls {
    private static final HotbarSwitcher SWITCHER = new HotbarSwitcher();
    private static KeyBinding stripKey;
    private static KeyBinding menuKey;
    /** Quién espera un código exportado (el menú o el Studio); si nadie, se copia y se avisa en la barra de acción. */
    private static Consumer<String> exportListener;
    private static int seenVersion = -1;
    private static List<String> seenNames = List.of();

    private HotbarControls() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HotbarSyncPayload.ID,
                (payload, context) -> context.client().execute(() -> ClientHotbars.update(payload)));
        ClientPlayNetworking.registerGlobalReceiver(HotbarTextPayload.ID,
                (payload, context) -> context.client().execute(() -> exported(context.client(), payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientHotbars.clear();
            SWITCHER.cancel();
            exportListener = null;
        });
        stripKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.komaedit.hotbar_strip",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "category.komaedit"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.komaedit.hotbar_menu",
                InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "category.komaedit"));
        ClientTickEvents.END_CLIENT_TICK.register(HotbarControls::tick);
        HudRenderCallback.EVENT.register(HotbarHud::render);
    }

    private static void tick(MinecraftClient client) {
        boolean usable = client.player != null && client.currentScreen == null && ClientHotbars.known();
        boolean down = usable && stripKey.isPressed();
        if (SWITCHER.held() && client.currentScreen != null) {
            SWITCHER.cancel();
        }
        if (SWITCHER.held() && seenVersion != ClientHotbars.version()) {
            // Llegó otro catálogo con la tira abierta: la elegida se vuelve a buscar por su nombre.
            int selected = SWITCHER.selected();
            String name = selected >= 0 && selected < seenNames.size() ? seenNames.get(selected) : "";
            remember();
            SWITCHER.resize(seenNames.size(), seenNames.indexOf(name));
        }
        if (!SWITCHER.held() && down) {
            remember();
            SWITCHER.press(seenNames.size(), ClientHotbars.activeIndex());
        } else if (SWITCHER.held() && !down) {
            HotbarSwitcher.Result result = SWITCHER.release(client.isWindowFocused(), ClientHotbars.activeIndex());
            switch (result.outcome()) {
                case APPLY -> apply(client, seenNames.get(result.index()));
                case OPEN_MENU -> client.setScreen(new HotbarMenuScreen());
                case NONE -> {
                }
            }
        }
        SWITCHER.tick();
        while (stripKey.wasPressed()) {
            // Lo mantenido se mira con isPressed; las pulsaciones sueltas no se acumulan.
        }
        while (menuKey.wasPressed()) {
            if (usable) {
                client.setScreen(new HotbarMenuScreen());
            }
        }
    }

    /** Los nombres con los que trabaja la tira, para no confundir índices si llega otro catálogo. */
    private static void remember() {
        seenNames = ClientHotbars.entries().stream().map(ClientHotbars.Entry::name).toList();
        seenVersion = ClientHotbars.version();
    }

    /** La rueda con la tecla mantenida: elige barra en la tira y no cambia de casilla. */
    public static boolean onScroll(double vertical) {
        return SWITCHER.held() && MinecraftClient.getInstance().currentScreen == null && SWITCHER.scroll(vertical);
    }

    public static boolean stripVisible() {
        return SWITCHER.visible();
    }

    public static int selected() {
        return SWITCHER.selected();
    }

    public static KeyBinding stripKey() {
        return stripKey;
    }

    // ---- Envíos ------------------------------------------------------------------------------------------------

    /** Cambia a la barra {@code name}: en creativo la pone ya en el cliente y el servidor la confirma. */
    public static void apply(MinecraftClient client, String name) {
        ClientPlayerEntity player = client.player;
        ClientHotbars.Entry entry = ClientHotbars.entry(name);
        if (player == null || entry == null || !ClientPlayNetworking.canSend(HotbarActionPayload.ID)) {
            return;
        }
        HotbarRules.Decision decision = ClientHotbars.decision(client);
        if (!decision.allowed()) {
            player.sendMessage(Text.literal(decision.reason()), true);
            return;
        }
        if (decision.sets()) {
            for (int slot = 0; slot < SavedHotbar.SLOTS; slot++) {
                player.getInventory().setStack(slot, entry.slots().get(slot).copy());
            }
        }
        ClientPlayNetworking.send(HotbarActionPayload.of(HotbarActionPayload.Kind.APPLY, name));
    }

    public static void save(String name, boolean overwrite) {
        send(new HotbarActionPayload(HotbarActionPayload.Kind.SAVE, name, "", overwrite ? 1 : 0));
    }

    public static void delete(String name) {
        send(HotbarActionPayload.of(HotbarActionPayload.Kind.DELETE, name));
    }

    public static void undo() {
        send(HotbarActionPayload.of(HotbarActionPayload.Kind.UNDO, ""));
    }

    public static void autosave(boolean on) {
        send(new HotbarActionPayload(HotbarActionPayload.Kind.AUTOSAVE, "", "", on ? 1 : 0));
    }

    /**
     * Pide el código de una barra (o de todas con {@code ""}); cuando llega se copia al portapapeles y se le pasa a
     * {@code listener} el aviso para el jugador.
     */
    public static void export(String name, Consumer<String> listener) {
        exportListener = listener;
        send(HotbarActionPayload.of(HotbarActionPayload.Kind.EXPORT, name));
    }

    /** Importa un código {@code kh1:}; devuelve el aviso si no lo parece. */
    public static String importCode(String code) {
        String text = code == null ? "" : code.strip();
        if (!text.startsWith(HotbarCodec.PREFIX)) {
            return I18n.translate("gui.komaedit.hotbar.status.no_code");
        }
        if (text.length() > HotbarCodec.MAX_TEXT) {
            return I18n.translate("gui.komaedit.hotbar.status.too_long", HotbarCodec.MAX_TEXT);
        }
        send(new HotbarActionPayload(HotbarActionPayload.Kind.IMPORT, "", text, 0));
        return I18n.translate("gui.komaedit.hotbar.status.importing");
    }

    public static boolean supported() {
        return ClientHotbars.known() && ClientPlayNetworking.canSend(HotbarActionPayload.ID);
    }

    private static void send(HotbarActionPayload payload) {
        if (ClientPlayNetworking.canSend(HotbarActionPayload.ID)) {
            ClientPlayNetworking.send(payload);
        }
    }

    private static void exported(MinecraftClient client, HotbarTextPayload payload) {
        client.keyboard.setClipboard(payload.text());
        String notice = I18n.translate(payload.hotbars() == 1 ? "gui.komaedit.hotbar.status.copied_one"
                : "gui.komaedit.hotbar.status.copied", payload.hotbars(), payload.text().length());
        Consumer<String> listener = exportListener;
        exportListener = null;
        if (listener != null) {
            listener.accept(notice);
        } else if (client.player != null) {
            client.player.sendMessage(Text.literal(notice), true);
        }
    }
}
