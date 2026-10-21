package com.komainu.moxia.brush.ez;

import java.util.Locale;
import java.util.Set;

/**
 * El pincel de zona ({@code /komaedit pincel zona <radio> <orden>}): una orden de la selección (retocar, pintar,
 * erosionar…) que se repite en un cilindro alrededor de cada clic en vez de en la selección de WorldEdit. Así todo
 * lo que se hace sobre la selección también se puede hacer con el pincel, con los mismos ajustes. Se lee sin
 * Minecraft.
 *
 * <p>Solo valen las órdenes que retocan lo que ya hay ({@link #ROOTS}): una orden que coloque figuras, copie o
 * borre la selección no tiene sentido como pincel.</p>
 *
 * @param radius  el radio del cilindro, en bloques
 * @param command la orden sin barra ni {@code komaedit} ({@code retocar nieve 2 -p})
 */
public record ZoneCommand(int radius, String command) {
    public static final int DEFAULT_RADIUS = 8;
    public static final int MAX_RADIUS = 32;
    /** Lo que el cilindro sube y baja desde el clic como poco: lo bastante para un talud o una ladera. */
    public static final int MIN_HALF_HEIGHT = 12;
    /** Las órdenes de la selección que valen como pincel (la primera palabra tras {@code komaedit}). */
    public static final Set<String> ROOTS = Set.of("retocar", "touchup", "pintar", "paint", "texture", "textura",
            "smooth", "suavizar", "surface", "superficie",
            // Los nombres de antes de «retocar», que siguen valiendo.
            "snow", "nieve", "drain", "drenar", "waterlog", "inundar", "gravity", "gravedad", "biome", "bioma",
            "tweak", "survival");
    /** De {@code terreno}, solo lo que trabaja sobre la selección: erosionar y pintar. */
    public static final Set<String> TERRAIN_ACTIONS = Set.of("erosion", "erosión", "erode", "erosionar", "pintar",
            "paint");
    private static final Set<String> TERRAIN = Set.of("terreno", "terrain");
    private static final Set<String> PREFIXES = Set.of("komaedit", "ke", "moxia", "mx");

    public ZoneCommand {
        if (radius < 1 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("El radio del pincel va de 1 a " + MAX_RADIUS);
        }
        command = command.strip();
    }

    /**
     * {@code <radio> <orden>}; sin radio, {@value #DEFAULT_RADIUS}. La orden puede llevar delante {@code /komaedit}.
     */
    public static ZoneCommand parse(String input) {
        String text = input == null ? "" : input.strip();
        String[] first = text.split("\\s+", 2);
        int radius = DEFAULT_RADIUS;
        if (!text.isEmpty() && BrushOptions.isNumber(first[0])) {
            try {
                radius = (int) Math.round(Double.parseDouble(first[0].replace(',', '.')));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("El radio es un número: " + first[0]);
            }
            text = first.length > 1 ? first[1].strip() : "";
        }
        return new ZoneCommand(radius, check(text));
    }

    /** La orden sin barra ni raíz, si es de las que valen; si no, el motivo. */
    static String check(String command) {
        String text = command.strip();
        if (text.startsWith("/")) {
            text = text.substring(1).strip();
        }
        String[] words = text.isEmpty() ? new String[0] : text.split("\\s+");
        int start = words.length > 0 && PREFIXES.contains(words[0].toLowerCase(Locale.ROOT)) ? 1 : 0;
        if (words.length <= start) {
            throw new IllegalArgumentException("Falta la orden: pincel zona <radio> retocar nieve 2, por ejemplo");
        }
        String root = words[start].toLowerCase(Locale.ROOT);
        boolean allowed = ROOTS.contains(root) || TERRAIN.contains(root) && words.length > start + 1
                && TERRAIN_ACTIONS.contains(words[start + 1].toLowerCase(Locale.ROOT));
        if (!allowed) {
            throw new IllegalArgumentException("«" + root + "» no vale como pincel: usa retocar, pintar, terreno"
                    + " erosion, texture, smooth o surface");
        }
        return String.join(" ", java.util.Arrays.copyOfRange(words, start, words.length));
    }

    /** Lo que el cilindro sube y baja desde el bloque pulsado. */
    public int halfHeight() {
        return Math.max(radius, MIN_HALF_HEIGHT);
    }

    /** La orden con su raíz, tal como se ejecuta en cada clic. */
    public String full() {
        return "komaedit " + command;
    }
}
