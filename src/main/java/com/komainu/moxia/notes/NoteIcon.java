package com.komainu.moxia.notes;

import com.komainu.moxia.feedback.WorldSketch;

import java.util.List;
import java.util.Locale;

/**
 * El icono de una nota, en píxeles (9×9) para que se vea igual con cualquier paquete de recursos: no depende de los
 * glifos de la fuente, que un servidor puede cambiar.
 */
public enum NoteIcon {
    NOTA("nota", List.of("note", "info"),
            "#######..",
            "#.....##.",
            "#.###..##",
            "#......##",
            "#.####.##",
            "#......##",
            "#.###..##",
            "#......##",
            "#########"),
    AVISO("aviso", List.of("warning", "alerta", "cuidado"),
            "....#....",
            "...###...",
            "...#.#...",
            "..##.##..",
            "..#...#..",
            ".##.#.##.",
            ".#.....#.",
            "##..#..##",
            "#########"),
    TAREA("tarea", List.of("todo", "task", "pendiente"),
            "#########",
            "#.......#",
            "#......##",
            "#.....#.#",
            "#.#..#..#",
            "#..##...#",
            "#...#...#",
            "#.......#",
            "#########"),
    IDEA("idea", List.of("bulb", "bombilla"),
            "..#####..",
            ".#.....#.",
            "#...#...#",
            "#..#.#..#",
            "#...#...#",
            ".#..#..#.",
            "..#####..",
            "...###...",
            "....#...."),
    PREGUNTA("pregunta", List.of("question", "duda"),
            "..#####..",
            ".##...##.",
            ".##...##.",
            ".....##..",
            "....##...",
            "....##...",
            ".........",
            "....##...",
            "....##..."),
    AQUI("aqui", List.of("here", "flecha", "arrow", "marca"),
            "...###...",
            "...###...",
            "...###...",
            "...###...",
            "#########",
            ".#######.",
            "..#####..",
            "...###...",
            "....#...."),
    ESTRELLA("estrella", List.of("star", "favorito"),
            "....#....",
            "....#....",
            "...###...",
            "#########",
            ".#######.",
            "..#####..",
            "..##.##..",
            ".##...##.",
            ".#.....#."),
    ERROR("error", List.of("x", "mal", "quitar", "remove"),
            "##.....##",
            "###...###",
            ".###.###.",
            "..#####..",
            "...###...",
            "..#####..",
            ".###.###.",
            "###...###",
            "##.....##");

    private final String id;
    private final List<String> aliases;
    private final WorldSketch.PixelIcon pixels;

    NoteIcon(String id, List<String> aliases, String... mask) {
        this.id = id;
        this.aliases = List.copyOf(aliases);
        this.pixels = new WorldSketch.PixelIcon(List.of(mask));
    }

    public String id() {
        return id;
    }

    public WorldSketch.PixelIcon pixels() {
        return pixels;
    }

    public String translationKey() {
        return "gui.komaedit.notes.icon." + id;
    }

    public static List<String> ids() {
        return java.util.Arrays.stream(values()).map(NoteIcon::id).toList();
    }

    /** El icono por su nombre o un alias; lanza si no es ninguno. */
    public static NoteIcon parse(String value) {
        String key = value.strip().toLowerCase(Locale.ROOT).replace("í", "i").replace("é", "e");
        for (NoteIcon icon : values()) {
            if (icon.id.equals(key) || icon.name().equalsIgnoreCase(key) || icon.aliases.contains(key)) {
                return icon;
            }
        }
        throw new IllegalArgumentException("Icono de nota desconocido: " + value + " (usa " + String.join(", ", ids())
                + ")");
    }

    /** Como {@link #parse}, pero {@code NOTA} si no lo conoce (al leer un archivo de otra versión). */
    public static NoteIcon byId(String value) {
        try {
            return parse(value);
        } catch (IllegalArgumentException exception) {
            return NOTA;
        }
    }
}
