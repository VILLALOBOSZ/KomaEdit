package com.komainu.moxia.client.reference;

import com.komainu.moxia.studio.ui.StudioTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Colocar las ventanas en pantalla: el juego se ve tal cual (sin velo ni desenfoque) y todas las ventanas llevan su
 * barra, también las que están «a través». Arriba, una línea con lo que se puede hacer. Esc vuelve a la lista.
 */
public final class ReferenceArrangeScreen extends Screen {
    private final Screen parent;

    public ReferenceArrangeScreen(Screen parent) {
        super(Text.translatable("gui.komaedit.reference.arrange"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Nada: las ventanas se colocan sobre el juego tal como se verá.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Text hint = Text.translatable("gui.komaedit.reference.arrange.hint");
        int width = textRenderer.getWidth(hint) + 12;
        int left = (this.width - width) / 2;
        context.fill(left, 4, left + width, 18, StudioTheme.fade(StudioTheme.PANEL, 0.85));
        context.drawCenteredTextWithShadow(textRenderer, hint, this.width / 2, 7, StudioTheme.TEXT);
        if (ReferenceClient.set().drawn(com.komainu.moxia.reference.ReferenceImage.Mode.SCREEN).isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.komaedit.reference.arrange.none"),
                    this.width / 2, this.height / 2, StudioTheme.TEXT_SECONDARY);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        ReferenceClient.save();
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
