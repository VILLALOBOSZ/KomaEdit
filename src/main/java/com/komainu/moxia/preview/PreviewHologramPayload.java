package com.komainu.moxia.preview;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.edit.BlockBounds;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PreviewHologramPayload(
        boolean visible,
        int minimumX,
        int minimumY,
        int minimumZ,
        int maximumX,
        int maximumY,
        int maximumZ,
        int color
) implements CustomPayload {
    public static final Id<PreviewHologramPayload> ID = new Id<>(
            Identifier.of(KomaEditMod.MOD_ID, "preview_hologram")
    );
    public static final PacketCodec<RegistryByteBuf, PreviewHologramPayload> CODEC = PacketCodec.ofStatic(
            PreviewHologramPayload::write,
            PreviewHologramPayload::read
    );

    public static PreviewHologramPayload show(BlockBounds bounds, int color) {
        return new PreviewHologramPayload(
                true,
                bounds.minimumX(),
                bounds.minimumY(),
                bounds.minimumZ(),
                bounds.maximumX(),
                bounds.maximumY(),
                bounds.maximumZ(),
                color
        );
    }

    public static PreviewHologramPayload clear() {
        return new PreviewHologramPayload(false, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, PreviewHologramPayload payload) {
        buffer.writeBoolean(payload.visible());
        if (!payload.visible()) {
            return;
        }
        buffer.writeInt(payload.minimumX());
        buffer.writeInt(payload.minimumY());
        buffer.writeInt(payload.minimumZ());
        buffer.writeInt(payload.maximumX());
        buffer.writeInt(payload.maximumY());
        buffer.writeInt(payload.maximumZ());
        buffer.writeInt(payload.color());
    }

    private static PreviewHologramPayload read(RegistryByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return clear();
        }
        return new PreviewHologramPayload(
                true,
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt()
        );
    }
}
