package com.komainu.moxia.studio.ui;

import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.gizmo.GhostOptions;
import com.komainu.moxia.gizmo.GhostScope;
import com.komainu.moxia.gizmo.GhostSession;
import com.komainu.moxia.gizmo.GhostTransform;
import com.komainu.moxia.gizmo.MeshHandover;
import com.komainu.moxia.gizmo.StudioGhostLink;
import com.komainu.moxia.preview.PreviewHandover;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.studio.live.LiveAnchors;
import com.komainu.moxia.studio.live.LivePreviewRequestPayload;
import com.komainu.moxia.studio.live.LivePreviewResultPayload;
import com.komainu.moxia.studio.live.LiveQueue;
import com.komainu.moxia.studio.live.LiveRequests;
import com.komainu.moxia.studio.net.JobStatusPayload;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * La vista previa en vivo de punta a punta, sin Minecraft. El Studio es el de verdad ({@link StudioView} con
 * {@link FakeStudioHost}); lo que manda pasa por una red de prueba a un servidor de prueba que se porta como el de
 * verdad y usa sus piezas ({@link LiveQueue}, {@link LiveAnchors}, {@link LiveRequests}, {@link PreviewHandover}); lo
 * que contesta llega a un cliente de prueba con las piezas del cliente de verdad ({@link StudioGhostLink},
 * {@link GhostSession}, {@link MeshHandover}), y de ahí sale lo que se ve.
 *
 * <p>Lo de prueba es el cálculo (un plan es la orden sin su {@code -v}) y los tiempos: un tick son
 * {@value #STEP} ms, leer una selección tarda {@value #READ_MILLIS} ms, calcular {@value #COMPUTE_MILLIS} ms y
 * preparar lo que se enseña (la vista previa o el fantasma) {@value #PREPARE_MILLIS} ms más.
 * Del servidor de verdad copia lo que importa aquí: una orden de chat se ejecuta al llegar y suma al antispam; un
 * árbol o una figura nueva cancelan el cálculo de la anterior; mientras se lee una selección, otra operación sobre la
 * selección se rechaza («ya hay una operación leyendo tu selección»); el fantasma nuevo sustituye al que se sostenía;
 * la vista previa nueva descarta la de antes; el progreso se cuenta al cliente cada cinco ticks.</p>
 */
final class LiveLoop {
    static final long STEP = 50L;
    static final long COMPUTE_MILLIS = 100L;
    static final long READ_MILLIS = 100L;
    static final long PREPARE_MILLIS = 100L;
    /** Lo que suma cada orden de chat al antispam de Minecraft y a partir de cuánto echa a quien no es OP. */
    static final int SPAM_PER_COMMAND = 20;
    static final int SPAM_KICK = 200;
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000cafe");
    private static final Set<String> AIMED = Set.of("tree", "arbol", "rock", "roca", "text", "texto");

    final StudioDriver studio;
    final Server server = new Server();
    final Client client = new Client();
    private final Deque<Runnable> toClient = new ArrayDeque<>();
    /** Lo que tarda el servidor de prueba en calcular cada plan. */
    private final long compute;
    private boolean open = true;

    /** @param network si el servidor tiene la red de la vista previa en vivo (si no, como uno anterior: el chat) */
    LiveLoop(boolean network) {
        this(network, COMPUTE_MILLIS);
    }

    /** @param compute lo que tarda el servidor en calcular cada plan */
    LiveLoop(boolean network, long compute) {
        this.compute = compute;
        studio = new StudioDriver("es_es", 960, 900);
        FakeStudioHost host = studio.host;
        host.context = new ToolContext(true, ToolCatalog.permissionNodes(), true, 720L, 0, false);
        host.liveNetwork = network ? request -> {
            server.inbox.add(() -> server.receive(request));
            return true;
        } : null;
        host.onSend = command -> server.inbox.add(() -> server.chat(command));
        host.onRecipeBuild = recipe -> server.inbox.add(() -> server.recipe(recipe));
        host.onExpectGhost = refresh -> client.link.expect(refresh, host.now);
    }

    long now() {
        return studio.host.now;
    }

    /** Pasa el tiempo tick a tick: el servidor, la red, el cliente y el Studio dan cada uno su paso. */
    void run(long millis) {
        for (long passed = 0; passed < millis; passed += STEP) {
            step();
        }
    }

    void step() {
        studio.host.now += STEP;
        server.tick();
        while (!toClient.isEmpty()) {
            toClient.poll().run();
        }
        client.frame();
        client.publish();
        if (open) {
            studio.view.tick(studio.host);
        }
        client.record();
    }

    /** Se cierra el Studio (Esc): con él se olvida el clic en el mundo, en el cliente y en el servidor. */
    void closeStudio() {
        open = false;
        studio.view.closed(studio.host);
        server.inbox.add(() -> server.clicked = null);
    }

    void openStudio() {
        open = true;
    }

    /** El servidor de prueba, con las piezas de la vista previa en vivo del de verdad. */
    final class Server {
        final LiveQueue<LivePreviewRequestPayload> queue = new LiveQueue<>();
        final LiveAnchors<String> anchors = new LiveAnchors<>();
        final PreviewHandover handover = new PreviewHandover();
        private final Deque<Runnable> inbox = new ArrayDeque<>();
        /** El bloque del centro de la pantalla (a donde mira el jugador) y el del último clic del Studio. */
        String crosshair = "120,64,-40";
        String clicked;
        /** Lo que se llegó a calcular entero, en orden. */
        final List<String> computed = new ArrayList<>();
        final List<String> chatCommands = new ArrayList<>();
        /** Lo que el servidor rechazó (por el chat o por la red de la vista en vivo). */
        final List<String> refused = new ArrayList<>();
        int spam;
        int maxSpam;
        int maxWaiting;
        private Work work;
        private int ghost;
        private int preview;
        private boolean busyTold;
        private int ticks;
        private int ids;

        /**
         * Lo que se hace para el jugador: leer (selección), calcular hasta {@code calculated} y, ya con el plan
         * (donde la vista previa nueva descarta la de antes), prepararlo hasta {@code doneAt}.
         */
        private final class Work {
            private final String plan;
            private final boolean selection;
            private final String anchor;
            private final long readUntil;
            private final long calculated;
            private final long doneAt;
            private boolean intercepted;

            private Work(String plan, boolean selection, String anchor, long start) {
                this.plan = plan;
                this.selection = selection;
                this.anchor = anchor;
                readUntil = selection ? start + READ_MILLIS : start;
                calculated = readUntil + compute;
                doneAt = calculated + PREPARE_MILLIS;
            }

            String plan() {
                return plan;
            }

            boolean selection() {
                return selection;
            }

            String anchor() {
                return anchor;
            }

            long readUntil() {
                return readUntil;
            }
        }

        void receive(LivePreviewRequestPayload request) {
            if (LiveRequests.problem(request).isPresent()) {
                reply(LivePreviewResultPayload.of(request, LivePreviewResultPayload.Outcome.FAILED, 0, "no"));
                return;
            }
            queue.offer(PLAYER, request);
        }

        /** Una orden de chat: suma al antispam y se ejecuta al llegar, como en Minecraft. */
        void chat(String command) {
            chatCommands.add(command);
            spam += SPAM_PER_COMMAND;
            maxSpam = Math.max(maxSpam, spam);
            if (!execute(command)) {
                refused.add(command);
            }
        }

        void recipe(ProceduralRecipe recipe) {
            start(new Work(figurePlan(recipe), false, "0,64,0", now()));
        }

        boolean busy() {
            return work != null;
        }

        private void tick() {
            ticks++;
            spam = Math.max(0, spam - 1);
            while (!inbox.isEmpty()) {
                inbox.poll().run();
            }
            liveTick();
            if (work != null && !work.intercepted && now() >= work.calculated) {
                intercept(work);
            }
            if (work != null && now() >= work.doneAt) {
                finish(work);
            }
            maxWaiting = Math.max(maxWaiting, queue.pending(PLAYER) == null ? 0 : 1);
            if (ticks % 5 == 0) {
                progress();
            }
        }

        /** Lo que hace {@code LiveService} en cada tick. */
        private void liveTick() {
            long now = now();
            boolean busy = busy();
            LivePreviewRequestPayload settled = queue.settle(PLAYER, now, busy);
            if (settled != null) {
                clear(handover.ended(PLAYER));
                reply(LivePreviewResultPayload.of(settled, LivePreviewResultPayload.Outcome.NOTHING, 0, ""));
            }
            LivePreviewRequestPayload next = queue.next(PLAYER, now, busy);
            if (next == null) {
                return;
            }
            boolean done = anchors.run(PLAYER, next.session(), next.first(), () -> next.figure()
                    ? start(new Work(figurePlan(next.recipe()), false, "0,64,0", now))
                    : execute(next.command()));
            if (!done) {
                refused.add(next.figure() ? figurePlan(next.recipe()) : next.command());
                queue.failed(PLAYER);
                clear(handover.ended(PLAYER));
                reply(LivePreviewResultPayload.of(next, LivePreviewResultPayload.Outcome.FAILED, 0, "leyendo"));
            }
        }

        private boolean execute(String command) {
            String[] words = command.strip().toLowerCase(Locale.ROOT).split("\\s+");
            boolean movable = GhostScope.movable(command);
            String plan = command.replace(" --preview", "").replace(" -v", "");
            if (movable) {
                String anchor = AIMED.contains(words[1]) ? target() : "0,64,0";
                return start(new Work(plan, false, anchor, now()));
            }
            return start(new Work(plan, true, "", now()));
        }

        /** {@code StudioAim.target}: lo fijado por la vista previa en vivo; si no, el clic o el centro (y se apunta). */
        private String target() {
            String pinned = anchors.pinned(PLAYER);
            if (pinned != null) {
                return pinned;
            }
            String spot = clicked != null ? clicked : crosshair;
            anchors.captured(PLAYER, spot);
            return spot;
        }

        private boolean start(Work next) {
            if (next.selection() && work != null && work.selection() && now() < work.readUntil()) {
                return false;
            }
            // Un cálculo nuevo cancela el anterior (AsyncEngine, la captura del terreno).
            work = next;
            return true;
        }

        /**
         * El plan está calculado y llega a la vista previa: como {@code PlanPreviews.intercept}, la nueva descarta la
         * de antes (que el cliente quita en el acto, salvo que la sustituya la vista previa en vivo); un fantasma nuevo
         * sustituye al que se sostenía sin decirle nada al cliente.
         */
        private void intercept(Work done) {
            done.intercepted = true;
            computed.add(done.plan());
            boolean refreshing = queue.running(PLAYER) != null && !queue.running(PLAYER).first();
            if (done.selection() && preview != 0) {
                clear(handover.hidden(PLAYER, preview, refreshing));
                preview = 0;
            }
        }

        private void finish(Work done) {
            work = null;
            if (done.selection()) {
                preview = ++ids;
                LivePreviewRequestPayload request = queue.shown(PLAYER);
                if (request != null) {
                    reply(LivePreviewResultPayload.of(request, LivePreviewResultPayload.Outcome.PREVIEW, preview, ""));
                }
                handover.shown(PLAYER);
                int id = preview;
                String plan = done.plan();
                toClient.add(() -> client.previewPlan(id, plan));
                return;
            }
            ghost = ++ids;
            LivePreviewRequestPayload request = queue.shown(PLAYER);
            if (request != null) {
                // Como GhostService.show: la respuesta antes que el fantasma.
                reply(LivePreviewResultPayload.of(request, LivePreviewResultPayload.Outcome.GHOST, ghost, ""));
            }
            int id = ghost;
            String[] at = done.anchor().split(",");
            int x = Integer.parseInt(at[0]);
            int y = Integer.parseInt(at[1]) + 1;
            int z = Integer.parseInt(at[2]);
            String plan = done.plan();
            toClient.add(() -> client.open(id, x, y, z, plan));
        }

        private void progress() {
            JobStatusPayload status;
            if (work != null) {
                ProgressLine.Phase phase = now() < work.readUntil() ? ProgressLine.Phase.READING
                        : now() < work.calculated ? ProgressLine.Phase.CALCULATING : ProgressLine.Phase.PREPARING;
                status = JobStatusPayload.of(new ProgressLine(work.plan(), phase, 1, 2, 0, null), 0);
            } else if (preview != 0) {
                status = JobStatusPayload.of(new ProgressLine("vista previa", ProgressLine.Phase.WAITING, 80_000,
                        90_000, 0, null), 100);
            } else if (busyTold) {
                status = JobStatusPayload.IDLE;
            } else {
                return;
            }
            busyTold = status.active();
            JobStatusPayload sent = status;
            toClient.add(() -> client.job(sent));
        }

        private void reply(LivePreviewResultPayload result) {
            toClient.add(() -> client.result(result));
        }

        private void clear(int plan) {
            if (plan != 0) {
                toClient.add(() -> client.previewClear(plan));
            }
        }

        private String figurePlan(ProceduralRecipe recipe) {
            return "figura radio " + recipe.radius();
        }
    }

    /** Lo que se ve en un tick: la malla del fantasma y dónde, y la vista previa que espera a Confirmar. */
    record Seen(long at, String ghost, GhostTransform where, String preview) {
    }

    /** El cliente de prueba, con las piezas del de verdad. */
    final class Client {
        final StudioGhostLink link = new StudioGhostLink();
        final List<Seen> seen = new ArrayList<>();
        GhostSession session;
        private final MeshHandover<String> meshes = new MeshHandover<>(mesh -> {
        });
        private GhostTransform retiredAt;
        private String building;
        private int sceneId;
        private String scene;
        private int incomingId;
        private String incoming;
        private JobStatusPayload job = JobStatusPayload.IDLE;
        private long jobAt;

        void result(LivePreviewResultPayload result) {
            if (result.outcomeValue() == LivePreviewResultPayload.Outcome.GHOST) {
                link.claim(result.id(), result.refresh());
            }
            studio.host.liveResults.addLast(result);
        }

        /** Como {@code GhostClient.open}: llega un fantasma; su malla se arma en el fotograma siguiente. */
        void open(int id, int x, int y, int z, String plan) {
            GhostSession.Info info = new GhostSession.Info(id, plan, new BlockBounds(-3, 0, -3, 3, 12, 3), 300, 0,
                    GhostTransform.at(x, y, z), GhostOptions.DEFAULT);
            StudioGhostLink.Arrival arrival = link.arrive(session, info, orientation -> true, now());
            if (arrival.previous() != null) {
                meshes.retire(now());
                retiredAt = arrival.previous().transform();
            } else {
                meshes.clear();
            }
            session = arrival.session();
            building = plan;
        }

        /** El jugador mueve el fantasma con el gizmo. */
        void move(int dx, int dy, int dz) {
            session.nudge(dx, dy, dz, transform -> OptionalInt.empty());
        }

        void turn() {
            session.rotate(1, 1, orientation -> true, transform -> OptionalInt.empty());
        }

        /** Coloca el fantasma: se va, y con él lo del Studio. */
        void place() {
            session = null;
            meshes.clear();
            link.closed();
            server.ghost = 0;
        }

        void previewPlan(int id, String plan) {
            incomingId = id;
            incoming = plan;
        }

        void previewClear(int id) {
            if (id == sceneId) {
                scene = null;
                sceneId = 0;
            }
            if (id == incomingId) {
                incoming = null;
                incomingId = 0;
            }
        }

        void job(JobStatusPayload status) {
            job = status;
            jobAt = now();
        }

        private void frame() {
            if (building != null) {
                meshes.install(building);
                building = null;
            }
            if (incoming != null) {
                // Como PlanPreviewRenderer.build: la nueva sustituye a la que se ve al estar armada.
                scene = incoming;
                sceneId = incomingId;
                incoming = null;
                incomingId = 0;
            }
        }

        private void publish() {
            FakeStudioHost host = studio.host;
            host.ghost = session == null ? null : new StudioHost.Ghost(session.info().description(), "", "", false,
                    false, false, false, !session.active(), "", () -> { }, () -> { }, () -> { }, () -> { },
                    () -> { }, () -> { }, link.fromStudio());
            host.job = job.active() && now() - jobAt <= 3_000L ? job : null;
        }

        private void record() {
            String mesh = meshes.current();
            GhostTransform where = session == null ? null : session.transform();
            if (mesh == null) {
                mesh = meshes.previous(now());
                where = retiredAt;
            }
            seen.add(new Seen(now(), mesh, mesh == null ? null : where, scene));
        }

        Seen last() {
            return seen.isEmpty() ? new Seen(now(), null, null, null) : seen.getLast();
        }
    }
}
