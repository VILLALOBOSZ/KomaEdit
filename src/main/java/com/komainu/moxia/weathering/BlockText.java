package com.komainu.moxia.weathering;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Estado de bloque como texto, sin Minecraft: {@code minecraft:oak_stairs[facing=north,half=bottom]}. El motor de
 * envejecimiento trabaja solo con esto para poder probarse sin servidor.
 */
public record BlockText(String namespace, String path, Map<String, String> properties) {
    public static final BlockText AIR = parse("minecraft:air");

    public BlockText {
        properties = Map.copyOf(properties);
    }

    public static BlockText parse(String text) {
        String value = text.strip().toLowerCase(Locale.ROOT);
        String id = value;
        Map<String, String> properties = new TreeMap<>();
        int open = value.indexOf('[');
        if (open >= 0) {
            if (!value.endsWith("]")) {
                throw new IllegalArgumentException("Estado de bloque mal escrito: " + text);
            }
            id = value.substring(0, open);
            String inside = value.substring(open + 1, value.length() - 1);
            if (!inside.isBlank()) {
                for (String pair : inside.split(",")) {
                    int equals = pair.indexOf('=');
                    if (equals <= 0) {
                        throw new IllegalArgumentException("Propiedad mal escrita en " + text);
                    }
                    properties.put(pair.substring(0, equals).strip(), pair.substring(equals + 1).strip());
                }
            }
        }
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Estado de bloque vacío");
        }
        return new BlockText(namespace, path, properties);
    }

    public static BlockText of(String path, String... keyValues) {
        Map<String, String> properties = new TreeMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            properties.put(keyValues[index], keyValues[index + 1]);
        }
        return new BlockText("minecraft", path, properties);
    }

    public boolean vanilla() {
        return namespace.equals("minecraft");
    }

    public String id() {
        return namespace + ":" + path;
    }

    public String property(String key) {
        return properties.get(key);
    }

    /** Otro bloque con las mismas propiedades (una escalera musgosa conserva su orientación). */
    public BlockText withPath(String newPath) {
        return new BlockText(namespace, newPath, properties);
    }

    /** Cambia la propiedad solo si el estado ya la tenía; así nunca se inventan propiedades que el bloque no admite. */
    public BlockText withExisting(String key, String value) {
        if (!properties.containsKey(key) || value.equals(properties.get(key))) {
            return this;
        }
        Map<String, String> copy = new TreeMap<>(properties);
        copy.put(key, value);
        return new BlockText(namespace, path, copy);
    }

    @Override
    public String toString() {
        if (properties.isEmpty()) {
            return id();
        }
        StringBuilder builder = new StringBuilder(id()).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : new TreeMap<>(properties).entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.append(']').toString();
    }
}
