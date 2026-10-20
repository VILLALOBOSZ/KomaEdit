package com.komainu.moxia.analysis;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.SnapshotJobs;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * {@code /komaedit analyze}: lee la selección repartida entre ticks, cuenta y calcula el coste en un worker, guarda
 * el CSV en {@code config/komaedit/exports/} (también en el worker) y muestra el resumen con un botón para copiar
 * la lista. No cambia ningún bloque.
 */
public final class AnalyzeService {
    /** Contar no cambia nada: va aparte de {@code komaedit.build}. */
    public static final String PERMISSION = "komaedit.analyze";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private AnalyzeService() {
    }

    public static Path exportsDirectory() {
        return KomaEditConfig.directory().resolve("exports");
    }

    private record Output(MaterialReport report, Path csv, String error) {
    }

    public static void start(ServerPlayerEntity player, String maskInput, int lines) throws IncompleteRegionException {
        KomaPermissions.require(player, PERMISSION);
        Region region = WorldEditService.selection(player);
        RegionMasks.Bound mask = maskInput == null ? null : RegionMasks.parse(player, maskInput);
        String name = player.getGameProfile().getName().replaceAll("[^A-Za-z0-9_]", "_");
        Path directory = exportsDirectory();
        SnapshotJobs.run(player, region, 0, "Lista de materiales", snapshot -> {
            if (mask != null) {
                mask.bind(snapshot);
            }
            List<MaterialReport.Counted> counted = MaterialReport.count(snapshot,
                    mask == null ? null : mask::test, () -> Thread.currentThread().isInterrupted());
            MaterialReport report = MaterialReport.build(counted, REGISTRY);
            Path file = directory.resolve("materiales-" + name + "-" + LocalDateTime.now().format(STAMP) + ".csv");
            try {
                Files.createDirectories(directory);
                Files.writeString(file, report.csv(), StandardCharsets.UTF_8);
                return new Output(report, file, null);
            } catch (IOException exception) {
                KomaEditMod.LOGGER.warn("Could not write material list {}", file, exception);
                return new Output(report, null, exception.getMessage());
            }
        }, (current, output) -> show(current, output, lines));
    }

    private static void show(ServerPlayerEntity player, Output output, int lines) {
        MaterialReport report = output.report();
        // La tarjeta Materiales del Studio enseña la lista entera.
        com.komainu.moxia.studio.net.StudioToolsNetwork.sendMaterials(player, report);
        if (report.blocks() == 0) {
            player.sendMessage(SnapshotJobs.message("Lista de materiales: la selección está vacía",
                    Formatting.YELLOW), false);
            return;
        }
        MutableText header = SnapshotJobs.message("Lista de materiales: " + report.blocks() + " bloques, "
                + report.placed().size() + " tipos · en survival " + report.totalItems() + " objetos, "
                + report.totalSlots() + " huecos ≈ " + report.chests() + " cofres ", Formatting.GREEN).copy();
        header.append(Text.literal("[COPIAR]").styled(style -> style.withColor(Formatting.AQUA).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, report.clipboard()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Text.literal("Copia la lista de materiales al portapapeles")))));
        player.sendMessage(header, false);
        int shown = 0;
        for (MaterialReport.Line line : report.cost()) {
            if (shown++ >= lines) {
                break;
            }
            player.sendMessage(Text.literal("  " + MaterialReport.display(line.item()) + ": ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(line.amount() + " (" + MaterialReport.stacks(line) + ")")
                            .formatted(Formatting.WHITE)), false);
        }
        if (report.cost().size() > lines) {
            player.sendMessage(Text.literal("  … y " + (report.cost().size() - lines) + " más en el CSV")
                    .formatted(Formatting.DARK_GRAY), false);
        }
        if (output.csv() != null) {
            String path = KomaEditConfig.directory().getParent().getParent().relativize(output.csv()).toString()
                    .replace('\\', '/');
            player.sendMessage(Text.literal("  CSV: " + path).formatted(Formatting.DARK_GRAY)
                    .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, path))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("Copia la ruta del archivo")))), false);
        } else {
            player.sendMessage(SnapshotJobs.message("No se pudo guardar el CSV: " + output.error(), Formatting.RED),
                    false);
        }
    }

    /** Los registros de Minecraft como {@link SurvivalCost.Registry}; solo se leen, así que vale desde el worker. */
    static final SurvivalCost.Registry REGISTRY = new SurvivalCost.Registry() {
        @Override
        public boolean isBlock(String id) {
            Identifier identifier = Identifier.tryParse(id);
            return identifier != null && Registries.BLOCK.containsId(identifier);
        }

        @Override
        public boolean isItem(String id) {
            Identifier identifier = Identifier.tryParse(id);
            return identifier != null && Registries.ITEM.containsId(identifier);
        }

        @Override
        public int maxStack(String itemId) {
            Identifier identifier = Identifier.tryParse(itemId);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                return 64;
            }
            return Registries.ITEM.get(identifier).getMaxCount();
        }
    };
}
