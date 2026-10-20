package com.komainu.moxia.capability;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Intención del cliente con las capacidades: activar o cambiar una opción, o un clic interceptado. El cliente solo
 * dice qué ha pulsado y dónde (como el paquete de uso de bloque de Minecraft); el servidor lo comprueba todo.
 *
 * @param position bloque pulsado (o la celda en el aire de «Colocar en el aire» o el final de un arrastre)
 * @param face     cara pulsada ({@code Direction} por id) o -1
 * @param hit      punto exacto del clic, para colocar como Minecraft (mitad de arriba o de abajo…)
 * @param index    capacidad u opción, según la acción
 * @param value    valor de la opción, modo de juego o 1/0/-1 (activar, desactivar, alternar)
 */
public record CapabilityActionPayload(
        Action action,
        BlockPos position,
        int face,
        Vec3d hit,
        boolean sneaking,
        int index,
        int value
) implements CustomPayload {
    public enum Action {
        SYNC,
        TOGGLE,
        OPTION,
        GAMEMODE,
        TWEAK,
        TWEAK_PROPERTY,
        EXTRUDE,
        SHRINK,
        REPLACE,
        PLACE,
        ANGEL,
        BREAK,
        SMEAR_START,
        SMEAR_END,
        SMEAR_CANCEL
    }

    public static final Id<CapabilityActionPayload> ID = new Id<>(
            Identifier.of(KomaEditMod.MOD_ID, "capability_action"));
    public static final PacketCodec<RegistryByteBuf, CapabilityActionPayload> CODEC = PacketCodec.ofStatic(
            CapabilityActionPayload::write,
            CapabilityActionPayload::read
    );

    public CapabilityActionPayload {
        action = action == null ? Action.SYNC : action;
        position = position == null ? BlockPos.ORIGIN : position.toImmutable();
        hit = hit == null ? Vec3d.ofCenter(position) : hit;
    }

    public static CapabilityActionPayload simple(Action action, int index, int value) {
        return new CapabilityActionPayload(action, BlockPos.ORIGIN, -1, null, false, index, value);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, CapabilityActionPayload payload) {
        buffer.writeVarInt(payload.action().ordinal());
        buffer.writeBlockPos(payload.position());
        buffer.writeByte(payload.face());
        buffer.writeDouble(payload.hit().x);
        buffer.writeDouble(payload.hit().y);
        buffer.writeDouble(payload.hit().z);
        buffer.writeBoolean(payload.sneaking());
        buffer.writeVarInt(payload.index());
        buffer.writeVarInt(payload.value());
    }

    private static CapabilityActionPayload read(RegistryByteBuf buffer) {
        Action[] actions = Action.values();
        int ordinal = buffer.readVarInt();
        return new CapabilityActionPayload(
                ordinal >= 0 && ordinal < actions.length ? actions[ordinal] : Action.SYNC,
                buffer.readBlockPos(),
                buffer.readByte(),
                new Vec3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }
}
