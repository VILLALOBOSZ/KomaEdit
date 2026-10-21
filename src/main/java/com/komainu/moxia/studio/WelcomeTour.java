package com.komainu.moxia.studio;

import com.komainu.moxia.studio.tools.PaintCard;
import com.komainu.moxia.studio.tools.TerrainCards;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.TreeCard;
import com.komainu.moxia.studio.tools.ui.JobStrip;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * La bienvenida guiada del Studio, sin Minecraft ni dibujo: en qué paso va, qué tiene que hacer el jugador y cuándo lo
 * ha hecho. Cinco pasos sobre el mundo de verdad (plantar un árbol, moverlo y colocarlo, estampar una montaña y
 * cambiarle la semilla, pintarla y deshacer), cada uno en golpes de una sola acción. Solo avanza con lo que hace el
 * jugador: lo que la vista ve en cada fotograma ({@link Situation}) y lo que le cuenta (las órdenes que se mandan, los
 * clics con el pincel, deshacer en el mundo y los controles que se pulsan).
 *
 * <p>Nunca coloca nada por su cuenta: el árbol sale como fantasma (o vista previa) que el jugador coloca, y el sello y
 * la pintura con la vista previa del pincel, que el jugador aplica o descarta desde la burbuja. En supervivencia o sin
 * ningún permiso de construir solo enseña: lo que tocaría el mundo pasa a ser un golpe de mirar, con «Siguiente». Si
 * falta el permiso de un paso, ese paso se salta solo y la burbuja siguiente lo dice.</p>
 */
public final class WelcomeTour {
    /** Sube cuando la bienvenida cambia tanto que merece volver a salir a quien ya la vio. */
    public static final int VERSION = 1;
    public static final String TREE = "tree";
    public static final String STAMP = "stamp";
    public static final String PAINT = "paint";
    /** El preset de Pintar que se enseña: nieve arriba, sobre la montaña recién hecha. */
    public static final String PEAKS = "paint_peaks";
    /** El grupo de las miniaturas de la galería de sellos (pulsar una es elegir sello). */
    public static final String STAMP_GROUP = "tools:stamp";
    public static final String APPLY = "komaedit pincel aplicar";
    public static final String DISCARD = "komaedit pincel cancelar";
    /** El ancla del sello elegido, sea cual sea: la vista la cambia por el suyo. */
    public static final String CHOSEN_STAMP = "stamp:chosen";
    public static final String GHOST_PLACE_ANCHOR = "ghost:place";

    /** Los cinco pasos, con el permiso que necesita cada uno para hacerse de verdad. */
    public enum Step {
        TREE(TreeCard.NODE, WelcomeTour.TREE),
        PLACE(TreeCard.NODE, WelcomeTour.TREE),
        STAMP(TerrainCards.STAMP_NODE, WelcomeTour.STAMP),
        PAINT(PaintCard.NODE, WelcomeTour.PAINT),
        UNDO("", "");

        private final String node;
        private final String tool;

        Step(String node, String tool) {
            this.node = node;
            this.tool = tool;
        }

        public String node() {
            return node;
        }

        /** La herramienta del paso, o vacío. */
        public String tool() {
            return tool;
        }

