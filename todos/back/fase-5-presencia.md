# Back — Fase 5: Presencia

**DoD:** cerrar el navegador a la fuerza elimina al usuario de la presencia en < ~30 s.

- [ ] **B5.1** `presence/domain` + puertos: `PresenceStore` (out); casos de uso `Heartbeat`, `LeaveBoard`, `StartEditingCard`, `StopEditingCard`
- [ ] **B5.2** `adapter/out/redis`: claves de presencia con TTL (~20 s) renovadas por heartbeat (~8 s)
- [ ] **B5.3** Soft lock: clave `lock:card:{id}` con TTL; avisa pero **no bloquea** escrituras
- [ ] **B5.4** `adapter/in/ws`: controladores STOMP de presencia con el mismo control de permisos (`canView`)
- [ ] **B5.5** Eventos `presence.*` por el canal del tablero (o `/topic/boards.{id}.presence`); decidir y documentar en `board-events.md`
- [ ] **B5.6** Detectar desconexión de sesión STOMP (`SessionDisconnectEvent`) para limpiar antes del TTL
- [ ] **B5.7** Tests (Testcontainers Redis): expiración por TTL, renovación por heartbeat, soft lock no bloquea
