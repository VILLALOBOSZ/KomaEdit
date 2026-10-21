package com.komainu.moxia.client.gui.studio;

import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.client.blueprint.ClientBlueprintCatalog;
import com.komainu.moxia.client.preview.PathHologramRenderer;
import com.komainu.moxia.client.preview.ProceduralPreviewRenderer;
import com.komainu.moxia.client.recipe.ClientInstanceCatalog;
import com.komainu.moxia.client.recipe.ClientRecipeCatalog;
import com.komainu.moxia.editor.EditorActionPayload;
import com.komainu.moxia.recipe.ProceduralInstanceSummary;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.recipe.RecipeActionPayload;
import com.komainu.moxia.studio.BlockNames;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.net.JobStatusPayload;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import com.komainu.moxia.studio.ui.StudioHost;
import com.komainu.moxia.studio.ui.StudioImage;
import com.komainu.moxia.studio.ui.StudioPalette;
import com.komainu.moxia.studio.ui.StudioTheme;
import com.komainu.moxia.studio.ui.StudioView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * El Studio común ({@link StudioView}) conectado con el juego: traduce con los archivos de idioma, manda órdenes y
 * paquetes, y cuenta la trayectoria, el holograma, lo que está en marcha, las recetas, los planos y los bloques.
 */
public final class ClientStudioHost implements StudioHost {
    private static final Path PREFERENCES_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("komaedit").resolve("studio.json");
    private static StudioPreferences preferences;
    private static List<String> blocks;
    private static final Map<String, String> NAMES = new HashMap<>();
    private static final Map<String, StudioImage> THUMBNAILS = new HashMap<>();

    private final MinecraftClient client;
    private final StudioView view;
    private final Runnable close;

    public ClientStudioHost(MinecraftClient client, StudioView view, Runnable close) {
        this.client = client;
        this.view = view;
        this.close = close;
    }

    public static StudioPreferences loadPreferences() {
        if (preferences == null) {
            preferences = StudioPreferences.load(PREFERENCES_FILE);
            StudioTheme.use(StudioPalette.byId(preferences.theme()));
        }
        return preferences;
    }

    /** Pone el tema de colores del Studio y lo guarda con las preferencias del jugador (ajustes de KomaEdit). */
    public static void setTheme(StudioPalette palette) {
        loadPreferences().setTheme(palette.id());
        StudioTheme.use(palette);
        try {
            loadPreferences().save(PREFERENCES_FILE);
        } catch (IOException ignored) {
            // Como el resto de preferencias: si no se puede guardar, sigue puesto hasta cerrar el juego.
        }
    }

    @Override
    public String tr(String key, Object... arguments) {
        return Text.translatable(key, arguments).getString();
    }

    @Override
    public void send(String command) {
        if (client != null && client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatCommand(command);
        }
    }

    @Override
    public void pickBlock(String title, String current, Consumer<String> onPick) {
        view.picker().open(title, current, onPick);
    }

    @Override
    public void status(String text) {
        // El pie lo enseña la vista; aquí no hace falta nada más.
    }

    @Override
    public void copy(String text) {
        if (client != null) {
            client.keyboard.setClipboard(text);
        }
    }

    @Override
    public void goToPath() {
        // La vista cambia de etapa por su cuenta.
    }

    @Override
    public boolean shift() {
        return Screen.hasShiftDown();
    }

    @Override
    public long now() {
        return System.currentTimeMillis();
    }

    @Override
    public ToolContext context() {
        return ToolClientState.context();
    }

    @Override
    public ToolsInfo info() {
        return ToolClientState.info(client);
    }

    @Override
    public void requestState() {
        ToolClientState.request();
    }

    @Override
    public StudioPreferences preferences() {
        return loadPreferences();
    }

    @Override
    public void savePreferences() {
        try {
            loadPreferences().save(PREFERENCES_FILE);
        } catch (IOException ignored) {
            // Favoritas, recientes y ajustes son una comodidad: si no se pueden guardar, siguen en memoria.
        }
    }

    /**
     * El nombre traducido del bloque (o del objeto); si el juego no tiene traducción, como pasa con bloques de algunos
     * mods, uno legible sacado del id ({@link BlockNames}), nunca la clave de traducción ni el id crudo.
     */
    @Override
    public String blockName(String id) {
        return NAMES.computeIfAbsent(id, key -> {
            Identifier identifier = Identifier.tryParse(key.contains(":") ? key : "minecraft:" + key);
            if (identifier == null) {
                return BlockNames.readable(key);
            }
            Language language = Language.getInstance();
            String translationKey = Registries.BLOCK.containsId(identifier)
                    ? Registries.BLOCK.get(identifier).getTranslationKey()
                    : Registries.ITEM.containsId(identifier) ? Registries.ITEM.get(identifier).getTranslationKey()
                    : null;
            if (translationKey != null && language.hasTranslation(translationKey)) {
                return language.get(translationKey);
            }
            return BlockNames.name(identifier.toString(),
                    candidate -> language.hasTranslation(candidate) ? language.get(candidate) : null);
        });
    }

    /** Al cambiar de idioma los nombres se vuelven a traducir. */
    public static void clearNames() {
        NAMES.clear();
    }

    @Override
    public String lookedAtBlock() {
        if (client == null || client.world == null) {
            return null;
        }
        HitResult target = client.crosshairTarget;
        if (target instanceof BlockHitResult hit && target.getType() == HitResult.Type.BLOCK) {
            Block block = client.world.getBlockState(hit.getBlockPos()).getBlock();
            if (!block.getDefaultState().isAir()) {
                return Registries.BLOCK.getId(block).toString();
            }
        }
        return null;
    }

