package com.komainu.moxia.edit;

import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El plan comprimido por páginas y el volcado a disco dan exactamente las mismas entradas que el de arrays: posición,
 * bloque fijo (el mismo objeto), estado esperado, captura y pieza, en el mismo orden.
 */
class PlanStorageTest {
    private static final BlockState[] STATES = FakeStates.fakes(40);

    @TempDir
    Path spill;

    /** Una entrada para montarla igual con cualquier almacén. */
    private record Entry(int x, int y, int z, BlockState override, int expected, int capture, byte piece) {
    }

    private PackedBlockPlan.Storage compressed() {
        return new PackedBlockPlan.Storage(0, -1L, spill);
    }

    private PackedBlockPlan.Storage onDisk() {
        return new PackedBlockPlan.Storage(0, 0L, spill);
    }

    private static PackedBlockPlan build(List<Entry> entries, PackedBlockPlan.Storage storage) {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(entries.size(), storage);
        for (Entry entry : entries) {
            BlockVector3 position = BlockVector3.at(entry.x, entry.y, entry.z);
            if (entry.override != null) {
                if (entry.expected != PackedBlockPlan.NO_EXPECTED_STATE) {
                    builder.addConditionalCapturedOverride(position, entry.expected, entry.override, entry.capture);
                } else {
                    builder.addCapturedOverride(position, entry.override, entry.capture);
                }
            } else {
                if (entry.expected != PackedBlockPlan.NO_EXPECTED_STATE) {
                    builder.addConditionalCapturedPattern(position, entry.expected, entry.capture);
                } else {
                    builder.addCapturedPattern(position, entry.capture);
                }
                builder.setPiece(builder.size() - 1, entry.piece);
            }
        }
        return builder.build();
    }

    /** Un //set por filas sobre terreno: tramos largos en X, pocos estados. */
    private static List<Entry> scan(int sizeX, int sizeY, int sizeZ, boolean captured) {
        List<Entry> entries = new ArrayList<>();
        int capture = 0;
        for (int y = -64; y < -64 + sizeY; y++) {
            for (int z = -300; z < -300 + sizeZ; z++) {
                for (int x = 1_000; x < 1_000 + sizeX; x++) {
                    int expected = y < -40 ? 1 : (x + z) % 13 == 0 ? 7 : 2;
                    entries.add(new Entry(x, y, z, STATES[y & 3], expected,
                            captured ? capture++ : PackedBlockPlan.NO_CAPTURE, SmoothPiece.FULL));
                }
            }
        }
        return entries;
    }

    /** Desordenado: posiciones al azar (como un mapa hash), de todo un poco. */
    private static List<Entry> scattered(int count, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        List<Entry> entries = new ArrayList<>();
        byte stairs = SmoothPiece.stairs(SmoothPiece.EAST, true, SmoothPiece.OUTER_LEFT);
        for (int index = 0; index < count; index++) {
            int x = random.nextInt(-30_000_000, 30_000_000);
            int y = random.nextInt(-2_048, 2_048);
            int z = random.nextInt(-30_000_000, 30_000_000);
            boolean override = random.nextInt(3) == 0;
            int expected = random.nextInt(4) == 0 ? PackedBlockPlan.NO_EXPECTED_STATE : random.nextInt(30_000);
            int capture = random.nextInt(5) == 0 ? PackedBlockPlan.NO_CAPTURE : random.nextInt(1_000_000);
            byte piece = override ? SmoothPiece.FULL
                    : random.nextInt(4) == 0 ? stairs : random.nextInt(4) == 0 ? SmoothPiece.slab(true)
                    : SmoothPiece.FULL;
            entries.add(new Entry(x, y, z, override ? STATES[random.nextInt(STATES.length)] : null, expected,
                    capture, piece));
        }
        return entries;
    }

