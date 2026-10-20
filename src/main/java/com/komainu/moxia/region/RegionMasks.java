package com.komainu.moxia.region;

import com.komainu.moxia.worldedit.WorldEditService;
import com.komainu.moxia.worldedit.ez.NearSearch;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Máscaras de WorldEdit para las operaciones de región. Se parsean en el hilo del servidor con un
 * {@link SnapshotExtent}; en el worker se conecta la foto y la máscara mira los bloques capturados, nunca el mundo.
 *
 * <p>Sirve todo lo que WorldEdit evalúa sobre bloques: listas ({@code stone,dirt}), estados
 * ({@code oak_stairs[half=top]}), {@code #existing}, {@code #solid}, {@code #surface}, desplazamientos
 * ({@code >stone}, {@code <air}), negación ({@code !}), {@code #region}, ruido ({@code %50}), expresiones
 * ({@code =y>64}) y las {@code #…} de KomaEdit ({@code #near}, {@code #fullblock}, {@code #eznoisemask}…). Varias
 * máscaras separadas por espacios se cumplen a la vez. {@code *} o {@code #all} aceptan cualquier bloque que no sea
 * aire.</p>
 *
 * <p>Las bandera {@code -m} de los generadores, la superficie y los pinceles aceptaban antes solo una lista de bloques
 * con su propia sintaxis. Esa lista sigue valiendo con su significado de siempre: si el texto lo es, se evalúa sin
 * WorldEdit; si no, se parsea como máscara de WorldEdit.</p>
 */
public final class RegionMasks {
    private static final Set<String> ALL = Set.of("*", "#all", "all", "todo", "#todo");
    /** {@code #current} (la máscara global): WorldEdit la ata al mundo, así que el worker no la puede leer. */
    private static final Pattern CURRENT = Pattern.compile("(?i)(^|[\\s!\\[,(])#c(urrent)?(?=$|[\\s\\],)])");
    private static final Pattern NEAR = Pattern.compile("(?i)#near\\[");
    /** Un id de bloque con o sin espacio de nombres y, si acaso, propiedades entre corchetes. */
    private static final Pattern BLOCK_ID = Pattern.compile(
            "[a-z0-9_.\\-]+(:[a-z0-9_.\\-/]+)?(\\[[a-z0-9_.\\-=,]*])?");

    private RegionMasks() {
    }

    /** Lee la lista de bloques de antes; devuelve {@code null} si el texto no es una lista de esas. */
    @FunctionalInterface
    public interface ListSyntax extends Function<String, Predicate<RegionSnapshot.StateInfo>> {
    }

    public static Bound parse(ServerPlayerEntity player, String input) {
        return parse(player, input, null);
    }

    /**
     * Máscara de {@code -m}: la lista de antes si el texto lo es (con su significado de siempre) o, si no, una
     * máscara de WorldEdit.
     */
    public static Bound parse(ServerPlayerEntity player, String input, ListSyntax lists) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta la máscara, por ejemplo #existing o stone,andesite");
        }
        String text = input.strip();
        if (ALL.contains(text.toLowerCase(Locale.ROOT))) {
            return new Bound(text, null, null, null, 0);
        }
        if (lists != null) {
            Predicate<RegionSnapshot.StateInfo> list = lists.apply(text);
            if (list != null) {
                return list(text, list);
            }
        }
        Parsed parsed = worldEdit(player, text);
        return new Bound(text, parsed.extent(), parsed.mask(), null, padding(text));
    }

    /** Una lista ya leída, sin WorldEdit (también para los tests). */
    public static Bound list(String source, Predicate<RegionSnapshot.StateInfo> predicate) {
        return new Bound(source, null, null, predicate, 0);
    }

    /** Una máscara de WorldEdit parseada sobre un {@link SnapshotExtent} todavía sin foto. */
    public record Parsed(Mask mask, SnapshotExtent extent) {
        public boolean test(int x, int y, int z) {
            return mask.test(BlockVector3.at(x, y, z));
        }
    }

    /**
     * Parsea una máscara de WorldEdit (con las de KomaEdit) en el hilo del servidor. Hasta que se conecte la foto, lo
     * que la máscara lea al parsearse viene del mundo del jugador.
     */
    public static Parsed worldEdit(ServerPlayerEntity player, String input) {
        String text = input.strip();
        if (CURRENT.matcher(text).find()) {
            throw new IllegalArgumentException("#current no se puede usar aquí: la máscara global de WorldEdit lee el"
                    + " mundo y esta operación se calcula fuera del hilo del servidor. Escribe la máscara directamente");
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        SnapshotExtent extent = new SnapshotExtent(context.world());
        ParserContext parser = new ParserContext();
        parser.setActor(context.actor());
        parser.setSession(context.session());
        parser.setWorld(context.world());
        parser.setExtent(extent);
        parser.setRestricted(!context.actor().hasPermission("worldedit.anyblock"));
        try {
            Mask mask = WorldEdit.getInstance().getMaskFactory().parseFromInput(text, parser);
            return new Parsed(mask, extent);
        } catch (InputParseException exception) {
            throw new IllegalArgumentException("Máscara inválida «" + text + "»: " + exception.getMessage(), exception);
        }
    }

    /**
     * ¿Es cada entrada (separada por comas o punto y coma, con {@code !} opcional delante) un id de bloque o una de
     * las clases que admite la lista?
     */
    public static boolean plainList(String text, Set<String> classes) {
        if (text == null || text.isBlank()) {
            return false;
        }
        int depth = 0;
        int start = 0;
        String lower = text.strip().toLowerCase(Locale.ROOT);
        for (int index = 0; index <= lower.length(); index++) {
            char character = index < lower.length() ? lower.charAt(index) : ',';
            if (character == '[') {
                depth++;
            } else if (character == ']') {
                depth--;
            } else if ((character == ',' || character == ';') && depth == 0) {
                String token = lower.substring(start, index).strip();
                if (token.startsWith("!")) {
                    token = token.substring(1).strip();
                }
                if (token.isEmpty() || !(classes.contains(token) || BLOCK_ID.matcher(token).matches())) {
                    return false;
                }
                start = index + 1;
            }
        }
        return depth == 0;
    }

    /** Margen que conviene capturar para que la máscara vea a los vecinos de la selección. */
    static int padding(String text) {
        return NEAR.matcher(text).find() ? NearSearch.MAX_DISTANCE + 1 : 1;
    }

    /** Una máscara lista para conectarse a la foto. Sin máscara ni lista acepta todo. */
    public static final class Bound {
        private final String source;
        private final SnapshotExtent extent;
        private final Mask mask;
        private final Predicate<RegionSnapshot.StateInfo> list;
        private final int padding;
        private volatile RegionSnapshot snapshot;

        private Bound(String source, SnapshotExtent extent, Mask mask, Predicate<RegionSnapshot.StateInfo> list,
                      int padding) {
            this.source = source;
            this.extent = extent;
            this.mask = mask;
            this.list = list;
            this.padding = padding;
        }

        public String source() {
            return source;
        }

        /** Margen alrededor de la selección que la máscara necesita leer (0 en las listas). */
        public int padding() {
            return padding;
        }

        public void bind(RegionSnapshot snapshot) {
            this.snapshot = snapshot;
            if (extent != null) {
                extent.bind(snapshot);
            }
        }

        public boolean test(int x, int y, int z) {
            if (list != null) {
                return list.test(snapshot.info(x, y, z));
            }
            return mask == null || mask.test(BlockVector3.at(x, y, z));
        }

        @Override
        public String toString() {
            return source;
        }
    }
}
