package com.komainu.moxia.studio.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Las tarjetas que se unieron en otra y cómo pasan sus ajustes guardados ({@code studio.json} de antes del formato 4)
 * a la que las reúne: «Pintar terreno» ({@code paintbrush}) y «Pintar superficie» ({@code paint}) son ahora
 * {@code paint}, con el modo Natural para la superficie; nieve, drenar, inundar, gravedad y bioma son modos de
 * {@code touchup} («Retocar»). Sin Minecraft, con pruebas.
 */
public final class ToolMigration {
    /** El primer formato de {@code studio.json} con las tarjetas unidas. */
    public static final int FORMAT = 4;

    /** Id de antes → id de la tarjeta que la reúne. */
    private static final Map<String, String> RENAMED = Map.of(
            "paintbrush", "paint",
            "snow", "touchup",
            "drain", "touchup",
            "waterlog", "touchup",
            "gravity", "touchup",
            "biome", "touchup");

    /** El modo de «Retocar» de cada tarjeta de antes. */
    private static final Map<String, String> TOUCH_UP_MODE = Map.of(
            "snow", TouchUpCard.SNOW,
            "drain", TouchUpCard.DRAIN,
            "waterlog", TouchUpCard.FLOOD,
            "gravity", TouchUpCard.GRAVITY,
            "biome", TouchUpCard.BIOME);

    /** Ajustes que cambiaron de nombre al entrar en «Retocar» (los demás se llaman igual). */
    private static final Map<String, Map<String, String>> RENAMED_PARAMS = Map.of(
            "snow", Map.of("radius", "drift"),
            "drain", Map.of("mode", "fluid"));

    private ToolMigration() {
    }

    /** El id de ahora de una tarjeta: el mismo si no se unió con otra. */
    public static String id(String stored) {
        return stored == null ? null : RENAMED.getOrDefault(stored, stored);
    }

    /** Si el id es de una tarjeta que ya no existe sola. */
    public static boolean renamed(String stored) {
        return RENAMED.containsKey(stored);
    }

    /**
     * Los ajustes guardados de antes del formato 4 en las tarjetas de ahora. La herramienta que se usó la última vez
     * ({@code lastTool}) decide el modo de la tarjeta que reúne varias y, si dos tienen el mismo ajuste (la máscara),
     * gana la suya.
     *
     * @param stored  los ajustes por id de tarjeta, tal como estaban en el archivo
     * @param format  el formato del archivo; desde {@link #FORMAT} se devuelven tal cual
     */
    public static Map<String, Map<String, Object>> toolValues(Map<String, Map<String, Object>> stored, int format,
                                                             String lastTool) {
        if (format >= FORMAT) {
            return stored;
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        stored.forEach((id, values) -> {
            if (!RENAMED.containsKey(id) && !id.equals("paint")) {
                result.put(id, new LinkedHashMap<>(values));
            }
        });
        Map<String, Object> paint = paint(stored, lastTool);
        if (!paint.isEmpty()) {
            result.put("paint", paint);
        }
        Map<String, Object> touchUp = touchUp(stored, lastTool);
        if (!touchUp.isEmpty()) {
            result.put("touchup", touchUp);
        }
        return result;
    }

    /** «Pintar terreno» tal cual y, de «Pintar superficie», su paleta (y su máscara si la otra no tenía). */
    private static Map<String, Object> paint(Map<String, Map<String, Object>> stored, String lastTool) {
        Map<String, Object> brush = stored.getOrDefault("paintbrush", Map.of());
        Map<String, Object> surface = stored.getOrDefault("paint", Map.of());
        Map<String, Object> result = new LinkedHashMap<>(brush);
        if (surface.get("palette") != null) {
            result.put("palette", surface.get("palette"));
        }
        boolean surfaceLast = "paint".equals(lastTool);
        if (surface.get("mask") != null && (surfaceLast || !result.containsKey("mask"))) {
            result.put("mask", surface.get("mask"));
        }
        if (surfaceLast) {
            result.put("gradient", PaintCard.NATURAL);
            result.put(ToolUse.PARAM, ToolUse.SELECTION.id());
        }
        return result;
    }

    /** Las cinco tarjetas de utilidades como modos de «Retocar»; la última usada decide el modo. */
    private static Map<String, Object> touchUp(Map<String, Map<String, Object>> stored, String lastTool) {
        Map<String, Object> result = new LinkedHashMap<>();
        // Primero las demás y la última usada encima, para que sus ajustes compartidos (la máscara) ganen.
        List<String> order = new java.util.ArrayList<>(TOUCH_UP_MODE.keySet().stream().sorted().toList());
        if (order.remove(lastTool)) {
            order.add(lastTool);
        }
        for (String old : order) {
            Map<String, Object> values = stored.get(old);
            if (values == null) {
                continue;
            }
            Map<String, String> renames = RENAMED_PARAMS.getOrDefault(old, Map.of());
            values.forEach((param, value) -> result.put(renames.getOrDefault(param, param), value));
        }
        if (TOUCH_UP_MODE.containsKey(lastTool)) {
            result.put(TouchUpCard.MODE, TOUCH_UP_MODE.get(lastTool));
        }
        return result;
    }
}
