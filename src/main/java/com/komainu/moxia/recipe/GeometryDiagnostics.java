package com.komainu.moxia.recipe;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

public final class GeometryDiagnostics {
    private static final int MAX_REPAIR_BLOCKS = 250_000;
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private GeometryDiagnostics() {
    }

    public static Result process(Set<BlockVector3> input, RecipeExtensions extensions) {
        return process(input, Set.of(), extensions);
    }

    public static Result process(
            Set<BlockVector3> input,
            Set<BlockVector3> accentInput,
            RecipeExtensions extensions
    ) {
        if ((!extensions.closeMicroGaps() && !extensions.removeIsolated()) || input.size() > MAX_REPAIR_BLOCKS) {
            Set<BlockVector3> blocks = Collections.unmodifiableSet(input);
            Set<BlockVector3> accents = retainedAccents(accentInput, blocks);
            return new Result(blocks, accents, new Report(
                    input.size(),
                    input.size(),
                    0,
                    0,
                    input.size() > MAX_REPAIR_BLOCKS && (extensions.closeMicroGaps() || extensions.removeIsolated())
            ));
        }

        Set<BlockVector3> blocks = new HashSet<>(input);
        int removed = extensions.removeIsolated() ? removeTinyComponents(blocks) : 0;
        int filled = extensions.closeMicroGaps() ? closeMicroGaps(blocks) : 0;
        Set<BlockVector3> resolvedBlocks = Set.copyOf(blocks);
        return new Result(
                resolvedBlocks,
                retainedAccents(accentInput, resolvedBlocks),
                new Report(input.size(), blocks.size(), filled, removed, false)
        );
    }

    private static Set<BlockVector3> retainedAccents(
            Set<BlockVector3> accentInput,
            Set<BlockVector3> blocks
    ) {
        if (accentInput.isEmpty()) {
            return Set.of();
        }
        Set<BlockVector3> accents = new HashSet<>(accentInput);
        accents.retainAll(blocks);
        return Set.copyOf(accents);
    }

    private static int closeMicroGaps(Set<BlockVector3> blocks) {
        Map<BlockVector3, Byte> candidates = new HashMap<>();
        int processed = 0;
        for (BlockVector3 block : blocks) {
            checkCancelled(processed++);
            for (int[] offset : NEIGHBORS) {
                BlockVector3 candidate = block.add(offset[0], offset[1], offset[2]);
                if (!blocks.contains(candidate)) {
                    candidates.merge(candidate, (byte) 1, (first, second) -> (byte) (first + second));
                }
            }
        }
        List<BlockVector3> additions = candidates.entrySet().stream()
                .filter(entry -> entry.getValue() >= 5)
                .map(Map.Entry::getKey)
                .toList();
        blocks.addAll(additions);
        return additions.size();
    }

    private static int removeTinyComponents(Set<BlockVector3> blocks) {
        Set<BlockVector3> unseen = new HashSet<>(blocks);
        List<BlockVector3> removals = new ArrayList<>();
        ArrayDeque<BlockVector3> queue = new ArrayDeque<>();
        int processed = 0;
        while (!unseen.isEmpty()) {
            checkCancelled(processed++);
            BlockVector3 start = unseen.iterator().next();
            List<BlockVector3> component = new ArrayList<>();
            unseen.remove(start);
            queue.add(start);
            while (!queue.isEmpty()) {
                checkCancelled(processed++);
                BlockVector3 block = queue.removeFirst();
                component.add(block);
                for (int[] offset : NEIGHBORS) {
                    BlockVector3 neighbor = block.add(offset[0], offset[1], offset[2]);
                    if (unseen.remove(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
            if (component.size() <= 4) {
                removals.addAll(component);
            }
        }
        blocks.removeAll(removals);
        return removals.size();
    }

    private static void checkCancelled(int processed) {
        if ((processed & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }

    public record Result(Set<BlockVector3> blocks, Set<BlockVector3> accentBlocks, Report report) {
    }

    public record Report(int original, int result, int gapsFilled, int isolatedRemoved, boolean repairSkipped) {
        public String summary() {
            if (repairSkipped) {
                return "QA omitido por tamaño; geometría conservada";
            }
            if (gapsFilled == 0 && isolatedRemoved == 0) {
                return "QA sin correcciones";
            }
            return "QA: +" + gapsFilled + " huecos · -" + isolatedRemoved + " aislados";
        }
    }
}
