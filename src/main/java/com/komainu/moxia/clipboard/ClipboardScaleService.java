package com.komainu.moxia.clipboard;

import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.symmetry.SymmetryStates;
import com.komainu.moxia.symmetry.SymmetryTransform;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Escala o gira el portapapeles de WorldEdit con {@link ClipboardTransform} y lo sustituye por el resultado. El
 * portapapeles se lee en el hilo del servidor; el cálculo y el portapapeles nuevo se hacen en un worker, y el cambio
 * de portapapeles vuelve al hilo del servidor. El anterior se guarda (uno por jugador) para
 * {@code /komaedit clipboard restore}.
 *
 * <p>Si el portapapeles tenía un //rotate o //flip pendiente, se aplica antes. Los bloques con datos (cofres,
 * carteles) conservan los suyos en cada copia; las entidades y los biomas del portapapeles no se copian.</p>
 */
public final class ClipboardScaleService {
    /** Caja máxima del portapapeles que se lee. */
    public static final long MAX_SOURCE = 1_000_000L;
    /** Con Scale2x, Scale3x y RotSprite el original se amplía: caja máxima más pequeña. */
    public static final long MAX_SMART_SOURCE = 300_000L;
    /** Caja máxima del resultado. */
    public static final long MAX_RESULT = 8_000_000L;
    private static final Map<UUID, ClipboardHolder> PREVIOUS = new ConcurrentHashMap<>();
    private static final AtomicBoolean HOOKED = new AtomicBoolean();

    private ClipboardScaleService() {
    }

    /** El portapapeles ya leído: la rejilla de números, qué bloque es cada número y el origen. */
    public record Captured(VoxelVolume volume, List<BaseBlock> palette, BlockVector3 origin, int entities,
                           ClipboardHolder holder) {
    }

    /** Lo que se le hace: escalar o girar. */
    public record Change(boolean rotate, double amount, ClipboardTransform.Mode mode) {
        public String describe() {
            String value = amount == Math.rint(amount) ? Long.toString((long) amount) : Double.toString(amount);
            return (rotate ? "giro de " + value + "°" : "escala ×" + value) + " (" + mode.id() + ")";
        }
    }

    /** Resumen del resultado para el chat. */
    public record Outcome(int blocksBefore, int blocksAfter, BlockVector3 size, int droppedEntities) {
    }

    /** Lee el portapapeles en el hilo del servidor. */
    public static Captured capture(ServerPlayerEntity player, ClipboardTransform.Mode mode) {
        LocalSession session = WorldEditService.context(player).session();
        ClipboardHolder holder;
        try {
            holder = session.getClipboard();
        } catch (EmptyClipboardException exception) {
            throw new IllegalArgumentException("El portapapeles está vacío: copia algo con //copy", exception);
        }
        Clipboard clipboard = holder.getClipboard();
        long limit = mode == ClipboardTransform.Mode.NEAREST ? MAX_SOURCE : MAX_SMART_SOURCE;
        if (clipboard.getRegion().getVolume() > limit) {
            throw new IllegalArgumentException("El portapapeles ocupa " + clipboard.getRegion().getVolume()
                    + " bloques de caja; con " + mode.id() + " el máximo es " + limit);
        }
        if (!holder.getTransform().isIdentity()) {
            try {
                clipboard = clipboard.transform(holder.getTransform());
            } catch (WorldEditException exception) {
                throw new IllegalArgumentException("No se pudo aplicar el //rotate pendiente: "
                        + exception.getMessage(), exception);
            }
        }
        BlockVector3 origin = clipboard.getOrigin();
        BlockVector3 minimum = clipboard.getMinimumPoint();
        BlockVector3 dimensions = clipboard.getDimensions();
        VoxelVolume volume = new VoxelVolume(minimum.x() - origin.x(), minimum.y() - origin.y(),
                minimum.z() - origin.z(), dimensions.x(), dimensions.y(), dimensions.z());
        // La entrada 0 es el aire y nunca se escribe.
        List<BaseBlock> palette = new ArrayList<>();
        palette.add(null);
        Map<BaseBlock, Integer> ids = new HashMap<>();
        for (BlockVector3 position : clipboard.getRegion()) {
            BaseBlock block = clipboard.getFullBlock(position);
            if (block.getBlockType().getMaterial().isAir()) {
                continue;
            }
            int id = ids.computeIfAbsent(block, key -> {
                palette.add(key);
                return palette.size() - 1;
            });
            volume.set(position.x() - origin.x(), position.y() - origin.y(), position.z() - origin.z(), id);
        }
        if (volume.blocks() == 0) {
            throw new IllegalArgumentException("El portapapeles solo tiene aire");
        }
        return new Captured(volume, Collections.unmodifiableList(palette), origin, clipboard.getEntities().size(),
                holder);
    }

