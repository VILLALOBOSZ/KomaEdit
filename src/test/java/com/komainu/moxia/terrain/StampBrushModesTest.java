package com.komainu.moxia.terrain;

import com.komainu.moxia.terrain.stamp.StampCategory;
import com.komainu.moxia.terrain.stamp.StampInfo;
import com.komainu.moxia.terrain.stamp.StampPlacement;
import com.komainu.moxia.terrain.stamp.StampVariation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Modos de mezcla, variación por clic, colocación, paletas por categoría y la API de muestreo. */
class StampBrushModesTest {
    private static final Runnable NO_CANCEL = () -> {
    };

    /** Un campo de {@code size}×{@code size} editable, a {@code height} o con el relieve que diga la función. */
    private static HeightField field(int size, java.util.function.IntBinaryOperator height) {
        HeightField field = new HeightField(0, 0, size, size);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                field.define(x, z, height.applyAsInt(x, z), true, -64, 320);
            }
        }
        return field;
    }

    private static StampBrush.Settings settings(String stamp, StampBrush.Mode mode, double radius, double height,
                                                double baseY) {
        return new StampBrush.Settings(StampLibrary.get(stamp), mode, radius, height, 1.0, 0.0, baseY);
    }

    // ── Modos de mezcla ─────────────────────────────────────────────────────────────────────────────

    @Test
    void addAndSubtractAreOpposites() {
        HeightField field = field(64, (x, z) -> 40);
        StampBrush.apply(field, 32.5, 32.5, settings("colina", StampBrush.Mode.RAISE, 20, 10, 40), NO_CANCEL);
        assertTrue(field.get(32, 32) > 48, "sumar levanta la colina");
        StampBrush.apply(field, 32.5, 32.5, settings("colina", StampBrush.Mode.LOWER, 20, 10, 40), NO_CANCEL);
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                assertEquals(40.0, field.get(x, z), 1e-9, "restar deshace sumar");
            }
        }
        assertSame(StampBrush.Mode.LOWER, StampBrush.Mode.RAISE.opposite());
        assertSame(StampBrush.Mode.LOWER, StampBrush.Mode.MAX.opposite(), "el clic izquierdo de máximo resta");
        assertSame(StampBrush.Mode.SMOOTH, StampBrush.Mode.SMOOTH.opposite());
    }

    @Test
    void maximumNeverSinksWhatIsHigherAndDoesNotPileUp() {
        // Una torre alta en medio y llano a 40 alrededor.
        HeightField field = field(64, (x, z) -> Math.abs(x - 32) <= 2 && Math.abs(z - 32) <= 2 ? 90 : 40);
        HeightField original = field.copy();
        StampBrush.Settings max = settings("montana", StampBrush.Mode.MAX, 24, 20, 40);
        StampBrush.apply(field, 32.5, 32.5, max, NO_CANCEL);
        assertEquals(90.0, field.get(32, 32), 1e-9, "lo que ya está más alto no se toca");
        double once = field.get(32, 26);
        assertTrue(once > 45, "la montaña sube donde el terreno estaba más bajo: " + once);
        HeightField first = field.copy();
        for (int pass = 0; pass < 5; pass++) {
            StampBrush.apply(field, 32.5, 32.5, max, NO_CANCEL);
        }
        HeightStamp montana = StampLibrary.get("montana");
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                double relief = max.placement().relief(montana, x + 0.5 - 32.5, z + 0.5 - 32.5);
                double stamp = 40 + relief * 20;
                assertTrue(field.get(x, z) <= Math.max(original.get(x, z), stamp) + 1e-9,
                        "seis pasadas no suben nada por encima de la montaña: " + x + "," + z);
                if (relief > 0.12) {
                    assertEquals(first.get(x, z), field.get(x, z), 1e-9, "donde la montaña manda, igual que una vez");
                }
            }
        }
        HeightField added = field(64, (x, z) -> 40);
        StampBrush.apply(added, 32.5, 32.5, settings("montana", StampBrush.Mode.RAISE, 24, 20, 40), NO_CANCEL);
        StampBrush.apply(added, 32.5, 32.5, settings("montana", StampBrush.Mode.RAISE, 24, 20, 40), NO_CANCEL);
        assertTrue(added.get(32, 26) > once + 3, "sumar dos veces sí apila");
    }

    @Test
    void flattenLeavesTheGroundAtTheStampHeight() {
        // Terreno revuelto: alto por un lado y bajo por el otro.
        HeightField field = field(64, (x, z) -> 30 + x);
        StampBrush.Settings flatten = settings("mesa", StampBrush.Mode.FLATTEN, 24, 12, 50);
        StampBrush.apply(field, 32.5, 32.5, flatten, NO_CANCEL);
        StampPlacement placement = flatten.placement();
        HeightStamp mesa = StampLibrary.get("mesa");
        for (int z = 26; z <= 38; z++) {
            for (int x = 26; x <= 38; x++) {
                double expected = 50 + placement.relief(mesa, x + 0.5 - 32.5, z + 0.5 - 32.5) * 12;
                assertEquals(expected, field.get(x, z), 1e-6, "dentro, la altura del sello puesto en el clic");
            }
        }
        assertEquals(30.0 + 2, field.get(2, 32), 1e-9, "fuera de su huella no cambia");
    }

    @Test
    void smoothSoftensWhatIsUnderTheStampAndNothingElse() {
        HeightField field = field(64, (x, z) -> 40 + ((x * 7 + z * 13) % 5) * 2);
        double before = roughness(field, 26, 38);
        double outside = field.get(1, 1);
        StampBrush.apply(field, 32.5, 32.5, settings("colina", StampBrush.Mode.SMOOTH, 16, 10, 40), NO_CANCEL);
        double after = roughness(field, 26, 38);
        assertTrue(after < before * 0.5, "rugosidad " + before + " → " + after);
        assertEquals(outside, field.get(1, 1), 1e-9, "lejos del sello no cambia");
    }

    private static double roughness(HeightField field, int from, int to) {
        double sum = 0.0;
        for (int z = from; z <= to; z++) {
            for (int x = from; x < to; x++) {
                sum += Math.abs(field.get(x + 1, z) - field.get(x, z));
            }
        }
        return sum;
    }

    @Test
    void modesReadTheirSpanishAndEnglishNames() {
        assertSame(StampBrush.Mode.RAISE, StampBrush.Mode.byId("sumar"));
        assertSame(StampBrush.Mode.RAISE, StampBrush.Mode.byId("elevar"));
        assertSame(StampBrush.Mode.LOWER, StampBrush.Mode.byId("restar"));
        assertSame(StampBrush.Mode.MAX, StampBrush.Mode.byId("máximo"));
        assertSame(StampBrush.Mode.FLATTEN, StampBrush.Mode.byId("aplanar"));
        assertSame(StampBrush.Mode.SMOOTH, StampBrush.Mode.byId("suavizar"));
        assertThrows(IllegalArgumentException.class, () -> StampBrush.Mode.byId("doblar"));
    }

    // ── Variación por clic ──────────────────────────────────────────────────────────────────────────

    @Test
    void eachClickVariesButTheSameSeedAndClickRepeat() {
        StampVariation variation = StampVariation.DEFAULT;
        Set<String> placements = new HashSet<>();
        boolean mirrored = false;
        boolean straight = false;
        for (int click = 0; click < 20; click++) {
            StampPlacement placement = variation.place(16, 1.0, false, 1234L, click);
            assertEquals(placement, variation.place(16, 1.0, false, 1234L, click), "misma semilla y clic");
            assertTrue(Math.abs(placement.angle() - 1.0) <= Math.toRadians(StampVariation.MAX_TURN_DEGREES)
                    * variation.amount() + 1e-9, "el giro solo se mueve un poco alrededor del pedido");
            assertTrue(Math.abs(placement.scale() - 1.0) <= StampVariation.MAX_SCALE * variation.amount() + 1e-9);
            assertTrue(placement.detail() > 0.0, "con ruido fino");
            placements.add(placement.angle() + "/" + placement.scale() + "/" + placement.mirrored());
            mirrored |= placement.mirrored();
            straight |= !placement.mirrored();
        }
        assertEquals(20, placements.size(), "veinte clics, veinte colocaciones distintas");
        assertTrue(mirrored && straight, "unos en espejo y otros no");
        assertNotEquals(variation.place(16, 1.0, false, 1L, 0), variation.place(16, 1.0, false, 2L, 0),
                "otra semilla, otra variación");
        StampPlacement none = StampVariation.NONE.place(16, 1.0, false, 99L, 5);
        assertEquals(StampPlacement.of(16, 1.0).angle(), none.angle(), 0.0);
        assertEquals(1.0, none.scale(), 0.0);
        assertFalse(none.mirrored());
        assertEquals(0.0, none.detail(), 0.0);
        double random = StampVariation.NONE.place(16, 1.0, true, 99L, 5).angle();
        assertTrue(random >= 0.0 && random < Math.PI * 2.0, "al azar, cualquier giro aunque no haya variación");
    }

    @Test
    void aLockedSeedRepeatsExactlyEveryClick() {
        HeightmapBrush.Config locked = config(true);
        HeightmapBrush.Config free = config(false);
        StampPlacement first = HeightmapBrush.placement(locked, 90f, 42L, 0);
        for (int click = 1; click < 6; click++) {
            assertEquals(first, HeightmapBrush.placement(locked, 90f, 42L, click), "bloqueada: el clic " + click);
        }
        assertNotEquals(HeightmapBrush.placement(free, 90f, 42L, 1), HeightmapBrush.placement(free, 90f, 42L, 2));
        // Y la operación de dos clics libres no deja dos copias iguales en el terreno.
        HeightField a = field(80, (x, z) -> 40);
        HeightField b = field(80, (x, z) -> 40);
        StampBrush.apply(a, 40.5, 40.5, new StampBrush.Settings(free.stamp(), StampBrush.Mode.RAISE,
                HeightmapBrush.placement(free, 90f, 42L, 1), 20, 1.0, 40), NO_CANCEL);
        StampBrush.apply(b, 40.5, 40.5, new StampBrush.Settings(free.stamp(), StampBrush.Mode.RAISE,
                HeightmapBrush.placement(free, 90f, 42L, 2), 20, 1.0, 40), NO_CANCEL);
        int different = 0;
        for (int index = 0; index < a.columns(); index++) {
            different += Math.abs(a.at(index) - b.at(index)) > 0.5 ? 1 : 0;
        }
        assertTrue(different > 200, different + " columnas distintas entre dos clics");
    }

    private static HeightmapBrush.Config config(boolean locked) {
        return new HeightmapBrush.Config(StampLibrary.get("montana"), 16, 20, StampBrush.Mode.RAISE, 1.0,
                HeightmapBrush.Rotation.AIM, 0.0, SurfacePalette.natural(), null, null, StampVariation.DEFAULT, 42L,
                locked);
    }

    // ── Colocación y muestreo (la API para otras herramientas) ─────────────────────────────────────

    @Test
    void placementRotatesScalesAndMirrors() {
        HeightStamp stamp = StampLibrary.get("duna");
        StampPlacement straight = StampPlacement.of(20, 0.0);
        StampPlacement mirrored = new StampPlacement(20, 0.0, 1.0, true, 0L, 0.0);
        StampPlacement turned = StampPlacement.of(20, Math.PI / 2.0);
        StampPlacement bigger = new StampPlacement(20, 0.0, 1.2, false, 0L, 0.0);
        for (double dx = -18; dx <= 18; dx += 3) {
            for (double dz = -18; dz <= 18; dz += 3) {
                assertEquals(straight.relief(stamp, -dx, dz), mirrored.relief(stamp, dx, dz), 1e-9, "espejo");
                // Girado 90° en sentido horario: lo que estaba al norte queda al este.
                assertEquals(straight.relief(stamp, dz, -dx), turned.relief(stamp, dx, dz), 1e-9, "giro");
                assertEquals(straight.relief(stamp, dx, dz), bigger.relief(stamp, dx * 1.2, dz * 1.2), 1e-9,
                        "escala");
            }
        }
        assertEquals(0.0, straight.relief(stamp, 30, 0), 0.0, "fuera del sello, nada");
        assertEquals(stamp.reliefAt(0.5, 0.5), straight.relief(stamp, 0, 0), 1e-12);
        assertTrue(bigger.reach() > straight.reach());
        assertEquals(1.0, straight.footprint(0, 0), 0.0);
        assertEquals(0.0, straight.footprint(20, 0), 0.0);
    }

    @Test
    void fineDetailVariesTheShapeButNotItsBorder() {
        HeightStamp stamp = StampLibrary.get("colina");
        StampPlacement plain = StampPlacement.of(20, 0.0);
        StampPlacement detailed = new StampPlacement(20, 0.0, 1.0, false, 77L, 0.3);
        double changed = 0.0;
        for (double dx = -19; dx <= 19; dx += 1) {
            for (double dz = -19; dz <= 19; dz += 1) {
                double base = plain.relief(stamp, dx, dz);
                double varied = detailed.relief(stamp, dx, dz);
                if (base == 0.0) {
                    assertEquals(0.0, varied, 0.0, "donde el sello es cero sigue en cero");
                }
                assertTrue(Math.abs(varied - base) <= Math.abs(base) * 0.3 + 1e-9);
                changed += Math.abs(varied - base);
            }
        }
        assertTrue(changed > 5.0, "el ruido fino se nota: " + changed);
    }

    @Test
    void theLibraryApiGivesWhatOtherToolsNeed() {
        StampInfo volcano = StampLibrary.info("volcan");
        assertEquals(StampCategory.VOLCANOES, volcano.category());
        assertEquals("ceniza", volcano.palette());
        assertTrue(StampLibrary.of(StampCategory.DUNES).stream().allMatch(info -> info.palette().equals("desierto")));
        assertEquals(volcano.naturalHeight(20, 96), HeightmapBrush.naturalHeight("volcan", 20));
        assertEquals(10, HeightmapBrush.naturalHeight("mi_png", 20), "un PNG propio: la mitad del radio");
        assertEquals(20 * 20, StampLibrary.thumbnail("volcan", 20).length);
        HeightStamp variant = StampLibrary.render("volcan", 48, 5L);
        assertEquals(48, variant.width());
    }

    // ── Paletas por categoría ───────────────────────────────────────────────────────────────────────

    @Test
    void autoPaletteIsTheCategoryOneAndCanBeChanged() {
        assertEquals("alpino", HeightmapBrush.paletteFor("montana", null));
        assertEquals("alpino", HeightmapBrush.paletteFor("montana", "auto"));
        assertEquals("desierto", HeightmapBrush.paletteFor("duna", ""));
        assertEquals("nevado", HeightmapBrush.paletteFor("duna", "nevado"));
        assertEquals(SurfacePalette.DEFAULT, HeightmapBrush.paletteFor("mi_png", null));
        for (StampCategory category : StampCategory.values()) {
            assertTrue(SurfacePalette.presets().containsKey(category.palette()), category.palette());
        }
    }

    @Test
    void aRelativePeakFollowsWhatIsBeingChanged() {
        SurfacePalette alpine = SurfacePalette.parse("alpino");
        assertTrue(alpine.relativePeak());
        SurfacePalette resolved = alpine.forRange(60, 100);
        assertFalse(resolved.relativePeak());
        SurfacePalette.Column top = new SurfacePalette.Column(95, 10.0, TerrainColumns.NONE, null, null);
        SurfacePalette.Column foot = new SurfacePalette.Column(65, 10.0, TerrainColumns.NONE, null, null);
        assertEquals("minecraft:snow_block", resolved.material(0, 95, 0, 0, top), "nieve en el tercio de arriba");
        assertEquals("minecraft:grass_block", resolved.material(0, 65, 0, 0, foot), "hierba al pie");
        HeightField field = field(8, (x, z) -> 60 + x * 5);
        assertFalse(TerrainWriter.resolvePeak(alpine, field).relativePeak());
        SurfacePalette flat = alpine.forRange(60, 63);
        assertEquals("minecraft:grass_block", flat.material(0, 63, 0, 0,
                new SurfacePalette.Column(63, 2.0, TerrainColumns.NONE, null, null)), "un llano no se nieva");
        assertThrows(IllegalArgumentException.class, () -> SurfacePalette.parse("natural,pendiente:stone@40%"),
                "solo la cumbre admite porcentaje");
        assertFalse(SurfacePalette.parse("alpino,cumbre:powder_snow@170").relativePeak(), "una Y fija la anula");
    }

    @Test
    void theBrushPutsSnowOnTheStampedMountainButNotInAHole() {
        FakeTerrain raised = TerrainScenes.flat(80, 90, 20);
        // Un cerro de cima casi llana: arriba no hay pendiente que lo pinte de roca.
        StampBrush.Settings mountain = new StampBrush.Settings(StampLibrary.get("cerro"), StampBrush.Mode.RAISE, 30,
                30, 1.0, 0.0, 20);
        TerrainJobs.stamp(raised, mountain, 40, 40, SurfacePalette.parse("alpino"), raised, NO_CANCEL);
        int top = raised.top(40, 40);
        assertTrue(top >= 48, "el cerro sube: " + top);
        assertEquals("minecraft:snow_block", raised.blockId(40, top, 40), "nieve en la cumbre");
        FakeTerrain hole = TerrainScenes.flat(80, 90, 60);
        TerrainJobs.stamp(hole, new StampBrush.Settings(StampLibrary.get("pico"), StampBrush.Mode.LOWER, 30, 40, 1.0,
                0.0, 60), 40, 40, SurfacePalette.parse("alpino"), hole, NO_CANCEL);
        for (int x = 0; x < 80; x++) {
            int y = hole.top(x, 40);
            assertNotEquals("minecraft:snow_block", hole.blockId(x, y, 40), "un hoyo no se nieva");
        }
    }

    // ── PNG que dejaba la 1.8 en la carpeta ─────────────────────────────────────────────────────────

    @Test
    void untouchedTemplatesFromVersionOneEightDoNotHideTheNewStamps(@TempDir Path directory) throws Exception {
        // Una imagen con la huella de la «montana» de la 1.8: cada octavo, del gris de la huella.
        String signature = "000001070a03000000041527351e05000114365f603b1501042263ad9c642504"
                + "0423639f9f572202011443736239140100061a28251304000000010506020000";
        BufferedImage old = new BufferedImage(64, 64, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int block = (y / 8) * 8 + x / 8;
                old.getRaster().setSample(x, y, 0, Integer.parseInt(signature, block * 2, block * 2 + 2, 16));
            }
        }
        Path folder = directory.resolve("heightmaps");
        java.nio.file.Files.createDirectories(folder);
        javax.imageio.ImageIO.write(old, "png", folder.resolve("montana.png").toFile());
        StampCatalog catalog = new StampCatalog(folder);
        assertSame(StampLibrary.get("montana"), catalog.load("montana"), "la plantilla vieja no tapa a la nueva");
        // Retocada, sí manda la del jugador.
        old.getRaster().setSample(30, 30, 0, 255);
        for (int y = 24; y < 40; y++) {
            for (int x = 24; x < 40; x++) {
                old.getRaster().setSample(x, y, 0, 255);
            }
        }
        javax.imageio.ImageIO.write(old, "png", folder.resolve("montana.png").toFile());
        java.nio.file.Files.setLastModifiedTime(folder.resolve("montana.png"),
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        HeightStamp edited = catalog.load("montana");
        assertEquals(64, edited.width(), "el PNG retocado del jugador gana");
    }
}
