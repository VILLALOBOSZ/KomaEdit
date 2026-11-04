package com.komainu.moxia.blueprint;

import com.komainu.moxia.studio.net.ButtonCommands;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Las descargas que el jugador ha pedido. El cliente solo guarda en {@code schematics} lo que llega después de que el
 * jugador exporte algo (con el botón del Studio o escribiendo {@code /komaedit blueprint export …}): cada exportación
 * deja pasar un archivo, durante {@link #TTL_MILLIS}. Lo que llega sin pedirlo se descarta sin escribir nada ni avisar,
 * así un servidor no puede llenar la carpeta ni dejar archivos que luego abre Litematica. Lógica pura, con el reloj
 * de fuera.
 */
public final class DownloadPermits {
    /** Lo que dura una petición sin que llegue su archivo. */
    public static final long TTL_MILLIS = 120_000L;
    /** Peticiones pendientes como mucho (las de más no cuentan). */
    public static final int MAX_WAITING = 4;
    /** La misma orden dos veces en este tiempo es una sola petición (el botón y el chat la ven pasar a la vez). */
    private static final long SAME_COMMAND_MILLIS = 1_000L;
    private static final Set<String> COMMANDS = Set.of("blueprint", "blueprints", "planos", "plano");
    private static final Set<String> EXPORT = Set.of("export", "exportar");
    private static final List<String> SERVER_FLAGS = List.of("-servidor", "-server", "-s");

    private final Deque<Long> waiting = new ArrayDeque<>();
    private final Set<Integer> admitted = new HashSet<>();
    private String lastCommand = "";
    private long lastAt = Long.MIN_VALUE;

    /**
     * ¿Pide esta orden un archivo al cliente? {@code komaedit blueprint export …} con cualquiera de sus alias, sin
     * {@code -servidor} (que lo deja en el disco del servidor). Con o sin la barra.
     */
    public static boolean requestsDownload(String command) {
        if (command == null) {
            return false;
        }
        String[] words = command.strip().replaceFirst("^/", "").toLowerCase(Locale.ROOT).split("\\s+");
        if (words.length < 3 || !ButtonCommands.ROOTS.contains(words[0]) || !COMMANDS.contains(words[1])
                || !EXPORT.contains(words[2])) {
            return false;
        }
        for (int index = 3; index < words.length; index++) {
            if (SERVER_FLAGS.contains(words[index])) {
                return false;
            }
        }
        return true;
    }

    /** El jugador mandó esta orden: si pide un archivo, deja pasar uno. */
    public synchronized void expect(String command, long now) {
        if (!requestsDownload(command)) {
            return;
        }
        String normalized = command.strip().replaceFirst("^/", "");
        if (normalized.equals(lastCommand) && now - lastAt < SAME_COMMAND_MILLIS) {
            return;
        }
        lastCommand = normalized;
        lastAt = now;
        expire(now);
        if (waiting.size() < MAX_WAITING) {
            waiting.addLast(now + TTL_MILLIS);
        }
    }

    /**
     * ¿Se acepta un trozo de este envío? Los de un envío ya admitido, sí; el primero de uno nuevo gasta una petición
     * vigente, y sin ninguna se rechaza.
     */
    public synchronized boolean admit(int transfer, long now) {
        if (admitted.contains(transfer)) {
            return true;
        }
        expire(now);
        if (waiting.isEmpty()) {
            return false;
        }
        waiting.pollFirst();
        admitted.add(transfer);
        return true;
    }

    /** El envío terminó (llegó entero o se tiró): sus trozos ya no entran. */
    public synchronized void finished(int transfer) {
        admitted.remove(transfer);
    }

    /** Peticiones que aún esperan archivo. */
    public synchronized int waiting(long now) {
        expire(now);
        return waiting.size();
    }

    public synchronized void clear() {
        waiting.clear();
        admitted.clear();
        lastCommand = "";
        lastAt = Long.MIN_VALUE;
    }

    private void expire(long now) {
        while (!waiting.isEmpty() && waiting.peekFirst() <= now) {
            waiting.pollFirst();
        }
    }
}
