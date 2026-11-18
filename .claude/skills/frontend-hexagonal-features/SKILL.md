---
name: frontend-hexagonal-features
description: Guía para trabajar en frontend/ (React + TS + Vite) de Kanban Live con arquitectura hexagonal y core dividido por funcionalidades (features). Úsala al crear o modificar features, puertos, adapters, casos de uso (hooks), reducers de eventos en tiempo real, dnd-kit o tests/reglas de import.
---

# Frontend: Hexagonal por features

Lee primero `kanban-project-context`.

## Estructura
```
src/
├── app/        # composition root: di/ (PortsContext), providers/, routes/
├── features/   # auth, workspaces, boards, lists, cards, realtime, presence
└── shared/     # kernel/ (Result, ids, fractional-index), ui/, lib/ (http-client), testing/
```
Cada feature es un mini-hexágono:
```
features/<x>/
├── domain/          # TS puro: tipos, invariantes, reducers apply-*-event, cálculo de posición
├── application/     # ports/ (interfaces) + hooks de caso de uso (TanStack Query) + query-keys
├── infrastructure/  # adapters: http-*-repository, dto, mapper, stomp
├── ui/              # componentes React
└── index.ts         # API pública (único punto de import desde fuera)
```

## Reglas de dependencia (dependency-cruiser en CI)
- `domain`: solo `shared/kernel`. Sin React, fetch, stomp, TanStack.
- `application`: `domain`, sus `ports`, `shared/kernel`, TanStack Query. Nunca `infrastructure`/`ui`, ni `fetch`/`stomp` directos.
- `infrastructure`: implementa `ports`; usa `domain` y `shared/lib`. Nunca `ui`.
- `ui`: `application`, tipos de `domain`, `shared/ui`. Nunca `infrastructure`.
- Feature → feature: **solo** vía `features/<otra>/index.ts`.
- `shared/*` nunca importa de `features/*`. Solo `app/` conoce adapters concretos.

## Receta: añadir una funcionalidad (ej. "archivar tarjeta")
1. **Domain**: tipo/invariante en `cards/domain`; si hay evento WS, añadir caso en `apply-card-event.ts` (puro, con test).
2. **Port**: añadir `archive(id, version)` a `CardRepository` (`application/ports`).
3. **Use case**: `use-archive-card.ts` con `useMutation`; optimistic update + rollback; mapear `StaleVersionError` a estado de conflicto. Obtiene el puerto con `usePorts()`. Test con `renderHook` + fake del puerto (en `shared/testing`).
4. **Adapter**: método en `HttpCardRepository` + DTO/mapper (DTO → dominio; los tipos generados de OpenAPI solo se tocan aquí). Test con MSW.
5. **UI**: componente que usa **solo** el hook. Sin `fetch`, sin conocer DTOs. Test con RTL (incluyendo teclado si es interactivo).
6. **Export**: añadir a `index.ts` solo lo que otras features necesiten.
7. **DI**: si hay adapter nuevo, registrarlo en `app/di`.

## Tiempo real (feature `realtime`)
- Puerto `BoardEventsPort { subscribe, fetchEventsSince }`; adapter `StompBoardEvents` (`@stomp/stompjs`, backoff exponencial + jitter, JWT en CONNECT, renovar token).
- `sequence-tracker` (dominio puro): decide `APPLY | IGNORE_DUPLICATE | RESYNC` según `seq`. Salto (41→43) ⇒ resync (`since` o refetch).
- Ignorar eventos con el `clientId` propio.
- `realtime` **orquesta**; cada feature aporta su `apply-*-event` para modificar el caché de TanStack Query. El WS nunca es fuente de verdad: ante duda, refetch.

## Reutilización
- `shared/kernel/fractional-index.ts` debe pasar **los mismos vectores** que el backend (`docs/contracts/fractional-index-vectors.json`).
- Un único `shared/lib/http-client.ts` (auth, ProblemDetail → `AppError`, 409 → `StaleVersionError`).
- `shared/testing`: fakes de puertos y factories (`aCard()`, `aBoard()`); no crear mocks ad hoc por test.
- `shared/ui`: componentes sin lógica de negocio.

## dnd-kit y accesibilidad
`PointerSensor` + `KeyboardSensor` siempre; anuncios `aria-live`; todo flujo debe completarse sin ratón. La posición nueva se calcula en `domain/card-ordering.ts` (puro) entre vecinos, nunca reindexando la lista. Con virtualización (Fase 7) usar `DragOverlay` para el ítem arrastrado.

## Antipatrones
`fetch` en componentes · lógica de negocio en JSX · importar `features/x/infrastructure/...` desde otra feature · tipos DTO en `domain` · `useEffect` para sincronizar lo que debería ser un reducer puro · carpetas `components/ hooks/ services/` globales con lógica de negocio · desactivar reglas de depcruise/ESLint.

## Antes de dar por terminado
`typecheck`, `lint`, `vitest`, `depcruise` en verde; feature nueva con su `index.ts`; tarea marcada en `initial-plan.md`.
