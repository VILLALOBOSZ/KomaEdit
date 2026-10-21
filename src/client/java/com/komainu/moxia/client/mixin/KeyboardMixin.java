package com.komainu.moxia.client.mixin;

import com.komainu.moxia.client.display.DisplayEditorClient;
import com.komainu.moxia.client.gizmo.GhostClient;
import com.komainu.moxia.client.hotbar.HotbarControls;
import com.komainu.moxia.client.reference.ReferenceClient;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Las teclas de KomaEdit en el mundo, antes que Minecraft. Cada uno deja pasar las que no son suyas en ese momento,
 * en este orden:
 * <ol>
 *     <li>el editor de displays ({@link DisplayEditorClient}), solo con él abierto y sin fantasma;</li>
 *     <li>el fantasma del gizmo ({@link GhostClient}): con uno en pantalla, Intro lo coloca, Esc lo quita (en lugar de
 *     abrir el menú), las flechas lo mueven y Ctrl+R lo gira; sin fantasma solo mira Ctrl+V (pegar);</li>
 *     <li>las imágenes de referencia ({@link ReferenceClient}): Ctrl+I y Ctrl+Mayús+I, e Intro o Esc para dejar de
 *     editar una con su gizmo (nunca con un fantasma en pantalla).</li>
 * </ol>
 * Antes de todo, una tecla pulsada con la de las barras mantenida la marca como modificador ({@link HotbarControls}):
 * Ctrl+Alt+R no abre el menú de barras al soltar Alt.
 */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void komaedit$ghostKeys(long window, int key, int scancode, int action, int modifiers,
                                    CallbackInfo callbackInfo) {
        if (window != MinecraftClient.getInstance().getWindow().getHandle()) {
            return;
        }
        HotbarControls.otherInput(InputUtil.Type.KEYSYM, key, action);
        if (DisplayEditorClient.onKey(key, action, modifiers) || GhostClient.onKey(key, action, modifiers)
                || ReferenceClient.onKey(key, action, modifiers)) {
            callbackInfo.cancel();
        }
    }
}
