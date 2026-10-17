package com.komainu.moxia.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Lee una lista de archivos del proyecto sin que uno dañado o de una versión antigua tumbe el resto: el archivo
 * ilegible se omite, se registra en {@code latest.log} y se queda en disco para poder recuperarlo.
 */
public final class TolerantListing {
    private static final Logger LOGGER = LoggerFactory.getLogger("komaedit");

    private TolerantListing() {
    }

    public static <T> List<T> readAll(List<Path> files, Function<Path, T> reader, String kind) {
        List<T> result = new ArrayList<>(files.size());
        for (Path file : files) {
            try {
                result.add(reader.apply(file));
            } catch (RuntimeException exception) {
                LOGGER.warn("KomaEdit ignora {} ilegible {}: {}", kind, file, exception.getMessage());
            }
        }
        return List.copyOf(result);
    }
}
