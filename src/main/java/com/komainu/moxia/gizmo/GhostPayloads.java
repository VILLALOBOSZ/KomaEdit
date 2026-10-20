package com.komainu.moxia.gizmo;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.preview.PreviewPlanCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Paquetes del fantasma con gizmo, solo entre el servidor y un cliente con KomaEdit. El servidor manda el objeto una
 * vez ({@link Open} y sus {@link Part}); el cliente solo devuelve la transformación al confirmar ({@link Confirm}),
 * o que lo cancela, y su modo ({@link Mode}).
 */
public final class GhostPayloads {
    private static final int MAX_TEXT = 512;

    private GhostPayloads() {
    }

    /**
     * Abre un fantasma: lo que hace falta para moverlo antes de que lleguen sus bloques.
     *
     * @param pivotX  posición de partida del pivote
     * @param bounds  caja local, alrededor del pivote
     * @param blocks  entradas del objeto (con el aire)
     * @param air     cuántas son aire
     * @param options opciones de partida ({@link GhostOptions#bits()})
     * @param palette estados distintos del objeto (para saber en el cliente qué giros admite)
     */
    public record Open(int id, String description, int pivotX, int pivotY, int pivotZ, BlockBounds bounds, int blocks,
                       int air, int options, int[] palette) implements CustomPayload {
        public static final int MAX_PALETTE = 65_536;

        public static final Id<Open> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "ghost_open"));
        public static final PacketCodec<RegistryByteBuf, Open> CODEC = PacketCodec.ofStatic(Open::write, Open::read);

        private static void write(RegistryByteBuf buffer, Open payload) {
            buffer.writeVarInt(payload.id());
            buffer.writeString(clip(payload.description()), MAX_TEXT);
            buffer.writeInt(payload.pivotX());
            buffer.writeInt(payload.pivotY());
            buffer.writeInt(payload.pivotZ());
            BlockBounds bounds = payload.bounds();
            buffer.writeInt(bounds.minimumX());
            buffer.writeInt(bounds.minimumY());
            buffer.writeInt(bounds.minimumZ());
            buffer.writeInt(bounds.maximumX());
            buffer.writeInt(bounds.maximumY());
            buffer.writeInt(bounds.maximumZ());
            buffer.writeVarInt(payload.blocks());
            buffer.writeVarInt(payload.air());
            buffer.writeByte(payload.options());
            int[] palette = payload.palette();
            buffer.writeVarInt(Math.min(palette.length, MAX_PALETTE));
            for (int index = 0; index < Math.min(palette.length, MAX_PALETTE); index++) {
                buffer.writeVarInt(palette[index]);
            }
        }

        private static Open read(RegistryByteBuf buffer) {
            int id = buffer.readVarInt();
            String description = buffer.readString(MAX_TEXT);
            int x = buffer.readInt();
            int y = buffer.readInt();
            int z = buffer.readInt();
            BlockBounds bounds = new BlockBounds(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readInt(), buffer.readInt());
            int blocks = buffer.readVarInt();
            int air = buffer.readVarInt();
            int options = buffer.readByte();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_PALETTE) {
                throw new IllegalArgumentException("Paleta fuera de rango: " + count);
            }
            int[] palette = new int[count];
            for (int index = 0; index < count; index++) {
                palette[index] = buffer.readVarInt();
            }
            return new Open(id, description, x, y, z, bounds, blocks, air, options, palette);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Una parte de los bloques del fantasma (su superficie), en el formato de {@link PreviewPlanCodec}. */
    public record Part(int id, int part, int parts, byte[] data) implements CustomPayload {
        public static final Id<Part> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "ghost_part"));
        public static final PacketCodec<RegistryByteBuf, Part> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.id());
                    buffer.writeVarInt(payload.part());
                    buffer.writeVarInt(payload.parts());
                    buffer.writeByteArray(payload.data());
                },
                buffer -> new Part(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readByteArray(PreviewPlanCodec.MAX_PART_BYTES)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Qué pasó con el fantasma {@code id}. */
    public record Result(int id, Outcome outcome, String message) implements CustomPayload {
        public static final Id<Result> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "ghost_result"));
        public static final PacketCodec<RegistryByteBuf, Result> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.id());
                    buffer.writeByte(payload.outcome().ordinal());
                    buffer.writeString(clip(payload.message()), MAX_TEXT);
                },
                buffer -> new Result(buffer.readVarInt(), Outcome.of(buffer.readByte()), buffer.readString(MAX_TEXT)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public enum Outcome {
        /** Colocado: el fantasma se cierra. */
        PLACED,
        /** Copia colocada: el fantasma sigue. */
        COPY_PLACED,
        /** No se colocó (motivo en el mensaje): el fantasma sigue para moverlo. */
        REJECTED,
        /** El servidor lo quitó (otra orden, cambio de mundo, {@code /komaedit cancel}). */
        CLOSED,
        /** {@code /komaedit confirm}: el cliente confirma con su transformación de ahora. */
        CONFIRM;

        static Outcome of(int ordinal) {
            Outcome[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CLOSED;
        }
    }

    /** Coloca el fantasma {@code id} con esta transformación (y lo cierra, salvo que sea una copia). */
    public record Confirm(int id, int orientation, int x, int y, int z, int options, boolean copy)
            implements CustomPayload {
        public static final Id<Confirm> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "ghost_confirm"));
        public static final PacketCodec<RegistryByteBuf, Confirm> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.id());
                    buffer.writeByte(payload.orientation());
                    buffer.writeInt(payload.x());
                    buffer.writeInt(payload.y());
                    buffer.writeInt(payload.z());
                    buffer.writeByte(payload.options());
                    buffer.writeBoolean(payload.copy());
                },
                buffer -> new Confirm(buffer.readVarInt(), buffer.readByte(), buffer.readInt(), buffer.readInt(),
                        buffer.readInt(), buffer.readByte(), buffer.readBoolean()));

        public static Confirm of(GhostSession.Confirm confirm) {
            GhostTransform transform = confirm.transform();
            return new Confirm(confirm.id(), transform.orientation().index(), transform.x(), transform.y(),
                    transform.z(), confirm.options().bits(), confirm.copy());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** El jugador quita el fantasma {@code id} sin colocarlo. */
    public record Cancel(int id) implements CustomPayload {
        public static final Id<Cancel> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "ghost_cancel"));
        public static final PacketCodec<RegistryByteBuf, Cancel> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> buffer.writeVarInt(payload.id()),
                buffer -> new Cancel(buffer.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Cuándo quiere el jugador el fantasma ({@link GizmoSettings.Mode}); lo manda el cliente al entrar. */
    public record Mode(GizmoSettings.Mode mode) implements CustomPayload {
        public static final Id<Mode> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "ghost_mode"));
        public static final PacketCodec<RegistryByteBuf, Mode> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> buffer.writeByte(payload.mode().ordinal()),
                buffer -> new Mode(GizmoSettings.Mode.byOrdinal(buffer.readByte())));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static String clip(String text) {
        String value = text == null ? "" : text;
        return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value;
    }
}
