package com.komainu.moxia.client.mixin;

import com.komainu.moxia.client.gizmo.GhostClient;
import com.komainu.moxia.client.hotbar.HotbarControls;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La rueda antes que Minecraft: con la tecla de las barras mantenida elige barra en la tira; con el fantasma del gizmo
 * en pantalla, lo acerca o lo aleja. En los dos casos no cambia de casilla.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void komaedit$ghostWheel(long window, double horizontal, double vertical, CallbackInfo callbackInfo) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle()
                && (HotbarControls.onScroll(vertical) || GhostClient.onScroll(vertical))) {
            callbackInfo.cancel();
        }
    }
}