    private static void assertSameEntries(PackedBlockPlan expected, PackedBlockPlan actual) {
        assertEquals(expected.size(), actual.size());
        assertEquals(expected.captureSize(), actual.captureSize());
        assertEquals(expected.bounds(), actual.bounds());
        assertEquals(expected.usesOnlyPattern(), actual.usesOnlyPattern());
        assertEquals(expected.hasPieces(), actual.hasPieces());
        assertEquals(expected.pieceCount(), actual.pieceCount());
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.packedPosition(index), actual.packedPosition(index), "posición " + index);
            assertSame(expected.override(index), actual.override(index), "bloque fijo " + index);
            assertEquals(expected.expectedStateId(index), actual.expectedStateId(index), "esperado " + index);
            assertEquals(expected.captureIndex(index), actual.captureIndex(index), "captura " + index);
            assertEquals(expected.piece(index), actual.piece(index), "pieza " + index);
        }
        Iterator<BlockVector3> left = expected.iterator();
        Iterator<BlockVector3> right = actual.iterator();
        while (left.hasNext()) {
            assertEquals(left.next(), right.next());
        }
        assertFalse(right.hasNext());
    }

    @Test
    void compressedPlanKeepsEveryEntryOfARowScan() {
        List<Entry> entries = scan(70, 30, 50, true);
        PackedBlockPlan flat = build(entries, PackedBlockPlan.Storage.FLAT);
        PackedBlockPlan paged = build(entries, compressed());
        assertFalse(flat.compressed());
        assertTrue(paged.compressed());
        assertSameEntries(flat, paged);
        assertTrue(paged.footprintBytes() * 8 < flat.footprintBytes(),
                "un //set por filas ocupa mucho menos comprimido: " + paged.footprintBytes() + " frente a "
                        + flat.footprintBytes());
    }

    @Test
    void compressedPlanKeepsEveryEntryInAnyOrder() {
        List<Entry> entries = scattered(30_000, 7L);
        assertSameEntries(build(entries, PackedBlockPlan.Storage.FLAT), build(entries, compressed()));
    }

    @Test
    void spilledPlanGivesTheSameBlocksFromDisk() {
        List<Entry> entries = new ArrayList<>(scan(64, 20, 40, false));
        entries.addAll(scattered(9_000, 11L));
        PackedBlockPlan flat = build(entries, PackedBlockPlan.Storage.FLAT);
        PackedBlockPlan spilled = build(entries, onDisk());
        // No se cuentan archivos: en Linux el temporal sale de la carpeta nada más abrirse (se borra al abrir y se
        // sigue leyendo por el canal), en Windows se queda hasta cerrarlo.
        int pages = (entries.size() + PlanPages.PAGE - 1) / PlanPages.PAGE;
        assertEquals(pages, spilled.pagesOnDisk(), "con el umbral a 0 todas las páginas van a disco");
        assertTrue(spilled.spilledBytes() > 0);
        assertTrue(spilled.footprintBytes() < 4_096, "en memoria solo quedan las paletas y el índice: "
                + spilled.footprintBytes());
        assertEquals(0, flat.pagesOnDisk());
        assertSameEntries(flat, spilled);
        // Leer al revés obliga a traer cada página otra vez del disco.
        for (int index = flat.size() - 1; index >= 0; index -= 97) {
            assertEquals(flat.packedPosition(index), spilled.packedPosition(index));
            assertSame(flat.override(index), spilled.override(index));
        }
    }

    @Test
    void readsFromSeveralThreadsAtOnce() throws Exception {
        List<Entry> entries = scattered(20_000, 3L);
        entries.addAll(scan(50, 10, 40, true));
        PackedBlockPlan flat = build(entries, PackedBlockPlan.Storage.FLAT);
        PackedBlockPlan paged = build(entries, onDisk());
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                long seed = thread;
                results.add(pool.submit(() -> {
                    SplittableRandom random = new SplittableRandom(seed);
                    int mismatches = 0;
                    for (int read = 0; read < 50_000; read++) {
                        int index = random.nextInt(flat.size());
                        if (flat.packedPosition(index) != paged.packedPosition(index)
                                || flat.override(index) != paged.override(index)
                                || flat.expectedStateId(index) != paged.expectedStateId(index)
                                || flat.captureIndex(index) != paged.captureIndex(index)
                                || flat.piece(index) != paged.piece(index)) {
                            mismatches++;
                        }
                    }
                    return mismatches;
                }));
            }
            for (Future<Integer> result : results) {
                assertEquals(0, result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void smallPlansStayInArrays() {
        PackedBlockPlan plan = build(scan(10, 2, 10, false), PackedBlockPlan.Storage.DEFAULT);
        assertFalse(plan.compressed());
        assertEquals(0L, plan.spilledBytes());
    }

    @Test
    void piecesOfOldEntriesCannotChangeOnceCompressed() {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(10_000, compressed());
        for (int index = 0; index < 10_000; index++) {
            builder.addPattern(index, 0, 0);
        }
        byte slab = SmoothPiece.slab(false);
        builder.setPiece(9_999, slab);
        assertThrows(IllegalStateException.class, () -> builder.setPiece(10, slab));
        PackedBlockPlan plan = builder.build();
        assertEquals(slab, plan.piece(9_999));
        assertEquals(1, plan.pieceCount());
    }

    @Test
    void pageEncodingRoundTripsExtremeCoordinates() {
        long[] positions = {
                PackedBlockPlan.pack(-33_554_432, -2_048, -33_554_432),
                PackedBlockPlan.pack(33_554_431, 2_047, 33_554_431),
                PackedBlockPlan.pack(0, 0, 0),
                PackedBlockPlan.pack(1, 0, 0),
                PackedBlockPlan.pack(2, 0, 0),
                PackedBlockPlan.pack(-5, 300, 7)
        };
        long[] decoded = PlanPages.decodeRuns(PlanPages.runs(positions, positions.length), positions.length);
        for (int index = 0; index < positions.length; index++) {
            assertEquals(positions[index], decoded[index]);
        }
    }

    @Test
    void sweepRemovesLeftoverSpillFiles() throws IOException {
        Path leftover = Files.createFile(spill.resolve(PlanSpill.PREFIX + "123.bin"));
        Path other = Files.createFile(spill.resolve("otra-cosa.bin"));
        assertEquals(1, PlanSpill.sweep(spill));
        assertFalse(Files.exists(leftover));
        assertTrue(Files.exists(other));
    }
}
