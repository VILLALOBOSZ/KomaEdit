package com.komainu.moxia.recipe;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RecipeActionPayload(int action, ProceduralRecipe recipe, String instanceId) implements CustomPayload {
    public static final int BUILD = 0;
    public static final int SAVE = 1;
    public static final int DELETE = 2;
    public static final int SYNC = 3;
    public static final int UPDATE_INSTANCE = 4;
    public static final int DELETE_INSTANCE = 5;
    public static final int EXPORT_PACKAGE = 6;
    public static final int IMPORT_PACKAGE = 7;

    public static final Id<RecipeActionPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "recipe_action"));
    public static final PacketCodec<RegistryByteBuf, RecipeActionPayload> CODEC = PacketCodec.ofStatic(
            RecipeActionPayload::write,
            RecipeActionPayload::read
    );

    public RecipeActionPayload {
        recipe = recipe == null ? ProceduralRecipe.defaults() : recipe;
        instanceId = instanceId == null ? "" : instanceId;
        if (instanceId.length() > 36) {
            throw new IllegalArgumentException("Identificador de instancia demasiado largo");
        }
    }

    public RecipeActionPayload(int action, ProceduralRecipe recipe) {
        this(action, recipe, "");
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, RecipeActionPayload payload) {
        buffer.writeVarInt(payload.action());
        RecipeCodec.write(buffer, payload.recipe());
        buffer.writeString(payload.instanceId(), 36);
    }

    private static RecipeActionPayload read(RegistryByteBuf buffer) {
        return new RecipeActionPayload(buffer.readVarInt(), RecipeCodec.read(buffer), buffer.readString(36));
    }
}
