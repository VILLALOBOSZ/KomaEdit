package com.komainu.moxia.display;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.shape.Vec3;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Paquetes del editor de displays, solo entre el servidor y un cliente con KomaEdit. El cliente elige con un rayo
 * ({@link Pick}), arrastra el gizmo él solo y al soltar manda cómo quedan ({@link Edit}); el servidor lo comprueba
 * todo y contesta con lo elegido y cómo está de verdad ({@link Selection}), que el cliente usa también para deshacer
 * lo que había previsto si algo no se aceptó.
 */
public final class DisplayPayloads {
    private static final int MAX_TEXT = 512;
    /** Más displays que esto no caben en un paquete (el límite de la selección es menor). */
    public static final int MAX_ENTRIES = 4_096;

    private DisplayPayloads() {
    }

    /** Un display y su pose, por el id de entidad de esta sesión. */
    public record Entry(int entityId, DisplayPose pose) {
    }

    /** El jugador pulsa un display ({@code entityId} −1: soltar todo). */
    public record Pick(int entityId, DisplaySelection.Mode mode, boolean single) implements CustomPayload {
        public static final Id<Pick> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "display_pick"));
        public static final PacketCodec<RegistryByteBuf, Pick> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.entityId() + 1);
                    buffer.writeByte(payload.mode().ordinal());
                    buffer.writeBoolean(payload.single());
                },
                buffer -> new Pick(buffer.readVarInt() - 1, modeOf(buffer.readByte()), buffer.readBoolean()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Qué fue la edición, para el historial y los mensajes. */
    public enum Operation {
        MOVE("Mover displays"),
        ROTATE("Girar displays"),
        SCALE("Escalar displays"),
        PASTE("Pegar transformación"),
        NUDGE("Mover displays");

        private final String label;

        Operation(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        static Operation of(int ordinal) {
            Operation[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MOVE;
        }
    }

    /** Así quedan los elegidos tras soltar el gizmo. Todo o nada: si uno no vale, no se toca ninguno. */
    public record Edit(Operation operation, List<Entry> entries) implements CustomPayload {
        public static final Id<Edit> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "display_edit"));
        public static final PacketCodec<RegistryByteBuf, Edit> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> {
                    buffer.writeByte(payload.operation().ordinal());
                    writeEntries(buffer, payload.entries());
                },
                buffer -> new Edit(Operation.of(buffer.readByte()), readEntries(buffer)));

        public Edit {
            entries = List.copyOf(entries);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Lo que se puede pedir con lo elegido sin mover el gizmo. */
    public enum Action {
        DELETE,
        DUPLICATE,
        GROUP,
        UNGROUP,
        CLEAR,
        /** Salir del editor. */
        CLOSE;

        static Action of(int ordinal) {
            Action[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CLEAR;
        }
    }

    public record Act(Action action) implements CustomPayload {
        public static final Id<Act> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "display_action"));
        public static final PacketCodec<RegistryByteBuf, Act> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> buffer.writeByte(payload.action().ordinal()),
                buffer -> new Act(Action.of(buffer.readByte())));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Cómo acabó lo último que pidió el jugador. */
    public enum Outcome {
        /** Nada que contar (al abrir el editor o al elegir). */
        INFO,
        APPLIED,
        /** No se aplicó: el cliente vuelve a {@link Selection#entries()}. */
        REJECTED
    }

    /**
     * Lo que el jugador tiene elegido, con la pose real de cada uno, y si el editor está abierto. {@code primary} es
     * el id de entidad del activo (−1 sin selección). {@code groups} dice cuántos de los elegidos van en grupo.
     */
    public record Selection(boolean editing, int primary, List<Entry> entries, int grouped, Outcome outcome,
                            String message) implements CustomPayload {
        public static final Id<Selection> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "display_selection"));
        public static final PacketCodec<RegistryByteBuf, Selection> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> {
                    buffer.writeBoolean(payload.editing());
                    buffer.writeVarInt(payload.primary() + 1);
                    writeEntries(buffer, payload.entries());
                    buffer.writeVarInt(payload.grouped());
                    buffer.writeByte(payload.outcome().ordinal());
                    buffer.writeString(clip(payload.message()), MAX_TEXT);
                },
                buffer -> new Selection(buffer.readBoolean(), buffer.readVarInt() - 1, readEntries(buffer),
                        buffer.readVarInt(), outcomeOf(buffer.readByte()), buffer.readString(MAX_TEXT)));

        public Selection {
            entries = List.copyOf(entries);
            message = message == null ? "" : message;
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // ---------------------------------------------------------------- codificación

    static void writeEntries(RegistryByteBuf buffer, List<Entry> entries) {
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            Entry entry = entries.get(index);
            buffer.writeVarInt(entry.entityId());
            writePose(buffer, entry.pose());
        }
    }

    static List<Entry> readEntries(RegistryByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Demasiados displays en un paquete: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buffer.readVarInt(), readPose(buffer)));
        }
        return entries;
    }

    /** Posición en dobles; la transformación en flotantes, como la guarda Minecraft. */
    static void writePose(RegistryByteBuf buffer, DisplayPose pose) {
        buffer.writeDouble(pose.position().x());
        buffer.writeDouble(pose.position().y());
        buffer.writeDouble(pose.position().z());
        buffer.writeFloat((float) pose.yaw());
        buffer.writeFloat((float) pose.pitch());
        writeVector(buffer, pose.translation());
        writeQuat(buffer, pose.left());
        writeVector(buffer, pose.scale());
        writeQuat(buffer, pose.right());
        buffer.writeByte(pose.billboard().ordinal());
    }

    static DisplayPose readPose(RegistryByteBuf buffer) {
        Vec3 position = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        double yaw = buffer.readFloat();
        double pitch = buffer.readFloat();
        Vec3 translation = readVector(buffer);
        Quat left = readQuat(buffer);
        Vec3 scale = readVector(buffer);
        Quat right = readQuat(buffer);
        return new DisplayPose(position, yaw, pitch, translation, left, scale, right,
                DisplayPose.Billboard.of(buffer.readByte()));
    }

    private static void writeVector(RegistryByteBuf buffer, Vec3 vector) {
        buffer.writeFloat((float) vector.x());
        buffer.writeFloat((float) vector.y());
        buffer.writeFloat((float) vector.z());
    }

    private static Vec3 readVector(RegistryByteBuf buffer) {
        return new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    private static void writeQuat(RegistryByteBuf buffer, Quat quat) {
        buffer.writeFloat((float) quat.x());
        buffer.writeFloat((float) quat.y());
        buffer.writeFloat((float) quat.z());
        buffer.writeFloat((float) quat.w());
    }

    private static Quat readQuat(RegistryByteBuf buffer) {
        return new Quat(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    private static DisplaySelection.Mode modeOf(int ordinal) {
        DisplaySelection.Mode[] values = DisplaySelection.Mode.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DisplaySelection.Mode.REPLACE;
    }

    private static Outcome outcomeOf(int ordinal) {
        Outcome[] values = Outcome.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Outcome.INFO;
    }

    private static String clip(String text) {
        String value = text == null ? "" : text;
        return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value;
    }
}
