# Changelog

## Unreleased

- Añade la **Hiedra viva**: `/komaedit ivy` (alias `hiedra`) hace crecer una enredadera desde los puntos P o desde el bloque que miras sobre lo que ya hay construido. Las puntas se pegan a las caras enteras, trepan muros, cruzan tejados, se ramifican, cuelgan con gravedad de dinteles y aleros y florecen; cada bloque lleva sus caras y propiedades de vanilla (`vine`, `glow_lichen`, `moss_carpet`, hojas persistentes, `cave_vines` con bayas, `spore_blossom`).
- Añade cuatro estilos (`hiedra`, `florido`, `selvatico`, `cueva`) y las banderas `-l` (largo), `-b` (ramas), `-g` (cuelgue), `-n` (tallos) y `-seed`; con la misma semilla sale la misma hiedra. Captura repartida por ticks, cálculo en un worker, una sola edición deshacible con //undo y sin pisar bloques puestos mientras crecía.
- Registra el avance por ticks de la captura del entorno, que no llegaba a ejecutarse.
- Añade la figura **Expresión** (`expression`): una fórmula del jugador en espacio de tubo (`x`, `y` de -1 a 1 en la sección, `z` a lo largo en radios, más `t`, `r`, `a` y `n`) decide qué bloques se colocan (salida > 0) y, con **Patrón figura**, su material (valor en `(0, 1]`).
- Añade un compilador de fórmulas propio, sin bucles ni estado compartido: operadores `+ - * / % ^`, comparaciones, `&& || !`, ternario, 26 funciones (con `noise` determinista), definiciones `nombre = valor;`, nombres sensibles a mayúsculas, límites de 256 caracteres, 16 definiciones, 40 niveles y 400 operaciones, y errores en español con la posición. Evalúa 6–12 veces más rápido que el motor de WorldEdit 7.3.8 con las mismas fórmulas.
- Acota el alcance de la fórmula por análisis (`r <= 1 && …`) para no recorrer las esquinas de la sección: `r <= 1` cuesta lo mismo que el perfil círculo.
- Añade cinco fórmulas de serie (ventanas, raíles, espiral, celosía y costillas), una fila **Fórmula** y un campo editable en el Studio, y `preset:` en las expresiones de figura.
- Añade `/komaedit expr` y `/komaedit expresion <radios> [banderas] [patrón] <fórmula…>`, con nombre de serie y `n` pegado (`ventanas8`).
- Guarda la fórmula propia como texto en recetas, instancias y paquetes (formato de receta 4) y la envía por red; los formatos 1–3 se leen sin migración.
- Añade el **motor exacto** opcional (`engine:exact`, fila **Motor** del Studio) para Chainlink, Rings, Beads, Oscillate, Red, Honeycomb, Knots y Coil: cada figura se evalúa como función implícita en espacio de tubo, sin huecos, con las proporciones del motor clásico y heredando extremos, normales, radios por fotogramas, torsión y la curva de modificadores. El motor clásico sigue siendo el predeterminado y no cambia ninguna figura existente.
- Las figuras con motor exacto también admiten **losas y escaleras** (`smooth`, `coverage`, filas **Piezas** y **Cobertura**).
- **Patrón figura** usa el valor implícito de las figuras con motor exacto (eslabones, cuentas, familias de hilos y puntales por franjas de paleta).
- Añade la figura **Red** (`fishnet`): dos familias de hilos helicoidales a ±45° con celdas cuadradas a cualquier escala.
- El comando general acepta expresiones de figura entre comillas, por ejemplo `/komaedit build "coil(engine:exact,end:round)" stone`.
- Añade pruebas JUnit del motor exacto: almas de hilo sin huecos en rectas, diagonales y curvas, cinta sin agujeros, cuentas cerradas, determinismo, volumen frente al clásico y opción por defecto sin cambios.

## 1.2.0 (estable, 2026-10-17)

