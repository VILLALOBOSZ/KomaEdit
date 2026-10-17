package com.komainu.moxia.client.brush;

import com.komainu.moxia.brush.BrushInput;
import com.komainu.moxia.brush.BrushTool;
import com.komainu.moxia.brush.BrushToolPayload;
import com.komainu.moxia.client.preview.PreviewHologramRenderer;
import com.komainu.moxia.preview.PreviewHologramPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

/**
 * Controles de las herramientas de KomaEdit en el cliente. Con un pincel o la supervarita en la mano, los clics y
 * la tecla de cambiar de mano no hacen lo de vanilla (romper, usar, cambiar de mano): se mandan al servidor como
 * {@link BrushToolPayload}. El servidor apunta con su propio rayo, así funcionan a cualquier distancia.
 *
 * <p>También recibe el holograma de la vista previa de pinceles y brochas.</p>
 */
public final class BrushToolControls {
    private BrushToolControls() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PreviewHologramPayload.ID,
                (payload, context) -> PreviewHologramRenderer.update(payload));
        WorldRenderEvents.AFTER_ENTITIES.register(PreviewHologramRenderer::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PreviewHologramRenderer.clear());

        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (!holdingTool(player)) {
                return false;
            }
            if (clickCount > 0 && client.currentScreen == null) {
                send(BrushInput.Base.LEFT, player.isSneaking());
            }
            return true;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient() || hand != Hand.MAIN_HAND || !holdingTool(player)) {
                return ActionResult.PASS;
            }
            send(BrushInput.Base.RIGHT, player.isSneaking());
            return ActionResult.FAIL;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            if (!world.isClient() || hand != Hand.MAIN_HAND || !holdingTool(player)) {
                return TypedActionResult.pass(stack);
            }
            send(BrushInput.Base.RIGHT, player.isSneaking());
            return TypedActionResult.fail(stack);
        });
        // Antes de que vanilla lea la tecla en este tick: si la herramienta está en la mano, la F es suya.
        ClientTickEvents.START_CLIENT_TICK.register(BrushToolControls::consumeSwap);
    }

    private static void consumeSwap(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null || !holdingTool(client.player)) {
            return;
        }
        boolean pressed = false;
        while (client.options.swapHandsKey.wasPressed()) {
            pressed = true;
        }
        if (pressed) {
            send(BrushInput.Base.SWAP, client.player.isSneaking());
        }
    }

    private static boolean holdingTool(PlayerEntity player) {
        return player != null && BrushTool.isTool(player.getMainHandStack());
    }

    private static void send(BrushInput.Base base, boolean sneaking) {
        if (ClientPlayNetworking.canSend(BrushToolPayload.ID)) {
            ClientPlayNetworking.send(new BrushToolPayload(BrushInput.of(base, sneaking)));
        }
    }
}
