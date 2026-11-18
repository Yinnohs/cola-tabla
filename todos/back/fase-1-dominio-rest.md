# Back — Fase 1: Dominio y REST (sin tiempo real)

Orden: una capacidad a la vez, siempre domain → application (con fakes) → adapters.
**DoD:** crear y mover tarjetas por REST; mover = 1 `UPDATE` (assert en test de persistencia).

- [ ] **B1.1** `FractionalIndex` en `sharedkernel/position`
  - [ ] VO string estilo LexoRank + `between(prev, next)`
  - [ ] Tests de propiedades (jqwik): orden total, `between` estricto, sin colisiones
  - [ ] `docs/contracts/fractional-index-vectors.json` (compartido con front 🔗 F1.1)
  - [ ] ADR-0003: fractional indexing y rebalanceo
- [ ] **B1.2** Migraciones Flyway (una por cambio): `workspace`, `board`, `board_list`, `card`; `position` con `COLLATE "C"`; índice `(list_id, position)`; columnas `version`
- [ ] **B1.3** `workspaces`: agregado + `CreateWorkspace`, `ListWorkspaces`; adapter REST + JPA (entidad JPA ≠ dominio, mapper)
- [ ] **B1.4** `boards`: `CreateBoard`, `GetBoard` (tablero + listas + tarjetas), `RenameBoard`
- [ ] **B1.5** `lists`: `CreateList`, `RenameList`, `MoveList` (fractional)
- [ ] **B1.6** `cards`: `CreateCard`, `UpdateCard` (`@Version`), `MoveCard` (una sola fila), `DeleteCard`
  - [ ] Traducir `OptimisticLockingFailureException` → `StaleVersionException` en el adapter
- [ ] **B1.7** Rebalanceo de posiciones cuando se agota el espacio (caso raro, con test)
- [ ] **B1.8** `platform/web`: `ProblemDetail` handler con `code` estable (`STALE_VERSION` → 409, 404, 403, 422)
- [ ] **B1.9** OpenAPI (springdoc) publicado, base para el cliente TS 🔗 F1.2
- [ ] **B1.10** Tests: dominio + aplicación con fakes; `@DataJpaTest` + Testcontainers; `@WebMvcTest` de controllers; ArchUnit sigue verde
