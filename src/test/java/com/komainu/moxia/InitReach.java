package com.komainu.moxia;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Lo que corre de verdad en una fase, leído del bytecode compilado: los métodos que se ejecutan desde las entradas, los
 * inicializadores estáticos de cada clase que eso carga (y lo que cargan ellos) y, en las llamadas por interfaz o
 * virtuales, solo las clases y lambdas que ya se crearon en la fase. Es aproximado por arriba: si dice que algo corre,
 * puede correr.
 *
 * <ul>
 *     <li><b>Arranque</b> ({@link #follow}): una lambda que se apunta en un evento no corre al apuntarse. Si el evento
 *     es de los que llegan antes de que WorldEdit tenga sus registros ({@link #EARLY}), su cuerpo es una entrada de la
 *     fase siguiente; los demás eventos se ignoran. Las lambdas que se pasan a métodos de Java (un {@code forEach}, un
 *     {@code computeIfAbsent}) corren en el acto, salvo en los de hilos y ejecutores; las que se pasan a métodos propios
 *     corren si algo de la fase llama a su método.</li>
 *     <li><b>Otros hilos</b> ({@link #submissions}, {@link #threads}): lo que se manda a un ejecutor, a un hilo o a
 *     {@code AsyncEngine.submit}, con el análisis de flujo de ASM para saber qué lambda llega a cada llamada. Ahí todas
 *     las clases propias pueden existir ya (se crearon en el hilo del servidor), pero las lambdas solo si se crean en
 *     el propio hilo, y no se siguen los inicializadores estáticos.</li>
 * </ul>
 */
final class InitReach {
    static final String OURS = "com/komainu/moxia/";
    /** Eventos que llegan antes de que WorldEdit llene sus registros: interfaz → método. */
    static final Map<String, String> EARLY = Map.of(
            "net/fabricmc/fabric/api/command/v2/CommandRegistrationCallback", "register",
            "net/fabricmc/fabric/api/event/lifecycle/v1/ServerLifecycleEvents$ServerStarting", "onServerStarting",
            "net/fabricmc/fabric/api/event/lifecycle/v1/ServerLifecycleEvents$ServerStarted", "onServerStarted",
            "net/fabricmc/fabric/api/event/lifecycle/v1/ServerWorldEvents$Load", "onWorldLoad",
            "net/fabricmc/fabric/api/event/lifecycle/v1/ServerEntityEvents$Load", "onLoad",
            "net/fabricmc/fabric/api/event/lifecycle/v1/ServerChunkEvents$Load", "onChunkLoad");
    /** Métodos de Java que guardan la lambda para otro hilo o para más tarde. */
    private static final Set<String> DEFERRING = Set.of(
            "java/lang/Thread", "java/lang/ThreadLocal", "java/lang/ref/Cleaner", "java/lang/Runtime", "java/util/Timer",
            "java/util/concurrent/Executor", "java/util/concurrent/ExecutorService",
            "java/util/concurrent/ScheduledExecutorService", "java/util/concurrent/Executors",
            "java/util/concurrent/CompletableFuture", "java/util/concurrent/ForkJoinPool",
            "java/util/concurrent/ThreadPoolExecutor", "java/util/concurrent/ScheduledThreadPoolExecutor",
            "java/util/concurrent/FutureTask");
    private static final Set<String> EXECUTORS = Set.of("java/util/concurrent/Executor",
            "java/util/concurrent/ExecutorService", "java/util/concurrent/ScheduledExecutorService",
            "java/util/concurrent/ThreadPoolExecutor", "java/util/concurrent/AbstractExecutorService",
            "java/util/concurrent/ScheduledThreadPoolExecutor");
    private static final Set<String> EXECUTOR_METHODS = Set.of("execute", "submit", "invokeAll", "invokeAny",
            "schedule", "scheduleAtFixedRate", "scheduleWithFixedDelay");
    private static final String ASYNC_ENGINE = OURS + "performance/AsyncEngine";
    /** Lo que pone una tarea en la cola del propio hilo. */
    private static final Set<String> HOPS = Set.of("net/minecraft/client/MinecraftClient",
            "net/minecraft/server/MinecraftServer", "net/minecraft/util/thread/ThreadExecutor",
            "net/minecraft/util/thread/ReentrantThreadExecutor");
    /** Lo que quita elementos de una colección o un mapa. */
    private static final Set<String> REMOVALS = Set.of("remove", "clear", "removeIf", "removeAll", "retainAll",
            "poll", "pollFirst", "pollLast", "removeFirst", "removeLast", "computeIfPresent", "compute");
    /** Vistas e iteradores: quitar de ellos quita del original. */
    private static final Set<String> VIEWS = Set.of("keySet", "values", "entrySet", "iterator", "listIterator",
            "descendingIterator", "long2ObjectEntrySet", "object2ObjectEntrySet");

    /** Algo de fuera que una fase no debe tocar. Devuelve qué es, o {@code null}. */
    @FunctionalInterface
    interface Sink {
        String check(int opcode, String owner, String name, String descriptor);
    }

    record MethodId(String owner, String name, String descriptor) {
        String label() {
            String simple = owner.substring(owner.lastIndexOf('/') + 1);
            if (name.startsWith("lambda$")) {
                String[] parts = name.split("\\$");
                return simple + "." + (parts.length > 1 ? parts[1] : name) + " (lambda"
                        + (parts.length > 2 ? " " + parts[2] : "") + ")";
            }
            return simple + "." + name;
        }
    }

    /** Lo encontrado y el camino desde la entrada. */
    record Finding(String what, String where, List<String> path) {
        @Override
        public String toString() {
            return what + " en " + where + (path.isEmpty() ? "" : " ← " + String.join(" ← ", path));
        }
    }

    private record Lambda(String type, String method, Handle implementation) {
    }

    private record VirtualCall(String owner, String name, String descriptor) {
    }

    private final List<Path> roots;
    private final Map<String, Optional<ClassNode>> classes = new HashMap<>();
    private final Map<String, Set<String>> supertypes = new HashMap<>();

    /** @param roots carpetas de clases compiladas, por si no están en el classpath (las del cliente) */
    InitReach(List<Path> roots) {
        this.roots = List.copyOf(roots);
    }

    /** Una fase: qué corre desde sus entradas. */
    final class Phase {
        private final boolean initializers;
        private final boolean hops;
        private final Map<MethodId, MethodId> reached = new LinkedHashMap<>();
        private final Map<String, MethodId> initialized = new LinkedHashMap<>();
        private final Set<String> instantiated = new LinkedHashSet<>();
        private final Map<String, List<String>> instancesOf = new HashMap<>();
        private final List<Lambda> lambdas = new ArrayList<>();
        private final Map<String, List<Lambda>> lambdasOf = new HashMap<>();
        private final Map<VirtualCall, MethodId> virtualCalls = new LinkedHashMap<>();
        private final Map<String, List<VirtualCall>> callsOn = new HashMap<>();
        private final Map<MethodId, MethodId> later = new LinkedHashMap<>();
        private final Deque<MethodId> work = new ArrayDeque<>();
        private final Map<MethodId, List<String[]>> external = new LinkedHashMap<>();
        private final Map<String, Set<MethodId>> statics = new LinkedHashMap<>();
        private final Map<String, Map<MethodId, MethodId>> created = new LinkedHashMap<>();

        private Phase(boolean initializers, boolean hops) {
            this.initializers = initializers;
            this.hops = hops;
        }

        /** Las entradas de los eventos tempranos que esta fase apuntó, con quién las apuntó. */
        Map<MethodId, MethodId> later() {
            return Map.copyOf(later);
        }

        Set<MethodId> methods() {
            return reached.keySet();
        }

        Set<String> initialized() {
            return initialized.keySet();
        }

        /** El cuerpo de cada lambda de ese tipo que se creó en la fase (las que se apuntan a un evento), con dónde. */
        Map<MethodId, MethodId> created(String type) {
            return created.getOrDefault(type, Map.of());
        }

        /**
         * Los campos estáticos propios que la fase vacía o cambia: les quita algo ({@code remove}, {@code clear},
         * {@code removeIf}…, también por sus vistas e iteradores) o les asigna otro valor.
         */
        Set<String> cleared() {
            Set<String> fields = new TreeSet<>();
            for (MethodId id : reached.keySet()) {
                ClassNode node = node(id.owner());
                MethodNode method = node == null ? null : find(node, id.name(), id.descriptor());
                if (method == null || method.instructions == null || method.instructions.size() == 0) {
                    continue;
                }
                Frame<SourceValue>[] frames = null;
                AbstractInsnNode[] instructions = method.instructions.toArray();
                for (int index = 0; index < instructions.length; index++) {
                    AbstractInsnNode instruction = instructions[index];
                    if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC
                            && field.owner.startsWith(OURS) && !id.name().equals("<clinit>")) {
                        fields.add(declaring(field.owner, field.name) + "." + field.name);
                    }
                    if (!(instruction instanceof MethodInsnNode call) || !REMOVALS.contains(call.name)
                            || call.getOpcode() == Opcodes.INVOKESTATIC) {
                        continue;
                    }
                    if (frames == null) {
                        frames = frames(id.owner(), method);
                        if (frames == null) {
                            break;
                        }
                    }
                    Frame<SourceValue> frame = frames[index];
                    if (frame == null) {
                        continue;
                    }
                    SourceValue receiver = frame.getStack(frame.getStackSize() - 1
                            - Type.getArgumentTypes(call.desc).length);
                    fields.addAll(staticSources(method, frames, receiver, new HashSet<>()));
                }
            }
            return fields;
        }

        /** Los campos estáticos propios que se leen o escriben en la fase ({@code Clase.campo}), con dónde. */
        Map<String, Set<MethodId>> statics() {
            return statics;
        }

        /** Cada uso de algo de fuera que el sumidero señala, con su camino. */
        List<Finding> findings(Sink sink) {
            Set<String> seen = new TreeSet<>();
            List<Finding> found = new ArrayList<>();
            external.forEach((method, uses) -> {
                for (String[] use : uses) {
                    String what = sink.check(Integer.parseInt(use[0]), use[1], use[2], use[3]);
                    if (what != null && seen.add(what + "@" + method)) {
                        found.add(new Finding(what, method.label(), path(method)));
                    }
                }
            });
            return found;
        }

        List<String> path(MethodId method) {
            List<String> path = new ArrayList<>();
            Set<MethodId> visited = new HashSet<>();
            MethodId current = reached.get(method);
            while (current != null && visited.add(current)) {
                path.add(current.label());
                current = reached.get(current);
            }
            return path;
        }

        private Phase seed(Phase before) {
            if (before != null) {
                before.instantiated.forEach(this::addInstance);
                before.lambdas.forEach(this::addLambda);
            }
            return this;
        }

        private Phase run(Map<MethodId, MethodId> entries) {
            entries.forEach((entry, from) -> {
                if (initializers) {
                    instantiate(entry.owner(), from);
                }
                enqueue(entry, from);
            });
            while (!work.isEmpty()) {
                process(work.poll());
            }
            return this;
        }

        private void enqueue(MethodId method, MethodId from) {
            if (method != null && !reached.containsKey(method)) {
                reached.put(method, from);
                work.add(method);
            }
        }

        private void initialize(String owner, MethodId from) {
            ClassNode node = node(owner);
            if (!initializers || node == null || initialized.containsKey(owner)) {
                return;
            }
            initialized.put(owner, from);
            if (node.superName != null) {
                initialize(node.superName, from);
            }
            if (find(node, "<clinit>", "()V") != null) {
                enqueue(new MethodId(owner, "<clinit>", "()V"), from);
            }
        }

        private void addInstance(String owner) {
            if (instantiated.add(owner)) {
                for (String type : supertypes(owner)) {
                    instancesOf.computeIfAbsent(type, ignored -> new ArrayList<>()).add(owner);
                }
            }
        }

        private void addLambda(Lambda lambda) {
            lambdas.add(lambda);
            for (String type : supertypes(lambda.type())) {
                lambdasOf.computeIfAbsent(type, ignored -> new ArrayList<>()).add(lambda);
            }
        }

        private void instantiate(String owner, MethodId from) {
            initialize(owner, from);
            if (node(owner) == null || instantiated.contains(owner)) {
                return;
            }
            addInstance(owner);
            for (String type : supertypes(owner)) {
                for (VirtualCall call : callsOn.getOrDefault(type, List.of())) {
                    enqueue(resolve(owner, call.name(), call.descriptor()), virtualCalls.get(call));
                }
                String method = EARLY.get(type);
                if (method != null && initializers) {
                    MethodId target = resolveByName(owner, method);
                    if (target != null) {
                        later.putIfAbsent(target, from);
                    }
                }
            }
        }

        private void process(MethodId id) {
            ClassNode node = node(id.owner());
            MethodNode method = node == null ? null : find(node, id.name(), id.descriptor());
            if (method == null || method.instructions == null) {
                return;
            }
            Map<AbstractInsnNode, List<AbstractInsnNode>> consumers = consumers(id.owner(), method);
            for (AbstractInsnNode instruction : method.instructions) {
                switch (instruction) {
                    case MethodInsnNode call -> call(id, call);
                    case FieldInsnNode field -> field(id, field);
                    case TypeInsnNode type when type.getOpcode() == Opcodes.NEW -> {
                        use(id, Opcodes.NEW, type.desc, "<new>", "");
                        instantiate(type.desc, id);
                    }
                    case InvokeDynamicInsnNode dynamic -> lambda(id, dynamic, consumers.get(dynamic));
                    default -> {
                    }
                }
            }
        }

        private void call(MethodId id, MethodInsnNode call) {
            use(id, call.getOpcode(), call.owner, call.name, call.desc);
            switch (call.getOpcode()) {
                case Opcodes.INVOKESTATIC -> {
                    initialize(call.owner, id);
                    enqueue(resolve(call.owner, call.name, call.desc), id);
                }
                case Opcodes.INVOKESPECIAL -> enqueue(resolve(call.owner, call.name, call.desc), id);
                default -> virtual(call.owner, call.name, call.desc, id);
            }
        }

        private void field(MethodId id, FieldInsnNode field) {
            use(id, field.getOpcode(), field.owner, field.name, field.desc);
            if ((field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC)
                    && field.owner.startsWith(OURS)) {
                statics.computeIfAbsent(declaring(field.owner, field.name) + "." + field.name,
                        ignored -> new LinkedHashSet<>()).add(id);
            }
            if (field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC) {
                initialize(declaring(field.owner, field.name), id);
            }
        }

        /**
         * Una lambda que se crea. Si solo se pasa a código de fuera que no es de Java (un evento, {@code server.execute})
         * se queda allí y no corre en esta fase; si se pasa a un método de Java que no la guarda, corre ya; si no (un
         * método propio, un campo), corre cuando algo de la fase llame a su método.
         */
        private void lambda(MethodId id, InvokeDynamicInsnNode dynamic, List<AbstractInsnNode> consumers) {
            if (!dynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                    || !(dynamic.bsmArgs[1] instanceof Handle implementation)) {
                return;
            }
            Lambda lambda = new Lambda(Type.getReturnType(dynamic.desc).getInternalName(), dynamic.name,
                    implementation);
            if (initializers) {
                created.computeIfAbsent(lambda.type(), ignored -> new LinkedHashMap<>()).putIfAbsent(
                        new MethodId(implementation.getOwner(), implementation.getName(), implementation.getDesc()), id);
            }
            if (initializers && EARLY.containsKey(lambda.type())) {
                later.putIfAbsent(new MethodId(implementation.getOwner(), implementation.getName(),
                        implementation.getDesc()), id);
                return;
            }
            List<AbstractInsnNode> uses = consumers == null ? List.of() : consumers;
            if (uses.stream().anyMatch(use -> use instanceof MethodInsnNode call && !call.owner.startsWith(OURS)
                    && (immediate(call.owner, call.name) || hops && HOPS.contains(call.owner)
                    && Set.of("execute", "send", "submit").contains(call.name)))) {
                implementation(implementation, id);
            }
            if (!uses.isEmpty() && uses.stream().allMatch(use -> use instanceof MethodInsnNode call
                    && !call.owner.startsWith(OURS))) {
                return;
            }
            addLambda(lambda);
            for (String type : supertypes(lambda.type())) {
                for (VirtualCall call : callsOn.getOrDefault(type, List.of())) {
                    if (call.name().equals(lambda.method())) {
                        implementation(implementation, virtualCalls.get(call));
                    }
                }
            }
        }

        private void virtual(String owner, String name, String descriptor, MethodId from) {
            VirtualCall call = new VirtualCall(owner, name, descriptor);
            if (virtualCalls.putIfAbsent(call, from) != null) {
                return;
            }
            callsOn.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(call);
            for (String type : List.copyOf(instancesOf.getOrDefault(owner, List.of()))) {
                enqueue(resolve(type, name, descriptor), from);
            }
            for (Lambda lambda : List.copyOf(lambdasOf.getOrDefault(owner, List.of()))) {
                if (lambda.method().equals(name)) {
                    implementation(lambda.implementation(), from);
                }
            }
        }

        private void implementation(Handle handle, MethodId from) {
            switch (handle.getTag()) {
                case Opcodes.H_INVOKESTATIC -> {
                    use(from, Opcodes.INVOKESTATIC, handle.getOwner(), handle.getName(), handle.getDesc());
                    initialize(handle.getOwner(), from);
                    enqueue(resolve(handle.getOwner(), handle.getName(), handle.getDesc()), from);
                }
                case Opcodes.H_NEWINVOKESPECIAL -> {
                    instantiate(handle.getOwner(), from);
                    enqueue(resolve(handle.getOwner(), handle.getName(), handle.getDesc()), from);
                }
                case Opcodes.H_INVOKESPECIAL -> enqueue(resolve(handle.getOwner(), handle.getName(),
                        handle.getDesc()), from);
                default -> {
                    use(from, Opcodes.INVOKEVIRTUAL, handle.getOwner(), handle.getName(), handle.getDesc());
                    virtual(handle.getOwner(), handle.getName(), handle.getDesc(), from);
                }
            }
        }

        private void use(MethodId where, int opcode, String owner, String name, String descriptor) {
            if (!owner.startsWith(OURS)) {
                external.computeIfAbsent(where, ignored -> new ArrayList<>())
                        .add(new String[]{Integer.toString(opcode), owner, name, descriptor});
            }
        }
    }

    /** Lo que corre desde {@code entries} (entrada → quién la apuntó); los objetos y lambdas de {@code before} existen. */
    Phase follow(Map<MethodId, MethodId> entries, Phase before) {
        return new Phase(true, false).seed(before).run(entries);
    }

    /**
     * Lo que corre al llegar un evento del ciclo de vida (desconexión, parada…): como {@link #follow}, pero lo que se
     * manda al propio hilo con {@code server.execute} o {@code client.execute} cuenta como hecho, porque corre enseguida.
     */
    Phase lifecycle(Map<MethodId, MethodId> entries, Phase before) {
        return new Phase(true, true).seed(before).run(entries);
    }

    Phase follow(List<MethodId> entries, Phase before) {
        Map<MethodId, MethodId> map = new LinkedHashMap<>();
        entries.forEach(entry -> map.put(entry, null));
        return follow(map, before);
    }

    /** Lo que corre en otros hilos desde {@code entries} ({@link #submissions}): cualquier clase propia puede existir. */
    Phase threads(Map<MethodId, MethodId> entries) {
        Phase phase = new Phase(false, false);
        allClasses().stream().filter(name -> {
            ClassNode node = node(name);
            return node != null && (node.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) == 0;
        }).forEach(phase::addInstance);
        return phase.run(entries);
    }

    /**
     * Lo que el código manda a otro hilo: el cuerpo de cada lambda (o el método de cada clase propia) que llega a un
     * ejecutor, a {@code CompletableFuture.*Async}, a un {@code Thread} o a {@code AsyncEngine.submit}, con el método
     * que lo manda.
     */
    Map<MethodId, MethodId> submissions() {
        return submissions(allClasses());
    }

    /** Lo mismo, solo en esas clases. */
    Map<MethodId, MethodId> submissions(Iterable<String> classes) {
        Map<MethodId, MethodId> entries = new LinkedHashMap<>();
        for (String name : classes) {
            ClassNode node = node(name);
            if (node == null || name.equals(ASYNC_ENGINE)) {
                continue;
            }
            for (MethodNode method : node.methods) {
                if (method.instructions == null || method.instructions.size() == 0 || !hasSubmission(method)) {
                    continue;
                }
                Frame<SourceValue>[] frames;
                try {
                    frames = new Analyzer<>(new SourceInterpreter()).analyze(name, method);
                } catch (AnalyzerException exception) {
                    throw new IllegalStateException("No se pudo analizar " + name + "." + method.name, exception);
                }
                MethodId from = new MethodId(name, method.name, method.desc);
                AbstractInsnNode[] instructions = method.instructions.toArray();
                for (int index = 0; index < instructions.length; index++) {
                    if (!(instructions[index] instanceof MethodInsnNode call) || !submits(call) || frames[index] == null) {
                        continue;
                    }
                    Frame<SourceValue> frame = frames[index];
                    Type[] arguments = Type.getArgumentTypes(call.desc);
                    for (int argument = 0; argument < arguments.length; argument++) {
                        if (arguments[argument].getSort() != Type.OBJECT) {
                            continue;
                        }
                        SourceValue value = frame.getStack(frame.getStackSize() - arguments.length + argument);
                        for (AbstractInsnNode producer : producers(method, frames, value, new HashSet<>())) {
                            for (MethodId entry : bodies(producer)) {
                                entries.putIfAbsent(entry, from);
                            }
                        }
                    }
                }
            }
        }
        return entries;
    }

    private static Frame<SourceValue>[] frames(String owner, MethodNode method) {
        try {
            return new Analyzer<>(new SourceInterpreter()).analyze(owner, method);
        } catch (AnalyzerException exception) {
            return null;
        }
    }

    /** Los campos estáticos propios de los que sale el valor, a través de variables, vistas e iteradores. */
    private Set<String> staticSources(MethodNode method, Frame<SourceValue>[] frames, SourceValue value,
                                      Set<AbstractInsnNode> visited) {
        Set<String> found = new TreeSet<>();
        for (AbstractInsnNode instruction : value.insns) {
            if (!visited.add(instruction)) {
                continue;
            }
            Frame<SourceValue> frame = frames[method.instructions.indexOf(instruction)];
            switch (instruction) {
                case FieldInsnNode field when field.getOpcode() == Opcodes.GETSTATIC && field.owner.startsWith(OURS) ->
                        found.add(declaring(field.owner, field.name) + "." + field.name);
                case MethodInsnNode call when VIEWS.contains(call.name) && call.getOpcode() != Opcodes.INVOKESTATIC
                        && frame != null -> found.addAll(staticSources(method, frames,
                        frame.getStack(frame.getStackSize() - 1 - Type.getArgumentTypes(call.desc).length), visited));
                case TypeInsnNode cast when cast.getOpcode() == Opcodes.CHECKCAST && frame != null ->
                        found.addAll(staticSources(method, frames, frame.getStack(frame.getStackSize() - 1), visited));
                case VarInsnNode load when load.getOpcode() == Opcodes.ALOAD && frame != null ->
                        found.addAll(staticSources(method, frames, frame.getLocal(load.var), visited));
                case VarInsnNode store when store.getOpcode() == Opcodes.ASTORE && frame != null ->
                        found.addAll(staticSources(method, frames, frame.getStack(frame.getStackSize() - 1), visited));
                default -> {
                }
            }
        }
        return found;
    }

    /** A qué instrucciones llega cada lambda que crea el método (llamadas, campos, {@code return}). */
    private static Map<AbstractInsnNode, List<AbstractInsnNode>> consumers(String owner, MethodNode method) {
        boolean lambdas = false;
        for (AbstractInsnNode instruction : method.instructions) {
            lambdas |= instruction instanceof InvokeDynamicInsnNode;
        }
        if (!lambdas) {
            return Map.of();
        }
        Frame<SourceValue>[] frames = frames(owner, method);
        if (frames == null) {
            return Map.of();
        }
        Map<AbstractInsnNode, List<AbstractInsnNode>> consumers = new HashMap<>();
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int index = 0; index < instructions.length; index++) {
            Frame<SourceValue> frame = frames[index];
            if (frame == null) {
                continue;
            }
            AbstractInsnNode instruction = instructions[index];
            int consumed = switch (instruction) {
                case MethodInsnNode call -> Type.getArgumentTypes(call.desc).length
                        + (call.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1);
                case InvokeDynamicInsnNode dynamic -> Type.getArgumentTypes(dynamic.desc).length;
                case FieldInsnNode field when field.getOpcode() == Opcodes.PUTFIELD
                        || field.getOpcode() == Opcodes.PUTSTATIC -> 1;
                default -> instruction.getOpcode() == Opcodes.ARETURN || instruction.getOpcode() == Opcodes.AASTORE
                        ? 1 : 0;
            };
            for (int slot = 0; slot < consumed && slot < frame.getStackSize(); slot++) {
                SourceValue value = frame.getStack(frame.getStackSize() - 1 - slot);
                for (AbstractInsnNode producer : producers(method, frames, value, new HashSet<>())) {
                    if (producer instanceof InvokeDynamicInsnNode) {
                        consumers.computeIfAbsent(producer, ignored -> new ArrayList<>()).add(instruction);
                    }
                }
            }
        }
        return consumers;
    }

    /**
     * El cuerpo de cada lambda de ese tipo en todo el código (también las que se apuntan a un evento la primera vez
     * que se usa algo), con el método que la crea.
     */
    Map<MethodId, MethodId> registrations(String type) {
        Map<MethodId, MethodId> entries = new LinkedHashMap<>();
        for (String name : allClasses()) {
            ClassNode node = node(name);
            for (MethodNode method : node.methods) {
                if (method.instructions == null) {
                    continue;
                }
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof InvokeDynamicInsnNode dynamic
                            && dynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                            && Type.getReturnType(dynamic.desc).getInternalName().equals(type)
                            && dynamic.bsmArgs[1] instanceof Handle handle) {
                        entries.putIfAbsent(new MethodId(handle.getOwner(), handle.getName(), handle.getDesc()),
                                new MethodId(name, method.name, method.desc));
                    }
                }
            }
        }
        return entries;
    }

    private static boolean hasSubmission(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && submits(call)) {
                return true;
            }
        }
        return false;
    }

    private static boolean submits(MethodInsnNode call) {
        if (call.owner.equals(ASYNC_ENGINE)) {
            return call.name.equals("submit");
        }
        if (EXECUTORS.contains(call.owner)) {
            return EXECUTOR_METHODS.contains(call.name);
        }
        if (call.owner.equals("java/util/concurrent/CompletableFuture")) {
            return call.name.endsWith("Async");
        }
        return call.owner.equals("java/lang/Thread") && call.name.equals("<init>");
    }

    /** Las instrucciones que crean el valor: lambdas y {@code new} de clases propias, a través de variables locales. */
    private static Set<AbstractInsnNode> producers(MethodNode method, Frame<SourceValue>[] frames, SourceValue value,
                                                   Set<AbstractInsnNode> visited) {
        Set<AbstractInsnNode> found = new LinkedHashSet<>();
        for (AbstractInsnNode instruction : value.insns) {
            if (!visited.add(instruction)) {
                continue;
            }
            int index = method.instructions.indexOf(instruction);
            Frame<SourceValue> frame = frames[index];
            switch (instruction) {
                case InvokeDynamicInsnNode dynamic -> found.add(dynamic);
                case TypeInsnNode type when type.getOpcode() == Opcodes.NEW -> found.add(type);
                case TypeInsnNode cast when cast.getOpcode() == Opcodes.CHECKCAST && frame != null ->
                        found.addAll(producers(method, frames, frame.getStack(frame.getStackSize() - 1), visited));
                case VarInsnNode load when load.getOpcode() == Opcodes.ALOAD && frame != null ->
                        found.addAll(producers(method, frames, frame.getLocal(load.var), visited));
                case VarInsnNode store when store.getOpcode() == Opcodes.ASTORE && frame != null ->
                        found.addAll(producers(method, frames, frame.getStack(frame.getStackSize() - 1), visited));
                default -> {
                }
            }
        }
        return found;
    }

    private List<MethodId> bodies(AbstractInsnNode producer) {
        if (producer instanceof InvokeDynamicInsnNode dynamic
                && dynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                && dynamic.bsmArgs[1] instanceof Handle handle && handle.getOwner().startsWith(OURS)) {
            MethodId resolved = resolve(handle.getOwner(), handle.getName(), handle.getDesc());
            return resolved == null ? List.of() : List.of(resolved);
        }
        if (producer instanceof TypeInsnNode type && node(type.desc) != null) {
            List<MethodId> bodies = new ArrayList<>();
            for (String name : List.of("run", "call", "get", "accept", "apply")) {
                MethodId method = resolveByName(type.desc, name);
                if (method != null) {
                    bodies.add(method);
                }
            }
            return bodies;
        }
        return List.of();
    }

    private static boolean immediate(String owner, String name) {
        if (owner.equals("net/minecraft/util/Util") && name.equals("make")) {
            return true;
        }
        return owner.startsWith("java/") && !DEFERRING.contains(owner);
    }

    /** Todas las clases propias de las carpetas. */
    Set<String> allClasses() {
        Set<String> names = new TreeSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(file -> file.toString().endsWith(".class")).forEach(file -> {
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    names.add(relative.substring(0, relative.length() - ".class".length()));
                });
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        names.removeIf(name -> !name.startsWith(OURS));
        return names;
    }

    ClassNode node(String internal) {
        if (!internal.startsWith(OURS)) {
            return null;
        }
        return classes.computeIfAbsent(internal, this::read).orElse(null);
    }

    private Optional<ClassNode> read(String internal) {
        try {
            byte[] bytes = null;
            for (int index = 0; bytes == null && index < roots.size(); index++) {
                Path file = roots.get(index).resolve(internal + ".class");
                if (Files.isRegularFile(file)) {
                    bytes = Files.readAllBytes(file);
                }
            }
            if (bytes == null) {
                try (InputStream input = InitReach.class.getClassLoader().getResourceAsStream(internal + ".class")) {
                    if (input != null) {
                        bytes = input.readAllBytes();
                    }
                }
            }
            if (bytes == null) {
                return Optional.empty();
            }
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return Optional.of(node);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static MethodNode find(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        return null;
    }

    /** La clase que declara el método, subiendo por las superclases y luego por los métodos por defecto. */
    private MethodId resolve(String owner, String name, String descriptor) {
        for (String current = owner; current != null; ) {
            ClassNode node = node(current);
            if (node == null) {
                break;
            }
            MethodNode method = find(node, name, descriptor);
            if (method != null && (method.access & Opcodes.ACC_ABSTRACT) == 0) {
                return new MethodId(current, name, descriptor);
            }
            current = node.superName;
        }
        for (String type : supertypes(owner)) {
            ClassNode node = node(type);
            if (node == null || (node.access & Opcodes.ACC_INTERFACE) == 0) {
                continue;
            }
            MethodNode method = find(node, name, descriptor);
            if (method != null && (method.access & Opcodes.ACC_ABSTRACT) == 0) {
                return new MethodId(type, name, descriptor);
            }
        }
        return null;
    }

    private MethodId resolveByName(String owner, String name) {
        for (String current = owner; current != null; ) {
            ClassNode node = node(current);
            if (node == null) {
                return null;
            }
            for (MethodNode method : node.methods) {
                if (method.name.equals(name) && (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_BRIDGE)) == 0) {
                    return new MethodId(current, name, method.desc);
                }
            }
            current = node.superName;
        }
        return null;
    }

    private String declaring(String owner, String field) {
        for (String current = owner; current != null; ) {
            ClassNode node = node(current);
            if (node == null) {
                return owner;
            }
            for (FieldNode candidate : node.fields) {
                if (candidate.name.equals(field)) {
                    return current;
                }
            }
            current = node.superName;
        }
        return owner;
    }

    /** El tipo, sus superclases y sus interfaces (las de fuera, solo por nombre). */
    private Set<String> supertypes(String type) {
        Set<String> cached = supertypes.get(type);
        if (cached != null) {
            return cached;
        }
        Set<String> all = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(List.of(type));
        while (!pending.isEmpty()) {
            String current = pending.poll();
            if (!all.add(current)) {
                continue;
            }
            ClassNode node = node(current);
            if (node != null) {
                if (node.superName != null) {
                    pending.add(node.superName);
                }
                pending.addAll(node.interfaces);
            }
        }
        supertypes.put(type, all);
        return all;
    }
}
