# Contratos entre capas

Ficheros compartidos entre backend y frontend. Se crean cuando lo indica el plan:

| Fichero | Contenido | Fase |
| --- | --- | --- |
| `fractional-index-vectors.json` | Vectores de prueba de `FractionalIndex`, consumidos por tests de ambos lados | 1 |
| `openapi.json` | Especificación OpenAPI 3.1 de la REST (springdoc). Base del cliente TypeScript (F1.2) | 1 |
| `board-events.md` | `BoardEnvelope` versionado (`schemaVersion`) y tipos de evento | 3 |

Errores: RFC 7807 `ProblemDetail` con `code` estable (`STALE_VERSION`, `FORBIDDEN_WORKSPACE`, ...).

## `openapi.json`

- Se genera desde los controllers (`adapter.in.web`) y la verifica `OpenApiContractIT`: si la API cambia y el fichero no se regenera, `./mvnw verify` falla (detección de deriva).
- Regenerar tras cambiar la API (desde `backend/`):

  ```
  ./mvnw -B verify -Dit.test=OpenApiContractIT -Dopenapi.export=true -Dtest='NoSuch*' -Dsurefire.failIfNoSpecifiedTests=false
  ```

- En vivo (perfil `local`): http://localhost:8080/v3/api-docs y Swagger UI en http://localhost:8080/swagger-ui.html. Fuera de los perfiles `local`/`test` springdoc está desactivado (`springdoc.api-docs.enabled=false`), aunque `SecurityConfig` deja la ruta permitida.
- Esquema de seguridad: `bearer-jwt` (HTTP bearer, JWT de Keycloak).
- Cada operación tiene un `operationId` estable (`createBoard`, `moveCard`, ...) pensado para generar el cliente.
