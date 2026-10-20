package com.komainu.moxia.brush.paint;

import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.terrain.HeightStamp;
import com.komainu.moxia.terrain.StampLibrary;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La huella como máscara de intensidad (círculo, cuadrado y sellos) y la lectura de las opciones del pincel. */
class PaintFootprintTest {
    @Test
    void theCircleIsStrongInTheMiddleAndSoftAtTheEdge() {
        PaintFootprint circle = PaintFootprint.circle();
        double previous = 2.0;
        for (double r = 0.0; r <= 1.0; r += 0.05) {
            double intensity = PaintFootprint.intensity(circle.shape(r, 0), 0.3);
            assertTrue(intensity <= previous + 1.0E-12, "baja hacia el borde");
            previous = intensity;
        }
        assertEquals(1.0, PaintFootprint.intensity(circle.shape(0.2, 0), 0.3), 1.0E-9, "centro lleno");
        assertEquals(0.0, PaintFootprint.intensity(circle.shape(1.0, 0), 0.3), 1.0E-9, "nada en el radio");
        assertEquals(0.0, PaintFootprint.intensity(circle.shape(0.8, 0.8), 0.3), 1.0E-9, "fuera del círculo");
        assertEquals(1.0, circle.extent());
    }

    @Test
    void hardnessGoesFromSoftToCut() {
        PaintFootprint circle = PaintFootprint.circle();
        double soft = PaintFootprint.intensity(circle.shape(0.6, 0), 0.0);
        double hard = PaintFootprint.intensity(circle.shape(0.6, 0), 1.0);
        assertTrue(soft < 0.5, "difuminado: " + soft);
        assertEquals(1.0, hard, 1.0E-9, "cortado");
        assertEquals(1.0, PaintFootprint.intensity(circle.shape(0.97, 0), 1.0), 1.0E-9);
    }

    @Test
    void theSquareReachesItsCorners() {
        PaintFootprint square = PaintFootprint.square();
        assertTrue(PaintFootprint.intensity(square.shape(0.6, 0.6), 0.6) > 0.5, "esquina pintada");
        assertEquals(0.0, square.shape(1.0, 0.2), 1.0E-9);
        assertEquals(Math.sqrt(2.0), square.extent(), 1.0E-9);
    }

    @Test
    void aStampIsUsedAsAnIntensityMask() {
        PaintFootprint hill = PaintFootprint.stamp(StampLibrary.render("colina", 64));
        assertEquals(1.0, hill.shape(0, 0), 0.02, "lo más alto del sello pinta del todo");
        assertTrue(hill.shape(0.5, 0) < hill.shape(0.2, 0), "y menos hacia el borde");
        assertEquals(0.0, hill.shape(0.99, 0.99), 1.0E-6, "fuera de la forma, nada");
        assertTrue(hill.extent() <= 1.1, "los de serie caben en su círculo: " + hill.extent());
        // Uno que hunde (gris neutro 1): lo hundido también pinta.
        PaintFootprint sinkhole = PaintFootprint.stamp(StampLibrary.render("dolina", 64));
        assertTrue(sinkhole.shape(0, 0) > 0.8, "el hoyo pinta");
        // El cráter (neutro 0,35): el borde alto pinta más que lo de fuera.
        PaintFootprint crater = PaintFootprint.stamp(StampLibrary.render("crater", 64));
        assertTrue(crater.shape(0.0, 0.0) > 0.0 && crater.shape(0.0, 0.0) <= 1.0);
    }

