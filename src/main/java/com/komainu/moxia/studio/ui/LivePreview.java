package com.komainu.moxia.studio.ui;

/**
 * La vista previa en vivo del Studio: tras Construir con vista previa (o un clic en el mundo), cada cambio de un
 * ajuste de ese mismo objeto la rehace sola con lo nuevo. Si hay fantasma con gizmo, el nuevo sale donde el jugador
 * dejó el de antes. Para no inundar el servidor:
 *
 * <ul>
 *     <li>espera {@value #DELAY_MILLIS} ms sin cambios antes de mandar nada;</li>
 *     <li>entre dos envíos pasan al menos {@value #MIN_INTERVAL_MILLIS} ms: cada orden suma un segundo al antispam de
 *     Minecraft, que echa a quien no es OP al pasar de diez, y descuenta uno por segundo;</li>
 *     <li>no manda mientras el servidor calcula o coloca algo (solo con la vista previa a la vista, que la nueva
 *     sustituye);</li>
 *     <li>no repite una orden ya mandada;</li>
 *     <li>no abre ninguna vista previa por su cuenta: solo rehace la que el jugador pidió. Si se coloca o se quita,
 *     se acaba hasta el próximo Construir;</li>
 *     <li>solo vale para obras pequeñas: el que llama no le pasa orden si la selección o la figura son grandes.</li>
 * </ul>
 */
public final class LivePreview {
    public static final long DELAY_MILLIS = 350L;
    public static final long MIN_INTERVAL_MILLIS = 1_000L;
    /** Lo que puede tardar en verse la vista previa pedida antes de darla por colocada o quitada. */
    public static final long GRACE_MILLIS = 4_000L;
    /** Selecciones más grandes que esto no tienen vista previa en vivo. */
    public static final long MAX_VOLUME = 64_000L;

    private String owner;
    private String pending;
    private long changedAt;
    private String sent;
    private long sentAt = Long.MIN_VALUE / 2;

    /**
     * Se acaba de construir con vista previa: desde ahora, los cambios de {@code owner} (una herramienta o la figura)
     * la rehacen.
     *
     * @param command la orden mandada (o lo que identifica a la figura), para no repetirla
     */
    public void arm(String owner, String command, long now) {
        this.owner = owner;
        pending = command;
        changedAt = now;
        sent = command;
        sentAt = now;
    }

    /**
     * Un paso.
     *
     * @param owner   de quién son los ajustes que se ven (la herramienta elegida o la figura)
     * @param command la orden de vista previa con los ajustes de ahora, o {@code null} si ahora no toca (vista
     *                previa apagada, herramienta sin vista previa, algo que falta, obra grande)
     * @param busy    si el servidor está calculando o colocando algo del jugador
     * @param shown   si la vista previa pedida se ve (el fantasma o la que espera a Confirmar)
     * @return la orden que hay que mandar ahora, o {@code null}
     */
    public String tick(String owner, String command, long now, boolean busy, boolean shown) {
        if (this.owner == null) {
            return null;
        }
        if (!shown && !busy) {
            if (now - sentAt >= GRACE_MILLIS) {
                // Se colocó o se quitó: hasta el próximo Construir no se rehace nada.
                reset();
            }
            return null;
        }
        if (command == null || !this.owner.equals(owner)) {
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

    /** De qué es la vista previa que se rehace, o {@code null} si no hay ninguna. */
    public String owner() {
        return owner;
    }

    /** La última orden mandada, o {@code null}. */
    public String sent() {
        return sent;
    }

    /** Olvida la vista previa (al apagar la vista previa en vivo). */
    public void reset() {
        owner = null;
        pending = null;
        sent = null;
    }
}
