# Commercial Readiness

Este documento separa la calidad técnica del producto de las decisiones legales y comerciales que debe aprobar el propietario antes de vender KomaEdit.

## Entregado en la base 4.8

- Proyecto persistente por mundo y jugador.
- Escrituras atómicas, recuperación de temporales, checksums y backups manuales.
- Instancias reabribles con trayectoria editable.
- Cuotas configurables y permisos granulares para servidores.
- Bitácora durable de operaciones completadas, canceladas o interrumpidas.
- Navegación principal del Studio en español e inglés.
- Pruebas unitarias ejecutadas por GitHub Actions.
- Metadata de soporte, política de seguridad y releases reproducibles por canal.

## Requisitos antes de cobrar

- Definir licencia comercial, EULA, política de reembolsos y alcance del soporte con asesoría legal. El repositorio conserva su licencia actual hasta que el propietario apruebe el cambio.
- Confirmar derechos sobre código, iconos, capturas, nombres, paletas y cualquier recurso distribuido.
- Publicar una matriz de compatibilidad probada por versión de Minecraft, Fabric, Java, WorldEdit y mods gráficos populares.
- Preparar una guía de inicio de menos de diez minutos, recetas de ejemplo propias y un mundo de demostración sin contenido de terceros.
- Definir un canal de soporte, tiempos de respuesta y una política para migraciones incompatibles.
- Realizar pruebas cerradas con constructores externos y registrar tiempo hasta el primer resultado, errores, rendimiento y claridad de la interfaz.

## Diferenciación prioritaria

KomaEdit debe venderse como un **estudio procedural no destructivo**, no como otro reemplazo de WorldEdit. Las funciones con mayor valor diferencial son:

1. Instancias vivas que pueden reabrirse, mover nodos y reconstruirse sin perder ediciones manuales.
2. Materiales conscientes de la figura, con zonas semánticas por motivo en vez de ruido global.
3. Restricciones de curva para anclar, conformar y mantener continuidad arquitectónica.
4. Variantes deterministas de una misma receta para explorar alternativas sin perder reproducibilidad.
5. Diagnóstico previo al Build: bloques estimados, chunks afectados, conflictos y coste esperado.
6. Paquetes de recetas versionados con dependencias, miniatura, autoría y migraciones.

## Puertas de publicación

Una versión candidata a venta debe cumplir simultáneamente:

- `./gradlew build` y GitHub Actions sin fallos.
- Entrada y salida repetida de mundos sin perder recetas ni instancias.
- Backup creado y restaurado en una copia del mundo.
- Operaciones en el límite configurado sin bloquear el tick del servidor.
- Denegación correcta de cada permiso en un servidor dedicado.
- Validación visual de todas las figuras, estructuras, degradados y hologramas soportados.
- Changelog, documentación, compatibilidad y limitaciones actualizados.
