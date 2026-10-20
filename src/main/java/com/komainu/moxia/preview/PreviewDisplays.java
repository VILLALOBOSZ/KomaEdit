package com.komainu.moxia.preview;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.edit.BlockBounds;
import com.mojang.serialization.DataResult;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Block displays de la vista previa para los clientes sin KomaEdit (los que lo tienen la dibujan ellos con
 * {@link PreviewPlanPayload}), enviados por paquetes solo al jugador que edita. No existen en el mundo del
 * servidor: nadie más los ve, no se guardan con el chunk y el cliente los olvida al salir o al cambiar de dimensión.
 * Llevan además la etiqueta {@link #TAG}, y el servidor borra cualquier entidad con ella que llegue a cargarse
 * (ver {@code KomaEditMod}), por si alguna acabara en el mundo.
 *
 * <p>Los block displays de vanilla no admiten transparencia: lo nuevo se ve con su bloque y contorno verde (un poco
 * más pequeño si va en un hueco, para distinguirlo de lo real, y envolviendo al bloque viejo si lo sustituye); lo
 * que se quita, cubierto de cristal rojo con contorno rojo. Lo que envuelve a un bloque real sobresale un 1 % por
 * cara: con menos, las caras coinciden con las del mundo y parpadean (z-fighting) a pocos bloques.
 */
public final class PreviewDisplays {
    public static final String TAG = "komaedit_preview";
    public static final int ADD_COLOR = 0x5CFF7A;
    public static final int REMOVE_COLOR = 0xFF4040;
    public static final int BOX_COLOR = 0xFFD84A;
    private static final float ADD_SCALE = 0.82f;
    private static final float REPLACE_SCALE = 1.02f;
    private static final float REMOVE_SCALE = 1.03f;
    private static final float EDGE = 0.07f;
    /** Unos 160 bloques con la distancia de entidades de serie: más lejos solo es ruido. */
    private static final float VIEW_RANGE = 2.5f;

    private PreviewDisplays() {
    }

    public enum Kind {
        /** Un bloque nuevo en un hueco. */
        ADD,
        /** Un bloque que sustituye a otro. */
        REPLACE,
        /** Un bloque que se quita (queda aire). */
        REMOVE
    }

    /**
     * Un display: la entidad va en {@code (x, y, z)} y su bloque se dibuja desplazado {@code offset} y escalado
     * {@code scale}. {@code width} y {@code height} son la caja con la que el cliente decide si lo dibuja.
     */
    public record Display(double x, double y, double z, BlockState state, Vector3f offset, Vector3f scale,
                          float width, float height, int glow) {
    }

    /** El display de un cambio en el bloque {@code (x, y, z)}. */
    public static Display block(int x, int y, int z, BlockState placed, Kind kind) {
        return switch (kind) {
            case ADD -> centered(x, y, z, placed, ADD_SCALE, ADD_COLOR);
            case REPLACE -> centered(x, y, z, placed, REPLACE_SCALE, ADD_COLOR);
            case REMOVE -> centered(x, y, z, Blocks.RED_STAINED_GLASS.getDefaultState(), REMOVE_SCALE, REMOVE_COLOR);
        };
    }

    private static Display centered(int x, int y, int z, BlockState state, float scale, int glow) {
        float shift = (1.0f - scale) / 2.0f;
        return new Display(x + 0.5, y, z + 0.5, state, new Vector3f(-0.5f + shift, shift, -0.5f + shift),
                new Vector3f(scale, scale, scale), 1.0f, 1.0f, glow);
    }

    /** Las doce aristas de la caja de la operación, como barras finas. */
    public static List<Display> box(BlockBounds bounds) {
        double x0 = bounds.minimumX();
        double y0 = bounds.minimumY();
        double z0 = bounds.minimumZ();
        double x1 = bounds.maximumX() + 1.0;
        double y1 = bounds.maximumY() + 1.0;
        double z1 = bounds.maximumZ() + 1.0;
        float sizeX = (float) (x1 - x0);
        float sizeY = (float) (y1 - y0);
        float sizeZ = (float) (z1 - z0);
        BlockState state = Blocks.YELLOW_CONCRETE.getDefaultState();
        float half = EDGE / 2.0f;
        List<Display> edges = new ArrayList<>(12);
        for (double y : new double[]{y0, y1}) {
            for (double z : new double[]{z0, z1}) {
                edges.add(new Display((x0 + x1) / 2.0, y - half, z, state, new Vector3f(-sizeX / 2.0f, 0.0f, -half),
                        new Vector3f(sizeX, EDGE, EDGE), sizeX, EDGE, BOX_COLOR));
            }
            for (double x : new double[]{x0, x1}) {
                edges.add(new Display(x, y - half, (z0 + z1) / 2.0, state, new Vector3f(-half, 0.0f, -sizeZ / 2.0f),
                        new Vector3f(EDGE, EDGE, sizeZ), sizeZ, EDGE, BOX_COLOR));
            }
        }
        for (double x : new double[]{x0, x1}) {
            for (double z : new double[]{z0, z1}) {
                edges.add(new Display(x, y0, z, state, new Vector3f(-half, 0.0f, -half),
                        new Vector3f(EDGE, sizeY, EDGE), EDGE, sizeY, BOX_COLOR));
            }
        }
        return edges;
    }

    /**
     * Manda los displays al jugador.
     *
     * @return los ids de entidad enviados, para retirarlos con {@link #destroy}
     */
    public static int[] spawn(ServerPlayerEntity player, List<Display> displays) {
        int[] ids = new int[displays.size()];
        int sent = 0;
        for (Display display : displays) {
            try {
                ids[sent] = spawn(player, display);
                sent++;
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.warn("Could not send a KomaEdit preview display", exception);
            }
        }
        return sent == ids.length ? ids : java.util.Arrays.copyOf(ids, sent);
    }

    public static void destroy(ServerPlayerEntity player, int[] ids) {
        if (player == null || ids == null || ids.length == 0 || player.networkHandler == null) {
            return;
        }
        player.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(ids));
    }

    private static int spawn(ServerPlayerEntity player, Display display) {
        DisplayEntity.BlockDisplayEntity entity = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY,
                player.getServerWorld());
        NbtCompound nbt = new NbtCompound();
        nbt.put(DisplayEntity.BlockDisplayEntity.BLOCK_STATE_NBT_KEY, NbtHelper.fromBlockState(display.state()));
        nbt.put(DisplayEntity.TRANSFORMATION_NBT_KEY, encode(AffineTransformation.ANY_CODEC.encodeStart(
                NbtOps.INSTANCE, new AffineTransformation(display.offset(), null, display.scale(), null))));
        nbt.put(DisplayEntity.BRIGHTNESS_NBT_KEY, encode(Brightness.CODEC.encodeStart(NbtOps.INSTANCE,
                new Brightness(15, 15))));
        nbt.putInt(DisplayEntity.GLOW_COLOR_OVERRIDE_NBT_KEY, display.glow());
        nbt.putFloat(DisplayEntity.VIEW_RANGE_NBT_KEY, VIEW_RANGE);
        nbt.putFloat(DisplayEntity.WIDTH_NBT_KEY, display.width());
        nbt.putFloat(DisplayEntity.HEIGHT_NBT_KEY, display.height());
        entity.readNbt(nbt);
        entity.setPosition(display.x(), display.y(), display.z());
        entity.setGlowing(true);
        entity.addCommandTag(TAG);
        List<DataTracker.SerializedEntry<?>> values = entity.getDataTracker().getChangedEntries();
        player.networkHandler.sendPacket(new EntitySpawnS2CPacket(entity.getId(), entity.getUuid(), display.x(),
                display.y(), display.z(), 0.0f, 0.0f, EntityType.BLOCK_DISPLAY, 0, Vec3d.ZERO, 0.0));
        if (values != null && !values.isEmpty()) {
            player.networkHandler.sendPacket(new EntityTrackerUpdateS2CPacket(entity.getId(), values));
        }
        return entity.getId();
    }

    private static NbtElement encode(DataResult<NbtElement> result) {
        return result.getOrThrow(IllegalStateException::new);
    }

    private static final java.util.Set<Entity> ORPHANS = java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<>());

    /** Si una entidad cargada es un display de vista previa que no debería existir. */
    public static boolean isOrphan(Entity entity) {
        return entity.getCommandTags().contains(TAG);
    }

    /**
     * Al cargarse una entidad: si es un display de vista previa (no debería haber ninguno en el mundo), queda
     * apuntada para borrarse en el siguiente tick, fuera de la carga del chunk.
     */
    public static void onEntityLoad(Entity entity) {
        if (isOrphan(entity)) {
            ORPHANS.add(entity);
        }
    }

    /** Borra los displays huérfanos apuntados; devuelve cuántos. */
    public static int sweep() {
        if (ORPHANS.isEmpty()) {
            return 0;
        }
        List<Entity> orphans = new ArrayList<>(ORPHANS);
        ORPHANS.clear();
        for (Entity entity : orphans) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        KomaEditMod.LOGGER.info("Removed {} leftover KomaEdit preview display(s)", orphans.size());
        return orphans.size();
    }

    public static void clearOrphans() {
        ORPHANS.clear();
    }
}
