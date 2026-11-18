import type { BoardId, WorkspaceId } from '../../../shared/kernel'

export const boardKeys = {
  all: ['boards'] as const,
  /** Tableros de un workspace (lista resumida). */
  list: (workspaceId: WorkspaceId) => [...boardKeys.all, 'list', workspaceId] as const,
  /** Cache normalizada del tablero (NormalizedBoard). */
  detail: (id: BoardId) => [...boardKeys.all, 'detail', id] as const,
}
