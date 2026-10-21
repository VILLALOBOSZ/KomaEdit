package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maquetas de la tarjeta «Imagen de referencia» en Utilidades con el mismo código que el juego, como
 * {@link PaintCardMockupTest}. Solo con {@code KOMAEDIT_STUDIO_MOCKUPS=<carpeta>}.
 */
class ReferenceCardMockupTest {
    private static final ToolContext CONTEXT = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L, 0,
            false);

    @Test
    void renderReferenceCard() throws IOException {
        String target = System.getenv("KOMAEDIT_STUDIO_MOCKUPS");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "KOMAEDIT_STUDIO_MOCKUPS sin definir");
        assertTrue(ImageCanvas.font().loaded(), "la fuente del juego tiene que estar en el classpath");
        Path folder = Path.of(target);
        Files.createDirectories(folder);
        scene(folder, "studio_1080p_gui3_tarjeta_es.png", 640, 360, 3, "es_es");
        scene(folder, "studio_1080p_gui2_tarjeta_en.png", 960, 540, 2, "en_us");
    }

    private static void scene(Path folder, String file, int width, int height, int scale, String language)
            throws IOException {
        StudioDriver driver = new StudioDriver(language, width, height);
        driver.host.context = CONTEXT;
        driver.host.info = new ToolsInfo(true, null, List.of(), Map.of(), Map.of(), ToolsInfo.Symmetry.OFF, null,
                List.of(), null);
        driver.view.selectStage(StudioStage.TOOLS, driver.host);
        driver.view.tools().select(ToolCatalog.byId("reference"), driver.host);
        driver.host.status = "";
        BufferedImage image = driver.paint(scale, ui -> StudioMockupsTest.world(ui, width, height));
        ImageIO.write(image, "png", folder.resolve(file).toFile());
    }
}
