import type { Role, WorkspaceId } from '../../../shared/kernel'
import { unwrap, type ApiClient } from '../../../shared/lib/api'
import type { MemberRepository } from '../application/ports/member-repository'
import { toMember } from './member-mapper'

export class HttpMemberRepository implements MemberRepository {
  private readonly api: ApiClient
  constructor(api: ApiClient) {
    this.api = api
  }

  async list(workspaceId: WorkspaceId, signal?: AbortSignal) {
    const dtos = await unwrap(
      this.api.GET('/api/workspaces/{workspaceId}/members', {
        params: { path: { workspaceId } },
        signal,
      }),
    )
    return dtos.map(toMember)
  }

  async invite(workspaceId: WorkspaceId, userId: string, role: Role) {
    const dto = await unwrap(
      this.api.POST('/api/workspaces/{workspaceId}/members', {
        params: { path: { workspaceId } },
        body: { userId, role },
      }),
    )
    return toMember(dto)
  }

  async changeRole(workspaceId: WorkspaceId, userId: string, role: Role) {
    const dto = await unwrap(
      this.api.PATCH('/api/workspaces/{workspaceId}/members/{userId}', {
        params: { path: { workspaceId, userId } },
        body: { role },
      }),
    )
    return toMember(dto)
  }

  async remove(workspaceId: WorkspaceId, userId: string) {
    await unwrap(
      this.api.DELETE('/api/workspaces/{workspaceId}/members/{userId}', {
        params: { path: { workspaceId, userId } },
      }),
    )
  }
}
