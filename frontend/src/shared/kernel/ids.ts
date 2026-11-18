declare const brand: unique symbol
export type Brand<T, B extends string> = T & { readonly [brand]: B }

export type WorkspaceId = Brand<string, 'WorkspaceId'>
export type BoardId = Brand<string, 'BoardId'>
export type ListId = Brand<string, 'ListId'>
export type CardId = Brand<string, 'CardId'>
export type UserId = Brand<string, 'UserId'>

const make =
  <T extends string>() =>
  (raw: string): T => {
    if (typeof raw !== 'string' || raw.trim() === '') {
      throw new Error('Id must be a non-empty string')
    }
    return raw as T
  }

export const workspaceId = make<WorkspaceId>()
export const boardId = make<BoardId>()
export const listId = make<ListId>()
export const cardId = make<CardId>()
export const userId = make<UserId>()
