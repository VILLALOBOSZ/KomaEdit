package com.komainu.moxia.hotbar;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cambiar de barra en supervivencia: solo se mueve lo que el jugador lleva, nunca se crea nada. */
class HotbarReorderTest {
    private static List<HotbarItem> inventory(HotbarItem... first) {
        List<HotbarItem> items = new ArrayList<>();
        for (HotbarItem item : first) {
            items.add(item);
        }
        while (items.size() < HotbarReorder.INVENTORY) {
            items.add(HotbarItem.EMPTY);
        }
        return items;
    }

    private static List<HotbarItem> hotbar(HotbarItem... slots) {
        return new SavedHotbar("x", "", List.of(slots)).slots();
    }

    @Test
    void bringsTheWantedItemsFromTheInventory() {
        List<HotbarItem> items = inventory(HotbarItem.of("dirt", 10));
        items.set(20, HotbarItem.of("stone", 64));
        items.set(30, HotbarItem.of("oak_planks", 12));
        HotbarReorder.Result result = HotbarReorder.plan(items,
                hotbar(HotbarItem.of("stone", 64), HotbarItem.of("oak_planks", 1)));
        assertEquals(HotbarItem.of("stone", 64), result.after().get(0));
        assertEquals(HotbarItem.of("oak_planks", 12), result.after().get(1), "se mueve la pila que lleva, no otra");
        assertEquals(HotbarItem.of("dirt", 10), result.after().get(20), "lo que había va adonde estaba la piedra");
        assertTrue(result.missing().isEmpty());
        assertSamePile(items, result.after());
    }

    @Test
    void whatThePlayerDoesNotHaveStaysAsItWasAndIsReported() {
        List<HotbarItem> items = inventory(HotbarItem.of("torch", 5), HotbarItem.of("bread", 3));
        HotbarReorder.Result result = HotbarReorder.plan(items,
                hotbar(HotbarItem.of("diamond_block", 64), HotbarItem.EMPTY, HotbarItem.of("bread", 1)));
        assertEquals(List.of(0), result.missing());
        assertEquals(HotbarItem.of("torch", 5), result.after().get(0), "no aparece un bloque de diamante");
        assertEquals(HotbarItem.of("bread", 3), result.after().get(2));
        assertTrue(result.after().get(1).isEmpty(), "el pan se fue al hueco 3 y deja el 2 vacío");
        assertSamePile(items, result.after());
    }

    @Test
    void sameComponentsFirstAndThenTheSameItem() {
        HotbarItem wanted = HotbarStoreTest.pickaxe();
        HotbarItem worn = HotbarItem.of("diamond_pickaxe", 1, "{\"minecraft:damage\":1500}");
        List<HotbarItem> items = inventory();
        items.set(10, worn);
        items.set(11, wanted);
        HotbarReorder.Result exact = HotbarReorder.plan(items, hotbar(wanted));
        assertEquals(wanted, exact.after().getFirst(), "el mismo pico encantado antes que otro pico");

        items.set(11, HotbarItem.EMPTY);
        HotbarReorder.Result loose = HotbarReorder.plan(items, hotbar(wanted));
        assertEquals(worn, loose.after().getFirst(), "sin él, un pico cualquiera vale");
        assertTrue(loose.missing().isEmpty());
    }

    @Test
    void doesNotStealWhatIsAlreadyInPlace() {
        List<HotbarItem> items = inventory(HotbarItem.EMPTY, HotbarItem.of("stone", 64));
        HotbarReorder.Result result = HotbarReorder.plan(items,
                hotbar(HotbarItem.of("stone", 1), HotbarItem.of("stone", 1)));
        assertEquals(HotbarItem.of("stone", 64), result.after().get(1), "la piedra del hueco 2 se queda");
        assertEquals(List.of(0), result.missing(), "solo hay una pila de piedra");
        assertFalse(result.changed());
    }

