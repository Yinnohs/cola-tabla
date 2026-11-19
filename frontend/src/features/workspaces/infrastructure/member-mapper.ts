import { toRole, workspaceId } from '../../../shared/kernel'
import { requireField } from '../../../shared/lib/api'
import type { Member } from '../domain/member'
import type { MemberDto } from './member-dto'

export function toMember(dto: MemberDto): Member {
  return {
    id: requireField(dto.id, 'id'),
    workspaceId: workspaceId(requireField(dto.workspaceId, 'workspaceId')),
    userId: requireField(dto.userId, 'userId'),
    role: toRole(requireField(dto.role, 'role')),
    version: requireField(dto.version, 'version'),
  }
}
