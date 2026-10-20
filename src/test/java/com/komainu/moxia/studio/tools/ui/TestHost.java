package com.komainu.moxia.studio.tools.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.tools.ToolContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** El Studio de mentira de las pruebas: traduce con los archivos de idioma y apunta todo lo que se le pide. */
final class TestHost implements ToolsPanel.Host, JobStrip.Actions {
    private final Map<String, String> lang;
    final List<String> sent = new ArrayList<>();
    final List<String> copied = new ArrayList<>();
    final StudioPreferences preferences = new StudioPreferences();
    String status = "";
    int requests;
    int pathVisits;
    boolean shift;
    double mouseX;
    long now = 1_000_000L;
    ToolContext context = ToolContext.UNKNOWN;
    ToolsInfo info = ToolsInfo.EMPTY;
    Consumer<String> picker;

    TestHost(String language) {
        lang = load(language);
    }

    static Map<String, String> load(String language) {
        Path file = Path.of("src", "main", "resources", "assets", "komaedit", "lang", language + ".json");
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
            return values;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Como el {@code Text.translatable} de Minecraft: %s en orden, %1$s por posición y %% para el tanto por ciento. */
    static String format(String pattern, Object... arguments) {
        StringBuilder out = new StringBuilder();
        int next = 0;
        for (int index = 0; index < pattern.length(); index++) {
            char character = pattern.charAt(index);
            if (character != '%' || index + 1 >= pattern.length()) {
                out.append(character);
                continue;
            }
            char following = pattern.charAt(index + 1);
            if (following == '%') {
                out.append('%');
                index++;
            } else if (following == 's') {
                out.append(next < arguments.length ? arguments[next] : "");
                next++;
                index++;
            } else if (Character.isDigit(following) && pattern.indexOf("$s", index) == index + 2) {
                int position = following - '1';
                out.append(position < arguments.length ? arguments[position] : "");
                index += 3;
            } else {
                out.append(character);
            }
        }
        return out.toString();
    }

    @Override
    public String tr(String key, Object... arguments) {
        String pattern = lang.get(key);
        return pattern == null ? key : format(pattern, arguments);
    }

    @Override
    public void send(String command) {
        sent.add(command);
    }

    @Override
    public void pickBlock(Consumer<String> onPick) {
        picker = onPick;
    }

    @Override
    public void status(String text) {
        status = text;
    }

    @Override
    public void copy(String text) {
        copied.add(text);
    }

    @Override
    public void goToPath() {
        pathVisits++;
    }

    @Override
    public boolean shift() {
        return shift;
    }

    @Override
    public double mouseX() {
        return mouseX;
    }

    @Override
    public long now() {
        return now;
    }

    @Override
    public ToolContext context() {
        return context;
    }

    @Override
    public ToolsInfo info() {
        return info;
    }

    @Override
    public void requestState() {
        requests++;
    }

    @Override
    public StudioPreferences preferences() {
        return preferences;
    }

    @Override
    public void savePreferences() {
    }

    String last() {
        return sent.isEmpty() ? null : sent.getLast();
    }
}
