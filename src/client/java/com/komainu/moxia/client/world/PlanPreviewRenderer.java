package com.komainu.moxia.client.world;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.feedback.WorldFeedbackSettings;
import com.komainu.moxia.preview.PreviewDisplays;
import com.komainu.moxia.preview.PreviewOutline;
import com.komainu.moxia.preview.PreviewPlanCodec;
import com.komainu.moxia.preview.PreviewPlanPayload;
import com.komainu.moxia.preview.PreviewSurface;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * La vista previa universal dibujada en el cliente con el plan compacto que manda el servidor
 * ({@link PreviewPlanCodec}), en lugar de cientos de block displays:
 *
 * <ul>
 *     <li>Lo que se pone o se cambia, con su propio bloque translúcido y un tinte leve (verdoso lo nuevo, azulado lo
 *     que sustituye); lo que se quita, cubierto de rojo translúcido. Siempre a plena luz (se ve igual de noche o en
 *     una cueva).</li>
 *     <li>Transparencia de una sola capa: primero una pasada solo de profundidad y luego el color, así solo se ve la
 *     cara más cercana (no un amasijo de caras y aristas de detrás). Se dibuja al final del mundo
 *     ({@code WorldRenderEvents.LAST}) para que esa profundidad no tape nubes, lluvia ni partículas.</li>
 *     <li>El contorno de la figura, sin la rejilla de cada bloque ({@link PreviewOutline}): verde lo que se pone,
 *     azul claro lo que sustituye a otro bloque y rojo lo que se quita.</li>
 *     <li>Sin z-fighting: lo que coincide con un bloque del mundo se adelanta con polygon offset, no con un tamaño
 *     un poco mayor.</li>
 *     <li>Se atenúa al acercarse ({@link WorldFeedbackSettings#opacityAt}) y puede quedarse solo en contorno.</li>
 * </ul>
 *
 * Solo llegan las caras que se ven: la superficie, nunca el interior. La malla se arma en un hilo aparte y se sube a
 * la GPU por secciones de 16³, las más cercanas primero.
 */
public final class PlanPreviewRenderer {
    private static final int MAX_UPLOADS_PER_FRAME = 4;
    private static final int MAX_QUADS_PER_FRAME = 40_000;
    private static final double RENDER_DISTANCE = 768.0;
    private static final int ADD = 0x5CFF7A;
    private static final int REPLACE = 0x7FD7FF;
    private static final int REMOVE = 0xFF4D4D;
    /** Tinte de los bloques fantasma (multiplica su textura): verdoso lo nuevo, azulado lo que sustituye. */
    private static final int GHOST_ADD = 0xD9FFD9;
    private static final int GHOST_REPLACE = 0xD9EBFF;
    private static final float[] SHADE = {0.6F, 0.6F, 0.5F, 1.0F, 0.8F, 0.8F};
    private static final Direction[] DIRECTIONS = {Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP,
            Direction.NORTH, Direction.SOUTH};
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ExecutorService BUILDER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit-PreviewMesh");
        thread.setDaemon(true);
        thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        return thread;
    });
    private static final PreviewPlanCodec.Assembler ASSEMBLER = new PreviewPlanCodec.Assembler();

    private static Scene scene = Scene.EMPTY;
    /** Id del plan que se está recibiendo o armando, o 0. */
    private static int incoming;
    private static boolean broken;

    private PlanPreviewRenderer() {
    }

    // ---------------------------------------------------------------- estado

    /** En el hilo del cliente. */
    public static void receive(PreviewPlanPayload payload, MinecraftClient client) {
        if (payload.clears()) {
            boolean any = payload.id() == PreviewPlanPayload.ANY;
            // Quitar la que se ve no corta la que está llegando, y al revés.
            if (any || payload.id() == scene.header().id()) {
                scene.close();
                scene = Scene.EMPTY;
            }
            if (any || payload.id() == incoming) {
                GENERATION.incrementAndGet();
                ASSEMBLER.clear();
                incoming = 0;
            }
            return;
        }
        try {
            incoming = payload.id();
            Optional<PreviewPlanCodec.Plan> plan = ASSEMBLER.accept(payload.id(), payload.part(), payload.parts(),
                    payload.data());
            plan.ifPresent(complete -> build(complete, client));
        } catch (IllegalArgumentException exception) {
            KomaEditMod.LOGGER.warn("Discarding a broken KomaEdit preview plan: {}", exception.getMessage());
            ASSEMBLER.clear();
            incoming = 0;
        }
    }

    /** Cabecera de la vista previa en pantalla (caja, recuentos), si la hay. */
    public static Optional<PreviewPlanCodec.Header> header() {
        return scene == Scene.EMPTY ? Optional.empty() : Optional.of(scene.header());
    }

    public static void clear() {
        GENERATION.incrementAndGet();
        ASSEMBLER.clear();
        incoming = 0;
        scene.close();
        scene = Scene.EMPTY;
    }

    private static void build(PreviewPlanCodec.Plan plan, MinecraftClient client) {
        long generation = GENERATION.incrementAndGet();
        BUILDER.execute(() -> {
            try {
                List<SectionData> sections = mesh(plan, client);
                client.execute(() -> {
                    if (generation == GENERATION.get()) {
                        scene.close();
                        scene = new Scene(plan.header(), sections.stream().map(SectionMesh::new).toList());
                        if (incoming == plan.header().id()) {
                            incoming = 0;
                        }
                        WorldFeedbackClient.previewReady(plan.header());
                    }
                });
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.warn("Could not build the KomaEdit preview mesh", exception);
                client.execute(() -> {
                    if (generation == GENERATION.get() && incoming == plan.header().id()) {
                        incoming = 0;
                    }
                });
            }
        });
    }

    // ---------------------------------------------------------------- malla (hilo aparte)

    /** Lo que dibuja una sección, aún sin GPU. */
    record SectionData(int x, int y, int z, BakedQuad[] quads, int[] quadInfo, int[] quadTint,
                               int[] colorFaces, int[] edges) {
    }

    /** La malla de un plan por secciones de 16³ (también la del fantasma del gizmo, en posiciones locales). */
    static List<SectionData> mesh(PreviewPlanCodec.Plan plan, MinecraftClient client) {
        Map<Long, PreviewOutline.Section> outline = new HashMap<>();
        for (PreviewOutline.Section section : PreviewOutline.build(plan.positions(), plan.kinds(), plan.faces())) {
            outline.put(sectionKey(section.x(), section.y(), section.z()), section);
        }
        Map<Long, Builder> builders = new HashMap<>();
        Random random = Random.create();
        BlockPos.Mutable position = new BlockPos.Mutable();
        for (int index = 0; index < plan.size(); index++) {
            long packed = plan.positions()[index];
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            int mask = plan.faces()[index] & PreviewSurface.ALL_FACES;
            if (mask == 0) {
                continue;
            }
            long key = sectionKey(x >> 4, y >> 4, z >> 4);
            Builder builder = builders.computeIfAbsent(key, ignored -> new Builder(x >> 4, y >> 4, z >> 4));
            int local = (x & 15) | (y & 15) << 4 | (z & 15) << 8;
            PreviewDisplays.Kind kind = plan.kind(index);
            BlockState state = Block.getStateFromRawId(plan.states()[index]);
            if (kind == PreviewDisplays.Kind.REMOVE || state.isAir()
                    || state.getRenderType() != BlockRenderType.MODEL) {
                // Lo que se quita, y lo que no tiene modelo (agua, luz…): caras de color.
                for (int direction = 0; direction < 6; direction++) {
                    if ((mask & 1 << direction) != 0) {
                        builder.colorFaces.add(local | direction << 12 | kind.ordinal() << 15);
                    }
                }
                continue;
            }
            BakedModel model = client.getBlockRenderManager().getModel(state);
            long seed = state.getRenderingSeed(position.set(x, y, z));
            for (int direction = 0; direction < 6; direction++) {
                if ((mask & 1 << direction) == 0) {
                    continue;
                }
                random.setSeed(seed);
                for (BakedQuad quad : model.getQuads(state, DIRECTIONS[direction], random)) {
                    builder.add(quad, local, kind, tint(client, state, quad));
                }
            }
            random.setSeed(seed);
            for (BakedQuad quad : model.getQuads(state, null, random)) {
                builder.add(quad, local, kind, tint(client, state, quad));
            }
        }
        for (PreviewOutline.Section section : outline.values()) {
            builders.computeIfAbsent(sectionKey(section.x(), section.y(), section.z()),
                    ignored -> new Builder(section.x(), section.y(), section.z()));
        }
        List<SectionData> sections = new ArrayList<>(builders.size());
        for (Map.Entry<Long, Builder> entry : builders.entrySet()) {
            PreviewOutline.Section edges = outline.get(entry.getKey());
            sections.add(entry.getValue().build(edges == null ? new int[0] : edges.edges()));
        }
        return sections;
    }

    private static int tint(MinecraftClient client, BlockState state, BakedQuad quad) {
        if (!quad.hasColor()) {
            return 0xFFFFFF;
        }
        // Sin mundo: el color de serie de la hierba y las hojas (no se lee el bioma fuera del hilo del cliente).
        int color = client.getBlockColors().getColor(state, null, null, quad.getColorIndex());
        return color == -1 ? 0xFFFFFF : color & 0xFFFFFF;
    }

    private static final class Builder {
        private final int x;
        private final int y;
        private final int z;
        private final List<BakedQuad> quads = new ArrayList<>();
        private final IntArrayList quadInfo = new IntArrayList();
        private final IntArrayList quadTint = new IntArrayList();
        private final IntArrayList colorFaces = new IntArrayList();

        private Builder(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private void add(BakedQuad quad, int local, PreviewDisplays.Kind kind, int tint) {
            int face = quad.getFace().ordinal();
            quads.add(quad);
            quadInfo.add(local | kind.ordinal() << 12 | (quad.hasShade() ? 1 : 0) << 14 | face << 15);
            quadTint.add(tint);
        }

        private SectionData build(int[] edges) {
            return new SectionData(x, y, z, quads.toArray(new BakedQuad[0]), quadInfo.toIntArray(),
                    quadTint.toIntArray(), colorFaces.toIntArray(), edges);
        }
    }

    // ---------------------------------------------------------------- dibujo

    public static void render(WorldRenderContext context) {
        Scene current = scene;
        if (current == Scene.EMPTY || broken) {
            return;
        }
        try {
            draw(context, current);
        } catch (RuntimeException exception) {
            // Mejor sin vista previa que sin juego: se avisa una vez y se deja de dibujar hasta la siguiente.
            broken = true;
            KomaEditMod.LOGGER.error("KomaEdit preview rendering failed; hiding it", exception);
            resetState();
        }
    }

    private static void draw(WorldRenderContext context, Scene current) {
        drawSections(context, current.sections(), null);
    }

    /**
     * Dónde va una malla armada en posiciones locales: su origen en el mundo y, si hace falta, un giro rígido
     * alrededor del centro del bloque del origen (mientras se rehace la malla de un fantasma recién girado).
     */
    record Anchor(double x, double y, double z, Matrix4f rotation) {
    }

    /** Dibuja unas secciones; con {@code anchor}, desplazadas (y giradas) como un fantasma. */
    static void drawSections(WorldRenderContext context, List<SectionMesh> sections, Anchor anchor) {
        WorldFeedbackSettings settings = ClientWorldSettings.get();
        Vec3d camera = context.camera().getPos();
        upload(context, sections, camera, anchor);
        boolean ghost = settings.previewStyle() == WorldFeedbackSettings.PreviewStyle.GHOST;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        if (ghost) {
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-1.0F, -10.0F);
            RenderSystem.enableCull();
            ShaderProgram textured = GameRenderer.getPositionTexColorProgram();
            ShaderProgram colored = GameRenderer.getPositionColorProgram();
            if (textured != null) {
                RenderSystem.setShaderTexture(0, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
                // Una sola capa: la profundidad de la cara más cercana y luego solo su color.
                RenderSystem.colorMask(false, false, false, false);
                RenderSystem.depthMask(true);
                for (SectionMesh section : sections) {
                    if (section.ghost != null && visible(context, section, camera, anchor)) {
                        section.ghost.bind();
                        section.ghost.draw(section.view(context, camera, false, anchor), context.projectionMatrix(),
                                textured);
                    }
                }
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(false);
                for (SectionMesh section : sections) {
                    if (section.ghost != null && visible(context, section, camera, anchor)) {
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F,
                                (float) settings.opacityAt(section.distance(camera, anchor)));
                        section.ghost.bind();
                        section.ghost.draw(section.view(context, camera, false, anchor), context.projectionMatrix(),
                                textured);
                    }
                }
            }
            if (colored != null) {
                for (SectionMesh section : sections) {
                    if (section.color != null && visible(context, section, camera, anchor)) {
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F,
                                (float) Math.min(0.85, settings.opacityAt(section.distance(camera, anchor)) * 0.9));
                        section.color.bind();
                        section.color.draw(section.view(context, camera, false, anchor), context.projectionMatrix(),
                                colored);
                    }
                }
            }
            RenderSystem.disablePolygonOffset();
        }
        ShaderProgram lines = GameRenderer.getRenderTypeLinesProgram();
        if (lines != null) {
            RenderSystem.disableCull();
            RenderSystem.lineWidth(Math.max(2.0F, MinecraftClient.getInstance().getWindow().getFramebufferWidth()
                    / 1920.0F * 2.0F));
            for (SectionMesh section : sections) {
                if (section.lines != null && visible(context, section, camera, anchor)) {
                    double distance = section.distance(camera, anchor);
                    float alpha = ghost ? (float) Math.clamp(0.45 + distance / 12.0, 0.45, 0.95) : 0.95F;
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                    section.lines.bind();
                    section.lines.draw(section.view(context, camera, true, anchor), context.projectionMatrix(), lines);
                }
            }
        }
        VertexBuffer.unbind();
        resetState();
    }

    private static void resetState() {
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.lineWidth(1.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void upload(WorldRenderContext context, List<SectionMesh> sections, Vec3d camera, Anchor anchor) {
        int quads = 0;
        for (int upload = 0; upload < MAX_UPLOADS_PER_FRAME; upload++) {
            SectionMesh nearest = null;
            double best = Double.MAX_VALUE;
            for (SectionMesh section : sections) {
                if (section.data == null || !visible(context, section, camera, anchor)) {
                    continue;
                }
                double distance = section.distance(camera, anchor);
                if (distance < best) {
                    best = distance;
                    nearest = section;
                }
            }
            if (nearest == null) {
                return;
            }
            int size = nearest.data.quads().length + nearest.data.colorFaces().length;
            if (quads > 0 && quads + size > MAX_QUADS_PER_FRAME) {
                return;
            }
            nearest.upload();
            quads += size;
        }
    }

    private static boolean visible(WorldRenderContext context, SectionMesh section, Vec3d camera, Anchor anchor) {
        if (section.distance(camera, anchor) > RENDER_DISTANCE) {
            return false;
        }
        if (anchor != null) {
            // Desplazada: la caja del mundo es la de la sección movida (girada, se dibuja siempre).
            return anchor.rotation() != null || context.frustum() == null
                    || context.frustum().isVisible(section.bounds.offset(anchor.x(), anchor.y(), anchor.z()));
        }
        return context.frustum() == null || context.frustum().isVisible(section.bounds);
    }

    private static long sectionKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFL) << 42 | ((long) y & 0xFFFFFL) << 22 | (long) z & 0x3FFFFFL;
    }

    private record Scene(PreviewPlanCodec.Header header, List<SectionMesh> sections) {
        static final Scene EMPTY = new Scene(new PreviewPlanCodec.Header(0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                com.komainu.moxia.preview.DisplaySampler.Mode.ALL, 0), List.of());

        void close() {
            sections.forEach(SectionMesh::close);
        }
    }

    static final class SectionMesh {
        private final int originX;
        private final int originY;
        private final int originZ;
        private final Box bounds;
        private SectionData data;
        private VertexBuffer ghost;
        private VertexBuffer color;
        private VertexBuffer lines;

        SectionMesh(SectionData data) {
            this.data = data;
            originX = data.x() * 16;
            originY = data.y() * 16;
            originZ = data.z() * 16;
            bounds = new Box(originX, originY, originZ, originX + 16, originY + 16, originZ + 16);
        }

        /** Distancia de la cámara a la caja de la sección (0 dentro), desplazada con el ancla si la hay. */
        private double distance(Vec3d camera, Anchor anchor) {
            double baseX = originX + (anchor == null ? 0.0 : anchor.x());
            double baseY = originY + (anchor == null ? 0.0 : anchor.y());
            double baseZ = originZ + (anchor == null ? 0.0 : anchor.z());
            double x = Math.max(0.0, Math.max(baseX - camera.x, camera.x - (baseX + 16)));
            double y = Math.max(0.0, Math.max(baseY - camera.y, camera.y - (baseY + 16)));
            double z = Math.max(0.0, Math.max(baseZ - camera.z, camera.z - (baseZ + 16)));
            return Math.sqrt(x * x + y * y + z * z);
        }

        /** Vista de la sección; las líneas, un pelo hacia la cámara para que no se hundan en las aristas. */
        private Matrix4f view(WorldRenderContext context, Vec3d camera, boolean layered, Anchor anchor) {
            Matrix4f view = layered ? new Matrix4f().scaling(0.99975586F).mul(context.positionMatrix())
                    : new Matrix4f(context.positionMatrix());
            if (anchor == null) {
                return view.translate((float) (originX - camera.x), (float) (originY - camera.y),
                        (float) (originZ - camera.z));
            }
            // Primero el ancla respecto a la cámara (en doble, sin perder precisión lejos del origen), luego el giro
            // y por último la sección en coordenadas locales.
            view.translate((float) (anchor.x() - camera.x), (float) (anchor.y() - camera.y),
                    (float) (anchor.z() - camera.z));
            if (anchor.rotation() != null) {
                view.mul(anchor.rotation());
            }
            return view.translate(originX, originY, originZ);
        }

        private void upload() {
            SectionData source = data;
            data = null;
            if (source == null) {
                return;
            }
            ghost = buffer(source.quads().length, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR,
                    builder -> emitGhost(builder, source));
            color = buffer(source.colorFaces().length, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR,
                    builder -> emitColor(builder, source));
            lines = buffer(source.edges().length, VertexFormat.DrawMode.LINES, VertexFormats.LINES,
                    builder -> emitLines(builder, source));
        }

        private static VertexBuffer buffer(int primitives, VertexFormat.DrawMode mode, VertexFormat format,
                                           java.util.function.Consumer<BufferBuilder> emit) {
            if (primitives == 0) {
                return null;
            }
            int bytes = Math.max(1_024, primitives * 4 * format.getVertexSizeByte());
            try (BufferAllocator allocator = new BufferAllocator(bytes)) {
                BufferBuilder builder = new BufferBuilder(allocator, mode, format);
                emit.accept(builder);
                BuiltBuffer built = builder.endNullable();
                if (built == null) {
                    return null;
                }
                VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(built);
                VertexBuffer.unbind();
                return buffer;
            }
        }

        private static void emitGhost(BufferBuilder builder, SectionData source) {
            for (int index = 0; index < source.quads().length; index++) {
                BakedQuad quad = source.quads()[index];
                int info = source.quadInfo()[index];
                int local = info & 0xFFF;
                float lx = local & 15;
                float ly = local >> 4 & 15;
                float lz = local >> 8 & 15;
                boolean shaded = (info >> 14 & 1) != 0;
                Direction face = Direction.values()[info >> 15 & 7];
                float shade = shaded ? SHADE[faceIndex(face)] : 1.0F;
                int tint = source.quadTint()[index];
                int ghost = (info >> 12 & 3) == PreviewDisplays.Kind.ADD.ordinal() ? GHOST_ADD : GHOST_REPLACE;
                int red = (int) ((tint >> 16 & 0xFF) * shade * (ghost >> 16 & 0xFF) / 255.0F);
                int green = (int) ((tint >> 8 & 0xFF) * shade * (ghost >> 8 & 0xFF) / 255.0F);
                int blue = (int) ((tint & 0xFF) * shade * (ghost & 0xFF) / 255.0F);
                int[] vertices = quad.getVertexData();
                int stride = vertices.length / 4;
                for (int vertex = 0; vertex < 4; vertex++) {
                    int base = vertex * stride;
                    builder.vertex(Float.intBitsToFloat(vertices[base]) + lx,
                                    Float.intBitsToFloat(vertices[base + 1]) + ly,
                                    Float.intBitsToFloat(vertices[base + 2]) + lz)
                            .texture(Float.intBitsToFloat(vertices[base + 4]), Float.intBitsToFloat(vertices[base + 5]))
                            .color(red, green, blue, 255);
                }
            }
        }

        private static void emitColor(BufferBuilder builder, SectionData source) {
            for (int face : source.colorFaces()) {
                int local = face & 0xFFF;
                float x = local & 15;
                float y = local >> 4 & 15;
                float z = local >> 8 & 15;
                int direction = face >> 12 & 7;
                int kind = face >> 15 & 3;
                int rgb = kind == PreviewDisplays.Kind.REMOVE.ordinal() ? REMOVE
                        : kind == PreviewDisplays.Kind.ADD.ordinal() ? ADD : REPLACE;
                float shade = SHADE[direction];
                int red = (int) ((rgb >> 16 & 0xFF) * shade);
                int green = (int) ((rgb >> 8 & 0xFF) * shade);
                int blue = (int) ((rgb & 0xFF) * shade);
                float x0 = x;
                float y0 = y;
                float z0 = z;
                float x1 = x + 1.0F;
                float y1 = y + 1.0F;
                float z1 = z + 1.0F;
                switch (direction) {
                    case 0 -> quad(builder, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, red, green, blue);
                    case 1 -> quad(builder, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, red, green, blue);
                    case 2 -> quad(builder, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, red, green, blue);
                    case 3 -> quad(builder, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, red, green, blue);
                    case 4 -> quad(builder, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, red, green, blue);
                    default -> quad(builder, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, red, green, blue);
                }
            }
        }

        private static void quad(BufferBuilder builder, float ax, float ay, float az, float bx, float by, float bz,
                                 float cx, float cy, float cz, float dx, float dy, float dz,
                                 int red, int green, int blue) {
            builder.vertex(ax, ay, az).color(red, green, blue, 255);
            builder.vertex(bx, by, bz).color(red, green, blue, 255);
            builder.vertex(cx, cy, cz).color(red, green, blue, 255);
            builder.vertex(dx, dy, dz).color(red, green, blue, 255);
        }

        private static void emitLines(BufferBuilder builder, SectionData source) {
            for (int edge : source.edges()) {
                float x = PreviewOutline.localX(edge);
                float y = PreviewOutline.localY(edge);
                float z = PreviewOutline.localZ(edge);
                int axis = PreviewOutline.axis(edge);
                int length = PreviewOutline.length(edge);
                int kind = PreviewOutline.kind(edge);
                int rgb = kind == PreviewDisplays.Kind.REMOVE.ordinal() ? REMOVE
                        : kind == PreviewDisplays.Kind.ADD.ordinal() ? ADD : REPLACE;
                float nx = axis == 0 ? 1.0F : 0.0F;
                float ny = axis == 1 ? 1.0F : 0.0F;
                float nz = axis == 2 ? 1.0F : 0.0F;
                int red = rgb >> 16 & 0xFF;
                int green = rgb >> 8 & 0xFF;
                int blue = rgb & 0xFF;
                builder.vertex(x, y, z).color(red, green, blue, 255).normal(nx, ny, nz);
                builder.vertex(x + nx * length, y + ny * length, z + nz * length).color(red, green, blue, 255)
                        .normal(nx, ny, nz);
            }
        }

        /** Índice de {@link #SHADE} (orden oeste, este, abajo, arriba, norte, sur) de una cara. */
        private static int faceIndex(Direction face) {
            return switch (face) {
                case WEST -> 0;
                case EAST -> 1;
                case DOWN -> 2;
                case UP -> 3;
                case NORTH -> 4;
                case SOUTH -> 5;
            };
        }

        void close() {
            data = null;
            for (VertexBuffer buffer : new VertexBuffer[]{ghost, color, lines}) {
                if (buffer != null) {
                    buffer.close();
                }
            }
            ghost = null;
            color = null;
            lines = null;
        }
    }

    /** Vuelve a intentar dibujar (tras un fallo) con la siguiente vista previa. */
    static void allowRetry() {
        broken = false;
    }
}
