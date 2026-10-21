package com.komainu.moxia;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La pasada entera de las pruebas, con {@code KOMAEDIT_FULL_TESTS=1}. Pásala al tocar los generadores que comprueban
 * o su rendimiento:
 *
 * <ul>
 *     <li>Las exhaustivas (todas las formas, recorridos, semillas o sellos) cuestan más de un minuto y casi nunca
 *     cambia lo que miran: en cada build recorren una muestra fija, siempre la misma; en la pasada entera, todo.</li>
 *     <li>Los límites de tiempo de reloj («en menos de 8 s») solo se comprueban en la pasada entera: en una máquina
 *     cargada (el CI, varias JVM a la vez) fallan sin que nada esté mal. Lo que hace la prueba se comprueba
 *     siempre. Lo que reparte trabajo entre ticks se prueba con un reloj falso, nunca con el de verdad.</li>
 * </ul>
 */
public final class FullTests {
    /** Si se pidió la pasada entera. */
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

    /** Un límite de tiempo de reloj ({@code elapsed} por debajo de {@code limit}): solo en la pasada entera. */
    public static void assertFasterThan(double elapsed, double limit, String message) {
        if (ENABLED) {
            assertTrue(elapsed < limit, message);
        }
    }

    static boolean isSet(String value) {
        return value != null && !value.isBlank() && !value.equals("0") && !value.equalsIgnoreCase("false");
    }
}
