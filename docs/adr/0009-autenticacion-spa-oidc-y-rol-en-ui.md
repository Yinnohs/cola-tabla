# ADR-0009: Autenticación de la SPA (OIDC + PKCE) y rol en la UI

- **Estado:** Aceptado
- **Fecha:** 2026-11-19
- **Ámbito:** transversal (frontend + ampliación mínima de backend)

## Contexto

La Fase 2 del frontend exige login OIDC, sesión renovable, manejo global de 401/403 y una UI que respete el rol (`OWNER/EDITOR/VIEWER`) del usuario en cada workspace. Restricciones: sin `useEffect` (ADR-0004), `shared` no puede importar `features`, el cliente generado solo se usa desde `infrastructure` y `app/di`, y el rol vive en nuestra BD (no en Keycloak), así que la UI no puede deducirlo del token.

## Decisión

1. **OIDC con `oidc-client-ts`** (Authorization Code + PKCE S256, cliente público `kanban-web`) detrás del puerto `AuthPort` (`features/auth`). El adapter `OidcAuthAdapter` depende de una interfaz mínima de `UserManager` (mockeable). Scope `openid profile offline_access`, `automaticSilentRenew` con `refresh_token`; si la renovación falla (`addSilentRenewError`) se hace `signinRedirect` guardando la ruta actual.
2. **Tokens solo en `sessionStorage`** (`WebStorageStateStore`): no sobreviven al cierre de la pestaña ni quedan en `localStorage`.
3. **Sesión como store externo sin efectos:** `AuthPort` expone `getSnapshot/subscribe` (`useSyncExternalStore`). La restauración de sesión (`initialize()`) se lanza en `main.tsx` antes de pintar; `AuthProvider` (`app/providers`) retiene la UI mientras el estado es `loading`. La redirección al login (`ProtectedRoute`) y el canje del código en `/auth/callback` se disparan desde el `queryFn` de TanStack Query (una vez por ruta, `staleTime: Infinity`, `retry: false`), no desde efectos.
4. **401/403 globales sin acoplar `shared` a `features`:** `createAppQueryClient` (`app/providers`) engancha un gestor a `QueryCache`/`MutationCache` `onError`. 401 ⇒ una sola renovación (`auth.refresh()`), reintento de consultas (`invalidateQueries`) y, si no se puede (o el servidor sigue rechazando en los 10 s siguientes), `login(rutaActual)`. 403 ⇒ aviso global (`ForbiddenBanner`) y, en pantallas de carga (workspace, tablero), pantalla `AccessDenied` (`shared/ui`). Las mutaciones que reciben 401 no se reintentan solas.
5. **Rol de la persona usuaria en el backend:** `WorkspaceResponse.role` (lista y detalle). **El rol sobre un tablero es el de su workspace padre** (la UI lo resuelve en `BoardPage` con `useWorkspace(board.workspaceId)`); no se añade `role` a `BoardResponse` para no acoplar `boards` con `membership`. Nuevos endpoints: `GET /api/workspaces/{id}/members` (**solo `canAdmin`/OWNER**: expone ids de usuario y la gestión es exclusiva del OWNER) y `DELETE /api/workspaces/{id}/members/{userId}` (solo OWNER; `LAST_OWNER` si es el último). Invitar sigue siendo por `userId` (`sub` de Keycloak).
6. **UI por rol** (helpers `canEdit/canAdmin` en `shared/kernel/role.ts`): VIEWER ve la UI sin controles de edición y **no se monta `DndContext`** (`BoardView` renderiza una variante de solo lectura); EDITOR edita; miembros (y futuras acciones de administración) solo para OWNER. La UI solo evita ofrecer acciones que fallarían: la autoridad sigue siendo el backend (`@perms`).
7. **Keycloak:** los usuarios de dev reciben el rol `offline_access` en `realm.json`; sin él, pedir ese scope falla con `not_allowed` (verificado importando el realm). El cliente `kanban-web` ya lo tenía como scope opcional.
8. Se elimina `VITE_DEV_TOKEN`/`devToken`. Configuración: `VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID`, `VITE_OIDC_REDIRECT_URI`, `VITE_OIDC_POST_LOGOUT_REDIRECT_URI` (con valores por defecto de desarrollo).

## Alternativas consideradas

| Alternativa | Pros | Contras |
| --- | --- | --- |
| Tokens en `localStorage` | Sesión entre pestañas | Persisten tras cerrar; mayor exposición ante XSS |
| `useEffect` para iniciar sesión/callback | Patrón habitual | Prohibido por ADR-0004; dobles ejecuciones en StrictMode |
| `role` en `BoardResponse` | Un solo GET | `boards` dependería de `membership`; el rol es del workspace |
| Reintentar la petición 401 tras refrescar (middleware) | Transparente | Hay que clonar cuerpos; las consultas ya se reintentan con `invalidateQueries` |
| `GET members` visible para cualquier miembro | Útil para mostrar equipo | Expone ids de usuario a VIEWER; sin necesidad hoy |

## Consecuencias

- Positivas: login real sin efectos, adapter testeable con mock de `UserManager`, UI coherente con el rol, backend sin cambios de esquema (sin migración).
- Negativas / deuda aceptada: la invitación exige conocer el `sub` (no hay búsqueda de usuarios); renombrar workspace no existe en el backend (no hay UI para ello); una mutación con 401 no se reintenta sola; el rol de un tablero cuesta una consulta extra (cacheada) al workspace; los volúmenes de Keycloak anteriores deben recrearse para recibir `offline_access`.
- Cómo se verifica: `oidc-auth-adapter.test.ts`, `auth-ui.test.tsx`, `providers.test.tsx`, `role-ui.test.tsx`, `members-panel.test.tsx`, `http-member-repository.test.ts`; backend `RolePermissionsIT`, `TenantIsolationIT` (nuevos endpoints), `MembershipServicesTest`; dependency-cruiser y ArchUnit en verde.

## Referencias

- `todos/front/fase-2-auth-multitenant.md` (F2.1–F2.8); ADR-0004, ADR-0006
