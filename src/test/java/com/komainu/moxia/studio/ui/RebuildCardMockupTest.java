package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.tools.ToolCard;
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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maquetas de la tarjeta «Reconstrucción animada» con el mismo código que el juego, como {@link StudioMockupsTest}:
 * de serie, con «Más ajustes» abierto (los botones de la que está en marcha), con un plano y en inglés. Solo con
 * {@code KOMAEDIT_STUDIO_MOCKUPS=<carpeta>}.
 */
class RebuildCardMockupTest {
    private static final ToolContext SELECTED = new ToolContext(true, ToolCatalog.permissionNodes(), true,
            10L * 6 * 12, 0, false);

    @Test
    void renderRebuildCard() throws IOException {
        String target = System.getenv("KOMAEDIT_STUDIO_MOCKUPS");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "KOMAEDIT_STUDIO_MOCKUPS sin definir");
        assertTrue(ImageCanvas.font().loaded(), "la fuente del juego tiene que estar en el classpath");
        ChoiceThumbnails.setBlocking(true);
        Path folder = Path.of(target);
        Files.createDirectories(folder);
        scene(folder, "1080p_gui3_reconstruir_es.png", 640, 360, 3, "es_es", driver -> rebuild(driver, false, false));
        scene(folder, "1080p_gui2_reconstruir_mas_ajustes_es.png", 960, 540, 2, "es_es",
                driver -> rebuild(driver, true, false));
        scene(folder, "1080p_gui2_reconstruir_plano_es.png", 960, 540, 2, "es_es",
                driver -> rebuild(driver, false, true));
        scene(folder, "1080p_gui2_reconstruir_en.png", 960, 540, 2, "en_us", driver -> rebuild(driver, false, false));
        scene(folder, "854_gui2_reconstruir_es.png", 427, 240, 2, "es_es", driver -> rebuild(driver, false, false));
    }

    private static void scene(Path folder, String file, int width, int height, int scale, String language,
                              Consumer<StudioDriver> setup) throws IOException {
        StudioDriver driver = new StudioDriver(language, width, height);
        driver.host.context = SELECTED;
        driver.host.info = new ToolsInfo(true, new int[]{10, 6, 12}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of("minecraft:plains"), null);
        setup.accept(driver);
        BufferedImage image = driver.paint(scale, ui -> StudioMockupsTest.world(ui, width, height));
        ImageIO.write(image, "png", folder.resolve(file).toFile());
    }

    private static void rebuild(StudioDriver driver, boolean more, boolean blueprint) {
        driver.view.selectStage(StudioStage.TOOLS, driver.host);
        driver.host.preferences.pushToolRecent("text");
        driver.host.preferences.pushToolRecent("analyze");
        ToolCard card = ToolCatalog.byId("rebuild");
        driver.view.tools().select(card, driver.host);
        if (blueprint) {
            driver.view.tools().setValues(card, driver.view.tools().values(card).with("source", "blueprint")
                    .with("blueprint", "casa_roble").with("order", "waves").with("particles", true));
        }
        if (more) {
            driver.host.preferences.setExpanded("tools:more:rebuild", true);
            for (int step = 0; step < 14 && !driver.has("Cancelar"); step++) {
                driver.view.mouseScrolled(driver.view.frame().innerLeft() + 4, 200, -1, driver.host);
            }
        }
        driver.host.status = "";
    }
}
