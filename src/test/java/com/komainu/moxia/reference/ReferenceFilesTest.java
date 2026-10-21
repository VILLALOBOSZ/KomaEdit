package com.komainu.moxia.reference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La carpeta {@code config/komaedit/references/}: nombres seguros, lista, lectura reducida y copiar lo arrastrado. */
class ReferenceFilesTest {
    @TempDir
    Path folder;
    @TempDir
    Path elsewhere;

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0xFF000000 | x * 255 / Math.max(1, width - 1) << 16 | y % 256);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @Test
    void namesNeverLeaveTheFolder() {
        assertEquals(folder.resolve("a.png").toAbsolutePath().normalize(), ReferenceFiles.resolve(folder, "a.png"));
        assertEquals(folder.resolve("sub/b.JPG").toAbsolutePath().normalize(),
                ReferenceFiles.resolve(folder, "sub/b.JPG"));
        for (String bad : List.of("../a.png", "/a.png", "a\\b.png", "C:/a.png", "sub/../../a.png", ".worlds/x.png",
                "a.gif", "a.txt", "", "  ", "a\u0001.png", "sub//a.png")) {
            assertThrows(IllegalArgumentException.class, () -> ReferenceFiles.resolve(folder, bad), bad);
        }
    }

    @Test
    void listShowsImagesSortedAndHidesTheWorldsFolder() throws IOException {
        Files.write(folder.resolve("Zeta.png"), png(2, 2));
        Files.write(folder.resolve("alfa.jpg"), new byte[]{1});
        Files.createDirectories(folder.resolve("fachadas"));
        Files.write(folder.resolve("fachadas/castillo.jpeg"), new byte[]{1});
        Files.createDirectories(folder.resolve(ReferenceFiles.WORLDS));
        Files.writeString(folder.resolve(ReferenceFiles.WORLDS).resolve("mundo_x.json"), "{}");
        Files.write(folder.resolve(ReferenceFiles.WORLDS).resolve("oculta.png"), new byte[]{1});
        Files.writeString(folder.resolve("notas.txt"), "nada");
        assertEquals(List.of("alfa.jpg", "fachadas/castillo.jpeg", "Zeta.png"), ReferenceFiles.list(folder));
        assertEquals(List.of(), ReferenceFiles.list(folder.resolve("no-existe")));
        assertEquals(List.of(), ReferenceFiles.list(null));
    }

    @Test
    void readingChecksTheFileAndShrinksBigImages() throws IOException {
        byte[] data = png(300, 120);
        Files.write(folder.resolve("ancha.png"), data);
        assertArrayEquals(data, ReferenceFiles.read(folder, "ancha.png"));
        assertThrows(IllegalArgumentException.class, () -> ReferenceFiles.read(folder, "falta.png"));
        ReferenceFiles.Decoded decoded = ReferenceFiles.decode(data, 100);
        assertEquals(300, decoded.originalWidth());
        assertEquals(120, decoded.originalHeight());
        assertEquals(100, decoded.image().width());
        assertEquals(40, decoded.image().height());
        ReferenceFiles.Decoded full = ReferenceFiles.decode(data, ReferenceFiles.TEXTURE_SIDE);
        assertEquals(300, full.image().width());
        assertEquals(1, ReferenceFiles.subsampling(2_048, 10, 2_048));
        assertEquals(2, ReferenceFiles.subsampling(2_049, 10, 2_048));
        assertEquals(4, ReferenceFiles.subsampling(100, 8_000, 2_048));
        assertThrows(IllegalArgumentException.class, () -> ReferenceFiles.decode(new byte[]{1, 2, 3}, 64));
    }

    @Test
    void droppedFilesAreCopiedWithAFreeName() throws IOException {
        Path source = elsewhere.resolve("Mi foto (1).png");
        Files.write(source, png(4, 4));
        assertEquals("Mi_foto_1_.png", ReferenceFiles.importFile(folder, source));
        assertEquals("Mi_foto_1__2.png", ReferenceFiles.importFile(folder, source));
        assertTrue(Files.isRegularFile(folder.resolve("Mi_foto_1__2.png")));
        // Lo que ya está en la carpeta no se duplica.
        assertEquals("Mi_foto_1_.png", ReferenceFiles.importFile(folder, folder.resolve("Mi_foto_1_.png")));
        // Un archivo que dice ser PNG pero no lo es, o de otro tipo, no entra.
        Path fake = elsewhere.resolve("falsa.png");
        Files.writeString(fake, "no soy una imagen");
        assertThrows(IllegalArgumentException.class, () -> ReferenceFiles.importFile(folder, fake));
        Path text = elsewhere.resolve("notas.txt");
        Files.writeString(text, "hola");
        assertThrows(IllegalArgumentException.class, () -> ReferenceFiles.importFile(folder, text));
        assertThrows(IllegalArgumentException.class,
                () -> ReferenceFiles.importFile(folder, elsewhere.resolve("no-existe.png")));
    }

    @Test
    void safeNames() {
        assertEquals("fachada_norte", ReferenceFiles.safeStem("fachada norte"));
        assertEquals("ñandú_2", ReferenceFiles.safeStem("ñandú 2"));
        assertEquals("imagen", ReferenceFiles.safeStem("   "));
        assertEquals("imagen", ReferenceFiles.safeStem(""));
        assertEquals(64, ReferenceFiles.safeStem("x".repeat(300)).length());
    }
}
