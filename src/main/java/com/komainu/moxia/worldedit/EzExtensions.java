package com.komainu.moxia.worldedit;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.worldedit.ez.EzContext;
import com.komainu.moxia.worldedit.ez.EzMaskParser;
import com.komainu.moxia.worldedit.ez.EzPatternParser;
import com.sk89q.worldedit.WorldEdit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Registra en WorldEdit las máscaras y patrones con la notación de ezEdits ({@code #palette}, {@code #near},
 * {@code #eznoisepattern}…), para que valgan en cualquier orden de WorldEdit. Se registran una vez por proceso al
 * arrancar el primer servidor; las factorías de WorldEdit duran lo que el proceso.
 */
public final class EzExtensions {
    private static boolean installed;

    private EzExtensions() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            EzContext.server(server);
            install();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> EzContext.server(null));
    }

    private static synchronized void install() {
        if (installed) {
            return;
        }
        WorldEdit worldEdit = WorldEdit.getInstance();
        worldEdit.getMaskFactory().register(new EzMaskParser(worldEdit));
        worldEdit.getPatternFactory().register(new EzPatternParser(worldEdit));
        installed = true;
        KomaEditMod.LOGGER.info("KomaEdit masks and patterns registered in WorldEdit");
    }
}
