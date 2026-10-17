package com.komainu.moxia.region;

import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;
import java.util.Set;

/**
 * Máscaras de WorldEdit para las operaciones de región. Se parsean en el hilo del servidor con un
 * {@link SnapshotExtent}; en el worker se conecta la foto y la máscara mira los bloques capturados, nunca el mundo.
 *
 * <p>Sirve todo lo que WorldEdit evalúa sobre bloques: listas ({@code stone,dirt}), estados
 * ({@code oak_stairs[half=top]}), {@code #existing}, {@code #solid}, {@code #surface}, desplazamientos
 * ({@code >stone}, {@code <air}), negación ({@code !}), {@code #region}, ruido ({@code %50}) y expresiones
 * ({@code =y>64}). {@code *} o {@code #all} aceptan cualquier bloque que no sea aire.</p>
 */
public final class RegionMasks {
    private static final Set<String> ALL = Set.of("*", "#all", "all", "todo", "#todo");

    private RegionMasks() {
    }

    public static Bound parse(ServerPlayerEntity player, String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta la máscara, por ejemplo #existing o stone,andesite");
        }
        String text = input.strip();
        SnapshotExtent extent = new SnapshotExtent();
        if (ALL.contains(text.toLowerCase(Locale.ROOT))) {
            return new Bound(text, extent, null);
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        ParserContext parser = new ParserContext();
        parser.setActor(context.actor());
        parser.setSession(context.session());
        parser.setWorld(context.world());
        parser.setExtent(extent);
        parser.setRestricted(!context.actor().hasPermission("worldedit.anyblock"));
        try {
            Mask mask = WorldEdit.getInstance().getMaskFactory().parseFromInput(text, parser);
            return new Bound(text, extent, mask);
        } catch (InputParseException exception) {
            throw new IllegalArgumentException("Máscara inválida «" + text + "»: " + exception.getMessage(), exception);
        }
    }

    /** Una máscara lista para conectarse a la foto. {@code mask == null} acepta todo. */
    public static final class Bound {
        private final String source;
        private final SnapshotExtent extent;
        private final Mask mask;

        private Bound(String source, SnapshotExtent extent, Mask mask) {
            this.source = source;
            this.extent = extent;
            this.mask = mask;
        }

        public String source() {
            return source;
        }

        public void bind(RegionSnapshot snapshot) {
            extent.bind(snapshot);
        }

        public boolean test(int x, int y, int z) {
            return mask == null || mask.test(BlockVector3.at(x, y, z));
        }
    }
}
