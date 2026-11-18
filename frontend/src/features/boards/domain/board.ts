import {
  validateName,
  type BoardId,
  type NameError,
  type Result,
  type WorkspaceId,
} from '../../../shared/kernel'

export interface Board {
  readonly id: BoardId
  readonly workspaceId: WorkspaceId
  readonly name: string
  readonly version: number
}

export type BoardNameError = NameError

export function boardName(raw: string): Result<string, BoardNameError> {
  return validateName(raw)
}
