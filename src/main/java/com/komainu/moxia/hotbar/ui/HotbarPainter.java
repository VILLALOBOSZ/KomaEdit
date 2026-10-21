package com.komainu.moxia.hotbar.ui;

import com.komainu.moxia.hotbar.HotbarItem;
import com.komainu.moxia.studio.ui.StudioCanvas;

/**
 * Pinta un hueco de 16×16. De serie, el icono del objeto y la cantidad como en el inventario; en el juego, el cliente
 * pinta la pila de verdad (encantamientos, durabilidad, cabezas con su textura).
 */
@FunctionalInterface
public interface HotbarPainter {
    /**
     * @param row  barra del mazo (-1 para la barra de verdad)
     * @param slot hueco de 0 a 8
     */
    void slot(StudioCanvas canvas, HotbarItem item, int row, int slot, int x, int y);

    HotbarPainter ICONS = (canvas, item, row, slot, x, y) -> {
        if (item.isEmpty()) {
            return;
        }
        canvas.item(item.id(), x, y);
        if (item.count() > 1) {
            String count = Integer.toString(item.count());
            int textX = x + 17 - canvas.width(count);
            canvas.text(count, textX + 1, y + 10, 0xFF3F3F3F);
            canvas.text(count, textX, y + 9, 0xFFFFFFFF);
        }
    };
}
