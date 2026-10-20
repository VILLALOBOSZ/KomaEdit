package com.komainu.moxia.utility;

import com.komainu.moxia.region.RegionSnapshot;

/**
 * Drenar e inundar la selección, sin Minecraft.
 *
 * <ul>
 *     <li>Drenar quita el agua (fuente, corriente o columna de burbujas), la lava o las dos, y desanega los
 *     bloques anegados. Las algas y praderas marinas se van con el agua salvo que se pida conservarlas (fuera del
 *     agua se romperían en cuanto les llegue una actualización).</li>
 *     <li>Inundar anega todo lo que admite agua por debajo de un nivel (incluido): los bloques con
 *     {@code waterlogged} quedan anegados y el aire y el agua corriente pasan a ser agua quieta, salvo que se pida
 *     anegar solo los bloques.</li>
 * </ul>
 */
public final class FluidEngine {
    private FluidEngine() {
    }

    /** Qué líquido se drena. */
    public enum Drain {
        WATER, LAVA, ALL;

        boolean water() {
            return this != LAVA;
        }

        boolean lava() {
            return this != WATER;
        }
    }

    /** Destino de los cambios. */
    public interface Sink {
        /** Deja aire. */
        void air(int x, int y, int z);

        /** Deja agua quieta (una fuente). */
        void water(int x, int y, int z);

        /** Cambia la propiedad {@code waterlogged} del bloque que hay. */
        void waterlogged(int x, int y, int z, boolean value);
    }

    /** Filtro de la máscara; {@code null} acepta todo. */
    @FunctionalInterface
    public interface Filter {
        boolean test(int x, int y, int z);
    }

    public record Result(int removed, int unwaterlogged, int filled, int waterlogged) {
        public int total() {
            return removed + unwaterlogged + filled + waterlogged;
        }
    }

    public static Result drain(RegionSnapshot snapshot, BlockKinds kinds, Drain mode, boolean keepPlants,
                               Filter filter, Sink sink, Runnable checkCancelled) {
        int removed = 0;
        int unwaterlogged = 0;
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                checkCancelled.run();
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    if (!snapshot.selected(x, y, z) || filter != null && !filter.test(x, y, z)) {
                        continue;
                    }
                    int rawId = snapshot.rawId(x, y, z);
                    boolean remove = mode.water() && kinds.water(rawId)
                            || mode.lava() && kinds.lava(rawId)
                            || mode.water() && !keepPlants && kinds.waterOnly(rawId);
                    if (remove) {
                        sink.air(x, y, z);
                        removed++;
                    } else if (mode.water() && kinds.waterlogged(rawId)) {
                        sink.waterlogged(x, y, z, false);
                        unwaterlogged++;
                    }
                }
            }
        }
        return new Result(removed, unwaterlogged, 0, 0);
    }

    /**
     * @param level       última altura que se inunda
     * @param blocksOnly  solo anegar los bloques que lo admiten, sin llenar el aire
     */
    public static Result flood(RegionSnapshot snapshot, BlockKinds kinds, int level, boolean blocksOnly,
                               Filter filter, Sink sink, Runnable checkCancelled) {
        int filled = 0;
        int waterlogged = 0;
        int top = Math.min(level, snapshot.maximumY());
        for (int y = snapshot.minimumY(); y <= top; y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                checkCancelled.run();
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    if (!snapshot.selected(x, y, z) || filter != null && !filter.test(x, y, z)) {
                        continue;
                    }
                    RegionSnapshot.StateInfo info = snapshot.info(x, y, z);
                    int rawId = info.rawId();
                    if (kinds.waterloggable(rawId)) {
                        if (!kinds.waterlogged(rawId)) {
                            sink.waterlogged(x, y, z, true);
                            waterlogged++;
                        }
                    } else if (!blocksOnly && (info.air() || kinds.water(rawId) && !kinds.source(rawId))) {
                        sink.water(x, y, z);
                        filled++;
                    }
                }
            }
        }
        return new Result(0, 0, filled, waterlogged);
    }
}
