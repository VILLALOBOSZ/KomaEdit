package com.komainu.moxia.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class RecipePackageService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir()
            .resolve("komaedit")
            .resolve("packages");

    private RecipePackageService() {
    }

    public static Path exportPackage(UUID playerId, String name) {
        ProceduralRecipe recipe = RecipeStore.load(playerId, name);
        Path target = packagePath(name);
        try {
            Files.createDirectories(ROOT);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("recipe.json"));
                OutputStreamWriter writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
                writer.write(GSON.toJson(RecipeStore.toJson(recipe)));
                writer.flush();
                zip.closeEntry();
            }
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo exportar el paquete: " + exception.getMessage(), exception);
        }
    }

    public static ProceduralRecipe importPackage(UUID playerId, String name) {
        Path source = packagePath(name);
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("No existe el paquete " + source.getFileName());
        }
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().equals("recipe.json")) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(zip, StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    ProceduralRecipe recipe = RecipeStore.fromJson(root, root.get("version").getAsInt());
                    RecipeStore.save(playerId, recipe);
                    return recipe;
                }
            }
            throw new IllegalArgumentException("El paquete no contiene recipe.json");
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo importar el paquete: " + exception.getMessage(), exception);
        }
    }

    private static Path packagePath(String name) {
        if (!ProceduralRecipe.VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Nombre de paquete no válido");
        }
        return ROOT.resolve(name + ".krecipe");
    }
}
