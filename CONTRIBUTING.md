# Flujo de desarrollo

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

## Publicaciones

`main` solo se actualizará cuando se decida preparar una publicación estable. Ese proceso queda fuera del flujo normal de desarrollo y requerirá aprobación explícita.
