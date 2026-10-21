package com.komainu.moxia.display;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.feedback.FeedbackEventPayload;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.studio.net.StudioAim;
import com.komainu.moxia.worldedit.EditHistory;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EditSession;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * El editor de displays en el servidor. Todo lo decide aquí: el cliente solo elige con su rayo y propone cómo quedan
 * los displays al soltar el gizmo; el servidor comprueba permiso, distancia, zona donde se puede construir, escala,
 * límites por chunk y por jugador, y lo aplica todo o nada, con un paso en el historial de WorldEdit para
 * {@code //undo} ({@link DisplayChange}).
 *
 * <p>Permisos: {@link #PERMISSION} para crear y tocar los propios; {@link #OTHERS} para los de otros y los que no creó
 * KomaEdit; {@link #UNLIMITED} quita los límites de número.</p>
 */
public final class DisplayService {
    public static final String PERMISSION = KomaPermissions.BUILD + ".display";
    public static final String OTHERS = KomaPermissions.BUILD + ".display_others";
    public static final String UNLIMITED = "komaedit.limit.displays";
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final int SAVE_EVERY_TICKS = 600;
    private static final Map<UUID, Editor> EDITORS = new HashMap<>();
    private static DisplayLedger ledger = new DisplayLedger();
    private static Path ledgerFile;
    private static int ticks;

    private DisplayService() {
    }

    /** Lo que tiene cada jugador en el editor. */
    private static final class Editor {
        private final DisplaySelection selection = new DisplaySelection();
        private boolean editing;
        private long windowStart;
        private int windowCount;

        private boolean allow(long now, int perSecond) {
            if (now - windowStart > WINDOW_NANOS) {
                windowStart = now;
                windowCount = 0;
            }
            return ++windowCount <= perSecond;
        }
    }

    private static Editor editor(ServerPlayerEntity player) {
        return EDITORS.computeIfAbsent(player.getUuid(), ignored -> new Editor());
    }

    // ---------------------------------------------------------------- ciclo de vida

    public static void started(MinecraftServer server) {
        ledgerFile = server.getSavePath(WorldSavePath.ROOT).resolve("komaedit").resolve("displays.json");
        ledger = new DisplayLedger();
        try {
            if (Files.isRegularFile(ledgerFile)) {
                ledger = DisplayLedger.fromJson(Files.readString(ledgerFile, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not read the KomaEdit display ledger; starting a new one", exception);
        }
    }

    public static void tick(MinecraftServer server) {
        if (++ticks % SAVE_EVERY_TICKS == 0) {
            save();
        }
    }

    public static void stopped() {
        save();
        EDITORS.clear();
        ledgerFile = null;
        ledger = new DisplayLedger();
    }

    private static void save() {
        if (ledgerFile == null || !ledger.dirty()) {
            return;
        }
        try {
            Files.createDirectories(ledgerFile.getParent());
            AtomicStorage.writeString(ledgerFile, ledger.toJson());
            ledger.saved();
        } catch (IOException exception) {
            KomaEditMod.LOGGER.warn("Could not save the KomaEdit display ledger", exception);
        }
    }

    public static void forget(UUID player) {
        EDITORS.remove(player);
    }

    /** Al cambiar de dimensión: lo elegido era del otro mundo. */
    public static void changedWorld(ServerPlayerEntity player) {
        Editor editor = EDITORS.get(player.getUuid());
        if (editor != null) {
            editor.selection.clear();
            send(player, editor, DisplayPayloads.Outcome.INFO, "");
        }
    }

    public static boolean editing(ServerPlayerEntity player) {
        Editor editor = EDITORS.get(player.getUuid());
        return editor != null && editor.editing;
    }

    // ---------------------------------------------------------------- permisos y zonas

    public static boolean allowed(ServerPlayerEntity player) {
        return KomaPermissions.can(player, PERMISSION);
    }

    private static void require(ServerPlayerEntity player) {
        if (!allowed(player)) {
            throw new IllegalArgumentException("Te falta el permiso " + PERMISSION);
        }
    }

    /** Por qué no puede tocar este display, o vacío si puede. */
    static Optional<String> denial(ServerPlayerEntity player, DisplayEntity entity) {
        if (entity.isRemoved() || entity.getWorld() != player.getWorld()) {
            return Optional.of("ese display ya no está");
        }
        if (!DisplayTags.editable(entity.getCommandTags(), player.getUuid(), KomaPermissions.can(player, OTHERS))) {
            return Optional.of("ese display es de otro jugador o no lo creó KomaEdit (hace falta " + OTHERS + ")");
        }
        DisplayLimits limits = DisplayConfig.limits();
        Vec3 center = center(entity, DisplayEntities.pose(entity));
        Vec3 eyes = eyes(player);
        if (center.distance(eyes) > limits.maxDistance() && DisplayEntities.pose(entity).position().distance(eyes)
                > limits.maxDistance()) {
            return Optional.of("está a más de " + DisplayLimits.format(limits.maxDistance()) + " bloques");
        }
        if (!canBuild(player, entity.getWorld(), center) || !canBuild(player, entity.getWorld(), entity.getPos())) {
            return Optional.of("ahí no puedes construir");
        }
        return Optional.empty();
    }

    /** Si el jugador puede construir en ese punto (protección de spawn, borde, modo aventura, zonas). */
    static boolean canBuild(ServerPlayerEntity player, World world, Vec3d point) {
        BlockPos pos = BlockPos.ofFloored(point);
        return player.canModifyBlocks() && world.getWorldBorder().contains(pos) && world.canPlayerModifyAt(player, pos)
                && !player.isBlockBreakingRestricted(world, pos, player.interactionManager.getGameMode());
    }

    private static boolean canBuild(ServerPlayerEntity player, World world, Vec3 point) {
        return canBuild(player, world, new Vec3d(point.x(), point.y(), point.z()));
    }

    private static Vec3 eyes(ServerPlayerEntity player) {
        Vec3d eyes = player.getEyePos();
        return new Vec3(eyes.x, eyes.y, eyes.z);
    }

    static Vec3 center(DisplayEntity entity, DisplayPose pose) {
        return pose.folded().world(DisplayEntities.serverVolume(entity).center());
    }

    // ---------------------------------------------------------------- abrir y cerrar

    /** Abre o cierra el editor. Sin el mod en el cliente vale igual con las órdenes. */
    public static boolean setEditing(ServerPlayerEntity player, boolean on) {
        require(player);
        Editor editor = editor(player);
        editor.editing = on;
        if (!on) {
            editor.selection.clear();
        }
        send(player, editor, DisplayPayloads.Outcome.INFO, "");
        return on;
    }

    public static boolean hasMod(ServerPlayerEntity player) {
        return player.networkHandler != null && ServerPlayNetworking.canSend(player, DisplayPayloads.Selection.ID);
    }

    // ---------------------------------------------------------------- crear

    /** Lo que se crea: un bloque, el objeto de la mano con su modo o un texto. */
    public sealed interface Spec permits BlockSpec, ItemSpec, TextSpec {
        DisplayKind kind();
    }

    public record BlockSpec(BlockState state) implements Spec {
        public DisplayKind kind() {
            return DisplayKind.BLOCK;
        }
    }

    public record ItemSpec(ItemStack stack, ModelTransformationMode mode) implements Spec {
        public DisplayKind kind() {
            return DisplayKind.ITEM;
        }
    }

    public record TextSpec(DisplayText text) implements Spec {
        public DisplayKind kind() {
            return DisplayKind.TEXT;
        }
    }

    /**
     * Crea un display contra la cara del bloque mirado, lo elige y abre el editor.
     *
     * @return su descripción para el chat
     */
    public static String create(ServerPlayerEntity player, Spec spec) {
        require(player);
        DisplayLimits limits = DisplayConfig.limits();
        ServerWorld world = player.getServerWorld();
        HitResult hit = StudioAim.target(player, limits.maxDistance());
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            throw new IllegalArgumentException("Mira a un bloque (hasta " + DisplayLimits.format(limits.maxDistance())
                    + " bloques) para poner el display contra su cara");
        }
        if (spec instanceof TextSpec text) {
            limits.checkText(text.text().plain()).ifPresent(DisplayService::fail);
        }
        int face = face(block.getSide());
        BlockPos pos = block.getBlockPos();
        Vec3 point = onFace(block, face);
        DisplayEntity entity = build(world, spec);
        DisplayVolume volume = DisplayEntities.serverVolume(entity);
        DisplayPose pose = DisplayPlacement.place(spec.kind(), pos.getX(), pos.getY(), pos.getZ(), face, point,
                volume, player.getYaw());
        Vec3 center = pose.world(volume.center());
        limits.checkPose(pose, center, eyes(player)).ifPresent(DisplayService::fail);
        if (!canBuild(player, world, center) || !canBuild(player, world, pose.position())) {
            fail("ahí no puedes construir");
        }
        checkCounts(player, world, List.of(chunkKey(pose.position())));
        DisplayEntities.apply(entity, pose);
        entity.addCommandTag(DisplayTags.MARK);
        entity.addCommandTag(DisplayTags.owner(player.getUuid()));
        if (!world.spawnEntity(entity)) {
            fail("Minecraft no dejó crear el display");
        }
        remember(entity, player.getUuid());
        record(player, new DisplayHistory<NbtCompound>().created(entity.getUuid(), snapshot(entity)),
                "Crear display");
        Editor editor = editor(player);
        editor.editing = true;
        editor.selection.set(List.of(entity.getUuid()), entity.getUuid());
        String description = describe(entity);
        send(player, editor, DisplayPayloads.Outcome.APPLIED, description + " creado");
        Feedback.sound(player, FeedbackEventPayload.Kind.CONFIRM);
        return description;
    }

    private static DisplayEntity build(ServerWorld world, Spec spec) {
        return switch (spec) {
            case BlockSpec block -> {
                if (block.state().isAir()) {
                    fail("el aire no se puede poner en un display");
                }
                DisplayEntity.BlockDisplayEntity entity = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY,
                        world);
                entity.setBlockState(block.state());
                yield entity;
            }
            case ItemSpec item -> {
                if (item.stack().isEmpty()) {
                    fail("lleva en la mano el objeto que quieres poner");
                }
                DisplayEntity.ItemDisplayEntity entity = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY,
                        world);
                entity.setItemStack(item.stack().copyWithCount(1));
                entity.setTransformationMode(item.mode());
                yield entity;
            }
            case TextSpec text -> {
                DisplayEntity.TextDisplayEntity entity = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY,
                        world);
                entity.setText(text(text.text()));
                entity.setLineWidth(text.text().lineWidth());
                entity.setBackground(text.text().backgroundArgb());
                entity.setDisplayFlags(text.text().flags());
                yield entity;
            }
        };
    }

    /** Los trozos del texto con su formato, en un {@code Text} de Minecraft con el color de todo el texto. */
    static Text text(DisplayText text) {
        MutableText result = Text.empty().setStyle(Style.EMPTY.withColor(TextColor.fromRgb(text.color())));
        for (DisplayText.Span span : text.spans()) {
            Style style = Style.EMPTY.withBold(span.bold() ? true : null).withItalic(span.italic() ? true : null)
                    .withUnderline(span.underline() ? true : null).withStrikethrough(span.strike() ? true : null)
                    .withObfuscated(span.obfuscated() ? true : null);
            if (span.color() >= 0) {
                style = style.withColor(TextColor.fromRgb(span.color()));
            }
            result.append(Text.literal(span.text()).setStyle(style));
        }
        return result;
    }

    /** El punto de la cara mirada (si el clic vino del Studio, el centro de la cara). */
    private static Vec3 onFace(BlockHitResult hit, int face) {
        Vec3d point = hit.getPos();
        BlockPos pos = hit.getBlockPos();
        double x = point.x;
        double y = point.y;
        double z = point.z;
        switch (face) {
            case 0 -> x = pos.getX();
            case 1 -> x = pos.getX() + 1.0;
            case 2 -> y = pos.getY();
            case 3 -> y = pos.getY() + 1.0;
            case 4 -> z = pos.getZ();
            default -> z = pos.getZ() + 1.0;
        }
        return new Vec3(x, y, z);
    }

    static int face(net.minecraft.util.math.Direction side) {
        return switch (side) {
            case WEST -> 0;
            case EAST -> 1;
            case DOWN -> 2;
            case UP -> 3;
            case NORTH -> 4;
            case SOUTH -> 5;
        };
    }

    // ---------------------------------------------------------------- límites

    private static long chunkKey(Vec3 position) {
        return net.minecraft.util.math.ChunkPos.toLong(ChunkSectionPos.getSectionCoord(position.x()),
                ChunkSectionPos.getSectionCoord(position.z()));
    }

    /** Cuántos displays hay en un chunk (cargado), de quien sean. */
    static int displaysInChunk(ServerWorld world, long chunk, List<UUID> ignoring) {
        int chunkX = net.minecraft.util.math.ChunkPos.getPackedX(chunk);
        int chunkZ = net.minecraft.util.math.ChunkPos.getPackedZ(chunk);
        Box box = new Box(chunkX << 4, world.getBottomY() - 64, chunkZ << 4, (chunkX << 4) + 16, world.getTopY() + 64,
                (chunkZ << 4) + 16);
        return world.getEntitiesByClass(DisplayEntity.class, box, entity -> !ignoring.contains(entity.getUuid()))
                .size();
    }

    /** Comprueba los límites de crear un display en cada chunk de la lista (uno por display nuevo). */
    private static void checkCounts(ServerPlayerEntity player, ServerWorld world, List<Long> chunks) {
        if (KomaPermissions.can(player, UNLIMITED)) {
            return;
        }
        DisplayLimits limits = DisplayConfig.limits();
        Map<Long, Integer> adding = new LinkedHashMap<>();
        for (long chunk : chunks) {
            if (!world.isChunkLoaded(net.minecraft.util.math.ChunkPos.getPackedX(chunk),
                    net.minecraft.util.math.ChunkPos.getPackedZ(chunk))) {
                fail("esa zona no está cargada");
            }
            adding.merge(chunk, 1, Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : adding.entrySet()) {
            limits.checkChunk(displaysInChunk(world, entry.getKey(), List.of()), entry.getValue())
                    .ifPresent(DisplayService::fail);
        }
        ledger.prune(player.getUuid(), lookup(player.getServer()));
        limits.checkPlayer(ledger.count(player.getUuid()), chunks.size()).ifPresent(DisplayService::fail);
    }

    private static DisplayLedger.Lookup lookup(MinecraftServer server) {
        return new DisplayLedger.Lookup() {
            @Override
            public boolean loaded(String world, int chunkX, int chunkZ) {
                ServerWorld found = world(server, world);
                return found != null && found.isChunkLoaded(chunkX, chunkZ);
            }

            @Override
            public boolean exists(String world, UUID display) {
                ServerWorld found = world(server, world);
                return found != null && found.getEntity(display) instanceof DisplayEntity;
            }
        };
    }

    private static ServerWorld world(MinecraftServer server, String id) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().getValue().toString().equals(id)) {
                return world;
            }
        }
        return null;
    }

    private static void remember(DisplayEntity entity, UUID owner) {
        ledger.add(entity.getUuid(), owner, entity.getWorld().getRegistryKey().getValue().toString(),
                entity.getChunkPos().x, entity.getChunkPos().z);
    }

    private static void track(DisplayEntity entity) {
        ledger.moved(entity.getUuid(), entity.getWorld().getRegistryKey().getValue().toString(),
                entity.getChunkPos().x, entity.getChunkPos().z);
    }

    // ---------------------------------------------------------------- elegir

    /** El cliente pulsó un display (o nada, para soltar todo). */
    public static void pick(ServerPlayerEntity player, DisplayPayloads.Pick payload) {
        if (!allowed(player)) {
            return;
        }
        Editor editor = editor(player);
        if (payload.entityId() < 0) {
            editor.selection.clear();
            send(player, editor, DisplayPayloads.Outcome.INFO, "");
            return;
        }
        Entity found = player.getServerWorld().getEntityById(payload.entityId());
        if (!(found instanceof DisplayEntity entity)) {
            send(player, editor, DisplayPayloads.Outcome.REJECTED, "Ese display ya no está");
            return;
        }
        select(player, editor, entity, payload.mode(), payload.single());
    }

    /** {@code /komaedit display select}: el display que mira el jugador, con el mismo rayo que el cliente. */
    public static String selectLooking(ServerPlayerEntity player, DisplaySelection.Mode mode) {
        require(player);
        Editor editor = editor(player);
        DisplayLimits limits = DisplayConfig.limits();
        Vec3 eyes = eyes(player);
        Vec3d look = player.getRotationVec(1.0F);
        com.komainu.moxia.gizmo.GizmoMath.Ray ray = new com.komainu.moxia.gizmo.GizmoMath.Ray(eyes,
                new Vec3(look.x, look.y, look.z));
        List<DisplayPick.Candidate> candidates = new ArrayList<>();
        double reach = limits.maxDistance();
        Box around = player.getBoundingBox().expand(reach);
        for (DisplayEntity entity : player.getServerWorld().getEntitiesByClass(DisplayEntity.class, around,
                entity -> true)) {
            DisplayPose pose = DisplayEntities.pose(entity);
            candidates.add(new DisplayPick.Candidate(entity.getId(),
                    DisplayEntities.serverVolume(entity).world(pose, player.getYaw(), player.getPitch())));
        }
        HitResult blocks = player.raycast(reach, 1.0F, false);
        double wall = blocks.getType() == HitResult.Type.MISS ? reach : blocks.getPos().distanceTo(player.getEyePos());
        Optional<DisplayPick.Hit> hit = DisplayPick.pick(ray, candidates, Math.min(reach, wall + 0.01));
        if (hit.isEmpty() || !(player.getServerWorld().getEntityById(hit.get().id()) instanceof DisplayEntity entity)) {
            throw new IllegalArgumentException("No miras a ningún display (hasta " + DisplayLimits.format(reach)
                    + " bloques)");
        }
        editor.editing = true;
        select(player, editor, entity, mode, false);
        return describe(entity);
    }

    private static void select(ServerPlayerEntity player, Editor editor, DisplayEntity entity,
                               DisplaySelection.Mode mode, boolean single) {
        Optional<String> denied = denial(player, entity);
        if (denied.isPresent()) {
            send(player, editor, DisplayPayloads.Outcome.REJECTED, capitalized(denied.get()));
            return;
        }
        List<UUID> members = single ? List.of(entity.getUuid()) : group(player, entity);
        List<UUID> before = editor.selection.ids();
        UUID previous = editor.selection.primary();
        editor.selection.pick(entity.getUuid(), members, mode);
        Optional<String> tooMany = DisplayConfig.limits().checkSelection(editor.selection.size());
        if (tooMany.isPresent()) {
            editor.selection.set(before, previous);
            send(player, editor, DisplayPayloads.Outcome.REJECTED, capitalized(tooMany.get()));
            return;
        }
        int grouped = members.size();
        String message = grouped > 1 ? describe(entity) + " · grupo de " + grouped : describe(entity);
        send(player, editor, DisplayPayloads.Outcome.INFO, editor.selection.contains(entity.getUuid()) ? message
                : "");
    }

    /** Los del grupo del display que se pueden tocar (él incluido), cerca del jugador. */
    private static List<UUID> group(ServerPlayerEntity player, DisplayEntity entity) {
        Optional<String> group = DisplayTags.groupOf(entity.getCommandTags());
        if (group.isEmpty()) {
            return List.of(entity.getUuid());
        }
        String tag = DisplayTags.group(group.get());
        double reach = DisplayConfig.limits().maxDistance() * 2.0;
        List<UUID> members = new ArrayList<>();
        for (DisplayEntity member : player.getServerWorld().getEntitiesByClass(DisplayEntity.class,
                entity.getBoundingBox().expand(reach), other -> other.getCommandTags().contains(tag))) {
            if (denial(player, member).isEmpty()) {
                members.add(member.getUuid());
            }
        }
        if (!members.contains(entity.getUuid())) {
            members.add(entity.getUuid());
        }
        return members;
    }

    /** Lo elegido que sigue existiendo y se puede tocar, en orden; lo demás sale de la selección. */
    private static List<DisplayEntity> selected(ServerPlayerEntity player, Editor editor) {
        List<DisplayEntity> entities = new ArrayList<>();
        ServerWorld world = player.getServerWorld();
        for (UUID id : editor.selection.ids()) {
            if (world.getEntity(id) instanceof DisplayEntity entity && denial(player, entity).isEmpty()) {
                entities.add(entity);
            }
        }
        editor.selection.retain(entities.stream().map(Entity::getUuid).toList());
        return entities;
    }

    // ---------------------------------------------------------------- editar

    /** Así quedan los elegidos tras el gizmo: todo o nada. */
    public static void edit(ServerPlayerEntity player, DisplayPayloads.Edit payload) {
        if (!allowed(player)) {
            return;
        }
        Editor editor = editor(player);
        if (!editor.allow(System.nanoTime(), DisplayConfig.limits().maxEditsPerSecond())) {
            return;
        }
        Map<DisplayEntity, DisplayPose> changes = new LinkedHashMap<>();
        for (DisplayPayloads.Entry entry : payload.entries()) {
            Entity found = player.getServerWorld().getEntityById(entry.entityId());
            if (!(found instanceof DisplayEntity entity) || !editor.selection.contains(entity.getUuid())) {
                send(player, editor, DisplayPayloads.Outcome.REJECTED, "Eso no está elegido");
                return;
            }
            // El billboard solo cambia con los ajustes.
            changes.put(entity, entry.pose().withBillboard(DisplayEntities.pose(entity).billboard()));
        }
        try {
            apply(player, editor, changes, payload.operation().label());
        } catch (IllegalArgumentException rejected) {
            // Ya se lo dijo send(): el cliente vuelve a las poses de verdad.
        }
    }

    /** {@code /komaedit display move}: todos los elegidos, lo mismo. */
    public static int move(ServerPlayerEntity player, Vec3 delta) {
        Editor editor = editor(player);
        Map<DisplayEntity, DisplayPose> changes = new LinkedHashMap<>();
        for (DisplayEntity entity : needSelection(player, editor)) {
            changes.put(entity, DisplayEntities.pose(entity).moved(delta));
        }
        return apply(player, editor, changes, "Mover displays");
    }

    /** {@code /komaedit display rotate}: alrededor del centro del activo, en un eje del mundo o del activo. */
    public static int rotate(ServerPlayerEntity player, int axis, double degrees, boolean local) {
        Editor editor = editor(player);
        List<DisplayEntity> entities = needSelection(player, editor);
        DisplayEntity primary = primary(player, editor, entities);
        DisplayPose primaryPose = DisplayEntities.pose(primary).folded();
        Vec3 pivot = center(primary, primaryPose);
        Vec3 direction = local ? primaryPose.frame().axisVector(axis) : unit(axis);
        Quat delta = Quat.axisAngle(direction, Math.toRadians(degrees));
        Map<DisplayEntity, DisplayPose> changes = new LinkedHashMap<>();
        for (DisplayEntity entity : entities) {
            changes.put(entity, DisplayEntities.pose(entity).rotated(delta, pivot));
        }
        return apply(player, editor, changes, "Girar displays");
    }

    /**
     * {@code /komaedit display scale}: con un solo factor, todos por igual alrededor del activo; con tres, cada uno en
     * sus ejes, sobre su centro.
     */
    public static int scale(ServerPlayerEntity player, Vec3 factors, boolean uniform) {
        Editor editor = editor(player);
        List<DisplayEntity> entities = needSelection(player, editor);
        DisplayEntity primary = primary(player, editor, entities);
        Vec3 pivot = center(primary, DisplayEntities.pose(primary));
        Map<DisplayEntity, DisplayPose> changes = new LinkedHashMap<>();
        for (DisplayEntity entity : entities) {
            DisplayPose pose = DisplayEntities.pose(entity);
            changes.put(entity, uniform ? pose.scaledAround(factors.x(), pivot)
                    : pose.scaledLocal(factors, DisplayEntities.serverVolume(entity).center()));
        }
        return apply(player, editor, changes, "Escalar displays");
    }

    /** {@code /komaedit display reset}: sin giro y con escala 1, sin mover su centro. */
    public static int reset(ServerPlayerEntity player) {
        Editor editor = editor(player);
        Map<DisplayEntity, DisplayPose> changes = new LinkedHashMap<>();
        for (DisplayEntity entity : needSelection(player, editor)) {
            changes.put(entity, DisplayEntities.pose(entity).withRotationScale(Quat.IDENTITY, Quat.IDENTITY,
                    DisplayPose.ONE, DisplayEntities.serverVolume(entity).center()));
        }
        return apply(player, editor, changes, "Enderezar displays");
    }

    private static List<DisplayEntity> needSelection(ServerPlayerEntity player, Editor editor) {
        require(player);
        List<DisplayEntity> entities = selected(player, editor);
        if (entities.isEmpty()) {
            fail("no tienes ningún display elegido (/komaedit display select o clic con el editor abierto)");
        }
        return entities;
    }

    private static DisplayEntity primary(ServerPlayerEntity player, Editor editor, List<DisplayEntity> entities) {
        UUID primary = editor.selection.primary();
        for (DisplayEntity entity : entities) {
            if (entity.getUuid().equals(primary)) {
                return entity;
            }
        }
        return entities.getLast();
    }

    private static Vec3 unit(int axis) {
        return switch (axis) {
            case 0 -> new Vec3(1, 0, 0);
            case 1 -> new Vec3(0, 1, 0);
            default -> new Vec3(0, 0, 1);
        };
    }

    /**
     * Comprueba y aplica poses nuevas: todas o ninguna. Si alguna no vale, el cliente recibe las de verdad y el motivo.
     *
     * @return cuántos cambió
     */
    private static int apply(ServerPlayerEntity player, Editor editor, Map<DisplayEntity, DisplayPose> changes,
                             String label) {
        Optional<String> problem = check(player, changes);
        if (problem.isPresent()) {
            String message = capitalized(problem.get());
            send(player, editor, DisplayPayloads.Outcome.REJECTED, message);
            throw new IllegalArgumentException(message);
        }
        DisplayHistory<NbtCompound> history = new DisplayHistory<>();
        for (Map.Entry<DisplayEntity, DisplayPose> change : changes.entrySet()) {
            DisplayEntity entity = change.getKey();
            NbtCompound before = snapshot(entity);
            DisplayEntities.apply(entity, change.getValue());
            history.changed(entity.getUuid(), before, snapshot(entity));
            track(entity);
        }
        record(player, history, label);
        send(player, editor, DisplayPayloads.Outcome.APPLIED, "");
        return changes.size();
    }

    private static Optional<String> check(ServerPlayerEntity player, Map<DisplayEntity, DisplayPose> changes) {
        if (changes.isEmpty()) {
            return Optional.of("no hay nada que cambiar");
        }
        DisplayLimits limits = DisplayConfig.limits();
        Vec3 eyes = eyes(player);
        ServerWorld world = player.getServerWorld();
        Map<Long, Integer> arriving = new LinkedHashMap<>();
        List<UUID> moving = changes.keySet().stream().map(Entity::getUuid).toList();
        for (Map.Entry<DisplayEntity, DisplayPose> change : changes.entrySet()) {
            DisplayEntity entity = change.getKey();
            Optional<String> denied = denial(player, entity);
            if (denied.isPresent()) {
                return denied;
            }
            DisplayPose pose = change.getValue();
            Vec3 center = pose.finite() ? center(entity, pose) : new Vec3(Double.NaN, 0, 0);
            Optional<String> bad = limits.checkPose(pose, center, eyes);
            if (bad.isPresent()) {
                return bad;
            }
            if (!canBuild(player, world, center) || !canBuild(player, world, pose.position())) {
                return Optional.of("ahí no puedes construir");
            }
            long chunk = chunkKey(pose.position());
            if (chunk != entity.getChunkPos().toLong()) {
                if (!world.isChunkLoaded(net.minecraft.util.math.ChunkPos.getPackedX(chunk),
                        net.minecraft.util.math.ChunkPos.getPackedZ(chunk))) {
                    return Optional.of("esa zona no está cargada");
                }
                arriving.merge(chunk, 1, Integer::sum);
            }
        }
        if (!KomaPermissions.can(player, UNLIMITED)) {
            for (Map.Entry<Long, Integer> entry : arriving.entrySet()) {
                Optional<String> full = limits.checkChunk(displaysInChunk(world, entry.getKey(), moving),
                        entry.getValue());
                if (full.isPresent()) {
                    return full;
                }
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- acciones

    public static void act(ServerPlayerEntity player, DisplayPayloads.Act payload) {
        if (!allowed(player)) {
            return;
        }
        try {
            String message = switch (payload.action()) {
                case DELETE -> Feedback.format("%s displays borrados · //undo", delete(player)).getString();
                case DUPLICATE -> Feedback.format("%s copias en el mismo sitio: muévelas con el gizmo",
                        duplicate(player)).getString();
                case GROUP -> Feedback.format("Grupo de %s displays", group(player)).getString();
                case UNGROUP -> Feedback.format("%s displays sin grupo", ungroup(player)).getString();
                case CLEAR -> {
                    editor(player).selection.clear();
                    send(player, editor(player), DisplayPayloads.Outcome.INFO, "");
                    yield "";
                }
                case CLOSE -> {
                    setEditing(player, false);
                    yield "";
                }
            };
            if (!message.isEmpty()) {
                // Con el mod, el aviso va con lo elegido y se queda en la barra del editor unos segundos.
                if (hasMod(player)) {
                    send(player, editor(player), DisplayPayloads.Outcome.APPLIED, message);
                } else {
                    Feedback.status(player, message);
                }
            }
        } catch (IllegalArgumentException exception) {
            Feedback.warn(player, capitalized(exception.getMessage()));
            send(player, editor(player), DisplayPayloads.Outcome.REJECTED, capitalized(exception.getMessage()));
        }
    }

    public static int delete(ServerPlayerEntity player) {
        Editor editor = editor(player);
        List<DisplayEntity> entities = needSelection(player, editor);
        DisplayHistory<NbtCompound> history = new DisplayHistory<>();
        for (DisplayEntity entity : entities) {
            history.deleted(entity.getUuid(), snapshot(entity));
            ledger.remove(entity.getUuid());
            entity.discard();
        }
        editor.selection.clear();
        record(player, history, "Borrar displays");
        send(player, editor, DisplayPayloads.Outcome.APPLIED, "");
        return entities.size();
    }

    /** Copias en el mismo sitio, con un grupo nuevo por cada grupo de los originales; se eligen las copias. */
    public static int duplicate(ServerPlayerEntity player) {
        Editor editor = editor(player);
        List<DisplayEntity> entities = needSelection(player, editor);
        ServerWorld world = player.getServerWorld();
        checkCounts(player, world, entities.stream().map(entity -> entity.getChunkPos().toLong()).toList());
        Map<String, String> groups = new HashMap<>();
        DisplayHistory<NbtCompound> history = new DisplayHistory<>();
        List<UUID> copies = new ArrayList<>();
        UUID primary = null;
        for (DisplayEntity entity : entities) {
            NbtCompound data = snapshot(entity);
            data.remove("UUID");
            Optional<Entity> created = EntityType.getEntityFromNbt(data, world);
            if (created.isEmpty() || !(created.get() instanceof DisplayEntity copy)) {
                continue;
            }
            copy.setUuid(UUID.randomUUID());
            for (String tag : List.copyOf(copy.getCommandTags())) {
                if (tag.startsWith(DisplayTags.OWNER_PREFIX) || tag.startsWith(DisplayTags.GROUP_PREFIX)) {
                    copy.removeCommandTag(tag);
                }
            }
            copy.addCommandTag(DisplayTags.MARK);
            copy.addCommandTag(DisplayTags.owner(player.getUuid()));
            DisplayTags.groupOf(entity.getCommandTags()).ifPresent(old -> copy.addCommandTag(DisplayTags.group(
                    groups.computeIfAbsent(old, ignored -> DisplayTags.newGroup(ThreadLocalRandom.current())))));
            if (!world.spawnEntity(copy)) {
                continue;
            }
            remember(copy, player.getUuid());
            history.created(copy.getUuid(), snapshot(copy));
            copies.add(copy.getUuid());
            if (entity.getUuid().equals(editor.selection.primary())) {
                primary = copy.getUuid();
            }
        }
        if (copies.isEmpty()) {
            fail("no se pudo copiar ninguno");
        }
        editor.selection.set(copies, primary);
        record(player, history, "Duplicar displays");
        send(player, editor, DisplayPayloads.Outcome.APPLIED, "");
        return copies.size();
    }

    public static int group(ServerPlayerEntity player) {
        Editor editor = editor(player);
        List<DisplayEntity> entities = needSelection(player, editor);
        if (entities.size() < 2) {
            fail("elige al menos dos displays para agruparlos (Mayús+clic)");
        }
        String tag = DisplayTags.group(DisplayTags.newGroup(ThreadLocalRandom.current()));
        retag(player, editor, entities, tag, "Agrupar displays");
        return entities.size();
    }

    public static int ungroup(ServerPlayerEntity player) {
        Editor editor = editor(player);
        List<DisplayEntity> entities = needSelection(player, editor);
        retag(player, editor, entities, null, "Desagrupar displays");
        return entities.size();
    }

    private static void retag(ServerPlayerEntity player, Editor editor, List<DisplayEntity> entities, String tag,
                              String label) {
        DisplayHistory<NbtCompound> history = new DisplayHistory<>();
        for (DisplayEntity entity : entities) {
            NbtCompound before = snapshot(entity);
            for (String old : List.copyOf(entity.getCommandTags())) {
                if (old.startsWith(DisplayTags.GROUP_PREFIX)) {
                    entity.removeCommandTag(old);
                }
            }
            if (tag != null) {
                entity.addCommandTag(tag);
            }
            history.changed(entity.getUuid(), before, snapshot(entity));
        }
        record(player, history, label);
        send(player, editor, DisplayPayloads.Outcome.APPLIED, "");
    }

    // ---------------------------------------------------------------- ajustes avanzados

    /** Un ajuste avanzado para los elegidos. */
    public sealed interface Setting permits BrightnessSetting, ShadowSetting, ViewRangeSetting, BillboardSetting,
            GlowSetting, LookSetting {
    }

    /** Todos a la vez (lo que manda «Aplicar ajustes» del Studio). */
    public record LookSetting(DisplayLook look) implements Setting {
    }

    /** Brillo fijo de bloque y de cielo (0–15), o {@code null} para el de su sitio. */
    public record BrightnessSetting(Integer block, Integer sky) implements Setting {
    }

    public record ShadowSetting(float radius, float strength) implements Setting {
    }

    public record ViewRangeSetting(float range) implements Setting {
    }

    public record BillboardSetting(DisplayPose.Billboard billboard) implements Setting {
    }

    /** Brillo del contorno: encendido y su color ({@code -1}, el del equipo o blanco). */
    public record GlowSetting(boolean on, int color) implements Setting {
    }

    public static int set(ServerPlayerEntity player, Setting setting) {
        Editor editor = editor(player);
        List<DisplayEntity> entities = needSelection(player, editor);
        DisplayHistory<NbtCompound> history = new DisplayHistory<>();
        for (DisplayEntity entity : entities) {
            NbtCompound before = snapshot(entity);
            switch (setting) {
                case BrightnessSetting brightness -> entity.setBrightness(brightness.block() == null ? null
                        : new Brightness(brightness.block(), brightness.sky()));
                case ShadowSetting shadow -> {
                    entity.setShadowRadius(shadow.radius());
                    entity.setShadowStrength(shadow.strength());
                }
                case ViewRangeSetting view -> entity.setViewRange(view.range());
                case BillboardSetting billboard -> entity.setBillboardMode(
                        DisplayEntity.BillboardMode.values()[billboard.billboard().ordinal()]);
                case GlowSetting glow -> {
                    entity.setGlowing(glow.on());
                    entity.setGlowColorOverride(glow.color());
                }
                case LookSetting all -> {
                    DisplayLook look = all.look();
                    entity.setBrightness(look.brightness() == null ? null
                            : new Brightness(look.brightness(), look.brightness()));
                    entity.setShadowRadius(look.shadowRadius());
                    entity.setShadowStrength(look.shadowStrength());
                    entity.setViewRange(look.viewRange());
                    entity.setBillboardMode(DisplayEntity.BillboardMode.values()[look.billboard().ordinal()]);
                    entity.setGlowing(look.glow());
                    entity.setGlowColorOverride(look.glowColor());
                }
            }
            history.changed(entity.getUuid(), before, snapshot(entity));
        }
        record(player, history, "Ajustes de displays");
        send(player, editor, DisplayPayloads.Outcome.APPLIED, "");
        return entities.size();
    }

    // ---------------------------------------------------------------- historial

    static NbtCompound snapshot(DisplayEntity entity) {
        NbtCompound data = new NbtCompound();
        entity.saveNbt(data);
        return data;
    }

    /** Un paso en el historial de WorldEdit del jugador, con su nombre para el Studio. */
    private static void record(ServerPlayerEntity player, DisplayHistory<NbtCompound> history, String label) {
        if (history.isEmpty()) {
            return;
        }
        try {
            WorldEditService.PlayerContext context = WorldEditService.context(player);
            EditSession session = context.session().createEditSession(context.actor());
            try {
                session.getChangeSet().add(new DisplayChange(player.getServer(),
                        player.getServerWorld().getRegistryKey(), history, player.getUuid()));
            } finally {
                session.close();
            }
            EditHistory.label(session, label);
            context.session().remember(session);
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not record a KomaEdit display edit in the WorldEdit history", exception);
        }
    }

    /** Desde {@link DisplayChange}: deshace o rehace en el hilo del servidor. */
    static void replay(MinecraftServer server, RegistryKey<World> key, DisplayHistory<NbtCompound> history,
                       UUID playerId, boolean undo) {
        ServerWorld world = server.getWorld(key);
        if (world == null) {
            return;
        }
        DisplayHistory.World<NbtCompound> adapter = new DisplayHistory.World<>() {
            @Override
            public boolean exists(UUID id) {
                return world.getEntity(id) instanceof DisplayEntity;
            }

            @Override
            public boolean spawn(NbtCompound state) {
                Optional<Entity> created = EntityType.getEntityFromNbt(state.copy(), world);
                if (created.isEmpty() || !(created.get() instanceof DisplayEntity entity)
                        || !world.isChunkLoaded(entity.getChunkPos().x, entity.getChunkPos().z)
                        || !world.spawnEntity(entity)) {
                    return false;
                }
                DisplayTags.ownerOf(entity.getCommandTags()).ifPresent(owner -> remember(entity, owner));
                return true;
            }

            @Override
            public boolean remove(UUID id) {
                if (!(world.getEntity(id) instanceof DisplayEntity entity)) {
                    return false;
                }
                ledger.remove(id);
                entity.discard();
                return true;
            }

            @Override
            public boolean apply(UUID id, NbtCompound state) {
                if (!(world.getEntity(id) instanceof DisplayEntity entity)) {
                    return false;
                }
                entity.readNbt(state.copy());
                track(entity);
                return true;
            }
        };
        DisplayHistory.Outcome outcome = undo ? history.undo(adapter) : history.redo(adapter);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            return;
        }
        if (outcome.missed() > 0) {
            Feedback.warn(player, Feedback.format("%s displays no se pudieron %s: ya no estaban o su zona no está "
                    + "cargada", outcome.missed(), undo ? "deshacer" : "rehacer").getString());
        }
        Editor editor = EDITORS.get(playerId);
        if (editor != null) {
            selected(player, editor);
            send(player, editor, DisplayPayloads.Outcome.INFO, "");
        }
    }

    // ---------------------------------------------------------------- informar

    /** Lo elegido y su pose real, al cliente con el mod. */
    private static void send(ServerPlayerEntity player, Editor editor, DisplayPayloads.Outcome outcome,
                             String message) {
        if (!hasMod(player)) {
            return;
        }
        List<DisplayPayloads.Entry> entries = new ArrayList<>();
        int primary = -1;
        int grouped = 0;
        ServerWorld world = player.getServerWorld();
        for (UUID id : editor.selection.ids()) {
            if (world.getEntity(id) instanceof DisplayEntity entity && !entity.isRemoved()) {
                entries.add(new DisplayPayloads.Entry(entity.getId(), DisplayEntities.pose(entity)));
                if (id.equals(editor.selection.primary())) {
                    primary = entity.getId();
                }
                if (DisplayTags.groupOf(entity.getCommandTags()).isPresent()) {
                    grouped++;
                }
            }
        }
        ServerPlayNetworking.send(player, new DisplayPayloads.Selection(editor.editing, primary, entries, grouped,
                outcome, message));
    }

    /** «Bloque de piedra», «Objeto: maceta», «Texto «Bienvenidos»»… */
    static String describe(DisplayEntity entity) {
        return switch (entity) {
            case DisplayEntity.BlockDisplayEntity block -> "Bloque " + block.getBlockState().getBlock().getName()
                    .getString();
            case DisplayEntity.ItemDisplayEntity item -> "Objeto " + item.getItemStack().getName().getString();
            case DisplayEntity.TextDisplayEntity text -> {
                String plain = text.getText().getString().replace('\n', ' ');
                yield "Texto «" + (plain.length() > 24 ? plain.substring(0, 24) + "…" : plain) + "»";
            }
            default -> "Display";
        };
    }

    /** Resumen de lo elegido para {@code /komaedit display}. */
    public static List<String> info(ServerPlayerEntity player) {
        Editor editor = editor(player);
        List<String> lines = new ArrayList<>();
        List<DisplayEntity> entities = selected(player, editor);
        lines.add((editor.editing ? "Editor abierto" : "Editor cerrado") + " · " + entities.size() + " elegidos");
        for (DisplayEntity entity : entities.subList(0, Math.min(5, entities.size()))) {
            DisplayPose pose = DisplayEntities.pose(entity).folded();
            Vec3 center = center(entity, pose);
            double[] euler = pose.left().eulerDegrees();
            lines.add(String.format(java.util.Locale.ROOT, "%s · centro %.2f, %.2f, %.2f · giro %.0f°, %.0f°, %.0f°"
                            + " · escala %.3g×%.3g×%.3g", describe(entity), center.x(), center.y(), center.z(),
                    euler[0], euler[1], euler[2], pose.scale().x(), pose.scale().y(), pose.scale().z()));
        }
        if (entities.size() > 5) {
            lines.add("… y " + (entities.size() - 5) + " más");
        }
        return lines;
    }

    private static void fail(String message) {
        throw new IllegalArgumentException(capitalized(message));
    }

    static String capitalized(String text) {
        return text == null || text.isEmpty() ? "" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
