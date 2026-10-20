package com.komainu.moxia.feedback;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * La selección de WorldEdit del jugador para que su cliente la dibuje con sus medidas: la caja que la contiene y la
 * forma ({@code cuboid}, {@code poly2d}, {@code ellipsoid}…). Con {@code complete} a falso solo hay el primer
 * punto. Solo se manda cuando cambia.
 */
public record SelectionPayload(boolean visible, boolean complete, int minX, int minY, int minZ, int maxX, int maxY,
                               int maxZ, String shape) implements CustomPayload {
    public static final Id<SelectionPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "selection"));
    public static final PacketCodec<RegistryByteBuf, SelectionPayload> CODEC = PacketCodec.ofStatic(
            SelectionPayload::write,
            SelectionPayload::read
    );
    public static final SelectionPayload NONE = new SelectionPayload(false, false, 0, 0, 0, 0, 0, 0, "");

    public SelectionPayload {
        shape = shape == null ? "" : shape;
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, SelectionPayload payload) {
        buffer.writeBoolean(payload.visible());
        if (!payload.visible()) {
            return;
        }
        buffer.writeBoolean(payload.complete());
        buffer.writeInt(payload.minX());
        buffer.writeInt(payload.minY());
        buffer.writeInt(payload.minZ());
        buffer.writeInt(payload.maxX());
        buffer.writeInt(payload.maxY());
        buffer.writeInt(payload.maxZ());
        buffer.writeString(payload.shape(), 32);
    }

    private static SelectionPayload read(RegistryByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return NONE;
        }
        return new SelectionPayload(true, buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readString(32));
    }
}
