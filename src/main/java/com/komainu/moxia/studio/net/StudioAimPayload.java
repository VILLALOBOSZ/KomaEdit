package com.komainu.moxia.studio.net;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * «Donde haces clic»: el bloque del mundo (y su cara, 0–5 en el orden de {@code Direction}) en el que el jugador hizo
 * clic con el Studio abierto, justo antes de la orden que se coloca ahí. {@code set} en falso lo olvida (al cerrar el
 * Studio).
 */
public record StudioAimPayload(boolean set, int x, int y, int z, int face) implements CustomPayload {
    public static final Id<StudioAimPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "studio_aim"));
    public static final StudioAimPayload CLEAR = new StudioAimPayload(false, 0, 0, 0, 0);
    public static final PacketCodec<RegistryByteBuf, StudioAimPayload> CODEC = PacketCodec.ofStatic(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.set());
                buffer.writeInt(payload.x());
                buffer.writeInt(payload.y());
                buffer.writeInt(payload.z());
                buffer.writeByte(payload.face());
            },
            buffer -> new StudioAimPayload(buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    Math.floorMod(buffer.readByte(), 6))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
