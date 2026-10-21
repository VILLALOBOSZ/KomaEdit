package com.komainu.moxia.brush.ez;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.gizmo.GizmoGeometry;
import com.komainu.moxia.gizmo.GizmoLayout;
import com.komainu.moxia.placement.AlignmentGuide;
import com.komainu.moxia.preview.PreviewDisplays;
import com.komainu.moxia.shape.Vec3;
import com.mojang.serialization.DataResult;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Las flechas del visualizador de la alineación para los clientes sin KomaEdit, con block displays de hormigón como
 * los de ezEdits: un asta y una punta más gruesa por eje (verde la primaria, rojo el +x, azul el +z) y un cubo blanco
 * en el centro. Como los de la vista previa ({@link PreviewDisplays}), van por paquetes solo al jugador del pincel, no
 * existen en el mundo del servidor y llevan la etiqueta {@link PreviewDisplays#TAG}. Al moverse no se rehacen: se
 * actualizan con interpolación, así se deslizan en vez de saltar. La geometría ({@link #beams}, {@link #transform})
 * es pública para las pruebas y los renders.
 */
public final class AlignmentGuideDisplays {
    /** A estos bloques de la cámara la flecha verde mide un bloque; más lejos crece para seguir viéndose. */
    private static final double LENGTH_PER_BLOCK = 0.14;
    private static final double MIN_LENGTH = 1.2;
    private static final double MAX_LENGTH = 16.0;
    private static final float VIEW_RANGE = 2.5f;
    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    private AlignmentGuideDisplays() {
    }

    public enum Part {SHAFT, TIP, CENTER}

    /**
     * Un trozo de flecha: una barra de {@code width} de lado y {@code length} de largo que sale de {@code from} hacia
     * {@code direction}, o un cubo de lado {@code width} centrado en {@code from}.
     */
    public record Beam(Part part, Vec3 from, Vec3 direction, double length, double width, int argb) {
    }

    /** Lo que se dibuja de una transformación de display: desplazamiento, giro y escala del bloque. */
    public record Transform(Vector3f translation, Quaternionf rotation, Vector3f scale) {
    }

    /** El largo de la flecha verde a {@code distance} bloques de la cámara. */
    public static double length(double distance) {
        return Math.clamp(distance * LENGTH_PER_BLOCK, MIN_LENGTH, MAX_LENGTH);
    }

    /** Las barras de la alineación, en el mismo orden siempre que no cambie el estado (para actualizarlas). */
    public static List<Beam> beams(AlignmentGuide.Result result, double length) {
        List<Beam> beams = new ArrayList<>();
        double width = Math.clamp(length * 0.045, 0.05, 0.4);
        if (result.state() == AlignmentGuide.State.READY) {
            arrow(beams, result.origin(), result.y(), length, width, GizmoGeometry.GREEN);
            arrow(beams, result.origin(), result.x(), length, width, GizmoGeometry.RED);
            arrow(beams, result.origin(), result.z(), length * 0.6, width * 0.8, GizmoGeometry.BLUE);
        } else {
            if (result.primary() != null && result.primary().length() > 0.5) {
                arrow(beams, result.origin(), result.primary(), length, width, GizmoGeometry.GREEN);
            }
            if (result.secondary() != null && result.secondary().length() > 0.5) {
                arrow(beams, result.origin(), result.secondary(), length, width * 0.8, AlignmentGuide.HINT);
            }
        }
        int center = result.state() == AlignmentGuide.State.INVALID ? AlignmentGuide.INVALID_MARK
                : GizmoGeometry.CENTER;
        beams.add(new Beam(Part.CENTER, result.origin(), new Vec3(0, 1, 0), 0.0, width * 3.2, center));
        return beams;
    }

    private static void arrow(List<Beam> beams, Vec3 origin, Vec3 direction, double length, double width,
                              int argb) {
        Vec3 unit = direction.normalized();
        beams.add(new Beam(Part.SHAFT, origin, unit, length * GizmoLayout.TIP_START, width, argb));
        beams.add(new Beam(Part.TIP, origin.add(unit.multiply(length * GizmoLayout.TIP_START)), unit,
                length * (1.0 - GizmoLayout.TIP_START), width * 2.6, argb));
    }

    /**
     * La transformación del display de {@code beam} con la entidad en {@code entity}. El bloque del display ocupa
     * de 0 a 1 en cada eje: se escala a la barra, se centra en su eje y se gira de +y a la dirección.
     */
    public static Transform transform(Beam beam, Vec3 entity) {
        Vec3 offset = beam.from().subtract(entity);
        float width = (float) beam.width();
        if (beam.part() == Part.CENTER) {
            float half = width / 2.0f;
            return new Transform(new Vector3f((float) offset.x() - half, (float) offset.y() - half,
                    (float) offset.z() - half), new Quaternionf(), new Vector3f(width, width, width));
        }
        Vec3 unit = beam.direction().normalized();
        Quaternionf rotation = new Quaternionf().rotationTo(0.0f, 1.0f, 0.0f, (float) unit.x(), (float) unit.y(),
                (float) unit.z());
        Vector3f center = rotation.transform(new Vector3f(width / 2.0f, 0.0f, width / 2.0f));
        return new Transform(new Vector3f((float) offset.x() - center.x, (float) offset.y() - center.y,
                (float) offset.z() - center.z), rotation, new Vector3f(width, (float) beam.length(), width));
    }

    // ---------------------------------------------------------------- paquetes

    private static final class Shown {
        private final ServerWorld world;
        private final List<DisplayEntity.BlockDisplayEntity> entities;
        private final List<Integer> colors;
        private Vec3 position;

        private Shown(ServerWorld world, List<DisplayEntity.BlockDisplayEntity> entities, List<Integer> colors,
                      Vec3 position) {
            this.world = world;
            this.entities = entities;
            this.colors = colors;
            this.position = position;
        }

        private int[] ids() {
            return entities.stream().mapToInt(DisplayEntity.BlockDisplayEntity::getId).toArray();
        }
    }

    static void show(ServerPlayerEntity player, AlignmentGuide.Result result, int updateTicks) {
        UUID id = player.getUuid();
        Shown shown = SHOWN.get(id);
        if (result == null) {
            if (shown != null) {
                PreviewDisplays.destroy(player, shown.ids());
                SHOWN.remove(id);
            }
            return;
        }
        Vec3d eye = player.getEyePos();
        List<Beam> beams = beams(result, length(result.origin().distance(new Vec3(eye.x, eye.y, eye.z))));
        List<Integer> colors = beams.stream().map(Beam::argb).toList();
        try {
            if (shown == null || shown.world != player.getServerWorld() || !shown.colors.equals(colors)) {
                if (shown != null) {
                    PreviewDisplays.destroy(player, shown.ids());
                }
                SHOWN.put(id, spawn(player, beams, result.origin(), colors, updateTicks));
                return;
            }
            update(player, shown, beams, result.origin(), updateTicks);
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not send the KomaEdit alignment guide displays", exception);
            SHOWN.remove(id);
        }
    }

    /** Retira los displays del jugador; devuelve cuántos había. */
    static int clear(ServerPlayerEntity player) {
        Shown shown = SHOWN.remove(player.getUuid());
        if (shown == null) {
            return 0;
        }
        PreviewDisplays.destroy(player, shown.ids());
        return shown.entities.size();
    }

    static void forget(UUID player) {
        SHOWN.remove(player);
    }

    static void clearAll() {
        SHOWN.clear();
    }

    private static Shown spawn(ServerPlayerEntity player, List<Beam> beams, Vec3 origin, List<Integer> colors,
                               int updateTicks) {
        List<DisplayEntity.BlockDisplayEntity> entities = new ArrayList<>(beams.size());
        for (Beam beam : beams) {
            DisplayEntity.BlockDisplayEntity entity = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY,
                    player.getServerWorld());
            NbtCompound nbt = new NbtCompound();
            nbt.put(DisplayEntity.BlockDisplayEntity.BLOCK_STATE_NBT_KEY, NbtHelper.fromBlockState(block(beam)));
            put(nbt, beam, origin, updateTicks);
            nbt.put(DisplayEntity.BRIGHTNESS_NBT_KEY, encode(Brightness.CODEC.encodeStart(NbtOps.INSTANCE,
                    new Brightness(15, 15))));
            nbt.putInt(DisplayEntity.GLOW_COLOR_OVERRIDE_NBT_KEY, beam.argb() & 0xFFFFFF);
            nbt.putFloat(DisplayEntity.VIEW_RANGE_NBT_KEY, VIEW_RANGE);
            entity.readNbt(nbt);
            entity.setPosition(origin.x(), origin.y(), origin.z());
            entity.setGlowing(true);
            entity.addCommandTag(PreviewDisplays.TAG);
            List<DataTracker.SerializedEntry<?>> values = entity.getDataTracker().getChangedEntries();
            player.networkHandler.sendPacket(new EntitySpawnS2CPacket(entity.getId(), entity.getUuid(), origin.x(),
                    origin.y(), origin.z(), 0.0f, 0.0f, EntityType.BLOCK_DISPLAY, 0, Vec3d.ZERO, 0.0));
            if (values != null && !values.isEmpty()) {
                player.networkHandler.sendPacket(new EntityTrackerUpdateS2CPacket(entity.getId(), values));
            }
            // Lo enviado ya no está «sucio»: la siguiente actualización solo manda lo que cambie.
            entity.getDataTracker().getDirtyEntries();
            entities.add(entity);
        }
        return new Shown(player.getServerWorld(), entities, colors, origin);
    }

    private static void update(ServerPlayerEntity player, Shown shown, List<Beam> beams, Vec3 origin,
                               int updateTicks) {
        boolean moved = !origin.equals(shown.position);
        shown.position = origin;
        for (int index = 0; index < beams.size(); index++) {
            DisplayEntity.BlockDisplayEntity entity = shown.entities.get(index);
            // Los setters de los displays son privados: se cambian como con /data merge, por NBT.
            NbtCompound nbt = entity.writeNbt(new NbtCompound());
            put(nbt, beams.get(index), origin, updateTicks);
            entity.readNbt(nbt);
            entity.setPosition(origin.x(), origin.y(), origin.z());
            if (moved) {
                player.networkHandler.sendPacket(new EntityPositionS2CPacket(entity));
            }
            List<DataTracker.SerializedEntry<?>> dirty = entity.getDataTracker().getDirtyEntries();
            if (dirty != null && !dirty.isEmpty()) {
                player.networkHandler.sendPacket(new EntityTrackerUpdateS2CPacket(entity.getId(), dirty));
            }
        }
    }

    private static void put(NbtCompound nbt, Beam beam, Vec3 origin, int updateTicks) {
        Transform transform = transform(beam, origin);
        nbt.put(DisplayEntity.TRANSFORMATION_NBT_KEY, encode(AffineTransformation.ANY_CODEC.encodeStart(
                NbtOps.INSTANCE, new AffineTransformation(transform.translation(), transform.rotation(),
                        transform.scale(), null))));
        nbt.putInt(DisplayEntity.INTERPOLATION_DURATION_KEY, updateTicks);
        nbt.putInt(DisplayEntity.START_INTERPOLATION_KEY, 0);
        nbt.putInt(DisplayEntity.TELEPORT_DURATION_KEY, updateTicks);
    }

    private static BlockState block(Beam beam) {
        return switch (beam.argb() & 0xFFFFFF) {
            case GizmoGeometry.GREEN & 0xFFFFFF -> Blocks.LIME_CONCRETE.getDefaultState();
            case GizmoGeometry.RED & 0xFFFFFF -> beam.argb() == AlignmentGuide.HINT
                    ? Blocks.RED_STAINED_GLASS.getDefaultState() : Blocks.RED_CONCRETE.getDefaultState();
            case GizmoGeometry.BLUE & 0xFFFFFF -> Blocks.BLUE_CONCRETE.getDefaultState();
            case GizmoGeometry.HIGHLIGHT & 0xFFFFFF -> Blocks.YELLOW_CONCRETE.getDefaultState();
            default -> Blocks.WHITE_CONCRETE.getDefaultState();
        };
    }

    private static NbtElement encode(DataResult<NbtElement> result) {
        return result.getOrThrow(IllegalStateException::new);
    }
}
