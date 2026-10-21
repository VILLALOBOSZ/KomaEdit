package com.komainu.moxia.reference;

import com.komainu.moxia.image.ImageDecoder;
import com.komainu.moxia.image.RgbaImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Las imágenes de {@code config/komaedit/references/} del equipo del jugador: listarlas, leerlas reducidas para la
 * tarjeta gráfica y copiar dentro las que se arrastran a la ventana. Un nombre nunca sale de la carpeta (ni
 * {@code ..}, ni rutas absolutas, ni enlaces que apunten fuera). Solo PNG y JPG, reconocidos por sus primeros bytes.
 */
public final class ReferenceFiles {
    public static final List<String> EXTENSIONS = List.of("png", "jpg", "jpeg");
    /** Una foto de móvil cabe de sobra; más no tiene sentido para calcar. */
    public static final long MAX_BYTES = 40L * 1024L * 1024L;
    /** Píxeles que puede declarar la cabecera (se lee saltando píxeles si sobra resolución). */
    public static final long MAX_PIXELS = 120_000_000L;
    /** Lado de la textura que se sube a la tarjeta gráfica; las medidas del modelo siguen siendo las originales. */
    public static final int TEXTURE_SIDE = 2_048;
    /** Lado de las miniaturas de la lista. */
    public static final int THUMBNAIL_SIDE = 96;
    /** Carpeta (dentro de la de referencias) con lo abierto en cada mundo; la lista no la enseña. */
    public static final String WORLDS = ".worlds";
    private static final int MAX_LISTED = 200;

    private ReferenceFiles() {
    }

