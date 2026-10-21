package com.komainu.moxia.command;

import com.komainu.moxia.brush.ez.ZoneCommand;
import com.komainu.moxia.deform.DeformArguments;
import com.komainu.moxia.deform.SmoothProfile;
import com.komainu.moxia.generator.ShapeRequest;
import com.komainu.moxia.glass.GlassGradientRequest;
import com.komainu.moxia.image.ImageRequest;
import com.komainu.moxia.ivy.IvySettings;
import com.komainu.moxia.loft.LoftSettings;
import com.komainu.moxia.nature.RockSettings;
import com.komainu.moxia.nature.TreeSettings;
import com.komainu.moxia.notes.NoteRequest;
import com.komainu.moxia.placement.PlacementSettings;
import com.komainu.moxia.roof.RoofSettings;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeExpression;
import com.komainu.moxia.shape.tube.EzSpline;
import com.komainu.moxia.shape.tube.TubeCommandParser;
import com.komainu.moxia.text.TextRequest;
import com.komainu.moxia.texture.TextureCommandParser;
import com.komainu.moxia.weathering.WeatheringSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lo que se escribe mal en una orden (un número enorme, una coma suelta, una comilla sin cerrar, una bandera sin
 * valor…) da un error claro de la propia orden: {@link IllegalArgumentException} o {@link IllegalStateException}
 * con un texto para el jugador, nunca otra excepción de Java ni su texto técnico. Pasa por los analizadores de texto
 * libre de las órdenes (los mismos que usan al ejecutarse) una lista de entradas que salen al escribir deprisa.
 */
class CommandParsersFuzzTest {
    private static final List<String> TYPOS = List.of("", " ", "-", "--", "-x", "-s", "-s abc", "-seed", "-seed x",
            "999999999999999999999", "-999999999999999", "-1", "NaN", "Infinity", "-Infinity", "1e400", "1e-400",
            "(", ")", "((", "a(b", "a(b:", "a(b:c", "\"", "\"sin cerrar", ",", ",,", ";", ":", "::", "1:2:3:4", "#",
            "##", "%", "50%", "0", "-0", "1,2,3,4,5,6,7,8,9", "..", "1..", "..1", "@", "|", "ñ", "x".repeat(5_000),
            "0x10", "1/0", "0/0", "~", "~x", "#ff", "#zzzzzz", "a b c d e f g h i j k l", "-w", "-w ???", "-m", "-q",
            "-e", "-n", "-t", "-r", "-h", "-b", "-d", "-l", "-g", "-i", "-c", "-a", "-p", "-o", "-u", "-z", "-k",
            "-f", "-y", "-j", "-x 0", "-x -1", "-x 1e9", "-s 1e99", "-r 1e99", "-d -5", "1 2 3 4 5 6 7 8 9 10",
            "-2147483648", "2147483648", "stone , 5", "stone ,, 5", "stone ; 5");

