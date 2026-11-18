# Back — Fase 2: Autenticación y multi-tenant

**DoD:** la suite de aislamiento demuestra que un usuario del workspace A no lee ni modifica nada del B.

- [x] **B2.1** `platform/security`: Resource Server JWT (issuer Keycloak); puerto `CurrentUser` en `application`, adapter lee el `sub`
- [x] **B2.2** `membership`: agregado `Membership(workspace, user, role)`, roles `OWNER/EDITOR/VIEWER`; migración `membership`
  - [x] Casos de uso `InviteMember`, `ChangeRole`
  - [x] El creador del workspace = OWNER (integración con `workspaces` vía `port.in`/evento)
- [x] **B2.3** Bean `@perms` (`canView/canEdit/canAdmin` por `boardId|workspaceId`) sobre un **puerto** de `membership` (sin JPA directo)
- [x] **B2.4** `@PreAuthorize` en cada controller; los `GET`/listados filtran por membresía
- [x] **B2.5** **Suite de aislamiento A vs B**: test parametrizado sobre *todos* los endpoints (lectura y escritura cruzando workspaces → 403/404)
- [x] **B2.6** Tests por rol: VIEWER no puede escribir; EDITOR no administra; OWNER sí
- [x] **B2.7** Keycloak: ajustar realm/usuarios de prueba si hace falta para los tests (Testcontainers o JWT mock firmado)
