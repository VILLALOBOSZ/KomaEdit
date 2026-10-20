package com.komainu.moxia.terrain;

import com.komainu.moxia.terrain.stamp.StampCategory;
import com.komainu.moxia.terrain.stamp.StampInfo;
import com.komainu.moxia.terrain.stamp.StampRecipe;
import com.komainu.moxia.terrain.stamp.StampRecipes;
import com.komainu.moxia.terrain.stamp.StampThumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La biblioteca de sellos de serie: 80 relieves en 14 categorías, generados por código con ruido por capas, crestas
 * y valles y con la erosión hidráulica y térmica horneada ({@link StampRecipe}). Nada sale de imágenes de nadie.
 *
 * <p>Van ya generados dentro del jar como PNG de 16 bits de {@value #SIZE}×{@value #SIZE} ({@value #RESOURCE}): unos
 * 1,6 MB que se leen al primer uso de cada sello en unos milisegundos. Generarlos al arrancar costaría unos 12 s de
 * CPU en cada cliente y servidor la primera vez, y la erosión es caótica: dos Java distintas podrían dar cauces algo
 * distintos y la miniatura del cliente no sería exactamente lo que estampa el servidor. Si falta un PNG (al
 * desarrollar) el sello se genera en el momento; la prueba {@code StampResourcesDump} los vuelve a escribir desde las
 * recetas.</p>
 *
 * <p>Es también la API para otras herramientas: {@link #all()} (nombre, categoría, descripción, altura natural),
 * {@link #get} (el sello), {@link #thumbnail} (miniatura en color) y, para muestrear con giro, escala, espejo y
 * semilla, {@link com.komainu.moxia.terrain.stamp.StampVariation} y
 * {@link com.komainu.moxia.terrain.stamp.StampPlacement}.</p>
 */
public final class StampLibrary {
    /** Lado en píxeles de los sellos de serie. */
    public static final int SIZE = 160;
    /** Carpeta de los PNG dentro del jar. */
    public static final String RESOURCE = "/assets/komaedit/stamps/";
    /** Precisión con la que se guardan: de sobra (1/4096 de la altura) y el PNG ocupa un cuarto menos. */
    public static final int STORED_BITS = 12;
    private static final Logger LOGGER = LoggerFactory.getLogger("komaedit");
    private static final Map<String, StampInfo> INFO = index();
    private static final Map<String, HeightStamp> CACHE = new ConcurrentHashMap<>();

    private StampLibrary() {
    }

    private static Map<String, StampInfo> index() {
        Map<String, StampInfo> map = new LinkedHashMap<>();
        for (StampRecipe recipe : StampRecipes.all()) {
            map.put(recipe.name(), StampInfo.of(recipe));
        }
        return Collections.unmodifiableMap(map);
    }

    /** Todos los sellos de serie, por categoría y en el orden de la galería. */
    public static List<StampInfo> all() {
        return List.copyOf(INFO.values());
    }

    /** Los de una categoría, en orden. */
    public static List<StampInfo> of(StampCategory category) {
        List<StampInfo> result = new ArrayList<>();
        for (StampInfo info : INFO.values()) {
            if (info.category() == category) {
                result.add(info);
            }
        }
        return result;
    }

    /** Nombres de los sellos de serie con su descripción, en orden. */
    public static Map<String, String> descriptions() {
        Map<String, String> result = new LinkedHashMap<>();
        INFO.values().forEach(info -> result.put(info.name(), info.description()));
        return Collections.unmodifiableMap(result);
    }

    public static boolean has(String name) {
        return INFO.containsKey(name);
    }

    /** Lo que se sabe del sello de serie, o {@code null} si no es de serie. */
    public static StampInfo info(String name) {
        return INFO.get(name);
    }

    /** El sello de serie; lanza si no existe. */
    public static HeightStamp get(String name) {
        StampRecipe recipe = recipe(name);
        return CACHE.computeIfAbsent(name, key -> load(recipe));
    }

    /** Lo genera desde la receta a otro tamaño, sin pasar por el PNG (las pruebas lo usan pequeño). */
    public static HeightStamp render(String name, int size) {
        return render(name, size, recipe(name).defaultSeed());
    }

    /** Lo genera con otra semilla: la misma forma con otros detalles. */
    public static HeightStamp render(String name, int size, long seed) {
        StampRecipe recipe = recipe(name);
        return new HeightStamp(name, size, size, recipe.generate(size, seed), recipe.neutral());
    }

    /** Miniatura en color y con sombreado, {@code size}×{@code size} píxeles ARGB por filas. */
    public static int[] thumbnail(String name, int size) {
        return StampThumbnails.argb(name, size);
    }

    private static StampRecipe recipe(String name) {
        StampRecipe recipe = StampRecipes.byName(name);
        if (recipe == null) {
            throw new IllegalArgumentException("No hay ningún sello de serie llamado " + name);
        }
        return recipe;
    }

    /** Lo que quedó a menos de un escalón del gris neutro al guardarlo vuelve a ser neutro exacto. */
    private static HeightStamp snapped(HeightStamp stamp, double neutral) {
        float[] values = stamp.values().clone();
        double tolerance = 1.0 / ((1 << STORED_BITS) - 1);
        for (int index = 0; index < values.length; index++) {
            if (Math.abs(values[index] - neutral) <= tolerance) {
                values[index] = (float) neutral;
            }
        }
        return new HeightStamp(stamp.name(), stamp.width(), stamp.height(), values, neutral);
    }

    private static HeightStamp load(StampRecipe recipe) {
        try (InputStream input = StampLibrary.class.getResourceAsStream(RESOURCE + recipe.name() + ".png")) {
            if (input != null) {
                return snapped(StampImages.read(recipe.name(), input), recipe.neutral());
            }
            LOGGER.warn("KomaEdit: falta el sello {} en el jar; se genera", recipe.name());
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("KomaEdit: el sello {} del jar no se pudo leer ({}); se genera", recipe.name(),
                    exception.getMessage());
        }
        return new HeightStamp(recipe.name(), SIZE, SIZE, recipe.generate(SIZE, recipe.defaultSeed()),
                recipe.neutral());
    }
}
