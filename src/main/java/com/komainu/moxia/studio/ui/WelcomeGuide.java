package com.komainu.moxia.studio.ui;

import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.WelcomeTour;
import com.komainu.moxia.studio.layout.Rect;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolPreset;
import com.komainu.moxia.studio.tools.ToolUse;
import com.komainu.moxia.studio.tools.ToolValues;
import com.komainu.moxia.studio.tools.ui.ToolsPanel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La bienvenida guiada ({@link WelcomeTour}) enganchada a un {@link StudioView}: le cuenta lo que se ve en cada
 * fotograma, prepara cada golpe (abre la cuadrícula de herramientas, desplaza hasta lo que hay que tocar), dibuja su
 * burbuja ({@link WelcomeBubble}) y, mientras se hace de verdad, sostiene la vista previa para que nada se coloque sin
 * confirmar. Sale sola la primera vez que se abre el Studio y queda apuntado en {@code studio.json}.
 */
final class WelcomeGuide {
    private static final String BRUSH_PREVIEW = "brush_preview";

    private final StudioView view;
    private final WelcomeTour tour = new WelcomeTour();
    /** Dónde quedó la burbuja en el último fotograma: lo que hay debajo no es el mundo. */
    private Rect bounds;
    /** Al empezar un golpe, el panel se desplaza una vez hasta lo que hay que tocar. */
    private boolean reveal;
    /** Lo que la bienvenida puso para que nada se coloque sin confirmar, con lo que había antes. */
    private boolean held;
    private StudioPreferences.PreviewMode preview;
    private final Map<String, Boolean> brushes = new LinkedHashMap<>();
    /** Cómo se usa cada herramienta de la bienvenida en sus pasos. */
    private static final Map<String, ToolUse> USES = Map.of(WelcomeTour.TREE, ToolUse.CLICK, WelcomeTour.STAMP,
            ToolUse.BRUSH, WelcomeTour.PAINT, ToolUse.BRUSH);
    /** La manera que tenía el jugador en las herramientas que la bienvenida cambió, para devolvérsela. */
    private final Map<String, ToolUse> uses = new LinkedHashMap<>();

    WelcomeGuide(StudioView view) {
        this.view = view;
    }

    WelcomeTour tour() {
        return tour;
    }

    /** Al cargar el Studio: la primera vez (o con una bienvenida nueva) sale sola, una sola vez por jugador. */
    void loaded(StudioHost host) {
        if (host.preferences().welcome() < WelcomeTour.VERSION) {
            // Queda apuntada aunque la cierre a medias: luego se lanza desde la ayuda o con la orden.
            host.preferences().setWelcome(WelcomeTour.VERSION);
            host.savePreferences();
            tour.offer();
        }
    }

    /** Si la burbuja tapa el punto (para que el pie y la leyenda no lo tomen por el mundo). */
    boolean covers(int x, int y) {
        return bounds != null && bounds.contains(x, y);
    }

    boolean visible() {
        return bounds != null;
    }

    /**
     * Mira lo que hay, prepara el golpe que empieza, manda lo que pide y, mientras se hace de verdad, sostiene la
     * vista previa. Va en cada tick y antes de cada dibujo.
     */
    void tick(StudioHost host) {
        if (!tour.active()) {
            release(host);
            return;
        }
        tour.observe(situation(host));
        if (tour.takeStarted()) {
            prepare(host);
        }
        send(host);
        if (tour.doing()) {
            hold(host);
        } else {
            release(host);
        }
    }

    /** Al cerrar el Studio: vuelve lo que la bienvenida puso; al abrirlo otra vez, se pone de nuevo. */
    void closed(StudioHost host) {
        release(host);
    }

    /** Un clic izquierdo del jugador en un control que no es de la burbuja. */
    void clicked(StudioUi.Widget widget, String anchor) {
        if (widget != null && !WelcomeBubble.GROUP.equals(widget.group())) {
            tour.clicked(widget.group(), anchor);
        }
    }

