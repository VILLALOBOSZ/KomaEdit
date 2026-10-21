package com.komainu.moxia.share;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShareConfigTest {
    @Test
    void missingFieldsKeepTheDefaults() {
        ShareLimits limits = ShareConfig.read("{\"format\":1,\"importsPerHour\":5,\"allowFiles\":false}");
        assertEquals(5, limits.importsPerHour());
        assertFalse(limits.allowFiles());
        assertEquals(ShareLimits.DEFAULT.maxCodeChars(), limits.maxCodeChars());
        assertEquals(ShareLimits.DEFAULT.maxFileBytes(), limits.maxFileBytes());
    }

    @Test
    void nonsenseIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ShareConfig.read("{\"format\":2}"));
        assertThrows(IllegalArgumentException.class, () -> ShareConfig.read("{\"maxItems\":0}"));
        assertThrows(IllegalArgumentException.class, () -> ShareConfig.read("{\"maxCodeChars\":10}"));
    }

    @Test
    void textsFromOutsideLoseWhatCouldDisguiseThem() {
        assertEquals("Casa de Villa", ShareText.display("  §6Casa‮ de\n\tVilla​ ", 48));
        assertEquals("abc", ShareText.display("abcdef", 3));
        ShareCodecTest.assertMessage("no permitidos", () -> ShareText.setting("El preset", "oak\n/op yo", 100));
        ShareCodecTest.assertMessage("vacío", () -> ShareText.setting("El preset", " ", 100));
    }
}
