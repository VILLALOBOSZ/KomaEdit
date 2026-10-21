package com.komainu.moxia.client.world;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.client.reference.ReferenceTextures;
import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.reference.ReferenceGrid;
import com.komainu.moxia.reference.ReferenceImage;
import com.komainu.moxia.reference.ReferenceSet;
import com.komainu.moxia.reference.WorldFrame;
import com.komainu.moxia.shape.Vec3;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Las referencias en el mundo: cada una, un plano con su imagen, su opacidad y su filtrado, por las dos caras. Con
 * profundidad, lo que tenga delante la tapa (y no parpadea contra la cara de un bloque: va un pelo adelantada);
 * sin ella se dibuja encima de todo, después de las que la tienen. La rejilla va encima, con su contorno.
 */
public final class ReferenceWorldRenderer {
    /** Mientras no llega la imagen: un velo gris para ver dónde está. */
    private static final int PLACEHOLDER = 0x40C8C8C8;
    private static final float GRID_WIDTH = 1.5F;
    private static final float MAJOR_WIDTH = 3.0F;
    /** Lado mínimo de una celda en bloques para dibujar sus líneas. */
    private static final double MIN_CELL_BLOCKS = 1.0 / 32.0;
    private static boolean broken;

    private ReferenceWorldRenderer() {
    }

    public static void render(WorldRenderContext context, ReferenceSet set, String editing) {
        List<ReferenceImage> drawn = set.drawn(ReferenceImage.Mode.WORLD);
        if (broken || drawn.isEmpty()) {
            return;
        }
        try {
            for (ReferenceImage reference : drawn) {
                if (reference.world().depth()) {
                    draw(context, reference);
                }
            }
            for (ReferenceImage reference : drawn) {
                if (!reference.world().depth()) {
                    draw(context, reference);
                }
            }
        } catch (RuntimeException exception) {
            broken = true;
            KomaEditMod.LOGGER.error("KomaEdit reference image rendering failed; hiding them", exception);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.lineWidth(1.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void draw(WorldRenderContext context, ReferenceImage reference) {
        Vec3d camera = context.camera().getPos();
        Matrix4f view = new Matrix4f(context.positionMatrix());
        Matrix4f projection = context.projectionMatrix();
        WorldFrame frame = reference.world();
        Vec3[] corners = frame.corners(reference.pixelWidth(), reference.pixelHeight());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        if (frame.depth()) {
            RenderSystem.enableDepthTest();
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-1.0F, -10.0F);
        } else {
            RenderSystem.disableDepthTest();
        }
        Optional<ReferenceTextures.Texture> texture = ReferenceTextures.full(reference.file());
        ShaderProgram textured = GameRenderer.getPositionTexColorProgram();
        if (texture.isPresent() && textured != null) {
            texture.get().filter(reference.smooth());
            RenderSystem.setShaderTexture(0, texture.get().id());
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) reference.opacity());
            float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            int[] order = {0, 1, 2, 0, 2, 3};
            GizmoRenderer.draw(view, projection, textured, VertexFormat.DrawMode.TRIANGLES,
                    VertexFormats.POSITION_TEXTURE_COLOR, 2, builder -> {
                        for (int index : order) {
                            Vec3 corner = corners[index];
                            builder.vertex((float) (corner.x() - camera.x), (float) (corner.y() - camera.y),
                                            (float) (corner.z() - camera.z))
                                    .texture(uv[index][0], uv[index][1])
                                    .color(255, 255, 255, 255);
                        }
                    });
        } else {
            ShaderProgram colored = GameRenderer.getPositionColorProgram();
            if (colored != null) {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                int[] order = {0, 1, 2, 0, 2, 3};
                GizmoRenderer.draw(view, projection, colored, VertexFormat.DrawMode.TRIANGLES,
                        VertexFormats.POSITION_COLOR, 2, builder -> {
                            for (int index : order) {
                                Vec3 corner = corners[index];
                                builder.vertex((float) (corner.x() - camera.x), (float) (corner.y() - camera.y),
                                                (float) (corner.z() - camera.z))
                                        .color(PLACEHOLDER >> 16 & 0xFF, PLACEHOLDER >> 8 & 0xFF, PLACEHOLDER & 0xFF,
                                                PLACEHOLDER >>> 24);
                            }
                        });
            }
        }
        RenderSystem.disablePolygonOffset();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        grid(view, projection, camera, reference, corners);
        VertexBuffer.unbind();
    }

    private static void grid(Matrix4f view, Matrix4f projection, Vec3d camera, ReferenceImage reference,
                             Vec3[] corners) {
        ReferenceGrid grid = reference.grid();
        ShaderProgram lines = GameRenderer.getRenderTypeLinesProgram();
        if (!grid.enabled() || lines == null) {
            return;
        }
        WorldFrame frame = reference.world();
        int width = reference.pixelWidth();
        int height = reference.pixelHeight();
        boolean minor = grid.visibleAt(1.0 / frame.pixelsPerBlock(), MIN_CELL_BLOCKS);
        int color = grid.color().rgb();
        List<GizmoGeometry.Line> thin = new ArrayList<>();
        List<GizmoGeometry.Line> bold = new ArrayList<>();
        for (int index = 0; index < corners.length; index++) {
            thin.add(new GizmoGeometry.Line(corners[index], corners[(index + 1) % corners.length],
                    0xC0 << 24 | color, GRID_WIDTH));
        }
        for (ReferenceGrid.Line line : grid.lines(width, height)) {
            if (!line.major() && !minor) {
                continue;
            }
            Vec3 from = line.vertical() ? frame.point(line.at(), 0, width, height)
                    : frame.point(0, line.at(), width, height);
            Vec3 to = line.vertical() ? frame.point(line.at(), height, width, height)
                    : frame.point(width, line.at(), width, height);
            (line.major() ? bold : thin).add(new GizmoGeometry.Line(from, to,
                    (line.major() ? 0xF0 : 0xA0) << 24 | color, line.major() ? MAJOR_WIDTH : GRID_WIDTH));
        }
        float scale = Math.max(1.0F, MinecraftClient.getInstance().getWindow().getFramebufferWidth() / 1920.0F);
        for (List<GizmoGeometry.Line> batch : List.of(thin, bold)) {
            if (batch.isEmpty()) {
                continue;
            }
            RenderSystem.lineWidth(batch.getFirst().width() * scale);
            GizmoRenderer.draw(view, projection, lines, VertexFormat.DrawMode.LINES, VertexFormats.LINES,
                    batch.size(), builder -> {
                        for (GizmoGeometry.Line line : batch) {
                            GizmoRenderer.line(builder, camera, line);
                        }
                    });
        }
        RenderSystem.lineWidth(1.0F);
    }
}
