package com.komainu.moxia.capability;

/**
 * Límites que fija el servidor para las capacidades ({@code config/komaedit/capabilities.json}).
 *
 * @param maxReach        alcance máximo en bloques de «Alcance largo» y «Colocar en el aire» (5 a 64; 64 es el tope
 *                        del atributo de Minecraft)
 * @param maxLayerBlocks  bloques como mucho por acción de extruir, encoger o arrastrar
 * @param maxSmearLength  bloques como mucho que se estira un arrastre
 * @param maxFlySpeed     velocidad de vuelo máxima en décimas del valor normal (10 = normal, 100 = diez veces)
 * @param actionsPerTick  acciones de un bloque por jugador y tick (lo demás se descarta)
 */
public record CapabilityLimits(
        int maxReach,
        int maxLayerBlocks,
        int maxSmearLength,
        int maxFlySpeed,
        int actionsPerTick
) {
    public static final int VANILLA_MAX_REACH = 64;
    public static final CapabilityLimits DEFAULT = new CapabilityLimits(64, 4096, 64, 100, 4);

    public CapabilityLimits {
        if (maxReach < 5 || maxReach > VANILLA_MAX_REACH) {
            throw new IllegalArgumentException("maxReach must be between 5 and 64");
        }
        if (maxLayerBlocks < 1 || maxLayerBlocks > 65_536) {
            throw new IllegalArgumentException("maxLayerBlocks must be between 1 and 65536");
        }
        if (maxSmearLength < 1 || maxSmearLength > 256) {
            throw new IllegalArgumentException("maxSmearLength must be between 1 and 256");
        }
        if (maxFlySpeed < 10 || maxFlySpeed > 200) {
            throw new IllegalArgumentException("maxFlySpeed must be between 10 and 200");
        }
        if (actionsPerTick < 1 || actionsPerTick > 20) {
            throw new IllegalArgumentException("actionsPerTick must be between 1 and 20");
        }
    }
}
