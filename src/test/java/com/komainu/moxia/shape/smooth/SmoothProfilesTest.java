package com.komainu.moxia.shape.smooth;

import com.komainu.moxia.deform.SmoothProfile;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Perfiles de smoothblocks con nombre (ezEdits): piezas de cada uno, cobertura y parámetros de {@code -w}. */
class SmoothProfilesTest {
    // ------------------------------------------------------------------ piezas nuevas

    @Test
    void layersFillFromTheGroundInEighths() {
        for (int count = 1; count <= SmoothPiece.MAX_LAYERS; count++) {
            byte piece = SmoothPiece.layers(count);
            assertTrue(SmoothPiece.isValid(piece) && SmoothPiece.isValid(SmoothPiece.added(piece)));
            assertTrue(SmoothPiece.isLayers(piece));
            assertFalse(SmoothPiece.isSlab(piece) || SmoothPiece.isStairs(piece) || SmoothPiece.isWall(piece));
            assertFalse(SmoothPiece.isTop(piece), "las capas siempre apoyan en el suelo");
            assertEquals(count, SmoothPiece.layerCount(SmoothPiece.added(piece)));
            assertEquals(PieceVariant.LAYER, SmoothPiece.variant(piece));
            double total = 0.0;
            for (int octant = 0; octant < 8; octant++) {
                total += SmoothPiece.fill(piece, octant);
            }
            assertEquals(count, total, 1.0E-12, "cada capa llena un octavo del bloque");
            assertEquals(2 * count, SmoothPiece.box(piece)[4], "altura en dieciseisavos");
        }
        assertEquals(0, SmoothPiece.mask(SmoothPiece.layers(3)), "tres capas no tapan ningún octante entero");
        assertEquals(0x33, SmoothPiece.mask(SmoothPiece.layers(4)));
        assertEquals(0x33, SmoothPiece.mask(SmoothPiece.layers(7)));
        assertThrows(IllegalArgumentException.class, () -> SmoothPiece.layers(0));
        assertThrows(IllegalArgumentException.class, () -> SmoothPiece.layers(8));
        assertEquals("5 capas", SmoothPiece.describe(SmoothPiece.layers(5)));
    }

    @Test
    void wallIsTheCentralColumn() {
        byte wall = SmoothPiece.wall();
        assertTrue(SmoothPiece.isWall(wall) && SmoothPiece.isValid(wall) && SmoothPiece.isAdded(SmoothPiece.added(wall)));
        assertEquals(PieceVariant.WALL, SmoothPiece.variant(wall));
        assertEquals(0, SmoothPiece.mask(wall));
        for (int octant = 0; octant < 8; octant++) {
            assertEquals(SmoothPiece.WALL_FILL, SmoothPiece.fill(wall, octant));
        }
        assertEquals(java.util.List.of(4, 0, 4, 12, 16, 12),
                Arrays.stream(SmoothPiece.box(wall)).boxed().toList());
        assertNull(SmoothPiece.box(SmoothPiece.slab(false)), "losas y escaleras se dibujan por octantes");
        assertEquals("muro", SmoothPiece.describe(wall));
    }

    @Test
    void everyByteIsEitherAValidPieceOrRejected() {
        Set<Byte> seen = new HashSet<>();
        int valid = 0;
        for (int code = -128; code < 128; code++) {
            byte piece = (byte) code;
            if (!SmoothPiece.isValid(piece)) {
                continue;
            }
            valid++;
            byte base = (byte) (piece & 0x7F);
            if (seen.add(base) && piece != SmoothPiece.FULL) {
                assertTrue(SmoothPiece.variant(piece) != null, "pieza sin variante: " + code);
            }
        }
        // Bloque entero + (2 losas + 40 escaleras + 7 capas + 1 muro + 16 paneles) × (normal, añadida).
        assertEquals(1 + 66 * 2, valid);
    }

    // ------------------------------------------------------------------ perfiles

