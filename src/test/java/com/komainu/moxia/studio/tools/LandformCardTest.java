package com.komainu.moxia.studio.tools;

import com.komainu.moxia.terrain.LandformPlanner;
import com.komainu.moxia.terrain.LandformRequest;
import com.komainu.moxia.terrain.stamp.StampCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La tarjeta «Trazar relieve»: la orden que manda la lee el servidor con los mismos ajustes. */
class LandformCardTest {
    private static final ToolCard CARD = ToolCatalog.byId("landform");
    private static final ToolContext NOTHING = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L, 0,
            false);
    private static final ToolContext PATH = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L, 3,
            false);

    private static LandformRequest read(String command) {
        String rest = command.substring("komaedit terreno ".length());
        int space = rest.indexOf(' ');
        String kind = space < 0 ? rest : rest.substring(0, space);
        return LandformRequest.parse(LandformPlanner.Kind.byId(kind), space < 0 ? "" : rest.substring(space + 1));
    }

    @Test
    void theDefaultsAreTheShortestCommandAndTheServerFillsTheRest() {
        assertEquals("komaedit terreno cordillera", CARD.command(CARD.defaults(), false));
        assertEquals("komaedit terreno cordillera -v", CARD.command(CARD.defaults(), true));
        LandformRequest request = read(CARD.command(CARD.defaults(), false));
        assertEquals(LandformRequest.defaultWidth(LandformPlanner.Kind.RANGE), request.width());
        assertEquals(LandformRequest.defaultHeight(LandformPlanner.Kind.RANGE), request.height());
        assertEquals(LandformPlanner.Profile.BELL, request.profile());
        assertEquals(LandformPlanner.Blend.ADD, request.blend());
        assertEquals("alpino", request.paletteName());
        assertEquals(StampCategory.RANGES, request.family());
        assertNull(request.seed(), "sin semilla escrita, una al azar en el servidor");
        assertFalse(request.water());
    }

    @Test
    void theServerReadsBackWhatTheCardSet() {
        ToolValues values = CARD.defaults().with("landform", "costa").with("width", 80).with("height", 30)
                .with("land_side", "izquierda").with("along", "sube").with("variation", 40).with("seed", 77)
                .with("family", "rocas").with("mode", "maximo").with("erosion", 0).with("water", "seco")
                .with("palette", "rocoso");
        String command = CARD.command(values, false);
        LandformRequest request = read(command);
        assertEquals(LandformPlanner.Kind.COAST, request.kind());
        assertEquals(80.0, request.width());
        assertEquals(30.0, request.height());
        assertEquals(LandformPlanner.Side.LEFT, request.side());
        assertEquals(LandformPlanner.Profile.RISING, request.profile());
        assertEquals(0.4, request.variation(), 1e-9);
        assertEquals(77L, request.seed());
        assertEquals(StampCategory.ROCKS, request.family());
        assertEquals(LandformPlanner.Blend.MAX, request.blend());
        assertEquals(0.0, request.erosion());
        assertFalse(request.water());
        assertEquals("rocoso", request.paletteName());
    }

    @Test
    void theValleyHasItsDepthSectionAndRiver() {
        ToolValues values = CARD.defaults().with("landform", "valle").with("depth", 36).with("section", "canon")
                .with("water", "agua");
        LandformRequest request = read(CARD.command(values, false));
        assertEquals(LandformPlanner.Kind.VALLEY, request.kind());
        assertEquals(36.0, request.height());
        assertEquals(LandformRequest.defaultWidth(LandformPlanner.Kind.VALLEY), request.width(),
                "con la hondura va también el ancho de serie");
        assertEquals(LandformPlanner.Form.CANYON, request.form());
        assertTrue(request.water());
        assertEquals("mesa", request.paletteName());
        assertEquals(LandformPlanner.Blend.MAX, request.blend(), "el valle talla sin rellenar lo hondo");
        assertFalse(values.param("land_side").visible(values), "el lado solo en la costa");
        assertFalse(values.param("height").visible(values), "en el valle es la hondura");
    }

    @Test
    void withoutPathOrSelectionTheButtonSaysWhatIsMissing() {
        ToolProblem problem = CARD.requirement(CARD.defaults(), NOTHING);
        assertEquals("no_path_landform", problem.id());
        assertTrue(problem.blocking());
        assertNull(CARD.requirement(CARD.defaults(), PATH));
    }

    @Test
    void badWordsAreRefusedWithAReason() {
        assertThrows(IllegalArgumentException.class, () -> LandformRequest.parse(LandformPlanner.Kind.RANGE,
                "100 40 30"));
        assertThrows(IllegalArgumentException.class, () -> LandformRequest.parse(LandformPlanner.Kind.RANGE,
                "-p zigzag"));
        assertThrows(IllegalArgumentException.class, () -> LandformRequest.parse(LandformPlanner.Kind.COAST,
                "-l arriba"));
        assertThrows(IllegalArgumentException.class, () -> LandformRequest.parse(LandformPlanner.Kind.RANGE,
                "4"));
        assertEquals(java.util.List.of("sierra", "macizo"),
                LandformRequest.parse(LandformPlanner.Kind.RANGE, "-h sierra,macizo").stampNames());
        assertTrue(LandformRequest.parse(LandformPlanner.Kind.RANGE, "-h ninguno").stampNames().isEmpty());
    }
}