    private void send(StudioHost host) {
        for (String command : tour.drain()) {
            host.send(command);
        }
    }

    /** Lo que la bienvenida necesita ver del Studio en este fotograma. */
    WelcomeTour.Situation situation(StudioHost host) {
        ToolsPanel tools = view.tools();
        ToolCard paint = ToolCatalog.byId(WelcomeTour.PAINT);
        ToolPreset peaks = paint.presets().stream().filter(preset -> preset.id().equals(WelcomeTour.PEAKS))
                .findFirst().orElse(null);
        Optional<StudioHost.Ghost> ghost = host.ghost();
        boolean waiting = host.job().map(job -> job.phaseValue() == ProgressLine.Phase.WAITING).orElse(false);
        return new WelcomeTour.Situation(host.creative(), host.context(), view.stage() == StudioStage.TOOLS,
                tools.browsing() ? "" : tools.selected().id(),
                tools.values(ToolCatalog.byId(WelcomeTour.STAMP)).number("seed"),
                peaks != null && paint.matches(tools.values(paint), peaks),
                ghost.map(value -> value.position() + " · " + value.summary()).orElse(null),
                ghost.map(StudioHost.Ghost::waiting).orElse(false), waiting, view.historyOpen(),
                host.worldHistory().entries().size(), host.context().pathPoints());
    }

    /**
     * Lo que hace falta para el golpe que empieza: abrir Herramientas con su cuadrícula para elegir y, en compacto,
     * abrir «Más ajustes», donde van los presets. Luego se desplaza hasta lo que hay que tocar.
     */
    private void prepare(StudioHost host) {
        reveal = true;
        WelcomeTour.Beat beat = tour.beat();
        if (beat == null) {
            return;
        }
        ToolsPanel tools = view.tools();
        switch (beat) {
            case TREE_PICK, STAMP_PICK, PAINT_PICK -> {
                if (view.stage() != StudioStage.TOOLS) {
                    view.selectStage(StudioStage.TOOLS, host);
                }
                if (tools.browsing() || !tools.selected().id().equals(beat.step().tool())) {
                    tools.setBrowsing(true);
                }
            }
            case STAMP_SEED, PAINT_PRESET -> {
                if (view.frame() != null && view.frame().compact()) {
                    host.preferences().setExpanded(ToolsPanel.MORE_KEY + beat.step().tool(), true);
                }
            }
            default -> {
            }
        }
    }

    /** El nombre de la zona tal como la registra el panel (el sello elegido es el de ahora). */
    private String anchor(String id) {
        return id.equals(WelcomeTour.CHOSEN_STAMP)
                ? "stamp:" + view.tools().values(ToolCatalog.byId(WelcomeTour.STAMP)).choice("stamp") : id;
    }

    /**
     * Tras dibujar el contenido: desplaza el panel una vez para que se vea lo que la bienvenida señala dentro de él
     * (una herramienta, un sello, un preset).
     */
    void reveal(StudioUi ui) {
        if (!reveal) {
            return;
        }
        if (!tour.running()) {
            reveal = false;
            return;
        }
        for (String id : tour.anchors()) {
            String name = anchor(id);
            if (!name.startsWith("tool:") && !name.startsWith("stamp:") && !name.startsWith("preset:")) {
                reveal = false;
                return;
            }
            StudioUi.Anchor found = ui.anchor(name);
            if (found != null) {
                view.scrollIntoView(found.bounds());
                reveal = false;
                return;
            }
        }
    }

    /** Dibuja la burbuja (si no hay una ventana encima) en la capa de encima. */
    void draw(StudioUi ui, StudioHost host) {
        bounds = null;
        if (tour.active() && !view.modal()) {
            bounds = WelcomeBubble.draw(ui, view.frame(), host, tour, target(ui), host.now(), actions(host));
        }
    }

