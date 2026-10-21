package com.komainu.moxia.studio.tools;

import com.komainu.moxia.loft.LoftSettings;
import com.komainu.moxia.nature.RockSettings;
import com.komainu.moxia.nature.RockType;
import com.komainu.moxia.nature.TreeSettings;
import com.komainu.moxia.nature.TreeSpecies;
import com.komainu.moxia.permission.PermissionNodes;
import com.komainu.moxia.roof.RoofSettings;
import com.komainu.moxia.roof.RoofType;
import com.komainu.moxia.symmetry.SymmetryMode;
import com.komainu.moxia.studio.ui.StudioIcons;
import com.komainu.moxia.text.TextRequest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cada tarjeta manda la misma orden que escribiría el jugador: aquí se arma la orden y se pasa por el analizador del
 * propio servidor, para que lo que el Studio construye sea lo que dice la tarjeta.
 */
class ToolCardsTest {
    private static final ToolContext EVERYTHING = new ToolContext(true, ToolCatalog.permissionNodes(), true, 1_000L,
            4, false);

    private static ToolCard card(String id) {
        ToolCard card = ToolCatalog.byId(id);
        assertNotNull(card, id);
        return card;
    }

    private static String arguments(String command, String prefix) {
        assertTrue(command.startsWith(prefix), command);
        return command.substring(prefix.length()).strip();
    }

    @Test
    void theCatalogHasEveryToolOnceWithUniqueSettings() {
        Set<String> ids = new HashSet<>();
        for (ToolCard card : ToolCatalog.ALL) {
            assertTrue(ids.add(card.id()), "repetida: " + card.id());
            Set<String> params = new HashSet<>();
            card.params().forEach(param -> assertTrue(params.add(param.id()), card.id() + "." + param.id()));
            assertFalse(card.command(card.defaults(), false).startsWith("/"), "sin barra: " + card.id());
            assertTrue(card.command(card.defaults(), false).startsWith("komaedit "), card.id());
        }
        assertEquals(List.of("tree", "rock", "roof", "loft", "text", "stamp", "paint", "ramp", "river", "erosion",
                "touchup", "analyze", "symmetry", "capabilities"), ToolCatalog.ALL.stream().map(ToolCard::id).toList());
        for (ToolCategory category : ToolCategory.values()) {
            assertFalse(ToolCatalog.category(category).isEmpty(), category.id());
        }
    }

    /** Una tarjeta nueva es una línea del catálogo: su grupo, ella y su icono, que no se repite. */
    @Test
    void everyCardIsRegisteredOnceInACategoryWithItsOwnIcon() {
        Set<StudioIcons.Icon> icons = new HashSet<>();
        int previous = -1;
        for (ToolCard card : ToolCatalog.ALL) {
            assertTrue(icons.add(ToolCatalog.icon(card)), card.id() + " repite icono");
            assertTrue(ToolCatalog.categoryOf(card).ordinal() >= previous, card.id() + ": salen por categoría");
            previous = ToolCatalog.categoryOf(card).ordinal();
        }
        assertEquals(ToolCatalog.ENTRIES.size(), ToolCatalog.ALL.size());
        assertNotEquals(StudioIcons.Icon.TOOLS, ToolCatalog.icon(card("touchup")));
    }

    @Test
    void everyPermissionTheCardsAskForExists() {
        for (String node : ToolCatalog.permissionNodes()) {
            assertNotNull(PermissionNodes.find(node), "nodo desconocido: " + node);
        }
    }

