package com.komainu.moxia.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Contenido de {@code /komaedit help [tema]}. Cada entrada es la sintaxis de un comando y lo que hace; al hacer clic
 * en el chat se escribe el comando hasta su primer argumento para completarlo a mano.
 */
public final class HelpTopics {
    public record Entry(String syntax, String description) {
        /** Lo que se deja escrito en el chat al hacer clic: hasta el primer {@code <…>} o {@code […]}. */
        public String suggestion() {
            return suggestionOf(syntax);
        }
    }

    public record Topic(String id, String title, List<String> aliases, List<Entry> entries) {
        public Topic {
            aliases = List.copyOf(aliases);
            entries = List.copyOf(entries);
        }
    }

    public static final List<Topic> TOPICS = List.of(
            new Topic("path", "Trayectorias", List.of("ruta", "trayectoria", "trayectorias", "puntos"), List.of(
                    new Entry("/komaedit path mode toggle", "Captura con clic derecho (o la tecla P)"),
                    new Entry("/komaedit path add", "Punto en la cara del bloque que miras"),
                    new Entry("/komaedit path here", "Punto en tu posición"),
                    new Entry("/komaedit path point <x> <y> <z>", "Punto en coordenadas exactas"),
                    new Entry("/komaedit path points", "Lista los puntos con botones para insertar, quitar o moverlos"),
                    new Entry("/komaedit path insert <n> [here]", "Inserta un punto entre el n y el siguiente"),
                    new Entry("/komaedit path remove <n>", "Quita el punto n"),
                    new Entry("/komaedit path set <n> here|<x> <y> <z>", "Mueve el punto n"),
                    new Entry("/komaedit path shift <dx> <dy> <dz>|here", "Mueve todo el recorrido"),
                    new Entry("/komaedit path reverse", "Invierte el sentido"),
                    new Entry("/komaedit path close [on|off]", "Cierra o abre el lazo"),
                    new Entry("/komaedit path mirror <x|y|z> [append]", "Refleja; con append añade la mitad simétrica"),
                    new Entry("/komaedit path rotate <90|180|270>", "Gira sobre el eje vertical"),
                    new Entry("/komaedit path snap <paso> [all]", "Ajusta a una rejilla de paso bloques (all: también la altura)"),
                    new Entry("/komaedit path smooth [fuerza] [pasadas]", "Suaviza sin cambiar el número de puntos"),
                    new Entry("/komaedit path simplify [tolerancia]", "Quita puntos casi alineados"),
                    new Entry("/komaedit path subdivide", "Añade un punto a mitad de cada tramo"),
                    new Entry("/komaedit path undo", "Deshace el último cambio de puntos (redo lo rehace)"),
                    new Entry("/komaedit path pop", "Quita el último punto"),
                    new Entry("/komaedit path clear", "Vacía la trayectoria (se puede deshacer)"),
                    new Entry("/komaedit path status", "Resumen con botones")
            )),
            new Topic("generate", "Generar trayectorias", List.of("generar", "curvas", "catenaria", "espiral"), List.of(
                    new Entry("/komaedit path selection", "Desde la selección de WorldEdit (cuboide, convex o poly)"),
                    new Entry("/komaedit path circle <radio> [puntos]", "Círculo cerrado a tu alrededor"),
                    new Entry("/komaedit path polygon <radio> <lados> [giro]", "Polígono regular cerrado"),
                    new Entry("/komaedit path spiral <radio inicial> <radio final> <vueltas> [altura] [puntos]",
                            "Espiral plana o que sube"),
                    new Entry("/komaedit path helix <radio> <vueltas> <altura> [puntos]", "Hélice"),
                    new Entry("/komaedit path arc <altura> [up|down|left|right] [puntos]",
                            "Arco entre el primer y el último punto"),
                    new Entry("/komaedit path catenary <comba> [down|up|left|right] [puntos]",
                            "Cadena colgante entre el primer y el último punto")
            )),
            new Topic("saved", "Recorridos guardados", List.of("guardar", "guardados", "save", "load"), List.of(
                    new Entry("/komaedit path save <nombre>", "Guarda el recorrido en este mundo"),
                    new Entry("/komaedit path load <nombre>", "Lo carga (se puede deshacer)"),
                    new Entry("/komaedit path list", "Tus recorridos; clic para cargar"),
                    new Entry("/komaedit path delete <nombre>", "Lo borra")
            )),
            new Topic("build", "Figuras", List.of("figuras", "construir", "figura", "shapes"), List.of(
                    new Entry("/komaedit build <figura> [patrón] [escala] [espaciado] [grosor] [giro]",
                            "Construye una figura a lo largo de la trayectoria"),
                    new Entry("/komaedit build \"coil(engine:exact,end:round)\" stone",
                            "Opciones de figura entre comillas"),
                    new Entry("/komaedit estimate <figura> [patrón] [escala] [espaciado] [grosor] [giro]",
                            "Bloques, chunks y tiempo sin construir nada"),
                    new Entry("/komaedit confirm [cancel]", "Lanza o descarta un Build grande en espera"),
                    new Entry("/komaedit bridge <altura> <ancho> [material]", "Puente arqueado"),
                    new Entry("/komaedit torii <altura> <grosor> <detalle> [material]", "Torii"),
                    new Entry("/komaedit lightningcircle <radio> <ramificación> [material]", "Aro de rayos")
            )),
            new Topic("tubes", "Tubos", List.of("tubos", "tubo", "sweep", "perfil"), List.of(
                    new Entry("/komaedit sweep <perfil> <radios> [patrón] [banderas]", "Tubo sin huecos con perfil"),
                    new Entry("/komaedit braid <radios> [patrón] [banderas]", "Trenza"),
                    new Entry("/komaedit noisetube <radios> [patrón] [banderas]", "Tubo erosionado"),
                    new Entry("/komaedit expr <radios> [banderas] [patrón] <fórmula>", "Figura por fórmula"),
                    new Entry("/komaedit sweep circle 2_6_2 stone -e round -w stairs",
                            "Ejemplo: radios con fotogramas, extremos redondos y escaleras")
            )),
            new Topic("edit", "Editar estructuras", List.of("editar", "doblar", "bend", "degradado", "gradient"), List.of(
                    new Entry("/komaedit bend [repeat|stretch] [-n upright|horizontal]", "Dobla el portapapeles por la trayectoria"),
                    new Entry("/komaedit gradient preset <paleta>", "Degradado de paleta sobre la selección"),
                    new Entry("/komaedit job status", "Progreso de la construcción en curso"),
                    new Entry("/komaedit job cancel", "Cancela la construcción en curso")
            )),
            new Topic("region", "Sobre lo construido", List.of("hiedra", "ivy", "envejecer", "weather", "retocar", "tweak"),
                    List.of(
                            new Entry("/komaedit ivy [estilo] [-l largo] [-b ramas 0–1] [-g cuelgue 0–1] [-n tallos] [-seed semilla]",
                                    "Hiedra viva desde los puntos P o el bloque que miras (alias hiedra)"),
                            new Entry("/komaedit ivy cancel", "Detiene la hiedra en curso"),
                            new Entry("/komaedit weather [intensidad 0–100] [clima] [-d derrumbe] [-v vida] [-seed semilla] [-agua]",
                                    "Envejece la selección (alias envejecer)"),
                            new Entry("/komaedit envejecer 70 nevado -d 40", "Ejemplo: ruina nevada con derrumbe"),
                            new Entry("/komaedit tweak deinfest|persist|waterlog|wax", "Retoques sobre la selección (alias retocar)")
                    )),
            new Topic("deform", "Deformar y suavizar", List.of("deformar", "superficie", "surface", "suavizar",
                    "smooth", "inflate", "inflar", "deflate", "smoothblocks", "cilindro", "esfera"),
                    List.of(
                            new Entry("/komaedit deform hexagonalize|noise|rotate|voronoialize|voronoialize2|voxelize …",
                                    "Deforma la selección (alias deformar)"),
                            new Entry("/komaedit deform cylindrify|spherify [-aflost] [-r radios] [-d profundidad]",
                                    "Proyecta la selección sobre un cilindro o una esfera nuevos"),
                            new Entry("/komaedit surface fuzzify|rockify|voronoify|noisify <radio> … [-c] [-e] [-m] [-t]",
                                    "Talla o hace crecer la superficie (alias superficie)"),
                            new Entry("/komaedit smooth <radios> [iteraciones] [sesgo]", "Suaviza la selección (alias suavizar)"),
                            new Entry("/komaedit inflate|deflate <radios>", "Engorda o adelgaza (alias inflar, desinflar)"),
                            new Entry("/komaedit smoothblocks <perfil> [radio] [sesgo]",
                                    "Suaviza con losas y escaleras; todas admiten -w Slabs|SlabsAndStairs")
                    )),
            new Topic("project", "Proyecto", List.of("proyecto", "copias", "backup"), List.of(
                    new Entry("/komaedit project status", "Recetas, instancias y espacio usado"),
                    new Entry("/komaedit project backup", "Copia de seguridad del proyecto"),
                    new Entry("/komaedit menu", "Paleta rápida (tecla K)")
            ))
    );

