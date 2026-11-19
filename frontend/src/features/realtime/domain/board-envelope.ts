import type { BoardId, CardId, FractionalIndex, ListId, WorkspaceId } from '../../../shared/kernel'

/** Version del contrato de eventos que entiende este cliente (docs/contracts/board-events.md). */
export const SUPPORTED_SCHEMA_VERSION = 1

export interface BoardPayload {
  readonly id: BoardId
  readonly workspaceId: WorkspaceId
  readonly name: string
  readonly version: number
}

export interface ListPayload {
  readonly id: ListId
  readonly boardId: BoardId
  readonly name: string
  readonly position: FractionalIndex
  readonly version: number
}

export interface CardPayload {
  readonly id: CardId
  readonly listId: ListId
  readonly title: string
  readonly description: string
  readonly position: FractionalIndex
  readonly version: number
}

/** Tipos de evento del contrato v1, con su payload ya mapeado al dominio (ids con marca, descripcion no nula). */
export type BoardEvent =
  | { readonly type: 'board.updated'; readonly payload: { readonly board: BoardPayload } }
  | { readonly type: 'list.created'; readonly payload: { readonly list: ListPayload } }
  | { readonly type: 'list.updated'; readonly payload: { readonly list: ListPayload } }
  | { readonly type: 'list.moved'; readonly payload: { readonly list: ListPayload } }
  | { readonly type: 'card.created'; readonly payload: { readonly card: CardPayload } }
  | { readonly type: 'card.updated'; readonly payload: { readonly card: CardPayload } }
  | {
      readonly type: 'card.moved'
      readonly payload: { readonly card: CardPayload; readonly fromListId: ListId }
    }
  | {
      readonly type: 'card.deleted'
      readonly payload: { readonly cardId: CardId; readonly listId: ListId }
    }
  /** Tipo (o schemaVersion) que este cliente no sabe aplicar: obliga a recargar el tablero. */
  | { readonly type: 'unknown'; readonly rawType: string; readonly payload: unknown }

export type BoardEventType = BoardEvent['type']

/** Metadatos comunes a todo mensaje del topic `/topic/boards.{id}`. */
export interface BoardEnvelopeMeta {
  readonly boardId: BoardId
  /** Contador por tablero, >= 1, sin huecos entre eventos confirmados. */
  readonly seq: number
  /** `X-Client-Id` de la peticion que origino el evento (o null). */
  readonly clientId: string | null
  readonly occurredAt: string
  readonly schemaVersion: number
}

export type BoardEnvelope = BoardEnvelopeMeta & BoardEvent
