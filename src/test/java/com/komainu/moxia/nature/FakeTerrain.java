package com.komainu.moxia.nature;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.function.IntBinaryOperator;

/** Terreno de prueba: un mapa de alturas (tierra arriba, piedra debajo) y bloques sueltos encima. */
final class FakeTerrain implements NatureTerrain {
    private final IntBinaryOperator height;
    private final Long2ObjectOpenHashMap<Kind> extra = new Long2ObjectOpenHashMap<>();

    FakeTerrain(IntBinaryOperator height) {
        this.height = height;
    }

    static FakeTerrain flat(int y) {
        return new FakeTerrain((x, z) -> y);
    }

    /** Colina suave con su cima en el origen. */
    static FakeTerrain hill(int top, double radius) {
        return new FakeTerrain((x, z) -> (int) Math.floor(top - (x * x + z * z) / (radius * radius) * 4.0));
    }

    /** Ladera inclinada en el eje x. */
    static FakeTerrain slope(int y, double grade) {
        return new FakeTerrain((x, z) -> (int) Math.floor(y - x * grade));
    }

    FakeTerrain with(int x, int y, int z, Kind kind) {
        extra.put(PackedBlockPlan.pack(x, y, z), kind);
        return this;
    }

    int height(int x, int z) {
        return height.applyAsInt(x, z);
    }

    @Override
    public Kind kind(int x, int y, int z) {
        Kind placed = extra.get(PackedBlockPlan.pack(x, y, z));
        if (placed != null) {
            return placed;
        }
        int top = height.applyAsInt(x, z);
        if (y > top) {
            return Kind.FREE;
        }
        return y >= top - 3 ? Kind.SOIL : Kind.STONE;
    }
}
