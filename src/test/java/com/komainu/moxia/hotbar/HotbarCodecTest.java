package com.komainu.moxia.hotbar;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exportar e importar barras como texto. */
class HotbarCodecTest {
    @Test
    void oneHotbarRoundTripsWithItsComponents() {
        SavedHotbar hotbar = HotbarStoreTest.sample("piedra").withIcon("lantern");
        String code = HotbarCodec.encode(hotbar);
        assertTrue(code.startsWith(HotbarCodec.PREFIX));
        assertEquals(List.of(hotbar), HotbarCodec.decode(code));
    }

    @Test
    void manyHotbarsGoInOneCode() {
        List<SavedHotbar> hotbars = List.of(HotbarStoreTest.sample("piedra"), HotbarStoreTest.sample("madera"));
        assertEquals(hotbars, HotbarCodec.decode(HotbarCodec.encode(hotbars)));
    }

    @Test
    void aHotbarOfBlocksFitsInTheChat() {
        SavedHotbar blocks = new SavedHotbar("castillo", "", List.of(
                HotbarItem.of("stone_bricks", 64), HotbarItem.of("mossy_stone_bricks", 64),
                HotbarItem.of("cracked_stone_bricks", 64), HotbarItem.of("stone_brick_stairs", 64),
                HotbarItem.of("stone_brick_slab", 64), HotbarItem.of("stone_brick_wall", 64),
                HotbarItem.of("spruce_planks", 64), HotbarItem.of("spruce_stairs", 64),
                HotbarItem.of("lantern", 64)));
        String code = HotbarCodec.encode(blocks);
        String command = "/komaedit hotbar import " + code;
        assertTrue(command.length() <= HotbarCodec.CHAT_LIMIT, "cabe en el chat: " + command.length() + " letras");
        assertEquals(List.of(blocks), HotbarCodec.decode(code));
    }

    @Test
    void spacesAndLineBreaksPastedOnTheWayAreIgnored() {
        SavedHotbar hotbar = HotbarStoreTest.sample("piedra");
        String code = HotbarCodec.encode(hotbar);
        String pasted = "  " + code.substring(0, 10) + "\n" + code.substring(10, 20) + " " + code.substring(20) + "\n";
        assertEquals(List.of(hotbar), HotbarCodec.decode(pasted));
    }

    @Test
    void damagedCodesAreRejectedWithAClearMessage() {
        String code = HotbarCodec.encode(HotbarStoreTest.sample("piedra"));
        assertMessage("empieza por kh1:", () -> HotbarCodec.decode("kp1:abc"));
        assertMessage("dañado", () -> HotbarCodec.decode("kh1:%%%"));
        assertMessage("incompleto", () -> HotbarCodec.decode(code.substring(0, code.length() / 2)));
        assertMessage("demasiado largo", () -> HotbarCodec.decode(HotbarCodec.PREFIX + "A".repeat(40_000)));
        assertMessage("no lleva barras", () -> HotbarCodec.decode(raw("{\"hotbars\":[]}")));
        assertMessage("no es un objeto", () -> HotbarCodec.decode(raw(
                "{\"hotbars\":[{\"name\":\"a\",\"slots\":[{\"id\":\"no vale\"}]}]}")));
        assertMessage("1 a 99", () -> HotbarCodec.decode(raw(
                "{\"hotbars\":[{\"name\":\"a\",\"slots\":[{\"id\":\"stone\",\"count\":500}]}]}")));
    }

    @Test
    void aCompressionBombIsCutShort() {
        String bomb = "{\"hotbars\":[{\"name\":\"a\",\"slots\":[],\"x\":\"" + "a".repeat(2_000_000) + "\"}]}";
        String code = raw(bomb);
        assertTrue(code.length() < HotbarCodec.MAX_TEXT, "el código cabe, pero descomprimido no");
        assertMessage("demasiado contenido", () -> HotbarCodec.decode(code));
    }

    @Test
    void exportingNothingIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> HotbarCodec.encode(List.of()));
        assertFalse(HotbarCodec.encode(new SavedHotbar("vacia", "", List.of())).isBlank(),
                "una barra vacía se puede compartir");
    }

    private static void assertMessage(String expected, org.junit.jupiter.api.function.Executable executable) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, executable);
        assertTrue(error.getMessage().contains(expected), error.getMessage());
    }

    /** Un código hecho a mano con este JSON. */
    private static String raw(String json) {
        byte[] input = json.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            output.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return HotbarCodec.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    }
}
