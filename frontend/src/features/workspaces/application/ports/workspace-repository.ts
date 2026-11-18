import type { WorkspaceId } from '../../../../shared/kernel'
import type { Workspace } from '../../domain/workspace'

export interface WorkspaceRepository {
  list(signal?: AbortSignal): Promise<Workspace[]>
  get(id: WorkspaceId, signal?: AbortSignal): Promise<Workspace>
  create(name: string): Promise<Workspace>
}
