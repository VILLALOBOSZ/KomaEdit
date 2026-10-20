# KomaEdit 4.8 Procedural Studio

## Documentación del mod

- [Catálogo funcional completo](docs/MOD_DOCUMENTATION.md)
- [Arquitectura](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Flujo de contribución](CONTRIBUTING.md)

KomaEdit es un complemento Fabric + WorldEdit para crear **detalle procedural reutilizable** sobre trayectorias 3D. No intenta sustituir las herramientas de selección masiva de WorldEdit ni el modelado general de Axiom: se concentra en convertir un recorrido en una pieza estilizada, repetible y editable mediante recetas.

## Flujo de trabajo

1. Pulsa `K` para abrir **Procedural Studio**.
2. En **Trayectoria**, añade puntos con clic sobre el mundo o con la tecla `P`.
3. En **Figura**, selecciona una gramática 3D; usa **Estructuras** para arquitectura monumental orientada por puntos.
4. En **Modificadores**, añade taper, torsión, fusión procedural, conformado, sockets Kitbash y reparación geométrica.
5. En **Material**, elige un bloque del registro completo o un degradado guiado por la forma.
6. Pulsa **Construir receta**. La geometría se calcula fuera del hilo principal y se coloca por lotes; la trayectoria permanece disponible para probar otras configuraciones.
7. Abre **Instancias** para actualizar o retirar la pieza sin destruir bloques modificados manualmente.
8. Asigna un nombre y pulsa **Guardar** para reutilizar o exportar la configuración.

Las recetas, instancias y bitácoras de operación se guardan dentro de cada mundo, separadas por UUID de jugador. KomaEdit migra una sola vez los datos antiguos de `config/komaedit/` y conserva copias de seguridad atómicas para reducir el riesgo de corrupción tras un cierre inesperado.

## Procedural Studio

- Interfaz flotante: mantiene visible el mundo y no aplica blur ni pausa el juego.
- Inspector derecho redimensionable y contenido con desplazamiento para resoluciones pequeñas.
- Categoría lateral **Estructuras** separada, con Pilar, Pilares arqueados, Puente arqueado y Torii.
- Navegación con `WASD`; clic derecho y arrastre para mover la cámara.
- Trayectoria con raycast de hasta 4096 bloques cargados.
- Nodos colocables y arrastrables desde el Studio.
- Cada Build conserva sus puntos; usa **Limpiar** o `/komaedit path clear` cuando quieras comenzar otro recorrido.
- Biblioteca buscable con todos los bloques registrados.
- Catálogo de recetas sincronizado entre cliente y servidor.
- Catálogo de instancias construidas con actualización y retirada desde la misma interfaz.
- `Ctrl+Z`, `Ctrl+Y` y `Ctrl+Shift+Z` para deshacer o rehacer mediante WorldEdit.

## Holograma de construcción

- Aparece automáticamente al tener suficientes puntos y una figura seleccionada.
- Representa cada posición de bloque que generará la receta normal; no modifica el mundo.
- Se recalcula al mover nodos o ajustar la figura y mantiene visible el resultado anterior mientras prepara el nuevo.
- Usa mallas persistentes por secciones, dibuja solo caras expuestas y descarta secciones fuera de cámara o a más de 4096 bloques.
- Limita el trabajo de GPU por frame para evitar congelamientos al previsualizar piezas grandes.
- El cálculo se ejecuta en un worker de baja prioridad con `180 ms` de debounce y cancelación cooperativa.
- Al usar **Surface** o **Kitbash**, el holograma enseña exactamente la figura base y avisa que la proyección o el clipboard se completarán en el servidor al pulsar Build.
- Las caras quedan ligeramente por fuera del bloque real, por lo que el holograma continúa visible después de Build sin desactivar el depth test.
- Cerrar `K` no elimina el preview: permanece visible y se actualiza cuando se añaden puntos con `P`.
- El holograma se libera al limpiar la trayectoria, desconectarse o salir del mundo.
- Conserva el último holograma válido mientras calcula el siguiente.
- Si una generación falla, libera la clave bloqueada y reintenta automáticamente con espera progresiva.

## Modificadores procedurales

La sección **Deformar** es opcional y no edita los puntos ni suaviza las curvas espaciales del brush. Únicamente altera progresivamente la figura ya escogida. Para construir figuras normales puede ignorarse por completo o mantenerse en sus valores neutrales.

