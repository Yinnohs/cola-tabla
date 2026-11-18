# Pruebas manuales — Frontend Fase 1 (dominio + REST)

Guía para probar a mano la SPA contra el backend real: crear workspace / tablero / listas / tarjetas, mover
tarjetas y listas (ratón **y solo teclado**), renombrar, editar con conflicto `STALE_VERSION` y borrar.

> El login con Keycloak llega en la Fase 2. Hasta entonces la SPA no tiene sesión: para que el backend no
> responda `401` hay un **token solo para desarrollo** (`VITE_DEV_TOKEN`, ver sección 3).

## 1. Requisitos

- Node 22+ y `npm install` hecho en `frontend/`.
- La infraestructura y el backend de `manual/test/fase-1/README.md` (secciones 1-3): `docker compose up -d` en
  `infra/`, esperar a Keycloak y arrancar el backend con el perfil `local` en `http://localhost:8080`.

## 2. Levantar backend + infraestructura

```bash
cd infra && docker compose up -d
until curl -sf http://localhost:8180/realms/kanban/.well-known/openid-configuration >/dev/null; do sleep 3; done
cd ../backend
./mvnw -B -DskipTests package
java -jar target/kanban-live-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
# en otra terminal: curl http://localhost:8080/actuator/health  ->  {"status":"UP"...}
```

## 3. Token de desarrollo y `npm run dev`

El cliente público `kanban-dev-cli` (solo desarrollo, ver README de esta carpeta, sección 4) permite obtener un
JWT por contraseña. Se copia en `frontend/.env.local` (git lo ignora):

```bash
cd frontend
TOKEN=$(curl -s -X POST http://localhost:8180/realms/kanban/protocol/openid-connect/token \
  -d grant_type=password -d client_id=kanban-dev-cli -d username=owner -d password=owner \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
echo "VITE_DEV_TOKEN=$TOKEN" > .env.local
npm run dev        # http://localhost:5173 ; Vite proxea /api -> http://localhost:8080
```

Notas de seguridad:

- `VITE_DEV_TOKEN` solo lo lee `createPorts()` cuando `import.meta.env.DEV` es `true` (`npm run dev`). En
  `npm run build` el código muerto se elimina y el token **no** acaba en el bundle (comprobado: el JS de `dist/` no
  contiene `VITE_DEV_TOKEN`).
- El token dura 1 hora. Si empiezas a ver errores `401`, repite el bloque anterior y recarga la página (Vite
  reinicia solo al cambiar `.env.local`; si no, reinicia `npm run dev`).
- Nunca pongas `.env.local` en git ni uses este mecanismo fuera de desarrollo; la Fase 2 lo sustituye por Keycloak.

## 4. Flujos a probar

Usa el navegador en `http://localhost:5173`.

### 4.1 Crear workspace, tablero, listas y tarjetas

1. Inicio: escribe un nombre en **Nombre del workspace** -> *Crear workspace*. Navega a la página del workspace.
2. En el workspace: el título (`<h1>`) es el **nombre del workspace** y debajo aparece la lista de sus tableros (enlaces a `/boards/{id}`; si no hay, un mensaje de vacío). Un id inexistente muestra *Workspace no encontrado*. **Nombre del tablero** -> *Crear tablero* navega a `/boards/{id}`; el enlace *Volver al workspace* del tablero regresa y el tablero nuevo ya figura en la lista.
3. En el tablero: **Nombre de la lista** -> *Añadir lista* (crea `Todo`, `Doing`, `Done`). Aparecen a la derecha de las existentes.
4. En cada lista: *Añadir tarjeta* -> escribe el título -> *Añadir* (o Enter). `Escape` cierra el formulario. Crea 3-4 tarjetas en `Todo`.
5. Un nombre/título vacío muestra un aviso (`El nombre es obligatorio`) y no llama al servidor.

Qué mirar: las listas y tarjetas aparecen sin recargar; al recargar la página (F5) todo sigue igual y en el mismo orden.

### 4.2 Mover con el ratón

- Arrastra una tarjeta dentro de la misma lista (hay que mover ~5 px para que empiece el arrastre; un simple clic abre la tarjeta).
- Arrástrala a otra lista (también a una lista vacía).
- Arrastra una lista por el asa `⠿` de su cabecera para reordenar columnas.

