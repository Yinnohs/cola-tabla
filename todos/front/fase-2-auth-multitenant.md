# Front — Fase 2: Autenticación y multi-tenant

**DoD:** login OIDC funcional y la UI respeta el rol del usuario.

- [x] **F2.1** Feature `auth`: OIDC PKCE con `oidc-client-ts` (cliente `kanban-web`), puerto `AuthPort` + adapter
- [x] **F2.2** `AuthProvider` en `app/providers`; rutas protegidas; callback de login/logout
- [x] **F2.3** Inyectar el token en `shared/lib/http-client` (vía puerto/callback, sin acoplar a `auth`)
- [x] **F2.4** Refresh silencioso del token; manejo de fallo de renovación → re-login
- [x] **F2.5** Manejo global 401 (re-login) y 403 (pantalla/aviso de sin permiso)
- [x] **F2.6** UI según rol (OWNER/EDITOR/VIEWER): VIEWER sin acciones de edición ni D&D activo
- [x] **F2.7** Feature `workspaces`: gestión de miembros/invitaciones y cambio de rol (según endpoints de 🔗 B2.2)
- [x] **F2.8** Tests: provider con fake de `AuthPort`, rutas protegidas, UI por rol
