package com.komainu.moxia.blueprint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.persistence.TolerantListing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Los planos en disco, sin Minecraft: cada uno son tres archivos con el mismo nombre, el esquema de WorldEdit
 * ({@code .schem}), sus metadatos ({@code .json}) y la miniatura ({@code .png}).
 *
 * <pre>
 * blueprints/players/&lt;uuid&gt;/&lt;nombre&gt;.schem|.json|.png   los de cada jugador
 * blueprints/shared/&lt;nombre&gt;.schem|.json|.png             los compartidos con el servidor
 * </pre>
 *
 * <p>El JSON se escribe el último y es el que da el plano por bueno: un cierre a medias deja el plano anterior o
 * el nuevo, nunca uno sin esquema. Todo pasa por {@link AtomicStorage}. Los listados se guardan en memoria y se
 * releen si cambia la carpeta.</p>
 */
public final class BlueprintLibrary {
    public static final String SCHEMATIC = ".schem";
    public static final String META = ".json";
    public static final String THUMBNAIL = ".png";
    /** Una miniatura más grande que esto no es nuestra: no se lee ni se manda. */
    public static final int MAX_THUMBNAIL_BYTES = 256 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path root;
    private final Map<Path, Listing> listings = new ConcurrentHashMap<>();

    /** Un plano en disco. */
    public record Entry(BlueprintMeta meta, Path schematic, Path thumbnail) {
        public String reference() {
            return meta.reference();
        }
    }

    private record Listing(long modified, List<Entry> entries) {
    }

