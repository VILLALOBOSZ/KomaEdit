package com.komainu.moxia.client.blueprint;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.blueprint.BlueprintPayloads;
import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.blueprint.ThumbnailChunks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La biblioteca de planos en el cliente: el último catálogo del servidor y las miniaturas ya recibidas, guardadas
 * como texturas por su huella. Una miniatura solo se pide una vez por sesión; si un plano cambia, cambia su huella y
 * se pide la nueva. Se vacía al salir del servidor. Todo pasa en el hilo del cliente.
 */
public final class ClientBlueprintCatalog {
    /** Texturas de miniatura como mucho; las menos usadas se liberan primero. */
    private static final int MAX_TEXTURES = 256;
    private static final long REFRESH_MILLIS = 1_500L;

    private static List<BlueprintSummary> blueprints = List.of();
    private static boolean received;
    private static boolean canSave;
    private static boolean canPlace;
    private static boolean canStamp;
    private static boolean canShare;
    private static long refreshedAt;
    private static final ThumbnailChunks.Assembler ASSEMBLER = new ThumbnailChunks.Assembler(32);
    private static final Map<String, Identifier> TEXTURES = new LinkedHashMap<>(64, 0.75F, true);
    private static final Set<String> REQUESTED = new HashSet<>();

    private ClientBlueprintCatalog() {
    }

    public static void update(BlueprintPayloads.Catalog catalog) {
        blueprints = catalog.blueprints();
        canSave = catalog.canSave();
        canPlace = catalog.canPlace();
        canStamp = catalog.canStamp();
        canShare = catalog.canShare();
        received = true;
    }

    public static List<BlueprintSummary> blueprints() {
        return blueprints;
    }

    public static boolean received() {
        return received;
    }

    public static boolean canSave() {
        return canSave;
    }

    public static boolean canPlace() {
        return canPlace;
    }

    public static boolean canStamp() {
        return canStamp;
    }

    public static boolean canShare() {
        return canShare;
    }

    /** Pide el catálogo; si se pidió hace nada no repite. */
    public static void refresh(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - refreshedAt < REFRESH_MILLIS) {
            return;
        }
        refreshedAt = now;
        if (ClientPlayNetworking.canSend(BlueprintPayloads.Request.ID)) {
            ClientPlayNetworking.send(new BlueprintPayloads.Request(BlueprintPayloads.Request.CATALOG, List.of()));
        }
    }

    /** La textura de la miniatura, o {@code null} si aún no ha llegado. */
    public static Identifier thumbnail(String hash) {
        return hash == null || hash.isEmpty() ? null : TEXTURES.get(hash);
    }

    /** Pide las miniaturas que faltan (las ya pedidas no se repiten). */
    public static void request(Collection<String> hashes) {
        List<String> missing = new ArrayList<>();
        for (String hash : hashes) {
            if (ThumbnailChunks.validHash(hash) && !TEXTURES.containsKey(hash) && REQUESTED.add(hash)) {
                missing.add(hash);
            }
        }
        if (missing.isEmpty() || !ClientPlayNetworking.canSend(BlueprintPayloads.Request.ID)) {
            return;
        }
        for (int from = 0; from < missing.size(); from += BlueprintPayloads.MAX_REQUESTED) {
            ClientPlayNetworking.send(new BlueprintPayloads.Request(BlueprintPayloads.Request.THUMBNAILS,
                    missing.subList(from, Math.min(missing.size(), from + BlueprintPayloads.MAX_REQUESTED))));
        }
    }

    /** Un trozo de miniatura; cuando llega el último se convierte en textura. */
    public static void receive(ThumbnailChunks.Chunk chunk) {
        ASSEMBLER.accept(chunk).ifPresent(png -> register(chunk.hash(), png));
    }

    private static void register(String hash, byte[] png) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            NativeImage image = NativeImage.read(png);
            Identifier id = Identifier.of(KomaEditMod.MOD_ID, "blueprint/" + hash.substring(0, 40));
            client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
            TEXTURES.put(hash, id);
            trim(client);
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("KomaEdit could not read a blueprint thumbnail: {}", exception.getMessage());
        }
    }

    private static void trim(MinecraftClient client) {
        Iterator<Map.Entry<String, Identifier>> oldest = TEXTURES.entrySet().iterator();
        while (TEXTURES.size() > MAX_TEXTURES && oldest.hasNext()) {
            Map.Entry<String, Identifier> entry = oldest.next();
            client.getTextureManager().destroyTexture(entry.getValue());
            REQUESTED.remove(entry.getKey());
            oldest.remove();
        }
    }

    public static void clear() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Identifier id : TEXTURES.values()) {
            client.getTextureManager().destroyTexture(id);
        }
        TEXTURES.clear();
        REQUESTED.clear();
        ASSEMBLER.clear();
        blueprints = List.of();
        received = false;
        refreshedAt = 0L;
    }
}
