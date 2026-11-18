# Pruebas manuales — Backend Fase 1 (dominio + REST)

Guía para comprobar a mano que la REST de la Fase 1 funciona de punta a punta contra la infraestructura real
(Postgres + Keycloak). Cubre: `Workspace -> Board -> List -> Card`, orden por posición fraccionaria, mover una
tarjeta = **una sola fila actualizada**, concurrencia optimista (`409 STALE_VERSION`), errores RFC 7807
(`404`, `422`, `401`), rebalanceo de posiciones y Swagger UI.

Ficheros de esta carpeta:

| Fichero | Para qué |
| --- | --- |
| `README.md` | Esta guía (requisitos + escenarios con `curl`) |
| `smoke.sh` | Camino feliz automático con aserciones de códigos HTTP |
| `rebalance-demo.sh` | Provoca el rebalanceo con inserciones repetidas en el mismo hueco |
| `requests.http` | Los mismos escenarios para REST Client (VS Code) / IntelliJ HTTP Client |

## 1. Requisitos

- JDK 21 (`java -version`), Docker con Compose v2, `curl` y bash (Git Bash en Windows). No hacen falta `jq` ni Python:
  los scripts solo usan `curl`, `grep` y `sed`.
- Puertos libres: 5432 (Postgres), 6379 (Redis), 8180 (Keycloak), 8000 (nginx), 8080 (backend).

## 2. Levantar la infraestructura

```bash
cd infra
docker compose up -d
docker compose ps          # postgres y redis "healthy"; keycloak tarda ~30-60 s en estar listo
```

Espera a Keycloak (importa el realm `kanban` de `infra/keycloak/realm.json` al arrancar):

```bash
until curl -sf http://localhost:8180/realms/kanban/.well-known/openid-configuration >/dev/null; do sleep 3; done && echo "Keycloak listo"
```

Si cambiaste `realm.json` con el volumen `keycloak_data` ya creado, Keycloak **no reimporta**: haz
`docker compose down -v && docker compose up -d` (borra también los datos de Postgres).

## 3. Arrancar el backend (perfil `local`)

El perfil `local` (`backend/src/main/resources/application-local.yml`) apunta a `localhost:5432` (Postgres
`kanban/kanban/kanban`), `localhost:6379` (Redis) y habilita OpenAPI/Swagger UI. La validación del JWT usa el
`jwk-set-uri` de Keycloak (`http://localhost:8180/realms/kanban/protocol/openid-connect/certs`), no hace falta
configurar nada más. Flyway crea el esquema al arrancar.

```bash
cd backend
./mvnw -B spring-boot:run -Dspring-boot.run.profiles=local
# o: ./mvnw -B -DskipTests package && java -jar target/kanban-live-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Comprueba: `curl http://localhost:8080/actuator/health` -> `{"status":"UP"...}`.

## 4. Obtener un JWT de Keycloak

Usuarios del realm `kanban` (contraseña = nombre de usuario): `owner`, `editor`, `viewer`.

El cliente de la SPA (`kanban-web`) usa Authorization Code + PKCE y **no** permite el flujo de contraseña, así que para
curl el realm incluye un cliente solo para desarrollo local, **`kanban-dev-cli`** (público, con
`directAccessGrantsEnabled: true`). No debe existir en entornos reales.

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/kanban/protocol/openid-connect/token \
  -d grant_type=password -d client_id=kanban-dev-cli -d username=owner -d password=owner \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
