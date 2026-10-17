package com.komainu.moxia.command;

import com.komainu.moxia.gradient.GradientPalette;
import com.komainu.moxia.path.PathGenerators;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.tube.TubeSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Completa los errores de «desconocido» con una propuesta: «Forma 3D desconocida: helx · ¿Quisiste decir «helix»?».
 * Reconoce el mensaje por su comienzo, así no hace falta tocar los analizadores que lo lanzan.
 */
public final class CommandHints {
    /** Banderas de los comandos de tubo ({@code TubeCommandParser}). */
    public static final List<String> TUBE_FLAGS = List.of(
            "-e", "-n", "-q", "-t", "-s", "-d", "-a", "-x", "-k", "-f", "-r", "-p", "-w", "-c",
            "-seed", "-semilla", "-hollow", "-hueco", "-fill", "-relleno"
    );
    /** Parámetros de {@code figura(nombre:valor)} más usados ({@code ShapeExpression}). */
    public static final List<String> SHAPE_PARAMETERS = List.of(
            "extrusion", "thickness", "grosor", "gap", "stretch", "quality", "angle", "columns", "depth",
            "profundidad", "profile", "perfil", "sides", "lados", "points", "puntas", "petals", "aspect", "aspecto",
            "exponent", "exponente", "hollow", "hueco", "wall", "pared", "filled", "relleno", "end", "extremo",
            "normal", "normales", "tension", "bias", "sesgo", "continuity", "continuidad", "twist", "torsion",
            "rollend", "girofinal", "strands", "hebras", "frequency", "frecuencia", "seed", "semilla", "smooth",
            "piezas", "coverage", "cobertura", "preset", "serie", "engine", "motor"
    );
    private static final Pattern UNKNOWN = Pattern.compile("^(.+?desconocid[oa]): (\\S+)(.*)$", Pattern.DOTALL);
    private static final Map<String, Supplier<Collection<String>>> CANDIDATES = new LinkedHashMap<>();

    static {
        CANDIDATES.put("Forma 3D desconocida", CommandHints::shapeNames);
        CANDIDATES.put("Paleta profesional desconocida", CommandHints::paletteNames);
        CANDIDATES.put("Perfil desconocido", () -> ids(TubeSettings.TubeProfile.values()));
        CANDIDATES.put("Extremo desconocido", () -> ids(TubeSettings.EndMode.values()));
        CANDIDATES.put("Modo de normales desconocido", () -> ids(TubeSettings.NormalMode.values()));
        CANDIDATES.put("Calidad desconocida", () -> ids(TubeSettings.Quality.values()));
        CANDIDATES.put("Modo de piezas desconocido", () -> Arrays.stream(SmoothMode.values()).map(SmoothMode::id).toList());
        CANDIDATES.put("Bandera desconocida", () -> TUBE_FLAGS);
        CANDIDATES.put("Parámetro de figura desconocido", () -> SHAPE_PARAMETERS);
        CANDIDATES.put("Dirección desconocida", () -> Arrays.stream(PathGenerators.Bulge.values())
                .map(PathGenerators.Bulge::id).toList());
    }

    private CommandHints() {
    }

    /** Devuelve el mensaje con la propuesta añadida, o tal cual si no la hay. */
    public static String enrich(String message) {
        if (message == null || message.contains("¿Quisiste decir")) {
            return message;
        }
        Matcher matcher = UNKNOWN.matcher(message);
        if (!matcher.matches()) {
            return message;
        }
        Supplier<Collection<String>> candidates = CANDIDATES.get(matcher.group(1).trim());
        if (candidates == null) {
            return message;
        }
        String typed = matcher.group(2);
        // Las expresiones de figura llegan como «coli(engine:exact)»: se compara solo el nombre.
        int parenthesis = typed.indexOf('(');
        if (parenthesis > 0) {
            typed = typed.substring(0, parenthesis);
        }
        // Si el mensaje ya trae la lista entre paréntesis, la propuesta va delante para que se vea.
        return DidYouMean.best(typed, candidates.get())
                .map(found -> matcher.group(1) + ": " + matcher.group(2) + " · ¿Quisiste decir «" + found + "»?"
                        + matcher.group(3))
                .orElse(message);
    }

    public static List<String> shapeNames() {
        List<String> names = new ArrayList<>();
        for (PathShape shape : PathShape.values()) {
            names.add(shape.id());
            names.addAll(shape.aliases());
        }
        return names;
    }

    public static List<String> paletteNames() {
        List<String> names = new ArrayList<>();
        for (GradientPalette palette : GradientPalette.values()) {
            names.add(palette.id());
            names.addAll(palette.aliases());
        }
        return names;
    }

    private static List<String> ids(TubeSettings.TubeProfile[] values) {
        return Arrays.stream(values).map(TubeSettings.TubeProfile::id).toList();
    }

    private static List<String> ids(TubeSettings.EndMode[] values) {
        return Arrays.stream(values).map(TubeSettings.EndMode::id).toList();
    }

    private static List<String> ids(TubeSettings.NormalMode[] values) {
        return Arrays.stream(values).map(TubeSettings.NormalMode::id).toList();
    }

    private static List<String> ids(TubeSettings.Quality[] values) {
        return Arrays.stream(values).map(TubeSettings.Quality::id).toList();
    }
}
