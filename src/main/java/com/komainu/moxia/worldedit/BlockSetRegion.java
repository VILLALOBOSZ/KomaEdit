package com.komainu.moxia.worldedit;

import com.komainu.moxia.selection.BlockSet;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.AbstractRegion;
import com.sk89q.worldedit.regions.FlatRegion;
import com.sk89q.worldedit.regions.RegionOperationException;
import com.sk89q.worldedit.world.World;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Una región de WorldEdit hecha de bloques sueltos: la del lazo y el pincel de selección. WorldEdit no trae ninguna
 * así, pero todo lo que recorre la selección bloque a bloque o pregunta si un bloque está dentro (//set, //replace,
 * //walls, //faces, //count, //copy, //move, //stack, la máscara {@code #sel}…) la respeta tal cual.
 *
 * <p>Como región plana ({@link FlatRegion}), sus columnas son las que tienen algún bloque: //overlay, //naturalize,
 * //forest y //flora trabajan solo en ellas. //expand y //contract se adaptan: ampliar hacia un lado es estirar cada
 * bloque hacia ese lado y reducirlo, quitar lo que queda a menos de esa distancia del borde por ese lado.</p>
 */
public final class BlockSetRegion extends AbstractRegion implements FlatRegion {
    private BlockSet blocks;
    private final int maxBlocks;
    private int revision;

    public BlockSetRegion(World world, BlockSet blocks, int maxBlocks) {
        super(world);
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("Una selección de bloques sueltos no puede estar vacía");
        }
        this.blocks = blocks;
        this.maxBlocks = maxBlocks;
    }

    /** Los bloques de la región (los de verdad: cambiarlos cambia la región). */
    public BlockSet blocks() {
        return blocks;
    }

    public int maxBlocks() {
        return maxBlocks;
    }

    /** Sube cada vez que una orden de WorldEdit cambia sus bloques (//expand, //contract, //shift, //move -s…). */
    public int revision() {
        return revision;
    }

    @Override
    public BlockVector3 getMinimumPoint() {
        int[] box = blocks.bounds();
        return BlockVector3.at(box[0], box[1], box[2]);
    }

    @Override
    public BlockVector3 getMaximumPoint() {
        int[] box = blocks.bounds();
        return BlockVector3.at(box[3], box[4], box[5]);
    }

    @Override
    public long getVolume() {
        return blocks.size();
    }

    @Override
    public boolean contains(BlockVector3 position) {
        return blocks.contains(position.x(), position.y(), position.z());
    }

    @Override
    public Iterator<BlockVector3> iterator() {
        LongIterator inner = blocks.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return inner.hasNext();
            }

            @Override
            public BlockVector3 next() {
                if (!inner.hasNext()) {
                    throw new NoSuchElementException();
                }
                long block = inner.nextLong();
                return BlockVector3.at(BlockSet.x(block), BlockSet.y(block), BlockSet.z(block));
            }
        };
    }

    /**
     * Estira cada bloque hacia cada {@code change} (un {@code //expand 5 up} añade los cinco de encima de cada
     * bloque). No puede pasar del máximo de bloques.
     */
    @Override
    public void expand(BlockVector3... changes) throws RegionOperationException {
        BlockSet result = blocks.copy();
        for (BlockVector3 change : changes) {
            int[] amounts = {change.x(), change.y(), change.z()};
            for (int axis = 0; axis < 3; axis++) {
                if (amounts[axis] == 0) {
                    continue;
                }
                int sign = Integer.signum(amounts[axis]);
                int distance = Math.abs(amounts[axis]);
                int minY = world == null ? -2048 : world.getMinY();
                int maxY = world == null ? 2047 : world.getMaxY();
                LongArrayList current = new LongArrayList(result.size());
                result.forEach(current::add);
                for (int index = 0; index < current.size(); index++) {
                    long block = current.getLong(index);
                    int[] at = {BlockSet.x(block), BlockSet.y(block), BlockSet.z(block)};
                    for (int step = 1; step <= distance; step++) {
                        at[axis] += sign;
                        if (!BlockSet.storable(at[1]) || at[1] < minY || at[1] > maxY) {
                            break;
                        }
                        result.add(at[0], at[1], at[2]);
                    }
                    if (result.size() > maxBlocks) {
                        throw failure("La selección pasaría de " + maxBlocks + " bloques");
                    }
                }
            }
        }
        blocks = result;
        revision++;
    }

    /**
     * Encoge por el lado contrario a cada {@code change}, como la caja de WorldEdit: {@code (0, -5, 0)} baja la cara
     * de arriba cinco bloques, así que se va todo lo que tiene el borde de arriba a cinco bloques o menos.
     */
    @Override
    public void contract(BlockVector3... changes) throws RegionOperationException {
        BlockSet result = blocks.copy();
        for (BlockVector3 change : changes) {
            int[] amounts = {change.x(), change.y(), change.z()};
            for (int axis = 0; axis < 3; axis++) {
                if (amounts[axis] == 0) {
                    continue;
                }
                // El borde que se mueve es el del lado contrario a la dirección del cambio.
                int toward = -Integer.signum(amounts[axis]);
                int distance = Math.abs(amounts[axis]);
                int along = axis;
                BlockSet before = result;
                LongOpenHashSet removed = new LongOpenHashSet();
                before.forEach(block -> {
                    int[] at = {BlockSet.x(block), BlockSet.y(block), BlockSet.z(block)};
                    for (int step = 1; step <= distance; step++) {
                        at[along] += toward;
                        if (!before.contains(at[0], at[1], at[2])) {
                            removed.add(block);
                            return;
                        }
                    }
                });
                result = before.copy();
                LongIterator iterator = removed.iterator();
                while (iterator.hasNext()) {
                    result.removePacked(iterator.nextLong());
                }
            }
        }
        if (result.isEmpty()) {
            throw failure("No quedaría ningún bloque seleccionado");
        }
        blocks = result;
        revision++;
    }

    @Override
    public void shift(BlockVector3 change) throws RegionOperationException {
        int[] box = blocks.bounds();
        if (!BlockSet.storable(box[1] + change.y()) || !BlockSet.storable(box[4] + change.y())) {
            throw failure("La selección se saldría del mundo");
        }
        blocks = blocks.shifted(change.x(), change.y(), change.z());
        revision++;
    }

    @Override
    public Set<BlockVector2> getChunks() {
        Set<BlockVector2> chunks = new HashSet<>();
        blocks.forEach(block -> chunks.add(BlockVector2.at(BlockSet.x(block) >> 4, BlockSet.z(block) >> 4)));
        return chunks;
    }

    @Override
    public Set<BlockVector3> getChunkCubes() {
        Set<BlockVector3> cubes = new HashSet<>();
        blocks.forEach(block -> cubes.add(BlockVector3.at(BlockSet.x(block) >> 4, BlockSet.y(block) >> 4,
                BlockSet.z(block) >> 4)));
        return cubes;
    }

    @Override
    public int getMinimumY() {
        return blocks.bounds()[1];
    }

    @Override
    public int getMaximumY() {
        return blocks.bounds()[4];
    }

    /** Las columnas con algún bloque seleccionado. */
    @Override
    public Iterable<BlockVector2> asFlatRegion() {
        LongOpenHashSet columns = new LongOpenHashSet();
        blocks.forEach(block -> columns.add((long) BlockSet.x(block) << 32 | BlockSet.z(block) & 0xFFFFFFFFL));
        List<BlockVector2> result = new ArrayList<>(columns.size());
        LongIterator iterator = columns.iterator();
        while (iterator.hasNext()) {
            long column = iterator.nextLong();
            result.add(BlockVector2.at((int) (column >> 32), (int) column));
        }
        return result;
    }

    @Override
    public BlockSetRegion clone() {
        return new BlockSetRegion(world, blocks.copy(), maxBlocks);
    }

    /**
     * El error con el texto tal cual: el constructor con {@code Component} lo traduce con la plataforma de
     * WorldEdit, y aquí no hay nada que traducir.
     */
    @SuppressWarnings("deprecation")
    private static RegionOperationException failure(String message) {
        return new RegionOperationException(message);
    }

    @Override
    public String toString() {
        return "BlockSetRegion[" + blocks.size() + "]";
    }
}
