# ADR-0006: Expiración y renovación del JWT en WebSocket

- **Estado:** Aceptado
- **Fecha:** 2026-11-19
- **Ámbito:** backend (decisión de protocolo que el frontend debe implementar)

## Contexto

El JWT (Keycloak) se valida una sola vez, en el `CONNECT` STOMP (ADR-0001, B3.1). Un socket puede vivir horas, bastante más que el `exp` del token: un usuario revocado o con membresía retirada podría seguir recibiendo eventos con un token vencido. Es una de las trampas listadas del proyecto ("JWT que expira con el socket abierto"). STOMP no tiene un frame estándar de re-autenticación.

## Decisión

1. **El servidor cierra la sesión cuando vence el `exp`.** `ExpiringSocketTokenLifecycle` (implementa el punto de extensión `SocketTokenLifecycle` de B3.9; `platform.messaging`) registra cada sesión con su caducidad en el `CONNECT` y la cancela en la desconexión. Un scheduler propio (un hilo daemon, independiente de `@EnableScheduling`) envía al vencer un frame STOMP `ERROR` (`message: Token expired`) por el canal de salida; el manejador del protocolo cierra el socket tras un `ERROR`.
2. **Defensa adicional barata:** `SUBSCRIBE` y `SEND` comprueban `exp` del principal y se rechazan con `Token expired` si ya venció, aunque el cierre programado aún no se haya ejecutado.
3. **Sin caducidad no se cierra nada:** con `kanban.security.enabled=false` (solo local) el `exp` es `null` y la sesión no se registra.
4. **El cliente renueva antes de que ocurra** (contrato para el frontend, no implementado en esta fase): unos ~60 s antes del `exp` (con jitter) obtiene un token nuevo (refresh de Keycloak), abre una conexión nueva con el token nuevo, se suscribe de nuevo y cierra la anterior. Al reconectar aplica el algoritmo de `seq` y, si se perdió algo, usa `GET /api/boards/{id}/events?since=` (ADR-0005) o recarga si recibe `410`. Si no logra renovar, el cierre del servidor es el respaldo y el cliente cae en su ruta normal de reconexión con backoff.
5. El rechazo por token vencido en `CONNECT` ya existía (el decoder valida `exp`); el cliente no debe reintentar con el mismo token.

## Alternativas consideradas

| Alternativa | Pros | Contras |
| --- | --- | --- |
| Validar solo en `CONNECT` (estado anterior) | Cero código | Sesiones válidas indefinidamente con token vencido |
| Frame de re-auth (`SEND /app/auth` con token nuevo) y extender la sesión | Sin reconexión | Protocolo propio; hay que revalidar firma, `sub` y permisos de cada suscripción; más superficie |
| Validar `exp` solo en cada frame entrante | Sin timers | Un cliente suscrito y callado nunca se entera y sigue recibiendo difusiones |
| Cerrar con el scheduler propio al vencer (elegida) | Cierre puntual, simple, sin estado compartido entre instancias (cada instancia cierra sus sesiones) | Un hilo y un mapa por instancia; reconexión obligatoria |
| Reutilizar `@Scheduled` | Sin hilo propio | Acopla el cierre a la bandera de scheduling (apagada en tests) y a su granularidad |

## Consecuencias

- Positivas: ninguna sesión sobrevive a su token; funciona igual con varias instancias (Fase 6) porque cada una gestiona las sesiones que aloja; la renovación proactiva del cliente hace que el cierre no se note.
- Negativas / deuda aceptada: la revocación de membresía antes del `exp` no se aplica hasta la siguiente reconexión (como mucho la vida del token; mitigación: tokens de vida corta en Keycloak); el frontend aún debe implementar la renovación (`todos/front`); el cierre depende de que el manejador cierre tras `ERROR` (verificado en `TokenExpiryIT`).
- Cómo se verifica: `TokenExpiryIT` (JWT firmado de 2,5 s: el cliente recibe el `ERROR`, el socket se cierra y otro cliente con token vigente no se ve afectado), `ExpiringSocketTokenLifecycleTest`, `StompExpiredTokenFrameTest`, `StompSecurityIT`.

## Referencias

- `todos/back/fase-4-conflictos-reconexion.md` (B4.5); `initial-plan.md` Fase 4, tarea 4.5
- ADR-0005 (resincronización), ADR-0008
