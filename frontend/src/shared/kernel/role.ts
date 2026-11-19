/** Rol de la persona usuaria en un workspace (lo decide el backend; el frontend solo lo respeta en la UI). */
export type Role = 'OWNER' | 'EDITOR' | 'VIEWER'

export const ROLES: readonly Role[] = ['OWNER', 'EDITOR', 'VIEWER']

export function isRole(raw: unknown): raw is Role {
  return typeof raw === 'string' && (ROLES as readonly string[]).includes(raw)
}

/** Convierte el texto del backend en `Role`; un valor desconocido es un contrato roto. */
export function toRole(raw: string): Role {
  if (!isRole(raw)) throw new Error(`Rol desconocido: ${raw}`)
  return raw
}

/** Crear/editar/mover contenido: EDITOR y OWNER. VIEWER solo lee. */
export function canEdit(role: Role): boolean {
  return role === 'OWNER' || role === 'EDITOR'
}

/** Gestionar miembros y ajustes del workspace: solo OWNER. */
export function canAdmin(role: Role): boolean {
  return role === 'OWNER'
}

export const ROLE_LABELS: Readonly<Record<Role, string>> = {
  OWNER: 'Propietario',
  EDITOR: 'Editor',
  VIEWER: 'Lector',
}
