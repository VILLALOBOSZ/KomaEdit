package com.komainu.moxia.client.preview;

import com.komainu.moxia.path.PathHologramPayload;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.Vec3;
import com.sk89q.worldedit.math.BlockVector3;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class PathHologramRenderer {
    private static final int MAX_CURVE_SEGMENTS = 2_048;
    private static volatile RenderState state = RenderState.clear();

    private PathHologramRenderer() {
    }

    public static void update(PathHologramPayload payload) {
        state = new RenderState(payload, sampleCurve(payload));
    }

    public static void moveLocal(int index, BlockPos position) {
        RenderState current = state;
        if (!current.payload().active() || index < 0 || index >= current.payload().points().size()) {
            return;
        }
        List<BlockPos> points = new ArrayList<>(current.payload().points());
        points.set(index, position.toImmutable());
        PathHologramPayload payload = new PathHologramPayload(true, current.payload().closed(), points);
        state = new RenderState(payload, sampleCurve(payload));
    }

    public static void clear() {
        state = RenderState.clear();
    }

    public static List<BlockPos> points() {
        return state.payload().points();
    }

    public static boolean closed() {
        return state.payload().closed();
    }

    public static void render(WorldRenderContext context) {
        RenderState current = state;
        MatrixStack matrices = context.matrixStack();
        if (!current.payload().active() || matrices == null || context.consumers() == null) {
            return;
        }

        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        drawNodes(matrices, lines, current.payload().points());
        drawCurve(matrices, lines, current.curve());
        matrices.pop();
    }

    private static void drawNodes(MatrixStack matrices, VertexConsumer lines, List<BlockPos> points) {
        for (int index = 0; index < points.size(); index++) {
            BlockPos point = points.get(index);
            double centerX = point.getX() + 0.5;
            double centerY = point.getY() + 0.5;
            double centerZ = point.getZ() + 0.5;
            float red = index == points.size() - 1 ? 1.0F : 0.35F;
            float green = index == points.size() - 1 ? 0.72F : 0.90F;
            float blue = index == points.size() - 1 ? 0.20F : 1.0F;
            WorldRenderer.drawBox(
                    matrices,
                    lines,
                    new Box(
                            centerX - 0.24, centerY - 0.24, centerZ - 0.24,
                            centerX + 0.24, centerY + 0.24, centerZ + 0.24
                    ),
                    red,
                    green,
                    blue,
                    1.0F
            );
        }
    }

    private static List<Vec3> sampleCurve(PathHologramPayload payload) {
        if (payload.points().size() < 2) {
            return List.of();
        }
        List<BlockVector3> controls = payload.points().stream()
                .map(point -> BlockVector3.at(point.getX(), point.getY(), point.getZ()))
                .toList();
        int minimum = payload.closed() ? 3 : 2;
        if (controls.size() < minimum) {
            return centeredControls(payload.points());
        }
        try {
            SampledPath path = SampledPath.createSmooth(controls, payload.closed());
            int segments = Math.clamp((int) Math.ceil(path.length() * 1.5), 1, MAX_CURVE_SEGMENTS);
            List<Vec3> curve = new ArrayList<>(segments + 1);
            for (int segment = 0; segment <= segments; segment++) {
                Vec3 point = path.at(path.length() * segment / segments).position();
                curve.add(new Vec3(point.x() + 0.5, point.y() + 0.5, point.z() + 0.5));
            }
            return List.copyOf(curve);
        } catch (IllegalArgumentException exception) {
            return centeredControls(payload.points());
        }
    }

    private static List<Vec3> centeredControls(List<BlockPos> points) {
        return points.stream()
                .map(point -> new Vec3(point.getX() + 0.5, point.getY() + 0.5, point.getZ() + 0.5))
                .toList();
    }

    private static void drawCurve(MatrixStack matrices, VertexConsumer lines, List<Vec3> curve) {
        for (int index = 1; index < curve.size(); index++) {
            drawLine(matrices, lines, curve.get(index - 1), curve.get(index));
        }
    }

    private static void drawLine(MatrixStack matrices, VertexConsumer lines, Vec3 start, Vec3 end) {
        Vec3d direction = new Vec3d(
                end.x() - start.x(),
                end.y() - start.y(),
                end.z() - start.z()
        ).normalize();
        Vec3d normal = direction.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        if (normal.lengthSquared() < 1.0E-6) {
            normal = direction.crossProduct(new Vec3d(1.0, 0.0, 0.0));
        }
        normal = normal.normalize();
        MatrixStack.Entry entry = matrices.peek();
        lines.vertex(entry, (float) start.x(), (float) start.y(), (float) start.z())
                .color(0.20F, 0.88F, 1.0F, 0.95F)
                .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
        lines.vertex(entry, (float) end.x(), (float) end.y(), (float) end.z())
                .color(0.20F, 0.88F, 1.0F, 0.95F)
                .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private record RenderState(PathHologramPayload payload, List<Vec3> curve) {
        private static RenderState clear() {
            return new RenderState(PathHologramPayload.clear(), List.of());
        }
    }
}
