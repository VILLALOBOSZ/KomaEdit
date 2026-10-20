package com.komainu.moxia.gizmo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Lo que cada jugador decide del fantasma con gizmo, en su equipo ({@code config/komaedit-gizmo.json}); el cliente
 * le dice el modo al servidor al entrar y al cambiarlo. Un archivo roto o a medias toma los valores de serie.
 *
 * @param mode  cuándo se coloca con el fantasma
 * @param wheel la rueda del ratón acerca y aleja el fantasma (si no, cambia de casilla como siempre)
 */
public record GizmoSettings(Mode mode, boolean wheel) {
    public static final int FORMAT = 1;
    public static final GizmoSettings DEFAULT = new GizmoSettings(Mode.ALWAYS, true);

    public enum Mode {
        /** Todo lo que se puede mover sale como fantasma (también con {@code -v}). */
        ALWAYS("always"),
        /** Solo con {@code -v}: la orden normal coloca al momento, como antes. */
        PREVIEW("preview"),
        /** Nunca: todo como antes ({@code -v}, la vista previa de siempre). */
        OFF("off");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Mode byId(String id, Mode fallback) {
            for (Mode mode : values()) {
                if (mode.id.equalsIgnoreCase(id)) {
                    return mode;
                }
            }
            return fallback;
        }

        public static Mode byOrdinal(int ordinal) {
            Mode[] modes = values();
            return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : OFF;
        }
    }

    public GizmoSettings {
        mode = mode == null ? Mode.ALWAYS : mode;
    }

    public GizmoSettings withMode(Mode value) {
        return new GizmoSettings(value, wheel);
    }

    public GizmoSettings withWheel(boolean value) {
        return new GizmoSettings(mode, value);
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("mode", mode.id());
        root.addProperty("wheel", wheel);
        return root.toString();
    }

    public static GizmoSettings parse(String json) {
        try {
            JsonElement element = JsonParser.parseString(json == null ? "" : json);
            if (!element.isJsonObject()) {
                return DEFAULT;
            }
            JsonObject root = element.getAsJsonObject();
            Mode mode = DEFAULT.mode();
            JsonElement modeElement = root.get("mode");
            if (modeElement != null && modeElement.isJsonPrimitive() && modeElement.getAsJsonPrimitive().isString()) {
                mode = Mode.byId(modeElement.getAsString(), DEFAULT.mode());
            }
            boolean wheel = DEFAULT.wheel();
            JsonElement wheelElement = root.get("wheel");
            if (wheelElement != null && wheelElement.isJsonPrimitive()
                    && wheelElement.getAsJsonPrimitive().isBoolean()) {
                wheel = wheelElement.getAsBoolean();
            }
            return new GizmoSettings(mode, wheel);
        } catch (RuntimeException exception) {
            return DEFAULT;
        }
    }
}
