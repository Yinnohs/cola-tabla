# Initial Plan — Kanban Live (cola-tabla)

> Fuente: `implentacion-inicial-colatabla.md`. Este documento lo amplía con arquitectura hexagonal (backend y frontend), screaming architecture en el backend, core del frontend dividido por funcionalidades, y un plan de ejecución por tareas.
> Skills para agentes: `.claude/skills/` (ver sección 12). **Léelas antes de escribir código.**

---

## 1. Objetivo y alcance

Tablero tipo Trello en tiempo real (`Workspace → Board → List → Card`) con auth multi-tenant, presencia, resincronización tras cortes de red, escalado horizontal y pruebas de carga. **No** es un editor de texto colaborativo (sin CRDT/OT).

Principios no negociables:

1. **La base de datos es la fuente de verdad.** El WebSocket solo notifica.
2. **Nunca emitir antes del commit** (`AFTER_COMMIT`).
3. **Permisos en REST *y* en suscripciones WebSocket.**
4. **Una escritura por movimiento** (fractional indexing).
5. **Dependencias hacia adentro** (hexagonal) en backend y frontend, verificadas automáticamente en CI.

## 2. Stack (sin cambios respecto al documento original)

| Capa | Tecnologías |
| --- | --- |
| Backend | Java 21+, Spring Boot, Spring WebSocket (STOMP), Spring Security (OAuth2 Resource Server), Spring Data JPA, PostgreSQL, Flyway, Redis |
| Frontend | React + TypeScript, Vite, TanStack Query, `@stomp/stompjs`, dnd-kit, TanStack Virtual |
| Infra | Docker Compose: Postgres, Redis, Keycloak, Nginx |
| Testing | JUnit 5, Testcontainers, **ArchUnit**, Vitest + React Testing Library, **dependency-cruiser**, Playwright, k6 |

Añadidos a propósito: **ArchUnit** (backend) y **dependency-cruiser / eslint-plugin-boundaries** (frontend) para que la arquitectura se haga cumplir en CI, no por buena voluntad.

---

## 3. Estructura del repositorio

```
cola-tabla/                      # raíz del repo
├── initial-plan.md
├── .claude/skills/              # guías para agentes (sección 12)
├── backend/
├── frontend/
├── infra/                       # docker-compose.yml, keycloak/realm.json, nginx/nginx.conf
├── docs/adr/                    # ADR-0001 hexagonal, ADR-0002 fractional-index, ...
└── .github/workflows/
```

---

## 4. Backend — Screaming + Hexagonal

### 4.1 Idea central

El primer nivel de paquetes **grita el negocio** (workspaces, boards, cards…), no el framework (`controller`, `service`, `repository`). Dentro de cada capacidad de negocio se aplica hexagonal: `domain` ← `application` ← `adapter`.

```
backend/src/main/java/com/kanbanlive/
├── KanbanLiveApplication.java
│
├── workspaces/                    # capacidad: espacios de trabajo
├── membership/                    # capacidad: miembros y roles por workspace
├── boards/                        # capacidad: tableros (+ secuencia de eventos)
├── lists/                         # capacidad: columnas/listas
├── cards/                         # capacidad: tarjetas y movimiento
├── collaboration/                 # capacidad: tiempo real (STOMP, secuencias, resync)
├── presence/                      # capacidad: quién está, soft lock
│
├── sharedkernel/                  # bloques de construcción reutilizables (SIN Spring)
│   ├── domain/                    # AggregateRoot, DomainEvent, Entity<ID>, ValueObject, DomainException
│   ├── application/               # UseCase<I,O>, Command/Query markers, Clock port, IdGenerator port
│   └── position/                  # FractionalIndex (value object + generador "between(a,b)")
│
└── platform/                      # infraestructura transversal (Spring, config)
    ├── security/                  # JWT resource server, CurrentUser, @perms bean
    ├── persistence/               # config JPA, auditoría, base de entidades JPA
    ├── web/                       # ProblemDetail handler, filtros, CORS
    └── messaging/                 # config STOMP, Redis, serialización de eventos
```

Estructura **interna estándar** de cada capacidad (ejemplo `cards/`):

