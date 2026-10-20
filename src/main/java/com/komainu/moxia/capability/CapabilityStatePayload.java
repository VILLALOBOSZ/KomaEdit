package com.komainu.moxia.capability;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Estado de las capacidades que el servidor manda al cliente: qué tiene activado, qué le deja usar y los límites.
 * El cliente solo intercepta clics de lo que aquí viene activo y permitido.
 *
 * @param enabled     capacidades activas (máscara de {@link BuildCapability#bit()})
 * @param permitted   capacidades con permiso
 * @param modeAllowed el modo de juego le deja usarlas (creativo o el permiso para cualquier modo)
 * @param gamemode    puede cambiar el modo de juego desde el menú
 * @param flySpeedAllowed puede cambiar la velocidad de vuelo
 * @param smearAnchor bloque pulsado al fijar el origen de un arrastre a medias ({@code null} si no hay)
 * @param smearFrom   inicio de la fila de origen
 * @param smearTo     final de esa fila
 * @param smearFace   cara del arrastre ({@code Direction} por id) o -1
 */
public record CapabilityStatePayload(
        int enabled,
        int permitted,
        boolean modeAllowed,
        boolean gamemode,
        boolean flySpeedAllowed,
        boolean replaceFamily,
        boolean extrudeByState,
        int extrudeLimit,
        int angelDistance,
        int reachDistance,
        int flySpeed,
        String tweakProperty,
        int maxReach,
        int maxLayerBlocks,
        int maxSmearLength,
        int maxFlySpeed,
        BlockPos smearAnchor,
        BlockPos smearFrom,
        BlockPos smearTo,
        int smearFace
) implements CustomPayload {
    public static final Id<CapabilityStatePayload> ID = new Id<>(
            Identifier.of(KomaEditMod.MOD_ID, "capability_state"));
    public static final PacketCodec<RegistryByteBuf, CapabilityStatePayload> CODEC = PacketCodec.ofStatic(
            CapabilityStatePayload::write,
            CapabilityStatePayload::read
    );

    public CapabilityStatePayload {
        tweakProperty = tweakProperty == null ? "" : tweakProperty;
        if (smearAnchor == null || smearFrom == null || smearTo == null) {
            smearAnchor = null;
            smearFrom = null;
            smearTo = null;
            smearFace = -1;
        }
    }

    public boolean smearPending() {
        return smearAnchor != null;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, CapabilityStatePayload payload) {
        buffer.writeVarInt(payload.enabled());
        buffer.writeVarInt(payload.permitted());
        int flags = (payload.modeAllowed() ? 1 : 0)
                | (payload.gamemode() ? 2 : 0)
                | (payload.flySpeedAllowed() ? 4 : 0)
                | (payload.replaceFamily() ? 8 : 0)
                | (payload.extrudeByState() ? 16 : 0)
                | (payload.smearPending() ? 32 : 0);
        buffer.writeVarInt(flags);
        buffer.writeVarInt(payload.extrudeLimit());
        buffer.writeVarInt(payload.angelDistance());
        buffer.writeVarInt(payload.reachDistance());
        buffer.writeVarInt(payload.flySpeed());
        buffer.writeString(payload.tweakProperty(), 64);
        buffer.writeVarInt(payload.maxReach());
        buffer.writeVarInt(payload.maxLayerBlocks());
        buffer.writeVarInt(payload.maxSmearLength());
        buffer.writeVarInt(payload.maxFlySpeed());
        if (payload.smearPending()) {
            buffer.writeBlockPos(payload.smearAnchor());
            buffer.writeBlockPos(payload.smearFrom());
            buffer.writeBlockPos(payload.smearTo());
            buffer.writeByte(payload.smearFace());
        }
    }

    private static CapabilityStatePayload read(RegistryByteBuf buffer) {
        int enabled = buffer.readVarInt();
        int permitted = buffer.readVarInt();
        int flags = buffer.readVarInt();
        int extrudeLimit = buffer.readVarInt();
        int angelDistance = buffer.readVarInt();
        int reachDistance = buffer.readVarInt();
        int flySpeed = buffer.readVarInt();
        String tweakProperty = buffer.readString(64);
        int maxReach = buffer.readVarInt();
        int maxLayer = buffer.readVarInt();
        int maxSmear = buffer.readVarInt();
        int maxFly = buffer.readVarInt();
        BlockPos anchor = null;
        BlockPos from = null;
        BlockPos to = null;
        int face = -1;
        if ((flags & 32) != 0) {
            anchor = buffer.readBlockPos();
            from = buffer.readBlockPos();
            to = buffer.readBlockPos();
            face = buffer.readByte();
        }
        return new CapabilityStatePayload(enabled, permitted, (flags & 1) != 0, (flags & 2) != 0,
                (flags & 4) != 0, (flags & 8) != 0, (flags & 16) != 0, extrudeLimit, angelDistance, reachDistance,
                flySpeed, tweakProperty, maxReach, maxLayer, maxSmear, maxFly, anchor, from, to, face);
    }
}
