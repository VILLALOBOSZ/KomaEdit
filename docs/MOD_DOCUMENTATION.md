# Documentación del MOD

Este documento es el catálogo funcional oficial de KomaEdit. Cada función nueva o cambio visible para el jugador debe añadirse aquí en el mismo Pull Request que introduce el código.

## Procedural Studio

Procedural Studio se abre con `K` y permite construir mientras el mundo continúa visible y activo. La interfaz ofrece categorías para trayectorias, figuras, estructuras, modificadores, materiales, envejecer, recetas e instancias.

- Los paneles son desplazables y se adaptan a resoluciones pequeñas.
- El catálogo de materiales utiliza todos los bloques registrados por Minecraft y los mods cargados.
- La cámara puede desplazarse mientras el Studio permanece abierto.
- `Ctrl+Z`, `Ctrl+Y` y `Ctrl+Shift+Z` utilizan el historial de WorldEdit.
- La navegación principal, estados de trayectoria y acciones esenciales están disponibles en español e inglés mediante el idioma seleccionado en Minecraft.

### Encontrar figuras

La etapa **Figuras** empieza con un buscador, filtros por familia y las figuras recientes.

- **Buscador** (`Ctrl+F` o `/` desde cualquier etapa): sin mayúsculas ni tildes, busca por nombre, id, alias del comando y descripción (`roca` encuentra Tubo erosionado; `chain`, Cadena). Cada palabra tiene que aparecer; el nombre pesa más que la descripción. `Intro` elige la primera, `Esc` borra la búsqueda y la cierra. La búsqueda recorre todas las familias.
- **Familias:** Todas, ★ Favoritas, Tubo (Perfil 2D, Trenza, Tubo erosionado, Expresión), Eslabones (Cadena, Cuentas, Anillos, Nudos, Espiral, Noodles), Pieles (Escamas, Escamas espinadas, Cuadros 3D, Armadura hex, Celdas Voronoi, Panal, Red) y Orgánicas (el resto). Una figura nueva sin familia asignada aparece en Orgánicas.
- **Favoritas:** la ★ a la derecha de cada botón la marca o la desmarca.
- **Recientes:** las cuatro últimas figuras elegidas, encima de la lista.
- Favoritas y recientes son de cada jugador y se guardan en su equipo en `config/komaedit/studio.json` (no viajan con recetas ni paquetes). Un archivo roto se ignora y se empieza de cero.
- Al pasar el ratón por una figura aparece su descripción completa; la de la figura elegida se ve debajo de la lista en hasta tres líneas.

### Ajustar rápido

Todos los controles numéricos (Proporciones, Modificadores y Mezcla orgánica) y las filas de ciclo (‹ ›) comparten estos gestos:

- **Rueda** sobre los botones o el valor: sube o baja un paso. Sobre la etiqueta o fuera de los controles, la rueda desplaza el panel como siempre, para no cambiar valores sin querer.
- **Mayús** con `−` `+`, la rueda o el arrastre: paso grande (×5, o el propio del control: 45° el giro, 90° la torsión, 25 % las escalas).
- **Arrastrar** el número en horizontal lo cambia como un deslizador (un paso cada 6 píxeles). **Un clic** sin mover abre el campo para escribirlo: `Intro` o pulsar fuera lo aplica (ajustado al rango), `Esc` lo cancela.
- **Clic derecho** restablece el valor de serie: el de la figura para radio, espaciado, grosor, giro y altura; el de los modificadores para el resto; en las filas de tubo quita la opción para que valga la de serie.
- Una barra fina bajo cada control muestra dónde está el valor dentro de su rango.
- **Ayuda contextual:** al pasar el ratón por la etiqueta de un control se ve qué hace, su rango, el paso, el paso grande y el valor de serie. Los botones de la barra superior y de presets también la tienen.

### Presets y variantes

Debajo de la descripción de la figura (Figuras y Estructuras):

- **Compacto** (radio ×0,6, un paso menos de espaciado, altura ×0,7), **De serie**, **Amplio** (radio ×1,5, un paso más de espaciado, altura ×1,4) y **Robusto** (radio ×1,2 y un punto más de grosor). Parten siempre de las proporciones de serie de la figura y no tocan opciones, material ni modificadores.
- **⚄ Azar** (`R`): variante con semilla alrededor de las proporciones de serie (radio de ×0,6 a ×1,6, espaciado ±2, grosor ±1, giro ±45° y altura de ×0,7 a ×1,5). La semilla aparece en la barra de estado (`Variante #4821`); la misma figura y semilla dan siempre la misma variante.
- Presets y variantes solo proponen valores dentro de los rangos del Studio y no cambian ningún generador.

### Deshacer ajustes

El Studio tiene su propio historial de ajustes, aparte del de WorldEdit:

- `Z` deshace y `Y` o `Mayús+Z` rehacen (también los botones ↶ ↷ de la barra superior). `Ctrl+Z` / `Ctrl+Y` siguen deshaciendo en el mundo.
- Guarda hasta 100 pasos. Los cambios seguidos del mismo control en menos de 0,8 s (arrastrar, la rueda o varios clics rápidos) cuentan como uno.
- Entran todos los cambios de la receta: figura, proporciones, opciones de tubo, modificadores, material, presets, variantes y cargar una receta o instancia. El nombre de la receta no entra.
- Sobrevive a cerrar y abrir el Studio y se borra al salir del mundo.

### Antes de construir

- El pie del panel y la barra inferior muestran **≈ N bloques**, el recuento del último holograma (con `…` mientras se recalcula).
- A partir de **250 000 bloques** el recuento sale en amarillo y Construir pide una segunda pulsación en 5 s; si el recuento cambia entre medias, vuelve a pedirla. Por encima de **1 500 000** (el `maxBlocksPerOperation` de serie) sale en rojo: el servidor puede rechazarlo si no tienes `komaedit.limit.unrestricted` o si su configuración es más baja.
- `H` o el botón **H** de la barra superior ocultan el holograma; se sigue calculando, así que el recuento sigue valiendo.

### Atajos de teclado

`F1` o `?` abren la chuleta dentro del Studio. Con un campo de texto abierto (nombre, fórmula, buscador, número o biblioteca de bloques) las letras se escriben y ningún atajo se dispara, y el personaje no se mueve con `W`, `A`, `S`, `D`.

| Tecla | Acción |
| --- | --- |
| `1` – `8` | Ir a una etapa, en el orden de la barra lateral |
| `Tab` / `Mayús+Tab` | Etapa siguiente / anterior |
| `Ctrl+F` o `/` | Buscar figura |
| `B` | Construir (las obras grandes piden confirmar) |
| `H` | Mostrar u ocultar el holograma |
| `Z` / `Y` (`Mayús+Z`) | Deshacer / rehacer ajustes del Studio |
| `Ctrl+Z` / `Ctrl+Y` (`Ctrl+Mayús+Z`) | Deshacer / rehacer en el mundo (WorldEdit) |
| `R` | Variante al azar con semilla |
| `Ctrl+S` | Guardar la receta |
| `Ctrl+Supr` | Limpiar la trayectoria |
| `RePág` / `AvPág` | Desplazar el panel |
| `F1` / `?` | Chuleta de atajos |
| `K` / `Esc` | Cerrar el Studio (`Esc` cierra antes la chuleta o el campo abierto) |

### Pantallas pequeñas

- La barra lateral encoge sus botones (hasta 24 píxeles) para no salirse en pantallas bajas o con escala de GUI grande.
- Con menos de 330 píxeles de alto (escala de GUI), el pie pierde la etiqueta «RECETA» y la chuleta se muestra en dos columnas.
- La barra superior omite primero el subtítulo, luego el título y por último el contador de nodos si no cabe.
- Lo que queda desplazado bajo la cabecera o el pie del panel ya no responde a clics.

## Trayectorias y hologramas

Las trayectorias se crean con puntos colocados mediante `P` o desde Procedural Studio. Los nodos pueden moverse con el gizmo XYZ y la geometría se recalcula sin modificar el mundo.

- El raycast alcanza hasta 4096 bloques cargados.
- El holograma representa las posiciones exactas de la futura construcción.
- La previsualización permanece visible al cerrar la interfaz.
- El cálculo se ejecuta fuera del hilo de renderizado, admite cancelación y conserva temporalmente el último resultado válido.

### Captura y estado

Requiere el permiso `komaedit.path`. Todos los subcomandos aceptan también el alias `ruta` (`/komaedit ruta …`, `/ke ruta …`).

```text
/komaedit path mode toggle            captura con clic derecho (como la tecla P)
/komaedit path add                    punto en la cara del bloque que miras
/komaedit path here                   punto en tu posición
/komaedit path point 120 64 -40       punto en coordenadas exactas
/komaedit path status                 resumen con botones: puntos, deshacer, invertir, cerrar, guardar
/komaedit path points                 lista los puntos con [+] insertar, [−] quitar, [aquí] y [editar]
```

`status` indica el número de puntos, si el lazo está cerrado, la longitud en bloques y los pasos de deshacer y rehacer disponibles. Con menos de dos puntos, los comandos de construcción usan la selección de WorldEdit.

### Editar puntos

Los puntos se numeran desde 1, como en el HUD. El autocompletado de `<n>` muestra las coordenadas de cada punto.

| Comando | Efecto |
|---|---|
| `path insert <n>` | Inserta un punto a mitad entre el `n` y el siguiente. Tras el último punto, en un lazo cerrado va entre el último y el primero; en uno abierto prolonga medio tramo. Falla si los dos puntos están pegados. |
| `path insert <n> here` | Inserta tu posición después del punto `n`. |
| `path remove <n>` | Quita el punto `n`. |
| `path set <n> here` · `path set <n> <x> <y> <z>` | Mueve el punto `n`. |
| `path shift <dx> <dy> <dz>` · `path shift here` | Mueve todo el recorrido; `here` lo lleva para que el primer punto quede en tu posición. |
| `path reverse` | Invierte el sentido (importa en figuras con dirección, extremos o progresión de radio). |
| `path close [on/off]` · `path open` | Cierra o abre el lazo; sin argumento lo alterna. |
| `path mirror <x/y/z>` | Refleja el recorrido dentro de su propia caja. |
| `path mirror <x/y/z> append` | Añade al final el reflejo apoyado en el último punto: media curva pasa a ser una curva simétrica completa. |
| `path rotate <90/180/270>` | Gira sobre el eje vertical alrededor del centro de su caja. |
| `path snap <paso> [all]` | Ajusta X y Z al múltiplo de `paso` más cercano (`all` también la altura). |
| `path smooth [fuerza 0.05–1] [pasadas 1–8]` | Acerca cada punto a la media de sus vecinos sin cambiar cuántos hay; los extremos de un recorrido abierto no se mueven. Por defecto `0.5` y una pasada. |
| `path simplify [tolerancia]` | Quita los puntos que se separan menos de `tolerancia` bloques (1 por defecto) de la recta entre sus vecinos (Ramer–Douglas–Peucker). |
| `path subdivide` | Añade un punto a mitad de cada tramo, si caben. |
| `path pop` | Quita el último punto (lo que hacía antes `path undo`). |
| `path clear` | Vacía la trayectoria; se puede deshacer. |

En el editor en vivo (`K`, herramienta de trayectoria), **Supr** quita el nodo señalado e **Insert** añade uno a mitad entre ese nodo y el siguiente.

Ejemplos:

```text
/komaedit path insert 3
/komaedit path shift 0 5 0
/komaedit path mirror x append
/komaedit path snap 2
/komaedit path smooth 0.7 3
```

### Deshacer y rehacer

`/komaedit path undo` y `/komaedit path redo` deshacen y rehacen cualquier cambio de puntos: añadir, mover con el gizmo, borrar, insertar, generar, cargar o vaciar. Arrastrar el mismo nodo de forma seguida cuenta como un solo paso. Se guardan los últimos 64 pasos por jugador, en memoria; se pierden al desconectarse. El historial de bloques del mundo sigue siendo el de WorldEdit (`//undo`, `//redo`, Ctrl+Z en el editor).

### Generar trayectorias

Las figuras centradas usan tu posición como centro; el arco y la catenaria van del primer al último punto del recorrido actual (o de `//pos1` a `//pos2` si no hay recorrido). Todas sustituyen la trayectoria (se puede deshacer) y respetan el máximo de 32 puntos.

```text
/komaedit path selection                                  cuboide (dos esquinas), convex (vértices) o poly (contorno)
/komaedit path circle <radio 2–128> [puntos 8–32]         círculo cerrado (16 puntos)
/komaedit path polygon <radio> <lados 3–32> [giro]        polígono regular cerrado
/komaedit path spiral <radio inicial> <radio final> <vueltas> [altura] [puntos]
/komaedit path helix <radio> <vueltas> <altura> [puntos]
/komaedit path arc <altura> [up|down|left|right] [puntos]
/komaedit path catenary <comba> [down|up|left|right] [puntos]
```

- **Espiral y hélice:** el radio pasa del inicial al final en las vueltas dadas mientras sube `altura` bloques (negativa baja). Sin `puntos`, usa unos doce por vuelta, entre 8 y 32.
- **Arco:** arco de circunferencia cuya flecha (separación máxima respecto a la recta) es `altura`; `up` por defecto. Con una altura mayor que media cuerda da más de media vuelta. `left` y `right` se miran yendo del primer punto al último.
- **Catenaria:** la curva de una cadena colgante, como `//selcatenary` de ezEdits. `comba` es la mayor distancia entre la cadena y la recta que une los extremos. Con `down` (por defecto) es la curva física: si un extremo está más alto, el punto más bajo se desplaza hacia el otro. `up` la invierte, como un arco de catenaria; `left` y `right` la tienden en horizontal. Falla si los extremos están uno encima del otro. 16 puntos por defecto.

```text
/komaedit path point 0 80 0
/komaedit path point 60 72 0
/komaedit path catenary 8
/komaedit build chainlink iron_block
```

### Recorridos guardados

```text
/komaedit path save <nombre>       guarda el recorrido actual (sobrescribe si existe)
/komaedit path load <nombre>       lo carga (se puede deshacer)
/komaedit path list                tus recorridos; clic en uno para cargarlo
/komaedit path delete <nombre>     lo borra
```

