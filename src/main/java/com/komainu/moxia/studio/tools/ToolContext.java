package com.komainu.moxia.studio.tools;

import java.util.Set;

/**
 * Lo que la pantalla sabe del jugador para decidir qué se puede: los permisos que el servidor le ha confirmado, su
 * selección de WorldEdit y los puntos de la trayectoria. Los permisos solo sirven para enseñar la tarjeta apagada
 * con su motivo; al construir vuelve a decidir el servidor.
 *
 * @param known        el servidor ya ha contestado (sin respuesta no se apaga nada: puede ser un servidor viejo)
 * @param granted      nodos con permiso de entre los que piden las tarjetas
 * @param hasSelection hay selección de WorldEdit completa
 * @param pathPoints   puntos de la trayectoria marcados
 */
public record ToolContext(boolean known, Set<String> granted, boolean hasSelection, long selectionVolume,
                          int pathPoints, boolean pathClosed) {
    public static final ToolContext UNKNOWN = new ToolContext(false, Set.of(), false, 0L, 0, false);

    public ToolContext {
        granted = Set.copyOf(granted);
    }

    /** Sin respuesta del servidor todo cuenta como permitido: el servidor lo comprobará al construir. */
    public boolean allows(String node) {
        return !known || granted.contains(node);
    }
}
