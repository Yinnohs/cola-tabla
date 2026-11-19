# E2E (Playwright) — tiempo real entre dos navegadores

DoD de la Fase 3 (F3.8): dos contextos de navegador (`owner` y `editor`) sobre el mismo tablero; lo que se hace en
uno se ve en el otro sin recargar. **No** forma parte de `npm test` (vitest solo busca en `src/` y `tests/`) ni de
dependency-cruiser; el stack se levanta a mano (no hay `webServer`).

## Requisitos

- Docker (postgres, redis, keycloak), JDK 21 y Node.
- Navegador: `npm run e2e:install` (descarga Chromium la primera vez).

## Pasos

1. **Infra** (desde `infra/`): `docker compose up -d postgres redis keycloak` y esperar a que Keycloak responda en
   `http://localhost:8180/realms/kanban`.
   - Si Keycloak ya tenía un volumen de una versión anterior del realm, el login falla con `not_allowed`
     ("Offline tokens not allowed") porque a los usuarios les falta el rol `offline_access` (ver ADR-0009). Recrea el
     volumen `keycloak_data` o arranca otro proyecto de compose (`docker compose -p kanban-live-e2e up -d keycloak`).
2. **Backend con seguridad activada** (desde `backend/`, `JAVA_HOME` = JDK 21):
   `./mvnw spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--kanban.security.enabled=true`
   (el perfil `local` por sí solo desactiva la seguridad; aquí hace falta JWT real). Escucha en `:8080`.
3. **Frontend** (desde `frontend/`): `npm run dev` (Vite en `:5173`, con proxy `/api` y `/ws` hacia `:8080`).
4. **Tests** (desde `frontend/`): `npm run e2e`.

## Qué hace

- `support/seed.ts`: con el cliente `kanban-dev-cli` (password grant) el owner crea workspace, tablero, dos listas y
  dos tarjetas, y añade al editor como miembro `EDITOR` (`POST /api/workspaces/{id}/members` con el `sub` del JWT del
  editor). El rol real sale de la membresía, no de Keycloak.
- `support/login.ts`: login real por el formulario de Keycloak en cada contexto (oidc-client-ts guarda la sesión en
  `sessionStorage`, así que **no** se usa `storageState`).
- `realtime.spec.ts`: mover una tarjeta con el teclado (dnd-kit) en A se ve en B; crear una tarjeta en B se ve en A.

## Variables opcionales

`E2E_BASE_URL` (por defecto `http://localhost:5173`), `E2E_API_URL` (`http://localhost:8080`),
`E2E_KEYCLOAK_URL` (`http://localhost:8180/realms/kanban`).
