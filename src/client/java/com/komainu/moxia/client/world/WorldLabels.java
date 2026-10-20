package com.komainu.moxia.client.world;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Textos y líneas pequeñas en el mundo: medidas de una caja y números de los puntos. Los textos miran siempre a la
 * cámara, tienen el mismo tamaño en pantalla a cualquier distancia y se ven a través de los bloques con un fondo
 * suave, como las medidas de Axiom.
 */
public final class WorldLabels {
    private static final int BACKGROUND = 0x66000000;
    private static final double MAX_DISTANCE = 160.0;

    private WorldLabels() {
    }

    /** Un texto centrado en {@code (x, y, z)}; la matriz de {@code context} sin desplazar a la cámara. */
    public static void draw(WorldRenderContext context, String text, double x, double y, double z, int color) {
        MatrixStack matrices = context.matrixStack();
        if (matrices == null || context.consumers() == null || text.isEmpty()) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        double distance = camera.distanceTo(new Vec3d(x, y, z));
        if (distance > MAX_DISTANCE) {
            return;
        }
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        float scale = (float) (0.022 * Math.clamp(distance / 9.0, 0.6, 8.0));
        matrices.push();
        matrices.translate(x - camera.x, y - camera.y, z - camera.z);
        matrices.multiply(context.camera().getRotation());
        matrices.scale(scale, -scale, scale);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float left = -font.getWidth(text) / 2.0F;
        font.draw(text, left, -4.0F, 0xFF000000 | color, false, matrix, context.consumers(),
                TextRenderer.TextLayerType.SEE_THROUGH, BACKGROUND, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }

    /**
     * Las medidas de {@code box} (en bloques: ancho×alto×largo) en las tres aristas más cercanas a la cámara, una por
     * eje. La matriz de {@code context} sin desplazar a la cámara.
     */
    public static void dimensions(WorldRenderContext context, Box box, int color) {
        Vec3d camera = context.camera().getPos();
        double nearX = Math.abs(camera.x - box.minX) < Math.abs(camera.x - box.maxX) ? box.minX : box.maxX;
        double nearY = Math.abs(camera.y - box.minY) < Math.abs(camera.y - box.maxY) ? box.minY : box.maxY;
        double nearZ = Math.abs(camera.z - box.minZ) < Math.abs(camera.z - box.maxZ) ? box.minZ : box.maxZ;
        int sizeX = (int) Math.round(box.maxX - box.minX);
        int sizeY = (int) Math.round(box.maxY - box.minY);
        int sizeZ = (int) Math.round(box.maxZ - box.minZ);
        draw(context, Integer.toString(sizeX), (box.minX + box.maxX) / 2.0, nearY, nearZ, color);
        if (sizeY > 1 || sizeX > 1 || sizeZ > 1) {
            draw(context, Integer.toString(sizeY), nearX, (box.minY + box.maxY) / 2.0, nearZ, color);
        }
        draw(context, Integer.toString(sizeZ), nearX, nearY, (box.minZ + box.maxZ) / 2.0, color);
    }

    /** Una línea con la normal bien puesta (la dirección de la línea, como pide el shader de líneas). */
    public static void line(MatrixStack matrices, VertexConsumer lines, Vec3d start, Vec3d end, int argb) {
        Vec3d direction = end.subtract(start);
        double length = direction.length();
        if (length < 1.0E-6) {
            return;
        }
        direction = direction.multiply(1.0 / length);
        float alpha = (argb >>> 24) / 255.0F;
        float red = (argb >> 16 & 0xFF) / 255.0F;
        float green = (argb >> 8 & 0xFF) / 255.0F;
        float blue = (argb & 0xFF) / 255.0F;
        MatrixStack.Entry entry = matrices.peek();
        lines.vertex(entry, (float) start.x, (float) start.y, (float) start.z)
                .color(red, green, blue, alpha)
                .normal(entry, (float) direction.x, (float) direction.y, (float) direction.z);
        lines.vertex(entry, (float) end.x, (float) end.y, (float) end.z)
                .color(red, green, blue, alpha)
                .normal(entry, (float) direction.x, (float) direction.y, (float) direction.z);
    }
}
