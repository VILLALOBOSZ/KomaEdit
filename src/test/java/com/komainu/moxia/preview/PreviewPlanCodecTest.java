package com.komainu.moxia.preview;

import com.komainu.moxia.edit.PackedBlockPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewPlanCodecTest {
    private record Sample(long[] positions, int[] states, byte[] kinds, byte[] faces) {
    }

    private static Sample random(int count, long seed) {
        Random random = new Random(seed);
        Map<Long, Integer> unique = new HashMap<>();
        while (unique.size() < count) {
            long packed = PackedBlockPlan.pack(random.nextInt(400) - 200, random.nextInt(380) - 64,
                    random.nextInt(400) - 200);
            unique.putIfAbsent(packed, unique.size());
        }
        long[] positions = new long[count];
        int[] states = new int[count];
        byte[] kinds = new byte[count];
        byte[] faces = new byte[count];
        for (Map.Entry<Long, Integer> entry : unique.entrySet()) {
            int index = entry.getValue();
            positions[index] = entry.getKey();
            states[index] = random.nextInt(30_000);
            kinds[index] = (byte) random.nextInt(3);
            faces[index] = (byte) (1 + random.nextInt(63));
        }
        return new Sample(positions, states, kinds, faces);
    }

    private static PreviewPlanCodec.Header header(int id, int entries) {
        return new PreviewPlanCodec.Header(id, -200, -64, -200, 199, 315, 199, 1_234_567L, 1_000_000L,
                234_567L, DisplaySampler.Mode.SURFACE, entries);
    }

    private static Map<Long, String> byPosition(long[] positions, int[] states, byte[] kinds, byte[] faces) {
        Map<Long, String> map = new HashMap<>();
        for (int index = 0; index < positions.length; index++) {
            map.put(positions[index], states[index] + "/" + kinds[index] + "/" + faces[index]);
        }
        return map;
    }

    @Test
    void roundTripKeepsEveryEntryAndTheHeader() {
        Sample sample = random(5_000, 7L);
        List<byte[]> parts = PreviewPlanCodec.encode(header(42, 5_000), sample.positions(), sample.states(),
                sample.kinds(), sample.faces(), PreviewPlanCodec.DEFAULT_PART_BYTES);
        PreviewPlanCodec.Plan plan = PreviewPlanCodec.decode(parts);
        assertEquals(header(42, 5_000), plan.header());
        assertEquals(5_000, plan.size());
        assertEquals(byPosition(sample.positions(), sample.states(), sample.kinds(), sample.faces()),
                byPosition(plan.positions(), plan.states(), plan.kinds(), plan.faces()));
    }

    /** {@code count} posiciones al azar dentro de una caja de 64×32×64, como una edición real. */
    private static Sample dense(int count, long seed) {
        Random random = new Random(seed);
        Map<Long, Integer> unique = new HashMap<>();
        while (unique.size() < count) {
            unique.putIfAbsent(PackedBlockPlan.pack(random.nextInt(64) - 30, 60 + random.nextInt(32),
                    random.nextInt(64) + 100), unique.size());
        }
        long[] positions = new long[count];
        int[] states = new int[count];
        byte[] kinds = new byte[count];
        byte[] faces = new byte[count];
        for (Map.Entry<Long, Integer> entry : unique.entrySet()) {
            int index = entry.getValue();
            positions[index] = entry.getKey();
            states[index] = random.nextInt(30_000);
            kinds[index] = (byte) random.nextInt(3);
            faces[index] = (byte) (1 + random.nextInt(63));
        }
        return new Sample(positions, states, kinds, faces);
    }

    @Test
    void partsRespectTheSizeAndAreCompact() {
        Sample sample = dense(20_000, 11L);
        int partBytes = 4_096;
        List<byte[]> parts = PreviewPlanCodec.encode(header(1, 20_000), sample.positions(), sample.states(),
                sample.kinds(), sample.faces(), partBytes);
        int total = 0;
        for (byte[] part : parts) {
            assertTrue(part.length <= partBytes, "parte de " + part.length + " bytes");
            total += part.length;
        }
        assertTrue(parts.size() > 10, "varias partes: " + parts.size());
        // Dos bytes de posición y tipo, uno de caras y el estado (hasta tres bytes), más las cabeceras.
        assertTrue(total < 20_000 * 7, "compacto: " + total + " bytes");
    }

    @Test
    void aDenseSectionSplitsAcrossParts() {
        int count = 16 * 16 * 16;
        long[] positions = new long[count];
        int[] states = new int[count];
        byte[] kinds = new byte[count];
        byte[] faces = new byte[count];
        for (int index = 0; index < count; index++) {
            positions[index] = PackedBlockPlan.pack(index & 15, 64 + (index >> 4 & 15), index >> 8);
            states[index] = 1_000 + index;
            faces[index] = 1;
        }
        List<byte[]> parts = PreviewPlanCodec.encode(header(3, count), positions, states, kinds, faces, 1_024);
        assertTrue(parts.size() > 10);
        PreviewPlanCodec.Plan plan = PreviewPlanCodec.decode(parts);
        assertEquals(byPosition(positions, states, kinds, faces),
                byPosition(plan.positions(), plan.states(), plan.kinds(), plan.faces()));
    }

    @Test
    void assemblerWaitsForEveryPartInAnyOrder() {
        Sample sample = random(3_000, 5L);
        List<byte[]> parts = PreviewPlanCodec.encode(header(9, 3_000), sample.positions(), sample.states(),
                sample.kinds(), sample.faces(), 2_048);
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            order.add(index);
        }
        Collections.shuffle(order, new Random(3));
        PreviewPlanCodec.Assembler assembler = new PreviewPlanCodec.Assembler();
        Optional<PreviewPlanCodec.Plan> result = Optional.empty();
        for (int slot = 0; slot < order.size(); slot++) {
            int part = order.get(slot);
            result = assembler.accept(9, part, parts.size(), parts.get(part));
            assertEquals(slot < order.size() - 1, result.isEmpty(), "solo al final");
        }
        assertEquals(3_000, result.orElseThrow().size());
        assertEquals(0, assembler.expected(), "queda vacío para el siguiente");
    }

    @Test
    void aNewPlanDropsTheOneHalfReceived() {
        Sample first = random(2_000, 1L);
        Sample second = random(500, 2L);
        List<byte[]> old = PreviewPlanCodec.encode(header(1, 2_000), first.positions(), first.states(),
                first.kinds(), first.faces(), 1_024);
        List<byte[]> fresh = PreviewPlanCodec.encode(header(2, 500), second.positions(), second.states(),
                second.kinds(), second.faces(), 1_024);
        PreviewPlanCodec.Assembler assembler = new PreviewPlanCodec.Assembler();
        assembler.accept(1, 0, old.size(), old.get(0));
        Optional<PreviewPlanCodec.Plan> result = Optional.empty();
        for (int part = 0; part < fresh.size(); part++) {
            result = assembler.accept(2, part, fresh.size(), fresh.get(part));
        }
        assertEquals(2, result.orElseThrow().header().id());
        assertEquals(500, result.orElseThrow().size());
    }

    @Test
    void brokenDataIsRejected() {
        Sample sample = random(100, 4L);
        List<byte[]> parts = PreviewPlanCodec.encode(header(5, 100), sample.positions(), sample.states(),
                sample.kinds(), sample.faces(), 4_096);
        byte[] cut = java.util.Arrays.copyOf(parts.getFirst(), parts.getFirst().length - 3);
        assertThrows(IllegalArgumentException.class, () -> PreviewPlanCodec.decode(List.of(cut)));
        assertThrows(IllegalArgumentException.class, () -> PreviewPlanCodec.decode(List.of(new byte[]{1, 2, 3})));
        assertThrows(IllegalArgumentException.class,
                () -> new PreviewPlanCodec.Assembler().accept(5, 3, 2, new byte[0]));
    }

    @Test
    void negativeAndHighCoordinatesSurvive() {
        long[] positions = {
                PackedBlockPlan.pack(-29_999_999, -64, 29_999_999),
                PackedBlockPlan.pack(-1, 319, -1),
                PackedBlockPlan.pack(0, 0, 0)
        };
        int[] states = {0, 27_000, 1};
        byte[] kinds = {0, 1, 2};
        byte[] faces = {63, 1, 32};
        PreviewPlanCodec.Plan plan = PreviewPlanCodec.decode(PreviewPlanCodec.encode(header(6, 3), positions,
                states, kinds, faces, 256));
        assertEquals(byPosition(positions, states, kinds, faces),
                byPosition(plan.positions(), plan.states(), plan.kinds(), plan.faces()));
        for (int index = 0; index < plan.size(); index++) {
            assertEquals(PreviewDisplays.Kind.values()[plan.kinds()[index]], plan.kind(index));
        }
    }
}
