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
            new Topic("spline", "Spline de ezEdits", List.of("ezspline", "ezsp", "splines"), List.of(
                    new Entry("/komaedit ezsp 2d <forma(parámetros)> <patrón> <radios> [banderas]",
                            "Perfil 2D: Circle, Square, SuperCircle, CirclesCircle, Pipe, Polygon, Rectangle, Star…"),
                    new Entry("/komaedit ezsp 3d <forma(parámetros)> <patrón> <radios> [banderas]",
                            "Beads, Cubes, Braids, Chainlink, Fishnet, Honeycomb, Oscillate, Rings, Scales, Noodles"),
                    new Entry("/komaedit ezsp basic|rope <patrón> <radios>", "Cilindro o cuerda"),
                    new Entry("/komaedit ezsp noise <paleta> <radios> [ruido] [profundidad] [-i expresión]",
                            "Cilindro mordido por un ruido"),
                    new Entry("/komaedit ezsp expression <paleta> <radios> [-z] [-o] <expresión>",
                            "Figura por expresión con paleta"),
                    new Entry("/komaedit ezsp structure <estructura> [radio] [-z]", "Estructura doblada por el recorrido"),
                    new Entry("/komaedit ezsp 3d Chainlink(M:99,N:99,E:0.6) clay 10 -q EXACT",
                            "Banderas: -p -q -r -s -t -n -e -w -h, como en ezEdits")
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
                            new Entry("/komaedit tweak deinfest|persist|waterlog|wax", "Retoques sobre la selección (alias retocar)"),
                            new Entry("/komaedit vines <máscara> <patrón> [porcentaje] [mínimo] [máximo] [-s semilla]",
                                    "Cuelga enredaderas bajo los bloques (alias enredaderas)"),
                            new Entry("/komaedit moss <patrón> [cantidad] [radios] [iteraciones] [-s semilla]",
                                    "Manchas de musgo en las caras de arriba (alias musgo)"),
                            new Entry("/komaedit slabmerge [máscara] [-b] [-t]", "Losas dobles a bloque entero (alias unirlosas)"),
                            new Entry("/komaedit statecycle <máscara> <propiedad> [pasos]",
                                    "Avanza una propiedad de estado (alias ciclarestado)")
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
                                    "Suaviza con piezas; todas admiten -w Slabs|SlabsAndStairs|SlabsAndStairs2D|Layers|SSW")
                    )),
            new Topic("texture", "Texturizado", List.of("textura", "texturas", "eztexture", "vidriera", "glass"), List.of(
                    new Entry("/komaedit texture ambient|curvature <máscara> <paleta> [radio] [brillo] [contraste] [-w]",
                            "Oclusión ambiental o curvatura (alias textura)"),
                    new Entry("/komaedit texture axisgradient <máscara> <paleta> [x|y|z] [-r]", "Degradado por eje"),
                    new Entry("/komaedit texture noise <máscara> <paleta> [ruido] [-z escala] [-s semilla]", "Ruido"),
                    new Entry("/komaedit texture cells <máscara> <paleta> <cantidad> [brillo] [contraste] [-s] [-r]",
                            "Celdas de Voronoi"),
                    new Entry("/komaedit texture flow <máscara> <paleta> [exposición] [iteraciones] [velocidad] "
                            + "[escalar] [ruido] [-m] [-g] [-f]", "Líneas de flujo sobre la superficie"),
                    new Entry("/komaedit texture pointlight|sunlight <máscara> <paleta> … [-l] [-o oclusión] [-r radio]",
                            "Luz puntual o del sol con sombras"),
                    new Entry("/komaedit texture blocklight <máscara> <paleta> [-v] [-s] [-l]", "Luz de bloque del mundo"),
                    new Entry("/komaedit texture blend|shift <paleta> [radio|desplazamiento]",
                            "Mezcla o desplaza los bloques de la paleta que ya hay"),
                    new Entry("/komaedit texture advanced <máscara> <paleta> <textura>",
                            "Combina modos: WeightedAverage(T1:Sun(),T2:Ambient())"),
                    new Entry("/komaedit glassgradient <inicio> [fin] <capas> [longitud] [calidad] [dirección] [-c] [-b] [-s]",
                            "Vidriera degradada de cristal tintado (alias vidriera)")
            )),
            new Topic("generators", "Generadores", List.of("generadores", "forma", "shape", "noisegen", "ruidogen",
                    "flowfield", "flujo", "flowline"), List.of(
                    new Entry("/komaedit shape cone|polydome|polygon|square|tetrahedron|torus <patrón> …",
                            "Forma suelta donde estás (alias forma)"),
                    new Entry("/komaedit shape torus prismarine 14 3 rounded_square -o", "Ejemplo: aro de pie hacia la mirada"),
                    new Entry("/komaedit noisegen heightmap|terrain|advanced <paleta> [ruido] …",
                            "Terreno con ruido en la selección (alias ruidogen)"),
                    new Entry("/komaedit noisegen heightmap water,sand,grass_block,stone,snow Perlin(F:0.03) -t",
                            "Ejemplo: colinas con lagos planos y nieve en capas"),
                    new Entry("/komaedit flowfield <paleta> <líneas|n%> [iteraciones] [velocidad] [escala] [ruido] …",
                            "Campo de flujo en la selección (alias flujo)"),
                    new Entry("/komaedit flowline <patrón> <longitud> [gravedad] [ruido] [-l líneas] [-s]",
                            "Línea de flujo hacia donde miras (alias lineaflujo)")
            )),
            new Topic("roof", "Tejados y lonas", List.of("tejado", "tejados", "roofs", "loft", "lona", "lonas",
                    "toldo"), List.of(
                    new Entry("/komaedit roof <tipo> [material] [banderas]",
                            "Tejado sobre la planta de la selección (alias tejado): gable, hip, mansard, shed, gablet, "
                                    + "dome, cone, spire, pagoda, flat"),
                    new Entry("/komaedit roof gable spruce_planks -p 1:1 -a 1 -c dark_oak_planks",
                            "Ejemplo: dos aguas a 45°, alero de 1 y caballete oscuro"),
                    new Entry("/komaedit roof hip deepslate_tiles -d -b 2",
                            "Ejemplo: planta sacada de los muros (L, T, U) y dos buhardillas por lado"),
                    new Entry("/komaedit roof shed ##Tejado -p 1:2 -lado s",
                            "Ejemplo: a un agua con degradado por altura de una paleta"),
                    new Entry("/komaedit roof <tipo> … -e", "Solo estima bloques, chunks y tiempo"),
                    new Entry("/komaedit loft <curva> <curva> [más curvas] [material] [banderas]",
                            "Superficie entre 2 a 6 recorridos guardados o «actual» (alias lona)"),
                    new Entry("/komaedit loft white_wool -i catenaria -comba 2",
                            "Ejemplo: lona colgante entre los 4 puntos marcados (dos tramos)"),
                    new Entry("/komaedit loft borde1 borde2 borde3 -i catmull -g 2 -w SlabsAndStairs",
                            "Ejemplo: vela suave entre tres curvas, de 2 de grosor y con piezas")
            )),
            new Topic("placement", "Colocación", List.of("colocar", "colocacion", "place", "array", "hilera",
                    "scatter", "dispersar", "estructuras"), List.of(
                    new Entry("/komaedit place <estructura> [primaria] [secundaria] [banderas]",
                            "Una estructura en tus pies (alias colocar)"),
                    new Entry("/komaedit array <estructura> [primaria] [secundaria] [-g hueco] [-q escalas] [-b]",
                            "Una tras otra por la trayectoria (alias hilera)"),
                    new Entry("/komaedit scatter <estructura> [primaria] [secundaria] [-n %] [-u] [-d] [-m] [-h región]",
                            "Repartidas por las superficies de la región (alias dispersar)"),
                    new Entry("/komaedit place Sh(S:Crystal,P:amethyst_block,O:BOTTOM) Aim Constant -s 6,18,6",
                            "Estructuras: Clipboard, Sc(N:regex), Sh(S:forma,P:patrón), Ex, TS e Ic(P:…)"),
                    new Entry("/komaedit array Cl C Tangential -g 2 -q 0.6,1.8",
                            "Alineaciones: Constant, Random, Noise, Aim, Upward, PlayerRelative, SurfaceNormal, "
                                    + "Expression, Tangential, Orthogonal y mezclas 10%C,2%R"),
                    new Entry("/komaedit scatter Cl C R -n 0.5 -d up -e 0.7 -o 0.8,1.3",
                            "Ejemplo: árboles del portapapeles en lo llano, girados y con tamaños distintos"),
                    new Entry("/komaedit pincel place|scatter|array <estructura> [primaria] [secundaria] [banderas]",
                            "Los mismos como pinceles: en el clic, alrededor del clic o por un trazo"),
                    new Entry("/komaedit pincel place Cl SurfaceNormal ViewDiff",
                            "ViewDiff: clic derecho dónde y clic izquierdo hacia dónde")
            )),
            new Topic("palette", "Paletas guardadas", List.of("paleta", "paletas", "palettes"), List.of(
                    new Entry("/komaedit palette list [all|default|mine]", "Paletas de serie y tuyas; pulsa una para verla"),
                    new Entry("/komaedit palette save <nombre> <paleta> [-f]", "Guarda una paleta para usarla con #nombre"),
                    new Entry("/komaedit palette fetch <world|selection|hotbar> <nombre> [largo] [-d dir] [-f]",
                            "Guarda los bloques de una fila, una selección de 1×1×N o la barra rápida"),
                    new Entry("/komaedit palette place <paleta> [dirección]", "Pone la paleta en fila delante de ti"),
                    new Entry("/komaedit palette swap <origen> <destino> [-a] [-s] [-f] [-b]",
                            "Cambia en la selección cada bloque del origen por el del destino"),
                    new Entry("/komaedit palette print <paleta> [-v]", "Muestra los bloques; pulsa para copiarlos"),
                    new Entry("/komaedit palette encode <paleta>", "Código kp1:… para compartirla"),
                    new Entry("/komaedit palette decode <código>", "Convierte un código kp1:… en bloques"),
                    new Entry("/komaedit palette delete <nombre>", "Borra una paleta guardada")
            )),
            new Topic("noise", "Ruidos guardados", List.of("ruido", "ruidos", "noises"), List.of(
                    new Entry("/komaedit noise list [all|default|mine]", "Ruidos de serie y tuyos"),
                    new Entry("/komaedit noise save <nombre> <ruido> [-f]", "Guarda un ruido para usarlo por su nombre"),
                    new Entry("/komaedit noise print <ruido>", "Muestra el tipo y los parámetros"),
                    new Entry("/komaedit noise delete <nombre>", "Borra un ruido guardado")
            )),
            new Topic("sel", "Selecciones", List.of("seleccion", "selecciones", "selection"), List.of(
                    new Entry("/komaedit sel save <nombre> [-f]", "Guarda la selección de WorldEdit"),
                    new Entry("/komaedit sel load <nombre> [-h]", "La recupera; -h la trae a tu posición"),
                    new Entry("/komaedit sel list [-g]", "Tus selecciones; -g las agrupa por tipo"),
                    new Entry("/komaedit sel delete <nombre>", "Borra una selección guardada"),
                    new Entry("/komaedit sel next [dirección] [hueco]", "Desplaza la selección su propio tamaño"),
                    new Entry("/komaedit sel here [pos1|pos2|center]", "Lleva ese punto de la selección a tus pies"),
                    new Entry("/komaedit sel invert", "Invierte el orden de los puntos"),
                    new Entry("/komaedit sel delpos2", "Quita el último punto de una selección convex o poly"),
                    new Entry("/komaedit sel encapsulate <máscara>", "Ajusta la selección a los bloques de la máscara"),
                    new Entry("/komaedit sel encapsulatenear <radio> <máscara>",
                            "Selecciona los bloques de la máscara que hay cerca"),
                    new Entry("/komaedit sel catenary <puntos> [comba] [down|up|left|right]",
                            "Selección convexa colgando como una cadena entre el principio y el final")
            )),
            new Topic("project", "Proyecto", List.of("proyecto", "copias", "backup"), List.of(
                    new Entry("/komaedit project status", "Recetas, instancias y espacio usado"),
                    new Entry("/komaedit project backup", "Copia de seguridad del proyecto"),
                    new Entry("/komaedit menu", "Paleta rápida (tecla K)"),
                    new Entry("/komaedit info", "Versión del mod y paletas y ruidos de serie"),
                    new Entry("/komaedit permisos [paquete]", "Qué nodos y paquetes de permisos tienes"),
                    new Entry("/komaedit reload", "Recarga la configuración y las paletas del servidor (admin)")
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