    @Test
    void typosGiveAClearErrorAndNeverAJavaException() {
        Map<String, Consumer<String>> parsers = new LinkedHashMap<>();
        for (TextureCommandParser.Mode mode : TextureCommandParser.Mode.values()) {
            parsers.put("texture " + mode.id(), text -> TextureCommandParser.parse(mode.id(), text, 1L));
            parsers.put("texture " + mode.id() + " con máscara",
                    text -> TextureCommandParser.parse(mode.id(), "#existing ##Magma " + text, 1L));
        }
        for (ShapeRequest.Form form : ShapeRequest.Form.values()) {
            parsers.put("shape " + form.id(), text -> ShapeRequest.parse(form, text));
            parsers.put("shape " + form.id() + " stone", text -> ShapeRequest.parse(form, "stone " + text));
        }
        parsers.put("roof", RoofSettings::parse);
        parsers.put("roof gable", text -> RoofSettings.parse("gable stone " + text));
        parsers.put("loft", text -> LoftSettings.parse(text, name -> false));
        parsers.put("tree", text -> TreeSettings.parse(text, name -> null));
        parsers.put("tree oak", text -> TreeSettings.parse("oak " + text, name -> null));
        parsers.put("rock", text -> RockSettings.parse(text, name -> null));
        parsers.put("rock crag", text -> RockSettings.parse("crag " + text, name -> null));
        parsers.put("text", TextRequest::parse);
        parsers.put("text \"hola\"", text -> TextRequest.parse("\"hola\" " + text));
        parsers.put("weather", WeatheringSettings::parse);
        parsers.put("ivy", IvySettings::parse);
        parsers.put("sweep: radios", text -> TubeCommandParser.parse(PathShape.SWEEP, "circle", text, "", "stone"));
        parsers.put("sweep: banderas", text -> TubeCommandParser.parse(PathShape.SWEEP, "circle", "2", text,
                "stone"));
        parsers.put("sweep: perfil", text -> TubeCommandParser.parse(PathShape.SWEEP, text, "2", "", "stone"));
        parsers.put("braid", text -> TubeCommandParser.parse(PathShape.BRAID, null, "2", text, "stone"));
        parsers.put("expr", text -> TubeCommandParser.parseExpression("2", text, "stone"));
        parsers.put("expr: radios", text -> TubeCommandParser.parseExpression(text, "r<1", "stone"));
        parsers.put("ezspline", text -> EzSpline.parse(text, name -> null));
        parsers.put("ezspline 2d", text -> EzSpline.parse("2d Circle stone " + text, name -> null));
        parsers.put("ezspline 3d", text -> EzSpline.parse("3d Rings(M:3) stone 4 " + text, name -> null));
        parsers.put("ezspline noise", text -> EzSpline.parse("noise ##Magma 4 " + text, name -> null));
        parsers.put("ezspline expression", text -> EzSpline.parse("expression ##Magma 4 " + text, name -> null));
        parsers.put("ezspline structure", text -> EzSpline.parse("structure Cl " + text, name -> null));
        parsers.put("image", ImageRequest::parse);
        parsers.put("image a.png", text -> ImageRequest.parse("a.png " + text));
        parsers.put("glassgradient", GlassGradientRequest::parse);
        parsers.put("glassgradient #ffffff", text -> GlassGradientRequest.parse("#ffffff " + text));
        parsers.put("build", ShapeExpression::parse);
        parsers.put("build coil(…)", text -> ShapeExpression.parse("coil(" + text + ")"));
        parsers.put("build coil(radius:…)", text -> ShapeExpression.parse("coil(radius:" + text + ")"));
        parsers.put("nota poner", NoteRequest::parse);
        parsers.put("pincel zona", ZoneCommand::parse);
        parsers.put("pincel zona 8", text -> ZoneCommand.parse("8 " + text));
        parsers.put("-w", SmoothProfile::parse);
        parsers.put("place", text -> PlacementSettings.parse(PlacementSettings.Kind.PLACE, text));
        parsers.put("place Cl", text -> PlacementSettings.parse(PlacementSettings.Kind.PLACE, "Cl " + text));
        parsers.put("array Cl", text -> PlacementSettings.parse(PlacementSettings.Kind.ARRAY, "Cl " + text));
        parsers.put("scatter Cl", text -> PlacementSettings.parse(PlacementSettings.Kind.SCATTER, "Cl " + text));
        parsers.put("deform, surface, smooth…", text -> {
            DeformArguments arguments = DeformArguments.parse(text, Set.of("m", "w", "s", "seed"), "abc");
            arguments.number(0, 1.0, 0.0, 10.0, "El número");
            arguments.value("s", 1.0, 0.0, 10.0, "La s");
            arguments.seed("seed", 1L);
        });
        parsers.put("color, hotbar, sel…", text -> {
            CommandTokens tokens = CommandTokens.parse(text, Set.of("v", "f"));
            tokens.at(0, null);
            tokens.option("v", "0");
        });
        parsers.put("retocar <modo> on|off", RetouchCommands::state);
        parsers.put("retocar inundar <nivel>", text -> BuilderCommands.level(text, 64, 64));
        List<String> wrong = new ArrayList<>();
        parsers.forEach((name, parser) -> {
            for (String typo : TYPOS) {
                String shown = typo.length() > 24 ? typo.substring(0, 24) + "…" : typo;
                try {
                    parser.accept(typo);
                } catch (NumberFormatException exception) {
                    wrong.add(name + " «" + shown + "»: " + exception);
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    if (!readable(exception.getMessage())) {
                        wrong.add(name + " «" + shown + "»: " + exception);
                    }
                } catch (RuntimeException exception) {
                    wrong.add(name + " «" + shown + "»: " + exception);
                }
            }
        });
        assertEquals(List.of(), wrong, "erratas que no dan un error claro");
    }

    /** Un texto para el jugador: ni vacío ni el de Java. */
    private static boolean readable(String message) {
        return message != null && !message.isBlank() && !message.contains("For input string")
                && !message.contains("out of bounds") && !message.contains("java.")
                && !message.matches("(?s).*\\b[A-Z][A-Za-z]+(Exception|Error)\\b.*");
    }
}
