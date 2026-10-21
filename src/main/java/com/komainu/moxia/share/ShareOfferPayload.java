package com.komainu.moxia.share;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que trae un paquete antes de importarlo, para la ventana del Studio (el chat lleva lo mismo con botones). Las
 * respuestas van por las órdenes de siempre: {@code /komaedit share accept renombrar|reemplazar|saltar} o
 * {@code cancel}.
 *
 * @param conflicts cuántos chocan con algo que ya tiene el jugador
 */
public record ShareOfferPayload(String name, String author, String modVersion, int conflicts, List<Line> lines)
        implements CustomPayload {
    public static final Id<ShareOfferPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "share_offer"));
    public static final int MAX_LINES = 64;
    public static final PacketCodec<RegistryByteBuf, ShareOfferPayload> CODEC = PacketCodec.ofStatic(
            ShareOfferPayload::write, ShareOfferPayload::read);

    /**
     * Un elemento.
     *
     * @param status ordinal de {@link ShareImport.Status}
     * @param label  «paleta «rocas»»
     * @param detail con qué choca, por qué no entra o qué se cambió
     */
    public record Line(int status, String label, String detail) {
    }

    public ShareOfferPayload {
        lines = List.copyOf(lines.subList(0, Math.min(lines.size(), MAX_LINES)));
    }

    private static void write(RegistryByteBuf buffer, ShareOfferPayload payload) {
        buffer.writeString(payload.name(), 64);
        buffer.writeString(payload.author(), 64);
        buffer.writeString(payload.modVersion(), 64);
        buffer.writeVarInt(payload.conflicts());
        buffer.writeVarInt(payload.lines().size());
        for (Line line : payload.lines()) {
            buffer.writeVarInt(line.status());
            buffer.writeString(line.label(), 128);
            buffer.writeString(line.detail(), 512);
        }
    }

    private static ShareOfferPayload read(RegistryByteBuf buffer) {
        String name = buffer.readString(64);
        String author = buffer.readString(64);
        String version = buffer.readString(64);
        int conflicts = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_LINES) {
            throw new IllegalArgumentException("Demasiadas líneas en la oferta de importación");
        }
        List<Line> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lines.add(new Line(buffer.readVarInt(), buffer.readString(128), buffer.readString(512)));
        }
        return new ShareOfferPayload(name, author, version, conflicts, lines);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
