import type { Role, WorkspaceId } from '../../../../shared/kernel'
import type { Member } from '../../domain/member'

/** Gestion de miembros de un workspace (todas las operaciones exigen ser OWNER en el backend). */
export interface MemberRepository {
  list(workspaceId: WorkspaceId, signal?: AbortSignal): Promise<Member[]>
  invite(workspaceId: WorkspaceId, userId: string, role: Role): Promise<Member>
  changeRole(workspaceId: WorkspaceId, userId: string, role: Role): Promise<Member>
  remove(workspaceId: WorkspaceId, userId: string): Promise<void>
}
