# Diagrama de infraestructura y comunicación de Kanban Live

Documento generado a partir de lo que **existe hoy** en el repo (`infra/docker-compose.yml`, `infra/nginx/nginx.conf`, `infra/keycloak/realm.json`, `backend/src/main/resources/application*.yml`, `SecurityConfig`, `Perms`, `frontend/vite.config.ts`, cliente HTTP del frontend, Dockerfiles y `initial-plan.md`).

Leyenda: las líneas continuas y los nodos normales están **implementados**; las líneas discontinuas y lo marcado *(planificado)* **no existen aún** en el código.

## 1. Vista general de componentes

Dos modos de acceso: **desarrollo** (Vite en 5173 con proxy `/api` al backend en 8080, infra en Docker) y **stack completo** (`--profile app`, todo detrás de nginx en el puerto 8000 del host).

```mermaid
flowchart LR
    Browser["Navegador<br/>(SPA React)"]

    subgraph DEV["Modo desarrollo (host)"]
        Vite["Vite dev server<br/>:5173<br/>proxy solo /api"]
        BEdev["Backend Spring Boot<br/>:8080 (perfil local)"]
    end

    subgraph DOCKER["Docker Compose - proyecto kanban-live"]
        Nginx["nginx 1.27<br/>host :8000 a :80<br/>/api, /ws, /"]
        FE["frontend (profile app)<br/>nginx estatico :80<br/>sin puerto publicado"]
        BE["backend (profile app)<br/>Spring Boot :8080<br/>sin puerto publicado"]
        PG[("PostgreSQL 16<br/>:5432")]
        RD[("Redis 7<br/>:6379")]
        KC["Keycloak 26.0<br/>:8180 start-dev<br/>realm kanban"]
    end

    Browser -->|"HTTP :5173"| Vite
    Vite -->|"proxy /api (HTTP)"| BEdev
    Browser -->|"HTTP :8000"| Nginx
    Nginx -->|"/ hacia frontend:80"| FE
    Nginx -->|"/api/ hacia backend:8080"| BE
    Nginx -.->|"/ws upgrade WebSocket (planificado: no hay endpoint STOMP)"| BE

    BEdev -->|"JDBC :5432 (localhost)"| PG
    BE -->|"JDBC postgres:5432"| PG
    BEdev -->|"Lettuce :6379 (sin uso aun)"| RD
    BE -->|"Lettuce redis:6379 (sin uso aun)"| RD
    BE -.->|"JWK set HTTP :8180 (solo con seguridad activa)"| KC
    BEdev -.->|"JWK set HTTP :8180 (solo con seguridad activa)"| KC
    Browser -.->|"OIDC code + PKCE (planificado, Fase 2 front)"| KC
    Browser -.->|"WebSocket STOMP /ws (planificado, Fase 3)"| Nginx
    BE -.->|"Redis pub/sub entre instancias (planificado, Fase 6)"| RD
```

Notas:
- `redis` está en compose y `spring-boot-starter-data-redis` / `spring-boot-starter-websocket` están en el `pom.xml`, pero `platform/messaging`, `collaboration/adapter/out/redis` y `presence/adapter/out/redis` solo contienen `package-info.java`: **no hay código de STOMP ni de Redis**.
- Vite **no** proxea `/ws` (solo `/api`).
- `postgres`, `redis`, `keycloak` y `nginx` arrancan siempre; `backend` y `frontend` solo con `docker compose --profile app up`.

## 2. Flujo de petición REST

Ejemplo con una operación sobre una tarjeta (`@PreAuthorize("@perms.canEdit(#cardId)")` en `CardController`), con seguridad activa (`kanban.security.enabled=true`, valor por defecto).

```mermaid
sequenceDiagram
    autonumber
    actor U as Navegador (SPA)
    participant N as nginx :8000 o Vite :5173
    participant F as Spring Security (BearerTokenAuthenticationFilter)
    participant KC as Keycloak :8180
    participant C as CardController
    participant P as bean perms (Perms)
    participant M as CheckAccessUseCase (membership)
    participant DB as PostgreSQL

    U->>N: HTTP /api/... con Authorization Bearer JWT
    Note over U: openapi-fetch con middleware que inyecta el token (getToken)
    N->>F: proxy_pass /api/ a backend:8080 (o Vite a localhost:8080)
    F->>F: Decodifica JWT y valida firma, exp y claim iss
    opt JWK no cacheado
        F->>KC: GET /realms/kanban/protocol/openid-connect/certs
        KC-->>F: JWK set (clave publica RS256)
    end
    F->>C: Authentication = Jwt (sub = id de usuario)
    C->>P: "@PreAuthorize: @perms.canEdit(cardId)"
    P->>M: execute(currentUser.id, id, EDIT)
    M->>DB: Resuelve card, list, board, workspace y busca membresia
    DB-->>M: rol OWNER, EDITOR, VIEWER o ninguno
    M-->>P: true o false
    alt permitido
        P-->>C: true
        C->>DB: Caso de uso @Transactional (JPA, esquema Flyway)
        DB-->>C: OK
        C-->>U: 200, 201 o 204 JSON
    else denegado o sin token
        F-->>U: 401 si no hay JWT valido
        P-->>U: 403 AccessDenied (GlobalExceptionHandler a ProblemDetail)
    end
```