    @Test
    void defaultsBuildTheShortestCommand() {
        assertEquals("komaedit tree oak", card("tree").command(card("tree").defaults(), false));
        assertEquals("komaedit rock boulder", card("rock").command(card("rock").defaults(), false));
        assertEquals("komaedit roof gable spruce_planks", card("roof").command(card("roof").defaults(), false));
        assertEquals("komaedit loft white_wool", card("loft").command(card("loft").defaults(), false));
        assertEquals("komaedit pincel heightmap montana 12 9 sumar auto",
                card("stamp").command(card("stamp").defaults(), false));
        assertEquals("komaedit terreno rampa plano 5 6 natural", card("ramp").command(card("ramp").defaults(), false));
        assertEquals("komaedit terreno rio natural", card("river").command(card("river").defaults(), false));
        assertEquals("komaedit terreno erosion hidraulica natural",
                card("erosion").command(card("erosion").defaults(), false));
        assertEquals("komaedit pincel paint hierba_piedra 8 pendiente 60",
                card("paint").command(card("paint").defaults(), false));
        assertEquals("komaedit text \"KomaEdit\" stone", card("text").command(card("text").defaults(), false));
        assertEquals("komaedit analyze", card("analyze").command(card("analyze").defaults(), false));
        assertEquals("komaedit retocar nieve 2", card("touchup").command(card("touchup").defaults(), false));
        assertEquals("komaedit symmetry on x", card("symmetry").command(card("symmetry").defaults(), false));
    }

    @Test
    void previewAddsTheFlagOnlyWhereTheCommandHasIt() {
        assertEquals("komaedit tree oak -v", card("tree").command(card("tree").defaults(), true));
        assertEquals("komaedit roof gable spruce_planks -v", card("roof").command(card("roof").defaults(), true));
        // En el texto -v es «de pie»: la vista previa va con la bandera larga.
        assertTrue(card("text").command(card("text").defaults(), true).endsWith(" --preview"));
        for (String id : List.of("stamp", "analyze", "symmetry", "capabilities")) {
            assertFalse(card(id).previewable(card(id).defaults()), id);
            assertEquals(card(id).command(card(id).defaults(), false), card(id).command(card(id).defaults(), true));
        }
        ToolValues biome = card("touchup").defaults().with("touch", "bioma");
        assertFalse(card("touchup").previewable(biome), "el bioma no tiene bloques que enseñar");
        assertEquals(card("touchup").command(biome, false), card("touchup").command(biome, true));
        // Con el interruptor en el pincel no hay vista previa universal: el pincel tiene la suya.
        assertFalse(card("tree").previewable(card("tree").defaults().with("use", "brush")));
        assertTrue(card("paint").previewable(card("paint").defaults().with("use", "selection")));
    }

    @Test
    void theTreeCommandIsReadBackByTheServerWithTheSameSettings() {
        ToolCard tree = card("tree");
        ToolValues values = tree.defaults().with("species", "palm").with("height", 24).with("thickness", 130)
                .with("crown", 140).with("lean", 25).with("heading", 90).with("curvature", 150).with("seed", 42)
                .with("trunk", "jungle_log,stripped_jungle_log").with("leaves", "jungle_leaves")
                .with("roots", false).with("variation", 30).with("mask", "air,short_grass");
        String command = tree.command(values, false);
        assertEquals("komaedit tree palm 24 -g 1.3 -w 1.4 -i 25,90 -c 1.5 -s 42 -t jungle_log,stripped_jungle_log"
                + " -h jungle_leaves -r no -m air,short_grass -x 0.3", command);
        TreeSettings parsed = TreeSettings.parse(arguments(command, "komaedit tree"));
        assertEquals(TreeSpecies.PALM, parsed.species());
        assertEquals(24, parsed.height());
        assertEquals(1.3, parsed.thickness(), 1e-9);
        assertEquals(1.4, parsed.width(), 1e-9);
        assertEquals(25.0, parsed.lean(), 1e-9);
        assertEquals(90.0, parsed.azimuth(), 1e-9);
        assertEquals(42L, parsed.seed().getAsLong());
        assertNull(parsed.rootMaterial());
        assertEquals(0.3, parsed.variation(), 1e-9);
        // Y el preset guardado con esos ajustes vuelve a la tarjeta igual.
        assertEquals(values, tree.parsePreset(arguments(command, "komaedit tree")));
    }

