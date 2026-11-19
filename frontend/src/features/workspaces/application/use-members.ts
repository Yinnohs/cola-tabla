import { useQuery } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { WorkspaceId } from '../../../shared/kernel'
import { workspaceKeys } from './query-keys'

/** Miembros del workspace (solo OWNER; con otro rol el backend responde 403). */
export function useMembers(workspaceId: WorkspaceId, enabled = true) {
  const { members } = usePorts()
  return useQuery({
    queryKey: workspaceKeys.members(workspaceId),
    queryFn: ({ signal }) => members.list(workspaceId, signal),
    enabled,
  })
}
