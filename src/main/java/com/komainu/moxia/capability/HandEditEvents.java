package com.komainu.moxia.capability;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.function.Consumer;

/**
 * Ganchos de las ediciones a mano de las capacidades. Las aplica el servidor con una {@code EditSession} de
 * WorldEdit, no con la colocación de Minecraft, así que un gancho sobre {@code BlockItem.place} no las ve.
 */
public final class HandEditEvents {
    /**
     * Antes de aplicar una acción (reemplazar, retocar, extruir, encoger, arrastrar, colocar en el aire, colocar o
     * romper sin actualizaciones). Lo que un oyente pase a {@code extra} se aplica en la misma operación: misma
     * {@code EditSession} y mismo //undo, mismo modo sin actualizaciones y las mismas comprobaciones de que el
     * jugador puede construir ahí (sin la de distancia). Sirve, por ejemplo, para replicar en simetría.
     */
    public static final Event<Collect> COLLECT = EventFactory.createArrayBacked(Collect.class,
            listeners -> (player, world, planned, extra) -> {
                for (Collect listener : listeners) {
                    listener.collect(player, world, planned, extra);
                }
            });

    private HandEditEvents() {
    }

    @FunctionalInterface
    public interface Collect {
        void collect(ServerPlayerEntity player, ServerWorld world, List<HandEdit> planned, Consumer<HandEdit> extra);
    }
}
