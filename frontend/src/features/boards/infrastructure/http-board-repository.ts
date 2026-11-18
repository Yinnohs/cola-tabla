import type { BoardId, WorkspaceId } from '../../../shared/kernel'
import { unwrap, type ApiClient } from '../../../shared/lib/api'
import type { BoardRepository } from '../application/ports/board-repository'
import { toBoard, toBoardDetails } from './board-mapper'

export class HttpBoardRepository implements BoardRepository {
  private readonly api: ApiClient

  constructor(api: ApiClient) {
    this.api = api
  }

  async listByWorkspace(workspaceId: WorkspaceId, signal?: AbortSignal) {
    const dtos = await unwrap(
      this.api.GET('/api/workspaces/{workspaceId}/boards', {
        params: { path: { workspaceId } },
        signal,
      }),
    )
    return dtos.map(toBoard)
  }

  async get(id: BoardId, signal?: AbortSignal) {
    const dto = await unwrap(
      this.api.GET('/api/boards/{boardId}', { params: { path: { boardId: id } }, signal }),
    )
    return toBoardDetails(dto)
  }

  async create(workspaceId: WorkspaceId, name: string) {
    const dto = await unwrap(
      this.api.POST('/api/workspaces/{workspaceId}/boards', {
        params: { path: { workspaceId } },
        body: { name },
      }),
    )
    return toBoard(dto)
  }

  async rename(id: BoardId, name: string, version: number) {
    const dto = await unwrap(
      this.api.PATCH('/api/boards/{boardId}', {
        params: { path: { boardId: id } },
        body: { name, version },
      }),
    )
    return toBoard(dto)
  }
}
