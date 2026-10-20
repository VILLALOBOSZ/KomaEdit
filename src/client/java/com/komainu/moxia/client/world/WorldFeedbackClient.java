package com.komainu.moxia.client.world;

import com.komainu.moxia.client.gui.studio.ToolClientState;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.feedback.FeedbackEventPayload;
import com.komainu.moxia.feedback.SelectionPayload;
import com.komainu.moxia.feedback.SymmetryGuidePayload;
import com.komainu.moxia.feedback.WorldFeedbackSettings;
import com.komainu.moxia.preview.PreviewPlanCodec;
import com.komainu.moxia.preview.PreviewPlanPayload;
import com.komainu.moxia.progress.ProgressLine;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Lo que KomaEdit hace en el mundo desde el cliente, sin abrir el Studio:
 *
 * <ul>
 *     <li>Recibe y dibuja la vista previa ({@link PlanPreviewRenderer}), la selección y la simetría
 *     ({@link WorldOverlayRenderer}).</li>
 *     <li>Teclas: aplicar o descartar la vista previa (Intro y Retroceso, solo cuando hay una esperando), repetir la
 *     última operación donde estás, cuentagotas de bloque y los ajustes; todas configurables en Controles.</li>
 *     <li>Sonidos sutiles al confirmar, al terminar una edición larga y al fallar, si están activados.</li>
 * </ul>
 */
public final class WorldFeedbackClient {
    private static final long SOUND_GAP_NANOS = 250_000_000L;
    private static final double PICK_REACH = 192.0;
    /** Tras aplicar o descartar, otra pulsación en este rato no vuelve a mandar nada (la vista previa ya se va). */
    private static final long PREVIEW_KEY_GAP_NANOS = 1_000_000_000L;
    private static KeyBinding confirm;
    private static KeyBinding discard;
    private static KeyBinding repeat;
    private static KeyBinding eyedropper;
    private static KeyBinding settings;
    private static long lastSound;
    private static long lastPreviewKey;

