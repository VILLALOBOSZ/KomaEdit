package com.komainu.moxia.client.world;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.brush.BrushTool;
import com.komainu.moxia.feedback.SelectionPayload;
import com.komainu.moxia.feedback.SymmetryGuidePayload;
import com.komainu.moxia.feedback.WorldFeedbackSettings;
import com.komainu.moxia.preview.PreviewOutline;
import com.komainu.moxia.preview.PreviewPlanCodec;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.selection.SelectionPayloads;
import com.komainu.moxia.studio.ui.StudioTheme;
import com.komainu.moxia.studio.world.BoxSelect;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

/**
 * Lo que KomaEdit dibuja en el mundo además de la vista previa, todo con líneas finas y apagable en los ajustes:
 *
 * <ul>
 *     <li>La selección de WorldEdit con sus medidas (y la caja de la vista previa con las suyas). La de bloques
 *     sueltos (lazo y pincel de selección), con su contorno y su número de bloques.</li>
 *     <li>El radio del pincel en la mano, sobre la superficie que se mira, como en Axiom.</li>
 *     <li>Los planos y el eje de la simetría: un marco alrededor del jugador con una rejilla tenue, sin partículas.</li>
 * </ul>
 */
public final class WorldOverlayRenderer {
    // Los colores del mundo están con las demás fichas, en StudioTheme (no cambian con el tema).
    private static final int SELECTION = StudioTheme.WORLD_SELECTION;
    private static final int PREVIEW_BOX = StudioTheme.WORLD_PREVIEW_BOX;
    private static final int RETICLE = StudioTheme.WORLD_RETICLE;
    private static final int FACE_HOVER = StudioTheme.WORLD_FACE_HOVER;
    private static final int FACE_GRABBED = StudioTheme.WORLD_FACE_GRABBED;
    private static final int PLANE_X = StudioTheme.WORLD_PLANE_X;
    private static final int PLANE_Z = StudioTheme.WORLD_PLANE_Z;
    private static final int PLANE_Y = StudioTheme.WORLD_PLANE_Y;
    private static final int AXIS = StudioTheme.WORLD_AXIS;
    private static final double BRUSH_REACH = 192.0;
    /** Medio lado del marco de cada plano de simetría. */
    private static final int PLANE_RADIUS = 12;
    private static final Set<String> SHAPES = Set.of("poly2d", "ellipsoid", "cylinder",
            "convex");

    private static volatile SelectionPayload selection = SelectionPayload.NONE;
    private static volatile SymmetryGuidePayload symmetry = SymmetryGuidePayload.NONE;
    /** Con el Studio abierto: la cara de la selección bajo el cursor (o agarrada) y si se está tirando de ella. */
    private static volatile BoxSelect.Face face;
    private static volatile boolean faceGrabbed;
    /** Con el Studio abierto: el bloque al que apunta el cursor, para el círculo del pincel. */
    private static volatile BlockHitResult studioCursor;
    /** Con el pincel de selección del Studio: su radio (si no, -1 y vale el del pincel de la mano). */
    private static volatile int selectionRadius = -1;
    /** El contorno de la selección de bloques sueltos y sus aristas ya en coordenadas del mundo (x1 y1 z1 x2 y2 z2). */
    private static volatile SelectionPayloads.Outline outline = SelectionPayloads.Outline.NONE;
    private static volatile float[] outlineLines = new float[0];
    private static boolean broken;

    private WorldOverlayRenderer() {
    }

    public static void selection(SelectionPayload payload) {
        selection = payload;
    }

    /** La selección que se está dibujando (la del servidor o, mientras se arrastra en el Studio, la de ahora). */
    public static SelectionPayload selection() {
        return selection;
    }

    /** La cara de la selección que se resalta (bajo el cursor del Studio o agarrada), o {@code null}. */
    public static void face(BoxSelect.Face value, boolean grabbed) {
        face = value;
        faceGrabbed = grabbed;
    }

    /** El bloque al que apunta el cursor del Studio (para el círculo del pincel), o {@code null} fuera de él. */
    public static void studioCursor(BlockHitResult hit) {
        studioCursor = hit;
    }

    /** El radio del pincel de selección del Studio para el círculo bajo el cursor, o -1 para el de la mano. */
    public static void selectionRadius(int radius) {
        selectionRadius = radius;
    }

    public static void symmetry(SymmetryGuidePayload payload) {
        symmetry = payload;
    }

