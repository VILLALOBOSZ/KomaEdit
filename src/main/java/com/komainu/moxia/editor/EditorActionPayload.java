package com.komainu.moxia.editor;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record EditorActionPayload(
        int action,
        int index,
        BlockPos position,
        int radius,
        String pattern
) implements CustomPayload {
    public static final int PATH_ADD = 0;
    public static final int PATH_MOVE = 1;
    public static final int PATH_REMOVE = 2;
    public static final int BOX_PRIMARY = 3;
    public static final int BOX_SECONDARY = 4;
    public static final int MAGIC_BLOCK = 5;
    public static final int MAGIC_AREA = 6;
    public static final int PAINT = 7;
    public static final int UNDO = 8;
    public static final int REDO = 9;
    public static final int PAINT_SELECTION = 10;
    public static final int SELECTION_REPLACE = 11;
    public static final int SELECTION_DELETE = 12;
    public static final int CLIPBOARD_COPY = 13;
    public static final int CLIPBOARD_CUT = 14;
    public static final int CLIPBOARD_PASTE = 15;
    public static final int PAINT_BEGIN = 16;
    public static final int PAINT_END = 17;
    /** Inserta un punto a mitad entre {@code index} y el siguiente. */
    public static final int PATH_INSERT = 18;

    public static final Id<EditorActionPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "editor_action"));
    public static final PacketCodec<RegistryByteBuf, EditorActionPayload> CODEC = PacketCodec.ofStatic(
            EditorActionPayload::write,
            EditorActionPayload::read
    );

    public EditorActionPayload {
        if (position == null) {
            position = BlockPos.ORIGIN;
        }
        pattern = pattern == null ? "stone" : pattern;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, EditorActionPayload payload) {
        buffer.writeVarInt(payload.action());
        buffer.writeVarInt(payload.index());
        buffer.writeBlockPos(payload.position());
        buffer.writeVarInt(payload.radius());
        buffer.writeString(payload.pattern(), 256);
    }

    private static EditorActionPayload read(RegistryByteBuf buffer) {
        return new EditorActionPayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readString(256)
        );
    }
}
