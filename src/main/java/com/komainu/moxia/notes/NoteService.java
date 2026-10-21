package com.komainu.moxia.notes;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.config.UtilitiesConfig;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.studio.net.StudioAim;
import com.komainu.moxia.views.ViewService;
import com.komainu.moxia.views.ViewTeleport;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Las notas en el servidor: las guarda ({@link NoteBook}), comprueba permisos y límites y manda a cada cliente con
 * KomaEdit las de su dimensión que puede ver. Todo pasa en el hilo del servidor.
 */
public final class NoteService {
    /** Hasta dónde se busca el bloque que se mira al poner una nota. */
    public static final double REACH = 128.0;
    private static volatile NoteBook book;

    private NoteService() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(NotePayloads.NoteSyncPayload.ID, NotePayloads.NoteSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NotePayloads.NoteRemovePayload.ID,
                NotePayloads.NoteRemovePayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                sync(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer));
        ServerLifecycleEvents.SERVER_STARTED.register(NoteService::open);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> book = null);
    }

    private static void open(MinecraftServer server) {
        NoteBook notes = new NoteBook(server.getSavePath(WorldSavePath.ROOT).resolve("komaedit").resolve("notes.json"));
        try {
            int skipped = notes.load();
            if (skipped > 0) {
                KomaEditMod.LOGGER.warn("Skipped {} unreadable KomaEdit notes", skipped);
            }
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not read KomaEdit notes; starting empty", exception);
        }
        book = notes;
    }

    private static NoteBook book() {
        NoteBook notes = book;
        if (notes == null) {
            throw new IllegalArgumentException("Las notas aún no están listas");
        }
        return notes;
    }

    /** ¿Puede usar las notas de alguna forma (poner, ver las del equipo)? */
    public static boolean canUse(ServerPlayerEntity player) {
        return KomaPermissions.canAny(player, "komaedit.notes");
    }

    public static NoteBook.Actor actor(ServerPlayerEntity player) {
        return new NoteBook.Actor(player.getUuid(), player.getGameProfile().getName(),
                KomaPermissions.can(player, NotePermissions.TEAM),
                KomaPermissions.can(player, NotePermissions.SHARE),
                KomaPermissions.can(player, NotePermissions.MANAGE));
    }

    // ------------------------------------------------------------------ cambios

    /** Pone una nota en el bloque que mira el jugador (o, con {@code -p}, donde está). */
    public static WorldNote add(ServerPlayerEntity player, NoteRequest request) {
        if (!KomaPermissions.can(player, NotePermissions.PRIVATE) && !request.team()) {
            throw new IllegalArgumentException("Te falta el permiso " + NotePermissions.PRIVATE);
        }
        NoteBook.Anchor anchor;
        String dimension = ViewService.dimension(player.getWorld());
        if (request.point()) {
            anchor = new NoteBook.Anchor(dimension, player.getX(), player.getEyeY() - 0.6, player.getZ(), false,
                    WorldNote.UP);
        } else {
            // Con el Studio abierto, el bloque donde se hizo clic; si no, el que se mira.
            HitResult hit = StudioAim.target(player, REACH);
            if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
                throw new IllegalArgumentException("Mira un bloque para poner la nota (o usa -p para ponerla donde"
                        + " estás)");
            }
            BlockPos pos = block.getBlockPos();
            anchor = new NoteBook.Anchor(dimension, pos.getX(), pos.getY(), pos.getZ(), true,
                    block.getSide().getId());
        }
        WorldNote note = book().add(request, anchor, actor(player), UtilitiesConfig.settings().notes(),
                System.currentTimeMillis(), bound -> ThreadLocalRandom.current().nextInt(bound));
        broadcast(player.getServer(), note);
        return note;
    }

    public static WorldNote resolve(ServerPlayerEntity player, String id, boolean resolved) {
        requireUse(player);
        WorldNote note = book().resolve(id, actor(player), resolved, System.currentTimeMillis());
        broadcast(player.getServer(), note);
        return note;
    }

    public static WorldNote remove(ServerPlayerEntity player, String id) {
        requireUse(player);
        WorldNote note = book().remove(id, actor(player));
        removed(player.getServer(), note);
        return note;
    }

    public static List<WorldNote> clearResolved(ServerPlayerEntity player) {
        requireUse(player);
        List<WorldNote> notes = book().clearResolved(actor(player));
        notes.forEach(note -> removed(player.getServer(), note));
        return notes;
    }

    /**
     * Lleva al jugador junto a la nota, con las mismas reglas que el salto a una vista.
     *
     * @return {@code null} si fue bien; si no, por qué no
     */
    public static String jump(ServerPlayerEntity player, String id) {
        requireUse(player);
        WorldNote note = book().visible(id, actor(player));
        ViewTeleport.Target target = NoteJump.target(note, ViewService.dimension(player.getWorld()), player.getX(),
                player.getZ());
        return ViewService.jump(player, target);
    }

    /** Las notas que ve el jugador en todas las dimensiones, de la más reciente a la más antigua. */
    public static List<WorldNote> visible(ServerPlayerEntity player) {
        requireUse(player);
        return book().visibleTo(actor(player), null);
    }

    public static WorldNote find(ServerPlayerEntity player, String id) {
        requireUse(player);
        return book().visible(id, actor(player));
    }

    private static void requireUse(ServerPlayerEntity player) {
        if (!canUse(player)) {
            throw new IllegalArgumentException("Te falta el permiso " + NotePermissions.PRIVATE);
        }
    }

    // ------------------------------------------------------------------ red

    /** Todas las notas de su dimensión que ve, en páginas; la primera vacía lo que tenía. */
    public static void sync(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, NotePayloads.NoteSyncPayload.ID)) {
            return;
        }
        NoteBook notes = book;
        String dimension = ViewService.dimension(player.getWorld());
        List<WorldNote> visible = notes == null || !canUse(player) ? List.of()
                : notes.visibleTo(actor(player), dimension);
        int pages = Math.max(1, (visible.size() + NotePayloads.PAGE - 1) / NotePayloads.PAGE);
        for (int page = 0; page < pages; page++) {
            List<WorldNote> slice = visible.subList(Math.min(visible.size(), page * NotePayloads.PAGE),
                    Math.min(visible.size(), (page + 1) * NotePayloads.PAGE));
            ServerPlayNetworking.send(player, payload(player, page == 0, dimension, slice));
        }
    }

    public static void syncAll(MinecraftServer server) {
        if (server != null) {
            server.getPlayerManager().getPlayerList().forEach(NoteService::sync);
        }
    }

    private static NotePayloads.NoteSyncPayload payload(ServerPlayerEntity player, boolean reset, String dimension,
                                                        List<WorldNote> notes) {
        return new NotePayloads.NoteSyncPayload(reset, dimension, new ArrayList<>(notes),
                KomaPermissions.can(player, NotePermissions.PRIVATE),
                KomaPermissions.can(player, NotePermissions.SHARE) && UtilitiesConfig.settings().notes().allowTeam(),
                KomaPermissions.can(player, NotePermissions.TEAM),
                KomaPermissions.can(player, NotePermissions.MANAGE));
    }

    /** Una nota nueva o cambiada: a quien la ve en su dimensión; a los demás, que la quiten si la tenían. */
    private static void broadcast(MinecraftServer server, WorldNote note) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!ServerPlayNetworking.canSend(player, NotePayloads.NoteSyncPayload.ID)
                    || !ViewService.dimension(player.getWorld()).equals(note.dimension())) {
                continue;
            }
            if (canUse(player) && note.visibleTo(player.getUuid(), KomaPermissions.can(player, NotePermissions.TEAM))) {
                ServerPlayNetworking.send(player, payload(player, false, note.dimension(), List.of(note)));
            } else {
                ServerPlayNetworking.send(player, new NotePayloads.NoteRemovePayload(note.id()));
            }
        }
    }

    private static void removed(MinecraftServer server, WorldNote note) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, NotePayloads.NoteRemovePayload.ID)) {
                ServerPlayNetworking.send(player, new NotePayloads.NoteRemovePayload(note.id()));
            }
        }
    }
}