    /** El contorno de la selección de bloques sueltos (llega cuando cambia). */
    public static void outline(SelectionPayloads.Outline payload) {
        int count = 0;
        for (PreviewOutline.Section section : payload.sections()) {
            count += section.edges().length;
        }
        float[] lines = new float[count * 6];
        int index = 0;
        for (PreviewOutline.Section section : payload.sections()) {
            for (int edge : section.edges()) {
                float x = section.x() * 16 + PreviewOutline.localX(edge);
                float y = section.y() * 16 + PreviewOutline.localY(edge);
                float z = section.z() * 16 + PreviewOutline.localZ(edge);
                int axis = PreviewOutline.axis(edge);
                int length = PreviewOutline.length(edge);
                lines[index++] = x;
                lines[index++] = y;
                lines[index++] = z;
                lines[index++] = x + (axis == 0 ? length : 0);
                lines[index++] = y + (axis == 1 ? length : 0);
                lines[index++] = z + (axis == 2 ? length : 0);
            }
        }
        outlineLines = lines;
        outline = payload;
    }

    public static void clear() {
        selection = SelectionPayload.NONE;
        symmetry = SymmetryGuidePayload.NONE;
        outline = SelectionPayloads.Outline.NONE;
        outlineLines = new float[0];
    }

    public static void render(WorldRenderContext context) {
        MatrixStack matrices = context.matrixStack();
        if (broken || matrices == null || context.consumers() == null) {
            return;
        }
        try {
            draw(context, matrices);
        } catch (RuntimeException exception) {
            broken = true;
            KomaEditMod.LOGGER.error("KomaEdit world overlay failed; hiding it", exception);
        }
    }

    private static void draw(WorldRenderContext context, MatrixStack matrices) {
        WorldFeedbackSettings settings = ClientWorldSettings.get();
        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        SelectionPayload currentSelection = selection;
        SelectionPayloads.Outline blocks = blocks(currentSelection);
        if (settings.selection() && currentSelection.visible()) {
            Box box = new Box(currentSelection.minX(), currentSelection.minY(), currentSelection.minZ(),
                    currentSelection.maxX() + 1.0, currentSelection.maxY() + 1.0, currentSelection.maxZ() + 1.0)
                    .expand(0.004);
            float[] edges = outlineLines;
            if (blocks != null && edges.length > 0) {
                // Bloques sueltos: su contorno, como las guías de la vista previa, en lugar de la caja.
                MatrixStack.Entry entry = matrices.peek();
                for (int index = 0; index + 5 < edges.length; index += 6) {
                    edge(entry, lines, edges, index, SELECTION);
                }
            } else {
                box(matrices, lines, box, SELECTION);
            }
            BoxSelect.Face highlighted = face;
            if (highlighted != null && currentSelection.complete() && blocks == null) {
                face(matrices, lines, box, highlighted, faceGrabbed ? FACE_GRABBED : FACE_HOVER);
            }
        }
        PreviewPlanCodec.Header preview = PlanPreviewRenderer.header().orElse(null);
        if (preview != null) {
            Box box = new Box(preview.minX(), preview.minY(), preview.minZ(), preview.maxX() + 1.0,
                    preview.maxY() + 1.0, preview.maxZ() + 1.0).expand(0.02);
            box(matrices, lines, box, PREVIEW_BOX);
        }
        if (settings.brushReticle()) {
            reticle(context, matrices, lines);
        }
        SymmetryGuidePayload currentSymmetry = symmetry;
        if (settings.symmetryGuides() && currentSymmetry.active()) {
            symmetry(matrices, lines, currentSymmetry, camera);
        }
        matrices.pop();

        // Los textos, ya sin el desplazamiento: WorldLabels pone el suyo.
        if (settings.dimensions()) {
            if (settings.selection() && currentSelection.visible()) {
                Box box = new Box(currentSelection.minX(), currentSelection.minY(), currentSelection.minZ(),
                        currentSelection.maxX() + 1.0, currentSelection.maxY() + 1.0, currentSelection.maxZ() + 1.0);
                if (currentSelection.complete()) {
                    WorldLabels.dimensions(context, box, SELECTION & 0xFFFFFF);
                    if (blocks != null) {
                        WorldLabels.draw(context, Text.translatable("gui.komaedit.world.blocks",
                                        ProgressLine.group(blocks.count())).getString(),
                                (box.minX + box.maxX) / 2.0, box.maxY + 0.6, (box.minZ + box.maxZ) / 2.0,
                                SELECTION & 0xFFFFFF);
                    } else if (SHAPES.contains(currentSelection.shape())) {
                        WorldLabels.draw(context, Text.translatable(
                                        "gui.komaedit.world.shape." + currentSelection.shape()).getString(),
                                (box.minX + box.maxX) / 2.0, box.maxY + 0.6, (box.minZ + box.maxZ) / 2.0,
                                SELECTION & 0xFFFFFF);
                    }
                } else {
                    WorldLabels.draw(context, "pos1", box.minX + 0.5, box.maxY + 0.4, box.minZ + 0.5,
                            SELECTION & 0xFFFFFF);
                }
            }
            if (preview != null) {
                WorldLabels.dimensions(context, new Box(preview.minX(), preview.minY(), preview.minZ(),
                        preview.maxX() + 1.0, preview.maxY() + 1.0, preview.maxZ() + 1.0), PREVIEW_BOX & 0xFFFFFF);
            }
        }
    }

