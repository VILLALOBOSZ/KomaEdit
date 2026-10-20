package com.komainu.moxia.capability;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lo que cada jugador tiene activado y sus opciones. Inmutable; se guarda en el proyecto del jugador
 * ({@code <mundo>/komaedit/players/<uuid>/capabilities/settings.json}).
 *
 * @param enabled        capacidades activas
 * @param replaceFamily  «Reemplazar familia»: con planchas de abeto, una escalera de roble pasa a escalera de abeto
 * @param extrudeByState la capa de «Extruir» solo sigue bloques con el mismo estado exacto (si no, el mismo tipo)
 * @param extrudeLimit   bloques como mucho por capa
 * @param angelDistance  distancia a la que «Colocar en el aire» pone el bloque
 * @param reachDistance  alcance con «Alcance largo»
 * @param flySpeed       velocidad de vuelo en décimas de la normal (10 = normal)
 * @param tweakProperty  propiedad que cambia «Retocar» (vacía: la primera del bloque)
 */
public record CapabilitySettings(
        Set<BuildCapability> enabled,
        boolean replaceFamily,
        boolean extrudeByState,
        int extrudeLimit,
        int angelDistance,
        int reachDistance,
        int flySpeed,
        String tweakProperty
) {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_EXTRUDE_LIMIT = 65_536;
    public static final CapabilitySettings DEFAULT = new CapabilitySettings(
            EnumSet.noneOf(BuildCapability.class), false, false, 256, 4, 24, 10, ""
    );

    public CapabilitySettings {
        EnumSet<BuildCapability> copy = EnumSet.noneOf(BuildCapability.class);
        if (enabled != null) {
            copy.addAll(enabled);
        }
        enabled = Collections.unmodifiableSet(copy);
        extrudeLimit = Math.clamp(extrudeLimit, 1, MAX_EXTRUDE_LIMIT);
        angelDistance = Math.clamp(angelDistance, 1, CapabilityLimits.VANILLA_MAX_REACH);
        reachDistance = Math.clamp(reachDistance, 5, CapabilityLimits.VANILLA_MAX_REACH);
        flySpeed = Math.clamp(flySpeed, 1, 200);
        tweakProperty = tweakProperty == null ? "" : tweakProperty;
    }

    public boolean has(BuildCapability capability) {
        return enabled.contains(capability);
    }

    public CapabilitySettings with(BuildCapability capability, boolean on) {
        EnumSet<BuildCapability> next = enabled.isEmpty()
                ? EnumSet.noneOf(BuildCapability.class)
                : EnumSet.copyOf(enabled);
        if (on) {
            next.add(capability);
        } else {
            next.remove(capability);
        }
        return new CapabilitySettings(next, replaceFamily, extrudeByState, extrudeLimit, angelDistance,
                reachDistance, flySpeed, tweakProperty);
    }

    public CapabilitySettings withEnabled(Set<BuildCapability> capabilities) {
        return new CapabilitySettings(capabilities, replaceFamily, extrudeByState, extrudeLimit, angelDistance,
                reachDistance, flySpeed, tweakProperty);
    }

    public CapabilitySettings withReplaceFamily(boolean value) {
        return new CapabilitySettings(enabled, value, extrudeByState, extrudeLimit, angelDistance, reachDistance,
                flySpeed, tweakProperty);
    }

    public CapabilitySettings withExtrudeByState(boolean value) {
        return new CapabilitySettings(enabled, replaceFamily, value, extrudeLimit, angelDistance, reachDistance,
                flySpeed, tweakProperty);
    }

    public CapabilitySettings withExtrudeLimit(int value) {
        return new CapabilitySettings(enabled, replaceFamily, extrudeByState, value, angelDistance, reachDistance,
                flySpeed, tweakProperty);
    }

    public CapabilitySettings withAngelDistance(int value) {
        return new CapabilitySettings(enabled, replaceFamily, extrudeByState, extrudeLimit, value, reachDistance,
                flySpeed, tweakProperty);
    }

    public CapabilitySettings withReachDistance(int value) {
        return new CapabilitySettings(enabled, replaceFamily, extrudeByState, extrudeLimit, angelDistance, value,
                flySpeed, tweakProperty);
    }

    public CapabilitySettings withFlySpeed(int value) {
        return new CapabilitySettings(enabled, replaceFamily, extrudeByState, extrudeLimit, angelDistance,
                reachDistance, value, tweakProperty);
    }

    public CapabilitySettings withTweakProperty(String value) {
        return new CapabilitySettings(enabled, replaceFamily, extrudeByState, extrudeLimit, angelDistance,
                reachDistance, flySpeed, value);
    }

    /** Capacidades activas como máscara de bits ({@link BuildCapability#bit()}). */
    public int mask() {
        return mask(enabled);
    }

    public static int mask(Set<BuildCapability> capabilities) {
        int mask = 0;
        for (BuildCapability capability : capabilities) {
            mask |= capability.bit();
        }
        return mask;
    }

    public static Set<BuildCapability> fromMask(int mask) {
        EnumSet<BuildCapability> result = EnumSet.noneOf(BuildCapability.class);
        for (BuildCapability capability : BuildCapability.values()) {
            if ((mask & capability.bit()) != 0) {
                result.add(capability);
            }
        }
        return result;
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        JsonArray active = new JsonArray();
        for (BuildCapability capability : enabled) {
            active.add(capability.id());
        }
        root.add("enabled", active);
        root.addProperty("replaceFamily", replaceFamily);
        root.addProperty("extrudeByState", extrudeByState);
        root.addProperty("extrudeLimit", extrudeLimit);
        root.addProperty("angelDistance", angelDistance);
        root.addProperty("reachDistance", reachDistance);
        root.addProperty("flySpeed", flySpeed);
        root.addProperty("tweakProperty", tweakProperty);
        return root;
    }

    /** Lee lo guardado. Un campo que falte o no se entienda toma el valor por defecto; un id desconocido se ignora. */
    public static CapabilitySettings fromJson(JsonObject root) {
        if (root == null) {
            return DEFAULT;
        }
        int format = integer(root, "format", FORMAT_VERSION);
        if (format > FORMAT_VERSION) {
            throw new IllegalArgumentException("Formato de capacidades no compatible: " + format);
        }
        EnumSet<BuildCapability> active = EnumSet.noneOf(BuildCapability.class);
        if (root.has("enabled") && root.get("enabled").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("enabled")) {
                BuildCapability capability = element.isJsonPrimitive()
                        ? BuildCapability.byName(element.getAsString())
                        : null;
                if (capability != null) {
                    active.add(capability);
                }
            }
        }
        return new CapabilitySettings(
                active,
                bool(root, "replaceFamily", DEFAULT.replaceFamily()),
                bool(root, "extrudeByState", DEFAULT.extrudeByState()),
                integer(root, "extrudeLimit", DEFAULT.extrudeLimit()),
                integer(root, "angelDistance", DEFAULT.angelDistance()),
                integer(root, "reachDistance", DEFAULT.reachDistance()),
                integer(root, "flySpeed", DEFAULT.flySpeed()),
                root.has("tweakProperty") && root.get("tweakProperty").isJsonPrimitive()
                        ? root.get("tweakProperty").getAsString()
                        : DEFAULT.tweakProperty()
        );
    }

    private static int integer(JsonObject root, String key, int fallback) {
        try {
            return root.has(key) ? root.get(key).getAsInt() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        try {
            return root.has(key) ? root.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }
}
