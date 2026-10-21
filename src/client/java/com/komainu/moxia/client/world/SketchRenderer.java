package com.komainu.moxia.client.world;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.feedback.WorldSketch;
import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.shape.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dibuja un {@link WorldSketch} (la regla, las notas): las líneas normales con prueba de profundidad, las que se ven a
 * través de los bloques con el dibujante del gizmo y los rótulos como las medidas de la selección, que miran a la
 * cámara y miden lo mismo en pantalla a cualquier distancia, con su icono de píxeles y un fondo suave.
 */
public final class SketchRenderer {
    /** Alto de un renglón en píxeles de la fuente. */
    private static final int ROW = 10;
    /** El icono se dibuja a este tamaño en píxeles de la fuente por píxel suyo. */
    private static final float ICON_PIXEL = 1.0F;
    private static final float GHOST_WIDTH = 2.0F;

    private SketchRenderer() {
    }

    /** Líneas con profundidad y rótulos: en {@code AFTER_ENTITIES}, con los buffers del mundo. */
    public static void drawWorld(WorldRenderContext context, WorldSketch sketch) {
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null || sketch.lines().isEmpty() && sketch.labels().isEmpty()) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        for (WorldSketch.Line line : sketch.lines()) {
            if (!line.seeThrough()) {
                WorldLabels.line(matrices, lines, vec(line.from()), vec(line.to()), line.argb());
            }
        }
        matrices.pop();
        // De lejos a cerca: el fondo de un rótulo cercano tapa al de detrás, como en los renders.
        List<WorldSketch.Label> labels = new ArrayList<>(sketch.labels());
        labels.sort(Comparator.comparingDouble((WorldSketch.Label label) -> -camera.squaredDistanceTo(
                label.at().x(), label.at().y(), label.at().z())));
        for (WorldSketch.Label label : labels) {
            label(context, matrices, consumers, label);
        }
    }

    /** Las líneas que se ven a través de los bloques: en {@code LAST}, encima de todo. */
    public static void drawGhost(WorldRenderContext context, WorldSketch sketch) {
        List<GizmoGeometry.Line> lines = new ArrayList<>();
        for (WorldSketch.Line line : sketch.lines()) {
            if (line.seeThrough()) {
                lines.add(new GizmoGeometry.Line(line.from(), line.to(), line.argb(), GHOST_WIDTH));
            }
        }
        if (!lines.isEmpty()) {
            GizmoRenderer.render(context, new GizmoGeometry.Shape(lines, List.of()));
        }
    }

    private static void label(WorldRenderContext context, MatrixStack matrices, VertexConsumerProvider consumers,
                              WorldSketch.Label label) {
        Vec3d camera = context.camera().getPos();
        Vec3 at = label.at();
        double distance = camera.distanceTo(vec(at));
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        float scale = (float) (0.022 * Math.clamp(distance / 9.0, 0.6, 8.0) * label.scale());
        int alpha = (int) Math.round(label.alpha() * 255.0);
        if (alpha < 8) {
            return;
        }
        WorldSketch.PixelIcon icon = label.icon();
        int iconWidth = icon == null ? 0 : Math.round(icon.width() * ICON_PIXEL) + 3;
        int width = 0;
        for (WorldSketch.Row row : label.rows()) {
            width = Math.max(width, font.getWidth(row.text()));
        }
        int height = label.rows().size() * ROW - 1;
        float left = -(width + iconWidth) / 2.0F;
        float top = -height / 2.0F;
        matrices.push();
        matrices.translate(at.x() - camera.x, at.y() - camera.y, at.z() - camera.z);
        matrices.multiply(context.camera().getRotation());
        matrices.scale(scale, -scale, scale);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        VertexConsumer quads = consumers.getBuffer(RenderLayer.getTextBackgroundSeeThrough());
        int background = label.backgroundAlpha() << 24;
        quad(quads, matrix, left - 2, top - 2, left + width + iconWidth + 2, top + height + 1, background, light);
        if (icon != null) {
            int color = alpha << 24 | label.iconRgb() & 0xFFFFFF;
            float iconTop = top + (ROW - 1 - icon.height() * ICON_PIXEL) / 2.0F;
            for (int y = 0; y < icon.height(); y++) {
                for (int x = 0; x < icon.width(); x++) {
                    if (icon.filled(x, y)) {
                        float px = left + x * ICON_PIXEL;
                        float py = iconTop + y * ICON_PIXEL;
                        quad(quads, matrix, px, py, px + ICON_PIXEL, py + ICON_PIXEL, color, light);
                    }
                }
            }
        }
        float textLeft = left + iconWidth;
        for (int index = 0; index < label.rows().size(); index++) {
            WorldSketch.Row row = label.rows().get(index);
            float x = textLeft + (width - font.getWidth(row.text())) / 2.0F;
            font.draw(row.text(), x, top + index * ROW, alpha << 24 | row.rgb() & 0xFFFFFF, false, matrix,
                    consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, light);
        }
        matrices.pop();
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1,
                             int argb, int light) {
        consumer.vertex(matrix, x0, y0, 0.01F).color(argb).light(light);
        consumer.vertex(matrix, x0, y1, 0.01F).color(argb).light(light);
        consumer.vertex(matrix, x1, y1, 0.01F).color(argb).light(light);
        consumer.vertex(matrix, x1, y0, 0.01F).color(argb).light(light);
    }

    private static Vec3d vec(Vec3 point) {
        return new Vec3d(point.x(), point.y(), point.z());
    }

    /** Dibuja sin cerrar el juego si algo falla: devuelve {@code false} para que quien llama lo oculte. */
    public static boolean safely(Runnable draw, String what) {
        try {
            draw.run();
            return true;
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.error("KomaEdit {} rendering failed; hiding it", what, exception);
            return false;
        }
    }
}
