package com.komainu.moxia.client.preview;

import com.komainu.moxia.client.world.ClientWorldSettings;
import com.komainu.moxia.client.world.WorldLabels;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class EditorOverlayRenderer {
    public static final int PATH = 0;
    public static final int SHAPES = 1;
    public static final int BOX = 2;
    public static final int MAGIC = 3;
    public static final int PAINT = 4;
    public static final int GRADIENT = 5;

    private static volatile State state = State.clear();

    private EditorOverlayRenderer() {
    }

    public static void update(
            boolean active,
            int tool,
            BlockPos hover,
            int radius,
            BlockPos boxFirst,
            BlockPos boxSecond,
            int selectedPathPoint,
            BlockPos gizmoOrigin,
            int gizmoAxis
    ) {
        state = new State(active, tool, hover, radius, boxFirst, boxSecond, selectedPathPoint, gizmoOrigin, gizmoAxis);
    }

    public static void clear() {
        state = State.clear();
    }

    public static void render(WorldRenderContext context) {
        State current = state;
        MatrixStack matrices = context.matrixStack();
        if (!current.active() || matrices == null || context.consumers() == null) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

        if (current.hover() != null) {
            if (current.tool() == PAINT) {
                drawSphere(matrices, lines, Vec3d.ofCenter(current.hover()), current.radius());
            } else {
                float red = current.tool() == MAGIC ? 1.0F : 0.25F;
                float green = current.tool() == BOX ? 0.55F : 1.0F;
                float blue = current.tool() == PATH ? 1.0F : 0.35F;
                WorldRenderer.drawBox(matrices, lines, new Box(current.hover()).expand(0.035), red, green, blue, 1.0F);
            }
        }

        Box selection = null;
        if (current.boxFirst() != null) {
            BlockPos second = current.boxSecond() == null ? current.boxFirst() : current.boxSecond();
            selection = new Box(
                    Math.min(current.boxFirst().getX(), second.getX()),
                    Math.min(current.boxFirst().getY(), second.getY()),
                    Math.min(current.boxFirst().getZ(), second.getZ()),
                    Math.max(current.boxFirst().getX(), second.getX()) + 1.0,
                    Math.max(current.boxFirst().getY(), second.getY()) + 1.0,
                    Math.max(current.boxFirst().getZ(), second.getZ()) + 1.0
            ).expand(0.04);
            WorldRenderer.drawBox(matrices, lines, selection, 1.0F, 0.28F, 0.36F, 1.0F);
        }

        if (current.gizmoOrigin() != null) {
            drawGizmo(matrices, lines, current.gizmoOrigin(), current.gizmoAxis(), camera);
        }

        List<BlockPos> points = PathHologramRenderer.points();
        if (current.selectedPathPoint() >= 0 && current.selectedPathPoint() < points.size()) {
            BlockPos point = points.get(current.selectedPathPoint());
            WorldRenderer.drawBox(matrices, lines, new Box(point).expand(0.12), 1.0F, 0.35F, 0.12F, 1.0F);
        }
        matrices.pop();
        if (selection != null && ClientWorldSettings.get().dimensions()) {
            WorldLabels.dimensions(context, selection.contract(0.04), 0xFF7A8A);
        }
    }

    private static void drawSphere(MatrixStack matrices, VertexConsumer lines, Vec3d center, double radius) {
        int segments = 48;
        for (int plane = 0; plane < 3; plane++) {
            Vec3d previous = null;
            for (int segment = 0; segment <= segments; segment++) {
                double angle = Math.PI * 2.0 * segment / segments;
                double first = Math.cos(angle) * radius;
                double second = Math.sin(angle) * radius;
                Vec3d point = switch (plane) {
                    case 0 -> center.add(first, second, 0.0);
                    case 1 -> center.add(first, 0.0, second);
                    default -> center.add(0.0, first, second);
                };
                if (previous != null) {
                    drawLine(matrices, lines, previous, point);
                }
                previous = point;
            }
        }
    }

    private static void drawGizmo(
            MatrixStack matrices,
            VertexConsumer lines,
            BlockPos origin,
            int selectedAxis,
            Vec3d camera
    ) {
        Vec3d center = Vec3d.ofCenter(origin);
        double length = gizmoLength(center.distanceTo(camera));
        drawArrow(matrices, lines, center, new Vec3d(1.0, 0.0, 0.0), length,
                selectedAxis == 0 ? 1.0F : 0.88F, 0.12F, 0.16F);
        drawArrow(matrices, lines, center, new Vec3d(0.0, 1.0, 0.0), length,
                0.15F, selectedAxis == 1 ? 1.0F : 0.82F, 0.25F);
        drawArrow(matrices, lines, center, new Vec3d(0.0, 0.0, 1.0), length,
                0.18F, 0.45F, selectedAxis == 2 ? 1.0F : 0.92F);
        WorldRenderer.drawBox(
                matrices,
                lines,
                new Box(center.subtract(0.16, 0.16, 0.16), center.add(0.16, 0.16, 0.16)),
                0.95F,
                0.95F,
                0.95F,
                1.0F
        );
    }

    private static void drawArrow(
            MatrixStack matrices,
            VertexConsumer lines,
            Vec3d center,
            Vec3d axis,
            double length,
            float red,
            float green,
            float blue
    ) {
        Vec3d end = center.add(axis.multiply(length));
        drawLine(matrices, lines, center, end, red, green, blue);
        double head = Math.clamp(length * 0.18, 0.55, 2.2);
        Vec3d firstPerpendicular = Math.abs(axis.y) > 0.5
                ? new Vec3d(1.0, 0.0, 0.0)
                : new Vec3d(0.0, 1.0, 0.0);
        Vec3d secondPerpendicular = axis.crossProduct(firstPerpendicular).normalize();
        Vec3d back = end.subtract(axis.multiply(head));
        double spread = head * 0.42;
        drawLine(matrices, lines, end, back.add(firstPerpendicular.multiply(spread)), red, green, blue);
        drawLine(matrices, lines, end, back.subtract(firstPerpendicular.multiply(spread)), red, green, blue);
        drawLine(matrices, lines, end, back.add(secondPerpendicular.multiply(spread)), red, green, blue);
        drawLine(matrices, lines, end, back.subtract(secondPerpendicular.multiply(spread)), red, green, blue);
    }

    private static double gizmoLength(double cameraDistance) {
        return Math.clamp(cameraDistance * 0.075, 3.0, 18.0);
    }

    private static void drawLine(MatrixStack matrices, VertexConsumer lines, Vec3d start, Vec3d end) {
        drawLine(matrices, lines, start, end, 1.0F, 0.20F, 0.42F);
    }

    private static void drawLine(
            MatrixStack matrices,
            VertexConsumer lines,
            Vec3d start,
            Vec3d end,
            float red,
            float green,
            float blue
    ) {
        // La normal de una línea es su dirección: el shader de líneas la usa para el grosor en pantalla.
        int argb = 0xFF000000 | Math.round(red * 255.0F) << 16 | Math.round(green * 255.0F) << 8
                | Math.round(blue * 255.0F);
        WorldLabels.line(matrices, lines, start, end, argb);
    }

    private record State(
            boolean active,
            int tool,
            BlockPos hover,
            int radius,
            BlockPos boxFirst,
            BlockPos boxSecond,
            int selectedPathPoint,
            BlockPos gizmoOrigin,
            int gizmoAxis
    ) {
        private static State clear() {
            return new State(false, PATH, null, 1, null, null, -1, null, -1);
        }
    }
}
