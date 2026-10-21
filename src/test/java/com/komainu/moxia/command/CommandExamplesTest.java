package com.komainu.moxia.command;

import com.komainu.moxia.brush.ez.EzBrushType;
import com.komainu.moxia.brush.ez.ZoneCommand;
import com.komainu.moxia.deform.SurfaceOps;
import com.komainu.moxia.generator.ShapeRequest;
import com.komainu.moxia.glass.GlassGradientRequest;
import com.komainu.moxia.image.ImageRequest;
import com.komainu.moxia.ivy.IvySettings;
import com.komainu.moxia.nature.RockSettings;
import com.komainu.moxia.nature.TreeSettings;
import com.komainu.moxia.notes.NoteRequest;
import com.komainu.moxia.preview.PreviewFlags;
import com.komainu.moxia.roof.RoofSettings;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeExpression;
import com.komainu.moxia.shape.tube.EzSpline;
import com.komainu.moxia.shape.tube.TubeCommandParser;
import com.komainu.moxia.text.TextRequest;
import com.komainu.moxia.texture.TextureCommandParser;
import com.komainu.moxia.weathering.WeatheringSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cada orden de ejemplo de la ayuda ({@code /komaedit help}), de {@code docs/MOD_DOCUMENTATION.md},
 * {@code docs/EZEDITS_PARITY.md} y del README es una orden que existe: Brigadier la lee entera hasta una orden, y lo
 * que lleva de texto libre lo acepta el analizador de esa orden (el mismo que usa al ejecutarse). Sin el juego no se
 * puede ejecutar, pero así un nombre cambiado, un modo que no es de esa orden o un número fuera de rango salen aquí y
 * no en el chat de quien copia el ejemplo.
 */
class CommandExamplesTest {
    private static final Pattern BLOCK = Pattern.compile("(?m)^\\s*(/(?:komaedit|ke|mx|moxia) .*)$");
    private static final Pattern INLINE = Pattern.compile("`(/(?:komaedit|ke|mx|moxia)(?: [^`]*)?)`");
    private static final Pattern ALTERNATIVES = Pattern.compile("[a-z0-9_]+(\\|[a-z0-9_]+)+");
    /** Ejemplos de un error a propósito: la documentación enseña qué responde la orden. */
    private static final Set<String> INTENDED_ERRORS = Set.of("/komaedit build coli stone");
    private static final Set<String> NATURE_MANAGEMENT = Set.of("save", "guardar", "list", "lista", "delete",
            "borrar", "cancel", "cancelar");
    private static final Set<String> BRUSH_MANAGEMENT = Set.of("aplicar", "apply", "cancelar", "cancel", "quitar",
            "off", "none", "ninguno", "estado", "status", "actualizar", "update");

    private static OpenCommandTree tree;
    private static Map<String, Example> examples;

    /** Una orden de ejemplo y dónde está; {@code complete} si es un ejemplo entero y no una mención. */
    private record Example(String source, String command, boolean complete) {
    }

    @BeforeAll
    static void collect() throws IOException {
        tree = new OpenCommandTree();
        examples = new LinkedHashMap<>();
        HelpTopics.TOPICS.forEach(topic -> topic.entries().forEach(entry -> add(
                new Example("help " + topic.id(), entry.syntax(), true))));
        for (String file : List.of("docs/MOD_DOCUMENTATION.md", "docs/EZEDITS_PARITY.md", "README.md")) {
            String text = Files.readString(Path.of(file));
            Matcher block = BLOCK.matcher(text);
            while (block.find()) {
                // En los bloques de texto, lo que va tras dos espacios es la explicación de la orden.
                add(new Example(file, block.group(1).strip().split("\\s{2,}")[0], true));
            }
            Matcher inline = INLINE.matcher(text);
            while (inline.find()) {
                add(new Example(file, inline.group(1).strip(), false));
            }
        }
    }

    private static void add(Example example) {
        String command = example.command().replace("\\|", "|");
        // La sintaxis con huecos (<radio>, [patrón], «…») ya la mira CommandTreeSmokeTest hasta el primer hueco.
        if (command.contains("<") || command.contains("[") || command.contains("…") || command.contains("...")) {
            return;
        }
        for (String variant : expand(command)) {
            examples.putIfAbsent(variant, new Example(example.source(), variant, example.complete()));
        }
    }

    @Test
    void everyExampleIsACommandThatExists() {
        assertTrue(examples.size() > 500, "solo hay " + examples.size() + " ejemplos");
        List<String> wrong = new ArrayList<>();
        for (Example example : examples.values()) {
            String problem = tree.problem(example.command().substring(1), example.complete());
            if (problem != null) {
                wrong.add(example.source() + ": " + example.command() + " → " + problem);
            }
        }
        assertEquals(List.of(), wrong, "ejemplos que Brigadier no lee");
    }

