# Tablero colaborativo en tiempo real: plan de implementación

Nov 18, 2026 · @jose ignacio

## Por qué un tablero tipo Trello

El proyecto es un tablero tipo Trello en tiempo real, no un editor de notas compartido. Un editor de texto colaborativo exige CRDTs u OT (Yjs, Automerge), que son un tema muy profundo en sí mismo. El tablero pone a prueba todo lo demás (tiempo real, conflictos, escalado y seguridad) y se puede terminar en un tiempo razonable.

## Stack recomendado

| Capa | Tecnologías |
| --- | --- |
| Backend | Java 21+, Spring Boot, Spring WebSocket (STOMP), Spring Security como OAuth2 Resource Server, Spring Data JPA, PostgreSQL, Flyway, Redis |
| Frontend | React con TypeScript, Vite, TanStack Query, `@stomp/stompjs`, dnd-kit (soporte de teclado nativo), TanStack Virtual |
| Infraestructura | Docker Compose con Postgres, Redis, Keycloak y Nginx |
| Testing | JUnit 5, Testcontainers, React Testing Library, Playwright (dos navegadores a la vez), k6 |

## Estructura del repo

```
kanban-live/
├── backend/          # Spring Boot (paquetes por módulo: workspace, board, realtime, presence)
├── frontend/         # React + Vite
├── infra/            # docker-compose, realm de Keycloak, nginx.conf
├── docs/adr/         # decisiones de arquitectura
└── .github/workflows/
```

## Plan por fases

Ocho fases, cada una con un criterio claro para darla por terminada.

### Fase 0: Base del proyecto

- Docker Compose con Postgres, Redis y Keycloak, y un pipeline de CI que compile y pase tests en cada PR.
- **Terminado cuando:** `docker compose up` levanta todo y hay un test de integración con Testcontainers en verde.

### Fase 1: Dominio y REST (sin tiempo real)

- Entidades: `Workspace → Board → List → Card`.
- **Ordenamiento con fractional indexing:** cada tarjeta lleva una posición tipo string (estilo LexoRank). Al mover una tarjeta solo se actualiza una fila, no toda la lista. Esta decisión es clave para lo que viene después.
- `@Version` en las entidades para tener locks optimistas.
- Frontend con TanStack Query, optimistic updates y drag & drop con dnd-kit, que también funcione con el teclado.
- **Terminado cuando:** puedes crear y mover tarjetas, y el drag & drop funciona sin mouse.

### Fase 2: Autenticación y multi-tenant

Conviene hacerla ahora: añadir seguridad al final, con WebSockets ya montados, es mucho más doloroso.

- Keycloak como proveedor OIDC y el backend como Resource Server que valida JWT.
- **Roles por workspace** (`OWNER`, `EDITOR`, `VIEWER`) guardados en tu propia base de datos, no en Keycloak, y verificados con `@PreAuthorize("@perms.canEdit(#boardId)")`.
- **Terminado cuando:** hay tests que demuestran que un usuario del workspace A no puede leer ni modificar nada del workspace B.

### Fase 3: Tiempo real en una sola instancia

- STOMP sobre WebSocket con un topic por tablero: `/topic/boards.{id}`.
- **Seguridad del canal:** enviar el JWT en el frame `CONNECT` y validarlo en un `ChannelInterceptor`. Al recibir un `SUBSCRIBE`, comprobar que el usuario tiene acceso a ese tablero (es un agujero de seguridad muy común).
- Publicar los eventos con `@TransactionalEventListener(phase = AFTER_COMMIT)`, para no emitir nunca cambios que luego hagan rollback.
- En el cliente, aplicar cada evento al caché de TanStack Query e ignorar los propios con un `clientId`.
- **Terminado cuando:** un test de Playwright con dos navegadores ve en el segundo lo que se mueve en el primero.

### Fase 4: Conflictos y reconexión

- Un número de secuencia por tablero en cada evento. Si el cliente detecta un salto (por ejemplo, del 41 al 43), resincroniza.
- **Reconexión** con backoff exponencial. Al volver, pide los eventos desde su última secuencia, o recarga el tablero si son demasiados.
- **Estrategia de conflictos:**
  - Movimientos de tarjetas: gana el último que escribe, con la posición de la tarjeta como fuente de verdad.
  - Edición de título o descripción: si `@Version` no coincide, el backend responde 409 y la UI muestra "alguien cambió esto" con opción de comparar.
- **Terminado cuando:** puedes cortar la red con las DevTools, hacer cambios desde otro cliente, reconectar y el estado converge.

### Fase 5: Presencia

- Quién está viendo el tablero y "Ana está editando esta tarjeta".
- Redis con claves con TTL renovadas por heartbeat. Si alguien cierra la pestaña de golpe, desaparece solo al vencer el TTL.
- Un "lock blando" en la tarjeta: avisa que alguien la está editando, pero no bloquea.
- **Terminado cuando:** cerrar el navegador a la fuerza elimina al usuario de la presencia en menos de unos 30 segundos.

### Fase 6: Escalado horizontal

- Dos o más instancias del backend detrás de Nginx (con el upgrade a WebSocket configurado).
- **El problema:** un usuario conectado a la instancia A no recibe los eventos que se generan en la instancia B.
- **La solución:** Redis pub/sub como bus entre instancias, donde cada una reenvía los eventos a sus clientes locales. Documenta en un ADR por qué elegiste esto y no un broker relay con RabbitMQ.
- **Terminado cuando:** un test con los clientes en instancias distintas pasa, y apagar una instancia solo provoca una reconexión transparente.

### Fase 7: Rendimiento y producción

- Un tablero con más de 5.000 tarjetas, usando virtualización. Ojo: combinar virtualización con drag & drop es complicado, y es un buen reto en sí mismo.
- Tests de carga con k6: por ejemplo, 1.000 conexiones WebSocket, midiendo la latencia desde el cambio hasta que llega a los demás clientes.
- Métricas con Micrometer (conexiones activas, eventos por segundo, latencia de fan-out), tracing con OpenTelemetry y un dashboard en Grafana.
- **Terminado cuando:** puedes decir con datos cuántos usuarios concurrentes aguanta y dónde está el cuello de botella.

## Trampas comunes

- Emitir eventos antes del commit, que produce estados fantasma en otros clientes.
- Validar permisos en REST pero olvidarlos en las suscripciones WebSocket.
- Reordenar toda la lista en cada movimiento, que genera muchas escrituras y conflictos.
- Tratar el WebSocket como fuente de verdad. La fuente de verdad es la base de datos y el WebSocket solo notifica.
- JWT que expiran con la conexión WebSocket abierta: decide cómo renovarlos o cerrar la conexión.

## Siguiente paso

Generar el esqueleto de la Fase 0: docker-compose con Postgres, Redis y Keycloak (con un realm ya configurado), y los proyectos de Spring Boot y Vite listos para arrancar. A partir de ahí, cada fase se implementa y se revisa por separado.