Rutas públicas incluso con seguridad activa: `/actuator/health/**`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**` (Swagger solo existe con springdoc habilitado: perfiles `local` y `test`). Todo lo demás exige JWT. CSRF está deshabilitado.

Modo local (`kanban.security.enabled=false`): se usa la cadena `openSecurityFilterChain` (`anyRequest().permitAll()`), `Perms` devuelve siempre `true` y `SecurityContextCurrentUser` devuelve `kanban.security.dev-user-id` (por defecto `dev-user`). No hay llamada a Keycloak ni comprobación de membresía.

## 3. Flujo de autenticación

```mermaid
flowchart TD
    subgraph Hoy["Implementado hoy"]
        Dev["Desarrollador"] -->|"POST /realms/kanban/protocol/openid-connect/token<br/>client kanban-dev-cli, password grant"| KCt["Keycloak :8180<br/>realm kanban"]
        KCt -->|"access_token RS256, 3600 s<br/>iss = http://localhost:8180/realms/kanban"| Dev
        Dev -->|"pega en frontend/.env.local<br/>VITE_DEV_TOKEN"| Env["devToken() en create-ports.ts<br/>solo si import.meta.env.DEV"]
        Env -->|"Authorization: Bearer"| API["Backend /api"]
    end

    subgraph Back["Backend: decision segun propiedad"]
        API --> Q{"kanban.security.enabled"}
        Q -->|"true (por defecto y prod)"| RS["oauth2ResourceServer jwt<br/>issuer-uri = KEYCLOAK_ISSUER_URI<br/>jwk-set-uri = KEYCLOAK_JWK_SET_URI"]
        RS -->|"valida firma con JWK y claim iss"| OK["SecurityContext = Jwt<br/>currentUser.id = claim sub"]
        Q -->|"false (perfil local)"| Open["permitAll y Perms siempre true<br/>currentUser = dev-user<br/>el token se ignora"]
    end

    subgraph Plan["Planificado (Fase 2 front)"]
        SPA["SPA kanban-web (public client)"] -.->|"Authorization Code + PKCE S256<br/>redirect http://localhost:5173/*"| KCl["Keycloak login"]
        KCl -.->|"code a tokens"| SPA
        SPA -.->|"sustituye devToken por el token OIDC"| API
        SPA -.->|"token en CONNECT y SUBSCRIBE STOMP (Fase 3)"| WS["WebSocket"]
    end
```

Detalles del realm `kanban` (`infra/keycloak/realm.json`, importado con `--import-realm`, `sslRequired: none`):
- Clientes: `kanban-web` (público, standard flow + PKCE S256, redirect y webOrigins `http://localhost:5173`), `kanban-dev-cli` (público, **solo** password grant para curl/pruebas manuales), `kanban-api` (bearer-only, resource server).
- Usuarios de desarrollo: `owner/owner`, `editor/editor`, `viewer/viewer` con roles de realm `owner|editor|viewer`. Estos roles **no** autorizan nada: la autorización real es la membresía por workspace en BD.
- El `iss` del token debe coincidir exactamente con `issuer-uri`; por eso importa el host usado para pedir el token (`localhost:8180` vs `keycloak:8180`).

## 4. Flujo en tiempo real / eventos

**Estado: planificado (Fase 3 una instancia, Fase 6 escalado con Redis). No hay código.** Solo existen las dependencias del `pom.xml`, los `package-info.java` vacíos, el contenedor Redis y la ruta `/ws` en nginx.

