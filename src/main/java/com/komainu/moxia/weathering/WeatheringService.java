package com.komainu.moxia.weathering;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.sculpt.SnapshotCaptureService;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Envejecer en el servidor: captura la selección repartida por ticks ({@link SnapshotCaptureService}), calcula la
 * ruina en un worker ({@link AsyncEngine}) con {@link WeatheringEngine} y la manda a {@link BatchEditService} como
 * un plan condicional: si alguien cambia un bloque mientras tanto, ese bloque se respeta. Se deshace con //undo.
 */
public final class WeatheringService {
    /** Lo que se puede envejecer de una vez: la captura de 2 millones de bloques dura unos segundos. */
    public static final long MAX_VOLUME = 2_000_000L;

    private WeatheringService() {
    }

    /** @return el volumen de la selección que se va a envejecer */
    public static long start(ServerPlayerEntity player, WeatheringSettings requested) throws IncompleteRegionException {
        KomaPermissions.requireBuild(player, "weather");
        Region region = WorldEditService.selection(player);
        BlockVector3 minimum = region.getMinimumPoint();
        BlockVector3 maximum = region.getMaximumPoint();
        WeatheringEngine.Box box = new WeatheringEngine.Box(
                minimum.x(), minimum.y(), minimum.z(), maximum.x(), maximum.y(), maximum.z()
        );
        if (box.volume() > MAX_VOLUME) {
            throw new IllegalArgumentException("La selección tiene " + box.volume()
                    + " bloques; Envejecer admite hasta 2 000 000. Hazlo por partes");
        }
        WeatheringSettings settings = requested.seed() == null
                ? requested.withSeed(ThreadLocalRandom.current().nextInt(1, 1_000_000))
                : requested;
        UUID playerId = player.getUuid();
        MinecraftServer server = player.getServer();
        RegistryKey<World> worldKey = player.getWorld().getRegistryKey();
        player.sendMessage(message("Capturando la construcción sin bloquear el servidor…"), true);
        SnapshotCaptureService.submitBox(
                player,
                WorldEditService.context(player).world(),
                minimum.subtract(1, 1, 1),
                maximum.add(1, 1, 1),
                snapshot -> compute(server, playerId, worldKey, snapshot, box, region, settings),
                throwable -> player.sendMessage(message("No se pudo capturar la selección: "
                        + readable(throwable)), false)
        );
        return box.volume();
    }

    private static void compute(
            MinecraftServer server,
            UUID playerId,
            RegistryKey<World> worldKey,
            WorldSnapshot snapshot,
            WeatheringEngine.Box box,
            Region region,
            WeatheringSettings settings
    ) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null || !player.getWorld().getRegistryKey().equals(worldKey)) {
            return;
        }
        player.sendMessage(message("Envejeciendo la construcción…"), true);
        AsyncEngine.submit(
                playerId,
                () -> plan(snapshot, box, region, settings),
                planned -> server.execute(() -> submit(server, playerId, worldKey, planned, settings)),
                throwable -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current != null) {
                        current.sendMessage(message("No se pudo envejecer: " + readable(throwable)), false);
                    }
                })
        );
    }

    private static void submit(
            MinecraftServer server,
            UUID playerId,
            RegistryKey<World> worldKey,
            Planned planned,
            WeatheringSettings settings
    ) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null || !player.getWorld().getRegistryKey().equals(worldKey)) {
            return;
        }
        WeatheringEngine.Result result = planned.result();
        if (planned.plan().size() == 0) {
            player.sendMessage(message("No hay nada que envejecer en la selección"
                    + " (¿solo terreno, o intensidad 0?)"), false);
            return;
        }
        try {
            int queued = BatchEditService.submit(player, planned.plan(), "air", "Envejecer");
            player.sendMessage(message("Envejecer en cola: " + queued + " bloques · desgaste " + result.eroded()
                    + ", caídos " + result.fallen() + ", escombro " + result.rubble() + ", variantes "
                    + result.aged() + ", vida " + result.life() + " · semilla " + settings.seed()
                    + " · //undo para deshacer"), false);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(message("No se pudo envejecer: " + readable(exception)), false);
        }
    }

    /** Trabajo del worker: solo lee la captura y la selección (una copia), nunca el mundo. */
    static Planned plan(WorldSnapshot snapshot, WeatheringEngine.Box box, Region region, WeatheringSettings settings) {
        Map<BlockState, String> names = new HashMap<>();
        WeatheringEngine.Result result = WeatheringEngine.weather(
                (x, y, z) -> names.computeIfAbsent(snapshot.get(x, y, z), BlockState::getAsString),
                box,
                (x, y, z) -> region.contains(BlockVector3.at(x, y, z)),
                settings
        );
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(result.changes().size());
        Map<String, BlockState> parsed = new HashMap<>();
        Map<BlockState, Integer> rawIds = new HashMap<>();
        for (WeatheringEngine.Change change : result.changes()) {
            BlockState after = parsed.computeIfAbsent(change.after(), WeatheringService::toState);
            if (after == null) {
                continue;
            }
            BlockState before = snapshot.get(change.x(), change.y(), change.z());
            int expected = rawIds.computeIfAbsent(before,
                    state -> Block.getRawIdFromState(FabricAdapter.adapt(state)));
            builder.addConditionalOverride(change.x(), change.y(), change.z(), expected, after);
        }
        return new Planned(builder.build(), result);
    }

    /** Texto del motor a estado de WorldEdit; las propiedades que el bloque no tenga se ignoran. */
    static BlockState toState(String text) {
        BlockText block = BlockText.parse(text);
        BlockType type = BlockTypes.get(block.id());
        if (type == null) {
            return null;
        }
        BlockState state = type.getDefaultState();
        for (Map.Entry<String, String> entry : block.properties().entrySet()) {
            Property<Object> property = type.getPropertyMap().containsKey(entry.getKey())
                    ? type.getProperty(entry.getKey())
                    : null;
            if (property == null) {
                continue;
            }
            try {
                Object value = property.getValueFor(entry.getValue());
                if (value != null) {
                    state = state.with(property, value);
                }
            } catch (IllegalArgumentException ignored) {
                // Valor que este bloque no admite: se queda el de serie.
            }
        }
        return state;
    }

    private static Text message(String value) {
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD)
                .append(Text.literal(value).formatted(Formatting.GRAY));
    }

    private static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    record Planned(PackedBlockPlan plan, WeatheringEngine.Result result) {
    }
}
