package com.komainu.moxia.blueprint;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las miniaturas viajan por red en trozos: partir, encolar con presupuesto y volver a juntar. */
class ThumbnailChunksTest {
    private static byte[] data(int size, long seed) {
        byte[] data = new byte[size];
        new SplittableRandom(seed).nextBytes(data);
        return data;
    }

    @Test
    void aRealThumbnailIsSplitIntoSmallChunksAndJoinedBackInAnyOrder() {
        byte[] png = BlueprintThumbnail.png(BlueprintFixtures.ruin(7));
        List<ThumbnailChunks.Chunk> chunks = new ArrayList<>(ThumbnailChunks.split(png));
        assertEquals((png.length + ThumbnailChunks.CHUNK_BYTES - 1) / ThumbnailChunks.CHUNK_BYTES, chunks.size());
        chunks.forEach(chunk -> assertTrue(chunk.data().length <= ThumbnailChunks.CHUNK_BYTES));
        Collections.shuffle(chunks, new java.util.Random(5));
        ThumbnailChunks.Assembler assembler = new ThumbnailChunks.Assembler(8);
        Optional<byte[]> joined = Optional.empty();
        for (int index = 0; index < chunks.size(); index++) {
            joined = assembler.accept(chunks.get(index));
            assertEquals(index == chunks.size() - 1, joined.isPresent(), "solo al llegar el último");
        }
        assertArrayEquals(png, joined.orElseThrow());
        assertEquals(0, assembler.pending());
    }

    @Test
    void severalThumbnailsCanArriveInterleavedAndDuplicatesDoNotHurt() {
        byte[] first = data(10_000, 1);
        byte[] second = data(9_000, 2);
        List<ThumbnailChunks.Chunk> a = ThumbnailChunks.split(first);
        List<ThumbnailChunks.Chunk> b = ThumbnailChunks.split(second);
        ThumbnailChunks.Assembler assembler = new ThumbnailChunks.Assembler(8);
        List<byte[]> done = new ArrayList<>();
        for (int index = 0; index < Math.max(a.size(), b.size()); index++) {
            if (index < a.size()) {
                assembler.accept(a.get(index)).ifPresent(done::add);
                assembler.accept(a.get(index)).ifPresent(done::add);
            }
            if (index < b.size()) {
                assembler.accept(b.get(index)).ifPresent(done::add);
            }
        }
        assertEquals(2, done.size());
        assertArrayEquals(first, done.get(0));
        assertArrayEquals(second, done.get(1));
    }

    @Test
    void aTamperedChunkFailsTheHashAndIsDropped() {
        byte[] png = data(9_000, 3);
        List<ThumbnailChunks.Chunk> chunks = ThumbnailChunks.split(png);
        ThumbnailChunks.Chunk last = chunks.getLast();
        byte[] broken = last.data().clone();
        broken[0] ^= 0x55;
        ThumbnailChunks.Assembler assembler = new ThumbnailChunks.Assembler(4);
        for (int index = 0; index < chunks.size() - 1; index++) {
            assembler.accept(chunks.get(index));
        }
        assertTrue(assembler.accept(new ThumbnailChunks.Chunk(last.hash(), last.index(), last.total(), broken))
                .isEmpty());
        assertEquals(0, assembler.pending());
    }

    @Test
    void halfReceivedThumbnailsAreForgottenWhenTooManyPileUp() {
        ThumbnailChunks.Assembler assembler = new ThumbnailChunks.Assembler(2);
        List<List<ThumbnailChunks.Chunk>> thumbnails = new ArrayList<>();
        for (int seed = 0; seed < 3; seed++) {
            thumbnails.add(ThumbnailChunks.split(data(6_000, seed)));
            assembler.accept(thumbnails.getLast().getFirst());
        }
        assertEquals(2, assembler.pending());
        // La primera se olvidó: su último trozo ya no la completa.
        assertTrue(assembler.accept(thumbnails.getFirst().getLast()).isEmpty());
        assertTrue(assembler.accept(thumbnails.getLast().getLast()).isPresent());
    }