```mermaid
sequenceDiagram
    autonumber
    actor A as Navegador A
    actor B as Navegador B
    participant NG as nginx /ws (existe)
    participant BE as Backend (planificado)
    participant DB as PostgreSQL
    participant RD as Redis (Fase 6)

    Note over A,RD: TODO es PLANIFICADO salvo la ruta nginx /ws, el contenedor Redis y el REST
    A->>BE: REST mover tarjeta (existe) con clientId
    BE->>DB: TX guarda tarjeta, boards.event_seq++, inserta board_event
    DB-->>BE: COMMIT
    BE-->>A: 200 OK (respuesta REST existente)
    BE->>BE: "@TransactionalEventListener AFTER_COMMIT a BoardEventBroadcaster (planificado)"
    alt Fase 3 una instancia
        BE-->>NG: STOMP /topic/boards.{id} (LocalStompBroadcaster)
    else Fase 6 varias instancias
        BE->>RD: PUBLISH canal del board (RedisPubSubBroadcaster)
        RD-->>BE: cada instancia recibe y reenvia a su STOMP local
    end
    NG-->>B: WebSocket upgrade y frame STOMP con el evento
    NG-->>A: mismo evento (se ignora por clientId propio)
    B->>B: sequence-tracker APPLY, IGNORE o RESYNC y cache TanStack Query
    B->>BE: en RESYNC consulta REST (la BD es la verdad)
```

Reglas del plan que aplican: permisos también en `CONNECT`/`SUBSCRIBE`, eventos solo tras commit, el WS solo notifica. Presencia (`presence/adapter/out/redis`) y colaboración (`collaboration/adapter/out/redis`) también son paquetes vacíos.

## 5. Red Docker, dependencias de arranque, volúmenes y variables

```mermaid
flowchart TB
    subgraph Host["Host (Windows)"]
        P8000["localhost:8000"]
        P5432["localhost:5432"]
        P6379["localhost:6379"]
        P8180["localhost:8180"]
        P5173["localhost:5173 (Vite, fuera de Docker)"]
        P8080["localhost:8080 (backend con mvnw, fuera de Docker)"]
    end

    subgraph NET["Red por defecto kanban-live_default (DNS interno 127.0.0.11)"]
        nginx["nginx"]
        backend["backend [profile app]"]
        frontend["frontend [profile app]"]
        postgres[("postgres")]
        redis[("redis")]
        keycloak["keycloak"]
    end

    subgraph VOL["Volumenes y binds"]
        pgdata[("pg_data")]
        kcdata[("keycloak_data")]
        realm["bind ro: infra/keycloak/realm.json"]
        ngconf["bind ro: infra/nginx/nginx.conf"]
    end

    P8000 --> nginx
    P5432 --> postgres
    P6379 --> redis
    P8180 --> keycloak
    P8080 -.-> postgres
    P8080 -.-> redis

    nginx -->|"resolver en cada peticion, arranca sin backend ni frontend"| backend
    nginx --> frontend
    backend --> postgres
    backend --> redis
    backend -.-> keycloak

    postgres --- pgdata
    keycloak --- kcdata
    realm --> keycloak
    ngconf --> nginx

    backend ==>|"depends_on service_healthy"| postgres
    backend ==>|"depends_on service_healthy"| redis
    backend ==>|"depends_on service_started"| keycloak
```

Orden de arranque con `--profile app`: `postgres` y `redis` (healthcheck cada 5 s, 10 reintentos) y `keycloak` (solo `started`, sin healthcheck propio aunque `KC_HEALTH_ENABLED=true`) y luego `backend`. `frontend` y `nginx` no tienen `depends_on`. El backend ejecuta Flyway al arrancar (`ddl-auto: validate`).

Imágenes: `backend/Dockerfile` (build `eclipse-temurin:21-jdk`, runtime `21-jre`, `EXPOSE 8080`); `frontend/Dockerfile` (build `node:22-alpine`, runtime `nginx:1.27-alpine` con fallback SPA `try_files $uri /index.html`, `EXPOSE 80`).

### Tabla de puertos

| Servicio | Puerto host | Puerto contenedor | Protocolo | Perfil compose |
|---|---|---|---|---|
| nginx (proxy inverso) | 8000 | 80 | HTTP (upgrade WS en `/ws`) | siempre |
| PostgreSQL 16 | 5432 | 5432 | TCP (JDBC) | siempre |
| Redis 7 | 6379 | 6379 | TCP (RESP) | siempre |
| Keycloak 26.0 | 8180 | 8180 (`KC_HTTP_PORT`) | HTTP | siempre |
| backend | no publicado | 8080 | HTTP | `app` |
| frontend (nginx estático) | no publicado | 80 | HTTP | `app` |
| Vite dev server | 5173 | n/a | HTTP | fuera de Docker |
| backend con `mvnw spring-boot:run` | 8080 | n/a | HTTP | fuera de Docker |

### Tabla de variables y propiedades por perfil

