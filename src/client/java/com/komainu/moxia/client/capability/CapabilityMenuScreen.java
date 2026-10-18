package com.komainu.moxia.client.capability;

import com.komainu.moxia.capability.BuildCapability;
import com.komainu.moxia.capability.CapabilityOption;
import com.komainu.moxia.capability.CapabilityStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menú rápido de las capacidades: un círculo con las ocho alrededor, la velocidad de vuelo y el modo de juego debajo.
 *
 * <p>Se abre manteniendo la tecla (Z por defecto). Al soltarla se cierra; si no se ha pulsado nada y el ratón
 * apunta a una capacidad, la alterna (gesto rápido). Pulsada y soltada en seguida, el menú se queda abierto hasta
 * Esc o volver a pulsarla. Clic izquierdo alterna, clic derecho cambia su opción y la rueda ajusta su número.</p>
 */
public final class CapabilityMenuScreen extends Screen {
    private static final BuildCapability[] RING = BuildCapability.values();
    private static final int TAP_TICKS = 5;
    private static final int ITEM_HEIGHT = 16;
    private static final int[] FLY_STEPS = {5, 10, 15, 20, 25, 30, 40, 50, 60, 80, 100, 150, 200};
    private static final GameMode[] MODES = {GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.ADVENTURE,
            GameMode.SPECTATOR};

    private final InputUtil.Key holdKey;
    private boolean sticky;
    private boolean clicked;
    private int age;
    private int hovered = -1;
    private int centerX;
    private int centerY;
    private int radiusX;
    private int radiusY;
    private int itemWidth;
    private int flyRowY;
    private ButtonWidget flySlower;
    private ButtonWidget flyFaster;
    private final List<ButtonWidget> modeButtons = new ArrayList<>();

    /**
     * @param holdKey tecla que lo abrió (se cierra al soltarla), o {@code null} para dejarlo abierto
     * @param sticky  abierto hasta Esc desde el principio
     */
    public CapabilityMenuScreen(InputUtil.Key holdKey, boolean sticky) {
        super(Text.translatable("gui.komaedit.capability.title"));
        this.holdKey = holdKey;
        this.sticky = sticky || holdKey == null || holdKey.equals(InputUtil.UNKNOWN_KEY);
    }

