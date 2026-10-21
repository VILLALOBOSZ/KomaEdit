package com.komainu.moxia.client.hotbar;

import com.komainu.moxia.client.gui.studio.DrawContextCanvas;
import com.komainu.moxia.hotbar.ui.HotbarDeck;
import com.komainu.moxia.hotbar.ui.HotbarPainter;
import com.komainu.moxia.hotbar.ui.HotbarStripView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;

/** La tira de barras en el HUD mientras se mantiene la tecla ({@link HotbarStripView}). */
final class HotbarHud {
    private static DrawContextCanvas canvas;

    private HotbarHud() {
    }

    static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!HotbarControls.stripVisible() || client.player == null || client.options.hudHidden
                || client.currentScreen != null) {
            return;
        }
        if (canvas == null) {
            canvas = new DrawContextCanvas(client.textRenderer);
        }
        canvas.begin(context);
        HotbarDeck deck = ClientHotbars.deck(client);
        int selected = HotbarControls.selected();
        HotbarStripView.Layout layout = HotbarStripView.layout(context.getScaledWindowWidth(),
                context.getScaledWindowHeight(), deck.size(), selected);
        HotbarStripView.draw(canvas, layout, deck, selected, painter(client, context), I18n::translate);
    }

    /** Pinta la pila de verdad del catálogo: encantamientos, durabilidad y cantidad como en el inventario. */
    static HotbarPainter painter(MinecraftClient client, DrawContext context) {
        return (canvas, item, row, slot, x, y) -> {
            ItemStack stack = row < 0 ? ItemStack.EMPTY : ClientHotbars.stack(row, slot);
            if (stack.isEmpty()) {
                HotbarPainter.ICONS.slot(canvas, item, row, slot, x, y);
                return;
            }
            context.drawItem(stack, x, y);
            context.drawItemInSlot(client.textRenderer, stack, x, y);
        };
    }
}
