package com.komainu.moxia.studio.tools;

import com.komainu.moxia.notes.NoteColor;
import com.komainu.moxia.notes.NoteIcon;
import com.komainu.moxia.notes.NoteLimits;
import com.komainu.moxia.notes.NotePermissions;
import com.komainu.moxia.notes.NoteRequest;
import com.komainu.moxia.ruler.RulerMode;
import com.komainu.moxia.studio.ValueUnit;
import com.komainu.moxia.views.ViewNames;
import com.komainu.moxia.views.ViewPermissions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ayudas de obra que no colocan bloques: guardar vistas, la regla y las notas en el mundo. Las vistas y la regla son
 * del cliente: sus órdenes ({@code /kegui …}) las atiende el propio cliente aunque las mande el Studio; las notas van
 * al servidor como siempre ({@code /komaedit nota}).
 */
public final class BuilderAidCards {
    private BuilderAidCards() {
    }

    /** Vistas guardadas: guardar la de ahora con un nombre, saltar, fijar, compartir y la lista. */
    public static final class Views extends ToolCard {
        Views() {
            super("views", ToolFamily.UTILITIES, ToolTarget.SELF,
                    List.of("views", "vista", "vistas", "camara", "cámara", "teleporte", "fija", "mirador",
                            "guardar", "saltar", "axiom"),
                    List.of(new ToolParam.Text("view_name", "", ViewNames.MAX_LENGTH, true, null)));
        }

        private static String name(ToolValues values) {
            return ViewNames.clean(values.text("view_name"));
        }

        @Override
        public boolean clientOnly() {
            return true;
        }

        /** Nunca apagada: lo suyo es del cliente y no pide permiso (saltar y compartir lo dicen en sus botones). */
        @Override
        public boolean locked(ToolContext context) {
            return false;
        }

        /** Guarda en el cliente la vista de ahora con su nombre. */
        @Override
        public String command(ToolValues values, boolean preview) {
            return "kegui vista guardar " + name(values);
        }

        @Override
        public boolean previewable() {
            return false;
        }

        /** Guardar es del cliente: no pide permiso. Saltar y compartir, sí (en sus acciones). */
        @Override
        public List<String> permissions(ToolValues values) {
            return List.of();
        }

        /** Sin nombre, «Ir» y «Fijar» abren la lista en lugar de mandar una orden a medias. */
        @Override
        public List<ToolAction> actions() {
            return List.of(
                    new ToolAction("view_go", List.of(ViewPermissions.TELEPORT), false,
                            values -> name(values).isEmpty() ? "kegui vistas" : "kegui vista ir " + name(values)),
                    new ToolAction("view_pin", List.of(), false,
                            values -> name(values).isEmpty() ? "kegui vistas" : "kegui vista fijar " + name(values)),
                    new ToolAction("view_share", List.of(ViewPermissions.SHARE), false,
                            values -> name(values).isEmpty() ? "kegui vistas"
                                    : "komaedit vista compartir " + name(values)),
                    new ToolAction("view_back", List.of(ViewPermissions.TELEPORT), false,
                            values -> "kegui vista volver"),
                    new ToolAction("view_list", List.of(), false, values -> "kegui vistas"));
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            String name = name(values);
            if (name.isEmpty()) {
                return List.of(ToolProblem.error("view_name"));
            }
            return ViewNames.valid(name) ? List.of() : List.of(ToolProblem.error("view_name"));
        }
    }

    /** La regla del cliente: elegir el modo y ponerla; copiar, borrar y quitarla. */
    public static final class Ruler extends ToolCard {
        public static final List<String> MODES = Arrays.stream(RulerMode.values()).map(RulerMode::id).toList();

        Ruler() {
            super("ruler", ToolFamily.UTILITIES, ToolTarget.MODE,
                    List.of("ruler", "regla", "medir", "medida", "medidas", "distancia", "metro", "cinta",
                            "measure", "manhattan", "radio", "diametro"),
                    List.of(new ToolParam.Choice("ruler_mode", MODES, MODES.getFirst(),
                            ToolParam.Choice.Style.BUTTONS, "", null)));
        }

