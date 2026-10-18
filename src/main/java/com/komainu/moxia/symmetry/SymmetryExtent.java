package com.komainu.moxia.symmetry;

import com.komainu.moxia.symmetry.SymmetryGeometry.Point;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.border.WorldBorder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extensión de WorldEdit que, por cada bloque que escribe una edición, escribe también sus copias simétricas con el
 * estado girado o reflejado. Las copias que caen sobre el propio bloque, fuera de la altura del mundo o fuera del borde
 * se saltan, y al pasar de {@code limit} copias deja de copiar (la edición sigue) y avisa una vez.
 */
final class SymmetryExtent extends AbstractDelegateExtent {
    private final SymmetryCenter center;
    private final List<SymmetryTransform> transforms = new ArrayList<>();
    private final List<Map<BlockState, BlockState>> states = new ArrayList<>();
    private final int bottomY;
    private final int topY;
    private final WorldBorder border;
    private final long limit;
    private final Runnable onLimit;
    private final long[] written;
    private long copies;
    private boolean stopped;

    SymmetryExtent(Extent extent, SymmetryMode mode, SymmetryCenter center, ServerWorld world, long limit,
                   Runnable onLimit) {
        super(extent);
        this.center = center;
        for (SymmetryTransform transform : SymmetryGeometry.transforms(mode)) {
            if (!transform.identity()) {
                transforms.add(transform);
                states.add(new HashMap<>());
            }
        }
        this.bottomY = world.getBottomY();
        this.topY = world.getTopY();
        this.border = world.getWorldBorder();
        this.limit = limit;
        this.onLimit = onLimit;
        this.written = new long[transforms.size()];
    }

    @Override
    public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 location, B block) throws WorldEditException {
        boolean changed = super.setBlock(location, block);
        if (stopped) {
            return changed;
        }
        int count = 0;
        for (int index = 0; index < transforms.size(); index++) {
            Point point = SymmetryGeometry.apply(transforms.get(index), center, location.x(), location.y(),
                    location.z());
            if (point.x() == location.x() && point.y() == location.y() && point.z() == location.z()) {
                continue;
            }
            long key = BlockPos.asLong(point.x(), point.y(), point.z());
            if (seen(key, count)) {
                continue;
            }
            written[count++] = key;
            if (point.y() < bottomY || point.y() >= topY || !border.contains(point.x() + 0.5, point.z() + 0.5)) {
                continue;
            }
            if (copies >= limit) {
                stopped = true;
                onLimit.run();
                break;
            }
            copies++;
            super.setBlock(BlockVector3.at(point.x(), point.y(), point.z()), transform(block, index));
        }
        return changed;
    }

    private boolean seen(long key, int count) {
        for (int index = 0; index < count; index++) {
            if (written[index] == key) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private <B extends BlockStateHolder<B>> B transform(B block, int index) {
        BlockState turned = states.get(index).computeIfAbsent(block.toImmutableState(), state -> FabricAdapter.adapt(
                SymmetryStates.transform(FabricAdapter.adapt(state), transforms.get(index))));
        if (block instanceof BaseBlock base) {
            return (B) turned.toBaseBlock(base.getNbtReference());
        }
        return (B) turned;
    }
}
