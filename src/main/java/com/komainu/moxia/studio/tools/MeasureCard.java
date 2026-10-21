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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * «Medir y anotar»: las ayudas de obra que no colocan bloques, como modos de una sola tarjeta. La regla (medir con
 * clics), las notas en el mundo y las vistas guardadas. La regla y las vistas son del cliente: sus órdenes
 * ({@code /kegui …}) las atiende el propio cliente aunque las mande el Studio; las notas van al servidor como
 * siempre ({@code /komaedit nota}).
 */
public final class MeasureCard extends ToolCard {
    /** El ajuste del modo: qué ayuda. */
    public static final String MODE = "aid";
    public static final String RULER = "regla";
    public static final String NOTE = "nota";
    public static final String VIEW = "vista";
    /** Los modos, en el orden de sus botones: lo que más se usa primero. */
    public static final List<String> MODES = List.of(RULER, NOTE, VIEW);
    public static final List<String> RULER_MODES = Arrays.stream(RulerMode.values()).map(RulerMode::id).toList();
    public static final List<String> COLORS = NoteColor.ids();
    public static final List<String> ICONS = NoteIcon.ids();

    MeasureCard() {
        super("measure", ToolTarget.MODE,
                List.of("medir", "anotar", "regla", "ruler", "medida", "medidas", "distancia", "metro", "cinta",
                        "measure", "manhattan", "radio", "diametro", "nota", "notas", "note", "notes", "apunte",
                        "marca", "tarea", "aviso", "comentario", "pendiente", "vista", "vistas", "views", "camara",
                        "teleporte", "fija", "mirador", "axiom"),
                List.of(
                        new ToolParam.Choice(MODE, MODES, RULER, ToolParam.Choice.Style.BUTTONS, "", null),
                        // Regla.
                        in(RULER, new ToolParam.Choice("ruler_mode", RULER_MODES, RULER_MODES.getFirst(),
                                ToolParam.Choice.Style.BUTTONS, "", null)),
                        // Nota.
                        in(NOTE, new ToolParam.Text("note", "", NoteLimits.DEFAULT.maxLength(), true, null)),
                        in(NOTE, new ToolParam.Choice("note_icon", ICONS, NoteIcon.NOTA.id(),
                                ToolParam.Choice.Style.CYCLE, "", null)),
                        in(NOTE, toggle("team", false)),
                        in(NOTE, new ToolParam.Choice("note_color", COLORS, NoteColor.AMARILLO.id(),
                                ToolParam.Choice.Style.CYCLE, "", null)),
                        in(NOTE, number("distance", NoteLimits.MIN_DISTANCE, NoteLimits.DEFAULT.maxDistance(), 4, 16,
                                NoteLimits.DEFAULT_DISTANCE).unit(ValueUnit.BLOCKS)),
                        in(NOTE, toggle("here", false)),
                        // Vista.
                        in(VIEW, new ToolParam.Text("view_name", "", ViewNames.MAX_LENGTH, true, null))
                ));
    }

    /** El ajuste, visible solo en el modo {@code mode}. */
    private static ToolParam in(String mode, ToolParam param) {
        return when(values -> values.choice(MODE).equals(mode), param);
    }

    private static String mode(ToolValues values) {
        return values.choice(MODE);
    }

    /** Qué ayuda va primero: los ajustes y los botones que salen son los de ese modo. */
    @Override
    public String modeParam() {
        return MODE;
    }

    /** A la vista: el modo y, como mucho, tres ajustes suyos; color, distancia y «donde estoy», en Más ajustes. */
    @Override
    public List<String> essentials() {
        return List.of(MODE, "ruler_mode", "note", "note_icon", "team", "view_name");
    }

    /** La regla se queda puesta; la nota va donde haces clic; la vista se guarda donde estás. */
    @Override
    protected ToolTarget modeTarget(ToolValues values) {
        return switch (mode(values)) {
            case NOTE -> ToolTarget.LOOK;
            case VIEW -> ToolTarget.SELF;
            default -> ToolTarget.MODE;
        };
    }

    /** La regla y las vistas no pasan por el servidor. */
    @Override
    public boolean clientOnly(ToolValues values) {
        return !mode(values).equals(NOTE);
    }

