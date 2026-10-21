package com.komainu.moxia.performance;

import com.komainu.moxia.FullTests;
import com.komainu.moxia.edit.FakeStates;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.edit.PlacementRun;
import com.komainu.moxia.edit.PlanApplication;
import com.komainu.moxia.region.RegionReader;
import com.komainu.moxia.region.RegionSnapshot;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.world.block.BlockState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ediciones enormes (1, 5 y 20 millones de bloques) medidas fuera del juego, fase a fase: leer el mundo (la lectura de
 * {@code RegionOperationService}, {@link RegionReader}, sobre un mundo de mentira), calcular, empaquetar el plan y
 * recorrerlo para colocarlo. Da el coste por bloque de lo que hace KomaEdit (sin lo que cuesta Minecraft al leer o
 * escribir un bloque, que no se puede medir sin el juego) y la memoria del plan: la que queda y el pico al montarlo.
 *
 * <p>La segunda tabla es el peor tick al colocar con un modelo de lo que cuesta WorldEdit por bloque (supuesto, no
 * medido): antes, todo lo escrito esperaba en el búfer de la sesión hasta cerrarla y caía en un solo tick; ahora va
 * por ventanas ({@link PlacementRun}) con el presupuesto de cada tick ({@link TickBudget}).</p>
 *
 * <p>En cada build solo corre lo que no depende del reloj ni de la memoria del equipo: un plan de un millón de bloques
 * sale comprimido y exacto, y el modelo de colocación con un reloj de mentira nunca pasa del presupuesto. Las tablas
 * con tiempos y memoria salen con {@code KOMAEDIT_FULL_TESTS=1} ({@link FullTests}); {@code gradlew benchmark} las
 * saca con la memoria que piden los 20 millones y las escribe en {@code build/reports/benchmarks/huge-edits.md}.</p>
 */
class HugeEditBenchmarkTest {
    private static final int HEIGHT = 64;
    private static final int[] MILLIONS = {1, 5, 20};
    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();
    private static final BlockState STONE = FakeStates.fake();
    private static final BlockState DIRT = FakeStates.fake();
    private static final BlockState ORE = FakeStates.fake();
    private static final BlockState ANDESITE = FakeStates.fake();
    /** Modelo de WorldEdit por bloque: escribir en la sesión (patrón, historial, búfer). */
    private static final long WRITE_NANOS = 2_000L;
    /** Pasar del búfer al mundo. */
    private static final long FLUSH_NANOS = 1_000L;
    /** Luz, vecinos y envío a los clientes. */
    private static final long EFFECTS_NANOS = 4_000L;
    /** El presupuesto de serie ({@code tickBudgetMillis}). */
    private static final long BUDGET_NANOS = 6_000_000L;

