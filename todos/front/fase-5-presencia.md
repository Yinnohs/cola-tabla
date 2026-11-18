# Front — Fase 5: Presencia

**DoD:** la presencia refleja quién está y quién edita, y desaparece < ~30 s tras un cierre brusco.

- [ ] **F5.1** Feature `presence`: dominio puro (`presence-state`, reducer de eventos `presence.*`), puerto `PresencePort` + adapter STOMP
- [ ] **F5.2** Heartbeat periódico (~8 s) mientras el tablero está abierto; `leave` al salir; pausar si la pestaña está oculta (opcional)
- [ ] **F5.3** UI: avatares de quienes ven el tablero (`shared/ui/Avatar`)
- [ ] **F5.4** UI: "Ana está editando esta tarjeta" (soft lock): avisa, **no bloquea**
- [ ] **F5.5** `StartEditingCard` / `StopEditingCard` al abrir/cerrar `CardDialog`
- [ ] **F5.6** Playwright: cerrar una pestaña a la fuerza ⇒ su avatar desaparece en < 30 s en la otra
- [ ] **F5.7** Tests: reducer, hook con fake de puerto
