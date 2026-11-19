import { toRole, workspaceId } from '../../../shared/kernel'
import { requireField } from '../../../shared/lib/api'
import type { Workspace } from '../domain/workspace'
import type { WorkspaceDto } from './workspace-dto'

export function toWorkspace(dto: WorkspaceDto): Workspace {
  return {
    id: workspaceId(requireField(dto.id, 'id')),
    name: requireField(dto.name, 'name'),
    version: requireField(dto.version, 'version'),
    createdAt: requireField(dto.createdAt, 'createdAt'),
    role: toRole(requireField(dto.role, 'role')),
  }
}
