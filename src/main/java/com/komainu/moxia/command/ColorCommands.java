package com.komainu.moxia.command;

import com.komainu.moxia.color.BlockColor;
import com.komainu.moxia.color.BlockColorCatalog;
import com.komainu.moxia.color.BlockColorCatalogs;
import com.komainu.moxia.color.ColorFilter;
import com.komainu.moxia.color.ColorGradient;
import com.komainu.moxia.color.OkLab;
import com.komainu.moxia.color.ShadeLight;
import com.komainu.moxia.color.ShadeOperation;
import com.komainu.moxia.gradient.BlockSpec;
import com.komainu.moxia.gradient.PaletteSpec;
import com.komainu.moxia.gradient.SavedPalettes;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.NamedDefinitions;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.texture.TextureContext;
import com.komainu.moxia.worldedit.WorldEditService;
import com.komainu.moxia.worldedit.ez.EzContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Elegir y sombrear colores, como el Colour Picker, el Gradient Helper y el Autoshade de Axiom:
 * {@code /komaedit color similar|gradient|palette|hotbar} y {@code /komaedit shade}. Las búsquedas usan
 * {@link BlockColorCatalogs} (OKLab) y enseñan cada bloque con su muestra de color en el chat; un clic en la muestra
 * lo pone en la barra rápida (solo en creativo).
 */
public final class ColorCommands {
    static final String SIMILAR = "komaedit.color.similar";
    static final String GRADIENT = "komaedit.color.gradient";
    static final String PALETTE = "komaedit.color.palette";
    static final String HOTBAR = "komaedit.color.hotbar";
    static final String SHADE = "shade";
    private static final int DEFAULT_SIMILAR = 8;
    private static final int MAX_SIMILAR = 32;
    private static final int DEFAULT_STEPS = 7;
    private static final int DEFAULT_RAMP = 7;
    private static final int HOTBAR_SLOTS = 9;
    private static final String SWATCH = "█";
    private static final SplittableRandom SEEDS = new SplittableRandom();
    /** La barra rápida de antes del último cambio de cada jugador, para {@code hotbar undo}. */
    private static final Map<UUID, List<ItemStack>> PREVIOUS_HOTBARS = new ConcurrentHashMap<>();

    private ColorCommands() {
    }

    /** El jugador se fue: su barra de antes ya no se puede recuperar. */
    public static void forget(UUID player) {
        PREVIOUS_HOTBARS.remove(player);
    }

