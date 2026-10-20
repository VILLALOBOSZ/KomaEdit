package com.komainu.moxia.terrain.stamp;

import com.komainu.moxia.terrain.HeightStamp;
import com.komainu.moxia.terrain.StampImages;
import com.komainu.moxia.terrain.StampLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Vuelve a escribir los PNG de los sellos de serie desde sus recetas, en la carpeta de {@code KOMAEDIT_STAMPS_DIR}
 * (normalmente {@code src/main/resources/assets/komaedit/stamps}). Solo con la variable definida; en el CI no hace
 * nada. Hay que pasarlo al cambiar una receta, o {@code StampLibraryTest} avisa de que el PNG ya no es el de la
 * receta.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_STAMPS_DIR", matches = ".+")
class StampResourcesDump {
    @Test
    void writeEveryStamp() throws IOException {
        Path directory = Path.of(System.getenv("KOMAEDIT_STAMPS_DIR"));
        Files.createDirectories(directory);
        try (Stream<Path> old = Files.list(directory)) {
            for (Path file : old.filter(path -> path.toString().endsWith(".png")).toList()) {
                Files.delete(file);
            }
        }
        for (StampRecipe recipe : StampRecipes.all()) {
            HeightStamp stamp = StampLibrary.render(recipe.name(), StampLibrary.SIZE);
            try (OutputStream output = Files.newOutputStream(directory.resolve(recipe.name() + ".png"))) {
                StampImages.write(stamp, output, StampLibrary.STORED_BITS);
            }
        }
    }
}
