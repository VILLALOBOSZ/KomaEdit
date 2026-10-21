package com.komainu.moxia.studio.tools;

import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.brush.ez.EzBrushType;
import com.komainu.moxia.brush.ez.ZoneCommand;
import com.komainu.moxia.brush.paint.PaintConfig;
import com.komainu.moxia.studio.StudioPreferences;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las herramientas que se unieron hacen lo mismo que las de antes, con los mismos ajustes de las dos maneras (pincel
 * y selección), y lo guardado con las tarjetas de antes sigue cargando en las nuevas.
 */
class ToolConsolidationTest {
    private static ToolCard card(String id) {
        return ToolCatalog.byId(id);
    }

    @Test
    void theOldIdsLeadToTheCardThatJoinsThem() {
        assertSame(card("paint"), card("paintbrush"));
        for (String old : List.of("snow", "drain", "waterlog", "gravity", "biome")) {
            assertSame(card("touchup"), card(old), old);
        }
        assertNull(ToolCatalog.byId("nada"));
        assertEquals("tree", ToolMigration.id("tree"), "lo que no se unió se queda igual");
    }

    /** «Pintar superficie» de antes es el modo Natural sobre la selección: la misma superficie por reglas. */
    @Test
    void theNaturalModeIsTheOldSurfacePaint() {
        ToolCard paint = card("paint");
        ToolValues surface = paint.defaults().with("use", "selection").with("gradient", "natural")
                .with("palette", "pradera").with("mask", "grass_block");
        // Antes: komaedit terreno pintar pradera -m grass_block (-v).
        assertEquals("komaedit pintar pradera natural -m grass_block -v", paint.command(surface, true));
        assertEquals(List.of(PaintCard.SURFACE_NODE), paint.permissions(surface), "el mismo permiso de antes");
        assertEquals(ToolTarget.SELECTION, paint.target(surface));
        assertEquals("komaedit pintar pradera natural -m grass_block -e",
                paint.actions(surface).getFirst().command().apply(surface), "Estimar, como antes");
        assertTrue(paint.actions(paint.defaults()).isEmpty(), "el pincel no estima");
        // Con el pincel, la misma paleta de superficie en cada clic.
        assertEquals("komaedit pincel paint pradera 8 natural -m grass_block",
                paint.command(surface.with("use", "brush"), false));
        // Solo se ven los ajustes que usa la superficie natural.
        assertFalse(surface.param("strength").visible(surface));
        assertFalse(surface.param("stamp").visible(surface));
        assertTrue(surface.param("palette").visible(surface));
        assertFalse(paint.defaults().param("palette").visible(paint.defaults()));
    }

    /** Los mismos ajustes del pincel sobre la selección: sin radio, espaciado ni semilla bloqueada. */
    @Test
    void theSwitchKeepsThePaintSettingsOnTheSelection() {
        ToolCard paint = card("paint");
        ToolValues brush = paint.defaults().with("paint_palette", "cumbres").with("gradient", "altura")
                .with("strength", 75).with("seed", 42).with("radius", 14).with("lock_seed", true)
                .with("height_low", 60).with("height_high", 140).with("layers", 2).with("mask", "grass_block");
        String brushCommand = paint.command(brush, false);
        assertEquals("komaedit pincel paint cumbres 14 altura 75 -s 42 -k -y 60..140 -d 2 -m grass_block",
                brushCommand);
        ToolValues selection = brush.with("use", "selection");
        String selectionCommand = paint.command(selection, false);
        assertEquals("komaedit pintar cumbres altura 75 -s 42 -y 60..140 -d 2 -m grass_block", selectionCommand);
        // El servidor lee lo mismo de las dos órdenes, salvo el radio y la semilla bloqueada, que son del pincel.
        PaintConfig fromBrush = PaintConfig.parse(options(brushCommand, "komaedit pincel paint "));
        PaintConfig fromSelection = PaintConfig.parse(options(selectionCommand, "komaedit pintar "), true);
        assertEquals(fromBrush.palette(), fromSelection.palette());
        assertEquals(fromBrush.gradient(), fromSelection.gradient());
        assertEquals(fromBrush.strength(), fromSelection.strength(), 1e-9);
        assertEquals(fromBrush.seed(), fromSelection.seed());
        assertEquals(fromBrush.heightLow(), fromSelection.heightLow(), 1e-9);
        assertEquals(fromBrush.layers(), fromSelection.layers());
        assertEquals(fromBrush.mask(), fromSelection.mask());
        assertEquals(PaintConfig.DEFAULT_RADIUS, fromSelection.radius(), "sin radio en la selección");
        // La huella solo cuenta sobre la selección con el modo Huella, que la estira hasta cubrirla.
        assertFalse(selection.param("stamp").visible(selection));
        assertTrue(selection.with("gradient", "huella").param("stamp").visible(selection.with("gradient", "huella")));
        assertFalse(selection.param("radius").visible(selection));
        assertFalse(selection.param("dab_spacing").visible(selection));
    }

    private static BrushOptions options(String command, String prefix) {
        assertTrue(command.startsWith(prefix), command);
        return BrushOptions.parse(command.substring(prefix.length()), PaintConfig.SWITCHES, PaintConfig.VALUED);
    }