- Añade **losas y escaleras en la superficie** de las figuras de tubo (`sweep`, `braid`, `noisetube`): cada bloque del borde se mide por octantes con la función implícita de la figura y se sustituye por la losa o escalera orientada que mejor encaja, o se rellena un hueco casi lleno.
- Añade las banderas `-w slabs|stairs` y `-c <cobertura>` a los comandos de tubo, las opciones de receta `smooth` y `coverage`, y las filas **Piezas** y **Cobertura** al Studio.
- Calcula las esquinas de las escaleras con la misma regla que Minecraft para que el mundo construido coincida con el holograma.
- Elige la pieza del mismo material que el patrón (por nombre, con tabla para los casos sin convención y, si no existe, por color de mapa), también con patrones aleatorios y degradados.
- Dibuja las losas y escaleras en el holograma con su forma real, por medios bloques.
- Corrige que una sola receta o instancia ilegible (p. ej. de la 4.7, con una figura retirada) desactivase KomaEdit en todo el mundo con «The KomaEdit project is not initialized for this player»: ahora se omite con un aviso en `latest.log` y el proyecto sigue abierto.
- Añade el doblado de estructuras: `/komaedit bend` (alias `doblar`) curva el portapapeles de WorldEdit a lo largo de la trayectoria, repitiendo copias enteras o estirando una sola, sin huecos por fuera de las curvas y con escaleras, troncos y vallas girados para seguirla.
- Añade los modos de Kitbash **Doblar** y **Estirar**, que meten el módulo doblado en la instancia de la receta.
- En modo `upright` el módulo se asocia a la vertical de cada punto, así que en las cuestas los muros y postes quedan a plomo.

## 1.1.0 (estable, 2026-10-17)

Primera publicación estable desde `1.0.0`; recoge todo lo integrado en `staging` sobre `4.8.0-alpha.1`.

- Añade el motor de **espacio de tubo**: voxelizado inverso por ladrillos de 8³, árbol de esferas para localizar el eje y figuras implícitas estancas por construcción.
- Añade **Perfil 2D** (`sweep`) con diez perfiles (círculo, cuadrado, rombo, redondeado, polígono, estrella, flor, cuerda, elipse y rectángulo), relleno macizo o hueco y pared cerrada por morfología.
- Añade **Trenza** (`braid`) y **Tubo erosionado** (`noisetube`), este último sin fragmentos flotantes.
- Añade trayectoria Kochanek–Bartels con corrección por cuerda, marcos de mínima rotación con cierre de lazos y normales `consistent`, `horizontal` y `upright`.
- Añade progresión de radio por fotogramas (`2,0.3:12,2`), extremos `flat`, `soft`, `spike`, `round` y `cube`, torsión por diámetro y giro inicial/final.
- Aplica escala, espaciado y torsión de los modificadores dentro del espacio de tubo, sin deformación posterior ni puentes.
- Añade `/komaedit sweep`, `/komaedit perfil`, `/komaedit braid`, `/komaedit trenza`, `/komaedit noisetube` y `/komaedit erosion` con banderas `-e -n -t -r -p -q -s -d -a -x -k -f -hollow -fill`.
- Añade filas de Perfil, Relleno, Extremos y Normales al Studio para las figuras de tubo.
- Permite nombres simbólicos en expresiones de figura (`profile:star`, `end:round`, `hollow:true`).
- Amplía de 16 a 32 los parámetros avanzados por receta.
- Persiste recetas, instancias y bitácoras dentro de cada mundo, separadas por UUID, en lugar de eliminarlas al desconectarse.
- Migra de forma no destructiva los datos heredados de `config/komaedit/` al primer acceso al mundo.
- Añade escrituras atómicas, recuperación de temporales, respaldos `.bak` y checksum SHA-256 para la geometría de instancias.
- Añade `/komaedit project status` y `/komaedit project backup`, con retención configurable de backups ZIP.
- Permite reabrir una instancia desde el Studio recuperando también su trayectoria editable.
- Registra el progreso de jobs de larga duración y marca operaciones interrumpidas después de un cierre inesperado.
- Añade permisos granulares para trayectoria, figuras, recetas, instancias, selección, pintura, clipboard, historial y backups.
- Añade límites configurables por operación y para jobs concurrentes en `config/komaedit/config.json`.
- Internacionaliza en español e inglés la navegación, estados y acciones principales de Procedural Studio.
- Añade pruebas JUnit para almacenamiento atómico, recuperación, posiciones empaquetadas y curvas de interpolación.
- Permite recuperar manualmente la publicación de una prerelease de `staging` cuando GitHub no emite el evento automático, sin habilitar publicaciones manuales de `main`.
- Añade la figura procedural **Rayos**, con núcleos quebrados deterministas, anclajes exactos y ramificación configurable de `2` a `8`.
- Añade bifurcaciones secundarias en densidades altas y una corona exterior especial para trayectorias cerradas, manteniendo el centro completamente hueco.
- Añade `/komaedit lightning`, `/komaedit rayos`, `/komaedit lightningcircle` y `/komaedit circulorayos`, con materiales eléctricos predeterminados y compatibilidad con patrones de fuego.
- Añade la figura procedural **Telaraña**, con nudos gruesos en P1 y P2 unidos por un puente fino, ahusado y ligeramente curvado.
- Convierte tres o más puntos en anclajes radiales ordenados, con radios rectos y vueltas cóncavas adaptadas a su distribución.
- Añade controles de apertura, separación, grosor y espiral, holograma exacto, degradados y `/komaedit web`.
- Elimina la categoría, generadores, comandos, materiales secundarios y hologramas de Assets naturales.
- Mueve Pilar monumental y Pilares arqueados exclusivamente a la categoría Estructuras, sin duplicarlos en Figuras.
- Conserva sin cambios las figuras procedurales existentes y su geometría.
- Separa el versionado de desarrollo (`mod_version`) del versionado estable (`release_version`).
- Añade automáticamente el SHA corto a la versión interna y al JAR de cada build de desarrollo.
- Publica cada merge a `staging` como prerelease `komaedit-staging-<versión>-<sha>` en GitHub Releases.
- Publica `main` únicamente como `komaedit-<release_version>`, sin nombres de canal, alpha ni SHA.
- Impide reutilizar una versión estable de `main` para un commit diferente.

