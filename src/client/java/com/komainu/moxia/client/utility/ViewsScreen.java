package com.komainu.moxia.client.utility;

import com.komainu.moxia.views.SavedView;
import com.komainu.moxia.views.ViewNames;
import com.komainu.moxia.views.ViewPayloads;
import com.komainu.moxia.views.ViewTeleport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * La lista de vistas ({@code /kegui vistas}): guardar la de ahora con un nombre y, de cada una, saltar, fijar,
 * compartir y borrar. Primero las propias (la fija con ★), luego las del equipo.
 */
public final class ViewsScreen extends Screen {
    private static final int ROW_WIDTH = 460;
    private final Screen parent;
    private TextFieldWidget name;
    private UtilityList list;
    private ButtonWidget back;
    private String message = "";

    public ViewsScreen(Screen parent) {
        super(Text.translatable("gui.komaedit.views.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientViews.requestCatalog();
        int width = Math.min(ROW_WIDTH, this.width - 20);
        int left = (this.width - width) / 2;
        String typed = name == null ? "" : name.getText();
        name = new TextFieldWidget(textRenderer, left, 30, width - 2 * (UtilityList.BUTTON_WIDTH + 2) - 40, 20,
                Text.translatable("gui.komaedit.views.name"));
        name.setMaxLength(ViewNames.MAX_LENGTH);
        name.setText(typed);
        name.setPlaceholder(Text.translatable("gui.komaedit.views.name"));
        addDrawableChild(name);
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.komaedit.views.save"), button -> save())
                .dimensions(left + width - 2 * (UtilityList.BUTTON_WIDTH + 2) - 36, 30, UtilityList.BUTTON_WIDTH + 36,
                        20)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("gui.komaedit.views.save.help")))
                .build());
        back = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.komaedit.views.back"), button -> {
                    ClientViews.back(client);
                    close();
                })
                .dimensions(left + width - UtilityList.BUTTON_WIDTH, 30, UtilityList.BUTTON_WIDTH, 20)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("gui.komaedit.views.back.help")))
                .build());
        list = addDrawableChild(new UtilityList(client, this.width, this.height - 90, 56, width));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
                .dimensions(this.width / 2 - 75, this.height - 28, 150, 20).build());
        refresh();
        ClientViews.onChange(() -> {
            if (client != null && client.currentScreen == this) {
                refresh();
            }
        });
    }

    private void save() {
        run(() -> ClientViews.save(client, name.getText()));
        name.setText("");
    }

    /** Hace algo y enseña su mensaje (o su error) bajo el título. */
    private void run(java.util.function.Supplier<String> action) {
        try {
            message = action.get();
        } catch (IllegalArgumentException exception) {
            message = "§c" + exception.getMessage();
        }
        refresh();
    }

    private void refresh() {
        if (list == null || client == null || client.player == null) {
            return;
        }
        back.active = ClientViews.canGoBack();
        ClientPlayerEntity player = client.player;
        ViewTeleport.Target here = ClientViews.here(player);
        ViewPayloads.ViewCatalogPayload catalog = ClientViews.catalog();
        List<UtilityList.Row> rows = new ArrayList<>();
        for (SavedView view : ClientViews.own(client)) {
            List<ButtonWidget> buttons = new ArrayList<>();
            ButtonWidget go = UtilityList.button(Text.translatable("gui.komaedit.views.go"), null, button -> {
                ClientViews.jump(client, ViewTeleport.Target.of(view));
                close();
            });
            go.active = catalog.canTeleport() && (view.dimension().equals(here.dimension())
                    || catalog.canChangeWorld());
            buttons.add(go);
            buttons.add(UtilityList.button(Text.translatable(view.pinned() ? "gui.komaedit.views.unpin"
                            : "gui.komaedit.views.pin"), Text.translatable("gui.komaedit.views.pin.help"),
                    button -> run(() -> ClientViews.pin(client, view.name(), !view.pinned()))));
            ButtonWidget share = UtilityList.button(Text.translatable("gui.komaedit.views.share"),
                    Text.translatable("gui.komaedit.views.share.help"), button -> {
                        ClientViews.share(client, view);
                        message = "";
                    });
            share.active = catalog.canShare();
            buttons.add(share);
            buttons.add(UtilityList.button(Text.translatable("gui.komaedit.views.delete"), null,
                    button -> run(() -> ClientViews.remove(client, view.name()))));
            rows.add(new UtilityList.Row(null, 0, Text.literal((view.pinned() ? "★ " : "") + view.name()),
                    Text.literal(where(view, here)), buttons));
        }
        for (SavedView view : catalog.shared()) {
            List<ButtonWidget> buttons = new ArrayList<>();
            ButtonWidget go = UtilityList.button(Text.translatable("gui.komaedit.views.go"), null, button -> {
                ClientViews.jump(client, ViewTeleport.Target.of(view));
                close();
            });
            go.active = catalog.canTeleport() && (view.dimension().equals(here.dimension())
                    || catalog.canChangeWorld());
            buttons.add(go);
            if (catalog.canManage() || player.getUuid().equals(view.authorId())) {
                buttons.add(UtilityList.button(Text.translatable("gui.komaedit.views.unshare"),
                        Text.translatable("gui.komaedit.views.unshare.help"), button -> {
                            player.networkHandler.sendChatCommand("komaedit vista borrar " + view.name());
                            message = "";
                        }));
            }
            rows.add(new UtilityList.Row(null, 0, Text.literal(view.name()), Text.literal(
                    ClientWords.INSTANCE.tr("gui.komaedit.views.team_by", view.author()) + " · " + where(view, here)),
                    buttons));
        }
        list.set(rows);
    }

    /** «a 42 bloques» o la dimensión, si es otra. */
    private static String where(SavedView view, ViewTeleport.Target here) {
        if (!view.dimension().equals(here.dimension())) {
            String dimension = view.dimension();
            return dimension.startsWith("minecraft:") ? dimension.substring("minecraft:".length()) : dimension;
        }
        long distance = Math.round(Math.sqrt(view.distanceSquared(here.x(), here.y(), here.z())));
        return ClientWords.INSTANCE.tr("gui.komaedit.views.distance", distance);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        String hint = message.isEmpty() ? ClientWords.INSTANCE.tr(ClientViews.own(client).isEmpty()
                && ClientViews.catalog().shared().isEmpty() ? "gui.komaedit.views.empty" : "gui.komaedit.views.hint")
                : message;
        context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, height - 42, 0xB0B0B0);
    }

    @Override
    public void removed() {
        ClientViews.onChange(null);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
