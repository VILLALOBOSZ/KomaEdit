package com.komainu.moxia.sculpt;

public record SculptProfile(SculptSettings settings, String pattern) {
    public SculptProfile {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("El patrón no puede estar vacío");
        }
    }

    public SculptProfile withSettings(SculptSettings value) {
        return new SculptProfile(value, pattern);
    }
}
