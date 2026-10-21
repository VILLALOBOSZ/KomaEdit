package com.komainu.moxia.fluid;

import java.util.Locale;

/** Lo que ponen las herramientas de agua: agua, lava o nieve en polvo (esta no corre). */
public enum FluidKind {
    WATER("agua", "minecraft:water"),
    LAVA("lava", "minecraft:lava"),
    POWDER_SNOW("nieve", "minecraft:powder_snow");

    private final String id;
    private final String blockId;

    FluidKind(String id, String blockId) {
        this.id = id;
        this.blockId = blockId;
    }

    /** Nombre en las órdenes y en el chat. */
    public String id() {
        return id;
    }

    public String blockId() {
        return blockId;
    }

    /** Si corre como el agua y la lava (la nieve en polvo se queda donde se pone). */
    public boolean flows() {
        return this != POWDER_SNOW;
    }

    public String displayName() {
        return switch (this) {
            case WATER -> "agua";
            case LAVA -> "lava";
            case POWDER_SNOW -> "nieve en polvo";
        };
    }

    /**
     * {@code agua}/{@code water}, {@code lava} y, si {@code snow}, {@code nieve}/{@code powder_snow}.
     *
     * @throws IllegalArgumentException si no es ninguno
     */
    public static FluidKind parse(String text, boolean snow) {
        String value = text.toLowerCase(Locale.ROOT);
        switch (value) {
            case "agua", "water", "minecraft:water" -> {
                return WATER;
            }
            case "lava", "minecraft:lava" -> {
                return LAVA;
            }
            case "nieve", "snow", "powder_snow", "polvo", "minecraft:powder_snow" -> {
                if (snow) {
                    return POWDER_SNOW;
                }
            }
            default -> {
            }
        }
        throw new IllegalArgumentException(snow ? "El fluido es agua, lava o nieve, no «" + text + "»"
                : "El líquido es agua o lava, no «" + text + "»");
    }

    /** Si el texto nombra un fluido (para leer las órdenes, donde va opcional). */
    public static boolean names(String text, boolean snow) {
        try {
            parse(text, snow);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
