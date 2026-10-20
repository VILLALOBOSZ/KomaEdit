package com.komainu.moxia.symmetry;

/**
 * Simetría de un jugador.
 *
 * @param center    centro, o {@code null} si aún no se ha fijado (se fija en el jugador al activarla)
 * @param world     dimensión del centro ({@code minecraft:overworld}…); la simetría solo actúa en ella
 * @param worldEdit si también se reflejan las ediciones de WorldEdit y KomaEdit del jugador
 */
public record SymmetrySettings(boolean active, SymmetryMode mode, SymmetryCenter center, String world,
                               boolean worldEdit) {
    public static final SymmetrySettings DEFAULT = new SymmetrySettings(false, SymmetryMode.DEFAULT, null, null, false);

    public SymmetrySettings withActive(boolean value) {
        return new SymmetrySettings(value, mode, center, world, worldEdit);
    }

    public SymmetrySettings withMode(SymmetryMode value) {
        return new SymmetrySettings(active, value, center, world, worldEdit);
    }

    public SymmetrySettings withCenter(SymmetryCenter value, String dimension) {
        return new SymmetrySettings(active, mode, value, dimension, worldEdit);
    }

    public SymmetrySettings withWorldEdit(boolean value) {
        return new SymmetrySettings(active, mode, center, world, value);
    }

    /** ¿Actúa en esta dimensión? */
    public boolean appliesIn(String dimension) {
        return active && center != null && dimension.equals(world);
    }
}
