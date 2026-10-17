# Documentación del MOD

Este documento es el catálogo funcional oficial de KomaEdit. Cada función nueva o cambio visible para el jugador debe añadirse aquí en el mismo Pull Request que introduce el código.

## Procedural Studio

Procedural Studio se abre con `K` y permite construir mientras el mundo continúa visible y activo. La interfaz ofrece categorías para trayectorias, figuras, estructuras, modificadores, materiales, recetas e instancias.

- Los paneles son desplazables y se adaptan a resoluciones pequeñas.
- El catálogo de materiales utiliza todos los bloques registrados por Minecraft y los mods cargados.
- La cámara puede desplazarse mientras el Studio permanece abierto.
- `Ctrl+Z`, `Ctrl+Y` y `Ctrl+Shift+Z` utilizan el historial de WorldEdit.
- La navegación principal, estados de trayectoria y acciones esenciales están disponibles en español e inglés mediante el idioma seleccionado en Minecraft.

## Trayectorias y hologramas

Las trayectorias se crean con puntos colocados mediante `P` o desde Procedural Studio. Los nodos pueden moverse con el gizmo XYZ y la geometría se recalcula sin modificar el mundo.

- El raycast alcanza hasta 4096 bloques cargados.
- El holograma representa las posiciones exactas de la futura construcción.
- La previsualización permanece visible al cerrar la interfaz.
- El cálculo se ejecuta fuera del hilo de renderizado, admite cancelación y conserva temporalmente el último resultado válido.

## Figuras procedurales

Las figuras convierten una trayectoria tridimensional en superficies o volúmenes repetibles. Actualmente se incluyen:

- Chainlink
- Scales
- Spiked Scales
- Beads
- Coil
- Rayos ramificados
- Crowns
- Honeycomb
- Oscillate
- Ribloops
- Rings
- Tiles
- Hexarmor
- Voronoi
- Knots
- DNA
- Tendrils
- Mandalas tridimensionales
- Nubes orgánicas
- Telarañas adaptativas
- Perfil 2D (espacio de tubo)
- Trenza (espacio de tubo)
- Tubo erosionado (espacio de tubo)

Cada figura conserva su gramática propia y puede combinar radio, espaciado, grosor, giro y materiales sin reutilizar estado residual de una figura anterior.

## Rayos ramificados

### Objetivo y acceso

La figura **Rayos** crea descargas tridimensionales quebradas entre los puntos asignados. Puede seleccionarse desde **Figuras** en Procedural Studio o ejecutarse mediante:

```text
/komaedit lightning <amplitud 2–32> <ramificación 2–8> [patrón]
/komaedit rayos <amplitud 2–32> <ramificación 2–8> [patrón]
```

El comando general también está disponible:

```text
/komaedit build lightning <patrón> <amplitud> <ramificación 2–8> <grosor 1–4> <caos -180–180>
```

### Comportamiento

- Cada par de puntos consecutivos se conecta con un núcleo quebrado y continuo.
- El desplazamiento se obtiene de una semilla formada por las coordenadas y el orden de los puntos. No utiliza aleatoriedad temporal: holograma, Build y actualizaciones producen el mismo resultado.
- Puntos adicionales crean nuevos tramos con una distribución propia, pero todos los anclajes permanecen exactamente en su posición.
- Las ramas nacen sobre el núcleo y se alejan lateralmente. Con ramificación alta algunas ramas producen una segunda bifurcación.
- Una trayectoria cerrada conecta el último punto con el primero y convierte las ramas en una corona exterior. El centro no se rellena.
- El modo circular orienta parte de las ramas fuera del plano para obtener lenguas similares a electricidad o fuego.

### Parámetros

- **Amplitud:** `2–32`; controla cuánto se separan los quiebres del segmento recto.
- **Ramificación:** `2–8`; controla densidad de ramas y posibilidad de bifurcaciones secundarias.
- **Grosor núcleo:** `1–4`; modifica el diámetro del rayo principal. Las ramas conservan un grosor menor.
- **Caos 3D:** `-180–180`; su magnitud aumenta la profundidad tridimensional y su signo invierte el lado principal del desplazamiento.

### Círculo hueco automático

```text
/komaedit lightningcircle <radio 4–128> <ramificación 2–8> [patrón]
/komaedit circulorayos <radio 4–128> <ramificación 2–8> [patrón]
```

