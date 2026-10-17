package com.komainu.moxia.studio;

import java.util.Locale;

/**
 * Aviso previo a Construir a partir del recuento del holograma. El límite real lo decide el
 * servidor ({@code maxBlocksPerOperation}); el cliente solo conoce el de serie y avisa con él.
 */
public final class BuildEstimate {
    /** A partir de aquí se pide confirmar con una segunda pulsación. */
    public static final int LARGE = 250_000;
    /** {@code maxBlocksPerOperation} de serie. */
    public static final int DEFAULT_SERVER_LIMIT = 1_500_000;
    /** Tiempo para la segunda pulsación. */
    public static final long CONFIRM_MILLIS = 5_000L;

    private long armedAt = Long.MIN_VALUE;
    private int armedBlocks = -1;

    public enum Level {
        UNKNOWN,
        NORMAL,
        LARGE,
        OVER_DEFAULT_LIMIT
    }

    public static Level level(int blocks) {
        if (blocks < 0) {
            return Level.UNKNOWN;
        }
        if (blocks > DEFAULT_SERVER_LIMIT) {
            return Level.OVER_DEFAULT_LIMIT;
        }
        return blocks >= LARGE ? Level.LARGE : Level.NORMAL;
    }

    /**
     * Si se puede construir ya. Una obra grande necesita dos pulsaciones en {@link #CONFIRM_MILLIS};
     * si el recuento cambia entre medias, vuelve a pedirse.
     */
    public boolean confirm(int blocks, long nowMillis) {
        if (level(blocks) == Level.NORMAL || level(blocks) == Level.UNKNOWN) {
            disarm();
            return true;
        }
        boolean confirmed = armedBlocks == blocks && nowMillis - armedAt <= CONFIRM_MILLIS;
        if (confirmed) {
            disarm();
            return true;
        }
        armedAt = nowMillis;
        armedBlocks = blocks;
        return false;
    }

    public boolean armed(long nowMillis) {
        return armedBlocks >= 0 && nowMillis - armedAt <= CONFIRM_MILLIS;
    }

    public void disarm() {
        armedAt = Long.MIN_VALUE;
        armedBlocks = -1;
    }

    /** 950 → «950», 12 345 → «12,3 k», 1 234 567 → «1,23 M» (coma decimal, como el resto del Studio). */
    public static String compact(int blocks) {
        if (blocks < 1_000) {
            return Integer.toString(Math.max(0, blocks));
        }
        if (blocks < 1_000_000) {
            return String.format(Locale.ROOT, blocks < 100_000 ? "%.1f k" : "%.0f k", blocks / 1_000.0)
                    .replace('.', ',');
        }
        return String.format(Locale.ROOT, "%.2f M", blocks / 1_000_000.0).replace('.', ',');
    }
}
