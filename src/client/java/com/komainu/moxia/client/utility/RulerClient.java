package com.komainu.moxia.client.utility;

import com.komainu.moxia.client.world.SketchRenderer;
import com.komainu.moxia.feedback.WorldSketch;
import com.komainu.moxia.ruler.Ruler;
import com.komainu.moxia.ruler.RulerMode;
import com.komainu.moxia.ruler.RulerPoint;
import com.komainu.moxia.ruler.RulerReport;
import com.komainu.moxia.ruler.RulerSketch;
import com.komainu.moxia.shape.Vec3;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * La regla en el cliente: con ella puesta, el clic derecho pone un punto en el bloque que se mira (con Mayús, en el
 * de al lado de la cara), el izquierdo quita el último, Ctrl+clic cambia de modo, Ctrl+C copia las medidas y Supr las
 * borra. Mientras se apunta, se ve lo que mediría el siguiente clic. No manda nada al servidor.
 */
public final class RulerClient {
    private static final double REACH = 256.0;
    private static final Identifier EARLY = Identifier.of("komaedit", "ruler");
    private static final Ruler RULER = new Ruler();
    private static KeyBinding toggle;
    private static boolean active;
    private static boolean useArmed = true;
    private static boolean copyHeld;
    private static boolean clearHeld;
    private static RulerPoint hover;
    private static WorldSketch sketch = WorldSketch.EMPTY;
    private static boolean broken;

    private RulerClient() {
    }

    public static void register() {
        toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.komaedit.ruler", InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(), "category.komaedit"));
        // Antes que pinceles y capacidades: con la regla puesta, el clic izquierdo es suyo.
        ClientPreAttackCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        ClientPreAttackCallback.EVENT.register(EARLY, (client, player, clickCount) -> onAttack(client, clickCount));
        ClientTickEvents.END_CLIENT_TICK.register(RulerClient::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(RulerClient::renderWorld);
        WorldRenderEvents.LAST.register(RulerClient::renderGhost);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            active = false;
            RULER.clear();
            sketch = WorldSketch.EMPTY;
        }));
    }

    public static boolean active() {
        return active;
    }

    public static Ruler ruler() {
        return RULER;
    }

    /** Pone o quita la regla; con un modo, la pone en ese modo. */
    public static void toggle(MinecraftClient client, RulerMode mode) {
        if (mode != null) {
            if (RULER.mode() != mode) {
                RULER.mode(mode);
            }
            active = true;
        } else {
            active = !active;
        }
        status(client, active ? RulerReport.summary(RULER, ClientWords.INSTANCE) + " · "
                + ClientWords.INSTANCE.tr("gui.komaedit.ruler.hint") : ClientWords.INSTANCE.tr("gui.komaedit.ruler.off"));
    }

    public static void clear(MinecraftClient client) {
        RULER.clear();
        status(client, RulerReport.summary(RULER, ClientWords.INSTANCE));
    }

    /** Copia el informe entero al portapapeles. */
    public static void copy(MinecraftClient client) {
        client.keyboard.setClipboard(RulerReport.text(RULER, ClientWords.INSTANCE));
        status(client, ClientWords.INSTANCE.tr("gui.komaedit.ruler.copied"));
    }

    /** Clic derecho (desde {@code MinecraftClient.doItemUse}): {@code true} si era de la regla. */
    public static boolean onUse(MinecraftClient client) {
        if (!active || client.currentScreen != null || client.player == null) {
            return false;
        }
        if (useArmed) {
            useArmed = false;
            RulerPoint point = aim(client, shift(client));
            if (point != null && RULER.add(point)) {
                client.player.swingHand(Hand.MAIN_HAND);
                click(client, 1.6F);
                status(client, RulerReport.summary(RULER, ClientWords.INSTANCE));
            }
        }
        return true;
    }

    private static boolean onAttack(MinecraftClient client, int clickCount) {
        if (!active || client.currentScreen != null || client.player == null) {
            return false;
        }
        if (clickCount > 0) {
            if (control(client)) {
                RULER.cycleMode();
            } else {
                RULER.removeLast();
            }
            click(client, 1.0F);
            status(client, RulerReport.summary(RULER, ClientWords.INSTANCE));
        }
        return true;
    }

    private static void tick(MinecraftClient client) {
        while (toggle != null && toggle.wasPressed()) {
            toggle(client, null);
        }
        if (!client.options.useKey.isPressed()) {
            useArmed = true;
        }
        if (!active || client.player == null || client.world == null) {
            hover = null;
            sketch = WorldSketch.EMPTY;
            return;
        }
        if (client.currentScreen == null) {
            long window = client.getWindow().getHandle();
            boolean copy = control(client) && InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_C);
            if (copy && !copyHeld) {
                copy(client);
            }
            copyHeld = copy;
            boolean clear = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_DELETE);
            if (clear && !clearHeld) {
                clear(client);
            }
            clearHeld = clear;
            hover = aim(client, shift(client));
        } else {
            hover = null;
        }
        Vec3 camera = new Vec3(client.gameRenderer.getCamera().getPos().x, client.gameRenderer.getCamera().getPos().y,
                client.gameRenderer.getCamera().getPos().z);
        sketch = RulerSketch.of(RULER, camera, hover, ClientWords.INSTANCE);
        if (client.player.age % 20 == 0) {
            status(client, RulerReport.summary(RULER, ClientWords.INSTANCE));
        }
    }

    /** El bloque mirado (con {@code beside}, el de al lado de la cara), o {@code null}. */
    private static RulerPoint aim(MinecraftClient client, boolean beside) {
        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        HitResult hit = camera.raycast(REACH, 1.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = beside ? block.getBlockPos().offset(block.getSide()) : block.getBlockPos();
        return RulerPoint.block(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void renderWorld(WorldRenderContext context) {
        if (!broken && active) {
            broken = !SketchRenderer.safely(() -> SketchRenderer.drawWorld(context, sketch), "ruler");
        }
    }

    private static void renderGhost(WorldRenderContext context) {
        if (!broken && active) {
            broken = !SketchRenderer.safely(() -> SketchRenderer.drawGhost(context, sketch), "ruler");
        }
    }

    private static boolean shift(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean control(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static void click(MinecraftClient client, float pitch) {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.25F));
    }

    static void status(MinecraftClient client, String text) {
        if (client.inGameHud != null) {
            client.inGameHud.setOverlayMessage(ClientWords.prefix().append(Text.literal(text)
                    .formatted(Formatting.GRAY)), false);
        }
    }
}
