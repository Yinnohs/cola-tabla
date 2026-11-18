import { useQuery } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { WorkspaceId } from '../../../shared/kernel'
import { boardKeys } from './query-keys'

/** Tableros de un workspace (resumen), en el orden que devuelve el servidor. */
export function useBoards(workspaceId: WorkspaceId) {
  const { boards } = usePorts()
  return useQuery({
    queryKey: boardKeys.list(workspaceId),
    queryFn: ({ signal }) => boards.listByWorkspace(workspaceId, signal),
  })
}
