package com.komainu.moxia.biome;

import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.SnapshotJobs;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.biome.BiomeType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;

/**
 * Pintar biomas: {@code /komaedit biome <bioma>} sobre la selección y el pincel {@code biome}. Las celdas se
 * calculan en un worker (leyendo el mundo solo si hay máscara) y se pintan con {@link BiomeEditService}.
 */
public final class BiomeService {
    private BiomeService() {
    }

    public static BiomeType parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta el bioma, por ejemplo cherry_grove o minecraft:snowy_plains");
        }
        String id = input.strip().toLowerCase(Locale.ROOT);
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        BiomeType biome = BiomeType.REGISTRY.get(id);
        if (biome == null) {
            throw new IllegalArgumentException("No existe el bioma " + id);
        }
        return biome;
    }

    /** Ids de los biomas del servidor (también los de datapacks y mods), para las sugerencias. */
    public static List<String> ids(MinecraftServer server) {
        return server.getRegistryManager().get(RegistryKeys.BIOME).getIds().stream()
                .map(Identifier::toString)
                .sorted()
                .toList();
    }

    /** Bioma del bloque, para el cuentagotas del pincel. */
    public static String biomeAt(ServerWorld world, BlockPos position) {
        return world.getBiome(position).getKey().map(key -> key.getValue().toString()).orElse("minecraft:plains");
    }

    /** {@code /komaedit biome}: la selección, con máscara y relleno vertical opcionales. */
    public static String paintSelection(ServerPlayerEntity player, BiomeType biome, boolean vertical, String maskInput)
            throws IncompleteRegionException {
        KomaPermissions.requireBuild(player, "biome");
        Region region = WorldEditService.selection(player).clone();
        ServerWorld world = player.getServerWorld();
        int minY = world.getBottomY();
        int maxY = world.getTopY() - 1;
        BlockVector3 low = region.getMinimumPoint();
        BlockVector3 high = region.getMaximumPoint();
        int[] box = {low.x(), Math.max(minY, low.y()), low.z(), high.x(), Math.min(maxY, high.y()), high.z()};
        String description = "Bioma " + display(biome);
        if (maskInput == null) {
            BiomeCells.Inside inside = region instanceof CuboidRegion ? null
                    : (x, y, z) -> region.contains(BlockVector3.at(x, y, z));
            SnapshotJobs.compute(player.getServer(), player.getUuid(), description,
                    () -> BiomeCells.cells(box, inside, vertical, minY, maxY, BiomeService::checkInterrupted),
                    (current, cells) -> queue(current, cells, biome, description));
        } else {
            RegionMasks.Bound mask = RegionMasks.parse(player, maskInput);
            SnapshotJobs.run(player, region, 0, description, snapshot -> {
                mask.bind(snapshot);
                return BiomeCells.cells(box, (x, y, z) -> snapshot.selected(x, y, z) && mask.test(x, y, z), vertical,
                        minY, maxY, BiomeService::checkInterrupted);
            }, (current, cells) -> queue(current, cells, biome, description));
        }
        return description + (vertical ? " con relleno vertical" : "") + (maskInput == null ? "" : " · máscara "
                + maskInput);
    }

    /** Un clic del pincel: una esfera alrededor del bloque mirado. */
    public static void paintSphere(ServerPlayerEntity player, BiomeType biome, BlockVector3 center, int radius,
                                   boolean vertical, String maskInput) {
        KomaPermissions.requireBuild(player, "brush.biome");
        if (BiomeEditService.running(player.getUuid())) {
            throw new IllegalStateException("Espera a que termine la pasada anterior");
        }
        ServerWorld world = player.getServerWorld();
        int minY = world.getBottomY();
        int maxY = world.getTopY() - 1;
        String description = "Pincel de bioma " + display(biome);
        if (maskInput == null) {
            SnapshotJobs.compute(player.getServer(), player.getUuid(), description,
                    () -> BiomeCells.sphere(center.x(), center.y(), center.z(), radius, vertical, minY, maxY, null),
                    (current, cells) -> queue(current, cells, biome, description));
            return;
        }
        RegionMasks.Bound mask = RegionMasks.parse(player, maskInput);
        Region box = new CuboidRegion(WorldEditService.context(player).world(),
                center.subtract(radius, radius, radius), center.add(radius, radius, radius));
        SnapshotJobs.run(player, box, 0, description, snapshot -> {
            mask.bind(snapshot);
            return BiomeCells.sphere(center.x(), center.y(), center.z(), radius, vertical, minY, maxY,
                    (x, y, z) -> snapshot.contains(x, y, z) && mask.test(x, y, z));
        }, (current, cells) -> queue(current, cells, biome, description));
    }

    private static void queue(ServerPlayerEntity player, long[] cells, BiomeType biome, String description) {
        if (cells.length == 0) {
            player.sendMessage(SnapshotJobs.message(description + ": la máscara no deja ninguna celda",
                    Formatting.YELLOW), false);
            return;
        }
        BiomeEditService.submit(player, cells, biome, description);
    }

    static String display(BiomeType biome) {
        String id = biome.id();
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException();
        }
    }
}
