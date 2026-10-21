package com.komainu.moxia.share;

import com.google.gson.JsonObject;
import com.komainu.moxia.gradient.PaletteCodec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareCodecTest {
    /**
     * Un código {@code kpack1:} de la primera versión: una paleta, un ruido y un preset de árbol de VILLALOBOSZ. Las
     * versiones nuevas del mod tienen que seguir leyéndolo tal cual.
     */
    private static final String GOLDEN = "kpack1:QzYzKD85sVghJVUhLDMnJxFqQ5inj4-jj7-Tf3AU1DJDPUs9Q5iNZgZwgNvyIpDBuFwAzy9IeR-"
            + "UtbAHUHEpMJdjhlIAJJTSrAz0DIw0scZHYlJqST6mTkj5p2BkAooaQz1TUPQgSiigSbG1AA";

    private static SharePack sample() {
        JsonObject palette = new JsonObject();
        palette.addProperty("e", "stone;andesite;moss_block;tuff");
        JsonObject noise = new JsonObject();
        noise.addProperty("t", "Perlin(f:0.02)");
        JsonObject tree = new JsonObject();
        tree.addProperty("t", "spruce 24 -g 1.5 -h oak_leaves");
        return SharePack.of("Rocas de Villa", "VILLALOBOSZ", "1.9.1", 1_760_000_000_000L, List.of(
                new SharePack.Item("palette", "rocas", 1, palette),
                new SharePack.Item("noise", "suave", 1, noise),
                new SharePack.Item("tree", "abeto", 1, tree)));
    }

    @Test
    void codeRoundTripKeepsEverything() {
        SharePack pack = sample();
        String code = ShareCodec.toCode(pack);
        assertTrue(code.startsWith(ShareCodec.PREFIX), code);
        assertTrue(code.matches("kpack1:[A-Za-z0-9_-]+"), "solo caracteres que pasan por el chat: " + code);
        assertEquals(pack, ShareCodec.fromCode(code, ShareLimits.DEFAULT));
    }

    @Test
    void fileRoundTripKeepsBlobs() {
        byte[] schematic = new byte[70_000];
        new java.util.Random(7).nextBytes(schematic);
        JsonObject meta = new JsonObject();
        meta.addProperty("author", "VILLALOBOSZ");
        SharePack pack = sample().withItems(List.of(sample().items().getFirst(),
                new SharePack.Item("blueprint", "torre", 1, meta, schematic)));
        byte[] file = ShareCodec.toFile(pack);
        SharePack read = ShareCodec.fromFile(file, ShareLimits.DEFAULT);
        assertEquals(pack, read);
        assertArrayEquals(schematic, read.items().get(1).blob());
        // El código también puede llevar el adjunto, si cabe.
        assertEquals(pack, ShareCodec.fromCode(ShareCodec.toCode(pack), new ShareLimits(200_000, 1024 * 1024,
                16L * 1024 * 1024, 64, 64L * 1024 * 1024, 30, 60, true)));
    }

    @Test
    void aSmallPaletteFitsInTheChat() {
        JsonObject palette = new JsonObject();
        palette.addProperty("e", "stone;andesite;cobblestone;mossy_cobblestone;tuff;gravel;deepslate;calcite");
        String code = ShareCodec.toCode(SharePack.of("rocas", "VILLALOBOSZ", "1.9.1", 1_760_000_000_000L,
                List.of(new SharePack.Item("palette", "rocas", 1, palette))));
        assertTrue(code.length() <= ShareService.CHAT_CODE_CHARS, code.length() + " caracteres: " + code);
    }

    @Test
    void pastedCodesSurviveSpacesLineBreaksAndBackticks() {
        String code = ShareCodec.toCode(sample());
        String pasted = "```\n" + code.substring(0, 20) + "\n  " + code.substring(20) + " \n```";
        assertEquals(sample(), ShareCodec.fromCode(pasted, ShareLimits.DEFAULT));
    }

    @Test
    void theFirstVersionStillLoads() {
        assertEquals(sample(), ShareCodec.fromCode(GOLDEN, ShareLimits.DEFAULT));
    }

    @Test
    void theDictionaryNeverChanges() throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ShareCodec.DICTIONARY));
        assertEquals("efd3334bf5af4eb2328a28a4df0e156543572ead5c04772ddbb3a199295656dc", hash, "cambiar el diccionario rompe los códigos kpack1: ya repartidos");
    }

    @Test
    void oldPaletteCodesLoadAsAPack() {
        String old = PaletteCodec.encode(List.of("minecraft:stone", "andesite", "oak_stairs[facing=east]"));
        SharePack pack = ShareCodec.fromCode(old, ShareLimits.DEFAULT);
        assertEquals(1, pack.items().size());
        SharePack.Item item = pack.items().getFirst();
        assertEquals("palette", item.kind());
        assertEquals(ShareCodec.LEGACY_PALETTE_NAME, item.name());
        assertEquals("stone;andesite;oak_stairs[facing=east]", item.data().get("e").getAsString());
    }

    @Test
    void oldRecipePackagesLoadAsAPack() throws IOException {
        byte[] zip = zip("recipe.json", "{\"version\":1,\"name\":\"muro\",\"shape\":\"wall\"}");
        SharePack pack = ShareCodec.fromFile(zip, ShareLimits.DEFAULT);
        assertEquals("recipe", pack.items().getFirst().kind());
        assertEquals("muro", pack.items().getFirst().name());
    }

    @Test
    void newerVersionsAreRefusedWithAClearMessage() {
        String newer = encode("{\"f\":2,\"i\":[{\"k\":\"palette\",\"n\":\"a\",\"d\":{\"e\":\"stone\"}}]}");
        assertMessage("más nueva", () -> ShareCodec.fromCode(newer, ShareLimits.DEFAULT));
        assertMessage("más nueva", () -> ShareCodec.fromCode("kpack2:AAAA", ShareLimits.DEFAULT));
    }

    @Test
    void brokenCodesFailCleanly() {
        String code = ShareCodec.toCode(sample());
        assertMessage("Base64", () -> ShareCodec.fromCode("kpack1:no*es*base64", ShareLimits.DEFAULT));
        assertMessage("cópialo entero", () -> ShareCodec.fromCode(code.substring(0, code.length() / 2),
                ShareLimits.DEFAULT));
        assertMessage("empieza por", () -> ShareCodec.fromCode("hola", ShareLimits.DEFAULT));
        assertMessage("Falta", () -> ShareCodec.fromCode("  ", ShareLimits.DEFAULT));
        assertMessage("JSON", () -> ShareCodec.fromCode(encode("{no es json"), ShareLimits.DEFAULT));
        assertMessage("objeto", () -> ShareCodec.fromCode(encode("[1,2,3]"), ShareLimits.DEFAULT));
        assertMessage("vacío", () -> ShareCodec.fromCode(encode("{\"f\":1,\"i\":[]}"), ShareLimits.DEFAULT));
        assertMessage("tipo inesperado", () -> ShareCodec.fromCode(
                encode("{\"f\":1,\"i\":[{\"k\":\"palette\",\"n\":{\"x\":1}}]}"), ShareLimits.DEFAULT));
        assertMessage("dañado", () -> ShareCodec.fromCode(
                encode("{\"f\":1,\"i\":[{\"k\":\"../x\",\"n\":\"a\"}]}"), ShareLimits.DEFAULT));
        assertMessage("dañado", () -> ShareCodec.fromCode(
                encode("{\"f\":1,\"i\":[{\"k\":\"blueprint\",\"n\":\"a\",\"b\":\"%%%\"}]}"), ShareLimits.DEFAULT));
        String deep = "[".repeat(200_000) + "]".repeat(200_000);
        assertMessage("dañado", () -> ShareCodec.fromCode(encode("{\"f\":1,\"i\":" + deep + "}"),
                new ShareLimits(32_000, 4L * 1024 * 1024, 16L * 1024 * 1024, 64, 64L * 1024 * 1024, 30, 60, true)));
    }

    @Test
    void sizesAreCappedBeforeTheyHurt() {
        // Más largo que el máximo: ni se mira.
        assertMessage("caracteres", () -> ShareCodec.fromCode("kpack1:" + "A".repeat(40_000), ShareLimits.DEFAULT));
        // Una bomba: pocos caracteres que se descomprimen en megas.
        String bomb = encode("{\"f\":1,\"n\":\"" + " ".repeat(3 * 1024 * 1024) + "\"}");
        assertTrue(bomb.length() < 32_000, bomb.length() + " caracteres");
        assertMessage("KiB", () -> ShareCodec.fromCode(bomb, ShareLimits.DEFAULT));
        // Demasiadas cosas.
        StringBuilder items = new StringBuilder("{\"f\":1,\"i\":[");
        for (int index = 0; index < 65; index++) {
            items.append(index == 0 ? "" : ",").append("{\"k\":\"noise\",\"n\":\"r").append(index).append("\"}");
        }
        assertMessage("máximo es 64", () -> ShareCodec.fromCode(encode(items + "]}"), ShareLimits.DEFAULT));
        // Un archivo más grande que el límite.
        assertMessage("KiB", () -> ShareCodec.fromFile(new byte[(int) ShareLimits.DEFAULT.maxFileBytes() + 1],
                ShareLimits.DEFAULT));
    }

    @Test
    void zipEntriesCannotLeaveTheirFolder() throws IOException {
        for (String evil : List.of("../evil.json", "blobs/../../evil.bin", "/etc/passwd", "C:/Windows/x.bin",
                "blobs\\..\\x.bin", "./pack.json", "blobs//0.bin")) {
            byte[] zip = zip("pack.json", "{\"f\":1,\"i\":[{\"k\":\"noise\",\"n\":\"a\",\"d\":{\"t\":\"Perlin()\"}}]}",
                    evil, "x");
            assertMessage("ruta no permitida", () -> ShareCodec.fromFile(zip, ShareLimits.DEFAULT));
        }
        byte[] repeated = duplicated("pack.json", "{}");
        assertThrows(IllegalArgumentException.class, () -> ShareCodec.fromFile(repeated, ShareLimits.DEFAULT));
        byte[] missingBlob = zip("pack.json", "{\"f\":1,\"i\":[{\"k\":\"blueprint\",\"n\":\"a\",\"b\":\"blobs/9.bin\"}]}");
        assertMessage("falta", () -> ShareCodec.fromFile(missingBlob, ShareLimits.DEFAULT));
        byte[] notAPack = zip("hola.txt", "hola");
        assertMessage("no es un paquete", () -> ShareCodec.fromFile(notAPack, ShareLimits.DEFAULT));
        assertMessage("dañado", () -> ShareCodec.fromFile(new byte[]{'P', 'K', 3, 4, 1, 2, 3}, ShareLimits.DEFAULT));
    }

    @Test
    void aZipBombInAnIgnoredEntryIsStillCapped() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            zip.putNextEntry(new ZipEntry("pack.json"));
            zip.write("{\"f\":1,\"i\":[{\"k\":\"noise\",\"n\":\"a\",\"d\":{\"t\":\"Perlin()\"}}]}"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("extra/zeros.bin"));
            byte[] zeros = new byte[1024 * 1024];
            for (int index = 0; index < 40; index++) {
                zip.write(zeros);
            }
            zip.closeEntry();
        }
        assertTrue(output.size() < ShareLimits.DEFAULT.maxFileBytes());
        assertMessage("demasiado", () -> ShareCodec.fromFile(output.toByteArray(), ShareLimits.DEFAULT));
    }

    @Test
    void displayTextsLoseFormattingCodesAndDirectionTricks() {
        String code = encode("{\"f\":1,\"n\":\"§4Casa\\u202Eevil\\nrara\",\"a\":\"Vi§kllo\",\"i\":[{\"k\":\"noise\","
                + "\"n\":\"a\",\"d\":{\"t\":\"Perlin()\"}}]}");
        SharePack pack = ShareCodec.fromCode(code, ShareLimits.DEFAULT);
        assertEquals("Casaevil rara", pack.name());
        assertEquals("Villo", pack.author());
    }

    /** Un código con el JSON que se quiera, como lo escribiría {@link ShareCodec#toCode}. */
    static String encode(String json) {
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setDictionary(ShareCodec.DICTIONARY);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            output.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return ShareCodec.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    }

    static byte[] zip(String... entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int index = 0; index + 1 < entries.length; index += 2) {
                zip.putNextEntry(new ZipEntry(entries[index]));
                zip.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    /** Un ZIP con la misma entrada dos veces (ZipOutputStream no lo deja hacer): dos ZIP de una entrada pegados. */
    private static byte[] duplicated(String name, String content) throws IOException {
        byte[] one = zip(name, content);
        byte[] two = zip(name, content);
        // ZipInputStream lee las cabeceras locales en orden: la segunda copia va detrás de la primera.
        int end = localEnd(one);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(one, 0, end);
        output.write(two);
        return output.toByteArray();
    }

    /** Dónde empieza el directorio central (lo de antes son las entradas). */
    private static int localEnd(byte[] zip) {
        for (int index = 0; index + 4 <= zip.length; index++) {
            if (zip[index] == 'P' && zip[index + 1] == 'K' && zip[index + 2] == 1 && zip[index + 3] == 2) {
                return index;
            }
        }
        return zip.length;
    }

    static void assertMessage(String expected, org.junit.jupiter.api.function.Executable executable) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, executable);
        assertTrue(exception.getMessage().contains(expected), "«" + exception.getMessage() + "» no dice «"
                + expected + "»");
    }
}
