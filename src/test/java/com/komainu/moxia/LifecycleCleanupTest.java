package com.komainu.moxia;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lo que queda vivo cuando ya no tiene a quién servir. Los servicios guardan lo de cada jugador en mapas estáticos: lo
 * que no se olvida al irse se acumula mientras dure el servidor y, en un solo jugador, pasa al siguiente mundo que se
 * abra en el mismo proceso (los dos son {@code minecraft:overworld}, así que mirar la dimensión no lo para). Se sigue
 * el bytecode ({@link InitReach}) desde lo que se apunta a cada evento, también lo que se apunta la primera vez que se
 * usa algo, y se mira qué campos estáticos vacía.
 */
class LifecycleCleanupTest {
    private static final String DISCONNECT =
            "net/fabricmc/fabric/api/networking/v1/ServerPlayConnectionEvents$Disconnect";
    private static final String STOPPING =
            "net/fabricmc/fabric/api/event/lifecycle/v1/ServerLifecycleEvents$ServerStopping";
    private static final String STOPPED =
            "net/fabricmc/fabric/api/event/lifecycle/v1/ServerLifecycleEvents$ServerStopped";
    private static final String CHANGE_WORLD =
            "net/fabricmc/fabric/api/entity/event/v1/ServerEntityWorldChangeEvents$AfterPlayerChange";
    private static final String RESPAWN = "net/fabricmc/fabric/api/entity/event/v1/ServerPlayerEvents$AfterRespawn";
    /** {@code Map<UUID, Valor>} o {@code Set<UUID>}: lo de cada jugador. */
    private static final Pattern PER_PLAYER = Pattern.compile("^L[^<]+<Ljava/util/UUID;(?:L([^;<]+))?");
    /** Lo que pertenece a un servidor o a un mundo concretos. */
    private static final List<String> SERVER_TYPES = List.of("Lnet/minecraft/server/MinecraftServer;",
            "Lnet/minecraft/server/world/ServerWorld;", "Lnet/minecraft/world/World;",
            "Lnet/minecraft/server/network/ServerPlayerEntity;", "Lnet/minecraft/entity/",
            "Lcom/sk89q/worldedit/EditSession;", "Lcom/sk89q/worldedit/LocalSession;");

    /** Lo de cada jugador que se queda a propósito al irse, con el porqué. */
    private static final Map<String, String> KEPT_AFTER_LEAVING = Map.of(
            "rebuild/RebuildService.RECOVERIES", "la clave es un id al azar, no el jugador: lo que dejó una caída se"
                    + " termina aunque su dueño no esté",
            "command/Renamed.TOLD", "el aviso de una orden renombrada sale una vez por jugador y proceso; son unas"
                    + " pocas palabras");
    /** Lo de un servidor que sigue al pararse, con el porqué. */
    private static final Map<String, String> KEPT_AFTER_STOP = Map.of(
            "performance/ServerWork.LIGHT", "lo ligero que se apunta al iniciar el mod, para todos los servidores",
            "worldedit/EditHistory.LABELS", "un WeakHashMap: no retiene ninguna EditSession",
            "command/Renamed.TOLD", "ver KEPT_AFTER_LEAVING");
    /** Ediciones con su EditSession que siguen en su mundo aunque el jugador se vaya a otro, con el porqué. */
    private static final Map<String, String> BOUND_TO_THEIR_WORLD = Map.of(
            "edit/BatchEditService.JOBS", "la cola coloca en el mundo de la edición, esté donde esté el jugador",
            "biome/BiomeEditService.JOBS", "pinta los biomas del mundo de la edición",
            "rebuild/RebuildService.JOBS", "la animación lee y escribe en el mundo de la obra",
            "rebuild/RebuildService.RECOVERIES", "lo que dejó una caída, en el mundo de su archivo",
            "brush/paint/PaintStrokes.STROKES", "comprueba el mundo en cada tick (sameWorld) y cierra el trazo");

    private static InitReach reach;
    private static Map<String, Set<String>> cleared;

    @BeforeAll
    static void follow() {
        reach = InitOrderTest.reach();
        InitReach.Phase init = reach.follow(List.of(InitOrderTest.SERVER_INIT), null);
        cleared = Map.of(
                DISCONNECT, reach.lifecycle(reach.registrations(DISCONNECT), init).cleared(),
                STOPPING, reach.lifecycle(reach.registrations(STOPPING), init).cleared(),
                STOPPED, reach.lifecycle(reach.registrations(STOPPED), init).cleared(),
                CHANGE_WORLD, reach.lifecycle(reach.registrations(CHANGE_WORLD), init).cleared(),
                RESPAWN, reach.lifecycle(reach.registrations(RESPAWN), init).cleared());
    }

