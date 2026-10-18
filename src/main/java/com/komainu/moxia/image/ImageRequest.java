package com.komainu.moxia.image;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Argumentos de {@code /komaedit image <origen> [ancho|anchoxalto] [paleta] [banderas]}.
 *
 * <pre>
 * -w &lt;bloques&gt;   ancho (el alto sale de la proporción)     -h &lt;bloques&gt;  alto
 * -p &lt;paleta&gt;    glass, panes, wool, concrete, terracotta, solid o cualquier paleta (##Nombre, #mía, a,b,c)
 * -d &lt;tramado&gt;   none, fs (Floyd–Steinberg, por defecto) o bayer
 * -b &lt;brillo&gt;    −100 a 100                                 -c &lt;contraste&gt;  −100 a 100
 * -a &lt;bloque&gt;    qué poner en los píxeles transparentes: air (por defecto), keep (no tocar) o un bloque
 * -u &lt;umbral&gt;    alfa mínimo en % para que un píxel cuente (50)
 * -o &lt;anclaje&gt;   center, bottom, top, left, right, bottomleft…
 * -s              ajustar a la selección (plana)          -e  estirar hasta llenarla (implica -s)
 * -r              sobre la superficie que miras, sustituyéndola, en lugar de delante
 * </pre>
 *
 * El origen es el nombre de un archivo de {@code config/komaedit/images/} o una URL {@code https://}; los nombres con
 * espacios van entre comillas.
 */
public record ImageRequest(
        String source,
        Integer width,
        Integer height,
        String palette,
        Dithering dithering,
        int brightness,
        int contrast,
        Transparency transparency,
        String transparentBlock,
        int alphaThreshold,
        Anchor anchor,
        boolean selection,
        boolean stretch,
        boolean surface
) {
    public static final String DEFAULT_PALETTE = "glass";

    public enum Transparency {
        /** Aire: la obra borra lo que hubiera en sus huecos. */
        AIR,
        /** No se toca: lo de detrás se queda. */
        KEEP,
        /** Un bloque elegido. */
        BLOCK
    }

    public boolean isUrl() {
        String lower = source.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    /** Nombre corto para los mensajes: el archivo, o el último tramo de la URL. */
    public String displayName() {
        if (!isUrl()) {
            return source;
        }
        String path = source.replaceAll("[?#].*$", "");
        int slash = path.lastIndexOf('/');
        String last = slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
        return last.length() > 40 ? last.substring(0, 40) + "…" : last;
    }

    public static ImageRequest parse(String input) {
        List<String> words = tokens(input);
        List<String> positional = new ArrayList<>();
        Integer width = null;
        Integer height = null;
        String palette = null;
        Dithering dithering = Dithering.FLOYD_STEINBERG;
        int brightness = 0;
        int contrast = 0;
        Transparency transparency = Transparency.AIR;
        String transparentBlock = null;
        int alpha = 128;
        Anchor anchor = null;
        boolean selection = false;
        boolean stretch = false;
        boolean surface = false;
        for (int index = 0; index < words.size(); index++) {
            String word = words.get(index);
            String lower = word.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("-") || lower.matches("-\\d.*") || lower.length() == 1) {
                positional.add(word);
                continue;
            }
            switch (lower) {
                case "-w", "-ancho", "-width" -> width = size(value(words, ++index, word), "el ancho");
                case "-h", "-alto", "-height" -> height = size(value(words, ++index, word), "el alto");
                case "-p", "-paleta", "-palette" -> palette = value(words, ++index, word);
                case "-d", "-tramado", "-dither" -> dithering = Dithering.parse(value(words, ++index, word));
                case "-b", "-brillo", "-brightness" -> brightness = percent(value(words, ++index, word), "el brillo");
                case "-c", "-contraste", "-contrast" ->
                        contrast = percent(value(words, ++index, word), "el contraste");
                case "-a", "-transparente", "-transparent" -> {
                    String block = value(words, ++index, word);
                    switch (block.toLowerCase(Locale.ROOT)) {
                        case "air", "aire", "minecraft:air" -> {
                            transparency = Transparency.AIR;
                            transparentBlock = null;
                        }
                        case "keep", "dejar", "skip", "none", "ninguno" -> {
                            transparency = Transparency.KEEP;
                            transparentBlock = null;
                        }
                        default -> {
                            transparency = Transparency.BLOCK;
                            transparentBlock = block;
                        }
                    }
                }
                case "-u", "-umbral", "-threshold" -> {
                    int percent = integer(value(words, ++index, word).replace("%", ""), "el umbral");
                    if (percent < 1 || percent > 100) {
                        throw new IllegalArgumentException("El umbral de transparencia va de 1 a 100 (%)");
                    }
                    alpha = Math.clamp(Math.round(percent * 2.55F), 1, 255);
                }
                case "-o", "-anclaje", "-anchor" -> anchor = Anchor.parse(value(words, ++index, word));
                default -> {
                    if (!lower.matches("-[ser]+")) {
                        throw new IllegalArgumentException("Bandera desconocida: " + word
                                + " (hay -w -h -p -d -b -c -a -u -o -s -e -r)");
                    }
                    selection |= lower.contains("s") || lower.contains("e");
                    stretch |= lower.contains("e");
                    surface |= lower.contains("r");
                }
            }
        }
        if (positional.isEmpty()) {
            throw new IllegalArgumentException("Falta la imagen: un archivo de config/komaedit/images/ o una URL "
                    + "https://");
        }
        String source = positional.getFirst();
        for (String word : positional.subList(1, positional.size())) {
            if (word.matches("\\d+[xX×]\\d+") && width == null && height == null) {
                String[] parts = word.split("[xX×]");
                width = size(parts[0], "el ancho");
                height = size(parts[1], "el alto");
            } else if (word.matches("\\d+") && width == null && height == null) {
                width = size(word, "el ancho");
            } else if (palette == null && !word.matches("-?\\d+")) {
                palette = word;
            } else {
                throw new IllegalArgumentException("Sobra «" + word + "»");
            }
        }
        if (selection && (width != null || height != null)) {
            throw new IllegalArgumentException("Con -s la medida sale de la selección: quita el ancho y el alto");
        }
        if (selection && surface) {
            throw new IllegalArgumentException("-r es para la superficie que miras; con -s se usa la selección");
        }
        return new ImageRequest(source, width, height, palette == null ? DEFAULT_PALETTE : palette, dithering,
                brightness, contrast, transparency, transparentBlock, alpha, anchor, selection, stretch, surface);
    }

    /** Palabras separadas por espacios; las comillas dobles agrupan («"mi foto.png"»). */
    static List<String> tokens(String input) {
        List<String> words = new ArrayList<>();
        if (input == null) {
            return words;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean started = false;
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            if (character == '"') {
                quoted = !quoted;
                started = true;
            } else if (Character.isWhitespace(character) && !quoted) {
                if (started) {
                    words.add(current.toString());
                    current.setLength(0);
                    started = false;
                }
            } else {
                current.append(character);
                started = true;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Faltan las comillas de cierre");
        }
        if (started) {
            words.add(current.toString());
        }
        return words;
    }

    private static String value(List<String> words, int index, String flag) {
        if (index >= words.size()) {
            throw new IllegalArgumentException("Falta el valor de " + flag);
        }
        return words.get(index);
    }

    private static int size(String text, String what) {
        int value = integer(text, what);
        if (value < 1) {
            throw new IllegalArgumentException(capitalized(what) + " tiene que ser de al menos 1 bloque");
        }
        return value;
    }

    private static int percent(String text, String what) {
        int value = integer(text.replace("%", ""), what);
        if (value < ImageFilters.MIN || value > ImageFilters.MAX) {
            throw new IllegalArgumentException(capitalized(what) + " va de " + ImageFilters.MIN + " a "
                    + ImageFilters.MAX);
        }
        return value;
    }

    private static int integer(String text, String what) {
        if (!text.matches("[+-]?\\d{1,6}")) {
            throw new IllegalArgumentException("«" + text + "» no es un número entero válido para " + what);
        }
        return Integer.parseInt(text);
    }

    private static String capitalized(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
