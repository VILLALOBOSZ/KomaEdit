package com.komainu.moxia.brush.place;

import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;

/**
 * Estructura {@code Clipboard} ({@code Cl}): el portapapeles de WorldEdit ({@code //copy}).
 *
 * <ul>
 *     <li>{@code Origin}/{@code O}: {@code INHERENT} (I, el punto desde el que se copió) o {@code CENTER} (C, el
 *     centro de la copia).</li>
 *     <li>{@code Rotation}/{@code R}: giro previo alrededor del «arriba», en múltiplos de 90°.</li>
 *     <li>{@code PasteMethod}/{@code PM}: solo {@code FAST}; el remuestreo suave llegará con los giros libres.</li>
 * </ul>
 */
public record ClipboardStructure(boolean center, int quarterTurns) implements StructureSource {
    public static final int MAX_BLOCKS = 1_000_000;

    public static ClipboardStructure parse(StructureSpec spec) {
        String origin = spec.option("origin", "o");
        boolean center = false;
        if (origin != null) {
            center = switch (origin.toLowerCase(Locale.ROOT)) {
                case "inherent", "i" -> false;
                case "center", "c", "centro" -> true;
                default -> throw new IllegalArgumentException("Origen desconocido: " + origin + " (INHERENT o CENTER)");
            };
        }
        String method = spec.option("pastemethod", "pm");
        if (method != null && !method.equalsIgnoreCase("fast")) {
            throw new IllegalArgumentException("De momento el pincel solo pega con PasteMethod:FAST");
        }
        return new ClipboardStructure(center, rotation(spec.option("rotation", "r")));
    }

    static int rotation(String value) {
        if (value == null) {
            return 0;
        }
        try {
            int degrees = (int) Math.round(Double.parseDouble(value));
            if (Math.floorMod(degrees, 90) != 0) {
                throw new IllegalArgumentException("El giro va en múltiplos de 90°: " + value);
            }
            return Math.floorMod(degrees / 90, 4);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Giro inválido: " + value);
        }
    }

    @Override
    public String describe() {
        return "portapapeles";
    }

    @Override
    public PlacementTemplate prepare(ServerPlayerEntity player, long seed) {
        Clipboard clipboard;
        try {
            clipboard = WorldEditService.clipboard(player);
        } catch (EmptyClipboardException exception) {
            throw new IllegalArgumentException("El portapapeles está vacío: copia algo con //copy");
        }
        Region region = clipboard.getRegion();
        long volume = region.getVolume();
        if (volume > MAX_BLOCKS) {
            throw new IllegalArgumentException("El portapapeles es demasiado grande para el pincel (" + volume
                    + " bloques; máximo " + MAX_BLOCKS + ")");
        }
        BlockVector3 origin = center
                ? region.getMinimumPoint().add(region.getMaximumPoint()).divide(2)
                : clipboard.getOrigin();
        int count = (int) volume;
        int[] xs = new int[count];
        int[] ys = new int[count];
        int[] zs = new int[count];
        BlockState[] states = new BlockState[count];
        int index = 0;
        for (BlockVector3 position : region) {
            BlockVector3 local = position.subtract(origin);
            xs[index] = local.x();
            ys[index] = local.y();
            zs[index] = local.z();
            states[index] = clipboard.getBlock(position);
            index++;
        }
        return new Snapshot(xs, ys, zs, states, index);
    }

    private record Snapshot(int[] xs, int[] ys, int[] zs, BlockState[] states, int size) implements PlacementTemplate {
        @Override
        public void forEach(Visitor visitor) {
            for (int index = 0; index < size; index++) {
                visitor.accept(xs[index], ys[index], zs[index], states[index]);
            }
        }
    }
}
