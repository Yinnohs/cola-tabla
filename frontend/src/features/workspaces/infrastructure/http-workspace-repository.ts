import type { WorkspaceId } from '../../../shared/kernel'
import { unwrap, type ApiClient } from '../../../shared/lib/api'
import type { WorkspaceRepository } from '../application/ports/workspace-repository'
import { toWorkspace } from './workspace-mapper'

export class HttpWorkspaceRepository implements WorkspaceRepository {
  private readonly api: ApiClient

  constructor(api: ApiClient) {
    this.api = api
  }

  async list(signal?: AbortSignal) {
    const dtos = await unwrap(this.api.GET('/api/workspaces', { signal }))
    return dtos.map(toWorkspace)
  }

  async get(id: WorkspaceId, signal?: AbortSignal) {
    const dto = await unwrap(
      this.api.GET('/api/workspaces/{workspaceId}', {
        params: { path: { workspaceId: id } },
        signal,
      }),
    )
    return toWorkspace(dto)
  }

  async create(name: string) {
    const dto = await unwrap(this.api.POST('/api/workspaces', { body: { name } }))
    return toWorkspace(dto)
  }
}
