package com.komainu.moxia.studio.ui;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.studio.StudioNames;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.layout.StudioLayout;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El modo compacto de verdad: a 854×480 con GUI 2 (y 1080p con GUI 4) lo esencial de cada herramienta (elegir,
 * ajustar y construir) se ve sin desplazar, sin descripciones y con lo demás plegado.
 */
class CompactModeTest {
    /**
     * Herramientas que se usan sobre la marcha; las de listas largas (capacidades, el bioma de Retocar) desplazan, y
     * está bien, como Pintar con el pincel, con sus dos galerías (ya desplazaba como «Pintar terreno»). Retocar, en
     * sus modos de uso diario, y Pintar, la superficie natural de la selección (lo que era «Pintar superficie»).
     */
    private static final List<String> ESSENTIAL = List.of("tree", "rock", "roof", "loft", "stamp", "ramp", "river",
            "erosion", "text", "touchup", "symmetry");
    private static final List<String> TOUCH_UPS = List.of("nieve", "drenar", "inundar", "gravedad");

    @BeforeAll
    static void thumbnails() {
        ChoiceThumbnails.setBlocking(true);
    }

    private static StudioDriver studio(int width, int height) {
        StudioDriver studio = new StudioDriver("es_es", width, height);
        studio.host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 4, false);
        studio.host.info = new ToolsInfo(true, new int[]{10, 6, 12}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of(), null);
        studio.view.selectStage(StudioStage.TOOLS, studio.host);
        return studio;
    }

    @Test
    void smallScreensAreCompactAndBigOnesAreNot() {
        assertTrue(StudioLayout.compact(427, 240), "854×480 con GUI 2");
        assertTrue(StudioLayout.compact(480, 270), "1080p con GUI 4");
        assertFalse(StudioLayout.compact(640, 360), "1080p con GUI 3");
        assertFalse(StudioLayout.compact(960, 540), "1080p con GUI 2");
        StudioLayout.Frame frame = StudioLayout.compute(427, 240, 0, StudioStage.groupSizes());
        assertEquals(StudioLayout.NavMode.ICONS, frame.navMode());
        assertEquals(StudioLayout.COMPACT_FOOTER_HEIGHT, frame.footer().height());
    }

    @Test
    void everyEssentialToolFitsWithoutScrollingAt854x480() {
        for (int[] screen : new int[][]{{427, 240}, {480, 270}}) {
            List<String> cases = new java.util.ArrayList<>(ESSENTIAL);
            TOUCH_UPS.forEach(mode -> cases.add("touchup:touch=" + mode));
            cases.add("paint:gradient=natural:use=selection");
            for (String id : cases) {
                StudioDriver studio = studio(screen[0], screen[1]);
                ToolCard card = ToolCatalog.byId(id.split(":")[0]);
                studio.view.tools().select(card, studio.host);
                for (String setting : id.contains(":") ? id.substring(id.indexOf(':') + 1).split(":") : new String[0]) {
                    String[] pair = setting.split("=");
                    studio.view.tools().setValues(card, studio.view.tools().values(card).with(pair[0], pair[1]));
                }
                studio.render();
                Rect viewport = studio.view.frame().viewport();
                assertTrue(studio.view.contentHeight() <= viewport.height(), screen[0] + "×" + screen[1] + " · "
                        + id + ": " + studio.view.contentHeight() + " > " + viewport.height());
                String build = studio.view.tools().buildLabel(studio.host);
                assertTrue(studio.has(build), id + ": el botón «" + build + "» a la vista");
            }
        }
    }

    @Test
    void theRestIsFoldedAndThereAreNoDescriptions() {
        StudioDriver studio = studio(427, 240);
        studio.view.tools().select(ToolCatalog.byId("tree"), studio.host);
        assertTrue(studio.has("MÁS AJUSTES"));
        assertFalse(studio.has("Semilla"), "la semilla queda plegada");
        assertFalse(studio.has("Joven"), "los presets, también: en compacto van dentro de «Más ajustes»");
        studio.view.figure().selectShape(studio.host, PathShape.BRAID);
        studio.view.selectStage(StudioStage.FIGURE, studio.host);
        String description = studio.host.tr(StudioNames.shapeDescription(PathShape.BRAID));
        assertFalse(studio.texts().stream().anyMatch(description::startsWith), "sin la descripción de la figura");
        StudioDriver roomy = studio(640, 360);
        roomy.view.figure().selectShape(roomy.host, PathShape.BRAID);
        roomy.view.selectStage(StudioStage.FIGURE, roomy.host);
        assertTrue(roomy.texts().stream().anyMatch(text -> description.startsWith(text.replace("…", ""))),
                "con sitio, la descripción sí sale");
    }

    @Test
    void buttonChoicesBecomeOneRow() {
        StudioDriver studio = studio(427, 240);
        studio.view.tools().select(ToolCatalog.byId("river"), studio.host);
        assertTrue(studio.has("Tipo"));
        assertTrue(studio.has("Río"), "el tipo elegido, como ‹ valor ›");
        assertFalse(studio.has("Barranco"), "no todos los botones, que gastarían dos filas");
        studio.click("Río");
        assertEquals("arroyo", studio.view.tools().values(ToolCatalog.byId("river")).choice("kind"),
                "pulsar el valor pasa al siguiente");
    }

    @Test
    void theGalleryIsOneRowWithPages() {
        StudioDriver studio = studio(427, 240);
        studio.view.tools().select(ToolCatalog.byId("tree"), studio.host);
        assertTrue(studio.has("Roble"));
        assertFalse(studio.has("Baobab"), "solo una fila");
        for (int page = 0; page < 4 && !studio.has("Baobab"); page++) {
            studio.click("›", "tools:species:page");
        }
        assertTrue(studio.has("Baobab"), "pasando de página");
    }
}
