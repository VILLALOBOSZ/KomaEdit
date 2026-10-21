package com.komainu.moxia.client.world;

import com.komainu.moxia.client.mixin.GameRendererAccessor;
import com.komainu.moxia.feedback.AlignmentGuidePayload;
import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.placement.AlignmentGuide;
import com.komainu.moxia.shape.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * El visualizador de la alineación del pincel de colocación en la mano ({@link AlignmentGuide}): las flechas que
 * calcula el servidor en el bloque que se mira, dibujadas como el gizmo (encima de todo y del mismo tamaño en
 * pantalla a cualquier distancia), con «primaria» y «secundaria» en sus puntas. Se apaga en {@code /kegui ajustes}.
 */
public final class AlignmentGuideRenderer {
    private static volatile AlignmentGuide.Result guide;

    private AlignmentGuideRenderer() {
    }

    public static void receive(AlignmentGuidePayload payload) {
        guide = payload.guide();
    }

    public static void clear() {
        guide = null;
    }

    /** Las flechas, al final del fotograma (como el gizmo). */
    public static void render(WorldRenderContext context) {
        AlignmentGuide.Result current = visible();
        if (current != null) {
            GizmoRenderer.render(context, AlignmentGuide.shape(current, length(context, current)));
        }
    }

    /** Los nombres de las flechas, con los demás textos del mundo. */
    public static void renderLabels(WorldRenderContext context) {
        AlignmentGuide.Result current = visible();
        if (current == null) {
            return;
        }
        for (AlignmentGuide.Label label : AlignmentGuide.labels(current, length(context, current))) {
            WorldLabels.draw(context, Text.translatable("gui.komaedit.guide." + label.key()).getString(),
                    label.at().x(), label.at().y(), label.at().z(), label.argb() & 0xFFFFFF);
        }
    }

    private static AlignmentGuide.Result visible() {
        AlignmentGuide.Result current = guide;
        MinecraftClient client = MinecraftClient.getInstance();
        if (current == null || client.player == null || !ClientWorldSettings.get().alignmentGuide()) {
            return null;
        }
        return current;
    }

    /** El largo de la flecha verde: siempre la misma parte de la pantalla, como el gizmo. */
    private static double length(WorldRenderContext context, AlignmentGuide.Result current) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = context.camera();
        Vec3d position = camera.getPos();
        double fov = ((GameRendererAccessor) client.gameRenderer).komaedit$getFov(camera,
                context.tickCounter().getTickDelta(true), true);
        return GizmoMath.screenSize(current.origin().distance(new Vec3(position.x, position.y, position.z)), fov,
                AlignmentGuide.SCREEN_FRACTION);
    }
}