    /**
     * Lee, calcula en un worker y sustituye el portapapeles. {@code done} y {@code failed} llegan en el hilo del
     * servidor.
     */
    public static void start(ServerPlayerEntity player, Change change, Consumer<Outcome> done,
                             Consumer<Throwable> failed) {
        hook();
        Captured captured = capture(player, change.mode());
        UUID playerId = player.getUuid();
        MinecraftServer server = player.getServer();
        AsyncEngine.submit(playerId, change.rotate() ? "Girar portapapeles" : "Escalar portapapeles",
                () -> compute(captured, change),
                result -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current == null) {
                        return;
                    }
                    LocalSession session = WorldEditService.context(current).session();
                    PREVIOUS.put(playerId, captured.holder());
                    session.setClipboard(new ClipboardHolder(result.clipboard()));
                    done.accept(result.outcome());
                }),
                throwable -> server.execute(() -> failed.accept(throwable)));
    }

    /** Vuelve a poner el portapapeles de antes del último escalado o giro. */
    public static boolean restore(ServerPlayerEntity player) {
        ClipboardHolder previous = PREVIOUS.remove(player.getUuid());
        if (previous == null) {
            return false;
        }
        WorldEditService.context(player).session().setClipboard(previous);
        return true;
    }

    /** Olvida el portapapeles anterior al salir el jugador o pararse el servidor (se engancha la primera vez). */
    private static void hook() {
        if (HOOKED.compareAndSet(false, true)) {
            ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                    PREVIOUS.remove(handler.player.getUuid()));
            ServerLifecycleEvents.SERVER_STOPPED.register(server -> PREVIOUS.clear());
        }
    }

    private record Computed(Clipboard clipboard, Outcome outcome) {
    }

    /** En el worker: transforma la rejilla y monta el portapapeles nuevo con los estados girados. */
    private static Computed compute(Captured captured, Change change) {
        Runnable cancel = () -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Cálculo cancelado");
            }
        };
        VoxelVolume result = change.rotate()
                ? ClipboardTransform.rotate(captured.volume(), change.amount(), change.mode(), MAX_RESULT, cancel)
                : ClipboardTransform.scale(captured.volume(), change.amount(), change.mode(), MAX_RESULT, cancel);
        if (result.blocks() == 0) {
            throw new IllegalArgumentException("El resultado se queda sin bloques; usa un factor mayor");
        }
        BlockVector3 origin = captured.origin();
        BlockVector3 minimum = origin.add(result.minimumX(), result.minimumY(), result.minimumZ());
        BlockVector3 maximum = minimum.add(result.sizeX() - 1, result.sizeY() - 1, result.sizeZ() - 1);
        BlockArrayClipboard clipboard = new BlockArrayClipboard(new CuboidRegion(minimum, maximum));
        clipboard.setOrigin(origin);
        BaseBlock[] blocks = new BaseBlock[captured.palette().size()];
        SymmetryTransform turn = change.rotate() ? ClipboardStates.rotation(change.amount()) : null;
        int[] written = {0};
        result.forEach((x, y, z, id) -> {
            BaseBlock block = blocks[id];
            if (block == null) {
                block = turn == null ? captured.palette().get(id) : rotate(captured.palette().get(id), turn);
                blocks[id] = block;
            }
            clipboard.setBlock(origin.add(x, y, z), block);
            if ((++written[0] & 0xFFFF) == 0) {
                cancel.run();
            }
        });
        Outcome outcome = new Outcome(captured.volume().blocks(), result.blocks(),
                BlockVector3.at(result.sizeX(), result.sizeY(), result.sizeZ()), captured.entities());
        return new Computed(clipboard, outcome);
    }

    /** Gira el estado de un bloque (escaleras, troncos, vallas…) y conserva sus datos. */
    static BaseBlock rotate(BaseBlock block, SymmetryTransform turn) {
        if (turn.identity()) {
            return block;
        }
        net.minecraft.block.BlockState state = FabricAdapter.adapt(block.toImmutableState());
        net.minecraft.block.BlockState turned = SymmetryStates.transform(state, turn);
        if (turned == state) {
            return block;
        }
        BlockState adapted = FabricAdapter.adapt(turned);
        return adapted.toBaseBlock(block.getNbtReference());
    }
}
