package com.komainu.moxia.client.capability;

import com.komainu.moxia.brush.BrushTool;
import com.komainu.moxia.capability.BlockStates;
import com.komainu.moxia.capability.BuildCapability;
import com.komainu.moxia.capability.CapabilityActionPayload;
import com.komainu.moxia.capability.CapabilityOption;
import com.komainu.moxia.capability.CapabilityStatePayload;
import com.komainu.moxia.capability.ClickRouting;
import com.komainu.moxia.capability.SmearPlanner;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Controles de las capacidades en el cliente. Decide con {@link ClickRouting} qué clics son de una capacidad; esos
 * no hacen lo de Minecraft y se mandan al servidor como intención ({@link CapabilityActionPayload}). También lleva
 * los atajos (menú rápido y uno por capacidad, configurables en Controles) y la colocación rápida.
 */
public final class CapabilityControls {
    private static final int VANILLA_USE_COOLDOWN = 4;
    /** En creativo, Minecraft rompe un bloque cada 5 ticks al mantener el clic izquierdo; se imita. */
    private static final int BREAK_REPEAT_TICKS = 5;
    private static final Map<BuildCapability, KeyBinding> TOGGLES = new EnumMap<>(BuildCapability.class);
    private static KeyBinding menuKey;
    /** Las acciones de una vez (retocar, extruir, arrastrar) piden soltar el botón entre una y otra. */
    private static boolean useArmed = true;
    private static int breakCooldown;

