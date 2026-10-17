package com.komainu.moxia.gradient;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Un bloque concreto de una paleta: id con espacio de nombres y las propiedades escritas entre corchetes. Las
 * propiedades que no se escriben quedan libres; así {@code oak_stairs} encaja con cualquier escalera de roble y
 * {@code oak_stairs[facing=east]} solo con las que miran al este.
 */
public record BlockSpec(String id, Map<String, String> properties) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
    private static final Pattern KEY = Pattern.compile("[a-z0-9_]+");

    public BlockSpec {
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static BlockSpec parse(String input) {
        String text = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        int open = text.indexOf('[');
        String id = open < 0 ? text : text.substring(0, open);
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("«" + input + "» no es un bloque (aquí la paleta solo admite bloques, "
                    + "sin porcentajes ni patrones)");
        }
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        Map<String, String> properties = new LinkedHashMap<>();
        if (open >= 0) {
            if (!text.endsWith("]")) {
                throw new IllegalArgumentException("Falta el ] de las propiedades en «" + input + "»");
            }
            String body = text.substring(open + 1, text.length() - 1).trim();
            if (!body.isEmpty()) {
                for (String pair : body.split(",")) {
                    String[] parts = pair.split("=", 2);
                    if (parts.length != 2 || !KEY.matcher(parts[0].trim()).matches() || parts[1].isBlank()) {
                        throw new IllegalArgumentException("Propiedad inválida «" + pair.trim() + "» en «" + input + "»");
                    }
                    properties.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return new BlockSpec(id, properties);
    }

    public boolean isAir() {
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    /**
     * ¿Encaja un estado con este bloque?
     *
     * @param strict si es {@code false} solo cuenta el tipo de bloque; si es {@code true}, además, cada propiedad
     *               escrita debe coincidir
     */
    public boolean matches(String stateId, Map<String, String> stateProperties, boolean strict) {
        if (!id.equals(stateId)) {
            return false;
        }
        if (!strict) {
            return true;
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!entry.getValue().equals(stateProperties.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /** Sin el espacio de nombres {@code minecraft:} ni propiedades. */
    public String shortName() {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

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
