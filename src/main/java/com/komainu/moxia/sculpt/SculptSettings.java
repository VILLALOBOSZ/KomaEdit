package com.komainu.moxia.sculpt;

public record SculptSettings(
        SculptBrushType type,
        int radius,
        int height,
        int intensity,
        BrushFalloff falloff,
        int noise,
        int randomness,
        boolean hollow,
        BrushMask mask
) {
    public SculptSettings {
        if (radius < 1 || radius > 32) {
            throw new IllegalArgumentException("El radio debe estar entre 1 y 32");
        }
        if (height < 1 || height > 64) {
            throw new IllegalArgumentException("La altura debe estar entre 1 y 64");
        }
        if (intensity < 1 || intensity > 16) {
            throw new IllegalArgumentException("La intensidad debe estar entre 1 y 16");
        }
        if (noise < 0 || noise > 100) {
            throw new IllegalArgumentException("El ruido debe estar entre 0 y 100");
        }
        if (randomness < 0 || randomness > 100) {
            throw new IllegalArgumentException("La aleatoriedad debe estar entre 0 y 100");
        }
    }

    public static SculptSettings defaults(SculptBrushType type) {
        return new SculptSettings(type, 8, 8, 4, BrushFalloff.SMOOTH, 0, 0, false, BrushMask.DEFAULT);
    }

    public SculptSettings withType(SculptBrushType value) {
        return new SculptSettings(value, radius, height, intensity, falloff, noise, randomness, hollow, mask);
    }

    public SculptSettings withGeometry(int newRadius, int newHeight, int newIntensity, BrushFalloff newFalloff, boolean newHollow) {
        return new SculptSettings(type, newRadius, newHeight, newIntensity, newFalloff, noise, randomness, newHollow, mask);
    }

    public SculptSettings withTuning(int newNoise, int newRandomness) {
        return new SculptSettings(type, radius, height, intensity, falloff, newNoise, newRandomness, hollow, mask);
    }

    public SculptSettings withMask(BrushMask value) {
        return new SculptSettings(type, radius, height, intensity, falloff, noise, randomness, hollow, value);
    }
}
