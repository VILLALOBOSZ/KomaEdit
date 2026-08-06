# Roadmap

KomaEdit se enfoca en ser el complemento procedural no destructivo de WorldEdit y Axiom. Las figuras actuales se consideran una biblioteca estable; las siguientes fases profundizan edición, materiales y colaboración sin convertir el mod en otro editor genérico.

## 4.8 — Base de producto

- Proyectos persistentes por mundo y jugador.
- Migración no destructiva, escrituras atómicas y checksums.
- Instancias reabribles con su trayectoria editable.
- Backups, bitácora de jobs, permisos y cuotas configurables.
- Internacionalización progresiva y pruebas automáticas.

## 4.9 — Diagnóstico antes de construir

- Estimación exacta de bloques, chunks y memoria antes de Build.
- Mapa de conflictos con bloques modificados manualmente.
- Presupuesto configurable de holograma y construcción por jugador.
- Panel de salud del proyecto con acciones de reparación seguras.

## 5.0 — Materiales semánticos

- Zonas nombradas por figura: núcleo, cuerpo, borde, punta, cavidad y reverso.
- Una paleta independiente por zona con transición controlada.
- Vista aislada de cada zona en el holograma.
- Presets con miniatura y compatibilidad declarada por figura.

## 5.1 — Variantes deterministas

- Semilla editable por receta.
- Galería rápida de variantes del mismo recorrido.
- Bloqueo parcial de zonas aprobadas mientras se regenera el resto.
- Comparación A/B antes de actualizar una instancia.

## 5.2 — Restricciones de trayectoria

- Anclajes fijos, tangentes, simetría y continuidad de curvatura.
- Conformado parcial a suelo, techo o superficies seleccionadas.
- Medidas arquitectónicas entre nodos y reglas de pendiente.
- Resolución automática de quiebres sin mover anclajes aprobados.

## 5.3 — Paquetes profesionales

- Manifiesto versionado con autor, licencia, dependencias y compatibilidad.
- Miniaturas generadas localmente y búsqueda por etiquetas.
- Migraciones de formato y validación antes de importar.
- Firma opcional para catálogos privados de equipos.

## 5.4 — Colaboración de servidor

- Hologramas compartidos y aprobación por roles.
- Bloqueo optimista de instancias durante edición.
- Auditoría de quién construyó, actualizó o retiró una instancia.
- Cuotas por grupo y prioridad del scheduler.

## Criterios de aceptación

Cada fase requiere:

- Ningún acceso al mundo desde workers.
- Cancelación sin corrupción y undo coherente.
- Perfil de memoria, ticks y tiempo con volúmenes representativos.
- Compatibilidad probada en servidor integrado y dedicado.
- Migración documentada para cualquier cambio de formato.
- Pruebas unitarias de algoritmos puros y pruebas de integración cuando el harness lo permita.