    @Test
    void theLibraryHasCircleSquareAndEveryStampAndPreparesEachOnce() {
        AtomicInteger loads = new AtomicInteger();
        HeightStamp stamp = StampLibrary.render("montana", 32);
        FootprintLibrary library = FootprintLibrary.of(new FootprintLibrary.StampSource() {
            @Override
            public List<String> names() {
                return List.of("montana", "circulo");
            }

            @Override
            public HeightStamp load(String name) throws IOException {
                loads.incrementAndGet();
                if (!name.equals("montana")) {
                    throw new IllegalArgumentException("No hay ningún sello «" + name + "»");
                }
                return stamp;
            }
        });
        assertEquals(List.of("circulo", "cuadrado", "montana"), library.names(), "sin repetir");
        PaintFootprint first = library.get("Montana");
        assertSame(first, library.get("montana"), "el mismo sello no se vuelve a medir");
        assertEquals(2, loads.get());
        assertSame(PaintFootprint.circle(), library.get("Círculo"));
        assertSame(PaintFootprint.square(), library.get("square"));
        assertThrows(IllegalArgumentException.class, () -> library.get("nada"));
        assertTrue(FootprintLibrary.builtIn().names().containsAll(StampLibrary.descriptions().keySet()));
    }

    @Test
    void theOptionsHaveGoodDefaultsAndReadInAnyOrder() {
        PaintConfig defaults = PaintConfig.defaults();
        assertEquals(PaintPalettes.DEFAULT, defaults.palette());
        assertEquals(PaintGradient.SLOPE, defaults.gradient());
        assertEquals(PaintConfig.DEFAULT_RADIUS, defaults.radius());
        assertEquals(0.6, defaults.strength(), 1.0E-9);
        assertEquals(FootprintLibrary.CIRCLE, defaults.footprint());
        assertNull(defaults.seed());
        assertTrue(defaults.surfaceOnly() && defaults.shaped() && defaults.autoHeights());

        PaintConfig config = parse("ruido 12 ##Grayscale 60 -h Montaña -s 1234 -k -e 50 -u 80 -j 0 -d 2 -t -c -x"
                + " -b grass_block,dirt -m #solid -y 120..60 -a 10:40 -n clouds -z 20 -p");
        assertEquals("##Grayscale", config.palette());
        assertEquals(12, config.radius());
        assertEquals(PaintGradient.NOISE, config.gradient());
        assertEquals(0.6, config.strength(), 1.0E-9);
        assertEquals("montana", config.footprint());
        assertEquals(1234L, config.seed());
        assertTrue(config.seedLocked() && config.preview() && config.everything());
        assertEquals(0.5, config.spacing(), 1.0E-9);
        assertEquals(0.8, config.hardness(), 1.0E-9);
        assertEquals(0.0, config.jitter(), 1.0E-9);
        assertEquals(2, config.layers());
        assertTrue(!config.surfaceOnly() && !config.shaped());
        assertEquals("grass_block,dirt", config.blocks());
        assertEquals("#solid", config.mask());
        assertEquals(60.0, config.heightLow());
        assertEquals(120.0, config.heightHigh(), "el orden da igual");
        assertEquals(10.0, config.slopeLow());
        assertEquals(40.0, config.slopeHigh());
        assertEquals("clouds", config.noise());
        assertEquals(20, config.noiseSize());
        assertEquals("-##Grayscale", parse("-##Grayscale").palette(), "una paleta invertida no es una bandera");
        assertEquals(PaintGradient.INTENSITY, parse("huella").gradient());
        assertEquals("nevado", parse("altura nevado").palette());
    }

    @Test
    void mistakesAreExplained() {
        assertThrows(IllegalArgumentException.class, () -> parse("40"), "radio fuera de rango");
        assertThrows(IllegalArgumentException.class, () -> parse("8 40 5"), "sobra un número");
        assertThrows(IllegalArgumentException.class, () -> parse("nevado musgo"), "dos paletas");
        assertThrows(IllegalArgumentException.class, () -> parse("-y 60"), "franja sin final");
        assertThrows(IllegalArgumentException.class, () -> parse("-y 60..60"), "franja vacía");
        assertThrows(IllegalArgumentException.class, () -> parse("-a 10..95"), "más de 90°");
    }

    private static PaintConfig parse(String input) {
        return PaintConfig.parse(BrushOptions.parse(input, PaintConfig.SWITCHES, PaintConfig.VALUED));
    }
}
