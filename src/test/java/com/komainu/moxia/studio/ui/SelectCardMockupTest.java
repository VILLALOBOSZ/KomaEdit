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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maquetas de la tarjeta «Seleccionar» (lazo y pincel de selección) con el mismo código que el juego, como
 * {@link StudioMockupsTest}: con el lazo, con el pincel, con «Más ajustes» abierto y en inglés, y el ratón sobre el
 * mundo para que el pie diga qué hace cada tecla. Solo con {@code KOMAEDIT_STUDIO_MOCKUPS=<carpeta>}.
 */
class SelectCardMockupTest {
    private static final ToolContext SELECTED = new ToolContext(true, ToolCatalog.permissionNodes(), true,
            657L, 0, false);

    @Test
    void renderSelectCard() throws IOException {
        String target = System.getenv("KOMAEDIT_STUDIO_MOCKUPS");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "KOMAEDIT_STUDIO_MOCKUPS sin definir");
        assertTrue(ImageCanvas.font().loaded(), "la fuente del juego tiene que estar en el classpath");
        ChoiceThumbnails.setBlocking(true);
        Path folder = Path.of(target);
        Files.createDirectories(folder);
        scene(folder, "1080p_gui3_seleccionar_lazo_es.png", 640, 360, 3, "es_es", driver -> select(driver, "lasso",
                false));
        scene(folder, "1080p_gui2_seleccionar_lazo_es.png", 960, 540, 2, "es_es", driver -> select(driver, "lasso",
                false));
        scene(folder, "1080p_gui2_seleccionar_pincel_es.png", 960, 540, 2, "es_es", driver -> select(driver, "brush",
                false));
        scene(folder, "1080p_gui2_seleccionar_mas_ajustes_es.png", 960, 540, 2, "es_es", driver -> select(driver,
                "brush", true));
        scene(folder, "1080p_gui2_seleccionar_lazo_en.png", 960, 540, 2, "en_us", driver -> select(driver, "lasso",
                false));
        scene(folder, "854_gui2_seleccionar_lazo_es.png", 427, 240, 2, "es_es", driver -> select(driver, "lasso",
                false));
    }

    private static void scene(Path folder, String file, int width, int height, int scale, String language,
                              Consumer<StudioDriver> setup) throws IOException {
        StudioDriver driver = new StudioDriver(language, width, height);
        driver.host.context = SELECTED;
        driver.host.info = new ToolsInfo(true, new int[]{34, 9, 27}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of("minecraft:plains"), null);
        setup.accept(driver);
        BufferedImage image = driver.paint(scale, ui -> StudioMockupsTest.world(ui, width, height));
        ImageIO.write(image, "png", folder.resolve(file).toFile());
    }

    private static void select(StudioDriver driver, String tool, boolean more) {
        driver.view.selectStage(StudioStage.TOOLS, driver.host);
        ToolCard card = ToolCatalog.byId("select");
        driver.view.tools().select(card, driver.host);
        driver.view.tools().setValues(card, driver.view.tools().values(card).with("tool", tool));
        assertEquals(tool.equals("lasso") ? StudioView.WorldAction.LASSO : StudioView.WorldAction.FREEHAND,
                driver.view.worldAction(driver.host));
        if (more) {
            driver.host.preferences.setExpanded("tools:more:select", true);
            for (int step = 0; step < 14 && !driver.has("Deshacer trazo"); step++) {
                driver.view.mouseScrolled(driver.view.frame().innerLeft() + 4, 200, -1, driver.host);
            }
        }
        driver.host.status = "";
        // El ratón sobre el mundo: el pie dice qué hacen el clic y las teclas.
        driver.render();
        driver.mouseX = 40;
        driver.mouseY = 120;
    }
}