```
cards/
├── domain/
│   ├── model/        Card, CardId, CardTitle, CardDescription     # puro, sin Spring/JPA
│   ├── event/        CardCreated, CardMoved, CardUpdated, CardDeleted
│   └── error/        CardNotFound, StaleCardVersion
├── application/
│   ├── port/
│   │   ├── in/       MoveCardUseCase, CreateCardUseCase, ...      # interfaces + Command/Result
│   │   └── out/      CardRepository, ListPositions, DomainEventPublisher (ver 4.4)
│   └── service/      MoveCardService implements MoveCardUseCase   # orquesta, @Transactional
└── adapter/
    ├── in/
    │   └── web/      CardController, CardRequest/Response (DTOs), CardWebMapper
    └── out/
        └── persistence/  CardJpaEntity, CardJpaRepository (Spring Data), CardPersistenceAdapter, CardEntityMapper
```

`collaboration/` y `presence/` añaden `adapter/in/ws/` (controladores STOMP, interceptors) y `adapter/out/redis/`.

### 4.2 Reglas de dependencia (se verifican con ArchUnit)

| Desde | Puede depender de | NO puede depender de |
| --- | --- | --- |
| `domain` | `sharedkernel.domain`, JDK | Spring, JPA, Jackson, `application`, `adapter` |
| `application` | `domain` propio, `sharedkernel`, puertos | `adapter`, Spring Web, JPA (`@Transactional` permitido) |
| `adapter.in.*` | `application.port.in`, DTOs propios | `adapter.out.*`, `domain` de otra capacidad |
| `adapter.out.*` | `application.port.out`, `domain` propio | `adapter.in.*` |
| Capacidad A → capacidad B | **Solo** `B.application.port.in` o eventos de dominio de B | `B.domain.model`, `B.adapter` |
| `platform` | todo lo que necesite para cablear | — (nadie depende de `platform` salvo el arranque) |

Tests ArchUnit obligatorios desde la Fase 0: `HexagonalArchitectureTest` (capas por capacidad) y `ScreamingPackagesTest` (nada de `..controller..`, `..service..`, `..repository..` en el primer nivel).

### 4.3 Bloques de construcción reutilizables (`sharedkernel`)

Crear **antes** de la primera capacidad; todas los reutilizan.

| Bloque | Responsabilidad |
| --- | --- |
| `AggregateRoot<ID>` | Identidad, `version`, lista de `DomainEvent` pendientes (`registerEvent`, `pullEvents`). |
| `DomainEvent` | Interfaz: `eventId`, `occurredAt`, `aggregateId`. Records inmutables por evento. |
| `Entity<ID>` / `ValueObject` | Igualdad por id / por valor. Records para VOs (`CardTitle`, `WorkspaceId`…). |
| `FractionalIndex` | VO de posición string estilo LexoRank + `between(prev, next)` + rebalanceo raro. Test de propiedades (jqwik). |
| `UseCase<I,O>` | Contrato mínimo de caso de uso (opcional; interfaces específicas por caso de uso son preferibles). |
| `DomainEventPublisher` (puerto out) | `publish(Collection<DomainEvent>)`. Lo implementa `platform.messaging` con `ApplicationEventPublisher`. |
| `Clock` / `IdGenerator` (puertos) | Tests deterministas. |
| `DomainException` + subtipos | El `ProblemDetail` handler en `platform.web` las traduce a 404/409/403/422. |
| `Page`/`PageQuery` neutros | Evita filtrar `org.springframework.data.Page` al dominio. |

Mapeo: **modelo de dominio ≠ entidad JPA.** `@Version` vive en la entidad JPA y se refleja como `long version` en el agregado; el adapter mapea ambos sentidos. Un `409` sale de `OptimisticLockingFailureException` traducida en el adapter a `StaleVersionException` (dominio).

### 4.4 Flujo de eventos y tiempo real

```
Controller → UseCase (@Transactional)
   → agregado emite DomainEvent
   → repo.save() + (misma tx) incrementa boards.event_seq y guarda fila en board_event (log)
   → DomainEventPublisher.publish()  [ApplicationEventPublisher]
        └─ @TransactionalEventListener(AFTER_COMMIT) en collaboration/adapter/in/event
              → BoardEventBroadcaster (puerto out de collaboration)
                    ├─ LocalStompBroadcaster       (Fase 3)  → /topic/boards.{id}
                    └─ RedisPubSubBroadcaster      (Fase 6)  → canal redis → cada instancia reenvía a STOMP local
```

