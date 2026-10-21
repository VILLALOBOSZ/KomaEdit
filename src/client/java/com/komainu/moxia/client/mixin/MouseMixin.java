package com.komainu.moxia.client.mixin;

import com.komainu.moxia.client.gizmo.GhostClient;
import com.komainu.moxia.client.reference.ReferenceClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La rueda con el fantasma del gizmo en pantalla: lo acerca o lo aleja en lugar de cambiar de casilla. Editando una
 * imagen de referencia, la agranda o la encoge.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void komaedit$ghostWheel(long window, double horizontal, double vertical, CallbackInfo callbackInfo) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle() && (GhostClient.onScroll(vertical)
                || ReferenceClient.onScroll(vertical))) {
            callbackInfo.cancel();
        }
    }
}
