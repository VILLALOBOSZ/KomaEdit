package com.komainu.moxia.brush;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Herramientas de KomaEdit (pinceles y supervarita) ligadas a un objeto concreto. La marca va en los datos propios
 * del objeto ({@code custom_data}), que el cliente también ve: así sabe que el clic izquierdo y la tecla de cambiar
 * de mano son de la herramienta y no rompe bloques ni cambia de mano.
 */
public final class BrushTool {
    public static final String KEY = "komaedit_tool";
    /** Radio del pincel, para que el cliente lo dibuje en la superficie que se mira (0 o ausente: sin radio). */
    public static final String RADIUS_KEY = "komaedit_radius";

    private BrushTool() {
    }

    public enum Kind {
        BRUSH("pincel"),
        SUPERWAND("superwand");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }

    /** Id de la herramienta del objeto ({@code pincel:12345}), o {@code null} si no es una herramienta. */
    public static String id(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || !data.contains(KEY)) {
            return null;
        }
        String id = data.copyNbt().getString(KEY);
        return id.isEmpty() ? null : id;
    }

    public static boolean isTool(ItemStack stack) {
        return id(stack) != null;
    }

    public static Kind kind(String id) {
        if (id == null) {
            return null;
        }
        for (Kind kind : Kind.values()) {
            if (id.startsWith(kind.prefix + ":")) {
                return kind;
            }
        }
        return null;
    }

    /**
     * Marca el objeto de la mano principal como herramienta o, con la mano vacía, entrega uno nuevo.
     * Un objeto que ya era herramienta del mismo tipo conserva su id (se reconfigura).
     *
     * @return el id de la herramienta
     */
    public static String bind(ServerPlayerEntity player, Kind kind, Item fallback, String name) {
        ItemStack held = player.getStackInHand(Hand.MAIN_HAND);
        String existing = id(held);
        if (existing != null && kind(existing) == kind) {
            return existing;
        }
        String id = kind.prefix + ":" + Integer.toUnsignedString(ThreadLocalRandom.current().nextInt(), 36);
        if (held.isEmpty()) {
            ItemStack stack = new ItemStack(fallback);
            mark(stack, id);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(Formatting.GOLD));
            player.setStackInHand(Hand.MAIN_HAND, stack);
        } else {
            mark(held, id);
        }
        return id;
    }

    private static void mark(ItemStack stack, String id) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, (NbtCompound nbt) -> nbt.putString(KEY, id));
    }

    /** Radio del pincel del objeto, o 0 si no lo tiene. */
    public static int radius(ItemStack stack) {
        if (!isTool(stack)) {
            return 0;
        }
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data == null ? 0 : Math.max(0, data.copyNbt().getInt(RADIUS_KEY));
    }

    /** Apunta en el objeto de la mano principal el radio de su pincel (0 lo quita). */
    public static void setRadius(ServerPlayerEntity player, int radius) {
        ItemStack held = player.getStackInHand(Hand.MAIN_HAND);
        if (!isTool(held)) {
            return;
        }
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, held, (NbtCompound nbt) -> {
            if (radius > 0) {
                nbt.putInt(RADIUS_KEY, radius);
            } else {
                nbt.remove(RADIUS_KEY);
            }
        });
    }

    /** Quita la marca del objeto de la mano principal; devuelve el id que tenía. */
    public static String unbind(ServerPlayerEntity player) {
        ItemStack held = player.getStackInHand(Hand.MAIN_HAND);
        String id = id(held);
        if (id != null) {
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, held, (NbtCompound nbt) -> {
                nbt.remove(KEY);
                nbt.remove(RADIUS_KEY);
            });
            NbtComponent data = held.get(DataComponentTypes.CUSTOM_DATA);
            if (data != null && data.isEmpty()) {
                held.remove(DataComponentTypes.CUSTOM_DATA);
            }
        }
        return id;
    }
}
