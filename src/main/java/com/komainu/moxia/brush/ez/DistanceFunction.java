package com.komainu.moxia.brush.ez;

/** Distancias del modo «desde el primer punto» del pincel de degradado ({@code -d}). */
public enum DistanceFunction {
    EUCLIDEAN("euclidean", "e", "euclidea", "esferica"),
    MANHATTAN("manhattan", "m", "taxi"),
    CHEBYSHEV("chebyshev", "c", "cubo", "cube");

    private final String id;
    private final String[] aliases;

    DistanceFunction(String id, String... aliases) {
        this.id = id;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    public double measure(double x, double y, double z) {
        return switch (this) {
            case EUCLIDEAN -> Math.sqrt(x * x + y * y + z * z);
            case MANHATTAN -> Math.abs(x) + Math.abs(y) + Math.abs(z);
            case CHEBYSHEV -> Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        };
    }

    /** @return la función, o {@code null} para {@code none} (modo plano) */
    public static DistanceFunction byName(String input) {
        String name = BrushOptions.normalize(input);
        if (name.equals("none") || name.equals("ninguna") || name.equals("plano")) {
            return null;
        }
        for (DistanceFunction value : values()) {
            if (value.id.equals(name)) {
                return value;
            }
            for (String alias : value.aliases) {
                if (alias.equals(name)) {
                    return value;
                }
            }
        }
        throw new IllegalArgumentException("Distancia desconocida: " + input + " (euclidean, manhattan, chebyshev, none)");
    }
}
