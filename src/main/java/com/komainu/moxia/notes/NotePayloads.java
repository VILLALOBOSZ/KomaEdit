package com.komainu.moxia.notes;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Los paquetes de las notas, solo hacia clientes con KomaEdit: el cliente las dibuja y las lista. */
public final class NotePayloads {
    /** Notas por paquete; una sincronización entera va en varios. */
    public static final int PAGE = 128;

    private NotePayloads() {
    }

    /**
     * Del servidor: notas de la dimensión del jugador que puede ver.
     *
     * @param reset    la primera página de una sincronización entera: lo que tenía el cliente se tira
     * @param notes    las notas (nuevas o cambiadas)
     * @param canWrite puede poner notas privadas
     * @param canShare puede poner notas del equipo
     * @param seesTeam ve las del equipo
     * @param manages  puede borrar las de otros
     */
    public record NoteSyncPayload(boolean reset, String dimension, List<WorldNote> notes, boolean canWrite,
                                  boolean canShare, boolean seesTeam, boolean manages) implements CustomPayload {
        public static final Id<NoteSyncPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "note_sync"));
        public static final PacketCodec<PacketByteBuf, NoteSyncPayload> CODEC = PacketCodec.ofStatic(
                NoteSyncPayload::write, NoteSyncPayload::read);

        public NoteSyncPayload {
            notes = List.copyOf(notes);
            if (notes.size() > PAGE) {
                throw new IllegalArgumentException("Una página lleva como mucho " + PAGE + " notas");
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(PacketByteBuf buffer, NoteSyncPayload payload) {
            buffer.writeBoolean(payload.reset());
            buffer.writeString(payload.dimension(), 128);
            buffer.writeVarInt(payload.notes().size());
            payload.notes().forEach(note -> writeNote(buffer, note));
            buffer.writeBoolean(payload.canWrite());
            buffer.writeBoolean(payload.canShare());
            buffer.writeBoolean(payload.seesTeam());
            buffer.writeBoolean(payload.manages());
        }

        private static NoteSyncPayload read(PacketByteBuf buffer) {
            boolean reset = buffer.readBoolean();
            String dimension = buffer.readString(128);
            int size = buffer.readVarInt();
            if (size < 0 || size > PAGE) {
                throw new IllegalArgumentException("Página de notas no válida");
            }
            List<WorldNote> notes = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                notes.add(readNote(buffer));
            }
            return new NoteSyncPayload(reset, dimension, notes, buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean());
        }
    }

    /** Del servidor: esta nota ya no está (borrada, o la dejó de ver). */
    public record NoteRemovePayload(String id) implements CustomPayload {
        public static final Id<NoteRemovePayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "note_remove"));
        public static final PacketCodec<PacketByteBuf, NoteRemovePayload> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> buffer.writeString(payload.id(), 16),
                buffer -> new NoteRemovePayload(buffer.readString(16)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    static void writeNote(PacketByteBuf buffer, WorldNote note) {
        buffer.writeString(note.id(), 16);
        buffer.writeString(note.text(), NoteLimits.HARD_MAX_LENGTH * 4);
        buffer.writeInt(note.color());
        buffer.writeVarInt(note.icon().ordinal());
        buffer.writeString(note.dimension(), 128);
        buffer.writeDouble(note.x());
        buffer.writeDouble(note.y());
        buffer.writeDouble(note.z());
        buffer.writeBoolean(note.block());
        buffer.writeVarInt(note.side());
        buffer.writeVarInt(note.distance());
        buffer.writeBoolean(note.team());
        buffer.writeUuid(note.authorId());
        buffer.writeString(note.author(), 64);
        buffer.writeLong(note.created());
        buffer.writeString(note.resolvedBy(), 64);
        buffer.writeLong(note.resolvedAt());
    }

    static WorldNote readNote(PacketByteBuf buffer) {
        String id = buffer.readString(16);
        String text = buffer.readString(NoteLimits.HARD_MAX_LENGTH * 4);
        int color = buffer.readInt();
        int icon = buffer.readVarInt();
        NoteIcon[] icons = NoteIcon.values();
        String dimension = buffer.readString(128);
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        boolean block = buffer.readBoolean();
        int side = buffer.readVarInt();
        int distance = Math.clamp(buffer.readVarInt(), NoteLimits.MIN_DISTANCE, NoteLimits.HARD_MAX_DISTANCE);
        boolean team = buffer.readBoolean();
        UUID authorId = buffer.readUuid();
        String author = buffer.readString(64);
        long created = buffer.readLong();
        String resolvedBy = buffer.readString(64);
        long resolvedAt = buffer.readLong();
        return new WorldNote(id, text, color, icon >= 0 && icon < icons.length ? icons[icon] : NoteIcon.NOTA,
                dimension, x, y, z, block, side, distance, team, authorId, author, created, resolvedBy, resolvedAt);
    }
}
