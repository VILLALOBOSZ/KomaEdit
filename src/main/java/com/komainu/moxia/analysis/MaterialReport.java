package com.komainu.moxia.analysis;

import com.komainu.moxia.region.RegionSnapshot;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Lista de materiales de una selección: qué bloques hay (agrupados por bloque, sin propiedades) y lo que cuesta
 * reunirlos en survival ({@link SurvivalCost}), en unidades, pilas y cofres. Sin Minecraft.
 */
public final class MaterialReport {
    /** Huecos de un cofre normal (y de una caja de shulker). */
    public static final int CHEST_SLOTS = 27;

    private final long blocks;
    private final List<Placed> placed;
    private final List<Line> cost;

    private MaterialReport(long blocks, List<Placed> placed, List<Line> cost) {
        this.blocks = blocks;
        this.placed = placed;
        this.cost = cost;
    }

    /** Bloques colocados de un tipo. */
    public record Placed(String blockId, long count) {
    }

    /** Un objeto a reunir: unidades (redondeadas hacia arriba), tamaño de pila, pilas llenas y sueltos. */
    public record Line(String item, long amount, int maxStack) {
        public long stacks() {
            return amount / maxStack;
        }

        public long remainder() {
            return amount % maxStack;
        }

        /** Huecos de inventario que ocupa. */
        public long slots() {
            return (amount + maxStack - 1) / maxStack;
        }
    }

    /** Estado contado: id, propiedades y cuántas veces aparece. */
    public record Counted(String blockId, String properties, long count) {
    }

    /**
     * Cuenta los estados de la selección que no sean aire ni líquido y pasen el filtro.
     *
     * @param filter máscara ({@code null} acepta todo)
     */
    public static List<Counted> count(RegionSnapshot snapshot, Filter filter, BooleanSupplier cancelled) {
        Int2LongOpenHashMap counts = new Int2LongOpenHashMap();
        int checks = 0;
        for (int y = snapshot.minimumY(); y <= snapshot.maximumY(); y++) {
            for (int z = snapshot.minimumZ(); z <= snapshot.maximumZ(); z++) {
                if ((checks++ & 63) == 0 && cancelled.getAsBoolean()) {
                    throw new java.util.concurrent.CancellationException();
                }
                for (int x = snapshot.minimumX(); x <= snapshot.maximumX(); x++) {
                    if (!snapshot.selected(x, y, z)) {
                        continue;
                    }
                    RegionSnapshot.StateInfo info = snapshot.info(x, y, z);
                    if (info.air() || filter != null && !filter.test(x, y, z)) {
                        continue;
                    }
                    counts.addTo(info.rawId(), 1L);
                }
            }
        }
        List<Counted> result = new ArrayList<>(counts.size());
        for (Int2LongMap.Entry entry : counts.int2LongEntrySet()) {
            RegionSnapshot.StateInfo info = snapshot.stateInfo(entry.getIntKey());
            result.add(new Counted(info.blockId(), info.properties(), entry.getLongValue()));
        }
        return result;
    }

    @FunctionalInterface
    public interface Filter {
        boolean test(int x, int y, int z);
    }

