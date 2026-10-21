package com.komainu.moxia.hotbar;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.hotbar.HotbarPayloads.HotbarActionPayload;
import com.komainu.moxia.hotbar.HotbarPayloads.HotbarSyncPayload;
import com.komainu.moxia.hotbar.HotbarPayloads.HotbarTextPayload;
import com.komainu.moxia.performance.RequestBudget;
import com.komainu.moxia.permission.KomaPermissions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor de las barras guardadas: las de cada jugador (en memoria y en su proyecto), cambiar de barra según
 * {@link HotbarRules}, guardar sola la barra que se deja, deshacer el último cambio, importar y exportar, y mandar al
 * cliente lo que necesita para la tira, el menú y el Studio.
 *
 * <p>Todo ocurre en el hilo del servidor. Los errores se lanzan como {@link IllegalArgumentException} con el
 * mensaje para el jugador; quien llama (la orden o el paquete) decide cómo enseñarlo.</p>
 */
public final class HotbarService {
    /** Ticks mínimos entre dos cambios de barra del mismo jugador (cada cambio escribe su archivo). */
    static final int APPLY_GAP_TICKS = 2;
    private static final Map<UUID, HotbarBook> BOOKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Previous> UNDO = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_APPLY = new ConcurrentHashMap<>();
    /**
     * Lo demás que llega por paquete (guardar, borrar, importar, exportar, sincronizar…) escribe el archivo o manda
     * todas las barras: un cliente modificado no lo puede pedir sin parar.
     */
    private static final RequestBudget REQUESTS = new RequestBudget(8, 2.0);
    /** Entradas por atender en el próximo tick, cuando el proyecto del jugador ya está abierto. */
    private static final Set<UUID> PENDING_JOINS = ConcurrentHashMap.newKeySet();

    /** La barra de antes del último cambio en creativo y cuál estaba en uso. */
    private record Previous(List<ItemStack> slots, String active) {
    }

    /**
     * Lo que hizo un cambio de barra.
     *
     * @param moved   huecos que cambiaron (en supervivencia, objetos que se movieron)
     * @param missing objetos que pedía la barra y el jugador no lleva (supervivencia) o que no existen aquí
     * @param autosaved barra que se guardó sola al dejarla, o {@code null}
     */
    public record Applied(SavedHotbar hotbar, HotbarRules.Mode mode, int moved, List<String> missing,
                          String autosaved) {
    }

    private HotbarService() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(HotbarActionPayload.ID, HotbarActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HotbarSyncPayload.ID, HotbarSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HotbarTextPayload.ID, HotbarTextPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HotbarActionPayload.ID, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
        // Al tick siguiente: el proyecto del jugador se abre en otro oyente de entrada.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PENDING_JOINS.add(handler.player.getUuid()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(HotbarService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
    }

    public static void forget(UUID player) {
        BOOKS.remove(player);
        UNDO.remove(player);
        LAST_APPLY.remove(player);
        PENDING_JOINS.remove(player);
        REQUESTS.forget(player);
    }

    public static void clearAll() {
        BOOKS.clear();
        UNDO.clear();
        LAST_APPLY.clear();
        PENDING_JOINS.clear();
        REQUESTS.clear();
    }

