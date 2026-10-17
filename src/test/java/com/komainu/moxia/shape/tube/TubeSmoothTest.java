package com.komainu.moxia.shape.tube;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeGenerator;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.shape.smooth.SmoothPieces;
import com.sk89q.worldedit.math.BlockVector3;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TubeSmoothTest {
    private static final int LIMIT = 1_500_000;
    private static final int LENGTH = 40;

    @Test
    void horizontalCylinderGetsSlabsOnTopAndHangingSlabsBelow() {
        SampledPath path = straight();
        ShapeParameters parameters = sweep(6, smooth(SmoothMode.SLABS, 0.5));
        Set<BlockVector3> original = TubeFigures.generate(path, parameters, LIMIT);
        TubeFigures.Smoothed smoothed = TubeFigures.smooth(path, parameters, TubeCurve.NONE, original, LIMIT);
        SmoothPieces pieces = smoothed.pieces();

        assertFalse(pieces.isEmpty());
        assertEquals(0, pieces.stairs(), "en modo losas no aparecen escaleras");
        assertTrue(smoothed.blocks().containsAll(original), "las piezas sustituyen o añaden, nunca quitan");
        for (BlockVector3 block : smoothed.blocks()) {
            byte piece = pieces.at(block.x(), block.y(), block.z());
            if (piece == SmoothPiece.FULL || block.x() < 3 || block.x() > LENGTH - 3 || block.y() == 0) {
                continue;
            }
            // El eje pasa por el centro de la fila y = 0: encima la superficie mira al cielo (losa abajo) y
            // debajo al suelo (losa colgada).
            assertEquals(block.y() < 0, SmoothPiece.isTop(piece), "losa mal orientada en " + block);
            assertTrue(isSurface(smoothed.blocks(), block), "pieza en el interior: " + block);
        }
    }

    @Test
    void stairsClimbTowardTheAxis() {
        SampledPath path = straight();
        ShapeParameters parameters = sweep(7, smooth(SmoothMode.STAIRS, 0.5));
        TubeFigures.Smoothed smoothed = TubeFigures.smooth(path, parameters, TubeCurve.NONE,
                TubeFigures.generate(path, parameters, LIMIT), LIMIT);
        int stairs = 0;
        for (BlockVector3 block : smoothed.blocks()) {
            byte piece = smoothed.pieces().at(block.x(), block.y(), block.z());
            if (!SmoothPiece.isStairs(piece) || block.x() < 3 || block.x() > LENGTH - 3) {
                continue;
            }
            stairs++;
            // Tubo a lo largo de X con el eje en el centro de la fila y = 0, z = 0: las escaleras suben hacia el
            // eje (al sur si están al norte y al revés) y las de la mitad de abajo cuelgan.
            if (block.z() != 0) {
                int expected = block.z() < 0 ? SmoothPiece.SOUTH : SmoothPiece.NORTH;
                assertEquals(expected, SmoothPiece.facing(piece), "escalera mal orientada en " + block);
            }
            if (block.y() != 0) {
                assertEquals(block.y() < 0, SmoothPiece.isTop(piece), "mitad equivocada en " + block);
            }
            assertEquals(SmoothPiece.STRAIGHT, SmoothPiece.shape(piece), "un cilindro recto no tiene esquinas");
        }
        assertTrue(stairs > 20, "un cilindro de radio 7 debe llevar escaleras en los flancos: " + stairs);
    }

    @Test
    void coverageZeroOrNoModeLeavesTheGeometryUntouched() {
        SampledPath path = straight();
        for (Map<String, Double> options : List.of(smooth(SmoothMode.STAIRS, 0.0), Map.<String, Double>of())) {
            ShapeParameters parameters = sweep(5, options);
            Set<BlockVector3> original = TubeFigures.generate(path, parameters, LIMIT);
            TubeFigures.Smoothed smoothed = TubeFigures.smooth(path, parameters, TubeCurve.NONE, original, LIMIT);
            assertSame(original, smoothed.blocks());
            assertTrue(smoothed.pieces().isEmpty());
            assertFalse(TubeFigures.usesPieces(parameters));
        }
    }

    @Test
    void higherCoverageUsesMorePieces() {
        SampledPath path = diagonal();
        int previous = -1;
        for (double coverage : new double[]{0.25, 0.5, 1.0}) {
            ShapeParameters parameters = sweep(6, smooth(SmoothMode.STAIRS, coverage));
            TubeFigures.Smoothed smoothed = TubeFigures.smooth(path, parameters, TubeCurve.NONE,
                    TubeFigures.generate(path, parameters, LIMIT), LIMIT);
            assertTrue(smoothed.pieces().size() > previous, "cobertura " + coverage);
            previous = smoothed.pieces().size();
        }
    }

    @Test
    void hollowPipesKeepTheirInnerWallWhole() {
        SampledPath path = straight();
        Map<String, Double> options = new HashMap<>(smooth(SmoothMode.STAIRS, 1.0));
        options.put("hollow", 1.0);
        options.put("wall", 2.0);
        ShapeParameters parameters = sweep(7, options);
        Set<BlockVector3> original = TubeFigures.generate(path, parameters, LIMIT);
        TubeFigures.Smoothed smoothed = TubeFigures.smooth(path, parameters, TubeCurve.NONE, original, LIMIT);
        assertEquals(original.size(), smoothed.blocks().size(), "en tubos huecos no se añaden piezas");
        assertFalse(smoothed.pieces().isEmpty(), "la cara exterior sí se suaviza");
        int[][] offsets = {{0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (BlockVector3 block : original) {
            byte piece = smoothed.pieces().at(block.x(), block.y(), block.z());
            if (piece == SmoothPiece.FULL || block.x() < 1 || block.x() > LENGTH - 2) {
                continue;
            }
            // Un vecino vacío cerca del eje es el interior de la tubería: ahí la pared debe seguir entera.
            for (int[] offset : offsets) {
                BlockVector3 neighbor = block.add(offset[0], offset[1], offset[2]);
                boolean cavity = !original.contains(neighbor) && Math.hypot(neighbor.y(), neighbor.z()) < 6.0;
                assertFalse(cavity, "pieza pegada al interior de la tubería: " + block);
            }
        }
    }

    @Test
    void commandPlanCarriesThePieces() {
        SampledPath path = straight();
        ShapeParameters parameters = sweep(5, smooth(SmoothMode.STAIRS, 0.5));
        PackedBlockPlan plan = ShapeGenerator.generatePlan(path, parameters);
        TubeFigures.Smoothed smoothed = TubeFigures.smooth(path, parameters, TubeCurve.NONE,
                TubeFigures.generate(path, parameters, LIMIT), LIMIT);
        assertTrue(plan.hasPieces());
        assertEquals(smoothed.blocks().size(), plan.size());
        assertEquals(smoothed.pieces().size(), plan.pieceCount());
        for (int index = 0; index < plan.size(); index++) {
            BlockVector3 position = plan.position(index);
            assertEquals(smoothed.pieces().at(position.x(), position.y(), position.z()), plan.piece(index));
        }
    }

    @Test
    void braidAndNoiseTubeAcceptPiecesDeterministically() {
        SampledPath path = diagonal();
        for (PathShape shape : List.of(PathShape.BRAID, PathShape.NOISE_TUBE)) {
            ShapeParameters parameters = shape.defaults().withOptions(new ShapeOptions(smooth(SmoothMode.STAIRS, 0.5)));
            TubeFigures.Smoothed first = TubeFigures.smooth(path, parameters, TubeCurve.NONE,
                    TubeFigures.generate(path, parameters, LIMIT), LIMIT);
            TubeFigures.Smoothed second = TubeFigures.smooth(path, parameters, TubeCurve.NONE,
                    TubeFigures.generate(path, parameters, LIMIT), LIMIT);
            assertFalse(first.pieces().isEmpty(), shape.id());
            assertEquals(first.blocks(), second.blocks(), shape.id() + ": el holograma y el Build deben coincidir");
            assertEquals(first.pieces().size(), second.pieces().size());
        }
    }

    @Test
    void interpolatedMeasurementMatchesExactSampling() {
        SampledPath path = diagonal();
        Map<String, Double> star = new HashMap<>(smooth(SmoothMode.STAIRS, 0.5));
        star.put("profile", (double) TubeSettings.TubeProfile.STAR.ordinal());
        star.put("twist", 40.0);
        star.put("k0", 60.0);
        star.put("k1", 100.0);
        star.put("k2", 70.0);
        List<ShapeParameters> cases = List.of(
                sweep(9, star),
                PathShape.BRAID.defaults().withOptions(new ShapeOptions(smooth(SmoothMode.STAIRS, 0.5))),
                PathShape.NOISE_TUBE.defaults().withOptions(new ShapeOptions(smooth(SmoothMode.SLABS, 0.7)))
        );
        for (ShapeParameters parameters : cases) {
            Set<BlockVector3> blocks = TubeFigures.generate(path, parameters, LIMIT);
            LongOpenHashSet fastBlocks = packed(blocks);
            LongOpenHashSet exactBlocks = packed(blocks);
            SmoothPieces fast = TubeSmoother.smooth(evaluator(path, parameters), TubeSettings.from(parameters),
                    fastBlocks, LIMIT, true);
            SmoothPieces exact = TubeSmoother.smooth(evaluator(path, parameters), TubeSettings.from(parameters),
                    exactBlocks, LIMIT, false);
            LongOpenHashSet union = new LongOpenHashSet(fastBlocks);
            union.addAll(exactBlocks);
            int different = 0;
            for (long position : union) {
                if (fast.at(position) != exact.at(position)
                        || fastBlocks.contains(position) != exactBlocks.contains(position)) {
                    different++;
                }
            }
            int total = Math.max(fast.size(), exact.size());
            assertTrue(total > 100, parameters.shape().id());
            assertTrue(different <= total * 0.02, parameters.shape().id() + ": " + different + " de " + total
                    + " piezas cambian al interpolar");
        }
    }

    @Test
    void largeTubesWithPiecesStayFast() {
        SampledPath path = SampledPath.createSmooth(List.of(
                BlockVector3.at(0, 0, 0), BlockVector3.at(120, 60, 120), BlockVector3.at(240, 120, 240)), false);
        ShapeParameters parameters = sweep(14, smooth(SmoothMode.STAIRS, 0.5));
        long start = System.nanoTime();
        TubeFigures.Smoothed smoothed = TubeFigures.smooth(path, parameters, TubeCurve.NONE,
                TubeFigures.generate(path, parameters, LIMIT), LIMIT);
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(smoothed.pieces().size() > 5_000);
        assertTrue(elapsed < 8_000, "demasiado lento: " + elapsed + " ms con piezas");
    }

    private static TubeVoxelizer.Evaluator evaluator(SampledPath path, ShapeParameters parameters) {
        TubeSettings settings = TubeSettings.from(parameters);
        return new TubeVoxelizer.Evaluator(TubeCenterline.create(path, settings, TubeCurve.NONE), settings,
                TubeShapes.create(settings, TubeFigures.pathSeed(path)));
    }

    private static LongOpenHashSet packed(Set<BlockVector3> blocks) {
        LongOpenHashSet packed = new LongOpenHashSet(blocks.size());
        for (BlockVector3 block : blocks) {
            packed.add(PackedBlockPlan.pack(block.x(), block.y(), block.z()));
        }
        return packed;
    }

    private static boolean isSurface(Set<BlockVector3> blocks, BlockVector3 block) {
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : offsets) {
            if (!blocks.contains(block.add(offset[0], offset[1], offset[2]))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Double> smooth(SmoothMode mode, double coverage) {
        return Map.of("smooth", (double) mode.ordinal(), "coverage", coverage);
    }

    private static ShapeParameters sweep(int radius, Map<String, Double> options) {
        return new ShapeParameters(PathShape.SWEEP, radius, 6, 1, 0, new ShapeOptions(new HashMap<>(options)));
    }

    private static SampledPath straight() {
        return SampledPath.createSmooth(List.of(BlockVector3.at(0, 0, 0), BlockVector3.at(LENGTH, 0, 0)), false);
    }

    private static SampledPath diagonal() {
        return SampledPath.createSmooth(List.of(
                BlockVector3.at(0, 0, 0),
                BlockVector3.at(18, 9, 18),
                BlockVector3.at(36, 18, 36)
        ), false);
    }
}
