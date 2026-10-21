package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.WelcomeTour;
import com.komainu.moxia.studio.WelcomeTour.Beat;
import com.komainu.moxia.studio.tools.TerrainCards;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La bienvenida guiada dentro del Studio de verdad: sale la primera vez y queda apuntada, se hace pulsando los mismos
 * controles que pulsaría un novato, nada se coloca sin que lo confirme, en supervivencia solo enseña y se vuelve a
 * lanzar desde la ayuda.
 */
class StudioWelcomeTest {
    private static final ToolContext ALL = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L, 0, false);
    private static final String HISTORY = "Cambios en el mundo: deshacer o rehacer hasta un punto (Ctrl+Z, Ctrl+Y)";

    /** Un Studio abierto por primera vez por un jugador en creativo con todos los permisos. */
    static StudioDriver firstTime(String language, int width, int height) {
        StudioDriver driver = new StudioDriver(language, width, height);
        driver.host.preferences.setWelcome(0);
        driver.host.context = ALL;
        driver.host.info = StudioMockupsTest.info();
        return driver;
    }

    static StudioHost.Ghost ghost(String position, boolean waiting) {
        return new StudioHost.Ghost("Árbol roble", "giro 0° · 7×12×7", position, false, true, false, true, waiting,
                "", () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, true);
    }

    /**
     * Pulsa la zona con nombre {@code anchor} (una herramienta de la cuadrícula) por su icono, lejos de la estrella de
     * favorita: con el nombre en dos líneas, buscarlo por su texto daría con la estrella, que lleva la misma ayuda.
     */
    static void press(StudioDriver driver, String anchor) {
        driver.render();
        StudioUi.Anchor zone = driver.ui().anchor(anchor);
        assertTrue(zone != null && !zone.visible().isEmpty(), () -> anchor + " no se ve: " + driver.texts());
        driver.clickAt(zone.visible().x() + 8, zone.visible().centerY());
    }

    /** Pasa un poco de tiempo: la vista mira lo que hay y prepara el golpe que empieza. */
    private static void settle(StudioDriver driver) {
        driver.advance(50);
        driver.render();
        driver.render();
    }

    @Test
    void itShowsTheFirstTimeOnlyAndIsRemembered() {
        StudioDriver driver = firstTime("es_es", 640, 360);
        assertTrue(driver.has("Primeros pasos en el Studio"), () -> "la entrada sale sola: " + driver.texts());
        assertEquals(Beat.INTRO, driver.view.welcome().beat());
        assertEquals(WelcomeTour.VERSION, driver.host.preferences.welcome(), "queda apuntada al salir");
        assertTrue(driver.host.saves > 0, "y se guarda en studio.json");
        assertEquals(WelcomeTour.VERSION, StudioPreferences.fromJson(driver.host.preferences.toJson()).welcome());

        driver.click("Ahora no");
        assertFalse(driver.view.welcome().active());
        assertTrue(driver.view.status().contains("/komaedit tutorial"), driver.view.status());

        StudioDriver again = new StudioDriver("es_es", 640, 360);
        again.host.preferences.setWelcome(WelcomeTour.VERSION);
        again.render();
        assertFalse(again.view.welcome().active(), "a quien ya la vio no le vuelve a salir");
        assertFalse(again.has("Primeros pasos en el Studio"));
    }

    @Test
    void aBeginnerDoesItWithTheRealControlsAndNothingIsPlacedWithoutConfirming() {
        StudioDriver driver = firstTime("es_es", 640, 360);
        driver.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.OFF);
        driver.render();
        driver.click("Empezar");
        settle(driver);
        assertEquals(StudioPreferences.PreviewMode.LIVE, driver.host.preferences.previewMode(),
                "durante la bienvenida Construir va siempre con vista previa");
        assertEquals(StudioStage.TOOLS, driver.view.stage(), "abre Herramientas para elegir");
        assertTrue(driver.view.tools().browsing());
        assertTrue(driver.shows("Pulsa «Árbol»"), () -> "la burbuja dice qué hacer: " + driver.texts());
        assertTrue(driver.shows("Paso 1 de 5"));

        // 1. Árbol y un clic en el mundo: sale como fantasma (con -v), no se planta.
        press(driver, "tool:tree");
        settle(driver);
        assertEquals(Beat.TREE_CLICK, driver.view.welcome().beat());
        assertTrue(driver.shows("Haz clic en el suelo"));
        assertEquals(StudioView.WorldAction.PLACE, driver.view.worldAction(driver.host));
        driver.host.clickedSpot = new int[]{124, 64, -38};
        driver.view.placeAtClick(driver.host);
        assertTrue(driver.host.last().startsWith("komaedit tree "), driver.host.last());
        assertTrue(driver.host.last().contains(" -v"), "el árbol sale como vista previa: " + driver.host.last());
        driver.host.ghost = ghost("124, 65, -38", false);
        settle(driver);
        assertEquals(Beat.GHOST_MOVE, driver.view.welcome().beat());

        // 2. Moverlo y colocarlo.
        driver.host.ghost = ghost("126, 65, -39 · (+2, 0, -1)", false);
        settle(driver);
        assertEquals(Beat.GHOST_PLACE, driver.view.welcome().beat());
        assertTrue(driver.shows("Pulsa «Colocar»"));
        driver.click("Colocar");
        driver.host.ghost = ghost("126, 65, -39 · (+2, 0, -1)", true);
        settle(driver);
        driver.host.ghost = null;
        settle(driver);
        assertEquals(Beat.STAMP_PICK, driver.view.welcome().beat());
        assertTrue(driver.view.tools().browsing(), "vuelve a la cuadrícula para elegir el sello");

        // 3. Sello, montaña, pincel con vista previa, clic, otra semilla, otro clic y aplicar.
        press(driver, "tool:stamp");
        settle(driver);
        assertEquals(Beat.STAMP_CHOOSE, driver.view.welcome().beat());
        driver.click("Montaña", WelcomeTour.STAMP_GROUP);
        settle(driver);
        assertEquals(Beat.STAMP_BRUSH, driver.view.welcome().beat());
        driver.click("Al pincel");
        String stamp = driver.host.last();
        assertTrue(stamp.startsWith("komaedit pincel heightmap montana"), stamp);
        assertTrue(stamp.contains(" -p"), "el pincel enseña cada clic antes de tocar nada: " + stamp);
        settle(driver);
        assertEquals(Beat.STAMP_CLICK, driver.view.welcome().beat());
        driver.view.brushUsed();
        settle(driver);
        assertEquals(Beat.STAMP_SEED, driver.view.welcome().beat());
        driver.click("Azar", "tools:preset");
        settle(driver);
        assertEquals(Beat.STAMP_AGAIN, driver.view.welcome().beat());
        driver.view.brushUsed();
        settle(driver);
        assertEquals(Beat.STAMP_APPLY, driver.view.welcome().beat());
        assertFalse(driver.host.sent.contains(WelcomeTour.APPLY), "no se aplica hasta que lo pide");
        driver.click("Aplicar");
        assertTrue(driver.host.sent.contains(WelcomeTour.APPLY));
        settle(driver);

        // 4. Pintar terreno con «Cumbres», el pincel con vista previa y aplicar.
        assertEquals(Beat.PAINT_PICK, driver.view.welcome().beat());
        press(driver, "tool:paintbrush");
        settle(driver);
        assertEquals(Beat.PAINT_PRESET, driver.view.welcome().beat());
        driver.click("Cumbres", "tools:preset");
        settle(driver);
        assertEquals(Beat.PAINT_BRUSH, driver.view.welcome().beat());
        driver.click("Al pincel");
        String paint = driver.host.last();
        assertTrue(paint.startsWith("komaedit pincel paint cumbres"), paint);
        assertTrue(paint.contains(" -p"), paint);
        settle(driver);
        driver.view.brushUsed();
        settle(driver);
        assertEquals(Beat.PAINT_APPLY, driver.view.welcome().beat());
        driver.click("Aplicar");
        settle(driver);

        // 5. Deshacer desde el historial.
        assertEquals(Beat.UNDO_OPEN, driver.view.welcome().beat());
        driver.host.history = new StudioHost.WorldHistory(List.of(
                new StudioHost.WorldHistory.Entry("Pincel de pintura", 640),
                new StudioHost.WorldHistory.Entry("Sello de relieve", 2_100),
                new StudioHost.WorldHistory.Entry("Árbol", 412)), 0);
        driver.click(HISTORY);
        settle(driver);
        assertEquals(Beat.UNDO_DO, driver.view.welcome().beat());
        driver.click("Deshacer");
        assertEquals(1, driver.host.undos);
        settle(driver);
        assertEquals(Beat.DONE, driver.view.welcome().beat());
        assertTrue(driver.has("¡Listo!"));
        driver.click("Terminar");
        assertFalse(driver.view.welcome().active());

        // Lo que se mandó: nada que coloque sin vista previa ni sin que el jugador lo pidiera.
        for (String command : driver.host.sent) {
            if (command.startsWith("komaedit tree ")) {
                assertTrue(command.contains(" -v"), command);
            }
            if (command.startsWith("komaedit pincel heightmap ") || command.startsWith("komaedit pincel paint ")) {
                assertTrue(command.contains(" -p"), command);
            }
        }
        assertEquals(2, driver.host.sent.stream().filter(WelcomeTour.APPLY::equals).count(),
                "solo las dos veces que pulsó Aplicar");
        // Al acabar vuelve lo que tenía.
        assertEquals(StudioPreferences.PreviewMode.OFF, driver.host.preferences.previewMode());
        for (String id : List.of(WelcomeTour.STAMP, WelcomeTour.PAINT)) {
            assertFalse(driver.view.tools().values(ToolCatalog.byId(id)).toggle("brush_preview"), id);
            assertFalse(driver.host.preferences.toolValues(id).containsKey("brush_preview"), id);
        }
    }

    @Test
    void inSurvivalItOnlyShowsAndSendsNothingThatPlaces() {
        StudioDriver driver = firstTime("es_es", 640, 360);
        driver.host.creative = false;
        assertTrue(driver.shows("Aquí no puedes construir"), () -> "la entrada lo dice: " + driver.texts());
        driver.click("Empezar");
        settle(driver);
        press(driver, "tool:tree");
        settle(driver);
        assertEquals(Beat.TREE_LOOK, driver.view.welcome().beat());
        assertTrue(driver.shows("En creativo"));
        driver.click("Siguiente");
        settle(driver);
        press(driver, "tool:stamp");
        settle(driver);
        driver.click("Montaña", WelcomeTour.STAMP_GROUP);
        settle(driver);
        driver.click("Azar", "tools:preset");
        settle(driver);
        assertEquals(Beat.STAMP_LOOK, driver.view.welcome().beat());
        driver.click("Siguiente");
        settle(driver);
        press(driver, "tool:paintbrush");
        settle(driver);
        driver.click("Cumbres", "tools:preset");
        settle(driver);
        driver.click("Siguiente");
        settle(driver);
        driver.click(HISTORY);
        settle(driver);
        assertEquals(Beat.UNDO_LOOK, driver.view.welcome().beat());
        driver.click("Siguiente");
        settle(driver);
        assertTrue(driver.shows("Cuando puedas construir"));
        driver.click("Terminar");
        assertTrue(driver.host.sent.stream().noneMatch(command -> command.startsWith("komaedit tree")
                        || command.startsWith("komaedit pincel")),
                () -> "en supervivencia no se manda nada que coloque: " + driver.host.sent);
        assertEquals(0, driver.host.undos);
        assertEquals(StudioPreferences.PreviewMode.LIVE, driver.host.preferences.previewMode());
    }

    @Test
    void aStepWithoutPermissionIsSkippedAndTheBubbleSaysSo() {
        StudioDriver driver = firstTime("es_es", 640, 360);
        Set<String> granted = new HashSet<>(ToolCatalog.permissionNodes());
        granted.remove(TerrainCards.STAMP_NODE);
        driver.host.context = new ToolContext(true, granted, false, 0L, 0, false);
        driver.render();
        driver.click("Empezar");
        settle(driver);
        driver.click("Saltar paso");
        settle(driver);
        assertEquals(Beat.PAINT_PICK, driver.view.welcome().beat(), "sin permiso para el sello, a la pintura");
        assertTrue(driver.shows("Sin permiso para Sello de"), () -> "y lo dice: " + driver.texts());
        assertTrue(driver.shows("Paso 3 de 4"), () -> "los pasos que quedan: " + driver.texts());
    }

    @Test
    void itIsRelaunchedFromTheHelpAndClosingTheStudioGivesBackThePreview() {
        StudioDriver driver = new StudioDriver("es_es", 640, 360);
        driver.host.context = ALL;
        driver.host.preferences.setPreviewMode(StudioPreferences.PreviewMode.OFF);
        driver.render();
        assertFalse(driver.view.welcome().active());
        driver.key(StudioKey.of(StudioKey.Code.F1));
        assertTrue(driver.has("Bienvenida guiada"), () -> "la chuleta la ofrece: " + driver.texts());
        driver.click("Bienvenida guiada");
        assertFalse(driver.view.cheatSheet(), "la chuleta se cierra");
        assertEquals(Beat.INTRO, driver.view.welcome().beat());
        driver.click("Empezar");
        settle(driver);
        assertEquals(StudioPreferences.PreviewMode.LIVE, driver.host.preferences.previewMode());
        assertTrue(driver.view.tools().values(ToolCatalog.byId(WelcomeTour.STAMP)).toggle("brush_preview"));

        driver.view.closed(driver.host);
        assertEquals(StudioPreferences.PreviewMode.OFF, driver.host.preferences.previewMode(),
                "al cerrar el Studio no se queda guardado lo que puso la bienvenida");
        assertFalse(driver.view.tools().values(ToolCatalog.byId(WelcomeTour.STAMP)).toggle("brush_preview"));
        settle(driver);
        assertEquals(StudioPreferences.PreviewMode.LIVE, driver.host.preferences.previewMode(),
                "al volver a abrirlo sigue donde iba, otra vez con vista previa");

        driver.view.startWelcome();
        settle(driver);
        assertEquals(Beat.INTRO, driver.view.welcome().beat(), "/komaedit tutorial la empieza de cero");
        assertEquals(StudioPreferences.PreviewMode.OFF, driver.host.preferences.previewMode());
    }

    @Test
    void theBubbleStaysBesideThePanelAndNextToWhatItPointsAt() {
        for (int[] size : new int[][]{{640, 360}, {427, 240}}) {
            StudioDriver driver = firstTime("en_us", size[0], size[1]);
            driver.render();
            driver.click("Start");
            settle(driver);
            var frame = driver.view.frame();
            StudioUi.Anchor tree = driver.ui().anchor("tool:tree");
            assertTrue(tree != null && !tree.visible().isEmpty(), "the tree is in sight at " + size[0]);
            StudioUi.Widget skip = driver.widget("Skip step");
            assertTrue(skip.bounds().right() <= frame.panel().x(), "the bubble does not cover the panel");
            assertTrue(skip.bounds().bottom() <= frame.screen().height());
            assertTrue(driver.shows("Press “Tree”"), () -> driver.texts().toString());
        }
    }
}
