package com.komainu.moxia.notes;

import com.komainu.moxia.feedback.WorldSketch;
import com.komainu.moxia.feedback.Words;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.studio.ui.StudioTheme;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cómo se ve una nota en el mundo, igual en el juego y en los renders: el rótulo con su icono y el texto partido en
 * renglones, un palito hasta el bloque o el punto y, cerca, el contorno del bloque y una línea con autor y fecha. Se
 * apaga poco a poco al llegar a su distancia.
 */
public final class NoteSketch {
    /**
     * Caracteres por renglón y renglones como mucho; lo que sobra acaba en «...» (tres puntos: el carácter «…» no
     * está en la fuente de píxeles del juego y un paquete de recursos podría no tenerlo).
     */
    public static final int ROW_CHARS = 30;
    public static final int MAX_ROWS = 4;
    /** Más cerca de esto se ven el autor, la fecha y el contorno del bloque. */
    public static final double DETAIL_DISTANCE = 24.0;
    /** A partir de esta fracción de su distancia la nota empieza a apagarse. */
    private static final double FADE_FROM = 0.8;
    private static final int TEXT = StudioTheme.WORLD_LABEL;
    private static final int MUTED = StudioTheme.WORLD_LABEL_MUTED;

    private NoteSketch() {
    }

    /**
     * @param camera       desde dónde se mira (la misma dimensión)
     * @param zone         el huso del jugador, para la fecha
     * @param showResolved dibujar también las resueltas (apagadas)
     */
    public static WorldSketch of(WorldNote note, Vec3 camera, ZoneId zone, long now, boolean showResolved,
                                 Words words) {
        if (note.resolved() && !showResolved) {
            return WorldSketch.EMPTY;
        }
        Vec3 label = new Vec3(note.labelX(), note.labelY(), note.labelZ());
        double distance = camera.distance(label);
        if (distance > note.distance()) {
            return WorldSketch.EMPTY;
        }
        double alpha = fade(distance, note.distance());
        int color = note.resolved() ? MUTED : note.color();
        int lineAlpha = (int) Math.round(alpha * 0xC0) << 24;
        WorldSketch.Builder sketch = WorldSketch.builder();
        if (note.block()) {
            // Un palito de la cara en la que se puso hasta el rótulo.
            int[] normal = note.normal();
            Vec3 out = new Vec3(normal[0], normal[1], normal[2]);
            Vec3 face = new Vec3(note.x() + 0.5, note.y() + 0.5, note.z() + 0.5).add(out.multiply(0.51));
            sketch.line(face, label.subtract(out.multiply(0.3)), lineAlpha | color);
            if (distance <= DETAIL_DISTANCE) {
                sketch.block((int) Math.floor(note.x()), (int) Math.floor(note.y()), (int) Math.floor(note.z()),
                        (int) Math.round(alpha * 0x90) << 24 | color);
            }
        } else {
            Vec3 point = new Vec3(note.x(), note.y(), note.z());
            double size = 0.2;
            sketch.ghostLine(point.add(new Vec3(-size, 0, 0)), point.add(new Vec3(size, 0, 0)), lineAlpha | color);
            sketch.ghostLine(point.add(new Vec3(0, 0, -size)), point.add(new Vec3(0, 0, size)), lineAlpha | color);
            sketch.ghostLine(point, label.add(new Vec3(0, -0.3, 0)), lineAlpha | color);
        }
        List<WorldSketch.Row> rows = new ArrayList<>();
        for (String row : wrap(note.text(), ROW_CHARS, MAX_ROWS)) {
            rows.add(new WorldSketch.Row(row, note.resolved() ? MUTED : TEXT));
        }
        if (distance <= DETAIL_DISTANCE) {
            rows.add(new WorldSketch.Row(meta(note, zone, now, words), MUTED));
        }
        NoteIcon icon = note.resolved() ? NoteIcon.TAREA : note.icon();
        sketch.label(new WorldSketch.Label(label, rows, icon.pixels(), color, alpha, 1.0, true));
        return sketch.build();
    }

    /** Autor, fecha y, si toca, «equipo» o «resuelta». */
    public static String meta(WorldNote note, ZoneId zone, long now, Words words) {
        StringBuilder meta = new StringBuilder(note.author().isEmpty() ? "?" : note.author());
        meta.append(" · ").append(date(note.created(), zone, now));
        if (note.team()) {
            meta.append(" · ").append(words.tr("gui.komaedit.notes.meta.team"));
        }
        if (note.resolved()) {
            meta.append(" · ").append(words.tr("gui.komaedit.notes.meta.resolved"));
        }
        return meta.toString();
    }

    /** {@code 21/10} este año; {@code 21/10/2025} si es de otro. */
    public static String date(long millis, ZoneId zone, long now) {
        ZonedDateTime date = Instant.ofEpochMilli(millis).atZone(zone);
        ZonedDateTime today = Instant.ofEpochMilli(now).atZone(zone);
        String day = String.format(Locale.ROOT, "%02d/%02d", date.getDayOfMonth(), date.getMonthValue());
        return date.getYear() == today.getYear() ? day : day + "/" + date.getYear();
    }

    static double fade(double distance, double limit) {
        double start = limit * FADE_FROM;
        if (distance <= start) {
            return 1.0;
        }
        return Math.clamp((limit - distance) / (limit - start), 0.0, 1.0);
    }

    /** Parte el texto por palabras en renglones de {@code width} caracteres como mucho. */
    public static List<String> wrap(String text, int width, int maxRows) {
        List<String> rows = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            while (word.length() > width) {
                // Una palabra más larga que el renglón se corta.
                if (!row.isEmpty()) {
                    rows.add(row.toString());
                    row.setLength(0);
                }
                rows.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (row.isEmpty()) {
                row.append(word);
            } else if (row.length() + 1 + word.length() <= width) {
                row.append(' ').append(word);
            } else {
                rows.add(row.toString());
                row.setLength(0);
                row.append(word);
            }
        }
        if (!row.isEmpty()) {
            rows.add(row.toString());
        }
        if (rows.size() > maxRows) {
            List<String> cut = new ArrayList<>(rows.subList(0, maxRows));
            String last = cut.getLast();
            cut.set(maxRows - 1, (last.length() > width - 3 ? last.substring(0, width - 3) : last) + "...");
            return cut;
        }
        return rows;
    }
}
