# Hotfix: eliminar `useEffect` del frontend

- **Estado:** implementado (lint, tsc y 206 tests en verde; ver ADR-0004)
- **Alcance:** `frontend/src/**`
- **Motivo:** decisión técnica — no se usará `useEffect` (ni `useLayoutEffect`) dentro de los componentes React de la aplicación.

## 1. Inventario

Búsqueda de `useEffect|useLayoutEffect` en `frontend/src`: **un solo archivo, 2 usos.**

| # | Archivo | Línea | Qué hace |
|---|---------|-------|----------|
| 1 | `features/cards/ui/CardDialog.tsx` | 28 | Cleanup al desmontar: devuelve el foco al elemento que abrió el diálogo (`opener`). |
| 2 | `features/cards/ui/CardDialog.tsx` | 34 | Si `card` desaparece de la cache (borrada por otro usuario/evento WS), llama `onClose()`. |

El resto del código (hooks de `application/`, reducers de eventos, dnd-kit) no usa efectos directos; la sincronización con el servidor va por TanStack Query.

## 2. Plan de cambio

### 2.1 Uso #2 — cerrar el diálogo si la tarjeta desaparece

**Problema:** es estado derivado disfrazado de efecto. El padre ya sabe si la tarjeta existe.

**Solución:** derivar en `BoardView` y renderizar el diálogo solo si la tarjeta existe.

```tsx
// BoardView.tsx
const openCard = openCardId ? board.cards[openCardId] : undefined
...
{openCard && <CardDialog boardId={boardId} card={openCard} onClose={closeDialog} />}
```

- Cambiar la condición de `openCardId &&` a `openCard &&`.
- En `CardDialog`: `card` pasa a ser `Card` (no `Card | undefined`); se eliminan `if (!card) return null`, los `if (!card) return` internos y el efecto.
- El estado `openCardId` queda "colgando" con un id inexistente; es inofensivo (el id no vuelve a existir), pero si se quiere limpio, ajustar el estado durante el render en `BoardView`:
  `if (openCardId && !openCard) setOpenCardId(null)` (patrón oficial "adjusting state during render", sin efecto).

### 2.2 Uso #1 — devolver el foco al cerrar

**Problema:** efecto usado solo por su cleanup para un side-effect de DOM en desmontaje.

**Opción A (recomendada): ref callback con cleanup** (React 19 lo soporta, el proyecto usa `react ^19.3.1`).

```tsx
const restoreFocusRef = useCallback(() => {
  return () => { if (opener instanceof HTMLElement) opener.focus() }
}, [opener])

<div role="dialog" ref={restoreFocusRef} ...>
```

- El callback **debe ser estable** (`useCallback`, dependiendo de `opener`); si no, el cleanup correría en cada render.
- `opener` se sigue capturando con `useState(() => document.activeElement)` (inicializador perezoso, no es un efecto).

**Opción B: `<dialog>` nativo + `showModal()`** — el navegador restaura el foco solo, además aporta focus trap y Escape. Es un cambio mayor (estilos, tests, jsdom) y queda fuera de este hotfix; se anota como mejora futura.

## 3. Prevención (regla de lint)

En `frontend/eslint.config.js` añadir:

```js
'no-restricted-imports': ['error', {
  paths: [{ name: 'react', importNames: ['useEffect', 'useLayoutEffect', 'useInsertionEffect'],
            message: 'No se permite useEffect: derivar estado, usar event handlers, ref callbacks o TanStack Query.' }],
}],
```

Complementar con una nota en la skill `frontend-hexagonal-features` y un ADR corto (`docs/adr/0004-sin-use-effect.md`) para dejar registrada la regla.

## 4. Guía para futuros casos

| Necesidad | Alternativa sin efecto |
|-----------|------------------------|
| Estado derivado de props/estado | Calcular en el render / `useMemo` |
| Reaccionar a una acción del usuario | Event handler |
| Cargar/sincronizar datos | TanStack Query (`useQuery`, `useMutation`, `setQueryData`) |
| Resetear estado al cambiar un id | `key={id}` en el componente |
| Side-effect de DOM al montar/desmontar | Ref callback con cleanup |
| Suscripción externa (WS, media query) | `useSyncExternalStore` o capa de infraestructura fuera de componentes |

## 5. Tareas

1. [x] `BoardView.tsx`: renderizar `CardDialog` con `openCard &&` (y opcional: reset de `openCardId` durante el render).
2. [x] `CardDialog.tsx`: tipar `card: Card`, quitar guards `!card` y el efecto #2.
3. [x] `CardDialog.tsx`: reemplazar efecto #1 por ref callback estable.
4. [x] `CardDialog.tsx`: quitar `useEffect` del import.
5. [x] Regla ESLint `no-restricted-imports` + `npm run lint`.
6. [x] ADR 0004 y nota en la skill de frontend.

## 6. Verificación

- `npm run lint`, `npm run build` (tsc) y `npm test` en `frontend/`.
- `card-dialog.test.tsx` ya cubre el retorno de foco (líneas ~60-66): debe seguir pasando sin cambios.
- Añadir test: si la tarjeta desaparece del tablero con el diálogo abierto, el diálogo se cierra y el foco vuelve a la tarjeta.
- `grep -rE "useEffect|useLayoutEffect" frontend/src` → sin resultados.

## 7. Riesgos

- **Bajo.** Solo 1 componente afectado. El punto delicado es la estabilidad del ref callback (si cambia de identidad, el foco saltaría en cada render) — cubierto por el test de foco.
- En StrictMode (dev) los ref callbacks con cleanup se ejecutan dos veces al montar; el cleanup devolvería el foco al `opener` momentáneamente y `autoFocus` del input lo recupera. Verificar manualmente en `npm run dev`.
