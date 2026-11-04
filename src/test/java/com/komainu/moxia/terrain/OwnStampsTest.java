package com.komainu.moxia.terrain;

import com.komainu.moxia.share.SharePack;
import com.komainu.moxia.share.ShareTarget;
import com.komainu.moxia.share.StampKind;
import com.komainu.moxia.terrain.stamp.StampCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnStampsTest {
    private static final String ANA = "11111111-1111-1111-1111-111111111111";
    private static final String LUIS = "22222222-2222-2222-2222-222222222222";

    private static StampCapture.Result captured(String name, int peak) {
        StampCapture.Columns columns = new StampCapture.Columns(48, 40);
        for (int z = 0; z < 40; z++) {
            for (int x = 0; x < 48; x++) {
                columns.set(x, z, 30 + (int) Math.round(Math.max(0.0, peak - Math.hypot(x - 24, z - 20))), false,
                        "minecraft:grass_block");
            }
        }
        return StampCapture.build(name, columns, null, () -> {
        });
    }

    private static OwnStamp meta(String name, String author, String id, long created, StampCapture.Result result) {
        return new OwnStamp(name, author, id, "Una loma de prueba", created, 48, 40, result.above(), result.below(),
                result.category(), false, "minecraft:overworld 0 64 0");
    }

    private static OwnStamps.Entry save(OwnStamps stamps, String name, String author, String id, long created) {
        StampCapture.Result result = captured(name, 14);
        return stamps.save(meta(name, author, id, created, result), result.stamp(), false, id, false,
                OwnStamps.Limits.DEFAULT, false);
    }

    @Test
    void savedStampsAreListedNewestFirstAndTheBrushLoadsThemByName(@TempDir Path folder) throws IOException {
        OwnStamps stamps = new OwnStamps(new StampCatalog(folder));
        save(stamps, "loma_vieja", "Ana", ANA, 1_000L);
        save(stamps, "loma_nueva", "Ana", ANA, 2_000L);
        List<OwnStamps.Entry> entries = stamps.list();
        assertEquals(List.of("loma_nueva", "loma_vieja"), entries.stream().map(OwnStamps.Entry::name).toList());
        OwnStamps.Entry entry = entries.getFirst();
        assertEquals("Ana", entry.meta().author());
        assertEquals("Una loma de prueba", entry.meta().description());
        assertEquals(48, entry.meta().sizeX());
        assertEquals(64, entry.hash().length());
        assertTrue(Files.isRegularFile(folder.resolve("loma_nueva.png")));
        assertTrue(Files.isRegularFile(folder.resolve("loma_nueva.json")));
        // El pincel lo lee con el catálogo de la carpeta, como cualquier PNG propio.
        HeightStamp stamp = stamps.catalog().load("loma_nueva");
        assertEquals(48, stamp.width());
        // Su altura natural es la de verdad y su paleta la de su categoría.
        assertNotNull(OwnStampIndex.get("loma_nueva"));
        assertEquals(Math.round(entry.meta().above() * 12 / 24.0), HeightmapBrush.naturalHeight("loma_nueva", 12));
        assertEquals(entry.meta().category().palette(), HeightmapBrush.paletteFor("loma_nueva", null));
    }

    @Test
    void onlyTheAuthorOrAnAdministratorDeletesOrDescribes(@TempDir Path folder) {
        OwnStamps stamps = new OwnStamps(new StampCatalog(folder));
        save(stamps, "de_ana", "Ana", ANA, 1L);
        assertThrows(IllegalArgumentException.class, () -> stamps.delete("de_ana", LUIS, false));
        assertThrows(IllegalArgumentException.class, () -> stamps.describe("de_ana", "otra", LUIS, false));
        assertEquals("Otra descripción", stamps.describe("de_ana", "Otra descripción", ANA, false).meta()
                .description());
        stamps.delete("de_ana", LUIS, true);
        assertNull(stamps.find("de_ana"));
        assertFalse(Files.exists(folder.resolve("de_ana.png")));
        assertFalse(Files.exists(folder.resolve("de_ana.json")));
    }

    /**
     * Cada sello leído (de 1 a 4 MB) se quedaba en memoria aunque se borrara: capturar y borrar con nombres distintos la
     * llenaba hasta cerrar el juego. Borrar uno (o quitar su PNG a mano) lo suelta.
     */
    @Test
    void aDeletedStampDoesNotStayInMemory(@TempDir Path folder) throws IOException {
        StampCatalog catalog = new StampCatalog(folder);
        OwnStamps stamps = new OwnStamps(catalog);
        for (int round = 0; round < 5; round++) {
            save(stamps, "prueba_" + round, "Ana", ANA, round);
            stamps.delete("prueba_" + round, ANA, false);
        }
        assertEquals(0, catalog.cached(), "los sellos borrados siguen en memoria");
        save(stamps, "queda", "Ana", ANA, 10L);
        save(stamps, "a_mano", "Ana", ANA, 11L);
        stamps.list();
        assertEquals(2, catalog.cached());
        Files.delete(folder.resolve("a_mano.png"));
        stamps.list();
        assertEquals(1, catalog.cached(), "el PNG quitado a mano tampoco se queda");
    }

    @Test
    void namesLimitsAndReplacing(@TempDir Path folder) {
        OwnStamps stamps = new OwnStamps(new StampCatalog(folder));
        StampCapture.Result result = captured("montana", 10);
        assertThrows(IllegalArgumentException.class, () -> stamps.save(meta("montana", "Ana", ANA, 1L, result),
                result.stamp(), false, ANA, false, OwnStamps.Limits.DEFAULT, false), "no tapa a uno de serie");
        save(stamps, "uno", "Ana", ANA, 1L);
        assertThrows(IllegalArgumentException.class, () -> save(stamps, "uno", "Ana", ANA, 2L), "sin -r no pisa");
        StampCapture.Result bigger = captured("uno", 20);
        assertThrows(IllegalArgumentException.class, () -> stamps.save(meta("uno", "Luis", LUIS, 3L, bigger),
                bigger.stamp(), true, LUIS, false, OwnStamps.Limits.DEFAULT, false), "ni con -r el de otro");
        stamps.save(meta("uno", "Ana", ANA, 3L, bigger), bigger.stamp(), true, ANA, false, OwnStamps.Limits.DEFAULT,
                false);
        assertEquals(bigger.above(), stamps.find("uno").meta().above(), 0.01);
        OwnStamps.Limits two = new OwnStamps.Limits(10, 2);
        StampCapture.Result other = captured("dos", 8);
        stamps.save(meta("dos", "Ana", ANA, 4L, other), other.stamp(), false, ANA, false, two, false);
        assertThrows(IllegalArgumentException.class, () -> stamps.save(meta("tres", "Ana", ANA, 5L, other),
                other.stamp(), false, ANA, false, two, false), "tope por jugador");
        stamps.save(meta("tres", "Ana", ANA, 5L, other), other.stamp(), false, ANA, false, two, true);
        assertEquals(3, stamps.countBy(ANA));
    }

    @Test
    void aPngLeftByHandIsOwnTooWithoutAuthor(@TempDir Path folder) throws IOException {
        HeightStamp stamp = StampLibrary.render("colina", 32);
        try (OutputStream output = Files.newOutputStream(folder.resolve("mi_png.png"))) {
            StampImages.write(stamp, output);
        }
        OwnStamps stamps = new OwnStamps(new StampCatalog(folder));
        OwnStamps.Entry entry = stamps.find("mi_png");
        assertNotNull(entry);
        assertEquals("", entry.meta().author());
        assertEquals(HeightmapBrush.CUSTOM_RATIO, entry.meta().heightRatio(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> stamps.describe("mi_png", "x", ANA, false),
                "un PNG del servidor solo lo describe un administrador");
    }

    @Test
    void theyTravelInAPackAndArriveCleaned(@TempDir Path here, @TempDir Path there) {
        OwnStamps origin = new OwnStamps(new StampCatalog(here));
        save(origin, "viajera", "Ana", ANA, 1L);
        StampKind out = new StampKind(() -> origin);
        ShareTarget ana = new ShareTarget(UUID.fromString(ANA), "Ana", permission -> true);
        SharePack.Item item = out.export(ana, "viajera");
        assertTrue(item.hasBlob());

        OwnStamps destination = new OwnStamps(new StampCatalog(there));
        StampKind in = new StampKind(() -> destination);
        SharePack.Item checked = in.check(item, null);
        ShareTarget luis = new ShareTarget(UUID.fromString(LUIS), "Luis", permission -> true);
        in.save(luis, checked, checked.name(), false);
        OwnStamps.Entry arrived = destination.find("viajera");
        assertNotNull(arrived);
        assertEquals("Ana", arrived.meta().author(), "conserva quién lo hizo");
        assertTrue(arrived.meta().ownedBy(LUIS), "y lo puede borrar quien lo importó");
        assertEquals(origin.find("viajera").meta().above(), arrived.meta().above(), 0.01);
        HeightStamp a = loaded(origin, "viajera");
        HeightStamp b = loaded(destination, "viajera");
        assertArrayEquals(a.values(), b.values());
        assertEquals("montana_propio", in.normalizeName("montana"), "no tapa a uno de serie");
        SharePack.Item broken = new SharePack.Item("stamp", "roto", 1, item.data(), new byte[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> in.check(broken, null));
    }

    private static HeightStamp loaded(OwnStamps stamps, String name) {
        try {
            return stamps.catalog().load(name);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void previewsAreSmallAndRebuildTheRelief(@TempDir Path folder) {
        OwnStamps stamps = new OwnStamps(new StampCatalog(folder));
        OwnStamps.Entry entry = save(stamps, "mini", "Ana", ANA, 1L);
        OwnStamps.Preview preview = stamps.preview(entry, OwnStamps.Preview.SIDE);
        assertEquals(OwnStamps.Preview.SIDE * OwnStamps.Preview.SIDE, preview.values().length);
        HeightStamp small = preview.stamp("mini");
        assertTrue(small.reliefAt(0.5, 0.5) > 0.8, "la cima en medio");
        assertEquals(0.0, small.reliefAt(0.02, 0.5), 0.05, "el borde a nivel");
        assertEquals(StampCategory.MOUNTAINS, entry.meta().category(), "hierba, pero alta para su ancho: montaña");
    }
}