- El círculo se centra en la posición actual del jugador y utiliza hasta `32` anclajes.
- El radio del comando define el tamaño del aro; la amplitud eléctrica se calcula proporcionalmente y se limita a un rango seguro.
- El recorrido queda guardado como trayectoria cerrada para poder editar sus puntos, cambiar materiales o reconstruirlo desde el Studio.

### Materiales

- El patrón predeterminado es `45%light_blue_concrete,25%white_concrete,20%sea_lantern,10%verdant_froglight`.
- Acepta cualquier bloque, patrón WorldEdit o degradado del Studio.
- Para fuego puede utilizarse `40%magma_block,30%orange_concrete,20%shroomlight,10%yellow_concrete`.
- **Patrón figura** concentra los extremos de la paleta en ramas, zonas expuestas y descargas alejadas del núcleo.

### Rendimiento y limitaciones

- La cantidad de vértices depende de la longitud y la amplitud; cada segmento se limita a `512` vértices.
- Cada tramo admite hasta `128` ramas antes del límite global de geometría.
- El generador respeta los límites generales de `32` puntos, `4096` bloques de trayectoria y `1 500 000` bloques generados.
- Toda la geometría se calcula fuera del hilo principal, admite cancelación y se deduplica mientras se crea.
- El aro automático es horizontal. Para un portal vertical, marque manualmente los puntos en un plano vertical y cierre la trayectoria.

## Telarañas adaptativas

### Objetivo y acceso

La figura **Telaraña** genera tejidos tensados directamente desde los puntos de la trayectoria. Se selecciona desde **Figuras** en Procedural Studio o mediante:

```text
/komaedit web <elasticidad> <separación 2–8> [patrón]
```

También está disponible con el comando general:

```text
/komaedit build web <patrón> <elasticidad> <separación 2–8> <grosor 1–4> <giro -180–180>
```

### Comportamiento

- Con exactamente dos puntos, P1 y P2 forman dos nudos gruesos conectados por una hebra fina. El grosor se reduce progresivamente desde ambos extremos y recupera volumen al acercarse al otro anclaje.
- El puente central conserva una comba y una ondulación ligera para evitar una línea completamente rígida.
- El modo de dos puntos nunca cierra una elipse ni genera anillos alrededor del eje.
- Con tres o más puntos únicos, cada punto actúa como anclaje exterior y crea un radio recto desde el centro.
- Entre radios consecutivos se generan curvas cuadráticas hundidas hacia el centro, reproduciendo las vueltas cóncavas de una telaraña clásica.
- Los anclajes se ordenan alrededor de su centro geométrico antes de conectar los anillos, evitando cruces producidos por un orden irregular de captura.
- Los radios interiores pueden retorcerse suavemente; los anclajes finales siempre permanecen en las coordenadas marcadas.
- El holograma y la construcción usan el mismo generador de vóxeles.

### Parámetros y materiales

- **Elasticidad:** controla la comba del puente fino con dos puntos y la profundidad de los arcos cóncavos con múltiples anclajes.
- **Separación hilos:** `2` produce vueltas próximas y `8` las distribuye a mayor distancia.
- **Grosor:** controla el diámetro del puente central, radios y vueltas; los nudos de P1 y P2 se engrosan automáticamente según la elasticidad.
- **Relieve 3D:** entre `-180` y `180` grados; orienta la comba del puente o levanta progresivamente los arcos fuera del plano sin desplazar los puntos exteriores.
- El patrón predeterminado es `white_wool`; se acepta cualquier bloque, patrón WorldEdit o degradado disponible en el Studio.

### Rendimiento y limitaciones

- La cantidad de anillos se calcula a partir del radio medio y se limita a 64 para mantener un coste predecible.
- Los puntos repetidos a menos de un cuarto de bloque se consolidan en un único anclaje.
- Si tres o más puntos son prácticamente colineales, el generador utiliza los dos extremos para desplegar automáticamente una malla radial en un plano estable.
- Una distribución muy irregular o no coplanar produce una telaraña tridimensional tensada, no una circunferencia plana.

## Figuras de espacio de tubo

### Objetivo y acceso

**Perfil 2D** (`sweep`), **Trenza** (`braid`) y **Tubo erosionado** (`noisetube`) se generan con un motor distinto al de las demás figuras: en lugar de estampar pinceles a lo largo de la curva, recorre los bloques cercanos al eje y evalúa en cada uno una función implícita de la figura. El resultado es estanco por construcción, sin puentes ni reparación posterior.

