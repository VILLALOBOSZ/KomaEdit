package com.komainu.moxia.worldedit.ez;

import com.komainu.moxia.noise.NoiseSpec;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Patrones con la notación de ezEdits para cualquier orden de WorldEdit que acepte un patrón. */
public final class EzPatternParser extends InputParser<Pattern> {
    /** Volumen máximo que copia {@code #selection}. */
    public static final long MAX_SELECTION_VOLUME = 262_144;
    static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("aim", "aim"),
            Map.entry("eznoisepattern", "eznoisepattern"), Map.entry("eznp", "eznoisepattern"),
            Map.entry("ridged", "ridged"), Map.entry("smoothcells", "smoothcells"),
            Map.entry("voronoiedge", "voronoiedge"),
            Map.entry("palette", "palette"),
            Map.entry("radialgradientpattern", "radialgradientpattern"), Map.entry("rgradientp", "radialgradientpattern"),
            Map.entry("radialgradient", "radialgradientpattern"),
            Map.entry("vectorgradientpattern", "vectorgradientpattern"), Map.entry("vgradientp", "vectorgradientpattern"),
            Map.entry("vectorgradient", "vectorgradientpattern"),
            Map.entry("selection", "selection"), Map.entry("sel", "selection")
    );
    private static final List<String> SUGGESTIONS = List.of("#aim", "#eznoisepattern[", "#ridged[", "#smoothcells[",
            "#voronoiedge[", "#palette[", "#radialgradientpattern[", "#vectorgradientpattern[", "#selection[");

    public EzPatternParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public Stream<String> getSuggestions(String input, ParserContext context) {
        if (!input.startsWith("#") || input.startsWith("##") || input.indexOf('[') >= 0) {
            return Stream.empty();
        }
        String lower = input.toLowerCase(Locale.ROOT);
        return SUGGESTIONS.stream().filter(suggestion -> suggestion.startsWith(lower));
    }

    @Override
    public Pattern parseFromInput(String input, ParserContext context) throws InputParseException {
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

    private Pattern create(String name, EzArguments arguments, ParserContext context) throws InputParseException {
        return switch (name) {
            case "aim" -> {
                arguments.atMost(1);
                ServerPlayerEntity player = EzContext.player(context, "#aim");
                BlockPos target = EzContext.aim(player, arguments.bool(0, false));
                if (target == null) {
                    throw EzContext.error("#aim: no estás mirando a ningún bloque a menos de "
                            + (int) EzContext.AIM_RANGE + " bloques");
                }
                BaseBlock block = context.requireExtent().getFullBlock(
                        BlockVector3.at(target.getX(), target.getY(), target.getZ()));
                yield pattern(position -> block);
            }
            case "palette" -> {
                arguments.atMost(1);
                RandomPattern random = new RandomPattern();
                for (Pattern pattern : palette(arguments.require(0, "una paleta"), context)) {
                    random.add(pattern, 1.0);
                }
                yield random;
            }
            case "eznoisepattern" -> {
                arguments.atMost(4);
                List<Pattern> palette = palette(arguments.require(0, "una paleta"), context);
                NoiseSpec noise = EzContext.noise(context, arguments.require(1, "un ruido, por ejemplo [ridged]"));
                yield noisePattern(palette, noise, arguments, 2);
            }
            case "ridged", "smoothcells", "voronoiedge" -> {
                arguments.atMost(3);
                List<Pattern> palette = palette(arguments.require(0, "una paleta"), context);
                yield noisePattern(palette, NoiseSpec.parse(name), arguments, 1);
            }
            case "radialgradientpattern" -> {
                arguments.atMost(7);
                List<Pattern> palette = palette(arguments.require(0, "una paleta"), context);
                ServerPlayerEntity player = EzContext.player(context, "#radialgradientpattern");
                double length = arguments.number(1, Double.NaN);
                checkLength(arguments, length);
                GradientMath.DistanceMode mode = arguments.has(2)
                        ? GradientMath.DistanceMode.byName(arguments.text(2, "sphere"))
                        : GradientMath.DistanceMode.SPHERE;
                double offset = arguments.number(3, 0.0);
                yield gradientPattern(palette, EzMasks.Progress.radial(EzMaskParser.origin(player), mode, offset,
                        length), EzMaskParser.randomness(arguments, 4, context));
            }
            case "vectorgradientpattern" -> {
                arguments.atMost(6);
                List<Pattern> palette = palette(arguments.require(0, "una paleta"), context);
                ServerPlayerEntity player = EzContext.player(context, "#vectorgradientpattern");
                Vector3 unit = EzDirections.vector(arguments.require(1, "un vector, por ejemplo [up] o [1,0,0]"),
                        player.getYaw(), player.getPitch());
                double length = arguments.number(2, Double.NaN);
                checkLength(arguments, length);
                yield gradientPattern(palette, EzMasks.Progress.along(EzMaskParser.origin(player), unit, length),
                        EzMaskParser.randomness(arguments, 3, context));
            }
            case "selection" -> selection(arguments.atMost(2), context);
            default -> throw new IllegalStateException(name);
        };
    }

    /** {@link Pattern} no es una interfaz funcional (sus dos métodos tienen cuerpo), así que se envuelve aquí. */
    static Pattern pattern(java.util.function.Function<BlockVector3, BaseBlock> function) {
        return new Pattern() {
            @Override
            public BaseBlock applyBlock(BlockVector3 position) {
                return function.apply(position);
            }
        };
    }

    private List<Pattern> palette(String input, ParserContext context) throws InputParseException {
        List<Pattern> patterns = new ArrayList<>();
        for (String entry : EzContext.palette(context, input)) {
            patterns.add(worldEdit.getPatternFactory().parseFromInput(entry, context));
        }
        return List.copyOf(patterns);
    }

    private static Pattern noisePattern(List<Pattern> palette, NoiseSpec noise, EzArguments arguments, int index) {
        double scale = arguments.number(index, 1.0);
        if (scale <= 0 || scale > 4096) {
            throw new IllegalArgumentException("#" + arguments.name() + ": la escala va de 0 a 4096");
        }
        EzMasks.Randomness field = EzMasks.Randomness.of(noise, scale, EzMaskParser.seed(arguments, index + 1));
        int size = palette.size();
        return pattern(position -> {
            double value = field.value(position.x(), position.y(), position.z());
            int paletteIndex = Math.clamp((int) Math.floor(value * size), 0, size - 1);
            return palette.get(paletteIndex).applyBlock(position);
        });
    }

    private static Pattern gradientPattern(List<Pattern> palette, EzMasks.Progress progress,
                                           EzMasks.Randomness randomness) {
        int size = palette.size();
        return pattern(position -> {
            double random = randomness.value(position.x(), position.y(), position.z());
            return palette.get(GradientMath.paletteIndex(progress.at(position), random, size)).applyBlock(position);
        });
    }

    private static void checkLength(EzArguments arguments, double length) {
        if (Double.isNaN(length) || length <= 0 || length > 4096) {
            throw new IllegalArgumentException("#" + arguments.name() + " necesita una distancia entre 0 y 4096");
        }
    }

    /** {@code #selection}: copia los bloques de una selección guardada y los repite como un mosaico. */
    private static Pattern selection(EzArguments arguments, ParserContext context) throws InputParseException {
        UUID playerId = EzContext.playerId(context);
        if (playerId == null) {
            throw EzContext.error("#selection solo lo puede usar un jugador");
        }
        SelectionShape shape = SavedSelections.load(playerId, arguments.require(0, "el nombre de una selección guardada"));
        String world = context.requireWorld().getName();
        if (shape.world() != null && !shape.world().equals(world)) {
            throw EzContext.error("La selección " + arguments.text(0, "") + " es de otro mundo (" + shape.world() + ")");
        }
        BlockVector3 offset = BlockVector3.ZERO;
        if (arguments.has(1)) {
            String[] parts = arguments.text(1, "").split("[,;:]");
            if (parts.length != 3) {
                throw EzContext.error("#selection: el desplazamiento se escribe x,y,z");
            }
            try {
                offset = BlockVector3.at(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()));
            } catch (NumberFormatException exception) {
                throw EzContext.error("#selection: el desplazamiento se escribe con números enteros x,y,z");
            }
        }
        BlockVector3 minimum = shape.minimum();
        BlockVector3 size = shape.size();
        long volume = (long) size.x() * size.y() * size.z();
        if (volume > MAX_SELECTION_VOLUME) {
            throw EzContext.error("#selection admite selecciones de hasta " + MAX_SELECTION_VOLUME + " bloques ("
                    + volume + ")");
        }
        Extent extent = context.requireExtent();
        BaseBlock[] blocks = new BaseBlock[(int) volume];
        int index = 0;
        for (int y = 0; y < size.y(); y++) {
            for (int z = 0; z < size.z(); z++) {
                for (int x = 0; x < size.x(); x++) {
                    blocks[index++] = extent.getFullBlock(minimum.add(x, y, z));
                }
            }
        }
        BlockVector3 shift = offset;
        return pattern(position -> {
            int x = Math.floorMod(position.x() - minimum.x() - shift.x(), size.x());
            int y = Math.floorMod(position.y() - minimum.y() - shift.y(), size.y());
            int z = Math.floorMod(position.z() - minimum.z() - shift.z(), size.z());
            return blocks[(y * size.z() + z) * size.x() + x];
        });
    }
}