- Cada evento viaja como `BoardEnvelope { boardId, seq, type, payload, clientId, occurredAt }`.
- `seq` se asigna **dentro de la transacción** (`UPDATE boards SET event_seq = event_seq + 1 ... RETURNING`), así el orden es el del commit.
- `board_event` (log acotado/TTL) permite el endpoint `GET /boards/{id}/events?since={seq}` para reconexión (Fase 4).
- Cambiar de `LocalStompBroadcaster` a Redis en Fase 6 = **un adapter nuevo**, cero cambios en dominio/aplicación.

### 4.5 Modelo de datos (Flyway, una migración por cambio)

`workspace`, `membership(workspace_id, user_id, role)`, `board(id, workspace_id, name, event_seq, version)`, `board_list(id, board_id, name, position, version)`, `card(id, list_id, board_id, title, description, position, version, ...)`, `board_event(board_id, seq, type, payload jsonb, created_at)`. Índices por `(list_id, position)` y `(board_id, seq)`. `position` con `COLLATE "C"` para orden lexicográfico correcto.

### 4.6 Testing backend

| Nivel | Qué | Herramienta |
| --- | --- | --- |
| Dominio | Agregados y VOs sin Spring | JUnit 5 + jqwik (FractionalIndex) |
| Aplicación | Casos de uso con puertos *fake* en memoria | JUnit 5 |
| Adapter out | Repos reales | `@DataJpaTest` + Testcontainers Postgres |
| Adapter in | REST/WS | `@WebMvcTest` / `@SpringBootTest` + Testcontainers |
| Arquitectura | Reglas de dependencias | ArchUnit |
| Seguridad | Aislamiento entre workspaces | tests de integración (Fase 2) |

---

## 5. Frontend — Hexagonal + core por funcionalidades

### 5.1 Idea central

El "core" del frontend se divide **por funcionalidad** (feature), no por tipo técnico. Cada feature es un mini-hexágono con cuatro capas. Lo compartido vive en `shared/`; el cableado (DI, router, providers) en `app/`.

```
frontend/src/
├── app/                              # composition root: providers, router, DI container
│   ├── di/                           # crea adapters concretos y los inyecta (Context)
│   ├── providers/                    # QueryClientProvider, AuthProvider, RealtimeProvider
│   └── routes/
│
├── features/
│   ├── auth/
│   ├── workspaces/
│   ├── boards/                       # tablero, vista, filtros
│   ├── lists/
│   ├── cards/                        # tarjeta, mover, editar, conflicto 409
│   ├── realtime/                     # cliente STOMP, secuencias, resync, reconexión
│   └── presence/                     # avatares, "Ana está editando", soft lock
│
└── shared/
    ├── kernel/                       # tipos base, Result, FractionalIndex (puro), ids
    ├── ui/                           # design system mínimo (Button, Dialog, Avatar…)
    ├── lib/                          # http client base, logger, utilidades puras
    └── testing/                      # fakes, factories, render helpers
```

Estructura **interna estándar** de cada feature (ejemplo `features/cards/`):

```
cards/
├── domain/                  # TS puro, sin React/fetch/stomp
│   ├── card.ts                       # tipos + invariantes
│   ├── card-ordering.ts              # calcular nueva posición al soltar (usa shared/kernel FractionalIndex)
│   └── apply-card-event.ts           # reducer puro: (BoardState, CardEvent) → BoardState
├── application/             # casos de uso + PUERTOS
│   ├── ports/
│   │   └── card-repository.ts        # interface CardRepository { move(...); update(...); ... }
│   ├── use-move-card.ts              # hook de caso de uso: TanStack Query mutation + optimistic update
│   ├── use-update-card.ts            # maneja 409 → estado "conflicto"
│   └── query-keys.ts
├── infrastructure/          # ADAPTERS (implementan puertos)
│   ├── http-card-repository.ts       # fetch → DTO → dominio (mapper)
│   └── card-dto.ts + card-mapper.ts
├── ui/                      # React: componentes presentacionales + contenedores finos
│   ├── CardView.tsx, CardDialog.tsx, ConflictBanner.tsx
│   └── dnd/                          # integración dnd-kit
└── index.ts                 # API pública del feature (único punto de importación externo)
```

