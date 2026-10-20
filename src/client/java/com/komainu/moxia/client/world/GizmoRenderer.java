package com.komainu.moxia.client.world;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.shape.Vec3;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Consumer;

/**
 * Dibuja el gizmo ({@link GizmoGeometry}) encima de todo, sin prueba de profundidad: se ve aunque esté dentro del
 * objeto o detrás de una pared, y es fino y algo translúcido para no taparlo. Su tamaño en el mundo ya viene
 * calculado para ocupar siempre lo mismo en pantalla.
 */
public final class GizmoRenderer {
    /** Los colores ya traen su alfa ({@link GizmoGeometry}). */
    private static final float ALPHA = 1.0F;
    private static boolean broken;

    private GizmoRenderer() {
    }

    public static void render(WorldRenderContext context, GizmoGeometry.Shape shape) {
        if (broken) {
            return;
        }
        try {
            draw(context, shape);
        } catch (RuntimeException exception) {
            broken = true;
            KomaEditMod.LOGGER.error("KomaEdit gizmo rendering failed; hiding it", exception);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.lineWidth(1.0F);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void draw(WorldRenderContext context, GizmoGeometry.Shape shape) {
        Vec3d camera = context.camera().getPos();
        Matrix4f view = new Matrix4f(context.positionMatrix());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, ALPHA);
        ShaderProgram lines = GameRenderer.getRenderTypeLinesProgram();
        if (lines != null) {
            float scale = Math.max(1.0F, MinecraftClient.getInstance().getWindow().getFramebufferWidth() / 1920.0F);
            for (float width : widths(shape.lines())) {
                RenderSystem.lineWidth(width * scale);
                draw(view, context.projectionMatrix(), lines, VertexFormat.DrawMode.LINES, VertexFormats.LINES,
                        shape.lines().size(), builder -> {
                            for (GizmoGeometry.Line line : shape.lines()) {
                                if (line.width() == width) {
                                    line(builder, camera, line);
                                }
                            }
                        });
            }
        }
        ShaderProgram colored = GameRenderer.getPositionColorProgram();
        if (colored != null && !shape.triangles().isEmpty()) {
            draw(view, context.projectionMatrix(), colored, VertexFormat.DrawMode.TRIANGLES,
                    VertexFormats.POSITION_COLOR, shape.triangles().size(), builder -> {
                        for (GizmoGeometry.Triangle triangle : shape.triangles()) {
                            vertex(builder, camera, triangle.a(), triangle.argb());
                            vertex(builder, camera, triangle.b(), triangle.argb());
                            vertex(builder, camera, triangle.c(), triangle.argb());
                        }
                    });
        }
        VertexBuffer.unbind();
    }

    /** Los grosores distintos de las líneas: cada uno va en su propia pasada. */
    private static float[] widths(List<GizmoGeometry.Line> lines) {
        float[] values = new float[lines.size()];
        int count = 0;
        outer:
        for (GizmoGeometry.Line line : lines) {
            for (int index = 0; index < count; index++) {
                if (values[index] == line.width()) {
                    continue outer;
                }
            }
            values[count++] = line.width();
        }
        return java.util.Arrays.copyOf(values, count);
    }

    private static void draw(Matrix4f view, Matrix4f projection, ShaderProgram program, VertexFormat.DrawMode mode,
                             VertexFormat format, int primitives, Consumer<BufferBuilder> emit) {
        int bytes = Math.max(1_024, primitives * 4 * format.getVertexSizeByte());
        try (BufferAllocator allocator = new BufferAllocator(bytes)) {
            BufferBuilder builder = new BufferBuilder(allocator, mode, format);
            emit.accept(builder);
            BuiltBuffer built = builder.endNullable();
            if (built == null) {
                return;
            }
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            try {
                buffer.bind();
                buffer.upload(built);
                buffer.draw(view, projection, program);
            } finally {
                VertexBuffer.unbind();
                buffer.close();
            }
        }
    }

    private static void line(BufferBuilder builder, Vec3d camera, GizmoGeometry.Line line) {
        Vec3 direction = line.to().subtract(line.from()).normalized();
        int argb = line.argb();
        builder.vertex((float) (line.from().x() - camera.x), (float) (line.from().y() - camera.y),
                        (float) (line.from().z() - camera.z))
                .color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24)
                .normal((float) direction.x(), (float) direction.y(), (float) direction.z());
        builder.vertex((float) (line.to().x() - camera.x), (float) (line.to().y() - camera.y),
                        (float) (line.to().z() - camera.z))
                .color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24)
                .normal((float) direction.x(), (float) direction.y(), (float) direction.z());
    }

    private static void vertex(BufferBuilder builder, Vec3d camera, Vec3 point, int argb) {
        builder.vertex((float) (point.x() - camera.x), (float) (point.y() - camera.y), (float) (point.z() - camera.z))
                .color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
    }
}
