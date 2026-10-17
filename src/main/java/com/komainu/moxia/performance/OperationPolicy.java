package com.komainu.moxia.performance;

import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.permission.KomaPermissions;
import net.minecraft.server.network.ServerPlayerEntity;

public final class OperationPolicy {
    private OperationPolicy() {
    }

    public static void validate(ServerPlayerEntity player, long blocks, int activeJobs) {
        if (blocks <= 0) {
            throw new IllegalArgumentException("The operation contains no blocks");
        }
        if (!KomaPermissions.can(player, KomaPermissions.LIMIT_UNRESTRICTED)
                && blocks > KomaEditConfig.settings().maxBlocksPerOperation()) {
            throw new IllegalArgumentException(
                    "Operation limit exceeded: " + blocks + "/"
                            + KomaEditConfig.settings().maxBlocksPerOperation()
            );
        }
        if (activeJobs >= KomaEditConfig.settings().maxConcurrentJobs()) {
            throw new IllegalStateException("The server KomaEdit job queue is full");
        }
    }
}
