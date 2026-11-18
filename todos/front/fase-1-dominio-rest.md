# Front — Fase 1: Dominio y REST

Orden por feature: dominio → casos de uso (con fakes) → infraestructura → UI.
**DoD:** crear y mover tarjetas funciona; D&D completable solo con teclado (test RTL + manual).

- [x] **F1.1** `shared/kernel/fractional-index.ts` pasando los mismos vectores `docs/contracts/fractional-index-vectors.json` 🔗 B1.1
- [x] **F1.2** Cliente TS generado desde OpenAPI en `shared/lib/api/` (solo `infrastructure` lo importa) 🔗 B1.9
- [x] **F1.3** Feature `workspaces` completa en 4 capas: lista y creación (puerto, `HttpWorkspaceRepository`, mapper, hooks, UI)
- [x] **F1.4** Feature `boards`: carga de tablero, `query-keys`, normalización en caché
- [x] **F1.5** Feature `lists`: dominio, puerto, adapter HTTP, crear/renombrar/mover
- [x] **F1.6** Feature `cards`:
  - [x] `card.ts`, `card-ordering.ts` (nueva posición al soltar usando `FractionalIndex`)
  - [x] Puerto `CardRepository` + `HttpCardRepository` + `card-dto`/`card-mapper`
  - [x] `use-move-card`: mutation con optimistic update + rollback
  - [x] `use-update-card`, `use-create-card`, `use-delete-card`
- [x] **F1.7** dnd-kit: `PointerSensor` + `KeyboardSensor`, anuncios accesibles (`aria-live`), integración en `cards/ui/dnd`
- [x] **F1.8** UI: `BoardView`, `ListView`, `CardView`, `CardDialog`
- [x] **F1.9** Tests: dominio y aplicación con fakes; adapters con MSW; teclado en dnd con RTL
