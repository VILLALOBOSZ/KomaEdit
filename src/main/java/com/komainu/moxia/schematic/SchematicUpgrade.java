package com.komainu.moxia.schematic;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.Schemas;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

import java.util.ArrayList;
import java.util.List;

/**
 * Pone al día un esquema de una versión anterior de Minecraft con el DataFixer de vanilla, el mismo que usa el juego
 * con los mundos y las estructuras: los estados de la paleta ({@code grass} → {@code short_grass}…), los datos de
 * bloque (los carteles de antes de 1.20, los objetos de antes de 1.20.5…) y las entidades. Se hace antes de revisar o
 * colocar nada, así lo que se revisa es lo que se va a colocar.
 */
public final class SchematicUpgrade {
    /** Qué se arregla. */
    public enum Kind {
        BLOCK_STATE, BLOCK_ENTITY, ENTITY
    }

    /** El DataFixer: el de Minecraft en el juego. */
    public interface Fixer {
        /** La versión de datos de este Minecraft. */
        int current();

        /** El dato puesto al día (o lanza si no se puede). */
        NbtCompound fix(Kind kind, NbtCompound tag, int from);
    }

    private SchematicUpgrade() {
    }

    /** El de Minecraft. Se construye la primera vez que hace falta. */
    public static Fixer vanilla() {
        return Vanilla.INSTANCE;
    }

    private static final class Vanilla implements Fixer {
        static final Vanilla INSTANCE = new Vanilla();

        @Override
        public int current() {
            return SharedConstants.getGameVersion().getSaveVersion().getId();
        }

        @Override
        public NbtCompound fix(Kind kind, NbtCompound tag, int from) {
            DSL.TypeReference type = switch (kind) {
                case BLOCK_STATE -> TypeReferences.BLOCK_STATE;
                case BLOCK_ENTITY -> TypeReferences.BLOCK_ENTITY;
                case ENTITY -> TypeReferences.ENTITY;
            };
            DataFixer fixer = Schemas.getFixer();
            NbtElement result = fixer.update(type, new Dynamic<>(NbtOps.INSTANCE, (NbtElement) tag), from, current())
                    .getValue();
            if (!(result instanceof NbtCompound compound)) {
                throw new IllegalStateException("el DataFixer no devolvió un compuesto");
            }
            return compound;
        }
    }

    /**
     * El esquema en la versión de datos de {@code fixer}. Si ya lo está, el mismo. Lo que no se puede poner al día se
     * queda como estaba, con un aviso.
     */
    public static SchematicData upgrade(SchematicData data, Fixer fixer, List<String> warnings) {
        int current = fixer.current();
        int from = data.dataVersion();
        if (from == current) {
            return data;
        }
        if (from > current) {
            warnings.add("Es de una versión más nueva de Minecraft (datos " + from + "; aquí, " + current
                    + "): lo que no exista aquí se cambia");
            return data;
        }
        int failed = 0;
        List<String> palette = new ArrayList<>(data.palette().size());
        for (String state : data.palette()) {
            try {
                palette.add(BlockStates.fromNbt(fixer.fix(Kind.BLOCK_STATE, BlockStates.toNbt(state), from)));
            } catch (RuntimeException exception) {
                failed++;
                palette.add(state);
            }
        }
        List<SchematicData.BlockEntity> blockEntities = new ArrayList<>(data.blockEntities().size());
        for (SchematicData.BlockEntity entity : data.blockEntities()) {
            NbtCompound tag = entity.data().copy();
            tag.putString("id", entity.id());
            tag.putInt("x", entity.x());
            tag.putInt("y", entity.y());
            tag.putInt("z", entity.z());
            try {
                NbtCompound fixed = fixer.fix(Kind.BLOCK_ENTITY, tag, from);
                String id = fixed.getString("id");
                SpongeCodec.strip(fixed, "id", "x", "y", "z");
                blockEntities.add(new SchematicData.BlockEntity(entity.x(), entity.y(), entity.z(),
                        id.isEmpty() ? entity.id() : id, fixed));
            } catch (RuntimeException exception) {
                failed++;
                blockEntities.add(entity);
            }
        }
        List<SchematicData.Entity> entities = new ArrayList<>(data.entities().size());
        for (SchematicData.Entity entity : data.entities()) {
            NbtCompound tag = entity.data().copy();
            tag.putString("id", entity.id());
            try {
                NbtCompound fixed = fixer.fix(Kind.ENTITY, tag, from);
                String id = fixed.getString("id");
                SpongeCodec.strip(fixed, "id");
                entities.add(new SchematicData.Entity(entity.x(), entity.y(), entity.z(),
                        id.isEmpty() ? entity.id() : id, fixed));
            } catch (RuntimeException exception) {
                failed++;
                entities.add(entity);
            }
        }
        if (failed > 0) {
            warnings.add(failed + " datos que no se pudieron poner al día desde la versión " + from
                    + ": van como estaban");
        }
        return data.withData(palette, blockEntities, entities, current);
    }
}
