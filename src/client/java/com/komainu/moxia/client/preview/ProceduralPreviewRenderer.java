package com.komainu.moxia.client.preview;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.recipe.RecipeGeometryEngine;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.shape.smooth.SmoothPieces;
import com.sk89q.worldedit.math.BlockVector3;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public final class ProceduralPreviewRenderer {
    private static final long DEBOUNCE_NANOS = 180_000_000L;
    private static final long MIN_RETRY_NANOS = 350_000_000L;
    private static final long MAX_RETRY_NANOS = 4_000_000_000L;
    private static final double RENDER_DISTANCE = 4_096.0;
    private static final double RENDER_DISTANCE_SQUARED = RENDER_DISTANCE * RENDER_DISTANCE;
    private static final int MAX_UPLOADS_PER_FRAME = 2;
    private static final int MAX_FACES_PER_FRAME = 40_000;
    private static final int[] OFFSETS_X = {-1, 1, 0, 0, 0, 0};
    private static final int[] OFFSETS_Y = {0, 0, -1, 1, 0, 0};
    private static final int[] OFFSETS_Z = {0, 0, 0, 0, -1, 1};
    /** Bit del octante en el eje de cada dirección (x = 1, y = 2, z = 4), en el orden de {@code OFFSETS_*}. */
    private static final int[] AXIS_BITS = {1, 1, 2, 2, 4, 4};
    private static final int OCTANT_FACE = 1 << 18;
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new PreviewThreadFactory());

    private static PreviewKey requestedKey;
    private static PreviewRequest pending;
    private static Future<?> running;
    private static long runningGeneration;
    private static long dueAt;
    private static PreviewKey failedKey;
    private static int failureCount;
    private static long retryNotBefore;
    private static RenderState state = RenderState.empty();
    /** Vacío hasta el primer cambio: se traduce al pedirlo, cuando el idioma ya está cargado. */
    private static String status = "";
    /** Oculto desde el Studio (tecla H): se sigue calculando para el recuento, pero no se dibuja. */
    private static boolean hidden;

    private ProceduralPreviewRenderer() {
    }

    public static void request(ProceduralRecipe recipe, List<BlockPos> points, boolean closed) {
        int minimum = closed ? 3 : 2;
        if (points.size() < minimum) {
            if (requestedKey != null || !state.sections().isEmpty()) {
                clearPreview(tr("gui.komaedit.hologram.waiting", minimum));
            }
            return;
        }
        List<BlockVector3> controls = points.stream()
                .map(point -> BlockVector3.at(point.getX(), point.getY(), point.getZ()))
                .toList();
        PathSnapshot path = new PathSnapshot(controls, closed, "preview-client");
        PreviewKey key = PreviewKey.create(recipe, points, closed);
        if (key.equals(requestedKey)) {
            return;
        }
        requestedKey = key;
        long generation = GENERATIONS.incrementAndGet();
        pending = new PreviewRequest(
                generation,
                key,
                recipe,
                path,
                recipe.extensions().surfaceMode() != com.komainu.moxia.recipe.SurfaceMode.NONE
                        || recipe.extensions().socketsEnabled()
        );
        long now = System.nanoTime();
        long retryAt = key.equals(failedKey) ? retryNotBefore : 0L;
        dueAt = Math.max(now + DEBOUNCE_NANOS, retryAt);
        if (running != null) {
            running.cancel(true);
        }
        status = tr("gui.komaedit.hologram.updating");
    }

    public static void tick(MinecraftClient client) {
        if (pending == null || System.nanoTime() < dueAt) {
            return;
        }
        if (running != null && !running.isDone()) {
            return;
        }
        PreviewRequest request = pending;
        pending = null;
        runningGeneration = request.generation();
        status = tr("gui.komaedit.hologram.computing");
        running = EXECUTOR.submit(() -> generate(client, request));
    }

    public static String status() {
        return status.isEmpty() ? tr("gui.komaedit.hologram.waiting", 2) : status;
    }

    public static boolean hidden() {
        return hidden;
    }

    public static void setHidden(boolean value) {
        hidden = value;
    }

    /** Bloques del último holograma calculado, o -1 si no hay; puede ir un paso por detrás del borrador. */
    public static int blockCount() {
        RenderState current = state;
        return current.sections().isEmpty() ? -1 : current.blockCount();
    }

    /** Si el holograma corresponde ya a lo último pedido (nada en cola ni calculándose). */
    public static boolean settled() {
        return pending == null && (running == null || running.isDone());
    }

    public static void clear() {
        clearPreview(tr("gui.komaedit.hologram.disabled"));
    }

    public static void render(WorldRenderContext context) {
        RenderState current = state;
        if (current.sections().isEmpty() || hidden) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        uploadNearestVisible(context, current, camera);
        ShaderProgram shader = GameRenderer.getPositionColorProgram();
        if (shader == null) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        for (SectionMesh section : current.sections()) {
            if (section.buffer() == null || !visible(context, section, camera)) {
                continue;
            }
            Matrix4f modelView = new Matrix4f(context.positionMatrix()).translate(
                    (float) (section.originX() - camera.x),
                    (float) (section.originY() - camera.y),
                    (float) (section.originZ() - camera.z)
            );
            section.buffer().bind();
            section.buffer().draw(modelView, context.projectionMatrix(), shader);
        }
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        if (current.uploadedSections() < current.sections().size()) {
            status = tr("gui.komaedit.hologram.uploading", current.blockCount(), current.uploadedSections(),
                    current.sections().size());
        } else if (current.serverAdjusted()) {
            status = tr("gui.komaedit.hologram.server_adjusted");
        } else {
            status = current.pieceSummary().isEmpty()
                    ? tr("gui.komaedit.hologram.exact", current.blockCount(), current.faceCount())
                    : tr("gui.komaedit.hologram.exact_pieces", current.blockCount(), current.pieceSummary(),
                            current.faceCount());
        }
    }

    private static void generate(MinecraftClient client, PreviewRequest request) {
        try {
            var result = RecipeGeometryEngine.generate(request.path(), request.recipe());
            checkCancelled(request);
            if (result.blocks().isEmpty()) {
                throw new IllegalStateException(tr("gui.komaedit.hologram.empty"));
            }
            PreparedPreview prepared = prepare(result.blocks(), result.pieces(), request);
            checkCancelled(request);
            client.execute(() -> install(request, prepared));
        } catch (CancellationException ignored) {
            finishCancelled(client, request);
        } catch (RuntimeException exception) {
            client.execute(() -> {
                if (request.generation() == GENERATIONS.get()) {
                    if (exception instanceof IllegalArgumentException) {
                        status = tr("gui.komaedit.hologram.pending", readable(exception));
                    } else {
                        recordFailure(request.key());
                        requestedKey = null;
                        status = tr("gui.komaedit.hologram.recovering", readable(exception), failureCount);
                    }
                }
                finish(request);
            });
        }
    }

    private static PreparedPreview prepare(
            java.util.Set<BlockVector3> blocks,
            SmoothPieces pieces,
            PreviewRequest request
    ) {
        LongOpenHashSet occupancy = new LongOpenHashSet(blocks.size());
        for (BlockVector3 block : blocks) {
            occupancy.add(pack(block.x(), block.y(), block.z()));
        }
        checkCancelled(request);

        Map<SectionKey, IntArrayList> grouped = new HashMap<>();
        int faces = 0;
        int processed = 0;
        for (BlockVector3 block : blocks) {
            if ((processed++ & 4_095) == 0) {
                checkCancelled(request);
            }
            int sectionX = Math.floorDiv(block.x(), 16);
            int sectionY = Math.floorDiv(block.y(), 16);
            int sectionZ = Math.floorDiv(block.z(), 16);
            int localX = Math.floorMod(block.x(), 16);
            int localY = Math.floorMod(block.y(), 16);
            int localZ = Math.floorMod(block.z(), 16);
            SectionKey key = new SectionKey(sectionX, sectionY, sectionZ);
            IntArrayList sectionFaces = null;
            int mask = SmoothPiece.mask(pieces.at(block.x(), block.y(), block.z()));
            int local = localX | localY << 4 | localZ << 8;
            for (int direction = 0; direction < 6; direction++) {
                int neighborX = block.x() + OFFSETS_X[direction];
                int neighborY = block.y() + OFFSETS_Y[direction];
                int neighborZ = block.z() + OFFSETS_Z[direction];
                boolean occupied = neighborY >= -2048 && neighborY <= 2047
                        && occupancy.contains(pack(neighborX, neighborY, neighborZ));
                int neighborMask = occupied ? SmoothPiece.mask(pieces.at(neighborX, neighborY, neighborZ)) : 0;
                if (mask == SmoothPiece.FULL_MASK && neighborMask == SmoothPiece.FULL_MASK) {
                    continue;
                }
                if (sectionFaces == null) {
                    sectionFaces = grouped.computeIfAbsent(key, ignored -> new IntArrayList());
                }
                if (mask == SmoothPiece.FULL_MASK && neighborMask == 0) {
                    sectionFaces.add(local | direction << 12);
                    faces++;
                    continue;
                }
                faces += octantFaces(sectionFaces, local, direction, mask, neighborMask);
            }
        }
        List<SectionData> sections = grouped.entrySet().stream()
                .map(entry -> new SectionData(entry.getKey(), entry.getValue().toIntArray()))
                .toList();
        return new PreparedPreview(sections, blocks.size(), faces, pieces.isEmpty() ? "" : tr("gui.komaedit.hologram.pieces", pieces.slabs(), pieces.stairs()));
    }

    /**
     * Caras de medio bloque de una losa o escalera (o de un bloque entero pegado a una): cada octante ocupado
     * dibuja su cara si el octante de al lado, dentro del mismo bloque o en el vecino, está vacío.
     */
    private static int octantFaces(IntArrayList faces, int local, int direction, int mask, int neighborMask) {
        int axisBit = AXIS_BITS[direction];
        boolean positive = (direction & 1) == 1;
        int added = 0;
        for (int octant = 0; octant < 8; octant++) {
            if ((mask & 1 << octant) == 0) {
                continue;
            }
            int adjacent = octant ^ axisBit;
            boolean boundary = ((octant & axisBit) != 0) == positive;
            int adjacentMask = boundary ? neighborMask : mask;
            if ((adjacentMask & 1 << adjacent) != 0) {
                continue;
            }
            faces.add(local | direction << 12 | octant << 15 | OCTANT_FACE);
            added++;
        }
        return added;
    }

    private static void install(PreviewRequest request, PreparedPreview prepared) {
        if (request.generation() != GENERATIONS.get() || !request.key().equals(requestedKey)) {
            finish(request);
            return;
        }
        state.close();
        List<SectionMesh> meshes = prepared.sections().stream().map(SectionMesh::new).toList();
        state = new RenderState(
                new ArrayList<>(meshes),
                prepared.blockCount(),
                prepared.faceCount(),
                request.serverAdjusted(),
                prepared.pieceSummary()
        );
        clearFailure(request.key());
        status = tr("gui.komaedit.hologram.prepared", prepared.blockCount());
        finish(request);
    }

    private static void uploadNearestVisible(WorldRenderContext context, RenderState current, Vec3d camera) {
        int uploadedFaces = 0;
        for (int upload = 0; upload < MAX_UPLOADS_PER_FRAME; upload++) {
            SectionMesh nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (SectionMesh section : current.sections()) {
                if (section.faces() == null || !visible(context, section, camera)) {
                    continue;
                }
                double distance = section.distanceSquared(camera);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = section;
                }
            }
            if (nearest == null) {
                break;
            }
            int faceCount = nearest.faces().length;
            if (uploadedFaces > 0 && uploadedFaces + faceCount > MAX_FACES_PER_FRAME) {
                break;
            }
            nearest.upload();
            current.markUploaded();
            uploadedFaces += faceCount;
        }
    }

    private static boolean visible(WorldRenderContext context, SectionMesh section, Vec3d camera) {
        return section.distanceSquared(camera) <= RENDER_DISTANCE_SQUARED
                && (context.frustum() == null || context.frustum().isVisible(section.bounds()));
    }

    private static void clearPreview(String nextStatus) {
        GENERATIONS.incrementAndGet();
        requestedKey = null;
        pending = null;
        if (running != null) {
            running.cancel(true);
            running = null;
        }
        runningGeneration = 0L;
        failedKey = null;
        failureCount = 0;
        retryNotBefore = 0L;
        state.close();
        state = RenderState.empty();
        status = nextStatus;
    }

    private static void finishCancelled(MinecraftClient client, PreviewRequest request) {
        client.execute(() -> finish(request));
    }

    private static void finish(PreviewRequest request) {
        if (runningGeneration == request.generation()) {
            running = null;
            runningGeneration = 0L;
        }
    }

    private static void checkCancelled(PreviewRequest request) {
        if (Thread.currentThread().isInterrupted() || request.generation() != GENERATIONS.get()) {
            throw new CancellationException();
        }
    }

    private static void recordFailure(PreviewKey key) {
        if (key.equals(failedKey)) {
            failureCount++;
        } else {
            failedKey = key;
            failureCount = 1;
        }
        int exponent = Math.min(4, Math.max(0, failureCount - 1));
        long delay = Math.min(MAX_RETRY_NANOS, MIN_RETRY_NANOS << exponent);
        retryNotBefore = System.nanoTime() + delay;
    }

    private static void clearFailure(PreviewKey key) {
        if (!key.equals(failedKey)) {
            return;
        }
        failedKey = null;
        failureCount = 0;
        retryNotBefore = 0L;
    }

    private static long pack(int x, int y, int z) {
        return PackedBlockPlan.pack(x, y, z);
    }

    private static String tr(String key, Object... arguments) {
        return Text.translatable(key, arguments).getString();
    }

    private static String readable(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private record PreviewRequest(
            long generation,
            PreviewKey key,
            ProceduralRecipe recipe,
            PathSnapshot path,
            boolean serverAdjusted
    ) {
    }

    private record PreviewKey(int geometryHash, List<BlockPos> points, boolean closed) {
        private static PreviewKey create(ProceduralRecipe recipe, List<BlockPos> points, boolean closed) {
            var extensions = recipe.extensions();
            int geometryHash = Objects.hash(
                    recipe.shape(), recipe.radius(), recipe.spacing(), recipe.thickness(), recipe.rollDegrees(),
                    recipe.height(), recipe.options(), recipe.formula(),
                    extensions.startScalePercent(), extensions.endScalePercent(),
                    extensions.startSpacingPercent(), extensions.endSpacingPercent(),
                    extensions.twistDegrees(), extensions.easing(),
                    extensions.rhythmEnabled(), extensions.accentShape(), extensions.rhythmInterval(),
                    extensions.surfaceMode(), extensions.conformRadius(), extensions.surfaceOffset(),
                    extensions.socketsEnabled(), extensions.socketPlacement(), extensions.socketInterval(),
                    extensions.closeMicroGaps(), extensions.removeIsolated()
            );
            return new PreviewKey(geometryHash, List.copyOf(points), closed);
        }
    }

    private record SectionKey(int x, int y, int z) {
    }

    private record SectionData(SectionKey key, int[] faces) {
    }

    private record PreparedPreview(List<SectionData> sections, int blockCount, int faceCount, String pieceSummary) {
    }

    private static final class RenderState {
        private final List<SectionMesh> sections;
        private final int blockCount;
        private final int faceCount;
        private final boolean serverAdjusted;
        private final String pieceSummary;
        private int uploadedSections;

        private RenderState(
                List<SectionMesh> sections,
                int blockCount,
                int faceCount,
                boolean serverAdjusted,
                String pieceSummary
        ) {
            this.sections = sections;
            this.blockCount = blockCount;
            this.faceCount = faceCount;
            this.serverAdjusted = serverAdjusted;
            this.pieceSummary = pieceSummary;
        }

        private static RenderState empty() {
            return new RenderState(new ArrayList<>(), 0, 0, false, "");
        }

        private List<SectionMesh> sections() {
            return sections;
        }

        private int blockCount() {
            return blockCount;
        }

        private int faceCount() {
            return faceCount;
        }

        private boolean serverAdjusted() {
            return serverAdjusted;
        }

        private String pieceSummary() {
            return pieceSummary;
        }

        private int uploadedSections() {
            return uploadedSections;
        }

        private void markUploaded() {
            uploadedSections++;
        }

        private void close() {
            for (SectionMesh section : sections) {
                section.close();
            }
            sections.clear();
        }
    }

    private static final class SectionMesh {
        private final int originX;
        private final int originY;
        private final int originZ;
        private final Box bounds;
        private int[] faces;
        private VertexBuffer buffer;

        private SectionMesh(SectionData data) {
            originX = data.key().x() * 16;
            originY = data.key().y() * 16;
            originZ = data.key().z() * 16;
            bounds = new Box(originX, originY, originZ, originX + 16, originY + 16, originZ + 16);
            faces = data.faces();
        }

        private int originX() {
            return originX;
        }

        private int originY() {
            return originY;
        }

        private int originZ() {
            return originZ;
        }

        private Box bounds() {
            return bounds;
        }

        private int[] faces() {
            return faces;
        }

        private VertexBuffer buffer() {
            return buffer;
        }

        private double distanceSquared(Vec3d camera) {
            double x = originX + 8.0 - camera.x;
            double y = originY + 8.0 - camera.y;
            double z = originZ + 8.0 - camera.z;
            return x * x + y * y + z * z;
        }

        private void upload() {
            if (faces == null || faces.length == 0) {
                faces = null;
                return;
            }
            int bytes = Math.max(1_024, faces.length * 4 * VertexFormats.POSITION_COLOR.getVertexSizeByte());
            try (BufferAllocator allocator = new BufferAllocator(bytes)) {
                BufferBuilder builder = new BufferBuilder(
                        allocator,
                        VertexFormat.DrawMode.QUADS,
                        VertexFormats.POSITION_COLOR
                );
                for (int face : faces) {
                    emitFace(builder, face);
                }
                BuiltBuffer built = builder.end();
                buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(built);
                VertexBuffer.unbind();
            }
            faces = null;
        }

        private void close() {
            faces = null;
            if (buffer != null) {
                buffer.close();
                buffer = null;
            }
        }

        private static void emitFace(BufferBuilder builder, int packed) {
            int x = packed & 15;
            int y = packed >> 4 & 15;
            int z = packed >> 8 & 15;
            int direction = packed >> 12 & 7;
            // Las caras de octante ocupan medio bloque; el margen solo se aplica en el borde del bloque para que
            // las caras de una misma pieza se vean continuas.
            boolean octantFace = (packed & OCTANT_FACE) != 0;
            int octant = packed >> 15 & 7;
            float size = octantFace ? 0.5F : 1.0F;
            float cellX = x + (octantFace ? (octant & 1) * 0.5F : 0.0F);
            float cellY = y + (octantFace ? (octant >> 1 & 1) * 0.5F : 0.0F);
            float cellZ = z + (octantFace ? (octant >> 2 & 1) * 0.5F : 0.0F);
            float inset = 0.035F;
            float minimumX = cellX + (cellX == x ? inset : 0.0F);
            float minimumY = cellY + (cellY == y ? inset : 0.0F);
            float minimumZ = cellZ + (cellZ == z ? inset : 0.0F);
            float maximumX = cellX + size - (cellX + size == x + 1.0F ? inset : 0.0F);
            float maximumY = cellY + size - (cellY + size == y + 1.0F ? inset : 0.0F);
            float maximumZ = cellZ + size - (cellZ + size == z + 1.0F ? inset : 0.0F);
            float west = cellX - 0.012F;
            float east = cellX + size + 0.012F;
            float down = cellY - 0.012F;
            float up = cellY + size + 0.012F;
            float north = cellZ - 0.012F;
            float south = cellZ + size + 0.012F;
            int brightness = switch (direction) {
                case 2 -> 145;
                case 3 -> 245;
                case 0, 1 -> 205;
                default -> 180;
            };
            int red = 58;
            int green = Math.min(255, brightness);
            int blue = 255;
            int alpha = 72;
            switch (direction) {
                case 0 -> quad(builder,
                        west, minimumY, minimumZ, west, minimumY, maximumZ,
                        west, maximumY, maximumZ, west, maximumY, minimumZ,
                        red, green, blue, alpha);
                case 1 -> quad(builder,
                        east, minimumY, maximumZ, east, minimumY, minimumZ,
                        east, maximumY, minimumZ, east, maximumY, maximumZ,
                        red, green, blue, alpha);
                case 2 -> quad(builder,
                        minimumX, down, maximumZ, maximumX, down, maximumZ,
                        maximumX, down, minimumZ, minimumX, down, minimumZ,
                        red, green, blue, alpha);
                case 3 -> quad(builder,
                        minimumX, up, minimumZ, maximumX, up, minimumZ,
                        maximumX, up, maximumZ, minimumX, up, maximumZ,
                        red, green, blue, alpha);
                case 4 -> quad(builder,
                        maximumX, minimumY, north, minimumX, minimumY, north,
                        minimumX, maximumY, north, maximumX, maximumY, north,
                        red, green, blue, alpha);
                default -> quad(builder,
                        minimumX, minimumY, south, maximumX, minimumY, south,
                        maximumX, maximumY, south, minimumX, maximumY, south,
                        red, green, blue, alpha);
            }
        }

        private static void quad(
                BufferBuilder builder,
                float firstX, float firstY, float firstZ,
                float secondX, float secondY, float secondZ,
                float thirdX, float thirdY, float thirdZ,
                float fourthX, float fourthY, float fourthZ,
                int red, int green, int blue, int alpha
        ) {
            builder.vertex(firstX, firstY, firstZ).color(red, green, blue, alpha);
            builder.vertex(secondX, secondY, secondZ).color(red, green, blue, alpha);
            builder.vertex(thirdX, thirdY, thirdZ).color(red, green, blue, alpha);
            builder.vertex(fourthX, fourthY, fourthZ).color(red, green, blue, alpha);
        }
    }

    private static final class PreviewThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "KomaEdit-Preview");
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            return thread;
        }
    }
}
