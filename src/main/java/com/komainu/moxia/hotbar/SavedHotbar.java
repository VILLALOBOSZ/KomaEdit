package com.komainu.moxia.hotbar;

import com.komainu.moxia.persistence.NamedDefinitions;

import java.util.ArrayList;
import java.util.List;

/**
 * Una barra guardada: nombre, icono y los nueve huecos. El nombre sigue las reglas de las paletas guardadas (de 1 a 32
 * letras, números, {@code _} o {@code -}, en minúsculas).
 *
 * @param icon objeto que la representa en la tira, el menú y el Studio; vacío para usar el primer hueco con algo
 */
public record SavedHotbar(String name, String icon, List<HotbarItem> slots) {
    public static final int SLOTS = 9;
    /** Lo que se dibuja cuando la barra está vacía y no tiene icono. */
    public static final String FALLBACK_ICON = "minecraft:barrier";

    public SavedHotbar {
        name = NamedDefinitions.normalizeName(name);
        icon = icon == null || icon.isBlank() ? "" : HotbarItem.normalize(icon);
        if (slots == null) {
            slots = List.of();
        }
        if (slots.size() > SLOTS) {
            throw new IllegalArgumentException("Una barra tiene " + SLOTS + " huecos, no " + slots.size());
        }
        List<HotbarItem> padded = new ArrayList<>(SLOTS);
        for (HotbarItem item : slots) {
            padded.add(item == null ? HotbarItem.EMPTY : item);
        }
        while (padded.size() < SLOTS) {
            padded.add(HotbarItem.EMPTY);
        }
        slots = List.copyOf(padded);
    }

    public HotbarItem slot(int index) {
        return slots.get(index);
    }

    /** El objeto del icono: el elegido o el del primer hueco con algo. */
    public String iconItem() {
        if (!icon.isEmpty()) {
            return icon;
        }
        for (HotbarItem item : slots) {
            if (!item.isEmpty()) {
                return item.id();
            }
        }
        return FALLBACK_ICON;
    }

    public boolean isEmpty() {
        return filled() == 0;
    }

    /** Huecos con algo. */
    public int filled() {
        int count = 0;
        for (HotbarItem item : slots) {
            if (!item.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public SavedHotbar withName(String value) {
        return new SavedHotbar(value, icon, slots);
    }

    public SavedHotbar withIcon(String value) {
        return new SavedHotbar(name, value, slots);
    }

    /** Los huecos nuevos; el icono se queda si sigue en la barra o si se eligió a mano un objeto que no está. */
    public SavedHotbar withSlots(List<HotbarItem> value) {
        return new SavedHotbar(name, icon, value);
    }

    /**
     * Icono a partir de lo que escribió el jugador: un hueco del 1 al 9 (el objeto que hay ahí) o un objeto.
     *
     * @return el id del objeto
     */
    public static String icon(String input, List<HotbarItem> slots) {
        String text = input == null ? "" : input.strip();
        if (text.matches("[1-9]")) {
            HotbarItem item = slots.get(Integer.parseInt(text) - 1);
            if (item.isEmpty()) {
                throw new IllegalArgumentException("El hueco " + text + " está vacío: elige otro o di un objeto");
            }
            return item.id();
        }
        return HotbarItem.normalize(text);
    }
}
