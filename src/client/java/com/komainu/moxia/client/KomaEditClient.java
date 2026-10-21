package com.komainu.moxia.client;

import com.komainu.moxia.blueprint.BlueprintPayloads;
import com.komainu.moxia.client.blueprint.ClientBlueprintCatalog;
import com.komainu.moxia.client.brush.BrushToolControls;
import com.komainu.moxia.client.capability.CapabilityControls;
import com.komainu.moxia.client.hud.PathCaptureHud;
import com.komainu.moxia.client.gui.ProceduralStudioScreen;
import com.komainu.moxia.client.preview.PathHologramRenderer;
import com.komainu.moxia.client.preview.EditorOverlayRenderer;
import com.komainu.moxia.client.preview.ProceduralPreviewRenderer;
import com.komainu.moxia.client.recipe.ClientRecipeCatalog;
import com.komainu.moxia.client.recipe.ClientInstanceCatalog;
import com.komainu.moxia.client.world.WorldFeedbackClient;
import com.komainu.moxia.client.world.WorldSettingsScreen;
import com.komainu.moxia.path.PathHologramPayload;
import com.komainu.moxia.recipe.RecipeCatalogPayload;
import com.komainu.moxia.recipe.InstanceCatalogPayload;
import com.komainu.moxia.ui.QuickBuildPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class KomaEditClient implements ClientModInitializer {
    private static KeyBinding openEditor;
    private static KeyBinding togglePathCapture;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                PathHologramPayload.ID,
                (payload, context) -> {
                    PathHologramRenderer.update(payload);
                    PathCaptureHud.update(payload);
                }
        );
        ClientPlayNetworking.registerGlobalReceiver(
                QuickBuildPayload.ID,
                (payload, context) -> context.client().execute(() -> openEditor(context.client()))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                com.komainu.moxia.studio.net.StudioWelcomePayload.ID,
                (payload, context) -> context.client().execute(() -> ProceduralStudioScreen.openWelcome(context.client()))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                RecipeCatalogPayload.ID,
                (payload, context) -> context.client().execute(() -> ClientRecipeCatalog.update(payload.recipes()))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                InstanceCatalogPayload.ID,
                (payload, context) -> context.client().execute(() -> ClientInstanceCatalog.update(payload.instances()))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                BlueprintPayloads.Catalog.ID,
                (payload, context) -> context.client().execute(() -> ClientBlueprintCatalog.update(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                BlueprintPayloads.Thumbnail.ID,
                (payload, context) -> context.client().execute(() -> ClientBlueprintCatalog.receive(payload.chunk()))
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.currentScreen instanceof ProceduralStudioScreen) {
                client.setScreen(null);
            }
            ProceduralStudioScreen.resetSession();
            PathHologramRenderer.clear();
            EditorOverlayRenderer.clear();
            ProceduralPreviewRenderer.clear();
            PathCaptureHud.clear();
            ClientRecipeCatalog.clear();
            ClientInstanceCatalog.clear();
            client.execute(ClientBlueprintCatalog::clear);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(PathHologramRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(EditorOverlayRenderer::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ProceduralPreviewRenderer::render);
        PathCaptureHud.register();
        BrushToolControls.register();
        openEditor = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.komaedit.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.komaedit"
        ));
        togglePathCapture = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.komaedit.path_capture",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.komaedit"
        ));
        CapabilityControls.register();
        com.komainu.moxia.client.hotbar.HotbarControls.register();
        com.komainu.moxia.client.gui.studio.ToolClientState.register();
        com.komainu.moxia.client.share.ClientShare.register();
        WorldFeedbackClient.register();
        com.komainu.moxia.client.gizmo.GhostClient.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openEditor.wasPressed()) {
                openEditor(client);
            }
            while (togglePathCapture.wasPressed()) {
                if (client.player != null && client.player.networkHandler != null) {
                    client.player.networkHandler.sendChatCommand("komaedit path mode toggle");
                }
            }
            if (client.player != null && client.world != null) {
                ProceduralPreviewRenderer.request(
                        ProceduralStudioScreen.previewRecipe(),
                        PathHologramRenderer.points(),
                        PathHologramRenderer.closed()
                );
                ProceduralPreviewRenderer.tick(client);
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("kegui").executes(context -> {
                    openEditor(MinecraftClient.getInstance());
                    return 1;
                }).then(literal("ajustes").executes(context -> openWorldSettings()))
                        .then(literal("settings").executes(context -> openWorldSettings()))
        ));
    }

    /** Los ajustes de lo que se ve en el mundo; después del comando, para que el chat no la cierre. */
    private static int openWorldSettings() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen(new WorldSettingsScreen(null)));
        return 1;
    }

    private static void openEditor(MinecraftClient client) {
        if (client.currentScreen instanceof ProceduralStudioScreen) {
            client.setScreen(null);
        } else if (client.player != null) {
            client.setScreen(new ProceduralStudioScreen());
        }
    }
}
