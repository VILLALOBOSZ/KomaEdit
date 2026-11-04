package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Estados de bloque entre el texto de WorldEdit ({@code minecraft:oak_stairs[facing=east,half=bottom]}) y el NBT de
 * Minecraft, Litematica y las estructuras ({@code {Name: "minecraft:oak_stairs", Properties: {facing: "east"}}}). Las
 * propiedades se ordenan por nombre, así un mismo estado se escribe siempre igual.
 */
public final class BlockStates {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");
    private static final Pattern KEY = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern VALUE = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final int MAX_PROPERTIES = 32;
    /** El aviso de un estado que no se entiende. */
    static final String UNREADABLE = "Estados de bloque que no se entienden, cambiados por aire";

    private BlockStates() {
    }

    /** Un estado entendido: id con espacio de nombres y sus propiedades por orden. */
    public record Parsed(String id, Map<String, String> properties) {
        @Override
        public String toString() {
            if (properties.isEmpty()) {
                return id;
            }
            StringJoiner joiner = new StringJoiner(",", id + "[", "]");
            properties.forEach((key, value) -> joiner.add(key + "=" + value));
            return joiner.toString();
        }
    }

    public static boolean isAir(String state) {
        return state.equals(SchematicData.AIR) || state.equals("minecraft:cave_air")
                || state.equals("minecraft:void_air");
    }

    /** El texto de un estado; lanza si no se entiende (nombres raros, propiedades sin valor…). */
    public static Parsed parse(String state) {
        String text = state == null ? "" : state.strip();
        int open = text.indexOf('[');
        String id = open < 0 ? text : text.substring(0, open);
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("estado de bloque no válido");
        }
        Map<String, String> properties = new TreeMap<>();
        if (open >= 0) {
            if (!text.endsWith("]")) {
                throw new IllegalArgumentException("estado de bloque no válido");
            }
            String body = text.substring(open + 1, text.length() - 1);
            if (!body.isBlank()) {
                for (String pair : body.split(",", MAX_PROPERTIES + 1)) {
                    String[] parts = pair.split("=", 2);
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("estado de bloque no válido");
                    }
                    put(properties, parts[0].strip(), parts[1].strip());
                }
            }
        }
        return new Parsed(id, properties);
    }

    /** El mismo estado escrito siempre igual. */
    public static String canonical(String state) {
        return parse(state).toString();
    }

    /**
     * Un estado de NBT: {@code Name} y {@code Properties} (también {@code id} y {@code properties}, por si cambian de
     * nombre). Lanza si no se entiende.
     */
    public static String fromNbt(NbtCompound tag) {
        String nameKey = tag.contains("Name", NbtElement.STRING_TYPE) ? "Name" : "id";
        String propertiesKey = tag.contains("Properties", NbtElement.COMPOUND_TYPE) ? "Properties" : "properties";
        if (!tag.contains(nameKey, NbtElement.STRING_TYPE)) {
            throw new IllegalArgumentException("estado de bloque sin nombre");
        }
        String id = tag.getString(nameKey);
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("estado de bloque no válido");
        }
        Map<String, String> properties = new TreeMap<>();
        if (tag.contains(propertiesKey, NbtElement.COMPOUND_TYPE)) {
            NbtCompound values = tag.getCompound(propertiesKey);
            if (values.getSize() > MAX_PROPERTIES) {
                throw new IllegalArgumentException("estado de bloque con demasiadas propiedades");
            }
            for (String key : values.getKeys()) {
                if (values.getType(key) != NbtElement.STRING_TYPE) {
                    throw new IllegalArgumentException("propiedad de bloque no válida");
                }
                put(properties, key, values.getString(key));
            }
        }
        return new Parsed(id, properties).toString();
    }

    /** {@code {Name, Properties}}, como lo escriben Minecraft y Litematica. */
    public static NbtCompound toNbt(String state) {
        Parsed parsed = parse(state);
        NbtCompound tag = new NbtCompound();
        tag.putString("Name", parsed.id());
        if (!parsed.properties().isEmpty()) {
            NbtCompound properties = new NbtCompound();
            parsed.properties().forEach(properties::putString);
            tag.put("Properties", properties);
        }
        return tag;
    }

    /** Un aviso, sin repetirlo. */
    static void warn(List<String> warnings, String message) {
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    private static void put(Map<String, String> properties, String key, String value) {
        if (!KEY.matcher(key).matches() || !VALUE.matcher(value).matches() || properties.size() >= MAX_PROPERTIES) {
            throw new IllegalArgumentException("propiedad de bloque no válida");
        }
        properties.put(key, value);
    }
}
