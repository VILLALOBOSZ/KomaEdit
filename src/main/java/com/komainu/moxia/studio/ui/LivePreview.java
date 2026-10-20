package com.komainu.moxia.studio.ui;

/**
 * La vista previa en vivo de las herramientas: cuando se deja de tocar un ajuste, se pide la vista previa de siempre
 * ({@code -v}, la del motor común) con lo nuevo. Para no inundar el servidor:
 *
 * <ul>
 *     <li>espera {@value #DELAY_MILLIS} ms sin cambios antes de mandar nada;</li>
 *     <li>entre dos envíos pasan al menos {@value #MIN_INTERVAL_MILLIS} ms;</li>
 *     <li>no manda mientras el servidor calcula o coloca algo (solo cuando no hay nada o hay una vista previa
 *     esperando, que la nueva sustituye);</li>
 *     <li>no repite una orden ya mandada (tras Confirmar no vuelve a enseñar lo mismo);</li>
 *     <li>solo vale para obras pequeñas: el que llama no le pasa orden si la selección es grande.</li>
 * </ul>
 */
public final class LivePreview {
    public static final long DELAY_MILLIS = 700L;
    public static final long MIN_INTERVAL_MILLIS = 1_500L;
    /** Selecciones más grandes que esto no tienen vista previa en vivo. */
    public static final long MAX_VOLUME = 64_000L;

    private String pending;
    private long changedAt;
    private String sent;
    private long sentAt = Long.MIN_VALUE / 2;

    /**
     * Un paso: {@code command} es la orden de vista previa con los ajustes de ahora, o {@code null} si ahora no
     * toca (vista previa apagada, herramienta sin vista previa, algo que falta, obra grande).
     *
     * @param busy si el servidor está calculando o colocando algo del jugador
     * @return la orden que hay que mandar ahora, o {@code null}
     */
    public String tick(String command, long now, boolean busy) {
        if (command == null) {
            pending = null;
            return null;
        }
        if (!command.equals(pending)) {
            pending = command;
            changedAt = now;
        }
        if (command.equals(sent) || busy || now - changedAt < DELAY_MILLIS || now - sentAt < MIN_INTERVAL_MILLIS) {
            return null;
        }
        sent = command;
        sentAt = now;
        return command;
    }

    /** La última orden mandada, o {@code null}. */
    public String sent() {
        return sent;
    }

    /** Olvida lo mandado (al apagar la vista previa en vivo o cambiar de herramienta). */
    public void reset() {
        pending = null;
        sent = null;
    }
}