## 4.7.0-alpha.1

- Añade la categoría lateral responsive **Assets** al Procedural Studio.
- Añade **Árbol épico**, generado desde un solo P1 con raíces, contrafuertes, tronco ahusado, ramas Bézier, ramificaciones, copa orgánica y lianas.
- Implementa crecimiento inteligente para árboles: analiza 16 sectores cercanos y orienta tronco, ramas y copa hacia el espacio libre.
- Ejecuta la misma adaptación ambiental en cliente y servidor; el holograma usa una caché ligera y el Build vuelve a validar el entorno.
- Añade **Roca monumental** con superelipsoides erosionados, grietas, estratos, musgo y piedras satélite.
- Añade **Tronco ancestral** con longitud ajustable, curvatura, taper, extremos huecos, raíces, ramas rotas y crecimientos.
- Añade **Raíces antiguas** con tocón retorcido, raíces radiales, arcos naturales, vegetación y elementos colgantes.
- Permite construir todos los assets con un único punto P1; P2 es opcional y puede orientar los assets alargados.
- Amplía el formato de receta a v3 con dos materiales independientes: principal y acento.
- Integra ambos materiales con catálogo completo de bloques, red, guardado, paquetes, instancias, actualización y protección de cambios manuales.
- Clasifica cada bloque por zona geométrica; tronco/hojas y piedra/musgo nunca se mezclan aleatoriamente.
- Añade controles y límites contextuales para tamaño, altura, longitud, detalle, relieve y orientación.
- Añade `/komaedit asset tree|boulder|log|roots <altura/largo> <tamaño> <detalle> [principal] [acento]`.
- Hace compacto el rail lateral automáticamente en ventanas de poca altura para conservar visibles todas las categorías.
- Conserva sin cambios las escamas, figuras, estructuras, degradados y torii existentes.

## 4.6.0-alpha.3

- Sustituye la curva central del torii por una interpolación recta entre las alturas de ambos pilares.
- Elimina la ondulación y la elevación artificial del centro cuando P1 y P2 están en terrenos desnivelados.
- Conserva únicamente la curvatura tradicional de los aleros en los extremos de la corona.
- Añade la categoría lateral **Estructuras** con accesos dedicados a Pilar monumental, Puente arqueado y Torii monumental.
- Separa las estructuras de la cuadrícula general de figuras para agilizar su selección.
- Corrige el cálculo del área desplazable del inspector para que controles inferiores como **Altura** no queden ocultos bajo el panel **Receta**.
- Conserva sin cambios la geometría de escamas y las demás figuras.

## 4.6.0-alpha.2

- Conserva la misma altura local en ambos pilares del torii aunque P1 y P2 estén en niveles distintos.
- Sustituye la coronación horizontal común por vigas adaptativas que conectan las dos alturas mediante una curva suave.
- Añade una elevación central proporcional al desnivel para que el techo se arquee de forma natural.
- Mantiene tangentes suaves junto a cada pilar y evita cambios bruscos de dirección.
- Aplica la adaptación al travesaño inferior, viga intermedia y todas las capas de la corona superior.
- Reposiciona ménsulas y placa central según las alturas deformadas.
- Conserva sin cambios el comportamiento del torii sobre terreno nivelado y todas las demás figuras.

