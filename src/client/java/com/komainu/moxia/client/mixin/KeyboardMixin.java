package com.komainu.moxia.client.mixin;

import com.komainu.moxia.client.gizmo.GhostClient;
import com.komainu.moxia.client.reference.ReferenceClient;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Las teclas del fantasma del gizmo antes que Minecraft: con un fantasma en pantalla, Intro lo coloca, Esc lo quita
 * (en lugar de abrir el menú), las flechas lo mueven y Ctrl+R lo gira. Sin fantasma solo mira Ctrl+V (pegar).
 * Después, las de las imágenes de referencia: Ctrl+I, Ctrl+Mayús+I e Intro o Esc para dejar de editar una.
 */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void komaedit$ghostKeys(long window, int key, int scancode, int action, int modifiers,
                                    CallbackInfo callbackInfo) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle()
                && (GhostClient.onKey(key, action, modifiers) || ReferenceClient.onKey(key, action, modifiers))) {
            callbackInfo.cancel();
        }
    }
}
