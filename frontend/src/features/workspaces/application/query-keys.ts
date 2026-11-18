import type { WorkspaceId } from '../../../shared/kernel'

export const workspaceKeys = {
  all: ['workspaces'] as const,
  list: () => [...workspaceKeys.all, 'list'] as const,
  detail: (id: WorkspaceId) => [...workspaceKeys.all, 'detail', id] as const,
}
