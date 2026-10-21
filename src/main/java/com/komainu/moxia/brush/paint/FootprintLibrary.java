package com.komainu.moxia.brush.paint;

import com.komainu.moxia.terrain.HeightStamp;
import com.komainu.moxia.terrain.StampLibrary;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * De dónde salen las huellas del pincel de pintura: el círculo, el cuadrado y los sellos de la biblioteca de relieve.
 * El pincel solo ve esta interfaz, así que si la biblioteca de sellos cambia de forma basta con otra
 * {@link StampSource}: el pincel no se toca.
 *
 * <p>Cada huella se prepara una vez (se mide su alcance sobre la imagen) y se guarda mientras el sello sea el mismo
 * objeto: los sellos de una carpeta se recargan solos si cambia el archivo, y entonces se mide de nuevo.</p>
 */
public interface FootprintLibrary {
    String CIRCLE = "circulo";
    String SQUARE = "cuadrado";

    /** Las huellas que se pueden elegir: círculo, cuadrado y después los sellos. */
    List<String> names();

    /** La huella con ese nombre (sin tildes ni mayúsculas); lanza {@link IllegalArgumentException} si no existe. */
    PaintFootprint get(String name);

    /** Lo mínimo que hace falta de una biblioteca de sellos. */
    interface StampSource {
        List<String> names();

        HeightStamp load(String name) throws IOException;
    }

    /** Los sellos de serie de {@link StampLibrary}, sin disco: los del Studio y las pruebas. */
    static FootprintLibrary builtIn() {
        return BuiltIn.INSTANCE;
    }

    /** Círculo, cuadrado y los sellos de {@code stamps}. */
    static FootprintLibrary of(StampSource stamps) {
        return new Library(stamps);
    }

    /** Fuente de sellos con los de serie de {@link StampLibrary}. */
    static StampSource libraryStamps() {
        return new StampSource() {
            @Override
            public List<String> names() {
                return List.copyOf(StampLibrary.descriptions().keySet());
            }

            @Override
            public HeightStamp load(String name) {
                return StampLibrary.get(name);
            }
        };
    }

    /** Nombre escrito por el jugador a nombre de huella: sin tildes, en minúsculas y con los alias de serie. */
    static String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta la huella: circulo, cuadrado o un sello (montana, colina…)");
        }
        String plain = Normalizer.normalize(input.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace(' ', '_');
        return switch (plain) {
            case "circle", "circulo", "redondo", "round", "disco" -> CIRCLE;
            case "square", "cuadrado", "caja", "box" -> SQUARE;
            default -> plain;
        };
    }

    final class BuiltIn {
        private static final FootprintLibrary INSTANCE = of(libraryStamps());

        private BuiltIn() {
        }
    }

    /** La de serie: círculo y cuadrado delante de los sellos, y cada sello medido una vez. */
    final class Library implements FootprintLibrary {
        private final StampSource stamps;
        private final Map<String, Prepared> prepared = new ConcurrentHashMap<>();

        private record Prepared(HeightStamp stamp, PaintFootprint footprint) {
        }

        private Library(StampSource stamps) {
            this.stamps = stamps;
        }

        @Override
        public List<String> names() {
            List<String> names = new ArrayList<>(List.of(CIRCLE, SQUARE));
            for (String name : stamps.names()) {
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
            return names;
        }

        @Override
        public PaintFootprint get(String input) {
            String name = normalize(input);
            if (name.equals(CIRCLE)) {
                return PaintFootprint.circle();
            }
            if (name.equals(SQUARE)) {
                return PaintFootprint.square();
            }
            HeightStamp stamp;
            try {
                stamp = stamps.load(name);
            } catch (IOException exception) {
                throw new IllegalArgumentException("No se pudo leer el sello " + name + ": " + exception.getMessage(),
                        exception);
            }
            Prepared cached = prepared.get(name);
            if (cached != null && cached.stamp == stamp) {
                return cached.footprint;
            }
            PaintFootprint footprint = PaintFootprint.stamp(stamp);
            prepared.put(name, new Prepared(stamp, footprint));
            return footprint;
        }
    }
}
