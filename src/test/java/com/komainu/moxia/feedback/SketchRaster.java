package com.komainu.moxia.feedback;

import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.gizmo.PerspectiveRaster;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.studio.ui.MinecraftFont;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pinta un {@link WorldSketch} en un {@link PerspectiveRaster} como lo dibuja el cliente ({@code SketchRenderer}):
 * las líneas normales con profundidad, las de «a través de los bloques» encima de todo y los rótulos con la fuente del
 * juego, su icono de píxeles y el fondo suave, del mismo tamaño en pantalla a cualquier distancia.
 *
 * <p>El tamaño de los rótulos sale de la misma fórmula que en el juego ({@code 0,022 × clamp(d / 9, 0,6, 8)} bloques
 * por píxel de la fuente) a 1080p: a cualquier distancia media, dos píxeles de imagen por píxel de la fuente.</p>
 */
public final class SketchRaster {
    private static final int ROW = 10;
    private static final MinecraftFont FONT = new MinecraftFont();

    private SketchRaster() {
    }

    public static void draw(PerspectiveRaster raster, WorldSketch sketch) {
        List<GizmoGeometry.Line> ghost = new ArrayList<>();
        for (WorldSketch.Line line : sketch.lines()) {
            if (line.seeThrough()) {
                ghost.add(new GizmoGeometry.Line(line.from(), line.to(), line.argb(), 1.6F));
            } else {
                raster.depthLine(line.from(), line.to(), line.argb(), 1.6);
            }
        }
        if (!ghost.isEmpty()) {
            raster.gizmo(new GizmoGeometry.Shape(ghost, List.of()), 1.0);
        }
        Vec3 eye = raster.eye();
        List<WorldSketch.Label> labels = new ArrayList<>(sketch.labels());
        // Los lejanos primero: los cercanos quedan encima, como en el juego con la prueba de profundidad apagada.
        labels.sort(Comparator.comparingDouble((WorldSketch.Label label) -> -label.at().distance(eye)));
        raster.paint(image -> labels.forEach(label -> label(image, raster, label)));
    }

    private static void label(BufferedImage image, PerspectiveRaster raster, WorldSketch.Label label) {
        double[] point = raster.project(label.at());
        if (point == null) {
            return;
        }
        double distance = label.at().distance(raster.eye());
        double regime = Math.clamp(distance / 9.0, 0.6, 8.0) / (distance / 9.0);
        int scale = Math.max(1, (int) Math.round(2.0 * regime * label.scale()));
        WorldSketch.PixelIcon icon = label.icon();
        int iconWidth = icon == null ? 0 : icon.width() + 3;
        int width = 0;
        for (WorldSketch.Row row : label.rows()) {
            width = Math.max(width, FONT.width(row.text()));
        }
        int height = label.rows().size() * ROW - 1;
        // El rótulo en su propia imagen (en píxeles de la fuente × escala) y luego encima, con su alfa.
        int boxWidth = width + iconWidth + 4;
        int boxHeight = height + 3;
        BufferedImage tile = new BufferedImage(boxWidth * scale, boxHeight * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = tile.createGraphics();
        // El alfa de todo el rótulo se pone al final, al componer.
        graphics.setColor(new java.awt.Color(0, 0, 0, label.solid() ? WorldSketch.Label.SOLID_BACKGROUND_ALPHA
                : WorldSketch.Label.BACKGROUND_ALPHA));
        graphics.fillRect(0, 0, tile.getWidth(), tile.getHeight());
        graphics.dispose();
        if (icon != null) {
            int top = 2 + (ROW - 1 - icon.height()) / 2;
            for (int y = 0; y < icon.height(); y++) {
                for (int x = 0; x < icon.width(); x++) {
                    if (icon.filled(x, y)) {
                        for (int dy = 0; dy < scale; dy++) {
                            for (int dx = 0; dx < scale; dx++) {
                                tile.setRGB((2 + x) * scale + dx, (top + y) * scale + dy,
                                        0xFF000000 | label.iconRgb());
                            }
                        }
                    }
                }
            }
        }
        for (int index = 0; index < label.rows().size(); index++) {
            WorldSketch.Row row = label.rows().get(index);
            int x = 2 + iconWidth + (width - FONT.width(row.text())) / 2;
            FONT.draw(tile, row.text(), x, 2 + index * ROW, 0xFF000000 | row.rgb(), false, scale, null);
        }
        Graphics2D target = image.createGraphics();
        target.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) label.alpha()));
        target.drawImage(tile, (int) Math.round(point[0] - tile.getWidth() / 2.0),
                (int) Math.round(point[1] - tile.getHeight() / 2.0), null);
        target.dispose();
    }
}
