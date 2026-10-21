package com.komainu.moxia.client.hotbar;

import com.komainu.moxia.client.gui.studio.DrawContextCanvas;
import com.komainu.moxia.hotbar.ui.HotbarDeck;
import com.komainu.moxia.hotbar.ui.HotbarWheelView;
import com.komainu.moxia.persistence.NamedDefinitions;
import com.komainu.moxia.studio.layout.Rect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Menú circular de las barras guardadas ({@link HotbarWheelView}): clic para usar una, clic derecho para copiar su
 * código, rueda para pasar de página. Abajo, guardar la barra actual con un nombre (si ya existe, el segundo clic la
 * sustituye), pegar un código {@code kh1:} y copiar todas.
 */
public final class HotbarMenuScreen extends Screen {
    private DrawContextCanvas canvas;
    private TextFieldWidget nameField;
    private HotbarWheelView.Layout layout;
    private int page;
    private int hovered = -1;
    private HotbarWheelView.Button hoveredButton = HotbarWheelView.Button.NONE;
    private String status;
    /** Nombre ya existente que el siguiente Guardar sustituye. */
    private String confirmOverwrite;
    private int seenVersion = -1;

    public HotbarMenuScreen() {
        super(Text.translatable("gui.komaedit.hotbar.menu.title"));
    }

    @Override
    protected void init() {
        canvas = new DrawContextCanvas(textRenderer);
        String typed = nameField == null ? "" : nameField.getText();
        relayout();
        Rect field = layout.nameField();
        nameField = new TextFieldWidget(textRenderer, field.x(), field.y(), field.width(), field.height(),
                Text.translatable("gui.komaedit.hotbar.menu.name"));
        nameField.setMaxLength(32);
        nameField.setPlaceholder(Text.translatable("gui.komaedit.hotbar.menu.name"));
        nameField.setText(typed);
        nameField.setChangedListener(text -> confirmOverwrite = null);
        addDrawableChild(nameField);
    }

    private void relayout() {
        layout = HotbarWheelView.layout(width, height, ClientHotbars.entries().size(), page);
        page = layout.page();
        seenVersion = ClientHotbars.version();
    }

    @Override
    public void tick() {
        if (client == null || client.player == null || !HotbarControls.supported()) {
            close();
            return;
        }
        if (seenVersion != ClientHotbars.version()) {
            relayout();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // El fondo lo pinta la vista: sin el desenfoque de las pantallas de Minecraft.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        HotbarDeck deck = ClientHotbars.deck(client);
        hovered = HotbarWheelView.tileAt(layout, mouseX, mouseY);
        hoveredButton = HotbarWheelView.buttonAt(layout, mouseX, mouseY);
        canvas.begin(context);
        HotbarWheelView.draw(canvas, layout, deck, hovered, hoveredButton, nameField.getText(), status,
                HotbarHud.painter(client, context), I18n::translate);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int tile = HotbarWheelView.tileAt(layout, mouseX, mouseY);
        if (tile >= 0) {
            String name = ClientHotbars.entries().get(tile).name();
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                HotbarControls.export(name, notice -> status = notice);
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                HotbarControls.apply(client, name);
                close();
                return true;
            }
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        switch (HotbarWheelView.buttonAt(layout, mouseX, mouseY)) {
            case SAVE -> save();
            case PASTE -> status = HotbarControls.importCode(client.keyboard.getClipboard());
            case COPY_ALL -> {
                if (ClientHotbars.entries().isEmpty()) {
                    status = I18n.translate("gui.komaedit.hotbar.menu.empty");
                } else {
                    HotbarControls.export("", notice -> status = notice);
                }
            }
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    private void save() {
        String name;
        try {
            name = NamedDefinitions.normalizeName(nameField.getText());
        } catch (IllegalArgumentException exception) {
            status = I18n.translate("gui.komaedit.hotbar.status.bad_name");
            nameField.setFocused(true);
            return;
        }
        boolean exists = ClientHotbars.entry(name) != null;
        if (exists && !name.equals(confirmOverwrite)) {
            confirmOverwrite = name;
            status = I18n.translate("gui.komaedit.hotbar.status.confirm_overwrite", name);
            return;
        }
        HotbarControls.save(name, exists);
        confirmOverwrite = null;
        nameField.setText("");
        status = I18n.translate("gui.komaedit.hotbar.status.saved", name);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (layout.pages() > 1 && verticalAmount != 0) {
            page += verticalAmount < 0 ? 1 : -1;
            relayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
