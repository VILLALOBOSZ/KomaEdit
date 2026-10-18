package com.komainu.moxia.capability;

import java.util.Locale;

/** Opciones de las capacidades que se cambian desde el menú rápido o con {@code /komaedit capability option}. */
public enum CapabilityOption {
    /** «Reemplazar familia»: 1 activa, 0 desactiva. */
    REPLACE_FAMILY("family", "familia", BuildCapability.REPLACE),
    /** Qué sigue la capa de «Extruir»: 0 el mismo tipo de bloque, 1 el mismo estado exacto. */
    EXTRUDE_MATCH("match", "coincidencia", BuildCapability.EXTRUDE),
    EXTRUDE_LIMIT("limit", "limite", BuildCapability.EXTRUDE),
    ANGEL_DISTANCE("distance", "distancia", BuildCapability.ANGEL),
    REACH_DISTANCE("reach", "alcance", BuildCapability.REACH),
    /** Velocidad de vuelo en décimas de la normal (10 = normal). */
    FLY_SPEED("flyspeed", "vuelo", null);

    private final String id;
    private final String alias;
    private final BuildCapability capability;

    CapabilityOption(String id, String alias, BuildCapability capability) {
        this.id = id;
        this.alias = alias;
        this.capability = capability;
    }

    public String id() {
        return id;
    }

    public String alias() {
        return alias;
    }

    /** Capacidad a la que pertenece ({@code null} para la velocidad de vuelo, que va aparte). */
    public BuildCapability capability() {
        return capability;
    }

    public int read(CapabilitySettings settings) {
        return switch (this) {
            case REPLACE_FAMILY -> settings.replaceFamily() ? 1 : 0;
            case EXTRUDE_MATCH -> settings.extrudeByState() ? 1 : 0;
            case EXTRUDE_LIMIT -> settings.extrudeLimit();
            case ANGEL_DISTANCE -> settings.angelDistance();
            case REACH_DISTANCE -> settings.reachDistance();
            case FLY_SPEED -> settings.flySpeed();
        };
    }

    /** Ajustes con la opción cambiada. Los valores se recortan a su rango; los límites del servidor van aparte. */
    public CapabilitySettings apply(CapabilitySettings settings, int value) {
        return switch (this) {
            case REPLACE_FAMILY -> settings.withReplaceFamily(value != 0);
            case EXTRUDE_MATCH -> settings.withExtrudeByState(value != 0);
            case EXTRUDE_LIMIT -> settings.withExtrudeLimit(value);
            case ANGEL_DISTANCE -> settings.withAngelDistance(value);
            case REACH_DISTANCE -> settings.withReachDistance(value);
            case FLY_SPEED -> settings.withFlySpeed(value);
        };
    }

    public static CapabilityOption byName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (CapabilityOption option : values()) {
            if (option.id.equals(normalized) || option.alias.equals(normalized)) {
                return option;
            }
        }
        return null;
    }

    public static CapabilityOption byOrdinal(int ordinal) {
        CapabilityOption[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
