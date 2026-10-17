package com.komainu.moxia.ivy;

/**
 * Un bloque de la hiedra. {@code faces} es una máscara de caras ({@code 1 << IvyGrower.NORTH}…) con el significado
 * de Minecraft: la enredadera está pegada al vecino de ese lado. {@code berries} solo cuenta en las bayas brillantes.
 */
public record IvyBlock(int x, int y, int z, IvyMaterial material, int faces, boolean berries) {
    public boolean hasFace(int face) {
        return (faces & (1 << face)) != 0;
    }

    /** Estado en texto de Minecraft, útil en registros y pruebas: {@code minecraft:vine[north=true,…]}. */
    public String stateString() {
        StringBuilder text = new StringBuilder(material.id());
        switch (material) {
            case VINE -> {
                text.append('[');
                appendFaces(text, IvyGrower.UP, IvyGrower.NORTH, IvyGrower.SOUTH, IvyGrower.WEST, IvyGrower.EAST);
                text.append(']');
            }
            case GLOW_LICHEN -> {
                text.append('[');
                appendFaces(text, IvyGrower.DOWN, IvyGrower.UP, IvyGrower.NORTH, IvyGrower.SOUTH, IvyGrower.WEST,
                        IvyGrower.EAST);
                text.append(",waterlogged=false]");
            }
            case OAK_LEAVES, JUNGLE_LEAVES, AZALEA_LEAVES, FLOWERING_AZALEA_LEAVES ->
                    text.append("[distance=7,persistent=true,waterlogged=false]");
            case CAVE_VINES -> text.append("[age=25,berries=").append(berries).append(']');
            case CAVE_VINES_PLANT -> text.append("[berries=").append(berries).append(']');
            case SPORE_BLOSSOM, MOSS_CARPET -> {
            }
        }
        return text.toString();
    }

    private void appendFaces(StringBuilder text, int... order) {
        for (int index = 0; index < order.length; index++) {
            if (index > 0) {
                text.append(',');
            }
            text.append(IvyGrower.FACE_NAMES[order[index]]).append('=').append(hasFace(order[index]));
        }
    }
}
