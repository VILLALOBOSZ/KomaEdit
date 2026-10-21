package com.komainu.moxia.share;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * «Pegar código» del Studio: el código que el jugador tiene en el portapapeles, para los que no caben en los 256
 * caracteres del chat. El servidor lo trata igual que {@code /komaedit share import <código>}.
 */
public record ShareImportPayload(String code) implements CustomPayload {
    public static final Id<ShareImportPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "share_import"));
    /** Lo que cabe en un paquete del cliente (32 KiB) con margen. */
    public static final int MAX_CODE = 32_000;
    public static final PacketCodec<RegistryByteBuf, ShareImportPayload> CODEC = PacketCodec.ofStatic(
            (buffer, payload) -> buffer.writeString(payload.code(), MAX_CODE),
            buffer -> new ShareImportPayload(buffer.readString(MAX_CODE))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
