package com.komainu.moxia.hotbar.ui;

import com.komainu.moxia.hotbar.HotbarItem;
import com.komainu.moxia.hotbar.HotbarRules;
import com.komainu.moxia.hotbar.SavedHotbar;

import java.util.List;

/**
 * Lo que dibujan la tira, el menú circular y la tarjeta del Studio: las barras en su orden, cuál está en uso y qué
 * pasará al cambiar (según el modo de juego).
 *
 * @param active índice de la barra en uso, o -1
 * @param mode   lo que hace cambiar de barra ahora mismo
 * @param reason por qué no se puede, si {@code mode} es {@link HotbarRules.Mode#DENIED}
 */
public record HotbarDeck(List<Row> rows, int active, HotbarRules.Mode mode, String reason) {
    public static final HotbarDeck EMPTY = new HotbarDeck(List.of(), -1, HotbarRules.Mode.SET, null);

    /** Una barra: nombre, objeto del icono y sus nueve huecos. */
    public record Row(String name, String icon, List<HotbarItem> slots) {
        public Row {
            slots = new SavedHotbar("x", "", slots).slots();
        }
    }

    public HotbarDeck {
        rows = List.copyOf(rows);
        active = active >= 0 && active < rows.size() ? active : -1;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int size() {
        return rows.size();
    }

    public Row row(int index) {
        return rows.get(index);
    }

    public int indexOf(String name) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).name().equals(name)) {
                return index;
            }
        }
        return -1;
    }
}
