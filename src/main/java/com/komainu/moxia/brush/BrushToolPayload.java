package com.komainu.moxia.brush;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Clic izquierdo o tecla de cambiar de mano con una herramienta de KomaEdit. El cliente los intercepta (no rompe el
 * bloque ni cambia de mano) y los manda con esto; el clic derecho también, para que todos sigan el mismo camino.
 * El servidor apunta con su propio rayo, así que solo viaja la acción.
 */
public record BrushToolPayload(BrushInput input) implements CustomPayload {
    public static final Id<BrushToolPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "brush_tool"));
    public static final PacketCodec<RegistryByteBuf, BrushToolPayload> CODEC = PacketCodec.ofStatic(
            (buffer, payload) -> buffer.writeByte(payload.input().ordinal()),
            buffer -> {
                int ordinal = buffer.readByte();
                BrushInput[] values = BrushInput.values();
                return new BrushToolPayload(values[Math.floorMod(ordinal, values.length)]);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
