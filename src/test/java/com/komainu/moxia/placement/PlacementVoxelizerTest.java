package com.komainu.moxia.placement;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementVoxelizerTest {
    /** Una «L» de 3×2×1: (0,0,0), (1,0,0), (2,0,0) y (0,1,0), con un bloque distinto en la punta. */
    private static VoxelStructure ell(boolean smooth, double fillBias) {
        return VoxelStructure.builder(3, 2, 1, 0, 0, 0)
                .set(0, 0, 0, 0).set(1, 0, 0, 0).set(2, 0, 0, 1).set(0, 1, 0, 0)
                .build(smooth, fillBias, 0.0, "L");
    }

    private static Placement place(Structure structure, Vec3 anchor, Mat3 rotation, double scale) {
        return new Placement(structure, anchor, rotation, new Vec3(scale, scale, scale), 1L);
    }

    private static PlacementVoxelizer.Result voxelize(Placement... placements) {
        return PlacementVoxelizer.voxelize(List.of(placements), PlacementVoxelizer.Options.defaults());
    }

    private static int at(PlacementVoxelizer.Result result, int x, int y, int z) {
        long key = PackedBlockPlan.pack(x, y, z);
        return result.cells().containsKey(key) ? PlacementVoxelizer.Result.palette(result.cells().get(key)) : -99;
    }

    @Test
    void identityIsAnExactCopyAtTheAnchor() {
        PlacementVoxelizer.Result result = voxelize(place(ell(false, 1), new Vec3(10.5, 64.5, -3.5), Mat3.IDENTITY, 1));
        assertEquals(4, result.size(), "el aire no se coloca sin -a");
        assertEquals(0, at(result, 10, 64, -4));
        assertEquals(1, at(result, 12, 64, -4));
        assertEquals(0, at(result, 10, 65, -4));
        assertEquals(-99, at(result, 11, 65, -4));
    }

    @Test
    void quarterTurnsAndMirrorsStayExact() {
        Mat3 turn = Mat3.rotation(Alignment.UP, 90);
        PlacementVoxelizer.Result turned = voxelize(place(ell(false, 1), new Vec3(0.5, 0.5, 0.5), turn, 1));
        assertEquals(4, turned.size());
        // Girar 90° sobre +y lleva +x a -z.
        assertEquals(1, at(turned, 0, 0, -2));
        Mat3 mirror = Mat3.diagonal(-1, 1, 1);
        PlacementVoxelizer.Result mirrored = voxelize(place(ell(false, 1), new Vec3(0.5, 0.5, 0.5), mirror, 1));
        assertEquals(1, at(mirrored, -2, 0, 0));
    }

    @Test
    void scalingFillsWithoutHoles() {
        PlacementVoxelizer.Result doubled = voxelize(place(ell(false, 1), new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, 2));
        assertEquals(4 * 8, doubled.size());
        Mat3 odd = Mat3.rotation(new Vec3(1, 2, 0.5), 33);
        VoxelStructure block = VoxelStructure.builder(6, 6, 6, 0, 0, 0).build(false, 1, 0, "cubo");
        VoxelStructure.Builder solid = VoxelStructure.builder(6, 6, 6, -3, -3, -3);
        for (int x = -3; x < 3; x++) {
            for (int y = -3; y < 3; y++) {
                for (int z = -3; z < 3; z++) {
                    solid.set(x, y, z, 0);
                }
            }
        }
        PlacementVoxelizer.Result rotated = voxelize(place(solid.build(false, 1, 0, "cubo"), new Vec3(0.5, 0.5, 0.5),
                odd, 2));
        // Un cubo macizo de 12 de lado girado: ningún hueco dentro (todo bloque rodeado por los seis lados).
        assertTrue(Math.abs(rotated.size() - 12 * 12 * 12) < 12 * 12 * 12 * 0.08, "volumen " + rotated.size());
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    assertEquals(0, at(rotated, x, y, z), "hueco en " + x + "," + y + "," + z);
                }
            }
        }
        assertEquals(0, block.solidCount());
    }

    @Test
    void smoothedPasteUsesFillBias() {
        Mat3 odd = Mat3.rotation(Alignment.UP, 45);
        PlacementVoxelizer.Result fast = voxelize(place(ell(false, 1), new Vec3(0.5, 0.5, 0.5), odd, 3));
        PlacementVoxelizer.Result smooth = voxelize(place(ell(true, 1), new Vec3(0.5, 0.5, 0.5), odd, 3));
        PlacementVoxelizer.Result generous = voxelize(place(ell(true, 3), new Vec3(0.5, 0.5, 0.5), odd, 3));
        PlacementVoxelizer.Result thin = voxelize(place(ell(true, 0.6), new Vec3(0.5, 0.5, 0.5), odd, 3));
        assertTrue(generous.size() > smooth.size(), generous.size() + " > " + smooth.size());
        assertTrue(smooth.size() > thin.size());
        assertTrue(Math.abs(fast.size() - smooth.size()) < fast.size() * 0.3);
    }

    @Test
    void airOnlyWithPlaceAirAndNeverOverAnotherPlacement() {
        VoxelStructure hollow = VoxelStructure.builder(3, 1, 1, 0, 0, 0).set(0, 0, 0, 0).set(2, 0, 0, 0)
                .build(false, 1, 0, "hueco");
        PlacementVoxelizer.Options air = PlacementVoxelizer.Options.defaults().withPlaceAir(true);
        PlacementVoxelizer.Result withAir = PlacementVoxelizer.voxelize(
                List.of(place(hollow, new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, 1)), air);
        assertEquals(Structure.AIR, at(withAir, 1, 0, 0));
        Placement first = place(ell(false, 1), new Vec3(1.5, 0.5, 0.5), Mat3.IDENTITY, 1);
        Placement second = place(hollow, new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, 1);
        PlacementVoxelizer.Result overlap = PlacementVoxelizer.voxelize(List.of(first, second), air);
        assertEquals(0, at(overlap, 1, 0, 0), "el aire de la segunda no borra el bloque de la primera");
        assertEquals(0, PlacementVoxelizer.Result.placement(overlap.cells().get(PackedBlockPlan.pack(1, 0, 0))));
    }

    @Test
    void clipKeepsOnlyAllowedBlocks() {
        PlacementVoxelizer.Result clipped = PlacementVoxelizer.voxelize(
                List.of(place(ell(false, 1), new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, 1)),
                PlacementVoxelizer.Options.defaults().withClip((x, y, z) -> x < 2));
        assertEquals(3, clipped.size());
    }

    @Test
    void smoothblocksPutSlabsAndStairsOnACurvedShape() {
        ProceduralStructure ball = StructureSpec.parse("Sh(S:Ellipsoid,P:stone)").procedural(1);
        Placement placement = new Placement(ball, new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, new Vec3(8, 8, 8), 1L);
        PlacementVoxelizer.Result plain = PlacementVoxelizer.voxelize(List.of(placement),
                PlacementVoxelizer.Options.defaults());
        assertTrue(plain.pieces().isEmpty());
        PlacementVoxelizer.Result smooth = PlacementVoxelizer.voxelize(List.of(placement),
                PlacementVoxelizer.Options.defaults().withSmooth(SmoothMode.STAIRS, 0.5));
        assertFalse(smooth.pieces().isEmpty());
        boolean slab = false;
        boolean stairs = false;
        for (byte piece : smooth.pieces().values()) {
            slab |= SmoothPiece.isSlab(piece);
            stairs |= SmoothPiece.isStairs(piece);
        }
        assertTrue(slab && stairs);
        // Arriba del todo, la pieza es una losa de abajo o una escalera apoyada.
        long top = PackedBlockPlan.pack(0, 7, 0);
        byte piece = smooth.pieces().get(top);
        assertTrue(piece == SmoothPiece.FULL || !SmoothPiece.isTop(piece));
    }

    @Test
    void sizeLimitsStopRunawayWork() {
        PlacementVoxelizer.Options tiny = new PlacementVoxelizer.Options(false, SmoothMode.NONE, 0.5, null, 10,
                1_000_000);
        assertThrows(RuntimeException.class, () -> PlacementVoxelizer.voxelize(
                List.of(place(ell(false, 1), new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, 3)), tiny));
        PlacementVoxelizer.Options cheap = new PlacementVoxelizer.Options(false, SmoothMode.NONE, 0.5, null, 1_000,
                100);
        assertThrows(IllegalArgumentException.class, () -> PlacementVoxelizer.voxelize(
                List.of(place(ell(false, 1), new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, 3)), cheap));
    }

    @Test
    void textureAndExpressionStructuresSpreadThePalette() {
        ProceduralStructure gradient = StructureSpec.parse("Ex(E:=y*.5+.5,P:\"a,b,c,d\")").procedural(4);
        Placement placement = new Placement(gradient, new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, new Vec3(4, 4, 4), 1L);
        PlacementVoxelizer.Result result = voxelize(placement);
        assertEquals(0, at(result, 0, -3, 0), "abajo, el primer bloque");
        assertEquals(3, at(result, 0, 3, 0), "arriba, el último");
        Long2LongOpenHashMap cells = result.cells();
        assertEquals(8 * 8 * 8 - 8 * 8, cells.size(), "la fila de abajo vale 0 y es aire");

        ProceduralStructure textured = StructureSpec.parse("TS(S:Ellipsoid,T:=y*.5+.5,P:\"a,b\")").procedural(2);
        PlacementVoxelizer.Result ball = voxelize(new Placement(textured, new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY,
                new Vec3(6, 6, 6), 1L));
        assertEquals(0, at(ball, 0, -4, 0));
        assertEquals(1, at(ball, 0, 4, 0));
        assertEquals(-99, at(ball, 5, 5, 5), "fuera de la forma no hay nada");
    }
}
