package com.komainu.moxia.studio.tools.ui;

/**
 * El pincel que salió del Studio sigue a su tarjeta: al cambiar cualquier ajuste de esa herramienta (tras
 * {@value #DELAY_MILLIS} ms sin tocarla) se le manda {@code /komaedit pincel actualizar …} con lo nuevo, mientras el
 * jugador tenga ese mismo pincel en la mano. Así quitar «vista previa en cada clic» en el Studio la quita también del
 * pincel, sin volver a pulsar nada.
 *
 * <p>Nunca toca otro objeto: el pincel se reconoce por el id de la herramienta que el servidor deja en el objeto de
 * la mano tras crearlo, y la orden de actualizar solo cambia un pincel que ya existe y es del mismo tipo (lo comprueba
 * el servidor). Lógica pura, con pruebas.</p>
 *
 * <p>Entre dos órdenes pasa al menos {@value #MIN_INTERVAL_MILLIS} ms: cada orden suma un segundo al antispam de
 * Minecraft, que echa a quien no es OP al pasar de diez, así que más de una por segundo acabaría echando al jugador
 * que va pulsando «+» con calma.</p>
 */
public final class BrushSync {
    public static final long DELAY_MILLIS = 300L;
    /** Lo mínimo entre dos órdenes de actualizar: una por segundo, lo que el antispam de Minecraft descuenta. */
    public static final long MIN_INTERVAL_MILLIS = 1_000L;
    /** Lo que se espera a ver el pincel en la mano tras crearlo (el servidor marca el objeto). */
    public static final long ADOPT_MILLIS = 5_000L;
    static final String CREATE = "komaedit pincel ";
    static final String UPDATE = "komaedit pincel actualizar ";

    private String card;
    private String action;
    private String tool;
    private String sent;
    private String pending;
    private long changedAt;
    private long linkedAt;
    private long sentAt = Long.MIN_VALUE / 2;

    /**
     * El Studio acaba de crear el pincel con {@code command} desde la tarjeta {@code card} (con la acción
     * {@code action}, o vacía si fue Construir de una herramienta de mano).
     */
    public void linked(String card, String action, String command, long now) {
        this.card = card;
        this.action = action;
        tool = null;
        sent = command;
        pending = command;
        changedAt = now;
        linkedAt = now;
    }

    /** La tarjeta del pincel que se sigue, o {@code null}. */
    public String card() {
        return card;
    }

    /** La acción de la tarjeta que crea el pincel («brush»), o vacía si es Construir. */
    public String action() {
        return action;
    }

    /** El id del pincel en la mano que se sigue, o {@code null} si aún no se ha visto. */
    public String tool() {
        return tool;
    }

    /**
     * Un paso.
     *
     * @param command la orden que crearía el pincel con los ajustes de ahora de su tarjeta, o {@code null} si ahora
     *                no se puede (algo mal escrito)
     * @param held    el id de la herramienta de la mano, o {@code null}
     * @return la orden de actualizar que hay que mandar ahora, o {@code null}
     */
    public String tick(String command, String held, long now) {
        if (card == null) {
            return null;
        }
        if (tool == null) {
            if (held != null && held.startsWith("pincel:") && now - linkedAt <= ADOPT_MILLIS) {
                // El primero que se ve tras crearlo: el servidor marca la mano o conserva el id del pincel que había.
                tool = held;
            } else {
                if (now - linkedAt > ADOPT_MILLIS) {
                    forget();
                }
                return null;
            }
        }
        if (command == null || !command.startsWith(CREATE)) {
            pending = null;
            return null;
        }
        if (!command.equals(pending)) {
            pending = command;
            changedAt = now;
        }
        if (!tool.equals(held) || command.equals(sent) || now - changedAt < DELAY_MILLIS
                || now - sentAt < MIN_INTERVAL_MILLIS) {
            return null;
        }
        sent = command;
        sentAt = now;
        return update(command);
    }

    /** La orden que crea un pincel ({@code komaedit pincel tree …}) como la que solo lo actualiza. */
    public static String update(String command) {
        return command.startsWith(CREATE) ? UPDATE + command.substring(CREATE.length()) : command;
    }

    public void forget() {
        card = null;
        action = null;
        tool = null;
        sent = null;
        pending = null;
    }
}
