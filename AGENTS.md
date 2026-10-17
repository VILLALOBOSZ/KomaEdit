# Instrucciones permanentes del repositorio

## Sincronización obligatoria

Antes de analizar, editar, compilar o ejecutar cualquier tarea:

1. Ejecutar `git fetch origin --prune`.
2. Verificar que no existan cambios locales ajenos a la tarea.
3. Cambiar a `staging`.
4. Ejecutar `git pull --ff-only origin staging`.
5. Confirmar que `staging` y `origin/staging` apuntan al mismo commit.

Nunca comenzar trabajo desde una copia desactualizada ni resolver divergencias mediante force-push.

## Flujo de ramas

- `main` permanece congelada y solo puede actualizarse con autorización explícita para una publicación estable.
- Todo trabajo comienza desde la versión actualizada de `staging`.
- Cada cambio utiliza una rama nueva llamada `agent/descripcion-corta`.
- Nunca se realizan pushes directos de desarrollo a `staging` o `main`.
- Cada rama se publica y se integra mediante Pull Request hacia `staging`.
- Antes de fusionar, la compilación de GitHub Actions debe terminar correctamente.
- Después del merge se elimina la rama de trabajo y se vuelve a sincronizar `staging`.

## Documentación del mod

- Toda función nueva o cambio visible para el usuario debe actualizar `docs/MOD_DOCUMENTATION.md`.
- La documentación debe explicar objetivo, acceso, parámetros, materiales, comportamiento, rendimiento y limitaciones relevantes.
- Si cambia un comando, también deben actualizarse sus ejemplos.
- Los cambios importantes deben registrarse adicionalmente en `CHANGELOG.md`.

## Versionado y publicaciones

- `mod_version` representa la línea de desarrollo utilizada por `staging`.
- Los builds de `staging` añaden automáticamente el SHA corto al `fabric.mod.json` y al nombre del JAR.
- Cada merge a `staging` publica una prerelease llamada `komaedit-staging-<versión>-<sha>`.
- `release_version` es exclusivo de `main` y nunca incluye nombres de canal, sufijos alpha ni SHA.
- Antes de publicar un commit nuevo en `main`, incrementar `release_version` (`1.0.0`, `1.0.1`, etc.).
- No reutilizar una versión estable para un commit diferente.

## Validación

- Ejecutar `./gradlew build` antes de publicar una rama.
- No publicar cachés, builds locales, mundos, registros, tokens ni copias de `backups/`.
- Mantener el repositorio privado.