    @Test
    void whatIsKeptPerPlayerIsForgottenWhenThePlayerLeaves() {
        List<String> kept = new ArrayList<>();
        for (Field field : serverFields()) {
            if (field.perPlayer() && !cleared(field, DISCONNECT) && !KEPT_AFTER_LEAVING.containsKey(field.name())) {
                kept.add(field.name());
            }
        }
        assertEquals(List.of(), kept, "lo de cada jugador sigue ahí al desconectarse: olvídalo en"
                + " ServerPlayConnectionEvents.DISCONNECT (KomaEditMod o el register() del servicio)");
    }

    @Test
    void nothingOfAStoppedServerReachesTheNextWorld() {
        List<String> kept = new ArrayList<>();
        for (Field field : serverFields()) {
            boolean stopped = cleared(field, STOPPING) || cleared(field, STOPPED);
            // Al pararse, el servidor desconecta a todos: lo que se olvida al irse cada uno también se va.
            boolean forgotten = stopped || field.perPlayer() && cleared(field, DISCONNECT);
            if ((field.perPlayer() || field.server()) && !forgotten && !KEPT_AFTER_STOP.containsKey(field.name())) {
                kept.add(field.name());
            }
        }
        assertEquals(List.of(), kept, "sigue ahí al pararse el servidor y llega al siguiente mundo del proceso:"
                + " vacíalo en ServerLifecycleEvents.SERVER_STOPPED");
    }

    @Test
    void editsOpenOnAWorldEndWhenThePlayerLeavesIt() {
        List<String> open = new ArrayList<>();
        for (Field field : serverFields()) {
            if (field.holdsEditSession() && !BOUND_TO_THEIR_WORLD.containsKey(field.name())
                    && !(cleared(field, CHANGE_WORLD) && cleared(field, RESPAWN))) {
                open.add(field.name());
            }
        }
        assertEquals(List.of(), open, "una EditSession escribe en el mundo donde se abrió: si lo de este jugador"
                + " sigue con su mundo nuevo, lo cambia en el anterior. Ciérralo en AFTER_PLAYER_CHANGE_WORLD y"
                + " AFTER_RESPAWN");
    }

    private static boolean cleared(Field field, String event) {
        return cleared.get(event).contains(InitReach.OURS + field.name());
    }

    /** Un campo estático con estado del servidor ({@code paquete/Clase.campo}, sin el prefijo del mod). */
    private record Field(String name, boolean perPlayer, boolean server, boolean holdsEditSession) {
    }

    private static List<Field> serverFields() {
        List<Field> fields = new ArrayList<>();
        for (String name : reach.allClasses()) {
            if (name.startsWith(InitReach.OURS + "client/")) {
                continue;
            }
            ClassNode node = reach.node(name);
            for (FieldNode field : node.fields) {
                if ((field.access & Opcodes.ACC_STATIC) == 0 || field.name.startsWith("$")) {
                    continue;
                }
                String type = field.desc + (field.signature == null ? "" : field.signature);
                Matcher player = PER_PLAYER.matcher(field.signature == null ? "" : field.signature);
                boolean perPlayer = player.find();
                boolean server = SERVER_TYPES.stream().anyMatch(type::contains);
                boolean editSession = perPlayer && player.group(1) != null && holds(player.group(1),
                        "Lcom/sk89q/worldedit/EditSession;");
                if (perPlayer || server) {
                    fields.add(new Field(name.substring(InitReach.OURS.length()) + "." + field.name, perPlayer,
                            server, editSession));
                }
            }
        }
        return fields;
    }

    /** Si la clase (o sus superclases) tiene un campo de ese tipo. */
    private static boolean holds(String owner, String descriptor) {
        Set<String> seen = new HashSet<>();
        for (String current = owner; current != null && seen.add(current); ) {
            ClassNode node = reach.node(current);
            if (node == null) {
                return false;
            }
            for (FieldNode field : node.fields) {
                if ((field.access & Opcodes.ACC_STATIC) == 0 && field.desc.equals(descriptor)) {
                    return true;
                }
            }
            current = node.superName;
        }
        return false;
    }
}
