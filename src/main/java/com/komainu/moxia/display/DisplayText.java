package com.komainu.moxia.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * El texto de un display de texto como lo escribe el jugador: banderas al principio y después el texto.
 *
 * <pre>/komaedit display text [-c color] [-f fondo] [-s] [-a izq|centro|der] [-w ancho] [-t] &lt;texto&gt;</pre>
 *
 * <ul>
 *     <li>{@code -c}: color del texto, {@code #RRGGBB} o un nombre ({@code rojo}, {@code gold}…).</li>
 *     <li>{@code -f}: fondo, {@code #AARRGGBB}, {@code #RRGGBB} (con el alfa de serie), {@code no} o {@code serie}.</li>
 *     <li>{@code -s}: sombra; {@code -t}: se ve a través de los bloques; {@code -w}: ancho de línea en píxeles.</li>
 *     <li>En el texto, {@code \n} parte la línea y {@code &} da formato como en los carteles de muchos servidores:
 *     {@code &c} rojo, {@code &l} negrita, {@code &o} cursiva, {@code &n} subrayado, {@code &m} tachado, {@code &r}
 *     quita el formato; {@code &&} es un {@code &}.</li>
 * </ul>
 *
 * Lógica pura: el servidor convierte los {@link Span} en un {@code Text} de Minecraft.
 */
public record DisplayText(List<Span> spans, String plain, int color, Background background, int backgroundArgb,
                          boolean shadow, Alignment alignment, int lineWidth, boolean seeThrough) {
    public static final int DEFAULT_COLOR = 0xFFFFFF;
    /** El fondo de serie de Minecraft (negro al 25 %). */
    public static final int DEFAULT_BACKGROUND = 0x40000000;
    public static final int DEFAULT_LINE_WIDTH = 200;
    public static final int MIN_LINE_WIDTH = 10;
    public static final int MAX_LINE_WIDTH = 2_000;

    /** Un trozo con el mismo formato; {@code color} −1 es el de todo el texto. */
    public record Span(String text, int color, boolean bold, boolean italic, boolean underline, boolean strike,
                       boolean obfuscated) {
    }

    public enum Background {
        /** El de serie de Minecraft (sigue el ajuste de fondo de texto de cada jugador). */
        DEFAULT,
        NONE,
        CUSTOM
    }

    public enum Alignment {
        CENTER("center"),
        LEFT("left"),
        RIGHT("right");

        private final String id;

        Alignment(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Alignment parse(String text) {
            return switch (text.strip().toLowerCase(Locale.ROOT)) {
                case "center", "centro", "centrado", "c" -> CENTER;
                case "left", "izq", "izquierda", "l", "i" -> LEFT;
                case "right", "der", "derecha", "r", "d" -> RIGHT;
                default -> throw new IllegalArgumentException("Alineación desconocida: " + text
                        + " (izq, centro o der)");
            };
        }
    }

    /** Los 16 colores de Minecraft, con sus nombres en inglés y en español. */
    public static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("negro", 0x000000),
            Map.entry("dark_blue", 0x0000AA), Map.entry("azul_oscuro", 0x0000AA),
            Map.entry("dark_green", 0x00AA00), Map.entry("verde_oscuro", 0x00AA00),
            Map.entry("dark_aqua", 0x00AAAA), Map.entry("turquesa", 0x00AAAA),
            Map.entry("dark_red", 0xAA0000), Map.entry("granate", 0xAA0000),
            Map.entry("dark_purple", 0xAA00AA), Map.entry("morado", 0xAA00AA),
            Map.entry("gold", 0xFFAA00), Map.entry("dorado", 0xFFAA00), Map.entry("naranja", 0xFFAA00),
            Map.entry("gray", 0xAAAAAA), Map.entry("gris", 0xAAAAAA),
            Map.entry("dark_gray", 0x555555), Map.entry("gris_oscuro", 0x555555),
            Map.entry("blue", 0x5555FF), Map.entry("azul", 0x5555FF),
            Map.entry("green", 0x55FF55), Map.entry("verde", 0x55FF55),
            Map.entry("aqua", 0x55FFFF), Map.entry("celeste", 0x55FFFF),
            Map.entry("red", 0xFF5555), Map.entry("rojo", 0xFF5555),
            Map.entry("light_purple", 0xFF55FF), Map.entry("rosa", 0xFF55FF), Map.entry("lila", 0xFF55FF),
            Map.entry("yellow", 0xFFFF55), Map.entry("amarillo", 0xFFFF55),
            Map.entry("white", 0xFFFFFF), Map.entry("blanco", 0xFFFFFF));
    private static final String CODES = "0123456789abcdef";
    private static final int[] CODE_COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
            0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    public DisplayText {
        spans = List.copyOf(spans);
    }

    /** Lee banderas y texto. Lanza con el motivo si algo no vale. */
    public static DisplayText parse(String input) {
        String rest = input == null ? "" : input.strip();
        int color = DEFAULT_COLOR;
        Background background = Background.DEFAULT;
        int backgroundArgb = DEFAULT_BACKGROUND;
        boolean shadow = false;
        boolean seeThrough = false;
        Alignment alignment = Alignment.CENTER;
        int lineWidth = DEFAULT_LINE_WIDTH;
        while (rest.startsWith("-") && rest.length() > 1 && !Character.isDigit(rest.charAt(1))) {
            int space = rest.indexOf(' ');
            String flag = (space < 0 ? rest : rest.substring(0, space)).toLowerCase(Locale.ROOT);
            rest = space < 0 ? "" : rest.substring(space + 1).stripLeading();
            switch (flag) {
                case "-s", "--sombra", "--shadow" -> shadow = true;
                case "-t", "--traves", "--through" -> seeThrough = true;
                case "-c", "--color", "-f", "--fondo", "--background", "-a", "--alinear", "--align", "-w",
                     "--ancho", "--width" -> {
                    int next = rest.indexOf(' ');
                    String value = next < 0 ? rest : rest.substring(0, next);
                    if (value.isEmpty()) {
                        throw new IllegalArgumentException("Falta el valor de " + flag);
                    }
                    rest = next < 0 ? "" : rest.substring(next + 1).stripLeading();
                    switch (flag) {
                        case "-c", "--color" -> color = color(value);
                        case "-f", "--fondo", "--background" -> {
                            String lower = value.toLowerCase(Locale.ROOT);
                            if (lower.equals("no") || lower.equals("none") || lower.equals("ninguno")) {
                                background = Background.NONE;
                                backgroundArgb = 0;
                            } else if (lower.equals("serie") || lower.equals("default")) {
                                background = Background.DEFAULT;
                                backgroundArgb = DEFAULT_BACKGROUND;
                            } else {
                                background = Background.CUSTOM;
                                backgroundArgb = argb(value);
                            }
                        }
                        case "-a", "--alinear", "--align" -> alignment = Alignment.parse(value);
                        default -> {
                            try {
                                lineWidth = Integer.parseInt(value);
                            } catch (NumberFormatException exception) {
                                throw new IllegalArgumentException("Ancho no válido: " + value);
                            }
                            if (lineWidth < MIN_LINE_WIDTH || lineWidth > MAX_LINE_WIDTH) {
                                throw new IllegalArgumentException("El ancho va de " + MIN_LINE_WIDTH + " a "
                                        + MAX_LINE_WIDTH + " píxeles");
                            }
                        }
                    }
                }
                default -> throw new IllegalArgumentException("Bandera desconocida: " + flag
                        + " (-c color, -f fondo, -s sombra, -a alineación, -w ancho, -t a través)");
            }
        }
        if (rest.isBlank()) {
            throw new IllegalArgumentException("Falta el texto");
        }
        List<Span> spans = spans(rest);
        StringBuilder plain = new StringBuilder();
        spans.forEach(span -> plain.append(span.text()));
        return new DisplayText(spans, plain.toString(), color, background, backgroundArgb, shadow, alignment,
                lineWidth, seeThrough);
    }

    /** Las banderas de Minecraft del display ({@code TextDisplayEntity}): sombra 1, a través 2, fondo de serie 4. */
    public byte flags() {
        int flags = 0;
        if (shadow) {
            flags |= 1;
        }
        if (seeThrough) {
            flags |= 2;
        }
        if (background == Background.DEFAULT) {
            flags |= 4;
        }
        flags |= switch (alignment) {
            case LEFT -> 8;
            case RIGHT -> 16;
            case CENTER -> 0;
        };
        return (byte) flags;
    }

    /** Un color de texto: {@code #RRGGBB}, {@code RRGGBB} o un nombre. */
    public static int color(String value) {
        String lower = value.strip().toLowerCase(Locale.ROOT);
        Integer named = NAMED.get(lower);
        if (named != null) {
            return named;
        }
        String hex = lower.startsWith("#") ? lower.substring(1) : lower;
        if (hex.length() != 6) {
            throw new IllegalArgumentException("Color no válido: " + value + " (#RRGGBB o un nombre como rojo)");
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Color no válido: " + value + " (#RRGGBB o un nombre como rojo)");
        }
    }

    /** Un color con alfa: {@code #AARRGGBB}, o {@code #RRGGBB} con el alfa del fondo de serie (25 %). */
    public static int argb(String value) {
        String lower = value.strip().toLowerCase(Locale.ROOT);
        String hex = lower.startsWith("#") ? lower.substring(1) : lower;
        try {
            if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
            if (hex.length() == 6) {
                return DEFAULT_BACKGROUND & 0xFF000000 | Integer.parseInt(hex, 16);
            }
        } catch (NumberFormatException ignored) {
            // Cae al error de abajo.
        }
        Integer named = NAMED.get(lower);
        if (named != null) {
            return DEFAULT_BACKGROUND & 0xFF000000 | named;
        }
        throw new IllegalArgumentException("Fondo no válido: " + value + " (#AARRGGBB, #RRGGBB, no o serie)");
    }

    /** El texto en trozos con su formato, con {@code \n} como salto de línea. */
    static List<Span> spans(String text) {
        List<Span> spans = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int color = -1;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        boolean obfuscated = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\\' && index + 1 < text.length() && text.charAt(index + 1) == 'n') {
                current.append('\n');
                index++;
                continue;
            }
            if (character != '&' || index + 1 >= text.length()) {
                current.append(character);
                continue;
            }
            char code = Character.toLowerCase(text.charAt(index + 1));
            if (code == '&') {
                current.append('&');
                index++;
                continue;
            }
            int colorCode = CODES.indexOf(code);
            boolean format = "lonmkr".indexOf(code) >= 0;
            if (colorCode < 0 && !format) {
                current.append(character);
                continue;
            }
            if (!current.isEmpty()) {
                spans.add(new Span(current.toString(), color, bold, italic, underline, strike, obfuscated));
                current.setLength(0);
            }
            index++;
            if (colorCode >= 0) {
                color = CODE_COLORS[colorCode];
                // Como en Minecraft: un color quita el formato de antes.
                bold = italic = underline = strike = obfuscated = false;
                continue;
            }
            switch (code) {
                case 'l' -> bold = true;
                case 'o' -> italic = true;
                case 'n' -> underline = true;
                case 'm' -> strike = true;
                case 'k' -> obfuscated = true;
                default -> {
                    color = -1;
                    bold = italic = underline = strike = obfuscated = false;
                }
            }
        }
        if (!current.isEmpty()) {
            spans.add(new Span(current.toString(), color, bold, italic, underline, strike, obfuscated));
        }
        return spans;
    }
}
