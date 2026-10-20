package com.komainu.moxia.gizmo;

import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.preview.EditRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que decide el servidor (alcance, validación) y las preferencias y teclas del cliente. */
class GhostRulesTest {
    private static final GhostValidation.World WORLD = new GhostValidation.World() {
        @Override
        public int bottomY() {
            return -64;
        }

        @Override
        public int topY() {
            return 320;
        }

        @Override
        public boolean insideBorder(int x, int z) {
            return Math.abs(x) < 1000 && Math.abs(z) < 1000;
        }

        @Override
        public boolean chunkLoaded(int chunkX, int chunkZ) {
            return Math.abs(chunkX) < 20 && Math.abs(chunkZ) < 20;
        }
    };

    @Test
    void theServerChecksHeightBorderDistanceAndLoadedChunks() {
        BlockBounds fine = new BlockBounds(0, 64, 0, 10, 80, 10);
        assertEquals(Optional.empty(), GhostValidation.check(fine, 5, 70, -3, true, WORLD));
        assertTrue(GhostValidation.check(fine, 5, 70, -3, false, WORLD).orElseThrow().contains("tumbar"));
        assertTrue(GhostValidation.check(new BlockBounds(0, 310, 0, 10, 330, 10), 5, 300, 0, true, WORLD)
                .orElseThrow().contains("altura"));
        assertTrue(GhostValidation.check(new BlockBounds(0, -70, 0, 10, -60, 10), 5, -60, 0, true, WORLD)
                .orElseThrow().contains("altura"));
        assertTrue(GhostValidation.check(new BlockBounds(990, 64, 0, 1010, 80, 10), 995, 70, 0, true, WORLD)
                .orElseThrow().contains("borde"));
        assertTrue(GhostValidation.check(new BlockBounds(0, 64, 0, 10, 80, 10), 5, 70, 700, true, WORLD)
                .orElseThrow().contains("lejos"));
        assertTrue(GhostValidation.check(new BlockBounds(330, 64, 0, 340, 80, 10), 330, 70, 0, true, WORLD)
                .orElseThrow().contains("cargada"));
        assertEquals(0.0, GhostValidation.distance(fine, 5, 70, 5));
        assertEquals(3.0, GhostValidation.distance(fine, 14, 70, 5));
    }

    @Test
    void onlyObjectsThatDoNotDependOnTheSiteBecomeGhosts() {
        for (String command : List.of("komaedit paste", "ke pegar -a", "komaedit place Sh(S:Cone,P:stone) Aim",
                "komaedit blueprint place casa", "mx planos colocar torre", "komaedit build coil stone 8",
                "komaedit torii 12 2 3 red_wool", "komaedit tree oak", "komaedit rock -s 4", "komaedit roof gable",
                "komaedit text Hola", "komaedit image url", "komaedit ezsp structure Clipboard",
                "/komaedit loft", "komaedit bend repeat")) {
            assertTrue(GhostScope.movable(command), command);
        }
        for (String command : List.of("komaedit scatter Clipboard", "komaedit array Clipboard Tangential",
                "komaedit weather", "komaedit texture rockify", "komaedit deform noise", "komaedit terrain raise",
                "komaedit ivy", "komaedit blueprint save casa", "komaedit blueprint stamp",
                "komaedit", "", "komaedit help paste", "komaedit sculpt set")) {
            assertFalse(GhostScope.movable(command), command);
        }
        assertFalse(GhostScope.movable((String) null));
        assertTrue(GhostScope.movable(EditRequest.command("/komaedit paste", false)));
    }

    @Test
    void theModeDecidesWhenTheGhostAppears() {
        EditRequest plain = EditRequest.command("komaedit paste", false);
        EditRequest preview = EditRequest.command("komaedit paste", true);
        assertTrue(GhostScope.applies(GizmoSettings.Mode.ALWAYS, plain));
        assertTrue(GhostScope.applies(GizmoSettings.Mode.ALWAYS, preview));
        assertFalse(GhostScope.applies(GizmoSettings.Mode.PREVIEW, plain));
        assertTrue(GhostScope.applies(GizmoSettings.Mode.PREVIEW, preview));
        assertFalse(GhostScope.applies(GizmoSettings.Mode.OFF, preview));
        assertFalse(GhostScope.applies(null, preview), "sin el mod no se sabe el modo: como siempre");
        assertFalse(GhostScope.applies(GizmoSettings.Mode.ALWAYS, EditRequest.CONFIRMED));
        assertFalse(GhostScope.applies(GizmoSettings.Mode.ALWAYS, EditRequest.TOOL));
    }

    @Test
    void settingsSurviveBrokenFiles() {
        GizmoSettings settings = new GizmoSettings(GizmoSettings.Mode.PREVIEW, false);
        assertEquals(settings, GizmoSettings.parse(settings.toJson()));
        assertEquals(GizmoSettings.DEFAULT, GizmoSettings.parse("{roto"));
        assertEquals(GizmoSettings.DEFAULT, GizmoSettings.parse(null));
        assertEquals(new GizmoSettings(GizmoSettings.Mode.OFF, true), GizmoSettings.parse("{\"mode\":\"off\"}"));
        assertEquals(GizmoSettings.Mode.ALWAYS, GizmoSettings.parse("{\"mode\":\"nope\",\"wheel\":3}").mode());
        assertEquals(GizmoSettings.Mode.OFF, GizmoSettings.Mode.byOrdinal(9));
        GhostOptions options = new GhostOptions(true, false, true);
        assertEquals(options, GhostOptions.fromBits(options.bits()));
    }