    @Test
    void retouchingWithTheBrushRepeatsTheSameCommandAroundTheClick() {
        ToolCard touchUp = card("touchup");
        ToolValues snow = touchUp.defaults().with("layers", 3).with("drift", 4).with("plants", true);
        String selection = touchUp.command(snow, false);
        assertEquals("komaedit retocar nieve 3 -r 4 -p", selection);
        String brush = touchUp.command(snow.with("use", "brush").with("radius", 8), false);
        assertEquals("komaedit pincel retocar 8 nieve 3 -r 4 -p", brush);
        assertEquals(ToolTarget.HAND, touchUp.target(snow.with("use", "brush")));
        assertEquals(List.of(TouchUpCard.ZONE_NODE, TouchUpCard.SNOW_NODE),
                touchUp.permissions(snow.with("use", "brush")));
        assertEquals(List.of(TouchUpCard.SNOW_NODE), touchUp.permissions(snow));
        // Cada modo de supervivencia pide el permiso de su tweak de antes.
        assertEquals(List.of("komaedit.build.tweak.wax"), touchUp.permissions(touchUp.defaults().with("touch",
                "encerar")));
        assertEquals("gui.komaedit.tools.touchup.build.drenar", touchUp.buildKey(touchUp.defaults().with("touch",
                "drenar")), "el botón dice lo que hace el modo");
        assertEquals(ToolCard.BRUSH_BUILD_KEY, touchUp.buildKey(snow.with("use", "brush")));
        // Los presets son los del modo elegido.
        assertEquals(List.of("dusting", "drifts", "peaks"), touchUp.presets(snow).stream().map(ToolPreset::id)
                .toList());
        assertEquals(List.of("dry_all"), touchUp.presets(touchUp.defaults().with("touch", "drenar")).stream()
                .map(ToolPreset::id).toList());
    }

    @Test
    void erodingAndStampingWorkBothWaysWithTheSameSettings() {
        ToolCard erosion = card("erosion");
        ToolValues thermal = erosion.defaults().with("kind", "termica").with("angle", 35).with("seed", 7);
        assertEquals("komaedit terreno erosion termica natural -a 35 -s 7", erosion.command(thermal, false));
        assertEquals("komaedit pincel zona 12 terreno erosion termica natural -a 35 -s 7",
                erosion.command(thermal.with("use", "brush").with("radius", 12), true),
                "el pincel repite la misma orden y no lleva vista previa universal");
        ZoneCommand zone = ZoneCommand.parse("12 terreno erosion termica natural -a 35 -s 7");
        assertEquals(12, zone.radius());
        assertEquals("komaedit terreno erosion termica natural -a 35 -s 7", zone.full());

        ToolCard stamp = card("stamp");
        ToolValues mountain = stamp.defaults().with("stamp", "cordillera").with("mode", "maximo").with("seed", 9);
        assertEquals("komaedit pincel heightmap cordillera 12 9 maximo auto -s 9", stamp.command(mountain, false));
        assertEquals("komaedit relieve cordillera maximo auto -s 9 -v",
                stamp.command(mountain.with("use", "selection"), true),
                "sobre la selección la altura natural sale de su tamaño");
        assertEquals("komaedit relieve cordillera 30 maximo auto -s 9",
                stamp.command(mountain.with("use", "selection").with("height", 30), false));
        assertEquals("gui.komaedit.tools.stamp.build", stamp.buildKey(mountain.with("use", "selection")));
    }

    @Test
    void theZoneBrushOnlyTakesCommandsThatRetouchWhatIsThere() {
        assertEquals("retocar nieve 2", ZoneCommand.parse("6 /komaedit retocar nieve 2").command());
        assertEquals(ZoneCommand.DEFAULT_RADIUS, ZoneCommand.parse("retocar drenar").radius());
        assertEquals("snow 2", ZoneCommand.parse("4 snow 2").command(), "los nombres de antes también valen");
        for (String refused : List.of("6 tree oak", "6 pincel zona 4 retocar nieve", "6 terreno rampa", "6",
                "40 retocar nieve")) {
            try {
                ZoneCommand.parse(refused);
                throw new AssertionError("no debería valer: " + refused);
            } catch (IllegalArgumentException expected) {
                assertFalse(expected.getMessage().isBlank());
            }
        }
        assertEquals(12, ZoneCommand.parse("6 retocar nieve").halfHeight(), "como poco, doce bloques arriba y abajo");
        // El modo Natural del pincel de pintura es este pincel, con la orden de pintar la selección.
        BrushOptions natural = BrushOptions.parse("pradera 10 natural -m grass_block", PaintConfig.SWITCHES,
                PaintConfig.VALUED);
        assertTrue(PaintConfig.natural(natural));
        assertEquals("10 pintar pradera natural -m grass_block", PaintConfig.naturalZone(natural));
        assertEquals("8 pintar natural natural", PaintConfig.naturalZone(BrushOptions.parse("natural",
                PaintConfig.SWITCHES, PaintConfig.VALUED)));
        assertFalse(PaintConfig.natural(BrushOptions.parse("hierba_piedra 8 pendiente", PaintConfig.SWITCHES,
                PaintConfig.VALUED)));
        // Desde el Studio, pasar de pendiente a Natural cambia el pincel de la mano sin dar otro.
        assertTrue(EzBrushType.PAINT.sameCard(EzBrushType.ZONE));
        assertTrue(EzBrushType.ZONE.sameCard(EzBrushType.PAINT));
        assertFalse(EzBrushType.TREE.sameCard(EzBrushType.ZONE));
    }

