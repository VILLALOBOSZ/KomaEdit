package com.komainu.moxia.studio.net;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * El resultado de {@code /komaedit analyze} para la tarjeta Materiales del Studio: totales y las líneas de coste en
 * survival (como mucho {@link #MAX_LINES}), más el texto que copia el botón [COPIAR] del chat.
 *
 * @param hidden líneas que no caben en el paquete (siguen en el CSV)
 */
public record MaterialListPayload(long blocks, int types, long items, long slots, long chests, List<Line> lines,
                                  int hidden, String clipboard) implements CustomPayload {
    public static final Id<MaterialListPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "material_list"));
    public static final PacketCodec<RegistryByteBuf, MaterialListPayload> CODEC = PacketCodec.ofStatic(
            MaterialListPayload::write, MaterialListPayload::read);
    public static final int MAX_LINES = 200;
    public static final int MAX_CLIPBOARD = 30_000;

    /** Un objeto que hay que reunir, con su tamaño de pila. */
    public record Line(String item, long amount, int maxStack) {
        public long stacks() {
            return maxStack <= 0 ? 0 : amount / maxStack;
        }

        public long remainder() {
            return maxStack <= 0 ? amount : amount % maxStack;
        }
    }

    public MaterialListPayload {
        lines = List.copyOf(lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines);
        clipboard = clipboard == null ? "" : clipboard.length() > MAX_CLIPBOARD
                ? clipboard.substring(0, MAX_CLIPBOARD) : clipboard;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, MaterialListPayload payload) {
        buffer.writeVarLong(payload.blocks());
        buffer.writeVarInt(payload.types());
        buffer.writeVarLong(payload.items());
        buffer.writeVarLong(payload.slots());
        buffer.writeVarLong(payload.chests());
        buffer.writeVarInt(payload.lines().size());
        for (Line line : payload.lines()) {
            buffer.writeString(line.item(), 128);
            buffer.writeVarLong(line.amount());
            buffer.writeVarInt(line.maxStack());
        }
        buffer.writeVarInt(payload.hidden());
        buffer.writeString(payload.clipboard(), MAX_CLIPBOARD);
    }

    private static MaterialListPayload read(RegistryByteBuf buffer) {
        long blocks = buffer.readVarLong();
        int types = buffer.readVarInt();
        long items = buffer.readVarLong();
        long slots = buffer.readVarLong();
        long chests = buffer.readVarLong();
        int count = Math.min(buffer.readVarInt(), MAX_LINES);
        List<Line> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lines.add(new Line(buffer.readString(128), buffer.readVarLong(), buffer.readVarInt()));
        }
        return new MaterialListPayload(blocks, types, items, slots, chests, lines, buffer.readVarInt(),
                buffer.readString(MAX_CLIPBOARD));
    }
}
