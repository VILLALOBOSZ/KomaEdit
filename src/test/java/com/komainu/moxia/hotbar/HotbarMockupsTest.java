package com.komainu.moxia.hotbar;

import com.komainu.moxia.hotbar.ui.HotbarDeck;
import com.komainu.moxia.hotbar.ui.HotbarPainter;
import com.komainu.moxia.hotbar.ui.HotbarStripView;
import com.komainu.moxia.hotbar.ui.HotbarTexts;
import com.komainu.moxia.hotbar.ui.HotbarWheelView;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.ui.ImageCanvas;
import com.komainu.moxia.studio.ui.StudioCanvas;
import com.komainu.moxia.studio.ui.StudioImage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maquetas de la tira y del menú circular, pintadas con el mismo código que en el juego ({@link HotbarStripView},
 * {@link HotbarWheelView}) sobre un mundo de fondo con el HUD de Minecraft (sus texturas, si están en el classpath).
 * Solo con {@code KOMAEDIT_STUDIO_MOCKUPS=<carpeta>}; salen en {@code <carpeta>/hotbars}:
 *
 * <pre>KOMAEDIT_STUDIO_MOCKUPS=docs/renders ./gradlew test --tests "*HotbarMockupsTest"</pre>
 */
class HotbarMockupsTest {
    private record Screen(String prefix, int width, int height, int scale) {
    }

    private static final Screen FULL_HD = new Screen("1080p_gui3", 640, 360, 3);
    private static final Screen SMALL = new Screen("854_gui2", 427, 240, 2);

    @Test
    void mockups() throws IOException {
        String target = System.getenv("KOMAEDIT_STUDIO_MOCKUPS");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "KOMAEDIT_STUDIO_MOCKUPS sin definir");
        Path folder = Path.of(target).resolve("hotbars");
        Files.createDirectories(folder);
        HotbarTexts es = HotbarViewsTest.texts("es_es");
        HotbarTexts en = HotbarViewsTest.texts("en_us");

        strip(folder, FULL_HD, "tira_creativo_es", deck(8, 0, HotbarRules.Mode.SET), 2, es);
        strip(folder, FULL_HD, "tira_supervivencia_es", deck(8, 0, HotbarRules.Mode.REORDER), 4, es);
        strip(folder, FULL_HD, "tira_muchas_es", deck(14, 9, HotbarRules.Mode.SET), 9, es);
        strip(folder, FULL_HD, "tira_admin_es", deck(8, 0, HotbarRules.Mode.GIVE), 3, es);
        strip(folder, FULL_HD, "tira_vacia_es", HotbarDeck.EMPTY, -1, es);
        strip(folder, SMALL, "tira_creativo_en", deck(8, 0, HotbarRules.Mode.SET), 1, en);

