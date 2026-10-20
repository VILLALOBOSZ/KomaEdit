package com.komainu.moxia.client.gui.studio;

import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.tools.ui.StampPreview;
import com.komainu.moxia.studio.tools.ui.ToolsUi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import static com.komainu.moxia.client.gui.studio.StudioTheme.*;

/**
 * Los controles del Studio ({@link StudioUi}) para la etapa Herramientas, que se dibuja con {@link ToolsUi} para
 * poder probarla y maquetarla fuera del juego. Solo traduce: cada control es el mismo de siempre.
 */
public final class StudioToolsUi implements ToolsUi {
    private static final int THUMBNAIL = 30;
    private static final Map<String, Identifier> STAMPS = new HashMap<>();

    private final StudioUi ui;

    public StudioToolsUi(StudioUi ui) {
        this.ui = ui;
    }

    public static int color(Ink ink) {
        return switch (ink) {
            case TEXT -> TEXT;
            case TEXT_SECONDARY -> TEXT_SECONDARY;
            case MUTED -> MUTED;
            case DISABLED -> DISABLED;
            case ACCENT -> ACCENT;
            case ACCENT_HOVER -> ACCENT_HOVER;
            case ACCENT_DIM -> ACCENT_DIM;
            case SECTION -> SECTION;
            case SUCCESS -> SUCCESS;
            case WARNING -> WARNING;
            case DANGER -> DANGER;
            case DANGER_DIM -> DANGER_DIM;
            case FAVORITE -> FAVORITE;
            case CARD -> CARD;
            case CARD_HOVER -> CARD_HOVER;
            case CARD_ACTIVE -> CARD_ACTIVE;
            case FIELD -> FIELD;
            case DIVIDER -> DIVIDER;
            case TRACK -> TRACK;
            case SURFACE -> SURFACE;
            case WHITE -> 0xFFFFFFFF;
        };
    }

    private static StudioUi.Tone tone(Tone tone) {
        return StudioUi.Tone.valueOf(tone.name());
    }

    @Override
    public int width(String text) {
        return ui.width(text);
    }

    @Override
    public String fit(String text, int maximumWidth) {
        return ui.fit(text, maximumWidth);
    }

    @Override
    public List<String> wrap(String text, int maximumWidth) {
        return ui.textRenderer().getTextHandler().wrapLines(text, Math.max(20, maximumWidth), Style.EMPTY).stream()
                .map(StringVisitable::getString)
                .toList();
    }

    @Override
    public boolean hovered(Rect rect) {
        return ui.hovered(rect);
    }

    @Override
    public boolean visible(Rect rect) {
        return ui.visible(rect);
    }

    @Override
    public void fill(Rect rect, Ink ink) {
        ui.fill(rect, color(ink));
    }

    @Override
    public void fill(Rect rect, int argb) {
        ui.fill(rect, argb);
    }

    @Override
    public void border(Rect rect, Ink ink) {
        ui.border(rect, color(ink));
    }

    @Override
    public void label(String text, int x, int y, int maximumWidth, Ink ink) {
        ui.label(text, x, y, maximumWidth, color(ink));
    }

    @Override
    public void bold(String text, int x, int y, int maximumWidth, Ink ink) {
        ui.bold(text, x, y, maximumWidth, color(ink));
    }

    @Override
    public void centered(String text, Rect rect, Ink ink) {
        ui.centered(text, rect, color(ink));
    }

    @Override
    public void item(String blockId, int x, int y) {
        Identifier identifier = Identifier.tryParse(blockId);
        ItemStack stack = identifier == null ? ItemStack.EMPTY
                : Registries.ITEM.get(identifier).getDefaultStack();
        if (stack.isEmpty() && identifier != null) {
            stack = Registries.BLOCK.get(identifier).asItem().getDefaultStack();
        }
        ui.item(stack.isEmpty() ? Items.BARRIER.getDefaultStack() : stack, x, y);
    }

    @Override
    public void stamp(String name, Rect rect) {
        Identifier texture = STAMPS.computeIfAbsent(name, StudioToolsUi::upload);
        if (texture == null) {
            ui.fill(rect, CARD);
            return;
        }
        ui.context().drawTexture(texture, rect.x(), rect.y(), 0.0f, 0.0f, rect.width(), rect.height(),
                rect.width(), rect.height());
    }

