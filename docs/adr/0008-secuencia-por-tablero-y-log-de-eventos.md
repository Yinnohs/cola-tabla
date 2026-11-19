# ADR-0008: Secuencia por tablero asignada en la transacción + log `board_event`

- **Estado:** Aceptado
- **Fecha:** 2026-11-18
- **Ámbito:** backend

## Contexto

El WebSocket solo notifica; la BD es la verdad. Los clientes necesitan detectar eventos perdidos o desordenados (reconexión, varias instancias) y resincronizar. Un `seq` global o un timestamp no sirve: el orden de commit puede diferir del orden de asignación y los relojes divergen entre instancias. Nota de numeración: el índice reservaba 0004 para esta decisión, pero ese número ya lo ocupa `0004-sin-use-effect.md`; se usa el siguiente libre.

## Decisión

- `board.event_seq bigint NOT NULL DEFAULT 0` guarda el último seq asignado de cada tablero.
- Todo cambio con evento ejecuta, **dentro de la transacción del caso de uso**, `UPDATE board SET event_seq = event_seq + 1 WHERE id = ? RETURNING event_seq` y luego inserta la fila en `board_event(board_id, seq, type, payload jsonb, created_at)` en la misma tx.
- El `UPDATE` toma el lock de fila del tablero hasta el commit: los escritores del mismo tablero se serializan, el orden de `seq` coincide con el orden de commit y no hay huecos (un rollback deshace también el incremento y la fila del log).
- `PRIMARY KEY (board_id, seq)` aporta unicidad e índice para el resync (`seq > N`). `ON DELETE CASCADE` desde `board`.
- Puertos: `boards.application.port.out.BoardEventLog` (adapter JDBC `BoardEventJdbcLog`, exige tx: `MANDATORY`) y `boards.application.port.in.RecordBoardEventUseCase` para que lists/cards registren eventos de su tablero sin tocar el adapter de boards. El payload viaja como JSON ya serializado.
- El broadcast (AFTER_COMMIT) lo hace después `collaboration`; el cliente aplica/ignora/resincroniza comparando `seq`.

## Alternativas consideradas

| Alternativa | Pros | Contras |
| --- | --- | --- |
| `SEQUENCE` de Postgres por tablero | Sin lock de fila | Una secuencia por tablero; no transaccional: deja huecos y orden != commit |
| Secuencia global | Simple | Huecos por tablero; sin garantía de orden de commit |
| Timestamp | Sin estado | Relojes y empates; no detecta huecos |
| `MAX(seq)+1` | Sin columna | Carreras; requiere lock o reintentos |
| **Elegida:** contador en `board` + log | Sin huecos, orden de commit, log para resync | Contención por tablero (aceptable: un tablero = pocos escritores) |

## Consecuencias

- Positivas: detección exacta de huecos; resync barato desde `board_event`; mismo mecanismo con Redis (Fase 6).
- Negativas / deuda aceptada: serialización de escrituras por tablero; el log crece (la política de retención se definió después en ADR-0005).
- Cómo se verifica: `BoardEventJdbcLogIT` (seq por tablero sin huecos, tx obligatoria, jsonb, unicidad); test de rollback en B3.7.

## Nota de actualización (2026-11-19)

Los dos pendientes de esta decisión quedaron resueltos en ADR posteriores, sin cambiar lo decidido aquí: la política de retención del log está en ADR-0005 (apartado 4) y la renovación/expiración del JWT en WebSocket en ADR-0006.

## Referencias

- `todos/back/fase-3-tiempo-real.md` (B3.3)
- ADR-0006 (renovación de JWT en WebSocket) y ADR-0005 (retención del log)
