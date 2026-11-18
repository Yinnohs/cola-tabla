---
name: kanban-project-context
description: Contexto global del proyecto Kanban Live (cola-tabla). Úsala SIEMPRE al empezar una sesión o antes de cambiar backend/ o frontend/: visión, reglas no negociables, mapa del repo, flujo de eventos, trampas y dónde está el plan.
---

# Kanban Live — contexto del proyecto

## Qué es
Tablero tipo Trello en tiempo real: `Workspace → Board → List → Card`. Auth OIDC (Keycloak), multi-tenant con roles por workspace (`OWNER/EDITOR/VIEWER`), presencia, reconexión, escalado horizontal. **No** es un editor de texto colaborativo (sin CRDT/OT).

## Documentos fuente (léelos, no los dupliques)
- `initial-plan.md` (raíz): plan por fases con tareas numeradas, DoD y tabla de estado (sección 11).
- `implentacion-inicial-colatabla.md`: documento original de requisitos.
- `docs/adr/`: decisiones de arquitectura.
- `docs/contracts/`: contratos compartidos (vectores de fractional index, eventos WS).

## Skills hermanas
- Trabajo en `backend/` → `backend-hexagonal-screaming`.
- Trabajo en `frontend/` → `frontend-hexagonal-features`.

## Reglas no negociables
1. **La BD es la fuente de verdad**; el WebSocket solo notifica.
2. Eventos solo con `@TransactionalEventListener(phase = AFTER_COMMIT)`.
3. Permisos en REST **y** en `SUBSCRIBE`/`CONNECT` de STOMP.
4. Mover una tarjeta = **una** fila actualizada (fractional index string, `COLLATE "C"`).
5. `@Version` (optimistic lock) en entidades editables; conflicto ⇒ `409` con `code=STALE_VERSION`.
6. Arquitectura hexagonal en ambos lados, forzada por **ArchUnit** (back) y **dependency-cruiser** (front). Nunca desactivar esos tests; si estorban, el diseño está mal o hay que escribir un ADR.
7. Backend = *screaming architecture* (paquetes por negocio). Frontend = core dividido por *features*.

## Mapa del repo
```
backend/   Spring Boot — com.kanbanlive.{workspaces,membership,boards,lists,cards,collaboration,presence,sharedkernel,platform}
frontend/  React+Vite — src/{app,features/{auth,workspaces,boards,lists,cards,realtime,presence},shared}
infra/     docker-compose, keycloak realm, nginx
docs/      adr/, contracts/
```

## Flujo de un cambio (ej. mover tarjeta)
UI (dnd-kit) → `use-move-card` (optimistic) → `CardRepository` (puerto) → `HttpCardRepository` → `CardController` → `MoveCardUseCase` (@Transactional: guarda, incrementa `boards.event_seq`, inserta `board_event`) → commit → `AFTER_COMMIT` → `BoardEventBroadcaster` (STOMP local; Redis pub/sub desde Fase 6) → `/topic/boards.{id}` → `StompBoardEvents` → `sequence-tracker` (APPLY / IGNORE / RESYNC) → `apply-card-event` sobre caché TanStack Query. Eventos con el `clientId` propio se ignoran.

## Trampas frecuentes
- Emitir antes del commit (estados fantasma).
- Olvidar permisos en suscripciones WS.
- Reordenar toda la lista en cada movimiento.
- Tratar el WS como verdad.
- JWT que expira con el socket abierto.
- Importar Spring/JPA en `domain`, o React/fetch en `domain` del front.
- Importar archivos internos de otra feature (usar solo su `index.ts`).

## Cómo trabajar
1. Localiza la tarea en `initial-plan.md` (número de tarea y fase). No saltes de fase.
2. Orden: dominio → casos de uso con fakes → adapters → UI.
3. PRs pequeños: una tarea, una capa.
4. Tests primero en dominio/aplicación (rápidos, sin infraestructura).
5. Al terminar: marcar la tarea y actualizar la tabla de estado en `initial-plan.md`; si tomaste una decisión de diseño, crea/actualiza un ADR.
6. Si trabajas con SDD, los artefactos viven en engram/openspec según la configuración del orquestador; no pegues contenido de código en el plan.

## Estado
Consultar la sección 11 de `initial-plan.md` (fuente única; no copiar aquí).
