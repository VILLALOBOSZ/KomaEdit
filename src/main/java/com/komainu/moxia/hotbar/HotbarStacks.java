package com.komainu.moxia.hotbar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.komainu.moxia.KomaEditMod;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.featuretoggle.FeatureSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Entre las pilas de Minecraft y los huecos de {@link HotbarItem}, con {@code ItemStack.CODEC}: así se guardan todos
 * los componentes, también los que Minecraft no manda al cliente, y se leen igual que en un mundo guardado.
 */
public final class HotbarStacks {
    private HotbarStacks() {
    }

    /** El hueco de una pila (vacío si la pila lo está). */
    public static HotbarItem item(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        if (stack.isEmpty()) {
            return HotbarItem.EMPTY;
        }
        DataResult<JsonElement> encoded = ItemStack.CODEC.encodeStart(registries.getOps(JsonOps.INSTANCE), stack);
        Optional<JsonElement> json = encoded.result();
        if (json.isEmpty() || !json.get().isJsonObject()) {
            KomaEditMod.LOGGER.warn("Could not save the components of {} in a hotbar: {}", stack,
                    encoded.error().map(DataResult.Error::message).orElse("?"));
            return HotbarItem.of(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount());
        }
        return HotbarJson.slot(json.get());
    }

    /**
     * La pila de un hueco: vacía si el hueco lo está; vacío el {@code Optional} si el objeto no existe en este
     * servidor, está desactivado o sus componentes no se pueden leer. La cantidad no pasa del máximo del objeto.
     */
    public static Optional<ItemStack> stack(HotbarItem item, RegistryWrapper.WrapperLookup registries,
                                            FeatureSet features) {
        if (item.isEmpty()) {
            return Optional.of(ItemStack.EMPTY);
        }
        JsonElement json = HotbarJson.slot(item);
        if (!(json instanceof JsonObject)) {
            return Optional.empty();
        }
        Optional<ItemStack> parsed = ItemStack.CODEC.parse(registries.getOps(JsonOps.INSTANCE), json).result();
        if (parsed.isEmpty() || parsed.get().isEmpty() || !parsed.get().isItemEnabled(features)) {
            return Optional.empty();
        }
        ItemStack stack = parsed.get();
        stack.setCount(HotbarRules.clamp(item, stack.getMaxCount()).count());
        return Optional.of(stack);
    }

    /** Los nueve huecos de la barra rápida del jugador. */
    public static List<HotbarItem> hotbar(PlayerInventory inventory, RegistryWrapper.WrapperLookup registries) {
        List<HotbarItem> items = new ArrayList<>(SavedHotbar.SLOTS);
        for (int slot = 0; slot < SavedHotbar.SLOTS; slot++) {
            items.add(item(inventory.getStack(slot), registries));
        }
        return items;
    }

    /** Los 36 huecos del inventario principal (barra rápida incluida). */
    public static List<HotbarItem> inventory(PlayerInventory inventory, RegistryWrapper.WrapperLookup registries) {
        List<HotbarItem> items = new ArrayList<>(HotbarReorder.INVENTORY);
        for (int slot = 0; slot < HotbarReorder.INVENTORY; slot++) {
            items.add(item(inventory.getStack(slot), registries));
        }
        return items;
    }
}