        wheel(folder, FULL_HD, "menu_es", deck(8, 0, HotbarRules.Mode.SET), 2, 0, "", null,
                HotbarWheelView.Button.NONE, es);
        wheel(folder, FULL_HD, "menu_supervivencia_es", deck(8, 0, HotbarRules.Mode.REORDER), 5, 0, "", null,
                HotbarWheelView.Button.NONE, es);
        wheel(folder, FULL_HD, "menu_paginas_es", deck(20, 3, HotbarRules.Mode.SET), -1, 0, "",
                es.tr("gui.komaedit.hotbar.status.copied", 20, 3412), HotbarWheelView.Button.COPY_ALL, es);
        wheel(folder, FULL_HD, "menu_guardar_es", deck(8, 0, HotbarRules.Mode.SET), -1, 0, "piedra",
                es.tr("gui.komaedit.hotbar.status.confirm_overwrite", "piedra"), HotbarWheelView.Button.SAVE, es);
        wheel(folder, FULL_HD, "menu_vacio_es", HotbarDeck.EMPTY, -1, 0, "", null, HotbarWheelView.Button.NONE, es);
        wheel(folder, SMALL, "menu_en", deck(12, 0, HotbarRules.Mode.SET), 7, 0, "", null,
                HotbarWheelView.Button.NONE, en);
        try (var files = Files.list(folder)) {
            assertTrue(files.count() >= 12);
        }
    }

    // ---- Escenas -----------------------------------------------------------------------------------------------

    private static void strip(Path folder, Screen screen, String name, HotbarDeck deck, int selected,
                              HotbarTexts texts) throws IOException {
        BufferedImage image = new BufferedImage(screen.width() * screen.scale(), screen.height() * screen.scale(),
                BufferedImage.TYPE_INT_ARGB);
        ImageCanvas canvas = new ImageCanvas(image, screen.scale());
        world(canvas, screen.width(), screen.height(), deck);
        HotbarStripView.draw(canvas, HotbarStripView.layout(screen.width(), screen.height(), deck.size(), selected),
                deck, selected, HotbarPainter.ICONS, texts);
        ImageIO.write(image, "png", folder.resolve(screen.prefix() + "_" + name + ".png").toFile());
    }

    private static void wheel(Path folder, Screen screen, String name, HotbarDeck deck, int hovered, int page,
                              String typed, String status, HotbarWheelView.Button button, HotbarTexts texts)
            throws IOException {
        BufferedImage image = new BufferedImage(screen.width() * screen.scale(), screen.height() * screen.scale(),
                BufferedImage.TYPE_INT_ARGB);
        ImageCanvas canvas = new ImageCanvas(image, screen.scale());
        world(canvas, screen.width(), screen.height(), deck);
        HotbarWheelView.Layout layout = HotbarWheelView.layout(screen.width(), screen.height(), deck.size(), page);
        HotbarWheelView.draw(canvas, layout, deck, hovered, button, typed, status, HotbarPainter.ICONS, texts);
        if (hovered >= 0) {
            // El ratón, sobre la barra de la que se ve el contenido.
            Rect tile = layout.tiles().get(hovered - layout.first());
            cursor(canvas, tile.centerX() + 6, tile.centerY() + 4);
        } else if (button != HotbarWheelView.Button.NONE) {
            Rect rect = layout.button(button);
            cursor(canvas, rect.centerX() + 4, rect.centerY() + 2);
        }
        ImageIO.write(image, "png", folder.resolve(screen.prefix() + "_" + name + ".png").toFile());
    }

    /** Barras de verdad: las que haría un constructor (piedra, madera, un tejado, un degradado, herramientas…). */
    static HotbarDeck deck(int size, int active, HotbarRules.Mode mode) {
        String[][] sets = {
                {"piedra", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "stone_brick_stairs",
                        "stone_brick_slab", "polished_andesite", "chiseled_stone_bricks", "andesite", "lantern"},
                {"madera", "spruce_planks", "spruce_log", "stripped_spruce_log", "spruce_stairs", "spruce_slab",
                        "spruce_fence", "spruce_trapdoor", "spruce_door", "glass_pane"},
                {"tejado_pizarra", "deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab",
                        "deepslate_bricks", "cracked_deepslate_tiles", "polished_deepslate", "", "", ""},
                {"degradado_gris", "deepslate", "cobbled_deepslate", "tuff", "andesite", "stone", "calcite",
                        "diorite", "white_concrete", "snow_block"},
                {"herramientas", "wooden_axe", "diamond_pickaxe", "compass", "spyglass", "brush", "shears",
                        "flint_and_steel", "water_bucket", "lava_bucket"},
                {"jardin", "grass_block", "moss_block", "oak_leaves", "azalea", "flowering_azalea", "poppy",
                        "dandelion", "fern", "rooted_dirt"},
                {"desierto", "sand", "sandstone", "smooth_sandstone", "cut_sandstone", "chiseled_sandstone",
                        "sandstone_stairs", "sandstone_slab", "red_sandstone", "terracotta"},
                {"puerto", "oak_planks", "oak_log", "barrel", "chain", "lantern", "dark_prismarine", "prismarine",
                        "sea_lantern", "kelp"},
                {"nether", "nether_bricks", "red_nether_bricks", "blackstone", "polished_blackstone_bricks",
                        "basalt", "magma_block", "crimson_planks", "warped_planks", "soul_lantern"},
                {"castillo_torre", "cobblestone", "mossy_cobblestone", "cobbled_deepslate", "cobblestone_stairs",
                        "iron_bars", "oak_trapdoor", "torch", "", ""},
        };
        List<HotbarDeck.Row> rows = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            String[] set = sets[index % sets.length];
            List<HotbarItem> slots = new ArrayList<>();
            for (int slot = 1; slot < set.length; slot++) {
                boolean tool = index % sets.length == 4;
                slots.add(set[slot].isEmpty() ? HotbarItem.EMPTY
                        : HotbarItem.of(set[slot], tool ? 1 : slot == 1 ? 64 : 64 - slot * 5));
            }
            String rowName = index < sets.length ? set[0] : set[0] + "_" + (index / sets.length + 1);
            rows.add(new HotbarDeck.Row(rowName, slots.getFirst().id(), slots));
        }
        return new HotbarDeck(rows, active, mode, null);
    }

    // ---- Fondo -------------------------------------------------------------------------------------------------

    /** Un mundo pintado y el HUD de Minecraft con la barra en uso puesta. */
    private static void world(StudioCanvas canvas, int width, int height, HotbarDeck deck) {
        int horizon = height * 11 / 20;
        for (int y = 0; y < height; y++) {
            int argb = y < horizon ? mix(0xFF6FA8F0, 0xFFBFDDFB, (double) y / horizon)
                    : mix(0xFF5E9E3C, 0xFF3F6E2A, (double) (y - horizon) / (height - horizon));
            canvas.fill(new Rect(0, y, width, 1), argb);
        }
        for (int x = 0; x < width; x += 4) {
            int hill = (int) (Math.sin(x / 37.0) * 9 + Math.sin(x / 13.0) * 3);
            canvas.fill(new Rect(x, horizon - 12 + hill, 4, 12 - hill), 0xFF4F8A34);
        }
        int wallX = width * 3 / 5;
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 7; column++) {
                int shade = (row + column) % 3 == 0 ? 0xFF8A8A8A : (row * 7 + column) % 5 == 0 ? 0xFF6E7F5C : 0xFF9A9A9A;
                canvas.fill(new Rect(wallX + column * 12, horizon - 20 - row * 12 + 8, 11, 11), shade);
            }
        }
        canvas.fill(new Rect(width / 2 - 4, height / 2, 9, 1), 0xCCFFFFFF);
        canvas.fill(new Rect(width / 2, height / 2 - 4, 1, 9), 0xCCFFFFFF);

        int left = width / 2 - 91;
        sprite(canvas, "hud/heart/container", left, height - 39, 9, 9, 10, 8, 0xFF3A0000);
        sprite(canvas, "hud/heart/full", left, height - 39, 9, 9, 10, 8, 0xFFE02020);
        for (int index = 0; index < 10; index++) {
            int x = width / 2 + 91 - index * 8 - 9;
            sprite(canvas, "hud/food_empty", x, height - 39, 9, 9, 1, 0, 0xFF3A2400);
            sprite(canvas, "hud/food_full", x, height - 39, 9, 9, 1, 0, 0xFFB5651D);
        }
        sprite(canvas, "hud/experience_bar_background", left, height - 29, 182, 5, 1, 0, 0xFF1E3A12);
        sprite(canvas, "hud/hotbar", left, height - 22, 182, 22, 1, 0, 0xB0101010);
        sprite(canvas, "hud/hotbar_selection", left - 1, height - 23, 24, 23, 1, 0, 0x00000000);
        if (deck.active() >= 0) {
            List<HotbarItem> slots = deck.row(deck.active()).slots();
            for (int slot = 0; slot < slots.size(); slot++) {
                HotbarPainter.ICONS.slot(canvas, slots.get(slot), -1, slot, left + 3 + slot * 20, height - 19);
            }
        }
    }

    /**
     * Un sprite del HUD de Minecraft repetido {@code count} veces cada {@code step} píxeles; sin la textura, un
     * recuadro de {@code fallback}.
     */
    private static void sprite(StudioCanvas canvas, String name, int x, int y, int width, int height, int count,
                               int step, int fallback) {
        int[] pixels = texture("assets/minecraft/textures/gui/sprites/" + name + ".png", width, height);
        for (int index = 0; index < count; index++) {
            Rect target = new Rect(x + index * step, y, width, height);
            if (pixels != null) {
                canvas.image(StudioImage.of("hud/" + name, width, height, () -> pixels), target);
            } else if ((fallback >>> 24) != 0) {
                canvas.fill(target, fallback);
            }
        }
    }

    private static int[] texture(String path, int width, int height) {
        try (InputStream stream = HotbarMockupsTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            BufferedImage image = ImageIO.read(stream);
            if (image == null || image.getWidth() < width || image.getHeight() < height) {
                return null;
            }
            return image.getRGB(0, 0, width, height, null, 0, width);
        } catch (IOException exception) {
            return null;
        }
    }

    /** La flecha del ratón de Windows, para ver qué se está señalando. */
    private static void cursor(StudioCanvas canvas, int x, int y) {
        for (int row = 0; row < 11; row++) {
            int length = Math.min(row + 1, 7);
            canvas.fill(new Rect(x, y + row, length, 1), 0xFF000000);
            if (length > 2) {
                canvas.fill(new Rect(x + 1, y + row, length - 2, 1), 0xFFFFFFFF);
            }
        }
    }

    private static int mix(int a, int b, double t) {
        int red = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int green = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int blue = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
