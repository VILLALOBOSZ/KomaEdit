package com.komainu.moxia.reference;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.gizmo.GizmoHandle;
import com.komainu.moxia.gizmo.Orientation;
import com.komainu.moxia.image.RgbaImage;
import com.komainu.moxia.preview.TextureTiles;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.ui.ImageCanvas;
import com.komainu.moxia.studio.ui.StudioTheme;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maquetas de las imágenes de referencia fuera del juego, en los dos modos. Solo con
 * {@code KOMAEDIT_REFERENCE_MOCKUPS=<carpeta>} (y, para las texturas del juego, {@code KOMAEDIT_MC_JAR}):
 *
 * <pre>KOMAEDIT_REFERENCE_MOCKUPS=docs/renders/referencias ./gradlew test --tests "*ReferenceMockupsTest"</pre>
 *
 * <p>Usan el mismo código que el cliente donde se puede: el plano ({@link WorldFrame}), la rejilla
 * ({@link ReferenceGrid}), el gizmo ({@link ReferenceGizmo}), la ventana flotante ({@link ScreenFrame}), los colores
 * del tema del Studio y los textos de {@code es_es.json}. A 1280×720 con escala de interfaz 2.</p>
 */
class ReferenceMockupsTest {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    /** Rayos por píxel en cada eje (se promedia al final). */
    private static final int SUPER = 2;
    private static final int GUI = 2;
    private static final double FOV = 70.0;
    /** La fachada: 24×16 bloques a 20 píxeles por bloque. */
    private static final String[] FACADE = {
            "           RR           ",
            "         RRRRRR         ",
            "       RRRRRRRRRR       ",
            "     RRRRRRRRRRRRRR     ",
            "   RRRRRRRRRRRRRRRRRR   ",
            " RRRRRRRRRRRRRRRRRRRRRR ",
            "  SSSSSSSSSSSSSSSSSSSS  ",
            "  SSWWWSSSSWWSSSSWWWSS  ",
            "  SSWWWSSSSWWSSSSWWWSS  ",
            "  SSWWWSSSSWWSSSSWWWSS  ",
            "  SSSSSSSSSSSSSSSSSSSS  ",
            "  SSWWWSSSDDDDSSSWWWSS  ",
            "  SSWWWSSSDDDDSSSWWWSS  ",
            "  SSSSSSSSDDDDSSSSSSSS  ",
            "  SSSSSSSSDDDDSSSSSSSS  ",
            "  PPPPPPPPPPPPPPPPPPPP  "
    };
    private static final int FACADE_CELL = 20;
    /** La fachada en el mundo: de x = 0 a 24, del suelo (y = 64) a y = 80, contra el muro en z = -10. */
    private static final Vec3 FACADE_CENTER = new Vec3(12.0, 72.0, -8.98);
    /** Filas de la fachada (desde abajo) que ya están construidas. */
    private static final int BUILT_ROWS = 6;
    private static final Vec3 SPRITE_CENTER = new Vec3(-14.0, 64.02, 10.0);

    private JsonObject lang;

