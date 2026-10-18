package com.komainu.moxia.deform;

import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeformCoreTest {
    @Test
    void distanceTransformIsExactAndRemembersTheNearestFeature() {
        int size = 9;
        boolean[] features = new boolean[size * size * size];
        int first = (4 * size + 4) * size + 1;
        int second = (4 * size + 4) * size + 7;
        features[first] = true;
        features[second] = true;
        DistanceTransform.Result result = DistanceTransform.compute(size, size, size, features, 1.0, 1.0, 1.0);
        int corner = 0;
        // (0,0,0) frente a (1,4,4): 1 + 16 + 16.
        assertEquals(33.0F, result.squared()[corner], 1.0E-4F);
        assertEquals(first, result.nearest()[corner]);
        int farCorner = (8 * size + 8) * size + 8;
        assertEquals(second, result.nearest()[farCorner]);
        assertEquals(1.0 + 16.0 + 16.0, result.squared()[farCorner], 1.0E-4);
    }

    @Test
    void weightedDistanceMeasuresAnEllipsoid() {
        int size = 11;
        boolean[] features = new boolean[size * size * size];
        int center = (5 * size + 5) * size + 5;
        features[center] = true;
        // Radios 4 en X y 2 en Y: (4,0,0) y (0,2,0) quedan justo en el borde.
        DistanceTransform.Result result = DistanceTransform.compute(size, size, size, features,
                1.0 / 16.0, 1.0 / 4.0, 1.0);
        assertEquals(1.0, result.squared()[(5 * size + 5) * size + 9], 1.0E-6);
        assertEquals(1.0, result.squared()[(7 * size + 5) * size + 5], 1.0E-6);
        assertTrue(result.squared()[(8 * size + 5) * size + 5] > 1.0F);
    }

    @Test
    void simplePointsKeepTopology() {
        boolean[] full = new boolean[27];
        java.util.Arrays.fill(full, true);
        assertFalse(Topology.simple(full), "vaciar el centro de un bloque macizo abre una cavidad");

        boolean[] corner = new boolean[27];
        // Bloque en una esquina de un cubo: vecinos solo en el octante +x+y+z.
        for (int dz = 0; dz <= 1; dz++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dx = 0; dx <= 1; dx++) {
                    corner[(dx + 1) + 3 * (dy + 1) + 9 * (dz + 1)] = true;
                }
            }
        }
        assertTrue(Topology.simple(corner), "quitar una esquina no cambia nada");

        boolean[] bridge = new boolean[27];
        bridge[12] = true;
        bridge[14] = true;
        assertFalse(Topology.simple(bridge), "quitar el centro de una barra la parte en dos");

        boolean[] lonely = new boolean[27];
        assertFalse(Topology.simple(lonely), "quitar un bloque suelto hace desaparecer una pieza");
    }

    @Test
    void argumentsMixValuesFlagsAndNegativeNumbers() {
        DeformArguments args = DeformArguments.parse("12 -0.5 -w Slabs(C:0.7) -afo -s 42",
                Set.of("w", "s"), "afot");
        assertEquals(2, args.count());
        assertEquals(12.0, args.number(0, 0, 0, 100, "a"));
        assertEquals(-0.5, args.number(1, 0, -1, 1, "b"));
        assertTrue(args.has('a') && args.has('f') && args.has('o'));
        assertFalse(args.has('t'));
        assertEquals("Slabs(C:0.7)", args.value("w"));
        assertEquals(42L, args.seed("s", 7L));
        assertEquals(7L, DeformArguments.parse("-s -1", Set.of("s"), "").seed("s", 7L));
        assertThrows(IllegalArgumentException.class, () -> DeformArguments.parse("-q", Set.of(), "a"));
        assertThrows(IllegalArgumentException.class, () -> DeformArguments.parse("-w", Set.of("w"), ""));
        assertEquals("SlabsAndStairs(Slab:oak,Stair:spruce)",
                DeformArguments.parse("3 -w SlabsAndStairs(Slab:oak, Stair:spruce) 2", Set.of("w"), "").value("w"),
                "un perfil con espacios tras las comas es un solo valor");
        assertThrows(IllegalArgumentException.class, () -> args.number(0, 0, 0, 5, "El tamaño"));
        assertThrows(IllegalArgumentException.class, () -> args.expectAtMost(1, "uso"));
    }

    @Test
    void profilesUseEzEditsNames() {
        assertEquals(SmoothMode.SLABS, SmoothProfile.parse("Slabs").mode());
        SmoothProfile sas = SmoothProfile.parse("SlabsAndStairs(C:0.8)");
        assertEquals(SmoothMode.STAIRS, sas.mode());
        assertEquals(0.8, sas.coverage(), 1.0E-9);
        assertEquals(SmoothMode.STAIRS, SmoothProfile.parse("sas").mode());
        assertEquals(SmoothMode.STAIRS, SmoothProfile.parse("escaleras").mode());
        assertFalse(SmoothProfile.parse(null).active());
        assertEquals(SmoothMode.LAYERS, SmoothProfile.parse("Layers").mode());
        assertEquals("acacia", SmoothProfile.parse("Slabs(Slab:acacia)").materials().get(
                com.komainu.moxia.shape.smooth.PieceVariant.SLAB));
        assertThrows(IllegalArgumentException.class, () -> SmoothProfile.parse("Slabs(C:0.5"));
        assertThrows(IllegalArgumentException.class, () -> SmoothProfile.parse("Slabs(Stair:oak)"));
    }

    @Test
    void blockMatcherResolvesIdsTagsAndNegation() {
        BlockMatcher matcher = BlockMatcher.parse("stone,#minecraft:dirtish", tag -> {
            assertEquals("minecraft:dirtish", tag);
            return Set.of("minecraft:dirt", "minecraft:coarse_dirt");
        });
        assertTrue(matcher.test("minecraft:stone"));
        assertTrue(matcher.test("coarse_dirt"));
        assertFalse(matcher.test("minecraft:grass_block"));
        BlockMatcher inverted = BlockMatcher.parse("!stone", tag -> Set.of());
        assertFalse(inverted.test("minecraft:stone"));
        assertTrue(inverted.test("minecraft:dirt"));
        assertThrows(IllegalArgumentException.class, () -> BlockMatcher.parse(" ", tag -> Set.of()));
    }

    @Test
    void writerSkipsUnchangedBlocksAndKeepsLiquids() {
        Volume volume = Volume.of(Shapes.snapshot(3, 3, 3, 0, (x, y, z) -> y == 0 ? Shapes.STONE
                : y == 1 ? Shapes.WATER : Shapes.AIR));
        DeformField clear = new DeformField() {
            @Override
            public int material(int x, int y, int z) {
                return y == 0 ? Shapes.STONE : AIR;
            }

            @Override
            public boolean solid(double x, double y, double z) {
                return y < 1.0;
            }
        };
        Shapes.Result result = Shapes.apply(volume, clear);
        assertEquals(0, result.stats.total(), "nada cambia: la piedra sigue y el agua no se borra");
    }

    @Test
    void writerPlacesSlabsWhereHalfABlockIsFilled() {
        Volume volume = Volume.of(Shapes.snapshot(4, 3, 4, 0, (x, y, z) -> y <= 1 ? Shapes.STONE : Shapes.AIR));
        DeformField halfway = new DeformField() {
            @Override
            public int material(int x, int y, int z) {
                return y <= 1 ? Shapes.STONE : AIR;
            }

            @Override
            public boolean solid(double x, double y, double z) {
                return y < 1.5;
            }
        };
        Shapes.Result slabs = Shapes.apply(volume, halfway, volume.selectionBounds(), SmoothProfile.parse("Slabs"));
        assertEquals(16, slabs.stats.slabs());
        assertEquals(SmoothPiece.slab(false), slabs.piece(2, 1, 2));
        assertEquals(Shapes.STONE, slabs.at(2, 1, 2));
        assertEquals(SmoothPiece.FULL, slabs.piece(2, 0, 2));

        Shapes.Result none = Shapes.apply(volume, halfway, volume.selectionBounds(), SmoothProfile.NONE);
        assertEquals(0, none.stats.total());
    }

    @Test
    void writerUsesStairsOnSlopesAndBorrowsANeighbourMaterial() {
        // Rampa a 45° que pasa por el centro de los bloques de la diagonal: ahí ocupa 6 de 8 octantes.
        Volume volume = Volume.of(Shapes.snapshot(6, 6, 3, 0, (x, y, z) -> y <= x ? Shapes.DIRT : Shapes.AIR));
        DeformField ramp = new DeformField() {
            @Override
            public int material(int x, int y, int z) {
                return y <= x ? Shapes.DIRT : AIR;
            }

            @Override
            public boolean solid(double x, double y, double z) {
                return y < x + 0.5;
            }
        };
        Shapes.Result result = Shapes.apply(volume, ramp, volume.selectionBounds(), SmoothProfile.parse("stairs"));
        assertTrue(result.stats.stairs() > 0, "una rampa a 45° se cubre con escaleras");
        for (int index = 0; index < result.ids.length; index++) {
            if (result.pieces[index] != SmoothPiece.FULL) {
                assertEquals(Shapes.DIRT, result.ids[index], "las piezas toman el material de la rampa");
            }
        }
    }
}