    @Test
    void aMillionBlockPlanIsSmallAndExact() {
        int side = 125;
        PackedBlockPlan plan = pack(side);
        assertEquals(1_000_000, plan.size());
        assertTrue(plan.compressed());
        assertTrue(plan.footprintBytes() < 1_000_000L, "menos de un byte por bloque: " + plan.footprintBytes());
        int index = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++, index++) {
                    if (index % 997 == 0) {
                        assertEquals(PackedBlockPlan.pack(x, y, z), plan.packedPosition(index));
                        assertEquals(rawId(x, y, z), plan.expectedStateId(index));
                        assertSame(ANDESITE, plan.override(index));
                    }
                }
            }
        }
        assertEquals(1_000_000, apply(plan));
    }

    @Test
    void placingAMillionBlocksNeverPassesTheBudgetInTheModel() {
        Ticks result = ticks(1_000_000);
        assertTrue(result.worst() <= BUDGET_NANOS + EFFECTS_NANOS, "peor tick: " + result.worst());
        // 7 µs por bloque en ticks de 6 ms: unos 1 167 ticks, sin desperdiciar el presupuesto.
        long ideal = 1_000_000L * (WRITE_NANOS + FLUSH_NANOS + EFFECTS_NANOS) / BUDGET_NANOS;
        assertTrue(result.ticks() <= ideal * 102 / 100, "ticks: " + result.ticks() + " (ideal " + ideal + ")");
    }

    @Test
    void hugeEdits() throws IOException {
        assumeTrue(FullTests.ENABLED, "las tablas, con KOMAEDIT_FULL_TESTS=1 (gradlew benchmark)");
        // Los 20 millones piden unos 2 GB de margen; con menos (la JVM de las pruebas), hasta 5 millones.
        int[] millions = Runtime.getRuntime().maxMemory() >= 2_500L * 1024 * 1024 ? MILLIONS : new int[]{1, 5};
        // Calentamiento del JIT con la talla pequeña; no cuenta.
        run(1, false);
        List<String> rows = new ArrayList<>();
        rows.add("| Bloques | Leer (ns/bl) | Foto (MB) | Calcular (ns/bl) | Empaquetar (ns/bl) | Plan (MB) "
                + "| Plan (B/bl) | Pico al montar (MB) | Recorrer (ns/bl) |");
        rows.add("|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        for (int size : millions) {
            rows.add(run(size, true));
        }
        rows.add("");
        rows.add("| Bloques (desordenados) | Plan en memoria (MB) | Plan en disco (MB) | B/bl en memoria "
                + "| Pico al montar (MB) | Recorrer (ns/bl) |");
        rows.add("|---:|---:|---:|---:|---:|---:|");
        for (int size : millions) {
            rows.add(scattered(size * 1_000_000));
        }
        rows.add("");
        rows.add("| Bloques | Peor tick antes | Peor tick ahora | Ticks ahora | Duración ahora (a 20 TPS) |");
        rows.add("|---:|---:|---:|---:|---:|");
        for (int size : millions) {
            Ticks result = ticks(size * 1_000_000L);
            rows.add(String.format(Locale.ROOT, "| %,d | %s | %.2f ms | %,d | %s |", size * 1_000_000L,
                    duration(result.before()), result.worst() / 1_000_000.0, result.ticks(),
                    duration(result.ticks() * 50_000_000L)).replace(',', ' '));
        }
        String table = String.join("\n", rows);
        System.out.println(table);
        Path out = Path.of("build", "reports", "benchmarks", "huge-edits.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, table + "\n", StandardCharsets.UTF_8);
    }

    private static String run(int millions, boolean report) {
        int side = (int) Math.round(Math.sqrt(millions * 1_000_000.0 / HEIGHT));
        long volume = (long) side * HEIGHT * side;

        gc();
        long before = used();
        long start = System.nanoTime();
        RegionSnapshot snapshot = capture(side);
        double readNanos = (System.nanoTime() - start) / (double) volume;
        gc();
        double snapshotMegabytes = (used() - before) / 1_048_576.0;

        start = System.nanoTime();
        long changes = calculate(snapshot, side);
        double calculateNanos = (System.nanoTime() - start) / (double) volume;
        assertEquals(volume, changes);
        snapshot = null;

        gc();
        before = used();
        Peak peak = Peak.start(before);
        start = System.nanoTime();
        PackedBlockPlan plan = pack(side);
        double packNanos = (System.nanoTime() - start) / (double) volume;
        double peakMegabytes = peak.stop() / 1_048_576.0;
        gc();
        long planBytes = used() - before;

        start = System.nanoTime();
        long written = apply(plan);
        double applyNanos = (System.nanoTime() - start) / (double) volume;
        assertEquals(volume, written);
        if (!report) {
            return "";
        }
        return String.format(Locale.ROOT, "| %,d | %.1f | %.0f | %.1f | %.1f | %.0f | %.2f | %.0f | %.1f |",
                volume, readNanos, snapshotMegabytes, calculateNanos, packNanos, planBytes / 1_048_576.0,
                planBytes / (double) volume, peakMegabytes, applyNanos).replace(',', ' ');
    }

    /** La lectura de la captura, con el mundo de mentira. */
    private static RegionSnapshot capture(int side) {
        RegionReader reader = new RegionReader(new RegionReader.BlockSource() {
            @Override
            public int rawId(int x, int y, int z) {
                return HugeEditBenchmarkTest.rawId(x, y, z);
            }

            @Override
            public RegionSnapshot.StateInfo describe(int rawId, int x, int y, int z) {
                return info(rawId);
            }
        }, (x, y, z) -> x >= 0 && x < side && y >= 0 && y < HEIGHT && z >= 0 && z < side, 0, 0, 0, side, HEIGHT,
                side, false);
        while (!reader.step(65_536, Long.MAX_VALUE, System::nanoTime)) {
            // Un tramo por turno, como en el servidor.
        }
        return reader.build();
    }

    /** Lo que decide una operación de región por bloque: si está en la selección, qué hay y qué pone. */
    private static long calculate(RegionSnapshot snapshot, int side) {
        long changes = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    if (snapshot.selected(x, y, z) && snapshot.state(x, y, z) != ANDESITE) {
                        changes++;
                    }
                }
            }
        }
        return changes;
    }

    /** Un //set sobre la foto: cada bloque, una escritura condicional al estado leído. */
    private static PackedBlockPlan pack(int side) {
        long volume = (long) side * HEIGHT * side;
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder((int) Math.min(1 << 16, Math.max(16L, volume / 8)));
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    builder.addConditionalOverride(x, y, z, rawId(x, y, z), ANDESITE);
                }
            }
        }
        return builder.build();
    }

    private static long apply(PackedBlockPlan plan) {
        AtomicLong written = new AtomicLong();
        PlanApplication application = new PlanApplication(plan);
        PlanApplication.Target target = new PlanApplication.Target() {
            @Override
            public int rawIdAt(int x, int y, int z) {
                return HugeEditBenchmarkTest.rawId(x, y, z);
            }

            @Override
            public boolean write(int index, int x, int y, int z) {
                if (plan.override(index) == ANDESITE) {
                    written.incrementAndGet();
                }
                return true;
            }
        };
        try {
            while (!application.done()) {
                application.step(target, 32_768, Long.MAX_VALUE, System::nanoTime);
            }
        } catch (MaxChangedBlocksException exception) {
            throw new IllegalStateException(exception);
        }
        return written.get();
    }

    /**
     * El peor caso para la compresión: posiciones al azar (como un mapa hash), 300 estados esperados y 20 bloques
     * fijos distintos. Con el umbral de serie, lo que pasa de 128 MB va a un archivo temporal.
     */
    private static String scattered(int blocks) {
        BlockState[] states = FakeStates.fakes(20);
        gc();
        long before = used();
        Peak peak = Peak.start(before);
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(1 << 16);
        for (int index = 0; index < blocks; index++) {
            long hash = index * 0x9E3779B97F4A7C15L;
            hash ^= hash >>> 29;
            int x = (int) (hash & 4_095) - 2_048;
            int y = (int) ((hash >>> 12) % 384) - 64;
            int z = (int) ((hash >>> 24) & 4_095) - 2_048;
            builder.addConditionalOverride(x, y, z, (int) ((hash >>> 40) % 300), states[(int) ((hash >>> 50) % 20)]);
        }
        PackedBlockPlan plan = builder.build();
        double peakMegabytes = peak.stop() / 1_048_576.0;
        gc();
        long memory = used() - before;
        long start = System.nanoTime();
        long sum = 0;
        for (int index = 0; index < plan.size(); index++) {
            sum += plan.packedPosition(index) ^ plan.expectedStateId(index);
            if (plan.override(index) == null) {
                throw new IllegalStateException();
            }
        }
        double readNanos = (System.nanoTime() - start) / (double) blocks;
        assertTrue(sum != 0);
        return String.format(Locale.ROOT, "| %,d | %.0f | %.0f | %.2f | %.0f | %.1f |", blocks,
                memory / 1_048_576.0, plan.spilledBytes() / 1_048_576.0, memory / (double) blocks, peakMegabytes,
                readNanos).replace(',', ' ');
    }

    /**
     * Peor tick al colocar {@code blocks} bloques con el modelo de costes de WorldEdit y un reloj de mentira. Antes:
     * la cola escribía en la sesión (hasta 32 768 por tick) y todo lo demás caía al cerrarla. Ahora: ventanas con el
     * presupuesto de serie.
     */
    /** Peor tick (antes y ahora) y ticks que hacen falta, en nanosegundos del reloj de mentira. */
    private record Ticks(long before, long worst, long ticks) {
    }

    private static Ticks ticks(long blocks) {
        long beforeWorst = blocks * (FLUSH_NANOS + EFFECTS_NANOS);
        long[] now = {0L};
        long[] buffered = {0L};
        long[] effects = {0L};
        long[] left = {blocks};
        PlacementRun.Source source = new PlacementRun.Source() {
            @Override
            public int place(int maxEntries, long deadline) {
                int placed = 0;
                while (left[0] > 0 && placed < maxEntries && now[0] < deadline) {
                    now[0] += WRITE_NANOS;
                    buffered[0]++;
                    left[0]--;
                    placed++;
                }
                return placed;
            }

            @Override
            public boolean done() {
                return left[0] == 0;
            }
        };
        PlacementRun.Session session = () -> {
            boolean[] moved = {false};
            return deadline -> {
                if (!moved[0]) {
                    now[0] += buffered[0] * FLUSH_NANOS;
                    effects[0] += buffered[0];
                    buffered[0] = 0;
                    moved[0] = true;
                    return false;
                }
                while (effects[0] > 0 && now[0] < deadline) {
                    now[0] += EFFECTS_NANOS;
                    effects[0]--;
                }
                return effects[0] == 0;
            };
        };
        PlacementRun run = new PlacementRun(source, session, () -> now[0], null);
        long worst = 0;
        long ticks = 0;
        try {
            while (!run.finished()) {
                long start = now[0];
                run.step(start + BUDGET_NANOS, true);
                worst = Math.max(worst, now[0] - start);
                now[0] = start + 50_000_000L;
                ticks++;
            }
        } catch (MaxChangedBlocksException exception) {
            throw new IllegalStateException(exception);
        }
        return new Ticks(beforeWorst, worst, ticks);
    }

    private static String duration(long nanos) {
        double seconds = nanos / 1_000_000_000.0;
        if (seconds < 60.0) {
            return String.format(Locale.ROOT, "%.1f s", seconds);
        }
        return String.format(Locale.ROOT, "%d min %02d s", (long) seconds / 60, (long) seconds % 60);
    }

    /** Terreno de mentira: piedra abajo, tierra arriba y algún mineral suelto. */
    static int rawId(int x, int y, int z) {
        int hash = (x * 73_856_093) ^ (y * 19_349_663) ^ (z * 83_492_791);
        if ((hash & 127) == 0) {
            return 3;
        }
        return y < HEIGHT / 2 ? 1 : 2;
    }

    private static RegionSnapshot.StateInfo info(int rawId) {
        return switch (rawId) {
            case 1 -> new RegionSnapshot.StateInfo(1, STONE, "minecraft:stone", false, false, true);
            case 2 -> new RegionSnapshot.StateInfo(2, DIRT, "minecraft:dirt", false, false, true);
            default -> new RegionSnapshot.StateInfo(3, ORE, "minecraft:iron_ore", false, false, true);
        };
    }

    private static void gc() {
        for (int round = 0; round < 3; round++) {
            System.gc();
            try {
                Thread.sleep(60);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static long used() {
        return MEMORY.getHeapMemoryUsage().getUsed();
    }

    /** Pico de memoria muestreado cada milisegundo mientras se monta el plan. */
    private static final class Peak {
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicLong maximum = new AtomicLong();
        private final long baseline;
        private final Thread thread;

        private Peak(long baseline) {
            this.baseline = baseline;
            this.thread = new Thread(() -> {
                while (running.get()) {
                    maximum.accumulateAndGet(used(), Math::max);
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException exception) {
                        return;
                    }
                }
            }, "benchmark-peak");
            thread.setDaemon(true);
        }

        static Peak start(long baseline) {
            Peak peak = new Peak(baseline);
            peak.thread.start();
            return peak;
        }

        long stop() {
            maximum.accumulateAndGet(used(), Math::max);
            running.set(false);
            try {
                thread.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return Math.max(0L, maximum.get() - baseline);
        }
    }
}