- Studio: aparecen en **Figuras**. Las tres añaden filas de **Extremos** y **Normales**; **Perfil 2D** añade además **Perfil** y **Relleno**.
- Comandos rápidos:

```text
/komaedit sweep <perfil> <radios> [patrón] [banderas]
/komaedit perfil <perfil> <radios> [patrón] [banderas]
/komaedit braid <radios> [patrón] [banderas]
/komaedit trenza <radios> [patrón] [banderas]
/komaedit noisetube <radios> [patrón] [banderas]
/komaedit erosion <radios> [patrón] [banderas]
```

### Comportamiento

- **Espacio de tubo:** cada bloque se localiza por su punto más cercano del eje. De ahí salen `x` e `y` sobre la sección, en radios (de `-1` a `1`), y `z` a lo largo del recorrido, que avanza un radio por cada radio de longitud. Las figuras se definen en ese espacio, así que crecen, giran y se estiran con el tubo.
- **Curva Kochanek–Bartels:** la trayectoria pasa exactamente por todos los puntos. Con tensión, sesgo y continuidad a `0` equivale a un Catmull-Rom; las tangentes se corrigen por longitud de cuerda para no formar bucles con puntos desigualmente separados.
- **Marcos sin giro espurio:** la sección se transporta con marcos de mínima rotación (doble reflexión). En trayectorias cerradas el desfase de cierre se reparte por todo el lazo para que el empalme no se note.
- **Normales:** `consistent` rueda con suavidad en las curvas; `horizontal` mantiene la cara superior mirando al cielo; `upright` alinea el eje vertical de la sección con el eje Y del mundo.
- **Extremos** (solo trayectorias abiertas): `flat` corta en seco, `soft` afila el último radio hacia dentro, `round` añade una cúpula, `spike` un cono de dos radios y `cube` un remate cuadrado.
- **Hueco:** la pared se obtiene por morfología (bloque dentro con algún vecino fuera a la distancia de la pared), así que queda cerrada aunque la superficie vaya en diagonal. Con extremo `flat` el tubo queda abierto como una tubería.
- **Modificadores exactos:** escala, espaciado y torsión de la sección Deformar se aplican dentro del espacio de tubo al evaluar cada bloque, sin deformar bloques ya generados ni puentear huecos.
- **Determinismo:** el ruido del tubo erosionado usa una semilla derivada de los puntos; holograma y Build coinciden.

### Parámetros

| Control / bandera | Opción de receta | Efecto |
| --- | --- | --- |
| Radio / `<radios>` | radio + `k0`…`k5`, `p1`…`p4` | Radio base. Con varios valores (`2,10,2`) el grosor sigue esos fotogramas con interpolación monótona; `2,0.3:12,2` fija la posición del intermedio. Sin comillas: `2_10_2`. |
| Lados / puntas | `sides` | Lados del polígono, puntas de la estrella, pétalos de la flor o hebras de la cuerda (Studio 2–8, comando hasta 32 con `star12`). |
| Pared (hueco), `-hollow <n>` | `hollow`, `wall` | Grosor de pared en bloques cuando el relleno es **Hueco**. |
| Giro, `-r <a>[,<b>]` | `angle`, `rollend` | Giro inicial de la sección; con dos valores gira de `a` a `b` a lo largo del recorrido. |
| `-t <grados>` | `twist` | Torsión por diámetro, como una cuerda: tras recorrer un diámetro la sección ha girado esos grados. La cuerda usa 90 por defecto. |
| `-e <modo>` / Extremos | `end` | `flat`, `soft`, `spike`, `round`, `cube`. |
| `-n <modo>` / Normales | `normal` | `consistent`, `horizontal`, `upright`. |
| `-p t:b:c` | `tension`, `bias`, `continuity` | Kochanek–Bartels, cada valor entre `-1` y `1`. |
| `-q <calidad>` | `quality` | `fast` (centro del bloque), `balanced` (rescata detalles finos con seis muestras), `high` (27 muestras, cobertura ≥ 25 %). |
| `-s <factor>` | `stretch` | Estira o comprime el patrón a lo largo del recorrido. |
| `-d <0–1>` | `depthmultiplier` | Profundidad de las estrellas y flores, o de la erosión. |
| `-a <0.1–1>` | `aspect` | Proporción de elipse y rectángulo. |
| `-x <exponente>` | `exponent` | Forma del perfil redondeado (4 = squircle). |
| `-k <2–8>` | `strands` | Hebras de la trenza. |
| `-f <frecuencia>` | `frequency` | Tamaño de las vetas del tubo erosionado. |
| `-fill` | `filled` | Rellena el alma de la cuerda. |

