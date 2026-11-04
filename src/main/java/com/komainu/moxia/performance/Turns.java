package com.komainu.moxia.performance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Por turnos: lo que se recorre en cada tick con un plazo común (los servicios ligeros de {@link ServerWork}, los
 * jugadores de cada uno) empieza cada vez por uno distinto. Si el primero puede gastar todo el plazo, los de detrás
 * no se quedan sin nada tick tras tick: cada uno va el primero una de cada {@code n} veces. Java puro.
 */
public final class Turns {
    private Turns() {
    }

    /** Una copia de {@code items} que empieza en la posición {@code turn} (módulo su tamaño) y da la vuelta. */
    public static <T> List<T> from(Collection<T> items, int turn) {
        List<T> all = new ArrayList<>(items);
        int size = all.size();
        if (size < 2) {
            return all;
        }
        int start = Math.floorMod(turn, size);
        List<T> rotated = new ArrayList<>(size);
        rotated.addAll(all.subList(start, size));
        rotated.addAll(all.subList(0, start));
        return rotated;
    }
}
