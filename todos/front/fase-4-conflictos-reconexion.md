# Front — Fase 4: Conflictos y reconexión

**DoD:** el estado converge tras cortar la red, cambiar desde otro cliente y reconectar.

- [ ] **F4.1** `realtime/domain/sequence-tracker.ts` (puro): decide `APPLY | IGNORE_DUPLICATE | RESYNC` (duplicado, salto 41→43, desorden), con tests
- [ ] **F4.2** Reconexión con backoff exponencial + jitter; estado de conexión (`connected | reconnecting | offline`) expuesto a la UI
- [ ] **F4.3** Al reconectar: `fetchEventsSince(lastSeq)`; si `410` o demasiados ⇒ refetch completo del tablero 🔗 B4.1/B4.2
- [ ] **F4.4** Renovación de token del WS: reconectar con token nuevo antes de expirar 🔗 B4.5
- [ ] **F4.5** `use-update-card`: manejar `409 STALE_VERSION` → estado "conflicto"
  - [ ] `ConflictBanner` ("alguien cambió esto") con comparar / sobrescribir / descartar
  - [ ] Mover tarjeta: last-write-wins, sin diálogo de conflicto
- [ ] **F4.6** Playwright: `context.setOffline(true)`, cambios desde otro cliente, reconectar ⇒ converge
- [ ] **F4.7** Indicador de conexión en la UI (banner "reconectando…")
