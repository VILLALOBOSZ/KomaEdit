package com.komainu.moxia.terrain;

import com.komainu.moxia.noise.NoiseEngine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los sellos de serie, generados por código con funciones y ruido de semilla fija: siempre salen iguales y no
 * vienen de ninguna imagen de nadie. Se guardan como PNG en {@code config/komaedit/heightmaps/} la primera vez para
 * que sirvan de plantilla, pero no hace falta el archivo para usarlos.
 *
 * <p>Cada uno ocupa el cuadrado de la imagen con el centro en medio y llega al gris neutro en el borde, para que el
 * pincel se funda con el terreno.</p>
 */
public final class StampLibrary {
    public static final int SIZE = 256;
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, HeightStamp> CACHE = new ConcurrentHashMap<>();

    static {
        add("montana", "Montaña con crestas y laderas surcadas", 0.0, StampLibrary::mountain);
        add("pico", "Pico afilado y solitario", 0.0, StampLibrary::peak);
        add("colina", "Colina suave y redonda", 0.0, (x, y, r) -> Math.exp(-r * r * 4.5) * window(r, 0.8));
        add("loma", "Loma alargada hacia la mirada, más empinada por delante", 0.0, StampLibrary::drumlin);
        add("cordillera", "Cordillera: una cresta larga con cumbres y estribaciones", 0.0, StampLibrary::range);
        add("macizo", "Macizo de varias cumbres juntas", 0.0, StampLibrary::massif);
        add("volcan", "Volcán con caldera y barrancos radiales", 0.0, StampLibrary::volcano);
        add("crater", "Cráter de impacto: borde alto y fondo hundido", 0.35, StampLibrary::crater);
        add("dolina", "Dolina: hoyo redondo que hunde el terreno", 1.0, StampLibrary::sinkhole);
        add("valle", "Valle en U a lo largo de la mirada (hunde)", 1.0, StampLibrary::valley);
        add("duna", "Duna en media luna (barján) con la cara empinada hacia la mirada", 0.0, StampLibrary::barchan);
        add("dunas", "Campo de dunas transversales", 0.0, StampLibrary::duneField);
        add("mesa", "Mesa de cima plana y bordes cortados", 0.0, StampLibrary::mesa);
        add("terrazas", "Colina escalonada en terrazas", 0.0, StampLibrary::terraces);
        add("acantilado", "Acantilado: una pared que se alza delante de ti", 0.0, StampLibrary::cliff);
        add("barrancos", "Ladera cruzada por barrancos", 0.0, StampLibrary::gullies);
        add("aguja", "Aguja de roca sobre un montículo", 0.0, StampLibrary::spire);
        add("domo", "Domo redondeado de granito", 0.0, StampLibrary::dome);
        add("atolon", "Atolón: anillo con cortes", 0.0, StampLibrary::atoll);
        add("circo", "Circo glaciar: herradura abierta hacia ti", 0.0, StampLibrary::cirque);
    }

    private StampLibrary() {
    }

    @FunctionalInterface
    private interface Shape {
        /** x e y de -1 a 1 (y hacia abajo en la imagen), r la distancia al centro. */
        double value(double x, double y, double r);
    }

    private record Entry(String name, String description, double neutral, Shape shape) {
    }

    private static void add(String name, String description, double neutral, Shape shape) {
        ENTRIES.put(name, new Entry(name, description, neutral, shape));
    }

    /** Nombres de los sellos de serie con su descripción, en orden. */
    public static Map<String, String> descriptions() {
        Map<String, String> result = new LinkedHashMap<>();
        ENTRIES.values().forEach(entry -> result.put(entry.name, entry.description));
        return Collections.unmodifiableMap(result);
    }

    public static boolean has(String name) {
        return ENTRIES.containsKey(name);
    }

