package com.komainu.moxia.rebuild;

import java.util.Locale;
import java.util.Optional;

/** Eje y sentido del barrido del orden {@link RebuildOrder#AXIS}. */
public enum RebuildAxis {
    X_POSITIVE(0, false, "x"),
    X_NEGATIVE(0, true, "-x"),
    Y_POSITIVE(1, false, "y"),
    Y_NEGATIVE(1, true, "-y"),
    Z_POSITIVE(2, false, "z"),
    Z_NEGATIVE(2, true, "-z");

    private final int axis;
    private final boolean negative;
    private final String id;

    RebuildAxis(int axis, boolean negative, String id) {
        this.axis = axis;
        this.negative = negative;
        this.id = id;
    }

    /** 0 = X, 1 = Y, 2 = Z. */
    public int axis() {
        return axis;
    }

    /** Si barre de mayor a menor coordenada. */
    public boolean negative() {
        return negative;
    }

    public String id() {
        return id;
    }

    /**
     * {@code x}, {@code +x}, {@code -x}, {@code x-}… y también {@code este}/{@code oeste}, {@code arriba}/{@code abajo}
     * y {@code sur}/{@code norte}.
     */
    public static Optional<RebuildAxis> parse(String text) {
        String value = RebuildOrder.fold(text).toLowerCase(Locale.ROOT);
        return Optional.ofNullable(switch (value) {
            case "x", "+x", "x+", "este", "east" -> X_POSITIVE;
            case "-x", "x-", "oeste", "west" -> X_NEGATIVE;
            case "y", "+y", "y+", "arriba", "up" -> Y_POSITIVE;
            case "-y", "y-", "abajo", "down" -> Y_NEGATIVE;
            case "z", "+z", "z+", "sur", "south" -> Z_POSITIVE;
            case "-z", "z-", "norte", "north" -> Z_NEGATIVE;
            default -> null;
        });
    }
}
