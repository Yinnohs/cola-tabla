# Contrato de eventos de tablero (tiempo real)

Versión del contrato: `schemaVersion = 1`. Implementa B3.5 a B3.8 del backend; lo consume el frontend en F3.1.
La base de datos es la fuente de verdad: el WebSocket solo **notifica**. Ante cualquier duda, el cliente
vuelve a cargar `GET /api/boards/{boardId}`.

## Conexión

- Endpoint STOMP sobre WebSocket nativo: `/ws` (mismo origen vía nginx / proxy de Vite).
- El JWT va en la cabecera STOMP del frame `CONNECT`: `Authorization: Bearer <token>` (no en el upgrade HTTP).
  Sin token o con token inválido/expirado el servidor responde con un frame `ERROR` y cierra la conexión.
- Con `kanban.security.enabled=false` (solo desarrollo local) no hace falta token.
- Suscripción: `/topic/boards.{boardId}`. Exige ser miembro (cualquier rol, incluido `VIEWER`) del workspace del
  tablero. Si no, el servidor responde con un frame `ERROR` (`message: Access denied`) y **cierra la conexión**;
  es la misma respuesta para un tablero ajeno y para uno inexistente. El cliente no debe reintentar en bucle:
  mostrar "sin acceso".
- Cualquier otro destino `/topic/**` se rechaza igual. Los clientes solo pueden enviar a `/app/**`
  (un `SEND` a `/topic/...` se rechaza).
- El token solo se valida al conectar. Qué ocurre cuando expira con el socket abierto se decide en Fase 4
  (B4.5); el backend deja el punto de extensión `SocketTokenLifecycle` (`platform.messaging`).

## Envelope

Cada mensaje del topic es un JSON:

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `boardId` | uuid | Tablero al que pertenece el evento |
| `seq` | entero >= 1 | Contador por tablero, asignado dentro de la transacción (ADR-0008) |
| `type` | string | Tipo de evento (tabla siguiente) |
| `payload` | objeto | Datos del evento; forma según `type` |
| `clientId` | string o `null` | Valor de la cabecera `X-Client-Id` de la petición REST que originó el evento |
| `occurredAt` | ISO-8601 (UTC) | Momento en que se registró el evento |
| `schemaVersion` | entero | Versión del contrato (hoy `1`) |

```json
{
  "boardId": "6f1c1b0e-8a7d-4c43-9d57-2f0a3d1f0001",
  "seq": 5,
  "type": "card.moved",
  "payload": {
    "card": {
      "id": "0b6d8d1e-7d0f-4b0d-8a6e-6a4c5f3a0002",
      "listId": "a1b2c3d4-0000-4000-8000-000000000003",
      "title": "Preparar demo",
      "description": null,
      "position": "V",
      "version": 3,
      "createdAt": "2026-11-18T10:00:00Z"
    },
    "fromListId": "a1b2c3d4-0000-4000-8000-000000000004"
  },
  "clientId": "tab-7f3a",
  "occurredAt": "2026-11-18T10:05:00.123Z",
  "schemaVersion": 1
}
```

## Tipos de evento y payload

| `type` | Origen REST | `payload` |
| --- | --- | --- |
| `board.updated` | `PATCH /api/boards/{boardId}` | `{ "board": BoardSummary }` |
| `list.created` | `POST /api/boards/{boardId}/lists` | `{ "list": ListSummary }` |
| `list.updated` | `PATCH /api/lists/{listId}` (renombrar) | `{ "list": ListSummary }` |
| `list.moved` | `POST /api/lists/{listId}/move` | `{ "list": ListSummary }` |
| `card.created` | `POST /api/lists/{listId}/cards` | `{ "card": CardSummary }` |
| `card.updated` | `PATCH /api/cards/{cardId}` | `{ "card": CardSummary }` |
| `card.moved` | `POST /api/cards/{cardId}/move` (mismo tablero) | `{ "card": CardSummary, "fromListId": uuid }` |
| `card.deleted` | `DELETE /api/cards/{cardId}` | `{ "cardId": uuid, "listId": uuid }` |

