package com.komainu.moxia.performance;

import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.edit.EditLimits;
import com.komainu.moxia.permission.KomaPermissions;
import net.minecraft.server.network.ServerPlayerEntity;

public final class OperationPolicy {
    private OperationPolicy() {
    }

    /** Bloques que puede cambiar el jugador en una edición: {@code maxBlocksPerOperation}, o sin límite. */
    public static long limit(ServerPlayerEntity player) {
        return KomaPermissions.can(player, KomaPermissions.LIMIT_UNRESTRICTED)
                ? Long.MAX_VALUE : KomaEditConfig.settings().maxBlocksPerOperation();
    }

    /**
     * Comprueba antes de empezar que la edición cabe en el límite: si no, el aviso dice cuánto ocupa, cuál es el
     * límite y en cuántos trozos partirla.
     *
     * @param activeJobs ya no se usa: desde que hay cola, las ediciones de más esperan su turno en vez de rechazarse
     */
    public static void validate(ServerPlayerEntity player, long blocks, int activeJobs) {
        if (blocks <= 0) {
            throw new IllegalArgumentException("La operación no contiene bloques");
        }
        EditLimits.check(EditLimits.Kind.SERVER, blocks, limit(player)).ifPresent(excess -> {
            throw new IllegalArgumentException(excess.message("La edición", false));
        });
    }
}
