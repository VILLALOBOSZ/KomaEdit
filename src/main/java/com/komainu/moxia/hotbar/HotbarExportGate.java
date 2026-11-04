package com.komainu.moxia.hotbar;

/**
 * Cuándo el cliente deja que el código de barras que manda el servidor ({@code HotbarTextPayload}) vaya al
 * portapapeles: solo justo después de pedir «Exportar», una vez por petición. Antes lo copiaba siempre, y un servidor
 * podía cambiar el portapapeles del jugador cuando quisiera (por ejemplo, por una orden de la consola que el jugador
 * pegaría luego). Java puro, con el reloj de fuera.
 */
public final class HotbarExportGate {
    /** Lo que se espera la respuesta tras pedir exportar. */
    public static final long WINDOW_MILLIS = 30_000L;

    private int pending;
    private long until = Long.MIN_VALUE;

    /** El jugador pidió exportar. */
    public synchronized void expect(long now) {
        pending = Math.min(pending + 1, 4);
        until = now + WINDOW_MILLIS;
    }

    /** ¿Se copia este código? Solo si se pidió hace poco; cada petición deja copiar uno. */
    public synchronized boolean accept(long now) {
        if (pending <= 0 || now > until) {
            pending = 0;
            return false;
        }
        pending--;
        return true;
    }

    public synchronized void clear() {
        pending = 0;
        until = Long.MIN_VALUE;
    }
}
