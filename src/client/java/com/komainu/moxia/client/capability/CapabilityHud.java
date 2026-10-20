package com.komainu.moxia.client.capability;

import com.komainu.moxia.capability.BuildCapability;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.Set;

/** Recordatorio discreto de las capacidades activas, a la derecha de la barra rápida. */
public final class CapabilityHud {
    private static final int TEXT = 0xFFB8F5C8;
    private static final int SMEAR = 0xFFFFD166;

    private CapabilityHud() {
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()
                || client.currentScreen instanceof CapabilityMenuScreen) {
            return;
        }
        Set<BuildCapability> active = CapabilityClientState.activeSet();
        boolean smear = CapabilityClientState.smearPending();
        if (active.isEmpty() && !smear) {
            return;
        }
        // Más allá del indicador de ataque, que puede ir pegado a la barra.
        int x = context.getScaledWindowWidth() / 2 + 118;
        int y = context.getScaledWindowHeight() - 12;
        if (smear) {
            context.drawTextWithShadow(client.textRenderer, Text.translatable("gui.komaedit.capability.hud.smear"),
                    x, y, SMEAR);
            y -= 10;
        }
        for (BuildCapability capability : active) {
            context.drawTextWithShadow(client.textRenderer, CapabilityTexts.shortName(capability), x, y, TEXT);
            y -= 10;
        }
    }
}