    @Test
    void prefersTheBiggestStackFromTheInventory() {
        List<HotbarItem> items = inventory();
        items.set(9, HotbarItem.of("stone", 3));
        items.set(27, HotbarItem.of("stone", 64));
        items.set(5, HotbarItem.of("stone", 64));
        HotbarReorder.Result result = HotbarReorder.plan(items, hotbar(HotbarItem.of("stone", 64)));
        assertEquals(List.of(new HotbarReorder.Swap(0, 27)), result.swaps(),
                "del inventario antes que de la barra, y la pila más grande");
    }

    @Test
    void aSecondPassChangesNothing() {
        List<HotbarItem> items = inventory();
        items.set(14, HotbarItem.of("glass", 64));
        items.set(15, HotbarItem.of("oak_log", 64));
        List<HotbarItem> wanted = hotbar(HotbarItem.of("oak_log", 1), HotbarItem.of("glass", 1));
        HotbarReorder.Result first = HotbarReorder.plan(items, wanted);
        HotbarReorder.Result second = HotbarReorder.plan(first.after(), wanted);
        assertFalse(second.changed());
        assertEquals(first.after(), second.after());
    }

    /** Miles de inventarios al azar (semilla fija): siempre las mismas pilas y cada objeto que se lleva, en su hueco. */
    @Test
    void neverCreatesOrDestroysItems() {
        String[] ids = {"stone", "dirt", "oak_log", "glass", "torch", "diamond_pickaxe", "bread", "sand"};
        String[] components = {"", "{\"minecraft:damage\":3}", "{\"minecraft:custom_name\":\"\\\"A\\\"\"}"};
        SplittableRandom random = new SplittableRandom(20261021L);
        for (int round = 0; round < 3_000; round++) {
            List<HotbarItem> items = new ArrayList<>();
            for (int slot = 0; slot < HotbarReorder.INVENTORY; slot++) {
                items.add(random.nextInt(3) == 0 ? HotbarItem.EMPTY : HotbarItem.of(ids[random.nextInt(ids.length)],
                        1 + random.nextInt(64), components[random.nextInt(components.length)]));
            }
            List<HotbarItem> wanted = new ArrayList<>();
            for (int slot = 0; slot < 9; slot++) {
                wanted.add(random.nextInt(4) == 0 ? HotbarItem.EMPTY : HotbarItem.of(ids[random.nextInt(ids.length)],
                        1, components[random.nextInt(components.length)]));
            }
            HotbarReorder.Result result = HotbarReorder.plan(items, wanted);
            assertSamePile(items, result.after());
            List<HotbarItem> replayed = new ArrayList<>(items);
            for (HotbarReorder.Swap swap : result.swaps()) {
                HotbarItem moved = replayed.get(swap.from());
                replayed.set(swap.from(), replayed.get(swap.slot()));
                replayed.set(swap.slot(), moved);
            }
            assertEquals(result.after(), replayed, "los intercambios dan el resultado anunciado");
            for (int slot = 0; slot < 9; slot++) {
                HotbarItem want = wanted.get(slot);
                int index = slot;
                boolean source = result.swaps().stream().anyMatch(swap -> swap.from() == index);
                if (want.isEmpty() || result.missing().contains(slot)) {
                    if (!source) {
                        assertEquals(items.get(slot), result.after().get(slot),
                                "un hueco que no pide nada (o no encuentra lo suyo) solo cambia si se le toma algo");
                    }
                } else {
                    assertTrue(result.after().get(slot).sameItem(want), "ronda " + round + ", hueco " + slot);
                }
            }
        }
    }

    private static void assertSamePile(List<HotbarItem> before, List<HotbarItem> after) {
        assertEquals(count(before), count(after), "el inventario tiene exactamente lo mismo");
    }

    private static Map<HotbarItem, Integer> count(List<HotbarItem> items) {
        Map<HotbarItem, Integer> counts = new HashMap<>();
        for (HotbarItem item : items) {
            if (!item.isEmpty()) {
                counts.merge(item, 1, Integer::sum);
            }
        }
        return counts;
    }
}
