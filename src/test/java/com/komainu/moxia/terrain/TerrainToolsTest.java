package com.komainu.moxia.terrain;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainToolsTest {
    private static final Runnable NO_CANCEL = FakeTerrain.NO_CANCEL;

    // ------------------------------------------------------------------ erosión

    @Test
    void hydraulicErosionConservesMass() {
        FakeTerrain terrain = TerrainScenes.mountain(96, 90, 3L);
        HeightField field = TerrainColumns.read(terrain, NO_CANCEL).heights();
        double before = field.editableSum();
        HydraulicErosion.Settings settings = HydraulicErosion.Settings.of(field.editableColumns(), 3.0, 1.0, 42L);
        HydraulicErosion.Report report = HydraulicErosion.run(field, settings, NO_CANCEL);
        double after = field.editableSum();
        assertTrue(report.eroded() > 500.0, "tiene que arrastrar algo: " + report.eroded());
        assertEquals(report.eroded(), report.deposited() + report.lost(), 1e-6 * report.eroded());
        assertEquals(before, after + report.lost(), 1e-6 * before, "la masa arrancada se deposita");
        assertTrue(report.lost() < report.eroded() * 0.01, "casi nada se pierde: " + report.lost());
    }

    @Test
    void thermalErosionConservesMassAndRelaxesSlopes() {
        FakeTerrain terrain = FakeTerrain.columns(48, 80, 48, (x, z) -> x >= 20 && x < 28 ? 40 : 10);
        HeightField field = TerrainColumns.read(terrain, NO_CANCEL).heights();
        double before = field.editableSum();
        double moved = ThermalErosion.run(field, new ThermalErosion.Settings(1_500, 40.0, 1.0), NO_CANCEL);
        assertTrue(moved > 100.0);
        assertEquals(before, field.editableSum(), 1e-6 * before);
        // Ninguna pendiente queda muy por encima del ángulo de reposo (con su variación de dureza del 30 %).
        double limit = Math.tan(Math.toRadians(40.0 * 1.3)) + 0.05;
        for (int z = 1; z < 47; z++) {
            for (int x = 1; x < 47; x++) {
                assertTrue(Math.abs(field.get(x + 1, z) - field.get(x, z)) <= limit + 0.5,
                        "talud en " + x + "," + z);
            }
        }
    }

    @Test
    void erosionKeepsBlockCountRoughlyAndIsDeterministic() {
        FakeTerrain first = TerrainScenes.mountain(80, 80, 8L);
        FakeTerrain second = first.copy();
        FakeTerrain third = first.copy();
        long blocks = first.groundBlocks();
        TerrainJobs.erode(first, TerrainJobs.Erosion.FULL, 2.0, 1.0, 40.0, 7L, SurfacePalette.natural(), first,
                NO_CANCEL);
        TerrainJobs.erode(second, TerrainJobs.Erosion.FULL, 2.0, 1.0, 40.0, 7L, SurfacePalette.natural(), second,
                NO_CANCEL);
        TerrainJobs.erode(third, TerrainJobs.Erosion.FULL, 2.0, 1.0, 40.0, 8L, SurfacePalette.natural(), third,
                NO_CANCEL);
        assertTrue(first.writes() > 0);
        // Al redondear cada columna gana o pierde como mucho medio bloque.
        long columns = 80L * 80L;
        assertTrue(Math.abs(first.groundBlocks() - blocks) <= columns / 2, "bloques: " + blocks + " → "
                + first.groundBlocks());
        assertArrayEquals(tops(first), tops(second), "misma semilla, mismo resultado");
        assertFalse(java.util.Arrays.equals(tops(first), tops(third)), "otra semilla cambia las gotas");
    }

    @Test
    void erosionRespectsMaskAndSelection() {
        FakeTerrain terrain = TerrainScenes.mountain(64, 80, 2L);
        // La mitad oeste es arena y queda fuera de la máscara; además solo se puede escribir en x < 50.
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 20; x++) {
                terrain.put(x, terrain.top(x, z), z, "minecraft:sand");
            }
        }
        terrain.mask(id -> !id.equals("minecraft:sand")).writable(new int[]{0, 0, 0, 49, 79, 63});
        int[] before = tops(terrain);
        TerrainJobs.erode(terrain, TerrainJobs.Erosion.HYDRAULIC, 3.0, 1.5, 40.0, 3L, SurfacePalette.natural(),
                terrain, NO_CANCEL);
        int[] after = tops(terrain);
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                if (x < 20 || x >= 50) {
                    assertEquals(before[z * 64 + x], after[z * 64 + x], "columna fija " + x + "," + z);
                }
            }
        }
        assertFalse(java.util.Arrays.equals(before, after));
    }

    // ------------------------------------------------------------------ ríos

    @Test
    void riverBedAndWaterNeverRise() {
        FakeTerrain terrain = TerrainScenes.slope(120, 90, 21L);
        List<double[]> path = TerrainScenes.curve(new double[][]{{6, 0, 20}, {40, 0, 50}, {70, 0, 30},
                {112, 0, 90}});
        RiverPlanner.Settings settings = RiverPlanner.preset("rio");
        TerrainColumns columns = TerrainColumns.read(terrain, NO_CANCEL);
        HeightField field = columns.heights();
        RiverPlanner.Result result = RiverPlanner.plan(field, path, settings, NO_CANCEL);
        assertFalse(result.reversed(), "la ladera baja hacia el este: el agua va en el orden de los puntos");
        for (int index = 1; index < result.bed().length; index++) {
            assertTrue(result.bed()[index] <= result.bed()[index - 1] + 1e-9, "el perfil sube en " + index);
        }
        TerrainWriter.write(columns, field, result, SurfacePalette.natural(), false, terrain, NO_CANCEL);
        int lastBed = Integer.MAX_VALUE;
        int lastWater = Integer.MAX_VALUE;
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        int wetColumns = 0;
        for (double[] sample : result.samples()) {
            int x = (int) Math.floor(sample[0]);
            int z = (int) Math.floor(sample[2]);
            if (x == lastX && z == lastZ) {
                continue;
            }
            lastX = x;
            lastZ = z;
            int bed = terrain.top(x, z);
            int water = terrain.liquidTop(x, z);
            assertTrue(bed <= lastBed, "el fondo sube en " + x + "," + z + ": " + lastBed + " → " + bed);
            lastBed = bed;
            if (water >= 0) {
                assertEquals("minecraft:water", terrain.blockId(x, water, z));
                assertTrue(water <= lastWater, "el agua sube en " + x + "," + z);
                lastWater = water;
                wetColumns++;
            }
        }
        assertTrue(wetColumns > 60, "casi todo el eje lleva agua: " + wetColumns);
        assertWaterContained(terrain);
    }

    @Test
    void riverFlowsTowardsTheLowerEndUnlessForced() {
        FakeTerrain terrain = TerrainScenes.slope(100, 80, 5L);
        List<double[]> uphill = TerrainScenes.curve(new double[][]{{94, 0, 50}, {50, 0, 40}, {6, 0, 60}});
        HeightField field = TerrainColumns.read(terrain, NO_CANCEL).heights();
        RiverPlanner.Result result = RiverPlanner.plan(field, uphill, RiverPlanner.preset("arroyo"), NO_CANCEL);
        assertTrue(result.reversed(), "los puntos van cuesta arriba: se invierten");
        assertTrue(result.startBed() > result.endBed() + 5.0);

        RiverPlanner.Settings base = RiverPlanner.preset("arroyo");
        RiverPlanner.Settings forced = new RiverPlanner.Settings(base.halfWidth(), base.depth(), base.bank(),
                base.wallSlope(), base.transition(), base.fluid(), base.level(), false, true, base.smoothing());
        HeightField other = TerrainColumns.read(terrain, NO_CANCEL).heights();
        RiverPlanner.Result kept = RiverPlanner.plan(other, uphill, forced, NO_CANCEL);
        assertFalse(kept.reversed());
        for (int index = 1; index < kept.bed().length; index++) {
            assertTrue(kept.bed()[index] <= kept.bed()[index - 1] + 1e-9, "forzado tampoco sube");
        }
    }

    @Test
    void canyonCutsDeepWithBanksThatBlendOutwards() {
        FakeTerrain terrain = TerrainScenes.flat(80, 80, 60);
        List<double[]> path = TerrainScenes.curve(new double[][]{{2, 0, 40}, {78, 0, 40}});
        TerrainJobs.river(terrain, path, RiverPlanner.preset("canon"), SurfacePalette.natural(), terrain, NO_CANCEL);
        assertEquals(48, terrain.top(40, 40), "el fondo baja la profundidad del cañón");
        assertEquals(50, terrain.liquidTop(40, 40), "agua dos bloques sobre el fondo");
        // El borde no baja, pero su tierra queda al aire sobre la orilla: se pinta como pendiente.
        assertEquals(60, terrain.top(40, 34));
        assertTrue(terrain.blockId(40, 59, 34).matches("minecraft:(stone|andesite)"),
                "la pared al aire se pinta como pendiente: " + terrain.blockId(40, 59, 34));
        assertEquals(60, terrain.top(40, 10), "lejos del cauce el terreno no cambia");
        // De fuera a dentro la sección solo baja, sin muros sueltos ni repisas que suban.
        int previous = terrain.top(40, 10);
        for (int z = 10; z <= 40; z++) {
            int top = terrain.top(40, z);
            assertTrue(top <= previous, "la orilla sube en z=" + z);
            previous = top;
        }
    }

    @Test
    void dryGullyHasNoFluidAndLavaUsesLava() {
        FakeTerrain dry = TerrainScenes.flat(50, 60, 40);
        TerrainJobs.river(dry, TerrainScenes.curve(new double[][]{{2, 0, 25}, {48, 0, 25}}),
                RiverPlanner.preset("barranco"), SurfacePalette.natural(), dry, NO_CANCEL);
        assertEquals(35, dry.top(25, 25));
        assertEquals(-1, dry.liquidTop(25, 25));

        FakeTerrain hot = TerrainScenes.flat(50, 60, 40);
        RiverPlanner.Settings base = RiverPlanner.preset("rio");
        RiverPlanner.Settings lava = new RiverPlanner.Settings(base.halfWidth(), base.depth(), base.bank(),
                base.wallSlope(), base.transition(), RiverPlanner.Fluid.LAVA, base.level(), false, false, 0.0);
        TerrainJobs.river(hot, TerrainScenes.curve(new double[][]{{2, 0, 25}, {48, 0, 25}}), lava,
                SurfacePalette.parse("volcanico"), hot, NO_CANCEL);
        assertEquals("minecraft:lava", hot.blockId(25, hot.liquidTop(25, 25), 25));
        assertTrue(hot.blockId(25, hot.top(25, 25), 25).matches("minecraft:(magma_block|blackstone)"),
                "lecho volcánico: " + hot.blockId(25, hot.top(25, 25), 25));
    }

    private static void assertWaterContained(FakeTerrain terrain) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int z = 1; z < terrain.sizeZ() - 1; z++) {
            for (int x = 1; x < terrain.sizeX() - 1; x++) {
                int water = terrain.liquidTop(x, z);
                if (water < 0) {
                    continue;
                }
                for (int[] offset : offsets) {
                    int nx = x + offset[0];
                    int nz = z + offset[1];
                    int neighbourWater = terrain.liquidTop(nx, nz);
                    int neighbourTop = terrain.top(nx, nz);
                    // O es agua también (a su nivel o más baja, río abajo) o su suelo llega al nivel del agua.
                    assertTrue(neighbourWater >= 0 || neighbourTop >= water,
                            "el agua de " + x + "," + z + " (Y " + water + ") se escapa hacia " + nx + "," + nz
                                    + " (suelo Y " + neighbourTop + ")");
                }
            }
        }
    }

    // ------------------------------------------------------------------ rampas

    @Test
    void rampHasNoStepsTallerThanOneBlock() {
        for (RampPlanner.Section section : RampPlanner.Section.values()) {
            FakeTerrain terrain = TerrainScenes.flat(90, 70, 30);
            int[] start = {10, 30, 12};
            int[] end = {72, 52, 70};
            // Plana: 22 bloques de desnivel en una transición de 52 dejan la ladera por debajo de uno por bloque.
            RampPlanner.Settings settings = new RampPlanner.Settings(section, 2.5, 52.0, 1.0, false);
            TerrainJobs.ramp(terrain, start, end, settings, SurfacePalette.natural(), terrain, NO_CANCEL);
            assertEquals(30, terrain.top(10, 12), "empieza en el bloque marcado");
            assertEquals(52, terrain.top(72, 70), "acaba en el bloque marcado");
            for (int z = 0; z < 89; z++) {
                for (int x = 0; x < 89; x++) {
                    int here = terrain.top(x, z);
                    assertTrue(Math.abs(terrain.top(x + 1, z) - here) <= 1,
                            section + ": escalón en " + x + "," + z);
                    assertTrue(Math.abs(terrain.top(x, z + 1) - here) <= 1,
                            section + ": escalón en " + x + "," + z);
                }
            }
        }
    }

    @Test
    void rampFillsAndCutsAndRejectsSteepSlopes() {
        FakeTerrain terrain = FakeTerrain.columns(80, 80, 40, (x, z) -> x < 40 ? 20 : 50);
        int[] start = {5, 30, 20};
        int[] end = {75, 40, 20};
        TerrainJobs.ramp(terrain, start, end, new RampPlanner.Settings(RampPlanner.Section.CONE, 2.0, 0.0, 1.0,
                false), SurfacePalette.natural(), terrain, NO_CANCEL);
        assertEquals(31, terrain.top(12, 20), "rellena sobre lo bajo");
        assertEquals(39, terrain.top(68, 20), "recorta en lo alto");
        assertEquals("minecraft:grass_block", terrain.blockId(12, 31, 20));
        IllegalArgumentException steep = assertThrows(IllegalArgumentException.class, () -> TerrainJobs.ramp(
                TerrainScenes.flat(40, 60, 10), new int[]{5, 10, 5}, new int[]{15, 40, 5},
                new RampPlanner.Settings(RampPlanner.Section.FLAT, 2.0, 4.0, 1.0, false), SurfacePalette.natural(),
                new FakeTerrain(0, 0, 0, 1, 1, 1), NO_CANCEL));
        assertTrue(steep.getMessage().contains("escalones"));
        assertTrue(RampPlanner.grade(new double[]{0, 0, 0}, new double[]{10, 10, 0}, true) > 1.0,
                "el perfil suave es 1,5 veces más empinado en el centro");
    }

    // ------------------------------------------------------------------ sellos

    @Test
    void stampsFollowScaleAndRotation() {
        // Un punto brillante en la parte de arriba de la imagen, a un 35 % del centro.
        int size = 101;
        float[] values = new float[size * size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                double dx = column - 50;
                double dy = row - 15;
                values[row * size + column] = (float) Math.exp(-(dx * dx + dy * dy) / 8.0);
            }
        }
        HeightStamp dot = new HeightStamp("punto", size, size, values, 0.0);
        assertPeakAt(dot, 20.0, 0.0, 64, 50);       // giro 0: hacia el norte (-Z)
        assertPeakAt(dot, 20.0, 90.0, 78, 64);      // 90°: hacia el este (+X)
        assertPeakAt(dot, 20.0, 180.0, 64, 78);     // 180°: hacia el sur
        assertPeakAt(dot, 10.0, 0.0, 64, 57);       // radio la mitad: la mitad de lejos
        assertPeakAt(dot, 40.0, 270.0, 36, 64);     // radio el doble, hacia el oeste
    }

    private static void assertPeakAt(HeightStamp stamp, double radius, double degrees, int expectedX,
                                     int expectedZ) {
        FakeTerrain terrain = TerrainScenes.flat(128, 60, 10);
        StampBrush.Settings settings = new StampBrush.Settings(stamp, StampBrush.Mode.RAISE, radius, 20.0, 1.0,
                Math.toRadians(degrees), 10);
        TerrainJobs.stamp(terrain, settings, 64, 64, SurfacePalette.natural(), terrain, NO_CANCEL);
        int bestX = -1;
        int bestZ = -1;
        int best = Integer.MIN_VALUE;
        int changedOutside = 0;
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                int top = terrain.top(x, z);
                if (top > best) {
                    best = top;
                    bestX = x;
                    bestZ = z;
                }
                double distance = Math.hypot(x + 0.5 - 64.5, z + 0.5 - 64.5);
                if (top != 10 && distance > settings.reach() + 1) {
                    changedOutside++;
                }
            }
        }
        assertEquals(30, best, "el blanco sube la altura entera");
        assertTrue(Math.abs(bestX - expectedX) <= 1 && Math.abs(bestZ - expectedZ) <= 1,
                "radio " + radius + ", giro " + degrees + "°: cima en " + bestX + "," + bestZ + " y no en "
                        + expectedX + "," + expectedZ);
        assertEquals(0, changedOutside, "nada cambia fuera del alcance del sello");
    }

    @Test
    void builtInStampsAreValidAndMeetTheTerrainAtTheirEdge() {
        assertTrue(StampLibrary.descriptions().size() >= 15 && StampLibrary.descriptions().size() <= 20);
        for (String name : StampLibrary.descriptions().keySet()) {
            HeightStamp stamp = StampLibrary.render(name, 64);
            double maximum = 0.0;
            double minimum = 1.0;
            for (int row = 0; row < 64; row++) {
                for (int column = 0; column < 64; column++) {
                    maximum = Math.max(maximum, stamp.pixel(column, row));
                    minimum = Math.min(minimum, stamp.pixel(column, row));
                }
            }
            assertTrue(maximum - minimum > 0.3, name + " tiene relieve");
            for (int index = 0; index < 64; index++) {
                assertEquals(stamp.neutral(), stamp.pixel(index, 0), 1e-6, name + " no llega neutro al borde");
                assertEquals(stamp.neutral(), stamp.pixel(0, index), 1e-6, name + " no llega neutro al borde");
            }
        }
        assertArrayEquals(StampLibrary.render("montana", 32).values(), StampLibrary.render("montana", 32).values(),
                "los sellos de serie son deterministas");
    }

    @Test
    void sinkingStampsLowerTheTerrain() {
        FakeTerrain terrain = TerrainScenes.flat(80, 60, 30);
        TerrainJobs.stamp(terrain, new StampBrush.Settings(StampLibrary.get("dolina"), StampBrush.Mode.RAISE, 20, 10,
                1.0, 0.0, 30), 40, 40, SurfacePalette.natural(), terrain, NO_CANCEL);
        assertEquals(20, terrain.top(40, 40), "la dolina hunde aunque el modo sea elevar");
        assertEquals(30, terrain.top(5, 5));
        FakeTerrain crater = TerrainScenes.flat(80, 60, 30);
        TerrainJobs.stamp(crater, new StampBrush.Settings(StampLibrary.get("crater"), StampBrush.Mode.RAISE, 24, 10,
                1.0, 0.0, 30), 40, 40, SurfacePalette.natural(), crater, NO_CANCEL);
        assertTrue(crater.top(40, 40) < 30 && crater.top(40 + 13, 40) > 30, "fondo hundido y borde alto");
    }

    @Test
    void flattenModeMovesTowardsTheClickHeight() {
        FakeTerrain terrain = TerrainScenes.hills(80, 80, 6L);
        int base = terrain.top(40, 40);
        TerrainJobs.stamp(terrain, new StampBrush.Settings(StampLibrary.get("mesa"), StampBrush.Mode.FLATTEN, 20, 0,
                1.0, 0.0, base), 40, 40, SurfacePalette.natural(), terrain, NO_CANCEL);
        for (int z = 36; z <= 44; z++) {
            for (int x = 36; x <= 44; x++) {
                assertEquals(base, terrain.top(x, z), 1, "lo de dentro de la mesa queda a la altura del clic");
            }
        }
    }

    @Test
    void stampPngRoundTripKeepsValuesAndNeutral() throws Exception {
        HeightStamp crater = StampLibrary.render("crater", 48);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StampImages.write(crater, output);
        HeightStamp read = StampImages.read("crater", new ByteArrayInputStream(output.toByteArray()));
        assertEquals(crater.neutral(), read.neutral(), 1e-4);
        for (int row = 0; row < 48; row++) {
            for (int column = 0; column < 48; column++) {
                assertEquals(crater.pixel(column, row), read.pixel(column, row), 1.0 / 65_535.0 + 1e-6);
            }
        }
    }

    @Test
    void catalogWritesDefaultsAndPrefersFiles(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        StampCatalog catalog = new StampCatalog(directory.resolve("heightmaps"));
        assertEquals(StampLibrary.descriptions().size(), catalog.writeDefaults());
        assertEquals(0, catalog.writeDefaults(), "no pisa lo que ya hay");
        assertTrue(Files.exists(directory.resolve("heightmaps/volcan.png")));
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(8, 8,
                java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setSample(3, 3, 0, 255);
        javax.imageio.ImageIO.write(image, "png", directory.resolve("heightmaps/Mio.png").toFile());
        assertTrue(catalog.names().contains("mio"));
        HeightStamp mine = catalog.load("mio");
        assertEquals(8, mine.width());
        assertEquals(1.0, mine.pixel(3, 3), 1e-6);
        assertEquals(0.35, catalog.load("Cráter").neutral(), 1e-3, "el neutro viaja en el PNG");
        assertThrows(IllegalArgumentException.class, () -> catalog.load("../secreto"));
        assertThrows(IllegalArgumentException.class, () -> catalog.load("no_existe"));
    }

    // ------------------------------------------------------------------ paleta y escritura

    @Test
    void paletteChoosesBySlopeHeightAndWater() {
        SurfacePalette palette = SurfacePalette.parse("natural,cumbre:powder_snow@100");
        SurfacePalette.Column flat = new SurfacePalette.Column(64, 5.0, TerrainColumns.NONE, null, null);
        assertEquals("minecraft:grass_block", palette.material(0, 64, 0, 0, flat));
        assertEquals("minecraft:dirt", palette.material(0, 63, 0, 1, flat));
        assertEquals("minecraft:stone", palette.material(0, 50, 0, 14, flat));
        SurfacePalette.Column cliff = new SurfacePalette.Column(64, 75.0, TerrainColumns.NONE, null, null);
        assertTrue(palette.material(0, 64, 0, 0, cliff).matches("minecraft:(stone|andesite)"));
        SurfacePalette.Column high = new SurfacePalette.Column(130, 5.0, TerrainColumns.NONE, null, null);
        assertEquals("minecraft:powder_snow", palette.material(0, 130, 0, 0, high));
        SurfacePalette.Column river = new SurfacePalette.Column(60, 5.0, 62, null, null);
        assertTrue(palette.material(0, 60, 0, 0, river).matches("minecraft:(gravel|sand|clay)"));
        SurfacePalette.Column shore = new SurfacePalette.Column(63, 5.0, 62, null, null);
        assertEquals("minecraft:sand", palette.material(0, 63, 0, 0, shore));

        SurfacePalette keep = SurfacePalette.parse("conservar");
        assertEquals("minecraft:moss_block", keep.material(0, 64, 0, 0,
                new SurfacePalette.Column(64, 70.0, TerrainColumns.NONE, "minecraft:moss_block", "minecraft:dirt")));
        assertTrue(SurfacePalette.parse("volcánico").blockIds().contains("minecraft:magma_block"));
        assertThrows(IllegalArgumentException.class, () -> SurfacePalette.parse("lava_planet"));
        assertThrows(IllegalArgumentException.class, () -> SurfacePalette.parse("natural,pendiente:stone@120"));
        assertThrows(IllegalArgumentException.class, () -> SurfacePalette.parse("llano:pie$dra"));
        assertThrows(IllegalArgumentException.class, () -> SurfacePalette.parse("llano:stone,natural"));
    }

    @Test
    void writerFillsCutsAndCleansUp() {
        FakeTerrain terrain = TerrainScenes.flat(16, 40, 20);
        terrain.put(3, 21, 3, "minecraft:poppy");
        terrain.put(12, 21, 12, "minecraft:oak_log");
        terrain.put(12, 22, 12, "minecraft:oak_log");
        terrain.put(12, 23, 12, "minecraft:oak_leaves");
        TerrainColumns columns = TerrainColumns.read(terrain, NO_CANCEL);
        HeightField field = columns.heights();
        for (int z = 1; z <= 5; z++) {
            for (int x = 1; x <= 5; x++) {
                field.set(x, z, 25);
            }
        }
        field.set(12, 12, 16);
        TerrainWriter.write(columns, field, null, SurfacePalette.natural(), false, terrain, NO_CANCEL);
        assertEquals("minecraft:grass_block", terrain.blockId(3, 25, 3));
        assertEquals("minecraft:dirt", terrain.blockId(3, 24, 3));
        assertEquals("minecraft:stone", terrain.blockId(3, 20, 3), "la vieja hierba enterrada a 5 pasa a piedra");
        assertEquals("minecraft:air", terrain.blockId(3, 26, 3), "la amapola queda enterrada, no encima");
        assertTrue(terrain.blockId(1, 23, 3).matches("minecraft:(stone|andesite)"),
                "el lado al aire del relleno es pared de roca: " + terrain.blockId(1, 23, 3));
        assertEquals(16, terrain.top(12, 12));
        assertEquals("minecraft:grass_block", terrain.blockId(12, 16, 12), "la superficie nueva se repinta");
        assertEquals("minecraft:air", terrain.blockId(12, 22, 12), "el tronco no se queda flotando");
        assertEquals("minecraft:air", terrain.blockId(12, 23, 12));
        assertEquals("minecraft:grass_block", terrain.blockId(8, 20, 8), "lo que no cambia no se toca");
    }

    @Test
    void loweringUnderALakeKeepsTheWater() {
        FakeTerrain terrain = TerrainScenes.flat(10, 40, 20);
        for (int y = 21; y <= 24; y++) {
            terrain.put(5, y, 5, "minecraft:water");
        }
        TerrainColumns columns = TerrainColumns.read(terrain, NO_CANCEL);
        HeightField field = columns.heights();
        field.set(5, 5, 17);
        TerrainWriter.write(columns, field, null, SurfacePalette.natural(), false, terrain, NO_CANCEL);
        for (int y = 18; y <= 24; y++) {
            assertEquals("minecraft:water", terrain.blockId(5, y, 5), "agua en " + y);
        }
        assertTrue(terrain.blockId(5, 17, 5).matches("minecraft:(gravel|sand|clay)"), "lecho bajo el agua");
    }

    @Test
    void parsersReadSpanishAndEnglish() {
        assertEquals(StampBrush.Mode.LOWER, StampBrush.Mode.byId("bajar"));
        assertEquals(StampBrush.Mode.RAISE, StampBrush.Mode.LOWER.opposite());
        assertEquals(RampPlanner.Section.CONE, RampPlanner.Section.byId("cono"));
        assertEquals(RiverPlanner.Fluid.LAVA, RiverPlanner.Fluid.byId("lava"));
        assertEquals(TerrainJobs.Erosion.HYDRAULIC, TerrainJobs.Erosion.byId("hidráulica"));
        assertEquals(TerrainJobs.Erosion.THERMAL, TerrainJobs.Erosion.byId("termica"));
        assertNotEquals(RiverPlanner.preset("rio"), RiverPlanner.preset("cañón"));
        assertThrows(IllegalArgumentException.class, () -> RiverPlanner.preset("autopista"));
    }

    private static int[] tops(FakeTerrain terrain) {
        int[] tops = new int[terrain.sizeX() * terrain.sizeZ()];
        for (int z = 0; z < terrain.sizeZ(); z++) {
            for (int x = 0; x < terrain.sizeX(); x++) {
                tops[z * terrain.sizeX() + x] = terrain.top(x, z);
            }
        }
        return tops;
    }
}
