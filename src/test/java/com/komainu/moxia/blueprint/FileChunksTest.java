package com.komainu.moxia.blueprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

/**
 * Los esquemas que bajan a la carpeta {@code schematics} del jugador y los que suben de ella: trozos, huella, tamaño,
 * nombres que no se salen de la carpeta y la cola con su presupuesto por tick.
 */
class FileChunksTest {
    @TempDir
    Path folder;

    @Test
    void aFileArrivesWholeInAnyOrder() {
        byte[] data = bytes(100_000, 1);
        List<FileChunks.Chunk> chunks = new ArrayList<>(FileChunks.split(7, "Casa grande.litematic", data));
        assertEquals(7, chunks.size());
        Collections.reverse(chunks);
        FileChunks.Assembler assembler = new FileChunks.Assembler(FileChunks.MAX_BYTES);
        Optional<FileChunks.Complete> complete = Optional.empty();
        for (int index = 0; index < chunks.size(); index++) {
            assertTrue(complete.isEmpty(), "llega solo con el último trozo");
            if (index < chunks.size() - 1) {
                assertTrue(assembler.accept(chunks.get(index), 0L).isEmpty());
                assertTrue(assembler.accept(chunks.get(index), 0L).isEmpty(), "un trozo repetido cuenta una vez");
            } else {
                complete = assembler.accept(chunks.get(index), 0L);
            }
        }
        assertTrue(complete.isPresent());
        assertEquals("Casa grande.litematic", complete.get().name());
        assertArrayEquals(data, complete.get().data());
        complete.get().verify();
        assertFalse(assembler.pending());
    }

    @Test
    void damagedTooBigOrMixedFilesAreRefused() {
        byte[] data = bytes(40_000, 2);
        List<FileChunks.Chunk> chunks = FileChunks.split(1, "torre.nbt", data);
        FileChunks.Chunk first = chunks.getFirst();
        FileChunks.Chunk tampered = new FileChunks.Chunk(1, "torre.nbt", first.hash(), 1, chunks.size(),
                new byte[chunks.get(1).data().length]);
        FileChunks.Assembler assembler = new FileChunks.Assembler(FileChunks.MAX_BYTES);
        assembler.accept(first, 0L);
        assembler.accept(tampered, 0L);
        IllegalArgumentException damaged = assertThrows(IllegalArgumentException.class,
                () -> assembler.accept(chunks.get(2), 0L).orElseThrow().verify());
        assertTrue(damaged.getMessage().contains("dañado"), damaged.getMessage());

        FileChunks.Assembler small = new FileChunks.Assembler(32 * 1024);
        IllegalArgumentException big = assertThrows(IllegalArgumentException.class,
                () -> small.accept(FileChunks.split(2, "enorme.schem", bytes(100_000, 3)).getFirst(), 0L));
        assertTrue(big.getMessage().contains("demasiado grande"), big.getMessage());

        FileChunks.Assembler mixed = new FileChunks.Assembler(FileChunks.MAX_BYTES);
        assertTrue(mixed.starts(chunks.getFirst()));
        mixed.accept(chunks.getFirst(), 0L);
        assertFalse(mixed.starts(chunks.get(1)), "el segundo trozo sigue el mismo envío");
        FileChunks.Chunk liar = new FileChunks.Chunk(1, "torre.nbt", first.hash(), 1, chunks.size() + 1,
                new byte[10]);
        assertThrows(IllegalArgumentException.class, () -> mixed.accept(liar, 0L));
        assertFalse(mixed.pending(), "lo que no cuadra tira el envío entero");

        assertThrows(IllegalArgumentException.class, () -> FileChunks.split(3, "x.nbt",
                new byte[FileChunks.MAX_BYTES + 1]));
    }

    @Test
    void aSharedPoolCapsHalfDoneTransfersAndBytesInFlight() {
        FileChunks.Pool pool = new FileChunks.Pool(2, 40 * 1024);
        FileChunks.Assembler first = new FileChunks.Assembler(FileChunks.MAX_BYTES, pool);
        FileChunks.Assembler second = new FileChunks.Assembler(FileChunks.MAX_BYTES, pool);
        FileChunks.Assembler third = new FileChunks.Assembler(FileChunks.MAX_BYTES, pool);
        List<FileChunks.Chunk> a = FileChunks.split(1, "a.nbt", bytes(100_000, 1));
        List<FileChunks.Chunk> b = FileChunks.split(2, "b.nbt", bytes(100_000, 2));
        first.accept(a.getFirst(), 0L);
        second.accept(b.getFirst(), 0L);
        assertEquals(2, pool.openTransfers());
        IllegalArgumentException crowded = assertThrows(IllegalArgumentException.class,
                () -> third.accept(FileChunks.split(3, "c.nbt", bytes(100, 3)).getFirst(), 0L));
        assertTrue(crowded.getMessage().contains("demasiados archivos"), crowded.getMessage());
        assertEquals(32 * 1024, pool.bytesInFlight());
        assertThrows(IllegalArgumentException.class, () -> first.accept(a.get(1), 0L),
                "48 KiB en vuelo no caben en 40");
        assertFalse(first.pending(), "el que no cabe se tira entero");
        assertEquals(1, pool.openTransfers());
        assertEquals(16 * 1024, pool.bytesInFlight());
        second.clear();
        assertEquals(0, pool.openTransfers());
        assertEquals(0, pool.bytesInFlight());
        assertTrue(third.accept(FileChunks.split(4, "c.nbt", bytes(100, 3)).getFirst(), 0L).isPresent());
        assertEquals(0, pool.bytesInFlight(), "uno que acaba devuelve su sitio");
    }

