import { useQuery } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { WorkspaceId } from '../../../shared/kernel'
import { workspaceKeys } from './query-keys'

/** Carga un workspace por id (para mostrar su nombre). No reintenta un 404. */
export function useWorkspace(id: WorkspaceId) {
  const { workspaces } = usePorts()
  return useQuery({
    queryKey: workspaceKeys.detail(id),
    queryFn: ({ signal }) => workspaces.get(id, signal),
  })
}
