package com.komainu.moxia.client.preview;

import com.komainu.moxia.client.world.ClientWorldSettings;
import com.komainu.moxia.client.world.WorldLabels;
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
    /** Hasta tantos puntos, todos llevan su número. */
    private static final int MAX_NUMBERED = 40;
    private static final int FIRST = 0x66BB6A;
    private static final int LAST = 0xFFB833;
    private static final int NODE = 0x59E6FF;
    private static final int CURVE = 0xF233E0FF;
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
        List<BlockPos> points = current.payload().points();
        drawNodes(matrices, lines, points);
        drawCurve(matrices, lines, current.curve());
        matrices.pop();
        if (ClientWorldSettings.get().pathNumbers()) {
            drawNumbers(context, points);
        }
    }

    /** Verde el primero, naranja el último y azul el resto, para ver hacia dónde va. */
    private static int nodeColor(int index, int count) {
        if (index == 0) {
            return FIRST;
        }
        return index == count - 1 ? LAST : NODE;
    }

    private static void drawNodes(MatrixStack matrices, VertexConsumer lines, List<BlockPos> points) {
        for (int index = 0; index < points.size(); index++) {
            BlockPos point = points.get(index);
            double centerX = point.getX() + 0.5;
            double centerY = point.getY() + 0.5;
            double centerZ = point.getZ() + 0.5;
            int color = nodeColor(index, points.size());
            double size = index == 0 || index == points.size() - 1 ? 0.3 : 0.24;
            WorldRenderer.drawBox(
                    matrices,
                    lines,
                    new Box(
                            centerX - size, centerY - size, centerZ - size,
                            centerX + size, centerY + size, centerZ + size
                    ),
                    (color >> 16 & 0xFF) / 255.0F,
                    (color >> 8 & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F,
                    1.0F
            );
        }
    }

    /**
     * El número de cada punto encima de él. Con muchos puntos, solo el primero, el último y uno de cada cinco, para
     * no llenar la pantalla de números.
     */
    private static void drawNumbers(WorldRenderContext context, List<BlockPos> points) {
        int count = points.size();
        for (int index = 0; index < count; index++) {
            boolean shown = count <= MAX_NUMBERED || index == 0 || index == count - 1 || (index + 1) % 5 == 0;
            if (!shown) {
                continue;
            }
            BlockPos point = points.get(index);
            WorldLabels.draw(context, Integer.toString(index + 1), point.getX() + 0.5, point.getY() + 1.15,
                    point.getZ() + 0.5, nodeColor(index, count));
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

    /**
     * Un tramo de la curva. La normal de una línea es su dirección (el shader de líneas la usa para darle grosor en
     * pantalla); con una perpendicular, como antes, los tramos casi desaparecían según desde dónde se mirasen.
     */
    private static void drawLine(MatrixStack matrices, VertexConsumer lines, Vec3 start, Vec3 end) {
        WorldLabels.line(matrices, lines, new Vec3d(start.x(), start.y(), start.z()),
                new Vec3d(end.x(), end.y(), end.z()), CURVE);
    }

    private record RenderState(PathHologramPayload payload, List<Vec3> curve) {
        private static RenderState clear() {
            return new RenderState(PathHologramPayload.clear(), List.of());
        }
    }
}
