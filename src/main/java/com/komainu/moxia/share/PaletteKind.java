package com.komainu.moxia.share;

import com.google.gson.JsonObject;
import com.komainu.moxia.gradient.PaletteSpec;
import com.komainu.moxia.persistence.NamedDefinitions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Paletas guardadas ({@code /komaedit palette save}). En el paquete van como la lista {@code a;b;c} sin
 * {@code minecraft:}, igual que en los códigos {@code kp1:} de antes. Datos: {@code {"e": "stone;andesite"}}.
 */
public final class PaletteKind extends NamedKind {
    private static final String VANILLA = "minecraft:";

    public PaletteKind(Function<ShareTarget, NamedDefinitions> store) {
        super(store);
    }

    /** Un elemento de paleta con estas entradas (lo usan también los códigos {@code kp1:}). */
    public static SharePack.Item item(String name, List<String> entries) {
        JsonObject data = new JsonObject();
        data.addProperty("e", compact(entries));
        return new SharePack.Item("palette", name, 1, data);
    }

    @Override
    public String id() {
        return "palette";
    }

    @Override
    public List<String> aliases() {
        return List.of("paleta", "paletas", "palettes");
    }

    @Override
    public String singular() {
        return "paleta";
    }

    @Override
    public String plural() {
        return "paletas";
    }

    @Override
    public String permission() {
        return "komaedit.palette.save";
    }

    @Override
    JsonObject data(String value) {
        return item("", PaletteSpec.parse(value)).data();
    }

    @Override
    String value(JsonObject data, ShareCheck check) {
        String text = ShareText.setting("La paleta", string(data, "e"), NamedDefinitions.MAX_VALUE_LENGTH);
        // Sin almacén de paletas: un #nombre de quien la exportó no existe aquí.
        List<String> entries = new ArrayList<>();
        for (String entry : PaletteSpec.parse(text, null)) {
            entries.add(check.block(entry));
        }
        return compact(entries);
    }

    @Override
    String text(JsonObject data) {
        return string(data, "e");
    }

    private static String compact(List<String> entries) {
        List<String> compact = new ArrayList<>(entries.size());
        for (String entry : entries) {
            String trimmed = entry.strip();
            compact.add(trimmed.startsWith(VANILLA) ? trimmed.substring(VANILLA.length()) : trimmed);
        }
        return PaletteSpec.join(compact);
    }
}
