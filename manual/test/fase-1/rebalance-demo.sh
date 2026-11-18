#!/usr/bin/env bash
# Provoca el rebalanceo de posiciones (B1.7): inserta tarjetas una y otra vez en el MISMO hueco
# (siempre entre A y la ultima movida) hasta que la clave llega a 32 caracteres y el backend rebalancea.
# Uso: bash manual/test/fase-1/rebalance-demo.sh [MAX_ITER=260]
set -u

API="${API:-http://localhost:8080}"
KEYCLOAK="${KEYCLOAK:-http://localhost:8180}"
MAX_ITER="${1:-260}"

jid() { sed -E "s/.*\"$1\":\"([^\"]+)\".*/\1/"; }
post() { # PATH JSON  -> imprime cuerpo; falla si el codigo no es 2xx
  local out code
  out=$(curl -s -w '\n%{http_code}' -X POST "$API$1" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$2")
  code=$(printf '%s' "$out" | tail -n1)
  case "$code" in 2*) printf '%s' "$out" | sed '$d' ;; *) echo "HTTP $code en POST $1: $(printf '%s' "$out" | sed '$d')" >&2; return 1 ;; esac
}

TOKEN=$(curl -s -X POST "$KEYCLOAK/realms/kanban/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=kanban-dev-cli -d username=owner -d password=owner \
  | sed -nE 's/.*"access_token":"([^"]+)".*/\1/p')
[ -n "$TOKEN" ] || { echo "sin token"; exit 2; }

WS=$(post /api/workspaces '{"name":"Rebalance WS"}' | jid id) || exit 1
BOARD=$(post "/api/workspaces/$WS/boards" '{"name":"Rebalance Board"}' | jid id) || exit 1
LIST=$(post "/api/boards/$BOARD/lists" '{"name":"Hueco"}' | jid id) || exit 1
A=$(post "/api/lists/$LIST/cards" '{"title":"A"}' | jid id) || exit 1
B=$(post "/api/lists/$LIST/cards" '{"title":"B"}' | jid id) || exit 1
echo "lista=$LIST  A=$A  B=$B"

last="$B"
prev_len=0
rebalanced_at=""
for i in $(seq 1 "$MAX_ITER"); do
  n=$(post "/api/lists/$LIST/cards" "{\"title\":\"N$i\"}" | jid id) || exit 1
  moved=$(post "/api/cards/$n/move" "{\"listId\":\"$LIST\",\"prevCardId\":\"$A\",\"nextCardId\":\"$last\"}") || exit 1
  pos=$(printf '%s' "$moved" | sed -E 's/.*"position":"([^"]+)".*/\1/')
  len=${#pos}
  if [ "$len" -lt "$prev_len" ] && [ -z "$rebalanced_at" ]; then
    rebalanced_at="$i"
    echo ">>> iteracion $i: la longitud cayo de $prev_len a $len -> REBALANCEO ejecutado"
  fi
  [ $((i % 20)) -eq 0 ] && echo "iter $i  position='$pos' (len $len)"
  prev_len=$len
  last="$n"
done

if [ -z "$rebalanced_at" ]; then
  echo "No se observo rebalanceo en $MAX_ITER iteraciones (sube MAX_ITER)"; exit 1
fi

# El orden debe seguir siendo: A, N<k>, ..., N1, B (cada tarjeta se metio justo despues de A)
EXPECTED="A,"
for i in $(seq "$MAX_ITER" -1 1); do EXPECTED="${EXPECTED}N$i,"; done
EXPECTED="${EXPECTED}B,"
ACTUAL=$(curl -s "$API/api/boards/$BOARD" -H "Authorization: Bearer $TOKEN" \
  | grep -oE '"title":"[^"]+"' | sed -E 's/.*:"//; s/"//' | tr '\n' ',')
if [ "$ACTUAL" = "$EXPECTED" ]; then
  echo "OK: orden preservado tras el rebalanceo ($((MAX_ITER + 2)) tarjetas)"
else
  echo "FALLO: el orden cambio"; echo " esperado: $EXPECTED"; echo " actual:   $ACTUAL"; exit 1
fi
echo "Comprobar en BD:"
echo "  docker compose -f infra/docker-compose.yml exec postgres psql -U kanban -d kanban -c \"select max(length(position)) maxlen, min(version) minv, max(version) maxv, count(*) from card where list_id='$LIST'\""