    private static void tick(MinecraftServer server) {
        if (PENDING_JOINS.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(PENDING_JOINS)) {
            PENDING_JOINS.remove(id);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null && ServerPlayNetworking.canSend(player, HotbarSyncPayload.ID)) {
                try {
                    sync(player, true, List.of());
                } catch (RuntimeException exception) {
                    KomaEditMod.LOGGER.warn("Could not send KomaEdit hotbars to {}", id, exception);
                }
            }
        }
    }

    // ---- Paquetes del cliente ----------------------------------------------------------------------------------

    private static void handle(ServerPlayerEntity player, HotbarActionPayload payload) {
        if (payload.kind() != HotbarActionPayload.Kind.APPLY && !REQUESTS.allow(player.getUuid(), System.nanoTime())) {
            return;
        }
        try {
            switch (payload.kind()) {
                case SYNC -> sync(player, true, List.of());
                case APPLY -> {
                    Applied applied = apply(player, payload.name());
                    if (applied != null) {
                        Feedback.status(player, describe(applied));
                    }
                }
                case SAVE -> {
                    SavedHotbar saved = save(player, payload.name(), payload.text(), payload.value() != 0);
                    Feedback.ok(player, "Barra guardada: " + saved.name());
                }
                case DELETE -> {
                    delete(player, payload.name());
                    Feedback.ok(player, "Barra borrada: " + payload.name());
                }
                case EXPORT -> {
                    List<SavedHotbar> hotbars = exported(player, payload.name());
                    ServerPlayNetworking.send(player, new HotbarTextPayload(HotbarCodec.encode(hotbars),
                            hotbars.size()));
                }
                case IMPORT -> {
                    Imported imported = importCode(player, payload.text(), "", payload.value() != 0);
                    Feedback.ok(player, describe(imported));
                }
                case UNDO -> {
                    undo(player);
                    Feedback.status(player, "Barra de antes recuperada");
                }
                case AUTOSAVE -> {
                    autosave(player, payload.value() != 0);
                    Feedback.status(player, payload.value() != 0 ? "La barra que dejas se guarda sola"
                            : "La barra que dejas ya no se guarda sola");
                }
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            if (payload.kind() == HotbarActionPayload.Kind.APPLY) {
                // El cliente pudo poner la barra por su cuenta (creativo): se le devuelve la de verdad.
                player.playerScreenHandler.syncState();
            }
            Feedback.statusWarn(player, net.minecraft.text.Text.literal(exception.getMessage()));
        }
    }

    // ---- Barras del jugador ------------------------------------------------------------------------------------

    public static HotbarBook book(ServerPlayerEntity player) {
        HotbarBook cached = BOOKS.get(player.getUuid());
        if (cached != null) {
            return cached;
        }
        HotbarStore.Loaded loaded;
        try {
            loaded = HotbarStore.of(player.getUuid()).load();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Las barras guardadas no están disponibles: el proyecto de KomaEdit no "
                    + "está abierto en este mundo");
        }
        if (loaded.problem() != null) {
            KomaEditMod.LOGGER.warn("KomaEdit hotbars of {}: {}", player.getUuid(), loaded.problem());
            Feedback.warn(player, loaded.problem());
        }
        BOOKS.put(player.getUuid(), loaded.book());
        return loaded.book();
    }

    private static void store(ServerPlayerEntity player, HotbarBook book) {
        HotbarStore.of(player.getUuid()).save(book);
        BOOKS.put(player.getUuid(), book);
    }

    private static void requireUse(ServerPlayerEntity player) {
        if (!KomaPermissions.can(player, HotbarRules.PERMISSION)) {
            throw new IllegalArgumentException("Te falta el permiso " + HotbarRules.PERMISSION);
        }
    }

    /** Guarda la barra rápida actual con un nombre; {@code icon}: hueco 1–9, objeto o vacío. */
    public static SavedHotbar save(ServerPlayerEntity player, String name, String icon, boolean overwrite) {
        requireUse(player);
        List<HotbarItem> slots = HotbarStacks.hotbar(player.getInventory(), player.getRegistryManager());
        SavedHotbar hotbar = new SavedHotbar(name, icon == null || icon.isBlank() ? "" : SavedHotbar.icon(icon, slots),
                slots);
        if (hotbar.isEmpty()) {
            throw new IllegalArgumentException("Tu barra rápida está vacía: no hay nada que guardar");
        }
        HotbarBook book = book(player).saved(hotbar, overwrite);
        // Quien guarda su barra está usándola: el guardado al cambiar la seguirá desde aquí.
        store(player, book.withActive(hotbar.name()));
        sync(player, false, List.of(hotbar.name()));
        return hotbar;
    }

    /**
     * Guarda una barra hecha de otra cosa (una paleta, un degradado, la selección…), con cada objeto pasado por el
     * códec de Minecraft: queda escrita igual que si se hubiera guardado de la barra.
     */
    public static SavedHotbar create(ServerPlayerEntity player, String name, List<HotbarItem> slots, boolean overwrite) {
        requireUse(player);
        SavedHotbar hotbar = new SavedHotbar(name, "", normalized(player, slots).slots());
        if (hotbar.isEmpty()) {
            throw new IllegalArgumentException("Ninguno de esos bloques tiene objeto: la barra saldría vacía");
        }
        store(player, book(player).saved(hotbar, overwrite));
        sync(player, false, List.of(hotbar.name()));
        return hotbar;
    }

    public static void delete(ServerPlayerEntity player, String name) {
        requireUse(player);
        store(player, book(player).without(name));
        sync(player, false, List.of());
    }

    public static void rename(ServerPlayerEntity player, String from, String to) {
        requireUse(player);
        HotbarBook book = book(player).renamed(from, to);
        store(player, book);
        sync(player, false, List.of(book.require(to).name()));
    }

    public static SavedHotbar icon(ServerPlayerEntity player, String name, String input) {
        requireUse(player);
        HotbarBook book = book(player);
        SavedHotbar hotbar = book.require(name);
        SavedHotbar changed = hotbar.withIcon(SavedHotbar.icon(input, hotbar.slots()));
        store(player, book.updated(changed));
        sync(player, false, List.of(changed.name()));
        return changed;
    }

    public static void move(ServerPlayerEntity player, String name, int position) {
        requireUse(player);
        store(player, book(player).moved(name, position));
        sync(player, false, List.of());
    }

    public static void autosave(ServerPlayerEntity player, boolean on) {
        requireUse(player);
        store(player, book(player).withAutosave(on));
        sync(player, false, List.of());
    }

    /**
     * Cambia a la barra {@code name} según el modo de juego ({@link HotbarRules}).
     *
     * @return lo que hizo, o {@code null} si llegó demasiado seguido al anterior (se ignora)
     */
    public static Applied apply(ServerPlayerEntity player, String name) {
        HotbarBook book = book(player);
        SavedHotbar target = book.require(name);
        HotbarRules.Decision decision = HotbarRules.decide(KomaPermissions.can(player, HotbarRules.PERMISSION),
                player.isCreative(), player.isSpectator(), KomaPermissions.can(player, HotbarRules.GIVE_PERMISSION));
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.reason());
        }
        int now = player.getServer() == null ? 0 : player.getServer().getTicks();
        Integer last = LAST_APPLY.get(player.getUuid());
        if (last != null && now - last >= 0 && now - last < APPLY_GAP_TICKS) {
            player.playerScreenHandler.syncState();
            return null;
        }
        LAST_APPLY.put(player.getUuid(), now);
        PlayerInventory inventory = player.getInventory();
        RegistryWrapper.WrapperLookup registries = player.getRegistryManager();
        Set<String> changed = new LinkedHashSet<>();
        Applied applied;
        if (decision.sets()) {
            List<HotbarItem> current = HotbarStacks.hotbar(inventory, registries);
            List<ItemStack> previous = new ArrayList<>(SavedHotbar.SLOTS);
            for (int slot = 0; slot < SavedHotbar.SLOTS; slot++) {
                previous.add(inventory.getStack(slot).copy());
            }
            UNDO.put(player.getUuid(), new Previous(previous, book.active()));
            String autosaved = null;
            Optional<SavedHotbar> outgoing = book.activeHotbar();
            if (book.autosave() && outgoing.isPresent() && !outgoing.get().name().equals(target.name())
                    && !outgoing.get().slots().equals(current)) {
                try {
                    book = book.updated(outgoing.get().withSlots(current));
                    autosaved = outgoing.get().name();
                    changed.add(autosaved);
                } catch (IllegalArgumentException tooBig) {
                    Feedback.warn(player, "No se guardó la barra " + outgoing.get().name() + ": " + tooBig.getMessage());
                }
            }
            FeatureSet features = player.getWorld().getEnabledFeatures();
            int moved = 0;
            List<String> missing = new ArrayList<>();
            for (int slot = 0; slot < SavedHotbar.SLOTS; slot++) {
                HotbarItem item = target.slot(slot);
                Optional<ItemStack> stack = HotbarStacks.stack(item, registries, features);
                if (stack.isEmpty()) {
                    missing.add(item.shortId());
                }
                ItemStack next = stack.orElse(ItemStack.EMPTY);
                if (!ItemStack.areEqual(inventory.getStack(slot), next)) {
                    moved++;
                }
                inventory.setStack(slot, next);
            }
            applied = new Applied(target, decision.mode(), moved, missing, autosaved);
        } else {
            HotbarReorder.Result plan = HotbarReorder.plan(HotbarStacks.inventory(inventory, registries),
                    target.slots());
            for (HotbarReorder.Swap swap : plan.swaps()) {
                ItemStack slot = inventory.getStack(swap.slot());
                inventory.setStack(swap.slot(), inventory.getStack(swap.from()));
                inventory.setStack(swap.from(), slot);
            }
            List<String> missing = plan.missing().stream().map(slot -> target.slot(slot).shortId()).toList();
            applied = new Applied(target, decision.mode(), plan.swaps().size(), missing, null);
        }
        inventory.markDirty();
        player.currentScreenHandler.sendContentUpdates();
        if (player.currentScreenHandler != player.playerScreenHandler) {
            player.playerScreenHandler.sendContentUpdates();
        }
        store(player, book.withActive(target.name()));
        sync(player, false, changed);
        return applied;
    }

    /** Vuelve a la barra de antes del último cambio en creativo. */
    public static void undo(ServerPlayerEntity player) {
        requireUse(player);
        HotbarRules.Decision decision = HotbarRules.decide(true, player.isCreative(), player.isSpectator(),
                KomaPermissions.can(player, HotbarRules.GIVE_PERMISSION));
        if (!decision.sets()) {
            throw new IllegalArgumentException("Deshacer el cambio de barra solo se puede en creativo; en "
                    + "supervivencia vuelve a la barra de antes, que solo mueve lo que llevas");
        }
        Previous previous = UNDO.remove(player.getUuid());
        if (previous == null) {
            throw new IllegalArgumentException("No hay ninguna barra de antes que recuperar");
        }
        for (int slot = 0; slot < SavedHotbar.SLOTS; slot++) {
            player.getInventory().setStack(slot, previous.slots().get(slot));
        }
        player.currentScreenHandler.sendContentUpdates();
        HotbarBook book = book(player);
        store(player, book.withActive(book.get(previous.active()).isPresent() ? previous.active() : ""));
        sync(player, false, List.of());
    }

    /** Las barras que exporta {@code name}: esa o, vacío o {@code all}/{@code todas}, todas. */
    public static List<SavedHotbar> exported(ServerPlayerEntity player, String name) {
        requireUse(player);
        HotbarBook book = book(player);
        if (name == null || name.isBlank() || name.equalsIgnoreCase("all") || name.equalsIgnoreCase("todas")) {
            if (book.isEmpty()) {
                throw new IllegalArgumentException("No tienes barras guardadas que exportar");
            }
            return book.hotbars();
        }
        return List.of(book.require(name));
    }

    /**
     * Lo que se importó.
     *
     * @param names   las barras guardadas, con el nombre que les tocó
     * @param dropped objetos que no existen en este servidor (o con componentes que no se pueden leer) y se quitaron
     */
    public record Imported(List<String> names, int dropped) {
    }

    /**
     * Importa un código {@code kh1:}. Cada objeto pasa por el códec de Minecraft: lo que no existe aquí se quita y se
     * guarda como lo escribiría el servidor. Un nombre ocupado recibe otro ({@code _2}) salvo con {@code overwrite}.
     *
     * @param rename nombre para la barra si el código lleva una sola; vacío para el suyo
     */
    public static Imported importCode(ServerPlayerEntity player, String code, String rename, boolean overwrite) {
        requireUse(player);
        List<SavedHotbar> hotbars = HotbarCodec.decode(code);
        if (rename != null && !rename.isBlank() && hotbars.size() > 1) {
            throw new IllegalArgumentException("El código lleva " + hotbars.size() + " barras: no se le puede dar "
                    + "un solo nombre");
        }
        HotbarBook book = book(player);
        List<String> names = new ArrayList<>();
        int dropped = 0;
        for (SavedHotbar hotbar : hotbars) {
            Normalized normalized = normalized(player, hotbar.slots());
            List<HotbarItem> slots = normalized.slots();
            dropped += normalized.dropped();
            String wanted = rename != null && !rename.isBlank() ? rename : hotbar.name();
            String name = overwrite ? wanted : book.freeName(wanted);
            String icon = hotbar.icon();
            SavedHotbar imported = new SavedHotbar(name, slots.stream().anyMatch(item -> item.id().equals(icon))
                    ? icon : "", slots);
            book = book.saved(imported, overwrite);
            names.add(imported.name());
        }
        store(player, book);
        sync(player, false, names);
        return new Imported(List.copyOf(names), dropped);
    }

    /** Huecos como los escribe el servidor y cuántos objetos se quitaron por no existir aquí. */
    private record Normalized(List<HotbarItem> slots, int dropped) {
    }

    /** Cada objeto por el códec de Minecraft: lo que no existe aquí (o no se puede leer) sale vacío. */
    private static Normalized normalized(ServerPlayerEntity player, List<HotbarItem> slots) {
        RegistryWrapper.WrapperLookup registries = player.getRegistryManager();
        FeatureSet features = player.getWorld().getEnabledFeatures();
        List<HotbarItem> result = new ArrayList<>(slots.size());
        int dropped = 0;
        for (HotbarItem item : slots) {
            Optional<ItemStack> stack = HotbarStacks.stack(item, registries, features);
            if (stack.isEmpty()) {
                dropped++;
            }
            result.add(stack.map(value -> HotbarStacks.item(value, registries)).orElse(HotbarItem.EMPTY));
        }
        return new Normalized(result, dropped);
    }

    // ---- Al cliente --------------------------------------------------------------------------------------------

    /** Manda al cliente sus barras: todas ({@code full}) o las cambiadas, con el orden y la que está en uso. */
    public static void sync(ServerPlayerEntity player, boolean full, Collection<String> changed) {
        if (!ServerPlayNetworking.canSend(player, HotbarSyncPayload.ID)) {
            return;
        }
        HotbarBook book = book(player);
        RegistryWrapper.WrapperLookup registries = player.getRegistryManager();
        FeatureSet features = player.getWorld().getEnabledFeatures();
        List<HotbarSyncPayload.Entry> entries = new ArrayList<>();
        for (SavedHotbar hotbar : book.hotbars()) {
            if (!full && !changed.contains(hotbar.name())) {
                continue;
            }
            List<ItemStack> slots = new ArrayList<>(SavedHotbar.SLOTS);
            for (HotbarItem item : hotbar.slots()) {
                slots.add(HotbarStacks.stack(item, registries, features).orElse(ItemStack.EMPTY));
            }
            entries.add(new HotbarSyncPayload.Entry(hotbar.name(), hotbar.iconItem(), slots));
        }
        ServerPlayNetworking.send(player, new HotbarSyncPayload(full, book.active(), book.autosave(),
                KomaPermissions.can(player, HotbarRules.PERMISSION),
                KomaPermissions.can(player, HotbarRules.GIVE_PERMISSION), book.names(), entries));
    }

    // ---- Textos ------------------------------------------------------------------------------------------------

    public static String describe(Applied applied) {
        String name = applied.hotbar().name();
        StringBuilder text = new StringBuilder();
        if (applied.mode() == HotbarRules.Mode.SET || applied.mode() == HotbarRules.Mode.GIVE) {
            text.append("Barra ").append(name);
            if (applied.autosaved() != null) {
                text.append(" · ").append(applied.autosaved()).append(" guardada");
            }
            if (!applied.missing().isEmpty()) {
                text.append(" · no existen aquí: ").append(String.join(", ", applied.missing()));
            }
        } else {
            text.append("Barra ").append(name).append(" (supervivencia): ")
                    .append(Feedback.count(applied.moved(), "objeto movido", "objetos movidos"));
            if (!applied.missing().isEmpty()) {
                text.append(" · no llevas: ").append(String.join(", ", applied.missing()));
            }
        }
        return text.toString();
    }

    public static String describe(Imported imported) {
        String text = (imported.names().size() == 1 ? "Barra importada: " : "Barras importadas: ")
                + String.join(", ", imported.names());
        if (imported.dropped() > 0) {
            text += " · " + Feedback.count(imported.dropped(), "objeto no existe aquí y se quitó",
                    "objetos no existen aquí y se quitaron");
        }
        return text;
    }
}
