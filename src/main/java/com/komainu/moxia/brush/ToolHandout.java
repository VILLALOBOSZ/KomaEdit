package com.komainu.moxia.brush;

import com.komainu.moxia.feedback.Feedback;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

/**
 * Entregar una herramienta del mod (el cepillo del esculpido y del brush de figuras) sin perderla nunca: si ya la
 * tiene, nada; con la mano vacía, a la mano; si cabe, al inventario; y con el inventario lleno no se tira al suelo:
 * se dice con claridad que haga sitio. Los pinceles y la supervarita van siempre en la mano ({@link BrushTool#bind}).
 */
public final class ToolHandout {
    private ToolHandout() {
    }

    /** Qué pasó con la herramienta. */
    public enum Result {
        /** Ya la llevaba. */
        ALREADY,
        IN_HAND,
        IN_INVENTORY,
        /** No cabía: no se ha dado nada. */
        FULL
    }

    /** La decisión, sin Minecraft: lo que haría {@link #give} con el inventario así. */
    public static Result decide(boolean alreadyHas, boolean handEmpty, boolean room) {
        if (alreadyHas) {
            return Result.ALREADY;
        }
        if (handEmpty) {
            return Result.IN_HAND;
        }
        return room ? Result.IN_INVENTORY : Result.FULL;
    }

    /** El aviso de cuando no cabe: qué falta y qué hacer. */
    public static String fullMessage(String name) {
        return "Inventario lleno: no te he dado " + name + " para no tirarlo al suelo. Vacía la mano o haz sitio y "
                + "vuelve a pedirlo";
    }

    /**
     * Da {@code stack} a {@code player} si aún no tiene uno igual.
     *
     * @param name cómo se llama en el aviso («el cepillo»)
     */
    public static Result give(ServerPlayerEntity player, ItemStack stack, String name) {
        PlayerInventory inventory = player.getInventory();
        boolean room = inventory.getEmptySlot() >= 0 || inventory.getOccupiedSlotWithRoomForStack(stack) >= 0;
        Result result = decide(inventory.contains(stack), player.getMainHandStack().isEmpty(), room);
        switch (result) {
            case IN_HAND -> player.setStackInHand(Hand.MAIN_HAND, stack);
            case IN_INVENTORY -> inventory.insertStack(stack);
            case FULL -> Feedback.warn(player, fullMessage(name));
            case ALREADY -> {
            }
        }
        return result;
    }
}
