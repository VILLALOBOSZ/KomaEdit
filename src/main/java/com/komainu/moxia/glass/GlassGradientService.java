package com.komainu.moxia.glass;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.performance.AsyncEngine;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Coloca una vidriera degradada delante del jugador: las capas de cristal se apilan en la dirección en la que mira
 * (la primera es la más cercana) y el degradado avanza en la dirección pedida. Con {@code -b} pone detrás el bloque
 * opaco que mejor completa cada color.
 */
public final class GlassGradientService {
    private GlassGradientService() {
    }

    public static void start(ServerPlayerEntity player, GlassGradientRequest request) {
        Direction view = view(player);
        Direction along = along(player, view, request.direction());
        BlockPos origin = BlockPos.ofFloored(player.getEyePos()).offset(view, 2);
        List<GlassColors.Swatch> backgrounds = request.findBackground()
                ? GlassColors.backgrounds()
                : List.of(new GlassColors.Swatch("", request.background()[0], request.background()[1],
                request.background()[2], 1.0));
        if (backgrounds.isEmpty()) {
            throw new IllegalStateException("Falta la tabla de colores de fondo del mod");
        }
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUuid();
        player.sendMessage(prefix().append(Text.literal("Vidriera: buscando las mezclas de " + request.layers()
                + " capas para " + request.length() + " pasos…").formatted(Formatting.GRAY)), false);
        AsyncEngine.submit(
                playerId,
                () -> GlassGradientSolver.standard().gradient(request.start(), request.end(), request.layers(),
                        request.length(), request.quality(), backgrounds, request.unordered(),
                        () -> Thread.currentThread().isInterrupted()),
                stacks -> server.execute(() -> place(server, playerId, request, stacks, origin, view, along)),
                throwable -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current != null && !(throwable instanceof java.util.concurrent.CancellationException)) {
                        current.sendMessage(prefix().append(Text.literal("Vidriera: " + throwable.getMessage())
                                .formatted(Formatting.RED)), false);
                    }
                })
        );
    }

    private static void place(MinecraftServer server, UUID playerId, GlassGradientRequest request,
                              List<GlassGradientSolver.Stack> stacks, BlockPos origin, Direction view,
                              Direction along) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            return;
        }
        PackedBlockPlan.Builder plan = PackedBlockPlan.builder(stacks.size() * (request.layers() + 1));
        double error = 0.0;
        double worst = 0.0;
        for (int step = 0; step < stacks.size(); step++) {
            GlassGradientSolver.Stack stack = stacks.get(step);
            error += stack.error();
            worst = Math.max(worst, stack.error());
            BlockPos row = origin.offset(along, step);
            for (int layer = 0; layer < stack.layers().size(); layer++) {
                put(plan, row.offset(view, layer), stack.layers().get(layer));
            }
            if (request.findBackground()) {
                put(plan, row.offset(view, stack.layers().size()), stack.background());
            }
        }
        try {
            PackedBlockPlan built = plan.build();
            int queued = BatchEditService.submit(player, built, "air", "Vidriera degradada");
            if (PlanPreviews.holds(player.getUuid(), built)) {
                return;
            }
            GlassGradientSolver.Stack first = stacks.getFirst();
            String detail = stacks.size() == 1
                    ? " · se ve " + GlassColors.toHex(first.color()) + " (buscado " + GlassColors.toHex(first.target())
                    + ")" + (request.findBackground() ? " sobre " + first.background() : "")
                    : String.format(Locale.ROOT, " · error medio %.3f, máximo %.3f (OKLab)", error / stacks.size(),
                    worst);
            player.sendMessage(prefix().append(Text.literal("Vidriera: " + queued + " bloques en cola" + detail
                    + " · //undo para deshacer").formatted(Formatting.GREEN)), false);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(prefix().append(Text.literal("Vidriera: " + exception.getMessage())
                    .formatted(Formatting.RED)), false);
        }
    }

    private static void put(PackedBlockPlan.Builder plan, BlockPos position, String blockId) {
        BlockType type = BlockTypes.get(blockId);
        if (type == null) {
            throw new IllegalStateException("No existe el bloque " + blockId);
        }
        plan.addOverride(position.getX(), position.getY(), position.getZ(), type.getDefaultState());
    }

    /** Hacia dónde mira el jugador: horizontal salvo que mire muy arriba o muy abajo. */
    private static Direction view(ServerPlayerEntity player) {
        if (player.getPitch() < -60.0F) {
            return Direction.UP;
        }
        if (player.getPitch() > 60.0F) {
            return Direction.DOWN;
        }
        return player.getHorizontalFacing();
    }

    private static Direction along(ServerPlayerEntity player, Direction view, String name) {
        Direction facing = player.getHorizontalFacing();
        Direction direction = switch (name) {
            case "up", "arriba" -> Direction.UP;
            case "down", "abajo" -> Direction.DOWN;
            case "left", "izquierda" -> facing.rotateYCounterclockwise();
            case "right", "derecha" -> facing.rotateYClockwise();
            case "north", "norte" -> Direction.NORTH;
            case "south", "sur" -> Direction.SOUTH;
            case "east", "este" -> Direction.EAST;
            case "west", "oeste" -> Direction.WEST;
            default -> throw new IllegalArgumentException("Dirección desconocida: " + name);
        };
        if (direction.getAxis() == view.getAxis()) {
            throw new IllegalArgumentException("El degradado no puede avanzar hacia donde miras (" + name
                    + "): las capas ya se apilan en esa dirección; usa up, down, left o right");
        }
        return direction;
    }

    private static net.minecraft.text.MutableText prefix() {
        return Text.literal("KomaEdit · ").formatted(Formatting.DARK_AQUA);
    }
}
