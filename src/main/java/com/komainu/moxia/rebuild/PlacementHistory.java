package com.komainu.moxia.rebuild;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.persistence.AtomicStorage;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * El orden en que se construyó cada mundo, para la reconstrucción en orden «historial». Apunta lo que los jugadores
 * colocan a mano (también con las capacidades de KomaEdit y la otra mitad de una puerta o una cama) y las ediciones
 * pequeñas de WorldEdit y KomaEdit (hasta {@link RebuildConfig.Settings#historyEditBlocks} bloques; un relleno de
 * terreno no cuenta como «cómo se construyó»). Romper o vaciar un bloque lo olvida.
 *
 * <p>Se guarda por mundo en {@code <mundo>/komaedit/placements/} cada cinco minutos (en otro hilo) y al parar el
 * servidor. Solo se apuntan posiciones, no quién ni qué. Todo pasa en el hilo del servidor.</p>
 */
public final class PlacementHistory {
    private static final int SAVE_EVERY_TICKS = 6_000;
    private static final Map<RegistryKey<World>, PlacementLog> LOGS = new HashMap<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit historial de colocación");
        thread.setDaemon(true);
        return thread;
    });
    private static final BlockState[] BEFORE = new BlockState[7];
    private static MinecraftServer server;
    private static BlockPos pendingPos;
    private static World pendingWorld;
    private static int ticks;
    private static boolean hookInstalled;

    private PlacementHistory() {
    }

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld serverWorld) {
                removed(serverWorld, pos);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ticked -> {
            if (++ticks % SAVE_EVERY_TICKS == 0) {
                save(false);
            }
        });
    }

    public static synchronized void start(MinecraftServer started) {
        server = started;
        // Como la simetría: el gancho de WorldEdit, una vez y con el servidor ya en marcha.
        if (!hookInstalled) {
            WorldEdit.getInstance().getEventBus().register(new Hook());
            hookInstalled = true;
        }
    }

    /** Puesto de la última colocación en esa posición del mundo (mayor = más reciente), o sin historial. */
    public static long rank(ServerWorld world, int x, int y, int z) {
        PlacementLog log = log(world, false);
        return log == null ? RebuildModel.NO_HISTORY : log.rank(PackedBlockPlan.pack(x, y, z));
    }

    /** Si hay algo apuntado en ese mundo. */
    public static boolean any(ServerWorld world) {
        PlacementLog log = log(world, false);
        return log != null && log.size() > 0;
    }

    public static void placed(ServerWorld world, BlockPos pos) {
        PlacementLog log = log(world, true);
        if (log != null) {
            log.record(PackedBlockPlan.pack(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    public static void removed(ServerWorld world, BlockPos pos) {
        PlacementLog log = log(world, false);
        if (log != null) {
            log.forget(PackedBlockPlan.pack(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    /** Lo llama {@code BlockItemMixin} antes de colocar con la mano: cómo estaban el sitio y sus vecinos. */
    public static void beforePlace(World world, BlockPos pos) {
        if (world.isClient() || RebuildConfig.settings().historyBlocks() == 0) {
            pendingWorld = null;
            return;
        }
        pendingWorld = world;
        pendingPos = pos.toImmutable();
        BEFORE[0] = world.getBlockState(pos);
        for (Direction direction : Direction.values()) {
            BEFORE[direction.ordinal() + 1] = world.getBlockState(pos.offset(direction));
        }
    }

    /** Y después: apunta el bloque y, si apareció a su lado el mismo bloque, su otra mitad. */
    public static void afterPlace(PlayerEntity player, World world, BlockPos pos, boolean accepted) {
        World expected = pendingWorld;
        pendingWorld = null;
        if (!accepted || expected != world || !pos.equals(pendingPos) || !(world instanceof ServerWorld serverWorld)
                || player == null) {
            return;
        }
        BlockState result = world.getBlockState(pos);
        if (result.isAir() || result.equals(BEFORE[0])) {
            return;
        }
        placed(serverWorld, pos);
        Block block = result.getBlock();
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            if (world.getBlockState(neighbor).isOf(block) && !BEFORE[direction.ordinal() + 1].isOf(block)) {
                placed(serverWorld, neighbor);
            }
        }
    }

    private static PlacementLog log(ServerWorld world, boolean create) {
        int capacity = RebuildConfig.settings().historyBlocks();
        if (capacity == 0 || server == null) {
            return null;
        }
        RegistryKey<World> key = world.getRegistryKey();
        PlacementLog log = LOGS.get(key);
        if (log == null) {
            // Una vez por mundo: sin archivo queda uno vacío (no ocupa hasta apuntar algo) y no se vuelve a mirar
            // el disco en cada consulta.
            log = load(key, capacity);
            if (log == null) {
                log = new PlacementLog(capacity);
            }
            LOGS.put(key, log);
        }
        return log;
    }

    private static PlacementLog load(RegistryKey<World> key, int capacity) {
        Path file = file(key);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream input = Files.newInputStream(file)) {
            return PlacementLog.read(input, capacity);
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("KomaEdit could not read the placement history of {}: {}", key.getValue(),
                    exception.getMessage());
            return null;
        }
    }

    private static Path file(RegistryKey<World> key) {
        Identifier id = key.getValue();
        return server.getSavePath(WorldSavePath.ROOT).resolve("komaedit").resolve("placements")
                .resolve(id.getNamespace()).resolve(id.getPath() + ".bin");
    }

    /**
     * Guarda los mundos con cambios. {@code sync}: en este hilo y esperando a lo que estuviera escribiéndose (al parar
     * el servidor); si no, en el hilo de guardado.
     */
    public static void save(boolean sync) {
        if (server == null) {
            return;
        }
        for (Map.Entry<RegistryKey<World>, PlacementLog> entry : LOGS.entrySet()) {
            PlacementLog log = entry.getValue();
            if (!log.dirty()) {
                continue;
            }
            long[] positions = log.positions();
            Path file = file(entry.getKey());
            log.saved();
            Runnable write = () -> write(file, positions);
            if (sync) {
                write.run();
            } else {
                IO.execute(write);
            }
        }
        if (sync) {
            // Lo que se estuviera escribiendo en el otro hilo, antes de dar el mundo por guardado.
            try {
                IO.submit(() -> { }).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException exception) {
                KomaEditMod.LOGGER.warn("KomaEdit placement history still saving: {}", exception.getMessage());
            }
        }
    }

    private static void write(Path file, long[] positions) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = AtomicStorage.temporary(file);
            try (OutputStream output = Files.newOutputStream(temporary)) {
                PlacementLog.writePositions(positions, output);
            }
            AtomicStorage.replace(temporary, file);
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("KomaEdit could not save the placement history {}: {}", file,
                    exception.getMessage());
        }
    }

    public static void clearAll() {
        LOGS.clear();
        server = null;
        pendingWorld = null;
    }

    /**
     * Apunta las ediciones de WorldEdit (y KomaEdit) de los jugadores, en la etapa {@code BEFORE_HISTORY} como la
     * simetría. La reconstrucción escribe sin jugador, así que no se apunta a sí misma; deshacer escribe por debajo
     * de esta etapa.
     */
    public static final class Hook {
        @Subscribe
        public void onEditSession(EditSessionEvent event) {
            Actor actor = event.getActor();
            if (event.getStage() != EditSession.Stage.BEFORE_HISTORY || actor == null || !actor.isPlayer()
                    || event.getWorld() == null || server == null) {
                return;
            }
            int limit = RebuildConfig.settings().historyEditBlocks();
            if (limit == 0 || RebuildConfig.settings().historyBlocks() == 0) {
                return;
            }
            ServerWorld world;
            try {
                world = (ServerWorld) FabricAdapter.adapt(event.getWorld());
            } catch (RuntimeException exception) {
                return;
            }
            event.setExtent(new Recording(event.getExtent(), world, limit));
        }
    }

    /**
     * Junta los bloques que escribe la edición y los apunta al vaciarse la sesión, en orden. Si pasa de
     * {@code limit}, deja de apuntar: una orden de WorldEdit se vacía de una vez al acabar, así que un relleno grande
     * no apunta nada (una edición de KomaEdit por ventanas puede haber apuntado ya sus primeras ventanas).
     */
    private static final class Recording extends AbstractDelegateExtent {
        private final ServerWorld world;
        private final int limit;
        private final LongArrayList pending = new LongArrayList();
        private final BooleanArrayList air = new BooleanArrayList();
        private int seen;

        private Recording(Extent extent, ServerWorld world, int limit) {
            super(extent);
            this.world = world;
            this.limit = limit;
        }

        @Override
        public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 location, B block)
                throws WorldEditException {
            boolean changed = super.setBlock(location, block);
            if (changed && seen <= limit) {
                if (++seen > limit) {
                    pending.clear();
                    air.clear();
                } else {
                    pending.add(PackedBlockPlan.pack(location.x(), location.y(), location.z()));
                    air.add(block.getBlockType().getMaterial().isAir());
                }
            }
            return changed;
        }

        @Override
        protected Operation commitBefore() {
            if (!pending.isEmpty() && server != null && server.isOnThread()) {
                BlockPos.Mutable pos = new BlockPos.Mutable();
                for (int index = 0; index < pending.size(); index++) {
                    long packed = pending.getLong(index);
                    pos.set(PackedBlockPlan.unpackX(packed), PackedBlockPlan.unpackY(packed),
                            PackedBlockPlan.unpackZ(packed));
                    if (air.getBoolean(index)) {
                        removed(world, pos);
                    } else {
                        placed(world, pos);
                    }
                }
            }
            pending.clear();
            air.clear();
            return super.commitBefore();
        }
    }
}
