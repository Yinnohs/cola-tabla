# Contratos entre capas

Ficheros compartidos entre backend y frontend. Se crean cuando lo indica el plan:

| Fichero | Contenido | Fase |
| --- | --- | --- |
| `fractional-index-vectors.json` | Vectores de prueba de `FractionalIndex`, consumidos por tests de ambos lados | 1 |
| `board-events.md` | `BoardEnvelope` versionado (`schemaVersion`) y tipos de evento | 3 |

Errores: RFC 7807 `ProblemDetail` con `code` estable (`STALE_VERSION`, `FORBIDDEN_WORKSPACE`, ...).
