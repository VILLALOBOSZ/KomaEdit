package com.komainu.moxia.roof;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoofGeneratorTest {
    private static final int BASE = 10;
    /** Casa de 9×7 con el muro de x 0 a 8 y de z 0 a 6. */
    private static final Footprint HOUSE = Footprint.rectangle(0, 0, 8, 6, BASE);

    private static RoofResult roof(Footprint footprint, String flags) {
        return RoofGenerator.generate(footprint, RoofSettings.parse(flags), RoofWorld.AIR);
    }

    // ---------------------------------------------------------------- orientación y piezas

    @Test
    void stairsFaceTheRidgeAndHangUpsideDownUnderTheEaves() {
        RoofResult result = roof(HOUSE, "gable -p 1:1 -a 1");
        // Faldón norte (z bajo): el caballete está al sur, así que las escaleras miran al sur.
        for (int z = -1; z <= 2; z++) {
            byte top = topPiece(result, 4, z);
            assertTrue(SmoothPiece.isStairs(top), "z=" + z + ": " + SmoothPiece.describe(top));
            assertEquals(SmoothPiece.SOUTH, SmoothPiece.facing(top), "z=" + z);
            assertFalse(SmoothPiece.isTop(top));
            assertEquals(SmoothPiece.STRAIGHT, SmoothPiece.shape(top));
        }
        // Faldón sur: miran al norte.
        for (int z = 4; z <= 7; z++) {
            byte top = topPiece(result, 4, z);
            assertEquals(SmoothPiece.NORTH, SmoothPiece.facing(top), "z=" + z);
        }
        // Alero: la escalera de fuera queda a la altura de lo alto del muro y debajo cuelga otra, boca abajo,
        // mirando hacia fuera (sin muescas por debajo).
        assertEquals(BASE - 1, topLevel(result, 4, -1));
        byte under = result.pieceAt(4, BASE - 2, -1);
        assertTrue(SmoothPiece.isStairs(under), SmoothPiece.describe(under));
        assertTrue(SmoothPiece.isTop(under));
        assertEquals(SmoothPiece.NORTH, SmoothPiece.facing(under));
        byte underSouth = result.pieceAt(4, BASE - 2, 7);
        assertTrue(SmoothPiece.isTop(underSouth));
        assertEquals(SmoothPiece.SOUTH, SmoothPiece.facing(underSouth));
        // Caballete de una casa de ancho impar: bloque entero con una losa encima.
        assertEquals(SmoothPiece.slab(false), topPiece(result, 4, 3));
        assertEquals(SmoothPiece.FULL, result.pieceAt(4, topLevel(result, 4, 3) - 1, 3));
        assertEquals(0, result.mismatches());
    }

    @Test
    void hipCornersUseOuterStairsOnTopAndInnerStairsBelow() {
        RoofResult result = roof(HOUSE, "hip -p 1:1 -a 1");
        // Esquina del alero (fuera de la casa, al noroeste): arriba escalera exterior con el escalón alto hacia
        // dentro (sureste); debajo, escalera colgada interior.
        byte corner = result.pieceAt(-1, BASE - 1, -1);
        assertTrue(SmoothPiece.isStairs(corner), SmoothPiece.describe(corner));
        assertTrue(SmoothPiece.shape(corner) == SmoothPiece.OUTER_LEFT
                || SmoothPiece.shape(corner) == SmoothPiece.OUTER_RIGHT);
        assertEquals(0x33 | 1 << SmoothPiece.octant(1, 1, 1), SmoothPiece.mask(corner));
        byte below = result.pieceAt(-1, BASE - 2, -1);
        assertTrue(SmoothPiece.isTop(below), SmoothPiece.describe(below));
        assertTrue(SmoothPiece.shape(below) == SmoothPiece.INNER_LEFT
                || SmoothPiece.shape(below) == SmoothPiece.INNER_RIGHT);
        assertEquals(0xCC | 1 << SmoothPiece.octant(0, 0, 0) | 1 << SmoothPiece.octant(1, 0, 0)
                | 1 << SmoothPiece.octant(0, 0, 1), SmoothPiece.mask(below));
        // Las cuatro esquinas, iguales salvo el giro.
        for (int[] cell : new int[][]{{9, -1}, {-1, 7}, {9, 7}}) {
            byte other = result.pieceAt(cell[0], BASE - 1, cell[1]);
            assertTrue(SmoothPiece.shape(other) >= SmoothPiece.OUTER_LEFT, SmoothPiece.describe(other));
        }
        assertEquals(0, result.mismatches());
    }

    @Test
    void valleysOfCompoundPlansUseInnerStairs() {
        for (String plan : List.of("L", "T", "U")) {
            for (String type : List.of("gable", "hip", "gablet")) {
                RoofResult result = roof(RoofTestPlans.plan(plan, BASE), type);
                int inner = 0;
                int outer = 0;
                for (byte piece : result.pieces()) {
                    if (SmoothPiece.isStairs(piece) && !SmoothPiece.isTop(piece)) {
                        int shape = SmoothPiece.shape(piece);
                        inner += shape == SmoothPiece.INNER_LEFT || shape == SmoothPiece.INNER_RIGHT ? 1 : 0;
                        outer += shape >= SmoothPiece.OUTER_LEFT ? 1 : 0;
                    }
                }
                assertTrue(inner > 0, plan + " " + type + ": sin limahoyas");
                // La T a dos aguas no tiene limatesas: todas sus esquinas de fuera son remates de hastial.
                if (!(plan.equals("T") && type.equals("gable"))) {
                    assertTrue(outer > 0, plan + " " + type + ": sin limatesas");
                }
                assertEquals(0, result.mismatches(), plan + " " + type);
            }
        }
    }

    @Test
    void shallowRoofsAlternateSlabsAndBlocksByFractionalHeight() {
        RoofResult result = roof(HOUSE, "gable -p 1:2 -a 1");
        // Alero a la altura de lo alto del muro: bloque entero; sobre el muro, losa abajo; luego bloque y losa.
        assertEquals(SmoothPiece.FULL, topPiece(result, 4, -1));
        assertEquals(BASE - 1, topLevel(result, 4, -1));
        assertEquals(SmoothPiece.slab(false), topPiece(result, 4, 0));
        assertEquals(BASE, topLevel(result, 4, 0));
        assertEquals(SmoothPiece.FULL, topPiece(result, 4, 1));
        assertEquals(SmoothPiece.slab(false), topPiece(result, 4, 2));
        assertEquals(BASE + 1, topLevel(result, 4, 2));
        // Por debajo, losas superiores donde la cara de abajo queda a media altura.
        boolean upperSlab = false;
        for (byte piece : result.pieces()) {
            upperSlab |= piece == SmoothPiece.slab(true);
        }
        assertTrue(upperSlab);
        assertEquals(0, result.mismatches());
    }

    @Test
    void steepRoofsPutStairsOnColumns() {
        RoofResult result = roof(HOUSE, "gable -p 2:1");
        for (int z = 0; z <= 2; z++) {
            byte top = topPiece(result, 4, z);
            assertTrue(SmoothPiece.isStairs(top), SmoothPiece.describe(top));
            assertEquals(SmoothPiece.SOUTH, SmoothPiece.facing(top));
            assertEquals(SmoothPiece.FULL, result.pieceAt(4, topLevel(result, 4, z) - 1, z));
        }
        assertEquals(2, topLevel(result, 4, 1) - topLevel(result, 4, 0));
    }

    // ---------------------------------------------------------------- simetría y determinismo

    @Test
    void roofsAreSymmetric() {
        for (String flags : List.of("gable", "hip", "mansard", "gablet", "pagoda -a 2", "gable -p 1:2", "hip -g 2",
                "gable -b 2", "flat", "dome", "cone", "spire", "spire -lados 4")) {
            // En la pagoda las puntas levantadas no caben exactas en piezas y el reparto de escaleras de las esquinas
            // depende del orden: ahí se exige simetría de posiciones, no de cada octante.
            boolean exact = !flags.startsWith("pagoda");
            boolean round = flags.startsWith("dome") || flags.startsWith("cone") || flags.startsWith("spire");
            Footprint footprint = round ? Footprint.rectangle(0, 0, 10, 10, BASE)
                    : Footprint.rectangle(0, 0, 16, 10, BASE);
            Map<Long, Integer> masks = masks(roof(footprint, flags));
            int mirrorX = footprint.minX() + footprint.maxX();
            int mirrorZ = footprint.minZ() + footprint.maxZ();
            for (Map.Entry<Long, Integer> entry : masks.entrySet()) {
                int x = PackedBlockPlan.unpackX(entry.getKey());
                int y = PackedBlockPlan.unpackY(entry.getKey());
                int z = PackedBlockPlan.unpackZ(entry.getKey());
                Integer mirroredX = masks.get(PackedBlockPlan.pack(mirrorX - x, y, z));
                Integer mirroredZ = masks.get(PackedBlockPlan.pack(x, y, mirrorZ - z));
                if (!exact) {
                    assertTrue(mirroredX != null && mirroredZ != null, flags + " en " + x + "," + y + "," + z);
                    continue;
                }
                assertEquals(mirror(entry.getValue(), true), mirroredX,
                        flags + ": espejo este-oeste en " + x + "," + y + "," + z);
                assertEquals(mirror(entry.getValue(), false), mirroredZ,
                        flags + ": espejo norte-sur en " + x + "," + y + "," + z);
            }
        }
    }

    @Test
    void squareHipRoofTurnsIntoItself() {
        Footprint square = Footprint.rectangle(0, 0, 10, 10, BASE);
        for (String flags : List.of("hip", "spire -lados 4 -p 2:1", "mansard")) {
            Map<Long, Integer> masks = masks(roof(square, flags));
            for (Map.Entry<Long, Integer> entry : masks.entrySet()) {
                int x = PackedBlockPlan.unpackX(entry.getKey());
                int y = PackedBlockPlan.unpackY(entry.getKey());
                int z = PackedBlockPlan.unpackZ(entry.getKey());
                // Cuarto de vuelta alrededor del centro (5,5): (x, z) → (10 − z, x).
                assertEquals(rotate(entry.getValue()), masks.get(PackedBlockPlan.pack(10 - z, y, x)),
                        flags + " en " + x + "," + y + "," + z);
            }
        }
    }

    @Test
    void sameInputGivesTheSameRoof() {
        for (String flags : List.of("gable -b 2 -c stone", "hip -p 30", "cone -p 2:1", "dome -h 6", "pagoda -a 2")) {
            Footprint footprint = RoofTestPlans.plan("rect", BASE);
            RoofResult first = roof(footprint, flags);
            RoofResult second = roof(footprint, flags);
            assertArrayEquals(first.positions(), second.positions(), flags);
            assertArrayEquals(first.pieces(), second.pieces(), flags);
            assertArrayEquals(first.materials(), second.materials(), flags);
        }
    }

    // ---------------------------------------------------------------- plantas sin huecos

    @Test
    void compoundPlansAreCoveredWithoutHoles() {
        for (String plan : List.of("L", "T", "U", "rect")) {
            Footprint footprint = RoofTestPlans.plan(plan, BASE);
            for (String flags : List.of("gable", "hip", "gablet", "mansard", "pagoda", "gable -p 1:2",
                    "gable -p 2:1", "hip -p 30", "shed", "flat")) {
                RoofResult result = roof(footprint, flags);
                int overhang = RoofSettings.parse(flags).overhang();
                for (int z = footprint.minZ() - 1; z <= footprint.maxZ() + 1; z++) {
                    for (int x = footprint.minX() - 1; x <= footprint.maxX() + 1; x++) {
                        boolean extended = false;
                        for (int dz = -overhang; dz <= overhang && !extended; dz++) {
                            for (int dx = -overhang; dx <= overhang && !extended; dx++) {
                                extended = footprint.contains(x + dx, z + dz);
                            }
                        }
                        if (extended) {
                            assertTrue(column(result, x, z), plan + " " + flags + ": columna vacía en " + x + "," + z);
                        }
                    }
                }
                assertTrue(atticSealed(result, footprint), plan + " " + flags + ": el desván se ve desde fuera");
            }
        }
    }

    @Test
    void roundRoofsHaveNoGaps() {
        Footprint tower = Footprint.rectangle(0, 0, 10, 10, BASE);
        for (String flags : List.of("cone", "cone -p 1:1", "spire", "spire -lados 6", "dome", "dome -h 4",
                "cone -g 2")) {
            assertTrue(atticSealed(roof(tower, flags), tower), flags);
        }
    }

    @Test
    void dormersKeepTheAtticSealed() {
        Footprint house = Footprint.rectangle(0, 0, 16, 10, BASE);
        RoofResult result = roof(house, "gable -b 2");
        assertEquals(4, result.dormers());
        assertTrue(result.count(RoofResult.WINDOW_X) >= 4);
        assertTrue(atticSealed(result, house));
        RoofResult mansard = roof(house, "mansard -b 2");
        assertEquals(4, mansard.dormers());
        assertTrue(atticSealed(mansard, house));
    }

    // ---------------------------------------------------------------- hastiales, muros y materiales

    @Test
    void gablesAreClosedWithTheWallMaterialUnlessOpen() {
        RoofResult closed = roof(HOUSE, "gable");
        // Hastial oeste (x = 0): triángulo de muro bajo el tejado.
        assertEquals(RoofResult.WALL, closed.materialAt(0, BASE, 3));
        assertEquals(RoofResult.WALL, closed.materialAt(0, BASE + 1, 3));
        assertEquals(RoofResult.WALL, closed.materialAt(8, BASE, 3));
        assertEquals(-1, closed.materialAt(4, BASE, 3), "el desván queda hueco");
        RoofResult open = roof(HOUSE, "gable -abierto");
        assertEquals(0, open.count(RoofResult.WALL));
        RoofResult hip = roof(HOUSE, "hip");
        assertEquals(0, hip.count(RoofResult.WALL), "un tejado a cuatro aguas no tiene hastiales");
    }

    @Test
    void flatRoofHasItsParapet() {
        RoofResult result = roof(HOUSE, "flat -pretil 2");
        for (int x = 0; x <= 8; x++) {
            assertEquals(SmoothPiece.FULL, result.pieceAt(x, BASE, 3));
        }
        assertEquals(RoofResult.WALL, result.materialAt(0, BASE + 1, 3));
        assertEquals(RoofResult.WALL, result.materialAt(0, BASE + 2, 3));
        assertEquals(-1, result.materialAt(0, BASE + 3, 3));
        assertEquals(-1, result.materialAt(4, BASE + 1, 3));
    }

    @Test
    void ridgeMaterialGoesOnTheRidgeOnly() {
        RoofResult result = roof(HOUSE, "gable -c dark_oak_planks");
        for (int x = -1; x <= 9; x++) {
            assertEquals(RoofResult.RIDGE, result.materialAt(x, topLevel(result, x, 3), 3), "x=" + x);
        }
        assertEquals(11, result.count(RoofResult.RIDGE));
    }

    @Test
    void roofNeverReplacesExistingBlocksUnlessAsked() {
        FakeWorld world = new FakeWorld();
        // Muros de 4 de alto, hasta y = BASE − 1.
        for (int y = BASE - 4; y < BASE; y++) {
            for (int z = 0; z <= 6; z++) {
                for (int x = 0; x <= 8; x++) {
                    if (HOUSE.isBoundary(x, z)) {
                        world.put(x, y, z, "minecraft:stone_bricks");
                    }
                }
            }
        }
        RoofResult kept = RoofGenerator.generate(HOUSE, RoofSettings.parse("gable"), world);
        for (long key : kept.positions()) {
            assertTrue(world.air(PackedBlockPlan.unpackX(key), PackedBlockPlan.unpackY(key),
                    PackedBlockPlan.unpackZ(key)));
        }
        RoofResult replaced = RoofGenerator.generate(HOUSE, RoofSettings.parse("gable -r"), world);
        assertTrue(replaced.size() > kept.size());
        assertTrue(replaced.indexOf(4, BASE - 1, 0) >= 0, "con -r la escalera colgada pisa lo alto del muro");
        assertTrue(kept.indexOf(4, BASE - 1, 0) < 0);
    }

    @Test
    void footprintIsDetectedFromTheWalls() {
        FakeWorld world = new FakeWorld();
        Footprint plan = RoofTestPlans.plan("L", 0);
        for (int y = 60; y <= 64; y++) {
            for (int z = plan.minZ(); z <= plan.maxZ(); z++) {
                for (int x = plan.minX(); x <= plan.maxX(); x++) {
                    if (plan.isBoundary(x, z)) {
                        world.put(x, y, z, y == 64 ? "minecraft:stone_bricks" : "minecraft:cobblestone");
                    }
                }
            }
        }
        // Una puerta abajo no cuenta: solo las dos capas de arriba hacen de muro.
        world.remove(0, 60, 3);
        world.remove(0, 61, 3);
        Footprint.Detection detection = Footprint.detect(world, -3, 58, -3, 20, 64, 19);
        Footprint found = detection.footprint();
        assertEquals(65, found.baseY());
        assertEquals("minecraft:stone_bricks", detection.wallMaterial());
        assertEquals(plan.area(), found.area());
        for (int z = plan.minZ(); z <= plan.maxZ(); z++) {
            for (int x = plan.minX(); x <= plan.maxX(); x++) {
                assertEquals(plan.contains(x, z), found.contains(x, z), x + "," + z);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Footprint.detect(new FakeWorld(), 0, 0, 0, 5, 5, 5));
    }

    // ---------------------------------------------------------------- ajustes

    @Test
    void parsesPitchesAndFlags() {
        assertEquals(0.5, RoofSettings.slope("1:2"), 1.0E-9);
        assertEquals(2.0, RoofSettings.slope("2:1"), 1.0E-9);
        assertEquals(1.0, RoofSettings.slope("45"), 1.0E-9);
        assertEquals(Math.tan(Math.toRadians(30)), RoofSettings.slope("30°"), 1.0E-9);
        assertEquals("1:2", RoofSettings.describeSlope(0.5));
        assertEquals("2:1", RoofSettings.describeSlope(2.0));
        RoofSettings settings = RoofSettings.parse("dosaguas oak_planks -p 1:2 -a 2 -g 2 -c stone -b 1 -m bricks "
                + "-eje z -d -r -e");
        assertEquals(RoofType.GABLE, settings.type());
        assertEquals("oak_planks", settings.material());
        assertEquals(0.5, settings.slope(), 1.0E-9);
        assertEquals(2, settings.overhang());
        assertEquals(2, settings.thickness());
        assertEquals("stone", settings.ridgeMaterial());
        assertEquals(1, settings.dormers());
        assertEquals("bricks", settings.wallMaterial());
        assertEquals(RoofSettings.Axis.Z, settings.axis());
        assertTrue(settings.detectWalls() && settings.replace() && settings.estimate());
        assertEquals(RoofType.FLAT, RoofSettings.parse("plano").type());
        assertEquals(RoofType.GABLET, RoofSettings.parse("holandés").type());
        assertEquals(0, RoofSettings.parse("dome").overhang());
        assertThrows(IllegalArgumentException.class, () -> RoofSettings.parse("gable -p 0:1"));
        assertThrows(IllegalArgumentException.class, () -> RoofSettings.parse("gable -p 89"));
        assertThrows(IllegalArgumentException.class, () -> RoofSettings.parse("dome -b 2"));
        assertThrows(IllegalArgumentException.class, () -> RoofSettings.parse("tejadito"));
        assertThrows(IllegalArgumentException.class, () -> RoofSettings.parse("gable stone dirt"));
    }

    @Test
    void axisFlagChoosesWhereTheRidgeRuns() {
        RoofResult alongX = roof(HOUSE, "gable");
        RoofResult alongZ = roof(HOUSE, "gable -eje z");
        assertEquals(RoofResult.WALL, alongX.materialAt(0, BASE, 3));
        assertEquals(RoofResult.WALL, alongZ.materialAt(4, BASE, 0));
        assertNotEquals(RoofResult.WALL, alongZ.materialAt(0, BASE, 3), "con el caballete en z, el oeste es alero");
    }

    // ---------------------------------------------------------------- utilidades

    private static int topLevel(RoofResult result, int x, int z) {
        for (int y = result.roofMaxY() + 8; y >= result.roofMinY() - 8; y--) {
            int index = result.indexOf(x, y, z);
            if (index >= 0 && result.materials()[index] <= RoofResult.RIDGE) {
                return y;
            }
        }
        throw new AssertionError("sin tejado en " + x + "," + z);
    }

    private static byte topPiece(RoofResult result, int x, int z) {
        return result.pieceAt(x, topLevel(result, x, z), z);
    }

    private static boolean column(RoofResult result, int x, int z) {
        for (int y = result.roofMinY() - 4; y <= result.roofMaxY() + 4; y++) {
            if (result.indexOf(x, y, z) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static Map<Long, Integer> masks(RoofResult result) {
        Map<Long, Integer> masks = new HashMap<>();
        for (int index = 0; index < result.size(); index++) {
            masks.put(result.positions()[index], SmoothPiece.mask(result.pieces()[index]) * 8
                    + result.materials()[index]);
        }
        return masks;
    }

    /** Máscara (con el material en los 3 bits bajos) reflejada en x o en z. */
    private static int mirror(int value, boolean alongX) {
        int mask = value >> 3;
        int mirrored = 0;
        for (int octant = 0; octant < 8; octant++) {
            if ((mask & 1 << octant) != 0) {
                int x = octant & 1;
                int y = octant >> 1 & 1;
                int z = octant >> 2 & 1;
                mirrored |= 1 << (alongX ? SmoothPiece.octant(1 - x, y, z) : SmoothPiece.octant(x, y, 1 - z));
            }
        }
        return mirrored << 3 | (value & 7);
    }

    /** Cuarto de vuelta (x, z) → (−z, x) de una máscara con material. */
    private static int rotate(int value) {
        int mask = value >> 3;
        int rotated = 0;
        for (int octant = 0; octant < 8; octant++) {
            if ((mask & 1 << octant) != 0) {
                int x = octant & 1;
                int y = octant >> 1 & 1;
                int z = octant >> 2 & 1;
                rotated |= 1 << SmoothPiece.octant(1 - z, y, x);
            }
        }
        return rotated << 3 | (value & 7);
    }

    /**
     * Desde el cielo y desde fuera de la casa, por caras y sin cruzar el tejado ni sus muros, ¿se llega al desván
     * (lo que hay justo encima de los muros por dentro)?
     */
    static boolean atticSealed(RoofResult result, Footprint footprint) {
        int minX = footprint.minX() - 12;
        int maxX = footprint.maxX() + 12;
        int minZ = footprint.minZ() - 12;
        int maxZ = footprint.maxZ() + 12;
        int base = footprint.baseY();
        int top = result.roofMaxY() + 2;
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                long key = PackedBlockPlan.pack(x, top, z);
                seen.add(key);
                queue.enqueue(key);
            }
        }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            long key = queue.dequeueLong();
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            if (y == base && footprint.contains(x, z) && !footprint.isBoundary(x, z)) {
                return false;
            }
            for (int[] step : steps) {
                int nx = x + step[0];
                int ny = y + step[1];
                int nz = z + step[2];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ || ny < base || ny > top) {
                    continue;
                }
                long next = PackedBlockPlan.pack(nx, ny, nz);
                // Dentro de la casa, por debajo de lo alto de los muros, no se pasa: ahí están las paredes.
                if (result.indexOf(nx, ny, nz) >= 0 || seen.contains(next)) {
                    continue;
                }
                seen.add(next);
                queue.enqueue(next);
            }
        }
        return true;
    }

    /** Mundo de pruebas: bloques macizos con su identificador; lo demás es aire. */
    static final class FakeWorld implements RoofWorld {
        private final Map<Long, String> blocks = new HashMap<>();

        void put(int x, int y, int z, String id) {
            blocks.put(PackedBlockPlan.pack(x, y, z), id);
        }

        void remove(int x, int y, int z) {
            blocks.remove(PackedBlockPlan.pack(x, y, z));
        }

        @Override
        public boolean air(int x, int y, int z) {
            return !blocks.containsKey(PackedBlockPlan.pack(x, y, z));
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return !air(x, y, z);
        }

        @Override
        public String blockId(int x, int y, int z) {
            return blocks.get(PackedBlockPlan.pack(x, y, z));
        }

        @Override
        public int stateId(int x, int y, int z) {
            return air(x, y, z) ? 0 : 1;
        }
    }
}
