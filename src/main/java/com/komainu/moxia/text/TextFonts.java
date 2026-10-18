package com.komainu.moxia.text;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fuentes de {@code /komaedit text}: las de serie, con licencia SIL Open Font License 1.1 (sus licencias van junto a
 * los archivos, en {@code komaedit/fonts/} dentro del jar), y las TrueType u OpenType que el administrador deje en
 * {@code config/komaedit/fonts/}. Una fuente propia se pide por el nombre de su archivo sin extensión.
 */
public final class TextFonts {
    public static final String DEFAULT = "sans";
    private static final String RESOURCES = "/komaedit/fonts/";
    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9][a-z0-9_.\\-]{0,63}");
    private static final List<Builtin> BUILTINS = List.of(
            new Builtin("sans", "LatoWeb-Bold.ttf", "Lato Bold", List.of("lato", "lato-bold", "negrita", "bold")),
            new Builtin("fina", "LatoWeb-Regular.ttf", "Lato Regular",
                    List.of("lato-regular", "regular", "thin", "normal")),
            new Builtin("mono", "JetBrainsMono-Bold.ttf", "JetBrains Mono Bold",
                    List.of("jetbrains", "jetbrainsmono", "monospace", "monoespaciada", "code"))
    );
    private static final Map<String, Font> BUILTIN_CACHE = new ConcurrentHashMap<>();
    private static final Map<Path, Cached> CUSTOM_CACHE = new ConcurrentHashMap<>();

    private TextFonts() {
    }

    /** Fuente de serie: id con el que se pide, archivo dentro del jar y nombre de la familia. */
    public record Builtin(String id, String file, String displayName, List<String> aliases) {
    }

    private record Cached(long modified, long size, Font font) {
    }

    public static List<Builtin> builtins() {
        return BUILTINS;
    }

    /**
     * Busca una fuente por nombre: primero las de serie (y sus alias), después los archivos {@code .ttf}/{@code
     * .otf} de {@code customDirectory}.
     */
    public static Font load(String name, Path customDirectory) {
        String normalized = (name == null || name.isBlank() ? DEFAULT : name.strip()).toLowerCase(Locale.ROOT);
        for (Builtin builtin : BUILTINS) {
            if (builtin.id().equals(normalized) || builtin.aliases().contains(normalized)) {
                return BUILTIN_CACHE.computeIfAbsent(builtin.id(), id -> readBuiltin(builtin));
            }
        }
        if (!SAFE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Nombre de fuente inválido: " + name);
        }
        Path file = customFile(normalized, customDirectory);
        if (file == null) {
            throw new IllegalArgumentException("No hay ninguna fuente «" + name + "». Disponibles: "
                    + String.join(", ", available(customDirectory)));
        }
        return readCustom(file);
    }

    /** Ids de las fuentes de serie y nombres de las propias, en orden. */
    public static List<String> available(Path customDirectory) {
        List<String> names = new ArrayList<>();
        for (Builtin builtin : BUILTINS) {
            names.add(builtin.id());
        }
        names.addAll(custom(customDirectory));
        return names;
    }

    /** Nombres de las fuentes propias (archivo sin extensión, en minúsculas). */
    public static List<String> custom(Path customDirectory) {
        if (customDirectory == null || !Files.isDirectory(customDirectory)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(customDirectory)) {
            files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(TextFonts::isFontFile)
                    .map(file -> file.substring(0, file.lastIndexOf('.')).toLowerCase(Locale.ROOT))
                    .filter(file -> SAFE_NAME.matcher(file).matches())
                    .sorted()
                    .forEach(names::add);
        } catch (IOException exception) {
            return List.of();
        }
        return names;
    }

    private static Path customFile(String name, Path customDirectory) {
        if (customDirectory == null || !Files.isDirectory(customDirectory)) {
            return null;
        }
        try (Stream<Path> files = Files.list(customDirectory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String file = path.getFileName().toString();
                        return isFontFile(file)
                                && file.substring(0, file.lastIndexOf('.')).toLowerCase(Locale.ROOT).equals(name);
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private static boolean isFontFile(String file) {
        String lower = file.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }

    private static Font readBuiltin(Builtin builtin) {
        try (InputStream stream = TextFonts.class.getResourceAsStream(RESOURCES + builtin.file())) {
            if (stream == null) {
                throw new IllegalStateException("Falta la fuente de serie " + builtin.file() + " en el jar");
            }
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (IOException | FontFormatException exception) {
            throw new IllegalStateException("No se pudo leer la fuente " + builtin.file(), exception);
        }
    }

    /** Las fuentes propias se vuelven a leer si cambia el archivo. */
    private static Font readCustom(Path file) {
        try {
            long modified = Files.getLastModifiedTime(file).toMillis();
            long size = Files.size(file);
            if (size > 32L * 1024 * 1024) {
                throw new IllegalArgumentException("La fuente " + file.getFileName() + " pasa de 32 MB");
            }
            Cached cached = CUSTOM_CACHE.get(file);
            if (cached != null && cached.modified() == modified && cached.size() == size) {
                return cached.font();
            }
            Font font;
            try (InputStream stream = Files.newInputStream(file)) {
                font = Font.createFont(Font.TRUETYPE_FONT, stream);
            }
            CUSTOM_CACHE.put(file, new Cached(modified, size, font));
            return font;
        } catch (IOException | FontFormatException exception) {
            throw new IllegalArgumentException("No se pudo leer la fuente " + file.getFileName() + ": "
                    + exception.getMessage());
        }
    }
}
