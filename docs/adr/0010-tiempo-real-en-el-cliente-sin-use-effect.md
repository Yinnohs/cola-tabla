# ADR-0010: Tiempo real en el cliente (STOMP + store externo, sin `useEffect`)

- **Estado:** Aceptado
- **Fecha:** 2026-11-19
- **Ámbito:** frontend (feature `realtime`) + ampliación mínima de backend (`lastSeq` en `GET /api/boards/{id}`)

## Contexto

La Fase 3 del frontend consume `docs/contracts/board-events.md`: STOMP sobre WebSocket nativo, un topic por tablero y eventos con `seq` por tablero. Restricciones: sin `useEffect` (ADR-0004), hexagonal por features con dependency-cruiser, `application` no puede importar `@stomp/stompjs`, el dominio de una feature no puede importar otra feature, y el WS **nunca** es fuente de verdad (la BD vía REST sí).

El algoritmo de `seq` necesita un `lastSeq` de partida: el `seq` del último evento ya reflejado en la carga REST. Hasta ahora `GET /api/boards/{id}` no lo devolvía.

## Decisión

1. **`lastSeq` en `BoardDetailsResponse`.** El reader lee `board.event_seq` en la misma consulta que el tablero y `GetBoardService` pasa a `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`: las tres lecturas (tablero, listas, tarjetas) salen de **una única instantánea**. Como cada escritura sube `event_seq` y cambia el estado en la misma transacción (ADR-0008), la instantánea contiene exactamente los eventos `seq <= lastSeq`. En el cliente el `NormalizedBoard` guarda `lastSeq` (la caché `boardKeys.detail` es la única fuente de ese contador; no hay estado paralelo).
2. **Puerto `BoardEventsPort { subscribe(boardId, onEvent, onStatus), fetchEventsSince(boardId, since) }`** en `realtime/application/ports`. Adapter `StompBoardEvents` (infrastructure): **una conexión compartida** y una suscripción STOMP por tablero abierto; abre con el primer `subscribe`, cierra con la última baja. JWT en la cabecera `Authorization` del `CONNECT` (`beforeConnect`, se lee el token en cada conexión). El cliente real queda tras una interfaz mínima `StompTransport` (mockeable) y se crea con `brokerURL` (sin `new WebSocket`, regla ESLint). `fetchEventsSince` se implementa con el cliente HTTP (`HttpBoardEventLog`). **Sin reconexión automática** (`reconnectDelay: 0`) ni renovación del token en el socket: es Fase 4 (ADR-0006).
3. **Sin `useEffect`: store externo + `useSyncExternalStore`.** `createBoardSyncSession` es un store por tablero cuyo `subscribe` (el de `useSyncExternalStore`) abre la suscripción al puerto con el primer listener y la cierra con el último; su snapshot es el estado de conexión. `useBoardSync(boardId)` crea la sesión con `useMemo` y devuelve el snapshot. `RealtimeProvider` (`app/providers`) llama a `useBoardSync` y se monta en `BoardPage` **cuando el tablero ya está en caché** (la carga REST es lo primero). Alternativas descartadas: ref callback (no encaja: no hay nodo DOM propio) y un registro global en el provider (más estado, sin ventaja).
4. **Reducers puros por feature, orquestados por `realtime`.** `applyBoardEvent` (boards/domain), `applyCardEvent` (cards/application) y `applyListEvent` (lists/application) son `(NormalizedBoard, Evento) -> NormalizedBoard`. Los de cards/lists viven en `application` y no en `domain` porque usan los helpers de caché de `boards` y `domain-is-pure` impide que el dominio de una feature importe otra; cada feature define su propio tipo de evento (ya traducido al dominio) y `realtime/application/apply-envelope.ts` traduce `BoardEnvelope → evento de feature`. Un tipo desconocido o `schemaVersion` distinto se convierte en `type: 'unknown'` y provoca **recarga**, no se ignora.
5. **`sequence-tracker` puro** (`decideSequence(lastSeq, seq)`: `APPLY | IGNORE_DUPLICATE | RESYNC`). Ante hueco se **recarga el tablero completo** (`refetchQueries`); `fetchEventsSince` no se usa para reaplicar eventos (eso es Fase 4). Convergencia de las recargas: la sesión anota el `seq` máximo visto (`maxSeen`); mientras una recarga está en curso los eventos solo actualizan `maxSeen`; al terminar, si la carga quedó por detrás (`lastSeq < maxSeen`) se recarga otra vez (máx. 5 rondas, y se corta si no hay progreso). Un vigilante de `QueryCache` aplica la misma comprobación a recargas externas (foco de ventana, invalidaciones, rollback optimista), y al pasar a `connected` se consulta `fetchEventsSince(lastSeq)` solo como **detector** de eventos perdidos entre la carga y la suscripción (si el servidor va por delante, o la consulta falla, se recarga).
6. **`clientId` por pestaña:** `crypto.randomUUID()` normalizado a `[A-Za-z0-9._:-]{1,64}` (`shared/kernel/client-id.ts`), en memoria (una recarga genera otro). `createApiClient({ clientId })` añade un middleware que envía `X-Client-Id` en las peticiones **no seguras** (POST/PATCH/DELETE; los GET no lo necesitan) y lo omite si el valor no cumple el formato. `create-ports` lo genera y lo expone también como `Ports.clientId` para que la sesión reconozca sus ecos. Un evento con el `clientId` propio **no se reaplica pero sí avanza `lastSeq`**.
7. **Evento remoto frente a una mutación optimista propia en vuelo.** Las mutaciones propias se serializan con el `scope` por tablero (ya existente), así que no se solapan entre sí. Entre una mutación y un evento remoto el estado se protege por **versión**: los reducers y las respuestas REST (`onSuccess` de crear/mover/editar usa `reconcileCard`/`reconcileList`) solo aplican una entidad si su `version` no es anterior a la de la caché. Un evento remoto más nuevo que la respuesta de nuestra petición no retrocede (probado en `use-board-sync.test.tsx`); si ocurre sobre la misma tarjeta mientras está “en el aire”, la UI muestra transitoriamente el estado remoto y converge con la respuesta o con el siguiente evento. Si la mutación falla, el rollback restaura el snapshot previo (con su `lastSeq` antiguo) y `invalidateQueries` recarga: el vigilante garantiza que no se pierdan eventos recibidos entre medias.
8. **Errores STOMP:** un frame `ERROR` se registra (`console.warn`); si el mensaje es “Access denied” el estado pasa a `access-denied` (aviso mínimo “Sin acceso…” en `RealtimeProvider`) y **no se reintenta**; cualquier otro `ERROR` pasa a `error`. Un cierre posterior no pisa el motivo. Un `subscribe` nuevo tras un fallo crea una conexión nueva. El indicador de conexión elaborado es Fase 4 (el estado ya se expone por `onStatus`/`useRealtimeStatus`).
9. **Playwright** (`frontend/e2e`, `npm run e2e`): fuera de vitest/depcruise; sin `webServer`; login real por contexto (sessionStorage), seed por API con `kanban-dev-cli`.

