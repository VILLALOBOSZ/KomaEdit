package com.komainu.moxia.share;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.blueprint.BlueprintNetwork;
import com.komainu.moxia.blueprint.BlueprintPermissions;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Property;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Exportar e importar paquetes en el juego: permisos, límites por jugador, el hilo donde se leen y escriben (un plano
 * grande tarda en descomprimirse y revisarse) y los mensajes del chat y del Studio. La lógica está en
 * {@link ShareExport}, {@link ShareCodec} y {@link ShareImport}.
 */
public final class ShareService {
    /** Lo que deja escribir el chat (256) menos {@code /ke share import }. */
    public static final int CHAT_CODE_CHARS = 256 - "/ke share import ".length();
    /** Lo que se espera a que el jugador acepte una importación. */
    private static final long OFFER_MILLIS = 10L * 60L * 1000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit paquetes");
        thread.setDaemon(true);
        return thread;
    });
    private static final ShareQuota QUOTA = new ShareQuota(() -> System.nanoTime() / 1_000_000L);
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static volatile MinecraftServer server;
    private static volatile List<String> blockIds;

    private ShareService() {
    }

    private record Pending(ShareImport plan, long expires) {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ShareCodePayload.ID, ShareCodePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShareOfferPayload.ID, ShareOfferPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShareImportPayload.ID, ShareImportPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ShareImportPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    try {
                        importCode(context.player(), payload.code());
                    } catch (IllegalArgumentException exception) {
                        Feedback.error(context.player(), exception.getMessage());
                    }
                }));
        ServerLifecycleEvents.SERVER_STARTING.register(starting -> server = starting);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            server = null;
            PENDING.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, ignored) ->
                PENDING.remove(handler.getPlayer().getUuid()));
    }

    /** El servidor en marcha, o {@code null}. */
    public static MinecraftServer server() {
        return server;
    }

    static ShareFiles files() {
        return new ShareFiles(KomaEditConfig.directory());
    }

    /**
     * El jugador con sus permisos ya mirados: lo que se hace en el hilo de los paquetes no pregunta a WorldEdit desde
     * otro hilo.
     */
    static ShareTarget target(ServerPlayerEntity player) {
        List<String> nodes = new ArrayList<>(List.of(SharePermissions.EXPORT, SharePermissions.IMPORT,
                SharePermissions.FILE, SharePermissions.UNLIMITED, BlueprintPermissions.UNLIMITED,
                BlueprintPermissions.LOAD));
        ShareKinds.all().forEach(kind -> nodes.add(kind.permission()));
        Set<String> granted = new HashSet<>();
        for (String node : nodes) {
            if (KomaPermissions.can(player, node)) {
                granted.add(node);
            }
        }
        return new ShareTarget(player.getUuid(), player.getGameProfile().getName(), granted::contains);
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer(KomaEditMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    // ------------------------------------------------------------------ Exportar

    /** {@code /komaedit share export …}: el resultado llega después por el chat (y al Studio). */
    public static void export(ServerPlayerEntity player, String arguments) {
        KomaPermissions.require(player, SharePermissions.EXPORT);
        ShareExport request = ShareExport.parse(arguments);
        ShareLimits limits = ShareConfig.limits();
        ShareTarget target = target(player);
        boolean canFile = limits.allowFiles() && target.can(SharePermissions.FILE);
        if (request.toFile() && !canFile) {
            throw new IllegalArgumentException(limits.allowFiles() ? "Te falta el permiso " + SharePermissions.FILE
                    : "En este servidor no se exporta a archivo");
        }
        if (!target.can(SharePermissions.UNLIMITED)) {
            QUOTA.takeExport(player.getUuid(), limits.exportsPerHour());
        }
        MinecraftServer current = player.getServer();
        UUID id = player.getUuid();
        String version = modVersion();
        long now = System.currentTimeMillis();
        IO.execute(() -> {
            try {
                SharePack pack = request.build(target, ShareKinds::find, version, now, limits);
                String code = request.toFile() ? "" : ShareCodec.toCode(pack);
                Path file = null;
                if (request.toFile() || code.length() > Math.min(limits.maxCodeChars(), ShareCodePayload.MAX_CODE)) {
                    if (!canFile) {
                        throw new IllegalArgumentException("El paquete no cabe en un código (" + code.length()
                                + " caracteres; máximo " + Math.min(limits.maxCodeChars(), ShareCodePayload.MAX_CODE)
                                + ") y no puedes exportar a archivo: comparte menos cosas a la vez");
                    }
                    file = files().writeExport(pack.name(), ShareCodec.toFile(pack), Instant.ofEpochMilli(now));
                    code = "";
                }
                String result = code;
                Path written = file;
                current.execute(() -> {
                    ServerPlayerEntity online = current.getPlayerManager().getPlayer(id);
                    if (online != null) {
                        exported(online, pack, result, written);
                    }
                });
            } catch (Exception exception) {
                report(current, id, "No se pudo exportar", exception);
            }
        });
        Feedback.status(player, "Preparando el paquete…");
    }

    private static void exported(ServerPlayerEntity player, SharePack pack, String code, Path file) {
        String summary = ShareExport.summary(pack.items(), ShareKinds::find);
        if (file != null) {
            String shown = "config/komaedit/exports/" + file.getFileName();
            Feedback.ok(player, Text.literal("Paquete «" + pack.name() + "» guardado: " + summary + " · ")
                    .append(copy(shown, shown)));
            Feedback.reply(player, Text.literal("En el otro servidor va en config/komaedit/imports y se importa con ")
                    .append(Feedback.fix("/ke share import archivo " + file.getFileName(),
                            "/ke share import archivo " + file.getFileName())).formatted(Formatting.GRAY));
        } else {
            String shown = code.length() <= 48 ? code : code.substring(0, 24) + "…" + code.substring(code.length() - 12);
            Feedback.ok(player, Text.literal("Paquete «" + pack.name() + "»: " + summary + " · "
                    + code.length() + " caracteres · ").append(copy("[Copiar código]", code))
                    .append(Text.literal(" " + shown).formatted(Formatting.DARK_GRAY)));
            Feedback.reply(player, Text.literal(code.length() <= CHAT_CODE_CHARS
                    ? "Cabe en el chat: en el otro servidor, /ke share import y pega el código"
                    : "Es largo para el chat: en el otro servidor, pégalo en el Studio (Planos → Pegar código) o "
                    + "guárdalo como .txt en config/komaedit/imports").formatted(Formatting.GRAY));
        }
        if (ServerPlayNetworking.canSend(player, ShareCodePayload.ID)) {
            ServerPlayNetworking.send(player, new ShareCodePayload(code, file == null ? ""
                    : "config/komaedit/exports/" + file.getFileName(), summary));
        }
    }

    private static MutableText copy(String label, String copied) {
        return Text.literal(label).styled(style -> style
                .withColor(Formatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copied))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Pulsa para copiar"))));
    }

    // ------------------------------------------------------------------ Importar

    /** {@code /komaedit share import <código>} o «Pegar código» del Studio. */
    public static void importCode(ServerPlayerEntity player, String code) {
        ShareLimits limits = ShareConfig.limits();
        prepare(player, () -> ShareCodec.fromCode(code, limits), limits);
    }

    /** {@code /komaedit share import archivo <nombre>}: un archivo de {@code config/komaedit/imports}. */
    public static void importFile(ServerPlayerEntity player, String name) {
        ShareLimits limits = ShareConfig.limits();
        ShareFiles files = files();
        prepare(player, () -> ShareCodec.fromFile(files.readImport(name, limits.maxFileBytes()), limits), limits);
    }

    /** Los archivos de {@code config/komaedit/imports}. */
    public static List<String> importable() {
        return files().importable();
    }

    private static void prepare(ServerPlayerEntity player, Callable<SharePack> source, ShareLimits limits) {
        KomaPermissions.require(player, SharePermissions.IMPORT);
        ShareTarget target = target(player);
        if (!target.can(SharePermissions.UNLIMITED)) {
            QUOTA.takeImport(player.getUuid(), limits.importsPerHour());
        }
        MinecraftServer current = player.getServer();
        BlockCatalog blocks = catalog(current);
        UUID id = player.getUuid();
        IO.execute(() -> {
            try {
                SharePack pack = source.call();
                ShareImport plan = ShareImport.prepare(pack, target, ShareKinds::find, new ShareCheck(blocks, limits));
                current.execute(() -> {
                    ServerPlayerEntity online = current.getPlayerManager().getPlayer(id);
                    if (online == null) {
                        return;
                    }
                    if (plan.importable()) {
                        PENDING.put(id, new Pending(plan, System.currentTimeMillis() + OFFER_MILLIS));
                    } else {
                        PENDING.remove(id);
                    }
                    offer(online, plan);
                });
            } catch (Exception exception) {
                report(current, id, "No se pudo leer el paquete", exception);
            }
        });
        Feedback.status(player, "Leyendo el paquete…");
    }

    private static void offer(ServerPlayerEntity player, ShareImport plan) {
        SharePack pack = plan.pack();
        MutableText head = Text.literal("Paquete «" + (pack.name().isEmpty() ? "sin nombre" : pack.name()) + "»");
        if (!pack.author().isEmpty()) {
            head.append(" de " + pack.author());
        }
        if (!pack.modVersion().isEmpty()) {
            head.append(Text.literal(" (KomaEdit " + pack.modVersion() + ")").formatted(Formatting.DARK_GRAY));
        }
        head.append(" · " + ShareExport.summary(pack.items(), ShareKinds::find));
        Feedback.reply(player, head);
        List<ShareOfferPayload.Line> lines = new ArrayList<>();
        for (ShareImport.Entry entry : plan.entries()) {
            Formatting color = switch (entry.status()) {
                case NEW -> Formatting.GREEN;
                case CONFLICT -> Formatting.YELLOW;
                default -> Formatting.RED;
            };
            String mark = switch (entry.status()) {
                case NEW -> "+ ";
                case CONFLICT -> "! ";
                case UNKNOWN -> "? ";
                default -> "× ";
            };
            MutableText line = Text.literal("  " + mark + entry.label()).formatted(color);
            if (!entry.problem().isEmpty()) {
                line.append(Text.literal(": " + entry.problem()).formatted(Formatting.GRAY));
            }
            player.sendMessage(line, false);
            for (String warning : entry.warnings()) {
                player.sendMessage(Text.literal("      " + warning).formatted(Formatting.DARK_GRAY), false);
            }
            String detail = entry.problem();
            if (!entry.warnings().isEmpty()) {
                detail = (detail.isEmpty() ? "" : detail + " · ") + String.join(" · ", entry.warnings());
            }
            lines.add(new ShareOfferPayload.Line(entry.status().ordinal(), clip(entry.label(), 128),
                    clip(detail, 512)));
        }
        long conflicts = plan.count(ShareImport.Status.CONFLICT);
        if (!plan.importable()) {
            Feedback.warn(player, "No hay nada que se pueda importar de este paquete");
        } else {
            MutableText buttons = Text.empty();
            if (conflicts == 0) {
                buttons.append(Feedback.button("[Importar]", "/komaedit share accept", Formatting.GREEN,
                        "Guarda todo lo que se puede importar"));
            } else {
                buttons.append(Feedback.button("[Importar con otro nombre]", "/komaedit share accept renombrar",
                        Formatting.GREEN, "Lo que choca se guarda como nombre_2"))
                        .append(" ")
                        .append(Feedback.button("[Reemplazar]", "/komaedit share accept reemplazar", Formatting.GOLD,
                                "Lo que choca sustituye a lo que tienes"))
                        .append(" ")
                        .append(Feedback.button("[Saltar]", "/komaedit share accept saltar", Formatting.YELLOW,
                                "Lo que choca se queda como está y no se importa"));
            }
            buttons.append(" ").append(Feedback.button("[Cancelar]", "/komaedit share cancel", Formatting.GRAY,
                    "No importa nada"));
            Feedback.reply(player, buttons);
        }
        if (ServerPlayNetworking.canSend(player, ShareOfferPayload.ID)) {
            ServerPlayNetworking.send(player, new ShareOfferPayload(clip(pack.name(), 64), clip(pack.author(), 64),
                    clip(pack.modVersion(), 64), (int) conflicts, lines));
        }
    }

    /** {@code /komaedit share accept [renombrar|reemplazar|saltar]}. */
    public static void accept(ServerPlayerEntity player, String policyName) {
        KomaPermissions.require(player, SharePermissions.IMPORT);
        ShareImport.Policy policy = policyName == null || policyName.isBlank() ? ShareImport.Policy.RENAME
                : ShareImport.Policy.byName(policyName);
        Pending pending = PENDING.remove(player.getUuid());
        if (pending == null || pending.expires() < System.currentTimeMillis()) {
            throw new IllegalArgumentException("No tienes ninguna importación pendiente: /ke share import <código>");
        }
        List<ShareImport.Outcome> outcomes = pending.plan().apply(target(player), policy);
        long saved = outcomes.stream().filter(ShareImport.Outcome::saved).count();
        Feedback.ok(player, "Importado: " + saved + " de " + outcomes.size());
        boolean blueprints = false;
        for (ShareImport.Outcome outcome : outcomes) {
            ShareImport.Entry entry = outcome.entry();
            String label = entry.label();
            if (outcome.saved() && !outcome.savedAs().equals(entry.name())) {
                label += " → «" + outcome.savedAs() + "»";
            }
            player.sendMessage(Text.literal("  " + (outcome.saved() ? "+ " : "· ") + label + ": " + outcome.message())
                    .formatted(outcome.saved() ? Formatting.GREEN : Formatting.GRAY), false);
            blueprints |= outcome.saved() && entry.kind() != null && entry.kind().id().equals("blueprint");
        }
        if (blueprints) {
            BlueprintNetwork.sendCatalog(player);
        }
    }

    /** {@code /komaedit share cancel}. */
    public static boolean cancel(ServerPlayerEntity player) {
        return PENDING.remove(player.getUuid()) != null;
    }

    // ------------------------------------------------------------------ Bloques del servidor

    /** Los bloques y biomas de este servidor, para comprobar lo que trae un paquete. */
    static BlockCatalog catalog(MinecraftServer current) {
        Registry<Biome> biomes = current.getRegistryManager().get(RegistryKeys.BIOME);
        return new BlockCatalog() {
            @Override
            public boolean has(String id) {
                Identifier identifier = Identifier.tryParse(id);
                return identifier != null && Registries.BLOCK.containsId(identifier);
            }

            @Override
            public Collection<String> ids() {
                List<String> ids = blockIds;
                if (ids == null) {
                    ids = Registries.BLOCK.getIds().stream().map(Identifier::toString).sorted().toList();
                    blockIds = ids;
                }
                return ids;
            }

            @Override
            public Map<String, List<String>> properties(String id) {
                Identifier identifier = Identifier.tryParse(id);
                if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
                    return Map.of();
                }
                Block block = Registries.BLOCK.get(identifier);
                Map<String, List<String>> result = new LinkedHashMap<>();
                for (Property<?> property : block.getStateManager().getProperties()) {
                    result.put(property.getName(), values(property));
                }
                return result;
            }

            @Override
            public boolean hasBiome(String id) {
                Identifier identifier = Identifier.tryParse(id);
                return identifier != null && biomes.containsId(identifier);
            }
        };
    }

    private static <T extends Comparable<T>> List<String> values(Property<T> property) {
        return property.getValues().stream().map(property::name).toList();
    }

    // ------------------------------------------------------------------ Mensajes

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static void report(MinecraftServer current, UUID id, String what, Exception exception) {
        String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (!(exception instanceof IllegalArgumentException)) {
            KomaEditMod.LOGGER.warn("KomaEdit share error: {}", detail, exception);
        }
        current.execute(() -> {
            ServerPlayerEntity online = current.getPlayerManager().getPlayer(id);
            if (online != null) {
                Feedback.error(online, Feedback.problem(what + ": " + detail, ""));
            }
        });
    }
}
