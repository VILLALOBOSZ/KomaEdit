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
                    new Entry("/komaedit confirm [cancel]", "Aplica o descarta la vista previa o el Build grande en espera"),
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
                    new Entry("/komaedit ezsp structure <estructura> [radios] [-z] [-t grados] [-r inicio[,fin]]",
                            "Estructura doblada; 4,12,4 la escala por fotogramas, -t la retuerce y -r la gira"),
                    new Entry("/komaedit ezsp 3d Chainlink(M:99,N:99,E:0.6) clay 10 -q EXACT",
                            "Banderas: -p -q -r -s -t -n -e -w -h, como en ezEdits")
            )),
            new Topic("edit", "Editar estructuras", List.of("editar", "doblar", "bend", "degradado", "gradient",
                    "portapapeles", "clipboard", "escalar", "rotsprite"), List.of(
                    new Entry("/komaedit bend [repeat|stretch] [-n upright|horizontal]", "Dobla el portapapeles por la trayectoria"),
                    new Entry("/komaedit clipboard scale <factor> [nearest|scale2x|scale3x|rotsprite]",
                            "Agranda o encoge el portapapeles sin dientes de sierra (alias portapapeles escalar)"),
                    new Entry("/komaedit clipboard rotate <grados> [rotsprite|nearest]",
                            "Gira el portapapeles cualquier ángulo; escaleras y troncos giran con él (alias girar)"),
                    new Entry("/komaedit clipboard restore", "Vuelve al portapapeles de antes del último escalado o giro"),
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
                            new Entry("/komaedit tweak deinfest|persist|waterlog|wax [on|off]",
                                    "Retoques sobre la selección (alias retocar); wax encera cobre y carteles"),
                            new Entry("/komaedit vines <máscara> <patrón> [porcentaje] [mínimo] [máximo] [-s semilla]",
                                    "Cuelga enredaderas bajo los bloques (alias enredaderas)"),
                            new Entry("/komaedit moss <patrón> [cantidad] [radios] [iteraciones] [-s semilla]",
                                    "Manchas de musgo en las caras de arriba (alias musgo)"),
                            new Entry("/komaedit slabmerge [máscara] [-b] [-t]", "Losas dobles a bloque entero (alias unirlosas)"),
                            new Entry("/komaedit statecycle <máscara> <propiedad> [pasos]",
                                    "Avanza una propiedad de estado (alias ciclarestado)")
                    )),
            new Topic("sculpt", "Esculpir y modelar", List.of("esculpir", "modelar", "modelado", "soldar", "fundir",
                    "grietas", "rugosidad", "escultura"), List.of(
                    new Entry("/komaedit pincel sculpt draw|rock|weld|melt|roughen|shatter [radio] [cantidad] [banderas]",
                            "Pinceles de modelado en la herramienta (alias modelar; atajo: /komaedit pincel soldar 8)"),
                    new Entry("/komaedit pincel sculpt draw 6 3 -s 1",
                            "Esculpir: empuja la superficie 3 bloques por su normal (clic izquierdo: la hunde)"),
                    new Entry("/komaedit pincel sculpt rock 7 0.5 -b stone,andesite,tuff",
                            "Roca: masa rocosa que se funde con lo que hay (clic izquierdo: la talla)"),
                    new Entry("/komaedit pincel sculpt weld 8 0.35 -g 2",
                            "Soldar: une piezas y rellena rincones (clic izquierdo: fundir, que redondea aristas)"),
                    new Entry("/komaedit pincel sculpt roughen 10 1.5",
                            "Rugosidad: más caras a la vista sin cambiar apenas el volumen"),
                    new Entry("/komaedit pincel sculpt shatter 12 6 -e y -w 1 -d 3 -b air",
                            "Grietas de Voronoi (3D o en columnas con -e) sobre lo que hay, vacías o con -b"),
                    new Entry("/komaedit pincel sculpt <modo> … -f smooth -m #solid -p",
                            "Comunes: -f caída, -m máscara, -b material (de serie, el de alrededor), -n cambia los"
                                    + " clics, -p vista previa"),
                    new Entry("/komaedit sculpt set draw|rock|weld|melt|roughen|shatter <patrón|auto> <radio> …",
                            "Los mismos con la brocha de siempre; auto pone el material de alrededor")
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
                                    "Suaviza con piezas; todas admiten -w Slabs|SlabsAndStairs|SlabsAndStairs2D|Layers|SSW|Panes")
                    )),
            new Topic("texture", "Texturizado", List.of("textura", "texturas", "eztexture", "vidriera", "glass", "image",
                    "imagen"), List.of(
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
                            "Vidriera degradada de cristal tintado (alias vidriera)"),
                    new Entry("/komaedit image <archivo|https://…> [ancho] [paleta] [-d none|fs|bayer] [-s] [-o anclaje]",
                            "Imagen en bloques: vidrio, paneles, lana, hormigón, terracota o tu paleta (alias imagen)")
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
                            "Línea de flujo hacia donde miras (alias lineaflujo)"),
                    new Entry("/komaedit noisegen heightmap sand,grass_block,stone -m \"#existing !#near[water][3]\"",
                            "-m admite cualquier máscara de WorldEdit (entre comillas si lleva espacios)")
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
                    new Entry("/komaedit roof shed ##DustyBrown -p 1:2 -lado s",
                            "Ejemplo: a un agua con degradado por altura de una paleta"),
                    new Entry("/komaedit roof <tipo> … -e", "Solo estima bloques, chunks y tiempo"),
                    new Entry("/komaedit loft <curva> <curva> [más curvas] [material] [banderas]",
                            "Superficie entre 2 a 6 recorridos guardados o «actual» (alias lona)"),
                    new Entry("/komaedit loft white_wool -i catenaria -comba 2",
                            "Ejemplo: lona colgante entre los 4 puntos marcados (dos tramos)"),
                    new Entry("/komaedit loft borde1 borde2 borde3 -i catmull -g 2 -w SlabsAndStairs",
                            "Ejemplo: vela suave entre tres curvas, de 2 de grosor y con piezas")
            )),
            new Topic("terrain", "Terraformado", List.of("terreno", "terraformar", "rampa", "rio", "canon",
                    "erosion", "relieve", "sellos", "heightmap"), List.of(
                    new Entry("/komaedit pincel heightmap <sello> [radio] [altura] [elevar|bajar|aplanar] [paleta] "
                            + "[-i intensidad] [-r mirada|azar|grados] [-m máscara] [-c neutro] [-p]",
                            "Pincel de relieve con un sello en grises (alias relieve, sello)"),
                    new Entry("/komaedit pincel heightmap montana 24 30 natural -r azar",
                            "Ejemplo: montañas giradas al azar con hierba, piedra y nieve"),
                    new Entry("/komaedit terreno sellos", "Sellos de serie y PNG de config/komaedit/heightmaps"),
                    new Entry("/komaedit terreno rampa [plano|cono] [ancho] [transición] [paleta] [-t talud] [-u] [-e]",
                            "Rampa del primer al último punto de la trayectoria (o //pos1 → //pos2)"),
                    new Entry("/komaedit terreno rio|arroyo|canon|barranco [ancho] [profundidad] [paleta] "
                            + "[-o orilla] [-w laderas] [-t transición] [-f agua|lava|seco] [-n nivel] [-y] [-k] [-e]",
                            "Río o cañón por la trayectoria; el fondo y el agua nunca suben"),
                    new Entry("/komaedit terreno erosion hidraulica|termica|completa [iteraciones] [fuerza] [paleta] "
                            + "[-a ángulo] [-s semilla] [-m máscara] [-e]", "Erosión física de la selección"),
                    new Entry("/komaedit terreno pintar [paleta] [-m máscara]",
                            "Repinta la superficie de la selección por pendiente y altura"),
                    new Entry("/komaedit terreno paletas", "Paletas de superficie: natural, desierto, mesa, nevado…")
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
                            "ViewDiff: clic derecho dónde y clic izquierdo hacia dónde"),
                    new Entry("/komaedit place Cl -copias 11 -desplazar 0,1,0 -revolver 30 -pivote 4,0,0",
                            "Copias que suben y giran alrededor de un pivote: escalera de caracol (y -torcer)")
            )),
            new Topic("symmetry", "Simetría", List.of("simetria", "sym", "espejo", "radial", "ezsymmetry"), List.of(
                    new Entry("/komaedit symmetry on [modo]",
                            "Copia lo que pones y rompes; sin centro, lo fija donde estás (alias simetria, sym)"),
                    new Entry("/komaedit symmetry off", "Deja de copiar"),
                    new Entry("/komaedit symmetry mode x|z|xz|y|xzy|radial [brazos] [y]",
                            "Espejos en cualquier combinación, o radial alrededor de un eje vertical"),
                    new Entry("/komaedit symmetry center [here|selection|<x> <y> <z>] [half [ejes]]",
                            "Centro en tu bloque, en la selección o en un punto; half lo pone entre bloques"),
                    new Entry("/komaedit symmetry arms <2-16>", "Brazos del radial"),
                    new Entry("/komaedit symmetry worldedit [on|off]",
                            "Refleja también tus ediciones de WorldEdit y KomaEdit, en el mismo //undo"),
                    new Entry("/komaedit symmetry info", "Estado, centro y botones"),
                    new Entry("/komaedit sym on radial 8 y", "Ejemplo: rosetón de 8 brazos reflejado arriba y abajo")
            )),
            new Topic("nature", "Árboles y rocas", List.of("arboles", "arbol", "tree", "trees", "rocas", "roca",
                    "rock", "rocks", "naturaleza", "vegetacion"), List.of(
                    new Entry("/komaedit tree [especie|#preset] [altura] [banderas]",
                            "Árbol donde miras o en cada punto P (alias arbol): oak, spruce, birch, jungle, acacia, "
                                    + "cherry, willow, dark_oak, dead, palm, baobab, fantasy"),
                    new Entry("/komaedit tree oak 22 -g 1.3 -i 12 -h oak_leaves,azalea_leaves",
                            "-g grosor, -b ramas, -d densidad, -w ancho, -i inclinación, -c curvatura, -s semilla, "
                                    + "-t tronco, -h hojas, -r raíces, -m máscara, -x variación, -v vista previa"),
                    new Entry("/komaedit rock [tipo|#preset] [tamaño] [banderas]",
                            "Roca, peñasco o aguja hundida en el terreno (alias roca): boulder, crag, spire"),
                    new Entry("/komaedit rock crag 9 -u 0.5 -e 2",
                            "-b bloques de oscuro a claro, -u musgo, -a alto, -e alargado, -r rugosidad, -g grietas, "
                                    + "-c caras, -h hundido, -i inclinación, -s semilla, -m máscara, -x, -v"),
                    new Entry("/komaedit tree save|list|delete <nombre> [ajustes] [-f]",
                            "Presets: guarda unos ajustes y úsalos con #nombre (también rock save|list|delete)"),
                    new Entry("/komaedit pincel tree|rock <ajustes> [-x variación] [-p]",
                            "Pinceles: un árbol o una roca en cada clic derecho, distinto cada vez")
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
            new Topic("color", "Color y sombreado", List.of("colores", "colour", "parecidos", "shade",
                    "sombrear", "sombreado"), List.of(
                    new Entry("/komaedit color similar [bloque|#hex] [n] [-c] [-s] [-o] [-u]",
                            "Los n bloques de color más parecido; sin bloque, el de la mano o el que miras"),
                    new Entry("/komaedit color gradient <A> <B> [pasos] [-c] [-s] [-o] [-u] [-v variante] [-p nombre]",
                            "Degradado en OKLab sin repetir bloques; [otra] da alternativas (alias degradado)"),
                    new Entry("/komaedit color palette <selection|hotbar|paleta> [nombre] [-f]",
                            "Paleta de la selección, la barra rápida o una paleta, de oscuro a claro"),
                    new Entry("/komaedit color hotbar <bloques|undo>", "Lleva bloques a la barra rápida (creativo)"),
                    new Entry("/komaedit shade [selection|rampa|paleta] [-l luz] [-a 0–1] [-o 0–1] [-d 0–1] [-m máscara]",
                            "Sombrea la superficie según la luz, con oclusión y sombras (alias sombrear)"),
                    new Entry("/komaedit shade -l ne -d 0.5", "Ejemplo: luz del noreste con tramado"),
                    new Entry("/komaedit color gradient deepslate calcite 7 -c -o",
                            "Ejemplo: de oscuro a claro solo con cubos opacos")
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
            new Topic("capability", "Construir a mano", List.of("capacidad", "capacidades", "capabilities",
                    "axiom", "builder"), List.of(
                    new Entry("/komaedit capability", "Qué capacidades tienes activas (menú rápido: mantén Z)"),
                    new Entry("/komaedit capability <capacidad> [on|off]",
                            "replace, tweak, extrude, smear, angel, reach, fastplace o noupdates"),
                    new Entry("/komaedit capability angel|reach <bloques>",
                            "Distancia de colocar en el aire o del alcance largo"),
                    new Entry("/komaedit capability extrude <bloques>", "Bloques como mucho por capa al extruir"),
                    new Entry("/komaedit capability option family on|off",
                            "Reemplazar familia: con planchas de abeto, la escalera de roble pasa a ser de abeto"),
                    new Entry("/komaedit capability option match type|state",
                            "La capa de extruir sigue el mismo tipo de bloque o el mismo estado exacto"),
                    new Entry("/komaedit capability option flyspeed <x>", "Velocidad de vuelo (1 normal, 2.5…)")
            )),
            new Topic("preview", "Vista previa y progreso", List.of("vistaprevia", "previa", "progreso", "cancelar",
                    "confirmar"), List.of(
                    new Entry("/komaedit texture rockify stone -v",
                            "-v (o --preview) en cualquier orden: calcula y enseña sin colocar nada"),
                    new Entry("/komaedit confirm", "Aplica exactamente lo que ves; si la zona cambió, lo recalcula"),
                    new Entry("/komaedit cancel", "Descarta la vista previa o para la lectura, el cálculo y la colocación"),
                    new Entry("/komaedit preview always <bloques>",
                            "Previsualiza siempre las ediciones de N bloques o más"),
                    new Entry("/komaedit preview off", "Vista previa solo con -v"),
                    new Entry("/komaedit preview", "Tu preferencia y la vista previa que tengas esperando"),
                    new Entry("/komaedit job status", "Progreso de la colocación (también en la bossbar)")
            )),
            new Topic("utilidades", "Utilidades de obra", List.of("utilities", "texto", "text", "materiales",
                    "analyze", "gravedad", "gravity", "nieve", "snow", "agua", "drain", "biomas", "biome"), List.of(
                    new Entry("/komaedit text \"<texto>\" [material] [-f fuente] [-s tamaño] [-d grosor] [-b borde]"
                            + " [-o material del borde]", "Texto en bloques donde miras (alias texto)"),
                    new Entry("/komaedit text \"Año Nuevo\" ##Magma -s 12 -b 1 -o black_concrete -w SlabsAndStairs",
                            "Ejemplo: degradado, borde negro y losas y escaleras"),
                    new Entry("/komaedit text fonts", "Fuentes de serie y las de config/komaedit/fonts"),
                    new Entry("/komaedit analyze [-m máscara] [-n líneas]",
                            "Lista de materiales: coste en survival, pilas y cofres, CSV (alias materiales)"),
                    new Entry("/komaedit gravity [-a] [-m máscara]",
                            "Deja caer los bloques por columnas; -a solo lo que no se apoya (alias gravedad)"),
                    new Entry("/komaedit snow [capas] [-r radio] [-s sombra] [-h altura] [-p] [-m máscara]",
                            "Nieve en capas que sigue la pendiente (alias nieve)"),
                    new Entry("/komaedit drain [water|lava|all] [-k] [-m máscara]",
                            "Quita el agua o la lava y desanega (alias drenar)"),
                    new Entry("/komaedit waterlog [nivel|~] [-b] [-m máscara]",
                            "Inunda y anega por debajo de un nivel (alias inundar)"),
                    new Entry("/komaedit biome <bioma> [-v] [-m máscara]",
                            "Pinta el bioma de la selección (alias bioma)"),
                    new Entry("/komaedit pincel biome <bioma> [radio] [-v] [-m máscara]",
                            "Pincel de biomas; el clic izquierdo toma el bioma del bloque")
            )),
            new Topic("project", "Proyecto", List.of("proyecto", "copias", "backup"), List.of(
                    new Entry("/komaedit project status", "Recetas, instancias y espacio usado"),
                    new Entry("/komaedit project backup", "Copia de seguridad del proyecto"),
                    new Entry("/komaedit menu", "Paleta rápida (tecla K)"),
                    new Entry("/komaedit info", "Versión del mod y paletas y ruidos de serie"),
                    new Entry("/komaedit quiet [on|off]", "Silencia los mensajes informativos (se guarda; alias silencio)"),
                    new Entry("/komaedit wiki", "Dónde está la ayuda"),
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
