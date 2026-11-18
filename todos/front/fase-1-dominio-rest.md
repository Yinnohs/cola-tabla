# Front — Fase 1: Dominio y REST

Orden por feature: dominio → casos de uso (con fakes) → infraestructura → UI.
**DoD:** crear y mover tarjetas funciona; D&D completable solo con teclado (test RTL + manual).

- [ ] **F1.1** `shared/kernel/fractional-index.ts` pasando los mismos vectores `docs/contracts/fractional-index-vectors.json` 🔗 B1.1
- [ ] **F1.2** Cliente TS generado desde OpenAPI en `shared/lib/api/` (solo `infrastructure` lo importa) 🔗 B1.9
- [ ] **F1.3** Feature `workspaces` completa en 4 capas: lista y creación (puerto, `HttpWorkspaceRepository`, mapper, hooks, UI)
- [ ] **F1.4** Feature `boards`: carga de tablero, `query-keys`, normalización en caché
- [ ] **F1.5** Feature `lists`: dominio, puerto, adapter HTTP, crear/renombrar/mover
- [ ] **F1.6** Feature `cards`:
  - [ ] `card.ts`, `card-ordering.ts` (nueva posición al soltar usando `FractionalIndex`)
  - [ ] Puerto `CardRepository` + `HttpCardRepository` + `card-dto`/`card-mapper`
  - [ ] `use-move-card`: mutation con optimistic update + rollback
  - [ ] `use-update-card`, `use-create-card`, `use-delete-card`
- [ ] **F1.7** dnd-kit: `PointerSensor` + `KeyboardSensor`, anuncios accesibles (`aria-live`), integración en `cards/ui/dnd`
- [ ] **F1.8** UI: `BoardView`, `ListView`, `CardView`, `CardDialog`
- [ ] **F1.9** Tests: dominio y aplicación con fakes; adapters con MSW; teclado en dnd con RTL
