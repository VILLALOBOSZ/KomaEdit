package com.komainu.moxia.worldedit.ez;

import com.komainu.moxia.gradient.SavedPalettes;
import com.komainu.moxia.gradient.PaletteSpec;
import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.noise.SavedNoises;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.ShapeContext;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lo que necesitan las máscaras y patrones de KomaEdit del contexto de WorldEdit: el jugador, su mundo de Minecraft,
 * sus paletas y ruidos guardados y la conversión de estados.
 */
public final class EzContext {
    /** Alcance de {@code #aim}, en bloques. */
    public static final double AIM_RANGE = 256.0;
    private static final Map<BlockState, net.minecraft.block.BlockState> STATES = new ConcurrentHashMap<>();
    private static volatile MinecraftServer server;

    private EzContext() {
    }

    public static void server(MinecraftServer current) {
        server = current;
        STATES.clear();
    }

    /** El jugador que escribió la orden, o una excepción de WorldEdit si no la escribió un jugador. */
    public static ServerPlayerEntity player(ParserContext context, String feature) throws InputParseException {
        Actor actor = context.getActor();
        MinecraftServer current = server;
        if (actor instanceof Player player && current != null) {
            ServerPlayerEntity serverPlayer = current.getPlayerManager().getPlayer(player.getUniqueId());
            if (serverPlayer != null) {
                return serverPlayer;
            }
        }
        throw error(feature + " solo lo puede usar un jugador");
    }

    /** El jugador que escribió la orden, o {@code null} (consola, bloque de órdenes…). */
    public static ServerPlayerEntity optionalPlayer(ParserContext context) {
        MinecraftServer current = server;
        if (context.getActor() instanceof Player player && current != null) {
            return current.getPlayerManager().getPlayer(player.getUniqueId());
        }
        return null;
    }

    /** UUID del jugador para buscar lo que tiene guardado; {@code null} si no es un jugador. */
    public static UUID playerId(ParserContext context) {
        return context.getActor() instanceof Player player ? player.getUniqueId() : null;
    }

    public static World world(ParserContext context) throws InputParseException {
        return FabricAdapter.adapt(context.requireWorld());
    }

    public static List<String> palette(ParserContext context, String input) throws InputParseException {
        try {
            UUID playerId = playerId(context);
            return playerId == null ? PaletteSpec.parse(input) : SavedPalettes.parse(playerId, input);
        } catch (IllegalArgumentException exception) {
            throw error(exception.getMessage());
        }
    }

    public static NoiseSpec noise(ParserContext context, String input) throws InputParseException {
        try {
            UUID playerId = playerId(context);
            return playerId == null ? NoiseSpec.parse(input) : SavedNoises.parse(playerId, input);
        } catch (IllegalArgumentException exception) {
            throw error(exception.getMessage());
        }
    }

    public static net.minecraft.block.BlockState minecraft(BlockState state) {
        return STATES.computeIfAbsent(state, FabricAdapter::adapt);
    }

    public static InputParseException error(String message) {
        return new InputParseException(TextComponent.of(message == null ? "Entrada inválida" : message));
    }

    /**
     * El bloque al que mira el jugador, o {@code null}. No carga chunks: se para en el primero sin cargar.
     *
     * @param hitboxes si es {@code true} respeta la forma de cada bloque (se puede apuntar detrás de una losa); si es
     *                 {@code false} todo bloque que no sea aire ni fluido cuenta como cubo completo
     */
    public static BlockPos aim(ServerPlayerEntity player, boolean hitboxes) {
        World world = player.getWorld();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d end = eye.add(look.multiply(AIM_RANGE));
        ShapeContext shapeContext = ShapeContext.of(player);
        BlockVector3 hit = VoxelRay.first(eye.x, eye.y, eye.z, look.x, look.y, look.z, AIM_RANGE, position -> {
            if (!world.getChunkManager().isChunkLoaded(position.x() >> 4, position.z() >> 4)
                    || world.isOutOfHeightLimit(position.y())) {
                return true;
            }
            BlockPos blockPos = new BlockPos(position.x(), position.y(), position.z());
            net.minecraft.block.BlockState state = world.getBlockState(blockPos);
            if (state.isAir() || state.getBlock() instanceof FluidBlock) {
                return false;
            }
            if (!hitboxes) {
                return true;
            }
            BlockHitResult result = state.getOutlineShape(world, blockPos, shapeContext).raycast(eye, end, blockPos);
            return result != null;
        });
        if (hit == null) {
            return null;
        }
        BlockPos result = new BlockPos(hit.x(), hit.y(), hit.z());
        if (!world.getChunkManager().isChunkLoaded(hit.x() >> 4, hit.z() >> 4) || world.isOutOfHeightLimit(result)) {
            return null;
        }
        return result;
    }
}