    public BlueprintLibrary(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path playerDirectory(UUID owner) {
        return root.resolve("players").resolve(owner.toString());
    }

    public Path sharedDirectory() {
        return root.resolve("shared");
    }

    /** Rehace los archivos que dejó a medias un cierre del servidor. */
    public void recover() {
        try {
            AtomicStorage.recoverTree(root);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // ------------------------------------------------------------------ Guardar

    /**
     * Guarda (o reemplaza) un plano. La huella de la miniatura se calcula aquí, así que la de {@code meta} da igual.
     *
     * @param unlimited se salta los límites de número y tamaño
     */
    public synchronized Entry save(BlueprintMeta meta, byte[] schematic, byte[] thumbnail, BlueprintLimits limits,
                                   boolean unlimited) {
        limits.checkSize(meta.sizeX(), meta.sizeY(), meta.sizeZ(), unlimited);
        limits.checkFile(schematic.length);
        if (thumbnail.length > MAX_THUMBNAIL_BYTES) {
            throw new IllegalArgumentException("La miniatura es demasiado grande");
        }
        Path directory = directory(meta);
        boolean replacing = Files.isRegularFile(directory.resolve(meta.name() + META));
        if (meta.scope() == BlueprintScope.PLAYER) {
            limits.checkCount(own(meta.owner()).size(), replacing, unlimited);
        } else {
            limits.checkShared(shared().size(), replacing, unlimited);
        }
        BlueprintMeta stored = new BlueprintMeta(meta.name(), meta.scope(), meta.owner(), meta.author(),
                meta.created(), meta.updated(), meta.sizeX(), meta.sizeY(), meta.sizeZ(), meta.blocks(), meta.tags(),
                sha256(thumbnail), meta.schematicFormat(), meta.sharedBy(), meta.sharedAt());
        return write(directory, stored, schematic, thumbnail);
    }

    /**
     * Copia un plano propio a los compartidos. Si ya hay uno compartido con ese nombre, solo lo reemplaza su mismo
     * dueño o quien pueda gestionar los compartidos ({@code replaceOthers}).
     */
    public synchronized Entry share(Entry own, String sharedBy, long now, BlueprintLimits limits, boolean unlimited,
                                    boolean replaceOthers) {
        if (own.meta().scope() != BlueprintScope.PLAYER) {
            throw new IllegalArgumentException("«" + own.reference() + "» ya es un plano compartido");
        }
        Optional<Entry> existing = sharedEntry(own.meta().name());
        if (existing.isPresent() && !existing.get().meta().owner().equals(own.meta().owner()) && !replaceOthers) {
            throw new IllegalArgumentException("Ya hay un plano compartido «" + own.meta().name() + "» de "
                    + existing.get().meta().author() + ": guarda el tuyo con otro nombre");
        }
        limits.checkShared(shared().size(), existing.isPresent(), unlimited);
        byte[] schematic = read(own.schematic());
        limits.checkFile(schematic.length);
        byte[] thumbnail = Files.isRegularFile(own.thumbnail()) ? read(own.thumbnail()) : new byte[0];
        return write(sharedDirectory(), own.meta().asShared(sharedBy, now), schematic, thumbnail);
    }

    /** Cambia las etiquetas sin tocar el esquema ni la miniatura. */
    public synchronized Entry retag(Entry entry, List<String> tags, long now) {
        BlueprintMeta meta = entry.meta().withTags(tags, now);
        Path directory = entry.schematic().getParent();
        writeMeta(directory, meta);
        invalidate(directory);
        return new Entry(meta, entry.schematic(), entry.thumbnail());
    }

    public synchronized boolean delete(Entry entry) {
        Path directory = entry.schematic().getParent();
        String name = entry.meta().name();
        try {
            boolean deleted = Files.deleteIfExists(directory.resolve(name + META));
            for (String extension : new String[]{META, SCHEMATIC, THUMBNAIL}) {
                Path file = directory.resolve(name + extension);
                Files.deleteIfExists(file);
                Files.deleteIfExists(AtomicStorage.temporary(file));
                Files.deleteIfExists(AtomicStorage.backup(file));
            }
            return deleted;
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo borrar el plano: " + exception.getMessage(), exception);
        } finally {
            invalidate(directory);
        }
    }

    // ------------------------------------------------------------------ Leer

    public List<Entry> own(UUID owner) {
        return listing(playerDirectory(owner));
    }

    public List<Entry> shared() {
        return listing(sharedDirectory());
    }

    /** Los propios y después los compartidos, cada grupo por nombre. */
    public List<Entry> visible(UUID owner) {
        List<Entry> all = new ArrayList<>(own(owner));
        all.addAll(shared());
        return List.copyOf(all);
    }

    /** Sin {@code servidor/}: primero el propio y, si no hay, el compartido. */
    public Optional<Entry> find(UUID owner, BlueprintRef ref) {
        if (!ref.sharedOnly()) {
            Optional<Entry> own = entry(playerDirectory(owner), ref.name());
            if (own.isPresent()) {
                return own;
            }
        }
        return sharedEntry(ref.name());
    }

    public Entry require(UUID owner, BlueprintRef ref) {
        return find(owner, ref).orElseThrow(() -> new IllegalArgumentException(ref.sharedOnly()
                ? "No hay ningún plano compartido «" + ref.name() + "»"
                : "No tienes ningún plano «" + ref.name() + "» (ni hay uno compartido con ese nombre)"));
    }

    /** Los planos visibles con esa etiqueta. */
    public List<Entry> tagged(UUID owner, String tag) {
        String normalized = BlueprintTags.normalize(tag);
        return visible(owner).stream().filter(entry -> entry.meta().hasTag(normalized)).toList();
    }

    public List<Entry> search(UUID owner, String query) {
        return BlueprintSearch.parse(query).filter(visible(owner), Entry::meta);
    }

    /** La miniatura con esa huella entre los planos que ve el jugador. */
    public Optional<Entry> thumbnail(UUID viewer, String hash) {
        for (Entry entry : visible(viewer)) {
            if (entry.meta().thumbnail().equals(hash) && Files.isRegularFile(entry.thumbnail())) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public byte[] readSchematic(Entry entry) {
        return read(entry.schematic());
    }

    public byte[] readThumbnail(Entry entry) {
        try {
            if (Files.size(entry.thumbnail()) > MAX_THUMBNAIL_BYTES) {
                throw new IllegalArgumentException("La miniatura de " + entry.reference() + " es demasiado grande");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer la miniatura: " + exception.getMessage(), exception);
        }
        return read(entry.thumbnail());
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }

    // ------------------------------------------------------------------ Interno

    private Path directory(BlueprintMeta meta) {
        return meta.scope() == BlueprintScope.SHARED ? sharedDirectory() : playerDirectory(meta.owner());
    }

    private Optional<Entry> sharedEntry(String name) {
        return entry(sharedDirectory(), name);
    }

    private Optional<Entry> entry(Path directory, String name) {
        String normalized = BlueprintRef.name(name);
        return listing(directory).stream().filter(entry -> entry.meta().name().equals(normalized)).findFirst();
    }

    private Entry write(Path directory, BlueprintMeta meta, byte[] schematic, byte[] thumbnail) {
        try {
            Files.createDirectories(directory);
            writeBytes(directory.resolve(meta.name() + SCHEMATIC), schematic);
            Path png = directory.resolve(meta.name() + THUMBNAIL);
            if (thumbnail.length > 0) {
                writeBytes(png, thumbnail);
            } else {
                Files.deleteIfExists(png);
            }
            writeMeta(directory, meta);
            return new Entry(meta, directory.resolve(meta.name() + SCHEMATIC), png);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar el plano: " + exception.getMessage(), exception);
        } finally {
            invalidate(directory);
        }
    }

    private static void writeMeta(Path directory, BlueprintMeta meta) {
        try {
            AtomicStorage.writeString(directory.resolve(meta.name() + META), GSON.toJson(meta.toJson()));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar el plano: " + exception.getMessage(), exception);
        }
    }

    private static void writeBytes(Path target, byte[] data) throws IOException {
        Path temporary = AtomicStorage.temporary(target);
        Files.write(temporary, data);
        AtomicStorage.replace(temporary, target);
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer " + file.getFileName() + ": " + exception.getMessage(),
                    exception);
        }
    }

    private void invalidate(Path directory) {
        listings.remove(directory);
    }

    private List<Entry> listing(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        long modified;
        try {
            modified = Files.getLastModifiedTime(directory).toMillis();
        } catch (IOException exception) {
            modified = -1L;
        }
        Listing cached = listings.get(directory);
        if (cached != null && cached.modified() == modified && modified >= 0) {
            return cached.entries();
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(META))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudieron listar los planos: " + exception.getMessage(), exception);
        }
        List<Entry> entries = TolerantListing.readAll(files, file -> readEntry(directory, file), "el plano").stream()
                .filter(entry -> Files.isRegularFile(entry.schematic()))
                .toList();
        listings.put(directory, new Listing(modified, entries));
        return entries;
    }

    private static Entry readEntry(Path directory, Path file) {
        try {
            BlueprintMeta meta = BlueprintMeta.fromJson(JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
            String base = file.getFileName().toString();
            base = base.substring(0, base.length() - META.length());
            if (!meta.name().equals(base)) {
                throw new IllegalArgumentException("el nombre del JSON no coincide con el del archivo");
            }
            return new Entry(meta, directory.resolve(base + SCHEMATIC), directory.resolve(base + THUMBNAIL));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