## 4.6.0-alpha.1

- Añade **Torii monumental**, un portal japonés gigante construido entre las bases P1 y P2.
- Genera columnas inclinadas, basas escalonadas, collarines, doble sistema de travesaños, corona multicapa, aleros curvos, ménsulas y placa central.
- Mantiene las vigas niveladas cuando P1 y P2 se encuentran a alturas distintas.
- Amplía la altura máxima de recetas monumentales desde 128 hasta 256 bloques.
- Añade controles contextuales **Grosor**, **Detalle**, **Relieve**, **Curvatura** y **Altura**.
- Hace que la altura del torii avance en pasos de cuatro bloques dentro del Studio.
- Añade `/komaedit torii <altura> <grosor> <detalle 2–8> [patrón]`.
- Evita reintentos infinitos del holograma cuando P1/P2 o los parámetros producen un error geométrico determinista.
- Integra el torii con holograma exacto, materiales WorldEdit, degradados locales, recetas y modificadores.
- Conserva sin cambios escamas, mandalas, nubes, pilares, arcos y las demás figuras.

## 4.5.0-alpha.1

- Añade **Mandala radial**, una figura 3D orientada por P1→P2 con núcleo, anillos ondulados, pétalos sólidos, filigrana, radios y corona exterior.
- Añade controles contextuales **Tamaño**, **Simetría**, **Relieve** y **Giro** para mandalas.
- Añade `/komaedit mandala <tamaño> <simetría 2–8> [patrón]`.
- Activa un gizmo XYZ mundial para seleccionar nodos y moverlos de forma restringida sobre X, Y o Z.
- Escala las flechas del gizmo con la distancia de cámara y aplica movimiento local optimista para eliminar retrasos visuales.
- Evita que el holograma quede bloqueado después de una excepción mediante reintentos automáticos con espera progresiva.
- Conserva el último holograma válido mientras se calcula o recupera el siguiente.
- Amplía la distancia del holograma desde 512 hasta 4096 bloques.
- Detecta resultados geométricos vacíos y los trata como un fallo recuperable.
- Conserva sin cambios escamas, pilares, arcos, nubes y las demás figuras.

## 4.4.0-alpha.4

- Alarga los bancos nubosos antes de aplicar cada separación.
- Organicidad baja genera aproximadamente 4–6 masas por banco y Organicidad alta puede producir unas 6–10.
- Conserva el solapamiento, la erosión, la variación determinista y la separación ajustable de `alpha.3`.
- Mantiene sin cambios escamas, pilares, arcos y las demás figuras.

## 4.4.0-alpha.3

- Distribuye las nubes en bancos naturales de 2–5 masas solapadas en lugar de generar una única cadena continua.
- Añade el control independiente **Separación**, medido aproximadamente en bloques libres entre bancos.
- Varía cada hueco alrededor de ±20% y altera el tamaño de cada banco mediante una secuencia determinista para evitar repeticiones mecánicas.
- Mantiene **Organicidad** como control separado para conservar el detalle y los lóbulos de cada nube.
- Actualiza en tiempo real el holograma cuando cambia la separación desde Procedural Studio.
- Amplía `/komaedit cloud` y `/komaedit nube` con la sintaxis opcional `<separación> [patrón]`.
- Conserva sin cambios la geometría de escamas, pilares, arcos y las demás figuras.

## 4.4.0-alpha.2

- Añade **Nube orgánica**, una figura volumétrica continua orientada por el recorrido de puntos.
- Construye masas principales sólidas, lóbulos laterales, coronas superiores y estelas afinadas sin recurrir a formas 2D.
- Añade erosión superficial mediante ruido tridimensional determinista de escala amplia y detalle fino.
- Adapta los controles del Studio a **Tamaño**, **Organicidad**, **Densidad** y **Giro** al seleccionar la nube.
- Integra la figura con recetas, deformadores, patrones WorldEdit, degradados locales y holograma exacto.
- Añade `/komaedit cloud <tamaño> <organicidad 2–8> [patrón]` y el alias `/komaedit nube`.
- Conserva sin cambios los generadores de pilares, arcos, escamas y el resto de figuras existentes.

## 4.4.0-alpha.1

