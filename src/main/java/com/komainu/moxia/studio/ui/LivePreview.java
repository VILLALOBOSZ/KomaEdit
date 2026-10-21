package com.komainu.moxia.studio.ui;

/**
 * La vista previa en vivo del Studio: tras Construir con vista previa (o un clic en el mundo), cada cambio de un
 * ajuste de ese mismo objeto la rehace sola con lo nuevo. Si hay fantasma con gizmo, el nuevo sale donde el jugador
 * dejó el de antes y con su giro.
 *
 * <p>Con la red de la vista previa en vivo ({@code LiveService} en el servidor), cada petición va por su paquete:</p>
 * <ul>
 *     <li>sale a los {@value #DELAY_MILLIS} ms de dejar de tocar (como mucho una cada {@value #FAST_INTERVAL_MILLIS}
 *     ms), también mientras el servidor calcula la anterior: allí espera solo la última y las de en medio se
 *     descartan;</li>
 *     <li>no cuenta para el antispam de Minecraft, y el servidor contesta cada una ({@link #answered}).</li>
 * </ul>
 *
 * <p>Con un servidor anterior va por el chat, y entonces:</p>
 * <ul>
 *     <li>espera {@value #CHAT_DELAY_MILLIS} ms sin cambios y deja al menos {@value #MIN_INTERVAL_MILLIS} ms entre dos
 *     envíos: cada orden suma un segundo al antispam de Minecraft, que echa a quien no es OP al pasar de diez, y
 *     descuenta uno por segundo;</li>
 *     <li>no manda mientras el servidor calcula o coloca algo.</li>
 * </ul>
 *
 * <p>En los dos casos:</p>
 * <ul>
 *     <li>no repite una petición ya mandada;</li>
 *     <li>no abre ninguna vista previa por su cuenta: solo rehace la que el jugador pidió, mientras se ve (o viene de
 *     camino). Si se coloca o se quita, se acaba hasta el próximo Construir;</li>
 *     <li>solo vale para obras hasta el tamaño de las preferencias: con más, el que llama no le pasa orden y avisa.</li>
 * </ul>
 */
public final class LivePreview {
    /** Con la red de la vista previa en vivo: lo que se espera sin cambios antes de mandar. */
    public static final long DELAY_MILLIS = 250L;
    /** Con esa red, lo mínimo entre dos envíos (el servidor se queda solo con el último). */
    public static final long FAST_INTERVAL_MILLIS = 200L;
    /** Por el chat: lo que se espera sin cambios antes de mandar. */
    public static final long CHAT_DELAY_MILLIS = 350L;
    /** Por el chat: lo mínimo entre dos órdenes, lo que descuenta el antispam de Minecraft. */
    public static final long MIN_INTERVAL_MILLIS = 1_000L;
    /** Lo que puede tardar en verse la vista previa pedida antes de darla por colocada o quitada. */
    public static final long GRACE_MILLIS = 4_000L;
    /** Lo que se espera la respuesta del servidor a una petición antes de darla por perdida. */
    public static final long ANSWER_MILLIS = 20_000L;

    private int session;
    private int seq;
    private String owner;
    private String pending;
    private long changedAt;
    private String sent;
    private long sentAt = Long.MIN_VALUE / 2;
    /** Una petición por la red de la vista previa en vivo espera respuesta. */
    private boolean awaiting;
    private long awaitingSince;
    private long answeredAt = Long.MIN_VALUE / 2;

    /**
     * Se acaba de construir con vista previa: desde ahora, los cambios de {@code owner} (una herramienta o la figura)
     * la rehacen.
     *
     * @param command la orden mandada (o lo que identifica a la figura), para no repetirla
     */
    public void arm(String owner, String command, long now) {
        arm(owner, command, now, false);
    }

    /**
     * Igual, sabiendo si el Construir fue por la red de la vista previa en vivo ({@code answered}): entonces su
     * respuesta está de camino y, hasta que llegue, la vista previa cuenta como a la vista.
     */
    public void arm(String owner, String command, long now, boolean answered) {
        session++;
        seq = 0;
        this.owner = owner;
        pending = command;
        changedAt = now;
        sent = command;
        sentAt = now;
        awaiting = answered;
        awaitingSince = now;
        answeredAt = Long.MIN_VALUE / 2;
    }

    /** Un paso por el chat (un servidor sin la red de la vista previa en vivo). */
    public String tick(String owner, String command, long now, boolean busy, boolean shown) {
        return tick(owner, command, now, busy, shown, false);
    }

    /**
     * Un paso.
     *
     * @param owner   de quién son los ajustes que se ven (la herramienta elegida o la figura)
     * @param command la orden de vista previa con los ajustes de ahora, o {@code null} si ahora no toca (vista
     *                previa apagada, herramienta sin vista previa, algo que falta, obra grande)
     * @param busy    si el servidor está calculando o colocando algo del jugador
     * @param shown   si la vista previa pedida se ve (el fantasma o la que espera a Confirmar)
     * @param fast    si va por la red de la vista previa en vivo
     * @return la orden que hay que mandar ahora, o {@code null}
     */
    public String tick(String owner, String command, long now, boolean busy, boolean shown, boolean fast) {
        if (this.owner == null) {
            return null;
        }
        if (awaiting && now - awaitingSince >= ANSWER_MILLIS) {
            awaiting = false;
        }
        if (!shown && !awaiting) {
            if (!busy && now - Math.max(sentAt, answeredAt) >= GRACE_MILLIS) {
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
        long delay = fast ? DELAY_MILLIS : CHAT_DELAY_MILLIS;
        long interval = fast ? FAST_INTERVAL_MILLIS : MIN_INTERVAL_MILLIS;
        if (command.equals(sent) || !fast && busy || now - changedAt < delay || now - sentAt < interval) {
            return null;
        }
        sent = command;
        sentAt = now;
        seq++;
        if (fast) {
            awaiting = true;
            awaitingSince = now;
        }
        return command;
    }

    /**
     * El servidor contestó a la petición {@code seq} de la sesión {@code session}. Solo cuenta la de la última que se
     * mandó: las de antes pudieron descartarse sin respuesta.
     */
    public void answered(int session, int seq, long now) {
        if (session == this.session && seq >= this.seq) {
            awaiting = false;
            answeredAt = now;
        }
    }

    /** La sesión de ahora (cambia con cada Construir). */
    public int session() {
        return session;
    }

    /** El número de la última petición mandada en la sesión (el Construir es la 0). */
    public int seq() {
        return seq;
    }

    /** Si una petición mandada por la red de la vista previa en vivo aún no tiene respuesta. */
    public boolean awaiting() {
        return awaiting;
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
        awaiting = false;
    }
}