    public static void clearAll() {
        PREVIOUS_HOTBARS.clear();
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(color("color").build());
        root.addChild(color("colour").build());
        root.addChild(shade("shade").build());
        root.addChild(shade("sombrear").build());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> color(String name) {
        return literal(name)
                .requires(source -> EzCommandSupport.canAny(source, "komaedit.color"))
                .executes(ColorCommands::help)
                .then(literal("similar").requires(EzCommandSupport.node(SIMILAR))
                        .executes(context -> similar(context, ""))
                        .then(argument("args", StringArgumentType.greedyString())
                                .executes(context -> similar(context, StringArgumentType.getString(context, "args")))))
                .then(literal("parecidos").requires(EzCommandSupport.node(SIMILAR))
                        .executes(context -> similar(context, ""))
                        .then(argument("args", StringArgumentType.greedyString())
                                .executes(context -> similar(context, StringArgumentType.getString(context, "args")))))
                .then(literal("gradient").requires(EzCommandSupport.node(GRADIENT))
                        .then(argument("args", StringArgumentType.greedyString()).executes(ColorCommands::gradient)))
                .then(literal("degradado").requires(EzCommandSupport.node(GRADIENT))
                        .then(argument("args", StringArgumentType.greedyString()).executes(ColorCommands::gradient)))
                .then(literal("palette").requires(EzCommandSupport.node(PALETTE))
                        .then(argument("args", StringArgumentType.greedyString())
                                .suggests(EzCommandSupport.words(List.of("selection", "hotbar")))
                                .executes(ColorCommands::palette)))
                .then(literal("paleta").requires(EzCommandSupport.node(PALETTE))
                        .then(argument("args", StringArgumentType.greedyString())
                                .suggests(EzCommandSupport.words(List.of("seleccion", "barra")))
                                .executes(ColorCommands::palette)))
                .then(literal("hotbar").requires(EzCommandSupport.node(HOTBAR))
                        .then(argument("args", StringArgumentType.greedyString()).executes(ColorCommands::hotbar)))
                .then(literal("barra").requires(EzCommandSupport.node(HOTBAR))
                        .then(argument("args", StringArgumentType.greedyString()).executes(ColorCommands::hotbar)));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> shade(String name) {
        return literal(name)
                .requires(EzCommandSupport.node(KomaPermissions.BUILD + "." + SHADE))
                .executes(context -> shade(context, ""))
                .then(argument("args", StringArgumentType.greedyString())
                        .suggests(EzCommandSupport.words(List.of("selection", "rampa", "##", "-l")))
                        .executes(context -> shade(context, StringArgumentType.getString(context, "args"))));
    }

    private static int help(CommandContext<ServerCommandSource> context) {
        String[] lines = {
                "/komaedit color similar [bloque|#hex] [n] [-c] [-s] [-o] [-u]: los n bloques de color más parecido",
                "/komaedit color gradient <A> <B> [pasos] [filtros] [-v variante] [-p nombre]: degradado en OKLab",
                "/komaedit color palette <selection|hotbar|paleta> [nombre] [-f]: paleta de oscuro a claro",
                "/komaedit color hotbar <bloques|undo>: a la barra rápida (creativo)",
                "/komaedit shade [selection|rampa|paleta] [-l sol|jugador|mirada|foco|ne…] [-a 0–1] [-o 0–1] [-d 0–1]",
                "Filtros: -c cubo entero, -s sólido, -o opaco, -u misma textura en todas las caras, -x también"
                        + " menas, máquinas y bloques con luz",
                "Clic en una muestra ███: a la barra rápida · Ej.: /komaedit color gradient deepslate calcite 7"
        };
        for (String line : lines) {
            context.getSource().sendFeedback(() -> Text.literal(line).formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    /** {@code similar [bloque|#hex] [n] [filtros]}: sin bloque, el de la mano o el que miras. */
    private static int similar(CommandContext<ServerCommandSource> context, String args) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CommandTokens tokens = CommandTokens.parse(args, Set.of()).allow("c", "s", "o", "u", "x").atMost(2);
            BlockColorCatalog catalog = BlockColorCatalogs.get();
            ColorFilter filter = filter(tokens);
            String reference = tokens.at(0, null);
            int count = DEFAULT_SIMILAR;
            if (reference != null && reference.matches("\\d+") && tokens.positional().size() == 1) {
                count = Integer.parseInt(reference);
                reference = null;
            } else if (tokens.positional().size() > 1) {
                count = parseCount(tokens.positional().get(1), MAX_SIMILAR, "la cantidad");
            }
            count = Math.clamp(count, 1, MAX_SIMILAR);
            Reference target = reference == null ? held(player, catalog) : reference(reference, catalog);
            Set<String> exclude = target.block() == null ? Set.of() : Set.of(target.block().id());
            List<BlockColorCatalog.Match> matches = catalog.similar(target.lab(), count, filter, exclude);
            if (matches.isEmpty()) {
                throw new IllegalArgumentException("Ningún bloque pasa el filtro (" + filter.describe() + ")");
            }
            String flags = filter.flags().isEmpty() ? "" : " " + filter.flags();
            MutableText header = Text.literal("Parecidos a ").formatted(Formatting.YELLOW)
                    .append(target.swatch()).append(Text.literal(" " + target.label()).formatted(Formatting.WHITE))
                    .append(Text.literal(" · " + filter.describe() + " ").formatted(Formatting.GRAY));
            List<String> ids = matches.stream().map(match -> match.block().shortName()).toList();
            header.append(EzCommandSupport.button("[todo a la barra]", "/komaedit color hotbar "
                    + String.join(",", sample(ids, HOTBAR_SLOTS)), Formatting.GREEN));
            EzCommandSupport.send(player, header);
            for (BlockColorCatalog.Match match : matches) {
                BlockColor block = match.block();
                MutableText line = Text.literal("  ").append(swatch(block, 2)).append(Text.literal(" "))
                        .append(EzCommandSupport.copyable(block.shortName(), block.shortName(), Formatting.WHITE))
                        .append(Text.literal(String.format(Locale.ROOT, "  Δ%.1f ", match.distance() * 100.0))
                                .formatted(Formatting.DARK_GRAY))
                        .append(EzCommandSupport.button("[barra]", "/komaedit color hotbar " + block.shortName(),
                                Formatting.GREEN))
                        .append(Text.literal(" "))
                        .append(EzCommandSupport.button("[parecidos]", "/komaedit color similar " + block.shortName()
                                + " " + matches.size() + flags, Formatting.AQUA));
                if (target.block() != null) {
                    line.append(Text.literal(" ")).append(EzCommandSupport.button("[degradado]",
                            "/komaedit color gradient " + target.label() + " " + block.shortName() + flags,
                            Formatting.LIGHT_PURPLE));
                }
                player.sendMessage(line, false);
            }
            return 1;
        });
    }

    /** {@code gradient <A> <B> [pasos] [filtros] [-v variante] [-p nombre] [-f]}. */
    private static int gradient(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CommandTokens tokens = CommandTokens.parse(StringArgumentType.getString(context, "args"), Set.of("v", "p"))
                    .allow("c", "s", "o", "u", "x", "v", "p", "f").atMost(3);
            BlockColorCatalog catalog = BlockColorCatalogs.get();
            ColorFilter filter = filter(tokens);
            Reference from = reference(tokens.require(0, "el bloque o color de inicio"), catalog);
            Reference to = reference(tokens.require(1, "el bloque o color final"), catalog);
            int steps = parseCount(tokens.at(2, String.valueOf(DEFAULT_STEPS)), ColorGradient.MAX_STEPS, "los pasos");
            int variant = parseCount(tokens.option("v", "0"), 1_000, "la variante");
            List<BlockColor> blocks = ColorGradient.between(catalog, from.anchor(), to.anchor(), Math.max(2, steps),
                    filter, variant);
            List<String> ids = blocks.stream().map(BlockColor::shortName).toList();
            String joined = String.join(",", ids);
            String savedName = tokens.option("p", null);
            if (savedName != null) {
                String name = NamedDefinitions.normalizeName(savedName);
                boolean replaced = SavedPalettes.store(player.getUuid()).put(name, PaletteSpec.join(ids),
                        tokens.flag("f"));
                EzCommandSupport.ok(player, (replaced ? "Paleta sustituida: #" : "Paleta guardada: #") + name);
            }
            String flags = filter.flags().isEmpty() ? "" : " " + filter.flags();
            String again = "/komaedit color gradient " + from.label() + " " + to.label() + " ";
            MutableText header = Text.literal("Degradado ").formatted(Formatting.YELLOW)
                    .append(from.swatch()).append(Text.literal(" " + from.label() + " → ").formatted(Formatting.WHITE))
                    .append(to.swatch()).append(Text.literal(" " + to.label()).formatted(Formatting.WHITE))
                    .append(Text.literal(" · " + blocks.size() + " pasos, " + filter.describe()
                            + (variant > 0 ? ", variante " + variant : "")).formatted(Formatting.GRAY));
            EzCommandSupport.send(player, header);
            player.sendMessage(swatches(blocks, 3), false);
            player.sendMessage(Text.literal("  ")
                    .append(EzCommandSupport.copyable(String.join(", ", ids), joined, Formatting.WHITE)), false);
            player.sendMessage(actions(ids, joined)
                    .append(Text.literal(" "))
                    .append(EzCommandSupport.button("[otra]", again + blocks.size() + flags + " -v " + (variant + 1),
                            Formatting.AQUA))
                    .append(Text.literal(" "))
                    .append(EzCommandSupport.button("[más pasos]", again
                            + Math.min(ColorGradient.MAX_STEPS, blocks.size() + 2) + flags, Formatting.AQUA)), false);
            return 1;
        });
    }

