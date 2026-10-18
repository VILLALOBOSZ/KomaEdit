package com.komainu.moxia.capability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

/**
 * Contexto de colocación en el bloque pulsado, no en el de al lado. Lo usa «Reemplazar» para calcular el estado del
 * bloque nuevo (hacia dónde mira, mitad de arriba o de abajo, conexiones) como si se colocara en su sitio.
 */
public final class TargetPlacementContext extends ItemPlacementContext {
    public TargetPlacementContext(PlayerEntity player, Hand hand, ItemStack stack, BlockHitResult hit) {
        super(player, hand, stack, hit);
        this.canReplaceExisting = true;
    }
}
