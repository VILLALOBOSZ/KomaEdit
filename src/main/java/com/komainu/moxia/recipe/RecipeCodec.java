package com.komainu.moxia.recipe;

import com.komainu.moxia.gradient.GradientAxis;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeOptions;
import net.minecraft.network.RegistryByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RecipeCodec {
    private RecipeCodec() {
    }

    public static void write(RegistryByteBuf buffer, ProceduralRecipe recipe) {
        buffer.writeString(recipe.name(), 32);
        buffer.writeString(recipe.shape().id(), 32);
        buffer.writeString(recipe.pattern(), 256);
        buffer.writeString(recipe.accentPattern(), 256);
        buffer.writeVarInt(recipe.radius());
        buffer.writeVarInt(recipe.spacing());
        buffer.writeVarInt(recipe.thickness());
        buffer.writeInt(recipe.rollDegrees());
        buffer.writeVarInt(recipe.height());
        buffer.writeString(recipe.paletteId(), 32);
        buffer.writeString(recipe.gradientAxis().id(), 16);
        buffer.writeVarInt(recipe.dither());
        buffer.writeBoolean(recipe.reverse());
        buffer.writeVarInt(recipe.options().size());
        recipe.options().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    buffer.writeString(entry.getKey(), 32);
                    buffer.writeDouble(entry.getValue());
                });
        writeExtensions(buffer, recipe.extensions());
        buffer.writeString(recipe.formula(), ShapeOptions.MAX_FORMULA_LENGTH);
    }

    public static ProceduralRecipe read(RegistryByteBuf buffer) {
        String name = buffer.readString(32);
        PathShape shape = PathShape.byId(buffer.readString(32));
        String pattern = buffer.readString(256);
        String accentPattern = buffer.readString(256);
        int radius = buffer.readVarInt();
        int spacing = buffer.readVarInt();
        int thickness = buffer.readVarInt();
        int roll = buffer.readInt();
        int height = buffer.readVarInt();
        String palette = buffer.readString(32);
        GradientAxis axis = GradientAxis.byId(buffer.readString(16));
        int dither = buffer.readVarInt();
        boolean reverse = buffer.readBoolean();
        int optionCount = buffer.readVarInt();
        if (optionCount < 0 || optionCount > ShapeOptions.MAX_ENTRIES) {
            throw new IllegalArgumentException("Cantidad de parámetros de receta no válida");
        }
        Map<String, Double> options = new LinkedHashMap<>();
        for (int index = 0; index < optionCount; index++) {
            options.put(buffer.readString(32), buffer.readDouble());
        }
        RecipeExtensions extensions = readExtensions(buffer);
        String formula = buffer.readString(ShapeOptions.MAX_FORMULA_LENGTH);
        return new ProceduralRecipe(
                name,
                shape,
                pattern,
                accentPattern,
                radius,
                spacing,
                thickness,
                roll,
                height,
                palette,
                axis,
                dither,
                reverse,
                options,
                extensions,
                formula
        );
    }

    private static void writeExtensions(RegistryByteBuf buffer, RecipeExtensions extensions) {
        buffer.writeVarInt(extensions.startScalePercent());
        buffer.writeVarInt(extensions.endScalePercent());
        buffer.writeVarInt(extensions.startSpacingPercent());
        buffer.writeVarInt(extensions.endSpacingPercent());
        buffer.writeInt(extensions.twistDegrees());
        buffer.writeString(extensions.easing().id(), 24);
        buffer.writeBoolean(extensions.rhythmEnabled());
        buffer.writeString(extensions.accentShape().id(), 32);
        buffer.writeVarInt(extensions.rhythmInterval());
        buffer.writeString(extensions.surfaceMode().id(), 16);
        buffer.writeVarInt(extensions.conformRadius());
        buffer.writeInt(extensions.surfaceOffset());
        buffer.writeString(extensions.materialMetric().id(), 16);
        buffer.writeBoolean(extensions.socketsEnabled());
        buffer.writeString(extensions.socketPlacement().id(), 16);
        buffer.writeVarInt(extensions.socketInterval());
        buffer.writeBoolean(extensions.closeMicroGaps());
        buffer.writeBoolean(extensions.removeIsolated());
    }

    private static RecipeExtensions readExtensions(RegistryByteBuf buffer) {
        return new RecipeExtensions(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readInt(),
                CurveEasing.byId(buffer.readString(24)),
                buffer.readBoolean(),
                PathShape.byId(buffer.readString(32)),
                buffer.readVarInt(),
                SurfaceMode.byId(buffer.readString(16)),
                buffer.readVarInt(),
                buffer.readInt(),
                MaterialMetric.byId(buffer.readString(16)),
                buffer.readBoolean(),
                SocketPlacement.byId(buffer.readString(16)),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }
}