    @Override
    protected void init() {
        modeButtons.clear();
        itemWidth = Math.clamp(width / 5, 64, 100);
        radiusY = Math.clamp(height / 2 - 62, 36, 90);
        radiusX = Math.max(radiusY, Math.min((int) (radiusY * 1.6), width / 2 - itemWidth / 2 - 6));
        centerX = width / 2;
        centerY = height / 2 - 14;
        flyRowY = centerY + radiusY + 14;
        flySlower = addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> flyStep(-1))
                .dimensions(centerX - 66, flyRowY, 20, 18).build());
        flyFaster = addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> flyStep(1))
                .dimensions(centerX + 46, flyRowY, 20, 18).build());
        int modeWidth = Math.min(80, (width - 20) / MODES.length - 4);
        int left = centerX - (MODES.length * (modeWidth + 4) - 4) / 2;
        for (int index = 0; index < MODES.length; index++) {
            GameMode mode = MODES[index];
            modeButtons.add(addDrawableChild(ButtonWidget.builder(mode.getSimpleTranslatableName(), button -> {
                clicked = true;
                CapabilityControls.gamemode(mode);
            }).dimensions(left + index * (modeWidth + 4), flyRowY + 22, modeWidth, 18).build()));
        }
        refreshWidgets();
    }

    @Override
    public void tick() {
        age++;
        refreshWidgets();
        if (client == null || client.player == null || !CapabilityClientState.supported()) {
            close();
            return;
        }
        if (!sticky && !held()) {
            if (age <= TAP_TICKS && !clicked) {
                sticky = true;
                return;
            }
            if (!clicked && hovered >= 0) {
                CapabilityControls.toggle(RING[hovered]);
            }
            close();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (sticky && holdKey != null && holdKey.getCategory() == InputUtil.Type.KEYSYM
                && holdKey.getCode() == keyCode) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int index = sector(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        clicked = true;
        BuildCapability capability = RING[index];
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CapabilityControls.toggle(capability);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            secondary(capability);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int steps = (int) Math.signum(verticalAmount);
        if (steps == 0) {
            return false;
        }
        if (mouseY >= flyRowY - 2 && mouseY <= flyRowY + 20 && Math.abs(mouseX - centerX) <= 70) {
            flyStep(steps);
            return true;
        }
        int index = sector(mouseX, mouseY);
        CapabilityStatePayload state = CapabilityClientState.get();
        if (index < 0 || state == null) {
            return false;
        }
        boolean fast = hasShiftDown();
        switch (RING[index]) {
            case ANGEL -> CapabilityControls.option(CapabilityOption.ANGEL_DISTANCE,
                    state.angelDistance() + steps * (fast ? 4 : 1));
            case REACH -> CapabilityControls.option(CapabilityOption.REACH_DISTANCE,
                    state.reachDistance() + steps * (fast ? 8 : 1));
            case EXTRUDE -> CapabilityControls.option(CapabilityOption.EXTRUDE_LIMIT,
                    steps > 0 ? state.extrudeLimit() * 2 : Math.max(1, state.extrudeLimit() / 2));
            default -> {
                return false;
            }
        }
        clicked = true;
        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x66000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        hovered = sector(mouseX, mouseY);
        CapabilityStatePayload state = CapabilityClientState.get();
        if (state == null) {
            return;
        }
        drawRing(context);
        for (int index = 0; index < RING.length; index++) {
            drawItem(context, state, index);
        }
        context.drawCenteredTextWithShadow(textRenderer, title, centerX, centerY - 9, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable(sticky
                ? "gui.komaedit.capability.hint_sticky" : "gui.komaedit.capability.hint_hold"),
                centerX, centerY + 3, 0xFF9A9A9A);
        if (state.flySpeedAllowed()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.komaedit.capability.flyspeed",
                    String.format(Locale.ROOT, "%.1f", state.flySpeed() / 10.0)), centerX, flyRowY + 5, 0xFFE0E0E0);
        }
        drawInfo(context, state);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- Dibujo -------------------------------------------------------------------------------------------------

    private void drawRing(DrawContext context) {
        int dots = 72;
        for (int dot = 0; dot < dots; dot++) {
            double angle = Math.PI * 2.0 * dot / dots;
            int x = centerX + (int) Math.round(Math.cos(angle) * radiusX);
            int y = centerY + (int) Math.round(Math.sin(angle) * radiusY);
            context.fill(x - 1, y - 1, x + 1, y + 1, 0x88FFFFFF);
        }
    }

    private void drawItem(DrawContext context, CapabilityStatePayload state, int index) {
        BuildCapability capability = RING[index];
        int x = itemX(index) - itemWidth / 2;
        int y = itemY(index) - ITEM_HEIGHT / 2;
        boolean on = (state.enabled() & capability.bit()) != 0;
        boolean permitted = (state.permitted() & capability.bit()) != 0;
        boolean usable = permitted && state.modeAllowed();
        int fill = on ? (usable ? 0xE0215C3A : 0xE0353D37) : 0xE0141414;
        int border = index == hovered ? 0xFFFFFFFF : on ? 0xFF5BE38A : 0xFF4A4A4A;
        context.fill(x, y, x + itemWidth, y + ITEM_HEIGHT, fill);
        context.drawBorder(x, y, itemWidth, ITEM_HEIGHT, border);
        MutableText label = CapabilityTexts.shortName(capability);
        String value = switch (capability) {
            case ANGEL -> String.valueOf(state.angelDistance());
            case REACH -> String.valueOf(state.reachDistance());
            case EXTRUDE -> String.valueOf(Math.min(state.extrudeLimit(), state.maxLayerBlocks()));
            default -> null;
        };
        if (value != null) {
            label.append(Text.literal(" " + value).formatted(Formatting.GRAY));
        }
        int color = !permitted ? 0xFF8A5050 : on ? 0xFFFFFFFF : 0xFFAAAAAA;
        context.drawCenteredTextWithShadow(textRenderer, label, x + itemWidth / 2, y + (ITEM_HEIGHT - 8) / 2, color);
    }

    /** Nombre, descripción y lo que hace la rueda o el clic derecho de la capacidad bajo el ratón, arriba. */
    private void drawInfo(DrawContext context, CapabilityStatePayload state) {
        if (hovered < 0) {
            return;
        }
        BuildCapability capability = RING[hovered];
        boolean on = (state.enabled() & capability.bit()) != 0;
        boolean permitted = (state.permitted() & capability.bit()) != 0;
        MutableText headline = CapabilityTexts.name(capability).formatted(Formatting.BOLD)
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(Text.translatable(on ? "gui.komaedit.capability.on" : "gui.komaedit.capability.off")
                        .formatted(on ? Formatting.GREEN : Formatting.GRAY));
        if (!permitted) {
            headline.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable("gui.komaedit.capability.no_permission").formatted(Formatting.RED));
        } else if (!state.modeAllowed()) {
            headline.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable("gui.komaedit.capability.creative_only").formatted(Formatting.GOLD));
        }
        List<OrderedText> description = textRenderer.wrapLines(CapabilityTexts.description(capability),
                Math.min(320, width - 20));
        Text hint = hint(capability, state);
        int lines = 1 + description.size() + (hint == null ? 0 : 1);
        int top = Math.max(4, centerY - radiusY - ITEM_HEIGHT / 2 - 4 - lines * 10);
        context.drawCenteredTextWithShadow(textRenderer, headline, centerX, top, 0xFFFFFFFF);
        int y = top + 10;
        for (OrderedText line : description) {
            context.drawCenteredTextWithShadow(textRenderer, line, centerX, y, 0xFFCFCFCF);
            y += 10;
        }
        if (hint != null) {
            context.drawCenteredTextWithShadow(textRenderer, hint, centerX, y, 0xFF8FD3FF);
        }
    }

    private Text hint(BuildCapability capability, CapabilityStatePayload state) {
        MutableText hint = switch (capability) {
            case REPLACE -> Text.translatable("gui.komaedit.capability.hint.family", Text.translatable(
                    state.replaceFamily() ? "gui.komaedit.capability.yes" : "gui.komaedit.capability.no"));
            case EXTRUDE -> Text.translatable("gui.komaedit.capability.hint.extrude",
                    Math.min(state.extrudeLimit(), state.maxLayerBlocks()), Text.translatable(state.extrudeByState()
                            ? "gui.komaedit.capability.match.state" : "gui.komaedit.capability.match.type"));
            case ANGEL -> Text.translatable("gui.komaedit.capability.hint.distance", state.angelDistance());
            case REACH -> Text.translatable("gui.komaedit.capability.hint.reach", state.reachDistance(),
                    state.maxReach());
            case TWEAK -> Text.translatable("gui.komaedit.capability.hint.tweak", state.tweakProperty().isEmpty()
                    ? Text.translatable("gui.komaedit.capability.tweak.auto") : Text.literal(state.tweakProperty()));
            case SMEAR -> state.smearPending() ? Text.translatable("gui.komaedit.capability.hint.smear_cancel") : null;
            default -> null;
        };
        KeyBinding key = CapabilityControls.toggleKey(capability);
        if (key != null && !key.isUnbound()) {
            Text shortcut = Text.translatable("gui.komaedit.capability.shortcut", key.getBoundKeyLocalizedText());
            hint = hint == null ? shortcut.copy() : hint.append(Text.literal(" · ")).append(shortcut);
        }
        return hint;
    }

    // ---- Lógica -------------------------------------------------------------------------------------------------

    private void refreshWidgets() {
        CapabilityStatePayload state = CapabilityClientState.get();
        boolean fly = state != null && state.flySpeedAllowed();
        if (flySlower != null) {
            flySlower.visible = fly;
            flyFaster.visible = fly;
        }
        boolean modes = state != null && state.gamemode();
        GameMode current = client == null || client.interactionManager == null
                ? null : client.interactionManager.getCurrentGameMode();
        for (int index = 0; index < modeButtons.size(); index++) {
            ButtonWidget button = modeButtons.get(index);
            button.visible = modes;
            button.active = MODES[index] != current;
        }
    }

    private void secondary(BuildCapability capability) {
        CapabilityStatePayload state = CapabilityClientState.get();
        if (state == null) {
            return;
        }
        switch (capability) {
            case REPLACE -> CapabilityControls.option(CapabilityOption.REPLACE_FAMILY, state.replaceFamily() ? 0 : 1);
            case EXTRUDE -> CapabilityControls.option(CapabilityOption.EXTRUDE_MATCH, state.extrudeByState() ? 0 : 1);
            case SMEAR -> CapabilityControls.cancelSmear();
            default -> {
            }
        }
    }

    private void flyStep(int direction) {
        CapabilityStatePayload state = CapabilityClientState.get();
        if (state == null || !state.flySpeedAllowed()) {
            return;
        }
        clicked = true;
        int current = state.flySpeed();
        int nearest = 0;
        for (int index = 0; index < FLY_STEPS.length; index++) {
            if (Math.abs(FLY_STEPS[index] - current) < Math.abs(FLY_STEPS[nearest] - current)) {
                nearest = index;
            }
        }
        int next = FLY_STEPS[Math.clamp(nearest + direction, 0, FLY_STEPS.length - 1)];
        CapabilityControls.option(CapabilityOption.FLY_SPEED, Math.min(next, state.maxFlySpeed()));
    }

    /** Capacidad del sector al que apunta el ratón, o -1 cerca del centro o lejos del círculo. */
    private int sector(double mouseX, double mouseY) {
        double dx = (mouseX - centerX) / Math.max(1, radiusX);
        double dy = (mouseY - centerY) / Math.max(1, radiusY);
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < 0.4 || distance > 1.6 || mouseY > flyRowY - 2) {
            return -1;
        }
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
        double slice = Math.PI * 2.0 / RING.length;
        return Math.floorMod((int) Math.round(angle / slice), RING.length);
    }

    private int itemX(int index) {
        return centerX + (int) Math.round(Math.cos(angle(index)) * radiusX);
    }

    private int itemY(int index) {
        return centerY + (int) Math.round(Math.sin(angle(index)) * radiusY);
    }

    private static double angle(int index) {
        return -Math.PI / 2.0 + Math.PI * 2.0 * index / RING.length;
    }

    private boolean held() {
        if (holdKey == null || client == null) {
            return false;
        }
        long window = client.getWindow().getHandle();
        if (holdKey.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, holdKey.getCode()) == GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(window, holdKey.getCode());
    }

    /** Abre el menú fijo (por ejemplo desde otra pantalla). */
    public static void open(MinecraftClient client) {
        if (client.player != null && CapabilityClientState.supported()) {
            client.setScreen(new CapabilityMenuScreen(null, true));
        }
    }
}
