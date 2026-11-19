import type { CardId, ListId } from '../../../shared/kernel'
import type { FractionalIndex } from '../../../shared/kernel'
import type { Board } from './board'

/** Read model de GET /api/boards/{id}: tablero con listas y tarjetas anidadas, ordenadas por posicion. */
export interface BoardCardView {
  readonly id: CardId
  readonly listId: ListId
  readonly title: string
  readonly description: string
  readonly position: FractionalIndex
  readonly version: number
}

export interface BoardListView {
  readonly id: ListId
  readonly name: string
  readonly position: FractionalIndex
  readonly version: number
  readonly cards: readonly BoardCardView[]
}

export interface BoardDetails {
  readonly board: Board
  /** `seq` del ultimo evento incluido en esta carga (el WS reanuda en `lastSeq + 1`). */
  readonly lastSeq: number
  readonly lists: readonly BoardListView[]
}
