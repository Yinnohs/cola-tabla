# Front — Fase 3: Tiempo real en una instancia

**DoD:** test Playwright con dos navegadores: lo que se mueve en A se ve en B.

- [ ] **F3.1** Contrato `BoardEnvelope` en tipos TS (`schemaVersion`, `seq`, `type`, `payload`, `clientId`) según `docs/contracts/board-events.md` 🔗 B3.5
- [ ] **F3.2** Puerto `BoardEventsPort { subscribe(boardId, onEvent, onStatus); fetchEventsSince(boardId, seq) }` (feature `realtime`)
- [ ] **F3.3** Adapter `StompBoardEvents` (`@stomp/stompjs`): JWT en `CONNECT`, suscripción a `/topic/boards.{id}`
- [ ] **F3.4** `clientId` único por pestaña, enviado en las mutaciones; los eventos propios se ignoran
- [ ] **F3.5** `apply-*-event` (reducers puros) en `cards`, `lists`, `boards`: `(BoardState, Event) → BoardState`, con tests
- [ ] **F3.6** `use-board-sync` en `realtime`: aplica eventos al caché de TanStack Query; `realtime` orquesta, cada feature sabe aplicar los suyos
- [ ] **F3.7** `RealtimeProvider` en `app/providers`; el WS **no** es fuente de verdad (la BD vía REST sí)
- [ ] **F3.8** Playwright: 2 contextos de navegador, mover en A ⇒ se ve en B
- [ ] **F3.9** Tests: reducers, `use-board-sync` con fake de `BoardEventsPort`
