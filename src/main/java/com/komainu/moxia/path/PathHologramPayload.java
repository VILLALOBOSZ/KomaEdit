package com.komainu.moxia.path;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public record PathHologramPayload(boolean active, boolean closed, List<BlockPos> points) implements CustomPayload {
    public static final Id<PathHologramPayload> ID = new Id<>(
            Identifier.of(KomaEditMod.MOD_ID, "path_hologram")
    );
    public static final PacketCodec<RegistryByteBuf, PathHologramPayload> CODEC = PacketCodec.ofStatic(
            PathHologramPayload::write,
            PathHologramPayload::read
    );

    public PathHologramPayload {
        points = List.copyOf(points);
        if (points.size() > PathManager.MAX_POINTS) {
            throw new IllegalArgumentException("Demasiados puntos para el holograma");
        }
    }

    public static PathHologramPayload clear() {
        return new PathHologramPayload(false, false, List.of());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, PathHologramPayload payload) {
        buffer.writeBoolean(payload.active());
        if (!payload.active()) {
            return;
        }
        buffer.writeBoolean(payload.closed());
        buffer.writeVarInt(payload.points().size());
        payload.points().forEach(buffer::writeBlockPos);
    }

    private static PathHologramPayload read(RegistryByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return clear();
        }
        boolean closed = buffer.readBoolean();
        int size = buffer.readVarInt();
        if (size < 0 || size > PathManager.MAX_POINTS) {
            throw new IllegalArgumentException("Cantidad inválida de puntos: " + size);
        }
        List<BlockPos> points = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            points.add(buffer.readBlockPos());
        }
        return new PathHologramPayload(true, closed, points);
    }
}