    private HelpTopics() {
    }

    public static Optional<Topic> find(String input) {
        String normalized = DidYouMean.fold(input);
        for (Topic topic : TOPICS) {
            if (DidYouMean.fold(topic.id()).equals(normalized)
                    || topic.aliases().stream().anyMatch(alias -> DidYouMean.fold(alias).equals(normalized))) {
                return Optional.of(topic);
            }
        }
        return Optional.empty();
    }

    /** Ids y alias de todos los temas, para el autocompletado y el «¿quisiste decir…?». */
    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (Topic topic : TOPICS) {
            names.add(topic.id());
            names.addAll(topic.aliases());
        }
        return names;
    }

    /** Devuelve el id del tema que corresponde a un nombre parecido, para proponerlo. */
    public static Optional<String> closest(String input) {
        return DidYouMean.best(input.toLowerCase(Locale.ROOT), names())
                .flatMap(HelpTopics::find)
                .map(Topic::id);
    }

    static String suggestionOf(String syntax) {
        String[] tokens = syntax.split(" ");
        StringBuilder prefix = new StringBuilder();
        for (String token : tokens) {
            if (token.contains("<") || token.contains("[") || token.contains("|")) {
                return prefix.toString();
            }
            prefix.append(token).append(' ');
        }
        return syntax;
    }
}
