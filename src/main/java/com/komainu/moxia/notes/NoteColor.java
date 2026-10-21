package com.komainu.moxia.notes;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Colores con nombre para las notas (o {@code #RRGGBB}). Son colores de dato, no de interfaz: los elige quien escribe
 * la nota y se leen sobre el fondo oscuro del rótulo.
 */
public enum NoteColor {
    AMARILLO("amarillo", 0xFFD54F, "yellow"),
    NARANJA("naranja", 0xFFA74D, "orange"),
    ROJO("rojo", 0xFF6B6B, "red"),
    ROSA("rosa", 0xF48FB1, "pink"),
    MORADO("morado", 0xB59DFF, "purple"),
    AZUL("azul", 0x64B5F6, "blue"),
    CIAN("cian", 0x4DD0E1, "cyan"),
    VERDE("verde", 0x81C784, "green"),
    BLANCO("blanco", 0xF2F2F2, "white");

    private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");
    private final String id;
    private final int rgb;
    private final String english;

    NoteColor(String id, int rgb, String english) {
        this.id = id;
        this.rgb = rgb;
        this.english = english;
    }

    public String id() {
        return id;
    }

    public int rgb() {
        return rgb;
    }

    public static List<String> ids() {
        return java.util.Arrays.stream(values()).map(NoteColor::id).toList();
    }

    /** El RGB de un nombre ({@code rojo}, {@code red}) o de {@code #RRGGBB}; lanza si no es ninguno. */
    public static int parse(String value) {
        String key = value.strip().toLowerCase(Locale.ROOT);
        for (NoteColor color : values()) {
            if (color.id.equals(key) || color.english.equals(key)) {
                return color.rgb;
            }
        }
        if (HEX.matcher(key).matches()) {
            return Integer.parseInt(key.startsWith("#") ? key.substring(1) : key, 16);
        }
        throw new IllegalArgumentException("Color de nota desconocido: " + value + " (usa " + String.join(", ", ids())
                + " o #RRGGBB)");
    }

    /** El nombre de un RGB si es uno de la lista; si no, {@code #rrggbb}. */
    public static String name(int rgb) {
        for (NoteColor color : values()) {
            if (color.rgb == (rgb & 0xFFFFFF)) {
                return color.id;
            }
        }
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }
}
