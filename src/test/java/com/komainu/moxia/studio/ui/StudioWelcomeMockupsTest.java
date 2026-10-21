package com.komainu.moxia.studio.ui;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maquetas de la bienvenida guiada, paso a paso, con el Studio de verdad (como {@link StudioMockupsTest}): cada escena
 * llega a su golpe pulsando los mismos controles que un jugador. Van a {@code <carpeta>/bienvenida/} con
 * {@code KOMAEDIT_STUDIO_MOCKUPS=<carpeta>}:
 *
 * <pre>KOMAEDIT_STUDIO_MOCKUPS=build/maquetas ./gradlew test --tests "*StudioWelcomeMockupsTest"</pre>
 */
class StudioWelcomeMockupsTest {
    /** Las escenas que se pintan en los dos tamaños y los dos idiomas. */
    static Map<String, Consumer<StudioDriver>> steps() {
        Map<String, Consumer<StudioDriver>> scenes = new LinkedHashMap<>();
        scenes.put("0_entrada", StudioWelcomeMockupsTest::intro);
        scenes.put("1_arbol", StudioWelcomeMockupsTest::treePick);
        scenes.put("2_colocar", StudioWelcomeMockupsTest::ghostPlace);
        scenes.put("3_sello", StudioWelcomeMockupsTest::stampChoose);
        scenes.put("4_pintar", StudioWelcomeMockupsTest::paintPreset);
        scenes.put("5_deshacer", StudioWelcomeMockupsTest::undo);
        scenes.put("6_final", StudioWelcomeMockupsTest::done);
        return scenes;
    }