    @Override
    public void recipeAction(int action, ProceduralRecipe recipe, String instanceId) {
        ClientPlayNetworking.send(new RecipeActionPayload(action, recipe, instanceId));
    }

    @Override
    public List<ProceduralRecipe> recipes() {
        return ClientRecipeCatalog.recipes();
    }

    @Override
    public List<ProceduralInstanceSummary> instances() {
        return ClientInstanceCatalog.instances();
    }

    @Override
    public List<int[]> pathPoints() {
        List<int[]> points = new ArrayList<>();
        for (BlockPos position : PathHologramRenderer.points()) {
            points.add(new int[]{position.getX(), position.getY(), position.getZ()});
        }
        return points;
    }

    @Override
    public boolean pathClosed() {
        return PathHologramRenderer.closed();
    }

    @Override
    public int previewBlocks() {
        return ProceduralPreviewRenderer.blockCount();
    }

    @Override
    public boolean previewSettled() {
        return ProceduralPreviewRenderer.settled();
    }

    @Override
    public boolean hologramHidden() {
        return ProceduralPreviewRenderer.hidden();
    }

    @Override
    public void setHologramHidden(boolean hidden) {
        ProceduralPreviewRenderer.setHidden(hidden);
    }

    @Override
    public String hologramStatus() {
        return ProceduralPreviewRenderer.status();
    }

    @Override
    public Optional<Ghost> ghost() {
        return com.komainu.moxia.client.gizmo.GhostClient.studioFooter();
    }

    @Override
    public void expectGhost(boolean refresh) {
        com.komainu.moxia.client.gizmo.GhostClient.expectStudio(refresh);
    }

    @Override
    public String heldTool() {
        return client == null || client.player == null ? null
                : com.komainu.moxia.brush.BrushTool.id(client.player.getMainHandStack());
    }

    @Override
    public int[] clickedSpot() {
        return ClientStudioAim.last();
    }

    @Override
    public com.komainu.moxia.hotbar.ui.HotbarDeck hotbars() {
        return client == null || !com.komainu.moxia.client.hotbar.HotbarControls.supported() ? null
                : com.komainu.moxia.client.hotbar.ClientHotbars.deck(client);
    }

    @Override
    public boolean hotbarAutosave() {
        return com.komainu.moxia.client.hotbar.ClientHotbars.autosave();
    }

    @Override
    public void hotbar(com.komainu.moxia.hotbar.ui.HotbarAction action, String name) {
        com.komainu.moxia.client.hotbar.HotbarControls.studio(client, action, name, text -> view.status(text, now()));
    }

    @Override
    public boolean creative() {
        return client != null && client.interactionManager != null
                && client.interactionManager.getCurrentGameMode() == GameMode.CREATIVE;
    }

    @Override
    public Optional<JobStatusPayload> job() {
        return ToolClientState.job();
    }

    @Override
    public void worldUndo() {
        editor(EditorActionPayload.UNDO);
    }

    @Override
    public void worldRedo() {
        editor(EditorActionPayload.REDO);
    }

    private void editor(int action) {
        ProceduralRecipe draft = view.figure().draft();
        ClientPlayNetworking.send(new EditorActionPayload(action, -1, BlockPos.ORIGIN, draft.radius(),
                draft.pattern()));
    }

    @Override
    public WorldHistory worldHistory() {
        return ToolClientState.history(this);
    }

    @Override
    public Blueprints blueprints() {
        return BLUEPRINTS;
    }

    private static final Blueprints BLUEPRINTS = new Blueprints() {
        @Override
        public boolean received() {
            return ClientBlueprintCatalog.received();
        }

        @Override
        public List<BlueprintSummary> list() {
            return ClientBlueprintCatalog.blueprints();
        }

        @Override
        public boolean canSave() {
            return ClientBlueprintCatalog.canSave();
        }

        @Override
        public boolean canPlace() {
            return ClientBlueprintCatalog.canPlace();
        }

        @Override
        public boolean canStamp() {
            return ClientBlueprintCatalog.canStamp();
        }

        @Override
        public boolean canShare() {
            return ClientBlueprintCatalog.canShare();
        }

        @Override
        public void refresh(boolean force) {
            ClientBlueprintCatalog.refresh(force);
        }

        @Override
        public StudioImage thumbnail(String hash) {
            Identifier texture = ClientBlueprintCatalog.thumbnail(hash);
            if (texture == null) {
                THUMBNAILS.remove(hash);
                return null;
            }
            return THUMBNAILS.computeIfAbsent(hash, key -> StudioImage.external("blueprint/" + key, 128, 128,
                    texture, null));
        }

        @Override
        public void request(List<String> hashes) {
            ClientBlueprintCatalog.request(hashes);
        }
    };

    @Override
    public List<String> allBlocks() {
        if (blocks == null) {
            blocks = Registries.BLOCK.getIds().stream()
                    .filter(id -> !Registries.BLOCK.get(id).getDefaultState().isAir())
                    .map(Identifier::toString)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
        return blocks;
    }

    @Override
    public List<String> hotbarBlocks() {
        List<String> hotbar = new ArrayList<>();
        if (client == null || client.player == null) {
            return hotbar;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            hotbar.add(stack.getItem() instanceof BlockItem item
                    ? Registries.BLOCK.getId(item.getBlock()).toString() : "");
        }
        return hotbar;
    }

    @Override
    public void suggest(String command) {
        if (client != null) {
            client.setScreen(new ChatScreen(command));
        }
    }

    @Override
    public void close() {
        close.run();
    }
}
