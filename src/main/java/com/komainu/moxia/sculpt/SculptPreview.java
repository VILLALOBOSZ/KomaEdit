package com.komainu.moxia.sculpt;

import com.komainu.moxia.edit.PackedBlockPlan;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/**
 * Vista previa pendiente de aplicar. Las brochas de esculpido llevan sus ajustes; las pasadas de
 * {@link BrushOperation} los dejan a {@code null} y traen su propio nombre y color.
 */
public record SculptPreview(
        RegistryKey<World> world,
        SculptSettings settings,
        String pattern,
        PackedBlockPlan plan,
        long createdAtMillis,
        String label,
        int color
) {
    public SculptPreview(
            RegistryKey<World> world,
            SculptSettings settings,
            String pattern,
            PackedBlockPlan plan,
            long createdAtMillis
    ) {
        this(world, settings, pattern, plan, createdAtMillis, null, -1);
    }

    public String description() {
        return label != null ? label : "Brocha " + settings.type().displayName();
    }
}
