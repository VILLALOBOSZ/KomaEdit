package com.komainu.moxia.studio.ui;

import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.recipe.ProceduralInstanceSummary;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.studio.net.JobStatusPayload;
import com.komainu.moxia.studio.tools.ui.ToolsPanel;

import java.util.List;
import java.util.Optional;

/**
 * Todo lo que la vista común del Studio necesita del juego: mandar órdenes y paquetes, la trayectoria, el holograma,
 * lo que está en marcha, las recetas e instancias, los planos y los bloques. En el juego lo da la pantalla del
 * cliente; en las pruebas y en las maquetas, un Studio de mentira.
 */
public interface StudioHost extends ToolsPanel.Host, ColorStage.Host {
    /** Manda una acción de receta ({@code RecipeActionPayload}: construir, guardar, borrar…). */
    void recipeAction(int action, ProceduralRecipe recipe, String instanceId);

    List<ProceduralRecipe> recipes();

    List<ProceduralInstanceSummary> instances();

    /** Los puntos de la trayectoria, como {x, y, z}. */
    List<int[]> pathPoints();

    boolean pathClosed();

    /** Bloques del último holograma, o -1 si aún no hay. */
    int previewBlocks();

    /** Si el holograma ya terminó de calcularse. */
    boolean previewSettled();

    boolean hologramHidden();

    void setHologramHidden(boolean hidden);

    /** Lo que cuenta el holograma («≈ 1,2 k bloques · 3 tramos»). */
    String hologramStatus();

    /** Lo que el jugador tiene en marcha, o vacío. */
    Optional<JobStatusPayload> job();

    /** Deshace o rehace en el mundo (el {@code //undo} de WorldEdit). */
    void worldUndo();

    void worldRedo();

    /** El historial de deshacer del mundo (lo último primero), o vacío si el servidor no lo cuenta. */
    WorldHistory worldHistory();

    /** La biblioteca de planos tal como la tiene el cliente. */
    Blueprints blueprints();

    /** Todos los bloques del registro (ids completos). */
    List<String> allBlocks();

    /** Los bloques de la barra rápida del jugador, en orden (ids completos; vacío lo que no es bloque). */
    List<String> hotbarBlocks();

    /** Abre el chat con la orden escrita para completarla. */
    void suggest(String command);

    void close();

    /** La biblioteca de planos del cliente: catálogo, permisos y miniaturas. */
    interface Blueprints {
        boolean received();

        List<BlueprintSummary> list();

        boolean canSave();

        boolean canPlace();

        boolean canStamp();

        boolean canShare();

        void refresh(boolean force);

        /** La miniatura de una huella, o {@code null} si aún no ha llegado. */
        StudioImage thumbnail(String hash);

        /** Pide las miniaturas que faltan (solo las que se ven). */
        void request(List<String> hashes);
    }

    /**
     * El historial de deshacer de WorldEdit visto desde el Studio.
     *
     * @param entries lo último primero
     * @param undone  cuántas de las primeras están deshechas (se pueden rehacer)
     */
    record WorldHistory(List<Entry> entries, int undone) {
        public static final WorldHistory EMPTY = new WorldHistory(List.of(), 0);

        public WorldHistory {
            entries = List.copyOf(entries);
            undone = Math.clamp(undone, 0, entries.size());
        }

        /** Una operación: qué fue y cuántos bloques cambió. */
        public record Entry(String label, long blocks) {
        }
    }
}
