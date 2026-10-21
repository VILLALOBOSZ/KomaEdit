package com.komainu.moxia.client.world;

import com.komainu.moxia.client.gizmo.ClientGizmoSettings;
import com.komainu.moxia.client.gui.studio.ClientStudioHost;
import com.komainu.moxia.feedback.WorldFeedbackSettings;
import com.komainu.moxia.gizmo.GizmoSettings;
import com.komainu.moxia.studio.ui.StudioPalette;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.function.BiFunction;
import java.util.function.DoubleFunction;
import java.util.function.Function;

/**
 * Ajustes de lo que KomaEdit dibuja y suena en el mundo ({@code /kegui ajustes} o su tecla en Controles). Cada
 * cambio se guarda al momento en {@code config/komaedit-client.json}.
 */
public final class WorldSettingsScreen extends Screen {
    private static final int BUTTON = 150;
    private static final int ROW = 24;
    private final Screen parent;

    public WorldSettingsScreen(Screen parent) {
        super(Text.translatable("gui.komaedit.world.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        WorldFeedbackSettings settings = ClientWorldSettings.get();
        int left = width / 2 - BUTTON - 5;
        int right = width / 2 + 5;
        int top = Math.max(36, height / 2 - 7 * ROW);
        int row = 0;
        addDrawableChild(CyclingButtonWidget.<WorldFeedbackSettings.PreviewStyle>builder(style ->
                        Text.translatable("gui.komaedit.world.style." + style.id()))
                .values(WorldFeedbackSettings.PreviewStyle.values())
                .initially(settings.previewStyle())
                .build(left, top + row * ROW, BUTTON, 20, Text.translatable("gui.komaedit.world.style"),
                        (button, value) -> update(current -> current.withPreviewStyle(value))));
        addDrawableChild(slider(right, top + row * ROW, "gui.komaedit.world.opacity",
                (settings.previewOpacity() - WorldFeedbackSettings.MIN_OPACITY)
                        / (WorldFeedbackSettings.MAX_OPACITY - WorldFeedbackSettings.MIN_OPACITY),
                value -> WorldFeedbackSettings.MIN_OPACITY
                        + value * (WorldFeedbackSettings.MAX_OPACITY - WorldFeedbackSettings.MIN_OPACITY),
                (current, opacity) -> current.withPreviewOpacity(opacity)));
        row++;
        toggle(left, top + row * ROW, "fade", settings.fadeNear(), WorldFeedbackSettings::withFadeNear);
        toggle(right, top + row * ROW, "selection", settings.selection(), WorldFeedbackSettings::withSelection);
        row++;
        toggle(left, top + row * ROW, "dimensions", settings.dimensions(), WorldFeedbackSettings::withDimensions);
        toggle(right, top + row * ROW, "path_numbers", settings.pathNumbers(), WorldFeedbackSettings::withPathNumbers);
        row++;
        toggle(left, top + row * ROW, "brush", settings.brushReticle(), WorldFeedbackSettings::withBrushReticle);
        toggle(right, top + row * ROW, "symmetry", settings.symmetryGuides(),
                WorldFeedbackSettings::withSymmetryGuides);
        row++;
        toggle(left, top + row * ROW, "sounds", settings.sounds(), WorldFeedbackSettings::withSounds);
        addDrawableChild(slider(right, top + row * ROW, "gui.komaedit.world.volume", settings.soundVolume(),
                value -> value, (current, volume) -> current.withSoundVolume(volume)));
        row++;
        // El fantasma con gizmo: cuándo sale y si la rueda lo mueve (archivo aparte, komaedit-gizmo.json).
        GizmoSettings gizmo = ClientGizmoSettings.get();
        addDrawableChild(CyclingButtonWidget.<GizmoSettings.Mode>builder(mode ->
                        Text.translatable("gui.komaedit.world.gizmo." + mode.id()))
                .values(GizmoSettings.Mode.values())
                .initially(gizmo.mode())
                .tooltip(mode -> net.minecraft.client.gui.tooltip.Tooltip.of(
                        Text.translatable("gui.komaedit.world.gizmo.tooltip")))
                .build(left, top + row * ROW, BUTTON, 20, Text.translatable("gui.komaedit.world.gizmo"),
                        (button, value) -> ClientGizmoSettings.set(ClientGizmoSettings.get().withMode(value))));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(gizmo.wheel())
                .tooltip(ignored -> net.minecraft.client.gui.tooltip.Tooltip.of(
                        Text.translatable("gui.komaedit.world.gizmo_wheel.tooltip")))
                .build(right, top + row * ROW, BUTTON, 20, Text.translatable("gui.komaedit.world.gizmo_wheel"),
                        (button, enabled) -> ClientGizmoSettings.set(ClientGizmoSettings.get().withWheel(enabled))));
        row++;
        // Los colores del Studio (archivo aparte, komaedit/studio.json; también se cambian dentro, con ? o Ctrl+K).
        addDrawableChild(CyclingButtonWidget.<StudioPalette>builder(palette ->
                        Text.translatable(palette.translationKey()))
                .values(StudioPalette.values())
                .initially(StudioPalette.byId(ClientStudioHost.loadPreferences().theme()))
                .tooltip(palette -> net.minecraft.client.gui.tooltip.Tooltip.of(
                        Text.translatable(palette.translationKey() + ".help")))
                .build(left, top + row * ROW, BUTTON, 20, Text.translatable("gui.komaedit.world.theme"),
                        (button, value) -> ClientStudioHost.setTheme(value)));
        toggle(right, top + row * ROW, "alignment_guide", settings.alignmentGuide(),
                WorldFeedbackSettings::withAlignmentGuide);
        row += 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.komaedit.world.defaults"), button -> {
            ClientWorldSettings.set(ClientWorldSettings.defaults());
            ClientGizmoSettings.set(GizmoSettings.DEFAULT);
            ClientStudioHost.setTheme(StudioPalette.DEFAULT);
            clearAndInit();
        }).dimensions(left, top + row * ROW, BUTTON, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
                .dimensions(right, top + row * ROW, BUTTON, 20).build());
    }

    private void toggle(int x, int y, String id, boolean value,
                        BiFunction<WorldFeedbackSettings, Boolean, WorldFeedbackSettings> change) {
        addDrawableChild(CyclingButtonWidget.onOffBuilder(value)
                .tooltip(ignored -> net.minecraft.client.gui.tooltip.Tooltip.of(
                        Text.translatable("gui.komaedit.world." + id + ".tooltip")))
                .build(x, y, BUTTON, 20, Text.translatable("gui.komaedit.world." + id),
                        (button, enabled) -> update(current -> change.apply(current, enabled))));
    }

    private static SliderWidget slider(int x, int y, String key, double initial, DoubleFunction<Double> map,
                                       BiFunction<WorldFeedbackSettings, Double, WorldFeedbackSettings> change) {
        return new SliderWidget(x, y, BUTTON, 20, Text.empty(), Math.clamp(initial, 0.0, 1.0)) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Text.translatable(key, WorldFeedbackSettings.percent(map.apply(value))));
            }

            @Override
            protected void applyValue() {
                double mapped = map.apply(value);
                update(current -> change.apply(current, mapped));
            }
        };
    }

    private static void update(Function<WorldFeedbackSettings, WorldFeedbackSettings> change) {
        ClientWorldSettings.set(change.apply(ClientWorldSettings.get()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, Math.max(14, height / 2 - 7 * ROW - 22),
                0xFFFFFF);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
