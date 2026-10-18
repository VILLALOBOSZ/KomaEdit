package com.komainu.moxia.image;

import com.komainu.moxia.shape.smooth.SmoothMaterials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Archivos de la carpeta del servidor, configuración y paletas de serie. */
class ImageSourcesTest {
    @Test
    void filesNeverLeaveTheImagesFolder(@TempDir Path folder) throws IOException {
        Path images = Files.createDirectories(folder.resolve("images"));
        Files.write(folder.resolve("secreto.png"), new byte[]{1, 2, 3});
        byte[] png = TestImages.encode(TestImages.icon(5), "png");
        Files.createDirectories(images.resolve("vidrieras"));
        Files.write(images.resolve("vidrieras/rosa.png"), png);
        Files.write(images.resolve("logo.PNG"), png);
        assertArrayEquals(png, ImageFiles.read(images, "vidrieras/rosa.png", 1024 * 1024));
        assertArrayEquals(png, ImageFiles.read(images, "logo.PNG", 1024 * 1024));
        for (String name : new String[]{"../secreto.png", "vidrieras/../../secreto.png", "/secreto.png",
                "C:/Windows/x.png", "vidrieras\\rosa.png", "rosa.txt", "rosa", "", "./logo.PNG", "vidrieras//rosa.png"}) {
            assertThrows(IllegalArgumentException.class, () -> ImageFiles.read(images, name, 1024 * 1024), name);
        }
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> ImageFiles.read(images, "nada.png", 1024 * 1024));
        assertTrue(missing.getMessage().contains("No existe"), missing.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ImageFiles.read(images, "logo.PNG", png.length - 1L));
        assertEquals(List.of("logo.PNG", "vidrieras/rosa.png"), ImageFiles.list(images));
    }

    @Test
    void settingsDefaultToNoDownloads() {
        ImageSettings parsed = ImageSettings.parse("{}");
        assertEquals(ImageSettings.DEFAULT, parsed);
        assertFalse(parsed.allowUrls());
        assertTrue(parsed.urlsRequireAdmin());
        assertTrue(parsed.allowedHosts().isEmpty());
        assertEquals(parsed, ImageSettings.parse(parsed.toJson()));
    }

    @Test
    void settingsAreValidated() {
        ImageSettings settings = ImageSettings.parse("""
                {"allowUrls": true, "allowedHosts": [" Imagenes.Ejemplo.com ", "*.cdn.org", ""],
                 "maxImageBytes": 2048, "timeoutSeconds": 3}""");
        assertTrue(settings.allowUrls());
        assertEquals(List.of("imagenes.ejemplo.com", "*.cdn.org"), settings.allowedHosts());
        assertEquals(2048, settings.maxImageBytes());
        assertEquals(3, settings.timeoutSeconds());
        assertEquals(ImageSettings.DEFAULT.maxPixels(), settings.maxPixels());
        for (String json : new String[]{"{\"maxImageBytes\": 10}", "{\"timeoutSeconds\": 0}",
                "{\"timeoutSeconds\": 600}", "{\"maxPixels\": 0}", "{\"maxBlocksPerSide\": 0}",
                "{\"allowedHosts\": [\"http://x.com\"]}", "{\"format\": 2}"}) {
            assertThrows(RuntimeException.class, () -> ImageSettings.parse(json), json);
        }
    }

    @Test
    void presetPalettesUseTheTextureTable() {
        Map<String, Integer> colors = SmoothMaterials.textureColors();
        assertEquals(16, ImagePalettes.preset(ImagePalettes.Preset.GLASS).size());
        assertEquals(16, ImagePalettes.preset(ImagePalettes.Preset.PANES).size());
        assertEquals(16, ImagePalettes.preset(ImagePalettes.Preset.WOOL).size());
        assertEquals(16, ImagePalettes.preset(ImagePalettes.Preset.CONCRETE).size());
        assertEquals(17, ImagePalettes.preset(ImagePalettes.Preset.TERRACOTTA).size());
        BlockPalette solid = ImagePalettes.preset(ImagePalettes.Preset.SOLID);
        assertTrue(solid.size() > 150, "bloques enteros: " + solid.size());
        for (ImagePalettes.Preset preset : ImagePalettes.Preset.values()) {
            for (BlockPalette.Entry entry : ImagePalettes.preset(preset).entries()) {
                assertEquals(colors.get(entry.key()), entry.rgb(), entry.key());
            }
        }
        List<String> ids = solid.entries().stream().map(BlockPalette.Entry::key).toList();
        assertTrue(ids.contains("minecraft:white_concrete"));
        assertTrue(ids.contains("minecraft:dead_brain_coral_block"));
        assertFalse(ids.contains("minecraft:brain_coral_block"), "el coral vivo muere fuera del agua");
        assertFalse(ids.stream().anyMatch(id -> id.contains("glass") || id.equals("minecraft:sand")
                || id.equals("minecraft:red_sand") || id.equals("minecraft:gravel") || id.contains("powder")
                || id.contains("suspicious")), "ni cristal ni bloques que caen");
        assertEquals(0x8E2121, colors.get("minecraft:red_concrete"));
    }

    @Test
    void presetNamesAndAliases() {
        assertEquals(ImagePalettes.Preset.GLASS, ImagePalettes.Preset.find("vidrio"));
        assertEquals(ImagePalettes.Preset.CONCRETE, ImagePalettes.Preset.find("Hormigón"));
        assertEquals(ImagePalettes.Preset.PANES, ImagePalettes.Preset.find("paneles"));
        assertEquals(ImagePalettes.Preset.SOLID, ImagePalettes.Preset.find("todos"));
        assertNull(ImagePalettes.Preset.find("##Grayscale"));
        assertTrue(ImagePalettes.isPane("minecraft:red_stained_glass_pane"));
        assertFalse(ImagePalettes.isPane("minecraft:red_stained_glass"));
    }
}