    /** {@code palette <selection|hotbar|paleta> [nombre] [-f]}: los materiales ordenados de oscuro a claro. */
    private static int palette(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CommandTokens tokens = CommandTokens.parse(StringArgumentType.getString(context, "args"), Set.of())
                    .allow("f").atMost(2);
            String source = tokens.require(0, "de dónde sacar la paleta: selection, hotbar o una paleta");
            String name = tokens.positional().size() > 1 ? NamedDefinitions.normalizeName(tokens.positional().get(1))
                    : null;
            boolean force = tokens.flag("f");
            BlockColorCatalog catalog = BlockColorCatalogs.get();
            switch (source.toLowerCase(Locale.ROOT)) {
                case "selection", "seleccion", "selección", "sel" -> {
                    Region selection = WorldEditService.selection(player);
                    UUID playerId = player.getUuid();
                    MinecraftServer server = player.getServer();
                    RegionOperationService.capture(player, selection, 0, "Paleta de la selección", snapshot -> {
                        ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                        if (current == null) {
                            return;
                        }
                        try {
                            List<String> ids = selectionMaterials(snapshot, catalog);
                            showPalette(current, catalog, ids, name, force, "de la selección");
                        } catch (RuntimeException exception) {
                            current.sendMessage(EzCommandSupport.prefix().append(Text.literal(exception.getMessage())
                                    .formatted(Formatting.RED)), false);
                        }
                    });
                }
                case "hotbar", "barra" -> showPalette(player, catalog, hotbarBlocks(player), name, force,
                        "de la barra rápida");
                default -> {
                    List<String> ids = new ArrayList<>();
                    for (String entry : SavedPalettes.parse(player.getUuid(), source)) {
                        ids.add(BlockSpec.parse(entry).id());
                    }
                    showPalette(player, catalog, ids, name, force, source);
                }
            }
            return 1;
        });
    }

    /** {@code hotbar <bloques|undo>}: pone los bloques en la barra rápida; uno solo va a la casilla elegida. */
    private static int hotbar(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            if (!player.isCreative()) {
                throw new IllegalArgumentException("Llevar bloques a la barra rápida solo se puede en creativo");
            }
            String text = StringArgumentType.getString(context, "args").trim();
            if (text.equalsIgnoreCase("undo") || text.equalsIgnoreCase("deshacer")) {
                List<ItemStack> previous = PREVIOUS_HOTBARS.remove(player.getUuid());
                if (previous == null) {
                    throw new IllegalArgumentException("No hay ninguna barra rápida que recuperar");
                }
                for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
                    player.getInventory().setStack(slot, previous.get(slot));
                }
                player.currentScreenHandler.sendContentUpdates();
                EzCommandSupport.ok(player, "Barra rápida recuperada");
                return 1;
            }
            List<Item> items = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            for (String entry : SavedPalettes.parse(player.getUuid(), text)) {
                String id = BlockSpec.parse(entry).id();
                Item item = item(id);
                if (item == null) {
                    skipped.add(entry);
                } else {
                    items.add(item);
                }
            }
            if (items.isEmpty()) {
                throw new IllegalArgumentException("Ninguno de esos bloques tiene objeto: " + String.join(", ", skipped));
            }
            items = sample(items, HOTBAR_SLOTS);
            List<ItemStack> previous = new ArrayList<>(HOTBAR_SLOTS);
            for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
                previous.add(player.getInventory().getStack(slot).copy());
            }
            PREVIOUS_HOTBARS.put(player.getUuid(), previous);
            if (items.size() == 1) {
                player.getInventory().setStack(player.getInventory().selectedSlot, new ItemStack(items.getFirst()));
            } else {
                for (int slot = 0; slot < items.size(); slot++) {
                    player.getInventory().setStack(slot, new ItemStack(items.get(slot)));
                }
            }
            player.currentScreenHandler.sendContentUpdates();
            MutableText message = Text.literal(items.size() == 1 ? "En la mano. " : items.size()
                    + " bloques en la barra rápida. ").formatted(Formatting.GREEN)
                    .append(EzCommandSupport.button("[deshacer]", "/komaedit color hotbar undo", Formatting.GRAY));
            if (!skipped.isEmpty()) {
                message.append(Text.literal(" Sin objeto: " + String.join(", ", skipped)).formatted(Formatting.GRAY));
            }
            EzCommandSupport.send(player, message);
            return 1;
        });
    }

    /**
     * {@code shade [selection|rampa|paleta] [-l luz] [-a oclusión] [-r radio] [-o sombras] [-d tramado]
     * [-m máscara] [-n tamaño de la rampa] [-k]}.
     */
    private static int shade(CommandContext<ServerCommandSource> context, String args) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CommandTokens tokens = CommandTokens.parse(args, Set.of("l", "a", "r", "o", "d", "m", "n"))
                    .allow("l", "a", "r", "o", "d", "m", "n", "k").atMost(1);
            BlockColorCatalog catalog = BlockColorCatalogs.get();
            Region selection = WorldEditService.selection(player);
            String source = tokens.at(0, "selection");
            int rampSize = Math.clamp(parseCount(tokens.option("n", String.valueOf(DEFAULT_RAMP)), 32, "la rampa"), 3,
                    16);
            ShadeOperation.Shading shading = new ShadeOperation.Shading(
                    fraction(tokens.option("a", "0.6"), "la oclusión ambiental (-a)"),
                    parseCount(tokens.option("r", "3"), 8, "el radio de oclusión (-r)"),
                    fraction(tokens.option("o", "0.8"), "las sombras (-o)"),
                    fraction(tokens.option("d", "0"), "el tramado (-d)"));
            ShadeLight light = ShadeLight.resolve(tokens.option("l", "sol"), scene(player, selection));
            RegionMasks.Bound mask = RegionMasks.parse(player, tokens.option("m", "*"));
            List<Pattern> palette = null;
            double[] pivot = {Double.NaN};
            boolean forceRamp = false;
            String paletteLabel;
            switch (source.toLowerCase(Locale.ROOT)) {
                case "selection", "seleccion", "selección", "sel" -> paletteLabel = "la de la selección";
                case "rampa", "ramp" -> {
                    forceRamp = true;
                    paletteLabel = "rampa del material principal";
                }
                default -> {
                    palette = explicitPalette(player, catalog, source, rampSize, tokens.flag("k"), pivot);
                    paletteLabel = source;
                }
            }
            TextureContext.Settings settings = TextureContext.Settings.at(player.getX(), player.getEyeY(),
                    player.getZ(), SEEDS.nextLong(1L, 1_000_000_000L));
            ShadeOperation operation = new ShadeOperation(mask, palette, pivot[0], forceRamp, rampSize, light, shading,
                    catalog, settings, notice(player.getServer(), player.getUuid()));
            checkVolume(selection, operation.padding());
            RegionOperationService.submit(player, operation.padding(), "Sombreado (" + light.label() + ", paleta "
                    + paletteLabel + ")", operation);
            return 1;
        });
    }

    /**
     * Una paleta escrita, de oscuro a claro (o en su orden con {@code -k}); un solo bloque da su rampa.
     *
     * @param pivot recibe el sitio del bloque base si sale una rampa
     */
    private static List<Pattern> explicitPalette(ServerPlayerEntity player, BlockColorCatalog catalog, String input,
                                                 int rampSize, boolean keepOrder, double[] pivot) {
        List<String> entries = SavedPalettes.parse(player.getUuid(), input);
        WorldEditService.PlayerContext worldEdit = WorldEditService.context(player);
        if (entries.size() == 1) {
            BlockColor base = catalog.require(BlockSpec.parse(entries.getFirst()).id());
            List<BlockColor> ramp = ColorGradient.ramp(catalog, base, rampSize, ColorFilter.BUILDING);
            pivot[0] = ramp.indexOf(base) / (ramp.size() - 1.0);
            entries = ramp.stream().map(BlockColor::shortName).toList();
            EzCommandSupport.info(player, "Rampa de " + base.shortName() + ": " + String.join(", ", entries));
        }
        List<Pattern> patterns = new ArrayList<>(entries.size());
        List<Double> lightness = new ArrayList<>(entries.size());
        for (String entry : entries) {
            Pattern pattern = WorldEditService.parsePatternUnchecked(worldEdit, entry);
            patterns.add(pattern);
            BlockColor block = catalog.get(catalog.base(pattern.applyBlock(BlockVector3.ZERO).getBlockType().id()));
            lightness.add(block == null ? 0.5 : block.l());
        }
        if (keepOrder) {
            return patterns;
        }
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < patterns.size(); index++) {
            order.add(index);
        }
        order.sort((first, second) -> Double.compare(lightness.get(first), lightness.get(second)));
        return order.stream().map(patterns::get).toList();
    }

    private static ShadeLight.Scene scene(ServerPlayerEntity player, Region selection) {
        BlockVector3 minimum = selection.getMinimumPoint();
        BlockVector3 maximum = selection.getMaximumPoint();
        double[] center = {(minimum.x() + maximum.x() + 1) / 2.0, (minimum.y() + maximum.y() + 1) / 2.0,
                (minimum.z() + maximum.z() + 1) / 2.0};
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        return new ShadeLight.Scene(player.getServerWorld().getSkyAngle(1.0f), new double[]{eye.x, eye.y, eye.z},
                new double[]{look.x, look.y, look.z}, center);
    }

    private static void checkVolume(Region selection, int padding) {
        BlockVector3 size = selection.getMaximumPoint().subtract(selection.getMinimumPoint());
        int margin = Math.clamp(padding, 0, 32);
        long volume = (long) (size.x() + 1 + 2 * margin) * (size.y() + 1 + 2 * margin) * (size.z() + 1 + 2 * margin);
        if (volume > TextureContext.MAX_VOLUME) {
            throw new IllegalArgumentException("La selección es demasiado grande para sombrear (" + ProgressLine.group(volume)
                    + " bloques con su margen de " + margin + "; máximo " + TextureContext.MAX_VOLUME + ")");
        }
    }

    private static Consumer<String> notice(MinecraftServer server, UUID playerId) {
        return text -> server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                EzCommandSupport.send(player, Text.literal("Sombreado: " + text).formatted(Formatting.YELLOW));
            }
        });
    }

    /** Materiales enteros de la selección (las escaleras cuentan como su bloque), los más abundantes primero. */
    private static List<String> selectionMaterials(RegionSnapshot snapshot, BlockColorCatalog catalog) {
        Map<String, Integer> counts = new HashMap<>();
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    if (!snapshot.selected(x, y, z) || snapshot.isAir(x, y, z) || snapshot.isLiquid(x, y, z)) {
                        continue;
                    }
                    counts.merge(catalog.base(snapshot.blockId(x, y, z)), 1, Integer::sum);
                }
            }
        }
        if (counts.isEmpty()) {
            throw new IllegalArgumentException("La selección está vacía");
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(PaletteSpec.MAX_ENTRIES)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static List<String> hotbarBlocks(ServerPlayerEntity player) {
        List<String> ids = new ArrayList<>();
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.getItem() instanceof BlockItem blockItem) {
                ids.add(Registries.BLOCK.getId(blockItem.getBlock()).toString());
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("No llevas bloques en la barra rápida");
        }
        return ids;
    }

    /** Ordena de oscuro a claro, la enseña con sus muestras y la guarda si se dio nombre. */
    private static void showPalette(ServerPlayerEntity player, BlockColorCatalog catalog, List<String> ids,
                                    String name, boolean force, String from) {
        List<BlockColor> known = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            BlockColor block = catalog.get(id);
            if (block == null) {
                unknown.add(id);
            } else if (seen.add(block.id())) {
                known.add(block);
            }
        }
        if (known.isEmpty()) {
            throw new IllegalArgumentException("No conozco el color de ninguno de esos bloques");
        }
        List<BlockColor> sorted = BlockColorCatalog.byLightness(known);
        List<String> names = sorted.stream().map(BlockColor::shortName).toList();
        String joined = String.join(",", names);
        if (name != null) {
            boolean replaced = SavedPalettes.store(player.getUuid()).put(name, PaletteSpec.join(names), force);
            EzCommandSupport.ok(player, (replaced ? "Paleta sustituida: #" : "Paleta guardada: #") + name + " ("
                    + ProgressLine.group(names.size()) + " bloques, de oscuro a claro)");
        }
        EzCommandSupport.send(player, Text.literal("Paleta " + from + ", de oscuro a claro (" + ProgressLine.group(names.size())
                + " bloques):").formatted(Formatting.YELLOW));
        player.sendMessage(swatches(sorted, sorted.size() > 16 ? 1 : 2), false);
        player.sendMessage(Text.literal("  ")
                .append(EzCommandSupport.copyable(String.join(", ", names), joined, Formatting.WHITE)), false);
        player.sendMessage(actions(names, joined), false);
        if (!unknown.isEmpty()) {
            EzCommandSupport.info(player, "Sin color conocido (fuera de la paleta): " + String.join(", ", unknown));
        }
    }

    /** {@code [a la barra] [guardar] [guardar barra] [copiar]} de una lista de bloques. */
    private static MutableText actions(List<String> ids, String joined) {
        return Text.literal("  ")
                .append(EzCommandSupport.button("[a la barra]", "/komaedit color hotbar "
                        + String.join(",", sample(ids, HOTBAR_SLOTS)), Formatting.GREEN))
                .append(Text.literal(" "))
                .append(suggest("[guardar]", "/komaedit palette save nombre " + joined, Formatting.YELLOW))
                .append(Text.literal(" "))
                .append(suggest("[guardar barra]", "/komaedit hotbar palette nombre " + joined + " -l",
                        Formatting.YELLOW))
                .append(Text.literal(" "))
                .append(EzCommandSupport.copyable("[copiar]", joined, Formatting.AQUA));
    }

    private static MutableText swatches(List<BlockColor> blocks, int width) {
        MutableText line = Text.literal("  ");
        for (BlockColor block : blocks) {
            line.append(swatch(block, width));
        }
        return line;
    }

    /** Muestra de color: al pasar el ratón dice qué bloque es; al pulsarla lo pone en la mano. */
    static MutableText swatch(BlockColor block, int width) {
        Text hover = Text.literal(block.shortName()).formatted(Formatting.WHITE)
                .append(Text.literal("\n" + block.hex() + String.format(Locale.ROOT, " · luz %.0f %%", block.l() * 100))
                        .formatted(Formatting.GRAY))
                .append(Text.literal("\n" + block.describeFlags()).formatted(Formatting.DARK_GRAY))
                .append(Text.literal("\nClic: a la barra rápida").formatted(Formatting.GREEN));
        return Text.literal(SWATCH.repeat(width)).styled(style -> style
                .withColor(TextColor.fromRgb(block.rgb()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/komaedit color hotbar "
                        + block.shortName())));
    }

    private static MutableText suggest(String label, String command, Formatting color) {
        return Text.literal(label).styled(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Text.literal("Escribe la orden; cambia «nombre» por el que quieras"))));
    }

    /** Un bloque, un {@code #hex}, {@code mano} o {@code mira}. */
    static Reference reference(String input, BlockColorCatalog catalog) {
        if (OkLab.isHex(input)) {
            int rgb = OkLab.parseHex(input);
            return new Reference(null, rgb, OkLab.of(rgb));
        }
        String id = BlockSpec.parse(input).id();
        BlockColor block = catalog.get(id);
        if (block == null) {
            // Una pieza sin color propio (de un mod) usa el de su bloque entero.
            block = catalog.require(catalog.base(id));
        }
        return new Reference(block, block.rgb(), block.lab());
    }

    /** El bloque de la mano o, si no lleva ninguno, el que mira. */
    private static Reference held(ServerPlayerEntity player, BlockColorCatalog catalog) {
        if (player.getMainHandStack().getItem() instanceof BlockItem blockItem) {
            BlockColor block = catalog.get(Registries.BLOCK.getId(blockItem.getBlock()).toString());
            if (block != null) {
                return new Reference(block, block.rgb(), block.lab());
            }
        }
        BlockPos aimed = EzContext.aim(player, false);
        if (aimed != null) {
            Block block = player.getWorld().getBlockState(aimed).getBlock();
            BlockColor color = catalog.get(Registries.BLOCK.getId(block).toString());
            if (color != null) {
                return new Reference(color, color.rgb(), color.lab());
            }
        }
        throw new IllegalArgumentException("Di un bloque o un color (#3A7FD2), lleva un bloque en la mano o mira uno");
    }

    record Reference(BlockColor block, int rgb, double[] lab) {
        String label() {
            return block != null ? block.shortName() : OkLab.hex(rgb);
        }

        ColorGradient.Anchor anchor() {
            return block != null ? ColorGradient.Anchor.of(block) : ColorGradient.Anchor.of(rgb);
        }

        MutableText swatch() {
            if (block != null) {
                return ColorCommands.swatch(block, 2);
            }
            return Text.literal(SWATCH.repeat(2)).styled(style -> style.withColor(TextColor.fromRgb(rgb)));
        }
    }

    static ColorFilter filter(CommandTokens tokens) {
        return ColorFilter.of(tokens.flag("c"), tokens.flag("s"), tokens.flag("o"), tokens.flag("u"),
                tokens.flag("x"));
    }

    private static Item item(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return null;
        }
        Item item = Registries.BLOCK.get(identifier).asItem();
        return item == Items.AIR ? null : item;
    }

    /** Como mucho {@code limit} elementos repartidos por igual, con el primero y el último. */
    static <T> List<T> sample(List<T> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }
        List<T> result = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            result.add(values.get((int) Math.round(index * (values.size() - 1) / (double) (limit - 1))));
        }
        return result;
    }

    private static int parseCount(String text, int maximum, String what) {
        try {
            int value = Integer.parseInt(text);
            if (value < 0 || value > maximum) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("«" + text + "» no vale para " + what + " (de 0 a " + maximum + ")");
        }
    }

    private static double fraction(String text, String what) {
        try {
            double value = Double.parseDouble(text.replace(',', '.'));
            if (!(value >= 0.0 && value <= 1.0)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("«" + text + "» no vale para " + what + ": va de 0 a 1");
        }
    }
}
