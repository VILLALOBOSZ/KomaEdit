package com.komainu.moxia.studio.ui;

import com.komainu.moxia.hotbar.HotbarItem;
import com.komainu.moxia.hotbar.HotbarRules;
import com.komainu.moxia.hotbar.ui.HotbarDeck;
import com.komainu.moxia.studio.tools.HotbarCard;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ToolValues;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La tarjeta Barras rápidas del Studio: la orden de Crear barra, la lista (clic para usar), guardar la que se lleva
 * (sustituir pide un segundo clic), borrar con dos clics y las maquetas ({@code KOMAEDIT_STUDIO_MOCKUPS}).
 */
class HotbarCardTest {
    private static final ToolContext SELECTED = new ToolContext(true, ToolCatalog.permissionNodes(), true,
            10L * 6 * 12, 0, false);

    static HotbarDeck deck(HotbarRules.Mode mode) {
        String[][] sets = {
                {"piedra", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "stone_brick_stairs",
                        "stone_brick_slab", "polished_andesite", "chiseled_stone_bricks", "andesite", "lantern"},
                {"madera", "spruce_planks", "spruce_log", "stripped_spruce_log", "spruce_stairs", "spruce_slab",
                        "spruce_fence", "spruce_trapdoor", "spruce_door", "glass_pane"},
                {"tejado_pizarra", "deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab",
                        "deepslate_bricks", "cracked_deepslate_tiles", "polished_deepslate", "", "", ""},
                {"degradado_gris", "deepslate", "cobbled_deepslate", "tuff", "andesite", "stone", "calcite",
                        "diorite", "white_concrete", "snow_block"},
        };
        List<HotbarDeck.Row> rows = new ArrayList<>();
        for (String[] set : sets) {
            List<HotbarItem> slots = new ArrayList<>();
            for (int slot = 1; slot < set.length; slot++) {
                slots.add(set[slot].isEmpty() ? HotbarItem.EMPTY : HotbarItem.of(set[slot], 64));
            }
            rows.add(new HotbarDeck.Row(set[0], slots.getFirst().id(), slots));
        }
        return new HotbarDeck(rows, 0, mode, null);
    }

    private static StudioDriver driver(String language, int width, int height, HotbarDeck deck) {
        StudioDriver driver = new StudioDriver(language, width, height);
        driver.host.context = SELECTED;
        driver.host.info = new ToolsInfo(true, new int[]{10, 6, 12}, List.of(), Map.of(), Map.of(),
                ToolsInfo.Symmetry.OFF, null, List.of("minecraft:plains"), null);
        driver.host.hotbarDeck = deck;
        driver.view.selectStage(StudioStage.TOOLS, driver.host);
        driver.view.tools().select(ToolCatalog.byId("hotbars"), driver.host);
        driver.host.status = "";
        driver.render();
        return driver;
    }

    @Test
    void createSendsTheHotbarOrderForEachSource() {
        ToolCard card = ToolCatalog.byId("hotbars");
        ToolValues values = card.defaults().with("name", "Mar");
        assertEquals("komaedit hotbar palette mar ##Magma -l", card.command(values, false));
        assertEquals("komaedit hotbar gradient mar deepslate calcite -l",
                card.command(values.with("source", "gradient"), false));
        assertEquals("komaedit hotbar selection mar -l -f",
                card.command(values.with("source", "selection").with("overwrite", true), false));
        assertEquals("komaedit hotbar stamp mar natural", card.command(values.with("source", "stamp")
                .with("load", false), false));
        assertEquals("komaedit hotbar roof mar deepslate_tiles -l", card.command(values.with("source", "roof"), false));
        assertTrue(card.problems(values.with("name", "con espacio"), SELECTED).stream()
                .anyMatch(problem -> problem.id().equals("hotbar_name")));
        ToolContext nothing = new ToolContext(true, ToolCatalog.permissionNodes(), false, 0, 0, false);
        assertTrue(card.blocked(values.with("source", "selection"), nothing), "desde la selección pide selección");
        assertFalse(card.blocked(values, nothing));
        assertEquals("mar", HotbarCard.name(values.with("name", "#Mar")));
    }

