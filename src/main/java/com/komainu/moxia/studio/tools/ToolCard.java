package com.komainu.moxia.studio.tools;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Una herramienta de la etapa Herramientas del Studio: sus ajustes, la orden que manda Construir, los permisos que
 * pide y lo que hay que comprobar antes. No sabe nada de Minecraft ni de la pantalla, así que todo se prueba fuera
 * del juego.
 *
 * <p>Construir manda exactamente la orden de {@link #command}, la misma que escribiría el jugador: el servidor la
 * analiza, comprueba los permisos y la ejecuta como siempre. La tarjeta no repite ninguna lógica del servidor.</p>
 *
 * <p>Textos: {@code gui.komaedit.tools.<id>} (nombre), {@code ….description} y {@code ….build} (botón).</p>
 */
public abstract class ToolCard {
    private final String id;
    private final ToolFamily family;
    private final ToolTarget target;
    private final List<String> keywords;
    private final List<ToolParam> params;
    private final ToolValues defaults;

    protected ToolCard(String id, ToolFamily family, ToolTarget target, List<String> keywords, List<ToolParam> params) {
        this.id = id;
        this.family = family;
        this.target = target;
        this.keywords = List.copyOf(keywords);
        this.params = List.copyOf(params);
        this.defaults = ToolValues.defaults(this.params);
    }

    public final String id() {
        return id;
    }

    public final ToolFamily family() {
        return family;
    }

    public final ToolTarget target() {
        return target;
    }

    /** Palabras que también la encuentran en el buscador: la orden, sus alias y sinónimos. */
    public final List<String> keywords() {
        return keywords;
    }

    public final List<ToolParam> params() {
        return params;
    }

    public final ToolValues defaults() {
        return defaults;
    }

    public final String nameKey() {
        return "gui.komaedit.tools." + id;
    }

    public final String descriptionKey() {
        return nameKey() + ".description";
    }

    /** Texto del botón Construir de esta herramienta («Plantar», «Tallar», «Contar»…). */
    public final String buildKey() {
        return nameKey() + ".build";
    }

    /** Ayuda de un ajuste en esta tarjeta: {@code gui.komaedit.tools.<id>.help.<ajuste>}. */
    public final String helpKey(ToolParam param) {
        return nameKey() + ".help." + param.id();
    }

    /** La orden de Construir, sin barra. {@code preview} añade la bandera de vista previa si la admite. */
    public abstract String command(ToolValues values, boolean preview);

    /** Nodos de permiso que necesita Construir con estos valores (todos). */
    public abstract List<String> permissions(ToolValues values);

    /** Si Construir admite la vista previa universal ({@code -v}). */
    public boolean previewable() {
        return true;
    }

    /** Bandera de vista previa: {@code -v} salvo donde la orden ya usa {@code -v} para otra cosa. */
    public String previewFlag() {
        return "-v";
    }

    /** Más botones de la tarjeta. */
    public List<ToolAction> actions() {
        return List.of();
    }

    /** Ajustes rápidos («Joven», «Viejo»…): cada uno cambia unos pocos valores sobre los de serie. */
    public List<ToolPreset> presets() {
        return List.of();
    }

    /** Lo que vale un ajuste en automático, para enseñarlo en gris ({@code null} si no se sabe). */
    public String autoHint(String paramId, ToolValues values) {
        return null;
    }

    /** Problemas de los valores (texto vacío, opciones que no casan…). */
    public List<ToolProblem> validate(ToolValues values) {
        return List.of();
    }

    /** Todos los nodos que la tarjeta puede llegar a pedir, para preguntárselos al servidor. */
    public Set<String> allPermissions() {
        Set<String> nodes = new LinkedHashSet<>(permissions(defaults));
        for (ToolAction action : actions()) {
            nodes.addAll(action.permissions());
        }
        return nodes;
    }

    /** El primer nodo que falta para Construir, o {@code null}. */
    public final String missingPermission(ToolValues values, ToolContext context) {
        for (String node : permissions(values)) {
            if (!context.allows(node)) {
                return node;
            }
        }
        return null;
    }

    /** Nodos de una acción: los suyos o, si no declara ninguno, los de Construir con estos valores. */
    public final List<String> actionPermissions(ToolAction action, ToolValues values) {
        return action.permissions().isEmpty() ? permissions(values) : action.permissions();
    }

    /** El primer nodo que falta para una acción, o {@code null}. */
    public final String missingPermission(ToolAction action, ToolValues values, ToolContext context) {
        for (String node : actionPermissions(action, values)) {
            if (!context.allows(node)) {
                return node;
            }
        }
        return null;
    }

    /** Si al jugador le falta todo: ni Construir ni ninguna acción. La tarjeta sale apagada en la lista. */
    public boolean locked(ToolContext context) {
        if (!context.known()) {
            return false;
        }
        for (String node : allPermissions()) {
            if (context.allows(node)) {
                return false;
            }
        }
        return true;
    }

    /** Lo que falta en el mundo para Construir (selección, puntos), según {@link #target()}. */
    public ToolProblem requirement(ToolValues values, ToolContext context) {
        return switch (target) {
            case SELECTION -> context.known() && !context.hasSelection()
                    ? ToolProblem.error("no_selection") : null;
            case PATH_OR_SELECTION -> context.known() && context.pathPoints() < 2 && !context.hasSelection()
                    ? ToolProblem.error("no_path_or_selection") : null;
            default -> null;
        };
    }

    /**
     * Todo lo que impide o avisa al construir: permisos, lo que falta en el mundo y la validación, en ese orden.
     * El primero bloqueante apaga el botón.
     */
    public final List<ToolProblem> problems(ToolValues values, ToolContext context) {
        List<ToolProblem> problems = new ArrayList<>();
        String missing = missingPermission(values, context);
        if (missing != null) {
            problems.add(ToolProblem.error("permission", missing));
        }
        ToolProblem requirement = requirement(values, context);
        if (requirement != null) {
            problems.add(requirement);
        }
        for (ToolParam param : params) {
            String text = switch (param) {
                case ToolParam.Text ignored -> values.text(param.id());
                case ToolParam.Block ignored -> values.block(param.id());
                default -> null;
            };
            if (text != null && !legal(text)) {
                problems.add(ToolProblem.error("illegal_character", param.id()));
            }
        }
        problems.addAll(validate(values));
        return problems;
    }

    /**
     * Minecraft echa del servidor a quien manda una orden con {@code §} o caracteres de control: la tarjeta no deja
     * construir con ellos.
     */
    public static boolean legal(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '§' || character < ' ' || character == 127) {
                return false;
            }
        }
        return true;
    }

    public final boolean blocked(ToolValues values, ToolContext context) {
        return problems(values, context).stream().anyMatch(ToolProblem::blocking);
    }

    /** Valores con un ajuste rápido aplicado sobre los de serie (conserva lo que el ajuste no toca). */
    public ToolValues apply(ToolValues values, ToolPreset preset) {
        ToolValues result = values;
        for (Map.Entry<String, Object> entry : preset.values().entrySet()) {
            result = result.with(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** Lee un preset guardado en el servidor ({@code #nombre}) en valores de la tarjeta; solo árboles y rocas. */
    public ToolValues parsePreset(String settings) {
        throw new UnsupportedOperationException(id + " no tiene presets guardados");
    }

    /** Si la tarjeta lee los presets guardados del jugador. */
    public boolean savedPresets() {
        return false;
    }

    /** La orden de una acción que pide nombre, con el nombre ya puesto. */
    public static String withName(String command, String name) {
        return command.replace(NAME_PLACEHOLDER, name);
    }

    /** Hueco del nombre en la orden de una acción con {@link ToolAction#needsName()}. */
    public static final String NAME_PLACEHOLDER = "\u0000name\u0000";

    // ── Ayudas para las subclases ───────────────────────────────────────────────────────────────────────

    protected static ToolParam.Number number(String id, int min, int max, int step, int bigStep, int defaultValue) {
        return new ToolParam.Number(id, new com.komainu.moxia.studio.NumericRange(min, max, step, bigStep),
                defaultValue, 0, false, null);
    }

    /**
     * Número que de serie no lleva bandera. {@code start} es desde donde arranca al tocarlo si la tarjeta no sabe
     * cuánto vale en automático ({@link #autoHint}).
     */
    protected static ToolParam.Number auto(String id, int min, int max, int step, int bigStep, int decimals,
                                           int start) {
        return new ToolParam.Number(id, new com.komainu.moxia.studio.NumericRange(min, max, step, bigStep), start,
                decimals, true, null);
    }

    protected static ToolParam.Number decimal(String id, int min, int max, int step, int bigStep, int defaultValue,
                                              int decimals) {
        return new ToolParam.Number(id, new com.komainu.moxia.studio.NumericRange(min, max, step, bigStep),
                defaultValue, decimals, false, null);
    }

    /** El mismo ajuste, visible solo cuando {@code shown} lo dice. */
    protected static ToolParam when(java.util.function.Predicate<ToolValues> shown, ToolParam param) {
        return switch (param) {
            case ToolParam.Number number -> new ToolParam.Number(number.id(), number.range(), number.defaultValue(),
                    number.decimals(), number.auto(), shown);
            case ToolParam.Choice choice -> new ToolParam.Choice(choice.id(), choice.options(), choice.defaultValue(),
                    choice.style(), choice.source(), shown);
            case ToolParam.Toggle toggle -> new ToolParam.Toggle(toggle.id(), toggle.defaultValue(), shown);
            case ToolParam.Block block -> new ToolParam.Block(block.id(), block.defaultValue(), shown);
            case ToolParam.Text text -> new ToolParam.Text(text.id(), text.defaultValue(), text.maxLength(),
                    text.required(), shown);
        };
    }

    protected static ToolParam.Choice choice(String id, ToolParam.Choice.Style style, String... options) {
        return new ToolParam.Choice(id, List.of(options), options[0], style, "", null);
    }

    protected static ToolParam.Block block(String id, String defaultValue) {
        return new ToolParam.Block(id, defaultValue, null);
    }

    /** Semilla: en automático, una al azar en cada orden (el chat la dice para repetirla). */
    protected static ToolParam.Number seed() {
        return auto("seed", 0, 999_999_999, 1, 1_000, 0, 0);
    }

    protected static ToolParam.Toggle toggle(String id, boolean defaultValue) {
        return new ToolParam.Toggle(id, defaultValue, null);
    }

    protected static ToolParam.Text mask() {
        return new ToolParam.Text("mask", "", 128, false, null);
    }

    /** Los ajustes de texto que van a una bandera no pueden llevar espacios (partirían la orden). */
    protected static void noSpaces(ToolValues values, String id, List<ToolProblem> problems) {
        String value = values.param(id) instanceof ToolParam.Block ? values.block(id) : values.text(id);
        if (value != null && value.strip().contains(" ")) {
            problems.add(ToolProblem.error("spaces", id));
        }
    }
}