    @Test
    void aHeadingWithoutLeanUsesTheLeanOfTheSpecies() {
        ToolCard tree = card("tree");
        ToolValues palm = tree.defaults().with("species", "palm").with("heading", 180);
        TreeSettings parsed = TreeSettings.parse(arguments(tree.command(palm, false), "komaedit tree"));
        assertEquals(TreeSpecies.PALM.form().defaultLean(), parsed.lean(), 0.5);
        assertEquals(180.0, parsed.azimuth(), 1e-9);
        assertEquals(Integer.toString(TreeSpecies.PALM.defaultHeight()), tree.autoHint("height", palm));
    }

    @Test
    void theTreeBrushAndPresetUseTheSameSettings() {
        ToolCard tree = card("tree");
        ToolValues values = tree.defaults().with("species", "spruce").with("height", 30);
        assertEquals("komaedit pincel tree spruce 30", tree.command(values.with("use", "brush"), false),
                "con el interruptor en el pincel, los mismos ajustes");
        assertEquals("komaedit tree spruce 30", tree.command(values, false));
        assertEquals(ToolTarget.HAND, tree.target(values.with("use", "brush")));
        assertEquals(List.of(TreeCard.BRUSH_NODE), tree.permissions(values.with("use", "brush")));
        ToolAction save = tree.actions().getFirst();
        assertTrue(save.needsName());
        assertEquals("komaedit tree save bosque spruce 30 -f",
                ToolCard.withName(save.command().apply(values), "bosque"));
    }

    @Test
    void theRockCommandIsReadBackByTheServer() {
        ToolCard rock = card("rock");
        ToolValues values = rock.defaults().with("type", "crag").with("size", 105).with("elongation", 200)
                .with("moss", 50).with("lean", 12).with("palette", "granite,diorite,calcite").with("sink", 40);
        String command = rock.command(values, false);
        RockSettings parsed = RockSettings.parse(arguments(command, "komaedit rock"));
        assertEquals(RockType.CRAG, parsed.type());
        assertEquals(10.5, parsed.size(), 1e-9);
        assertEquals(2.0, parsed.elongation(), 1e-9);
        assertEquals(0.5, parsed.mossAmount(), 1e-9);
        assertEquals(12.0, parsed.lean(), 1e-9);
        assertEquals(0.4, parsed.sink(), 1e-9);
        assertEquals("granite,diorite,calcite", parsed.palette());
        assertEquals(values, rock.parsePreset(arguments(command, "komaedit rock")));
        assertEquals("7", rock.autoHint("size", rock.defaults().with("type", "crag")));
    }

    @Test
    void theRoofCommandIsReadBackByTheServerAndHidesWhatTheTypeDoesNotUse() {
        ToolCard roof = card("roof");
        ToolValues values = roof.defaults().with("type", "spire").with("material", "deepslate_tiles")
                .with("sides", 8).with("height", 12).with("slope", "2:1").with("dormers", 3).with("side", "s");
        String command = roof.command(values, false);
        // Las buhardillas y el lado bajo no valen en una aguja: no se mandan.
        assertEquals("komaedit roof spire deepslate_tiles -p 2:1 -lados 8 -h 12", command);
        RoofSettings parsed = RoofSettings.parse(arguments(command, "komaedit roof"));
        assertEquals(RoofType.SPIRE, parsed.type());
        assertEquals(8, parsed.sides());
        assertEquals(12.0, parsed.height(), 1e-9);
        assertEquals(2.0, parsed.slope(), 1e-9);

        ToolValues shed = roof.defaults().with("type", "shed").with("side", "e").with("overhang", 2)
                .with("dormers", 2).with("window", "glass").with("open", true).with("replace", true)
                .with("detect", true);
        String previewed = roof.command(shed, true);
        assertTrue(previewed.endsWith(" -v"), previewed);
        RoofSettings house = RoofSettings.parse(arguments(previewed.substring(0, previewed.length() - 3),
                "komaedit roof"));
        assertEquals(RoofSettings.Side.EAST, house.lowSide());
        assertEquals(2, house.overhang());
        assertEquals(2, house.dormers());
        assertFalse(house.closedGables());
        assertTrue(house.replace());
        assertTrue(house.detectWalls());
        assertTrue(roof.command(shed, false).contains("-ventana glass"));
    }

