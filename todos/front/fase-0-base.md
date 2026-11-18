# Front — Fase 0: Base del proyecto

**DoD:** lint + typecheck + tests + dependency-cruiser en verde en CI.

- [x] **F0.1** Proyecto Vite + React + TypeScript (`strict: true`), Vitest + React Testing Library, ESLint, Prettier
- [x] **F0.2** Esqueleto `src/app/ features/ shared/`:
  - [x] `app/{di,providers,routes}`
  - [x] `features/{auth,workspaces,boards,lists,cards,realtime,presence}` con `index.ts` vacío y capas `domain/ application/ infrastructure/ ui/`
  - [x] `shared/{kernel,ui,lib,testing}`
- [x] **F0.3** `PortsContext` + `usePorts()` en `app/di`; helper de render con fakes en `shared/testing`
- [x] **F0.4** **dependency-cruiser** (y/o `eslint-plugin-boundaries`) con reglas 5.2 en verde:
  - [x] `domain` sin React/TanStack/fetch/stomp
  - [x] `application` sin `infrastructure`/`ui`
  - [x] `ui` sin `infrastructure`
  - [x] Feature→feature solo por `index.ts`
  - [x] `shared/*` no importa `features/`
  - [x] Test que demuestra que una violación falla
- [x] **F0.5** `shared/kernel`: `Result<T,E>`, ids tipados, con tests
- [x] **F0.6** `shared/lib/http-client.ts`: único wrapper de `fetch` (auth header, `ProblemDetail` → `AppError`, 409 → `StaleVersionError`), con tests (MSW)
- [x] **F0.7** CI: añadir job de frontend (lint + typecheck + test + depcruise) al workflow 🔗 B0.4
- [x] **F0.8** Scripts npm: `dev`, `build`, `lint`, `typecheck`, `test`, `depcruise`
- [x] **F0.9** Dockerfile del frontend / servido por Nginx (placeholder) 🔗 B0.3
