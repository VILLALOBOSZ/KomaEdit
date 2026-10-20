package com.komainu.moxia.client.world;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.gizmo.GhostPlacement;
import com.komainu.moxia.gizmo.GhostStates;
import com.komainu.moxia.gizmo.GhostTransform;
import com.komainu.moxia.gizmo.MeshHandover;
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
 * anterior se dibuja girada entera para que el giro se vea al momento. Al rehacerse con la vista previa en vivo, la
 * malla de antes sigue a la vista, quieta donde estaba, hasta que la nueva está armada ({@link MeshHandover}): así no
 * parpadea.
 */
public final class GhostRenderer {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ExecutorService BUILDER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit-GhostMesh");
        thread.setDaemon(true);
        thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        return thread;
    });

    /** Una malla armada con la orientación {@code meshed}. */
    private record Mesh(List<PlanPreviewRenderer.SectionMesh> sections, Orientation meshed) {
        void close() {
            sections.forEach(PlanPreviewRenderer.SectionMesh::close);
        }
    }

    private static final MeshHandover<Mesh> MESHES = new MeshHandover<>(Mesh::close);
    private static PreviewPlanCodec.Plan source;
    /** Orientación con la que se está armando la malla. */
    private static Orientation building;
    /** Dónde estaba el fantasma de antes, para dibujar su malla quieta mientras llega la nueva. */
    private static GhostTransform retiredAt;
    private static boolean broken;

    private GhostRenderer() {
    }

    /** Los bloques de un fantasma nuevo (su superficie, en posiciones locales). */
    public static void load(PreviewPlanCodec.Plan plan, Orientation orientation, MinecraftClient client) {
        GENERATION.incrementAndGet();
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
        Mesh current = MESHES.current();
        if (source != null && orientation != building && (current == null || current.meshed() != orientation)) {
            rebuild(orientation, client);
        }
    }

    /**
     * El fantasma se va a rehacer con la vista previa en vivo: su malla sigue a la vista en {@code transform} hasta
     * que llegue la del nuevo.
     */
    public static void retire(GhostTransform transform) {
        GENERATION.incrementAndGet();
        MESHES.retire(System.currentTimeMillis());
        retiredAt = transform;
        source = null;
        building = null;
    }

    public static void clear() {
        GENERATION.incrementAndGet();
        MESHES.clear();
        source = null;
        building = null;
        retiredAt = null;
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
                        MESHES.install(new Mesh(data.stream().map(PlanPreviewRenderer.SectionMesh::new).toList(),
                                orientation));
                        building = null;
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
        if (broken) {
            return;
        }
        Mesh mesh = MESHES.current();
        GhostTransform at = transform;
        if (mesh == null) {
            // Mientras se arma la del fantasma nuevo, la de antes donde estaba.
            mesh = MESHES.previous(System.currentTimeMillis());
            at = retiredAt;
        }
        if (mesh == null || at == null || mesh.sections().isEmpty()) {
            return;
        }
        try {
            PlanPreviewRenderer.drawSections(context, mesh.sections(), new PlanPreviewRenderer.Anchor(at.x(),
                    at.y(), at.z(), rotation(mesh.meshed(), at.orientation())));
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
