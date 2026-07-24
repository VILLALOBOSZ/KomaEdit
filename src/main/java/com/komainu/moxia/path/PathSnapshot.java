package com.komainu.moxia.path;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.List;

public record PathSnapshot(List<BlockVector3> points, boolean closed, String source) {
    public PathSnapshot {
        points = List.copyOf(points);
    }
}
