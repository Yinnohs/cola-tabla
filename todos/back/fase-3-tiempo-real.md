# Back — Fase 3: Tiempo real en una instancia

**DoD:** test Playwright de dos navegadores en verde (🔗 F3.6).

- [x] **B3.1** `platform/messaging`: config STOMP; `ChannelInterceptor` que valida el JWT en `CONNECT`
- [x] **B3.2** Interceptor de `SUBSCRIBE`: `/topic/boards.{id}` exige `canView(boardId)` — **test negativo obligatorio**
- [x] **B3.3** `boards`: migraciones `board.event_seq` + `board_event(board_id, seq, type, payload jsonb, created_at)`, índice `(board_id, seq)`
  - [x] Secuencia asignada **dentro de la tx** (`UPDATE boards SET event_seq = event_seq + 1 ... RETURNING`)
  - [x] Guardar fila en `board_event` en la misma tx
  - [x] ADR-0008: secuencia por tablero + log
- [x] **B3.4** `DomainEventPublisher` implementado en `platform/messaging` con `ApplicationEventPublisher`
- [x] **B3.5** `collaboration`: `BoardEnvelope { boardId, seq, type, payload, clientId, occurredAt, schemaVersion }`
  - [x] Recibir `clientId` del cliente (cabecera/campo) y propagarlo en el envelope
  - [x] Documentar en `docs/contracts/board-events.md` 🔗 F3.1
- [x] **B3.6** `collaboration/adapter/in/event`: `@TransactionalEventListener(AFTER_COMMIT)` → puerto `BoardEventBroadcaster`; adapter `LocalStompBroadcaster` → `/topic/boards.{id}`
- [x] **B3.7** Test: **rollback ⇒ no se emite evento**
- [x] **B3.8** Eventos para cards, lists y board: `card.created|updated|moved|deleted`, `list.*`, `board.updated`
- [x] **B3.9** Decisión sobre expiración de JWT con socket abierto (se documenta en Fase 4, B4.5); aquí dejar el punto de extensión (`SocketTokenLifecycle`, documentado en `docs/contracts/board-events.md`)
