package com.komainu.moxia.region;

import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code moss}: manchas de musgo sobre las caras de arriba de la selección, como {@code //ezmoss} de ezEdits. Con un
 * patrón de bloque entero ({@code moss_block}) se cambian los bloques de la superficie; con uno que no ocupa el
 * bloque entero ({@code moss_carpet}, flores, nieve…) se coloca encima, en el aire.
 */
public final class MossOperation implements RegionOperation {
    private final Pattern pattern;
    private final boolean cover;
    private final double amount;
    private final int[] radii;
    private final int iterations;
    private final long seed;

    /**
     * @param cover {@code true} si el patrón va encima de la superficie (alfombra) en lugar de sustituirla
     */
    public MossOperation(Pattern pattern, boolean cover, double amount, int[] radii, int iterations, long seed) {
        this.pattern = pattern;
        this.cover = cover;
        this.amount = amount;
        this.radii = radii.clone();
        this.iterations = iterations;
        this.seed = seed;
    }

    @Override
    public void apply(RegionSnapshot snapshot, RegionEdits edits) {
        List<int[]> candidates = new ArrayList<>();
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    edits.checkCancelled();
                    if (!snapshot.selected(x, y, z)) {
                        continue;
                    }
                    boolean candidate = cover
                            ? snapshot.isAir(x, y, z) && snapshot.isSolid(x, y - 1, z)
                            : snapshot.isSolid(x, y, z) && !snapshot.isSolid(x, y + 1, z)
                            && !snapshot.isLiquid(x, y + 1, z);
                    if (candidate) {
                        candidates.add(new int[]{x, y, z});
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(cover
                    ? "No hay aire sobre suelo en la selección donde poner el musgo"
                    : "La selección no tiene caras de arriba al aire donde poner el musgo");
        }
        int[][] positions = candidates.toArray(int[][]::new);
        boolean[] chosen = RegionRules.moss(positions, amount, radii, iterations, seed, edits::checkCancelled);
        for (int index = 0; index < positions.length; index++) {
            if (chosen[index]) {
                int[] position = positions[index];
                edits.set(position[0], position[1], position[2],
                        pattern.applyBlock(BlockVector3.at(position[0], position[1], position[2])).toImmutableState());
            }
        }
    }
}