- **Escala inicial/final:** modifica el grosor transversal desde el primer punto hasta el último. `100% → 100%` no deforma; `160% → 70%` crea un inicio grande que se reduce progresivamente. El marco se muestrea cada `0.75` bloques y reconstruye separaciones producidas por escalas grandes.
- **Espaciado inicial/final:** comprime o estira el patrón a lo largo del recorrido. `100%` es neutral; valores bajos juntan repeticiones y valores altos las separan.
- **Curva o easing:** controla cómo ocurre la transición. Lineal mantiene velocidad constante; Suave evita cambios bruscos al inicio y al final.
- **Torsión total:** gira gradualmente toda la sección alrededor del path. `360` representa una vuelta completa y `-360` invierte el sentido.
- **Fusión procedural:** mantiene completa la figura principal y superpone una figura secundaria cada N tramos. Ambas se deforman por separado para soportar giro, torsión, escala y espaciado variable sin conexiones cruzadas.
- **Conformado:** proyecta el recorrido al suelo, techo o superficie sólida más cercana.
- **Kitbash sockets:** inserta el clipboard de WorldEdit en extremos, nodos o intervalos y lo orienta con la trayectoria, o lo dobla por todo el recorrido (**Doblar** / **Estirar**). Para doblar solo el módulo: `/komaedit bend [repeat|stretch] [-a x|z] [-g hueco] [-y desplazamiento]`.
- **QA geométrico:** cierra microhuecos y elimina componentes aislados de forma opcional.

Los modificadores ahora deforman una única geometría continua. Ya no dividen el path en figuras independientes, por lo que cadenas, anillos y otras formas no reinician su patrón en cada tramo. Al escoger otra figura se reinician automáticamente todos los modificadores y opciones específicas de la anterior. El botón **Restablecer modificadores** permite volver manualmente a `100%`, sin torsión, sin ritmo, sin conformado y sin Kitbash.

## Figuras disponibles

- `chainlink`
- `scales`
- `spiked_scales`
- `beads`
- `coil`
- `lightning`
- `crowns`
- `honeycomb`
- `fishnet` (red de hilos helicoidales a ±45°)
- `oscillate`
- `ribloops`
- `rings`
- `tiles`
- `hexarmor`
- `voronoi`
- `knots`
- `noodles`
- `mandala`
- `clouds`
- `web`
- `sweep` (perfil 2D en espacio de tubo)
- `braid` (trenza en espacio de tubo)
- `noisetube` (tubo erosionado en espacio de tubo)
- `expression` (figura definida por una fórmula en espacio de tubo)

`scales` y `spiked_scales` continúan usando su muestreo geométrico heredado para preservar exactamente su apariencia. Las demás figuras conservan el spline suavizado actual.

## Telarañas adaptativas

- **Dos puntos:** P1 y P2 forman nudos gruesos unidos por un puente fino. El grosor disminuye progresivamente hacia el centro y la hebra presenta una comba suave.
- **Tres o más puntos:** cada punto se convierte en un anclaje exterior. Desde el centro salen radios rectos y entre cada par de radios se dibujan arcos cóncavos como una telaraña clásica triangular, cuadrada o circular.
- **Controles:** **Elasticidad** controla la comba del puente o la curvatura de los arcos, **Separación hilos** ajusta la distancia entre vueltas, **Grosor** modifica los filamentos y **Relieve 3D** levanta los arcos fuera del plano sin mover los anclajes.
- **Holograma exacto:** la previsualización utiliza la misma geometría que el Build.
- **Comando rápido:** `/komaedit web <elasticidad> <separación 2–8> [patrón]`.

Ejemplo:

```text
/komaedit web 12 4 white_wool
```

## Rayos ramificados

- **Trayectoria por puntos:** cada par de puntos consecutivos queda unido por un núcleo quebrado. Todos los anclajes se conservan exactamente.
- **Aleatoriedad estable:** las desviaciones y bifurcaciones se calculan a partir de las coordenadas marcadas; mover un punto cambia el rayo, pero el mismo recorrido siempre produce el mismo holograma y Build.
- **Ramificación configurable:** valores `2–8`; los valores altos reducen la distancia entre ramas y permiten bifurcaciones secundarias.
- **Corona hueca:** al cerrar la trayectoria, las descargas se orientan hacia fuera y fuera del plano, formando un aro de electricidad o fuego sin colocar bloques en el centro.
- **Controles:** **Amplitud**, **Ramificación**, **Grosor núcleo** y **Caos 3D**.
- **Material predeterminado:** mezcla de concreto celeste y blanco, linterna marina y verdant froglight. Puede sustituirse por magma, shroomlight y bloques naranjas para un aro de fuego.
- **Comandos rápidos:** `/komaedit lightning <amplitud> <ramificación 2–8> [patrón]` y `/komaedit rayos`.
- **Círculo automático:** `/komaedit lightningcircle <radio> <ramificación 2–8> [patrón]` o `/komaedit circulorayos`.

