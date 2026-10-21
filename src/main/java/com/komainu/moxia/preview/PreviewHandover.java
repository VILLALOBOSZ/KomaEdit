package com.komainu.moxia.preview;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El relevo de la vista previa que dibuja el cliente cuando la vista previa en vivo la rehace. Al descartarse la de
 * antes, el cliente la quitaba en el acto y no quedaba nada a la vista hasta que llegaba la nueva (unas décimas de
 * segundo de parpadeo en cada ajuste tocado). Ahora, si es la vista previa en vivo la que la sustituye, la de antes
 * se queda a la vista: la nueva la reemplaza al llegar y, si no llega (falló o no había nada que cambiar), se quita
 * entonces.
 *
 * <p>Lógica pura: dice qué vista previa del cliente hay que quitar y cuándo.</p>
 */
public final class PreviewHandover {
    private final Map<UUID, Integer> replaced = new ConcurrentHashMap<>();

    /**
     * Se descarta la vista previa {@code plan} del jugador.
     *
     * @param refreshing si la descarta la vista previa en vivo, que trae otra
     * @return la vista previa del cliente que hay que quitar ya, o 0
     */
    public int hidden(UUID player, int plan, boolean refreshing) {
        if (plan == 0) {
            return 0;
        }
        if (!refreshing) {
            return plan;
        }
        Integer older = replaced.put(player, plan);
        // Si ya esperaba otra (dos seguidas sin llegar ninguna), esa sí se quita: solo se mantiene la última vista.
        return older == null ? 0 : older;
    }

    /** Llegó la nueva: el cliente reemplaza la de antes él solo. */
    public void shown(UUID player) {
        replaced.remove(player);
    }

    /**
     * La vista previa en vivo acabó sin traer otra.
     *
     * @return la vista previa del cliente que se quedó a la vista y hay que quitar, o 0
     */
    public int ended(UUID player) {
        Integer plan = replaced.remove(player);
        return plan == null ? 0 : plan;
    }

    public void forget(UUID player) {
        replaced.remove(player);
    }

    public void clear() {
        replaced.clear();
    }
}
