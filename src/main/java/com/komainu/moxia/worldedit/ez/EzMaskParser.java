package com.komainu.moxia.worldedit.ez;

import com.komainu.moxia.gradient.BlockSpec;
import com.komainu.moxia.noise.NoiseSpec;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Máscaras con la notación de ezEdits para cualquier orden de WorldEdit ({@code //set}, {@code //replace},
 * {@code //gmask}, pinceles…). Se parsean en el hilo de la orden; lo que dependa del jugador (su mirada, su posición,
 * su portapapeles) se fija en ese momento.
 */
public final class EzMaskParser extends InputParser<Mask> {
    /** Nombre canónico de cada máscara y sus alias. */
    static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("aim", "aim"),
            Map.entry("attached", "attached"),
            Map.entry("blocklight", "blocklight"),
            Map.entry("clipblocks", "clipblocks"), Map.entry("cb", "clipblocks"),
            Map.entry("current", "current"), Map.entry("c", "current"),
            Map.entry("eznoisemask", "eznoisemask"), Map.entry("eznm", "eznoisemask"),
            Map.entry("fullblock", "fullblock"),
            Map.entry("hotbar", "hotbar"),
            Map.entry("fuzzypalette", "fuzzypalette"), Map.entry("fpalette", "fuzzypalette"),
            Map.entry("infested", "infested"),
            Map.entry("lightsource", "lightsource"),
            Map.entry("near", "near"),
            Map.entry("palette", "palette"),
            Map.entry("radialgradientmask", "radialgradientmask"), Map.entry("rgradientm", "radialgradientmask"),
            Map.entry("schemblocks", "schemblocks"), Map.entry("sb", "schemblocks"),
            Map.entry("truelight", "truelight"),
            Map.entry("vectorgradientmask", "vectorgradientmask"), Map.entry("vgradientm", "vectorgradientmask")
    );
    private static final List<String> SUGGESTIONS = List.of("#aim", "#attached", "#blocklight[", "#clipblocks",
            "#current", "#eznoisemask[", "#fullblock", "#fuzzypalette[", "#hotbar", "#infested", "#lightsource",
            "#near[",
            "#palette[", "#radialgradientmask[", "#schemblocks[", "#truelight[", "#vectorgradientmask[");
    private static final int MAX_SCAN = 4_000_000;

    public EzMaskParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public Stream<String> getSuggestions(String input, ParserContext context) {
        if (!input.startsWith("#") || input.startsWith("##") || input.indexOf('[') >= 0) {
            return Stream.empty();
        }
        String lower = input.toLowerCase(java.util.Locale.ROOT);
        return SUGGESTIONS.stream().filter(suggestion -> suggestion.startsWith(lower));
    }

    @Override
    public Mask parseFromInput(String input, ParserContext context) throws InputParseException {
        EzArguments arguments;
        try {
            arguments = EzArguments.parse(input);
        } catch (IllegalArgumentException exception) {
            throw EzContext.error(exception.getMessage());
        }
        if (arguments == null) {
            return null;
        }
        String name = NAMES.get(arguments.name());
        if (name == null) {
            return null;
        }
        try {
            return create(name, arguments, context);
        } catch (IllegalArgumentException exception) {
            throw EzContext.error(exception.getMessage());
        }
    }

    private Mask create(String name, EzArguments arguments, ParserContext context) throws InputParseException {
        return switch (name) {
            case "aim" -> aim(arguments.atMost(1), context);
            case "attached" -> attached(arguments.atMost(1), context);
            case "blocklight" -> light(arguments.atMost(2), context, false);
            case "truelight" -> light(arguments.atMost(2), context, true);
            case "lightsource" -> {
                arguments.atMost(2);
                int minimum = arguments.has(0) ? level(arguments, 0) : 1;
                int maximum = arguments.has(1) ? level(arguments, 1) : arguments.has(0) ? minimum : 15;
                yield new EzMasks.StateMask(context.requireExtent(), state -> {
                    int light = EzMasks.luminance(state);
                    return light >= minimum && light <= maximum;
                });
            }
            case "clipblocks" -> {
                arguments.atMost(2);
                Clipboard clipboard;
                try {
                    clipboard = context.requireSession().getClipboard().getClipboard();
                } catch (EmptyClipboardException exception) {
                    throw EzContext.error("#clipblocks necesita algo en el portapapeles (//copy)");
                }
                yield containedMask(context, clipboard, arguments.bool(0, false), arguments.bool(1, false));
            }
            case "schemblocks" -> {
                arguments.atMost(3);
                Clipboard schematic = Schematics.load(arguments.require(0, "el nombre del schematic"));
                yield containedMask(context, schematic, arguments.bool(1, false), arguments.bool(2, false));
            }
            case "current" -> {
                arguments.atMost(0);
                Mask current = context.requireSession().getMask();
                yield current == null ? Masks.alwaysTrue() : current;
            }
            case "eznoisemask" -> {
                arguments.atMost(4);
                NoiseSpec noise = EzContext.noise(context, arguments.require(0, "un ruido, por ejemplo [ridged]"));
                double scale = positive(arguments, 1, 1.0);
                double threshold = arguments.number(2, 0.5);
                yield new EzMasks.NoiseMask(EzMasks.Randomness.of(noise, scale, seed(arguments, 3)), threshold);
            }
            case "fullblock" -> new EzMasks.StateMask(context.requireExtent(), EzMasks::fullBlock);
            case "infested" -> new EzMasks.StateMask(context.requireExtent(), EzMasks::infested);
            case "hotbar" -> {
                arguments.atMost(0);
                Set<com.sk89q.worldedit.world.block.BlockType> types = new LinkedHashSet<>();
                for (BlockState state : EzContext.hotbar(EzContext.player(context, "#hotbar"))) {
                    types.add(state.getBlockType());
                }
                yield types.isEmpty() ? Masks.negate(Masks.alwaysTrue())
                        : new EzMasks.StateMask(context.requireExtent(), state -> types.contains(state.getBlockType()));
            }
            case "palette", "fuzzypalette" -> {
                arguments.atMost(name.equals("palette") ? 2 : 1);
                List<BlockSpec> entries = EzContext.palette(context, arguments.require(0, "una paleta"))
                        .stream().map(BlockSpec::parse).toList();
                boolean strict = name.equals("palette") && arguments.bool(1, true);
                yield new EzMasks.StateMask(context.requireExtent(), EzMasks.palette(entries, strict));
            }
            case "near" -> {
                arguments.atMost(3);
                Mask inner = worldEdit.getMaskFactory().parseFromInput(arguments.require(0, "una máscara"), context);
                double first = arguments.number(1, 1.0);
                double minimum = arguments.has(2) ? first : 0.0;
                double maximum = arguments.has(2) ? arguments.number(2, 1.0) : first;
                if (maximum < 1 || maximum > NearSearch.MAX_DISTANCE || minimum < 0 || minimum > maximum) {
                    throw new IllegalArgumentException("#near: la distancia va de 1 a " + NearSearch.MAX_DISTANCE
                            + " y la mínima no puede superar la máxima");
                }
                yield new EzMasks.NearMask(inner, minimum, maximum);
            }
            case "radialgradientmask" -> {
                arguments.atMost(6);
                ServerPlayerEntity player = EzContext.player(context, "#radialgradientmask");
                double length = positive(arguments, 0, Double.NaN);
                GradientMath.DistanceMode mode = arguments.has(1)
                        ? GradientMath.DistanceMode.byName(arguments.text(1, "sphere"))
                        : GradientMath.DistanceMode.SPHERE;
                double offset = arguments.number(2, 0.0);
                yield new EzMasks.GradientMask(EzMasks.Progress.radial(origin(player), mode, offset, length),
                        randomness(arguments, 3, context));
            }
            case "vectorgradientmask" -> {
                arguments.atMost(5);
                ServerPlayerEntity player = EzContext.player(context, "#vectorgradientmask");
                Vector3 unit = EzDirections.vector(arguments.require(0, "un vector, por ejemplo [up] o [1,0,0]"),
                        player.getYaw(), player.getPitch());
                double length = positive(arguments, 1, Double.NaN);
                yield new EzMasks.GradientMask(EzMasks.Progress.along(origin(player), unit, length),
                        randomness(arguments, 2, context));
            }
            default -> throw new IllegalStateException(name);
        };
    }

    private static Mask aim(EzArguments arguments, ParserContext context) throws InputParseException {
        ServerPlayerEntity player = EzContext.player(context, "#aim");
        BlockPos target = EzContext.aim(player, arguments.bool(0, false));
        if (target == null) {
            return Masks.negate(Masks.alwaysTrue());
        }
        BlockState state = context.requireExtent().getBlock(BlockVector3.at(target.getX(), target.getY(),
                target.getZ()));
        Extent extent = context.requireExtent();
        return new EzMasks.StateMask(extent, state::equals);
    }

    private static Mask attached(EzArguments arguments, ParserContext context) throws InputParseException {
        List<Direction> directions = new ArrayList<>();
        if (arguments.has(0)) {
            ServerPlayerEntity player = EzContext.optionalPlayer(context);
            for (String part : arguments.require(0, "las direcciones").split(",")) {
                BlockVector3 vector = EzDirections.parse(part, player == null ? 0f : player.getYaw(),
                        player == null ? 0f : player.getPitch());
                Direction direction = Direction.fromVector(vector.x(), vector.y(), vector.z());
                if (direction != null && !directions.contains(direction)) {
                    directions.add(direction);
                }
            }
        } else {
            directions.addAll(List.of(Direction.values()));
        }
        return new EzMasks.AttachedMask(context.requireExtent(), List.copyOf(directions));
    }

    private static Mask light(EzArguments arguments, ParserContext context, boolean sky) throws InputParseException {
        String feature = sky ? "#truelight" : "#blocklight";
        arguments.require(0, "un nivel de luz de 0 a 15");
        int minimum = level(arguments, 0);
        int maximum = arguments.has(1) ? level(arguments, 1) : minimum;
        if (minimum > maximum) {
            throw new IllegalArgumentException(feature + ": el mínimo no puede superar el máximo");
        }
        return new EzMasks.LightMask(EzContext.world(context), sky, minimum, maximum);
    }

    private static int level(EzArguments arguments, int index) {
        double value = arguments.number(index, 0);
        if (value < 0 || value > 15 || value != Math.floor(value)) {
            throw new IllegalArgumentException("#" + arguments.name() + ": la luz va de 0 a 15");
        }
        return (int) value;
    }

    private static double positive(EzArguments arguments, int index, double fallback) {
        double value = arguments.has(index) ? arguments.number(index, fallback) : fallback;
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("#" + arguments.name() + " necesita una distancia");
        }
        if (value <= 0 || value > 4096) {
            throw new IllegalArgumentException("#" + arguments.name() + ": el valor debe estar entre 0 y 4096");
        }
        return value;
    }

    static long seed(EzArguments arguments, int index) {
        if (!arguments.has(index)) {
            return ThreadLocalRandom.current().nextLong();
        }
        double value = arguments.number(index, -1);
        return value == -1 ? ThreadLocalRandom.current().nextLong() : (long) value;
    }

    /** Ruido opcional en {@code index}, su escala en {@code index + 1} y su semilla en {@code index + 2}. */
    static EzMasks.Randomness randomness(EzArguments arguments, int index, ParserContext context)
            throws InputParseException {
        NoiseSpec noise = arguments.has(index) ? EzContext.noise(context, arguments.text(index, "")) : null;
        double scale = positive(arguments, index + 1, 1.0);
        return EzMasks.Randomness.of(noise, scale, seed(arguments, index + 2));
    }

    static Vector3 origin(ServerPlayerEntity player) {
        return Vector3.at(player.getX(), player.getY(), player.getZ());
    }

    private static Mask containedMask(ParserContext context, Clipboard clipboard, boolean includeAir, boolean strict)
            throws InputParseException {
        if (clipboard.getRegion().getVolume() > MAX_SCAN) {
            throw EzContext.error("El portapapeles o schematic tiene más de " + MAX_SCAN + " bloques");
        }
        Set<BlockState> states = new LinkedHashSet<>();
        for (BlockVector3 position : clipboard.getRegion()) {
            BlockState state = clipboard.getBlock(position);
            if (includeAir || !EzMasks.isAir(state)) {
                states.add(state);
            }
        }
        if (states.isEmpty()) {
            return Masks.negate(Masks.alwaysTrue());
        }
        return new EzMasks.StateMask(context.requireExtent(), EzMasks.contained(states, strict));
    }
}