    /** Lo que se ve del control al que señala el golpe, o {@code null} si es el mundo o no está a la vista. */
    private Rect target(StudioUi ui) {
        for (String id : tour.anchors()) {
            StudioUi.Anchor found = ui.anchor(anchor(id));
            if (found != null && !found.visible().isEmpty()) {
                return found.visible();
            }
        }
        return null;
    }

    private WelcomeBubble.Actions actions(StudioHost host) {
        return new WelcomeBubble.Actions(
                tour::start,
                tour::next,
                () -> {
                    tour.apply();
                    send(host);
                },
                () -> {
                    tour.discard();
                    send(host);
                },
                () -> {
                    tour.skipStep();
                    send(host);
                },
                () -> {
                    boolean skipped = tour.beat() != WelcomeTour.Beat.DONE;
                    tour.finish();
                    send(host);
                    release(host);
                    if (skipped) {
                        host.status(host.tr("gui.komaedit.welcome.skipped"));
                    }
                });
    }

    /**
     * Mientras se hace de verdad, nada se coloca sin confirmar: Construir va con vista previa y los pinceles del sello
     * y de la pintura, con la vista previa de cada clic (que la burbuja aplica o descarta). Se pone una vez, al
     * empezar y al volver a abrir el Studio, así que lo que el jugador cambie entretanto se respeta.
     */
    private void hold(StudioHost host) {
        if (held) {
            return;
        }
        held = true;
        if (host.preferences().previewMode() == StudioPreferences.PreviewMode.OFF) {
            preview = StudioPreferences.PreviewMode.OFF;
            host.preferences().setPreviewMode(StudioPreferences.PreviewMode.LIVE);
        }
        ToolsPanel tools = view.tools();
        for (String id : List.of(WelcomeTour.STAMP, WelcomeTour.PAINT)) {
            ToolCard card = ToolCatalog.byId(id);
            ToolValues values = tools.values(card);
            if (!values.toggle(BRUSH_PREVIEW)) {
                brushes.put(id, false);
                tools.setValues(card, values.with(BRUSH_PREVIEW, true));
            }
        }
        // Cada paso enseña una manera de usar la herramienta: el árbol donde haces clic, el sello y la pintura con
        // el pincel. Si el jugador las tenía de otra manera, se vuelve a la suya al acabar.
        for (Map.Entry<String, ToolUse> wanted : USES.entrySet()) {
            ToolCard card = ToolCatalog.byId(wanted.getKey());
            ToolValues values = tools.values(card);
            if (card.use(values) != wanted.getValue()) {
                uses.put(wanted.getKey(), card.use(values));
                tools.setValues(card, values.with(ToolUse.PARAM, wanted.getValue().id()));
            }
        }
    }

    /**
     * Al acabar, saltarla o cerrar el Studio vuelve lo que había (si el jugador no lo cambió entretanto) y se guarda:
     * en {@code studio.json} no se queda lo que puso la bienvenida.
     */
    private void release(StudioHost host) {
        if (!held) {
            return;
        }
        held = false;
        if (preview != null && host.preferences().previewMode() == StudioPreferences.PreviewMode.LIVE) {
            host.preferences().setPreviewMode(preview);
        }
        preview = null;
        ToolsPanel tools = view.tools();
        for (String id : brushes.keySet()) {
            ToolCard card = ToolCatalog.byId(id);
            ToolValues values = tools.values(card);
            if (values.toggle(BRUSH_PREVIEW)) {
                tools.setValues(card, values.with(BRUSH_PREVIEW, false));
                host.preferences().setToolValues(id, tools.values(card).changed());
            }
        }
        brushes.clear();
        for (Map.Entry<String, ToolUse> previous : uses.entrySet()) {
            ToolCard card = ToolCatalog.byId(previous.getKey());
            ToolValues values = tools.values(card);
            if (card.use(values) == USES.get(previous.getKey())) {
                tools.setValues(card, values.with(ToolUse.PARAM, previous.getValue().id()));
                host.preferences().setToolValues(previous.getKey(), tools.values(card).changed());
            }
        }
        uses.clear();
        host.savePreferences();
    }
}
