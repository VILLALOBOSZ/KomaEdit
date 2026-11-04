package com.komainu.moxia.terrain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.terrain.stamp.StampInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los sellos propios del servidor: los que se capturan del mundo y los PNG que se dejan a mano en
 * {@code config/komaedit/heightmaps/}. Son de todo el servidor (como la carpeta), cada uno con su autor: lo puede
 * borrar o cambiar quien lo capturó o quien tenga {@code komaedit.admin.stamps}.
 *
 * <pre>
 * heightmaps/&lt;nombre&gt;.png    el relieve, PNG en grises de 16 bits con su gris neutro
 * heightmaps/&lt;nombre&gt;.json   autor, descripción, tamaño, alturas y categoría (solo los capturados o importados)
 * </pre>
 *
 * <p>El PNG se escribe primero y el JSON el último, los dos con {@link AtomicStorage}. Se leen con el
 * {@link StampCatalog} de la misma carpeta, así el pincel, la orden sobre la selección, la huella de pintar y la
 * cordillera los usan por su nombre sin más. Java puro: las pruebas le dan una carpeta temporal.</p>
 */
public final class OwnStamps {
    public static final String META = ".json";
    public static final String IMAGE = ".png";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final StampCatalog catalog;
    private final Map<Path, CachedMeta> metas = new ConcurrentHashMap<>();
    private final Map<Path, CachedHash> hashes = new ConcurrentHashMap<>();

    /**
     * Topes de los sellos propios.
     *
     * @param maxStamps    en todo el servidor
     * @param maxPerAuthor de cada jugador
     */
    public record Limits(int maxStamps, int maxPerAuthor) {
        public static final Limits DEFAULT = new Limits(200, 40);
    }

    /**
     * Un sello propio en disco.
     *
     * @param hash huella del PNG (64 cifras hexadecimales): cambia si cambia el sello
     */
    public record Entry(String name, OwnStamp meta, Path image, double neutral, String hash) {
        public StampInfo info() {
            return meta.info(neutral);
        }
    }

    private record CachedMeta(long modified, long size, long imageModified, OwnStamp meta) {
    }

    private record CachedHash(long modified, long size, String hash) {
    }

    public OwnStamps(StampCatalog catalog) {
        this.catalog = catalog;
    }

    public Path directory() {
        return catalog.directory();
    }

    public StampCatalog catalog() {
        return catalog;
    }

