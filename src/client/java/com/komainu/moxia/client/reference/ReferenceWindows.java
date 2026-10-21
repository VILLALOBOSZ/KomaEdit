package com.komainu.moxia.client.reference;

import com.komainu.moxia.reference.ReferenceGrid;
import com.komainu.moxia.reference.ReferenceImage;
import com.komainu.moxia.reference.ScreenFrame;
import com.komainu.moxia.studio.ui.StudioTheme;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Las referencias en pantalla: ventanas flotantes encima del juego. Sin pantalla abierta se ven en el HUD, quietas.
 * Con una pantalla abierta (el chat, el Studio, el inventario o {@link ReferenceArrangeScreen}) llevan su barra: se
 * arrastran por ella o por la imagen, se agrandan por la esquina o con la rueda (con Mayús, la rueda cambia la
 * opacidad) y se ocultan o se dejan «a través». Una ventana «a través» no lleva barra y el ratón la ignora, salvo en
 * la pantalla de colocar, que sirve justo para recuperarla.
 */
final class ReferenceWindows {
    private static final double ZOOM = 1.1;
    private static final double OPACITY_STEP = 0.05;
    /** Menos unidades de pantalla por celda y la rejilla no se distingue: no se dibuja. */
    private static final double MIN_CELL = 3.0;
    private static final double BOLD_CELL = 4.0;

    private static String dragging;
    private static ScreenFrame.Part part = ScreenFrame.Part.NONE;
    private static double startX;
    private static double startY;
    private static ScreenFrame.Rect startRect;
    private static int dragButton;

    private ReferenceWindows() {
    }

