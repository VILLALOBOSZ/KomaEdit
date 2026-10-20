package com.komainu.moxia.studio.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.blueprint.BlueprintSummary;
import com.komainu.moxia.recipe.ProceduralInstanceSummary;
import com.komainu.moxia.recipe.ProceduralRecipe;
import com.komainu.moxia.studio.BlockNames;
import com.komainu.moxia.studio.StudioPreferences;
import com.komainu.moxia.studio.net.JobStatusPayload;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * El Studio de mentira de las pruebas y las maquetas: traduce con los archivos de idioma del mod, apunta todo lo que
 * se le pide y deja montar cualquier situación (selección, puntos, trabajo en marcha, planos, historial).
 *
 * <p>Los nombres de bloque salen de una tabla fija con los que usan las pruebas ({@link #TEST_NAMES}), igual en
 * cualquier equipo; los demás, de los idiomas del juego si están a mano (el inglés va en el jar; el español solo en
 * la caché de Loom, que en el CI no existe). Lo que no tiene traducción sale como en el juego: un nombre legible
 * sacado del id.</p>
 */
public final class FakeStudioHost implements StudioHost {
    private static final Map<String, Map<String, String>> LANGS = new HashMap<>();
    /** Nombres de bloque que las pruebas comprueban, en los dos idiomas (los mismos que da el juego). */
    static final Map<String, Map<String, String>> TEST_NAMES = Map.of(
            "es_es", Map.of(
                    "block.minecraft.spruce_planks", "Tablones de abeto",
                    "block.minecraft.oak_planks", "Tablones de roble",
                    "block.minecraft.stone_bricks", "Ladrillos de piedra",
                    "block.minecraft.deepslate_tiles", "Baldosas de pizarra profunda",
                    "block.minecraft.torch", "Antorcha",
                    "block.minecraft.stone", "Piedra",
                    "block.minecraft.deepslate", "Pizarra profunda",
                    "block.minecraft.calcite", "Calcita"),
            "en_us", Map.of(
                    "block.minecraft.spruce_planks", "Spruce Planks",
                    "block.minecraft.oak_planks", "Oak Planks",
                    "block.minecraft.stone_bricks", "Stone Bricks",
                    "block.minecraft.deepslate_tiles", "Deepslate Tiles",
                    "block.minecraft.torch", "Torch",
                    "block.minecraft.stone", "Stone",
                    "block.minecraft.deepslate", "Deepslate",
                    "block.minecraft.calcite", "Calcite"));

    static {
        // Sin registro de bloques, la etapa Color usa la tabla de colores medidos.
        com.komainu.moxia.color.BlockColorCatalogs.use(com.komainu.moxia.color.TestColors.vanilla());
    }

    private final Map<String, String> lang;
    /** Traducciones de bloque por clave ({@code block.minecraft.stone}); las pruebas pueden quitar o poner. */
    public final Map<String, String> blockNames;
    public final List<String> sent = new ArrayList<>();
    public final List<String> copied = new ArrayList<>();
    public final List<Integer> recipeActions = new ArrayList<>();
    public final StudioPreferences preferences = new StudioPreferences();
    public String status = "";
    public int requests;
    public int pathVisits;
    public int undos;
    public int redos;
    public int saves;
    public boolean closed;
    public boolean shift;
    public long now = 1_000_000L;
    public ToolContext context = ToolContext.UNKNOWN;
    public ToolsInfo info = ToolsInfo.EMPTY;
    public String lookedAt;
    public List<int[]> points = new ArrayList<>();
    public boolean pathClosed;
    public int previewBlocks = -1;
    public boolean hologramHidden;
    public JobStatusPayload job;
    /** El fantasma con gizmo que se está colocando, o {@code null}. */
    public Ghost ghost;
    public WorldHistory history = WorldHistory.EMPTY;
    public List<String> hotbar = new ArrayList<>();
    public List<ProceduralRecipe> recipes = new ArrayList<>();
    public List<ProceduralInstanceSummary> instances = new ArrayList<>();
    public final FakeBlueprints blueprints = new FakeBlueprints();
    public Consumer<String> picker;

    public FakeStudioHost(String language) {
        this(language, true);
    }

    /** @param gameNames si se usan también los nombres de los idiomas del juego, además de la tabla fija */
    public FakeStudioHost(String language, boolean gameNames) {
        lang = load(language);
        blockNames = gameNames ? minecraftNames(language) : new HashMap<>();
        blockNames.putAll(TEST_NAMES.getOrDefault(language, TEST_NAMES.get("en_us")));
    }

    public static Map<String, String> load(String language) {
        return LANGS.computeIfAbsent(language, key -> {
            Path file = Path.of("src", "main", "resources", "assets", "komaedit", "lang", key + ".json");
            try {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    values.put(entry.getKey(), entry.getValue().getAsString());
                }
                return values;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    /**
     * Los nombres de bloque del juego: el inglés va en el jar; el español, en los recursos que descarga Loom (si no
     * están, se usa el inglés).
     */
    private static Map<String, String> minecraftNames(String language) {
        Map<String, String> names = new HashMap<>();
        try (InputStream english = FakeStudioHost.class.getClassLoader()
                .getResourceAsStream("assets/minecraft/lang/en_us.json")) {
            if (english != null) {
                read(names, english);
            }
        } catch (IOException | RuntimeException ignored) {
            // Sin el jar del juego: los ids.
        }
        if (!language.equals("en_us")) {
            Path index = Path.of(System.getProperty("user.home"), ".gradle", "caches", "fabric-loom", "assets",
                    "indexes", "1.21.1-17.json");
            try {
                JsonObject objects = JsonParser.parseString(Files.readString(index)).getAsJsonObject()
                        .getAsJsonObject("objects");
                JsonObject entry = objects.getAsJsonObject("minecraft/lang/" + language + ".json");
                if (entry != null) {
                    String hash = entry.get("hash").getAsString();
                    Path file = index.getParent().getParent().resolve("objects").resolve(hash.substring(0, 2))
                            .resolve(hash);
                    try (InputStream stream = Files.newInputStream(file)) {
                        read(names, stream);
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // Sin la caché de Loom: nombres en inglés.
            }
        }
        return names;
    }

    private static void read(Map<String, String> names, InputStream stream) {
        JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getKey().startsWith("block.minecraft.") || entry.getKey().startsWith("item.minecraft.")) {
                names.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
    }

    /** Como el {@code Text.translatable} de Minecraft: %s en orden, %1$s por posición y %% para el tanto por ciento. */
    public static String format(String pattern, Object... arguments) {
        StringBuilder out = new StringBuilder();
        int next = 0;
        for (int index = 0; index < pattern.length(); index++) {
            char character = pattern.charAt(index);
            if (character != '%' || index + 1 >= pattern.length()) {
                out.append(character);
                continue;
            }
            char following = pattern.charAt(index + 1);
            if (following == '%') {
                out.append('%');
                index++;
            } else if (following == 's') {
                out.append(next < arguments.length ? arguments[next] : "");
                next++;
                index++;
            } else if (Character.isDigit(following) && pattern.indexOf("$s", index) == index + 2) {
                int position = following - '1';
                out.append(position < arguments.length ? arguments[position] : "");
                index += 3;
            } else {
                out.append(character);
            }
        }
        return out.toString();
    }

    @Override
    public String tr(String key, Object... arguments) {
        String pattern = lang.get(key);
        return pattern == null ? key : format(pattern, arguments);
    }

    @Override
    public void send(String command) {
        sent.add(command);
    }

    @Override
    public void pickBlock(String title, String current, Consumer<String> onPick) {
        picker = onPick;
    }

    @Override
    public void status(String text) {
        status = text;
    }

    @Override
    public void copy(String text) {
        copied.add(text);
    }

    @Override
    public void goToPath() {
        pathVisits++;
    }

    @Override
    public boolean shift() {
        return shift;
    }

    @Override
    public long now() {
        return now;
    }

    @Override
    public ToolContext context() {
        return context;
    }

    @Override
    public ToolsInfo info() {
        return info;
    }

    @Override
    public void requestState() {
        requests++;
    }

    @Override
    public StudioPreferences preferences() {
        return preferences;
    }

    @Override
    public void savePreferences() {
        saves++;
    }

    @Override
    public String blockName(String id) {
        return BlockNames.name(id, blockNames::get);
    }

    @Override
    public String lookedAtBlock() {
        return lookedAt;
    }

    @Override
    public void recipeAction(int action, ProceduralRecipe recipe, String instanceId) {
        recipeActions.add(action);
    }

    @Override
    public List<ProceduralRecipe> recipes() {
        return recipes;
    }

    @Override
    public List<ProceduralInstanceSummary> instances() {
        return instances;
    }

    @Override
    public List<int[]> pathPoints() {
        return points;
    }

    @Override
    public boolean pathClosed() {
        return pathClosed;
    }

    @Override
    public int previewBlocks() {
        return previewBlocks;
    }

    @Override
    public boolean previewSettled() {
        return true;
    }

    @Override
    public boolean hologramHidden() {
        return hologramHidden;
    }

    @Override
    public void setHologramHidden(boolean hidden) {
        hologramHidden = hidden;
    }

    @Override
    public String hologramStatus() {
        return previewBlocks < 0 ? "" : tr("gui.komaedit.estimate", previewBlocks);
    }

    @Override
    public Optional<Ghost> ghost() {
        return Optional.ofNullable(ghost);
    }

    @Override
    public Optional<JobStatusPayload> job() {
        return Optional.ofNullable(job).filter(JobStatusPayload::active);
    }

    @Override
    public void worldUndo() {
        undos++;
    }

    @Override
    public void worldRedo() {
        redos++;
    }

    @Override
    public WorldHistory worldHistory() {
        return history;
    }

    @Override
    public Blueprints blueprints() {
        return blueprints;
    }

    @Override
    public List<String> allBlocks() {
        List<String> all = new ArrayList<>();
        for (String key : blockNames.keySet()) {
            if (key.startsWith("block.minecraft.") && key.indexOf('.', "block.minecraft.".length()) < 0) {
                all.add("minecraft:" + key.substring("block.minecraft.".length()));
            }
        }
        all.sort(String::compareTo);
        return all.isEmpty() ? List.of("minecraft:stone", "minecraft:spruce_planks", "minecraft:oak_log") : all;
    }

    @Override
    public List<String> hotbarBlocks() {
        return hotbar;
    }

    @Override
    public void suggest(String command) {
        sent.add("suggest:" + command);
    }

    @Override
    public void close() {
        closed = true;
    }

    public String last() {
        return sent.isEmpty() ? null : sent.getLast();
    }

    /** Planos de mentira, con miniaturas ya hechas. */
    public static final class FakeBlueprints implements Blueprints {
        public boolean received = true;
        public final List<BlueprintSummary> list = new ArrayList<>();
        public final Map<String, StudioImage> thumbnails = new LinkedHashMap<>();
        public boolean canPlace = true;
        public boolean canStamp = true;
        public boolean canSave = true;
        public boolean canShare = true;
        public int refreshes;
        public final List<String> requested = new ArrayList<>();

        @Override
        public boolean received() {
            return received;
        }

        @Override
        public List<BlueprintSummary> list() {
            return list;
        }

        @Override
        public boolean canSave() {
            return canSave;
        }

        @Override
        public boolean canPlace() {
            return canPlace;
        }

        @Override
        public boolean canStamp() {
            return canStamp;
        }

        @Override
        public boolean canShare() {
            return canShare;
        }

        @Override
        public void refresh(boolean force) {
            refreshes++;
        }

        @Override
        public StudioImage thumbnail(String hash) {
            return thumbnails.get(hash);
        }

        @Override
        public void request(List<String> hashes) {
            requested.addAll(hashes);
        }
    }

    @Override
    public String toString() {
        return "FakeStudioHost[" + sent.size() + " órdenes, " + status.toLowerCase(Locale.ROOT) + "]";
    }
}
