package com.komainu.moxia.blueprint;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El cliente solo guarda en {@code schematics} lo que el jugador pidió: cada exportación deja pasar un archivo durante
 * un rato, y lo que llega sin pedirlo se tira sin juntarlo ni avisar.
 */
class DownloadInboxTest {
    private static final String EXPORT = "komaedit blueprint export torre litematic";

    @Test
    void whatNobodyAskedForIsDroppedWithoutAWord() {
        DownloadInbox inbox = new DownloadInbox();
        for (FileChunks.Chunk chunk : FileChunks.split(1, "regalo.litematic", bytes(40_000, 1))) {
            assertTrue(inbox.receive(chunk, 0L).isEmpty(), "sin pedirlo no se junta nada");
        }
        inbox.expect("komaedit blueprint list", 0L);
        inbox.expect("komaedit blueprint export torre -servidor", 0L);
        for (FileChunks.Chunk chunk : FileChunks.split(2, "regalo.litematic", bytes(10, 2))) {
            assertTrue(inbox.receive(chunk, 10L).isEmpty(), "ni otras órdenes ni -servidor piden archivo");
        }
    }

    @Test
    void eachExportLetsOneFileIn() {
        DownloadInbox inbox = new DownloadInbox();
        inbox.expect(EXPORT, 0L);
        byte[] data = bytes(40_000, 3);
        assertArrayEquals(data, deliver(inbox, FileChunks.split(5, "torre.litematic", data), 100L)
                .orElseThrow().data());
        assertTrue(deliver(inbox, FileChunks.split(6, "otro.litematic", bytes(10, 4)), 200L).isEmpty(),
                "la exportación ya se gastó");
        inbox.expect(EXPORT, 2_000L);
        inbox.expect("/ke planos exportar seleccion nbt", 2_100L);
        assertEquals(2, inbox.waiting(2_100L));
        assertTrue(deliver(inbox, FileChunks.split(7, "a.litematic", bytes(10, 5)), 2_200L).isPresent());
        assertTrue(deliver(inbox, FileChunks.split(8, "b.nbt", bytes(10, 6)), 2_300L).isPresent());
        assertTrue(deliver(inbox, FileChunks.split(9, "c.nbt", bytes(10, 7)), 2_400L).isEmpty());
    }

    @Test
    void requestsExpireAndRepeatsCountOnce() {
        DownloadInbox inbox = new DownloadInbox();
        inbox.expect(EXPORT, 0L);
        inbox.expect(EXPORT, 500L);
        assertEquals(1, inbox.waiting(500L), "el botón y el chat ven pasar la misma orden: una petición");
        assertTrue(deliver(inbox, FileChunks.split(1, "tarde.litematic", bytes(10, 8)),
                DownloadPermits.TTL_MILLIS + 1).isEmpty(), "pasado el plazo ya no entra");
        long later = DownloadPermits.TTL_MILLIS * 2;
        for (int index = 0; index < 10; index++) {
            inbox.expect(EXPORT + " n" + index, later + index);
        }
        assertEquals(DownloadPermits.MAX_WAITING, inbox.waiting(later + 10), "como mucho cuatro esperando");
    }

    @Test
    void aBrokenRequestedFileIsReportedAndFreesItsTurn() {
        DownloadInbox inbox = new DownloadInbox();
        inbox.expect(EXPORT, 0L);
        List<FileChunks.Chunk> chunks = FileChunks.split(3, "torre.litematic", bytes(40_000, 9));
        inbox.receive(chunks.getFirst(), 0L);
        FileChunks.Chunk liar = new FileChunks.Chunk(3, "torre.litematic", chunks.getFirst().hash(), 1,
                chunks.size() + 1, new byte[1]);
        assertThrows(IllegalArgumentException.class, () -> inbox.receive(liar, 0L));
        assertTrue(inbox.receive(chunks.get(1), 0L).isEmpty(), "tras tirarlo, sus trozos ya no entran");
    }

    @Test
    void onlyExportsToTheClientAskForAFile() {
        assertTrue(DownloadPermits.requestsDownload("komaedit blueprint export torre"));
        assertTrue(DownloadPermits.requestsDownload("/ke planos exportar portapapeles nbt"));
        assertTrue(DownloadPermits.requestsDownload("mx plano export seleccion"));
        assertFalse(DownloadPermits.requestsDownload("komaedit blueprint export torre -servidor"));
        assertFalse(DownloadPermits.requestsDownload("komaedit blueprint export torre -s"));
        assertFalse(DownloadPermits.requestsDownload("komaedit blueprint import torre"));
        assertFalse(DownloadPermits.requestsDownload("komaedit share export plano:torre"));
        assertFalse(DownloadPermits.requestsDownload("tell alguien komaedit blueprint export torre"));
        assertFalse(DownloadPermits.requestsDownload(null));
    }

    private static Optional<FileChunks.Complete> deliver(DownloadInbox inbox, List<FileChunks.Chunk> chunks,
                                                         long now) {
        Optional<FileChunks.Complete> complete = Optional.empty();
        for (FileChunks.Chunk chunk : chunks) {
            complete = inbox.receive(chunk, now);
        }
        return complete;
    }

    private static byte[] bytes(int length, long seed) {
        byte[] data = new byte[length];
        new SplittableRandom(seed).nextBytes(data);
        return data;
    }
}
