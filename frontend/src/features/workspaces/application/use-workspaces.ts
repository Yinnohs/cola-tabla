import { useQuery } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import { workspaceKeys } from './query-keys'

export function useWorkspaces() {
  const { workspaces } = usePorts()
  return useQuery({
    queryKey: workspaceKeys.list(),
    queryFn: ({ signal }) => workspaces.list(signal),
  })
}
