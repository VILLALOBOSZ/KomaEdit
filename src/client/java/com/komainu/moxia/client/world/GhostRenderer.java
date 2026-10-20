package com.komainu.moxia.client.world;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.gizmo.GhostPlacement;
import com.komainu.moxia.gizmo.GhostStates;
import com.komainu.moxia.gizmo.GhostTransform;
import com.komainu.moxia.gizmo.Orientation;
import com.komainu.moxia.preview.PreviewPlanCodec;
import com.komainu.moxia.preview.PreviewSurface;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * El fantasma del gizmo: la misma malla que la vista previa ({@link PlanPreviewRenderer}), armada en posiciones
 * locales alrededor del pivote y dibujada donde esté ahora. Moverlo no rehace nada (solo cambia el ancla); al girarlo
 * o voltearlo la malla se rehace en otro hilo con los estados girados (escaleras, troncos…) y, mientras tanto, la
 * anterior se dibuja girada entera para que el giro se vea al momento.
 */
public final class GhostRenderer {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ExecutorService BUILDER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit-GhostMesh");
        thread.setDaemon(true);
        thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        return thread;
    });

    private static PreviewPlanCodec.Plan source;
    private static List<PlanPreviewRenderer.SectionMesh> sections = List.of();
    /** Orientación con la que está armada la malla que se dibuja, y la que se está armando. */
    private static Orientation meshed;
    private static Orientation building;
    private static boolean broken;

    private GhostRenderer() {
    }

    /** Los bloques de un fantasma nuevo (su superficie, en posiciones locales). */
    public static void load(PreviewPlanCodec.Plan plan, Orientation orientation, MinecraftClient client) {
        clear();
        source = plan;
        broken = false;
        rebuild(orientation, client);
    }

    public static boolean loaded() {
        return source != null;
    }

    /** Las posiciones locales recibidas (para la huella de «apoyar en el suelo»). */
    public static long[] positions() {
        return source == null ? new long[0] : source.positions();
    }

    /** Pide la malla de otra orientación si no es la que hay (ni la que se está armando). */
    public static void orient(Orientation orientation, MinecraftClient client) {
        if (source != null && orientation != building) {
            rebuild(orientation, client);
        }
    }

    public static void clear() {
        GENERATION.incrementAndGet();
        sections.forEach(PlanPreviewRenderer.SectionMesh::close);
        sections = List.of();
        source = null;
        meshed = null;
        building = null;
    }

    private static void rebuild(Orientation orientation, MinecraftClient client) {
        PreviewPlanCodec.Plan plan = source;
        long generation = GENERATION.incrementAndGet();
        building = orientation;
        BUILDER.execute(() -> {
            try {
                List<PlanPreviewRenderer.SectionData> data = PlanPreviewRenderer.mesh(oriented(plan, orientation),
                        client);
                client.execute(() -> {
                    if (generation == GENERATION.get()) {
                        sections.forEach(PlanPreviewRenderer.SectionMesh::close);
                        sections = data.stream().map(PlanPreviewRenderer.SectionMesh::new).toList();
                        meshed = orientation;
                    }
                });
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.warn("Could not build the KomaEdit ghost mesh", exception);
            }
        });
    }

    /** El plan con posiciones, estados y caras girados: lo mismo que colocará el servidor. */
    static PreviewPlanCodec.Plan oriented(PreviewPlanCodec.Plan plan, Orientation orientation) {
        long[] positions = GhostPlacement.orientPositions(plan.positions(), orientation);
        int[] states = GhostPlacement.orientStates(plan.states(), orientation, GhostStates.INSTANCE);
        byte[] faces = new byte[plan.faces().length];
        for (int index = 0; index < faces.length; index++) {
            int mask = plan.faces()[index];
            faces[index] = (byte) (orientation.faces(mask & PreviewSurface.ALL_FACES) | mask & ~PreviewSurface.ALL_FACES);
        }
        return new PreviewPlanCodec.Plan(plan.header(), positions, states, plan.kinds(), faces);
    }

    public static void render(WorldRenderContext context, GhostTransform transform) {
        if (sections.isEmpty() || broken || meshed == null) {
            return;
        }
        try {
            PlanPreviewRenderer.drawSections(context, sections, new PlanPreviewRenderer.Anchor(transform.x(),
                    transform.y(), transform.z(), rotation(meshed, transform.orientation())));
        } catch (RuntimeException exception) {
            broken = true;
            KomaEditMod.LOGGER.error("KomaEdit ghost rendering failed; hiding it", exception);
        }
    }

    /**
     * El giro rígido que lleva la malla armada con {@code meshed} a {@code current}, alrededor del centro del bloque
     * del pivote; {@code null} si ya coinciden.
     */
    static Matrix4f rotation(Orientation meshed, Orientation current) {
        if (meshed == current) {
            return null;
        }
        Orientation delta = meshed.inverse().then(current);
        int[] x = delta.apply(1, 0, 0);
        int[] y = delta.apply(0, 1, 0);
        int[] z = delta.apply(0, 0, 1);
        Matrix4f matrix = new Matrix4f(
                x[0], x[1], x[2], 0.0F,
                y[0], y[1], y[2], 0.0F,
                z[0], z[1], z[2], 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F);
        return new Matrix4f().translation(0.5F, 0.5F, 0.5F).mul(matrix).translate(-0.5F, -0.5F, -0.5F);
    }
}
