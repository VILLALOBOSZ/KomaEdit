package com.komainu.moxia.client.utility;

import com.komainu.moxia.feedback.WorldSketch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * La lista de las pantallas de vistas y notas: cada fila lleva un icono opcional, un título, una línea gris y sus
 * botones a la derecha.
 */
final class UtilityList extends ElementListWidget<UtilityList.Row> {
    static final int ROW_HEIGHT = 26;
    static final int BUTTON_WIDTH = 56;
    private static final int TEXT = 0xFFFFFF;
    private static final int MUTED = 0xA0A0A0;
    private final int rowWidth;

    UtilityList(MinecraftClient client, int width, int height, int top, int rowWidth) {
        super(client, width, height, top, ROW_HEIGHT);
        this.rowWidth = rowWidth;
    }

    void set(List<Row> rows) {
        replaceEntries(rows);
    }

    @Override
    public int getRowWidth() {
        return rowWidth;
    }

    /**
     * Una fila.
     *
     * @param icon    icono de píxeles o {@code null}
     * @param iconRgb su color
     */
    static final class Row extends ElementListWidget.Entry<Row> {
        private final WorldSketch.PixelIcon icon;
        private final int iconRgb;
        private final Text title;
        private final Text detail;
        private final List<ButtonWidget> buttons;

        Row(WorldSketch.PixelIcon icon, int iconRgb, Text title, Text detail, List<ButtonWidget> buttons) {
            this.icon = icon;
            this.iconRgb = iconRgb;
            this.title = title;
            this.detail = detail;
            this.buttons = List.copyOf(buttons);
        }

        @Override
        public List<? extends Element> children() {
            return buttons;
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return buttons;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX,
                           int mouseY, boolean hovered, float tickDelta) {
            var font = MinecraftClient.getInstance().textRenderer;
            int left = x + 2;
            if (icon != null) {
                int top = y + (entryHeight - icon.height()) / 2 - 1;
                for (int py = 0; py < icon.height(); py++) {
                    for (int px = 0; px < icon.width(); px++) {
                        if (icon.filled(px, py)) {
                            context.fill(left + px, top + py, left + px + 1, top + py + 1, 0xFF000000 | iconRgb);
                        }
                    }
                }
                left += icon.width() + 5;
            }
            int buttonsWidth = buttons.size() * (BUTTON_WIDTH + 2);
            int textWidth = Math.max(20, x + entryWidth - buttonsWidth - left - 4);
            context.drawTextWithShadow(font, font.trimToWidth(title.getString(), textWidth), left, y + 3, TEXT);
            context.drawText(font, font.trimToWidth(detail.getString(), textWidth), left, y + 14, MUTED, false);
            int buttonX = x + entryWidth - buttonsWidth;
            for (ButtonWidget button : buttons) {
                button.setPosition(buttonX, y + 2);
                button.render(context, mouseX, mouseY, tickDelta);
                buttonX += BUTTON_WIDTH + 2;
            }
        }
    }

    static ButtonWidget button(Text label, Text tooltip, ButtonWidget.PressAction action) {
        ButtonWidget button = ButtonWidget.builder(label, action).dimensions(0, 0, BUTTON_WIDTH, 20).build();
        if (tooltip != null) {
            button.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(tooltip));
        }
        return button;
    }
}
