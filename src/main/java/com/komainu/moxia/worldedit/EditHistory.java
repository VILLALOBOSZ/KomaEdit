package com.komainu.moxia.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * El historial de deshacer de WorldEdit visto desde el Studio: qué operación fue cada paso y cuántos bloques cambió.
 * KomaEdit apunta el nombre de sus operaciones al guardarlas en el historial ({@link #label}); las de WorldEdit
 * salen sin nombre. WorldEdit no publica su historial, así que se lee de la sesión (si una versión lo cambia, el
 * Studio simplemente no enseña el historial).
 */
public final class EditHistory {
    /** Una operación del historial; {@code label} vacío si no es de KomaEdit. */
    public record Line(String label, long blocks) {
    }

    /**
     * El historial, lo último primero.
     *
     * @param undone cuántas de las primeras están deshechas (se pueden rehacer)
     */
    public record Snapshot(List<Line> lines, int undone) {
        public static final Snapshot EMPTY = new Snapshot(List.of(), 0);

        public Snapshot {
            lines = List.copyOf(lines);
        }
    }

    private static final Map<EditSession, String> LABELS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Field HISTORY = field("history");
    private static final Field POINTER = field("historyPointer");

    private EditHistory() {
    }

    private static Field field(String name) {
        try {
            Field field = LocalSession.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /** Apunta el nombre de una operación de KomaEdit antes de guardarla en el historial. */
    public static void label(EditSession session, String label) {
        if (session != null && label != null && !label.isBlank()) {
            LABELS.put(session, label.length() > 64 ? label.substring(0, 64) : label);
        }
    }

    /** Las últimas {@code limit} operaciones de la sesión, o vacío si no se puede leer. */
    public static Snapshot snapshot(LocalSession session, int limit) {
        if (session == null || HISTORY == null || POINTER == null) {
            return Snapshot.EMPTY;
        }
        try {
            List<?> history = (List<?>) HISTORY.get(session);
            int pointer = POINTER.getInt(session);
            List<Line> lines = new ArrayList<>();
            int size = history.size();
            for (int index = size - 1; index >= 0 && lines.size() < limit; index--) {
                if (history.get(index) instanceof EditSession edit) {
                    String label = LABELS.get(edit);
                    lines.add(new Line(label == null ? "" : label, edit.size()));
                }
            }
            int undone = Math.clamp(size - pointer, 0, lines.size());
            return new Snapshot(lines, undone);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return Snapshot.EMPTY;
        }
    }
}