    @Test
    void renderMockups() throws IOException {
        String target = System.getenv("KOMAEDIT_REFERENCE_MOCKUPS");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "KOMAEDIT_REFERENCE_MOCKUPS sin definir");
        assertTrue(ImageCanvas.font().loaded(), "la fuente del juego tiene que estar en el classpath");
        Path folder = Path.of(target);
        Files.createDirectories(folder);
        lang = JsonParser.parseString(Files.readString(Path.of("src", "main", "resources", "assets", "komaedit",
                "lang", "es_es.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        RgbaImage facade = facadePhoto();
        RgbaImage sprite = pixelArt();
        try (TextureTiles tiles = new TextureTiles()) {
            ReferenceScene scene = world(tiles, sprite, false);
            ReferenceImage photo = ReferenceImage.open("fachada", "fachada.jpg", facade.width(), facade.height(),
                    null).withMode(ReferenceImage.Mode.WORLD).withOpacity(0.6)
                    .withGrid(new ReferenceGrid(true, FACADE_CELL, 4, 0, 0, ReferenceGrid.Color.WHITE))
                    .withWorld(new WorldFrame(FACADE_CENTER, ReferenceRotation.IDENTITY, FACADE_CELL, false));
            ReferenceScene.Camera facadeCamera = ReferenceScene.Camera.looking(new Vec3(-1.5, 70.2, 13.5),
                    new Vec3(12.0, 72.5, -9.0), FOV, WIDTH * SUPER, HEIGHT * SUPER);

            // Mundo 1: la fachada de pie, con la rejilla a un bloque por celda y el gizmo con una esquina debajo.
            BufferedImage first = world(scene, facadeCamera, photo, facade,
                    ReferenceGizmo.Handle.corner(1), ReferenceGizmo.Handle.NONE);
            hud(first, photo, null);
            write(first, folder, "mundo_1_fachada_con_gizmo.png");

            // Mundo 2: pixel art tumbado en el suelo, con profundidad: lo ya colocado lo tapa.
            ReferenceScene floor = world(tiles, sprite, true);
            ReferenceImage art = ReferenceImage.open("seta", "seta.png", sprite.width(), sprite.height(), null)
                    .withMode(ReferenceImage.Mode.WORLD).withOpacity(0.75)
                    .withGrid(new ReferenceGrid(true, 1, 8, 0, 0, ReferenceGrid.Color.BLACK))
                    .withWorld(new WorldFrame(SPRITE_CENTER, ReferenceRotation.flat(180.0), 1.0, true));
            ReferenceScene.Camera artCamera = ReferenceScene.Camera.looking(new Vec3(-14.0, 84.0, 36.0),
                    new Vec3(-14.0, 64.0, 9.0), FOV, WIDTH * SUPER, HEIGHT * SUPER);
            BufferedImage second = world(floor, artCamera, art, sprite, null, null);
            write(second, folder, "mundo_2_pixel_art_en_el_suelo.png");

            // Mundo 3: con y sin profundidad, con un árbol y un andamio delante.
            ReferenceScene front = world(tiles, sprite, false);
            front.tree(4, 64, -3, 5);
            front.tree(12, 64, -3, 5);
            ReferenceImage clean = photo.withGrid(photo.grid().withEnabled(false)).withOpacity(0.7);
            BufferedImage depth = world(front, facadeCamera, clean.withWorld(clean.world().withDepth(true)), facade,
                    null, null);
            BufferedImage over = world(front, facadeCamera, clean, facade, null, null);
            write(split(depth, over, tr("gui.komaedit.reference.depth") + ": Sí",
                    tr("gui.komaedit.reference.depth") + ": No"), folder, "mundo_3_con_y_sin_profundidad.png");

            // Mundo 4: girando con el anillo Y (lo demás atenuado), suave y sin rejilla.
            ReferenceImage turned = clean.withOpacity(0.65).withWorld(clean.world()
                    .withRotation(ReferenceRotation.IDENTITY.turned(Orientation.Y, 20.0)));
            ReferenceGizmo.Handle ring = ReferenceGizmo.Handle.of(GizmoHandle.RING_Y);
            BufferedImage fourth = world(scene, facadeCamera, turned, facade, ring, ring);
            hud(fourth, turned, tr("gui.komaedit.reference.hud.turning", ReferenceFormat.signed(20.0)));
            write(fourth, folder, "mundo_4_girando_con_el_anillo.png");

            // Pantalla: el fondo es el juego sin referencias en el mundo.
            ReferenceScene.Camera close = ReferenceScene.Camera.looking(new Vec3(5.5, 66.6, 2.5),
                    new Vec3(13.0, 67.0, -9.0), FOV, WIDTH * SUPER, HEIGHT * SUPER);
            BufferedImage game = ReferenceScene.downsample(scene.trace(close, List.of()).image(), SUPER);
            ReferenceImage windowPhoto = ReferenceImage.open("fachada", "fachada.jpg", facade.width(),
                            facade.height(), null).withOpacity(0.9)
                    .withGrid(new ReferenceGrid(true, FACADE_CELL, 4, 0, 0, ReferenceGrid.Color.WHITE))
                    .withScreen(new ScreenFrame(0.585, 0.09, 0.38, false));
            ReferenceImage windowArt = ReferenceImage.open("seta", "seta.png", sprite.width(), sprite.height(), null)
                    .withOpacity(1.0)
                    .withGrid(new ReferenceGrid(true, 1, 8, 0, 0, ReferenceGrid.Color.BLACK))
                    .withScreen(new ScreenFrame(0.03, 0.5, 0.2, true));
            List<Window> windows = List.of(new Window(windowArt, sprite), new Window(windowPhoto, facade));
            write(screen(game, windows, true, 520, 120), folder, "pantalla_1_colocar_ventanas.png");
            write(screen(game, List.of(new Window(windowArt, sprite), new Window(windowPhoto.withOpacity(0.6),
                    facade)), false, -1, -1), folder, "pantalla_2_jugando.png");
        }
    }

    private static void write(BufferedImage image, Path folder, String name) throws IOException {
        ImageIO.write(image, "png", folder.resolve(name).toFile());
    }

    private String tr(String key, Object... arguments) {
        String value = lang.has(key) ? lang.get(key).getAsString() : key;
        return arguments.length == 0 ? value.replace("%%", "%") : String.format(value, arguments);
    }

    // ---------------------------------------------------------------- el mundo

    /** El suelo, unos árboles, la fachada a medio construir y, si {@code traced}, la seta a medio calcar. */
    private static ReferenceScene world(TextureTiles tiles, RgbaImage sprite, boolean traced) {
        ReferenceScene scene = new ReferenceScene(tiles);
        scene.ground();
        scene.tree(-6, 64, -14, 6);
        scene.tree(31, 64, -12, 5);
        scene.tree(36, 64, 4, 6);
        // La fachada en z = -10, de abajo arriba, hasta las filas ya construidas.
        for (int row = 0; row < BUILT_ROWS; row++) {
            String line = FACADE[FACADE.length - 1 - row];
            for (int column = 0; column < line.length(); column++) {
                ReferenceScene.Block block = switch (line.charAt(column)) {
                    case 'S' -> ReferenceScene.Block.STONE_BRICKS;
                    case 'W' -> ReferenceScene.Block.WINDOW;
                    case 'D' -> ReferenceScene.Block.DOOR;
                    case 'P' -> ReferenceScene.Block.COBBLESTONE;
                    default -> ReferenceScene.Block.AIR;
                };
                if (block != ReferenceScene.Block.AIR) {
                    scene.set(column, 64 + row, -10, block);
                }
            }
        }
        // Un andamio en la esquina por la que se va subiendo.
        for (int y = 64; y < 64 + BUILT_ROWS + 2; y++) {
            scene.set(1, y, -8, ReferenceScene.Block.SCAFFOLD);
        }
        if (traced) {
            // La seta, calcada desde abajo: el pie entero y el arranque del sombrero.
            for (int v = 17; v < sprite.height(); v++) {
                for (int u = 0; u < sprite.width(); u++) {
                    ReferenceScene.Block wool = wool(sprite.get(u, v));
                    if (wool != ReferenceScene.Block.AIR) {
                        scene.set(-30 + u, 64, -6 + v, wool);
                    }
                }
            }
        }
        return scene;
    }

    private static ReferenceScene.Block wool(int argb) {
        if (argb >>> 24 < 128) {
            return ReferenceScene.Block.AIR;
        }
        int red = argb >> 16 & 0xFF;
        int green = argb >> 8 & 0xFF;
        int blue = argb & 0xFF;
        if (red + green + blue < 120) {
            return ReferenceScene.Block.BLACK_WOOL;
        }
        if (red > 180 && green < 90) {
            return ReferenceScene.Block.RED_WOOL;
        }
        if (blue > 200 && green > 200) {
            return ReferenceScene.Block.WHITE_WOOL;
        }
        return ReferenceScene.Block.SANDSTONE;
    }

    /** Traza el mundo con la referencia, dibuja su rejilla y el gizmo (si {@code hovered} no es nulo). */
    private static BufferedImage world(ReferenceScene scene, ReferenceScene.Camera camera, ReferenceImage reference,
                                       RgbaImage pixels, ReferenceGizmo.Handle hovered,
                                       ReferenceGizmo.Handle dragged) {
        ReferenceScene.Plane plane = new ReferenceScene.Plane(reference.world(), pixels, reference.opacity(),
                reference.smooth(), reference.grid());
        ReferenceScene.Frame frame = scene.trace(camera, List.of(plane));
        // Las líneas, con el grosor del cliente a 1080p escalado a esta imagen.
        double pixelScale = SUPER * Math.max(1.0, WIDTH / 1920.0);
        ReferenceScene.grid(frame, camera, plane, pixelScale);
        if (hovered != null) {
            ReferenceGizmo.Layout layout = ReferenceGizmo.layout(reference.world(), reference.pixelWidth(),
                    reference.pixelHeight(), camera.eye(), FOV);
            GizmoGeometry.Shape shape = ReferenceGizmo.build(layout, hovered,
                    dragged == null ? ReferenceGizmo.Handle.NONE : dragged);
            ReferenceScene.gizmo(frame.image(), camera, shape, pixelScale);
        }
        BufferedImage image = ReferenceScene.downsample(frame.image(), SUPER);
        ReferenceScene.crosshair(image, GUI);
        hotbar(image);
        return image;
    }

    /** La línea de la action bar mientras se edita, como la pone el cliente. */
    private void hud(BufferedImage image, ReferenceImage reference, String dragging) {
        ImageCanvas canvas = new ImageCanvas(image, GUI);
        String size = tr("gui.komaedit.reference.size_blocks",
                ReferenceFormat.blocks(reference.world().widthBlocks(reference.pixelWidth())),
                ReferenceFormat.blocks(reference.world().heightBlocks(reference.pixelHeight())));
        List<String[]> parts = List.of(new String[]{Feedback.PREFIX, "FFAA00"}, new String[]{" · ", "555555"},
                new String[]{reference.displayName(), "FFFFFF"}, new String[]{" · ", "555555"},
                new String[]{size, "AAAAAA"}, new String[]{" · ", "555555"},
                dragging == null ? new String[]{tr("gui.komaedit.reference.hud.hint"), "AAAAAA"}
                        : new String[]{dragging, "55FFFF"});
        int total = parts.stream().mapToInt(part -> canvas.width(part[0])).sum();
        int x = (WIDTH / GUI - total) / 2;
        int y = HEIGHT / GUI - 68;
        for (String[] part : parts) {
            shadow(canvas, part[0], x, y, 0xFF000000 | Integer.parseInt(part[1], 16));
            x += canvas.width(part[0]);
        }
    }

    private static void hotbar(BufferedImage image) {
        ImageCanvas canvas = new ImageCanvas(image, GUI);
        int width = 182;
        int left = (WIDTH / GUI - width) / 2;
        int top = HEIGHT / GUI - 22;
        canvas.fill(new Rect(left, top, width, 22), 0xA0000000);
        for (int slot = 0; slot < 9; slot++) {
            int x = left + 1 + slot * 20;
            border(canvas, new Rect(x, top + 1, 20, 20), slot == 0 ? 0xFFFFFFFF : 0xFF5A5A5A);
        }
    }

    /** Dos imágenes partidas por la mitad, con su rótulo arriba. */
    private static BufferedImage split(BufferedImage left, BufferedImage right, String leftLabel, String rightLabel) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                image.setRGB(x, y, x < WIDTH / 2 ? left.getRGB(x, y) : right.getRGB(x, y));
            }
        }
        ImageCanvas canvas = new ImageCanvas(image, GUI);
        canvas.fill(new Rect(WIDTH / GUI / 2, 0, 1, HEIGHT / GUI), 0xFFFFFFFF);
        for (int side = 0; side < 2; side++) {
            String label = side == 0 ? leftLabel : rightLabel;
            int width = canvas.width(label) + 10;
            int x = side * WIDTH / GUI / 2 + (WIDTH / GUI / 2 - width) / 2;
            canvas.fill(new Rect(x, 8, width, 14), StudioTheme.fade(StudioTheme.PANEL, 0.85));
            shadow(canvas, label, x + 5, 11, StudioTheme.TEXT_STRONG);
        }
        return image;
    }

    // ---------------------------------------------------------------- en pantalla

    private record Window(ReferenceImage reference, RgbaImage pixels) {
    }

    /**
     * El juego con las ventanas encima. Con {@code arrange}, la pantalla de colocar: la línea de arriba y todas las
     * ventanas con su barra; el ratón sobre la imagen de la última enseña la celda. Si no, como se ven jugando.
     */
    private BufferedImage screen(BufferedImage game, List<Window> windows, boolean arrange, int mouseX, int mouseY) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        image.getGraphics().drawImage(game, 0, 0, null);
        ImageCanvas canvas = new ImageCanvas(image, GUI);
        int width = WIDTH / GUI;
        int height = HEIGHT / GUI;
        if (!arrange) {
            ReferenceScene.crosshair(image, GUI);
            hotbar(image);
        } else {
            String hint = tr("gui.komaedit.reference.arrange.hint");
            int hintWidth = canvas.width(hint) + 12;
            int left = (width - hintWidth) / 2;
            canvas.fill(new Rect(left, 4, hintWidth, 14), StudioTheme.fade(StudioTheme.PANEL, 0.85));
            shadow(canvas, hint, (width - canvas.width(hint)) / 2, 7, StudioTheme.TEXT);
        }
        List<String> tooltip = new ArrayList<>();
        for (Window window : windows) {
            ReferenceImage reference = window.reference();
            ScreenFrame.Window layout = reference.screen().layout(width, height, reference.pixelWidth(),
                    reference.pixelHeight());
            image(image, window, layout);
            grid(canvas, reference, layout);
            if (arrange) {
                boolean hovered = layout.hit(mouseX, mouseY, true) != ScreenFrame.Part.NONE;
                chrome(canvas, reference, layout, hovered, mouseX, mouseY);
                if (hovered && reference.grid().enabled()) {
                    double[] pixel = layout.pixel(mouseX, mouseY, reference.pixelWidth(), reference.pixelHeight());
                    tooltip = List.of(tr("gui.komaedit.reference.window.cell",
                            reference.grid().column(pixel[0], reference.pixelWidth()),
                            reference.grid().row(pixel[1], reference.pixelHeight())));
                }
            }
        }
        if (!tooltip.isEmpty()) {
            tooltip(canvas, tooltip, mouseX, mouseY);
        }
        if (arrange) {
            cursor(image, mouseX * GUI, mouseY * GUI);
        }
        return image;
    }

    private static void image(BufferedImage target, Window window, ScreenFrame.Window layout) {
        ScreenFrame.Rect rect = layout.image();
        RgbaImage pixels = window.pixels();
        ReferenceImage reference = window.reference();
        for (int y = rect.y() * GUI; y < rect.bottom() * GUI; y++) {
            for (int x = rect.x() * GUI; x < rect.right() * GUI; x++) {
                if (x < 0 || y < 0 || x >= target.getWidth() || y >= target.getHeight()) {
                    continue;
                }
                double u = (x + 0.5 - rect.x() * GUI) / (rect.width() * GUI) * pixels.width();
                double v = (y + 0.5 - rect.y() * GUI) / (rect.height() * GUI) * pixels.height();
                int argb = sample(pixels, u, v, reference.smooth());
                double alpha = (argb >>> 24) / 255.0 * reference.opacity();
                target.setRGB(x, y, ReferenceScene.blend(target.getRGB(x, y), argb, alpha));
            }
        }
    }

    private static int sample(RgbaImage image, double u, double v, boolean smooth) {
        if (!smooth) {
            return image.get(Math.clamp((int) u, 0, image.width() - 1), Math.clamp((int) v, 0, image.height() - 1));
        }
        double x = Math.clamp(u - 0.5, 0, image.width() - 1);
        double y = Math.clamp(v - 0.5, 0, image.height() - 1);
        int x0 = (int) x;
        int y0 = (int) y;
        int x1 = Math.min(image.width() - 1, x0 + 1);
        int y1 = Math.min(image.height() - 1, y0 + 1);
        double fx = x - x0;
        double fy = y - y0;
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            double top = (image.get(x0, y0) >>> shift & 0xFF) * (1 - fx) + (image.get(x1, y0) >>> shift & 0xFF) * fx;
            double bottom = (image.get(x0, y1) >>> shift & 0xFF) * (1 - fx) + (image.get(x1, y1) >>> shift & 0xFF)
                    * fx;
            result |= (int) Math.round(top * (1 - fy) + bottom * fy) << shift;
        }
        return result;
    }

    /** La rejilla como la dibuja {@code ReferenceWindows}: una línea de un punto cada celda y el borde. */
    private static void grid(ImageCanvas canvas, ReferenceImage reference, ScreenFrame.Window layout) {
        ReferenceGrid grid = reference.grid();
        double scale = layout.scale(reference.pixelWidth());
        if (!grid.enabled() || !grid.visibleAt(scale, 3.0) && grid.major() == 0) {
            return;
        }
        boolean minor = grid.visibleAt(scale, 3.0);
        int bold = grid.visibleAt(scale, 4.0) ? 2 : 1;
        ScreenFrame.Rect rect = layout.image();
        int color = grid.color().rgb();
        for (ReferenceGrid.Line line : grid.lines(reference.pixelWidth(), reference.pixelHeight())) {
            if (!line.major() && !minor) {
                continue;
            }
            int argb = (line.major() ? 0xE8 : 0x70) << 24 | color;
            int thickness = line.major() ? bold : 1;
            if (line.vertical()) {
                int x = rect.x() + (int) Math.round(line.at() * rect.width() / reference.pixelWidth());
                canvas.fill(new Rect(x, rect.y(), thickness, rect.height()), argb);
            } else {
                int y = rect.y() + (int) Math.round(line.at() * rect.height() / reference.pixelHeight());
                canvas.fill(new Rect(rect.x(), y, rect.width(), thickness), argb);
            }
        }
        border(canvas, new Rect(rect.x(), rect.y(), rect.width(), rect.height()), 0x90 << 24 | color);
    }

    /** La barra, los botones, el borde y la esquina, como {@code ReferenceWindows}. */
    private static void chrome(ImageCanvas canvas, ReferenceImage reference, ScreenFrame.Window layout,
                               boolean hovered, int mouseX, int mouseY) {
        ScreenFrame.Rect bar = layout.bar();
        ScreenFrame.Rect image = layout.image();
        canvas.fill(rect(bar), StudioTheme.fade(StudioTheme.PANEL, 0.92));
        int titleRight = layout.through().x() - 2;
        String name = trim(canvas, reference.displayName(), Math.max(0, titleRight - bar.x() - 4));
        canvas.text(name, bar.x() + 3, bar.y() + 2, hovered ? StudioTheme.TEXT_STRONG : StudioTheme.TEXT);
        button(canvas, layout.through(), "»", reference.screen().clickThrough(),
                layout.through().contains(mouseX, mouseY));
        button(canvas, layout.hide(), "×", false, layout.hide().contains(mouseX, mouseY));
        border(canvas, new Rect(image.x() - 1, bar.y() - 1, image.width() + 2, image.height() + bar.height() + 2),
                hovered ? StudioTheme.ACCENT : StudioTheme.OUTLINE);
        ScreenFrame.Rect grip = layout.grip();
        int gripColor = grip.contains(mouseX, mouseY) ? StudioTheme.ACCENT_TEXT : StudioTheme.TEXT_SECONDARY;
        canvas.fill(rect(grip), StudioTheme.fade(StudioTheme.PANEL, 0.75));
        for (int step = 0; step < 3; step++) {
            int offset = 2 + step * 2;
            canvas.fill(new Rect(grip.right() - offset, grip.bottom() - 1, 1, 1), gripColor);
            canvas.fill(new Rect(grip.right() - 1, grip.bottom() - offset, 1, 1), gripColor);
        }
    }

    private static void button(ImageCanvas canvas, ScreenFrame.Rect rect, String label, boolean on,
                               boolean hovered) {
        int fill = on ? StudioTheme.ACCENT : hovered ? StudioTheme.CARD_HOVER : StudioTheme.CARD;
        canvas.fill(new Rect(rect.x() + 1, rect.y() + 1, rect.width() - 2, rect.height() - 2), fill);
        int x = rect.x() + rect.width() / 2 - canvas.width(label) / 2;
        shadow(canvas, label, x, rect.y() + 2, on ? StudioTheme.ON_ACCENT : StudioTheme.TEXT_STRONG);
    }

    /** El recuadro de ayuda de Minecraft: fondo morado oscuro y borde morado. */
    private static void tooltip(ImageCanvas canvas, List<String> lines, int mouseX, int mouseY) {
        int width = lines.stream().mapToInt(canvas::width).max().orElse(0);
        int height = lines.size() * 10 - 2;
        int x = mouseX + 12;
        int y = mouseY - 12;
        canvas.fill(new Rect(x - 3, y - 4, width + 6, 1), 0xF0100010);
        canvas.fill(new Rect(x - 3, y + height + 3, width + 6, 1), 0xF0100010);
        canvas.fill(new Rect(x - 3, y - 3, width + 6, height + 6), 0xF0100010);
        canvas.fill(new Rect(x - 4, y - 3, 1, height + 6), 0xF0100010);
        canvas.fill(new Rect(x + width + 3, y - 3, 1, height + 6), 0xF0100010);
        border(canvas, new Rect(x - 3, y - 3, width + 6, height + 6), 0x505000FF);
        for (int index = 0; index < lines.size(); index++) {
            shadow(canvas, lines.get(index), x, y + index * 10, 0xFFFFFFFF);
        }
    }

    private static void cursor(BufferedImage image, int x, int y) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(x, y);
        arrow.lineTo(x, y + 22);
        arrow.lineTo(x + 6, y + 17);
        arrow.lineTo(x + 10, y + 26);
        arrow.lineTo(x + 14, y + 24);
        arrow.lineTo(x + 10, y + 15);
        arrow.lineTo(x + 17, y + 15);
        arrow.closePath();
        graphics.setColor(Color.WHITE);
        graphics.fill(arrow);
        graphics.setColor(Color.BLACK);
        graphics.draw(arrow);
        graphics.dispose();
    }

    private static Rect rect(ScreenFrame.Rect rect) {
        return new Rect(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private static void border(ImageCanvas canvas, Rect rect, int argb) {
        canvas.fill(new Rect(rect.x(), rect.y(), rect.width(), 1), argb);
        canvas.fill(new Rect(rect.x(), rect.bottom() - 1, rect.width(), 1), argb);
        canvas.fill(new Rect(rect.x(), rect.y() + 1, 1, rect.height() - 2), argb);
        canvas.fill(new Rect(rect.right() - 1, rect.y() + 1, 1, rect.height() - 2), argb);
    }

    /** Texto con sombra, como {@code drawTextWithShadow}. */
    private static void shadow(ImageCanvas canvas, String text, int x, int y, int argb) {
        canvas.text(text, x + 1, y + 1, argb & 0xFF000000 | (argb & 0xFCFCFC) >> 2);
        canvas.text(text, x, y, argb);
    }

    private static String trim(ImageCanvas canvas, String text, int width) {
        String result = text;
        while (!result.isEmpty() && canvas.width(result) > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    // ---------------------------------------------------------------- las imágenes

    /**
     * Una «foto» de la fachada, de 480×320: cielo, tejado de teja, sillería con su llaga, ventanas con reflejo,
     * puerta de tablas y zócalo, con luz de lado y grano. Cada bloque son 20 píxeles.
     */
    static RgbaImage facadePhoto() {
        int width = FACADE[0].length() * FACADE_CELL;
        int height = FACADE.length * FACADE_CELL;
        RgbaImage image = RgbaImage.blank(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int column = x / FACADE_CELL;
                int row = y / FACADE_CELL;
                int localX = x % FACADE_CELL;
                int localY = y % FACADE_CELL;
                char kind = FACADE[row].charAt(column);
                int rgb = switch (kind) {
                    case 'R' -> roof(x, y);
                    case 'S' -> wall(x, y);
                    case 'W' -> window(row, column, localX, localY, x, y);
                    case 'D' -> door(row, column, localX, localY, x);
                    case 'P' -> plinth(x, y);
                    default -> sky(x, y, height);
                };
                if (kind != ' ') {
                    double light = 1.06 - 0.14 * x / width;
                    rgb = ReferenceScene.scale(rgb, light) & 0xFFFFFF;
                }
                image.set(x, y, 0xFF000000 | grain(rgb, x, y, kind == ' ' ? 2 : 6));
            }
        }
        return image;
    }

    private static int sky(int x, int y, int height) {
        int base = ReferenceScene.lerp(0x8DB9EA, 0xD9E7F6, y / (double) height) & 0xFFFFFF;
        double cloud = Math.max(0.0, Math.sin(x * 0.021 + 1.3) * Math.sin(y * 0.09 + 0.4) - 0.55) * 1.8;
        return ReferenceScene.lerp(base, 0xF7F9FC, Math.min(1.0, cloud)) & 0xFFFFFF;
    }

    private static int roof(int x, int y) {
        int course = y / 8;
        boolean seam = y % 8 == 0 || (x + (course % 2) * 6) % 12 == 0;
        int base = (int) (hash(x / 12, course) % 3) == 0 ? 0xA9493A : 0x9C4130;
        return seam ? 0x5E2A20 : base;
    }

    private static int wall(int x, int y) {
        int course = y / 10;
        boolean seam = y % 10 == 0 || (x + (course % 2) * 10) % 20 == 0;
        if (seam) {
            return 0x958A74;
        }
        long stone = hash((x + (course % 2) * 10) / 20, course);
        return ReferenceScene.scale(0xFFCBBD9F, 0.93 + (stone % 9) / 100.0) & 0xFFFFFF;
    }

    private static int window(int row, int column, int localX, int localY, int x, int y) {
        boolean left = FACADE[row].charAt(column - 1) != 'W';
        boolean right = FACADE[row].charAt(column + 1) != 'W';
        boolean top = FACADE[row - 1].charAt(column) != 'W';
        boolean bottom = FACADE[row + 1].charAt(column) != 'W';
        if (left && localX < 3 || right && localX > 16 || top && localY < 3 || bottom && localY > 16) {
            return 0xEDEBE4;
        }
        if (localX == 10 || localY == 10) {
            return 0xD9D6CE;
        }
        double reflection = Math.max(0.0, 1.0 - Math.abs((x - y * 0.8) % 46 - 18) / 9.0);
        return ReferenceScene.lerp(0x2E4660, 0x8FB0CC, reflection * 0.6) & 0xFFFFFF;
    }

    private static int door(int row, int column, int localX, int localY, int x) {
        boolean left = FACADE[row].charAt(column - 1) != 'D';
        boolean right = FACADE[row].charAt(column + 1) != 'D';
        boolean top = FACADE[row - 1].charAt(column) != 'D';
        if (left && localX < 2 || right && localX > 17 || top && localY < 2) {
            return 0x3E2817;
        }
        if (x % 7 == 0) {
            return 0x553620;
        }
        if (column == 12 && row == 13 && localX > 3 && localX < 7 && localY > 6 && localY < 10) {
            return 0xC9A44B;
        }
        return 0x7A5130;
    }

    private static int plinth(int x, int y) {
        long cell = hash(x / 5, y / 5);
        return cell % 4 == 0 ? 0x6E6E6E : cell % 4 == 1 ? 0x8C8C8C : 0x7C7C7C;
    }

    private static int grain(int rgb, int x, int y, int amount) {
        int noise = (int) (hash(x * 7 + 3, y * 13 + 5) % (2L * amount + 1)) - amount;
        int red = Math.clamp((rgb >> 16 & 0xFF) + noise, 0, 255);
        int green = Math.clamp((rgb >> 8 & 0xFF) + noise, 0, 255);
        int blue = Math.clamp((rgb & 0xFF) + noise, 0, 255);
        return red << 16 | green << 8 | blue;
    }

    private static long hash(int a, int b) {
        long value = a * 73_856_093L ^ b * 19_349_663L;
        value = (value ^ value >>> 13) * 0x5bd1e995L;
        return Math.abs(value ^ value >>> 15);
    }

    /** Pixel art de 32×32: una seta roja con lunares, pie claro con ojos y contorno negro. */
    static RgbaImage pixelArt() {
        int size = 32;
        int[] grid = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double capX = (x + 0.5 - 16.0) / 14.0;
                double capY = (y + 0.5 - 15.0) / 12.0;
                boolean cap = capX * capX + capY * capY <= 1.0 && y <= 16;
                boolean stem = x >= 10 && x <= 21 && y >= 15 && y <= 28;
                int color = 0;
                if (stem) {
                    color = x >= 19 ? 0xFFCDB98A : 0xFFEAD9B0;
                }
                if (cap) {
                    color = y >= 14 ? 0xFFA81E24 : 0xFFD8282F;
                    for (double[] spot : new double[][]{{10, 8, 2.6}, {20, 6, 2.8}, {25, 12, 2.0}, {15, 13, 1.8},
                            {5, 13, 1.4}}) {
                        if (Math.hypot(x + 0.5 - spot[0], y + 0.5 - spot[1]) <= spot[2]) {
                            color = 0xFFF5F5F5;
                        }
                    }
                }
                if (stem && !cap && (x == 13 || x == 14 || x == 17 || x == 18) && y >= 19 && y <= 22) {
                    color = 0xFF151515;
                }
                grid[y * size + x] = color;
            }
        }
        RgbaImage image = RgbaImage.blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int color = grid[y * size + x];
                boolean edge = color == 0 && (opaque(grid, x - 1, y, size) || opaque(grid, x + 1, y, size)
                        || opaque(grid, x, y - 1, size) || opaque(grid, x, y + 1, size));
                image.set(x, y, edge ? 0xFF151515 : color);
            }
        }
        return image;
    }

    private static boolean opaque(int[] grid, int x, int y, int size) {
        return x >= 0 && y >= 0 && x < size && y < size && grid[y * size + x] != 0;
    }
}