    @Test
    void eachProfileOffersItsOwnPieces() {
        assertEquals(0, SmoothMode.NONE.candidates().length);
        assertTrue(allMatch(SmoothMode.SLABS, SmoothPiece::isSlab));
        assertTrue(allMatch(SmoothMode.LAYERS, SmoothPiece::isLayers));
        assertEquals(7, SmoothMode.LAYERS.candidates().length);
        assertEquals(10, SmoothMode.STAIRS.candidates().length, "2 losas + 8 escaleras rectas");
        assertEquals(10, SmoothMode.STAIRS_2D.candidates().length);
        assertEquals(11, SmoothMode.WALLS.candidates().length, "SSW añade el muro");
        assertEquals(1, SmoothMode.PANES.candidates().length, "el panel se elige sin conexiones");
        assertTrue(SmoothMode.STAIRS.solvesCorners() && SmoothMode.WALLS.solvesCorners());
        assertFalse(SmoothMode.STAIRS_2D.solvesCorners() || SmoothMode.SLABS.solvesCorners());
        for (SmoothMode mode : SmoothMode.values()) {
            for (byte piece : mode.candidates()) {
                assertTrue(mode.uses(SmoothPiece.variant(piece)), mode + " ofrece " + SmoothPiece.describe(piece));
                if (SmoothPiece.isStairs(piece)) {
                    assertEquals(SmoothPiece.STRAIGHT, SmoothPiece.shape(piece));
                }
            }
        }
    }

    @Test
    void profileOrdinalsStayStableForSavedRecipes() {
        // Las recetas guardan el ordinal: 0 = sin piezas, 1 = losas, 2 = losas y escaleras. Los nuevos, al final.
        assertEquals(0, SmoothMode.NONE.ordinal());
        assertEquals(1, SmoothMode.SLABS.ordinal());
        assertEquals(2, SmoothMode.STAIRS.ordinal());
        assertEquals(5, SmoothMode.WALLS.ordinal());
        assertEquals(SmoothMode.PANES, SmoothMode.byIndex(SmoothMode.values().length - 1));
    }

