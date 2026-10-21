package com.komainu.moxia.views;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.config.UtilitiesConfig;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.feedback.FeedbackEventPayload;
import com.komainu.moxia.permission.KomaPermissions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las vistas en el servidor: el salto validado ({@link ViewTeleport}), las compartidas del mundo
 * ({@link SharedViews}) y su catálogo para los clientes con KomaEdit. Las vistas privadas viven en el cliente; el
 * servidor solo ve el sitio al que se quiere ir.
 */
public final class ViewService {
    private static final Map<UUID, Long> LAST_JUMP = new ConcurrentHashMap<>();
    private static volatile SharedViews shared;

    private ViewService() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ViewPayloads.ViewJumpPayload.ID, ViewPayloads.ViewJumpPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ViewPayloads.ViewSharePayload.ID,
                ViewPayloads.ViewSharePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ViewPayloads.ViewCatalogRequest.ID,
                ViewPayloads.ViewCatalogRequest.CODEC);
        PayloadTypeRegistry.playS2C().register(ViewPayloads.ViewCatalogPayload.ID,
                ViewPayloads.ViewCatalogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ViewPayloads.ViewResultPayload.ID,
                ViewPayloads.ViewResultPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ViewPayloads.ViewJumpPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    String problem = jump(context.player(), payload.target());
                    reply(context.player(), true, problem);
                }));
        ServerPlayNetworking.registerGlobalReceiver(ViewPayloads.ViewSharePayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    String message;
                    boolean ok;
                    try {
                        message = share(context.player(), payload.name(), payload.target());
                        ok = true;
                    } catch (IllegalArgumentException exception) {
                        message = exception.getMessage();
                        ok = false;
                    }
                    ServerPlayNetworking.send(context.player(), new ViewPayloads.ViewResultPayload(false, ok,
                            message));
                }));
        ServerPlayNetworking.registerGlobalReceiver(ViewPayloads.ViewCatalogRequest.ID, (payload, context) ->
                context.server().execute(() -> sendCatalog(context.player())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendCatalog(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_JUMP.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STARTED.register(ViewService::open);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            shared = null;
            LAST_JUMP.clear();
        });
    }

    private static void open(MinecraftServer server) {
        SharedViews views = new SharedViews(server.getSavePath(WorldSavePath.ROOT).resolve("komaedit")
                .resolve("views.json"));
        try {
            int skipped = views.load();
            if (skipped > 0) {
                KomaEditMod.LOGGER.warn("Skipped {} unreadable KomaEdit shared views", skipped);
            }
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not read KomaEdit shared views; starting empty", exception);
        }
        shared = views;
    }

    private static SharedViews store() {
        SharedViews views = shared;
        if (views == null) {
            throw new IllegalArgumentException("Las vistas compartidas aún no están listas");
        }
        return views;
    }

    // ------------------------------------------------------------------ saltar

    /**
     * Lleva al jugador a {@code target} si se puede.
     *
     * @return {@code null} si fue bien; si no, por qué no
     */
    public static String jump(ServerPlayerEntity player, ViewTeleport.Target target) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return "El servidor no está listo";
        }
        Identifier id = Identifier.tryParse(target.dimension());
        ServerWorld world = id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
        ViewTeleport.Destination destination = world == null
                ? new ViewTeleport.Destination(false, 0, 0, (x, z) -> false)
                : new ViewTeleport.Destination(true, world.getBottomY(), world.getTopY(),
                world.getWorldBorder()::contains);
        long now = System.currentTimeMillis();
        ViewTeleport.Verdict verdict = ViewTeleport.check(target, traveler(player), destination,
                UtilitiesConfig.settings().teleport(), now, LAST_JUMP.getOrDefault(player.getUuid(), Long.MIN_VALUE));
        if (!verdict.allowed()) {
            return verdict.reason();
        }
        LAST_JUMP.put(player.getUuid(), now);
        ViewTeleport.Target clean = verdict.target();
        player.stopRiding();
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0.0F;
        // En creativo, mejor volando: la vista suele estar en el aire.
        if (player.getAbilities().allowFlying && !player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
        }
        if (!player.teleport(world, clean.x(), clean.y(), clean.z(), Set.of(), clean.yaw(), clean.pitch())) {
            return "Minecraft no dejó hacer el salto";
        }
        return null;
    }

    static ViewTeleport.Traveler traveler(ServerPlayerEntity player) {
        GameMode mode = player.interactionManager.getGameMode();
        return new ViewTeleport.Traveler(dimension(player.getWorld()), player.getX(), player.getY(), player.getZ(),
                mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR,
                KomaPermissions.can(player, ViewPermissions.TELEPORT),
                KomaPermissions.can(player, ViewPermissions.DIMENSION),
                KomaPermissions.can(player, ViewPermissions.ANY_MODE));
    }

    public static String dimension(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    /** Lo que ve el cliente con el mod en su barra de acción; en el chat si no lo tiene. */
    private static void reply(ServerPlayerEntity player, boolean jump, String problem) {
        if (ServerPlayNetworking.canSend(player, ViewPayloads.ViewResultPayload.ID)) {
            ServerPlayNetworking.send(player, new ViewPayloads.ViewResultPayload(jump, problem == null,
                    problem == null ? "" : problem));
        } else if (problem != null) {
            Feedback.error(player, problem);
        }
        if (problem != null) {
            Feedback.sound(player, FeedbackEventPayload.Kind.FAIL);
        }
    }

    // ------------------------------------------------------------------ compartir

    /** Comparte con el equipo la vista de {@code target} con ese nombre. Devuelve el mensaje para el jugador. */
    public static String share(ServerPlayerEntity player, String name, ViewTeleport.Target target) {
        if (!KomaPermissions.can(player, ViewPermissions.SHARE)) {
            throw new IllegalArgumentException("Te falta el permiso " + ViewPermissions.SHARE);
        }
        String clean = ViewNames.validate(name);
        MinecraftServer server = player.getServer();
        Identifier id = Identifier.tryParse(target.dimension());
        ServerWorld world = server == null || id == null ? null
                : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
        if (world == null) {
            throw new IllegalArgumentException("La dimensión " + target.dimension() + " no existe en este servidor");
        }
        if (!Double.isFinite(target.x()) || !Double.isFinite(target.y()) || !Double.isFinite(target.z())
                || target.y() < world.getBottomY() - ViewTeleport.FLOOR_MARGIN
                || target.y() > world.getTopY() + ViewTeleport.SKY_MARGIN
                || !world.getWorldBorder().contains(target.x(), target.z())) {
            throw new IllegalArgumentException("La vista está fuera del mundo");
        }
        boolean manage = KomaPermissions.can(player, ViewPermissions.MANAGE);
        SavedView view = new SavedView(clean, target.dimension(), target.x(), target.y(), target.z(), target.yaw(),
                target.pitch(), false, 0L, "", null).sharedBy(player.getGameProfile().getName(), player.getUuid(),
                System.currentTimeMillis());
        Optional<SavedView> replaced = store().share(view, UtilitiesConfig.settings().views(), manage, manage);
        sendCatalogToAll(server);
        return replaced.isPresent() ? "Vista compartida «" + clean + "» actualizada"
                : "Vista «" + clean + "» compartida con el equipo";
    }

    /** Comparte la vista de donde está el jugador ahora. */
    public static String shareHere(ServerPlayerEntity player, String name) {
        return share(player, name, here(player));
    }

    public static ViewTeleport.Target here(ServerPlayerEntity player) {
        return new ViewTeleport.Target(dimension(player.getWorld()), player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch());
    }

    public static SavedView remove(ServerPlayerEntity player, String name) {
        SavedView removed = store().remove(name, player.getUuid(), KomaPermissions.can(player,
                ViewPermissions.MANAGE));
        sendCatalogToAll(player.getServer());
        return removed;
    }

    /** Las compartidas que ve el jugador (todas, si tiene el permiso; si no, ninguna). */
    public static List<SavedView> visible(ServerPlayerEntity player) {
        if (!KomaPermissions.can(player, ViewPermissions.SEE_SHARED) || shared == null) {
            return List.of();
        }
        return store().all();
    }

    public static Optional<SavedView> find(ServerPlayerEntity player, String name) {
        return visible(player).stream().filter(view -> ViewNames.key(view.name()).equals(ViewNames.key(name)))
                .findFirst();
    }

    // ------------------------------------------------------------------ catálogo

    public static void sendCatalog(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, ViewPayloads.ViewCatalogPayload.ID)) {
            return;
        }
        List<SavedView> views = visible(player);
        if (views.size() > ViewPayloads.MAX_CATALOG) {
            views = views.subList(0, ViewPayloads.MAX_CATALOG);
        }
        ServerPlayNetworking.send(player, new ViewPayloads.ViewCatalogPayload(views,
                KomaPermissions.can(player, ViewPermissions.TELEPORT),
                KomaPermissions.can(player, ViewPermissions.DIMENSION),
                KomaPermissions.can(player, ViewPermissions.SHARE)
                        && UtilitiesConfig.settings().views().allowSharing(),
                KomaPermissions.can(player, ViewPermissions.MANAGE)));
    }

    public static void sendCatalogToAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendCatalog(player);
        }
    }
}
