package com.komainu.moxia.sculpt;

import com.komainu.moxia.edit.PackedBlockPlan;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public record SculptPreview(
        RegistryKey<World> world,
        SculptSettings settings,
        String pattern,
        PackedBlockPlan plan,
        long createdAtMillis
) {
    public String description() {
        return "Brocha " + settings.type().displayName();
    }
}
