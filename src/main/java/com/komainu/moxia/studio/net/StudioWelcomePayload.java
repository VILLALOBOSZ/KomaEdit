package com.komainu.moxia.studio.net;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * {@code /komaedit tutorial} (o {@code bienvenida}): el cliente abre el Procedural Studio con la bienvenida guiada
 * desde el principio. La bienvenida vive en el cliente; el servidor solo avisa.
 */
public record StudioWelcomePayload() implements CustomPayload {
    public static final StudioWelcomePayload INSTANCE = new StudioWelcomePayload();
    public static final Id<StudioWelcomePayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "studio_welcome"));
    public static final PacketCodec<RegistryByteBuf, StudioWelcomePayload> CODEC = PacketCodec.ofStatic(
            (buffer, payload) -> {
            },
            buffer -> INSTANCE
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