## Alternativas consideradas

| Alternativa | Pros | Contras |
| --- | --- | --- |
| `useEffect` para suscribir | Patrón habitual | Prohibido (ADR-0004); dobles ejecuciones en StrictMode |
| `lastSeq` en un estado paralelo al caché | No toca `NormalizedBoard` | Dos fuentes de verdad que se desincronizan en recargas/rollbacks |
| Leer `event_seq` con una consulta aparte sin transacción común | Sin cambio de aislamiento | Carrera: el estado puede incluir eventos posteriores o carecer de anteriores a `lastSeq` |
| Reaplicar eventos de `fetchEventsSince` ante un hueco | Menos tráfico que recargar | Es el alcance de Fase 4; recargar es siempre correcto |
| Registro global de sesiones en el provider | Una sola sesión por tablero aunque haya varios consumidores | Más estado global; hoy hay un único consumidor y la conexión STOMP ya es compartida |

## Consecuencias

- Positivas: el cliente reanuda desde un `lastSeq` consistente; sincronización sin efectos y testeable con fakes (`FakeBoardEvents`); un evento perdido o fuera de orden nunca deja la UI divergente de forma permanente; capas intactas (depcruise y ArchUnit en verde).
- Negativas / deuda aceptada: tras un hueco se recarga todo el tablero (coste proporcional al tamaño); sin reconexión ni renovación de JWT en el socket (se pierde la conexión si el servidor la cierra) hasta la Fase 4; la ventana entre el `SUBSCRIBE` y la consulta `since` no es atómica (se cubre con el vigilante y el siguiente evento); los reducers de cards/lists viven en `application`, no en `domain` como sugería la receta.
- Cómo se verifica: `sequence-tracker.test.ts` (+fast-check), `apply-*-event.test.ts` (+fast-check), `use-board-sync.test.tsx` (con `FakeBoardEvents`), `stomp-board-events.test.ts`, `stomp-transport.test.ts`, `parse-envelope.test.ts`, `client.test.ts` (X-Client-Id), `create-ports.test.ts`; backend `BoardServicesTest`, `BoardPersistenceAdapterIT`, `EventsResyncIT`, `OpenApiContractIT`; e2e Playwright de dos navegadores.

## Referencias

- `docs/contracts/board-events.md`; ADR-0004, ADR-0005, ADR-0006, ADR-0008; `todos/front/fase-3-tiempo-real.md` (F3.1–F3.9)
