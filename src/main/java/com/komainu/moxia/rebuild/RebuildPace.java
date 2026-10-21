package com.komainu.moxia.rebuild;

/**
 * El ritmo de una reconstrucción, en ticks de animación (20 por segundo a velocidad 1).
 *
 * @param blocksPerTick bloques por tick (puede ser menos de 1: 0,25 es un bloque cada 4 ticks)
 * @param durationTicks duración total; si es mayor que 0 manda sobre {@code blocksPerTick}
 * @param layerPause    pausa al empezar cada capa, rodaja, onda o fase
 * @param sameDelay     retraso extra cuando el bloque es el mismo que el anterior (como en Axiom)
 * @param otherDelay    retraso extra cuando el bloque cambia respecto al anterior
 * @param leadIn        espera con la obra vacía antes del primer bloque
 */
public record RebuildPace(double blocksPerTick, double durationTicks, double layerPause, double sameDelay,
                          double otherDelay, double leadIn) {
    public static final double MIN_BLOCKS_PER_TICK = 0.01;
    public static final double MAX_BLOCKS_PER_TICK = 4096.0;
    /** Ninguna pausa ni duración pasa de una hora. */
    public static final double MAX_TICKS = 72_000.0;
    public static final RebuildPace DEFAULT = new RebuildPace(4.0, 0.0, 0.0, 0.0, 0.0, 20.0);

    public RebuildPace {
        if (!Double.isFinite(blocksPerTick) || blocksPerTick < MIN_BLOCKS_PER_TICK
                || blocksPerTick > MAX_BLOCKS_PER_TICK) {
            throw new IllegalArgumentException("Los bloques por tick van de " + decimal(MIN_BLOCKS_PER_TICK) + " a "
                    + decimal(MAX_BLOCKS_PER_TICK));
        }
        durationTicks = ticks(durationTicks, "La duración");
        layerPause = ticks(layerPause, "La pausa entre capas");
        sameDelay = ticks(sameDelay, "El retraso entre bloques iguales");
        otherDelay = ticks(otherDelay, "El retraso entre bloques distintos");
        leadIn = ticks(leadIn, "La espera antes de empezar");
    }

    public RebuildPace withBlocksPerTick(double value) {
        return new RebuildPace(value, durationTicks, layerPause, sameDelay, otherDelay, leadIn);
    }

    public RebuildPace withDuration(double ticks) {
        return new RebuildPace(blocksPerTick, ticks, layerPause, sameDelay, otherDelay, leadIn);
    }

    public RebuildPace withLayerPause(double ticks) {
        return new RebuildPace(blocksPerTick, durationTicks, ticks, sameDelay, otherDelay, leadIn);
    }

    public RebuildPace withDelays(double same, double other) {
        return new RebuildPace(blocksPerTick, durationTicks, layerPause, same, other, leadIn);
    }

    public RebuildPace withLeadIn(double ticks) {
        return new RebuildPace(blocksPerTick, durationTicks, layerPause, sameDelay, otherDelay, ticks);
    }

    private static double ticks(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > MAX_TICKS) {
            throw new IllegalArgumentException(name + " va de 0 a 3600 segundos");
        }
        return value;
    }

    private static String decimal(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value).replace('.', ',');
    }
}
