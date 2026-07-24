package com.komainu.moxia.recipe;

import com.komainu.moxia.path.PathSnapshot;

import java.util.Arrays;

public record ProceduralInstance(
        String id,
        ProceduralRecipe recipe,
        PathSnapshot path,
        long[] positions,
        int[] stateIds,
        long updatedAt
) {
    public ProceduralInstance {
        if (id == null || !id.matches("[a-f0-9-]{36}")) {
            throw new IllegalArgumentException("Identificador de instancia no válido");
        }
        if (recipe == null || path == null || path.points().size() < 2) {
            throw new IllegalArgumentException("La instancia procedural está incompleta");
        }
        positions = positions == null ? new long[0] : Arrays.copyOf(positions, positions.length);
        if (stateIds == null) {
            stateIds = new int[positions.length];
            Arrays.fill(stateIds, -1);
        } else {
            stateIds = Arrays.copyOf(stateIds, stateIds.length);
        }
        if (positions.length > 1_500_000) {
            throw new IllegalArgumentException("La instancia supera el límite de bloques");
        }
        if (stateIds.length != positions.length) {
            throw new IllegalArgumentException("Los estados de la instancia no coinciden con sus posiciones");
        }
    }

    @Override
    public long[] positions() {
        return Arrays.copyOf(positions, positions.length);
    }

    @Override
    public int[] stateIds() {
        return Arrays.copyOf(stateIds, stateIds.length);
    }

    public ProceduralInstanceSummary summary() {
        return new ProceduralInstanceSummary(
                id,
                recipe,
                path.points().size(),
                positions.length,
                updatedAt
        );
    }
}