    @Test
    void everyExampleIsAcceptedByItsOwnParser() {
        List<String> wrong = new ArrayList<>();
        for (Example example : examples.values()) {
            if (INTENDED_ERRORS.contains(example.command()) || tree.problem(example.command().substring(1),
                    example.complete()) != null) {
                continue;
            }
            List<String> words = words(example.command());
            // Una mención suelta en el texto («`/komaedit texture`») no es un ejemplo que haya que poder ejecutar.
            if (words.size() < (example.complete() ? 3 : 4)) {
                continue;
            }
            try {
                check(words);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                wrong.add(example.source() + ": " + example.command() + " → " + exception.getMessage());
            }
        }
        assertEquals(List.of(), wrong, "ejemplos que su orden rechazaría");
    }

    /** Pasa el texto libre de la orden por el analizador que usa al ejecutarse. */
    private static void check(List<String> words) {
        String family = words.get(1).toLowerCase(Locale.ROOT);
        List<String> rest = words.subList(2, words.size());
        List<String> path = words.subList(1, words.size()).stream().map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
        boolean shortFlag = PreviewScope.eligible(path) && !PreviewScope.shortFlagTaken(path);
        List<String> clean = words(PreviewFlags.strip(String.join(" ", rest), shortFlag).text());
        String mode = clean.isEmpty() ? "" : clean.getFirst();
        String after = String.join(" ", clean.subList(Math.min(1, clean.size()), clean.size()));
        String all = String.join(" ", clean);
        switch (family) {
            case "texture", "textura" -> TextureCommandParser.parse(mode, after, 1L);
            case "surface", "superficie" -> SurfaceOps.Mode.byId(mode);
            case "retocar", "touchup" -> {
                if (RetouchCommands.Mode.find(mode) == null) {
                    throw new IllegalArgumentException("modo de retocar desconocido: " + mode);
                }
            }
            case "roof", "tejado" -> RoofSettings.parse(all);
            case "tree", "arbol" -> {
                if (!NATURE_MANAGEMENT.contains(mode)) {
                    TreeSettings.parse(all, name -> "oak");
                }
            }
            case "rock", "roca" -> {
                if (!NATURE_MANAGEMENT.contains(mode)) {
                    RockSettings.parse(all, name -> "boulder");
                }
            }
            case "text", "texto" -> {
                if (!Set.of("fonts", "fuentes").contains(mode)) {
                    TextRequest.parse(all);
                }
            }
            case "weather", "envejecer" -> WeatheringSettings.parse(all);
            case "ivy", "hiedra" -> {
                if (!Set.of("cancel", "cancelar").contains(mode)) {
                    IvySettings.parse(all);
                }
            }
            case "sweep", "perfil" -> TubeCommandParser.parse(PathShape.SWEEP, mode,
                    clean.size() > 1 ? unquote(clean.get(1)) : "",
                    String.join(" ", clean.subList(Math.min(2, clean.size()), clean.size())), "stone");
            case "braid", "trenza" -> TubeCommandParser.parse(PathShape.BRAID, null, unquote(mode), after, "stone");
            case "noisetube", "erosion" -> TubeCommandParser.parse(PathShape.NOISE_TUBE, null, unquote(mode), after,
                    "stone");
            case "expr", "expresion" -> TubeCommandParser.parseExpression(unquote(mode), after, "stone");
            case "ezspline", "ezsp", "spline" -> EzSpline.parse(all, name -> null);
            case "image", "imagen", "stainedglassimage" -> ImageRequest.parse(all);
            case "glassgradient", "vidriera" -> GlassGradientRequest.parse(all);
            case "shape", "forma" -> ShapeRequest.parse(ShapeRequest.Form.byId(mode), after);
            case "build", "estimate", "estimar" -> ShapeExpression.parse(unquote(mode));
            case "pincel", "ezbrush" -> {
                if (!BRUSH_MANAGEMENT.contains(mode)) {
                    BrushCommands.Resolved resolved = BrushCommands.resolve(mode, after);
                    if (resolved.type() == EzBrushType.ZONE) {
                        ZoneCommand.parse(resolved.options());
                    }
                }
            }
            case "nota", "notas", "note", "notes" -> {
                if (Set.of("poner", "nueva", "add").contains(mode)) {
                    NoteRequest.parse(after);
                }
            }
            default -> {
            }
        }
    }

    /** Palabras separadas por espacios, sin partir lo que va entre comillas. */
    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char character : text.toCharArray()) {
            if (character == '"') {
                quoted = !quoted;
            }
            if (character == ' ' && !quoted) {
                if (!current.isEmpty()) {
                    words.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        if (!current.isEmpty()) {
            words.add(current.toString());
        }
        return words;
    }

    private static String unquote(String word) {
        return word.length() > 1 && word.startsWith("\"") && word.endsWith("\"")
                ? word.substring(1, word.length() - 1) : word;
    }

    /** {@code pincel aplicar|cancelar} son dos ejemplos. */
    private static List<String> expand(String command) {
        List<String> variants = new ArrayList<>(List.of(""));
        for (String word : command.split(" ", -1)) {
            String[] alternatives = ALTERNATIVES.matcher(word).matches() ? word.split("\\|") : new String[]{word};
            List<String> next = new ArrayList<>();
            for (String prefix : variants) {
                for (String alternative : alternatives) {
                    next.add(prefix.isEmpty() ? alternative : prefix + " " + alternative);
                }
            }
            variants = next;
        }
        return variants;
    }
}
