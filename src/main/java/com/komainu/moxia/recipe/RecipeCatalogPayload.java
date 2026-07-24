package com.komainu.moxia.recipe;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record RecipeCatalogPayload(List<ProceduralRecipe> recipes) implements CustomPayload {
    public static final Id<RecipeCatalogPayload> ID = new Id<>(Identifier.of(KomaEditMod.MOD_ID, "recipe_catalog"));
    public static final PacketCodec<RegistryByteBuf, RecipeCatalogPayload> CODEC = PacketCodec.ofStatic(
            RecipeCatalogPayload::write,
            RecipeCatalogPayload::read
    );

    public RecipeCatalogPayload {
        recipes = List.copyOf(recipes);
        if (recipes.size() > 64) {
            throw new IllegalArgumentException("El catálogo admite un máximo de 64 recetas");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buffer, RecipeCatalogPayload payload) {
        buffer.writeVarInt(payload.recipes().size());
        payload.recipes().forEach(recipe -> RecipeCodec.write(buffer, recipe));
    }

    private static RecipeCatalogPayload read(RegistryByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 64) {
            throw new IllegalArgumentException("Tamaño de catálogo no válido");
        }
        java.util.ArrayList<ProceduralRecipe> recipes = new java.util.ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            recipes.add(RecipeCodec.read(buffer));
        }
        return new RecipeCatalogPayload(recipes);
    }
}