    @Test
    void theLoftCommandIsReadBackByTheServer() {
        ToolCard loft = card("loft");
        ToolValues values = loft.defaults().with("material", "red_wool").with("interpolation", "catenaria")
                .with("sag", 4).with("thickness", 2).with("profile", "SlabsAndStairs").with("closed", true)
                .with("count", 3);
        String command = loft.command(values, false);
        LoftSettings parsed = LoftSettings.parse(arguments(command, "komaedit loft"), name -> false);
        assertEquals(LoftSettings.Interpolation.CATENARY, parsed.interpolation());
        assertEquals(4.0, parsed.sag(), 1e-9);
        assertEquals(2, parsed.thickness());
        assertTrue(parsed.closed());
        assertEquals(3, parsed.split());
        assertEquals("red_wool", parsed.material());

        ToolValues named = loft.defaults().with("curves", "borde1 borde2");
        LoftSettings curves = LoftSettings.parse(arguments(loft.command(named, false), "komaedit loft"),
                name -> name.startsWith("borde"));
        assertEquals(List.of("borde1", "borde2"), curves.curves());
        assertFalse(loft.command(named, false).contains("-n"));
        assertTrue(loft.validate(loft.defaults().with("curves", "solo")).stream()
                .anyMatch(problem -> problem.id().equals("curve_count")));
    }

    @Test
    void theTextCommandQuotesTheTextAndIsReadBackByTheServer() {
        ToolCard text = card("text");
        ToolValues values = text.defaults().with("text", "Plaza \"Mayor\"\\nAlcázara").with("material", "quartz_block")
                .with("font", "fina").with("size", 12).with("thickness", 2).with("border", 1)
                .with("border_material", "black_concrete").with("orientation", "standing").with("align", "izquierda")
                .with("spacing", 15).with("line_spacing", 12).with("profile", "SlabsAndStairs").with("mask", "air");
        String command = text.command(values, false);
        TextRequest parsed = TextRequest.parse(arguments(command, "komaedit text"));
        assertEquals("Plaza \"Mayor\"\\nAlcázara", parsed.text());
        assertEquals("quartz_block", parsed.material());
        assertEquals("fina", parsed.font());
        assertEquals(12.0, parsed.size(), 1e-9);
        assertEquals(2, parsed.depth());
        assertEquals(1.0, parsed.border(), 1e-9);
        assertEquals("black_concrete", parsed.borderMaterial());
        assertEquals(TextRequest.Orientation.STANDING, parsed.orientation());
        assertEquals(1.5, parsed.tracking(), 1e-9);
        assertEquals(1.2, parsed.lineSpacing(), 1e-9);
        assertEquals("air", parsed.mask());
        assertEquals(TextRequest.Orientation.LYING, TextRequest.parse(arguments(text.command(
                text.defaults().with("orientation", "flat"), false), "komaedit text")).orientation());
    }

    @Test
    void terrainCommandsFollowThePositionalOrderOfTheServer() {
        ToolCard river = card("river");
        assertEquals("komaedit terreno canon 5 20 mesa -w 2.5 -f seco",
                river.command(river.defaults().with("kind", "canon").with("depth", 20).with("palette", "mesa")
                        .with("walls", 25).with("fluid", "seco").with("level", 3), false));
        assertEquals("komaedit terreno arroyo 4 natural",
                river.command(river.defaults().with("kind", "arroyo").with("width", 4), false));
        ToolCard erosion = card("erosion");
        assertEquals("komaedit terreno erosion termica 40 1.5 nevado -a 35 -s 7",
                erosion.command(erosion.defaults().with("kind", "termica").with("strength", 15)
                        .with("palette", "nevado").with("angle", 35).with("seed", 7), false));
        ToolCard ramp = card("ramp");
        assertEquals("komaedit terreno rampa cono 7 desierto -t 0.8 -u -v",
                ramp.command(ramp.defaults().with("profile", "cono").with("width", 7).with("palette", "desierto")
                        .with("talus", 8).with("smooth", true), true));
        ToolCard stamp = card("stamp");
        assertEquals("komaedit pincel heightmap mi_isla 32 40 restar playa -i 50 -r azar -x 20 -s 77 -k -c 0.35"
                        + " -m sand -p",
                stamp.command(stamp.defaults().with("custom", "mi_isla").with("radius", 32).with("height", 40)
                        .with("mode", "restar").with("palette", "playa").with("intensity", 50)
                        .with("rotation", "azar").with("variation", 20).with("seed", 77).with("lock_seed", true)
                        .with("neutral", 35).with("mask", "sand").with("brush_preview", true), false));
        // En automático, la altura natural del sello para ese radio: una colina baja y un pico alto.
        assertEquals("komaedit pincel heightmap colina 20 7 sumar auto",
                stamp.command(stamp.defaults().with("stamp", "colina").with("radius", 20), false));
        assertEquals("komaedit pincel heightmap pico 20 19 sumar auto",
                stamp.command(stamp.defaults().with("stamp", "pico").with("radius", 20), false));
    }