    @Override
    public String command(ToolValues values, boolean preview) {
        return switch (mode(values)) {
            case NOTE -> noteCommand(values);
            case VIEW -> "kegui vista guardar " + viewName(values);
            default -> "kegui regla " + values.choice("ruler_mode");
        };
    }

    private static String noteCommand(ToolValues values) {
        CommandLine line = new CommandLine("komaedit nota poner " + NoteRequest.quote(values.text("note").strip()));
        if (!values.choice("note_color").equals(NoteColor.AMARILLO.id())) {
            line.flag("-c", values.choice("note_color"));
        }
        if (!values.choice("note_icon").equals(NoteIcon.NOTA.id())) {
            line.flag("-i", values.choice("note_icon"));
        }
        line.changed(values, "distance", "-d");
        return line.flag("-e", values.toggle("team")).flag("-p", values.toggle("here")).build();
    }

    private static String viewName(ToolValues values) {
        return ViewNames.clean(values.text("view_name"));
    }

    /** El botón dice lo que hace el modo: «Medir», «Poner nota», «Guardar aquí». */
    @Override
    public String buildKey(ToolValues values) {
        return nameKey() + ".build." + mode(values);
    }

    @Override
    public boolean previewable() {
        return false;
    }

    /** La regla y guardar una vista son del cliente: no piden permiso. Saltar y compartir, en sus botones. */
    @Override
    public List<String> permissions(ToolValues values) {
        if (!mode(values).equals(NOTE)) {
            return List.of();
        }
        return List.of(values.toggle("team") ? NotePermissions.SHARE : NotePermissions.PRIVATE);
    }

    @Override
    public Set<String> allPermissions() {
        return new LinkedHashSet<>(List.of(NotePermissions.PRIVATE, NotePermissions.SHARE, ViewPermissions.TELEPORT,
                ViewPermissions.SHARE));
    }

    /** Nunca apagada: la regla y las vistas son del cliente y no piden permiso. */
    @Override
    public boolean locked(ToolContext context) {
        return false;
    }

    /**
     * Todos los botones; se ven los del modo elegido. Sin nombre, «Ir», «Fijar» y «Compartir aquí» abren la lista en
     * lugar de mandar una orden a medias.
     */
    @Override
    public List<ToolAction> actions() {
        return List.of(
                new ToolAction("ruler_copy", List.of(), false, values -> "kegui regla copiar"),
                new ToolAction("ruler_clear", List.of(), false, values -> "kegui regla borrar"),
                new ToolAction("ruler_off", List.of(), false, values -> "kegui regla off"),
                new ToolAction("notes_list", List.of(), false, values -> "kegui notas"),
                new ToolAction("notes_resolved", List.of(), false, values -> "kegui notas resueltas"),
                new ToolAction("view_go", List.of(ViewPermissions.TELEPORT), false,
                        values -> viewName(values).isEmpty() ? "kegui vistas" : "kegui vista ir " + viewName(values)),
                new ToolAction("view_pin", List.of(), false,
                        values -> viewName(values).isEmpty() ? "kegui vistas" : "kegui vista fijar " + viewName(values)),
                new ToolAction("view_share", List.of(ViewPermissions.SHARE), false,
                        values -> viewName(values).isEmpty() ? "kegui vistas"
                                : "komaedit vista compartir " + viewName(values)),
                new ToolAction("view_back", List.of(ViewPermissions.TELEPORT), false, values -> "kegui vista volver"),
                new ToolAction("view_list", List.of(), false, values -> "kegui vistas"));
    }

    @Override
    public List<ToolAction> actions(ToolValues values) {
        String prefix = switch (mode(values)) {
            case NOTE -> "notes_";
            case VIEW -> "view_";
            default -> "ruler_";
        };
        return actions().stream().filter(action -> action.id().startsWith(prefix)).toList();
    }

    @Override
    public List<ToolProblem> validate(ToolValues values) {
        return switch (mode(values)) {
            case NOTE -> values.text("note").isBlank() ? List.of(ToolProblem.error("text_empty")) : List.of();
            case VIEW -> ViewNames.valid(viewName(values)) ? List.of() : List.of(ToolProblem.error("view_name"));
            default -> List.of();
        };
    }
}
