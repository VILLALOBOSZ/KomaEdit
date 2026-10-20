package com.komainu.moxia.roof;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Tipos de tejado de {@code /komaedit roof}. Cada uno trae la pendiente por defecto (subida por bloque de avance)
 * y dice si tiene hastiales (paredes triangulares que se cierran con el material del muro) y si admite
 * buhardillas.
 */
public enum RoofType {
    GABLE("gable", "A dos aguas", 1.0, true, true,
            "dosaguas", "2aguas", "aguas", "dos", "gable"),
    HIP("hip", "A cuatro aguas", 1.0, false, true,
            "cuatroaguas", "4aguas", "pabellon", "cuatro", "hipped"),
    MANSARD("mansard", "Mansarda", 0.5, false, true,
            "mansarda", "mansardo", "frances"),
    SHED("shed", "A un agua", 0.5, true, true,
            "unagua", "1agua", "cobertizo", "leanto", "lean", "un"),
    GABLET("gablet", "Holandés", 1.0, true, true,
            "holandes", "dutch", "gablete", "medioshastiales"),
    DOME("dome", "Cúpula", 1.0, false, false,
            "cupula", "boveda"),
    CONE("cone", "Cono", 2.0, false, false,
            "cono", "torreon", "conico"),
    SPIRE("spire", "Aguja", 3.0, false, false,
            "aguja", "chapitel", "pinaculo", "piramide"),
    PAGODA("pagoda", "Pagoda", 1.0, false, false,
            "oriental", "japones"),
    FLAT("flat", "Plano con pretil", 0.0, false, false,
            "plano", "azotea", "terraza", "pretil");

    private final String id;
    private final String displayName;
    private final double defaultSlope;
    private final boolean gables;
    private final boolean dormers;
    private final List<String> aliases;

    RoofType(String id, String displayName, double defaultSlope, boolean gables, boolean dormers,
             String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.defaultSlope = defaultSlope;
        this.gables = gables;
        this.dormers = dormers;
        this.aliases = List.of(aliases);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Subida por bloque de avance si no se da {@code -pendiente}. */
    public double defaultSlope() {
        return defaultSlope;
    }

    /** Si tiene hastiales que se cierran con el material del muro. */
    public boolean hasGables() {
        return gables;
    }

    public boolean allowsDormers() {
        return dormers;
    }

    /** Las figuras redondas (cúpula, cono y aguja) se inscriben en la planta; el resto de la planta queda plano. */
    public boolean round() {
        return this == DOME || this == CONE || this == SPIRE;
    }

    public static RoofType byId(String input) {
        String normalized = normalize(input);
        for (RoofType type : values()) {
            if (type.id.equals(normalized) || type.aliases.contains(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Tipo de tejado desconocido: " + input + " (" + names() + ")");
    }

    public static String names() {
        return Arrays.stream(values()).map(RoofType::id).collect(Collectors.joining(", "));
    }

    static String normalize(String input) {
        return input.trim().toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                .replace("_", "").replace("-", "").replace(" ", "");
    }
}
