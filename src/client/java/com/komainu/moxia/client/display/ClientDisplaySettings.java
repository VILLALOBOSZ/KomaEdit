package com.komainu.moxia.client.display;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.display.DisplayEditorSettings;
import com.komainu.moxia.persistence.AtomicStorage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Herramienta, marco y pasos del editor de displays en {@code config/komaedit-displays.json}. */
public final class ClientDisplaySettings {
    private static final String FILE = "komaedit-displays.json";
    private static volatile DisplayEditorSettings current;

    private ClientDisplaySettings() {
    }

    public static DisplayEditorSettings get() {
        DisplayEditorSettings settings = current;
        if (settings == null) {
            settings = load();
            current = settings;
        }
        return settings;
    }

    public static void set(DisplayEditorSettings settings) {
        if (settings.equals(get())) {
            return;
        }
        current = settings;
        try {
            AtomicStorage.writeString(file(), settings.toJson());
        } catch (IOException exception) {
            KomaEditMod.LOGGER.warn("Could not save {}", FILE, exception);
        }
    }

    private static DisplayEditorSettings load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return DisplayEditorSettings.DEFAULT;
        }
        try {
            return DisplayEditorSettings.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not read {}; using the defaults", FILE, exception);
            return DisplayEditorSettings.DEFAULT;
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE);
    }
}
