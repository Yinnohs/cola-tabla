import {
  validateName,
  type BoardId,
  type FractionalIndex,
  type ListId,
  type NameError,
  type Result,
} from '../../../shared/kernel'

export interface List {
  readonly id: ListId
  readonly boardId: BoardId
  readonly name: string
  readonly position: FractionalIndex
  readonly version: number
}

export type ListNameError = NameError

export function listName(raw: string): Result<string, ListNameError> {
  return validateName(raw)
}
