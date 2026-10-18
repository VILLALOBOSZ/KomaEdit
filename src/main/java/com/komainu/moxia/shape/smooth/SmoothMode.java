package com.komainu.moxia.shape.smooth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Perfil de smoothblocks: qué piezas pueden sustituir a los bloques de superficie, con los nombres de ezEdits.
 *
 * <p>El orden es parte del formato: las recetas y el Studio guardan el ordinal ({@code smooth}). Los perfiles
 * nuevos van siempre al final.</p>
 */
public enum SmoothMode {
    NONE("none", "None", "Sin piezas", "No", EnumSet.noneOf(PieceVariant.class), false,
            "off", "no", "ninguno", "nada", "full", "bloques"),
    SLABS("slabs", "Slabs", "Losas", "Losas", EnumSet.of(PieceVariant.SLAB), false,
            "slab", "slabsonly", "losa", "losas"),
    /** Losas y escaleras con las esquinas de las escaleras elegidas en 3D. */
    STAIRS("stairs", "SlabsAndStairs", "Losas y escaleras", "Escaleras",
            EnumSet.of(PieceVariant.SLAB, PieceVariant.STAIR), true,
            "slabsandstairs", "slabsstairs", "sas", "both", "escaleras", "escalera", "losasyescaleras", "ambas"),
    /** Losas y escaleras rectas: más rápido; las esquinas solo salen donde Minecraft las fuerza. */
    STAIRS_2D("stairs2d", "SlabsAndStairs2D", "Losas y escaleras 2D", "Esc. 2D",
            EnumSet.of(PieceVariant.SLAB, PieceVariant.STAIR), false,
            "slabsandstairs2d", "slabsstairs2d", "sas2d", "escaleras2d", "losasyescaleras2d"),
    /** Capas de nieve: alturas de 1/8 de bloque, solo apoyadas en el suelo. */
    LAYERS("layers", "Layers", "Capas de nieve", "Capas", EnumSet.of(PieceVariant.LAYER), false,
            "layer", "capas", "capa", "snow", "nieve", "snowlayers"),
    /** Losas, escaleras y muros: los muros toman las columnas finas que no llenan ni media pieza. */
    WALLS("ssw", "SSW", "Losas, escaleras y muros", "Muros",
            EnumSet.of(PieceVariant.SLAB, PieceVariant.STAIR, PieceVariant.WALL), true,
            "slabsstairswalls", "slabsandstairsandwalls", "sasw", "walls", "muros", "losasescalerasymuros");

    private final String id;
    private final String ezName;
    private final String displayName;
    private final String shortName;
    private final Set<PieceVariant> variants;
    private final boolean corners;
    private final String[] aliases;
    private final byte[] candidates;

    SmoothMode(
            String id,
            String ezName,
            String displayName,
            String shortName,
            Set<PieceVariant> variants,
            boolean corners,
            String... aliases
    ) {
        this.id = id;
        this.ezName = ezName;
        this.displayName = displayName;
        this.shortName = shortName;
        this.variants = Set.copyOf(variants);
        this.corners = corners;
        this.aliases = aliases;
        this.candidates = candidates(variants);
    }

    public String id() {
        return id;
    }

    /** Nombre del perfil en ezEdits ({@code SlabsAndStairs}). */
    public String ezName() {
        return ezName;
    }

    public String displayName() {
        return displayName;
    }

    /** Para controles estrechos como las filas del Studio. */
    public String shortName() {
        return shortName;
    }

    /** Tipos de pieza que usa el perfil. */
    public Set<PieceVariant> variants() {
        return variants;
    }

    public boolean uses(PieceVariant variant) {
        return variants.contains(variant);
    }

    /**
     * Si las esquinas de las escaleras se eligen mirando a los vecinos ({@link PieceSolver}). Sin esto las
     * escaleras se eligen rectas, una a una, y las esquinas salen solo donde Minecraft las fuerza.
     */
    public boolean solvesCorners() {
        return corners;
    }

    /**
     * Piezas que se prueban en cada bloque, de la más sencilla a la más compleja: ante un empate gana la primera.
     * Las escaleras van rectas; sus esquinas dependen de los vecinos. No se modifica.
     */
    byte[] candidates() {
        return candidates;
    }

    public static SmoothMode byIndex(int index) {
        SmoothMode[] values = values();
        return values[Math.floorMod(index, values.length)];
    }

    public static SmoothMode byId(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (SmoothMode mode : values()) {
            if (mode.id.equals(normalized) || mode.ezName.toLowerCase(Locale.ROOT).equals(normalized)
                    || List.of(mode.aliases).contains(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Perfil de piezas desconocido: " + input + " (" + names() + ")");
    }

    /** Nombres de ezEdits de los perfiles con piezas, para los mensajes de ayuda. */
    public static String names() {
        return Arrays.stream(values())
                .filter(mode -> mode != NONE)
                .map(SmoothMode::ezName)
                .collect(Collectors.joining(", "));
    }

    private static byte[] candidates(Set<PieceVariant> variants) {
        List<Byte> pieces = new ArrayList<>();
        if (variants.contains(PieceVariant.LAYER)) {
            for (int layers = 1; layers <= SmoothPiece.MAX_LAYERS; layers++) {
                pieces.add(SmoothPiece.layers(layers));
            }
        }
        if (variants.contains(PieceVariant.SLAB)) {
            pieces.add(SmoothPiece.slab(false));
            pieces.add(SmoothPiece.slab(true));
        }
        if (variants.contains(PieceVariant.STAIR)) {
            for (boolean top : new boolean[]{false, true}) {
                for (int facing : new int[]{SmoothPiece.NORTH, SmoothPiece.EAST, SmoothPiece.SOUTH,
                        SmoothPiece.WEST}) {
                    pieces.add(SmoothPiece.stairs(facing, top, SmoothPiece.STRAIGHT));
                }
            }
        }
        if (variants.contains(PieceVariant.WALL)) {
            pieces.add(SmoothPiece.wall());
        }
        byte[] result = new byte[pieces.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = pieces.get(index);
        }
        return result;
    }
}
