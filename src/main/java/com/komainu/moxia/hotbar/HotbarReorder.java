package com.komainu.moxia.hotbar;

import java.util.ArrayList;
import java.util.List;

/**
 * El cambio de barra en supervivencia: lleva a cada hueco de la barra rápida el objeto que pide la barra guardada,
 * buscándolo en el inventario del jugador, y no crea nada. Solo intercambia huecos, así que el inventario tiene las
 * mismas pilas antes y después (las pruebas lo comprueban con inventarios al azar).
 *
 * <p>Para cada hueco con algo en la barra guardada:</p>
 * <ol>
 *     <li>si ya tiene ese objeto, se queda;</li>
 *     <li>si no, se busca primero el mismo objeto con los mismos componentes y después el mismo objeto con otros (un
 *     pico gastado vale por uno nuevo); entre los que valen, primero los del inventario antes que los de la barra, la
 *     pila más grande y el hueco más bajo;</li>
 *     <li>lo que había en el hueco va a donde estaba el objeto encontrado (se intercambian).</li>
 * </ol>
 * <p>Los huecos vacíos de la barra guardada no piden nada: solo cambian si otro hueco se lleva lo que tenían (y les
 * deja lo suyo). Los que piden algo que el jugador no lleva se quedan igual, y se avisa.</p>
 */
public final class HotbarReorder {
    /** Huecos del inventario principal: 0–8 la barra rápida, 9–35 el resto. */
    public static final int INVENTORY = 36;

    /** Intercambio del hueco {@code slot} de la barra con el hueco {@code from} del inventario. */
    public record Swap(int slot, int from) {
    }

    /**
     * @param swaps   intercambios en orden
     * @param missing huecos de la barra cuyo objeto no lleva el jugador
     * @param after   el inventario tras los intercambios
     */
    public record Result(List<Swap> swaps, List<Integer> missing, List<HotbarItem> after) {
        public boolean changed() {
            return !swaps.isEmpty();
        }
    }

    private enum Match {
        EXACT,
        ITEM
    }

    private HotbarReorder() {
    }

    /**
     * @param inventory los 36 huecos del inventario principal
     * @param wanted    los 9 huecos de la barra guardada
     */
    public static Result plan(List<HotbarItem> inventory, List<HotbarItem> wanted) {
        if (inventory.size() != INVENTORY) {
            throw new IllegalArgumentException("El inventario tiene " + INVENTORY + " huecos, no " + inventory.size());
        }
        if (wanted.size() != SavedHotbar.SLOTS) {
            throw new IllegalArgumentException("Una barra tiene " + SavedHotbar.SLOTS + " huecos");
        }
        List<HotbarItem> items = new ArrayList<>(inventory);
        boolean[] locked = new boolean[SavedHotbar.SLOTS];
        List<Swap> swaps = new ArrayList<>();
        for (Match match : Match.values()) {
            // Primero se fijan los que ya están bien, para no robárselos a otro hueco.
            for (int slot = 0; slot < SavedHotbar.SLOTS; slot++) {
                if (!locked[slot] && matches(items.get(slot), wanted.get(slot), match)) {
                    locked[slot] = true;
                }
            }
            for (int slot = 0; slot < SavedHotbar.SLOTS; slot++) {
                if (locked[slot] || wanted.get(slot).isEmpty()) {
                    continue;
                }
                int from = source(items, locked, slot, wanted.get(slot), match);
                if (from < 0) {
                    continue;
                }
                HotbarItem moved = items.get(from);
                items.set(from, items.get(slot));
                items.set(slot, moved);
                locked[slot] = true;
                swaps.add(new Swap(slot, from));
            }
        }
        List<Integer> missing = new ArrayList<>();
        for (int slot = 0; slot < SavedHotbar.SLOTS; slot++) {
            if (!locked[slot] && !wanted.get(slot).isEmpty()) {
                missing.add(slot);
            }
        }
        return new Result(List.copyOf(swaps), List.copyOf(missing), List.copyOf(items));
    }

    private static int source(List<HotbarItem> items, boolean[] locked, int slot, HotbarItem wanted, Match match) {
        int best = -1;
        for (int index = 0; index < INVENTORY; index++) {
            if (index == slot || index < SavedHotbar.SLOTS && locked[index]
                    || !matches(items.get(index), wanted, match)) {
                continue;
            }
            if (best < 0 || better(items, index, best)) {
                best = index;
            }
        }
        return best;
    }

    /** ¿Es {@code candidate} mejor origen que {@code best}? Inventario antes que barra, más cantidad, hueco más bajo. */
    private static boolean better(List<HotbarItem> items, int candidate, int best) {
        boolean candidateMain = candidate >= SavedHotbar.SLOTS;
        boolean bestMain = best >= SavedHotbar.SLOTS;
        if (candidateMain != bestMain) {
            return candidateMain;
        }
        int countDifference = items.get(candidate).count() - items.get(best).count();
        if (countDifference != 0) {
            return countDifference > 0;
        }
        return candidate < best;
    }

    private static boolean matches(HotbarItem have, HotbarItem wanted, Match match) {
        return match == Match.EXACT ? have.sameStack(wanted) : have.sameItem(wanted);
    }
}
