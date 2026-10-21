package com.komainu.moxia.hotbar;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lo que hay en un hueco de una barra guardada, sin Minecraft: el objeto, cuántos y sus componentes como el JSON que
 * escribe {@code ItemStack.CODEC} (vacío si no lleva ninguno). Los componentes van con las claves ordenadas
 * ({@link HotbarJson#canonical}), así que dos objetos iguales dan el mismo texto y se comparan como texto.
 *
 * @param id         objeto con espacio de nombres ({@code minecraft:stone}); el aire es el hueco vacío
 * @param count      de 1 a {@link #MAX_COUNT} (0 en el hueco vacío)
 * @param components objeto JSON de componentes, compacto y ordenado, o {@code ""}
 */
public record HotbarItem(String id, int count, String components) {
    // Antes que EMPTY: su constructor ya lo usa.
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public static final String AIR = "minecraft:air";
    /** El tope de Minecraft para cualquier pila ({@code max_stack_size} admite hasta 99). */
    public static final int MAX_COUNT = 99;
    public static final HotbarItem EMPTY = new HotbarItem(AIR, 0, "");

    public HotbarItem {
        id = id == null || id.isBlank() ? AIR : normalize(id);
        components = components == null ? "" : components.strip();
        if (components.equals("{}")) {
            components = "";
        }
        if (id.equals(AIR) || count <= 0) {
            id = AIR;
            count = 0;
            components = "";
        } else {
            count = Math.min(count, MAX_COUNT);
        }
    }

    public static HotbarItem of(String id, int count) {
        return new HotbarItem(id, count, "");
    }

    public static HotbarItem of(String id, int count, String components) {
        return new HotbarItem(id, count, components);
    }

    public boolean isEmpty() {
        return count == 0;
    }

    /** Mismo objeto, sin mirar cuántos ni los componentes (un pico gastado y uno nuevo). */
    public boolean sameItem(HotbarItem other) {
        return !isEmpty() && other != null && !other.isEmpty() && id.equals(other.id);
    }

    /** Mismo objeto con los mismos componentes; la cantidad no cuenta. */
    public boolean sameStack(HotbarItem other) {
        return sameItem(other) && components.equals(other.components);
    }

    public HotbarItem withCount(int value) {
        return new HotbarItem(id, value, components);
    }

    /** Sin {@code minecraft:}. */
    public String shortId() {
        return shortId(id);
    }

    public static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    /** Id en minúsculas y con espacio de nombres; error si no es un id de objeto. */
    public static String normalize(String id) {
        String text = id.strip().toLowerCase(Locale.ROOT);
        if (text.indexOf(':') < 0) {
            text = "minecraft:" + text;
        }
        if (!ID.matcher(text).matches()) {
            throw new IllegalArgumentException("«" + id + "» no es un objeto");
        }
        return text;
    }
}
