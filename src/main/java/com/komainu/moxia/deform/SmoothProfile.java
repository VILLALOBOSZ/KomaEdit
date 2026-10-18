package com.komainu.moxia.deform;

import com.komainu.moxia.shape.smooth.PieceFit;
import com.komainu.moxia.shape.smooth.PieceMaterials;
import com.komainu.moxia.shape.smooth.PieceVariant;
import com.komainu.moxia.shape.smooth.SmoothMode;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Perfil de smoothblocks de la bandera {@code -w}, con la notación de ezEdits. Es el mismo en todos los comandos
 * que la admiten (tubos, deform, surface, smooth, inflate, deflate, smoothblocks, place, array y scatter):
 *
 * <ul>
 *     <li>Nombre: {@code Slabs}, {@code SlabsAndStairs}, {@code SlabsAndStairs2D}, {@code Layers}, {@code SSW}
 *     (o sus alias en español, {@link SmoothMode#byId}).</li>
 *     <li>Cobertura entre paréntesis: {@code Slabs(C:0.7)}, de 0 (solo bloques enteros) a 1 (toda la superficie
 *     con piezas); por defecto {@value PieceFit#DEFAULT_COVERAGE}.</li>
 *     <li>Material fijo por variante: {@code Slabs(Slab:acacia)}, {@code SlabsAndStairs(Slab:oak,Stair:spruce)},
 *     {@code Layers(Layer:snow)}, {@code SSW(Wall:cobblestone)}. Se separan con comas y se combinan con la
 *     cobertura.</li>
 *     <li>Compatibilidad: la forma antigua {@code stairs:0.7} (cobertura tras dos puntos) sigue valiendo.</li>
 * </ul>
 */
public record SmoothProfile(SmoothMode mode, double coverage, PieceMaterials materials) {
    public static final SmoothProfile NONE = new SmoothProfile(SmoothMode.NONE, PieceFit.DEFAULT_COVERAGE);

    public SmoothProfile {
        if (mode == null) {
            mode = SmoothMode.NONE;
        }
        if (!(coverage >= 0.0 && coverage <= 1.0)) {
            throw new IllegalArgumentException("La cobertura va de 0 a 1");
        }
        materials = materials == null ? PieceMaterials.NONE : materials;
    }

    public SmoothProfile(SmoothMode mode, double coverage) {
        this(mode, coverage, PieceMaterials.NONE);
    }

    public boolean active() {
        return mode != SmoothMode.NONE;
    }

    public static SmoothProfile parse(String input) {
        if (input == null || input.isBlank()) {
            return NONE;
        }
        String text = input.trim();
        String name = text;
        double coverage = PieceFit.DEFAULT_COVERAGE;
        Map<PieceVariant, String> materials = new EnumMap<>(PieceVariant.class);
        int open = text.indexOf('(');
        if (open >= 0) {
            if (!text.endsWith(")")) {
                throw new IllegalArgumentException("Falta el paréntesis de cierre en el perfil " + text);
            }
            name = text.substring(0, open);
            String body = text.substring(open + 1, text.length() - 1).trim();
            for (String entry : body.isEmpty() ? new String[0] : body.split(",")) {
                String[] pair = entry.split(":", 2);
                if (pair.length != 2 || pair[1].isBlank()) {
                    throw new IllegalArgumentException("Parámetro de perfil inválido: «" + entry.trim()
                            + "» (usa C:0.5 o Slab:material)");
                }
                String key = pair[0].trim().toLowerCase(Locale.ROOT);
                String value = pair[1].trim();
                if (key.equals("c") || key.equals("coverage") || key.equals("cobertura")) {
                    coverage = coverage(value);
                    continue;
                }
                PieceVariant variant = PieceVariant.find(key);
                if (variant == null) {
                    throw new IllegalArgumentException("Parámetro de perfil desconocido: «" + pair[0].trim()
                            + "» (C, Slab, Stair, Wall o Layer)");
                }
                if (materials.put(variant, value) != null) {
                    throw new IllegalArgumentException("El material de " + variant.key() + " está repetido");
                }
            }
        } else {
            // Forma antigua de KomaEdit: stairs:0.7.
            int colon = text.indexOf(':');
            if (colon > 0) {
                name = text.substring(0, colon);
                coverage = coverage(text.substring(colon + 1));
            }
        }
        SmoothMode mode = SmoothMode.byId(name);
        for (PieceVariant variant : materials.keySet()) {
            if (!mode.uses(variant)) {
                throw new IllegalArgumentException("El perfil " + mode.ezName() + " no usa " + variant.key()
                        + " (usa " + keys(mode) + ")");
            }
        }
        return new SmoothProfile(mode, coverage, PieceMaterials.of(materials));
    }

    /** Si el texto del perfil fija la cobertura ({@code Slabs(C:0.7)}, {@code stairs:0.7}). */
    public static boolean statesCoverage(String input) {
        if (input == null) {
            return false;
        }
        String text = input.replace(" ", "").toLowerCase(Locale.ROOT);
        int open = text.indexOf('(');
        if (open < 0) {
            return text.indexOf(':') > 0;
        }
        String body = text.substring(open + 1);
        return body.startsWith("c:") || body.contains(",c:") || body.contains("coverage:")
                || body.contains("cobertura:");
    }

    /**
     * Junta en un solo valor un perfil que el jugador escribió con espacios tras las comas
     * ({@code SlabsAndStairs(Slab:oak, Stair:spruce)}): devuelve cuántas palabras más, desde {@code start}, hacen
     * falta para cerrar el paréntesis (0 si ya está cerrado o no lo hay).
     */
    public static int extraWords(String[] words, int start) {
        int depth = 0;
        for (int index = start; index < words.length; index++) {
            for (char character : words[index].toCharArray()) {
                if (character == '(') {
                    depth++;
                } else if (character == ')') {
                    depth--;
                }
            }
            if (depth <= 0) {
                return index - start;
            }
        }
        return 0;
    }

    /** Escritura canónica, la que se lee igual con {@link #parse}: {@code SlabsAndStairs(C:0.7,Slab:oak)}. */
    @Override
    public String toString() {
        if (!active()) {
            return "none";
        }
        String extra = materials.isEmpty() ? "" : "," + materials;
        return mode.ezName() + "(C:" + coverage + extra + ")";
    }

    private static double coverage(String value) {
        double parsed;
        try {
            parsed = Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("La cobertura no es un número: " + value.trim());
        }
        if (!(parsed >= 0.0 && parsed <= 1.0)) {
            throw new IllegalArgumentException("La cobertura va de 0 a 1: " + value.trim());
        }
        return parsed;
    }

    private static String keys(SmoothMode mode) {
        StringBuilder builder = new StringBuilder();
        for (PieceVariant variant : PieceVariant.values()) {
            if (mode.uses(variant)) {
                builder.append(builder.isEmpty() ? "" : ", ").append(variant.key());
            }
        }
        return builder.isEmpty() ? "ninguna pieza" : builder.toString();
    }
}
