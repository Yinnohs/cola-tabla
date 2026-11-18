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

Requisitos: Docker, JDK 21, Node LTS (20+).

### Infraestructura (Postgres, Redis, Keycloak, Nginx)

```
docker compose -f infra/docker-compose.yml up -d
```

- Postgres: `localhost:5432` (db/user/password `kanban`)
- Redis: `localhost:6379`
- Keycloak: http://localhost:8180 (admin/admin; realm `kanban` importado)
- Nginx: http://localhost:8000 (proxy a backend/frontend; solo útil con el perfil `app`; el backend local usa 8080)

Usuarios de desarrollo del realm `kanban`: `owner`, `editor`, `viewer` (contraseña = nombre de usuario).

Para levantar también backend y frontend en contenedores: `docker compose -f infra/docker-compose.yml --profile app up -d --build`.

### Backend

```
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
./mvnw verify          # compila y ejecuta tests (unitarios + IT con Testcontainers; requiere Docker)
```

OpenAPI/Swagger UI (perfil `local`): http://localhost:8080/swagger-ui.html · spec en http://localhost:8080/v3/api-docs. Token para probar: ver [`manual/test/fase-1/README.md`](manual/test/fase-1/README.md) (cliente de desarrollo `kanban-dev-cli`).

### Frontend

```
cd frontend
npm ci
npm run dev            # http://localhost:5173
npm run lint && npm run typecheck && npm test
```

## Documentación

- Plan: [`initial-plan.md`](initial-plan.md)
- Tareas: [`todos/`](todos/README.md) ([back](todos/back/), [front](todos/front/))
- Decisiones: [`docs/adr/`](docs/adr/README.md)
- Contratos: [`docs/contracts/`](docs/contracts/README.md)
- Pruebas manuales: [`manual/test/fase-1/`](manual/test/fase-1/README.md)
