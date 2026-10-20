package com.komainu.moxia.image;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageRequestTest {
    @Test
    void defaultsAreStainedGlassWithFloydSteinberg() {
        ImageRequest request = ImageRequest.parse("logo.png");
        assertEquals("logo.png", request.source());
        assertFalse(request.isUrl());
        assertNull(request.width());
        assertNull(request.height());
        assertEquals("glass", request.palette());
        assertEquals(Dithering.FLOYD_STEINBERG, request.dithering());
        assertEquals(ImageRequest.Transparency.AIR, request.transparency());
        assertEquals(128, request.alphaThreshold());
        assertNull(request.anchor());
        assertFalse(request.selection());
    }

    @Test
    void positionalWidthAndPalette() {
        ImageRequest request = ImageRequest.parse("foto.jpg 64 concrete");
        assertEquals(64, request.width());
        assertNull(request.height());
        assertEquals("concrete", request.palette());
        ImageRequest both = ImageRequest.parse("foto.jpg 64x40 ##Grayscale");
        assertEquals(64, both.width());
        assertEquals(40, both.height());
        assertEquals("##Grayscale", both.palette());
    }

    @Test
    void flagsInAnyOrder() {
        ImageRequest request = ImageRequest.parse("-d bayer -h 48 \"mi foto.png\" -p wool -b 20 -c -30 -a keep -u 25 "
                + "-o bottomleft -r");
        assertEquals("mi foto.png", request.source());
        assertEquals(48, request.height());
        assertEquals("wool", request.palette());
        assertEquals(Dithering.BAYER, request.dithering());
        assertEquals(20, request.brightness());
        assertEquals(-30, request.contrast());
        assertEquals(ImageRequest.Transparency.KEEP, request.transparency());
        assertEquals(64, request.alphaThreshold());
        assertEquals(Anchor.BOTTOM_LEFT, request.anchor());
        assertTrue(request.surface());
    }

    @Test
    void transparentPixelsCanBeABlock() {
        ImageRequest request = ImageRequest.parse("icono.png -a white_concrete");
        assertEquals(ImageRequest.Transparency.BLOCK, request.transparency());
        assertEquals("white_concrete", request.transparentBlock());
        assertEquals(ImageRequest.Transparency.AIR, ImageRequest.parse("icono.png -a aire").transparency());
    }

    @Test
    void selectionAndStretch() {
        ImageRequest request = ImageRequest.parse("mural.png -e panes");
        assertTrue(request.selection());
        assertTrue(request.stretch());
        assertEquals("panes", request.palette());
        assertTrue(ImageRequest.parse("mural.png -s").selection());
    }

    @Test
    void urlsAndTheirShortName() {
        ImageRequest request = ImageRequest.parse("https://imagenes.ejemplo.com/arte/vitral.png?v=2 32");
        assertTrue(request.isUrl());
        assertEquals("vitral.png", request.displayName());
        assertEquals(32, request.width());
    }

    @Test
    void clearErrors() {
        List<String> inputs = List.of("", "foto.png -w", "foto.png -w 0", "foto.png -b 300", "foto.png -u 0",
                "foto.png -d mucho", "foto.png -o diagonal", "foto.png 64 wool extra", "foto.png -x",
                "foto.png -s -w 20", "foto.png -s -r", "\"foto.png");
        for (String input : inputs) {
            assertThrows(IllegalArgumentException.class, () -> ImageRequest.parse(input), input);
        }
    }

    @Test
    void tokensRespectQuotes() {
        assertEquals(List.of("a b.png", "-p", "wool"), ImageRequest.tokens("  \"a b.png\"   -p wool "));
        assertEquals(List.of(""), ImageRequest.tokens("\"\""));
    }
}
