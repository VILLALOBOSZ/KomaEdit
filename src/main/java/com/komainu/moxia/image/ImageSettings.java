package com.komainu.moxia.image;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Configuración de {@code /komaedit image} en {@code config/komaedit/images.json}. Por defecto no se descarga nada
 * de internet: el admin tiene que activar {@code allowUrls} y poner los dominios en {@code allowedHosts}.
 *
 * @param allowUrls        si se aceptan URL {@code https}
 * @param urlsRequireAdmin las URL solo para quien tenga {@code komaedit.admin.image.url} (o sea operador)
 * @param allowedHosts     dominios permitidos: {@code ejemplo.com} exacto, {@code *.ejemplo.com} sus subdominios
 * @param maxImageBytes    tamaño máximo del archivo o de la descarga
 * @param maxPixels        píxeles máximos que puede declarar la imagen (ancho × alto)
 * @param timeoutSeconds   tiempo máximo de una descarga entera, con sus redirecciones
 * @param maxBlocksPerSide lado máximo de la obra en bloques
 */
public record ImageSettings(
        boolean allowUrls,
        boolean urlsRequireAdmin,
        List<String> allowedHosts,
        long maxImageBytes,
        long maxPixels,
        int timeoutSeconds,
        int maxBlocksPerSide
) {
    public static final int FORMAT_VERSION = 1;
    public static final ImageSettings DEFAULT = new ImageSettings(false, true, List.of(), 8L * 1024 * 1024,
            2048L * 2048, 10, 512);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ImageSettings {
        List<String> hosts = new ArrayList<>();
        for (String host : allowedHosts) {
            String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                if (!normalized.matches("(\\*\\.)?[a-z0-9.-]+")) {
                    throw new IllegalArgumentException("allowedHosts: «" + host + "» no es un dominio (ejemplo.com o "
                            + "*.ejemplo.com)");
                }
                hosts.add(normalized);
            }
        }
        allowedHosts = List.copyOf(hosts);
        if (maxImageBytes < 1024 || maxImageBytes > 64L * 1024 * 1024) {
            throw new IllegalArgumentException("maxImageBytes va de 1024 a 67108864");
        }
        if (maxPixels < 1 || maxPixels > 8192L * 8192) {
            throw new IllegalArgumentException("maxPixels va de 1 a 67108864");
        }
        if (timeoutSeconds < 1 || timeoutSeconds > 60) {
            throw new IllegalArgumentException("timeoutSeconds va de 1 a 60");
        }
        if (maxBlocksPerSide < 1 || maxBlocksPerSide > 4096) {
            throw new IllegalArgumentException("maxBlocksPerSide va de 1 a 4096");
        }
    }

    /** Lee el JSON; las claves que falten toman el valor por defecto. */
    public static ImageSettings parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT_VERSION;
        if (format != FORMAT_VERSION) {
            throw new IllegalArgumentException("Formato de images.json no admitido: " + format);
        }
        List<String> hosts = new ArrayList<>();
        if (root.has("allowedHosts")) {
            for (JsonElement element : root.getAsJsonArray("allowedHosts")) {
                hosts.add(element.getAsString());
            }
        } else {
            hosts.addAll(DEFAULT.allowedHosts());
        }
        return new ImageSettings(
                bool(root, "allowUrls", DEFAULT.allowUrls()),
                bool(root, "urlsRequireAdmin", DEFAULT.urlsRequireAdmin()),
                hosts,
                root.has("maxImageBytes") ? root.get("maxImageBytes").getAsLong() : DEFAULT.maxImageBytes(),
                root.has("maxPixels") ? root.get("maxPixels").getAsLong() : DEFAULT.maxPixels(),
                root.has("timeoutSeconds") ? root.get("timeoutSeconds").getAsInt() : DEFAULT.timeoutSeconds(),
                root.has("maxBlocksPerSide") ? root.get("maxBlocksPerSide").getAsInt() : DEFAULT.maxBlocksPerSide()
        );
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("allowUrls", allowUrls);
        root.addProperty("urlsRequireAdmin", urlsRequireAdmin);
        JsonArray hosts = new JsonArray();
        allowedHosts.forEach(hosts::add);
        root.add("allowedHosts", hosts);
        root.addProperty("maxImageBytes", maxImageBytes);
        root.addProperty("maxPixels", maxPixels);
        root.addProperty("timeoutSeconds", timeoutSeconds);
        root.addProperty("maxBlocksPerSide", maxBlocksPerSide);
        return GSON.toJson(root);
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        return root.has(key) ? root.get(key).getAsBoolean() : fallback;
    }
}