- Se guardan por mundo y jugador en `<mundo>/komaedit/players/<uuid>/paths/<nombre>.json`, con la escritura atómica del proyecto (`.tmp` y `.bak`).
- Nombres de 1 a 32 caracteres: letras sin tilde, números, `_` y `-`; se pasan a minúsculas.
- Formato 1: `{"format":1,"name":…,"dimension":…,"closed":…,"points":[[x,y,z],…]}`. Al leer se ignoran los campos desconocidos. El archivo guarda la dimensión; cargarlo en otra avisa pero no lo impide.
- Guardar con otro nombre sirve para copiar un recorrido; combinado con `shift` o `mirror`, para repetirlo en otro sitio.

### Limitaciones

- Máximo 32 puntos por trayectoria, como hasta ahora: el holograma y la red no cambian.
- Los puntos son posiciones de bloque enteras: no hay ajuste a media altura de bloque. `snap` trabaja en rejillas de 1 a 64 bloques.
- La trayectoria activa y su historial viven en memoria; para conservarla entre sesiones, guárdala con `path save`.

## Ayuda y comandos

```text
/komaedit help                     índice de temas clicables (alias: ayuda)
/komaedit help path                trayectorias
/komaedit help generate            generar trayectorias
/komaedit help saved               recorridos guardados
/komaedit help build               figuras, con todas las figuras clicables
/komaedit help tubes               tubos, banderas y fórmula
/komaedit help edit                doblar, degradados y jobs
/komaedit help region              hiedra, envejecer y retocar
/komaedit help project             proyecto y menús
```

- Cada comando de la ayuda se puede pulsar: se escribe en el chat hasta su primer argumento para completarlo. Al pasar el ratón se ve qué hace.
- El autocompletado de figuras, perfiles, paletas, direcciones e índices de punto muestra una descripción junto a cada opción.
- Los errores de nombre desconocido proponen el más parecido: figuras y sus alias, perfiles, extremos, normales, calidad, piezas, paletas, banderas de tubo, parámetros de figura, temas de ayuda y recorridos guardados. Por ejemplo, `/komaedit build coli stone` responde «Forma 3D desconocida: coli · ¿Quisiste decir «coil»?». Se acepta un error cada cuatro letras (cambiar, sobrar, faltar o intercambiar dos vecinas), sin contar tildes ni mayúsculas, o un prefijo de tres letras o más.
- Alias: `/ke`, `/mx` y `/moxia` para `/komaedit`; `ruta` para `path`; `ayuda` para `help`; `estimar` para `estimate`; `confirmar` para `confirm`.

## Diagnóstico antes de construir

### Estimar

```text
/komaedit estimate <figura> [patrón] [escala] [espaciado] [grosor] [giro]
/komaedit estimate coil stone 8
```

Calcula la misma geometría que `build` (fuera del hilo del servidor) y responde sin tocar el mundo, por ejemplo «Estimación de Coil: 120 345 bloques · 42 chunks · caja 60×24×88 · ~5 MB · ~2 s». Indica también si superaría tu límite de bloques o si pedirá confirmación.

- **Chunks:** columnas de 16×16 distintas que toca la construcción.
- **Memoria:** aproximada, unos 40 bytes por bloque mientras el plan está en cola.
- **Tiempo:** aproximado, suponiendo unos 3000 bloques por tick (20 ticks por segundo) en la cola de KomaEdit; en servidores cargados o con muchos chunks sin cargar tarda más.

### Confirmación de Build grandes

Cuando un Build supera `confirmBuildAboveBlocks` bloques (250 000 por defecto) no se encola enseguida: el chat muestra la estimación con los botones **[Construir]** y **[Descartar]**, y la barra de acción recuerda `/komaedit confirm`. Afecta al comando `build`, a los comandos de figura y tubo, y a Construir y Actualizar del Studio.

```text
/komaedit confirm            manda a la cola el Build en espera
/komaedit confirm cancel     lo descarta
```

La geometría ya está calculada, así que se construye exactamente lo estimado. La espera caduca a los dos minutos, al desconectarse o al pedir otro Build. Con `confirmBuildAboveBlocks` a `0` no se pregunta nunca.

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
- Red (fishnet)
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

