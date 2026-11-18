# Front — Fase 6: Escalado horizontal

Mayormente backend/infra; el frontend valida que el cambio de instancia es transparente.

- [ ] **F6.1** Playwright/E2E: dos clientes conectados a instancias distintas se ven; apagar una instancia ⇒ reconexión transparente y convergencia (🔗 B6.6)
- [ ] **F6.2** Verificar que el reconnect/resync (Fase 4) cubre el corte de instancia sin intervención del usuario
- [ ] **F6.3** Servir el frontend detrás de Nginx con el mismo origen para REST y WS (sin CORS ni mixed content)
- [ ] **F6.4** Ajustar backoff/jitter si hay "thundering herd" al caer una instancia
