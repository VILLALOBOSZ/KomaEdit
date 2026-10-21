package com.komainu.moxia.studio.net;

import com.komainu.moxia.studio.live.LiveAnchors;
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
import java.util.function.Supplier;

/**
 * «Donde haces clic»: con el Studio abierto el ratón está suelto y el centro de la pantalla no es a donde apunta el
 * jugador, así que el cliente dice en qué bloque hizo clic ({@link StudioAimPayload}) antes de mandar la orden. Lo que
 * se coloca «donde miras» (árbol, roca, texto) usa ese bloque mientras el Studio siga abierto, también en la vista
 * previa en vivo; al cerrarlo se olvida y todo vuelve a ser el centro de la pantalla. La vista previa en vivo rehace
 * en el sitio de su Construir aunque el jugador ya mire a otra parte o haya cerrado el Studio ({@link #live}).
 *
 * <p>El servidor no se fía de más: el bloque tiene que estar en la dimensión del jugador y al alcance de su mirada.</p>
 */
public final class StudioAim {
    private static final Map<UUID, Spot> SPOTS = new ConcurrentHashMap<>();
    private static final LiveAnchors<Aim> LIVE = new LiveAnchors<>();

    private StudioAim() {
    }

    /** El bloque del clic, su cara y en qué dimensión. */
    public record Spot(RegistryKey<World> world, BlockPos pos, Direction side) {
    }

    /** Dónde cayó el Construir de una vista previa en vivo y si fue con un clic del Studio. */
    private record Aim(Spot spot, boolean clicked) {
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
        LIVE.clear();
    }

    /**
     * Ejecuta una petición de la vista previa en vivo: la que abre la sesión apunta dónde cae lo que se coloca «donde
     * miras»; las siguientes de la misma sesión caen ahí.
     */
    public static <T> T live(UUID player, int session, boolean first, Supplier<T> action) {
        return LIVE.run(player, session, first, action);
    }

    /** Al salir el jugador: se olvida el sitio de su vista previa en vivo. */
    public static void forgetLive(UUID player) {
        LIVE.forget(player);
    }

    /**
     * El bloque al que apunta el jugador: el de su vista previa en vivo si se está rehaciendo, donde hizo clic con el
     * Studio abierto o, si no, el que mira hasta {@code reach} bloques.
     */
    public static HitResult target(ServerPlayerEntity player, double reach) {
        Vec3d eyes = player.getEyePos();
        Spot pinned = pinned(player, reach, eyes);
        if (pinned != null) {
            return new BlockHitResult(Vec3d.ofCenter(pinned.pos()), pinned.side(), pinned.pos(), false);
        }
        HitResult result = aimed(player, reach, eyes);
        if (result instanceof BlockHitResult block && result.getType() == HitResult.Type.BLOCK) {
            LIVE.captured(player.getUuid(), new Aim(new Spot(player.getWorld().getRegistryKey(),
                    block.getBlockPos(), block.getSide()), clicked(player)));
        }
        return result;
    }

    /** El sitio fijado de la vista previa en vivo que se rehace, si sigue en su dimensión y a su alcance. */
    private static Spot pinned(ServerPlayerEntity player, double reach, Vec3d eyes) {
        Aim aim = LIVE.pinned(player.getUuid());
        if (aim == null) {
            return null;
        }
        Spot spot = aim.spot();
        return spot.world().equals(player.getWorld().getRegistryKey())
                && within(eyes.x, eyes.y, eyes.z, spot.pos().getX(), spot.pos().getY(), spot.pos().getZ(), reach)
                ? spot : null;
    }

    private static HitResult aimed(ServerPlayerEntity player, double reach, Vec3d eyes) {
        Spot spot = SPOTS.get(player.getUuid());
        if (spot != null && spot.world().equals(player.getWorld().getRegistryKey())
                && within(eyes.x, eyes.y, eyes.z, spot.pos().getX(), spot.pos().getY(), spot.pos().getZ(), reach)) {
            return new BlockHitResult(Vec3d.ofCenter(spot.pos()), spot.side(), spot.pos(), false);
        }
        return player.raycast(reach, 1.0F, false);
    }

    /**
     * Si el jugador apunta con un clic del Studio (para decirlo así en el chat y orientar el texto por la cara). Al
     * rehacer la vista previa en vivo, lo que fue su Construir.
     */
    public static boolean clicked(ServerPlayerEntity player) {
        Aim aim = LIVE.pinned(player.getUuid());
        if (aim != null) {
            return aim.clicked();
        }
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