### 5.2 Reglas de dependencia (dependency-cruiser / eslint-plugin-boundaries)

| Capa | Puede importar | NO puede importar |
| --- | --- | --- |
| `domain` | `shared/kernel`, TS | React, TanStack, fetch, stomp, otras capas |
| `application` | `domain` propio, `shared/kernel`, sus `ports`, TanStack Query | `infrastructure`, `ui`, `fetch`/`stomp` directos |
| `infrastructure` | `application/ports`, `domain`, `shared/lib` | `ui` |
| `ui` | `application`, `domain` (tipos), `shared/ui` | `infrastructure` |
| Feature A → Feature B | **solo** `B/index.ts` (API pública) | rutas internas de B |
| `app/` | todo (es el único que conoce adapters concretos) | — |
| `shared/*` | nada de `features/` | — |

**Inyección de dependencias:** `app/di` construye `{ cardRepository: new HttpCardRepository(http), boardEvents: new StompBoardEvents(...) }` y lo expone con un `PortsContext`. Los hooks de `application` obtienen los puertos con `usePorts()`. En tests se inyectan fakes en memoria → casos de uso testeables sin red.

### 5.3 Responsabilidad de `realtime` (feature transversal)

- **Puerto** `BoardEventsPort { subscribe(boardId, onEvent, onStatus); fetchEventsSince(boardId, seq) }`.
- **Adapter** `StompBoardEvents` (`@stomp/stompjs`, backoff exponencial, JWT en CONNECT, renovación de token).
- **Dominio puro** `sequence-tracker.ts`: detecta saltos (41→43) y decide `APPLY | IGNORE_DUPLICATE | RESYNC`. `clientId` descarta eventos propios.
- **Aplicación** `use-board-sync`: aplica eventos al caché de TanStack Query vía `apply-*-event` de cada feature (cards, lists, boards). `realtime` **orquesta**, cada feature **sabe cómo aplicar sus eventos**.

### 5.4 Reutilización de bloques

- `shared/kernel/fractional-index.ts` — espejo del algoritmo del backend, con **los mismos vectores de prueba** (fichero JSON compartido en `docs/contracts/fractional-index-vectors.json` consumido por tests de ambos lados).
- `shared/lib/http-client.ts` — único wrapper de `fetch` (auth header, ProblemDetail → `AppError`, 409 → `StaleVersionError`).
- `shared/kernel/result.ts` — `Result<T,E>` para dominio.
- `shared/testing/` — fakes de puertos y factories (`aCard()`, `aBoard()`).
- `shared/ui/` — componentes sin lógica de negocio.

### 5.5 Testing frontend

| Nivel | Qué | Herramienta |
| --- | --- | --- |
| Dominio | reducers y ordenamiento | Vitest (puro, rápido) |
| Aplicación | casos de uso con fakes de puertos | Vitest + RTL `renderHook` |
| Infraestructura | mappers y adapters con MSW | Vitest + MSW |
| UI | componentes, teclado en dnd | RTL |
| Arquitectura | reglas de import | dependency-cruiser en CI |
| E2E | 2 navegadores, desconexión de red | Playwright |

---

## 6. Contratos entre capas (definir temprano)

- **REST:** OpenAPI generado desde el backend (`springdoc`), cliente TS generado en `frontend/src/shared/lib/api/` (los DTO generados solo los toca `infrastructure`).
- **Evento WS:** `BoardEnvelope` versionado (`schemaVersion`). Tipos: `card.created|updated|moved|deleted`, `list.*`, `board.updated`, `presence.*`. Documentado en `docs/contracts/board-events.md`.
- **Errores:** RFC 7807 `ProblemDetail` con `code` estable (`STALE_VERSION`, `FORBIDDEN_WORKSPACE`, …).

---

## 7. Plan de ejecución por fases

