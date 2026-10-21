package com.komainu.moxia.selection;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.preview.PreviewOutline;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Los paquetes de la selección a mano: el lazo y el pincel (del cliente) y el contorno (del servidor). */
public final class SelectionPayloads {
    private SelectionPayloads() {
    }

    /**
     * Un lazo terminado: la cámara con la que se dibujó, el contorno en la pantalla, cómo se junta con la selección
     * ({@link SelectionCombine}) y su profundidad.
     */
    public record Lasso(int mode, double cameraX, double cameraY, double cameraZ, float forwardX, float forwardY,
                        float forwardZ, float upX, float upY, float upZ, float fov, float width, float height,
                        float[] xs, float[] ys, int depth, int layers, boolean nonSolid) implements CustomPayload {
        public static final Id<Lasso> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "selection_lasso"));
        public static final PacketCodec<RegistryByteBuf, Lasso> CODEC = PacketCodec.ofStatic(Lasso::write,
                Lasso::read);

        public static Lasso of(SelectionCombine mode, LassoCamera camera, LassoPolygon polygon,
                               LassoSettings settings) {
            return new Lasso(mode.ordinal(), camera.position().x(), camera.position().y(), camera.position().z(),
                    (float) camera.forward().x(), (float) camera.forward().y(), (float) camera.forward().z(),
                    (float) camera.up().x(), (float) camera.up().y(), (float) camera.up().z(), (float) camera.fov(),
                    (float) camera.width(), (float) camera.height(), polygon.xs(), polygon.ys(),
                    settings.depth().ordinal(), settings.layers(), settings.nonSolid());
        }

        public LassoCamera camera() {
            return LassoCamera.of(new com.komainu.moxia.shape.Vec3(cameraX, cameraY, cameraZ),
                    new com.komainu.moxia.shape.Vec3(forwardX, forwardY, forwardZ),
                    new com.komainu.moxia.shape.Vec3(upX, upY, upZ), fov, width, height);
        }

        public LassoPolygon polygon() {
            return new LassoPolygon(xs, ys);
        }

        public LassoSettings settings() {
            LassoSettings.Depth[] depths = LassoSettings.Depth.values();
            if (depth < 0 || depth >= depths.length) {
                throw new IllegalArgumentException("Profundidad desconocida");
            }
            return new LassoSettings(depths[depth], layers, nonSolid);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(RegistryByteBuf buffer, Lasso payload) {
            buffer.writeVarInt(payload.mode());
            buffer.writeDouble(payload.cameraX());
            buffer.writeDouble(payload.cameraY());
            buffer.writeDouble(payload.cameraZ());
            buffer.writeFloat(payload.forwardX());
            buffer.writeFloat(payload.forwardY());
            buffer.writeFloat(payload.forwardZ());
            buffer.writeFloat(payload.upX());
            buffer.writeFloat(payload.upY());
            buffer.writeFloat(payload.upZ());
            buffer.writeFloat(payload.fov());
            buffer.writeFloat(payload.width());
            buffer.writeFloat(payload.height());
            buffer.writeVarInt(payload.xs().length);
            for (int index = 0; index < payload.xs().length; index++) {
                buffer.writeFloat(payload.xs()[index]);
                buffer.writeFloat(payload.ys()[index]);
            }
            buffer.writeVarInt(payload.depth());
            buffer.writeVarInt(payload.layers());
            buffer.writeBoolean(payload.nonSolid());
        }

        private static Lasso read(RegistryByteBuf buffer) {
            int mode = buffer.readVarInt();
            double x = buffer.readDouble();
            double y = buffer.readDouble();
            double z = buffer.readDouble();
            float forwardX = buffer.readFloat();
            float forwardY = buffer.readFloat();
            float forwardZ = buffer.readFloat();
            float upX = buffer.readFloat();
            float upY = buffer.readFloat();
            float upZ = buffer.readFloat();
            float fov = buffer.readFloat();
            float width = buffer.readFloat();
            float height = buffer.readFloat();
            int count = buffer.readVarInt();
            if (count < 0 || count > LassoPolygon.MAX_POINTS) {
                throw new IllegalArgumentException("Lazo con demasiados puntos: " + count);
            }
            float[] xs = new float[count];
            float[] ys = new float[count];
            for (int index = 0; index < count; index++) {
                xs[index] = buffer.readFloat();
                ys[index] = buffer.readFloat();
            }
            return new Lasso(mode, x, y, z, forwardX, forwardY, forwardZ, upX, upY, upZ, fov, width, height, xs, ys,
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
        }
    }

    /**
     * Una pasada del pincel de selección en {@code position}. {@code phase}: {@link #START} empieza el trazo (con su
     * modo y su forma) y ya pinta, {@link #DAB} sigue pintando y {@link #END} lo acaba.
     */
    public record Brush(int phase, int mode, BlockPos position, int radius, int shape, boolean nonSolid)
            implements CustomPayload {
        public static final int START = 0;
        public static final int DAB = 1;
        public static final int END = 2;
        public static final Id<Brush> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "selection_brush"));
        public static final PacketCodec<RegistryByteBuf, Brush> CODEC = PacketCodec.ofStatic(Brush::write,
                Brush::read);

        public Brush {
            position = position == null ? BlockPos.ORIGIN : position.toImmutable();
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(RegistryByteBuf buffer, Brush payload) {
            buffer.writeVarInt(payload.phase());
            buffer.writeVarInt(payload.mode());
            buffer.writeBlockPos(payload.position());
            buffer.writeVarInt(payload.radius());
            buffer.writeVarInt(payload.shape());
            buffer.writeBoolean(payload.nonSolid());
        }

        private static Brush read(RegistryByteBuf buffer) {
            return new Brush(buffer.readVarInt(), buffer.readVarInt(), buffer.readBlockPos(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readBoolean());
        }
    }

    /**
     * El contorno de la selección de bloques sueltos del jugador, para que su cliente lo dibuje: cuántos bloques
     * tiene, su caja y sus aristas por secciones ({@link PreviewOutline}). Sin aristas ({@code sections} vacío y
     * {@code count} mayor que cero), la selección es demasiado grande y se dibuja solo la caja.
     */
    public record Outline(boolean visible, int count, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                          List<PreviewOutline.Section> sections) implements CustomPayload {
        public static final Id<Outline> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "selection_outline"));
        public static final PacketCodec<RegistryByteBuf, Outline> CODEC = PacketCodec.ofStatic(Outline::write,
                Outline::read);
        public static final Outline NONE = new Outline(false, 0, 0, 0, 0, 0, 0, 0, List.of());

        public Outline {
            sections = sections == null ? List.of() : List.copyOf(sections);
        }

        /** El contorno de {@code blocks} (solo la caja si es demasiado grande para dibujarlo entero). */
        public static Outline of(BlockSet blocks) {
            int[] box = blocks.bounds();
            if (box == null) {
                return NONE;
            }
            List<PreviewOutline.Section> sections = SelectionOutline.build(blocks);
            return new Outline(true, blocks.size(), box[0], box[1], box[2], box[3], box[4], box[5],
                    sections == null ? List.of() : sections);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(RegistryByteBuf buffer, Outline payload) {
            buffer.writeBoolean(payload.visible());
            if (!payload.visible()) {
                return;
            }
            buffer.writeVarInt(payload.count());
            buffer.writeInt(payload.minX());
            buffer.writeInt(payload.minY());
            buffer.writeInt(payload.minZ());
            buffer.writeInt(payload.maxX());
            buffer.writeInt(payload.maxY());
            buffer.writeInt(payload.maxZ());
            buffer.writeVarInt(payload.sections().size());
            for (PreviewOutline.Section section : payload.sections()) {
                buffer.writeVarInt(section.x());
                buffer.writeVarInt(section.y());
                buffer.writeVarInt(section.z());
                buffer.writeVarInt(section.edges().length);
                for (int edge : section.edges()) {
                    buffer.writeVarInt(edge);
                }
            }
        }

        private static Outline read(RegistryByteBuf buffer) {
            if (!buffer.readBoolean()) {
                return NONE;
            }
            int count = buffer.readVarInt();
            int minX = buffer.readInt();
            int minY = buffer.readInt();
            int minZ = buffer.readInt();
            int maxX = buffer.readInt();
            int maxY = buffer.readInt();
            int maxZ = buffer.readInt();
            int sectionCount = buffer.readVarInt();
            if (sectionCount < 0 || sectionCount > SelectionOutline.MAX_EDGES) {
                throw new IllegalArgumentException("Contorno no válido");
            }
            List<PreviewOutline.Section> sections = new ArrayList<>(sectionCount);
            int total = 0;
            for (int index = 0; index < sectionCount; index++) {
                int x = buffer.readVarInt();
                int y = buffer.readVarInt();
                int z = buffer.readVarInt();
                int edgeCount = buffer.readVarInt();
                total += edgeCount;
                if (edgeCount < 0 || total > SelectionOutline.MAX_EDGES) {
                    throw new IllegalArgumentException("Contorno no válido");
                }
                int[] edges = new int[edgeCount];
                for (int edge = 0; edge < edgeCount; edge++) {
                    edges[edge] = buffer.readVarInt();
                }
                sections.add(new PreviewOutline.Section(x, y, z, edges));
            }
            return new Outline(true, count, minX, minY, minZ, maxX, maxY, maxZ, sections);
        }
    }
}
