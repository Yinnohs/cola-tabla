# Kanban Live (cola-tabla)

Tablero tipo Trello en tiempo real (`Workspace -> Board -> List -> Card`) con autenticación multi-tenant (Keycloak), presencia, resincronización tras cortes de red y escalado horizontal. No es un editor colaborativo de texto (sin CRDT/OT).

Principios: la base de datos es la fuente de verdad; el WebSocket solo notifica; nunca se emite antes del commit; permisos en REST y WebSocket; una escritura por movimiento (fractional indexing); dependencias hacia adentro (hexagonal) verificadas en CI.

## Estructura

```
backend/            Spring Boot 3 / Java 21 (hexagonal + screaming architecture)
frontend/           React + TypeScript + Vite (hexagonal por features)
infra/              docker-compose.yml, keycloak/realm.json, nginx/nginx.conf
docs/adr/           Architecture Decision Records
docs/contracts/     Contratos compartidos backend/frontend
todos/              Tareas por fase (back/ y front/)
.github/workflows/  CI
```

## Cómo levantar

Requisitos: Docker (con Compose v2), JDK 21 (`JAVA_HOME` apuntando a él) y Node LTS (20+). Puertos libres: 5432, 6379, 8080, 8180, 5173 (y 8000 con el perfil `app`).

### Inicio rápido (desarrollo local, 3 terminales)

La SPA exige login (OIDC), así que el backend **debe** correr con seguridad activada.

```
# 1. Infra: Postgres, Redis, Keycloak (esperar a que http://localhost:8180/realms/kanban responda)
docker compose -f infra/docker-compose.yml up -d postgres redis keycloak

# 2. Backend (:8080) con JWT real
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--kanban.security.enabled=true

# 3. Frontend (:5173; Vite proxea /api y /ws a :8080)
cd frontend
npm ci
cp .env.example .env.local   # opcional: los valores por defecto ya apuntan al Keycloak de desarrollo
npm run dev
```

Abre http://localhost:5173 e inicia sesión con `owner`/`owner`, `editor`/`editor` o `viewer`/`viewer`. El rol dentro de cada workspace sale de la membresía (no de Keycloak): el owner crea el workspace y añade miembros. Para ver el tiempo real, abre el mismo tablero con dos usuarios en navegadores distintos (o una ventana privada).

### Stack completo en contenedores

```
docker compose -f infra/docker-compose.yml --profile app up -d --build
```

Sirve todo en http://localhost:8000 (Nginx -> frontend + `/api` + `/ws`), con JWT validado contra Keycloak.

Servicios de `infra/docker-compose.yml`:

- Postgres: `localhost:5432` (db/user/password `kanban`)
- Redis: `localhost:6379`
- Keycloak: http://localhost:8180 (admin/admin; realm `kanban` importado)
- Nginx: http://localhost:8000 (**solo con el perfil `app`**)

Usuarios de desarrollo del realm `kanban`: `owner`, `editor`, `viewer` (contraseña = nombre de usuario). Clientes: `kanban-web` (SPA, Authorization Code + PKCE, redirects `:5173` y `:8000`), `kanban-dev-cli` (password grant, solo para pruebas) y `kanban-api`.

Parar: `docker compose -f infra/docker-compose.yml --profile app down` (añade `-v` para borrar los datos).

### Backend

```
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # sin seguridad (ver abajo)
./mvnw verify          # compila y ejecuta tests (unitarios + IT con Testcontainers; requiere Docker)
```

Las migraciones Flyway se aplican solas al arrancar.

OpenAPI/Swagger UI (perfiles `local` y `docker`): http://localhost:8080/swagger-ui.html · spec en http://localhost:8080/v3/api-docs.

Seguridad por perfil:

- `local` (por defecto, `kanban.security.enabled: false`): sin seguridad, todo responde sin token. Útil para curl/Swagger, **no** para usar la SPA. Actívala con `--kanban.security.enabled=true`.
- `docker` (backend en contenedor, `--profile app`): **JWT validado** contra Keycloak; `KANBAN_SECURITY_ENABLED=false` la desactiva.

Token para probar con la seguridad activada (cliente `kanban-dev-cli`):

```
TOKEN=$(curl -s -X POST http://localhost:8180/realms/kanban/protocol/openid-connect/token   -d grant_type=password -d client_id=kanban-dev-cli -d username=owner -d password=owner   | sed -E 's/.*"access_token":"([^"]+)".*//')
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/workspaces
```

Si cambia `infra/keycloak/realm.json`, Keycloak no lo reimporta mientras exista su volumen:
`docker compose -f infra/docker-compose.yml rm -sf keycloak && docker volume rm kanban-live_keycloak_data && docker compose -f infra/docker-compose.yml up -d keycloak`.
Más detalle en [`manual/test/fase-1/README.md`](manual/test/fase-1/README.md).

### Frontend

```
cd frontend
npm ci
npm run dev            # http://localhost:5173
npm run lint && npm run typecheck && npm test
npm run build          # tsc + build de producción
npm run depcruise      # reglas de dependencias hexagonales
```

Variables (`frontend/.env.example`, copiar a `.env.local`): `VITE_API_BASE_URL`, `VITE_WS_URL`, `VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID`, `VITE_OIDC_REDIRECT_URI`, `VITE_OIDC_POST_LOGOUT_REDIRECT_URI`. Todas son opcionales en desarrollo.

### Tests E2E (tiempo real)

Con infra + backend (seguridad activada) + `npm run dev` ya levantados:

```
cd frontend
npm run e2e:install    # solo la primera vez (Chromium)
npm run e2e
```

Detalle en [`frontend/e2e/README.md`](frontend/e2e/README.md).

### Problemas frecuentes

- **401 en todas las llamadas desde la SPA**: el backend está sin seguridad o sin Keycloak listo; arranca con `--kanban.security.enabled=true` tras esperar a Keycloak.
- **Login falla con `not_allowed` (offline tokens)**: volumen de Keycloak con un realm antiguo; recréalo (ver arriba).
- **`mvnw verify` falla al conectar a Docker**: los IT usan Testcontainers, Docker debe estar corriendo.
- **Puerto ocupado**: libera 5432/6379/8080/8180/5173 o detén otros Postgres/Redis locales.

## Documentación

- Plan: [`initial-plan.md`](initial-plan.md)
- Tareas: [`todos/`](todos/README.md) ([back](todos/back/), [front](todos/front/))
- Decisiones: [`docs/adr/`](docs/adr/README.md)
- Contratos: [`docs/contracts/`](docs/contracts/README.md)
- Pruebas manuales: [`manual/test/fase-1/`](manual/test/fase-1/README.md)