    /**
     * Cada modo de Retocar manda los mismos argumentos que la orden de antes (la de la tarjeta suelta), detrás de
     * {@code retocar <modo>}: el servidor los lee con el mismo código.
     */
    @Test
    void utilityCommandsMatchTheDocumentedSyntax() {
        ToolCard touchUp = card("touchup");
        ToolValues snow = touchUp.defaults();
        assertEquals("komaedit retocar nieve 3 -r 4 -s 4 -p", touchUp.command(snow.with("layers", 3)
                .with("drift", 4).with("shade", 4).with("plants", true), false));
        ToolValues flood = touchUp.defaults().with("touch", "inundar");
        assertEquals("komaedit retocar inundar ~-2 -b", touchUp.command(flood.with("level", "feet")
                .with("offset", -2).with("blocks_only", true), false));
        assertEquals("komaedit retocar inundar 62 -v", touchUp.command(flood.with("level", "fixed"), true));
        ToolValues drain = touchUp.defaults().with("touch", "drenar");
        assertEquals("komaedit retocar drenar all -k -m stone", touchUp.command(drain.with("fluid", "all")
                .with("keep_plants", true).with("mask", "stone"), false));
        ToolValues cherry = touchUp.defaults().with("touch", "bioma").with("biome", "minecraft:cherry_grove")
                .with("vertical", true).with("radius", 12);
        assertEquals("komaedit retocar bioma cherry_grove -v", touchUp.command(cherry, false));
        assertEquals("komaedit pincel biome cherry_grove 12 -v", touchUp.command(cherry.with("use", "brush"), false),
                "el bioma con el pincel es el pincel de biomas de siempre");
        assertEquals("komaedit retocar bioma terralith:moonlight_grove",
                touchUp.command(touchUp.defaults().with("touch", "bioma").with("biome", "terralith:moonlight_grove"),
                        false));
        ToolValues gravity = touchUp.defaults().with("touch", "gravedad");
        assertEquals("komaedit retocar gravedad -a -v", touchUp.command(gravity.with("unsupported", true), true));
        assertEquals("komaedit retocar desinfestar", touchUp.command(touchUp.defaults().with("touch", "desinfestar"),
                false));
        assertEquals("komaedit retocar hojas off", touchUp.command(touchUp.defaults().with("touch", "hojas")
                .with("leaves", "off"), false));
        assertEquals("komaedit retocar encerar on", touchUp.command(touchUp.defaults().with("touch", "encerar"),
                false));
        ToolCard analyze = card("analyze");
        assertEquals("komaedit analyze -n 25 -m #existing",
                analyze.command(analyze.defaults().with("lines", 25).with("mask", "#existing"), false));
    }

