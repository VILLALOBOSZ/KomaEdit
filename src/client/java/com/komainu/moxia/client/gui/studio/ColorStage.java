package com.komainu.moxia.client.gui.studio;

import com.komainu.moxia.color.BlockColor;
import com.komainu.moxia.color.BlockColorCatalog;
import com.komainu.moxia.color.BlockColorCatalogs;
import com.komainu.moxia.color.ColorFilter;
import com.komainu.moxia.color.ColorGradient;
import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import static com.komainu.moxia.client.gui.studio.StudioTheme.ACCENT;
import static com.komainu.moxia.client.gui.studio.StudioTheme.CARD;
import static com.komainu.moxia.client.gui.studio.StudioTheme.CARD_HOVER;
import static com.komainu.moxia.client.gui.studio.StudioTheme.MUTED;
import static com.komainu.moxia.client.gui.studio.StudioTheme.TEXT;
import static com.komainu.moxia.client.gui.studio.StudioTheme.TEXT_SECONDARY;

/**
 * Etapa «Color» del Studio: degradado entre dos bloques con sus muestras, los parecidos al primero y el sombreado de
 * la selección. Calcula en el cliente con el mismo catálogo que el servidor ({@link BlockColorCatalogs}) y deja el
 * trabajo a las órdenes de siempre: {@code /komaedit color hotbar}, {@code color gradient} y {@code shade}. Guarda
 * su estado entre aperturas del Studio.
 */
public final class ColorStage {
    /** Lo que la etapa necesita de la pantalla. */
    public interface Host {
        String tr(String key, Object... arguments);

        void send(String command);

        /** Abre el catálogo de bloques y entrega el elegido. */
        void pickBlock(Consumer<String> onPick);

        void status(String text);
    }

    private static final List<String> LIGHTS = List.of("sol", "jugador", "mirada", "arriba", "ne", "no", "se", "so");
    private static final List<String> PALETTES = List.of("selection", "rampa", "gradient");
    private static final int MIN_STEPS = 2;
    private static final int MAX_STEPS = 16;
    private static final int SIMILAR = 8;
    private static final int SWATCH = 22;

    private String from = "minecraft:deepslate";
    private String to = "minecraft:calcite";
    private int steps = 7;
    private int variant;
    private boolean cubes = true;
    private boolean anything;
    private int light;
    private int palette;
    private int dither = 40;
    private String gradientKey = "";
    private List<BlockColor> gradient = List.of();
    private String gradientError = "";
    private String similarKey = "";
    private List<BlockColorCatalog.Match> similar = List.of();

    public void draw(StudioUi ui, Column column, Host host) {
        ui.section(column, host.tr("gui.komaedit.color.gradient"));
        Rect[] ends = column.row(2, 30);
        blockCard(ui, ends[0], from, host.tr("gui.komaedit.color.from"), host, id -> {
            from = id;
            variant = 0;
        });
        blockCard(ui, ends[1], to, host.tr("gui.komaedit.color.to"), host, id -> {
            to = id;
            variant = 0;
        });
        ui.cycle(column.next(), host.tr("gui.komaedit.color.steps"), Integer.toString(steps), direction -> {
            steps = Math.clamp(steps + direction, MIN_STEPS, MAX_STEPS);
            variant = 0;
        }).tooltip(host.tr("gui.komaedit.help.color.steps"));
        ui.toggle(column.next(), host.tr("gui.komaedit.color.cubes"), cubes)
                .tooltip(host.tr("gui.komaedit.help.color.cubes"))
                .onClick(() -> cubes = !cubes);
        ui.toggle(column.next(), host.tr("gui.komaedit.color.anything"), anything)
                .tooltip(host.tr("gui.komaedit.help.color.anything"))
                .onClick(() -> anything = !anything);

        List<BlockColor> blocks = gradient();
        if (blocks.isEmpty()) {
            ui.paragraph(column, gradientError, MUTED, 3);
        } else {
            swatches(ui, column, blocks, host);
            Rect[] actions = column.row(3, StudioUi.ROW);
            ui.button(actions[0], host.tr("gui.komaedit.color.to_hotbar"), StudioUi.Tone.PRIMARY)
                    .tooltip(host.tr("gui.komaedit.help.color.to_hotbar"))
                    .onClick(() -> {
                        host.send("komaedit color hotbar " + ids(blocks));
                        host.status(host.tr("gui.komaedit.status.color.hotbar", blocks.size()));
                    });
            ui.button(actions[1], host.tr("gui.komaedit.color.another"), StudioUi.Tone.SECONDARY)
                    .tooltip(host.tr("gui.komaedit.help.color.another"))
                    .onClick(() -> variant++);
            ui.button(actions[2], host.tr("gui.komaedit.color.to_chat"), StudioUi.Tone.SECONDARY)
                    .tooltip(host.tr("gui.komaedit.help.color.to_chat"))
                    .onClick(() -> host.send(gradientCommand()));
        }

        ui.section(column, host.tr("gui.komaedit.color.similar", shortName(from)));
        List<BlockColorCatalog.Match> matches = similar();
        swatches(ui, column, matches.stream().map(BlockColorCatalog.Match::block).toList(), host);

        ui.section(column, host.tr("gui.komaedit.color.shade"));
        ui.cycle(column.next(), host.tr("gui.komaedit.color.light"),
                host.tr("gui.komaedit.color.light." + LIGHTS.get(light)),
                direction -> light = Math.floorMod(light + direction, LIGHTS.size()))
                .tooltip(host.tr("gui.komaedit.help.color.light"));
        ui.cycle(column.next(), host.tr("gui.komaedit.color.palette"),
                host.tr("gui.komaedit.color.palette." + PALETTES.get(palette)),
                direction -> palette = Math.floorMod(palette + direction, PALETTES.size()))
                .tooltip(host.tr("gui.komaedit.help.color.palette"));
        ui.cycle(column.next(), host.tr("gui.komaedit.color.dither"), dither + " %",
                direction -> dither = Math.clamp(dither + direction * 10, 0, 100))
                .tooltip(host.tr("gui.komaedit.help.color.dither"));
        ui.paragraph(column, host.tr("gui.komaedit.color.shade_hint"), TEXT_SECONDARY, 0);
        ui.button(column.next(), host.tr("gui.komaedit.color.shade_run"), StudioUi.Tone.PRIMARY).onClick(() -> {
            host.send(shadeCommand());
            host.status(host.tr("gui.komaedit.status.color.shading"));
        });
    }

