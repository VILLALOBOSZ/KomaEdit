package com.komainu.moxia.recipe;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record InstanceCatalogPayload(List<ProceduralInstanceSummary> instances) implements CustomPayload {
    public static final Id<InstanceCatalogPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "instance_catalog"));
    public static final PacketCodec<RegistryByteBuf, InstanceCatalogPayload> CODEC = PacketCodec.ofStatic(
            InstanceCatalogPayload::write,
            InstanceCatalogPayload::read
    );

    public InstanceCatalogPayload {
        instances = List.copyOf(instances);
        if (instances.size() > 128) {
            throw new IllegalArgumentException("El catálogo admite un máximo de 128 instancias");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, InstanceCatalogPayload payload) {
        buffer.writeVarInt(payload.instances().size());
        for (ProceduralInstanceSummary instance : payload.instances()) {
            buffer.writeString(instance.id(), 36);
            RecipeCodec.write(buffer, instance.recipe());
            buffer.writeVarInt(instance.pathPoints());
            buffer.writeVarInt(instance.blockCount());
            buffer.writeLong(instance.updatedAt());
        }
    }

    private static InstanceCatalogPayload read(RegistryByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 128) {
            throw new IllegalArgumentException("Tamaño de catálogo de instancias no válido");
        }
        List<ProceduralInstanceSummary> instances = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            instances.add(new ProceduralInstanceSummary(
                    buffer.readString(36),
                    RecipeCodec.read(buffer),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readLong()
            ));
        }
        return new InstanceCatalogPayload(instances);
    }
}