        @Override
        public boolean clientOnly() {
            return true;
        }

        /** Nunca apagada: lo suyo es del cliente y no pide permiso (saltar y compartir lo dicen en sus botones). */
        @Override
        public boolean locked(ToolContext context) {
            return false;
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            return "kegui regla " + values.choice("ruler_mode");
        }

        @Override
        public boolean previewable() {
            return false;
        }

        /** Es del cliente: no toca el servidor ni pide permiso. */
        @Override
        public List<String> permissions(ToolValues values) {
            return List.of();
        }

        @Override
        public List<ToolAction> actions() {
            return List.of(
                    new ToolAction("ruler_copy", List.of(), false, values -> "kegui regla copiar"),
                    new ToolAction("ruler_clear", List.of(), false, values -> "kegui regla borrar"),
                    new ToolAction("ruler_off", List.of(), false, values -> "kegui regla off"));
        }
    }

    /** Una nota en el bloque donde haces clic (o donde estás), con color, icono, distancia y para quién. */
    public static final class Note extends ToolCard {
        public static final List<String> COLORS = NoteColor.ids();
        public static final List<String> ICONS = NoteIcon.ids();

        Note() {
            super("note", ToolFamily.UTILITIES, ToolTarget.LOOK,
                    List.of("note", "nota", "notas", "notes", "apunte", "marca", "tarea", "aviso", "comentario",
                            "pendiente", "equipo"),
                    List.of(
                            new ToolParam.Text("note", "", NoteLimits.DEFAULT.maxLength(), true, null),
                            new ToolParam.Choice("note_color", COLORS, NoteColor.AMARILLO.id(),
                                    ToolParam.Choice.Style.CYCLE, "", null),
                            new ToolParam.Choice("note_icon", ICONS, NoteIcon.NOTA.id(),
                                    ToolParam.Choice.Style.CYCLE, "", null),
                            toggle("team", false),
                            number("distance", NoteLimits.MIN_DISTANCE, NoteLimits.DEFAULT.maxDistance(), 4, 16,
                                    NoteLimits.DEFAULT_DISTANCE).unit(ValueUnit.BLOCKS),
                            toggle("here", false)
                    ));
        }

        @Override
        public List<String> essentials() {
            return List.of("note", "note_color", "note_icon", "team");
        }

        @Override
        public String command(ToolValues values, boolean preview) {
            CommandLine line = new CommandLine("komaedit nota poner " + NoteRequest.quote(values.text("note")
                    .strip()));
            if (!values.choice("note_color").equals(NoteColor.AMARILLO.id())) {
                line.flag("-c", values.choice("note_color"));
            }
            if (!values.choice("note_icon").equals(NoteIcon.NOTA.id())) {
                line.flag("-i", values.choice("note_icon"));
            }
            line.changed(values, "distance", "-d");
            return line.flag("-e", values.toggle("team")).flag("-p", values.toggle("here")).build();
        }

        @Override
        public boolean previewable() {
            return false;
        }

        @Override
        public List<String> permissions(ToolValues values) {
            return List.of(values.toggle("team") ? NotePermissions.SHARE : NotePermissions.PRIVATE);
        }

        @Override
        public java.util.Set<String> allPermissions() {
            return new java.util.LinkedHashSet<>(List.of(NotePermissions.PRIVATE, NotePermissions.SHARE));
        }

        @Override
        public List<ToolAction> actions() {
            return List.of(
                    new ToolAction("notes_list", List.of(), false, values -> "kegui notas"),
                    new ToolAction("notes_resolved", List.of(), false, values -> "kegui notas resueltas"));
        }

        @Override
        public List<ToolProblem> validate(ToolValues values) {
            List<ToolProblem> problems = new ArrayList<>();
            if (values.text("note").isBlank()) {
                problems.add(ToolProblem.error("text_empty"));
            }
            return problems;
        }
    }
}
