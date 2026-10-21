package com.komainu.moxia.permission;

import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.worldedit.WorldEditService;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Permisos de KomaEdit. Los operadores lo pueden todo; los demás jugadores se consultan a través del actor de
 * WorldEdit (así valen LuckPerms y cualquier gestor que WorldEdit entienda). Los nodos finos y los paquetes están
 * en {@link PermissionNodes}.
 */
public final class KomaPermissions {
    public static final String USE = "komaedit.use";
    /** Nombre antiguo de {@link #USE}. */
    public static final String LEGACY_USE = "moxia.use";
    public static final String PATH = "komaedit.path";
    public static final String BUILD = "komaedit.build";
    public static final String INSTANCE = "komaedit.instance";
    public static final String RECIPE = "komaedit.recipe";
    public static final String SELECT = "komaedit.select";
    public static final String PAINT = "komaedit.paint";
    public static final String CLIPBOARD = "komaedit.clipboard";
    public static final String HISTORY = "komaedit.history";
    public static final String PROJECT_BACKUP = "komaedit.project.backup";
    public static final String LIMIT_UNRESTRICTED = "komaedit.limit.unrestricted";
    public static final String ADMIN_RELOAD = "komaedit.admin.reload";
    /** Copiar un sello de serie a {@code config/komaedit/heightmaps} para retocarlo. */
    public static final String ADMIN_STAMP_EXPORT = "komaedit.admin.stamps.export";
    /** {@code /komaedit jobs}: ver, pausar y cancelar los trabajos de todos los jugadores. */
    public static final String ADMIN_JOBS = "komaedit.admin.jobs";
    /** Imágenes por URL en {@code /komaedit image} cuando {@code urlsRequireAdmin} está activo. */
    public static final String ADMIN_IMAGE_URL = "komaedit.admin.image.url";
    /** {@code /komaedit rebuild}: la reconstrucción animada. */
    public static final String REBUILD = BUILD + ".rebuild";

    private KomaPermissions() {
    }

    public static boolean canAccess(ServerPlayerEntity player) {
        return operator(player) || permission(player, USE) || permission(player, LEGACY_USE)
                || PermissionNodes.anyPack(node -> permission(player, node));
    }

    /** ¿Puede usar la función del nodo? Ver {@link PermissionNodes#allows}. */
    public static boolean can(ServerPlayerEntity player, String permission) {
        if (operator(player)) {
            return true;
        }
        if (permission.equals(USE)) {
            return canAccess(player);
        }
        return PermissionNodes.allows(permission, node -> permission(player, node))
                || KomaEditConfig.settings().rootPermissionGrantsAll() && canAccess(player);
    }

    /** ¿Puede usar alguna función de la familia? Sirve para mostrar u ocultar una orden con varios modos. */
    public static boolean canAny(ServerPlayerEntity player, String prefix) {
        return operator(player)
                || PermissionNodes.allowsAny(prefix, node -> permission(player, node))
                || KomaEditConfig.settings().rootPermissionGrantsAll() && canAccess(player);
    }

    public static boolean canBuild(ServerPlayerEntity player, String shapeId) {
        return can(player, BUILD + "." + shapeId);
    }

    public static void require(ServerPlayerEntity player, String permission) {
        if (!can(player, permission)) {
            throw new IllegalArgumentException("Te falta el permiso " + permission);
        }
    }

    public static void requireBuild(ServerPlayerEntity player, String shapeId) {
        if (!canBuild(player, shapeId)) {
            throw new IllegalArgumentException("Te falta el permiso " + BUILD + "." + shapeId);
        }
    }

    private static boolean permission(ServerPlayerEntity player, String node) {
        try {
            return WorldEditService.context(player).actor().hasPermission(node);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean operator(ServerPlayerEntity player) {
        return player.getCommandSource().hasPermissionLevel(2);
    }
}
