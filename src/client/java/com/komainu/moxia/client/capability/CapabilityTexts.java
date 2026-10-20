package com.komainu.moxia.client.capability;

import com.komainu.moxia.capability.BuildCapability;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** Textos traducidos de las capacidades ({@code gui.komaedit.capability.<id>}, {@code .short} y {@code .desc}). */
public final class CapabilityTexts {
    private CapabilityTexts() {
    }

    public static String key(BuildCapability capability) {
        return "gui.komaedit.capability." + capability.id();
    }

    public static MutableText name(BuildCapability capability) {
        return Text.translatable(key(capability));
    }

    public static MutableText shortName(BuildCapability capability) {
        return Text.translatable(key(capability) + ".short");
    }

    public static MutableText description(BuildCapability capability) {
        return Text.translatable(key(capability) + ".desc");
    }
}
