package com.komainu.moxia.brush;

import com.komainu.moxia.brush.ez.EzBrushManager;
import com.komainu.moxia.brush.superwand.SuperwandManager;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entrada de las herramientas de KomaEdit (pinceles y supervarita).
 *
 * <p>Con KomaEdit en el cliente, los clics y la tecla F con la herramienta llegan como {@link BrushToolPayload}
 * (el cliente no rompe bloques ni cambia de mano). Para clientes sin el mod quedan los eventos de Fabric del
 * servidor: clic derecho siempre y clic izquierdo sobre bloques al alcance; ahí la F no se puede interceptar.</p>
 */
public final class BrushToolInputs {
    /** Último tick con clic derecho por jugador: un clic sobre un bloque llega como uso de bloque y de objeto. */
    private static final Map<UUID, Integer> LAST_RIGHT = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_LEFT = new ConcurrentHashMap<>();

    private BrushToolInputs() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(BrushToolPayload.ID, BrushToolPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BrushToolPayload.ID, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload.input()))
        );
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity server)
                    || !BrushTool.isTool(server.getMainHandStack())) {
                return ActionResult.PASS;
            }
            fallback(server, BrushInput.Base.RIGHT, LAST_RIGHT);
            return ActionResult.SUCCESS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (world.isClient() || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity server)
                    || !BrushTool.isTool(stack)) {
                return TypedActionResult.pass(stack);
            }
            fallback(server, BrushInput.Base.RIGHT, LAST_RIGHT);
            return TypedActionResult.success(stack);
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity server)
                    || !BrushTool.isTool(server.getMainHandStack())) {
                return ActionResult.PASS;
            }
            fallback(server, BrushInput.Base.LEFT, LAST_LEFT);
            return ActionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(SuperwandManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.player.getUuid();
            EzBrushManager.remove(playerId);
            SuperwandManager.remove(playerId);
            LAST_RIGHT.remove(playerId);
            LAST_LEFT.remove(playerId);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            EzBrushManager.clearAll();
            SuperwandManager.clearAll();
            LAST_RIGHT.clear();
            LAST_LEFT.clear();
        });
    }

    private static void fallback(ServerPlayerEntity player, BrushInput.Base base, Map<UUID, Integer> last) {
        int tick = player.getServer().getTicks();
        Integer previous = last.put(player.getUuid(), tick);
        if (previous != null && previous == tick) {
            return;
        }
        handle(player, BrushInput.of(base, player.isSneaking()));
    }

    /** Una acción con la herramienta de la mano principal. */
    public static void handle(ServerPlayerEntity player, BrushInput input) {
        String toolId = BrushTool.id(player.getMainHandStack());
        BrushTool.Kind kind = BrushTool.kind(toolId);
        if (kind == null) {
            return;
        }
        if (kind == BrushTool.Kind.SUPERWAND ? !KomaPermissions.can(player, KomaPermissions.SELECT + ".superwand")
                : !KomaPermissions.canAny(player, KomaPermissions.BUILD + ".brush")) {
            Feedback.statusWarn(player, Text.literal("No tienes permiso para usar esta herramienta"));
            return;
        }
        switch (kind) {
            case BRUSH -> EzBrushManager.handle(player, toolId, input);
            case SUPERWAND -> SuperwandManager.handle(player, input);
        }
    }
}
