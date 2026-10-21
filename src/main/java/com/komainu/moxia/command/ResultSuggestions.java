package com.komainu.moxia.command;

import com.komainu.moxia.gradient.PaletteSpec;
import com.komainu.moxia.gradient.SavedPalettes;
import com.komainu.moxia.placement.DirectionExpr;
import com.komainu.moxia.placement.ParamText;
import com.komainu.moxia.shape.Vec3;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * El {@code =} final de ezEdits («Result»): al escribir {@code ##Magma=}, {@code -#mia(1:4)=} o
 * {@code Sh(S:Cone,P:##Ice=}, la sugerencia del chat es la paleta ya desplegada en bloques, y con
 * {@code C(D:east-z+(0,0.5,0)=} la dirección ya calculada; el tabulador la escribe. Es una sugerencia normal de
 * Brigadier que sustituye la última palabra: no cambia lo que se manda si no se acepta (el {@code =} se ignora).
 */
public final class ResultSuggestions {
    /** Lo que cabe en la línea del chat de Minecraft. */
    static final int CHAT_LIMIT = 256;
    private static final String VANILLA = "minecraft:";
    private static final Pattern BLOCK = Pattern.compile(
            "[a-z0-9_.\\-]+(:[a-z0-9_./\\-]+)?(\\[[a-z0-9_=,:.\\-]*])?");
    private static final Set<String> DIRECTION_KEYS = Set.of("d", "direction", "direccion", "dir");

    /** Solo el {@code =}, para los argumentos de texto libre que no sugieren nada más. */
    public static final SuggestionProvider<ServerCommandSource> PROVIDER = or((context, builder) ->
            builder.buildFuture());

    private ResultSuggestions() {
    }

    /** Con la última palabra acabada en {@code =}, su resultado; si no, las sugerencias de {@code base}. */
    public static SuggestionProvider<ServerCommandSource> or(SuggestionProvider<ServerCommandSource> base) {
        return (context, builder) -> {
            String remaining = builder.getRemaining();
            int lastSpace = remaining.lastIndexOf(' ');
            String token = remaining.substring(lastSpace + 1);
            if (token.endsWith("=")) {
                ServerCommandSource source = context.getSource();
                ServerPlayerEntity player = source != null && source.getEntity() instanceof ServerPlayerEntity found
                        ? found : null;
                Optional<String> result = complete(token,
                        player == null ? null : SavedPalettes.resolver(player.getUuid()), view(player));
                int lineLength = builder.getInput().length() - token.length();
                if (result.isPresent() && lineLength + result.get().length() <= CHAT_LIMIT) {
                    SuggestionsBuilder tail = builder.createOffset(builder.getStart() + lastSpace + 1);
                    tail.suggest(result.get());
                    return tail.buildFuture();
                }
            }
            return base.getSuggestions(context, builder);
        };
    }

    private static DirectionExpr.Context view(ServerPlayerEntity player) {
        if (player == null) {
            return DirectionExpr.Context.NONE;
        }
        var look = player.getRotationVec(1.0F);
        return new DirectionExpr.Context(new Vec3(look.x, look.y, look.z));
    }

    /**
     * El resultado de {@code token} (que acaba en {@code =}): la paleta desplegada o la dirección calculada, con lo
     * que va delante de ella intacto ({@code P:}, {@code #palette[}, {@code C(D:}…). Se prueba desde el principio y
     * después tras cada {@code [}, {@code (}, {@code :} y {@code ,}: gana el trozo más largo que se lee entero, y una
     * dirección (tras {@code D:} o {@code Direction:}) antes que una paleta.
     *
     * @param userPalettes las paletas guardadas del jugador ({@code #nombre}); {@code null} sin jugador
     */
    public static Optional<String> complete(String token, Function<String, String> userPalettes,
                                            DirectionExpr.Context view) {
        if (token == null || token.length() < 2 || !token.endsWith("=")) {
            return Optional.empty();
        }
        String body = token.substring(0, token.length() - 1);
        List<Integer> starts = starts(body);
        // Primero las direcciones: «D:right» también parecería un bloque con espacio de nombres.
        for (int start : starts) {
            String prefix = body.substring(0, start);
            if (directionKey(prefix) && !body.substring(start).isBlank()) {
                try {
                    return Optional.of(prefix + vector(DirectionExpr.parse(body.substring(start)).evaluate(view)));
                } catch (IllegalArgumentException exception) {
                    // No es una dirección entera: puede que sí lo sea un trozo más corto.
                }
            }
        }
        for (int start : starts) {
            String prefix = body.substring(0, start);
            String text = body.substring(start);
            if (text.isBlank() || directionKey(prefix)) {
                continue;
            }
            Optional<String> palette = palette(text, userPalettes);
            if (palette.isPresent()) {
                return Optional.of(prefix + palette.get());
            }
        }
        return Optional.empty();
    }

    /** La paleta desplegada (bloques separados por comas, sin {@code minecraft:}), si {@code text} lo es. */
    static Optional<String> palette(String text, Function<String, String> userPalettes) {
        List<String> entries;
        try {
            entries = PaletteSpec.parse(text, userPalettes);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        List<String> compact = new ArrayList<>(entries.size());
        for (String entry : entries) {
            String block = entry.trim();
            if (!BLOCK.matcher(block.toLowerCase(Locale.ROOT)).matches()) {
                return Optional.empty();
            }
            compact.add(block.startsWith(VANILLA) ? block.substring(VANILLA.length()) : block);
        }
        return Optional.of(String.join(",", compact));
    }

    private static List<Integer> starts(String body) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int index = 0; index < body.length(); index++) {
            if ("[(:,".indexOf(body.charAt(index)) >= 0) {
                starts.add(index + 1);
            }
        }
        return starts;
    }

    /** Si lo de delante acaba en la clave de una dirección: {@code C(D:}, {@code Constant(Direction:}… */
    private static boolean directionKey(String prefix) {
        if (!prefix.endsWith(":")) {
            return false;
        }
        int from = Math.max(prefix.lastIndexOf('('), Math.max(prefix.lastIndexOf(','), prefix.lastIndexOf('[')));
        return DIRECTION_KEYS.contains(ParamText.normalize(prefix.substring(from + 1, prefix.length() - 1)));
    }

    private static String vector(Vec3 vector) {
        return "(" + number(vector.x()) + "," + number(vector.y()) + "," + number(vector.z()) + ")";
    }

    private static String number(double value) {
        double rounded = Math.rint(value * 1000.0) / 1000.0 + 0.0;
        return rounded == Math.rint(rounded) ? Long.toString((long) rounded) : Double.toString(rounded);
    }
}
