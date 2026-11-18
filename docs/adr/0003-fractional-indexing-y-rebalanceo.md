# ADR-0003: Fractional indexing (LexoRank-like) y rebalanceo

- **Estado:** Aceptado
- **Fecha:** 2026-11-18
- **Ámbito:** transversal

## Contexto

Mover una tarjeta (o lista) con DnD no debe reescribir las posiciones de sus vecinas: un `UPDATE` por movimiento es un requisito (DoD de la Fase 1) y reduce conflictos entre usuarios concurrentes. Backend y frontend deben calcular posiciones idénticas (el cliente hace movimiento optimista) y Postgres debe ordenar exactamente igual que el código. Trampa: el orden de `varchar` depende del collation; con collations de idioma (`en_US`) mayúsculas/minúsculas se mezclan.

## Decisión

- `FractionalIndex` (`sharedkernel.position`) es un value object (record) con un string que representa una fracción `0.d1d2…` en base 62. Alfabeto `0-9A-Za-z` (mismo orden que ASCII).
- Forma canónica: no vacío, solo alfabeto, **sin `0` final** (así cada fracción tiene una única representación y siempre existe un valor estrictamente entre dos distintos). Longitud máxima 64.
- `between(prev, next)` acepta límites `null` (principio/final/lista vacía), devuelve un punto medio y lanza `ValidationException` si `prev >= next`. Primera inserción: `"V"`.
- El orden es el orden de string plano (`String.compareTo`, equivalente a `ORDER BY position` con `COLLATE "C"`). Las columnas `position` se declaran `varchar(64) COLLATE "C"` y se indexan `(list_id, position)` / `(board_id, position)`.
- **Agotamiento:** inserciones repetidas en el mismo hueco alargan la clave ~1 carácter cada ~6 inserciones. `isExhausted()` es `true` al llegar a 32 caracteres. Entonces (B1.7) se rebalancea la lista dentro de una transacción reasignando `FractionalIndex.evenlySpaced(n)` (posiciones equiespaciadas con holgura de ≥3 huecos entre vecinas). Es un caso raro y es la única operación que reescribe varias filas.
- **Implementación del rebalanceo (B1.7):** `CreateListService`, `MoveListService`, `CreateCardService` y `MoveCardService` capturan `RebalanceRequiredException`, reasignan los hermanos (`Positions.rebalance` + `ListRepository`/`CardRepository.findIdsInOrder` y `updatePositions`) dentro de la misma transacción del caso de uso y reintentan una vez; cada fila reescrita incrementa su `version`. Si el reintento vuelve a agotarse, la excepción sigue traduciéndose a 409 `REBALANCE_REQUIRED`.
- `docs/contracts/fractional-index-vectors.json` es el contrato compartido: el backend (JUnit) y el frontend (vitest, F1.1) consumen los mismos vectores `between`, `evenlySpaced`, inválidos y orden.

## Alternativas consideradas

| Alternativa | Pros | Contras |
| --- | --- | --- |
| Enteros densos (reindexar al mover) | Simple | N `UPDATE` por movimiento, conflictos, ruido en eventos |
| Gaps numéricos (`double`/`bigint`) | Un `UPDATE` | Se agota rápido, `double` pierde precisión, rebalanceo frecuente |
| Lista enlazada (`prev_id`) | Un `UPDATE` local | Lecturas recursivas, difícil de ordenar en SQL |
| LexoRank de Jira (buckets) | Probado | Más complejo de lo necesario |
| **Elegida:** fractional index string base 62 | Un `UPDATE`, orden por índice, portable a TS | Claves crecen en hot spots; requiere rebalanceo ocasional y `COLLATE "C"` |

## Consecuencias

- Positivas: mover = una fila; orden resuelto por Postgres con índice; mismo algoritmo en front y back verificado con vectores.
- Negativas / deuda aceptada: rebalanceo (B1.7) debe emitir eventos de reordenación completa en Fase 3; el cliente debe reproducir exactamente `between`.
- Cómo se verifica: tests jqwik (orden total, `between` estricto, sin colisiones), test de vectores, `SchemaMigrationIT` (collation `C` y orden de bytes).

## Referencias

- `initial-plan.md` secciones 4.5 y 5
- `docs/contracts/fractional-index-vectors.json`
- ADR-0002
