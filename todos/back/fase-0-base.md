# Back — Fase 0: Base del proyecto

**DoD:** `docker compose up` levanta todo; test Testcontainers y ArchUnit en verde en CI.
Incluye las tareas transversales de repo/infra/CI/ADR (no tienen carpeta propia).

## Transversal (repo, infra, CI, docs)

- [x] **B0.1** Monorepo: carpetas `backend/ frontend/ infra/ docs/adr/ .github/workflows/`, `.gitignore`, `.editorconfig`, README raíz
- [x] **B0.2** `docs/adr/` con README y plantilla ✅ *(creado)* + `docs/contracts/` ✅ *(creado)*
- [x] **B0.3** `infra/docker-compose.yml`: Postgres, Redis, Keycloak, Nginx (placeholder)
  - [x] Realm importado `infra/keycloak/realm.json`: cliente `kanban-web` (público, PKCE) y `kanban-api`
  - [x] 3 usuarios de prueba (owner / editor / viewer)
  - [x] `infra/nginx/nginx.conf` placeholder
- [x] **B0.4** CI GitHub Actions: build + tests backend en cada PR (el job de frontend está en `front/fase-0`)
- [x] **B0.5** ADR-0001: hexagonal + screaming (backend) y feature slices (frontend)
- [x] **B0.6** ADR-0002: modelo de dominio ≠ entidad JPA

## Backend

- [x] **B0.7** Proyecto Spring Boot 3 / Java 21 (Gradle o Maven), paquete base `com.kanbanlive`, `KanbanLiveApplication` *(verificado: build, ArchUnit, unit e IT en verde con JDK 21; arranque real con compose OK)*
  - [x] Perfiles `local` y `test`
  - [x] Flyway con `V1__init.sql`
  - [x] Actuator (health)
- [x] **B0.8** `sharedkernel` completo, con tests unitarios (sin Spring):
  - [x] `domain/`: `AggregateRoot<ID>` (id, `version`, `registerEvent`/`pullEvents`), `DomainEvent`, `Entity<ID>`, `ValueObject`, `DomainException` + subtipos (NotFound, Conflict/StaleVersion, Forbidden, Validation)
  - [x] `application/`: `UseCase<I,O>`, markers `Command`/`Query`, puertos `Clock` e `IdGenerator`, `DomainEventPublisher`
  - [x] `Page` / `PageQuery` neutros
  - [x] `position/`: carpeta preparada para `FractionalIndex` (Fase 1)
- [x] **B0.9** Esqueleto de capacidades con `package-info.java`: `workspaces`, `membership`, `boards`, `lists`, `cards`, `collaboration`, `presence`, y `platform/{security,persistence,web,messaging}`
- [x] **B0.10** ArchUnit en verde:
  - [x] `HexagonalArchitectureTest` (reglas 4.2: domain ← application ← adapter; A→B solo vía `port.in`/eventos)
  - [x] `ScreamingPackagesTest` (prohibido `..controller..`, `..service..`, `..repository..` en primer nivel)
- [x] **B0.11** Test de integración Testcontainers (Postgres) que arranca contexto + Flyway *(`KanbanLiveApplicationIT` en verde; requiere Testcontainers 1.21.4 por Docker Engine 29)*
- [ ] **B0.12** Verificar DoD: `docker compose up` completo + CI verde en un PR de prueba
