package com.komainu.moxia.capability;

import java.util.Locale;

/**
 * Capacidades de construcción a mano, como el modo Builder de Axiom. Cada una se activa por separado, se recuerda
 * por jugador y la comprueba el servidor (permiso propio, modo creativo y alcance).
 */
public enum BuildCapability {
    REPLACE("replace", "reemplazar", "Reemplazar",
            "Clic derecho cambia el bloque apuntado por el de la mano y conserva sus propiedades"),
    TWEAK("tweak", "retocar", "Cambiar estado",
            "Clic derecho con la mano vacía cambia una propiedad del bloque; F elige cuál"),
    EXTRUDE("extrude", "extruir", "Extruir",
            "Con el mismo bloque en la mano: clic derecho copia la capa hacia fuera, izquierdo la encoge"),
    SMEAR("smear", "arrastrar", "Arrastrar",
            "Dos clics derechos estiran un bloque (o una fila con Mayús) en el eje de la cara"),
    ANGEL("angel", "aire", "Colocar en el aire",
            "Clic derecho mirando al aire coloca el bloque a la distancia elegida"),
    REACH("reach", "alcance", "Alcance largo",
            "Alarga la distancia a la que llegas a los bloques"),
    FAST_PLACE("fastplace", "rapida", "Colocación rápida",
            "Sin espera entre bloques al mantener el clic derecho"),
    NO_UPDATES("noupdates", "sinactualizar", "Sin actualizaciones",
            "Coloca y rompe sin avisar a los vecinos: arena flotante, agua quieta");

    public static final String PERMISSION_PREFIX = "komaedit.capability.";
    /** Cambiar la velocidad de vuelo desde el menú rápido. */
    public static final String FLY_SPEED_PERMISSION = PERMISSION_PREFIX + "flyspeed";
    /** Cambiar el modo de juego desde el menú rápido. */
    public static final String GAMEMODE_PERMISSION = "komaedit.admin.gamemode";
    /** Usar las capacidades fuera del modo creativo. */
    public static final String ANY_MODE_PERMISSION = "komaedit.admin.anymode";

    private final String id;
    private final String alias;
    private final String displayName;
    private final String description;

    BuildCapability(String id, String alias, String displayName, String description) {
        this.id = id;
        this.alias = alias;
        this.displayName = displayName;
        this.description = description;
    }

    public String id() {
        return id;
    }

    /** Nombre en español para las órdenes ({@code /komaedit capability reemplazar}). */
    public String alias() {
        return alias;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String permission() {
        return PERMISSION_PREFIX + id;
    }

    /** Bit de esta capacidad en las máscaras que viajan por red. */
    public int bit() {
        return 1 << ordinal();
    }

    /** Capacidad por id o alias, sin distinguir mayúsculas; {@code null} si no existe. */
    public static BuildCapability byName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (BuildCapability capability : values()) {
            if (capability.id.equals(normalized) || capability.alias.equals(normalized)) {
                return capability;
            }
        }
        return null;
    }

    public static BuildCapability byOrdinal(int ordinal) {
        BuildCapability[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
