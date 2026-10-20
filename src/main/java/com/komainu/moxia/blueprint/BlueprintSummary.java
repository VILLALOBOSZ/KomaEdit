package com.komainu.moxia.blueprint;

import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lo que el Studio sabe de cada plano: lo justo para la cuadrícula y el buscador. La miniatura no va aquí; se pide
 * aparte por su huella ({@link #thumbnail}) y llega en trozos.
 *
 * @param reference cómo se le nombra en las órdenes ({@code servidor/…} si es compartido)
 * @param mine      si es de quien lo ve (puede borrarlo o compartirlo)
 */
public record BlueprintSummary(
        String reference,
        String name,
        boolean shared,
        boolean mine,
        String author,
        long created,
        int sizeX,
        int sizeY,
        int sizeZ,
        int blocks,
        List<String> tags,
        String thumbnail
) {
    public static final int MAX_REFERENCE = 48;
    public static final int MAX_AUTHOR = 32;

    public BlueprintSummary {
        tags = List.copyOf(tags);
        author = author.length() > MAX_AUTHOR ? author.substring(0, MAX_AUTHOR) : author;
    }

    public static BlueprintSummary of(BlueprintLibrary.Entry entry, UUID viewer) {
        BlueprintMeta meta = entry.meta();
        return new BlueprintSummary(meta.reference(), meta.name(), meta.scope() == BlueprintScope.SHARED,
                meta.owner().equals(viewer), meta.author(), meta.created(), meta.sizeX(), meta.sizeY(), meta.sizeZ(),
                meta.blocks(), meta.tags(), meta.thumbnail());
    }

    public boolean matches(BlueprintSearch search) {
        return search.matches(name, tags);
    }

    public String describeSize() {
        return sizeX + "×" + sizeY + "×" + sizeZ;
    }

    public void write(PacketByteBuf buffer) {
        buffer.writeString(reference, MAX_REFERENCE);
        buffer.writeString(name, 32);
        buffer.writeBoolean(shared);
        buffer.writeBoolean(mine);
        buffer.writeString(author, MAX_AUTHOR);
        buffer.writeLong(created);
        buffer.writeVarInt(sizeX);
        buffer.writeVarInt(sizeY);
        buffer.writeVarInt(sizeZ);
        buffer.writeVarInt(blocks);
        buffer.writeVarInt(tags.size());
        for (String tag : tags) {
            buffer.writeString(tag, BlueprintTags.MAX_LENGTH);
        }
        buffer.writeString(thumbnail, 64);
    }

    public static BlueprintSummary read(PacketByteBuf buffer) {
        String reference = buffer.readString(MAX_REFERENCE);
        String name = buffer.readString(32);
        boolean shared = buffer.readBoolean();
        boolean mine = buffer.readBoolean();
        String author = buffer.readString(MAX_AUTHOR);
        long created = buffer.readLong();
        int sizeX = buffer.readVarInt();
        int sizeY = buffer.readVarInt();
        int sizeZ = buffer.readVarInt();
        int blocks = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > BlueprintTags.MAX_TAGS) {
            throw new IllegalArgumentException("Demasiadas etiquetas en un plano");
        }
        List<String> tags = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            tags.add(buffer.readString(BlueprintTags.MAX_LENGTH));
        }
        return new BlueprintSummary(reference, name, shared, mine, author, created, sizeX, sizeY, sizeZ, blocks, tags,
                buffer.readString(64));
    }
}
