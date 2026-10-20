package com.komainu.moxia.brush.ez;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pincel Desplazar paleta (Palette Shift de ezEdits): cada bloque que esté en la paleta pasa al que tiene
 * {@code shift} posiciones más allá. Con desplazamiento 2 el primer bloque se convierte en el tercero.
 *
 * <p>Si un bloque se repite en la paleta ({@code stone*3,andesite}), hacia delante se cuenta desde su última
 * aparición y hacia atrás desde la primera, así un desplazamiento de 1 siempre cambia algo. En los extremos el
 * desplazamiento se queda en el primer o el último bloque; con {@code wrap} da la vuelta.</p>
 *
 * <p>Las losas y escaleras del material de una entrada cuentan como esa entrada y pasan a la losa o escalera del
 * material de destino, si existe.</p>
 */
public final class PaletteShift {
    private final int size;
    private final List<String> full;
    private final List<String> slabs;
    private final List<String> stairs;
    private final boolean wrap;
    private final Map<String, Entry> lookup = new HashMap<>();

    /**
     * @param full   id del bloque entero de cada posición
     * @param slabs  id de su losa o {@code null}; misma longitud que {@code full}
     * @param stairs id de su escalera o {@code null}; misma longitud que {@code full}
     */
    public PaletteShift(List<String> full, List<String> slabs, List<String> stairs, boolean wrap) {
        if (full.isEmpty()) {
            throw new IllegalArgumentException("La paleta está vacía");
        }
        if (slabs.size() != full.size() || stairs.size() != full.size()) {
            throw new IllegalArgumentException("Las variantes no cuadran con la paleta");
        }
        this.size = full.size();
        this.full = full.stream().map(PaletteShift::normalize).toList();
        this.slabs = slabs.stream().map(PaletteShift::normalize).toList();
        this.stairs = stairs.stream().map(PaletteShift::normalize).toList();
        this.wrap = wrap;
        register(this.full, Kind.FULL);
        register(this.slabs, Kind.SLAB);
        register(this.stairs, Kind.STAIRS);
    }

    public static PaletteShift of(List<String> full, boolean wrap) {
        List<String> none = java.util.Collections.nCopies(full.size(), null);
        return new PaletteShift(full, none, none, wrap);
    }

    private void register(List<String> ids, Kind kind) {
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            if (id == null) {
                continue;
            }
            Entry previous = lookup.get(id);
            if (previous == null) {
                lookup.put(id, new Entry(kind, index, index));
            } else if (previous.kind == kind) {
                lookup.put(id, new Entry(kind, previous.first, index));
            }
        }
    }

    /** Índice de destino para un desplazamiento entre dos posiciones (sin mirar variantes). */
    public int shiftIndex(int from, int shift) {
        int target = from + shift;
        return wrap ? Math.floorMod(target, size) : Math.clamp(target, 0, size - 1);
    }

    /** @return el destino del bloque, o {@code null} si no está en la paleta o no cambiaría */
    public Target target(String blockId, int shift) {
        String id = normalize(blockId);
        Entry entry = lookup.get(id);
        if (entry == null || shift == 0) {
            return null;
        }
        int from = shift > 0 ? entry.last : entry.first;
        int to = shiftIndex(from, shift);
        String targetId = switch (entry.kind) {
            case FULL -> full.get(to);
            case SLAB -> slabs.get(to);
            case STAIRS -> stairs.get(to);
        };
        if (targetId == null || targetId.equals(id)) {
            return null;
        }
        return new Target(to, entry.kind);
    }

    /** Recorre la esfera del pincel y entrega cada cambio. */
    public int paint(BrushWorld world, int centerX, int centerY, int centerZ, int radius, int shift, Sink sink) {
        int changes = 0;
        double limit = (radius + 0.5) * (radius + 0.5);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dy * dy + dz * dz > limit) {
                        continue;
                    }
                    int x = centerX + dx;
                    int y = centerY + dy;
                    int z = centerZ + dz;
                    if (!world.contains(x, y, z) || world.cell(x, y, z) == BrushWorld.Cell.AIR) {
                        continue;
                    }
                    Target target = target(world.blockId(x, y, z), shift);
                    if (target != null) {
                        sink.accept(x, y, z, target);
                        changes++;
                    }
                }
            }
        }
        return changes;
    }

    public static String normalize(String id) {
        if (id == null) {
            return null;
        }
        if (canonical(id)) {
            // Lo normal en cada bloque de una pasada: el id ya viene como lo da WorldEdit y no se copia.
            return id;
        }
        String value = id.trim().toLowerCase(Locale.ROOT);
        int bracket = value.indexOf('[');
        if (bracket >= 0) {
            value = value.substring(0, bracket);
        }
        return value.contains(":") ? value : "minecraft:" + value;
    }

    /** Si el id ya está como lo da WorldEdit: con espacio de nombres, en minúsculas, sin estado ni espacios. */
    private static boolean canonical(String id) {
        boolean namespace = false;
        for (int index = 0; index < id.length(); index++) {
            char character = id.charAt(index);
            if (character == ':') {
                namespace = true;
            } else if (character == '[' || character <= ' ' || character >= 'A' && character <= 'Z'
                    || character > 127) {
                return false;
            }
        }
        return namespace;
    }

    public enum Kind {
        FULL, SLAB, STAIRS
    }

    public record Target(int index, Kind kind) {
    }

    @FunctionalInterface
    public interface Sink {
        void accept(int x, int y, int z, Target target);
    }

    private record Entry(Kind kind, int first, int last) {
    }
}
