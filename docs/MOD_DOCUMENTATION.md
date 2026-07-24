# Documentación del MOD

Este documento es el catálogo funcional oficial de KomaEdit. Cada función nueva o cambio visible para el jugador debe añadirse aquí en el mismo Pull Request que introduce el código.

## Procedural Studio

Procedural Studio se abre con `K` y permite construir mientras el mundo continúa visible y activo. La interfaz ofrece categorías para trayectorias, figuras, estructuras, assets, modificadores, materiales, recetas e instancias.

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

Cada figura conserva su gramática propia y puede combinar radio, espaciado, grosor, giro y materiales sin reutilizar estado residual de una figura anterior.

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

La categoría Estructuras contiene generadores arquitectónicos orientados por puntos:

- Pilares monumentales configurables.
- Puentes arqueados entre apoyos.
- Torii japoneses gigantes que respetan diferencias verticales del terreno sin ondular sus vigas.

## Assets naturales

Los assets se originan en `P1` y utilizan dos materiales configurables.

### Árbol épico

Genera raíces de soporte, tronco curvado y decreciente, ramas Bézier, ramificación secundaria, copa irregular y vegetación colgante. Analiza el espacio circundante en 16 direcciones y orienta el crecimiento hacia la zona con menos obstáculos.

```text
/komaedit asset tree 70 24 7 dark_oak_wood azalea_leaves
```

### Roca monumental

Genera un megalito erosionado con silueta asimétrica, estratos, grietas, material secundario superficial y rocas satélite.

```text
/komaedit asset boulder 32 24 6 deepslate moss_block
```

### Tronco ancestral

Genera un tronco caído largo y curvado, con extremos rotos, desgaste, raíces expuestas, ramas residuales y crecimiento superficial. Un segundo punto puede definir su dirección.

```text
/komaedit asset log 80 10 7 stripped_spruce_wood moss_block
```

### Raíces antiguas

Genera un tocón retorcido con raíces radiales, arcos naturales elevados y vegetación secundaria.

```text
/komaedit asset roots 48 22 7 mangrove_roots moss_block
```

## Materiales y degradados

KomaEdit admite bloques individuales, patrones de WorldEdit y paletas de degradado. La distribución puede seguir el eje, exposición, cavidades, bordes, curvatura o propiedades específicas de la figura. Los assets naturales utilizan un material principal y otro de detalle.

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

## Política de actualización

Cada nueva función debe documentar:

1. Qué problema resuelve.
2. Cómo se abre o ejecuta.
3. Sus parámetros y materiales.
4. Su interacción con puntos, hologramas y modificadores.
5. Consideraciones de rendimiento.
6. Comandos de ejemplo cuando correspondan.