    @Test
    void keysDoNotStealAnyoneElsesKey() {
        GizmoKeys keys = new GizmoKeys();
        int enter = GizmoKeys.KEY_ENTER;
        int backspace = GizmoKeys.KEY_BACKSPACE;
        // De serie: Ctrl+R gira; la R sola no (es de Cobblemon), ni la F sola (cambiar de mano).
        assertEquals(GizmoKeys.Action.ROTATE, keys.match('R', GizmoKeys.MOD_CONTROL, enter, backspace)
                .orElseThrow().action());
        assertTrue(keys.match('R', 0, enter, backspace).isEmpty());
        assertTrue(keys.match('F', 0, enter, backspace).isEmpty());
        assertEquals(GizmoKeys.Action.FLIP, keys.match('F', GizmoKeys.MOD_CONTROL, enter, backspace)
                .orElseThrow().action());
        GizmoKeys.Press shifted = keys.match(GizmoKeys.KEY_UP, GizmoKeys.MOD_SHIFT, enter, backspace).orElseThrow();
        assertEquals(GizmoKeys.Action.FORWARD, shifted.action());
        assertEquals(5, shifted.step());
        assertEquals(GizmoKeys.Action.CONFIRM, keys.match(enter, GizmoKeys.MOD_CONTROL, enter, backspace)
                .orElseThrow().action());
        assertTrue(keys.match(enter, GizmoKeys.MOD_CONTROL, enter, backspace).orElseThrow().control(), "copia");
        assertEquals(GizmoKeys.Action.CANCEL, keys.match(GizmoKeys.KEY_ESCAPE, 0, enter, backspace)
                .orElseThrow().action());
        assertEquals(GizmoKeys.Action.CANCEL, keys.match(backspace, 0, enter, backspace).orElseThrow().action());
        // Cambiada en Controles: la tecla nueva, sola.
        keys.bind(GizmoKeys.Action.ROTATE, 'U');
        assertEquals(GizmoKeys.Action.ROTATE, keys.match('U', 0, enter, backspace).orElseThrow().action());
        assertTrue(keys.match('R', GizmoKeys.MOD_CONTROL, enter, backspace).isEmpty());
        // Sin fantasma solo cuenta pegar.
        assertTrue(keys.matchIdle('V', GizmoKeys.MOD_CONTROL).isPresent());
        assertTrue(keys.matchIdle('V', 0).isEmpty());
        assertTrue(keys.matchIdle('R', GizmoKeys.MOD_CONTROL).isEmpty());
    }

    @Test
    void groundSnapRestsOnTheHighestSupport() {
        // Un objeto de 3×1 (y su columna central, de 2 de alto) flotando sobre un suelo a y = 60, con un escalón a 62.
        long[] oriented = {PackedBlockPlan.pack(-1, 0, 0), PackedBlockPlan.pack(0, 0, 0), PackedBlockPlan.pack(1, 0, 0),
                PackedBlockPlan.pack(0, 1, 0)};
        GroundSnap.Footprint footprint = GroundSnap.footprint(oriented);
        assertEquals(3, footprint.columns());
        assertEquals(2, footprint.height());
        GroundSnap.Terrain terrain = (x, y, z) -> y <= 60 || x == 11 && y <= 62;
        GhostTransform transform = GhostTransform.at(10, 70, 0);
        assertEquals(OptionalInt.of(-7), GroundSnap.rest(footprint, transform, terrain), "lo sostiene el escalón");
        // Medio enterrado en el escalón: sube hasta asomar.
        assertEquals(OptionalInt.of(2), GroundSnap.rest(footprint, GhostTransform.at(10, 61, 0), terrain));
        assertEquals(OptionalInt.empty(), GroundSnap.rest(footprint, transform, (x, y, z) -> false));
    }

    @Test
    void theGizmoHighlightsWhatIsUnderThePointer() {
        GizmoLayout layout = new GizmoLayout(new com.komainu.moxia.shape.Vec3(0.5, 0.5, 0.5), 2.0, false, true,
                false);
        GizmoGeometry.Shape idle = GizmoGeometry.build(layout, GizmoHandle.NONE, GizmoHandle.NONE);
        GizmoGeometry.Shape hover = GizmoGeometry.build(layout, GizmoHandle.MOVE_Y, GizmoHandle.NONE);
        GizmoGeometry.Shape drag = GizmoGeometry.build(layout, GizmoHandle.MOVE_Y, GizmoHandle.RING_Y);
        assertTrue(idle.lines().stream().noneMatch(line -> line.argb() == GizmoGeometry.HIGHLIGHT));
        assertTrue(hover.lines().stream().anyMatch(line -> line.argb() == GizmoGeometry.HIGHLIGHT));
        // Arrastrando el anillo, el anillo se ilumina y lo demás se atenúa.
        assertTrue(drag.lines().stream().filter(line -> line.argb() == GizmoGeometry.HIGHLIGHT).count() > 32);
        assertTrue(drag.lines().stream().anyMatch(line -> (line.argb() >>> 24) < 0xFF));
        // Solo el anillo de Y: el objeto no se tumba.
        long ringSegments = idle.lines().stream().filter(line -> line.width() < 3.0F).count();
        assertEquals(64, ringSegments);
        // Las puntas acaban donde acaba la flecha.
        double farthest = idle.triangles().stream().mapToDouble(triangle -> triangle.c().y()).max().orElseThrow();
        assertEquals(2.5, farthest, 1.0E-9);
    }
}
