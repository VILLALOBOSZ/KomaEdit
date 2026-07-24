package com.komainu.moxia.shape;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public record NaturalAssetGeometry(Set<BlockVector3> blocks, Set<BlockVector3> accentBlocks) {
    public NaturalAssetGeometry {
        Set<BlockVector3> resolvedBlocks = new HashSet<>(blocks);
        Set<BlockVector3> resolvedAccents = new HashSet<>(accentBlocks);
        resolvedAccents.retainAll(resolvedBlocks);
        blocks = Collections.unmodifiableSet(resolvedBlocks);
        accentBlocks = Collections.unmodifiableSet(resolvedAccents);
    }
}
