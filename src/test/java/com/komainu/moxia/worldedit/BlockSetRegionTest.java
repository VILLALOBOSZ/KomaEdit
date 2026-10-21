package com.komainu.moxia.worldedit;

import com.komainu.moxia.selection.BlockSet;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.function.LayerFunction;
import com.sk89q.worldedit.function.RegionFunction;
import com.sk89q.worldedit.function.mask.RegionMask;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.visitor.LayerVisitor;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.RegionOperationException;
import com.sk89q.worldedit.regions.Regions;
import com.sk89q.worldedit.regions.selector.limit.PermissiveSelectorLimits;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La selección de bloques sueltos vista por WorldEdit: lo que recorren sus visitantes (los de //set, //replace,
 * //count…), la máscara {@code #sel} ({@link RegionMask}), las columnas de //overlay, y cómo se adaptan //expand,
 * //contract y //shift. Sin mundo: WorldEdit solo necesita la región.
 */
class BlockSetRegionTest {
    private static BlockSet lShape() {
        BlockSet blocks = BlockSet.box(0, 64, 0, 4, 64, 0);
        blocks.addAll(BlockSet.box(0, 64, 1, 0, 64, 4));
        blocks.add(0, 65, 0);
        return blocks;
    }

    @Test
    void worldEditVisitsExactlyTheSelectedBlocks() throws Exception {
        BlockSetRegion region = new BlockSetRegion(null, lShape(), 1_000);
        assertEquals(10, region.getVolume());
        assertEquals(BlockVector3.at(0, 64, 0), region.getMinimumPoint());
        assertEquals(BlockVector3.at(4, 65, 4), region.getMaximumPoint());
        assertTrue(region.contains(BlockVector3.at(4, 64, 0)));
        assertFalse(region.contains(BlockVector3.at(4, 64, 4)), "la caja no es la región");

        // Lo que hacen //set, //replace o //count: un RegionVisitor sobre la selección.
        Set<BlockVector3> visited = new HashSet<>();
        RegionFunction collect = position -> visited.add(position);
        Operations.complete(new RegionVisitor(region, collect));
        Set<BlockVector3> expected = new HashSet<>();
        lShape().forEach(block -> expected.add(BlockVector3.at(BlockSet.x(block), BlockSet.y(block),
                BlockSet.z(block))));
        assertEquals(expected, visited);

        // La máscara #sel de WorldEdit (RegionMask con la selección) responde bloque a bloque.
        RegionMask mask = new RegionMask(region);
        assertTrue(mask.test(BlockVector3.at(0, 65, 0)));
        assertFalse(mask.test(BlockVector3.at(1, 65, 0)));
    }

    @Test
    void flatCommandsUseOnlyTheSelectedColumns() throws Exception {
        BlockSetRegion region = new BlockSetRegion(null, lShape(), 1_000);
        Set<BlockVector2> columns = new HashSet<>();
        region.asFlatRegion().forEach(columns::add);
        assertEquals(9, columns.size(), "nueve columnas, no las 25 de la caja");
        assertSame(region, Regions.asFlatRegion(region), "WorldEdit la usa como región plana");
        assertEquals(64, region.getMinimumY());
        assertEquals(65, region.getMaximumY());

        // //overlay y //naturalize recorren las columnas con un LayerVisitor.
        Set<BlockVector2> layered = new HashSet<>();
        LayerFunction function = new LayerFunction() {
            @Override
            public boolean isGround(BlockVector3 position) {
                return position.y() <= 64;
            }

            @Override
            public boolean apply(BlockVector3 position, int depth) {
                layered.add(position.toBlockVector2());
                return false;
            }
        };
        Operations.complete(new LayerVisitor(region, region.getMinimumY(), region.getMaximumY(), function));
        assertEquals(columns, layered);
        assertEquals(Set.of(BlockVector2.at(0, 0)), region.getChunks());
        assertEquals(Set.of(BlockVector3.at(0, 4, 0)), region.getChunkCubes());
    }

    @Test
    void expandContractAndShiftAreAdapted() throws Exception {
        BlockSetRegion region = new BlockSetRegion(null, BlockSet.box(0, 64, 0, 0, 64, 0), 1_000);
        region.expand(BlockVector3.at(0, 3, 0));
        assertEquals(BlockSet.box(0, 64, 0, 0, 67, 0), region.blocks(), "//expand 3 up estira hacia arriba");
        region.expand(BlockVector3.at(-2, 0, 0));
        assertEquals(BlockSet.box(-2, 64, 0, 0, 67, 0), region.blocks());

        // //contract 1 down (cambio (0, -1, 0)): baja la cara de arriba.
        region.contract(BlockVector3.at(0, -1, 0));
        assertEquals(BlockSet.box(-2, 64, 0, 0, 66, 0), region.blocks());
        // Cambio (+1, 0, 0): sube la cara de −X, como la caja.
        region.contract(BlockVector3.at(1, 0, 0));
        assertEquals(BlockSet.box(-1, 64, 0, 0, 66, 0), region.blocks());

        int before = region.revision();
        region.shift(BlockVector3.at(10, -4, 2));
        assertEquals(BlockSet.box(9, 60, 2, 10, 62, 2), region.blocks());
        assertNotEquals(before, region.revision());

        assertThrows(RegionOperationException.class, () -> region.contract(BlockVector3.at(0, 5, 0)),
                "no puede quedarse vacía");
        assertThrows(RegionOperationException.class, () -> region.expand(BlockVector3.at(0, 0, 500)),
                "ni pasar del máximo");
        assertThrows(RegionOperationException.class, () -> region.shift(BlockVector3.at(0, 5_000, 0)));
        assertEquals(BlockSet.box(9, 60, 2, 10, 62, 2), region.blocks(), "lo que falla no cambia nada");

        BlockSetRegion copy = region.clone();
        copy.shift(BlockVector3.at(1, 0, 0));
        assertNotSame(copy.blocks(), region.blocks());
        assertTrue(region.contains(BlockVector3.at(9, 60, 2)), "la copia es independiente");
        assertThrows(IllegalArgumentException.class, () -> new BlockSetRegion(null, new BlockSet(), 10));
    }

    @Test
    void theSelectorTurnsIntoABoxWithTheWand() throws Exception {
        BlockSetSelector selector = new BlockSetSelector(null, lShape(), 1_000);
        assertTrue(selector.isDefined());
        assertInstanceOf(BlockSetRegion.class, selector.getRegion());
        assertEquals(10, selector.getVolume());
        assertEquals(lShape(), selector.blocks());
        long version = selector.version();
        ((BlockSetRegion) selector.getRegion()).shift(BlockVector3.at(0, 1, 0));
        assertNotEquals(version, selector.version(), "//shift cambia lo que hay que dibujar");
        assertNotEquals(selector.version(), new BlockSetSelector(null, lShape(), 1_000).version());

        selector.selectPrimary(BlockVector3.at(5, 70, 5), PermissiveSelectorLimits.getInstance());
        assertNull(selector.blocks(), "la varita la pasa a caja");
        assertTrue(selector.fallback() != null && !selector.isDefined());
        selector.selectSecondary(BlockVector3.at(7, 71, 9), PermissiveSelectorLimits.getInstance());
        assertTrue(selector.isDefined());
        assertInstanceOf(CuboidRegion.class, selector.getRegion());
        assertEquals(3 * 2 * 5, selector.getVolume());
        assertEquals("cuboid", selector.getTypeName());

        BlockSetSelector cleared = new BlockSetSelector(null, lShape(), 1_000);
        cleared.clear();
        assertFalse(cleared.isDefined());
        assertThrows(IncompleteRegionException.class, cleared::getRegion);
        assertNull(cleared.blocks());
    }
}
