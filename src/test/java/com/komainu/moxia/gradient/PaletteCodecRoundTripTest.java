package com.komainu.moxia.gradient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /komaedit palette encode} y {@code decode}: cada paleta de serie vuelve igual, y un código que no es de
 * KomaEdit (como los de ezEdits, cuyo formato no está publicado) se rechaza explicando qué pegar en su lugar.
 */
class PaletteCodecRoundTripTest {
    @Test
    void everyDefaultPaletteComesBackTheSame() {
        for (GradientPalette palette : GradientPalette.values()) {
            String spec = "##" + palette.displayName();
            assertEquals(PaletteSpec.parse(spec), PaletteCodec.decode(PaletteCodec.encode(PaletteSpec.parse(spec))),
                    spec);
        }
        // Los bloques de mods conservan su espacio de nombres; el de Minecraft se quita para acortar el código.
        String modded = "create:andesite_casing,minecraft:oak_stairs[facing=east],stone";
        assertEquals(PaletteSpec.parse("create:andesite_casing,oak_stairs[facing=east],stone"),
                PaletteCodec.decode(PaletteCodec.encode(PaletteSpec.parse(modded))));
    }

    @Test
    void foreignCodesSayWhatToPasteInstead() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PaletteCodec.decode("eNpLzs8tKEotLs5MztFLK8rPVUjKTM0rUUjOzM1JTEnNSU3OzEsBAP1lD3c"));
        assertTrue(error.getMessage().contains("kp1:"), error.getMessage());
        assertTrue(error.getMessage().contains("ezEdits"), error.getMessage());
    }
}