    @Test
    void symmetryBuildsTheModeTheServerReads() {
        SymmetryCard symmetry = (SymmetryCard) card("symmetry");
        ToolValues radial = symmetry.defaults().with("mode", "radial").with("arms", 8).with("mirror_y", true);
        assertEquals("komaedit symmetry on radial 8 y", symmetry.command(radial, false));
        SymmetryMode parsed = SymmetryMode.parse(SymmetryCard.modeArgument(radial), SymmetryMode.DEFAULT_ARMS);
        assertTrue(parsed.radial());
        assertEquals(8, parsed.arms());
        assertTrue(parsed.mirrorY());
        assertEquals(List.of(SymmetryCard.ROTATIONAL), symmetry.permissions(radial));
        assertEquals(List.of(SymmetryCard.REFLECTIONAL), symmetry.permissions(symmetry.defaults().with("mode", "xz")));
        // El estado del servidor vuelve a la tarjeta: «radial 6 y» es radial, 6 brazos y espejo en Y.
        ToolValues back = symmetry.fromServer(symmetry.defaults(), parsed.withArms(6).id(), 6);
        assertEquals("radial", back.choice("mode"));
        assertEquals(6, back.number("arms"));
        assertTrue(back.toggle("mirror_y"));
        assertEquals("xzy", symmetry.fromServer(symmetry.defaults(), SymmetryMode.parse("xzy", 4).id(), 4)
                .choice("mode"));
        assertEquals("komaedit symmetry worldedit on", SymmetryCard.worldEditCommand(true));
    }

    @Test
    void capabilityCommandsUseTheOptionsThatAcceptEveryValue() {
        assertEquals("komaedit capability replace on",
                CapabilityCard.toggleCommand(com.komainu.moxia.capability.BuildCapability.REPLACE, true));
        assertEquals("komaedit capability option limit 512",
                CapabilityCard.distanceCommand(com.komainu.moxia.capability.BuildCapability.EXTRUDE, 512));
        assertEquals("komaedit capability option reach 32",
                CapabilityCard.distanceCommand(com.komainu.moxia.capability.BuildCapability.REACH, 32));
        ToolCard capabilities = card("capabilities");
        assertEquals("komaedit capability option flyspeed 2.5",
                CapabilityCard.flySpeedCommand(capabilities.defaults().with("flyspeed", 25)));
        assertThrows(IllegalArgumentException.class,
                () -> CapabilityCard.distanceCommand(com.komainu.moxia.capability.BuildCapability.TWEAK, 3));
    }

    @Test
    void permissionsDependOnTheChosenVariant() {
        ToolCard river = card("river");
        assertEquals(List.of(TerrainCards.RIVER_NODE), river.permissions(river.defaults()));
        assertEquals(List.of(TerrainCards.CANYON_NODE), river.permissions(river.defaults().with("kind", "barranco")));
        ToolCard erosion = card("erosion");
        assertEquals(List.of(TerrainCards.HYDRAULIC_NODE, TerrainCards.THERMAL_NODE),
                erosion.permissions(erosion.defaults().with("kind", "completa")));

        ToolContext riverOnly = new ToolContext(true, Set.of(TerrainCards.RIVER_NODE), true, 10L, 4, false);
        assertNull(river.missingPermission(river.defaults(), riverOnly));
        assertEquals(TerrainCards.CANYON_NODE, river.missingPermission(river.defaults().with("kind", "canon"),
                riverOnly));
        assertFalse(river.locked(riverOnly), "con el río ya se puede usar la tarjeta");
        assertTrue(erosion.locked(riverOnly), "sin ningún nodo de erosión la tarjeta sale apagada");
        assertTrue(erosion.blocked(erosion.defaults(), riverOnly));
        assertEquals("permission", erosion.problems(erosion.defaults(), riverOnly).getFirst().id());
    }

    @Test
    void withoutAnAnswerFromTheServerNothingIsLocked() {
        for (ToolCard card : ToolCatalog.ALL) {
            assertFalse(card.locked(ToolContext.UNKNOWN), card.id());
            assertNull(card.missingPermission(card.defaults(), ToolContext.UNKNOWN), card.id());
            assertNull(card.requirement(card.defaults(), ToolContext.UNKNOWN), card.id());
        }
    }