Ejemplos:

```text
/komaedit lightning 8 6
/komaedit lightningcircle 40 7
/komaedit circulorayos 48 8 40%magma_block,30%orange_concrete,20%shroomlight,10%yellow_concrete
```

## Mandalas tridimensionales

- **Orientación mediante puntos:** P1 define el centro y el vector P1→P2 define la normal del plano. Un P2 vertical crea un mandala horizontal; un P2 horizontal crea uno vertical.
- **Geometría con relieve:** combina núcleo sólido, cinco anillos ondulados, pétalos principales rellenos, filigrana secundaria, radios y puntas exteriores.
- **Controles contextuales:** **Tamaño** ajusta el diámetro, **Simetría** aumenta sectores y detalle, **Relieve** controla profundidad y **Giro** rota todo el diseño.
- **Materiales completos:** admite cualquier patrón WorldEdit y todos los degradados locales del Studio.
- **Comando rápido:** `/komaedit mandala <tamaño> <simetría 2–8> [patrón]`.

## Gizmo XYZ para puntos

- Haz clic izquierdo sobre cualquier nodo mientras estás en **Trayectoria** para seleccionarlo.
- Aparecen flechas rojas, verdes y azules para los ejes X, Y y Z.
- Arrastra una flecha para mover el punto exclusivamente sobre ese eje; el movimiento no necesita tocar una superficie.
- El tamaño visual del gizmo se adapta a la distancia de cámara y el punto se actualiza localmente antes de recibir la confirmación del servidor.
- La selección permanece activa después de soltar el ratón para encadenar varios ajustes.

## Arquitectura monumental

- **Categoría exclusiva:** Pilar monumental y Pilares arqueados aparecen únicamente en **Estructuras**, nunca en la cuadrícula de Figuras.
- **Pilar monumental:** marca la base y la parte superior con `P`; el recorrido define el eje, altura e inclinación. Ajusta **Ancho**, **Detalle**, **Relieve** y **Giro** desde Estructuras.
- **Pilares arqueados:** marca dos bases con `P`; KomaEdit levanta un pilar monumental en cada punto y los conecta mediante un arco profundo con dovelas, clave central, vigas y refuerzos.
- **Altura ajustable:** `Pilares arqueados` muestra un control de altura propio; la separación entre P1 y P2 determina la luz del arco.
- **Geometría sólida:** bases escalonadas, fuste con éntasis, nervaduras, bandas, contrafuertes y capiteles se generan como una sola pieza construible.
- **Holograma exacto:** ambos generadores utilizan el mismo `ShapeGenerator` para previsualizar y construir, por lo que cualquier ajuste se refleja antes de colocar bloques.
- **Comandos rápidos:** `/komaedit pillar <ancho> <detalle> [patrón]` y `/komaedit pillararches <altura> <ancho> [patrón]`.

## Torii japoneses monumentales

- **P1 y P2 son las bases:** coloca P1 en el primer pilar y P2 en el segundo. La distancia horizontal define automáticamente el ancho completo del portal.
- **Escala gigante:** la altura admite entre 16 y 256 bloques; el Studio comienza en 96 y avanza de 4 en 4 para ajustes rápidos.
- **Geometría imperial:** incluye columnas ligeramente inclinadas, basas escalonadas, collarines, travesaño inferior, viga de hombros, corona de varias capas, aleros curvos, ménsulas diagonales y placa central.
- **Terreno desigual:** cada columna conserva la altura completa desde su propia base y las vigas conectan ambos apoyos mediante una pendiente recta y continua.
- **Silueta limpia:** se elimina la ondulación central; únicamente los extremos conservan el alero curvo tradicional del torii.
- **Controles:** **Grosor** modifica las columnas y profundidad de vigas, **Detalle** añade capas y ménsulas, **Relieve** refuerza perfiles y **Curvatura** eleva o aplana los extremos del techo.
- **Curvatura:** valores negativos aplanan el alero, `0` usa la curva tradicional y valores positivos levantan más los extremos.
- **Separación mínima:** P1 y P2 deben estar separados al menos unas 4.5 veces el grosor seleccionado.
- **Comando rápido:** `/komaedit torii <altura> <grosor> <detalle 2–8> [patrón]`.

## Nubes orgánicas