- Añade **Pilar monumental**, una figura sólida orientada por P1→P2 con base escalonada, fuste con éntasis, nervaduras, anillos ornamentales, contrafuertes y capitel.
- Añade **Pilares arqueados**, que levanta dos pilares desde P1 y P2 y los conecta con un arco profundo, dovelas, clave central, vigas superiores y refuerzos diagonales.
- Integra ambas figuras en Procedural Studio, recetas, holograma exacto, deformadores, materiales sólidos y degradados por figura.
- Adapta los controles de Figura a **Ancho**, **Detalle**, **Relieve**, **Giro** y **Altura** según el generador seleccionado.
- Amplía dinámicamente el desplazamiento del panel Figura para que los nuevos controles sigan accesibles en resoluciones pequeñas.
- Añade los comandos rápidos `/komaedit pillar` y `/komaedit pillararches` con patrones WorldEdit opcionales.
- Mantiene intactas la geometría, el muestreo y el sombreado de `scales` y `spiked_scales`.

## 4.3.0-alpha.3

- Elimina el retorno accidental al degradado global en bloques interiores o reconstruidos de **Scales**.
- Asigna las zonas superpuestas a la escama exterior dominante para impedir parches claros y cambios de propietario dentro de una misma pieza.
- Calcula el contorno mediante distancia física en bloques, evitando que el borde ocupe grandes sectores en las puntas estrechas.
- Reduce y endurece la transición del labio y los laterales para conservar un cuerpo oscuro con un borde fino y definido.

## 4.3.0-alpha.2

- Sustituye las bandas longitudinales de **Scales** por coordenadas locales independientes para cada escama.
- Reconstruye la fila, columna, raíz, cuerpo, laterales y labio de la escama sin modificar su geometría.
- Mantiene el cuerpo en la zona oscura de la paleta y concentra los materiales cálidos o luminosos en laterales y borde frontal.
- Compensa escala continua, torsión y espaciado variable para que el material permanezca adherido a cada escama deformada.
- Actualiza **Frente/reverso**, **Relieve** y **Filas alternas** para trabajar con las piezas reales de Scales y no con anillos completos.

## 4.3.0-alpha.1

- Añade métricas locales **Patrón figura**, **Frente/reverso**, **Relieve** y **Filas alternas**.
- Calcula el periodo real de cada figura para reiniciar el degradado en cada motivo, en vez de usar únicamente coordenadas globales.
- En escamas, combina la fase raíz-frente de cada fila con la altura del relieve para separar base, cuerpo y labio.
- Añade distribuciones específicas para paneles, anillos, cadenas, espirales, celdas, costillas, coronas y demás figuras.
- Reduce el coste de Exposición, Cavidades, Bordes y Curvatura mediante un índice hash de posiciones.
- Evita crear el índice geométrico cuando la métrica seleccionada no lo necesita.
- Aumenta la precisión del marco usado para deformaciones continuas mediante muestras cada `0.75` bloques.
- Amplía dinámicamente la reconstrucción entre vóxeles cuando la escala supera `100%`, conservando mejor escamas y detalles.
- Calcula dinámicamente la altura desplazable del panel Material según paletas y métricas disponibles.
- Mantiene el holograma activo al cerrar `K` y continúa actualizándolo al añadir puntos mediante `P`.
- Desplaza las caras del holograma ligeramente fuera del bloque para que siga visible después de construir.
- Conserva sin cambios `ShapeGenerator` y `SampledPath`; las figuras neutrales mantienen sus coordenadas originales.

## 4.2.0-alpha.2

- Fija el holograma al mundo componiendo la matriz de cámara completa antes de dibujar cada sección.
- Evita que el holograma se desplace o rote con la vista del jugador.
- Conserva los puntos después de Build; ahora solo se eliminan mediante **Limpiar** o `/komaedit path clear`.
- Renombra visualmente Ritmo como **Fusión procedural** para describir mejor su función.
- Mantiene completa la figura principal y superpone una figura secundaria cada N tramos.
- Deforma por separado la figura principal y la secundaria antes de fusionarlas.
- Evita conexiones cruzadas entre ambas geometrías cuando se usa giro, torsión, escala o espaciado variable.
- Mantiene intactos los algoritmos y coordenadas de todas las figuras base.

## 4.2.0-alpha.1

