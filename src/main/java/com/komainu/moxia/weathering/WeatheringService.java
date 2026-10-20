package com.komainu.moxia.weathering;

import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.RegionEdits;
import com.komainu.moxia.region.RegionOperation;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Envejecer en el servidor, sobre el motor común de operaciones de región ({@link RegionOperationService}): la
 * selección se lee repartida entre ticks con un bloque de margen, {@link WeatheringEngine} calcula la ruina en un
 * worker y los cambios se escriben de forma condicional (lo que alguien cambie mientras tanto se respeta) y se
 * deshacen con //undo.
 */
public final class WeatheringService {
    /** Lo que se puede envejecer de una vez; el cálculo tarda alrededor de 1 s con 2 millones de bloques. */
    public static final long MAX_VOLUME = 2_000_000L;

    private WeatheringService() {
    }

    /** @return la semilla usada, para que el jugador pueda repetir el resultado */
    public static long start(ServerPlayerEntity player, WeatheringSettings requested) throws IncompleteRegionException {
        KomaPermissions.requireBuild(player, "weather");
        Region region = WorldEditService.selection(player);
        BlockVector3 minimum = region.getMinimumPoint();
        BlockVector3 maximum = region.getMaximumPoint();
        WeatheringEngine.Box box = new WeatheringEngine.Box(
                minimum.x(), minimum.y(), minimum.z(), maximum.x(), maximum.y(), maximum.z()
        );
        if (box.volume() > MAX_VOLUME) {
            throw new IllegalArgumentException("La selección ocupa " + ProgressLine.group(box.volume())
                    + " bloques; Envejecer admite hasta 2 000 000. Hazlo por partes");
        }
        WeatheringSettings settings = requested.seed() == null
                ? requested.withSeed(ThreadLocalRandom.current().nextInt(1, 1_000_000))
                : requested;
        RegionOperationService.submit(player, 1, "Envejecer (semilla " + settings.seed() + ")",
                new Operation(box, settings));
        return settings.seed();
    }

    /** La operación del worker: solo lee la foto de la región. */
    record Operation(WeatheringEngine.Box box, WeatheringSettings settings) implements RegionOperation {
        @Override
        public void apply(RegionSnapshot snapshot, RegionEdits edits) {
            Map<BlockState, String> names = new HashMap<>();
            WeatheringEngine.Result result = WeatheringEngine.weather(
                    (x, y, z) -> {
                        BlockState state = snapshot.state(x, y, z);
                        return state == null ? "minecraft:air" : names.computeIfAbsent(state, BlockState::getAsString);
                    },
                    box,
                    snapshot::selected,
                    settings
            );
            edits.checkCancelled();
            Map<String, BlockState> parsed = new HashMap<>();
            for (WeatheringEngine.Change change : result.changes()) {
                BlockState after = parsed.computeIfAbsent(change.after(), WeatheringService::toState);
                if (after != null) {
                    edits.set(change.x(), change.y(), change.z(), after);
                }
                edits.checkCancelled();
            }
        }
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
            if (!type.getPropertyMap().containsKey(entry.getKey())) {
                continue;
            }
            Property<Object> property = type.getProperty(entry.getKey());
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
}
