package com.komainu.moxia.hotbar;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Los paquetes de las barras guardadas. Cambiar de barra va por paquete y no por orden de chat: cada orden suma al
 * antispam de Minecraft, que echa a quien no es OP, y la rueda de la tira cambia muchas veces seguidas.
 */
public final class HotbarPayloads {
    private static final int MAX_NAME = 64;

    private HotbarPayloads() {
    }

    /**
     * Del cliente: lo que pide hacer con sus barras. El servidor lo comprueba todo; para cambiar de barra el cliente
     * solo dice cuál, y el servidor pone su copia guardada (nadie puede colar objetos con un paquete hecho a mano).
     *
     * @param name  barra (vacío en {@code SYNC}, {@code UNDO} y {@code AUTOSAVE}; en {@code EXPORT}, todas)
     * @param text  código {@code kh1:} en {@code IMPORT}; icono en {@code SAVE} (hueco 1–9 u objeto, o vacío)
     * @param value 1/0: sustituir en {@code SAVE} e {@code IMPORT}; activar en {@code AUTOSAVE}
     */
    public record HotbarActionPayload(Kind kind, String name, String text, int value) implements CustomPayload {
        public enum Kind {
            SYNC,
            APPLY,
            SAVE,
            DELETE,
            EXPORT,
            IMPORT,
            UNDO,
            AUTOSAVE
        }

        public static final Id<HotbarActionPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "hotbar_action"));
        public static final PacketCodec<PacketByteBuf, HotbarActionPayload> CODEC = PacketCodec.ofStatic(
                HotbarActionPayload::write, HotbarActionPayload::read);

        public HotbarActionPayload {
            kind = kind == null ? Kind.SYNC : kind;
            name = name == null ? "" : name;
            text = text == null ? "" : text;
        }

        public static HotbarActionPayload of(Kind kind, String name) {
            return new HotbarActionPayload(kind, name, "", 0);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(PacketByteBuf buffer, HotbarActionPayload payload) {
            buffer.writeVarInt(payload.kind().ordinal());
            buffer.writeString(payload.name(), MAX_NAME);
            buffer.writeString(payload.text(), HotbarCodec.MAX_TEXT);
            buffer.writeVarInt(payload.value());
        }

        private static HotbarActionPayload read(PacketByteBuf buffer) {
            Kind[] kinds = Kind.values();
            int ordinal = buffer.readVarInt();
            return new HotbarActionPayload(ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : Kind.SYNC,
                    buffer.readString(MAX_NAME), buffer.readString(HotbarCodec.MAX_TEXT), buffer.readVarInt());
        }
    }

    /**
     * Del servidor: las barras del jugador para la tira, el menú y el Studio. Con {@code full} lleva todas; si no,
     * solo las que cambiaron (las demás se quedan como las tenía el cliente). {@code order} lleva siempre todos los
     * nombres en su orden: lo que no está en ella se ha borrado.
     *
     * @param permitted tiene el permiso de las barras
     * @param give      tiene el de ponerlas como en creativo en cualquier modo
     */
    public record HotbarSyncPayload(boolean full, String active, boolean autosave, boolean permitted, boolean give,
                                    List<String> order, List<Entry> entries) implements CustomPayload {
        /** Una barra: nombre, objeto del icono y los nueve huecos (vacíos los que no existen en el servidor). */
        public record Entry(String name, String icon, List<ItemStack> slots) {
            public Entry {
                slots = List.copyOf(slots);
                if (slots.size() != SavedHotbar.SLOTS) {
                    throw new IllegalArgumentException("Una barra tiene " + SavedHotbar.SLOTS + " huecos");
                }
            }
        }

        public static final Id<HotbarSyncPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "hotbar_sync"));
        public static final PacketCodec<RegistryByteBuf, HotbarSyncPayload> CODEC = PacketCodec.ofStatic(
                HotbarSyncPayload::write, HotbarSyncPayload::read);

        public HotbarSyncPayload {
            order = List.copyOf(order);
            entries = List.copyOf(entries);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(RegistryByteBuf buffer, HotbarSyncPayload payload) {
            buffer.writeBoolean(payload.full());
            buffer.writeString(payload.active(), MAX_NAME);
            buffer.writeBoolean(payload.autosave());
            buffer.writeBoolean(payload.permitted());
            buffer.writeBoolean(payload.give());
            buffer.writeVarInt(payload.order().size());
            payload.order().forEach(name -> buffer.writeString(name, MAX_NAME));
            buffer.writeVarInt(payload.entries().size());
            for (Entry entry : payload.entries()) {
                buffer.writeString(entry.name(), MAX_NAME);
                buffer.writeString(entry.icon(), 256);
                for (ItemStack stack : entry.slots()) {
                    ItemStack.OPTIONAL_PACKET_CODEC.encode(buffer, stack);
                }
            }
        }

        private static HotbarSyncPayload read(RegistryByteBuf buffer) {
            boolean full = buffer.readBoolean();
            String active = buffer.readString(MAX_NAME);
            boolean autosave = buffer.readBoolean();
            boolean permitted = buffer.readBoolean();
            boolean give = buffer.readBoolean();
            int names = count(buffer.readVarInt());
            List<String> order = new ArrayList<>(names);
            for (int index = 0; index < names; index++) {
                order.add(buffer.readString(MAX_NAME));
            }
            int size = count(buffer.readVarInt());
            List<Entry> entries = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                String name = buffer.readString(MAX_NAME);
                String icon = buffer.readString(256);
                List<ItemStack> slots = new ArrayList<>(SavedHotbar.SLOTS);
                for (int slot = 0; slot < SavedHotbar.SLOTS; slot++) {
                    slots.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buffer));
                }
                entries.add(new Entry(name, icon, slots));
            }
            return new HotbarSyncPayload(full, active, autosave, permitted, give, order, entries);
        }

        private static int count(int value) {
            if (value < 0 || value > HotbarBook.MAX_HOTBARS) {
                throw new IllegalArgumentException("Lista de barras no válida");
            }
            return value;
        }
    }

    /** Del servidor: un código {@code kh1:} para el portapapeles del cliente (Exportar en el Studio o el menú). */
    public record HotbarTextPayload(String text, int hotbars) implements CustomPayload {
        public static final Id<HotbarTextPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "hotbar_text"));
        public static final PacketCodec<PacketByteBuf, HotbarTextPayload> CODEC = PacketCodec.ofStatic(
                HotbarTextPayload::write, HotbarTextPayload::read);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        private static void write(PacketByteBuf buffer, HotbarTextPayload payload) {
            buffer.writeString(payload.text(), 1 << 20);
            buffer.writeVarInt(payload.hotbars());
        }

        private static HotbarTextPayload read(PacketByteBuf buffer) {
            return new HotbarTextPayload(buffer.readString(1 << 20), buffer.readVarInt());
        }
    }
}
