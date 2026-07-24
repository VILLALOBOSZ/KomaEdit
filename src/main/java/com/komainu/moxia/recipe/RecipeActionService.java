package com.komainu.moxia.recipe;

import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.extension.input.InputParseException;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class RecipeActionService {
    private RecipeActionService() {
    }

    public static void handle(ServerPlayerEntity player, RecipeActionPayload payload) {
        try {
            if (!canUse(player)) {
                throw new IllegalArgumentException("No tienes permiso para usar KomaEdit");
            }
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

    private static boolean canUse(ServerPlayerEntity player) {
        try {
            return player.getCommandSource().hasPermissionLevel(2) || WorldEditService.hasUsePermission(player);
        } catch (RuntimeException exception) {
            return player.getCommandSource().hasPermissionLevel(2);
        }
    }

    private static Text message(String value) {
        return Text.literal("KomaEdit · ").formatted(Formatting.DARK_RED)
                .append(Text.literal(value).formatted(Formatting.GRAY));
    }
}