    @Test
    void clickingAHotbarUsesItThroughItsPacket() {
        StudioDriver driver = driver("es_es", 960, 540, deck(HotbarRules.Mode.SET));
        assertTrue(driver.texts().stream().anyMatch("Tus barras"::equalsIgnoreCase), "la lista tiene su título");
        assertTrue(driver.shows("piedra · en uso"), "la que está en uso se marca");
        driver.click("madera");
        assertEquals(List.of("APPLY madera"), driver.host.hotbarActions);
        assertTrue(driver.host.sent.isEmpty(), "nada por el chat");
        driver.reset("madera");
        assertEquals("EXPORT madera", driver.host.hotbarActions.getLast(), "clic derecho: su código");
    }

    @Test
    void savingOverAnExistingNameNeedsASecondClick() {
        StudioDriver driver = driver("es_es", 960, 540, deck(HotbarRules.Mode.SET));
        driver.click("Guardar la que llevas");
        assertEquals(List.of("SAVE nueva"), driver.host.hotbarActions, "un nombre nuevo se guarda a la primera");
        driver.view.tools().select(ToolCatalog.byId("hotbars"), driver.host);
        driver.host.hotbarActions.clear();
        // Con el nombre de una que ya existe: el primer clic avisa y el segundo la sustituye.
        ToolCard card = ToolCatalog.byId("hotbars");
        driver.view.tools().setValues(card, driver.view.tools().values(card).with("name", "madera"));
        driver.render();
        driver.click("Guardar la que llevas");
        assertTrue(driver.host.hotbarActions.isEmpty());
        assertTrue(driver.host.status.contains("otra vez"), driver.host.status);
        driver.render();
        driver.click("Guardar la que llevas");
        assertEquals(List.of("SAVE_OVERWRITE madera"), driver.host.hotbarActions);
    }

    @Test
    void survivalSaysThatOnlyWhatYouCarryMoves() {
        StudioDriver driver = driver("es_es", 960, 540, deck(HotbarRules.Mode.REORDER));
        assertTrue(driver.shows("nunca crea objetos"), "avisa de que en supervivencia solo se reordena");
        StudioDriver missing = driver("en_us", 960, 540, null);
        assertTrue(missing.shows("send saved hotbars"), "sin catálogo del servidor lo dice");
    }

    @Test
    void mockups() throws IOException {
        String target = System.getenv("KOMAEDIT_STUDIO_MOCKUPS");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "KOMAEDIT_STUDIO_MOCKUPS sin definir");
        Path folder = Path.of(target).resolve("hotbars");
        Files.createDirectories(folder);
        scene(folder, "1080p_gui3_studio_es.png", "es_es", 640, 360, 3, deck(HotbarRules.Mode.SET));
        scene(folder, "1080p_gui2_studio_es.png", "es_es", 960, 540, 2, deck(HotbarRules.Mode.SET));
        scene(folder, "1080p_gui2_studio_supervivencia_en.png", "en_us", 960, 540, 2,
                deck(HotbarRules.Mode.REORDER));
        scene(folder, "854_gui2_studio_es.png", "es_es", 427, 240, 2, deck(HotbarRules.Mode.SET));
        scene(folder, "1080p_gui3_studio_vacia_es.png", "es_es", 640, 360, 3, HotbarDeck.EMPTY);
    }

    private static void scene(Path folder, String file, String language, int width, int height, int scale,
                              HotbarDeck deck) throws IOException {
        StudioDriver driver = driver(language, width, height, deck);
        BufferedImage image = driver.paint(scale, ui -> StudioMockupsTest.world(ui, width, height));
        ImageIO.write(image, "png", folder.resolve(file).toFile());
    }
}
