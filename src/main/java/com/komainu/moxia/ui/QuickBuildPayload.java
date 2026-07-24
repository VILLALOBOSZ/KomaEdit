package com.komainu.moxia.ui;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record QuickBuildPayload() implements CustomPayload {
    public static final QuickBuildPayload INSTANCE = new QuickBuildPayload();
    public static final Id<QuickBuildPayload> ID = new Id<>(
            Identifier.of(KomaEditMod.MOD_ID, "quickbuild")
    );
    public static final PacketCodec<RegistryByteBuf, QuickBuildPayload> CODEC = PacketCodec.ofStatic(
            (buffer, payload) -> {
            },
            buffer -> INSTANCE
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