    /** Ruta del archivo dentro de la carpeta, comprobada. */
    public static Path resolve(Path directory, String name) {
        String text = name == null ? "" : name.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Falta el nombre de la imagen");
        }
        if (text.length() > 255 || text.contains("\\") || text.contains(":") || text.startsWith("/")
                || text.chars().anyMatch(character -> character < 0x20)) {
            throw new IllegalArgumentException("Nombre de imagen no válido: " + name);
        }
        for (String part : text.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.startsWith(".")) {
                throw new IllegalArgumentException("Nombre de imagen no válido: " + name);
            }
        }
        if (!EXTENSIONS.contains(extension(text))) {
            throw new IllegalArgumentException("La imagen tiene que ser .png o .jpg: " + name);
        }
        Path base = directory.toAbsolutePath().normalize();
        Path file = base.resolve(text).normalize();
        if (!file.startsWith(base)) {
            throw new IllegalArgumentException("Nombre de imagen no válido: " + name);
        }
        return file;
    }

    /** Las imágenes de la carpeta (con una subcarpeta como mucho), por orden alfabético, hasta 200. */
    public static List<String> list(Path directory) {
        List<String> names = new ArrayList<>();
        if (directory == null || !Files.isDirectory(directory)) {
            return names;
        }
        Path base = directory.toAbsolutePath().normalize();
        try (Stream<Path> files = Files.walk(base, 2)) {
            files.filter(Files::isRegularFile)
                    .map(path -> base.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'))
                    .filter(ReferenceFiles::listed)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(MAX_LISTED)
                    .forEach(names::add);
        } catch (IOException | RuntimeException ignored) {
            // Sin lista: se ve vacía y el jugador puede volver a mirar.
        }
        return names;
    }

    private static boolean listed(String name) {
        if (!EXTENSIONS.contains(extension(name))) {
            return false;
        }
        for (String part : name.split("/")) {
            if (part.startsWith(".")) {
                return false;
            }
        }
        return true;
    }

    /** Lee el archivo con el límite de tamaño y sin salir de la carpeta. */
    public static byte[] read(Path directory, String name) {
        Path file = resolve(directory, name);
        try {
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("No existe la imagen " + name);
            }
            Path real = file.toRealPath();
            if (!real.startsWith(directory.toRealPath())) {
                throw new IllegalArgumentException("La imagen " + name + " apunta fuera de la carpeta");
            }
            if (Files.size(real) > MAX_BYTES) {
                throw tooLarge(name);
            }
            byte[] data = Files.readAllBytes(real);
            if (data.length > MAX_BYTES) {
                throw tooLarge(name);
            }
            return data;
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo leer la imagen " + name + ": " + exception.getMessage());
        }
    }

    private static IllegalArgumentException tooLarge(String name) {
        return new IllegalArgumentException("La imagen " + name + " pasa de " + MAX_BYTES / (1024 * 1024) + " MB");
    }

    /** Una imagen leída: los píxeles (quizá reducidos) y las medidas del archivo original. */
    public record Decoded(RgbaImage image, int originalWidth, int originalHeight) {
    }

    /**
     * Decodifica la imagen saltando píxeles hasta que ningún lado pase de {@code maxSide}. Las medidas originales
     * se devuelven aparte: la rejilla y el tamaño en bloques las usan a ellas.
     */
    public static Decoded decode(byte[] data, int maxSide) {
        ImageDecoder.Header header = ImageDecoder.probe(data, MAX_PIXELS);
        if (header.format() == ImageDecoder.Format.GIF) {
            throw new IllegalArgumentException("La imagen tiene que ser PNG o JPG");
        }
        RgbaImage image = ImageDecoder.decode(data, MAX_PIXELS, subsampling(header.width(), header.height(),
                maxSide));
        return new Decoded(image, header.width(), header.height());
    }

    /** Cada cuántos píxeles se toma uno para que el lado más largo no pase de {@code maxSide}. */
    public static int subsampling(int width, int height, int maxSide) {
        int longest = Math.max(1, Math.max(width, height));
        return Math.max(1, (int) Math.ceil(longest / (double) Math.max(1, maxSide)));
    }

    /**
     * Copia a la carpeta una imagen arrastrada a la ventana. Se queda con su nombre (sin espacios raros) y, si ya hay
     * otra igual de nombre, le añade {@code _2}, {@code _3}… Comprueba que de verdad es PNG o JPG.
     *
     * @return el nombre dentro de la carpeta
     */
    public static String importFile(Path directory, Path source) {
        String original = source.getFileName() == null ? "" : source.getFileName().toString();
        String extension = extension(original);
        if (!EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Solo se pueden abrir imágenes PNG o JPG: " + original);
        }
        try {
            if (!Files.isRegularFile(source)) {
                throw new IllegalArgumentException("No se encuentra " + original);
            }
            if (Files.size(source) > MAX_BYTES) {
                throw tooLarge(original);
            }
            byte[] head = new byte[8];
            int read;
            try (var input = Files.newInputStream(source)) {
                read = input.readNBytes(head, 0, head.length);
            }
            ImageDecoder.Format format = ImageDecoder.sniff(java.util.Arrays.copyOf(head, read));
            if (format != ImageDecoder.Format.PNG && format != ImageDecoder.Format.JPEG) {
                throw new IllegalArgumentException("El archivo no es una imagen PNG ni JPG: " + original);
            }
            Files.createDirectories(directory);
            Path base = directory.toAbsolutePath().normalize();
            String stem = safeStem(original.substring(0, original.length() - extension.length() - 1));
            String name = stem + "." + extension;
            for (int copy = 2; Files.exists(base.resolve(name)); copy++) {
                if (Files.isSameFile(base.resolve(name), source)) {
                    return name;
                }
                name = stem + "_" + copy + "." + extension;
            }
            Files.copy(source, base.resolve(name), StandardCopyOption.COPY_ATTRIBUTES);
            return name;
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo copiar " + original + ": " + exception.getMessage());
        }
    }

    /** El nombre sin lo que daría problemas en la carpeta o en una orden: espacios y símbolos pasan a {@code _}. */
    static String safeStem(String stem) {
        StringBuilder safe = new StringBuilder();
        for (char character : stem.toCharArray()) {
            boolean plain = Character.isLetterOrDigit(character) || character == '-' || character == '_';
            safe.append(plain ? character : '_');
            if (safe.length() >= 64) {
                break;
            }
        }
        String text = safe.toString().replaceAll("_+", "_");
        return text.isEmpty() || text.equals("_") ? "imagen" : text;
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
