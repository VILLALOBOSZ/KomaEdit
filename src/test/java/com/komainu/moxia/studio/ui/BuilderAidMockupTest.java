package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolValues;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maquetas de las tarjetas Vistas, Regla y Nota con el mismo código que el juego, como {@link StudioMockupsTest}: la
 * cuadrícula de utilidades y cada tarjeta con algo escrito, a 1080p con GUI 3 en español y una en inglés. Solo con
 * {@code KOMAEDIT_STUDIO_MOCKUPS=<carpeta>}.
 */
class BuilderAidMockupTest {
    @Test
    void renderBuilderAidCards() throws IOException {
        String target = System.getenv("KOMAEDIT_STUDIO_MOCKUPS");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "KOMAEDIT_STUDIO_MOCKUPS sin definir");
        assertTrue(ImageCanvas.font().loaded(), "la fuente del juego tiene que estar en el classpath");
        ChoiceThumbnails.setBlocking(true);
        Path folder = Path.of(target);
        Files.createDirectories(folder);
        scene(folder, "1080p_gui3_utilidades_es.png", "es_es", driver -> {
            StudioMockupsTest.tools(driver, null);
            StudioMockupsTest.scrollTo(driver, "Vistas");
        });
        scene(folder, "1080p_gui3_vistas_es.png", "es_es", driver -> card(driver, "views",
                values -> values.with("view_name", "Mirador del puerto")));
        scene(folder, "1080p_gui3_regla_es.png", "es_es", driver -> card(driver, "ruler",
                values -> values.with("ruler_mode", "caja")));
        scene(folder, "1080p_gui3_nota_es.png", "es_es", driver -> card(driver, "note",
                values -> values.with("note", "Falta el tejado del ala norte").with("note_color", "rojo")
                        .with("note_icon", "aviso").with("team", true)));
        scene(folder, "1080p_gui3_nota_en.png", "en_us", driver -> card(driver, "note",
                values -> values.with("note", "North wing roof missing").with("note_icon", "tarea")));
    }

    private static void scene(Path folder, String file, String language, Consumer<StudioDriver> setup)
            throws IOException {
        int width = 640;
        int height = 360;
        StudioDriver driver = new StudioDriver(language, width, height);
        driver.host.context = StudioMockupsTest.SELECTED;
        driver.host.info = StudioMockupsTest.info();
        setup.accept(driver);
        BufferedImage image = driver.paint(3, ui -> StudioMockupsTest.world(ui, width, height));
        ImageIO.write(image, "png", folder.resolve(file).toFile());
    }

    private static void card(StudioDriver driver, String id, UnaryOperator<ToolValues> values) {
        StudioMockupsTest.tools(driver, id);
        ToolCard card = ToolCatalog.byId(id);
        driver.view.tools().setValues(card, values.apply(driver.view.tools().values(card)));
        driver.host.status = "";
    }
}
