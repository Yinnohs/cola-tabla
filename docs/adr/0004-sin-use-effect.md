# ADR-0004: Sin `useEffect` en el frontend

- **Estado:** Aceptado
- **Fecha:** 2026-11-18
- **Ámbito:** frontend

## Contexto

Decisión técnica: los componentes React de la aplicación no usan `useEffect` (ni `useLayoutEffect`/`useInsertionEffect`). Los efectos suelen encubrir estado derivado, lógica de eventos o sincronización de datos que tienen mejores herramientas. Plan y ejecución: `docs/hotfix/eliminar-use-effect-frontend.md`.

## Decisión

- Regla ESLint `no-restricted-imports` en `frontend/eslint.config.js` que prohíbe importar esos hooks de `react` en `src/**`.
- Alternativas: estado derivado en el render; event handlers; TanStack Query para datos; `key={id}` para resetear estado; ref callbacks con cleanup para side-effects de DOM; `useSyncExternalStore` o infraestructura fuera de componentes para suscripciones externas.
- Migración: `CardDialog` (cierre al desaparecer la tarjeta → derivado en `BoardView`; retorno de foco → ref callback con cleanup).

## Consecuencias

- Positivas: menos bugs de dependencias/ciclos; flujo de datos explícito.
- Negativas: requiere conocer los patrones alternativos; los ref callbacks deben ser estables.
- Cómo se verifica: `npm run lint` y `grep -rE "useEffect|useLayoutEffect" frontend/src` sin resultados.