    static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (MinecraftClient.getInstance().currentScreen == null) {
                draw(context, -1, -1, false);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof ReferenceScreen) {
                return;
            }
            ScreenEvents.afterRender(screen).register((current, context, mouseX, mouseY, delta) -> {
                follow(mouseX, mouseY, context.getScaledWindowWidth(), context.getScaledWindowHeight());
                draw(context, mouseX, mouseY, true);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((current, mouseX, mouseY, button) ->
                    !press(mouseX, mouseY, button, current.width, current.height));
            ScreenMouseEvents.allowMouseRelease(screen).register((current, mouseX, mouseY, button) ->
                    !release(button));
            ScreenMouseEvents.allowMouseScroll(screen).register((current, mouseX, mouseY, horizontal, vertical) ->
                    !scroll(mouseX, mouseY, vertical, current.width, current.height));
        });
    }

    static void reset() {
        dragging = null;
        part = ScreenFrame.Part.NONE;
    }

    /** Si la ventana lleva barra y atiende al ratón en la pantalla de ahora. */
    private static boolean chrome(ReferenceImage reference) {
        return !reference.screen().clickThrough()
                || MinecraftClient.getInstance().currentScreen instanceof ReferenceArrangeScreen;
    }

    // ---------------------------------------------------------------- dibujo

    private static void draw(DrawContext context, int mouseX, int mouseY, boolean onScreen) {
        List<ReferenceImage> drawn = ReferenceClient.set().drawn(ReferenceImage.Mode.SCREEN);
        if (drawn.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer text = client.textRenderer;
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        ReferenceImage top = onScreen ? topAt(drawn, mouseX, mouseY, width, height).orElse(null) : null;
        List<Text> tooltip = new ArrayList<>();
        context.getMatrices().push();
        // Por encima de lo que dibuje la pantalla (botones, objetos del inventario).
        context.getMatrices().translate(0.0F, 0.0F, 400.0F);
        for (ReferenceImage reference : drawn) {
            ScreenFrame.Window window = reference.screen().layout(width, height, reference.pixelWidth(),
                    reference.pixelHeight());
            image(context, text, reference, window);
            grid(context, reference, window);
            if (onScreen && chrome(reference)) {
                boolean hovered = reference == top;
                chromeOf(context, text, reference, window, hovered, mouseX, mouseY);
                if (hovered) {
                    tooltip = tooltip(reference, window, mouseX, mouseY);
                }
            }
        }
        context.getMatrices().pop();
        if (!tooltip.isEmpty()) {
            context.drawTooltip(text, tooltip, mouseX, mouseY);
        }
    }

    private static void image(DrawContext context, TextRenderer text, ReferenceImage reference,
                              ScreenFrame.Window window) {
        ScreenFrame.Rect rect = window.image();
        Optional<ReferenceTextures.Texture> texture = ReferenceTextures.full(reference.file());
        if (texture.isEmpty()) {
            context.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), StudioTheme.fade(StudioTheme.PANEL, 0.6));
            String error = ReferenceTextures.error(reference.file());
            Text message = error == null ? Text.translatable("gui.komaedit.reference.loading")
                    : Text.translatable("gui.komaedit.reference.missing");
            context.drawCenteredTextWithShadow(text, message, rect.x() + rect.width() / 2,
                    rect.y() + rect.height() / 2 - 4, error == null ? StudioTheme.TEXT : StudioTheme.DANGER);
            return;
        }
        texture.get().filter(reference.smooth());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.setShaderColor(1.0F, 1.0F, 1.0F, (float) reference.opacity());
        context.drawTexture(texture.get().id(), rect.x(), rect.y(), rect.width(), rect.height(), 0.0F, 0.0F, 1, 1,
                1, 1);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void grid(DrawContext context, ReferenceImage reference, ScreenFrame.Window window) {
        ReferenceGrid grid = reference.grid();
        double scale = window.scale(reference.pixelWidth());
        if (!grid.enabled() || !grid.visibleAt(scale, MIN_CELL) && grid.major() == 0) {
            return;
        }
        boolean minor = grid.visibleAt(scale, MIN_CELL);
        // Con celdas de 4 puntos o más, la línea marcada va el doble de gruesa para distinguirla.
        int bold = grid.visibleAt(scale, BOLD_CELL) ? 2 : 1;
        ScreenFrame.Rect rect = window.image();
        int color = grid.color().rgb();
        for (ReferenceGrid.Line line : grid.lines(reference.pixelWidth(), reference.pixelHeight())) {
            if (!line.major() && !minor) {
                continue;
            }
            int argb = (line.major() ? 0xE8 : 0x70) << 24 | color;
            int thickness = line.major() ? bold : 1;
            if (line.vertical()) {
                int x = rect.x() + (int) Math.round(line.at() * rect.width() / reference.pixelWidth());
                context.fill(x, rect.y(), x + thickness, rect.bottom(), argb);
            } else {
                int y = rect.y() + (int) Math.round(line.at() * rect.height() / reference.pixelHeight());
                context.fill(rect.x(), y, rect.right(), y + thickness, argb);
            }
        }
        context.drawBorder(rect.x(), rect.y(), rect.width(), rect.height(), 0x90 << 24 | color);
    }

    private static void chromeOf(DrawContext context, TextRenderer text, ReferenceImage reference,
                                 ScreenFrame.Window window, boolean hovered, int mouseX, int mouseY) {
        ScreenFrame.Rect bar = window.bar();
        ScreenFrame.Rect image = window.image();
        boolean active = hovered || reference.id().equals(dragging);
        context.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), StudioTheme.fade(StudioTheme.PANEL, 0.92));
        int titleRight = window.through().x() - 2;
        String name = text.trimToWidth(reference.displayName(), Math.max(0, titleRight - bar.x() - 4));
        context.drawText(text, name, bar.x() + 3, bar.y() + 2, active ? StudioTheme.TEXT_STRONG : StudioTheme.TEXT,
                false);
        button(context, text, window.through(), "»", reference.screen().clickThrough(),
                window.through().contains(mouseX, mouseY));
        button(context, text, window.hide(), "×", false, window.hide().contains(mouseX, mouseY));
        int outline = active ? StudioTheme.ACCENT : StudioTheme.OUTLINE;
        context.drawBorder(image.x() - 1, bar.y() - 1, image.width() + 2, image.height() + bar.height() + 2,
                outline);
        ScreenFrame.Rect grip = window.grip();
        int gripColor = grip.contains(mouseX, mouseY) ? StudioTheme.ACCENT_TEXT : StudioTheme.TEXT_SECONDARY;
        context.fill(grip.x(), grip.y(), grip.right(), grip.bottom(), StudioTheme.fade(StudioTheme.PANEL, 0.75));
        for (int step = 0; step < 3; step++) {
            int offset = 2 + step * 2;
            context.fill(grip.right() - offset, grip.bottom() - 1, grip.right() - offset + 1, grip.bottom(),
                    gripColor);
            context.fill(grip.right() - 1, grip.bottom() - offset, grip.right(), grip.bottom() - offset + 1,
                    gripColor);
        }
    }

    private static void button(DrawContext context, TextRenderer text, ScreenFrame.Rect rect, String label,
                               boolean on, boolean hovered) {
        int fill = on ? StudioTheme.ACCENT : hovered ? StudioTheme.CARD_HOVER : StudioTheme.CARD;
        context.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1, fill);
        context.drawCenteredTextWithShadow(text, label, rect.x() + rect.width() / 2, rect.y() + 2,
                on ? StudioTheme.ON_ACCENT : StudioTheme.TEXT_STRONG);
    }

    /** Lo que dice el ratón sobre la ventana: el botón, o la celda de la rejilla y la opacidad. */
    private static List<Text> tooltip(ReferenceImage reference, ScreenFrame.Window window, int mouseX, int mouseY) {
        ScreenFrame.Part hit = window.hit(mouseX, mouseY, true);
        return switch (hit) {
            case THROUGH -> List.of(Text.translatable("gui.komaedit.reference.window.through"));
            case HIDE -> List.of(Text.translatable("gui.komaedit.reference.window.hide"));
            case GRIP -> List.of(Text.translatable("gui.komaedit.reference.window.grip"));
            case IMAGE -> {
                if (!reference.grid().enabled() || dragging != null) {
                    yield List.of();
                }
                double[] pixel = window.pixel(mouseX, mouseY, reference.pixelWidth(), reference.pixelHeight());
                yield List.of(Text.translatable("gui.komaedit.reference.window.cell",
                        reference.grid().column(pixel[0], reference.pixelWidth()),
                        reference.grid().row(pixel[1], reference.pixelHeight())));
            }
            default -> List.of();
        };
    }

    // ---------------------------------------------------------------- ratón

    /** La ventana de encima bajo el ratón, entre las que lo atienden. */
    private static Optional<ReferenceImage> topAt(List<ReferenceImage> drawn, double mouseX, double mouseY,
                                                  int width, int height) {
        for (int index = drawn.size() - 1; index >= 0; index--) {
            ReferenceImage reference = drawn.get(index);
            if (!chrome(reference)) {
                continue;
            }
            ScreenFrame.Window window = reference.screen().layout(width, height, reference.pixelWidth(),
                    reference.pixelHeight());
            if (window.hit(mouseX, mouseY, true) != ScreenFrame.Part.NONE) {
                return Optional.of(reference);
            }
        }
        return Optional.empty();
    }

    /** Un clic: si cae en una ventana, es suyo ({@code true}) y la pantalla no lo ve. */
    private static boolean press(double mouseX, double mouseY, int button, int width, int height) {
        List<ReferenceImage> drawn = ReferenceClient.set().drawn(ReferenceImage.Mode.SCREEN);
        Optional<ReferenceImage> found = topAt(drawn, mouseX, mouseY, width, height);
        if (found.isEmpty()) {
            return false;
        }
        ReferenceImage reference = found.get();
        ScreenFrame.Window window = reference.screen().layout(width, height, reference.pixelWidth(),
                reference.pixelHeight());
        ScreenFrame.Part hit = window.hit(mouseX, mouseY, true);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        switch (hit) {
            case HIDE -> ReferenceClient.update(reference.withVisible(false));
            case THROUGH -> ReferenceClient.update(reference.withScreen(reference.screen()
                    .withClickThrough(!reference.screen().clickThrough())));
            default -> {
                dragging = reference.id();
                part = hit == ScreenFrame.Part.GRIP ? ScreenFrame.Part.GRIP : ScreenFrame.Part.BAR;
                startX = mouseX;
                startY = mouseY;
                startRect = window.image();
                dragButton = button;
                ReferenceClient.update(ReferenceClient.set().toFront(reference.id()));
            }
        }
        return true;
    }

    private static boolean release(int button) {
        if (dragging == null || button != dragButton) {
            return false;
        }
        reset();
        ReferenceClient.save();
        return true;
    }

    /** Mientras se arrastra, cada fotograma: la ventana sigue al ratón (aunque se suelte fuera de la pantalla). */
    private static void follow(int mouseX, int mouseY, int width, int height) {
        if (dragging == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (GLFW.glfwGetMouseButton(client.getWindow().getHandle(), dragButton) != GLFW.GLFW_PRESS) {
            reset();
            ReferenceClient.save();
            return;
        }
        Optional<ReferenceImage> found = ReferenceClient.set().byId(dragging);
        if (found.isEmpty()) {
            reset();
            return;
        }
        ReferenceImage reference = found.get();
        int dx = (int) Math.round(mouseX - startX);
        int dy = (int) Math.round(mouseY - startY);
        ScreenFrame next = part == ScreenFrame.Part.GRIP
                ? reference.screen().withImageWidth(Math.max(ScreenFrame.MIN_SIDE, startRect.width() + dx), width)
                : reference.screen().movedTo(startRect.x() + dx, startRect.y() + dy, width, height);
        ReferenceClient.update(reference.withScreen(next));
    }

    /** La rueda sobre una ventana: la agranda o la encoge alrededor del ratón; con Mayús, la opacidad. */
    private static boolean scroll(double mouseX, double mouseY, double amount, int width, int height) {
        if (amount == 0.0 || dragging != null) {
            return dragging != null;
        }
        List<ReferenceImage> drawn = ReferenceClient.set().drawn(ReferenceImage.Mode.SCREEN);
        Optional<ReferenceImage> found = topAt(drawn, mouseX, mouseY, width, height);
        if (found.isEmpty()) {
            return false;
        }
        ReferenceImage reference = found.get();
        if (Screen.hasShiftDown()) {
            ReferenceClient.update(reference.withOpacity(reference.opacity() + Math.signum(amount) * OPACITY_STEP));
        } else {
            double factor = amount > 0 ? ZOOM : 1.0 / ZOOM;
            ReferenceClient.update(reference.withScreen(reference.screen().zoomed(factor, mouseX, mouseY, width,
                    height, reference.pixelWidth(), reference.pixelHeight())));
        }
        return true;
    }
}
