package com.komainu.moxia.nature;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NatureSettingsTest {
    private static final Function<String, String> PRESETS = Map.of(
            "mio", "birch 18 -g 1.5 -s 9 -h azalea_leaves",
            "piedra", "spire 4 -a 4 -u 0.3")::get;

    @Test
    void readsSpeciesHeightAndFlags() {
        TreeSettings tree = TreeSettings.parse("dark_oak 20 -g 1.4 -b 0.5 -d 1.2 -w 1.3 -i 15,90 -c 2 -s 77 "
                + "-t dark_oak_log,stripped_dark_oak_log -h no -r mangrove_roots -m grass_block -v 0.3 -p");
        assertEquals(TreeSpecies.DARK_OAK, tree.species());
        assertEquals(20, tree.height());
        assertEquals(1.4, tree.thickness());
        assertEquals(15.0, tree.lean());
        assertEquals(90.0, tree.azimuth());
        assertEquals(77L, tree.seed().orElseThrow());
        assertNull(tree.leafMaterial());
        assertEquals("mangrove_roots", tree.rootMaterial());
        assertEquals("grass_block", tree.mask());
        assertTrue(tree.preview());
        assertEquals(TreeSpecies.WILLOW, TreeSettings.parse("sauce").species());
        assertEquals(TreeSpecies.FANTASY, TreeSettings.parse("fantástico").species());
        assertEquals(TreeSpecies.OAK, TreeSettings.parse("").species());
        assertEquals(30, TreeSettings.parse("30").height());
        assertEquals("oak_log", TreeSettings.parse("oak -r si").trunkMaterial());
        assertNull(TreeSettings.parse("oak -r no").rootMaterial());
        assertThrows(IllegalArgumentException.class, () -> TreeSettings.parse("pino 200"));
        assertThrows(IllegalArgumentException.class, () -> TreeSettings.parse("arbusto"));
        assertThrows(IllegalArgumentException.class, () -> TreeSettings.parse("oak 10 12"));
        assertThrows(IllegalArgumentException.class, () -> TreeSettings.parse("oak -i 90"));
    }

    @Test
    void presetsKeepWhatIsSavedAndTakeWhatIsTyped() {
        TreeSettings tree = TreeSettings.parse("#mio 25 -s 3", PRESETS);
        assertEquals(TreeSpecies.BIRCH, tree.species());
        assertEquals(25, tree.height());
        assertEquals(1.5, tree.thickness());
        assertEquals(3L, tree.seed().orElseThrow());
        assertEquals("azalea_leaves", tree.leafMaterial());
        assertEquals("mio", tree.preset());
        assertEquals(TreeSpecies.OAK, TreeSettings.parse("mio oak", PRESETS).species());
        assertThrows(IllegalArgumentException.class, () -> TreeSettings.parse("#nada", PRESETS));
        RockSettings rock = RockSettings.parse("#piedra 6", PRESETS);
        assertEquals(RockType.SPIRE, rock.type());
        assertEquals(6.0, rock.size());
        assertEquals(4.0, rock.aspect());
        assertEquals("moss_block", rock.moss());
    }

    @Test
    void readsRocks() {
        RockSettings rock = RockSettings.parse("peñasco 9 -u mossy_cobblestone -e 2 -g 0.8 -h 0.4 -b ##Grayscale");
        assertEquals(RockType.CRAG, rock.type());
        assertEquals(9.0, rock.size());
        assertEquals("mossy_cobblestone", rock.moss());
        assertEquals("##Grayscale", rock.paletteMaterial());
        assertEquals(RockSettings.DEFAULT_PALETTE, RockSettings.parse("aguja").paletteMaterial());
        assertEquals(RockType.SPIRE, RockSettings.parse("-t spire").type());
        assertNull(RockSettings.parse("boulder -u 0").moss());
        assertThrows(IllegalArgumentException.class, () -> RockSettings.parse("boulder -t crag"));
        assertThrows(IllegalArgumentException.class, () -> RockSettings.parse("boulder 80"));
    }

    @Test
    void brushVariantsStayInRange() {
        TreeSettings tree = TreeSettings.parse("oak 18");
        SplittableRandom random = new SplittableRandom(1L);
        for (int index = 0; index < 200; index++) {
            TreeSettings varied = tree.vary(0.5, random);
            assertTrue(varied.height() >= 9 && varied.height() <= 27, String.valueOf(varied.height()));
            assertTrue(varied.lean() <= 75.0);
        }
        assertEquals(tree, tree.vary(0.0, random));
        RockSettings rock = RockSettings.parse("crag 6");
        for (int index = 0; index < 200; index++) {
            RockSettings varied = rock.vary(1.0, random);
            assertTrue(varied.size() >= 1.0 && varied.size() <= 12.0);
        }
    }

    /** Cada ejemplo de árboles y rocas de la documentación tiene que leerse sin errores. */
    @Test
    void documentedExamplesParse() throws IOException {
        String documentation = Files.readString(Path.of("docs", "MOD_DOCUMENTATION.md"));
        Matcher matcher = Pattern.compile("^/komaedit (tree|arbol|rock|roca|pincel tree|pincel rock)( .*)?$",
                Pattern.MULTILINE).matcher(documentation);
        int examples = 0;
        while (matcher.find()) {
            String command = matcher.group(1);
            String arguments = matcher.group(2) == null ? "" : matcher.group(2).strip();
            if (arguments.startsWith("<") || arguments.startsWith("[") || arguments.startsWith("save")
                    || arguments.startsWith("list") || arguments.startsWith("delete")
                    || arguments.startsWith("cancel")) {
                continue;
            }
            boolean tree = command.contains("tree") || command.contains("arbol");
            assertDoesNotThrow(() -> {
                if (tree) {
                    TreeSettings.parse(arguments, PRESETS);
                } else {
                    RockSettings.parse(arguments, PRESETS);
                }
            }, () -> "/komaedit " + command + " " + arguments);
            examples++;
        }
        assertTrue(examples >= 10, "solo se encontraron " + examples + " ejemplos");
    }
}
