package com.komainu.moxia.capability;

import com.komainu.moxia.worldedit.EditHistory;
import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.OperatorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldEvents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aplica las ediciones de las capacidades. Todas pasan por aquí: comprueba que el jugador pueda construir en cada
 * posición, deja que {@link HandEditEvents#COLLECT} añada copias (simetría), escribe con una {@code EditSession}
 * de WorldEdit (sin avisar a los vecinos si toca) y guarda en el historial lo que cambia más de un bloque.
 */
public final class HandEditApplier {
    /** Efectos de «Sin actualizaciones»: ni vecinos ni la llamada al colocarse (la arena no cae, el agua no corre). */
    private static final SideEffectSet NO_UPDATES = SideEffectSet.defaults()
            .with(SideEffect.NEIGHBORS, SideEffect.State.OFF)
            .with(SideEffect.UPDATE, SideEffect.State.OFF);

    /** Resultado: bloques cambiados, posiciones descartadas y el motivo de la primera que se descartó. */
    public record Result(int changed, int skipped, String reason) {
    }

    private HandEditApplier() {
    }

    public static Result apply(ServerPlayerEntity player, List<HandEdit> planned, boolean updates) {
        ServerWorld world = player.getServerWorld();
        Map<BlockPos, BlockState> edits = new LinkedHashMap<>();
        for (HandEdit edit : planned) {
            edits.put(edit.pos(), edit.state());
        }
        List<HandEdit> extra = new ArrayList<>();
        try {
            HandEditEvents.COLLECT.invoker().collect(player, world, List.copyOf(planned), extra::add);
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.warn("A hand edit listener failed for {}", player.getUuid(), exception);
        }
        for (HandEdit edit : extra) {
            edits.putIfAbsent(edit.pos(), edit.state());
        }

        List<HandEdit> accepted = new ArrayList<>(edits.size());
        int skipped = 0;
        String reason = null;
        for (Map.Entry<BlockPos, BlockState> entry : edits.entrySet()) {
            String denial = denial(player, world, entry.getKey(), entry.getValue());
            if (denial == null) {
                accepted.add(new HandEdit(entry.getKey(), entry.getValue()));
            } else {
                skipped++;
                if (reason == null) {
                    reason = denial;
                }
            }
        }
        if (accepted.isEmpty()) {
            return new Result(0, skipped, reason);
        }

        WorldEditService.PlayerContext context = WorldEditService.context(player);
        EditSession session = context.session().createEditSession(context.actor());
        if (!updates) {
            session.setSideEffectApplier(NO_UPDATES);
        }
        List<HandEdit> placed = new ArrayList<>();
        List<HandEdit> broken = new ArrayList<>();
        try {
            for (HandEdit edit : accepted) {
                BlockState before = world.getBlockState(edit.pos());
                BlockPos pos = edit.pos();
                if (!session.setBlock(BlockVector3.at(pos.getX(), pos.getY(), pos.getZ()),
                        FabricAdapter.adapt(edit.state()))) {
                    continue;
                }
                boolean removal = !before.isAir()
                        && (edit.state().isAir() || edit.state().getBlock() instanceof FluidBlock);
                if (removal) {
                    broken.add(new HandEdit(pos, before));
                } else {
                    placed.add(edit);
                }
            }
        } catch (MaxChangedBlocksException exception) {
            reason = "Has llegado al límite de bloques de WorldEdit";
        } finally {
            session.close();
            if (session.size() > 1) {
                EditHistory.label(session, "Edición a mano");
                context.session().remember(session);
            }
        }
        feedback(world, placed, broken);
        return new Result(session.size(), skipped, reason);
    }

    /** Motivo para no poder poner {@code state} en {@code pos}, o {@code null} si se puede. */
    private static String denial(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state) {
        if (!world.isInBuildLimit(pos)) {
            return "Fuera de los límites del mundo";
        }
        if (!BlockStates.loaded(world, pos)) {
            return "Esa zona no está cargada";
        }
        if (!player.canModifyBlocks() || !world.canPlayerModifyAt(player, pos)
                || player.isBlockBreakingRestricted(world, pos, player.interactionManager.getGameMode())) {
            return "No puedes construir ahí";
        }
        BlockState current = world.getBlockState(pos);
        if (current.equals(state)) {
            return "Ya es ese bloque";
        }
        if ((current.getBlock() instanceof OperatorBlock || state.getBlock() instanceof OperatorBlock)
                && !player.isCreativeLevelTwoOp()) {
            return "Solo un operador puede tocar ese bloque";
        }
        if (!state.isAir() && !world.canPlace(state, pos, ShapeContext.of(player))) {
            return "Hay alguien en medio";
        }
        // Los mods de protección (claims) escuchan este evento para decidir si se puede tocar un bloque.
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, pos, current,
                world.getBlockEntity(pos))) {
            return "Esa zona está protegida";
        }
        return null;
    }

    private static void feedback(ServerWorld world, List<HandEdit> placed, List<HandEdit> broken) {
        if (!placed.isEmpty()) {
            HandEdit first = placed.getFirst();
            BlockSoundGroup sounds = first.state().getSoundGroup();
            world.playSound(null, first.pos(), sounds.getPlaceSound(), SoundCategory.BLOCKS,
                    (sounds.getVolume() + 1.0F) / 2.0F, sounds.getPitch() * 0.8F);
        }
        if (!broken.isEmpty()) {
            HandEdit first = broken.getFirst();
            world.syncWorldEvent(null, WorldEvents.BLOCK_BROKEN, first.pos(), Block.getRawIdFromState(first.state()));
        }
    }
}
