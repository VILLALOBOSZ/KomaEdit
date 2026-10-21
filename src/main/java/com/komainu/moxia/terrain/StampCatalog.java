package com.komainu.moxia.terrain;

import com.komainu.moxia.image.ImageFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los sellos de una carpeta ({@code config/komaedit/heightmaps/}): las imágenes que haya y los de serie. Una con el
 * nombre de uno de serie lo sustituye, así se pueden retocar ({@link #export} deja allí una copia para empezar). Los
 * archivos leídos se guardan en memoria mientras no cambien.
 *
 * <p>Hasta la 1.8 se escribían aquí los 20 sellos de serie de entonces como plantilla. Si uno sigue sin tocar (su
 * huella coincide con la de entonces) no tapa al nuevo del mismo nombre: se usa el de serie.</p>
 */
public final class StampCatalog {
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    static final String README = "LEEME.txt";
    /**
     * Huella de los sellos que escribía la 1.8: la media de cada octavo de la imagen (8×8) en un byte, en
     * hexadecimal.
     */
    private static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("montana", "000001070a03000000041527351e05000114365f603b1501042263ad9c642504"
                    + "0423639f9f572202011443736239140100061a28251304000000010506020000"),
            Map.entry("pico", "00000001010000000001070f10060100000621413f1f070001113f8c8c410e01"
                    + "00104191904310010006204240200600000107100e0701000000000101000000"),
            Map.entry("colina", "00000206060200000005182a2a1805000218487c7c481802062a7cd5d57c2a06"
                    + "062a7cd5d57c2a060218487c7c4818020005182a2a1805000000020606020000"),
            Map.entry("loma", "0000030a0a03000000041a3b3b1a0400010d439797430d01011360d9d9601301"
                    + "01125acaca5a1201010d429494420d0100062757572706000000081c1c080000"),
            Map.entry("cordillera", "000005232a07000000052aaea923040000082bcbbc280700010729bfcd2d0801"
                    + "01082ac2c12b0801010936d2ad220600000529b5a72605000000062625050000"),
            Map.entry("macizo", "000000010501000000080f15471b010002365d617c2a0200011c5ed0a84c1901"
                    + "011c62947da43a02022e7c3f213f170100061208020401000000000000000000"),
            Map.entry("volcan", "0000040e0e040000000d354f4e340e000434709c9d6f34040e4f9ccbcb9c4f0e"
                    + "0e4f9ccbcb9c4f0e04346f9c9c6f3404000d344e4e350d000000040e0e040000"),
            Map.entry("crater", "5959595a5a595959595a74afab745a595977d07b77cf78595ab47d1b1a78b05a"
                    + "5aad711a1a71b25a5976c97472cb7859595a72acb3745a595959595a5a595959"),
            Map.entry("dolina", "fffffffffffffffffffffffcfbffffffffffdf938ae1fffffffc8f121296fcff"
                    + "fffa840d0f89fbffffffdd8e87dffffffffffffbf9ffffffffffffffffffffff"),
            Map.entry("valle", "ffffecc8c8edffffffff981e1fa2ffffffff950d0d95ffffffff920d0d99ffff"
                    + "ffff990d0d92ffffffff950d0d96ffffffff951e1fa6ffffffffecc8c8edffff"),
            Map.entry("duna", "000000000000000001080000000008010b4b260404264b0b0846a1b4b4a14608"
                    + "011f78cbcb781f0100042c66662c040000000310100300000000000000000000"),
            Map.entry("dunas", "00022050502a0300012a534d504e2d021f69665b5d6769193f7f929184877844"
                    + "3b6368566c526e3f1341525c5c5c4c110347656267683c0300022a5046310200"),
            Map.entry("mesa", "000000030601000000051946772c0400001cb2f4f8c41c000248f7f8f8f75a04"
                    + "0579f7f8f7f85c03012abaf5f8bd1c000004173f6e2503000000000203000000"),
            Map.entry("terrazas", "000000000000000000011a36341701000018568280541a00002b85cac7823000"
                    + "003186cecd84340000194f838650160000011b2f341b01000000000000000000"),
            Map.entry("acantilado", "0001111b1b11010002609c9c9d9b600219cfe9e9e7e9d01a28d6eaeceae8d426"
                    + "0a37404029262704000000000000000000000000000000000000000000000000"),
            Map.entry("barrancos", "0000071010070000000f2a3c3b2b0f00072b517276512b07103b6e9599773c10"
                    + "0f3c74949e6f3d10072a506f70532b07000f2a3c3b2a0f000000070f10070000"),
            Map.entry("aguja", "0000000000000000000002050502000000020b1a1a0b020000051a6c74190500"
                    + "00051a6d701a050000020b191a0b020000000205050200000000000000000000"),
            Map.entry("domo", "0000051d1d050000001a79a2a3791a000579c1dcdbc179051da2dcf3f2dca21d"
                    + "1da2dbf3f3dba31d0579c1dbdbc17905001a79a2a3791a000000051d1d050000"),
            Map.entry("atolon", "000000020200000000034c9d9d4c0300004c951b1b944b00029d1b00001b9c02"
                    + "029d1b00001b9d020044861b1b954c0000024c969d4c03000000000202000000"),
            Map.entry("circo", "00000f2c2e0f0000002ca8dad8aa2c000c95b05253ac930c1776320303327618"
                    + "08240d01010d2408000101000001010000000000000000000000000000000000")
    );

    private final Path directory;
    private final Map<Path, Cached> cache = new ConcurrentHashMap<>();

    /** @param legacy si es una plantilla de la 1.8 sin tocar: entonces vale el de serie */
    private record Cached(long modified, long size, HeightStamp stamp, boolean legacy) {
    }

    public StampCatalog(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    /**
     * Crea la carpeta con un {@value #README} que explica cómo poner sellos propios. Los de serie van dentro del mod y
     * ya no se copian aquí. Devuelve cuántos archivos ha escrito (0 si ya estaba).
     */
    public int writeDefaults() throws IOException {
        Files.createDirectories(directory);
        Path readme = directory.resolve(README);
        if (Files.exists(readme)) {
            return 0;
        }
        Files.writeString(readme, """
                Sellos de relieve propios de KomaEdit
                =====================================

                Los sellos de serie (80, en 14 categorías) van dentro del mod: no hace falta ningún archivo aquí.

                Para usar uno propio, deja aquí un PNG en escala de grises (8 o 16 bits; el blanco es lo más alto y la
                transparencia no toca el terreno) y úsalo con /komaedit pincel heightmap <nombre>. Un PNG con el nombre
                de uno de serie lo sustituye.

                Para retocar uno de serie: /komaedit terreno sellos exportar <sello> deja aquí su PNG de 16 bits.
                Los que hunden (cañones, dolinas…) guardan su gris neutro en el PNG; la orden puede cambiarlo con -c.
                """);
        return 1;
    }

    /** Copia un sello de serie a la carpeta como PNG de 16 bits para retocarlo. No pisa lo que haya. */
    public Path export(String input) throws IOException {
        String name = normalize(input);
        if (!StampLibrary.has(name)) {
            throw new IllegalArgumentException("No hay ningún sello de serie llamado " + name);
        }
        Files.createDirectories(directory);
        Path file = directory.resolve(name + ".png");
        if (Files.exists(file)) {
            throw new IllegalArgumentException("Ya hay un " + file.getFileName() + " en " + directory
                    + "; bórralo o cámbiale el nombre antes");
        }
        Path temporary = directory.resolve(name + ".png.tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            StampImages.write(StampLibrary.get(name), output);
        }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return file;
    }

    /** Nombres disponibles: los de serie y las imágenes de la carpeta, sin repetir y en orden alfabético. */
    public List<String> names() {
        TreeSet<String> names = new TreeSet<>(StampLibrary.descriptions().keySet());
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                for (Path file : files) {
                    String base = baseName(file);
                    if (base != null && base.matches("[a-z0-9_\\-]{1,64}")) {
                        names.add(base);
                    }
                }
            } catch (IOException ignored) {
                // Sin carpeta legible quedan los de serie.
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * Carga un sello por nombre (sin extensión): primero la imagen de la carpeta y si no, el de serie. Valen los
     * mismos formatos que {@code /komaedit image} (PNG, JPG y GIF); el PNG de 16 bits conserva toda su precisión.
     * El nombre solo admite letras, números, guiones y subrayados, y un enlace que apunte fuera de la carpeta no se
     * sigue.
     */
    public HeightStamp load(String input) throws IOException {
        String name = normalize(input);
        Path file = findFile(name);
        if (file == null) {
            if (StampLibrary.has(name)) {
                return StampLibrary.get(name);
            }
            throw new IllegalArgumentException("No hay ningún sello «" + name + "»; mira /komaedit terreno sellos o"
                    + " deja un PNG en " + directory);
        }
        Path real = file.toRealPath();
        if (!real.startsWith(directory.toRealPath())) {
            throw new IllegalArgumentException("El sello " + name + " apunta fuera de " + directory);
        }
        long modified = Files.getLastModifiedTime(real).toMillis();
        long size = Files.size(real);
        if (size > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("La imagen " + file.getFileName() + " pasa de 8 MB");
        }
        Cached cached = cache.get(real);
        if (cached == null || cached.modified != modified || cached.size != size) {
            HeightStamp stamp;
            try (InputStream stream = Files.newInputStream(real)) {
                stamp = StampImages.read(name, stream);
            }
            cached = new Cached(modified, size, stamp, isLegacy(name, stamp));
            cache.put(real, cached);
        }
        return cached.legacy && StampLibrary.has(name) ? StampLibrary.get(name) : cached.stamp;
    }

    /** Si el sello es la plantilla que escribía la 1.8 con ese nombre, sin retocar. */
    static boolean isLegacy(String name, HeightStamp stamp) {
        String expected = LEGACY.get(name);
        if (expected == null) {
            return false;
        }
        String actual = signature(stamp);
        for (int index = 0; index < 64; index++) {
            int wanted = Integer.parseInt(expected, index * 2, index * 2 + 2, 16);
            int found = Integer.parseInt(actual, index * 2, index * 2 + 2, 16);
            if (Math.abs(wanted - found) > 2) {
                return false;
            }
        }
        return true;
    }

    /** La media de cada octavo de la imagen (8×8), un byte cada una en hexadecimal. */
    static String signature(HeightStamp stamp) {
        StringBuilder hex = new StringBuilder(128);
        for (int blockY = 0; blockY < 8; blockY++) {
            for (int blockX = 0; blockX < 8; blockX++) {
                int fromX = blockX * stamp.width() / 8;
                int toX = (blockX + 1) * stamp.width() / 8;
                int fromY = blockY * stamp.height() / 8;
                int toY = (blockY + 1) * stamp.height() / 8;
                double sum = 0.0;
                int count = 0;
                for (int y = fromY; y < Math.max(fromY + 1, toY); y++) {
                    for (int x = fromX; x < Math.max(fromX + 1, toX); x++) {
                        sum += stamp.pixel(x, y);
                        count++;
                    }
                }
                hex.append(String.format(Locale.ROOT, "%02x", (int) Math.round(sum / count * 255.0)));
            }
        }
        return hex.toString();
    }

    /** Nombre sin extensión en minúsculas si es una imagen admitida; si no, {@code null}. */
    private static String baseName(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || !ImageFiles.EXTENSIONS.contains(fileName.substring(dot + 1))) {
            return null;
        }
        return fileName.substring(0, dot);
    }

    static String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta el sello, por ejemplo montana, crater o duna");
        }
        String name = input.strip().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot > 0 && ImageFiles.EXTENSIONS.contains(name.substring(dot + 1))) {
            name = name.substring(0, dot);
        }
        name = name.replace("ñ", "n").replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o")
                .replace("ú", "u");
        if (!name.matches("[a-z0-9_\\-]{1,64}")) {
            throw new IllegalArgumentException("Nombre de sello no válido: " + input
                    + " (solo letras, números, - y _)");
        }
        return name;
    }

    private Path findFile(String name) {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        for (String extension : ImageFiles.EXTENSIONS) {
            Path exact = directory.resolve(name + "." + extension);
            if (Files.isRegularFile(exact)) {
                return exact;
            }
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (name.equals(baseName(file)) && Files.isRegularFile(file)) {
                    return file;
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }
}