    private CapabilityControls() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CapabilityStatePayload.ID,
                (payload, context) -> context.client().execute(() -> CapabilityClientState.update(payload)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(CapabilityActionPayload.ID)) {
                sender.sendPacket(CapabilityActionPayload.simple(CapabilityActionPayload.Action.SYNC, 0, 0));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CapabilityClientState.clear();
            CapabilityOverlayRenderer.clear();
        });

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.komaedit.capability_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, "category.komaedit"));
        for (BuildCapability capability : BuildCapability.values()) {
            TOGGLES.put(capability, KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.komaedit.capability." + capability.id(), InputUtil.Type.KEYSYM,
                    InputUtil.UNKNOWN_KEY.getCode(), "category.komaedit")));
        }

        ClientPreAttackCallback.EVENT.register(CapabilityControls::onAttack);
        // Antes de que Minecraft lea la F en este tick: con «Retocar» y las manos vacías, elige propiedad.
        ClientTickEvents.START_CLIENT_TICK.register(CapabilityControls::consumeSwap);
        ClientTickEvents.END_CLIENT_TICK.register(CapabilityControls::endTick);
        WorldRenderEvents.AFTER_ENTITIES.register(CapabilityOverlayRenderer::render);
        HudRenderCallback.EVENT.register(CapabilityHud::render);
    }

    // ---- Llamado desde el mixin de MinecraftClient --------------------------------------------------------------

    /** Clic derecho: {@code true} si es de una capacidad (ya se ha mandado y Minecraft no debe hacer nada). */
    public static boolean onUse(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null
                || client.interactionManager.isBreakingBlock() || player.isRiding()) {
            return false;
        }
        Set<BuildCapability> active = CapabilityClientState.activeSet();
        if (active.isEmpty() && !CapabilityClientState.smearPending()) {
            return false;
        }
        ClickRouting.Intent intent = ClickRouting.use(context(client));
        if (intent == ClickRouting.Intent.NONE) {
            return false;
        }
        if (ClickRouting.pressOnly(intent)) {
            if (!useArmed) {
                return true;
            }
            useArmed = false;
        }
        if (dispatch(client, intent)) {
            player.swingHand(Hand.MAIN_HAND);
        }
        return true;
    }

    /** Espera que queda tras un clic derecho interceptado. */
    public static int useCooldown() {
        return CapabilityClientState.active(BuildCapability.FAST_PLACE) ? 0 : VANILLA_USE_COOLDOWN;
    }

    /** ¿Quitar la espera tras una colocación normal? Solo con un bloque o un cubo en la mano. */
    public static boolean fastPlace(MinecraftClient client) {
        if (client.player == null || !CapabilityClientState.active(BuildCapability.FAST_PLACE)) {
            return false;
        }
        ClickRouting.Held held = held(client.player.getMainHandStack());
        return held == ClickRouting.Held.BLOCK || held == ClickRouting.Held.FLUID;
    }

    // ---- Envíos que usan el menú --------------------------------------------------------------------------------

    public static void toggle(BuildCapability capability) {
        send(CapabilityActionPayload.simple(CapabilityActionPayload.Action.TOGGLE, capability.ordinal(), -1));
    }

    public static void option(CapabilityOption option, int value) {
        send(CapabilityActionPayload.simple(CapabilityActionPayload.Action.OPTION, option.ordinal(), value));
    }

    public static void gamemode(GameMode mode) {
        send(CapabilityActionPayload.simple(CapabilityActionPayload.Action.GAMEMODE, 0, mode.getId()));
    }

    public static void cancelSmear() {
        send(CapabilityActionPayload.simple(CapabilityActionPayload.Action.SMEAR_CANCEL, 0, 0));
    }

    public static KeyBinding toggleKey(BuildCapability capability) {
        return TOGGLES.get(capability);
    }

    public static KeyBinding menuKey() {
        return menuKey;
    }

    // ---- Entrada ------------------------------------------------------------------------------------------------

    private static boolean onAttack(MinecraftClient client, ClientPlayerEntity player, int clickCount) {
        if (client.currentScreen != null || client.world == null) {
            return false;
        }
        if (CapabilityClientState.activeSet().isEmpty() && !CapabilityClientState.smearPending()) {
            return false;
        }
        ClickRouting.Intent intent = ClickRouting.attack(context(client));
        if (intent == ClickRouting.Intent.NONE) {
            return false;
        }
        boolean fresh = clickCount > 0;
        boolean repeat = !fresh && intent == ClickRouting.Intent.BREAK && --breakCooldown <= 0;
        if ((fresh || repeat) && dispatch(client, intent)) {
            player.swingHand(Hand.MAIN_HAND);
            breakCooldown = BREAK_REPEAT_TICKS;
        }
        return true;
    }

    private static void consumeSwap(MinecraftClient client) {
        if (client.player == null || client.world == null || client.currentScreen != null
                || !CapabilityClientState.active(BuildCapability.TWEAK)
                || ClickRouting.swap(context(client)) != ClickRouting.Intent.TWEAK_PROPERTY) {
            return;
        }
        boolean pressed = false;
        while (client.options.swapHandsKey.wasPressed()) {
            pressed = true;
        }
        if (pressed) {
            dispatch(client, ClickRouting.Intent.TWEAK_PROPERTY);
        }
    }

    private static void endTick(MinecraftClient client) {
        if (!client.options.useKey.isPressed()) {
            useArmed = true;
        }
        boolean supported = client.player != null && CapabilityClientState.supported();
        for (Map.Entry<BuildCapability, KeyBinding> entry : TOGGLES.entrySet()) {
            while (entry.getValue().wasPressed()) {
                if (supported) {
                    toggle(entry.getKey());
                }
            }
        }
        while (menuKey.wasPressed()) {
            if (supported && client.currentScreen == null) {
                client.setScreen(new CapabilityMenuScreen(KeyBindingHelper.getBoundKeyOf(menuKey), false));
            }
        }
        CapabilityOverlayRenderer.tick(client);
    }

    // ---- Apoyo --------------------------------------------------------------------------------------------------

    static ClickRouting.Context context(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        HitResult hit = client.crosshairTarget;
        ClickRouting.Target target = hit == null ? ClickRouting.Target.AIR : switch (hit.getType()) {
            case BLOCK -> ClickRouting.Target.BLOCK;
            case ENTITY -> ClickRouting.Target.ENTITY;
            case MISS -> ClickRouting.Target.AIR;
        };
        ItemStack main = player.getMainHandStack();
        boolean sameBlock = target == ClickRouting.Target.BLOCK && main.getItem() instanceof BlockItem item
                && client.world.getBlockState(((BlockHitResult) hit).getBlockPos()).isOf(item.getBlock());
        return new ClickRouting.Context(CapabilityClientState.activeSet(), held(main), target, sameBlock,
                CapabilityClientState.smearPending(), player.getOffHandStack().isEmpty());
    }

    static ClickRouting.Held held(ItemStack stack) {
        if (stack.isEmpty()) {
            return ClickRouting.Held.EMPTY;
        }
        if (BrushTool.isTool(stack)) {
            return ClickRouting.Held.TOOL;
        }
        if (stack.isOf(Items.WATER_BUCKET) || stack.isOf(Items.LAVA_BUCKET)) {
            return ClickRouting.Held.FLUID;
        }
        if (stack.getItem() instanceof BlockItem item) {
            // Puertas, camas y plantas altas siguen la colocación normal: son dos bloques.
            return BlockStates.multiBlock(item.getBlock()) ? ClickRouting.Held.OTHER : ClickRouting.Held.BLOCK;
        }
        return ClickRouting.Held.OTHER;
    }

    /** Celda de «Colocar en el aire»: a la distancia elegida en la dirección de la mirada. */
    static BlockPos angelCell(MinecraftClient client, float tickDelta) {
        CapabilityStatePayload state = CapabilityClientState.get();
        if (state == null || client.player == null) {
            return null;
        }
        int distance = Math.clamp(state.angelDistance(), 1, Math.max(1, state.maxReach()));
        Vec3d eye = client.player.getCameraPosVec(tickDelta);
        Vec3d look = client.player.getRotationVec(tickDelta);
        return BlockPos.ofFloored(eye.add(look.multiply(distance)));
    }

    /** Final de un arrastre según hacia dónde se mira, o {@code null} si no estira nada. */
    static BlockPos smearEnd(MinecraftClient client, float tickDelta) {
        CapabilityStatePayload state = CapabilityClientState.get();
        if (state == null || !state.smearPending() || client.player == null || state.smearFace() < 0
                || state.smearFace() >= Direction.values().length) {
            return null;
        }
        Direction face = Direction.byId(state.smearFace());
        Vec3d eye = client.player.getCameraPosVec(tickDelta);
        Vec3d look = client.player.getRotationVec(tickDelta);
        HitResult hit = client.crosshairTarget;
        double fallback = hit != null && hit.getType() == HitResult.Type.BLOCK
                ? eye.distanceTo(hit.getPos())
                : client.player.getBlockInteractionRange();
        int length = SmearPlanner.length(new double[]{eye.x, eye.y, eye.z}, new double[]{look.x, look.y, look.z},
                BlockStates.cell(state.smearAnchor()), BlockStates.normal(face), fallback, state.maxSmearLength());
        return length == 0 ? null : state.smearAnchor().offset(face, length);
    }

    /** Manda la intención; {@code false} si no había nada que mandar. */
    private static boolean dispatch(MinecraftClient client, ClickRouting.Intent intent) {
        ClientPlayerEntity player = client.player;
        CapabilityActionPayload.Action action = CapabilityActionPayload.Action.valueOf(intent.name());
        switch (intent) {
            case SMEAR_CANCEL -> {
                cancelSmear();
                return true;
            }
            case ANGEL -> {
                BlockPos cell = angelCell(client, 1.0F);
                if (cell == null) {
                    return false;
                }
                send(new CapabilityActionPayload(action, cell, -1, null, player.isSneaking(), 0, 0));
                return true;
            }
            case SMEAR_END -> {
                BlockPos end = smearEnd(client, 1.0F);
                if (end == null) {
                    return false;
                }
                send(new CapabilityActionPayload(action, end, -1, null, player.isSneaking(), 0, 0));
                return true;
            }
            default -> {
                if (!(client.crosshairTarget instanceof BlockHitResult hit)
                        || hit.getType() != HitResult.Type.BLOCK) {
                    return false;
                }
                send(new CapabilityActionPayload(action, hit.getBlockPos(), hit.getSide().getId(), hit.getPos(),
                        player.isSneaking(), 0, 0));
                return true;
            }
        }
    }

    private static void send(CapabilityActionPayload payload) {
        if (ClientPlayNetworking.canSend(CapabilityActionPayload.ID)) {
            ClientPlayNetworking.send(payload);
        }
    }
}
