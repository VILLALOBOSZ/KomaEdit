package com.komainu.moxia.client.hotbar;

import com.komainu.moxia.hotbar.HotbarItem;
import com.komainu.moxia.hotbar.HotbarPayloads.HotbarSyncPayload;
import com.komainu.moxia.hotbar.HotbarRules;
import com.komainu.moxia.hotbar.ui.HotbarDeck;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Las barras guardadas tal como las mandó el servidor ({@link HotbarSyncPayload}): las pilas de verdad para pintarlas
 * y para poner la barra al instante en creativo, la que está en uso y los permisos. Sin catálogo (servidor sin
 * KomaEdit o aún sin llegar) la tecla no hace nada.
 */
public final class ClientHotbars {
    /** Una barra: nombre, objeto del icono y sus nueve pilas. */
    public record Entry(String name, String icon, List<ItemStack> slots) {
    }

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static List<String> order = List.of();
    private static String active = "";
    private static boolean autosave = true;
    private static boolean permitted;
    private static boolean give;
    private static boolean known;
    /** Cambia con cada catálogo: quien pinta sabe si tiene que rehacer lo suyo. */
    private static int version;

    private ClientHotbars() {
    }

    public static void update(HotbarSyncPayload payload) {
        if (payload.full()) {
            ENTRIES.clear();
        }
        for (HotbarSyncPayload.Entry entry : payload.entries()) {
            ENTRIES.put(entry.name(), new Entry(entry.name(), entry.icon(), entry.slots()));
        }
        ENTRIES.keySet().retainAll(payload.order());
        order = payload.order();
        active = payload.active();
        autosave = payload.autosave();
        permitted = payload.permitted();
        give = payload.give();
        known = true;
        version++;
    }

    public static void clear() {
        ENTRIES.clear();
        order = List.of();
        active = "";
        known = false;
        version++;
    }

    public static boolean known() {
        return known;
    }

    public static int version() {
        return version;
    }

    public static boolean autosave() {
        return autosave;
    }

    public static String active() {
        return active;
    }

    /** Las barras en su orden (las que aún no llegaron enteras se saltan). */
    public static List<Entry> entries() {
        List<Entry> result = new ArrayList<>(order.size());
        for (String name : order) {
            Entry entry = ENTRIES.get(name);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    public static Entry entry(String name) {
        return ENTRIES.get(name);
    }

    public static int activeIndex() {
        List<Entry> entries = entries();
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).name().equals(active)) {
                return index;
            }
        }
        return -1;
    }

    /** Lo que hará el servidor al cambiar, con el modo de juego de ahora. */
    public static HotbarRules.Decision decision(MinecraftClient client) {
        GameMode mode = client.interactionManager == null ? GameMode.SURVIVAL
                : client.interactionManager.getCurrentGameMode();
        return HotbarRules.decide(permitted, mode == GameMode.CREATIVE, mode == GameMode.SPECTATOR, give);
    }

    /** Lo que pintan la tira, el menú y el Studio. */
    public static HotbarDeck deck(MinecraftClient client) {
        List<Entry> entries = entries();
        List<HotbarDeck.Row> rows = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            List<HotbarItem> slots = new ArrayList<>(entry.slots().size());
            for (ItemStack stack : entry.slots()) {
                slots.add(stack.isEmpty() ? HotbarItem.EMPTY
                        : HotbarItem.of(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount()));
            }
            rows.add(new HotbarDeck.Row(entry.name(), entry.icon(), slots));
        }
        HotbarRules.Decision decision = decision(client);
        return new HotbarDeck(rows, activeIndex(), decision.mode(), decision.reason());
    }

    /** La pila de verdad de un hueco del mazo, o vacía. */
    public static ItemStack stack(int row, int slot) {
        List<Entry> entries = entries();
        if (row < 0 || row >= entries.size()) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> slots = entries.get(row).slots();
        return slot >= 0 && slot < slots.size() ? slots.get(slot) : ItemStack.EMPTY;
    }
}