    @Test
    void renderWelcomeMockups() throws IOException {
        String target = System.getenv("KOMAEDIT_STUDIO_MOCKUPS");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "KOMAEDIT_STUDIO_MOCKUPS sin definir");
        assertTrue(ImageCanvas.font().loaded(), "la fuente del juego tiene que estar en el classpath");
        ChoiceThumbnails.setBlocking(true);
        Path folder = Path.of(target).resolve("bienvenida");
        Files.createDirectories(folder);
        for (String language : List.of("es_es", "en_us")) {
            String suffix = language.substring(0, 2);
            for (Map.Entry<String, Consumer<StudioDriver>> scene : steps().entrySet()) {
                write(folder, "1080p_gui3_bienvenida_" + scene.getKey() + "_" + suffix, 640, 360, 3, language,
                        scene.getValue());
                write(folder, "854_gui2_bienvenida_" + scene.getKey() + "_" + suffix, 427, 240, 2, language,
                        scene.getValue());
            }
        }
        // Los golpes en el mundo y el de aplicar, y en supervivencia (solo enseña).
        write(folder, "1080p_gui3_bienvenida_1_clic_es", 640, 360, 3, "es_es", StudioWelcomeMockupsTest::treeClick);
        write(folder, "1080p_gui3_bienvenida_3_aplicar_es", 640, 360, 3, "es_es", StudioWelcomeMockupsTest::stampApply);
        write(folder, "854_gui2_bienvenida_3_aplicar_en", 427, 240, 2, "en_us", StudioWelcomeMockupsTest::stampApply);
        write(folder, "1080p_gui3_bienvenida_supervivencia_es", 640, 360, 3, "es_es", StudioWelcomeMockupsTest::survival);
        write(folder, "854_gui2_bienvenida_supervivencia_en", 427, 240, 2, "en_us", StudioWelcomeMockupsTest::survival);
    }

    private static void write(Path folder, String name, int width, int height, int scale, String language,
                              Consumer<StudioDriver> setup) throws IOException {
        StudioDriver driver = driver(language, width, height);
        setup.accept(driver);
        BufferedImage image = driver.paint(scale, ui -> StudioMockupsTest.world(ui, width, height));
        ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
    }

    /** Un Studio como el de las maquetas, con la bienvenida a la vista desde la entrada. */
    static StudioDriver driver(String language, int width, int height) {
        StudioDriver driver = new StudioDriver(language, width, height);
        driver.host.context = StudioMockupsTest.SELECTED;
        driver.host.info = StudioMockupsTest.info();
        // La luz del borde, a un tercio de su vuelta.
        driver.host.now += WelcomeBubble.LAP_MILLIS / 3;
        return driver;
    }

    private static boolean spanish(StudioDriver driver) {
        return driver.host.tr("gui.komaedit.welcome.start").equals("Empezar");
    }

    private static void settle(StudioDriver driver) {
        driver.tick();
        driver.render();
        driver.render();
    }

    /** Sin los mensajes del pie de los golpes de antes (en el juego se van solos a los 4 s). */
    private static void quiet(StudioDriver driver) {
        driver.view.status("", driver.host.now());
        driver.host.status = "";
    }

    static void intro(StudioDriver driver) {
        driver.view.startWelcome();
        settle(driver);
    }

    static void treePick(StudioDriver driver) {
        intro(driver);
        driver.view.welcome().start();
        settle(driver);
    }

    static void treeClick(StudioDriver driver) {
        treePick(driver);
        StudioWelcomeTest.press(driver, "tool:tree");
        settle(driver);
        // El ratón sobre el mundo, donde va a hacer clic.
        driver.mouseX = 150;
        driver.mouseY = 230;
    }

    static void ghostPlace(StudioDriver driver) {
        treePick(driver);
        StudioWelcomeTest.press(driver, "tool:tree");
        settle(driver);
        driver.host.clickedSpot = new int[]{124, 64, -38};
        driver.view.placeAtClick(driver.host);
        driver.host.ghost = ghost(driver, "124, 65, -38", false);
        settle(driver);
        driver.host.ghost = ghost(driver, "126, 65, -39 · (+2, 0, -1)", false);
        settle(driver);
        quiet(driver);
    }

    static void stampChoose(StudioDriver driver) {
        ghostPlace(driver);
        StudioWelcomeTest.press(driver, "ghost:place");
        driver.host.ghost = ghost(driver, "126, 65, -39 · (+2, 0, -1)", true);
        settle(driver);
        driver.host.ghost = null;
        settle(driver);
        StudioWelcomeTest.press(driver, "tool:stamp");
        settle(driver);
        quiet(driver);
    }

    static void stampApply(StudioDriver driver) {
        stampChoose(driver);
        StudioWelcomeTest.press(driver, "stamp:montana");
        settle(driver);
        StudioWelcomeTest.press(driver, "footer:build");
        settle(driver);
        driver.view.brushUsed();
        settle(driver);
        StudioWelcomeTest.press(driver, "preset:random");
        settle(driver);
        driver.view.brushUsed();
        settle(driver);
        driver.mouseX = 150;
        driver.mouseY = 230;
    }

    static void paintPreset(StudioDriver driver) {
        stampApply(driver);
        driver.view.welcome().apply();
        settle(driver);
        StudioWelcomeTest.press(driver, "tool:paint");
        settle(driver);
        quiet(driver);
        driver.mouseX = -1;
        driver.mouseY = -1;
    }

    static void undo(StudioDriver driver) {
        paintPreset(driver);
        StudioWelcomeTest.press(driver, "preset:" + com.komainu.moxia.studio.WelcomeTour.PEAKS);
        settle(driver);
        StudioWelcomeTest.press(driver, "footer:build");
        settle(driver);
        driver.view.brushUsed();
        settle(driver);
        driver.view.welcome().apply();
        settle(driver);
        boolean spanish = spanish(driver);
        driver.host.history = new StudioHost.WorldHistory(List.of(
                new StudioHost.WorldHistory.Entry(spanish ? "Trazo" : "Stroke", 640),
                new StudioHost.WorldHistory.Entry(spanish ? "Sello de relieve" : "Height stamp", 2_100),
                new StudioHost.WorldHistory.Entry(spanish ? "Árbol" : "Tree", 412)), 0);
        StudioWelcomeTest.press(driver, "top:history");
        settle(driver);
        quiet(driver);
    }

    static void done(StudioDriver driver) {
        undo(driver);
        StudioWelcomeTest.press(driver, "history:undo");
        driver.host.history = new StudioHost.WorldHistory(driver.host.history.entries(), 1);
        settle(driver);
    }

    /** En supervivencia: elegir sí, colocar no; la burbuja lo enseña con «Siguiente». */
    static void survival(StudioDriver driver) {
        driver.host.creative = false;
        treePick(driver);
        StudioWelcomeTest.press(driver, "tool:tree");
        settle(driver);
    }

    private static StudioHost.Ghost ghost(StudioDriver driver, String position, boolean waiting) {
        boolean spanish = spanish(driver);
        return new StudioHost.Ghost(spanish ? "Árbol roble" : "Oak tree",
                spanish ? "giro 0° · 7×12×7" : "turned 0° · 7×12×7", position, false, true, false, true, waiting, "",
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, true);
    }
}
