import {
  validateName,
  type NameError,
  type Result,
  type Role,
  type WorkspaceId,
} from '../../../shared/kernel'

export interface Workspace {
  readonly id: WorkspaceId
  readonly name: string
  readonly version: number
  readonly createdAt: string
  /** Rol de la persona usuaria actual en este workspace (lo decide el backend). */
  readonly role: Role
}

export type WorkspaceNameError = NameError

export function workspaceName(raw: string): Result<string, WorkspaceNameError> {
  return validateName(raw)
}
