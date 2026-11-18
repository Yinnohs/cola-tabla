import type { BoardId, WorkspaceId } from '../../../../shared/kernel'
import type { Board } from '../../domain/board'
import type { BoardDetails } from '../../domain/board-details'

export interface BoardRepository {
  listByWorkspace(workspaceId: WorkspaceId, signal?: AbortSignal): Promise<Board[]>
  get(id: BoardId, signal?: AbortSignal): Promise<BoardDetails>
  create(workspaceId: WorkspaceId, name: string): Promise<Board>
  rename(id: BoardId, name: string, version: number): Promise<Board>
}
