package com.komainu.moxia.share;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareExportTest {
    private static final ShareTarget ALICE = ShareFixtures.player(ShareFixtures.ALICE, "VILLALOBOSZ");

    @TempDir
    Path root;

    @Test
    void readsThingsNameAndFile() {
        ShareExport request = ShareExport.parse("paleta:rocas plano:servidor/torre -n \"Casas de Villa\" -f");
        assertEquals(List.of("paleta:rocas", "plano:servidor/torre"), request.refs());
        assertEquals("Casas de Villa", request.name());
        assertTrue(request.toFile());
        assertFalse(ShareExport.parse("ruido:*").toFile());
        ShareCodecTest.assertMessage("Di qué", () -> ShareExport.parse("-f"));
        ShareCodecTest.assertMessage("desconocida", () -> ShareExport.parse("paleta:a -x"));
        ShareCodecTest.assertMessage("-n", () -> ShareExport.parse("paleta:a -n"));
        ShareCodecTest.assertMessage("comillas", () -> ShareExport.parse("paleta:a -n \"sin cerrar"));
    }

    @Test
    void buildsOnePackWithoutRepeats() {
        ShareKinds.Registry kinds = ShareFixtures.kinds(root);
        ShareFixtures.store(root, ALICE, "palettes").put("rocas", "stone;andesite", false);
        ShareFixtures.store(root, ALICE, "palettes").put("arena", "sand", false);
        ShareFixtures.store(root, ALICE, "noises").put("suave", "Perlin()", false);
        SharePack pack = ShareExport.parse("paleta:* palette:rocas ruido:suave").build(ALICE, kinds::find, "1.9.1",
                5L, ShareLimits.DEFAULT);
        assertEquals(3, pack.items().size());
        assertEquals("Paquete de VILLALOBOSZ", pack.name());
        assertEquals("VILLALOBOSZ", pack.author());
        assertEquals("1.9.1", pack.modVersion());
        assertEquals("2 paletas y 1 ruido", ShareExport.summary(pack.items(), kinds::find));
        assertEquals("rocas", ShareExport.parse("paleta:rocas").build(ALICE, kinds::find, "", 0L,
                ShareLimits.DEFAULT).name());
    }

    @Test
    void explainsWhatIsMissing() {
        ShareKinds.Registry kinds = ShareFixtures.kinds(root);
        ShareCodecTest.assertMessage("no está entre tus paletas", () -> ShareExport.parse("paleta:nada")
                .build(ALICE, kinds::find, "", 0L, ShareLimits.DEFAULT));
        ShareCodecTest.assertMessage("No tienes ruidos", () -> ShareExport.parse("ruido:*")
                .build(ALICE, kinds::find, "", 0L, ShareLimits.DEFAULT));
        ShareCodecTest.assertMessage("No se puede compartir", () -> ShareExport.parse("coche:rojo")
                .build(ALICE, kinds::find, "", 0L, ShareLimits.DEFAULT));
        ShareCodecTest.assertMessage("tipo:nombre", () -> ShareExport.parse("rocas")
                .build(ALICE, kinds::find, "", 0L, ShareLimits.DEFAULT));
    }

    @Test
    void respectsTheItemLimit() {
        ShareKinds.Registry kinds = ShareFixtures.kinds(root);
        for (int index = 0; index < 5; index++) {
            ShareFixtures.store(root, ALICE, "noises").put("r" + index, "Perlin()", false);
        }
        ShareLimits three = new ShareLimits(32_000, 1024 * 1024, 16L * 1024 * 1024, 3, 64L * 1024 * 1024, 30, 60,
                true);
        ShareCodecTest.assertMessage("como mucho 3", () -> ShareExport.parse("ruido:*").build(ALICE, kinds::find,
                "", 0L, three));
    }
}
