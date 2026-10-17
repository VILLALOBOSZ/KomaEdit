# Flujo de desarrollo

## Sincronización previa

Antes de comenzar cualquier tarea se debe ejecutar:

```bash
git fetch origin --prune
git switch staging
git pull --ff-only origin staging
```

La implementación solo comienza cuando `staging` coincide con `origin/staging` y el árbol de trabajo está limpio.

## Ramas

- `main` conserva la versión estable publicada y no recibe cambios de desarrollo.
- `staging` es la rama de integración para todos los cambios.
- Cada cambio comienza en una rama nueva con el formato `agent/descripcion-corta`.
- Las ramas de trabajo se integran exclusivamente mediante Pull Request hacia `staging`.
- No se permiten pushes directos a `main` después de la publicación inicial.

## Proceso

1. Actualizar `staging`.
2. Crear `agent/descripcion-corta` desde `staging`.
3. Implementar y validar el cambio con `./gradlew build`.
4. Publicar la rama y abrir un Pull Request hacia `staging`.
5. Revisar que la integración continua termine correctamente.
6. Fusionar el Pull Request y eliminar la rama de trabajo.

## Documentación

Toda función nueva o modificación visible para el jugador debe quedar documentada en `docs/MOD_DOCUMENTATION.md`. Los comandos y ejemplos afectados deben actualizarse en el mismo Pull Request, junto con `CHANGELOG.md` cuando el cambio sea relevante para una versión.

## Versionado

- `mod_version` controla la línea de desarrollo de `staging`.
- GitHub Actions añade el SHA corto a la versión interna de cada build de desarrollo.
- Los merges a `staging` crean prereleases con el formato `komaedit-staging-<versión>-<sha>`.
- `release_version` controla exclusivamente las publicaciones de `main`.
- Cada commit nuevo publicado desde `main` debe incrementar `release_version`; una versión estable no puede reutilizarse para otro commit.

## Publicaciones

`main` solo se actualizará cuando se decida preparar una publicación estable. Ese proceso queda fuera del flujo normal de desarrollo y requerirá aprobación explícita.

Si GitHub no emite el evento `push` después de una fusión automatizada, un mantenedor puede ejecutar manualmente **Build KomaEdit** seleccionando la rama `staging`. La ejecución manual puede publicar únicamente una prerelease de `staging`; nunca puede publicar `main`. El tag conserva el SHA del commit exacto y el workflow reutiliza o reemplaza solamente el asset de ese mismo tag.
