package com.komainu.moxia.studio.net;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lo que el Studio necesita saber del servidor para las herramientas. Solo sirve para enseñar: al construir el
 * servidor vuelve a comprobarlo todo.
 *
 * @param granted           nodos con permiso de entre los que piden las tarjetas
 * @param selection         tamaño de la selección de WorldEdit (x, y, z), o vacío si no hay
 * @param fonts             fuentes propias de {@code config/komaedit/fonts/}
 * @param treePresets       presets de árbol guardados del jugador (nombre → ajustes)
 * @param rockPresets       presets de roca guardados
 * @param symmetryMode      modo de simetría ({@code xz}, {@code radial 8 y}…)
 * @param symmetryCenter    centro escrito como en el chat, o vacío sin centro
 * @param symmetryElsewhere el centro está en otra dimensión
 */
public record ToolStatePayload(
        List<String> granted,
        int[] selection,
        List<String> fonts,
        Map<String, String> treePresets,
        Map<String, String> rockPresets,
        boolean symmetryActive,
        String symmetryMode,
        int symmetryArms,
        String symmetryCenter,
        boolean symmetryWorldEdit,
        boolean symmetryElsewhere
) implements CustomPayload {
    public static final Id<ToolStatePayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "tool_state"));
    public static final PacketCodec<RegistryByteBuf, ToolStatePayload> CODEC = PacketCodec.ofStatic(
            ToolStatePayload::write, ToolStatePayload::read);
    private static final int MAX_ITEMS = 256;

    public ToolStatePayload {
        granted = List.copyOf(granted);
        selection = selection == null || selection.length != 3 ? new int[0] : selection.clone();
        fonts = List.copyOf(fonts);
        treePresets = new LinkedHashMap<>(treePresets);
        rockPresets = new LinkedHashMap<>(rockPresets);
        symmetryMode = symmetryMode == null ? "" : symmetryMode;
        symmetryCenter = symmetryCenter == null ? "" : symmetryCenter;
    }

    public boolean hasSelection() {
        return selection.length == 3;
    }

    public long selectionVolume() {
        return hasSelection() ? (long) selection[0] * selection[1] * selection[2] : 0L;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ToolStatePayload that && granted.equals(that.granted)
                && Arrays.equals(selection, that.selection) && fonts.equals(that.fonts)
                && treePresets.equals(that.treePresets) && rockPresets.equals(that.rockPresets)
                && symmetryActive == that.symmetryActive && symmetryMode.equals(that.symmetryMode)
                && symmetryArms == that.symmetryArms && symmetryCenter.equals(that.symmetryCenter)
                && symmetryWorldEdit == that.symmetryWorldEdit && symmetryElsewhere == that.symmetryElsewhere;
    }

    @Override
    public int hashCode() {
        return Objects.hash(granted, Arrays.hashCode(selection), fonts, treePresets, rockPresets, symmetryActive,
                symmetryMode, symmetryArms, symmetryCenter, symmetryWorldEdit, symmetryElsewhere);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, ToolStatePayload payload) {
        strings(buffer, payload.granted());
        buffer.writeBoolean(payload.hasSelection());
        if (payload.hasSelection()) {
            for (int size : payload.selection()) {
                buffer.writeVarInt(size);
            }
        }
        strings(buffer, payload.fonts());
        presets(buffer, payload.treePresets());
        presets(buffer, payload.rockPresets());
        buffer.writeBoolean(payload.symmetryActive());
        buffer.writeString(payload.symmetryMode(), 64);
        buffer.writeVarInt(payload.symmetryArms());
        buffer.writeString(payload.symmetryCenter(), 128);
        buffer.writeBoolean(payload.symmetryWorldEdit());
        buffer.writeBoolean(payload.symmetryElsewhere());
    }

    private static ToolStatePayload read(RegistryByteBuf buffer) {
        List<String> granted = strings(buffer);
        int[] selection = buffer.readBoolean()
                ? new int[]{buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()} : new int[0];
        List<String> fonts = strings(buffer);
        Map<String, String> trees = presets(buffer);
        Map<String, String> rocks = presets(buffer);
        return new ToolStatePayload(granted, selection, fonts, trees, rocks, buffer.readBoolean(),
                buffer.readString(64), buffer.readVarInt(), buffer.readString(128), buffer.readBoolean(),
                buffer.readBoolean());
    }

    private static void strings(RegistryByteBuf buffer, List<String> values) {
        List<String> limited = values.size() > MAX_ITEMS ? values.subList(0, MAX_ITEMS) : values;
        buffer.writeVarInt(limited.size());
        limited.forEach(value -> buffer.writeString(value, 128));
    }

    private static List<String> strings(RegistryByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), MAX_ITEMS);
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(buffer.readString(128));
        }
        return values;
    }

    private static void presets(RegistryByteBuf buffer, Map<String, String> presets) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(presets.entrySet());
        if (entries.size() > MAX_ITEMS) {
            entries = entries.subList(0, MAX_ITEMS);
        }
        buffer.writeVarInt(entries.size());
        for (Map.Entry<String, String> entry : entries) {
            buffer.writeString(entry.getKey(), 64);
            buffer.writeString(entry.getValue(), 1024);
        }
    }

    private static Map<String, String> presets(RegistryByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), MAX_ITEMS);
        Map<String, String> presets = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            presets.put(buffer.readString(64), buffer.readString(1024));
        }
        return presets;
    }
}
