package com.komainu.moxia.client.preview;

import com.komainu.moxia.client.world.ClientWorldSettings;
import com.komainu.moxia.client.world.WorldLabels;
import com.komainu.moxia.preview.PreviewHologramPayload;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class PreviewHologramRenderer {
    private static volatile PreviewHologramPayload hologram = PreviewHologramPayload.clear();

    private PreviewHologramRenderer() {
    }

    public static void update(PreviewHologramPayload payload) {
        hologram = payload;
    }

    public static void clear() {
        hologram = PreviewHologramPayload.clear();
    }

    public static void render(WorldRenderContext context) {
        PreviewHologramPayload current = hologram;
        MatrixStack matrices = context.matrixStack();
        if (!current.visible() || matrices == null || context.consumers() == null) {
            return;
        }

        Vec3d camera = context.camera().getPos();
        int color = current.color();
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        Box bounds = new Box(
                current.minimumX() - 0.03,
                current.minimumY() - 0.03,
                current.minimumZ() - 0.03,
                current.maximumX() + 1.03,
                current.maximumY() + 1.03,
                current.maximumZ() + 1.03
        );

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        WorldRenderer.drawBox(matrices, lines, bounds, red, green, blue, 0.95F);
        matrices.pop();
        if (ClientWorldSettings.get().dimensions()) {
            WorldLabels.dimensions(context, bounds.contract(0.03), color & 0xFFFFFF);
        }
    }
}