    @Test
    void requirementsSayWhatIsMissingInTheWorld() {
        ToolContext nothing = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0L, 0, false);
        assertEquals("no_selection", card("roof").requirement(card("roof").defaults(), nothing).id());
        assertEquals("no_path_or_selection", card("river").requirement(card("river").defaults(), nothing).id());
        assertEquals("loft_points", card("loft").requirement(card("loft").defaults(), nothing).id());
        assertNull(card("tree").requirement(card("tree").defaults(), nothing), "el árbol vale donde se mira");
        assertNull(card("loft").requirement(card("loft").defaults(), EVERYTHING));
        assertFalse(card("roof").blocked(card("roof").defaults(), EVERYTHING));
    }

    @Test
    void invalidValuesBlockTheBuildWithAReason() {
        ToolCard text = card("text");
        assertTrue(text.blocked(text.defaults().with("text", "  "), EVERYTHING));
        assertTrue(text.problems(text.defaults().with("text", "Hola §cRojo"), EVERYTHING).stream()
                .anyMatch(problem -> problem.id().equals("illegal_character")));
        assertTrue(text.problems(text.defaults().with("mask", "air stone"), EVERYTHING).stream()
                .anyMatch(problem -> problem.id().equals("spaces")));
        ToolCard erosion = card("erosion");
        assertTrue(erosion.blocked(erosion.defaults().with("iterations", 100), EVERYTHING), "más de 64 gotas");
        assertFalse(erosion.blocked(erosion.defaults().with("kind", "termica").with("iterations", 100), EVERYTHING));
        assertTrue(erosion.blocked(erosion.defaults().with("kind", "termica").with("strength", 30), EVERYTHING));
        ToolCard touchUp = card("touchup");
        assertTrue(touchUp.blocked(touchUp.defaults().with("touch", "bioma").with("biome", "Not A Biome"),
                EVERYTHING));
        assertFalse(touchUp.blocked(touchUp.defaults().with("biome", "Not A Biome"), EVERYTHING),
                "el bioma solo cuenta en su modo");
        ToolCard stamp = card("stamp");
        assertTrue(stamp.blocked(stamp.defaults().with("custom", "../secreto"), EVERYTHING));
    }

    @Test
    void numbersStayInsideTheirRangeAndAutoOnlyWhereAllowed() {
        ToolValues tree = card("tree").defaults();
        assertEquals(TreeSettings.MAX_HEIGHT, tree.with("height", 500).number("height"));
        assertNull(tree.with("height", null).number("height"));
        assertEquals(100, tree.with("thickness", null).number("thickness"), "sin auto vuelve al de serie");
        assertEquals("1.25", tree.with("thickness", 125).formatted("thickness"));
        assertThrows(IllegalArgumentException.class, () -> tree.with("species", "baobob"));
        assertThrows(IllegalArgumentException.class, () -> tree.with("nope", 1));
    }

    @Test
    void everyPresetLeavesACompleteKnownState() {
        for (ToolCard card : ToolCatalog.ALL) {
            for (ToolPreset preset : card.presets()) {
                ToolValues fresh = card.apply(card.defaults(), preset);
                for (String id : preset.values().keySet()) {
                    assertNotNull(fresh.param(id), card.id() + "." + preset.id());
                }
                // La orden con el preset la sigue entendiendo la tarjeta (no lanza).
                assertNotNull(card.command(fresh, false));
                assertTrue(card.matches(fresh, preset), card.id() + "." + preset.id() + " sale marcado");
                // Venga de donde venga (otro preset, ajustes tocados a mano), la forma queda igual.
                for (ToolPreset before : card.presets()) {
                    ToolValues chained = card.apply(card.apply(card.defaults(), before), preset);
                    for (ToolParam param : card.params()) {
                        // Lo que es elección del jugador (el tipo de roca de «Peñasco grande») sí se queda.
                        boolean chosen = card.keptByPresets(param) && before.values().containsKey(param.id())
                                && !preset.values().containsKey(param.id());
                        if (!chosen) {
                            assertEquals(fresh.changed().get(param.id()), chained.changed().get(param.id()),
                                    card.id() + ": " + before.id() + " → " + preset.id() + " deja " + param.id());
                        }
                    }
                }
            }
        }
        ToolCard tree = card("tree");
        ToolValues young = tree.apply(tree.defaults().with("species", "birch").with("seed", 9)
                .with("leaves", "cherry_leaves").with("density", 0).with("lean", 30), tree.presets().getFirst());
        assertEquals("birch", young.choice("species"), "la especie se queda");
        assertEquals(9, young.number("seed"), "la semilla también");
        assertEquals("cherry_leaves", young.block("leaves"), "y los materiales");
        assertEquals(100, young.number("density"), "la copa vuelve a la de serie");
        assertNull(young.number("lean"), "y la inclinación, a la de la especie");
    }

    @Test
    void bareThenYoungGivesTheLeavesBack() {
        ToolCard tree = card("tree");
        ToolPreset bare = preset(tree, "bare");
        ToolValues values = tree.defaults();
        for (String id : List.of("old", "windswept", "bare", "young")) {
            values = tree.apply(values, preset(tree, id));
        }
        assertEquals(100, values.number("density"));
        String command = tree.command(values, false);
        assertFalse(command.contains("-d "), command);
        TreeSettings settings = TreeSettings.parse(arguments(command, "komaedit tree "));
        assertNotNull(settings.leafMaterial(), "con hojas");
        // Y a mano: de 0 se vuelve a subir paso a paso.
        ToolParam.Number density = (ToolParam.Number) tree.defaults().param("density");
        ToolValues bareValues = tree.apply(tree.defaults(), bare);
        assertEquals(0, bareValues.number("density"));
        int up = density.range().stepped(bareValues.number("density"), 1, false);
        assertEquals(10, up);
        assertEquals(10, bareValues.with("density", up).number("density"));
    }

    @Test
    void aStampPresetDropsTheTypedStampButKeepsHowItPlaces() {
        ToolCard stamp = card("stamp");
        ToolValues values = stamp.apply(stamp.defaults().with("custom", "mi_sello").with("brush_preview", true),
                preset(stamp, "dunes"));
        assertEquals("", values.text("custom"));
        assertTrue(stamp.command(values, false).startsWith("komaedit pincel heightmap dunas "));
        assertTrue(values.toggle("brush_preview"), "la vista previa en cada clic no es la forma: se queda");
        ToolCard roof = card("roof");
        assertTrue(roof.apply(roof.defaults().with("replace", true), preset(roof, "steep")).toggle("replace"));
        // Un interruptor que algún preset sí pone es parte de la forma: los demás lo devuelven al de serie.
        ToolCard snow = card("touchup");
        assertFalse(snow.apply(snow.apply(snow.defaults(), preset(snow, "drifts")), preset(snow, "dusting"))
                .toggle("plants"));
        // Y lo que no es la forma, tampoco el interruptor de cómo se usa: un preset no cambia el pincel por la
        // selección.
        ToolCard paint = card("paint");
        assertEquals("selection", paint.apply(paint.defaults().with("use", "selection"), preset(paint, "paint_peaks"))
                .choice("use"));
    }

    private static ToolPreset preset(ToolCard card, String id) {
        return card.presets().stream().filter(preset -> preset.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theSearchFindsToolsByNameCommandAndSynonym() {
        List<ToolCard> all = ToolCatalog.ALL;
        assertEquals("tree", search(all, "arbol").getFirst().id());
        assertEquals("tree", search(all, "árbol").getFirst().id());
        assertEquals("river", search(all, "canon").getFirst().id());
        assertEquals("roof", search(all, "tejado").getFirst().id());
        assertEquals("touchup", search(all, "bioma").getFirst().id());
        assertEquals("capabilities", search(all, "extruir").getFirst().id());
        assertEquals("touchup", search(all, "nieve").getFirst().id());
        assertEquals("touchup", search(all, "drenar").getFirst().id());
        assertEquals("paint", search(all, "superficie").getFirst().id(), "Pintar superficie es ahora Pintar");
        assertTrue(search(all, "agua").stream().map(ToolCard::id).toList().containsAll(List.of("touchup", "river")));
        assertTrue(search(all, "zzzz").isEmpty());
        assertEquals(all.size(), search(all, "").size());
    }

    private static List<ToolCard> search(List<ToolCard> cards, String query) {
        return ToolCatalog.search(cards, query, card -> card.id(), card -> "", ToolCategory::id);
    }
}
