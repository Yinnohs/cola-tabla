import type { BoardId, ListId } from '../../../shared/kernel'
import { unwrap, type ApiClient } from '../../../shared/lib/api'
import type { ListNeighbours, ListRepository } from '../application/ports/list-repository'
import { toList } from './list-mapper'

export class HttpListRepository implements ListRepository {
  private readonly api: ApiClient

  constructor(api: ApiClient) {
    this.api = api
  }

  async create(boardId: BoardId, name: string) {
    const dto = await unwrap(
      this.api.POST('/api/boards/{boardId}/lists', {
        params: { path: { boardId } },
        body: { name },
      }),
    )
    return toList(dto)
  }

  async rename(id: ListId, name: string, version: number) {
    const dto = await unwrap(
      this.api.PATCH('/api/lists/{listId}', {
        params: { path: { listId: id } },
        body: { name, version },
      }),
    )
    return toList(dto)
  }

  async move(id: ListId, { prevListId, nextListId }: ListNeighbours) {
    const dto = await unwrap(
      this.api.POST('/api/lists/{listId}/move', {
        params: { path: { listId: id } },
        body: { prevListId: prevListId ?? undefined, nextListId: nextListId ?? undefined },
      }),
    )
    return toList(dto)
  }
}
