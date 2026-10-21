package com.komainu.moxia.reference;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.komainu.moxia.shape.Vec3;

import java.util.Locale;
import java.util.Optional;

/**
 * Una imagen de referencia abierta en un mundo: qué archivo es, si va en pantalla o en el mundo, su opacidad, su
 * filtrado, su rejilla y dónde está en cada modo (se guardan los dos, así al cambiar de modo vuelve a donde estaba).
 * Es solo del cliente: no cambia el mundo ni pasa por el servidor.
 *
 * @param file        nombre dentro de {@code config/komaedit/references/}
 * @param pixelWidth  ancho de la imagen original en píxeles (las medidas no cambian aunque se dibuje reducida)
 * @param pixelHeight alto de la imagen original
 * @param visible     se dibuja (la tecla de ocultar todo va aparte, en {@link ReferenceSet#shown})
 * @param smooth      filtrado suave al agrandarla; nítido deja ver cada píxel (pixel art)
 */
public record ReferenceImage(String id, String file, int pixelWidth, int pixelHeight, Mode mode, boolean visible,
                             double opacity, boolean smooth, ReferenceGrid grid, ScreenFrame screen,
                             WorldFrame world) {
    public static final double MIN_OPACITY = 0.05;
    public static final double MAX_OPACITY = 1.0;
    public static final double DEFAULT_OPACITY = 0.6;
    /** Hasta este lado (en píxeles) se abre nítida: casi seguro es pixel art. */
    public static final int SHARP_UP_TO = 256;

    /** En pantalla (ventana flotante) o en el mundo (plano fijo). */
    public enum Mode {
        SCREEN("screen"),
        WORLD("world");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Mode byId(String id) {
            for (Mode mode : values()) {
                if (mode.id.equalsIgnoreCase(id)) {
                    return mode;
                }
            }
            return SCREEN;
        }
    }

    public ReferenceImage {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("La referencia necesita un identificador");
        }
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException("La referencia necesita un archivo");
        }
        pixelWidth = Math.max(1, pixelWidth);
        pixelHeight = Math.max(1, pixelHeight);
        mode = mode == null ? Mode.SCREEN : mode;
        opacity = Double.isFinite(opacity) ? Math.clamp(opacity, MIN_OPACITY, MAX_OPACITY) : DEFAULT_OPACITY;
        grid = grid == null ? ReferenceGrid.DEFAULT : grid;
        screen = screen == null ? ScreenFrame.DEFAULT : screen;
        world = world == null ? WorldFrame.at(null, pixelWidth, pixelHeight) : world;
    }

    /**
     * Una referencia recién abierta: en pantalla, visible, al 60 %, nítida si es pequeña (pixel art) y suave si no,
     * con la rejilla apagada y el plano del mundo listo en {@code worldCenter} para cuando se pase al mundo.
     */
    public static ReferenceImage open(String id, String file, int pixelWidth, int pixelHeight, Vec3 worldCenter) {
        boolean small = Math.max(pixelWidth, pixelHeight) <= SHARP_UP_TO;
        return new ReferenceImage(id, file, pixelWidth, pixelHeight, Mode.SCREEN, true, DEFAULT_OPACITY, !small,
                ReferenceGrid.DEFAULT.withPixels(small ? 1 : 16), ScreenFrame.DEFAULT,
                WorldFrame.at(worldCenter, pixelWidth, pixelHeight));
    }

    public ReferenceImage withMode(Mode value) {
        return new ReferenceImage(id, file, pixelWidth, pixelHeight, value, visible, opacity, smooth, grid, screen,
                world);
    }

    public ReferenceImage withVisible(boolean value) {
        return new ReferenceImage(id, file, pixelWidth, pixelHeight, mode, value, opacity, smooth, grid, screen,
                world);
    }

    public ReferenceImage withOpacity(double value) {
        return new ReferenceImage(id, file, pixelWidth, pixelHeight, mode, visible, value, smooth, grid, screen,
                world);
    }

    public ReferenceImage withSmooth(boolean value) {
        return new ReferenceImage(id, file, pixelWidth, pixelHeight, mode, visible, opacity, value, grid, screen,
                world);
    }

    public ReferenceImage withGrid(ReferenceGrid value) {
        return new ReferenceImage(id, file, pixelWidth, pixelHeight, mode, visible, opacity, smooth, value, screen,
                world);
    }

    public ReferenceImage withScreen(ScreenFrame value) {
        return new ReferenceImage(id, file, pixelWidth, pixelHeight, mode, visible, opacity, smooth, grid, value,
                world);
    }

    public ReferenceImage withWorld(WorldFrame value) {
        return new ReferenceImage(id, file, pixelWidth, pixelHeight, mode, visible, opacity, smooth, grid, screen,
                value);
    }

    /** Las medidas reales del archivo (si cambió desde que se abrió); el plano conserva su anchura en bloques. */
    public ReferenceImage withPixelSize(int width, int height) {
        if (width == pixelWidth && height == pixelHeight) {
            return this;
        }
        double blocks = world.widthBlocks(pixelWidth);
        ReferenceImage resized = new ReferenceImage(id, file, width, height, mode, visible, opacity, smooth, grid,
                screen, world);
        return resized.withWorld(world.withWidthBlocks(blocks, resized.pixelWidth, resized.pixelHeight));
    }

    /** El plano del mundo con cada celda de la rejilla del tamaño de un bloque. */
    public ReferenceImage cellPerBlock() {
        return withWorld(world.withPixelsPerBlock(grid.pixels()));
    }

    /** El nombre que se enseña: el del archivo sin carpeta ni extensión. */
    public String displayName() {
        return displayName(file);
    }

    public static String displayName(String file) {
        String name = file.substring(file.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** La opacidad como porcentaje entero, como sale en los controles. */
    public int opacityPercent() {
        return (int) Math.round(opacity * 100.0);
    }

    // ---------------------------------------------------------------- JSON

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("id", id);
        root.addProperty("file", file);
        root.addProperty("width", pixelWidth);
        root.addProperty("height", pixelHeight);
        root.addProperty("mode", mode.id());
        root.addProperty("visible", visible);
        root.addProperty("opacity", opacity);
        root.addProperty("smooth", smooth);
        JsonObject gridJson = new JsonObject();
        gridJson.addProperty("enabled", grid.enabled());
        gridJson.addProperty("pixels", grid.pixels());
        gridJson.addProperty("major", grid.major());
        gridJson.addProperty("offset_x", grid.offsetX());
        gridJson.addProperty("offset_y", grid.offsetY());
        gridJson.addProperty("color", grid.color().id());
        root.add("grid", gridJson);
        JsonObject screenJson = new JsonObject();
        screenJson.addProperty("x", screen.x());
        screenJson.addProperty("y", screen.y());
        screenJson.addProperty("width", screen.width());
        screenJson.addProperty("click_through", screen.clickThrough());
        root.add("screen", screenJson);
        JsonObject worldJson = new JsonObject();
        worldJson.addProperty("x", world.center().x());
        worldJson.addProperty("y", world.center().y());
        worldJson.addProperty("z", world.center().z());
        ReferenceRotation rotation = world.rotation();
        worldJson.addProperty("qx", rotation.x());
        worldJson.addProperty("qy", rotation.y());
        worldJson.addProperty("qz", rotation.z());
        worldJson.addProperty("qw", rotation.w());
        worldJson.addProperty("pixels_per_block", world.pixelsPerBlock());
        worldJson.addProperty("depth", world.depth());
        root.add("world", worldJson);
        return root;
    }

    /**
     * Lee una referencia guardada. Lo que falte o no se entienda toma el valor de serie; sin identificador o sin
     * archivo no hay referencia.
     */
    public static Optional<ReferenceImage> parse(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject root = element.getAsJsonObject();
        String id = string(root, "id", "");
        String file = string(root, "file", "");
        if (id.isBlank() || file.isBlank()) {
            return Optional.empty();
        }
        int width = integer(root, "width", 1);
        int height = integer(root, "height", 1);
        ReferenceImage defaults = open(id, file, width, height, null);
        JsonObject gridJson = object(root, "grid");
        ReferenceGrid grid = new ReferenceGrid(bool(gridJson, "enabled", defaults.grid().enabled()),
                integer(gridJson, "pixels", defaults.grid().pixels()),
                integer(gridJson, "major", defaults.grid().major()),
                integer(gridJson, "offset_x", 0), integer(gridJson, "offset_y", 0),
                ReferenceGrid.Color.byId(string(gridJson, "color", ReferenceGrid.Color.WHITE.id())));
        JsonObject screenJson = object(root, "screen");
        ScreenFrame screen = new ScreenFrame(number(screenJson, "x", ScreenFrame.DEFAULT.x()),
                number(screenJson, "y", ScreenFrame.DEFAULT.y()),
                number(screenJson, "width", ScreenFrame.DEFAULT.width()),
                bool(screenJson, "click_through", false));
        JsonObject worldJson = object(root, "world");
        WorldFrame fallback = defaults.world();
        WorldFrame world = new WorldFrame(
                new Vec3(number(worldJson, "x", fallback.center().x()), number(worldJson, "y", fallback.center().y()),
                        number(worldJson, "z", fallback.center().z())),
                new ReferenceRotation(number(worldJson, "qx", 0.0), number(worldJson, "qy", 0.0),
                        number(worldJson, "qz", 0.0), number(worldJson, "qw", 1.0)),
                number(worldJson, "pixels_per_block", fallback.pixelsPerBlock()),
                bool(worldJson, "depth", fallback.depth()));
        return Optional.of(new ReferenceImage(id, file, width, height,
                Mode.byId(string(root, "mode", Mode.SCREEN.id())), bool(root, "visible", true),
                number(root, "opacity", DEFAULT_OPACITY), bool(root, "smooth", defaults.smooth()), grid, screen,
                world));
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    static String string(JsonObject root, String key, String fallback) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : fallback;
    }

    static boolean bool(JsonObject root, String key, boolean fallback) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean() : fallback;
    }

    static double number(JsonObject root, String key, double fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        double value = element.getAsDouble();
        return Double.isFinite(value) ? value : fallback;
    }

    static int integer(JsonObject root, String key, int fallback) {
        double value = number(root, key, fallback);
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (int) Math.round(value) : fallback;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "ReferenceImage[%s %s %dx%d %s]", id, file, pixelWidth, pixelHeight,
                mode.id());
    }
}