- **Silueta dibujable:** marca dos o más puntos con `P`; el recorrido se convierte en el eje de una nube continua en lugar de una sucesión de esferas aisladas.
- **Volumen natural:** combina un cuerpo sólido, lóbulos laterales, coronas superiores tipo coliflor y estelas finas en extremos y transiciones.
- **Superficie erosionada:** utiliza ruido tridimensional determinista a dos escalas; el holograma y el Build producen exactamente el mismo contorno.
- **Bancos naturales largos:** distribuye aproximadamente 4–10 masas solapadas antes de abrir un hueco real, evitando nubes demasiado cortas o una cuadrícula repetitiva.
- **Longitud adaptativa:** con Organicidad baja aparecen bancos de unas 4–6 masas; con Organicidad alta crecen aproximadamente hasta 6–10.
- **Controles contextuales:** **Tamaño** define el volumen, **Organicidad** aumenta la irregularidad y cantidad de lóbulos, **Densidad** compacta la masa, **Giro** cambia su asimetría y **Separación** controla los bloques libres aproximados entre bancos.
- **Variación del espaciado:** cada hueco modifica automáticamente el valor elegido alrededor de ±20%, de modo que la separación conserve intención sin parecer mecánica.
- **Material por defecto:** el comando rápido mezcla lana blanca, calcita y lana gris clara; desde Material puede sustituirse por cualquier bloque, patrón o degradado para nubes mágicas y piezas orgánicas.
- **Comandos rápidos:** `/komaedit cloud <tamaño> <organicidad 2–8> [separación] [patrón]` y su alias español `/komaedit nube`.
- **Rendimiento:** genera directamente un conjunto compacto de vóxeles, cancela cálculos obsoletos y reutiliza el motor asíncrono y el holograma seccionado de las demás figuras.

## Figuras de espacio de tubo

- **Sin huecos por construcción:** `sweep`, `braid` y `noisetube` recorren los bloques cercanos al eje y evalúan en cada uno la función implícita de la figura, en vez de estampar pinceles y puentear lo que queda abierto.
- **Perfiles 2D:** círculo, cuadrado, rombo, redondeado, polígono, estrella, flor, cuerda, elipse y rectángulo, macizos o huecos con pared cerrada.
- **Radios por fotogramas:** `2,10,2` o `2,0.3:12,2` engordan y afinan el tubo con interpolación monótona.
- **Curva y marcos:** Kochanek–Bartels (tensión, sesgo, continuidad) y marcos de mínima rotación; normales `consistent`, `horizontal` o `upright`.
- **Extremos:** plano, suave, punta, cúpula o cubo.
- **Modificadores exactos:** escala, espaciado y torsión se aplican al evaluar cada bloque.
- **Losas y escaleras:** con `-w slabs|stairs` (fila **Piezas** del Studio) la superficie que la figura solo ocupa en parte se termina con losas o escaleras orientadas del mismo material; `-c` (Cobertura, 0–1) decide cuántas.
- **Fórmulas:** `expression` construye lo que dé mayor que cero una fórmula en `x`, `y` (sección, de -1 a 1), `z` (a lo largo, en radios), `t`, `r`, `a` y `n`, con cinco fórmulas de serie (ventanas, raíles, espiral, celosía y costillas).
- **Comandos rápidos:** `/komaedit sweep <perfil> <radios> [patrón] [-e …] [-n …] [-t …] [-r …] [-p …] [-hollow …] [-w …] [-c …]`, `/komaedit braid <radios>`, `/komaedit noisetube <radios>` y `/komaedit expr <radios> [banderas] [patrón] <fórmula…>`.
- **Motor exacto para figuras clásicas:** `chainlink`, `rings`, `beads`, `oscillate`, `fishnet`, `honeycomb`, `knots` y `coil` aceptan `engine:exact` (fila **Motor** del Studio) y se evalúan en este mismo espacio de tubo, sin huecos y con extremos, normales, radios por fotogramas, torsión y piezas. El motor clásico sigue siendo el predeterminado.

```text
/komaedit sweep star5 "2,0.3:12,2" polished_andesite -e spike -t 30
/komaedit sweep polygon6 10 deepslate_tiles -hollow 2 -n horizontal
/komaedit sweep circle 9 stone_bricks -w stairs -c 0.6
/komaedit expr 8 stone_bricks ventanas8
/komaedit expr 6 -e round polished_andesite r<1 && abs(sin(a*3+z)) > 0.4
/komaedit build "coil(engine:exact,end:round)" stone 8
```

## Materiales integrados

Una receta puede usar:

