package com.komainu.moxia.symmetry;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.util.eventbus.Subscribe;

/**
 * Engancha {@link SymmetryExtent} a las ediciones de un jugador con la simetría y su opción de WorldEdit activas. Va en
 * la etapa {@code BEFORE_HISTORY}, por fuera del historial: las copias entran en el mismo {@code //undo} que la edición
 * y pasan por la máscara global y el límite de bloques de WorldEdit. Deshacer escribe por debajo de esta etapa, así que
 * un {@code //undo} no se vuelve a reflejar.
 */
public final class SymmetryWorldEditHook {
    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        Actor actor = event.getActor();
        if (event.getStage() != EditSession.Stage.BEFORE_HISTORY || actor == null || !actor.isPlayer()
                || event.getWorld() == null) {
            return;
        }
        Extent extent = SymmetryService.worldEditExtent(actor.getUniqueId(), event.getWorld(), event.getExtent());
        if (extent != null) {
            event.setExtent(extent);
        }
    }
}