- `list.deleted` no existe todavía (no hay endpoint de borrado de listas).
- Las formas `BoardSummary`, `ListSummary` y `CardSummary` son las mismas que devuelve la REST
  (`docs/contracts/openapi.json`): el payload lleva el estado resultante, incluida su `version`.
- Si una tarjeta se mueve a una lista de **otro tablero**, el tablero de origen recibe `card.deleted` y el de destino
  `card.created` (nunca `card.moved`).
- Crear tablero y crear/ver/borrar espacios de trabajo no generan eventos de tablero.
- El rebalanceo de posiciones (ADR-0003) reescribe las hermanas sin emitir evento propio todavía: el cliente puede
  ver posiciones que cambian en un `GET`; el orden relativo se mantiene.

## Orden y `seq`

- `seq` crece de uno en uno por tablero, sin huecos entre eventos confirmados, y respeta el orden de commit.
- Un evento solo se difunde **después del commit** de la transacción que lo produce: un rollback no emite nada ni
  consume `seq`.
- Todos los eventos quedan también en la tabla `board_event(board_id, seq, type, payload, created_at)`.
- `GET /api/boards/{boardId}` devuelve `lastSeq`: el `seq` del último evento incluido en la carga. Se lee de
  `board.event_seq` en la **misma instantánea** (transacción `REPEATABLE_READ` de solo lectura) que el tablero, las
  listas y las tarjetas, así que el estado contiene exactamente los eventos con `seq <= lastSeq`. Un tablero sin
  eventos devuelve `0`. El cliente reanuda desde `lastSeq + 1`.
- Algoritmo del cliente con `lastSeq` (el de la última carga completa del tablero o el `seq` del último aplicado):
  - `seq == lastSeq + 1`: aplicar y avanzar.
  - `seq <= lastSeq`: ignorar (duplicado o ya incluido en la carga).
  - `seq > lastSeq + 1`: hay un hueco, recargar el tablero completo (`GET /api/boards/{boardId}`) y reanudar.
  - Un `type` desconocido o un `schemaVersion` distinto de `1` no se puede aplicar: recargar el tablero.
- Si la difusión falla tras el commit, el cambio persiste y el hueco se detecta por `seq`.

## Resincronización por `since` (Fase 4, ADR-0005)

- `GET /api/boards/{boardId}/events?since={seq}` (permiso `canView`) devuelve
  `{ boardId, since, lastSeq, events: [...] }`; cada evento tiene la forma del envelope del WebSocket, salvo que
  `clientId` es siempre `null` (no se persiste). Los eventos van en orden de `seq`; el cliente continúa desde `lastSeq`.
- `since == lastSeq` ⇒ `events` vacío. `since` negativo o mayor que el `seq` actual ⇒ `400` (`code = INVALID_SINCE`).
- `410 Gone` (`code = EVENTS_GONE`) si faltan más de `kanban.events.max-gap` eventos (500) o el log fue purgado
  (retención `kanban.events.retention`, 24 h): recargar el tablero completo (`GET /api/boards/{boardId}`).
- Conflictos: editar con `version` obsoleta ⇒ `409` `STALE_VERSION` con la propiedad `current` (estado actual del
  recurso); mover es last-write-wins y nunca devuelve `409`.
- Token vencido: el servidor envía un frame STOMP `ERROR` (`Token expired`) y cierra el socket; el cliente renueva
  ~60 s antes y reconecta (ADR-0006).

## `clientId` y eco propio

- El cliente genera un identificador por instancia (pestaña/app), `[A-Za-z0-9._:-]{1,64}`, y lo envía en la
  cabecera opcional `X-Client-Id` de las peticiones REST de escritura de tableros, listas y tarjetas
  (declarada en `openapi.json`).
- El servidor lo devuelve tal cual en `clientId`; un valor ausente o inválido se descarta (`null`).
- El cliente que envió la petición ya aplicó el cambio de forma optimista: al recibir un evento con su propio
  `clientId` **no lo reaplica**, pero sí avanza `lastSeq`.
- El frontend lo genera con `crypto.randomUUID()` (normalizado al formato), en memoria por pestaña, y lo envía en las
  peticiones de escritura (no en los `GET`).
- `clientId` no es una credencial ni prueba de identidad.