| Variable / propiedad | Dónde | Valor | Aplica a |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | compose backend | `local` | stack `app` (usa perfil local, ver inconsistencias) |
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | compose backend | `jdbc:postgresql://postgres:5432/kanban`, `kanban`, `kanban` | stack `app` (sobreescribe `application-local.yml`) |
| `spring.datasource.*` | `application-local.yml` | `jdbc:postgresql://localhost:5432/kanban`, `kanban/kanban` | local |
| datasource | Testcontainers `@ServiceConnection` (`PostgresIT`) | dinámico | test |
| `SPRING_DATA_REDIS_HOST`, `spring.data.redis.host/port` | compose / `application-local.yml` / `application-test.yml` | `redis` / `localhost:6379` / `localhost:6379` | app, local, test |
| `management.health.redis.enabled` | `application-test.yml` | `false` | test |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | compose backend | `http://keycloak:8180/realms/kanban` | stack `app` |
| `KEYCLOAK_ISSUER_URI` | `application.yml` | por defecto `http://localhost:8180/realms/kanban` | base (test, prod; override por entorno) |
| `KEYCLOAK_JWK_SET_URI` | `application.yml` | por defecto `http://localhost:8180/realms/kanban/protocol/openid-connect/certs` | base (test, prod) |
| `kanban.security.enabled` | ausente en `application.yml` (= `true`); `false` en `application-local.yml` | boolean | `false` solo en local; `true` en prod y por defecto |
| `kanban.security.dev-user-id` | `SecurityContextCurrentUser` | por defecto `dev-user` | solo con seguridad desactivada |
| `springdoc.api-docs/swagger-ui.enabled` | `application.yml` `false`; `local` y `test` `true` | boolean | habilitado en local y test |
| `management.endpoints.web.exposure.include` | `application.yml` | `health,info` | todos |
| `VITE_API_BASE_URL` | `frontend/.env.example` | vacío = mismo origen | dev y build (en prod, nginx `/api`) |
| `VITE_DEV_TOKEN` | `frontend/.env.local` (ignorado por git) | JWT de `kanban-dev-cli` | solo `import.meta.env.DEV`; nunca en `vite build` |
| `POSTGRES_DB/USER/PASSWORD` | compose | `kanban/kanban/kanban` | contenedor postgres |
| `KC_BOOTSTRAP_ADMIN_USERNAME/PASSWORD`, `KC_HTTP_PORT`, `KC_HEALTH_ENABLED` | compose | `admin/admin`, `8180`, `true` | contenedor keycloak |

Perfil **prod**: no existe `application-prod.yml`; el comportamiento "prod" es el `application.yml` base (seguridad activa, springdoc apagado) con `KEYCLOAK_*` y datasource inyectados por entorno.

## 6. Inconsistencias detectadas en la infraestructura

1. **Stack `app` con seguridad desactivada**: compose fija `SPRING_PROFILES_ACTIVE=local`, que pone `kanban.security.enabled=false`. Aunque se define el issuer de Keycloak, el backend en Docker acepta todo sin JWT.
2. **Issuer/JWK mezclados en compose**: se sobreescribe el issuer con `http://keycloak:8180/...`, pero no `jwk-set-uri`, que sigue en `localhost:8180` (dentro del contenedor no es Keycloak). Además, un token pedido en `localhost:8180` lleva `iss=http://localhost:8180/...` y no coincidiría con `keycloak:8180`. Si se activa la seguridad en Docker fallaría la validación (hace falta `KC_HOSTNAME` o unificar issuer).
3. **Nombres de variable distintos**: `application.yml` usa `KEYCLOAK_ISSUER_URI`/`KEYCLOAK_JWK_SET_URI`; compose usa el binding relajado `SPRING_SECURITY_OAUTH2_...`. Funcionan ambos, pero conviene unificar.
4. **Redirect URIs de Keycloak**: `kanban-web` solo permite `http://localhost:5173/*`; con el stack completo la SPA se sirve en `http://localhost:8000`, que no está en `redirectUris`/`webOrigins`.
5. **`/ws` sin destino**: nginx proxea `/ws` y la dependencia `starter-websocket` está en el `pom.xml`, pero no hay endpoint STOMP; el proxy de Vite tampoco cubre `/ws`.
6. **Redis sin uso**: contenedor, dependencia y `depends_on` existen, pero no hay código que lo use (el backend en `app` espera `redis` healthy sin necesitarlo).
7. **`depends_on` de Keycloak**: solo `service_started` pese a `KC_HEALTH_ENABLED`; no hay healthcheck (hoy irrelevante por el JWK perezoso).
8. **Keycloak en `start-dev`** con BD embebida en el volumen `keycloak_data` y credenciales `admin/admin`: solo desarrollo.
9. **nginx siempre arranca**: sin el perfil `app` devuelve 502 para `/` y `/api`.
10. **CORS**: `platform/web/package-info.java` lo menciona pero no hay configuración; funciona por mismo origen (proxy Vite / nginx) y fallaría si la SPA llamara a otro origen directamente.
11. **Puertos del front**: el realm asume la SPA en 5173 (Vite), mientras el stack Docker expone 8000; no hay entorno documentado que alinee ambos.
