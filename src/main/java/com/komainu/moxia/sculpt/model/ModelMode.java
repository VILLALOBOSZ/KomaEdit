package com.komainu.moxia.sculpt.model;

import java.util.Locale;

/** Los pinceles de modelado (los de Axiom que faltaban), con sus nombres en inglés y en español. */
public enum ModelMode {
    /** Empuja o hunde la superficie a lo largo de su normal media. */
    DRAW("draw", "Esculpir", "esculpir", "dibujar", "sculptdraw"),
    /** Añade masa con ruido que se funde con lo que hay. */
    ROCK("rock", "Roca", "roca", "rocoso"),
    /** Desenfoque gaussiano que solo añade: une piezas sueltas y rellena rincones. */
    WELD("weld", "Soldar", "soldar", "unir"),
    /** Desenfoque gaussiano que solo quita: redondea aristas y esquinas. */
    MELT("melt", "Fundir", "fundir", "derretir"),
    /** Más caras expuestas sin cambiar apenas el volumen. */
    ROUGHEN("roughen", "Rugosidad", "rugosidad", "rugoso", "rough"),
    /** Grietas por las aristas de Voronoi. */
    SHATTER("shatter", "Grietas", "grietas", "agrietar", "romper");

    private final String id;
    private final String displayName;
    private final String[] aliases;

    ModelMode(String id, String displayName, String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static ModelMode byId(String input) {
        String name = input.trim().toLowerCase(Locale.ROOT);
        for (ModelMode mode : values()) {
            if (mode.id.equals(name)) {
                return mode;
            }
            for (String alias : mode.aliases) {
                if (alias.equals(name)) {
                    return mode;
                }
            }
        }
        throw new IllegalArgumentException("Modo de modelado desconocido: " + input
                + " (draw, rock, weld, melt, roughen, shatter)");
    }

    /** Eje de las grietas: columnas a lo largo de un eje o celdas en 3D. */
    public enum Axis {
        X("x"), Y("y"), Z("z"), FREE("3d");

        private final String id;

        Axis(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Axis byId(String input) {
            return switch (input.trim().toLowerCase(Locale.ROOT)) {
                case "x" -> X;
                case "y" -> Y;
                case "z" -> Z;
                case "3d", "xyz", "libre", "free" -> FREE;
                default -> throw new IllegalArgumentException("El eje de las grietas es x, y, z o 3d: " + input);
            };
        }
    }
}
