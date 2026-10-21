package com.komainu.moxia.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Que la configuración se cargue sin el juego: la de serie, una de la 1.4.0 y una rota. */
class KomaEditConfigTest {
    /** {@code config/komaedit/config.json} tal como lo escribía la 1.4.0: sin la vista previa ni el array. */
    private static final String CONFIG_1_4_0 = """
            {
              "format": 1,
              "maxBlocksPerOperation": 500000,
              "maxConcurrentJobs": 2,
              "rootPermissionGrantsAll": true,
              "backupRetention": 7,
              "confirmBuildAboveBlocks": 100000
            }
            """;

    @TempDir
    Path directory;

    @Test
    void defaultsSurviveARoundTrip() {
        assertEquals(KomaEditConfig.Settings.DEFAULT,
                KomaEditConfig.parse(KomaEditConfig.toJson(KomaEditConfig.Settings.DEFAULT)));
        assertEquals(KomaEditConfig.Settings.DEFAULT, KomaEditConfig.parse("{}"), "un archivo vacío es el de serie");
    }

    @Test
    void aMissingFileIsCreatedWithEveryField() throws IOException {
        Path file = directory.resolve("komaedit").resolve("config.json");
        assertEquals(KomaEditConfig.Settings.DEFAULT, KomaEditConfig.load(file));
        String written = Files.readString(file, StandardCharsets.UTF_8);
        for (String key : new String[]{"format", "maxBlocksPerOperation", "maxConcurrentJobs",
                "rootPermissionGrantsAll", "backupRetention", "confirmBuildAboveBlocks", "previewTimeoutSeconds",
                "previewMaxDisplays", "arraySnapRadius", "tickBudgetMillis", "tickBudgetMinMillis",
                "planCompressAboveBlocks", "planMemoryMegabytes"}) {
            assertTrue(written.contains("\"" + key + "\""), "falta " + key + " en el archivo de serie");
        }
        assertEquals(KomaEditConfig.Settings.DEFAULT, KomaEditConfig.load(file), "y se vuelve a leer igual");
    }

    @Test
    void aConfigFrom140KeepsItsValuesAndTakesTheNewDefaults() throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, CONFIG_1_4_0, StandardCharsets.UTF_8);
        KomaEditConfig.Settings settings = KomaEditConfig.load(file);
        KomaEditConfig.Settings defaults = KomaEditConfig.Settings.DEFAULT;
        assertEquals(500_000, settings.maxBlocksPerOperation());
        assertEquals(2, settings.maxConcurrentJobs());
        assertTrue(settings.rootPermissionGrantsAll());
        assertEquals(7, settings.backupRetention());
        assertEquals(100_000, settings.confirmBuildAboveBlocks());
        assertEquals(defaults.previewTimeoutSeconds(), settings.previewTimeoutSeconds());
        assertEquals(defaults.previewMaxDisplays(), settings.previewMaxDisplays());
        assertEquals(defaults.arraySnapRadius(), settings.arraySnapRadius());
        assertEquals(defaults.tickBudgetMillis(), settings.tickBudgetMillis());
        assertEquals(defaults.tickBudgetMinMillis(), settings.tickBudgetMinMillis());
        assertEquals(defaults.planCompressAboveBlocks(), settings.planCompressAboveBlocks());
        assertEquals(defaults.planMemoryMegabytes(), settings.planMemoryMegabytes());
        assertEquals(CONFIG_1_4_0, Files.readString(file, StandardCharsets.UTF_8), "leerla no la reescribe");
    }

    @Test
    void theTickBudgetAndPlanStorageCanBeTuned() {
        KomaEditConfig.Settings settings = KomaEditConfig.parse("""
                { "tickBudgetMillis": 2.5, "tickBudgetMinMillis": 0.5, "planCompressAboveBlocks": 0,
                  "planMemoryMegabytes": 0 }
                """);
        assertEquals(2_500_000L, settings.tickBudget().budgetNanos());
        assertEquals(500_000L, settings.tickBudget().minimumNanos());
        assertEquals(0, settings.planStorage().compressAbove(), "0: comprimir siempre");
        assertEquals(-1L, settings.planStorage().spillAbove(), "0 MB: nunca a disco");
        assertEquals(128L * 1024 * 1024, KomaEditConfig.Settings.DEFAULT.planStorage().spillAbove());
        assertEquals(KomaEditConfig.Settings.DEFAULT, KomaEditConfig.parse(KomaEditConfig.toJson(
                KomaEditConfig.Settings.DEFAULT)));
        assertThrows(IllegalArgumentException.class, () -> KomaEditConfig.parse("{ \"tickBudgetMillis\": 0 }"));
        assertThrows(IllegalArgumentException.class,
                () -> KomaEditConfig.parse("{ \"tickBudgetMillis\": 2, \"tickBudgetMinMillis\": 3 }"),
                "el mínimo no puede pasar del presupuesto");
    }

    @Test
    void aBrokenOrFutureConfigFallsBackToTheDefaults() throws IOException {
        Path broken = directory.resolve("broken.json");
        Files.writeString(broken, "{ \"maxBlocksPerOperation\": ", StandardCharsets.UTF_8);
        assertEquals(KomaEditConfig.Settings.DEFAULT, KomaEditConfig.load(broken));

        Path future = directory.resolve("future.json");
        Files.writeString(future, "{ \"format\": 2 }", StandardCharsets.UTF_8);
        assertEquals(KomaEditConfig.Settings.DEFAULT, KomaEditConfig.load(future));
        assertThrows(IllegalArgumentException.class, () -> KomaEditConfig.parse("{ \"format\": 2 }"));

        assertThrows(IllegalArgumentException.class,
                () -> KomaEditConfig.parse("{ \"previewTimeoutSeconds\": 5 }"), "fuera de rango");
    }
}
