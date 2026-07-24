package com.komainu.moxia.client.recipe;

import com.komainu.moxia.recipe.ProceduralInstanceSummary;

import java.util.List;

public final class ClientInstanceCatalog {
    private static volatile List<ProceduralInstanceSummary> instances = List.of();

    private ClientInstanceCatalog() {
    }

    public static void update(List<ProceduralInstanceSummary> values) {
        instances = List.copyOf(values);
    }

    public static List<ProceduralInstanceSummary> instances() {
        return instances;
    }

    public static void clear() {
        instances = List.of();
    }
}
