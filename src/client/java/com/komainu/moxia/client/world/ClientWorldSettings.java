package com.komainu.moxia.client.world;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.feedback.WorldFeedbackSettings;
import com.komainu.moxia.persistence.AtomicStorage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Los ajustes de lo que KomaEdit dibuja y suena en el mundo, en {@code config/komaedit-client.json}. Se leen una vez
 * al arrancar y se guardan al cambiarlos desde {@link WorldSettingsScreen}.
 */
public final class ClientWorldSettings {
    private static final String FILE = "komaedit-client.json";
    private static volatile WorldFeedbackSettings current;

    private ClientWorldSettings() {
    }

    public static WorldFeedbackSettings get() {
        WorldFeedbackSettings settings = current;
        if (settings == null) {
            settings = load();
            current = settings;
        }
        return settings;
    }

    public static void set(WorldFeedbackSettings settings) {
        current = settings;
        try {
            AtomicStorage.writeString(file(), settings.toJson());
        } catch (IOException exception) {
            KomaEditMod.LOGGER.warn("Could not save {}", FILE, exception);
        }
    }

    public static WorldFeedbackSettings defaults() {
        return WorldFeedbackSettings.defaults(FabricLoader.getInstance().isModLoaded("worldeditcui"));
    }

    private static WorldFeedbackSettings load() {
        WorldFeedbackSettings defaults = defaults();
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return defaults;
        }
        try {
            return WorldFeedbackSettings.parse(Files.readString(file, StandardCharsets.UTF_8), defaults);
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not read {}; using the defaults", FILE, exception);
            return defaults;
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE);
    }
}
