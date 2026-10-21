package com.komainu.moxia.hotbar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.persistence.ProjectStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Las barras guardadas de un jugador en su proyecto: {@code <mundo>/komaedit/players/<uuid>/hotbars/hotbars.json},
 * escrito con {@link AtomicStorage}. Un archivo que no se puede leer no se pisa: se aparta como
 * {@code hotbars.broken-N.json} y se empieza de cero, para poder recuperarlo a mano.
 */
public final class HotbarStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path file;

    public HotbarStore(Path file) {
        this.file = file;
    }

    public static HotbarStore of(UUID playerId) {
        return new HotbarStore(ProjectStorage.namespace(playerId, "hotbars").resolve("hotbars.json"));
    }

    public Path file() {
        return file;
    }

    /**
     * Lo guardado.
     *
     * @param book    las barras (vacías si no había archivo o no se pudo leer)
     * @param problem qué pasó si el archivo no se pudo leer y dónde quedó apartado; {@code null} si todo fue bien
     */
    public record Loaded(HotbarBook book, String problem) {
    }

    public Loaded load() {
        if (!Files.isRegularFile(file)) {
            return new Loaded(HotbarBook.EMPTY, null);
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return new Loaded(HotbarJson.book(JsonParser.parseString(json).getAsJsonObject()), null);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudieron leer las barras guardadas: " + exception.getMessage(),
                    exception);
        } catch (RuntimeException exception) {
            Path aside = aside();
            try {
                Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailure) {
                throw new IllegalStateException("Las barras guardadas están dañadas y no se pudieron apartar: "
                        + moveFailure.getMessage(), moveFailure);
            }
            return new Loaded(HotbarBook.EMPTY, "Las barras guardadas estaban dañadas (" + exception.getMessage()
                    + "); el archivo quedó como " + aside.getFileName());
        }
    }

    public void save(HotbarBook book) {
        try {
            AtomicStorage.writeString(file, GSON.toJson(HotbarJson.book(book)));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudieron guardar las barras: " + exception.getMessage(), exception);
        }
    }

    private Path aside() {
        for (int number = 1; ; number++) {
            Path candidate = file.resolveSibling("hotbars.broken-" + number + ".json");
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
    }
}