Cada fase: **tareas → entregables → pruebas → criterio de terminado (DoD)**. Cada tarea es pequeña (≤ medio día) y se puede asignar a un agente. Marca `[x]` al completar y actualiza la tabla de la sección 11.

### Fase 0 — Base del proyecto

| # | Tarea | Capa |
| --- | --- | --- |
| 0.1 | Monorepo: carpetas, `.gitignore`, `.editorconfig`, README raíz | repo |
| 0.2 | `infra/docker-compose.yml`: Postgres, Redis, Keycloak (realm importado: clientes `kanban-web` público PKCE y `kanban-api`, 3 usuarios de prueba), Nginx (placeholder) | infra |
| 0.3 | Proyecto Spring Boot (Gradle/Maven), perfiles `local`/`test`, Flyway `V1__init.sql`, Actuator | backend |
| 0.4 | Crear **`sharedkernel`** completo (4.3) con tests unitarios | backend |
| 0.5 | Crear esqueleto de capacidades (paquetes vacíos con `package-info.java`) + **ArchUnit** (reglas 4.2) en verde | backend |
| 0.6 | Test de integración Testcontainers (Postgres) que arranca contexto + Flyway | backend |
| 0.7 | Proyecto Vite + React + TS estricto, Vitest, ESLint, Prettier | frontend |
| 0.8 | Esqueleto `app/ features/ shared/`, `PortsContext`, **dependency-cruiser** con reglas 5.2 en verde | frontend |
| 0.9 | `shared/kernel` (Result, ids) y `shared/lib/http-client` con tests | frontend |
| 0.10 | CI GitHub Actions: build + tests backend, lint + typecheck + tests + depcruise frontend, en cada PR | CI |
| 0.11 | ADR-0001 (hexagonal + screaming + feature slices) y ADR-0002 (modelo ≠ entidad JPA) | docs |

**DoD:** `docker compose up` levanta todo; test Testcontainers y tests de arquitectura (ArchUnit + depcruise) en verde en CI.

### Fase 1 — Dominio y REST (sin tiempo real)

Backend (en este orden, una capacidad a la vez, siempre domain → application → adapters):

| # | Tarea |
| --- | --- |
| 1.1 | `FractionalIndex` (sharedkernel/position) + tests de propiedades + `docs/contracts/fractional-index-vectors.json` |
| 1.2 | `workspaces`: agregado, casos de uso `CreateWorkspace`, `ListWorkspaces`; adapter REST + JPA |
| 1.3 | `boards`: `CreateBoard`, `GetBoard` (devuelve tablero + listas + tarjetas), `RenameBoard` |
| 1.4 | `lists`: `CreateList`, `RenameList`, `MoveList` (fractional) |
| 1.5 | `cards`: `CreateCard`, `UpdateCard` (@Version), `MoveCard` (una sola fila), `DeleteCard` |
| 1.6 | Rebalanceo de posiciones cuando se agota el espacio (caso raro, con test) |
| 1.7 | OpenAPI + `ProblemDetail` handler (`STALE_VERSION` → 409) |

Frontend:

| # | Tarea |
| --- | --- |
| 1.8 | `shared/kernel/fractional-index.ts` pasando los mismos vectores JSON |
| 1.9 | Feature `workspaces` (lista/creación) completa en 4 capas |
| 1.10 | Feature `boards` (carga de tablero, query keys, normalización en caché) |
| 1.11 | Features `lists` y `cards`: dominio (`card-ordering`), puertos, `HttpCardRepository`, mutations con optimistic update + rollback |
| 1.12 | dnd-kit: `KeyboardSensor` + `PointerSensor`, anuncios accesibles (`aria-live`) |
| 1.13 | Tests: dominio y aplicación con fakes; adapters con MSW |

**DoD:** crear y mover tarjetas funciona; D&D completable solo con teclado (test RTL + manual); mover = 1 `UPDATE` (assert en test de persistencia).

### Fase 2 — Autenticación y multi-tenant

