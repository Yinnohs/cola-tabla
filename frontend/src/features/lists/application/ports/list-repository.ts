import type { BoardId, ListId } from '../../../../shared/kernel'
import type { List } from '../../domain/list'

export interface ListNeighbours {
  readonly prevListId: ListId | null
  readonly nextListId: ListId | null
}

export interface ListRepository {
  create(boardId: BoardId, name: string): Promise<List>
  rename(id: ListId, name: string, version: number): Promise<List>
  /** El servidor calcula la posicion a partir de los vecinos y devuelve la lista con position/version nuevos. */
  move(id: ListId, neighbours: ListNeighbours): Promise<List>
}
