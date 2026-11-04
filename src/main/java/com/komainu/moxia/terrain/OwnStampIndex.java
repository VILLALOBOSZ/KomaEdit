package com.komainu.moxia.terrain;

import com.komainu.moxia.terrain.stamp.StampInfo;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lo que se sabe de los sellos propios del servidor (altura natural y paleta), al alcance de quien solo tiene el
 * nombre: el pincel de relieve en el servidor y la tarjeta del Studio en el cliente. El servidor lo rellena al listar
 * su carpeta ({@link OwnStamps#list}); el cliente, con el catálogo que le llega. En un mundo de un jugador los dos
 * lados ven la misma carpeta, así que da igual quién lo rellene el último.
 */
public final class OwnStampIndex {
    private static volatile Map<String, StampInfo> infos = Map.of();

    private OwnStampIndex() {
    }

    public static void replace(Collection<StampInfo> values) {
        Map<String, StampInfo> next = new ConcurrentHashMap<>();
        for (StampInfo info : values) {
            next.put(info.name(), info);
        }
        infos = next;
    }

    /** El sello propio con ese nombre, o {@code null}. */
    public static StampInfo get(String name) {
        return name == null ? null : infos.get(name);
    }

    public static void clear() {
        infos = Map.of();
    }
}
