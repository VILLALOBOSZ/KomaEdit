package com.komainu.moxia.views;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Los paquetes de las vistas guardadas, solo entre clientes y servidores con KomaEdit. */
public final class ViewPayloads {
    /** Vistas que caben en un catálogo: el máximo que puede tener el servidor con margen. */
    public static final int MAX_CATALOG = 2_048;
    private static final int MAX_DIMENSION = 128;

    private ViewPayloads() {
    }

    /** Del cliente: llévame a esta vista. El servidor lo comprueba todo ({@link ViewTeleport}). */
    public record ViewJumpPayload(ViewTeleport.Target target) implements CustomPayload {
        public static final Id<ViewJumpPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "view_jump"));
        public static final PacketCodec<PacketByteBuf, ViewJumpPayload> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> writeTarget(buffer, payload.target()),
                buffer -> new ViewJumpPayload(readTarget(buffer)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Del cliente: comparte con el equipo esta vista privada (con su nombre). */
    public record ViewSharePayload(String name, ViewTeleport.Target target) implements CustomPayload {
        public static final Id<ViewSharePayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "view_share"));
        public static final PacketCodec<PacketByteBuf, ViewSharePayload> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> {
                    buffer.writeString(payload.name(), ViewNames.MAX_LENGTH * 4);
                    writeTarget(buffer, payload.target());
                },
                buffer -> new ViewSharePayload(buffer.readString(ViewNames.MAX_LENGTH * 4), readTarget(buffer)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Del cliente: mándame las vistas compartidas (al abrir la lista). */
    public record ViewCatalogRequest() implements CustomPayload {
        public static final Id<ViewCatalogRequest> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID,
                "view_catalog_request"));
        public static final PacketCodec<PacketByteBuf, ViewCatalogRequest> CODEC = PacketCodec.unit(
                new ViewCatalogRequest());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Del servidor: las vistas compartidas que ve el jugador y lo que puede hacer.
     *
     * @param canTeleport    saltar a una vista de su dimensión
     * @param canChangeWorld saltar a otra dimensión
     * @param canShare       compartir
     * @param canManage      quitar las de otros
     */
    public record ViewCatalogPayload(List<SavedView> shared, boolean canTeleport, boolean canChangeWorld,
                                     boolean canShare, boolean canManage) implements CustomPayload {
        public static final Id<ViewCatalogPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "view_catalog"));
        public static final PacketCodec<PacketByteBuf, ViewCatalogPayload> CODEC = PacketCodec.ofStatic(
                ViewCatalogPayload::write, ViewCatalogPayload::read);
        public static final ViewCatalogPayload NONE = new ViewCatalogPayload(List.of(), false, false, false, false);

        public ViewCatalogPayload {
            shared = List.copyOf(shared);
            if (shared.size() > MAX_CATALOG) {
                throw new IllegalArgumentException("El catálogo admite como mucho " + MAX_CATALOG + " vistas");
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(PacketByteBuf buffer, ViewCatalogPayload payload) {
            buffer.writeVarInt(payload.shared().size());
            for (SavedView view : payload.shared()) {
                buffer.writeString(view.name(), ViewNames.MAX_LENGTH * 4);
                writeTarget(buffer, ViewTeleport.Target.of(view));
                buffer.writeString(view.author(), 64);
                buffer.writeUuid(view.authorId());
                buffer.writeLong(view.created());
            }
            buffer.writeBoolean(payload.canTeleport());
            buffer.writeBoolean(payload.canChangeWorld());
            buffer.writeBoolean(payload.canShare());
            buffer.writeBoolean(payload.canManage());
        }

        private static ViewCatalogPayload read(PacketByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_CATALOG) {
                throw new IllegalArgumentException("Tamaño de catálogo de vistas no válido");
            }
            List<SavedView> views = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                String name = buffer.readString(ViewNames.MAX_LENGTH * 4);
                ViewTeleport.Target target = readTarget(buffer);
                String author = buffer.readString(64);
                UUID authorId = buffer.readUuid();
                long created = buffer.readLong();
                views.add(new SavedView(name, target.dimension(), target.x(), target.y(), target.z(), target.yaw(),
                        target.pitch(), false, created, author, authorId));
            }
            return new ViewCatalogPayload(views, buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean());
        }
    }

    /** Del servidor: cómo fue un salto o un compartir, con el mensaje para la barra de acción. */
    public record ViewResultPayload(boolean jump, boolean ok, String message) implements CustomPayload {
        public static final Id<ViewResultPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "view_result"));
        public static final PacketCodec<PacketByteBuf, ViewResultPayload> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> {
                    buffer.writeBoolean(payload.jump());
                    buffer.writeBoolean(payload.ok());
                    buffer.writeString(payload.message(), 512);
                },
                buffer -> new ViewResultPayload(buffer.readBoolean(), buffer.readBoolean(), buffer.readString(512)));

        public ViewResultPayload {
            message = message == null ? "" : message.length() > 512 ? message.substring(0, 512) : message;
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    static void writeTarget(PacketByteBuf buffer, ViewTeleport.Target target) {
        buffer.writeString(target.dimension(), MAX_DIMENSION);
        buffer.writeDouble(target.x());
        buffer.writeDouble(target.y());
        buffer.writeDouble(target.z());
        buffer.writeFloat(target.yaw());
        buffer.writeFloat(target.pitch());
    }

    static ViewTeleport.Target readTarget(PacketByteBuf buffer) {
        return new ViewTeleport.Target(buffer.readString(MAX_DIMENSION), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readFloat(), buffer.readFloat());
    }
}