- Añade un holograma 3D de bloques que reproduce la geometría futura con el mismo motor usado por Build.
- Actualiza el holograma automáticamente al cambiar puntos, figura, escala, separación, grosor, giro o deformadores.
- Sustituye partículas por mallas persistentes agrupadas en secciones de 16×16×16 bloques.
- Renderiza únicamente caras expuestas, aplica distancia y frustum culling y limita las subidas a GPU por frame.
- Calcula la geometría en un worker de baja prioridad con debounce, generaciones cancelables y descarte de resultados obsoletos.
- Añade cancelación cooperativa a las operaciones geométricas largas sin cambiar las coordenadas generadas.
- Libera mallas y memoria al construir, cerrar el Studio o desconectarse del mundo.
- Evita regenerar el holograma mientras Build consume y reinicia la trayectoria.
- Conserva la colocación asíncrona, compacta y por lotes de las versiones anteriores.
- Surface y Kitbash muestran la figura base y se identifican como ajustes completados por el servidor al construir.

## 4.1.0-alpha.3

- Corrige la reutilización accidental de puntos entre builds consecutivos.
- Cada Build captura y consume su trayectoria antes de comenzar el siguiente brush.
- Evita que una figura nueva conecte su primer punto con el último recorrido de la figura anterior.
- Limpia también el estado abierto/cerrado del path y actualiza inmediatamente el holograma.
- Renombra `Modificar` como `Deformar` y aclara que es una función opcional que no edita los puntos del recorrido.
- Mantiene intactas las fórmulas originales de todas las figuras y escamas.

## 4.1.0-alpha.2

- Impide que una figura nueva herede opciones, ritmo, acento, taper o torsión de la figura anterior.
- Restablece automáticamente los modificadores cuando se cambia de figura.
- Añade el botón `Restablecer modificadores` y una explicación visible dentro del panel Modificar.
- Sustituye la generación por segmentos independientes por una deformación continua de una sola geometría.
- Evita reinicios de fase, nudos gigantes y acumulaciones en uniones de cadenas, anillos y patrones repetidos.
- Convierte el ritmo procedural en una mezcla por ventanas sobre geometrías completas, con transición en los bordes.
- Limpia recetas, instancias y borradores al entrar o salir del mundo.
- Mantiene los paquetes `.krecipe` exportados como la única persistencia intencional.
- Conserva sin cambios el generador original de escamas y todas las figuras base.

## 4.1.0-alpha.1

- Añade curvas independientes de escala y espaciado, easing y torsión acumulada por receta.
- Añade ritmo procedural para alternar una figura de acento a intervalos configurables.
- Añade conformado del path a suelo, techo o superficie cercana con alcance y offset.
- Añade sockets Kitbash basados en el clipboard de WorldEdit, orientados con la trayectoria.
- Añade métricas de material por exposición, cavidad, bordes, curvatura y ruido orgánico.
- Añade QA geométrico opcional para cerrar microhuecos y eliminar fragmentos aislados.
- Añade instancias persistentes que pueden actualizarse o eliminarse desde Procedural Studio.
- Protege los bloques editados manualmente al actualizar o retirar una instancia.
- Captura los estados colocados progresivamente durante el trabajo por lotes para evitar pausas finales.
- Añade exportación e importación de recetas mediante paquetes `.krecipe`.
- Migra el formato de receta a versión 2 manteniendo lectura de recetas versión 1.
- Mantiene intactos `ShapeGenerator`, las escamas originales y el resto de figuras existentes.

## 4.0.0-alpha.3

- Integra las coordenadas del path en una tarjeta compacta específica del Procedural Studio.
- Evita solapamientos con la barra superior, la paleta lateral y el inspector redimensionable.
- Prioriza los nodos recientes y resume los anteriores cuando la trayectoria es larga.

## 4.0.0-alpha.2

- Elimina por completo el shader de desenfoque del Procedural Studio.
- Mantiene el mundo y todos los controles nítidos mientras el panel está abierto.

## 4.0.0-alpha.1

- Añade el modelo versionado `ProceduralRecipe`.
- Añade guardado atómico y catálogo de recetas por jugador.
- Añade protocolo cliente/servidor para construir, guardar, eliminar y sincronizar recetas.
- Añade material sólido o degradado aplicado directamente al plan geométrico.
- Sustituye el acceso de `K` por Procedural Studio, una interfaz flotante, redimensionable y responsive.
- Añade biblioteca completa y buscable de bloques.
- Conserva sin cambios todas las geometrías existentes, incluido el comportamiento especial de escamas.
- Mantiene cálculo asíncrono, colocación por lotes e historial WorldEdit.
