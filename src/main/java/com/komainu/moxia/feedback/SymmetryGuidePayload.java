package com.komainu.moxia.feedback;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * La simetría activa del jugador para que su cliente dibuje los planos y el eje en lugar de recibir partículas. El
 * centro va en medios bloques, como {@code SymmetryCenter}. Solo se manda cuando cambia.
 */
public record SymmetryGuidePayload(boolean active, boolean radial, boolean mirrorX, boolean mirrorZ, boolean mirrorY,
                                   int arms, int x2, int y2, int z2) implements CustomPayload {
    public static final Id<SymmetryGuidePayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "symmetry_guide"));
    public static final PacketCodec<RegistryByteBuf, SymmetryGuidePayload> CODEC = PacketCodec.ofStatic(
            SymmetryGuidePayload::write,
            SymmetryGuidePayload::read
    );
    public static final SymmetryGuidePayload NONE = new SymmetryGuidePayload(false, false, false, false, false, 0, 0,
            0, 0);

    public double centerX() {
        return x2 / 2.0;
    }

    public double centerY() {
        return y2 / 2.0;
    }

    public double centerZ() {
        return z2 / 2.0;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, SymmetryGuidePayload payload) {
        buffer.writeBoolean(payload.active());
        if (!payload.active()) {
            return;
        }
        int flags = (payload.radial() ? 1 : 0) | (payload.mirrorX() ? 2 : 0) | (payload.mirrorZ() ? 4 : 0)
                | (payload.mirrorY() ? 8 : 0);
        buffer.writeByte(flags);
        buffer.writeVarInt(payload.arms());
        buffer.writeInt(payload.x2());
        buffer.writeInt(payload.y2());
        buffer.writeInt(payload.z2());
    }

    private static SymmetryGuidePayload read(RegistryByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return NONE;
        }
        int flags = buffer.readByte();
        return new SymmetryGuidePayload(true, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0,
                (flags & 8) != 0, buffer.readVarInt(), buffer.readInt(), buffer.readInt(), buffer.readInt());
    }
}