    @Test
    void namesFollowEzEditsAndKomaEditAliases() {
        assertEquals(SmoothMode.SLABS, SmoothMode.byId("Slabs"));
        assertEquals(SmoothMode.SLABS, SmoothMode.byId("SlabsOnly"));
        assertEquals(SmoothMode.STAIRS, SmoothMode.byId("SlabsAndStairs"));
        assertEquals(SmoothMode.STAIRS, SmoothMode.byId("stairs"));
        assertEquals(SmoothMode.STAIRS_2D, SmoothMode.byId("SlabsAndStairs2D"));
        assertEquals(SmoothMode.STAIRS_2D, SmoothMode.byId("sas2d"));
        assertEquals(SmoothMode.LAYERS, SmoothMode.byId("Layers"));
        assertEquals(SmoothMode.LAYERS, SmoothMode.byId("capas"));
        assertEquals(SmoothMode.WALLS, SmoothMode.byId("SSW"));
        assertEquals(SmoothMode.WALLS, SmoothMode.byId("muros"));
        assertEquals(SmoothMode.NONE, SmoothMode.byId("none"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> SmoothMode.byId("rampa"));
        assertTrue(error.getMessage().contains("SlabsAndStairs2D"), error.getMessage());
    }

    @Test
    void parsesCoverageAndMaterialOverrides() {
        SmoothProfile plain = SmoothProfile.parse("SlabsAndStairs");
        assertEquals(SmoothMode.STAIRS, plain.mode());
        assertEquals(PieceFit.DEFAULT_COVERAGE, plain.coverage());
        assertTrue(plain.materials().isEmpty());

        SmoothProfile ssw = SmoothProfile.parse("SSW(C:0.25)");
        assertEquals(SmoothMode.WALLS, ssw.mode());
        assertEquals(0.25, ssw.coverage());

        SmoothProfile slabs = SmoothProfile.parse("Slabs(Slab:acacia)");
        assertEquals("acacia", slabs.materials().get(PieceVariant.SLAB));
        assertNull(slabs.materials().get(PieceVariant.STAIR));

        SmoothProfile both = SmoothProfile.parse("SlabsAndStairs( Slab : oak , Stair:minecraft:spruce_stairs, C:0.9 )");
        assertEquals("oak", both.materials().get(PieceVariant.SLAB));
        assertEquals("minecraft:spruce_stairs", both.materials().get(PieceVariant.STAIR),
                "el material puede llevar su espacio de nombres");
        assertEquals(0.9, both.coverage());
        assertEquals("SlabsAndStairs(C:0.9,Slab:oak,Stair:minecraft:spruce_stairs)", both.toString());
        assertEquals(both, SmoothProfile.parse(both.toString()), "toString se vuelve a leer igual");

        assertEquals("snow", SmoothProfile.parse("Layers(Layer:snow)").materials().get(PieceVariant.LAYER));
        assertEquals("cobblestone", SmoothProfile.parse("SSW(Wall:cobblestone)").materials().get(PieceVariant.WALL));
        assertEquals("oak", SmoothProfile.parse("escaleras(losa:oak)").materials().get(PieceVariant.SLAB),
                "también con las claves en español");
    }

    @Test
    void legacyColonCoverageStillWorks() {
        SmoothProfile legacy = SmoothProfile.parse("stairs:0.7");
        assertEquals(SmoothMode.STAIRS, legacy.mode());
        assertEquals(0.7, legacy.coverage());
        assertTrue(SmoothProfile.statesCoverage("stairs:0.7"));
        assertTrue(SmoothProfile.statesCoverage("SSW(Slab:oak, C:0.2)"));
        assertFalse(SmoothProfile.statesCoverage("Slabs(Slab:acacia)"));
        assertFalse(SmoothProfile.statesCoverage("Slabs"));
        assertEquals(SmoothProfile.NONE, SmoothProfile.parse(" "));
    }

    @Test
    void rejectsMistakesWithUsefulMessages() {
        assertThrows(IllegalArgumentException.class, () -> SmoothProfile.parse("Slabs(C:2)"));
        assertThrows(IllegalArgumentException.class, () -> SmoothProfile.parse("Slabs(C:mucho)"));
        assertThrows(IllegalArgumentException.class, () -> SmoothProfile.parse("Slabs(Slab:)"));
        assertThrows(IllegalArgumentException.class, () -> SmoothProfile.parse("Slabs(Slab:oak,Slab:birch)"));
        IllegalArgumentException unused = assertThrows(IllegalArgumentException.class,
                () -> SmoothProfile.parse("Slabs(Stair:oak)"));
        assertTrue(unused.getMessage().contains("Slabs no usa Stair"), unused.getMessage());
        assertThrows(IllegalArgumentException.class, () -> SmoothProfile.parse("Layers(Wall:stone)"));
        assertThrows(IllegalArgumentException.class, () -> SmoothProfile.parse("SSW(Cosa:1)"));
    }

    @Test
    void joinsProfilesWrittenWithSpacesAfterTheCommas() {
        String[] words = {"-w", "SlabsAndStairs(Slab:oak,", "Stair:spruce,", "C:0.5)", "stone"};
        assertEquals(2, SmoothProfile.extraWords(words, 1));
        assertEquals(0, SmoothProfile.extraWords(new String[]{"Slabs(C:0.5)"}, 0));
        assertEquals(0, SmoothProfile.extraWords(new String[]{"Slabs"}, 0));
    }

    // ------------------------------------------------------------------ elección por perfil

    @Test
    void layersFollowTheHeightOfTheGround() {
        for (int eighths = 1; eighths <= 7; eighths++) {
            double height = eighths / 8.0;
            byte piece = PieceFit.choose(ground(height), false, SmoothMode.LAYERS, 0.5);
            assertTrue(SmoothPiece.isLayers(piece), "altura " + height + " → " + SmoothPiece.describe(piece));
            assertEquals(eighths, SmoothPiece.layerCount(piece), "altura " + height);
            assertTrue(SmoothPiece.isAdded(piece));
        }
        assertEquals(SmoothPiece.EMPTY, PieceFit.choose(ground(0.02), false, SmoothMode.LAYERS, 0.5));
        assertEquals(SmoothPiece.FULL, PieceFit.choose(ground(1.0), true, SmoothMode.LAYERS, 1.0),
                "un bloque lleno no se rebaja ni con cobertura 1");
        // Un techo (lleno arriba) no admite capas: se queda como estaba.
        double[] ceiling = new double[8];
        for (int octant = 0; octant < 8; octant++) {
            ceiling[octant] = (octant >> 1 & 1) == 1 ? 1.0 : 0.0;
        }
        assertEquals(SmoothPiece.EMPTY, PieceFit.choose(ceiling, false, SmoothMode.LAYERS, 0.5));
    }

    @Test
    void thinColumnsBecomeWallsOnlyWithSsw() {
        double[] column = new double[8];
        Arrays.fill(column, 0.28);
        assertEquals(SmoothPiece.wall(), PieceFit.choose(column, true, SmoothMode.WALLS, 0.5));
        assertTrue(SmoothPiece.isAdded(PieceFit.choose(column, false, SmoothMode.WALLS, 0.5)));
        assertFalse(SmoothPiece.isWall(PieceFit.choose(column, true, SmoothMode.STAIRS, 0.5)));
        // Una losa sigue siendo losa con SSW.
        double[] slab = new double[8];
        for (int octant = 0; octant < 8; octant++) {
            slab[octant] = (octant >> 1 & 1) == 0 ? 1.0 : 0.0;
        }
        assertEquals(SmoothPiece.slab(false), PieceFit.choose(slab, true, SmoothMode.WALLS, 0.5));
    }

    @Test
    void coverageGoesFromFullBlocksToEveryPossiblePiece() {
        // Rampa suave: abajo lleno y arriba el 75 % al norte y el 50 % al sur. El bloque entero y la escalera
        // fallan lo mismo: con C:0.5 se queda el bloque y con C:1 se pone la escalera.
        double[] ramp = new double[8];
        for (int octant = 0; octant < 8; octant++) {
            boolean top = (octant >> 1 & 1) == 1;
            boolean north = (octant >> 2 & 1) == 0;
            ramp[octant] = !top ? 1.0 : north ? 0.75 : 0.5;
        }
        for (SmoothMode mode : SmoothMode.values()) {
            assertEquals(SmoothPiece.FULL, PieceFit.choose(ramp, true, mode, 0.0), mode + " con C:0");
        }
        assertEquals(SmoothPiece.FULL, PieceFit.choose(ramp, true, SmoothMode.STAIRS, 0.5));
        byte eager = PieceFit.choose(ramp, true, SmoothMode.STAIRS, 1.0);
        assertTrue(SmoothPiece.isStairs(eager), "con C:1 se pone la escalera donde puede: "
                + SmoothPiece.describe(eager));
        // Contar piezas en una rejilla de ocupaciones: crece con la cobertura y nunca baja.
        int previous = -1;
        for (int step = 0; step <= 10; step++) {
            int count = piecesOnGrid(SmoothMode.WALLS, step / 10.0);
            assertTrue(count >= previous, "C:" + step / 10.0 + " → " + count + " < " + previous);
            previous = count;
        }
        assertEquals(0, piecesOnGrid(SmoothMode.WALLS, 0.0));
    }

    // ------------------------------------------------------------------ esquinas en 3D

    @Test
    void solverChoosesCornersThatMinecraftWillKeep() {
        // Esquina exterior de una pirámide escalonada: dos rampas que suben hacia el noroeste. En 2D cada bloque
        // elige su escalera recta; en 3D el bloque de la esquina toma la orientación que hace que Minecraft le
        // ponga la esquina exterior, y el error total baja.
        double total2d = solveCorner(SmoothMode.STAIRS_2D);
        double total3d = solveCorner(SmoothMode.STAIRS);
        assertTrue(total3d < total2d - 0.5, "3D " + total3d + " frente a 2D " + total2d);
        assertEquals(0.0, total3d, 1.0E-9, "en 3D la meseta encaja sin error");
    }

    @Test
    void solverKeepsSlabsAndStraightRunsUntouched() {
        PieceSolver solver = new PieceSolver(SmoothMode.STAIRS, 0.5);
        double[] slope = new double[8];
        for (int octant = 0; octant < 8; octant++) {
            slope[octant] = (octant >> 1 & 1) == 0 || (octant >> 2 & 1) == 0 ? 1.0 : 0.0;
        }
        for (int x = 0; x < 6; x++) {
            solver.put(com.komainu.moxia.edit.PackedBlockPlan.pack(x, 0, 0), solver.choose(slope, true), slope);
        }
        var pieces = solver.finish();
        assertEquals(6, pieces.size());
        for (byte piece : pieces.values()) {
            assertEquals(SmoothPiece.stairs(SmoothPiece.NORTH, false, SmoothPiece.STRAIGHT), piece);
        }
        assertTrue(new PieceSolver(SmoothMode.SLABS, 0.5).finish().isEmpty());
        assertFalse(new PieceSolver(SmoothMode.NONE, 0.5).active());
        assertFalse(new PieceSolver(SmoothMode.STAIRS, 0.0).active());
    }

    @Test
    void occupancyPacksIntoEightBytes() {
        double[] occupancy = {0.0, 0.2, 0.4, 0.6, 0.8, 1.0, 0.5, 0.25};
        double[] back = new double[8];
        PieceSolver.unpack(PieceSolver.pack(occupancy), back);
        for (int octant = 0; octant < 8; octant++) {
            assertEquals(occupancy[octant], back[octant], 0.5 / 255.0 + 1.0E-12);
        }
    }

    // ------------------------------------------------------------------ ayudas

    private static double solveCorner(SmoothMode mode) {
        // Rejilla 3×3 en y = 0, llena por abajo. Arriba solo una meseta al noroeste: los cuartos cuyo centro cae
        // en x < 1.5 y z < 1.5. Quedan una rampa que sube al oeste, otra que sube al norte y, en (1, 1), la
        // esquina exterior, con solo su cuarto noroeste lleno.
        PieceSolver solver = new PieceSolver(mode, 0.5);
        java.util.Map<Long, double[]> occupancies = new java.util.HashMap<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                double[] occupancy = new double[8];
                for (int octant = 0; octant < 8; octant++) {
                    double centerX = x + 0.25 + 0.5 * (octant & 1);
                    double centerZ = z + 0.25 + 0.5 * (octant >> 2 & 1);
                    boolean top = (octant >> 1 & 1) == 1;
                    occupancy[octant] = !top || centerX < 1.5 && centerZ < 1.5 ? 1.0 : 0.0;
                }
                long key = com.komainu.moxia.edit.PackedBlockPlan.pack(x, 0, z);
                occupancies.put(key, occupancy);
                solver.put(key, solver.choose(occupancy, true), occupancy);
            }
        }
        var pieces = solver.finish();
        double total = 0.0;
        for (var entry : occupancies.entrySet()) {
            long key = entry.getKey();
            byte piece = pieces.containsKey(key) ? pieces.get(key) : SmoothPiece.FULL;
            total += PieceFit.error(entry.getValue(), piece);
        }
        if (mode.solvesCorners()) {
            byte corner = pieces.get(com.komainu.moxia.edit.PackedBlockPlan.pack(1, 0, 1));
            assertTrue(SmoothPiece.isStairs(corner) && SmoothPiece.shape(corner) >= SmoothPiece.OUTER_LEFT,
                    "la esquina debe ser una escalera de esquina exterior: " + SmoothPiece.describe(corner));
        }
        return total;
    }

    private static double[] ground(double height) {
        double[] occupancy = new double[8];
        for (int octant = 0; octant < 8; octant++) {
            double bottom = (octant >> 1 & 1) * 0.5;
            occupancy[octant] = Math.clamp((height - bottom) / 0.5, 0.0, 1.0);
        }
        return occupancy;
    }

    private static int piecesOnGrid(SmoothMode mode, double coverage) {
        int count = 0;
        for (int sample = 0; sample < 400; sample++) {
            double[] occupancy = new double[8];
            long seed = sample * 0x9E3779B97F4A7C15L;
            for (int octant = 0; octant < 8; octant++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                occupancy[octant] = ((seed >>> 33) % 6) / 5.0;
            }
            boolean existing = sample % 2 == 0;
            byte piece = PieceFit.choose(occupancy, existing, mode, coverage);
            if (piece != SmoothPiece.FULL && piece != SmoothPiece.EMPTY) {
                count++;
            }
        }
        return count;
    }

    private static boolean allMatch(SmoothMode mode, java.util.function.Predicate<Byte> test) {
        for (byte piece : mode.candidates()) {
            if (!test.test(piece)) {
                return false;
            }
        }
        return true;
    }
}