    /** Sube la miniatura a una textura del cliente (una vez por sello). */
    private static Identifier upload(String name) {
        try {
            int[] pixels = StampPreview.argb(name, THUMBNAIL);
            NativeImage image = new NativeImage(THUMBNAIL, THUMBNAIL, false);
            for (int row = 0; row < THUMBNAIL; row++) {
                for (int column = 0; column < THUMBNAIL; column++) {
                    int argb = pixels[row * THUMBNAIL + column];
                    // NativeImage guarda ABGR.
                    int abgr = argb & 0xFF00FF00 | (argb >> 16) & 0xFF | (argb & 0xFF) << 16;
                    image.setColor(column, row, abgr);
                }
            }
            Identifier id = Identifier.of("komaedit", "stamp_preview/" + name);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
            return id;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public void lock(int x, int y, Ink ink) {
        int color = color(ink);
        ui.fill(new Rect(x + 2, y, 4, 1), color);
        ui.fill(new Rect(x + 1, y + 1, 1, 3), color);
        ui.fill(new Rect(x + 6, y + 1, 1, 3), color);
        ui.fill(new Rect(x, y + 4, 8, 5), color);
        ui.fill(new Rect(x + 3, y + 5, 2, 2), CARD);
    }

    @Override
    public Zone register(Rect rect) {
        return new WidgetZone(ui.register(rect));
    }

    @Override
    public void section(Column column, String title) {
        ui.section(column, title);
    }

    @Override
    public void paragraph(Column column, String text, Ink ink, int maximumLines) {
        ui.paragraph(column, text, color(ink), maximumLines);
    }

    @Override
    public Zone button(Rect rect, String label, Tone tone) {
        return new WidgetZone(ui.button(rect, label, tone(tone)));
    }

    @Override
    public Zone chip(Rect rect, String label, boolean selected, Ink ink) {
        return new WidgetZone(ui.chip(rect, label, selected, color(ink)));
    }

    @Override
    public int chipWidth(String label) {
        return ui.chipWidth(label);
    }

    @Override
    public Zone field(Rect rect, String value, String placeholder, boolean editing, boolean error) {
        return new WidgetZone(ui.field(rect, value, placeholder, editing, error));
    }

    @Override
    public Zone listItem(Rect rect, String title, String subtitle, boolean selected, int rightReserve) {
        return new WidgetZone(ui.listItem(rect, title, subtitle, selected, rightReserve));
    }

    @Override
    public Zone stepper(Rect row, Stepper model) {
        return new WidgetZone(ui.stepper(row, new StudioUi.Stepper(model.label(), model.value(), model.fraction(),
                model.editing(), model.dragging(), model.input(), model.step(), model.press(), model.reset(),
                model.help(), model.group(), model.key())));
    }

    @Override
    public Zone cycle(Rect row, String label, String value, IntConsumer step) {
        return new WidgetZone(ui.cycle(row, label, value, step));
    }

    @Override
    public Zone toggle(Rect row, String label, boolean on) {
        return new WidgetZone(ui.toggle(row, label, on));
    }

    /** La zona de {@link StudioUi}: cada llamada pasa tal cual. */
    private record WidgetZone(StudioUi.Widget widget) implements Zone {
        @Override
        public Zone onClick(Runnable action) {
            widget.onClick(action);
            return this;
        }

        @Override
        public Zone onClick(Click action) {
            widget.onClick(action::run);
            return this;
        }

        @Override
        public Zone onScroll(IntConsumer action) {
            widget.onScroll(action);
            return this;
        }

        @Override
        public Zone onReset(Runnable action) {
            widget.onReset(action);
            return this;
        }

        @Override
        public Zone tooltip(String text) {
            widget.tooltip(text);
            return this;
        }

        @Override
        public Zone group(String group) {
            widget.group(group);
            return this;
        }

        @Override
        public Zone key(Object key) {
            widget.key(key);
            return this;
        }

        @Override
        public Zone valueZone(Rect zone) {
            widget.valueZone(zone);
            return this;
        }
    }
}