    /** Un {@code studio.json} de antes (formato 3) con las tarjetas sueltas carga en las nuevas. */
    @Test
    void savedSettingsOfTheOldCardsLoadIntoTheNewOnes() {
        String json = """
                {"format": 3, "tool": "drain", "toolFavorites": ["snow", "tree", "waterlog"],
                 "toolRecents": ["drain", "paintbrush", "snow", "paint"],
                 "expanded": ["tools:more:snow", "tools:more:tree"],
                 "scroll": {"tool:paintbrush": 40, "tool:tree": 12},
                 "toolValues": {
                   "paintbrush": {"paint_palette": "cumbres", "gradient": "altura", "radius": 14, "mask": "#solid"},
                   "paint": {"palette": "pradera", "mask": "grass_block"},
                   "snow": {"layers": 4, "radius": 6, "plants": true},
                   "drain": {"mode": "lava", "keep_plants": true, "mask": "stone"},
                   "waterlog": {"level": "fixed", "level_y": 70},
                   "gravity": {"unsupported": true},
                   "biome": {"biome": "minecraft:cherry_grove", "radius": 12},
                   "tree": {"species": "spruce"}
                 }}
                """;
        StudioPreferences preferences = StudioPreferences.fromJson(json);
        assertEquals("touchup", preferences.tool(), "la última herramienta era Drenar: ahora Retocar");
        assertTrue(preferences.isToolFavorite("touchup"));
        assertTrue(preferences.isToolFavorite("tree"));
        assertFalse(preferences.isToolFavorite("snow"));
        assertEquals(List.of("touchup", "paint"), preferences.toolRecents(), "sin repetir la tarjeta que las reúne");
        assertTrue(preferences.expanded("tools:more:touchup"));
        assertTrue(preferences.expanded("tools:more:tree"));
        assertEquals(40, preferences.scroll("tool:paint"));

        ToolValues paint = card("paint").defaults().restore(preferences.toolValues("paint"));
        assertEquals("cumbres", paint.choice("paint_palette"));
        assertEquals("altura", paint.choice("gradient"), "lo de Pintar terreno sigue igual");
        assertEquals(14, paint.number("radius"));
        assertEquals("pradera", paint.choice("palette"), "la paleta de Pintar superficie, para el modo Natural");
        assertEquals("#solid", paint.text("mask"));

        ToolValues touchUp = card("touchup").defaults().restore(preferences.toolValues("touchup"));
        assertEquals("drenar", touchUp.choice("touch"), "el modo de la última que se usó");
        assertEquals("lava", touchUp.choice("fluid"), "«mode» de Drenar es ahora «fluid»");
        assertTrue(touchUp.toggle("keep_plants"));
        assertEquals("stone", touchUp.text("mask"), "la máscara de la última usada");
        assertEquals(4, touchUp.number("layers"));
        assertEquals(6, touchUp.number("drift"), "el radio de la nieve es ahora «suavizar en cuestas»");
        assertTrue(touchUp.toggle("plants"));
        assertEquals("fixed", touchUp.choice("level"));
        assertEquals(70, touchUp.number("level_y"));
        assertTrue(touchUp.toggle("unsupported"));
        assertEquals("minecraft:cherry_grove", touchUp.choice("biome"));
        assertEquals(12, touchUp.number("radius"), "el radio del pincel de biomas es el del pincel");
        assertEquals("spruce", card("tree").defaults().restore(preferences.toolValues("tree")).choice("species"));
        // Y se vuelve a guardar en el formato nuevo, que ya no se migra.
        StudioPreferences again = StudioPreferences.fromJson(preferences.toJson());
        assertEquals(preferences.toolValues("touchup"), again.toolValues("touchup"));
        assertEquals("touchup", again.tool());
    }

    @Test
    void theLastOldSurfacePaintOpensPaintOnTheSelectionInNaturalMode() {
        Map<String, Map<String, Object>> stored = new LinkedHashMap<>();
        stored.put("paint", Map.of("palette", "desierto"));
        Map<String, Map<String, Object>> upgraded = ToolMigration.toolValues(stored, 3, "paint");
        ToolValues paint = card("paint").defaults().restore(upgraded.get("paint"));
        assertEquals("natural", paint.choice("gradient"));
        assertEquals("selection", paint.choice("use"));
        assertEquals("desierto", paint.choice("palette"));
        assertEquals("komaedit pintar desierto natural", card("paint").command(paint, false),
                "lo mismo que mandaba Pintar superficie");
        assertSame(stored, ToolMigration.toolValues(stored, ToolMigration.FORMAT, "paint"),
                "un archivo nuevo se lee tal cual");
    }
}