Perfiles: `circle`, `square`, `diamond`, `rounded`, `polygon`, `star`, `flower`, `rope`, `ellipse` y `rectangle` (también en español: `circulo`, `estrella`, `cuerda`…). Un número al final fija los lados: `polygon6`, `star5`, `rope4`.

En recetas y expresiones también se aceptan nombres: `sweep(profile:star,sides:7,end:round,normal:horizontal,hollow:true)`.

Ejemplos:

```text
/komaedit sweep circle 8
/komaedit sweep star5 "2,0.3:12,2" polished_andesite -e spike -t 30
/komaedit sweep rope3 6 oak_planks
/komaedit sweep polygon6 10 deepslate_tiles -hollow 2 -n horizontal
/komaedit braid 7 stripped_birch_wood -k 4
/komaedit noisetube 2_12_4 60%stone,25%andesite,15%cobblestone -e round
```

### Materiales

Con **Patrón figura** las figuras de tubo usan su propio valor implícito: en los perfiles va del núcleo (`0`) al borde (`1`), y en cuerdas y trenzas cada hebra ocupa su tramo de la paleta, de modo que una paleta de tres colores pinta tres hebras distintas. **Relieve**, **Frente / reverso** y **Filas alternas** funcionan igual que en el resto de figuras.

### Rendimiento y limitaciones

- Solo se evalúan ladrillos de 8×8×8 bloques que tocan el tubo; un árbol de esferas sobre el eje localiza el punto más cercano en tiempo logarítmico y parte del resultado del bloque anterior.
- Un tubo de radio 14 y 360 bloques de recorrido (~220 000 bloques) se calcula en menos de un segundo en un worker.
- El volumen candidato se limita a 64 millones de bloques y el resultado al límite general de 1 500 000.
- Con normales `upright` las secciones se inclinan en tramos empinados y pueden solaparse; en tramos verticales conviene `consistent`.
- Si una curva se cierra más que el propio radio, el tubo se pliega por dentro como en cualquier barrido: separa más los puntos o reduce el radio.
- El tubo erosionado conserva solo la pieza conectada al núcleo, sin fragmentos flotantes.

## Modificadores

Los modificadores transforman de manera continua la figura seleccionada:

- Escala inicial y final.
- Espaciado inicial y final.
- Curvas de transición.
- Torsión progresiva.
- Fusión con una figura secundaria.
- Conformado a suelo, techo o superficie.
- Sockets Kitbash.
- Reparación de microhuecos y componentes aislados.

En las figuras de espacio de tubo la escala, el espaciado y la torsión se aplican al evaluar cada bloque, sin deformación posterior ni puentes entre bloques separados.

## Estructuras

La categoría Estructuras contiene generadores arquitectónicos orientados por puntos. Sus herramientas no aparecen duplicadas en la cuadrícula de Figuras:

- Pilares monumentales configurables.
- Pares de pilares monumentales conectados mediante arcos.
- Puentes arqueados entre apoyos.
- Torii japoneses gigantes que respetan diferencias verticales del terreno sin ondular sus vigas.

## Materiales y degradados

KomaEdit admite bloques individuales, patrones de WorldEdit y paletas de degradado. La distribución puede seguir el eje, exposición, cavidades, bordes, curvatura o propiedades específicas de la figura.

## Recetas, instancias y paquetes

- Las recetas guardan parámetros reutilizables de forma persistente dentro del mundo.
- Las instancias conservan receta, trayectoria, estados colocados y checksum; permiten actualizar o retirar una construcción sin borrar bloques modificados manualmente.
- Seleccionar una instancia vuelve a cargar sus puntos en el editor antes de actualizarla.
- Los paquetes exportables conservan configuraciones compartibles en `config/komaedit/packages/`.
- Los datos se aíslan por mundo y UUID de jugador para evitar contaminación entre proyectos.

## Proyecto persistente y recuperación

### Objetivo y acceso

El proyecto persistente evita perder recetas e instancias al desconectarse y permite auditar operaciones interrumpidas. Se administra con:

```text
/komaedit project status
/komaedit project backup
```

### Almacenamiento y migración