| # | Tarea |
| --- | --- |
| 2.1 | `platform/security`: Resource Server JWT (issuer Keycloak), `CurrentUser` (puerto en `application`, adapter lee el `sub`) |
| 2.2 | `membership`: agregado `Membership(workspace, user, role)`, roles `OWNER/EDITOR/VIEWER`, casos de uso `InviteMember`, `ChangeRole`; creador del workspace = OWNER |
| 2.3 | Bean `@perms` (`canView/canEdit/canAdmin(boardId|workspaceId)`) implementado **sobre un puerto** de `membership` (no consulta JPA directo) |
| 2.4 | `@PreAuthorize` en cada controller; `GET` filtra por membresía |
| 2.5 | **Suite de aislamiento A vs B**: ningún endpoint de lectura/escritura accesible cruzando workspaces (test parametrizado sobre todos los endpoints) |
| 2.6 | Frontend `auth`: OIDC PKCE (`oidc-client-ts`), `AuthProvider`, token en `http-client`, refresh silencioso |
| 2.7 | UI según rol (VIEWER sin acciones de edición); manejo 401/403 |

**DoD:** la suite de aislamiento demuestra que A no lee ni modifica nada de B.

### Fase 3 — Tiempo real, una instancia

| # | Tarea |
| --- | --- |
| 3.1 | `platform/messaging`: config STOMP, `ChannelInterceptor` que valida JWT en `CONNECT` |
| 3.2 | Interceptor de `SUBSCRIBE`: `/topic/boards.{id}` exige `canView(boardId)` (test negativo obligatorio) |
| 3.3 | `boards`: secuencia por tablero (`event_seq`) y tabla `board_event` dentro de la transacción |
| 3.4 | `collaboration`: `@TransactionalEventListener(AFTER_COMMIT)` → `BoardEventBroadcaster` (adapter STOMP local); `BoardEnvelope` |
| 3.5 | Test: rollback ⇒ **no** se emite evento |
| 3.6 | Frontend `realtime`: `StompBoardEvents`, `clientId`, `use-board-sync`; `apply-*-event` en cards/lists/boards |
| 3.7 | Playwright con 2 contextos de navegador: mover en A ⇒ se ve en B |

**DoD:** test Playwright de dos navegadores en verde.

### Fase 4 — Conflictos y reconexión

| # | Tarea |
| --- | --- |
| 4.1 | `sequence-tracker` (dominio frontend) con tests: duplicado, salto, orden |
| 4.2 | Endpoint `GET /boards/{id}/events?since=` + política "demasiado atrasado ⇒ 410/recarga completa" |
| 4.3 | Reconexión con backoff exponencial + jitter; al reconectar: `since` o refetch |
| 4.4 | Movimientos: last-write-wins (posición = verdad). Edición título/desc: `409` + UI `ConflictBanner` con comparar/sobrescribir |
| 4.5 | Renovación/expiración de JWT en WS: decisión documentada en ADR (reconectar con token nuevo antes de expirar) |
| 4.6 | Playwright: cortar red (`context.setOffline`), cambiar desde otro cliente, reconectar ⇒ converge |

**DoD:** el estado converge tras corte de red.

### Fase 5 — Presencia

| # | Tarea |
| --- | --- |
| 5.1 | `presence`: puertos `PresenceStore` (out) + casos de uso `Heartbeat`, `LeaveBoard`, `StartEditingCard`, `StopEditingCard` |
| 5.2 | Adapter Redis: claves con TTL (~20 s) renovadas por heartbeat (~8 s); soft lock como clave `lock:card:{id}` con TTL |
| 5.3 | Eventos `presence.*` por el mismo canal del tablero (o topic hermano `/topic/boards.{id}.presence`) |
| 5.4 | Frontend `presence`: avatares, indicador "Ana está editando", sin bloquear |
| 5.5 | Test: cierre brusco ⇒ desaparece < 30 s |

### Fase 6 — Escalado horizontal

| # | Tarea |
| --- | --- |
| 6.1 | Nginx con upgrade WebSocket + 2 réplicas backend en Compose |
| 6.2 | Nuevo adapter `RedisPubSubBroadcaster` (implementa el mismo puerto `BoardEventBroadcaster`); cada instancia publica en Redis y reenvía a sus suscriptores locales |
| 6.3 | Evitar doble entrega (la instancia origen no reenvía dos veces) |
| 6.4 | ADR: Redis pub/sub vs RabbitMQ broker relay |
| 6.5 | Test: clientes en instancias distintas se ven; matar una instancia ⇒ reconexión transparente |

