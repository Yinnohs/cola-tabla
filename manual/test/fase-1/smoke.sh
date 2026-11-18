#!/usr/bin/env bash
# Smoke test Fase 1: camino feliz de punta a punta + errores, con aserciones de codigos HTTP.
# Requisitos: infra levantada (docker compose up -d), backend en perfil local, curl.
# Uso: bash manual/test/fase-1/smoke.sh        (API=http://localhost:8080 KEYCLOAK=http://localhost:8180 por defecto)
set -u

API="${API:-http://localhost:8080}"
KEYCLOAK="${KEYCLOAK:-http://localhost:8180}"
KC_USER="${KC_USER:-owner}"
KC_PASSWORD="${KC_PASSWORD:-owner}"

PASS=0
FAIL=0
BODY=""
STATUS=""

jid() { sed -E "s/.*\"$1\":\"([^\"]+)\".*/\1/"; }
jnum() { sed -E "s/.*\"$1\":([0-9]+).*/\1/"; }

# call METHOD PATH [JSON_BODY]  -> rellena BODY y STATUS
call() {
  local method="$1" path="$2" data="${3:-}" out
  if [ -n "$data" ]; then
    out=$(curl -s -w '\n%{http_code}' -X "$method" "$API$path" -H "Authorization: Bearer ${TOKEN:-}" \
      -H 'Content-Type: application/json' -d "$data")
  else
    out=$(curl -s -w '\n%{http_code}' -X "$method" "$API$path" -H "Authorization: Bearer ${TOKEN:-}")
  fi
  STATUS=$(printf '%s' "$out" | tail -n1)
  BODY=$(printf '%s' "$out" | sed '$d')
}

# expect DESCRIPCION CODIGO_ESPERADO
expect() {
  if [ "$STATUS" = "$2" ]; then
    PASS=$((PASS + 1)); echo "  ok   [$STATUS] $1"
  else
    FAIL=$((FAIL + 1)); echo "  FAIL [$STATUS != $2] $1"; echo "       body: $BODY"
  fi
}

# assert_contains DESCRIPCION TEXTO
assert_contains() {
  if printf '%s' "$BODY" | grep -q -- "$2"; then
    PASS=$((PASS + 1)); echo "  ok   $1"
  else
    FAIL=$((FAIL + 1)); echo "  FAIL $1 (no contiene: $2)"; echo "       body: $BODY"
  fi
}

# assert_eq DESCRIPCION ACTUAL ESPERADO
assert_eq() {
  if [ "$2" = "$3" ]; then
    PASS=$((PASS + 1)); echo "  ok   $1 ($2)"
  else
    FAIL=$((FAIL + 1)); echo "  FAIL $1: '$2' != '$3'"
  fi
}

