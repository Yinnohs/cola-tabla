# Back — Fase 4: Conflictos y reconexión

**DoD:** el estado converge tras un corte de red (🔗 F4.6).

- [x] **B4.1** Endpoint `GET /boards/{id}/events?since={seq}` (permiso `canView`)
- [x] **B4.2** Política "demasiado atrasado": si faltan más de N eventos o el log fue purgado → `410 Gone` (el cliente recarga el tablero completo)
- [x] **B4.3** Retención/TTL del log `board_event` (job de purga acotado)
- [x] **B4.4** Conflictos:
  - [x] Mover tarjeta: last-write-wins, la posición es la verdad (sin 409)
  - [x] Editar título/descripción: `@Version` no coincide → `409 STALE_VERSION` con el estado actual en el cuerpo para poder comparar
  - [x] ADR-0005: estrategia de conflictos
- [x] **B4.5** Expiración de JWT en WS: reconectar con token nuevo antes de expirar; el servidor cierra la sesión cuando el token vence
  - [x] ADR-0006: renovación de JWT en WebSocket
- [x] **B4.6** Tests: `since` válido, `since` demasiado antiguo (410), 409 en edición concurrente, cierre por token expirado
