package com.komainu.moxia.studio.net;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.performance.RequestBudget;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.SignedArgumentList;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Las órdenes de los botones del cliente en el servidor ({@link ButtonCommandPayload}). Se ejecutan como si el jugador
 * las escribiera: con su fuente, por el árbol de órdenes de siempre y con {@code CommandManager.execute}, así que
 * pasan los mismos permisos, las mismas cuotas, el mismo clic apuntado ({@link StudioAim}) y contestan los mismos
 * avisos y errores. Lo que no hacen es sumar al antispam del chat. A cambio:
 *
 * <ul>
 *     <li>Solo órdenes de KomaEdit, enteras y con caracteres de chat ({@link ButtonCommands}).</li>
 *     <li>Un freno por jugador ({@link RequestBudget}): {@value #BURST} seguidas y {@value #PER_SECOND} por segundo
 *     después, más de lo que da una mano; lo de más se descarta, avisando una vez.</li>
 * </ul>
 */
public final class ButtonCommandService {
    static final int BURST = 40;
    static final double PER_SECOND = 10.0;
    static final String TOO_FAST = "Vas demasiado deprisa con los botones: espera un momento";
    private static final RequestBudget REQUESTS = new RequestBudget(BURST, PER_SECOND);
    /** A quién ya se avisó de que iba demasiado deprisa (hasta que se le vuelve a atender). */
    private static final Set<UUID> SLOWED = ConcurrentHashMap.newKeySet();

    private ButtonCommandService() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ButtonCommandPayload.ID, ButtonCommandPayload.CODEC);
        // Ya en el hilo del servidor y en el orden en que llegó: detrás del clic apuntado que el cliente manda antes
        // (StudioAimPayload), como pasaba con la orden de chat.
        ServerPlayNetworking.registerGlobalReceiver(ButtonCommandPayload.ID, (payload, context) ->
                receive(context.player(), payload.command()));
    }

    /**
     * Qué se hace con lo que llega: ejecutarlo o no y, si no, qué se le dice al jugador ({@code null}: nada, ya se le
     * avisó de que iba deprisa).
     */
    public record Admission(boolean run, String message) {
        public static final Admission RUN = new Admission(true, null);
        public static final Admission QUIET = new Admission(false, null);
    }

    /** El freno va antes que mirar la orden: quien inunda con órdenes que no valen tampoco llena el chat de errores. */
    public static Admission admit(UUID player, String command, long nanos) {
        if (!REQUESTS.allow(player, nanos)) {
            return SLOWED.add(player) ? new Admission(false, TOO_FAST) : Admission.QUIET;
        }
        SLOWED.remove(player);
        Optional<String> problem = ButtonCommands.problem(command);
        return problem.map(text -> new Admission(false, "No se ejecuta: " + text)).orElse(Admission.RUN);
    }

    private static void receive(ServerPlayerEntity player, String command) {
        Admission admission = admit(player.getUuid(), command, System.nanoTime());
        if (!admission.run()) {
            if (admission.message() != null) {
                Feedback.warn(player, admission.message());
            }
            return;
        }
        // Como una orden de chat: el jugador está haciendo algo (no lo echa la espera por inactividad).
        player.updateLastActionTime();
        CommandManager commands = player.getServer().getCommandManager();
        run(commands.getDispatcher(), player.getCommandSource(), command, commands::execute);
    }

    /**
     * Lo que hace Minecraft con una orden escrita sin firma ({@code ServerPlayNetworkHandler.executeCommand}): la lee
     * con la fuente del jugador, con sus permisos, y la ejecuta con {@code execute} ({@code CommandManager.execute},
     * que contesta los errores igual que en el chat). Una orden con texto firmado (como {@code /msg}) no entra:
     * KomaEdit no tiene ninguna y sin firma no se le dejaría pasar.
     *
     * @return si se ejecutó
     */
    public static boolean run(CommandDispatcher<ServerCommandSource> dispatcher, ServerCommandSource source,
                              String command, BiConsumer<ParseResults<ServerCommandSource>, String> execute) {
        ParseResults<ServerCommandSource> parse = dispatcher.parse(command, source);
        if (SignedArgumentList.isNotEmpty(parse)) {
            source.sendError(Text.literal("Esa orden lleva texto firmado: escríbela en el chat"));
            return false;
        }
        execute.accept(parse, command);
        return true;
    }

    public static void forget(UUID player) {
        REQUESTS.forget(player);
        SLOWED.remove(player);
    }

    public static void clearAll() {
        REQUESTS.clear();
        SLOWED.clear();
    }
}