Chainlink, Rings, Beads, Oscillate, Red, Honeycomb, Knots y Coil pueden generarse además con el [motor exacto](#motor-exacto-de-las-figuras-clásicas), que las evalúa en espacio de tubo sin huecos.

## Red

### Objetivo y acceso

La figura **Red** (`fishnet`) envuelve el recorrido con dos familias de hilos helicoidales que giran en sentidos contrarios a ±45°, de modo que las celdas salen cuadradas (rombos vistos de lado) a cualquier escala. Se elige en **Figuras** del Studio o con el comando general:

```text
/komaedit build fishnet <patrón> <escala> <espaciado 2–8> <grosor 1–4> <giro -180–180>
```

También se acepta `net`, `red` o `malla`.

### Parámetros y comportamiento

- **Escala:** radio del cilindro sobre el que corren los hilos (`6` por defecto).
- **Espaciado:** ancho de celda; hay un hilo por familia cada `1,2 × espaciado` bloques de perímetro, entre `3` y `24` hilos.
- **Grosor:** diámetro de los hilos, como en el resto de figuras de alambre.
- **Giro:** desplaza el arranque de los hilos alrededor del eje.
- Los hilos dan una vuelta completa cada perímetro recorrido; con el motor clásico se trazan con pinceles y con el exacto salen en una sola pieza.

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

**Perfil 2D** (`sweep`), **Trenza** (`braid`), **Tubo erosionado** (`noisetube`) y **Expresión** (`expression`, en su propia sección) se generan con un motor distinto al de las demás figuras: en lugar de estampar pinceles a lo largo de la curva, recorre los bloques cercanos al eje y evalúa en cada uno una función implícita de la figura. El resultado es estanco por construcción, sin puentes ni reparación posterior. Ocho figuras clásicas pueden usar también este motor con la opción `engine:exact` (ver [Motor exacto de las figuras clásicas](#motor-exacto-de-las-figuras-clásicas)).

- Studio: aparecen en **Figuras**. Las tres añaden filas de **Extremos**, **Normales** y **Piezas** (con **Cobertura** cuando hay piezas); **Perfil 2D** añade además **Perfil** y **Relleno**.
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
- **Losas y escaleras:** opcionalmente la superficie se termina con piezas del mismo material; ver [Losas y escaleras en la superficie](#losas-y-escaleras-en-la-superficie).

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
| `-w <modo>` / Piezas | `smooth` | `none` (bloques enteros), `slabs` (losas) o `stairs` (losas y escaleras). |
| `-c <0–1>` / Cobertura | `coverage` | Cuánta superficie se cubre con piezas: `0` solo bloques enteros, `0.5` (por defecto) la pieza que mejor encaja, `1` toda la superficie que no esté entera. `-c` sin `-w` activa `stairs`. |

Perfiles: `circle`, `square`, `diamond`, `rounded`, `polygon`, `star`, `flower`, `rope`, `ellipse` y `rectangle` (también en español: `circulo`, `estrella`, `cuerda`…). Un número al final fija los lados: `polygon6`, `star5`, `rope4`.

En recetas y expresiones también se aceptan nombres: `sweep(profile:star,sides:7,end:round,normal:horizontal,hollow:true)` o `braid(smooth:stairs,coverage:0.7)`. En el comando general la expresión va entre comillas: `/komaedit build "sweep(profile:star,end:round)" stone 8`.

Ejemplos:

```text
/komaedit sweep circle 8
/komaedit sweep star5 "2,0.3:12,2" polished_andesite -e spike -t 30
/komaedit sweep rope3 6 oak_planks
/komaedit sweep polygon6 10 deepslate_tiles -hollow 2 -n horizontal
/komaedit braid 7 stripped_birch_wood -k 4
/komaedit noisetube 2_12_4 60%stone,25%andesite,15%cobblestone -e round
/komaedit sweep circle 9 stone_bricks -w stairs
/komaedit sweep ellipse 12 smooth_stone -a 0.4 -n horizontal -w slabs -c 0.7
```

### Losas y escaleras en la superficie

Los tubos hechos solo con bloques enteros dejan escalones en la superficie. Con **Piezas** (`-w`) los bloques del borde que la figura solo ocupa en parte se sustituyen por losas o escaleras orientadas del mismo material, y los huecos vacíos que la figura casi llena reciben una pieza.

- **Medición:** cada bloque de la superficie, y cada hueco vacío pegado a ella, se divide en 8 octantes. La misma función implícita que decide la figura mide qué fracción de cada octante queda dentro: el centro de cada octante y, si el bloque está a medias, cuatro muestras más en damero (con calidad `fast`, solo el centro). Para no localizar el eje en cada muestra, se localiza el centro del bloque y tres puntos a un cuarto de bloque y el resto se interpola; cerca de los extremos, en la costura de los lazos o donde el eje salta, cada muestra se localiza por separado.
- **Elección:** las piezas candidatas son las dos losas y, con `stairs`, las escaleras rectas en las cuatro orientaciones, apoyadas o colgadas. Gana la que menos se aleja de la ocupación medida, y sustituye al bloque (o rellena el hueco) si mejora el encaje en más que el umbral de la cobertura. Con `0.5` basta con encajar mejor; hacia `0` hace falta tanta ventaja que no se usa ninguna; con `1` se aceptan piezas hasta dos octantes peores.
- **Esquinas como en vanilla:** al colocar bloques, Minecraft recalcula la forma de cada escalera según las escaleras de alrededor. El plan ya trae esa forma (esquinas interiores y exteriores) calculada con la misma regla, así el mundo queda igual que el holograma.
- **Material:** la pieza sale del bloque que elige el patrón en esa posición, también con patrones aleatorios, degradados y **Patrón figura**. La familia se busca por nombre (`stone` → `stone_slab`/`stone_stairs`, `stone_bricks` → `stone_brick_*`, `oak_planks` o `oak_log` → `oak_*`, `quartz_pillar` → `quartz_*`, cobre → cobre cortado) con una tabla para los casos sin convención (`deepslate` → `cobbled_deepslate`, `smooth_stone` → losa propia y escalera de `stone`, `end_stone` → `end_stone_brick`…). Funciona igual con bloques de otros mods que sigan la convención.
- **Sin familia:** si el bloque no tiene losas o escaleras (hormigón, lana, basalto…), se usa la losa o escalera registrada con el color de mapa más parecido, solo si el color se parece de verdad. Si tampoco hay, el bloque de la figura se queda entero y la pieza que iba en un hueco vacío no se coloca.
- **No se convierten:** cristal, hojas y demás bloques no opacos, bloques con entidad (cofres, hornos…) y patrones que ya son losas o escaleras (estos solo se orientan).
- **Holograma:** dibuja las losas y escaleras con su forma real, por medios bloques, y el estado indica cuántas hay.

Limitaciones:

- En tubos huecos no se añaden piezas fuera y la capa que toca el interior se queda entera, para que ninguna losa abra la pared.
- Solo se tocan bloques que coloca la propia figura de tubo: la figura de acento del ritmo y los bloques que añade **Cerrar huecos** se quedan enteros.
- Las escaleras son de una sola pieza por bloque: superficies muy finas (hebras de trenza de 1–2 bloques) pueden quedar con escalones de media altura.
- Si junto al tubo ya había escaleras en el mundo, Minecraft puede doblar las del borde hacia ellas.

### Materiales

Con **Patrón figura** las figuras de tubo usan su propio valor implícito: en los perfiles va del núcleo (`0`) al borde (`1`), y en cuerdas y trenzas cada hebra ocupa su tramo de la paleta, de modo que una paleta de tres colores pinta tres hebras distintas. **Relieve**, **Frente / reverso** y **Filas alternas** funcionan igual que en el resto de figuras.

### Rendimiento y limitaciones

- Solo se evalúan ladrillos de 8×8×8 bloques que tocan el tubo; un árbol de esferas sobre el eje localiza el punto más cercano en tiempo logarítmico y parte del resultado del bloque anterior.
- Un tubo de radio 14 y 360 bloques de recorrido (~220 000 bloques) se calcula en menos de un segundo en un worker.
- El volumen candidato se limita a 64 millones de bloques y el resultado al límite general de 1 500 000.
- Con normales `upright` las secciones se inclinan en tramos empinados y pueden solaparse; en tramos verticales conviene `consistent`.
- Si una curva se cierra más que el propio radio, el tubo se pliega por dentro como en cualquier barrido: separa más los puntos o reduce el radio.
- El tubo erosionado conserva solo la pieza conectada al núcleo, sin fragmentos flotantes.
- Las losas y escaleras solo miden la superficie. En un tubo de radio 14 y unos 360 bloques de recorrido (~230 000 bloques) añaden unos 0,3 s a los 0,6 s de la figura en calidad `balanced`; en radio 6, menos de 0,1 s. Solo cuentan como bloques nuevos las piezas que rellenan huecos.

## Motor exacto de las figuras clásicas

### Objetivo y acceso

Las figuras **Chainlink**, **Rings**, **Beads**, **Oscillate**, **Red**, **Honeycomb**, **Knots** y **Coil** se generan por defecto estampando pinceles a lo largo de la curva (motor **clásico**). El motor **exacto** reimplementa cada una como función implícita en [espacio de tubo](#figuras-de-espacio-de-tubo) y la evalúa con el mismo voxelizador inverso que `sweep`, `braid` y `noisetube`: mide la distancia de cada bloque al hilo, al anillo o a la cinta, así que el resultado no tiene huecos aunque el recorrido vaya en diagonal.

- **Studio:** estas figuras muestran la fila **Motor** (`Clásico` / `Exacto`). Con **Exacto** aparecen también **Extremos**, **Normales** y **Piezas** (con **Cobertura** cuando hay piezas).
- **Recetas y expresiones:** opción `engine` (`0` clásico, `1` exacto) o su nombre: `engine:exact`, `engine:classic` (también `motor:exacto`).
- **Comando general:** la figura admite una expresión entre comillas:

```text
/komaedit build "coil(engine:exact)" stone 8 5 2 0
/komaedit build "chainlink(engine:exact,end:round,twist:45)" iron_block 6
/komaedit build "beads(engine:exact,k0:40,k1:100,k2:40)" glass 5
/komaedit build "fishnet(engine:exact,normal:horizontal)" dark_oak_fence 10 6 1
```

El motor por defecto es el clásico: las recetas, instancias e instantáneas existentes no cambian de aspecto.

### Comportamiento

- **Mismas proporciones:** radio, espaciado, grosor y giro se leen con las mismas fórmulas que el motor clásico, igual que `extrusion`, `gap`, `stretch` (longitud del eslabón), `majorexponent`, `minorexponent` y `thickness`. El hilo de grosor `n` mide lo mismo que el pincel clásico (`n − 0,65` de radio).
- **Sin huecos:** el alma de cada hilo queda cubierta en cualquier dirección y la cinta de Oscillate no se puede atravesar. Por eso hilos y cintas tienen un radio (o semigrosor) mínimo de `0,87` bloques: con grosor `1` salen algo más gruesos que en el motor clásico.
- **Hereda el espacio de tubo:** extremos (`end`), normales (`normal`), radios por fotogramas (`k0`…`k5`, `p1`…`p4`), torsión (`twist`, `rollend`), curva Kochanek–Bartels (`tension`, `bias`, `continuity`), calidad (`quality`), losas y escaleras en la superficie (`smooth`, `coverage`) y la escala, espaciado y torsión de la sección Deformar, que se aplican al evaluar cada bloque.
- **Espaciado sin aplastar:** el espaciado de la curva de modificadores y `stretch` separan los motivos (eslabones, anillos, cuentas, nudos) o abren la hélice sin aplastar el alambre. En Chainlink `stretch` conserva su significado clásico y alarga cada eslabón.
- **Extremos:** con `flat` el primer y el último motivo salen enteros, como en el motor clásico; las figuras continuas se cortan en el plano del extremo. `round`, `spike`, `soft` y `cube` actúan sobre la figura como en `sweep`.
- **Lazos cerrados:** el paso se ajusta para que quepa un número entero de motivos; en Chainlink y Honeycomb el número es par para que la alternancia empalme sin costura.
- **Por figura:**
  - **Chainlink:** eslabones superelípticos alternados 90°; `minorexponent` da además la sección del alambre (`2` redonda, más alto casi cuadrada).
  - **Rings:** supertoros con la corona y el fondo del motor clásico.
  - **Beads:** cáscaras de `grosor + 0,45` bloques como las clásicas; `hollow:false` las rellena.
  - **Oscillate:** cinta de un diámetro que gira una vuelta por longitud de onda mientras su centro oscila `0,42` radios.
  - **Red, Honeycomb, Knots y Coil:** hilos con la misma trayectoria que en el motor clásico (hélices, hexágonos con puntales, tréboles 2:3 girados `0,47 rad` entre estaciones y una hélice).
- **Determinismo:** holograma y Build producen los mismos bloques.

### Materiales

Con **Patrón figura** el motor exacto usa el valor implícito de cada figura: en Chainlink cada orientación de eslabón ocupa una mitad de la paleta, en Beads se alternan las mitades por cuenta, en Red cada familia de hilos tiene su mitad, en Honeycomb los anillos usan una mitad y los puntales la otra, y dentro de cada franja el color va del alma (`0`) a la superficie (`1`). En Rings, Knots y Coil va del alma a la superficie del hilo y en Oscillate del centro al borde de la cinta.

### Rendimiento y limitaciones

- El coste depende del volumen del tubo que envuelve la figura, no del número de estampados. Las figuras miden su holgura hasta la superficie y el voxelizador no supermuestrea los bloques claramente vacíos.
- Con radio `16` y 480 bloques de recorrido cada figura exacta tarda entre `0,5` y `1,4` s en un worker; la clásica, milisegundos. Con radio `8` rondan `0,3`–`0,6` s.
- El volumen suele quedar entre `0,9` y `2,5` veces el del clásico: los hilos de grosor `1` engordan para no dejar huecos y las figuras clásicas que no cerraban (Red, Honeycomb, Chainlink) quedan ahora unidas.
- Como en cualquier barrido, si la curva se cierra más que el alcance de la figura, el lado interior se pliega: separa más los puntos o reduce la escala.
- `cube` añade un remate macizo en los extremos de cualquier figura.

## Doblar estructuras del portapapeles

### Objetivo y acceso

Curva un módulo copiado con WorldEdit (un tramo de muro, puente, calzada, vía o valla) a lo largo de la trayectoria: o lo repite copia tras copia, o estira una sola copia por todo el recorrido. A diferencia de los sockets Kitbash normales, que pegan el módulo entero en puntos sueltos, aquí el módulo se dobla con la curva.

- Comando rápido, solo la estructura:

```text
/komaedit bend [repeat|stretch] [banderas]
/komaedit doblar [repetir|estirar] [banderas]
```

- En recetas: en **Modificadores → Kitbash**, la fila **Colocar** tiene dos modos nuevos, **Doblar** (repetir) y **Estirar**, que se suman a la figura de la receta y quedan dentro de la instancia, así que se pueden actualizar o retirar.

### Comportamiento

- Se usa el portapapeles de WorldEdit (`//copy`). Su origen, el bloque donde estabas al copiar, es el que recorre la trayectoria.
- **Repetir:** en un recorrido abierto caben tantas copias enteras como permita la longitud, centradas, sin trozos cortados. En uno cerrado, las copias se ajustan un poco para que el lazo empalme sin costura.
- **Estirar:** una sola copia ocupa todo el recorrido.
- Igual que las figuras de tubo, se trabaja al revés: cada bloque del mundo cercano al eje pregunta qué bloque del módulo le toca. Por fuera de las curvas el módulo se estira sin abrir huecos; por dentro se comprime y puede perder algún detalle fino.
- **Normales `upright` (por defecto):** el módulo se asocia a la vertical de cada punto del recorrido, así que en una cuesta los postes y muros quedan a plomo y la altura se mide en bloques del mundo. Con `horizontal` o `consistent` la sección se inclina con la pendiente.
- Escaleras, troncos, vallas y demás bloques con dirección se giran en cuartos de vuelta para seguir la curva.

### Parámetros

| Bandera | Efecto |
| --- | --- |
| `repeat` / `stretch` | Repetir copias (por defecto) o estirar una sola. |
| `-a <x\|z\|-x\|-z>` | Dirección del portapapeles que avanza con el recorrido. Por defecto, el lado horizontal más largo; el comando dice cuál ha usado. Con módulos más anchos que largos (un tramo de puente, por ejemplo) hay que indicarla. |
| `-g <bloques>` | Hueco entre copias al repetir; admite valores negativos para solaparlas. |
| `-y <bloques>` | Sube o baja el módulo respecto al recorrido. |
| `-n <modo>` | `upright`, `horizontal` o `consistent`. |
| `-p t:b:c` | Tensión, sesgo y continuidad de la curva Kochanek–Bartels. |

Ejemplos:

```text
/komaedit bend
/komaedit bend -a x -g 2
/komaedit bend stretch -n horizontal
/komaedit doblar repetir -a z -y -1
```

### Rendimiento y limitaciones

- El portapapeles admite hasta 100 000 bloques y como mucho 128 bloques de alto o de lado desde el origen.
- Un tramo de puente de 7×3×6 doblado sobre 85 bloques de curva (unos 850 bloques) se calcula en menos de 0,1 s en un worker.
- Los giros de bloques van en cuartos de vuelta: una escalera no puede quedar a 30°.
- El holograma del Studio no muestra el módulo doblado (igual que el resto de Kitbash): se calcula en el servidor al pulsar Build.

## Envejecer

### Objetivo y acceso

Convierte una construcción terminada en una ruina creíble. No es ruido al azar: el desgaste sigue la exposición a la intemperie, lo que pierde el apoyo se cae y forma montones de escombro, y la vida crece donde tiene sentido. Ningún otro editor hace esto sobre lo que ya está construido.

- Selecciona la obra con WorldEdit (`//pos1`, `//pos2`; vale cualquier forma de selección) y escribe:

```text
/komaedit weather [intensidad] [clima] [-d derrumbe] [-v vida] [-seed semilla] [-agua]
/komaedit envejecer [intensidad] [clima] [-d derrumbe] [-v vida] [-seed semilla] [-agua]
```

- En el Studio, la categoría **Envejecer** tiene los cuatro climas, los pasos de **Intensidad**, **Derrumbe** y **Vida**, el interruptor de **Agua estancada** y el botón **Envejecer selección**, que lanza el mismo comando.
- Permiso: `komaedit.build` o `komaedit.build.weather`.

### Comportamiento

El cálculo imita el orden en que trabaja el tiempo:

1. **Desgaste por exposición.** Cada bloque mide sus caras y sus 26 vecinos al aire, ponderados por la intemperie de ese aire: 1 bajo el cielo abierto, 0,6 y 0,3 a uno y dos pasos de él (una ventana, bajo un alero) y 0,1 en el aire cerrado de una habitación o de un tejado hueco. Así las aristas, esquinas y almenas se pierden antes que una cara lisa, lo alto antes que lo bajo, y lo que está bajo techo apenas se gasta mientras el tejado aguante. Un ruido suave por semilla agrupa el daño en mordiscos de unos 4 bloques en vez de agujeros sueltos. Se hacen de una a cuatro pasadas según la intensidad, recalculando la exposición, de modo que el daño entra desde los bordes. El vidrio y la lana se van antes que la piedra, la madera algo antes, y la pizarra profunda, la piedra negra y el cobre aguantan más.
2. **Derrumbe.** Se mide, capa a capa desde el suelo, la distancia en horizontal de cada bloque a una columna que baje hasta algo firme, como el andamio de Minecraft. El apoyo baja en vertical sin coste y en diagonal (arista con arista) con coste uno, que es como descansan los tejados de escaleras y los escalonados. Si la distancia supera el vano que aguanta el material, el bloque cae: con derrumbe 50 la piedra vuela 5 bloques, con 100 solo 2; la madera y el metal aguantan 2 más y la pizarra 1 más. Lo que queda suelto del todo cae siempre (salvo con derrumbe 0, que desactiva esta fase). Sostienen la obra el terreno, los bloques protegidos y lo que está fuera de la selección.
3. **Escombro.** Lo que cae y parte de lo desgastado baja en vertical (con un pequeño desvío), resbala si el montón queda dos bloques más alto que el hueco de al lado y se amontona con su propio material: adoquín, grava y andesita bajo la piedra, adoquín de pizarra bajo la pizarra, ladrillo bajo el ladrillo, tablones bajo la madera. La cara de arriba del montón se cubre de losas y de escaleras apoyadas contra lo que tienen al lado. Una parte se pulveriza (todo el vidrio, el 45 % de la piedra).
4. **Variantes envejecidas.** Conservan las propiedades del original (una escalera musgosa mira hacia el mismo lado):

| Envejecimiento | Bloques | Dónde |
| --- | --- | --- |
| Musgo | adoquín y ladrillo de piedra, con sus escaleras, losas y muros | abajo, en caras norte, a la sombra y cerca del suelo, en manchas |
| Grieta | ladrillo de piedra (y cincelado), ladrillo y baldosa de pizarra profunda, ladrillo del Nether, ladrillo de piedra negra pulida | lo más expuesto y alto; más con helada (nevado) |
| Desgaste | lo pulido o tallado pierde el acabado: andesita, diorita, granito, pizarra, piedra negra y toba pulidas, piedra lisa, arenisca cortada, lisa y cincelada, cuarzo; la escalera y la losa de piedra pasan a adoquín | según exposición; en el desierto el ladrillo pasa a terracota |
| Óxido | cobre en bloque, cortado, escaleras, losas, cincelado, rejilla, puerta, trampilla y lámpara: de normal a expuesto, erosionado y oxidado, hasta tres pasos según la intensidad | todo lo que se ve; **el cobre encerado no cambia** |
| Corteza | troncos, madera, tallos e hifas pasan a descortezados | más en el desierto |
| Podrido | los tablones quedan en media losa de la misma madera | según exposición |

5. **Vida**, según el clima:
   - `templado` (por defecto): hierba y helechos sobre tierra, musgo en alfombra y algún bloque de musgo con hierba en lo alto, enredaderas que cuelgan de lo alto de los muros al sol (más en las caras norte), telarañas en rincones oscuros con techo y dos paredes, y raíces colgantes bajo techo.
   - `desierto`: arena acumulada contra los muros y en las esquinas, sobre todo abajo; arbustos secos sobre arena y terracota; el escombro lleva arena y terracota; sin musgo.
   - `nevado`: capas de nieve sobre todo lo que mira al cielo (más gruesas contra las paredes); los charcos son hielo; poco musgo y más grietas.
   - `hundido`: el aire de la selección se vuelve agua y lo que admite agua queda anegado; crecen algas, kelp de 2 a 6 bloques y pepinos de mar; más musgo. Pensado para ruinas bajo el mar o para seleccionar hasta el nivel del agua.
6. **Agua estancada** (`-agua`): rellena con agua (hielo en el clima nevado) los huecos pequeños del suelo cerrados por todos los lados, de una capa y hasta 40 bloques.

Antorchas, faroles, velas, alfombras y estandartes desaparecen con la intensidad (nadie los repuso) y lo pequeño que se queda sin nada que lo sujete se cae.

### Parámetros

| Parámetro | Efecto |
| --- | --- |
| `intensidad` (0–100, 50 por defecto) | Cuánto tiempo ha pasado. Con 0 no cambia nada; hacia 50 la obra está vieja pero en pie; desde 80 se hunden tejados y suelos. |
| `clima` | `templado`, `desierto`, `nevado` o `hundido` (también en inglés: `temperate`, `desert`, `snowy`, `sunken`). |
| `-d <0–100>` | Derrumbe (50 por defecto): lo que vuela cada material sin apoyo. 0 no tumba nada. |
| `-v <0–100>` | Vida (50 por defecto): cuánta vegetación, nieve, arena o algas; escala también con la intensidad. |
| `-seed <n>` | Semilla. Con la misma semilla y la misma obra el resultado es idéntico. Sin ella se elige una al azar y el chat la dice para poder repetirla. |
| `-agua` | Charcos de agua estancada. |

Ejemplos:

```text
/komaedit weather
/komaedit envejecer 30
/komaedit envejecer 85 -d 80 -v 70 -agua
/komaedit weather 70 nevado -d 40 -seed 1234
/komaedit envejecer 60 desierto -v 30
/komaedit envejecer 70 hundido -v 80
```

### Materiales y bloques protegidos

- Solo cambian los bloques dentro de la selección; el borde de un bloque alrededor se lee para saber qué sostiene la obra y qué está a la intemperie.
- El terreno (hierba, tierra, piedra, pizarra profunda, arena, grava, nieve, hielo, menas…) sostiene y recibe vegetación, pero no se desgasta. La piedra natural cuenta como terreno.
- Nunca se tocan los bloques con contenido o de dos piezas: cofres, barriles, cajas de shulker, hornos, tolvas, generadores, atriles, camas, puertas, vasijas, etc.
- Los bloques de otros mods se desgastan y se caen como piedra, pero no tienen variantes.

### Rendimiento y limitaciones

- Usa el motor común de operaciones sobre la selección (como los retoques): la selección y un bloque de margen se leen repartidos entre ticks sin congelar el servidor, el cálculo va en un worker y tarda alrededor de 1 s con 2 millones de bloques, y el resultado se coloca como una edición por lotes que se deshace entera con `//undo`.
- Hasta 2 000 000 bloques de selección por vez; a los cambios se les aplica además el límite de bloques por operación de la configuración.
- Si alguien cambia un bloque de la selección entre la captura y la colocación, ese bloque se respeta y el chat lo cuenta como protegido.
- Las plantas se escriben con su estado completo sin comprobar que puedan vivir ahí: Minecraft puede romper alguna si se actualiza un vecino. Por eso la hierba solo sale sobre tierra o musgo, y sobre piedra se pone alfombra de musgo.
- `hundido` llena de agua todo el aire de la selección: en tierra firme queda un bloque de agua con la forma de la selección.
- No hay holograma previo: para probar, envejece, mira y usa `//undo`, o cambia de semilla.

## Figura Expresión

### Objetivo y acceso

**Expresión** (`expression`) construye la figura a partir de una fórmula escrita por el jugador y evaluada en el espacio de tubo: la misma curva, el mismo voxelizado estanco y las mismas opciones de extremos, normales, torsión, radios por fotogramas, hueco y calidad que **Perfil 2D**, pero la sección (y su evolución a lo largo del recorrido) la decide la fórmula.

- Studio: en **Figuras**, botón **Expresión**. La fila **Fórmula** recorre las fórmulas de serie; debajo, un campo muestra la fórmula activa (en gris si es de serie). Al pulsarlo se edita partiendo de la fórmula activa: **Intro** la aplica, **Esc** cancela, **Ctrl+V** pega y **Ctrl+Retroceso** la borra; pulsar fuera también la aplica. Mientras se escribe, una línea amarilla y el estado señalan el error y la posición; si la fórmula no compila no se aplica, y al volver a abrir el campo aparece lo escrito para corregirlo. Vaciar el campo, o elegir otra con la fila, vuelve a la de serie. **Repeticiones (n)** da valor a `n` y **Pared (hueco)** con **Relleno** vacía la figura como en Perfil 2D; **Extremos**, **Normales**, **Piezas** y **Cobertura** funcionan igual que en las demás figuras de tubo, también las losas y escaleras de la superficie.
- Comando rápido (también `/komaedit expresion`):

```text
/komaedit expr <radios> [banderas] [patrón] <fórmula…>
```

Las banderas van delante (`-e -n -t -r -p -q -s -hollow -seed -w -c`, las mismas de los tubos), después el patrón opcional y al final la fórmula, que puede llevar espacios. El patrón se reconoce compilando: si todo el texto es una fórmula válida no hay patrón; si no, la primera palabra es el patrón y el resto la fórmula. En lugar de una fórmula vale el nombre de una de serie, con `n` pegado si se quiere cambiar (`ventanas8`). Si la fórmula empieza por un signo menos, envuélvela en paréntesis para que no se lea como bandera.

En expresiones de figura (`/komaedit build`) la de serie se elige con `preset`: `expression(preset:lattice,end:round)`.

### Variables y lenguaje

| Variable | Significado |
| --- | --- |
| `x`, `y` | Posición en la sección, en radios, de `-1` a `1`. Fuera del cuadrado `[-1, 1]` no se evalúa nada. |
| `z` | Avance a lo largo del recorrido, en radios (empieza en `0`; con `-s` se estira o comprime). |
| `t` | Progreso a lo largo del recorrido, de `0` al principio a `1` al final. |
| `r`, `a` | Distancia al eje (`sqrt(x^2+y^2)`) y ángulo en radianes (`atan2(y, x)`, de `-pi` a `pi`). |
| `n` | Repeticiones: **Repeticiones (n)** del Studio o el número pegado a la fórmula de serie (2–32). |
| `pi`, `e` | Constantes. |

- **Salida:** un valor mayor que `0` coloca bloque; `0`, negativo o no numérico (`NaN`) lo deja vacío. Con **Patrón figura** el valor, limitado a `(0, 1]`, elige el tramo de la paleta, así que una fórmula puede pintar piezas distintas (`riel ? 1 : traviesa ? 0.4 : 0`).
- **Operadores:** `+ - * / % ^`, paréntesis, comparaciones `< <= > >= == !=` (devuelven `1` o `0`), lógicos `&& || !` (verdadero es mayor que `0`) y el ternario `condición ? sí : no`. `-x^2` es `-(x^2)` y `2^3^2` es `2^9`. `%` conserva el signo del dividendo, como en WorldEdit; `mod(a, b)` es siempre positivo.
- **Funciones:** `sin cos tan asin acos atan atan2 abs sqrt cbrt exp ln log log10 pow min max floor ceil round frac mod sign clamp hypot noise`. `min` y `max` admiten de 2 a 8 argumentos; `clamp(v, mín, máx)`; `noise(x, y, z)` es ruido de Perlin en `[0, 1]` con la semilla de la figura (`-seed`, o una derivada de los puntos).
- **Definiciones:** al principio se pueden fijar valores con `nombre = valor;` y usarlos después, como en `R=0.2; w=0.7; sqrt((abs(x)-w)^2+y^2) < R`. Las que solo dependen de números se calculan una vez al compilar. `r`, `a` y `n` se pueden redefinir; `x`, `y`, `z`, `t`, `pi`, `e` y las funciones no.
- **Mayúsculas:** los nombres las distinguen, como en WorldEdit: `R` y `r` son variables distintas, y `SIN(x)` no existe (el error sugiere `sin`).

Fórmulas de serie:

| Nombre | Alias | Figura |
| --- | --- | --- |
| `windows` | `ventanas` | Tubo con `n` ventanas por vuelta y suelo corrido. |
| `rails` | `railes`, `via` | Dos raíles sobre traviesas, como una vía. |
| `stripes` | `espiral`, `franjas` | Núcleo macizo con `n` franjas en espiral en relieve. |
| `lattice` | `celosia`, `rejilla` | Piel de barras cruzadas en diagonal, `n` por vuelta. |
| `ribs` | `costillas`, `anillos` | Anillos cada `6/n` radios unidos por una quilla. |

Ejemplos:

```text
/komaedit expr 8 ventanas
/komaedit expr 10 stone_bricks ventanas8
/komaedit expr 6 -e round polished_andesite r<1 && abs(sin(a*3+z)) > 0.4
/komaedit expr 2_9_2 -t 45 deepslate_tiles abs(x) + abs(y) < 1 && frac(z/2) < 0.7
/komaedit expr 12 R=0.2; w=0.7; sqrt((abs(x)-w)^2+y^2) < R || abs(y+0.6) < 0.1 && abs(x) < w
/komaedit expr 9 mossy_cobblestone r < 0.7 + 0.3*noise(x*2, y*2, z)
```

### Seguridad y errores

- La fórmula admite como máximo 256 caracteres, 16 definiciones, 40 niveles de anidamiento y 400 operaciones.
- No hay bucles, asignaciones fuera de las definiciones iniciales, funciones con efectos ni estado compartido: cada punto cuesta un número fijo de pasos y la misma fórmula da siempre la misma figura, en el holograma y en el Build.
- Los errores llegan en español y señalan la posición: `Fórmula, posición 10: Variable desconocida «foo»…`. Una fórmula inválida se rechaza al escribir el comando, al aplicarla en el Studio y al leer la receta; nunca llega al voxelizado.

### Recetas

La fórmula propia se guarda como texto en la receta (campo `formula`, formato de receta 4) y viaja por red con ella; la de serie se guarda como la opción numérica `preset`, así que una receta sin fórmula propia sigue usando solo opciones numéricas. Las recetas, instancias y paquetes `.krecipe` de formatos 1–3 se leen sin cambios (con la fórmula de serie), y no hace falta migrar nada en disco. Las versiones anteriores del mod no abren recetas de formato 4.

### Rendimiento y limitaciones

- La fórmula se compila una vez a un árbol con las constantes plegadas y cada worker la evalúa con su propio marco de variables: entre 10 y 60 ns por punto en las fórmulas habituales, poco frente a localizar el punto en el eje.
- **Alcance:** si la fórmula se limita a un radio (`r <= 1 && …`, `c - r`, `(r < c) * …`, también a través de definiciones y ternarios), el motor solo recorre hasta ese radio; si no, recorre hasta las esquinas del cuadrado (√2 radios), que cuesta más del doble. Empezar por `r <= 1 &&` cuando la figura cabe en el círculo la acelera sin cambiarla.
- Medido en un tubo de radio 10 sobre 360 bloques de recorrido: `r <= 1` tarda lo mismo que el perfil círculo (~0,36 s, 117 000 bloques); ventanas ~0,5 s y celosía ~0,6 s.
- No se usa el motor de expresiones de WorldEdit: en 7.3.8 cada evaluación consulta el reloj, busca variables en un mapa y crea objetos (150–335 ns por punto medidos, frente a 12–63 ns aquí con las mismas fórmulas), y su lenguaje permite bucles y búferes globales.
- Las piezas muy finas (menos de un bloque en el radio elegido) pueden perder continuidad; sube el radio o usa `-q high`.
- `x` e `y` se miden en radios: con radios por fotogramas la figura entera escala con el tubo.

## Hiedra viva

### Objetivo y acceso

Una enredadera que **crece de verdad sobre lo que ya está construido**: se pega a las caras de los bloques, trepa muros, entra en las jambas de las ventanas, cruza tejados, cuelga de aleros y dinteles, se ramifica y echa matas de hojas y flores. No se dibuja una figura: la forma sale de la construcción que tenga delante.

```text
/komaedit ivy [estilo] [banderas]
/komaedit hiedra [estilo] [banderas]
/komaedit ivy cancel
```

- **Semillas:** los puntos de trayectoria marcados con **P** (uno o varios, hasta 32). Si no hay ninguno, la celda delante de la cara del bloque que miras. Cada semilla busca la superficie más cercana en 4 bloques; las que no encuentran ninguna se saltan y se avisa.
- Permiso: `komaedit.build` o `komaedit.build.ivy`.
- Todo va a una sola edición por lotes: **//undo** la quita entera y `/komaedit job status|cancel` funciona como en el resto de figuras.

### Comportamiento

- Se captura el entorno de las semillas repartido por ticks (sin congelar el servidor) y el crecimiento se calcula en un worker.
- Cada semilla lanza varios tallos. Cada punta avanza bloque a bloque por las celdas de aire que tienen al lado una cara entera de un bloque (o, por encima, algo donde apoyar una alfombra). Elige el paso por su rumbo, con querencia a subir, una deriva aleatoria y menos ganas de pisar por donde ya pasó otra punta; nunca atraviesa un bloque ni se cuela entre dos que se tocan por la arista.
- Por el suelo se cansa el doble de rápido: busca muros, no alfombra el terreno.
- **Ramas:** en cada paso puede salir una rama de lado sobre la misma superficie, con parte de la energía que le queda a la punta, hasta cinco niveles.
- **Gravedad:** donde una enredadera llega al borde de abajo de una pared (un dintel, un alero, una cornisa) puede colgar una tira que hereda sus caras, como hace la enredadera de Minecraft al crecer hacia abajo. Si una punta se queda sin sitio al que agarrarse en un borde, cuelga lo que le queda. De los techos caen tiras de bayas brillantes en los estilos que las tienen.
- **Huecos cortos:** una punta puede cruzar por el aire hasta dos bloques de lado si al otro lado hay donde agarrarse; esos tramos son hojas.
- Al final cada celda se viste según sus apoyos: **enredadera** con las caras exactas de los bloques que la sostienen (incluido `up` bajo un techo), **alfombra de musgo** en lo horizontal y **matas de hojas** por ruido, más tupidas cerca de la raíz y donde se cruzan varias puntas. Las hojas van con `persistent=true`, así que no se secan.
- Solo ocupa celdas que eran aire y lo comprueba otra vez al colocar: si alguien ha construido ahí mientras crecía, esa celda se respeta.
- Con la misma semilla, el mismo sitio y los mismos parámetros sale la misma hiedra. Si no se fija `-seed`, se elige una y el chat la dice para poder repetirla.

### Estilos y materiales

| Estilo | Bloques |
| --- | --- |
| `hiedra` (por defecto) | Enredadera, hojas de roble y de azalea, alfombra de musgo. Bajo los techos, enredadera pegada arriba. |
| `florido` (`flores`) | Enredadera, hojas de azalea, mitad en flor, tiras de bayas brillantes con muchas bayas y alguna flor de esporas. |
| `selvatico` (`selva`) | Mucha hoja de jungla y de azalea, algo de flor, tiras colgantes más largas y pocas bayas. |
| `cueva` (`gruta`) | Liquen brillante mezclado con la enredadera y por el suelo, apenas hojas, tiras de bayas y flores de esporas bajo los techos. |

### Parámetros

| Bandera | Efecto |
| --- | --- |
| `[estilo]` o `-s <estilo>` | Paleta de bloques. |
| `-l <bloques>` | Largo de cada tallo principal, el vigor (4–200, por defecto 28). Las ramas salen con parte de él. |
| `-b <0–1>` | Cuánto se ramifica (por defecto 0,4). |
| `-g <0–1>` | Cuánto cuelga de bordes y techos y lo largas que son las tiras (por defecto 0,5; 0 no cuelga nada). |
| `-n <tallos>` | Tallos por semilla (1–8, por defecto 2). |
| `-seed <n>` | Semilla del crecimiento. |

También valen los nombres largos `-largo`, `-ramas`, `-cuelga`, `-tallos`, `-semilla` y `-estilo`.

Ejemplos:

```text
/komaedit ivy
/komaedit ivy florido -l 40 -g 0.8
/komaedit hiedra selvatico -l 80 -n 4 -b 0.7 -seed 12
/komaedit ivy cueva -g 1
```

### Rendimiento y limitaciones

- El crecimiento es rápido: en una casa de pruebas, 28 bloques de largo dan unos 250 bloques en 1 ms y el máximo (200 de largo, 8 tallos, ramas y cuelgue a 1) unos 3 700 bloques en 75 ms.
- Lo que más tarda es capturar el entorno: alrededor de 16 000 bloques por tick. Con el largo por defecto son unos 2 s; con largos grandes la captura llega al tope de 72 bloques de radio en horizontal y 48 en vertical (unos 6 s).
- Las semillas de una misma hiedra deben caber en unos 120 bloques en horizontal y 80 en vertical; para cubrir un pueblo, por partes.
- Tope de 200 000 bloques por hiedra (o el límite de operación del servidor, si es menor); al llegar se para y el chat lo avisa.
- Solo reconoce como apoyo las caras enteras, igual que la enredadera de vanilla: no trepa por el frente de escaleras, losas ni vallas, aunque sí tiende alfombra encima.
- La enredadera de vanilla se sigue extendiendo sola con los ticks aleatorios del mundo, como cualquier enredadera puesta a mano.
- No hay holograma previo ni entrada en el Studio: se coloca directamente y se deshace con //undo.

## Modificadores

Los modificadores transforman de manera continua la figura seleccionada:

- Escala inicial y final.
- Espaciado inicial y final.
- Curvas de transición.
- Torsión progresiva.
- Fusión con una figura secundaria.
- Conformado a suelo, techo o superficie.
- Sockets Kitbash, también doblados a lo largo del recorrido (modos **Doblar** y **Estirar**).
- Reparación de microhuecos y componentes aislados.

En las figuras de espacio de tubo y en las clásicas con motor exacto, la escala, el espaciado y la torsión se aplican al evaluar cada bloque, sin deformación posterior ni puentes entre bloques separados. La figura de **Fusión** hereda el motor exacto de la receta cuando también lo tiene.

## Estructuras

La categoría Estructuras contiene generadores arquitectónicos orientados por puntos. Sus herramientas no aparecen duplicadas en la cuadrícula de Figuras:

- Pilares monumentales configurables.
- Pares de pilares monumentales conectados mediante arcos.
- Puentes arqueados entre apoyos.
- Torii japoneses gigantes que respetan diferencias verticales del terreno sin ondular sus vigas.

## Materiales y degradados

KomaEdit admite bloques individuales, patrones de WorldEdit y paletas de degradado. La distribución puede seguir el eje, exposición, cavidades, bordes, curvatura o propiedades específicas de la figura.

### Paletas con nombre

Donde KomaEdit espera una paleta (por ejemplo, `/komaedit gradient` o el material de una receta) se puede usar la notación de paletas de ezEdits, además de la lista `a;b;c` de siempre:

| Escritura | Resultado |
| --- | --- |
| `##Grayscale`, `##Magma`, `##GlowBlue`… | Paleta de serie de KomaEdit; no distingue mayúsculas. |
| `#mipaleta` | Paleta guardada del jugador (ver «Paletas guardadas»). |
| `stone,andesite` o `stone;andesite` | Concatena bloques o paletas. |
| `-##Grayscale` | Invierte el orden. |
| `##Grayscale(1:8)` | Tramo de la posición 1 a la 8, ambas incluidas. |
| `gold_block*3,diamond_block` | Repite el tramo anterior. |
| `-[##Grayscale,gold_block]` | Agrupa para aplicar un modificador a todo el grupo. |
| `oak_stairs[facing=east]` | Bloques con estado: los corchetes pegados al nombre son parte del bloque. |

Una paleta admite hasta 256 entradas.

### Ruido configurable

Las herramientas que usan ruido aceptan la notación de ezEdits `Tipo(clave:valor,…)`, con nombres largos o abreviados y sin distinguir mayúsculas. Por defecto el valor va de 0 a 1.

- **Tipos:** `Perlin` (`Pe`), `OpenSimplex2` (`Si`), `OpenSimplex2S` (`Sm`), `Value` (`Va`), `ValueCubic` (`VC`), `White` (`Wh`), `Cellular` (`Ce`) y `Gabor` (`Gb`).
- **Básicos:** `Seed` (`s`, `-1` = semilla del contexto), `Frequency` (`f`), `Inverted` (`i`), escalas `x`, `y` y `z`, y `ValueMapping` (`m`): `Default`, `None` (valor crudo de −1 a 1) u `Override` (`OR`) con `l`, `u`, `Mn` y `Mx`.
- **Celular:** `cJ` (jitter), `cD` (`e`, `sq`, `man`, `h`) y `cR`: `cell`, `distance`, `distance2`, `add`, `sub`, `mul`, `div`, `sq`, `inv`, `log`, `exp`, `2sq`, `2inv`, `2log`, `2exp` y `edge`.
- **Fractal:** `fT` (`N`, `F`, `R`, `P`), `fO` (octavas), `fL` (lacunaridad), `fG` (ganancia), `fW` (fuerza ponderada) y `fP` (ping-pong).
- **Distorsión de dominio:** `wT` (`G`, `S`, `R`), `wF`, `wO`, `wG`, `wA`, `wC` (`N`, `I`, `P`) y `wl`.
- **Gabor:** `gR` (radio), `gF` (frecuencia de vetas), `gFs` (dispersión), `gO` (orientación `1;0;0`), `gJ` (variación), `gN` (impulsos por celda, máx. 8) y `gM` (contraste).

Ejemplos: `Perlin(f:0.04)`, `Ce(cD:e,cR:sub,F:.1)`, `Si(fT:R,fO:4,wT:S,wA:30)`.

En lugar de escribir el ruido entero se puede usar el nombre de un ruido de serie (`ridged`, `smoothcells`, `voronoiedge`, `clouds`, `marble`, `veins`, `pebbles`, `dunes`, `static`) o el de un ruido guardado (ver «Ruidos guardados»).

Limitaciones: `Shard`, las distancias Minkowski y los retornos celulares `rounded` y `noiseLookup` aún no están disponibles y dan un error claro. El ruido se calcula con FastNoiseLite (licencia MIT), con posiciones en doble precisión.

## Retoques sobre la selección

### Objetivo y acceso

Ajustes rápidos de supervivencia sobre la selección de WorldEdit, como `//eztweak` de ezEdits:

```text
/komaedit tweak deinfest
/komaedit tweak persist [on|off]
/komaedit tweak waterlog [on|off]
/komaedit tweak wax [on|off]
```

Alias: `/komaedit retocar` y `desinfestar`, `persistir`, `anegar`, `encerar`.

### Comportamiento

- `deinfest` cambia cada bloque infestado por su versión normal, conservando las propiedades (por ejemplo, el eje de la pizarra profunda).
- `persist` activa o desactiva la persistencia de las hojas para que no se caigan.
- `waterlog` anega o seca todo lo que admita agua (escaleras, losas, vallas…).
- `wax` encera o desencera el cobre, incluidas escaleras, losas, puertas, trampillas, rejillas y bombillas. El mineral, el bloque de cobre en bruto y el pararrayos no tienen versión encerada y se dejan igual.
- La selección se lee repartiendo el trabajo entre ticks, se calcula en un worker y se escribe por lotes. Cada bloque solo se cambia si sigue igual que al leerlo, así no se pisa lo que alguien construya mientras tanto. Se deshace con `//undo`.

### Limitaciones

- Los carteles encerados guardan el encerado en sus datos, no en el estado del bloque; `wax` no los toca.
- La selección más su margen admite hasta 16 millones de bloques y 1 500 000 cambios.

## Deformar la selección

### Objetivo y acceso

Transforma lo que hay en la selección de WorldEdit en columnas, celdas, cuboides o figuras nuevas, como `//ezdeform` de ezEdits:

```text
/komaedit deform hexagonalize [tamaño 12] [hueco 0] [giroX 0] [giroZ 0] [ángulo 60] [-w perfil]
/komaedit deform noise <ruido> [fuerza 2] [-z zoom] [-s semilla] [-h] [-v] [-w perfil]
/komaedit deform rotate <ángulo> [-o] [-w perfil]
/komaedit deform voronoialize [tamaño 12] [hueco 0] [-s semilla] [-w perfil]
/komaedit deform voronoialize2 <cantidad> [hueco 0] [-s semilla] [-r uniformidad] [-n profundidad] [-w perfil]
/komaedit deform voxelize [escalas 3] [hueco 0] [distorsión 0] [-i eje] [-j eje] [-s semilla] [-h] [-v] [-w perfil]
/komaedit deform cylindrify [-afost] [-r radios] [-d profundidad] [-x mapa] [-i eje] [-j eje] [-w perfil]
/komaedit deform spherify [-aflost] [-r radios] [-d profundidad] [-x mapa] [-i eje] [-j eje] [-w perfil]
```

Alias: `/komaedit deformar` y los modos `hex`, `ruido`, `girar`, `voronoi`, `voronoi2`, `voxeles`, `cilindro` y `esfera`. Necesita el permiso de construir.

### Comportamiento

Cada modo es una función de punto: para cada punto del resultado dice de qué punto de la selección original sale su material, o que queda hueco. Así no aparecen agujeros al estirar o girar, y las losas y escaleras de `-w` miden la forma deformada dentro de cada bloque. Cuenta como «forma» todo lo que no es aire ni líquido. Donde el resultado deja aire sobre agua o lava, el líquido se conserva.

- **hexagonalize**: reparte el plano perpendicular a las columnas en celdas de una red y cada columna copia la línea que pasa por el centro de su celda. Quedan columnas de tapa plana, como el basalto. `ángulo` es el que forman los dos vectores de la red: 60° da hexágonos regulares y 90° cuadrados. `giroX` y `giroZ` tumban las columnas (en grados). Las celdas del borde leen el punto más cercano dentro de la selección para no desaparecer.
- **noise**: desplaza cada punto según tres campos del ruido indicado, uno por eje. `fuerza` es el desplazamiento máximo en bloques y `-z` agranda los rasgos (2 = el doble de grandes). `-h` solo mueve en horizontal y `-v` solo en vertical. El ruido usa la notación de ezEdits (ver [Ruido configurable](#ruido-configurable)) o el nombre de un ruido de serie o guardado (ver [Ruidos guardados](#ruidos-guardados)).
- **rotate**: gira la selección en el sentido de las agujas del reloj mirando a lo largo de tu mirada, alrededor del centro de la selección (o de tus ojos con `-o`). Si miras a menos de 10° de un eje del mundo, se usa ese eje exacto. Lo que sale de la selección se escribe fuera de ella. Donde la selección queda vacía se pone aire.
- **voronoialize**: celdas de Voronoi al azar, una por cada cubo de `tamaño` bloques. Cada celda queda entera si su sitio cae dentro de la forma y desaparece si no, así salen trozos convexos de roca. El `hueco` los separa.
- **voronoialize2**: reparte `cantidad` puntos por la superficie y los separa entre sí con `-r` pasadas de repulsión (0 = al azar, 15 = uniforme). Cada punto da el plano tangente de su zona y su sitio se hunde `-n` bloques hacia dentro. Dentro de cada celda la forma queda cortada por su plano, de modo que la superficie se vuelve facetas planas que siguen la forma original. Las facetas solo actúan cerca de la superficie: el interior y lo lejano no cambian.
- **voxelize**: reparte la selección en celdas de `escalas` bloques (`3` o `2,4,2` en x, y, z de la rejilla). Cada celda queda llena si más de la mitad estaba ocupada y vacía si no. Lo que se rellena toma el material mayoritario de la celda. `-i` y `-j` eligen qué eje del mundo es la «y» y la «x» de la rejilla (por defecto `y` y `-x`). `distorsión` (0–1) desplaza al azar los planos de corte sin dejar de formar cuboides. `-h` agrupa solo en horizontal y `-v` solo en vertical.
- **cylindrify** y **spherify**: proyectan la selección, como si fuera un mapa, sobre un cilindro o una esfera nuevos que se construyen aparte. La selección original no se toca. Por defecto el eje X del mapa da la vuelta, el Z recorre el cilindro a lo largo (o la latitud de la esfera) y el Y es la profundidad: lo más alto del mapa queda en la cara exterior. La esfera usa Mercator, o LAEA de un solo parche con `-l` (área igual; el centro del mapa queda arriba y los bordes se juntan abajo).

### Parámetros de cylindrify y spherify

- `-r`: radios. Cilindro: `r`, `r,mitad` o `r1,r2,mitad` (mitad = media longitud). Esfera: `r` o `r1,r2,r3` en los ejes `-j`, `-i` y el tercero. Sin `-r`, la vuelta mide lo mismo que el mapa (radio = ancho / 2π; con LAEA, la esfera tiene la misma área que el mapa).
- `-d`: profundidad de la proyección hacia el centro. Por defecto, el alto de la selección.
- `-x`: qué eje del mapa va a cada eje de la figura, en orden `EW,UD,NS`: `xyz` (por defecto), `zyx`, `-xyz` (el signo menos da la vuelta a ese eje).
- `-i`: eje de la figura en el mundo (`y` por defecto). `-j`: dirección donde empieza la vuelta (`x` por defecto). Valen `x`, `-x`, `y`, `-y`, `z` y `-z`.
- `-a`: no pone la caja de aire alrededor de la figura. Lo que la figura no ocupa se respeta, aunque el aire del mapa sí se coloca.
- `-f`: rellena el interior repitiendo la capa más honda del mapa hasta el centro.
- `-o`: construye alrededor de tu posición en vez de encima de la selección. Si encima no cabe en la altura del mundo, se construye al lado (al este).
- `-s`: descarta medio bloque del mapa en la dirección de la vuelta, para que un patrón repetido case en la costura.
- `-t`: centro de dos bloques (diámetro par). Necesita `-r`.

Antes de empezar se avisa del radio, la profundidad, la caja donde se construye y cuántos bloques fuera de la selección se van a sustituir. Esa caja se lee y se escribe con la misma escritura condicional que el resto: si alguien cambia un bloque mientras se calcula, ese bloque se respeta.

### Ejemplos

```text
/komaedit deform hexagonalize 8 1
/komaedit deform hexagonalize 16 0 45 0 60 -w SlabsAndStairs
/komaedit deform noise Perlin(F:0.06) 4 -z 2 -h
/komaedit deform rotate 30 -w Slabs
/komaedit deform voronoialize 16 2 -s 69420
/komaedit deform voronoialize2 50 -r 15 -n 5
/komaedit deform voxelize 4,2,4 0 0.5
/komaedit deform cylindrify -f
/komaedit deform spherify -l -r 24 -d 4 -o
```

### Rendimiento y limitaciones

- La lectura se reparte entre ticks y el cálculo va en un worker. La selección más su margen (y, en rotate, cylindrify y spherify, la caja de salida) admite hasta 16 millones de bloques y 1 500 000 cambios. Con `-o` lejos de la selección, la caja que hay que leer crece; acércate si el aviso dice que es demasiado grande.
- `voronoialize2` admite hasta 4096 celdas. En formas finas con pocas celdas, los planos de celdas vecinas pueden cruzarse y rellenar huecos pequeños. Baja `-n` o sube la cantidad.
- hexagonalize, noise, voronoialize, voronoialize2 y voxelize escriben solo dentro de la selección: lo que el ruido o las celdas empujen fuera se corta en su borde.
- Los modos se calculan con el bloque más cercano, sin mezclar materiales: los estados (orientación de troncos, escaleras…) se copian tal cual y no se giran con rotate.
- Respeta la máscara global de WorldEdit (`//gmask`) al escribir, como el resto de ediciones por lotes.

## Superficie

### Objetivo y acceso

Retoca la superficie de lo que hay en la selección, tallándola hacia dentro o haciéndola crecer hacia fuera hasta un radio, como `//ezsurface` de ezEdits:

```text
/komaedit surface fuzzify <radio> [radioSuave 0] [iteraciones 0] [-c] [-e] [-m máscara] [-t] [-w perfil]
/komaedit surface rockify <radio> [tamaño 10] [octavas 1] [radioSuave 1] [iteraciones 4] [-c] [-e] [-m máscara] [-t] [-w perfil]
/komaedit surface voronoify <radio> [celda 12] [radioSuave 0] [iteraciones 0] [-c] [-e] [-m máscara] [-t] [-w perfil]
/komaedit surface noisify <radio> <ruido> [escala 1] [radioSuave 1] [iteraciones 4] [-c] [-e] [-m máscara] [-t] [-w perfil]
```

Alias: `/komaedit superficie` y los modos `pelusa`, `roca`, `celdas` y `ruido`. Todos admiten además `-seed <n>` para repetir el resultado.

### Comportamiento

Se calcula la distancia exacta de cada bloque a la superficie (positiva fuera, negativa dentro) y la superficie nueva pasa por donde esa distancia iguala `radio × ruido`. El ruido va de −1 (tallar el radio entero) a +1 (crecer el radio entero). Nada a más del radio cambia. Después, si se pide, el campo se suaviza `iteraciones` veces con una caja de `radioSuave` bloques. Lo que crece toma el material del bloque ocupado más cercano: sobre la hierba crece hierba y en la roca, roca.

- **fuzzify**: ruido blanco, un valor al azar por bloque. Deja la superficie áspera.
- **rockify**: Perlin fractal con rasgos de unos `tamaño` bloques y `octavas` capas. Da roca ondulada.
- **voronoify**: celdas de unos `celda` bloques: una cúpula por celda con un pliegue en cada borde, como roca agrietada.
- **noisify**: cualquier ruido con la notación de ezEdits (ver [Ruido configurable](#ruido-configurable)) o por su nombre, de serie o guardado, muestreado a `escala` (2 = rasgos el doble de grandes).

Banderas:

- `-c`: solo talla (el ruido va de 0 a −1).
- `-e`: solo crece (de 0 a +1). No se puede usar a la vez que `-c`.
- `-m <máscara>`: solo se tallan los bloques de la lista y solo se crece desde ellos. Es una lista de ids y etiquetas separadas por comas; con `!` delante, la lista se invierte. Por ejemplo: `stone,andesite`, `#minecraft:base_stone_overworld` o `!grass_block`.
- `-t`: intenta conservar la topología. Los cambios se aplican de uno en uno, del más claro al más dudoso, y solo si el bloque es *simple* en ese momento: no se abren túneles, no se cierran huecos y nada se parte en dos ni se une. Lo que no se puede cambiar se queda como estaba.

### Ejemplos

```text
/komaedit surface fuzzify 1.5
/komaedit surface rockify 4 12 2 1 4
/komaedit surface voronoify 3 10 -c
/komaedit surface noisify 3 Ce(F:0.1,cR:sub) 1 1 4 -m stone,andesite -t
/komaedit surface rockify 3 -w SlabsAndStairs
```

### Rendimiento y limitaciones

- Se lee la selección más un margen de `radio + radioSuave × iteraciones + 2` bloques (como mucho 32), así el borde de la selección ve lo que hay al otro lado. Solo se escribe dentro de la selección; amplíala si quieres que la superficie crezca hacia fuera.
- `-t` es más lento: comprueba cada cambio con sus 26 vecinos, en dos pasadas.
- `-m` usa una lista de bloques y etiquetas, no la sintaxis completa de máscaras de WorldEdit. La máscara global (`//gmask`) se respeta al escribir.
- Radio de 0 a 32; suavizado de radio 0–8 y 0–16 iteraciones.

## Suavizado

### Objetivo y acceso

Suaviza, engorda o adelgaza lo que hay en la selección, como `//ezsmooth`, `//ezinflate`, `//ezdeflate` y `//ezsmoothblocks` de ezEdits:

```text
/komaedit smooth <radios> [iteraciones 1] [sesgo 0] [-w perfil]
/komaedit inflate <radios> [-w perfil]
/komaedit deflate <radios> [-w perfil]
/komaedit smoothblocks <perfil> [radio 2] [sesgo 0]
```

Alias: `suavizar`, `inflar`, `desinflar` y `suavizarpiezas`.

### Comportamiento

- **smooth**: la ocupación se convierte en densidad (1 lleno, 0 vacío), se desenfoca con una caja de los radios dados (dos pasadas, que se parecen a un desenfoque gaussiano) y se vuelve a cortar. Cada iteración repite las dos cosas. El `sesgo` (−1 a 1) mueve el corte: positivo engorda y negativo adelgaza.
- **inflate**: añade todo lo que queda a menos de los radios de un bloque ocupado, con distancia euclídea exacta: un bloque suelto con radio 2 se convierte en una esfera de 33 bloques.
- **deflate**: quita todo lo que queda a menos de los radios del aire. Un cubo de 5 con radio 1 se queda en uno de 3.
- **smoothblocks**: suaviza una vez con el radio y el sesgo dados y pone losas y escaleras en la superficie según el perfil.
- Los radios son un número o tres separados por comas para este-oeste, arriba-abajo y norte-sur: `inflate 3,0,3` solo crece en horizontal.
- Lo que aparece toma el material del bloque ocupado más cercano y solo se escribe dentro de la selección.

### Ejemplos

```text
/komaedit smooth 2 3 0
/komaedit smooth 3,1,3 2 0.3 -w SlabsAndStairs
/komaedit inflate 2
/komaedit deflate 1,2,1
/komaedit smoothblocks Slabs 2 0
/komaedit smoothblocks SlabsAndStairs(C:0.7) 3 -0.2
```

### Rendimiento y limitaciones

- smooth lee un margen de dos veces el radio por iteración (como mucho 32 bloques); inflate y deflate, el radio. Radios de hasta 16 en smooth y smoothblocks y de hasta 30 en inflate y deflate, con 1–32 iteraciones.
- Al suavizar un terreno, lo que se talla deja a la vista la capa de debajo (tierra o piedra). La hierba no se vuelve a poner encima.

## Losas y escaleras en las operaciones sobre la selección (`-w`)

Todos los modos de deform, surface y suavizado admiten `-w <perfil>`, como la bandera de smoothblocks de ezEdits. Cada bloque de la superficie del resultado se mide por octantes y se sustituye por la losa o escalera que mejor encaja, del mismo material (o del más parecido por color si ese material no tiene piezas), con la misma elección que las figuras de tubo.

- Perfiles: `Slabs` (losas) y `SlabsAndStairs` o `SAS` (losas y escaleras). También valen los nombres de KomaEdit `slabs`, `stairs`, `losas` y `escaleras`. `SlabsAndStairs2D` y `SSW` se tratan como `SlabsAndStairs`.
- Cobertura entre paréntesis: `Slabs(C:0.7)`. 0,5 es lo más fiel; hacia 1 se usan piezas en toda la superficie y hacia 0 casi ninguna.
- Limitaciones: no hay perfil `Layers` (capas de nieve), ni muros, ni materiales a elegir por pieza (`Slabs(Slab:acacia)`). Las piezas salen del bloque original.
## Formas sueltas

### Objetivo y acceso

Formas geométricas de una sola pieza colocadas donde estás, como `//ezshapes` de ezEdits. No dependen de trayectorias ni de la selección: se calculan con sus parámetros y aparecen con la base en el bloque de tus pies (el toro, centrado en él).

```text
/komaedit shape <forma> <parámetros> [banderas]
/komaedit forma <forma> <parámetros> [banderas]
```

| Forma | Uso |
| --- | --- |
| `cone` (`cono`) | `<patrón> <radios> <altura> [giro] [-dhos]` |
| `polydome` (`polidomo`) | `<patrón> <lados> <radio> [altura] [-v <patrón>] [-s]` |
| `polygon` (`poligono`) | `<patrón> <lados> <radio> [altura] [dirección] [-s]` |
| `square` (`cuadrado`) | `<patrón> <radio> [altura] [-fws]` |
| `tetrahedron` (`tetraedro`) | `<patrón> <radio> [giro] [-hos]` |
| `torus` (`toro`) | `<patrón> <radio_mayor> <radio_menor> [sección] [-hos]` |

- Permiso: `komaedit.build` o `komaedit.build.shape`.
- El patrón es cualquier patrón de WorldEdit (`stone`, `50%stone,50%andesite`, `#clipboard`…).
- Cada forma es una sola edición por lotes: **//undo** la quita entera y `/komaedit job status|cancel` funciona como en el resto de figuras.
- No duplican las brochas de esculpir (`/komaedit sculpt`): aquellas modelan terreno con cono, esfera, cubo o domo a golpe de clic; estas colocan una forma exacta con medidas.

### Comportamiento

- Cada forma se evalúa en el centro de cada bloque. Los radios cuentan medio bloque más, como en `//sphere`: un radio de 3 da 7 bloques de lado a lado.
- **Cono:** base elíptica; el primer radio va de norte a sur y el segundo de este a oeste (con uno solo, base redonda). La base sale con el radio entero y la cúspide es un solo bloque. `[giro]` lo gira en grados alrededor de la vertical.
- **Polidomo:** un domo facetado. En planta es un polígono regular de `<lados>` lados y en alzado una poligonal que sigue un cuarto de círculo, con un tramo por cada cuatro lados (como mínimo dos). Siempre es hueco, con un bloque de grosor y el suelo abierto. `[altura]` es una proporción del radio (1 = media esfera, 0,5 = domo rebajado; de 0,1 a 4).
- **Polígono:** prisma relleno de base regular y `[altura]` capas (1 por defecto). Con lados pares tiene una arista mirando a cada eje (un cuadrado sale alineado); con impares, un vértice hacia el sur. Crece en la `[dirección]` dada o, sin ella, hacia donde miras redondeado a una de las 26 direcciones (ejes y diagonales). Las placas inclinadas engordan lo justo para no dejar agujeros.
- **Cuadrado:** caja de 2·radio+1 de lado y `[altura]` capas, bloque a bloque, sin redondeos.
- **Tetraedro:** regular, apoyado en una cara. El radio es el de la circunferencia de la base; la altura sale sola (radio × √2).
- **Toro:** aro horizontal alrededor del bloque de tus pies, con sección `circle` (círculo, por defecto), `diamond` (rombo), `rounded_square` (cuadrado redondeado) o `square` (cuadrado).
- Las formas huecas dejan una cáscara de un bloque de grosor, sin huecos por las diagonales. El cono hueco queda abierto por la base, como un tejado.

### Parámetros

| Bandera | Formas | Efecto |
| --- | --- | --- |
| `-h` | cone, tetrahedron, torus | Hueca. |
| `-d` | cone | Con la punta hacia abajo. |
| `-o` | cone, tetrahedron | Suma al giro el de tu mirada (en horizontal). |
| `-o` | torus | El aro se pone perpendicular a tu mirada: mirando al frente sale de pie, como un portal; mirando abajo, tumbado. |
| `-s` | todas | Mueve tu selección de WorldEdit para que envuelva la forma. |
| `-v <patrón>` | polydome | Pinta las aristas con otro patrón (los nervios del domo). |
| `-f` | square | Solo las caras: paredes, suelo y techo. |
| `-w` | square | Solo las paredes. |

Las direcciones del polígono son `north`, `south`, `east`, `west`, `up` y `down` y sus combinaciones (`northeast`, `upwest`, `down-south`…), también con iniciales (`une`) o en español (`norte`, `arriba`…); `me` es tu mirada. Las banderas sin valor se pueden juntar (`-hos`).

Ejemplos:

```text
/komaedit shape cone stone 6,10 12 30
/komaedit shape cone spruce_planks 8 6 -ho
/komaedit shape polydome white_concrete 8 12 -v gold_block -s
/komaedit forma poligono quartz_block 6 10 3 up
/komaedit shape square stone_bricks 7 5 -w
/komaedit shape tetrahedron sandstone 12 45
/komaedit shape torus prismarine 14 3 rounded_square -o
```

### Rendimiento y limitaciones

- La forma se calcula en un worker: los ejemplos de arriba tardan entre 1 y 20 ms.
- Radios hasta 160 y alturas hasta 384. La caja que envuelve la forma puede tener hasta 48 millones de bloques y la forma hasta 1 500 000 (o el límite de operación del servidor, si es menor).
- Lo que quede fuera de la altura del mundo se recorta.
- No hay holograma previo: se coloca directamente y se deshace con //undo.

## Terreno con ruido

### Objetivo y acceso

Genera terreno dentro de la selección de WorldEdit a partir de un ruido y una paleta, como `//eznoisegen` de ezEdits.

```text
/komaedit noisegen heightmap <paleta> [ruido] [altura] [-z zoom] [-s semilla] [-o x,y,z] [-m máscara] [-cty]
/komaedit noisegen terrain <paleta> [ruido] [altura] [fuerza] [-z zoom] [-s semilla] [-l estirado] [-o x,y,z] [-m máscara] [-c]
/komaedit noisegen advanced <paleta> [ruido] [inferior] [superior] [-z zoom] [-s semilla] [-l estirado] [-o x,y,z] [-m máscara] [-chnty]
```

- Alias: `/komaedit ruidogen` y los modos `alturas`, `terreno` y `avanzado`.
- Permiso: `komaedit.build` o `komaedit.build.noisegen`.
- La paleta usa la notación de [paletas con nombre](#paletas-con-nombre) (`##Nombre`, `-`, `(a:b)`, `*n`, `[…]`) y el ruido la de [ruido configurable](#ruido-configurable), también los guardados con `/komaedit noise save`; sin ruido se usa `Perlin()`.

### Comportamiento

- Solo añade bloques: lo que haya por encima de la superficie generada se queda como estaba. Para regenerar una zona, vacíala antes con `//set air`.
- La paleta se reparte **por alturas**: la primera entrada abajo y la última arriba, en franjas iguales de la altura del terreno.
- **heightmap:** cada columna de la selección toma un valor de ruido 2D (de 0 a 1) y se llena desde el suelo de la selección hasta ese valor por la altura. La altura por defecto (0) es la de la selección; si se pone mayor, el terreno puede salir por encima de ella.
- **terrain:** ruido 3D sobre un degradado vertical que va de macizo abajo a vacío arriba. La **fuerza** dice cuánto pesa el ruido a cada altura: con poca fuerza sale un llano a media altura y con mucha aparecen voladizos, cuevas e islas sueltas.
- **advanced:** coloca donde el ruido 3D cae entre el umbral inferior y el superior (0 y 0,5 por defecto, de 0 a 1), y la paleta se reparte de un umbral a otro. Con `-h` es un mapa de alturas 2D en el que los umbrales hacen de rango: por debajo del inferior la columna queda vacía y por encima del superior, llena.
- Los umbrales admiten fórmulas de KomaEdit (las de la [figura Expresión](#figura-expresión)) con `x`, `y` y `z` normalizadas de −1 a 1 dentro de la selección y `t`, la altura relativa (0 abajo, 1 arriba). Por ejemplo `"0.3+0.2*t"` o `"r < 0.5 ? 0.1 : 0.4"`. Se eligió el compilador de KomaEdit y no el de WorldEdit porque es cerrado (sin bucles ni búferes globales) y unas diez veces más rápido, y aquí se evalúa una vez por bloque.
- Las coordenadas del ruido son las del mundo, así que dos selecciones vecinas empalman sin costura. Con `-c` se cuentan desde el centro de la selección y con `-n` (advanced) van de −1 a 1 dentro de ella (pide frecuencias altas, del orden de 1).
- Con `-m` la base de cada columna es el bloque más alto que cumple la máscara: el terreno crece encima de lo que ya hay y las columnas sin ese bloque se saltan. La máscara admite bloques con o sin propiedades (`grass_block`, `oak_log[axis=y]`), `#solid`, `#existing`, `#air`, `#liquid` y `!` para negar, separados por comas.
- Se respeta la forma de la selección (cilindros, polígonos, esferas…).
- Con la misma semilla, la misma selección y los mismos parámetros sale el mismo terreno. Si no se fija `-s` (o vale −1) se elige una al azar y el chat la dice para poder repetirla.

### Parámetros

| Parámetro | Modos | Efecto |
| --- | --- | --- |
| `<paleta>` | todos | Bloques de abajo arriba. Cada entrada tiene que ser un bloque (con estado si se quiere). |
| `[ruido]` | todos | Ruido; por defecto `Perlin()`. |
| `[altura]` | heightmap, terrain | Altura del terreno en bloques; 0 = la de la selección. |
| `[fuerza]` | terrain | Hasta tres valores separados por comas, de abajo arriba e interpolados: `0.5` = 50 % en toda la altura, `0.7,0` = del 70 % abajo al 0 % arriba, `0,1,0` = solo en medio. Por defecto `1,0.5,0`. |
| `[inferior] [superior]` | advanced | Umbrales del ruido (números de 0 a 1 o fórmulas entre comillas). |
| `-z <zoom>` | todos | Divide las coordenadas: con 2 el dibujo del ruido sale el doble de grande. |
| `-s <semilla>` | todos | Semilla del ruido (si el ruido no fija la suya con `s:`). |
| `-o x,y,z` | todos | Desplaza las coordenadas del ruido. |
| `-l <estirado>` | terrain, advanced | Estira el ruido 3D en vertical: con 1 sale el doble de alto. |
| `-m <máscara>` | todos | Genera encima de los bloques que la cumplen. |
| `-c` | todos | Coordenadas desde el centro de la selección. |
| `-h` | advanced | Modo mapa de alturas (ruido 2D). |
| `-n` | advanced | Coordenadas normalizadas de −1 a 1 centradas en la selección. |
| `-t` | heightmap, advanced con `-h` | Modo suave: la nieve (`snow`) de la paleta se pone en capas según la altura exacta de la columna y solo en la cima; el agua y la lava forman lagos planos hasta el techo de su franja, y bajo tierra se cambian por la entrada sólida más cercana. |
| `-y` | heightmap, advanced con `-h` | Usa ruido 3D cortado a la altura del suelo de la selección; así cuenta también la `y` de `-o`. |

Ejemplos:

```text
/komaedit noisegen heightmap water,sand,grass_block,stone,snow Perlin(F:0.03,fT:F,fO:4) -t
/komaedit noisegen heightmap ##Grayscale Si(F:0.02,fT:R,fO:5) 24 -z 2 -s 7
/komaedit noisegen terrain stone,dirt,grass_block Perlin(F:0.05,fT:F,fO:3) 0 1,0.5,0
/komaedit ruidogen terreno andesite,stone,moss_block Si(F:0.04) 0 0,1,0 -l 2
/komaedit noisegen advanced -##Magma Perlin(F:0.06) 0.55 1
/komaedit noisegen advanced sand,grass_block,stone,snow Ce(F:0.05) 0 "0.3+0.2*t" -h -t
/komaedit noisegen heightmap dirt,grass_block Perlin(F:0.05) 6 -m grass_block
```

### Rendimiento y limitaciones

- Sin máscara no hace falta leer el mundo: se calcula directamente en un worker. Una selección de 80×32×80 tarda unos 10 ms en heightmap y unos 30 ms en terrain y advanced; el tiempo crece con columnas × altura.
- Con `-m` primero se lee la selección repartida entre ticks (unos 4 ms por tick), y cada bloque solo se cambia si sigue igual que al leerlo. En ese modo el terreno no pasa del techo de la selección.
- Límites: hasta 64 millones de bloques recorridos (columnas × altura), 16 millones leídos con `-m` y 1 500 000 bloques colocados (o el límite de operación del servidor).
- La paleta de los generadores solo admite bloques, no patrones (`#clipboard`, porcentajes…), porque el worker escribe estados fijos. Sí admite las paletas guardadas con `/komaedit palette save` (`#nombre`).
- La máscara es un subconjunto de las de WorldEdit (ver arriba); las expresiones, los porcentajes o las máscaras de región dan un error claro.
- No hay holograma previo; se deshace con //undo.

## Campos y líneas de flujo

### Objetivo y acceso

El arte generativo de los «flow fields», como `//ezflowfield` y `//ezflowline` de ezEdits: un ruido da una dirección en cada punto y muchas partículas lo recorren dejando rastro. Sirve para vetas, ríos de bloques, texturas de suelo, raíces o lianas.

```text
/komaedit flowfield <paleta> <líneas|n%> [iteraciones] [velocidad] [escala_paleta] [ruido] [banderas]
/komaedit flujo …
/komaedit flowline <patrón> <longitud> [gravedad] [ruido] [-i inercia] [-c puntos] [-l líneas] [-r azar] [-s]
/komaedit lineaflujo …
```

- Permiso: `komaedit.build` o `komaedit.build.flowfield` / `komaedit.build.flowline`.
- `flowfield` trabaja dentro de la selección de WorldEdit; `flowline` sale de tus ojos hacia donde miras.

### Comportamiento

- **Campo de flujo:** las líneas salen de puntos al azar de la selección (o de los bloques que cumplen `-m`). En cada paso avanzan `velocidad` bloques en la dirección del campo, mezclada con la anterior según la inercia y con la gravedad sumada. Una línea se corta al salir de la selección (más el borde `-b`) o al agotar sus iteraciones.
- Cada paso suma su fuerza a los bloques que pisa (una vez por línea) y al final la suma de cada bloque, multiplicada por la escala de paleta, elige la entrada: con escala 1, por donde pasa una línea va la primera entrada, por donde se cruzan dos la segunda, y así. Con escala 0,5 hacen falta dos pasadas por entrada. Las fuerzas negativas de `-p` restan, y donde la suma no es positiva no se coloca nada.
- Por defecto el flujo es **plano**: las partículas se mueven en horizontal (la gravedad puede moverlas en vertical) y el ruido se muestrea en 3D, así que cada altura tiene su propio dibujo. La dirección da dos vueltas completas en el rango del ruido.
- `-t` hace el flujo **3D**: la dirección sale de tres muestras del ruido desfasadas.
- `-h` es el modo **mapa de alturas**: las partículas siguen la superficie del terreno de la selección (el bloque sólido o líquido más alto de cada columna) y pintan ese bloque (`surface`, por defecto) o el de encima (`above`).
- `-c` usa el **rotacional** del campo: en plano, (∂ψ/∂z, −∂ψ/∂x) con ψ el ruido; en 3D, el rotacional de tres ruidos. No tiene fuentes ni sumideros, así que salen remolinos en vez de líneas que se juntan.
- `-x`, `-y` y `-z` transforman las coordenadas antes de muestrear el ruido con una fórmula de KomaEdit en `x`, `y` y `z`; si empieza por un operador se aplica al propio eje (`-x *10` es `x*10`, `-z "+x/2"` es `z+x/2`).
- **Línea de flujo:** la dirección en cada paso es tu mirada torcida por un ruido 3D, mezclada con la anterior según la inercia y con la gravedad empujando en vertical (−1 por defecto: tiende a caer). Cada línea mide `longitud` pasos de un bloque. Con `-l` salen varias, cada una con el ruido desplazado y el arranque torcido según `-r`.

### Parámetros

| Parámetro | Comando | Efecto |
| --- | --- | --- |
| `<paleta>` | flowfield | Bloques por número de pasadas, de menos a más. |
| `<líneas>` | flowfield | Número de líneas, o `n%`: n líneas por cada 100 bloques de la selección (columnas con `-h`); `100%` es una por bloque. |
| `[iteraciones]` | flowfield | Pasos por línea (32). |
| `[velocidad]` | flowfield | Bloques por paso (1). |
| `[escala_paleta]` | flowfield | Multiplica la suma de cada bloque (1). |
| `[ruido]` | ambos | Ruido; por defecto `Perlin()`. Frecuencias de 0,01 a 0,05 dan curvas amplias. |
| `-m <máscara>` | flowfield | Solo salen líneas de los bloques que la cumplen (misma sintaxis que en noisegen). Va bien con `-j`. |
| `-h [surface\|above]` | flowfield | Mapa de alturas. |
| `-i <0–1>` | ambos | Inercia: cuánto pesa la dirección anterior (0). |
| `-g x,y,z` | flowfield | Gravedad sumada a cada paso, en bloques. |
| `-j x,y,z` | flowfield | Temblor: desplaza al azar los puntos de salida hasta esos bloques. |
| `-b <bloques>` | flowfield | Las líneas pueden salir de la selección hasta esa distancia y volver; fuera no se coloca nada. |
| `-x`, `-y`, `-z <fórmula>` | flowfield | Transforman las coordenadas del ruido. |
| `-p inicio:fin` | flowfield | Fuerza de la línea al principio y al final (1:1); con negativos resta. |
| `-s <semilla>` | flowfield | Semilla de las salidas y del ruido. |
| `-c` | flowfield | Rotacional del campo. |
| `-f` | flowfield | Rellena el resto de la selección (o de la superficie con `-h`) con la primera entrada. |
| `-t` | flowfield | Flujo 3D; no se combina con `-h`. |
| `<patrón>` | flowline | Cualquier patrón de WorldEdit. |
| `<longitud>` | flowline | Pasos de la línea (hasta 2 048). |
| `[gravedad]` | flowline | Empuje vertical por paso: negativo cae, positivo sube (−1). |
| `-c <puntos>` | flowline | Crea una selección convexa de WorldEdit con esos puntos repartidos a lo largo de las líneas. |
| `-l <líneas>` | flowline | Número de líneas (1, hasta 32). |
| `-r <azar>` | flowline | Cuánto difieren las líneas: desplaza el ruido de cada una hasta r bloques y tuerce su arranque hasta r/2 grados (50). |
| `-s` | flowline | Pega las líneas a las superficies: cada paso sube o baja hasta 8 bloques hasta el suelo y pinta su bloque de arriba. |

Ejemplos:

```text
/komaedit flowfield ##GlowBlue 600 64 1 0.5 Perlin(F:0.02)
/komaedit flowfield stone,andesite,diorite,calcite 400 80 1 0.34 Perlin(F:0.015) -c
/komaedit flujo coarse_dirt,rooted_dirt 50% 24 1 1 Si(F:0.03) -h -f
/komaedit flowfield moss_block,moss_carpet 300 48 1 1 Perlin(F:0.03) -m grass_block -j 2,0,2 -h above
/komaedit flowfield ##Magma 120 96 1 1 Perlin(F:0.03) -t -c -s 5
/komaedit flowline oak_log 80 -1 Perlin(F:0.05) -i 0.6
/komaedit flowline moss_block 120 -2 Si(F:0.04) -l 5 -r 30 -s
/komaedit flowline stone 60 0 -c 16
```

### Rendimiento y limitaciones

- El campo de flujo se calcula en un worker: 600 líneas de 64 pasos tardan unos 45 ms. Topes: 200 000 líneas, 4 096 iteraciones y 60 millones de pasos en total.
- Sin `-h` ni `-m` no se lee el mundo. Con ellos primero se lee la selección repartida entre ticks y cada bloque solo se cambia si sigue igual que al leerlo.
- La línea de flujo son pocos miles de bloques como mucho (longitud × líneas ≤ 16 384), así que se calcula en el hilo del servidor y puede leer el mundo para `-s` sin foto previa.
- Como en noisegen, la paleta del campo solo admite bloques (también las guardadas, `#nombre`).
- La selección convexa de `-c` necesita al menos cuatro puntos que no estén en un mismo plano para tener volumen.
- No hay holograma previo; los dos se deshacen con //undo.

## Máscaras y patrones en WorldEdit

### Objetivo y acceso

KomaEdit registra en WorldEdit las máscaras y patrones de ezEdits, así que valen en **cualquier** orden de WorldEdit que acepte una máscara o un patrón: `//set`, `//replace`, `//gmask`, `//mask`, pinceles, `//overlay`… No hay que activar nada: se registran al arrancar el servidor. Se escriben `#nombre[argumento][argumento]…`; los argumentos entre `<…>` son opcionales y se pueden dejar vacíos (`[]`) para usar el valor por defecto.

```text
//replace #palette[##Grayscale] #palette[##Ice]
//set #eznoisepattern[##Magma][ridged][2]
//gmask #near[water][3]
//replace #lightsource[10][15] #aim
//set #vectorgradientpattern[##FadeBlue][up][20][marble]
```

### Máscaras

| Máscara | Pasa en… |
| --- | --- |
| `#aim[<hitboxes>]` | Bloques iguales (mismo estado) al que miras. Con `true` respeta la forma de los bloques y puedes apuntar detrás de una losa; con `false` (por defecto) todo bloque cuenta como cubo. Si no miras a nada a menos de 256 bloques, no pasa ninguno. |
| `#attached[<direcciones>]` | Bloques pegados a un vecino que no es aire. Por defecto mira los seis lados; `#attached[up,down,left,north]` solo esos. «Pegado» es que el bloque llegue a esa cara: una losa inferior no pasa `#attached[up]`; un farol colgado, una antorcha de pared, un botón o una enredadera sí, por su estado. |
| `#blocklight[nivel]` / `#blocklight[mín][máx]` | Luz de bloques (sin el cielo) en ese nivel o rango. |
| `#truelight[nivel]` / `#truelight[mín][máx]` | Luz total, incluida la del cielo sin el ciclo de día. |
| `#lightsource` / `#lightsource[nivel]` / `#lightsource[mín][máx]` | Bloques que emiten luz (o la emiten en ese rango). |
| `#clipblocks[<aire>][<estricto>]` (`#cb`) | Bloques que hay en tu portapapeles. `aire` (false) incluye el aire; `estricto` (false) exige el mismo estado. |
| `#schemblocks[schematic][<aire>][<estricto>]` (`#sb`) | Lo mismo con un schematic de la carpeta `schematics` de WorldEdit (`.schem` o `.schematic`). |
| `#current` (`#c`) | Tu máscara global actual (`//gmask`). `//gmask !#current` la invierte. Sin máscara global, pasa todo. |
| `#eznoisemask[ruido][<escala>][<umbral>][<semilla>]` (`#eznm`) | Donde el ruido (de 0 a 1) supera el umbral (0,5). La escala estira el ruido (2 = el doble de grande). |
| `#fullblock` | Bloques que ocupan el cubo entero (piedra, cristal, nieve de 8 capas; no losas ni nieve de 1 a 7). |
| `#infested` | Bloques infestados de lepismas. |
| `#near[máscara][distancia]` / `#near[máscara][mín][máx]` | Bloques a esa distancia esférica de alguno de la máscara, sin contar los de la propia máscara. Con mínimo, excluye los que están más cerca. Distancia hasta 16. |
| `#palette[paleta][<estricto>]` | Bloques de la paleta. Estricto (por defecto) mira además las propiedades escritas: `oak_stairs[facing=east]` no encaja con una escalera al oeste; `oak_stairs` sin corchetes encaja con todas. |
| `#fuzzypalette[paleta]` (`#fpalette`) | Igual que `#palette[paleta][false]`: solo el tipo de bloque. |
| `#radialgradientmask[distancia][<modo>][<inicio>][<ruido>][<escala>][<semilla>]` (`#rgradientm`) | Degradado desde tu posición: cerca pasa casi todo, a `distancia` bloques ya nada. `modo`: `sphere`, `cube`, `diamond` o `cylinder`. `inicio`: bloques que pasan siempre antes de empezar el degradado. Con ruido, el borde sigue el ruido en vez de salir moteado. |
| `#vectorgradientmask[vector][distancia][<ruido>][<escala>][<semilla>]` (`#vgradientm`) | Degradado a lo largo de un vector desde tu posición: `up`, `north`, `me`, `left`… o `x,y,z`. Lo que queda detrás pasa siempre. |

### Patrones

| Patrón | Pone… |
| --- | --- |
| `#aim[<hitboxes>]` | El bloque al que miras, con sus datos. |
| `#palette[paleta]` | Un bloque al azar de la paleta: `//set #palette[##Ice]`. |
| `#eznoisepattern[paleta][ruido][<escala>][<semilla>]` (`#eznp`) | El bloque de la paleta que toca según el valor del ruido (0 = primero, 1 = último). |
| `#ridged[paleta][<escala>][<semilla>]`, `#smoothcells[…]`, `#voronoiedge[…]` | Atajos de `#eznoisepattern` con esos ruidos de serie. |
| `#radialgradientpattern[paleta][distancia][<modo>][<inicio>][<ruido>][<escala>][<semilla>]` (`#rgradientp`) | La paleta según la distancia a ti, con medio bloque de mezcla a cada lado entre tramos (o el ruido si se da). |
| `#vectorgradientpattern[paleta][vector][distancia][<ruido>][<escala>][<semilla>]` (`#vgradientp`, `#vectorgradient`) | La paleta a lo largo de un vector. |
| `#selection[selección][<desplazamiento>]` (`#sel`) | Los bloques que hay ahora en una selección guardada, repetidos como un mosaico. `desplazamiento` = `x,y,z`. |

Las paletas aceptan toda la notación de «Paletas con nombre», incluidas tus paletas guardadas (`#mipaleta`). Los ruidos aceptan la notación completa, los nombres de serie y tus ruidos guardados. La semilla `-1` o vacía es aleatoria en cada orden; con una semilla fija sale siempre lo mismo.

En la wiki de ezEdits el apartado del patrón `#vectorgradient` describe en realidad el degradado radial; aquí `#vectorgradient` es el vectorial y el radial es `#radialgradientpattern`.

### Rendimiento y limitaciones

- Todo lo que depende de ti (la mirada de `#aim`, tu posición en los degradados, el portapapeles, la máscara global, la selección de `#selection`) se fija al escribir la orden.
- Las máscaras leen los bloques a través de WorldEdit (ven los cambios de la propia edición) y guardan la respuesta por estado de bloque. La luz se lee del motor de luz de Minecraft, que no carga chunks: en zonas sin cargar vale 0. Se evalúan donde WorldEdit las evalúa, en el hilo del servidor.
- `#near` revisa una esfera alrededor de cada bloque y se para en el primero que encuentra; guarda lo que ya sabe de la máscara interior. Con distancias grandes en zonas sin coincidencias es la más lenta.
- `#clipblocks` y `#schemblocks` revisan hasta 4 millones de bloques; `#selection` copia hasta 262 144.
- Solo las puede usar un jugador cuando dependen de él (`#aim`, degradados, `#selection`).

## Paletas guardadas

### Objetivo y acceso

Como `//ezpalette` de ezEdits. Las paletas guardadas son de cada jugador y de cada mundo, y se usan con `#nombre` en cualquier sitio que acepte una paleta (degradados, recetas, máscaras y patrones de WorldEdit…).

```text
/komaedit palette list [all|default|mine]
/komaedit palette save <nombre> <paleta> [-f]
/komaedit palette delete <nombre>
/komaedit palette fetch <world|selection|hotbar> <nombre> [largo] [-d dirección] [-f]
/komaedit palette place <paleta> [dirección]
/komaedit palette swap <origen> <destino> [-a] [-s] [-f] [-b]
/komaedit palette print <paleta> [-v]
/komaedit palette encode <paleta>
/komaedit palette decode <código>
```

Alias: `/komaedit paleta`.

### Comportamiento

- `save` guarda la paleta ya desplegada: si guardas `#hielo,stone` y luego borras `#hielo`, la nueva sigue igual. `-f` sustituye una que ya exista.
- `fetch world` lee bloques en fila desde el bloque al que miras (o el que pisas) hacia donde miras o hacia `-d`; `fetch selection` lee una selección de 1×1×N de pos1 a pos2; `fetch hotbar` toma los bloques de la barra rápida. Con largo 0 (por defecto) lee hasta encontrar aire. Solo guarda las propiedades que no son las de por defecto.
- `place` pone la paleta en fila delante de ti; se deshace con `//undo`.
- `swap` cambia, dentro de la selección, el bloque n del origen por el n del destino. `-a` no toca el aire del origen, `-s` estira el destino para cubrir todo el origen (sin él, lo que sobra del origen se queda igual), `-f` encaja el origen solo por tipo y `-b` copia las propiedades del bloque original sobre el nuevo cuando las admite. Va por el motor de operaciones sobre la selección: lectura repartida, cálculo en un worker y un solo `//undo`.
- `print` muestra la paleta (`-v` con id completo y estados); al pulsarla se copia.
- `encode` da un código `kp1:…` que se copia al pulsarlo; `decode` lo convierte otra vez en bloques. El formato es propio de KomaEdit (no es el de ezEdits) y admite bloques de mods.

### Paletas de serie

Además de las de degradados (`##Grayscale`, `##GlowBlue`, `##FadeRed`…), KomaEdit trae con sus propias listas `##Beige`, `##Brown`, `##DustyBrown`, `##EnchantedDark`, `##EnchantedWarm`, `##EnchantedBright`, `##LegacyWool` (las 16 lanas en el orden de los tintes), `##Light`, `##Moss`, `##Ice`, `##Rocks`, `##Leaf`, `##LeavesAll` (hojas persistentes), `##Sand` y `##ShiningGold`.

### Rendimiento y limitaciones

- Hasta 512 paletas por jugador y mundo, de hasta 256 entradas; se guardan en `<mundo>/komaedit/players/<uuid>/palettes/palettes.json` con escritura atómica.
- `swap` y `fetch` solo admiten bloques (no porcentajes ni patrones) en las paletas; `save`, `place` y `print` admiten cualquier patrón de WorldEdit.
- `place` y `swap` necesitan `komaedit.build`.

## Ruidos guardados

### Objetivo y acceso

Como `//eznoise` de ezEdits. Un ruido guardado se usa por su nombre (o `#nombre`) en cualquier sitio que acepte un ruido.

```text
/komaedit noise list [all|default|mine]
/komaedit noise save <nombre> <ruido> [-f]
/komaedit noise delete <nombre>
/komaedit noise print <ruido>
```

Alias: `/komaedit ruido`.

### Comportamiento

- `save` comprueba el ruido y lo guarda con su definición completa. No admite nombres de tipos ni de ruidos de serie.
- `print` muestra el tipo y cada parámetro, y la definición para copiarla al pulsarla. Vale también con un ruido de serie: `/komaedit noise print marble`.
- Ruidos de serie: `ridged`, `smoothcells`, `voronoiedge`, `clouds`, `marble`, `veins`, `pebbles`, `dunes` y `static`.

### Rendimiento y limitaciones

Hasta 512 ruidos por jugador y mundo, en `<mundo>/komaedit/players/<uuid>/noises/noises.json`.

## Selecciones

### Objetivo y acceso

Las órdenes de selección de ezEdits, sobre la selección de WorldEdit, con el permiso `komaedit.select`:

| KomaEdit | ezEdits | Qué hace |
| --- | --- | --- |
| `/komaedit sel list [-g]` | `//ezsel list` | Lista tus selecciones (pulsa una para cargarla); `-g` las agrupa por tipo. |
| `/komaedit sel save <nombre> [-f]` | `//ezsel save` | Guarda la selección actual. |
| `/komaedit sel load <nombre> [-h]` | `//selload`, `//ezsel load` | La recupera; con `-h` la trae a tu posición. |
| `/komaedit sel delete <nombre>` | `//ezsel delete` | La borra. |
| `/komaedit sel next [dirección] [hueco]` | `//next` | Desplaza la selección su propio tamaño hacia donde miras o en la dirección dada, más el hueco. |
| `/komaedit sel here [pos1\|pos2\|center]` | `//selhere`, `//seltome` | Lleva ese punto de la selección a tus pies; el resto la acompaña. Alias `tome`. |
| `/komaedit sel invert` | `//ezselinvert` | Invierte el orden de los puntos (en un cubo cambia pos1 por pos2). |
| `/komaedit sel delpos2` | `//delpos2` | Quita el último punto de una selección convex o poly. |
| `/komaedit sel encapsulate <máscara>` | `//encapsulate` | Ajusta la selección al cubo más pequeño que contiene los bloques de la máscara. Alias `enc`. |
| `/komaedit sel encapsulatenear <radio> <máscara>` | `//encapsulatenear` | Lo mismo, buscando en un cubo de ese radio alrededor de ti. Alias `encnear`. |

Alias: `/komaedit seleccion`. La catenaria (`//selcatenary` de ezEdits) es `/komaedit path catenary <comba> [dirección] [puntos]`, que la genera como trayectoria entre el primer y el último punto. También están en `/komaedit help palette`, `help noise` y `help sel`.

### Comportamiento

- Se guardan cuboid, extend, poly, convex, ellipsoid, sphere y cylinder, con el mundo donde se marcaron. Al cargar una de otro mundo se avisa y se carga igual; `#selection` en cambio exige el mismo mundo.
- Las direcciones aceptan `up`, `down`, `north`, `south`, `east`, `west`, `me`, `back`, `left` y `right` (también `arriba`, `norte`, `izquierda`…).
- `here` con una selección redonda usa siempre el centro.
- `encapsulate` admite cualquier máscara de WorldEdit, también las de KomaEdit: `/komaedit sel enc #palette[##Ice]`.

### Rendimiento y limitaciones

- `encapsulate` revisa hasta 1 000 000 de bloques en el hilo del servidor; `encapsulatenear` admite un radio de hasta 48.
- Las selecciones se guardan en `<mundo>/komaedit/players/<uuid>/selections/selections.json`.

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

- Ruta: `<mundo>/komaedit/players/<uuid>/` con namespaces `recipes`, `instances`, `jobs` y `paths` (recorridos guardados).
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
- Trayectorias y construcción: `komaedit.path`, `komaedit.build`, `komaedit.build.<figura>` (`komaedit.build.weather` para Envejecer).
- Contenido: `komaedit.recipe`, `komaedit.instance`.
- Edición: `komaedit.select`, `komaedit.paint`, `komaedit.clipboard`, `komaedit.history`.
- Administración de proyecto: `komaedit.project.backup`.
- Excepción de volumen: `komaedit.limit.unrestricted`.

### Configuración

`config/komaedit/config.json` se crea automáticamente con:

- `maxBlocksPerOperation`: `1500000` de forma predeterminada; rango válido `10000–10000000`.
- `maxConcurrentJobs`: `4` de forma predeterminada; rango válido `1–64`.
- `backupRetention`: `5` de forma predeterminada; rango válido `1–50`.
- `confirmBuildAboveBlocks`: `250000` de forma predeterminada; rango válido `0–10000000`. Por encima, un Build pide confirmación; `0` lo desactiva. Si falta en un `config.json` anterior se usa el valor por defecto.
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