echo "== Token ($KC_USER) =="
TOKEN=$(curl -s -X POST "$KEYCLOAK/realms/kanban/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=kanban-dev-cli -d "username=$KC_USER" -d "password=$KC_PASSWORD" \
  | sed -nE 's/.*"access_token":"([^"]+)".*/\1/p')
if [ -z "$TOKEN" ]; then echo "No se pudo obtener el token de Keycloak ($KEYCLOAK)"; exit 2; fi
echo "  ok   token obtenido (${#TOKEN} chars)"

echo "== Seguridad =="
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$API/api/workspaces")
BODY=""
expect "GET /api/workspaces sin token -> 401" 401
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$API/api/workspaces" -H 'Authorization: Bearer basura')
expect "GET /api/workspaces con token invalido -> 401" 401

echo "== Crear workspace / board / listas / tarjetas =="
call POST /api/workspaces '{"name":"Smoke WS"}'; expect "crear workspace" 201
WS=$(printf '%s' "$BODY" | jid id)
call GET /api/workspaces; expect "listar workspaces" 200
assert_contains "el workspace aparece en la lista" "$WS"

call POST "/api/workspaces/$WS/boards" '{"name":"Smoke Board"}'; expect "crear board" 201
BOARD=$(printf '%s' "$BODY" | jid id)

call POST "/api/boards/$BOARD/lists" '{"name":"Todo"}'; expect "crear lista Todo" 201
TODO=$(printf '%s' "$BODY" | jid id)
call POST "/api/boards/$BOARD/lists" '{"name":"Doing"}'; expect "crear lista Doing" 201
DOING=$(printf '%s' "$BODY" | jid id)

call POST "/api/lists/$TODO/cards" '{"title":"Tarjeta 1"}'; expect "crear tarjeta 1" 201
C1=$(printf '%s' "$BODY" | jid id)
call POST "/api/lists/$TODO/cards" '{"title":"Tarjeta 2","description":"detalle"}'; expect "crear tarjeta 2" 201
C2=$(printf '%s' "$BODY" | jid id)
call POST "/api/lists/$TODO/cards" '{"title":"Tarjeta 3"}'; expect "crear tarjeta 3" 201
C3=$(printf '%s' "$BODY" | jid id)

echo "== GET board: orden =="
call GET "/api/boards/$BOARD"; expect "GET board" 200
ORDER=$(printf '%s' "$BODY" | grep -oE '"(name|title)":"[^"]+"' | sed -E 's/.*:"//; s/"//' | tr '\n' ',')
assert_eq "orden de listas y tarjetas" "$ORDER" "Smoke Board,Todo,Tarjeta 1,Tarjeta 2,Tarjeta 3,Doing,"

echo "== Mover tarjeta en la misma lista (C3 entre C1 y C2) =="
# estado previo de C1 y C2 (position+version) para comprobar que NO cambian
snapshot() { printf '%s' "$BODY" | grep -oE "\"id\":\"$1\"[^}]*" | grep -oE '"position":"[^"]+","version":[0-9]+'; }
SNAP_C1=$(snapshot "$C1"); SNAP_C2=$(snapshot "$C2")
call POST "/api/cards/$C3/move" "{\"listId\":\"$TODO\",\"prevCardId\":\"$C1\",\"nextCardId\":\"$C2\"}"
expect "mover C3 entre C1 y C2" 200
assert_eq "version de C3 tras mover" "$(printf '%s' "$BODY" | jnum version)" "1"
call GET "/api/boards/$BOARD"; expect "GET board tras mover" 200
ORDER=$(printf '%s' "$BODY" | grep -oE '"title":"[^"]+"' | sed -E 's/.*:"//; s/"//' | tr '\n' ',')
assert_eq "nuevo orden" "$ORDER" "Tarjeta 1,Tarjeta 3,Tarjeta 2,"
assert_eq "C1 intacta (position+version)" "$(snapshot "$C1")" "$SNAP_C1"
assert_eq "C2 intacta (position+version)" "$(snapshot "$C2")" "$SNAP_C2"

echo "== Mover tarjeta entre listas (C1 -> Doing) =="
call POST "/api/cards/$C1/move" "{\"listId\":\"$DOING\"}"; expect "mover C1 a Doing" 200
assert_contains "C1 ahora en Doing" "\"listId\":\"$DOING\""
assert_eq "version de C1 tras mover" "$(printf '%s' "$BODY" | jnum version)" "1"

echo "== Editar: version correcta y version vieja =="
call PATCH "/api/cards/$C2" '{"title":"Tarjeta 2 editada","description":"x","version":0}'; expect "editar C2 con version 0" 200
assert_eq "version de C2 tras editar" "$(printf '%s' "$BODY" | jnum version)" "1"
call PATCH "/api/cards/$C2" '{"title":"conflicto","version":0}'; expect "editar C2 con version vieja -> 409" 409
assert_contains "code STALE_VERSION" '"code":"STALE_VERSION"'
call PATCH "/api/boards/$BOARD" '{"name":"Smoke Board 2","version":0}'; expect "renombrar board" 200
call PATCH "/api/lists/$TODO" '{"name":"Todo 2","version":0}'; expect "renombrar lista" 200

echo "== Errores 404 / 422 =="
NIL=00000000-0000-0000-0000-000000000000
call GET "/api/boards/$NIL"; expect "GET board inexistente -> 404" 404
assert_contains "code NOT_FOUND" '"code":"NOT_FOUND"'
call POST "/api/lists/$NIL/cards" '{"title":"x"}'; expect "crear tarjeta en lista inexistente -> 404" 404
call POST /api/workspaces '{"name":""}'; expect "workspace con nombre vacio -> 422" 422
assert_contains "code VALIDATION" '"code":"VALIDATION"'
call PATCH "/api/cards/$C2" '{"title":"sin version"}'; expect "editar sin version -> 422" 422
call POST "/api/cards/$C2/move" "{\"listId\":\"$TODO\",\"prevCardId\":\"$C2\"}"; expect "tarjeta vecina de si misma -> 422" 422

echo "== Borrar =="
call DELETE "/api/cards/$C3"; expect "borrar C3" 204
call DELETE "/api/cards/$C3"; expect "borrar C3 otra vez -> 404" 404

echo "== OpenAPI =="
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$API/v3/api-docs"); BODY=""
expect "GET /v3/api-docs publico" 200
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$API/swagger-ui/index.html"); BODY=""
expect "GET /swagger-ui/index.html publico" 200

echo
echo "Resultado: $PASS ok, $FAIL fallos"
[ "$FAIL" -eq 0 ]