echo "${TOKEN:0:30}..."
```

El token dura 1 hora en el realm de desarrollo (`accessTokenLifespan: 3600`); repite el comando si recibes `401`.

En todos los ejemplos siguientes se asume `TOKEN` definido y:

```bash
API=http://localhost:8080
AUTH="Authorization: Bearer $TOKEN"
JSON="Content-Type: application/json"
# Helper para extraer un campo string de un JSON simple: id=$(echo "$resp" | jid id)
jid() { sed -E "s/.*\"$1\":\"([^\"]+)\".*/\1/"; }
jnum() { sed -E "s/.*\"$1\":([0-9]+).*/\1/"; }
```

> `jid` toma la **primera** aparición del campo en la línea; en las respuestas de creación el `id` del propio
> recurso es el primer `"id"`.

## 5. Atajo: smoke test automático

```bash
bash manual/test/fase-1/smoke.sh        # desde la raíz del repo; usa API=http://localhost:8080 por defecto
```

Sale con código 0 si todos los pasos devuelven lo esperado. Los escenarios de abajo son lo mismo, a mano.

## 6. Escenarios

### Escenario 1 — Crear workspace, tablero, listas y tarjetas

```bash
WS=$(curl -s -X POST $API/api/workspaces -H "$AUTH" -H "$JSON" -d '{"name":"Equipo Demo"}' | jid id)
BOARD=$(curl -s -X POST $API/api/workspaces/$WS/boards -H "$AUTH" -H "$JSON" -d '{"name":"Sprint 1"}' | jid id)
TODO=$(curl -s -X POST $API/api/boards/$BOARD/lists -H "$AUTH" -H "$JSON" -d '{"name":"Todo"}' | jid id)
DOING=$(curl -s -X POST $API/api/boards/$BOARD/lists -H "$AUTH" -H "$JSON" -d '{"name":"Doing"}' | jid id)
C1=$(curl -s -X POST $API/api/lists/$TODO/cards -H "$AUTH" -H "$JSON" -d '{"title":"Tarjeta 1"}' | jid id)
C2=$(curl -s -X POST $API/api/lists/$TODO/cards -H "$AUTH" -H "$JSON" -d '{"title":"Tarjeta 2","description":"detalle"}' | jid id)
C3=$(curl -s -X POST $API/api/lists/$TODO/cards -H "$AUTH" -H "$JSON" -d '{"title":"Tarjeta 3"}' | jid id)
echo "$WS $BOARD $TODO $DOING $C1 $C2 $C3"
```

Esperado: cada `POST` devuelve `201` con cabecera `Location`. Para ver el código HTTP añade `-i` o
`-o /dev/null -w '%{http_code}\n'`. `GET $API/api/workspaces` lista el workspace creado.

### Escenario 2 — `GET /api/boards/{id}`: orden de listas y tarjetas

```bash
curl -s $API/api/boards/$BOARD -H "$AUTH"
```

Esperado: `lists` ordenadas `Todo`, `Doing` y dentro de `Todo` las tarjetas `Tarjeta 1`, `Tarjeta 2`, `Tarjeta 3`;
cada elemento trae `position` (string base 62, p. ej. `"V"`, `"k"`...) y `version` (`0`).

### Escenario 3 — Mover tarjeta: una sola fila cambia

Antes de mover, guarda el estado de la tabla:

```bash
docker compose -f infra/docker-compose.yml exec -T postgres psql -U kanban -d kanban \
  -c "select title, list_id, position, version, updated_at from card order by list_id, position;"
```

**3a. Misma lista.** Mover `Tarjeta 3` entre `Tarjeta 1` y `Tarjeta 2`:

```bash
curl -s -X POST $API/api/cards/$C3/move -H "$AUTH" -H "$JSON" \
  -d "{\"listId\":\"$TODO\",\"prevCardId\":\"$C1\",\"nextCardId\":\"$C2\"}"
```

Repite la consulta de `psql`. Esperado: **solo la fila de `Tarjeta 3` cambió** (`position` nuevo entre los de 1 y 2,
`version` 0 -> 1); las filas de `Tarjeta 1` y `Tarjeta 2` tienen exactamente los mismos `position`, `version` y
`updated_at` que antes. Orden final en `GET /api/boards/$BOARD`: 1, 3, 2.

**3b. Entre listas.** Mover `Tarjeta 1` al principio de `Doing` (lista vacía: sin vecinas):

```bash
curl -s -X POST $API/api/cards/$C1/move -H "$AUTH" -H "$JSON" -d "{\"listId\":\"$DOING\"}"
```

Esperado: en `psql`, `Tarjeta 1` tiene `list_id = $DOING` y `version` 1; el resto de filas intactas. Mover a un
extremo: `prevCardId` omitido = al principio; `nextCardId` omitido = al final.

Para **ver el único `UPDATE`** en el log del backend, arráncalo con
`-Dspring-boot.run.arguments=--spring.jpa.show-sql=true` y busca `update card set ... where id=? and version=?`:
aparece una sola vez por movimiento.

Mover también listas: `POST /api/lists/{id}/move` con `{"prevListId":...,"nextListId":...}` (misma semántica).

### Escenario 4 — Editar con versión vieja -> `409 STALE_VERSION`

```bash
# 'version' actual de C2 = 0
curl -s -X PATCH $API/api/cards/$C2 -H "$AUTH" -H "$JSON" -d '{"title":"Tarjeta 2 (editada)","description":"x","version":0}'
# -> 200, version pasa a 1. Repetir con la versión vieja:
curl -s -i -X PATCH $API/api/cards/$C2 -H "$AUTH" -H "$JSON" -d '{"title":"otro cambio","version":0}'
```

Esperado en la segunda llamada: `HTTP/1.1 409` y cuerpo `application/problem+json` con `"code":"STALE_VERSION"`.
Lo mismo aplica a `PATCH /api/boards/{id}` y `PATCH /api/lists/{id}` (campo `version` obligatorio).

### Escenario 5 — Errores

```bash
# 404: recurso inexistente (code NOT_FOUND)
curl -s -i $API/api/boards/00000000-0000-0000-0000-000000000000 -H "$AUTH"
curl -s -i -X POST $API/api/lists/00000000-0000-0000-0000-000000000000/cards -H "$AUTH" -H "$JSON" -d '{"title":"x"}'

