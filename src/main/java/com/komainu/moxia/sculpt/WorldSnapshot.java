package com.komainu.moxia.sculpt;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.Objects;

public final class WorldSnapshot {
    private static final BlockState AIR = Objects.requireNonNull(BlockTypes.AIR).getDefaultState();

    private final int minimumX;
    private final int minimumY;
    private final int minimumZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BlockState[] blocks;

    private WorldSnapshot(
            int minimumX,
            int minimumY,
            int minimumZ,
            int sizeX,
            int sizeY,
            int sizeZ,
            BlockState[] blocks
    ) {
        this.minimumX = minimumX;
        this.minimumY = minimumY;
        this.minimumZ = minimumZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = blocks;
    }

    public static WorldSnapshot capture(World world, BlockVector3 center, int horizontalRadius, int verticalRadius) {
        Capture capture = begin(world, center, horizontalRadius, verticalRadius);
        while (!capture.complete()) {
            capture.step(Integer.MAX_VALUE, Long.MAX_VALUE);
        }
        return capture.finish();
    }

    public static Capture begin(World world, BlockVector3 center, int horizontalRadius, int verticalRadius) {
        int minimumX = center.x() - horizontalRadius;
        int maximumX = center.x() + horizontalRadius;
        int minimumY = Math.max(world.getMinY(), center.y() - verticalRadius);
        int maximumY = Math.min(world.getMaxY(), center.y() + verticalRadius);
        int minimumZ = center.z() - horizontalRadius;
        int maximumZ = center.z() + horizontalRadius;
        int sizeX = maximumX - minimumX + 1;
        int sizeY = maximumY - minimumY + 1;
        int sizeZ = maximumZ - minimumZ + 1;
        BlockState[] blocks = new BlockState[Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ)];
        return new Capture(world, minimumX, minimumY, minimumZ, sizeX, sizeY, sizeZ, blocks);
    }

    public BlockState get(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return AIR;
        }
        int localX = x - minimumX;
        int localY = y - minimumY;
        int localZ = z - minimumZ;
        return blocks[(localY * sizeZ + localZ) * sizeX + localX];
    }

    public boolean contains(int x, int y, int z) {
        return x >= minimumX && x < minimumX + sizeX
                && y >= minimumY && y < minimumY + sizeY
                && z >= minimumZ && z < minimumZ + sizeZ;
    }

    public int minimumY() {
        return minimumY;
    }

    public int maximumY() {
        return minimumY + sizeY - 1;
    }

    public static final class Capture {
        private final World world;
        private final int minimumX;
        private final int minimumY;
        private final int minimumZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final BlockState[] blocks;
        private int index;

        private Capture(
                World world,
                int minimumX,
                int minimumY,
                int minimumZ,
                int sizeX,
                int sizeY,
                int sizeZ,
                BlockState[] blocks
        ) {
            this.world = world;
            this.minimumX = minimumX;
            this.minimumY = minimumY;
            this.minimumZ = minimumZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.blocks = blocks;
        }

        public int step(int maximumBlocks, long deadlineNanos) {
            int processed = 0;
            int layerSize = sizeX * sizeZ;
            while (index < blocks.length && processed < maximumBlocks && System.nanoTime() < deadlineNanos) {
                int localY = index / layerSize;
                int remainder = index - localY * layerSize;
                int localZ = remainder / sizeX;
                int localX = remainder - localZ * sizeX;
                blocks[index] = world.getBlock(BlockVector3.at(
                        minimumX + localX,
                        minimumY + localY,
                        minimumZ + localZ
                ));
                index++;
                processed++;
            }
            return processed;
        }

        public boolean complete() {
            return index >= blocks.length;
        }

        public int captured() {
            return index;
        }

        public int total() {
            return blocks.length;
        }

        public WorldSnapshot finish() {
            if (!complete()) {
                throw new IllegalStateException("La captura todavía no está completa");
            }
            return new WorldSnapshot(minimumX, minimumY, minimumZ, sizeX, sizeY, sizeZ, blocks);
        }
    }
}