    /** La orden de sombreado con lo elegido en la etapa. */
    String shadeCommand() {
        String source = switch (PALETTES.get(palette)) {
            case "rampa" -> "rampa ";
            case "gradient" -> gradient.isEmpty() ? "" : ids(gradient) + " ";
            default -> "";
        };
        return "komaedit shade " + source + "-l " + LIGHTS.get(light) + " -d "
                + String.format(Locale.ROOT, "%.1f", dither / 100.0);
    }

    private String gradientCommand() {
        String flags = filter().flags();
        return "komaedit color gradient " + shortName(from) + " " + shortName(to) + " " + steps
                + (flags.isEmpty() ? "" : " " + flags) + (variant > 0 ? " -v " + variant : "");
    }

    private ColorFilter filter() {
        return ColorFilter.of(cubes, cubes, cubes, false, anything);
    }

    private List<BlockColor> gradient() {
        String key = from + "|" + to + "|" + steps + "|" + variant + "|" + cubes + "|" + anything;
        if (!key.equals(gradientKey)) {
            gradientKey = key;
            try {
                BlockColorCatalog catalog = BlockColorCatalogs.get();
                gradient = ColorGradient.between(catalog, ColorGradient.Anchor.of(catalog.require(from)),
                        ColorGradient.Anchor.of(catalog.require(to)), steps, filter(), variant);
                gradientError = "";
            } catch (IllegalArgumentException exception) {
                gradient = List.of();
                gradientError = exception.getMessage();
            }
        }
        return gradient;
    }

    private List<BlockColorCatalog.Match> similar() {
        String key = from + "|" + cubes + "|" + anything;
        if (!key.equals(similarKey)) {
            similarKey = key;
            BlockColorCatalog catalog = BlockColorCatalogs.get();
            BlockColor block = catalog.get(from);
            similar = block == null ? List.of() : catalog.similar(block.lab(), SIMILAR, filter(), Set.of(block.id()));
        }
        return similar;
    }

    private static void blockCard(StudioUi ui, Rect card, String id, String caption, Host host,
                                  Consumer<String> onPick) {
        boolean hovered = ui.hovered(card);
        ui.fill(card, hovered ? CARD_HOVER : CARD);
        BlockColor color = BlockColorCatalogs.get().get(id);
        if (color != null) {
            ui.fill(new Rect(card.x(), card.y(), 3, card.height()), 0xFF000000 | color.rgb());
        }
        ui.item(stack(id), card.x() + 7, card.y() + 7);
        ui.label(caption, card.x() + 27, card.y() + 5, card.width() - 31, MUTED);
        ui.label(shortName(id), card.x() + 27, card.y() + 16, card.width() - 31, TEXT);
        ui.register(card).tooltip(host.tr("gui.komaedit.help.color.pick")).onClick(() -> host.pickBlock(onPick));
    }

    /** Fila de muestras: el color arriba y el bloque debajo; un clic lo lleva a la mano. */
    private static void swatches(StudioUi ui, Column column, List<BlockColor> blocks, Host host) {
        int perRow = Math.max(1, (column.width() + Column.GAP) / (SWATCH + 2));
        for (int start = 0; start < blocks.size(); start += perRow) {
            Rect row = column.next(SWATCH + 6);
            for (int index = start; index < Math.min(blocks.size(), start + perRow); index++) {
                BlockColor block = blocks.get(index);
                Rect cell = new Rect(row.x() + (index - start) * (SWATCH + 2), row.y(), SWATCH, SWATCH + 6);
                boolean hovered = ui.hovered(cell);
                ui.fill(cell, hovered ? CARD_HOVER : CARD);
                ui.fill(new Rect(cell.x(), cell.y(), cell.width(), 5), 0xFF000000 | block.rgb());
                if (hovered) {
                    ui.fill(new Rect(cell.x(), cell.bottom() - 1, cell.width(), 1), ACCENT);
                }
                ui.item(stack(block.id()), cell.x() + 3, cell.y() + 8);
                ui.register(cell)
                        .tooltip(block.shortName() + "\n" + block.hex() + " · " + block.describeFlags() + "\n"
                                + host.tr("gui.komaedit.help.color.swatch"))
                        .onClick(() -> host.send("komaedit color hotbar " + block.shortName()));
            }
        }
    }

    private static String ids(List<BlockColor> blocks) {
        return String.join(",", blocks.stream().map(BlockColor::shortName).toList());
    }

    private static String shortName(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static ItemStack stack(String id) {
        Identifier identifier = Identifier.tryParse(id);
        ItemStack stack = identifier == null ? ItemStack.EMPTY
                : Registries.BLOCK.get(identifier).asItem().getDefaultStack();
        return stack.isEmpty() ? Items.BARRIER.getDefaultStack() : stack;
    }
}
