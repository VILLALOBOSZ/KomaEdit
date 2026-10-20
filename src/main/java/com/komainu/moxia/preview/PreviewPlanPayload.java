package com.komainu.moxia.preview;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Una parte del plan de una vista previa ({@link PreviewPlanCodec}) para el cliente con KomaEdit, o, con
 * {@code parts == 0}, la orden de quitar la vista previa {@code id} (o cualquiera, con {@link #ANY}).
 */
public record PreviewPlanPayload(int id, int part, int parts, byte[] data) implements CustomPayload {
    public static final int ANY = -1;
    public static final Id<PreviewPlanPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "preview_plan"));
    public static final PacketCodec<RegistryByteBuf, PreviewPlanPayload> CODEC = PacketCodec.ofStatic(
            PreviewPlanPayload::write,
            PreviewPlanPayload::read
    );

    public PreviewPlanPayload {
        data = data == null ? new byte[0] : data;
    }

    public static PreviewPlanPayload clear(int id) {
        return new PreviewPlanPayload(id, 0, 0, new byte[0]);
    }

    public boolean clears() {
        return parts == 0;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, PreviewPlanPayload payload) {
        buffer.writeVarInt(payload.id());
        buffer.writeVarInt(payload.parts());
        if (payload.parts() > 0) {
            buffer.writeVarInt(payload.part());
            buffer.writeByteArray(payload.data());
        }
    }

    private static PreviewPlanPayload read(RegistryByteBuf buffer) {
        int id = buffer.readVarInt();
        int parts = buffer.readVarInt();
        if (parts <= 0) {
            return clear(id);
        }
        int part = buffer.readVarInt();
        return new PreviewPlanPayload(id, part, parts, buffer.readByteArray(PreviewPlanCodec.MAX_PART_BYTES));
    }
}
