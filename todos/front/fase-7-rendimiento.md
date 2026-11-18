# Front — Fase 7: Rendimiento y producción

**DoD:** un tablero de > 5.000 tarjetas es fluido y se mide con datos.

- [ ] **F7.1** Virtualización con TanStack Virtual + dnd-kit (> 5.000 tarjetas)
  - [ ] Estrategia de `DragOverlay` para el ítem arrastrado
  - [ ] Auto-scroll durante el arrastre; mantener accesibilidad por teclado
- [ ] **F7.2** Carga por lista / paginación si el payload es grande 🔗 B7.1
- [ ] **F7.3** Evitar re-renders: selectores/memoización en listas y tarjetas; medir con React Profiler
- [ ] **F7.4** Aplicación de eventos RT por lotes (batching) bajo ráfagas
- [ ] **F7.5** Build de producción: code splitting, análisis de bundle
- [ ] **F7.6** Métricas del cliente (opcional): latencia percibida cambio→render
- [ ] **F7.7** Aportar datos al informe final 🔗 B7.6