    /** Todos, el más nuevo primero; sin las plantillas de la 1.8 que siguen sin tocar. */
    public synchronized List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        Path directory = catalog.directory();
        if (!Files.isDirectory(directory)) {
            return entries;
        }
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + IMAGE)) {
            for (Path file : files) {
                String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                String name = fileName.substring(0, fileName.length() - IMAGE.length());
                if (name.matches("[a-z0-9_\\-]{1,64}") && Files.isRegularFile(file)) {
                    names.add(name);
                }
            }
        } catch (IOException exception) {
            return entries;
        }
        for (String name : names) {
            Entry entry = read(name);
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparingLong((Entry entry) -> entry.meta.created()).reversed()
                .thenComparing(Entry::name));
        OwnStampIndex.replace(entries.stream().map(Entry::info).toList());
        return entries;
    }

    public synchronized Entry find(String input) {
        return read(StampCatalog.normalize(input));
    }

    /** Lo que se sabe del sello propio, o {@code null} si no hay. */
    public StampInfo info(String input) {
        Entry entry = find(input);
        return entry == null ? null : entry.info();
    }

    /** Cuántos son de este autor. */
    public synchronized int countBy(String authorId) {
        int count = 0;
        for (Entry entry : list()) {
            if (entry.meta.ownedBy(authorId)) {
                count++;
            }
        }
        return count;
    }

    private Entry read(String name) {
        Path image = catalog.directory().resolve(name + IMAGE);
        if (!Files.isRegularFile(image)) {
            return null;
        }
        HeightStamp stamp;
        try {
            stamp = catalog.load(name);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
        Path json = catalog.directory().resolve(name + META);
        boolean hasMeta = Files.isRegularFile(json);
        // Una plantilla de la 1.8 sin tocar no es propia: el catálogo da el sello de serie.
        if (!hasMeta && StampLibrary.has(name) && stamp == StampLibrary.get(name)) {
            return null;
        }
        OwnStamp meta = hasMeta ? meta(name, json, image, stamp) : OwnStamp.plain(name, stamp);
        return new Entry(name, meta, image, stamp.neutral(), hash(image));
    }

    private OwnStamp meta(String name, Path json, Path image, HeightStamp stamp) {
        try {
            long modified = Files.getLastModifiedTime(json).toMillis();
            long size = Files.size(json);
            long imageModified = Files.getLastModifiedTime(image).toMillis();
            CachedMeta cached = metas.get(json);
            if (cached == null || cached.modified != modified || cached.size != size
                    || cached.imageModified != imageModified) {
                OwnStamp meta;
                try {
                    JsonObject object = JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    meta = OwnStamp.fromJson(name, object, stamp);
                } catch (RuntimeException exception) {
                    meta = OwnStamp.plain(name, stamp);
                }
                cached = new CachedMeta(modified, size, imageModified, meta);
                metas.put(json, cached);
            }
            return cached.meta;
        } catch (IOException exception) {
            return OwnStamp.plain(name, stamp);
        }
    }

    private String hash(Path image) {
        try {
            long modified = Files.getLastModifiedTime(image).toMillis();
            long size = Files.size(image);
            CachedHash cached = hashes.get(image);
            if (cached == null || cached.modified != modified || cached.size != size) {
                cached = new CachedHash(modified, size, AtomicStorage.sha256(image));
                hashes.put(image, cached);
            }
            return cached.hash;
        } catch (IOException exception) {
            return "";
        }
    }

    /**
     * Guarda un sello capturado o importado.
     *
     * @param replace   si puede sustituir uno propio que ya existe (el suyo, o cualquiera con {@code manage})
     * @param actorId   quien lo guarda
     * @param manage    si puede tocar los de otros
     * @param unlimited sin los topes de número
     */
    public synchronized Entry save(OwnStamp meta, HeightStamp stamp, boolean replace, String actorId, boolean manage,
                                   Limits limits, boolean unlimited) {
        String name = StampCatalog.normalize(meta.name());
        if (StampLibrary.has(name)) {
            throw new IllegalArgumentException("«" + name + "» es un sello de serie; elige otro nombre");
        }
        if (stamp.width() > HeightStamp.MAX_SIZE || stamp.height() > HeightStamp.MAX_SIZE) {
            throw new IllegalArgumentException("El sello es demasiado grande");
        }
        Entry existing = read(name);
        if (existing != null) {
            if (!replace) {
                throw new IllegalArgumentException("Ya hay un sello propio «" + name + "»; borra ese, usa otro"
                        + " nombre o -r para sustituirlo");
            }
            if (!manage && !existing.meta.ownedBy(actorId)) {
                throw new IllegalArgumentException("«" + name + "» es de " + who(existing.meta)
                        + "; solo lo puede sustituir quien lo hizo o un administrador");
            }
        } else if (!unlimited) {
            List<Entry> all = list();
            if (all.size() >= limits.maxStamps()) {
                throw new IllegalArgumentException("El servidor ya tiene " + all.size() + " sellos propios, el tope;"
                        + " borra alguno con /komaedit sello borrar");
            }
            long mine = all.stream().filter(entry -> entry.meta.ownedBy(actorId)).count();
            if (mine >= limits.maxPerAuthor()) {
                throw new IllegalArgumentException("Ya tienes " + mine + " sellos propios, el tope por jugador;"
                        + " borra alguno con /komaedit sello borrar");
            }
        }
        Path directory = catalog.directory();
        try {
            Files.createDirectories(directory);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            StampImages.write(stamp, png, 16);
            Path image = directory.resolve(name + IMAGE);
            writeBytes(image, png.toByteArray());
            AtomicStorage.writeString(directory.resolve(name + META), GSON.toJson(meta.withName(name).toJson()));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar el sello: " + exception.getMessage(), exception);
        }
        Entry saved = read(name);
        list();
        return saved;
    }

    /** Borra un sello propio: su PNG, su JSON y sus copias de seguridad. */
    public synchronized void delete(String input, String actorId, boolean manage) {
        String name = StampCatalog.normalize(input);
        Entry existing = read(name);
        if (existing == null) {
            throw new IllegalArgumentException("No hay ningún sello propio «" + name + "»");
        }
        if (!manage && !existing.meta.ownedBy(actorId)) {
            throw new IllegalArgumentException("«" + name + "» es de " + who(existing.meta)
                    + "; solo lo puede borrar quien lo hizo o un administrador");
        }
        Path directory = catalog.directory();
        try {
            for (String suffix : List.of(IMAGE, META)) {
                Path file = directory.resolve(name + suffix);
                Files.deleteIfExists(file);
                Files.deleteIfExists(AtomicStorage.backup(file));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo borrar el sello: " + exception.getMessage(), exception);
        }
        list();
    }

    /** Cambia la descripción (también a un PNG puesto a mano, que pasa a tener JSON). */
    public synchronized Entry describe(String input, String text, String actorId, boolean manage) {
        String name = StampCatalog.normalize(input);
        Entry existing = read(name);
        if (existing == null) {
            throw new IllegalArgumentException("No hay ningún sello propio «" + name + "»");
        }
        if (!manage && !existing.meta.authorId().isEmpty() && !existing.meta.ownedBy(actorId)) {
            throw new IllegalArgumentException("«" + name + "» es de " + who(existing.meta)
                    + "; solo lo puede cambiar quien lo hizo o un administrador");
        }
        if (!manage && existing.meta.authorId().isEmpty()) {
            throw new IllegalArgumentException("«" + name + "» es un PNG del servidor; solo lo puede describir un"
                    + " administrador");
        }
        try {
            AtomicStorage.writeString(catalog.directory().resolve(name + META),
                    GSON.toJson(existing.meta.withDescription(text).toJson()));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar el sello: " + exception.getMessage(), exception);
        }
        list();
        return read(name);
    }

    /** El PNG tal cual está en disco (para un paquete). */
    public byte[] imageBytes(Entry entry) {
        try {
            return Files.readAllBytes(entry.image);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer el sello: " + exception.getMessage(), exception);
        }
    }

    /** El relieve en pequeño para la miniatura del Studio: {@code side}×{@code side} grises de 16 bits. */
    public Preview preview(Entry entry, int side) {
        HeightStamp stamp;
        try {
            stamp = catalog.load(entry.name);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer el sello: " + exception.getMessage(), exception);
        }
        return Preview.of(entry.hash, stamp, side);
    }

    /**
     * El relieve en pequeño, sin Minecraft, para mandarlo y pintar la miniatura en el cliente.
     *
     * @param values grises de 0 a 65535, por filas
     */
    public record Preview(String hash, int side, double neutral, short[] values) {
        public static final int SIDE = 64;

        public static Preview of(String hash, HeightStamp stamp, int side) {
            short[] values = new short[side * side];
            for (int row = 0; row < side; row++) {
                for (int column = 0; column < side; column++) {
                    double u = (column + 0.5) / side;
                    double v = (row + 0.5) / side;
                    values[row * side + column] = (short) Math.round(Math.clamp(stamp.sample(u, v), 0.0, 1.0)
                            * 65_535.0);
                }
            }
            return new Preview(hash, side, stamp.neutral(), values);
        }

        public HeightStamp stamp(String name) {
            float[] floats = new float[values.length];
            for (int index = 0; index < values.length; index++) {
                floats[index] = (values[index] & 0xFFFF) / 65_535f;
            }
            return new HeightStamp(name, side, side, floats, neutral);
        }
    }

    private static String who(OwnStamp meta) {
        return meta.author().isEmpty() ? "otro jugador" : meta.author();
    }

    private static void writeBytes(Path target, byte[] data) throws IOException {
        AtomicStorage.requireWritableName(target);
        Path temporary = AtomicStorage.temporary(target);
        Files.write(temporary, data);
        AtomicStorage.replace(temporary, target);
    }

    /** Huella corta de unos bytes, para las pruebas y los paquetes. */
    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
