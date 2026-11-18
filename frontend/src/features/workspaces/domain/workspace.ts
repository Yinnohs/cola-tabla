import { validateName, type NameError, type Result, type WorkspaceId } from '../../../shared/kernel'

export interface Workspace {
  readonly id: WorkspaceId
  readonly name: string
  readonly version: number
  readonly createdAt: string
}

export type WorkspaceNameError = NameError

export function workspaceName(raw: string): Result<string, WorkspaceNameError> {
  return validateName(raw)
}
