import { err, ok, type Result, type Role, type WorkspaceId } from '../../../shared/kernel'

export interface Member {
  readonly id: string
  readonly workspaceId: WorkspaceId
  /** Identificador estable de la persona: el sub de Keycloak. */
  readonly userId: string
  readonly role: Role
  readonly version: number
}

export type MemberUserIdError = 'EMPTY' | 'TOO_LONG'

export const MEMBER_USER_ID_MAX = 255

/** Valida el identificador de la persona a invitar (el sub de Keycloak). */
export function memberUserId(raw: string): Result<string, MemberUserIdError> {
  const value = raw.trim()
  if (value === '') return err('EMPTY')
  if (value.length > MEMBER_USER_ID_MAX) return err('TOO_LONG')
  return ok(value)
}

/**
 * Invariante del workspace: siempre queda al menos un OWNER. Devuelve true si el miembro es el unico OWNER
 * (no se le puede quitar ni degradar; el backend responde LAST_OWNER).
 */
export function isLastOwner(members: readonly Member[], member: Member): boolean {
  return member.role === 'OWNER' && members.filter((m) => m.role === 'OWNER').length <= 1
}
