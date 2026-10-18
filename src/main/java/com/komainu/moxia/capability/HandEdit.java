package com.komainu.moxia.capability;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** Un bloque que una capacidad va a poner: posición y estado de Minecraft. */
public record HandEdit(BlockPos pos, BlockState state) {
    public HandEdit {
        pos = pos.toImmutable();
    }
}
