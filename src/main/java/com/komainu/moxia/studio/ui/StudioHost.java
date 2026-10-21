package com.komainu.moxia.studio.ui;

import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.recipe.ProceduralInstanceSummary;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.share.ShareCodePayload;
import com.komainu.moxia.share.ShareOfferPayload;
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

    /** El fantasma con gizmo que se está colocando, o vacío: el pie lo enseña en lugar de Construir. */
    default Optional<Ghost> ghost() {
        return Optional.empty();
    }

    /**
     * El Studio va a mandar algo que abre un fantasma (Construir con vista previa). Con {@code refresh} es la vista
     * previa en vivo, que sustituye al fantasma de ahora: el nuevo sale donde el jugador dejó este y con su giro.
     */
    default void expectGhost(boolean refresh) {
    }

    /**
     * Si el jugador está en creativo. En supervivencia la bienvenida guiada solo enseña, sin colocar nada (el servidor
     * vuelve a decidir igualmente lo que deja construir).
     */
    default boolean creative() {
        return true;
    }

    void close();

    @Override
    default void shareExport(String things) {
        send("komaedit share export " + things);
    }

    /** Lo que el jugador tiene en el portapapeles, o vacío. */
    default String clipboard() {
        return "";
    }

    /**
     * «Pegar código»: manda el código al servidor, que contesta con lo que trae ({@link #takeShareOffer}).
     *
     * @return si se pudo mandar (un servidor sin KomaEdit, o uno antiguo con un código largo, no lo recibe)
     */
    default boolean shareImport(String code) {
        return false;
    }

    /** Lo que trae un paquete que el servidor acaba de leer, una sola vez; el Studio lo enseña en su ventana. */
    default Optional<ShareOfferPayload> takeShareOffer() {
        return Optional.empty();
    }

    /** El resultado de un «Compartir» del Studio (ya en el portapapeles), una sola vez. */
    default Optional<ShareCodePayload> takeShareResult() {
        return Optional.empty();
    }

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
     * El fantasma que se está colocando, ya en texto: qué es, sus medidas y su giro, dónde está, sus opciones y lo que
     * hacen los botones del pie.
     *
     * @param title    qué es («Árbol roble»)
     * @param summary  giro y medidas («giro 90° · 7×12×7»), de más a menos importante
     * @param position dónde está y cuánto se ha movido («120, 64, -40 · (+3, 0, -2)»)
     * @param waiting confirmado: esperando al servidor
     * @param message por qué no se colocó la última vez, o vacío
     * @param fromStudio lo abrió Construir del Studio (la vista previa en vivo lo puede rehacer)
     */
    record Ghost(String title, String summary, String position, boolean keepExisting, boolean pasteAir,
                 boolean ground, boolean hasAir, boolean waiting, String message, Runnable place, Runnable copy,
                 Runnable cancel, Runnable toggleKeep, Runnable toggleAir, Runnable toggleGround,
                 boolean fromStudio) {
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
