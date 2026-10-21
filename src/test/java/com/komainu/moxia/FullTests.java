package com.komainu.moxia;

import java.util.ArrayList;
import java.util.List;

/**
 * Las pruebas exhaustivas (todas las formas, recorridos, semillas o sellos) cuestan más de un minuto y casi nunca
 * cambia lo que miran. En cada build recorren una muestra fija, siempre la misma; con {@code KOMAEDIT_FULL_TESTS=1}
 * recorren todo. Pásalas enteras al tocar los generadores que comprueban.
 */
public final class FullTests {
    /** Si se pidieron las pruebas enteras. */
    public static final boolean ENABLED = isSet(System.getenv("KOMAEDIT_FULL_TESTS"));

    private FullTests() {
    }

    /** Todo con la variable; si no, uno de cada {@code every}, empezando por el primero. */
    public static <T> List<T> sample(List<T> all, int every) {
        return sample(all, every, 0);
    }

    /** Como {@link #sample(List, int)}, empezando por el de {@code offset} (otra muestra de la misma lista). */
    public static <T> List<T> sample(List<T> all, int every, int offset) {
        if (ENABLED || every <= 1) {
            return all;
        }
        List<T> picked = new ArrayList<>();
        for (int index = Math.floorMod(offset, every); index < all.size(); index += every) {
            picked.add(all.get(index));
        }
        return picked;
    }

    /** Todo con la variable; si no, solo {@code quick}. */
    public static <T> List<T> pick(List<T> all, List<T> quick) {
        return ENABLED ? all : quick;
    }

    static boolean isSet(String value) {
        return value != null && !value.isBlank() && !value.equals("0") && !value.equalsIgnoreCase("false");
    }
}