- Ruta: `<mundo>/komaedit/players/<uuid>/` con namespaces `recipes`, `instances` y `jobs`.
- La primera entrada copia de forma no destructiva las recetas e instancias heredadas de `config/komaedit/`; un marcador impide repetir la migración.
- Cada reemplazo conserva el archivo anterior como `.bak` y utiliza un `.tmp` recuperable si el proceso se interrumpe.
- La geometría comprimida de una instancia incluye SHA-256 en sus metadatos. Una discrepancia detiene la carga en vez de aplicar datos dañados.
- `project backup` crea un ZIP en `<mundo>/komaedit/backups/` y conserva las copias más recientes según `backupRetention`.

### Comportamiento y limitaciones

- Los jobs activos escriben progreso como máximo una vez por segundo para no convertir el disco en un cuello de botella.
- Tras un cierre inesperado, los jobs `RUNNING` pasan a `INTERRUPTED`; no se reanudan automáticamente porque el mundo pudo cambiar mientras el servidor estaba apagado.
- Las operaciones ya confirmadas siguen usando el historial de WorldEdit para `//undo`; la bitácora persistente es de diagnóstico, no una sustitución del historial.
- Los backups son manuales y por jugador. Los administradores deben incluir la carpeta `komaedit` del mundo en su estrategia normal de respaldo.
- Una receta o instancia que no se pueda leer (dañada, o de una versión que usaba una figura ya retirada) se omite del catálogo y se registra en `latest.log` con su nombre de archivo; el archivo se queda en disco y el resto del proyecto sigue funcionando.

## Permisos, cuotas y configuración

### Permisos

Los operadores poseen acceso total. Para otros jugadores, KomaEdit consulta permisos a través del actor de WorldEdit:

- Base: `komaedit.use`.
- Trayectorias y construcción: `komaedit.path`, `komaedit.build`, `komaedit.build.<figura>`.
- Contenido: `komaedit.recipe`, `komaedit.instance`.
- Edición: `komaedit.select`, `komaedit.paint`, `komaedit.clipboard`, `komaedit.history`.
- Administración de proyecto: `komaedit.project.backup`.
- Excepción de volumen: `komaedit.limit.unrestricted`.

### Configuración

`config/komaedit/config.json` se crea automáticamente con:

- `maxBlocksPerOperation`: `1500000` de forma predeterminada; rango válido `10000–10000000`.
- `maxConcurrentJobs`: `4` de forma predeterminada; rango válido `1–64`.
- `backupRetention`: `5` de forma predeterminada; rango válido `1–50`.
- `rootPermissionGrantsAll`: `false` de forma predeterminada. Si se activa, `komaedit.use` concede todos los subpermisos como compatibilidad con instalaciones antiguas.

Una configuración inválida se rechaza completa y el servidor utiliza límites seguros. Los cambios requieren reiniciar el servidor.

## Rendimiento

- La geometría se calcula de forma asíncrona.
- La colocación se agrupa por chunks y lotes.
- Los hologramas utilizan secciones persistentes, descarte por cámara y caras expuestas.
- Los generadores poseen límites de seguridad y cancelación cooperativa.
- Las figuras de espacio de tubo recorren solo los ladrillos de 8³ bloques cercanos al eje y localizan el eje con un árbol de esferas.
- El límite de volumen y la cuota global de jobs se aplican antes de abrir una sesión de edición.
- Cada job persistente registra avance con frecuencia limitada y su finalización o cancelación.

## Versiones descargables

Todas las compilaciones de integración quedan visibles en GitHub Releases además de los artefactos temporales de GitHub Actions.

### Canal staging

- La versión interna del mod incluye la versión de desarrollo y el SHA corto: `komaedit-4.8.0-alpha.1.c0d75e5.jar`.
- La prerelease se muestra con el formato `komaedit-staging-4.8.0-c0d75e5`.
- Cada merge genera una prerelease independiente apuntando al commit exacto compilado.
- Estas versiones son de prueba y no sustituyen una publicación estable.

### Canal main

- Utiliza únicamente `release_version`.
- El nombre estable sigue el formato `komaedit-1.0.0`.
- No incluye `staging`, sufijos alpha ni SHA.
- Antes de publicar otro commit en `main` se incrementa la versión, por ejemplo de `1.0.0` a `1.0.1`.
- GitHub Actions rechaza una publicación si intenta reutilizar una versión estable asociada a otro commit.

## Política de actualización

Cada nueva función debe documentar:

1. Qué problema resuelve.
2. Cómo se abre o ejecuta.
3. Sus parámetros y materiales.
4. Su interacción con puntos, hologramas y modificadores.
5. Consideraciones de rendimiento.
6. Comandos de ejemplo cuando correspondan.
