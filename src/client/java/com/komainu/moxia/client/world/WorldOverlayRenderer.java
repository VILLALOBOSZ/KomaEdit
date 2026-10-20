package com.komainu.moxia.client.world;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.brush.BrushTool;
import com.komainu.moxia.feedback.SelectionPayload;
import com.komainu.moxia.feedback.SymmetryGuidePayload;
import com.komainu.moxia.feedback.WorldFeedbackSettings;
import com.komainu.moxia.preview.PreviewPlanCodec;
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
 *     <li>La selección de WorldEdit con sus medidas (y la caja de la vista previa con las suyas).</li>
 *     <li>El radio del pincel en la mano, sobre la superficie que se mira, como en Axiom.</li>
 *     <li>Los planos y el eje de la simetría: un marco alrededor del jugador con una rejilla tenue, sin partículas.</li>
 * </ul>
 */
public final class WorldOverlayRenderer {
    private static final int SELECTION = 0xE6F2F2FF;
    private static final int PREVIEW_BOX = 0xF0FFD84A;
    private static final int RETICLE = 0xD9FFFFFF;
    private static final int PLANE_X = 0xEF5350;
    private static final int PLANE_Z = 0x42A5F5;
    private static final int PLANE_Y = 0x66BB6A;
    private static final int AXIS = 0xFFCA28;
    private static final double BRUSH_REACH = 192.0;
    /** Medio lado del marco de cada plano de simetría. */
    private static final int PLANE_RADIUS = 12;
    private static final Set<String> SHAPES = Set.of("poly2d", "ellipsoid", "cylinder",
            "convex");

    private static volatile SelectionPayload selection = SelectionPayload.NONE;
    private static volatile SymmetryGuidePayload symmetry = SymmetryGuidePayload.NONE;
    private static boolean broken;

    private WorldOverlayRenderer() {
    }

    public static void selection(SelectionPayload payload) {
        selection = payload;
    }

    public static void symmetry(SymmetryGuidePayload payload) {
        symmetry = payload;
    }

    public static void clear() {
        selection = SelectionPayload.NONE;
        symmetry = SymmetryGuidePayload.NONE;
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
        if (settings.selection() && currentSelection.visible()) {
            Box box = new Box(currentSelection.minX(), currentSelection.minY(), currentSelection.minZ(),
                    currentSelection.maxX() + 1.0, currentSelection.maxY() + 1.0, currentSelection.maxZ() + 1.0)
                    .expand(0.004);
            box(matrices, lines, box, SELECTION);
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
                    if (SHAPES.contains(currentSelection.shape())) {
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

    private static void box(MatrixStack matrices, VertexConsumer lines, Box box, int argb) {
        WorldRenderer.drawBox(matrices, lines, box, (argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
    }

    // ---------------------------------------------------------------- pincel

    /** El radio del pincel en la mano sobre la cara que se mira, más una cruz pequeña en el centro. */
    private static void reticle(WorldRenderContext context, MatrixStack matrices, VertexConsumer lines) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null) {
            return;
        }
        int radius = BrushTool.radius(client.player.getMainHandStack());
        if (radius <= 0) {
            return;
        }
        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        float delta = context.tickCounter().getTickDelta(true);
        HitResult hit = camera.raycast(BRUSH_REACH, delta, false);
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
