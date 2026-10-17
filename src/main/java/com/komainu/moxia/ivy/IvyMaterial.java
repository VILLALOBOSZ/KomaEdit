package com.komainu.moxia.ivy;

/** Bloques de vanilla que pone la hiedra; el estado exacto lo completan las caras de {@link IvyBlock}. */
public enum IvyMaterial {
    VINE("minecraft:vine"),
    GLOW_LICHEN("minecraft:glow_lichen"),
    MOSS_CARPET("minecraft:moss_carpet"),
    OAK_LEAVES("minecraft:oak_leaves"),
    JUNGLE_LEAVES("minecraft:jungle_leaves"),
    AZALEA_LEAVES("minecraft:azalea_leaves"),
    FLOWERING_AZALEA_LEAVES("minecraft:flowering_azalea_leaves"),
    /** Punta de una tira de bayas brillantes (la de abajo del todo). */
    CAVE_VINES("minecraft:cave_vines"),
    /** Tramo de una tira de bayas brillantes con otro tramo o la punta debajo. */
    CAVE_VINES_PLANT("minecraft:cave_vines_plant"),
    SPORE_BLOSSOM("minecraft:spore_blossom");

    private final String id;

    IvyMaterial(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean leaves() {
        return this == OAK_LEAVES || this == JUNGLE_LEAVES || this == AZALEA_LEAVES || this == FLOWERING_AZALEA_LEAVES;
    }

    /** Enredaderas de caras múltiples: las caras dicen a qué bloques vecinos se agarran. */
    public boolean multiface() {
        return this == VINE || this == GLOW_LICHEN;
    }

    public boolean caveVines() {
        return this == CAVE_VINES || this == CAVE_VINES_PLANT;
    }
}
