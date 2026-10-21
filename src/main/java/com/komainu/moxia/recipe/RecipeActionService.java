package com.komainu.moxia.recipe;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.path.PathHologramSync;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.performance.RequestBudget;
import com.komainu.moxia.permission.KomaPermissions;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.extension.input.InputParseException;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class RecipeActionService {
    /** Cada acción lee o escribe archivos del jugador o calcula una figura: con freno por jugador. */
    private static final RequestBudget REQUESTS = new RequestBudget(10, 3.0);

    private RecipeActionService() {
    }

    public static void handle(ServerPlayerEntity player, RecipeActionPayload payload) {
        if (!REQUESTS.allow(player.getUuid(), System.nanoTime())) {
            return;
        }
        try {
            if (!KomaPermissions.canAccess(player)) {
                throw new IllegalArgumentException("No tienes permiso para usar KomaEdit");
            }
            requireAction(player, payload);
            switch (payload.action()) {
                case RecipeActionPayload.BUILD -> {
                    RecipeBuildService.submit(player, payload.recipe());
                    player.sendMessage(message("Trayectoria conservada · usa Limpiar cuando termines"), false);
                }
                case RecipeActionPayload.SAVE -> {
                    RecipeStore.save(player.getUuid(), payload.recipe());
                    player.sendMessage(message("Receta guardada: " + payload.recipe().name()), false);
                    sync(player);
                }
                case RecipeActionPayload.DELETE -> {
                    boolean deleted = RecipeStore.delete(player.getUuid(), payload.recipe().name());
                    player.sendMessage(message(deleted
                            ? "Receta eliminada: " + payload.recipe().name()
                            : "La receta ya no existía: " + payload.recipe().name()), false);
                    sync(player);
                }
                case RecipeActionPayload.SYNC -> sync(player);
                case RecipeActionPayload.UPDATE_INSTANCE -> RecipeBuildService.update(
                        player,
                        payload.recipe(),
                        payload.instanceId()
                );
                case RecipeActionPayload.DELETE_INSTANCE -> RecipeBuildService.deleteInstance(
                        player,
                        payload.instanceId()
                );
                case RecipeActionPayload.LOAD_INSTANCE -> {
                    ProceduralInstance instance = ProceduralInstanceStore.load(
                            player.getUuid(), payload.instanceId()
                    );
                    PathManager.replace(
                            player.getUuid(), instance.path().points(), instance.path().closed()
                    );
                    PathHologramSync.sync(player);
                    player.sendMessage(message(
                            "Instancia cargada: " + instance.recipe().name() + " · mueve sus nodos y actualiza"
                    ), false);
                }
                case RecipeActionPayload.EXPORT_PACKAGE -> {
                    var path = RecipePackageService.exportPackage(player.getUuid(), payload.recipe().name());
                    player.sendMessage(message("Paquete exportado: " + path.getFileName()), false);
                }
                case RecipeActionPayload.IMPORT_PACKAGE -> {
                    ProceduralRecipe imported = RecipePackageService.importPackage(
                            player.getUuid(),
                            payload.recipe().name()
                    );
                    player.sendMessage(message("Paquete importado: " + imported.name()), false);
                    sync(player);
                }
                default -> throw new IllegalArgumentException("Acción de receta desconocida");
            }
        } catch (IncompleteRegionException exception) {
            player.sendMessage(message("Marca al menos dos puntos con P o completa //pos1 y //pos2"), false);
        } catch (InputParseException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(message(exception.getMessage() == null ? "La receta no es válida" : exception.getMessage()), false);
        }
    }

    public static void forget(UUID player) {
        REQUESTS.forget(player);
    }

    public static void sync(ServerPlayerEntity player) {
        if (ServerPlayNetworking.canSend(player, RecipeCatalogPayload.ID)) {
            ServerPlayNetworking.send(player, new RecipeCatalogPayload(RecipeStore.list(player.getUuid())));
        }
        if (ServerPlayNetworking.canSend(player, InstanceCatalogPayload.ID)) {
            ServerPlayNetworking.send(player, new InstanceCatalogPayload(
                    ProceduralInstanceStore.list(player.getUuid())
            ));
        }
    }

    private static void requireAction(ServerPlayerEntity player, RecipeActionPayload payload) {
        switch (payload.action()) {
            case RecipeActionPayload.BUILD -> KomaPermissions.requireBuild(player, payload.recipe().shape().id());
            case RecipeActionPayload.UPDATE_INSTANCE -> {
                KomaPermissions.require(player, KomaPermissions.INSTANCE);
                KomaPermissions.requireBuild(player, payload.recipe().shape().id());
            }
            case RecipeActionPayload.DELETE_INSTANCE, RecipeActionPayload.LOAD_INSTANCE ->
                    KomaPermissions.require(player, KomaPermissions.INSTANCE);
            case RecipeActionPayload.SAVE, RecipeActionPayload.DELETE,
                 RecipeActionPayload.EXPORT_PACKAGE, RecipeActionPayload.IMPORT_PACKAGE ->
                    KomaPermissions.require(player, KomaPermissions.RECIPE);
            default -> {
            }
        }
    }

    private static Text message(String value) {
        return Feedback.prefix()
                .append(Text.literal(value).formatted(Formatting.GRAY));
    }
}
