package com.komainu.moxia.share;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ShareQuotaTest {
    @Test
    void eachPlayerHasItsOwnHourlyLimit() {
        AtomicLong clock = new AtomicLong(1_000_000L);
        ShareQuota quota = new ShareQuota(clock::get);
        for (int index = 0; index < 3; index++) {
            quota.takeImport(ShareFixtures.ALICE, 3);
        }
        ShareCodecTest.assertMessage("espera 60 min", () -> quota.takeImport(ShareFixtures.ALICE, 3));
        // Los demás y las exportaciones van aparte.
        assertDoesNotThrow(() -> quota.takeImport(ShareFixtures.BRUNO, 3));
        assertDoesNotThrow(() -> quota.takeExport(ShareFixtures.ALICE, 3));
        // Pasada la hora del primero, vuelve a poder.
        clock.addAndGet(59L * 60L * 1000L);
        ShareCodecTest.assertMessage("espera 1 min", () -> quota.takeImport(ShareFixtures.ALICE, 3));
        clock.addAndGet(60L * 1000L);
        assertDoesNotThrow(() -> quota.takeImport(ShareFixtures.ALICE, 3));
        ShareCodecTest.assertMessage("no se permiten importaciones", () -> quota.takeImport(ShareFixtures.BRUNO, 0));
    }
}