    @Test
    void chunksRejectBadHashesIndexesAndSizes() {
        String hash = BlueprintLibrary.sha256(new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> new ThumbnailChunks.Chunk("nope", 0, 1, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new ThumbnailChunks.Chunk(hash, 1, 1, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new ThumbnailChunks.Chunk(hash, 0, 0, new byte[1]));
        assertThrows(IllegalArgumentException.class,
                () -> new ThumbnailChunks.Chunk(hash, 0, 1, new byte[ThumbnailChunks.CHUNK_BYTES + 1]));
        assertThrows(IllegalArgumentException.class,
                () -> ThumbnailChunks.split(data(BlueprintLibrary.MAX_THUMBNAIL_BYTES + 1, 9)));
        assertEquals(1, ThumbnailChunks.split(new byte[0]).size(), "un PNG vacío es un trozo vacío");
    }

    @Test
    void theSendQueueSpendsItsBudgetPerTickAndDoesNotRepeatAThumbnail() {
        ThumbnailChunks.Queue queue = new ThumbnailChunks.Queue();
        List<ThumbnailChunks.Chunk> big = ThumbnailChunks.split(data(20_000, 4));
        assertTrue(queue.offer(big));
        assertFalse(queue.offer(big), "ya estaba en cola");
        assertEquals(2, queue.poll(9_000).size(), "dos trozos de 4 KiB caben en 9 KB");
        assertEquals(1, queue.poll(100).size(), "al menos uno por tick aunque no quepa");
        List<ThumbnailChunks.Chunk> rest = queue.poll(1_000_000);
        assertEquals(big.size() - 3, rest.size());
        assertTrue(queue.isEmpty());
        assertTrue(queue.offer(big), "terminada, se puede volver a pedir");
    }

    @Test
    void aThumbnailBeingReadOrSentIsNotReadAgain() {
        // Un cliente modificado puede pedir la misma miniatura sin parar: cada lectura se hace en disco y su huella
        // se calcula en el hilo del servidor.
        ThumbnailChunks.Queue queue = new ThumbnailChunks.Queue();
        byte[] png = data(20_000, 6);
        String hash = BlueprintLibrary.sha256(png);
        assertTrue(queue.request(hash));
        for (int repeat = 0; repeat < 1_000; repeat++) {
            assertFalse(queue.request(hash), "ya se está leyendo");
        }
        assertFalse(queue.isEmpty(), "mientras se lee, la cola no se da por terminada");
        assertTrue(queue.offer(ThumbnailChunks.split(png)));
        assertFalse(queue.request(hash), "en cola, tampoco");
        queue.poll(Integer.MAX_VALUE);
        assertTrue(queue.isEmpty());
        assertTrue(queue.request(hash), "enviada, se puede volver a pedir");
    }

    @Test
    void onlyAFewThumbnailsAreReadAtOnceAndAFinishedReadIsReleased() {
        ThumbnailChunks.Queue queue = new ThumbnailChunks.Queue();
        List<String> hashes = new ArrayList<>();
        for (int index = 0; index < ThumbnailChunks.Queue.MAX_PENDING; index++) {
            String hash = BlueprintLibrary.sha256(data(16, index));
            hashes.add(hash);
            assertTrue(queue.request(hash));
        }
        String extra = BlueprintLibrary.sha256(data(16, -1));
        assertFalse(queue.request(extra), "como mucho " + ThumbnailChunks.Queue.MAX_PENDING + " pendientes");
        queue.finished(hashes.getFirst());
        assertTrue(queue.request(extra), "la que terminó sin llegar deja sitio");
        assertFalse(queue.request(hashes.get(1)), "las demás siguen pendientes");
        assertFalse(queue.isEmpty());
    }

    @Test
    void payloadsSurviveTheNetworkCodec() {
        byte[] png = BlueprintThumbnail.png(BlueprintFixtures.well());
        ThumbnailChunks.Chunk chunk = ThumbnailChunks.split(png).getFirst();
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        BlueprintPayloads.Thumbnail.CODEC.encode(buffer, new BlueprintPayloads.Thumbnail(chunk));
        ThumbnailChunks.Chunk read = BlueprintPayloads.Thumbnail.CODEC.decode(buffer).chunk();
        assertEquals(chunk.hash(), read.hash());
        assertEquals(chunk.total(), read.total());
        assertArrayEquals(chunk.data(), read.data());

        BlueprintSummary summary = new BlueprintSummary("servidor/pozo", "pozo", true, false, "Bruno", 5L, 5, 6, 5,
                61, List.of("agua", "aldea"), BlueprintLibrary.sha256(png));
        PacketByteBuf catalogBuffer = new PacketByteBuf(Unpooled.buffer());
        BlueprintPayloads.Catalog.CODEC.encode(catalogBuffer,
                new BlueprintPayloads.Catalog(List.of(summary), true, false, true, false));
        BlueprintPayloads.Catalog catalog = BlueprintPayloads.Catalog.CODEC.decode(catalogBuffer);
        assertEquals(List.of(summary), catalog.blueprints());
        assertTrue(catalog.canSave() && !catalog.canPlace() && catalog.canStamp() && !catalog.canShare());

        PacketByteBuf requestBuffer = new PacketByteBuf(Unpooled.buffer());
        BlueprintPayloads.Request request = new BlueprintPayloads.Request(BlueprintPayloads.Request.THUMBNAILS,
                List.of(chunk.hash()));
        BlueprintPayloads.Request.CODEC.encode(requestBuffer, request);
        assertEquals(request, BlueprintPayloads.Request.CODEC.decode(requestBuffer));
    }
}
