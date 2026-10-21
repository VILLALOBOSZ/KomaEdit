package com.komainu.moxia.share;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * El resultado de exportar, para el cliente con KomaEdit: el código (que el Studio copia al portapapeles si lo acaba
 * de pedir con «Compartir») o el archivo donde se guardó. El chat lleva lo mismo con un clic para copiar.
 *
 * @param code    el código, o vacío si fue a un archivo
 * @param file    el archivo ({@code config/komaedit/exports/…}), o vacío
 * @param summary «2 paletas y 1 plano», para el pie del Studio
 */
public record ShareCodePayload(String code, String file, String summary) implements CustomPayload {
    public static final Id<ShareCodePayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "share_code"));
    public static final int MAX_CODE = 32_000;
    public static final PacketCodec<RegistryByteBuf, ShareCodePayload> CODEC = PacketCodec.ofStatic(
            (buffer, payload) -> {
                buffer.writeString(payload.code(), MAX_CODE);
                buffer.writeString(payload.file(), 512);
                buffer.writeString(payload.summary(), 256);
            },
            buffer -> new ShareCodePayload(buffer.readString(MAX_CODE), buffer.readString(512),
                    buffer.readString(256))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
