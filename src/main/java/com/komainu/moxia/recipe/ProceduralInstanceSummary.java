package com.komainu.moxia.recipe;

public record ProceduralInstanceSummary(
        String id,
        ProceduralRecipe recipe,
        int pathPoints,
        int blockCount,
        long updatedAt
) {
}
