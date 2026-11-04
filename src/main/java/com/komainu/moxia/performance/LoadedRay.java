package com.komainu.moxia.performance;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * El rayo de la mirada en el servidor sin cargar chunks. {@code player.raycast(192, …)} lee cada bloque con
 * {@code World.getBlockState}, que carga (o genera) en el acto el chunk que no esté cargado: mirando al horizonte con
 * un pincel de colocación en la mano (la guía de alineación lanza el rayo cada 4 ticks) o pintando (cada tick), el
 * servidor cargaba y volvía a soltar los chunks de más allá de la distancia de visión una y otra vez, y generaba los
 * que nunca se habían visitado, en el hilo del servidor. El rayo se corta ahora antes del primer chunk sin cargar: lo
 * que hay más allá el jugador tampoco lo ve.
 */
public final class LoadedRay {
    /** Margen antes del borde del chunk sin cargar, en bloques (el rayo de Minecraft se alarga 1e-7 de su largo). */
    private static final double MARGIN = 1.0E-3;

    private LoadedRay() {
    }

    /** Si el chunk de esas coordenadas de chunk está cargado. */
    @FunctionalInterface
    public interface Loaded {
        boolean test(int chunkX, int chunkZ);
    }

    /** Como {@code player.raycast(reach, 1, fluids)}, sin pasar de los chunks cargados. */
    public static HitResult cast(ServerPlayerEntity player, double reach, boolean fluids) {
        Vec3d eyes = player.getCameraPosVec(1.0F);
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d end = eyes.add(look.x * reach, look.y * reach, look.z * reach);
        ServerWorld world = player.getServerWorld();
        Vec3d clipped = clip(eyes, end, (chunkX, chunkZ) -> world.getChunkManager().isChunkLoaded(chunkX, chunkZ));
        return world.raycast(new RaycastContext(eyes, clipped, RaycastContext.ShapeType.OUTLINE,
                fluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE, player));
    }

    /**
     * Hasta dónde llega el rayo de {@code start} a {@code end} sin entrar en un chunk sin cargar: {@code end} si todos
     * los que cruza lo están, el propio {@code start} si ni siquiera lo está el primero. Recorre las columnas de chunk
     * que corta el rayo (como el recorrido por bloques de Minecraft, pero de 16 en 16 y sin la altura) y, si pasa justo
     * por una esquina, mira las dos de al lado.
     */
    public static Vec3d clip(Vec3d start, Vec3d end, Loaded loaded) {
        int chunkX = Math.floorDiv((int) Math.floor(start.x), 16);
        int chunkZ = Math.floorDiv((int) Math.floor(start.z), 16);
        if (!loaded.test(chunkX, chunkZ)) {
            return start;
        }
        double dx = end.x - start.x;
        double dz = end.z - start.z;
        int lastX = Math.floorDiv((int) Math.floor(end.x), 16);
        int lastZ = Math.floorDiv((int) Math.floor(end.z), 16);
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 16.0 / Math.abs(dx);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 16.0 / Math.abs(dz);
        double nextX = stepX == 0 ? Double.POSITIVE_INFINITY
                : ((stepX > 0 ? chunkX + 1 : chunkX) * 16.0 - start.x) / dx;
        double nextZ = stepZ == 0 ? Double.POSITIVE_INFINITY
                : ((stepZ > 0 ? chunkZ + 1 : chunkZ) * 16.0 - start.z) / dz;
        double length = start.distanceTo(end);
        double margin = length == 0.0 ? 0.0 : MARGIN / length;
        int guard = 0;
        while ((chunkX != lastX || chunkZ != lastZ) && guard++ < 1_000_000) {
            double t = Math.min(nextX, nextZ);
            if (t > 1.0) {
                break;
            }
            boolean crossX = nextX <= nextZ;
            boolean crossZ = nextZ <= nextX;
            if (crossX && crossZ && (!loaded.test(chunkX + stepX, chunkZ) || !loaded.test(chunkX, chunkZ + stepZ))) {
                return at(start, end, t - margin);
            }
            if (crossX) {
                chunkX += stepX;
                nextX += deltaX;
            }
            if (crossZ) {
                chunkZ += stepZ;
                nextZ += deltaZ;
            }
            if (!loaded.test(chunkX, chunkZ)) {
                return at(start, end, t - margin);
            }
        }
        return end;
    }

    private static Vec3d at(Vec3d start, Vec3d end, double t) {
        double clamped = Math.clamp(t, 0.0, 1.0);
        return new Vec3d(start.x + (end.x - start.x) * clamped, start.y + (end.y - start.y) * clamped,
                start.z + (end.z - start.z) * clamped);
    }
}
