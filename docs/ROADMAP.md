# Roadmap

El objetivo completo es un editor profesional de mundos. KomaEdit 2.0 entrega el núcleo de rendimiento y brochas, pero el resto se incorporará por fases verificables.

## Fase 2.1 — Preview renderizado

- Buffer de instancias por chunk en cliente.
- Cajas translúcidas y contornos OpenGL.
- Colores por operación: añadir, eliminar, reemplazar y conflicto.
- Invalidación incremental al mover el objetivo.
- Protocolo de red comprimido para planes de preview.

El preview actual usa partículas muestreadas y no envía millones de posiciones al cliente.

## Fase 2.2 — Selecciones y transformaciones

- Selección cubo, esfera, cilindro y polígono.
- Lazo proyectado y selección inteligente por conectividad.
- Gizmos para mover, rotar, escalar y reflejar.
- Preview transformado sin modificar el clipboard original.

## Fase 2.3 — Clipboard y schematics

- Copiar, cortar, pegar y múltiples clipboards.
- Historial persistente con presupuesto de disco/memoria.
- Lectura y escritura Sponge Schematic con NBT y entidades.
- Biblioteca, miniaturas y búsqueda.

## Fase 2.4 — Terraform procedural

- Generadores de montañas, acantilados, colinas, valles y mesetas.
- Ríos, lagos, islas y cuevas con restricciones topológicas.
- OpenSimplex 2 y composición visual de nodos de ruido.
- Filtros blur, sharpen, warp, cellular, ridged y máscaras de pendiente/bioma.

## Fase 2.5 — Decoración

- Reglas de distribución por Poisson disk y densidad.
- Vegetación, rocas, troncos, hongos, corales y plantas.
- Rotación/estado de bloque aleatorio y validación de soporte.
- Presets por bioma y altura.

## Fase 2.6 — Plataforma extensible

- API pública y registro de `BrushOperation`.
- Scripts con sandbox y presupuesto de ejecución.
- Macros, parámetros y repetición determinista.
- Plugins externos y compatibilidad opcional con otros mods.
- Compartir presets con versión y migraciones.

## Fase 2.7 — Multijugador

- Permisos granulares por herramienta y volumen.
- Cuotas concurrentes y prioridad del scheduler.
- Preview compartido y aprobación por roles.
- Persistencia de jobs y recuperación después de reinicio.

## Criterios de aceptación

Cada fase debe incluir:

- Perfil de memoria y tiempo con volúmenes representativos.
- Ningún acceso a mundo desde workers.
- Cancelación sin corrupción y undo coherente.
- Compatibilidad en servidor dedicado e integrado.
- Documentación del formato persistente/protocolo.
- Pruebas unitarias para algoritmos puros y pruebas de integración cuando el harness de Minecraft lo permita.