    /** El contorno de bloques sueltos si es el de la selección que se dibuja (misma caja), o {@code null}. */
    private static SelectionPayloads.Outline blocks(SelectionPayload selection) {
        SelectionPayloads.Outline current = outline;
        if (!"blocks".equals(selection.shape()) || !current.visible() || current.minX() != selection.minX()
                || current.minY() != selection.minY() || current.minZ() != selection.minZ()
                || current.maxX() != selection.maxX() || current.maxY() != selection.maxY()
                || current.maxZ() != selection.maxZ()) {
            return null;
        }
        return current;
    }

    /** Una arista del contorno (seis números seguidos de {@code edges}) con su normal, sin crear vectores. */
    private static void edge(MatrixStack.Entry entry, VertexConsumer lines, float[] edges, int index, int argb) {
        float dx = edges[index + 3] - edges[index];
        float dy = edges[index + 4] - edges[index + 1];
        float dz = edges[index + 5] - edges[index + 2];
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6F) {
            return;
        }
        float alpha = (argb >>> 24) / 255.0F;
        float red = (argb >> 16 & 0xFF) / 255.0F;
        float green = (argb >> 8 & 0xFF) / 255.0F;
        float blue = (argb & 0xFF) / 255.0F;
        lines.vertex(entry, edges[index], edges[index + 1], edges[index + 2]).color(red, green, blue, alpha)
                .normal(entry, dx / length, dy / length, dz / length);
        lines.vertex(entry, edges[index + 3], edges[index + 4], edges[index + 5]).color(red, green, blue, alpha)
                .normal(entry, dx / length, dy / length, dz / length);
    }

    /** Una cara de la caja resaltada: su borde algo metido hacia dentro y una cruz, para que se vea qué se agarra. */
    private static void face(MatrixStack matrices, VertexConsumer lines, Box box, BoxSelect.Face side, int argb) {
        double inset = 0.12;
        double[] low = {box.minX, box.minY, box.minZ};
        double[] high = {box.maxX, box.maxY, box.maxZ};
        int axis = side.axis();
        double plane = side.sign() > 0 ? high[axis] : low[axis];
        int u = (axis + 1) % 3;
        int v = (axis + 2) % 3;
        Vec3d[] corners = new Vec3d[4];
        double[][] uv = {{low[u] + inset, low[v] + inset}, {high[u] - inset, low[v] + inset},
                {high[u] - inset, high[v] - inset}, {low[u] + inset, high[v] - inset}};
        for (int index = 0; index < 4; index++) {
            double[] point = new double[3];
            point[axis] = plane;
            point[u] = uv[index][0];
            point[v] = uv[index][1];
            corners[index] = new Vec3d(point[0], point[1], point[2]);
        }
        for (int index = 0; index < 4; index++) {
            WorldLabels.line(matrices, lines, corners[index], corners[(index + 1) % 4], argb);
        }
        WorldLabels.line(matrices, lines, corners[0], corners[2], argb);
        WorldLabels.line(matrices, lines, corners[1], corners[3], argb);
    }

    private static void box(MatrixStack matrices, VertexConsumer lines, Box box, int argb) {
        WorldRenderer.drawBox(matrices, lines, box, (argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
    }

    // ---------------------------------------------------------------- pincel

    /** El radio del pincel en la mano sobre la cara que se mira, más una cruz pequeña en el centro. */
    private static void reticle(WorldRenderContext context, MatrixStack matrices, VertexConsumer lines) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockHitResult cursor = studioCursor;
        if (client.player == null || client.currentScreen != null && cursor == null) {
            return;
        }
        int ownRadius = selectionRadius;
        int radius = cursor != null && ownRadius >= 0 ? ownRadius : BrushTool.radius(client.player.getMainHandStack());
        if (radius < 0 || radius == 0 && ownRadius < 0) {
            return;
        }
        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        float delta = context.tickCounter().getTickDelta(true);
        // Con el Studio abierto el pincel va donde apunta el cursor, no el centro de la pantalla.
        HitResult hit = client.currentScreen != null ? cursor : camera.raycast(BRUSH_REACH, delta, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Direction side = block.getSide();
        Vec3d normal = new Vec3d(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
        Vec3d center = Vec3d.ofCenter(block.getBlockPos()).add(normal.multiply(0.5 + 0.02));
        Vec3d first = Math.abs(normal.y) > 0.5 ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(0.0, 1.0, 0.0);
        Vec3d second = normal.crossProduct(first).normalize();
        int segments = Math.clamp(radius * 8, 32, 96);
        Vec3d previous = null;
        for (int segment = 0; segment <= segments; segment++) {
            double angle = Math.PI * 2.0 * segment / segments;
            Vec3d point = center.add(first.multiply(Math.cos(angle) * radius))
                    .add(second.multiply(Math.sin(angle) * radius));
            if (previous != null) {
                WorldLabels.line(matrices, lines, previous, point, RETICLE);
            }
            previous = point;
        }
        double cross = 0.35;
        WorldLabels.line(matrices, lines, center.subtract(first.multiply(cross)), center.add(first.multiply(cross)),
                RETICLE);
        WorldLabels.line(matrices, lines, center.subtract(second.multiply(cross)), center.add(second.multiply(cross)),
                RETICLE);
    }

    // ---------------------------------------------------------------- simetría

    /**
     * Cada plano, como un marco de {@code 2·PLANE_RADIUS} alrededor del jugador con líneas tenues cada 4 bloques; el
     * eje vertical en amarillo y, en el radial, un rayo por brazo (el primero en verde).
     */
    private static void symmetry(MatrixStack matrices, VertexConsumer lines, SymmetryGuidePayload guide,
                                 Vec3d camera) {
        double cx = guide.centerX();
        double cy = guide.centerY();
        double cz = guide.centerZ();
        // La rejilla va pegada a múltiplos de 4: al andar salta de 4 en 4 en lugar de arrastrarse con el jugador.
        double baseX = Math.floor(camera.x / 4.0) * 4.0;
        double baseY = Math.floor(camera.y / 4.0) * 4.0;
        double baseZ = Math.floor(camera.z / 4.0) * 4.0;
        int r = PLANE_RADIUS;
        if (guide.mirrorX()) {
            plane(matrices, lines, PLANE_X, (u, v) -> new Vec3d(cx, baseY + v, baseZ + u), r);
        }
        if (guide.mirrorZ()) {
            plane(matrices, lines, PLANE_Z, (u, v) -> new Vec3d(baseX + u, baseY + v, cz), r);
        }
        if (guide.mirrorY()) {
            plane(matrices, lines, PLANE_Y, (u, v) -> new Vec3d(baseX + u, cy, baseZ + v), r);
        }
        boolean axis = guide.radial() || guide.mirrorX() && guide.mirrorZ();
        if (axis) {
            WorldLabels.line(matrices, lines, new Vec3d(cx, baseY - r, cz), new Vec3d(cx, baseY + r, cz),
                    0xF0000000 | AXIS);
            if (guide.radial() && guide.arms() > 0) {
                double y = Math.floor(camera.y - 1.0) + 0.05;
                for (int arm = 0; arm < guide.arms(); arm++) {
                    double angle = 2.0 * Math.PI * arm / guide.arms();
                    int color = arm == 0 ? PLANE_Y : AXIS;
                    WorldLabels.line(matrices, lines, new Vec3d(cx, y, cz),
                            new Vec3d(cx + Math.cos(angle) * r, y, cz + Math.sin(angle) * r),
                            (arm == 0 ? 0xF0000000 : 0xA0000000) | color);
                }
            }
        }
    }

    @FunctionalInterface
    private interface PlanePoint {
        Vec3d at(double u, double v);
    }

    private static void plane(MatrixStack matrices, VertexConsumer lines, int rgb, PlanePoint point, int radius) {
        int edge = 0xE0000000 | rgb;
        int faint = 0x40000000 | rgb;
        WorldLabels.line(matrices, lines, point.at(-radius, -radius), point.at(radius, -radius), edge);
        WorldLabels.line(matrices, lines, point.at(radius, -radius), point.at(radius, radius), edge);
        WorldLabels.line(matrices, lines, point.at(radius, radius), point.at(-radius, radius), edge);
        WorldLabels.line(matrices, lines, point.at(-radius, radius), point.at(-radius, -radius), edge);
        for (int step = -radius + 4; step < radius; step += 4) {
            WorldLabels.line(matrices, lines, point.at(step, -radius), point.at(step, radius), faint);
            WorldLabels.line(matrices, lines, point.at(-radius, step), point.at(radius, step), faint);
        }
    }
}
