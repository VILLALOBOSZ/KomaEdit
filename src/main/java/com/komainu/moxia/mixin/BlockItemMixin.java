package com.komainu.moxia.mixin;

import com.komainu.moxia.symmetry.SymmetryService;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Avisa a la simetría de cada bloque que un jugador coloca con la mano. Fabric no tiene evento de colocar: antes se
 * apunta cómo estaban el sitio y sus vecinos, y después se copia lo que haya aparecido (también la otra mitad de una
 * puerta o una cama, que pone el propio bloque al colocarse).
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"))
    private void komaedit$symmetryBefore(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> callback) {
        if (!context.getWorld().isClient()) {
            SymmetryService.beforePlace(context.getPlayer(), context.getWorld(), context.getBlockPos());
        }
    }

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN"))
    private void komaedit$symmetryAfter(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> callback) {
        if (!context.getWorld().isClient()) {
            SymmetryService.afterPlace(context.getPlayer(), context.getWorld(), context.getBlockPos(),
                    callback.getReturnValue().isAccepted());
        }
    }
}
