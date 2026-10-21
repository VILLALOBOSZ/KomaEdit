package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.tools.WaterCard;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maquetas de «Poner agua o lava» y del modo «Quitar flujos sueltos» de Retocar, como las de {@link StudioMockupsTest} pero
 * aparte (en {@code <carpeta>/agua/}), para no rehacer todas. Solo con {@code KOMAEDIT_STUDIO_MOCKUPS=<carpeta>}.
 */
class FluidMockupsTest {
    @Test
    void renderWaterCards() throws IOException {
        String target = System.getenv("KOMAEDIT_STUDIO_MOCKUPS");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "KOMAEDIT_STUDIO_MOCKUPS sin definir");
        assertTrue(ImageCanvas.font().loaded(), "la fuente del juego tiene que estar en el classpath");
        Path folder = Path.of(target, "agua");
        Files.createDirectories(folder);
        write(folder, "1080p_gui3_rellenar_es.png", "es_es", driver -> mode(driver, WaterCard.FILL));
        write(folder, "1080p_gui3_rellenar_mas_ajustes_es.png", "es_es", driver -> {
            mode(driver, WaterCard.FILL);
            driver.host.preferences.setExpanded("tools:more:water", true);
            for (int step = 0; step < 12 && !driver.has("Límite del hueco"); step++) {
                driver.view.mouseScrolled(driver.view.frame().innerLeft() + 4, 200, -1, driver.host);
            }
        });
        write(folder, "1080p_gui3_bola_es.png", "es_es", driver -> mode(driver, WaterCard.BALL));
        write(folder, "1080p_gui3_cascada_es.png", "es_es", driver -> {
            StudioMockupsTest.path(driver, 3);
            mode(driver, WaterCard.FALL);
        });
        write(folder, "1080p_gui3_rellenar_en.png", "en_us", driver -> mode(driver, WaterCard.FILL));
        write(folder, "1080p_gui3_retocar_sueltos_es.png", "es_es", driver -> {
            StudioMockupsTest.tools(driver, "touchup");
            driver.click("Quitar flujos sueltos");
        });
    }

    /** La tarjeta de agua con el modo elegido, pulsando su botón como lo haría el jugador (las maquetas en español). */
    private static void mode(StudioDriver driver, String mode) {
        StudioMockupsTest.tools(driver, "water");
        switch (mode) {
            case WaterCard.BALL -> driver.click("Bola");
            case WaterCard.FALL -> driver.click("Cascada");
            default -> {
            }
        }
    }

    private static void write(Path folder, String file, String language, Consumer<StudioDriver> setup)
            throws IOException {
        StudioDriver driver = new StudioDriver(language, 640, 360);
        driver.host.context = StudioMockupsTest.SELECTED;
        driver.host.info = StudioMockupsTest.info();
        setup.accept(driver);
        ImageIO.write(driver.paint(3, ui -> StudioMockupsTest.world(ui, 640, 360)), "png",
                folder.resolve(file).toFile());
    }
}
