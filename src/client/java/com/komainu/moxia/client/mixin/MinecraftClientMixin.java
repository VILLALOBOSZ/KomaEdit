package com.komainu.moxia.client.mixin;

import com.komainu.moxia.client.capability.CapabilityControls;
import com.komainu.moxia.client.gizmo.GhostClient;
import com.komainu.moxia.client.reference.ReferenceClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clic derecho de las capacidades: si es de una capacidad, no hace lo de Minecraft (lo manda
 * {@link CapabilityControls}); con «Colocación rápida» quita la espera de 4 ticks entre bloques. Con el fantasma del
 * gizmo en pantalla, el clic derecho lo coloca ({@link GhostClient}); editando una imagen de referencia, termina
 * ({@link ReferenceClient}).
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow
    private int itemUseCooldown;

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void komaedit$capabilityUse(CallbackInfo callbackInfo) {
        // Con el fantasma del gizmo en pantalla, el clic derecho lo coloca.
        if (GhostClient.onUse((MinecraftClient) (Object) this)) {
            itemUseCooldown = 4;
            callbackInfo.cancel();
            return;
        }
        if (ReferenceClient.onUse((MinecraftClient) (Object) this)) {
            itemUseCooldown = 4;
            callbackInfo.cancel();
            return;
        }
        if (CapabilityControls.onUse((MinecraftClient) (Object) this)) {
            itemUseCooldown = CapabilityControls.useCooldown();
            callbackInfo.cancel();
        }
    }

    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void komaedit$fastPlace(CallbackInfo callbackInfo) {
        if (CapabilityControls.fastPlace((MinecraftClient) (Object) this)) {
            itemUseCooldown = 0;
        }
    }
}
