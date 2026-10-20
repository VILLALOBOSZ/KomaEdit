package com.komainu.moxia.command;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.QuietMode;
import com.komainu.moxia.placement.PlacementService;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BaseBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit paste} (alias {@code pegar}): pega el portapapeles de WorldEdit como {@code //paste}, con su
 * {@code //rotate} pendiente y sus bloques con datos, pero por el motor de KomaEdit. Con el mod en el cliente y el
 * fantasma activado no se pega al momento: aparece el fantasma con su gizmo para moverlo, girarlo y confirmarlo.
 * Sin el mod, pega directamente (y con {@code -v}, con la vista previa de siempre).
 *
 * <p>Banderas: {@code -a} no pega el aire; {@code -o} lo pone donde se copió en lugar de junto al jugador.</p>
 */
public final class PasteCommands {
    private static final List<String> FLAGS = List.of("-a", "-o", "-a -o");

    private PasteCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(command("paste").build());
        root.addChild(command("pegar").build());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        return literal(name)
                .requires(EzCommandSupport.node(KomaPermissions.CLIPBOARD))
                .executes(context -> paste(context, ""))
                .then(argument("banderas", StringArgumentType.greedyString())
                        .suggests(EzCommandSupport.words(FLAGS))
                        .executes(context -> paste(context, StringArgumentType.getString(context, "banderas"))));
    }

    /** Lo que piden las banderas. */
    record Flags(boolean skipAir, boolean atOrigin) {
        static Flags parse(String input) {
            boolean skipAir = false;
            boolean atOrigin = false;
            for (String word : input.strip().toLowerCase(Locale.ROOT).split("\\s+")) {
                switch (word) {
                    case "" -> {
                    }
                    case "-a" -> skipAir = true;
                    case "-o" -> atOrigin = true;
                    case "-ao", "-oa" -> {
                        skipAir = true;
                        atOrigin = true;
                    }
                    default -> throw new IllegalArgumentException("Bandera desconocida: " + word
                            + " (-a sin aire, -o donde se copió)");
                }
            }
            return new Flags(skipAir, atOrigin);
        }
    }

    private static int paste(CommandContext<ServerCommandSource> context, String input) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            Flags flags = Flags.parse(input);
            Clipboard clipboard = clipboard(player);
            Region region = clipboard.getRegion();
            BlockVector3 size = region.getMaximumPoint().subtract(region.getMinimumPoint()).add(1, 1, 1);
            if ((long) size.x() * size.y() * size.z() > PlacementService.MAX_STRUCTURE_VOLUME) {
                throw new IllegalArgumentException("El portapapeles supera " + ProgressLine.group(
                        PlacementService.MAX_STRUCTURE_VOLUME) + " bloques de caja");
            }
            BlockVector3 target = flags.atOrigin() ? clipboard.getOrigin()
                    : BlockVector3.at(player.getBlockX(), player.getBlockY(), player.getBlockZ());
            BlockVector3 offset = target.subtract(clipboard.getOrigin());
            PackedBlockPlan.Builder builder = PackedBlockPlan.builder((int) Math.min(region.getVolume(), 1 << 20));
            Long2ObjectOpenHashMap<BaseBlock> data = new Long2ObjectOpenHashMap<>();
            for (BlockVector3 position : region) {
                BaseBlock block = clipboard.getFullBlock(position);
                if (flags.skipAir() && block.getBlockType().getMaterial().isAir()) {
                    continue;
                }
                BlockVector3 at = position.add(offset);
                if (block.getNbtReference() != null) {
                    builder.addPattern(at);
                    data.put(PackedBlockPlan.pack(at.x(), at.y(), at.z()), block);
                } else {
                    builder.addOverride(at, block.toImmutableState());
                }
            }
            PackedBlockPlan plan = builder.build();
            if (plan.size() == 0) {
                throw new IllegalArgumentException("El portapapeles solo tiene aire");
            }
            int queued = data.isEmpty()
                    ? BatchEditService.submit(player, plan, "air", "Pegar")
                    : BatchEditService.submitStyled(player, plan,
                    position -> data.get(PackedBlockPlan.pack(position.x(), position.y(), position.z())), "Pegar");
            if (!PlanPreviews.holds(player.getUuid(), plan)) {
                QuietMode.status(player, Feedback.line(Feedback.Level.OK, Feedback.format(
                        "Pegando %s bloques · %s×%s×%s · //undo", queued, size.x(), size.y(), size.z())));
            }
            return 1;
        });
    }

    /** El portapapeles con su {@code //rotate} y {@code //flip} pendientes, como lo pegaría {@code //paste}. */
    private static Clipboard clipboard(ServerPlayerEntity player) {
        ClipboardHolder holder;
        try {
            holder = WorldEditService.context(player).session().getClipboard();
        } catch (EmptyClipboardException exception) {
            throw new IllegalArgumentException("El portapapeles está vacío: copia algo con //copy", exception);
        }
        Clipboard clipboard = holder.getClipboard();
        if (!holder.getTransform().isIdentity()) {
            try {
                clipboard = clipboard.transform(holder.getTransform());
            } catch (WorldEditException exception) {
                throw new IllegalArgumentException("No se pudo girar el portapapeles: " + exception.getMessage(),
                        exception);
            }
        }
        return clipboard;
    }
}