        public String titleKey() {
            return "gui.komaedit.welcome.step." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Qué clase de golpe es. */
    public enum Kind {
        /** Hace falta que el jugador haga algo (en el panel o en el mundo). */
        ACTION,
        /** Solo se mira: avanza con «Siguiente». */
        LOOK,
        /** La entrada y el final: una tarjeta en medio del mundo. */
        CARD
    }

    /**
     * Un golpe: una acción, una frase. {@code anchors} son las zonas a las que señala, por orden de preferencia; sin
     * ninguna, la burbuja va sobre el mundo (lo que se hace ahí es un clic en el suelo).
     */
    public enum Beat {
        INTRO(null, Kind.CARD),
        TREE_PICK(Step.TREE, Kind.ACTION, "tool:tree"),
        TREE_CLICK(Step.TREE, Kind.ACTION),
        TREE_LOOK(Step.TREE, Kind.LOOK, "footer:build"),
        GHOST_MOVE(Step.PLACE, Kind.ACTION),
        GHOST_PLACE(Step.PLACE, Kind.ACTION, GHOST_PLACE_ANCHOR, "job:confirm"),
        STAMP_PICK(Step.STAMP, Kind.ACTION, "tool:stamp"),
        STAMP_CHOOSE(Step.STAMP, Kind.ACTION, "stamp:montana", CHOSEN_STAMP),
        STAMP_BRUSH(Step.STAMP, Kind.ACTION, "footer:build"),
        STAMP_CLICK(Step.STAMP, Kind.ACTION),
        STAMP_SEED(Step.STAMP, Kind.ACTION, "preset:random"),
        STAMP_AGAIN(Step.STAMP, Kind.ACTION),
        STAMP_APPLY(Step.STAMP, Kind.ACTION),
        STAMP_LOOK(Step.STAMP, Kind.LOOK, "footer:build"),
        PAINT_PICK(Step.PAINT, Kind.ACTION, "tool:" + PAINT),
        PAINT_PRESET(Step.PAINT, Kind.ACTION, "preset:" + PEAKS),
        PAINT_BRUSH(Step.PAINT, Kind.ACTION, "footer:build"),
        PAINT_CLICK(Step.PAINT, Kind.ACTION),
        PAINT_APPLY(Step.PAINT, Kind.ACTION),
        PAINT_LOOK(Step.PAINT, Kind.LOOK, "footer:build"),
        UNDO_OPEN(Step.UNDO, Kind.ACTION, "top:history"),
        UNDO_DO(Step.UNDO, Kind.ACTION, "history:undo"),
        UNDO_LOOK(Step.UNDO, Kind.LOOK, "history:undo"),
        DONE(null, Kind.CARD);

        private final Step step;
        private final Kind kind;
        private final List<String> anchors;

        Beat(Step step, Kind kind, String... anchors) {
            this.step = step;
            this.kind = kind;
            this.anchors = List.of(anchors);
        }

        public Step step() {
            return step;
        }

        public Kind kind() {
            return kind;
        }

        public String textKey() {
            return "gui.komaedit.welcome.beat." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Lo que la vista ve en un fotograma.
     *
     * @param creative       el jugador está en creativo
     * @param context        los permisos que el servidor le ha confirmado
     * @param toolsStage     la etapa abierta es Herramientas
     * @param tool           la herramienta elegida (con sus ajustes a la vista), o vacío si se ve la cuadrícula
     * @param stampSeed      la semilla del sello de relieve ({@code null}: al azar)
     * @param peaks          Pintar tiene puesto el preset «Cumbres»
     * @param ghost          qué fantasma hay y dónde está, o {@code null} sin fantasma
     * @param ghostWaiting   el fantasma está confirmado, esperando al servidor
     * @param previewWaiting hay una vista previa esperando a Confirmar
     * @param historyOpen    la lista de cambios en el mundo está abierta
     * @param historyEntries cuántos cambios cuenta
     * @param pathPoints     puntos de la trayectoria (con ellos el árbol va en cada punto, no donde se hace clic)
     */
    public record Situation(boolean creative, ToolContext context, boolean toolsStage, String tool, Integer stampSeed,
                            boolean peaks, String ghost, boolean ghostWaiting, boolean previewWaiting,
                            boolean historyOpen, int historyEntries, int pathPoints) {
        public static final Situation START = new Situation(true, ToolContext.UNKNOWN, false, "", null, false, null,
                false, false, false, 0, 0);

        public Situation {
            context = context == null ? ToolContext.UNKNOWN : context;
            tool = tool == null ? "" : tool;
        }

        /** Si se ven los ajustes de {@code id} en Herramientas. */
        boolean holding(String id) {
            return toolsStage && tool.equals(id);
        }
    }

    /**
     * Lo que dice la burbuja encima de la frase: un paso saltado, algo cancelado. {@code names} son claves de nombres
     * (las herramientas sin permiso), que van juntas en el único {@code %s} del texto.
     */
    public record Notice(String key, List<String> names) {
        public Notice {
            names = List.copyOf(names);
        }
    }

    private static final List<Beat> DOING = List.of(Beat.TREE_PICK, Beat.TREE_CLICK, Beat.GHOST_MOVE,
            Beat.GHOST_PLACE, Beat.STAMP_PICK, Beat.STAMP_CHOOSE, Beat.STAMP_BRUSH, Beat.STAMP_CLICK, Beat.STAMP_SEED,
            Beat.STAMP_AGAIN, Beat.STAMP_APPLY, Beat.PAINT_PICK, Beat.PAINT_PRESET, Beat.PAINT_BRUSH,
            Beat.PAINT_CLICK, Beat.PAINT_APPLY, Beat.UNDO_OPEN, Beat.UNDO_DO);
    private static final List<Beat> SHOWING = List.of(Beat.TREE_PICK, Beat.TREE_LOOK, Beat.STAMP_PICK,
            Beat.STAMP_CHOOSE, Beat.STAMP_SEED, Beat.STAMP_LOOK, Beat.PAINT_PICK, Beat.PAINT_PRESET, Beat.PAINT_LOOK,
            Beat.UNDO_OPEN, Beat.UNDO_LOOK);

    private Beat beat;
    private boolean showing;
    private List<Beat> plan = List.of();
    private int index;
    private Situation last = Situation.START;
    private Notice notice;
    private final Set<Step> skipped = EnumSet.noneOf(Step.class);
    private final List<String> outbox = new ArrayList<>();
    private boolean started;
    /** El golpe al que volver cuando el jugador vuelva a la herramienta del paso. */
    private Beat resume;
    private boolean treeSent;
    private String ghostStart;
    /** El fantasma estaba confirmado la última vez que se vio. */
    private boolean placed;
    /** Se pulsó Colocar desde la última vez que se miró. */
    private boolean pressedPlace;
    private boolean confirmSent;
    private Integer seedStart;
    /** Hay una vista previa del pincel esperando, hecha en la bienvenida: al saltar se descarta. */
    private boolean brushPreview;

    // ── Estado ──────────────────────────────────────────────────────────────────────────────────────────

    /** Si la bienvenida está en pantalla (entrada, pasos o final). */
    public boolean active() {
        return beat != null;
    }

    /** Si ya empezaron los pasos (pasada la entrada y antes de acabar). */
    public boolean running() {
        return beat != null && beat.kind() != Kind.CARD;
    }

    /** Si se hace de verdad: ya empezó y el jugador puede construir. */
    public boolean doing() {
        return running() && !showing;
    }

    /** Si solo enseña (supervivencia o sin permisos de construir). */
    public boolean showing() {
        return showing;
    }

    public Beat beat() {
        return beat;
    }

    public Step step() {
        return beat == null ? null : beat.step();
    }

    public Notice notice() {
        return notice;
    }

    /** Si con lo que se ve ahora, al empezar, solo enseñaría: lo dice la tarjeta de entrada. */
    public boolean wouldShow() {
        return wouldShow(last);
    }

    private static boolean wouldShow(Situation situation) {
        if (!situation.creative()) {
            return true;
        }
        ToolContext context = situation.context();
        return context.known() && !context.allows(Step.TREE.node()) && !context.allows(Step.STAMP.node())
                && !context.allows(Step.PAINT.node());
    }

    /** Los pasos que se hacen esta vez, en orden (sin los saltados por permiso). */
    public List<Step> steps() {
        Set<Step> steps = new LinkedHashSet<>();
        for (Beat each : plan) {
            if (!skipped.contains(each.step())) {
                steps.add(each.step());
            }
        }
        return List.copyOf(steps);
    }

    /** El número del paso de ahora (desde 1), o 0 fuera de los pasos. */
    public int stepNumber() {
        return running() ? steps().indexOf(beat.step()) + 1 : 0;
    }

    /** Una sola vez por golpe nuevo: la vista lo prepara (abre la cuadrícula, desplaza hasta lo que hay que tocar). */
    public boolean takeStarted() {
        boolean value = started;
        started = false;
        return value;
    }

    /** Las órdenes que la bienvenida pide mandar (aplicar o descartar la vista previa del pincel). */
    public List<String> drain() {
        List<String> commands = List.copyOf(outbox);
        outbox.clear();
        return commands;
    }

    /** Las zonas a las que señala el golpe de ahora, por orden; vacío para el mundo o una tarjeta. */
    public List<String> anchors() {
        if (beat == null) {
            return List.of();
        }
        if (beat == Beat.TREE_CLICK && last.pathPoints() > 0) {
            return List.of("footer:build");
        }
        return beat.anchors;
    }

    /** La frase del golpe de ahora, con su variante si la hay (con puntos de trayectoria, con vista previa…). */
    public String textKey() {
        if (beat == null) {
            return "";
        }
        if (beat == Beat.INTRO && wouldShow(last)) {
            return beat.textKey() + ".show";
        }
        if (beat == Beat.DONE && showing) {
            return beat.textKey() + ".show";
        }
        if (beat == Beat.TREE_CLICK && last.pathPoints() > 0) {
            return beat.textKey() + ".path";
        }
        if (beat == Beat.GHOST_PLACE && last.ghost() == null && last.previewWaiting()) {
            return beat.textKey() + ".preview";
        }
        if (beat == Beat.UNDO_DO && last.historyEntries() == 0) {
            return beat.textKey() + ".empty";
        }
        return beat.textKey();
    }

    /** Si la burbuja ofrece «Siguiente»: en los golpes de mirar y cuando no hay nada que deshacer. */
    public boolean offersNext() {
        return beat != null && (beat.kind() == Kind.LOOK || beat == Beat.UNDO_DO && last.historyEntries() == 0);
    }

    /** Si la burbuja ofrece «Aplicar» y «Descartar» (la vista previa del pincel ya está en el mundo). */
    public boolean offersApply() {
        return beat == Beat.STAMP_APPLY || beat == Beat.PAINT_APPLY;
    }

    // ── Entrar, avanzar y salir ─────────────────────────────────────────────────────────────────────────

    /** La tarjeta de entrada: la primera vez que se abre el Studio, desde la ayuda o con {@code /komaedit tutorial}. */
    public void offer() {
        discardPending();
        beat = Beat.INTRO;
        showing = false;
        plan = List.of();
        notice = null;
        skipped.clear();
        resume = null;
        started = true;
    }

    /** «Empezar»: decide si se hace de verdad o solo se enseña, con lo que se ve ahora, y va al primer paso. */
    public void start() {
        if (beat != Beat.INTRO) {
            return;
        }
        showing = wouldShow(last);
        plan = showing ? SHOWING : DOING;
        go(0);
    }

    /** «Saltar bienvenida», «Ahora no» o «Terminar»: se acaba (y se descarta la vista previa del pincel que dejó). */
    public void finish() {
        discardPending();
        beat = null;
        plan = List.of();
        notice = null;
        resume = null;
    }

    /** «Saltar paso»: al primer golpe del paso siguiente (sin árbol no hay nada que colocar: salta los dos). */
    public void skipStep() {
        if (!running()) {
            return;
        }
        discardPending();
        notice = null;
        go(after(beat.step() == Step.TREE ? Step.PLACE : beat.step()));
    }

    /** «Siguiente», en los golpes de mirar (o sin nada que deshacer). */
    public void next() {
        if (offersNext()) {
            advance();
        }
    }

    /** «Aplicar»: la vista previa del pincel se coloca (lo pide el jugador) y se sigue. */
    public void apply() {
        if (offersApply()) {
            outbox.add(APPLY);
            brushPreview = false;
            advance();
        }
    }

    /** «Descartar»: la vista previa se quita sin tocar nada y se vuelve a pedir el clic. */
    public void discard() {
        if (!offersApply()) {
            return;
        }
        outbox.add(DISCARD);
        brushPreview = false;
        Beat back = beat == Beat.STAMP_APPLY ? Beat.STAMP_CLICK : Beat.PAINT_CLICK;
        moveTo(back);
        notice = new Notice("gui.komaedit.welcome.notice.discarded", List.of());
    }

    // ── Lo que pasa ─────────────────────────────────────────────────────────────────────────────────────

    /** Lo que se ve en este fotograma; avanza (o vuelve atrás) según lo que ya está hecho. */
    public void observe(Situation situation) {
        last = situation == null ? Situation.START : situation;
        if (!running()) {
            return;
        }
        // Un mismo fotograma puede cerrar varios golpes seguidos (elegir una herramienta que ya estaba elegida).
        for (int guard = 0; guard < 8 && evaluate(last); guard++) {
            // Sigue mirando el siguiente.
        }
    }

    /** Una orden que el Studio manda (Construir, el pincel, Confirmar…). */
    public void sent(String command) {
        if (!running() || command == null) {
            return;
        }
        String text = command.strip();
        switch (beat) {
            case TREE_CLICK -> treeSent |= text.equals("komaedit tree") || text.startsWith("komaedit tree ");
            case STAMP_BRUSH -> {
                if (text.startsWith("komaedit pincel heightmap ")) {
                    advance();
                }
            }
            case PAINT_BRUSH -> {
                if (text.startsWith("komaedit pincel paint ")) {
                    advance();
                }
            }
            case GHOST_MOVE, GHOST_PLACE -> {
                if (text.equals(JobStrip.CONFIRM)) {
                    confirmSent = true;
                } else if (text.equals(JobStrip.CANCEL)) {
                    confirmSent = false;
                }
            }
            default -> {
            }
        }
    }

    /** Un clic (o una pasada) con el pincel en el mundo. */
    public void brushUsed() {
        if (beat == Beat.STAMP_CLICK || beat == Beat.STAMP_AGAIN || beat == Beat.PAINT_CLICK) {
            brushPreview = true;
            advance();
        }
    }

    /** Se deshizo algo en el mundo (el historial, Ctrl+Z). */
    public void undone() {
        if (beat == Beat.UNDO_OPEN || beat == Beat.UNDO_DO) {
            go(after(Step.UNDO));
        }
    }

    /**
     * Un clic en un control del Studio: {@code group} es el de su control y {@code anchor} la zona con nombre que hay
     * debajo (o {@code null}).
     */
    public void clicked(String group, String anchor) {
        if (beat == Beat.STAMP_CHOOSE && STAMP_GROUP.equals(group)) {
            advance();
        } else if ((beat == Beat.GHOST_MOVE || beat == Beat.GHOST_PLACE) && GHOST_PLACE_ANCHOR.equals(anchor)) {
            pressedPlace = true;
        }
    }

    /** Mira el golpe de ahora; devuelve si cambió de golpe. */
    private boolean evaluate(Situation situation) {
        Beat pick = pick(beat.step());
        if (pick != null && beat != pick && !situation.holding(beat.step().tool())) {
            // Se fue a otra herramienta: primero que vuelva; luego sigue donde iba.
            resume = beat;
            return moveTo(pick);
        }
        switch (beat) {
            case TREE_PICK, STAMP_PICK, PAINT_PICK -> {
                if (situation.holding(beat.step().tool())) {
                    if (resume != null) {
                        Beat back = resume;
                        resume = null;
                        return moveTo(back);
                    }
                    return advance();
                }
            }
            case TREE_CLICK -> {
                if (treeSent && (situation.ghost() != null || situation.previewWaiting())) {
                    return advance();
                }
            }
            case GHOST_MOVE -> {
                if (situation.ghost() != null) {
                    watchGhost(situation);
                    if (!placed && !situation.ghost().equals(ghostStart)) {
                        return advance();
                    }
                } else if (situation.previewWaiting()) {
                    // La vista previa de bloques no se mueve: solo queda confirmarla.
                    return advance();
                } else {
                    return gone();
                }
            }
            case GHOST_PLACE -> {
                if (situation.ghost() != null) {
                    watchGhost(situation);
                } else if (!situation.previewWaiting()) {
                    return gone();
                }
            }
            case STAMP_SEED -> {
                if (!Objects.equals(situation.stampSeed(), seedStart)) {
                    return advance();
                }
            }
            case PAINT_PRESET -> {
                if (situation.peaks()) {
                    return advance();
                }
            }
            case UNDO_OPEN -> {
                if (situation.historyOpen()) {
                    return advance();
                }
            }
            case UNDO_DO, UNDO_LOOK -> {
                if (!situation.historyOpen()) {
                    return moveTo(Beat.UNDO_OPEN);
                }
            }
            default -> {
            }
        }
        return false;
    }

    /**
     * Si el fantasma está colocándose: confirmado y esperando al servidor, o se acaba de pulsar Colocar. Si el servidor
     * no lo coloca (se sale del mundo, sin permiso), vuelve a no estarlo y quitarlo cuenta como cancelar.
     */
    private void watchGhost(Situation situation) {
        placed = situation.ghostWaiting() || pressedPlace;
        pressedPlace = false;
    }

    /** El fantasma (o la vista previa) ya no está: si se colocó, al paso siguiente; si no, a plantarlo otra vez. */
    private boolean gone() {
        if (placed || pressedPlace || confirmSent) {
            return go(after(Step.PLACE));
        }
        moveTo(Beat.TREE_CLICK);
        notice = new Notice("gui.komaedit.welcome.notice.cancelled", List.of());
        return true;
    }

    private static Beat pick(Step step) {
        if (step == null) {
            return null;
        }
        return switch (step) {
            case TREE -> Beat.TREE_PICK;
            case STAMP -> Beat.STAMP_PICK;
            case PAINT -> Beat.PAINT_PICK;
            default -> null;
        };
    }

    private boolean advance() {
        notice = null;
        return go(index + 1);
    }

    /** Al golpe {@code target} del plan (para volver atrás dentro del paso). */
    private boolean moveTo(Beat target) {
        int at = plan.indexOf(target);
        if (at < 0) {
            return false;
        }
        index = at;
        enter(target);
        return true;
    }

    /** El primer golpe del plan que es de un paso posterior a {@code step}. */
    private int after(Step step) {
        int at = index;
        while (at < plan.size() && plan.get(at).step().ordinal() <= step.ordinal()) {
            at++;
        }
        return at;
    }

    /**
     * Va al golpe {@code target} del plan. Al entrar en un paso sin su permiso (y haciéndolo de verdad), se salta
     * entero y la burbuja siguiente lo dice; al acabarse el plan, la tarjeta del final.
     */
    private boolean go(int target) {
        int at = target;
        List<String> missing = new ArrayList<>();
        while (at < plan.size() && !showing) {
            Step step = plan.get(at).step();
            boolean entering = at == 0 || plan.get(at - 1).step() != step;
            if (!entering || step.node().isEmpty() || last.context().allows(step.node())) {
                break;
            }
            skipped.add(step);
            String name = "gui.komaedit.tools." + step.tool();
            if (!missing.contains(name)) {
                missing.add(name);
            }
            index = at;
            at = after(step);
        }
        if (!missing.isEmpty()) {
            notice = new Notice("gui.komaedit.welcome.notice.skipped", missing);
        }
        if (at >= plan.size()) {
            index = plan.size();
            beat = Beat.DONE;
            started = true;
            return true;
        }
        index = at;
        enter(plan.get(at));
        return true;
    }

    private void enter(Beat next) {
        beat = next;
        started = true;
        switch (next) {
            case TREE_CLICK -> treeSent = false;
            case GHOST_MOVE -> {
                ghostStart = last.ghost();
                placed = false;
                pressedPlace = false;
                confirmSent = false;
            }
            case STAMP_SEED -> seedStart = last.stampSeed();
            default -> {
            }
        }
    }

    /** Al saltar o salir con una vista previa del pincel en el mundo, se descarta: no queda nada a medias. */
    private void discardPending() {
        if (brushPreview) {
            outbox.add(DISCARD);
            brushPreview = false;
        }
    }
}
