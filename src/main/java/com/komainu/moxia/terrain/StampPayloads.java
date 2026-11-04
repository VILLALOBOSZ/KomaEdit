package com.komainu.moxia.terrain;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.terrain.stamp.StampCategory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Los paquetes de los sellos propios entre el Studio y el servidor: el catálogo (nombre, autor, descripción, altura
 * natural y categoría de cada uno) y el relieve en pequeño de los que se ven, que el cliente pinta con
 * {@code StampThumbnails} como los de serie.
 */
public final class StampPayloads {
    public static final int MAX_CATALOG = 512;
    public static final int MAX_REQUESTED = 64;

    private StampPayloads() {
    }

    static void write(PacketByteBuf buffer, OwnStampSummary stamp) {
        buffer.writeString(stamp.name(), 64);
        buffer.writeString(stamp.author(), OwnStamp.MAX_AUTHOR * 2);
        buffer.writeString(stamp.description(), OwnStamp.MAX_DESCRIPTION * 2);
        buffer.writeVarInt(stamp.category().ordinal());
        buffer.writeFloat(stamp.heightRatio());
        buffer.writeFloat(stamp.neutral());
        buffer.writeString(stamp.hash(), 64);
        buffer.writeVarInt(stamp.sizeX());
        buffer.writeVarInt(stamp.sizeZ());
        buffer.writeBoolean(stamp.mine());
    }

    static OwnStampSummary read(PacketByteBuf buffer) {
        String name = buffer.readString(64);
        String author = buffer.readString(OwnStamp.MAX_AUTHOR * 2);
        String description = buffer.readString(OwnStamp.MAX_DESCRIPTION * 2);
        int category = buffer.readVarInt();
        StampCategory[] categories = StampCategory.values();
        return new OwnStampSummary(name, author, description, categories[Math.clamp(category, 0,
                categories.length - 1)], Math.clamp(buffer.readFloat(), 0.0f, 16.0f),
                Math.clamp(buffer.readFloat(), 0.0f, 1.0f), buffer.readString(64), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readBoolean());
    }

    /** Del cliente: pide el catálogo ({@link #CATALOG}) o el relieve en pequeño de unas huellas ({@link #PREVIEWS}). */
    public record StampsRequest(int action, List<String> values) implements CustomPayload {
        public static final int CATALOG = 0;
        public static final int PREVIEWS = 1;
        public static final Id<StampsRequest> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "stamp_request"));
        public static final PacketCodec<PacketByteBuf, StampsRequest> CODEC = PacketCodec.ofStatic(
                StampsRequest::write, StampsRequest::read);

        public StampsRequest {
            values = List.copyOf(values);
            if (values.size() > MAX_REQUESTED) {
                throw new IllegalArgumentException("Demasiados sellos en una petición");
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(PacketByteBuf buffer, StampsRequest request) {
            buffer.writeVarInt(request.action());
            buffer.writeVarInt(request.values().size());
            request.values().forEach(value -> buffer.writeString(value, 64));
        }

        private static StampsRequest read(PacketByteBuf buffer) {
            int action = buffer.readVarInt();
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_REQUESTED) {
                throw new IllegalArgumentException("Petición de sellos no válida");
            }
            List<String> values = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                values.add(buffer.readString(64));
            }
            return new StampsRequest(action, values);
        }
    }

    /** Del servidor: los sellos propios y si el jugador puede capturar y gestionar los de otros. */
    public record StampsCatalog(List<OwnStampSummary> stamps, boolean canCapture, boolean canManage)
            implements CustomPayload {
        public static final Id<StampsCatalog> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "stamp_catalog"));
        public static final PacketCodec<PacketByteBuf, StampsCatalog> CODEC = PacketCodec.ofStatic(
                StampsCatalog::write, StampsCatalog::read);

        public StampsCatalog {
            stamps = List.copyOf(stamps.subList(0, Math.min(stamps.size(), MAX_CATALOG)));
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(PacketByteBuf buffer, StampsCatalog catalog) {
            buffer.writeVarInt(catalog.stamps().size());
            catalog.stamps().forEach(stamp -> StampPayloads.write(buffer, stamp));
            buffer.writeBoolean(catalog.canCapture());
            buffer.writeBoolean(catalog.canManage());
        }

        private static StampsCatalog read(PacketByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_CATALOG) {
                throw new IllegalArgumentException("Catálogo de sellos no válido");
            }
            List<OwnStampSummary> stamps = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                stamps.add(StampPayloads.read(buffer));
            }
            return new StampsCatalog(stamps, buffer.readBoolean(), buffer.readBoolean());
        }
    }

    /** Del servidor: el relieve en pequeño de un sello propio, por su huella. */
    public record StampsPreview(OwnStamps.Preview preview) implements CustomPayload {
        public static final Id<StampsPreview> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "stamp_preview"));
        public static final PacketCodec<PacketByteBuf, StampsPreview> CODEC = PacketCodec.ofStatic(
                StampsPreview::write, StampsPreview::read);
        private static final int MAX_SIDE = 128;

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(PacketByteBuf buffer, StampsPreview payload) {
            OwnStamps.Preview preview = payload.preview();
            buffer.writeString(preview.hash(), 64);
            buffer.writeVarInt(preview.side());
            buffer.writeFloat((float) preview.neutral());
            for (short value : preview.values()) {
                buffer.writeShort(value);
            }
        }

        private static StampsPreview read(PacketByteBuf buffer) {
            String hash = buffer.readString(64);
            int side = buffer.readVarInt();
            if (side < 2 || side > MAX_SIDE) {
                throw new IllegalArgumentException("Miniatura de sello no válida");
            }
            double neutral = Math.clamp(buffer.readFloat(), 0.0f, 1.0f);
            short[] values = new short[side * side];
            for (int index = 0; index < values.length; index++) {
                values[index] = buffer.readShort();
            }
            return new StampsPreview(new OwnStamps.Preview(hash, side, neutral, values));
        }
    }
}