    public static MaterialReport build(List<Counted> counted, SurvivalCost.Registry registry) {
        Map<String, Long> placed = new LinkedHashMap<>();
        Map<String, Double> needed = new LinkedHashMap<>();
        long blocks = 0;
        for (Counted entry : counted) {
            List<SurvivalCost.Cost> costs = SurvivalCost.of(entry.blockId(), entry.properties(), registry);
            if (costs.isEmpty() && isFluidOrAir(entry.blockId())) {
                continue;
            }
            placed.merge(SurvivalCost.normalize(entry.blockId()), entry.count(), Long::sum);
            blocks += entry.count();
            for (SurvivalCost.Cost cost : costs) {
                needed.merge(cost.item(), cost.amount() * entry.count(), Double::sum);
            }
        }
        List<Placed> placedList = new ArrayList<>();
        placed.forEach((id, count) -> placedList.add(new Placed(id, count)));
        placedList.sort(Comparator.comparingLong(Placed::count).reversed().thenComparing(Placed::blockId));
        List<Line> lines = new ArrayList<>();
        needed.forEach((item, amount) -> {
            // 0,5 de losa sobrante también hay que fabricarlo: se redondea hacia arriba.
            long rounded = (long) Math.ceil(amount - 1e-9);
            if (rounded > 0) {
                lines.add(new Line(item, rounded, Math.max(1, registry.maxStack(item))));
            }
        });
        lines.sort(Comparator.comparingLong(Line::amount).reversed().thenComparing(Line::item));
        return new MaterialReport(blocks, List.copyOf(placedList), List.copyOf(lines));
    }

    private static boolean isFluidOrAir(String blockId) {
        String id = SurvivalCost.normalize(blockId);
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air")
                || id.equals("minecraft:water") || id.equals("minecraft:lava") || id.equals("minecraft:bubble_column");
    }

    /** Bloques contados (sin aire ni líquidos). */
    public long blocks() {
        return blocks;
    }

    public List<Placed> placed() {
        return placed;
    }

    public List<Line> cost() {
        return cost;
    }

    public long totalItems() {
        return cost.stream().mapToLong(Line::amount).sum();
    }

    /** Huecos de inventario que hacen falta para todo. */
    public long totalSlots() {
        return cost.stream().mapToLong(Line::slots).sum();
    }

    /** Cofres normales (27 huecos) que llenaría todo. */
    public long chests() {
        return (totalSlots() + CHEST_SLOTS - 1) / CHEST_SLOTS;
    }

    /** {@code 1234} → {@code 19 pilas + 18}; {@code 40} → {@code 40}. */
    public static String stacks(Line line) {
        if (line.maxStack() <= 1) {
            return line.amount() + (line.amount() == 1 ? " unidad" : " unidades");
        }
        if (line.stacks() == 0) {
            return Long.toString(line.amount());
        }
        String stacks = line.stacks() + (line.stacks() == 1 ? " pila" : " pilas");
        if (line.maxStack() != 64) {
            stacks += " de " + line.maxStack();
        }
        return line.remainder() == 0 ? stacks : stacks + " + " + line.remainder();
    }

    /** Texto para pegar en cualquier sitio: una línea por objeto. */
    public String clipboard() {
        StringBuilder builder = new StringBuilder();
        builder.append("Lista de materiales (KomaEdit): ").append(blocks).append(" bloques, ")
                .append(totalSlots()).append(" huecos, ").append(chests()).append(" cofres\n");
        for (Line line : cost) {
            builder.append(display(line.item())).append(": ").append(line.amount()).append(" (")
                    .append(stacks(line)).append(")\n");
        }
        return builder.toString().strip();
    }

    /** CSV con punto y coma (lo abre bien una hoja de cálculo en español): coste y bloques colocados. */
    public String csv() {
        StringBuilder builder = new StringBuilder("seccion;id;cantidad;pila;pilas;sueltos;huecos\n");
        for (Line line : cost) {
            builder.append("coste;").append(line.item()).append(';').append(line.amount()).append(';')
                    .append(line.maxStack()).append(';').append(line.stacks()).append(';')
                    .append(line.remainder()).append(';').append(line.slots()).append('\n');
        }
        for (Placed entry : placed) {
            builder.append("colocado;").append(entry.blockId()).append(';').append(entry.count()).append(";;;;\n");
        }
        builder.append("total;bloques;").append(blocks).append(";;;;").append(totalSlots()).append('\n');
        builder.append("total;cofres;").append(chests()).append(";;;;\n");
        return builder.toString();
    }

    /** {@code minecraft:oak_planks} → {@code oak_planks}; los de otros mods conservan su espacio de nombres. */
    public static String display(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