Qué mirar: la tarjeta queda donde la soltaste **al instante** (optimista). En la pestaña *Network* del navegador,
`POST /api/cards/{id}/move` lleva `{"listId":..., "prevCardId":..., "nextCardId":...}` (ids de vecinas, **no** una
posición) y la respuesta trae `position` y `version` nuevos. Al recargar el orden se conserva.

### 4.3 Mover solo con teclado (DoD de la fase)

Sin tocar el ratón:

| Acción | Teclas |
| --- | --- |
| Ir a una tarjeta / al asa de una lista | `Tab` / `Shift+Tab` |
| Recoger la tarjeta (o lista) enfocada | `Espacio` |
| Moverla dentro de la lista | `Flecha arriba` / `Flecha abajo` |
| Pasarla a la lista de la derecha / izquierda | `Flecha derecha` / `Flecha izquierda` |
| Soltarla en el sitio actual | `Espacio` |
| Cancelar y volver al sitio original | `Escape` |
| Abrir la tarjeta (diálogo de edición) | `Enter` (no recoge: `Enter` queda libre para abrir) |

Reordenar **listas**: `Tab` hasta el botón `Mover lista <nombre>` (el asa `⠿`), `Espacio`, `Flecha izquierda/derecha`, `Espacio`.

Qué mirar:

- Bajo el tablero hay una región de estado (aria-live) con el último mensaje, p. ej.
  `Tarjeta "A" recogida en la lista "Todo", posición 1 de 3...` -> `Tarjeta "A" en la lista "Todo", posición 2 de 3.`
  -> `Tarjeta "A" soltada en la lista "Todo", posición 2 de 3.`; con `Escape`: `Movimiento cancelado...`.
- Con un lector de pantalla (NVDA/Narrator) se anuncian los mismos mensajes y las instrucciones al enfocar la tarjeta.
- El foco vuelve a la tarjeta al soltar. La tarjeta "objetivo" se resalta con un borde mientras se mueve.
- Mover a una lista vacía: `Flecha derecha` desde la tarjeta salta a la lista vacía y `Espacio` la deja ahí.

### 4.4 Renombrar

- Tablero: botón *Renombrar* junto al título -> *Guardar*.
- Lista: *Renombrar* en la cabecera de la lista (`Escape` cancela).

Qué mirar: el nombre cambia al instante; `PATCH` envía `version`; si el servidor lo rechaza, el nombre se revierte.

### 4.5 Editar tarjeta y conflicto `STALE_VERSION` (dos pestañas)

1. Abre el mismo tablero en **dos pestañas** (A y B).
2. En A: clic en una tarjeta -> cambia el título -> *Guardar*. Se cierra el diálogo.
3. En B (sin recargar): abre la **misma** tarjeta (todavía con la versión antigua), cambia el título -> *Guardar*.
4. B muestra un aviso: *"Esta tarjeta ha sido modificada por otra persona mientras la editabas. Tus cambios no se han guardado."* con el botón **Recargar datos actuales**; *Guardar* queda deshabilitado.
5. Pulsa *Recargar datos actuales*: el formulario muestra el título de A. Edita de nuevo y *Guardar* funciona.

Qué mirar: en *Network*, el `PATCH /api/cards/{id}` de B responde `409` con `"code":"STALE_VERSION"` y a continuación se
hace un `GET /api/boards/{id}` (refetch). El servidor conserva el cambio de A hasta que B guarda tras recargar.

### 4.6 Borrar

Clic en una tarjeta -> *Eliminar tarjeta* -> *Confirmar eliminación* (o *No eliminar*). La tarjeta desaparece al instante;
`DELETE /api/cards/{id}` responde `204`. Si el borrado falla, la tarjeta reaparece.

### 4.7 Errores

- Con el backend parado: crear/mover muestra el mensaje de error y revierte el cambio optimista.
- Con token caducado/ausente: respuestas `401` (la SPA aún no tiene login; ver sección 3).

## 5. Comprobaciones automáticas relacionadas

```bash
cd frontend
npm run lint && npm run typecheck && npm test -- --run && npm run depcruise && npm run build && npm run format:check
```

Los tests de teclado del DnD (`src/features/cards/ui/board-dnd.test.tsx`) reproducen el flujo de 4.3 con React Testing
Library (Espacio / flechas / Espacio) y comprueban los ids de vecinas enviados y los anuncios.

## 6. Parar y limpiar

```bash
# Ctrl+C en `npm run dev` y en el backend
cd infra && docker compose down        # NO uses -v salvo que quieras borrar los datos
rm frontend/.env.local
```
