package com.komainu.moxia.client.mixin;

import com.komainu.moxia.client.EditorViewportState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void komaedit$applyEditorViewport(RenderTickCounter tickCounter, CallbackInfo callbackInfo) {
        if (EditorViewportState.active()) {
            RenderSystem.viewport(
                    EditorViewportState.x(),
                    0,
                    EditorViewportState.width(),
                    EditorViewportState.height()
            );
        }
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void komaedit$restoreFullViewport(RenderTickCounter tickCounter, CallbackInfo callbackInfo) {
        if (EditorViewportState.active()) {
            Window window = MinecraftClient.getInstance().getWindow();
            RenderSystem.viewport(0, 0, window.getFramebufferWidth(), window.getFramebufferHeight());
        }
    }

    @Redirect(
            method = "getBasicProjectionMatrix",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/Window;getFramebufferWidth()I"
            )
    )
    private int komaedit$useEditorViewportAspect(Window window) {
        return EditorViewportState.active() ? EditorViewportState.width() : window.getFramebufferWidth();
    }
}
