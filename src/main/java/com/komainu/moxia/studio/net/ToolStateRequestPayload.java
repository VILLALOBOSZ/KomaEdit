package com.komainu.moxia.studio.net;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * El Studio pide el estado de las herramientas: al abrir la etapa Herramientas y cada segundo mientras está a la
 * vista (permisos, selección, presets y simetría pueden cambiar en cualquier momento).
 */
public record ToolStateRequestPayload() implements CustomPayload {
    public static final Id<ToolStateRequestPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID,
            "tool_state_request"));
    public static final ToolStateRequestPayload INSTANCE = new ToolStateRequestPayload();
    public static final PacketCodec<RegistryByteBuf, ToolStateRequestPayload> CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
