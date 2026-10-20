package com.komainu.moxia.client.gui.studio;

import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.ui.StudioCanvas;
import com.komainu.moxia.studio.ui.StudioImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * El lienzo del Studio en el juego: rectángulos y texto del {@link DrawContext}, iconos de objeto y las imágenes del
 * Studio subidas como texturas (una vez por clave; las menos usadas se liberan).
 */
public final class DrawContextCanvas implements StudioCanvas {
    private static final int MAX_TEXTURES = 192;
    private static final Map<String, Identifier> TEXTURES = new LinkedHashMap<>(64, 0.75F, true);
    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    private final TextRenderer text;
    private final Deque<Rect> clips = new ArrayDeque<>();
    private DrawContext context;
    private int overlays;

    public DrawContextCanvas(TextRenderer text) {
        this.text = text;
    }

    public void begin(DrawContext context) {
        this.context = context;
        clips.clear();
        overlays = 0;
    }

    public DrawContext context() {
        return context;
    }

    @Override
    public int width(String value) {
        return text.getWidth(value);
    }

    @Override
    public int boldWidth(String value) {
        return text.getWidth(Text.literal(value).setStyle(Style.EMPTY.withBold(true)));
    }

    @Override
    public void text(String value, int x, int y, int argb) {
        context.drawText(text, value, x, y, argb, false);
    }

    @Override
    public void bold(String value, int x, int y, int argb) {
        context.drawText(text, Text.literal(value).setStyle(Style.EMPTY.withBold(true)), x, y, argb, false);
    }

    @Override
    public void fill(Rect rect, int argb) {
        if (!rect.isEmpty()) {
            context.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), argb);
        }
    }

    @Override
    public void item(String id, int x, int y) {
        context.drawItem(stack(id), x, y);
    }

    /** El objeto de {@code id}: el objeto con ese nombre o, si no, el del bloque; una barrera si no hay ninguno. */
    public static ItemStack stack(String id) {
        return STACKS.computeIfAbsent(id, key -> {
            Identifier identifier = Identifier.tryParse(key.contains(":") ? key : "minecraft:" + key);
            if (identifier == null) {
                return Items.BARRIER.getDefaultStack();
            }
            ItemStack stack = Registries.ITEM.containsId(identifier)
                    ? Registries.ITEM.get(identifier).getDefaultStack() : ItemStack.EMPTY;
            if (stack.isEmpty() && Registries.BLOCK.containsId(identifier)) {
                stack = Registries.BLOCK.get(identifier).asItem().getDefaultStack();
            }
            return stack.isEmpty() ? Items.BARRIER.getDefaultStack() : stack;
        });
    }

    @Override
    public void image(StudioImage image, Rect target) {
        Identifier texture = image.handle() instanceof Identifier handle ? handle : texture(image);
        if (texture == null) {
            return;
        }
        RenderSystem.enableBlend();
        context.drawTexture(texture, target.x(), target.y(), target.width(), target.height(), 0, 0,
                image.width(), image.height(), image.width(), image.height());
        RenderSystem.disableBlend();
    }

    private static Identifier texture(StudioImage image) {
        Identifier known = TEXTURES.get(image.key());
        if (known != null) {
            return known;
        }
        int[] pixels = image.pixels();
        if (pixels.length == 0) {
            return null;
        }
        NativeImage nativeImage = new NativeImage(image.width(), image.height(), false);
        for (int row = 0; row < image.height(); row++) {
            for (int column = 0; column < image.width(); column++) {
                int argb = pixels[row * image.width() + column];
                // NativeImage guarda ABGR.
                nativeImage.setColor(column, row, argb & 0xFF00FF00 | (argb >> 16) & 0xFF | (argb & 0xFF) << 16);
            }
        }
        Identifier id = Identifier.of("komaedit", "studio/" + sanitize(image.key()));
        MinecraftClient client = MinecraftClient.getInstance();
        client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(nativeImage));
        TEXTURES.put(image.key(), id);
        if (TEXTURES.size() > MAX_TEXTURES) {
            Iterator<Map.Entry<String, Identifier>> oldest = TEXTURES.entrySet().iterator();
            client.getTextureManager().destroyTexture(oldest.next().getValue());
            oldest.remove();
        }
        return id;
    }

    private static String sanitize(String key) {
        StringBuilder out = new StringBuilder();
        for (char character : key.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(character >= 'a' && character <= 'z' || character >= '0' && character <= '9'
                    || character == '_' || character == '-' || character == '/' || character == '.' ? character : '_');
        }
        return out.toString();
    }

    /** El {@code DrawContext} lleva su propia pila de recortes: cada push tiene su pop. */
    @Override
    public void pushClip(Rect clip) {
        clips.push(clip);
        context.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
    }

    @Override
    public void popClip() {
        if (!clips.isEmpty()) {
            clips.pop();
            context.disableScissor();
        }
    }

    @Override
    public void beginOverlay() {
        overlays++;
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 400.0F);
    }

    @Override
    public void endOverlay() {
        if (overlays > 0) {
            overlays--;
            context.getMatrices().pop();
        }
    }
}
