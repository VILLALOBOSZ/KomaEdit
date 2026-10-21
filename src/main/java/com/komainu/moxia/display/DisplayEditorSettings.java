package com.komainu.moxia.display;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Cómo tiene el jugador el editor de displays: herramienta, marco local o global y pasos. Lo guarda el cliente en
 * {@code config/komaedit-displays.json}; lógica pura.
 */
public record DisplayEditorSettings(DisplayGizmo.Tool tool, boolean local, DisplaySteps steps) {
    public static final DisplayEditorSettings DEFAULT = new DisplayEditorSettings(DisplayGizmo.Tool.MOVE, false,
            DisplaySteps.DEFAULT);

    public DisplayEditorSettings {
        if (tool == null || steps == null) {
            throw new IllegalArgumentException("Ajustes del editor incompletos");
        }
    }

    public DisplayEditorSettings withTool(DisplayGizmo.Tool value) {
        return new DisplayEditorSettings(value, local, steps);
    }

    public DisplayEditorSettings withLocal(boolean value) {
        return new DisplayEditorSettings(tool, value, steps);
    }

    public DisplayEditorSettings withSteps(DisplaySteps value) {
        return new DisplayEditorSettings(tool, local, value);
    }

    /**
     * El marco del gizmo: el del objeto en local y la identidad en global. Al escalar, siempre el del objeto: la
     * escala de un display va en sus propios ejes.
     */
    public Quat frame(Quat objectFrame) {
        return local || tool == DisplayGizmo.Tool.SCALE ? objectFrame : Quat.IDENTITY;
    }

    /** Si al mover se usa la rejilla del mundo (en global) o los ejes del objeto (en local). */
    public boolean globalGrid() {
        return !local;
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("tool", tool.id());
        root.addProperty("local", local);
        root.addProperty("move", steps.move().id());
        root.addProperty("angle", steps.angle().id());
        return root.toString();
    }

    /** Lee los ajustes; lo que falte o no se entienda queda como de serie. */
    public static DisplayEditorSettings parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        DisplayEditorSettings result = DEFAULT;
        try {
            if (root.has("tool")) {
                result = result.withTool(DisplayGizmo.Tool.parse(root.get("tool").getAsString()));
            }
        } catch (IllegalArgumentException ignored) {
            // De serie.
        }
        if (root.has("local")) {
            result = result.withLocal(root.get("local").getAsBoolean());
        }
        DisplaySteps steps = result.steps();
        try {
            if (root.has("move")) {
                steps = steps.withMove(DisplaySteps.Move.parse(root.get("move").getAsString()));
            }
        } catch (IllegalArgumentException ignored) {
            // De serie.
        }
        try {
            if (root.has("angle")) {
                steps = steps.withAngle(DisplaySteps.Angle.parse(root.get("angle").getAsString()));
            }
        } catch (IllegalArgumentException ignored) {
            // De serie.
        }
        return result.withSteps(steps);
    }
}