    /** El sello de serie; lanza si no existe. */
    public static HeightStamp get(String name) {
        Entry entry = ENTRIES.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("No hay ningún sello de serie llamado " + name);
        }
        return CACHE.computeIfAbsent(name, key -> render(entry, SIZE));
    }

    /** Lo genera a otro tamaño (las pruebas lo usan pequeño). */
    public static HeightStamp render(String name, int size) {
        Entry entry = ENTRIES.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("No hay ningún sello de serie llamado " + name);
        }
        return render(entry, size);
    }

    private static HeightStamp render(Entry entry, int size) {
        float[] values = new float[size * size];
        for (int row = 0; row < size; row++) {
            double y = row / (size - 1.0) * 2.0 - 1.0;
            for (int column = 0; column < size; column++) {
                double x = column / (size - 1.0) * 2.0 - 1.0;
                double r = Math.sqrt(x * x + y * y);
                double value = entry.neutral;
                if (r < 1.0) {
                    double shaped = entry.shape.value(x, y, r);
                    // Cerca del borde todo tiende al gris neutro.
                    double edge = window(r, 0.82);
                    value = entry.neutral + (shaped - entry.neutral) * edge;
                }
                values[row * size + column] = (float) Math.clamp(value, 0.0, 1.0);
            }
        }
        return new HeightStamp(entry.name, size, size, values, entry.neutral);
    }

    // ------------------------------------------------------------------ utilidades

    /** 1 hasta {@code start}, baja suave a 0 en r = 1. */
    private static double window(double r, double start) {
        return smoothstep(1.0, start, r);
    }

    /** Hermite entre los bordes; admite {@code edge0 > edge1} para la curva al revés. */
    static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double fbm(double x, double y, long seed, int octaves) {
        return NoiseEngine.fractalBrownianMotion2D(x, y, seed, octaves, 2.0, 0.5);
    }

    private static double ridged(double x, double y, long seed, int octaves) {
        return NoiseEngine.ridged2D(x, y, seed, octaves);
    }

    private static double gauss(double x, double y, double cx, double cy, double sx, double sy) {
        double dx = (x - cx) / sx;
        double dy = (y - cy) / sy;
        return Math.exp(-(dx * dx + dy * dy));
    }

    // ------------------------------------------------------------------ sellos

    private static double mountain(double x, double y, double r) {
        double warpX = fbm(x * 1.7, y * 1.7, 11L, 3) * 0.18;
        double warpY = fbm(x * 1.7 + 5.2, y * 1.7 - 1.3, 13L, 3) * 0.18;
        double wx = x + warpX;
        double wy = y + warpY;
        double rr = Math.clamp(Math.sqrt(wx * wx + wy * wy), 0.0, 1.0);
        double base = Math.pow(Math.max(0.0, 1.0 - rr / 0.95), 1.5);
        double crests = ridged(wx * 2.6 + 7.0, wy * 2.6 - 3.0, 12L, 5);
        return (base * (0.45 + 0.55 * crests * crests) + 0.1 * Math.pow(base, 3.0)) / 1.1;
    }

    private static double peak(double x, double y, double r) {
        double warp = fbm(x * 3.0, y * 3.0, 21L, 3) * 0.06;
        double rr = Math.clamp(r + warp, 0.0, 1.0);
        double base = Math.pow(Math.max(0.0, 1.0 - rr / 0.92), 2.4);
        return base * (0.85 + 0.15 * ridged(x * 5.0, y * 5.0, 22L, 4));
    }

    private static double drumlin(double x, double y, double r) {
        // Más empinada por la parte de arriba de la imagen (hacia donde se mira).
        double sy = y < -0.1 ? 0.45 : 0.75;
        return gauss(x, y, 0.0, -0.1, 0.38, sy);
    }

    private static double range(double x, double y, double r) {
        double bend = fbm(y * 1.5, 3.0, 31L, 2) * 0.18;
        double across = (x - bend) / 0.2;
        double ridge = Math.exp(-across * across);
        double along = smoothstep(1.0, 0.55, Math.abs(y));
        double summits = 0.55 + 0.45 * (0.5 + 0.5 * fbm(1.0, y * 4.0, 32L, 3));
        double spurs = ridged(x * 4.0, y * 4.0, 33L, 4) * Math.exp(-across * across / 4.0) * 0.35;
        return (ridge * summits + spurs) * along;
    }

    private static double massif(double x, double y, double r) {
        double value = 0.0;
        value = Math.max(value, gauss(x, y, -0.05, -0.08, 0.32, 0.32));
        value = Math.max(value, 0.8 * gauss(x, y, 0.38, 0.12, 0.22, 0.24));
        value = Math.max(value, 0.72 * gauss(x, y, -0.36, 0.3, 0.24, 0.2));
        value = Math.max(value, 0.62 * gauss(x, y, 0.15, -0.45, 0.2, 0.22));
        value = Math.max(value, 0.55 * gauss(x, y, -0.45, -0.32, 0.2, 0.2));
        double detail = ridged(x * 4.5, y * 4.5, 41L, 5);
        return value * (0.7 + 0.3 * detail);
    }

    private static double volcano(double x, double y, double r) {
        double angle = Math.atan2(y, x);
        double gullies = 0.5 + 0.5 * Math.cos(angle * 14.0 + fbm(r * 4.0, angle, 51L, 2) * 3.0);
        double cone = Math.pow(Math.max(0.0, 1.0 - r / 0.95), 1.15);
        cone *= 1.0 - 0.08 * gullies * smoothstep(0.2, 0.6, r);
        double caldera = smoothstep(0.2, 0.06, r) * 0.42;
        return Math.max(0.0, cone - caldera) / 0.85;
    }

    private static double crater(double x, double y, double r) {
        double rr = r + fbm(x * 3.0, y * 3.0, 61L, 3) * 0.04;
        double rim = 0.35 + 0.65 * Math.exp(-Math.pow((rr - 0.55) / 0.11, 2.0));
        double bowl = smoothstep(0.5, 0.15, rr);
        double floor = 0.05 + 0.1 * Math.exp(-rr * rr * 60.0);
        return rr < 0.55 ? rim * (1.0 - bowl) + floor * bowl : rim;
    }

    private static double sinkhole(double x, double y, double r) {
        double rr = r + fbm(x * 2.5, y * 2.5, 71L, 2) * 0.05;
        return 1.0 - smoothstep(0.62, 0.15, rr);
    }

    private static double valley(double x, double y, double r) {
        double bend = fbm(y * 1.2, 5.0, 81L, 2) * 0.12;
        double across = Math.abs(x - bend);
        double depth = smoothstep(0.5, 0.22, across) * smoothstep(1.0, 0.6, Math.abs(y));
        return 1.0 - 0.95 * depth;
    }

    private static double barchan(double x, double y, double r) {
        // Viento de abajo hacia arriba de la imagen: barlovento suave abajo, sotavento empinado arriba.
        double shifted = y + 0.85 * x * x;
        double profile;
        if (shifted >= -0.05) {
            profile = smoothstep(0.75, -0.05, shifted);
        } else {
            profile = smoothstep(-0.3, -0.05, shifted);
        }
        double width = Math.exp(-Math.pow(x / 0.62, 2.0));
        return profile * width;
    }

    private static double duneField(double x, double y, double r) {
        double phase = y * 3.2 + fbm(x * 1.6, y * 0.8, 91L, 3) * 0.6;
        double t = phase - Math.floor(phase);
        double ridge = t < 0.75 ? t / 0.75 : (1.0 - t) / 0.25;
        double crest = smoothstep(0.0, 1.0, ridge);
        return crest * (0.6 + 0.4 * (0.5 + 0.5 * fbm(x * 2.0, y * 2.0, 92L, 2)));
    }

    private static double mesa(double x, double y, double r) {
        double rr = r + fbm(x * 2.2, y * 2.2, 101L, 4) * 0.12;
        double top = smoothstep(0.62, 0.55, rr);
        double talus = 0.22 * smoothstep(0.9, 0.58, rr);
        return Math.max(top, talus) * (0.97 + 0.03 * fbm(x * 8.0, y * 8.0, 102L, 2));
    }

    private static double terraces(double x, double y, double r) {
        double rr = r + fbm(x * 2.0, y * 2.0, 111L, 3) * 0.08;
        double hill = Math.exp(-rr * rr * 3.2);
        double steps = hill * 5.0;
        double level = Math.floor(steps);
        double fraction = steps - level;
        return (level + smoothstep(0.78, 1.0, fraction)) / 5.0;
    }

    private static double cliff(double x, double y, double r) {
        double edge = y - 0.05 + fbm(x * 3.0, 0.5, 121L, 3) * 0.12;
        double front = smoothstep(0.06, -0.04, edge);
        double back = smoothstep(-1.0, -0.4, y);
        double sides = smoothstep(0.95, 0.6, Math.abs(x));
        double top = 0.92 + 0.08 * fbm(x * 5.0, y * 5.0, 122L, 2);
        return front * back * sides * top;
    }

    private static double gullies(double x, double y, double r) {
        double hill = Math.exp(-r * r * 2.6);
        double channels = 1.0 - ridged(x * 3.5, y * 3.5, 131L, 4);
        return hill * (0.62 + 0.38 * channels);
    }

    private static double spire(double x, double y, double r) {
        double rr = r + fbm(x * 6.0, y * 6.0, 141L, 2) * 0.02;
        double needle = smoothstep(0.2, 0.12, rr);
        double mound = 0.3 * Math.exp(-rr * rr * 7.0);
        return Math.max(needle, mound);
    }

    private static double dome(double x, double y, double r) {
        double rr = r / 0.86;
        return rr >= 1.0 ? 0.0 : Math.sqrt(1.0 - rr * rr) * (0.98 + 0.02 * fbm(x * 6.0, y * 6.0, 151L, 2));
    }

    private static double atoll(double x, double y, double r) {
        double angle = Math.atan2(y, x);
        double ring = Math.exp(-Math.pow((r - 0.6) / 0.1, 2.0));
        double cuts = smoothstep(-0.55, -0.2, fbm(Math.cos(angle) * 2.0, Math.sin(angle) * 2.0, 161L, 3));
        return ring * (0.35 + 0.65 * cuts);
    }

    private static double cirque(double x, double y, double r) {
        double ring = Math.exp(-Math.pow((r - 0.62) / 0.2, 2.0));
        double open = smoothstep(-0.55, 0.35, y);
        return ring * (1.0 - open) * (0.8 + 0.2 * ridged(x * 4.0, y * 4.0, 171L, 3));
    }
}
