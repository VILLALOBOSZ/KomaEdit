# Documentación del MOD

Este documento es el catálogo funcional oficial de KomaEdit. Cada función nueva o cambio visible para el jugador debe añadirse aquí en el mismo Pull Request que introduce el código.

## Procedural Studio

Procedural Studio se abre con `K` y permite construir mientras el mundo continúa visible y activo. La interfaz ofrece categorías para trayectorias, figuras, estructuras, modificadores, materiales, recetas e instancias.

- Los paneles son desplazables y se adaptan a resoluciones pequeñas.
- El catálogo de materiales utiliza todos los bloques registrados por Minecraft y los mods cargados.
- La cámara puede desplazarse mientras el Studio permanece abierto.
- `Ctrl+Z`, `Ctrl+Y` y `Ctrl+Shift+Z` utilizan el historial de WorldEdit.

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
- Escamas alternativa 2
- Spiked Scales
- Beads
- Coil
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
- Braids
- Mandalas tridimensionales
- Nubes orgánicas
- Telarañas adaptativas

Cada figura conserva su gramática propia y puede combinar radio, espaciado, grosor, giro y materiales sin reutilizar estado residual de una figura anterior.

## Escamas alternativa 2

### Objetivo y acceso

**Escamas alternativa 2** crea una piel radial inspirada en tejidos tensados y placas colocadas a mano. Es una figura nueva e independiente: no reemplaza ni altera `scales`. Se selecciona desde **Figuras** en Procedural Studio o mediante:

```text
/komaedit scales2 <curvatura> <separación 2–8> [patrón]
```

El comando general también está disponible:

```text
/komaedit build scales_v2 <patrón> <curvatura> <separación 2–8> <grosor 1–4> <relieve -180–180>
```

### Parámetros y comportamiento

- Requiere al menos dos puntos distintos; con tres o más, los puntos se ordenan alrededor del centro y funcionan como anclajes exteriores.
- **Curvatura:** de `3` a `64`; aumenta la concavidad de cada borde frontal sin desplazar los anclajes.
- **Separación capas:** de `2` a `8`; `2` produce capas densas y `8` placas más largas y separadas.
- **Grosor:** de `1` a `4`; controla la profundidad sólida y el borde frontal.
- **Relieve 3D:** de `-180` a `180`; su magnitud eleva las coronas y su signo cambia el lado del plano en el que sobresalen.
- Las placas cubren completamente cada franja, se solapan hacia la capa anterior y elevan su centro y borde frontal para conservar una silueta legible.
- Con exactamente dos puntos, la alternativa utiliza internamente una geometría aislada compatible con la trayectoria clásica.
- El holograma y el Build ejecutan el mismo generador de vóxeles.

### Materiales

- Acepta cualquier bloque o patrón WorldEdit.
- El comando rápido usa `deepslate_tiles` y un relieve inicial de `24`.
- Puede combinarse con cualquier paleta profesional y con **Pintura ambiental** para separar cavidades, cuerpos y bordes expuestos.

### Rendimiento y limitaciones

- El número máximo de capas es `64`.
- La resolución se adapta al tamaño real de cada sector y escribe directamente en el conjunto voxelizado, evitando duplicados.
- La generación se calcula fuera del hilo principal y respeta el límite general de `1 500 000` bloques.
- Anclajes muy alejados o polígonos extremadamente irregulares aumentan el área que debe cubrirse; reduzca la densidad o el tamaño si se alcanza el límite.
- Los puntos no coplanares producen una piel tensada tridimensional.

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

## Estructuras

La categoría Estructuras contiene generadores arquitectónicos orientados por puntos. Sus herramientas no aparecen duplicadas en la cuadrícula de Figuras:

- Pilares monumentales configurables.
- Pares de pilares monumentales conectados mediante arcos.
- Puentes arqueados entre apoyos.
- Torii japoneses gigantes que respetan diferencias verticales del terreno sin ondular sus vigas.

## Materiales y degradados

KomaEdit admite bloques individuales, patrones de WorldEdit y paletas de degradado. La distribución puede seguir el eje, exposición, cavidades, bordes, curvatura o propiedades específicas de la figura.

### Pintura ambiental

- **Objetivo:** distribuir la paleta como una pintura integrada al volumen, oscureciendo cavidades y concentrando contraste en superficies abiertas, crestas y cambios de dirección.
- **Acceso:** abra **Material → Degradado → Distribución geométrica → Pintura ambiental**.
- **Parámetros:** la paleta define los materiales; **Mezcla orgánica** controla el tramado entre escalones; **Dirección** aporta únicamente una variación global ligera; **Invertir dirección** invierte el resultado completo.
- **Comportamiento:** combina exposición local, curvatura, bordes, ruido coherente y una contribución direccional mínima. Funciona con todas las figuras y estructuras.
- **Rendimiento:** analiza únicamente los seis vecinos dentro del plan ya generado; no consulta chunks ni estados del mundo por bloque.
- **Limitaciones:** el resultado depende del orden de la paleta. Coloque primero los bloques destinados a cavidades y al final los materiales de luz o acento.

## Recetas, instancias y paquetes

- Las recetas guardan parámetros reutilizables durante la sesión.
- Las instancias permiten actualizar o retirar una construcción sin borrar bloques modificados manualmente.
- Los paquetes exportables conservan configuraciones compartibles en `config/komaedit/packages/`.
- Las recetas temporales se limpian al salir del mundo para evitar contaminación entre sesiones.

## Rendimiento

- La geometría se calcula de forma asíncrona.
- La colocación se agrupa por chunks y lotes.
- Los hologramas utilizan secciones persistentes, descarte por cámara y caras expuestas.
- Los generadores poseen límites de seguridad y cancelación cooperativa.
- El árbol épico utiliza almacenamiento voxel compacto y validación lineal de materiales.

## Versiones descargables

Todas las compilaciones de integración quedan visibles en GitHub Releases además de los artefactos temporales de GitHub Actions.

### Canal staging

- La versión interna del mod incluye la versión de desarrollo y el SHA corto: `komaedit-4.7.0-alpha.1.c0d75e5.jar`.
- La prerelease se muestra con el formato `komaedit-staging-4.7.0-c0d75e5`.
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