# 422: validación (code VALIDATION, lista `errors` por campo)
curl -s -i -X POST $API/api/workspaces -H "$AUTH" -H "$JSON" -d '{"name":""}'
curl -s -i -X PATCH $API/api/cards/$C2 -H "$AUTH" -H "$JSON" -d '{"title":"sin version"}'

# 401: sin token (y con token inválido)
curl -s -i $API/api/workspaces
curl -s -i $API/api/workspaces -H "Authorization: Bearer basura"
```

Esperado: `404` / `422` con `Content-Type: application/problem+json` y propiedad `code`; `401` sin cuerpo útil
(lo responde Spring Security con `WWW-Authenticate: Bearer`). Un vecino que no pertenece a la lista destino al mover
también es `404`; un vecino igual a la propia tarjeta es `422`.

### Escenario 6 — Rebalanceo de posiciones

Las posiciones son strings que crecen ~1 carácter cada ~6 inserciones repetidas **en el mismo hueco** (ADR-0003). Al
llegar a 32 caracteres, el servicio rebalancea los hermanos (reescribe `evenlySpaced(n)`, es el único caso donde se
escriben varias filas, todo en la misma transacción) y reintenta.

Provócalo con el script (hace ~200 inserciones/movimientos en el mismo hueco y vigila las longitudes):

```bash
bash manual/test/fase-1/rebalance-demo.sh
```

Qué hace: crea una lista con 2 tarjetas A y B y mueve sucesivamente tarjetas nuevas entre `A` y la última movida,
siempre en el mismo hueco, hasta que una clave supera los 32 caracteres. Imprime la longitud máxima de `position` tras
cada paso; cuando ocurre el rebalanceo la longitud cae de golpe a 1–2 caracteres, la `version` de todas las tarjetas
de la lista sube en +1 y el orden sigue intacto (el script lo comprueba con `GET /api/boards/{id}`).

A mano, tras el script, en psql:

```bash
docker compose -f infra/docker-compose.yml exec -T postgres psql -U kanban -d kanban \
  -c "select title, position, length(position) len, version from card where list_id = '<LISTA_DEL_SCRIPT>' order by position;"
```

### Escenario 7 — Swagger UI / OpenAPI

- Swagger UI: http://localhost:8080/swagger-ui.html (se redirige a `/swagger-ui/index.html`)
- Spec JSON: http://localhost:8080/v3/api-docs
- Pulsa **Authorize** y pega el access token (sin el prefijo `Bearer`) para probar desde la UI.
- La spec versionada para el cliente TS está en `docs/contracts/openapi.json` (ver `docs/contracts/README.md`).
- OpenAPI solo está habilitado en los perfiles `local`/`test`; sin perfil `local` `/v3/api-docs` responde 404.

## 7. Parar y limpiar

```bash
# Ctrl+C en la terminal del backend (o mata el proceso java)
cd infra && docker compose down        # añade -v para borrar los datos (Postgres/Keycloak)
```

Para dejar la base limpia entre ejecuciones sin tirar Keycloak:

```bash
docker compose -f infra/docker-compose.yml exec -T postgres psql -U kanban -d kanban \
  -c "truncate card, board_list, board, workspace cascade;"
```

## 8. Problemas frecuentes

| Síntoma | Causa / solución |
| --- | --- |
| `401` en todo, token vacío | Keycloak aún no listo o realm sin importar: revisa `docker compose logs keycloak`; `invalid_client` = realm viejo sin `kanban-dev-cli` -> `docker compose down -v && up -d` |
| `401` con `Jwt expired` recién pedido el token | Desfase de reloj entre la VM de Docker y el host (visto en Docker Desktop: la VM iba ~6 min atrasada). Compara `date -u` con `docker compose exec postgres date -u`; reinicia Docker Desktop (o `wsl --shutdown`) para resincronizar. El realm usa tokens de 1 h precisamente para tolerar desfases pequeños |
| Backend no arranca: `Connection refused` a 5432 | Postgres no está sano aún (`docker compose ps`) |
| `/v3/api-docs` da 404 | Arrancaste sin `-Dspring-boot.run.profiles=local` |
| Puerto 8080/8000 ocupado | Cambia `--server.port=...` y `API=...` |
