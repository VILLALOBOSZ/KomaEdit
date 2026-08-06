package com.komainu.moxia.permission;

import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.worldedit.WorldEditService;
import net.minecraft.server.network.ServerPlayerEntity;

public final class KomaPermissions {
    public static final String USE = "komaedit.use";
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

    private KomaPermissions() {
    }

    public static boolean canAccess(ServerPlayerEntity player) {
        return operator(player) || permission(player, USE) || permission(player, "moxia.use");
    }

    public static boolean can(ServerPlayerEntity player, String permission) {
        return operator(player)
                || permission(player, permission)
                || KomaEditConfig.settings().rootPermissionGrantsAll() && canAccess(player);
    }

    public static boolean canBuild(ServerPlayerEntity player, String shapeId) {
        return can(player, BUILD) || permission(player, BUILD + "." + shapeId);
    }

    public static void require(ServerPlayerEntity player, String permission) {
        if (!can(player, permission)) {
            throw new IllegalArgumentException("Missing permission: " + permission);
        }
    }

    public static void requireBuild(ServerPlayerEntity player, String shapeId) {
        if (!canBuild(player, shapeId)) {
            throw new IllegalArgumentException("Missing permission: " + BUILD + "." + shapeId);
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