    private WorldFeedbackClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PreviewPlanPayload.ID, (payload, context) ->
                context.client().execute(() -> PlanPreviewRenderer.receive(payload, context.client())));
        ClientPlayNetworking.registerGlobalReceiver(SelectionPayload.ID, (payload, context) ->
                WorldOverlayRenderer.selection(payload));
        ClientPlayNetworking.registerGlobalReceiver(SymmetryGuidePayload.ID, (payload, context) ->
                WorldOverlayRenderer.symmetry(payload));
        ClientPlayNetworking.registerGlobalReceiver(FeedbackEventPayload.ID, (payload, context) ->
                context.client().execute(() -> sound(context.client(), payload.kind())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            PlanPreviewRenderer.clear();
            WorldOverlayRenderer.clear();
        }));
        WorldRenderEvents.LAST.register(PlanPreviewRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(WorldOverlayRenderer::render);

        confirm = key("preview_confirm", GLFW.GLFW_KEY_ENTER);
        discard = key("preview_discard", GLFW.GLFW_KEY_BACKSPACE);
        repeat = key("repeat", InputUtil.UNKNOWN_KEY.getCode());
        eyedropper = key("eyedropper", InputUtil.UNKNOWN_KEY.getCode());
        settings = key("world_settings", InputUtil.UNKNOWN_KEY.getCode());
        ClientTickEvents.END_CLIENT_TICK.register(WorldFeedbackClient::tick);
    }

    /** La tecla de aplicar la vista previa (Intro de serie); también coloca el fantasma del gizmo. */
    public static KeyBinding confirmKey() {
        return confirm;
    }

    /** La de descartarla (Retroceso de serie); también quita el fantasma. */
    public static KeyBinding discardKey() {
        return discard;
    }

    private static KeyBinding key(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.komaedit." + id, InputUtil.Type.KEYSYM, code,
                "category.komaedit"));
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        boolean pending = previewWaiting() && System.nanoTime() - lastPreviewKey > PREVIEW_KEY_GAP_NANOS;
        while (confirm.wasPressed()) {
            if (player != null && pending) {
                player.networkHandler.sendChatCommand("komaedit confirm");
                lastPreviewKey = System.nanoTime();
                pending = false;
            }
        }
        while (discard.wasPressed()) {
            if (player != null && pending) {
                player.networkHandler.sendChatCommand("komaedit confirm cancel");
                lastPreviewKey = System.nanoTime();
                pending = false;
            }
        }
        while (repeat.wasPressed()) {
            if (player != null) {
                player.networkHandler.sendChatCommand(InputUtil.isKeyPressed(client.getWindow().getHandle(),
                        GLFW.GLFW_KEY_LEFT_SHIFT) || player.isSneaking() ? "komaedit repeat -v" : "komaedit repeat");
            }
        }
        while (eyedropper.wasPressed()) {
            if (player != null) {
                pick(client, player);
            }
        }
        while (settings.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new WorldSettingsScreen(null));
            }
        }
    }

    /** Si hay una vista previa esperando confirmación (dibujada aquí o anunciada por el servidor). */
    static boolean previewWaiting() {
        return PlanPreviewRenderer.header().isPresent() || ToolClientState.previewWaiting();
    }

    /** Al llegar una vista previa: una pista en la action bar con las teclas de aplicar y descartar. */
    static void previewReady(PreviewPlanCodec.Header header) {
        PlanPreviewRenderer.allowRetry();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) {
            return;
        }
        Text hint = prefix().append(Text.translatable("gui.komaedit.world.preview_ready",
                Text.literal(ProgressLine.group(header.changes())).formatted(Formatting.WHITE),
                confirm.getBoundKeyLocalizedText().copy().formatted(Formatting.GREEN),
                discard.getBoundKeyLocalizedText().copy().formatted(Formatting.RED)).formatted(Formatting.GRAY));
        client.inGameHud.setOverlayMessage(hint, false);
    }

    /** El prefijo de los mensajes de KomaEdit ({@code KE ·}), también en lo que escribe el cliente. */
    private static MutableText prefix() {
        return Text.literal(Feedback.PREFIX).formatted(Formatting.GOLD)
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY));
    }

    // ---------------------------------------------------------------- cuentagotas

    /**
     * El bloque que se mira, hasta 192 bloques: en creativo va a la mano (como la rueda del ratón, pero lejos); en
     * supervivencia se elige si está en el inventario. Con Mayús se copia además su estado exacto
     * ({@code oak_stairs[facing=east,…]}) para pegarlo en una orden.
     */
    private static void pick(MinecraftClient client, ClientPlayerEntity player) {
        Entity camera = client.getCameraEntity() == null ? player : client.getCameraEntity();
        HitResult hit = camera.raycast(PICK_REACH, 1.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK || client.world == null) {
            return;
        }
        BlockPos pos = block.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        String exact = BlockArgumentParser.stringifyBlockState(state);
        boolean copy = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT);
        if (copy) {
            client.keyboard.setClipboard(exact);
        }
        ItemStack stack = state.getBlock().getPickStack(client.world, pos, state);
        if (!stack.isEmpty() && client.interactionManager != null) {
            PlayerInventory inventory = player.getInventory();
            if (player.getAbilities().creativeMode) {
                inventory.addPickBlock(stack);
                client.interactionManager.clickCreativeStack(player.getStackInHand(Hand.MAIN_HAND),
                        36 + inventory.selectedSlot);
            } else {
                int slot = inventory.getSlotWithStack(stack);
                if (slot >= 0 && PlayerInventory.isValidHotbarIndex(slot)) {
                    inventory.selectedSlot = slot;
                } else if (slot >= 0) {
                    client.interactionManager.pickFromInventory(slot);
                }
            }
        }
        if (client.inGameHud != null) {
            client.inGameHud.setOverlayMessage(prefix().append(Text.translatable(
                    copy ? "gui.komaedit.world.copied" : "gui.komaedit.world.picked", exact)
                    .formatted(Formatting.GRAY)), false);
        }
    }

    // ---------------------------------------------------------------- sonidos

    private static void sound(MinecraftClient client, FeedbackEventPayload.Kind kind) {
        WorldFeedbackSettings current = ClientWorldSettings.get();
        if (!current.sounds() || current.soundVolume() <= 0.0) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastSound < SOUND_GAP_NANOS) {
            return;
        }
        lastSound = now;
        RegistryEntry<SoundEvent> event = switch (kind) {
            case CONFIRM -> SoundEvents.BLOCK_NOTE_BLOCK_HAT;
            case DONE -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME;
            case FAIL -> SoundEvents.BLOCK_NOTE_BLOCK_BASS;
        };
        float pitch = switch (kind) {
            case CONFIRM -> 1.4F;
            case DONE -> 1.5F;
            case FAIL -> 0.7F;
        };
        client.getSoundManager().play(PositionedSoundInstance.master(event.value(), pitch,
                (float) current.soundVolume()));
    }
}
