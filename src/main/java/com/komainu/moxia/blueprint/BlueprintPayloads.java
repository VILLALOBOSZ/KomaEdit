package com.komainu.moxia.blueprint;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Los paquetes de la biblioteca de planos entre el Studio y el servidor. */
public final class BlueprintPayloads {
    /** Planos que caben en un catálogo: los propios más los compartidos con los límites de serie y margen. */
    public static final int MAX_CATALOG = 1_024;
    public static final int MAX_REQUESTED = 64;

    private BlueprintPayloads() {
    }

    /**
     * Del cliente: pide el catálogo ({@link #CATALOG}) o las miniaturas de unas huellas ({@link #THUMBNAILS}).
     */
    public record Request(int action, List<String> values) implements CustomPayload {
        public static final int CATALOG = 0;
        public static final int THUMBNAILS = 1;
        public static final Id<Request> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "blueprint_request"));
        public static final PacketCodec<PacketByteBuf, Request> CODEC = PacketCodec.ofStatic(Request::write,
                Request::read);

        public Request {
            values = List.copyOf(values);
            if (values.size() > MAX_REQUESTED) {
                throw new IllegalArgumentException("Demasiadas miniaturas en una petición");
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(PacketByteBuf buffer, Request request) {
            buffer.writeVarInt(request.action());
            buffer.writeVarInt(request.values().size());
            request.values().forEach(value -> buffer.writeString(value, 64));
        }

        private static Request read(PacketByteBuf buffer) {
            int action = buffer.readVarInt();
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_REQUESTED) {
                throw new IllegalArgumentException("Petición de miniaturas no válida");
            }
            List<String> values = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                values.add(buffer.readString(64));
            }
            return new Request(action, values);
        }
    }

    /** Del servidor: los planos que ve el jugador y qué puede hacer con ellos. */
    public record Catalog(List<BlueprintSummary> blueprints, boolean canSave, boolean canPlace, boolean canStamp,
                          boolean canShare) implements CustomPayload {
        public static final Id<Catalog> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "blueprint_catalog"));
        public static final PacketCodec<PacketByteBuf, Catalog> CODEC = PacketCodec.ofStatic(Catalog::write,
                Catalog::read);

        public Catalog {
            blueprints = List.copyOf(blueprints);
            if (blueprints.size() > MAX_CATALOG) {
                throw new IllegalArgumentException("El catálogo admite como mucho " + MAX_CATALOG + " planos");
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(PacketByteBuf buffer, Catalog catalog) {
            buffer.writeVarInt(catalog.blueprints().size());
            catalog.blueprints().forEach(blueprint -> blueprint.write(buffer));
            buffer.writeBoolean(catalog.canSave());
            buffer.writeBoolean(catalog.canPlace());
            buffer.writeBoolean(catalog.canStamp());
            buffer.writeBoolean(catalog.canShare());
        }

        private static Catalog read(PacketByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_CATALOG) {
                throw new IllegalArgumentException("Tamaño de catálogo de planos no válido");
            }
            List<BlueprintSummary> blueprints = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                blueprints.add(BlueprintSummary.read(buffer));
            }
            return new Catalog(blueprints, buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean());
        }
    }

    /** Del servidor: un trozo de una miniatura. */
    public record Thumbnail(ThumbnailChunks.Chunk chunk) implements CustomPayload {
        public static final Id<Thumbnail> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "blueprint_thumbnail"));
        public static final PacketCodec<PacketByteBuf, Thumbnail> CODEC = PacketCodec.ofStatic(Thumbnail::write,
                Thumbnail::read);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(PacketByteBuf buffer, Thumbnail payload) {
            ThumbnailChunks.Chunk chunk = payload.chunk();
            buffer.writeString(chunk.hash(), 64);
            buffer.writeVarInt(chunk.index());
            buffer.writeVarInt(chunk.total());
            buffer.writeByteArray(chunk.data());
        }

        private static Thumbnail read(PacketByteBuf buffer) {
            String hash = buffer.readString(64);
            int index = buffer.readVarInt();
            int total = buffer.readVarInt();
            byte[] data = buffer.readByteArray(ThumbnailChunks.CHUNK_BYTES);
            return new Thumbnail(new ThumbnailChunks.Chunk(hash, index, total, data));
        }
    }
}
