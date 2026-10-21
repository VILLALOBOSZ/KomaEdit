package com.komainu.moxia.client.mixin;

import com.komainu.moxia.client.gizmo.GhostClient;
import com.komainu.moxia.client.hotbar.HotbarControls;
import com.komainu.moxia.client.reference.ReferenceClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La rueda antes que Minecraft. Cada uno la toma solo cuando le toca, en este orden, y si nadie la toma cambia de
 * casilla como siempre:
 * <ol>
 *     <li>con la tecla de las barras mantenida, elige barra en la tira (sin esa tecla no la toca; va primero para que
 *     también se pueda cambiar de barra con un fantasma en pantalla);</li>
 *     <li>con el fantasma del gizmo en pantalla, lo acerca o lo aleja;</li>
 *     <li>editando una imagen de referencia con su gizmo (nunca a la vez que un fantasma), la agranda o la encoge.</li>
 * </ol>
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void komaedit$ghostWheel(long window, double horizontal, double vertical, CallbackInfo callbackInfo) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle()
                && (HotbarControls.onScroll(vertical) || GhostClient.onScroll(vertical)
                || ReferenceClient.onScroll(vertical))) {
            callbackInfo.cancel();
        }
    }
}
