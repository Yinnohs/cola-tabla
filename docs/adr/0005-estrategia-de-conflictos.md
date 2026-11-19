# ADR-0005: Estrategia de conflictos y resincronización (LWW para mover, 409 para editar, log con retención)

- **Estado:** Aceptado
- **Fecha:** 2026-11-19
- **Ámbito:** backend (contrato consumido por el frontend)

## Contexto

Varios usuarios editan el mismo tablero a la vez y los clientes pierden la conexión. Hay que decidir qué pasa cuando dos cambios chocan y cómo se recupera un cliente que estuvo desconectado. Reglas del proyecto: la BD es la verdad, el WebSocket solo notifica, `@Version` en entidades editables, mover una tarjeta es **una** fila actualizada (ADR-0003). El log `board_event` con `seq` por tablero (ADR-0008) ya existe y su retención quedó pendiente.

## Decisión

### 1. Mover (tarjeta y lista): last-write-wins, sin 409

- La posición es la verdad: gana el último movimiento que llega a la BD. El caso de uso carga la fila, calcula la posición con sus vecinas y persiste con `saveMove`, un `UPDATE` directo **sin** `AND version = ?` que además hace `version = version + 1`.
- Resultado: dos movimientos concurrentes nunca producen `409`; el segundo en el lock de fila sobrescribe. El bump de versión hace que cualquier edición basada en una versión previa al movimiento se detecte como obsoleta.
- Un movimiento solo escribe lista + posición (+ `updated_at`): no pisa el título ni la descripción editados por otro.
- El rebalanceo (ADR-0003) conserva su propia semántica de versión.

### 2. Editar (título/descripción de tarjeta, nombre de lista, nombre de tablero): concurrencia optimista

- `PATCH` lleva `version`. Si no coincide con la actual: `409` `application/problem+json` con `code = STALE_VERSION` y la propiedad `current` con el estado actual del recurso (misma forma que `CardResponse` / `ListResponse` / `BoardResponse`). El cliente compara, y puede reintentar con `current.version` (sobrescribir) o descartar su cambio. Sin segundo `GET`.
- `StaleVersionException` (sharedkernel, sin Spring) transporta `current` como objeto opaco; los servicios de aplicación lo rellenan con el resumen que ya devuelven (`CardSummary`, `ListSummary`, `BoardSummary`) y `GlobalExceptionHandler` lo serializa.
- Límite conocido: si el choque se detecta recién al escribir (carrera entre el chequeo y el flush, `OptimisticLockingFailureException`), el `409` lleva `code = STALE_VERSION` pero **sin** `current` (leer dentro de una transacción ya marcada para rollback no es fiable). El cliente debe hacer `GET` en ese caso, que es el comportamiento previo.

### 3. Resincronización tras una desconexión

- `GET /api/boards/{boardId}/events?since={seq}` (`@PreAuthorize("@perms.canView(#boardId)")`) devuelve `{boardId, since, lastSeq, events[]}`; cada evento tiene la misma forma que el envelope del WebSocket (`docs/contracts/board-events.md`). El `clientId` no se persiste en el log, así que los eventos reproducidos llevan `clientId = null`.
- `since == lastSeq` → lista vacía. `since` negativo o `since > event_seq` actual → `400` (`code = INVALID_SINCE`): el cliente tiene un cursor imposible.
- `410 Gone` con `code = EVENTS_GONE` cuando el log ya no puede reconstruir el tramo: faltan más de `kanban.events.max-gap` eventos (500 por defecto), o parte del tramo fue purgado por retención. Como `seq` no tiene huecos (ADR-0008), el servidor comprueba que los eventos retenidos cubren exactamente `since+1 … lastSeq` (equivale a `since >= primer_seq_retenido − 1`). El cliente responde recargando el tablero completo.

### 4. Retención del log (cierra el pendiente de ADR-0008)

- `kanban.events.retention` (24 h por defecto): los eventos con `created_at` anterior se purgan.
- Un job `@Scheduled` (`BoardEventPurgeJob`, adapter de entrada de `boards`, `kanban.events.purge.interval` = 1 h) invoca el caso de uso `PurgeBoardEventsUseCase`, que borra en lotes acotados (`batch-size` 1000, `max-batches` 100 por ejecución; cada lote es una transacción corta) para no mantener locks largos. Lo que no cabe queda para la siguiente ejecución.
- `V8__board_event_retention.sql` añade `idx_board_event_created_at`.
- `@EnableScheduling` vive en `platform.scheduling.SchedulingConfig`, desactivable con `kanban.scheduling.enabled=false` (apagado en el perfil `test`, salvo el test del job).
- Efecto colateral aceptado: un cliente desconectado más de la retención recibe `410` y recarga; es el mismo camino que el de "demasiado atrasado".
- Con varias instancias (Fase 6) la purga es idempotente: dos jobs concurrentes borran filas distintas o ninguna; no hace falta lock distribuido.

## Alternativas consideradas

| Alternativa | Pros | Contras |
| --- | --- | --- |
| Mover con `@Version` y `409` | Detecta todo | Movimientos de arrastrar-soltar fallarían con frecuencia y el usuario no tiene qué "comparar"; la posición ya es un dato de última escritura |
| Fusionar ediciones campo a campo (merge) | Menos conflictos | Complejidad tipo CRDT que el proyecto descarta |
| `409` sin estado actual | Más simple | Obliga a un `GET` extra y a otra condición de carrera |
| Resync siempre con recarga completa | Sin endpoint ni retención | Tráfico y parpadeo en cada microcorte |
| Log sin retención | Sin job | Crecimiento ilimitado de `board_event` |
| Purgar por `seq` (conservar los últimos N por tablero) | Sin dependencia del reloj | Consulta más cara por tablero; el tiempo es lo que importa a un cliente que se reconecta |

## Consecuencias

- Positivas: arrastrar y soltar nunca falla por concurrencia; los conflictos de edición son resolubles desde el cuerpo del `409`; el cliente tiene un camino de reconexión barato (`since`) y uno de reserva (`410` → recarga).
- Negativas / deuda aceptada: LWW puede deshacer silenciosamente un movimiento ajeno (aceptado, la UI se actualiza por el evento `*.moved`); `current` ausente en la carrera de escritura; eventos reproducidos sin `clientId`.
- Cómo se verifica: `ConflictsIT` (409 con `current` para los tres recursos, movimientos concurrentes sin 409), `CardPersistenceAdapterIT`/`ListPersistenceAdapterIT` (`saveMove` ignora versión obsoleta), `EventsResyncIT` (since válido, vacío, 400, 410 por `max-gap` y por purga), `GetBoardEventsSinceServiceTest`, `PurgeBoardEventsServiceTest`, `BoardEventJdbcLogIT`, `BoardEventPurgeJobIT`, `SchemaMigrationIT` (V8), `OpenApiContractIT`.

## Referencias

- `initial-plan.md` sección 4.x / Fase 4; `todos/back/fase-4-conflictos-reconexion.md` (B4.1–B4.4)
- ADR-0003 (posiciones), ADR-0008 (seq y log), ADR-0006 (JWT en WebSocket)
