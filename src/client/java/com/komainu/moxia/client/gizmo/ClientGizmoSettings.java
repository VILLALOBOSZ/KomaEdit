package com.komainu.moxia.client.gizmo;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.gizmo.GhostPayloads;
import com.komainu.moxia.gizmo.GizmoSettings;
import com.komainu.moxia.persistence.AtomicStorage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Las preferencias del fantasma con gizmo en {@code config/komaedit-gizmo.json}. Al cambiar el modo se lo dice al
 * servidor (que también lo recibe al entrar).
 */
public final class ClientGizmoSettings {
    private static final String FILE = "komaedit-gizmo.json";
    private static volatile GizmoSettings current;

    private ClientGizmoSettings() {
    }

    public static GizmoSettings get() {
        GizmoSettings settings = current;
        if (settings == null) {
            settings = load();
            current = settings;
        }
        return settings;
    }

    public static void set(GizmoSettings settings) {
        GizmoSettings previous = get();
        current = settings;
        try {
            AtomicStorage.writeString(file(), settings.toJson());
        } catch (IOException exception) {
            KomaEditMod.LOGGER.warn("Could not save {}", FILE, exception);
        }
        if (previous.mode() != settings.mode()) {
            announce();
        }
    }

    /** Le dice al servidor el modo (si el servidor tiene KomaEdit). */
    public static void announce() {
        if (ClientPlayNetworking.canSend(GhostPayloads.Mode.ID)) {
            ClientPlayNetworking.send(new GhostPayloads.Mode(get().mode()));
        }
    }

    private static GizmoSettings load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return GizmoSettings.DEFAULT;
        }
        try {
            return GizmoSettings.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not read {}; using the defaults", FILE, exception);
            return GizmoSettings.DEFAULT;
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE);
    }
}