    @Test
    void aNewTransferReplacesAHalfDoneOneAndStaleOnesAreSeen() {
        FileChunks.Assembler assembler = new FileChunks.Assembler(FileChunks.MAX_BYTES);
        List<FileChunks.Chunk> old = FileChunks.split(1, "vieja.nbt", bytes(50_000, 4));
        assembler.accept(old.getFirst(), 1_000L);
        assertTrue(assembler.stale(2_000L));
        assertFalse(assembler.stale(500L));
        byte[] fresh = bytes(10, 5);
        assertArrayEquals(fresh, assembler.accept(FileChunks.split(2, "nueva.nbt", fresh).getFirst(), 3_000L)
                .orElseThrow().data());
    }

    @Test
    void namesStayInsideTheFolder() {
        assertTrue(FileChunks.validName("Casa del herrero (v2).litematic"));
        assertTrue(FileChunks.validName("pueblo_ñandú.nbt"));
        for (String bad : new String[]{"../casa.litematic", "casa/x.litematic", "casa\\x.nbt", "casa.exe",
                ".litematic", "con.nbt", "COM1.schem", "nul.tar.nbt", "", "a..b.schem", "casa.litematic ",
                // Windows reserva también los de cifras voladas, que \p{N} deja pasar.
                "com¹.litematic", "LPT³.nbt", "com².casa.schem"}) {
            assertFalse(FileChunks.validName(bad), bad);
            assertThrows(IllegalArgumentException.class, () -> new FileChunks.Chunk(1, bad, "0".repeat(64), 0, 1,
                    new byte[1]), bad);
        }
        assertEquals("casa_vieja.litematic", FileChunks.cleanName("casa/vieja.litematic"));
        assertTrue(FileChunks.validName(FileChunks.cleanName("..\u0000x.nbt")));
        assertEquals("esquema.nbt", FileChunks.cleanName("con.nbt"));
        assertEquals("esquema.litematic", FileChunks.cleanName("???"));
    }

    @Test
    void savedFilesNeverOverwrite() throws IOException {
        Path first = FileChunks.target(folder, "casa.litematic");
        assertEquals(folder.toAbsolutePath().normalize().resolve("casa.litematic"), first);
        Files.write(first, new byte[]{1});
        Path second = FileChunks.target(folder, "casa.litematic");
        assertEquals("casa-2.litematic", second.getFileName().toString());
        Files.write(second, new byte[]{2});
        assertEquals("casa-3.litematic", FileChunks.target(folder, "casa.litematic").getFileName().toString());
        assertThrows(IOException.class, () -> FileChunks.target(folder, "../fuera.nbt"));
    }

    @Test
    void theQueueSpendsItsBudgetAndHoldsFewFiles() {
        FileChunks.Queue queue = new FileChunks.Queue();
        for (int file = 0; file < FileChunks.Queue.MAX_FILES; file++) {
            assertTrue(queue.offer(FileChunks.split(file, "f" + file + ".nbt", bytes(40_000, file))));
        }
        assertFalse(queue.offer(FileChunks.split(9, "otro.nbt", bytes(10, 9))), "como mucho cuatro en cola");
        List<FileChunks.Chunk> tick = queue.poll(32 * 1024);
        assertEquals(2, tick.size(), "dos trozos de 16 KiB por tick");
        int sent = tick.size();
        while (!queue.isEmpty()) {
            sent += queue.poll(32 * 1024).size();
        }
        assertEquals(4 * 3, sent);
        assertTrue(queue.offer(FileChunks.split(10, "otro.nbt", bytes(10, 9))), "al vaciarse vuelve a admitir");
    }

    private static byte[] bytes(int length, long seed) {
        byte[] data = new byte[length];
        new SplittableRandom(seed).nextBytes(data);
        return data;
    }
}
