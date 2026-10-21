package com.komainu.moxia.studio.net;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «Donde haces clic»: con el Studio abierto el ratón está suelto y el centro de la pantalla no es a donde apunta el
 * jugador, así que el cliente dice en qué bloque hizo clic ({@link StudioAimPayload}) antes de mandar la orden. Lo que
 * se coloca «donde miras» (árbol, roca, texto) usa ese bloque mientras el Studio siga abierto, también en la vista
 * previa en vivo; al cerrarlo se olvida y todo vuelve a ser el centro de la pantalla.
 *
 * <p>El servidor no se fía de más: el bloque tiene que estar en la dimensión del jugador y al alcance de su mirada.</p>
 */
public final class StudioAim {
    private static final Map<UUID, Spot> SPOTS = new ConcurrentHashMap<>();

    private StudioAim() {
    }

    /** El bloque del clic, su cara y en qué dimensión. */
    public record Spot(RegistryKey<World> world, BlockPos pos, Direction side) {
    }

    public static void receive(ServerPlayerEntity player, StudioAimPayload payload) {
        BlockPos pos = new BlockPos(payload.x(), payload.y(), payload.z());
        if (!payload.set() || !player.getWorld().isInBuildLimit(pos)) {
            SPOTS.remove(player.getUuid());
            return;
        }
        SPOTS.put(player.getUuid(), new Spot(player.getWorld().getRegistryKey(), pos, Direction.byId(payload.face())));
    }

    public static void forget(UUID player) {
        SPOTS.remove(player);
    }

    public static void clearAll() {
        SPOTS.clear();
    }

    /**
     * El bloque al que apunta el jugador: donde hizo clic con el Studio abierto o, si no, el que mira hasta
     * {@code reach} bloques.
     */
    public static HitResult target(ServerPlayerEntity player, double reach) {
        Spot spot = SPOTS.get(player.getUuid());
        if (spot != null && spot.world().equals(player.getWorld().getRegistryKey())) {
            Vec3d eyes = player.getEyePos();
            if (within(eyes.x, eyes.y, eyes.z, spot.pos().getX(), spot.pos().getY(), spot.pos().getZ(), reach)) {
                return new BlockHitResult(Vec3d.ofCenter(spot.pos()), spot.side(), spot.pos(), false);
            }
        }
        return player.raycast(reach, 1.0F, false);
    }

    /** Si el jugador apunta con un clic del Studio (para decirlo así en el chat). */
    public static boolean clicked(ServerPlayerEntity player) {
        Spot spot = SPOTS.get(player.getUuid());
        return spot != null && spot.world().equals(player.getWorld().getRegistryKey());
    }

    /** Si el centro del bloque queda a {@code reach} bloques (más uno, por la mitad del bloque) de los ojos. */
    static boolean within(double eyeX, double eyeY, double eyeZ, int x, int y, int z, double reach) {
        double dx = x + 0.5 - eyeX;
        double dy = y + 0.5 - eyeY;
        double dz = z + 0.5 - eyeZ;
        double limit = reach + 1.0;
        return dx * dx + dy * dy + dz * dz <= limit * limit;
    }
}