### Fase 7 — Rendimiento y producción

| # | Tarea |
| --- | --- |
| 7.1 | Virtualización con TanStack Virtual + dnd-kit (>5.000 tarjetas); estrategia de overlay para el ítem arrastrado |
| 7.2 | Paginación/carga por lista si el payload es grande |
| 7.3 | k6: 1.000 conexiones WS, latencia cambio→recepción (p50/p95/p99) |
| 7.4 | Micrometer (conexiones, eventos/s, latencia de fan-out), OpenTelemetry, dashboard Grafana en `infra/` |
| 7.5 | Informe final con datos: usuarios concurrentes soportados y cuello de botella |

---

## 8. Trampas (checklist de revisión de PR)

- [ ] ¿Se emite algún evento fuera de `AFTER_COMMIT`?
- [ ] ¿La suscripción WS valida permisos?
- [ ] ¿Algún movimiento reescribe más de una fila?
- [ ] ¿Algún cliente trata el WS como fuente de verdad?
- [ ] ¿Qué pasa si el JWT expira con el socket abierto?
- [ ] ¿`domain` importa algo de Spring/JPA/React? (los tests de arquitectura deben fallar si sí)
- [ ] ¿Una feature importa archivos internos de otra feature?
- [ ] ¿Un DTO (REST/WS) se filtró hacia `domain`?

## 9. Estrategia de trabajo con agentes

1. Antes de tocar código: leer `.claude/skills/kanban-project-context/SKILL.md` y la skill de la capa que se modifica.
2. Una tarea del plan = una rama/PR pequeña; no mezclar capas ni fases.
3. Orden dentro de una capacidad: **dominio → casos de uso (con fakes) → adapters → UI**.
4. Los tests de arquitectura nunca se desactivan para "hacer pasar" código: se corrige el diseño o se escribe un ADR.
5. Al cerrar una tarea: marcar la casilla del plan y actualizar la tabla de estado (sección 11).

## 10. Decisiones a documentar como ADR

| ADR | Tema |
| --- | --- |
| 0001 | Hexagonal + screaming (backend) y feature slices (frontend) |
| 0002 | Modelo de dominio separado de entidades JPA |
| 0003 | Fractional indexing (LexoRank-like) y rebalanceo |
| 0004 | Secuencia por tablero asignada en la transacción + `board_event` log |
| 0005 | Estrategia de conflictos (LWW para mover, 409 para editar) |
| 0006 | Renovación de JWT en WebSocket |
| 0007 | Redis pub/sub vs RabbitMQ relay |

## 11. Estado actual (actualizar al avanzar)

| Fase | Estado |
| --- | --- |
| 0 Base | ⬜ pendiente |
| 1 Dominio y REST | ⬜ |
| 2 Auth multi-tenant | ✅ backend (B2.1–B2.7) + frontend (F2.1–F2.8, ADR-0009): login OIDC, 401/403, UI por rol, gestión de miembros (`role` en workspace, `GET`/`DELETE` miembros) |
| 3 Tiempo real | ⬜ |
| 4 Conflictos/reconexión | 🟨 backend hecho (B4.1–B4.6, ADR-0005/0006); frontend pendiente |
| 5 Presencia | ⬜ |
| 6 Escalado | ⬜ |
| 7 Rendimiento | ⬜ |

## 12. Skills para agentes (ya creadas)

| Skill | Cuándo usarla |
| --- | --- |
| `.claude/skills/kanban-project-context/SKILL.md` | **Siempre primero.** Visión, reglas, mapa del repo, estado, trampas. |
| `.claude/skills/backend-hexagonal-screaming/SKILL.md` | Cualquier cambio en `backend/`. Cómo crear una capacidad/caso de uso/adapter. |
| `.claude/skills/frontend-hexagonal-features/SKILL.md` | Cualquier cambio en `frontend/`. Cómo crear una feature, puerto, adapter, hook. |

## 13. Primer paso inmediato

Ejecutar la **Fase 0** tareas 0.1 → 0.11 en ese orden. Sugerido con SDD: `/sdd-new fase-0-base` y luego `/sdd-ff`.
