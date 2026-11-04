package com.komainu.moxia.client.blueprint;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.blueprint.BlueprintPayloads;
import com.komainu.moxia.blueprint.FileChunks;
import com.komainu.moxia.studio.ui.StudioHost;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * La carpeta {@code schematics} del juego (la que usa Litematica) vista desde el Studio: lo que se exporta en el
 * servidor baja aquí, y lo que hay aquí se puede subir para importarlo como plano. Leer y escribir archivos va en un
 * hilo propio; la red y los mensajes, en el del cliente.
 *
 * <p>Lo que baja solo entra en esa carpeta, con un nombre comprobado ({@link FileChunks#validName}), sin pisar nada
 * (si ya hay uno, {@code casa-2.litematic}) y como mucho {@link #MAX_DOWNLOADS} por conexión.</p>
 */
public final class ClientSchematicFiles {
    /** Archivos que pueden bajar en una conexión: un servidor no llena la carpeta. */
    private static final int MAX_DOWNLOADS = 64;
    /** Trozos que suben por tick (32 KiB). */
    private static final int CHUNKS_PER_TICK = 2;
    private static final int MAX_LISTED = 300;
    private static final int MAX_DEPTH = 3;
    private static final long LIST_MILLIS = 2_000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit esquemas");
        thread.setDaemon(true);
        return thread;
    });
    private static final FileChunks.Assembler DOWNLOADS = new FileChunks.Assembler(FileChunks.MAX_BYTES);
    private static final Deque<FileChunks.Chunk> OUTGOING = new ArrayDeque<>();
    private static int downloads;
    private static int transfer = (int) (System.nanoTime() & 0x3FFF_FFFF);
    private static volatile List<StudioHost.LocalFile> listed = List.of();
    private static volatile boolean listing;
    private static long listedAt;

    private ClientSchematicFiles() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayloads.Download.ID,
                (payload, context) -> context.client().execute(() -> receive(payload.chunk())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> send());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            DOWNLOADS.clear();
            OUTGOING.clear();
            downloads = 0;
        }));
    }

    /** {@code <juego>/schematics}. */
    public static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("schematics").toAbsolutePath().normalize();
    }

    // ------------------------------------------------------------------ Bajar

    private static void receive(FileChunks.Chunk chunk) {
        FileChunks.Complete complete;
        try {
            complete = DOWNLOADS.accept(chunk, System.currentTimeMillis()).orElse(null);
        } catch (IllegalArgumentException exception) {
            message(Text.translatable("gui.komaedit.blueprint.download_failed", chunk.name(), exception.getMessage()),
                    Formatting.RED);
            return;
        }
        if (complete == null) {
            return;
        }
        if (++downloads > MAX_DOWNLOADS) {
            message(Text.translatable("gui.komaedit.blueprint.download_limit", MAX_DOWNLOADS), Formatting.RED);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        IO.execute(() -> {
            try {
                complete.verify();
                Path written = write(folder(), complete.name(), complete.data());
                String shown = "schematics/" + written.getFileName();
                client.execute(() -> {
                    listedAt = 0L;
                    message(Text.translatable("gui.komaedit.blueprint.downloaded", shown), Formatting.GREEN);
                });
            } catch (IOException | RuntimeException exception) {
                client.execute(() -> message(Text.translatable("gui.komaedit.blueprint.download_failed",
                        complete.name(), String.valueOf(exception.getMessage())), Formatting.RED));
            }
        });
    }

    /** Escribe dentro de {@code folder} sin pisar otro archivo. */
    private static Path write(Path folder, String name, byte[] data) throws IOException {
        Files.createDirectories(folder);
        Path target = FileChunks.target(folder, name);
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        Files.write(temporary, data);
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    // ------------------------------------------------------------------ Subir

    /**
     * Los esquemas de la carpeta, los más nuevos primero (como mucho {@link #MAX_LISTED}). La lista se rehace en el
     * hilo de los archivos cada pocos segundos: llamarla en cada fotograma no cuesta.
     */
    public static List<StudioHost.LocalFile> list() {
        long now = System.currentTimeMillis();
        if (!listing && now - listedAt > LIST_MILLIS) {
            listing = true;
            listedAt = now;
            IO.execute(() -> {
                try {
                    listed = scan(folder());
                } finally {
                    listing = false;
                }
            });
        }
        return listed;
    }

    static List<StudioHost.LocalFile> scan(Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        List<StudioHost.LocalFile> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(folder, MAX_DEPTH)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> supported(path.getFileName().toString()))
                    .limit(MAX_LISTED * 4L)
                    .forEach(path -> {
                        try {
                            files.add(new StudioHost.LocalFile(folder.relativize(path).toString().replace('\\', '/'),
                                    Files.size(path), Files.getLastModifiedTime(path).toMillis()));
                        } catch (IOException ignored) {
                            // Un archivo que desaparece mientras se lista no sale.
                        }
                    });
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("KomaEdit could not list the schematics folder: {}", exception.getMessage());
        }
        files.sort(Comparator.comparingLong(StudioHost.LocalFile::modified).reversed()
                .thenComparing(StudioHost.LocalFile::path));
        return List.copyOf(files.subList(0, Math.min(MAX_LISTED, files.size())));
    }

    private static boolean supported(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return FileChunks.EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    /**
     * Sube un esquema de la carpeta para importarlo: el servidor enseña lo que trae antes de guardar nada.
     *
     * @param path la ruta dentro de {@code schematics}, como la da {@link #list()}
     * @return {@code false} si el servidor no tiene KomaEdit o ya hay una subida en marcha
     */
    public static boolean upload(String path) {
        if (!ClientPlayNetworking.canSend(BlueprintPayloads.Upload.ID) || !OUTGOING.isEmpty()) {
            return false;
        }
        Path folder = folder();
        Path file = folder.resolve(path).normalize();
        if (!file.startsWith(folder)) {
            return false;
        }
        String name = FileChunks.cleanName(file.getFileName().toString());
        int id = ++transfer;
        MinecraftClient client = MinecraftClient.getInstance();
        IO.execute(() -> {
            try {
                if (Files.size(file) > FileChunks.MAX_BYTES) {
                    throw new IOException(Files.size(file) / 1024 + " KiB; el máximo es " + FileChunks.MAX_BYTES / 1024
                            + " KiB");
                }
                List<FileChunks.Chunk> chunks = FileChunks.split(id, name, Files.readAllBytes(file));
                client.execute(() -> OUTGOING.addAll(chunks));
            } catch (IOException | RuntimeException exception) {
                client.execute(() -> message(Text.translatable("gui.komaedit.blueprint.upload_failed", name,
                        String.valueOf(exception.getMessage())), Formatting.RED));
            }
        });
        return true;
    }

    /** ¿Hay algo subiendo? */
    public static boolean uploading() {
        return !OUTGOING.isEmpty();
    }

    private static void send() {
        if (OUTGOING.isEmpty()) {
            return;
        }
        if (!ClientPlayNetworking.canSend(BlueprintPayloads.Upload.ID)) {
            OUTGOING.clear();
            return;
        }
        for (int sent = 0; sent < CHUNKS_PER_TICK && !OUTGOING.isEmpty(); sent++) {
            ClientPlayNetworking.send(new BlueprintPayloads.Upload(OUTGOING.pollFirst()));
        }
    }

    private static void message(Text text, Formatting color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("KomaEdit · ").formatted(Formatting.DARK_AQUA)
                    .append(text.copy().formatted(color)), false);
        }
    }
}
