package com.komainu.moxia.shape.smooth;

import com.komainu.moxia.deform.SmoothProfile;
import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El perfil {@code Panes}: paneles de vidrio o barrotes unidos entre sí y a los bloques enteros de al lado. */
class PanesProfileTest {
    private static final int SOUTH = 1 << SmoothPiece.SOUTH;
    private static final int WEST = 1 << SmoothPiece.WEST;
    private static final int NORTH = 1 << SmoothPiece.NORTH;
    private static final int EAST = 1 << SmoothPiece.EAST;

    @Test
    void paneEncodingKeepsConnectionsAndTheAddedMark() {
        for (int connections = 0; connections < 16; connections++) {
            byte pane = SmoothPiece.pane(connections);
            byte added = SmoothPiece.added(pane);
            assertTrue(SmoothPiece.isValid(pane) && SmoothPiece.isValid(added));
            assertTrue(SmoothPiece.isPane(pane) && SmoothPiece.isPane(added));
            assertFalse(SmoothPiece.isAdded(pane));
            assertTrue(SmoothPiece.isAdded(added));
            assertEquals(connections, SmoothPiece.paneConnections(added));
            assertEquals(PieceVariant.PANE, SmoothPiece.variant(added));
            assertEquals(0, SmoothPiece.mask(pane), "un panel no tapa ningún octante entero");
            assertTrue(SmoothPiece.isAdded(SmoothPiece.withConnections(added, 15 - connections)));
            assertFalse(SmoothPiece.isWall(pane) || SmoothPiece.isLayers(pane) || SmoothPiece.isStairs(pane));
        }
        // Las piezas de antes no cambian de significado con la marca en el bit de signo.
        byte stairs = SmoothPiece.added(SmoothPiece.stairs(SmoothPiece.EAST, true, SmoothPiece.OUTER_LEFT));
        assertTrue(SmoothPiece.isAdded(stairs) && SmoothPiece.isStairs(stairs) && SmoothPiece.isTop(stairs));
        assertEquals(SmoothPiece.EAST, SmoothPiece.facing(stairs));
        assertFalse(SmoothPiece.isValid(SmoothPiece.EMPTY));
        assertFalse(SmoothPiece.isAdded(SmoothPiece.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> SmoothPiece.pane(16));
        assertEquals("panel oeste este", SmoothPiece.describe(SmoothPiece.pane(WEST | EAST)));
    }

    @Test
    void paneOccupancyIsThePostAndItsArms() {
        // Al elegir: la lámina entera de 2 de 16 píxeles.
        for (int octant = 0; octant < 8; octant++) {
            assertEquals(SmoothPiece.PANE_FILL, SmoothPiece.fill(SmoothPiece.pane(0), octant), 1.0E-12);
        }
        // Una cruz: poste (2×16×2) y cuatro brazos de 7×16×2, en píxeles cúbicos.
        assertEquals(64 + 4 * 224, volume(SmoothPiece.pane(SOUTH | WEST | NORTH | EAST)), 1.0E-9);
        // Un panel recto norte-sur ocupa lo mismo en los ocho octantes.
        byte straight = SmoothPiece.pane(NORTH | SOUTH);
        assertEquals(64 + 2 * 224, volume(straight), 1.0E-9);
        for (int octant = 1; octant < 8; octant++) {
            assertEquals(SmoothPiece.fill(straight, 0), SmoothPiece.fill(straight, octant), 1.0E-12);
        }
        assertEquals(3, SmoothPiece.boxes(SmoothPiece.pane(NORTH | EAST)).length, "poste y dos brazos");
        assertEquals(1, SmoothPiece.boxes(SmoothPiece.wall()).length);
        assertEquals(0, SmoothPiece.boxes(SmoothPiece.slab(false)).length);
    }

    @Test
    void thinPartsBecomePanesOnlyWithThePanesProfile() {
        double[] sheet = new double[8];
        Arrays.fill(sheet, 0.15);
        assertEquals(SmoothPiece.pane(0), PieceFit.choose(sheet, true, SmoothMode.PANES, 0.5));
        assertEquals(SmoothPiece.added(SmoothPiece.pane(0)), PieceFit.choose(sheet, false, SmoothMode.PANES, 0.5));
        for (SmoothMode mode : SmoothMode.values()) {
            if (mode != SmoothMode.PANES) {
                assertFalse(SmoothPiece.isPane(PieceFit.choose(sheet, true, mode, 0.5)), mode.ezName());
            }
        }
        // Medio bloque lleno (un muro grueso): ni bloque ni vacío ganan al panel por sí solos; con C:0.5 se queda
        // el bloque y con C:1 toda la superficie pasa a paneles, como en el resto de perfiles.
        double[] half = new double[8];
        for (int octant = 0; octant < 8; octant++) {
            half[octant] = (octant & 1) == 0 ? 1.0 : 0.0;
        }
        assertEquals(SmoothPiece.FULL, PieceFit.choose(half, true, SmoothMode.PANES, 0.5));
        assertTrue(SmoothPiece.isPane(PieceFit.choose(half, true, SmoothMode.PANES, 1.0)));
        double[] solid = new double[8];
        Arrays.fill(solid, 1.0);
        assertEquals(SmoothPiece.FULL, PieceFit.choose(solid, true, SmoothMode.PANES, 1.0));
    }

    @Test
    void solverJoinsPanesToEachOtherAndToFullBlocks() {
        // Fila de paneles de x = 0 a 2 con un bloque entero de la figura en x = -1 y otro panel suelto lejos.
        PieceSolver solver = new PieceSolver(SmoothMode.PANES, 0.5);
        double[] sheet = new double[8];
        Arrays.fill(sheet, 0.15);
        for (int x = 0; x <= 2; x++) {
            solver.put(PackedBlockPlan.pack(x, 0, 0), solver.choose(sheet, true), sheet);
        }
        solver.put(PackedBlockPlan.pack(10, 0, 10), SmoothPiece.added(SmoothPiece.pane(0)), sheet);
        // Un panel encima no se une: los paneles solo se unen en horizontal.
        solver.put(PackedBlockPlan.pack(1, 1, 0), solver.choose(sheet, true), sheet);
        LongOpenHashSet solid = new LongOpenHashSet();
        solid.add(PackedBlockPlan.pack(-1, 0, 0));
        solid.add(PackedBlockPlan.pack(1, 0, 1));
        Long2ByteOpenHashMap pieces = solver.finish(solid::contains);
        assertEquals(WEST | EAST, SmoothPiece.paneConnections(pieces.get(PackedBlockPlan.pack(0, 0, 0))));
        assertEquals(WEST | EAST | SOUTH, SmoothPiece.paneConnections(pieces.get(PackedBlockPlan.pack(1, 0, 0))));
        assertEquals(WEST, SmoothPiece.paneConnections(pieces.get(PackedBlockPlan.pack(2, 0, 0))));
        assertEquals(0, SmoothPiece.paneConnections(pieces.get(PackedBlockPlan.pack(1, 1, 0))));
        byte lonely = pieces.get(PackedBlockPlan.pack(10, 0, 10));
        assertEquals(0, SmoothPiece.paneConnections(lonely));
        assertTrue(SmoothPiece.isAdded(lonely), "la marca de añadido se conserva al unir");
        // Sin saber qué bloques son enteros, solo se une a otros paneles.
        PieceSolver blind = new PieceSolver(SmoothMode.PANES, 0.5);
        blind.put(PackedBlockPlan.pack(0, 0, 0), SmoothPiece.pane(0), sheet);
        blind.put(PackedBlockPlan.pack(0, 0, 1), SmoothPiece.pane(0), sheet);
        Long2ByteOpenHashMap alone = blind.finish();
        assertEquals(SOUTH, SmoothPiece.paneConnections(alone.get(PackedBlockPlan.pack(0, 0, 0))));
        assertEquals(NORTH, SmoothPiece.paneConnections(alone.get(PackedBlockPlan.pack(0, 0, 1))));
    }

    @Test
    void profileNamesAndMaterials() {
        assertEquals(SmoothMode.PANES, SmoothMode.byId("Panes"));
        assertEquals(SmoothMode.PANES, SmoothMode.byId("paneles"));
        assertEquals(SmoothMode.PANES, SmoothMode.byId("bars"));
        SmoothProfile profile = SmoothProfile.parse("Panes(C:0.7,Pane:iron_bars)");
        assertEquals(SmoothMode.PANES, profile.mode());
        assertEquals(0.7, profile.coverage(), 1.0E-12);
        assertEquals("iron_bars", profile.materials().get(PieceVariant.PANE));
        assertEquals("Panes(C:0.7,Pane:iron_bars)", profile.toString());
        assertThrows(IllegalArgumentException.class, () -> SmoothProfile.parse("SSW(Pane:glass)"));
        assertThrows(IllegalArgumentException.class, () -> SmoothProfile.parse("Panes(Slab:oak)"));
        assertTrue(SmoothMode.names().contains("Panes"));
    }

    @Test
    void glassBecomesItsPaneAndOtherBlocksTheClosestColour() {
        VanillaCatalog catalog = VanillaCatalog.INSTANCE;
        assertEquals("minecraft:glass_pane", SmoothMaterials.resolve("glass", PieceVariant.PANE, catalog));
        assertEquals("minecraft:light_gray_stained_glass_pane",
                SmoothMaterials.resolve("minecraft:light_gray_stained_glass", PieceVariant.PANE, catalog));
        assertEquals("minecraft:iron_bars", SmoothMaterials.resolve("iron_block", PieceVariant.PANE, catalog));
        assertEquals("minecraft:iron_bars", SmoothMaterials.override("iron_bars", PieceVariant.PANE, catalog));
        assertEquals("minecraft:red_stained_glass_pane",
                SmoothMaterials.override("red_stained_glass", PieceVariant.PANE, catalog));
        String byColour = SmoothMaterials.resolve("minecraft:lime_concrete", PieceVariant.PANE, catalog);
        assertNotNull(byColour);
        assertTrue(catalog.is(byColour, PieceVariant.PANE), byColour);
    }

    private static double volume(byte pane) {
        double total = 0.0;
        for (int octant = 0; octant < 8; octant++) {
            total += SmoothPiece.fill(pane, octant) * 512.0;
        }
        return total;
    }
}
