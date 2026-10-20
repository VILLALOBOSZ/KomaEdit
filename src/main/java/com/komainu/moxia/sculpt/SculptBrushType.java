package com.komainu.moxia.sculpt;

import com.komainu.moxia.sculpt.model.ModelMode;

import java.util.Arrays;
import java.util.Locale;

public enum SculptBrushType {
    SPHERE("sphere", "Esfera", Category.SHAPE, "esfera"),
    CUBE("cube", "Cubo", Category.SHAPE, "cubo"),
    CYLINDER("cylinder", "Cilindro", Category.SHAPE, "cilindro"),
    DISC("disc", "Disco", Category.SHAPE, "disco"),
    DOME("dome", "Domo", Category.SHAPE, "domo"),
    CONE("cone", "Cono", Category.SHAPE, "cono"),
    LINE("line", "Línea", Category.LINE, "linea", "línea"),
    SMOOTH("smooth", "Suavizar", Category.HEIGHT, "suavizar"),
    FLATTEN("flatten", "Aplanar", Category.HEIGHT, "aplanar"),
    RAISE("raise", "Elevar", Category.HEIGHT, "elevar", "subir"),
    LOWER("lower", "Bajar", Category.HEIGHT, "bajar"),
    FILL("fill", "Rellenar", Category.VOLUME, "rellenar"),
    REPLACE("replace", "Reemplazar", Category.VOLUME, "reemplazar"),
    OVERLAY("overlay", "Recubrir", Category.VOLUME, "recubrir", "capa"),
    SURFACE("surface", "Superficie", Category.VOLUME, "superficie"),
    PAINT("paint", "Pintar", Category.VOLUME, "pintar"),
    BLEND("blend", "Mezclar", Category.VOLUME, "mezclar"),
    EROSION("erosion", "Erosión", Category.HEIGHT, "erosion", "erosión"),
    NOISE("noise", "Ruido", Category.HEIGHT, "ruido"),
    // Modelado (paquete sculpt.model): los pinceles de Axiom que faltaban.
    DRAW("draw", "Esculpir", Category.MODEL, "esculpir", "dibujar"),
    ROCK("rock", "Roca", Category.MODEL, "roca"),
    WELD("weld", "Soldar", Category.MODEL, "soldar"),
    MELT("melt", "Fundir", Category.MODEL, "fundir"),
    ROUGHEN("roughen", "Rugosidad", Category.MODEL, "rugosidad", "rugoso"),
    SHATTER("shatter", "Grietas", Category.MODEL, "grietas");

    private final String id;
    private final String displayName;
    private final Category category;
    private final String[] aliases;

    SculptBrushType(String id, String displayName, Category category, String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Category category() {
        return category;
    }

    public boolean needsSnapshot() {
        return category != Category.LINE;
    }

    /** El pincel de modelado de este tipo, o {@code null} si no es de modelado. */
    public ModelMode modelMode() {
        return category == Category.MODEL ? ModelMode.byId(id) : null;
    }

    public static SculptBrushType byId(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.id.equals(normalized)
                        || Arrays.stream(type.aliases).anyMatch(normalized::equals))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Brocha desconocida: " + input));
    }

    public enum Category {
        SHAPE,
        LINE,
        VOLUME,
        HEIGHT,
        /** Esculpir, roca, soldar, fundir, rugosidad y grietas: van por {@code SculptManager.run}. */
        MODEL
    }
}