- Un patrón sólido compatible con WorldEdit.
- Una de las paletas profesionales de KomaEdit.
- Ejes `X`, `Y`, `Z`, diagonal o radial.
- Mezcla orgánica configurable e inversión de dirección.
- Métricas globales por eje, exposición, cavidad, bordes, curvatura y ruido orgánico.
- **Patrón figura:** distribución especializada según la geometría elegida.
- En **Scales**, **Patrón figura** mantiene oscuro el cuerpo de cada pieza y concentra el contraste en sus laterales y labio frontal; no genera bandas alrededor del path.
- **Frente/reverso:** reinicia toda la paleta entre la raíz y el frente de cada motivo.
- **Relieve:** distribuye materiales entre núcleo, profundidad y zonas elevadas.
- **Filas alternas:** usa mitades distintas de la paleta en filas consecutivas.

En `scales`, las métricas locales usan el mismo paso de fila que la figura original. Esto permite dar materiales oscuros a la raíz, tonos medios al cuerpo y materiales claros o brillantes al labio de cada escama. El degradado se resuelve mientras se coloca el plan generado; no requiere una segunda operación rectangular.

## Instancias editables

Cada construcción avanzada crea un identificador persistente dentro del mundo actual. Al seleccionar una instancia, KomaEdit recupera también su trayectoria para poder mover los nodos y reconstruirla. Al actualizarla, calcula la nueva geometría fuera del hilo principal y aplica únicamente el resultado por lotes. Al retirarla, solo elimina bloques que todavía coinciden con el estado colocado por KomaEdit; cualquier edición manual se conserva y se informa al jugador.

## Proyectos persistentes y recuperación

- Datos por mundo: `<carpeta-del-mundo>/komaedit/players/<uuid>/`.
- Escrituras atómicas con archivos temporales y respaldo `.bak` antes de reemplazar datos existentes.
- Verificación SHA-256 de la geometría binaria de cada instancia.
- Migración no destructiva de recetas e instancias antiguas desde `config/komaedit/`.
- Bitácora de jobs con progreso periódico; un job que quedó activo durante un cierre se marca como `INTERRUPTED` al volver a entrar.
- `/komaedit project status` muestra recetas, instancias, jobs, interrupciones y espacio utilizado.
- `/komaedit project backup` crea un ZIP del proyecto del jugador; la retención predeterminada es de cinco copias.

## Permisos y cuotas

Los operadores conservan acceso total. En servidores con un proveedor de permisos compatible con WorldEdit, un jugador normal necesita `komaedit.use` y el nodo específico de la herramienta:

- `komaedit.path`, `komaedit.build` o `komaedit.build.<figura>`.
- `komaedit.instance`, `komaedit.recipe`, `komaedit.select` y `komaedit.paint`.
- `komaedit.clipboard`, `komaedit.history` y `komaedit.project.backup`.
- `komaedit.limit.unrestricted` omite el límite de bloques de una operación, pero no el scheduler global.

`config/komaedit/config.json` permite ajustar `maxBlocksPerOperation`, `maxConcurrentJobs`, `backupRetention` y el modo de compatibilidad `rootPermissionGrantsAll`.

## Paquetes compartibles

- **Exportar** crea `config/komaedit/packages/<nombre>.krecipe`.
- **Importar** valida y guarda la receta contenida en ese paquete.
- El formato incluye figura, material, gradiente y todos los modificadores de la receta.

## Rendimiento

- Cálculo de geometría mediante `AsyncEngine`.
- Planes compactos con posiciones empaquetadas.
- Colocación con presupuesto de tiempo por tick.
- Una única entrada de historial WorldEdit por construcción.
- Límite preventivo de 1 500 000 bloques por geometría.
- Captura incremental de propiedad durante la colocación, sin escaneo masivo al finalizar.
- Protección condicional de bloques modificados manualmente durante update/delete.
- Holograma sin partículas: caras expuestas agrupadas por secciones de `16×16×16`.
- Subida gradual de VBOs, frustum culling, límite de distancia y caché hasta el siguiente cambio geométrico.
- Cancelación inmediata de previews obsoletos para no competir con la construcción.

Usa `/komaedit job status`, `/komaedit job cancel`, `/komaedit project status`, `//undo` y `//redo` para administrar trabajos.

## Compatibilidad

- Minecraft `1.21.1`
- Fabric Loader `0.17.2` o posterior
- Fabric API `0.116.6+1.21.1` o posterior
- WorldEdit `7.3.8` o posterior
- Java `21`

## Fuentes de terceros

`/komaedit text` incluye las fuentes Lato (© tyPoland Łukasz Dziedzic) y JetBrains Mono (© The JetBrains Mono Project Authors), sin modificar y bajo la SIL Open Font License 1.1; sus licencias están en `src/main/resources/komaedit/fonts/` y dentro del jar.

KomaEdit no es un producto oficial de Minecraft y no está aprobado ni asociado con Mojang o Microsoft.
