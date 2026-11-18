# Back — Fase 7: Rendimiento y producción

**DoD:** se puede afirmar con datos cuántos usuarios concurrentes aguanta y dónde está el cuello de botella.

- [ ] **B7.1** Paginación/carga por lista en `GetBoard` si el payload es grande (🔗 F7.2); índices revisados con `EXPLAIN`
- [ ] **B7.2** Dataset de prueba: tablero con > 5.000 tarjetas (script/seed)
- [ ] **B7.3** k6: 1.000 conexiones WS; latencia cambio→recepción (p50/p95/p99)
- [ ] **B7.4** Micrometer: conexiones activas, eventos/s, latencia de fan-out
- [ ] **B7.5** OpenTelemetry (tracing) + dashboard Grafana en `infra/`
- [ ] **B7.6** Informe final con datos: usuarios concurrentes soportados y cuello de botella (`docs/`)
